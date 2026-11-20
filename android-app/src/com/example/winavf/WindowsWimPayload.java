// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Converts a standalone WIM directly from a seekable ISO payload descriptor. */
final class WindowsWimPayload {
    private static final boolean AVAILABLE = load();

    private WindowsWimPayload() {}

    static boolean available() { return AVAILABLE; }

    private static boolean load() {
        try {
            System.loadLibrary("uavf_windows_wim");
            return true;
        } catch (UnsatisfiedLinkError absent) {
            return false;
        }
    }

    static List<Path> split(ParcelFileDescriptor payload, Path staging, long partBytes) throws IOException {
        if (!available()) throw new IOException("The WIM converter is unavailable.");
        if (partBytes <= 0 || partBytes >= 4L * 1024 * 1024 * 1024)
            throw new IllegalArgumentException("WIM split size must fit FAT32.");
        try (var files = Files.list(staging)) {
            if (files.findAny().isPresent()) throw new IOException("A fresh staging directory is required.");
        }
        Path destination = staging.resolve("install.swm").toAbsolutePath();
        int status = splitFd(payload.getFd(), destination.toString(), partBytes);
        if (status != 0) throw new IOException("WIM split failed (" + status + "): " + errorText(status));
        List<Path> parts;
        try (var files = Files.list(staging)) {
            parts = files.sorted().toList();
        }
        if (parts.isEmpty() || !parts.contains(staging.resolve("install.swm")))
            throw new IOException("WIM split produced no install.swm.");
        for (Path part : parts) {
            long size = Files.size(part);
            if (size <= 0 || size >= 4L * 1024 * 1024 * 1024)
                throw new IOException("A split WIM part does not fit FAT32.");
        }
        return parts;
    }

    private static native int splitFd(int fd, String destination, long partBytes);
    private static native String errorText(int status);
}
