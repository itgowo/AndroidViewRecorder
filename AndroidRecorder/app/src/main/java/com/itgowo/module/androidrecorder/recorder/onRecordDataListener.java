package com.itgowo.module.androidrecorder.recorder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.nio.Buffer;

public interface onRecordDataListener {
    void onRecordAudioData(Buffer... data) throws FFmpegFrameRecorder.Exception;

    void onRecordVideoData(Frame data) throws FFmpegFrameRecorder.Exception;
}
