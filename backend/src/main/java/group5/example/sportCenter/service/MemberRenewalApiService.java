package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MemberRenewalAttemptActionResponse;
import group5.example.sportCenter.dto.MemberRenewalIntentResponse;
import group5.example.sportCenter.dto.MemberRenewalPaymentAttemptResponse;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;

/** Member-facing read and attempt actions; settlement remains exclusively on trusted server paths. */
@Service
public class MemberRenewalApiService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MembershipRenewalIntentRepository intents;
    private final MembershipRepository memberships;
    private final PaymentRepository payments;
    private final MembershipRenewalPaymentAttemptService attemptService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    public MemberRenewalApiService(
            MembershipRenewalIntentRepository intents,
            MembershipRepository memberships,
            PaymentRepository payments,
            MembershipRenewalPaymentAttemptService attemptService,
            PaymentService paymentService,
            PaymentGateway paymentGateway,
            Clock clock) {
        this.intents = intents;
        this.memberships = memberships;
        this.payments = payments;
        this.attemptService = attemptService;
        this.paymentService = paymentService;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MemberRenewalIntentResponse> listMine(Jwt jwt) {
        Integer memberId = requireMember(jwt);
        return intents.findAllBySourceMembership_Member_IdOrderByCreatedAtDesc(memberId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MemberRenewalIntentResponse getMine(Integer intentId, Jwt jwt) {
        Integer memberId = requireMember(jwt);
        return toResponse(ownedIntent(intentId, memberId));
    }

    /** Staff read for the Receptionist sales workflow; the service also enforces the staff role. */
    @Transactional(readOnly = true)
    public List<MemberRenewalIntentResponse> listForReceptionist(Integer memberId, Jwt jwt) {
        paymentService.authorizeCounterActor(jwt);
        return intents.findAllBySourceMembership_Member_IdOrderByCreatedAtDesc(memberId).stream()
                .map(this::toResponse).toList();
    }

    /** Creates only a COUNTER attempt for a staff-selected Member's existing Intent. */
    public MemberRenewalAttemptActionResponse startCounterAttemptForReceptionist(
            Integer memberId, Integer intentId, Jwt jwt) {
        paymentService.authorizeCounterActor(jwt);
        MembershipRenewalIntent intent = ownedIntent(intentId, memberId);
        Payment attempt = attemptService.createAttempt(intentId, PaymentMethod.COUNTER);
        MembershipRenewalIntent current = intents.findWithSourceMembershipMemberById(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("Renewal Intent not found"));
        return new MemberRenewalAttemptActionResponse(toResponse(current), toAttemptResponse(attempt), null);
    }

    /** Creates or safely resumes the one current ONLINE attempt; never settles or creates a Membership. */
    public MemberRenewalAttemptActionResponse startOnlineAttempt(Integer intentId, Jwt jwt, String clientIp) {
        Integer memberId = requireMember(jwt);
        MembershipRenewalIntent intent = ownedIntent(intentId, memberId);
        Payment attempt = currentResumableAttempt(intentId, intent);
        if (attempt == null) {
            try {
                attempt = attemptService.createAttempt(intentId, PaymentMethod.ONLINE);
            } catch (ResponseStatusException failure) {
                // A concurrent request may have won while this request waited for the Member/Intent locks.
                // Resume only if a matching live attempt is now persisted; otherwise preserve the original error.
                MembershipRenewalIntent latest = ownedIntent(intentId, memberId);
                attempt = currentResumableAttempt(intentId, latest);
                if (attempt == null) throw failure;
                intent = latest;
            }
        }
        String checkoutUrl = paymentGateway.createPaymentUrl(
                attempt.getTransactionCode(), attempt.getAmount(), clientIp);
        return new MemberRenewalAttemptActionResponse(toResponse(intent), toAttemptResponse(attempt), checkoutUrl);
    }

    private Integer requireMember(Jwt jwt) {
        var actor = paymentService.authenticatePaymentActor(jwt);
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owning Member can access Renewal Intents");
        }
        return actor.getId();
    }

    private MembershipRenewalIntent ownedIntent(Integer intentId, Integer memberId) {
        MembershipRenewalIntent intent = intents.findWithSourceMembershipMemberById(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("Renewal Intent not found"));
        if (intent.getSourceMembership() == null || intent.getSourceMembership().getMember() == null
                || !memberId.equals(intent.getSourceMembership().getMember().getId())) {
            // Return 404 so a Member cannot use the API to enumerate another Member's Intent IDs.
            throw new ResourceNotFoundException("Renewal Intent not found");
        }
        return intent;
    }

    private Payment currentResumableAttempt(Integer intentId, MembershipRenewalIntent intent) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (intent.getIntentStatus() != RenewalIntentStatus.OPEN || intent.getValidFrom() == null
                || now.isBefore(intent.getValidFrom()) || intent.getValidUntil() == null
                || !now.isBefore(intent.getValidUntil())) return null;
        var source = intent.getSourceMembership();
        if (source == null || !"ACTIVE".equalsIgnoreCase(source.getStatus())
                || !Boolean.TRUE.equals(source.getAutoRenew())
                || !Boolean.TRUE.equals(intent.getAutoRenewOptInSnapshot())
                || source.getStartDate() == null || source.getStartDate().isAfter(now.toLocalDate())
                || source.getEndDate() == null || !source.getEndDate().equals(intent.getSourceEndDateSnapshot())) {
            return null;
        }
        List<Payment> rows = payments.findByRenewalIntent_IdOrderByCreatedAtAsc(intentId);
        if (rows.stream().anyMatch(this::requiresManualReconciliation)
                || rows.stream().anyMatch(payment -> payment.getRenewalSettlementAcceptedAt() != null
                        || payment.getStatus() == PaymentStatus.SUCCESS
                        || payment.getStatus() == PaymentStatus.REFUNDED)) return null;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Payment payment = rows.get(i);
            if (payment.getStatus() == PaymentStatus.PENDING
                    && payment.getMethod() == PaymentMethod.ONLINE
                    && payment.getAttemptValidUntil() != null
                    && now.isBefore(payment.getAttemptValidUntil())
                    && !payment.hasRecordedRenewalCollection()
                    && payment.getRenewalCollectionConflictDetectedAt() == null
                    && payment.getPaidAt() == null) {
                return payment;
            }
        }
        return null;
    }

    private MemberRenewalIntentResponse toResponse(MembershipRenewalIntent intent) {
        List<Payment> attemptRows = payments.findByRenewalIntent_IdOrderByCreatedAtAsc(intent.getId());
        List<MemberRenewalPaymentAttemptResponse> attempts = attemptRows.stream()
                .map(this::toAttemptResponse).toList();
        String result = intent.getIntentStatus().name();
        boolean manualReconciliation = attemptRows.stream().anyMatch(this::requiresManualReconciliation);
        boolean refunded = attemptRows.stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.REFUNDED);
        if (intent.getIntentStatus() == RenewalIntentStatus.SETTLED) result = "SETTLED";
        if (refunded && !manualReconciliation) result = "REFUNDED";
        if (manualReconciliation) result = "REFUND_REQUIRED";
        Integer successorId = memberships.findByRenewalIntent_Id(intent.getId())
                .map(membership -> membership.getId()).orElse(null);
        return new MemberRenewalIntentResponse(
                intent.getId(),
                intent.getSourceMembership().getId(),
                intent.getPlanNameSnapshot(),
                intent.getPlanDescriptionSnapshot(),
                intent.getQuotedAmount(),
                intent.getDurationMonthsSnapshot(),
                intent.getCourseDiscountPercentSnapshot(),
                intent.getPtDiscountPercentSnapshot(),
                intent.getAllowPtPlanSnapshot(),
                intent.getAllowOneOnOneSnapshot(),
                intent.getSuccessorStartDate(),
                intent.getSuccessorEndDate(),
                intent.getCreatedAt(), intent.getValidFrom(), intent.getValidUntil(),
                intent.getIntentStatus().name(), result, successorId, attempts);
    }

    private MemberRenewalPaymentAttemptResponse toAttemptResponse(Payment payment) {
        return new MemberRenewalPaymentAttemptResponse(
                payment.getId(), payment.getMethod().name(), payment.getStatus().name(), payment.getAmount(),
                payment.getCreatedAt(), payment.getAttemptValidUntil(),
                payment.getRenewalAttemptCloseReason() == null ? null : payment.getRenewalAttemptCloseReason().name(),
                payment.hasRecordedRenewalCollection(), payment.getRenewalSettlementAcceptedAt() != null,
                requiresManualReconciliation(payment));
    }

    private boolean requiresManualReconciliation(Payment payment) {
        return payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getRenewalCollectionConflictDetectedAt() != null
                || (payment.hasRecordedRenewalCollection() && payment.getRenewalSettlementAcceptedAt() == null);
    }
}
