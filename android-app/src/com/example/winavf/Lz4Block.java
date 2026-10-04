package com.example.winavf;

/** Bounded LZ4 block decoder. No frame wrapper, dictionaries or native dependencies. */
final class Lz4Block {
    static byte[] decode(byte[] input, int offset, int length, int expected, int limit) {
        if (offset < 0 || length < 0 || offset > input.length - length
                || expected < 0 || expected > limit) throw new IllegalArgumentException("LZ4 bounds");
        if (WavfNative.AVAILABLE) {
            byte[] output = new byte[expected];
            if (WavfNative.expand(input, offset, length, output) != expected)
                throw new IllegalArgumentException("LZ4 native invalid block");
            return output;
        }
        return decodeJava(input, offset, length, expected, limit);
    }

    static byte[] decodeJava(byte[] input, int offset, int length, int expected, int limit) {
        if (offset < 0 || length < 0 || offset > input.length - length
                || expected < 0 || expected > limit) throw new IllegalArgumentException("LZ4 bounds");
        byte[] output = new byte[expected];
        int end = offset + length, cursor = offset, written = 0;
        while (cursor < end) {
            int token = input[cursor++] & 255;
            int literal = token >>> 4;
            if (literal == 15) {
                int extension;
                do {
                    if (cursor == end) throw new IllegalArgumentException("LZ4 literal truncated");
                    extension = input[cursor++] & 255;
                    if (literal > expected - extension) throw new IllegalArgumentException("LZ4 literal overflow");
                    literal += extension;
                } while (extension == 255);
            }
            if (literal > end - cursor || literal > expected - written)
                throw new IllegalArgumentException("LZ4 literal bounds");
            System.arraycopy(input, cursor, output, written, literal);
            cursor += literal; written += literal;
            if (cursor == end) break;
            if (end - cursor < 2) throw new IllegalArgumentException("LZ4 match truncated");
            int distance = (input[cursor] & 255) | ((input[cursor + 1] & 255) << 8);
            cursor += 2;
            if (distance == 0 || distance > written) throw new IllegalArgumentException("LZ4 distance");
            int match = (token & 15) + 4;
            if ((token & 15) == 15) {
                int extension;
                do {
                    if (cursor == end) throw new IllegalArgumentException("LZ4 match length truncated");
                    extension = input[cursor++] & 255;
                    if (match > expected - extension) throw new IllegalArgumentException("LZ4 match overflow");
                    match += extension;
                } while (extension == 255);
            }
            if (match > expected - written) throw new IllegalArgumentException("LZ4 output overflow");
            // Expand overlaps by doubling an already initialized prefix, never
            // copying from uninitialized bytes as one large arraycopy would.
            int initialized = distance;
            while (match > 0) {
                int count = Math.min(match, initialized);
                System.arraycopy(output, written - initialized, output, written, count);
                written += count; match -= count; initialized += count;
            }
        }
        if (written != expected) throw new IllegalArgumentException("LZ4 size mismatch");
        return output;
    }
}
