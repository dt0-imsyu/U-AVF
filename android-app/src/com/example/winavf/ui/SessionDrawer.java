// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Controls shared by the Ubuntu and Windows sessions. */
public final class SessionDrawer extends ScreenLayout {
    private static final int X = 760, Y = 480;
    private static final int PRIMARY = 0xfff5f5f7, SECONDARY = 0xffa6a7af;
    private static final int LEFT = Gravity.START | Gravity.CENTER_VERTICAL;

    public SessionDrawer(Context context, boolean temporary, String input, String graphics,
            Runnable close, Runnable chooseInput, java.util.function.Consumer<View> display,
            Runnable settings, Runnable shutdown, Runnable restart) {
        super(context, Surface.OVERLAY);
        int powerTop = temporary ? 484 : 612;
        View panel = shape(X, Y, 1040, powerTop + 136, 0xff27282c, 0, 48);
        panel.setOnTouchListener((view, event) -> true);
        text("Управление VM", X+72, Y+50, 490, 68, 48, PRIMARY, true, LEFT);
        icon("eb26c", X+72, Y+142, 10);
        text("Работает", X+94, Y+121, 500, 48, 28, 0xff77d9a1, false, LEFT);
        icon("1304c", X+919, Y+63, 42);
        action("Закрыть", X+896, Y+40, 88, 88, close);

        row(174, "3b6cc", "Режим ввода", input, chooseInput);
        if (!temporary) {
            TextView output = row(302, "aea17", "Графический вывод", graphics, () -> {});
            output.setOnClickListener(display::accept);
        }
        row(temporary ? 302 : 430, "33456", "Параметры воркспейса", "", settings);

        if (restart != null) {
            button("Завершить…", X+56, Y+powerTop, 448, 96, 34, PRIMARY,
                    ProductAppearance.tint(context, 0xff492d30), 0, 48, shutdown);
            /*
             * The label must match a dictionary key exactly, including its
             * grammatical form. It used to read "Перезагрузка", while every
             * translation is registered under "Перезагрузить", so the button
             * stayed Russian in all languages.
             */
            button("Перезагрузить", X+528, Y+powerTop, 456, 96, 34, PRIMARY,
                    0xff0a84ff, 0, 48, restart);
        } else {
            button("Завершить…", X+56, Y+powerTop, 928, 96, 34, PRIMARY,
                    ProductAppearance.tint(context, 0xff492d30), 0, 48, shutdown);
        }
    }

    private void icon(String name, int x, int y, int size) {
        asset("v12/" + name + "-svg.png", x, y, size, size);
    }

    private TextView row(int top, String image, String title, String value, Runnable action) {
        icon(image, X+96, Y+top+43, 42);
        text(title, X+162, Y+top, value.isEmpty()? 714 : 530, 128, 32, PRIMARY, false, LEFT);
        if (!value.isEmpty()) {
            text(value, X+696, Y+top, action == null ? 250 : 194, 128, 30, SECONDARY,
                    false, Gravity.END | Gravity.CENTER_VERTICAL);
        }
        if (action != null) {
            icon("6c14a", X+914, Y+top+49, 30);
            return action(UiLanguage.text(title)+(value.isEmpty()?"":", "+UiLanguage.text(value)),
                    X+56, Y+top, 928, 128, action);
        }
        return null;
    }

    private TextView action(String label, int x, int y, int width, int height, Runnable run) {
        TextView target = button("", x, y, width, height, 30, PRIMARY, 0, 0, 0, run);
        target.setContentDescription(label);
        return target;
    }
}
