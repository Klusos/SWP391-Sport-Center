package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PaymentCheckoutAttemptRepository;
import group5.example.sportCenter.repository.MembershipUpgradePaymentEvidenceRepository;
import group5.example.sportCenter.repository.UserRepository;

/** Creates pending, method-neutral renewal requests. Settlement remains disabled until Stage C. */
@Service
public class MembershipRenewalService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCheckoutAttemptRepository attemptRepository;
    private final MembershipUpgradePaymentEvidenceRepository upgradeEvidenceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MembershipRenewalService(MembershipRepository membershipRepository,
            PaymentRepository paymentRepository, PaymentCheckoutAttemptRepository attemptRepository,
            MembershipUpgradePaymentEvidenceRepository upgradeEvidenceRepository,
            UserRepository userRepository, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.upgradeEvidenceRepository = upgradeEvidenceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** One transaction per scheduled invocation; membership row locks serialize overlapping runs. */
    @Transactional
    public int createDueRenewalRequests() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Integer> candidates = membershipRepository.findRenewalCandidateIds(today, today.plusDays(3));
        int created = 0;
        for (Integer id : candidates) {
            created += createForMembership(id, today);
        }
        return created;
    }

    /** Used by the opt-in endpoint to create promptly if the member enables inside the window. */
    @Transactional
    public int createRenewalRequestIfDue(Integer membershipId) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return createForMembership(membershipId, today);
    }

    private int createForMembership(Integer id, LocalDate today) {
        Membership snapshot = membershipRepository.findById(id).orElse(null);
        if (snapshot == null) return 0;
        Integer memberId = snapshot.getMember().getId();
        if (userRepository.findByIdForBookingUpdate(memberId).isEmpty()) return 0;
        if (hasUnresolvedUpgradePayment(memberId, Instant.now(clock))) return 0;
        Membership source = membershipRepository.findByIdForRenewalUpdate(id).orElse(null);
        if (!isEligible(source, today) || !isFinalPaidPeriod(source)) return 0;
        Payment existing = paymentRepository.findRenewalByMembershipIdForUpdate(id).orElse(null);
        if (existing != null) {
            attemptRepository.findByPaymentIdForUpdateOrderByCreatedAtAscIdAsc(existing.getId());
            return 0;
        }

        MembershipPlan plan = source.getPlan();
        var monthlyPrice = plan.getEffectiveMonthlyPrice();
        if (monthlyPrice == null || monthlyPrice.signum() <= 0) return 0;

        Payment payment = new Payment();
        payment.setMember(source.getMember());
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId());
        payment.setRenewalMembership(source);
        payment.setPurchasedMonths(1);
        payment.setMonthlyPriceSnapshot(monthlyPrice);
        payment.setAmount(monthlyPrice);
        payment.setMethod(null);
        payment.setAutoRenew(true);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment.setNote("Automatic renewal payment request; member must select a payment method");
        paymentRepository.save(payment);
        return 1;
    }

    /** Prevents the scheduler/opt-in path from creating a competing Renewal during Upgrade. */
    private boolean hasUnresolvedUpgradePayment(Integer memberId, Instant now) {
        List<Payment> drafts = paymentRepository.findByMember_IdAndPurposeAndStatusIn(memberId,
                PaymentPurpose.UPGRADE, List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED,
                        PaymentStatus.CANCELLED));
        boolean unresolved = false;
        for (Payment draft : drafts) {
            if (draft.getStatus() == PaymentStatus.REFUND_REQUIRED
                    || (draft.getStatus() == PaymentStatus.CANCELLED && draft.getPaidAt() != null)) {
                unresolved = true;
            } else if (draft.getStatus() == PaymentStatus.PENDING) {
                var evidence = upgradeEvidenceRepository.findByPayment_Id(draft.getId()).orElse(null);
                if (evidence == null || evidence.getExpiresAtUtc() == null || now.isBefore(evidence.getExpiresAtUtc())) {
                    unresolved = true;
                } else if (draft.getPaidAt() == null) {
                    // C2.5A draft has no checkout URL/provider evidence, so this expiry is safe to close.
                    draft.setStatus(PaymentStatus.CANCELLED);
                    draft.setNote("Unpaid Upgrade draft expired before checkout was enabled");
                    paymentRepository.save(draft);
                }
            }
        }
        return unresolved;
    }

    private boolean isEligible(Membership source, LocalDate today) {
        return source != null
                && "ACTIVE".equalsIgnoreCase(source.getStatus())
                && Boolean.TRUE.equals(source.getAutoRenew())
                && source.getSourcePayment() != null
                && source.getSourcePayment().getStatus() == PaymentStatus.SUCCESS
                && source.getStartDate() != null && !source.getStartDate().isAfter(today)
                && source.getEndDate() != null && source.getEndDate().isAfter(today)
                && !source.getEndDate().isAfter(today.plusDays(3));
    }

    private boolean isFinalPaidPeriod(Membership source) {
        List<Membership> paidPeriods = membershipRepository
                .findSuccessfulPaidPeriods(source.getMember().getId());
        return !paidPeriods.isEmpty() && paidPeriods.get(0).getId().equals(source.getId());
    }
}
