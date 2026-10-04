// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.io.InputStream;

/** Setup's shared scene; decorations are original transparent Figma exports. */
final class FigmaProductBackdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] glow=new Bitmap[3];
    FigmaProductBackdrop(Context context) {
        super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        String[] names={"setup-glow-b.png","setup-glow-a.png","setup-glow-depth.png"};
        for(int i=0;i<names.length;i++) {
            try(InputStream stream=context.getAssets().open("product-ui/"+names[i])) {
                glow[i]=BitmapFactory.decodeStream(stream);
            } catch(java.io.IOException error) {throw new IllegalStateException("Missing Setup asset",error);}
            if(glow[i]==null)throw new IllegalStateException("Invalid Setup asset");
        }
    }
    @Override protected void onDraw(Canvas canvas) {
        paint.setShader(new LinearGradient(0,0,getWidth(),0,0xff071019,0xff0b1720,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);
        float scale=Math.min(getWidth()/2560f,getHeight()/1600f);
        canvas.save();canvas.translate((getWidth()-2560*scale)/2,(getHeight()-1600*scale)/2);canvas.scale(scale,scale);
        canvas.drawBitmap(glow[0],null,new RectF(-121,929,519,1569),paint);
        canvas.drawBitmap(glow[1],null,new RectF(1549,519,2449,1419),paint);
        canvas.drawBitmap(glow[2],null,new RectF(759,249,1709,1199),paint);
        paint.setColor(0xe634e38b);canvas.drawRoundRect(151,89,671,95,3,3,paint);
        paint.setColor(0xa659b8ff);canvas.drawRoundRect(679,89,829,95,3,3,paint);
        canvas.restore();
    }
}
