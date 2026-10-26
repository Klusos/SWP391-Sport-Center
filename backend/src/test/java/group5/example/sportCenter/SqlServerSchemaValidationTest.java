package group5.example.sportCenter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.repository.MembershipRepository;

/**
 * Opt-in, read-only SQL Server schema validation. This bootstrap intentionally does not
 * component-scan the application, so application services, schedulers, listeners and
 * startup writers are not registered.
 */
class SqlServerSchemaValidationTest {

    private static final String EXPECTED_SERVER = "sportcenter-recovery-sql";
    private static final String EXPECTED_DATABASE = "SportCenterDB";
    private static final String OPT_IN_PROPERTY = "sportcenter.recovery.sqlserver.validation.opt-in";

    @Test
    void validationBootstrapDoesNotEnableApplicationScanningOrScheduling() {
        assertFalse(ValidationOnlyConfiguration.class.isAnnotationPresent(ComponentScan.class));
        assertFalse(ValidationOnlyConfiguration.class.isAnnotationPresent(
                org.springframework.scheduling.annotation.EnableScheduling.class));
        assertTrue(ValidationOnlyConfiguration.class.isAnnotationPresent(EntityScan.class));
        assertTrue(ValidationOnlyConfiguration.class.isAnnotationPresent(EnableJpaRepositories.class));
    }

    @Test
    void jdbcTargetParserRejectsNonApprovedOrAmbiguousEndpoints() {
        assertTrue(isApprovedUrl(
                "jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterDB;encrypt=true;trustServerCertificate=true"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDB"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://localhost:14333;databaseName=SportCenterDB"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:14333;databaseName=OtherDb"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterDB;databaseName=OtherDb"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterDB;failoverPartner=other"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterDB;INSTANCENAME=other"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterDB;password=secret"));
        assertFalse(isApprovedUrl("jdbc:sqlserver://127.0.0.1:14333"));
    }

    @Test
    void preJpaGuardRejectsSchedulingAndStartupRunnerBeans() {
        var guard = ValidationOnlyConfiguration.rejectStartupWritersBeforeJpaInitialization();
        assertDoesNotThrow(() -> guard.postProcessBeanFactory(new DefaultListableBeanFactory()));
        var scheduled = new DefaultListableBeanFactory();
        scheduled.registerBeanDefinition("scheduledProcessor", new RootBeanDefinition(ScheduledAnnotationBeanPostProcessor.class));
        assertThrows(IllegalStateException.class, () -> guard.postProcessBeanFactory(scheduled));
        var runner = new DefaultListableBeanFactory();
        runner.registerBeanDefinition("applicationRunner", new RootBeanDefinition(NoopApplicationRunner.class));
        assertThrows(IllegalStateException.class, () -> guard.postProcessBeanFactory(runner));
    }

    @Test
    void validatesRecoverySqlServerSchemaReadOnlyWhenExplicitlyOptedIn() throws Exception {
        assumeTrue("true".equals(System.getProperty(OPT_IN_PROPERTY)),
                "Set the explicit system-property opt-in to run SQL Server validation");

        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (!isApprovedUrl(url)) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL is absent, malformed, or not the approved recovery target");
        }

        SpringApplication application = new SpringApplication(ValidationOnlyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(new SqlServerIdentityGuard());
        application.setDefaultProperties(Map.of(
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.hbm2ddl.auto", "validate",
                "spring.jpa.generate-ddl", "false",
                "spring.sql.init.mode", "never",
                "spring.flyway.enabled", "false",
                "spring.liquibase.enabled", "false"));

        try (ConfigurableApplicationContext context = application.run(
                "--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.hbm2ddl.auto=validate",
                "--spring.jpa.generate-ddl=false",
                "--spring.sql.init.mode=never",
                "--spring.flyway.enabled=false",
                "--spring.liquibase.enabled=false",
                "--spring.main.web-application-type=none",
                "--spring.jpa.show-sql=false")) {

            if (!context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty()) {
                throw new IllegalStateException("Scheduling processor unexpectedly registered in validation context");
            }
            if (!context.getBeansOfType(org.springframework.boot.ApplicationRunner.class).isEmpty()
                    || !context.getBeansOfType(org.springframework.boot.CommandLineRunner.class).isEmpty()) {
                throw new IllegalStateException("Startup runner unexpectedly registered in validation context");
            }

            DataSource dataSource = context.getBean(DataSource.class);
            verifyConnectedIdentity(dataSource);
        }
    }

    private static boolean isApprovedUrl(String url) {
        if (url == null || !url.startsWith("jdbc:sqlserver://")) {
            return false;
        }
        String[] parts = url.substring("jdbc:sqlserver://".length()).split(";", -1);
        if (!"127.0.0.1:14333".equalsIgnoreCase(parts[0])) {
            return false;
        }

        String database = null;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            int separator = part.indexOf('=');
            if (separator <= 0) {
                return false;
            }
            String key = part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(separator + 1).trim();
            if (key.equals("databasename")) {
                if (database != null) return false;
                database = value;
            } else if (key.equals("instancename") || key.equals("failoverpartner")
                    || key.equals("database") || key.equals("user") || key.equals("password")
                    || key.equals("authentication") || key.equals("accesstoken")) {
                return false;
            }
        }
        return EXPECTED_DATABASE.equals(database);
    }

    private static void verifyConnectedIdentity(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            verifyConnectedIdentity(connection);
        } catch (Exception exception) {
            throw new IllegalStateException("Effective Spring DataSource identity verification failed", exception);
        }
    }

