// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Random;

/**
 * Exercises the WAVFPAT1 apply/rollback engine without a device.
 *
 * The patch engine rewrites a runtime disk image, so a mistake here corrupts a
 * user workspace. This test builds a synthetic image and patch, applies it,
 * rolls it back, and checks that every hash guard rejects a tampered input.
 */
public final class ImagePatchTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("uavf-image-patch-test-");
        try {
            File image = root.resolve("image.raw").toFile();
            byte[] baseline = new byte[64 * 1024];
            new Random(42).nextBytes(baseline);
            Files.write(image.toPath(), baseline);
            String baselineHash = hash(baseline);

            Range first = new Range(4096, 512, baseline, new Random(7));
            Range second = new Range(32000, 1024, baseline, new Random(8));

            File patch = root.resolve("patch.bin").toFile();
            writePatch(patch, baseline.length, baselineHash, first, second);

            // Apply.
            ImagePatch.apply(patch, image, false);
            byte[] applied = Files.readAllBytes(image.toPath());
            check(first.matches(applied), "first range applied");
            check(second.matches(applied), "second range applied");
            check(!baselineHash.equals(hash(applied)), "image changed after apply");

            // targetsBaseline must reject the patched image.
            check(!ImagePatch.targetsBaseline(patch, image), "patched image is not the baseline");

            // Roll back.
            ImagePatch.apply(patch, image, true);
            byte[] restored = Files.readAllBytes(image.toPath());
            check(Arrays.equals(restored, baseline), "rollback restores every byte");
            check(baselineHash.equals(hash(restored)), "rollback restores the baseline hash");
            check(ImagePatch.targetsBaseline(patch, image), "restored image matches the baseline");

            // Applying twice must be refused: the on-disk state no longer matches.
            check(ImagePatch.targetsBaseline(patch, image), "still baseline before re-apply");
            ImagePatch.apply(patch, image, false);
            expectFailure(() -> ImagePatch.apply(patch, image, false), "double apply is refused");
            ImagePatch.apply(patch, image, true);
            check(Arrays.equals(Files.readAllBytes(image.toPath()), baseline), "second rollback clean");

            // A tampered payload must be caught by the payload hash.
            File tampered = root.resolve("tampered.bin").toFile();
            byte[] bytes = Files.readAllBytes(patch.toPath());
            bytes[bytes.length - 1] ^= 0x5A;
            Files.write(tampered.toPath(), bytes);
            expectFailure(() -> ImagePatch.apply(tampered, image, false), "tampered payload is refused");

            // Wrong magic.
            File wrongMagic = root.resolve("wrong-magic.bin").toFile();
            byte[] bad = Files.readAllBytes(patch.toPath());
            bad[0] = 'X';
            Files.write(wrongMagic.toPath(), bad);
            expectFailure(() -> ImagePatch.apply(wrongMagic, image, false), "wrong magic is refused");

            // Patch recorded for a different image size.
            File wrongSize = root.resolve("wrong-size.bin").toFile();
            writePatch(wrongSize, baseline.length + 1, baselineHash, first);
            expectFailure(() -> ImagePatch.apply(wrongSize, image, false), "size mismatch is refused");

            // Patch recorded for a different baseline hash.
            File wrongBaseline = root.resolve("wrong-baseline.bin").toFile();
            writePatch(wrongBaseline, baseline.length, "0".repeat(64), first);
            expectFailure(() -> ImagePatch.apply(wrongBaseline, image, false),
                    "baseline mismatch is refused");

            // Range beyond the end of the image.
            File outOfRange = root.resolve("out-of-range.bin").toFile();
            Range past = new Range(baseline.length - 16, 512, baseline, new Random(9));
            writePatch(outOfRange, baseline.length, baselineHash, past);
            expectFailure(() -> ImagePatch.apply(outOfRange, image, false), "out-of-range is refused");

            check(Arrays.equals(Files.readAllBytes(image.toPath()), baseline),
                    "refused patches leave the image untouched");
        } finally {
            deleteTree(root);
        }
        System.out.println("IMAGE_PATCH_TEST=PASS");
    }

    /** One patch range plus the bytes it should become. */
    private static final class Range {
        final int offset;
        final int length;
        final byte[] before;
        final byte[] after;

        Range(int offset, int length, byte[] image, Random random) {
            this.offset = offset;
            this.length = length;
            this.before = Arrays.copyOfRange(image, offset, offset + length);
            this.after = new byte[length];
            random.nextBytes(this.after);
        }

        boolean matches(byte[] image) {
            return Arrays.equals(Arrays.copyOfRange(image, offset, offset + length), after);
        }
    }

    private static void writePatch(File target, long imageSize, String baselineHash,
                                   Range... ranges) throws Exception {
        try (RandomAccessFile out = new RandomAccessFile(target, "rw")) {
            out.setLength(0);
            out.write(ImagePatch.MAGIC);
            writeInt(out, 1);
            writeLong(out, imageSize);
            out.write(HexFormat.of().parseHex(baselineHash));
            writeInt(out, ranges.length);
            for (Range range : ranges) {
                writeRange(out, range);
            }
        }
    }

    private static void writeRange(RandomAccessFile out, Range range) throws Exception {
        writeLong(out, range.offset);
        writeInt(out, range.length);
        out.write(sha(range.before));
        out.write(sha(range.after));
        out.write(range.after);
        out.write(range.before);
    }

    private static void writeInt(RandomAccessFile out, int value) throws Exception {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeLong(RandomAccessFile out, long value) throws Exception {
        writeInt(out, (int) value);
        writeInt(out, (int) (value >>> 32));
    }

    private static byte[] sha(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(sha(data)).toUpperCase(Locale.ROOT);
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void expectFailure(Action action, String what) throws Exception {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError("Expected refusal: " + what);
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("Failed: " + what);
    }

    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
        }
    }
}
