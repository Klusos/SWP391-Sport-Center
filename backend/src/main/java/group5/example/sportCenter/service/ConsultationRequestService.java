package group5.example.sportCenter.service;

import static group5.example.sportCenter.service.AssessmentAccess.error;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
import group5.example.sportCenter.dto.LinkConsultationMemberRequest;
import group5.example.sportCenter.entity.ConsultationRequest;
import group5.example.sportCenter.entity.ConsultationStatus;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ConsultationRequestRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class ConsultationRequestService {

    private final ConsultationRequestRepository consultations;
    private final UserRepository users;
    private final SportRepository sports;
    private final MemberRegistrationService memberRegistration;
    private final AssessmentAccess access;
    private final ApplicationEventPublisher events;

    public ConsultationRequestService(
            ConsultationRequestRepository consultations,
            UserRepository users,
            SportRepository sports,
            MemberRegistrationService memberRegistration,
            AssessmentAccess access,
            ApplicationEventPublisher events) {
        this.consultations = consultations;
        this.users = users;
        this.sports = sports;
        this.memberRegistration = memberRegistration;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public ConsultationResponse create(CreateConsultationRequest request, Jwt jwt) {
        User member = null;
        if (jwt != null) {
            member = access.actor(jwt);
            access.requireMember(member);
        }

        Sport sport = sports.findById(request.sportId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Sport not found"));
        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) {
            throw error(HttpStatus.CONFLICT, "Sport must be ACTIVE");
        }

        ConsultationRequest consultation = new ConsultationRequest();
        consultation.setMember(member);
        consultation.setName(request.name().trim());
        consultation.setPhone(request.phone().trim());
        consultation.setEmail(trimToNull(request.email()));
        consultation.setSport(sport);
        consultation.setAge(request.age());
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
        return response(consultations.saveAndFlush(consultation));
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
            throw error(HttpStatus.CONFLICT, "Consultation is already linked to a member; use link-member only when selecting an existing account");
        }
        if (consultation.getEmail() == null || consultation.getEmail().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "An email is required to create a login account; link an existing MEMBER instead");
        }

        CreateMemberRequest memberRequest = new CreateMemberRequest();
        memberRequest.setName(consultation.getName());
        memberRequest.setEmail(consultation.getEmail());
        memberRequest.setPhone(consultation.getPhone());
        memberRequest.setDateOfBirth(request.dateOfBirth());
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
        return response(consultation);
    }

    @Transactional
    public ConsultationResponse linkMember(Integer id, LinkConsultationMemberRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        requireAssignedHandlerOrAdminRole(actor);
        ConsultationRequest consultation = locked(id);
        requireAssignedHandlerOrAdmin(actor, consultation);
        requireInProgress(consultation);
        if (consultation.getMember() != null) {
            if (consultation.getMember().getId().equals(request.memberId())) {
                return response(consultation);
            }
            throw error(HttpStatus.CONFLICT, "Consultation is already linked to another member");
        }

        User member = users.findById(request.memberId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Member user not found"));
        if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw error(HttpStatus.CONFLICT, "Target account must be an ACTIVE MEMBER");
        }
        consultation.setMember(member);
        return response(consultations.saveAndFlush(consultation));
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
                request.getExperience(),
                request.getNote(),
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getHandledBy() == null ? null : request.getHandledBy().getId(),
                request.getHandledAt(),
                request.getResolutionNote(),
                request.getCompletedAt(),
                request.getCancelledAt());
    }
}
