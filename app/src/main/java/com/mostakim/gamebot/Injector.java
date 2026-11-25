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
    static long downTime = 0;
    static volatile boolean isDown = false;
    static volatile float lx = 540, ly = 2130;

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

    static synchronized void send(int action, float x, float y) throws Exception {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent e = MotionEvent.obtain(downTime, now, action, x, y,
            1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject.invoke(mgr, e, 0);
        e.recycle();
    }

    static void release() {
        try {
            if (isDown) {
                send(MotionEvent.ACTION_UP, lx, ly);
                isDown = false;
            }
        } catch (Throwable ignore) {}
    }

    public static void main(String[] args) {
        try {
            init();
            Runtime.getRuntime().addShutdownHook(new Thread(Injector::release));
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
                        String[] p = line.trim().split(" ");
                        if (p.length < 3) continue;
                        float x = Float.parseFloat(p[1]);
                        float y = Float.parseFloat(p[2]);
                        if (p[0].equals("D")) {
                            send(MotionEvent.ACTION_DOWN, x, y);
                            isDown = true;
                        } else if (p[0].equals("M") && isDown) {
                            send(MotionEvent.ACTION_MOVE, x, y);
                        } else if (p[0].equals("U") && isDown) {
                            send(MotionEvent.ACTION_UP, x, y);
                            isDown = false;
                        }
                        lx = x;
                        ly = y;
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                release();
                s.close();
                System.out.println("অ্যাপ বিচ্ছিন্ন");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
