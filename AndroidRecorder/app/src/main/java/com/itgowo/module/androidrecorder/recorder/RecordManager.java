package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.TextureView;

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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordManager {
    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;
    private static final String TAG = "RecordManager";
    public static final long MIN_VIDEO_LENGTH = 1 * 1000;
    public static final long MAX_VIDEO_LENGTH = 90 * 1000;

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

    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;
    private long lastPreviewFrameTime;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int sampleAudioRateInHz = 44100;
    private int frameRate = 30;
    private int mPreviewWidth = PREFERRED_PREVIEW_WIDTH;
    private int mPreviewHeight = PREFERRED_PREVIEW_HEIGHT;
    private int videoWidth = 320;
    private int videoHeight = 240;

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

    public void init() {
        mPreview.setPreviewSize(mPreviewWidth, mPreviewHeight);
        mPreview.setCroppedSizeWeight(videoWidth, videoHeight);
        mPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startPreview(surface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
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

    public void startPreview(SurfaceTexture surfaceTexture) {
        if (mCamera == null) {
            return;
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes, PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT);
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

    public void startRecording() {
        startRecorder();
        mAudioRecordThread = new AudioRecordThread(sampleAudioRateInHz, recordDataListener);
        mAudioRecordThread.start();
        mVideoRecordThread = new VideoRecordThread(context, frameRate, mFrameToRecordQueue, mRecycledFrameQueue, recordDataListener);
        if (mVideoRecordThread != null) {
            mVideoRecordThread.setPreviewWidthHeight(mPreviewWidth, mPreviewHeight);
        }
        mVideoRecordThread.start();
        try {
            recordStatusListener.onRecordStarted();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public void initRecorder() {
        String recordedTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mVideo = CameraHelper.getOutputMediaFile(recordedTime, CameraHelper.MEDIA_TYPE_VIDEO);
        mFrameRecorder = new FFmpegFrameRecorder(mVideo, videoWidth, videoHeight, 1);
        mFrameRecorder.setFormat("mp4");
        mFrameRecorder.setSampleRate(sampleAudioRateInHz);
        mFrameRecorder.setFrameRate(frameRate);
        mFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        mFrameRecorder.setVideoOption("crf", "28");
        mFrameRecorder.setVideoOption("preset", "superfast");
        mFrameRecorder.setVideoOption("tune", "zerolatency");
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

    /**
     * 只有在未录制情况下可以启动，暂停状态下不能start
     */
    private void startRecorder() {
        if (isRecording()) {
            return;
        }
        initRecorder();
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

    public void pauseRecorder() {
        if (isRecording()) {
            mRecordFragments.peek().setEndTimestamp(System.currentTimeMillis());
            setRecording(false);
            mAudioRecordThread.pauseRecord();
            try {
                recordStatusListener.onRecordPause();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void resumeRecording() {
        if (!isRecording()) {
            RecordFragment recordFragment = new RecordFragment();
            recordFragment.setStartTimestamp(System.currentTimeMillis());
            mRecordFragments.push(recordFragment);

            setRecording(true);
            mAudioRecordThread.resumeRecord();
            try {
                recordStatusListener.onRecordResume();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public long calculateTotalRecordedTime() {
        long recordedTime = 0;
        for (RecordFragment recordFragment : mRecordFragments) {
            recordedTime += recordFragment.getDuration();
        }
        return recordedTime;
    }

    public void previewFrameCamera(byte[] data, Camera camera, int frameDepth, int frameChannels) {
        long thisPreviewFrameTime = System.currentTimeMillis();
        if (lastPreviewFrameTime > 0) {
            Log.d(TAG, "Preview frame interval: " + (thisPreviewFrameTime - lastPreviewFrameTime) + "ms");
        }
        lastPreviewFrameTime = thisPreviewFrameTime;

        // get video data
        if (isRecording()) {
            if (mAudioRecordThread == null || !mAudioRecordThread.isRunning()) {
                // wait for AudioRecord to init and start
                mRecordFragments.peek().setStartTimestamp(System.currentTimeMillis());
            } else {
                // pop the current record fragment when calculate total recorded time
                RecordFragment curFragment = mRecordFragments.pop();
                long recordedTime = calculateTotalRecordedTime();
                // push it back after calculation
                mRecordFragments.push(curFragment);
                long curRecordedTime = System.currentTimeMillis()
                        - curFragment.getStartTimestamp() + recordedTime;
                // check if exceeds time limit
                if (curRecordedTime > MAX_VIDEO_LENGTH) {
                    try {
                        recordStatusListener.onRecordStoped();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return;
                }

                long timestamp = 1000 * curRecordedTime;
                Frame frame;
                FrameToRecord frameToRecord = mRecycledFrameQueue.poll();
                if (frameToRecord != null) {
                    frame = frameToRecord.getFrame();
                    frameToRecord.setTimestamp(timestamp);
                } else {
                    frame = new Frame(mPreviewWidth, mPreviewHeight, frameDepth, frameChannels);
                    frameToRecord = new FrameToRecord(timestamp, frame);
                }
                ((ByteBuffer) frame.image[0].position(0)).put(data);

                if (mFrameToRecordQueue.offer(frameToRecord)) {
                    mFrameToRecordCount++;
                }
            }
        }
        mCamera.addCallbackBuffer(data);

    }

}
