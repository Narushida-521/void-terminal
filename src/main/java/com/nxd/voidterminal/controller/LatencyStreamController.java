package com.nxd.voidterminal.controller;

import com.nxd.voidterminal.service.PingService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 延迟监控流控制器 (SSE)
 * * 这个 Controller 专门用于向前端仪表盘推送实时的 Ping 延迟数据。
 * 它使用 Server-Sent Events (SSE) 技术。
 */
@RestController
public class LatencyStreamController {
    @Resource
    private PingService pingService;
    // 配置监控的目标IP
    private static final String UNICOM_HOST = "ha-cu-v4.ip.zstaticcdn.com";
    private static final String MOBILE_HOST = "ha-cm-v4.ip.zstaticcdn.com";
    private static final String TELECOM_HOST = "222.88.88.88";

    @GetMapping(value = "/api/latency/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Double>> getLatencyStream() {
        // 创建一个数据流 它会每2秒进行请求
        return Flux.interval(Duration.ofSeconds(2))
                .flatMap(tick -> {
                    System.out.println("后端正在执行第" + (tick + 1) + "次ping测试");
                    // 创建三个并行的任务 创建三个Mono<Double> 如果失败 返回-1 不会让整个流崩溃
                    Mono<Double> unicomPing = pingService.pingHost(UNICOM_HOST)
                            .onErrorReturn(-1.0);
                    Mono<Double> mobilePing = pingService.pingHost(MOBILE_HOST)
                            .onErrorReturn(-1.0);
                    Mono<Double> telecomPing = pingService.pingHost(TELECOM_HOST)
                            .onErrorReturn(-1.0);
                    // 组合结果
                    return Mono.zip(unicomPing, mobilePing, telecomPing)
                            // 格式化为json
                            .map(tuple -> Map.of(
                                    "unicom", tuple.getT1(),
                                    "mobile", tuple.getT2(),
                                    "telecom", tuple.getT3()
                            ));
                });
    }
    @GetMapping("/api/ping/test")
    public Mono<String> pingTest() {
        return Mono.just("Pong!");
    }
}
