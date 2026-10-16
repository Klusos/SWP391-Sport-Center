package group5.example.sportCenter.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CoachResponse;
import group5.example.sportCenter.dto.CreateCoachRequest;
import group5.example.sportCenter.dto.CreateStaffCoachRequest;
import group5.example.sportCenter.dto.CreateStaffReceptionistRequest;
import group5.example.sportCenter.dto.CreateUserRequest;
import group5.example.sportCenter.dto.UpdateStaffRequest;
import group5.example.sportCenter.dto.UserResponse;
import group5.example.sportCenter.entity.Coach;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.CoachRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class StaffManagementService {
    private final UserRepository users;
    private final CoachRepository coaches;
    private final UserService userService;
    private final CoachService coachService;
    private final CoachSportService coachSportService;

    public StaffManagementService(UserRepository users, CoachRepository coaches,
            UserService userService, CoachService coachService, CoachSportService coachSportService) {
        this.users = users;
        this.coaches = coaches;
        this.userService = userService;
        this.coachService = coachService;
        this.coachSportService = coachSportService;
    }

    @Transactional
    public UserResponse createReceptionist(CreateStaffReceptionistRequest request) {
        CreateUserRequest account = new CreateUserRequest();
        account.setName(request.getName());
        account.setEmail(request.getEmail());
        account.setDateOfBirth(request.getDateOfBirth());
        account.setGender(request.getGender());
        account.setPhone(request.getPhone());
        account.setPassword(request.getPassword());
        account.setRole("RECEPTIONIST");
        return userService.createUser(account);
    }

    @Transactional
    public CoachResponse createCoach(CreateStaffCoachRequest request) {
        CreateCoachRequest account = new CreateCoachRequest();
        account.setName(request.getName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setGender(request.getGender());
        account.setPassword(request.getPassword());
        CoachResponse coach = coachService.createCoach(account);

        java.util.HashSet<Integer> sportIds = new java.util.HashSet<>();
        for (CreateStaffCoachRequest.Specialization specialization : request.getSpecializations()) {
            if (!sportIds.add(specialization.getSportId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each sport can be assigned only once");
            }
            var assignment = new group5.example.sportCenter.dto.AssignCoachSportRequest();
            assignment.setSportId(specialization.getSportId());
            assignment.setExperienceYears(specialization.getExperienceYears());
            assignment.setCertification(specialization.getCertification());
            coachSportService.assignSport(coach.getId(), assignment);
        }
        return coach;
    }

    @Transactional
    public UserResponse update(Integer userId, UpdateStaffRequest request) {
        User staff = findStaff(userId);
        String email = request.getEmail().trim();
        if (!staff.getEmail().equalsIgnoreCase(email) && users.existsByEmailAndIdNot(email, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        staff.setName(request.getName().trim());
        staff.setEmail(email);
        staff.setDateOfBirth(request.getDateOfBirth());
        staff.setGender(request.getGender());
        staff.setPhone(request.getPhone());
        return response(users.save(staff));
    }

    @Transactional
    public UserResponse updateStatus(Integer userId, String requestedStatus) {
        User staff = findStaff(userId);
        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or INACTIVE");
        }
        staff.setStatus(status);
        if ("COACH".equalsIgnoreCase(staff.getRole())) {
            coaches.findByUserId(userId).ifPresent(coach -> coach.setStatus(status));
        }
        return response(users.save(staff));
    }

    private User findStaff(Integer userId) {
        User staff = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found with id: " + userId));
        if (!"COACH".equalsIgnoreCase(staff.getRole())
                && !"RECEPTIONIST".equalsIgnoreCase(staff.getRole())) {
            throw new ResourceNotFoundException("Staff user not found with id: " + userId);
        }
        return staff;
    }

    private UserResponse response(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getDateOfBirth(),
                user.getGender(), user.getPhone(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
