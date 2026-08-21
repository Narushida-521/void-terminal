package com.nxd.voidterminal.agent.ping;

import com.zaxxer.ping.FailureReason;
import com.zaxxer.ping.IcmpPinger;
import com.zaxxer.ping.PingResponseHandler;
import com.zaxxer.ping.PingTarget;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.MonoSink;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Conditional(OnPosixCondition.class)
public class PosixPingConfig implements PingResponseHandler {
    private final ConcurrentHashMap<InetAddress, MonoSink<Double>> pendingRequests = new ConcurrentHashMap<>();

    @Bean
    public IcmpPinger icmpPinger() {
        IcmpPinger pinger = new IcmpPinger(this);
        Thread pingerThread = new Thread(pinger::runSelector, "jnb-pinger-thread");
        pingerThread.setDaemon(true);
        pingerThread.start();
        return pinger;
    }

    @Override
    public void onResponse(PingTarget target, double rttSeconds, int bytes, int seq) {
        MonoSink<Double> sink = pendingRequests.remove(target.getInetAddress());
        if (sink != null) {
            sink.success(rttSeconds * 1000.0);
        }
    }

    @Override
    public void onFailure(PingTarget target, FailureReason reason) {
        MonoSink<Double> sink = pendingRequests.remove(target.getInetAddress());
        if (sink != null) {
            sink.error(new RuntimeException(reason.toString()));
        }
    }

    public ConcurrentHashMap<InetAddress, MonoSink<Double>> getPendingRequests() {
        return pendingRequests;
    }
}
