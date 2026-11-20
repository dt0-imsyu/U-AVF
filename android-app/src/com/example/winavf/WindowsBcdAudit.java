// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Bounded reader for the boot-manager and OS-loader elements of a clean BCD hive. */
final class WindowsBcdAudit {
    private static final int BASE_BLOCK = 4096;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final String BOOT_MANAGER = "{9dea862c-5cdd-4e70-acc1-f32b344d4795}";
    private final Map<Integer, ByteBuffer> cells = new HashMap<>();
    private final ByteBuffer root;

    static void verify(byte[] bytes, byte[] diskGuid, byte[] targetGuid) throws IOException {
        try {
            var hive = new WindowsBcdAudit(bytes);
            if (hive.value(hive.key(hive.root, "Description"), "System", 4).getInt() != 1) {
                throw new IOException("BCD system-store role is missing");
            }
            ByteBuffer objects = hive.key(hive.root, "Objects");
            ByteBuffer manager = hive.key(hive.key(objects, BOOT_MANAGER), "Elements");
            String selected = hive.stringElement(manager, "23000003");
            if (!selected.matches("\\{[0-9a-fA-F-]{36}\\}")) throw new IOException("Invalid BCD default loader ID");
            ByteBuffer loader = hive.key(hive.key(objects, selected), "Elements");
            if (!hive.stringElement(loader, "12000002").equalsIgnoreCase("\\Windows\\system32\\winload.efi")
                    || !hive.stringElement(loader, "22000002").equalsIgnoreCase("\\Windows")) {
                throw new IOException("BCD default is outside the installed Windows directory");
            }
            hive.verifyDevice(loader, "11000001", diskGuid, targetGuid);
            hive.verifyDevice(loader, "21000001", diskGuid, targetGuid);
            for (String debug : new String[]{"16000010", "260000a0"}) {
                ByteBuffer value = hive.value(hive.key(loader, debug), "Element", 3);
                if (value.remaining() != 1 || value.get() != 1) {
                    throw new IOException("Installed Windows debugger settings differ");
                }
            }
        } catch (IndexOutOfBoundsException | java.nio.BufferUnderflowException malformed) {
            throw new IOException("Malformed Windows BCD hive", malformed);
        }
    }

    private WindowsBcdAudit(byte[] bytes) throws IOException {
        if (bytes.length < 2 * BASE_BLOCK || bytes.length > MAX_BYTES) throw new IOException("Invalid BCD size");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int bins = data.getInt(40);
        int end = BASE_BLOCK + bins;
        if (data.getInt(0) != 0x66676572 || data.getInt(4) != data.getInt(8)
                || bins > bytes.length - BASE_BLOCK || bins < BASE_BLOCK) throw new IOException("BCD hive is invalid or dirty");
        int checksum = 0;
        for (int at = 0; at < 508; at += 4) checksum ^= data.getInt(at);
        if (checksum == 0) checksum = 1;
        else if (checksum == -1) checksum = -2;
        if (checksum != data.getInt(508)) throw new IOException("Invalid BCD hive checksum");
        for (int bin = BASE_BLOCK; bin < end; ) {
            int length = data.getInt(bin + 8);
            if (data.getInt(bin) != 0x6e696268 || data.getInt(bin + 4) != bin - BASE_BLOCK
                    || length < BASE_BLOCK || length % BASE_BLOCK != 0 || length > end - bin) {
                throw new IOException("Invalid BCD hive bin");
            }
            int binEnd = bin + length;
            for (int at = bin + 32; at < binEnd; ) {
                int cell = data.getInt(at);
                int size = Math.abs(cell);
                if (size < 8 || size % 8 != 0 || size > binEnd - at) throw new IOException("Invalid BCD cell");
                if (cell < 0) cells.put(at - BASE_BLOCK, data.slice(at + 4, size - 4).order(ByteOrder.LITTLE_ENDIAN));
                at += size;
            }
            bin = binEnd;
        }
        root = cell(data.getInt(36));
    }

