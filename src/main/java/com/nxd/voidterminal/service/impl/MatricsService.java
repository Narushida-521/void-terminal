package com.nxd.voidterminal.service.impl;

import com.nxd.voidterminal.service.MetricsService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 指标服务 - 负责生成实时数据流
 * 在阶段一，我们只生成一个简单的、每秒递增的数字流用于测试。
 */
@Service
public class MatricsService implements MetricsService {
    /**
     * 返回心跳的数据流
     * Flux是一个可以发出0到N个元素的响应式流
     * return 一个Long类型的无限数据流,每秒发出一个新数字
     *
     */
    public Flux<Long> getHeartbeatStream() {
        return Flux.interval(Duration.ofSeconds(1));
    }
}
