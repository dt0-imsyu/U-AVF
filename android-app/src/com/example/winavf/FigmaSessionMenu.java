// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Controls shared by the Ubuntu and Windows session frames in Figma. */
final class FigmaSessionMenu extends FigmaCanvas {
    private static final int X = 760, Y = 236;
    private static final int PRIMARY = 0xfff5f5f7, SECONDARY = 0xffa6a7af;
    private static final int LEFT = Gravity.START | Gravity.CENTER_VERTICAL;

    FigmaSessionMenu(Context context, String input, String graphics,
            Runnable close, Runnable chooseInput, java.util.function.Consumer<View> display,
            Runnable settings, Runnable home, Runnable shutdown, Runnable restart) {
        super(context, true, 0, true);
        View panel = shape(X, Y, 1040, 1000, 0xff27282c, 0, 48);
        panel.setOnTouchListener((view, event) -> true);
        text("Управление VM", X+56, Y+56, 740, 60, 48, PRIMARY, true, LEFT);
        icon("1304c", X+919, Y+63, 42);
        action("Закрыть", X+896, Y+40, 88, 88, close);
        icon("eb26c", X+56, Y+211, 10);
        text("Работает", X+78, Y+192, 500, 48, 24, 0xff77d9a1, false, LEFT);

        row(288, "3b6cc", "Режим ввода", input, chooseInput);
        TextView output=row(416, "aea17", "Графический вывод", graphics, ()->{});
        output.setOnClickListener(display::accept);
        row(544, "33456", "Параметры воркспейса", "", settings);

        icon("c4173", X+96, Y+876, 40);
        text("На главную", X+152, Y+848, 235, 96, 34, PRIMARY, true, LEFT);
        action("На главную", X+56, Y+848, 328, 96, home);
        icon("bbdc3", X+456, Y+876, 40);
        text("Завершить…", X+512, Y+848, 245, 96, 34, PRIMARY, true, LEFT);
        action("Завершить сеанс", X+404, Y+848, 376, 96, shutdown);
        if (restart != null) {
            icon("8de1f", X+823, Y+875, 42);
            action("Перезагрузить", X+800, Y+848, 88, 96, restart);
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
