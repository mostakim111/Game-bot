package com.mostakim.gamebot;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(40, 200, 40, 40);

        status = new TextView(this);
        status.setTextSize(20);

        Button a = new Button(this);
        a.setText("1. Accessibility settings খোলো");
        a.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button t = new Button(this);
        t.setText("2. Test: Home + swipe");
        t.setOnClickListener(v -> {
            BotService s = BotService.instance;
            if (s == null) {
                status.setText("Service চালু নেই");
                return;
            }
            status.setText("পরীক্ষা চলছে...");
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            new Handler(Looper.getMainLooper()).postDelayed(
                () -> s.swipe(900, 1200, 200, 1200, 300), 1000);
        });

        Button c = new Button(this);
        c.setText("3. Screenshot গতি পরীক্ষা (২০ বার)");
        c.setOnClickListener(v -> {
            BotService s = BotService.instance;
            if (s == null) {
                status.setText("Service চালু নেই");
                return;
            }
            final int N = 20;
            final int[] n = {0, 0};
            final long t0 = System.currentTimeMillis();
            status.setText("মাপা হচ্ছে...");
            BotService.ShotCb cb = new BotService.ShotCb() {
                @Override
                public void done(boolean ok) {
                    n[0]++;
                    if (ok) n[1]++;
                    if (n[0] < N) {
                        s.shot(this);
                    } else {
                        long ms = System.currentTimeMillis() - t0;
                        status.setText("সফল " + n[1] + "/" + N
                            + ", মোট " + ms + "ms, প্রতি সফল ফ্রেম ≈ "
                            + (n[1] > 0 ? ms / n[1] : 0) + "ms");
                    }
                }
            };
            s.shot(cb);
        });

        l.addView(status);
        l.addView(a);
        l.addView(t);
        l.addView(c);
        setContentView(l);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(BotService.instance != null
            ? "Service চালু আছে" : "Service চালু নেই");
    }
}
