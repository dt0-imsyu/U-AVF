// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.function.LongConsumer;

/** Verifies Ubuntu media before replacing the app-owned copy. */
final class UbuntuIsoImport {
    static final String NAME = "ubuntu-24.04.5-desktop-arm64.iso";
    static final long BYTES = 3_967_463_424L;
    static final String SHA256 = "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14";
    private static final long PROGRESS_STEP = 512L << 20;

    private UbuntuIsoImport() {}

    static boolean present(File directory) {
        File image = new File(directory, NAME);
        return image.isFile() && image.length() == BYTES;
    }

    /** Returns true when the verified ISO is already staged. */
    static synchronized boolean stage(ContentResolver resolver, Uri source, File directory,
                                      LongConsumer progress) throws Exception {
        File image = new File(directory, NAME);
        try (InputStream input = open(resolver, source)) {
            verify(input, null, bytes -> {});
        }
        if (present(directory) && SHA256.equals(Digest.hex(Digest.sha256File(image)))) return true;

        // The provider can change between opens. Verify the actual copy too.
        File staging = new File(directory, NAME + ".importing");
        try {
            try (InputStream input = open(resolver, source);
                 FileOutputStream output = new FileOutputStream(staging)) {
                verify(input, output, progress);
                output.getFD().sync();
            }
            IoUtil.publishFile(staging, image);
            return false;
        } catch (Exception failure) {
            try {
                Files.deleteIfExists(staging.toPath());
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static InputStream open(ContentResolver resolver, Uri source) throws IOException {
        InputStream input = resolver.openInputStream(source);
        if (input == null) throw new IOException("Could not read the selected file");
        return input;
    }

    private static void verify(InputStream input, FileOutputStream output, LongConsumer progress) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1 << 20];
        long total = 0;
        long nextProgress = PROGRESS_STEP;
        for (int count; (count = input.read(buffer)) != -1;) {
            total += count;
            if (total > BYTES) throw new IllegalArgumentException("Selected image is too large");
            digest.update(buffer, 0, count);
            if (output != null) output.write(buffer, 0, count);
            if (total >= nextProgress) {
                progress.accept(total);
                nextProgress += PROGRESS_STEP;
            }
        }
        if (total != BYTES || !SHA256.equals(Digest.hex(digest.digest()))) {
            throw new SecurityException("This is not the verified Ubuntu 24.04.5 ARM64 desktop ISO (size/hash mismatch)");
        }
    }

}
