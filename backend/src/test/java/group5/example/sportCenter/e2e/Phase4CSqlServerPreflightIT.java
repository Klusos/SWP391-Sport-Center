package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** Read-only gate for the already-restored Phase 4C clone and its row-level baseline. */
class Phase4CSqlServerPreflightIT {
    private static final List<String> TABLES = List.of(
            "membership", "payment", "membership_upgrade", "membership_cancellation_request");

    @Test
    void exactCloneIsOnlineUpgradePathMigrationIsPresentAndBaselineRowsMatch() throws Exception {
        try (Connection connection = Phase4CSqlServerGuard.openVerifiedConnection(
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
                Statement statement = connection.createStatement()) {
            assertTrue(exists(statement,
                    "SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name=N'membership_plan_upgrade'"),
                    "Authorized Phase 4C migration must remain applied");
            assertEquals(0, scalar(statement, "SELECT COUNT_BIG(*) FROM dbo.membership_plan_upgrade"),
                    "Transactional Upgrade Path test fixtures must be rolled back");
            long total = 0;
            for (String table : TABLES) {
                String baseline = "phase4c_baseline_" + table;
                assertTrue(exists(statement, "SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name=N'"
                        + baseline + "'"), "Missing saved baseline table " + baseline);
                long currentCount = scalar(statement, "SELECT COUNT_BIG(*) FROM dbo." + table);
                long baselineCount = scalar(statement, "SELECT COUNT_BIG(*) FROM dbo." + baseline);
                assertEquals(baselineCount, currentCount, "Row count differs for " + table);
                assertEquals(0, differenceCount(statement,
                        "SELECT * FROM dbo." + table + " EXCEPT SELECT * FROM dbo." + baseline),
                        "Current rows differ from baseline for " + table);
                assertEquals(0, differenceCount(statement,
                        "SELECT * FROM dbo." + baseline + " EXCEPT SELECT * FROM dbo." + table),
                        "Baseline rows are missing from " + table);
                total += currentCount;
            }
            assertEquals(146, total, "Expected all 146 protected historical rows");
        }
    }

    @Test
    void everyMigrationGuardColumnExistsWithThePhase2dSqlServerType() throws Exception {
        String migration = Files.readString(Path.of(
                "docs/sql/proposals/membership-upgrade-paths-migration.sql"));
        String cloneMigration = Files.readString(Path.of(
                "docs/sql/proposals/membership-upgrade-paths-phase4c-clone-migration.sql"));
        Set<String> required = extractMigrationGuardColumns(migration);
        assertFalse(required.isEmpty(), "The migration must have a frozen quote prerequisite guard");
        assertFalse(required.contains("target_plan_duration_months_snapshot"));
        assertTrue(required.contains("target_duration_months_snapshot"));
        String cloneBody = cloneMigration.replaceFirst(
                "(?s)^/\\* Phase 4C authorized clone-only copy[^\\n]*\\R", "");
        String canonicalMigration = migration.replace("\r\n", "\n")
                .replace("N'SportCenterDemo'", "N'" + Phase4CSqlServerGuard.DATABASE + "'");
        assertEquals(canonicalMigration, cloneBody.replace("\r\n", "\n"),
                "Clone migration body must match the authoritative migration except its identity guard");

        try (Connection connection = Phase4CSqlServerGuard.openVerifiedConnection(
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, String> actualTypes = new LinkedHashMap<>();
            try (var columns = metadata.getColumns(null, "dbo", "membership_upgrade", null)) {
                while (columns.next()) {
                    actualTypes.put(columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME").toLowerCase());
                }
            }
            for (String column : required) {
                assertTrue(actualTypes.containsKey(column), "Missing Phase 2D column: " + column);
                System.out.println("Phase 2D metadata " + column + " type=" + actualTypes.get(column));
            }
            assertEquals("int", actualTypes.get("target_duration_months_snapshot"));
        }
    }

    static Set<String> extractMigrationGuardColumns(String sql) {
        Matcher matcher = Pattern.compile("COL_LENGTH\\(N'dbo\\.membership_upgrade',\\s*N'([^']+)'\\)",
                Pattern.CASE_INSENSITIVE).matcher(sql);
        java.util.LinkedHashSet<String> columns = new java.util.LinkedHashSet<>();
        while (matcher.find()) columns.add(matcher.group(1));
        return Set.copyOf(columns);
    }

    private boolean exists(Statement statement, String query) throws Exception {
        try (ResultSet result = statement.executeQuery(query)) {
            return result.next();
        }
    }

    private long scalar(Statement statement, String query) throws Exception {
        try (ResultSet result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private long differenceCount(Statement statement, String differenceQuery) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT COUNT_BIG(*) FROM (" + differenceQuery + ") AS differences")) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
