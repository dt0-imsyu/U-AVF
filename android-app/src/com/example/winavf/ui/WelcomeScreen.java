// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.content.Context;
import android.view.Gravity;
import java.util.function.IntConsumer;

/** Entry point for the local device check and optional reporting consent. */
public final class WelcomeScreen extends ScreenLayout {
    public WelcomeScreen(Context context,Runnable start,Runnable privacy,IntConsumer language) {
        super(context);
        brandIcon(1222, 270, 116);
        text("Добро пожаловать в U-AVF",420,460,1720,126,72,0xfff5f5f7,true,Gravity.CENTER);
        text("Виртуальные машины ARM64 на Android",510,596,1540,72,36,0xffa6a7af,false,Gravity.CENTER);
        button("Начать",1024,750,512,96,36,0xfff5f5f7,0xff0a84ff,0,48,start);
        languagePicker(1140,866,280,72,language);
        text("Совместимость можно проверить повторно в диагностике.",560,958,1440,72,28,0xffa6a7af,false,Gravity.CENTER);
        outlineAction("Подробнее о приватности",920,1044,720,76,28,privacy);
    }

    /**
     * Small translucent control under the start button. It shows only the same
     * glyph the settings screen uses for its language row, so the welcome screen
     * stays readable in every language, and it opens the same list the settings
     * screen shows.
     */
    private void languagePicker(float x, float y, float w, float h, IntConsumer language) {
        shape(x, y, w, h, 0x33101419, 0x80e6eef9, h / 2f);
        android.widget.ImageView glyph = asset("v12/f79ec-svg.png", x + w / 2f - 21, y + h / 2f - 21, 42, 42);
        glyph.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        android.view.View target = new android.view.View(getContext());
        target.setContentDescription("Язык");
        widget(target, x, y, w, h);
        click(target, () -> DropdownMenu.show(target, UiLanguage.NAMES, UiLanguage.selected(), language));
        roundedPress(target, h / 2f, h);
    }
}
