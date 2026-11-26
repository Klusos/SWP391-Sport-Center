package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.MembershipRenewalIntentService;

/** Explicitly invoked, clone-pinned test-only fixture builder/cleanup for Phase 7D browser verification. */
@SpringBootTest(properties = {
        "spring.datasource.url=" + Phase8CJPtOutcomeSqlServerIT.CloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never",
        "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false",
        "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "debug=false",
        "logging.level.root=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase8CJPtOutcomeSqlServerIT.CloneGuard.class)
class Phase7DRenewalFixtureIT {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PREFIX = "P7D-" + UUID.randomUUID().toString().substring(0, 10);
    private static final String[] TABLES = { "membership", "payment", "membership_upgrade",
            "membership_cancellation_request", "membership_plan_upgrade" };

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipRenewalIntentService renewalIntents;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void explicitlyRequestedSetupOrCleanup() throws Exception {
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        Path manifestPath = requiredManifestPath();
        String operation = System.getProperty("phase7d.operation", "");
        if ("setup".equals(operation)) setup(manifestPath);
        else if ("cleanup".equals(operation)) cleanup(manifestPath);
        else if ("cleanup-orphan".equals(operation)) cleanupOrphanedSetupRecords();
        else if ("inspect".equals(operation)) inspectFixturePayments(manifestPath);
        else fail("Set phase7d.operation to setup or cleanup");
    }

    private void setup(Path manifestPath) throws Exception {
        assertFalse(Files.exists(manifestPath), "Refusing to overwrite an existing fixture/secret manifest");
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ? OR email LIKE ?",
                Integer.class, PREFIX + "%", "%@phase7d.test");
        assertEquals(0, existing, "Unexpected Phase 7D marker already exists");
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("prefix", PREFIX);
        manifest.put("baseline", snapshotProtectedRows());
        List<Map<String, Object>> fixtures = new ArrayList<>();
        LocalDate today = LocalDate.now(VN);
        LocalDateTime now = LocalDateTime.now(VN).withNano(0);
        for (String label : List.of("online", "counter")) {
            String email = label + "." + UUID.randomUUID().toString().replace("-", "") + "@phase7d.test";
            String password = UUID.randomUUID().toString().replace("-", "") + "Aa7!";
            User member = new User();
            member.setName(PREFIX + "-" + label);
            member.setEmail(email);
            member.setPasswordHash(passwordEncoder.encode(password));
            member.setRole("MEMBER");
            member.setStatus("ACTIVE");
            member = users.saveAndFlush(member);

            MembershipPlan plan = new MembershipPlan();
            plan.setName(PREFIX + "-" + label + "-plan");
            plan.setDescription("Temporary Phase 7D Renewal browser fixture");
            plan.setPrice(new BigDecimal("725000.00"));
            plan.setDurationMonths(1);
            plan.setCourseDiscountPercent(new BigDecimal("10.00"));
            plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
            plan.setAllowPersonalTrainingPlan(true);
            plan.setAllowOneOnOneCoaching(true);
            plan.setStatus("ACTIVE");
            plan = plans.saveAndFlush(plan);

            Membership source = new Membership();
            source.setMember(member);
            source.setPlan(plan);
            source.setAutoRenew(true);
            source.setStatus("ACTIVE");
            source.setEndDate(today.plusDays(2));
            source.setStartDate(source.getEndDate().minusMonths(1));
            source.setBenefitSnapshotStatus("COMPLETE");
            source.setBenefitSnapshotSource("PURCHASE");
            source.setPlanNameSnapshot(plan.getName());
            source.setPlanDescriptionSnapshot(plan.getDescription());
            source.setPlanPriceSnapshot(plan.getPrice());
            source.setPlanDurationMonthsSnapshot(plan.getDurationMonths());
            source.setCourseDiscountPercentSnapshot(plan.getCourseDiscountPercent());
            source.setPtDiscountPercentSnapshot(plan.getPersonalTrainingDiscountPercent());
            source.setAllowPtPlanSnapshot(plan.getAllowPersonalTrainingPlan());
            source.setAllowOneOnOneSnapshot(plan.getAllowOneOnOneCoaching());
            source.setPaidValueBasisStatus("UNKNOWN");
            source.setPaidValueBasisSource("LEGACY_UNKNOWN");
            source = memberships.saveAndFlush(source);

            MembershipRenewalIntentService.CreationResult creation = renewalIntents.createForScheduler(source.getId());
            assertEquals(MembershipRenewalIntentService.Outcome.CREATED, creation.outcome());
            assertEquals(RenewalIntentStatus.OPEN, creation.intent().getIntentStatus());
            assertEquals(today.plusDays(2), creation.intent().getSuccessorStartDate());
            assertTrue(now.isBefore(creation.intent().getValidUntil()));

            Map<String, Object> item = new HashMap<>();
            item.put("label", label);
            item.put("memberId", member.getId());
            item.put("email", email);
            item.put("password", password);
            item.put("planId", plan.getId());
            item.put("sourceMembershipId", source.getId());
            item.put("intentId", creation.intent().getId());
            item.put("sourceEndDate", source.getEndDate().toString());
            item.put("deadline", creation.intent().getValidUntil().toString());
            item.put("quotedAmount", creation.intent().getQuotedAmount().toPlainString());
            fixtures.add(item);
        }
        manifest.put("fixtures", fixtures);
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, mapper.writeValueAsString(manifest), StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        System.out.println("PHASE7D_FIXTURES_CREATED count=2 prefix=" + PREFIX
                + " sourceEnd=" + today.plusDays(2) + " intents=OPEN deadline=" + today.plusDays(2).atStartOfDay());
    }

    private void cleanup(Path manifestPath) throws Exception {
        assertTrue(Files.isRegularFile(manifestPath), "Fixture manifest missing; refusing unowned cleanup");
        Map<String, Object> manifest = mapper.readValue(Files.readString(manifestPath),
                new TypeReference<>() {});
        String prefix = (String) manifest.get("prefix");
        assertNotNull(prefix);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixtures = (List<Map<String, Object>>) manifest.get("fixtures");
        assertNotNull(fixtures);
        List<Integer> ownedPlanIds = fixtures.stream()
                .map(item -> ((Number) item.get("planId")).intValue()).toList();
        for (Map<String, Object> item : fixtures) {
            int planId = ((Number) item.get("planId")).intValue();
            int planRows = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan WHERE id=?",
                    Integer.class, planId);
            assertTrue(planRows <= 1, "Duplicate run-owned Plan ID; cleanup refused");
            if (planRows == 0) {
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE plan_id=?",
                        Integer.class, planId), "Missing Plan still has Membership references; cleanup refused");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan_upgrade WHERE from_plan_id=? OR to_plan_id=?",
                        Integer.class, planId, planId), "Missing Plan still has Upgrade Path references; cleanup refused");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan WHERE id=?",
                        Integer.class, planId), "Plan ID exists with unexpected ownership marker");
            }
        }
        for (int i = 0; i < ownedPlanIds.size(); i++) {
            int fromPlanId = ownedPlanIds.get(i);
            for (int j = 0; j < ownedPlanIds.size(); j++) {
                if (i == j) continue;
                int toPlanId = ownedPlanIds.get(j);
                Integer pathCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan_upgrade WHERE from_plan_id=? AND to_plan_id=?",
                        Integer.class, fromPlanId, toPlanId);
                assertTrue(pathCount <= 1, "Unexpected duplicate path between run-owned plans; cleanup refused");
                jdbc.update("DELETE FROM dbo.membership_plan_upgrade WHERE from_plan_id=? AND to_plan_id=?",
                        fromPlanId, toPlanId);
            }
        }
        for (Map<String, Object> item : fixtures) {
            int memberId = ((Number) item.get("memberId")).intValue();
            int planId = ((Number) item.get("planId")).intValue();
            int sourceId = ((Number) item.get("sourceMembershipId")).intValue();
            int intentId = ((Number) item.get("intentId")).intValue();
            String email = (String) item.get("email");
            Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE id=? AND email=? AND name=?",
                    Integer.class, memberId, email, prefix + "-" + item.get("label"));
            if (owned == 0) {
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE id=? OR email=?",
                        Integer.class, memberId, email), "Fixture identity exists but ownership marker differs");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE id=? OR renewal_intent_id=?",
                        Integer.class, sourceId, intentId), "Unowned Membership rows remain for absent fixture");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE renewal_intent_id=?",
                        Integer.class, intentId), "Unowned Payment rows remain for absent fixture");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_renewal_intent WHERE id=?",
                        Integer.class, intentId), "Unowned Intent remains for absent fixture");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan WHERE id=?",
                        Integer.class, planId), "Unowned Plan remains for absent fixture");
                continue;
            }
            assertEquals(1, owned, "Ownership mismatch; cleanup refused");
            int sourceMembershipRows = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE id=? AND member_id=? AND plan_id=?",
                    Integer.class, sourceId, memberId, planId);
            if (sourceMembershipRows == 0) {
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE member_id=?",
                        Integer.class, memberId), "Synthetic user still has Membership rows; cleanup refused");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=? OR renewal_intent_id=?",
                        Integer.class, memberId, intentId), "Synthetic user still has Payment rows; cleanup refused");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_renewal_intent WHERE id=?",
                        Integer.class, intentId), "Synthetic user still has Renewal Intent; cleanup refused");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan_upgrade WHERE from_plan_id=? OR to_plan_id=?",
                        Integer.class, planId, planId), "Synthetic Plan still has Upgrade Path references; cleanup refused");
                jdbc.update("DELETE FROM dbo.notification WHERE user_id=?", memberId);
                jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id=?", memberId);
                jdbc.update("DELETE FROM dbo.users WHERE id=? AND email=? AND name=?", memberId, email, prefix + "-" + item.get("label"));
                jdbc.update("DELETE FROM dbo.membership_plan WHERE id=?", planId);
                continue;
            }
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan WHERE id=?",
                    Integer.class, planId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE id=? AND member_id=? AND plan_id=?",
                    Integer.class, sourceId, memberId, planId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_renewal_intent WHERE id=? AND source_membership_id=?",
                    Integer.class, intentId, sourceId));
            String intentStatus = jdbc.queryForObject("SELECT intent_status FROM dbo.membership_renewal_intent WHERE id=?",
                    String.class, intentId);
            Integer acceptedPayments = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE renewal_intent_id=? "
                    + "AND status='SUCCESS' AND renewal_settlement_accepted_at IS NOT NULL",
                    Integer.class, intentId);
            List<Integer> successorIds = jdbc.query("SELECT id FROM dbo.membership WHERE member_id=? AND id<>?",
                    (rs, row) -> rs.getInt(1), memberId, sourceId);
            assertTrue(successorIds.size() <= 1, "Unexpected extra Membership rows for owned fixture Member");
            if ("SETTLED".equals(intentStatus)) {
                assertEquals(1, acceptedPayments, "Expected exactly one accepted Payment for the settled Intent");
                assertEquals(1, successorIds.size(), "Expected exactly one successor Membership for the settled Intent");
                for (Integer successorId : successorIds) {
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE id=? AND member_id=? "
                            + "AND plan_id=? AND start_date=? AND benefit_snapshot_source='RENEWAL'",
                            Integer.class, successorId, memberId, planId, java.sql.Date.valueOf((String) item.get("sourceEndDate"))));
                    jdbc.update("DELETE FROM dbo.membership WHERE id=? AND member_id=? AND plan_id=?",
                            successorId, memberId, planId);
                }
            } else {
                assertTrue(List.of("OPEN", "CANCELLED").contains(intentStatus), "Unexpected fixture Renewal state; cleanup refused");
                assertEquals(0, acceptedPayments, "Unsettled fixture cannot contain an accepted Payment");
                assertTrue(successorIds.isEmpty(), "Unsettled fixture must not have an entitlement successor");
            }
            jdbc.update("UPDATE dbo.membership_renewal_intent SET intent_status='CANCELLED', settled_payment_id=NULL, "
                    + "settled_at=NULL, closed_at=SYSUTCDATETIME(), closed_reason='SOURCE_NO_LONGER_ELIGIBLE' "
                    + "WHERE id=? AND intent_status IN ('OPEN','SETTLED')", intentId);
            jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='RENEWAL_PAYMENT' AND entity_id IN "
                    + "(SELECT CAST(id AS varchar(30)) FROM dbo.payment WHERE renewal_intent_id=?)", intentId);
            jdbc.update("DELETE FROM dbo.payment WHERE renewal_intent_id=?", intentId);
            jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE id=?", intentId);
            jdbc.update("DELETE FROM dbo.membership WHERE id=? AND member_id=?", sourceId, memberId);
            jdbc.update("DELETE FROM dbo.notification WHERE user_id=?", memberId);
            jdbc.update("DELETE FROM dbo.membership_plan WHERE id=?", planId);
            jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id=?", memberId);
            jdbc.update("DELETE FROM dbo.users WHERE id=? AND email=?", memberId, email);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> baseline = (Map<String, Object>) manifest.get("baseline");
        Map<String, Object> after = snapshotProtectedRows();
        for (Map.Entry<String, Object> entry : baseline.entrySet()) {
            assertEquals(entry.getValue(), after.get(entry.getKey()),
                    "Original rows and persisted columns must match the pre-fixture manifest for " + entry.getKey());
        }
        for (Integer planId : ownedPlanIds) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan_upgrade WHERE from_plan_id=? OR to_plan_id=?",
                    Integer.class, planId, planId), "No Upgrade Path may reference a cleaned run-owned Plan");
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE name LIKE ? OR email LIKE ?",
                Integer.class, prefix + "%", "%@phase7d.test"));
        Files.delete(manifestPath);
        System.out.println("PHASE7D_FIXTURES_CLEANED count=" + fixtures.size()
                + " protectedHistoricalRowsAndColumns=UNCHANGED manifestDeleted=true");
    }

    private void cleanupOrphanedSetupRecords() {
        List<Map<String, Object>> orphanUsers = jdbc.query(
                "SELECT id, email, name FROM dbo.users WHERE name LIKE 'P7D-%' AND email LIKE '%@phase7d.test'",
                (rs, row) -> Map.of("id", rs.getInt("id"), "email", rs.getString("email"), "name", rs.getString("name")));
        List<Map<String, Object>> orphanPlans = jdbc.query(
                "SELECT id, name FROM dbo.membership_plan WHERE name LIKE 'P7D-%' AND description=N'Temporary Phase 7D Renewal browser fixture'",
                (rs, row) -> Map.of("id", rs.getInt("id"), "name", rs.getString("name")));
        assertEquals(orphanUsers.size(), orphanPlans.size(), "Orphan setup markers do not form exact user/plan pairs");
        for (Map<String, Object> user : orphanUsers) {
            int userId = (Integer) user.get("id");
            String email = (String) user.get("email");
            String name = (String) user.get("name");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE member_id=?", Integer.class, userId));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment WHERE member_id=?", Integer.class, userId));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_renewal_intent ri JOIN dbo.membership m ON m.id=ri.source_membership_id WHERE m.member_id=?", Integer.class, userId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE id=? AND email=? AND name=?", Integer.class, userId, email, name));
            jdbc.update("DELETE FROM dbo.notification WHERE user_id=?", userId);
            jdbc.update("DELETE FROM dbo.users WHERE id=? AND email=? AND name=?", userId, email, name);
        }
        for (Map<String, Object> plan : orphanPlans) {
            int planId = (Integer) plan.get("id");
            String planName = (String) plan.get("name");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE plan_id=?", Integer.class, planId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan WHERE id=? AND name=? AND description=N'Temporary Phase 7D Renewal browser fixture'", Integer.class, planId, planName));
            jdbc.update("DELETE FROM dbo.membership_plan WHERE id=? AND name=?", planId, planName);
        }
        System.out.println("PHASE7D_ORPHAN_SETUP_CLEANED users=" + orphanUsers.size() + " plans=" + orphanPlans.size());
    }

    private void inspectFixturePayments(Path manifestPath) throws Exception {
        assertTrue(Files.isRegularFile(manifestPath), "Fixture manifest missing");
        Map<String, Object> manifest = mapper.readValue(Files.readString(manifestPath), new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixtures = (List<Map<String, Object>>) manifest.get("fixtures");
        for (Map<String, Object> fixture : fixtures) {
            int intentId = ((Number) fixture.get("intentId")).intValue();
            int memberId = ((Number) fixture.get("memberId")).intValue();
            String expectedName = manifest.get("prefix") + "-" + fixture.get("label");
            Integer ownedMember = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM dbo.users WHERE id=? AND name=? AND email=?",
                    Integer.class, memberId, expectedName, fixture.get("email"));
            List<Map<String, Object>> intentState = jdbc.query(
                    "SELECT id, intent_status, settled_payment_id FROM dbo.membership_renewal_intent WHERE id=?",
                    (rs, row) -> Map.of("id", rs.getInt("id"), "status", rs.getString("intent_status"),
                            "settledPaymentId", rs.getObject("settled_payment_id") == null ? 0 : rs.getInt("settled_payment_id")),
                    intentId);
            List<Map<String, Object>> payments = jdbc.query(
                    "SELECT id, transaction_code, method, status, amount, created_at, attempt_valid_until, paid_at, "
                            + "renewal_verified_collection_amount, renewal_verified_collection_currency, renewal_paid_time_evidence_type, "
                            + "renewal_collection_verified_at, renewal_settlement_accepted_at, renewal_collection_conflict_detected_at, "
                            + "renewal_attempt_close_reason, CASE WHEN renewal_provider_transaction_reference IS NULL THEN 0 ELSE 1 END AS hasProviderRef "
                            + "FROM dbo.payment WHERE renewal_intent_id=? ORDER BY id",
                    (rs, row) -> Map.ofEntries(
                            Map.entry("id", rs.getInt("id")),
                            Map.entry("transactionCode", rs.getString("transaction_code")),
                            Map.entry("method", rs.getString("method")),
                            Map.entry("status", rs.getString("status")),
                            Map.entry("amount", rs.getBigDecimal("amount")),
                            Map.entry("createdAt", String.valueOf(rs.getObject("created_at"))),
                            Map.entry("deadline", String.valueOf(rs.getObject("attempt_valid_until"))),
                            Map.entry("paidAt", String.valueOf(rs.getObject("paid_at"))),
                            Map.entry("collectionAmount", String.valueOf(rs.getObject("renewal_verified_collection_amount"))),
                            Map.entry("currency", String.valueOf(rs.getObject("renewal_verified_collection_currency"))),
                            Map.entry("paidTimeType", String.valueOf(rs.getObject("renewal_paid_time_evidence_type"))),
                            Map.entry("collectionAt", String.valueOf(rs.getObject("renewal_collection_verified_at"))),
                            Map.entry("accepted", rs.getObject("renewal_settlement_accepted_at") != null),
                            Map.entry("conflict", rs.getObject("renewal_collection_conflict_detected_at") != null),
                            Map.entry("closeReason", String.valueOf(rs.getObject("renewal_attempt_close_reason"))),
                            Map.entry("hasProviderRef", rs.getInt("hasProviderRef"))), intentId);
            List<Map<String, Object>> memberRows = jdbc.query(
                    "SELECT id, status, start_date, end_date, renewal_intent_id FROM dbo.membership WHERE member_id=? ORDER BY id",
                    (rs, row) -> Map.of("id", rs.getInt("id"), "status", rs.getString("status"),
                            "start", rs.getDate("start_date").toString(), "end", rs.getDate("end_date").toString(),
                            "intentId", rs.getObject("renewal_intent_id") == null ? 0 : rs.getInt("renewal_intent_id")), memberId);
            System.out.println("PHASE7D_PAYMENT_DIAGNOSTIC fixture=" + fixture.get("label") + " memberId="
                    + memberId + " markerOwned=" + ownedMember + " intent=" + intentState + " payments=" + payments
                    + " memberships=" + memberRows);
        }
    }

    private Map<String, Object> snapshotProtectedRows() throws Exception {
        Map<String, Object> result = new TreeMap<>();
        try (Connection connection = dataSource.getConnection()) {
            Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyIdentity(connection);
            for (String table : TABLES) {
                Map<Short, String> keyColumns = new TreeMap<>();
                DatabaseMetaData metadata = connection.getMetaData();
                try (ResultSet keys = metadata.getPrimaryKeys(null, "dbo", table)) {
                    while (keys.next()) keyColumns.put(keys.getShort("KEY_SEQ"), keys.getString("COLUMN_NAME"));
                }
                assertFalse(keyColumns.isEmpty(), "No PK metadata for dbo." + table);
                String orderBy = String.join(",", keyColumns.values().stream().map(n -> "[" + n + "]").toList());
                Map<String, Object> rows = new TreeMap<>();
                try (Statement statement = connection.createStatement();
                        ResultSet rs = statement.executeQuery("SELECT * FROM dbo.[" + table + "] ORDER BY " + orderBy)) {
                    int count = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        Map<String, String> columnHashes = new TreeMap<>();
                        StringBuilder rowKey = new StringBuilder();
                        for (String key : keyColumns.values()) {
                            if (rowKey.length() > 0) rowKey.append('|');
                            rowKey.append(canonical(rs.getObject(key)));
                        }
                        for (int i = 1; i <= count; i++) {
                            String name = rs.getMetaData().getColumnLabel(i);
                            columnHashes.put(name, digest(name + "\u0000" + canonical(rs.getObject(i))));
                        }
                        assertNull(rows.put(rowKey.toString(), columnHashes), "Duplicate key in dbo." + table);
                    }
                }
                result.put(table, rows);
            }
        }
        return result;
    }

    private String canonical(Object value) {
        if (value == null) return "<NULL>";
        if (value instanceof byte[] bytes) return "B64:" + Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.math.BigDecimal decimal) return "DEC:" + decimal.toPlainString();
        if (value instanceof java.sql.Timestamp timestamp) return "TS:" + timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return "D:" + date.toLocalDate();
        if (value instanceof java.sql.Time time) return "T:" + time.toLocalTime();
        return value.getClass().getName() + ":" + value;
    }

    private String digest(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private Path requiredManifestPath() {
        String configured = System.getProperty("phase7d.fixture.manifest");
        if (configured == null || configured.isBlank()) fail("phase7d.fixture.manifest is required");
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
