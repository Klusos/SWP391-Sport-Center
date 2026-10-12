package group5.example.sportCenter.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.dao.DataIntegrityViolationException;
import group5.example.sportCenter.dto.*;
import group5.example.sportCenter.entity.*;
import group5.example.sportCenter.repository.*;
import static group5.example.sportCenter.service.AssessmentAccess.error;

@Service
@Transactional(readOnly = true)
public class SkillAssessmentService {
    private final SkillAssessmentRepository assessments;
    private final MemberSportLevelRepository levels;
    private final UserRepository users;
    private final SportRepository sports;
    private final CoachRepository coaches;
    private final CoachSportRepository qualifications;
    private final AssessmentAccess access;

    public SkillAssessmentService(SkillAssessmentRepository assessments, MemberSportLevelRepository levels,
            UserRepository users, SportRepository sports, CoachRepository coaches,
            CoachSportRepository qualifications, AssessmentAccess access) {
        this.assessments = assessments; this.levels = levels; this.users = users;
        this.sports = sports; this.coaches = coaches; this.qualifications = qualifications; this.access = access;
    }

    @Transactional
    public SkillAssessmentResponse create(CreateSkillAssessmentRequest request, Jwt jwt) {
        User actor = access.actor(jwt);
        access.requireStaff(actor);
        positive(request.memberId()); positive(request.sportId()); positive(request.assignedCoachId());
        validateNotes(request.notes());
        User member = users.findByIdForAssessmentUpdate(request.memberId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Member user not found"));
        if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus()))
            throw error(HttpStatus.CONFLICT, "Target must be an ACTIVE MEMBER");
        Sport sport = sports.findById(request.sportId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Sport not found"));
        if (!"ACTIVE".equalsIgnoreCase(sport.getStatus())) throw error(HttpStatus.CONFLICT, "Sport must be ACTIVE");
        Coach coach = coaches.findById(request.assignedCoachId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Coach not found"));
        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus()) || !"ACTIVE".equalsIgnoreCase(coach.getUser().getStatus())
                || !"COACH".equalsIgnoreCase(coach.getUser().getRole()))
            throw error(HttpStatus.CONFLICT, "Assigned coach and COACH account must be ACTIVE");
        if (!qualifications.existsByCoachIdAndSportId(coach.getId(), sport.getId()))
            throw error(HttpStatus.CONFLICT, "Coach is not qualified for this sport");
        if (assessments.existsByMemberIdAndSportIdAndStatus(member.getId(), sport.getId(), AssessmentStatus.PENDING))
            throw error(HttpStatus.CONFLICT, "A PENDING assessment already exists for this member and sport");
        SkillAssessment assessment = new SkillAssessment();
        assessment.setMember(member); assessment.setSport(sport); assessment.setAssignedCoach(coach);
        assessment.setRequestedByUser(actor); assessment.setStatus(AssessmentStatus.PENDING); assessment.setNotes(request.notes());
        try { return response(assessments.saveAndFlush(assessment)); }
        catch (DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT, "Assessment conflicts with existing data"); }
    }

    public List<SkillAssessmentResponse> all(Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        return assessments.findAllByOrderByCreatedAtDescIdDesc().stream().map(this::response).toList();
    }
    public List<SkillAssessmentResponse> myAssignments(Jwt jwt) {
        Coach coach = access.coach(access.actor(jwt));
        return assessments.findByAssignedCoachIdOrderByCreatedAtDescIdDesc(coach.getId()).stream().map(this::response).toList();
    }
    public List<SkillAssessmentResponse> mine(Jwt jwt) {
        User member = access.actor(jwt); access.requireMember(member);
        return assessments.findByMemberIdOrderByCreatedAtDescIdDesc(member.getId()).stream().map(this::response).toList();
    }
    public SkillAssessmentResponse get(Integer id, Jwt jwt) {
        User actor = access.actor(jwt);
        SkillAssessment assessment = assessments.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Assessment not found"));
        if (!access.staff(actor)) {
            if ("MEMBER".equalsIgnoreCase(actor.getRole())) {
                if (!actor.getId().equals(assessment.getMember().getId())) throw error(HttpStatus.FORBIDDEN, "Assessment belongs to another member");
            } else if (!access.coach(actor).getId().equals(assessment.getAssignedCoach().getId())) {
                throw error(HttpStatus.FORBIDDEN, "Assessment assigned to another coach");
            }
        }
        return response(assessment);
    }

    @Transactional
    public SkillAssessmentResponse complete(Integer id, CompleteSkillAssessmentRequest request, Jwt jwt) {
        Coach coach = access.coach(access.actor(jwt));
        SportLevel level;
        try { level = SportLevel.valueOf(request.assessedLevel().trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw error(HttpStatus.BAD_REQUEST, "assessedLevel must be BEGINNER, INTERMEDIATE, or ADVANCED"); }
        validateNotes(request.notes());
        SkillAssessment assessment = locked(id);
        if (!coach.getId().equals(assessment.getAssignedCoach().getId())) throw error(HttpStatus.FORBIDDEN, "Only the assigned coach can complete this assessment");
        pending(assessment);
        LocalDateTime now = LocalDateTime.now();
        assessment.setStatus(AssessmentStatus.COMPLETED); assessment.setAssessedLevel(level); assessment.setAssessedAt(now);
        // Omitted/null completion notes retain the assignment notes; an empty string clears them.
        if (request.notes() != null) assessment.setNotes(request.notes());
        MemberSportLevel current = levels.findByMemberIdAndSportId(assessment.getMember().getId(), assessment.getSport().getId())
                .orElseGet(MemberSportLevel::new);
        current.setMember(assessment.getMember()); current.setSport(assessment.getSport()); current.setLevel(level);
        current.setVerifiedByCoach(coach); current.setVerifiedAt(now); current.setUpdatedAt(now);
        // Both writes belong to this transaction. Never catch a failure and commit partial state.
        levels.saveAndFlush(current);
        return response(assessments.saveAndFlush(assessment));
    }

    @Transactional
    public SkillAssessmentResponse cancel(Integer id, Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        SkillAssessment assessment = locked(id); pending(assessment);
        assessment.setStatus(AssessmentStatus.CANCELLED); assessment.setCancelledAt(LocalDateTime.now());
        return response(assessments.saveAndFlush(assessment));
    }
    private SkillAssessment locked(Integer id) {
        Integer memberId = assessments.findMemberIdByAssessmentId(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Assessment not found"));
        // Always member first, then assessment. Works even before the first level insert.
        users.findByIdForAssessmentUpdate(memberId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Member user not found"));
        return assessments.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Assessment not found"));
    }
    private void pending(SkillAssessment assessment) {
        if (assessment.getStatus() != AssessmentStatus.PENDING) throw error(HttpStatus.CONFLICT, "Only PENDING assessments can transition");
    }
    private void positive(Integer id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "IDs must be positive");
    }
    private void validateNotes(String notes) {
        if (notes != null && notes.length() > 1000) throw error(HttpStatus.BAD_REQUEST, "notes cannot exceed 1000 characters");
    }
    private SkillAssessmentResponse response(SkillAssessment a) {
        return new SkillAssessmentResponse(a.getId(), a.getMember().getId(), a.getSport().getId(),
                a.getAssignedCoach().getId(), a.getRequestedByUser().getId(), a.getStatus().name(),
                a.getAssessedLevel() == null ? null : a.getAssessedLevel().name(), a.getNotes(),
                a.getCreatedAt(), a.getAssessedAt(), a.getCancelledAt());
    }
}
