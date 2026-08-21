package com.nxd.voidterminal.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NodeSnapshotTest {

    @Test
    void snapshotKeepsRequiredFieldsAndAllowsNullStaticInfo() {
        SystemMetrics metrics = new SystemMetrics(8, 12.5, 4.0, 16.0, 25.0, 100.0, 500.0, 20.0, 1.2, 0.4);
        LatencyMetrics latency = new LatencyMetrics(10.0, -1.0, 8.5);
        Instant reportedAt = Instant.parse("2026-08-20T15:00:00Z");

        NodeSnapshot snapshot = new NodeSnapshot(
                "node-a", "Alpha", "host-a", reportedAt, metrics, latency, null);

        assertEquals("node-a", snapshot.nodeId());
        assertEquals(metrics, snapshot.metrics());
        assertEquals(-1.0, snapshot.latency().mobile());
        assertNull(snapshot.staticInfo());

        NodeView view = new NodeView(
                snapshot.nodeId(),
                snapshot.nodeName(),
                snapshot.hostname(),
                NodeStatus.ONLINE,
                reportedAt,
                metrics,
                latency,
                null);
        assertEquals(NodeStatus.ONLINE, view.status());
        assertEquals(List.of(), new StaticSystemInfo(
                "os", new String[]{"1.1.1.1"}, "host", "cpu", "arch", "board", "sys", "mem", List.of()).graphicsCardInfo());
    }
}
