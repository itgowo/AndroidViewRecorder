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

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.Buffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ViewRecordManager {

    private static final String TAG = "ViewRecordManager";
    public static final long MAX_VIDEO_LENGTH = 90 * 1000;

    private Activity context;
    private View view;
    private SurfaceView preview;
    private onRecordStatusListener recordStatusListener;
    private onRecordDataListener recordDataListener;
    private ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("ViewRecorderInit");
            return thread;
        }
    });


    private LinkedBlockingQueue<FrameToRecord> frameToRecordQueue;
    private Time time = new Time();
    private AndroidFrameConverter androidFrameConverter = new AndroidFrameConverter();

    private VideoRecordThread videoRecordThread;
    private AudioRecordThread audioRecordThread;
    private ViewRecordSendThread viewRecordSendThread;
    private FFmpegFrameRecorder frameRecorder;
    private CameraManager camera;

    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;
    private long lastPreviewFrameTime;

    private int sampleAudioRateInHz = 44100;
    private int frameRate = 30;
    private int previewWidth;
    private int previewHeight;
    private int videoWidth = 640;
    private int videoHeight = 480;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;

    private volatile boolean isRunning = false;
    private volatile boolean isRecording = false;
    private File videoFile;


    private ImageView cameraview;


    public boolean isRunning() {
        return isRunning;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public ViewRecordManager setRecording(boolean recording) {
        isRecording = recording;
        return this;
    }

    public ViewRecordManager setRunning(boolean running) {
        isRunning = running;
        return this;
    }

    public void initLibrary() {
        threadPoolExecutor.execute(new initLibraryRunnable());
    }


    public ViewRecordManager(Activity context, View view, final ImageView cameraview, final onRecordStatusListener recordStatusListener) {
        this.context = context;
        camera = new CameraManager(context);
        this.view = view;
        this.cameraview = cameraview;
        this.preview = new SurfaceView(context);
        this.preview.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
        ViewGroup viewGroup = (ViewGroup) view.getParent();
        viewGroup.addView(this.preview);
        this.recordStatusListener = recordStatusListener;
        frameToRecordQueue = new LinkedBlockingQueue<>(10);
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
            public void onRecordTimestamp(long timestamp) throws Exception {
                if (frameRecorder != null) {
                    if (timestamp > frameRecorder.getTimestamp()) {
                        frameRecorder.setTimestamp(timestamp);
                    }
                }
            }

            @Override
            public void onPriviewCameraData(byte[] data, Camera camera) throws Exception {
                cameraview.setImageBitmap(getBitmapFromYuv(data, camera));
            }

        };
    }

    private void init() {
        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                camera.releaseCamera();
                camera.acquireCamera();
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
        stopRecording();
        camera.stopPreview();
        camera.switchCamera();
        startPreview();
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


    public void startPreview() {
        camera.startPreview(previewWidth, previewHeight, preview, new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                try {
                    recordDataListener.onPriviewCameraData(data, camera);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void onActivityPause() {
        pauseRecorder();
        stopRecording();
        camera.stopPreview();
        camera.releaseCamera();
    }

    public ViewRecordManager setVideoFile(File videoFile) {
        this.videoFile = videoFile;
        return this;
    }

    /**
     * 启动录制功能，等价于启动后暂停，isRecording目前为false
     */
    public void prepare() {
        threadPoolExecutor.execute(new prepareRecordRunnable());
    }

    public void stopRecording() {
        setRunning(false);
        setRecording(false);
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

        if (frameRecorder != null) {
            try {
                frameRecorder.stop();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }

        time.clear();
        try {
            setRunning(false);
            recordStatusListener.onRecordStoped();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initRecorder() {
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
        if (!isRunning()) {
            return;
        }
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
        if (!isRunning()) {
            setRunning(true);
            setRecording(true);
            time.start();
            audioRecordThread.resumeRecord();
            viewRecordSendThread.start();
            try {
                recordStatusListener.onRecordStarted();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
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

    private void previewFrame(Frame frame, Camera camera) {
        long thisPreviewFrameTime = System.currentTimeMillis();
        lastPreviewFrameTime = thisPreviewFrameTime;
        if (isRunning()) {
            if (audioRecordThread == null || !audioRecordThread.isRunning()) {
                time.start();
            } else {
                long recordedTime = time.getTime();
                long curRecordedTime = System.currentTimeMillis() - time.getStartTime() + recordedTime;
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
            while (isRunning()) {
                long delay = (long) (1000 / (frameRate * 1.1));
                while (isRecording()) {
                    Bitmap bitmap = Bitmap.createScaledBitmap(getBitmapFromView(view), videoWidth, videoHeight, true);
                    recordStatusListener.onResultBitmap(bitmap);
                    Frame frame = androidFrameConverter.convert(bitmap);
                    try {
                        Thread.sleep(delay);
                        previewFrame(frame, camera.getCamera());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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

    private class initLibraryRunnable implements Runnable {

        @Override
        public void run() {
            try {
                FFmpegFrameRecorder.tryLoad();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class prepareRecordRunnable implements Runnable {

        @Override
        public void run() {
            try {
                initRecorder();
                frameRecorder.start();
                audioRecordThread = new AudioRecordThread(sampleAudioRateInHz, recordDataListener);
                audioRecordThread.start();
                videoRecordThread = new VideoRecordThread(frameToRecordQueue, recordDataListener);
                if (videoRecordThread != null) {
                    videoRecordThread.setPreviewWidthHeight(previewWidth, previewHeight);
                }
                videoRecordThread.start();
                viewRecordSendThread = new ViewRecordSendThread();
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            recordStatusListener.onRecordPrepare();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
