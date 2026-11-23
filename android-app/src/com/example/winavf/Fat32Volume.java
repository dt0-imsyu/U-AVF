// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Read-only FAT32 access to the Windows Setup ESP.
 *
 * Extracted from MainActivity so the boot-media audit can be read and tested on
 * its own. Every path here is read-only; nothing writes to the volume.
 *
 * The ESP starts at byte 1 MiB of the prepared disk, which is why the geometry
 * is read from that absolute offset rather than from sector 0.
 */
final class Fat32Volume implements AutoCloseable {
    private static final long ESP_OFFSET = 1024L * 1024;
    private static final int SECTOR = 512;
    private static final long FAT_EOC = 0x0FFFFFF8L;
    private static final long FAT_MASK = 0x0FFFFFFFL;
    private static final int ATTRIBUTE_DIRECTORY = 0x10;
    private static final int ATTRIBUTE_LONG_NAME = 0x0F;
    private static final int DIRECTORY_ENTRY = 32;
    private static final int LONG_NAME_SLOTS = 21;
    private static final int MAX_READ_BYTES = 1024 * 1024;

    private final RandomAccessFile file;
    private final int bytesPerSector;
    private final int sectorsPerCluster;
    private final long fatOffset;
    private final long dataOffset;
    private final int clusterBytes;
    private final long rootCluster;

    private Fat32Volume(RandomAccessFile file, byte[] boot) throws IOException {
        this.file = file;
        this.bytesPerSector = u16(boot, 11);
        this.sectorsPerCluster = boot[13] & 0xFF;
        int reservedSectors = u16(boot, 14);
        int fatCount = boot[16] & 0xFF;
        long sectorsPerFat = u32(boot, 36);
        this.rootCluster = u32(boot, 44);
        if (bytesPerSector != SECTOR || sectorsPerCluster == 0 || fatCount != 2 || rootCluster < 2) {
            throw new IOException("Unexpected FAT32 geometry");
        }
        this.clusterBytes = bytesPerSector * sectorsPerCluster;
        this.fatOffset = ESP_OFFSET + (long) reservedSectors * bytesPerSector;
        this.dataOffset = ESP_OFFSET + ((long) reservedSectors + (long) fatCount * sectorsPerFat)
                * bytesPerSector;
    }

    /** Opens the FAT32 volume that starts at byte 1 MiB of {@code disk}. */
    static Fat32Volume open(File disk) throws IOException {
        RandomAccessFile file = new RandomAccessFile(disk, "r");
        try {
            file.seek(ESP_OFFSET);
            byte[] boot = new byte[SECTOR];
            file.readFully(boot);
            return new Fat32Volume(file, boot);
        } catch (IOException | RuntimeException error) {
            file.close();
            throw error;
        }
    }

    /**
     * Lists the names of root-directory files whose 8.3 extension matches.
     *
     * Used by the WinPE marker scan, which looks for an arbitrary .TXT name
     * rather than a known file.
     */
    java.util.List<String> rootFileNames(String extension) throws IOException {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (long cluster : chain(rootCluster)) {
            byte[] block = readCluster(cluster);
            String[] longName = new String[LONG_NAME_SLOTS];
            for (int at = 0; at + DIRECTORY_ENTRY <= block.length; at += DIRECTORY_ENTRY) {
                if (block[at] == 0) return names;
                if (block[at] == (byte) 0xE5) {
                    java.util.Arrays.fill(longName, null);
                    continue;
                }
                int attributes = block[at + 11] & 0xFF;
                if (attributes == ATTRIBUTE_LONG_NAME) {
                    int slot = block[at] & 0x1F;
                    if (slot > 0 && slot < longName.length) longName[slot] = longNamePart(block, at);
                    continue;
                }
                java.util.Arrays.fill(longName, null);
                if ((attributes & ATTRIBUTE_DIRECTORY) != 0) continue;
                if (extension.equals(ascii(block, at + 8, 3))) {
                    names.add(ascii(block, at, 8) + "." + extension);
                }
            }
        }
        return names;
    }

    /** Reads a root-directory file addressed by its 8.3 name and extension. */
    byte[] readRootFileByExtension(String name, String extension, long maximumBytes)
            throws IOException {
        int dot = name.indexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return readRootFile(base, extension, (int) maximumBytes);
    }

    /** Reads a file by path components, for example {@code readFile("EFI", "BOOT", "BCD")}. */
    byte[] readFile(String... path) throws IOException {
        Entry file = findPath(path);
        if (file == null || (file.attributes & ATTRIBUTE_DIRECTORY) != 0) return null;
        return readChain(file.cluster, file.size);
    }

    /** Checks directory entries without allocating memory for the file contents. */
    boolean exists(String... path) throws IOException {
        return findPath(path) != null;
    }

    private Entry findPath(String... path) throws IOException {
        if (path.length == 0) throw new IllegalArgumentException("A file path is required");
        long directory = rootCluster;
        for (int index = 0; index < path.length - 1; index++) {
            Entry entry = find(directory, path[index]);
            if (entry == null || (entry.attributes & ATTRIBUTE_DIRECTORY) == 0) return null;
            directory = entry.cluster;
        }
        return find(directory, path[path.length - 1]);
    }

