package com.nxd.voidterminal.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "voidterminal.server")
public record ServerProperties(Duration offlineAfter, Duration evictAfter) {
    public ServerProperties {
        if (offlineAfter == null) {
            offlineAfter = Duration.ofSeconds(5);
        }
        if (evictAfter == null) {
            evictAfter = Duration.ofSeconds(30);
        }
    }
}
