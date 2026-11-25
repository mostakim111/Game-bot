package com.mostakim.gamebot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.nio.ByteBuffer;

public class CaptureService extends Service {
    MediaProjection mp;
    VirtualDisplay vd;
    ImageReader ir;
    Handler h = new Handler(Looper.getMainLooper());

    static final int W = 540, H = 1170, STEP = 6;
    static final int X0 = 60, X1 = 480, Y0 = 350, Y1 = 880, THR = 40;
    static final int CATCH_Y = 2000;
    final int gw = (X1 - X0) / STEP, gh = (Y1 - Y0) / STEP;
    int[] prev = new int[gw * gh];
    boolean hasPrev = false;

    int lastRow = -1;
    int lastX = -1;
    long lastT = 0;
    double vy = 500;
    long holdUntil = 0;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            "cap", "Capture", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "cap")
            .setContentTitle("GameBot capture")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

        int code = in.getIntExtra("code", 0);
        Intent data = in.getParcelableExtra("data");
        MediaProjectionManager m = getSystemService(MediaProjectionManager.class);
        mp = m.getMediaProjection(code, data);
        mp.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {}
        }, h);

        ir = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2);
        vd = mp.createVirtualDisplay("cap", W, H,
            getResources().getDisplayMetrics().densityDpi / 2,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir.getSurface(), null, h);

        Link.target = -1;
        Link.start();

        ir.setOnImageAvailableListener(r -> {
            Image im = r.acquireLatestImage();
            if (im == null) return;
            try { process(im); } finally { im.close(); }
        }, h);
        return START_NOT_STICKY;
    }

    boolean chg(int[] cur, int gy, int gx) {
        int i = gy * gw + gx;
        return Math.abs(cur[i] - prev[i]) > THR;
    }

    void arm(long now) {
        double eta = (CATCH_Y - (Y0 + lastRow * STEP) * 2) / vy * 1000 + 100;
        if (eta < 200) eta = 200;
        if (eta > 1000) eta = 1000;
        holdUntil = now + (long) eta;
    }

    void process(Image im) {
        Image.Plane p = im.getPlanes()[0];
        ByteBuffer buf = p.getBuffer();
        int rs = p.getRowStride(), ps = p.getPixelStride();
        int[] cur = new int[gw * gh];
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int x = X0 + gx * STEP, y = Y0 + gy * STEP;
                int o = y * rs + x * ps;
                int r = buf.get(o) & 0xff;
                int g = buf.get(o + 1) & 0xff;
                int b = buf.get(o + 2) & 0xff;
                cur[gy * gw + gx] = (r * 3 + g * 6 + b) / 10;
            }
        }

        if (Bot.running && hasPrev) {
            long now = System.currentTimeMillis();
            int refX = Link.target > 0 ? Link.target : 540;
            int found = -1, fx = -1;
            for (int gy = gh - 1; gy >= 0 && found < 0; gy--) {
                int bestX = -1, bestD = Integer.MAX_VALUE;
                int gx = 0;
                while (gx < gw) {
                    if (!chg(cur, gy, gx)) { gx++; continue; }
                    int cnt = 0, sum = 0, last = gx;
                    while (gx < gw && gx - last <= 2) {
                        if (chg(cur, gy, gx)) { cnt++; sum += gx; last = gx; }
                        gx++;
                    }
                    if (cnt >= 4) {
                        int x = (X0 + (sum / cnt) * STEP) * 2;
                        int d = Math.abs(x - refX);
                        if (d < bestD) { bestD = d; bestX = x; }
                    }
                }
                if (bestX >= 0) { found = gy; fx = bestX; }
            }

            if (found >= 0) {
                int tx = fx;
                if (tx < 150) tx = 150;
                if (tx > 930) tx = 930;
                int ty = (Y0 + found * STEP) * 2;
                if (lastRow >= 0 && now > lastT && Math.abs(tx - lastX) < 80) {
                    int dy = ty - (Y0 + lastRow * STEP) * 2;
                    if (dy > 0 && dy < 120) {
                        double v = dy * 1000.0 / (now - lastT);
                        if (v > 100 && v < 3000) vy = vy * 0.8 + v * 0.2;
                    }
                }
                if (lastRow >= gh - 10 && found < lastRow - 12) arm(now);
                lastRow = found;
                lastX = tx;
                lastT = now;
                if (now >= holdUntil) Link.target = tx;
                Bot.info = "আইটেম x=" + tx
                    + (now < holdUntil ? " (ধরে রাখছি)" : "")
                    + "\n" + Link.state;
            } else {
                if (lastRow >= gh - 10) arm(now);
                lastRow = -1;
                Bot.info = "কিছু দেখছি না\n" + Link.state;
            }
        } else if (!Bot.running) {
            lastRow = -1;
            holdUntil = 0;
            Bot.info = "থেমে আছে\n" + Link.state;
        }
        prev = cur;
        hasPrev = true;
    }

    @Override
    public void onDestroy() {
        Bot.running = false;
        Link.shutdown();
        if (vd != null) vd.release();
        if (ir != null) ir.close();
        if (mp != null) mp.stop();
    }
}
