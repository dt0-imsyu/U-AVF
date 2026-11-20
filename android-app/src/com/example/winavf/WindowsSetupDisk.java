// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/** Builds a new Windows Setup disk in private storage from the verified WinPE profile. */
final class WindowsSetupDisk {
    private static final long GIB = 1024L * 1024 * 1024;
    static final long MEDIA_BYTES = WindowsSetupLayout.MEDIA_BYTES;
    static final long TARGET_BYTES = WindowsSetupLayout.TARGET_BYTES;
    static final String INSTALL_BUILD = "26200.6584";
    static final String REFERENCE_TEMPLATE = "windows-display-live-name-fix-20261008.img";
    static final long RESERVE_BYTES = 5 * GIB;
    private static final long PREPARATION_HEADROOM = 64L * 1024 * 1024;
    // The retained installation consumed 9.77 GiB beyond its WinPE disk.
    private static final long INSTALL_HEADROOM = 10 * GIB;
    private static final int SECTOR = WindowsSetupLayout.SECTOR;
    private static final int ESP_OFFSET = 1024 * 1024;
    private static final int TABLE_BYTES = WindowsSetupLayout.TABLE_BYTES;
    private static final String TEMPLATE_SHA = "A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A";
    // The retained 25H2 ARM64 ru-RU payload report pins the source of both SWMs in the template.
    private static final long INSTALL_WIM_BYTES = 6_347_029_986L;
    private static final String INSTALL_WIM_SHA = "293DFA0750995884136952EC54BBB292E037CF2FB77E284C54DC8885E2537131";
    private static final String BOOT_EFI_SHA = "893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B";
    private static final String NATIVE_DISPLAY_EFI_SHA = "688299BB5CA7DD8DE60D69EA03D3049D946CB57BE6CAA6F0BE3CFEA11E6B4957";
    private static final String SMP_DISPLAY_EFI_SHA = "359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A";
    private static final String BOOT_WIM_SHA = "A90BA45CC1404CF18FE2F10CE195E565B8F70E45416583F2F25F3EF4391BEF15";
    private static final String[] INSTALL_TOOLS = {
            "INSTALL.CMD", "BASE.CMD", "BOOTPREP.CMD", "FIRSTBOT.CMD", "DISPLAY.CMD"
    };
    private static final int MAX_TOOL_BYTES = 16384;
    private static final int MAX_BROKER_BYTES = 71168;
    private record MediaProfile(String templateSha, String bootWimSha) {}

    private static MediaProfile guidedProfile(Context context) throws IOException {
        try (var input = context.getAssets().open("windows-setup/guided-profile.txt")) {
            String profile = new String(input.readNBytes(132), java.nio.charset.StandardCharsets.US_ASCII);
            if (!profile.matches("[0-9A-F]{64}\n[0-9A-F]{64}\n") || input.read() != -1
                    || profile.substring(65, 129).equals(BOOT_WIM_SHA))
                throw new IOException("Invalid packaged guided WinPE profile");
            return new MediaProfile(profile.substring(0, 64), profile.substring(65, 129));
        } catch (java.io.FileNotFoundException absent) {
            return null;
        }
    }

    private static final boolean AVAILABLE = load();

    private WindowsSetupDisk() {}

    enum Phase { CHECKING_ISO, CHECKING, COPYING, FINALIZING }

    static boolean available() { return AVAILABLE; }

    private static boolean load() {
        try {
            System.loadLibrary("uavf_windows_setup_fat");
            return true;
        } catch (UnsatisfiedLinkError absent) {
            return false;
        }
    }

    static long requiredFreeBytes() { return RESERVE_BYTES + MEDIA_BYTES + PREPARATION_HEADROOM; }
    static long requiredInstallFreeBytes() { return RESERVE_BYTES + INSTALL_HEADROOM; }

    static void requireMatchingPayload(WindowsInstallIso.Inspection iso) throws IOException {
        if (iso.esd() || iso.installBytes() != INSTALL_WIM_BYTES) {
            throw new IOException(UiLanguage.text("Для шаблона WinPE нужен install.wim из Windows 11 ARM64 25H2 (ru-RU)."));
        }
    }

