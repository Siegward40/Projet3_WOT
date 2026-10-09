package wot.metercircuitbreaker;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the Meter-Circuit Breaker (GET /model). */
public final class MeterCircuitBreakerDescription {

    public static final String ID = "meter-circuit-breaker";

    private MeterCircuitBreakerDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "Meter-Circuit Breaker");
        model.put("description", "Device serving as both a meter and a circuit breaker");
        model.put("properties", Map.of(
        		"live", Map.of("type", "boolean", "description", "True when the devices are powered by electricity"),
                "energyConsumed", Map.of("type", "float", "description", "Total energy units consumed by all devices"),
                "instConsumption", Map.of("type", "float", "description", "Instantaneous consumption in energy units by all devices")
        ));
        model.put("actions", Map.of(
                "toggle", Map.of("description", "Cut or restore power to all devices")
        ));
        model.put("events", Map.of(
        		"breakerToggled", Map.of("description", "the power was cut off or restored"),
                "propertyChanged", Map.of("description", "a property changed: {\"property\": ..., \"value\": ...}")
        ));
        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "toggle", "/actions/toggle"));
        return model;
    }
}

