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
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;

/**
 * Nâng cấp gói giữa kỳ: gói mới có hiệu lực ngay, giữ nguyên ngày hết hạn của gói hiện tại.
 * Member trả phần chênh lệch giá theo ngày cho số ngày còn lại (tháng tính 30 ngày), làm tròn lên 1.000đ.
 * Không hạ cấp giữa kỳ và không hoàn tiền phần chênh lệch.
 */
@Service
public class MembershipUpgradeService {

    public static final String UPGRADED = "UPGRADED";
    static final int MIN_REMAINING_DAYS = 3;
    static final Duration ONLINE_QUOTE_VALIDITY = Duration.ofMinutes(15);
    // An online payment started this close to midnight could not finish before the quote ends.
    static final Duration ONLINE_CUTOFF_BEFORE_MIDNIGHT = Duration.ofMinutes(5);

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final BigDecimal ROUNDING_STEP = BigDecimal.valueOf(1000);
    private static final List<PaymentPurpose> MEMBERSHIP_PURPOSES = List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository membershipPlanRepository;
    private final MembershipUpgradeRepository upgradeRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public MembershipUpgradeService(
            MembershipRepository membershipRepository,
            MembershipPlanRepository membershipPlanRepository,
            MembershipUpgradeRepository upgradeRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.membershipPlanRepository = membershipPlanRepository;
        this.upgradeRepository = upgradeRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Số tiền đã chốt cho một lần nâng cấp; được lưu thành MembershipUpgrade khi tạo Payment. */
    public record Quote(Membership source, MembershipPlan target, int remainingDays, BigDecimal amount) {
    }

    private record Eligibility(Membership current, int remainingDays, String blockedReason) {
    }

    @Transactional(readOnly = true)
    public MembershipUpgradeOptionsResponse getOptions(Integer memberId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        boolean staff = role.equals("ADMIN") || role.equals("RECEPTIONIST");
        if (!staff && !(role.equals("MEMBER") && actor.getId().equals(memberId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to view upgrade options for this member");
        }
        if (!userRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("User not found with id: " + memberId);
        }

        Eligibility eligibility = checkEligibility(memberId, today());
        Membership current = eligibility.current();
        List<MembershipUpgradeOptionResponse> options = eligibility.blockedReason() != null ? List.of()
                : membershipPlanRepository.findByStatus("ACTIVE").stream()
                        .filter(plan -> isUpgrade(current.getPlan(), plan))
                        .sorted(Comparator.comparing(MembershipPlan::getPrice))
                        .map(plan -> new MembershipUpgradeOptionResponse(plan.getId(), plan.getName(), plan.getPrice(),
                                plan.getDurationMonths(), plan.getCourseDiscountPercent(),
                                plan.getPersonalTrainingDiscountPercent(),
                                proratedAmount(current.getPlan(), plan, eligibility.remainingDays())))
                        .toList();
        return new MembershipUpgradeOptionsResponse(
                current == null ? null : current.getId(),
                current == null ? null : current.getPlan().getId(),
                current == null ? null : current.getPlan().getName(),
                current == null ? null : current.getStartDate(),
                current == null ? null : current.getEndDate(),
                current == null ? null : eligibility.remainingDays(),
                eligibility.blockedReason(),
                options);
    }

    /** Gọi khi tạo Payment UPGRADE; caller đã khoá dòng user của member. */
    public Quote quote(User member, Integer targetPlanId) {
        Eligibility eligibility = checkEligibility(member.getId(), today());
        if (eligibility.blockedReason() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership cannot be upgraded: " + eligibility.blockedReason());
        }
        MembershipPlan target = membershipPlanRepository.findById(targetPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership plan not found with id: " + targetPlanId));
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw new IllegalArgumentException("Membership plan is not active");
        }
        Membership current = eligibility.current();
        if (!isUpgrade(current.getPlan(), target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Target plan must cost more per day and keep every benefit of the current plan");
        }
        return new Quote(current, target, eligibility.remainingDays(),
                proratedAmount(current.getPlan(), target, eligibility.remainingDays()));
    }

    /**
     * Lưu báo giá cho Payment vừa tạo. Báo giá hết hạn lúc nửa đêm vì số ngày còn lại đổi theo ngày;
     * thanh toán online còn bị giới hạn 15 phút như liên kết VNPay.
     */
    public void record(Payment payment, Quote quote) {
        LocalDateTime now = now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime expiresAt = midnight;
        if (payment.getMethod() == PaymentMethod.ONLINE) {
            if (Duration.between(now, midnight).compareTo(ONLINE_CUTOFF_BEFORE_MIDNIGHT) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Online upgrades are unavailable in the last minutes before midnight; try again after 00:00");
            }
            LocalDateTime onlineLimit = now.plus(ONLINE_QUOTE_VALIDITY);
            expiresAt = onlineLimit.isBefore(midnight) ? onlineLimit : midnight;
        }
        MembershipUpgrade upgrade = new MembershipUpgrade();
        upgrade.setPayment(payment);
        upgrade.setSourceMembership(quote.source());
        upgrade.setTargetPlan(quote.target());
        upgrade.setRemainingDays(quote.remainingDays());
        upgrade.setExpiresAt(expiresAt);
        upgrade.setCreatedAt(now);
        upgradeRepository.save(upgrade);
    }

    /** Lễ tân chỉ xác nhận đã thu tiền khi báo giá còn hạn; quá hạn thì huỷ và tạo yêu cầu mới. */
    public void requireOpenQuote(Payment payment) {
        MembershipUpgrade upgrade = upgradeRepository.findByPaymentId(payment.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade details are missing for this payment"));
        if (now().isAfter(upgrade.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This upgrade quote has expired; cancel the payment and create a new upgrade request");
        }
    }

    /**
     * Áp dụng nâng cấp khi Payment thành công (trong transaction của PaymentService).
     * Trả về lý do hoàn tiền nếu không áp dụng được: báo giá hết hạn hoặc gói nguồn không còn hiệu lực.
     */
    public Optional<String> apply(Payment payment) {
        Optional<MembershipUpgrade> found = upgradeRepository.findByPaymentId(payment.getId());
        if (found.isEmpty()) {
            return Optional.of("Upgrade details are missing for this payment");
        }
        MembershipUpgrade upgrade = found.get();
        // Same member-row lock as purchases, so an upgrade and a renewal cannot interleave.
        Integer memberId = payment.getMember().getId();
        userRepository.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + memberId));

        LocalDateTime now = now();
        if (now.isAfter(upgrade.getExpiresAt())) {
            return Optional.of("Upgrade quote expired before the payment completed");
        }
        Membership source = upgrade.getSourceMembership();
        LocalDate today = now.toLocalDate();
        if (!"ACTIVE".equals(source.getStatus()) || source.getStartDate().isAfter(today) || !source.getEndDate().isAfter(today)) {
            return Optional.of("The membership being upgraded is no longer active");
        }

        source.setStatus(UPGRADED);
        membershipRepository.save(source);

        Membership upgraded = new Membership();
        upgraded.setMember(source.getMember());
        upgraded.setPlan(upgrade.getTargetPlan());
        upgraded.setAutoRenew(source.getAutoRenew());
        upgraded.setStartDate(today);
        upgraded.setEndDate(source.getEndDate());
        upgraded.setStatus("ACTIVE");
        upgraded.setPurchasePayment(payment);
        upgrade.setResultMembership(membershipRepository.save(upgraded));
        upgradeRepository.save(upgrade);
        return Optional.empty();
    }

    // ---------------------------------------------------------------- rules

    private Eligibility checkEligibility(Integer memberId, LocalDate today) {
        List<Membership> unexpired = membershipRepository.findByMemberIdAndStatusAndEndDateGreaterThan(memberId, "ACTIVE", today);
        List<Membership> current = unexpired.stream().filter(m -> !m.getStartDate().isAfter(today)).toList();
        if (current.isEmpty()) {
            return new Eligibility(null, 0, "NO_ACTIVE_MEMBERSHIP");
        }
        if (current.size() > 1) {
            return new Eligibility(null, 0, "MULTIPLE_ACTIVE_MEMBERSHIPS");
        }
        Membership membership = current.get(0);
        int remainingDays = (int) ChronoUnit.DAYS.between(today, membership.getEndDate());
        if (unexpired.size() > current.size()) {
            return new Eligibility(membership, remainingDays, "QUEUED_MEMBERSHIP");
        }
        if (paymentRepository.existsByMember_IdAndPurposeInAndStatus(memberId, MEMBERSHIP_PURPOSES, PaymentStatus.PENDING)) {
            return new Eligibility(membership, remainingDays, "PENDING_PAYMENT");
        }
        if (remainingDays < MIN_REMAINING_DAYS) {
            return new Eligibility(membership, remainingDays, "TOO_FEW_DAYS_LEFT");
        }
        return new Eligibility(membership, remainingDays, null);
    }

    /** Gói đích phải đắt hơn theo ngày và không mất quyền lợi nào của gói hiện tại. */
    static boolean isUpgrade(MembershipPlan from, MembershipPlan to) {
        if (from.getId().equals(to.getId())) {
            return false;
        }
        BigDecimal toPerMonthOfFrom = to.getPrice().multiply(BigDecimal.valueOf(from.getDurationMonths()));
        BigDecimal fromPerMonthOfTo = from.getPrice().multiply(BigDecimal.valueOf(to.getDurationMonths()));
        return toPerMonthOfFrom.compareTo(fromPerMonthOfTo) > 0
                && to.getCourseDiscountPercent().compareTo(from.getCourseDiscountPercent()) >= 0
                && to.getPersonalTrainingDiscountPercent().compareTo(from.getPersonalTrainingDiscountPercent()) >= 0
                && (!from.getAllowPersonalTrainingPlan() || to.getAllowPersonalTrainingPlan())
                && (!from.getAllowOneOnOneCoaching() || to.getAllowOneOnOneCoaching());
    }

    /** (giá/ngày gói mới − giá/ngày gói cũ) × số ngày còn lại, làm tròn lên 1.000đ. */
    static BigDecimal proratedAmount(MembershipPlan from, MembershipPlan to, int remainingDays) {
        BigDecimal fromMonths = BigDecimal.valueOf(from.getDurationMonths());
        BigDecimal toMonths = BigDecimal.valueOf(to.getDurationMonths());
        BigDecimal numerator = to.getPrice().multiply(fromMonths)
                .subtract(from.getPrice().multiply(toMonths))
                .multiply(BigDecimal.valueOf(remainingDays));
        BigDecimal denominator = DAYS_PER_MONTH.multiply(fromMonths).multiply(toMonths).multiply(ROUNDING_STEP);
        return numerator.divide(denominator, 0, RoundingMode.CEILING).multiply(ROUNDING_STEP).setScale(2);
    }

    // ---------------------------------------------------------------- helpers

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
