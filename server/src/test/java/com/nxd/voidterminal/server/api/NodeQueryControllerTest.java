package com.nxd.voidterminal.server.api;

import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.server.ServerApplication;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import com.nxd.voidterminal.server.support.Snapshots;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@SpringBootTest(classes = ServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NodeQueryControllerTest {

    @Autowired
    WebTestClient client;

    @Autowired
    NodeRegistry registry;

    @Test
    void emptyListAndMissingNode() {
        client.get().uri("/api/nodes").exchange().expectStatus().isOk().expectBodyList(NodeView.class).hasSize(0);
        client.get().uri("/api/nodes/missing").exchange().expectStatus().isNotFound();
    }

    @Test
    void listAndGetAfterAccept() {
        registry.accept(Snapshots.snapshot("node-a", Instant.parse("2026-08-20T12:00:00Z"), Snapshots.staticInfo("h")));
        client.get().uri("/api/nodes").exchange().expectStatus().isOk().expectBodyList(NodeView.class).hasSize(1);
        client.get().uri("/api/nodes/node-a").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.nodeId").isEqualTo("node-a")
                .jsonPath("$.status").isEqualTo("ONLINE");
    }

    @Test
    void multiNodeStreamEmitsEvenWhenEmpty() {
        Flux<List<NodeView>> flux = client.get().uri("/api/nodes/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<List<NodeView>>() {})
                .getResponseBody();
        StepVerifier.create(flux)
                .expectNextMatches(List::isEmpty)
                .thenCancel()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    void singleNodeStream404WhenMissing() {
        client.get().uri("/api/nodes/missing/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }
}
