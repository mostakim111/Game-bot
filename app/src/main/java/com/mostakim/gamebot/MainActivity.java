package com.mostakim.gamebot;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
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
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
            new Handler(Looper.getMainLooper()).postDelayed(
                () -> s.swipe(900, 1200, 200, 1200, 300), 1000);
        });

        Button c = new Button(this);
        c.setText("3. স্ট্রিম গতি পরীক্ষা (৬ সেকেন্ড)");
        c.setOnClickListener(v -> {
            CaptureService.result = "";
            MediaProjectionManager m = getSystemService(MediaProjectionManager.class);
            startActivityForResult(m.createScreenCaptureIntent(), 1);
        });

        l.addView(status);
        l.addView(a);
        l.addView(t);
        l.addView(c);
        setContentView(l);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && res == RESULT_OK) {
            Intent i = new Intent(this, CaptureService.class);
            i.putExtra("code", res);
            i.putExtra("data", data);
            startForegroundService(i);
            status.setText("চালু। এখন গেমে বা ভিডিওতে যাও, ৬ সেকেন্ড পর ফিরে এসো।");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!CaptureService.result.isEmpty()) {
            status.setText(CaptureService.result);
        } else {
            status.setText(BotService.instance != null
                ? "Service চালু আছে" : "Service চালু নেই");
        }
    }
}
