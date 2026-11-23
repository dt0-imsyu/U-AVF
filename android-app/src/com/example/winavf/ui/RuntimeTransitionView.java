// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf.ui;

import com.example.winavf.runtime.RuntimeTransition;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Boot and power transitions driven by observed runtime events. */
public final class RuntimeTransitionView extends FrameLayout {
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
    private final Runnable forceStop,stopWindowsLaunch,recoverWindows,returnHome;
    private ScreenLayout canvas;
    private TextView heading,detail,forceAction;
    private final TextView[] rowNames=new TextView[4], rowStates=new TextView[4];
    private final View[] done=new View[4], pending=new View[4];
    private StepProgressView progress;
    private View bootCard;
    private TextView waitAction,cancelAction,launchStop,waitHint;
    private View powerIcon;
    private PopupWindow stopConfirmation;
    private boolean bootActionsVisible;
    private String currentLayout="";
    private boolean forceAllowed=true;
    private boolean hiding;

    public RuntimeTransitionView(Context context,RuntimeTransition model,BooleanSupplier windows,
                          Runnable forceStop,Runnable stopWindowsLaunch,Runnable recoverWindows,Runnable returnHome) {
        super(context);
        this.model=model;this.windows=windows;this.forceStop=forceStop;this.returnHome=returnHome;
        this.stopWindowsLaunch=stopWindowsLaunch;this.recoverWindows=recoverWindows;
        setBackgroundColor(CANVAS);setClickable(true);setFocusable(true);
        setVisibility(GONE);
    }

    public void refresh() {
        if(!model.visible()){hide();return;}
        boolean opening=getVisibility()!=VISIBLE || hiding;
        if(hiding){animate().cancel();hiding=false;}
        setAlpha(1f);
        setVisibility(VISIBLE);bringToFront();
        long now=SystemClock.elapsedRealtime();
        boolean win=windows.getAsBoolean();
        String os=win?"Windows":"Ubuntu";
        RuntimeTransition.Mode mode=model.mode(), operation=model.operation();
        boolean waiting=model.waiting(now,win);
        boolean reconnect=operation==RuntimeTransition.Mode.DISPLAY_RECONNECT;
        String layout=mode==RuntimeTransition.Mode.ERROR?"error":
            reconnect?"reconnect":
            operation==RuntimeTransition.Mode.SHUTDOWN?"shutdown":
            operation==RuntimeTransition.Mode.REBOOT||win?"boot":
            waiting?"waiting":operation==RuntimeTransition.Mode.BOOT?"boot":"power";
        String key=layout+":"+operation+":"+os+":"+ProductAppearance.theme(getContext());
        if(!key.equals(currentLayout)){currentLayout=key;build(layout,win);opening=true;}
        if(opening) PageMotion.showPanel(canvas);
        Map<String,String> events=model.events();
        if("shutdown".equals(layout)) {
            update(heading,UiLanguage.format("Завершаем %s",os));
            boolean stopped=events.containsKey("VM stopped");
            progress.complete(stopped);
            String stage=stopped?UiLanguage.text("VM остановлена"):waiting
                    ?UiLanguage.format("%s завершает работу дольше обычного",os):UiLanguage.text("Остановка гостя");
            update(detail,stage);
            return;
        }
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
            if(!reconnect || win) {
                waitHint.setText(win && model.windowsPostInstall() && !complete
                        ? UiLanguage.text("Первые запуски после установки могут занять 5–10 минут.") : "");
                int stopVisibility=!complete && forceAllowed ? VISIBLE : GONE;
                if(stopVisibility==GONE) closeStopConfirmation();
                launchStop.setVisibility(stopVisibility);
                powerIcon.setVisibility(stopVisibility);
                showBootActions(waiting && forceAllowed);
            }
            return;
        }
        if("error".equals(layout)) {
            update(heading,UiLanguage.format(reconnect?"Не удалось восстановить изображение %s":
                operation==RuntimeTransition.Mode.SHUTDOWN?"Не удалось завершить %s":
                operation==RuntimeTransition.Mode.REBOOT?"Не удалось перезапустить %s":"Не удалось запустить %s",os));
            update(detail,model.detail());
            forceAction.setVisibility(forceAllowed?VISIBLE:GONE);
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
        closeStopConfirmation();
        removeAllViews();
        canvas=new ScreenLayout(getContext());
        setBackground(ScreenLayout.wallpaper(getContext()));
        addView(canvas,new LayoutParams(-1,-1));
        boolean shuttingDown="shutdown".equals(layout);
        if("boot".equals(layout)||"reconnect".equals(layout)||shuttingDown) {
            bootCard=canvas.shape(800,580,960,440,SURFACE,LINE,40);
            canvas.asset(win?"v12/efef3-svg.png":"v12/d6129-svg.png",1232,628,96,96)
                .setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            heading=canvas.text("",848,748,864,58,46,PRIMARY,true,Gravity.CENTER);
            progress=new StepProgressView(getContext());
            canvas.widget(progress,848,846,864,16);
            detail=canvas.text("",848,894,864,42,26,SECONDARY,false,Gravity.CENTER);
            if("reconnect".equals(layout))
                canvas.outlineAction("На главную",160,56,360,88,32,returnHome);
            if((!"reconnect".equals(layout) || win) && !shuttingDown) {
                bootActionsVisible=false;
                waitHint=canvas.text("",848,944,864,56,25,SECONDARY,false,Gravity.CENTER);
                launchStop=canvas.button("",1644,608,84,82,42,PRIMARY,0xff303238,LINE,26,
                        ()->confirmWindowsStop(launchStop));
                launchStop.setContentDescription(UiLanguage.text("Остановить запуск"));
                powerIcon=new PowerIcon(getContext());
                canvas.widget(powerIcon,1666,629,40,40);
                powerIcon.setOnClickListener(view -> confirmWindowsStop(launchStop));
                waitAction=canvas.button("Ждать",848,1012,420,82,30,PRIMARY,ACCENT,0,28,
                    ()->{model.keepWaiting(SystemClock.elapsedRealtime());refresh();});
                cancelAction=canvas.button("Остановить",1292,1012,420,82,30,PRIMARY,SURFACE,LINE,28,
                        ()->confirmWindowsStop(cancelAction));
                waitAction.setVisibility(INVISIBLE);
                cancelAction.setVisibility(INVISIBLE);
            }
            return;
        }
        boolean isWaiting="waiting".equals(layout), error="error".equals(layout);
        if(error) {
            bootCard=canvas.shape(570,437,1420,700,SURFACE,LINE,40);
            canvas.shape(630,495,84,84,0xff492d30,0,42);
            canvas.text("!",630,495,84,84,48,0xffffada7,true,Gravity.CENTER);
            heading=canvas.text("",745,493,1175,93,47,PRIMARY,true,START);
            canvas.shape(630,633,1300,198,0xff242529,LINE,24);
            detail=canvas.text("",664,654,1232,155,31,SECONDARY,false,START);
            canvas.button(win?"Открыть воркспейс":"На главную",630,869,638,88,31,
                    0xffffffff,ACCENT,0,28,win?recoverWindows:returnHome);
            canvas.button("Скопировать ошибку",1292,869,638,88,31,PRIMARY,0xff303238,LINE,28,()->{
                var clipboard=getContext().getSystemService(android.content.ClipboardManager.class);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("U-AVF", model.detail()));
            });
            forceAction=canvas.button("Принудительно остановить…",630,988,638,82,29,
                    0xffffada7,0xff492d30,0,26,()->{
                        if(forceAllowed) {
                            if(win) confirmWindowsStop(forceAction);
                            else forceStop.run();
                        }
                    });
            if(win) canvas.button("На главную",1292,988,638,82,29,SECONDARY,0,0,26,returnHome);
            return;
        }
        heading=canvas.text("",160,224,2240,88,64,PRIMARY,true,START);
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

