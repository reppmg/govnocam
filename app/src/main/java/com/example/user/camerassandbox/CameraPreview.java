package com.example.user.camerassandbox;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;
import static android.content.Context.WINDOW_SERVICE;

/**
 * Created by Maxim.r on 8/2/2017.
 */
@SuppressWarnings("deprecation")
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Context mContext;
    private int mPreviewWidth = 1280;
    private int mPreviewHeight = 720;
    private boolean mIsFrontCamera = true;
    private int mRotation;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        mContext = context;
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);

        mPreviewWidth = mCamera.getParameters().getPreviewSize().width;
        mPreviewHeight = mCamera.getParameters().getPreviewSize().height;

    }


    private void calculateAndSetSurfaceSize() {
        WindowManager wm = (WindowManager) mContext.getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        Point size = new Point();
        display.getSize(size);

        int displayWidth = size.x;
        int displayHeight = size.y;
        float heightDiff = (float) mPreviewHeight / (float) displayHeight;
        float widthDiff = (float) mPreviewWidth / (float) displayWidth;
        float displayRatio = 1 / Math.max(heightDiff, widthDiff);

        int calculatedWidth = (int) (mPreviewWidth * displayRatio);
        int calculatedHeight = (int) (mPreviewHeight * displayRatio);

        mHolder.setFixedSize(calculatedWidth, calculatedHeight);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            if (mCamera == null) {
                return;
            }
            calculateAndSetSurfaceSize();
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
        } catch (RuntimeException e) {
            //for some reason it's called after activity was destroyed and camera released (on blocked screen)
        }
    }


    public void chosePreviewResolution() {
        Camera.Parameters params = mCamera.getParameters();

        Display display = ((WindowManager) mContext.getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        mRotation = display.getRotation();

        switch (mRotation) {
            case Surface.ROTATION_0:
                mCamera.setDisplayOrientation(90);
                break;
            case Surface.ROTATION_90:
                mCamera.setDisplayOrientation(0);
                break;
            case Surface.ROTATION_180:
                mCamera.setDisplayOrientation(270);
                break;
            case Surface.ROTATION_270:
                mCamera.setDisplayOrientation(180);
        }

        Point size = new Point();
        display.getSize(size);

        int displayWidth = size.x;
        int displayHeight = size.y;
        if (displayHeight > displayWidth){
            displayHeight += displayWidth - (displayWidth = displayHeight);
        }
        double resolutionByTen = Math.floor((double) displayWidth / displayHeight * 10);

        int previewWidth = mPreviewWidth;
        int previewHeight = mPreviewHeight;

        if (previewHeight > previewWidth) {
            previewWidth += previewHeight - (previewHeight = previewWidth);
        }

        for (Camera.Size previewSize : params.getSupportedPreviewSizes()) {
            if (previewSize.width == previewWidth && previewSize.height == previewHeight) {
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
                break;
            } else {
                if (Math.abs(Math.floor((double) 10 * previewSize.width / previewSize.height) - resolutionByTen) < 0.01) { //1.7 ratio for 16:9 resolutions
                    previewWidth = previewSize.width;
                    previewHeight = previewSize.height;
                    break;
                }
            }
        }
        params.setPreviewSize(previewWidth, previewHeight);
        mCamera.setParameters(params);

    }


    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.


        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }
        mPreviewWidth = holder.getSurfaceFrame().width();
        mPreviewHeight = holder.getSurfaceFrame().height();

        //Это, наверное, один из самых страшшных костылей в моей жизни.
        //Если вызвать сначала stopReview, а потом chosePictureResolution (правильный путь),
        //то на некоторых девайсах при повороте экрана растягивается и поворачивается
        // изображение до поворота, и через треть секунды появляется нормльное.
        boolean resolutionChosen = false;
        try {
            chosePreviewResolution();
            resolutionChosen = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }
        if (!resolutionChosen)
            chosePreviewResolution();

        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e) {
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsFrontCamera) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            Rect touchRect = new Rect(
                    (int) (x - 100),
                    (int) (y - 100),
                    (int) (x + 100),
                    (int) (y + 100));

            Rect targetFocusRect = new Rect(
                    touchRect.left * 2000 / this.getWidth() - 1000,
                    touchRect.top * 2000 / this.getHeight() - 1000,
                    touchRect.right * 2000 / this.getWidth() - 1000,
                    touchRect.bottom * 2000 / this.getHeight() - 1000);

            doTouchFocus(targetFocusRect);

        }

        return false;
    }


    public void doTouchFocus(final Rect tfocusRect) {
        if (tfocusRect == null) return;
        try {
            Camera.Parameters param = mCamera.getParameters();
            List<Camera.Area> focusList = new ArrayList<>();
            Camera.Area focusArea = new Camera.Area(tfocusRect, 1000);
            focusList.add(focusArea);

            param.setFocusAreas(focusList);
            mCamera.setParameters(param);
            mCamera.autoFocus(manualAutoFocusCallback);

        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "Unable to autofocus");
        }
    }

    public void setCamera(boolean isFrontCamera, Camera camera) {
        this.mIsFrontCamera = isFrontCamera;
        mCamera = camera;
    }

    private Camera.AutoFocusCallback manualAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {
                try {
                    mCamera.cancelAutoFocus();
                } catch (Exception ex) {
                }

            }
        }
    };

}
