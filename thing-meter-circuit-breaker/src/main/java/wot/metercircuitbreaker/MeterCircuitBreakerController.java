package wot.metercircuitbreaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/{name}. */
@RestController
public class MeterCircuitBreakerController {

	private final Map<String, Object> state = new ConcurrentHashMap<>(Map.of("live", true, "energyConsumed", (float)0, "instConsumption", (float)0));
    private final GatewayClient gateway;

    public MeterCircuitBreakerController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public static Map<String, Object> model() {
        return MeterCircuitBreakerController.model();
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
        if ("live".equals(name)) {
            if (!(value instanceof Boolean)) {
                return error(HttpStatus.BAD_REQUEST, "property live expects a boolean {\"value\": true|false}");
            }
        }
        if ("energyConsumed".equals(name) || "instConsumption".equals(name)) {
            if (!(value instanceof Number)) {
                return error(HttpStatus.BAD_REQUEST, "property"+name+"expects a numeric value");
            }
        }
        set(name, value);
        return ResponseEntity.ok(Map.of("name", name, "value", value));
    }

    @PostMapping("/actions/toggle")
    public Map<String, Object> toggle() {
        set("live", !(Boolean) state.get("live"));
        gateway.emit("breakerToggled", state);
        return Map.of("action", "toggle", "status", "completed", "properties", state);
    }

    private void set(String name, Object value) {
        state.put(name, value);
        gateway.emit("propertyChanged", Map.of("property", name, "value", value));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
    
    // power of the devices (consumption per second in energy units)
    @Value("${power.motion}")
    private double power_motion;
    @Value("${power.lamp}")
    private double power_lamp;
    @Value("${power.eco-mode}")
    private double power_eco_mode;
    @Value("${power.heat-mode}")
    private double power_heat_mode;
    
    @Scheduled(fixedDelay = 1000)
    public void measureConsumption() {
        if(!(boolean) state.get("live")) {
        	set("instConsumption", 0);
        	return;
        }
        
        float instConsumption = 0;
        
        instConsumption += power_motion; // uses energy even when no motion is detected
        
        // Check property 'on' of lamp
        Object lamp_on = gateway.getProperty("lamp", "on");
        if(lamp_on != null && (boolean)lamp_on) instConsumption += power_lamp;

        // Check property 'mode' of thermostat
        Object thermostat_mode = gateway.getProperty("thermostat", "mode");
        if(thermostat_mode != null) {
        	if(thermostat_mode.equals("heat")) instConsumption += power_heat_mode;
        	else if(thermostat_mode.equals("eco")) instConsumption += power_eco_mode;
        }
        
        float energyConsumed = (float) state.get("energyConsumed") + instConsumption;
        
        set("instConsumption", instConsumption);
        set("energyConsumed", energyConsumed);
    }
}
