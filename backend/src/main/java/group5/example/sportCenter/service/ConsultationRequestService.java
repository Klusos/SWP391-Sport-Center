package group5.example.sportCenter.service;

import static group5.example.sportCenter.service.AssessmentAccess.error;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.dto.CancelConsultationRequest;
import group5.example.sportCenter.dto.CompleteConsultationRequest;
import group5.example.sportCenter.dto.ConsultationResponse;
import group5.example.sportCenter.dto.CreateConsultationMemberRequest;
import group5.example.sportCenter.dto.CreateConsultationRequest;
import group5.example.sportCenter.dto.CreateMemberRequest;
import group5.example.sportCenter.entity.ConsultationRequest;
import group5.example.sportCenter.entity.ConsultationStatus;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ConsultationRequestRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.validation.VietnamesePhoneNumbers;

@Service
@Transactional(readOnly = true)
public class ConsultationRequestService {

    private final ConsultationRequestRepository consultations;
    private final UserRepository users;
    private final SportRepository sports;
    private final MemberRegistrationService memberRegistration;
    private final AssessmentAccess access;
    private final ApplicationEventPublisher events;
    private final AuditLogService auditLogs;
    private final Validator validator;

    public ConsultationRequestService(
            ConsultationRequestRepository consultations,
            UserRepository users,
            SportRepository sports,
            MemberRegistrationService memberRegistration,
            AssessmentAccess access,
            ApplicationEventPublisher events,
            AuditLogService auditLogs,
            Validator validator) {
        this.consultations = consultations;
        this.users = users;
        this.sports = sports;
        this.memberRegistration = memberRegistration;
        this.access = access;
        this.events = events;
        this.auditLogs = auditLogs;
        this.validator = validator;
    }

