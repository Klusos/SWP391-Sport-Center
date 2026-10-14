package group5.example.sportCenter.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Read-only fail-closed guard, executed before Spring refresh, Tomcat, or scheduling. */
public final class SqlServerE2ETargetGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String EXPECTED_SERVER = "DESKTOP-EMK77PD";
    private static final String EXPECTED_DATABASE = "SportCenterDB";
    private static final int EXPECTED_ADMIN_ID = 11;
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "id", "member_id", "class_id", "status", "registered_at", "cancelled_at",
            "original_price", "membership_discount", "final_price");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!"true".equals(System.getProperty("sportcenter.e2e.sqlserver.enabled"))) {
            throw new IllegalStateException("SQL Server E2E profile was not explicitly enabled by Failsafe");
        }
        if (!(EXPECTED_SERVER + "/" + EXPECTED_DATABASE)
                .equals(System.getenv("SPORTCENTER_E2E_SQLSERVER_CONFIRM"))) {
            throw new IllegalStateException("Missing exact SQL Server E2E confirmation opt-in");
        }

        String url = requiredEnv("E2E_SQLSERVER_JDBC_URL");
        String environmentUrl = context.getEnvironment().getProperty("spring.datasource.url");
        String environmentUser = context.getEnvironment().getProperty("spring.datasource.username");
        String environmentPassword = context.getEnvironment().getProperty("spring.datasource.password");
        String configuredDdlMode = context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto");
        String configuredDriver = context.getEnvironment().getProperty("spring.datasource.driver-class-name");
        String jwtSecret = requiredEnv("E2E_SQLSERVER_JWT_SECRET");
        String configuredJwtSecret = context.getEnvironment().getProperty("app.jwt.secret");
        if (!url.equals(environmentUrl)
                || !requiredEnv("E2E_SQLSERVER_USERNAME").equals(environmentUser)
                || !requiredEnv("E2E_SQLSERVER_PASSWORD").equals(environmentPassword)
                || !"validate".equalsIgnoreCase(configuredDdlMode)
                || !"com.microsoft.sqlserver.jdbc.SQLServerDriver".equals(configuredDriver)
                || !jwtSecret.equals(configuredJwtSecret)) {
            throw new IllegalStateException("Resolved Spring datasource or schema mode does not match the approved E2E configuration");
        }
        String expectedUrl = requiredEnv("E2E_SQLSERVER_EXPECTED_JDBC_URL");
        if (!url.equals(expectedUrl) || !isExpectedUrl(url)) {
            throw new IllegalStateException("Configured E2E JDBC URL does not exactly match the approved target");
        }
        String username = requiredEnv("E2E_SQLSERVER_USERNAME");
        String password = requiredEnv("E2E_SQLSERVER_PASSWORD");
        requiredEnv("E2E_SQLSERVER_JWT_SECRET");
        String adminEmail = requiredEnv("E2E_ADMIN_EMAIL");
        String runId = requiredEnv("E2E_ADMIN_RUN_ID");
        if (!"20261014095644-80179052".equals(runId)
                || !adminEmail.toLowerCase(Locale.ROOT).contains(runId.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Expected E2E ADMIN run identity was not supplied");
        }

        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                verifyTargetAndAccount(connection, adminEmail, runId);
            }
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Read-only SQL Server E2E preflight failed; no writes were attempted");
        }
    }

    private static void verifyTargetAndAccount(Connection connection, String adminEmail, String runId)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT @@SERVERNAME, DB_NAME(), d.state_desc "
                                + "FROM sys.databases d WHERE d.name = DB_NAME()")) {
            if (!result.next()
                    || !EXPECTED_SERVER.equalsIgnoreCase(result.getString(1))
                    || !EXPECTED_DATABASE.equals(result.getString(2))
                    || !"ONLINE".equalsIgnoreCase(result.getString(3))) {
                throw new SQLException("Connected SQL Server identity or database state is not approved");
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT email, role, status FROM dbo.users WHERE id = ?")) {
            statement.setInt(1, EXPECTED_ADMIN_ID);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !adminEmail.equalsIgnoreCase(result.getString("email"))
                        || !result.getString("email").toLowerCase(Locale.ROOT)
                                .contains(runId.toLowerCase(Locale.ROOT))
                        || !"ADMIN".equalsIgnoreCase(result.getString("role"))
                        || !"ACTIVE".equalsIgnoreCase(result.getString("status"))
                        || result.next()) {
                    throw new SQLException("users.id=11 is not the explicitly identified ACTIVE E2E ADMIN");
                }
            }
        }

        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT c.name FROM sys.columns c "
                                + "WHERE c.object_id = OBJECT_ID(N'dbo.course_registration', N'U')")) {
            Set<String> found = new HashSet<>();
            while (result.next()) found.add(result.getString(1).toLowerCase(Locale.ROOT));
            if (!found.equals(REQUIRED_COLUMNS)) {
                throw new SQLException("Phase 2 registration schema is missing or differs from the expected contract");
            }
        }
    }

    private static boolean isExpectedUrl(String url) {
        String[] parts = url.split(";", -1);
        if (parts.length < 2 || !parts[0].matches("(?i)jdbc:sqlserver://DESKTOP-EMK77PD(?::1433)?")) {
            return false;
        }
        for (int index = 1; index < parts.length; index++) {
            String[] keyValue = parts[index].split("=", 2);
            if (keyValue.length == 2 && "databaseName".equalsIgnoreCase(keyValue[0].trim())) {
                return EXPECTED_DATABASE.equals(keyValue[1].trim());
            }
        }
        return false;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required E2E environment input is missing: " + name);
        }
        return value;
    }
}
