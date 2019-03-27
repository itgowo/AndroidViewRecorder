package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.Surface;

import com.itgowo.module.androidrecorder.data.FrameToRecord;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Thread.State.WAITING;

public class VideoRecordThread extends BaseRecordThread {
    private Activity context;
    private int cameraId;
    private int frameRate = 30;
    private LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue;
    private LinkedBlockingQueue<FrameToRecord> mRecycledFrameQueue;
    private onRecordDataListener onRecordDataListener;
    int previewWidth = 480;
    int previewHeight = 640;

    int videoHeight = 480;
    int videoWidth = 640;
    public VideoRecordThread(Activity context, int frameRate, LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue, LinkedBlockingQueue<FrameToRecord> mRecycledFrameQueue, onRecordDataListener onRecordDataListener) {
        this.context = context;
        this.frameRate = frameRate;
        this.mFrameToRecordQueue = mFrameToRecordQueue;
        this.mRecycledFrameQueue = mRecycledFrameQueue;
        this.onRecordDataListener = onRecordDataListener;
    }

    public VideoRecordThread setPreviewWidthHeight(int previewWidth,int previewHeight) {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        return this;
    }

    public VideoRecordThread setCameraId(int cameraId) {
        this.cameraId = cameraId;
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
            mRecycledFrameQueue.offer(recordedFrame);
        }
    }

    public void stopRunning() {
        super.stopRunning();
        if (getState() == WAITING) {
            interrupt();
        }
    }
}