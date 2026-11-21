// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Waits for the guest agent's LVH1 readiness reply on the established AVF service port. */
final class UbuntuReadinessProbe {
    private static final long PORT = 4051;
    private static final long INSTALLED_TIMEOUT_MS = 300_000;
    private static final long LIVE_TIMEOUT_MS = 90_000;

    private record Hello(long revision, long status, long capabilities) {
        boolean installedRuntimeReady() { return revision >= 2 && (capabilities & 3) == 3; }
        boolean audioAdvertised() { return (capabilities & 4) != 0; }
    }

    private UbuntuReadinessProbe() {}

    static void start(Object vm, File reportDirectory, boolean installed, BooleanSupplier active,
                      IntConsumer observedRevision, Runnable ready, Consumer<String> show) {
        File report = new File(reportDirectory, "ubuntu-gnome-vsock-report.txt");
        new Thread(() -> {
            try (PrintWriter log = new PrintWriter(new FileOutputStream(report, false))) {
                log.println("transport=AVF_CONNECT_VSOCK");
                log.println("guest=" + (installed ? "UBUNTU_INSTALLED_SYSTEM" : "UBUNTU_LIVE_OR_INITRAMFS"));
                log.println("port=" + PORT);
                waitForHello(vm, installed, active, observedRevision, ready, show, log);
            } catch (Exception error) {
                show.accept("Ubuntu vsock probe failed: " + IoUtil.rootMessage(error));
            }
        }, "WinAVF-ubuntu-vsock-hello").start();
    }

    private static void waitForHello(Object vm, boolean installed, BooleanSupplier active,
                                     IntConsumer observedRevision, Runnable ready, Consumer<String> show,
                                     PrintWriter log) throws Exception {
        Method connect = vm.getClass().getMethod("connectVsock", long.class);
        long deadline = SystemClock.elapsedRealtime() + (installed ? INSTALLED_TIMEOUT_MS : LIVE_TIMEOUT_MS);
        Exception lastError = null;
        long lastRevision = 0;
        int attempt = 1;
        // Revision 1 is a valid reply during first boot. The deadline bounds the wait even with quick replies.
        while (active.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            try {
                Hello hello = readHello(vm, connect);
                if (!active.getAsBoolean()) break;
                lastRevision = hello.revision();
                observedRevision.accept((int) lastRevision);
                if (complete(hello, attempt, installed, ready, show, log)) return;
            } catch (IOException | ReflectiveOperationException error) {
                lastError = error;
            }
            attempt++;
            Thread.sleep(1000);
        }
        if (!active.getAsBoolean()) {
            log.println("result=LINUX_VSOCK_PROBE_CANCELLED");
            return;
        }
        log.println("result=" + (installed && lastRevision == 1 ? "LINUX_INSTALLED_RUNTIME_NOT_READY"
                : "LINUX_VSOCK_HELLO_NOT_OBSERVED"));
        log.println("lastRevision=" + lastRevision);
        log.println("lastError=" + (lastError == null ? "none" : IoUtil.rootMessage(lastError)));
        show.accept("Ubuntu vsock HELLO was not observed.");
    }

    private static Hello readHello(Object vm, Method connect) throws IOException, ReflectiveOperationException {
        ParcelFileDescriptor socket = (ParcelFileDescriptor) connect.invoke(vm, PORT);
        if (socket == null) throw new IOException("vsock " + PORT + " is unavailable");
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(socket)) {
            byte[] header = IoUtil.readExactly(input, 16);
            if (header[0] != 'L' || header[1] != 'V' || header[2] != 'H' || header[3] != '1') {
                throw new IOException("unexpected Linux vsock hello magic");
            }
            return new Hello(ByteCodec.u32(header, 4), ByteCodec.u32(header, 8), ByteCodec.u32(header, 12));
        }
    }

    private static boolean complete(Hello hello, int attempt, boolean installed, Runnable ready,
                                    Consumer<String> show, PrintWriter log) {
        boolean isReady = hello.installedRuntimeReady();
        if (isReady) ready.run();
        log.println("attempt=" + attempt);
        if (installed && !isReady) {
            log.println("lastRevision=" + hello.revision());
            log.flush();
            return false;
        }
        log.println("hello=LVH1");
        log.println("revision=" + hello.revision());
        log.println("agentStatus=" + hello.status());
        log.println("capabilities=" + hello.capabilities());
        log.println("audioSocketAdvertised=" + hello.audioAdvertised());
        log.println("result=" + (isReady ? "LINUX_INSTALLED_RUNTIME_READY" : "LINUX_VSOCK_HELLO_PASS"));
        show.accept(isReady ? "Installed Ubuntu runtime is ready." : "Ubuntu vsock HELLO received; Linux transport is live.");
        return true;
    }
}
