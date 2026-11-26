package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Properties;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgradePath;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.dto.LoginRequest;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradePathRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.MembershipRenewalIntentService;
import group5.example.sportCenter.service.AuthService;

/** Opt-in only, clone-pinned fixture and controlled Renewal lifecycle helper for final browser QA. */
@SpringBootTest(properties = {
        "spring.datasource.url=" + Phase8CJPtOutcomeSqlServerIT.CloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}", "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false", "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false", "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock", "app.payment.mock-gateway.enabled=true", "debug=false",
        "logging.level.root=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase8CJPtOutcomeSqlServerIT.CloneGuard.class)
class Phase9MemberBrowserFixtureIT {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Path MANIFEST = Path.of(System.getProperty("java.io.tmpdir"),
            "sportcenter-browser-qa", "phase9-member-flow.properties");

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRepository memberships;
    @Autowired PaymentRepository payments;
    @Autowired MembershipUpgradePathRepository paths;
    @Autowired MembershipRenewalIntentService renewalIntents;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void explicitlyRequestedFixtureOperation() throws Exception {
        assumeTrue(System.getProperty("phase9.memberFlow.operation") != null,
                "Fixture helper is inert unless its explicit operation property is supplied");
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        switch (System.getProperty("phase9.memberFlow.operation")) {
            case "setup" -> setup();
            case "verify-passwords" -> verifyPasswords();
            case "renewal-lifecycle" -> createRenewalIntent();
            case "browser-renewal-lifecycle" -> createBrowserRenewalIntent();
            case "cleanup" -> cleanup();
            default -> fail("Unsupported phase9.memberFlow.operation");
        }
    }

    private void setup() throws Exception {
        assertFalse(Files.exists(MANIFEST), "Refusing to overwrite an existing synthetic fixture manifest");
        String marker = "P9MF-" + UUID.randomUUID().toString().substring(0, 10);
        String upgradePassword = UUID.randomUUID().toString().replace("-", "") + "Aa7!";
        String renewalPassword = UUID.randomUUID().toString().replace("-", "") + "Bb8!";
        User upgradeMember = createMember(marker + "-upgrade", upgradePassword);
        User renewalMember = createMember(marker + "-renewal", renewalPassword);
        MembershipPlan upgradeSourcePlan = createPlan(marker + "-upgrade-source", "500000.00", 1, 10, 5);
        MembershipPlan upgradeTargetPlan = createPlan(marker + "-upgrade-target", "900000.00", 1, 25, 15);
        MembershipPlan renewalPlan = createPlan(marker + "-renewal", "725000.00", 1, 15, 8);
        User receptionist = users.findByEmail("demo.receptionist@sportcenter.test").orElseThrow();
        LocalDate today = LocalDate.now(VN);
        Membership upgradeSource = createActiveMembership(upgradeMember, upgradeSourcePlan,
                today.minusDays(20), today.plusDays(20), false, receptionist, marker + "-UP");
        Membership renewalSource = createActiveMembership(renewalMember, renewalPlan,
                today.minusDays(28), today.plusDays(2), false, receptionist, marker + "-RE");
        MembershipUpgradePath path = new MembershipUpgradePath();
        path.setFromPlan(upgradeSourcePlan);
        path.setToPlan(upgradeTargetPlan);
        path.setActive(true);
        path.setCreatedAt(LocalDateTime.now(VN));
        path.setUpdatedAt(LocalDateTime.now(VN));
        path = paths.saveAndFlush(path);

        Properties m = new Properties();
        m.setProperty("marker", marker);
        m.setProperty("upgrade.memberId", upgradeMember.getId().toString());
        m.setProperty("upgrade.email", upgradeMember.getEmail());
        m.setProperty("upgrade.password", upgradePassword);
        m.setProperty("upgrade.sourceMembershipId", upgradeSource.getId().toString());
        m.setProperty("upgrade.sourcePlanId", upgradeSourcePlan.getId().toString());
        m.setProperty("upgrade.targetPlanId", upgradeTargetPlan.getId().toString());
        m.setProperty("upgrade.pathId", path.getId().toString());
        m.setProperty("upgrade.sourceEnd", upgradeSource.getEndDate().toString());
        m.setProperty("renewal.memberId", renewalMember.getId().toString());
        m.setProperty("renewal.email", renewalMember.getEmail());
        m.setProperty("renewal.password", renewalPassword);
        m.setProperty("renewal.sourceMembershipId", renewalSource.getId().toString());
        m.setProperty("renewal.planId", renewalPlan.getId().toString());
        m.setProperty("renewal.sourceEnd", renewalSource.getEndDate().toString());
        Files.createDirectories(MANIFEST.getParent());
        try (var out = Files.newOutputStream(MANIFEST, java.nio.file.StandardOpenOption.CREATE_NEW)) {
            m.store(out, "Local QA credentials and exact run-owned IDs; do not commit or report contents");
        }
        System.out.println("PHASE9_MEMBER_FIXTURES_CREATED upgradeMemberId=" + upgradeMember.getId()
                + " upgradeMembershipId=" + upgradeSource.getId() + " renewalMemberId=" + renewalMember.getId()
                + " renewalMembershipId=" + renewalSource.getId() + " pathId=" + path.getId()
                + " sourceEndDates=" + upgradeSource.getEndDate() + "/" + renewalSource.getEndDate());
    }

    private User createMember(String name, String password) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@phase9.test");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private MembershipPlan createPlan(String name, String price, int months, int courseDiscount, int ptDiscount) {
        MembershipPlan plan = new MembershipPlan();
        plan.setName(name);
        plan.setDescription("Synthetic final Member browser acceptance fixture");
        plan.setPrice(new BigDecimal(price));
        plan.setDurationMonths(months);
        plan.setCourseDiscountPercent(BigDecimal.valueOf(courseDiscount));
        plan.setPersonalTrainingDiscountPercent(BigDecimal.valueOf(ptDiscount));
        plan.setAllowPersonalTrainingPlan(true);
        plan.setAllowOneOnOneCoaching(true);
        plan.setStatus("ACTIVE");
        return plans.saveAndFlush(plan);
    }

    private Membership createActiveMembership(User member, MembershipPlan plan,
            LocalDate start, LocalDate end, boolean autoRenew, User receptionist, String paymentMarker) {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(plan.getPrice());
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId());
        payment.setAutoRenew(autoRenew);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode(paymentMarker + "-BASE");
        payment.setProcessedBy(receptionist);
        payment.setNote("Synthetic baseline purchase fixture");
        payment.setPaidAt(LocalDateTime.now(VN));
        payment.setMembershipPlanNameSnapshot(plan.getName());
        payment.setMembershipPlanDescriptionSnapshot(plan.getDescription());
        payment.setMembershipPlanPriceSnapshot(plan.getPrice());
        payment.setMembershipDurationMonthsSnapshot(plan.getDurationMonths());
        payment.setMembershipCourseDiscountPercentSnapshot(plan.getCourseDiscountPercent());
        payment.setMembershipPtDiscountPercentSnapshot(plan.getPersonalTrainingDiscountPercent());
        payment.setMembershipAllowPtPlanSnapshot(plan.getAllowPersonalTrainingPlan());
        payment.setMembershipAllowOneOnOneSnapshot(plan.getAllowOneOnOneCoaching());
        payment.setMembershipQuoteCapturedAt(LocalDateTime.now(VN));
        payment = payments.saveAndFlush(payment);
        Membership membership = new Membership();
        membership.setMember(member);
        membership.setPlan(plan);
        membership.setAutoRenew(autoRenew);
        membership.setStartDate(start);
        membership.setEndDate(end);
        membership.setStatus("ACTIVE");
        membership.setPurchasePayment(payment);
        membership.setBenefitSnapshotStatus("COMPLETE");
        membership.setBenefitSnapshotSource("PURCHASE");
        membership.setPlanNameSnapshot(plan.getName());
        membership.setPlanDescriptionSnapshot(plan.getDescription());
        membership.setPlanPriceSnapshot(plan.getPrice());
        membership.setPlanDurationMonthsSnapshot(plan.getDurationMonths());
        membership.setCourseDiscountPercentSnapshot(plan.getCourseDiscountPercent());
        membership.setPtDiscountPercentSnapshot(plan.getPersonalTrainingDiscountPercent());
        membership.setAllowPtPlanSnapshot(plan.getAllowPersonalTrainingPlan());
        membership.setAllowOneOnOneSnapshot(plan.getAllowOneOnOneCoaching());
        membership.setPaidValueBasisStatus("VERIFIED");
        membership.setPaidValueBasisAmount(plan.getPrice());
        membership.setPaidValueBasisSource("PURCHASE_PAYMENT");
        return memberships.saveAndFlush(membership);
    }

    private void createRenewalIntent() throws Exception {
        Properties m = readManifest();
        int sourceId = Integer.parseInt(m.getProperty("renewal.sourceMembershipId"));
        Membership source = memberships.findById(sourceId).orElseThrow();
        assertTrue(source.getEndDate().equals(LocalDate.now(VN).plusDays(2)));
        assertTrue(source.getAutoRenew(), "Member must opt in through the UI before lifecycle creation");
        var first = renewalIntents.createForScheduler(sourceId);
        assertEquals(MembershipRenewalIntentService.Outcome.CREATED, first.outcome());
        assertEquals(source.getEndDate(), first.intent().getSuccessorStartDate());
        var retry = renewalIntents.createForScheduler(sourceId);
        assertNotEquals(MembershipRenewalIntentService.Outcome.CREATED, retry.outcome());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_renewal_intent WHERE source_membership_id=?",
                Integer.class, sourceId));
        m.setProperty("renewal.intentId", first.intent().getId().toString());
        try (var out = Files.newOutputStream(MANIFEST, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            m.store(out, "Local QA credentials and exact run-owned IDs; do not commit or report contents");
        }
        System.out.println("PHASE9_RENEWAL_LIFECYCLE_CREATED intentId=" + first.intent().getId()
                + " outcome=" + first.outcome() + " duplicateRetry=" + retry.outcome()
                + " successorStart=" + first.intent().getSuccessorStartDate());
    }

    private void createBrowserRenewalIntent() {
        int sourceId = Integer.parseInt(System.getProperty("phase9.memberFlow.sourceMembershipId", "0"));
        Membership source = memberships.findById(sourceId).orElseThrow();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership m JOIN dbo.users u ON u.id=m.member_id "
                + "WHERE m.id=? AND u.name='P9U2-20261125 Member' AND u.email='p9u2-20261125@sportcenter.test'",
                Integer.class, sourceId));
        assertEquals(LocalDate.now(VN).plusDays(2), source.getEndDate());
        assertTrue(source.getAutoRenew(), "Member must opt in through the UI before lifecycle creation");
        var first = renewalIntents.createForScheduler(sourceId);
        assertEquals(MembershipRenewalIntentService.Outcome.CREATED, first.outcome());
        assertEquals(source.getEndDate(), first.intent().getSuccessorStartDate());
        var retry = renewalIntents.createForScheduler(sourceId);
        assertNotEquals(MembershipRenewalIntentService.Outcome.CREATED, retry.outcome());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_renewal_intent WHERE source_membership_id=?",
                Integer.class, sourceId));
        System.out.println("PHASE9_BROWSER_RENEWAL_LIFECYCLE_CREATED sourceMembershipId=" + sourceId
                + " intentId=" + first.intent().getId() + " outcome=" + first.outcome()
                + " duplicateRetry=" + retry.outcome() + " successorStart=" + first.intent().getSuccessorStartDate());
    }

    private Properties readManifest() throws Exception {
        assertTrue(Files.isRegularFile(MANIFEST), "Exact run-owned manifest required");
        Properties m = new Properties();
        try (var in = Files.newInputStream(MANIFEST)) { m.load(in); }
        return m;
    }

    private void verifyPasswords() throws Exception {
        Properties m = readManifest();
        for (String key : new String[] { "upgrade", "renewal" }) {
            User user = users.findByEmail(m.getProperty(key + ".email")).orElseThrow();
            assertEquals(Integer.parseInt(m.getProperty(key + ".memberId")), user.getId());
            assertTrue(passwordEncoder.matches(m.getProperty(key + ".password"), user.getPasswordHash()));
            assertEquals("ACTIVE", user.getStatus());
            LoginRequest request = new LoginRequest();
            request.setEmail(m.getProperty(key + ".email"));
            request.setPassword(m.getProperty(key + ".password"));
            assertEquals(user.getId(), authService.login(request).getUserId());
        }
        System.out.println("PHASE9_SYNTHETIC_LOGIN_CREDENTIALS_MATCH=true");
    }

    private void cleanup() throws Exception {
        Properties m = readManifest();
        String marker = m.getProperty("marker");
        int upgradeMember = Integer.parseInt(m.getProperty("upgrade.memberId"));
        int renewalMember = Integer.parseInt(m.getProperty("renewal.memberId"));
        int sourcePlan = Integer.parseInt(m.getProperty("upgrade.sourcePlanId"));
        int targetPlan = Integer.parseInt(m.getProperty("upgrade.targetPlanId"));
        int renewalPlan = Integer.parseInt(m.getProperty("renewal.planId"));
        int sourceId = Integer.parseInt(m.getProperty("upgrade.sourceMembershipId"));
        int renewalSourceId = Integer.parseInt(m.getProperty("renewal.sourceMembershipId"));
        int pathId = Integer.parseInt(m.getProperty("upgrade.pathId"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE id IN (?,?) AND email LIKE ? AND name LIKE ?",
                Integer.class, upgradeMember, renewalMember, "%@phase9.test", marker + "%"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership_plan_upgrade WHERE id=? AND from_plan_id=? AND to_plan_id=?",
                Integer.class, pathId, sourcePlan, targetPlan));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE member_id IN (?,?) AND plan_id IN (?,?,?)",
                Integer.class, upgradeMember, renewalMember, sourcePlan, targetPlan, renewalPlan) >= 2);
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id IN (?,?)", upgradeMember, renewalMember);
        jdbc.update("DELETE FROM dbo.audit_log WHERE (entity_type='MEMBERSHIP' AND entity_id IN "
                + "(SELECT CAST(id AS varchar(30)) FROM dbo.membership WHERE member_id IN (?,?))) OR "
                + "(entity_type='PAYMENT' AND entity_id IN (SELECT CAST(id AS varchar(30)) FROM dbo.payment WHERE member_id IN (?,?)))",
                upgradeMember, renewalMember, upgradeMember, renewalMember);
        jdbc.update("DELETE FROM dbo.notification WHERE user_id IN (?,?)", upgradeMember, renewalMember);
        jdbc.update("DELETE FROM dbo.membership_upgrade WHERE payment_id IN (SELECT id FROM dbo.payment WHERE member_id IN (?,?))",
                upgradeMember, renewalMember);
        jdbc.update("UPDATE dbo.membership_renewal_intent SET settled_payment_id=NULL WHERE source_membership_id=?", renewalSourceId);
        jdbc.update("DELETE FROM dbo.membership_renewal_intent WHERE source_membership_id=?", renewalSourceId);
        jdbc.update("DELETE FROM dbo.membership WHERE member_id IN (?,?) AND plan_id IN (?,?,?)",
                upgradeMember, renewalMember, sourcePlan, targetPlan, renewalPlan);
        jdbc.update("DELETE FROM dbo.payment WHERE member_id IN (?,?)", upgradeMember, renewalMember);
        jdbc.update("DELETE FROM dbo.membership_plan_upgrade WHERE id=?", pathId);
        jdbc.update("DELETE FROM dbo.membership_plan WHERE id IN (?,?,?)", sourcePlan, targetPlan, renewalPlan);
        jdbc.update("DELETE FROM dbo.users WHERE id IN (?,?) AND name LIKE ? AND email LIKE ?",
                upgradeMember, renewalMember, marker + "%", "%@phase9.test");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users WHERE id IN (?,?)", Integer.class,
                upgradeMember, renewalMember));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.membership WHERE id IN (?,?)",
                Integer.class, sourceId, renewalSourceId));
        Files.delete(MANIFEST);
        System.out.println("PHASE9_MEMBER_FIXTURES_CLEANED marker=" + marker + " originalRowsPreserved=true manifestDeleted=true");
    }
}
