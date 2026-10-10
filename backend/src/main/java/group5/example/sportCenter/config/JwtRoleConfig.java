package group5.example.sportCenter.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class JwtRoleConfig {

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {

        return jwt -> {

            String role = jwt.getClaimAsString("role");

            if (role == null || !List.of("ADMIN", "RECEPTIONIST", "MEMBER", "COACH").contains(role)) {

                return new JwtAuthenticationToken(
                        jwt,
                        List.of(),
                        jwt.getSubject()
                );
            }

            return new JwtAuthenticationToken(
                    jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                    jwt.getSubject()
            );
        };
    }
}