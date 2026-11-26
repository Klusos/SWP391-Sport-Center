package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** SQL Server catalog/constraint checks. Each path fixture is rolled back by DataJpaTest. */
@DataJpaTest(properties = {
        "spring.datasource.url=" + Phase4CSqlServerGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false",
        "sportcenter.scheduling.enabled=false",
        "app.payment.provider=mock",
        "app.payment.mock-gateway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase4CSqlServerGuard.class)
class Phase4CSqlServerUpgradePathConstraintIT {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext applicationContext;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void verifyTargetBeforeFixtureWork() throws Exception {
        Phase4CSqlServerGuard.verifyRuntime(dataSource, environment, applicationContext);
    }

    @Test
    void directedPathForeignKeysSelfLinkAndDuplicatePairAreEnforced() {
        List<Integer> planIds = jdbc.queryForList(
                "SELECT TOP (2) id FROM dbo.membership_plan WHERE status='ACTIVE' AND price > 0 ORDER BY id",
                Integer.class);
        assertTrue(planIds.size() >= 2, "Clone must contain two existing active positive-price Plans");
        int from = planIds.get(0);
        int to = planIds.get(1);

        jdbc.update("INSERT INTO dbo.membership_plan_upgrade (from_plan_id,to_plan_id,is_active) VALUES (?,?,1)",
                from, to);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.membership_plan_upgrade WHERE from_plan_id=? AND to_plan_id=?",
                Integer.class, from, to));

        jdbc.update("UPDATE dbo.membership_plan_upgrade SET is_active=0 WHERE from_plan_id=? AND to_plan_id=?",
                from, to);
        assertEquals(false, jdbc.queryForObject(
                "SELECT is_active FROM dbo.membership_plan_upgrade WHERE from_plan_id=? AND to_plan_id=?",
                Boolean.class, from, to));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO dbo.membership_plan_upgrade (from_plan_id,to_plan_id,is_active) VALUES (?,?,1)", from, from));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO dbo.membership_plan_upgrade (from_plan_id,to_plan_id,is_active) VALUES (?,?,1)", from, to));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO dbo.membership_plan_upgrade (from_plan_id,to_plan_id,is_active) VALUES (?,?,1)",
                Integer.MIN_VALUE, to));
    }
}
