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

/** Shared product dialogs. Dismissal never performs a destructive action. */
final class ProductDialog {
    private static final int TEXT=0xffedf4fa, MUTED=0xffa7b6c3, ACCENT=0xff34e38b;
    static void show(Context context,String title,String message,String confirm,boolean destructive,Runnable action) {
        Panel panel=new Panel(context,title);panel.description(message);
        LinearLayout actions=panel.actions();
        panel.action(actions,"Cancel",false,false,panel.dialog::dismiss);
        panel.action(actions,confirm,true,destructive,()->{panel.dialog.dismiss();action.run();});
        panel.open();
    }
    static void notice(Context context,String title,String message) {
        Panel panel=new Panel(context,title);panel.description(message);
        panel.action(panel.actions(),"Got it",true,false,panel.dialog::dismiss);panel.open();
    }
    static void choose(Context context,String title,String message,String[] options,int selected,IntConsumer action) {
        Panel panel=new Panel(context,title);panel.description(message);
        for(int i=0;i<options.length;i++) {
            final int index=i;
            Button row=panel.button(options[i]+(i==selected?"  ✓":""),i==selected?0xff173b30:0xff16232f,TEXT);
            row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.setBackground(panel.background(i==selected?0xff173b30:0xff16232f,i==selected?ACCENT:0xff2a3b4d,14));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=panel.dp(10);
            panel.content.addView(row,size);row.setOnClickListener(v->{panel.dialog.dismiss();action.accept(index);});
        }
        panel.action(panel.actions(),"Cancel",false,false,panel.dialog::dismiss);panel.open();
    }
    private static final class Panel {
        final Context context;final Dialog dialog;final LinearLayout card,content;final Typeface font;
        Panel(Context context,String title) {
            this.context=context;font=ProductFont.load(context);
            dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(24),dp(24),dp(24),dp(24));
            card.setBackground(background(0xff101923,0xff293b4f,24));card.setElevation(dp(16));
            TextView heading=new TextView(context);heading.setText(title);heading.setTextSize(23);
            heading.setTextColor(TEXT);heading.setTypeface(font);card.addView(heading);
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
            TextView body=new TextView(context);body.setText(message);body.setTextSize(15);
            body.setTextColor(MUTED);body.setTypeface(font);body.setLineSpacing(dp(3),1);
            content.addView(body,new LinearLayout.LayoutParams(-1,-2));
        }
        Button button(String label,int fill,int color) {
            Button button=new Button(context);button.setText(label);button.setAllCaps(false);
            button.setTypeface(font);button.setTextSize(15);button.setTextColor(color);
            button.setMinHeight(dp(52));button.setMinimumWidth(0);button.setMinWidth(0);
            button.setPadding(dp(18),dp(10),dp(18),dp(10));button.setBackground(background(fill,fill,14));return button;
        }
        LinearLayout actions() {
            LinearLayout buttons=new LinearLayout(context);buttons.setGravity(Gravity.END);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(24);card.addView(buttons,size);return buttons;
        }
        void action(LinearLayout buttons,String label,boolean primary,boolean destructive,Runnable action) {
            Button button=button(label,primary?(destructive?0xff522b2b:ACCENT):0xff1b2937,
                    primary?(destructive?0xffffb3a5:0xff07140e):TEXT);
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
