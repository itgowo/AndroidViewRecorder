package com.itgowo.module.androidrecorder.recorder;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.itgowo.module.androidrecorder.util.CameraHelper;

import java.io.IOException;
import java.util.List;

public class CameraManager {
    public static final int PREFERRED_PREVIEW_WIDTH = 320;
    public static final int PREFERRED_PREVIEW_HEIGHT = 240;
    private Activity context;
    private Camera camera;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

    public CameraManager(Activity context) {
        this.context = context;
    }

    public void releaseCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    public void acquireCamera() {
        try {
            if (camera == null) {
                camera = Camera.open(cameraId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void switchCamera() {
        cameraId = (cameraId + 1) % 2;
         releaseCamera();
         acquireCamera();
    }
    public void stopPreview() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallbackWithBuffer(null);
        }
    }

    public Camera getCamera() {
        return camera;
    }

    public void startPreview(int previewWidth, int previewHeight, SurfaceView surfaceView, Camera.PreviewCallback previewCallback) {
        if (camera == null) {
            return;
        }

        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = CameraHelper.getOptimalSize(previewSizes, PREFERRED_PREVIEW_WIDTH, PREFERRED_PREVIEW_HEIGHT);
        // if changed, reassign values and request layout
        if (previewWidth != previewSize.width || previewHeight != previewSize.height) {
            previewWidth = previewSize.width;
            previewHeight = previewSize.height;
            surfaceView.requestLayout();
        }
        parameters.setPreviewSize(previewWidth, previewHeight);
//        parameters.setPreviewFormat(ImageFormat.NV21);
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        camera.setParameters(parameters);
        camera.setDisplayOrientation(CameraHelper.getCameraDisplayOrientation(context, cameraId));
        camera.setPreviewCallback( previewCallback);
        try {
            camera.setPreviewDisplay(surfaceView.getHolder());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        camera.startPreview();
    }
}
