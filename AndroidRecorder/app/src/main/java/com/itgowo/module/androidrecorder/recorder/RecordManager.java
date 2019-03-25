package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.TextureView;

import com.itgowo.module.androidrecorder.FixedRatioCroppedTextureView;
import com.itgowo.module.androidrecorder.data.FrameToRecord;
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
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordManager {
    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;
    private static final String TAG = "RecordManager";
    public static final long MIN_VIDEO_LENGTH = 1 * 1000;
    public static final long MAX_VIDEO_LENGTH = 90 * 1000;

    private Activity context;
    private FixedRatioCroppedTextureView preview;
    private onRecordStatusListener recordStatusListener;
    private onRecordDataListener recordDataListener;


    private LinkedBlockingQueue<FrameToRecord> frameToRecordQueue;
    private LinkedBlockingQueue<FrameToRecord> recycledFrameQueue;
    private Time time = new Time();

    private VideoRecordThread videoRecordThread;
    private AudioRecordThread audioRecordThread;
    private FFmpegFrameRecorder frameRecorder;

    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;
    private long lastPreviewFrameTime;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int sampleAudioRateInHz = 44100;
    private int frameRate = 30;
    private int previewWidth = PREFERRED_PREVIEW_WIDTH;
    private int previewHeight = PREFERRED_PREVIEW_HEIGHT;
    private int videoWidth = 320;
    private int videoHeight = 240;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;

    private volatile boolean isRecording = false;
    private File videoFile;

    private Camera camera;


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
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    public void acquireCamera() {
        try {
            camera = Camera.open(cameraId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public RecordManager(Activity context, FixedRatioCroppedTextureView croppedTextureView, final onRecordStatusListener recordStatusListener) {
        this.context = context;
        this.preview = croppedTextureView;
        this.recordStatusListener = recordStatusListener;
        // At most buffer 10 Frame
        frameToRecordQueue = new LinkedBlockingQueue<>(10);
        // At most recycle 2 Frame
        recycledFrameQueue = new LinkedBlockingQueue<>(2);
        time.clear();
        initLibrary();
        recordDataListener = new onRecordDataListener() {
            @Override
            public void onRecordAudioData(Buffer... data) throws FFmpegFrameRecorder.Exception {
                if (frameRecorder != null) {
                    frameRecorder.recordSamples(data);
                }
            }

            @Override
            public void onRecordVideoData(Frame data) throws FFmpegFrameRecorder.Exception {
                if (frameRecorder != null) {
                    frameRecorder.record(data);
                    System.out.println("RecordManager.onRecordVideoData");
                }
            }

            @Override
            public void onRecordTimestamp(long timestamp) throws Exception {
                if (frameRecorder != null) {
                    if (timestamp > frameRecorder.getTimestamp()) {
                        frameRecorder.setTimestamp(timestamp);
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
        preview.setPreviewSize(previewWidth, previewHeight);
        preview.setCroppedSizeWeight(videoWidth, videoHeight);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                startPreview();
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
        return videoFile;
    }

    public void switchCamera() {
        cameraId = (cameraId + 1) % 2;
        stopRecording();
        stopPreview();
        releaseCamera();
        acquireCamera();
        startPreview();
        startRecordPrepare();
    }

    public FixedRatioCroppedTextureView getPreview() {
        return preview;
    }

    public void setPreviewSize(int width, int height) {
        if (MiscUtils.isOrientationLandscape(context)) {
            preview.setPreviewSize(width, height);
        } else {
            // Swap width and height
            preview.setPreviewSize(height, width);
        }
    }

    public void stopPreview() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallbackWithBuffer(null);
        }
    }

    public void startPreview() {
        if (camera == null) {
            return;
        }

        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes, PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT);
        // if changed, reassign values and request layout
        if (previewWidth != previewSize.width || previewHeight != previewSize.height) {
            previewWidth = previewSize.width;
            previewHeight = previewSize.height;
            setPreviewSize(previewWidth, previewHeight);
            preview.requestLayout();
        }
        parameters.setPreviewSize(previewWidth, previewHeight);
//        parameters.setPreviewFormat(ImageFormat.NV21);
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        camera.setParameters(parameters);

        camera.setDisplayOrientation(CameraHelper.getCameraDisplayOrientation(context, cameraId));

        // YCbCr_420_SP (NV21) format
        byte[] bufferByte = new byte[previewWidth * previewHeight * 3 / 2];
        camera.addCallbackBuffer(bufferByte);
        camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {


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
            camera.setPreviewTexture(preview.getSurfaceTexture());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        camera.startPreview();
    }

    public void onActivityPause() {
        pauseRecorder();
        stopRecording();
        stopPreview();
        releaseCamera();
    }

    public RecordManager setVideoFile(File videoFile) {
        this.videoFile = videoFile;
        return this;
    }

    /**
     * 启动录制功能，等价于启动后暂停，isRecording目前为false
     */
    public void startRecordPrepare() {
        try {
            initRecorder();
            frameRecorder.start();
            audioRecordThread = new AudioRecordThread(sampleAudioRateInHz, recordDataListener);
            audioRecordThread.start();
            videoRecordThread = new VideoRecordThread(context, frameRate, frameToRecordQueue, recycledFrameQueue, recordDataListener);
            if (videoRecordThread != null) {
                videoRecordThread.setPreviewWidthHeight(previewWidth, previewHeight);
            }
            videoRecordThread.start();
            recordStatusListener.onRecordPrepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        if (audioRecordThread != null) {
            if (audioRecordThread.isRunning()) {
                audioRecordThread.stopRunning();
            }
        }
        if (videoRecordThread != null) {
            if (videoRecordThread.isRunning()) {
                videoRecordThread.stopRunning();
            }
        }
        try {
            if (audioRecordThread != null) {
                audioRecordThread.join();
            }
            if (videoRecordThread != null) {
                videoRecordThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        audioRecordThread = null;
        videoRecordThread = null;
        frameToRecordQueue.clear();
        recycledFrameQueue.clear();

        if (frameRecorder != null) {
            try {
                frameRecorder.stop();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }

        time.clear();
        try {
            recordStatusListener.onRecordStoped();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initRecorder() {
        if (videoFile.exists()) {
            String oriFileName = videoFile.getName();
            int index = videoFile.getName().lastIndexOf(".");
            int i = 0;
            while (videoFile.exists()) {
                String name = null;
                if (index >= 0) {
                    name = oriFileName.substring(0, index) + "_" + i + oriFileName.substring(index);
                } else {
                    name = oriFileName + "_" + i;
                }
                videoFile = new File(videoFile.getParentFile(), name);
                i++;
            }
        }

        frameRecorder = new FFmpegFrameRecorder(videoFile, videoWidth, videoHeight, 1);
        frameRecorder.setFormat("mp4");
        frameRecorder.setSampleRate(sampleAudioRateInHz);
        frameRecorder.setFrameRate(frameRate);
        frameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        frameRecorder.setVideoOption("crf", "28");
        frameRecorder.setVideoOption("preset", "superfast");
        frameRecorder.setVideoOption("tune", "zerolatency");
    }

    public void releaseRecorder(boolean deleteFile) {
        if (frameRecorder != null) {
            try {
                frameRecorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            frameRecorder = null;
            if (deleteFile) {
                videoFile.delete();
            }
        }
    }

    public void pauseRecorder() {
        if (isRecording()) {
            time.pause();
            setRecording(false);
            audioRecordThread.pauseRecord();
            try {
                recordStatusListener.onRecordPause();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void resumeRecording() {
        if (!isRecording()) {
            setRecording(true);
            time.start();
            audioRecordThread.resumeRecord();
            try {
                recordStatusListener.onRecordResume();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Time getTime() {
        return time;
    }

    public void previewFrameCamera(byte[] data, Camera camera) {
        long thisPreviewFrameTime = System.currentTimeMillis();
//        if (lastPreviewFrameTime > 0) {
//            Log.d(TAG, "Preview frame interval: " + (thisPreviewFrameTime - lastPreviewFrameTime) + "ms");
//        }
        lastPreviewFrameTime = thisPreviewFrameTime;
        if (isRecording()) {
            if (audioRecordThread == null || !audioRecordThread.isRunning()) {
                time.start();
            } else {
                long recordedTime = time.getTime();
                long curRecordedTime = System.currentTimeMillis() - time.getStartTime() + recordedTime;
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
                FrameToRecord frameToRecord = recycledFrameQueue.poll();
                if (frameToRecord != null) {
                    frame = frameToRecord.getFrame();
                    frameToRecord.setTimestamp(timestamp);
                } else {
                    frame = new Frame(previewWidth, previewHeight, frameDepth, frameChannels);
                    frameToRecord = new FrameToRecord(timestamp, frame);
                }
                ((ByteBuffer) frame.image[0].position(0)).put(data);
                if (frameToRecordQueue.offer(frameToRecord)) {
                    mFrameToRecordCount++;
                }
            }
        }
        this.camera.addCallbackBuffer(data);
    }

}
