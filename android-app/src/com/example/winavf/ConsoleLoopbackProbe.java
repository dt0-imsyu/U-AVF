// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/** Checks byte-exact UART I/O on a disposable VM with no workspace disks. */
final class ConsoleLoopbackProbe {
    private static final String VM = "winavf-console-binary-echo-20260909";
    private static final String AVF = "android.system.virtualmachine.";
    private static final byte[] READY = "WINAVF_ECHO_READY\n".getBytes(StandardCharsets.US_ASCII);
    private static final long TIMEOUT_MS = 8_000;
    private final Context context;
    private final GuestMediaFiles media;
    private final Consumer<String> show;

    ConsoleLoopbackProbe(Context context, GuestMediaFiles media, Consumer<String> show) {
        this.context = context;
        this.media = media;
        this.show = show;
    }

    void run() {
        File directory = context.getExternalFilesDir(null);
        File report = new File(directory, "console-binary-loopback-report.txt");
        Object vm = null;
        try (PrintWriter log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("scope=DISPOSABLE_NO_DISK_NO_WINDOWS_NO_PRODUCT_MEDIA");
            byte[] pattern = pattern();
            log.println("patternBytes=" + pattern.length);
            log.println("patternSha256=" + sha(pattern));
            vm = AvfGuest.replaceStopped(context, VM, buildConfig(kernel()));
            try (InputStream input = AvfGuest.consoleOutput(vm);
                 OutputStream output = (OutputStream) invoke(vm, "getConsoleInput")) {
                verifyEcho(vm, input, output, pattern, new File(directory, "console-binary-loopback.raw"), log);
            }
            log.println("result=PASS");
            show.accept("Console binary loopback PASS: 4096/4096 exact.");
        } catch (Exception error) {
            recordFailure(report, error);
            show.accept("Console binary loopback failed: " + IoUtil.rootMessage(error));
        } finally {
            if (vm != null) {
                try {
                    remove(vm);
                } catch (Exception error) {
                    Log.w("U-AVF", "Could not remove disposable console probe VM", error);
                }
            }
        }
    }

    void cleanup() {
        try {
            Object vm = AvfGuest.find(context, VM);
            if (vm != null) remove(vm);
            show.accept("Console binary loopback VM removed.");
        } catch (Exception error) {
            show.accept("Console binary loopback cleanup failed: " + IoUtil.rootMessage(error));
        }
    }

    private void remove(Object vm) throws Exception {
        if (!"STOPPED".equals(AvfGuest.state(vm))) invoke(vm, "stop");
        AvfGuest.removeStopped(context, VM);
    }

    private File kernel() throws IOException {
        File payload = new File(context.getFilesDir(), "payload");
        if (!payload.isDirectory() && !payload.mkdirs()) throw new IOException("Cannot create payload directory");
        return media.copyAsset("console-binary-echo.Image", new File(payload, "console-binary-echo.Image"), -1);
    }

    private static void verifyEcho(Object vm, InputStream input, OutputStream output, byte[] pattern,
                                   File capture, PrintWriter log) throws Exception {
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        startReader(input, console);
        AvfGuest.run(vm);
        int offset = IoUtil.waitForBytes(console, console, READY, TIMEOUT_MS);
        if (offset < 0) throw new IOException("Guest readiness marker not observed");
        log.println("readyMarkerOffset=" + offset);
        output.write(pattern);
        output.flush();
        log.println("patternTransmitted=true");
        int start = offset + READY.length;
        if (!IoUtil.waitForLength(console, console, start + pattern.length, TIMEOUT_MS)) {
            throw new IOException("Timed out waiting for echoed pattern");
        }
        byte[] bytes = console.toByteArray();
        byte[] echo = Arrays.copyOfRange(bytes, start, start + pattern.length);
        try (FileOutputStream file = new FileOutputStream(capture, false)) {
            file.write(bytes);
        }
        log.println("echoedBytes=" + echo.length);
        log.println("echoedSha256=" + sha(echo));
        boolean exact = Arrays.equals(pattern, echo);
        log.println("byteExact=" + exact);
        if (!exact) throw new IOException("Echoed bytes differ from transmitted pattern");
    }

    private static void startReader(InputStream input, ByteArrayOutputStream console) {
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[512];
            try {
                for (int count; (count = input.read(buffer)) != -1;) {
                    synchronized (console) {
                        console.write(buffer, 0, count);
                        console.notifyAll();
                    }
                }
            } catch (IOException ignored) {
                // Closing the disposable VM's console stream wakes this reader.
            }
        }, "WinAVF-console-binary-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private Object buildConfig(File kernel) throws Exception {
        Class<?> imageType = Class.forName(AVF + "VirtualMachineCustomImageConfig");
        Object image = Class.forName(imageType.getName() + "$Builder").getConstructor().newInstance();
        set(image, "setName", String.class, VM);
        set(image, "setOsName", String.class, "winavf-console-binary-echo");
        set(image, "setKernelPath", String.class, kernel.getAbsolutePath());

        Class<?> configType = Class.forName(AVF + "VirtualMachineConfig");
        Object builder = Class.forName(configType.getName() + "$Builder").getConstructor(Context.class).newInstance(context);
        set(builder, "setProtectedVm", boolean.class, false);
        set(builder, "setDebugLevel", int.class, 1);
        set(builder, "setCpuTopology", int.class, configType.getField("CPU_TOPOLOGY_ONE_CPU").getInt(null));
        set(builder, "setMemoryBytes", long.class, 512L << 20);
        set(builder, "setConsoleInputDevice", String.class, "ttyS0");
        set(builder, "setVmOutputCaptured", boolean.class, true);
        set(builder, "setVmConsoleInputSupported", boolean.class, true);
        set(builder, "setCustomImageConfig", imageType, invoke(image, "build"));
        Object config = invoke(builder, "build");
        if (!Boolean.TRUE.equals(invoke(config, "isVmConsoleInputSupported"))) {
            throw new IllegalStateException("Console input was not persisted in loopback config");
        }
        return config;
    }

    private static void set(Object owner, String method, Class<?> type, Object value) throws Exception {
        owner.getClass().getMethod(method, type).invoke(owner, value);
    }

    private static Object invoke(Object owner, String method) throws Exception {
        return owner.getClass().getMethod(method).invoke(owner);
    }

    private static byte[] pattern() {
        byte[] bytes = new byte[4096];
        for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) index;
        return bytes;
    }

    private static String sha(byte[] bytes) {
        return Digest.hex(Digest.sha256(bytes));
    }

    private static void recordFailure(File report, Exception error) {
        try (PrintWriter log = new PrintWriter(new FileOutputStream(report, true))) {
            log.println("result=FAIL");
            log.println("error=" + IoUtil.rootMessage(error));
        } catch (IOException writeError) {
            Log.w("U-AVF", "Could not save console probe failure", writeError);
        }
    }
}
