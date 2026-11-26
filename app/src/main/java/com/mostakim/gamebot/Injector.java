package com.mostakim.gamebot;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Injector {
    static Object mgr;
    static Method inject;
    static final int TAP_MS = 35;

    static void init() throws Exception {
        try {
            Class<?> c = Class.forName("android.hardware.input.InputManagerGlobal");
            mgr = c.getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            Class<?> c = Class.forName("android.hardware.input.InputManager");
            mgr = c.getMethod("getInstance").invoke(null);
        }
        inject = mgr.getClass().getMethod("injectInputEvent",
            InputEvent.class, int.class);
    }

    static void send(int action, long down, float x, float y) throws Exception {
        long now = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(down, now, action, x, y,
            1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject.invoke(mgr, e, 0);
        e.recycle();
    }

    static void tap(float x, float y) throws Exception {
        long down = SystemClock.uptimeMillis();
        send(MotionEvent.ACTION_DOWN, down, x, y);
        Thread.sleep(TAP_MS);
        send(MotionEvent.ACTION_UP, down, x, y);
    }

    public static void main(String[] args) {
        try {
            init();
            if (args.length > 0 && args[0].equals("test")) {
                System.out.println("৮ সেকেন্ডে Cart Catch-এ গিয়ে খেলা শুরু করো...");
                Thread.sleep(8000);
                float[] xs = {200, 880, 540, 200, 880, 540};
                for (float x : xs) {
                    System.out.println("tap x=" + (int) x);
                    tap(x, 2130);
                    Thread.sleep(1000);
                }
                System.out.println("শেষ");
                return;
            }
            ServerSocket ss = new ServerSocket(7777, 1,
                InetAddress.getByName("127.0.0.1"));
            System.out.println("সার্ভার চালু (পোর্ট 7777)। বন্ধ করতে CTRL+C");
            while (true) {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                System.out.println("অ্যাপ যুক্ত হয়েছে");
                try {
                    BufferedReader r = new BufferedReader(
                        new InputStreamReader(s.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) {
                        String last = line;
                        while (r.ready()) {
                            String l2 = r.readLine();
                            if (l2 == null) break;
                            last = l2;
                        }
                        String[] p = last.trim().split(" ");
                        if (p.length < 3 || !p[0].equals("T")) continue;
                        tap(Float.parseFloat(p[1]), Float.parseFloat(p[2]));
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                s.close();
                System.out.println("অ্যাপ বিচ্ছিন্ন");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
