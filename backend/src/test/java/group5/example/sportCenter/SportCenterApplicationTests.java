package group5.example.sportCenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("phase1")
class SportCenterApplicationTests {
	@Autowired private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void schedulingRemainsEnabledByDefaultAndRegistersAllProjectJobs() {
		var processors = context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class);
		assertEquals(1, processors.size());
		assertEquals(3, processors.values().iterator().next().getScheduledTasks().size());
	}

}
