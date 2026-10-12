package group5.example.sportCenter.controller;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import group5.example.sportCenter.dto.MemberSportLevelResponse;
import group5.example.sportCenter.service.MemberSportLevelService;

@RestController
@RequestMapping("/api/member-sport-levels")
public class MemberSportLevelController {
    private final MemberSportLevelService service;
    public MemberSportLevelController(MemberSportLevelService service) { this.service = service; }
    @GetMapping("/me")
    public List<MemberSportLevelResponse> mine(@AuthenticationPrincipal Jwt jwt) { return service.mine(jwt); }
    @GetMapping("/member/{memberId}")
    public List<MemberSportLevelResponse> forMember(@PathVariable Integer memberId, @AuthenticationPrincipal Jwt jwt) {
        return service.forMember(memberId, jwt);
    }
}
