package com.example.winavf;

/** Optional ARM64 native hot path, with Java fallback and startup parity tests. */
final class WavfNative {
    static final boolean AVAILABLE;
    static {
        boolean enabled = false;
        try {
            System.loadLibrary("wavf");
            // Numeric ARGB parity, including transparent and non-gray pixels.
            byte[] pixels = new byte[4096];
            new java.util.Random(93).nextBytes(pixels);
            int[] colors = new int[pixels.length / 4];
            if (!copyPixels(pixels, colors)) throw new IllegalStateException("Native pixel copy bounds");
            for (int i = 0, p = 0; i < colors.length; i++, p += 4) {
                int expected = (pixels[p] & 255) | ((pixels[p + 1] & 255) << 8)
                        | ((pixels[p + 2] & 255) << 16) | ((pixels[p + 3] & 255) << 24);
                if (colors[i] != expected) throw new IllegalStateException("Native pixel channel parity");
            }
            if (copyPixels(new byte[3], new int[1]))
                throw new IllegalStateException("Native pixel malformed bounds");
            for (int size : new int[]{1, 4, 16, 255, 4096, 65536}) {
                java.io.ByteArrayOutputStream block = new java.io.ByteArrayOutputStream();
                // One literal followed by an overlapping distance-one match.
                if (size < 5) {
                    block.write(size << 4);
                    for (int i = 0; i < size; i++) block.write(0x5a);
                } else {
                    int match = size - 1;
                    block.write(0x10 | Math.min(15, match - 4));
                    block.write(0x5a); block.write(1); block.write(0);
                    if (match >= 19) {
                        int remaining = match - 19;
                        while (remaining >= 255) { block.write(255); remaining -= 255; }
                        block.write(remaining);
                    }
                }
                byte[] encoded = block.toByteArray();
                byte[] actual = new byte[size];
                byte[] expected = Lz4Block.decodeJava(encoded, 0, encoded.length, size, size);
                if (expand(encoded, 0, encoded.length, actual) != size ||
                        !java.util.Arrays.equals(expected, actual))
                    throw new IllegalStateException("Native LZ4 parity");
            }
            java.util.Random random = new java.util.Random(71);
            for (int i = 0; i < 2000; i++) {
                byte[] malformed = new byte[random.nextInt(64)];
                random.nextBytes(malformed);
                int expectedSize = random.nextInt(256);
                byte[] reference = null;
                try { reference = Lz4Block.decodeJava(malformed, 0, malformed.length, expectedSize, 256); }
                catch (IllegalArgumentException expected) { }
                byte[] actual = new byte[expectedSize];
                int result = expand(malformed, 0, malformed.length, actual);
                if ((reference == null) != (result < 0) ||
                        (reference != null && !java.util.Arrays.equals(reference, actual)))
                    throw new IllegalStateException("Native LZ4 malformed parity");
            }
            enabled = true;
        } catch (UnsatisfiedLinkError | RuntimeException unavailable) {
            enabled = false;
        }
        AVAILABLE = enabled;
    }
    static native int expand(byte[] input, int offset, int length, byte[] output);
    static native boolean copyPixels(byte[] bgra, int[] argb);
}
