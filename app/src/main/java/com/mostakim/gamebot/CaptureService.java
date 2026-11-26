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
    static final int X0 = 60, X1 = 480, Y0 = 350, Y1 = 900, THR = 40;
    static final int CATCH_Y = 2040;
    final int gw = (X1 - X0) / STEP, gh = (Y1 - Y0) / STEP;
    int[] prev = new int[gw * gh];
    boolean hasPrev = false;

    int[] bx = new int[16];
    int[] by = new int[16];
    int nb = 0;

    boolean tracking = false, lost = false;
    int tx = 540, ty = 0, x0 = 0, y0 = 0;
    long releaseAt = 0, t0 = 0;
    double vy = 500, vx = 0;

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

    void findBlobs(int[] cur) {
        nb = 0;
        for (int gy = gh - 1; gy >= 0; gy--) {
            int gx = 0;
            while (gx < gw) {
                if (!chg(cur, gy, gx)) { gx++; continue; }
                int cnt = 0, sum = 0, last = gx;
                while (gx < gw && gx - last <= 2) {
                    if (chg(cur, gy, gx)) { cnt++; sum += gx; last = gx; }
                    gx++;
                }
                if (cnt < 4) continue;
                int x = (X0 + (sum / cnt) * STEP) * 2;
                int y = (Y0 + gy * STEP) * 2;
                boolean same = false;
                for (int k = 0; k < nb; k++) {
                    if (Math.abs(bx[k] - x) < 140 && by[k] - y <= 120) {
                        same = true;
                        break;
                    }
                }
                if (!same && nb < 16) {
                    bx[nb] = x;
                    by[nb] = y;
                    nb++;
                }
            }
        }
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
            findBlobs(cur);

            if (tracking && now - t0 > 2500) {
                tracking = false;
                lost = false;
            }

            if (tracking) {
                int bi = -1;
                for (int k = 0; k < nb; k++) {
                    if (Math.abs(bx[k] - tx) < 130
                            && by[k] >= ty - 60 && by[k] <= ty + 240) {
                        if (bi < 0 || by[k] > by[bi]) bi = k;
                    }
                }
                if (bi >= 0) {
                    tx = bx[bi];
                    ty = by[bi];
                    lost = false;
                    long span = now - t0;
                    if (span > 200 && ty - y0 > 40) {
                        vy = Math.max(150, Math.min(3000, (ty - y0) * 1000.0 / span));
                        vx = (tx - x0) * 1000.0 / span;
                    }
                } else {
                    if (!lost) {
                        lost = true;
                        double e = (CATCH_Y - ty) / vy * 1000 + 120;
                        e = Math.max(200, Math.min(1000, e));
                        releaseAt = now + (long) e;
                    }
                    if (now >= releaseAt) {
                        tracking = false;
                        lost = false;
                    }
                }
            }

            if (!tracking) {
                int best = -1;
                for (int k = 0; k < nb; k++) {
                    if (by[k] < CATCH_Y - 120 && (best < 0 || by[k] > by[best])) {
                        best = k;
                    }
                }
                if (best >= 0) {
                    tracking = true;
                    lost = false;
                    tx = bx[best];
                    ty = by[best];
                    x0 = tx;
                    y0 = ty;
                    t0 = now;
                    vx = 0;
                }
            }

            if (tracking) {
                double eta = Math.max(0, Math.min(1.2, (CATCH_Y - ty) / vy));
                double drift = Math.max(-150, Math.min(150, vx * eta));
                int aim = (int) (tx + drift);
                aim = Math.max(150, Math.min(930, aim));
                Link.target = aim;
                Bot.info = "লক্ষ্য x=" + aim + " গতি=" + (int) vy
                    + (lost ? " (হারিয়েছে, ধরে আছি)" : "")
                    + "\n" + Link.state;
            } else {
                Bot.info = "কিছু দেখছি না\n" + Link.state;
            }
        } else if (!Bot.running) {
            tracking = false;
            lost = false;
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
