// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.CRC32;

/** Read-only checks for the first-boot disk and the partitions created by Windows Setup. */
final class WindowsSetupLayout {
    static final int SECTOR = 512;
    static final int TABLE_BYTES = 128 * 128;
    static final long MEDIA_BYTES = 9126805504L;
    static final long TARGET_BYTES = MEDIA_BYTES + 64L * 1024 * 1024 * 1024;
    private static final long LAST_LBA = TARGET_BYTES / SECTOR - 1;
    private static final long LAST_USABLE = LAST_LBA - 33;
    private static final long TARGET_START = MEDIA_BYTES / SECTOR;
    private static final String FRESH_SHA =
            "5f364daa0d62768e0f46108973f743d1a3b9b5bd29e77ecb60cccd123f23e02e";
    private static final byte[] GPT_SIGNATURE = "EFI PART".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DISK_GUID = HexFormat.of().parseHex("42de74cab0157e4ea6869c21e2d41320");
    private static final byte[] SOURCE_ENTRY = java.util.Base64.getDecoder().decode(
            "KHMqwR/40hG6SwCgyT7JOw2RMHRrZE1Ji6R8TH5GL6kACAAAAAAAAN7/DwEAAAAAAAAAAAAAAABXAGkAbgBkAG8AdwBzACAAUwBlAHQAdQBwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    private static final String BASIC_DATA = "a2a0d0ebe5b9334487c068b6b72699c7";
    private static final String MSR = "16e3c9e35c0bb84d817df92df00215ae";
    private static final String RECOVERY = "a4bb94ded106404da16abfd50179d6ac";

    enum State { FRESH, PARTITIONED, NTFS_TARGET }
    record Inspection(State state, byte[] diskGuid, byte[] targetGuid) {}

    private WindowsSetupLayout() {}

    static State read(Path disk) throws IOException {
        return inspect(disk).state();
    }

    static Inspection inspect(Path disk) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(disk.toFile(), "r")) {
            if (file.length() != TARGET_BYTES) throw new IOException("Unexpected Windows Setup disk size");
            byte[] prefix = new byte[2 * SECTOR], tail = new byte[TABLE_BYTES + SECTOR];
            byte[] entries = new byte[TABLE_BYTES];
            file.readFully(prefix);
            file.readFully(entries);
            file.seek(TARGET_BYTES - tail.length);
            file.readFully(tail);
            long firstBasic = verify(prefix, entries, tail);
            if (firstBasic == 0) return new Inspection(State.FRESH, null, null);
            if (firstBasic < 0) return new Inspection(State.PARTITIONED, null, null);
            byte[] sector = new byte[SECTOR];
            file.seek(firstBasic * SECTOR);
            file.readFully(sector);
            if (!isNtfsBootSector(sector)) return new Inspection(State.PARTITIONED, null, null);
            var partitions = littleEndian(entries);
            for (int at = 128; at < TABLE_BYTES; at += 128) {
                if (partitions.getLong(at + 32) == firstBasic
                        && BASIC_DATA.equals(HexFormat.of().formatHex(entries, at, at + 16))) {
                    return new Inspection(State.NTFS_TARGET, DISK_GUID.clone(),
                            Arrays.copyOfRange(entries, at + 16, at + 32));
                }
            }
            throw new IOException("Windows Setup NTFS target changed");
        }
    }

    static boolean isNtfsBootSector(byte[] sector) {
        return sector.length == SECTOR
                && new String(sector, 3, 8, StandardCharsets.US_ASCII).equals("NTFS    ")
                && Short.toUnsignedInt(littleEndian(sector).getShort(11)) == SECTOR
                && sector[510] == 0x55 && sector[511] == (byte) 0xaa;
    }

    /** Returns zero for fresh GPT, -1 without a basic partition, or the basic partition LBA. */
    static long verify(byte[] prefix, byte[] entries, byte[] tail) throws IOException {
        if (Arrays.mismatch(entries, 0, TABLE_BYTES, tail, 0, TABLE_BYTES) != -1) {
            throw new IOException("Windows Setup primary and backup GPT tables differ");
        }
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException absent) { throw new AssertionError(absent); }
        digest.update(prefix);
        digest.update(tail);
        if (FRESH_SHA.equals(HexFormat.of().formatHex(digest.digest()))) return 0;

        byte[] primary = Arrays.copyOfRange(prefix, SECTOR, 2 * SECTOR);
        byte[] backup = Arrays.copyOfRange(tail, TABLE_BYTES, TABLE_BYTES + SECTOR);
        var mbr = littleEndian(prefix);
        if (prefix[450] != (byte) 0xee || mbr.getInt(454) != 1
                || Integer.toUnsignedLong(mbr.getInt(458)) != LAST_LBA
                || prefix[510] != 0x55 || prefix[511] != (byte) 0xaa) {
            throw new IOException("Invalid Windows Setup protective MBR");
        }
        int tableCrc = crc(entries, entries.length);
        verifyHeader(primary, 1, LAST_LBA, 2, tableCrc);
        verifyHeader(backup, LAST_LBA, 1, LAST_LBA - 32, tableCrc);
        if (Arrays.mismatch(primary, 56, 72, DISK_GUID, 0, 16) != -1
                || Arrays.mismatch(backup, 56, 72, DISK_GUID, 0, 16) != -1
                || Arrays.mismatch(entries, 0, 128, SOURCE_ENTRY, 0, 128) != -1) {
            throw new IOException("Windows Setup source partition or disk identity differs");
        }

        var table = littleEndian(entries);
        long previousEnd = TARGET_START - 1;
        long firstBasic = -1;
        boolean hasTarget = false;
        for (int offset = 128; offset < entries.length; offset += 128) {
            long typeLo = table.getLong(offset), typeHi = table.getLong(offset + 8);
            if (typeLo == 0 && typeHi == 0) continue;
            hasTarget = true;
            long start = table.getLong(offset + 32), end = table.getLong(offset + 40);
            if (start <= previousEnd || end < start || end > LAST_USABLE) {
                throw new IOException("Windows Setup target partition escapes the approved space");
            }
            previousEnd = end;
            String type = HexFormat.of().formatHex(entries, offset, offset + 16);
            switch (type) {
                case BASIC_DATA -> { if (firstBasic < 0) firstBasic = start; }
                case MSR, RECOVERY -> { }
                default -> throw new IOException("Unexpected Windows Setup partition type");
            }
        }
        if (!hasTarget) throw new IOException("Unexpected first-boot GPT identity");
        return firstBasic;
    }

    private static void verifyHeader(byte[] sector, long self, long alternate, long entries,
                                     int tableCrc) throws IOException {
        var header = littleEndian(sector);
        if (Arrays.mismatch(sector, 0, 8, GPT_SIGNATURE, 0, 8) != -1
                || header.getInt(12) != 92 || header.getLong(24) != self
                || header.getLong(32) != alternate || header.getLong(40) != 34
                || header.getLong(48) != LAST_USABLE || header.getLong(72) != entries
                || header.getInt(80) != 128 || header.getInt(84) != 128
                || header.getInt(88) != tableCrc) {
            throw new IOException("Invalid Windows Setup GPT geometry");
        }
        int expected = header.getInt(16);
        header.putInt(16, 0);
        if (crc(sector, 92) != expected) throw new IOException("Invalid Windows Setup GPT checksum");
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int crc(byte[] bytes, int length) {
        CRC32 value = new CRC32();
        value.update(bytes, 0, length);
        return (int) value.getValue();
    }
}
