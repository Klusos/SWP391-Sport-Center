package group5.example.sportCenter.e2e;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=" + CloneDatasourceSafetyGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sportcenter.scheduling.enabled=false"
})
@ContextConfiguration(initializers = CloneDatasourceSafetyGuard.class)
class CloneDatasourceIdentityDiagnosticTest {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void effectiveDataSourceIsCloneAndSchedulingInfrastructureIsAbsent() throws Exception {
        CloneDatasourceSafetyGuard.verifyRuntime(dataSource, environment, context);
    }
}
