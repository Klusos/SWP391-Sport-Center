package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.Notification;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("phase1")
class NotificationBroadcastIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired NotificationRepository notifications;
    @MockitoSpyBean UserRepository users;
    @Autowired JwtService tokens;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        notifications.deleteAllInBatch();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void adminBroadcastsOneUnreadPersonalNotificationToEveryActiveRoleIncludingSelf() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        User coach = user("COACH", "ACTIVE");
        User member = user("MEMBER", "ACTIVE");
        user("MEMBER", "INACTIVE");
        int expectedRecipients = users.findEligibleActiveUserIds().size();
        String body = "{\"title\":\"Center notice\",\"message\":\"Thông báo: lịch bảo trì đã được cập nhật.\"}";

        mvc.perform(post("/api/admin/notifications/broadcast")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCount").value(expectedRecipients));

        List<Notification> saved = notifications.findAll();
        assertEquals(expectedRecipients, saved.size());
        Map<Integer, Long> byOwner = saved.stream().collect(Collectors.groupingBy(
                notification -> notification.getUser().getId(), Collectors.counting()));
        assertEquals(expectedRecipients, byOwner.size());
        assertEquals(1L, byOwner.get(admin.getId()));
        assertEquals(1L, byOwner.get(receptionist.getId()));
        assertEquals(1L, byOwner.get(coach.getId()));
        assertEquals(1L, byOwner.get(member.getId()));
        assertEquals(expectedRecipients, saved.stream().filter(notification -> !notification.isRead()).count());
        assertEquals(expectedRecipients, saved.stream()
                .filter(notification -> "Thông báo: lịch bảo trì đã được cập nhật.".equals(notification.getMessage()))
                .count());

        mvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Center notice"))
                .andExpect(jsonPath("$.content[0].isRead").value(false));
    }

    @Test
    void onlyAdminCanBroadcastAndAnonymousCallIsRejected() throws Exception {
        for (String role : List.of("RECEPTIONIST", "COACH", "MEMBER")) {
            User user = user(role, "ACTIVE");
            mvc.perform(post("/api/admin/notifications/broadcast")
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Notice\",\"message\":\"Details\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Notice\",\"message\":\"Details\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, notifications.count());
    }

    @Test
    void invalidBroadcastBodiesReturnBadRequestWithoutCreatingNotifications() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        String[] invalidBodies = {
                "{\"title\":\"   \",\"message\":\"Text\"}",
                "{\"title\":\"" + "x".repeat(151) + "\",\"message\":\"Text\"}",
                "{\"title\":\"Notice\",\"message\":\"   \"}",
                "{\"title\":\"Notice\",\"message\":\"" + "x".repeat(5001) + "\"}"
        };
        for (String invalidBody : invalidBodies) {
            mvc.perform(post("/api/admin/notifications/broadcast")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                    .andExpect(status().isBadRequest());
            assertEquals(0, notifications.count());
        }
    }

    @Test
    void persistenceFailureRollsBackEarlierBatchRows() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        doReturn(List.of(admin.getId(), Integer.MAX_VALUE)).when(users).findEligibleActiveUserIds();

        mvc.perform(post("/api/admin/notifications/broadcast")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Atomic notice\",\"message\":\"All or none\"}"))
                .andExpect(status().isConflict());

        assertEquals(0, notifications.count(), "The valid first insert must roll back with the invalid second FK");
    }

    @Test
    void unknownRequestFieldsCannotControlRecipientsOrReadState() throws Exception {
        User admin = user("ADMIN", "ACTIVE");
        user("MEMBER", "ACTIVE");
        int expectedRecipients = users.findEligibleActiveUserIds().size();

        mvc.perform(post("/api/admin/notifications/broadcast")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Notice\",\"message\":\"All active\","
                                + "\"userId\":999999,\"recipientIds\":[999999],\"targetRole\":\"MEMBER\","
                                + "\"senderId\":999999,\"isRead\":true,\"createdAt\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCount").value(expectedRecipients));
        assertEquals(expectedRecipients, notifications.count());
        assertEquals(expectedRecipients, notifications.findAll().stream()
                .filter(notification -> !notification.isRead()).count());
    }

    private User user(String role, String status) {
        User user = new User();
        user.setName("Broadcast " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus(status);
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }
}
