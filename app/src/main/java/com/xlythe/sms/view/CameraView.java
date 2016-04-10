package com.xlythe.sms.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@TargetApi(21)
public class CameraView extends TextureView implements ICameraView {
    private static final String TAG = CameraView.class.getSimpleName();
    private static final String TEMP_FILE_PIC = "temp.png";
    private static final String TEMP_FILE_VID = "temp.mp4";

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    
    private enum State {
        PREVIEW, WAITING_LOCK, WAITING_PRECAPTURE, WAITING_NON_PRECAPTURE, PICTURE_TAKEN;
    }

    /**
     * The cameraId that's currently active. If null, use the default camera id.
     */
    private String mActiveCamera;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * Camera preview.
     */
    private CaptureRequest.Builder mPreviewBuilder;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private State mState = State.PREVIEW;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * Different surfaces that the camera displays on.
     */
    private PreviewSurface mPreviewSurface = new PreviewSurface(this);
    private PhotoSurface mPhotoSurface = new PhotoSurface(this);
    private VideoSurface mVideoSurface = new VideoSurface(this);
    private CameraSurface[] mCameraSurfaces = new CameraSurface[] {
            mPreviewSurface,
            mPhotoSurface,
            mVideoSurface
    };

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // The camera has opened. Start the preview now.
            mCameraDevice = cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mState) {
                case PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(State.PICTURE_TAKEN);
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        setState(State.WAITING_NON_PRECAPTURE);
                    }
                    break;
                }
                case WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(State.PICTURE_TAKEN);
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }
    };

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void open() {
        startBackgroundThread();
        if (isAvailable()) {
            openCamera(getWidth(), getHeight());
        } else {
            setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void close() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        for (CameraSurface surface : mCameraSurfaces) {
            surface.close();
        }

        stopBackgroundThread();
    }

    @Override
    public void takePicture(CameraListener listener) {
        mPhotoSurface.takePicture(listener);
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            setState(State.WAITING_PRECAPTURE);
            mCaptureSession.capture(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mPhotoSurface.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Orientation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(getDisplayRotation()));

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    mPhotoSurface.onPictureTaken();
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setState(State state) {
        Log.d(TAG, "Setting State: " + state.name());
        mState = state;
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            setState(State.WAITING_LOCK);
            mCaptureSession.capture(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewBuilder.build(), mCaptureCallback, mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            setState(State.PREVIEW);
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startRecording(CameraListener listener) {
        mVideoSurface.startRecording(listener);
    }

    @Override
    public void stopRecording() {
        mVideoSurface.stopRecording();
    }

    @Override
    public boolean isRecording() {
        return mVideoSurface.mIsRecordingVideo;
    }

    private void openCamera(int width, int height) {
        try {
            String cameraId = mActiveCamera == null ? getDefaultCamera() : mActiveCamera;
            openCamera(cameraId, width, height);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"MissingPermission"})
    private void openCamera(String cameraId, int width, int height) throws CameraAccessException {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        mActiveCamera = cameraId;

        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        for (CameraSurface surface : mCameraSurfaces) {
            surface.initialize(map);
        }

        configureTransform(width, height);
        manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of the TextureView is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        int rotation = getDisplayRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSurface.getHeight(), mPreviewSurface.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        setTransform(matrix);
    }

    private int getDisplayRotation() {
        return getDisplay().getRotation();
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (mCameraDevice == null || !isAvailable()) {
            return;
        }
        try {
            SurfaceTexture texture = getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSurface.getWidth(), mPreviewSurface.getHeight());

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);

            List<Surface> surfaces = new ArrayList<>();

            // The preview is, well, the preview. This surface draws straight to the CameraView
            surfaces.add(mPreviewSurface.getSurface());
            mPreviewBuilder.addTarget(mPreviewSurface.getSurface());

            // The video surface is for recording. When you call startRecording, it starts capturing
            // the bytes given to it.
            surfaces.add(mVideoSurface.getSurface());
            mPreviewBuilder.addTarget(mVideoSurface.getSurface());

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // When the session is ready, we start displaying the preview.
                    mCaptureSession = cameraCaptureSession;
                    try {
                        // Auto focus should be continuous for camera preview.
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        // Finally, we start displaying the camera preview.
                        mPreviewRequest = mPreviewBuilder.build();
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
                        // Crashes on rotation. However, it does restore itself.
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "Configure failed");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private String getDefaultCamera() throws CameraAccessException {
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId;
            }
        }
        return manager.getCameraIdList()[0];
    }

    @Override
    public boolean hasFrontFacingCamera() {
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void toggleCamera() {
        int position = 0;

        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                if (cameraId.equals(mActiveCamera)) {
                    break;
                }
                position++;
            }
            openCamera(manager.getCameraIdList()[(position + 1) % manager.getCameraIdList().length], getWidth(), getHeight());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static abstract class CameraSurface {
        final CameraView mCameraView;

        Size mSize;

        CameraSurface(CameraView cameraView) {
            mCameraView = cameraView;
        }

        abstract void initialize(StreamConfigurationMap map);

        void initialize(Size size) {
            mSize = size;
        }

        int getWidth() {
            return mSize.getWidth();
        }

        int getHeight() {
            return mSize.getHeight();
        }

        abstract void close();

        abstract Surface getSurface();
    }

    private static final class PreviewSurface extends CameraSurface {
        /**
         * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
         * width and height are at least as large as the respective requested values.
         *
         * @param choices     The list of sizes that the camera supports for the intended output class
         * @param width       The minimum desired width
         * @param height      The minimum desired height
         * @return The optimal {@code Size}, or an arbitrary one if none were big enough
         */
        private static Size chooseOptimalSize(Size[] choices, int width, int height) {
            // Collect the supported resolutions that are at least as big as the preview Surface
            List<Size> bigEnough = new ArrayList<>();
            for (Size option : choices) {
                if (option.getWidth() >= width && option.getHeight() >= height) {
                    bigEnough.add(option);
                }
            }

            // Pick the smallest of those, assuming we found any
            if (bigEnough.size() > 0) {
                return Collections.min(bigEnough, new CompareSizesByArea());
            } else {
                Log.e(TAG, "Couldn't find any suitable preview size");
                return choices[0];
            }
        }

        private Surface mSurface;

        PreviewSurface(CameraView cameraView) {
            super(cameraView);
        }

        void initialize(StreamConfigurationMap map) {
            super.initialize(chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), mCameraView.getWidth(), mCameraView.getHeight()));

            SurfaceTexture texture = mCameraView.getSurfaceTexture();
            texture.setDefaultBufferSize(getWidth(), getHeight());

            // This is the output Surface we need to start preview.
            mSurface = new Surface(texture);

        }

        @Override
        Surface getSurface() {
            return mSurface;
        }

        @Override
        void close() {

        }
    }

    private static final class PhotoSurface extends CameraSurface {

        /**
         * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
         * still image is ready to be saved.
         */
        private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                mCameraView.mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), getFile()));
            }
        };

        private ImageReader mImageReader;

        /**
         * Callback for when the video has saved
         */
        private CameraListener mPhotoListener;

        PhotoSurface(CameraView cameraView) {
            super(cameraView);
        }

        void initialize(StreamConfigurationMap map) {
            super.initialize(Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea()));

            mImageReader = ImageReader.newInstance(getWidth(), getHeight(), ImageFormat.JPEG, 2 /*maxImages*/);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraView.mBackgroundHandler);
        }

        private File getFile() {
            return new File(mCameraView.getContext().getCacheDir(), TEMP_FILE_PIC);
        }

        void takePicture(CameraListener listener) {
            mPhotoListener = listener;
            mCameraView.lockFocus();
        }

        void onPictureTaken() {
            mPhotoListener.onCaptured(getFile());
        }

        @Override
        Surface getSurface() {
            return mImageReader.getSurface();
        }

        @Override
        void close() {
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
            getFile().delete();
        }
    }

    private static final class VideoSurface extends CameraSurface {
        private static Size chooseVideoSize(Size[] choices) {
            for (Size size : choices) {
                if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                    return size;
                }
            }
            Log.e(TAG, "Couldn't find any suitable video size");
            return choices[0];
        }

        /**
         * Whether the app is recording video now
         */
        private boolean mIsRecordingVideo;

        /**
         * The recorder that handles saving the frames as a mp4
         */
        private MediaRecorder mMediaRecorder;

        /**
         * Callback for when the video has saved
         */
        private CameraListener mVideoListener;

        VideoSurface(CameraView cameraView) {
            super(cameraView);
        }

        void initialize(StreamConfigurationMap map) {
            super.initialize(chooseVideoSize(map.getOutputSizes(MediaRecorder.class)));

            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(getFile().getAbsolutePath());
            mMediaRecorder.setVideoEncodingBitRate(10000000);
            mMediaRecorder.setVideoFrameRate(30);
            mMediaRecorder.setVideoSize(getWidth(), getHeight());
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            int rotation = mCameraView.getDisplayRotation();
            int orientation = ORIENTATIONS.get(rotation);
            mMediaRecorder.setOrientationHint(orientation);
            try {
                mMediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        Surface getSurface() {
            return mMediaRecorder.getSurface();
        }

        private File getFile() {
            return new File(mCameraView.getContext().getCacheDir(), TEMP_FILE_VID);
        }

        void startRecording(CameraListener listener) {
            mVideoListener = listener;
            try {
                mIsRecordingVideo = true;
                mMediaRecorder.start();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        void stopRecording() {
            mIsRecordingVideo = false;
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            if (mVideoListener != null) {
                mVideoListener.onCaptured(getFile());
            }
        }

        @Override
        void close() {
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        }
    }
}
