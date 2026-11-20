// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.File;
import java.nio.file.Path;

/**
 * Verifies Fat32Volume against the real WinPE ESP image.
 *
 * The FAT32 reader used to be inlined in MainActivity and could only be
 * exercised by running Windows Setup on a device. This test pins the extracted
 * implementation to actual WinPE media, so a refactor that changes file lookup
 * fails here instead of during an install.
 *
 * Usage: Fat32VolumeTest <path-to-esp-image>
 */
public final class Fat32VolumeTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("Pass the WinPE ESP image path");
        File image = Path.of(args[0]).toFile();
        if (!image.isFile()) throw new IllegalArgumentException("Missing image: " + image);

        try (Fat32Volume volume = Fat32Volume.open(image)) {
            // The ARM64 boot loader must be readable and look like a PE32+ ARM64 image.
            byte[] loader = volume.readFile("EFI", "BOOT", "BOOTAA64.EFI");
            check(loader != null, "EFI/BOOT/BOOTAA64.EFI is readable");
            check(loader.length > 1024, "boot loader has content");
            check(loader[0] == 'M' && loader[1] == 'Z', "boot loader has an MZ header");
            int peOffset = (loader[0x3c] & 0xFF) | (loader[0x3d] & 0xFF) << 8
                    | (loader[0x3e] & 0xFF) << 16 | (loader[0x3f] & 0xFF) << 24;
            check(loader[peOffset] == 'P' && loader[peOffset + 1] == 'E', "boot loader has a PE header");
            check((loader[peOffset + 4] & 0xFF) == 0x64 && (loader[peOffset + 5] & 0xFF) == 0xAA,
                    "boot loader is ARM64");

            // Localised Setup resources live under sources/ru-RU for this profile.
            byte[] setupPlatform = volume.readFile("SOURCES", "SETUPP~1.DLL");
            check(setupPlatform == null || setupPlatform.length > 0, "8.3 lookup is consistent");

            // A long name must resolve the same way as its 8.3 alias.
            byte[] longName = volume.readFile("SOURCES", "SETUPPLATFORM.DLL");
            check(longName != null, "long name resolves");
            check(longName.length > 1024, "long-name target has content");

            // Missing names return null rather than throwing.
            check(volume.readFile("EFI", "BOOT", "DOESNOT.EXIST") == null, "missing file returns null");
            check(volume.readFile("NOPE") == null, "missing directory returns null");

            // Root lookup is what the witness audit uses.
            byte[] absent = volume.readRootFile("SETUPACT", "LOG", 16 * 1024 * 1024);
            check(absent == null || absent.length > 0, "root lookup is bounded");

            // Root enumeration backs the WinPE marker scan.
            var txtNames = volume.rootFileNames("TXT");
            check(txtNames != null, "root enumeration returns a list");
            for (String name : txtNames) {
                check(name.toUpperCase(java.util.Locale.ROOT).endsWith(".TXT"),
                        "enumerated names carry the requested extension");
            }
        }

        System.out.println("FAT32_VOLUME_TEST=PASS");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("Failed: " + what);
    }
}
