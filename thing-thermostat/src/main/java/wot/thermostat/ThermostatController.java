package wot.thermostat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/{name}. */
@RestController
public class ThermostatController {

    private final Map<String, Object> state = new ConcurrentHashMap<>(Map.of("mode", "off", "temperature", 18.0, "target", 19.0));
    private final GatewayClient gateway;
    private final ArrayList<String> modes = new ArrayList<String>(Arrays.asList(
    		"off",
            "heat",
            "eco"));;

    public ThermostatController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return ThermostatDescription.model();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties() {
        return state;
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return ResponseEntity.ok(Map.of("name", name, "value", state.get(name)));
    }

    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name, @RequestBody Map<String, Object> body) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        Object value = body.get("value");
        if ("temperature".equals(name) || "target".equals(name)) {
            if (!(value instanceof Number)) {
                return error(HttpStatus.BAD_REQUEST, "property"+name+"expects a numeric value");
            }
        }
        else if ("mode".equals(name)) {
            if (!modes.contains(value)) {
                return error(HttpStatus.BAD_REQUEST, "mode must be 'off', 'heat', or 'eco'");
            }
        }
        set(name, value);
        return ResponseEntity.ok(Map.of("name", name, "value", value));
    }

    @PostMapping("/actions/setTarget")
    public ResponseEntity<Map<String, Object>> setTarget(@RequestBody Map<String, Object> body) {
        Object value = body.get("target");
        if (!(value instanceof Number)) {
            return error(HttpStatus.BAD_REQUEST,"action setTarget expects numeric value");
        }
        float target = ((Number) value).floatValue();
        // TODO maybe later : limit target
        /*if (...) {
            return error(HttpStatus.BAD_REQUEST, "...");
        }*/
        set("target", target);
        return ResponseEntity.ok(Map.of("action", "setTarget", "status", "completed", "properties", state));
    }

    private void set(String name, Object value) {
        state.put(name, value);
        gateway.emit("propertyChanged", Map.of("property", name, "value", value));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
