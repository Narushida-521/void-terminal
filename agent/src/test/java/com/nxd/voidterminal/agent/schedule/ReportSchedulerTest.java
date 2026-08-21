package com.nxd.voidterminal.agent.schedule;

import com.nxd.voidterminal.agent.AgentProperties;
import com.nxd.voidterminal.agent.collect.MetricsCollector;
import com.nxd.voidterminal.agent.ping.PingService;
import com.nxd.voidterminal.agent.report.SnapshotReporter;
import com.nxd.voidterminal.model.LatencyMetrics;
import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportSchedulerTest {

    @Test
    void firstTickIncludesStaticInfoAndFailedPingIsMinusOne() {
        SystemMetrics metrics = new SystemMetrics(2, 1, 1, 2, 50, 1, 2, 50, 0, 0);
        StaticSystemInfo info = new StaticSystemInfo("os", new String[]{}, "h", "c", "a", "b", "s", "m", List.of());
        MetricsCollector collector = new MetricsCollector() {
            @Override
            public Mono<SystemMetrics> collectMetrics() {
                return Mono.just(metrics);
            }

            @Override
            public Mono<StaticSystemInfo> collectStaticInfo() {
                return Mono.just(info);
            }
        };
        PingService ping = host -> "bad".equals(host) ? Mono.error(new RuntimeException("fail")) : Mono.just(9.0);
        AtomicReference<NodeSnapshot> posted = new AtomicReference<>();
        SnapshotReporter reporter = snapshot -> {
            posted.set(snapshot);
            return Mono.empty();
        };
        AgentProperties properties = new AgentProperties(
                "node-a", "", "http://localhost:8080", Duration.ofSeconds(1),
                new AgentProperties.Ping("ok", "bad", "ok"));
        ReportScheduler scheduler = new ReportScheduler(properties, collector, ping, reporter);
        StepVerifier.create(scheduler.reportOnce()).verifyComplete();
        assertNotNull(posted.get().staticInfo());
        assertEqualsLatency(posted.get().latency(), 9.0, -1.0, 9.0);
        StepVerifier.create(scheduler.reportOnce()).verifyComplete();
        assertNull(posted.get().staticInfo());
    }

    private static void assertEqualsLatency(LatencyMetrics latency, double u, double m, double t) {
        assertEquals(u, latency.unicom());
        assertEquals(m, latency.mobile());
        assertEquals(t, latency.telecom());
    }
}
