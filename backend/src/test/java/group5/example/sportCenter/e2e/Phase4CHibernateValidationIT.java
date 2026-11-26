package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

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
class Phase4CHibernateValidationIT {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext applicationContext;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void recheckExactCloneIdentity() throws Exception {
        Phase4CSqlServerGuard.verifyRuntime(dataSource, environment, applicationContext);
    }

    @Test
    void currentJpaMappingsValidateAgainstPhase4cSchema() {
        assertNotNull(entityManager.getMetamodel().entity(
                group5.example.sportCenter.entity.MembershipUpgradePath.class));
        assertNotNull(entityManager.getMetamodel().entity(
                group5.example.sportCenter.entity.MembershipUpgrade.class));
    }
}
