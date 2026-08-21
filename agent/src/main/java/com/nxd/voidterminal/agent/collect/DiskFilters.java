package com.nxd.voidterminal.agent.collect;

public final class DiskFilters {
    private DiskFilters() {}

    public static boolean isLocalDisk(String type, String name) {
        String t = type == null ? "" : type.toLowerCase();
        String n = name == null ? "" : name.toLowerCase();
        return !t.contains("nfs") && !t.contains("smb") && !t.contains("tmpfs")
                && !n.contains("iso") && !n.contains("loop") && !n.contains("tmpfs");
    }
}
