// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.ui.UiLanguage;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

/** Builds a new Windows Setup disk in private storage from the selected ISO. */
final class WindowsSetupDisk {
    private static final long GIB = 1024L * 1024 * 1024;
    static final long MEDIA_BYTES = WindowsSetupLayout.MEDIA_BYTES;
    static final long TARGET_BYTES = WindowsSetupLayout.TARGET_BYTES;
    static final long RESERVE_BYTES = 5 * GIB;
    private static final long PREPARATION_HEADROOM = 64L * 1024 * 1024;
    // The retained installation consumed 9.77 GiB beyond its WinPE disk.
    private static final long INSTALL_HEADROOM = 10 * GIB;
    private static final int SECTOR = WindowsSetupLayout.SECTOR;
    private static final int ESP_OFFSET = 1024 * 1024;
    private static final long SOURCE_PARTITION_BYTES = MEDIA_BYTES - ESP_OFFSET - 33 * SECTOR;
    private static final long SPLIT_WIM_PART_BYTES = 3500L * 1024 * 1024;
    private static final long BOOT_STAGING_BYTES = 2 * GIB;
    /*
     * Space reservation for the extracted installation image. This is not a
     * compatibility gate: it only decides whether the device can hold the
     * split parts. It is sized for the largest retail install.wim observed so
     * far (26H2 en-US, 7.09 GiB) with room for a future release to grow.
     */
    private static final long INSTALL_WIM_RESERVE_BYTES = 8 * GIB;
    private static final String GENERATED_BOOT_HASH = "iso_boot_wim_sha256";
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

    static long requiredFreeBytes() {
        return RESERVE_BYTES + MEDIA_BYTES + INSTALL_WIM_RESERVE_BYTES + BOOT_STAGING_BYTES + PREPARATION_HEADROOM;
    }
    static long requiredInstallFreeBytes() { return RESERVE_BYTES + INSTALL_HEADROOM; }

    /**
     * Rejects media that cannot produce a working installation.
     *
     * An ESD cannot be read by the splitter, so it is refused. The WIM size is
     * whatever the selected ISO contains: pinning one number would reject every
     * other release and language while adding no safety, because the split and
     * the install itself both read their length from this same measurement.
     */
    static void requireMatchingPayload(WindowsInstallIso.Inspection iso) throws IOException {
        if (iso.esd() || iso.installBytes() <= 0) {
            throw new IOException(UiLanguage.text(
                    "Нужен install.wim в формате WIM (не ESD) из ARM64-образа Windows."));
        }
    }

    static WindowsInstallIso.Inspection verifyInstallIso(ParcelFileDescriptor file) throws IOException {
        var inspection = WindowsInstallIso.inspect(file);
        requireMatchingPayload(inspection);
        return inspection;
    }

    static Path workspace(Context context) {
        return context.getFilesDir().toPath().resolve("windows-25h2-setup.raw");
    }

    static boolean isoSetupAvailable(Context context) {
        return available() && WindowsInstallIso.available() && WindowsIsoBootRuntime.available(context)
                && installToolsAvailable(context);
    }

