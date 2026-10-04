package group5.example.sportCenter.service;

import group5.example.sportCenter.repository.MembershipRepository;

public class MembershipService {
    private final MembershipRepository membershipRepository;

    public MembershipService(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }
}
