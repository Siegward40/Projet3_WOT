package wot.thermostat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the Thermostat (GET /model). */
public final class ThermostatDescription {

    public static final String ID = "thermostat";

    private ThermostatDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "Thermostat");
        model.put("description", "Virtual thermostat of the Smart Lab");
        model.put("properties", Map.of(
                "temperature", Map.of("type", "float", "description", "temperature detected by the thermostat in °C"),
                "target", Map.of("type", "float", "description", "target temperature in °C"),
                "mode", Map.of("type", "string", "description", "heating consumption mode ∈ {off, heat, eco}"),
                "outdoorTemperature", Map.of("type", "float", "readOnly", true, "description", "Outdoor temperature")
        ));
        model.put("actions", Map.of(
                "setTarget", Map.of("description", "changes the thermostat's target temperature in °C")
        ));
        model.put("events", Map.of(
                "propertyChanged", Map.of("description", "a property changed: {\"property\": ..., \"value\": ...}")));
        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "setTarget", "/actions/setTarget"));
        return model;
    }
}
