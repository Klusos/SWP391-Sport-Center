package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentCreationSource;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:renewalCollectionJpa;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("phase1")
@Import({ MembershipRenewalCollectionPersistenceService.class, MembershipRenewalCollectionService.class,
        MembershipRenewalSettlementService.class, MembershipRenewalCollectionPersistenceTest.FixedClockConfiguration.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MembershipRenewalCollectionPersistenceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDate SOURCE_END = LocalDate.of(2026, 11, 23);
    private static final BigDecimal PRICE = new BigDecimal("800000.00");

    @Autowired MembershipRenewalCollectionPersistenceService persistence;
    @Autowired MembershipRenewalCollectionService collectionService;
    @Autowired MembershipRenewalSettlementService settlement;
    @Autowired UserRepository users;
    @Autowired MembershipPlanRepository plans;
    @Autowired MembershipRenewalIntentRepository intents;
    @Autowired PaymentRepository payments;
    @MockitoSpyBean MembershipRepository memberships;
    @MockitoBean NotificationService notifications;

    private User member;
    private MembershipPlan plan;
    private Membership source;
    private MembershipRenewalIntent intent;
    private Payment payment;
    private User counterStaff;
    private final List<Payment> extraPayments = new ArrayList<>();

    @BeforeEach
    void createIsolatedFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        member = new User(); member.setName("Renewal evidence test"); member.setEmail(suffix + "@renewal.test");
        member.setPasswordHash("test-only"); member.setRole("MEMBER"); member.setStatus("ACTIVE");
        member = users.saveAndFlush(member);

        plan = new MembershipPlan(); plan.setName("Evidence test plan"); plan.setPrice(PRICE);
        plan.setDurationMonths(2); plan.setCourseDiscountPercent(new BigDecimal("10.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
        plan.setAllowPersonalTrainingPlan(true); plan.setAllowOneOnOneCoaching(true); plan.setStatus("ACTIVE");
        plan = plans.saveAndFlush(plan);

        source = new Membership(); source.setMember(member); source.setPlan(plan); source.setAutoRenew(true);
        source.setStatus("ACTIVE"); source.setStartDate(SOURCE_END.minusMonths(1)); source.setEndDate(SOURCE_END);
        source = memberships.saveAndFlush(source);

        intent = new MembershipRenewalIntent(); intent.setSourceMembership(source); intent.setTargetPlan(plan);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true); intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(NOW.minusDays(2)); intent.setValidFrom(SOURCE_END.minusDays(3).atStartOfDay());
        intent.setValidUntil(SOURCE_END.atStartOfDay()); intent.setQuoteCapturedAt(NOW.minusDays(2));
        intent.setSourceEndDateSnapshot(SOURCE_END); intent.setSuccessorStartDate(SOURCE_END);
        intent.setSuccessorEndDate(SOURCE_END.plusMonths(2)); intent.setPlanNameSnapshot("Captured plan");
        intent.setPlanDescriptionSnapshot("Captured description"); intent.setPlanPriceSnapshot(PRICE);
        intent.setQuotedAmount(PRICE); intent.setDurationMonthsSnapshot(2);
        intent.setCourseDiscountPercentSnapshot(new BigDecimal("10.00"));
        intent.setPtDiscountPercentSnapshot(new BigDecimal("5.00"));
        intent.setAllowPtPlanSnapshot(true); intent.setAllowOneOnOneSnapshot(true);
        intent = intents.saveAndFlush(intent);

        payment = new Payment(); payment.setMember(member); payment.setAmount(PRICE);
        payment.setMethod(PaymentMethod.ONLINE); payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId()); payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-REN-" + suffix.substring(0, 18)); payment.setCreatedAt(NOW.minusMinutes(5));
        payment.setAttemptValidUntil(NOW.plusMinutes(10)); payment.setRenewalIntent(intent);
        payment = payments.saveAndFlush(payment);
    }

    @AfterEach
    void removeOnlyThisTestFixture() {
        if (intent == null) return;
        memberships.findByRenewalIntent_Id(intent.getId()).ifPresent(row -> {
            memberships.delete(row); memberships.flush();
        });
        intents.findById(intent.getId()).ifPresent(row -> {
            row.setIntentStatus(RenewalIntentStatus.CANCELLED); row.setSettledPayment(null); row.setSettledAt(null);
            row.setClosedAt(NOW); row.setClosedReason(group5.example.sportCenter.entity.RenewalIntentCloseReason.AUTO_RENEW_DISABLED);
            intents.saveAndFlush(row);
        });
        payments.findById(payment.getId()).ifPresent(payments::delete); payments.flush();
        extraPayments.forEach(row -> payments.findById(row.getId()).ifPresent(payments::delete)); payments.flush();
        intents.findById(intent.getId()).ifPresent(intents::delete); intents.flush();
        memberships.findById(source.getId()).ifPresent(memberships::delete); memberships.flush();
        plans.findById(plan.getId()).ifPresent(plans::delete); plans.flush();
        users.findById(member.getId()).ifPresent(users::delete); users.flush();
        if (counterStaff != null) {
            users.findById(counterStaff.getId()).ifPresent(users::delete); users.flush();
        }
    }

    @Test
    void firstVerifiedCollectionIsCommittedAndDuplicateIsIdempotent() {
        RenewalSettlementEvidence evidence = online("provider-100");

        var first = persistence.recordFirstCollection(evidence);
        var duplicate = persistence.recordFirstCollection(evidence);

        assertEquals(RenewalCollectionCaptureResult.Status.RECORDED, first.status());
        assertEquals(RenewalCollectionCaptureResult.Status.IDEMPOTENT, duplicate.status());
        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertEquals("provider-100", stored.getRenewalProviderTransactionReference());
        assertEquals(0, PRICE.compareTo(stored.getRenewalVerifiedCollectionAmount()));
        assertEquals("VND", stored.getRenewalVerifiedCollectionCurrency());
        assertNotNull(stored.getRenewalCollectionVerifiedAt());
        assertEquals(NOW.minusMinutes(1), stored.getPaidAt());
        assertEquals(PaymentStatus.PENDING, stored.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());
    }

    @Test
    void duplicateSignedCollectionWithMissingProviderReferenceStaysIdempotentForManualReview() {
        RenewalSettlementEvidence evidence = new RenewalSettlementEvidence(intent.getId(), payment.getId(),
                PaymentMethod.ONLINE, RenewalSettlementEvidence.CollectionOutcome.COLLECTED, true,
                NOW.minusMinutes(1), RenewalSettlementEvidence.PaidAtEvidence.VERIFIED_PROVIDER_TIME,
                "VNPAY_SIGNATURE_VERIFIED", payment.getTransactionCode(), "provider reference missing",
                PRICE, "VND", null, null);

        var first = persistence.recordFirstCollection(evidence);
        var repeated = persistence.recordFirstCollection(evidence);

        assertEquals(RenewalCollectionCaptureResult.Status.RECORDED, first.status());
        assertEquals(RenewalCollectionCaptureResult.Status.IDEMPOTENT, repeated.status());
        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertNotNull(stored.getRenewalCollectionVerifiedAt());
        assertNull(stored.getRenewalProviderTransactionReference());
        assertNull(stored.getRenewalCollectionConflictDetectedAt());
        assertEquals(PaymentStatus.PENDING, stored.getStatus());
    }

    @Test
    void conflictingCollectionKeepsFirstFactsAndMarksManualReconciliation() {
        persistence.recordFirstCollection(online("provider-first"));
        RenewalSettlementEvidence conflicting = RenewalSettlementEvidence.verifiedOnlineCollection(
                intent.getId(), payment.getId(), new BigDecimal("799000.00"), "VND", "provider-second",
                NOW, "VNPAY_SIGNATURE_VERIFIED", "provider-second");

        var result = persistence.recordFirstCollection(conflicting);

        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertEquals(RenewalCollectionCaptureResult.Status.CONFLICT, result.status());
        assertEquals("provider-first", stored.getRenewalProviderTransactionReference());
        assertEquals(0, PRICE.compareTo(stored.getRenewalVerifiedCollectionAmount()));
        assertNotNull(stored.getRenewalCollectionConflictDetectedAt());
        assertEquals(PaymentStatus.REFUND_REQUIRED, stored.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());
    }

    @Test
    void collectionReportedForWrongIntentIsPreservedAndBlockedForManualHandling() {
        RenewalSettlementEvidence misrouted = RenewalSettlementEvidence.verifiedOnlineCollection(
                intent.getId() + 1000, payment.getId(), PRICE, "VND", "99100003",
                NOW.minusMinutes(1), "VNPAY_SIGNATURE_VERIFIED", "99100003");

        var result = persistence.recordFirstCollection(misrouted);

        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertEquals(RenewalCollectionCaptureResult.Status.CONFLICT, result.status());
        assertEquals("99100003", stored.getRenewalProviderTransactionReference());
        assertNotNull(stored.getRenewalCollectionVerifiedAt());
        assertNotNull(stored.getRenewalCollectionConflictDetectedAt());
        assertEquals(PaymentStatus.REFUND_REQUIRED, stored.getStatus());
    }

    @Test
    void providerReferenceAlreadyUsedByAnotherRenewalAttemptBecomesManualConflict() {
        Payment second = new Payment(); second.setMember(member); second.setAmount(PRICE);
        second.setMethod(PaymentMethod.ONLINE); second.setPurpose(PaymentPurpose.MEMBERSHIP);
        second.setReferenceId(plan.getId()); second.setStatus(PaymentStatus.PENDING);
        second.setTransactionCode("TXN-REN-SECOND-" + UUID.randomUUID().toString().substring(0, 8));
        second.setCreatedAt(NOW.minusMinutes(2)); second.setAttemptValidUntil(NOW.plusMinutes(13));
        second.setRenewalIntent(intent);
        second = payments.saveAndFlush(second); extraPayments.add(second);
        persistence.recordFirstCollection(RenewalSettlementEvidence.verifiedOnlineCollection(
                intent.getId(), payment.getId(), PRICE, "VND", "99100004", NOW.minusMinutes(1),
                "VNPAY_SIGNATURE_VERIFIED", "99100004"));

        var result = persistence.recordFirstCollection(RenewalSettlementEvidence.verifiedOnlineCollection(
                intent.getId(), second.getId(), PRICE, "VND", "99100004", NOW.minusMinutes(1),
                "VNPAY_SIGNATURE_VERIFIED", "99100004"));

        assertEquals(RenewalCollectionCaptureResult.Status.CONFLICT, result.status());
        Payment storedSecond = payments.findById(second.getId()).orElseThrow();
        assertNotNull(storedSecond.getRenewalCollectionConflictDetectedAt());
        assertEquals(PaymentStatus.REFUND_REQUIRED, storedSecond.getStatus());
        assertNull(storedSecond.getRenewalProviderTransactionReference());
    }

    @Test
    void signedCollectionWithBadAmountCurrencyOrMissingProviderDataIsRecordedButNotSettled() {
        RenewalSettlementEvidence badAmount = RenewalSettlementEvidence.verifiedOnlineCollection(
                intent.getId(), payment.getId(), new BigDecimal("700000.00"), "USD", "provider-bad",
                NOW.minusMinutes(1), "VNPAY_SIGNATURE_VERIFIED", "provider-bad");

        var result = collectionService.recordAndSettle(badAmount);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertEquals(new BigDecimal("700000.00"), stored.getRenewalVerifiedCollectionAmount());
        assertEquals("USD", stored.getRenewalVerifiedCollectionCurrency());
        assertNotNull(stored.getRenewalCollectionVerifiedAt());
        assertEquals(PaymentStatus.REFUND_REQUIRED, stored.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());
    }

    @Test
    void missingProviderReferenceIsDurableButCannotSettle() {
        RenewalSettlementEvidence missingReference = new RenewalSettlementEvidence(intent.getId(), payment.getId(),
                PaymentMethod.ONLINE, RenewalSettlementEvidence.CollectionOutcome.COLLECTED, true,
                NOW.minusMinutes(1), RenewalSettlementEvidence.PaidAtEvidence.VERIFIED_PROVIDER_TIME,
                "VNPAY_SIGNATURE_VERIFIED", payment.getTransactionCode(), null, PRICE, "VND", null, null);

        RenewalSettlementResult result = collectionService.recordAndSettle(missingReference);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertNotNull(stored.getRenewalCollectionVerifiedAt());
        assertNull(stored.getRenewalProviderTransactionReference());
        assertEquals(PaymentStatus.REFUND_REQUIRED, stored.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());
    }

    @Test
    void malformedPaidTimeIsDurableWithoutInventingPaidAt() {
        RenewalSettlementEvidence malformedTime = RenewalSettlementEvidence.verifiedCollectionWithTimestampIssue(
                intent.getId(), payment.getId(), PRICE, "VND", "provider-bad-time",
                RenewalSettlementEvidence.PaidAtEvidence.MALFORMED, "VNPAY_SIGNATURE_VERIFIED",
                "provider-bad-time", "provider timestamp malformed");

        RenewalSettlementResult result = collectionService.recordAndSettle(malformedTime);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertEquals(group5.example.sportCenter.entity.RenewalPaidTimeEvidenceType.MALFORMED,
                stored.getRenewalPaidTimeEvidenceType());
        assertNull(stored.getPaidAt());
        assertNotNull(stored.getRenewalCollectionVerifiedAt());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());
    }

    @Test
    void counterCollectionUsesServerTimeAndExistingProcessedByField() {
        counterStaff = new User(); counterStaff.setName("Counter staff");
        counterStaff.setEmail(UUID.randomUUID() + "@counter.test"); counterStaff.setPasswordHash("test-only");
        counterStaff.setRole("RECEPTIONIST"); counterStaff.setStatus("ACTIVE");
        counterStaff = users.saveAndFlush(counterStaff);
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setAttemptValidUntil(intent.getValidUntil());
        payment = payments.saveAndFlush(payment);
        RenewalSettlementEvidence counter = RenewalSettlementEvidence.verifiedCounterCollection(
                intent.getId(), payment.getId(), PRICE, counterStaff.getId(), "counter-confirmation");

        var first = persistence.recordFirstCollection(counter);
        LocalDateTime recordedAt = payments.findById(payment.getId()).orElseThrow().getPaidAt();
        var duplicate = persistence.recordFirstCollection(counter);
        User secondReceptionist = new User(); secondReceptionist.setName("Second counter staff");
        secondReceptionist.setEmail(UUID.randomUUID() + "@counter.test");
        secondReceptionist.setPasswordHash("test-only"); secondReceptionist.setRole("RECEPTIONIST");
        secondReceptionist.setStatus("ACTIVE");
        secondReceptionist = users.saveAndFlush(secondReceptionist);
        RenewalSettlementEvidence repeatedByAnotherStaff = RenewalSettlementEvidence.verifiedCounterCollection(
                intent.getId(), payment.getId(), PRICE, secondReceptionist.getId(), "counter-confirmation");
        var repeatedFromAnotherStaff = persistence.recordFirstCollection(repeatedByAnotherStaff);

        assertEquals(RenewalCollectionCaptureResult.Status.RECORDED, first.status());
        assertEquals(RenewalCollectionCaptureResult.Status.IDEMPOTENT, duplicate.status());
        assertEquals(RenewalCollectionCaptureResult.Status.IDEMPOTENT, repeatedFromAnotherStaff.status());
        assertEquals(counterStaff.getId(), repeatedFromAnotherStaff.evidence().counterActorId(),
                "The first authenticated processor remains the durable processedBy evidence");
        assertEquals(NOW, recordedAt);
        Payment stored = payments.findById(payment.getId()).orElseThrow();
        assertEquals(counterStaff.getId(), stored.getProcessedBy().getId());
        assertEquals(group5.example.sportCenter.entity.RenewalPaidTimeEvidenceType.SERVER_CONFIRMATION,
                stored.getRenewalPaidTimeEvidenceType());
        assertEquals(NOW, stored.getPaidAt());
    }

    @Test
    void processCrashAfterEvidenceCommitCanResumeAfterServiceRecreation() {
        RenewalSettlementEvidence evidence = online("99100001");
        persistence.recordFirstCollection(evidence); // Simulates process exit before settlement call.

        assertTrue(payments.findRecordedRenewalCollectionsPendingReview().contains(payment.getId()));

        RenewalSettlementResult recovered = collectionService.resumeRecordedSettlement(payment.getId());

        assertEquals(RenewalSettlementOutcome.ACCEPTED, recovered.outcome());
        assertEquals(1, memberships.findByRenewalIntent_Id(intent.getId()).stream().count());
        assertEquals(PaymentStatus.SUCCESS, payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    @Test
    void evidenceSurvivesSettlementRollbackAndSameCallbackCanRetry() {
        doThrow(new IllegalStateException("controlled successor insert failure"))
                .when(memberships).saveAndFlush(any(Membership.class));
        RenewalSettlementEvidence evidence = online("99100002");

        assertThrows(IllegalStateException.class, () -> collectionService.recordAndSettle(evidence));

        Payment afterRollback = payments.findById(payment.getId()).orElseThrow();
        assertEquals("99100002", afterRollback.getRenewalProviderTransactionReference());
        assertNotNull(afterRollback.getRenewalCollectionVerifiedAt());
        assertEquals(PaymentStatus.PENDING, afterRollback.getStatus());
        assertTrue(memberships.findByRenewalIntent_Id(intent.getId()).isEmpty());

        reset(memberships);
        RenewalSettlementResult retry = collectionService.resumeRecordedSettlement(payment.getId());
        assertEquals(RenewalSettlementOutcome.ACCEPTED, retry.outcome());
        assertEquals(1, memberships.findByRenewalIntent_Id(intent.getId()).stream().count());
    }

    @Test
    void legacyPaymentCanKeepAllNewCollectionColumnsNull() {
        Payment legacy = new Payment(); legacy.setMember(member); legacy.setAmount(new BigDecimal("100.00"));
        legacy.setMethod(PaymentMethod.ONLINE); legacy.setPurpose(PaymentPurpose.COURSE_REGISTRATION);
        legacy.setReferenceId(999); legacy.setStatus(PaymentStatus.SUCCESS);
        legacy.setTransactionCode("TXN-LEGACY-" + UUID.randomUUID().toString().substring(0, 12));
        legacy.setCreatedAt(NOW.minusDays(10));
        legacy = payments.saveAndFlush(legacy);

        Payment stored = payments.findById(legacy.getId()).orElseThrow();
        assertNull(stored.getRenewalCollectionVerifiedAt());
        assertNull(stored.getRenewalProviderTransactionReference());
        assertNull(stored.getRenewalVerifiedCollectionAmount());
        assertNull(stored.getRenewalVerifiedCollectionCurrency());
        assertNull(stored.getRenewalPaidTimeEvidenceType());
        assertNull(stored.getRenewalCollectionConflictDetectedAt());
        payments.delete(stored); payments.flush();
    }

    private RenewalSettlementEvidence online(String providerReference) {
        return RenewalSettlementEvidence.verifiedOnlineCollection(intent.getId(), payment.getId(), PRICE, "VND",
                providerReference, NOW.minusMinutes(1), "VNPAY_SIGNATURE_VERIFIED", providerReference);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedRenewalClock() { return Clock.fixed(NOW.atZone(VN).toInstant(), VN); }
    }
}
