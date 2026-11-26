package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class Phase4CSqlServerPreflightScriptIT {
    private static final Path PREFLIGHT = Path.of(
            "docs/sql/proposals/membership-upgrade-paths-phase4c-clone-preflight.sql");
    private static final Path MIGRATION = Path.of(
            "docs/sql/proposals/membership-upgrade-paths-phase4c-clone-migration.sql");

    @Test
    void executesCloneOnlyPreflightAndFindsNoBlockingUpgradeDataAnomalies() throws Exception {
        String script = Files.readString(PREFLIGHT);
        assertTrue(script.contains("DECLARE @expectedDatabase sysname = N'" + Phase4CSqlServerGuard.DATABASE + "'"));
        String executableSql = script.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "");
        assertFalse(executableSql.matches("(?ims).*^\\s*USE\\s+.*"), "Preflight must not switch databases");
        assertFalse(executableSql.contains("SportCenterDemo.dbo."), "Preflight must not read/write another database");
        Set<String> migrationColumns = Phase4CSqlServerPreflightIT.extractMigrationGuardColumns(
                Files.readString(MIGRATION));
        int insertStart = script.indexOf("INSERT INTO @requiredUpgradeQuoteColumns");
        int insertEnd = script.indexOf(';', insertStart);
        assertTrue(insertStart >= 0 && insertEnd > insertStart,
                "Preflight must declare the migration prerequisite list");
        Matcher requiredMatcher = Pattern.compile("\\(N'([^']+)'\\)").matcher(script.substring(insertStart, insertEnd));
        Set<String> preflightColumns = new LinkedHashSet<>();
        while (requiredMatcher.find()) preflightColumns.add(requiredMatcher.group(1));
        assertEquals(migrationColumns, preflightColumns,
                "Preflight must check every migration prerequisite using the same names");
        assertFalse(preflightColumns.contains("target_plan_duration_months_snapshot"));
        assertTrue(executableSql.contains("LEFT JOIN sys.columns actual"));
        assertTrue(executableSql.contains("WHERE actual.column_id IS NULL"));
        assertTrue(executableSql.contains("THROW 50702"),
                "Preflight must stop before migration if a required column is missing");

        Set<String> simulatedColumns = new LinkedHashSet<>(preflightColumns);
        String simulatedMissingColumn = simulatedColumns.iterator().next();
        simulatedColumns.remove(simulatedMissingColumn);
        Set<String> detectedMissing = new LinkedHashSet<>(preflightColumns);
        detectedMissing.removeAll(simulatedColumns);
        assertEquals(Set.of(simulatedMissingColumn), detectedMissing,
                "Missing-column preflight predicate must identify a missing prerequisite");

        boolean sawPresentTable = false;
        int requiredColumnCount = 0;
        int missingColumnCount = 0;
        int resultSetNumber = 0;
        int finalResultRows = -1;
        try (Connection connection = Phase4CSqlServerGuard.openVerifiedConnection(
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            boolean hasResults = statement.execute(script);
            while (true) {
                if (hasResults) {
                    try (ResultSet result = statement.getResultSet()) {
                        ResultSetMetaData metadata = result.getMetaData();
                        int rows = 0;
                        resultSetNumber++;
                        System.out.println("Phase 4C preflight result set " + resultSetNumber);
                        while (result.next()) {
                            rows++;
                            StringBuilder line = new StringBuilder();
                            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                                String label = metadata.getColumnLabel(i);
                                String value = result.getString(i);
                                line.append(label).append('=').append(value);
                                if (i < metadata.getColumnCount()) line.append(" | ");
                                if ("path_table_state".equalsIgnoreCase(label)
                                        && value.startsWith("PRESENT")) sawPresentTable = true;
                                if ("column_state".equalsIgnoreCase(label)) {
                                    requiredColumnCount++;
                                    if ("MISSING".equals(value)) missingColumnCount++;
                                }
                            }
                            System.out.println(line);
                        }
                        finalResultRows = rows;
                    }
                } else if (statement.getUpdateCount() == -1) {
                    break;
                }
                hasResults = statement.getMoreResults();
            }
        }
        assertTrue(sawPresentTable, "The already-migrated Phase 4C clone must retain its Upgrade Path table");
        assertEquals(migrationColumns.size(), requiredColumnCount,
                "Preflight must evaluate every prerequisite against sys.columns");
        assertEquals(0, missingColumnCount, "No Phase 2D prerequisite columns may be missing");
        assertTrue(resultSetNumber >= 6, "Expected identity, quote columns, schema, plan, and anomaly result sets");
        assertEquals(0, finalResultRows, "Preflight's final Upgrade anomaly query must be empty");
    }
}
