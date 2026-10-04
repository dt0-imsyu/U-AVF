package com.example.winavf;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;

public final class Lz4BlockTest {
    private static void check(byte[] block, String expected) {
        byte[] want = expected.getBytes(StandardCharsets.UTF_8);
        byte[] got = Lz4Block.decode(block, 0, block.length, want.length, 1024);
        if (!Arrays.equals(want, got)) throw new AssertionError("pixels differ");
    }
    private static void reject(byte[] block, int expected, int limit) {
        try { Lz4Block.decode(block, 0, block.length, expected, limit); }
        catch (IllegalArgumentException correct) { return; }
        throw new AssertionError("malformed block accepted");
    }
    public static void main(String[] args) {
        check(new byte[]{0x50, 'h', 'e', 'l', 'l', 'o'}, "hello");
        check(new byte[]{0x16, 'a', 1, 0, 0}, "aaaaaaaaaaa");
        check(new byte[]{0x32, 'a', 'b', 'c', 3, 0, 0}, "abcabcabc");
        byte[] longLiteral = new byte[264];
        longLiteral[0] = (byte)0xf0; longLiteral[1] = (byte)245;
        Arrays.fill(longLiteral, 2, 262, (byte)'x');
        check(Arrays.copyOf(longLiteral, 262), "x".repeat(260));
        byte[] longMatch = new byte[]{0x1f, 'z', 1, 0, (byte)255, 25, 0};
        check(longMatch, "z".repeat(300));
        reject(new byte[]{0x50,'h'}, 5, 100);
        reject(new byte[]{0x10,'a',0,0}, 5, 100);
        reject(new byte[]{0x10,'a',2,0}, 5, 100);
        reject(new byte[]{(byte)0xf0}, 100, 100);
        reject(new byte[]{0x50,'h','e','l','l','o'}, 5, 4);
        reject(new byte[]{0x50,'h','e','l','l','o'}, 6, 100);
        System.out.println("LZ4_BLOCK_TEST=PASS");
    }
}
