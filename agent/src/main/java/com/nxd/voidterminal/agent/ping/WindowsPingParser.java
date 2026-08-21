package com.nxd.voidterminal.agent.ping;

import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WindowsPingParser {
    private static final Pattern TIME = Pattern.compile(
            "(?:time|时间)(?:=|<)(\\d+\\.?\\d*)\\s*ms", Pattern.CASE_INSENSITIVE);

    private WindowsPingParser() {}

    public static OptionalDouble parse(String output) {
        if (output == null) {
            return OptionalDouble.empty();
        }
        Matcher matcher = TIME.matcher(output);
        if (matcher.find()) {
            return OptionalDouble.of(Double.parseDouble(matcher.group(1)));
        }
        return OptionalDouble.empty();
    }
}
