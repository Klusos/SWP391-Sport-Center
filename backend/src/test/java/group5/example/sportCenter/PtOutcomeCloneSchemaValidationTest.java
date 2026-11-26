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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
 * Opt-in, validation-only JPA bootstrap for the isolated Phase 8C-F2 clone.
 * This test deliberately does not component-scan the SportCenter application.
 */
class PtOutcomeCloneSchemaValidationTest {

    private static final String EXPECTED_SERVER = "LAPTOP-416VBDLK";
    private static final String EXPECTED_PRODUCT_VERSION = "17.0.1155.3";
    private static final String EXPECTED_DATABASE = "SportCenterDemo_PTOutcome_8CF1";
    private static final int EXPECTED_DATABASE_ID = 12;
    private static final int PROTECTED_ORIGINAL_DATABASE_ID = 5;
    private static final String EXPECTED_JDBC_URL = "jdbc:sqlserver://127.0.0.1:1433;"
            + "databaseName=SportCenterDemo_PTOutcome_8CF1;encrypt=true;trustServerCertificate=true";
    private static final String ENTITY_PACKAGE = "group5.example.sportCenter.entity";
    private static final String AUTHORIZATION_OPT_IN = "sportcenter.f3c.hibernate.authorized";
    private static final String EXECUTION_OPT_IN = "sportcenter.f3c.hibernate.execute";

    @Test
    void validatesEveryMappedEntityAgainstTheExplicitCloneOnlyWhenSeparatelyAuthorized() {
        // These assumptions are deliberately the first executable statements. A normal test run
        // must skip before reading credentials, creating Spring, or opening any connection.
        assumeTrue("true".equalsIgnoreCase(System.getProperty(AUTHORIZATION_OPT_IN)),
                "Requires separate Phase 8C-F3C authorization flag");
        assumeTrue("true".equalsIgnoreCase(System.getProperty(EXECUTION_OPT_IN)),
                "Requires execution-specific Hibernate validation opt-in");

        ValidationIdentityGuard identityGuard = new ValidationIdentityGuard();
        SpringApplication application = new SpringApplication(ValidationOnlyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.addInitializers(identityGuard);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.config.name=phase8c-f3b-no-application-config")) {
            assertEquals(EXPECTED_JDBC_URL, context.getEnvironment().getProperty("spring.datasource.url"),
                    "Effective Spring datasource must be pinned to the approved clone");
            assertEquals("validate",
                    context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("validate",
                    context.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"));
            assertEquals("never", context.getEnvironment().getProperty("spring.sql.init.mode"));
            assertFalse(Boolean.parseBoolean(context.getEnvironment().getProperty("spring.jpa.generate-ddl")));
            assertNoApplicationStartupComponents(context);

            DataSource dataSource = context.getBean(DataSource.class);
            verifyConnectedCloneIdentity(dataSource);

            EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
            assertNotNull(entityManagerFactory);
            assertAllEntityClassesAreManaged(entityManagerFactory);
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
        System.out.println("F3C_ENTITY_COVERAGE discovered=" + discoveredEntityTypes.size()
                + " managed=" + managedEntityTypes.size() + " missing=0 unexpected=0");
    }

    private static void verifyConnectedCloneIdentity(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            verifyConnection(connection);
        } catch (Exception exception) {
            throw new IllegalStateException("Effective Hibernate DataSource failed the clone identity guard", exception);
        }
    }

    private static void verifyConnection(Connection connection) throws Exception {
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
                    || databaseId == PROTECTED_ORIGINAL_DATABASE_ID
                    || !"ONLINE".equalsIgnoreCase(databaseState)
                    || hasDatabaseAccess != 1
                    || !EXPECTED_PRODUCT_VERSION.equals(productVersion)) {
                throw new IllegalStateException("SQL Server clone identity/version guard failed");
            }
            if (result.next()) {
                throw new IllegalStateException("SQL Server identity query returned multiple database rows");
            }
            System.out.println("F3C_IDENTITY stage=preHibernate server=" + serverName
                    + " database=" + databaseName + " databaseId=" + databaseId + " state=" + databaseState
                    + " productVersion=" + productVersion);
        }
    }

    private static final class ValidationIdentityGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var environment = context.getEnvironment();
            String url = requireEnvironment("SPRING_DATASOURCE_URL");
            if (!EXPECTED_JDBC_URL.equals(url)) {
                throw new IllegalStateException("SPRING_DATASOURCE_URL is not the exact approved clone URL");
            }
            rejectAmbiguousOverrides(environment);

            String username = requireEnvironment("F3C_SQLSERVER_USERNAME");
            String password = requireEnvironment("F3C_SQLSERVER_PASSWORD");
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
                verifyConnection(connection);
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
                throw new IllegalStateException("Pre-Hibernate clone identity guard failed at "
                        + guardStage + " (" + diagnostic + ")");
            }
        }

        private static Connection openGuardConnection(String url, String username, String password) throws Exception {
            java.util.Properties properties = new java.util.Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            properties.setProperty("encrypt", "true");
            properties.setProperty("trustServerCertificate", "true");
            properties.setProperty("applicationName", "SportCenter-F3C-Hibernate-ReadOnly-Guard");
            Connection connection = DriverManager.getConnection(url, properties);
            connection.setReadOnly(true);
            return connection;
        }

        private static String requireEnvironment(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required F3C process environment value is missing: " + name);
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
