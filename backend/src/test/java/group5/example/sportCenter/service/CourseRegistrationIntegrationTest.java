package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateCourseRegistrationRequest;
import group5.example.sportCenter.entity.ClassSession;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.Course;
import group5.example.sportCenter.entity.CourseClass;
import group5.example.sportCenter.entity.CourseOffering;
import group5.example.sportCenter.entity.CourseRegistration;
import group5.example.sportCenter.entity.CourseRegistrationStatus;
import group5.example.sportCenter.entity.MemberSportLevel;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.SportLevel;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.ClassSessionRepository;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CourseClassRepository;
import group5.example.sportCenter.repository.CourseOfferingRepository;
import group5.example.sportCenter.repository.CourseRegistrationRepository;
import group5.example.sportCenter.repository.CourseRepository;
import group5.example.sportCenter.repository.MemberSportLevelRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipPlanRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
@Import(CourseRegistrationIntegrationTest.TestClockConfiguration.class)
class CourseRegistrationIntegrationTest {

    private static final Instant TEST_INSTANT = Instant.parse("2026-10-13T12:00:00Z");

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean @Primary
        Clock testClock() {
            return Clock.fixed(TEST_INSTANT, ZoneId.systemDefault());
        }
    }

    @Autowired CourseRegistrationService service;
    @Autowired CourseRegistrationExpirationService registrationExpiration;
    @Autowired MembershipService membershipService;
    @Autowired CourseRegistrationRepository registrations;
    @Autowired CourseRepository courses;
    @Autowired SportRepository sports;
    @Autowired UserRepository users;
    @Autowired MemberSportLevelRepository levels;
    @Autowired MembershipRepository memberships;
    @Autowired MembershipPlanRepository plans;
    @Autowired CourseOfferingRepository offerings;
    @Autowired CourseClassRepository classes;
    @Autowired ClassSessionRepository sessions;
    @Autowired CoachRepository coaches;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired DataSource dataSource;
    @Autowired Clock clock;
    @MockitoSpyBean CourseRegistrationRepository registrationSpy;

    private MockMvc mvc;
    private User member;
    private Sport sport;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        now = LocalDateTime.now(clock);
        member = user("MEMBER", "ACTIVE");
        sport = sport("ACTIVE");
    }

    @Test
    void integrationDatabaseIsOnlyTheIsolatedH2Profile() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url.startsWith("jdbc:h2:mem:phase1"), "Unexpected JDBC URL: " + url);
            assertFalse(url.contains("SportCenterDB"));
        }
    }

    @Test
    void beginnerMemberWithoutLevelRegistersForSelectedClassAndReadsOnlyItsSessions() {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering offering = offering(course, "OPEN", now.plusHours(1));
        CourseClass selected = courseClass(offering, "ACTIVE", 5);
        CourseClass sibling = courseClass(offering, "ACTIVE", 5);
        session(selected, now.plusDays(2), now.plusDays(2).plusHours(1), "SCHEDULED");
        session(selected, now.plusDays(4), now.plusDays(4).plusHours(1), "CANCELLED");
        session(sibling, now.plusDays(3), now.plusDays(3).plusHours(1), "SCHEDULED");

        var response = service.register(request(selected), jwt(member));
        assertEquals(member.getId(), response.memberId());
        assertEquals(course.getId(), response.courseId());
        assertEquals(offering.getId(), response.courseOfferingId());
        assertEquals(selected.getId(), response.classId());
        assertEquals("PENDING_PAYMENT", response.status());
        assertEquals(0, service.myCourseSessions(jwt(member)).size());
        CourseRegistration saved = registrations.findById(response.id()).orElseThrow();
        saved.setStatus(CourseRegistrationStatus.CONFIRMED);
        registrations.saveAndFlush(saved);
        assertEquals(1, service.myCourseSessions(jwt(member)).size());
        assertEquals(selected.getId(), service.myCourseSessions(jwt(member)).get(0).classId());
    }

    @Test
    void receptionistCreatesPendingRegistrationForSelectedMemberAndCounterSettlementConfirmsIt() throws Exception {
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseClass selected = classFor(course, 1);
        String registrationBody = "{\"memberId\":" + member.getId() + ",\"classId\":" + selected.getId() + "}";

        var created = mvc.perform(post("/api/course-registrations/staff")
                        .header("Authorization", bearer(receptionist))
                        .contentType("application/json").content(registrationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentDeadline").exists())
                .andExpect(jsonPath("$.finalPrice").value(100.0))
                .andReturn();
        int registrationId = responseId(created.getResponse().getContentAsString());

        mvc.perform(post("/api/course-registrations/staff")
                        .header("Authorization", bearer(member))
                        .contentType("application/json").content(registrationBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/course-registrations/staff")
                        .contentType("application/json").content(registrationBody))
                .andExpect(status().isUnauthorized());
        User admin = user("ADMIN", "ACTIVE");
        CourseClass adminClass = classFor(course, 2);
        mvc.perform(post("/api/course-registrations/staff")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{\"memberId\":" + member.getId() + ",\"classId\":" + adminClass.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.registerForMember(member.getId(), request(selected), jwt(receptionist)))
                .getStatusCode().value());
        User secondMember = user("MEMBER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.registerForMember(secondMember.getId(), request(selected), jwt(receptionist)))
                .getStatusCode().value());

        mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"memberId\":" + secondMember.getId()
                                + ",\"method\":\"COUNTER\",\"purpose\":\"COURSE_REGISTRATION\",\"referenceId\":"
                                + registrationId + "}"))
                .andExpect(status().isForbidden());

        var payment = mvc.perform(post("/api/payments")
                        .header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"memberId\":" + member.getId()
                                + ",\"method\":\"COUNTER\",\"purpose\":\"COURSE_REGISTRATION\",\"referenceId\":"
                                + registrationId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.referenceId").value(registrationId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        int paymentId = responseId(payment.getResponse().getContentAsString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/payments/" + paymentId + "/counter")
                        .header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"success\":true,\"note\":\"received at counter\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertEquals(CourseRegistrationStatus.CONFIRMED,
                registrations.findById(registrationId).orElseThrow().getStatus());
    }

    @Test
    void equalOrHigherCoachVerifiedLevelAllowsEnrollmentAndInsufficientLevelIsRejected() {
        Course intermediate = course(sport, "INTERMEDIATE", "ACTIVE");
        CourseClass intermediateClass = classFor(intermediate, 5);
        verifyLevel(member, sport, SportLevel.INTERMEDIATE);
        assertEquals("PENDING_PAYMENT", service.register(request(intermediateClass), jwt(member)).status());

        User advancedMember = user("MEMBER", "ACTIVE");
        verifyLevel(advancedMember, sport, SportLevel.ADVANCED);
        Course lower = course(sport, "BEGINNER", "ACTIVE");
        assertEquals("PENDING_PAYMENT", service.register(request(classFor(lower, 5)), jwt(advancedMember)).status());

        Course advanced = course(sport, "ADVANCED", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(advanced, 5)), jwt(member))).getStatusCode().value());
    }

    @Test
    void rejectsInactiveMemberClassOfferingCourseAndSport() {
        Course activeCourse = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering openOffering = offering(activeCourse, "OPEN", now.plusHours(1));
        CourseClass activeClass = courseClass(openOffering, "ACTIVE", 5);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.register(request(activeClass), jwt(user("MEMBER", "INACTIVE"))))
                .getStatusCode().value());

        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(courseClass(openOffering, "INACTIVE", 5)), jwt(member)))
                .getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(courseClass(offering(activeCourse, "CLOSED", now.plusHours(1)), "ACTIVE", 5)), jwt(member)))
                .getStatusCode().value());
        Course inactiveCourse = course(sport, "BEGINNER", "INACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(inactiveCourse, 5)), jwt(member))).getStatusCode().value());
        Sport inactiveSport = sport("INACTIVE");
        Course inactiveSportCourse = course(inactiveSport, "BEGINNER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(inactiveSportCourse, 5)), jwt(member))).getStatusCode().value());
        assertTrue(assertThrows(ResourceNotFoundException.class,
                () -> service.register(new CreateCourseRegistrationRequest(Integer.MAX_VALUE), jwt(member)))
                .getMessage().contains("CourseClass not found"));
    }

    @Test
    void enforcesAgeRangeAndCoachVerifiedLevelRequirements() {
        member.setDateOfBirth(LocalDate.now().minusYears(20));
        users.saveAndFlush(member);
        Course ageEligible = course(sport, "BEGINNER", "ACTIVE");
        ageEligible.setTargetAgeMin(18);
        ageEligible.setTargetAgeMax(40);
        courses.saveAndFlush(ageEligible);
        assertEquals("PENDING_PAYMENT", service.register(request(classFor(ageEligible, 5)), jwt(member)).status());

        Course ageIneligible = course(sport, "BEGINNER", "ACTIVE");
        ageIneligible.setTargetAgeMin(30);
        courses.saveAndFlush(ageIneligible);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(ageIneligible, 5)), jwt(member))).getStatusCode().value());

        Course ageRequiresDob = course(sport, "BEGINNER", "ACTIVE");
        ageRequiresDob.setTargetAgeMax(40);
        courses.saveAndFlush(ageRequiresDob);
        User missingDob = user("MEMBER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(ageRequiresDob, 5)), jwt(missingDob))).getStatusCode().value());

        Course unverifiedRequirement = course(sport, "INTERMEDIATE", "ACTIVE");
        User noDob = user("MEMBER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(unverifiedRequirement, 5)), jwt(noDob))).getStatusCode().value());
    }

    @Test
    void offeringDeadlineAllowsBeforeCutoffAndRejectsAtOrAfterCutoff() {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        // SQL Server DATETIME2(6) and the H2 profile persist microsecond precision; use a full
        // second before the cutoff so this case remains distinct after database round-tripping.
        assertEquals("PENDING_PAYMENT", service.register(request(classFor(course, now.plusSeconds(1), 5)), jwt(member)).status());
        Course afterDeadlineCourse = course(sport, "BEGINNER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(afterDeadlineCourse, now.minusSeconds(1), 5)), jwt(member)))
                .getStatusCode().value());
        Course exactDeadlineCourse = course(sport, "BEGINNER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(classFor(exactDeadlineCourse, now, 5)), jwt(member)))
                .getStatusCode().value());
    }

    @Test
    void registrationWindowOpensOnTheThirtiethCalendarDay() {
        LocalDate registrationDate = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        Course tooEarlyCourse = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering tooEarlyOffering = offering(tooEarlyCourse, "OPEN", now.plusHours(1));
        tooEarlyOffering.setStartDate(registrationDate.plusDays(31));
        offerings.saveAndFlush(tooEarlyOffering);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(courseClass(tooEarlyOffering, "ACTIVE", 3)), jwt(member)))
                .getStatusCode().value());

        Course exactCourse = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering exactOffering = offering(exactCourse, "OPEN", now.plusHours(1));
        exactOffering.setStartDate(registrationDate.plusDays(30));
        offerings.saveAndFlush(exactOffering);
        assertEquals("PENDING_PAYMENT", service.register(
                request(courseClass(exactOffering, "ACTIVE", 3)), jwt(member)).status());

        Course insideCourse = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering insideOffering = offering(insideCourse, "OPEN", now.plusHours(1));
        insideOffering.setStartDate(registrationDate.plusDays(20));
        offerings.saveAndFlush(insideOffering);
        assertEquals(registrationDate.plusDays(20),
                offerings.findById(insideOffering.getId()).orElseThrow().getStartDate());
        assertEquals("PENDING_PAYMENT", service.register(
                request(courseClass(insideOffering, "ACTIVE", 3)), jwt(user("MEMBER", "ACTIVE"))).status());
    }

    @Test
    void eligibleMembershipUsesRegistrationDateAndSnapshotsHighestDiscount() {
        LocalDate today = now.toLocalDate();
        User pricedMember = user("MEMBER", "ACTIVE");
        MembershipPlan tenPercent = plan("10.00");
        MembershipPlan twentyPercent = plan("20.00");
        CourseClass selected = pricedClass("100.00");
        membership(pricedMember, tenPercent, today, today.plusDays(1), "ACTIVE", false);
        membership(pricedMember, twentyPercent, today.minusDays(3), today.plusDays(1), "ACTIVE", false);

        var response = service.register(request(selected), jwt(pricedMember));
        assertEquals(new BigDecimal("100.00"), response.originalPrice());
        assertEquals(new BigDecimal("20.00"), response.membershipDiscount());
        assertEquals(new BigDecimal("80.00"), response.finalPrice());

        var secondOfferingRegistration = service.register(request(pricedClass("50.00")), jwt(pricedMember));
        assertEquals(new BigDecimal("10.00"), secondOfferingRegistration.membershipDiscount());
        assertEquals(new BigDecimal("40.00"), secondOfferingRegistration.finalPrice());

        MembershipPlan changedPlan = plans.findById(twentyPercent.getId()).orElseThrow();
        changedPlan.setCourseDiscountPercent(new BigDecimal("90.00"));
        plans.saveAndFlush(changedPlan);
        selected.getCourseOffering().setPrice(new BigDecimal("500.00"));
        offerings.saveAndFlush(selected.getCourseOffering());
        membership(pricedMember, plan("100.00"), today.plusDays(1), today.plusMonths(2), "ACTIVE", false);

        var persisted = registrations.findById(response.id()).orElseThrow();
        assertEquals(new BigDecimal("100.00"), persisted.getOriginalPrice());
        assertEquals(new BigDecimal("20.00"), persisted.getMembershipDiscount());
        assertEquals(new BigDecimal("80.00"), persisted.getFinalPrice());
    }

    @Test
    void exclusiveMembershipDatesAndNoMembershipUseFullPrice() {
        LocalDate today = now.toLocalDate();
        MembershipPlan halfOff = plan("50.00");

        User endingToday = user("MEMBER", "ACTIVE");
        membership(endingToday, halfOff, today.minusDays(10), today, "ACTIVE", false);
        var endBoundary = service.register(request(pricedClass("100.00")), jwt(endingToday));
        assertEquals(new BigDecimal("0.00"), endBoundary.membershipDiscount());
        assertEquals(new BigDecimal("100.00"), endBoundary.finalPrice());

        User startsTomorrow = user("MEMBER", "ACTIVE");
        membership(startsTomorrow, halfOff, today.plusDays(1), today.plusMonths(1), "ACTIVE", false);
        assertEquals(new BigDecimal("0.00"), service.register(
                request(pricedClass("100.00")), jwt(startsTomorrow)).membershipDiscount());

        User expired = user("MEMBER", "ACTIVE");
        membership(expired, halfOff, today.minusMonths(2), today.minusDays(1), "EXPIRED", false);
        assertEquals(new BigDecimal("0.00"), service.register(
                request(pricedClass("100.00")), jwt(expired)).membershipDiscount());

        User noMembership = user("MEMBER", "ACTIVE");
        var noDiscount = service.register(request(pricedClass("123.45")), jwt(noMembership));
        assertEquals(new BigDecimal("0.00"), noDiscount.membershipDiscount());
        assertEquals(new BigDecimal("123.45"), noDiscount.finalPrice());
    }

    @Test
    void discountUsesCoursePercentAndRoundsHalfUpForZeroFullAndFractionalRates() {
        User zeroMember = user("MEMBER", "ACTIVE");
        membership(zeroMember, plan("0.00"), now.toLocalDate(), now.toLocalDate().plusDays(1), "ACTIVE", false);
        assertEquals(new BigDecimal("0.00"), service.register(
                request(pricedClass("19.99")), jwt(zeroMember)).membershipDiscount());

        User fullMember = user("MEMBER", "ACTIVE");
        membership(fullMember, plan("100.00"), now.toLocalDate(), now.toLocalDate().plusDays(1), "ACTIVE", false);
        var full = service.register(request(pricedClass("19.99")), jwt(fullMember));
        assertEquals(new BigDecimal("19.99"), full.membershipDiscount());
        assertEquals(new BigDecimal("0.00"), full.finalPrice());

        User fractionalMember = user("MEMBER", "ACTIVE");
        membership(fractionalMember, plan("5.00"), now.toLocalDate(), now.toLocalDate().plusDays(1), "ACTIVE", false);
        var fractional = service.register(request(pricedClass("0.10")), jwt(fractionalMember));
        assertEquals(new BigDecimal("0.01"), fractional.membershipDiscount());
        assertEquals(new BigDecimal("0.09"), fractional.finalPrice());

        User invalidPriceMember = user("MEMBER", "ACTIVE");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(pricedClass("-1.00")), jwt(invalidPriceMember)))
                .getStatusCode().value());
    }

    @Test
    void membershipExpirationUsesExclusiveEndDateRegardlessOfAutoRenewAndIsIdempotent() {
        LocalDate today = now.toLocalDate();
        memberships.deleteAll();
        MembershipPlan plan = plan("10.00");
        User regular = user("MEMBER", "ACTIVE");
        User renewing = user("MEMBER", "ACTIVE");
        User expiredEarlier = user("MEMBER", "ACTIVE");
        User alreadyExpired = user("MEMBER", "ACTIVE");
        User validTomorrow = user("MEMBER", "ACTIVE");
        User renewingValidTomorrow = user("MEMBER", "ACTIVE");
        membership(regular, plan, today.minusMonths(1), today, "ACTIVE", false);
        membership(renewing, plan, today.minusMonths(1), today, "ACTIVE", true);
        membership(expiredEarlier, plan, today.minusMonths(2), today.minusDays(1), "ACTIVE", true);
        membership(alreadyExpired, plan, today.minusMonths(2), today.minusDays(1), "EXPIRED", true);
        membership(validTomorrow, plan, today.minusMonths(1), today.plusDays(1), "ACTIVE", false);
        membership(renewingValidTomorrow, plan, today.minusMonths(1), today.plusDays(1), "ACTIVE", true);

        assertEquals(3, membershipService.expireMemberships());
        assertEquals("EXPIRED", memberships.findByMemberId(regular.getId()).get(0).getStatus());
        assertEquals("EXPIRED", memberships.findByMemberId(renewing.getId()).get(0).getStatus());
        assertEquals("EXPIRED", memberships.findByMemberId(expiredEarlier.getId()).get(0).getStatus());
        assertEquals("EXPIRED", memberships.findByMemberId(alreadyExpired.getId()).get(0).getStatus());
        assertEquals("ACTIVE", memberships.findByMemberId(validTomorrow.getId()).get(0).getStatus());
        assertEquals("ACTIVE", memberships.findByMemberId(renewingValidTomorrow.getId()).get(0).getStatus());
        assertEquals(0, membershipService.expireMemberships());
        assertEquals("EXPIRED", memberships.findByMemberId(renewing.getId()).get(0).getStatus());
    }

    @Test
    void expirationDoesNotDiscountOrChangeExistingRegistrationPriceSnapshots() {
        LocalDate today = now.toLocalDate();
        User pricedMember = user("MEMBER", "ACTIVE");
        MembershipPlan plan = plan("10.00");
        Membership membership = membership(pricedMember, plan, today.minusDays(2), today.plusDays(1), "ACTIVE", true);
        var originalRegistration = service.register(request(pricedClass("100.00")), jwt(pricedMember));
        assertEquals(new BigDecimal("10.00"), originalRegistration.membershipDiscount());

        membership.setEndDate(today);
        memberships.saveAndFlush(membership);
        assertEquals(1, membershipService.expireMemberships());
        assertEquals("EXPIRED", memberships.findById(membership.getId()).orElseThrow().getStatus());

        var afterExpiry = service.register(request(pricedClass("100.00")), jwt(pricedMember));
        assertEquals(new BigDecimal("0.00"), afterExpiry.membershipDiscount());
        var persistedRegistration = registrations.findById(originalRegistration.id()).orElseThrow();
        assertEquals(new BigDecimal("100.00"), persistedRegistration.getOriginalPrice());
        assertEquals(new BigDecimal("10.00"), persistedRegistration.getMembershipDiscount());
        assertEquals(new BigDecimal("90.00"), persistedRegistration.getFinalPrice());
    }

    @Test
    void membershipOwnershipAndManualExpirationAuthorizationRemainEnforced() {
        LocalDate today = now.toLocalDate();
        User otherMember = user("MEMBER", "ACTIVE");
        Membership other = membership(otherMember, plan("10.00"), today.minusDays(2), today, "ACTIVE", true);

        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> membershipService.getMembershipById(other.getId(), jwt(member)))
                .getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> membershipService.expireMemberships(jwt(user("RECEPTIONIST", "ACTIVE"))))
                .getStatusCode().value());

        User admin = user("ADMIN", "ACTIVE");
        assertEquals(1, membershipService.expireMemberships(jwt(admin)));
        assertEquals("EXPIRED", memberships.findById(other.getId()).orElseThrow().getStatus());
    }

    @Test
    void membershipReadEndpointsMapLazyAssociationsAndEnforceRoleOwnership() throws Exception {
        MembershipPlan membershipPlan = plan("10.00");
        Membership own = membership(member, membershipPlan, now.toLocalDate(), now.toLocalDate().plusDays(30),
                "ACTIVE", false);
        User otherMember = user("MEMBER", "ACTIVE");
        Membership other = membership(otherMember, membershipPlan, now.toLocalDate(), now.toLocalDate().plusDays(30),
                "ACTIVE", false);
        User admin = user("ADMIN", "ACTIVE");
        User receptionist = user("RECEPTIONIST", "ACTIVE");

        mvc.perform(get("/api/memberships/" + own.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberName").value(member.getName()))
                .andExpect(jsonPath("$.planName").value(membershipPlan.getName()));
        mvc.perform(get("/api/memberships/member/" + member.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberName").value(member.getName()))
                .andExpect(jsonPath("$[0].planName").value(membershipPlan.getName()));

        mvc.perform(get("/api/memberships/" + other.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/memberships").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/memberships").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/memberships/" + own.getId()).header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/memberships/" + other.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/memberships/member/" + otherMember.getId()).header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/memberships").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsFullClassDuplicateAndAnotherClassInSameOffering() {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering offering = offering(course, "OPEN", now.plusHours(1));
        CourseClass first = courseClass(offering, "ACTIVE", 1);
        CourseClass second = courseClass(offering, "ACTIVE", 3);
        service.register(request(first), jwt(member));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(first), jwt(member))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(second), jwt(member))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(first), jwt(user("MEMBER", "ACTIVE"))))
                .getStatusCode().value());
    }

    @Test
    void pendingHoldUsesEarlierDeadlineAndExpirationReleasesSeatIdempotently() {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering offering = offering(course, "OPEN", now.plusHours(12));
        CourseClass selected = courseClass(offering, "ACTIVE", 1);
        var hold = service.register(request(selected), jwt(member));
        assertEquals("PENDING_PAYMENT", hold.status());
        assertEquals(offering.getRegistrationDeadline(), hold.paymentDeadline());

        CourseRegistration saved = registrations.findById(hold.id()).orElseThrow();
        saved.setPaymentDeadline(now);
        registrations.saveAndFlush(saved);
        assertEquals(1, registrationExpiration.expireDueRegistrations());
        assertEquals(0, registrationExpiration.expireDueRegistrations());
        assertEquals(CourseRegistrationStatus.EXPIRED, registrations.findById(hold.id()).orElseThrow().getStatus());

        User secondMember = user("MEMBER", "ACTIVE");
        assertEquals("PENDING_PAYMENT", service.register(request(selected), jwt(secondMember)).status());
    }

    @Test
    void pendingHoldUsesTwentyFourHoursWhenRegistrationDeadlineIsLaterAndCanBeCancelled() {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseClass selected = courseClass(offering(course, "OPEN", now.plusDays(3)), "ACTIVE", 1);
        var response = service.register(request(selected), jwt(member));
        assertEquals(now.plusHours(24), response.paymentDeadline());
        assertEquals("CANCELLED", service.cancel(response.id(), jwt(member)).status());
        assertEquals(0, registrations.countOccupiedSeats(selected.getId(), CourseRegistrationStatus.CONFIRMED,
                CourseRegistrationStatus.PENDING_PAYMENT, now));
    }

    @Test
    void pendingCancellationReleasesTheHoldAndEnforcesMemberOwnership() {
        CourseClass selected = classFor(course(sport, "BEGINNER", "ACTIVE"), 1);
        var response = service.register(request(selected), jwt(member));
        User other = user("MEMBER", "ACTIVE");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.cancel(response.id(), jwt(other))).getStatusCode().value());
        assertEquals("CANCELLED", service.cancel(response.id(), jwt(member)).status());
        assertEquals(0, registrations.countOccupiedSeats(selected.getId(), CourseRegistrationStatus.CONFIRMED,
                CourseRegistrationStatus.PENDING_PAYMENT, now));
    }

    @Test
    void concurrentMembersCompetingForLastSeatHaveOneWinner() throws Exception {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseClass selected = classFor(course, 1);
        User secondMember = user("MEMBER", "ACTIVE");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> raceRegister(member, selected, ready, start));
            Future<String> second = pool.submit(() -> raceRegister(secondMember, selected, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("201"::equals).count(), results.toString());
            assertEquals(1, results.stream().filter("409"::equals).count(), results.toString());
            assertEquals(1, registrations.countByClassIdAndStatus(selected.getId(), CourseRegistrationStatus.PENDING_PAYMENT));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateClassRegistrationHasOneWinner() throws Exception {
        CourseClass selected = classFor(course(sport, "BEGINNER", "ACTIVE"), 3);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> raceRegister(member, selected, ready, start));
            Future<String> second = pool.submit(() -> raceRegister(member, selected, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("201"::equals).count(), results.toString());
            assertEquals(1, results.stream().filter("409"::equals).count(), results.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void detectsOverlappingUpcomingSessionsAcrossSelectedClassesButNotTouchingTimes() {
        CourseClass enrolled = classFor(course(sport, "BEGINNER", "ACTIVE"), 5);
        CourseClass requested = classFor(course(sport, "BEGINNER", "ACTIVE"), 5);
        service.register(request(enrolled), jwt(member));
        LocalDateTime start = now.plusDays(2);
        session(enrolled, start, start.plusHours(1), "SCHEDULED");
        session(requested, start.plusMinutes(30), start.plusMinutes(90), "SCHEDULED");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(request(requested), jwt(member))).getStatusCode().value());

        CourseClass touching = classFor(course(sport, "BEGINNER", "ACTIVE"), 5);
        session(touching, start.plusHours(1), start.plusHours(2), "SCHEDULED");
        assertEquals("PENDING_PAYMENT", service.register(request(touching), jwt(member)).status());
    }

    @Test
    void memberCannotReadOrRegisterAsAnotherUserAndStaffReadRolesAreRestricted() throws Exception {
        CourseClass selected = classFor(course(sport, "BEGINNER", "ACTIVE"), 5);
        User otherMember = user("MEMBER", "ACTIVE");
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        User coach = user("COACH", "ACTIVE");

        mvc.perform(post("/api/course-registrations").contentType("application/json")
                .content("{\"classId\":" + selected.getId() + "}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/course-registrations").header("Authorization", bearer(receptionist))
                .contentType("application/json").content("{\"classId\":" + selected.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/course-registrations").header("Authorization", bearer(member))
                .contentType("application/json").content("{\"classId\":" + selected.getId()
                        + ",\"memberId\":" + otherMember.getId()
                        + ",\"originalPrice\":0,\"membershipDiscount\":0,\"finalPrice\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentDeadline").exists()).andExpect(jsonPath("$.memberId").value(member.getId()))
                .andExpect(jsonPath("$.originalPrice").value(100.0))
                .andExpect(jsonPath("$.membershipDiscount").value(0.0))
                .andExpect(jsonPath("$.finalPrice").value(100.0));
        mvc.perform(get("/api/course-registrations/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].classId").value(selected.getId()));
        mvc.perform(get("/api/course-registrations/me").header("Authorization", bearer(otherMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/course-registrations").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/course-registrations").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/course-registrations").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].classId").value(selected.getId()));
    }

    @Test
    void persistenceFailureLeavesNoRegistration() {
        CourseClass selected = classFor(course(sport, "BEGINNER", "ACTIVE"), 5);
        doThrow(new DataIntegrityViolationException("simulated persistence failure"))
                .when(registrationSpy).saveAndFlush(any());
        assertThrows(DataIntegrityViolationException.class, () -> service.register(request(selected), jwt(member)));
        assertEquals(0, registrations.countByClassIdAndStatus(selected.getId(), CourseRegistrationStatus.PENDING_PAYMENT));
    }

    private String raceRegister(User user, CourseClass selected, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
        try {
            service.register(request(selected), jwt(user));
            return "201";
        } catch (ResponseStatusException ex) {
            return Integer.toString(ex.getStatusCode().value());
        }
    }

    private CreateCourseRegistrationRequest request(CourseClass selected) {
        return new CreateCourseRegistrationRequest(selected.getId());
    }

    private CourseClass classFor(Course course, Integer capacity) {
        return courseClass(offering(course, "OPEN", now.plusHours(1)), "ACTIVE", capacity);
    }

    private CourseClass classFor(Course course, LocalDateTime deadline, Integer capacity) {
        return courseClass(offering(course, "OPEN", deadline), "ACTIVE", capacity);
    }

    private User user(String role, String status) {
        User result = new User();
        result.setName(role + " test");
        result.setEmail(role.toLowerCase() + "-" + UUID.randomUUID() + "@example.test");
        result.setRole(role);
        result.setStatus(status);
        result.setPasswordHash("test-only-unused-hash");
        return users.saveAndFlush(result);
    }

    private Sport sport(String status) {
        Sport result = new Sport();
        result.setName("Sport-" + UUID.randomUUID());
        result.setStatus(status);
        return sports.saveAndFlush(result);
    }

    private Course course(Sport forSport, String requiredLevel, String status) {
        Course result = new Course();
        result.setSport(forSport);
        result.setName("Course-" + UUID.randomUUID());
        result.setLevel(requiredLevel);
        result.setStatus(status);
        return courses.saveAndFlush(result);
    }

    private CourseOffering offering(Course course, String status, LocalDateTime deadline) {
        CourseOffering result = new CourseOffering();
        result.setCourse(course);
        result.setName("Offering-" + UUID.randomUUID());
        result.setPrice(new BigDecimal("100.00"));
        result.setPlannedSessions(2);
        result.setSessionDuration(60);
        result.setStartDate(now.toLocalDate().plusDays(30));
        result.setEndDate(now.toLocalDate().plusDays(60));
        result.setRegistrationDeadline(deadline);
        result.setCancellationDeadline(deadline);
        result.setStatus(status);
        return offerings.saveAndFlush(result);
    }

    private CourseClass courseClass(CourseOffering offering, String status, Integer capacity) {
        User coachUser = user("COACH", "ACTIVE");
        Coach coach = new Coach();
        coach.setUser(coachUser);
        coach.setStatus("ACTIVE");
        coach = coaches.saveAndFlush(coach);
        CourseClass result = new CourseClass();
        result.setCourseOffering(offering);
        result.setCoach(coach);
        result.setName("Class-" + UUID.randomUUID());
        result.setCapacity(capacity);
        result.setStatus(status);
        return classes.saveAndFlush(result);
    }

    private CourseClass pricedClass(String price) {
        Course course = course(sport, "BEGINNER", "ACTIVE");
        CourseOffering offering = offering(course, "OPEN", now.plusHours(1));
        offering.setPrice(new BigDecimal(price));
        offerings.saveAndFlush(offering);
        return courseClass(offering, "ACTIVE", 10);
    }

    private MembershipPlan plan(String discountPercent) {
        MembershipPlan result = new MembershipPlan();
        result.setName("Plan-" + UUID.randomUUID());
        result.setPrice(new BigDecimal("20.00"));
        result.setDurationMonths(1);
        result.setCourseDiscountPercent(new BigDecimal(discountPercent));
        result.setPersonalTrainingDiscountPercent(BigDecimal.ZERO);
        result.setAllowPersonalTrainingPlan(false);
        result.setAllowOneOnOneCoaching(false);
        result.setStatus("ACTIVE");
        return plans.saveAndFlush(result);
    }

    private Membership membership(User forMember, MembershipPlan plan, LocalDate start, LocalDate end,
            String status, boolean autoRenew) {
        Membership result = new Membership();
        result.setMember(forMember);
        result.setPlan(plan);
        result.setStartDate(start);
        result.setEndDate(end);
        result.setStatus(status);
        result.setAutoRenew(autoRenew);
        return memberships.saveAndFlush(result);
    }

    private void session(CourseClass courseClass, LocalDateTime start, LocalDateTime end, String status) {
        ClassSession result = new ClassSession();
        result.setCourseClass(courseClass);
        result.setStartTime(start);
        result.setEndTime(end);
        result.setStatus(status);
        sessions.saveAndFlush(result);
    }

    private void verifyLevel(User forMember, Sport forSport, SportLevel level) {
        User coachUser = user("COACH", "ACTIVE");
        Coach coach = new Coach();
        coach.setUser(coachUser);
        coach.setStatus("ACTIVE");
        coach = coaches.saveAndFlush(coach);
        MemberSportLevel record = new MemberSportLevel();
        record.setMember(forMember);
        record.setSport(forSport);
        record.setLevel(level);
        record.setVerifiedByCoach(coach);
        record.setVerifiedAt(now);
        levels.saveAndFlush(record);
    }

    private Jwt jwt(User user) {
        return tokens.validateToken(tokens.generateToken(user));
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }

    private int responseId(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)").matcher(body);
        assertTrue(matcher.find(), "Response must contain an id: " + body);
        return Integer.parseInt(matcher.group(1));
    }
}
