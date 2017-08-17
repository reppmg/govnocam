package com.example.user.camerassandbox;

import android.Manifest;
import android.content.Context;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int LAST_RESORT_PICTURE_WIDTH = 176;
    private static final int LAST_RESORT_PICTURE_HEIGHT = 144;
    private static final int HIGHEST_PICTURE_HEIGHT = 721;
    private static final double ASPECT_RATIO = 1.7;
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int FRAME_RATE = 30;
    private static final int VIDEO_BIT_RATE = 4000000;
    private static final int AUDIO_SAMPLING_RATE = 44100;
    private static final int AUDIO_BIT_RATE = 126000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
        }

        final Camera camera = getFrontCameraInstance();
        try {
            CameraPreview cameraPreview = new CameraPreview(this, camera);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPictureSize(1280, 720);
            parameters.setPreviewSize(1280, 720);
            camera.setParameters(parameters);
            final MediaRecorder mediaRecorder = new MediaRecorder();

            // Step 1: Unlock and set camera to MediaRecorder
            camera.unlock();
            mediaRecorder.setCamera(camera);

            // Step 2: Set sources
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
            //Этот костыл здесь за тем, что несмотря на наличие профиля QUALITY_HIGH,
            //при его использовании на некоторых девайсах возникает RuntimeException
            if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P))
                mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
            else
                mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

            mediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            mediaRecorder.setVideoFrameRate(FRAME_RATE);
            mediaRecorder.setVideoEncodingBitRate(VIDEO_BIT_RATE); // ~3 megabits
            mediaRecorder.setAudioSamplingRate(AUDIO_SAMPLING_RATE);
            mediaRecorder.setAudioEncodingBitRate(AUDIO_BIT_RATE);


            // Step 4: Set output file
            mediaRecorder.setOutputFile(getOutputMediaFile().toString());

            // Step 5: Set the preview output
            mediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());
            mediaRecorder.prepare();
            mediaRecorder.start();
            new CountDownTimer(2000, 2000) {
                @Override
                public void onTick(long l) {

                }

                @Override
                public void onFinish() {
                    mediaRecorder.stop();
                    mediaRecorder.reset();
                    mediaRecorder.release();
                    finish();
                    camera.lock();
                    camera.release();

                }
            }.start();
        } catch (Exception e) {
            camera.lock();
            camera.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//
    }
    public Camera getFrontCameraInstance() {
        int cameraCount;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = Camera.open(camIdx);
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(camIdx, info);
                    if (info.canDisableShutterSound) {
                        if (Build.VERSION.SDK_INT >= 17)
                            cam.enableShutterSound(false);
                    }
                } catch (RuntimeException e) {

                }
            }
        }
        return cam;
    }

    private File getOutputMediaFile() {
        //this will save file in accessible destination.
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Test");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "VID_" + timeStamp + ".mp4");

        return mediaFile;
    }
}
