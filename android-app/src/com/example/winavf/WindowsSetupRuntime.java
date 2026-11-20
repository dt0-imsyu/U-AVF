// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owns an experimental first boot independently of the Activity and its Surface. */
final class WindowsSetupRuntime {
    private static final String TAG = "UAVF-Windows-Setup";
    private static final String WRAPPER = "u-boot-wrapper-v24.Image";
    private static final String WRAPPER_SHA =
            "93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234";
    // Raw AVF DeathReason values verified against guest shutdown and reboot on Tab S11.
    private static final int AVF_REBOOT = 5;
    private static final int AVF_SHUTDOWN = 3;

    enum Phase { VERIFYING, BOOTING, WAITING_DISPLAY, DISPLAYING, DISPLAY_UNAVAILABLE, STOPPING, STOPPED, FAILED }
    enum Boot { PREVIEW, INSTALL, INSTALLED }

    private final Context context;
    private final Boot boot;
    private volatile Phase phase = Phase.VERIFYING;
    private volatile String error = "";
    private boolean stopRequested;
    private boolean started;
    private boolean exited;
    private boolean displayed;
    private boolean stateQueryFailed;
    private volatile boolean continueInstalled;
    private volatile boolean restarting;
    private final boolean rebootSuccessor;
    private volatile int deathReason = -1;
    private boolean observingDeath;
    private int deadPolls;
    private WindowsAvfOwner avf;
    private WindowsKdUart transport;
    private Object vm;

    WindowsSetupRuntime(Context context, Boot boot) {
        this(context, boot, false);
    }

    WindowsSetupRuntime(Context context, Boot boot, boolean rebootSuccessor) {
        this.context = context.getApplicationContext();
        this.boot = boot;
        this.rebootSuccessor = rebootSuccessor;
    }

    static boolean available(Context context) {
        return WindowsSetupDisk.available()
                && WindowsSetupDisk.installToolsAvailable(context)
                && WindowsKdUart.available(new File(context.getApplicationInfo().nativeLibraryDir));
    }

    Phase phase() { return phase; }
    String error() { return error; }
    boolean active() { return phase != Phase.STOPPED && phase != Phase.FAILED; }
    boolean continueInstalled() { return continueInstalled; }
    boolean restarting() { return restarting; }
    boolean rebootSuccessor() { return rebootSuccessor; }
    boolean installing() { return boot == Boot.INSTALL; }
    boolean setupEnvironment() { return boot != Boot.INSTALLED; }

    synchronized void start(Runnable ended) {
        if (started) return;
        started = true;
        new Thread(() -> {
            run();
            ended.run();
        }, "U-AVF-Windows-Setup-owner").start();
    }

