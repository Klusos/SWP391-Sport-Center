package group5.example.sportCenter.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.CreateMemberProfileRequest;
import group5.example.sportCenter.dto.MemberProfileResponse;
import group5.example.sportCenter.dto.MemberProfileRequest;
import group5.example.sportCenter.dto.UpdateMemberProfileRequest;
import group5.example.sportCenter.entity.MemberProfile;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MemberProfileRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class MemberProfileService {

    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;

    public MemberProfileService(
            MemberProfileRepository memberProfileRepository,
            UserRepository userRepository) {

        this.memberProfileRepository = memberProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MemberProfileResponse createProfile(
            CreateMemberProfileRequest request) {

        User member = userRepository.findById(request.getMemberId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: "
                                        + request.getMemberId()));
        
        if (!"MEMBER".equalsIgnoreCase(member.getRole())) {
            throw new IllegalArgumentException(
                    "Only users with MEMBER role can have a member profile");
        }                                
        if (memberProfileRepository
                .existsByMemberId(request.getMemberId())) {

            throw new IllegalArgumentException(
                    "Member already has a profile");
        }

        MemberProfile profile = new MemberProfile();

        profile.setMember(member);
        profile.setHeight(request.getHeight());
        profile.setWeight(request.getWeight());
        profile.setGoal(request.getGoal());

        MemberProfile savedProfile =
                memberProfileRepository.save(profile);

        return toResponse(savedProfile);
    }

    @Transactional
    public MemberProfileResponse createMyProfile(
            Jwt jwt,
            MemberProfileRequest request) {

        Integer memberId;
        try {
            memberId = Integer.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token subject");
        }

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User no longer exists"));

        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive");
        }
        if (!"MEMBER".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only members can create a member profile");
        }
        if (memberProfileRepository.existsByMemberId(memberId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Member already has a profile");
        }

        MemberProfile profile = new MemberProfile();
        profile.setMember(member);
        profile.setHeight(request.height());
        profile.setWeight(request.weight());
        profile.setGoal(request.goal());

        try {
            return toResponse(memberProfileRepository.saveAndFlush(profile));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // The unique memberId constraint also protects concurrent requests.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Member already has a profile",
                    e);
        }
    }

    public MemberProfileResponse getMyProfile(Jwt jwt) {

        User member = getCurrentMember(jwt);
        MemberProfile profile = memberProfileRepository
                .findByMemberId(member.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member profile not found for current member"));

        return toResponse(profile);
    }

    @Transactional
    public MemberProfileResponse updateMyProfile(
            Jwt jwt,
            MemberProfileRequest request) {

        User member = getCurrentMember(jwt);
        MemberProfile profile = memberProfileRepository
                .findByMemberId(member.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Member profile not found for current member"));

        profile.setHeight(request.height());
        profile.setWeight(request.weight());
        profile.setGoal(request.goal().isEmpty() ? null : request.goal());

        return toResponse(memberProfileRepository.save(profile));
    }

    private User getCurrentMember(Jwt jwt) {

        Integer memberId;
        try {
            memberId = Integer.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token subject");
        }

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User no longer exists"));

        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is inactive");
        }
        if (!"MEMBER".equalsIgnoreCase(member.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only members can access their member profile");
        }

        return member;
    }

    public MemberProfileResponse getProfileById(Integer id) {

        MemberProfile profile =
                memberProfileRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Member profile not found with id: "
                                                + id));

        return toResponse(profile);
    }

    public MemberProfileResponse getProfileByMemberId(
            Integer memberId) {

        MemberProfile profile =
                memberProfileRepository.findByMemberId(memberId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Member profile not found for member id: "
                                                + memberId));

        return toResponse(profile);
    }

    @Transactional
    public MemberProfileResponse updateProfile(
            Integer id,
            UpdateMemberProfileRequest request) {

        MemberProfile profile =
                memberProfileRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Member profile not found with id: "
                                                + id));

        profile.setHeight(request.getHeight());
        profile.setWeight(request.getWeight());
        profile.setGoal(request.getGoal());

        MemberProfile updatedProfile =
                memberProfileRepository.save(profile);

        return toResponse(updatedProfile);
    }

    private MemberProfileResponse toResponse(
            MemberProfile profile) {

        return new MemberProfileResponse(
                profile.getId(),
                profile.getMember().getId(),
                profile.getMember().getName(),
                profile.getHeight(),
                profile.getWeight(),
                profile.getGoal(),
                profile.getUpdatedAt()
        );
    }
}
