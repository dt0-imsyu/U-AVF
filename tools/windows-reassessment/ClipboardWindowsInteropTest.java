package com.example.winavf;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Reads actual native-Windows output with the unchanged Android wire code. */
public final class ClipboardWindowsInteropTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new AssertionError("One native vector required");
        String expected = "Android ↔ Windows\nПривет\n😄\n";
        byte[] nativePacket = Files.readAllBytes(Path.of(args[0]));
        ClipboardWire.Message message = ClipboardWire.read(new ByteArrayInputStream(nativePacket));
        if (message.type != ClipboardWire.TEXT || !expected.equals(message.text))
            throw new AssertionError("Native Windows vector mismatch");
        if (!Arrays.equals(nativePacket, ClipboardWire.encode(ClipboardWire.TEXT, expected)))
            throw new AssertionError("Android encoder bytes differ");
        System.out.println("ACTUAL_NATIVE_WINDOWS_TO_ANDROID_UCIP_VECTOR=PASS");
    }
}
