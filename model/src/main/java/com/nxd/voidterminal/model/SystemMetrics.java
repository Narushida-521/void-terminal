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
