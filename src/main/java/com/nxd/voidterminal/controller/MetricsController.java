package com.nxd.voidterminal.controller;
import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import com.nxd.voidterminal.service.MetricsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono; // 确保导入 Mono

@RestController
public class MetricsController {
    // 将依赖声明为 final，保证不可变性
    private final MetricsService metricsService;
    // 使用构造函数注入，这是 Spring 官方推荐的唯一方式
    // 当 Spring 创建这个 Controller 的实例时，会自动寻找一个 MetricsService 的 Bean 并传进来
    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping(value = "/api/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SystemMetrics> streamMetrics() {
        return metricsService.getMetricsStream();
    }

    /**
     * 用于获取静态系统信息的 API 端点
     */
    @GetMapping("/api/info/static")
    public Mono<StaticSystemInfo> getStaticSystemInfo() {
        return metricsService.getStaticSystemInfo();
    }
}