package com.example.winavf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.CRC32;

/** Builds one GPT disk containing the U-AVF ESP, untouched Ubuntu ISO, and install target. */
final class PersistentUbuntuDisk {
    static final long SECTOR = 512L;
    static final long MIB = 1024L * 1024L;
    static final long ESP_PREFIX_BYTES = 128L * MIB;
    static final long ESP_PARTITION_OFFSET = MIB;
    static final long ESP_PARTITION_BYTES = 126L * MIB;
    static final long TARGET_BYTES = 32L * 1024L * MIB;
    static final long ISO_FIRST_LBA = ESP_PREFIX_BYTES / SECTOR;
    static final int GPT_ENTRY_COUNT = 128;
    static final int GPT_ENTRY_BYTES = 128;
    static final long GPT_ENTRIES_BYTES = (long) GPT_ENTRY_COUNT * GPT_ENTRY_BYTES;
    static final String ESP_TYPE = "c12a7328-f81f-11d2-ba4b-00a0c93ec93b";
    static final String LINUX_TYPE = "0fc63daf-8483-4772-8e79-3d69d8477de4";

    private PersistentUbuntuDisk() { }

    static long expectedDiskBytes(long isoBytes) {
        return expectedDiskBytes(isoBytes,TARGET_BYTES);
    }

    static long expectedDiskBytes(long isoBytes,long rootBytes) {
        if(rootBytes<TARGET_BYTES || rootBytes>1024L*1024*MIB || rootBytes%MIB!=0)
            throw new IllegalArgumentException("Root size must be 32–1024 GiB and MiB-aligned");
        long isoSectors = isoBytes / SECTOR;
        long targetFirst = alignLba(ISO_FIRST_LBA + isoSectors, 2048L);
        long targetSectors = rootBytes / SECTOR;
        long totalLbas = alignLba(targetFirst + targetSectors + 34L, 2048L);
        return totalLbas * SECTOR;
    }

    static void create(File prefix, File iso, File output, String expectedPrefixSha,
            String expectedIsoSha) throws Exception {
        create(prefix,iso,output,expectedPrefixSha,expectedIsoSha,TARGET_BYTES);
    }

    static void create(File prefix, File iso, File output, String expectedPrefixSha,
            String expectedIsoSha,long rootBytes) throws Exception {
        expectedDiskBytes(iso.length(),rootBytes);
        if (output.exists()) throw new IllegalStateException("Persistent Ubuntu disk already exists; refusing to replace it.");
        if (prefix.length() != ESP_PREFIX_BYTES || !expectedPrefixSha.equalsIgnoreCase(sha256(prefix)))
            throw new SecurityException("U-AVF platform prefix failed its size or SHA-256 check.");
        if (iso.length() % SECTOR != 0 || !expectedIsoSha.equalsIgnoreCase(sha256(iso)))
            throw new SecurityException("Official Ubuntu ISO failed its size or SHA-256 check.");
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IllegalStateException("Cannot create persistent Ubuntu storage directory.");

        long isoSectors = iso.length() / SECTOR;
        long targetFirst = alignLba(ISO_FIRST_LBA + isoSectors, 2048L);
        long targetSectors = rootBytes / SECTOR;
        long totalLbas = alignLba(targetFirst + targetSectors + 34L, 2048L);
        long totalBytes = totalLbas * SECTOR;
        long isoOffset = ISO_FIRST_LBA * SECTOR;
        long targetOffset = targetFirst * SECTOR;
        long espSectors = (126L * MIB) / SECTOR;

        try (RandomAccessFile disk = new RandomAccessFile(output, "rw");
             FileInputStream prefixIn = new FileInputStream(prefix);
             FileInputStream isoIn = new FileInputStream(iso)) {
            disk.setLength(totalBytes);
            disk.seek(0);
            copy(prefixIn, disk, ESP_PREFIX_BYTES);
            disk.seek(isoOffset);
            copy(isoIn, disk, iso.length());

            byte[] entries = new byte[(int) GPT_ENTRIES_BYTES];
            putPartition(entries, 0, ESP_TYPE, new UUID(0x5541564653500001L, 0x9d4a770100000001L),
                    2048L, 2048L + espSectors - 1L, "U-AVF Platform");
            putPartition(entries, 1, LINUX_TYPE, UUID.randomUUID(), ISO_FIRST_LBA,
                    ISO_FIRST_LBA + isoSectors - 1L, "Ubuntu 24.04.5 ISO");
            putPartition(entries, 2, LINUX_TYPE, UUID.randomUUID(), targetFirst,
                    targetFirst + targetSectors - 1L, "U-AVF Ubuntu Root");

            byte[] protectiveMbr = new byte[(int) SECTOR];
            ByteBuffer mbr = ByteBuffer.wrap(protectiveMbr).order(ByteOrder.LITTLE_ENDIAN);
            mbr.position(446);
            mbr.put((byte) 0).put(new byte[] {0, 2, 0}).put((byte) 0xee).put(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff});
            mbr.putInt(1).putInt((int) Math.min(totalLbas - 1L, 0xffff_ffffL));
            protectiveMbr[510] = 0x55;
            protectiveMbr[511] = (byte) 0xaa;

            long backupEntriesLba = totalLbas - 33L;
            byte[] primaryHeader = makeHeader(1L, totalLbas - 1L, 2L, totalLbas, entries);
            byte[] backupHeader = makeHeader(totalLbas - 1L, 1L, backupEntriesLba, totalLbas, entries);
            disk.seek(0); disk.write(protectiveMbr);
            disk.seek(SECTOR); disk.write(primaryHeader);
            disk.seek(2L * SECTOR); disk.write(entries);
            disk.seek(backupEntriesLba * SECTOR); disk.write(entries);
            disk.seek((totalLbas - 1L) * SECTOR); disk.write(backupHeader);
            disk.getFD().sync();
        } catch (Exception error) {
            output.delete();
            throw error;
        }

