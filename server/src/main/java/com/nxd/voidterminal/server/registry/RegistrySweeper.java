package com.nxd.voidterminal.server.registry;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegistrySweeper {
    private final NodeRegistry registry;

    public RegistrySweeper(NodeRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedRate = 1000)
    public void sweep() {
        registry.sweep();
    }
}
