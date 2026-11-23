// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import com.example.winavf.diagnostics.CompatibilityChecks;
import com.example.winavf.diagnostics.CompatibilityStatus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Local AVF check and results of the disposable guest. */
public final class CompatibilityScreen extends ScreenLayout {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, BLUE=0xff0a84ff;
    private static final int LEFT=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int RIGHT=Gravity.END|Gravity.CENTER_VERTICAL;
    private final TextView[] values=new TextView[CompatibilityChecks.LABELS.size()];
    private final CheckMark[] marks=new CheckMark[CompatibilityChecks.LABELS.size()];

    public CompatibilityScreen(Context context,boolean running,CompatibilityStatus status,boolean hasPermissions,
            boolean shareResults,java.util.function.Consumer<Boolean> setSharing,
            Runnable repeat,Runnable proceed,Runnable back,Runnable skip,Runnable permission,Runnable diagnostics) {
        super(context);
        backLink(160,56,back);
        String title=switch(status) {
            case COMPATIBLE -> "Совместимо";
            case NEEDS_WORK -> "Требуется доработка";
            case INCOMPATIBLE -> "Несовместимо";
            case INSUFFICIENT_DATA -> "Недостаточно данных";
        };
        text(running?"Проверка устройства":title,
                160,228,2240,76,64,TEXT,true,LEFT);
        text("Базовая совместимость AVF",320,408,1920,48,28,MUTED,false,LEFT);
        shape(320,476,1920,576,0xff1c1d20,0,32);
        for(int i=0;i<CompatibilityChecks.LABELS.size();i++) {
            int top=476+i*64;
            marks[i]=new CheckMark(context);
            widget(marks[i],360,top+12,40,40);
            text(CompatibilityChecks.LABELS.get(i),424,top,1136,64,30,TEXT,false,LEFT);
            values[i]=text("",1600,top,!running&&!hasPermissions&&i==2?540:600,64,28,MUTED,false,RIGHT);
        }
        text("Ubuntu, Windows и Mavk проверяются отдельно.",320,1104,1000,52,28,MUTED,false,LEFT);
        if(!running) {
            String label="Отправлять результаты";
            text(label,1440,1092,680,80,28,TEXT,false,RIGHT);
            ToggleView toggle=new ToggleView(context,shareResults);
            widget(toggle,2150,1104,90,56);
            View target=new View(context);
            target.setContentDescription(label);
            target.setSelected(shareResults);
            target.setStateDescription(UiLanguage.text(shareResults?"Включено":"Выключено"));
            widget(target,1440,1092,800,80);
            click(target,()->{
                boolean enabled=!target.isSelected();
                setSharing.accept(enabled);
                target.setSelected(enabled);
                target.setStateDescription(UiLanguage.text(enabled?"Включено":"Выключено"));
                toggle.checked(enabled);
            });
        }
        if(running)widget(new StepProgressView(context),320,1074,1920,14);
        else if(status==CompatibilityStatus.COMPATIBLE) {
            action("На главную","c4173",320,300,proceed);
            button("Повторить",660,1208,320,96,34,TEXT,0,0,0,repeat);
        }
        else {
            action("Повторить","955ba",320,300,repeat);
            button("Пропустить проверку…",660,1208,480,96,34,TEXT,0,0,0,skip);
            if(!hasPermissions) {
                asset("v12/b9ac3-svg.png",2164,620,32,32);
                View target=new View(context);
                target.setContentDescription("Доступ к виртуализации");
                widget(target,320,604,1920,64);
                click(target,permission);
            }
        }
        if(!running)outlineAction("Диагностика",1840,1208,400,96,32,diagnostics);
    }

    public void results(boolean running,int completed,String[] results) {
        for(int i=0;i<values.length;i++) {
            String value=results[i];
            boolean passed=value.startsWith("Passed"),failed=value.startsWith("Failed");
            marks[i].show(passed?1:failed?-1:0);
            String label=passed?"Пройдено":failed?
                    (i==2?"Нужен доступ":"Ошибка"):running&&i==completed?"Проверяем…":
                    running?"Ожидание":"Не проверена";
            String localized=UiLanguage.text(label);
            if(!android.text.TextUtils.equals(values[i].getText(),localized))values[i].setText(localized);
        }
    }

    private static final class CheckMark extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int state;

        CheckMark(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void show(int value) {
            if(state!=value) {state=value;invalidate();}
        }

        @Override protected void onDraw(Canvas canvas) {
            float size=Math.min(getWidth(),getHeight());
            float x=getWidth()/2f,y=getHeight()/2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(state==1?0xff203a34:state==-1?0xff482e37:0xff2b2d33);
            canvas.drawCircle(x,y,size*.47f,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size*.062f);
            paint.setColor(state==1?0xff82dfba:state==-1?0xfff9a9b8:0xff9297a3);
            if(state==1) {
                canvas.drawLine(x-size*.19f,y,x-size*.05f,y+size*.13f,paint);
                canvas.drawLine(x-size*.05f,y+size*.13f,x+size*.21f,y-size*.16f,paint);
            } else if(state==-1) {
                float d=size*.145f;
                canvas.drawLine(x-d,y-d,x+d,y+d,paint);
                canvas.drawLine(x-d,y+d,x+d,y-d,paint);
            } else {
                canvas.drawLine(x-size*.13f,y,x+size*.13f,y,paint);
            }
        }
    }

    private void action(String label,String glyph,int x,int width,Runnable action) {
        shape(x,1208,width,96,BLUE,0,48);
        asset("v12/"+glyph+"-svg.png",x+36,1236,40,40);
        text(label,x+92,1208,width-104,96,32,TEXT,true,LEFT);
        View target=new View(getContext());
        target.setContentDescription(label);
        widget(target,x,1208,width,96);
        click(target,action);
    }
}
