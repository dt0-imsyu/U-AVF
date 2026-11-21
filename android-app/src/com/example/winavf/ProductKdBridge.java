// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Loopback bridge between a host KD session and the product VM serial console.
 *
 * The host connects to 127.0.0.1:39100 and authenticates with a capability
 * token. After that, bytes received on the socket are mirrored into the VM
 * console and bytes from the console are streamed back, while both directions
 * also land in capture files for offline inspection.
 *
 * The bridge owns its own VM so a KD session never disturbs the workspace VM.
 * Extracted from MainActivity: this is a self-contained protocol with no UI
 * state, and it was previously the largest source of varN naming debt.
 */
final class ProductKdBridge {
    static final String VM_NAME = "winavf-gop-ebs-r1";
    private static final int PORT = 39100;
    private static final int HANDSHAKE_TIMEOUT_MS = 45_000;
    private static final int PUMP_BUFFER_BYTES = 4096;
    private static final long HEADLESS_MEDIUM_BYTES = 9_126_805_504L;
    private static final byte[] ACCEPTED = "WINAVF_KD_BRIDGE_OK\n".getBytes(StandardCharsets.US_ASCII);

    /** Reports user-visible progress and failures. */
    interface Listener {
        void status(String message);
    }

    private final File reportFile;
    private final File captureDirectory;
    private final File externalDirectory;
    private final android.content.Context context;
    private final GuestMediaFiles mediaFiles;
    private final ConsoleFrameDecoder frameDecoder;
    private final Listener listener;
    private final ImagePatches imagePatches;
    private final FileCopier fileCopier;

    /** The patch engine, narrowed to what the bridge needs. */
    interface ImagePatches {
        void applyStaged(File payloadDirectory, File image) throws Exception;
    }

    /** Copies a file with optional size verification. */
    interface FileCopier {
        File copy(File source, File target, long expectedBytes, boolean replace) throws Exception;
    }

    volatile Socket connection;
    volatile Object vm;

    ProductKdBridge(android.content.Context context, File externalDirectory, File filesDirectory,
                    GuestMediaFiles mediaFiles, ConsoleFrameDecoder frameDecoder,
                    ImagePatches imagePatches, FileCopier fileCopier, Listener listener) {
        this.context = context;
        this.externalDirectory = externalDirectory;
        this.captureDirectory = filesDirectory;
        this.reportFile = new File(externalDirectory, "product-kd-bridge-report.txt");
        this.mediaFiles = mediaFiles;
        this.frameDecoder = frameDecoder;
        this.imagePatches = imagePatches;
        this.fileCopier = fileCopier;
        this.listener = listener;
    }

    /**
     * Serves one authenticated host connection and then keeps the VM alive until
     * the socket closes.
     *
     * @param capabilityToken hex token the host must present, or null to refuse
     */
    void serve(String capabilityToken) {
        if (capabilityToken == null || !capabilityToken.matches("[0-9A-Fa-f]{32,128}")) {
            writeFailure("invalid diagnostic capability token");
            return;
        }
        try {
            runSession(capabilityToken);
        } catch (Exception error) {
            writeFailure(rootMessage(error));
            listener.status("Product KD bridge failed: " + rootMessage(error));
        } finally {
            connection = null;
            vm = null;
        }
    }

