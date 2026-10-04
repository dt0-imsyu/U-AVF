// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.CheckBox;

/** Figma294:10, native interactive welcome, no bypass of first-run checks. */
final class FigmaWelcomeView extends FigmaCanvas {
    private final android.widget.TextView consentLabel;
    FigmaWelcomeView(Context context,boolean sharing,java.util.function.Consumer<Boolean> consent,Runnable start,Runnable privacy) {
        super(context,"create-background.png",false);
        asset("welcome-header.png",150,105,1370,821);
        text("Welcome to U-AVF",560,900,1440,128,102,0xfaf4f7fa,true,Gravity.CENTER);
        text("Full ARM64 virtual machines on stock Android — no root, no bootloader unlock.",590,1028,1380,70,34,0xe6b8c4d0,false,Gravity.CENTER);
        button("Get started",962,1200,636,110,36,0xff06120b,0xff2fe58b,0xff2fe58b,26,start);
        text("Compatibility runs once and can be repeated later from Diagnostics.",710,1450,1140,50,25,0xffa7b6c3,false,Gravity.CENTER);
        CheckBox checkbox=new CheckBox(context);checkbox.setChecked(sharing);
        checkbox.setButtonTintList(ColorStateList.valueOf(0xff2fe58b));
        checkbox.setContentDescription("Send compatibility results to the developer's Firebase cloud database after the test; optional");
        checkbox.setOnCheckedChangeListener((v,value)->consent.accept(value));
        android.widget.LinearLayout row=new android.widget.LinearLayout(context);
        row.setGravity(Gravity.CENTER);row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.addView(checkbox,new android.widget.LinearLayout.LayoutParams(-2,-1));
        consentLabel=new android.widget.TextView(context);consentLabel.setText("Share test results via Firebase cloud · optional");
        consentLabel.setTextColor(0xffa7b6c3);consentLabel.setGravity(Gravity.CENTER_VERTICAL);
        consentLabel.setTypeface(ProductFont.load(context));
        row.addView(consentLabel,new android.widget.LinearLayout.LayoutParams(-2,-1));
        click(consentLabel,()->checkbox.setChecked(!checkbox.isChecked()));widget(row,710,1330,1140,80);
        text("For development · device model, Android, test results & anonymous ID",710,1410,1140,35,22,0xffa7b6c3,false,Gravity.CENTER);
        button("Privacy details",1040,1510,480,55,25,0xffa7b6c3,0xff15202b,0xff2a3b4d,14,privacy);
    }
    @Override protected void onMeasure(int w,int h) {
        consentLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24*Math.min(android.view.View.MeasureSpec.getSize(w)/2560f,android.view.View.MeasureSpec.getSize(h)/1600f));
        super.onMeasure(w,h);
    }
}
