// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

/** Shared product dialogs. Dismissal never performs a destructive action. */
final class ProductDialog {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, ACCENT=0xff0a84ff;
    private static final int CARD=0xff27282c, SECONDARY=0xff34363b, OUTLINE=0xff3a3c42;
    static Dialog show(Context context,String title,String message,String confirm,boolean destructive,Runnable action) {
        Panel panel=new Panel(context,title);panel.description(message);
        LinearLayout actions=panel.actions();
        panel.action(actions,"Отмена",false,false,panel.dialog::dismiss);
        panel.action(actions,confirm,true,destructive,()->{panel.dialog.dismiss();action.run();});
        panel.open();
        return panel.dialog;
    }
    static void notice(Context context,String title,String message) {
        Panel panel=new Panel(context,title);panel.description(message);
        panel.action(panel.actions(),"Понятно",true,false,panel.dialog::dismiss);panel.open();
    }
    static void menu(Context context,String title,String[] options,IntConsumer action) {
        Panel panel=new Panel(context,title);
        for(int i=0;i<options.length;i++) {
            final int index=i;
            Button row=panel.button(options[i],CARD,TEXT);
            row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.setElevation(0);
            row.setStateListAnimator(null);
            panel.content.addView(row,new LinearLayout.LayoutParams(-1,-2));
            row.setOnClickListener(view->{panel.dialog.dismiss();action.accept(index);});
        }
        panel.action(panel.actions(),"Закрыть",false,false,panel.dialog::dismiss);
        panel.open();
    }

    static void rename(Context context,String current,Consumer<String> save) {
        Panel panel=new Panel(context,"Название воркспейса");
        EditText input=new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(42)});
        input.setContentDescription(UiLanguage.text("Название воркспейса"));
        panel.content.addView(input,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=panel.actions();
        panel.action(actions,"Отмена",false,false,panel.dialog::dismiss);
        panel.action(actions,"Сохранить",true,false,()->{
            String next=input.getText().toString().strip();
            if(!next.isEmpty())save.accept(next);
            panel.dialog.dismiss();
        });
        panel.open();
    }
    static void choose(Context context,String title,String message,String[] options,int selected,IntConsumer action) {
        Panel panel=new Panel(context,title);panel.description(message);
        for(int i=0;i<options.length;i++) {
            final int index=i;
            Button row=panel.button(UiLanguage.text(options[i])+(i==selected?"  ✓":""),i==selected?SECONDARY:CARD,TEXT);
            row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.setBackground(panel.background(i==selected?SECONDARY:CARD,i==selected?ACCENT:OUTLINE,14));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=panel.dp(10);
            panel.content.addView(row,size);row.setOnClickListener(v->{panel.dialog.dismiss();action.accept(index);});
        }
        panel.action(panel.actions(),"Отмена",false,false,panel.dialog::dismiss);panel.open();
    }
    private static final class Panel {
        final Context context;final Dialog dialog;final LinearLayout card,content;final Typeface font;
        Panel(Context context,String title) {
            this.context=context;font=Typeface.create("sans-serif",Typeface.NORMAL);
            dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(24),dp(24),dp(24),dp(24));
            card.setBackground(background(CARD,OUTLINE,24));card.setElevation(dp(16));
            TextView heading=new TextView(context);heading.setText(UiLanguage.text(title));heading.setTextSize(23);
            heading.setTextColor(TEXT);heading.setTypeface(Typeface.create(font,600,false));card.addView(heading);
            content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
            ScrollView scroll=new ScrollView(context) {
                @Override protected void onMeasure(int width,int height) {
                    int maximum=(int)(context.getResources().getDisplayMetrics().heightPixels*0.55f);
                    super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(maximum,View.MeasureSpec.AT_MOST));
                }
            };
            scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(16);card.addView(scroll,size);
        }
        int dp(int value) {return Math.round(value*context.getResources().getDisplayMetrics().density);}
        GradientDrawable background(int fill,int stroke,int radius) {
            GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(radius));
            shape.setStroke(dp(1),stroke);return shape;
        }
        void description(String message) {
            if(message==null||message.isEmpty())return;
            TextView body=new TextView(context);body.setText(UiLanguage.text(message));body.setTextSize(15);
            body.setTextColor(MUTED);body.setTypeface(font);body.setLineSpacing(dp(3),1);
            content.addView(body,new LinearLayout.LayoutParams(-1,-2));
        }
        Button button(String label,int fill,int color) {
            Button button=new Button(context);button.setText(UiLanguage.text(label));button.setAllCaps(false);
            button.setTypeface(font);button.setTextSize(15);button.setTextColor(color);
            button.setMinHeight(dp(52));button.setMinimumWidth(0);button.setMinWidth(0);
            button.setPadding(dp(18),dp(10),dp(18),dp(10));button.setBackground(background(fill,fill,14));
            button.setStateListAnimator(null);
            FigmaCanvas.pressFeedback(button);
            return button;
        }
        LinearLayout actions() {
            LinearLayout buttons=new LinearLayout(context);buttons.setGravity(Gravity.END);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(24);card.addView(buttons,size);return buttons;
        }
        void action(LinearLayout buttons,String label,boolean primary,boolean destructive,Runnable action) {
            int fill=primary?(destructive?0xff522b2b:ACCENT):SECONDARY;
            Button button=button(label,fill,destructive?0xffffb3a5:TEXT);
            button.setBackground(background(fill,fill,28));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);
            if(buttons.getChildCount()>0)size.leftMargin=dp(12);
            buttons.addView(button,size);button.setOnClickListener(v->action.run());
        }
        void open() {
            dialog.setContentView(card);dialog.setCanceledOnTouchOutside(true);dialog.show();
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=0.62f;window.setAttributes(params);
                window.setLayout(Math.max(dp(200),Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(32),dp(560))),-2);
            }
            card.setTranslationY(dp(12));card.animate().translationY(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
        }
    }
}
