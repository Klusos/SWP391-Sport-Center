package group5.example.sportCenter.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.MemberSportLevel;

public interface MemberSportLevelRepository extends JpaRepository<MemberSportLevel, Integer> {
    Optional<MemberSportLevel> findByMemberIdAndSportId(Integer memberId, Integer sportId);
    List<MemberSportLevel> findByMemberIdOrderBySportIdAsc(Integer memberId);
}
