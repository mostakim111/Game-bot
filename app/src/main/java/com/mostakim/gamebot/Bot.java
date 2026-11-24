package com.mostakim.gamebot;

public class Bot {
    public static volatile boolean running = false;
    public static volatile long startTime = 0;
    public static volatile String info = "থেমে আছে";
  public static final long MAX_MS = Long.MAX_VALUE / 2;
}
