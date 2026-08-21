package com.nxd.voidterminal.server.api;

import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@RestController
public class NodeQueryController {
    private final NodeRegistry registry;

    public NodeQueryController(NodeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/nodes")
    public List<NodeView> list() {
        return registry.list();
    }

    @GetMapping("/api/nodes/{id}")
    public NodeView get(@PathVariable String id) {
        return registry.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/api/nodes/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<List<NodeView>>> streamAll() {
        return Flux.interval(Duration.ofSeconds(1))
                .startWith(0L)
                .map(tick -> ServerSentEvent.<List<NodeView>>builder(registry.list()).event("nodes").build());
    }

    @GetMapping(value = "/api/nodes/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NodeView>> streamOne(@PathVariable String id) {
        if (registry.find(id).isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return Flux.interval(Duration.ofSeconds(1))
                .startWith(0L)
                .handle((tick, sink) -> registry.find(id).ifPresentOrElse(
                        view -> sink.next(ServerSentEvent.<NodeView>builder(view).event("snapshot").build()),
                        sink::complete));
    }
}
