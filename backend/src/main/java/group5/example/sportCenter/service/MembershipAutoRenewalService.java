package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
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

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<PaymentPurpose> MEMBERSHIP_PURPOSES = List.of(PaymentPurpose.MEMBERSHIP, PaymentPurpose.UPGRADE);

    private final MembershipRepository membershipRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public MembershipAutoRenewalService(
            MembershipRepository membershipRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.membershipRepository = membershipRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
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

    /**
     * Tạo yêu cầu gia hạn nếu gói đang bật tự gia hạn, sắp hết hạn, là gói cuối trong chuỗi và member
     * chưa có thanh toán gói nào đang chờ. Gọi từ job và khi member vừa bật tự gia hạn.
     */
    @Transactional
    public Optional<Payment> createIfDue(Membership given) {
        // Re-read inside this transaction: callers may pass a detached entity with lazy member/plan proxies.
        Membership membership = membershipRepository.findById(given.getId()).orElse(given);
        LocalDate today = today();
        if (!Boolean.TRUE.equals(membership.getAutoRenew()) || !"ACTIVE".equals(membership.getStatus())
                || !membership.getEndDate().isAfter(today)
                || membership.getEndDate().isAfter(today.plusDays(RENEWAL_NOTICE_DAYS))) {
            return Optional.empty();
        }
        Integer memberId = membership.getMember().getId();
        // Same member-row lock as purchases and upgrades, so a manual purchase cannot race the job.
        userRepository.findByIdForBookingUpdate(memberId);
        if (!"ACTIVE".equalsIgnoreCase(membership.getMember().getStatus())
                || membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(
                        memberId, "ACTIVE", membership.getEndDate())
                || paymentRepository.existsByMember_IdAndPurposeInAndStatus(memberId, MEMBERSHIP_PURPOSES, PaymentStatus.PENDING)) {
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

    /** Khi member tắt tự gia hạn: huỷ yêu cầu gia hạn tự động chưa thanh toán của gói đó. */
    @Transactional
    public void cancelPendingFor(Membership membership) {
        for (Payment payment : pendingAutoRenewals()) {
            if (membership.getId().equals(sourceMembershipId(payment))) {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setNote(NOTE_PREFIX + membership.getId() + " cancelled: auto-renew turned off");
                paymentRepository.save(payment);
            }
        }
    }

    private int cancelUnpaidAfterExpiry() {
        LocalDate today = today();
        int cancelled = 0;
        for (Payment payment : pendingAutoRenewals()) {
            Integer sourceId = sourceMembershipId(payment);
            Optional<Membership> source = sourceId == null ? Optional.empty() : membershipRepository.findById(sourceId);
            if (source.isEmpty() || !source.get().getEndDate().isAfter(today)) {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setNote(NOTE_PREFIX + sourceId + " cancelled: not paid before the membership ended");
                paymentRepository.save(payment);
                cancelled++;
            }
        }
        return cancelled;
    }

    private List<Payment> pendingAutoRenewals() {
        return paymentRepository.findByStatusAndPurposeAndNoteStartingWith(
                PaymentStatus.PENDING, PaymentPurpose.MEMBERSHIP, NOTE_PREFIX);
    }

    static Integer sourceMembershipId(Payment payment) {
        try {
            return Integer.valueOf(payment.getNote().substring(NOTE_PREFIX.length()).trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }
}
