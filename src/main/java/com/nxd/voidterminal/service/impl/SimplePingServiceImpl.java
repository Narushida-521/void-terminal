package com.nxd.voidterminal.service.impl; // (您的 service.impl 包)

import com.nxd.voidterminal.config.OnWindowsCondition;
import com.nxd.voidterminal.service.PingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset; // 导入 Charset
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Conditional(OnWindowsCondition.class) // 只在 Windows 上加载此 Bean
public class SimplePingServiceImpl implements PingService {

    private static final Logger logger = LoggerFactory.getLogger(SimplePingServiceImpl.class);

    private static final Pattern PING_TIME_PATTERN =
            Pattern.compile("(?:time|时间)(?:=|<)(\\d+\\.?\\d*)\\s*ms", Pattern.CASE_INSENSITIVE);

    // ！！！ 关键修正 ！！！
    // Windows 的中文命令行使用 GBK 编码
    private static final Charset WINDOWS_CONSOLE_CHARSET = Charset.forName("GBK");

    public SimplePingServiceImpl() {
        System.out.println("--- 成功加载 [Windows] 版本的 PingService ---");
    }

    @Override
    public Mono<Double> pingHost(String ipAddress) {

        Mono<Double> blockingCall = Mono.fromCallable(() -> {

            logger.debug("Executing system ping on thread: {}", Thread.currentThread().getName());

            // 因为这个类只在 Windows 上加载，我们不再需要 Linux 的 "if" 逻辑
            String command = "ping -n 1 -w 3000 " + ipAddress; // 1个包, 3秒超时

            Process process = Runtime.getRuntime().exec(command);

            StringBuilder output = new StringBuilder();

            // ！！！ 关键修正：显式使用 GBK 编码 ！！！
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), WINDOWS_CONSOLE_CHARSET))) {

                String line;
                while ((line = reader.readLine()) != null) {

                    logger.trace("Ping Output: {}", line); // 跟踪日志，查看原始输出
                    output.append(line).append("\n");

                    Matcher matcher = PING_TIME_PATTERN.matcher(line);
                    if (matcher.find()) {
                        // ！！！ 成功 ！！！
                        // 现在它可以正确匹配 "时间=196ms"
                        return Double.parseDouble(matcher.group(1));
                    }
                }
            }

            // ... (如果没找到 "time="，下面的失败逻辑保持不变) ...

            process.waitFor();
            String fullOutput = output.toString().toLowerCase();

            if (fullOutput.contains("timed out") || fullOutput.contains("超时")) {
                throw new RuntimeException("请求超时 (Request timed out)");
            }
            if (fullOutput.contains("unreachable") || fullOutput.contains("无法访问")) {
                throw new RuntimeException("目标主机不可达 (Destination host unreachable)");
            }
            if (fullOutput.contains("could not find host") || fullOutput.contains("找不到主机")) {
                throw new RuntimeException("找不到主机 (Could not find host " + ipAddress + ")");
            }

            logger.warn("Ping 失败，原始(乱码)输出可能已被正确解码: \n{}", fullOutput);
            throw new RuntimeException("Ping 失败: 无法解析 Ping 响应。");

        });

        return blockingCall.subscribeOn(Schedulers.boundedElastic());
    }
}