package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import group5.example.sportCenter.config.SchedulingConfiguration;

@SpringBootTest(properties = "sportcenter.scheduling.enabled=true")
@ActiveProfiles("phase1")
class SchedulingEnabledIntegrationTest {
    @Autowired private ApplicationContext context;

    @Test
    void explicitTrueRegistersAllThreeProjectJobs() {
        var processors = context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class);
        assertEquals(1, processors.size());
        assertEquals(3, processors.values().iterator().next().getScheduledTasks().size());
        assertFalse(context.getBeansOfType(SchedulingConfiguration.class).isEmpty());
    }
}
