package com.itgowo.module.androidrecorder;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.itgowo.module.androidrecorder.recorder.ViewRecordManager;
import com.itgowo.module.androidrecorder.recorder.onRecordStatusListener;
import com.itgowo.module.androidrecorder.util.CameraHelper;

public class Main2Activity extends AppCompatActivity {
    private RelativeLayout recordLayout;
    private ImageView cameraview,resultview;
    private ViewRecordManager recordManager;
    private Button startbtn, previewBtn, stopBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        recordLayout = (RelativeLayout) findViewById(R.id.record_layout);
        cameraview = (ImageView) findViewById(R.id.cameraview);
        previewBtn = findViewById(R.id.preview);
        startbtn = findViewById(R.id.start);
        stopBtn = findViewById(R.id.stop);
        resultview=findViewById(R.id.result);
        recordManager = new ViewRecordManager(this, recordLayout, cameraview, new onRecordStatusListener() {
            @Override
            public void onRecordStoped() throws Exception {

            }

            @Override
            public void onRecordPrepare() throws Exception {

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
            public void onResultBitmap(final Bitmap bitmap) {
                resultview.post(new Runnable() {
                    @Override
                    public void run() {
                        resultview.setImageBitmap(bitmap);
                    }
                });
            }


        });
        previewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordManager.setVideoFile(CameraHelper.getOutputMediaFile("111", CameraHelper.MEDIA_TYPE_VIDEO));
                recordManager.acquireCamera();
                recordManager.startRecordPrepare();
                recordManager.startPreview();
            }
        });
        startbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordManager.resumeRecording();
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordManager.pauseRecorder();
                recordManager.stopRecording();
            }
        });
        recordLayout.setBackground(Drawable.createFromPath("/storage/emulated/0/UCDownloads/pictures/111.jpg"));
    }
}
