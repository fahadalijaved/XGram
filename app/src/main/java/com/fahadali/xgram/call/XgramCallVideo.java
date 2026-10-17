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
package com.fahadali.xgram.call;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.voip.VoIPInstance;
import org.thunderdog.challegram.voip.annotation.VideoState;

import java.security.SecureRandom;
import java.util.ArrayList;

/**
 * XGram: the video of the current call, the single state machine behind both call screens and the call service.
 * CallManager makes one when a call becomes current on a device that can do video (and releases it when the call
 * ends); everything else listens. Main thread only.
 * <p>
 * The rules:
 * <ul>
 * <li>The camera runs only while you can see the call screen: it is focused and an app activity is started. Leave it
 * (minimise, open the chat, go Home, turn the screen off) and the capture is detached and paused, so the other side
 * sees your avatar; come back and it resumes by itself.</li>
 * <li>Video off and the background pause detach the capture from the call (this tgcalls reports our video as active
 * whenever a capture is attached, it never sends "paused"); video on attaches it. The capture itself only closes the
 * camera.</li>
 * <li>{@link #apply} is the only place that touches the capture.</li>
 * </ul>
 */
public final class XgramCallVideo implements XgramVideoBackend.Events {
  public interface Listener {
    void onCallVideoChanged (XgramCallVideo video);

    default void onCameraFailed (XgramCallVideo video) { }

    default void onCameraSwitched (XgramCallVideo video, boolean front, boolean ok) { }
  }

  /** The notification's answer for a video call opens the activity with this extra (a per-call token, see {@link #answerToken}). */
  public static final String EXTRA_ANSWER_VIDEO = BuildConfig.APPLICATION_ID + ".XGRAM_ANSWER_VIDEO";
  public static final String EXTRA_ANSWER_CALL_ID = BuildConfig.APPLICATION_ID + ".XGRAM_ANSWER_CALL_ID";

  public static final int CORNER_TOP_START = 0, CORNER_TOP_END = 1, CORNER_BOTTOM_START = 2, CORNER_BOTTOM_END = 3;
  /** Where the tile with your own camera goes; kept for the whole process. */
  public static int lastTileCorner = CORNER_TOP_END;

  private static final long CAMERA_RETRY_MS = 500L;

  // Availability

  private static int availability = -1;

  /** Calls, a WebRTC flavor and a camera (checked once per process). */
  public static boolean isAvailable () {
    if (availability == -1) {
      boolean available = false;
      if (BuildConfig.CALLS_AVAILABLE && tgx.flavor.Flavor.isVideoCallsSupported()) {
        try {
          available = AppContext.get().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        } catch (Throwable t) {
          return false; // not cached: the context may not be ready yet
        }
      }
      availability = available ? 1 : 0;
    }
    return availability == 1;
  }

  /** Whether {@code userFull} can take a video call. */
  public static boolean canVideoCall (@Nullable TdApi.UserFullInfo userFull) {
    return userFull != null && userFull.supportsVideoCalls && (userFull.canBeCalled || userFull.hasPrivateCalls);
  }

  public static boolean cameraGranted () {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AppContext.get().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
  }

  // Started activities (the camera runs only while one is)

  private static int activityStarted;
  private static boolean lifecycleInstalled;
  private static @Nullable XgramCallVideo current;

