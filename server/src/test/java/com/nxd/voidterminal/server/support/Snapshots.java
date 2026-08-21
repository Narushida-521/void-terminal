package com.nxd.voidterminal.server.support;

import com.nxd.voidterminal.model.LatencyMetrics;
import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;

import java.time.Instant;
import java.util.List;

public final class Snapshots {
    private Snapshots() {}

    public static SystemMetrics metrics() {
        return new SystemMetrics(4, 10.0, 2.0, 8.0, 25.0, 50.0, 200.0, 25.0, 0.5, 0.2);
    }

    public static LatencyMetrics latency() {
        return new LatencyMetrics(12.0, 13.0, 14.0);
    }

    public static StaticSystemInfo staticInfo(String host) {
        return new StaticSystemInfo("os", new String[]{"8.8.8.8"}, host, "cpu", "arch", "board", "sys", "mem", List.of("gpu"));
    }

    public static NodeSnapshot snapshot(String id, Instant at, StaticSystemInfo staticInfo) {
        return new NodeSnapshot(id, id, id + "-host", at, metrics(), latency(), staticInfo);
    }
}
