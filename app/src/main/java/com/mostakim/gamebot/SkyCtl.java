package com.mostakim.gamebot;

/** Flappy-style controller: decides when to flap. Pure Java (no Android). */
public class SkyCtl {
    static final int N = 8;
    final double[] ts = new double[N], ys = new double[N];
    int cnt = 0, pos = 0;

    public double lat = 0.18;        // seconds: tap -> effect + vision delay
    public double cool = 0.20;       // minimum seconds between flaps
    public double hEst = 110;        // learned flap rise height (px)
    public double biasK = 0;         // extra offset (px, +down)
    public double vy = 0;            // px/s, +down
    public double gEst = 2500;       // learned gravity px/s^2
    double prevVy = 0, prevVyT = -1;

    double lastTap = -10, tapY = 0, apexY = 0;
    boolean tracking = false;
    public int flaps = 0;

    public void reset() {
        cnt = 0; pos = 0; lastTap = -10; tracking = false; vy = 0; flaps = 0;
        prevVyT = -1;
    }

    void pushSample(double t, double y) {
        ts[pos] = t; ys[pos] = y; pos = (pos + 1) % N;
        if (cnt < N) cnt++;
        double sumT = 0, sumY = 0, sumTT = 0, sumTY = 0; int n = 0;
        for (int k = 0; k < cnt; k++) {
            int i = (pos - 1 - k + N * 2) % N;
            if (t - ts[i] > 0.14) break;
            double dt = ts[i] - t;
            sumT += dt; sumY += ys[i]; sumTT += dt * dt; sumTY += dt * ys[i]; n++;
        }
        if (n >= 3) {
            double den = n * sumTT - sumT * sumT;
            if (Math.abs(den) > 1e-9) vy = (n * sumTY - sumT * sumY) / den;
        }
        if (prevVyT > 0 && t - prevVyT >= 0.12 && t - lastTap > lat + 0.30 && vy > 60 && prevVy > 20) {
            double g = (vy - prevVy) / (t - prevVyT);
            if (g > 600 && g < 9000) gEst = 0.9 * gEst + 0.1 * g;
            prevVy = vy; prevVyT = t;
        } else if (prevVyT < 0 || t - prevVyT > 0.30) {
            prevVy = vy; prevVyT = t;
        }
    }

    /** t seconds, y = cart centre (px, +down), target = wanted y. Returns true to flap. */
    public boolean update(double t, double y, double target) {
        pushSample(t, y);
        if (tracking) {
            if (y < apexY) apexY = y;
            if (vy > 40 && t - lastTap > 0.08) {
                double rise = tapY - apexY;
                if (rise > 15 && rise < 600) hEst = 0.7 * hEst + 0.3 * rise;
                tracking = false;
            }
            if (t - lastTap > 1.2) tracking = false;
        }
        double yp = y + vy * lat + 0.5 * gEst * lat * lat;
        double thr = target + hEst * 0.5 + biasK;
        if (yp > thr && t - lastTap >= cool) {
            lastTap = t; tapY = y; apexY = y; tracking = true; flaps++;
            return true;
        }
        return false;
    }
}
