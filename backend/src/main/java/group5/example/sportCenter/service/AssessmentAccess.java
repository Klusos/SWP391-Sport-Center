package group5.example.sportCenter.service;

import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import group5.example.sportCenter.entity.*;
import group5.example.sportCenter.repository.*;

@Component
public class AssessmentAccess {
    private final UserRepository users;
    private final CoachRepository coaches;
    public AssessmentAccess(UserRepository users, CoachRepository coaches) {
        this.users = users;
        this.coaches = coaches;
    }
    public User actor(Jwt jwt) {
        final Integer id;
        try { id = Integer.valueOf(jwt.getSubject()); }
        catch (RuntimeException e) { throw error(HttpStatus.UNAUTHORIZED, "Invalid JWT subject"); }
        User user = users.findById(id).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) throw error(HttpStatus.FORBIDDEN, "Account is inactive");
        return user;
    }
    public boolean staff(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "RECEPTIONIST".equalsIgnoreCase(user.getRole());
    }
    public void requireStaff(User user) {
        if (!staff(user)) throw error(HttpStatus.FORBIDDEN, "ADMIN or RECEPTIONIST required");
    }
    public void requireMember(User user) {
        if (!"MEMBER".equalsIgnoreCase(user.getRole())) throw error(HttpStatus.FORBIDDEN, "MEMBER required");
    }
    public Coach coach(User user) {
        if (!"COACH".equalsIgnoreCase(user.getRole())) throw error(HttpStatus.FORBIDDEN, "COACH required");
        Coach coach = coaches.findByUserId(user.getId())
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "No Coach linked to authenticated user"));
        if (!"ACTIVE".equalsIgnoreCase(coach.getStatus())) throw error(HttpStatus.FORBIDDEN, "Coach is inactive");
        return coach;
    }
    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
