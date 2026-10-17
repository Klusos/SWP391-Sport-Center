package group5.example.sportCenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import group5.example.sportCenter.config.ImageStorageProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ImageStorageProperties.class)
public class SportCenterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SportCenterApplication.class, args);
	}

}
