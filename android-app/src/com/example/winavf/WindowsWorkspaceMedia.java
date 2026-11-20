// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Windows installer file access, owned URI grants and disk inspection. */
final class WindowsWorkspaceMedia {
    enum BootOptions { INSTALLED, PREVIEW_AND_INSTALL, PREVIEW, PENDING_INSTALL, PARTITIONED, UNAVAILABLE }
    enum Document {
        ISO("windows_install_iso_uri"), WINPE("windows_setup_winpe_uri");
        final String key;
        Document(String key) { this.key = key; }
    }
    private final Context context;
    private final ContentResolver resolver;
    private final SharedPreferences prefs;

    WindowsWorkspaceMedia(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.prefs = prefs;
    }

    Uri selected(Document document) {
        String stored = prefs.getString(document.key, "");
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
            remember(Document.ISO, source);
            return result;
        }
    }

    void selectWinpe(Uri source) throws IOException {
        try (ParcelFileDescriptor file = resolver.openFileDescriptor(source, "r")) {
            if (file == null || file.getStatSize() != WindowsSetupDisk.MEDIA_BYTES) {
                throw new IOException("Выберите проверенный образ WinPE указанного размера.");
            }
            remember(Document.WINPE, source);
        }
    }

    private void remember(Document type, Uri uri) {
        synchronized (prefs) {
            int read = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            resolver.takePersistableUriPermission(uri, read);
            String previous = prefs.getString(type.key, "");
            prefs.edit().putString(type.key, uri.toString()).apply();

            Document other = type == Document.ISO ? Document.WINPE : Document.ISO;
            if (!previous.isEmpty() && !previous.equals(uri.toString()) && !previous.equals(prefs.getString(other.key, ""))) {
                try {
                    resolver.releasePersistableUriPermission(Uri.parse(previous), read);
                } catch (SecurityException ignored) {}
            }
        }
    }

    void prepare(Uri iso, Uri template, Consumer<WindowsSetupDisk.Phase> progress) throws IOException {
        try (ParcelFileDescriptor source = resolver.openFileDescriptor(iso, "r");
             ParcelFileDescriptor winpe = resolver.openFileDescriptor(template, "r")) {
            if (source == null || winpe == null) throw new IOException("Windows source files could not be opened.");
            WindowsSetupDisk.prepare(context, source, winpe, progress);
        }
    }

    BootOptions inspect(Path workspace) throws IOException {
        if (!Files.isRegularFile(workspace, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected Windows workspace path");
        WindowsSetupLayout.Inspection layout = WindowsSetupLayout.inspect(workspace);
        boolean runtimeAvailable = WindowsSetupRuntime.available(context);
        return switch (layout.state()) {
            case NTFS_TARGET -> {
                if (!runtimeAvailable) yield BootOptions.PENDING_INSTALL;
                WindowsSetupDisk.verifyInstalledBcd(workspace, layout);
                yield BootOptions.INSTALLED;
            }
            case FRESH -> {
                if (!runtimeAvailable) yield BootOptions.UNAVAILABLE;
                boolean guided = WindowsSetupDisk.hasGuidedProfile(context) && WindowsSetupDisk.verifyFresh(context, workspace);
                yield guided ? BootOptions.PREVIEW_AND_INSTALL : BootOptions.PREVIEW;
            }
            case PARTITIONED -> BootOptions.PARTITIONED;
        };
    }
}
