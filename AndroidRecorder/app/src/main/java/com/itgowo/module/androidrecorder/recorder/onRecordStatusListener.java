package com.itgowo.module.androidrecorder.recorder;

import android.graphics.Bitmap;
import android.hardware.Camera;

public interface onRecordStatusListener {

    void onRecordStoped() throws Exception;

    void onRecordPrepare() throws Exception;

    void onRecordStarted() throws Exception;

    void onRecordPause() throws Exception;

    void onRecordResume() throws Exception;

    void onResultBitmap(Bitmap bitmap);
}
