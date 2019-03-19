package com.itgowo.module.androidrecorder;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.itgowo.module.androidrecorder.data.FrameToRecord;
import com.itgowo.module.androidrecorder.data.RecordFragment;
import com.itgowo.module.androidrecorder.recorder.AudioRecordThread;
import com.itgowo.module.androidrecorder.recorder.ProgressDialogTask;
import com.itgowo.module.androidrecorder.recorder.RecordManager;
import com.itgowo.module.androidrecorder.recorder.VideoRecordThread;
import com.itgowo.module.androidrecorder.recorder.onRecordDataListener;
import com.itgowo.module.androidrecorder.util.CameraHelper;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

public class FFmpegRecordActivity extends BaseActivity {
    private static final String LOG_TAG = FFmpegRecordActivity.class.getSimpleName();


    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;

    // both in milliseconds
    private static final long MIN_VIDEO_LENGTH = 1 * 1000;
    private static final long MAX_VIDEO_LENGTH = 90 * 1000;
    private Button mBtnResumeOrPause;
    private Button mBtnDone;
    private Button mBtnSwitchCamera;
    private Button mBtnReset;


    private FFmpegFrameRecorder mFrameRecorder;
    private VideoRecordThread mVideoRecordThread;
    private AudioRecordThread mAudioRecordThread;
    private volatile boolean isRecording = false;
    private File mVideo;


    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;
    private Stack<RecordFragment> mRecordFragments;

    private int sampleAudioRateInHz = 44100;
    /* The sides of width and height are based on camera orientation.
    That is, the preview size is the size before it is rotated. */
    private int mPreviewWidth = PREFERRED_PREVIEW_WIDTH;
    private int mPreviewHeight = PREFERRED_PREVIEW_HEIGHT;
    // Output video size
    private int videoWidth = 320;
    private int videoHeight = 240;
    private int frameRate = 30;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;
    private long lastPreviewFrameTime;
    // Workaround for https://code.google.com/p/android/issues/detail?id=190966