    @Transactional
    public ConsultationResponse create(CreateConsultationRequest request, Jwt jwt) {
        if (request == null) {
            throw error(HttpStatus.BAD_REQUEST, "Consultation details are required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, violations.iterator().next().getMessage());
        }

        User member = null;
        if (jwt != null) {
            member = access.actor(jwt);
            access.requireMember(member);
            VietnamesePhoneNumbers.normalizeOptional(member.getPhone());
            if (isBlank(member.getName()) || isBlank(member.getPhone()) || isBlank(member.getEmail())) {
                throw error(HttpStatus.BAD_REQUEST,
                        "Vui lòng cập nhật họ tên, số điện thoại và email trong tài khoản hoặc liên hệ lễ tân trước khi gửi yêu cầu.");
            }
        } else {
            if (request.dateOfBirth() == null || !request.dateOfBirth().isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
                throw error(HttpStatus.BAD_REQUEST, "A valid date of birth is required to submit this request");
            }
            if (request.email() != null && users.existsByEmailIgnoreCase(request.email())) {
                throw error(HttpStatus.BAD_REQUEST,
                        "We could not submit this request with those details. If you already have an account, sign in or contact staff.");
            }
        }

        Sport sport = sports.findById(request.sportId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Sport not found"));
        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) {
            throw error(HttpStatus.CONFLICT, "Sport must be ACTIVE");
        }

        ConsultationRequest consultation = new ConsultationRequest();
        consultation.setMember(member);
        consultation.setName(member == null ? request.name().trim() : member.getName().trim());
        consultation.setPhone(member == null
                ? VietnamesePhoneNumbers.normalizeOptional(request.phone())
                : VietnamesePhoneNumbers.normalizeOptional(member.getPhone()));
        consultation.setEmail(member == null ? request.email() : member.getEmail().trim().toLowerCase(Locale.ROOT));
        consultation.setSport(sport);
        consultation.setAge(jwt == null ? null : request.age());
        consultation.setDateOfBirth(jwt == null ? request.dateOfBirth() : member.getDateOfBirth());
        consultation.setExperience(trimToNull(request.experience()));
        consultation.setNote(trimToNull(request.note()));
        consultation.setStatus(ConsultationStatus.PENDING);
        ConsultationRequest created = consultations.save(consultation);
        events.publishEvent(new NotificationBusinessEvents.ConsultationCreated(created.getId()));
        return response(created);
    }

    public List<ConsultationResponse> mine(Jwt jwt) {
        User member = access.actor(jwt);
        access.requireMember(member);
        return consultations.findByMemberIdOrderByCreatedAtDescIdDesc(member.getId())
                .stream().map(this::response).toList();
    }

    public Page<ConsultationResponse> all(String status, int page, int size, Jwt jwt) {
        requireStaff(access.actor(jwt));
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "page must be nonnegative and size must be between 1 and 100");
        }
        PageRequest pageable = PageRequest.of(page, size);
        Page<ConsultationRequest> result;
        if (status == null || status.isBlank()) {
            result = consultations.findAllByOrderByCreatedAtDescIdDesc(pageable);
        } else {
            result = consultations.findByStatusOrderByCreatedAtDescIdDesc(parseStatus(status), pageable);
        }
        return result.map(this::response);
    }

    public ConsultationResponse get(Integer id, Jwt jwt) {
        User actor = access.actor(jwt);
        ConsultationRequest consultation = find(id);
        String role = actor.getRole().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return response(consultation);
        }
        if ("RECEPTIONIST".equals(role)) {
            requireAssignedHandler(actor, consultation);
            return response(consultation);
        }
        if ("MEMBER".equals(role)) {
            if (consultation.getMember() == null || !actor.getId().equals(consultation.getMember().getId())) {
                throw error(HttpStatus.FORBIDDEN, "Consultation belongs to another member");
            }
            return response(consultation);
        }
        throw error(HttpStatus.FORBIDDEN, "Consultation is not available to this role");
    }

    @Transactional
    public ConsultationResponse take(Integer id, Jwt jwt) {
        User actor = access.actor(jwt);
        requireStaff(actor);
        ConsultationRequest consultation = locked(id);
        if (consultation.getStatus() != ConsultationStatus.PENDING) {
            throw error(HttpStatus.CONFLICT, "Only PENDING consultations can be taken");
        }
        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        consultation.setHandledBy(actor);
        consultation.setHandledAt(LocalDateTime.now());
        ConsultationRequest taken = consultations.saveAndFlush(consultation);
        auditLogs.record("TAKE", "CONSULTATION_REQUEST", taken.getId(), "Consultation request moved to IN_PROGRESS.");
        return response(taken);
    }

    @Transactional
    public ConsultationResponse complete(Integer id, CompleteConsultationRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        requireAssignedHandlerOrAdminRole(actor);
        ConsultationRequest consultation = locked(id);
        requireAssignedHandlerOrAdmin(actor, consultation);
        requireInProgress(consultation);
        consultation.setStatus(ConsultationStatus.COMPLETED);
        consultation.setResolutionNote(trimToNull(request.resolutionNote()));
        consultation.setCompletedAt(LocalDateTime.now());
        ConsultationRequest completed = consultations.saveAndFlush(consultation);
        auditLogs.record("COMPLETE", "CONSULTATION_REQUEST", completed.getId(), "Consultation request completed.");
        if (completed.getMember() != null) {
            events.publishEvent(new NotificationBusinessEvents.ConsultationCompleted(
                    completed.getId(), completed.getMember().getId()));
        }
        return response(completed);
    }

    @Transactional
    public ConsultationResponse cancel(Integer id, CancelConsultationRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        requireAssignedHandlerOrAdminRole(actor);
        ConsultationRequest consultation = locked(id);
        requireAssignedHandlerOrAdmin(actor, consultation);
        requireInProgress(consultation);
        consultation.setStatus(ConsultationStatus.CANCELLED);
        consultation.setResolutionNote(request.reason().trim());
        consultation.setCancelledAt(LocalDateTime.now());
        ConsultationRequest cancelled = consultations.saveAndFlush(consultation);
        auditLogs.record("CANCEL", "CONSULTATION_REQUEST", cancelled.getId(), "Consultation request cancelled.");
        if (cancelled.getMember() != null) {
            events.publishEvent(new NotificationBusinessEvents.ConsultationCancelled(
                    cancelled.getId(), cancelled.getMember().getId()));
        }
        return response(cancelled);
    }

    @Transactional
    public ConsultationResponse createMember(
            Integer id, CreateConsultationMemberRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        requireAssignedHandlerOrAdminRole(actor);
        ConsultationRequest consultation = locked(id);
        requireAssignedHandlerOrAdmin(actor, consultation);
        requireInProgress(consultation);
        if (consultation.getMember() != null) {
            throw error(HttpStatus.CONFLICT, "Consultation is already linked to a member");
        }
        if (consultation.getEmail() == null || consultation.getEmail().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "An email is required to create a login account");
        }
        VietnamesePhoneNumbers.normalizeOptional(consultation.getPhone());

        CreateMemberRequest memberRequest = new CreateMemberRequest();
        memberRequest.setName(consultation.getName());
        memberRequest.setEmail(consultation.getEmail());
        memberRequest.setPhone(consultation.getPhone());
        memberRequest.setDateOfBirth(consultation.getDateOfBirth() != null
                ? consultation.getDateOfBirth() : request.dateOfBirth());
        memberRequest.setPassword(request.password());

        final User created;
        try {
            var result = memberRegistration.createMember(memberRequest);
            users.flush();
            created = users.findById(result.id())
                    .orElseThrow(() -> error(HttpStatus.INTERNAL_SERVER_ERROR, "Created member could not be loaded"));
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "A user account with this email already exists");
        }

        consultation.setMember(created);
        try {
            consultations.saveAndFlush(consultation);
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "Consultation could not be linked; member creation was rolled back");
        }
        auditLogs.record("LINK_MEMBER", "CONSULTATION_REQUEST", consultation.getId(),
                "memberId=" + created.getId() + "; account creation is recorded on USER");
        return response(consultation);
    }

    private void requireAssignedHandlerOrAdminRole(User actor) {
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        if (!"RECEPTIONIST".equalsIgnoreCase(actor.getRole())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned handler or ADMIN can change this consultation");
        }
    }

    private void requireAssignedHandlerOrAdmin(User actor, ConsultationRequest consultation) {
        requireAssignedHandlerOrAdminRole(actor);
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) {
            return;
        }
        requireAssignedHandler(actor, consultation);
    }

    private void requireAssignedHandler(User actor, ConsultationRequest consultation) {
        if (consultation.getHandledBy() == null || !actor.getId().equals(consultation.getHandledBy().getId())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned handler can access this consultation");
        }
    }

    private void requireStaff(User actor) {
        access.requireStaff(actor);
    }

    private void requireInProgress(ConsultationRequest consultation) {
        if (consultation.getStatus() != ConsultationStatus.IN_PROGRESS) {
            throw error(HttpStatus.CONFLICT, "Only IN_PROGRESS consultations can be changed");
        }
    }

    private ConsultationStatus parseStatus(String status) {
        try {
            return ConsultationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw error(HttpStatus.BAD_REQUEST, "status must be PENDING, IN_PROGRESS, COMPLETED, or CANCELLED");
        }
    }

    private ConsultationRequest find(Integer id) {
        return consultations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConsultationRequest not found with id: " + id));
    }

    private ConsultationRequest locked(Integer id) {
        return consultations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConsultationRequest not found with id: " + id));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ConsultationResponse response(ConsultationRequest request) {
        return new ConsultationResponse(
                request.getId(),
                request.getMember() == null ? null : request.getMember().getId(),
                request.getName(),
                request.getPhone(),
                request.getEmail(),
                request.getSport().getId(),
                request.getSport().getName(),
                request.getAge(),
                request.getDateOfBirth(),
                request.getExperience(),
                request.getNote(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getHandledBy() == null ? null : request.getHandledBy().getId(),
                request.getHandledBy() == null ? null : request.getHandledBy().getName(),
                request.getHandledAt(),
                request.getResolutionNote(),
                request.getCompletedAt(),
                request.getCancelledAt());
    }
}
