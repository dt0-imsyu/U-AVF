// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
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

    /**
     * Produce a writable WinPE WIM from the user's read-only ISO and a private, app-owned overlay.
     *
     * The source is inspected inside the ISO before anything is copied, so a
     * foreign medium is refused without paying for a 600 MiB copy. The copy is
     * then inspected again, because the overlay writes to image 2 and must not
     * run against a truncated staging file.
     *
     * Inspection and copying use separate descriptors. wimlib duplicates the
     * descriptor it is given, and a duplicate shares the file offset: reading
     * the WIM (whose XML data sits at the end of the file) leaves that offset at
     * EOF, so a copy taken through the same descriptor is empty.
     */
    static void prepareBootWim(Context context, ParcelFileDescriptor iso, Path candidate, Path overlay)
            throws IOException {
        if (!available()) throw new IOException("The WIM converter is unavailable.");
        try (var probe = WindowsInstallIso.openPayload(context, iso, true)) {
            /*
             * Reject a foreign medium before paying for the copy. A read error
             * here is not authoritative: the staged copy below is inspected
             * again, so an unreadable descriptor must not reject a valid ISO.
             */
            int verdict = probeSetupImageFd(probe.getFd());
            if (verdict >= 0) requireSetupImage(verdict);
        }
        try (var payload = WindowsInstallIso.openPayload(context, iso, true);
             var input = new ParcelFileDescriptor.AutoCloseInputStream(payload)) {
            Files.copy(input, candidate);
        }
        if (Files.size(candidate) <= 0)
            throw new IOException("The ISO boot image could not be copied out.");
        requireSetupImage(probeSetupImage(candidate.toAbsolutePath().toString()));
        int status = overlayBootWim(candidate.toAbsolutePath().toString(), overlay.toAbsolutePath().toString());
        if (status != 0) throw new IOException("WinPE image preparation failed (" + status + "): " + errorText(status));
    }

    /** Turns a native verdict into the message the user sees. */
    static void requireSetupImage(int verdict) throws IOException {
        if (verdict < 0)
            throw new IOException("Windows ISO boot image could not be read (" + errorText(-verdict) + ").");
        if (verdict == 0)
            throw new IOException("The ISO does not contain an ARM64 Windows Setup image.");
        if (verdict != 1)
            throw new IOException(com.example.winavf.ui.UiLanguage.text(
                    "Для этой сборки Windows нет профиля загрузки U-AVF. Выберите Windows 11 ARM64 25H2, сборка 26200.6584."));
    }

    private static native int splitFd(int fd, String destination, long partBytes);
    private static native int overlayBootWim(String image, String overlay);
    /** 1 for an ARM64 Setup image, 0 for a foreign one, negative wimlib error on failure. */
    static native int probeSetupImageFd(int fd);
    /** Same contract as {@link #probeSetupImageFd}, for a private copy on disk. */
    static native int probeSetupImage(String image);
    static native String errorText(int status);
}