    private static void verifyConnectedIdentity(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT CAST(SERVERPROPERTY('ServerName') AS nvarchar(128)), "
                                + "@@SERVERNAME, DB_NAME(), d.state_desc "
                                + "FROM sys.databases d WHERE d.name = DB_NAME()")) {
            if (!result.next()
                    || !EXPECTED_SERVER.equalsIgnoreCase(result.getString(1))
                    || !EXPECTED_SERVER.equalsIgnoreCase(result.getString(2))
                    || !EXPECTED_DATABASE.equals(result.getString(3))
                    || !"ONLINE".equalsIgnoreCase(result.getString(4))) {
                throw new IllegalStateException("Connected SQL Server identity/database/state did not match the approved target");
            }
        }
    }

    private static final class SqlServerIdentityGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var environment = context.getEnvironment();
            String url = environment.getProperty("spring.datasource.url");
            if (!isApprovedUrl(url)
                    || !url.equals(System.getenv("SPRING_DATASOURCE_URL"))
                    || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !"validate".equalsIgnoreCase(
                            environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"))
                    || Boolean.parseBoolean(environment.getProperty("spring.jpa.generate-ddl", "false"))
                    || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode"))
                    || Boolean.parseBoolean(environment.getProperty("spring.flyway.enabled", "false"))
                    || Boolean.parseBoolean(environment.getProperty("spring.liquibase.enabled", "false"))
                    || hasValue(environment.getProperty("spring.datasource.jndi-name"))
                    || hasValue(environment.getProperty("spring.datasource.type"))
                    || hasValue(environment.getProperty("spring.datasource.hikari.jdbc-url"))
                    || hasValue(environment.getProperty("spring.datasource.hikari.data-source-class-name"))
                    || hasValue(environment.getProperty("spring.datasource.hikari.connection-init-sql"))
                    || hasValue(environment.getProperty("spring.datasource.hikari.connection-test-query"))) {
                throw new IllegalStateException(
                        "Validation configuration is not strict, read-only, or pinned to the approved datasource");
            }

            String username = environment.getProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password");
            if (!hasValue(username) || !hasValue(password) || username.contains("${") || password.contains("${")) {
                throw new IllegalStateException("SQL Server credentials are missing; no connection was attempted");
            }

            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                verifyConnectedIdentity(connection);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Pre-Hibernate SQL Server identity verification failed; no application context was refreshed");
            }
        }

        private static boolean hasValue(String value) {
            return value != null && !value.isBlank();
        }
    }

    private static final class NoopApplicationRunner implements org.springframework.boot.ApplicationRunner {
        @Override
        public void run(org.springframework.boot.ApplicationArguments args) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = TaskSchedulingAutoConfiguration.class)
    @EntityScan(basePackageClasses = Membership.class)
    @EnableJpaRepositories(basePackageClasses = MembershipRepository.class)
    static class ValidationOnlyConfiguration {
        @org.springframework.context.annotation.Bean
        static BeanFactoryPostProcessor rejectStartupWritersBeforeJpaInitialization() {
            return beanFactory -> {
                if (beanFactory.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(org.springframework.boot.ApplicationRunner.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(org.springframework.boot.CommandLineRunner.class, false, false).length > 0) {
                    throw new IllegalStateException("Scheduling or startup runner detected before Hibernate initialization");
                }
            };
        }
    }
}


