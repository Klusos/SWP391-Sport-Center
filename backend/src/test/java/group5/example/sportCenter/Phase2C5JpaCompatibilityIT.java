package group5.example.sportCenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Explicitly opt-in, clone-only compatibility checks. This deliberately avoids
 * application component scanning, services, schedulers, listeners, and runners.
 * It validates only mappings that exist in this application build.
 */
class Phase2C5JpaCompatibilityIT {

    private static final String EXPECTED_SERVER = "LAPTOP-416VBDLK";
    private static final String EXPECTED_DATABASE = "SportCenterDemo_Phase2C4_Rehearsal_20261119";
    private static final String OPT_IN = "sportcenter.phase2c5.sqlserver.opt-in";

    @Test
    void currentJpaMappingsValidateAgainstTheApprovedClone() {
        requireExplicitOptIn();
        try (ConfigurableApplicationContext context = startJpaOnlyContext()) {
            assertNotNull(context.getBean(EntityManagerFactory.class));
            verifyEffectiveDataSource(context.getBean(DataSource.class));
        }
    }

    @Test
    void legacyMembershipPaymentAndUpgradeWritesRollback() {
        requireExplicitOptIn();
        try (ConfigurableApplicationContext context = startJpaOnlyContext()) {
            EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            EntityManager entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
            MembershipRepository membershipRepository = context.getBean(MembershipRepository.class);
            PaymentRepository paymentRepository = context.getBean(PaymentRepository.class);
            MembershipUpgradeRepository upgradeRepository = context.getBean(MembershipUpgradeRepository.class);
            String membershipTransactionCode = "M" + UUID.randomUUID();
            String upgradeTransactionCode = "U" + UUID.randomUUID();
            Integer[] insertedIds = new TransactionTemplate(transactionManager).execute(status -> {
                Object[] existingReference = entityManager.createQuery(
                        "select m.member.id, m.plan.id from Membership m where m.member.id is not null",
                        Object[].class).setMaxResults(1).getSingleResult();
                User member = entityManager.getReference(User.class, (Integer) existingReference[0]);
                MembershipPlan plan = entityManager.getReference(MembershipPlan.class,
                        (Integer) existingReference[1]);

                Payment membershipPayment = paymentRepository.save(newPendingPayment(member, plan.getId(),
                        PaymentPurpose.MEMBERSHIP, membershipTransactionCode));
                Membership membership = new Membership();
                membership.setMember(member);
                membership.setPlan(plan);
                membership.setAutoRenew(false);
                membership.setStartDate(LocalDate.now().minusDays(2));
                membership.setEndDate(LocalDate.now().plusDays(28));
                membership.setStatus("EXPIRED");
                membership.setPurchasePayment(membershipPayment);
                membership = membershipRepository.save(membership);

                Payment upgradePayment = paymentRepository.save(newPendingPayment(member, plan.getId(),
                        PaymentPurpose.UPGRADE, upgradeTransactionCode));
                MembershipUpgrade upgrade = new MembershipUpgrade();
                upgrade.setPayment(upgradePayment);
                upgrade.setSourceMembership(membership);
                upgrade.setTargetPlan(plan);
                upgrade.setRemainingDays(1);
                upgrade.setExpiresAt(LocalDateTime.now().plusDays(1));
                upgrade.setCreatedAt(LocalDateTime.now());
                upgrade = upgradeRepository.save(upgrade);

                paymentRepository.flush();
                membershipRepository.flush();
                upgradeRepository.flush();
                Integer[] ids = {membershipPayment.getId(), membership.getId(),
                        upgradePayment.getId(), upgrade.getId()};
                status.setRollbackOnly();
                return ids;
            });

            assertNotNull(insertedIds);
            for (Integer id : insertedIds) assertNotNull(id);

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Long membershipPayments = entityManager.createQuery(
                        "select count(p) from Payment p where p.transactionCode = :code", Long.class)
                        .setParameter("code", membershipTransactionCode).getSingleResult();
                Long upgradePayments = entityManager.createQuery(
                        "select count(p) from Payment p where p.transactionCode = :code", Long.class)
                        .setParameter("code", upgradeTransactionCode).getSingleResult();
                Long memberships = entityManager.createQuery(
                        "select count(m) from Membership m where m.id = :id", Long.class)
                        .setParameter("id", insertedIds[1]).getSingleResult();
                Long upgradeAttempts = entityManager.createQuery(
                        "select count(u) from MembershipUpgrade u where u.id = :id", Long.class)
                        .setParameter("id", insertedIds[3]).getSingleResult();
                assertEquals(0L, membershipPayments);
                assertEquals(0L, upgradePayments);
                assertEquals(0L, memberships);
                assertEquals(0L, upgradeAttempts);
            });
        }
    }

    private static Payment newPendingPayment(User member, Integer planId, PaymentPurpose purpose,
            String transactionCode) {
        Payment payment = new Payment();
        payment.setMember(member);
        payment.setAmount(BigDecimal.ONE);
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(purpose);
        payment.setReferenceId(planId);
        payment.setAutoRenew(false);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode(transactionCode);
        return payment;
    }

    private static void requireExplicitOptIn() {
        assumeTrue("true".equalsIgnoreCase(System.getProperty(OPT_IN)),
                "Requires separate owner authorization and explicit clone-only opt-in");
    }

    private static ConfigurableApplicationContext startJpaOnlyContext() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (!isApprovedCloneUrl(url)) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL is not the approved Phase 2C.4 clone endpoint");
        }

        SpringApplication application = new SpringApplication(CloneJpaConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(new CloneIdentityGuard());
        return application.run(
                "--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.properties.hibernate.hbm2ddl.auto=validate",
                "--spring.jpa.generate-ddl=false",
                "--spring.sql.init.mode=never",
                "--spring.flyway.enabled=false",
                "--spring.liquibase.enabled=false",
                "--spring.task.scheduling.enabled=false",
                "--sportcenter.scheduling.enabled=false",
                "--spring.main.banner-mode=off");
    }

    private static boolean isApprovedCloneUrl(String url) {
        if (url == null || !url.equals(System.getenv("SPRING_DATASOURCE_URL"))) return false;
        String[] pieces = url.split(";");
        if (!pieces[0].equalsIgnoreCase("jdbc:sqlserver://127.0.0.1:1433")) return false;
        String database = null;
        boolean encrypt = false;
        boolean trustCert = false;
        for (int index = 1; index < pieces.length; index++) {
            int equals = pieces[index].indexOf('=');
            if (equals <= 0) return false;
            String key = pieces[index].substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = pieces[index].substring(equals + 1).trim();
            switch (key) {
                case "databasename" -> {
                    if (database != null) return false;
                    database = value;
                }
                case "encrypt" -> encrypt = "true".equalsIgnoreCase(value);
                case "trustservercertificate" -> trustCert = "true".equalsIgnoreCase(value);
                case "instancename", "failoverpartner", "database", "user", "password", "accesstoken",
                        "authentication" -> { return false; }
                default -> { return false; }
            }
        }
        return EXPECTED_DATABASE.equals(database) && encrypt && trustCert;
    }

    private static void verifyIdentity(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT CONVERT(sysname,SERVERPROPERTY('ServerName')), @@SERVERNAME, DB_NAME(), "
                                + "(SELECT state_desc FROM sys.databases WHERE name=DB_NAME())")) {
            if (!result.next()
                    || !EXPECTED_SERVER.equalsIgnoreCase(result.getString(1))
                    || !EXPECTED_SERVER.equalsIgnoreCase(result.getString(2))
                    || !EXPECTED_DATABASE.equals(result.getString(3))
                    || !"ONLINE".equalsIgnoreCase(result.getString(4))) {
                throw new IllegalStateException("Connected database identity is not the approved clone");
            }
        }
    }

    private static void verifyEffectiveDataSource(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            verifyIdentity(connection);
        } catch (Exception exception) {
            throw new IllegalStateException("Effective JPA datasource identity validation failed", exception);
        }
    }

    private static final class CloneIdentityGuard
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var environment = context.getEnvironment();
            String url = environment.getProperty("spring.datasource.url");
            if (!isApprovedCloneUrl(url)
                    || !"validate".equalsIgnoreCase(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                    || !"validate".equalsIgnoreCase(
                            environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto"))
                    || Boolean.parseBoolean(environment.getProperty("spring.jpa.generate-ddl", "false"))
                    || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode"))
                    || Boolean.parseBoolean(environment.getProperty("spring.flyway.enabled", "false"))
                    || Boolean.parseBoolean(environment.getProperty("spring.liquibase.enabled", "false"))) {
                throw new IllegalStateException("Test context is not strictly pinned to read-only clone validation");
            }

            String username = environment.getProperty("spring.datasource.username");
            String password = environment.getProperty("spring.datasource.password");
            if (!hasValue(username) || !hasValue(password) || username.contains("${") || password.contains("${")) {
                throw new IllegalStateException("SQL Server credentials are unavailable; no connection was attempted");
            }

            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                verifyIdentity(connection);
            } catch (Exception exception) {
                throw new IllegalStateException("Clone identity check failed before Hibernate initialization");
            }
        }

        private static boolean hasValue(String value) {
            return value != null && !value.isBlank();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = TaskSchedulingAutoConfiguration.class)
    @EntityScan(basePackageClasses = Membership.class)
    @EnableJpaRepositories(basePackageClasses = MembershipRepository.class)
    static class CloneJpaConfiguration {
        @org.springframework.context.annotation.Bean
        static BeanFactoryPostProcessor rejectSchedulingAndStartupRunners() {
            return beanFactory -> {
                if (beanFactory.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(org.springframework.boot.ApplicationRunner.class, false, false).length > 0
                        || beanFactory.getBeanNamesForType(org.springframework.boot.CommandLineRunner.class, false, false).length > 0) {
                    throw new IllegalStateException("Scheduled processor or startup writer detected");
                }
            };
        }
    }
}