    private onRecordDataListener onRecordDataListener;
    private RecordManager recordManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ffmpeg_record);
        FixedRatioCroppedTextureView mPreview = findViewById(R.id.camera_preview);
        mBtnResumeOrPause = findViewById(R.id.btn_resume_or_pause);
        mBtnDone = findViewById(R.id.btn_done);
        mBtnSwitchCamera = findViewById(R.id.btn_switch_camera);
        mBtnReset = findViewById(R.id.btn_reset);
        onRecordDataListener = new onRecordDataListener() {
            @Override
            public void onRecordAudioData(Buffer... data) throws FFmpegFrameRecorder.Exception {
                if (mFrameRecorder != null) {
                    mFrameRecorder.recordSamples(data);
                }
            }

            @Override
            public void onRecordVideoData(Frame d) throws FFmpegFrameRecorder.Exception {
                if (mFrameRecorder != null) {
                    mFrameRecorder.record(d);
                }
            }

            @Override
            public void onRecordTimestamp(long timestamp) throws Exception {
                if (mFrameRecorder != null) {
                    if (timestamp > mFrameRecorder.getTimestamp()) {
                        mFrameRecorder.setTimestamp(timestamp);
                    }
                }
            }

            @Override
            public void onPriviewData(byte[] data, Camera camera) throws Exception {
                previewFrameCamera(data, camera);
            }
        };

        recordManager = new RecordManager(this, mPreview, onRecordDataListener);
        recordManager.getmPreview().setPreviewSize(mPreviewWidth, mPreviewHeight);
        recordManager.getmPreview().setCroppedSizeWeight(videoWidth, videoHeight);
        recordManager.getmPreview().setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                recordManager.startPreview(surface, mPreviewWidth, mPreviewHeight);
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
        mBtnResumeOrPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    pauseRecording();
                } else {
                    resumeRecording();
                }
            }
        });
        mBtnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseRecording();
                // check video length
                if (calculateTotalRecordedTime(mRecordFragments) < MIN_VIDEO_LENGTH) {
                    Toast.makeText(v.getContext(), R.string.video_too_short, Toast.LENGTH_SHORT).show();
                    return;
                }
                new FinishRecordingTask(context).execute();
            }
        });
        mBtnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final SurfaceTexture surfaceTexture = recordManager.getmPreview().getSurfaceTexture();
                new ProgressDialogTask<Void, Integer, Void>(R.string.please_wait, context) {

                    @Override
                    protected Void doInBackground(Void... params) {
                        stopRecording();
                        recordManager.stopPreview();
                        recordManager.releaseCamera();

                        recordManager.switchCamera();

                        recordManager.acquireCamera();
                        recordManager.startPreview(surfaceTexture, mPreviewWidth, mPreviewHeight);
                        startRecording();
                        return null;
                    }
                }.execute();
            }
        });
        mBtnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseRecording();
                new ProgressDialogTask<Void, Integer, Void>(R.string.please_wait, context) {

                    @Override
                    protected Void doInBackground(Void... params) {
                        stopRecording();
                        stopRecorder();

                        startRecorder();
                        startRecording();
                        return null;
                    }
                }.execute();
            }
        });



        mRecordFragments = new Stack<>();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecorder();
        releaseRecorder(true);
    }


    public void onActivityResume() {

    }

    @Override
    protected void onPause() {
        super.onPause();
        onActivityPause();
    }

    public void onActivityPause() {
        pauseRecording();
        stopRecording();
        recordManager.stopPreview();
        recordManager.releaseCamera();
    }

    @Override
    public void doAfterAllPermissionsGranted() {
        recordManager.acquireCamera();
        SurfaceTexture surfaceTexture = recordManager.getmPreview().getSurfaceTexture();
        if (surfaceTexture != null) {
            // SurfaceTexture already created
            recordManager.startPreview(surfaceTexture, mPreviewWidth, mPreviewHeight);
        }
        new ProgressDialogTask<Void, Integer, Void>(R.string.initiating, context) {

            @Override
            protected Void doInBackground(Void... params) {
                if (mFrameRecorder == null) {
                    initRecorder();
                    startRecorder();
                }
                startRecording();
                return null;
            }
        }.execute();
    }

    private void previewFrameCamera(byte[] data, Camera camera) {
        long thisPreviewFrameTime = System.currentTimeMillis();
        if (lastPreviewFrameTime > 0) {
            Log.d(LOG_TAG, "Preview frame interval: " + (thisPreviewFrameTime - lastPreviewFrameTime) + "ms");
        }
        lastPreviewFrameTime = thisPreviewFrameTime;

        // get video data
        if (isRecording) {
            if (mAudioRecordThread == null || !mAudioRecordThread.isRunning()) {
                // wait for AudioRecord to init and start
                mRecordFragments.peek().setStartTimestamp(System.currentTimeMillis());
            } else {
                // pop the current record fragment when calculate total recorded time
                RecordFragment curFragment = mRecordFragments.pop();
                long recordedTime = calculateTotalRecordedTime(mRecordFragments);
                // push it back after calculation
                mRecordFragments.push(curFragment);
                long curRecordedTime = System.currentTimeMillis()
                        - curFragment.getStartTimestamp() + recordedTime;
                // check if exceeds time limit
                if (curRecordedTime > MAX_VIDEO_LENGTH) {
                    pauseRecording();
                    new FinishRecordingTask(context).execute();
                    return;
                }

                long timestamp = 1000 * curRecordedTime;
                Frame frame;
                FrameToRecord frameToRecord = recordManager.getmRecycledFrameQueue().poll();
                if (frameToRecord != null) {
                    frame = frameToRecord.getFrame();
                    frameToRecord.setTimestamp(timestamp);
                } else {
                    frame = new Frame(mPreviewWidth, mPreviewHeight, frameDepth, frameChannels);
                    frameToRecord = new FrameToRecord(timestamp, frame);
                }
                ((ByteBuffer) frame.image[0].position(0)).put(data);

                if (recordManager.getmFrameToRecordQueue().offer(frameToRecord)) {
                    mFrameToRecordCount++;
                }
            }
        }
        recordManager.getmCamera().addCallbackBuffer(data);

    }

    private void initRecorder() {
        Log.i(LOG_TAG, "init mFrameRecorder");

        String recordedTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mVideo = CameraHelper.getOutputMediaFile(recordedTime, CameraHelper.MEDIA_TYPE_VIDEO);
        Log.i(LOG_TAG, "Output Video: " + mVideo);

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

        Log.i(LOG_TAG, "mFrameRecorder initialize success");
    }

    private void releaseRecorder(boolean deleteFile) {
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

    private void startRecorder() {
        try {
            mFrameRecorder.start();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecorder() {
        if (mFrameRecorder != null) {
            try {
                mFrameRecorder.stop();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }

        mRecordFragments.clear();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBtnReset.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void startRecording() {
        mAudioRecordThread = new AudioRecordThread(sampleAudioRateInHz, onRecordDataListener);
        mAudioRecordThread.start();
        mVideoRecordThread = new VideoRecordThread(context, frameRate, recordManager.getmFrameToRecordQueue(), recordManager.getmRecycledFrameQueue(), onRecordDataListener);
        if (mVideoRecordThread != null) {
            mVideoRecordThread.setPreviewWidthHeight(mPreviewWidth, mPreviewHeight);
        }
        mVideoRecordThread.start();
    }

    private void stopRecording() {
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
        recordManager.getmFrameToRecordQueue().clear();
        recordManager.getmFrameToRecordQueue().clear();
    }

    private void resumeRecording() {
        if (!isRecording) {
            RecordFragment recordFragment = new RecordFragment();
            recordFragment.setStartTimestamp(System.currentTimeMillis());
            mRecordFragments.push(recordFragment);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnReset.setVisibility(View.VISIBLE);
                    mBtnSwitchCamera.setVisibility(View.INVISIBLE);
                    mBtnResumeOrPause.setText(R.string.pause);
                }
            });
            isRecording = true;
            mAudioRecordThread.resumeRecord();
        }
    }

    private void pauseRecording() {
        if (isRecording) {
            mRecordFragments.peek().setEndTimestamp(System.currentTimeMillis());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnSwitchCamera.setVisibility(View.VISIBLE);
                    mBtnResumeOrPause.setText(R.string.resume);
                }
            });
            isRecording = false;
            mAudioRecordThread.pauseRecord();
        }
    }

    private long calculateTotalRecordedTime(Stack<RecordFragment> recordFragments) {
        long recordedTime = 0;
        for (RecordFragment recordFragment : recordFragments) {
            recordedTime += recordFragment.getDuration();
        }
        return recordedTime;
    }


    class FinishRecordingTask extends ProgressDialogTask<Void, Integer, Void> {

        public FinishRecordingTask(Context context) {
            super(R.string.processing, context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            stopRecording();
            stopRecorder();
            releaseRecorder(false);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            Intent intent = new Intent(FFmpegRecordActivity.this, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.INTENT_NAME_VIDEO_PATH, mVideo.getPath());
            startActivity(intent);
        }
    }


}
