package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;

import group5.example.sportCenter.dto.CancelConsultationRequest;
import group5.example.sportCenter.dto.CompleteConsultationRequest;
import group5.example.sportCenter.dto.ConsultationResponse;
import group5.example.sportCenter.dto.CreateConsultationMemberRequest;
import group5.example.sportCenter.dto.CreateConsultationRequest;
import group5.example.sportCenter.dto.CreateSkillAssessmentRequest;
import group5.example.sportCenter.dto.LinkConsultationMemberRequest;
import group5.example.sportCenter.entity.ConsultationRequest;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.CoachSport;
import group5.example.sportCenter.entity.MemberSportLevel;
import group5.example.sportCenter.entity.SkillAssessment;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.CoachSportRepository;
import group5.example.sportCenter.repository.ConsultationRequestRepository;
import group5.example.sportCenter.repository.MemberSportLevelRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.SkillAssessmentRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class ConsultationRequestIntegrationTest {

    @Autowired ConsultationRequestService service;
    @Autowired SkillAssessmentService skillAssessments;
    @MockitoSpyBean ConsultationRequestRepository consultations;
    @MockitoSpyBean SkillAssessmentRepository assessments;
    @Autowired MemberSportLevelRepository levels;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired SportRepository sports;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository qualifications;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired DataSource dataSource;

    private MockMvc mvc;
    private User admin;
    private User receptionist;
    private User otherReceptionist;
    private User member;
    private User otherMember;
    private User coachUser;
    private Coach coach;
    private Sport sport;

    @BeforeEach
    void setUp() {
        notifications.deleteAllInBatch();
        assessments.deleteAll();
        consultations.deleteAll();
        levels.deleteAll();
        qualifications.deleteAll();
        coaches.deleteAll();
        sports.deleteAll();
        users.deleteAll();

        admin = user("ADMIN");
        receptionist = user("RECEPTIONIST");
        otherReceptionist = user("RECEPTIONIST");
        member = user("MEMBER");
        otherMember = user("MEMBER");
        coachUser = user("COACH");
        coach = new Coach();
        coach.setUser(coachUser);
        coach.setStatus("ACTIVE");
        coach = coaches.saveAndFlush(coach);

        sport = new Sport();
        sport.setName("Tennis");
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);

        CoachSport qualification = new CoachSport();
        qualification.setCoach(coach);
        qualification.setSport(sport);
        qualification.setExperienceYears(2);
        qualifications.saveAndFlush(qualification);

        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void integrationTestsUseOnlyTheIsolatedInMemoryH2Database() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            assertTrue(jdbcUrl.startsWith("jdbc:h2:mem:phase1"),
                    "Phase 1.5 integration tests must use the isolated H2 database, got: " + jdbcUrl);
        }
    }

    @Test
    void guestAndMemberSubmissionsLinkOnlyTheAuthenticatedMember() {
        ConsultationResponse guest = service.create(request(uniqueEmail(), null), null);
        assertNull(guest.memberId());
        assertEquals("PENDING", guest.status());

        ConsultationResponse submittedByMember = service.create(request(uniqueEmail(), 99), jwt(member));
        assertEquals(member.getId(), submittedByMember.memberId());
        assertNotEquals(otherMember.getId(), submittedByMember.memberId());
        assertEquals("PENDING", submittedByMember.status());

        assertStatus(403, () -> service.create(request(uniqueEmail(), null), jwt(coachUser)));
        assertStatus(403, () -> service.create(request(uniqueEmail(), null), jwt(receptionist)));
    }

    @Test
    void pendingRequestCanBeTakenThenCompletedByItsHandler() {
        ConsultationResponse created = service.create(request(uniqueEmail(), null), null);

        ConsultationResponse taken = service.take(created.id(), jwt(receptionist));
        assertEquals("IN_PROGRESS", taken.status());
        assertEquals(receptionist.getId(), taken.handledBy());
        assertNotNull(taken.handledAt());

        ConsultationResponse completed = service.complete(created.id(),
                new CompleteConsultationRequest("Consultation completed; course registration remains separate"), jwt(receptionist));
        assertEquals("COMPLETED", completed.status());
        assertEquals("Consultation completed; course registration remains separate", completed.resolutionNote());
        assertNotNull(completed.completedAt());
        assertEquals(0, assessments.count(), "Completing the consultation conversation must not require or create an assessment");
        assertEquals(0, levels.count(), "Consultation completion must not create a member sport level");
    }

    @Test
    void pendingRequestCanBeTakenThenCancelledWithoutChangingPhaseOneRecords() {
        ConsultationResponse created = service.create(request(uniqueEmail(), null), null);
        service.take(created.id(), jwt(receptionist));

        ConsultationResponse cancelled = service.cancel(created.id(),
                new CancelConsultationRequest("Member no longer wants this consultation"), jwt(receptionist));
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("Member no longer wants this consultation", cancelled.resolutionNote());
        assertNotNull(cancelled.cancelledAt());
        assertEquals(0, assessments.count());
        assertEquals(0, levels.count());
    }

    @Test
    void terminalStatesCannotTransitionAndOtherReceptionistsCannotModifyAssignedWork() {
        ConsultationResponse first = service.create(request(uniqueEmail(), null), null);
        assertStatus(409, () -> service.complete(first.id(), new CompleteConsultationRequest("Done"), jwt(admin)));
        service.take(first.id(), jwt(receptionist));
        assertStatus(403, () -> service.complete(first.id(), new CompleteConsultationRequest("No"), jwt(otherReceptionist)));
        ConsultationResponse completed = service.complete(first.id(), new CompleteConsultationRequest(null), jwt(receptionist));
        assertStatus(409, () -> service.cancel(completed.id(), new CancelConsultationRequest("Too late"), jwt(receptionist)));
        assertStatus(409, () -> service.complete(completed.id(), new CompleteConsultationRequest("Again"), jwt(admin)));

        ConsultationResponse second = service.create(request(uniqueEmail(), null), null);
        service.take(second.id(), jwt(admin));
        ConsultationResponse cancelled = service.cancel(second.id(), new CancelConsultationRequest("Closed"), jwt(admin));
        assertStatus(409, () -> service.complete(cancelled.id(), new CompleteConsultationRequest(null), jwt(admin)));
    }

    @Test
    void adminCanOverrideAssignedHandlerForInProgressCompletionAndCancellation() {
        ConsultationResponse completeRequest = service.create(request(uniqueEmail(), null), null);
        service.take(completeRequest.id(), jwt(receptionist));
        ConsultationResponse completed = service.complete(completeRequest.id(),
                new CompleteConsultationRequest("ADMIN override"), jwt(admin));
        assertEquals("COMPLETED", completed.status());
        assertEquals("ADMIN override", completed.resolutionNote());

        ConsultationResponse cancelRequest = service.create(request(uniqueEmail(), null), null);
        service.take(cancelRequest.id(), jwt(receptionist));
        ConsultationResponse cancelled = service.cancel(cancelRequest.id(),
                new CancelConsultationRequest("ADMIN override"), jwt(admin));
        assertEquals("CANCELLED", cancelled.status());
    }

    @Test
    void memberAndCoachCannotReadStaffQueueOrAnotherMembersRequest() {
        ConsultationResponse own = service.create(request(uniqueEmail(), null), jwt(member));
        ConsultationResponse another = service.create(request(uniqueEmail(), null), jwt(otherMember));
        assertEquals(own.id(), service.get(own.id(), jwt(member)).id());
        assertStatus(403, () -> service.get(another.id(), jwt(member)));
        assertStatus(403, () -> service.get(own.id(), jwt(coachUser)));
        assertStatus(403, () -> service.take(own.id(), jwt(member)));
        assertStatus(403, () -> service.all(null, 0, 20, jwt(member)));
        assertStatus(403, () -> service.all(null, 0, 20, jwt(coachUser)));
        assertEquals(2, service.all("PENDING", 0, 20, jwt(receptionist)).getTotalElements());
        assertStatus(400, () -> service.all("UNKNOWN", 0, 20, jwt(admin)));
    }

    @Test
    void concurrentTakeRequestsHaveExactlyOneWinner() throws Exception {
        ConsultationResponse pending = service.create(request(uniqueEmail(), null), null);
        List<Integer> results = race(
                () -> takeResult(pending.id(), receptionist),
                () -> takeResult(pending.id(), otherReceptionist));
        assertTrue(results.contains(200));
        assertTrue(results.contains(409));
        assertEquals("IN_PROGRESS", service.get(pending.id(), jwt(admin)).status());
    }

    @Test
    void assignedHandlerCreatesMemberAndLinksInOneOperationWithoutReturningCredentials() {
        String email = uniqueEmail();
        ConsultationResponse pending = service.create(request(email, null), null);
        service.take(pending.id(), jwt(receptionist));

        ConsultationResponse linked = service.createMember(pending.id(),
                new CreateConsultationMemberRequest("temporary-test-password", LocalDate.of(1998, 5, 1)), jwt(receptionist));

        User created = users.findByEmail(email).orElseThrow();
        assertEquals("MEMBER", created.getRole());
        assertTrue(passwordEncoder.matches("temporary-test-password", created.getPasswordHash()));
        assertEquals(created.getId(), linked.memberId());
        assertFalse(linked.toString().contains("temporary-test-password"));
        assertFalse(linked.toString().contains(created.getPasswordHash()));
        assertEquals(1, consultations.count());
    }

    @Test
    void memberCreationFailureLeavesConsultationUnlinkedAndExistingEmailConflicts() {
        String email = uniqueEmail();
        ConsultationResponse pending = service.create(request(email, null), null);
        service.take(pending.id(), jwt(receptionist));
        users.saveAndFlush(userWithEmail(email));

        assertStatus(409, () -> service.createMember(pending.id(),
                new CreateConsultationMemberRequest("temporary-test-password", LocalDate.of(1998, 5, 1)), jwt(receptionist)));
        assertNull(service.get(pending.id(), jwt(receptionist)).memberId());
    }

    @Test
    void linkingFailureRollsBackTheNewMemberAndConsultationLink() {
        String email = uniqueEmail();
        ConsultationResponse pending = service.create(request(email, null), null);
        service.take(pending.id(), jwt(receptionist));
        doThrow(new DataIntegrityViolationException("simulated link write failure"))
                .when(consultations).saveAndFlush(any(ConsultationRequest.class));

        assertStatus(409, () -> service.createMember(pending.id(),
                new CreateConsultationMemberRequest("temporary-test-password", LocalDate.of(1998, 5, 1)), jwt(receptionist)));

        org.mockito.Mockito.reset(consultations);
        assertTrue(users.findByEmail(email).isEmpty());
        assertNull(consultations.findById(pending.id()).orElseThrow().getMember());
    }

    @Test
    void linksOnlyAnExplicitExistingActiveMemberAndRejectsDuplicateOrInvalidTargets() {
        ConsultationResponse pending = service.create(request(uniqueEmail(), null), null);
        service.take(pending.id(), jwt(receptionist));
        ConsultationResponse linked = service.linkMember(pending.id(),
                new LinkConsultationMemberRequest(member.getId()), jwt(receptionist));
        assertEquals(member.getId(), linked.memberId());
        assertEquals(member.getId(), service.linkMember(pending.id(),
                new LinkConsultationMemberRequest(member.getId()), jwt(receptionist)).memberId());
        assertStatus(409, () -> service.linkMember(pending.id(),
                new LinkConsultationMemberRequest(otherMember.getId()), jwt(receptionist)));

        User inactive = user("MEMBER");
        inactive.setStatus("INACTIVE");
        users.saveAndFlush(inactive);
        ConsultationResponse second = service.create(request(uniqueEmail(), null), null);
        service.take(second.id(), jwt(receptionist));
        assertStatus(409, () -> service.linkMember(second.id(), new LinkConsultationMemberRequest(inactive.getId()), jwt(receptionist)));
        assertStatus(404, () -> service.linkMember(second.id(), new LinkConsultationMemberRequest(Integer.MAX_VALUE), jwt(receptionist)));
        assertStatus(403, () -> service.linkMember(second.id(), new LinkConsultationMemberRequest(member.getId()), jwt(otherReceptionist)));
    }

    @Test
    void skillAssessmentCanLinkToInProgressConsultationAndDoesNotCompleteIt() {
        ConsultationResponse pending = service.create(request(uniqueEmail(), null), null);
        service.take(pending.id(), jwt(receptionist));
        ConsultationResponse linked = service.linkMember(pending.id(),
                new LinkConsultationMemberRequest(member.getId()), jwt(receptionist));

        var created = skillAssessments.create(new CreateSkillAssessmentRequest(
                member.getId(), sport.getId(), coach.getId(), "Intake", linked.id()), jwt(receptionist));
        assertEquals(linked.id(), created.consultationRequestId());
        skillAssessments.complete(created.id(), new group5.example.sportCenter.dto.CompleteSkillAssessmentRequest("BEGINNER", null), jwt(coachUser));
        assertEquals("IN_PROGRESS", service.get(linked.id(), jwt(receptionist)).status());
        assertEquals("COMPLETED", skillAssessments.get(created.id(), jwt(admin)).status());
    }

    @Test
    void skillAssessmentConsultationMustMatchMemberSportAndRemainInProgress() {
        ConsultationResponse pending = service.create(request(uniqueEmail(), null), null);
        service.take(pending.id(), jwt(receptionist));
        service.linkMember(pending.id(), new LinkConsultationMemberRequest(member.getId()), jwt(receptionist));

        assertStatus(409, () -> skillAssessments.create(new CreateSkillAssessmentRequest(
                otherMember.getId(), sport.getId(), coach.getId(), null, pending.id()), jwt(receptionist)));
        Sport secondSport = new Sport();
        secondSport.setName("Badminton");
        secondSport.setStatus("ACTIVE");
        final Sport anotherSport = sports.saveAndFlush(secondSport);
        assertStatus(409, () -> skillAssessments.create(new CreateSkillAssessmentRequest(
                member.getId(), anotherSport.getId(), coach.getId(), null, pending.id()), jwt(receptionist)));
        service.complete(pending.id(), new CompleteConsultationRequest(null), jwt(receptionist));
        assertStatus(409, () -> skillAssessments.create(new CreateSkillAssessmentRequest(
                member.getId(), sport.getId(), coach.getId(), null, pending.id()), jwt(receptionist)));

        var standalone = skillAssessments.create(new CreateSkillAssessmentRequest(
                member.getId(), sport.getId(), coach.getId(), "Standalone"), jwt(receptionist));
        assertNull(standalone.consultationRequestId());
    }

    @Test
    void httpSecurityAllowsPublicOrMemberSubmissionAndRestrictsConsultationRoutes() throws Exception {
        String body = "{\"name\":\"Guest\",\"phone\":\"0900000000\",\"email\":\"guest-"
                + UUID.randomUUID() + "@example.test\",\"sportId\":" + sport.getId() + ",\"age\":25}";
        mvc.perform(post("/api/consultation-requests").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.memberId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/consultation-requests").header("Authorization", bearer(member))
                        .contentType("application/json").content(body.replace("Guest", "Member")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.memberId").value(member.getId()));
        mvc.perform(post("/api/consultation-requests").header("Authorization", bearer(coachUser))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests").header("Authorization", bearer(coachUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests").header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/consultation-requests/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/consultation-requests/me").header("Authorization", bearer(receptionist)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/consultation-requests").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void httpCreationCoversAgeEmailLengthSportAndJwtIdentityValidation() throws Exception {
        for (int age : List.of(1, 120)) {
            String valid = "{\"name\":\"Guest\",\"phone\":\"0900000000\",\"email\":\""
                    + uniqueEmail() + "\",\"sportId\":" + sport.getId() + ",\"age\":" + age + "}";
            mvc.perform(post("/api/consultation-requests").contentType("application/json").content(valid))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.age").value(age));
        }

        String base = "\"name\":\"Guest\",\"phone\":\"0900000000\",\"sportId\":" + sport.getId();
        for (String invalid : List.of(
                "{" + base + ",\"age\":0}",
                "{" + base + ",\"age\":121}",
                "{" + base + ",\"email\":\"not-an-email\"}",
                "{" + base + ",\"name\":\"\"}",
                "{" + base + ",\"phone\":\"\"}",
                "{\"name\":\"Guest\",\"phone\":\"" + "1".repeat(21) + "\",\"sportId\":" + sport.getId() + "}",
                "{\"name\":\"Guest\",\"phone\":\"0900000000\",\"email\":\""
                        + "a".repeat(145) + "@x.com\",\"sportId\":" + sport.getId() + "}",
                "{" + base + ",\"name\":\"" + "N".repeat(101) + "\"}")) {
            mvc.perform(post("/api/consultation-requests").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }

        String missingSport = "{\"name\":\"Guest\",\"phone\":\"0900000000\",\"sportId\":"
                + Integer.MAX_VALUE + "}";
        mvc.perform(post("/api/consultation-requests").contentType("application/json").content(missingSport))
                .andExpect(status().isNotFound());

        Sport inactive = new Sport();
        inactive.setName("Inactive-" + UUID.randomUUID());
        inactive.setStatus("INACTIVE");
        inactive = sports.saveAndFlush(inactive);
        String inactiveSport = "{\"name\":\"Guest\",\"phone\":\"0900000000\",\"sportId\":"
                + inactive.getId() + "}";
        mvc.perform(post("/api/consultation-requests").contentType("application/json").content(inactiveSport))
                .andExpect(status().isConflict());

        String memberSubmission = "{\"name\":\"Member\",\"phone\":\"0900000000\",\"sportId\":"
                + sport.getId() + ",\"memberId\":" + otherMember.getId() + "}";
        mvc.perform(post("/api/consultation-requests").header("Authorization", bearer(member))
                        .contentType("application/json").content(memberSubmission))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.memberId").value(member.getId()));
    }

    @Test
    void httpListPaginationFilteringAndDetailOwnershipAreEnforced() throws Exception {
        ConsultationResponse pendingA = service.create(request(uniqueEmail(), null), null);
        ConsultationResponse pendingB = service.create(request(uniqueEmail(), null), jwt(member));
        ConsultationResponse inProgress = service.create(request(uniqueEmail(), null), null);
        service.take(inProgress.id(), jwt(receptionist));

        mvc.perform(get("/api/consultation-requests?status=PENDING&page=0&size=1")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/consultation-requests?status=IN_PROGRESS")
                        .header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/consultation-requests?status=NOT_A_STATUS")
                        .header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/consultation-requests?page=-1")
                        .header("Authorization", bearer(admin))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/consultation-requests?size=101")
                        .header("Authorization", bearer(admin))).andExpect(status().isBadRequest());

        mvc.perform(get("/api/consultation-requests/" + pendingA.id())
                        .header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/consultation-requests/" + inProgress.id())
                        .header("Authorization", bearer(receptionist))).andExpect(status().isOk());
        mvc.perform(get("/api/consultation-requests/" + inProgress.id())
                        .header("Authorization", bearer(otherReceptionist))).andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests/" + pendingB.id())
                        .header("Authorization", bearer(member))).andExpect(status().isOk());
        mvc.perform(get("/api/consultation-requests/" + pendingA.id())
                        .header("Authorization", bearer(member))).andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests/" + pendingA.id())
                        .header("Authorization", bearer(coachUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/consultation-requests/" + Integer.MAX_VALUE)
                        .header("Authorization", bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/consultation-requests/" + pendingA.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void httpCreateMemberRequiresAssignedStaffInProgressAndValidCredentials() throws Exception {
        ConsultationResponse pending = service.create(request(uniqueEmail(), null), null);
        String path = "/api/consultation-requests/" + pending.id() + "/create-member";
        String validBody = "{\"password\":\"" + UUID.randomUUID() + "Aa1!\",\"dateOfBirth\":\"1990-05-01\"}";

        mvc.perform(post(path).header("Authorization", bearer(admin)).contentType("application/json")
                        .content(validBody)).andExpect(status().isConflict());
        mvc.perform(post(path).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content(validBody))
                .andExpect(status().isForbidden());

        service.take(pending.id(), jwt(receptionist));
        mvc.perform(post(path).header("Authorization", bearer(otherReceptionist)).contentType("application/json")
                        .content(validBody)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(member)).contentType("application/json")
                        .content(validBody)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(coachUser)).contentType("application/json")
                        .content(validBody)).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"password\":\"short\",\"dateOfBirth\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest());

        var result = mvc.perform(post(path).header("Authorization", bearer(receptionist))
                        .contentType("application/json").content(validBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").isNumber())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")).andReturn();
        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains(validBody));
        assertFalse(responseBody.contains("password"));
    }

    @Test
    void httpLinkMemberRequiresAssignmentAndAcceptsOnlyActiveMembers() throws Exception {
        ConsultationResponse pending = service.create(request(uniqueEmail(), null), null);
        String path = "/api/consultation-requests/" + pending.id() + "/link-member";
        String body = "{\"memberId\":" + member.getId() + "}";
        mvc.perform(patch(path).header("Authorization", bearer(admin)).contentType("application/json").content(body))
                .andExpect(status().isConflict());

        service.take(pending.id(), jwt(receptionist));
        mvc.perform(patch(path).header("Authorization", bearer(otherReceptionist)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).header("Authorization", bearer(coachUser)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).header("Authorization", bearer(member)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"memberId\":0}"))
                .andExpect(status().isBadRequest());

        var linked = mvc.perform(patch(path).header("Authorization", bearer(receptionist))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberId").value(member.getId())).andReturn();
        assertTrue(linked.getResponse().getContentAsString().contains("IN_PROGRESS"));
        mvc.perform(patch(path).header("Authorization", bearer(receptionist)).contentType("application/json")
                        .content("{\"memberId\":" + otherMember.getId() + "}"))
                .andExpect(status().isConflict());
    }

    private User user(String role) {
        User user = new User();
        user.setName(role + " user");
        user.setEmail(role + "-" + UUID.randomUUID() + "@example.test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setPasswordHash("test-only-unused-hash");
        return users.saveAndFlush(user);
    }

    private User userWithEmail(String email) {
        User user = new User();
        user.setName("Test user");
        user.setEmail(email);
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setPasswordHash("test-only-unused-hash");
        return users.saveAndFlush(user);
    }

    private CreateConsultationRequest request(String email, Integer age) {
        return new CreateConsultationRequest("Nguyễn An", "0900000000", email,
                sport.getId(), age, "Kinh nghiệm cơ bản", "Muốn được tư vấn");
    }

    private String uniqueEmail() {
        return "consult-" + UUID.randomUUID() + "@example.test";
    }

    private Jwt jwt(User user) {
        return tokens.validateToken(tokens.generateToken(user));
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }

    private void assertStatus(int expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation).getStatusCode().value());
    }

    private int takeResult(Integer id, User handler) {
        try {
            service.take(id, jwt(handler));
            return 200;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private List<Integer> race(Supplier<Integer> first, Supplier<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> attempt(first, ready, start));
            var b = pool.submit(() -> attempt(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private int attempt(Supplier<Integer> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        return operation.get();
    }
}
