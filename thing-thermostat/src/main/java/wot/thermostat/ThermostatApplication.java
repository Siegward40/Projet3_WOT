package wot.thermostat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Thermosta (port 8082). Copy this module for the thermostat (8081) and the motion sensor (8083). */
@SpringBootApplication
@EnableScheduling
public class ThermostatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThermostatApplication.class, args);
    }
}