    private void runSession(String capabilityToken) throws Exception {
        try (ServerSocket listener = new ServerSocket(PORT, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            try (PrintWriter report = openReport()) {
                report.println("scope=APP_OWNED_PRODUCT_VM_RAW_KD_BRIDGE");
                report.println("listener=127.0.0.1:" + PORT);
                report.println("state=LISTENING");
                report.flush();
                this.listener.status("KD bridge waiting for the host connection…");

                try (Socket host = listener.accept()) {
                    host.setTcpNoDelay(true);
                    host.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
                    authenticate(host, capabilityToken);
                    report.println("state=HOST_AUTHENTICATED");
                    report.flush();

                    File payload = requirePayloadDirectory();
                    File bootWrapper = mediaFiles.copyAsset("u-boot-wrapper-v24.Image",
                            new File(payload, "u-boot-wrapper-v24.Image"), -1L);
                    File image = stageHeadlessMedium(payload);
                    imagePatches.applyStaged(payload, image);

                    Object created = createVm(bootWrapper, image);
                    vm = created;
                    InputStream consoleOutput = (InputStream) created.getClass()
                            .getMethod("getConsoleOutput").invoke(created);
                    OutputStream consoleInput = (OutputStream) created.getClass()
                            .getMethod("getConsoleInput").invoke(created);
                    report.println("state=VM_CREATED");
                    report.flush();

                    startPumps(consoleOutput, consoleInput, host);
                    created.getClass().getMethod("run").invoke(created);
                    report.println("state=VM_RUNNING");
                    report.flush();
                    this.listener.status("Product KD bridge running on raw ttyS0.");

                    while (!host.isClosed()) {
                        Thread.sleep(1000L);
                    }
                }
            }
        }
    }

    private void authenticate(Socket host, String capabilityToken) throws IOException {
        byte[] presented = IoUtil.readExactly(host.getInputStream(), capabilityToken.length());
        if (!capabilityToken.equals(new String(presented, StandardCharsets.US_ASCII))) {
            throw new SecurityException("KD bridge capability token mismatch");
        }
        host.getOutputStream().write(ACCEPTED);
        host.getOutputStream().flush();
        host.setSoTimeout(0);
        connection = host;
    }

    private File requirePayloadDirectory() {
        File payload = new File(captureDirectory, "payload");
        if (!payload.exists() && !payload.mkdirs()) {
            throw new IllegalStateException("Cannot create payload directory");
        }
        return payload;
    }

    /**
     * Copies the headless boot medium into private storage.
     *
     * The copy is what the VM writes to; the external file stays untouched so a
     * failed session can simply be retried.
     */
    private File stageHeadlessMedium(File payload) throws Exception {
        File source = new File(externalDirectory, "win11-gop-ebs-r1.img");
        if (!source.isFile() || source.length() != HEADLESS_MEDIUM_BYTES) {
            throw new IllegalStateException("Missing compact headless boot medium: " + source);
        }
        File target = new File(payload, "win11-gop-ebs-r1.img");
        return fileCopier.copy(source, target, HEADLESS_MEDIUM_BYTES, false);
    }

    private Object createVm(File bootWrapper, File image) throws Exception {
        Object config = DiagnosticVmConfig.build(context, bootWrapper, image, false);
        Object manager = context.getSystemService(
                Class.forName("android.system.virtualmachine.VirtualMachineManager"));
        deleteExistingVm(manager);
        return manager.getClass().getMethod("create", String.class, config.getClass())
                .invoke(manager, VM_NAME, config);
    }

    private static void deleteExistingVm(Object manager) {
        try {
            Object existing = manager.getClass().getMethod("get", String.class).invoke(manager, VM_NAME);
            if (existing != null) {
                manager.getClass().getMethod("delete", String.class).invoke(manager, VM_NAME);
            }
        } catch (Exception ignored) {
            // No previous VM: the normal case on a first run.
        }
    }

    /** Streams console to host and host to console, capturing both directions. */
    private void startPumps(InputStream consoleOutput, OutputStream consoleInput, Socket host)
            throws IOException {
        File receiveCapture = new File(externalDirectory, "product-kd-bridge-rx.bin");
        File transmitCapture = new File(externalDirectory, "product-kd-bridge-tx.bin");
        pump("WinAVF-product-kd-rx", consoleOutput, receiveCapture, host.getOutputStream(), host);
        pump("WinAVF-product-kd-tx", host.getInputStream(), transmitCapture, consoleInput, host);
    }

    private void pump(String threadName, InputStream source, File capture, OutputStream sink,
                      Socket host) {
        new Thread(() -> {
            try (FileOutputStream recording = new FileOutputStream(capture, false)) {
                translate(source, recording, sink, true);
            } catch (Exception error) {
                IoUtil.closeQuietly(host);
            }
        }, threadName).start();
    }

    /**
     * Copies bytes in one direction.
     *
     * @param inspect when true, feed the console decoder so the desktop preview updates
     */
    private void translate(InputStream source, FileOutputStream recording, OutputStream sink,
                           boolean inspect) throws IOException {
        byte[] buffer = new byte[PUMP_BUFFER_BYTES];
        for (int count; (count = source.read(buffer)) >= 0; ) {
            recording.write(buffer, 0, count);
            recording.flush();
            sink.write(buffer, 0, count);
            sink.flush();
            if (inspect) frameDecoder.feed(buffer, 0, count);
        }
    }

    /** Stops the bridge VM and releases the host socket. */
    void stop() {
        Object active = vm;
        try {
            if (active != null) {
                active.getClass().getMethod("stop").invoke(active);
            }
        } catch (Exception ignored) {
            // The VM may already be gone; stopping is best effort.
        }
        IoUtil.closeQuietly(connection);
        listener.status("Product KD bridge stop requested.");
    }

    private PrintWriter openReport() throws IOException {
        return new PrintWriter(new FileOutputStream(reportFile, false));
    }

    private void writeFailure(String reason) {
        try (PrintWriter report = openReport()) {
            report.println("scope=APP_OWNED_PRODUCT_VM_RAW_KD_BRIDGE");
            report.println("state=FAILED");
            report.println("error=" + reason);
        } catch (IOException ignored) {
            // The report is diagnostic; losing it must not mask the real failure.
        }
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
