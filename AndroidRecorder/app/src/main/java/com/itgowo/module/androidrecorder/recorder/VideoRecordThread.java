package com.itgowo.module.androidrecorder.recorder;

import com.itgowo.module.androidrecorder.data.FrameToRecord;

import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Thread.State.WAITING;

public class VideoRecordThread extends BaseRecordThread {
    private LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue;
    private onRecordDataListener onRecordDataListener;
    int previewWidth = 480;
    int previewHeight = 640;

    public VideoRecordThread(LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue, onRecordDataListener onRecordDataListener) {
        this.mFrameToRecordQueue = mFrameToRecordQueue;
        this.onRecordDataListener = onRecordDataListener;
    }

    public VideoRecordThread setPreviewWidthHeight(int previewWidth, int previewHeight) {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        return this;
    }


    @Override
    public void run() {
        isRunning = true;
        FrameToRecord recordedFrame;
        while (isRunning || !mFrameToRecordQueue.isEmpty()) {
            try {
                recordedFrame = mFrameToRecordQueue.take();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
                break;
            }
            try {
                onRecordDataListener.onRecordTimestamp(recordedFrame.getTimestamp());
                onRecordDataListener.onRecordVideoData(recordedFrame.getFrame());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stopRunning() {
        super.stopRunning();
        if (getState() == WAITING) {
            interrupt();
        }
    }
}