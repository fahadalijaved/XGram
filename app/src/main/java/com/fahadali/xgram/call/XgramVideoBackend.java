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

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * XGram: the video side of a call without org.webrtc types (the main source set is also built for the legacy flavor,
 * which has no WebRTC). The implementation lives in the sinceLollipop source set
 * (com.fahadali.xgram.call.video.XgramVideoBackendImpl) and comes from {@code tgx.flavor.Flavor.createVideoBackend()}.
 * One per call (XgramCallVideo). Main thread, unless noted.
 */
public interface XgramVideoBackend {
  int FEED_LOCAL = 1, FEED_REMOTE = 2;

  /** Main thread. */
  interface Events {
    /** The first frame of {@code feed} since {@link #resetFirstFrame}. */
    void onFirstFrame (int feed);
    void onCameraSwitched (boolean front, boolean ok);
    void onCameraError (String reason);
  }

  void setEvents (@Nullable Events events);

  /** A camera capture (N.newVideoCapture) that starts active, or 0 on failure. Never while another one is alive. */
  long newCapture (boolean front);

  /** {@code videoState}: org.thunderdog.challegram.voip.annotation.VideoState. */
  void setCaptureState (long capture, int videoState);

  /** Switches to the front or the rear camera; the same side again reopens it. The result comes to Events. */
  void switchCamera (long capture, boolean front);

  void destroyCapture (long capture);

  /** An org.webrtc.VideoSink for the other side's video, given to the call instance. */
  Object remoteSink ();

  /** Whether {@code feed} had any frame since {@link #resetFirstFrame}. */
  boolean hasFrame (int feed);

  void resetFirstFrame (int feed);

  /** A video view sharing this call's EGL context; the caller releases it. */
  XgramVideoSurface createSurface (Context context);

  /** Shows {@code feed} on {@code surface} (or stops it); allocates only when the bindings change. */
  void setTarget (int feed, XgramVideoSurface surface, boolean attached);

  /** Drops the owner's reference; the surfaces are released by their owners. */
  void release ();
}
