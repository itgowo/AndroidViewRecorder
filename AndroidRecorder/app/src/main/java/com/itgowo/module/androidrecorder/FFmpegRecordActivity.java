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
import com.itgowo.module.androidrecorder.recorder.ProgressDialogTask;
import com.itgowo.module.androidrecorder.recorder.RecordManager;
import com.itgowo.module.androidrecorder.recorder.onRecordStatusListener;

import org.bytedeco.javacv.Frame;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.concurrent.Executors;

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





    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;


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

    private onRecordStatusListener onRecordStatusListener;
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
        onRecordStatusListener = new onRecordStatusListener() {


            @Override
            public void onRecordStoped() throws Exception {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBtnReset.setVisibility(View.INVISIBLE);
                    }
                });
            }

            @Override
            public void onRecordStarted() throws Exception {

            }

            @Override
            public void onRecordPause() throws Exception {

            }

            @Override
            public void onRecordResume() throws Exception {

            }

            @Override
            public void onPriviewData(byte[] data, Camera camera) throws Exception {
                previewFrameCamera(data, camera);
            }
        };

        recordManager = new RecordManager(this, mPreview, onRecordStatusListener);
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
                if (recordManager.isRecording()) {
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
                if (calculateTotalRecordedTime(recordManager.getmRecordFragments()) < MIN_VIDEO_LENGTH) {
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
                        recordManager.startRecording(mPreviewWidth, mPreviewHeight);
                        return null;
                    }
                }.executeOnExecutor(Executors.newCachedThreadPool());
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
                        recordManager.stopRecorder();

                        recordManager.startRecorder();
                        recordManager.startRecording(mPreviewWidth, mPreviewHeight);
                        return null;
                    }
                }.execute();
            }
        });





    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recordManager.stopRecorder();
        recordManager.releaseRecorder(true);
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
        recordManager.stopRecording();
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
                recordManager.initRecorder(videoWidth,videoHeight);
                recordManager.startRecorder();
                recordManager.startRecording(mPreviewWidth, mPreviewHeight);
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
        if (recordManager.isRecording()) {
            if (recordManager.getmAudioRecordThread() == null || !recordManager.getmAudioRecordThread().isRunning()) {
                // wait for AudioRecord to init and start
                recordManager.getmRecordFragments().peek().setStartTimestamp(System.currentTimeMillis());
            } else {
                // pop the current record fragment when calculate total recorded time
                RecordFragment curFragment = recordManager.getmRecordFragments().pop();
                long recordedTime = calculateTotalRecordedTime(recordManager.getmRecordFragments());
                // push it back after calculation
                recordManager.getmRecordFragments().push(curFragment);
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


    private void stopRecording() {

    }

    private void resumeRecording() {
        if (!recordManager.isRecording()) {
            RecordFragment recordFragment = new RecordFragment();
            recordFragment.setStartTimestamp(System.currentTimeMillis());
            recordManager.getmRecordFragments().push(recordFragment);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnReset.setVisibility(View.VISIBLE);
                    mBtnSwitchCamera.setVisibility(View.INVISIBLE);
                    mBtnResumeOrPause.setText(R.string.pause);
                }
            });
            recordManager.setRecording(true);
            recordManager.getmAudioRecordThread().resumeRecord();
        }
    }

    private void pauseRecording() {
        if (recordManager.isRecording()) {
            recordManager.getmRecordFragments().peek().setEndTimestamp(System.currentTimeMillis());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mBtnSwitchCamera.setVisibility(View.VISIBLE);
                    mBtnResumeOrPause.setText(R.string.resume);
                }
            });
            recordManager.setRecording(false);
            recordManager.getmAudioRecordThread().pauseRecord();
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
            recordManager.stopRecorder();
            recordManager.releaseRecorder(false);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            Intent intent = new Intent(FFmpegRecordActivity.this, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.INTENT_NAME_VIDEO_PATH, recordManager.getmVideo().getPath());
            startActivity(intent);
        }
    }


}
