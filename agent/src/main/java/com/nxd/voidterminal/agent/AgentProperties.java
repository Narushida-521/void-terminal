package com.nxd.voidterminal.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "voidterminal.agent")
public record AgentProperties(
        String nodeId,
        String nodeName,
        String serverUrl,
        Duration reportInterval,
        Ping ping
) {
    public record Ping(String unicom, String mobile, String telecom) {}

    public String resolvedName() {
        return (nodeName == null || nodeName.isBlank()) ? nodeId : nodeName;
    }
}
