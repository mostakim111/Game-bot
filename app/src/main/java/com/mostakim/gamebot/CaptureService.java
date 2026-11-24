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

public class CaptureService extends Service {
    public static volatile String result = "";
    MediaProjection mp;
    VirtualDisplay vd;
    ImageReader ir;
    int frames = 0;
    long t0;
    Handler h = new Handler(Looper.getMainLooper());

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

        int w = 540, hh = 1170;
        ir = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2);
        vd = mp.createVirtualDisplay("cap", w, hh,
            getResources().getDisplayMetrics().densityDpi / 2,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir.getSurface(), null, h);

        frames = 0;
        t0 = System.currentTimeMillis();
        result = "চলছে...";
        ir.setOnImageAvailableListener(r -> {
            Image im = r.acquireLatestImage();
            if (im != null) { frames++; im.close(); }
            long el = System.currentTimeMillis() - t0;
            if (el >= 6000) {
                result = frames + " ফ্রেম / " + el + "ms ≈ "
                    + (frames * 1000L / el) + " fps";
                stopSelf();
            }
        }, h);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (vd != null) vd.release();
        if (ir != null) ir.close();
        if (mp != null) mp.stop();
    }
}
