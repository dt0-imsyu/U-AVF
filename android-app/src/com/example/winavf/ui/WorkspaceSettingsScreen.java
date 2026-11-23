// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/** Shared chrome and controls for the five workspace preference screens. */
public final class WorkspaceSettingsScreen extends ScreenLayout {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int RIGHT=Gravity.END|Gravity.CENTER_VERTICAL;
    public static final String[] TABS={"Performance","Display","Input","Audio","Storage"};
    private static final String[] LABELS={"Ресурсы","Экран","Ввод","Звук и сеть","Хранилище"};
    private static final String[] ICONS={"10430","aea17","3b6cc","4f479","d5ba9"};
    private static final String[] SELECTED={"08548","408c2","bf032","112e7","a910f"};
    private final java.util.ArrayList<Runnable> toggleRefresh=new java.util.ArrayList<>();

    public WorkspaceSettingsScreen(Context context, boolean windows, int tab, int previousTab, Runnable back,
            IntConsumer select) {
        super(context);
        backLink(112,60,back);
        text(windows?"Windows ARM64":"Ubuntu · Работа",112,208,1800,80,64,TEXT,true,LEFT);
        if(windows) {
            panel(112,368,2336,200);
            text("Экспериментальный профиль Windows ARM64",160,398,2110,60,42,TEXT,true,LEFT);
            text("Параметры виртуальной машины закреплены в проверенном профиле запуска.",
                    160,472,2110,52,30,MUTED,false,LEFT);
            return;
        }
        panel(112,368,512,976).setClipToOutline(true);
        android.widget.ImageView preview=asset("v12/ubuntu-workspace.webp",112,368,512,288);
        preview.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        var selected=shape(136,688+112*tab,464,104,0xff34363b,0,20);
        if(previousTab>=0 && previousTab!=tab && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            selected.addOnLayoutChangeListener(new android.view.View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(android.view.View view,int left,int top,int right,int bottom,
                        int oldLeft,int oldTop,int oldRight,int oldBottom) {
                    view.removeOnLayoutChangeListener(this);
                    view.setTranslationY((previousTab-tab)*112*view.getHeight()/104f);
                    view.animate().translationY(0f).setDuration(230)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
                }
            });
        }
        for(int i=0;i<TABS.length;i++) {
            final int index=i;
            int y=688+112*i;
            icon(i==tab?SELECTED[i]:ICONS[i],164,y+31,42);
            text(LABELS[i],230,y,348,104,32,TEXT,i==tab,LEFT);
            var control=target(LABELS[i],136,y,464,104,()->select.accept(index));
            control.setSelected(i==tab);
            roundedPress(control,20,104);
        }
        text(LABELS[tab],688,368,1700,60,48,TEXT,true,LEFT);
    }

    private android.view.View panel(int x,int y,int w,int h) {
        return shape(x,y,w,h,0xff1c1d20,0xff3a3c42,40);
    }

    public void card(int y,int height) { panel(688,y,1760,height); }

    public void option(int y,String image,String title,String value,Runnable action) {
        optionTarget(y,image,title,value,action);
    }

    private TextView optionTarget(int y,String image,String title,String value,Runnable action) {
        row(y,image,title);
        text(value,1500,y,action==null?894:856,128,30,MUTED,false,RIGHT);
        if(action!=null) {
            icon("6c14a",2378,y+49,30);
            return target(title,712,y,1712,128,action);
        }
        return null;
    }

    public void choice(int y,String image,String title,String[] options,int selected,IntConsumer action) {
        TextView anchor=optionTarget(y,image,title,selected>=0?options[selected]:"",()->{});
        anchor.setOnClickListener(view->DropdownMenu.show(view,options,selected,action));
    }

    public void toggle(int y,String image,String title,BooleanSupplier checked,Runnable action) {
        row(y,image,title);
        ToggleView visual=new ToggleView(getContext(),checked.getAsBoolean());
        widget(visual,2316,y+36,92,56);
        TextView control=target(title,712,y,1712,128,()->{
            action.run();
            refreshToggles();
        });
        Runnable update=()->{
            boolean value=checked.getAsBoolean();
            control.setStateDescription(UiLanguage.text(value?"Включено":"Выключено"));
            visual.checked(value);
        };
        toggleRefresh.add(update);
        update.run();
    }

    public void refreshToggles() {
        for(Runnable update:toggleRefresh)update.run();
    }

    public void note(int y,String label) {
        text(label,736,y,1664,64,28,MUTED,false,LEFT);
    }

    public void segments(int y,String title,String[] values,int selected,IntPredicate select) {
        text(title,736,y,1600,55,32,TEXT,false,LEFT);
        shape(736,y+76,1664,88,0xff27282c,0,24);
        int width=1664/values.length;
        ViewIndicator slider=new ViewIndicator(shape(744,y+84,width-16,72,0xff34363b,0,20),width,selected);
        TextView[] choices=new TextView[values.length];
        int[] current={selected};
        for(int i=0;i<values.length;i++) {
            final int index=i;
            int x=736+width*i;
            TextView choice=button(values[i],x+8,y+84,width-16,72,34,
                    i==selected?TEXT:MUTED,0,0,20,()->{
                        if(current[0]==index || !select.test(index))return;
                        current[0]=index;
                        for(int j=0;j<choices.length;j++){
                            choices[j].setSelected(j==index);
                            choices[j].setTextColor(j==index?TEXT:MUTED);
                        }
                        slider.select(index);
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    });
            choice.setContentDescription(UiLanguage.text(title)+", "+UiLanguage.text(values[i]));
            choice.setSelected(i==selected);
            choices[i]=choice;
        }
    }

    public void displaySelector(int y,String[] values,int selected,IntPredicate select,Runnable refresh) {
        String title="Графический вывод";
        text(title,736,y,1600,55,32,TEXT,false,LEFT);
        shape(736,y+76,1664,88,0xff27282c,0,24);
        int width=1664/values.length;
        ViewIndicator slider=new ViewIndicator(shape(744,y+84,width-16,72,0xff34363b,0,20),width,selected);
        TextView[] choices=new TextView[values.length];
        for(int i=0;i<values.length;i++) {
            final int index=i;
            choices[i]=button(values[i],744+width*i,y+84,width-16,72,34,i==selected?TEXT:MUTED,0,0,20,()->{
                if(!select.test(index))return;
                for(int current=0;current<choices.length;current++) {
                    choices[current].setSelected(current==index);
                    choices[current].setTextColor(current==index?TEXT:MUTED);
                }
                slider.select(index);
                removeCallbacks(refresh);
                if(android.animation.ValueAnimator.areAnimatorsEnabled())postDelayed(refresh,190);
                else refresh.run();
            });
            choices[i].setContentDescription(UiLanguage.text(title)+", "+UiLanguage.text(values[i]));
            choices[i].setSelected(i==selected);
        }
    }

    private static final class ViewIndicator {
        private final android.view.View track;
        private final int width;
        private int index;
        ViewIndicator(android.view.View track,int width,int index) {
            this.track=track;this.width=width;this.index=index;
            track.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->view.setTranslationX(offset()));
        }
        private float offset() {return index*width*track.getWidth()/(float)(width-16);}
        void select(int next) {
            index=next;
            track.animate().cancel();
            if(android.animation.ValueAnimator.areAnimatorsEnabled())track.animate().translationX(offset())
                    .setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            else track.setTranslationX(offset());
        }
    }

    public void storage(String disk,Runnable expand,Runnable recover) {
        card(476,320);
        icon("07d5c",736,542,62);
        text("Ubuntu Root",828,518,1400,52,36,TEXT,true,LEFT);
        text(disk,828,570,1400,42,26,MUTED,false,LEFT);
        if(expand!=null) {
            icon("5ab2e",792,702,30);
            text("Расширить…",844,670,270,96,34,TEXT,true,LEFT);
            target("Расширить диск",760,670,320,96,expand);
        }
        button("Восстановление",1112,670,402,96,34,TEXT,0,0,0,recover);
    }

    private void row(int y,String image,String title) {
        icon(image,728,y+43,42);
        text(title,794,y,720,128,32,TEXT,false,LEFT);
    }

    private void icon(String name,int x,int y,int size) {
        asset("v12/"+name+"-svg.png",x,y,size,size);
    }

    private TextView target(String title,int x,int y,int w,int h,Runnable action) {
        TextView target=button("",x,y,w,h,30,TEXT,0,0,0,action);
        target.setContentDescription(UiLanguage.text(title));
        return target;
    }
}
