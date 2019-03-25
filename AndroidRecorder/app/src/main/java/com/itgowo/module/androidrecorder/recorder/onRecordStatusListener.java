package com.itgowo.module.androidrecorder.recorder;

import android.hardware.Camera;

public interface onRecordStatusListener {

    void onRecordStoped() throws Exception;

    void onRecordPrepare() throws Exception;

    void onRecordStarted() throws Exception;

    void onRecordPause() throws Exception;

    void onRecordResume() throws Exception;

    void onPriviewData(byte[] data, Camera camera) throws Exception;

}