    private void run() {
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
                task -> new Thread(task, "U-AVF-Windows-Setup-monitor"));
        try {
            File disk = WindowsSetupDisk.workspace(context).toFile();
            if (!Files.isRegularFile(disk.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Prepared Windows Setup disk is missing");
            }
            if (boot == Boot.INSTALLED) {
                WindowsSetupDisk.verifyInstalledBcd(disk.toPath(), WindowsSetupLayout.inspect(disk.toPath()));
            } else {
                WindowsSetupDisk.verifyFreshGpt(disk.toPath());
            }
            var profile = WindowsSetupDisk.verifyBootFiles(context, disk.toPath());
            boolean guided = boot != Boot.INSTALLED && profile.guided();
            if (boot != Boot.INSTALLED) WindowsSetupDisk.verifyInstallTools(context, disk.toPath(), false);
            if (profile.matchHostCpus() && Runtime.getRuntime().availableProcessors() != 8)
                throw new IOException("The verified Windows SMP profile requires eight host CPUs");
            long available = context.getFilesDir().getUsableSpace();
            if (available <= WindowsSetupDisk.RESERVE_BYTES) {
                throw new IOException("Windows Setup is at the storage reserve");
            }
            if (boot == Boot.INSTALL && (!guided || available < WindowsSetupDisk.requiredInstallFreeBytes())) {
                throw new IOException("Automatic Windows Setup requires the guided WinPE and at least "
                        + (WindowsSetupDisk.requiredInstallFreeBytes() / (1024L * 1024 * 1024))
                        + " GiB free after preparation");
            }
            File directory = new File(context.getFilesDir(), "windows-setup-runtime");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create Windows runtime");
            File wrapper = verifiedWrapper(directory);
            File nativeDir = new File(context.getApplicationInfo().nativeLibraryDir);
            avf = new WindowsAvfOwner(WindowsAvfOwner.rawConfig(context, wrapper, disk,
                    4L * 1024 * 1024 * 1024, profile.matchHostCpus()));
            try (var uart = new WindowsKdUart(nativeDir, directory)) {
                synchronized (this) {
                    if (stopRequested) return;
                    if (guided) {
                        WindowsSetupDisk.setInstallApproval(disk.toPath(), false);
                        if (boot == Boot.INSTALL) WindowsSetupDisk.setInstallApproval(disk.toPath(), true);
                    }
                    transport = uart;
                    vm = uart.createVm(avf);
                    try {
                        avf.observeDeath(vm, reason -> {
                            deathReason = reason;
                            Log.i(TAG, "Windows AVF death reason=" + reason);
                        });
                        observingDeath = true;
                        Log.i(TAG, "Windows AVF death callback registered");
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
                        Log.w(TAG, "Windows AVF death callback unavailable", absent);
                    }
                    avf.start(vm);
                    phase = Phase.BOOTING;
                }
                watchdog.scheduleWithFixedDelay(this::checkGuest, 1, 1, TimeUnit.SECONDS);
                watchdog.schedule(this::checkBootDeadline, WindowsKdUart.BOOT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                uart.serve(() -> {
                    synchronized (this) {
                        if (!stopRequested && error.isEmpty()) phase = Phase.WAITING_DISPLAY;
                    }
                }, profile.matchHostCpus());
                synchronized (this) {
                    if (!stopRequested && !exited && error.isEmpty()) throw new IOException("Windows Setup UART ended");
                }
            }
        } catch (Exception | LinkageError failure) {
            boolean unexpected;
            synchronized (this) {
                unexpected = !stopRequested && !exited && error.isEmpty();
                if (unexpected) error = rootMessage(failure);
            }
            if (unexpected) Log.w(TAG, "Windows Setup owner exited", failure);
        } finally {
            watchdog.shutdownNow();
            Object stoppingVm;
            boolean auditInstalled;
            synchronized (this) {
                stoppingVm = exited ? null : vm;
                auditInstalled = (boot == Boot.INSTALL || restarting) && exited && !stopRequested && error.isEmpty();
                vm = null;
                transport = null;
                phase = Phase.STOPPING;
            }
            if (stoppingVm != null) {
                try { avf.stop(stoppingVm); }
                catch (Exception failure) {
                    if (error.isEmpty()) error = rootMessage(failure);
                    Log.w(TAG, "Windows Setup VM stop failed", failure);
                }
            }
            boolean installedVerified = false;
            if (auditInstalled) {
                try {
                    var disk = WindowsSetupDisk.workspace(context);
                    WindowsSetupDisk.verifyInstalledBcd(disk, WindowsSetupLayout.inspect(disk));
                    installedVerified = true;
                    Log.i(TAG, "Windows installed boot verified after " + (restarting ? "reboot" : "installation"));
                } catch (IOException | LinkageError failure) {
                    synchronized (this) {
                        if (!stopRequested) error = rootMessage(failure);
                    }
                    Log.w(TAG, "Windows Setup exited without a verified installed boot", failure);
                }
            }
            synchronized (this) {
                continueInstalled = installedVerified && !stopRequested;
                phase = error.isEmpty() ? Phase.STOPPED : Phase.FAILED;
            }
        }
    }

    private void checkBootDeadline() {
        if (phase == Phase.BOOTING) fail("Windows Setup did not reach the kernel within the boot deadline");
    }

    private void checkGuest() {
        Object current;
        synchronized (this) {
            if (!active() || stopRequested || exited || vm == null) return;
            current = vm;
        }
        try {
            if (avf.hasExited(current)) {
                synchronized (this) {
                    if (vm != current || stopRequested) return;
                    if (observingDeath && deathReason < 0 && deadPolls++ == 0) return;
                    exited = true;
                    restarting = boot == Boot.INSTALLED && deathReason == AVF_REBOOT
                            && (!rebootSuccessor || displayed);
                    if (!restarting && !displayed && boot != Boot.INSTALL && error.isEmpty())
                        error = "AVF exited before the first Windows frame";
                    else if (!restarting && displayed && boot == Boot.INSTALLED && error.isEmpty()
                            && deathReason >= 0 && deathReason != AVF_SHUTDOWN)
                        error = "Windows VM ended unexpectedly (AVF reason " + deathReason + ")";
                    phase = Phase.STOPPING;
                    if (transport != null) transport.close();
                }
                Log.i(TAG, "Windows AVF reached DEAD");
                return;
            }
            stateQueryFailed = false;
        } catch (ReflectiveOperationException failure) {
            if (!stateQueryFailed) Log.w(TAG, "Cannot verify Windows AVF state", failure);
            stateQueryFailed = true;
        }
        if (context.getFilesDir().getUsableSpace() < WindowsSetupDisk.RESERVE_BYTES) {
            fail("Windows Setup stopped at the storage reserve");
        }
    }

    private synchronized void fail(String detail) {
        if (stopRequested || !active()) return;
        error = detail;
        phase = Phase.STOPPING;
        if (transport != null) transport.close();
    }

    synchronized boolean displayReady() {
        if (phase != Phase.WAITING_DISPLAY && phase != Phase.DISPLAYING) return false;
        displayed = true;
        phase = Phase.DISPLAYING;
        return true;
    }

    synchronized void displayFailed() {
        if (phase == Phase.WAITING_DISPLAY || phase == Phase.DISPLAYING) phase = Phase.DISPLAY_UNAVAILABLE;
    }

    synchronized boolean retryDisplay() {
        if (phase != Phase.DISPLAY_UNAVAILABLE) return false;
        phase = Phase.WAITING_DISPLAY;
        return true;
    }

    synchronized void stop() {
        stopRequested = true;
        continueInstalled = false;
        if (!active()) return;
        phase = Phase.STOPPING;
        if (transport != null) transport.close();
    }

    /** Adapter for the existing H.264 receiver's app-owned AVF connection. */
    public ParcelFileDescriptor connectVsock(long port) throws IOException, ReflectiveOperationException {
        Object current;
        synchronized (this) {
            if (port != 4055 || (phase != Phase.WAITING_DISPLAY && phase != Phase.DISPLAYING) || vm == null) {
                throw new IOException("Windows Setup display socket is unavailable");
            }
            current = vm;
        }
        return avf.connectVsock(current, (int) port);
    }

    private File verifiedWrapper(File directory) throws Exception {
        byte[] bytes;
        try (var input = context.getAssets().open(WRAPPER)) {
            bytes = input.readNBytes(1430913);
        }
        if (bytes.length != 1430912 || !WRAPPER_SHA.equals(HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
            throw new IOException("Unexpected Windows Setup wrapper identity");
        }
        File file = new File(directory, WRAPPER);
        Files.write(file.toPath(), bytes, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        return file;
    }

    private static String rootMessage(Throwable cause) {
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
