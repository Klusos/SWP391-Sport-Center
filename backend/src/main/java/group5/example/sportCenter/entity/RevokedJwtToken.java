package group5.example.sportCenter.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_revoked_token", indexes = {
        @Index(name = "ix_auth_revoked_token_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RevokedJwtToken {
    @Id
    @Column(name = "token_fingerprint", nullable = false, length = 32, columnDefinition = "binary(32)")
    private byte[] tokenFingerprint;

    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime revokedAt;

    public RevokedJwtToken(byte[] tokenFingerprint, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        this.tokenFingerprint = tokenFingerprint;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }
}