        if (output.length() != totalBytes || !audit(output, iso.length(), TARGET_BYTES)) {
            output.delete();
            throw new IllegalStateException("Persistent Ubuntu disk GPT audit failed.");
        }
    }

    /** Replace only the managed FAT ESP payload, preserving GPT, ISO, and root partition bytes. */
    static String updatePlatformEsp(File disk, File prefix, long isoBytes,
            String expectedPrefixSha, String expectedEspSha, File backupDirectory) throws Exception {
        if (!audit(disk, isoBytes, TARGET_BYTES))
            throw new SecurityException("Persistent disk GPT audit failed before platform update.");
        if (prefix.length() != ESP_PREFIX_BYTES
                || !expectedPrefixSha.equalsIgnoreCase(sha256(prefix)))
            throw new SecurityException("U-AVF platform prefix failed its size or SHA-256 check.");
        if (ISO_FIRST_LBA * SECTOR != ESP_PREFIX_BYTES)
            throw new IllegalStateException("ESP update range would overlap the ISO partition.");

        String currentSha = sha256Range(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
        String candidateSha = sha256Range(prefix, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
        if (!expectedEspSha.equalsIgnoreCase(candidateSha))
            throw new SecurityException("U-AVF platform ESP payload failed its SHA-256 check.");
        if (currentSha.equalsIgnoreCase(candidateSha)) return "UNCHANGED SHA256=" + currentSha;

        if (!backupDirectory.isDirectory() && !backupDirectory.mkdirs())
            throw new IllegalStateException("Cannot create the platform ESP backup directory.");
        File backup = new File(backupDirectory, "platform-esp-backup-" + currentSha + ".bin");
        if (!backup.isFile()) copyRangeToFile(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES, backup);
        if (backup.length() != ESP_PARTITION_BYTES
                || !currentSha.equalsIgnoreCase(sha256(backup)))
            throw new SecurityException("Platform ESP rollback backup failed verification.");

        try {
            copyRange(prefix, ESP_PARTITION_OFFSET, disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
            String readbackSha = sha256Range(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
            if (!candidateSha.equalsIgnoreCase(readbackSha))
                throw new IllegalStateException("Platform ESP readback SHA-256 mismatch.");
            if (!audit(disk, isoBytes, TARGET_BYTES))
                throw new IllegalStateException("Persistent disk GPT audit failed after platform update.");
            return "UPDATED OLD_SHA256=" + currentSha + " NEW_SHA256=" + readbackSha
                    + " BACKUP=" + backup.getAbsolutePath();
        } catch (Exception failure) {
            try {
                copyRange(backup, 0L, disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
                String restored = sha256Range(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
                if (!currentSha.equalsIgnoreCase(restored))
                    failure.addSuppressed(new IllegalStateException("ESP rollback SHA-256 mismatch."));
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    static boolean audit(File disk, long isoBytes, long targetBytes) throws Exception {
        if (!disk.isFile() || disk.length() < expectedDiskBytes(isoBytes) || disk.length() % SECTOR != 0) return false;
        long totalLbas = disk.length() / SECTOR;
        byte[] header = new byte[(int) SECTOR];
        byte[] backup = new byte[(int) SECTOR];
        byte[] entries = new byte[(int) GPT_ENTRIES_BYTES];
        byte[] backupEntries = new byte[(int) GPT_ENTRIES_BYTES];
        try (RandomAccessFile file = new RandomAccessFile(disk, "r")) {
            file.seek(SECTOR); file.readFully(header);
            file.seek(2L * SECTOR); file.readFully(entries);
            file.seek((totalLbas - 1L) * SECTOR); file.readFully(backup);
            file.seek((totalLbas - 33L) * SECTOR); file.readFully(backupEntries);
        }
        if (!java.util.Arrays.equals(entries,backupEntries)) return false;
        if (!validHeader(header, 1L, totalLbas - 1L, 2L, entries)
                || !validHeader(backup, totalLbas - 1L, 1L, totalLbas - 33L, entries)) return false;
        long isoSectors = isoBytes / SECTOR;
        long targetFirst = alignLba(ISO_FIRST_LBA + isoSectors, 2048L);
        ByteBuffer table = ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN);
        long espStart = table.getLong(32);
        long espEnd = table.getLong(40);
        long isoStart = table.getLong(GPT_ENTRY_BYTES + 32);
        long isoEnd = table.getLong(GPT_ENTRY_BYTES + 40);
        long targetStart = table.getLong(2 * GPT_ENTRY_BYTES + 32);
        long targetEnd = table.getLong(2 * GPT_ENTRY_BYTES + 40);
        return espStart == 2048L && espEnd - espStart + 1L == ESP_PARTITION_BYTES / SECTOR
                && isoStart == ISO_FIRST_LBA && isoEnd - isoStart + 1L == isoSectors
                && targetStart == targetFirst && targetEnd - targetStart + 1L >= targetBytes / SECTOR
                && targetEnd <= totalLbas - 34L && totalLbas - 34L - targetEnd < 2048L
                && managedEntries(entries);
    }

    private static boolean managedEntries(byte[] entries) {
        byte[] expected=new byte[16];ByteBuffer guid=ByteBuffer.wrap(expected);putGuid(guid,UUID.fromString(LINUX_TYPE));
        if(!java.util.Arrays.equals(expected,java.util.Arrays.copyOfRange(entries,256,272)))return false;
        putGuid(ByteBuffer.wrap(expected),UUID.fromString(ESP_TYPE));
        if(!java.util.Arrays.equals(expected,java.util.Arrays.copyOfRange(entries,0,16)))return false;
        for(int i=3*GPT_ENTRY_BYTES;i<entries.length;i++)if(entries[i]!=0)return false;
        String name=new String(entries,2*GPT_ENTRY_BYTES+56,72,StandardCharsets.UTF_16LE).replace("\0","");
        return "U-AVF Ubuntu Root".equals(name);
    }

    static long rootBytes(File disk,long isoBytes) throws Exception {
        if(!audit(disk,isoBytes,TARGET_BYTES))throw new SecurityException("Disk layout is not a supported U-AVF layout");
        try(RandomAccessFile file=new RandomAccessFile(disk,"r")) {
            file.seek(2*SECTOR+2*GPT_ENTRY_BYTES+32);byte[] p=new byte[16];file.readFully(p);
            ByteBuffer b=ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);return (b.getLong(8)-b.getLong(0)+1)*SECTOR;
        }
    }

    /** Stopped-VM operation only. Durable intent allows completing interrupted metadata writes. */
    static void grow(File disk,long isoBytes,long newRootBytes,File journal) throws Exception {
        if(journal.exists())throw new IllegalStateException("A storage transaction already requires recovery");
        long oldRoot=rootBytes(disk,isoBytes);
        if(newRootBytes<=oldRoot || newRootBytes%MIB!=0 || newRootBytes>1024L*1024*MIB)
            throw new IllegalArgumentException("Choose a larger root size, up to 1 TiB");
        byte[] h=new byte[512],entries=new byte[(int)GPT_ENTRIES_BYTES],mbr=new byte[512];
        try(RandomAccessFile file=new RandomAccessFile(disk,"r")) {
            file.readFully(mbr);file.readFully(h);file.readFully(entries);
        }
        long first=ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN).getLong(256+32);
        long total=alignLba(first+newRootBytes/SECTOR+34,2048);
        ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN).putLong(256+40,first+newRootBytes/SECTOR-1);
        byte[] plan=new byte[8+512+512+entries.length+4];ByteBuffer p=ByteBuffer.wrap(plan).order(ByteOrder.LITTLE_ENDIAN);
        p.putLong(total*SECTOR).put(mbr).put(h).put(entries);
        p.putInt(crc(plan,plan.length-4));
        try(FileOutputStream out=new FileOutputStream(journal)) {out.write(plan);out.getFD().sync();}
        completeGrow(disk,isoBytes,journal);
    }

    static void completeGrow(File disk,long isoBytes,File journal) throws Exception {
        byte[] plan=new byte[8+512+512+(int)GPT_ENTRIES_BYTES+4];
        try(RandomAccessFile in=new RandomAccessFile(journal,"r")) {
            if(in.length()!=plan.length)throw new SecurityException("Invalid storage recovery journal");in.readFully(plan);
        }
        ByteBuffer p=ByteBuffer.wrap(plan).order(ByteOrder.LITTLE_ENDIAN);long bytes=p.getLong();
        if(p.getInt(plan.length-4)!=crc(plan,plan.length-4))throw new SecurityException("Storage recovery checksum mismatch");
        byte[] mbr=new byte[512],oldHeader=new byte[512],entries=new byte[(int)GPT_ENTRIES_BYTES];p.get(mbr).get(oldHeader).get(entries);
        long total=bytes/SECTOR;
        if(bytes<disk.length() || bytes%MIB!=0 || !managedEntries(entries) || bytes>1024L*1024*MIB+8L*1024*MIB)
            throw new SecurityException("Unsafe storage recovery plan");
        byte[] primary=makeHeader(1,total-1,2,total,entries),backup=makeHeader(total-1,1,total-33,total,entries);
        // Disk and partition identities must survive growth (GRUB/fstab/PARTUUID).
        for(byte[] header:new byte[][]{primary,backup}) {
            System.arraycopy(oldHeader,56,header,56,16);ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(16,0);
            ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(16,crc(header,92));
        }
        ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN).putInt(458,(int)Math.min(total-1,0xffff_ffffL));
        try(RandomAccessFile out=new RandomAccessFile(disk,"rw")) {
            out.setLength(bytes);
            out.seek((total-33)*SECTOR);out.write(entries);out.write(backup);out.getFD().sync();
            out.seek(2*SECTOR);out.write(entries);out.seek(SECTOR);out.write(primary);out.seek(0);out.write(mbr);out.getFD().sync();
        }
        if(!audit(disk,isoBytes,TARGET_BYTES))throw new SecurityException("Expanded GPT readback failed; recovery journal retained");
        if(!journal.delete())throw new IOException("Growth complete, journal cleanup failed");
    }

    /**
     * Detect the installer's U-AVF boot handoff marker without mounting or
     * modifying the ESP. The marker payload is written only after GRUB and
     * the installed runtime have been staged into the dedicated root target.
     */
    static boolean hasInstalledBootCandidate(File disk) throws Exception {
        if (!disk.isFile() || disk.length() < ESP_PARTITION_OFFSET + ESP_PARTITION_BYTES) return false;
        return containsInstalledMarker(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
    }

    private static boolean containsInstalledMarker(File disk, long offset, long bytes) throws Exception {
        byte[] needle = "version=1\nroot_partition=U-AVF Ubuntu Root\n"
                .getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[1024 * 1024];
        byte[] carry = new byte[needle.length - 1];
        int carryLength = 0;
        long remaining = bytes;
        try (RandomAccessFile file = new RandomAccessFile(disk, "r")) {
            file.seek(offset);
            while (remaining > 0) {
                int count = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count <= 0) return false;
                byte[] window = new byte[carryLength + count];
                System.arraycopy(carry, 0, window, 0, carryLength);
                System.arraycopy(buffer, 0, window, carryLength, count);
                if (indexOf(window, needle) >= 0) return true;
                carryLength = Math.min(carry.length, window.length);
                System.arraycopy(window, window.length - carryLength, carry, 0, carryLength);
                remaining -= count;
            }
        }
        return false;
    }

    /** Recover only a verified prior installed ESP over the exact shipped Live ESP.
     * Never touches GPT, ISO or root; refuses ambiguous or unverified backups. */
    static String recoverInstalledEsp(File disk, long isoBytes, String liveEspSha,
            File backupDirectory) throws Exception {
        if (!audit(disk, isoBytes, TARGET_BYTES)) throw new SecurityException("Recovery GPT audit failed");
        if (hasInstalledBootCandidate(disk)) return "NOT_NEEDED";
        if (!liveEspSha.equalsIgnoreCase(sha256Range(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES)))
            return "CURRENT_ESP_NOT_MANAGED_LIVE";
        File[] backups = backupDirectory.listFiles();
        if (backups == null) return "NO_BACKUP";
        File selected = null;
        for (File backup : backups) {
            String name = backup.getName();
            if (!name.matches("platform-esp-backup-[0-9a-fA-F]{64}\\.bin")
                    || backup.length() != ESP_PARTITION_BYTES) continue;
            String expected = name.substring(20, 84);
            if (!expected.equalsIgnoreCase(sha256(backup))
                    || !containsInstalledMarker(backup, 0, ESP_PARTITION_BYTES)) continue;
            if (selected != null) return "AMBIGUOUS_BACKUPS";
            selected = backup;
        }
        if (selected == null) return "NO_INSTALLED_BACKUP";
        File rollback = new File(backupDirectory, "platform-esp-backup-" + liveEspSha + ".bin");
        if (!rollback.exists()) copyRangeToFile(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES, rollback);
        if (rollback.length() != ESP_PARTITION_BYTES || !liveEspSha.equalsIgnoreCase(sha256(rollback)))
            throw new SecurityException("Recovery rollback verification failed");
        try {
            copyRange(selected, 0, disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
            if (!sha256(selected).equalsIgnoreCase(sha256Range(disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES))
                    || !audit(disk, isoBytes, TARGET_BYTES) || !hasInstalledBootCandidate(disk))
                throw new SecurityException("Installed ESP recovery readback failed");
            return "RESTORED SHA256=" + sha256(selected);
        } catch (Exception failure) {
            copyRange(rollback, 0, disk, ESP_PARTITION_OFFSET, ESP_PARTITION_BYTES);
            throw failure;
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; ++i) {
            for (int j = 0; j < needle.length; ++j) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static byte[] makeHeader(long current, long backup, long entriesLba,
            long totalLbas, byte[] entries) {
        byte[] data = new byte[(int) SECTOR];
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("EFI PART".getBytes(StandardCharsets.US_ASCII));
        b.putInt(0x00010000).putInt(92).putInt(0).putInt(0);
        b.putLong(current).putLong(backup).putLong(34L).putLong(totalLbas - 34L);
        putGuid(b, UUID.nameUUIDFromBytes(("U-AVF install disk " + totalLbas).getBytes(StandardCharsets.US_ASCII)));
        b.putLong(entriesLba).putInt(GPT_ENTRY_COUNT).putInt(GPT_ENTRY_BYTES).putInt(crc(entries));
        b.putInt(16, crc(data, 92));
        return data;
    }

    private static boolean validHeader(byte[] data, long current, long backup, long entriesLba, byte[] entries) {
        if (!new String(data, 0, 8, StandardCharsets.US_ASCII).equals("EFI PART")) return false;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int expected = b.getInt(16);
        byte[] copy = data.clone();
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(16, 0);
        return expected == crc(copy, 92) && b.getLong(24) == current && b.getLong(32) == backup
                && b.getLong(72) == entriesLba && b.getInt(80) == GPT_ENTRY_COUNT
                && b.getInt(84) == GPT_ENTRY_BYTES && b.getInt(88) == crc(entries);
    }

    private static void putPartition(byte[] entries, int slot, String type, UUID id,
            long first, long last, String name) {
        int offset = slot * GPT_ENTRY_BYTES;
        ByteBuffer b = ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN);
        b.position(offset); putGuid(b, UUID.fromString(type)); putGuid(b, id);
        b.putLong(first).putLong(last).putLong(0L);
        byte[] encoded = name.getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(encoded, 0, entries, offset + 56, Math.min(encoded.length, 72));
    }

    private static void putGuid(ByteBuffer b, UUID value) {
        b.order(ByteOrder.BIG_ENDIAN).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        int start = b.position() - 16;
        byte[] array = b.array();
        reverse(array, start, 4); reverse(array, start + 4, 2); reverse(array, start + 6, 2);
        b.order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void reverse(byte[] bytes, int start, int length) {
        for (int a = start, z = start + length - 1; a < z; a++, z--) {
            byte temp = bytes[a]; bytes[a] = bytes[z]; bytes[z] = temp;
        }
    }

    private static void copy(FileInputStream in, RandomAccessFile out, long bytes) throws Exception {
        byte[] buffer = new byte[1024 * 1024];
        long remaining = bytes;
        while (remaining > 0) {
            int count = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count <= 0) throw new IllegalStateException("Unexpected end of installation media.");
            out.write(buffer, 0, count);
            remaining -= count;
        }
    }

    private static long alignLba(long value, long alignment) {
        return ((value + alignment - 1L) / alignment) * alignment;
    }

    private static int crc(byte[] data) { return crc(data, data.length); }
    private static int crc(byte[] data, int count) {
        CRC32 crc = new CRC32(); crc.update(data, 0, count); return (int) crc.getValue();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int count; (count = in.read(buffer)) >= 0;) if (count > 0) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02X", value & 0xff));
        return result.toString();
    }

    private static String sha256Range(File file, long offset, long length) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (offset < 0 || length < 0 || offset + length > input.length())
                throw new IllegalArgumentException("File range is outside the source file.");
            input.seek(offset);
            byte[] buffer = new byte[1024 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count <= 0) throw new IllegalStateException("Unexpected EOF while hashing file range.");
                digest.update(buffer, 0, count);
                remaining -= count;
            }
        }
        return hex(digest.digest());
    }

    private static void copyRangeToFile(File source, long offset, long length, File destination) throws Exception {
        try (FileOutputStream output = new FileOutputStream(destination);
             RandomAccessFile input = new RandomAccessFile(source, "r")) {
            copyRange(input, offset, output, length);
            output.getFD().sync();
        }
    }

    private static void copyRange(File source, long sourceOffset, RandomAccessFile destination,
            long destinationOffset, long length) throws Exception {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            input.seek(sourceOffset);
            destination.seek(destinationOffset);
            byte[] buffer = new byte[1024 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count <= 0) throw new IllegalStateException("Unexpected EOF while copying file range.");
                destination.write(buffer, 0, count);
                remaining -= count;
            }
            destination.getFD().sync();
        }
    }

    private static void copyRange(File source, long sourceOffset, File destination,
            long destinationOffset, long length) throws Exception {
        try (RandomAccessFile output = new RandomAccessFile(destination, "rw")) {
            copyRange(source, sourceOffset, output, destinationOffset, length);
        }
    }

    private static void copyRange(RandomAccessFile source, long sourceOffset, FileOutputStream destination,
            long length) throws Exception {
        source.seek(sourceOffset);
        byte[] buffer = new byte[1024 * 1024];
        long remaining = length;
        while (remaining > 0) {
            int count = source.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count <= 0) throw new IllegalStateException("Unexpected EOF while backing up file range.");
            destination.write(buffer, 0, count);
            remaining -= count;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02X", value & 0xff));
        return result.toString();
    }
}
