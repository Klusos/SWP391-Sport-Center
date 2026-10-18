package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import group5.example.sportCenter.dto.CreateClassRequest;
import group5.example.sportCenter.dto.UpdateClassRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.Notification;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("phase1")
class CourseClassNotificationEventsIntegrationTest {

    @Autowired CourseClassService classes;
    @Autowired CourseClassRepository classRows;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseRepository courses;
    @Autowired SportRepository sports;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository coachSports;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;

    private Sport sport;
    private CourseOffering offering;
    private Coach firstCoach;
    private Coach secondCoach;

    @BeforeEach
    void setUp() {
        notifications.deleteAllInBatch();
        sport = new Sport();
        sport.setName("Class event sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);

        Course course = new Course();
        course.setSport(sport);
        course.setName("Class event course");
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);

        offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Class event offering");
        offering.setPrice(new BigDecimal("100000.00"));
        offering.setSessionDuration(60);
        offering.setStartDate(LocalDate.now().plusDays(10));
        offering.setEndDate(LocalDate.now().plusDays(30));
        offering.setRegistrationDeadline(LocalDateTime.now().plusDays(5));
        offering.setCancellationDeadline(LocalDateTime.now().plusDays(5));
        offering.setStatus("OPEN");
        offering = offerings.saveAndFlush(offering);

        // Advance User IDs independently so this test also detects Coach.id/User.id confusion.
        user("MEMBER");
        user("MEMBER");
        user("MEMBER");
        firstCoach = coach(user("COACH"));
        secondCoach = coach(user("COACH"));
        qualify(firstCoach);
        qualify(secondCoach);
    }

    @Test
    void createAndActualReassignmentNotifyOnlyTheNewCoachUserAndNoOpEditsDoNotNotify() {
        var created = classes.createClass(createRequest(firstCoach));
        assertEquals(1, notifications.countByUserIdAndReadFalse(firstCoach.getUser().getId()));
        assertEquals(0, notifications.countByUserIdAndReadFalse(secondCoach.getUser().getId()));
        assertEquals(1, notifications.count());
        Notification first = notifications.findByUserId(firstCoach.getUser().getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
        assertEquals("Class assignment", first.getTitle());
        assertEquals("You have been assigned to class #" + created.getId() + ".", first.getMessage());
        assertEquals(false, firstCoach.getId().equals(firstCoach.getUser().getId()),
                "Fixture must use distinct Coach and User identifiers");

        classes.updateClass(created.getId(), updateRequest(firstCoach, "Renamed class"));
        assertEquals(1, notifications.count());

        classes.updateClass(created.getId(), updateRequest(secondCoach, "Renamed class"));
        assertEquals(1, notifications.countByUserIdAndReadFalse(firstCoach.getUser().getId()),
                "The previous coach is not notified");
        assertEquals(1, notifications.countByUserIdAndReadFalse(secondCoach.getUser().getId()));
        assertEquals(2, notifications.count());

        classes.updateClass(created.getId(), updateRequest(secondCoach, "Another unrelated edit"));
        assertEquals(2, notifications.count(), "Unchanged coach assignment must not emit another event");
    }

    @Test
    void failedClassCreationDoesNotNotify() {
        long classCount = classRows.count();
        Coach unqualified = coach(user("COACH"));
        assertThrows(IllegalArgumentException.class, () -> classes.createClass(createRequest(unqualified)));
        assertEquals(0, notifications.count());
        assertEquals(classCount, classRows.count());

        var created = classes.createClass(createRequest(firstCoach));
        long notificationsAfterCreate = notifications.count();
        assertThrows(IllegalArgumentException.class,
                () -> classes.updateClass(created.getId(), updateRequest(unqualified, "Should not update")));
        assertEquals(notificationsAfterCreate, notifications.count());
        assertEquals(firstCoach.getId(), classRows.findById(created.getId()).orElseThrow().getCoach().getId());
    }

    private CreateClassRequest createRequest(Coach coach) {
        CreateClassRequest request = new CreateClassRequest();
        request.setCourseOfferingId(offering.getId());
        request.setCoachId(coach.getId());
        request.setName("Class event class");
        request.setCapacity(10);
        return request;
    }

    private UpdateClassRequest updateRequest(Coach coach, String name) {
        UpdateClassRequest request = new UpdateClassRequest();
        request.setCoachId(coach.getId());
        request.setName(name);
        request.setCapacity(10);
        request.setStatus("ACTIVE");
        return request;
    }

    private User user(String role) {
        User user = new User();
        user.setName("Event " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private Coach coach(User user) {
        Coach coach = new Coach();
        coach.setUser(user);
        coach.setStatus("ACTIVE");
        return coaches.saveAndFlush(coach);
    }

    private void qualify(Coach coach) {
        CoachSport qualification = new CoachSport();
        qualification.setCoach(coach);
        qualification.setSport(sport);
        qualification.setExperienceYears(1);
        coachSports.saveAndFlush(qualification);
    }
}
