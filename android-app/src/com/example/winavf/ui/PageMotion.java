// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Shared movement for screen content. Wallpaper belongs to the stationary parent. */
public final class PageMotion {
    private static final PathInterpolator EASE_OUT = new PathInterpolator(.22f,.75f,.27f,1f);

    private PageMotion() {}

    public static void enter(View view,boolean back) {
        if(!ValueAnimator.areAnimatorsEnabled()) return;
        view.animate().cancel();
        float distance=32*view.getResources().getDisplayMetrics().density;
        view.setTranslationX(back?-distance:distance);
        view.setAlpha(0f);
        view.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(EASE_OUT).start();
    }

    static void showPanel(View view) {
        if(!ValueAnimator.areAnimatorsEnabled()) return;
        view.setTranslationY(12*view.getResources().getDisplayMetrics().density);
        view.setAlpha(0f);
        view.animate().translationY(0f).alpha(1f).setDuration(220).setInterpolator(EASE_OUT).start();
    }

    static void fadeOut(View view,Runnable end) {
        if(!ValueAnimator.areAnimatorsEnabled()) {end.run();return;}
        view.animate().alpha(0f).setDuration(180).setInterpolator(EASE_OUT).withEndAction(end).start();
    }
}
