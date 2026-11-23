// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.ArrayList;

/** Keeps wallpaper and launcher icon choices independent of VM and workspace state. */
public final class ProductAppearance {
    public enum Color {
        BLUE("blue", "Синяя", 0xff0a84ff, 0x263b57, "Blue"),
        GREY("grey", "Серая", 0xff53677e, 0x394654, "Grey"),
        PURPLE("purple", "Фиолетовая", 0xff7953c7, 0x453151, "Purple");

        final String asset;
        final String label;
        final int accent;
        final int surfaceTint;
        final String alias;

        Color(String asset, String label, int accent, int surfaceTint, String alias) {
            this.asset = asset;
            this.label = label;
            this.accent = accent;
            this.surfaceTint = surfaceTint;
            this.alias = alias;
        }
    }

    private static final String ASSETS = "Themes and App icons/";

    private ProductAppearance() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("product_appearance", Context.MODE_PRIVATE);
    }

    public static Color theme(Context context) {
        String stored = preferences(context).getString("theme", Color.BLUE.asset);
        for (Color color : Color.values()) if (color.asset.equals(stored)) return color;
        return Color.BLUE;
    }

    static void setTheme(Context context, Color color) {
        preferences(context).edit().putString("theme", color.asset).apply();
    }

    public static int accent(Context context) { return theme(context).accent; }

    static int tint(Context context, int color) {
        return tint(theme(context), color);
    }

    static int tint(Color theme, int color) {
        if (color == Color.BLUE.accent) return theme.accent;
        return switch (color) {
            case 0xff1c1d20, 0xff242529, 0xff27282c, 0xff303238, 0xff34363b,
                    0xff3a3c42, 0xff492d30, 0xf2282b31 -> tintSurface(color, theme.surfaceTint);
            default -> color;
        };
    }

    private static int tintSurface(int color, int tint) {
        int red = surfaceChannel(color >> 16, tint >> 16);
        int green = surfaceChannel(color >> 8, tint >> 8);
        int blue = surfaceChannel(color, tint);
        return (color & 0xff000000) | (red << 16) | (green << 8) | blue;
    }

    private static int surfaceChannel(int base, int tint) {
        return ((base & 255) * 3 + (tint & 255) + 2) / 4;
    }

    static String wallpaper(Context context) { return ASSETS + "Wallpaper " + theme(context).asset + ".png"; }
    static String iconAsset(Color color) { return ASSETS + "Icon " + color.asset + ".png"; }

    static Color launcherIcon(Context context) {
        var manager = context.getPackageManager();
        for (Color color : Color.values()) {
            if (manager.getComponentEnabledSetting(alias(context, color)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return color;
            }
        }
        return Color.BLUE;
    }

    static void setLauncherIcon(Context context, Color selected) {
        var settings = new ArrayList<PackageManager.ComponentEnabledSetting>();
        for (Color color : Color.values()) {
            settings.add(new PackageManager.ComponentEnabledSetting(alias(context, color),
                    color == selected ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP));
        }
        context.getPackageManager().setComponentEnabledSettings(settings);
    }

    private static ComponentName alias(Context context, Color color) {
        return new ComponentName(context.getPackageName(), "com.example.winavf.Launcher" + color.alias);
    }
}
