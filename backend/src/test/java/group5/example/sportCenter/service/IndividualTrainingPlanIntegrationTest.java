package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import group5.example.sportCenter.dto.IndividualTrainingPlanRequest;
import group5.example.sportCenter.dto.IndividualTrainingWeekRequest;
import group5.example.sportCenter.dto.TrainingExerciseRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.TrainingPlan;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanExerciseRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanRevisionRepository;
import group5.example.sportCenter.repository.IndividualTrainingPlanWeekRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.TrainingPlanRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class IndividualTrainingPlanIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired CoachRepository coaches;
    @Autowired SportRepository sports;
    @Autowired CourseRepository courses;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseClassRepository classes;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPlanRepository membershipPlans;
    @Autowired IndividualTrainingPlanRepository plans;
    @Autowired IndividualTrainingPlanRevisionRepository revisions;
    @Autowired IndividualTrainingPlanWeekRepository weeks;
    @Autowired IndividualTrainingPlanExerciseRepository exercises;
    @Autowired TrainingPlanRepository classPlans;
    @Autowired IndividualTrainingPlanService service;
    @Autowired JwtService jwtService;
    @Autowired Clock clock;
    @Autowired ObjectMapper objectMapper;

    private MockMvc mvc;
    private LocalDate today;
    private User coachUser;
    private User otherCoachUser;
    private User admin;
    private User member;
    private User otherMember;
    private CourseClass courseClass;

    @BeforeEach
    void setUp() {
        exercises.deleteAllInBatch();
        weeks.deleteAllInBatch();
        revisions.deleteAllInBatch();
        plans.deleteAllInBatch();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        today = LocalDate.now(clock.withZone(ZONE));
        coachUser = user("COACH");
        Coach coach = coach(coachUser);
        otherCoachUser = user("COACH");
        coach(otherCoachUser);
        admin = user("ADMIN");
        member = user("MEMBER");
        otherMember = user("MEMBER");
        courseClass = courseClass(coach);
        register(member, courseClass, CourseRegistrationStatus.CONFIRMED);
        qualifyingMembership(member, true, true, today, today.plusMonths(1));
    }

    @Test
    void coachCreatesRevisionHistoryAndMemberReadsOnlyOwnPlan() throws Exception {
        String createUrl = createUrl(member);
        String body = body("First goal", "Week one", "Footwork");
        String response = mvc.perform(post(createUrl).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.currentRevision.revisionNumber").value(1))
                .andExpect(jsonPath("$.currentRevision.weeks[0].exercises[0].name").value("Footwork"))
                .andReturn().getResponse().getContentAsString();
        Integer planId = integerField(response, "id");

        mvc.perform(put("/api/training/individual-plans/" + planId).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Adjusted goal", "Week one adjusted", "Serve drill")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.revisionNumber").value(2));
        mvc.perform(get("/api/training/individual-plans/" + planId).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.goal").value("Adjusted goal"));
        mvc.perform(get("/api/training/individual-plans/" + planId + "/revisions")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].revisionNumber").value(2))
                .andExpect(jsonPath("$[1].goal").value("First goal"));
        mvc.perform(get("/api/training/me/individual-plans").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(planId));
        mvc.perform(get("/api/training/classes/" + courseClass.getId() + "/individual-plans")
                        .header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(planId));
        mvc.perform(get("/api/training/classes/" + courseClass.getId() + "/individual-plans")
                        .header("Authorization", bearer(otherCoachUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/training/individual-plans/" + planId).header("Authorization", bearer(otherMember)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/training/individual-plans/" + planId).header("Authorization", bearer(otherCoachUser))
                        .contentType("application/json").content(body("Hijack", "Week", "Exercise")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/training/individual-plans/" + planId).header("Authorization", bearer(member))
                        .contentType("application/json").content(body("Member edit", "Week", "Exercise")))
                .andExpect(status().isForbidden());
        assertEquals(2, revisions.count());
    }

    @Test
    void benefitAndConfirmedEnrollmentAreRequiredAndOneOnOneBenefitIsNotSubstituted() throws Exception {
        qualifyingMembership(member, false, true, today, today.plusMonths(1));
        mvc.perform(post(createUrl(member)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Goal", "Week", "Exercise")))
                .andExpect(status().isForbidden());

        qualifyingMembership(member, true, false, today, today.plusMonths(1));
        mvc.perform(post(createUrl(member)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Goal", "Week", "Exercise")))
                .andExpect(status().isCreated());

        qualifyingMembership(otherMember, true, true, today, today.plusMonths(1));
        mvc.perform(post(createUrl(otherMember)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Goal", "Week", "Exercise")))
                .andExpect(status().isConflict());
        assertEquals(1, plans.count());
    }

    @Test
    void expiredMembershipDisablesWritesButPreservesMemberReadAndRenewalRestoresEditing() throws Exception {
        String created = mvc.perform(post(createUrl(member)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Original", "Week", "Exercise")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer planId = integerField(created, "id");
        memberships.findByMemberId(member.getId()).forEach(membership -> {
            membership.setEndDate(today);
            memberships.save(membership);
        });

        mvc.perform(put("/api/training/individual-plans/" + planId).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Expired edit", "Week", "Exercise")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/training/individual-plans/" + planId).header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.goal").value("Original"));

        qualifyingMembership(member, true, false, today, today.plusMonths(1));
        mvc.perform(put("/api/training/individual-plans/" + planId).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Renewed edit", "Week", "Exercise")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.revisionNumber").value(2));
    }

    @Test
    void futureMembershipAndExclusiveEndDateDoNotGrantWriteEntitlement() throws Exception {
        qualifyingMembership(member, true, false, today.plusDays(1), today.plusMonths(1));
        mvc.perform(post(createUrl(member)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Future", "Week", "Exercise")))
                .andExpect(status().isForbidden());
        qualifyingMembership(member, true, false, today.minusMonths(1), today);
        mvc.perform(post(createUrl(member)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("End boundary", "Week", "Exercise")))
                .andExpect(status().isForbidden());
        assertEquals(0, plans.count());
    }

    @Test
    void duplicatePlanIsRejectedAndClassWidePlanRemainsSeparate() throws Exception {
        mvc.perform(post(createUrl(member)).header("Authorization", bearer(admin))
                        .contentType("application/json").content(body("Goal", "Week", "Exercise")))
                .andExpect(status().isCreated());
        mvc.perform(post(createUrl(member)).header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body("Duplicate", "Week", "Exercise")))
                .andExpect(status().isConflict());
        assertEquals(1, plans.count());

        TrainingPlan classPlan = new TrainingPlan();
        classPlan.setCourseClass(courseClass);
        classPlan.setGoal("Shared class goal");
        classPlan.setUpdatedBy(coachUser);
        classPlan.setCreatedAt(LocalDateTime.now(clock));
        classPlan.setUpdatedAt(LocalDateTime.now(clock));
        classPlans.saveAndFlush(classPlan);
        assertEquals("Shared class goal", classPlans.findByCourseClassId(courseClass.getId()).orElseThrow().getGoal());
    }

    @Test
    void concurrentCreateRequestsProduceAtMostOnePlan() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> createAfterGate(ready, start));
            var second = pool.submit(() -> createAfterGate(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "both create requests must reach the start gate");
            start.countDown();
            int successes = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, plans.count());
            assertEquals(1, revisions.count());
        }
    }

    private boolean createAfterGate(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "create start gate was not released");
        try {
            service.create(courseClass.getId(), member.getId(), request("Concurrent", "Week", "Exercise"), jwt(coachUser));
            return true;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 409) return false;
            throw exception;
        }
    }

    private CourseClass courseClass(Coach assignedCoach) {
        Sport sport = new Sport();
        sport.setName("Individual training sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);
        Course course = new Course();
        course.setSport(sport);
        course.setName("Individual course " + UUID.randomUUID());
        course.setStatus("ACTIVE");
        course = courses.saveAndFlush(course);
        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setName("Individual offering " + UUID.randomUUID());
        offering.setPrice(new BigDecimal("100.00"));
        offering.setSessionDuration(90);
        offering.setPlannedSessions(8);
        offering.setStartDate(today.minusDays(1));
        offering.setEndDate(today.plusMonths(2));
        offering.setRegistrationDeadline(today.atStartOfDay().plusDays(1));
        offering.setCancellationDeadline(today.atStartOfDay().plusDays(1));
        offering.setStatus("OPEN");
        offering = offerings.saveAndFlush(offering);
        CourseClass result = new CourseClass();
        result.setCourseOffering(offering);
        result.setCoach(assignedCoach);
        result.setName("Individual class " + UUID.randomUUID());
        result.setCapacity(20);
        result.setStatus("ACTIVE");
        return classes.saveAndFlush(result);
    }

    private void register(User who, CourseClass target, CourseRegistrationStatus statusValue) {
        CourseRegistration registration = new CourseRegistration();
        registration.setMember(who);
        registration.setCourseClass(target);
        registration.setStatus(statusValue);
        registration.setOriginalPrice(new BigDecimal("100.00"));
        registration.setMembershipDiscount(BigDecimal.ZERO);
        registration.setFinalPrice(new BigDecimal("100.00"));
        registrations.saveAndFlush(registration);
    }

    private Membership qualifyingMembership(User who, boolean benefit, boolean oneOnOne,
            LocalDate startDate, LocalDate endDate) {
        memberships.deleteAll(memberships.findByMemberId(who.getId()));
        MembershipPlan plan = new MembershipPlan();
        plan.setName("Individual plan benefit " + UUID.randomUUID());
        plan.setPrice(new BigDecimal("100.00"));
        plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(BigDecimal.ZERO);
        plan.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        plan.setAllowPersonalTrainingPlan(benefit);
        plan.setAllowOneOnOneCoaching(oneOnOne);
        plan.setStatus("ACTIVE");
        plan = membershipPlans.saveAndFlush(plan);
        Membership membership = new Membership();
        membership.setMember(who);
        membership.setPlan(plan);
        membership.setAutoRenew(false);
        membership.setStartDate(startDate);
        membership.setEndDate(endDate);
        membership.setStatus("ACTIVE");
        return memberships.saveAndFlush(membership);
    }

    private Coach coach(User owner) {
        Coach coach = new Coach();
        coach.setUser(owner);
        coach.setStatus("ACTIVE");
        return coaches.saveAndFlush(coach);
    }

    private User user(String role) {
        User result = new User();
        result.setName(role + " individual training " + UUID.randomUUID());
        result.setEmail(role.toLowerCase() + "-individual-" + UUID.randomUUID() + "@example.test");
        result.setPasswordHash("test-only");
        result.setRole(role);
        result.setStatus("ACTIVE");
        return users.saveAndFlush(result);
    }

    private String createUrl(User target) {
        return "/api/training/classes/" + courseClass.getId() + "/members/" + target.getId() + "/individual-plan";
    }

    private String body(String goal, String title, String exercise) {
        return "{\"goal\":\"" + goal + "\",\"description\":\"Personalized course plan\",\"weeks\":[{"
                + "\"weekNumber\":1,\"title\":\"" + title + "\",\"description\":\"Training focus\","
                + "\"exercises\":[{\"name\":\"" + exercise + "\",\"sets\":3,\"reps\":10}]}]}";
    }

    private IndividualTrainingPlanRequest request(String goal, String title, String exercise) {
        return new IndividualTrainingPlanRequest(goal, "Personalized course plan", List.of(
                new IndividualTrainingWeekRequest(1, title, "Training focus",
                        List.of(new TrainingExerciseRequest(exercise, 3, 10, null, null, null)))));
    }

    private Integer integerField(String json, String field) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode value = root.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new AssertionError("Expected integer field '" + field + "' in response JSON");
        }
        return value.intValue();
    }

    private String bearer(User user) { return "Bearer " + jwtService.generateToken(user); }

    private Jwt jwt(User user) {
        return Jwt.withTokenValue("test-token").header("alg", "none").subject(user.getId().toString())
                .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(3600)).build();
    }
}