    /** Reads a file that lives in the root directory, addressed as 8.3 name plus extension. */
    byte[] readRootFile(String name, String extension, int maximumBytes) throws IOException {
        for (long cluster = rootCluster, visited = 0;
             cluster >= 2 && cluster < FAT_EOC && visited < 1024;
             cluster = nextCluster(cluster), visited++) {
            byte[] block = readCluster(cluster);
            for (int at = 0; at + DIRECTORY_ENTRY <= block.length; at += DIRECTORY_ENTRY) {
                if (block[at] == 0) return null;
                int attributes = block[at + 11] & 0xFF;
                if (block[at] == (byte) 0xE5 || attributes == ATTRIBUTE_LONG_NAME
                        || (attributes & ATTRIBUTE_DIRECTORY) != 0) {
                    continue;
                }
                if (!name.equals(ascii(block, at, 8)) || !extension.equals(ascii(block, at + 8, 3))) {
                    continue;
                }
                long size = u32(block, at + 28);
                if (size <= 0 || size > maximumBytes) {
                    throw new IOException("FAT32 file has an invalid or excessive size: " + size);
                }
                return readChain(clusterOf(block, at), size);
            }
        }
        return null;
    }

    /** Returns the entry, or null when the directory has no such name. */
    private Entry find(long directory, String path) throws IOException {
        for (long cluster : chain(directory)) {
            byte[] block = readCluster(cluster);
            String[] longName = new String[LONG_NAME_SLOTS];
            for (int at = 0; at + DIRECTORY_ENTRY <= block.length; at += DIRECTORY_ENTRY) {
                int first = block[at] & 0xFF;
                if (first == 0) return null;
                if (first == 0xE5) {
                    java.util.Arrays.fill(longName, null);
                    continue;
                }
                int attributes = block[at + 11] & 0xFF;
                if (attributes == ATTRIBUTE_LONG_NAME) {
                    int slot = block[at] & 0x1F;
                    if (slot > 0 && slot < longName.length) {
                        longName[slot] = longNamePart(block, at);
                    }
                    continue;
                }
                String shortName = ascii(block, at, 8);
                String extension = ascii(block, at + 8, 3);
                String full = extension.isEmpty() ? shortName : shortName + "." + extension;
                StringBuilder assembled = new StringBuilder();
                for (int slot = 1; slot < longName.length; slot++) {
                    if (longName[slot] != null) assembled.append(longName[slot]);
                }
                String resolved = assembled.length() == 0 ? full : assembled.toString();
                java.util.Arrays.fill(longName, null);
                if (path.equalsIgnoreCase(resolved) || path.equalsIgnoreCase(full)) {
                    return new Entry(clusterOf(block, at), u32(block, at + 28), attributes);
                }
            }
        }
        return null;
    }

    private byte[] readChain(long startCluster, long size) throws IOException {
        if (size > Integer.MAX_VALUE) {
            throw new IOException("Read-only export is bounded to 2 GiB");
        }
        byte[] result = new byte[(int) size];
        int filled = 0;
        for (long cluster : chain(startCluster)) {
            int chunk = (int) Math.min(clusterBytes, size - filled);
            if (chunk <= 0) break;
            file.seek(dataOffset + (cluster - 2) * clusterBytes);
            file.readFully(result, filled, chunk);
            filled += chunk;
        }
        if (filled != size) throw new IOException("FAT chain shorter than file size");
        return result;
    }

    /** Follows the FAT chain, rejecting loops and out-of-range clusters. */
    private List<Long> chain(long start) throws IOException {
        List<Long> clusters = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (long cluster = start; cluster >= 2 && cluster < FAT_EOC; cluster = nextCluster(cluster)) {
            if (!seen.add(cluster)) throw new IOException("FAT loop at " + cluster);
            clusters.add(cluster);
        }
        return clusters;
    }

    private long nextCluster(long cluster) throws IOException {
        byte[] entry = new byte[4];
        file.seek(fatOffset + cluster * 4);
        file.readFully(entry);
        return u32(entry, 0) & FAT_MASK;
    }

    private byte[] readCluster(long cluster) throws IOException {
        if (clusterBytes <= 0 || clusterBytes > MAX_READ_BYTES) {
            throw new IOException("Unexpected FAT32 cluster size: " + clusterBytes);
        }
        byte[] block = new byte[clusterBytes];
        file.seek(dataOffset + (cluster - 2) * clusterBytes);
        file.readFully(block);
        return block;
    }

    /** A long file name fragment, as laid out in a VFAT directory entry. */
    private static String longNamePart(byte[] block, int at) {
        int[] offsets = {1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
        StringBuilder name = new StringBuilder();
        for (int offset : offsets) {
            int character = u16(block, at + offset);
            if (character == 0 || character == 0xFFFF) break;
            name.append((char) character);
        }
        return name.toString();
    }

    private static long clusterOf(byte[] block, int at) {
        return (long) u16(block, at + 20) << 16 | u16(block, at + 26);
    }

    private static String ascii(byte[] block, int at, int length) {
        return new String(block, at, length, StandardCharsets.US_ASCII).trim();
    }

    private static int u16(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] bytes, int at) {
        return (long) u16(bytes, at) | (long) u16(bytes, at + 2) << 16;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    private static final class Entry {
        final long cluster;
        final long size;
        final int attributes;

        Entry(long cluster, long size, int attributes) {
            this.cluster = cluster;
            this.size = size;
            this.attributes = attributes;
        }
    }
}
