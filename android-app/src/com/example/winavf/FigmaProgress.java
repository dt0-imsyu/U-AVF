// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Activity indication for operations without a meaningful percentage. */
final class FigmaProgress extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private boolean complete;

    FigmaProgress(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void complete(boolean complete) {
        if(this.complete==complete)return;
        this.complete=complete;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float width=getWidth(),height=getHeight(),radius=height/2;
        rect.set(0,0,width,height);
        paint.setColor(0xff34363b);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setColor(0xff0a84ff);
        boolean animate=!complete && ValueAnimator.areAnimatorsEnabled();
        float segment=complete?width:width*0.28f;
        float phase=animate?(SystemClock.uptimeMillis()%1260)/1260f:0.5f;
        float left=complete?0:(width+segment)*phase-segment;
        rect.set(Math.max(0,left),0,Math.min(width,left+segment),height);
        canvas.drawRoundRect(rect,radius,radius,paint);
        if(animate && isShown())postInvalidateOnAnimation();
    }
}
