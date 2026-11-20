// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;

final class FigmaAvfPermission extends FigmaCanvas {
    private static final int TEXT=0xfff5f5f7, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;

    FigmaAvfPermission(Context context,String manage,String custom,boolean granted,Runnable back,
            Runnable check,Runnable copy) {
        super(context,true,1393);
        asset("v12/8b7c5-svg.png",168,83,26,34);
        button("Назад",200,56,260,88,34,BLUE,0,0,0,back).setGravity(LEFT);
        text("Доступ к виртуализации",160,228,2240,76,64,TEXT,true,LEFT);
        shape(160,468,2240,488,0xff1c1d20,0,32);
        text(granted?"Доступ предоставлен":"Выполните обе команды",208,516,2144,52,40,TEXT,true,LEFT);
        text(manage,208,604,2144,64,28,TEXT,false,LEFT).setTypeface(Typeface.MONOSPACE);
        text(custom,208,680,2144,64,28,TEXT,false,LEFT).setTypeface(Typeface.MONOSPACE);
        action(160,422,"Проверить доступ","63b38",BLUE,check);
        action(614,500,"Скопировать команды","d8f93",0,copy);
    }

    private void action(int x,int w,String label,String icon,int fill,Runnable action) {
        if(fill!=0)shape(x,1044,w,96,fill,0,48);
        asset("v12/"+icon+"-svg.png",x+40,1072,40,40);
        text(label,x+96,1044,w-108,96,32,TEXT,true,LEFT);
        View target=new View(getContext());
        target.setContentDescription(label);
        widget(target,x,1044,w,96);
        click(target,action);
    }
}
