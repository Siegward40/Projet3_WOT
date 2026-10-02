package wot.gateway;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Registry of the things (in memory). */
@RestController
@RequestMapping("/things")
public class ThingsController {

    public record Thing(String id, String name, String baseUrl, Map<String, Object> model) {
    }

    private final Map<String, Thing> things = new ConcurrentHashMap<>();
    private final EventHub hub;
    private final RestClient http = RestClient.create();

    public ThingsController(EventHub hub) {
        this.hub = hub;
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
    public ResponseEntity<String> writeProperty(@PathVariable String id, @PathVariable String name,
                                                @RequestBody(required = false) String body) {
        return call(id, HttpMethod.PUT, "/properties/" + name, body);
    }
    @PostMapping("/{id}/actions/{name}")
    public ResponseEntity<String> invokeAction(@PathVariable String id, @PathVariable String name,
                                               @RequestBody(required = false) String body) {
        return call(id, HttpMethod.POST, "/actions/" + name, body);
    }

    // TODO (R4, bonus): POST /things/{id}/automation/resume

    /**
     * Forwards the request to the thing and returns its answer unchanged (status code and JSON body),
     * so that the thing's 200/400/404 reach the client. If the thing cannot be reached: 502.
     */
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
