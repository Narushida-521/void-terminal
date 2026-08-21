# Multi-Node Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite Void Terminal into a Gradle multi-module agent + server so multiple machines can report live metrics to one in-memory hub.

**Architecture:** `model` holds shared records. Each machine runs `agent` (no HTTP server): OSHI + dual-platform ping, then POST snapshots. `server` is WebFlux: ingest on `/internal/nodes/{id}/snapshot`, memory registry with 5s offline / 30s evict, REST + SSE for callers.

**Tech Stack:** Java 25, Gradle 9.7.1, Spring Boot 4.1.0, WebFlux, oshi-core 7.5.0, jnb-ping 5.0.0, JUnit 5, Reactor Test, WebTestClient.

## Global Constraints

- Java toolchain 25 (current LTS; not 21, not 26).
- Gradle wrapper 9.7.1.
- Spring Boot 4.1.0 (use a newer 4.1.x patch if one exists on Maven Central).
- oshi-core 7.5.0 (agent only; follow 7.x API).
- jnb-ping 5.0.0 (POSIX ping only; `onFailure(PingTarget, FailureReason)`, not `onTimeout`).
- BOM-managed deps (Lombok, Reactor, JUnit) get no handwritten old versions.
- Do not keep Spring Boot `4.0.0-M3`, OSHI 6.x, or jnb-ping 2.x.
- Constructor injection only; no `@Resource`.
- Public APIs and shared models use full generics.
- Class names PascalCase (`CorsConfig`, not `corsConfig`).
- No login, database, frontend, SSH pull, alerts, or extra metrics (NIC rate, process list).
- Do not expose old routes: `/api/metrics/stream`, `/api/info/static`, `/api/latency/stream`, `/api/ping/test`.
- Windows commands use `.\gradlew.bat`.

---

## File map

Create:

- `settings.gradle` — include `model`, `agent`, `server`
- `build.gradle` — Java 25, Spring Boot 4.1.0, shared repos
- `model/build.gradle`
- `model/src/main/java/com/nxd/voidterminal/model/{NodeStatus,SystemMetrics,LatencyMetrics,StaticSystemInfo,NodeSnapshot,NodeView}.java`
- `model/src/test/java/com/nxd/voidterminal/model/NodeSnapshotTest.java`
- `server/build.gradle`
- `server/src/main/java/com/nxd/voidterminal/server/ServerApplication.java`
- `server/src/main/java/com/nxd/voidterminal/server/ServerProperties.java`
- `server/src/main/java/com/nxd/voidterminal/server/CorsConfig.java`
- `server/src/main/java/com/nxd/voidterminal/server/registry/NodeRecord.java`
- `server/src/main/java/com/nxd/voidterminal/server/registry/NodeRegistry.java`
- `server/src/main/java/com/nxd/voidterminal/server/ingest/SnapshotIngestController.java`
- `server/src/main/java/com/nxd/voidterminal/server/api/NodeQueryController.java`
- `server/src/main/resources/application.properties`
- `server/src/test/java/com/nxd/voidterminal/server/registry/NodeRegistryTest.java`
- `server/src/test/java/com/nxd/voidterminal/server/ingest/SnapshotIngestControllerTest.java`
- `server/src/test/java/com/nxd/voidterminal/server/api/NodeQueryControllerTest.java`
- `server/src/test/java/com/nxd/voidterminal/server/support/MutableClock.java`
- `server/src/test/java/com/nxd/voidterminal/server/support/Snapshots.java`
- `agent/build.gradle`
- `agent/src/main/java/com/nxd/voidterminal/agent/AgentApplication.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/AgentProperties.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/collect/MetricsCollector.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/collect/OshiMetricsCollector.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/collect/DiskFilters.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/collect/NetworkFilters.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/ping/PingService.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/ping/OnPosixCondition.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/ping/OnWindowsCondition.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/ping/PosixPingConfig.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/ping/PosixPingService.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/ping/WindowsPingService.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/report/SnapshotReporter.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/report/WebClientSnapshotReporter.java`
- `agent/src/main/java/com/nxd/voidterminal/agent/schedule/ReportScheduler.java`
- `agent/src/main/resources/application.properties`
- `agent/src/test/java/com/nxd/voidterminal/agent/collect/DiskFiltersTest.java`
- `agent/src/test/java/com/nxd/voidterminal/agent/collect/NetworkFiltersTest.java`
- `agent/src/test/java/com/nxd/voidterminal/agent/ping/WindowsPingParserTest.java`
- `agent/src/test/java/com/nxd/voidterminal/agent/report/WebClientSnapshotReporterTest.java`
- `agent/src/test/java/com/nxd/voidterminal/agent/schedule/ReportSchedulerTest.java`
- `README.md`

Modify:

- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.7.1

Delete after Task 8:

- `src/main/java/com/nxd/voidterminal/**`
- `src/main/resources/application.properties`
- `src/test/java/com/nxd/voidterminal/VoidTerminalApplicationTests.java`

---

### Task 1: Multi-module Gradle and shared model

