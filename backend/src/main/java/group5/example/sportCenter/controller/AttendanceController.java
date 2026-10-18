package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.AttendanceHistoryResponse;
import group5.example.sportCenter.dto.AttendanceResponse;
import group5.example.sportCenter.dto.AttendanceRosterResponse;
import group5.example.sportCenter.dto.AttendanceSessionResponse;
import group5.example.sportCenter.dto.MarkAttendanceRequest;
import group5.example.sportCenter.dto.MemberAttendanceResponse;
import group5.example.sportCenter.service.AttendanceService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/coach/sessions")
    public List<AttendanceSessionResponse> coachSessions(@AuthenticationPrincipal Jwt jwt) {
        return attendanceService.coachSessions(jwt);
    }

    @GetMapping("/sessions")
    public List<AttendanceSessionResponse> staffSessions(@AuthenticationPrincipal Jwt jwt) {
        return attendanceService.staffSessions(jwt);
    }

    @GetMapping("/sessions/{sessionId}/roster")
    public AttendanceRosterResponse roster(
            @PathVariable Integer sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        return attendanceService.roster(sessionId, jwt);
    }

    @PutMapping("/sessions/{sessionId}/members/{memberId}")
    public AttendanceResponse mark(
            @PathVariable Integer sessionId,
            @PathVariable Integer memberId,
            @Valid @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return attendanceService.mark(sessionId, memberId, request, jwt);
    }

    @GetMapping("/{attendanceId}/history")
    public List<AttendanceHistoryResponse> history(
            @PathVariable Integer attendanceId,
            @AuthenticationPrincipal Jwt jwt) {
        return attendanceService.history(attendanceId, jwt);
    }

    @GetMapping("/me")
    public List<MemberAttendanceResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return attendanceService.mine(jwt);
    }
}
