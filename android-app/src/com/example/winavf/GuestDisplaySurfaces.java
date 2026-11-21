// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.FrameLayout;

/** Owns the replaceable Android surfaces for the embedded Windows and Ubuntu displays. */
final class GuestDisplaySurfaces {
    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1200;
    private final FrameLayout viewport;
    private final FrameSurfaceView fallback;
    private volatile SurfaceView encodedSurface;
    private EncodedDisplayProbe decoder;
    private volatile SurfaceView mavkSurface;
    private MavkDisplayRuntime renderer;

    GuestDisplaySurfaces(FrameLayout viewport, FrameSurfaceView fallback) {
        this.viewport = viewport;
        this.fallback = fallback;
        viewport.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            fitEncoded();
            fitMavk();
        });
    }

    EncodedDisplayProbe decoder() { return decoder; }
    MavkDisplayRuntime renderer() { return renderer; }
    boolean ownsEncoded(SurfaceView surface) { return encodedSurface == surface; }
    boolean ownsMavk(SurfaceView surface) { return mavkSurface == surface; }

    SurfaceView openEncoded() {
        closeEncoded();
        return encodedSurface = addSurface();
    }

    SurfaceView openMavk() {
        closeMavk();
        return mavkSurface = addSurface();
    }

    void useDecoder(EncodedDisplayProbe decoder) { this.decoder = decoder; }
    void useRenderer(MavkDisplayRuntime renderer) { this.renderer = renderer; }

    void resumeDecoder() {
        if (decoder != null) decoder.resumeSurface();
    }

    void suspendDecoder() {
        if (decoder != null) decoder.suspendSurface();
    }

    void closeEncoded() {
        EncodedDisplayProbe oldDecoder = decoder;
        SurfaceView oldSurface = encodedSurface;
        // Retire the old surface before closing it so late decoder callbacks cannot use it.
        decoder = null;
        encodedSurface = null;
        if (oldDecoder != null) oldDecoder.closeSilently();
        if (oldSurface != null) viewport.removeView(oldSurface);
    }

    private void closeMavk() {
        MavkDisplayRuntime oldRenderer = renderer;
        SurfaceView oldSurface = mavkSurface;
        renderer = null;
        mavkSurface = null;
        if (oldRenderer != null) oldRenderer.close();
        if (oldSurface != null) viewport.removeView(oldSurface);
    }

    void close() {
        closeMavk();
        closeEncoded();
        fallback.setEncodedMode(false, 0, 0);
    }

    void fitEncoded() {
        fit(encodedSurface, decoder == null ? DEFAULT_WIDTH : decoder.streamWidth(),
                decoder == null ? DEFAULT_HEIGHT : decoder.streamHeight());
    }

    void fitMavk() {
        fit(mavkSurface, renderer == null ? DEFAULT_WIDTH : renderer.frameWidth(),
                renderer == null ? DEFAULT_HEIGHT : renderer.frameHeight());
    }

    private SurfaceView addSurface() {
        var surface = new SurfaceView(viewport.getContext());
        viewport.addView(surface, 0, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        return surface;
    }

    private void fit(SurfaceView surface, int sourceWidth, int sourceHeight) {
        if (surface == null || sourceWidth <= 0 || sourceHeight <= 0) return;
        int width = (int) Math.min(viewport.getWidth(), (long) viewport.getHeight() * sourceWidth / sourceHeight);
        int height = (int) ((long) width * sourceHeight / sourceWidth);
        var layout = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (width > 0 && height > 0 && (layout.width != width || layout.height != height)) {
            layout.width = width;
            layout.height = height;
            surface.setLayoutParams(layout);
        }
    }
}
