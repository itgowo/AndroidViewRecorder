package com.itgowo.module.androidrecorder.recorder;

public class BaseRecordThread extends Thread {
    /**
     * 是否是在录制过程中
     */
    public boolean isRunning;
    /**
     * 是否正在录制，false代表暂停
     */
    volatile boolean isRecording = false;

    public boolean isRunning() {
        return isRunning;
    }

    public void stopRunning() {
        this.isRunning = false;
    }

    public void pauseRecord() {
        isRecording = false;
    }

    public void resumeRecord() {
        isRecording = true;
    }
}
