package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;

import com.itgowo.module.androidrecorder.FixedRatioCroppedTextureView;

public class RecordManager {
    private Activity context;
    private FixedRatioCroppedTextureView mPreview;

    public RecordManager(Activity context, FixedRatioCroppedTextureView mPreview) {
        this.context = context;
        this.mPreview = mPreview;
    }

    public FixedRatioCroppedTextureView getmPreview() {
        return mPreview;
    }
}
