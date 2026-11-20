// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf;

import com.example.winavf.runtime.RuntimeTransition;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Figma boot, waiting and power surfaces driven by observed runtime events. */
final class RuntimeTransitionView extends FrameLayout {
    private static final int CANVAS=0xff101113, SURFACE=0xff1c1d20, LINE=0xff3a3c42;
    private static final int PRIMARY=0xfff5f5f7, SECONDARY=0xffa6a7af, ACCENT=0xff0a84ff;
    private static final int START=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int END=Gravity.END|Gravity.CENTER_VERTICAL;
    private static final String[] BOOT_EVENTS={"Start requested","Virtual machine created","UEFI started",
        "Linux kernel","systemd userspace","GDM started","Display connected","Desktop presented"};
    private static final String[] BOOT_LABELS={"Запрос отправлен","Создание виртуальной машины","Запуск UEFI",
        "Загрузка ядра","Запуск служб","Подключение дисплея…","Ожидание первого кадра…","Рабочий стол готов"};
    private final RuntimeTransition model;
    private final BooleanSupplier windows;
    private final Runnable forceStop,returnHome;
    private FigmaCanvas canvas;
    private TextView heading,detail,forceAction;
    private final TextView[] rowNames=new TextView[4], rowStates=new TextView[4];
    private final View[] done=new View[4], pending=new View[4];
    private FigmaProgress progress;
    private View bootCard;
    private TextView waitAction,cancelAction;
    private boolean bootActionsVisible;
    private String currentLayout="";
    private boolean forceAllowed=true;

    RuntimeTransitionView(Context context,RuntimeTransition model,BooleanSupplier windows,
                          Runnable forceStop,Runnable returnHome) {
        super(context);
        this.model=model;this.windows=windows;this.forceStop=forceStop;this.returnHome=returnHome;
        setBackgroundColor(CANVAS);setClickable(true);setFocusable(true);
        setVisibility(GONE);
    }

