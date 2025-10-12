package com.nxd.voidterminal.service.impl;

import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import com.nxd.voidterminal.service.MetricsService;
import org.springframework.stereotype.Service;
import oshi.hardware.PhysicalMemory;
import oshi.software.os.OperatingSystem;
import reactor.core.publisher.Flux;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OSFileStore;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class MetricsServiceImpl implements MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceImpl.class);

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final CentralProcessor processor;
    private long[] oldTicks;

    public MetricsServiceImpl() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.processor = hardware.getProcessor();
        this.oldTicks = processor.getSystemCpuLoadTicks();
        log.info("MetricsService initialized successfully.");
    }

    private SystemMetrics collectRealTimeMetrics() {
        // --- CPU 和 内存部分保持不变 ---
        final double cpuLoad = processor.getSystemCpuLoadBetweenTicks(this.oldTicks) * 100;
        this.oldTicks = processor.getSystemCpuLoadTicks();

        final GlobalMemory memory = hardware.getMemory();
        final long totalBytes = memory.getTotal();
        final long usedBytes = totalBytes - memory.getAvailable();
        final double totalGB = totalBytes / GIGABYTE;
        final double usedGB = usedBytes / GIGABYTE;
        final double memoryUsage = usedBytes * 100.0 / totalBytes;
        long totalDiskBytes = 0;
        long usedDiskBytes = 0;
        List<OSFileStore> fileStores = systemInfo.getOperatingSystem().getFileSystem().getFileStores();

        log.info("---------- Found {} File Stores ----------", fileStores.size());
        for (OSFileStore fs : fileStores) {
            long currentTotal = fs.getTotalSpace();
            long currentUsable = fs.getUsableSpace();
            long currentUsed = currentTotal - currentUsable;
            // 打印出所有原始数据！
            log.info("Store: {}, Type: {}, Total: {}, Usable: {}, CalculatedUsed: {}",
                    fs.getName(), fs.getType(), currentTotal, currentUsable, currentUsed);
            if (currentTotal > 0 && isLocalDisk(fs)) {
                totalDiskBytes += currentTotal;
                usedDiskBytes += currentUsed;
                log.info(">>> Included in calculation.");
            } else {
                log.info("--- Skipped.");
            }
        }
        log.info("----------------------------------------");

        final double totalDiskGB = totalDiskBytes / GIGABYTE;
        final double usedDiskGB = usedDiskBytes / GIGABYTE;
        final double diskUsage = (totalDiskBytes > 0) ? (usedDiskBytes * 100.0 / totalDiskBytes) : 0.0;
        // ==========================================================

        return SystemMetrics.builder()
                .cpuCores(processor.getLogicalProcessorCount())
                .cpuUsage(round(cpuLoad, 2))
                .memoryUsed(round(usedGB, 2))
                .memoryTotal(round(totalGB, 2))
                .memoryUsage(round(memoryUsage, 2))
                .diskUsed(round(usedDiskGB, 2))
                .diskTotal(round(totalDiskGB, 2))
                .diskUsage(round(diskUsage, 2))
                .build();
    }

    private boolean isLocalDisk(OSFileStore fs) {
        String type = fs.getType().toLowerCase();
        String name = fs.getName().toLowerCase();
        return !type.contains("nfs") && !type.contains("smb") &&
                !name.contains("iso") && !name.contains("loop") &&
                !name.contains("tmpfs");
    }

    private static final double GIGABYTE = 1024.0 * 1024.0 * 1024.0;

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    @Override
    public Flux<SystemMetrics> getMetricsStream() {
        return Flux.interval(Duration.ofSeconds(1)).skip(1)
                .map(tick -> collectRealTimeMetrics());
    }

    @Override
    public Mono<StaticSystemInfo> getStaticSystemInfo() {
        return Mono.fromCallable(() -> {
            // 操作系统与网络
            OperatingSystem os = systemInfo.getOperatingSystem();
            String osString = os.toString();
            String hostName = os.getNetworkParams().getHostName();
            String[] dnsServer = os.getNetworkParams().getDnsServers();
            // cpu
            String cpuStr = processor.getProcessorIdentifier().getName();
            String cpuArch = processor.getProcessorIdentifier().getMicroarchitecture();
            // 主板与系统
            String baseboard = hardware.getComputerSystem().getBaseboard().getManufacturer();
            String system = hardware.getComputerSystem().getManufacturer() + " " + hardware.getComputerSystem().getModel();
            // 物理内存
            List<PhysicalMemory> memoryList = hardware.getMemory().getPhysicalMemory();
            StringBuilder memInfo = new StringBuilder();
            long totalMemCapacity = 0;
            for (PhysicalMemory memory : memoryList) {
                totalMemCapacity += memory.getCapacity();
            }
            memInfo.append(memoryList.size()).append(" Slots Used,Total")
                    .append(totalMemCapacity / (GIGABYTE)).append("GB");
            List<String> gpuList = hardware.getGraphicsCards().stream().map(gpu -> {
                double vramGB = gpu.getVRam() / GIGABYTE;
                return String.format("%s(%.2f GB)", gpu.getName(), vramGB);
            }).toList();
            log.info("Collected Static Info successfully");
            return StaticSystemInfo.builder()
                    .osInfo(osString)
                    .hostName(hostName)
                    .dnsServer(dnsServer)
                    .cpuInfo(cpuStr)
                    .cpuArch(cpuArch)
                    .baseboardInfo(baseboard)
                    .computerSystemInfo(system)
                    .physicalMemoryInfo(memInfo.toString())
                    .graphicsCardInfo(gpuList)
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}