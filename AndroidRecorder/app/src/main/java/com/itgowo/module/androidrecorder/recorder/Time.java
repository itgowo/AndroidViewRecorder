package com.itgowo.module.androidrecorder.recorder;

public class Time {
    private long time;
    private long startTime;

    public long getStartTime() {
        return startTime;
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void pause() {
        time += System.currentTimeMillis() - startTime;
    }

    public void clear() {
        time = 0;
    }

    public long getTime() {
        return time;
    }
}