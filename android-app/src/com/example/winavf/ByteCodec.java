// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.RandomAccessFile;

/**
 * Little-endian binary helpers for the boot-media and patch formats.
 *
 * These were scattered through MainActivity as u16/u32/putLe16/putLe32 and
 * readLittleEndian*. Keeping them together makes the on-disk layouts they serve
 * readable, and lets them be tested without an Activity.
 */
final class ByteCodec {
    private ByteCodec() {}

    /** Reads a little-endian unsigned 16-bit value from a byte array. */
    static int u16(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8;
    }

    /** Reads a little-endian unsigned 32-bit value from a byte array. */
    static long u32(byte[] bytes, int at) {
        return (long) u16(bytes, at) | (long) u16(bytes, at + 2) << 16;
    }

    static void putLe16(byte[] bytes, int at, int value) {
        bytes[at] = (byte) value;
        bytes[at + 1] = (byte) (value >>> 8);
    }

    static void putLe32(byte[] bytes, int at, int value) {
        putLe16(bytes, at, value & 0xFFFF);
        putLe16(bytes, at + 2, value >>> 16);
    }

    /** Reads a little-endian 32-bit value from the current file position. */
    static int readLittleEndianInt(RandomAccessFile file) throws java.io.IOException {
        int b0 = file.readUnsignedByte();
        int b1 = file.readUnsignedByte();
        int b2 = file.readUnsignedByte();
        int b3 = file.readUnsignedByte();
        return b0 | b1 << 8 | b2 << 16 | b3 << 24;
    }

    /** Reads a little-endian 64-bit value from the current file position. */
    static long readLittleEndianLong(RandomAccessFile file) throws java.io.IOException {
        return (long) readLittleEndianInt(file) & 0xFFFFFFFFL
                | (long) readLittleEndianInt(file) << 32;
    }
}
