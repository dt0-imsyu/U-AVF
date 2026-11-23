// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.animation.ValueAnimator;
import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Draws the switch from the committed preference. */
final class ToggleView extends View {
    private static final ArgbEvaluator TRACK_COLORS = new ArgbEvaluator();
    private final int accent;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track=new RectF();
    private float amount;
    private ValueAnimator animation;

    ToggleView(Context context,boolean enabled) {
        super(context);
        accent=ProductAppearance.accent(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        amount=enabled?1:0;
    }

    void checked(boolean value) {
        float target=value?1:0;
        if(animation!=null)animation.cancel();
        if(!isShown() || !ValueAnimator.areAnimatorsEnabled() || amount==target) {
            amount=target;
            invalidate();
            return;
        }
        animation=ValueAnimator.ofFloat(amount,target);
        animation.setDuration(160);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(valueAnimator->{
            amount=(float)valueAnimator.getAnimatedValue();
            invalidate();
        });
        animation.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float width=getWidth(),height=getHeight();
        track.set(0,0,width,height);
        paint.setColor((int)TRACK_COLORS.evaluate(amount,0xff34363b,accent));
        canvas.drawRoundRect(track,height/2,height/2,paint);
        paint.setColor(0xfff5f5f7);
        canvas.drawCircle(height/2+(width-height)*amount,height/2,height*22/56,paint);
    }

    @Override protected void onDetachedFromWindow() {
        if(animation!=null)animation.cancel();
        super.onDetachedFromWindow();
    }
}
