// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.content.Context;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** Renders the OS chooser and Ubuntu installation form. */
public final class WorkspaceCreationScreen extends ScreenLayout {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;

    public WorkspaceCreationScreen(Context context,boolean ubuntuExists,boolean windowsExists,Runnable back,Runnable ubuntu,Runnable windows) {
        super(context);
        header("Новый воркспейс",back);
        text("Операционная система",112,350,2300,64,34,TEXT,true,LEFT);
        card(112,456,1144,824,false,ubuntuExists?"Открыть воркспейс":"Ubuntu 24.04 LTS · ARM64");
        card(1304,456,1144,824,true,"Экспериментальный запуск Windows");
        icon(ubuntuExists?"bd3c2":"c7402",1102,1158,36);
        icon(windowsExists?"bd3c2":"c7402",2294,1158,36);
        hit(ubuntuExists?"Открыть воркспейс":"Создать Ubuntu",112,456,1144,824,ubuntu);
        hit("Экспериментальный запуск Windows",1304,456,1144,824,windows);
    }

    public WorkspaceCreationScreen(Context context,String name,String resources,boolean verified,boolean hasDisk,
            Runnable back,Runnable rename,Runnable image,Runnable advanced,Runnable start) {
        super(context);
        header("Новый Ubuntu",back);
        card(112,368,880,880,false,"Ubuntu 24.04 LTS · ARM64");
        option(368,"Название",name,rename,"fc13e");
        option(548,"Установочный ISO",verified?"ubuntu-24.04.5-desktop-arm64.iso":"Выбрать файл…",image,"3cf56");
        fixed(1056,728,"Среда рабочего стола","GNOME");
        fixed(1776,728,"Графическая сессия","X11");
        option(908,"Ресурсы",resources,advanced,"3cf56");
        String primary=hasDisk?"Открыть воркспейс":verified?"Создать воркспейс":"Выбрать ISO";
        shape(1936,1264,512,96,BLUE,0,48);
        icon(verified?"b73ab":"c7402",1980,1292,40);
        text(primary,2040,1264,388,96,32,TEXT,true,LEFT);
        hit(primary,1936,1264,512,96,hasDisk||verified?start:image);
    }

    private void header(String title,Runnable back) {
        backLink(112,60,back);
        text(title,112,208,2200,80,64,TEXT,true,LEFT);
    }

    private void card(int x,int y,int w,int h,boolean windows,String subtitle) {
        shape(x,y,w,h,0xff1c1d20,0xff3a3c42,40);
        int imageHeight=h-212;
        ImageView preview=asset(windows?"v12/windows-workspace.webp":"v12/ubuntu-workspace.webp",x+1,y+1,w-2,imageHeight);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view,Outline outline) {
                float radius=40*view.getWidth()/(float)(w-2);
                outline.setRoundRect(0,0,view.getWidth(),view.getHeight()+Math.round(radius),radius);
            }
        });
        preview.setClipToOutline(true);
        icon(windows?"efef3":"d6129",x+52,y+imageHeight+52,88);
        text(windows?"Windows":"Ubuntu",x+176,y+imageHeight+44,w-280,65,46,TEXT,true,LEFT);
        text(subtitle,x+176,y+imageHeight+110,w-250,48,28,MUTED,false,LEFT);
    }

    private void option(int y,String title,String value,Runnable action,String chevron) {
        shape(1056,y,1392,148,0xff1c1d20,0xff3a3c42,40);
        text(title,1092,y+22,1260,40,25,MUTED,false,LEFT);
        text(value,1092,y+64,1235,50,32,TEXT,false,LEFT);
        icon(chevron,2340,y+58,32);
        hit(title,1056,y,1392,148,action);
    }

    private void fixed(int x,int y,String label,String value) {
        shape(x,y,672,148,0xff1c1d20,0xff3a3c42,40);
        text(label,x+36,y+22,600,40,25,MUTED,false,LEFT);
        shape(x+36,y+62,600,66,0xff27282c,0,28);
        text(value,x+36,y+62,600,66,30,TEXT,true,Gravity.CENTER);
    }

    private void icon(String name,int x,int y,int size) {
        asset("v12/"+name+"-svg.png",x,y,size,size).setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void hit(String label,int x,int y,int w,int h,Runnable action) {
        View target=new View(getContext());
        target.setContentDescription(label);
        widget(target,x,y,w,h);
        click(target,action);
    }
}
