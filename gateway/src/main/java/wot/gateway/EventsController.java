package wot.gateway;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** POST /events (from the things) and GET /events/stream (to the browsers). */
@RestController
@RequestMapping("/events")
public class EventsController {

    public record ThingEvent(String thingId, String type, Map<String, Object> data, String timestamp) {
    }

    private final EventHub hub;
    private final ThingsController things;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        if("motion".equals(event.type())){
            ResponseEntity<String> respEnt = things.readProperty("thermostat", "temperature");
            float temp;
            try {
                temp = MAPPER.readTree(respEnt.getBody()).get("value").floatValue();
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Réponse thermostat invalide", e);
            }
            things.writeProperty("lamp", "on", "{\"value\":true}");
            if(temp < 19.0){
                things.writeProperty("thermostat", "target", "{\"value\":19.0}");
                things.writeProperty("thermostat", "mode", "{\"value\":\"heat\"}");
            }
        }
        // TODO: rules R3, R4
        return ResponseEntity.accepted().build();
    }
}
