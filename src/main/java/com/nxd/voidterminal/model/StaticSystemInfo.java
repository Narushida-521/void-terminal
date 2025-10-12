package com.nxd.voidterminal.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StaticSystemInfo {
    // 操作系统
    private String osInfo;
    private String[] dnsServer;
    private String hostName;
    // CPU
    private String cpuInfo;
    private String cpuArch;
    // 主板与系统
    private String baseboardInfo;
    private String computerSystemInfo;
    // 内存
    private String physicalMemoryInfo;
    // 显卡
    private List<String> graphicsCardInfo;
}
