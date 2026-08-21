package com.nxd.voidterminal.agent.collect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskFiltersTest {
    @Test
    void rejectsRemoteAndVirtualStores() {
        assertFalse(DiskFilters.isLocalDisk("nfs", "share"));
        assertFalse(DiskFilters.isLocalDisk("smb", "share"));
        assertFalse(DiskFilters.isLocalDisk("cdfs", "iso"));
        assertFalse(DiskFilters.isLocalDisk("ext4", "loop0"));
        assertFalse(DiskFilters.isLocalDisk("tmpfs", "tmp"));
        assertTrue(DiskFilters.isLocalDisk("NTFS", "C:\\"));
        assertTrue(DiskFilters.isLocalDisk("ext4", "sda1"));
    }
}
