package com.nxd.voidterminal.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemMetrics {
    // cpu核心数
    private int cpuCores;
    // cpu总使用率(%)
    private double cpuUsage;
    // 已用内存(MB)
    private double memoryUsed;
    // 总内存(MB)
    private double memoryTotal;
    // 内存使用率(%)
    private double memoryUsage;
    // 磁盘已用
    private double diskUsed;
    // 磁盘总
    private double diskTotal;
    // 磁盘使用率(%)
    private double diskUsage;
}
