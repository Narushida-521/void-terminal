package com.nxd.voidterminal.agent.collect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkFiltersTest {
    @Test
    void requiresIpAndRejectsLoopback() {
        assertFalse(NetworkFilters.isCountable(false, false));
        assertFalse(NetworkFilters.isCountable(true, true));
        assertTrue(NetworkFilters.isCountable(true, false));
    }
}
