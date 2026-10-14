package group5.example.sportCenter.security;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import group5.example.sportCenter.exception.ApiErrorResponseWriter;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Resolves JWT callers against the current account state before role authorization. */
public final class CurrentAccountAuthorizationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN", "RECEPTIONIST", "MEMBER", "COACH");

    private final UserRepository users;
    private final ApiErrorResponseWriter errors;

    public CurrentAccountAuthorizationFilter(UserRepository users, ApiErrorResponseWriter errors) {
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.isAuthenticated()) {
            final Integer userId;
            try {
                userId = Integer.valueOf(jwtAuth.getToken().getSubject());
            } catch (RuntimeException exception) {
                reject(response, HttpStatus.UNAUTHORIZED, "The authenticated account could not be identified");
                return;
            }

            User user = users.findById(userId).orElse(null);
            if (user == null) {
                reject(response, HttpStatus.UNAUTHORIZED, "The authenticated account no longer exists");
                return;
            }
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                reject(response, HttpStatus.FORBIDDEN, "The account is inactive");
                return;
            }

            String currentRole = user.getRole() == null
                    ? ""
                    : user.getRole().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_ROLES.contains(currentRole)) {
                reject(response, HttpStatus.FORBIDDEN, "The account role is not permitted");
                return;
            }

            JwtAuthenticationToken currentAuthentication = new JwtAuthenticationToken(
                    jwtAuth.getToken(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + currentRole)),
                    jwtAuth.getName());
            SecurityContextHolder.getContext().setAuthentication(currentAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        errors.write(response, status, message);
    }
}
