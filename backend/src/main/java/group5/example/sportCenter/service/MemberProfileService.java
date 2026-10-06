package group5.example.sportCenter.service;

import org.springframework.stereotype.Service;

import group5.example.sportCenter.dto.CreateMemberProfileRequest;
import group5.example.sportCenter.dto.MemberProfileResponse;
import group5.example.sportCenter.dto.UpdateMemberProfileRequest;
import group5.example.sportCenter.entity.MemberProfile;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.repository.MemberProfileRepository;
import group5.example.sportCenter.repository.UserRepository;

@Service
public class MemberProfileService {

    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;

    public MemberProfileService(
            MemberProfileRepository memberProfileRepository,
            UserRepository userRepository) {

        this.memberProfileRepository = memberProfileRepository;
        this.userRepository = userRepository;
    }

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