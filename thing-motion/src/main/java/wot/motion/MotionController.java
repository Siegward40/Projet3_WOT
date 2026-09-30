package wot.motion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/{name}. */
@RestController
public class MotionController {

    private static final String LAST_MOTION = "lastMotion";

    // ISO-8601 timestamp of the last motion, null until the first one
    private volatile String lastMotion = null;
    private final GatewayClient gateway;

    public MotionController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return MotionDescription.model();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties() {
        return state();
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!LAST_MOTION.equals(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return ResponseEntity.ok(property(name, lastMotion));
    }

    // lastMotion is the only property and it is read-only
    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name) {
        if (!LAST_MOTION.equals(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return error(HttpStatus.BAD_REQUEST, "property " + name + " is read-only");
    }

    // no body: the sensor detects a motion now
    @PostMapping("/actions/simulateMotion")
    public ResponseEntity<Map<String, Object>> simulateMotion() {
        String timestamp = Instant.now().toString();
        lastMotion = timestamp;
        gateway.emit("motion", Map.of(LAST_MOTION, timestamp));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "simulateMotion");
        result.put("status", "completed");
        result.put("properties", state());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/actions/{name}")
    public ResponseEntity<Map<String, Object>> unknownAction(@PathVariable String name) {
        return error(HttpStatus.NOT_FOUND, "unknown action " + name);
    }

    private Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(LAST_MOTION, lastMotion);
        return state;
    }

    // Map.of refuses null values, lastMotion is null before the first motion
    private static Map<String, Object> property(String name, Object value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("value", value);
        return body;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
