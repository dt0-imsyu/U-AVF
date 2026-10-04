// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.animation.ValueAnimator;

/** Native vector geometry with a stationary cube and independently rotating rings. */
final class RuntimeOrbitalView extends View {
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator spin;
    private float outerAngle,innerAngle;
    RuntimeOrbitalView(Context context) {super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    protected void onDraw(Canvas c) {
        super.onDraw(c);c.save();float scale=Math.min(getWidth(),getHeight())/330f;c.scale(scale,scale);
        pen.setStyle(Paint.Style.STROKE);pen.setColor(0xff34e38b);pen.setStrokeWidth(8);
        pen.setPathEffect(new DashPathEffect(new float[]{42,22},0));
        c.save();c.rotate(outerAngle,165,165);c.drawCircle(165,165,117.333f,pen);c.restore();
        pen.setColor(0x8c59b8ff);pen.setStrokeWidth(4);pen.setPathEffect(new DashPathEffect(new float[]{12,16},0));
        c.save();c.rotate(innerAngle,165,165);c.drawCircle(165,165,84.333f,pen);c.restore();
        pen.setPathEffect(null);pen.setStrokeWidth(7);pen.setColor(0xffeaf3f9);
        Path cube=new Path();cube.moveTo(165,106.333f);cube.lineTo(216.333f,135.666f);cube.lineTo(216.333f,194.333f);cube.lineTo(165,223.666f);cube.lineTo(113.666f,194.333f);cube.lineTo(113.666f,135.666f);cube.close();c.drawPath(cube,pen);
        pen.setStrokeWidth(5);pen.setAlpha(204);Path edges=new Path();edges.moveTo(165,106.333f);edges.lineTo(165,223.666f);edges.moveTo(165,165);edges.lineTo(216.333f,135.666f);edges.moveTo(165,165);edges.lineTo(113.666f,135.666f);c.drawPath(edges,pen);
        pen.setStyle(Paint.Style.FILL);pen.setColor(0xff34e38b);c.drawCircle(260,70,11,pen);c.restore();
    }
    protected void onVisibilityChanged(View changed,int visibility) {super.onVisibilityChanged(changed,visibility);updateMotion();}
    protected void onAttachedToWindow() {super.onAttachedToWindow();updateMotion();}
    protected void onWindowVisibilityChanged(int visibility) {super.onWindowVisibilityChanged(visibility);updateMotion();}
    protected void onDetachedFromWindow() {if(spin!=null) {spin.cancel();spin=null;}super.onDetachedFromWindow();}
    private void updateMotion() {
        if(spin!=null) {spin.cancel();spin=null;}
        if(isAttachedToWindow() && isShown() && getWindowVisibility()==VISIBLE && ValueAnimator.areAnimatorsEnabled()) {
            // 52 seconds is a whole number of both 4s and 6.5s revolutions.
            spin=ValueAnimator.ofFloat(0,1);spin.setDuration(52000);spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new android.view.animation.LinearInterpolator());
            spin.addUpdateListener(animation -> {float phase=(float)animation.getAnimatedValue();outerAngle=phase*4680;innerAngle=-phase*2880;invalidate();});
            spin.start();
        }
    }
}
