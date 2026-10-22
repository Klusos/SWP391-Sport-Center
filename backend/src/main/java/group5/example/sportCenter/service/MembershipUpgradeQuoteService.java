package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MembershipUpgradeOptionResponse;
import group5.example.sportCenter.dto.MembershipUpgradeQuoteResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.MembershipPlanUpgrade;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradePaymentEvidenceRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MembershipUpgradeQuoteService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final MembershipRepository membershipRepository;
    private final MembershipPlanUpgradeRepository upgradeRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipUpgradePaymentEvidenceRepository upgradeEvidenceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MembershipUpgradeQuoteService(MembershipRepository membershipRepository,
            MembershipPlanUpgradeRepository upgradeRepository, PaymentRepository paymentRepository,
            MembershipUpgradePaymentEvidenceRepository upgradeEvidenceRepository,
            UserRepository userRepository, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.upgradeRepository = upgradeRepository;
        this.paymentRepository = paymentRepository;
        this.upgradeEvidenceRepository = upgradeEvidenceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MembershipUpgradeQuoteResponse getMyUpgradeOptions(Jwt jwt) {
        User actor = currentMember(jwt);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<Membership> current = membershipRepository.findEffectiveMembershipsForUpgrade(actor.getId(), today);
        List<String> blockers = new ArrayList<>();
        if (current.isEmpty()) {
            blockers.add("No ACTIVE Membership is effective on the current business date.");
            return emptyResponse(today, 0, blockers);
        }
        if (current.size() > 1) {
            blockers.add("Multiple effective Memberships overlap; staff must resolve the data-integrity issue.");
            return emptyResponse(today, current.size(), blockers);
        }

        Membership source = current.get(0);
        MembershipPlan sourcePlan = source.getPlan();
        BigDecimal actualPaid = verifiedActualPaidAmount(source, actor.getId(), blockers);
        if (!"ACTIVE".equalsIgnoreCase(sourcePlan.getStatus())) {
            blockers.add("The source MembershipPlan is not ACTIVE.");
        }

        List<Membership> future = membershipRepository.findFutureActiveMembershipsForUpgrade(actor.getId(), today);
        if (!future.isEmpty()) {
            blockers.add("A future ACTIVE Membership is already scheduled; resolve it before requesting an upgrade.");
        }

        boolean paymentConflict = paymentRepository.findByMember_IdAndPurposeAndStatusIn(actor.getId(),
                PaymentPurpose.MEMBERSHIP, List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED,
                        PaymentStatus.CANCELLED))
                .stream().anyMatch(payment -> payment.getStatus() != PaymentStatus.CANCELLED || payment.getPaidAt() != null);
        if (paymentConflict) {
            blockers.add("A pending Membership payment or unresolved payment reconciliation exists.");
        }

        boolean upgradeConflict = paymentRepository.findByMember_IdAndPurposeAndStatusIn(actor.getId(),
                PaymentPurpose.UPGRADE, List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED,
                        PaymentStatus.CANCELLED)).stream().anyMatch(payment -> {
                            if (payment.getStatus() == PaymentStatus.REFUND_REQUIRED
                                    || (payment.getStatus() == PaymentStatus.CANCELLED && payment.getPaidAt() != null)) {
                                return true;
                            }
                            if (payment.getStatus() != PaymentStatus.PENDING) return false;
                            var evidence = upgradeEvidenceRepository.findByPayment_Id(payment.getId()).orElse(null);
                            return evidence == null || evidence.getExpiresAtUtc() == null
                                    || clock.instant().isBefore(evidence.getExpiresAtUtc());
                        });
        if (upgradeConflict) {
            blockers.add("A pending or unresolved Upgrade Payment already exists.");
        }

        Integer durationDays = durationDays(source, blockers);
        int remainingDays = (int) Math.max(0L, Math.min((long) durationDaysOrZero(durationDays),
                ChronoUnit.DAYS.between(today, source.getEndDate())));
        BigDecimal credit = null;
        if (actualPaid != null && durationDays != null && durationDays > 0) {
            credit = actualPaid.multiply(BigDecimal.valueOf(remainingDays))
                    .divide(BigDecimal.valueOf(durationDays), 2, RoundingMode.HALF_UP);
        }

        List<MembershipUpgradeOptionResponse> options = new ArrayList<>();
        List<MembershipPlanUpgrade> paths = upgradeRepository.findByFromPlan_IdOrderByIdAsc(sourcePlan.getId());
        if (paths.isEmpty()) blockers.add("No ADMIN-configured upgrade path exists from the current plan.");
        boolean sourceBlocked = !blockers.isEmpty();
        BigDecimal sourcePrice = sourcePlan.getEffectiveMonthlyPrice();
        for (MembershipPlanUpgrade path : paths) {
            MembershipPlan target = path.getToPlan();
            BigDecimal targetPrice = target.getEffectiveMonthlyPrice();
            String optionBlocker = null;
            BigDecimal amount = null;
            if (sourceBlocked) {
                optionBlocker = String.join(" ", blockers);
            } else if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
                optionBlocker = "The configured target plan is not ACTIVE.";
            } else if (sourcePrice == null || targetPrice == null) {
                optionBlocker = "A safe authoritative monthly price is unavailable for one of the plans.";
            } else if (targetPrice.compareTo(sourcePrice) <= 0) {
                optionBlocker = "The configured target is not more expensive than the source plan.";
            } else {
                amount = targetPrice.setScale(2, RoundingMode.UNNECESSARY).subtract(credit).setScale(2, RoundingMode.HALF_UP);
                if (amount.signum() <= 0) {
                    optionBlocker = "Calculated upgrade amount is not positive; business review is required.";
                    amount = null;
                }
            }
            options.add(new MembershipUpgradeOptionResponse(target.getId(), target.getName(), targetPrice,
                    optionBlocker == null, optionBlocker == null ? credit : null, amount, optionBlocker));
        }

        return new MembershipUpgradeQuoteResponse(today, BUSINESS_ZONE.getId(), "VND",
                "CALENDAR_DAYS_HALF_OPEN", "Money values rounded to 2 decimal places using HALF_UP",
                1, source.getId(), sourcePlan.getId(), sourcePlan.getName(), sourcePrice,
                source.getStartDate(), source.getEndDate(),
                source.getAutoRenew(), durationDays, remainingDays, actualPaid, credit,
                options.stream().anyMatch(MembershipUpgradeOptionResponse::eligible) && blockers.isEmpty(),
                List.copyOf(blockers), List.copyOf(options));
    }

    private BigDecimal verifiedActualPaidAmount(Membership membership, Integer memberId, List<String> blockers) {
        BigDecimal snapshot = membership.getPaidAmount();
        Payment payment = membership.getSourcePayment();
        if (snapshot == null || snapshot.signum() < 0 || payment == null
                || payment.getStatus() != PaymentStatus.SUCCESS
                || payment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || payment.getAmount() == null || payment.getAmount().compareTo(snapshot) != 0
                || payment.getMember() == null || !memberId.equals(payment.getMember().getId())
                || !membership.getPlan().getId().equals(payment.getReferenceId())) {
            blockers.add("Verified actual-paid purchase evidence is missing or inconsistent; credit cannot be calculated.");
            return null;
        }
        return snapshot.setScale(2, RoundingMode.UNNECESSARY);
    }

    private Integer durationDays(Membership membership, List<String> blockers) {
        if (membership.getStartDate() == null || membership.getEndDate() == null) {
            blockers.add("Membership period dates are incomplete; credit cannot be calculated.");
            return null;
        }
        long days = ChronoUnit.DAYS.between(membership.getStartDate(), membership.getEndDate());
        if (days <= 0 || days > Integer.MAX_VALUE) {
            blockers.add("Membership period dates do not define a valid positive duration.");
            return null;
        }
        Integer membershipMonths = membership.getPurchasedMonths();
        Integer paymentMonths = membership.getSourcePayment() == null ? null : membership.getSourcePayment().getPurchasedMonths();
        if (membershipMonths != null && paymentMonths != null && !membershipMonths.equals(paymentMonths)) {
            blockers.add("Membership and payment duration snapshots disagree; credit cannot be calculated.");
            return null;
        }
        Integer months = membershipMonths != null ? membershipMonths : paymentMonths;
        if (months != null && (months <= 0 || !membership.getStartDate().plusMonths(months).equals(membership.getEndDate()))) {
            blockers.add("Recorded purchase duration does not match the Membership dates; credit cannot be calculated.");
            return null;
        }
        return (int) days;
    }

    private User currentMember(Jwt jwt) {
        final Integer userId;
        try {
            userId = Integer.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive users cannot access Membership upgrades");
        }
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only MEMBER can view personal upgrade options");
        }
        return actor;
    }

    private int durationDaysOrZero(Integer durationDays) {
        return durationDays == null ? 0 : durationDays;
    }

    private MembershipUpgradeQuoteResponse emptyResponse(LocalDate today, int currentCount, List<String> blockers) {
        return new MembershipUpgradeQuoteResponse(today, BUSINESS_ZONE.getId(), "VND",
                "CALENDAR_DAYS_HALF_OPEN", "Money values rounded to 2 decimal places using HALF_UP",
                currentCount, null, null, null, null, null, null, null, null, null, null, null,
                false, List.copyOf(blockers), List.of());
    }
}
