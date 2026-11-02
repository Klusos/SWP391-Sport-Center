package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;

/** Shared coach conflict rules for CourseClass sessions and PT bookings. */
@Service
public class CoachScheduleConflictService {
    private static final String CANCELLED = "CANCELLED";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ClassSessionRepository classSessions;
    private final PersonalTrainingBookingRepository ptBookings;
    private final Clock clock;

    public CoachScheduleConflictService(ClassSessionRepository classSessions,
            PersonalTrainingBookingRepository ptBookings, Clock clock) {
        this.classSessions = classSessions;
        this.ptBookings = ptBookings;
        this.clock = clock;
    }

    public boolean hasConflict(Integer coachId, LocalDateTime startTime, LocalDateTime endTime,
            Integer excludedSessionId) {
        boolean classConflict = excludedSessionId == null
                ? classSessions.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                        coachId, CANCELLED, endTime, startTime)
                : classSessions.existsByCourseClass_Coach_IdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                        coachId, CANCELLED, endTime, startTime, excludedSessionId);
        if (classConflict) return true;

        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        return ptBookings.existsCoachOverlap(coachId, startTime, endTime, now);
    }

    public void requireNoConflict(Integer coachId, LocalDateTime startTime, LocalDateTime endTime,
            Integer excludedSessionId) {
        if (hasConflict(coachId, startTime, endTime, excludedSessionId)) {
            throw new IllegalArgumentException(
                    "Coach already has a ClassSession or active PT Booking during this time range");
        }
    }
}
