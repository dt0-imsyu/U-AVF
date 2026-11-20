// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * SHA-256 and hex helpers shared by the boot-media and release checks.
 *
 * These used to be private statics inside MainActivity. The verification code
 * they serve (install.wim digests, boot-file audits, asset integrity) is the
 * security-relevant part of the Windows path, so keeping it in a small named
 * class makes it reviewable and testable.
 */
final class Digest {
    private static final int BUFFER_BYTES = 1024 * 1024;

    private Digest() {}

    static byte[] sha256(byte[] data) {
        return digest().digest(data);
    }

    static byte[] sha256File(File file) throws java.io.IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            for (int read; (read = input.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    /** Uppercase hex, matching the form used in the verified payload constants. */
    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
