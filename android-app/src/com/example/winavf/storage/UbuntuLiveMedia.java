// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.storage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;

/** Prepares app-owned temporary boot media. The caller must stop the VM first. */
public final class UbuntuLiveMedia {
    private static final long PREFIX_BYTES = 134_217_728L;
    private static final long ISO_BYTES = 3_967_463_424L;
    private static final long TRAILER_BYTES = 16_896L;
    // The GPT trailer lives after the ISO and the reserved padding in the live disk.
    private static final long TRAILER_OFFSET = 4_102_012_416L;
    private static final long DISK_BYTES = TRAILER_OFFSET + TRAILER_BYTES;
    private static final long MIN_FREE_BYTES = 4_370_464_768L;
    private static final String ISO_SHA = "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14";
    private static final String TRAILER_SHA = "A0F9379853E2BD1CD9FF2854113CCB971B8A2A236C7C7A0736983EAA427C861B";
    private static final String PREVIOUS_VIRGL_ASSET = "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-real-gnome.img";
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private record Prefix(String asset, String sha) {}

    public enum Profile {
        BASELINE("winavf-P35-rfb-incremental.img", "uavf-ubuntu-live-runtime.ready",
                "p33-platform-prefix-128m.img", "012942E1A9203E659D43A1CEB5AB2DD70AEF6D01A8F4C4FDEA22B146DC088CB1",
                "68582C7187EB83BEEE9E2D994BE4952FC5DE772BF2273AD1ECBCE6621B59BB47"),
        GFXSTREAM("winavf-P7-gfxstream-zink-experimental.img", "uavf-ubuntu-zink-runtime.ready",
                "p7-platform-prefix-gfxstream-zink.img", "24C0C1D7A5D5E070F3D61A87EDE1691525B9ECBCA7BEDBCAD182B5366CC9EE27",
                "D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F"),
        GFXSTREAM_LINUX_FIRST(GFXSTREAM, "p7-platform-prefix-linux-first-gpu.img",
                "E2336A9161C31CC2BBB386C77BFE3459640573DC29F5B8AA7DA61E4270AF95B5",
                "C2C670C75BC0534706078898A82DD683B87A491D320D153C44F2F0BB4D81A764"),
        VIRGL("winavf-P7-virgl-gbm-runtime.img", "uavf-ubuntu-virgl-gbm-runtime.ready",
                "p7-platform-prefix-virgl-gbm.img", "C7AE4D1F9BBDBFFA2CD27063A9FF59E483CC60F828CB3D9BFB66B65572C663DB", ""),
        VIRGL_LINUX_FIRST(VIRGL, "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img",
                "FE451CA13834DC5EDE3096EDC3AF1EF2894BFDECAA7801CED9E69EDB07447858", ""),
        ENCODED(VIRGL, "uavf-live-encoded-geometry-prefix.img",
                "E6A2AEA957CE0871EC28FE73D2781FC560CCAB3FE9EE0E62ADC1B28645D73A49", "");

        private final String diskName;
        private final String markerName;
        private final Prefix prefix;
        private final String diskSha;

        Profile(String diskName, String markerName, String asset, String prefixSha, String diskSha) {
            this.diskName = diskName;
            this.markerName = markerName;
            this.prefix = new Prefix(asset, prefixSha);
            this.diskSha = diskSha;
        }

        Profile(Profile layout, String asset, String prefixSha, String diskSha) {
            this(layout.diskName, layout.markerName, asset, prefixSha, diskSha);
        }

        public String asset() { return prefix.asset(); }
        public String prefixSha() { return prefix.sha(); }

        public String expectedDiskDigest() {
            return diskSha.isEmpty() ? "COMPONENT_HASHES" : diskSha;
        }

        public static Profile select(boolean gfxstream, boolean virgl, boolean linuxFirstGpu,
                                     boolean linuxFirstVirgl, boolean encodedGeometry) {
            if (linuxFirstGpu && !gfxstream) {
                throw new IllegalStateException("Enable the existing Gfxstream/Vulkan experiment before Linux-first-GPU A/B");
            }
            if (linuxFirstVirgl && (!virgl || gfxstream || linuxFirstGpu)) {
                throw new IllegalStateException("Linux-first VirGL requires the GBM Xvnc profile");
            }
            if (encodedGeometry && !linuxFirstVirgl) {
                throw new IllegalArgumentException("Encoded geometry requires the Linux-first VirGL profile");
            }
            if (encodedGeometry) return ENCODED;
            if (linuxFirstVirgl) return VIRGL_LINUX_FIRST;
            if (linuxFirstGpu) return GFXSTREAM_LINUX_FIRST;
            if (gfxstream) return GFXSTREAM;
            return virgl ? VIRGL : BASELINE;
        }
    }

