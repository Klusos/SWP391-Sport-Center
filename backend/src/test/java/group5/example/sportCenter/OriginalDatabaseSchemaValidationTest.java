package group5.example.sportCenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Opt-in, validation-only JPA bootstrap for the isolated Phase 8C-H original database.
 * This test deliberately does not component-scan the SportCenter application.
 */
class OriginalDatabaseSchemaValidationTest {

    private static final String EXPECTED_SERVER = "LAPTOP-416VBDLK";
    private static final String EXPECTED_PRODUCT_VERSION = "17.0.1155.3";
    private static final String EXPECTED_DATABASE = "SportCenterDemo";
    private static final int EXPECTED_DATABASE_ID = 5;
    private static final int PROTECTED_CLONE_DATABASE_ID = 12;
    private static final String EXPECTED_JDBC_URL = "jdbc:sqlserver://127.0.0.1:1433;"
            + "databaseName=SportCenterDemo;encrypt=true;trustServerCertificate=true";
    private static final String ENTITY_PACKAGE = "group5.example.sportCenter.entity";
    private static final String AUTHORIZATION_OPT_IN = "sportcenter.phase8ch.authorized";
    private static final String EXECUTION_OPT_IN = "sportcenter.phase8ch.execute";
    private static volatile DatabaseSnapshot preHibernateSnapshot;

    @Test
    void validatesEveryMappedEntityAgainstTheExplicitOriginalOnlyWhenSeparatelyAuthorized() {
        // These assumptions are deliberately the first executable statements. A normal test run
        // must skip before reading credentials, creating Spring, or opening any connection.
        assumeTrue("true".equalsIgnoreCase(System.getProperty(AUTHORIZATION_OPT_IN)),
                "Requires separate Phase 8C-H authorization flag");
        assumeTrue("true".equalsIgnoreCase(System.getProperty(EXECUTION_OPT_IN)),
                "Requires execution-specific Hibernate validation opt-in");

        ValidationIdentityGuard identityGuard = new ValidationIdentityGuard();
        SpringApplication application = new SpringApplication(ValidationOnlyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.addInitializers(identityGuard);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.config.name=phase8c-h-no-application-config")) {
            assertEquals(EXPECTED_JDBC_URL, context.getEnvironment().getProperty("spring.datasource.url"),
                    "Effective Spring datasource must be pinned to the approved original database");
            assertEquals("validate",
                    context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("validate",
                    context.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"));
            assertEquals("never", context.getEnvironment().getProperty("spring.sql.init.mode"));
            assertFalse(Boolean.parseBoolean(context.getEnvironment().getProperty("spring.jpa.generate-ddl")));
            assertNoApplicationStartupComponents(context);

            DataSource dataSource = context.getBean(DataSource.class);
            verifyConnectedOriginalIdentity(dataSource);

            EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
            assertNotNull(entityManagerFactory);
            assertAllEntityClassesAreManaged(entityManagerFactory);

            DatabaseSnapshot after = readSnapshot(dataSource);
            assertEquals(preHibernateSnapshot, after,
                    "Read-only database metadata and aggregate indicators must match before and after validation");
            System.out.println("F8H_DATABASE_SNAPSHOT prePost=match tables=" + after.tableCounts().size()
                    + " ptStatuses=" + after.ptStatusCounts().size()
                    + " tableCounts=" + after.tableCounts()
                    + " ptStatusCounts=" + after.ptStatusCounts()
                    + " schemaFingerprint=" + after.schemaFingerprint());
        }
    }

    private static void assertNoApplicationStartupComponents(ConfigurableApplicationContext context) {
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                "Scheduling infrastructure must not be registered");
        assertTrue(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty(),
                "ApplicationRunner beans must not be registered");
        assertTrue(context.getBeansOfType(org.springframework.boot.CommandLineRunner.class).isEmpty(),
                "CommandLineRunner beans must not be registered");

        for (String beanName : context.getBeanDefinitionNames()) {
            String className = context.getType(beanName, false) == null
                    ? context.getBeanFactory().getBeanDefinition(beanName).getBeanClassName()
                    : context.getType(beanName, false).getName();
            if (className == null) continue;
            assertFalse(className.startsWith("group5.example.sportCenter.service."),
                    "Business service unexpectedly registered: " + className);
            assertFalse(className.startsWith("group5.example.sportCenter.controller."),
                    "Business controller unexpectedly registered: " + className);
            assertFalse(className.startsWith("group5.example.sportCenter.scheduler."),
                    "Business scheduler unexpectedly registered: " + className);
            assertFalse(className.startsWith("group5.example.sportCenter.config."),
                    "Application configuration unexpectedly registered: " + className);
        }
    }

    private static void assertAllEntityClassesAreManaged(EntityManagerFactory entityManagerFactory) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<String> discoveredEntityTypes = new HashSet<>();
        scanner.findCandidateComponents(ENTITY_PACKAGE)
                .forEach(beanDefinition -> discoveredEntityTypes.add(beanDefinition.getBeanClassName()));

        Set<String> managedEntityTypes = new HashSet<>();
        entityManagerFactory.getMetamodel().getEntities()
                .forEach(entity -> managedEntityTypes.add(entity.getJavaType().getName()));

        assertFalse(discoveredEntityTypes.isEmpty(), "No entity classes were discovered in the entity package");
        assertEquals(discoveredEntityTypes, managedEntityTypes,
                "Hibernate must manage every current @Entity class discovered in the entity package");
        System.out.println("F8H_ENTITY_COVERAGE discovered=" + discoveredEntityTypes.size()
                + " managed=" + managedEntityTypes.size() + " missing=0 unexpected=0");
    }

