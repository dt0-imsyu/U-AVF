package com.example.winavf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Checks both retained first-boot metadata and a read-only snapshot of the installed test disk. */
public final class WindowsSetupLayoutTest {
    public static void main(String[] args) throws Exception {
        byte[] source = new byte[34 * 512];
        try (var template = Files.newInputStream(Path.of(args[0]))) {
            if (template.readNBytes(source, 0, source.length) != source.length) throw new AssertionError("template");
        }
        Path geometry = Path.of(args[1]);
        byte[] prefix = Arrays.copyOf(source, 1024);
        System.arraycopy(Files.readAllBytes(geometry.resolve("patch-0.bin")), 0, prefix, 0, 512);
        System.arraycopy(Files.readAllBytes(geometry.resolve("patch-1.bin")), 0, prefix, 512, 512);
        byte[] table = Arrays.copyOfRange(source, 1024, source.length);
        byte[] tail = new byte[33 * 512];
        System.arraycopy(Files.readAllBytes(geometry.resolve("patch-2.bin")), 0, tail, 0, table.length);
        System.arraycopy(Files.readAllBytes(geometry.resolve("patch-3.bin")), 0, tail, table.length, 512);
        if (WindowsSetupLayout.verify(prefix, table, tail) != 0) throw new AssertionError("fresh disk");

        byte[] installed = Files.readAllBytes(Path.of(args[2]));
        prefix = Arrays.copyOf(installed, 1024);
        table = Arrays.copyOfRange(installed, 1024, 34 * 512);
        tail = Arrays.copyOfRange(installed, 34 * 512, 67 * 512);
        if (WindowsSetupLayout.verify(prefix, table, tail) != 17858560) throw new AssertionError("NTFS LBA");
        byte[] ntfs = Arrays.copyOfRange(installed, 67 * 512, installed.length);
        if (!WindowsSetupLayout.isNtfsBootSector(ntfs)) throw new AssertionError("NTFS boot sector");
        ntfs[510] = 0;
        if (WindowsSetupLayout.isNtfsBootSector(ntfs)) throw new AssertionError("Damaged NTFS boot sector");

        table[128] ^= 1;
        reject(prefix, table, tail, "primary and backup");
        table[128] ^= 1;
        prefix[512 + 16] ^= 1;
        reject(prefix, table, tail, "checksum");
        prefix[512 + 16] ^= 1;

        // An internally consistent but overlapping GPT must still be rejected.
        littleEndian(table).putLong(2 * 128 + 32, 17825792);
        System.arraycopy(table, 0, tail, 0, table.length);
        fixHeader(prefix, 512, table);
        fixHeader(tail, table.length, table);
        reject(prefix, table, tail, "approved space");
        System.out.println("WINDOWS_SETUP_LAYOUT_PASS fresh=1 installedGeometry=1 malformed=3");
    }

    private static void reject(byte[] prefix, byte[] table, byte[] tail, String reason) throws Exception {
        try {
            WindowsSetupLayout.verify(prefix, table, tail);
            throw new AssertionError("Accepted " + reason);
        } catch (IOException expected) {
            if (!expected.getMessage().contains(reason)) throw expected;
        }
    }

    private static void fixHeader(byte[] data, int at, byte[] table) {
        var fields = littleEndian(data);
        fields.putInt(at + 88, crc(table, 0, table.length));
        fields.putInt(at + 16, 0);
        fields.putInt(at + 16, crc(data, at, 92));
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int crc(byte[] bytes, int start, int length) {
        var value = new CRC32();
        value.update(bytes, start, length);
        return (int) value.getValue();
    }
}
