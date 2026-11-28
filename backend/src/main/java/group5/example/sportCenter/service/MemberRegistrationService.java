package group5.example.sportCenter.service;

import group5.example.sportCenter.dto.CreateMemberRequest;
import group5.example.sportCenter.dto.MemberResponse;
import group5.example.sportCenter.dto.UpdateMemberRequest;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import group5.example.sportCenter.validation.VietnamesePhoneNumbers;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogs;

    @Transactional
    public MemberResponse createMember(CreateMemberRequest request) {

        String email = request.getEmail().trim();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already exists"
            );
        }

        User user = new User();

        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setDateOfBirth(request.getDateOfBirth());
        user.setPhone(VietnamesePhoneNumbers.normalizeOptional(request.getPhone()));
        user.setGender(request.getGender());

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        auditLogs.record("CREATE", "USER", savedUser.getId(), "Staff created MEMBER account.");

        return new MemberResponse(
            savedUser.getId(),
            savedUser.getName(),
            savedUser.getEmail(),
            savedUser.getDateOfBirth(),
            savedUser.getPhone(),
            savedUser.getGender(),
            savedUser.getRole(),
            savedUser.getStatus()
        );
    }

    @Transactional
    public MemberResponse updateMember(Integer id, UpdateMemberRequest request) {
        User member = userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Member not found"));
        requireMember(member);

        String email = request.getEmail().trim();
        if (!member.getEmail().equalsIgnoreCase(email)
                && userRepository.existsByEmailAndIdNot(email, id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.CONFLICT, "Email already exists");
        }

        java.util.List<String> changedFields = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(member.getName(), request.getName().trim())) changedFields.add("name");
        if (!java.util.Objects.equals(member.getEmail(), request.getEmail().trim())) changedFields.add("email");
        if (!java.util.Objects.equals(member.getDateOfBirth(), request.getDateOfBirth())) changedFields.add("dateOfBirth");
        if (!java.util.Objects.equals(member.getGender(), request.getGender())) changedFields.add("gender");
        if (!java.util.Objects.equals(member.getPhone(), request.getPhone())) changedFields.add("phone");
        member.setName(request.getName().trim());
        member.setEmail(email);
        member.setDateOfBirth(request.getDateOfBirth());
        member.setGender(request.getGender());
        member.setPhone(VietnamesePhoneNumbers.normalizeOptional(request.getPhone()));
        User saved = userRepository.saveAndFlush(member);
        if (!changedFields.isEmpty()) {
            auditLogs.record("PROFILE_UPDATE", "USER", id,
                    "Administrative profile fields changed=" + String.join(",", changedFields) + ";values=not-copied");
        }
        return response(saved);
    }

    @Transactional
    public MemberResponse deactivateMember(Integer id) {
        User member = userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Member not found"));
        requireMember(member);
        member.setStatus("INACTIVE");
        User saved = userRepository.saveAndFlush(member);
        auditLogs.record("STATUS_CHANGE", "USER", id, "Account status changed=ACTIVE->INACTIVE.");
        return response(saved);
    }

    private void requireMember(User user) {
        if (!"MEMBER".equalsIgnoreCase(user.getRole())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Member not found");
        }
    }

    private MemberResponse response(User user) {
        return new MemberResponse(user.getId(), user.getName(), user.getEmail(),
                user.getDateOfBirth(), user.getPhone(), user.getGender(),
                user.getRole(), user.getStatus());
    }
}
