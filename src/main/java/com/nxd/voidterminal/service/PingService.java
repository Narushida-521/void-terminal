package com.nxd.voidterminal.service;


import reactor.core.publisher.Mono;

public interface PingService {
    /**
     * 异步 Ping 一个主机.
     *
     * @param host 目标主机的 IP 地址或域名.
     * @return 一个 Mono<Double>，它将在成功时发出 RTT (毫秒),
     * 在失败或超时时发出 error 信号.
     */
    Mono<Double> pingHost(String host);
}
