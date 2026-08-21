package com.nxd.voidterminal.server.ingest;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SnapshotIngestController {
    private final NodeRegistry registry;

    public SnapshotIngestController(NodeRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/internal/nodes/{id}/snapshot")
    public ResponseEntity<Void> ingest(@PathVariable String id, @RequestBody NodeSnapshot snapshot) {
        if (snapshot == null
                || snapshot.nodeId() == null
                || snapshot.nodeId().isBlank()
                || !id.equals(snapshot.nodeId())
                || snapshot.metrics() == null
                || snapshot.latency() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        registry.accept(snapshot);
        return ResponseEntity.noContent().build();
    }
}
