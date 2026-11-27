package group5.example.sportCenter.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import group5.example.sportCenter.service.ClassSessionCompletionService;

/** Catches up elapsed classes at startup and then checks every five minutes. */
@Component
@ConditionalOnProperty(prefix = "sportcenter.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClassSessionCompletionScheduler {
    private final ClassSessionCompletionService completion;

    public ClassSessionCompletionScheduler(ClassSessionCompletionService completion) {
        this.completion = completion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        completion.completeElapsedScheduledSessions();
    }

    @Scheduled(fixedDelayString = "${sportcenter.class-session-completion.interval-ms:300000}")
    public void completeElapsedSessions() {
        completion.completeElapsedScheduledSessions();
    }
}
