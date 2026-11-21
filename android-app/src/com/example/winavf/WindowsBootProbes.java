// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Debug-only observations of the WinPE hello and firmware console. */
final class WindowsBootProbes {
    private static final long HELLO_PORT = 4050;
    private static final String SERIAL_TRANSPORT = "APP_CONSOLE_INPUT_TO_EDK2_SERIAL_CONIN";
    private static final String KEYBOARD_TRANSPORT = "AVF_VIRTIO_KEYBOARD";
    private static final short KEY_ESCAPE = 1;
    private final File directory;
    private final Consumer<String> show;

    WindowsBootProbes(File directory, Consumer<String> show) {
        this.directory = directory;
        this.show = show;
    }

    void startMarkerReport(File disk) {
        new Thread(() -> waitForMarker(disk), "WinAVF-winpe-marker").start();
    }

    private void waitForMarker(File disk) {
        for (int attempt = 0; attempt < 360; attempt++) {
            try {
                String marker = readMarker(disk);
                if (marker != null) {
                    File report = new File(directory, "winpe-userland-marker.txt");
                    Files.writeString(report.toPath(), marker, StandardCharsets.US_ASCII);
                    show.accept("WINPE_USERLAND marker found: " + marker.replace("\r", " ").replace("\n", " | "));
                    return;
                }
            } catch (Exception unavailable) {
                // The guest can be updating the ESP while this read-only probe runs.
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String readMarker(File disk) throws Exception {
        try (var volume = Fat32Volume.open(disk)) {
            for (String name : volume.rootFileNames("TXT")) {
                byte[] body = volume.readRootFileByExtension(name, "TXT", 4096);
                if (body == null) continue;
                String text = new String(body, StandardCharsets.US_ASCII);
                if (text.contains("stage=WINPE_USERLAND_")) return text;
            }
            return null;
        }
    }

    void startKeyboardEscape(Object vm) {
        new Thread(() -> probeKeyboardEscape(vm), "WinAVF-esc-input-probe").start();
    }

    private void probeKeyboardEscape(Object vm) {
        File report = new File(directory, "uefi-input-probe-report.txt");
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("transport=" + KEYBOARD_TRANSPORT);
            log.println("key=KEY_ESC(1)");
            Thread.sleep(1200);
            var sendKey = vm.getClass().getMethod("sendKeyEvent", short.class, boolean.class);
            boolean pressed = (Boolean) sendKey.invoke(vm, KEY_ESCAPE, true);
            boolean released;
            try {
                Thread.sleep(40);
            } finally {
                released = (Boolean) sendKey.invoke(vm, KEY_ESCAPE, false);
            }
            log.println("sendKeyEvent=RESOLVED");
            log.println("pressAccepted=" + pressed);
            log.println("releaseAccepted=" + released);
            log.println("result=" + (pressed && released ? "HOST_INPUT_ACCEPTED" : "HOST_INPUT_REJECTED"));
            show.accept("UEFI keyboard probe: press=" + pressed + " release=" + released);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            String reason = IoUtil.rootMessage(error);
            try (var log = new PrintWriter(new FileOutputStream(report, false))) {
                log.println("transport=" + KEYBOARD_TRANSPORT);
                log.println("result=HOST_INPUT_ERROR");
                log.println("error=" + reason);
            } catch (IOException writeError) {
                Log.w("U-AVF", "Could not save firmware keyboard probe failure", writeError);
            }
            show.accept("UEFI keyboard probe failed: " + reason);
        }
    }

    void startHello(Object vm) {
        new Thread(() -> {
            File report = new File(directory, "vsock-hello-probe-report.txt");
            try (var log = new PrintWriter(new FileOutputStream(report, false))) {
                log.println("transport=AVF_CONNECT_VSOCK");
                log.println("port=" + HELLO_PORT);
                waitForHello(vm, log);
            } catch (Exception error) {
                show.accept("VSOCK HELLO probe failed: " + IoUtil.rootMessage(error));
            }
        }, "WinAVF-vsock-hello-probe").start();
    }

    private void waitForHello(Object vm, PrintWriter log) throws Exception {
        var connect = vm.getClass().getMethod("connectVsock", long.class);
        Exception lastError = null;
        for (int attempt = 1; attempt <= 60; attempt++) {
            try {
                ParcelFileDescriptor socket = (ParcelFileDescriptor) connect.invoke(vm, HELLO_PORT);
                if (socket == null) throw new IOException("WinPE vsock port is unavailable");
                byte[] header;
                try (var input = new ParcelFileDescriptor.AutoCloseInputStream(socket)) {
                    header = IoUtil.readExactly(input, 16);
                }
                if (header[0] != 'W' || header[1] != 'V' || header[2] != 'H' || header[3] != '1') {
                    throw new IOException("unexpected vsock hello magic");
                }
                log.println("attempt=" + attempt);
                log.println("hello=WVH1");
                log.println("agentStatus=" + ByteCodec.u32(header, 8));
                log.println("capabilities=" + ByteCodec.u32(header, 12));
                log.println("result=VSOCK_HELLO_PASS");
                show.accept("VSOCK HELLO received from WinPE agent.");
                return;
            } catch (IOException | ReflectiveOperationException error) {
                lastError = error;
            }
            Thread.sleep(1000);
        }
        log.println("result=VSOCK_HELLO_NOT_OBSERVED");
        log.println("lastError=" + (lastError == null ? "none" : IoUtil.rootMessage(lastError)));
        show.accept("VSOCK HELLO was not observed.");
    }

    void startSerialEscape(Object vm, BooleanSupplier prompt, BooleanSupplier acknowledged, IntSupplier frames) {
        new Thread(() -> {
            File report = new File(directory, "uefi-serial-escape-probe-report.txt");
            try (var log = new PrintWriter(new FileOutputStream(report, false))) {
                probeSerial(vm, prompt, acknowledged, frames, log);
            } catch (Exception error) {
                writeSerialFailure(report, error);
                show.accept("UEFI serial input probe failed: " + IoUtil.rootMessage(error));
            }
        }, "WinAVF-serial-escape-probe").start();
    }

    private void probeSerial(Object vm, BooleanSupplier prompt, BooleanSupplier acknowledged,
                             IntSupplier frames, PrintWriter log) throws Exception {
        log.println("transport=" + SERIAL_TRANSPORT);
        log.println("expectedPrompt=Press ESCAPE for boot options");
        log.println("mode=BOUNDED_PERIODIC_ESC_UNTIL_FIRMWARE_ACK");
        long promptDeadline = SystemClock.elapsedRealtime() + 15_000;
        while (!prompt.getAsBoolean() && SystemClock.elapsedRealtime() < promptDeadline) Thread.sleep(25);
        boolean promptSeen = prompt.getAsBoolean();
        log.println("promptSeen=" + promptSeen);
        if (!promptSeen) {
            log.println("result=NOT_SENT_PROMPT_NOT_OBSERVED");
            show.accept("UEFI serial input probe: prompt was not observed; ESC was not sent.");
            return;
        }

        int framesBefore = frames.getAsInt();
        log.println("payloadHex=1B");
        log.println("framesBefore=" + framesBefore);
        // The VM owns the shared console. Leave it available to its other observers.
        var console = (OutputStream) vm.getClass().getMethod("getConsoleInput").invoke(vm);
        int writes = sendEscape(console, acknowledged);
        log.println("uartWrites=" + writes);
        log.println("uartWrite=" + (writes > 0 ? "PASS" : "NOT_SENT"));
        show.accept("UEFI serial input probe: bounded ESC stream finished.");
        Thread.sleep(3000);
        int framesAfter = frames.getAsInt();
        boolean firmwareAcknowledged = acknowledged.getAsBoolean();
        log.println("framesAfter=" + framesAfter);
        log.println("firmwareEscapeAcknowledged=" + firmwareAcknowledged);
        String result = !firmwareAcknowledged ? "UART_WRITE_PASS_RESPONSE_NOT_OBSERVED"
                : framesAfter > framesBefore ? "UEFI_SERIAL_INPUT_AND_GRAPHICS_RESPONSE_PASS"
                : "UEFI_SERIAL_INPUT_PASS_FRAME_NOT_OBSERVED";
        log.println("result=" + result);
    }

    private static int sendEscape(OutputStream console, BooleanSupplier acknowledged) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 55_000;
        int writes = 0;
        while (!acknowledged.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            console.write(0x1b);
            console.flush();
            writes++;
            Thread.sleep(250);
        }
        return writes;
    }

    private static void writeSerialFailure(File report, Exception error) {
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("transport=" + SERIAL_TRANSPORT);
            log.println("result=ERROR");
            log.println("error=" + IoUtil.rootMessage(error));
        } catch (IOException writeError) {
            Log.w("U-AVF", "Could not save firmware serial probe failure", writeError);
        }
    }
}
