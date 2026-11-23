// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Opens the stable application task from any of the replaceable launcher icons. */
public final class LauncherEntryActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var intent = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
