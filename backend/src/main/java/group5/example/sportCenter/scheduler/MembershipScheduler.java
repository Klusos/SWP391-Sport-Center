package group5.example.sportCenter.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import group5.example.sportCenter.service.MembershipAutoRenewalService;
import group5.example.sportCenter.service.MembershipCancellationRequestService;
import group5.example.sportCenter.service.MembershipService;

@Component
public class MembershipScheduler {

    private final MembershipService membershipService;
    private final MembershipAutoRenewalService autoRenewalService;
    private final MembershipCancellationRequestService cancellationRequestService;

    public MembershipScheduler(MembershipService membershipService, MembershipAutoRenewalService autoRenewalService,
            MembershipCancellationRequestService cancellationRequestService) {
        this.membershipService = membershipService;
        this.autoRenewalService = autoRenewalService;
        this.cancellationRequestService = cancellationRequestService;
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

        int expiredRequests = cancellationRequestService.expireUnapprovable();
        System.out.println(
                "Membership cancellation request check completed. "
                + expiredRequests
                + " request(s) expired."
        );

        // After expiry, so unpaid renewals of memberships that just ended are cancelled first.
        MembershipAutoRenewalService.RunResult renewal = autoRenewalService.runDaily();
        System.out.println(
                "Membership auto-renewal completed. "
                + renewal.created() + " renewal request(s) created, "
                + renewal.cancelled() + " unpaid request(s) cancelled."
        );
    }
}
