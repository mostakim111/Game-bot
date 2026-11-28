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

    static Button small(Context c, String t) {
        Button b = new Button(c);
        b.setText(t);
        b.setTextSize(11);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(24, 6, 24, 6);
        return b;
    }

    public void showOverlay() {
        if (overlay != null) return;
        final WindowManager wm =
            (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xCC000000);
        box.setPadding(8, 4, 8, 4);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);

        final TextView tv = new TextView(this);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(10);
        tv.setWidth(360);
        final Button go = small(this, "START");
        Button x = small(this, "CLOSE");
        final Button md = small(this, "CATCH");
        Button bm = small(this, "B-");
        Button bp = small(this, "B+");
        Button lm = small(this, "L-");
        Button lp = small(this, "L+");

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
        md.setOnClickListener(v -> {
            Bot.running = false;
            Bot.mode = (Bot.mode == 0) ? 1 : 0;
            go.setText("START");
        });
        bm.setOnClickListener(v -> { Bot.skyBias = Bot.skyBias - 15; });
        bp.setOnClickListener(v -> { Bot.skyBias = Bot.skyBias + 15; });
        lm.setOnClickListener(v -> {
            Bot.skyLat = Math.max(0.04, Bot.skyLat - 0.02);
        });
        lp.setOnClickListener(v -> {
            Bot.skyLat = Math.min(0.50, Bot.skyLat + 0.02);
        });
        x.setOnClickListener(v -> hideOverlay());

        row1.addView(tv);
        row1.addView(go);
        row1.addView(x);
        row2.addView(md);
        row2.addView(bm);
        row2.addView(bp);
        row2.addView(lm);
        row2.addView(lp);
        box.addView(row1);
        box.addView(row2);

        WindowManager.LayoutParams lp2 = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp2.gravity = Gravity.TOP | Gravity.START;
        lp2.x = 0;
        lp2.y = 100;
        wm.addView(box, lp2);
        overlay = box;

        final Handler hd = new Handler(Looper.getMainLooper());
        hd.post(new Runnable() {
            @Override
            public void run() {
                if (overlay == null) return;
                tv.setText(Bot.info + "\nB=" + (int) Bot.skyBias
                    + " L=" + (int) Math.round(Bot.skyLat * 1000) + "ms");
                md.setText(Bot.mode == 0 ? "CATCH" : "SKY");
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
