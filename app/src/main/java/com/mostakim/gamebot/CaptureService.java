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
    static final int X0 = 60, X1 = 480, Y0 = 350, Y1 = 870, THR = 35;
    static final int NR = 4;
    final int gw = (X1 - X0) / STEP, gh = (Y1 - Y0) / STEP;
    int[][] ring = new int[NR][];
    int ringPos = 0, ringN = 0;
    int[] ref = null;

    int[] bx = new int[16];
    int[] by = new int[16];
    int nb = 0;

    Tracker tr = new Tracker();
    int cartX = -1;

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
        return Math.abs(cur[i] - ref[i]) > THR;
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
                if (cnt < 4 || cnt > 16) continue;
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

    int findCart(ByteBuffer buf, int rs, int ps) {
        int best = 0, cx = -1;
        for (int y = 1004; y <= 1016; y += 3) {
            int c = 0, mn = 9999, mx = -1;
            for (int x = 20; x < 520; x += 2) {
                int o = y * rs + x * ps;
                int r = buf.get(o) & 0xff;
                int g = buf.get(o + 1) & 0xff;
                int b = buf.get(o + 2) & 0xff;
                if (r > 180 && g < 100 && b > 40 && b < 170) {
                    c++;
                    if (x < mn) mn = x;
                    if (x > mx) mx = x;
                }
            }
            if (c > best && c >= 25) { best = c; cx = mn + mx; }
        }
        return cx;
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
        ref = (ringN >= NR) ? ring[ringPos] : null;

        if (Bot.running && ref != null) {
            long now = System.currentTimeMillis();
            cartX = findCart(buf, rs, ps);
            findBlobs(cur);
            int tg = tr.update(bx, by, nb, now);
            if (tg > 0) {
                int aim = Math.max(150, Math.min(930, tg));
                Link.target = aim;
                Bot.info = "লক্ষ্য x=" + aim + " cart=" + cartX
                    + " আইটেম=" + tr.nTracks + " গতি=" + (int) tr.vy
                    + "\n" + Link.state;
            } else {
                Bot.info = "কিছু দেখছি না cart=" + cartX + "\n" + Link.state;
            }
        } else if (!Bot.running) {
            tr = new Tracker();
            Bot.info = "থেমে আছে\n" + Link.state;
        }

        ring[ringPos] = cur;
        ringPos = (ringPos + 1) % NR;
        if (ringN < NR) ringN++;
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