    private ByteBuffer key(ByteBuffer parent, String name) throws IOException {
        if (parent.getShort(0) != 0x6b6e) throw new IOException("Invalid BCD key");
        ByteBuffer match = findKey(cell(parent.getInt(28)), name, 0, new int[]{8192});
        if (match == null) throw new IOException("Missing BCD key " + name);
        return match;
    }

    private ByteBuffer findKey(ByteBuffer list, String name, int depth, int[] budget) throws IOException {
        if (depth > 5) throw new IOException("BCD subkey list is too deep");
        int type = Short.toUnsignedInt(list.getShort(0));
        int count = Short.toUnsignedInt(list.getShort(2));
        int stride = switch (type) {
            case 0x666c, 0x686c -> 8; // lf and lh
            case 0x696c, 0x6972 -> 4; // li and ri
            default -> throw new IOException("Invalid BCD subkey list");
        };
        budget[0] -= count;
        if (count > (list.capacity() - 4) / stride || budget[0] < 0) {
            throw new IOException("Invalid BCD subkey count");
        }
        for (int i = 0; i < count; i++) {
            ByteBuffer entry = cell(list.getInt(4 + i * stride));
            if (type == 0x6972) {
                ByteBuffer found = findKey(entry, name, depth + 1, budget);
                if (found != null) return found;
            } else {
                if (entry.getShort(0) != 0x6b6e) throw new IOException("Invalid BCD child key");
                String keyName = name(entry, 76, Short.toUnsignedInt(entry.getShort(72)), (entry.getShort(2) & 0x20) != 0);
                if (keyName.equalsIgnoreCase(name)) return entry;
            }
        }
        return null;
    }

    private ByteBuffer value(ByteBuffer key, String name, int expectedType) throws IOException {
        int count = key.getInt(36);
        if (count < 1 || count > 128) throw new IOException("Invalid BCD value count");
        ByteBuffer values = cell(key.getInt(40));
        for (int i = 0; i < count; i++) {
            ByteBuffer entry = cell(values.getInt(i * 4));
            if (entry.getShort(0) != 0x6b76) throw new IOException("Invalid BCD value");
            if (!name(entry, 20, Short.toUnsignedInt(entry.getShort(2)), (entry.getShort(16) & 1) != 0)
                    .equalsIgnoreCase(name)) continue;
            int size = entry.getInt(4);
            int length = size & 0x7fffffff;
            if (entry.getInt(12) != expectedType || length > 512 || length == 0 || (size < 0 && length > 4)) {
                throw new IOException("Unexpected BCD value format");
            }
            return (size < 0 ? entry.slice(8, length) : cell(entry.getInt(8)).slice(0, length))
                    .order(ByteOrder.LITTLE_ENDIAN);
        }
        throw new IOException("Missing BCD value " + name);
    }

    private String stringElement(ByteBuffer elements, String id) throws IOException {
        ByteBuffer value = value(key(elements, id), "Element", 1);
        int length = value.remaining();
        if (length < 2 || length % 2 != 0 || value.getShort(length - 2) != 0) throw new IOException("Invalid BCD string");
        return name(value, 0, length - 2, false);
    }

    private void verifyDevice(ByteBuffer loader, String id, byte[] diskGuid, byte[] targetGuid) throws IOException {
        ByteBuffer value = value(key(loader, id), "Element", 3);
        byte[] bytes = new byte[value.remaining()];
        value.get(bytes);
        if (bytes.length != 88 || value.getInt(16) != 6 || value.getInt(24) != 72
                || Arrays.mismatch(bytes, 32, 48, targetGuid, 0, 16) != -1
                || Arrays.mismatch(bytes, 56, 72, diskGuid, 0, 16) != -1) {
            throw new IOException("BCD loader device differs from the Windows GPT target");
        }
    }

    private ByteBuffer cell(int offset) throws IOException {
        ByteBuffer result = cells.get(offset);
        if (result == null) throw new IOException("Missing BCD hive cell");
        return result;
    }

    private static String name(ByteBuffer source, int offset, int length, boolean ascii) {
        byte[] bytes = new byte[length];
        source.slice(offset, length).get(bytes);
        return new String(bytes, ascii ? StandardCharsets.US_ASCII : StandardCharsets.UTF_16LE);
    }
}
