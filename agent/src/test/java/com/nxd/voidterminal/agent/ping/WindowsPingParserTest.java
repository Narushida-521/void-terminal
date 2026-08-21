package com.nxd.voidterminal.agent.ping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsPingParserTest {

    @Test
    void parsesEnglishAndChineseTimes() {
        assertEquals(32.0, WindowsPingParser.parse("Reply from 1.1.1.1: bytes=32 time=32ms TTL=54").orElseThrow());
        assertEquals(196.0, WindowsPingParser.parse("来自 1.1.1.1 的回复: 字节=32 时间=196ms TTL=54").orElseThrow());
        assertEquals(1.0, WindowsPingParser.parse("time<1ms").orElseThrow());
    }

    @Test
    void emptyWhenTimeout() {
        assertTrue(WindowsPingParser.parse("Request timed out.").isEmpty());
        assertTrue(WindowsPingParser.parse("请求超时。").isEmpty());
    }
}
