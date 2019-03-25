package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.View;

import com.itgowo.module.androidrecorder.FixedRatioCroppedTextureView;
import com.itgowo.module.androidrecorder.data.FrameToRecord;
import com.itgowo.module.androidrecorder.data.RecordFragment;
import com.itgowo.module.androidrecorder.util.CameraHelper;
import com.itgowo.module.androidrecorder.util.MiscUtils;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordManager {
    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;
    private static final String TAG = "RecordManager";

    private Activity context;
    private FixedRatioCroppedTextureView mPreview;
    private onRecordStatusListener recordStatusListener;
    private onRecordDataListener recordDataListener;


    private LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue;
    private LinkedBlockingQueue<FrameToRecord> mRecycledFrameQueue;
    private Stack<RecordFragment> mRecordFragments;

    private VideoRecordThread mVideoRecordThread;
    private AudioRecordThread mAudioRecordThread;
    private FFmpegFrameRecorder mFrameRecorder;


    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int sampleAudioRateInHz = 44100;
    private int frameRate = 30;
    private volatile boolean isRecording = false;
    private File mVideo;

    private Camera mCamera;

    public int getmCameraId() {
        return mCameraId;
    }

    public Camera getmCamera() {
        return mCamera;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public RecordManager setRecording(boolean recording) {
        isRecording = recording;
        return this;
    }

    public void initLibrary() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FFmpegFrameRecorder.tryLoad();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    public void acquireCamera() {
        try {
            mCamera = Camera.open(mCameraId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public RecordManager(Activity context, FixedRatioCroppedTextureView mPreview, final onRecordStatusListener recordStatusListener) {
        this.context = context;
        this.mPreview = mPreview;
        this.recordStatusListener = recordStatusListener;
        // At most buffer 10 Frame
        mFrameToRecordQueue = new LinkedBlockingQueue<>(10);
        // At most recycle 2 Frame
        mRecycledFrameQueue = new LinkedBlockingQueue<>(2);
        mRecordFragments = new Stack<>();
        initLibrary();
        recordDataListener = new onRecordDataListener() {
            @Override
            public void onRecordAudioData(Buffer... data) throws FFmpegFrameRecorder.Exception {
                if (mFrameRecorder != null) {
                    mFrameRecorder.recordSamples(data);
                }
            }

            @Override
            public void onRecordVideoData(Frame data) throws FFmpegFrameRecorder.Exception {
                if (mFrameRecorder != null) {
                    mFrameRecorder.record(data);
                    System.out.println("RecordManager.onRecordVideoData");
                }
            }

            @Override
            public void onRecordTimestamp(long timestamp) throws Exception {
                if (mFrameRecorder != null) {
                    if (timestamp > mFrameRecorder.getTimestamp()) {
                        mFrameRecorder.setTimestamp(timestamp);
                        System.out.println("RecordManager.onRecordTimestamp");
                    }
                }
            }

            @Override
            public void onPriviewData(byte[] data, Camera camera) throws Exception {
                recordStatusListener.onPriviewData(data, camera);
            }
        };
    }

    public File getmVideo() {
        return mVideo;
    }

    @Deprecated
    public LinkedBlockingQueue<FrameToRecord> getmFrameToRecordQueue() {
        return mFrameToRecordQueue;
    }

    @Deprecated
    public LinkedBlockingQueue<FrameToRecord> getmRecycledFrameQueue() {
        return mRecycledFrameQueue;
    }

    public void switchCamera() {
        mCameraId = (mCameraId + 1) % 2;
    }

    public FixedRatioCroppedTextureView getmPreview() {
        return mPreview;
    }

    public void setPreviewSize(int width, int height) {
        if (MiscUtils.isOrientationLandscape(context)) {
            getmPreview().setPreviewSize(width, height);
        } else {
            // Swap width and height
            getmPreview().setPreviewSize(height, width);
        }
    }

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
        }
    }

    public void startPreview(SurfaceTexture surfaceTexture, int mPreviewWidth, int mPreviewHeight) {
        if (mCamera == null) {
            return;
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes,
                PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT);
        // if changed, reassign values and request layout
        if (mPreviewWidth != previewSize.width || mPreviewHeight != previewSize.height) {
            mPreviewWidth = previewSize.width;
            mPreviewHeight = previewSize.height;
            setPreviewSize(mPreviewWidth, mPreviewHeight);
            mPreview.requestLayout();
        }
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
//        parameters.setPreviewFormat(ImageFormat.NV21);
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        mCamera.setParameters(parameters);

        mCamera.setDisplayOrientation(CameraHelper.getCameraDisplayOrientation(context, mCameraId));

        // YCbCr_420_SP (NV21) format
        byte[] bufferByte = new byte[mPreviewWidth * mPreviewHeight * 3 / 2];
        mCamera.addCallbackBuffer(bufferByte);
        mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {


            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                try {
                    recordDataListener.onPriviewData(data, camera);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        mCamera.startPreview();
    }

    public void startRecording(int mPreviewWidth, int mPreviewHeight) {
        mAudioRecordThread = new AudioRecordThread(sampleAudioRateInHz, recordDataListener);
        mAudioRecordThread.start();
        mVideoRecordThread = new VideoRecordThread(context, frameRate, mFrameToRecordQueue, mRecycledFrameQueue, recordDataListener);
        if (mVideoRecordThread != null) {
            mVideoRecordThread.setPreviewWidthHeight(mPreviewWidth, mPreviewHeight);
        }
        mVideoRecordThread.start();
    }

    public void stopRecording() {
        if (mAudioRecordThread != null) {
            if (mAudioRecordThread.isRunning()) {
                mAudioRecordThread.stopRunning();
            }
        }
        if (mVideoRecordThread != null) {
            if (mVideoRecordThread.isRunning()) {
                mVideoRecordThread.stopRunning();
            }
        }
        try {
            if (mAudioRecordThread != null) {
                mAudioRecordThread.join();
            }
            if (mVideoRecordThread != null) {
                mVideoRecordThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mAudioRecordThread = null;
        mVideoRecordThread = null;
        mFrameToRecordQueue.clear();
        mRecycledFrameQueue.clear();
    }

    public void initRecorder(int videoWidth,int videoHeight) {
        Log.i(TAG, "init mFrameRecorder");

        String recordedTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mVideo = CameraHelper.getOutputMediaFile(recordedTime, CameraHelper.MEDIA_TYPE_VIDEO);
        Log.i(TAG, "Output Video: " + mVideo);

        mFrameRecorder = new FFmpegFrameRecorder(mVideo, videoWidth, videoHeight, 1);
        mFrameRecorder.setFormat("mp4");
        mFrameRecorder.setSampleRate(sampleAudioRateInHz);
        mFrameRecorder.setFrameRate(frameRate);

        // Use H264
        mFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        // See: https://trac.ffmpeg.org/wiki/Encode/H.264#crf
        /*
         * The range of the quantizer scale is 0-51: where 0 is lossless, 23 is default, and 51 is worst possible. A lower value is a higher quality and a subjectively sane range is 18-28. Consider 18 to be visually lossless or nearly so: it should look the same or nearly the same as the input but it isn't technically lossless.
         * The range is exponential, so increasing the CRF value +6 is roughly half the bitrate while -6 is roughly twice the bitrate. General usage is to choose the highest CRF value that still provides an acceptable quality. If the output looks good, then try a higher value and if it looks bad then choose a lower value.
         */
        mFrameRecorder.setVideoOption("crf", "28");
        mFrameRecorder.setVideoOption("preset", "superfast");
        mFrameRecorder.setVideoOption("tune", "zerolatency");

        Log.i(TAG, "mFrameRecorder initialize success");
    }

    public void releaseRecorder(boolean deleteFile) {
        if (mFrameRecorder != null) {
            try {
                mFrameRecorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            mFrameRecorder = null;

            if (deleteFile) {
                mVideo.delete();
            }
        }
    }

    public void startRecorder() {
        try {
            mFrameRecorder.start();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecorder() {
        if (mFrameRecorder != null) {
            try {
                mFrameRecorder.stop();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }

        mRecordFragments.clear();
        try {
            recordStatusListener.onRecordStoped();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public Stack<RecordFragment> getmRecordFragments() {
        return mRecordFragments;
    }

    @Deprecated
    public VideoRecordThread getmVideoRecordThread() {
        return mVideoRecordThread;
    }

    @Deprecated
    public AudioRecordThread getmAudioRecordThread() {
        return mAudioRecordThread;
    }
}