**Files:**
- Create: `settings.gradle`, `build.gradle`, `model/build.gradle`, all six model types, `model/src/test/java/com/nxd/voidterminal/model/NodeSnapshotTest.java`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Test: `model/src/test/java/com/nxd/voidterminal/model/NodeSnapshotTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `NodeStatus`, `SystemMetrics`, `LatencyMetrics`, `StaticSystemInfo`, `NodeSnapshot`, `NodeView` records/enum in `com.nxd.voidterminal.model`

- [ ] **Step 1: Write the failing model test**

Create `model/src/test/java/com/nxd/voidterminal/model/NodeSnapshotTest.java`:

```java
package com.nxd.voidterminal.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NodeSnapshotTest {

    @Test
    void snapshotKeepsRequiredFieldsAndAllowsNullStaticInfo() {
        SystemMetrics metrics = new SystemMetrics(8, 12.5, 4.0, 16.0, 25.0, 100.0, 500.0, 20.0, 1.2, 0.4);
        LatencyMetrics latency = new LatencyMetrics(10.0, -1.0, 8.5);
        Instant reportedAt = Instant.parse("2026-08-20T15:00:00Z");

        NodeSnapshot snapshot = new NodeSnapshot(
                "node-a", "Alpha", "host-a", reportedAt, metrics, latency, null);

        assertEquals("node-a", snapshot.nodeId());
        assertEquals(metrics, snapshot.metrics());
        assertEquals(-1.0, snapshot.latency().mobile());
        assertNull(snapshot.staticInfo());

        NodeView view = new NodeView(
                snapshot.nodeId(),
                snapshot.nodeName(),
                snapshot.hostname(),
                NodeStatus.ONLINE,
                reportedAt,
                metrics,
                latency,
                null);
        assertEquals(NodeStatus.ONLINE, view.status());
        assertEquals(List.of(), new StaticSystemInfo(
                "os", new String[]{"1.1.1.1"}, "host", "cpu", "arch", "board", "sys", "mem", List.of()).graphicsCardInfo());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :model:test --tests com.nxd.voidterminal.model.NodeSnapshotTest`

Expected: FAIL because `model` project and types do not exist (`Project with path ':model' not found` or compile error).

- [ ] **Step 3: Write Gradle + model types**

Replace `settings.gradle`:

```groovy
rootProject.name = 'VoidTerminal'
include 'model', 'agent', 'server'
```

Replace root `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
    repositories {
        mavenCentral()
    }
    tasks.named('test') {
        useJUnitPlatform()
    }
}
```

Create `model/build.gradle`:

```groovy
plugins {
    id 'java-library'
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.13.4'
}
```

If Maven Central has a newer JUnit 5.x, use that. Do not add Spring to `model`.

Set `gradle/wrapper/gradle-wrapper.properties` `distributionUrl` to:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
```

Create the six types:

```java
package com.nxd.voidterminal.model;
public enum NodeStatus { ONLINE, OFFLINE }
```

```java
package com.nxd.voidterminal.model;
public record SystemMetrics(
        int cpuCores,
        double cpuUsage,
        double memoryUsed,
        double memoryTotal,
        double memoryUsage,
        double diskUsed,
        double diskTotal,
        double diskUsage,
        double networkTotalReceived,
        double networkTotalSent
) {}
```

```java
package com.nxd.voidterminal.model;
public record LatencyMetrics(double unicom, double mobile, double telecom) {}
```

```java
package com.nxd.voidterminal.model;
import java.util.List;
public record StaticSystemInfo(
        String osInfo,
        String[] dnsServer,
        String hostName,
        String cpuInfo,
        String cpuArch,
        String baseboardInfo,
        String computerSystemInfo,
        String physicalMemoryInfo,
        List<String> graphicsCardInfo
) {}
```

```java
package com.nxd.voidterminal.model;
import java.time.Instant;
public record NodeSnapshot(
        String nodeId,
        String nodeName,
        String hostname,
        Instant reportedAt,
        SystemMetrics metrics,
        LatencyMetrics latency,
        StaticSystemInfo staticInfo
) {}
```

```java
package com.nxd.voidterminal.model;
import java.time.Instant;
public record NodeView(
        String nodeId,
        String nodeName,
        String hostname,
        NodeStatus status,
        Instant lastSeenAt,
        SystemMetrics metrics,
        LatencyMetrics latency,
        StaticSystemInfo staticInfo
) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :model:test --tests com.nxd.voidterminal.model.NodeSnapshotTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add settings.gradle build.gradle model gradle/wrapper/gradle-wrapper.properties
git commit -m "搭好多模块构建和共享监控模型。"
```

---

### Task 2: In-memory node registry

**Files:**
- Create: `server/build.gradle`, `ServerProperties.java`, `NodeRecord.java`, `NodeRegistry.java`, `MutableClock.java`, `Snapshots.java`, `NodeRegistryTest.java`
- Test: `server/src/test/java/com/nxd/voidterminal/server/registry/NodeRegistryTest.java`

**Interfaces:**
- Consumes: `NodeSnapshot`, `NodeView`, `NodeStatus`, `StaticSystemInfo` from `model`
- Produces: `NodeRegistry.accept(NodeSnapshot)`, `NodeRegistry.list()`, `NodeRegistry.find(String)`, `NodeRegistry.sweep()`, `ServerProperties.offlineAfter()`, `ServerProperties.evictAfter()`

- [ ] **Step 1: Write the failing registry test**

`server/src/test/java/com/nxd/voidterminal/server/support/MutableClock.java`:

```java
package com.nxd.voidterminal.server.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {
    private Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
```

`server/src/test/java/com/nxd/voidterminal/server/support/Snapshots.java`:

```java
package com.nxd.voidterminal.server.support;

import com.nxd.voidterminal.model.LatencyMetrics;
import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;

import java.time.Instant;
import java.util.List;

public final class Snapshots {
    private Snapshots() {}

    public static SystemMetrics metrics() {
        return new SystemMetrics(4, 10.0, 2.0, 8.0, 25.0, 50.0, 200.0, 25.0, 0.5, 0.2);
    }

    public static LatencyMetrics latency() {
        return new LatencyMetrics(12.0, 13.0, 14.0);
    }

    public static StaticSystemInfo staticInfo(String host) {
        return new StaticSystemInfo("os", new String[]{"8.8.8.8"}, host, "cpu", "arch", "board", "sys", "mem", List.of("gpu"));
    }

    public static NodeSnapshot snapshot(String id, Instant at, StaticSystemInfo staticInfo) {
        return new NodeSnapshot(id, id, id + "-host", at, metrics(), latency(), staticInfo);
    }
}
```

`server/src/test/java/com/nxd/voidterminal/server/registry/NodeRegistryTest.java`:

```java
package com.nxd.voidterminal.server.registry;

import com.nxd.voidterminal.model.NodeStatus;
import com.nxd.voidterminal.server.ServerProperties;
import com.nxd.voidterminal.server.support.MutableClock;
import com.nxd.voidterminal.server.support.Snapshots;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRegistryTest {

    @Test
    void acceptRegistersUnknownNodeOnline() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T12:00:00Z"));
        NodeRegistry registry = new NodeRegistry(new ServerProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), clock);

        registry.accept(Snapshots.snapshot("node-a", clock.instant(), Snapshots.staticInfo("host-a")));

        assertEquals(1, registry.list().size());
        assertEquals(NodeStatus.ONLINE, registry.find("node-a").orElseThrow().status());
        assertEquals("gpu", registry.find("node-a").orElseThrow().staticInfo().graphicsCardInfo().getFirst());
    }

    @Test
    void laterSnapshotWithoutStaticInfoKeepsCachedStaticInfo() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T12:00:00Z"));
        NodeRegistry registry = new NodeRegistry(new ServerProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), clock);
        registry.accept(Snapshots.snapshot("node-a", clock.instant(), Snapshots.staticInfo("host-a")));
        clock.advance(Duration.ofSeconds(1));
        registry.accept(Snapshots.snapshot("node-a", clock.instant(), null));

        assertEquals("host-a", registry.find("node-a").orElseThrow().staticInfo().hostName());
    }

    @Test
    void sweepMarksOfflineThenEvicts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T12:00:00Z"));
        NodeRegistry registry = new NodeRegistry(new ServerProperties(Duration.ofMillis(50), Duration.ofMillis(100)), clock);
        registry.accept(Snapshots.snapshot("node-a", clock.instant(), null));

        clock.advance(Duration.ofMillis(51));
        registry.sweep();
        assertEquals(NodeStatus.OFFLINE, registry.find("node-a").orElseThrow().status());
        assertEquals(Snapshots.metrics(), registry.find("node-a").orElseThrow().metrics());

        clock.advance(Duration.ofMillis(50));
        registry.sweep();
        assertTrue(registry.find("node-a").isEmpty());
        assertTrue(registry.list().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :server:test --tests com.nxd.voidterminal.server.registry.NodeRegistryTest`

Expected: FAIL (`Project with path ':server' not found` or missing classes).

- [ ] **Step 3: Write minimal server module + registry**

`server/build.gradle`:

```groovy
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation project(':model')
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}
```

```java
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
```

```java
package com.nxd.voidterminal.server.registry;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.NodeStatus;
import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.model.StaticSystemInfo;

import java.time.Instant;

public final class NodeRecord {
    private NodeSnapshot snapshot;
    private StaticSystemInfo cachedStaticInfo;
    private Instant lastSeenAt;
    private NodeStatus status;

    public NodeRecord(NodeSnapshot snapshot, Instant lastSeenAt) {
        this.snapshot = snapshot;
        this.cachedStaticInfo = snapshot.staticInfo();
        this.lastSeenAt = lastSeenAt;
        this.status = NodeStatus.ONLINE;
    }

    public void update(NodeSnapshot snapshot, Instant lastSeenAt) {
        this.snapshot = snapshot;
        if (snapshot.staticInfo() != null) {
            this.cachedStaticInfo = snapshot.staticInfo();
        }
        this.lastSeenAt = lastSeenAt;
        this.status = NodeStatus.ONLINE;
    }

    public void markOffline() {
        this.status = NodeStatus.OFFLINE;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public NodeView toView() {
        return new NodeView(
                snapshot.nodeId(),
                snapshot.nodeName(),
                snapshot.hostname(),
                status,
                lastSeenAt,
                snapshot.metrics(),
                snapshot.latency(),
                cachedStaticInfo);
    }
}
```

```java
package com.nxd.voidterminal.server.registry;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.server.ServerProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NodeRegistry {
    private final ConcurrentHashMap<String, NodeRecord> nodes = new ConcurrentHashMap<>();
    private final ServerProperties properties;
    private final Clock clock;

    public NodeRegistry(ServerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void accept(NodeSnapshot snapshot) {
        Instant now = clock.instant();
        nodes.compute(snapshot.nodeId(), (id, existing) -> {
            if (existing == null) {
                return new NodeRecord(snapshot, now);
            }
            existing.update(snapshot, now);
            return existing;
        });
    }

    public List<NodeView> list() {
        return nodes.values().stream().map(NodeRecord::toView).toList();
    }

    public Optional<NodeView> find(String nodeId) {
        NodeRecord record = nodes.get(nodeId);
        return record == null ? Optional.empty() : Optional.of(record.toView());
    }

    public void sweep() {
        Instant now = clock.instant();
        nodes.forEach((id, record) -> {
            java.time.Duration age = java.time.Duration.between(record.lastSeenAt(), now);
            if (age.compareTo(properties.evictAfter()) >= 0) {
                nodes.remove(id, record);
            } else if (age.compareTo(properties.offlineAfter()) >= 0) {
                record.markOffline();
            }
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :server:test --tests com.nxd.voidterminal.server.registry.NodeRegistryTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "实现内存节点登记和离线淘汰。"
```

---

### Task 3: Snapshot ingest API

**Files:**
- Create: `SnapshotIngestController.java`, `SnapshotIngestControllerTest.java`, `ServerApplication.java`, `server/src/main/resources/application.properties`
- Test: `server/src/test/java/com/nxd/voidterminal/server/ingest/SnapshotIngestControllerTest.java`

**Interfaces:**
- Consumes: `NodeRegistry.accept`, `NodeSnapshot`
- Produces: `POST /internal/nodes/{id}/snapshot` → `204` or `400`

- [ ] **Step 1: Write the failing ingest test**

```java
package com.nxd.voidterminal.server.ingest;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.server.ServerApplication;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import com.nxd.voidterminal.server.support.Snapshots;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :server:test --tests com.nxd.voidterminal.server.ingest.SnapshotIngestControllerTest`

Expected: FAIL (missing `ServerApplication` or mapping).

- [ ] **Step 3: Write application, clock bean, controller**

```java
package com.nxd.voidterminal.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@EnableConfigurationProperties(ServerProperties.class)
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

`server/src/main/resources/application.properties`:

```properties
spring.application.name=void-terminal-server
server.port=8080
voidterminal.server.offline-after=5s
voidterminal.server.evict-after=30s
```

```java
package com.nxd.voidterminal.server.ingest;

import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SnapshotIngestController {
    private final NodeRegistry registry;

    public SnapshotIngestController(NodeRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/internal/nodes/{id}/snapshot")
    public ResponseEntity<Void> ingest(@PathVariable String id, @RequestBody NodeSnapshot snapshot) {
        if (snapshot == null
                || snapshot.nodeId() == null
                || snapshot.nodeId().isBlank()
                || !id.equals(snapshot.nodeId())
                || snapshot.metrics() == null
                || snapshot.latency() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        registry.accept(snapshot);
        return ResponseEntity.noContent().build();
    }
}
```

Add `@EnableScheduling` and a `@Scheduled(fixedRate = 1000)` method on `NodeRegistry` or a small `RegistrySweeper` that calls `sweep()` every second. Put the scheduled method in `RegistrySweeper` so tests that construct `NodeRegistry` directly stay isolated:

```java
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
```

Add `@EnableScheduling` on `ServerApplication`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :server:test --tests com.nxd.voidterminal.server.ingest.SnapshotIngestControllerTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "增加 agent 快照上报接口。"
```

---

### Task 4: Query API, SSE, and CORS

**Files:**
- Create: `NodeQueryController.java`, `CorsConfig.java`, `NodeQueryControllerTest.java`
- Test: `server/src/test/java/com/nxd/voidterminal/server/api/NodeQueryControllerTest.java`

**Interfaces:**
- Consumes: `NodeRegistry.list()`, `NodeRegistry.find(String)`
- Produces: `GET /api/nodes`, `GET /api/nodes/{id}`, SSE `/api/nodes/stream` event `nodes`, SSE `/api/nodes/{id}/stream` event `snapshot`

- [ ] **Step 1: Write the failing query/SSE test**

```java
package com.nxd.voidterminal.server.api;

import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.server.ServerApplication;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import com.nxd.voidterminal.server.support.Snapshots;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@SpringBootTest(classes = ServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
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
        Flux<List> flux = client.get().uri("/api/nodes/stream")
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
```

Add imports `reactor.core.publisher.Flux`, `org.springframework.core.ParameterizedTypeReference`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :server:test --tests com.nxd.voidterminal.server.api.NodeQueryControllerTest`

Expected: FAIL (no mappings).

- [ ] **Step 3: Write controller and CORS**

```java
package com.nxd.voidterminal.server.api;

import com.nxd.voidterminal.model.NodeView;
import com.nxd.voidterminal.server.registry.NodeRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@RestController
public class NodeQueryController {
    private final NodeRegistry registry;

    public NodeQueryController(NodeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/nodes")
    public List<NodeView> list() {
        return registry.list();
    }

    @GetMapping("/api/nodes/{id}")
    public NodeView get(@PathVariable String id) {
        return registry.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/api/nodes/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<List<NodeView>>> streamAll() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> ServerSentEvent.<List<NodeView>>builder(registry.list()).event("nodes").build());
    }

    @GetMapping(value = "/api/nodes/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NodeView>> streamOne(@PathVariable String id) {
        if (registry.find(id).isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return Flux.interval(Duration.ofSeconds(1))
                .handle((tick, sink) -> registry.find(id).ifPresentOrElse(
                        view -> sink.next(ServerSentEvent.<NodeView>builder(view).event("snapshot").build()),
                        () -> sink.complete()));
    }
}
```

```java
package com.nxd.voidterminal.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class CorsConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

`streamAll` must emit the first event without waiting a full second so the empty-list test can finish. Use `Flux.interval(Duration.ofSeconds(1)).startWith(0L)` (startWith tick value) so the first `nodes` event is immediate.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :server:test`

Expected: all server tests `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add server
git commit -m "增加节点查询和 SSE 推送。"
```

---

### Task 5: Agent metrics collection

**Files:**
- Create: `agent/build.gradle`, `DiskFilters.java`, `NetworkFilters.java`, `MetricsCollector.java`, `OshiMetricsCollector.java`, `DiskFiltersTest.java`, `NetworkFiltersTest.java`
- Test: `agent/src/test/java/com/nxd/voidterminal/agent/collect/DiskFiltersTest.java`, `NetworkFiltersTest.java`

**Interfaces:**
- Consumes: `SystemMetrics`, `StaticSystemInfo`
- Produces: `DiskFilters.isLocalDisk(String type, String name)`, `NetworkFilters.isCountable(boolean hasIp, boolean loopback)`, `MetricsCollector.collectMetrics()`, `MetricsCollector.collectStaticInfo()`

- [ ] **Step 1: Write the failing filter tests**

```java
package com.nxd.voidterminal.agent.collect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskFiltersTest {
    @Test
    void rejectsRemoteAndVirtualStores() {
        assertFalse(DiskFilters.isLocalDisk("nfs", "share"));
        assertFalse(DiskFilters.isLocalDisk("smb", "share"));
        assertFalse(DiskFilters.isLocalDisk("cdfs", "iso"));
        assertFalse(DiskFilters.isLocalDisk("ext4", "loop0"));
        assertFalse(DiskFilters.isLocalDisk("tmpfs", "tmp"));
        assertTrue(DiskFilters.isLocalDisk("NTFS", "C:\\"));
        assertTrue(DiskFilters.isLocalDisk("ext4", "sda1"));
    }
}
```

```java
package com.nxd.voidterminal.agent.collect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkFiltersTest {
    @Test
    void requiresIpAndRejectsLoopback() {
        assertFalse(NetworkFilters.isCountable(false, false));
        assertFalse(NetworkFilters.isCountable(true, true));
        assertTrue(NetworkFilters.isCountable(true, false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :agent:test --tests com.nxd.voidterminal.agent.collect.DiskFiltersTest --tests com.nxd.voidterminal.agent.collect.NetworkFiltersTest`

Expected: FAIL (`:agent` missing or classes missing).

- [ ] **Step 3: Write agent module and collectors**

`agent/build.gradle`:

```groovy
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation project(':model')
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'com.github.oshi:oshi-core:7.5.0'
    implementation 'com.zaxxer:jnb-ping:5.0.0'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}
```

```java
package com.nxd.voidterminal.agent.collect;

public final class DiskFilters {
    private DiskFilters() {}

    public static boolean isLocalDisk(String type, String name) {
        String t = type == null ? "" : type.toLowerCase();
        String n = name == null ? "" : name.toLowerCase();
        return !t.contains("nfs") && !t.contains("smb")
                && !n.contains("iso") && !n.contains("loop") && !n.contains("tmpfs");
    }
}
```

```java
package com.nxd.voidterminal.agent.collect;

public final class NetworkFilters {
    private NetworkFilters() {}

    public static boolean isCountable(boolean hasIp, boolean loopback) {
        return hasIp && !loopback;
    }
}
```

```java
package com.nxd.voidterminal.agent.collect;

import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import reactor.core.publisher.Mono;

public interface MetricsCollector {
    Mono<SystemMetrics> collectMetrics();
    Mono<StaticSystemInfo> collectStaticInfo();
}
```

Port the body of the old `MetricsServiceImpl` into `OshiMetricsCollector`:

- Keep `processor.getSystemCpuLoadBetweenTicks(oldTicks)` then refresh ticks.
- Disk: sum stores where `total > 0 && DiskFilters.isLocalDisk(fs.getType(), fs.getName())`.
- Network: for each `NetworkIF`, `boolean hasIp = net.getIPv4addr().length > 0 || net.getIPv6addr().length > 0;` then `NetworkFilters.isCountable(hasIp, net.queryNetworkInterface().isLoopback())`. This is the operator-precedence fix.
- No per-disk INFO logs, no `System.out`.
- `collectMetrics` / `collectStaticInfo` wrap work in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.
- Round doubles to 2 decimal places as the old `round` helper did.
- GiB = `1024.0 * 1024.0 * 1024.0`.

Do not copy unused fields (`networkLatency`, `packetLossRate`, `pingTarget`).

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :agent:test --tests com.nxd.voidterminal.agent.collect.DiskFiltersTest --tests com.nxd.voidterminal.agent.collect.NetworkFiltersTest`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add agent
git commit -m "迁入 OSHI 采集并修好网卡过滤。"
```

---

### Task 6: Agent ping (POSIX 5.x + Windows)

**Files:**
- Create: `PingService.java`, conditions, `PosixPingConfig.java`, `PosixPingService.java`, `WindowsPingService.java`, `WindowsPingParser.java`, `WindowsPingParserTest.java`
- Test: `agent/src/test/java/com/nxd/voidterminal/agent/ping/WindowsPingParserTest.java`

**Interfaces:**
- Consumes: host string
- Produces: `PingService.pingHost(String host)` → `Mono<Double>` RTT ms, error on failure; `WindowsPingParser.parse(String output)` → `OptionalDouble`

- [ ] **Step 1: Write the failing parser test**

```java
package com.nxd.voidterminal.agent.ping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsPingParserTest {

    @Test
    void parsesEnglishAndChineseTimes() {
        assertEquals(32.0, WindowsPingParser.parse("Reply from 1.1.1.1: bytes=32 time=32ms TTL=54").orElseThrow());
        assertEquals(196.0, WindowsPingParser.parse("来自 1.1.1.1 的回复: 字节=32 时间=196ms TTL=54").orElseThrow());
        assertEquals(1.0, WindowsPingParser.parse("time<1ms").orElseThrow());
    }

    @Test
    void emptyWhenTimeout() {
        assertTrue(WindowsPingParser.parse("Request timed out.").isEmpty());
        assertTrue(WindowsPingParser.parse("请求超时。").isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :agent:test --tests com.nxd.voidterminal.agent.ping.WindowsPingParserTest`

Expected: FAIL (missing `WindowsPingParser`).

- [ ] **Step 3: Write parser and ping services**

```java
package com.nxd.voidterminal.agent.ping;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WindowsPingParser {
    private static final Pattern TIME = Pattern.compile(
            "(?:time|时间)(?:=|<)(\\d+\\.?\\d*)\\s*ms", Pattern.CASE_INSENSITIVE);

    private WindowsPingParser() {}

    public static OptionalDouble parse(String output) {
        if (output == null) {
            return OptionalDouble.empty();
        }
        Matcher matcher = TIME.matcher(output);
        if (matcher.find()) {
            return OptionalDouble.of(Double.parseDouble(matcher.group(1)));
        }
        return OptionalDouble.empty();
    }
}
```

```java
package com.nxd.voidterminal.agent.ping;
import reactor.core.publisher.Mono;
public interface PingService {
    Mono<Double> pingHost(String host);
}
```

Copy `OnPosixCondition` / `OnWindowsCondition` into `com.nxd.voidterminal.agent.ping` unchanged.

`PosixPingConfig` (only `@Conditional(OnPosixCondition.class)`):

- Implement jnb-ping 5 `PingResponseHandler`.
- `onResponse(PingTarget target, double rttSeconds, int bytes, int seq)` → complete the pending `MonoSink<Double>` with `rttSeconds * 1000.0`.
- `onFailure(PingTarget target, FailureReason reason)` → `sink.error(new RuntimeException(reason.toString()))`.
- Key pending map by `target.getInetAddress()` (or `target.getInetAddress()` / Kotlin `getInetAddress()`).
- `@Bean IcmpPinger icmpPinger()` starts daemon thread `pinger::runSelector`.

`PosixPingService`: same pending-map + `pinger.ping(new PingTarget(address))` + 5s timeout as the old `PingServiceImpl`.

`WindowsPingService`: `@Conditional(OnWindowsCondition.class)`, `Runtime.exec("ping -n 1 -w 3000 " + host)`, read with `Charset.forName("GBK")`, parse with `WindowsPingParser`. If parse empty, throw timeout/unreachable/generic using the old Chinese/English substring checks. `subscribeOn(Schedulers.boundedElastic())`. No `System.out`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :agent:test --tests com.nxd.voidterminal.agent.ping.WindowsPingParserTest`

Expected: `BUILD SUCCESSFUL`. If `PosixPingConfig` does not compile against jnb-ping 5.0.0, fix to the 5.x handler (`onFailure` + `FailureReason`) until `:agent:compileJava` succeeds. Do not fall back to jnb-ping 2.x.

- [ ] **Step 5: Commit**

```bash
git add agent
git commit -m "按平台拆分 ping，并对齐 jnb-ping 5。"
```

---

### Task 7: Agent reporter, scheduler, and application

**Files:**
- Create: `AgentProperties.java`, `AgentApplication.java`, `SnapshotReporter.java`, `WebClientSnapshotReporter.java`, `ReportScheduler.java`, `application.properties`, reporter/scheduler tests
- Test: `WebClientSnapshotReporterTest.java`, `ReportSchedulerTest.java`

**Interfaces:**
- Consumes: `MetricsCollector`, `PingService`, `NodeSnapshot`
- Produces: `SnapshotReporter.report(NodeSnapshot)` → `Mono<Void>` that swallows errors after logging; `ReportScheduler` posts every `report-interval`; first snapshot includes `staticInfo`, later ones include it every 60s; `node-id` blank fails startup

- [ ] **Step 1: Write the failing reporter and scheduler tests**

```java
package com.nxd.voidterminal.agent.report;

import com.nxd.voidterminal.model.NodeSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
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
            return Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(HttpStatus.NO_CONTENT).build());
        };
        WebClient client = WebClient.builder().exchangeFunction(exchange).baseUrl("http://localhost:8080").build();
        WebClientSnapshotReporter reporter = new WebClientSnapshotReporter(client);
        NodeSnapshot snapshot = new NodeSnapshot("node-a", "node-a", "h", Instant.parse("2026-08-20T12:00:00Z"),
                new com.nxd.voidterminal.model.SystemMetrics(1, 1, 1, 2, 50, 1, 2, 50, 0, 0),
                new com.nxd.voidterminal.model.LatencyMetrics(1, 1, 1),
                null);
        StepVerifier.create(reporter.report(snapshot)).verifyComplete();
    }

    @Test
    void connectionFailureCompletesInsteadOfError() {
        ExchangeFunction exchange = request -> Mono.error(new IllegalStateException("down"));
        WebClient client = WebClient.builder().exchangeFunction(exchange).baseUrl("http://localhost:8080").build();
        WebClientSnapshotReporter reporter = new WebClientSnapshotReporter(client);
        NodeSnapshot snapshot = new NodeSnapshot("node-a", "node-a", "h", Instant.parse("2026-08-20T12:00:00Z"),
                new com.nxd.voidterminal.model.SystemMetrics(1, 1, 1, 2, 50, 1, 2, 50, 0, 0),
                new com.nxd.voidterminal.model.LatencyMetrics(1, 1, 1),
                null);
        StepVerifier.create(reporter.report(snapshot)).verifyComplete();
    }
}
```

Do **not** import `server.support.Snapshots` from agent tests. Inline metrics as shown.

```java
package com.nxd.voidterminal.agent.schedule;

import com.nxd.voidterminal.agent.AgentProperties;
import com.nxd.voidterminal.agent.collect.MetricsCollector;
import com.nxd.voidterminal.agent.ping.PingService;
import com.nxd.voidterminal.agent.report.SnapshotReporter;
import com.nxd.voidterminal.model.LatencyMetrics;
import com.nxd.voidterminal.model.NodeSnapshot;
import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReportSchedulerTest {

    @Test
    void firstTickIncludesStaticInfoAndFailedPingIsMinusOne() {
        SystemMetrics metrics = new SystemMetrics(2, 1, 1, 2, 50, 1, 2, 50, 0, 0);
        StaticSystemInfo info = new StaticSystemInfo("os", new String[]{}, "h", "c", "a", "b", "s", "m", List.of());
        MetricsCollector collector = new MetricsCollector() {
            @Override public Mono<SystemMetrics> collectMetrics() { return Mono.just(metrics); }
            @Override public Mono<StaticSystemInfo> collectStaticInfo() { return Mono.just(info); }
        };
        PingService ping = host -> "bad".equals(host) ? Mono.error(new RuntimeException("fail")) : Mono.just(9.0);
        AtomicReference<NodeSnapshot> posted = new AtomicReference<>();
        SnapshotReporter reporter = snapshot -> {
            posted.set(snapshot);
            return Mono.empty();
        };
        AgentProperties properties = new AgentProperties(
                "node-a", "", "http://localhost:8080", Duration.ofSeconds(1),
                new AgentProperties.Ping("ok", "bad", "ok"));
        ReportScheduler scheduler = new ReportScheduler(properties, collector, ping, reporter);
        StepVerifier.create(scheduler.reportOnce()).verifyComplete();
        assertNotNull(posted.get().staticInfo());
        assertEqualsLatency(posted.get().latency(), 9.0, -1.0, 9.0);
        StepVerifier.create(scheduler.reportOnce()).verifyComplete();
        assertNull(posted.get().staticInfo());
    }

    private static void assertEqualsLatency(LatencyMetrics latency, double u, double m, double t) {
        org.junit.jupiter.api.Assertions.assertEquals(u, latency.unicom());
        org.junit.jupiter.api.Assertions.assertEquals(m, latency.mobile());
        org.junit.jupiter.api.Assertions.assertEquals(t, latency.telecom());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :agent:test --tests com.nxd.voidterminal.agent.report.WebClientSnapshotReporterTest --tests com.nxd.voidterminal.agent.schedule.ReportSchedulerTest`

Expected: FAIL (missing classes).

- [ ] **Step 3: Write reporter, scheduler, properties, application**

```java
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
```

```java
package com.nxd.voidterminal.agent.report;

import com.nxd.voidterminal.model.NodeSnapshot;
import reactor.core.publisher.Mono;

public interface SnapshotReporter {
    Mono<Void> report(NodeSnapshot snapshot);
}
```

`WebClientSnapshotReporter`: POST `{baseUrl}/internal/nodes/{nodeId}/snapshot` with JSON body. On success (2xx) complete. On 4xx log error and `Mono.empty()`. On 5xx / connection error log warn and `Mono.empty()`. Never propagate the error.

`ReportScheduler`:

- `reportOnce()` zips `collectMetrics()`, three `pingHost` calls with `onErrorReturn(-1.0)`, and `collectStaticInfo()` only when `lastStaticAt == null || now - lastStaticAt >= 60s`.
- If metrics Mono errors: log error, return `Mono.empty()` (no POST).
- Build `NodeSnapshot` with `nodeId`, `resolvedName()`, hostname from static info when present else last known hostname else `nodeId`.
- Call `reporter.report`.
- `@Scheduled` on `reportInterval` (use a `PeriodicTrigger` from `properties.reportInterval()`, or `@Scheduled(fixedRateString = "${voidterminal.agent.report-interval}")`).

`AgentApplication`:

```java
package com.nxd.voidterminal.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
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
}
```

`agent/src/main/resources/application.properties`:

```properties
spring.application.name=void-terminal-agent
spring.main.web-application-type=none
voidterminal.agent.node-id=
voidterminal.agent.node-name=
voidterminal.agent.server-url=http://localhost:8080
voidterminal.agent.report-interval=1s
voidterminal.agent.ping.unicom=ha-cu-v4.ip.zstaticcdn.com
voidterminal.agent.ping.mobile=ha-cm-v4.ip.zstaticcdn.com
voidterminal.agent.ping.telecom=222.88.88.88
```

Fail startup if `node-id` is blank with an `ApplicationRunner` that throws `IllegalStateException("voidterminal.agent.node-id is required")`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :agent:test`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add agent
git commit -m "实现 agent 定时上报和启动校验。"
```

---

### Task 8: Remove monolith and write README

**Files:**
- Delete: entire old `src/` tree including `VoidTerminalApplicationTests`
- Create: `README.md`
- Test: `.\gradlew.bat test` (model + agent + server)

**Interfaces:**
- Consumes: finished modules
- Produces: README with run commands, API table, warning not to expose server to the internet; no leftover old routes

- [ ] **Step 1: Delete old single-module sources**

Delete:

- `src/main/java/com/nxd/voidterminal/` (all files)
- `src/main/resources/application.properties`
- `src/test/java/com/nxd/voidterminal/VoidTerminalApplicationTests.java`

If empty `src` directories remain, remove them.

- [ ] **Step 2: Run full test suite (must still pass)**

Run: `.\gradlew.bat test`

Expected: `BUILD SUCCESSFUL`. If a leftover root Spring Boot plugin tries to build the old app, remove `id 'org.springframework.boot'` / application plugin from the **root** `build.gradle` (root stays `apply false` only). Root must not be a boot app.

- [ ] **Step 3: Write README.md**

```markdown
# Void Terminal

多机实时监控：每台机器跑 agent，中心跑 server。

## 要求

- JDK 25
- 不要把 server 暴露到公网。第一版内部上报接口没有鉴权。

## 启动

```bat
.\gradlew.bat :server:bootRun
.\gradlew.bat :agent:bootRun --args="--voidterminal.agent.node-id=node-a"
.\gradlew.bat :agent:bootRun --args="--voidterminal.agent.node-id=node-b"
```

## 调用方接口

- `GET /api/nodes`
- `GET /api/nodes/{id}`
- `GET /api/nodes/stream` (SSE, event `nodes`)
- `GET /api/nodes/{id}/stream` (SSE, event `snapshot`)

Agent 上报：`POST /internal/nodes/{id}/snapshot`
```

- [ ] **Step 4: Run tests once more**

Run: `.\gradlew.bat test`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "移除旧单机入口并补上多机启动说明。"
```

---

## Manual check (after Task 8)

1. Start server on `:8080`.
2. Start two agents with `node-id=node-a` and `node-id=node-b`.
3. `GET http://localhost:8080/api/nodes` shows both `ONLINE`.
4. `GET http://localhost:8080/api/nodes/stream` keeps pushing both.
5. Stop `node-b`: about 5s → `OFFLINE`; about 30s → gone; `node-a` unchanged.

---

## Self-review

**Spec coverage**

| Spec item | Task |
| --- | --- |
| Multi-module model/agent/server | 1, 5, 3 |
| Latest versions (Java 25, Boot 4.1, Gradle 9.7.1, OSHI 7.5, jnb-ping 5) | 1, 5, 6 |
| Models and dropped unused metric fields | 1 |
| Registry 5s/30s, cache staticInfo | 2 |
| POST ingest 204/400 | 3 |
| GET list/get + SSE events | 4 |
| CORS localhost:5173 | 4 |
| OSHI collect, disk filter, NIC precedence fix, boundedElastic | 5 |
| Dual ping, -1 on failure | 6, 7 |
| Report interval, swallow errors, node-id required | 7 |
| Delete old APIs and empty SpringBootTest | 8 |
| README / no public expose | 8 |
| No auth/db/frontend | all (not added) |

**Type names used everywhere:** `NodeStatus`, `SystemMetrics`, `LatencyMetrics`, `StaticSystemInfo`, `NodeSnapshot`, `NodeView`, `NodeRegistry.accept/list/find/sweep`, `PingService.pingHost`, `MetricsCollector.collectMetrics/collectStaticInfo`, `SnapshotReporter.report`.
