package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreatePersonalTrainingBookingRequest;
import group5.example.sportCenter.dto.EligiblePtCoachResponse;
import group5.example.sportCenter.dto.PersonalTrainingBookingResponse;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.PersonalTrainingBooking;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.exception.PtPricingConfigurationException;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class PersonalTrainingBookingService {
    public static final int SESSION_MINUTES = 90;
    public static final int PAYMENT_HOLD_MINUTES = 15;
    public static final int CANCEL_NOTICE_HOURS = 24;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PersonalTrainingBookingRepository bookings;
    private final UserRepository users;
    private final CoachRepository coaches;
    private final CoachSportRepository coachSports;
    private final SportRepository sports;
    private final MembershipRepository memberships;
    private final ClassSessionRepository classSessions;
    private final PaymentRepository payments;
    private final NotificationService notifications;
    private final Clock clock;
    private final String configuredBasePrice;

    public PersonalTrainingBookingService(
            PersonalTrainingBookingRepository bookings,
            UserRepository users,
            CoachRepository coaches,
            CoachSportRepository coachSports,
            SportRepository sports,
            MembershipRepository memberships,
            ClassSessionRepository classSessions,
            PaymentRepository payments,
            NotificationService notifications,
            Clock clock,
            @Value("${app.pt.session-base-price:}") String configuredBasePrice) {
        this.bookings = bookings;
        this.users = users;
        this.coaches = coaches;
        this.coachSports = coachSports;
        this.sports = sports;
        this.memberships = memberships;
        this.classSessions = classSessions;
        this.payments = payments;
        this.notifications = notifications;
        this.clock = clock;
        this.configuredBasePrice = configuredBasePrice;
    }

    @Transactional(readOnly = true)
    public List<EligiblePtCoachResponse> eligibleCoaches(Jwt jwt, Integer sportId, LocalDateTime startTime) {
        Integer memberId = currentUserId(jwt);
        User member = requireMember(memberId);
        validateStartTime(startTime);
        Membership membership = eligibleMembership(memberId, startTime.toLocalDate());
        Price price = price(membership);
        Sport sport = activeSport(sportId);

        return coachSports.findBySportId(sportId).stream()
                .map(assignment -> assignment.getCoach())
                .filter(coach -> "ACTIVE".equalsIgnoreCase(coach.getStatus())
                        && "ACTIVE".equalsIgnoreCase(coach.getUser().getStatus())
                        && "COACH".equalsIgnoreCase(coach.getUser().getRole()))
                .filter(coach -> !hasScheduleConflict(coach.getId(), member.getId(), startTime,
                        startTime.plusMinutes(SESSION_MINUTES)))
                .distinct()
                .map(coach -> new EligiblePtCoachResponse(coach.getId(), coach.getUser().getName(),
                        sport.getId(), sport.getName(), price.original(), price.discount(), price.finalPrice(),
                        SESSION_MINUTES))
                .toList();
    }

    @Transactional
    public PersonalTrainingBookingResponse create(Jwt jwt, CreatePersonalTrainingBookingRequest request) {
        Integer memberId = currentUserId(jwt);
        // A stable row lock serializes all bookings for this member, including when no booking exists yet.
        User member = users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        requireActiveMember(member);
        validateStartTime(request.getStartTime());
        LocalDateTime endTime = request.getStartTime().plusMinutes(SESSION_MINUTES);
        Membership membership = eligibleMembership(memberId, request.getStartTime().toLocalDate());
        Price price = price(membership);
        Sport sport = activeSport(request.getSportId());

        // Lock order is always member then coach to serialize both member and coach conflict predicates.
        Coach coach = coaches.findByIdForBookingUpdate(request.getCoachId())
                .orElseThrow(() -> new ResourceNotFoundException("Coach not found with id: " + request.getCoachId()));
        requireEligibleCoach(coach, sport);
        validateNoConflicts(coach.getId(), member.getId(), request.getStartTime(), endTime);

        PersonalTrainingBooking booking = new PersonalTrainingBooking();
        booking.setMember(member);
        booking.setCoach(coach);
        booking.setSport(sport);
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(endTime);
        booking.setOriginalPrice(price.original());
        booking.setDiscount(price.discount());
        booking.setFinalPrice(price.finalPrice());
        booking.setStatus(PersonalTrainingBookingStatus.PENDING_PAYMENT);
        LocalDateTime createdAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        booking.setCreatedAt(createdAt);
        booking.setPaymentExpiresAt(createdAt.plusMinutes(PAYMENT_HOLD_MINUTES));
        return toResponse(bookings.save(booking));
    }

    /**
     * Huỷ booking PT. Chờ thanh toán: huỷ luôn và huỷ các yêu cầu thanh toán đang chờ.
     * Đã xác nhận: member huỷ được đến 24 giờ trước giờ tập, ADMIN huỷ thay được trước giờ tập;
     * khoản đã trả chuyển REFUND_REQUIRED để lễ tân hoàn 100%. HLV và member được thông báo.
     * Lễ tân không huỷ booking của member.
     */
    @Transactional
    public PersonalTrainingBookingResponse cancel(Jwt jwt, Integer bookingId) {
        User actor = users.findById(currentUserId(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        boolean staff = role.equals("ADMIN");
        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus()) || (!staff && !role.equals("MEMBER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the member or ADMIN can cancel a PT booking");
        }
        Integer memberId = bookings.findMemberIdById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("PT booking not found with id: " + bookingId));
        if (!staff && !memberId.equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own PT bookings");
        }

        // Same lock order as booking creation and counter payment: member, payments, then booking.
        users.findByIdForBookingUpdate(memberId);
        List<Payment> attempts = payments.findIdsByPurposeAndReferenceIdAndStatusIn(PaymentPurpose.PT_BOOKING, bookingId,
                        List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS)).stream()
                .map(id -> payments.findByIdForUpdate(id).orElseThrow())
                .toList();
        PersonalTrainingBooking booking = bookings.findByIdForLifecycleUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("PT booking not found with id: " + bookingId));

        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        boolean confirmed = booking.getStatus() == PersonalTrainingBookingStatus.CONFIRMED;
        if (!confirmed && booking.getStatus() != PersonalTrainingBookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("PT booking cannot be cancelled: " + booking.getStatus());
        }
        if (confirmed && !booking.getStartTime().isAfter(now)) {
            throw new IllegalArgumentException("PT session has already started");
        }
        if (confirmed && !staff && booking.getStartTime().minusHours(CANCEL_NOTICE_HOURS).isBefore(now)) {
            throw new IllegalArgumentException("Confirmed PT sessions can be cancelled up to " + CANCEL_NOTICE_HOURS
                    + " hours before they start; contact the front desk");
        }

        booking.setStatus(PersonalTrainingBookingStatus.CANCELLED);
        String moneyMessage = "";
        for (Payment payment : attempts) {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setNote("Cancelled with PT booking #" + bookingId);
            } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
                payment.setStatus(PaymentStatus.REFUND_REQUIRED);
                payment.setNote("Refund 100%: PT booking #" + bookingId + " was cancelled by " + role);
                moneyMessage = " Khoản thanh toán #" + payment.getId() + " sẽ được hoàn 100%; vui lòng liên hệ lễ tân để nhận tiền.";
            }
            payments.save(payment);
        }
        String when = booking.getStartTime().format(VN_DATE_TIME);
        notifications.createForUser(memberId, "Buổi PT đã được huỷ",
                "Buổi PT " + booking.getSport().getName() + " lúc " + when + " với HLV "
                        + booking.getCoach().getUser().getName() + " đã được huỷ." + moneyMessage);
        if (confirmed) {
            notifications.createForUser(booking.getCoach().getUser().getId(), "Buổi PT bị huỷ",
                    "Buổi PT " + booking.getSport().getName() + " lúc " + when + " với hội viên "
                            + booking.getMember().getName() + " đã bị huỷ.");
        }
        return toResponse(bookings.save(booking));
    }

    @Transactional(readOnly = true)
    public List<PersonalTrainingBookingResponse> mine(Jwt jwt) {
        Integer memberId = currentUserId(jwt);
        requireMember(memberId);
        return bookings.findAllForMember(memberId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonalTrainingBookingResponse> assignedToMe(Jwt jwt) {
        Integer userId = currentUserId(jwt);
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus()) || !"COACH".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only active COACH accounts can view assigned sessions");
        }
        Coach coach = coaches.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Coach profile was not found"));
        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Inactive coaches cannot view assigned sessions");
        }
        return bookings.findAllForCoach(coach.getId()).stream().map(this::toResponse).toList();
    }

    private Membership eligibleMembership(Integer memberId, LocalDate sessionDate) {
        List<Membership> valid = memberships.findActiveAndValidForPt(memberId, sessionDate).stream()
                .filter(m -> Boolean.TRUE.equals(m.getPlan().getAllowOneOnOneCoaching()))
                .toList();
        if (valid.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "An ACTIVE membership with 1-on-1 coaching eligibility is required on the session date");
        }
        if (valid.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "More than one eligible membership is valid for the session date; staff must resolve the membership records");
        }
        return valid.get(0);
    }

    private Price price(Membership membership) {
        if (configuredBasePrice == null || configuredBasePrice.isBlank()) {
            throw new PtPricingConfigurationException(
                    "PT session pricing is not configured; contact the center administrator");
        }
        final BigDecimal base;
        try {
            base = new BigDecimal(configuredBasePrice.trim()).setScale(2, RoundingMode.UNNECESSARY);
        } catch (RuntimeException ex) {
            throw new PtPricingConfigurationException(
                    "PT session pricing configuration is invalid; contact the center administrator");
        }
        if (base.signum() < 0 || base.precision() > 12) {
            throw new PtPricingConfigurationException(
                    "PT session pricing configuration is invalid; contact the center administrator");
        }
        BigDecimal percent = membership.getPlan().getPersonalTrainingDiscountPercent();
        BigDecimal discount = base.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = base.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Price(base, discount, finalPrice);
    }

    private Sport activeSport(Integer sportId) {
        Sport sport = sports.findById(sportId)
                .orElseThrow(() -> new ResourceNotFoundException("Sport not found with id: " + sportId));
        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ACTIVE sports may be booked");
        }
        return sport;
    }

    private void requireEligibleCoach(Coach coach, Sport sport) {
        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus())
                || !"ACTIVE".equalsIgnoreCase(coach.getUser().getStatus())
                || !"COACH".equalsIgnoreCase(coach.getUser().getRole())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ACTIVE coaches may be booked");
        }
        if (!coachSports.existsByCoachIdAndSportId(coach.getId(), sport.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coach is not qualified for the selected sport");
        }
    }

    private boolean hasScheduleConflict(Integer coachId, Integer memberId, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        return classSessions.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                    coachId, "CANCELLED", end, start)
                || bookings.existsCoachOverlap(coachId, start, end, now)
                || bookings.existsMemberOverlap(memberId, start, end, now);
    }

    private void validateNoConflicts(Integer coachId, Integer memberId, LocalDateTime start, LocalDateTime end) {
        if (hasScheduleConflict(coachId, memberId, start, end)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The selected time conflicts with a coach class or an existing PT booking");
        }
    }

    private void validateStartTime(LocalDateTime startTime) {
        if (startTime == null || !startTime.isAfter(LocalDateTime.now(clock.withZone(BUSINESS_ZONE)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PT booking must start in the future");
        }
    }

    private User requireMember(Integer userId) {
        User member = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        requireActiveMember(member);
        return member;
    }

    private void requireActiveMember(User member) {
        if (!"ACTIVE".equalsIgnoreCase(member.getStatus()) || !"MEMBER".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only active MEMBER accounts can book PT sessions");
        }
    }

    private Integer currentUserId(Jwt jwt) {
        try {
            return Integer.valueOf(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must contain the user ID");
        }
    }

    private PersonalTrainingBookingResponse toResponse(PersonalTrainingBooking booking) {
        return new PersonalTrainingBookingResponse(booking.getId(), booking.getMember().getId(),
                booking.getMember().getName(), booking.getCoach().getId(), booking.getCoach().getUser().getName(),
                booking.getSport().getId(), booking.getSport().getName(), booking.getStartTime(), booking.getEndTime(),
                SESSION_MINUTES, booking.getOriginalPrice(), booking.getDiscount(), booking.getFinalPrice(),
                booking.getStatus().name(), booking.getCreatedAt(), booking.getPaymentExpiresAt());
    }

    private record Price(BigDecimal original, BigDecimal discount, BigDecimal finalPrice) { }
}
