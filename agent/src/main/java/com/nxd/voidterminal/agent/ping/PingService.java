package com.nxd.voidterminal.agent.ping;

import reactor.core.publisher.Mono;

public interface PingService {
    Mono<Double> pingHost(String host);
}
