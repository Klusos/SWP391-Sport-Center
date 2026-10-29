package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** Fail-closed, test-only gate for the explicitly approved local clone. */
public final class CloneDatasourceSafetyGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String URL = "jdbc:sqlserver://127.0.0.1:14333;databaseName=SportCenterPtE2E;encrypt=true;trustServerCertificate=true";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        verifyConfiguration(context.getEnvironment());
    }

    public static void verifyConfiguration(Environment environment) {
        if (!URL.equals(environment.getProperty("spring.datasource.url"))
                || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.scheduling.enabled"))) {
            throw new IllegalStateException("Clone E2E refused: exact clone URL, ddl-auto=validate, and scheduling=false are required");
        }
    }

    /** Verifies the actual Spring DataSource, not merely the configured JDBC URL. */
    public static void verifyRuntime(DataSource dataSource, Environment environment, ApplicationContext context)
            throws Exception {
        verifyConfiguration(environment);
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                "Scheduling infrastructure must be absent");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT DB_NAME(), "
                        + "CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')), "
                        + "CONVERT(nvarchar(128), SERVERPROPERTY('MachineName')), "
                        + "SERVERPROPERTY('InstanceName'), d.state_desc "
                        + "FROM sys.databases d WHERE d.name = DB_NAME()")) {
            assertTrue(result.next(), "SQL Server must report the current database identity");
            assertEquals("SportCenterPtE2E", result.getString(1));
            assertEquals("sportcenter-recovery-sql", result.getString(2));
            assertEquals("sportcenter-recovery-sql", result.getString(3));
            assertNull(result.getString(4), "Only the default SQL Server instance is authorized");
            assertEquals("ONLINE", result.getString(5));
            assertTrue(!result.next(), "Expected exactly one current database row");
        }
    }
}
