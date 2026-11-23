// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/** Shared native primitives in the 2560x1600 tablet layout. */
public class ScreenLayout extends ViewGroup {
    private static final HashMap<String,Bitmap> bitmaps=new HashMap<>();
    private final ArrayList<Slot> slots=new ArrayList<>();
    private final Typeface regular,medium;
    private final ProductAppearance.Color theme;
    static final int OUTLINE_STROKE=0x80e6eef9;
    private static final int OUTLINE_FILL=0x33101419;
    private float scale,offsetX,offsetY;
    private final TextView notice;

    public final TextView notice() { return notice; }
    private static final class Slot {
        final View view;final float x,y,w,h,font,radius;final int fill,end,border;
        float appliedScale=-1;
        Slot(View v,float x,float y,float w,float h,float font,int fill,int end,int border,float radius) {
            view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;this.fill=fill;this.end=end;this.border=border;this.radius=radius;
        }
    }
    enum Surface { PAGE, OVERLAY }

    ScreenLayout(Context context) {
        this(context, Surface.PAGE);
    }

    ScreenLayout(Context context, Surface surface) {
        super(context);
        theme=ProductAppearance.theme(context);
        setClipChildren(true);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create(regular, 600, false);
        boolean overlay = surface == Surface.OVERLAY;
        notice = overlay
                ? text("", 0, 0, 0, 0, 0, 0, false, Gravity.CENTER)
                : text("", 112, 1393, 2336, 32, 24, 0xfff5f5f7, false, Gravity.CENTER);
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
        TextView t=new TextView(getContext());t.setText(UiLanguage.text(value));t.setTextColor(themedColor(color));t.setTypeface(bold?medium:regular);t.setGravity(gravity);t.setIncludeFontPadding(false);t.setMaxLines(3);
        slot(t,x,y,w,h,size,0,0,0,0);return t;
    }
    final TextView button(String value,float x,float y,float w,float h,float size,int color,int fill,int border,float radius,Runnable action) {
        if(fill==0xffff8a47) {fill=0xf538d185;border=0xcc38d185;color=0xff05140f;}
        TextView t=new TextView(getContext());t.setText(UiLanguage.text(value));t.setTextColor(themedColor(color));t.setTypeface(regular);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);t.setMaxLines(2);
        slot(t,x,y,w,h,size,fill,fill==0xffff8a47?0xffe36e35:fill,border,radius);click(t,action);return t;
    }
    final void click(View view,Runnable action) {
        if(view.getContentDescription()!=null)view.setContentDescription(UiLanguage.text(view.getContentDescription().toString()));
        view.setFocusable(true);view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        pressFeedback(view);
        view.setOnClickListener(v->action.run());
    }
    public static void pressFeedback(View view) {
        pressFeedback(view,12*view.getResources().getDisplayMetrics().density);
    }

    final void backLink(int left,int top,Runnable back) {
        int padding=24, iconWidth=23, gap=14, fontSize=32;
        var measure=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        measure.setTypeface(regular);
        measure.setTextSize(fontSize);
        int labelWidth=(int)Math.ceil(measure.measureText(UiLanguage.text("Назад")))+2;
        int width=padding*2+iconWidth+gap+labelWidth;
        shape(left,top,width,86,OUTLINE_FILL,OUTLINE_STROKE,43);
        accentIcon("v12/8b7c5-svg.png",left+padding,top+27,iconWidth,32);
        TextView label=text("Назад",left+padding+iconWidth+gap,top,labelWidth,86,fontSize,
                ProductAppearance.Color.BLUE.accent,false,Gravity.CENTER);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        View target=new View(getContext());
        target.setContentDescription("Назад");
        widget(target,left,top,width,86);
        click(target,back);
        roundedPress(target,43,86);
    }

    final void outlineAction(String title,int x,int y,int width,int height,int font,Runnable action) {
        TextView target=button(title,x,y,width,height,font,ProductAppearance.Color.BLUE.accent,
                OUTLINE_FILL,OUTLINE_STROKE,height/2f,action);
        roundedPress(target,height/2f,height);
    }
    public static void roundedPress(View view,float radius,float height) {
        view.addOnLayoutChangeListener((v,l,t,r,b,oldL,oldT,oldR,oldB)->{
            if(b-t!=oldB-oldT)pressFeedback(v,radius*(b-t)/height);
        });
    }
    public static Drawable wallpaper(Context context) {
        Bitmap bitmap=bitmap(context,ProductAppearance.wallpaper(context));
        var wallpaper=new BitmapDrawable(context.getResources(),bitmap) {
            private final RectF area=new RectF();

            @Override public void draw(Canvas canvas) {
                var bounds=getBounds();
                float scale = Math.max((float) bounds.width() / bitmap.getWidth(),
                        (float) bounds.height() / bitmap.getHeight());
                float width = bitmap.getWidth() * scale;
                float height = bitmap.getHeight() * scale;
                float left=bounds.exactCenterX()-width/2, top=bounds.exactCenterY()-height/2;
                area.set(left,top,left+width,top+height);
                canvas.drawColor(0xff101113);
                int saved = canvas.save();
                canvas.clipRect(bounds);
                canvas.drawBitmap(bitmap, null, area, getPaint());
                canvas.restoreToCount(saved);
            }
        };
        wallpaper.setFilterBitmap(true);
        return wallpaper;
    }
    private static void pressFeedback(View view,float radius) {
        view.setDefaultFocusHighlightEnabled(false);
        GradientDrawable mask=new GradientDrawable();
        mask.setColor(0xffffffff);
        mask.setCornerRadius(radius);
        int[][] states={{android.R.attr.state_pressed},{android.R.attr.state_focused},{}};
        view.setForeground(new RippleDrawable(new ColorStateList(states,
                new int[]{0x247fa9d8,0x1a7fa9d8,0x207fa9d8}),null,mask));
    }
    private static Bitmap bitmap(Context context,String name) {
        Bitmap bitmap=bitmaps.get(name);
        if(bitmap==null) {
            try(InputStream stream=context.getAssets().open("product-ui/"+name)) {bitmap=BitmapFactory.decodeStream(stream);}
            catch(java.io.IOException e) {throw new IllegalStateException("Missing UI asset: "+name,e);}
            if(bitmap==null)throw new IllegalStateException("Invalid UI bitmap: "+name);
            bitmaps.put(name,bitmap);
        }
        return bitmap;
    }
    final void brandIcon(float x, float y, float size) {
        asset("U-AVF ICON.png",x,y,size,size).setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    final ImageView asset(String name,float x,float y,float w,float h) {
        Bitmap bitmap=bitmap(getContext(),name);
        ImageView image=new ImageView(getContext());image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_XY);image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        slot(image,x,y,w,h,0,0,0,0,0);
        return image;
    }

    final void accentIcon(String name,float x,float y,float w,float h) {
        ImageView icon=asset(name,x,y,w,h);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(theme.accent,android.graphics.PorterDuff.Mode.SRC_IN);
    }
    private void slot(View view,float x,float y,float w,float h,float font,int fill,int end,int border,float radius) {slots.add(new Slot(view,x,y,w,h,font,fill,end,border,radius));addView(view);}
    final void widget(View view,float x,float y,float w,float h) {slot(view,x,y,w,h,0,0,0,0,0);}
    protected void onMeasure(int widthSpec,int heightSpec) {
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        scale=Math.min(w/2560f,h/1600f);offsetX=(w-2560*scale)/2;offsetY=(h-1600*scale)/2;
        for(Slot s:slots) {
            if(s.appliedScale!=scale) {
                s.appliedScale=scale;
                if(s.font>0)((TextView)s.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,s.font*scale);
                if(s.fill!=0) {
                    GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{themedColor(s.fill),themedColor(s.end)});
                    background.setCornerRadius(s.radius*scale);
                    if(s.border!=0)background.setStroke(Math.max(1,Math.round(1.5f*scale)),themedColor(s.border));
                    s.view.setBackground(background);
                }
            }
            s.view.measure(MeasureSpec.makeMeasureSpec(Math.round(s.w*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(s.h*scale),MeasureSpec.EXACTLY));
        }
    }

    private int themedColor(int color) {
        return ProductAppearance.tint(theme,color);
    }
    protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        for(Slot s:slots) {int x=Math.round(offsetX+s.x*scale),y=Math.round(offsetY+s.y*scale);s.view.layout(x,y,x+s.view.getMeasuredWidth(),y+s.view.getMeasuredHeight());}
    }
}
