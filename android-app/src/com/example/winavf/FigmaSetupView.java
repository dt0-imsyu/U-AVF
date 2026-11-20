// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.runtime.VmLifecycle;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** The status sheet shows evidence from the installed workspace and live installer. */
final class FigmaSetupView extends FigmaCanvas {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int RIGHT=Gravity.END|Gravity.CENTER_VERTICAL;
    private final TextView system,platform,runtime,phase,action;
    private final FigmaProgress progress;

    FigmaSetupView(Context context,boolean iso,Runnable primary,Runnable back,Runnable diagnostics) {
        super(context,true,1393);
        asset("v12/8b7c5-svg.png",168,83,26,34);
        button("Назад",200,56,260,88,34,BLUE,0,0,0,back).setGravity(LEFT);
        text("Состояние установки",160,228,2200,76,64,TEXT,true,LEFT);
        text("Проверено последним запуском",160,460,2240,40,28,MUTED,false,LEFT);
        shape(160,520,2240,480,0xff1c1d20,0,32);
        system=row(520,"Установленная система","Не проверена");
        platform=row(640,"EFI / платформа U-AVF","Чтение отчёта…");
        row(760,"Официальный ISO",iso?"Образ доступен":"Не выбран");
        runtime=row(880,"Runtime гостя","Не проверен");
        phase=text("",160,1030,2240,54,30,MUTED,false,LEFT);
        progress=new FigmaProgress(context);
        widget(progress,160,1100,2240,12);
        progress.setVisibility(View.GONE);
        action=button("",160,1190,620,96,34,TEXT,BLUE,0,48,primary);
        button("Проверки и диагностика",812,1190,500,96,32,TEXT,0,0,0,diagnostics);
    }

    void partitionAudit(boolean verified) {
        update(platform,verified?"Разметка проверена":"Не проверена");
    }

    void state(VmLifecycle.Install stage,boolean disk,boolean installed,boolean running) {
        update(system,installed?"Готова":disk?"Настройка не завершена":"Не установлена");
        update(runtime,installed?"HELLO подтверждён":"Не проверен");
        boolean busy=stage==VmLifecycle.Install.PREPARING || stage==VmLifecycle.Install.INSTALLING
                || stage==VmLifecycle.Install.FINALIZING || stage==VmLifecycle.Install.FIRST_BOOT;
        update(phase,switch(stage) {
            case PREPARING -> "Готовим установку…";
            case INSTALLING -> "Запрошено открытие установщика";
            case FINALIZING -> "Подготовка установленной системы…";
            case FIRST_BOOT -> "Первый запуск Ubuntu…";
            case FAILED -> "Установка не завершилась. Подробности в диагностике.";
            default -> "";
        });
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        update(action,running?"К рабочему столу":busy?"Подождите…":installed?"Открыть воркспейс":"Продолжить настройку");
        action.setEnabled(running||!busy);
        action.setAlpha(action.isEnabled()?1f:0.55f);
    }

    private TextView row(int y,String name,String value) {
        text(name,200,y,1440,120,34,TEXT,false,LEFT);
        return text(value,1640,y,720,120,34,MUTED,false,RIGHT);
    }

    private static void update(TextView view,String value) {
        String localized=UiLanguage.text(value);
        if(!android.text.TextUtils.equals(view.getText(),localized))view.setText(localized);
    }
}
