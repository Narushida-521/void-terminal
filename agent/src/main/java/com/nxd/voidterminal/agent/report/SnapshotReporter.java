package com.nxd.voidterminal.agent.report;

import com.nxd.voidterminal.model.NodeSnapshot;
import reactor.core.publisher.Mono;

public interface SnapshotReporter {
    Mono<Void> report(NodeSnapshot snapshot);
}
