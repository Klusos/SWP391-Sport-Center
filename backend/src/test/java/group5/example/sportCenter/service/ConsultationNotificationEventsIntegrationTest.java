package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CancelConsultationRequest;
import group5.example.sportCenter.dto.CompleteConsultationRequest;
import group5.example.sportCenter.dto.CreateConsultationRequest;
import group5.example.sportCenter.entity.Notification;
import group5.example.sportCenter.entity.Sport;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.ConsultationRequestRepository;
import group5.example.sportCenter.repository.NotificationRepository;
import group5.example.sportCenter.repository.SportRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;

@SpringBootTest
@ActiveProfiles("phase1")
class ConsultationNotificationEventsIntegrationTest {

    @Autowired ConsultationRequestService consultations;
    @Autowired ConsultationRequestRepository consultationRows;
    @Autowired UserRepository users;
    @Autowired SportRepository sports;
    @MockitoSpyBean NotificationRepository notifications;
    @Autowired JwtService tokens;
    @Autowired PlatformTransactionManager transactionManager;

    private Sport sport;

    @BeforeEach
    void setUp() {
        notifications.deleteAllInBatch();
        sport = new Sport();
        sport.setName("Notification sport " + UUID.randomUUID());
        sport.setStatus("ACTIVE");
        sport = sports.saveAndFlush(sport);
    }

    @Test
    void memberAndGuestRequestsNotifyOnlyActiveReceptionistsWithoutPrivateDetails() {
        User activeReceptionist = user("RECEPTIONIST", "ACTIVE");
        User secondActiveReceptionist = user("RECEPTIONIST", "ACTIVE");
        User inactiveReceptionist = user("RECEPTIONIST", "INACTIVE");
        User admin = user("ADMIN", "ACTIVE");
        User member = user("MEMBER", "ACTIVE");

        var memberRequest = consultations.create(request("member@example.test", "private note"), jwt(member));
        var guestRequest = consultations.create(request("guest@example.test", "guest private note"), null);

        for (User recipient : List.of(activeReceptionist, secondActiveReceptionist)) {
            List<Notification> delivered = notifications.findByUserId(recipient.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
            assertEquals(2, delivered.size());
            assertEquals(1, delivered.stream().filter(n -> n.getMessage().contains("#" + memberRequest.id())).count());
            assertEquals(1, delivered.stream().filter(n -> n.getMessage().contains("#" + guestRequest.id())).count());
            delivered.forEach(notification -> {
                assertEquals("New consultation request", notification.getTitle());
                org.junit.jupiter.api.Assertions.assertFalse(notification.getMessage().contains("example.test"));
                org.junit.jupiter.api.Assertions.assertFalse(notification.getMessage().contains("private note"));
            });
        }
        assertEquals(0, notifications.countByUserIdAndReadFalse(inactiveReceptionist.getId()));
        assertEquals(0, notifications.countByUserIdAndReadFalse(admin.getId()));
        assertEquals(0, notifications.countByUserIdAndReadFalse(member.getId()));
    }

    @Test
    void completedAndCancelledLinkedRequestsNotifyOnlyTheirActiveMemberOnce() {
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        User member = user("MEMBER", "ACTIVE");
        var request = consultations.create(request("member@example.test", null), jwt(member));
        notifications.deleteAllInBatch();

        consultations.take(request.id(), jwt(receptionist));
        consultations.complete(request.id(), new CompleteConsultationRequest("Private resolution"), jwt(receptionist));
        assertEquals(1, notifications.countByUserIdAndReadFalse(member.getId()));
        assertEquals("Consultation completed", notifications.findByUserId(member.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst().getTitle());
        assertThrows(ResponseStatusException.class, () -> consultations.complete(request.id(),
                new CompleteConsultationRequest(null), jwt(receptionist)));
        assertEquals(1, notifications.countByUserIdAndReadFalse(member.getId()));

        notifications.deleteAllInBatch();
        var cancellation = consultations.create(request("member2@example.test", null), jwt(member));
        notifications.deleteAllInBatch();
        consultations.take(cancellation.id(), jwt(receptionist));
        consultations.cancel(cancellation.id(), new CancelConsultationRequest("Private cancel reason"), jwt(receptionist));
        assertEquals(1, notifications.countByUserIdAndReadFalse(member.getId()));
        Notification cancellationNotice = notifications.findByUserId(member.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
        assertEquals("Consultation cancelled", cancellationNotice.getTitle());
        org.junit.jupiter.api.Assertions.assertFalse(cancellationNotice.getMessage().contains("Private cancel reason"));
    }

    @Test
    void guestCompletionAndFailedOrRolledBackCreationDoNotSendMemberNotifications() {
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        User member = user("MEMBER", "ACTIVE");
        long before = consultationRows.count();

        assertThrows(ResponseStatusException.class,
                () -> consultations.create(new CreateConsultationRequest("Guest", "123", null,
                        Integer.MAX_VALUE, null, null, null), null));
        assertEquals(0, notifications.count());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            consultations.create(request("rolled-back@example.test", null), jwt(member));
            status.setRollbackOnly();
            return null;
        });
        assertEquals(before, consultationRows.count());
        assertEquals(0, notifications.count());

        var guest = consultations.create(request("guest@example.test", null), null);
        notifications.deleteAllInBatch();
        consultations.take(guest.id(), jwt(receptionist));
        consultations.complete(guest.id(), new CompleteConsultationRequest(null), jwt(receptionist));
        assertEquals(0, notifications.countByUserIdAndReadFalse(member.getId()));
    }

    @Test
    void memberDeactivatedBeforeConsultationCompletionIsNotNotified() {
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        User member = user("MEMBER", "ACTIVE");
        var request = consultations.create(request("member@example.test", null), jwt(member));
        notifications.deleteAllInBatch();
        member.setStatus("INACTIVE");
        users.saveAndFlush(member);

        consultations.take(request.id(), jwt(receptionist));
        consultations.complete(request.id(), new CompleteConsultationRequest(null), jwt(receptionist));

        assertEquals(0, notifications.countByUserIdAndReadFalse(member.getId()));
    }

    @Test
    void notificationPersistenceFailureDoesNotUndoCommittedConsultation() {
        User receptionist = user("RECEPTIONIST", "ACTIVE");
        doThrow(new DataIntegrityViolationException("simulated notification failure"))
                .when(notifications).save(any(Notification.class));

        var created = consultations.create(request("guest@example.test", null), null);

        assertEquals("PENDING", consultationRows.findById(created.id()).orElseThrow().getStatus().name());
        assertEquals(0, notifications.countByUserIdAndReadFalse(receptionist.getId()));
    }

    private CreateConsultationRequest request(String email, String note) {
        return new CreateConsultationRequest("Visitor", "0900000000", email, sport.getId(), 25,
                "recreational", note);
    }

    private User user(String role, String status) {
        User user = new User();
        user.setName("Event test " + role);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus(status);
        return users.saveAndFlush(user);
    }

    private Jwt jwt(User user) {
        return tokens.validateToken(tokens.generateToken(user));
    }
}
