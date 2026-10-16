package group5.example.sportCenter.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.CoachResponse;
import group5.example.sportCenter.dto.CreateStaffCoachRequest;
import group5.example.sportCenter.dto.CreateStaffReceptionistRequest;
import group5.example.sportCenter.dto.UpdateStaffRequest;
import group5.example.sportCenter.dto.UpdateStaffStatusRequest;
import group5.example.sportCenter.dto.UserResponse;
import group5.example.sportCenter.service.StaffManagementService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/staff")
public class StaffManagementController {
    private final StaffManagementService staffService;

    public StaffManagementController(StaffManagementService staffService) {
        this.staffService = staffService;
    }

    @PostMapping("/coaches")
    @ResponseStatus(HttpStatus.CREATED)
    public CoachResponse createCoach(@Valid @RequestBody CreateStaffCoachRequest request) {
        return staffService.createCoach(request);
    }

    @PostMapping("/receptionists")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createReceptionist(@Valid @RequestBody CreateStaffReceptionistRequest request) {
        return staffService.createReceptionist(request);
    }

    @PutMapping("/{userId}")
    public UserResponse update(@PathVariable Integer userId, @Valid @RequestBody UpdateStaffRequest request) {
        return staffService.update(userId, request);
    }

    @PatchMapping("/{userId}/status")
    public UserResponse updateStatus(@PathVariable Integer userId,
            @Valid @RequestBody UpdateStaffStatusRequest request) {
        return staffService.updateStatus(userId, request.getStatus());
    }
}
