// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

/** A missing/unreadable optional brand font must never prevent app startup. */
final class ProductFont {
    static Typeface load(Context context) {
        try { return Typeface.createFromAsset(context.getAssets(), "product-ui/LexendDeca.ttf"); }
        catch (RuntimeException error) {
            Log.w("U-AVF", "Product font unavailable; using system font", error);
            return Typeface.create("sans-serif", Typeface.NORMAL);
        }
    }
}
