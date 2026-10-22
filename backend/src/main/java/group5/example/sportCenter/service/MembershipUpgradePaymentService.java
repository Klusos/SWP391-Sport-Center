package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateMembershipUpgradePaymentRequest;
import group5.example.sportCenter.dto.MembershipUpgradeOptionResponse;
import group5.example.sportCenter.dto.MembershipUpgradePaymentResponse;
import group5.example.sportCenter.dto.MembershipUpgradeQuoteResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipUpgradePaymentEvidence;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradePaymentEvidenceRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipUpgradePaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration MAX_HOLD = Duration.ofMinutes(15);
    private static final Duration MINIMUM_WINDOW = Duration.ofMinutes(2);

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final MembershipPlanUpgradeRepository upgradePathRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipUpgradePaymentEvidenceRepository evidenceRepository;
    private final MembershipUpgradeQuoteService quoteService;
    private final Clock clock;

    public MembershipUpgradePaymentService(UserRepository userRepository,
            MembershipRepository membershipRepository, MembershipPlanRepository planRepository,
            MembershipPlanUpgradeRepository upgradePathRepository, PaymentRepository paymentRepository,
            MembershipUpgradePaymentEvidenceRepository evidenceRepository,
            MembershipUpgradeQuoteService quoteService, Clock clock) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.planRepository = planRepository;
        this.upgradePathRepository = upgradePathRepository;
        this.paymentRepository = paymentRepository;
        this.evidenceRepository = evidenceRepository;
        this.quoteService = quoteService;
        this.clock = clock;
    }

    /** Creates only a pending, non-payable Upgrade Payment draft; no gateway is called. */
    @Transactional
    public MembershipUpgradePaymentResponse create(Jwt jwt, CreateMembershipUpgradePaymentRequest request) {
        Integer actorId;
        try {
            actorId = Integer.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User lockedMember = userRepository.findByIdForBookingUpdate(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"MEMBER".equalsIgnoreCase(lockedMember.getRole()) || !"ACTIVE".equalsIgnoreCase(lockedMember.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an active MEMBER can request an Upgrade Payment");
        }
        if (request.paymentMethod() != PaymentMethod.ONLINE && request.paymentMethod() != PaymentMethod.COUNTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method must be ONLINE or COUNTER");
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate businessDate = LocalDate.ofInstant(now, BUSINESS_ZONE);
        Instant nextMidnight = businessDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant expiresAt = now.plus(MAX_HOLD).isBefore(nextMidnight) ? now.plus(MAX_HOLD) : nextMidnight;
        if (Duration.between(now, expiresAt).compareTo(MINIMUM_WINDOW) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upgrade Payment window is under two minutes; request a new quote after the Vietnam business date changes");
        }

        // All Membership/payment creation workflows serialize on this member row first.
        MembershipUpgradeQuoteResponse initialQuote = quoteService.getMyUpgradeOptions(jwt);
        if (initialQuote.currentEffectiveMembershipCount() != 1 || initialQuote.sourceMembershipId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    initialQuote.blockers().isEmpty() ? "Exactly one current Membership is required" : String.join(" ", initialQuote.blockers()));
        }
        Membership source = membershipRepository.findByIdForUpgradeUpdate(initialQuote.sourceMembershipId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Current Membership changed; refresh upgrade options"));
        int firstPlanId = Math.min(source.getPlan().getId(), request.targetPlanId());
        int secondPlanId = Math.max(source.getPlan().getId(), request.targetPlanId());
        MembershipPlan firstPlan = planRepository.findByIdForUpgradeUpdate(firstPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found: " + firstPlanId));
        MembershipPlan secondPlan = firstPlanId == secondPlanId ? firstPlan
                : planRepository.findByIdForUpgradeUpdate(secondPlanId)
                        .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found: " + secondPlanId));
        MembershipPlan sourcePlan = source.getPlan().getId().equals(firstPlanId) ? firstPlan : secondPlan;
        MembershipPlan target = request.targetPlanId().equals(firstPlanId) ? firstPlan : secondPlan;

        // Recompute only after acquiring the row locks so the persisted evidence reflects
        // the source period, paid snapshot, and plan prices protected for this transaction.
        MembershipUpgradeQuoteResponse quote = quoteService.getMyUpgradeOptions(jwt);
        if (quote.currentEffectiveMembershipCount() != 1
                || !source.getId().equals(quote.sourceMembershipId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Current Membership changed; refresh upgrade options");
        }

        if (!upgradePathRepository.existsByFromPlan_IdAndToPlan_Id(sourcePlan.getId(), target.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ADMIN has not configured this directional upgrade path");
        }
        if (!"ACTIVE".equalsIgnoreCase(sourcePlan.getStatus()) || !"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source and target plans must both be ACTIVE");
        }
        BigDecimal sourcePrice = sourcePlan.getEffectiveMonthlyPrice();
        BigDecimal targetPrice = target.getEffectiveMonthlyPrice();
        if (sourcePrice == null || targetPrice == null || targetPrice.compareTo(sourcePrice) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upgrade target must have a valid monthly price strictly above the source plan");
        }

        MembershipUpgradeOptionResponse option = quote.upgradeOptions().stream()
                .filter(candidate -> candidate.targetPlanId().equals(target.getId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No eligible quote exists for this configured upgrade path"));
        if (!quote.eligible() || !option.eligible() || option.upgradeAmount() == null
                || option.upgradeAmount().signum() <= 0 || quote.actualPaidAmount() == null
                || quote.remainingCredit() == null || !businessDate.equals(quote.businessDate())) {
            String reason = option.blocker() != null ? option.blocker()
                    : String.join(" ", quote.blockers());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    reason.isBlank() ? "Upgrade is no longer eligible; refresh the quote" : reason);
        }

        // A timed-out draft never had a gateway URL or provider evidence. Preserve its row,
        // but close it before creating a replacement. An unresolved/captured draft blocks.
        List<Payment> prior = paymentRepository.findByMember_IdAndPurposeAndStatusIn(actorId,
                PaymentPurpose.UPGRADE, List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED));
        for (Payment old : prior) {
            MembershipUpgradePaymentEvidence oldEvidence = evidenceRepository.findByPayment_Id(old.getId()).orElse(null);
            if (old.getStatus() == PaymentStatus.PENDING && oldEvidence != null
                    && !now.isBefore(oldEvidence.getExpiresAtUtc()) && old.getPaidAt() == null) {
                old.setStatus(PaymentStatus.CANCELLED);
                old.setNote("Unpaid Upgrade draft expired before checkout was enabled");
                paymentRepository.save(old);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An unresolved Upgrade Payment already exists; staff review or expiry is required");
            }
        }

        // Quotes use the established two-decimal HALF_UP convention. VNPay encodes amount
        // as amount * 100 integer minor units; this validates representability without changing it.
        BigDecimal amount = option.upgradeAmount().setScale(2, RoundingMode.UNNECESSARY);
        try {
            amount.movePointRight(2).toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upgrade amount cannot be represented in VND minor units", exception);
        }

        Payment payment = new Payment();
        payment.setMember(lockedMember);
        payment.setAmount(amount);
        payment.setMethod(request.paymentMethod());
        payment.setPurpose(PaymentPurpose.UPGRADE);
        payment.setReferenceId(target.getId());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("UPG-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment.setNote("Upgrade Payment draft; awaiting member checkout and verified settlement");
        payment = paymentRepository.saveAndFlush(payment);

        MembershipUpgradePaymentEvidence evidence = new MembershipUpgradePaymentEvidence();
        evidence.setPayment(payment);
        evidence.setSourceMembership(source);
        evidence.setTargetPlan(target);
        evidence.setSourceStartDate(source.getStartDate());
        evidence.setSourceEndDate(source.getEndDate());
        evidence.setSourcePurchasedMonths(source.getPurchasedMonths());
        evidence.setOriginalDurationDays(quote.originalDurationDays());
        evidence.setRemainingDays(quote.remainingDays());
        evidence.setSourcePaidAmount(quote.actualPaidAmount().setScale(2, RoundingMode.UNNECESSARY));
        evidence.setTargetMonthlyPrice(targetPrice.setScale(2, RoundingMode.UNNECESSARY));
        evidence.setRemainingCredit(option.remainingCredit().setScale(2, RoundingMode.UNNECESSARY));
        evidence.setPayableAmount(amount);
        evidence.setIssuanceBusinessDate(businessDate);
        evidence.setCreatedAtUtc(now);
        evidence.setExpiresAtUtc(expiresAt);
        evidenceRepository.save(evidence);
        return new MembershipUpgradePaymentResponse(payment.getId(), payment.getPurpose().name(),
                payment.getStatus().name(), payment.getMethod().name(), source.getId(), target.getId(), amount,
                evidence.getSourcePaidAmount(), evidence.getTargetMonthlyPrice(), evidence.getRemainingCredit(),
                businessDate, expiresAt, false, false);
    }
}