    @FunctionalInterface
    public interface Assets {
        InputStream open(String name) throws IOException;
    }

    private final Assets assets;
    private final Supplier<String> previousVirglSha;

    public UbuntuLiveMedia(Assets assets, Supplier<String> previousVirglSha) {
        this.assets = assets;
        this.previousVirglSha = previousVirglSha;
    }

    public static String previousVirglAsset() { return PREVIOUS_VIRGL_ASSET; }

    public File prepare(File iso, File directory, Profile profile, boolean skipIsoDigest) throws Exception {
        verifyIso(iso, skipIsoDigest);
        File disk = new File(directory, profile.diskName);
        File marker = new File(directory, profile.markerName);

        if (matches(disk, profile.prefix, profile.diskSha)) {
            writeMarker(marker, profile);
            return disk;
        }
        if (disk.isFile() && disk.length() == DISK_BYTES) {
            if (profile == Profile.VIRGL || profile == Profile.VIRGL_LINUX_FIRST) {
                Prefix previous = profile == Profile.VIRGL_LINUX_FIRST
                        ? new Prefix(PREVIOUS_VIRGL_ASSET, previousVirglSha.get())
                        : new Prefix(Profile.VIRGL_LINUX_FIRST.asset(),
                                "925979C0DDF0A36CFC8BC756844D2CA341E4ED8B2B30906B0ED8ADCEE90CD978");
                if (matches(disk, previous, "")) {
                    replacePrefix(disk, marker, profile, previous, "");
                    return disk;
                }
            } else if (profile == Profile.GFXSTREAM || profile == Profile.GFXSTREAM_LINUX_FIRST) {
                Profile previous = profile == Profile.GFXSTREAM ? Profile.GFXSTREAM_LINUX_FIRST : Profile.GFXSTREAM;
                if (matches(disk, previous.prefix, previous.diskSha)) {
                    replacePrefix(disk, marker, profile, previous.prefix, previous.diskSha);
                    return disk;
                }
            }
        }
        if ((profile == Profile.GFXSTREAM || profile == Profile.GFXSTREAM_LINUX_FIRST) && !disk.exists()) {
            File baseline = new File(directory, Profile.BASELINE.diskName);
            if (matches(baseline, Profile.BASELINE.prefix, Profile.BASELINE.diskSha)) {
                promoteBaseline(baseline, disk, marker, profile);
                return disk;
            }
        }

        if (iso.length() != ISO_BYTES) {
            throw new IllegalArgumentException("Select the official Ubuntu 24.04.5 ARM64 Desktop ISO.");
        }
        if (!disk.exists() && directory.getUsableSpace() < MIN_FREE_BYTES) {
            throw new IllegalStateException("Free at least 4.1 GiB in app storage to prepare the temporary Linux boot disk.");
        }
        if (marker.exists() && !marker.delete()) {
            throw new IOException("Cannot invalidate temporary-media cache before rebuild");
        }
        rebuild(iso, disk, profile);
        writeMarker(marker, profile);
        return disk;
    }

    private static void verifyIso(File iso, boolean skipIsoDigest) throws Exception {
        if (!iso.isFile() || iso.length() == 0) throw new IOException("Missing or empty official Ubuntu ISO: " + iso);
        if (skipIsoDigest) return;
        if (iso.length() != ISO_BYTES) throw new IOException("official Ubuntu ISO size mismatch");
        if (!ISO_SHA.equals(digest(iso, 0, ISO_BYTES))) throw new SecurityException("official Ubuntu ISO SHA-256 mismatch");
    }

    private static boolean matches(File disk, Prefix prefix, String diskSha) throws Exception {
        if (!disk.isFile() || disk.length() != DISK_BYTES
                || !digest(disk, 0, PREFIX_BYTES).equals(prefix.sha())) return false;
        if (!diskSha.isEmpty()) return diskSha.equals(digest(disk, 0, DISK_BYTES));
        return ISO_SHA.equals(digest(disk, PREFIX_BYTES, ISO_BYTES))
                && TRAILER_SHA.equals(digest(disk, TRAILER_OFFSET, TRAILER_BYTES));
    }

