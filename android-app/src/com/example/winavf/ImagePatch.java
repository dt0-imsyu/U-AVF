// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Applies and reverts a WAVFPAT1 image patch against a runtime disk image.
 *
 * The format is: magic, version, target size, baseline SHA-256, range count,
 * then per range the offset, length, expected-before hash and expected-after
 * hash followed by the after bytes and before bytes. Every hash is verified
 * before and after the write, so a partially applied patch is detected rather
 * than silently corrupting the image.
 *
 * Extracted from MainActivity. Nothing here touches Android APIs, so the patch
 * format can be tested off-device.
 */
final class ImagePatch {
    static final byte[] MAGIC = "WAVFPAT1".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int HASH_BYTES = 32;
    private static final int MAX_RANGES = 4096;
    private static final int MAX_RANGE_BYTES = 64 * 1024 * 1024;

    private ImagePatch() {}

    /**
     * Applies (or reverts) the patch in {@code patchFile} to {@code image}.
     *
     * @param rollback when true, restores the before state instead of the after state
     */
    static void apply(File patchFile, File image, boolean rollback) throws IOException {
        try (RandomAccessFile patch = new RandomAccessFile(patchFile, "r")) {
            byte[] magic = new byte[MAGIC.length];
            patch.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Unsupported image patch magic");
            }
            if (ByteCodec.readLittleEndianInt(patch) != VERSION) {
                throw new IOException("Unsupported image patch version");
            }
            long imageSize = ByteCodec.readLittleEndianLong(patch);
            if (imageSize != image.length()) {
                throw new IOException("Patch targets a different runtime image size");
            }
            byte[] baseline = new byte[HASH_BYTES];
            patch.readFully(baseline);
            int rangeCount = ByteCodec.readLittleEndianInt(patch);
            if (rangeCount < 1 || rangeCount > MAX_RANGES) {
                throw new IOException("Invalid image patch range count");
            }

            Range[] ranges = readRanges(patch, rangeCount, imageSize);
            if (patch.getFilePointer() != patch.length()) {
                throw new IOException("Trailing data in image patch");
            }
            if (!rollback && !Arrays.equals(baseline, Digest.sha256File(image))) {
                throw new IOException("Runtime image does not match the patch baseline");
            }

            writeRanges(patch, image, ranges, rollback);

            if (rollback && !Arrays.equals(baseline, Digest.sha256File(image))) {
                throw new IOException("Rollback did not restore the baseline hash");
            }
        }
    }

    /** Returns true when the patch record in {@code patchFile} targets this exact image. */
    static boolean targetsBaseline(File patchFile, File image) throws IOException {
        try (RandomAccessFile patch = new RandomAccessFile(patchFile, "r")) {
            byte[] magic = new byte[MAGIC.length];
            patch.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) return false;
            if (ByteCodec.readLittleEndianInt(patch) != VERSION) return false;
            if (ByteCodec.readLittleEndianLong(patch) != image.length()) return false;
            byte[] baseline = new byte[HASH_BYTES];
            patch.readFully(baseline);
            return Arrays.equals(baseline, Digest.sha256File(image));
        }
    }

    private static Range[] readRanges(RandomAccessFile patch, int count, long imageSize)
            throws IOException {
        Range[] ranges = new Range[count];
        for (int index = 0; index < count; index++) {
            long offset = ByteCodec.readLittleEndianLong(patch);
            int length = ByteCodec.readLittleEndianInt(patch);
            if (offset < 0 || length < 1 || length > MAX_RANGE_BYTES || offset > imageSize - length) {
                throw new IOException("Invalid image patch range");
            }
            byte[] before = new byte[HASH_BYTES];
            byte[] after = new byte[HASH_BYTES];
            patch.readFully(before);
            patch.readFully(after);
            long afterOffset = patch.getFilePointer();
            patch.seek(afterOffset + length);
            long beforeOffset = patch.getFilePointer();
            patch.seek(beforeOffset + length);
            ranges[index] = new Range(offset, length, before, after, beforeOffset, afterOffset);
        }
        return ranges;
    }

    private static void writeRanges(RandomAccessFile patch, File image, Range[] ranges,
                                    boolean rollback) throws IOException {
        byte[][] payloads = new byte[ranges.length][];
        byte[] current = new byte[0];

        // Pass 1: verify every range before touching the image. A single pass would
        // leave earlier ranges rewritten when a later range fails verification.
        try (RandomAccessFile target = new RandomAccessFile(image, "r")) {
            for (int index = 0; index < ranges.length; index++) {
                Range range = ranges[index];
                byte[] expected = rollback ? range.afterHash : range.beforeHash;
                current = new byte[range.length];
                target.seek(range.offset);
                target.readFully(current);
                if (!Arrays.equals(expected, Digest.sha256(current))) {
                    throw new IOException("Patch range state hash mismatch at " + range.offset);
                }

                byte[] after = readPayload(patch, range.afterDataOffset, range.length);
                if (!Arrays.equals(range.afterHash, Digest.sha256(after))) {
                    throw new IOException("Patch payload hash mismatch at " + range.offset);
                }
                byte[] before = readPayload(patch, range.beforeDataOffset, range.length);
                if (!Arrays.equals(range.beforeHash, Digest.sha256(before))) {
                    throw new IOException("Patch rollback data hash mismatch at " + range.offset);
                }
                payloads[index] = rollback ? before : after;
            }
        }

        // Pass 2: write and verify. Everything is already known to be consistent.
        try (RandomAccessFile target = new RandomAccessFile(image, "rw")) {
            for (int index = 0; index < ranges.length; index++) {
                Range range = ranges[index];
                byte[] payload = payloads[index];
                byte[] wanted = rollback ? range.beforeHash : range.afterHash;
                target.seek(range.offset);
                target.write(payload);
                target.getFD().sync();
                target.seek(range.offset);
                byte[] readBack = new byte[range.length];
                target.readFully(readBack);
                if (!Arrays.equals(wanted, Digest.sha256(readBack))) {
                    throw new IOException("Patch read-back verification failed at " + range.offset);
                }
            }
        }
    }

    private static byte[] readPayload(RandomAccessFile patch, long offset, int length)
            throws IOException {
        byte[] payload = new byte[length];
        patch.seek(offset);
        patch.readFully(payload);
        return payload;
    }

    private static final class Range {
        final long offset;
        final int length;
        final byte[] beforeHash;
        final byte[] afterHash;
        final long beforeDataOffset;
        final long afterDataOffset;

        Range(long offset, int length, byte[] beforeHash, byte[] afterHash,
              long beforeDataOffset, long afterDataOffset) {
            this.offset = offset;
            this.length = length;
            this.beforeHash = beforeHash;
            this.afterHash = afterHash;
            this.beforeDataOffset = beforeDataOffset;
            this.afterDataOffset = afterDataOffset;
        }
    }
}
