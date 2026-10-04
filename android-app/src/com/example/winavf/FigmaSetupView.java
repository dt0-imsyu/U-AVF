// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;
import java.util.ArrayList;

/** Native implementation of Figma342:2. Reference geometry scales as one canvas. */
final class FigmaSetupView extends ViewGroup {
    private final ArrayList<Slot> slots=new ArrayList<>();
    private final Typeface regular,medium;
    private final TextView evidence;
    private float scale=1,offsetX,offsetY;
    private static final class Slot {
        final View view;final float x,y,w,h;final int color,border;final float radius,fontSize;
        Slot(View v,float x,float y,float w,float h,int color,int border,float radius,float fontSize) {
            this.view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.color=color;this.border=border;this.radius=radius;this.fontSize=fontSize;
        }
    }
    FigmaSetupView(Context context,boolean installed,boolean running,boolean isoVerified,Runnable primary,Runnable back,Runnable diagnostics) {
        super(context);setClipChildren(true);setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff071019,0xff0b1720}));
        regular=ProductFont.load(context);medium=Typeface.create(regular,500,false);
        asset("setup-glow-b.png",-121,929,640,640);
        asset("setup-glow-a.png",1549,519,900,900);
        asset("setup-glow-depth.png",759,249,950,950);
        shape(0,0,2560,66,0xf509121a,0xd91d3142,0);
        text("Ubuntu",87,0,260,66,30,0xffdde7ef,Gravity.CENTER_VERTICAL,false);
        text("Installation mode",1039,0,480,66,30,0xffdde7ef,Gravity.CENTER,false);
        TextView backButton=text("‹  Workspace",87,91,430,65,30,0xffabbacc,Gravity.CENTER_VERTICAL,false);backButton.setOnClickListener(v->back.run());
        shape(151,89,520,6,0xe634e38b,0,3);shape(679,89,150,6,0xa659b8ff,0,3);
        shape(2091,87,376,56,0xf00f1720,0xcc293b4f,28);
        text("U-AVF installation mode",2109,87,340,56,28,0xffabbacc,Gravity.CENTER,false);
        shape(519,234,1520,1030,0xf7101923,0xe6293b4f,36);
        shape(519,234,1520,92,0xf5151e28,0xd92b3b4b,28);
        text("Ubuntu 24.04.5 LTS · Native Setup",599,234,1360,92,30,0xfff4f7fa,Gravity.CENTER,false);
        text(installed?"Ubuntu is installed":"Install Ubuntu",649,378,1260,100,62,0xfff7fafc,Gravity.CENTER,true);
        text(running?"Ubuntu is running.":installed?"Ubuntu is installed.":"Ready to install Ubuntu.",649,467,1260,64,30,0xff97a8b7,Gravity.CENTER,false);
        row(584,"Install target","Ubuntu Root · vda3","Reinstall target",false);
        row(724,"U-AVF platform / EFI","Protected profile","Preserved",false);
        row(864,"Official Ubuntu ISO",isoVerified?"Verified source":"Not verified","Preserved",isoVerified);
        evidence=text("Loading recorded setup checks…",689,1015,1180,92,27,0xffabbacc,Gravity.CENTER,false);
        TextView action=text(running?"Continue in Setup":installed?"Open workspace":"Prepare Ubuntu Setup",1341,1135,516,64,30,0xff05140f,Gravity.CENTER,false);
        shape(1311,1119,576,96,0xf538d185,0xcc38d185,24);
        // Bring the label above its button background; background owns the same action.
        action.bringToFront();action.setOnClickListener(v->primary.run());
        slots.get(slots.size()-1).view.setOnClickListener(v->primary.run());
        TextView reports=text("Diagnostics & report",689,1120,520,96,28,0xffabbacc,Gravity.CENTER,false);reports.setOnClickListener(v->diagnostics.run());
    }
    void recordedState(String value) {evidence.setText(value);}
    private void row(int y,String label,String detail,String state,boolean verified) {
        shape(689,y,1180,116,0xf50c121a,0xcc293b4f,22);
        text(label,734,y,430,116,29,0xffabbacc,Gravity.CENTER_VERTICAL,false);
        text(detail,1169,y,450,116,30,verified?0xff38d185:0xffabbacc,Gravity.CENTER_VERTICAL,false);
        text(state,1599,y,220,116,28,0xffabbacc,Gravity.CENTER_VERTICAL|Gravity.RIGHT,false);
    }
    private void asset(String name,int x,int y,int w,int h) {
        ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.FIT_XY);
        try(InputStream stream=getContext().getAssets().open("product-ui/"+name)) {image.setImageBitmap(BitmapFactory.decodeStream(stream));}
        catch(java.io.IOException e) {throw new IllegalStateException("Missing Figma asset: "+name,e);}
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);slot(image,x,y,w,h,0,0,0,0);
    }
    private View shape(float x,float y,float w,float h,int color,int border,float radius) {View v=new View(getContext());v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);slot(v,x,y,w,h,color,border,radius,0);return v;}
    private TextView text(String value,float x,float y,float w,float h,float size,int color,int gravity,boolean bold) {
        TextView t=new TextView(getContext());t.setText(value);t.setTextColor(color);t.setTypeface(bold?medium:regular);t.setGravity(gravity);t.setIncludeFontPadding(false);t.setMaxLines(2);
        slot(t,x,y,w,h,0,0,0,size);return t;
    }
    private void slot(View view,float x,float y,float w,float h,int color,int border,float radius,float fontSize) {slots.add(new Slot(view,x,y,w,h,color,border,radius,fontSize));addView(view);}
    protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        scale=Math.min(w/2560f,h/1600f);offsetX=(w-2560*scale)/2;offsetY=(h-1600*scale)/2;
        for(Slot s:slots) {
            if(s.fontSize>0)((TextView)s.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,s.fontSize*scale);
            if(s.color!=0) {GradientDrawable d=new GradientDrawable();d.setColor(s.color);d.setCornerRadius(s.radius*scale);if(s.border!=0)d.setStroke(Math.max(1,Math.round(scale)),s.border);s.view.setBackground(d);}
            s.view.measure(MeasureSpec.makeMeasureSpec(Math.round(s.w*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(s.h*scale),MeasureSpec.EXACTLY));
        }
    }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        for(Slot s:slots) {int x=Math.round(offsetX+s.x*scale),y=Math.round(offsetY+s.y*scale);s.view.layout(x,y,x+s.view.getMeasuredWidth(),y+s.view.getMeasuredHeight());}
    }
}
