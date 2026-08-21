package com.nxd.voidterminal.agent;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    @Bean
    WebClient webClient(AgentProperties properties, WebClient.Builder builder) {
        return builder.baseUrl(properties.serverUrl()).build();
    }

    @Bean
    ApplicationRunner requireNodeId(AgentProperties properties) {
        return args -> {
            if (properties.nodeId() == null || properties.nodeId().isBlank()) {
                throw new IllegalStateException("voidterminal.agent.node-id is required");
            }
        };
    }
}
