/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui.camera.x;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.os.SystemClock;
import android.util.Rational;
import android.util.SizeF;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.extensions.ExtensionMode;
import androidx.camera.extensions.ExtensionsManager;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.google.common.util.concurrent.ListenableFuture;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraDelegate;
import org.thunderdog.challegram.ui.camera.CameraFeatures;
import org.thunderdog.challegram.ui.camera.CameraManager;
import org.thunderdog.challegram.ui.camera.CameraQrBridge;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraManagerX extends CameraManager<PreviewView> {
  public CameraManagerX (Context context, CameraDelegate delegate) {
    super(context, delegate);
  }

  @Override
  protected PreviewView onCreateView () {
    PreviewView view = new PreviewView(context);
    view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
      prepareBitmaps(v.getMeasuredWidth(), v.getMeasuredHeight())
    );
    view.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    return view;
  }

  private boolean isOpen;
  private ProcessCameraProvider cameraProvider;
  private ImageCapture imageCapture;
  private VideoCapture<Recorder> videoCapture;
  private Recording videoRecording;
  private VideoRecordEvent videoRecordStatus;
  private Preview preview;
  private int previewRotation;
  private Camera camera;
  private int flashMode = ImageCapture.FLASH_MODE_OFF;
  private boolean originalFacing;
  private CameraQrBridge cameraQrBridge;

  @Override
  public void openCamera () {
    this.isOpen = true;
    this.originalFacing = preferFrontFacingCamera();
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
    cameraProviderFuture.addListener(() -> {
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        if (isOpen) {
          this.cameraProvider = cameraProvider;
          this.bindPreview();
        } else {
          cameraProvider.unbindAll();
        }
      } catch (ExecutionException | InterruptedException e) {
        // No errors need to be handled for this Future.
        // This should never be reached.
      }
    }, ContextCompat.getMainExecutor(context));
  }

  @Override
  protected void onParentSizeChanged (int width, int height) {
    if (Settings.instance().getCameraAspectRatioMode() == Settings.CAMERA_RATIO_FULL_SCREEN) {
      if (width == 0 || height == 0)
        return;
      Rational aspectRatioCustom = null;
      int aspectRatio = AspectRatio.RATIO_16_9;
      float ratio = (float) Math.max(width, height) / (float) Math.min(width, height);
      if (ratio == 16f / 9f) {
        aspectRatio = AspectRatio.RATIO_16_9;
      } else if (ratio == 4f / 3f) {
        aspectRatio = AspectRatio.RATIO_4_3;
      } else if (ratio == 1f) {
        aspectRatioCustom = new Rational(1, 1);
      } else {
        aspectRatioCustom = new Rational(Math.min(width, height), Math.max(width, height));
      }
      bindPreview();
    }
  }

  private static android.util.Size toSize (Rational rational, int surfaceRotation) {
    double maxSize = 1920;
    double minSize = maxSize * rational.doubleValue();
    return new android.util.Size((int) maxSize, (int) minSize - ((int) minSize) % 2);
  }

  @SuppressWarnings("RestrictedApi")
  private void bindPreview () {
    if (!isOpen || isPaused || cameraProvider == null)
      return;

    // Must unbind the use-cases before rebinding them
    cameraProvider.unbindAll();

    AspectRatioStrategy aspectRatioStrategy;
    int aspectRatioMode = Settings.instance().getCameraAspectRatioMode();
    switch (aspectRatioMode) {
      case Settings.CAMERA_RATIO_FULL_SCREEN:
      case Settings.CAMERA_RATIO_16_9:
        aspectRatioStrategy = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY;
        break;
      case Settings.CAMERA_RATIO_1_1:
      case Settings.CAMERA_RATIO_4_3:
      default:
        aspectRatioStrategy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY;
        break;
    }

    ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
      .setAspectRatioStrategy(aspectRatioStrategy)
      .build();

    final int lensFacing = preferFrontFacingCamera() ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;

    CameraSelector cameraSelector = new CameraSelector.Builder()
      .requireLensFacing(lensFacing)
      .build();
    try {
      if (!cameraProvider.hasCamera(cameraSelector)) {
        Log.w(Log.TAG_CAMERA, "Now camera with facing: %d", lensFacing);
        cameraSelector = new CameraSelector.Builder().build();
      }
    } catch (CameraInfoUnavailableException e) {
      Log.e(Log.TAG_CAMERA, "Unable to camera %d", lensFacing);
    }

    // XGram: the phone's own camera mode (Night, Portrait, HDR) takes the photos (com.fahadali.xgram.camera)
    xgramBaseSelector = cameraSelector;
    boolean xgramExtension = false;
    if (xgramListener != null && xgramMode != ExtensionMode.NONE && !delegate.useQrScanner()) {
      try {
        if (xgramExtensions != null && xgramExtensions.isExtensionAvailable(cameraSelector, xgramMode)) {
          cameraSelector = xgramExtensions.getExtensionEnabledCameraSelector(cameraSelector, xgramMode);
          xgramExtension = true;
        } else {
          xgramMode = ExtensionMode.NONE;
        }
      } catch (Throwable t) {
        Log.e(Log.TAG_CAMERA, "XGram: camera mode %d unavailable", t, xgramMode);
        xgramMode = ExtensionMode.NONE;
      }
    }

    Preview.Builder previewBuilder = new Preview.Builder()
      .setTargetRotation(previewRotation = getSurfaceRotation())
      .setResolutionSelector(resolutionSelector);
    preview = previewBuilder.build();

    ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
      .setFlashMode(flashMode)
      .setTargetRotation(getSurfaceRotation())
      .setResolutionSelector(resolutionSelector);
    imageCapture = imageCaptureBuilder.build();

    QualitySelector qualitySelector = QualitySelector.fromOrderedList(
      Arrays.asList(Quality.FHD, Quality.HD, Quality.SD),
      FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
    );
    Recorder recorder = new Recorder.Builder()
      .setQualitySelector(qualitySelector)
      .build();
    videoCapture = new VideoCapture.Builder<>(recorder)
      .setCaptureType(UseCaseConfigFactory.CaptureType.VIDEO_CAPTURE)
      .setTargetRotation(getSurfaceRotation())
      .setResolutionSelector(resolutionSelector)
      .build();

    ImageAnalysis imageAnalyzer;

    boolean needQrScanner = delegate.useQrScanner();
    if (needQrScanner) {
      imageAnalyzer = new ImageAnalysis.Builder()
        .setTargetRotation(getSurfaceRotation())
        .setResolutionSelector(resolutionSelector)
        .build();
      if (cameraQrBridge == null) {
        cameraQrBridge = new CameraQrBridge(this);
      }
      imageAnalyzer.setAnalyzer(cameraQrBridge.getBackgroundExecutor(), proxy -> cameraQrBridge.processImage(proxy));
    } else {
      imageAnalyzer = null;
    }

    try {
      configureCameraX();
      // A variable number of use-cases can be passed here -
      // camera provides access to CameraControl & CameraInfo
      if (needQrScanner) {
        // We probably don't want to take photos or videos while scanning QR codes. (Also, there are 3 use case limit in CameraX)
        this.camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageAnalyzer);
      } else if (xgramExtension) {
        // XGram: the phone's modes take photos only (no video with an extension)
        this.camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageCapture);
      } else {
        this.camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageCapture, videoCapture);
      }
      xgramVideoBound = !needQrScanner && !xgramExtension;
    } catch (Exception e) {
      Log.e(Log.TAG_CAMERA, "Use case binding failed", e);
      if (xgramExtension) {
        // XGram: the mode could not be bound: the usual camera instead
        xgramMode = ExtensionMode.NONE;
        bindPreview();
      }
      return;
    }

    Preview.SurfaceProvider surfaceProvider = cameraView.getSurfaceProvider();
    preview.setSurfaceProvider(surfaceProvider);
    UI.post(delegate::onRenderedFirstFrame, 800);

    reportFlashMode();
    xgramOnBound(); // XGram
  }

  private static boolean cameraXConfigured;

  @SuppressWarnings("UnsafeOptInUsageError")
  private static synchronized void configureCameraX () {
    if (!cameraXConfigured) {
      ProcessCameraProvider.configureInstance(Camera2Config.defaultConfig());
      cameraXConfigured = true;
    }
  }

  @Override
  public void closeCamera () {
    if (this.isOpen) {
      xgramStopZoom(); // XGram
      if (cameraProvider != null) {
        cameraProvider.unbindAll();
        preview = null;
        videoRecordStatus = null;
        if (videoRecording != null) {
          videoRecording.close();
          videoRecording = null;
        }
        videoCapture = null;
        imageCapture = null;
        cameraProvider = null;
        camera = null;
      }
      this.isOpen = false;
    }
  }

  @Override
  protected void onDisplayRotationChanged (int rotation) {
    if (cameraProvider != null) {
      bindPreview();
    }
  }

  private boolean switchingCamera;

  @Override
  public void switchCamera () {
    if (switchingCamera)
      return;
    if (cameraProvider != null) {
      int facing = !preferFrontFacingCamera() ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
      try {
        long speed = SystemClock.uptimeMillis();
        if (!cameraProvider.hasCamera(new CameraSelector.Builder().requireLensFacing(facing).build())) {
          Log.w(Log.TAG_CAMERA, "Camera is not available, facing: %d", facing);
          return;
        }
        Log.w("checked camera availability in %dms", SystemClock.uptimeMillis() - speed);
      } catch (CameraInfoUnavailableException e) {
        Log.e(Log.TAG_CAMERA, "Camera info unavailable, facing: %d", e, facing);
        return;
      }
      switchingCamera = true;
      setPreferFrontFacingCamera(!preferFrontFacingCamera());
      delegate.onCameraSourceChange(false, originalFacing != preferFrontFacingCamera(), preferFrontFacingCamera());
      delegate.onResetRenderState(true, () -> {
        bindPreview();
        delegate.onCameraSourceChange(true, originalFacing != preferFrontFacingCamera(), preferFrontFacingCamera());
        switchingCamera = false;
      });
    }
  }

  @Override
  public void switchFlashMode () {
    if (camera != null) {
      if (camera.getCameraInfo().hasFlashUnit()) {
        switch (flashMode) {
          case ImageCapture.FLASH_MODE_AUTO:
            flashMode = ImageCapture.FLASH_MODE_ON;
            break;
          case ImageCapture.FLASH_MODE_ON:
            flashMode = ImageCapture.FLASH_MODE_OFF;
            break;
          case ImageCapture.FLASH_MODE_OFF:
            flashMode = isCapturingVideo() ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_AUTO;
            break;
          default:
            return;
        }
        imageCapture.setFlashMode(flashMode);
        reportFlashMode();
      } else if (preferFrontFacingCamera()) {
        this.flashMode = this.flashMode == ImageCapture.FLASH_MODE_OFF ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF;
        reportFlashMode();
      }
    }
  }

  private void reportFlashMode () {
    if (camera != null) {
      int reportFlashMode;
      if (camera.getCameraInfo().hasFlashUnit()) {
        switch (flashMode) {
          case ImageCapture.FLASH_MODE_AUTO:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_AUTO;
            break;
          case ImageCapture.FLASH_MODE_ON:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_ON;
            break;
          case ImageCapture.FLASH_MODE_OFF:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
            break;
          default:
            return;
        }
      } else if (preferFrontFacingCamera()) {
        reportFlashMode = flashMode == ImageCapture.FLASH_MODE_OFF ? CameraFeatures.FEATURE_FLASH_OFF : CameraFeatures.FEATURE_FLASH_FAKE;
      } else {
        reportFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
      }
      delegate.onFlashModeChanged(reportFlashMode);
    }
  }

  @Override
  protected boolean getBitmap (Bitmap bitmap) {
    for (int i = 0; i < cameraView.getChildCount(); i++) {
      View view = cameraView.getChildAt(i);
      if (view instanceof TextureView) {
        if (((TextureView) view).isAvailable()) {
          ((TextureView) view).getBitmap(bitmap);
          return true;
        }
        return false;
      }
    }
    return false;
  }

  @Override
  public boolean isCameraActive () {
    return isOpen && cameraProvider != null;
  }

  @Override
  public float getMaxZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getMaxZoomRatio() : 0f;
  }

  @Override
  public float getMinZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getMinZoomRatio() : 0f;
  }

  @Override
  public float getCurrentZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getZoomRatio() : 0f;
  }

  @Override
  public float getMinZoomStep () {
    return .1f;
  }

  @Override
  protected void onRequestZoom (float zoom) {
    if (camera != null && getCurrentZoom() != zoom) {
      camera.getCameraControl().setZoomRatio(zoom);
      onZoomChanged(zoom);
    }
  }

  @Override
  protected void onResetPreferences () {
    this.flashMode = ImageCapture.FLASH_MODE_OFF;
  }

  @Override
  protected void onTakePhoto (int trimWidth, int trimHeight, int outRotation) {
    if (imageCapture != null) {
      final File outFile = getOutputFile(false);
      if (outFile == null) {
        onTakeMediaError(false);
        return;
      }

      boolean ok;
      try {
        ImageCapture.OutputFileOptions.Builder b = new ImageCapture.OutputFileOptions.Builder(outFile);
        if (preferFrontFacingCamera() && !Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_NO_FLIP)) {
          ImageCapture.Metadata metadata = new ImageCapture.Metadata();
          metadata.setReversedHorizontal(true);
          b.setMetadata(metadata);
        }
        imageCapture.takePicture(b.build(), ContextCompat.getMainExecutor(context), new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved (@NonNull ImageCapture.OutputFileResults ignored) {
            U.toGalleryFile(outFile, false, file -> {
              if (file != null) {
                onTakeMediaResult(file, false);
              } else {
                Log.e(Log.TAG_CAMERA, "Output file still does not exist!");
                onTakeMediaError(false);
              }
            });
          }

          @Override
          public void onError (@NonNull ImageCaptureException e) {
            Log.e(Log.TAG_CAMERA, "Cannot take photo", e);
            onTakeMediaError(false);
          }
        });
        ok = true;
      } catch (Throwable t) {
        Log.e(Log.TAG_CAMERA, "Unable to take photo", t);
        onTakeMediaError(false);
        ok = false;
      }
      if (ok) {
        delegate.onPerformSuccessHint(false);
      }
    }
  }

  @Override
  protected boolean onStartVideoCapture (int outRotation) {
    if (xgramListener != null && !xgramVideoBound) {
      xgramListener.onVideoUnavailable(xgramMode); // XGram: a camera mode for photos only
      return false;
    }
    if (videoCapture != null) {
      boolean success;
      File outFile = getOutputFile(true);
      boolean forceEnableTorch = flashMode == ImageCapture.FLASH_MODE_ON && camera.getCameraInfo().hasFlashUnit();
      try {
        if (!outFile.createNewFile()) {
          return false;
        }
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Unable to create output file for video", t);
        return false;
      }
      if (forceEnableTorch) {
        forceEnableTorch();
      }
      try {
        AtomicBoolean isFinished = new AtomicBoolean(false);
        videoRecording = videoCapture.getOutput()
          .prepareRecording(context, new FileOutputOptions.Builder(outFile).build())
          .withAudioEnabled()
          .start(ContextCompat.getMainExecutor(context), event -> {
            if (!(event instanceof VideoRecordEvent.Status)) {
              videoRecordStatus = event;
            }
            if (event instanceof VideoRecordEvent.Start) {
              VideoRecordEvent.Start start = (VideoRecordEvent.Start) event;
              setTakingVideo(true, SystemClock.uptimeMillis());
            } else if (event instanceof VideoRecordEvent.Finalize) {
              if (isFinished.getAndSet(true)) {
                return;
              }
              VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;

              if (finalize.hasError()) {
                if (outFile.exists()) {
                  if (!outFile.delete()) {
                    Log.e(Log.TAG_CAMERA, "Unable to delete video output file");
                  }
                }
                setTakingVideo(false, -1);
                Log.e(Log.TAG_CAMERA, "Failed to capture video: %d", finalize.getCause(), finalize.getError());
                onTakeMediaError(true);
              } else {
                U.toGalleryFile(outFile, true, file -> {
                  setTakingVideo(false, -1);
                  onTakeMediaResult(file, true);
                });
              }
            }
          });
        success = true;
      } catch (Throwable t) {
        Log.e("Cannot start recording video", t);
        success = false;
      }
      if (!success) {
        disableTorch();
      }
      return success;
    }
    return false;
  }

  @SuppressWarnings("RestrictedApi")
  @Override
  protected void onFinishOrCancelVideoCapture () {
    if (videoRecording != null) {
      videoRecording.stop();
      onVideoRecordingFinished();
    }
  }

  private boolean torchForceEnabled;

  private void forceEnableTorch () {
    if (!torchForceEnabled) {
      torchForceEnabled = true;
      camera.getCameraControl().enableTorch(true);
    }
  }

  private void disableTorch () {
    if (torchForceEnabled) {
      torchForceEnabled = false;
      camera.getCameraControl().enableTorch(false);
    }
  }

  private void onVideoRecordingFinished () {
    disableTorch();
    delegate.onVideoCaptureEnded();
  }

  @Override
  public void destroy () {
    if (cameraQrBridge != null) {
      cameraQrBridge.destroy();
      cameraQrBridge = null;
    }
  }

  private boolean isPaused;

  @Override
  public void pauseCamera () {
    isPaused = true;
    if (cameraProvider != null) {
      cameraProvider.unbindAll();
    }
  }

  @Override
  public void resumeCamera () {
    isPaused = false;
    if (isOpen) {
      bindPreview();
    }
  }

  // XGram: lenses, zoom, focus and exposure, and the phone's own camera modes (Night, Portrait, HDR), for XGram's
  // camera controls (com.fahadali.xgram.camera.XgramCameraControls). Nothing here runs while no controls listen.

  public interface XgramListener {
    /** A camera was bound: its lenses, zoom range, exposure and modes may have changed. */
    void onCameraBound ();

    /** The zoom ratio now (pinch, a lens, the shutter's drag, the volume keys). */
    void onZoomRatio (float ratio);

    /** The shutter was held in a camera mode that takes photos only. */
    void onVideoUnavailable (int mode);
  }

  private @Nullable XgramListener xgramListener;
  private @Nullable ExtensionsManager xgramExtensions;
  private boolean xgramExtensionsRequested, xgramVideoBound = true;
  private @Nullable CameraSelector xgramBaseSelector;
  private int xgramMode = ExtensionMode.NONE;
  private int[] xgramModes = {ExtensionMode.NONE};
  private float[] xgramStops = {1f};
  private @Nullable ValueAnimator xgramZoomAnimator;
  private @Nullable Camera xgramObservedCamera;
  private final Observer<ZoomState> xgramZoomObserver = state -> {
    if (xgramListener == null || state == null) {
      return;
    }
    if (xgramStops.length == 1 && state.getMaxZoomRatio() > 1f && camera != null) {
      // The zoom range came after the camera was bound: its lenses now
      xgramStops = xgramLensStops(camera.getCameraInfo(), state.getMinZoomRatio(), state.getMaxZoomRatio());
      xgramListener.onCameraBound();
    }
    xgramListener.onZoomRatio(state.getZoomRatio());
  };

  public void xgramSetListener (@Nullable XgramListener listener) {
    this.xgramListener = listener;
    if (listener == null) {
      xgramStopZoom();
      if (xgramObservedCamera != null) {
        xgramObservedCamera.getCameraInfo().getZoomState().removeObserver(xgramZoomObserver);
        xgramObservedCamera = null;
      }
      // The usual camera from the next binding on (never bound again from here: this manager may be on its way out)
      xgramMode = ExtensionMode.NONE;
    } else if (camera != null) {
      xgramOnBound();
    }
  }

  private void xgramOnBound () {
    if (camera == null || xgramListener == null) {
      return;
    }
    if (xgramObservedCamera != camera) {
      if (xgramObservedCamera != null) {
        xgramObservedCamera.getCameraInfo().getZoomState().removeObserver(xgramZoomObserver);
      }
      xgramObservedCamera = camera;
      camera.getCameraInfo().getZoomState().observe((LifecycleOwner) context, xgramZoomObserver);
    }
    xgramStops = xgramLensStops(camera.getCameraInfo(), getMinZoom(), getMaxZoom());
    if (!xgramExtensionsRequested && cameraProvider != null) {
      xgramExtensionsRequested = true;
      final ListenableFuture<ExtensionsManager> future = ExtensionsManager.getInstanceAsync(context, cameraProvider);
      future.addListener(() -> {
        try {
          xgramExtensions = future.get();
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "XGram: no camera extensions", t);
        }
        xgramUpdateModes();
        if (xgramListener != null) {
          xgramListener.onCameraBound();
        }
      }, ContextCompat.getMainExecutor(context));
    }
    xgramUpdateModes();
    xgramListener.onCameraBound();
  }

  private void xgramUpdateModes () {
    List<Integer> modes = new ArrayList<>();
    if (xgramExtensions != null && xgramBaseSelector != null && !delegate.useQrScanner()) {
      // In the order a camera app shows them: Night, Photo, Portrait, HDR
      for (int mode : new int[] {ExtensionMode.NIGHT, ExtensionMode.NONE, ExtensionMode.BOKEH, ExtensionMode.HDR}) {
        try {
          if (mode == ExtensionMode.NONE || xgramExtensions.isExtensionAvailable(xgramBaseSelector, mode)) {
            modes.add(mode);
          }
        } catch (Throwable ignored) { }
      }
    }
    if (modes.isEmpty()) {
      modes.add(ExtensionMode.NONE);
    }
    int[] result = new int[modes.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = modes.get(i);
    }
    xgramModes = result;
  }

  /** The modes this lens can take photos in (ExtensionMode; NONE is the usual camera). */
  public int[] xgramModes () {
    return xgramModes;
  }

  public int xgramMode () {
    return xgramMode;
  }

  /** The zoom ratios of the lenses ("0.6", "1", "3"), or a 2× crop without a telephoto camera; at least {1}. */
  public float[] xgramLensStops () {
    return xgramStops;
  }

  public boolean xgramIsBusy () {
    return switchingCamera || isCapturingVideo();
  }

  /** Switches to a camera mode (ExtensionMode); the preview blurs while the camera is bound again. */
  public void xgramSetMode (int mode) {
    if (mode == xgramMode || switchingCamera || isCapturingVideo()) {
      return;
    }
    xgramMode = mode;
    if (cameraProvider == null) {
      return;
    }
    switchingCamera = true;
    delegate.onResetRenderState(true, () -> {
      bindPreview();
      switchingCamera = false;
    });
  }

  private void xgramStopZoom () {
    if (xgramZoomAnimator != null) {
      xgramZoomAnimator.cancel();
      xgramZoomAnimator = null;
    }
    xgramZoomPending = false;
  }

  private float xgramZoomTarget = -1f;
  private boolean xgramZoomPending;
  private long xgramZoomSentAt;
  private static final long XGRAM_ZOOM_INTERVAL_MS = 50L;

  /**
   * Zooms to {@code ratio}. One request per lens tap: every zoom change rebuilds the camera's repeating request, and a
   * stepped ramp (16 of them) made the phone's lens switch and the camera modes (an extension session) fall far behind.
   * A pinch ({@code fromGesture}) sends at most one request every 50 ms, always the latest ratio.
   */
  public void xgramSetZoom (float ratio, boolean fromGesture) {
    if (camera == null) {
      return;
    }
    final float min = getMinZoom(), max = getMaxZoom();
    final float target = max > 0f ? Math.max(min, Math.min(max, ratio)) : ratio;
    xgramZoomTarget = target;
    if (!fromGesture) {
      xgramStopZoom();
      xgramSendZoom();
      return;
    }
    long now = SystemClock.uptimeMillis();
    if (now - xgramZoomSentAt >= XGRAM_ZOOM_INTERVAL_MS) {
      xgramSendZoom();
    } else if (!xgramZoomPending) {
      xgramZoomPending = true;
      UI.post(() -> {
        if (xgramZoomPending) {
          xgramZoomPending = false;
          xgramSendZoom();
        }
      }, XGRAM_ZOOM_INTERVAL_MS - (now - xgramZoomSentAt));
    }
  }

  private void xgramSendZoom () {
    if (camera == null || xgramZoomTarget <= 0f) {
      return;
    }
    xgramZoomSentAt = SystemClock.uptimeMillis();
    camera.getCameraControl().setZoomRatio(xgramZoomTarget);
    onZoomChanged(xgramZoomTarget);
  }

  /** The zoom the controls asked for last (a pinch builds on it, not on the camera's late state); 0 before any. */
  public float xgramZoomTarget () {
    return xgramZoomTarget > 0f ? xgramZoomTarget : getCurrentZoom();
  }

  /**
   * Focuses and meters at a point of the preview (its own coordinates); {@code lock}: until the next tap (AE/AF lock).
   * False: the camera can't.
   */
  public boolean xgramFocusAt (float x, float y, boolean lock) {
    if (camera == null || cameraView == null) {
      return false;
    }
    try {
      MeteringPoint point = cameraView.getMeteringPointFactory().createPoint(x, y);
      FocusMeteringAction action = xgramFocusAction(point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB, lock);
      if (!camera.getCameraInfo().isFocusMeteringSupported(action)) {
        // A fixed-focus camera (most front cameras): exposure and white balance only
        action = xgramFocusAction(point, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AWB, lock);
        if (!camera.getCameraInfo().isFocusMeteringSupported(action)) {
          return false;
        }
      }
      camera.getCameraControl().startFocusAndMetering(action);
      return true;
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "XGram: cannot focus", t);
      return false;
    }
  }

  private static FocusMeteringAction xgramFocusAction (MeteringPoint point, int flags, boolean lock) {
    FocusMeteringAction.Builder builder = new FocusMeteringAction.Builder(point, flags);
    if (lock) {
      builder.disableAutoCancel();
    } else {
      builder.setAutoCancelDuration(5, TimeUnit.SECONDS);
    }
    return builder.build();
  }

  public void xgramCancelFocus () {
    if (camera != null) {
      camera.getCameraControl().cancelFocusAndMetering();
    }
  }

  /** The exposure compensation the camera supports (null before it is bound). */
  public @Nullable ExposureState xgramExposure () {
    return camera != null ? camera.getCameraInfo().getExposureState() : null;
  }

  public void xgramSetExposure (int index) {
    if (camera != null) {
      camera.getCameraControl().setExposureCompensationIndex(index);
    }
  }

  /** The lenses as zoom ratios: the ultra wide (the zoom's minimum below 1), 1, the telephoto cameras (35mm equivalents). */
  @SuppressWarnings("UnsafeOptInUsageError")
  public static float[] xgramLensStops (CameraInfo info, float minZoom, float maxZoom) {
    List<Float> stops = new ArrayList<>();
    stops.add(1f);
    if (maxZoom <= 0f) {
      return new float[] {1f};
    }
    if (minZoom > 0f && minZoom < .95f) {
      stops.add(Math.round(minZoom * 10f) / 10f);
    }
    float main = xgramEquivalentFocalLength(info);
    boolean hasTele = false;
    if (main > 0f) {
      try {
        for (CameraInfo physical : info.getPhysicalCameraInfos()) {
          float focal = xgramEquivalentFocalLength(physical);
          if (focal <= 0f) {
            continue;
          }
          float ratio = focal / main;
          if (ratio > 1.4f && ratio <= maxZoom + .05f) {
            stops.add(ratio >= 1.8f ? (float) Math.round(ratio) : Math.round(ratio * 10f) / 10f);
            hasTele = true;
          }
        }
      } catch (Throwable ignored) { }
    }
    if (!hasTele && maxZoom >= 2f) {
      stops.add(2f); // a 2× crop without a telephoto camera
    }
    Collections.sort(stops);
    List<Float> unique = new ArrayList<>();
    for (float stop : stops) {
      float clamped = Math.max(minZoom > 0f ? minZoom : stop, Math.min(maxZoom, stop));
      if (unique.isEmpty() || Math.abs(unique.get(unique.size() - 1) - clamped) >= .1f) {
        unique.add(clamped);
      }
    }
    float[] result = new float[unique.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = unique.get(i);
    }
    return result;
  }

  /** The lens's focal length as on a 35mm camera (0: unknown). */
  @SuppressWarnings("UnsafeOptInUsageError")
  private static float xgramEquivalentFocalLength (CameraInfo info) {
    try {
      Camera2CameraInfo camera2 = Camera2CameraInfo.from(info);
      float[] focalLengths = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
      SizeF sensor = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
      if (focalLengths == null || focalLengths.length == 0 || sensor == null) {
        return 0f;
      }
      double diagonal = Math.hypot(sensor.getWidth(), sensor.getHeight());
      return diagonal > 0 ? (float) (focalLengths[0] * 43.27 / diagonal) : 0f;
    } catch (Throwable t) {
      return 0f;
    }
  }
}
