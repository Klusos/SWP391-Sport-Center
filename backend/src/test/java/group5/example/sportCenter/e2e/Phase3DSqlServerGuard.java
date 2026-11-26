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

/** Test-only fail-closed guard for the owner-authorized Phase 3D SQL Server clone. */
public final class Phase3DSqlServerGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String URL = "jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDemo_PTOutcome_8CF1;encrypt=true;trustServerCertificate=true";
    public static final String DATABASE = "SportCenterDemo_PTOutcome_8CF1";
    public static final String SERVER = "LAPTOP-416VBDLK";
    public static final int DATABASE_ID = 12;
    public static final String PRODUCT_VERSION = "17.0.1155.3";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        verifyConfiguration(context.getEnvironment());
        verifyIdentityBeforeHibernate(context.getEnvironment());
    }

    public static void verifyConfiguration(Environment environment) {
        if (!URL.equals(environment.getProperty("spring.datasource.url"))
                || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"org.hibernate.dialect.SQLServerDialect".equals(
                        environment.getProperty("spring.jpa.database-platform"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.scheduling.enabled"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.renewal.scheduler.enabled"))
                || !"false".equalsIgnoreCase(environment.getProperty("sportcenter.pt.outcome-fallback.enabled"))
                || !"mock".equalsIgnoreCase(environment.getProperty("app.payment.provider"))) {
            throw new IllegalStateException("Phase 3D refused: exact clone URL, SQL Server dialect, ddl-auto=validate, scheduling=false, and mock payment provider are required");
        }
        if (environment.getProperty("spring.datasource.url", "").contains("databaseName=SportCenterDemo;")) {
            throw new IllegalStateException("Phase 3D refused: original SportCenterDemo is not a test target");
        }
    }

    private static void verifyIdentityBeforeHibernate(Environment environment) {
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Phase 3D refused: clone datasource credentials are unavailable");
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);
        credentials.setProperty("encrypt", "true");
        credentials.setProperty("trustServerCertificate", "true");
        try (Connection connection = DriverManager.getConnection(URL, credentials);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')), "
                        + "CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')), DB_NAME(), "
                        + "d.state_desc, DB_ID(), CONVERT(nvarchar(30), SERVERPROPERTY('ProductVersion')) "
                        + "FROM sys.databases d WHERE d.name = DB_NAME()")) {
            assertTrue(result.next(), "SQL Server must report a current database identity");
            assertEquals(SERVER, result.getString(1));
            assertNull(result.getString(2), "Only the default SQL Server instance is authorized");
            assertEquals(DATABASE, result.getString(3));
            assertEquals("ONLINE", result.getString(4));
            assertEquals(DATABASE_ID, result.getInt(5));
            assertEquals(PRODUCT_VERSION, result.getString(6));
            assertTrue(!result.next(), "Expected exactly one current database row");
        } catch (Exception exception) {
            throw new IllegalStateException("Phase 3D refused: clone identity could not be verified", exception);
        }
    }

    /** Runtime recheck immediately before any test fixture can be written. */
    public static void verifyRuntime(DataSource dataSource, Environment environment, ApplicationContext context)
            throws Exception {
        verifyConfiguration(environment);
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                "Scheduling infrastructure must be absent");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')), "
                        + "CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')), DB_NAME(), "
                        + "d.state_desc, DB_ID(), CONVERT(nvarchar(30), SERVERPROPERTY('ProductVersion')) "
                        + "FROM sys.databases d WHERE d.name = DB_NAME()")) {
            assertTrue(result.next(), "SQL Server must report a current database identity");
            assertEquals(SERVER, result.getString(1));
            assertNull(result.getString(2), "Only the default SQL Server instance is authorized");
            assertEquals(DATABASE, result.getString(3));
            assertEquals("ONLINE", result.getString(4));
            assertEquals(DATABASE_ID, result.getInt(5));
            assertEquals(PRODUCT_VERSION, result.getString(6));
            assertTrue(!result.next(), "Expected exactly one current database row");
        }
    }
}
