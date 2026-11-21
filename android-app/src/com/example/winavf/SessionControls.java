// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;

/** Owns the desktop menu, its animation and pointer-capture handoff. */
final class SessionControls {
    interface Host {
        boolean frameVisible();
        boolean homeVisible();
        boolean windows();
        boolean vmRunning();
        void preferences(String tab);
        void home();
        void shutdown();
        void reboot();
    }

    private final Activity activity;
    private final FrameSurfaceView frame;
    private final GuestDisplaySurfaces surfaces;
    private final GuestInput input;
    private final Host host;
    private final Button button;
    private final LinearLayout drawer;
    private final View scrim;
    private final GuestIme ime;
    private boolean restoreCapture;

    SessionControls(Activity activity, FrameLayout page, FrameSurfaceView frame, GuestDisplaySurfaces surfaces,
                    DisplayConnection connection, GuestInput input, Host host) {
        this.activity = activity;
        this.frame = frame;
        this.surfaces = surfaces;
        this.input = input;
        this.host = host;
        button = createButton();
        var buttonLayout = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.END);
        buttonLayout.setMargins(0, dp(12), dp(12), 0);
        page.addView(button, buttonLayout);
        scrim = new View(activity);
        scrim.setBackgroundColor(1996488704);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(view -> close());
        page.addView(scrim, fill());
        drawer = new LinearLayout(activity);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setElevation(dp(14));
        drawer.setVisibility(View.GONE);
        page.addView(drawer, fill());
        ime = new GuestIme(activity, key -> {
            if (key == 0 || host.homeVisible() || isOpen()) return;
            connection.send(GuestInput.KEY, 1, key, 0);
            connection.send(GuestInput.KEY, 0, key, 0);
        });
        page.addView(ime, new FrameLayout.LayoutParams(1, 1));
    }

    boolean isOpen() { return drawer.getVisibility() == View.VISIBLE; }

    void toggle() {
        if (isOpen()) close();
        else open();
    }

    void dismissIme() { ime.dismiss(); }

    void open() {
        if (!host.frameVisible() || isOpen()) return;
        dismissIme();
        input.releaseKeys();
        restoreCapture = frame.isPointerCaptured();
        if (restoreCapture) frame.releaseCapturedPointer();
        populate();
        scrim.animate().cancel();
        scrim.setAlpha(0f);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(.58f).setDuration(170).start();
        drawer.animate().cancel();
        drawer.setAlpha(0f);
        drawer.setTranslationX(dp(22));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).translationX(0f).setDuration(190).setInterpolator(new DecelerateInterpolator()).start();
        button.setVisibility(View.GONE);
        button.setContentDescription("Close session controls");
    }

    void close() {
        if (!isOpen()) return;
        drawer.animate().cancel();
        drawer.animate().alpha(0f).translationX(dp(22)).setDuration(150).withEndAction(() -> {
            hideDrawer();
            if (host.frameVisible()) {
                revealButton();
                if (restoreCapture && !frame.isPointerCaptured()) {
                    frame.requestFocus();
                    frame.capturePointer();
                }
            }
            restoreCapture = false;
        }).start();
        scrim.animate().alpha(0f).setDuration(150).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        button.setContentDescription("Open session controls");
    }

    void closeImmediately() {
        drawer.animate().cancel();
        hideDrawer();
        scrim.animate().cancel();
        scrim.setVisibility(View.GONE);
        scrim.setAlpha(1f);
        if (host.frameVisible()) button.setVisibility(buttonVisibility());
        restoreCapture = false;
    }

    void hideButton() { button.setVisibility(View.GONE); }

    void revealButton() {
        if (!host.frameVisible() || isOpen()) return;
        button.animate().cancel();
        button.setVisibility(buttonVisibility());
        button.setAlpha(.92f);
        button.bringToFront();
        button.setContentDescription("Open session controls");
    }

    void populate() {
        drawer.removeAllViews();
        boolean windows = host.windows();
        Mode mode = surfaces.renderer() != null ? Mode.MAVK : surfaces.decoder() != null ? Mode.H264 : Mode.FALLBACK;
        String graphics = windows ? "H.264" : UbuntuSettingsTabs.displayLabel(mode);
        Runnable restart = windows ? null : () -> ProductDialog.show(activity, "Перезагрузить Ubuntu?",
                "Сохраните работу в Ubuntu. U-AVF переподключится после перезагрузки.", "Перезагрузить", false, host::reboot);
        var controls = new FigmaSessionMenu(activity, restoreCapture ? "Захват мыши" : "Мышь", graphics,
                this::close, this::choosePointerMode, this::chooseDisplayMode, () -> host.preferences("Performance"),
                host::home, host::shutdown, restart);
        drawer.addView(controls, new LinearLayout.LayoutParams(-1, -1));
    }

    private void choosePointerMode() {
        ProductDialog.choose(activity, "Мышь", "Управление указателем",
                new String[]{"Захватить указатель", "Освободить указатель"}, restoreCapture ? 0 : 1, index -> {
                    restoreCapture = index == 0;
                    close();
                });
    }

    private void chooseDisplayMode(View anchor) {
        if (host.windows()) {
            host.preferences("Display");
            return;
        }
        var settings = new DisplaySettings(activity.getPreferences(0));
        boolean mavkAvailable = MavkRuntimeAssets.isBundled(activity);
        FigmaDropdown.show(anchor, UbuntuSettingsTabs.displayLabels(mavkAvailable),
                settings.selected(() -> mavkAvailable).ordinal(), index -> {
                    Mode requested = Mode.values()[index];
                    if (requested == settings.selected(() -> mavkAvailable)) return;
                    ProductDialog.show(activity, "Графический вывод",
                            "Изменение вступит в силу после перезапуска Ubuntu.", "Сохранить", false,
                            () -> settings.select(requested, true, host.vmRunning()));
                });
    }

    private Button createButton() {
        var result = new Button(activity);
        result.setText(UiLanguage.text("⋯"));
        result.setTextSize(20f);
        result.setTextColor(0xffffffff);
        result.setAllCaps(false);
        result.setTypeface(ProductFont.load(activity));
        var background = new GradientDrawable();
        background.setColor(-14406859);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), 0xcc293b4f);
        result.setBackground(background);
        result.setContentDescription("Open session controls");
        result.setOnClickListener(view -> { if (host.frameVisible()) toggle(); });
        return result;
    }

    private void hideDrawer() {
        drawer.setVisibility(View.GONE);
        drawer.setAlpha(1f);
        drawer.setTranslationX(0f);
    }

    private int buttonVisibility() {
        return activity.getPreferences(0).getBoolean("hide_runtime_panel_button", false) ? View.GONE : View.VISIBLE;
    }

    private int dp(int size) { return Math.round(size * activity.getResources().getDisplayMetrics().density); }
    private static FrameLayout.LayoutParams fill() { return new FrameLayout.LayoutParams(-1, -1); }
}