    private static void verifyConnectedOriginalIdentity(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            verifyConnection(connection, "postHibernate");
        } catch (Exception exception) {
            throw new IllegalStateException("Effective Hibernate DataSource failed the original database identity guard", exception);
        }
    }

    private static DatabaseSnapshot readSnapshot(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            verifyConnection(connection, "postHibernate");
            return readSnapshot(connection);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not capture guarded original database snapshot", exception);
        }
    }

    private static void verifyConnection(Connection connection, String stage) throws Exception {
        connection.setReadOnly(true);
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT CAST(SERVERPROPERTY('ServerName') AS nvarchar(128)) AS server_name,
                               @@SERVERNAME AS configured_server_name,
                               DB_NAME() AS database_name,
                               DB_ID() AS database_id,
                               CONVERT(nvarchar(30), DATABASEPROPERTYEX(DB_NAME(), 'Status')) AS database_state,
                               HAS_DBACCESS(DB_NAME()) AS has_database_access,
                               CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(30)) AS product_version
                        FROM sys.databases WHERE database_id = DB_ID()
                        """)) {
            if (!result.next()) {
                throw new IllegalStateException("SQL Server identity query returned no current database row");
            }
            String serverName = result.getString("server_name");
            String configuredServerName = result.getString("configured_server_name");
            String databaseName = result.getString("database_name");
            int databaseId = result.getInt("database_id");
            String databaseState = result.getString("database_state");
            int hasDatabaseAccess = result.getInt("has_database_access");
            String productVersion = result.getString("product_version");
            if (!EXPECTED_SERVER.equalsIgnoreCase(serverName)
                    || !EXPECTED_SERVER.equalsIgnoreCase(configuredServerName)
                    || !EXPECTED_DATABASE.equals(databaseName)
                    || databaseId != EXPECTED_DATABASE_ID
                    || databaseId == PROTECTED_CLONE_DATABASE_ID
                    || !"ONLINE".equalsIgnoreCase(databaseState)
                    || hasDatabaseAccess != 1
                    || !EXPECTED_PRODUCT_VERSION.equals(productVersion)) {
                throw new IllegalStateException("SQL Server original database identity/version guard failed");
            }
            if (result.next()) {
                throw new IllegalStateException("SQL Server identity query returned multiple database rows");
            }
            System.out.println("F8H_IDENTITY stage=" + stage + " server=" + serverName
                    + " database=" + databaseName + " databaseId=" + databaseId + " state=" + databaseState
                    + " productVersion=" + productVersion);
        }
        verifyRequiredSchema(connection);
        preHibernateSnapshot = readSnapshot(connection);
        System.out.println("F8H_DATABASE_SNAPSHOT stage=" + stage + " tables="
                + preHibernateSnapshot.tableCounts().size() + " ptStatuses="
                + preHibernateSnapshot.ptStatusCounts().size() + " schemaFingerprint="
                + preHibernateSnapshot.schemaFingerprint() + " tableCounts="
                + preHibernateSnapshot.tableCounts() + " ptStatusCounts="
                + preHibernateSnapshot.ptStatusCounts());
    }

    private static void verifyRequiredSchema(Connection connection) throws Exception {
        String sql = """
                DECLARE @statusHash char(64) = (
                    SELECT CONVERT(char(64), HASHBYTES('SHA2_256', CONVERT(varbinary(max), definition)), 2)
                    FROM sys.check_constraints
                    WHERE parent_object_id=OBJECT_ID(N'dbo.personal_training_booking')
                      AND name=N'ck_personal_training_booking_status');
                DECLARE @expected TABLE(name sysname, type_name sysname, max_length smallint,
                    precision_value tinyint, scale_value tinyint);
                INSERT INTO @expected VALUES
                    (N'renewal_provider_transaction_reference',N'varchar',64,0,0),
                    (N'renewal_verified_collection_amount',N'decimal',9,12,2),
                    (N'renewal_verified_collection_currency',N'varchar',3,0,0),
                    (N'renewal_paid_time_evidence_type',N'varchar',24,0,0),
                    (N'renewal_collection_verified_at',N'datetime2',8,26,6),
                    (N'renewal_collection_conflict_detected_at',N'datetime2',8,26,6);
                SELECT CASE WHEN @statusHash='F03C1F9A348204BF9BEE482252B9437106C191B98731B5DF229A52C2E811F57E'
                    AND EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.personal_training_booking')
                        AND name=N'ck_personal_training_booking_status' AND is_disabled=0 AND is_not_trusted=0)
                    AND NOT EXISTS (SELECT 1 FROM @expected e LEFT JOIN sys.columns c
                        ON c.object_id=OBJECT_ID(N'dbo.payment') AND c.name=e.name
                        WHERE c.column_id IS NULL OR TYPE_NAME(c.user_type_id)<>e.type_name
                          OR c.max_length<>e.max_length OR c.precision<>e.precision_value
                          OR c.scale<>e.scale_value OR c.is_nullable<>1)
                    AND (SELECT COUNT(*) FROM @expected)=6
                    AND (SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.payment')
                        AND name IN (N'ck_payment_renewal_collection_evidence',N'ck_payment_renewal_settlement_has_collection')
                        AND is_disabled=0 AND is_not_trusted=0)=2
                    AND EXISTS (SELECT 1 FROM sys.indexes i
                        JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
                        JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
                        WHERE i.object_id=OBJECT_ID(N'dbo.payment')
                          AND i.name=N'ux_payment_renewal_provider_transaction_reference'
                          AND i.is_unique=1 AND i.is_disabled=0 AND i.has_filter=1
                          AND LOWER(REPLACE(REPLACE(REPLACE(i.filter_definition,N'[',N''),N']',N''),N' ',N''))
                            =N'(renewal_intent_idisnotnullandrenewal_provider_transaction_referenceisnotnull)'
                          AND ic.key_ordinal=1 AND c.name=N'renewal_provider_transaction_reference'
                          AND NOT EXISTS (SELECT 1 FROM sys.index_columns ic2 WHERE ic2.object_id=i.object_id
                            AND ic2.index_id=i.index_id AND ic2.key_ordinal>1))
                    THEN 1 ELSE 0 END;
                """;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next() || result.getInt(1) != 1 || result.next()) {
                throw new IllegalStateException("Required PT Outcome or Payment Collection schema assertions failed");
            }
        }
        System.out.println("F8H_SCHEMA_ASSERTIONS ptOutcome=pass paymentCollection=pass");
    }

    private static DatabaseSnapshot readSnapshot(Connection connection) throws Exception {
        Map<String, Long> tableCounts = new TreeMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                SELECT N'membership',COUNT_BIG(*) FROM dbo.membership
                UNION ALL SELECT N'payment',COUNT_BIG(*) FROM dbo.payment
                UNION ALL SELECT N'membership_upgrade',COUNT_BIG(*) FROM dbo.membership_upgrade
                UNION ALL SELECT N'membership_cancellation_request',COUNT_BIG(*) FROM dbo.membership_cancellation_request
                UNION ALL SELECT N'personal_training_booking',COUNT_BIG(*) FROM dbo.personal_training_booking
                UNION ALL SELECT N'membership_renewal_intent',COUNT_BIG(*) FROM dbo.membership_renewal_intent
                """)) {
            while (result.next()) tableCounts.put(result.getString(1), result.getLong(2));
        }
        Map<String, Long> ptStatusCounts = new TreeMap<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT COALESCE(status,N'<NULL>'),COUNT_BIG(*) FROM dbo.personal_training_booking GROUP BY status")) {
            while (result.next()) ptStatusCounts.put(result.getString(1), result.getLong(2));
        }
        List<String> schemaRows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
                SELECT N'PT_CHECK|' + name + N'|' + definition + N'|' + CONVERT(nvarchar(1),is_disabled)
                    + N'|' + CONVERT(nvarchar(1),is_not_trusted) AS row_value
                  FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.personal_training_booking')
                UNION ALL
                SELECT N'PAYMENT_COLUMN|' + c.name + N'|' + TYPE_NAME(c.user_type_id) + N'|'
                    + CONVERT(nvarchar(10),c.max_length) + N'|' + CONVERT(nvarchar(10),c.precision) + N'|'
                    + CONVERT(nvarchar(10),c.scale) + N'|' + CONVERT(nvarchar(1),c.is_nullable)
                  FROM sys.columns c WHERE c.object_id=OBJECT_ID(N'dbo.payment') AND c.name LIKE N'renewal[_]%'
                UNION ALL
                SELECT N'PAYMENT_CHECK|' + name + N'|' + definition + N'|' + CONVERT(nvarchar(1),is_disabled)
                    + N'|' + CONVERT(nvarchar(1),is_not_trusted)
                  FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.payment')
                    AND name IN (N'ck_payment_renewal_collection_evidence',N'ck_payment_renewal_settlement_has_collection')
                UNION ALL
                SELECT N'PAYMENT_INDEX|' + i.name + N'|' + CONVERT(nvarchar(1),i.is_unique) + N'|'
                    + CONVERT(nvarchar(1),i.is_disabled) + N'|' + CONVERT(nvarchar(1),i.has_filter) + N'|'
                    + COALESCE(i.filter_definition,N'') + N'|' + c.name
                  FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
                  JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
                  WHERE i.object_id=OBJECT_ID(N'dbo.payment') AND i.name=N'ux_payment_renewal_provider_transaction_reference'
                """)) {
            while (result.next()) schemaRows.add(result.getString(1));
        }
        schemaRows.sort(String::compareTo);
        byte[] canonical = String.join("\n", schemaRows).getBytes(StandardCharsets.UTF_8);
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        return new DatabaseSnapshot(tableCounts, ptStatusCounts, fingerprint);
    }

    private record DatabaseSnapshot(Map<String, Long> tableCounts, Map<String, Long> ptStatusCounts,
            String schemaFingerprint) {}

    private static final class ValidationIdentityGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var environment = context.getEnvironment();
            String url = requireEnvironment("SPRING_DATASOURCE_URL");
            if (!EXPECTED_JDBC_URL.equals(url)) {
                throw new IllegalStateException("SPRING_DATASOURCE_URL is not the exact approved original database URL");
            }
            rejectAmbiguousOverrides(environment);

            String username = requireEnvironment("F8H_SQLSERVER_USERNAME");
            String password = requireEnvironment("F8H_SQLSERVER_PASSWORD");
            if (username.contains("${") || password.contains("${")) {
                throw new IllegalStateException("SQL credential is unresolved");
            }

            Map<String, Object> forcedProperties = Map.ofEntries(
                    Map.entry("spring.datasource.url", EXPECTED_JDBC_URL),
                    Map.entry("spring.datasource.username", username),
                    Map.entry("spring.datasource.password", password),
                    Map.entry("spring.datasource.driver-class-name", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
                    Map.entry("spring.datasource.hikari.read-only", "true"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "validate"),
                    Map.entry("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.SQLServerDialect"),
                    Map.entry("spring.jpa.generate-ddl", "false"),
                    Map.entry("spring.jpa.show-sql", "false"),
                    Map.entry("spring.sql.init.mode", "never"),
                    Map.entry("spring.flyway.enabled", "false"),
                    Map.entry("spring.liquibase.enabled", "false"),
                    Map.entry("spring.main.web-application-type", "none"),
                    Map.entry("spring.main.banner-mode", "off"),
                    Map.entry("sportcenter.scheduling.enabled", "false"),
                    Map.entry("sportcenter.renewal.scheduler.enabled", "false"),
                    Map.entry("sportcenter.pt.outcome-fallback.enabled", "false"),
                    Map.entry("app.payment.provider", "mock"),
                    Map.entry("app.payment.mock-gateway.enabled", "false"),
                    Map.entry("logging.level.org.hibernate.SQL", "OFF"),
                    Map.entry("logging.level.org.hibernate.orm.jdbc.bind", "OFF"));
            environment.getPropertySources().addFirst(new MapPropertySource("phase8cF3bFailClosed", forcedProperties));

            if (!EXPECTED_JDBC_URL.equals(environment.getProperty("spring.datasource.url"))
                    || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !"validate".equalsIgnoreCase(
                            environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"))) {
                throw new IllegalStateException("Forced validation-only properties did not become effective");
            }

            String guardStage = "connect";
            try (Connection connection = openGuardConnection(url, username, password)) {
                guardStage = "identity-query";
                verifyConnection(connection, "preHibernate");
            } catch (Exception exception) {
                String diagnostic = exception.getClass().getSimpleName();
                if (exception instanceof java.sql.SQLException sqlException) {
                    String safeMessage = sqlException.getMessage();
                    if (safeMessage != null) {
                        safeMessage = safeMessage.replace(username, "[redacted]").replace(password, "[redacted]");
                    }
                    diagnostic += ", SQLState=" + sqlException.getSQLState() + ", vendorCode="
                            + sqlException.getErrorCode() + ", message=" + safeMessage;
                }
                throw new IllegalStateException("Pre-Hibernate original database identity guard failed at "
                        + guardStage + " (" + diagnostic + ")");
            }
        }

        private static Connection openGuardConnection(String url, String username, String password) throws Exception {
            java.util.Properties properties = new java.util.Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            properties.setProperty("encrypt", "true");
            properties.setProperty("trustServerCertificate", "true");
            properties.setProperty("applicationName", "SportCenter-F8H-Hibernate-ReadOnly-Guard");
            Connection connection = DriverManager.getConnection(url, properties);
            connection.setReadOnly(true);
            return connection;
        }

        private static String requireEnvironment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required F8H process environment value is missing: " + name);
            }
            return value;
        }

        private static void rejectAmbiguousOverrides(org.springframework.core.env.ConfigurableEnvironment environment) {
            String[] forbiddenEnvironment = {
                    "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "SPRING_DATASOURCE_JNDI_NAME",
                    "SPRING_DATASOURCE_TYPE", "SPRING_DATASOURCE_DRIVER_CLASS_NAME",
                    "SPRING_DATASOURCE_HIKARI_JDBC_URL", "SPRING_DATASOURCE_HIKARI_READ_ONLY",
                    "SPRING_DATASOURCE_HIKARI_DATA_SOURCE_CLASS_NAME", "SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL",
                    "SPRING_DATASOURCE_HIKARI_CONNECTION_TEST_QUERY", "SPRING_PROFILES_ACTIVE",
                    "SPRING_CONFIG_LOCATION", "SPRING_CONFIG_ADDITIONAL_LOCATION", "SPRING_CONFIG_IMPORT",
                    "SPRING_CONFIG_NAME", "SPRING_APPLICATION_JSON" };
            for (String name : forbiddenEnvironment) {
                if (System.getenv(name) != null && !System.getenv(name).isBlank()) {
                    throw new IllegalStateException("Ambiguous datasource/configuration override is present: " + name);
                }
            }

            String[] guardedProperties = { "spring.datasource.url", "spring.datasource.username",
                    "spring.datasource.password", "spring.datasource.jndi-name", "spring.datasource.type",
                    "spring.datasource.driver-class-name", "spring.datasource.hikari.jdbc-url",
                    "spring.datasource.hikari.read-only", "spring.datasource.hikari.data-source-class-name",
                    "spring.datasource.hikari.connection-init-sql", "spring.datasource.hikari.connection-test-query",
                    "spring.datasource.hikari.data-source-properties", "spring.profiles.active", "spring.config.location",
                    "spring.config.additional-location", "spring.config.import", "spring.application.json" };
            for (PropertySource<?> source : environment.getPropertySources()) {
                for (String key : guardedProperties) {
                    if (!source.containsProperty(key)) continue;
                    Object configured = source.getProperty(key);
                    if ("spring.datasource.url".equals(key) && EXPECTED_JDBC_URL.equals(configured)) continue;
                    throw new IllegalStateException("Ambiguous datasource/configuration property source detected: " + key);
                }
            }

            if (environment.getActiveProfiles().length != 0) {
                throw new IllegalStateException("Spring profiles are not allowed in the validation-only context");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = { TaskSchedulingAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class })
    @EntityScan(basePackages = ENTITY_PACKAGE)
    static class ValidationOnlyConfiguration {

        @Bean
        static BeanFactoryPostProcessor rejectStartupWriters() {
            return beanFactory -> {
                if (beanFactory.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(org.springframework.boot.ApplicationRunner.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(org.springframework.boot.CommandLineRunner.class, false, false).length > 0) {
                    throw new IllegalStateException("Scheduling infrastructure or startup runner detected before Hibernate validation");
                }
            };
        }
    }
}

