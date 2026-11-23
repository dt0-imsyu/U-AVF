// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Small packaged WinPE additions, applied only to private staging. */
final class WindowsIsoBootRuntime {
    static final String ASSET = "windows-setup/iso-boot-runtime.zip";
    static final String EFI_SHA = "359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A";
    private static final String FIRMWARE_SHA = "9ED3DD035116A39FEAC7C79A9C0C9AC640EA222D499A8EC3B1C6FCEE1ADFE72F";
    /*
     * The boot configuration is byte-identical across Windows 11 releases:
     * 25H2 (26200) and 26H2 (26300) both ship this exact 16 KiB hive. That is
     * why the BCD patch below is not release-specific and why pinning the stock
     * digest is safe.
     */
    private static final String STOCK_BCD_SHA = "B90EF16B94C3DDA7D76CC39840BEFAA9786887517445C3B6F47EBC806D0AB105";
    /** SHA-256 of the result of applying the packaged patch to STOCK_BCD_SHA. */
    private static final String SETUP_BCD_SHA = "034362E7531FF242E32F24F1ABD37EBECA05CC888981D95094E7986A75521784";
    private static final int MAX_BCD = 64 * 1024;
    private static final int MAX_ENTRY = 2 * 1024 * 1024;
    private static final long BCD_MAGIC = 0x5541564642434431L; // UAVFBCD1
    private static final Set<String> ENTRIES = Set.of(
            "bootaa64.efi", "bcd.patch", "qemu_efi.fd",
            "overlay/Windows/System32/openh264.dll",
            "overlay/Windows/System32/winpe-display-startup.exe",
            "overlay/Windows/System32/winpe-encode.exe",
            "overlay/Windows/System32/winpe-screen.exe",
            "overlay/Windows/System32/winpe-vsock.exe",
            "overlay/Windows/System32/winpeshl.ini",
            "overlay/Windows/System32/uavf-drivers/viostor/viostor.cat",
            "overlay/Windows/System32/uavf-drivers/viostor/viostor.inf",
            "overlay/Windows/System32/uavf-drivers/viostor/viostor.sys",
            "overlay/Windows/System32/uavf-drivers/viosock/viosock-test.exe",
            "overlay/Windows/System32/uavf-drivers/viosock/viosock.cat",
            "overlay/Windows/System32/uavf-drivers/viosock/viosock.inf",
            "overlay/Windows/System32/uavf-drivers/viosock/viosock.sys",
            "overlay/Windows/System32/uavf-drivers/viosock/viosocklib-test.exe",
            "overlay/Windows/System32/uavf-drivers/viosock/viosocklib.dll",
            "overlay/Windows/System32/uavf-drivers/viosock/viosockwspsvc.exe",
            "overlay/Windows/System32/uavf-drivers/viosock/vstbridge.exe");

    record FilesForBoot(Path wim, Path loader, Path bcd, Path firmware) {}

    private WindowsIsoBootRuntime() {}

    static boolean available(Context context) {
        if (!WindowsWimPayload.available()) return false;
        try (var asset = context.getAssets().open(ASSET)) {
            return asset.read() == 'P';
        } catch (IOException error) {
            return false;
        }
    }

    static FilesForBoot prepare(Context context, ParcelFileDescriptor iso, Path staging) throws IOException {
        unpack(context, staging);
        Path loader = staging.resolve("bootaa64.efi");
        requireDigest(Files.newInputStream(loader), EFI_SHA);
        Path firmware = staging.resolve("qemu_efi.fd");
        requireDigest(Files.newInputStream(firmware), FIRMWARE_SHA);
        byte[] stock = readStockBcd(context, iso);
        Path bcd = staging.resolve("BCD");
        byte[] setup;
        try (var patch = new DataInputStream(Files.newInputStream(staging.resolve("bcd.patch")))) {
            setup = patchBcd(stock, patch);
        }
        if (!SETUP_BCD_SHA.equals(digest(setup))) throw new IOException("Windows Setup BCD digest differs.");
        Files.write(bcd, setup);
        Path wim = staging.resolve("boot.wim");
        WindowsWimPayload.prepareBootWim(context, iso, wim, staging.resolve("overlay"));
        return new FilesForBoot(wim, loader, bcd, firmware);
    }

