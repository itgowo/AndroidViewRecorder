package com.itgowo.module.androidrecorder;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.ImageReader;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.itgowo.module.androidrecorder.recorder.ProgressDialogTask;
import com.itgowo.module.androidrecorder.recorder.RecordManager;
import com.itgowo.module.androidrecorder.recorder.onRecordStatusListener;
import com.itgowo.module.androidrecorder.util.CameraHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

import static com.itgowo.module.androidrecorder.recorder.RecordManager.MIN_VIDEO_LENGTH;

public class FFmpegRecordActivity extends BaseActivity {
    private Button mBtnResumeOrPause;
    private Button mBtnDone;
    private Button mBtnSwitchCamera;
    private Button mBtnReset;


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
            public void onRecordPrepare() throws Exception {
                System.out.println("FFmpegRecordActivity.onRecordPrepare");
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
                recordManager.previewFrameCamera(data, camera);
            }
        };

        recordManager = new RecordManager(this, mPreview, onRecordStatusListener);
        mBtnResumeOrPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recordManager.isRecording()) {
                    recordManager.pauseRecorder();
                } else {
                    recordManager.resumeRecording();
                }
            }
        });
        mBtnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordManager.pauseRecorder();
                // check video length
                if (recordManager.getTime().getTime() < MIN_VIDEO_LENGTH) {
                    Toast.makeText(v.getContext(), R.string.video_too_short, Toast.LENGTH_SHORT).show();
                    return;
                }
                new FinishRecordingTask(context).execute();
            }
        });
        mBtnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ProgressDialogTask<Void, Integer, Void>(R.string.please_wait, context) {

                    @Override
                    protected Void doInBackground(Void... params) {
                        recordManager.switchCamera();
                        return null;
                    }
                }.executeOnExecutor(Executors.newCachedThreadPool());
            }
        });
        mBtnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordManager.pauseRecorder();
                new ProgressDialogTask<Void, Integer, Void>(R.string.please_wait, context) {

                    @Override
                    protected Void doInBackground(Void... params) {
                        recordManager.stopRecording();
//                        String recordedTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                        recordManager.setVideoFile(CameraHelper.getOutputMediaFile("111", CameraHelper.MEDIA_TYPE_VIDEO));
                        recordManager.startRecordPrepare();
                        return null;
                    }
                }.execute();
            }
        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recordManager.stopRecording();
        recordManager.releaseRecorder(true);
    }


    public void onActivityResume() {

    }

    @Override
    protected void onPause() {
        super.onPause();
        recordManager.onActivityPause();
    }


    @Override
    public void doAfterAllPermissionsGranted() {
        recordManager.acquireCamera();
        recordManager.startPreview();
        new ProgressDialogTask<Void, Integer, Void>(R.string.initiating, context) {

            @Override
            protected Void doInBackground(Void... params) {
//                String recordedTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                recordManager.setVideoFile(CameraHelper.getOutputMediaFile("222", CameraHelper.MEDIA_TYPE_VIDEO));
                recordManager.startRecordPrepare();
                return null;
            }
        }.execute();
    }


    class FinishRecordingTask extends ProgressDialogTask<Void, Integer, Void> {

        public FinishRecordingTask(Context context) {
            super(R.string.processing, context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            recordManager.stopRecording();
            recordManager.releaseRecorder(false);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            Intent intent = new Intent(FFmpegRecordActivity.this, PlaybackActivity.class);
            intent.putExtra(PlaybackActivity.INTENT_NAME_VIDEO_PATH, recordManager.getVideoFile().getPath());
            startActivity(intent);
        }
    }


}
