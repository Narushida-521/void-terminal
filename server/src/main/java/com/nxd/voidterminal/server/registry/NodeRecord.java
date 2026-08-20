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
