package com.nxd.voidterminal.server.registry;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.server.ServerProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NodeRegistry {
    private final ConcurrentHashMap<String, NodeRecord> nodes = new ConcurrentHashMap<>();
    private final ServerProperties properties;
    private final Clock clock;

    public NodeRegistry(ServerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void accept(NodeSnapshot snapshot) {
        Instant now = clock.instant();
        nodes.compute(snapshot.nodeId(), (id, existing) -> {
            if (existing == null) {
                return new NodeRecord(snapshot, now);
            }
            existing.update(snapshot, now);
            return existing;
        });
    }

    public List<NodeView> list() {
        return nodes.values().stream().map(NodeRecord::toView).toList();
    }

    public Optional<NodeView> find(String nodeId) {
        NodeRecord record = nodes.get(nodeId);
        return record == null ? Optional.empty() : Optional.of(record.toView());
    }

    public void sweep() {
        Instant now = clock.instant();
        nodes.forEach((id, record) -> {
            Duration age = Duration.between(record.lastSeenAt(), now);
            if (age.compareTo(properties.evictAfter()) >= 0) {
                nodes.remove(id, record);
            } else if (age.compareTo(properties.offlineAfter()) >= 0) {
                record.markOffline();
            }
        });
    }
}
