package group5.example.sportCenter.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import group5.example.sportCenter.entity.MemberProfile;

@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfile, Integer> {
    Optional<MemberProfile> findByMemberId(Integer memberId);
    boolean existsByMemberId(Integer memberId);
}
