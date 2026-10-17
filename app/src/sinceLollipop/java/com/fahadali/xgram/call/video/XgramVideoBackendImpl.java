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
package com.fahadali.xgram.call.video;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.fahadali.xgram.call.XgramVideoBackend;
import com.fahadali.xgram.call.XgramVideoSurface;

import org.telegram.messenger.voip.VideoCameraCapturer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.tool.UI;
import org.webrtc.EglBase;
import org.webrtc.VideoSink;

/**
 * XGram: the WebRTC side of a call's video (XgramVideoBackend): the camera capture through N.newVideoCapture, the
 * local and remote fan-outs, and the video views. One EglBase per call, reference counted: the owner (this backend
 * until {@link #release}), every live view, and every live capture until its camera thread has let go of it. Main
 * thread, apart from the {@link VideoCameraCapturer.Host} callbacks (the camera thread), which post to it.
 */
@Keep
public final class XgramVideoBackendImpl implements XgramVideoBackend, VideoCameraCapturer.Host {
  /** Captures alive in the process: tgcalls' AndroidInterface hands every capturer the video source made last. */
  private static int liveCaptures;

  private @Nullable EglBase eglBase;
  private volatile @Nullable EglBase.Context eglContext;
  private int refs = 1; // the owner
  private boolean released;

  private @Nullable Events events;
  private final XgramFanoutSink localSink, remoteSink;
  private final XgramVideoSurface[] localTargets = new XgramVideoSurface[2], remoteTargets = new XgramVideoSurface[2];

  private final Runnable releaseRef = this::releaseRef;

  public XgramVideoBackendImpl () {
    localSink = new XgramFanoutSink(() -> {
      if (events != null) {
        events.onFirstFrame(FEED_LOCAL);
      }
    });
    remoteSink = new XgramFanoutSink(() -> {
      if (events != null) {
        events.onFirstFrame(FEED_REMOTE);
      }
    });
  }

  // References

  private void acquire () {
    refs++;
    ensureEgl();
  }

  private void ensureEgl () {
    if (eglBase == null) {
      eglBase = EglBase.create();
      eglContext = eglBase.getEglBaseContext();
    }
  }

  private void releaseRef () {
    if (refs <= 0) {
      return;
    }
    refs--;
    if (refs == 0 && eglBase != null) {
      eglContext = null;
      eglBase.release();
      eglBase = null;
    }
  }

  // XgramVideoBackend

  @Override
  public void setEvents (@Nullable Events events) {
    this.events = events;
  }

  @Override
  public long newCapture (boolean front) {
    if (released || liveCaptures > 0) {
      Log.w(Log.TAG_VOIP, "XGram video: a capture is already alive (%d)", liveCaptures);
      return 0;
    }
    acquire();
    long capture = 0;
    VideoCameraCapturer.setNextHost(this);
    try {
      capture = N.newVideoCapture(front);
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "XGram video: cannot create the capture", t);
    } finally {
      VideoCameraCapturer.setNextHost(null);
    }
    if (capture == 0) {
      releaseRef();
      return 0;
    }
    liveCaptures++;
    return capture;
  }

  @Override
  public void setCaptureState (long capture, int videoState) {
    if (capture != 0) {
      N.setVideoCaptureState(capture, videoState);
    }
  }

  @Override
  public void switchCamera (long capture, boolean front) {
    if (capture != 0) {
      N.switchVideoCaptureDevice(capture, front);
    }
  }

  @Override
  public void destroyCapture (long capture) {
    if (capture != 0) {
      N.destroyVideoCapture(capture);
      liveCaptures = Math.max(0, liveCaptures - 1);
      // The EGL reference of the capture goes once its camera thread is done (onCaptureDisposed)
    }
  }

  @Override
  public Object remoteSink () {
    return remoteSink;
  }

  @Override
  public boolean hasFrame (int feed) {
    return (feed == FEED_LOCAL ? localSink : remoteSink).hasFrame();
  }

  @Override
  public void resetFirstFrame (int feed) {
    (feed == FEED_LOCAL ? localSink : remoteSink).reset();
  }

  @Override
  public XgramVideoSurface createSurface (Context context) {
    acquire();
    return new XgramVideoView(context, eglContext, releaseRef);
  }

  @Override
  public void setTarget (int feed, XgramVideoSurface surface, boolean attached) {
    final XgramVideoSurface[] targets = feed == FEED_LOCAL ? localTargets : remoteTargets;
    int index = -1, free = -1;
    for (int i = 0; i < targets.length; i++) {
      if (targets[i] == surface) {
        index = i;
      } else if (targets[i] == null && free == -1) {
        free = i;
      }
    }
    if (attached == (index != -1)) {
      return;
    }
    if (attached) {
      if (free == -1) {
        Log.w(Log.TAG_VOIP, "XGram video: more than %d views for one feed", targets.length);
        return;
      }
      targets[free] = surface;
    } else {
      targets[index] = null;
    }
    int count = 0;
    for (XgramVideoSurface target : targets) {
      if (target instanceof VideoSink) {
        count++;
      }
    }
    final VideoSink[] sinks = count == 0 ? XgramFanoutSink.EMPTY : new VideoSink[count];
    int j = 0;
    for (XgramVideoSurface target : targets) {
      if (target instanceof VideoSink) {
        sinks[j++] = (VideoSink) target;
      }
    }
    (feed == FEED_LOCAL ? localSink : remoteSink).setTargets(sinks);
  }

  @Override
  public void release () {
    if (released) {
      return;
    }
    released = true;
    events = null;
    localSink.setTargets(XgramFanoutSink.EMPTY);
    remoteSink.setTargets(XgramFanoutSink.EMPTY);
    for (int i = 0; i < 2; i++) {
      localTargets[i] = null;
      remoteTargets[i] = null;
    }
    releaseRef();
  }

  // VideoCameraCapturer.Host: the camera thread

  @Nullable
  @Override
  public EglBase.Context eglContext () {
    return eglContext;
  }

  @Nullable
  @Override
  public VideoSink localSink () {
    return localSink;
  }

  @Override
  public void onCaptureSwitched (boolean front, boolean ok) {
    UI.post(() -> {
      if (events != null) {
        events.onCameraSwitched(front, ok);
      }
    });
  }

  @Override
  public void onCaptureError (String reason) {
    Log.w(Log.TAG_VOIP, "XGram video: camera error %s", reason);
    UI.post(() -> {
      if (events != null) {
        events.onCameraError(reason);
      }
    });
  }

  @Override
  public void onCaptureDisposed () {
    UI.post(releaseRef);
  }
}
