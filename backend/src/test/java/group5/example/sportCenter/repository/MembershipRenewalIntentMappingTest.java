package group5.example.sportCenter.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalAttemptCloseReason;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;

class MembershipRenewalIntentMappingTest {
    // This persistence-only fixture exercises mapped columns; it does not invoke renewal business workflows.
    @Test
    void renewalQuoteAndRelationshipsPersistOnIsolatedH2() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url",
                        "jdbc:h2:mem:renewal-intent-mapping;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(MembershipPlan.class)
                .addAnnotatedClass(Membership.class)
                .addAnnotatedClass(Payment.class)
                .addAnnotatedClass(MembershipRenewalIntent.class);

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            LocalDateTime capturedAt = LocalDateTime.of(2026, 10, 29, 0, 0);
            Integer intentId;
            Integer sourceId;
            Integer planId;
            Integer paymentId;

            try (var session = sessionFactory.openSession()) {
                assertEquals("H2", session.doReturningWork(
                        connection -> connection.getMetaData().getDatabaseProductName()));
                var tx = session.beginTransaction();

                User member = new User();
                member.setName("Renewal mapping member");
                member.setEmail("renewal-mapping@example.test");
                member.setPasswordHash("not-used");
                member.setRole("MEMBER");
                member.setStatus("ACTIVE");
                session.persist(member);

                MembershipPlan plan = new MembershipPlan();
                plan.setName("Captured plan");
                plan.setDescription("Captured description");
                plan.setPrice(new BigDecimal("125000.00"));
                plan.setDurationMonths(1);
                plan.setCourseDiscountPercent(new BigDecimal("10.00"));
                plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
                plan.setAllowPersonalTrainingPlan(true);
                plan.setAllowOneOnOneCoaching(false);
                plan.setStatus("ACTIVE");
                session.persist(plan);

                Membership source = new Membership();
                source.setMember(member);
                source.setPlan(plan);
                source.setAutoRenew(true);
                source.setStartDate(LocalDate.of(2026, 10, 1));
                source.setEndDate(LocalDate.of(2026, 11, 1));
                source.setStatus("ACTIVE");
                session.persist(source);

                MembershipRenewalIntent intent = new MembershipRenewalIntent();
                intent.setSourceMembership(source);
                intent.setTargetPlan(plan);
                intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
                intent.setAutoRenewOptInSnapshot(true);
                intent.setIntentStatus(RenewalIntentStatus.OPEN);
                intent.setCreatedAt(capturedAt);
                intent.setValidFrom(capturedAt);
                intent.setValidUntil(LocalDateTime.of(2026, 11, 1, 0, 0));
                intent.setQuoteCapturedAt(capturedAt);
                intent.setSourceEndDateSnapshot(source.getEndDate());
                intent.setSuccessorStartDate(source.getEndDate());
                intent.setSuccessorEndDate(source.getEndDate().plusMonths(1));
                intent.setPlanNameSnapshot("Captured plan");
                intent.setPlanDescriptionSnapshot("Captured description");
                intent.setPlanPriceSnapshot(new BigDecimal("125000.00"));
                intent.setQuotedAmount(new BigDecimal("125000.00"));
                intent.setDurationMonthsSnapshot(1);
                intent.setCourseDiscountPercentSnapshot(new BigDecimal("10.00"));
                intent.setPtDiscountPercentSnapshot(new BigDecimal("5.00"));
                intent.setAllowPtPlanSnapshot(true);
                intent.setAllowOneOnOneSnapshot(false);
                session.persist(intent);
                session.flush();

                Payment attempt = new Payment();
                attempt.setMember(member);
                attempt.setAmount(new BigDecimal("125000.00"));
                attempt.setMethod(PaymentMethod.COUNTER);
                attempt.setPurpose(PaymentPurpose.MEMBERSHIP);
                attempt.setReferenceId(plan.getId());
                attempt.setStatus(PaymentStatus.CANCELLED);
                attempt.setTransactionCode("RENEWAL-MAPPING-ATTEMPT");
                attempt.setRenewalIntent(intent);
                attempt.setAttemptValidUntil(intent.getValidUntil());
                attempt.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.MEMBER_CANCELLED);
                session.persist(attempt);
                session.flush();

                intentId = intent.getId();
                sourceId = source.getId();
                planId = plan.getId();
                paymentId = attempt.getId();
                tx.commit();
            }

            try (var session = sessionFactory.openSession()) {
                MembershipRenewalIntent loaded = session.find(MembershipRenewalIntent.class, intentId);
                assertNotNull(loaded);
                assertEquals(sourceId, loaded.getSourceMembership().getId());
                assertEquals(planId, loaded.getTargetPlan().getId());
                assertEquals(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER, loaded.getCreationSource());
                assertEquals(RenewalIntentStatus.OPEN, loaded.getIntentStatus());
                assertEquals(new BigDecimal("125000.00"), loaded.getQuotedAmount());
                assertEquals(LocalDate.of(2026, 11, 1), loaded.getSuccessorStartDate());
                assertEquals("Captured description", loaded.getPlanDescriptionSnapshot());
                Payment loadedAttempt = session.find(Payment.class, paymentId);
                assertEquals(intentId, loadedAttempt.getRenewalIntent().getId());
                assertEquals(RenewalAttemptCloseReason.MEMBER_CANCELLED,
                        loadedAttempt.getRenewalAttemptCloseReason());
            }
        }
    }
}
