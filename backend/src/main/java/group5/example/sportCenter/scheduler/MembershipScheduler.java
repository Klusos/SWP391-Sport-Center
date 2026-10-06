package group5.example.sportCenter.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import group5.example.sportCenter.service.MembershipService;

@Component
public class MembershipScheduler {

    private final MembershipService membershipService;

    public MembershipScheduler(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Scheduled(
        cron = "0 0 0 * * *",
        zone = "Asia/Ho_Chi_Minh"
    )
    public void expireMemberships() {

        int expiredCount =
                membershipService.expireMemberships();

        System.out.println(
                "Membership expiration check completed. "
                + expiredCount
                + " membership(s) expired."
        );
    }
}