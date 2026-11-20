// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;

final class FigmaRecovery extends FigmaCanvas {
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int TEXT=0xfff5f5f7;

    FigmaRecovery(Context context, boolean hasDisk, boolean running, Runnable back, Runnable status,
            Runnable live, Runnable install, Runnable delete) {
        super(context,true,1393);
        shape(720,402,1120,796,0xff27282c,0,44);
        text("Установка и восстановление",776,458,1008,60,48,TEXT,true,LEFT);
        text("Ubuntu 24.04.5 Desktop ARM64",776,550,1008,48,32,0xffa6a7af,false,LEFT);
        row(622,"ee180","Статус установки",TEXT,status);
        row(750,"439ef","Временная Live-сессия",running?0xffa6a7af:TEXT,running?null:live);
        row(878,"ce3d0",hasDisk?"Переустановить Ubuntu…":"Создать воркспейс",running?0xffa6a7af:TEXT,
                running?null:install);
        if(hasDisk)row(1006,"cc2fd","Удалить воркспейс…",running?0xffa6a7af:0xffff8b86,running?null:delete);
        button("Назад",720,1242,200,84,32,TEXT,0xff27282c,0,42,back);
    }

    private void row(int y,String icon,String label,int color,Runnable action) {
        asset("v12/"+icon+"-svg.png",812,y+43,42,42);
        text(label,878,y,880,128,32,color,false,LEFT);
        if(action!=null) {
            TextView target=button("",776,y,1008,128,32,TEXT,0,0,0,action);
            target.setContentDescription(label);
        }
    }
}
