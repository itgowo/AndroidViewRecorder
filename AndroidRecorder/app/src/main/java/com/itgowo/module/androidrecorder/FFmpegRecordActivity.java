package com.itgowo.module.androidrecorder;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.itgowo.module.androidrecorder.data.RecordFragment;
import com.itgowo.module.androidrecorder.recorder.ProgressDialogTask;
import com.itgowo.module.androidrecorder.recorder.RecordManager;
import com.itgowo.module.androidrecorder.recorder.onRecordStatusListener;

import org.bytedeco.javacv.Frame;

import java.util.concurrent.Executors;

import static com.itgowo.module.androidrecorder.recorder.RecordManager.MIN_VIDEO_LENGTH;

public class FFmpegRecordActivity extends BaseActivity {
    private static final String LOG_TAG = FFmpegRecordActivity.class.getSimpleName();


    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;

    // both in milliseconds

    private Button mBtnResumeOrPause;
    private Button mBtnDone;
    private Button mBtnSwitchCamera;
    private Button mBtnReset;


    private int sampleAudioRateInHz = 44100;
    /* The sides of width and height are based on camera orientation.
    That is, the preview size is the size before it is rotated. */

    // Output video size
    private int videoWidth = 320;
    private int videoHeight = 240;
    private int frameRate = 30;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;

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
                new FFmpegRecordActivity.FinishRecordingTask(context).execute();
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBtnSwitchCamera.setVisibility(View.VISIBLE);
                        mBtnResumeOrPause.setText(R.string.resume);
                    }
                });
            }

            @Override
            public void onRecordResume() throws Exception {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBtnReset.setVisibility(View.VISIBLE);
                        mBtnSwitchCamera.setVisibility(View.INVISIBLE);
                        mBtnResumeOrPause.setText(R.string.pause);
                    }
                });
            }

            @Override
            public void onPriviewData(byte[] data, Camera camera) throws Exception {
                recordManager.previewFrameCamera(data, camera, frameDepth, frameChannels);
            }
        };

        recordManager = new RecordManager(this, mPreview, onRecordStatusListener);
        recordManager.init();
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
                if (recordManager.calculateTotalRecordedTime() < MIN_VIDEO_LENGTH) {
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
                        recordManager.startPreview(surfaceTexture);
                        recordManager.startRecording();
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
                        recordManager.startRecording();
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
            recordManager.startPreview(surfaceTexture);
        }
        new ProgressDialogTask<Void, Integer, Void>(R.string.initiating, context) {

            @Override
            protected Void doInBackground(Void... params) {
                recordManager.initRecorder(videoWidth, videoHeight);
                recordManager.startRecorder();
                recordManager.startRecording();
                return null;
            }
        }.execute();
    }


    private void stopRecording() {

    }

    private void resumeRecording() {
        recordManager.resumeRecording();

    }

    private void pauseRecording() {
        recordManager.pauseRecorder();

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
