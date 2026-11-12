package group5.example.sportCenter.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ApiErrorResponseWriter;
import group5.example.sportCenter.repository.RevokedJwtTokenRepository;
import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.service.JwtRevocationService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mockito;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jwt-revocation;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "sportcenter.scheduling.enabled=false",
        "app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.payment.callback-secret=auth-test-payment-callback-secret"
})
class JwtRevocationIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy security;
    @Autowired private UserRepository users;
    @Autowired private RevokedJwtTokenRepository revokedTokens;
    @Autowired private JwtRevocationService revocations;
    @Autowired private JwtService jwtService;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginIssuesUniqueJtiAndValidTokensAreAccepted() throws Exception {
        User member = user("MEMBER");
        String password = "isolated-test-password";
        member.setPasswordHash(passwordEncoder.encode(password));
        users.saveAndFlush(member);
        String loginBody = "{\"email\":\"" + member.getEmail() + "\",\"password\":\"" + password + "\"}";
        String firstToken = objectMapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").stringValue();
        String secondToken = objectMapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").stringValue();
        Jwt first = jwtService.validateToken(firstToken);
        Jwt second = jwtService.validateToken(secondToken);

        org.junit.jupiter.api.Assertions.assertNotEquals(first.getId(), second.getId());
        org.junit.jupiter.api.Assertions.assertTrue(JwtRevocationFingerprint.isValidJti(first.getId()));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(first)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesOnlyItsTokenAndRepeatedLogoutIsIdempotent() throws Exception {
        User member = user("MEMBER");
        Jwt first = jwtService.validateToken(jwtService.generateToken(member));
        Jwt otherDevice = jwtService.validateToken(jwtService.generateToken(member));

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(first)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(first)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(otherDevice)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(first)))
                .andExpect(status().isNoContent());
    }

    @Test
    void concurrentRevocationsOfOneTokenAreIdempotentAndPersistExactlyOneRow() throws Exception {
        int callers = 8;
        CyclicBarrier simultaneousInitialChecks = new CyclicBarrier(callers);
        AtomicInteger initialChecks = new AtomicInteger();
        RevokedJwtTokenRepository coordinatedRepository = new RevokedJwtTokenRepository() {
            @Override
            public boolean existsById(byte[] fingerprint) {
                boolean exists = revokedTokens.existsById(fingerprint);
                if (!exists && initialChecks.incrementAndGet() <= callers) {
                    try {
                        simultaneousInitialChecks.await(10, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new IllegalStateException("Concurrent revocation test barrier failed", exception);
                    }
                }
                return exists;
            }

            @Override
            public group5.example.sportCenter.entity.RevokedJwtToken save(
                    group5.example.sportCenter.entity.RevokedJwtToken token) {
                return revokedTokens.save(token);
            }
        };
        JwtRevocationService concurrentService = new JwtRevocationService(
                coordinatedRepository, transactionManager);
        User member = user("MEMBER");
        Jwt token = jwtService.validateToken(jwtService.generateToken(member));
        byte[] fingerprint = JwtRevocationFingerprint.fromJti(token.getId());
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> concurrentService.revoke(token)));
            }
            for (Future<?> result : results) result.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_revoked_token WHERE token_fingerprint = ?",
                Integer.class,
                fingerprint);
        org.junit.jupiter.api.Assertions.assertEquals(1, rows);
        org.junit.jupiter.api.Assertions.assertTrue(revocations.isRevoked(token.getId()));
    }

    @Test
    void genuineRevocationInsertFailureRemainsUnavailable() {
        RevokedJwtTokenRepository failingRepository = Mockito.mock(RevokedJwtTokenRepository.class);
        Mockito.when(failingRepository.existsById(Mockito.any(byte[].class))).thenReturn(false);
        Mockito.when(failingRepository.save(Mockito.any()))
                .thenThrow(new DataAccessResourceFailureException("isolated insert failure"));
        JwtRevocationService failingService = new JwtRevocationService(
                failingRepository, transactionManager);
        User member = user("MEMBER");
        Jwt token = jwtService.validateToken(jwtService.generateToken(member));

        ResponseStatusException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> failingService.revoke(token));
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }

    @Test
    void unrelatedIntegrityFailureIsNotAcceptedAsAnIdempotentDuplicate() {
        RevokedJwtTokenRepository failingRepository = Mockito.mock(RevokedJwtTokenRepository.class);
        Mockito.when(failingRepository.existsById(Mockito.any(byte[].class))).thenReturn(false);
        Mockito.when(failingRepository.save(Mockito.any()))
                .thenThrow(new DataIntegrityViolationException("constraint ck_unrelated revoked_at invalid"));
        JwtRevocationService failingService = new JwtRevocationService(failingRepository, transactionManager);
        User member = user("MEMBER");
        Jwt token = jwtService.validateToken(jwtService.generateToken(member));

        ResponseStatusException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> failingService.revoke(token));
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
        Mockito.verify(failingRepository, Mockito.times(1)).existsById(Mockito.any(byte[].class));
    }

    @Test
    void revocationRemainsInPersistentStoreAfterEntityManagerIsCleared() {
        User member = user("MEMBER");
        Jwt token = jwtService.validateToken(jwtService.generateToken(member));
        revocations.revoke(token);
        entityManager.clear();

        JwtRevocationService reconstructedService = new JwtRevocationService(revokedTokens, transactionManager);
        org.junit.jupiter.api.Assertions.assertTrue(reconstructedService.isRevoked(token.getId()));
    }

    @Test
    void legacyTokenWithoutJtiIsRejected() throws Exception {
        User member = user("MEMBER");
        String legacy = encode(member, Instant.now().plusSeconds(600), false);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + legacy))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndInvalidSignatureTokensAreRejected() throws Exception {
        User member = user("MEMBER");
        String expired = encode(member, Instant.now().minusSeconds(600), true);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid.signature.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revocationStoreFailureFailsClosedBeforeTheController() throws Exception {
        RevokedJwtTokenRepository failingRepository = Mockito.mock(RevokedJwtTokenRepository.class);
        Mockito.when(failingRepository.existsById(Mockito.any(byte[].class)))
                .thenThrow(new DataAccessResourceFailureException("isolated test failure"));
        JwtRevocationService failingService = new JwtRevocationService(failingRepository, transactionManager);
        ApiErrorResponseWriter errorWriter = new ApiErrorResponseWriter();
        CurrentAccountAuthorizationFilter filter = new CurrentAccountAuthorizationFilter(users, errorWriter, failingService);
        User member = user("MEMBER");
        Jwt jwt = jwtService.validateToken(jwtService.generateToken(member));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), jwt.getSubject()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reachedController = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> reachedController.set(true);

        filter.doFilter(request, response, chain);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(reachedController.get());
        SecurityContextHolder.clearContext();
    }

    private User user(String role) {
        User user = new User();
        user.setName("JWT revocation test");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("not-used-in-test");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private String bearer(Jwt jwt) {
        return "Bearer " + jwt.getTokenValue();
    }

    private String encode(User user, Instant expiresAt, boolean includeJti) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("sport-center")
                .subject(user.getId().toString())
                .issuedAt(expiresAt.isAfter(Instant.now()) ? Instant.now() : expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("role", user.getRole());
        if (includeJti) claims.claim("jti", UUID.randomUUID().toString());
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
