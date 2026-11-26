package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class Phase4CSqlServerGuardTest {
    @Test
    void acceptsOnlyTheExactPhase4CConfiguration() {
        MockEnvironment environment = validEnvironment();
        assertDoesNotThrow(() -> Phase4CSqlServerGuard.verifyConfiguration(environment));
        environment.setProperty("spring.datasource.url",
                "jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterPhase4C_Integration_20261120;encrypt=true;trustServerCertificate=true");
        assertThrows(IllegalStateException.class, () -> Phase4CSqlServerGuard.verifyConfiguration(environment));
    }

    @Test
    void rejectsAnIncorrectConnectedDatabaseIdentity() {
        assertThrows(AssertionError.class, () -> Phase4CSqlServerGuard.verifyIdentityValues(
                Phase4CSqlServerGuard.SERVER, null, "SportCenterPhase4C_Integration_20261120", "ONLINE", 1));
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", Phase4CSqlServerGuard.URL)
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.jpa.database-platform", "org.hibernate.dialect.SQLServerDialect")
                .withProperty("sportcenter.scheduling.enabled", "false")
                .withProperty("sportcenter.renewal.scheduler.enabled", "false")
                .withProperty("sportcenter.pt.outcome-fallback.enabled", "false")
                .withProperty("app.payment.provider", "mock")
                .withProperty("app.payment.mock-gateway.enabled", "true");
    }
}
