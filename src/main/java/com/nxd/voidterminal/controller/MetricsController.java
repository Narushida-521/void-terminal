package com.nxd.voidterminal.controller;

import com.nxd.voidterminal.service.impl.MatricsService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class MetricsController {
    @Resource
    private MatricsService matricsService;

    @GetMapping(value = "/api/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Long> streamHeartbeat() {
        // 直接调用service的方法 获取数据流 并直接返回
        return matricsService.getHeartbeatStream();
    }
}
