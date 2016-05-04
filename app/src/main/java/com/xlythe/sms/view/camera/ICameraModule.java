package com.xlythe.sms.view.camera;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;

public abstract class ICameraModule {
    static final String TAG = "CameraModule";
    static final boolean DEBUG = true;

    private final CameraView mView;
    private BaseCameraView.OnImageCapturedListener mOnImageCapturedListener;
    private BaseCameraView.OnVideoCapturedListener mOnVideoCapturedListener;

    ICameraModule(CameraView view) {
        mView = view;
    }

    public int getWidth() {
        return mView.getWidth();
    }

    public int getHeight() {
        return mView.getHeight();
    }

    public int getDisplayRotation() {
        int displayRotation = mView.getDisplayRotation();
        switch (displayRotation) {
            case 0:
                displayRotation = 0;
                break;
            case 1:
                displayRotation = 90;
                break;
            case 2:
                displayRotation = 180;
                break;
            case 3:
                displayRotation = 270;
                break;
        }
        return displayRotation;
    }

    public SurfaceTexture getSurfaceTexture() {
        return mView.getSurfaceTexture();
    }

    public Matrix getTransform(Matrix matrix) {
        return mView.getTransform(matrix);
    }

    public void setTransform(Matrix matrix) {
        mView.setTransform(matrix);
    }

    protected void configureTransform(int viewWidth, int viewHeight, int previewWidth, int previewHeight, int cameraOrientation) {
        if (DEBUG) {
            Log.d(TAG, String.format("Configuring SurfaceView matrix: "
                            + "viewWidth=%s, viewHeight=%s, previewWidth=%s, previewHeight=%s, cameraOrientation=%s",
                    viewWidth, viewHeight, previewWidth, previewHeight, cameraOrientation));
        }

        if (cameraOrientation == 90 || cameraOrientation == 270) {
            int temp = previewWidth;
            previewWidth = previewHeight;
            previewHeight = temp;
        }

        double aspectRatio = (double) previewHeight / (double) previewWidth;
        int newWidth, newHeight;

        if (getHeight() > viewWidth * aspectRatio) {
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        } else {
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        }

        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;

        Matrix txform = new Matrix();

        getTransform(txform);

        float xscale = (float) newWidth / (float) viewWidth;
        float yscale = (float) newHeight / (float) viewHeight;

        txform.setScale(xscale, yscale);

        // TODO rotate?

        txform.postTranslate(xoff, yoff);

        setTransform(txform);
    }

    /*
     * Opens the camera and starts displaying a preview. You are in charge of checking if the
     * phone has PackageManager.FEATURE_CAMERA_ANY and, if you are targeting Android M+, that
     * the phone has the following permissions:
     *       Manifest.permission.CAMERA
     *       Manifest.permission.RECORD_AUDIO
     *       Manifest.permission.WRITE_EXTERNAL_STORAGE
     */
    public abstract void open();

    /*
     * Closes the camera.
     */
    public abstract void close();

    /**
     * Takes a picture. Set a OnImageCapturedListener to be
     * notified of when the picture has finished saving.
     */
    public abstract void takePicture(File file);

    /**
     * Records a video. Set a OnVideoCapturedListener to be notified of when
     * the video has finished saving.
     */
    public abstract void startRecording(File file);

    /**
     * Stops recording the video. It's recommended that you set a timeout when recording to avoid
     * excessively large files.
     */
    public abstract void stopRecording();

    /**
     * Returns true if recording.
     */
    public abstract boolean isRecording();

    public abstract void toggleCamera();

    public abstract boolean hasFrontFacingCamera();

    public abstract boolean isUsingFrontFacingCamera();

    public abstract void focus(Rect focus, Rect metering);

    public void setOnImageCapturedListener(BaseCameraView.OnImageCapturedListener l) {
        mOnImageCapturedListener = l;
    }

    public BaseCameraView.OnImageCapturedListener getOnImageCapturedListener() {
        return mOnImageCapturedListener;
    }

    public void setOnVideoCapturedListener(BaseCameraView.OnVideoCapturedListener l) {
        mOnVideoCapturedListener = l;
    }

    public BaseCameraView.OnVideoCapturedListener getOnVideoCapturedListener() {
        return mOnVideoCapturedListener;
    }

    static int getRelativeImageOrientation(int displayRotation, int sensorOrientation, boolean isFrontFacing, boolean compensateForMirroring) {
        Log.d(TAG, String.format("getRelativeImageOrientation displayRotation=%s, sensorOrientation=%s, isFrontFacing=%s, compensateForMirroring=%s",
                displayRotation, sensorOrientation, isFrontFacing, compensateForMirroring));
        int result;
        if (isFrontFacing) {
            result = (sensorOrientation + displayRotation) % 360;
            if (compensateForMirroring) {
                result = (360 - result) % 360;
            }
        } else {
            result = (sensorOrientation - displayRotation + 360) % 360;
        }
        return result;
    }
}