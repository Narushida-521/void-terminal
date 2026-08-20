package com.nxd.voidterminal.model;

import java.time.Instant;

public record NodeSnapshot(
        String nodeId,
        String nodeName,
        String hostname,
        Instant reportedAt,
        SystemMetrics metrics,
        LatencyMetrics latency,
        StaticSystemInfo staticInfo
) {}
