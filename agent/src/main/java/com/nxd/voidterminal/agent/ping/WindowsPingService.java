package com.nxd.voidterminal.agent.ping;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

@Service
@Conditional(OnWindowsCondition.class)
public class WindowsPingService implements PingService {
    private static final Charset WINDOWS_CONSOLE_CHARSET = Charset.forName("GBK");

    @Override
    public Mono<Double> pingHost(String host) {
        return Mono.fromCallable(() -> pingOnce(host)).subscribeOn(Schedulers.boundedElastic());
    }

    private Double pingOnce(String host) throws Exception {
        Process process = Runtime.getRuntime().exec("ping -n 1 -w 3000 " + host);
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), WINDOWS_CONSOLE_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                var parsed = WindowsPingParser.parse(line);
                if (parsed.isPresent()) {
                    return parsed.getAsDouble();
                }
            }
        }
        process.waitFor();
        String fullOutput = output.toString().toLowerCase();
        if (fullOutput.contains("timed out") || fullOutput.contains("超时")) {
            throw new RuntimeException("请求超时 (Request timed out)");
        }
        if (fullOutput.contains("unreachable") || fullOutput.contains("无法访问")) {
            throw new RuntimeException("目标主机不可达 (Destination host unreachable)");
        }
        if (fullOutput.contains("could not find host") || fullOutput.contains("找不到主机")) {
            throw new RuntimeException("找不到主机 (Could not find host " + host + ")");
        }
        throw new RuntimeException("Ping 失败: 无法解析 Ping 响应。");
    }
}
