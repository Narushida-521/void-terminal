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
