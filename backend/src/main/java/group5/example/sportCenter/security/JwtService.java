package group5.example.sportCenter.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import group5.example.sportCenter.entity.User;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final long expirationMinutes;

    public JwtService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            @Value("${app.jwt.expiration-minutes}")
            long expirationMinutes) {

        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(User user) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sport-center")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(
                        now.plus(
                                expirationMinutes,
                                ChronoUnit.MINUTES
                        )
                )
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    public Jwt validateToken(String token) {
        return jwtDecoder.decode(token);
    }
}