    static WindowsInstallIso.Inspection verifyInstallIso(ParcelFileDescriptor file) throws IOException {
        var inspection = WindowsInstallIso.inspect(file);
        requireMatchingPayload(inspection);
        if (!INSTALL_WIM_SHA.equals(WindowsInstallIso.installDigest(file, INSTALL_WIM_BYTES)))
            throw new IOException(UiLanguage.text("Контрольная сумма install.wim не совпадает с профилем WinPE."));
        return inspection;
    }

    static Path workspace(Context context) {
        return context.getFilesDir().toPath().resolve("windows-25h2-setup.raw");
    }

    static synchronized void prepare(Context context, ParcelFileDescriptor iso, ParcelFileDescriptor template,
                        Consumer<Phase> phase)
            throws IOException {
        if (!available()) throw new IOException("The Windows Setup disk builder is unavailable.");
        phase.accept(Phase.CHECKING);
        Path root = context.getFilesDir().toPath().toRealPath();
        Path workspace = root.resolve(workspace(context).getFileName());
        if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("A new private Windows workspace path is required.");
        if (template.getStatSize() != MEDIA_BYTES)
            throw new IOException("The verified WinPE template is missing.");
        MediaProfile guided = guidedProfile(context);
        // The pinned template contains the complete, verified SWMs from this
        // verified installation image. Validate the ISO payload on the open descriptor.
        Path staging = root.resolve(".windows-25h2-setup.pending");
        Path partsDirectory = staging.resolve("parts");
        Path candidate = staging.resolve("workspace.raw");
        verifyInstallIso(iso);
        if (Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS))
            cleanStaging(staging, candidate, partsDirectory);
        if (root.toFile().getUsableSpace() < requiredFreeBytes())
            throw new IOException("Not enough space to prepare Windows while preserving the 5 GiB device reserve.");

        Files.createDirectory(staging);
        try {
            phase.accept(Phase.COPYING);
            String templateSha = copyTemplate(template, candidate);
            if (!TEMPLATE_SHA.equals(templateSha) && (guided == null || !guided.templateSha.equals(templateSha)))
                throw new IOException("WinPE template digest differs.");
            phase.accept(Phase.FINALIZING);
            if (guided != null && TEMPLATE_SHA.equals(templateSha)) {
                applyGuidedDelta(context, candidate);
                templateSha = guided.templateSha;
            }
            try (var readback = Files.newInputStream(candidate, LinkOption.NOFOLLOW_LINKS)) {
                if (!templateSha.equals(sha(readback))) throw new IOException("WinPE staging readback differs.");
            }
            if (installToolsAvailable(context)) verifyInstallTools(context, candidate, true);
            expandGpt(candidate);
            verifyFresh(context, candidate);
            if (root.toFile().getUsableSpace() < RESERVE_BYTES)
                throw new IOException("The storage reserve changed during Windows preparation.");
            publish(candidate, workspace);
        } catch (IOException | RuntimeException error) {
            try { cleanStaging(staging, candidate, partsDirectory); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
        try { cleanStaging(staging, candidate, partsDirectory); }
        catch (IOException cleanup) { Log.w("UAVF-Windows-Setup", "Could not clear staging", cleanup); }
    }

    private static void applyGuidedDelta(Context context, Path candidate) throws IOException {
        final int blockBytes = 1024 * 1024;
        try (var delta = context.getAssets().open("windows-setup/guided.delta");
             var disk = new RandomAccessFile(candidate.toFile(), "rw")) {
            var header = ByteBuffer.wrap(delta.readNBytes(20)).order(ByteOrder.LITTLE_ENDIAN);
            if (header.remaining() != 20 || header.getLong() != 0x3130303050445755L
                    || header.getLong() != MEDIA_BYTES)
                throw new IOException("Invalid guided WinPE delta header");
            int count = header.getInt();
            if (count < 1 || count > 31) throw new IOException("Invalid guided WinPE delta size");
            long end = 0;
            byte[] old = new byte[blockBytes];
            for (int i = 0; i < count; i++) {
                var record = ByteBuffer.wrap(delta.readNBytes(20)).order(ByteOrder.LITTLE_ENDIAN);
                if (record.remaining() != 20) throw new IOException("Truncated guided WinPE delta");
                long offset = record.getLong();
                int size = record.getInt();
                int before = record.getInt(), after = record.getInt();
                if (size != blockBytes || offset < end || offset % blockBytes != 0
                        || offset > MEDIA_BYTES - size)
                    throw new IOException("Invalid guided WinPE delta range");
                byte[] bytes = delta.readNBytes(size);
                disk.seek(offset);
                disk.readFully(old);
                if (bytes.length != size || blockCrc(old) != before || blockCrc(bytes) != after)
                    throw new IOException("Guided WinPE delta block failed verification");
                disk.seek(offset);
                disk.write(bytes);
                end = offset + size;
            }
            if (delta.read() != -1) throw new IOException("Unexpected guided WinPE delta data");
            disk.getFD().sync();
        }
    }

    private static int blockCrc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }

