package wot.metercircuitbreaker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Motion sensor (port 8084). */
@SpringBootApplication
@EnableScheduling
public class MeterCircuitBreakerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeterCircuitBreakerApplication.class, args);
    }
}