  /** Once, early (CallManager): counts the app's started activities. */
  public static void installLifecycle () {
    if (lifecycleInstalled) {
      return;
    }
    Context context = AppContext.get();
    if (!(context instanceof Application)) {
      context = context.getApplicationContext();
    }
    if (!(context instanceof Application)) {
      return;
    }
    lifecycleInstalled = true;
    if (UI.getUiState() == UI.State.RESUMED) {
      activityStarted = 1; // installed while an activity is already on screen
    }
    ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
      @Override
      public void onActivityStarted (@NonNull Activity activity) {
        activityStarted++;
        if (current != null) {
          current.apply();
        }
      }

      @Override
      public void onActivityStopped (@NonNull Activity activity) {
        activityStarted = Math.max(0, activityStarted - 1);
        if (current != null) {
          current.apply();
        }
      }

      @Override
      public void onActivityCreated (@NonNull Activity activity, @Nullable Bundle savedInstanceState) { }

      @Override
      public void onActivityResumed (@NonNull Activity activity) { }

      @Override
      public void onActivityPaused (@NonNull Activity activity) { }

      @Override
      public void onActivitySaveInstanceState (@NonNull Activity activity, @NonNull Bundle outState) { }

      @Override
      public void onActivityDestroyed (@NonNull Activity activity) { }
    });
  }

  // State

  private final Tdlib tdlib;
  private final int callId;
  private final long userId;
  private final XgramVideoBackend backend;
  private final ArrayList<Listener> listeners = new ArrayList<>();

  private boolean isVideoCall, outgoing, pendingIncoming, finished, released;
  private boolean wantsVideo;
  private boolean front = true;
  private long capture;
  private int captureState = -1;
  private @Nullable VoIPInstance instance;
  private boolean attached;
  private boolean screenVisible;
  private @Nullable Object visibleOwner;
  private @VideoState int remoteVideo = VideoState.INACTIVE;
  private boolean localFrame, remoteFrame, usedVideo;
  /** The speaker was turned on for video (TGCallService turns it back off when the video ends, unless you changed it). */
  public boolean autoSpeaker;
  private @Nullable Boolean peerSupportsVideo;
  private int cameraRetries;
  private long answerToken;

  private final Runnable cameraRetry = this::retryCamera;

  private void retryCamera () {
    if (!released && capture != 0 && shouldCapture()) {
      // The native state is still active: step it down and up so the camera opens again
      backend.setCaptureState(capture, VideoState.PAUSED);
      backend.setCaptureState(capture, VideoState.ACTIVE);
      captureState = VideoState.ACTIVE;
    }
  }

  public XgramCallVideo (Tdlib tdlib, TdApi.Call call, XgramVideoBackend backend) {
    this.tdlib = tdlib;
    this.callId = call.id;
    this.userId = call.userId;
    this.backend = backend;
    backend.setEvents(this);
    readCall(call);
    current = this;
  }

  private void readCall (TdApi.Call call) {
    this.isVideoCall = call.isVideo;
    this.outgoing = call.isOutgoing;
    this.pendingIncoming = !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;
    this.finished = TD.isFinished(call);
  }

  // Getters

  public Tdlib tdlib () {
    return tdlib;
  }

  public int callId () {
    return callId;
  }

  public long userId () {
    return userId;
  }

  public XgramVideoBackend backend () {
    return backend;
  }

  public boolean isVideoCall () {
    return isVideoCall;
  }

  public boolean isReleased () {
    return released;
  }

  /** What you asked for (the Video toggle). */
  public boolean wantsVideo () {
    return wantsVideo;
  }

  /** The Video toggle is on (the camera may still be paused while the screen is not visible). */
  public boolean isCameraOn () {
    return wantsVideo && cameraGranted();
  }

  public boolean isFront () {
    return front;
  }

  public boolean shouldCapture () {
    return wantsVideo && !released && !finished && screenVisible && activityStarted > 0 && cameraGranted();
  }

  public boolean isCapturing () {
    return captureState == VideoState.ACTIVE;
  }

  public boolean isLocalShown () {
    return isCapturing() && localFrame;
  }

  public boolean isRemoteShown () {
    return remoteVideo == VideoState.ACTIVE && remoteFrame;
  }

  public @VideoState int remoteVideoState () {
    return remoteVideo;
  }

  /**
   * Video is going on in this call as far as the audio route and the proximity sensor go: what you asked for (not
   * while an incoming call still rings) or the other side's video. Intent level, so Home does not flap the route.
   */
  public boolean isVideoActive () {
    return (wantsVideo && !pendingIncoming && !finished) || remoteVideo == VideoState.ACTIVE;
  }

  /** Video was used in this call (DiscardCall's isVideo, the notification, the call chip). */
  public boolean wasVideoUsed () {
    return usedVideo || isVideoCall;
  }

  public @Nullable Boolean peerSupportsVideo () {
    return peerSupportsVideo;
  }

  public void setPeerSupportsVideo (@Nullable Boolean supports) {
    this.peerSupportsVideo = supports;
  }

  /** A random token the notification's answer carries, so only our own notification answers this call. */
  public long answerToken () {
    if (answerToken == 0) {
      long token;
      do {
        token = new SecureRandom().nextLong();
      } while (token == 0);
      answerToken = token;
    }
    return answerToken;
  }

  // Listeners

  public void addListener (Listener listener) {
    if (!listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private void notifyChanged () {
    for (int i = listeners.size() - 1; i >= 0; i--) {
      if (i < listeners.size()) {
        listeners.get(i).onCallVideoChanged(this);
      }
    }
  }

  private void notifyCameraFailed () {
    for (int i = listeners.size() - 1; i >= 0; i--) {
      if (i < listeners.size()) {
        listeners.get(i).onCameraFailed(this);
      }
    }
  }

  private void notifyCameraSwitched (boolean front, boolean ok) {
    for (int i = listeners.size() - 1; i >= 0; i--) {
      if (i < listeners.size()) {
        listeners.get(i).onCameraSwitched(this, front, ok);
      }
    }
  }

  // The capture

  /** The only place that touches the capture. */
  private void apply () {
    if (released) {
      return;
    }
    if (shouldCapture()) {
      if (capture == 0) {
        capture = backend.newCapture(front);
        if (capture == 0) {
          Log.w(Log.TAG_VOIP, "XGram video: no camera capture");
          wantsVideo = false;
          notifyCameraFailed();
          notifyChanged();
          return;
        }
        captureState = VideoState.ACTIVE;
        cameraRetries = 0;
      } else if (captureState != VideoState.ACTIVE) {
        backend.setCaptureState(capture, VideoState.ACTIVE);
        captureState = VideoState.ACTIVE;
        cameraRetries = 0;
      }
      if (instance != null && !attached) {
        instance.setVideoCapture(capture);
        attached = true;
      }
    } else {
      if (instance != null && attached) {
        instance.setVideoCapture(0); // the other side sees the camera off
        attached = false;
      }
      if (capture != 0 && captureState == VideoState.ACTIVE) {
        int state = wantsVideo ? VideoState.PAUSED : VideoState.INACTIVE;
        backend.setCaptureState(capture, state);
        captureState = state;
      }
      UI.removePendingRunnable(cameraRetry);
      localFrame = false;
      backend.resetFirstFrame(XgramVideoBackend.FEED_LOCAL);
    }
    notifyChanged();
  }

  // Events

  /** The Video toggle (or the call's kind for a new outgoing call). */
  public void setWantsVideo (boolean wantsVideo) {
    if (this.wantsVideo == wantsVideo || released) {
      return;
    }
    this.wantsVideo = wantsVideo;
    this.cameraRetries = 0;
    apply();
  }

  /**
   * The call screen {@code owner} is focused, or not (CallController onFocus / onBlur / destroy). Only the screen that
   * made it visible can make it invisible again, so a replaced screen going away later does not pause the new one.
   */
  public void setScreenVisible (Object owner, boolean visible) {
    if (visible) {
      if (activityStarted == 0 && UI.getUiState() == UI.State.RESUMED) {
        activityStarted = 1; // the screen is on top of a resumed activity, whatever the counter missed
      }
      visibleOwner = owner;
    } else if (visibleOwner != owner) {
      return;
    } else {
      visibleOwner = null;
    }
    if (this.screenVisible != visible) {
      this.screenVisible = visible;
      apply();
    }
  }

  public void onCallUpdated (TdApi.Call call) {
    if (call.id != callId || released) {
      return;
    }
    readCall(call);
    if (finished) {
      wantsVideo = false; // the camera closes the moment the call ends
    }
    apply();
  }

  /** The capture to hand a new call instance, if the camera should run now (0 otherwise). */
  public long captureForNewInstance () {
    if (!shouldCapture()) {
      return 0;
    }
    apply();
    return capture;
  }

  public void onInstanceCreated (VoIPInstance instance, boolean hadCapture) {
    if (released) {
      return;
    }
    this.instance = instance;
    this.attached = hadCapture;
    apply();
  }

  public void onInstanceReleased () {
    this.instance = null;
    this.attached = false;
  }

  /** The other side's media state (TGCallService, posted to the main thread). */
  public void onRemoteMediaState (int audioState, @VideoState int videoState) {
    if (released) {
      return;
    }
    remoteVideo = videoState;
    if (videoState == VideoState.ACTIVE) {
      if (backend.hasFrame(XgramVideoBackend.FEED_REMOTE)) {
        remoteFrame = true; // frames can come before the state message
        markUsed();
      }
    } else {
      remoteFrame = false;
      backend.resetFirstFrame(XgramVideoBackend.FEED_REMOTE);
    }
    notifyChanged();
  }

  public void flipCamera () {
    if (isCapturing()) {
      backend.switchCamera(capture, !front);
    }
  }

  /** The front camera again after a fold or unfold (the cover and inner selfie cameras are different sensors). */
  public void reopenFrontCamera () {
    if (isCapturing() && front) {
      backend.switchCamera(capture, true);
    }
  }

  private void markUsed () {
    if (!usedVideo) {
      usedVideo = true;
      tdlib.context().calls().notifyCurrentCallChanged();
    }
  }

  // XgramVideoBackend.Events

  @Override
  public void onFirstFrame (int feed) {
    if (released) {
      return;
    }
    if (feed == XgramVideoBackend.FEED_LOCAL) {
      if (isCapturing() && !localFrame) {
        localFrame = true;
        markUsed();
        notifyChanged();
      }
    } else if (feed == XgramVideoBackend.FEED_REMOTE) {
      if (remoteVideo == VideoState.ACTIVE && !remoteFrame) {
        remoteFrame = true;
        markUsed();
        notifyChanged();
      }
    }
  }

  @Override
  public void onCameraSwitched (boolean front, boolean ok) {
    if (released) {
      return;
    }
    if (ok) {
      this.front = front;
    }
    notifyCameraSwitched(this.front, ok);
    notifyChanged();
  }

  @Override
  public void onCameraError (String reason) {
    if (released || capture == 0) {
      return;
    }
    if (!shouldCapture()) {
      // On its way to the background (Android 14 disconnects the camera then): a pause, not a failure. The native
      // state follows, so coming back sets it active again (the same state twice would not reach the camera)
      if (captureState == VideoState.ACTIVE) {
        final int state = wantsVideo ? VideoState.PAUSED : VideoState.INACTIVE;
        backend.setCaptureState(capture, state);
        captureState = state;
      }
      return;
    }
    if (cameraRetries++ == 0) {
      UI.removePendingRunnable(cameraRetry);
      UI.post(cameraRetry, CAMERA_RETRY_MS);
      return;
    }
    Log.w(Log.TAG_VOIP, "XGram video: the camera failed (%s)", reason);
    wantsVideo = false;
    apply();
    notifyCameraFailed();
  }

  // Release

  /** The call is over (CallManager): the camera goes, the hosts unbind and release their views. */
  public void release () {
    if (released) {
      return;
    }
    UI.removePendingRunnable(cameraRetry);
    if (instance != null && attached) {
      instance.setVideoCapture(0);
    }
    instance = null;
    attached = false;
    wantsVideo = false;
    if (capture != 0) {
      backend.destroyCapture(capture);
      capture = 0;
    }
    captureState = -1;
    localFrame = remoteFrame = false;
    remoteVideo = VideoState.INACTIVE;
    visibleOwner = null;
    screenVisible = false;
    released = true;
    notifyChanged();
    listeners.clear();
    backend.setEvents(null);
    backend.release();
    if (current == this) {
      current = null;
    }
  }
}
