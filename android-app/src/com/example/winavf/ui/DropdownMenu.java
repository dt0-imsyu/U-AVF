// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Compact choice list anchored to the control that opened it. */
public final class DropdownMenu {
    public static void show(View anchor,String[] options,int selected,IntConsumer action) {
        var context=anchor.getContext();
        float density=anchor.getResources().getDisplayMetrics().density;
        int pad=Math.round(6*density);
        LinearLayout list=new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad,pad,pad,pad);
        GradientDrawable card=new GradientDrawable();
        card.setColor(ProductAppearance.tint(context,0xff27282c));
        card.setCornerRadius(16*density);
        card.setStroke(Math.max(1,Math.round(density)),ProductAppearance.tint(context,0xff3a3c42));
        Rect bounds=new Rect();
        anchor.getWindowVisibleDisplayFrame(bounds);
        int rowHeight=Math.round(48*density);
        Paint measure=new Paint();
        measure.setTextSize(15*anchor.getResources().getDisplayMetrics().scaledDensity);
        float widest=0;
        for(String option:options)widest=Math.max(widest,measure.measureText(UiLanguage.text(option)));
        int width=Math.min(Math.max(Math.round(220*density),(int)Math.ceil(widest)+pad*6),
                Math.max(1,bounds.width()-pad*2));
        int height=Math.min(rowHeight*options.length+pad*2,Math.max(rowHeight,bounds.height()-pad*2));
        ScrollView scroll=new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(list,new ScrollView.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT));
        PopupWindow popup=new PopupWindow(scroll,width,height,true);
        popup.setBackgroundDrawable(card);
        popup.setElevation(12*density);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        for(int index=0;index<options.length;index++) {
            final int choice=index;
            boolean checked=index==selected;
            TextView row=new TextView(context);
            row.setText(UiLanguage.text(options[index]));
            row.setTextColor(checked?Color.WHITE:0xffe0e0e4);
            row.setTextSize(15);
            row.setTypeface(Typeface.create(Typeface.DEFAULT,checked?600:400,false));
            row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.setMaxLines(2);
            row.setPadding(2*pad,0,2*pad,0);
            row.setSelected(checked);
            if(checked) {
                GradientDrawable highlight=new GradientDrawable();
                highlight.setColor(ProductAppearance.tint(context,0xff34363b));
                highlight.setCornerRadius(10*density);
                row.setBackground(highlight);
            }
            ScreenLayout.pressFeedback(row);
            row.setFocusable(true);
            row.setOnClickListener(view->{popup.dismiss();action.accept(choice);});
            list.addView(row,new LinearLayout.LayoutParams(-1,rowHeight));
        }
        popup.showAsDropDown(anchor,0,pad,Gravity.END);
        if(android.animation.ValueAnimator.areAnimatorsEnabled()) {
            list.setAlpha(.72f);
            list.setTranslationY(-pad);
            list.animate().alpha(1f).translationY(0f).setDuration(145).start();
        }
    }
}
