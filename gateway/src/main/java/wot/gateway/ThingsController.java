package wot.gateway;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Registry of the things (in memory). */
@RestController
@RequestMapping("/things")
public class ThingsController {

    public record Thing(String id, String name, String baseUrl, Map<String, Object> model) {
    }

    private static final String MANUAL_SOURCE = "dashboard-manual";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ThingsController.class);

    private final Map<String, Thing> things = new ConcurrentHashMap<>();
    private final EventHub hub;
    private final RulesState state;
    private final RestClient http = RestClient.create();

    public ThingsController(EventHub hub, RulesState state) {
        this.hub = hub;
        this.state = state;
    }

    @PostMapping
    public ResponseEntity<Thing> register(@RequestBody Thing thing) {
        if (thing.id() == null || thing.id().isBlank() || thing.baseUrl() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields id and baseUrl are required");
        }
        if (things.putIfAbsent(thing.id(), thing) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "thing " + thing.id() + " is already registered");
        }
        hub.broadcast("registry", Map.of("type", "registered", "thingId", thing.id()));
        return ResponseEntity.created(URI.create("/things/" + thing.id())).body(thing);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return things.values().stream()
                .map(t -> Map.<String, Object>of("id", t.id(), "name", String.valueOf(t.name()),
                        "links", Map.of("self", "/things/" + t.id())))
                .toList();
    }

    @GetMapping("/{id}")
    public Thing get(@PathVariable String id) {
        return find(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        find(id);
        things.remove(id);
        hub.broadcast("registry", Map.of("type", "unregistered", "thingId", id));
        return ResponseEntity.noContent().build();
    }

    public Thing find(String id) {
        Thing thing = things.get(id);
        if (thing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "thing " + id + " is not registered");
        }
        return thing;
    }


    @GetMapping("/{id}/properties")
    public ResponseEntity<String> properties(@PathVariable String id) {
        return call(id, HttpMethod.GET, "/properties", null);
    }

    @GetMapping("/{id}/properties/{name}")
    public ResponseEntity<String> readProperty(@PathVariable String id, @PathVariable String name) {
        return call(id, HttpMethod.GET, "/properties/" + name, null);
    }

    @PutMapping("/{id}/properties/{name}")
    public ResponseEntity<String> putProperty(@PathVariable String id, @PathVariable String name,
                                              @RequestBody(required = false) String body,
                                              @RequestHeader(value = "X-Source", required = false) String source) {
        ResponseEntity<String> response = writeProperty(id, name, body);
        if (MANUAL_SOURCE.equals(source) && response.getStatusCode().is2xxSuccessful()) {
            markManual(id, name, body);
        }
        return response;
    }

    public ResponseEntity<String> writeProperty(String id, String name, String body) {
        return call(id, HttpMethod.PUT, "/properties/" + name, body);
    }

    @PostMapping("/{id}/actions/{name}")
    public ResponseEntity<String> invokeAction(@PathVariable String id, @PathVariable String name,
                                               @RequestBody(required = false) String body) {
        return call(id, HttpMethod.POST, "/actions/" + name, body);
    }

    private void markManual(String id, String name, String body) {
        try {
            JsonNode value = MAPPER.readTree(body).get("value");
            if (value == null) return;

            if ("lamp".equals(id) && "on".equals(name)) {
                boolean on = value.asBoolean();
                state.setLampManual(on);
                state.setLampOnByR1(false);
            }
            if ("thermostat".equals(id) && "mode".equals(name)) {
                state.setThermostatManual(!"off".equals(value.asText()));
            }
        } catch (Exception e) {
            log.warn("Could not read manual write {}/{}: {}", id, name, e.getMessage());
        }
    }

    private ResponseEntity<String> call(String id, HttpMethod method, String path, String body) {
        Thing thing = find(id);
        try {
            RestClient.RequestBodySpec request = http.method(method).uri(thing.baseUrl() + path);
            if (body != null && !body.isBlank()) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return request.exchange((req, res) -> ResponseEntity
                    .status(res.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "thing " + id + " does not answer");
        }
    }
}