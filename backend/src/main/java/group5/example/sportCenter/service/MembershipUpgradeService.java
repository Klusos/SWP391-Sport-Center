package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MembershipUpgradeOptionResponse;
import group5.example.sportCenter.dto.MembershipUpgradeOptionsResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgrade;
import group5.example.sportCenter.entity.MembershipUpgradePath;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipRenewalIntentRepository;
import group5.example.sportCenter.repository.MembershipUpgradePathRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipUpgradeService {
    public static final String UPGRADED = "UPGRADED";
    static final int MIN_REMAINING_DAYS = 3;
    static final Duration ONLINE_QUOTE_VALIDITY = Duration.ofMinutes(15);
    static final Duration ONLINE_CUTOFF_BEFORE_MIDNIGHT = Duration.ofMinutes(5);

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<PaymentPurpose> MEMBERSHIP_PURPOSES =
            List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);

    private final MembershipRepository membershipRepository;
    private final MembershipRenewalIntentRepository renewalIntentRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipUpgradePathRepository pathRepository;
    private final MembershipUpgradeRepository upgradeRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MembershipUpgradeService(
            MembershipRepository membershipRepository,
            MembershipRenewalIntentRepository renewalIntentRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipUpgradePathRepository pathRepository,
            MembershipUpgradeRepository upgradeRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.renewalIntentRepository = renewalIntentRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.pathRepository = pathRepository;
        this.upgradeRepository = upgradeRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Frozen server quote written with the corresponding UPGRADE Payment. */
    public record Quote(
            Membership source,
            MembershipPlan target,
            LocalDate valuationDate,
            int totalDays,
            int unusedDays,
            BigDecimal sourcePaidValueBasis,
            BigDecimal appliedCredit,
            BigDecimal amount,
            LocalDateTime createdAt) {
    }

    private record Eligibility(Membership current, int remainingDays, String blockedReason) {
    }

    private record PlanPair(MembershipPlan source, MembershipPlan target) {
    }

    @Transactional(readOnly = true)
    public MembershipUpgradeOptionsResponse getOptions(Integer memberId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        boolean staff = role.equals("ADMIN") || role.equals("RECEPTIONIST");
        if (!staff && !(role.equals("MEMBER") && actor.getId().equals(memberId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to view upgrade options for this member");
        }
        if (!userRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("User not found with id: " + memberId);
        }

        Eligibility eligibility = checkEligibility(memberId, today());
        Membership current = eligibility.current();
        List<MembershipUpgradeOptionResponse> options = List.of();
        String blockedReason = eligibility.blockedReason();
        if (blockedReason == null) {
            if (!"ACTIVE".equalsIgnoreCase(current.getPlan().getStatus())) {
                blockedReason = "INACTIVE_SOURCE_PLAN";
            } else if (!validTarget(current.getPlan())) {
                blockedReason = "INVALID_SOURCE_PLAN";
            }
        }
        if (blockedReason == null) {
            List<MembershipUpgradePath> paths = pathRepository
                    .findByFromPlanIdAndActiveTrueOrderByToPlanPriceAsc(current.getPlan().getId());
            options = paths.stream()
                    .filter(path -> "ACTIVE".equalsIgnoreCase(path.getToPlan().getStatus()))
                    .filter(path -> validTarget(path.getToPlan()))
                    .map(path -> preview(current, path.getToPlan()))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(MembershipUpgradeOptionResponse::planPrice))
                    .toList();
            if (options.isEmpty() && paths.isEmpty()) blockedReason = "NO_CONFIGURED_PATHS";
            else if (options.isEmpty()) blockedReason = "NO_VALID_UPGRADE_OPTIONS";
        }

        return new MembershipUpgradeOptionsResponse(
                current == null ? null : current.getId(),
                current == null ? null : current.getPlan().getId(),
                current == null ? null : current.getPlan().getName(),
                current == null ? null : current.getStartDate(),
                current == null ? null : current.getEndDate(),
                current == null ? null : eligibility.remainingDays(),
                blockedReason,
                options);
    }

    /** Called inside PaymentService's transaction after it has locked the member row. */
    public Quote quote(User member, Integer targetPlanId) {
        // PaymentService obtains the shared Member row lock before invoking this quote path.
        if (renewalIntentRepository.existsOpenByMemberId(member.getId())) {
            throw conflict("An open Membership Renewal Intent prevents a new Upgrade quote");
        }
        LocalDate valuationDate = today();
        Eligibility eligibility = checkEligibility(member.getId(), valuationDate);
        if (eligibility.blockedReason() != null) {
            throw conflict("Membership cannot be upgraded: " + eligibility.blockedReason());
        }

        Membership source = eligibility.current();
        if (!"ACTIVE".equalsIgnoreCase(source.getPlan().getStatus())) {
            throw conflict("INACTIVE_SOURCE_PLAN: source Membership Plan is not active for a new Upgrade quote");
        }
        if (!validTarget(source.getPlan())) {
            throw conflict("INVALID_SOURCE_PLAN: source Membership Plan has invalid terms for a new Upgrade quote");
        }
        MembershipUpgradePath path = pathRepository
                .findActiveForQuoteUpdate(source.getPlan().getId(), targetPlanId)
                .orElseThrow(() -> conflict("No active Admin-configured Upgrade Path allows this target Plan"));
        PlanPair lockedPlans = lockPlans(source.getPlan().getId(), targetPlanId);
        MembershipPlan lockedSourcePlan = lockedPlans.source();
        MembershipPlan target = lockedPlans.target();
        if (!"ACTIVE".equalsIgnoreCase(lockedSourcePlan.getStatus())) {
            throw conflict("INACTIVE_SOURCE_PLAN: source Membership Plan is not active for a new Upgrade quote");
        }
        if (!validTarget(lockedSourcePlan)) {
            throw conflict("INVALID_SOURCE_PLAN: source Membership Plan has invalid terms for a new Upgrade quote");
        }
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus()) || !validTarget(target)) {
            throw conflict("Target Membership Plan is not available for a new Upgrade quote");
        }
        if (!path.getFromPlan().getId().equals(source.getPlan().getId())
                || !path.getToPlan().getId().equals(target.getId())) {
            throw conflict("Upgrade Path no longer matches the selected source and target Plans");
        }
        return buildQuote(source, target, valuationDate, now());
    }

    /** Persists all quote evidence before an online URL or counter payment is returned. */
    public void record(Payment payment, Quote quote) {
        if (payment.getAmount() == null || payment.getAmount().compareTo(quote.amount()) != 0) {
            throw new IllegalStateException("Payment amount must equal the frozen Upgrade quote");
        }
        LocalDateTime expiresAt = quote.createdAt().toLocalDate().plusDays(1).atStartOfDay();
        if (payment.getMethod() == PaymentMethod.ONLINE) {
            if (Duration.between(quote.createdAt(), expiresAt).compareTo(ONLINE_CUTOFF_BEFORE_MIDNIGHT) < 0) {
                throw conflict("Online upgrades are unavailable in the last minutes before midnight; try again after 00:00");
            }
            LocalDateTime onlineExpiry = quote.createdAt().plus(ONLINE_QUOTE_VALIDITY);
            if (onlineExpiry.isBefore(expiresAt)) expiresAt = onlineExpiry;
        }

        MembershipUpgrade upgrade = new MembershipUpgrade();
        upgrade.setPayment(payment);
        upgrade.setSourceMembership(quote.source());
        upgrade.setTargetPlan(quote.target());
        upgrade.setRemainingDays(quote.unusedDays());
        upgrade.setExpiresAt(expiresAt);
        upgrade.setCreatedAt(quote.createdAt());
        upgrade.setSourcePeriodStartDate(quote.source().getStartDate());
        upgrade.setSourcePeriodEndDate(quote.source().getEndDate());
        upgrade.setCreditValuationDate(quote.valuationDate());
        upgrade.setSourceTotalDays(quote.totalDays());
        upgrade.setSourceUnusedDays(quote.unusedDays());
        upgrade.setSourcePaidValueBasisAmount(quote.sourcePaidValueBasis());
        upgrade.setTargetPlanNameSnapshot(quote.target().getName());
        upgrade.setTargetPlanDescriptionSnapshot(quote.target().getDescription());
        upgrade.setTargetPlanPriceSnapshot(quote.target().getPrice());
        upgrade.setTargetDurationMonthsSnapshot(quote.target().getDurationMonths());
        upgrade.setTargetCourseDiscountPercentSnapshot(quote.target().getCourseDiscountPercent());
        upgrade.setTargetPtDiscountPercentSnapshot(quote.target().getPersonalTrainingDiscountPercent());
        upgrade.setTargetAllowPtPlanSnapshot(quote.target().getAllowPersonalTrainingPlan());
        upgrade.setTargetAllowOneOnOneSnapshot(quote.target().getAllowOneOnOneCoaching());
        upgrade.setAppliedCreditAmount(quote.appliedCredit());
        upgradeRepository.save(upgrade);
    }

    /** Returns a reconciliation reason rather than mutating Membership if an old quote is unsafe. */
    public Optional<String> apply(Payment payment) {
        if (payment.getPurpose() != PaymentPurpose.UPGRADE) {
            return Optional.of("Payment purpose is not UPGRADE");
        }
        Optional<MembershipUpgrade> found = upgradeRepository.findByPaymentId(payment.getId());
        if (found.isEmpty()) return Optional.of("Upgrade details are missing for this payment");
        MembershipUpgrade upgrade = found.get();
        if (!upgrade.hasCompleteFrozenQuote()) {
            return Optional.of("Legacy Upgrade has no complete frozen quote; manual reconciliation is required");
        }
        BigDecimal expectedCredit = upgrade.getSourcePaidValueBasisAmount()
                .multiply(BigDecimal.valueOf(upgrade.getSourceUnusedDays()))
                .divide(BigDecimal.valueOf(upgrade.getSourceTotalDays()), 0, RoundingMode.DOWN)
                .min(upgrade.getTargetPlanPriceSnapshot());
        if (expectedCredit.compareTo(upgrade.getAppliedCreditAmount()) != 0) {
            return Optional.of("Frozen Upgrade credit evidence does not match the approved calculation");
        }
        if (payment.getAmount() == null
                || payment.getAmount().signum() <= 0
                || payment.getAmount().compareTo(upgrade.getTargetPlanPriceSnapshot()
                        .subtract(upgrade.getAppliedCreditAmount())) != 0) {
            return Optional.of("Upgrade Payment does not match its frozen payable amount");
        }

        Integer memberId = payment.getMember().getId();
        userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));

        LocalDateTime now = now();
        if (!now.isBefore(upgrade.getExpiresAt())) {
            return Optional.of("Upgrade quote expired before the payment was accepted");
        }
        LocalDate settlementDate = today();
        Membership source = upgrade.getSourceMembership();
        if (!source.getMember().getId().equals(memberId)
                || !"ACTIVE".equalsIgnoreCase(source.getStatus())
                || source.getStartDate().isAfter(settlementDate)
                || !source.getEndDate().isAfter(settlementDate)
                || !source.getStartDate().equals(upgrade.getSourcePeriodStartDate())
                || !source.getEndDate().equals(upgrade.getSourcePeriodEndDate())) {
            return Optional.of("The source Membership is no longer eligible for this Upgrade");
        }
        long actualTotalDays = ChronoUnit.DAYS.between(upgrade.getSourcePeriodStartDate(), upgrade.getSourcePeriodEndDate());
        long actualUnusedDays = ChronoUnit.DAYS.between(upgrade.getCreditValuationDate(), upgrade.getSourcePeriodEndDate());
        if (actualTotalDays != upgrade.getSourceTotalDays()
                || actualUnusedDays != upgrade.getSourceUnusedDays()
                || !upgrade.getRemainingDays().equals(upgrade.getSourceUnusedDays())
                || !upgrade.getCreditValuationDate().equals(upgrade.getCreatedAt().toLocalDate())) {
            return Optional.of("Frozen Upgrade source-period evidence is inconsistent");
        }
        BigDecimal currentVerifiedBasis = source.verifiedPaidValueBasis().orElse(null);
        if (currentVerifiedBasis == null
                || currentVerifiedBasis.compareTo(upgrade.getSourcePaidValueBasisAmount()) != 0) {
            return Optional.of("Verified source paid-value evidence no longer matches the frozen quote");
        }

        List<Membership> effective = membershipRepository
                .findByMemberIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                        memberId, "ACTIVE", settlementDate, settlementDate);
        if (effective.size() != 1 || !effective.get(0).getId().equals(source.getId())) {
            return Optional.of("Member no longer has exactly the quoted effective source Membership");
        }
        if (membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThan(
                memberId, "ACTIVE", settlementDate)) {
            return Optional.of("A future ACTIVE Membership prevents this Upgrade");
        }
        if (paymentRepository.existsByMember_IdAndPurposeInAndStatus(
                memberId, List.of(PaymentPurpose.MEMBERSHIP), PaymentStatus.PENDING)) {
            return Optional.of("A pending Membership Purchase prevents this Upgrade");
        }
        if (paymentRepository.existsByMember_IdAndPurposeInAndStatusAndIdNot(
                memberId, List.of(PaymentPurpose.UPGRADE), PaymentStatus.PENDING, payment.getId())) {
            return Optional.of("Another pending Upgrade prevents this settlement");
        }

        BigDecimal resultingBasis = upgrade.getAppliedCreditAmount().add(payment.getAmount());
        if (resultingBasis.compareTo(upgrade.getTargetPlanPriceSnapshot()) != 0) {
            return Optional.of("Upgrade credit and payment do not equal the captured target Plan price");
        }

        Membership upgraded = new Membership();
        upgraded.setMember(source.getMember());
        upgraded.setPlan(upgrade.getTargetPlan());
        upgraded.setAutoRenew(source.getAutoRenew());
        upgraded.setStartDate(settlementDate);
        upgraded.setEndDate(settlementDate.plusMonths(upgrade.getTargetDurationMonthsSnapshot()));
        upgraded.setStatus("ACTIVE");
        upgraded.setPurchasePayment(payment);
        upgraded.setBenefitSnapshotStatus("COMPLETE");
        upgraded.setBenefitSnapshotSource("UPGRADE");
        upgraded.setPlanNameSnapshot(upgrade.getTargetPlanNameSnapshot());
        upgraded.setPlanDescriptionSnapshot(upgrade.getTargetPlanDescriptionSnapshot());
        upgraded.setPlanPriceSnapshot(upgrade.getTargetPlanPriceSnapshot());
        upgraded.setPlanDurationMonthsSnapshot(upgrade.getTargetDurationMonthsSnapshot());
        upgraded.setCourseDiscountPercentSnapshot(upgrade.getTargetCourseDiscountPercentSnapshot());
        upgraded.setPtDiscountPercentSnapshot(upgrade.getTargetPtDiscountPercentSnapshot());
        upgraded.setAllowPtPlanSnapshot(upgrade.getTargetAllowPtPlanSnapshot());
        upgraded.setAllowOneOnOneSnapshot(upgrade.getTargetAllowOneOnOneSnapshot());
        upgraded.setPaidValueBasisStatus("VERIFIED");
        upgraded.setPaidValueBasisAmount(resultingBasis);
        upgraded.setPaidValueBasisSource("UPGRADE_CHAIN");

        source.setStatus(UPGRADED);
        membershipRepository.save(source);
        Membership saved = membershipRepository.save(upgraded);
        upgrade.setResultMembership(saved);
        upgradeRepository.save(upgrade);
        return Optional.empty();
    }

    private Eligibility checkEligibility(Integer memberId, LocalDate valuationDate) {
        List<Membership> unexpired = membershipRepository
                .findByMemberIdAndStatusAndEndDateGreaterThan(memberId, "ACTIVE", valuationDate);
        List<Membership> current = unexpired.stream()
                .filter(membership -> !membership.getStartDate().isAfter(valuationDate)).toList();
        if (current.isEmpty()) {
            return new Eligibility(null, 0, unexpired.isEmpty() ? "NO_ACTIVE_MEMBERSHIP" : "FUTURE_MEMBERSHIP");
        }
        if (current.size() != 1) return new Eligibility(null, 0, "MULTIPLE_ACTIVE_MEMBERSHIPS");
        Membership source = current.get(0);
        int remainingDays = Math.toIntExact(ChronoUnit.DAYS.between(valuationDate, source.getEndDate()));
        if (unexpired.size() > current.size()) return new Eligibility(source, remainingDays, "QUEUED_MEMBERSHIP");
        if (paymentRepository.existsByMember_IdAndPurposeInAndStatus(memberId, MEMBERSHIP_PURPOSES, PaymentStatus.PENDING)) {
            return new Eligibility(source, remainingDays, "PENDING_PAYMENT");
        }
        if (remainingDays < MIN_REMAINING_DAYS) return new Eligibility(source, remainingDays, "TOO_FEW_DAYS_LEFT");
        if (source.verifiedPaidValueBasis().isEmpty()) return new Eligibility(source, remainingDays, "UNKNOWN_PAID_VALUE");
        return new Eligibility(source, remainingDays, null);
    }

    private Optional<MembershipUpgradeOptionResponse> preview(Membership source, MembershipPlan target) {
        try {
            Quote quote = buildQuote(source, target, today(), now());
            return Optional.of(toOption(quote));
        } catch (ResponseStatusException exception) {
            if (exception.getReason() != null && exception.getReason().contains("zero payable")) return Optional.empty();
            throw exception;
        }
    }

    private Quote buildQuote(Membership source, MembershipPlan target, LocalDate valuationDate, LocalDateTime createdAt) {
        if (!validTarget(target)) throw conflict("Target Membership Plan is invalid for a new Upgrade quote");
        BigDecimal basis = source.verifiedPaidValueBasis()
                .orElseThrow(() -> conflict("UNKNOWN_PAID_VALUE: source Membership has no verified paid-value evidence"));
        long totalDays = ChronoUnit.DAYS.between(source.getStartDate(), source.getEndDate());
        long unusedDays = ChronoUnit.DAYS.between(valuationDate, source.getEndDate());
        if (totalDays <= 0) throw conflict("Source Membership has an invalid period");
        if (unusedDays < MIN_REMAINING_DAYS || unusedDays > totalDays) {
            throw conflict("Source Membership has fewer than three eligible unused days");
        }
        BigDecimal rawCredit = basis.multiply(BigDecimal.valueOf(unusedDays))
                .divide(BigDecimal.valueOf(totalDays), 0, RoundingMode.DOWN);
        BigDecimal credit = rawCredit.min(target.getPrice()).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal payable = target.getPrice().subtract(credit).setScale(2, RoundingMode.UNNECESSARY);
        if (payable.signum() <= 0) throw conflict("Upgrade quote has zero payable amount");
        return new Quote(source, target, valuationDate, Math.toIntExact(totalDays), Math.toIntExact(unusedDays),
                basis, credit, payable, createdAt);
    }

    private MembershipUpgradeOptionResponse toOption(Quote quote) {
        MembershipPlan target = quote.target();
        return new MembershipUpgradeOptionResponse(
                target.getId(), target.getName(), target.getDescription(), target.getPrice(), target.getDurationMonths(),
                target.getCourseDiscountPercent(), target.getPersonalTrainingDiscountPercent(),
                target.getAllowPersonalTrainingPlan(), target.getAllowOneOnOneCoaching(),
                quote.appliedCredit(), quote.amount());
    }

    private boolean validTarget(MembershipPlan target) {
        return target.getPrice() != null && target.getPrice().signum() > 0
                && target.getDurationMonths() != null && target.getDurationMonths() > 0
                && target.getName() != null && !target.getName().isBlank()
                && target.getCourseDiscountPercent() != null
                && target.getPersonalTrainingDiscountPercent() != null
                && target.getAllowPersonalTrainingPlan() != null
                && target.getAllowOneOnOneCoaching() != null;
    }

    private PlanPair lockPlans(Integer sourcePlanId, Integer targetPlanId) {
        Integer firstId = Math.min(sourcePlanId, targetPlanId);
        Integer secondId = Math.max(sourcePlanId, targetPlanId);
        MembershipPlan first = membershipPlanRepository.findByIdForUpgradeQuote(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership Plan not found with id: " + firstId));
        MembershipPlan second = membershipPlanRepository.findByIdForUpgradeQuote(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership Plan not found with id: " + secondId));
        return sourcePlanId.equals(firstId) ? new PlanPair(first, second) : new PlanPair(second, first);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private User currentActor(Jwt callerJwt) {
        final Integer userId;
        try {
            userId = Integer.valueOf(callerJwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot access memberships");
        }
        return actor;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }
}
