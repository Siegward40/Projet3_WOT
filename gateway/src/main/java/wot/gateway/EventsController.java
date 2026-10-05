package wot.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;

/** POST /events (from the things) and GET /events/stream (to the browsers). */
@RestController
@RequestMapping("/events")
public class EventsController {

    public record ThingEvent(String thingId, String type, Map<String, Object> data, String timestamp) {
    }

    private final EventHub hub;
    private final ThingsController things;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private volatile boolean lampOnByR1 = false;
    
    // for R4
    private static boolean block_rules = false;
    private static Instant downtime = null;
    public static void manualOverride() {
    	block_rules = true;
    	downtime = Instant.now();
    }

    public EventsController(EventHub hub, ThingsController things) {
        this.hub = hub;
        this.things = things;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.subscribe();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody ThingEvent event) {
        if (event.thingId() == null || event.type() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields thingId and type are required");
        }
        things.find(event.thingId()); // 404 if unknown
        hub.broadcast("thing", event);
        // R1 and R2
        if("motion".equals(event.type()) && !block_rules){
            ResponseEntity<String> respEnt = things.readProperty("thermostat", "temperature", true);
            float temp;
            try {
                temp = MAPPER.readTree(respEnt.getBody()).get("value").floatValue();
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Réponse thermostat invalide", e);
            }
            things.writeProperty("lamp", "on", "{\"value\":true}", true);
            lampOnByR1 = true;
            if(temp < 19.0){
                things.writeProperty("thermostat", "target", "{\"value\":19.0}", true);
                things.writeProperty("thermostat", "mode", "{\"value\":\"heat\"}", true);
            }
        }
        // R2 - end
        if("targetReached".equals(event.type()) && !block_rules){
            things.writeProperty("thermostat", "mode", "{\"value\":\"off\"}", true);
        }
        return ResponseEntity.accepted().build();
    }

    private Instant readLastMotion() throws JsonProcessingException {
        String body = things.readProperty("motion", "lastMotion", true).getBody();
        JsonNode v = MAPPER.readTree(body).get("value");
        return (v == null || v.isNull()) ? null : Instant.parse(v.asText());
    }

    // base values in resources/application.yml
    @Value("${rules.t1-ms:60000}")
    private long t1Ms;
    @Value("${rules.t3-ms:300000}")
    private long t3Ms;
    @Value("${rules.t4-ms:600000}")
    private long t4Ms;

    @Scheduled(fixedDelay = 1000)
    public void checkTimers() {
        try {
            if (!lampOnByR1) return;
            Instant lastMotion = readLastMotion();
            // au bout de T1, on eteint la lampe si pas de motion depuis
            if (lastMotion != null
            		&& !block_rules
                    && Duration.between(lastMotion, Instant.now()).toMillis() >= t1Ms) {
                things.writeProperty("lamp", "on", "{\"value\":false}", true);
                lampOnByR1 = false;
            }
            // au bout de T3, on passe en mode eco et target 17 si pas de motion depuis
            if (lastMotion != null
            		&& !block_rules
                    && Duration.between(lastMotion, Instant.now()).toMillis() >= t3Ms) {
                things.writeProperty("thermostat", "mode", "{\"value\":\"eco\"}", true);
                things.writeProperty("thermostat", "target", "{\"value\":17.0}", true);
            }
            // au bout de T4
            if (block_rules && Duration.between(downtime, Instant.now()).toMillis() >= t4Ms) {
            	block_rules = false;
            }
        } catch (Exception e) {
            log.warn("R1 check failed: {}", e.getMessage());
        }
    }
}
