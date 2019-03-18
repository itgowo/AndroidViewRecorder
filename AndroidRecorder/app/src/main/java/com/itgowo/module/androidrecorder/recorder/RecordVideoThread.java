package com.itgowo.module.androidrecorder.recorder;

import android.hardware.Camera;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.itgowo.module.androidrecorder.data.FrameToRecord;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Thread.State.WAITING;

public class RecordVideoThread extends BaseRecordThread {
    private static final String TAG = "RecordVideoThread";
    private int frameRate;

    public RecordVideoThread setFrameRate(int frameRate) {
        this.frameRate = frameRate;
        return this;
    }

    public RecordVideoThread setRotation(int rotation) {
        this.rotation = rotation;
        return this;
    }

    /**
     * 屏幕方向
     */
    private int rotation;
//        @Override
//        public void run() {
//            int previewWidth = mPreviewWidth;
//            int previewHeight = mPreviewHeight;
//
//            List<String> filters = new ArrayList<>();
//            // Transpose
//            String transpose = null;
//            String hflip = null;
//            String vflip = null;
//            String crop = null;
//            String scale = null;
//            int cropWidth;
//            int cropHeight;
//            Camera.CameraInfo info = new Camera.CameraInfo();
//            Camera.getCameraInfo(mCameraId, info);
//              rotation = getWindowManager().getDefaultDisplay().getRotation();
//            switch (rotation) {
//                case Surface.ROTATION_0:
//                    switch (info.orientation) {
//                        case 270:
//                            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                                transpose = "transpose=clock_flip"; // Same as preview display
//                            } else {
//                                transpose = "transpose=cclock"; // Mirrored horizontally as preview display
//                            }
//                            break;
//                        case 90:
//                            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                                transpose = "transpose=cclock_flip"; // Same as preview display
//                            } else {
//                                transpose = "transpose=clock"; // Mirrored horizontally as preview display
//                            }
//                            break;
//                    }
//                    cropWidth = previewHeight;
//                    cropHeight = cropWidth * videoHeight / videoWidth;
//                    crop = String.format("crop=%d:%d:%d:%d",
//                            cropWidth, cropHeight,
//                            (previewHeight - cropWidth) / 2, (previewWidth - cropHeight) / 2);
//                    // swap width and height
//                    scale = String.format("scale=%d:%d", videoHeight, videoWidth);
//                    break;
//                case Surface.ROTATION_90:
//                case Surface.ROTATION_270:
//                    switch (rotation) {
//                        case Surface.ROTATION_90:
//                            // landscape-left
//                            switch (info.orientation) {
//                                case 270:
//                                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                                        hflip = "hflip";
//                                    }
//                                    break;
//                            }
//                            break;
//                        case Surface.ROTATION_270:
//                            // landscape-right
//                            switch (info.orientation) {
//                                case 90:
//                                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
//                                        hflip = "hflip";
//                                        vflip = "vflip";
//                                    }
//                                    break;
//                                case 270:
//                                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                                        vflip = "vflip";
//                                    }
//                                    break;
//                            }
//                            break;
//                    }
//                    cropHeight = previewHeight;
//                    cropWidth = cropHeight * videoWidth / videoHeight;
//                    crop = String.format("crop=%d:%d:%d:%d",
//                            cropWidth, cropHeight,
//                            (previewWidth - cropWidth) / 2, (previewHeight - cropHeight) / 2);
//                    scale = String.format("scale=%d:%d", videoWidth, videoHeight);
//                    break;
//                case Surface.ROTATION_180:
//                    break;
//            }
//            // transpose
//            if (transpose != null) {
//                filters.add(transpose);
//            }
//            // horizontal flip
//            if (hflip != null) {
//                filters.add(hflip);
//            }
//            // vertical flip
//            if (vflip != null) {
//                filters.add(vflip);
//            }
//            // crop
//            if (crop != null) {
//                filters.add(crop);
//            }
//            // scale (to designated size)
//            if (scale != null) {
//                filters.add(scale);
//            }
//
//            FFmpegFrameFilter frameFilter = new FFmpegFrameFilter(TextUtils.join(",", filters),
//                    previewWidth, previewHeight);
//            frameFilter.setPixelFormat(avutil.AV_PIX_FMT_NV21);
//            frameFilter.setFrameRate(frameRate);
//            try {
//                frameFilter.start();
//            } catch (FrameFilter.Exception e) {
//                e.printStackTrace();
//            }
//
//            isRunning = true;
//            FrameToRecord recordedFrame;
//
//            while (isRunning || !mFrameToRecordQueue.isEmpty()) {
//                try {
//                    recordedFrame = mFrameToRecordQueue.take();
//                } catch (InterruptedException ie) {
//                    ie.printStackTrace();
//                    try {
//                        frameFilter.stop();
//                    } catch (FrameFilter.Exception e) {
//                        e.printStackTrace();
//                    }
//                    break;
//                }
//
//                if (mFrameRecorder != null) {
//                    long timestamp = recordedFrame.getTimestamp();
//                    if (timestamp > mFrameRecorder.getTimestamp()) {
//                        mFrameRecorder.setTimestamp(timestamp);
//                    }
//                    long startTime = System.currentTimeMillis();
////                    Frame filteredFrame = recordedFrame.getFrame();
//                    Frame filteredFrame = null;
//                    try {
//                        frameFilter.push(recordedFrame.getFrame());
//                        filteredFrame = frameFilter.pull();
//                    } catch (FrameFilter.Exception e) {
//                        e.printStackTrace();
//                    }
//                    try {
//                        mFrameRecorder.record(filteredFrame);
//                    } catch (FFmpegFrameRecorder.Exception e) {
//                        e.printStackTrace();
//                    }
//                    long endTime = System.currentTimeMillis();
//                    long processTime = endTime - startTime;
//                    mTotalProcessFrameTime += processTime;
//                    Log.d(TAG, "This frame process time: " + processTime + "ms");
//                    long totalAvg = mTotalProcessFrameTime / ++mFrameRecordedCount;
//                    Log.d(TAG, "Avg frame process time: " + totalAvg + "ms");
//                }
//                Log.d(TAG, mFrameRecordedCount + " / " + mFrameToRecordCount);
//                mRecycledFrameQueue.offer(recordedFrame);
//            }
//        }

        public void stopRunning() {
            super.stopRunning();
            if (getState() == WAITING) {
                interrupt();
            }
        }

}
