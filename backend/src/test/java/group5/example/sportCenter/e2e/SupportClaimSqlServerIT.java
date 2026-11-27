package group5.example.sportCenter.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.entity.SupportTicket;
import group5.example.sportCenter.entity.SupportTicketCategory;
import group5.example.sportCenter.entity.SupportTicketStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.SupportTicketRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.security.JwtService;
import group5.example.sportCenter.service.SupportTicketService;

/** Clone-pinned synthetic proof that competing Receptionist claims serialize and audit once. */
@SpringBootTest(properties = {
        "spring.datasource.url=" + Phase8CJPtOutcomeSqlServerIT.CloneGuard.URL,
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "spring.datasource.username=${DB_USERNAME}", "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect",
        "spring.jpa.show-sql=false", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false", "sportcenter.scheduling.enabled=false",
        "sportcenter.renewal.scheduler.enabled=false", "sportcenter.pt.outcome-fallback.enabled=false",
        "app.payment.provider=mock", "app.payment.mock-gateway.enabled=true", "debug=false",
        "logging.level.root=WARN"
})
@ActiveProfiles("phase1")
@ContextConfiguration(initializers = Phase8CJPtOutcomeSqlServerIT.CloneGuard.class)
class SupportClaimSqlServerIT {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired SupportTicketRepository tickets;
    @Autowired SupportTicketService service;
    @Autowired JwtService jwtService;

    private final String marker = "SCL-" + UUID.randomUUID().toString().substring(0, 10);
    private User member;
    private User receptionistOne;
    private User receptionistTwo;
    private SupportTicket ticket;
    private int userCount;
    private int ticketCount;
    private int messageCount;
    private int auditCount;

    @BeforeEach
    void createSyntheticTicket() throws Exception {
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys.dm_exec_requests r "
                + "JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id "
                + "WHERE r.database_id=DB_ID() AND r.session_id<>@@SPID AND s.is_user_process=1 "
                + "AND UPPER(r.command) IN ('INSERT','UPDATE','DELETE','MERGE','ALTER','CREATE','DROP','TRUNCATE')",
                Integer.class), "Competing DB12 writer detected before fixture setup");
        userCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users", Integer.class);
        ticketCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.support_ticket", Integer.class);
        messageCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.support_ticket_message", Integer.class);
        auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log", Integer.class);
        member = user("MEMBER");
        receptionistOne = user("RECEPTIONIST");
        receptionistTwo = user("RECEPTIONIST");
        ticket = new SupportTicket(); ticket.setMember(member); ticket.setSubject(marker + " claim test");
        ticket.setCategory(SupportTicketCategory.OTHER); ticket.setDescription(marker + " synthetic only");
        ticket.setStatus(SupportTicketStatus.OPEN); ticket = tickets.saveAndFlush(ticket);
    }

    @Test
    void competingClaimsHaveOneWinnerAndOneTransactionalAudit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one = pool.submit(() -> claim(receptionistOne, ready, start));
            Future<Integer> two = pool.submit(() -> claim(receptionistTwo, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int first = one.get(30, TimeUnit.SECONDS);
            int second = two.get(30, TimeUnit.SECONDS);
            assertTrue((first == 200 && second == 409) || (first == 409 && second == 200));
            SupportTicket persisted = tickets.findById(ticket.getId()).orElseThrow();
            assertEquals(SupportTicketStatus.IN_PROGRESS, persisted.getStatus());
            assertTrue(persisted.getAssignedStaff().getId().equals(receptionistOne.getId())
                    || persisted.getAssignedStaff().getId().equals(receptionistTwo.getId()));
            assertEquals(1, scopedAuditCount("CLAIM_AND_START"));
            User winnerUser = persisted.getAssignedStaff().getId().equals(receptionistOne.getId())
                    ? receptionistOne : receptionistTwo;
            Jwt winner = jwt(winnerUser);
            service.assignToMe(ticket.getId(), winner);
            assertEquals(1, scopedAuditCount("CLAIM_AND_START"), "repeat claim must not duplicate the transition audit");
            System.out.println("SUPPORT_CLAIM_SQLSERVER winner=" + persisted.getAssignedStaff().getId()
                    + " status=" + persisted.getStatus() + " claimAndStartAudits=" + scopedAuditCount("CLAIM_AND_START"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void legacyUnassignedInProgressTicketCanBeClaimedWithoutChangingStatus() {
        ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        tickets.saveAndFlush(ticket);
        service.assignToMe(ticket.getId(), jwt(receptionistOne));
        SupportTicket persisted = tickets.findById(ticket.getId()).orElseThrow();
        assertEquals(SupportTicketStatus.IN_PROGRESS, persisted.getStatus());
        assertEquals(receptionistOne.getId(), persisted.getAssignedStaff().getId());
        assertEquals(1, scopedAuditCount("ASSIGN"));
        assertEquals(0, scopedAuditCount("CLAIM_AND_START"));
    }

    private int claim(User actor, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("SQL Server claim start gate timed out");
        try { service.assignToMe(ticket.getId(), jwt(actor)); return 200; }
        catch (ResponseStatusException error) { return error.getStatusCode().value(); }
    }

    private int scopedAuditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE entity_type='SUPPORT_TICKET' AND entity_id=? AND action=?",
                Integer.class, ticket.getId().toString(), action);
    }

    private User user(String role) {
        User user = new User(); user.setName(marker + " " + role);
        user.setEmail(marker.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@support-claim-qa.test");
        user.setPasswordHash("synthetic-unused-hash"); user.setRole(role); user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private Jwt jwt(User user) { return jwtService.validateToken(jwtService.generateToken(user)); }

    @AfterEach
    void cleanExactFixture() throws Exception {
        Phase8CJPtOutcomeSqlServerIT.CloneGuard.verifyRuntime(dataSource, environment, context);
        if (ticket != null) {
            jdbc.update("DELETE FROM dbo.audit_log WHERE entity_type='SUPPORT_TICKET' AND entity_id=?", ticket.getId().toString());
            jdbc.update("DELETE FROM dbo.support_ticket_message WHERE ticket_id=?", ticket.getId());
            jdbc.update("DELETE FROM dbo.support_ticket WHERE id=?", ticket.getId());
        }
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id IN (SELECT id FROM dbo.users WHERE name LIKE ? AND email LIKE ?)",
                marker + "%", "%@support-claim-qa.test");
        jdbc.update("DELETE FROM dbo.users WHERE name LIKE ? AND email LIKE ?", marker + "%", "%@support-claim-qa.test");
        assertEquals(userCount, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.users", Integer.class));
        assertEquals(ticketCount, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.support_ticket", Integer.class));
        assertEquals(messageCount, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.support_ticket_message", Integer.class));
        assertEquals(auditCount, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log", Integer.class));
    }
}
