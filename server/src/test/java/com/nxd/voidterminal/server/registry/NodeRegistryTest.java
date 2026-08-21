package com.nxd.voidterminal.server.registry;

import com.nxd.voidterminal.model.NodeStatus;
import com.nxd.voidterminal.server.ServerProperties;
import com.nxd.voidterminal.server.support.MutableClock;
import com.nxd.voidterminal.server.support.Snapshots;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryTest {

    @Test
    void acceptRegistersUnknownNodeOnline() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T12:00:00Z"));
        NodeRegistry registry = new NodeRegistry(new ServerProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), clock);

        registry.accept(Snapshots.snapshot("node-a", clock.instant(), Snapshots.staticInfo("host-a")));

        assertEquals(1, registry.list().size());
        assertEquals(NodeStatus.ONLINE, registry.find("node-a").orElseThrow().status());
        assertEquals("gpu", registry.find("node-a").orElseThrow().staticInfo().graphicsCardInfo().getFirst());
    }

    @Test
    void laterSnapshotWithoutStaticInfoKeepsCachedStaticInfo() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T12:00:00Z"));
        NodeRegistry registry = new NodeRegistry(new ServerProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), clock);
        registry.accept(Snapshots.snapshot("node-a", clock.instant(), Snapshots.staticInfo("host-a")));
        clock.advance(Duration.ofSeconds(1));
        registry.accept(Snapshots.snapshot("node-a", clock.instant(), null));

        assertEquals("host-a", registry.find("node-a").orElseThrow().staticInfo().hostName());
    }

    @Test
    void sweepMarksOfflineThenEvicts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T12:00:00Z"));
        NodeRegistry registry = new NodeRegistry(new ServerProperties(Duration.ofMillis(50), Duration.ofMillis(100)), clock);
        registry.accept(Snapshots.snapshot("node-a", clock.instant(), null));

        clock.advance(Duration.ofMillis(51));
        registry.sweep();
        assertEquals(NodeStatus.OFFLINE, registry.find("node-a").orElseThrow().status());
        assertEquals(Snapshots.metrics(), registry.find("node-a").orElseThrow().metrics());

        clock.advance(Duration.ofMillis(50));
        registry.sweep();
        assertTrue(registry.find("node-a").isEmpty());
        assertTrue(registry.list().isEmpty());
    }
}
