package group5.example.sportCenter.repository;

import org.springframework.data.repository.Repository;

import group5.example.sportCenter.entity.RevokedJwtToken;

public interface RevokedJwtTokenRepository extends Repository<RevokedJwtToken, byte[]> {
    boolean existsById(byte[] tokenFingerprint);
    RevokedJwtToken save(RevokedJwtToken token);
}
