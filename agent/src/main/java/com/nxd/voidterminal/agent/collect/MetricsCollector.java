package com.nxd.voidterminal.agent.collect;

import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import reactor.core.publisher.Mono;

public interface MetricsCollector {
    Mono<SystemMetrics> collectMetrics();
    Mono<StaticSystemInfo> collectStaticInfo();
}
