package com.mostakim.gamebot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;

public class BotService extends AccessibilityService {
    public static BotService instance;

    public interface ShotCb { void done(boolean ok); }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {}

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent i) {
        instance = null;
        return super.onUnbind(i);
    }

    public void swipe(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, ms));
        dispatchGesture(b.build(), null, null);
    }

    public void shot(final ShotCb cb) {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
            new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult r) {
                    try {
                        Bitmap hw = Bitmap.wrapHardwareBuffer(
                            r.getHardwareBuffer(), r.getColorSpace());
                        Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hw.recycle();
                    } catch (Throwable t) {}
                    r.getHardwareBuffer().close();
                    cb.done(true);
                }

                @Override
                public void onFailure(int code) {
                    cb.done(false);
                }
            });
    }
}
