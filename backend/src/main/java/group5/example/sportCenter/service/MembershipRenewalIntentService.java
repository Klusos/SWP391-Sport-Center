package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipRenewalIntent;
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

/** Internal scheduler-only Renewal quote creation. It is deliberately not wired to a scheduler or API. */
@Service
public class MembershipRenewalIntentService {
    public static final int RENEWAL_WINDOW_DAYS = 3;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<PaymentPurpose> CONFLICTING_PURPOSES =
            List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);
    private static final List<PaymentStatus> CONFLICTING_STATUSES =
            List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED);

    private final MembershipRepository membershipRepository;
    private final MembershipRenewalIntentRepository intentRepository;
    private final MembershipPlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public MembershipRenewalIntentService(
            MembershipRepository membershipRepository,
            MembershipRenewalIntentRepository intentRepository,
            MembershipPlanRepository planRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            Clock clock,
            NotificationService notificationService) {
        this.membershipRepository = membershipRepository;
        this.intentRepository = intentRepository;
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.notificationService = notificationService;
    }

    /**
     * Called only by a future scheduler integration. No API or current scheduler calls this method.
     * The Member row lock is the common serialization anchor used by Purchase and Upgrade creation.
     */
    @Transactional
    public CreationResult createForScheduler(Integer sourceMembershipId) {
        if (sourceMembershipId == null) {
            return CreationResult.of(Outcome.SOURCE_NOT_FOUND);
        }
        Membership initial = membershipRepository.findById(sourceMembershipId).orElse(null);
        if (initial == null || initial.getMember() == null || initial.getMember().getId() == null) {
            return CreationResult.of(Outcome.SOURCE_NOT_FOUND);
        }

        Integer memberId = initial.getMember().getId();
        User member = userRepository.findByIdForBookingUpdate(memberId).orElse(null);
        if (!activeMember(member)) {
            return CreationResult.of(Outcome.INELIGIBLE_SOURCE);
        }

        // Lock order begins Member -> existing Intent -> Payment checks -> Membership -> Plan.
        Optional<MembershipRenewalIntent> existing =
                intentRepository.findBySourceMembershipIdForUpdate(sourceMembershipId);
        if (existing.isPresent()) {
            return new CreationResult(Outcome.ALREADY_EXISTS, existing.get());
        }

        if (paymentRepository.existsByMember_IdAndPurposeInAndStatusIn(
                memberId, CONFLICTING_PURPOSES, CONFLICTING_STATUSES)) {
            return CreationResult.of(Outcome.CONFLICTING_PAYMENT);
        }

        Membership source = membershipRepository.findByIdForRenewalUpdate(sourceMembershipId).orElse(null);
        if (source == null || !source.getMember().getId().equals(memberId)
                || !Boolean.TRUE.equals(source.getAutoRenew())) {
            return CreationResult.of(Outcome.INELIGIBLE_SOURCE);
        }

        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        LocalDate today = now.toLocalDate();
        if (source.getEndDate() == null) {
            return CreationResult.of(Outcome.INELIGIBLE_SOURCE);
        }
        LocalDateTime validFrom = source.getEndDate().minusDays(RENEWAL_WINDOW_DAYS).atStartOfDay();
        LocalDateTime validUntil = source.getEndDate().atStartOfDay();
        if (now.isBefore(validFrom) || !now.isBefore(validUntil)) {
            return CreationResult.of(Outcome.OUTSIDE_WINDOW);
        }

        if (!"ACTIVE".equalsIgnoreCase(source.getStatus()) || source.getStartDate() == null
                || source.getStartDate().isAfter(today) || !source.getEndDate().isAfter(today)) {
            return CreationResult.of(Outcome.INELIGIBLE_SOURCE);
        }

        List<Membership> effectiveMemberships = membershipRepository
                .findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                        memberId, "ACTIVE", today, today);
        if (effectiveMemberships.size() != 1
                || !effectiveMemberships.get(0).getId().equals(sourceMembershipId)) {
            return CreationResult.of(Outcome.CONFLICTING_MEMBERSHIP);
        }
        if (membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThan(memberId, "ACTIVE", today)) {
            // Includes pre-redesign future-start rows; preserve them and do not create another successor intent.
            return CreationResult.of(Outcome.CONFLICTING_MEMBERSHIP);
        }

        Integer planId = source.getPlan() == null ? null : source.getPlan().getId();
        if (planId == null) {
            return CreationResult.of(Outcome.INELIGIBLE_SOURCE);
        }
        MembershipPlan plan = planRepository.findByIdForUpgradeQuote(planId).orElse(null);
        if (plan == null || !"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
            if (notificationService != null) {
                notificationService.createForUserOnce(memberId, "Renewal unavailable",
                        "Renewal for Membership #" + sourceMembershipId
                                + " is unavailable because its Plan is inactive. Your Membership remains unchanged.");
            }
            return CreationResult.of(Outcome.INACTIVE_PLAN);
        }
        if (plan.getPrice() == null || plan.getDurationMonths() == null || plan.getDurationMonths() <= 0
                || plan.getName() == null || plan.getCourseDiscountPercent() == null
                || plan.getPersonalTrainingDiscountPercent() == null || plan.getAllowPersonalTrainingPlan() == null
                || plan.getAllowOneOnOneCoaching() == null) {
            return CreationResult.of(Outcome.INVALID_PLAN_TERMS);
        }

        MembershipRenewalIntent intent = new MembershipRenewalIntent();
        intent.setSourceMembership(source);
        intent.setTargetPlan(plan);
        intent.setCreationSource(RenewalIntentCreationSource.AUTO_RENEW_SCHEDULER);
        intent.setAutoRenewOptInSnapshot(true);
        intent.setIntentStatus(RenewalIntentStatus.OPEN);
        intent.setCreatedAt(now);
        intent.setValidFrom(validFrom);
        intent.setValidUntil(validUntil);
        intent.setQuoteCapturedAt(now);
        intent.setSourceEndDateSnapshot(source.getEndDate());
        intent.setSuccessorStartDate(source.getEndDate());
        intent.setSuccessorEndDate(source.getEndDate().plusMonths(plan.getDurationMonths()));
        intent.setPlanNameSnapshot(plan.getName());
        intent.setPlanDescriptionSnapshot(plan.getDescription());
        intent.setPlanPriceSnapshot(plan.getPrice());
        intent.setQuotedAmount(plan.getPrice());
        intent.setDurationMonthsSnapshot(plan.getDurationMonths());
        intent.setCourseDiscountPercentSnapshot(plan.getCourseDiscountPercent());
        intent.setPtDiscountPercentSnapshot(plan.getPersonalTrainingDiscountPercent());
        intent.setAllowPtPlanSnapshot(plan.getAllowPersonalTrainingPlan());
        intent.setAllowOneOnOneSnapshot(plan.getAllowOneOnOneCoaching());

        MembershipRenewalIntent saved = intentRepository.saveAndFlush(intent);
        if (notificationService != null) {
            notificationService.createForUserOnce(memberId, "Renewal available",
                    "Renewal Intent #" + saved.getId() + " is available for payment until "
                            + validUntil + " (Vietnam time). Payment is required; no automatic charge was made.");
        }
        return new CreationResult(Outcome.CREATED, saved);
    }

    private boolean activeMember(User member) {
        return member != null && "MEMBER".equalsIgnoreCase(member.getRole())
                && "ACTIVE".equalsIgnoreCase(member.getStatus());
    }

    public enum Outcome {
        CREATED,
        ALREADY_EXISTS,
        SOURCE_NOT_FOUND,
        INELIGIBLE_SOURCE,
        INACTIVE_PLAN,
        OUTSIDE_WINDOW,
        CONFLICTING_PAYMENT,
        CONFLICTING_MEMBERSHIP,
        INVALID_PLAN_TERMS
    }

    public record CreationResult(Outcome outcome, MembershipRenewalIntent intent) {
        static CreationResult of(Outcome outcome) {
            return new CreationResult(outcome, null);
        }
    }
}
