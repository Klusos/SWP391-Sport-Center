package group5.example.sportCenter.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import group5.example.sportCenter.service.MembershipService;
import group5.example.sportCenter.service.MembershipRenewalService;

@Component
@ConditionalOnProperty(name = "app.scheduled-jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MembershipScheduler {

    private final MembershipService membershipService;
    private final MembershipRenewalService membershipRenewalService;

    public MembershipScheduler(MembershipService membershipService,
            MembershipRenewalService membershipRenewalService) {
        this.membershipService = membershipService;
        this.membershipRenewalService = membershipRenewalService;
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

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void createRenewalRequests() {
        int created = membershipRenewalService.createDueRenewalRequests();
        System.out.println("Membership renewal request check completed. " + created + " request(s) created.");
    }
}
