package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;

import com.itgowo.module.androidrecorder.FixedRatioCroppedTextureView;
import com.itgowo.module.androidrecorder.util.MiscUtils;

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
    public void setPreviewSize(int width, int height) {
        if (MiscUtils.isOrientationLandscape(context)) {
             getmPreview().setPreviewSize(width, height);
        } else {
            // Swap width and height
             getmPreview().setPreviewSize(height, width);
        }
    }
}
