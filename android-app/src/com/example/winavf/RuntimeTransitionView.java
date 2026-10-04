// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Map;

/** Responsive native translation of Figma369:123/173/223/273. No progress percentage. */
final class RuntimeTransitionView extends FrameLayout {
    private final RuntimeTransition model;
    private final TextView title,subtitle,telemetry;
    private final LinearLayout stages,actions;
    private final Button force,back;
    private String previous="";
    RuntimeTransitionView(Context context,RuntimeTransition model,Runnable forceStop,Runnable returnHome) {
        super(context); this.model=model;
        setBackgroundColor(0xff070a0f); setClickable(true); setFocusable(true);
        int pad=dp(16); setPadding(pad,pad,pad,pad);
        LinearLayout shell=new LinearLayout(context); shell.setOrientation(1);shell.setPadding(pad,pad,pad,pad);
        shell.setBackground(bg(0xe6080c12,26));addView(shell,new LayoutParams(-1,-1));
        View rail=new View(context);rail.setBackgroundColor(0xff34e38b);
        shell.addView(rail,new LinearLayout.LayoutParams(dp(280),dp(3)));
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);shell.addView(header,space(-1,-2,16));
        LinearLayout heading=new LinearLayout(context);heading.setOrientation(1);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(label("U-AVF / RUNTIME",12,0xffa7b6c3));
        title=label("Starting Ubuntu",26,0xfff6f9fb);title.setMaxLines(2);heading.addView(title,space(-1,-2,6));
        subtitle=label("Please wait…",13,0xff91a2b4);subtitle.setMaxLines(2);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(subtitle,space(-1,-2,4));
        header.addView(new RuntimeOrbitalView(context),new LinearLayout.LayoutParams(dp(80),dp(80)));
        LinearLayout columns=new LinearLayout(context);columns.setOrientation(0);columns.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1);body.topMargin=dp(12);shell.addView(columns,body);
        LinearLayout panel=new LinearLayout(context);panel.setOrientation(1);panel.setPadding(dp(12),dp(12),dp(12),dp(12));panel.setBackground(bg(0xe0101a24,18));
        columns.addView(panel,new LinearLayout.LayoutParams(0,-1,3));
        panel.addView(label("PROGRESS",13,0xffa7b6c3));
        stages=new LinearLayout(context);stages.setOrientation(1);LinearLayout.LayoutParams stageArea=new LinearLayout.LayoutParams(-1,0,1);stageArea.topMargin=dp(8);panel.addView(stages,stageArea);
        telemetry=label("",14,0xffa7b6c3);telemetry.setPadding(dp(20),dp(20),dp(20),dp(20));telemetry.setBackground(bg(0xff0d141c,17));
        telemetry.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams side=new LinearLayout.LayoutParams(0,-1,1);side.leftMargin=dp(16);columns.addView(telemetry,side);
        actions=new LinearLayout(context);actions.setGravity(Gravity.END);shell.addView(actions,space(-1,-2,20));
        Button wait=action(context,"Keep waiting",0xff34e38b);wait.setOnClickListener(v->{model.keepWaiting(SystemClock.elapsedRealtime());refresh();});actions.addView(wait);
        force=action(context,"Force stop…",0xffff8d7a);force.setOnClickListener(v->forceStop.run());
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,-2);fp.leftMargin=dp(16);actions.addView(force,fp);
        back=action(context,"Back to workspace",0xfff6f9fb);back.setOnClickListener(v->returnHome.run());actions.addView(back);
        shell.addView(label("✓ Completed · ○ Waiting",12,0xff91a2b4),space(-1,-2,8));
        setVisibility(GONE);
    }
    void refresh() {
        if(!model.visible()) {setVisibility(GONE);return;}
        setVisibility(VISIBLE);bringToFront();
        long now=SystemClock.elapsedRealtime();RuntimeTransition.Mode mode=model.mode();
        title.setText(mode==RuntimeTransition.Mode.COMPLETE?"Ubuntu is ready":mode==RuntimeTransition.Mode.ERROR?"Ubuntu needs attention":mode==RuntimeTransition.Mode.SHUTDOWN?"Shutting down Ubuntu":mode==RuntimeTransition.Mode.REBOOT?"Restarting Ubuntu":"Starting Ubuntu");
        subtitle.setText(mode==RuntimeTransition.Mode.COMPLETE?"Opening desktop…":mode==RuntimeTransition.Mode.ERROR?model.detail():model.waiting(now)?"Still working. You can keep waiting.":"Please wait…");
        Map<String,String> events=model.events();String signature=mode+events.toString();
        if(!signature.equals(previous)) {
            previous=signature; stages.removeAllViews();
            String[] rows=(model.operation()==RuntimeTransition.Mode.SHUTDOWN)?new String[]{"Shutdown requested","Power request accepted","VM stopped"}:
                model.operation()==RuntimeTransition.Mode.REBOOT?new String[]{"Restart requested","Power request accepted","VM stopped","UEFI started","Linux kernel","systemd userspace","GDM started","Display connected","Desktop presented"}:
                new String[]{"Start requested","Virtual machine created","UEFI started","Linux kernel","systemd userspace","GDM started","Display connected","Desktop presented"};
            for(String name:rows) {
                boolean seen=events.containsKey(name);
                TextView row=label((seen?"✓  ":"○  ")+name,14,seen?0xff6ff0b0:0xff8293a5);
                row.setGravity(Gravity.CENTER_VERTICAL);row.setMaxLines(1);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                row.setTypeface(null,seen?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                row.setContentDescription(name+(seen?", completed, "+events.get(name):", not yet observed"));
                row.setPadding(dp(12),0,dp(12),0);GradientDrawable card=bg(seen?0xff102c24:0xff101923,10);
                if(seen)card.setStroke(dp(1),0xff34e38b);row.setBackground(card);
                LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,0,1);rowSize.topMargin=dp(5);stages.addView(row,rowSize);
            }
        }
        String last="None";for(String name:events.keySet())last=name;
        telemetry.setText("STATUS\n\nElapsed\n"+String.format(java.util.Locale.ROOT,"%02d:%02d",model.elapsed(now)/60,model.elapsed(now)%60)+"\n\nLatest step\n"+last);
        actions.setVisibility(model.waiting(now)||mode==RuntimeTransition.Mode.ERROR?VISIBLE:GONE);
        back.setVisibility(mode==RuntimeTransition.Mode.ERROR?VISIBLE:GONE);
    }
    void allowForceStop(boolean allowed) {force.setEnabled(allowed);}
    private Button action(Context context,String text,int color) {Button b=new Button(context);b.setText(text);b.setAllCaps(false);b.setTextColor(color);b.setTextSize(14);b.setBackground(bg(0xff142230,14));b.setPadding(dp(20),dp(10),dp(20),dp(10));b.setMinHeight(dp(48));return b;}
    private TextView label(String value,int size,int color) {TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable bg(int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xff293b4f);return d;}
    private LinearLayout.LayoutParams space(int w,int h,int top) {LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int value) {return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
}
