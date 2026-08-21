package com.nxd.voidterminal.agent.ping;

import com.zaxxer.ping.IcmpPinger;
import com.zaxxer.ping.PingTarget;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Conditional(OnPosixCondition.class)
public class PosixPingService implements PingService {
    private final IcmpPinger pinger;
    private final ConcurrentHashMap<InetAddress, MonoSink<Double>> pendingRequests;

    public PosixPingService(IcmpPinger pinger, PosixPingConfig pingConfig) {
        this.pinger = pinger;
        this.pendingRequests = pingConfig.getPendingRequests();
    }

    @Override
    public Mono<Double> pingHost(String host) {
        return Mono.<Double>create((MonoSink<Double> sink) -> {
                    try {
                        InetAddress address = InetAddress.getByName(host);
                        pendingRequests.put(address, sink);
                        sink.onCancel(() -> pendingRequests.remove(address));
                        pinger.ping(new PingTarget(address));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }).timeout(Duration.ofSeconds(5))
                .doOnError(e -> {
                    try {
                        pendingRequests.remove(InetAddress.getByName(host));
                    } catch (Exception ignored) {
                    }
                });
    }
}
