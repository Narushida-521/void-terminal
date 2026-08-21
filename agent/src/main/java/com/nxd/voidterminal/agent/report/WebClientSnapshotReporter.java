package com.nxd.voidterminal.agent.report;

import com.nxd.voidterminal.model.NodeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class WebClientSnapshotReporter implements SnapshotReporter {
    private static final Logger log = LoggerFactory.getLogger(WebClientSnapshotReporter.class);
    private final WebClient webClient;

    public WebClientSnapshotReporter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<Void> report(NodeSnapshot snapshot) {
        return webClient.post()
                .uri("/internal/nodes/{id}/snapshot", snapshot.nodeId())
                .bodyValue(snapshot)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().is4xxClientError()) {
                        log.error("Snapshot rejected for {}: {}", snapshot.nodeId(), ex.getStatusCode());
                    } else {
                        log.warn("Snapshot upload failed for {}: {}", snapshot.nodeId(), ex.getStatusCode());
                    }
                })
                .doOnError(ex -> {
                    if (!(ex instanceof WebClientResponseException)) {
                        log.warn("Snapshot upload failed for {}: {}", snapshot.nodeId(), ex.toString());
                    }
                })
                .onErrorResume(ex -> Mono.empty());
    }
}
