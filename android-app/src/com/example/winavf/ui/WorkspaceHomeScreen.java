// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import java.util.function.IntConsumer;

public final class WorkspaceHomeScreen extends ScreenLayout {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int RIGHT=Gravity.END|Gravity.CENTER_VERTICAL;

    public void showPreparationProgress() {
        widget(new StepProgressView(getContext()), 112, 1370, 1312, 12);
    }

    public record WindowsDetails(String disk,String iso,Runnable openMedia) {}

    public WorkspaceHomeScreen(Context context,boolean windows,String title,String state,String resources,String display,
            String launchLabel,Runnable launch,Runnable back,Runnable menu,IntConsumer settings,WindowsDetails details) {
        super(context);
        backLink(112,60,back);
        text(title,112,208,2200,80,64,TEXT,true,LEFT);
        ImageView preview=asset(windows?"v12/windows-workspace.webp":"v12/ubuntu-workspace.webp",112,368,1312,820);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view,Outline outline) {
                outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),40*view.getWidth()/1312f);
            }
        });
        preview.setClipToOutline(true);
        icon(windows?"cd16d":"e5b29",1520,388,80);
        text(windows?"Windows ARM64":"Ubuntu 24.04",1636,368,810,62,38,TEXT,true,LEFT);
        icon("23d66",1636,449,10);
        text(state,1660,434,788,40,24,MUTED,false,LEFT);
        if(windows) {
            shape(1520,536,928,384,0xff1c1d20,0xff3a3c42,40);
            detailRow(536,"10430","Ресурсы",resources,null);
            detailRow(664,"d5ba9","Виртуальный диск",details.disk(),null);
            detailRow(792,"d5ba9","Установочный ISO",details.iso(),details.openMedia());
        } else {
            shape(1520,536,928,652,0xff1c1d20,0xff3a3c42,40);
            String[] labels={"Ресурсы","Экран","Ввод","Звук и сеть","Образы и диски"};
            String[] images={"10430","aea17","3b6cc","4f479","d5ba9"};
            for(int i=0;i<labels.length;i++) {
                int y=536+i*128;
                final int index=i;
                detailRow(y,images[i],labels[i],i==0?resources:i==1?display:null,()->settings.accept(index));
            }
        }
        shape(112,1248,400,96,BLUE,0,48);
        ImageView launchIcon=asset("v12/910d2-svg.png",112,1276,40,40);
        launchIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        var launchText=text(launchLabel,168,1248,304,96,34,TEXT,true,LEFT);
        launchText.setSingleLine(true);
        launchText.setEllipsize(TextUtils.TruncateAt.END);
        launchText.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            float labelWidth=Math.min(launchText.getWidth(),launchText.getPaint().measureText(launchText.getText().toString()));
            float groupWidth=left-launchIcon.getLeft()+labelWidth;
            float buttonWidth=launchText.getWidth()*400f/304;
            float offset=(buttonWidth-groupWidth)/2;
            launchIcon.setTranslationX(offset);
            launchText.setTranslationX(offset);
        });
        hit(launchLabel,112,1248,400,96,launch);
        shape(536,1252,88,88,0xff27282c,0,44);
        icon("8e936",560,1276,40);
        hit("Действия воркспейса",536,1252,88,88,menu);
    }

    private void detailRow(int y,String image,String title,String value,Runnable action) {
        icon(image,1560,y+43,42);
        text(title,1626,y,435,128,32,TEXT,false,LEFT);
        if(value!=null)text(value,2040,y,310,128,27,MUTED,false,RIGHT);
        if(action!=null) {
            icon("6c14a",2378,y+49,30);
            hit(title,1520,y,928,128,action);
        }
    }

    private void icon(String name,int x,int y,int size) {
        asset("v12/"+name+"-svg.png",x,y,size,size).setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void hit(String label,int x,int y,int w,int h,Runnable action) {
        View view=new View(getContext());
        view.setContentDescription(label);
        widget(view,x,y,w,h);
        click(view,action);
    }
}
