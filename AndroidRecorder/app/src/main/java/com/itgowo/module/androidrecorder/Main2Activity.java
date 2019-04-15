package com.itgowo.module.androidrecorder;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
    private Button startbtn, previewBtn, stopBtn,btn_switch_camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        recordLayout = (RelativeLayout) findViewById(R.id.record_layout);
        cameraview = (ImageView) findViewById(R.id.cameraview);
        previewBtn = findViewById(R.id.prepare);
        startbtn = findViewById(R.id.start);
        stopBtn = findViewById(R.id.stop);
        btn_switch_camera = findViewById(R.id.btn_switch_camera);
        resultview=findViewById(R.id.result);
        recordManager = new ViewRecordManager(this, recordLayout,  cameraview, new onRecordStatusListener() {
            @Override
            public void onRecordStoped() throws Exception {
                startbtn.setText("开始");
                startbtn.setEnabled(false);
            }

            @Override
            public void onRecordPrepare() throws Exception {
                System.out.println("Main2Activity.onRecordPrepare");
                startbtn.setEnabled(true);
                startbtn.setText("开始");
            }

            @Override
            public void onRecordStarted() throws Exception {
                startbtn.setText("暂停");
            }

            @Override
            public void onRecordPause() throws Exception {
                startbtn.setText("继续");
            }

            @Override
            public void onRecordResume() throws Exception {
                startbtn.setText("暂停");
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
                startbtn.setEnabled(false);
                recordManager.setVideoFile(CameraHelper.getOutputMediaFile("111", CameraHelper.MEDIA_TYPE_VIDEO));
                recordManager.prepare();
                recordManager.startPreview();
            }
        });
        startbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recordManager.isRecording()){
                    recordManager.pauseRecorder();
                }else {
                    recordManager.resumeRecording();
                }
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
        recordManager.initLibrary();
        btn_switch_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordManager.switchCamera();
            }
        });
    }
}
