package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.scheduling.annotation.EnableScheduling;
import group5.example.sportCenter.SportCenterApplication;
import group5.example.sportCenter.config.PtBookingSchedulingConfiguration;

import group5.example.sportCenter.scheduler.MembershipScheduler;

@SpringBootTest(properties = "sportcenter.scheduling.enabled=false")
@ActiveProfiles("phase1")
class SchedulingDisabledIntegrationTest {
    @Autowired private ApplicationContext context;

    @Test
    void falseRemovesScheduledInfrastructureWithoutRemovingBusinessServices() {
        assertFalse(SportCenterApplication.class.isAnnotationPresent(EnableScheduling.class));
        assertFalse(PtBookingSchedulingConfiguration.class.isAnnotationPresent(EnableScheduling.class));
        assertTrue(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty(),
                "@Scheduled processor must not be registered when scheduling is disabled");
        int registeredTasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .mapToInt(holder -> holder.getScheduledTasks().size()).sum();
        assertEquals(0, registeredTasks, "No project scheduled task may be registered");
        assertFalse(context.getBeansOfType(MembershipScheduler.class).isEmpty());
        assertFalse(context.getBeansOfType(CourseRegistrationExpirationService.class).isEmpty());
        assertFalse(context.getBeansOfType(PtBookingExpirationService.class).isEmpty());
        assertFalse(context.getBeansOfType(TrainingService.class).isEmpty());
    }
}
