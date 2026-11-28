package com.mostakim.gamebot;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Link {
    public static volatile int target = -1;
    public static volatile long flapSeq = 0;
    public static volatile String state = "link: বন্ধ";
    static volatile boolean alive = false;

    public static synchronized void start() {
        if (alive) return;
        alive = true;
        Thread th = new Thread(Link::loop);
        th.setDaemon(true);
        th.start();
    }

    public static synchronized void shutdown() {
        alive = false;
    }

    static void send(OutputStream o, String s) throws Exception {
        o.write((s + "\n").getBytes());
        o.flush();
    }

    static void loop() {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", 7777), 1500);
            s.setTcpNoDelay(true);
            OutputStream o = s.getOutputStream();
            state = "link: যুক্ত";
            int last = -1000;
            int count = 0;
            long lastSend = 0;
            long sentFlap = flapSeq;
            while (alive) {
                long now = System.currentTimeMillis();
                if (Bot.mode == 1) {
                    if (!Bot.running) {
                        sentFlap = flapSeq;
                    } else if (flapSeq != sentFlap) {
                        send(o, "T 540 1500");
                        sentFlap = flapSeq;
                        count++;
                        state = "link: যুক্ত tap=" + count;
                    }
                } else {
                    int t = target;
                    if (!Bot.running) {
                        last = -1000;
                    } else if (t > 0 && Math.abs(t - last) >= 25
                            && now - lastSend >= 70) {
                        send(o, "T " + t + " 2130");
                        last = t;
                        lastSend = now;
                        count++;
                        state = "link: যুক্ত tap=" + count;
                    }
                }
                Thread.sleep(4);
            }
        } catch (Throwable e) {
            state = "link ত্রুটি: " + e.getClass().getSimpleName();
        } finally {
            alive = false;
            try { if (s != null) s.close(); } catch (Throwable ignore) {}
        }
    }
}
