package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.view.View;

import com.itgowo.module.androidrecorder.FixedRatioCroppedTextureView;

public class BitmapRecordManager extends TextureRecordManager {
    private View view;


    public BitmapRecordManager(Activity context, FixedRatioCroppedTextureView croppedTextureView, onRecordStatusListener recordStatusListener) {
        super(context, croppedTextureView, recordStatusListener);
    }
}
