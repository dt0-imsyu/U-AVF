// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.graphics.Bitmap;
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
import java.util.HashMap;

/** Shared native primitives in the Figma tablet's 2560x1600 coordinate space. */
class FigmaCanvas extends ViewGroup {
    private static final HashMap<String,Bitmap> bitmaps=new HashMap<>();
    private final ArrayList<Slot> slots=new ArrayList<>();
    private final Typeface regular,medium;
    private float scale,offsetX,offsetY;
    final TextView notice;
    private static final class Slot {
        final View view;final float x,y,w,h,font,radius;final int fill,end,border;
        Slot(View v,float x,float y,float w,float h,float font,int fill,int end,int border,float radius) {
            view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;this.fill=fill;this.end=end;this.border=border;this.radius=radius;
        }
    }
    FigmaCanvas(Context context,String background,boolean home) {
        this(context,background,home,1340);
    }
    FigmaCanvas(Context context,String background,boolean home,float noticeY) {
        super(context);setBackgroundColor(0xff070a0f);setClipChildren(true);
        regular=ProductFont.load(context);medium=Typeface.create(regular,500,false);
        // Product-wide Setup scene; keep each page's controls and truthful state.
        widget(new FigmaProductBackdrop(context),0,0,2560,1600);
        asset("logo.png",150,105,120,162);
        shape(151,89,520,6,0xe634e38b,0,3);shape(679,89,150,6,0xa659b8ff,0,3);
        notice=text("",430,noticeY,1700,96,26,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
    }
    final void header(String title,String subtitle,String back,Runnable backAction) {
        text(title,360,119,1760,113,76,0xfaf4f7fa,true,Gravity.CENTER_VERTICAL);
        text(subtitle,360,239,1700,55,30,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
        button(back,2150,122,230,90,28,0xffa7b6c3,0xfa15202b,0xe62a3b4d,24,backAction);
    }
    final void wizard(int step) {
        String[] names={"Operating\nsystem","Image","Storage","Review"};
        for(int i=0;i<4;i++) {
            int x=420+i*430;boolean done=i+1<step;
            asset(done?"step-done.png":"step.png",x,310,42,42);
            text(done?"✓":Integer.toString(i+1),x,310,42,42,20,done?0xff05140f:0xffb8c4d0,false,Gravity.CENTER);
            text(names[i],x-55,365,190,60,23,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            if(i<3)asset(done?"step-line-done.png":"step-line.png",x+58,327,330,4);
        }
    }
    final View shape(float x,float y,float w,float h,int fill,int border,float radius) {return gradient(x,y,w,h,fill,fill,border,radius);}
    final View gradient(float x,float y,float w,float h,int start,int end,int border,float radius) {
        View v=new View(getContext());v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);slot(v,x,y,w,h,0,start,end,border,radius);return v;
    }
    final TextView text(String value,float x,float y,float w,float h,float size,int color,boolean bold,int gravity) {
        TextView t=new TextView(getContext());t.setText(value);t.setTextColor(color);t.setTypeface(bold?medium:regular);t.setGravity(gravity);t.setIncludeFontPadding(false);t.setMaxLines(3);
        slot(t,x,y,w,h,size,0,0,0,0);return t;
    }
    final TextView button(String value,float x,float y,float w,float h,float size,int color,int fill,int border,float radius,Runnable action) {
        if(fill==0xffff8a47) {fill=0xf538d185;border=0xcc38d185;color=0xff05140f;}
        TextView t=new TextView(getContext());t.setText(value);t.setTextColor(color);t.setTypeface(regular);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);t.setMaxLines(2);
        slot(t,x,y,w,h,size,fill,fill==0xffff8a47?0xffe36e35:fill,border,radius);click(t,action);return t;
    }
    final void click(View view,Runnable action) {view.setFocusable(true);view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);view.setOnClickListener(v->action.run());}
    final void asset(String name,float x,float y,float w,float h) {
        Bitmap bitmap=bitmaps.get(name);
        if(bitmap==null) {
            try(InputStream stream=getContext().getAssets().open("product-ui/"+name)) {bitmap=BitmapFactory.decodeStream(stream);}
            catch(java.io.IOException e) {throw new IllegalStateException("Missing Figma asset: "+name,e);}
            if(bitmap==null)throw new IllegalStateException("Invalid Figma bitmap: "+name);
            bitmaps.put(name,bitmap);
        }
        ImageView image=new ImageView(getContext());image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_XY);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        slot(image,x,y,w,h,0,0,0,0,0);
    }
    private void slot(View view,float x,float y,float w,float h,float font,int fill,int end,int border,float radius) {slots.add(new Slot(view,x,y,w,h,font,fill,end,border,radius));addView(view);}
    final void widget(View view,float x,float y,float w,float h) {slot(view,x,y,w,h,0,0,0,0,0);}
    protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        scale=Math.min(w/2560f,h/1600f);offsetX=(w-2560*scale)/2;offsetY=(h-1600*scale)/2;
        for(Slot s:slots) {
            if(s.font>0)((TextView)s.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,s.font*scale);
            if(s.fill!=0) {GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{s.fill,s.end});d.setCornerRadius(s.radius*scale);if(s.border!=0)d.setStroke(Math.max(1,Math.round(1.5f*scale)),s.border);s.view.setBackground(d);}
            s.view.measure(MeasureSpec.makeMeasureSpec(Math.round(s.w*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(s.h*scale),MeasureSpec.EXACTLY));
        }
    }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        for(Slot s:slots) {int x=Math.round(offsetX+s.x*scale),y=Math.round(offsetY+s.y*scale);s.view.layout(x,y,x+s.view.getMeasuredWidth(),y+s.view.getMeasuredHeight());}
    }
}
