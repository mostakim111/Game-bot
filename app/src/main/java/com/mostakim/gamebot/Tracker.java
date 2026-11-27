package com.mostakim.gamebot;

public class Tracker {
    static final int MAXT = 24;
    final double[] x = new double[MAXT], y = new double[MAXT], y0 = new double[MAXT];
    final long[] t0 = new long[MAXT], seen = new long[MAXT];
    final int[] hits = new int[MAXT];
    final boolean[] used = new boolean[MAXT];
    final boolean[] now_ = new boolean[MAXT];

    public double vy = 225;
    public double catchY = 1960;
    public double zoneBottom = 1740;
    public long holdAfter = 60;
    public int lastTarget = -1;
    public int nTracks = 0;

    long arrival(int i) {
        return seen[i] + (long) ((catchY - y[i]) / vy * 1000.0);
    }

    public int update(int[] bx, int[] by, int nb, long now) {
        for (int i = 0; i < MAXT; i++) now_[i] = false;

        for (int k = 0; k < nb; k++) {
            int best = -1;
            double bd = 1e9;
            for (int i = 0; i < MAXT; i++) {
                if (!used[i]) continue;
                double py = y[i] + vy * (now - seen[i]) / 1000.0;
                double dx = Math.abs(bx[k] - x[i]);
                double dy = by[k] - py;
                if (dx < 90 && dy > -90 && dy < 90) {
                    double d = dx + Math.abs(dy);
                    if (d < bd) { bd = d; best = i; }
                }
            }
            if (best >= 0) {
                if (now_[best]) continue;
                int i = best;
                now_[i] = true;
                x[i] = 0.7 * x[i] + 0.3 * bx[k];
                y[i] = by[k];
                seen[i] = now;
                hits[i]++;
                long age = now - t0[i];
                if (age > 500 && y[i] - y0[i] > 100) {
                    double v = (y[i] - y0[i]) * 1000.0 / age;
                    if (v > 100 && v < 3000) vy = 0.98 * vy + 0.02 * v;
                }
            } else {
                int slot = -1;
                long oldest = Long.MAX_VALUE;
                for (int i = 0; i < MAXT; i++) {
                    if (!used[i]) { slot = i; break; }
                    if (seen[i] < oldest) { oldest = seen[i]; slot = i; }
                }
                used[slot] = true;
                now_[slot] = true;
                x[slot] = bx[k];
                y[slot] = by[k];
                y0[slot] = by[k];
                t0[slot] = now;
                seen[slot] = now;
                hits[slot] = 1;
            }
        }

        nTracks = 0;
        int bestI = -1;
        long bestA = Long.MAX_VALUE;
        for (int i = 0; i < MAXT; i++) {
            if (!used[i]) continue;
            long a = arrival(i);
            boolean ghost = (now - seen[i] > 150) && (y[i] < zoneBottom - 120);
            boolean passed = a < now - holdAfter;
            boolean stale = now - seen[i] > 4000;
            if (ghost || passed || stale) { used[i] = false; continue; }
            if (hits[i] >= 3) {
                nTracks++;
                if (a < bestA) { bestA = a; bestI = i; }
            }
        }
        if (bestI >= 0) lastTarget = (int) x[bestI];
        return lastTarget;
    }
}
