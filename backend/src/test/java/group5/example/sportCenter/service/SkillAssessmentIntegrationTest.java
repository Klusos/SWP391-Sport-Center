package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import group5.example.sportCenter.dto.*;
import group5.example.sportCenter.entity.*;
import group5.example.sportCenter.repository.*;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class SkillAssessmentIntegrationTest {
    @Autowired SkillAssessmentService service;
    @Autowired MemberSportLevelService levelService;
    @Autowired UserRepository users;
    @Autowired SportRepository sports;
    @Autowired CoachRepository coaches;
    @Autowired CoachSportRepository qualifications;
    @Autowired ConsultationRequestRepository consultationRequests;
    @Autowired MemberSportLevelRepository levels;
    @MockitoSpyBean SkillAssessmentRepository assessments;
    @Autowired JwtService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    MockMvc mvc;
    User admin, receptionist, member, otherMember, coachUser, otherCoachUser;
    Coach coach, otherCoach;
    Sport sport;

    @BeforeEach
    void setup() {
        assessments.deleteAll(); consultationRequests.deleteAll(); levels.deleteAll();
        qualifications.deleteAll(); coaches.deleteAll(); sports.deleteAll(); users.deleteAll();
        admin = user("ADMIN"); receptionist = user("RECEPTIONIST"); member = user("MEMBER"); otherMember = user("MEMBER");
        coachUser = user("COACH"); otherCoachUser = user("COACH");
        coach = coach(coachUser); otherCoach = coach(otherCoachUser);
        sport = new Sport(); sport.setName("Tennis"); sport.setStatus("ACTIVE"); sport = sports.saveAndFlush(sport);
        CoachSport qualification = new CoachSport(); qualification.setCoach(coach); qualification.setSport(sport); qualification.setExperienceYears(1);
        qualifications.saveAndFlush(qualification);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }
    User user(String role) {
        User u = new User(); u.setName(role); u.setEmail(role + "-" + java.util.UUID.randomUUID() + "@example.test");
        u.setRole(role); u.setStatus("ACTIVE"); u.setPasswordHash("test-only-unused-hash"); return users.saveAndFlush(u);
    }
    Coach coach(User u) { Coach c = new Coach(); c.setUser(u); c.setStatus("ACTIVE"); return coaches.saveAndFlush(c); }
    Jwt jwt(User u) { return tokens.validateToken(tokens.generateToken(u)); }
    String bearer(User u) { return "Bearer " + tokens.generateToken(u); }
    CreateSkillAssessmentRequest request() { return new CreateSkillAssessmentRequest(member.getId(), sport.getId(), coach.getId(), "Initial notes"); }
    SkillAssessmentResponse assign() { return service.create(request(), jwt(admin)); }
    SkillAssessmentResponse complete(Integer id, String level) { return service.complete(id, new CompleteSkillAssessmentRequest(level, null), jwt(coachUser)); }
    void assertError(int expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation).getStatusCode().value());
    }

    @Test void staffCreatesPendingAndMissingLevelStaysAbsent() {
        assertTrue(levelService.mine(jwt(member)).isEmpty());
        SkillAssessmentResponse a = service.create(request(), jwt(receptionist));
        assertEquals("PENDING", a.status()); assertNull(a.assessedLevel()); assertNull(a.assessedAt()); assertNull(a.cancelledAt());
        assertEquals(receptionist.getId(), a.requestedByUserId()); assertNotNull(a.createdAt());
        assertEquals(1, service.all(jwt(admin)).size()); assertTrue(levelService.mine(jwt(member)).isEmpty());
    }
    @Test void missingResourcesAre404() {
        assertError(404, () -> service.create(new CreateSkillAssessmentRequest(Integer.MAX_VALUE, sport.getId(), coach.getId(), null), jwt(admin)));
        assertError(404, () -> service.create(new CreateSkillAssessmentRequest(member.getId(), Integer.MAX_VALUE, coach.getId(), null), jwt(admin)));
        assertError(404, () -> service.create(new CreateSkillAssessmentRequest(member.getId(), sport.getId(), Integer.MAX_VALUE, null), jwt(admin)));
        assertError(404, () -> service.get(Integer.MAX_VALUE, jwt(admin)));
        assertError(404, () -> service.cancel(Integer.MAX_VALUE, jwt(admin)));
        assertError(404, () -> complete(Integer.MAX_VALUE, "BEGINNER"));
    }
    @Test void invalidMemberAndInactiveMemberAreRejected() {
        assertError(409, () -> service.create(new CreateSkillAssessmentRequest(admin.getId(), sport.getId(), coach.getId(), null), jwt(admin)));
        member.setStatus("INACTIVE"); users.saveAndFlush(member);
        assertError(409, () -> assign());
    }
    @Test void inactiveSportRejected() {
        sport.setStatus("INACTIVE"); sports.saveAndFlush(sport); assertError(409, () -> assign());
    }
    @Test void inactiveCoachRejected() {
        coach.setStatus("INACTIVE"); coaches.saveAndFlush(coach); assertError(409, () -> assign());
    }
    @Test void invalidCoachAccountRejected() {
        coachUser.setRole("MEMBER"); users.saveAndFlush(coachUser); assertError(409, () -> assign());
        coachUser.setRole("COACH"); coachUser.setStatus("INACTIVE"); users.saveAndFlush(coachUser); assertError(409, () -> assign());
    }
    @Test void qualificationRequired() {
        assertError(409, () -> service.create(new CreateSkillAssessmentRequest(member.getId(), sport.getId(), otherCoach.getId(), null), jwt(admin)));
    }
    @Test void duplicatePendingRejected() { assign(); assertError(409, () -> assign()); assertEquals(1, assessments.count()); }
    @Test void assignedCoachCompletesCreatesLevelAndCanDowngradeAfterReassessment() {
        SkillAssessmentResponse first = complete(assign().id(), " beginner ");
        assertEquals("COMPLETED", first.status()); assertEquals("BEGINNER", first.assessedLevel()); assertNotNull(first.assessedAt());
        assertEquals("Initial notes", first.notes());
        MemberSportLevelResponse initial = levelService.mine(jwt(member)).getFirst();
        assertEquals("BEGINNER", initial.level()); assertEquals(coach.getId(), initial.verifiedByCoachId()); assertNotNull(initial.updatedAt());
        complete(assign().id(), "ADVANCED");
        assertEquals("ADVANCED", levelService.mine(jwt(member)).getFirst().level());
        complete(assign().id(), "INTERMEDIATE");
        MemberSportLevelResponse downgraded = levelService.mine(jwt(member)).getFirst();
        assertEquals("INTERMEDIATE", downgraded.level()); assertEquals(initial.id(), downgraded.id()); assertEquals(1, levels.count());
        assertEquals(3, service.mine(jwt(member)).size()); assertEquals(3, service.myAssignments(jwt(coachUser)).size());
    }
    @Test void wrongCoachCannotCompleteOrViewAndMembersCannotViewOthers() {
        Integer id = assign().id();
        assertError(403, () -> service.complete(id, new CompleteSkillAssessmentRequest("ADVANCED", null), jwt(otherCoachUser)));
        assertError(403, () -> service.get(id, jwt(otherCoachUser)));
        assertError(403, () -> service.get(id, jwt(otherMember)));
        assertEquals(id, service.get(id, jwt(coachUser)).id()); assertEquals(id, service.get(id, jwt(member)).id());
        assertEquals(id, service.get(id, jwt(receptionist)).id());
        assertTrue(service.myAssignments(jwt(otherCoachUser)).isEmpty()); assertTrue(service.mine(jwt(otherMember)).isEmpty());
        complete(id, "ADVANCED"); assertTrue(levelService.mine(jwt(otherMember)).isEmpty());
        assertEquals(1, levelService.forMember(member.getId(), jwt(receptionist)).size());
        assertError(403, () -> levelService.forMember(member.getId(), jwt(otherMember)));
    }
    @Test void assignedCoachReadsOnlyAssessmentScopedMemberSportLevel() throws Exception {
        Integer id = assign().id();
        mvc.perform(get("/api/skill-assessments/" + id + "/member-level").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessmentId").value(id))
                .andExpect(jsonPath("$.memberId").value(member.getId())).andExpect(jsonPath("$.sportId").value(sport.getId()))
                .andExpect(jsonPath("$.level").value("BEGINNER")).andExpect(jsonPath("$.defaultLevel").value(true))
                .andExpect(jsonPath("$.verifiedByCoachId").doesNotExist());
        assertEquals(0, levels.count(), "Reading the default level must not create a MemberSportLevel row");

        Sport unrelatedSport = new Sport(); unrelatedSport.setName("Swimming"); unrelatedSport.setStatus("ACTIVE");
        unrelatedSport = sports.saveAndFlush(unrelatedSport);
        MemberSportLevel unrelated = new MemberSportLevel(); unrelated.setMember(member); unrelated.setSport(unrelatedSport);
        unrelated.setLevel(SportLevel.ADVANCED); unrelated.setUpdatedAt(java.time.LocalDateTime.now());
        levels.saveAndFlush(unrelated);
        mvc.perform(get("/api/skill-assessments/" + id + "/member-level").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sportId").value(sport.getId()))
                .andExpect(jsonPath("$.level").value("BEGINNER")).andExpect(jsonPath("$.defaultLevel").value(true));
        MemberSportLevel relevant = new MemberSportLevel(); relevant.setMember(member); relevant.setSport(sport);
        relevant.setLevel(SportLevel.ADVANCED); relevant.setVerifiedByCoach(coach);
        relevant.setVerifiedAt(java.time.LocalDateTime.now()); relevant.setUpdatedAt(java.time.LocalDateTime.now());
        levels.saveAndFlush(relevant);
        mvc.perform(get("/api/skill-assessments/" + id + "/member-level").header("Authorization", bearer(coachUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.level").value("ADVANCED"))
                .andExpect(jsonPath("$.defaultLevel").value(false)).andExpect(jsonPath("$.verifiedByCoachId").value(coach.getId()));
        mvc.perform(get("/api/skill-assessments/" + id + "/member-level").header("Authorization", bearer(otherCoachUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/skill-assessments/" + Integer.MAX_VALUE + "/member-level").header("Authorization", bearer(coachUser)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/member-sport-levels/member/" + member.getId()).header("Authorization", bearer(coachUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/member-sport-levels/member/" + member.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/member-sport-levels/member/" + member.getId()).header("Authorization", bearer(receptionist)))
                .andExpect(status().isOk());
    }

    @Test void invalidLevelAndNotesAre400WithoutMutation() {
        Integer id = assign().id();
        for (String invalid : new String[] {null, "", "  ", "EXPERT"}) assertError(400, () -> complete(id, invalid));
        assertError(400, () -> service.complete(id, new CompleteSkillAssessmentRequest("BEGINNER", "x".repeat(1001)), jwt(coachUser)));
        assertError(400, () -> service.create(new CreateSkillAssessmentRequest(otherMember.getId(), sport.getId(), coach.getId(), "x".repeat(1001)), jwt(admin)));
        assertEquals("PENDING", service.get(id, jwt(admin)).status()); assertEquals(0, levels.count());
    }
    @Test void cancellationAndTerminalStatesAreEnforced() {
        Integer id = assign().id();
        SkillAssessmentResponse cancelled = service.cancel(id, jwt(receptionist));
        assertEquals("CANCELLED", cancelled.status()); assertNotNull(cancelled.cancelledAt()); assertNull(cancelled.assessedAt());
        assertError(409, () -> service.cancel(id, jwt(admin))); assertError(409, () -> complete(id, "BEGINNER"));
        assertEquals(0, levels.count());
        Integer completed = assign().id(); complete(completed, "BEGINNER");
        assertError(409, () -> service.cancel(completed, jwt(admin))); assertError(409, () -> complete(completed, "ADVANCED"));
        assertEquals("BEGINNER", levelService.mine(jwt(member)).getFirst().level());
    }
    @Test void failedAssessmentWriteRollsBackBothFirstInsertAndExistingLevelUpdate() {
        Integer first = assign().id();
        doThrow(new IllegalStateException("simulated assessment persistence failure")).when(assessments).saveAndFlush(any(SkillAssessment.class));
        try { assertThrows(IllegalStateException.class, () -> complete(first, "ADVANCED")); }
        finally { reset(assessments); }
        assertEquals(0, levels.count()); assertEquals("PENDING", service.get(first, jwt(admin)).status());
        complete(first, "BEGINNER"); Integer next = assign().id();
        doThrow(new IllegalStateException("simulated assessment persistence failure")).when(assessments).saveAndFlush(any(SkillAssessment.class));
        try { assertThrows(IllegalStateException.class, () -> complete(next, "ADVANCED")); }
        finally { reset(assessments); }
        assertEquals("BEGINNER", levelService.mine(jwt(member)).getFirst().level());
        assertEquals("PENDING", service.get(next, jwt(admin)).status());
    }
    @Test void concurrentCreationHasOneWinner() throws Exception {
        List<Integer> results = race(() -> { service.create(request(), jwt(admin)); return 201; },
                () -> { service.create(request(), jwt(receptionist)); return 201; });
        assertTrue(results.contains(201)); assertTrue(results.contains(409)); assertEquals(1, assessments.count());
    }
    @Test void concurrentCompletionCannotOverwriteWinner() throws Exception {
        Integer id = assign().id();
        List<Integer> results = race(() -> { complete(id, "ADVANCED"); return 200; }, () -> { complete(id, "BEGINNER"); return 200; });
        assertTrue(results.contains(200)); assertTrue(results.contains(409));
        assertEquals(service.get(id, jwt(admin)).assessedLevel(), levelService.mine(jwt(member)).getFirst().level()); assertEquals(1, levels.count());
    }
    @Test void concurrentCancelAndCompleteHaveOneTerminalWinner() throws Exception {
        Integer id = assign().id();
        List<Integer> results = race(() -> { service.cancel(id, jwt(admin)); return 200; }, () -> { complete(id, "ADVANCED"); return 200; });
        assertTrue(results.contains(200)); assertTrue(results.contains(409));
        boolean completed = "COMPLETED".equals(service.get(id, jwt(admin)).status());
        assertEquals(completed ? 1 : 0, levels.count());
    }
    List<Integer> race(Supplier<Integer> first, Supplier<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> attempt(first, ready, start)); var b = pool.submit(() -> attempt(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    int attempt(Supplier<Integer> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
        try { return operation.get(); } catch (ResponseStatusException e) { return e.getStatusCode().value(); }
    }

    @Test void httpRoleMatrixUsesRealJwtAndOwnershipChecks() throws Exception {
        Integer id = assign().id();
        mvc.perform(get("/api/skill-assessments/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/skill-assessments/me").header("Authorization", bearer(otherMember)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/member-sport-levels/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertEquals(0, levels.count(), "Member read APIs must not create default level records");
        for (User u : List.of(admin, receptionist, member, coachUser)) {
            boolean staff = u == admin || u == receptionist;
            mvc.perform(get("/api/skill-assessments").header("Authorization", bearer(u))).andExpect(status().is(staff ? 200 : 403));
            mvc.perform(get("/api/skill-assessments/me").header("Authorization", bearer(u))).andExpect(status().is(u == member ? 200 : 403));
            mvc.perform(get("/api/skill-assessments/my-assignments").header("Authorization", bearer(u))).andExpect(status().is(u == coachUser ? 200 : 403));
            mvc.perform(get("/api/member-sport-levels/me").header("Authorization", bearer(u))).andExpect(status().is(u == member ? 200 : 403));
            mvc.perform(get("/api/member-sport-levels/member/" + member.getId()).header("Authorization", bearer(u))).andExpect(status().is(staff ? 200 : 403));
            mvc.perform(get("/api/skill-assessments/" + id).header("Authorization", bearer(u))).andExpect(status().isOk());
            if (!staff) {
                mvc.perform(post("/api/skill-assessments").header("Authorization", bearer(u)).contentType("application/json").content(createJson())).andExpect(status().isForbidden());
                mvc.perform(patch("/api/skill-assessments/" + id + "/cancel").header("Authorization", bearer(u))).andExpect(status().isForbidden());
            }
            if (u != coachUser) mvc.perform(patch("/api/skill-assessments/" + id + "/complete").header("Authorization", bearer(u))
                    .contentType("application/json").content("{\"assessedLevel\":\"ADVANCED\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/skill-assessments/" + id).header("Authorization", bearer(otherMember))).andExpect(status().isForbidden());
        mvc.perform(get("/api/skill-assessments/" + id).header("Authorization", bearer(otherCoachUser))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/skill-assessments/" + id + "/complete").header("Authorization", bearer(otherCoachUser))
                .contentType("application/json").content("{\"assessedLevel\":\"ADVANCED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/member-sport-levels/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/skill-assessments")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/member-sport-levels/me").header("Authorization", bearer(coachUser))).andExpect(status().isForbidden());
    }
    @Test void httpCreationValidationAndCompletionResponse() throws Exception {
        mvc.perform(post("/api/skill-assessments").header("Authorization", bearer(admin)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/skill-assessments").header("Authorization", bearer(admin)).contentType("application/json").content(createJson()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        Integer id = service.all(jwt(admin)).getFirst().id();
        mvc.perform(patch("/api/skill-assessments/" + id + "/complete").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"assessedLevel\":\"expert\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/skill-assessments/" + id + "/complete").header("Authorization", bearer(coachUser))
                .contentType("application/json").content("{\"assessedLevel\":\"intermediate\",\"notes\":\"Đánh giá kỹ năng: cầu lông\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.assessedLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.notes").value("Đánh giá kỹ năng: cầu lông"));
        assertEquals("Đánh giá kỹ năng: cầu lông", service.get(id, jwt(member)).notes());
        mvc.perform(get("/api/member-sport-levels/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].level").value("INTERMEDIATE"));
    }

    @Test void receptionistCannotCompleteAssessmentAndCoachWorkflowRemainsPending() throws Exception {
        SkillAssessmentResponse pending = service.create(request(), jwt(receptionist));
        mvc.perform(patch("/api/skill-assessments/" + pending.id() + "/complete")
                .header("Authorization", bearer(receptionist)).contentType("application/json")
                .content("{\"assessedLevel\":\"INTERMEDIATE\",\"notes\":\"Receptionist-entered screening result\"}"))
                .andExpect(status().isForbidden());
        assertEquals("PENDING", service.get(pending.id(), jwt(admin)).status());
        assertEquals(0, levels.count());
        assertEquals("BEGINNER", complete(pending.id(), "BEGINNER").assessedLevel());
        assertEquals(coach.getId(), levels.findByMemberIdAndSportId(member.getId(), sport.getId())
                .orElseThrow().getVerifiedByCoach().getId());
    }
    @Test void invalidIdentityAndInactiveCallerRejected() {
        Jwt malformed = Jwt.withTokenValue("test").header("alg", "none").subject("not-an-id").build();
        assertError(401, () -> service.all(malformed));
        admin.setStatus("INACTIVE"); users.saveAndFlush(admin); assertError(403, () -> service.all(jwt(admin)));
        assertError(403, () -> service.myAssignments(jwt(user("COACH"))));
    }
    String createJson() {
        return "{\"memberId\":" + member.getId() + ",\"sportId\":" + sport.getId() + ",\"assignedCoachId\":" + coach.getId() + "}";
    }
}
