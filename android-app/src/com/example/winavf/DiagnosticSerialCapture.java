// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Records the legacy serial stream and recognizes firmware markers split across reads. */
final class DiagnosticSerialCapture {
    private static final int TAIL_LIMIT = 256;
    private final StringBuilder tail = new StringBuilder();
    private volatile boolean bootOptionsPrompt;
    private volatile boolean escapeAcknowledged;

    boolean bootOptionsPrompt() { return bootOptionsPrompt; }
    boolean escapeAcknowledged() { return escapeAcknowledged; }

    synchronized void resetMarkers() {
        tail.setLength(0);
        bootOptionsPrompt = false;
        escapeAcknowledged = false;
    }

    void start(InputStream stream, File log, ConsoleFrameDecoder frames, Consumer<String> serial,
               Consumer<String> show) {
        new Thread(() -> capture(stream, log, frames, serial, show), "WinAVF-console").start();
    }

    private void capture(InputStream stream, File log, ConsoleFrameDecoder frames, Consumer<String> serial,
                         Consumer<String> show) {
        show.accept("Writing complete VM serial log to " + log.getAbsolutePath());
        try (InputStream input = stream; var output = new FileOutputStream(log, false)) {
            byte[] buffer = new byte[256];
            for (int count; (count = input.read(buffer)) != -1;) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
                output.flush();
                frames.feed(buffer, 0, count);
                String chunk = new String(buffer, 0, count, StandardCharsets.UTF_8);
                serial.accept(chunk);
                observe(chunk);
                if (chunk.indexOf('\n') >= 0) {
                    show.accept("SERIAL: " + chunk.replace("\r", "").replace("\n", ""));
                }
            }
        } catch (Exception error) {
            show.accept("Console closed: " + IoUtil.rootMessage(error));
        }
    }

    private synchronized void observe(String chunk) {
        tail.append(chunk);
        if (!bootOptionsPrompt) bootOptionsPrompt = tail.indexOf("Press ESCAPE for boot options") >= 0;
        if (!escapeAcknowledged) escapeAcknowledged = tail.indexOf("AVF_UEFI_ESC_RECEIVED") >= 0;
        if (tail.length() > TAIL_LIMIT) tail.delete(0, tail.length() - TAIL_LIMIT);
    }
}
