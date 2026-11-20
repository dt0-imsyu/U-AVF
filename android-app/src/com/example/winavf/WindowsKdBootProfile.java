package com.example.winavf;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HexFormat;

/** In-memory bootstrap for the one verified WinPE kernel. The caller holds a KD stop. */
final class WindowsKdBootProfile {
    private static final int PE_OFFSET = 0x120;
    private static final int PE_TIMESTAMP = 0xbadfd933;
    private static final int IMAGE_SIZE = 0x124a000;
    private static final int WRITE_CHUNK_BYTES = 32;

    interface StoppedMemory {
        byte[] read(long address, int size) throws IOException;
        void write(long address, byte[] data) throws IOException;
        default void recoverWrite(long address, byte[] before, byte[] after,
                                  SocketTimeoutException failure) throws IOException {
            throw failure;
        }
    }

    private record Patch(int rva, byte[] before, byte[] after) {}

    // Derived from prepare-native-kd-adapters.py and checked against the WinPE PASS capture.
    private static final Patch[] PATCHES = {
        patch(0xc03c31, "0c", "08"),
        patch(0xc03c32, "0f", "0b"),
        patch(0xc03c38, "14", "10"),
        zeroCave(0x665b20, "102c00129402100bf53c00d0f1d7f817"),
        patch(0x49baec, "554b0090", "0d280714"),
        patch(0x49bacc, "1f054071", "1f010471"),
        zeroCave(0x665b00, "30e33bd59000003610fa7e9230e31bd5df3f03d57fb7ee17"),
        patch(0x213968, "eaffff17", "66481114"),
        zeroCave(0x665c40, "7f2303d5fd7bbca9f35301a9e00702a9fd030091f30300aaf40301aa689240b9"
                + "1f0d0072c1050054689640b9087d1a53680000341f6100712105005460a640f9e1c30091020080d2"
                + "f2eafe9780040034e83340b909691b120aa093526aa7ba723f010a6bc10300540811001249e03bd5"
                + "1f490071820000546a820291497928f80f000014c00000541f750071c20000540a4d0051897a2af8"
                + "09000014699a00f907000014a00000541f79007181000054699e00f90200001469a200f90b9e3bd5"
                + "68a640f90811009168a600f9f35341a9fd7bc4a8ff2303d5c0035fd6e00742a9f35341a9fd7bc4a8"
                + "ff2303d57f2303d5c151f117"),
        patch(0x2ba430, "7f2303d5", "04ae0e14"),
        patch(0x2fbfa0, "7f2303d5", "c0035fd6"),
    };

    private WindowsKdBootProfile() {}

    static void apply(StoppedMemory memory, long base, long stoppedPc) throws IOException {
        if ((base >>> 48) != 0xffff || (base & 4095) != 0 || Long.compareUnsigned(stoppedPc, base) < 0
                || Long.compareUnsigned(stoppedPc - base, IMAGE_SIZE) >= 0) {
            throw new IOException("Windows KD is not stopped in the pinned kernel image");
        }
        expect(memory, base, hex("4d5a"));
        byte[] dosOffset = memory.read(base + 60, 4);
        byte[] pe = memory.read(base + PE_OFFSET, 84);
        if (dosOffset.length != 4 || pe.length != 84
                || WindowsKdWire.littleEndian(dosOffset).getInt() != PE_OFFSET) {
            throw new IOException("Unexpected Windows kernel header");
        }
        var header = WindowsKdWire.littleEndian(pe);
        if (header.getInt(0) != 0x00004550 || Short.toUnsignedInt(header.getShort(4)) != 0xaa64
                || header.getInt(8) != PE_TIMESTAMP || header.getInt(80) != IMAGE_SIZE) {
            throw new IOException("Unexpected Windows ARM64 kernel identity");
        }
        expect(memory, base + 0x49bc4c, hex("08c57039"));
        expect(memory, base + 0x49bc60, hex("08e17039"));
        expect(memory, base + 0x49bc70, hex("08c97039"));
        expect(memory, base + 0x2ba430, hex("7f2303d5f353bba9"));
        expect(memory, base + 0x2fbfa0, hex("7f2303d5f30f1ff8"));
        // Preflight every patch before the first write. Any failure leaves the guest stopped.
        for (Patch patch : PATCHES) expect(memory, base + patch.rva, patch.before);
        for (Patch patch : PATCHES) {
            long address = base + patch.rva;
            for (int offset = 0; offset < patch.after.length; offset += WRITE_CHUNK_BYTES) {
                byte[] part = Arrays.copyOfRange(patch.after, offset,
                        Math.min(offset + WRITE_CHUNK_BYTES, patch.after.length));
                try {
                    memory.write(address + offset, part);
                } catch (SocketTimeoutException missingReply) {
                    byte[] before = Arrays.copyOfRange(patch.before, offset, offset + part.length);
                    memory.recoverWrite(address + offset, before, part, missingReply);
                }
                expect(memory, address + offset, part);
            }
            expect(memory, address, patch.after);
        }
    }

    private static void expect(StoppedMemory memory, long address, byte[] bytes) throws IOException {
        if (!Arrays.equals(memory.read(address, bytes.length), bytes)) {
            throw new IOException("Windows KD memory mismatch at 0x" + Long.toHexString(address));
        }
    }

    private static Patch patch(int rva, String before, String after) {
        return new Patch(rva, hex(before), hex(after));
    }

    private static Patch zeroCave(int rva, String after) {
        byte[] bytes = hex(after);
        return new Patch(rva, new byte[bytes.length], bytes);
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
