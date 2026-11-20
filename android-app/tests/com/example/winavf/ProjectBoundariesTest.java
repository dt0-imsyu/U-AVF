// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.diagnostics.DiagnosticJournal;
import com.example.winavf.diagnostics.CompatibilityHistory;
import com.example.winavf.navigation.ProductNavigation;
import com.example.winavf.navigation.ProductNavigation.Location;
import com.example.winavf.navigation.ProductScreen;
import com.example.winavf.storage.UbuntuLiveMedia;
import com.example.winavf.storage.UbuntuLiveMedia.Profile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class ProjectBoundariesTest {
    public static void main(String[] args) throws Exception {
        navigation();
        diagnostics();
        compatibilityHistory();
        liveMedia();
        if (args.length == 3) verifyRealLiveMedia(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("ProjectBoundariesTest PASS");
    }

    private static void verifyRealLiveMedia(Path iso, Path assets, Path scratch) throws Exception {
        Path directory = Files.createTempDirectory(scratch, "uavf-live-media-test-");
        Path baseline = directory.resolve("winavf-P35-rfb-incremental.img");
        Path gfxstream = directory.resolve("winavf-P7-gfxstream-zink-experimental.img");
        Path baselineMarker = directory.resolve("uavf-ubuntu-live-runtime.ready");
        Path gfxstreamMarker = directory.resolve("uavf-ubuntu-zink-runtime.ready");
        var media = new UbuntuLiveMedia(name -> Files.newInputStream(assets.resolve(name)),
                () -> { throw new AssertionError("Unexpected VirGL asset lookup"); });
        try {
            var disk = media.prepare(iso.toFile(), directory.toFile(), Profile.BASELINE, false);
            check(disk.toPath().equals(baseline) && disk.length() == 4_102_029_312L, "Original boot disk geometry");
            check(Files.readString(baselineMarker).contains(Profile.BASELINE.expectedDiskDigest()), "Baseline receipt");

            var broken = new UbuntuLiveMedia(name -> {
                if (name.equals(Profile.GFXSTREAM_LINUX_FIRST.asset())) throw new IOException("Test conversion failure");
                return Files.newInputStream(assets.resolve(name));
            }, () -> { throw new AssertionError("Unexpected VirGL asset lookup"); });
            try {
                broken.prepare(iso.toFile(), directory.toFile(), Profile.GFXSTREAM_LINUX_FIRST, true);
                throw new AssertionError("Conversion should propagate the simulated failure");
            } catch (IOException expected) {
                check(expected.getMessage().equals("Test conversion failure"), "Verified rollback preserves original error");
            }
            check(Files.exists(baseline) && Files.notExists(gfxstreamMarker), "Failed conversion leaves baseline ready");

            disk = media.prepare(iso.toFile(), directory.toFile(), Profile.GFXSTREAM_LINUX_FIRST, true);
            check(disk.toPath().equals(gfxstream) && Files.notExists(baseline), "Reuse disk with the requested profile");
            check(Files.readString(gfxstreamMarker).contains(Profile.GFXSTREAM_LINUX_FIRST.expectedDiskDigest()),
                    "Receipt reports the requested profile");
            disk = media.prepare(iso.toFile(), directory.toFile(), Profile.GFXSTREAM, true);
            check(disk.toPath().equals(gfxstream) && Files.readString(gfxstreamMarker).contains(Profile.GFXSTREAM.expectedDiskDigest()),
                    "Switch Gfxstream profiles on the same disk");
            System.out.println("REAL_LIVE_MEDIA_TEST=PASS");
        } finally {
            Files.deleteIfExists(baselineMarker);
            Files.deleteIfExists(gfxstreamMarker);
            Files.deleteIfExists(baseline);
            Files.deleteIfExists(gfxstream);
            Files.delete(directory);
        }
    }

    private static void navigation() {
        var navigation = new ProductNavigation();
        var home = new Location(ProductScreen.MODE_SELECTION, "Performance", false, false);
        var settings = new Location(ProductScreen.VM_SETTINGS, "Display", true, false);
        navigation.opening(ProductScreen.SYSTEMS, home, true, true, false);
        navigation.opening(ProductScreen.INPUT_SETTINGS, settings, true, true, false);
        check(navigation.back(true) == null, "Tab switches and the nested input pane add no route");

        var desktop = new Location(ProductScreen.VM_SETTINGS, "Display", false, true);
        navigation.opening(ProductScreen.DIAGNOSTICS, home, true, true, false);
        navigation.opening(ProductScreen.VM_SETTINGS, desktop, false, false, true);
        var restored = new ProductNavigation();
        restored.restore(navigation.snapshot());
        var returnToDesktop = restored.back(true);
        check(returnToDesktop.screen() == null && returnToDesktop.drawer(), "Restore desktop and its drawer");
        check(restored.back(true).equals(home), "Preserve the previous product route");
        check(navigation.back(false).equals(home), "Skip the desktop if the VM has since stopped");
    }

    private static void diagnostics() {
        var journal = new DiagnosticJournal(value -> value);
        String history = "2026-11-20T00:00:00Z\tAVF\tEvent\tCompatibility result: Compatible";
        check(journal.newestFirst(history).get(0).component().equals("Check"), "Read existing persisted entries");
        for (int i = 0; i < 130; i++) {
            history = journal.append(history, Instant.EPOCH, "Ubuntu", "Event", "Frame " + i + "\nPresented");
        }
        var entries = journal.newestFirst(history);
        check(entries.size() == 128, "Retain 128 rows");
        check(entries.get(0).event().equals("Frame 129 Presented"), "Newest first and single-line export");
        check(entries.get(127).event().equals("Frame 2 Presented"), "Retain the correct oldest entry");
    }

    private static void compatibilityHistory() {
        String history = "";
        for (int run = 0; run < 13; run++) {
            history = CompatibilityHistory.append(history, new CompatibilityHistory.Run(
                    Instant.EPOCH.plusSeconds(run), "%032x".formatted(run), "COMPATIBLE",
                    "VM_CREATED=true\nVSOCK_VERIFIED=" + (run == 12 ? "true" : "false")));
        }
        var runs = CompatibilityHistory.read("invalid\n" + history);
        check(runs.size() == 12, "Compatibility history is bounded");
        check(runs.get(0).completed().equals(Instant.EPOCH.plusSeconds(12)), "Latest report first");
        String result = CompatibilityHistory.search(runs, "vsock_verified=TRUE", "");
        check(result.contains("VM_CREATED=true") && !result.contains("VSOCK_VERIFIED=false"),
                "Search returns whole reports and case-insensitive matches in their details");
        history = CompatibilityHistory.append(history, runs.get(0));
        check(CompatibilityHistory.read(history).equals(runs), "Saving a completed run twice is idempotent");
        var previous = new CompatibilityHistory.Run(null, "f".repeat(32), "INSUFFICIENT_DATA", "Older report");
        check(CompatibilityHistory.read(CompatibilityHistory.append("", previous)).get(0).equals(previous),
                "Legacy report survives without a guessed timestamp");
        check(CompatibilityHistory.search(java.util.List.of(), "older", "Older report").equals("Older report"),
                "Report saved by previous app versions is searchable");
    }

    private static void liveMedia() throws Exception {
        check(Profile.select(false, false, false, false, false) == Profile.BASELINE, "Software fallback");
        check(Profile.select(false, true, false, true, false) == Profile.VIRGL_LINUX_FIRST, "Linux-first VirGL");
        check(Profile.select(false, true, false, true, true) == Profile.ENCODED, "Encoded geometry");
        check(Profile.select(true, true, true, false, false) == Profile.GFXSTREAM_LINUX_FIRST, "Linux-first Gfxstream");
        var directory = Files.createTempDirectory("uavf-live-media-test-");
        var iso = directory.resolve("incorrect.iso");
        var staged = directory.resolve("winavf-P35-rfb-incremental.img");
        var marker = directory.resolve("uavf-ubuntu-live-runtime.ready");
        try {
            Files.writeString(iso, "invalid input");
            Files.writeString(staged, "previous disk");
            Files.writeString(marker, "previous marker");
            var media = new UbuntuLiveMedia(name -> { throw new AssertionError("Unexpected asset access"); },
                    () -> { throw new AssertionError("Unexpected previous asset lookup"); });
            try {
                media.prepare(iso.toFile(), directory.toFile(), Profile.BASELINE, false);
                throw new AssertionError("Invalid ISO was accepted");
            } catch (IOException expected) {
                check(expected.getMessage().contains("size mismatch"), "Rejected invalid ISO before staging");
            }
            check(Files.readString(staged).equals("previous disk"), "Preserve existing image on rejection");
            check(Files.readString(marker).equals("previous marker"), "Preserve existing marker on rejection");
        } finally {
            Files.deleteIfExists(marker);
            Files.deleteIfExists(staged);
            Files.deleteIfExists(iso);
            Files.delete(directory);
        }
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
