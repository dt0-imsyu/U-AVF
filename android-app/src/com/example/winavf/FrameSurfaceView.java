// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.MotionEvent;


/** App-owned presentation surface for decoded guest frames. */
final class FrameSurfaceView extends View {
    interface InputListener {
        void onPointer(int x, int y, int action);
        void onMouseButton(int button, boolean pressed);
        void onScroll(int horizontal, int vertical);
        default void onRelativePointer(int dx, int dy) { }
        default void onPointerCaptureChanged(boolean captured) { }
    }

    private Bitmap bitmap;
    private boolean encodedMode;
    private int encodedWidth, encodedHeight;
    synchronized void setEncodedMode(boolean enabled, int width, int height) {
        encodedMode=enabled; encodedWidth=width; encodedHeight=height;
        setBackgroundColor(enabled ? Color.TRANSPARENT : Color.BLACK);
        invalidate();
    }
    private int imageWidth() { return encodedMode ? encodedWidth : bitmap.getWidth(); }
    private int imageHeight() { return encodedMode ? encodedHeight : bitmap.getHeight(); }
    private int[] argb;
    private InputListener inputListener;
    private boolean captureRequested;
    private int capturedButtons;
    private long preparedFrames;
    private long drawnFrames;
    private long lastDrawnGeneration;
    private long prepareNanos;
    private long drawNanos;

    void setInputListener(InputListener listener) {
        inputListener = listener;
    }

    void capturePointer() {
        captureRequested = true;
        requestFocus();
        requestPointerCaptureWhenReady();
    }

    boolean isPointerCaptured() {
        return android.os.Build.VERSION.SDK_INT >= 26 && hasPointerCapture();
    }

    void releaseCapturedPointer() {
        captureRequested = false;
        if (android.os.Build.VERSION.SDK_INT >= 26 && isPointerCaptured()) releasePointerCapture();
    }

    private void requestPointerCaptureWhenReady() {
        if (android.os.Build.VERSION.SDK_INT < 26) return;
        post(() -> {
            if (captureRequested && isAttachedToWindow() && hasWindowFocus() && isFocused()
                    && !hasPointerCapture()) requestPointerCapture();
        });
    }

