// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.res.AssetManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;

/** Stages boot assets and disk images for the guest launchers. */
final class GuestMediaFiles {
    private final AssetManager assets;

    GuestMediaFiles(AssetManager assets) {
        this.assets = assets;
    }

    File copyAsset(String asset, File target, long expectedBytes) throws IOException {
        if (target.isFile() && (expectedBytes < 0 || target.length() == expectedBytes)) return target;
        try (InputStream input = assets.open(asset); FileOutputStream output = new FileOutputStream(target)) {
            transfer(input, output);
        }
        return target;
    }

    File copyVerifiedAsset(String asset, File target, long expectedBytes, String sha256) throws IOException {
        if (matches(target, expectedBytes, sha256)) return target;
        File parent = target.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File staged = new File(parent, target.getName() + ".verified-tmp");
        Files.deleteIfExists(staged.toPath());
        try {
            try (InputStream input = assets.open(asset); FileOutputStream output = new FileOutputStream(staged)) {
                transfer(input, output);
                output.getFD().sync();
            }
            if (!matches(staged, expectedBytes, sha256)) {
                throw new SecurityException("Bundled platform update failed its size or SHA-256 check.");
            }
            // Keep the previous payload available until the replacement has been verified.
            IoUtil.publishFile(staged, target);
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(staged.toPath());
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return target;
    }

    static File copy(File source, File target, long expectedBytes, boolean force, Runnable copying) throws IOException {
        long bytes = expectedBytes >= 0 ? expectedBytes : source.length();
        if (!force && target.isFile() && target.length() == bytes) return target;
        copying.run();
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            transfer(input, output);
        }
        if (target.length() != bytes) throw new IOException("Setup-medium copy has unexpected size");
        return target;
    }

    static File sparseTarget(File target, long bytes) throws IOException {
        if (target.isFile() && target.length() == bytes) return target;
        try (RandomAccessFile output = new RandomAccessFile(target, "rw")) {
            output.setLength(bytes);
        }
        return target;
    }

    private static boolean matches(File file, long bytes, String sha256) throws IOException {
        return file.isFile() && file.length() == bytes && sha256.equalsIgnoreCase(Digest.hex(Digest.sha256File(file)));
    }

    private static void transfer(InputStream input, FileOutputStream output) throws IOException {
        byte[] buffer = new byte[1 << 20];
        for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
    }
}
