package com.itgowo.module.androidrecorder.recorder;

import android.hardware.Camera;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.nio.Buffer;

public interface onRecordDataListener {
    void onRecordAudioData(Buffer... data) throws FFmpegFrameRecorder.Exception;

    void onRecordVideoData(Frame data) throws FFmpegFrameRecorder.Exception;

    void onRecordTimestamp(long timestamp) throws Exception;
    void onPriviewData(byte[] data, Camera camera) throws Exception;
}
