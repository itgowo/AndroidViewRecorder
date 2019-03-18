package com.itgowo.module.androidrecorder.recorder;

import android.content.Context;
import android.content.Intent;

import com.itgowo.module.androidrecorder.FFmpegRecordActivity;
import com.itgowo.module.androidrecorder.PlaybackActivity;
import com.itgowo.module.androidrecorder.R;

class FinishRecordingTask extends ProgressDialogTask<Void, Integer, Void> {

    public FinishRecordingTask(Context context) {
        super(R.string.processing, context);
    }

    @Override
    protected Void doInBackground(Void... params) {

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);

    }
}
