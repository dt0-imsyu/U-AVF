// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import java.util.function.Consumer;

/** Routes Android document results to the guest media flows. */
final class GuestIsoPicker {
    enum Target {
        UBUNTU_INSTALL(21825),
        UBUNTU_LIVE(21829),
        WINDOWS_INSTALL(21827);

        final int requestCode;

        Target(int requestCode) {
            this.requestCode = requestCode;
        }
    }

    private final Activity activity;
    private final Consumer<Uri> windowsIso;
    private final Consumer<Target> ubuntuReady;
    private final Consumer<String> status;

    GuestIsoPicker(Activity activity, Consumer<Uri> windowsIso, Consumer<Target> ubuntuReady,
                   Consumer<String> status) {
        this.activity = activity;
        this.windowsIso = windowsIso;
        this.ubuntuReady = ubuntuReady;
        this.status = status;
    }

    void open(Target target) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (target == Target.WINDOWS_INSTALL) {
            // Windows keeps access to the selected ISO until disk preparation.
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
        try {
            activity.startActivityForResult(intent, target.requestCode);
        } catch (ActivityNotFoundException | SecurityException error) {
            status.accept("Could not open image picker: " + IoUtil.rootMessage(error));
        }
    }

    void accept(int requestCode, Uri source) {
        for (Target target : Target.values()) {
            if (target.requestCode != requestCode) continue;
            if (target == Target.WINDOWS_INSTALL) {
                windowsIso.accept(source);
            } else {
                new Thread(() -> importUbuntu(source, target), "U-AVF-import-ubuntu-iso").start();
            }
            return;
        }
    }

    private void importUbuntu(Uri source, Target target) {
        try {
            status.accept("Checking the selected official Ubuntu image…");
            boolean alreadyStaged = UbuntuIsoImport.stage(activity.getContentResolver(), source,
                    activity.getExternalFilesDir(null),
                    bytes -> status.accept("Importing Ubuntu image: " + (bytes >> 20) + " MiB"));
            ubuntuReady.accept(target);
            status.accept(alreadyStaged ? "The selected Ubuntu image matches the copy already staged in U-AVF."
                    : "Official Ubuntu 24.04.5 ARM64 image verified and ready.");
        } catch (Exception error) {
            status.accept("Ubuntu image was not accepted: " + IoUtil.rootMessage(error));
        }
    }
}
