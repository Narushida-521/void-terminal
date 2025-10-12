package com.nxd.voidterminal.service;

import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


/**
 * 指标服务接口
 * 定义了所有与指标相关的功能契约
 */
public interface MetricsService {
    Flux<SystemMetrics> getMetricsStream();
    Mono<StaticSystemInfo> getStaticSystemInfo();
}
