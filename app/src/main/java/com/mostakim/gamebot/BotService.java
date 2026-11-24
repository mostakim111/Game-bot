package com.mostakim.gamebot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BotService extends AccessibilityService {
    public static BotService instance;
    View overlay;

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
        hideOverlay();
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

    public void showOverlay() {
        if (overlay != null) return;
        final WindowManager wm =
            (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xCC000000);
        box.setPadding(16, 16, 16, 16);

        final TextView tv = new TextView(this);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        final Button go = new Button(this);
        go.setText("START");
        Button x = new Button(this);
        x.setText("CLOSE");

        go.setOnClickListener(v -> {
            if (Bot.running) {
                Bot.running = false;
                Bot.info = "থেমে আছে";
                go.setText("START");
            } else {
                Bot.startTime = System.currentTimeMillis();
                Bot.running = true;
                go.setText("STOP");
            }
        });
        x.setOnClickListener(v -> hideOverlay());

        box.addView(tv);
        box.addView(go);
        box.addView(x);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 1100;
        wm.addView(box, lp);
        overlay = box;

        final Handler hd = new Handler(Looper.getMainLooper());
        hd.post(new Runnable() {
            @Override
            public void run() {
                if (overlay == null) return;
                tv.setText(Bot.info);
                if (!Bot.running) go.setText("START");
                hd.postDelayed(this, 200);
            }
        });
    }

    public void hideOverlay() {
        Bot.running = false;
        if (overlay != null) {
            WindowManager wm =
                (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            wm.removeView(overlay);
            overlay = null;
        }
        stopService(new Intent(this, CaptureService.class));
    }
}
