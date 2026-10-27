package group5.example.sportCenter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Central scheduling switch for all project @Scheduled jobs. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sportcenter.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