    void refresh() {
        if(!model.visible()){setVisibility(GONE);return;}
        setVisibility(VISIBLE);bringToFront();
        long now=SystemClock.elapsedRealtime();
        boolean win=windows.getAsBoolean();
        String os=win?"Windows":"Ubuntu";
        RuntimeTransition.Mode mode=model.mode(), operation=model.operation();
        boolean waiting=model.waiting(now);
        boolean reconnect=operation==RuntimeTransition.Mode.DISPLAY_RECONNECT;
        String layout=mode==RuntimeTransition.Mode.ERROR?"error":
            reconnect?"reconnect":
            win && operation!=RuntimeTransition.Mode.SHUTDOWN?"boot":
            waiting?"waiting":operation==RuntimeTransition.Mode.BOOT?"boot":"power";
        String key=layout+":"+os;
        if(!key.equals(currentLayout)){currentLayout=key;build(layout,win);}
        Map<String,String> events=model.events();
        if("boot".equals(layout) || "reconnect".equals(layout)) {
            boolean complete=mode==RuntimeTransition.Mode.COMPLETE;
            boolean reboot=operation==RuntimeTransition.Mode.REBOOT;
            boolean reconnecting=reconnect || (win && events.containsKey("Windows display reconnecting"));
            boolean install=win && events.containsKey("Windows installation");
            boolean setup=install || (win && events.containsKey("Windows setup"));
            String ready=setup?"Экран установки подключён":win?"Экран Windows подключён":"Рабочий стол готов";
            String title=complete?ready:reboot?"Перезапускаем %s":reconnecting?"Восстанавливаем изображение %s":
                    install?"Установка Windows":setup?"Запускаем WinPE":"Запускаем %s";
            update(heading,title.contains("%s")?UiLanguage.format(title,os):UiLanguage.text(title));
            int last=-1;
            for(int i=0;i<BOOT_EVENTS.length;i++)if(events.containsKey(BOOT_EVENTS[i]))last=i;
            progress.complete(complete);
            String stage=complete?ready:reconnect
                    ?waiting?"Восстановление изображения занимает больше времени":"Ожидание первого кадра…"
                    :waiting && win
                    ?reboot?"%s перезапускается дольше обычного":reconnecting?"Восстановление изображения занимает больше времени":
                     install?"Подготовка установки занимает больше времени":"%s запускается дольше обычного"
                    :win && events.containsKey("Windows kernel") && !events.containsKey("Display connected")
                    ?"Ожидание изображения Windows…":last<0?"Подготовка запуска…":BOOT_LABELS[last];
            update(detail,stage.contains("%s")?UiLanguage.format(stage,os):UiLanguage.text(stage));
            if(win) showBootActions(waiting);
            return;
        }
        if("error".equals(layout)) {
            update(heading,UiLanguage.format(reconnect?"Не удалось восстановить изображение %s":
                operation==RuntimeTransition.Mode.SHUTDOWN?"Не удалось завершить %s":
                operation==RuntimeTransition.Mode.REBOOT?"Не удалось перезапустить %s":"Не удалось запустить %s",os));
            update(detail,model.detail());
            forceAction.setEnabled(forceAllowed);
            return;
        }
        if("waiting".equals(layout)) {
            update(heading,UiLanguage.format(operation==RuntimeTransition.Mode.SHUTDOWN?"%s завершает работу дольше обычного":
                operation==RuntimeTransition.Mode.REBOOT?"%s перезапускается дольше обычного":"%s запускается дольше обычного",os));
            if(operation==RuntimeTransition.Mode.SHUTDOWN) {
                setRow(0,"Запрос завершения",events.containsKey("Shutdown requested"));
                setRow(1,"Синхронизация файлов",events.containsKey("Filesystem sync"));
                setRow(2,"VM остановлена",events.containsKey("VM stopped"));
            } else {
                boolean winpe=win&&events.containsKey("Windows kernel");
                setRow(0,winpe?"Ядро Windows":win?"UEFI":"Ядро Linux",
                        winpe||events.containsKey(win?"UEFI started":"Linux kernel"));
                setRow(1,win?"Виртуальная машина":"systemd",events.containsKey(win?"Virtual machine created":"systemd userspace"));
                setRow(2,"Дисплей",events.containsKey("Display connected"));
            }
            forceAction.setEnabled(forceAllowed);
            return;
        }
        boolean restart=operation==RuntimeTransition.Mode.REBOOT;
        update(heading,UiLanguage.format(restart?"Перезапускаем %s":"Завершаем %s",os));
        setRow(0,restart?"Запрос перезапуска":"Запрос завершения",events.containsKey(restart?"Restart requested":"Shutdown requested"));
        setRow(1,restart?"Остановка гостя":"Гостевые службы",events.containsKey(restart?"VM stopped":"systemd stopping services"));
        setRow(2,restart?(win?"UEFI":"Загрузка ядра"):"Синхронизация файлов",events.containsKey(restart?(win?"UEFI started":"Linux kernel"):"Filesystem sync"));
        setRow(3,restart?"Восстановление дисплея":"VM остановлена",events.containsKey(restart?"Display connected":"VM stopped"));
        long elapsed=model.elapsed(now);
        update(detail,UiLanguage.format("Прошло %02d:%02d",elapsed/60,elapsed%60));
    }

