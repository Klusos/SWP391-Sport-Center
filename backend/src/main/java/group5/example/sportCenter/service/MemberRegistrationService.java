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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

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

        member.setName(request.getName().trim());
        member.setEmail(email);
        member.setDateOfBirth(request.getDateOfBirth());
        member.setGender(request.getGender());
        member.setPhone(request.getPhone());
        return response(userRepository.save(member));
    }

    @Transactional
    public MemberResponse deactivateMember(Integer id) {
        User member = userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Member not found"));
        requireMember(member);
        member.setStatus("INACTIVE");
        return response(userRepository.save(member));
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
