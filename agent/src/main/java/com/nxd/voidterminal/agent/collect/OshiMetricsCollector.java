package com.nxd.voidterminal.agent.collect;

import com.nxd.voidterminal.model.StaticSystemInfo;
import com.nxd.voidterminal.model.SystemMetrics;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PhysicalMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.SocketException;
import java.util.List;

@Service
public class OshiMetricsCollector implements MetricsCollector {
    private static final double GIGABYTE = 1024.0 * 1024.0 * 1024.0;

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final CentralProcessor processor = hardware.getProcessor();
    private long[] oldTicks = processor.getSystemCpuLoadTicks();

    @Override
    public Mono<SystemMetrics> collectMetrics() {
        return Mono.fromCallable(this::collectRealTimeMetrics).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<StaticSystemInfo> collectStaticInfo() {
        return Mono.fromCallable(this::readStaticInfo).subscribeOn(Schedulers.boundedElastic());
    }

    private SystemMetrics collectRealTimeMetrics() throws SocketException {
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(this.oldTicks) * 100;
        this.oldTicks = processor.getSystemCpuLoadTicks();
        GlobalMemory memory = hardware.getMemory();
        long totalBytes = memory.getTotal();
        long usedBytes = totalBytes - memory.getAvailable();
        double totalGB = totalBytes / GIGABYTE;
        double usedGB = usedBytes / GIGABYTE;
        double memoryUsage = usedBytes * 100.0 / totalBytes;

        long totalDiskBytes = 0;
        long usedDiskBytes = 0;
        for (OSFileStore fs : systemInfo.getOperatingSystem().getFileSystem().getFileStores()) {
            long currentTotal = fs.getTotalSpace();
            long currentUsed = currentTotal - fs.getUsableSpace();
            if (currentTotal > 0 && DiskFilters.isLocalDisk(fs.getType(), fs.getName())) {
                totalDiskBytes += currentTotal;
                usedDiskBytes += currentUsed;
            }
        }
        double totalDiskGB = totalDiskBytes / GIGABYTE;
        double usedDiskGB = usedDiskBytes / GIGABYTE;
        double diskUsage = totalDiskBytes > 0 ? usedDiskBytes * 100.0 / totalDiskBytes : 0.0;

        long totalBytesRecv = 0;
        long totalBytesSent = 0;
        for (NetworkIF net : hardware.getNetworkIFs()) {
            boolean hasIp = net.getIPv4addr().length > 0 || net.getIPv6addr().length > 0;
            if (NetworkFilters.isCountable(hasIp, net.queryNetworkInterface().isLoopback())) {
                totalBytesRecv += net.getBytesRecv();
                totalBytesSent += net.getBytesSent();
            }
        }

        return new SystemMetrics(
                processor.getLogicalProcessorCount(),
                round(cpuLoad, 2),
                round(usedGB, 2),
                round(totalGB, 2),
                round(memoryUsage, 2),
                round(usedDiskGB, 2),
                round(totalDiskGB, 2),
                round(diskUsage, 2),
                round(totalBytesRecv / GIGABYTE, 2),
                round(totalBytesSent / GIGABYTE, 2));
    }

    private StaticSystemInfo readStaticInfo() {
        OperatingSystem os = systemInfo.getOperatingSystem();
        List<PhysicalMemory> memoryList = hardware.getMemory().getPhysicalMemory();
        long totalMemCapacity = 0;
        for (PhysicalMemory memory : memoryList) {
            totalMemCapacity += memory.getCapacity();
        }
        String memInfo = memoryList.size() + " Slots Used,Total" + (totalMemCapacity / GIGABYTE) + "GB";
        List<String> gpuList = hardware.getGraphicsCards().stream().map((GraphicsCard gpu) -> {
            double vramGB = gpu.getVRam() / GIGABYTE;
            return String.format("%s(%.2f GB)", gpu.getName(), vramGB);
        }).toList();
        return new StaticSystemInfo(
                os.toString(),
                os.getNetworkParams().getDnsServers(),
                os.getNetworkParams().getHostName(),
                processor.getProcessorIdentifier().getName(),
                processor.getProcessorIdentifier().getMicroarchitecture(),
                hardware.getComputerSystem().getBaseboard().getManufacturer(),
                hardware.getComputerSystem().getManufacturer() + " " + hardware.getComputerSystem().getModel(),
                memInfo,
                gpuList);
    }

    private static double round(double value, int places) {
        long factor = (long) Math.pow(10, places);
        return Math.round(value * factor) / (double) factor;
    }
}
