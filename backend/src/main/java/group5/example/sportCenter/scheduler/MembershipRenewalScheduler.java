package group5.example.sportCenter.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import group5.example.sportCenter.service.MembershipRenewalLifecycleService;

/** Renewal catch-up is separately gated because older databases may not yet have Renewal schema. */
@Component
@ConditionalOnProperty(prefix = "sportcenter.renewal.scheduler", name = "enabled", havingValue = "true")
public class MembershipRenewalScheduler {
    private final MembershipRenewalLifecycleService lifecycle;

    public MembershipRenewalScheduler(MembershipRenewalLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        lifecycle.runDueLifecycle();
    }

    @Scheduled(cron = "${sportcenter.renewal.scheduler.cron}", zone = "${sportcenter.renewal.scheduler.time-zone}")
    public void catchUpPeriodically() {
        lifecycle.runDueLifecycle();
    }
}
