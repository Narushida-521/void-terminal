package com.nxd.voidterminal.agent.collect;

public final class NetworkFilters {
    private NetworkFilters() {}

    public static boolean isCountable(boolean hasIp, boolean loopback) {
        return hasIp && !loopback;
    }
}
