package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.itgowo.module.androidrecorder.data.FrameToRecord;
import com.itgowo.module.androidrecorder.util.CameraHelper;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class ViewRecordManager {
    private static final int PREFERRED_PREVIEW_WIDTH = 320;
    private static final int PREFERRED_PREVIEW_HEIGHT = 240;
    private static final String TAG = "BaseRecordManager";
    public static final long MIN_VIDEO_LENGTH = 1 * 1000;
    public static final long MAX_VIDEO_LENGTH = 90 * 1000;

    private Activity context;
    private View view;
    private SurfaceView preview;
    private onRecordStatusListener recordStatusListener;
    private onRecordDataListener recordDataListener;


    private LinkedBlockingQueue<FrameToRecord> frameToRecordQueue;
    private LinkedBlockingQueue<FrameToRecord> recycledFrameQueue;
    private Time time = new Time();
    private AndroidFrameConverter androidFrameConverter = new AndroidFrameConverter();

    private VideoRecordThread videoRecordThread;
    private AudioRecordThread audioRecordThread;
    private ViewRecordSendThread viewRecordSendThread;
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
    private int videoWidth = 640;
    private int videoHeight = 480;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;

    private volatile boolean isRecording = false;
    private File videoFile;

    private Camera camera;
    private ImageView cameraview;


    public boolean isRecording() {
        return isRecording;
    }

    public ViewRecordManager setRecording(boolean recording) {
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

    public ViewRecordManager(Activity context, View view, final ImageView cameraview, final onRecordStatusListener recordStatusListener) {
        this.context = context;
        this.view = view;
        this.cameraview = cameraview;
        this.preview = new SurfaceView(context);
        this.preview.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
        ViewGroup viewGroup = (ViewGroup) view.getParent();
        viewGroup.addView(this.preview);
        this.recordStatusListener = recordStatusListener;
        // At most buffer 10 Frame
        frameToRecordQueue = new LinkedBlockingQueue<>(10);
        // At most recycle 2 Frame
        recycledFrameQueue = new LinkedBlockingQueue<>(2);
        time.clear();
        initLibrary();
        init();
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
                }
            }

            @Override
            public void onRecordVideoData2(Buffer data) throws FFmpegFrameRecorder.Exception {
                if (frameRecorder != null) {
                    frameRecorder.recordSamples(data);
                }
            }

            @Override
            public void onRecordTimestamp(long timestamp) throws Exception {
                if (frameRecorder != null) {
                    if (timestamp > frameRecorder.getTimestamp()) {
                        frameRecorder.setTimestamp(timestamp);
                    }
                }
            }

            @Override
            public void onPriviewData(byte[] data, Camera camera) throws Exception {
                cameraview.setImageBitmap(getBitmapFromYuv(data, camera));
            }

        };
    }

    private void init() {
        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                releaseCamera();
                acquireCamera();
                startPreview();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    public File getVideoFile() {
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

    public void setPreviewSize(int width, int height) {

    }

    private Bitmap getBitmapFromYuv(byte[] data, Camera camera) {
        if (data != null) {
            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 100, baos);
            byte[] jdata = baos.toByteArray();
            Bitmap tmpBitmap = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
            return tmpBitmap;
        }
        return null;
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
        camera.setPreviewCallback(new Camera.PreviewCallback() {

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
            camera.setPreviewDisplay(preview.getHolder());
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

    public ViewRecordManager setVideoFile(File videoFile) {
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
            viewRecordSendThread = new ViewRecordSendThread();
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
        try {
            if (viewRecordSendThread != null) {
                viewRecordSendThread.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        audioRecordThread = null;
        videoRecordThread = null;
        viewRecordSendThread = null;
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
            viewRecordSendThread.start();
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

    public void previewFrameCamera(Frame frame, Camera camera) {
        long thisPreviewFrameTime = System.currentTimeMillis();
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
                FrameToRecord frameToRecord = new FrameToRecord(timestamp, frame);
                frameToRecord.setTimestamp(timestamp);
                if (frameToRecordQueue.offer(frameToRecord)) {
                    mFrameToRecordCount++;
                }
            }
        }

    }

    class ViewRecordSendThread extends Thread {
        @Override
        public void run() {
            long delay= (long) (1000/(frameRate*1.1));
            while (isRecording()) {
                Bitmap bitmap = Bitmap.createScaledBitmap(getBitmapFromView(view), videoWidth, videoHeight, true);
                recordStatusListener.onResultBitmap(bitmap);
                Frame frame = androidFrameConverter.convert(bitmap);
                try {
                    Thread.sleep(delay);
                    previewFrameCamera(frame, camera);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public Bitmap getBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        Drawable bgDrawable = v.getBackground();
        if (bgDrawable != null)
            bgDrawable.draw(c);
        else
            c.drawColor(Color.WHITE);
        v.draw(c);
        return b;
    }
}
