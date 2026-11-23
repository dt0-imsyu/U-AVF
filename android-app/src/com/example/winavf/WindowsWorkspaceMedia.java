// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Windows installer file access, owned URI grants and disk inspection. */
final class WindowsWorkspaceMedia {
    enum BootOptions { INSTALLED, PREVIEW_AND_INSTALL, PREVIEW, PENDING_INSTALL, PARTITIONED, UNAVAILABLE }
    private static final String ISO_URI = "windows_install_iso_uri";
    private final Context context;
    private final ContentResolver resolver;
    private final SharedPreferences prefs;

    WindowsWorkspaceMedia(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.prefs = prefs;
    }

    Uri selectedIso() {
        String stored = prefs.getString(ISO_URI, "");
        if (stored.isEmpty()) return null;
        Uri uri = Uri.parse(stored);
        return resolver.getPersistedUriPermissions().stream()
                .anyMatch(grant -> grant.isReadPermission() && uri.equals(grant.getUri())) ? uri : null;
    }

    WindowsInstallIso.Inspection selectIso(Uri source) throws IOException {
        try (ParcelFileDescriptor file = resolver.openFileDescriptor(source, "r")) {
            if (file == null) throw new IOException("Unable to open the selected ISO.");
            var result = WindowsSetupDisk.available() ? WindowsSetupDisk.verifyInstallIso(file)
                    : WindowsInstallIso.inspect(file);
            if (WindowsSetupDisk.isoSetupAvailable(context)) WindowsIsoBootRuntime.verifySource(context, file);
            rememberIso(source);
            return result;
        }
    }

    void forgetIso() {
        synchronized (prefs) {
            Uri uri = selectedIso();
            prefs.edit().remove(ISO_URI).apply();
            if (uri != null) {
                try {
                    resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {}
            }
        }
    }

    private void rememberIso(Uri uri) {
        synchronized (prefs) {
            int read = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            resolver.takePersistableUriPermission(uri, read);
            String previous = prefs.getString(ISO_URI, "");
            prefs.edit().putString(ISO_URI, uri.toString()).apply();
            if (!previous.isEmpty() && !previous.equals(uri.toString())) {
                try {
                    resolver.releasePersistableUriPermission(Uri.parse(previous), read);
                } catch (SecurityException ignored) {}
            }
        }
    }

    void prepare(Uri iso, Consumer<WindowsSetupDisk.Phase> progress) throws IOException {
        try (ParcelFileDescriptor source = resolver.openFileDescriptor(iso, "r")) {
            if (source == null) throw new IOException("The selected Windows ISO could not be opened.");
            WindowsSetupDisk.prepare(context, source, progress);
        }
    }

    BootOptions inspect(Path workspace) throws IOException {
        if (!Files.isRegularFile(workspace, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected Windows workspace path");
        WindowsSetupLayout.Inspection layout = WindowsSetupLayout.inspect(workspace);
        boolean runtimeAvailable = WindowsSetupRuntime.available(context);
        return switch (layout.state()) {
            case NTFS_TARGET -> {
                if (!runtimeAvailable) yield BootOptions.PENDING_INSTALL;
                try {
                    WindowsSetupDisk.verifyInstalledBcd(workspace, layout);
                } catch (IOException unverified) {
                    Log.w("UAVF-Windows-Setup", "Windows target exists without a verified boot", unverified);
                    yield BootOptions.PENDING_INSTALL;
                }
                yield BootOptions.INSTALLED;
            }
            case FRESH -> {
                if (!runtimeAvailable) yield BootOptions.UNAVAILABLE;
                boolean guided = WindowsSetupDisk.verifyFresh(context, workspace);
                yield guided ? BootOptions.PREVIEW_AND_INSTALL : BootOptions.PREVIEW;
            }
            case PARTITIONED -> BootOptions.PARTITIONED;
        };
    }
}
