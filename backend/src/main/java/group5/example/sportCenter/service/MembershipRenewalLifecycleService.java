package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.RenewalAttemptCloseReason;
import group5.example.sportCenter.entity.RenewalIntentCloseReason;
import group5.example.sportCenter.entity.RenewalIntentStatus;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;

/** Scheduled Renewal lifecycle operations. No charge or Payment attempt is created here. */
@Service
public class MembershipRenewalLifecycleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MembershipRepository memberships;
    private final MembershipRenewalIntentRepository intents;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final MembershipRenewalIntentService intentService;
    private final NotificationService notifications;
    private final Clock clock;
    private final AuditLogService auditLogs;

    public MembershipRenewalLifecycleService(MembershipRepository memberships,
            MembershipRenewalIntentRepository intents, PaymentRepository payments, UserRepository users,
            MembershipRenewalIntentService intentService, NotificationService notifications, Clock clock,
            AuditLogService auditLogs) {
        this.memberships = memberships;
        this.intents = intents;
        this.payments = payments;
        this.users = users;
        this.intentService = intentService;
        this.notifications = notifications;
        this.clock = clock;
        this.auditLogs = auditLogs;
    }

    /** Catch-up scan: membership end dates from tomorrow through the third day from today. */
    public RunResult runDueLifecycle() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        int created = 0;
        int inactive = 0;
        for (Integer id : memberships.findRenewalIntentCandidates(today.plusDays(1), today.plusDays(3))) {
            var result = intentService.createForScheduler(id);
            if (result.outcome() == MembershipRenewalIntentService.Outcome.CREATED) created++;
            if (result.outcome() == MembershipRenewalIntentService.Outcome.INACTIVE_PLAN) inactive++;
        }
        int expired = 0;
        int reminded = 0;
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        for (Integer intentId : intents.findExpiredOpenIds(now)) {
            if (expireIntent(intentId)) expired++;
        }
        for (Integer intentId : intents.findOpenIdsForReminder(now, now.plusDays(1))) {
            MembershipRenewalIntent intent = intents.findWithSourceMembershipMemberById(intentId).orElse(null);
            if (intent != null && intent.getValidUntil() != null
                    && !now.isBefore(intent.getValidUntil().minusDays(1))) {
                Integer memberId = intent.getSourceMembership().getMember().getId();
                notifications.createForUserOnce(memberId, "Renewal deadline approaching",
                        "Renewal Intent #" + intentId + " must be paid before " + intent.getValidUntil()
                                + " (exclusive, Vietnam time). Collected money after the deadline requires manual review.");
                reminded++;
            }
        }
        return new RunResult(created, inactive, expired, reminded);
    }

    /** Closes one unsettled source-period Intent and its pending attempts under Member → Intent → Payment locks. */
    @Transactional
    public void closeForAutoRenewDisabled(Integer sourceMembershipId) {
        closeSourceIntent(sourceMembershipId, RenewalIntentCloseReason.AUTO_RENEW_DISABLED, "AUTO_RENEW_DISABLED");
    }

    @Transactional
    public void closeForMembershipCancelled(Integer sourceMembershipId) {
        closeSourceIntent(sourceMembershipId, RenewalIntentCloseReason.MEMBER_CANCELLED, "SOURCE_MEMBERSHIP_CANCELLED");
    }

    private void closeSourceIntent(Integer sourceMembershipId, RenewalIntentCloseReason closeReason, String auditReason) {
        Membership source = memberships.findById(sourceMembershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        Integer memberId = source.getMember().getId();
        users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        MembershipRenewalIntent intent = intents.findBySourceMembershipIdForUpdate(sourceMembershipId).orElse(null);
        if (intent == null || intent.getIntentStatus() != RenewalIntentStatus.OPEN) return;
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        boolean collected = false;
        List<Payment> attempts = payments.findByRenewalIntentIdForUpdate(intent.getId());
        for (Payment attempt : attempts) {
            PaymentStatus previousStatus = attempt.getStatus();
            if (hasCollectionOrAcceptedPayment(attempt)) {
                if (attempt.getStatus() != PaymentStatus.REFUNDED) collected = true;
                if (attempt.getRenewalAttemptCloseReason() == null) {
                    attempt.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.MEMBER_CANCELLED);
                }
                if (attempt.getStatus() != PaymentStatus.REFUNDED) attempt.setStatus(PaymentStatus.REFUND_REQUIRED);
            } else if (attempt.getStatus() == PaymentStatus.PENDING) {
                attempt.setStatus(PaymentStatus.CANCELLED);
                attempt.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.MEMBER_CANCELLED);
            }
            payments.save(attempt);
            auditPaymentTransition(attempt, previousStatus, "Renewal Intent " + intent.getId() + " closed: " + auditReason);
        }
        intent.setIntentStatus(RenewalIntentStatus.CANCELLED);
        intent.setClosedAt(now);
        intent.setClosedReason(closeReason);
        intents.saveAndFlush(intent);
        auditLogs.record("CLOSE", "RENEWAL_INTENT", intent.getId(),
                "status=OPEN->CANCELLED;sourceMembership=" + sourceMembershipId + ";reason=" + auditReason);
        notifications.createForUserOnce(memberId, "Renewal opted out",
                "Renewal Intent #" + intent.getId() + " was closed after auto-renewal was turned off.");
        if (collected) {
            for (Payment attempt : attempts) {
                if (hasCollectionOrAcceptedPayment(attempt) && attempt.getStatus() != PaymentStatus.REFUNDED) {
                    notifications.renewalReconciliationRequired(attempt.getId(), memberId);
                }
            }
        }
    }

    @Transactional
    public boolean expireIntent(Integer intentId) {
        MembershipRenewalIntent initial = intents.findById(intentId).orElse(null);
        if (initial == null || initial.getSourceMembership() == null) return false;
        Integer memberId = initial.getSourceMembership().getMember().getId();
        users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        MembershipRenewalIntent intent = intents.findByIdForUpdate(intentId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (intent == null || intent.getIntentStatus() != RenewalIntentStatus.OPEN
                || intent.getValidUntil() == null || now.isBefore(intent.getValidUntil())) return false;
        List<Payment> attempts = payments.findByRenewalIntentIdForUpdate(intentId);
        boolean collected = false;
        for (Payment attempt : attempts) {
            PaymentStatus previousStatus = attempt.getStatus();
            if (hasCollectionOrAcceptedPayment(attempt)) {
                if (attempt.getStatus() != PaymentStatus.REFUNDED) collected = true;
                if (attempt.getRenewalAttemptCloseReason() == null) {
                    attempt.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.INTENT_EXPIRED);
                }
                if (attempt.getStatus() != PaymentStatus.REFUNDED) attempt.setStatus(PaymentStatus.REFUND_REQUIRED);
            } else if (attempt.getStatus() == PaymentStatus.PENDING) {
                attempt.setStatus(PaymentStatus.CANCELLED);
                attempt.setRenewalAttemptCloseReason(RenewalAttemptCloseReason.INTENT_EXPIRED);
            }
            payments.save(attempt);
            auditPaymentTransition(attempt, previousStatus, "Renewal Intent " + intentId + " reached its deadline");
        }
        intent.setIntentStatus(RenewalIntentStatus.EXPIRED);
        intent.setClosedAt(now);
        intent.setClosedReason(RenewalIntentCloseReason.DEADLINE_REACHED);
        intents.saveAndFlush(intent);
        auditLogs.record("EXPIRE", "RENEWAL_INTENT", intentId,
                "status=OPEN->EXPIRED;sourceMembership=" + intent.getSourceMembership().getId()
                        + ";reason=DEADLINE_REACHED");
        notifications.createForUserOnce(memberId, "Renewal expired",
                "Renewal Intent #" + intentId + " expired at its exclusive deadline. No successor was created without accepted payment.");
        if (collected) {
            for (Payment attempt : attempts) {
                if (hasCollectionOrAcceptedPayment(attempt) && attempt.getStatus() != PaymentStatus.REFUNDED) {
                    notifications.renewalReconciliationRequired(attempt.getId(), memberId);
                }
            }
        }
        return true;
    }

    private boolean hasCollectionOrAcceptedPayment(Payment payment) {
        return payment.hasRecordedRenewalCollection() || payment.getPaidAt() != null
                || payment.getRenewalCollectionConflictDetectedAt() != null
                || payment.getRenewalSettlementAcceptedAt() != null
                || payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                || payment.getStatus() == PaymentStatus.REFUNDED;
    }

    private void auditPaymentTransition(Payment payment, PaymentStatus previousStatus, String context) {
        if (previousStatus == payment.getStatus()) return;
        String action = payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                ? "RECONCILIATION_REQUIRED" : "RENEWAL_ATTEMPT_STATUS";
        auditLogs.record(action, "PAYMENT", payment.getId(), context + ";status="
                + previousStatus + "->" + payment.getStatus() + ";amount="
                + (payment.getAmount() == null ? "unknown" : payment.getAmount().stripTrailingZeros().toPlainString()));
    }

    public record RunResult(int intentsCreated, int inactivePlan, int intentsExpired, int remindersCreated) { }
}
