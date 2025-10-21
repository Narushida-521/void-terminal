package com.nxd.voidterminal.service.impl;

import com.nxd.voidterminal.config.OnPosixCondition;
import com.nxd.voidterminal.config.PingConfig;
import com.nxd.voidterminal.service.PingService;
import com.zaxxer.ping.IcmpPinger;
import com.zaxxer.ping.PingTarget;
import lombok.Getter;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Service
@Conditional(OnPosixCondition.class) // ！！！只在 Linux/macOS 上加载此 Bean！！！
public class PingServiceImpl implements PingService {
    private final IcmpPinger pinger;
    private final ConcurrentHashMap<InetAddress, MonoSink<Double>> pendingRequests;

    public PingServiceImpl(IcmpPinger pinger, PingConfig pingconfig) {
        System.out.println("--- 成功加载 [Linux/macOS] 版本的 PingService ---");
        this.pinger = pinger;
        this.pendingRequests = pingconfig.getPendingRequests();
    }


    @Override
    public Mono<Double> pingHost(String host) {
        return Mono.create((MonoSink<Double> sink) -> {
                    try {
                        InetAddress address = InetAddress.getByName(host);
                        PingTarget target = new PingTarget(address);
                        // "挂号"
                        pendingRequests.put(address, sink);
                        // 设置清理回调
                        sink.onCancel(() -> {
                            pendingRequests.remove(address);
                        });
                        // 提交ping请求(非阻塞)
                        pinger.ping(target);
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }).timeout(Duration.ofSeconds(5))
                .doOnError(e -> {
                    try {
                        pendingRequests.remove(InetAddress.getByName(host));
                    } catch (Exception ex) {
                    }
                });
    }
}
