package com.nxd.voidterminal.model;

import java.time.Instant;

public record NodeView(
        String nodeId,
        String nodeName,
        String hostname,
        NodeStatus status,
        Instant lastSeenAt,
        SystemMetrics metrics,
        LatencyMetrics latency,
        StaticSystemInfo staticInfo
) {}
