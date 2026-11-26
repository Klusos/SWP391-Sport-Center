package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** Fail-closed SQL Server identity guard for the owner-authorized Phase 8C-K clone run. */
public final class Phase4CSqlServerGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String SERVER = "LAPTOP-416VBDLK";
    public static final String DATABASE = "SportCenterDemo_PTOutcome_8CF1";
    public static final int DATABASE_ID = 12;
    public static final String PRODUCT_VERSION = "17.0.1155.3";
    public static final String URL = "jdbc:sqlserver://127.0.0.1:1433;databaseName=" + DATABASE
            + ";encrypt=true;trustServerCertificate=true";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        verifyConfiguration(environment);
        verifyIdentityBeforeHibernate(environment);
    }

    public static void verifyConfiguration(Environment environment) {
        if (!URL.equals(environment.getProperty("spring.datasource.url"))
                || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"org.hibernate.dialect.SQLServerDialect".equals(
                        environment.getProperty("spring.jpa.database-platform"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.scheduling.enabled"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.renewal.scheduler.enabled"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.pt.outcome-fallback.enabled"))
                || !"mock".equalsIgnoreCase(environment.getProperty("app.payment.provider"))
                || !"true".equalsIgnoreCase(environment.getProperty("app.payment.mock-gateway.enabled"))) {
            throw new IllegalStateException(
                    "Phase 4C refused: exact clone URL, SQLServerDialect, ddl-auto=validate, scheduling disabled, and mock payment gateway are required");
        }
        if (environment.getProperty("spring.datasource.url", "").contains("databaseName=SportCenterDemo;")) {
            throw new IllegalStateException("Phase 4C refused: original SportCenterDemo is not a test target");
        }
    }

    /** Opens only the pinned Phase 4C URL and verifies the connected identity before returning it. */
    public static Connection openVerifiedConnection(String username, String password) throws Exception {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Phase 4C refused: clone datasource credentials are unavailable");
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);
        credentials.setProperty("encrypt", "true");
        credentials.setProperty("trustServerCertificate", "true");
        Connection connection = DriverManager.getConnection(URL, credentials);
        try {
            verifyIdentity(connection);
            return connection;
        } catch (Exception exception) {
            connection.close();
            throw exception;
        }
    }

    private static void verifyIdentityBeforeHibernate(Environment environment) {
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Phase 4C refused: clone datasource credentials are unavailable");
        }
        try (Connection connection = openVerifiedConnection(username, password)) {
            // The helper validates the actual database before Hibernate starts.
        } catch (Exception exception) {
            throw new IllegalStateException("Phase 4C refused: exact clone identity could not be verified", exception);
        }
    }

    /** Recheck the actual database on the established connection before each fixture write. */
    public static void verifyRuntime(DataSource dataSource, Environment environment, ApplicationContext context)
            throws Exception {
        verifyConfiguration(environment);
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                "Scheduling infrastructure must be absent in Phase 4C tests");
        try (Connection connection = dataSource.getConnection()) {
            verifyIdentity(connection);
        }
    }

    public static void verifyIdentity(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')), "
                        + "CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')), DB_NAME(), "
                        + "CONVERT(nvarchar(30), DATABASEPROPERTYEX(DB_NAME(), 'Status')), "
                        + "HAS_DBACCESS(DB_NAME()), DB_ID(), "
                        + "CONVERT(nvarchar(30), SERVERPROPERTY('ProductVersion'))")) {
            assertTrue(result.next(), "SQL Server must report the connected database identity");
            verifyIdentityValues(result.getString(1), result.getString(2), result.getString(3), result.getString(4),
                    result.getInt(5));
            assertEquals(DATABASE_ID, result.getInt(6));
            assertEquals(PRODUCT_VERSION, result.getString(7));
            assertTrue(!result.next(), "Expected exactly one connected database identity row");
        }
    }

    public static void verifyIdentityValues(String server, String instance, String database, String status,
            int hasDatabaseAccess) {
        assertEquals(SERVER, server);
        assertNull(instance, "Only the default SQL Server instance is authorized");
        assertEquals(DATABASE, database);
        assertEquals("ONLINE", status);
        assertEquals(1, hasDatabaseAccess, "Connected login must have access to the exact clone");
    }
}
