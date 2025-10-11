package com.nxd.voidterminal.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


/**
 * 指标服务接口
 * 定义了所有与指标相关的功能契约
 */
public interface MetricsService {
    /**
     * 获取一个“心跳”数据流。
     * @return 一个 Long 类型的无限数据流。
     */
    Flux<Long> getHeartbeatStream();
}
