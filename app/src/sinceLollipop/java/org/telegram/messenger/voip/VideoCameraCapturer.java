/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.telegram.messenger.voip;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.unsorted.AppContext;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * XGram: the Java camera capturer tgcalls expects on Android (tgcalls/platform/android): AndroidContext builds it with
 * {@code <init>()V} on the thread that makes the capture and calls {@code onDestroy()V} on the tgcalls media thread
 * when the capture dies; VideoCameraCapturer.cpp calls {@code init(JZ)V} (again on the same object for every
 * switchToDevice), {@code onStateChanged(JI)V} (0 inactive, 1 paused, 2 active) and {@code onAspectRatioRequested(F)V}.
 * <p>
 * WebRTC's Camera2 (or Camera1) capturer runs on its own SurfaceTextureHelper thread with the call's shared EGL
 * context; every frame goes to the native video source (what the call sends) and to the local preview fan-out of the
 * host (the backend of this call). All camera work happens on the "XgramCamera" thread, never the main thread.
 * {@link #nativeGetJavaVideoCapturerObserver} is bound with RegisterNatives from tgvoip.cpp.
 */
@Keep
public final class VideoCameraCapturer {
  /** The backend of this capturer's call. Called on the camera thread. */
  public interface Host {
    @Nullable EglBase.Context eglContext ();
    /** The local preview fan-out. */
    @Nullable VideoSink localSink ();
    void onCaptureSwitched (boolean front, boolean ok);
    void onCaptureError (String reason);
    void onCaptureDisposed ();
  }

  private static volatile @Nullable Host nextHost;

  /** Set by the backend right before N.newVideoCapture, on the same thread: the new capturer talks to that host. */
  public static void setNextHost (@Nullable Host host) {
    nextHost = host;
  }

  private static final int WIDTH = 1280, HEIGHT = 720, FPS = 30;
  private static boolean loggedDevices;

  private final @Nullable Host host = nextHost;
  private final HandlerThread thread;
  private final Handler handler;

  private final Object observerLock = new Object();
  private @Nullable CapturerObserver nativeObserver; // guarded by observerLock

  // Camera thread only
  private @Nullable CameraEnumerator enumerator;
  private @Nullable CameraVideoCapturer capturer;
  private @Nullable SurfaceTextureHelper textureHelper;
  private @Nullable String deviceName;
  private boolean front, capturing, wantActive, disposed;
  /** The side last asked for, and the last native state: a resume reopens a camera that failed to open (null capturer). */
  private boolean requestedFront = true;
  private int lastState = -1;

  /** Frames go to the native source (what the call sends) and to the local preview. No allocation per frame. */
  private final CapturerObserver tee = new CapturerObserver() {
    @Override
    public void onCapturerStarted (boolean success) {
      synchronized (observerLock) {
        if (nativeObserver != null) {
          nativeObserver.onCapturerStarted(success);
        }
      }
    }

    @Override
    public void onCapturerStopped () {
      synchronized (observerLock) {
        if (nativeObserver != null) {
          nativeObserver.onCapturerStopped();
        }
      }
    }

    @Override
    public void onFrameCaptured (VideoFrame frame) {
      synchronized (observerLock) {
        if (nativeObserver != null) {
          nativeObserver.onFrameCaptured(frame);
        }
      }
      final Host h = host;
      final VideoSink local = h != null ? h.localSink() : null;
      if (local != null) {
        local.onFrame(frame);
      }
    }
  };

  private final CameraVideoCapturer.CameraEventsHandler events = new CameraVideoCapturer.CameraEventsHandler() {
    @Override
    public void onCameraError (String errorDescription) {
      // WebRTC's CameraCapturer stops itself after an error (Android 14 also disconnects the camera when the app
      // leaves the foreground): only the flag follows it
      handler.post(() -> {
        capturing = false;
        if (host != null && !disposed) {
          host.onCaptureError(errorDescription != null ? errorDescription : "error");
        }
      });
    }

    @Override
    public void onCameraDisconnected () {
      handler.post(() -> {
        capturing = false;
        if (host != null && !disposed) {
          host.onCaptureError("disconnected");
        }
      });
    }

    @Override
    public void onCameraFreezed (String errorDescription) {
      handler.post(() -> {
        stopCapture();
        if (host != null && !disposed) {
          host.onCaptureError("freeze");
        }
      });
    }

    @Override
    public void onCameraOpening (String cameraName) { }

    @Override
    public void onFirstFrameAvailable () { }

    @Override
    public void onCameraClosed () { }
  };

  @Keep
  public VideoCameraCapturer () {
    thread = new HandlerThread("XgramCamera");
    thread.start();
    handler = new Handler(thread.getLooper());
  }

  // Called from tgcalls (VideoCameraCapturer.cpp, AndroidContext.cpp)

  /** The tgcalls media thread, inside the native capturer's constructor: {@code ptr} is only valid during this call. */
  @Keep
  public void init (long ptr, boolean useFrontCamera) {
    CapturerObserver observer = nativeGetJavaVideoCapturerObserver(ptr);
    synchronized (observerLock) {
      nativeObserver = observer;
    }
    handler.post(() -> open(useFrontCamera));
  }

  @Keep
  public void onStateChanged (long ptr, int state) {
    handler.post(() -> applyState(state));
  }

  @Keep
  public void onAspectRatioRequested (float aspectRatio) { }

  /** The tgcalls media thread, when the capture dies (nothing sets it inactive before: this closes the camera). */
  @Keep
  public void onDestroy () {
    synchronized (observerLock) {
      nativeObserver = null; // no frame may reach a native source on its way out
    }
    handler.post(this::dispose);
    thread.quitSafely();
  }

  private static native CapturerObserver nativeGetJavaVideoCapturerObserver (long ptr);

  // Camera thread

  private void open (boolean useFront) {
    if (disposed) {
      return;
    }
    requestedFront = useFront;
    final Context context = AppContext.get();
    try {
      if (enumerator == null) {
        enumerator = Camera2Enumerator.isSupported(context) ? new Camera2Enumerator(context) : new Camera1Enumerator(true);
      }
      logDevicesOnce(context, enumerator);
      final String[] names = enumerator.getDeviceNames();
      String name = null;
      for (String candidate : names) {
        if (useFront ? enumerator.isFrontFacing(candidate) : enumerator.isBackFacing(candidate)) {
          name = candidate;
          break;
        }
      }
      if (name == null && names.length > 0) {
        name = names[0];
      }
      if (name == null) {
        if (host != null) {
          host.onCaptureError("no camera");
        }
        return;
      }
      if (capturer == null) {
        create(context, name);
      } else if (!name.equals(deviceName)) {
        if (capturing) {
          final String target = name;
          capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone (boolean isFrontCamera) {
              handler.post(() -> {
                front = isFrontCamera;
                deviceName = target;
                if (host != null && !disposed) {
                  host.onCaptureSwitched(isFrontCamera, true);
                }
              });
            }

            @Override
            public void onCameraSwitchError (String errorDescription) {
              handler.post(() -> {
                if (host != null && !disposed) {
                  host.onCaptureSwitched(front, false);
                }
              });
            }
          }, name);
        } else {
          releaseCapturer();
          create(context, name);
          if (host != null) {
            host.onCaptureSwitched(front, capturer != null);
          }
        }
      } else if (useFront != front) {
        // Only one camera on this side of the device
        if (host != null) {
          host.onCaptureSwitched(front, false);
        }
      } else if (capturing) {
        // The same camera again (fold / unfold): reopen, so the logical front camera follows the active display
        stopCapture();
        startCapture();
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "XGram video: cannot open the camera", t);
      if (host != null) {
        host.onCaptureError("open");
      }
    }
  }

  private void create (Context context, String name) {
    final CameraEnumerator e = enumerator;
    if (e == null) {
      return;
    }
    final SurfaceTextureHelper helper = SurfaceTextureHelper.create("XgramCaptureTexture", host != null ? host.eglContext() : null);
    if (helper == null) {
      if (host != null) {
        host.onCaptureError("texture");
      }
      return;
    }
    final CameraVideoCapturer c = e.createCapturer(name, events);
    if (c == null) {
      helper.dispose();
      if (host != null) {
        host.onCaptureError("capturer");
      }
      return;
    }
    c.initialize(helper, context, tee);
    textureHelper = helper;
    capturer = c;
    deviceName = name;
    front = e.isFrontFacing(name);
    if (wantActive) {
      startCapture();
    }
  }

  private void applyState (int state) {
    if (disposed) {
      return;
    }
    // Only a real non-active -> active step (the retry, a resume) reopens a camera that failed to open synchronously:
    // the first active state right after init must not, or a second failure would come within milliseconds
    final boolean resumed = state == 2 && lastState != -1 && lastState != 2;
    lastState = state;
    wantActive = state == 2;
    if (wantActive && capturer == null) {
      if (resumed) {
        open(requestedFront); // create() starts the capture (wantActive); a failure reports the error again
      }
      return;
    }
    if (wantActive && !capturing && capturer != null) {
      startCapture();
    } else if (!wantActive && capturing) {
      stopCapture(); // the camera closes, so the privacy indicator goes off
    }
  }

  private void startCapture () {
    if (capturer == null) {
      return;
    }
    try {
      capturer.startCapture(WIDTH, HEIGHT, FPS);
      capturing = true;
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "XGram video: startCapture failed", t);
      capturing = false;
      if (host != null) {
        host.onCaptureError("start");
      }
    }
  }

  private void stopCapture () {
    if (capturer != null) {
      try {
        capturer.stopCapture();
      } catch (InterruptedException ignored) {
        // stopCapture blocks until the camera is closed; nothing more to do
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "XGram video: stopCapture failed", t);
      }
    }
    capturing = false;
  }

  private void releaseCapturer () {
    if (capturer != null) {
      try {
        capturer.dispose();
      } catch (Throwable t) {
        Log.e(Log.TAG_VOIP, "XGram video: capturer dispose failed", t);
      }
      capturer = null;
    }
    if (textureHelper != null) {
      textureHelper.dispose();
      textureHelper = null;
    }
    deviceName = null;
  }

  private void dispose () {
    if (disposed) {
      return;
    }
    disposed = true;
    if (capturing) {
      stopCapture();
    }
    releaseCapturer();
    if (host != null) {
      host.onCaptureDisposed();
    }
  }

  /** One log line with every camera: pins the ids of the Fold's cover and inner cameras. */
  private static void logDevicesOnce (Context context, CameraEnumerator e) {
    if (loggedDevices) {
      return;
    }
    loggedDevices = true;
    StringBuilder b = new StringBuilder("XGram video cameras:");
    CameraManager manager = e instanceof Camera2Enumerator ? (CameraManager) context.getSystemService(Context.CAMERA_SERVICE) : null;
    for (String name : e.getDeviceNames()) {
      b.append(' ').append(name).append(e.isFrontFacing(name) ? " front" : e.isBackFacing(name) ? " back" : " other");
      if (manager != null) {
        try {
          CameraCharacteristics characteristics = manager.getCameraCharacteristics(name);
          Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
          Size size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
          b.append(" (facing ").append(facing).append(", ").append(size).append(')');
        } catch (Throwable ignored) { }
      }
      b.append(';');
    }
    Log.i(Log.TAG_VOIP, "%s", b.toString());
  }
}