    private void replacePrefix(File disk, File marker, Profile profile, Prefix previous, String previousDiskSha)
            throws Exception {
        try {
            writePrefix(disk, profile.prefix);
            requireMatch(disk, profile.prefix, profile.diskSha);
            writeMarker(marker, profile);
        } catch (Exception failure) {
            try {
                writePrefix(disk, previous);
                requireMatch(disk, previous, previousDiskSha);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                throw new IOException("Could not restore pre-A/B live disk", failure);
            }
            throw failure;
        }
    }

    private void promoteBaseline(File baseline, File disk, File marker, Profile profile) throws Exception {
        boolean renamed = false;
        try {
            writePrefix(baseline, profile.prefix);
            requireMatch(baseline, profile.prefix, profile.diskSha);
            if (!baseline.renameTo(disk)) throw new IOException("Could not rename the verified Zink test disk.");
            renamed = true;
            writeMarker(marker, profile);
        } catch (Exception failure) {
            File original = renamed ? disk : baseline;
            try {
                writePrefix(original, Profile.BASELINE.prefix);
                requireMatch(original, Profile.BASELINE.prefix, Profile.BASELINE.diskSha);
                if (renamed && !disk.renameTo(baseline)) throw new IOException("Could not restore the baseline filename.");
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                throw new IOException("Could not restore the baseline live disk after Zink conversion", failure);
            }
            if (marker.exists() && !marker.delete()) {
                failure.addSuppressed(new IOException("Could not remove the failed conversion marker"));
            }
            throw failure;
        }
    }

    private void rebuild(File iso, File disk, Profile profile) throws Exception {
        try {
            try (RandomAccessFile output = new RandomAccessFile(disk, "rw")) {
                output.setLength(0);
                output.setLength(DISK_BYTES);
                writeAsset(output, profile.prefix, 0, PREFIX_BYTES);
                try (InputStream input = new BufferedInputStream(new FileInputStream(iso))) {
                    copyVerified(input, output, PREFIX_BYTES, ISO_BYTES, ISO_SHA, "Ubuntu ISO");
                }
                writeAsset(output, new Prefix("p33-gpt-trailer.bin", TRAILER_SHA), TRAILER_OFFSET, TRAILER_BYTES);
                output.getFD().sync();
            }
            requireMatch(disk, profile.prefix, profile.diskSha);
        } catch (Exception failure) {
            if (disk.exists() && !disk.delete()) {
                failure.addSuppressed(new IOException("Could not remove incomplete temporary live disk"));
            }
            throw failure;
        }
    }

    private static void requireMatch(File disk, Prefix prefix, String diskSha) throws Exception {
        if (!matches(disk, prefix, diskSha)) throw new SecurityException("Prepared Linux disk integrity check failed");
    }

    private void writePrefix(File disk, Prefix prefix) throws Exception {
        try (RandomAccessFile output = new RandomAccessFile(disk, "rw")) {
            writeAsset(output, prefix, 0, PREFIX_BYTES);
            output.getFD().sync();
        }
    }

    private void writeAsset(RandomAccessFile output, Prefix asset, long offset, long size) throws Exception {
        try (InputStream input = assets.open(asset.asset())) {
            copyVerified(input, output, offset, size, asset.sha(), asset.asset());
        }
    }

    private static void copyVerified(InputStream input, RandomAccessFile output, long offset, long size,
                                     String expected, String label) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1024 * 1024];
        output.seek(offset);
        long total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            total += count;
            if (total > size) throw new IOException(label + " is larger than expected");
            hash.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
        if (total != size || !expected.equals(HEX.formatHex(hash.digest()))) {
            throw new SecurityException(label + " size/SHA-256 verification failed");
        }
    }

    private static String digest(File file, long offset, long size) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1024 * 1024];
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(offset);
            for (long remaining = size; remaining > 0;) {
                int count = (int)Math.min(buffer.length, remaining);
                input.readFully(buffer, 0, count);
                hash.update(buffer, 0, count);
                remaining -= count;
            }
        }
        return HEX.formatHex(hash.digest());
    }

    private static void writeMarker(File marker, Profile profile) throws IOException {
        String content = "ISO_SHA256=" + ISO_SHA + "\nPREFIX_SHA256=" + profile.prefixSha()
                + "\nTRAILER_SHA256=" + TRAILER_SHA + "\nDISK_SHA256=" + profile.expectedDiskDigest()
                + "\nBYTES=" + DISK_BYTES + "\n";
        try (FileOutputStream output = new FileOutputStream(marker, false)) {
            output.write(content.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        }
    }
}
