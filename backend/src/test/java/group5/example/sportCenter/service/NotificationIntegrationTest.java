package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("phase1")
class NotificationIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired JwtService tokens;
    @Autowired NotificationService notificationService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        notifications.deleteAllInBatch();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void everySupportedRoleCanReadOnlyItsOwnNotifications() throws Exception {
        for (String role : new String[] { "ADMIN", "RECEPTIONIST", "COACH", "MEMBER" }) {
            User owner = user(role);
            User other = user("MEMBER");
            notificationService.createForUser(owner.getId(), role + " notice", "Private " + role + " message");
            notificationService.createForUser(other.getId(), "Other notice", "Must not leak");

            mvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value(role + " notice"))
                    .andExpect(jsonPath("$.content[0].message").value("Private " + role + " message"))
                    .andExpect(jsonPath("$.content[0].isRead").value(false))
                    .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.content[0].userId").doesNotExist());
        }
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificationFromAnotherOwnerIsNotReadableOrMutable() throws Exception {
        User owner = user("MEMBER");
        User other = user("MEMBER");
        var created = notificationService.createForUser(owner.getId(), "Private", "Only the owner sees this");

        mvc.perform(patch("/api/notifications/{id}/read", created.id())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        assertEquals(false, notifications.findById(created.id()).orElseThrow().isRead());

        mvc.perform(get("/api/notifications").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void unreadCountAndSingleReadAreCorrectAndIdempotent() throws Exception {
        User owner = user("MEMBER");
        var first = notificationService.createForUser(owner.getId(), "First", "One");
        var second = notificationService.createForUser(owner.getId(), "Second", "Two");
        String authorization = bearer(owner);

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(patch("/api/notifications/{id}/read", first.id()).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.id()))
                .andExpect(jsonPath("$.isRead").value(true));
        mvc.perform(patch("/api/notifications/{id}/read", first.id()).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
        assertEquals(false, notifications.findById(second.id()).orElseThrow().isRead());
    }

    @Test
    void markAllReadUpdatesOnlyTheCurrentOwnerAndIsIdempotent() throws Exception {
        User owner = user("RECEPTIONIST");
        User other = user("ADMIN");
        notificationService.createForUser(owner.getId(), "One", "1");
        notificationService.createForUser(owner.getId(), "Two", "2");
        notificationService.createForUser(other.getId(), "Other", "3");

        mvc.perform(patch("/api/notifications/read-all").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));
        mvc.perform(patch("/api/notifications/read-all").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));
        assertEquals(1, notifications.countByUserIdAndReadFalse(other.getId()));
    }

    @Test
    void listIsPagedNewestFirstAndEmptyPagesAreValid() throws Exception {
        User owner = user("COACH");
        notificationService.createForUser(owner.getId(), "Older", "first");
        notificationService.createForUser(owner.getId(), "Newer", "second");
        String authorization = bearer(owner);

        mvc.perform(get("/api/notifications?page=0&size=1").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Newer"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(1));
        mvc.perform(get("/api/notifications?page=1&size=1").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Older"));

        User empty = user("MEMBER");
        mvc.perform(get("/api/notifications").header("Authorization", bearer(empty)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownIdAndInvalidPaginationAreRejected() throws Exception {
        User owner = user("MEMBER");
        String authorization = bearer(owner);
        mvc.perform(patch("/api/notifications/{id}/read", 999999).header("Authorization", authorization))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications?size=0").header("Authorization", authorization))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications?size=101").header("Authorization", authorization))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalNotificationCreationValidatesRequiredFieldsAndOwner() {
        User owner = user("ADMIN");
        assertThrows(IllegalArgumentException.class,
                () -> notificationService.createForUser(owner.getId(), " ", "message"));
        assertThrows(IllegalArgumentException.class,
                () -> notificationService.createForUser(owner.getId(), "x".repeat(151), "message"));
        assertThrows(IllegalArgumentException.class,
                () -> notificationService.createForUser(owner.getId(), "title", " "));
        assertThrows(ResourceNotFoundException.class,
                () -> notificationService.createForUser(Integer.MAX_VALUE, "title", "message"));
    }

    private User user(String role) {
        User user = new User();
        user.setName("Notification " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + tokens.generateToken(user);
    }
}
