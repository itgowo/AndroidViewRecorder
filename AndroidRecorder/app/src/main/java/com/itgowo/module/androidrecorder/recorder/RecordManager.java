package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import com.itgowo.module.androidrecorder.FixedRatioCroppedTextureView;
import com.itgowo.module.androidrecorder.util.CameraHelper;
import com.itgowo.module.androidrecorder.util.MiscUtils;

import java.io.IOException;
import java.util.List;

public class RecordManager {
    private static final int PREFERRED_PREVIEW_WIDTH = 640;
    private static final int PREFERRED_PREVIEW_HEIGHT = 480;


    private Activity context;
    private FixedRatioCroppedTextureView mPreview;
    private onRecordDataListener recordDataListener;


    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    ;
    private Camera mCamera;

    public int getmCameraId() {
        return mCameraId;
    }

    public Camera getmCamera() {
        return mCamera;
    }


    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }
    public void acquireCamera() {
        try {
            mCamera=Camera.open(mCameraId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public RecordManager(Activity context, FixedRatioCroppedTextureView mPreview, onRecordDataListener recordDataListener) {
        this.context = context;
        this.mPreview = mPreview;
        this.recordDataListener = recordDataListener;
    }

    public void switchCamera() {
        mCameraId = (mCameraId + 1) % 2;
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

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
        }
    }

    public void startPreview(SurfaceTexture surfaceTexture, int mPreviewWidth, int mPreviewHeight) {
        if (mCamera == null) {
            return;
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes,
                PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT);
        // if changed, reassign values and request layout
        if (mPreviewWidth != previewSize.width || mPreviewHeight != previewSize.height) {
            mPreviewWidth = previewSize.width;
            mPreviewHeight = previewSize.height;
            setPreviewSize(mPreviewWidth, mPreviewHeight);
            mPreview.requestLayout();
        }
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
//        parameters.setPreviewFormat(ImageFormat.NV21);
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        mCamera.setParameters(parameters);

        mCamera.setDisplayOrientation(CameraHelper.getCameraDisplayOrientation(context, mCameraId));

        // YCbCr_420_SP (NV21) format
        byte[] bufferByte = new byte[mPreviewWidth * mPreviewHeight * 3 / 2];
        mCamera.addCallbackBuffer(bufferByte);
        mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {


            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                try {
                    recordDataListener.onPriviewData(data, camera);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        mCamera.startPreview();
    }
}
