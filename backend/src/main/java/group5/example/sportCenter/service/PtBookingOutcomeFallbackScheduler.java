package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import group5.example.sportCenter.repository.PersonalTrainingBookingRepository;

@Component
@ConditionalOnProperty(prefix = "sportcenter.pt.outcome-fallback", name = "enabled", havingValue = "true")
public class PtBookingOutcomeFallbackScheduler {
    private static final Logger log = LoggerFactory.getLogger(PtBookingOutcomeFallbackScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int BATCH_SIZE = 100;
    private final PersonalTrainingBookingRepository bookings;
    private final PtBookingOutcomeService outcomes;
    private final Clock clock;
    private final String rolloutCutover;
    private final boolean globalSchedulingEnabled;

    public PtBookingOutcomeFallbackScheduler(PersonalTrainingBookingRepository bookings,
            PtBookingOutcomeService outcomes, Clock clock,
            @Value("${sportcenter.pt.outcome-fallback.rollout-cutover:}") String rolloutCutover,
            @Value("${sportcenter.scheduling.enabled:true}") boolean globalSchedulingEnabled) {
        this.bookings = bookings;
        this.outcomes = outcomes;
        this.clock = clock;
        this.rolloutCutover = rolloutCutover;
        this.globalSchedulingEnabled = globalSchedulingEnabled;
    }

    @Scheduled(fixedDelayString = "${sportcenter.pt.outcome-fallback.interval-ms}")
    public int completeDueOutcomes() {
        if (!globalSchedulingEnabled) return 0;
        LocalDateTime cutover = parseCutover();
        if (cutover == null) return 0;
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        var ids = bookings.findOutcomeFallbackCandidateIds(cutover, now.minusHours(24), PageRequest.of(0, BATCH_SIZE));
        int changed = 0;
        for (Integer id : ids) {
            if (outcomes.autoComplete(id, cutover, now)) changed++;
        }
        return changed;
    }

    private LocalDateTime parseCutover() {
        if (rolloutCutover == null || rolloutCutover.isBlank()) {
            log.warn("PT outcome fallback remains inactive because rolloutCutover is not configured");
            return null;
        }
        try {
            return LocalDateTime.parse(rolloutCutover.trim());
        } catch (DateTimeParseException exception) {
            log.error("PT outcome fallback remains inactive because rolloutCutover is invalid; expected an Asia/Ho_Chi_Minh local date-time", exception);
            return null;
        }
    }
}
