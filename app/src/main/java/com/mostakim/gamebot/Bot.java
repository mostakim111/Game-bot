package com.mostakim.gamebot;

public class Bot {
    public static volatile boolean running = false;
    public static volatile long startTime = 0;
    public static volatile String info = "থেমে আছে";
    public static final long MAX_MS = Long.MAX_VALUE / 2;
    public static volatile int tapOk = 0;
    public static volatile int tapFail = 0;

    public static volatile int mode = 0;            // 0 = Cart Catch, 1 = Sky Cart
    public static volatile double skyBias = 0;      // px, +down (flap later)
    public static volatile double skyLat = 0.18;    // seconds
    public static volatile double skyCool = 0.24;   // seconds between flaps
}
