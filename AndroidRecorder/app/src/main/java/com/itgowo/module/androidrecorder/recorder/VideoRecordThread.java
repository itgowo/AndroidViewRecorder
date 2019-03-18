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

        List<String> filters = new ArrayList<>();
        // Transpose
        String transpose = null;
        String hflip = null;
        String vflip = null;
        String crop = null;
        String scale = null;
        int cropWidth;
        int cropHeight;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = context.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                switch (info.orientation) {
                    case 270:
                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            transpose = "transpose=clock_flip"; // Same as preview display
                        } else {
                            transpose = "transpose=cclock"; // Mirrored horizontally as preview display
                        }
                        break;
                    case 90:
                        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            transpose = "transpose=cclock_flip"; // Same as preview display
                        } else {
                            transpose = "transpose=clock"; // Mirrored horizontally as preview display
                        }
                        break;
                }
                cropWidth = previewHeight;
                cropHeight = cropWidth * videoHeight / videoWidth;
                crop = String.format("crop=%d:%d:%d:%d", cropWidth, cropHeight, (previewHeight - cropWidth) / 2, (previewWidth - cropHeight) / 2);
                // swap width and height
                scale = String.format("scale=%d:%d", videoHeight, videoWidth);
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                switch (rotation) {
                    case Surface.ROTATION_90:
                        // landscape-left
                        switch (info.orientation) {
                            case 270:
                                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                    hflip = "hflip";
                                }
                                break;
                        }
                        break;
                    case Surface.ROTATION_270:
                        // landscape-right
                        switch (info.orientation) {
                            case 90:
                                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                                    hflip = "hflip";
                                    vflip = "vflip";
                                }
                                break;
                            case 270:
                                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                    vflip = "vflip";
                                }
                                break;
                        }
                        break;
                }
                cropHeight = previewHeight;
                cropWidth = cropHeight * videoWidth / videoHeight;
                crop = String.format("crop=%d:%d:%d:%d", cropWidth, cropHeight, (previewWidth - cropWidth) / 2, (previewHeight - cropHeight) / 2);
                scale = String.format("scale=%d:%d", videoWidth, videoHeight);
                break;
            case Surface.ROTATION_180:
                break;
        }
        // transpose
        if (transpose != null) {
            filters.add(transpose);
        }
        // horizontal flip
        if (hflip != null) {
            filters.add(hflip);
        }
        // vertical flip
        if (vflip != null) {
            filters.add(vflip);
        }
        // crop
        if (crop != null) {
            filters.add(crop);
        }
        // scale (to designated size)
        if (scale != null) {
            filters.add(scale);
        }
        FFmpegFrameFilter frameFilter = new FFmpegFrameFilter(TextUtils.join(",", filters), previewWidth, previewHeight);
        frameFilter.setPixelFormat(avutil.AV_PIX_FMT_NV21);
        frameFilter.setFrameRate(frameRate);
        try {
            frameFilter.start();
        } catch (FrameFilter.Exception e) {
            e.printStackTrace();
        }
        isRunning = true;
        FrameToRecord recordedFrame;
        while (isRunning || !mFrameToRecordQueue.isEmpty()) {
            try {
                recordedFrame = mFrameToRecordQueue.take();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
                try {
                    frameFilter.stop();
                } catch (FrameFilter.Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            Frame filteredFrame = null;
            try {
                onRecordDataListener.onRecordTimestamp(recordedFrame.getTimestamp());
                frameFilter.push(recordedFrame.getFrame());
                filteredFrame = frameFilter.pull();
                onRecordDataListener.onRecordVideoData(filteredFrame);
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