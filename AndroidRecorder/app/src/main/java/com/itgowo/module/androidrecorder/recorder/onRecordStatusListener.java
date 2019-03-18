package com.itgowo.module.androidrecorder.recorder;

public interface onRecordStatusListener {

    void onRecordStoped() throws Exception;

    void onRecordStarted() throws Exception;

    void onRecordPause() throws Exception;

    void onRecordResume() throws Exception;
}
