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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

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

                .requestMatchers(HttpMethod.POST, "/api/member-profiles/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/member-profiles/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.PUT, "/api/member-profiles/me").hasRole("MEMBER")
                .requestMatchers("/api/member-profiles", "/api/member-profiles/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/skill-assessments").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/my-assignments").hasRole("COACH")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/skill-assessments/*").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER", "COACH")
                .requestMatchers(HttpMethod.PATCH, "/api/skill-assessments/*/complete").hasRole("COACH")
                .requestMatchers(HttpMethod.PATCH, "/api/skill-assessments/*/cancel").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/skill-assessments", "/api/skill-assessments/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/api/member-sport-levels/me").hasRole("MEMBER")
                .requestMatchers(HttpMethod.GET, "/api/member-sport-levels/member/*").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/member-sport-levels", "/api/member-sport-levels/**").denyAll()

                .requestMatchers(HttpMethod.GET, "/api/membership-plans", "/api/membership-plans/**").authenticated()
                .requestMatchers("/api/membership-plans", "/api/membership-plans/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/memberships/expire").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/memberships").hasAnyRole("ADMIN", "RECEPTIONIST", "MEMBER")
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
                .requestMatchers("/api/sports", "/api/sports/**",
                        "/api/courses", "/api/courses/**",
                        "/api/course-offerings", "/api/course-offerings/**",
                        "/api/classes", "/api/classes/**",
                        "/api/facilities", "/api/facilities/**")
                    .hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            );

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
