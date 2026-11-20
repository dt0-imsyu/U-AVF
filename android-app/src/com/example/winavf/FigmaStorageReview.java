// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;

final class FigmaStorageReview extends FigmaCanvas {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int RIGHT=Gravity.END|Gravity.CENTER_VERTICAL;

    FigmaStorageReview(Context context,boolean review,String disk,String cpu,int ram,boolean verified,String display,
            String primary,Runnable next,Runnable back,Runnable settings,String rootSize,Runnable storageSettings) {
        super(context,true,1393);
        asset("v12/4de18-svg.png",112,80,40,40);
        button("Назад",168,60,250,80,32,BLUE,0,0,0,back).setGravity(LEFT);
        text(review?"Проверка параметров":"Диск и производительность",112,208,2300,80,64,TEXT,true,LEFT);
        if(review) {
            card(112,368,2336,660);
            row(368,"Операционная система","Ubuntu 24.04 LTS");
            row(496,"Установочный ISO",verified?"Образ доступен":"Не выбран");
            row(624,"Диск",disk);
            row(752,"Процессор",cpu);
            row(880,"Разрешение",display);
        } else {
            card(112,368,1144,540);
            text("Хранилище",160,404,1048,64,44,TEXT,true,LEFT);
            text("Внутренняя память / U-AVF",160,476,1048,56,32,MUTED,false,LEFT);
            text(disk,160,580,1048,60,38,TEXT,true,LEFT);
            text(rootSize,160,646,1048,60,30,MUTED,false,LEFT);
            if(storageSettings!=null)button("Выбрать размер",160,768,460,96,32,TEXT,0xff34363b,0,48,storageSettings);
            card(1304,368,1144,540);
            text("Ресурсы",1352,404,1048,64,44,TEXT,true,LEFT);
            text(UiLanguage.text(cpu)+" · "+UiLanguage.format("%d ГБ",ram),1352,540,1048,64,36,TEXT,true,LEFT);
            text(display,1352,620,1048,64,30,MUTED,false,LEFT);
            button("Параметры",1352,768,460,96,32,TEXT,0xff34363b,0,48,settings);
        }
        button(review?primary:"Проверить параметры",1870,1160,578,96,34,TEXT,BLUE,0,48,next);
    }

    private void card(int x,int y,int w,int h) {
        shape(x,y,w,h,0xff1c1d20,0xff3a3c42,40);
    }

    private void row(int y,String label,String value) {
        text(label,160,y,1140,128,34,TEXT,false,LEFT);
        text(value,1400,y,1000,128,32,MUTED,false,RIGHT);
    }
}