    /** Checks an unpartitioned Setup target before it is admitted to first boot. */
    static boolean verifyFresh(Context context, Path disk) throws IOException {
        if (!available()) throw new IOException("The Windows Setup disk reader is unavailable.");
        verifyFreshGpt(disk);
        return verifyBootFiles(context, disk).guided();
    }

    record VerifiedBoot(boolean guided, boolean matchHostCpus) {}

    static VerifiedBoot verifyBootFiles(Context context, Path disk) throws IOException {
        MediaProfile guided = guidedProfile(context);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(disk.toFile(),
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            String[] hashes = auditBootFiles(descriptor, ESP_OFFSET, MEDIA_BYTES - ESP_OFFSET - 33 * SECTOR);
            boolean guidedBoot = guided != null && guided.bootWimSha.equals(hashes[1]);
            boolean smp = SMP_DISPLAY_EFI_SHA.equals(hashes[0]);
            if ((!smp && !BOOT_EFI_SHA.equals(hashes[0]) && !NATIVE_DISPLAY_EFI_SHA.equals(hashes[0]))
                    || (!BOOT_WIM_SHA.equals(hashes[1]) && !guidedBoot))
                throw new IOException("The verified Windows Setup boot files have changed.");
            return new VerifiedBoot(guidedBoot, smp);
        }
    }

    static boolean hasGuidedProfile(Context context) throws IOException { return guidedProfile(context) != null; }

    static void setInstallApproval(Path disk, boolean approve) throws IOException {
        try (var descriptor = ParcelFileDescriptor.open(disk.toFile(), ParcelFileDescriptor.MODE_READ_WRITE)) {
            int result = setApproval(descriptor.getFd(), ESP_OFFSET,
                    MEDIA_BYTES - ESP_OFFSET - 33 * SECTOR, approve);
            if (result != 0) throw new IOException("Windows install approval could not be updated (" + result + ")");
        }
    }

    static void verifyInstalledBcd(Path disk, WindowsSetupLayout.Inspection layout) throws IOException {
        if (layout.state() != WindowsSetupLayout.State.NTFS_TARGET)
            throw new IOException("No NTFS Windows target was found");
        byte[] bcd = new byte[1024 * 1024];
        try (var descriptor = ParcelFileDescriptor.open(disk.toFile(), ParcelFileDescriptor.MODE_READ_ONLY)) {
            int size = readInstalledBcd(descriptor.getFd(), ESP_OFFSET,
                    MEDIA_BYTES - ESP_OFFSET - 33 * SECTOR, bcd);
            if (size <= 0 || size > bcd.length) throw new IOException("Windows boot preparation record or BCD is unavailable (" + size + ")");
            WindowsBcdAudit.verify(Arrays.copyOf(bcd, size), layout.diskGuid(), layout.targetGuid());
        }
    }

    static String[] auditBootFiles(ParcelFileDescriptor disk, long offset, long length) throws IOException {
        MessageDigest efi = digest(), wim = digest();
        int result = auditFat(disk.getFd(), offset, length, efi, wim);
        if (result != 0) throw new IOException("Windows Setup FAT audit failed (" + result + ").");
        HexFormat hex = HexFormat.of().withUpperCase();
        return new String[]{hex.formatHex(efi.digest()), hex.formatHex(wim.digest())};
    }

