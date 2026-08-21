package com.nxd.voidterminal.agent.schedule;

import com.nxd.voidterminal.agent.AgentProperties;
import com.nxd.voidterminal.agent.collect.MetricsCollector;
import com.nxd.voidterminal.agent.ping.PingService;
import com.nxd.voidterminal.agent.report.SnapshotReporter;
import com.nxd.voidterminal.model.LatencyMetrics;
import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.StaticSystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
public class ReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);
    private static final Duration STATIC_REFRESH = Duration.ofSeconds(60);

    private final AgentProperties properties;
    private final MetricsCollector collector;
    private final PingService pingService;
    private final SnapshotReporter reporter;

    private Instant lastStaticAt;
    private String lastHostname;

    public ReportScheduler(
            AgentProperties properties,
            MetricsCollector collector,
            PingService pingService,
            SnapshotReporter reporter) {
        this.properties = properties;
        this.collector = collector;
        this.pingService = pingService;
        this.reporter = reporter;
    }

    @Scheduled(fixedRateString = "${voidterminal.agent.report-interval}")
    public void scheduledReport() {
        reportOnce().block();
    }

    public Mono<Void> reportOnce() {
        Instant now = Instant.now();
        boolean includeStatic = lastStaticAt == null || Duration.between(lastStaticAt, now).compareTo(STATIC_REFRESH) >= 0;
        Mono<StaticSystemInfo> staticMono = includeStatic
                ? collector.collectStaticInfo()
                : Mono.empty();

        return Mono.zip(
                        collector.collectMetrics(),
                        pingService.pingHost(properties.ping().unicom()).onErrorReturn(-1.0),
                        pingService.pingHost(properties.ping().mobile()).onErrorReturn(-1.0),
                        pingService.pingHost(properties.ping().telecom()).onErrorReturn(-1.0),
                        staticMono.singleOptional()
                )
                .flatMap(tuple -> {
                    StaticSystemInfo staticInfo = tuple.getT5().orElse(null);
                    if (staticInfo != null) {
                        lastStaticAt = Instant.now();
                        lastHostname = staticInfo.hostName();
                    }
                    String hostname = lastHostname != null ? lastHostname : properties.nodeId();
                    NodeSnapshot snapshot = new NodeSnapshot(
                            properties.nodeId(),
                            properties.resolvedName(),
                            hostname,
                            Instant.now(),
                            tuple.getT1(),
                            new LatencyMetrics(tuple.getT2(), tuple.getT3(), tuple.getT4()),
                            staticInfo);
                    return reporter.report(snapshot);
                })
                .onErrorResume(ex -> {
                    log.error("Metrics collection failed; skip this report", ex);
                    return Mono.empty();
                });
    }
}
