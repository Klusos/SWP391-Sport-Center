package group5.example.sportCenter.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import group5.example.sportCenter.dto.MemberSportLevelResponse;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.*;
import static group5.example.sportCenter.service.AssessmentAccess.error;

@Service
@Transactional(readOnly = true)
public class MemberSportLevelService {
    private final MemberSportLevelRepository levels;
    private final UserRepository users;
    private final AssessmentAccess access;
    public MemberSportLevelService(MemberSportLevelRepository levels, UserRepository users, AssessmentAccess access) {
        this.levels = levels; this.users = users; this.access = access;
    }
    public List<MemberSportLevelResponse> mine(Jwt jwt) {
        User member = access.actor(jwt); access.requireMember(member);
        return read(member.getId());
    }
    public List<MemberSportLevelResponse> forMember(Integer memberId, Jwt jwt) {
        access.requireStaff(access.actor(jwt));
        User member = users.findById(memberId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Member user not found"));
        if (!"MEMBER".equalsIgnoreCase(member.getRole())) throw error(HttpStatus.CONFLICT, "Target must have MEMBER role");
        return read(memberId);
    }
    private List<MemberSportLevelResponse> read(Integer memberId) {
        return levels.findByMemberIdOrderBySportIdAsc(memberId).stream().map(l -> new MemberSportLevelResponse(
                l.getId(), l.getMember().getId(), l.getSport().getId(), l.getLevel().name(),
                l.getVerifiedByCoach() == null ? null : l.getVerifiedByCoach().getId(), l.getVerifiedAt(), l.getUpdatedAt())).toList();
    }
}