    static boolean installToolsAvailable(Context context) {
        try {
            String[] names = context.getAssets().list("windows-setup");
            return names != null && Arrays.asList(names).containsAll(Arrays.asList(INSTALL_TOOLS));
        } catch (IOException absent) {
            return false;
        }
    }

    static void verifyInstallTools(Context context, Path disk, boolean stage) throws IOException {
        byte[][] tools = new byte[INSTALL_TOOLS.length + 2][];
        for (int i = 0; i < INSTALL_TOOLS.length; i++) {
            try (var input = context.getAssets().open("windows-setup/" + INSTALL_TOOLS[i])) {
                tools[i] = input.readNBytes(MAX_TOOL_BYTES + 1);
            }
            if (tools[i].length == 0 || tools[i].length > MAX_TOOL_BYTES) {
                throw new IOException("Invalid Windows installation command asset");
            }
        }
        // Optional for older media. New media can stage the desktop-aware
        // Windows service alongside the existing Setup scripts.
        try (var input = context.getAssets().open("windows-setup/BROKER.EXE")) {
            tools[INSTALL_TOOLS.length] = input.readNBytes(MAX_BROKER_BYTES + 1);
            if (tools[INSTALL_TOOLS.length].length == 0 || tools[INSTALL_TOOLS.length].length > MAX_BROKER_BYTES)
                throw new IOException("Invalid Windows display broker asset");
        } catch (java.io.FileNotFoundException absent) {
            tools = Arrays.copyOf(tools, INSTALL_TOOLS.length);
        }
        if (tools.length > INSTALL_TOOLS.length) {
            try (var input = context.getAssets().open("windows-setup/OFFLINE.XML")) {
                byte[] answer = input.readNBytes(MAX_TOOL_BYTES + 1);
                if (answer.length == 0 || answer.length > MAX_TOOL_BYTES)
                    throw new IOException("Invalid Windows offline OOBE answer file");
                tools[INSTALL_TOOLS.length + 1] = answer;
            } catch (java.io.FileNotFoundException absent) {
                tools = Arrays.copyOf(tools, INSTALL_TOOLS.length + 1);
            }
        }
        try (var descriptor = ParcelFileDescriptor.open(disk.toFile(), stage
                ? ParcelFileDescriptor.MODE_READ_WRITE : ParcelFileDescriptor.MODE_READ_ONLY)) {
            int result = checkInstallTools(descriptor.getFd(), ESP_OFFSET,
                    MEDIA_BYTES - ESP_OFFSET - 33 * SECTOR, tools, stage);
            if (result != 0) throw new IOException("Windows installation command audit failed (" + result + ").");
        }
    }

    static void verifyFreshGpt(Path disk) throws IOException {
        if (WindowsSetupLayout.read(disk) != WindowsSetupLayout.State.FRESH) {
            throw new IOException("The Windows target has already been partitioned");
        }
    }

    static void publish(Path candidate, Path workspace) throws IOException {
        int result = renameNew(candidate.toString(), workspace.toString());
        if (result != 0) throw new IOException("Could not publish a new Windows disk (errno " + result + ").");
    }

