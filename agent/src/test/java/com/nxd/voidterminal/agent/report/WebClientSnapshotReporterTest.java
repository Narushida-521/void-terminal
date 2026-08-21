package com.nxd.voidterminal.agent.report;

import com.nxd.voidterminal.model.LatencyMetrics;
import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.SystemMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

class WebClientSnapshotReporterTest {

    @Test
    void postsToInternalSnapshotPath() {
        ExchangeFunction exchange = request -> {
            if (!request.url().getPath().equals("/internal/nodes/node-a/snapshot")) {
                return Mono.error(new AssertionError(request.url().toString()));
            }
            return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
        };
        WebClient client = WebClient.builder().exchangeFunction(exchange).baseUrl("http://localhost:8080").build();
        WebClientSnapshotReporter reporter = new WebClientSnapshotReporter(client);
        StepVerifier.create(reporter.report(sample("node-a"))).verifyComplete();
    }

    @Test
    void connectionFailureCompletesInsteadOfError() {
        ExchangeFunction exchange = request -> Mono.error(new IllegalStateException("down"));
        WebClient client = WebClient.builder().exchangeFunction(exchange).baseUrl("http://localhost:8080").build();
        WebClientSnapshotReporter reporter = new WebClientSnapshotReporter(client);
        StepVerifier.create(reporter.report(sample("node-a"))).verifyComplete();
    }

    private static NodeSnapshot sample(String id) {
        return new NodeSnapshot(
                id, id, "h", Instant.parse("2026-08-20T12:00:00Z"),
                new SystemMetrics(1, 1, 1, 2, 50, 1, 2, 50, 0, 0),
                new LatencyMetrics(1, 1, 1),
                null);
    }
}