    static synchronized void prepare(Context context, ParcelFileDescriptor iso, Consumer<Phase> phase)
            throws IOException {
        if (!isoSetupAvailable(context)) throw new IOException("The packaged Windows Setup runtime is unavailable.");
        phase.accept(Phase.CHECKING);
        Path root = context.getFilesDir().toPath().toRealPath();
        Path workspace = root.resolve(workspace(context).getFileName());
        if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("A new private Windows workspace path is required.");
        verifyInstallIso(iso);
        if (root.toFile().getUsableSpace() < requiredFreeBytes())
            throw new IOException("Not enough space to assemble Windows Setup and preserve the device reserve.");

        Path staging = Files.createTempDirectory(root, ".windows-iso-");
        try {
            phase.accept(Phase.COPYING);
            Path boot = Files.createDirectory(staging.resolve("winpe"));
            var runtime = WindowsIsoBootRuntime.prepare(context, iso, boot);
            Path candidate = staging.resolve("workspace.raw");
            Path parts = Files.createDirectory(staging.resolve("parts"));
            buildIsoSource(context, iso, candidate, parts);
            phase.accept(Phase.FINALIZING);
            stageBootRuntime(candidate, runtime);
            verifyInstallTools(context, candidate, true);
            verifyFreshGpt(candidate);

            String[] hashes;
            try (var disk = ParcelFileDescriptor.open(candidate.toFile(), ParcelFileDescriptor.MODE_READ_ONLY)) {
                hashes = auditBootFiles(disk, ESP_OFFSET, SOURCE_PARTITION_BYTES);
            }
            String expectedWim;
            try (var streamed = Files.newInputStream(runtime.wim())) {
                expectedWim = sha(streamed);
            }
            if (!WindowsIsoBootRuntime.EFI_SHA.equals(hashes[0]) || !expectedWim.equals(hashes[1]))
                throw new IOException("Generated Windows Setup boot files failed readback.");
            if (root.toFile().getUsableSpace() < RESERVE_BYTES)
                throw new IOException("The storage reserve changed during Windows preparation.");

            var provenance = context.getSharedPreferences("windows_setup_provenance", Context.MODE_PRIVATE);
            if (!provenance.edit().putString(GENERATED_BOOT_HASH, hashes[1]).commit())
                throw new IOException("Could not record the verified Windows boot image.");
            publish(candidate, workspace);
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException cleanup) {
                Log.w("UAVF-Windows-Setup", "Could not clear ISO staging", cleanup);
            }
        }
    }

    private static void stageBootRuntime(Path candidate, WindowsIsoBootRuntime.FilesForBoot runtime)
            throws IOException {
        try (var disk = ParcelFileDescriptor.open(candidate.toFile(), ParcelFileDescriptor.MODE_READ_WRITE);
             var wim = ParcelFileDescriptor.open(runtime.wim().toFile(), ParcelFileDescriptor.MODE_READ_ONLY);
             var loader = ParcelFileDescriptor.open(runtime.loader().toFile(), ParcelFileDescriptor.MODE_READ_ONLY);
             var bcd = ParcelFileDescriptor.open(runtime.bcd().toFile(), ParcelFileDescriptor.MODE_READ_ONLY);
             var firmware = ParcelFileDescriptor.open(runtime.firmware().toFile(), ParcelFileDescriptor.MODE_READ_ONLY)) {
            int status = stageIsoBootRuntime(disk.getFd(), ESP_OFFSET, SOURCE_PARTITION_BYTES,
                    wim.getFd(), loader.getFd(), bcd.getFd(), firmware.getFd());
            if (status != 0) throw new IOException("Could not stage the Windows boot runtime (" + status + ").");
        }
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
            boolean smp = SMP_DISPLAY_EFI_SHA.equals(hashes[0]);
            boolean fromIso = smp && hashes[1].equals(context.getSharedPreferences(
                    "windows_setup_provenance", Context.MODE_PRIVATE).getString(GENERATED_BOOT_HASH, ""));
            boolean guidedBoot = fromIso || guided != null && guided.bootWimSha.equals(hashes[1]);
            if ((!smp && !BOOT_EFI_SHA.equals(hashes[0]) && !NATIVE_DISPLAY_EFI_SHA.equals(hashes[0]))
                    || (!BOOT_WIM_SHA.equals(hashes[1]) && !guidedBoot))
                throw new IOException("The verified Windows Setup boot files have changed.");
            return new VerifiedBoot(guidedBoot, smp);
        }
    }


    static void setInstallApproval(Path disk, boolean approve) throws IOException {
        try (var descriptor = ParcelFileDescriptor.open(disk.toFile(), ParcelFileDescriptor.MODE_READ_WRITE)) {
            int result = setApproval(descriptor.getFd(), ESP_OFFSET,
                    MEDIA_BYTES - ESP_OFFSET - 33 * SECTOR, approve);
            if (result != 0) throw new IOException("Windows install approval could not be updated (" + result + ")");
        }
    }

    static void requireUnapprovedSetup(Path disk) throws IOException {
        try (var volume = Fat32Volume.open(disk.toFile())) {
            if (volume.exists("UAVF", "INSTALL.OK")) {
                throw new IOException("Automatic installation is staged on this disk. Choose another Windows Setup mode before opening read-only Setup.");
            }
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

    /** Assemble only into an empty staging path. The caller still has to stage and audit the boot runtime. */
    static void buildIsoSource(Context context, ParcelFileDescriptor iso, Path candidate, Path partsDirectory)
            throws IOException {
        try (var payload = WindowsInstallIso.openPayload(context, iso, false)) {
            List<Path> parts = WindowsWimPayload.split(payload, partsDirectory, SPLIT_WIM_PART_BYTES);
            WindowsSetupLayout.createFresh(candidate);
            try (var destination = ParcelFileDescriptor.open(candidate.toFile(),
                    ParcelFileDescriptor.MODE_READ_WRITE)) {
                int result = createSource(iso.getFd(), destination.getFd(), ESP_OFFSET, SOURCE_PARTITION_BYTES);
                if (result != 0) throw new IOException("Could not stage Windows ISO files (" + result + ").");
                putParts(destination, ESP_OFFSET, SOURCE_PARTITION_BYTES, parts, false);
            }
            verifyFreshGpt(candidate);
        }
    }

    static void putParts(ParcelFileDescriptor destination, long offset, long length, List<Path> parts)
            throws IOException {
        putParts(destination, offset, length, parts, true);
    }

    private static void putParts(ParcelFileDescriptor destination, long offset, long length, List<Path> parts,
                                 boolean replaceExisting)
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
            int result = replaceExisting ? writeParts(destination.getFd(), offset, length, descriptors)
                    : writeNewParts(destination.getFd(), offset, length, descriptors);
            if (result != 0) throw new IOException("Windows Setup FAT write/readback failed (" + result + ").");
        } finally {
            for (ParcelFileDescriptor input : inputs) {
                try { input.close(); } catch (IOException ignored) {}
            }
        }
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
    private static native int writeNewParts(int output, long offset, long length, int[] parts);
    private static native int createSource(int iso, int output, long offset, long length);
    private static native int stageIsoBootRuntime(
            int output, long offset, long length, int wim, int loader, int bcd, int firmware);
    private static native int auditFat(int input, long offset, long length, MessageDigest efi, MessageDigest wim);
    private static native int checkInstallTools(int fd, long offset, long length, byte[][] tools, boolean write);
    private static native int readInstalledBcd(int fd, long offset, long length, byte[] output);
    private static native int setApproval(int fd, long offset, long length, boolean approve);
    private static native int renameNew(String from, String to);
}
