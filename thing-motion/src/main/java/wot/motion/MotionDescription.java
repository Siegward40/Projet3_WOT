package wot.motion;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the Motion Sensor (GET /model). */
public final class MotionDescription {

    public static final String ID = "motion";

    private MotionDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "Motion Sensor");
        model.put("description", "Virtual motion sensor of the Smart Lab");
        model.put("properties", Map.of(
                "lastMotion", Map.of("type", "string", "readOnly", true,
                        "description", "ISO-8601 timestamp of the last detected motion (null if none yet)")
        ));
        model.put("actions", Map.of(
                "simulateMotion", Map.of("description", "simulates a motion detection (no body needed)")
        ));
        model.put("events", Map.of(
                "motion", Map.of("description", "motion detected: {\"lastMotion\": <timestamp>}")));
        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "simulateMotion", "/actions/simulateMotion"));
        return model;
    }
}
