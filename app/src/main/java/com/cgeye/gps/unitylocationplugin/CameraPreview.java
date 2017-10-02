package com.cgeye.gps.unitylocationplugin;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by CGEye.
 */

public class CameraPreview {

    private String cameraId;
    private CameraCaptureSession cameraCaptureSession;
    private CameraDevice cameraDevice;
    private Size previewSize;

    //Max preview width and height that are guaranteed by Camera2 API
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;


}
