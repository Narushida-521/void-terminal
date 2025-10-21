package com.nxd.voidterminal.config; // (您的 config 包)

import com.zaxxer.ping.IcmpPinger;
import com.zaxxer.ping.PingResponseHandler;
import com.zaxxer.ping.PingTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional; // 导入 @Conditional
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.MonoSink;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Conditional(OnPosixCondition.class) // ！！！只在 Linux/macOS 上加载此配置！！！
public class PingConfig implements PingResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(PingConfig.class);
    private final ConcurrentHashMap<InetAddress, MonoSink<Double>> pendingRequests = new ConcurrentHashMap<>();

    public PingConfig() {
        System.out.println("--- 正在尝试加载 [Linux/macOS] 版本的 IcmpPinger... ---");
    }

    @Bean(destroyMethod = "close")
    public IcmpPinger icmpPinger() {
        // ！！！注意：这里仍然需要权限 (setcap) ！！！
        final IcmpPinger pinger = new IcmpPinger(this);

        Thread pingerThread = new Thread(pinger::runSelector, "jnb-pinger-thread");
        pingerThread.setDaemon(true);
        pingerThread.start();

        logger.info("--- 成功加载 [Linux/macOS] 版本的 IcmpPinger ---");
        return pinger;
    }

    // ... (onResponse, onTimeout, getPendingRequests 的代码保持不变) ...
    @Override
    public void onResponse(PingTarget target, double rttSeconds, int bytes, int seq) {
        MonoSink<Double> sink = pendingRequests.remove(target.getInetAddress());
        if (sink != null) {
            sink.success(rttSeconds * 1000.0);
        }
    }
    @Override
    public void onTimeout(PingTarget target) {
        MonoSink<Double> sink = pendingRequests.remove(target.getInetAddress());
        if (sink != null) {
            sink.error(new RuntimeException("Ping timeout for " + target.getInetAddress()));
        }
    }
    public ConcurrentHashMap<InetAddress, MonoSink<Double>> getPendingRequests() {
        return pendingRequests;
    }
}