    private static void cleanStaging(Path staging, Path candidate, Path partsDirectory) throws IOException {
        Files.deleteIfExists(candidate);
        if (Files.isDirectory(partsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try (var contents = Files.newDirectoryStream(partsDirectory)) {
                for (Path part : contents) {
                    if (!part.getFileName().toString().matches("install(?:[2-9]|[1-9][0-9]+)?\\.swm"))
                        throw new IOException("Unexpected file in Windows Setup staging.");
                    Files.deleteIfExists(part);
                }
            }
            Files.delete(partsDirectory);
        }
        Files.delete(staging);
    }

    static void putParts(ParcelFileDescriptor destination, long offset, long length, List<Path> parts)
            throws IOException {
        if (parts.size() < 2 || parts.size() > 9)
            throw new IOException("Expected between two and nine split WIM parts.");
        List<ParcelFileDescriptor> inputs = new ArrayList<>();
        try {
            for (int index = 0; index < parts.size(); index++) {
                String expected = index == 0 ? "install.swm" : "install" + (index + 1) + ".swm";
                if (!parts.get(index).getFileName().toString().equals(expected))
                    throw new IOException("Unexpected SWM sequence.");
                inputs.add(ParcelFileDescriptor.open(parts.get(index).toFile(), ParcelFileDescriptor.MODE_READ_ONLY));
            }
            int[] descriptors = inputs.stream().mapToInt(ParcelFileDescriptor::getFd).toArray();
            int result = writeParts(destination.getFd(), offset, length, descriptors);
            if (result != 0) throw new IOException("Windows Setup FAT write/readback failed (" + result + ").");
        } finally {
            for (ParcelFileDescriptor input : inputs) {
                try { input.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static String copyTemplate(ParcelFileDescriptor template, Path candidate) throws IOException {
        byte[] buffer = new byte[1024 * 1024], zeros = new byte[buffer.length];
        MessageDigest digest = digest();
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(
                     ParcelFileDescriptor.dup(template.getFileDescriptor()));
             RandomAccessFile disk = new RandomAccessFile(Files.createFile(candidate).toFile(), "rw")) {
            input.getChannel().position(0);
            for (long offset = 0; offset < MEDIA_BYTES; offset += buffer.length) {
                if (input.readNBytes(buffer, 0, buffer.length) != buffer.length)
                    throw new IOException("WinPE template ended early.");
                digest.update(buffer);
                if (Arrays.equals(buffer, zeros)) disk.seek(offset + buffer.length);
                else disk.write(buffer);
            }
            if (input.read() != -1) throw new IOException("Unexpected bytes after the WinPE template.");
            disk.setLength(MEDIA_BYTES);
            disk.getFD().sync();
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static void expandGpt(Path candidate) throws IOException {
        byte[] mbr = new byte[SECTOR], primary = new byte[SECTOR], table = new byte[TABLE_BYTES];
        try (RandomAccessFile disk = new RandomAccessFile(candidate.toFile(), "rw")) {
            disk.readFully(mbr);
            disk.readFully(primary);
            disk.readFully(table);
            long last = TARGET_BYTES / SECTOR - 1;
            ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN).putInt(458, (int)last);
            byte[] first = growHeader(primary, 1, last, 2, last - 33);
            byte[] backup = growHeader(primary, last, 1, last - 32, last - 33);
            disk.seek(0);
            disk.write(mbr);
            disk.write(first);
            long tail = (last - 32) * SECTOR;
            disk.seek(tail);
            disk.write(table);
            disk.write(backup);
            disk.getFD().sync();
            verify(disk, 0, mbr);
            verify(disk, SECTOR, first);
            verify(disk, tail, table);
            verify(disk, last * SECTOR, backup);
        }
    }

    private static byte[] growHeader(byte[] original, long current, long alternate, long table, long lastUsable) {
        byte[] header = original.clone();
        ByteBuffer fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        fields.putLong(24, current).putLong(32, alternate).putLong(48, lastUsable).putLong(72, table);
        fields.putInt(16, 0);
        CRC32 crc = new CRC32();
        crc.update(header, 0, fields.getInt(12));
        fields.putInt(16, (int)crc.getValue());
        return header;
    }

    private static void verify(RandomAccessFile disk, long offset, byte[] expected) throws IOException {
        byte[] actual = new byte[expected.length];
        disk.seek(offset);
        disk.readFully(actual);
        if (!Arrays.equals(actual, expected)) throw new IOException("Windows GPT readback differs.");
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }

    private static String sha(java.io.InputStream input) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[1024 * 1024];
        for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static native int writeParts(int output, long offset, long length, int[] parts);
    private static native int auditFat(int input, long offset, long length, MessageDigest efi, MessageDigest wim);
    private static native int checkInstallTools(int fd, long offset, long length, byte[][] tools, boolean write);
    private static native int readInstalledBcd(int fd, long offset, long length, byte[] output);
    private static native int setApproval(int fd, long offset, long length, boolean approve);
    private static native int renameNew(String from, String to);
}
