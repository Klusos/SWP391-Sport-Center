package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.PersonalTrainingBookingStatus;
import group5.example.sportCenter.entity.AssessmentStatus;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;
import group5.example.sportCenter.repository.SkillAssessmentRepository;

/** Blocks account deactivation while operational work still depends on the Coach. */
@Service
public class CoachDeactivationGuard {
    private final CourseClassRepository classes;
    private final ClassSessionRepository sessions;
    private final PersonalTrainingBookingRepository bookings;
    private final SkillAssessmentRepository assessments;
    private final Clock clock;

    public CoachDeactivationGuard(CourseClassRepository classes, ClassSessionRepository sessions,
            PersonalTrainingBookingRepository bookings, SkillAssessmentRepository assessments, Clock clock) {
        this.classes = classes;
        this.sessions = sessions;
        this.bookings = bookings;
        this.assessments = assessments;
        this.clock = clock;
    }

    public void requireNoUnfinishedObligations(Coach coach) {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> blockers = new ArrayList<>();
        long activeClasses = classes.findByCoachId(coach.getId()).stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .filter(c -> c.getCourseOffering().getEndDate() == null
                        || !c.getCourseOffering().getEndDate().isBefore(today))
                .count();
        long scheduledSessions = sessions.countByCoachAndScheduled(coach.getId());
        if (activeClasses > 0) blockers.add(activeClasses + " lớp đang hoạt động hoặc chưa kết thúc");
        if (scheduledSessions > 0) blockers.add(scheduledSessions + " buổi học đang chờ hoàn tất");

        long actionablePt = bookings.countUnfinishedForCoach(coach.getId(), now);
        if (actionablePt > 0) blockers.add(actionablePt + " lịch huấn luyện cá nhân đang chờ hoặc đã xác nhận");
        long pendingAssessments = assessments.countByAssignedCoachIdAndStatus(coach.getId(), AssessmentStatus.PENDING);
        if (pendingAssessments > 0) blockers.add(pendingAssessments + " yêu cầu đánh giá đang được giao");

        if (!blockers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể vô hiệu hóa huấn luyện viên khi còn trách nhiệm chưa hoàn tất: "
                            + String.join("; ", blockers) + ". Hãy xử lý các trách nhiệm hiện có trước.");
        }
    }
}
