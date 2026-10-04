package com.example.winavf;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Lz4NativeFixtureTest {
    public static void main(String[] args) throws Exception {
        byte[] payload = HexFormat.of().parseHex(args[0]);
        int size = (payload[0] & 255) | ((payload[1] & 255) << 8)
                | ((payload[2] & 255) << 16) | ((payload[3] & 255) << 24);
        if (size != Integer.parseInt(args[1])) throw new AssertionError("fixture size");
        byte[] decoded = Lz4Block.decode(payload, 4, payload.length - 4, size, 16*1024*1024);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(decoded));
        if (!hash.equalsIgnoreCase(args[2])) throw new AssertionError("native LZ4 differs");
        System.out.println("NATIVE_LZ4_TO_JAVA=PASS");
    }
}
