package group5.example.sportCenter.config;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import group5.example.sportCenter.repository.UserRepository;
import group5.example.sportCenter.exception.ApiErrorResponseWriter;
import group5.example.sportCenter.security.ApiSecurityErrorHandler;
import group5.example.sportCenter.security.CurrentAccountAuthorizationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            UserRepository userRepository,
            ApiErrorResponseWriter apiErrors,
            ApiSecurityErrorHandler securityErrors
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(securityErrors)
                .accessDeniedHandler(securityErrors)
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/sports").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/sports/*/courses", "/api/public/courses/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/membership-plans").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/coaches", "/api/public/facilities", "/api/public/images/*").permitAll()

                .requestMatchers(HttpMethod.POST, "/api/consultation-requests").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/consultation-requests/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/consultation-requests")
                    .hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.PATCH, "/api/consultation-requests/*/take")
                    .hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/consultation-requests/*").authenticated()
                .requestMatchers(HttpMethod.PATCH,
                        "/api/consultation-requests/*/complete",
                        "/api/consultation-requests/*/cancel",
                        "/api/consultation-requests/*/link-member").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/consultation-requests/*/create-member").authenticated()
                .requestMatchers("/api/consultation-requests", "/api/consultation-requests/**").authenticated()

                .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*")
                    .hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/users", "/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/members").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.PUT, "/api/members/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/members/*/deactivate").hasRole("ADMIN")
                .requestMatchers("/api/staff", "/api/staff/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/member-profiles/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/member-profiles/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.PUT, "/api/member-profiles/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/member-profiles")
                    .hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/member-profiles", "/api/member-profiles/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/skill-assessments").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/my-assignments").hasRole("COACH")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/*/member-level").hasRole("COACH")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/*").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER", "COACH")
                .requestMatchers(HttpMethod.PATCH, "/api/skill-assessments/*/complete").hasRole("COACH")
                .requestMatchers(HttpMethod.PATCH, "/api/skill-assessments/*/cancel").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/skill-assessments", "/api/skill-assessments/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/member-sport-levels/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/member-sport-levels/member/*").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/member-sport-levels", "/api/member-sport-levels/**").denyAll()

                .requestMatchers(HttpMethod.POST, "/api/course-registrations").hasRole("MEMBER")
                .requestMatchers(HttpMethod.POST, "/api/course-registrations/staff").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.POST, "/api/course-registrations/*/cancel").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/course-registrations/me",
                        "/api/course-registrations/me/class-sessions").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/course-registrations").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/course-registrations", "/api/course-registrations/**").denyAll()

                .requestMatchers(HttpMethod.GET, "/api/attendance/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/attendance/coach/sessions").hasRole("COACH")
                .requestMatchers(HttpMethod.GET, "/api/attendance/sessions")
                    .hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/attendance/sessions/*/roster")
                    .hasAnyRole("ADMIN", "RECEPTIONIST", "COACH")
                .requestMatchers(HttpMethod.PUT, "/api/attendance/sessions/*/members/*")
                    .hasAnyRole("ADMIN", "COACH")
                .requestMatchers(HttpMethod.GET, "/api/attendance/*/history").authenticated()
                .requestMatchers("/api/attendance", "/api/attendance/**").denyAll()

                .requestMatchers(HttpMethod.GET, "/api/pt-bookings/eligible-coaches", "/api/pt-bookings/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.POST, "/api/pt-bookings").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/pt-bookings/assigned").hasRole("COACH")
                .requestMatchers("/api/pt-bookings", "/api/pt-bookings/**").denyAll()

                .requestMatchers(HttpMethod.GET, "/api/payments/online/callback",
                        "/api/payments/vnpay/return", "/api/payments/vnpay/ipn").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                .requestMatchers(HttpMethod.PATCH, "/api/payments/*/counter").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.PATCH, "/api/payments/*/cancel").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/payments/pending-memberships", "/api/payments/pending-courses").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/payments/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/payments/member/*").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/payments", "/api/payments/**").denyAll()

                .requestMatchers(HttpMethod.POST, "/api/support-tickets").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/support-tickets/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/support-tickets").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/support-tickets/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/support-tickets/*/messages").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/support-tickets/*/status",
                        "/api/support-tickets/*/assign-to-me").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/support-tickets", "/api/support-tickets/**").denyAll()

                .requestMatchers(HttpMethod.GET, "/api/reports/**").hasRole("ADMIN")
                .requestMatchers("/api/reports", "/api/reports/**").denyAll()

                .requestMatchers(HttpMethod.POST, "/api/admin/notifications/broadcast").hasRole("ADMIN")
                .requestMatchers("/api/admin/notifications", "/api/admin/notifications/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/admin/audit-logs").hasRole("ADMIN")
                .requestMatchers("/api/admin/audit-logs", "/api/admin/audit-logs/**").denyAll()
                .requestMatchers("/api/notifications", "/api/notifications/**")
                    .hasAnyRole("ADMIN", "RECEPTIONIST", "COACH", "MEMBER")

                .requestMatchers(HttpMethod.GET, "/api/membership-plans", "/api/membership-plans/**").hasRole("ADMIN")
                .requestMatchers("/api/membership-plans", "/api/membership-plans/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/memberships/expire").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/memberships").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/memberships").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/memberships/member/*", "/api/memberships/*")
                    .hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                .requestMatchers(HttpMethod.PATCH,
                        "/api/memberships/*/cancel-renewal",
                        "/api/memberships/*/enable-auto-renew")
                    .hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
                .requestMatchers("/api/memberships", "/api/memberships/**").denyAll()

                .requestMatchers(HttpMethod.GET, "/api/coaches", "/api/coaches/**").authenticated()
                .requestMatchers("/api/coaches", "/api/coaches/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/class-sessions", "/api/class-sessions/**").authenticated()
                .requestMatchers("/api/class-sessions", "/api/class-sessions/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET,
                        "/api/sports", "/api/sports/**",
                        "/api/courses", "/api/courses/**",
                        "/api/course-offerings", "/api/course-offerings/**",
                        "/api/classes", "/api/classes/**",
                        "/api/facilities", "/api/facilities/**")
                    .authenticated()
                .requestMatchers(HttpMethod.POST,
                        "/api/class-sessions/generation/preview",
                        "/api/class-sessions/generation/confirm")
                    .hasRole("ADMIN")
                .requestMatchers("/api/sports", "/api/sports/**",
                        "/api/courses", "/api/courses/**",
                        "/api/course-offerings", "/api/course-offerings/**",
                        "/api/classes", "/api/classes/**",
                        "/api/facilities", "/api/facilities/**")
                    .hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(securityErrors)
                .accessDeniedHandler(securityErrors)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            );

        http.addFilterAfter(
                new CurrentAccountAuthorizationFilter(userRepository, apiErrors),
                BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String configuredOrigins) {

        List<String> allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins must contain explicit origins and cannot contain '*'");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
