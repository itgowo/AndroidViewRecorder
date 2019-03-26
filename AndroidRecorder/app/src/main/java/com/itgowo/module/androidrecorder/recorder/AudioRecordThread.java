package com.itgowo.module.androidrecorder.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.nio.ShortBuffer;

public class AudioRecordThread extends BaseRecordThread {
    private static final String TAG = "AudioRecordThread";
    private AudioRecord mAudioRecord;
    private ShortBuffer audioData;
    private int sampleAudioRateInHz = 44100;
    private onRecordDataListener onRecordDataListener;


    public AudioRecordThread(int sampleAudioRateInHz, com.itgowo.module.androidrecorder.recorder.onRecordDataListener onRecordDataListener) {
        this.sampleAudioRateInHz = sampleAudioRateInHz;
        this.onRecordDataListener = onRecordDataListener;
        int bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        audioData = ShortBuffer.allocate(bufferSize);
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        setName("AudioRecordThread");
        mAudioRecord.startRecording();

        isRunning = true;
        /* ffmpeg_audio encoding loop */
        while (isRunning) {
            if (isRecording) {
                int bufferReadResult = mAudioRecord.read(audioData.array(), 0, audioData.capacity());
                audioData.limit(bufferReadResult);
                if (bufferReadResult > 0) {
                    try {
                        onRecordDataListener.onRecordAudioData(audioData);
                    } catch (FFmpegFrameRecorder.Exception e) {
                        Log.v(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
        mAudioRecord.stop();
        mAudioRecord.release();
        mAudioRecord = null;
    }
}