    /**
     * Rejects a foreign boot image before the selection is saved or a disk is prepared.
     *
     * The boot image is the only file that genuinely differs between Windows
     * releases, so it is checked for ARM64 Setup metadata and a supported
     * WinPE build before the overlay is applied. Inspect the ISO directly
     * when the document provider supports seekable reads.
     */
    static void verifySource(Context context, ParcelFileDescriptor iso) throws IOException {
        readStockBcd(context, iso);
        try (var payload = WindowsInstallIso.openPayload(context, iso, true)) {
            int verdict = WindowsWimPayload.probeSetupImageFd(payload.getFd());
            if (verdict >= 0) WindowsWimPayload.requireSetupImage(verdict);
            /*
             * A negative verdict means the descriptor could not be read here,
             * not that the medium is wrong: the staging step re-inspects the
             * copied file and reports an unreadable WIM with its own message.
             */
        }
    }

    private static byte[] readStockBcd(Context context, ParcelFileDescriptor iso) throws IOException {
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(
                WindowsInstallIso.openFile(context, iso, "efi/microsoft/boot/bcd"))) {
            byte[] stock = input.readNBytes(MAX_BCD + 1);
            if (stock.length > MAX_BCD || !STOCK_BCD_SHA.equals(digest(stock)))
                throw new IOException("Windows ISO boot configuration does not match the supported release.");
            return stock;
        }
    }

    private static void unpack(Context context, Path staging) throws IOException {
        Set<String> remaining = new HashSet<>(ENTRIES);
        try (var archive = new ZipInputStream(context.getAssets().open(ASSET))) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                if (entry.isDirectory() || !remaining.remove(entry.getName()))
                    throw new IOException("Unexpected WinPE runtime file: " + entry.getName());
                byte[] bytes = archive.readNBytes(MAX_ENTRY + 1);
                if (bytes.length == 0 || bytes.length > MAX_ENTRY)
                    throw new IOException("Invalid WinPE runtime file size: " + entry.getName());
                Path path = staging.resolve(entry.getName());
                Files.createDirectories(path.getParent());
                Files.write(path, bytes);
            }
        }
        if (!remaining.isEmpty()) throw new IOException("Incomplete WinPE runtime bundle.");
    }

    static byte[] patchBcd(byte[] stock, DataInputStream patch) throws IOException {
        if (patch.readLong() != BCD_MAGIC) throw new IOException("Invalid Windows Setup BCD patch.");
        int size = patch.readInt();
        if (size <= 0 || size > MAX_BCD) throw new IOException("Invalid target BCD size.");
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        while (output.size() < size) {
            int offset = patch.readInt();
            int length = patch.readInt();
            if (length <= 0 || length > size - output.size() ||
                    offset < -1 || offset >= 0 && (offset > stock.length || length > stock.length - offset))
                throw new IOException("Invalid Windows Setup BCD patch range.");
            if (offset == -1) {
                byte[] data = new byte[length];
                patch.readFully(data);
                output.writeBytes(data);
            } else {
                output.write(stock, offset, length);
            }
        }
        if (patch.read() != -1) throw new IOException("Unexpected data after BCD patch.");
        return output.toByteArray();
    }

    static void requireDigest(InputStream input, String expected) throws IOException {
        requireDigest(input, expected, "Windows boot file does not match the supported release.");
    }

    private static void requireDigest(InputStream input, String expected, String mismatch) throws IOException {
        try (input) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[256 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            if (!expected.equals(HexFormat.of().withUpperCase().formatHex(digest.digest())))
                throw new IOException(mismatch);
        }
    }

    private static String digest(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
