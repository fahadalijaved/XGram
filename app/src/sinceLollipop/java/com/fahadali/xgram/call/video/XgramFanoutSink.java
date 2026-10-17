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

import androidx.annotation.Keep;

import org.thunderdog.challegram.tool.UI;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * XGram: one video feed of a call to up to two views (the full screen stage and the tile). Frames arrive on a WebRTC
 * thread (the camera's texture thread, or the decoder's); the targets array is replaced on the main thread only when
 * the bindings change, so a frame never allocates. The first frame since {@link #reset} posts one preallocated
 * runnable to the main thread.
 */
final class XgramFanoutSink implements VideoSink {
  static final VideoSink[] EMPTY = new VideoSink[0];

  private volatile VideoSink[] targets = EMPTY;
  private volatile boolean hasFrame;
  private volatile boolean needFirst = true;
  private final Runnable firstFrame;

  XgramFanoutSink (Runnable onFirstFrame) {
    this.firstFrame = onFirstFrame;
  }

  @Keep
  @Override
  public void onFrame (VideoFrame frame) {
    final VideoSink[] list = targets;
    for (VideoSink target : list) {
      target.onFrame(frame);
    }
    hasFrame = true;
    if (needFirst) {
      needFirst = false;
      UI.post(firstFrame);
    }
  }

  /** Main thread. */
  void setTargets (VideoSink[] targets) {
    this.targets = targets;
  }

  VideoSink[] targets () {
    return targets;
  }

  boolean hasFrame () {
    return hasFrame;
  }

  /** The next frame counts as the first one again. */
  void reset () {
    hasFrame = false;
    needFirst = true;
  }
}
