package com.example.winavf;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HexFormat;

/** In-memory bootstrap for verified WinPE kernels. The caller holds a KD stop. */
final class WindowsKdBootProfile {
    private static final int PE_OFFSET = 0x120;
    private static final int WRITE_CHUNK_BYTES = 32;

    interface StoppedMemory {
        byte[] read(long address, int size) throws IOException;
        void write(long address, byte[] data) throws IOException;
        default void recoverWrite(long address, byte[] before, byte[] after,
                                  SocketTimeoutException failure) throws IOException {
            throw failure;
        }
    }

    enum Build { WINPE_6584, WINPE_9457 }

    private record Patch(int rva, byte[] before, byte[] after) {}
    private record Anchor(int rva, byte[] expected) {}
    private record Profile(Build build, int timestamp, int imageSize, Anchor[] anchors, Patch[] patches) {}

    // Derived from prepare-native-kd-adapters.py and checked against the WinPE PASS capture.
    private static final Profile WINPE_6584 = new Profile(Build.WINPE_6584, 0xbadfd933, 0x124a000, new Anchor[] {
        anchor(0x49bc4c, "08c57039"),
        anchor(0x49bc60, "08e17039"),
        anchor(0x49bc70, "08c97039"),
        anchor(0x2ba430, "7f2303d5f353bba9"),
        anchor(0x2fbfa0, "7f2303d5f30f1ff8"),
    }, new Patch[] {
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
    });

    // WinPE 26100.9457 from the English Windows 11 ARM64 26H2 ISO.
    // The installer gate stays closed until this profile completes Setup on hardware.
    private static final Profile WINPE_9457 = new Profile(Build.WINPE_9457, 0x409ce488, 0x124a000,
            new Anchor[] {
                anchor(0x497b78, "7f2303d5f353bda9"),
                anchor(0x497bc0, "ab9a44f9"),
                anchor(0x2b69a0, "7f2303d5f353bba9"),
                anchor(0x2f7b50, "7f2303d5f30f1ff8"),
                anchor(0x62de20, "230080d2845582d2"),
            }, new Patch[] {
                patch(0xc03c31, "0c", "08"),
                patch(0xc03c38, "0f", "0b"),
                patch(0xc03c39, "14", "10"),
                zeroCave(0x62e780, "102c00129402100bb53e00d00da5f917"),
                patch(0x497bbc, "754b00b0", "f15a0614"),
                patch(0x497b9c, "1f054071", "1f010471"),
                zeroCave(0x62e760, "30e33bd59000003610fa7e9230e31bd5df3f03d57398ef17"),
                patch(0x214998, "eaffff17", "72671014"),
                zeroCave(0x62e800, "7f2303d5fd7bbca9f35301a9e00702a9fd030091f30300aaf40301aa689240b9"
                        + "1f0d0072c1050054689640b9087d1a53680000341f6100712105005460a640f9e1c30091020080d2"
                        + "76fdff9780040034e83340b909691b120aa093526aa7ba723f010a6bc10300540811001249e03bd5"
                        + "1f490071820000546a820291497928f80f000014c00000541f750071c20000540a4d0051897a2af8"
                        + "09000014699a00f907000014a00000541f79007181000054699e00f90200001469a200f90b9e3bd5"
                        + "68a640f90811009168a600f9f35341a9fd7bc4a8ff2303d5c0035fd6e00742a9f35341a9fd7bc4a8"
                        + "ff2303d57f2303d52d20f217"),
                patch(0x2b69a0, "7f2303d5", "98df0d14"),
                patch(0x2f7b50, "7f2303d5", "c0035fd6"),
            });

    private WindowsKdBootProfile() {}

    static Build apply(StoppedMemory memory, long base, long stoppedPc) throws IOException {
        if ((base >>> 48) != 0xffff || (base & 4095) != 0 || Long.compareUnsigned(stoppedPc, base) < 0) {
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
        if (header.getInt(0) != 0x00004550 || Short.toUnsignedInt(header.getShort(4)) != 0xaa64) {
            throw new IOException("Windows guest did not expose an ARM64 PE kernel");
        }
        Profile profile = profileFor(header.getInt(8), header.getInt(80));
        if (Long.compareUnsigned(stoppedPc - base, profile.imageSize()) >= 0) {
            throw new IOException("Windows KD is not stopped in the pinned kernel image");
        }
        for (Anchor anchor : profile.anchors()) expect(memory, base + anchor.rva(), anchor.expected());
        // Preflight every patch before the first write. Any failure leaves the guest stopped.
        for (Patch patch : profile.patches()) expect(memory, base + patch.rva, patch.before);
        for (Patch patch : profile.patches()) {
            applyPatch(memory, base, patch);
        }
        return profile.build();
    }

    private static Profile profileFor(int timestamp, int imageSize) throws IOException {
        for (Profile profile : new Profile[] {WINPE_6584, WINPE_9457}) {
            if (timestamp == profile.timestamp() && imageSize == profile.imageSize()) return profile;
        }
        throw new IOException(String.format(java.util.Locale.ROOT,
                "Windows ARM64 kernel has no matching U-AVF boot profile (PE timestamp 0x%08x, image size 0x%x). "
                + "Use the tested Windows 11 ARM64 25H2 build 26200.6584.",
                timestamp, imageSize));
    }

    private static void applyPatch(StoppedMemory memory, long base, Patch patch) throws IOException {
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

    private static void expect(StoppedMemory memory, long address, byte[] bytes) throws IOException {
        if (!Arrays.equals(memory.read(address, bytes.length), bytes)) {
            throw new IOException("Windows KD memory mismatch at 0x" + Long.toHexString(address));
        }
    }

    private static Patch patch(int rva, String before, String after) {
        return new Patch(rva, hex(before), hex(after));
    }

    private static Anchor anchor(int rva, String expected) {
        return new Anchor(rva, hex(expected));
    }

    private static Patch zeroCave(int rva, String after) {
        byte[] bytes = hex(after);
        return new Patch(rva, new byte[bytes.length], bytes);
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
