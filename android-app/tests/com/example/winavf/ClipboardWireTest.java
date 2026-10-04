package com.example.winavf;
import java.io.*;
import java.util.Arrays;
public class ClipboardWireTest {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static void rejects(byte[] data) throws Exception {
        try { ClipboardWire.read(new ByteArrayInputStream(data)); throw new AssertionError("accepted bad packet"); }
        catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        String text = "Android ↔ Ubuntu\nПривет\n😄\n";
        byte[] packet = ClipboardWire.encode(1, text);
        ClipboardWire.Message result = ClipboardWire.read(new ByteArrayInputStream(packet));
        check(result.type == 1 && result.text.equals(text));
        check(ClipboardWire.read(new ByteArrayInputStream(ClipboardWire.encode(2, ""))).type == 2);
        byte[] bad = packet.clone(); bad[8] = -1; bad[9] = -1; bad[10] = -1; bad[11] = 127; rejects(bad);
        rejects(Arrays.copyOf(packet, packet.length - 1));
        bad = packet.clone(); bad[6] = 1; rejects(bad);
        bad = ClipboardWire.encode(1, "a"); bad[12] = (byte)0xff; rejects(bad);
        try { ClipboardWire.encode(1, "x".repeat(ClipboardWire.MAX_BYTES + 1)); throw new AssertionError(); }
        catch (IOException expected) { }
        System.out.println("CLIPBOARD_WIRE_TESTS=PASS");
    }
}
