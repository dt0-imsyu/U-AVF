// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class WindowsIsoProfileTest {
    public static void main(String[] args) throws Exception {
        long wimSize = 6_347_029_986L;
        WindowsSetupDisk.requireMatchingPayload(new WindowsInstallIso.Inspection(7_253_645_312L, 900, wimSize, false));
        WindowsSetupDisk.requireMatchingPayload(new WindowsInstallIso.Inspection(7_253_647_360L, 900, wimSize, false));
        WindowsSetupDisk.requireMatchingPayload(
                new WindowsInstallIso.Inspection(8_528_523_264L, 630_231_583, 7_612_941_295L, false));
        expectFailure(() -> WindowsSetupDisk.requireMatchingPayload(
                new WindowsInstallIso.Inspection(7_253_645_312L, 900, 0, false)));
        expectFailure(() -> WindowsSetupDisk.requireMatchingPayload(
                new WindowsInstallIso.Inspection(7_253_645_312L, 900, wimSize, true)));
        WindowsWimPayload.requireSetupImage(1);
        expectFailure(() -> WindowsWimPayload.requireSetupImage(0));
        expectFailure(() -> WindowsWimPayload.requireSetupImage(2));
        byte[] payload = "abc".getBytes(StandardCharsets.US_ASCII);
        String result = WindowsInstallIso.digestPayload(payload.length, (offset, size, buffer) -> {
            buffer[0] = payload[(int) offset];
            return 1;
        });
        if (!result.equals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"))
            throw new AssertionError("Digest did not cover the exact returned bytes");
        expectFailure(() -> WindowsInstallIso.digestPayload(3, (offset, size, buffer) -> 0));
        expectFailure(() -> WindowsInstallIso.digestPayload(3, (offset, size, buffer) -> size + 1));

        // The Windows ARM64 ISOs declare partition 2989 while libudfread 1.2.0
        // hardcoded partition 0, so every selection failed with "Could not read
        // the UDF filesystem". Pin the status mapping so a regression is caught
        // here instead of looking like a dead file picker.
        IOException udf = WindowsInstallIso.statusFailure(2);
        if (!udf.getMessage().equals("Could not read the UDF filesystem in this ISO."))
            throw new AssertionError("UDF status text changed: " + udf.getMessage());
        if (WindowsInstallIso.statusFailure(3).getMessage().equals(udf.getMessage()))
            throw new AssertionError("Missing ARM64 EFI must not report a UDF failure");
        if (WindowsInstallIso.statusFailure(6).getMessage().equals(udf.getMessage()))
            throw new AssertionError("Incomplete ISO sectors must not report a UDF failure");
        for (int status = 0; status <= 6; status++) {
            if (status == 0) continue;
            if (WindowsInstallIso.statusFailure(status).getMessage().isBlank())
                throw new AssertionError("Empty message for status " + status);
        }
        checkBootConfigurationPatch();

        System.out.println("WINDOWS_ISO_PROFILE_TEST=PASS");
    }

    private static void checkBootConfigurationPatch() throws Exception {
        byte[] source = {1, 2, 3};
        byte[] patch = ByteBuffer.allocate(29).putLong(0x5541564642434431L).putInt(3)
                .putInt(1).putInt(2).putInt(-1).putInt(1).put((byte) 4).array();
        try (var input = new DataInputStream(new ByteArrayInputStream(patch))) {
            if (!Arrays.equals(WindowsIsoBootRuntime.patchBcd(source, input), new byte[]{2, 3, 4}))
                throw new AssertionError("BCD patch did not reuse the stock bytes");
        }
        ByteBuffer.wrap(patch).putInt(12, 2);
        expectFailure(() -> {
            try (var input = new DataInputStream(new ByteArrayInputStream(patch))) {
                WindowsIsoBootRuntime.patchBcd(source, input);
            }
        });
    }

    private interface CheckedAction { void run() throws IOException; }

    private static void expectFailure(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Invalid Windows media was accepted");
    }
}
