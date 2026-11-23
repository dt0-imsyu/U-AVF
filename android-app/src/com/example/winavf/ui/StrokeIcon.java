// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

final class StrokeIcon extends Drawable {
    enum Kind { CHECKLIST, FILE_CHECK, LOCK, INFO, MESSAGE, EXTERNAL }

    private final Kind kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    StrokeIcon(Kind kind,int color) {
        this.kind=kind;
        paint.setColor(color);
        paint.setStrokeWidth(1.75f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas canvas) {
        int save=canvas.save();
        var bounds=getBounds();
        canvas.translate(bounds.left,bounds.top);
        canvas.scale(bounds.width()/24f,bounds.height()/24f);
        switch(kind) {
            case CHECKLIST -> {
                canvas.drawRoundRect(4,3,20,21,2,2,paint);
                line(canvas,7.5f,9,9,10.5f,11,8);
                line(canvas,7.5f,15,9,16.5f,11,14);
                line(canvas,14,9,17,9);
                line(canvas,14,15,17,15);
            }
            case FILE_CHECK -> {
                line(canvas,17.5f,7,13,2.5f,5,2.5f,5,21.5f,19,21.5f,19,8,13,8,13,2.5f);
                line(canvas,8.5f,15,11,17.5f,15.5f,12);
            }
            case LOCK -> {
                canvas.drawRoundRect(4.5f,10,19.5f,21,2.5f,2.5f,paint);
                line(canvas,8,10,8,7);
                canvas.drawArc(8,3,16,11,180,180,false,paint);
                line(canvas,16,7,16,10);
                line(canvas,12,14.5f,12,17);
            }
            case INFO -> {
                canvas.drawCircle(12,12,9,paint);
                line(canvas,12,11,12,16);
                canvas.drawPoint(12,7.8f,paint);
            }
            case MESSAGE -> {
                line(canvas,4,4,20,4,20,17,10,17,4,21,4,4);
                line(canvas,8,8.5f,16,8.5f);
                line(canvas,8,12.5f,13,12.5f);
            }
            case EXTERNAL -> line(canvas,6,18,18,6,10,6,18,6,18,14);
        }
        canvas.restoreToCount(save);
    }

    private void line(Canvas canvas,float... points) {
        Path path=new Path();
        path.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2) path.lineTo(points[i],points[i+1]);
        canvas.drawPath(path,paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
