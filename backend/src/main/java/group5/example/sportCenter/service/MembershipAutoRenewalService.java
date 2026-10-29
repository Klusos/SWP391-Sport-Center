package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PendingAutoRenewalLockRow;
import group5.example.sportCenter.repository.UserRepository;

/**
 * Tự gia hạn không lưu thẻ: RENEWAL_NOTICE_DAYS ngày trước khi gói hết hạn, hệ thống tạo một Payment
 * MEMBERSHIP PENDING (tại quầy, cùng gói, autoRenew = true) và báo cho member. Member trả tại quầy hoặc
 * chuyển sang VNPay; thành công thì gói mới nối tiếp qua MembershipService.activateOrRenewFromPayment.
 * Hết ngày cuối mà chưa trả thì yêu cầu bị huỷ và gói hết hạn bình thường.
 */
@Service
public class MembershipAutoRenewalService {

    public static final int RENEWAL_NOTICE_DAYS = 3;
    /** Payment.note của yêu cầu do hệ thống tạo; số phía sau là Membership.id được gia hạn. */
    public static final String NOTE_PREFIX = "AUTO_RENEWAL membership #";
    static final String EXPIRY_CANCELLATION_NOTE = "cancelled: not paid before the membership ended";
    private static final DateTimeFormatter VNPAY_PAYMENT_TIME =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<PaymentPurpose> MEMBERSHIP_PURPOSES = List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);

    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final EntityManager entityManager;

    public MembershipAutoRenewalService(
            MembershipRepository membershipRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock,
            EntityManager entityManager) {
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /** Job hằng ngày: huỷ yêu cầu quá hạn trước, rồi tạo yêu cầu cho các gói sắp hết hạn. */
    @Transactional
    public RunResult runDaily() {
        int cancelled = cancelUnpaidAfterExpiry();
        LocalDate today = today();
        int created = 0;
        for (Membership membership : membershipRepository.findByStatusAndEndDateBetween(
                "ACTIVE", today.plusDays(1), today.plusDays(RENEWAL_NOTICE_DAYS))) {
            if (createIfDue(membership).isPresent()) {
                created++;
            }
        }
        return new RunResult(created, cancelled);
    }

    public record RunResult(int created, int cancelled) {
    }

    public record VerifiedRenewalSettlement(Membership sourceMembership, LocalDateTime paidAt,
            String rejectionReason) {
        public boolean eligible() {
            return rejectionReason == null;
        }
    }

    /**
     * Tạo yêu cầu gia hạn nếu gói đang bật tự gia hạn, sắp hết hạn, là gói cuối trong chuỗi và member
     * chưa có thanh toán gói nào đang chờ. Gọi từ job và khi member vừa bật tự gia hạn.
     */
    @Transactional
    public Optional<Payment> createIfDue(Membership given) {
        // The first read is only to discover the stable member-row lock. It is not authoritative:
        // opt-out may commit while this transaction waits for that lock.
        Membership membership = membershipRepository.findById(given.getId()).orElse(null);
        if (membership == null) {
            return Optional.empty();
        }
        Integer memberId = membership.getMember().getId();
        // Same member-row lock as purchases and upgrades, so a manual purchase cannot race the job.
        User lockedMember = userRepository.findByIdForBookingUpdate(memberId).orElse(null);
        if (lockedMember == null) {
            return Optional.empty();
        }

        // findById can return the already-managed pre-lock entity. refresh forces a database read
        // after the lock is held, so a committed opt-out cannot be hidden by the persistence context.
        try {
            entityManager.refresh(membership);
            entityManager.refresh(lockedMember);
        } catch (EntityNotFoundException exception) {
            return Optional.empty();
        }

        LocalDate today = today();
        if (!Boolean.TRUE.equals(membership.getAutoRenew())
                || !"ACTIVE".equalsIgnoreCase(membership.getStatus())
                || !membership.getEndDate().isAfter(today)
                || membership.getEndDate().isAfter(today.plusDays(RENEWAL_NOTICE_DAYS))
                || !"ACTIVE".equalsIgnoreCase(lockedMember.getStatus())
                || membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(
                        memberId, "ACTIVE", membership.getEndDate())
                || paymentRepository.existsByMember_IdAndPurposeInAndStatus(
                        memberId, MEMBERSHIP_PURPOSES, PaymentStatus.PENDING)
                || paymentRepository.existsSuccessfulAutoRenewalForSource(
                        PaymentStatus.SUCCESS, PaymentPurpose.MEMBERSHIP,
                        NOTE_PREFIX + membership.getId())) {
            return Optional.empty();
        }
        MembershipPlan plan = membership.getPlan();
        if (!"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
            notificationService.createForUser(memberId, "Không thể tự gia hạn gói " + plan.getName(),
                    "Gói " + plan.getName() + " đã ngừng bán nên không thể tự gia hạn. Gói hiện tại hết hạn ngày "
                            + membership.getEndDate().format(VN_DATE) + "; vui lòng chọn gói khác tại trang Gói thành viên.");
            return Optional.empty();
        }

        Payment payment = new Payment();
        payment.setMember(membership.getMember());
        payment.setAmount(plan.getPrice());
        payment.setMethod(PaymentMethod.COUNTER);
        payment.setPurpose(PaymentPurpose.MEMBERSHIP);
        payment.setReferenceId(plan.getId());
        payment.setAutoRenew(true);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionCode("TXN-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment.setNote(NOTE_PREFIX + membership.getId());
        payment = paymentRepository.save(payment);

        notificationService.createForUser(memberId, "Gói " + plan.getName() + " sắp hết hạn",
                "Gói hết hạn ngày " + membership.getEndDate().format(VN_DATE) + ". Yêu cầu tự gia hạn #" + payment.getId()
                        + " đã được tạo. Vui lòng thanh toán tại quầy hoặc qua VNPay trước ngày hết hạn; "
                        + "nếu chưa thanh toán, yêu cầu sẽ tự huỷ.");
        return Optional.of(payment);
    }

    /** Validate a member's request to replace a cancelled auto-renew COUNTER payment with ONLINE. */
    @Transactional(readOnly = true)
    public Integer validateOnlineReplacement(Integer sourcePaymentId, User member, Integer planId) {
        if (sourcePaymentId == null || member == null || member.getId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auto-renew source payment is required");
        }
        Payment sourcePayment = paymentRepository.findById(sourcePaymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Auto-renew source payment is unavailable"));
        Integer sourceMembershipId = sourceMembershipId(sourcePayment);
        if (sourcePayment.getPurpose() != PaymentPurpose.MEMBERSHIP
                || sourcePayment.getMethod() != PaymentMethod.COUNTER
                || sourcePayment.getStatus() != PaymentStatus.CANCELLED
                || !sourcePayment.getMember().getId().equals(member.getId())
                || !sourcePayment.getReferenceId().equals(planId)
                || sourceMembershipId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment is not a cancelled auto-renew request for this plan");
        }

        Membership source = membershipRepository.findById(sourceMembershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Auto-renew source membership is unavailable"));
        LocalDate today = today();
        if (!source.getMember().getId().equals(member.getId())
                || !source.getPlan().getId().equals(planId)
                || !Boolean.TRUE.equals(source.getAutoRenew())
                || !"ACTIVE".equalsIgnoreCase(source.getStatus())
                || !source.getEndDate().isAfter(today)
                || source.getEndDate().isAfter(today.plusDays(RENEWAL_NOTICE_DAYS))
                || membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(
                        member.getId(), "ACTIVE", source.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auto-renew source membership is no longer eligible");
        }
        return sourceMembershipId;
    }

    /**
     * Validate a verified provider/receipt timestamp for a marked renewal. VNPay PayDate is
     * documented as yyyyMMddHHmmss in GMT+7; its signature must already have been verified.
     */
    @Transactional(readOnly = true)
    public VerifiedRenewalSettlement validateVerifiedSettlement(Payment payment, String paymentTime,
            LocalDateTime callbackReceivedAt) {
        Integer sourceId = sourceMembershipId(payment);
        if (sourceId == null || payment.getPurpose() != PaymentPurpose.MEMBERSHIP) {
            return new VerifiedRenewalSettlement(null, null, "Auto-renew source membership is unavailable");
        }

        LocalDateTime verifiedPaidAt;
        try {
            if (paymentTime == null || !paymentTime.matches("[0-9]{14}")) {
                throw new IllegalArgumentException("missing or malformed timestamp");
            }
            verifiedPaidAt = LocalDateTime.parse(paymentTime, VNPAY_PAYMENT_TIME);
        } catch (RuntimeException exception) {
            return new VerifiedRenewalSettlement(null, null, "Verified payment time is missing or invalid");
        }
        return validateVerifiedSettlement(payment, verifiedPaidAt, callbackReceivedAt);
    }

    @Transactional(readOnly = true)
    public VerifiedRenewalSettlement validateVerifiedSettlement(Payment payment, LocalDateTime verifiedPaidAt,
            LocalDateTime callbackReceivedAt) {
        Integer sourceId = sourceMembershipId(payment);
        if (sourceId == null || payment.getPurpose() != PaymentPurpose.MEMBERSHIP || verifiedPaidAt == null) {
            return new VerifiedRenewalSettlement(null, null, "Auto-renew source or verified payment time is unavailable");
        }
        if (verifiedPaidAt.isAfter(callbackReceivedAt)) {
            return new VerifiedRenewalSettlement(null, null,
                    "Verified payment time is later than callback receipt time");
        }

        Membership source = membershipRepository.findById(sourceId).orElse(null);
        if (source == null || !source.getMember().getId().equals(payment.getMember().getId())
                || !source.getPlan().getId().equals(payment.getReferenceId())) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt,
                    "Auto-renew source membership does not match the payment");
        }
        if (!Boolean.TRUE.equals(source.getAutoRenew())) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt,
                    "Auto-renew was disabled before settlement");
        }
        if (!"ACTIVE".equalsIgnoreCase(source.getStatus()) && !"EXPIRED".equalsIgnoreCase(source.getStatus())) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt,
                    "Auto-renew source membership is no longer eligible");
        }
        LocalDateTime exclusiveDeadline = source.getEndDate().atStartOfDay();
        if (!verifiedPaidAt.isBefore(exclusiveDeadline)) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt,
                    "Payment was made at or after the membership renewal deadline");
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED
                && !isExpiryCancelledPayment(payment.getNote(), sourceId)) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt,
                    "Payment was cancelled for a reason that does not allow renewal settlement");
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.CANCELLED) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt, "Payment is no longer pending");
        }
        if (membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(
                payment.getMember().getId(), "ACTIVE", source.getEndDate())) {
            return new VerifiedRenewalSettlement(source, verifiedPaidAt,
                    "A later membership period already exists");
        }
        return new VerifiedRenewalSettlement(source, verifiedPaidAt, null);
    }

    static boolean isExpiryCancelledPayment(String note, Integer sourceMembershipId) {
        return sourceMembershipId != null && note != null
                && note.equals(NOTE_PREFIX + sourceMembershipId + " " + EXPIRY_CANCELLATION_NOTE);
    }

    /** Khi member tắt tự gia hạn: huỷ yêu cầu gia hạn tự động chưa thanh toán của gói đó. */
    @Transactional
    public void cancelPendingFor(Membership membership) {
        for (PendingAutoRenewalLockRow row : pendingAutoRenewals()) {
            if (membership.getMember().getId().equals(row.memberId())
                    && membership.getId().equals(sourceMembershipId(row.note()))) {
                paymentRepository.findByIdForUpdate(row.paymentId()).ifPresent(locked -> {
                    if (locked.getStatus() == PaymentStatus.PENDING
                            && membership.getId().equals(sourceMembershipId(locked))) {
                        locked.setStatus(PaymentStatus.CANCELLED);
                        locked.setNote(NOTE_PREFIX + membership.getId() + " cancelled: auto-renew turned off");
                        paymentRepository.save(locked);
                    }
                });
            }
        }
    }

    private int cancelUnpaidAfterExpiry() {
        LocalDate today = today();
        int cancelled = 0;
        for (PendingAutoRenewalLockRow row : pendingAutoRenewals()) {
            // Keep the same member -> payment lock order used by renewal callbacks and opt-out.
            Integer memberId = row.memberId();
            userRepository.findByIdForBookingUpdate(memberId);
            int[] changed = { 0 };
            paymentRepository.findByIdForUpdate(row.paymentId()).ifPresent(locked -> {
                Integer sourceId = sourceMembershipId(locked);
                Optional<Membership> source = sourceId == null ? Optional.empty()
                        : membershipRepository.findById(sourceId);
                if (locked.getStatus() == PaymentStatus.PENDING
                        && (source.isEmpty() || !source.get().getEndDate().isAfter(today))) {
                    locked.setStatus(PaymentStatus.CANCELLED);
                    locked.setNote(NOTE_PREFIX + sourceId + " " + EXPIRY_CANCELLATION_NOTE);
                    paymentRepository.save(locked);
                    changed[0] = 1;
                }
            });
            cancelled += changed[0];
        }
        return cancelled;
    }

    private List<PendingAutoRenewalLockRow> pendingAutoRenewals() {
        return paymentRepository.findPendingAutoRenewalLockRows(
                PaymentStatus.PENDING, PaymentPurpose.MEMBERSHIP, NOTE_PREFIX);
    }

    static Integer sourceMembershipId(Payment payment) {
        return payment == null ? null : sourceMembershipId(payment.getNote());
    }

    static Integer sourceMembershipId(String note) {
        try {
            if (note == null || !note.startsWith(NOTE_PREFIX)) return null;
            String suffix = note.substring(NOTE_PREFIX.length()).trim();
            String id = suffix.split("\\s", 2)[0];
            return Integer.valueOf(id);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }
}
