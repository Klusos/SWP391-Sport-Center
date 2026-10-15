package group5.example.sportCenter.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import group5.example.sportCenter.dto.MembershipPlanResponse;
import group5.example.sportCenter.service.MembershipPlanService;

@RestController
@RequestMapping("/api/public/membership-plans")
public class PublicMembershipPlanController {

    private final MembershipPlanService membershipPlanService;

    public PublicMembershipPlanController(MembershipPlanService membershipPlanService) {
        this.membershipPlanService = membershipPlanService;
    }

    @GetMapping
    public List<MembershipPlanResponse> getActivePlans() {
        return membershipPlanService.getActivePlans();
    }
}
