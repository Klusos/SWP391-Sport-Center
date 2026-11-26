package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Executes only the separately guarded Phase 4C copy of the additive Upgrade Path migration. */
class Phase4CSqlServerMigrationIT {
    private static final Path MIGRATION = Path.of(
            "docs/sql/proposals/membership-upgrade-paths-phase4c-clone-migration.sql");
    private static final Path VERIFY = Path.of(
            "docs/sql/proposals/membership-upgrade-paths-phase4c-clone-verify.sql");
    private static final List<String> TABLES = List.of(
            "membership", "payment", "membership_upgrade", "membership_cancellation_request");

    @Test
    void appliesAndVerifiesTheCloneOnlyUpgradePathMigration() throws Exception {
        String migration = Files.readString(MIGRATION);
        assertTrue(migration.contains("DECLARE @expectedDatabase sysname = N'" + Phase4CSqlServerGuard.DATABASE + "'"));
        String executableSql = migration.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "");
        assertFalse(executableSql.matches("(?ims).*^\\s*USE\\s+.*"), "Migration must not switch database");
        assertFalse(executableSql.contains("SportCenterDemo.dbo."), "Migration must not write another database");

        try (Connection connection = Phase4CSqlServerGuard.openVerifiedConnection(
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(90);
            assertFalse(exists(statement,
                    "SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name=N'membership_plan_upgrade'"),
                    "Migration already exists; do not rerun it");
            statement.execute(migration);

            String verify = Files.readString(VERIFY);
            assertTrue(verify.contains("DECLARE @expectedDatabase sysname = N'" + Phase4CSqlServerGuard.DATABASE + "'"));
            String verifySql = verify.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "");
            assertFalse(verifySql.matches("(?ims).*^\\s*USE\\s+.*"), "Verify script must not switch database");
            statement.execute(verify);

            assertTrue(exists(statement,
                    "SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name=N'membership_plan_upgrade'"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id="
                    + "OBJECT_ID(N'dbo.membership_plan_upgrade') AND name=N'pk_membership_plan_upgrade' AND type='PK'"));
            assertEquals(2, scalar(statement, "SELECT COUNT(*) FROM sys.foreign_keys WHERE parent_object_id="
                    + "OBJECT_ID(N'dbo.membership_plan_upgrade') AND name IN "
                    + "(N'fk_membership_plan_upgrade_from',N'fk_membership_plan_upgrade_to')"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM sys.key_constraints WHERE parent_object_id="
                    + "OBJECT_ID(N'dbo.membership_plan_upgrade') AND name=N'uq_membership_plan_upgrade_directed_pair' AND type='UQ'"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id="
                    + "OBJECT_ID(N'dbo.membership_plan_upgrade') AND name=N'ck_membership_plan_upgrade_not_self' AND is_disabled=0 AND is_not_trusted=0"));
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM sys.indexes WHERE object_id="
                    + "OBJECT_ID(N'dbo.membership_plan_upgrade') AND name=N'ix_membership_plan_upgrade_active_source'"));
            assertEquals(0, scalar(statement, "SELECT COUNT(*) FROM dbo.membership_plan_upgrade"),
                    "Migration must not seed path business data");
            assertEquals(1, scalar(statement, "SELECT COUNT(*) FROM sys.indexes WHERE object_id="
                    + "OBJECT_ID(N'dbo.membership_upgrade') AND name=N'ux_membership_upgrade_one_success_per_source' "
                    + "AND is_unique=1 AND has_filter=1"));
            assertEquals(4, scalar(statement, "SELECT COUNT(*) FROM sys.check_constraints WHERE parent_object_id IN "
                    + "(OBJECT_ID(N'dbo.membership'),OBJECT_ID(N'dbo.payment'),OBJECT_ID(N'dbo.membership_upgrade')) "
                    + "AND name IN (N'ck_membership_benefit_snapshot',N'ck_membership_paid_value_basis',"
                    + "N'ck_payment_membership_quote_snapshot',N'ck_membership_upgrade_frozen_quote')"));

            for (String table : TABLES) {
                assertEquals(0, differenceCount(statement,
                        "SELECT * FROM dbo." + table + " EXCEPT SELECT * FROM dbo.phase4c_baseline_" + table),
                        "Migration changed existing rows in " + table);
                assertEquals(0, differenceCount(statement,
                        "SELECT * FROM dbo.phase4c_baseline_" + table + " EXCEPT SELECT * FROM dbo." + table),
                        "Migration removed historical rows from " + table);
            }
            System.out.println("PASS Phase 4C migration and catalog verification; path table empty; historical rows unchanged");
        }
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

    private long differenceCount(Statement statement, String query) throws Exception {
        return scalar(statement, "SELECT COUNT_BIG(*) FROM (" + query + ") AS differences");
    }
}
