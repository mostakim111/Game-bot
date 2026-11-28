package com.mostakim.gamebot;

import java.nio.ByteBuffer;

/** Finds the next tower gap and the cart in a 540x1170 RGBA frame. Pure Java. Output in full-screen (1080x2340) pixels. */
public class SkyVision {
    static final int Y0 = 165, Y1 = 1055;          // play area rows (frame px)
    static final int CAP = 76;                     // cap height (full px)
    public static final int CART_BAND_L = 100, CART_BAND_R = 250; // frame px

    public boolean haveGap, haveCart;
    public double gapC, gapTop, gapBot;            // gap centre / free edges (full px)
    public double towerL, towerR;                  // selected tower x range (full px)
    public double cartY, cartX;                    // full px
    public int nTowers;

    static boolean brown(int r, int g, int b) {
        return r >= 160 && g >= 100 && g <= 235 && b >= 70 && b <= 185
            && (r - b) >= 60 && (g - b) >= 25;
    }

    static boolean cartPink(int r, int g, int b) {
        return r >= 190 && g >= 46 && g <= 170 && b >= 110 && b <= 215 && (r - g) >= 50;
    }

    int px(ByteBuffer buf, int rs, int ps, int x, int y, int ch) {
        return buf.get(y * rs + x * ps + ch) & 0xff;
    }

    public void analyze(ByteBuffer buf, int rs, int ps) {
        haveGap = false; haveCart = false; nTowers = 0;

        // 1) tower columns: brown count per column
        int[] cnt = new int[540];
        for (int x = 0; x < 540; x += 2) {
            int c = 0;
            for (int y = Y0; y < Y1; y += 3) {
                int o = y * rs + x * ps;
                if (brown(buf.get(o) & 0xff, buf.get(o + 1) & 0xff, buf.get(o + 2) & 0xff)) c++;
            }
            cnt[x] = c;
        }
        // runs of columns with enough brown
        int[] rs0 = new int[8], re0 = new int[8];
        int nr = 0, start = -1, last = -1;
        for (int x = 0; x < 540; x += 2) {
            if (cnt[x] >= 35) {
                if (start < 0) start = x;
                last = x;
            } else if (start >= 0 && x - last > 4) {
                if (last - start >= 18 && nr < 8) { rs0[nr] = start; re0[nr] = last; nr++; }
                start = -1;
            }
        }
        if (start >= 0 && last - start >= 18 && nr < 8) { rs0[nr] = start; re0[nr] = last; nr++; }
        nTowers = nr;

        // select first tower not yet passed (right edge beyond cart left)
        int sel = -1;
        for (int i = 0; i < nr; i++) {
            if (re0[i] * 2 >= 200) { sel = i; break; }
        }
        double exL = -1, exR = -1, exTop = 0, exBot = 0;
        if (sel >= 0) {
            int s = rs0[sel], e = re0[sel];
            towerL = s * 2; towerR = e * 2 + 2;
            // 2) brown rows using the two side strips of the tower body
            int w = e - s;
            int a1 = s + 3, a2 = s + Math.min(18, w / 4);
            int b1 = e - Math.min(18, w / 4), b2 = e - 3;
            boolean[] row = new boolean[H3];
            for (int y = Y0, k = 0; y < Y1; y += 3, k++) {
                int c1 = 0, n1 = 0, c2 = 0, n2 = 0;
                for (int x = a1; x <= a2; x += 2) { n1++; int o = y * rs + x * ps; if (brown(buf.get(o) & 0xff, buf.get(o + 1) & 0xff, buf.get(o + 2) & 0xff)) c1++; }
                for (int x = b1; x <= b2; x += 2) { n2++; int o = y * rs + x * ps; if (brown(buf.get(o) & 0xff, buf.get(o + 1) & 0xff, buf.get(o + 2) & 0xff)) c2++; }
                row[k] = (n1 > 0 && c1 * 10 >= n1 * 6) || (n2 > 0 && c2 * 10 >= n2 * 6);
            }
            // largest distance between consecutive brown rows
            int prev = -1, bestLen = 0, bt = -1, bb = -1;
            for (int k = 0; k < H3; k++) {
                if (!row[k]) continue;
                if (prev >= 0 && (k - prev) > bestLen) { bestLen = k - prev; bt = prev; bb = k; }
                prev = k;
            }
            if (bestLen * 3 * 2 >= 330) {   // at least 330 full px between brown blocks
                double topBrown = (Y0 + bt * 3) * 2, botBrown = (Y0 + bb * 3) * 2;
                gapTop = topBrown + CAP;
                gapBot = botBrown - CAP;
                gapC = (topBrown + botBrown) / 2.0;
                haveGap = gapBot > gapTop + 100;
                exTop = gapTop; exBot = gapBot; exL = towerL; exR = towerR;
            }
        }

        // 3) cart: densest vertical cluster of pink pixels in the cart band
        int[] hist = new int[1200];
        long[] hx = new long[1200];
        for (int y = Y0; y < Y1; y += 2) {
            double fy = y * 2.0;
            for (int x = CART_BAND_L; x < CART_BAND_R; x += 2) {
                double fx = x * 2.0;
                if (exL >= 0 && fx >= exL - 6 && fx <= exR + 6 && (fy < exTop || fy > exBot)) continue;
                int o = y * rs + x * ps;
                if (cartPink(buf.get(o) & 0xff, buf.get(o + 1) & 0xff, buf.get(o + 2) & 0xff)) {
                    hist[y]++; hx[y] += x;
                }
            }
        }
        int win = 70, bestSum = 0, bestY = -1;
        int cur = 0;
        for (int y = Y0; y < Y0 + win; y++) cur += hist[y];
        bestSum = cur; bestY = Y0;
        for (int y = Y0 + 1; y + win < Y1; y++) {
            cur += hist[y + win - 1] - hist[y - 1];
            if (cur > bestSum) { bestSum = cur; bestY = y; }
        }
        if (bestSum >= 250) {
            long sy = 0, sx = 0, n = 0;
            for (int y = bestY; y < bestY + win; y++) { sy += (long) y * hist[y]; sx += hx[y]; n += hist[y]; }
            if (n > 0) {
                haveCart = true;
                cartY = (sy / (double) n) * 2.0;
                cartX = (sx / (double) n) * 2.0;
            }
        }
    }

    static final int H3 = (Y1 - Y0 + 2) / 3;
}