    FrameSurfaceView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) requestPointerCaptureWhenReady();
    }

    @Override protected void onFocusChanged(boolean focused, int direction,
            android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) requestPointerCaptureWhenReady();
    }

    synchronized void present(ConsoleFrameDecoder.Frame frame) {
        long started = System.nanoTime();
        if (bitmap == null || bitmap.getWidth() != frame.width || bitmap.getHeight() != frame.height) {
            bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888);
            argb = null;
        }
        byte[] bgra = frame.bgra;
        long dirtyArea = 0;
        if (!frame.fullFrame && frame.dirtyRects != null) {
            for (int i = 0; i < frame.dirtyRectCount; i++) {
                int offset = i * 4;
                dirtyArea += (long) frame.dirtyRects[offset + 2] * frame.dirtyRects[offset + 3];
            }
        }
        boolean fullCopy = frame.fullFrame || frame.dirtyRects == null || frame.dirtyRectCount == 0
                || dirtyArea * 3 >= (long) frame.width * frame.height;
        if (!fullCopy) {
            for (int i = 0; i < frame.dirtyRectCount; i++) {
                int offset = i * 4;
                int x = frame.dirtyRects[offset];
                int y = frame.dirtyRects[offset + 1];
                int width = frame.dirtyRects[offset + 2];
                int height = frame.dirtyRects[offset + 3];
                int pixels = width * height;
                if (argb == null || argb.length < pixels) argb = new int[pixels];
                for (int row = 0; row < height; row++) {
                    int source = ((y + row) * frame.width + x) * 4;
                    int end = source + width * 4;
                    int pixel = row * width;
                    for (int p = source; p < end; p += 4) {
                        int b = bgra[p] & 0xff;
                        int g = bgra[p + 1] & 0xff;
                        int r = bgra[p + 2] & 0xff;
                        int a = bgra[p + 3] & 0xff;
                        argb[pixel++] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                bitmap.setPixels(argb, 0, width, x, y, width, height);
            }
        } else {
            // Convert protocol BGRA explicitly. Bitmap's native byte layout
            // is not a portable contract for copyPixelsFromBuffer across
            // Android graphics implementations; assuming it matches BGRA
            // can produce channel/alpha artifacts on some devices.
            if (argb == null || argb.length != frame.width * frame.height)
                argb = new int[frame.width * frame.height];
            boolean copied = WavfNative.AVAILABLE && WavfNative.copyPixels(bgra, argb);
            for (int i = 0, p = 0; !copied && i < argb.length; i++, p += 4) {
                int b = bgra[p] & 0xff;
                int g = bgra[p + 1] & 0xff;
                int r = bgra[p + 2] & 0xff;
                int a = bgra[p + 3] & 0xff;
                argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            bitmap.setPixels(argb, 0, frame.width, 0, 0, frame.width, frame.height);
        }
        preparedFrames++;
        prepareNanos += System.nanoTime() - started;
        postInvalidateOnAnimation();
    }

    synchronized void clearFrame() {
        bitmap = null;
        argb = null;
        preparedFrames = 0;
        drawnFrames = 0;
        lastDrawnGeneration = 0;
        prepareNanos = 0;
        drawNanos = 0;
        postInvalidateOnAnimation();
    }

    synchronized long[] performanceSnapshot() {
        return new long[] {preparedFrames, drawnFrames, prepareNanos, drawNanos};
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        long started = System.nanoTime();
        super.onDraw(canvas);
        if (encodedMode) return;
        if (bitmap == null) return;
        float scale = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        float left = (getWidth() - bitmap.getWidth() * scale) / 2f;
        float top = (getHeight() - bitmap.getHeight() * scale) / 2f;
        canvas.drawBitmap(bitmap, null,
                new android.graphics.RectF(left, top, left + bitmap.getWidth() * scale,
                        top + bitmap.getHeight() * scale), null);
        if (lastDrawnGeneration != preparedFrames) {
            lastDrawnGeneration = preparedFrames;
            drawnFrames++;
            drawNanos += System.nanoTime() - started;
        }
    }

    @Override public synchronized boolean onTouchEvent(MotionEvent event) {
        if ((!encodedMode && bitmap == null) || inputListener == null) return false;
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return false;
        float scale = Math.min(getWidth() / (float) imageWidth(), getHeight() / (float) imageHeight());
        float left = (getWidth() - imageWidth() * scale) / 2f;
        float top = (getHeight() - imageHeight() * scale) / 2f;
        int x = (int) ((event.getX() - left) / scale);
        int y = (int) ((event.getY() - top) / scale);
        if (action == MotionEvent.ACTION_DOWN &&
                (x < 0 || y < 0 || x >= imageWidth() || y >= imageHeight())) return false;
        x = Math.max(0, Math.min(imageWidth() - 1, x));
        y = Math.max(0, Math.min(imageHeight() - 1, y));
        // Mouse button transitions arrive through onGenericMotionEvent; do
        // not turn a right click into a simultaneous left click here.
        inputListener.onPointer(x, y, event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)
                ? MotionEvent.ACTION_MOVE : action);
        return true;
    }

    @Override public synchronized boolean onGenericMotionEvent(MotionEvent event) {
        if ((!encodedMode && bitmap == null) || inputListener == null || !event.isFromSource(android.view.InputDevice.SOURCE_MOUSE))
            return super.onGenericMotionEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_SCROLL) {
            int horizontal = Math.round(event.getAxisValue(MotionEvent.AXIS_HSCROLL));
            int vertical = Math.round(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
            if (horizontal != 0 || vertical != 0) inputListener.onScroll(horizontal, vertical);
            return true;
        }
        if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            int button = event.getActionButton();
            int xButton = button == MotionEvent.BUTTON_PRIMARY ? 1
                    : button == MotionEvent.BUTTON_TERTIARY ? 2
                    : button == MotionEvent.BUTTON_SECONDARY ? 3 : 0;
            if (xButton == 0) return false;
            inputListener.onMouseButton(xButton, action == MotionEvent.ACTION_BUTTON_PRESS);
            return true;
        }
        if (action == MotionEvent.ACTION_HOVER_MOVE) {
            float scale = Math.min(getWidth() / (float) imageWidth(), getHeight() / (float) imageHeight());
            float left = (getWidth() - imageWidth() * scale) / 2f;
            float top = (getHeight() - imageHeight() * scale) / 2f;
            int x = Math.max(0, Math.min(imageWidth() - 1, (int) ((event.getX() - left) / scale)));
            int y = Math.max(0, Math.min(imageHeight() - 1, (int) ((event.getY() - top) / scale)));
            inputListener.onPointer(x, y, MotionEvent.ACTION_MOVE);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public boolean onCapturedPointerEvent(MotionEvent event) {
        if (inputListener == null) return false;
        int action = event.getActionMasked();
        int buttons = event.getButtonState();
        // Captured mouse events use SOURCE_MOUSE_RELATIVE and do not pass
        // through onGenericMotionEvent. Handle all button transitions here.
        if (action == MotionEvent.ACTION_BUTTON_PRESS) buttons |= event.getActionButton();
        if (action == MotionEvent.ACTION_BUTTON_RELEASE) buttons &= ~event.getActionButton();
        if (action == MotionEvent.ACTION_CANCEL) buttons = 0;
        updateCapturedButtons(buttons);
        if (action == MotionEvent.ACTION_MOVE) {
            inputListener.onRelativePointer(Math.round(event.getX()), Math.round(event.getY()));
        } else if (action == MotionEvent.ACTION_SCROLL) {
            int horizontal = Math.round(event.getAxisValue(MotionEvent.AXIS_HSCROLL));
            int vertical = Math.round(event.getAxisValue(MotionEvent.AXIS_VSCROLL));
            if (horizontal != 0 || vertical != 0) inputListener.onScroll(horizontal, vertical);
        }
        return true;
    }

    private void updateCapturedButtons(int buttons) {
        int changed = capturedButtons ^ buttons;
        int[] androidButtons = {MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_TERTIARY,
                MotionEvent.BUTTON_SECONDARY};
        for (int i = 0; i < androidButtons.length; i++) {
            int mask = androidButtons[i];
            if ((changed & mask) != 0 && inputListener != null)
                inputListener.onMouseButton(i + 1, (buttons & mask) != 0);
        }
        capturedButtons = buttons;
    }

    @Override public void onPointerCaptureChange(boolean hasCapture) {
        super.onPointerCaptureChange(hasCapture);
        if (!hasCapture) updateCapturedButtons(0);
        if (inputListener != null) inputListener.onPointerCaptureChanged(hasCapture);
    }
}
