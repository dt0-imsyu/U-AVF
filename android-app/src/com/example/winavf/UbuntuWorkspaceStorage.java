// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.winavf.runtime.VmLifecycle;
import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Manages the installed Ubuntu disk and its allocation preferences. */
final class UbuntuWorkspaceStorage {
    static final long GIB = 1L << 30;
    static final long RESERVE_BYTES = 2 * GIB;
    private static final String DISK_NAME = "uavf-ubuntu-persistent-one-disk.raw";
    private static final String ROOT_SIZE = "new_workspace_root_bytes";
    private static final String GROWTH_PENDING = "ubuntu_growth_pending_bytes";
    private final Context context;
    private final SharedPreferences preferences;
    private final VmLifecycle lifecycle;
    private final BooleanSupplier vmRunning;

    UbuntuWorkspaceStorage(Context context, SharedPreferences preferences, VmLifecycle lifecycle,
                           BooleanSupplier vmRunning) {
        this.context = context;
        this.preferences = preferences;
        this.lifecycle = lifecycle;
        this.vmRunning = vmRunning;
    }

    File disk() {
        return new File(new File(context.getFilesDir(), "linux-persistent"), DISK_NAME);
    }

    long plannedRootBytes() {
        long bytes = preferences.getLong(ROOT_SIZE, PersistentUbuntuDisk.TARGET_BYTES);
        boolean valid = bytes >= PersistentUbuntuDisk.TARGET_BYTES && bytes <= 1024 * GIB && bytes % (1 << 20) == 0;
        return valid ? bytes : PersistentUbuntuDisk.TARGET_BYTES;
    }

    long installedRootBytes() throws Exception {
        return PersistentUbuntuDisk.rootBytes(disk(), UbuntuIsoImport.BYTES);
    }

    long freeBytes() { return context.getFilesDir().getUsableSpace(); }

    void planRoot(long bytes) {
        if (freeBytes() < PersistentUbuntuDisk.expectedDiskBytes(UbuntuIsoImport.BYTES, bytes) + RESERVE_BYTES) {
            throw new IllegalStateException("Not enough Android storage for this size, ISO and 2 GiB reserve.");
        }
        preferences.edit().putLong(ROOT_SIZE, bytes).apply();
    }

    String expansionBlocker() {
        if (vmRunning.getAsBoolean()) return "Stop Ubuntu before expanding storage.";
        if (!preferences.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false)) return "Finish Ubuntu installation first.";
        if (preferences.getLong(GROWTH_PENDING, 0) > 0) {
            return "Start Ubuntu to finish the previous filesystem expansion first.";
        }
        return "";
    }

    boolean guestCanExpand() { return preferences.getBoolean("ubuntu_growth_helper_verified", false); }

    void expand(long bytes) throws Exception {
        if (!lifecycle.beginStorageMutation()) throw new IllegalStateException("Stop VM before resizing");
        try {
            if (vmRunning.getAsBoolean()) throw new IllegalStateException("VM is still active");
            long current = installedRootBytes();
            if (freeBytes() < bytes - current + RESERVE_BYTES) throw new IOException("Insufficient free space");
            File disk = disk();
            File journal = new File(disk.getParentFile(), "root-growth.intent");
            PersistentUbuntuDisk.grow(disk, UbuntuIsoImport.BYTES, bytes, journal);
            preferences.edit().putLong(GROWTH_PENDING, bytes).apply();
        } finally {
            lifecycle.endStorageMutation();
        }
    }

    void delete() throws Exception {
        if (!lifecycle.beginStorageMutation()) throw new IllegalStateException("A VM or storage operation is active.");
        try {
            if (vmRunning.getAsBoolean()) throw new IllegalStateException("The Ubuntu VM is running; it was not deleted.");
            File disk = disk();
            String allowedDirectory = context.getFilesDir().getCanonicalPath() + File.separator;
            if (!disk.getCanonicalPath().startsWith(allowedDirectory)) {
                throw new SecurityException("Refusing to delete a file outside the named app-private workspace.");
            }
            try {
                AvfGuest.removeStopped(context, AvfGuest.UBUNTU_VM);
            } catch (ClassNotFoundException error) {
                throw new IllegalStateException("AVF VM record could not be checked; workspace was preserved.", error);
            }
            if (disk.exists() && !disk.delete()) throw new IOException("Could not delete the persistent Ubuntu disk.");
            preferences.edit().putBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false)
                    .putBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, false)
                    .putBoolean("ubuntu_install_handoff_attempted", false).apply();
        } finally {
            lifecycle.endStorageMutation();
        }
    }
}