    private void build(String layout,boolean win) {
        removeAllViews();
        canvas=new FigmaCanvas(getContext(),true,1393);
        addView(canvas,new LayoutParams(-1,-1));
        if("boot".equals(layout)||"reconnect".equals(layout)) {
            canvas.asset(win?"v12/windows-workspace.webp":"v12/ubuntu-workspace.webp",0,0,2560,1600)
                .setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            canvas.shape(0,0,2560,1600,0xb8101113,0,0);
            bootCard=canvas.shape(800,580,960,440,SURFACE,LINE,40);
            canvas.asset(win?"v12/efef3-svg.png":"v12/d6129-svg.png",1232,628,96,96)
                .setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            heading=canvas.text("",848,748,864,58,46,PRIMARY,true,Gravity.CENTER);
            progress=new FigmaProgress(getContext());
            canvas.widget(progress,848,846,864,16);
            detail=canvas.text("",848,894,864,42,26,SECONDARY,false,Gravity.CENTER);
            if("reconnect".equals(layout))
                canvas.button("На главную",160,56,360,88,32,ACCENT,0,0,0,returnHome);
            if(win) {
                bootActionsVisible=false;
                waitAction=canvas.button("Ждать",848,990,420,82,30,PRIMARY,ACCENT,0,28,
                    ()->{model.keepWaiting(SystemClock.elapsedRealtime());refresh();});
                cancelAction=canvas.button("Отменить",1292,990,420,82,30,PRIMARY,SURFACE,LINE,28,forceStop);
                waitAction.setVisibility(INVISIBLE);
                cancelAction.setVisibility(INVISIBLE);
            }
            return;
        }
        canvas.shape(0,0,2560,1600,CANVAS,0,0);
        boolean isWaiting="waiting".equals(layout), error="error".equals(layout);
        if(error) {
            canvas.button("На главную",160,56,360,88,32,ACCENT,0,0,0,returnHome);
        }
        heading=canvas.text("",160,224,2240,88,64,PRIMARY,true,START);
        if(error) {
            canvas.shape(320,520,1920,360,SURFACE,LINE,40);
            detail=canvas.text("",360,550,1840,290,32,PRIMARY,false,START);
            forceAction=canvas.button("Принудительно остановить…",810,1052,515,96,30,PRIMARY,0,0,0,forceStop);
            return;
        }
        if(isWaiting)canvas.text("Состояние",320,460,1700,40,26,SECONDARY,false,START);
        canvas.shape(isWaiting?320:400,isWaiting?520:488,isWaiting?1920:1760,isWaiting?360:704,SURFACE,0,32);
        int count=isWaiting?3:4;
        for(int i=0;i<count;i++) {
            int top=(isWaiting?520:544)+i*(isWaiting?120:104);
            if(!isWaiting) {
                done[i]=canvas.asset("v12/d3abb-svg.png",460,top+24,40,40);
                pending[i]=canvas.asset("v12/39050-svg.png",460,top+24,40,40);
            }
            rowNames[i]=canvas.text("",isWaiting?360:532,top,1300,96,32,PRIMARY,false,START);
            rowStates[i]=canvas.text("",isWaiting?1840:1700,top,360,96,30,SECONDARY,false,END);
        }
        if(isWaiting) {
            canvas.button("Продолжить ожидание",320,1052,426,96,32,PRIMARY,ACCENT,0,48,
                ()->{model.keepWaiting(SystemClock.elapsedRealtime());refresh();});
            forceAction=canvas.button("Принудительно остановить…",780,1052,520,96,32,PRIMARY,0,0,0,forceStop);
        } else {
            detail=canvas.text("",456,1036,1648,52,28,SECONDARY,false,START);
        }
    }

    private void showBootActions(boolean visible) {
        cancelAction.setEnabled(forceAllowed);
        if(visible==bootActionsVisible)return;
        bootActionsVisible=visible;
        bootCard.setPivotY(0);
        bootCard.animate().scaleY(visible?1.23f:1f).setDuration(200).start();
        animateBootAction(waitAction,visible);
        animateBootAction(cancelAction,visible);
    }

    private void animateBootAction(View action,boolean visible) {
        action.animate().cancel();
        if(visible) {
            action.setAlpha(0);
            action.setTranslationY(-12*getResources().getDisplayMetrics().density);
            action.setVisibility(VISIBLE);
            action.animate().alpha(1).translationY(0).setDuration(200).start();
        } else {
            action.setVisibility(INVISIBLE);
        }
    }

    private void setRow(int index,String name,boolean seen) {
        update(rowNames[index],UiLanguage.text(name));
        update(rowStates[index],UiLanguage.text(seen?"Подтверждено":"Ожидание"));
        if(currentLayout.startsWith("power:")) {
            done[index].setVisibility(seen?VISIBLE:INVISIBLE);
            pending[index].setVisibility(seen?INVISIBLE:VISIBLE);
        }
    }

    private static void update(TextView view,String text) {
        if(!android.text.TextUtils.equals(view.getText(),text))view.setText(text);
    }

    void allowForceStop(boolean allowed) {forceAllowed=allowed;}
}
