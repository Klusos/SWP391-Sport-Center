package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import jakarta.persistence.EntityManager;

/** Clone-pinned mapping validation only; no repositories are used to write fixtures. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDemo_Phase5B4B2C1B_Rehearsal_20261121;encrypt=true;trustServerCertificate=true",
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "sportcenter.scheduling.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true",
        "debug=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase5B4B2C1BHibernateValidationIT.CloneGuard.class)
class Phase5B4B2C1BHibernateValidationIT {
    static final String SERVER = "LAPTOP-416VBDLK";
    static final String DATABASE = "SportCenterDemo_Phase5B4B2C1B_Rehearsal_20261121";
    static final String URL = "jdbc:sqlserver://127.0.0.1:1433;databaseName=" + DATABASE
            + ";encrypt=true;trustServerCertificate=true";

    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext applicationContext;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void currentJpaMappingsValidateAgainstTheExactRehearsalClone() throws Exception {
        CloneGuard.verifyRuntime(dataSource, environment, applicationContext);
        assertNotNull(entityManager.getMetamodel().entity(Payment.class));
        assertNotNull(entityManager.getMetamodel().entity(MembershipRenewalIntent.class));
    }

    @Test
    void renewalCollectionSchemaAndHistoricalBaselineAvailabilityAreReportedReadOnly() throws Exception {
        CloneGuard.verifyRuntime(dataSource, environment, applicationContext);

        Map<String, String> expectedTypes = Map.of(
                "renewal_provider_transaction_reference", "varchar:64:nullable",
                "renewal_verified_collection_amount", "decimal:12:2:nullable",
                "renewal_verified_collection_currency", "varchar:3:nullable",
                "renewal_paid_time_evidence_type", "varchar:24:nullable",
                "renewal_collection_verified_at", "datetime2:6:nullable",
                "renewal_collection_conflict_detected_at", "datetime2:6:nullable");
        Map<String, String> actualTypes = new HashMap<>();
        jdbcTemplate.query("""
                SELECT c.name, t.name, c.max_length, c.precision, c.scale, c.is_nullable
                FROM sys.columns c
                JOIN sys.types t ON t.user_type_id = c.user_type_id
                WHERE c.object_id = OBJECT_ID(N'dbo.payment')
                  AND c.name IN (N'renewal_provider_transaction_reference',
                                 N'renewal_verified_collection_amount',
                                 N'renewal_verified_collection_currency',
                                 N'renewal_paid_time_evidence_type',
                                 N'renewal_collection_verified_at',
                                 N'renewal_collection_conflict_detected_at')
                """, rs -> {
            String type = rs.getString(2);
            String actual = switch (type) {
                case "varchar" -> type + ":" + rs.getInt(3) + ":nullable";
                case "decimal" -> type + ":" + rs.getInt(4) + ":" + rs.getInt(5) + ":nullable";
                case "datetime2" -> type + ":" + rs.getInt(5) + ":nullable";
                default -> type + ":unexpected";
            };
            if (!rs.getBoolean(6)) actual = actual.replace(":nullable", ":not-null");
            actualTypes.put(rs.getString(1), actual);
        });
        org.junit.jupiter.api.Assertions.assertEquals(expectedTypes, actualTypes,
                "Renewal collection Payment columns must match the rehearsed schema");

        Map<String, Boolean> requiredChecks = jdbcTemplate.query("""
                SELECT name, CONVERT(int, is_disabled) | (CONVERT(int, is_not_trusted) * 2)
                FROM sys.check_constraints
                WHERE parent_object_id = OBJECT_ID(N'dbo.payment')
                  AND name IN (N'ck_payment_renewal_collection_evidence',
                               N'ck_payment_renewal_settlement_has_collection')
                """, rs -> {
            Map<String, Boolean> checks = new HashMap<>();
            while (rs.next()) checks.put(rs.getString(1), rs.getInt(2) == 0);
            return checks;
        });
        org.junit.jupiter.api.Assertions.assertEquals(Map.of(
                "ck_payment_renewal_collection_evidence", true,
                "ck_payment_renewal_settlement_has_collection", true), requiredChecks,
                "Renewal collection checks must exist, be enabled, and be trusted");

        Integer providerIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys.indexes
                WHERE object_id = OBJECT_ID(N'dbo.payment')
                  AND name = N'ux_payment_renewal_provider_transaction_reference'
                  AND is_unique = 1 AND is_disabled = 0 AND filter_definition IS NOT NULL
                """, Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, providerIndexCount,
                "Provider reference uniqueness index must be active and filtered");

        Map<String, Long> protectedCounts = new HashMap<>();
        for (String table : new String[] { "membership", "payment", "membership_upgrade",
                "membership_cancellation_request" }) {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT_BIG(*) FROM dbo.[" + table + "]", Long.class);
            protectedCounts.put(table, count == null ? -1L : count);
        }
        String baselineObjects = String.join(",", jdbcTemplate.queryForList("""
                SELECT s.name + N'.' + t.name
                FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE t.name LIKE N'%baseline%' OR t.name LIKE N'%manifest%'
                ORDER BY s.name, t.name
                """, String.class));
        System.out.println("PHASE5B4B2C2V_PREFLIGHT protectedRowCounts=" + protectedCounts
                + " persistentBaselineObjects=" + (baselineObjects.isBlank() ? "none" : baselineObjects));
    }

    @Test
    void reportsOnlyMarkedFixturesRetainedByTheInterruptedVerificationRun() throws Exception {
        CloneGuard.verifyRuntime(dataSource, environment, applicationContext);
        List<Integer> userIds = jdbcTemplate.queryForList("""
                SELECT id FROM dbo.users
                WHERE email LIKE N'%@phase5b42cv.test' AND name LIKE N'P5B42CV-%'
                ORDER BY id
                """, Integer.class);
        List<Integer> planIds = jdbcTemplate.queryForList("""
                SELECT id FROM dbo.membership_plan WHERE name LIKE N'P5B42CV-%' ORDER BY id
                """, Integer.class);
        List<Integer> membershipIds = jdbcTemplate.queryForList("""
                SELECT m.id FROM dbo.membership m
                WHERE (m.member_id IN (SELECT id FROM dbo.users
                                      WHERE email LIKE N'%@phase5b42cv.test' AND name LIKE N'P5B42CV-%')
                       AND m.plan_id IN (SELECT id FROM dbo.membership_plan WHERE name LIKE N'P5B42CV-%'))
                   OR m.renewal_intent_id IN (
                       SELECT ri.id FROM dbo.membership_renewal_intent ri
                       JOIN dbo.membership src ON src.id=ri.source_membership_id
                       WHERE src.member_id IN (SELECT id FROM dbo.users
                                              WHERE email LIKE N'%@phase5b42cv.test' AND name LIKE N'P5B42CV-%'))
                ORDER BY m.id
                """, Integer.class);
        List<Integer> intentIds = jdbcTemplate.queryForList("""
                SELECT ri.id FROM dbo.membership_renewal_intent ri
                JOIN dbo.membership src ON src.id=ri.source_membership_id
                WHERE src.member_id IN (SELECT id FROM dbo.users
                                       WHERE email LIKE N'%@phase5b42cv.test' AND name LIKE N'P5B42CV-%')
                ORDER BY ri.id
                """, Integer.class);
        List<Integer> paymentIds = jdbcTemplate.queryForList("""
                SELECT p.id FROM dbo.payment p
                WHERE p.renewal_intent_id IN (
                    SELECT ri.id FROM dbo.membership_renewal_intent ri
                    JOIN dbo.membership src ON src.id=ri.source_membership_id
                    WHERE src.member_id IN (SELECT id FROM dbo.users
                                           WHERE email LIKE N'%@phase5b42cv.test' AND name LIKE N'P5B42CV-%'))
                ORDER BY p.id
                """, Integer.class);
        System.out.println("PHASE5B4B2C2V_OWNED_FIXTURE_INVENTORY userIds=" + userIds
                + " planIds=" + planIds + " membershipIds=" + membershipIds
                + " intentIds=" + intentIds + " paymentIds=" + paymentIds);
        System.out.println("PHASE5B4B2C2V_OWNERSHIP_GRAPH users=" + jdbcTemplate.queryForList("""
                SELECT id, name, email FROM dbo.users
                WHERE email LIKE N'%@phase5b42cv.test' AND name LIKE N'P5B42CV-%' ORDER BY id
                """));
        System.out.println("PHASE5B4B2C2V_OWNERSHIP_GRAPH plans=" + jdbcTemplate.queryForList("""
                SELECT id, name FROM dbo.membership_plan WHERE name LIKE N'P5B42CV-%' ORDER BY id
                """));
        System.out.println("PHASE5B4B2C2V_OWNERSHIP_GRAPH memberships=" + jdbcTemplate.queryForList("""
                SELECT id, member_id, plan_id, renewal_intent_id, purchase_payment_id, status
                FROM dbo.membership
                WHERE id IN (5016,5018,5020,5021,5022) ORDER BY id
                """));
        System.out.println("PHASE5B4B2C2V_OWNERSHIP_GRAPH intents=" + jdbcTemplate.queryForList("""
                SELECT id, source_membership_id, target_plan_id, settled_payment_id, intent_status
                FROM dbo.membership_renewal_intent WHERE id IN (2,3,4,5) ORDER BY id
                """));
        System.out.println("PHASE5B4B2C2V_OWNERSHIP_GRAPH payments=" + jdbcTemplate.queryForList("""
                SELECT id, member_id, renewal_intent_id, transaction_code, status
                FROM dbo.payment WHERE id IN (11046,11047,11048,11049) ORDER BY id
                """));
    }

    public static final class CloneGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            Environment environment = context.getEnvironment();
            if (!URL.equals(environment.getProperty("spring.datasource.url"))
                    || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !"org.hibernate.dialect.SQLServerDialect".equals(
                            environment.getProperty("spring.jpa.database-platform"))
                    || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.scheduling.enabled"))
                    || !"mock".equals(environment.getProperty("app.payment.provider"))
                    || !"true".equalsIgnoreCase(
                            environment.getProperty("app.payment.mock-gateway.enabled"))) {
                throw new IllegalStateException("Phase 5B clone validation refused: configuration is not exact and read-only");
            }
            String username = environment.getProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password");
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                throw new IllegalStateException("Phase 5B clone validation refused: datasource credentials are unavailable");
            }
            Properties properties = new Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            properties.setProperty("encrypt", "true");
            properties.setProperty("trustServerCertificate", "true");
            try (Connection connection = DriverManager.getConnection(URL, properties)) {
                verifyIdentity(connection);
            } catch (Exception failure) {
                throw new IllegalStateException("Phase 5B clone identity verification failed before Hibernate", failure);
            }
        }

        static void verifyIdentity(Connection connection) throws Exception {
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "SELECT @@SERVERNAME, SERVERPROPERTY('InstanceName'), DB_NAME(), "
                                    + "CONVERT(nvarchar(30), DATABASEPROPERTYEX(DB_NAME(), 'Status')), "
                                    + "HAS_DBACCESS(DB_NAME())")) {
                if (!result.next()
                        || !SERVER.equals(result.getString(1))
                        || result.getString(2) != null
                        || !DATABASE.equals(result.getString(3))
                        || !"ONLINE".equalsIgnoreCase(result.getString(4))
                        || result.getInt(5) != 1
                        || result.next()) {
                    throw new IllegalStateException("Connected SQL Server identity is not the authorized clone");
                }
            }
        }

        static void verifyRuntime(DataSource dataSource, Environment environment, ApplicationContext context)
                throws Exception {
            if (!URL.equals(environment.getProperty("spring.datasource.url"))
                    || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw new IllegalStateException("Effective datasource/schema mode changed after initialization");
            }
            assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                    "Scheduling infrastructure must be absent in the JPA test slice");
            try (Connection connection = dataSource.getConnection()) {
                verifyIdentity(connection);
            }
        }
    }
}
