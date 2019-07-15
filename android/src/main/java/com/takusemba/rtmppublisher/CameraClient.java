package com.takusemba.rtmppublisher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;

import io.flutter.plugin.common.PluginRegistry;

import static android.content.Context.WINDOW_SERVICE;

class CameraClient {

  private PluginRegistry.Registrar registrar;
  private Camera camera;
  private CameraMode mode;
  private SurfaceTexture surfaceTexture;

  private static final int desiredHeight = 1280;
  private static final int desiredWidth = 720;

  CameraClient(PluginRegistry.Registrar registrar, CameraMode mode) {
    this.registrar = registrar;
    this.mode = mode;
  }

  public static abstract class OnCameraOpened implements Runnable {
    private Camera.Parameters params;

    abstract void onCameraOpened(Camera.Parameters params);

    @Override
    public void run() {
      onCameraOpened(params);
    }
  }

  void open(final OnCameraOpened onCameraOpened) {
    initCamera(new PermissionRequester.PermissionsGranted() {
      @Override
      public void onResult(boolean allGranted) {
        if (!allGranted)
          return;
        onCameraOpened.params = camera.getParameters();
        setParameters(onCameraOpened.params);
        onCameraOpened.run();
      }
    });
  }

  CameraMode getMode() {
    return mode;
  }

  void swap() {
    close();

    mode = mode.swap();
    initCamera(new PermissionRequester.PermissionsGranted() {
      @Override
      public void onResult(boolean allGranted) {
        if (allGranted)
          startPreview(surfaceTexture);
      }
    });

  }

  void startPreview(SurfaceTexture surfaceTexture) {
    this.surfaceTexture = surfaceTexture;
    try {
      camera.setPreviewTexture(surfaceTexture);
      camera.startPreview();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void close() {
    if (camera != null) {
      camera.stopPreview();
      camera.release();
      camera = null;
    }
  }

  private void initCamera(final PermissionRequester.PermissionsGranted then) {
    PermissionRequester.requestPermission(registrar, Manifest.permission.CAMERA, new PermissionRequester.PermissionsGranted() {
      @Override
      public void onResult(boolean allGranted) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
          Camera.getCameraInfo(i, info);
          if (info.facing == mode.getId()) {
            camera = Camera.open(i);
            setRotation(info);
          }
        }
        if (camera == null) {
          Camera.open();
        }
        then.allGranted = allGranted;
        then.run();
      }
    });

  }

  private void setParameters(Camera.Parameters params) {
    boolean isDesiredSizeFound = false;
    for (Camera.Size size : params.getSupportedPreviewSizes()) {
      if (size.width == desiredWidth && size.height == desiredHeight) {
        params.setPreviewSize(desiredWidth, desiredHeight);
        isDesiredSizeFound = true;
      }
    }

    if (!isDesiredSizeFound) {
      Camera.Size ppsfv = params.getPreferredPreviewSizeForVideo();
      if (ppsfv != null) {
        params.setPreviewSize(ppsfv.width, ppsfv.height);
      }
    }

    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    params.setRecordingHint(true);

    camera.setParameters(params);

    int[] fpsRange = new int[2];
    params.getPreviewFpsRange(fpsRange);

    Display display =
      ((WindowManager) registrar.activity().getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

    if (display.getRotation() == Surface.ROTATION_0) {
      camera.setDisplayOrientation(90);
    } else if (display.getRotation() == Surface.ROTATION_270) {
      camera.setDisplayOrientation(180);
    }
  }

  private void setRotation(Camera.CameraInfo info) {
    WindowManager windowManager = (WindowManager) registrar.activity().getSystemService(Context.WINDOW_SERVICE);
    int rotation = windowManager.getDefaultDisplay().getRotation();
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }

    if (mode == CameraMode.FRONT) {
      degrees = (info.orientation + degrees) % 360;
      degrees = (360 - degrees) % 360;
    } else {
      degrees = (info.orientation - degrees + 360) % 360;
    }
    camera.setDisplayOrientation(degrees);
  }
}
