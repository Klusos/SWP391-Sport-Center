package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MembershipCancellationRequestResponse;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipCancellationRequest;
import group5.example.sportCenter.entity.MembershipCancellationRequestStatus;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MembershipCancellationRequestRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.UserRepository;

/**
 * Member gửi yêu cầu huỷ gói chưa bắt đầu (chậm nhất 72 giờ trước 00:00 ngày bắt đầu, giờ Việt Nam);
 * nhân viên duyệt (gói CANCELLED, khoản mua gói REFUND_REQUIRED) hoặc từ chối kèm lý do.
 * Member có thể rút yêu cầu khi còn PENDING; job hằng ngày đóng yêu cầu của gói đã bắt đầu (EXPIRED).
 */
@Service
public class MembershipCancellationRequestService {

    public static final int NOTICE_HOURS = 72;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter VN_DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final MembershipCancellationRequestRepository requestRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final NotificationService notificationService;
    private final Clock clock;

    public MembershipCancellationRequestService(
            MembershipCancellationRequestRepository requestRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            MembershipService membershipService,
            NotificationService notificationService,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.membershipService = membershipService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** Hạn cuối gửi yêu cầu: 72 giờ trước 00:00 ngày gói bắt đầu. */
    public static LocalDateTime requestDeadline(Membership membership) {
        return membership.getStartDate().atStartOfDay().minusHours(NOTICE_HOURS);
    }

    @Transactional
    public MembershipCancellationRequestResponse create(Integer membershipId, String reason, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        if (!"MEMBER".equalsIgnoreCase(actor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only members send cancellation requests; staff cancel directly");
        }
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found with id: " + membershipId));
        if (!membership.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only request cancellation of your own membership");
        }
        // Same member-row lock as purchases and cancellations, so the chain cannot change underneath.
        userRepository.findByIdForBookingUpdate(actor.getId());

        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();
        if (!"ACTIVE".equals(membership.getStatus()) || !membership.getEndDate().isAfter(today)) {
            throw new IllegalArgumentException("Only an active upcoming membership can be cancelled");
        }
        if (!membership.getStartDate().isAfter(today)) {
            throw new IllegalArgumentException("A running membership cannot be cancelled; turn off auto-renew instead");
        }
        if (membershipRepository.existsByMemberIdAndStatusAndStartDateGreaterThanEqual(
                actor.getId(), "ACTIVE", membership.getEndDate())) {
            throw new IllegalArgumentException("Request cancellation of the membership that starts after this one first");
        }
        LocalDateTime deadline = requestDeadline(membership);
        if (!now.isBefore(deadline)) {
            throw new IllegalArgumentException("Cancellation requests close " + NOTICE_HOURS
                    + " hours before the membership starts (" + deadline.format(VN_DATE_TIME) + ")");
        }
        if (requestRepository.existsByMembershipIdAndStatus(membershipId, MembershipCancellationRequestStatus.PENDING)) {
            throw new IllegalArgumentException("This membership already has a pending cancellation request");
        }

        MembershipCancellationRequest request = new MembershipCancellationRequest();
        request.setMembership(membership);
        request.setMember(actor);
        request.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        request.setStatus(MembershipCancellationRequestStatus.PENDING);
        request.setRequestedAt(now);
        MembershipCancellationRequest saved = requestRepository.save(request);

        String title = "Yêu cầu huỷ gói #" + membership.getId();
        String message = actor.getName() + " yêu cầu huỷ gói " + membership.getPlan().getName() + " ("
                + membership.getStartDate().format(VN_DATE) + " → " + membership.getEndDate().format(VN_DATE) + ")"
                + (saved.getReason() != null ? ". Lý do: " + saved.getReason() : "") + ".";
        Stream.concat(userRepository.findByRole("ADMIN").stream(), userRepository.findByRole("RECEPTIONIST").stream())
                .filter(staff -> "ACTIVE".equalsIgnoreCase(staff.getStatus()))
                .forEach(staff -> notificationService.createForUser(staff.getId(), title, message));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MembershipCancellationRequestResponse> getMine(Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        return requestRepository.findByMemberIdOrderByRequestedAtDesc(actor.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipCancellationRequestResponse> getByStatus(MembershipCancellationRequestStatus status, Jwt callerJwt) {
        requireStaff(currentActor(callerJwt));
        return requestRepository.findByStatusOrderByRequestedAtAsc(status).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MembershipCancellationRequestResponse withdraw(Integer requestId, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        MembershipCancellationRequest request = lockPending(requestId);
        if (!request.getMember().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only withdraw your own cancellation request");
        }
        close(request, MembershipCancellationRequestStatus.WITHDRAWN, actor, null);
        return toResponse(requestRepository.save(request));
    }

    @Transactional
    public MembershipCancellationRequestResponse approve(Integer requestId, String note, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);
        MembershipCancellationRequest request = lockPending(requestId);
        String trimmedNote = trim(note);
        // Notifies the member and marks the purchase payment REFUND_REQUIRED.
        membershipService.cancelForApprovedRequest(request.getMembership(), request.getReason() != null
                ? "Yêu cầu huỷ #" + request.getId() + ": " + request.getReason()
                : "Yêu cầu huỷ #" + request.getId() + " của hội viên đã được duyệt");
        close(request, MembershipCancellationRequestStatus.APPROVED, actor, trimmedNote);
        return toResponse(requestRepository.save(request));
    }

    @Transactional
    public MembershipCancellationRequestResponse reject(Integer requestId, String note, Jwt callerJwt) {
        User actor = currentActor(callerJwt);
        requireStaff(actor);
        String trimmedNote = trim(note);
        if (trimmedNote == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note is required to reject a cancellation request");
        }
        MembershipCancellationRequest request = lockPending(requestId);
        close(request, MembershipCancellationRequestStatus.REJECTED, actor, trimmedNote);
        Membership membership = request.getMembership();
        notificationService.createForUser(request.getMember().getId(), "Yêu cầu huỷ gói bị từ chối",
                "Yêu cầu huỷ gói " + membership.getPlan().getName() + " (" + membership.getStartDate().format(VN_DATE)
                        + " → " + membership.getEndDate().format(VN_DATE) + ") không được chấp nhận. Lý do: " + trimmedNote);
        return toResponse(requestRepository.save(request));
    }

    /** Job hằng ngày: yêu cầu PENDING của gói đã bắt đầu (hoặc đã bị huỷ/nâng cấp) không duyệt được nữa. */
    @Transactional
    public int expireUnapprovable() {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        List<MembershipCancellationRequest> stale = requestRepository.findPendingNoLongerApprovable(today);
        for (MembershipCancellationRequest request : stale) {
            close(request, MembershipCancellationRequestStatus.EXPIRED, null,
                    "Gói đã bắt đầu hoặc đã thay đổi trước khi yêu cầu được xử lý");
            requestRepository.save(request);
            notificationService.createForUser(request.getMember().getId(), "Yêu cầu huỷ gói đã hết hiệu lực",
                    "Yêu cầu huỷ gói " + request.getMembership().getPlan().getName()
                            + " chưa được xử lý trước ngày bắt đầu nên đã hết hiệu lực. Vui lòng liên hệ lễ tân nếu cần hỗ trợ.");
        }
        return stale.size();
    }

    private MembershipCancellationRequest lockPending(Integer requestId) {
        MembershipCancellationRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Cancellation request not found with id: " + requestId));
        if (request.getStatus() != MembershipCancellationRequestStatus.PENDING) {
            throw new IllegalArgumentException("Cancellation request was already processed: " + request.getStatus());
        }
        return request;
    }

    private void close(MembershipCancellationRequest request, MembershipCancellationRequestStatus status, User actor, String note) {
        request.setStatus(status);
        request.setDecidedBy(actor);
        request.setDecidedAt(now());
        request.setDecisionNote(note);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }

    private void requireStaff(User actor) {
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        if (!role.equals("ADMIN") && !role.equals("RECEPTIONIST")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN or RECEPTIONIST can review cancellation requests");
        }
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

    private MembershipCancellationRequestResponse toResponse(MembershipCancellationRequest request) {
        Membership membership = request.getMembership();
        Payment purchase = membership.getPurchasePayment();
        return new MembershipCancellationRequestResponse(
                request.getId(),
                membership.getId(),
                request.getMember().getId(),
                request.getMember().getName(),
                membership.getPlan().getName(),
                membership.getStartDate(),
                membership.getEndDate(),
                request.getReason(),
                request.getStatus().name(),
                request.getRequestedAt(),
                request.getDecidedBy() == null ? null : request.getDecidedBy().getName(),
                request.getDecidedAt(),
                request.getDecisionNote(),
                purchase == null ? null : purchase.getId(),
                purchase == null ? null : purchase.getAmount());
    }
}
