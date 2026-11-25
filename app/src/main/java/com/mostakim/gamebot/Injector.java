package com.mostakim.gamebot;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

public class Injector {
    static Object mgr;
    static Method inject;
    static long injNs = 0;
    static int injN = 0;

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

    static void send(int action, float x, float y, long down) throws Exception {
        long now = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(down, now, action, x, y,
            1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        long t0 = System.nanoTime();
        inject.invoke(mgr, e, 0);
        injNs += System.nanoTime() - t0;
        injN++;
        e.recycle();
    }

    public static void main(String[] args) {
        try {
            init();
            System.out.println("তৈরি। ৮ সেকেন্ডে গেমে গিয়ে খেলা শুরু করো...");
            Thread.sleep(8000);
            long down = SystemClock.uptimeMillis();
            send(MotionEvent.ACTION_DOWN, 200, 2130, down);
            for (int i = 1; i <= 40; i++) {
                send(MotionEvent.ACTION_MOVE, 200 + 680f * i / 40f, 2130, down);
                Thread.sleep(16);
            }
            send(MotionEvent.ACTION_UP, 880, 2130, down);
            System.out.println("শেষ। প্রতি ইভেন্টে গড় "
                + (injNs / injN / 1000) + " মাইক্রোসেকেন্ড");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
