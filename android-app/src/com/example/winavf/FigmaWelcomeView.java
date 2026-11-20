// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;

/** Entry point for the local device check and optional reporting consent. */
final class FigmaWelcomeView extends FigmaCanvas {
    FigmaWelcomeView(Context context,Runnable start,Runnable privacy) {
        super(context,true,1393);
        asset("v12/eacca-svg.png",1222,270,116,116);
        text("Добро пожаловать в U-AVF",420,460,1720,126,72,0xfff5f5f7,true,Gravity.CENTER);
        text("Виртуальные машины ARM64 на Android",510,596,1540,72,36,0xffa6a7af,false,Gravity.CENTER);
        button("Начать",1024,750,512,96,36,0xfff5f5f7,0xff0a84ff,0,48,start);
        text("Совместимость можно проверить повторно в диагностике.",560,958,1440,72,28,0xffa6a7af,false,Gravity.CENTER);
        button("Подробнее о приватности",980,1044,600,76,28,0xffa6a7af,0,0,0,privacy);
    }
}