    private void confirmWindowsStop(View anchor) {
        if(!forceAllowed || (stopConfirmation!=null && stopConfirmation.isShowing())) return;
        int generation=model.generation();
        boolean error=model.mode()==RuntimeTransition.Mode.ERROR;
        stopConfirmation=ProductDialog.confirmFrom(anchor,bootCard,
                error?"Остановить Windows?":model.operation()==RuntimeTransition.Mode.REBOOT
                        ?"Остановить перезапуск?":"Вы хотите остановить запуск?",()->{
            if(forceAllowed && model.generation()==generation && model.mode()!=RuntimeTransition.Mode.COMPLETE
                    && model.visible()) {
                stopWindowsLaunch.run();
            }
        });
    }

    private void closeStopConfirmation() {
        if(stopConfirmation==null) return;
        stopConfirmation.dismiss();
        stopConfirmation=null;
    }

    public void hide() {
        closeStopConfirmation();
        if(getVisibility()!=VISIBLE || hiding) return;
        hiding=true;
        PageMotion.fadeOut(this,()->{
            setVisibility(GONE);
            setAlpha(1f);
            hiding=false;
        });
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        return !hiding && super.dispatchTouchEvent(event);
    }

    private static final class PowerIcon extends View {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        PowerIcon(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setColor(PRIMARY);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeWidth(2f);
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth()/24f,getHeight()/24f);
            canvas.drawArc(4,5,20,21,-45,270,false,paint);
            canvas.drawLine(12,2.5f,12,10.5f,paint);
            canvas.restore();
        }
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

    public void allowForceStop(boolean allowed) {forceAllowed=allowed;}
}
