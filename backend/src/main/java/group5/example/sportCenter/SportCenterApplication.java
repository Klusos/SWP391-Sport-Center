package group5.example.sportCenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SportCenterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SportCenterApplication.class, args);
	}

}
