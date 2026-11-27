package group5.example.sportCenter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class DataSourceConfigurationTest {
    private static final String NORMAL_DATABASE_URL =
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=SportCenterDemo;encrypt=true;trustServerCertificate=true";
    private static final String CONFIGURED_QA_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=ExplicitTestTarget;encrypt=true;trustServerCertificate=true";

    @Test
    void normalDatasourceUsesLocalDevelopmentDatabaseDefault() throws IOException {
        Properties application = applicationProperties();
        assertEquals(NORMAL_DATABASE_URL, application.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}", application.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}", application.getProperty("spring.datasource.password"));
    }

    @Test
    void processEnvironmentCanSelectAnExplicitQaDatabase() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource("processEnvironment",
                Map.of("SPRING_DATASOURCE_URL", CONFIGURED_QA_URL)));
        sources.addLast(new PropertiesPropertySource("application", applicationProperties()));

        assertEquals(CONFIGURED_QA_URL,
                new PropertySourcesPropertyResolver(sources).getProperty("spring.datasource.url"));
    }

    @Test
    void commandLineDatasourceOverrideTakesPrecedence() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("commandLine", Map.of("spring.datasource.url", CONFIGURED_QA_URL)));
        sources.addLast(new PropertiesPropertySource("application", applicationProperties()));

        assertEquals(CONFIGURED_QA_URL,
                new PropertySourcesPropertyResolver(sources).getProperty("spring.datasource.url"));
    }

    @Test
    void approvedNormalDefaultsAreDirectApplicationProperties() throws IOException {
        Properties application = applicationProperties();

        assertEquals("sportCenter", application.getProperty("spring.application.name"));
        assertEquals("8080", application.getProperty("server.port"));
        assertEquals("true", application.getProperty("sportcenter.scheduling.enabled"));
        assertEquals("true", application.getProperty("sportcenter.renewal.scheduler.enabled"));
        assertEquals("false", application.getProperty("sportcenter.pt.outcome-fallback.enabled"));
        assertEquals("0 0 0 * * *", application.getProperty("sportcenter.membership.scheduler.cron"));
        assertEquals("0 */15 * * * *", application.getProperty("sportcenter.renewal.scheduler.cron"));
        assertEquals("Asia/Ho_Chi_Minh", application.getProperty("sportcenter.membership.scheduler.time-zone"));
        assertEquals("Asia/Ho_Chi_Minh", application.getProperty("sportcenter.renewal.scheduler.time-zone"));
        assertEquals("60000", application.getProperty("app.course-registration.expiration-sweep-interval-ms"));
        assertEquals("60000", application.getProperty("app.pt.expiration-sweep-interval-ms"));
        assertEquals("60000", application.getProperty("sportcenter.pt.outcome-fallback.interval-ms"));
        assertEquals("validate", application.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("${IMAGE_STORAGE_DIRECTORY:uploads}", application.getProperty("app.images.storage-directory"));
        assertEquals("5MB", application.getProperty("app.images.max-file-size"));
        assertEquals("500000", application.getProperty("app.pt.session-base-price"));
        assertEquals("false", application.getProperty("app.payment.mock-gateway.enabled"));
        assertEquals("vnpay", application.getProperty("app.payment.provider"));
    }

    @Test
    void processLocalPaymentPropertiesOverrideNormalProviderDefaults() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource("processEnvironment", Map.of(
                "APP_PAYMENT_PROVIDER", "mock",
                "APP_PAYMENT_MOCK_GATEWAY_ENABLED", "true",
                "SPORTCENTER_SCHEDULING_ENABLED", "false",
                "SPORTCENTER_RENEWAL_SCHEDULER_ENABLED", "false",
                "SPORTCENTER_PT_OUTCOME_FALLBACK_ENABLED", "false",
                "SPRING_JPA_HIBERNATE_DDL_AUTO", "validate")));
        sources.addLast(new PropertiesPropertySource("application", applicationProperties()));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertEquals("mock", resolver.getProperty("app.payment.provider"));
        assertEquals("true", resolver.getProperty("app.payment.mock-gateway.enabled"));
        assertEquals("false", resolver.getProperty("sportcenter.scheduling.enabled"));
        assertEquals("false", resolver.getProperty("sportcenter.renewal.scheduler.enabled"));
        assertEquals("false", resolver.getProperty("sportcenter.pt.outcome-fallback.enabled"));
        assertEquals("validate", resolver.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    private Properties applicationProperties() throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    }
}
