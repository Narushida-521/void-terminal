package com.nxd.voidterminal.server.ingest;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.server.ServerApplication;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import com.nxd.voidterminal.server.support.Snapshots;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SnapshotIngestControllerTest {

    @Autowired
    WebTestClient client;

    @Autowired
    NodeRegistry registry;

    @Test
    void validSnapshotReturns204AndRegistersNode() {
        NodeSnapshot body = Snapshots.snapshot("node-a", Instant.parse("2026-08-20T12:00:00Z"), Snapshots.staticInfo("h"));
        client.post().uri("/internal/nodes/node-a/snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNoContent();
        assertTrue(registry.find("node-a").isPresent());
    }

    @Test
    void mismatchedIdReturns400AndDoesNotRegister() {
        NodeSnapshot body = Snapshots.snapshot("node-b", Instant.parse("2026-08-20T12:00:00Z"), null);
        client.post().uri("/internal/nodes/node-a/snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
        assertTrue(registry.find("node-a").isEmpty());
        assertTrue(registry.find("node-b").isEmpty());
    }
}
