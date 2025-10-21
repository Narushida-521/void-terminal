package com.nxd.voidterminal.controller;

import com.nxd.voidterminal.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PingController {
    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping("/ping/{host}")
    public Mono<String> ping(@PathVariable("host") String host) {
        System.out.println("Controller on thread: " + Thread.currentThread().getName());
        return pingService.pingHost(host)
                .map(rttMs -> String.format("Ping: %s:成功!RTT:%3f ms", host, rttMs))
                .onErrorResume(e ->
                        Mono.just(String.format("Ping %s:失败:%s", host, e.getMessage())));
    }
}
