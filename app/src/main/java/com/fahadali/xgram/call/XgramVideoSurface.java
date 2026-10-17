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

import android.view.View;

import androidx.annotation.Nullable;

/**
 * XGram: a view that renders one video feed of a call (XgramVideoBackend.createSurface). A TextureView, so it follows
 * alpha, translation, scale, the parent's outline clip and the navigation slide. Main thread.
 */
public interface XgramVideoSurface {
  /** Center crop. */
  int SCALE_FILL = 0;
  /** The whole frame; the uncovered area stays transparent. */
  int SCALE_FIT = 1;

  /** Main thread, posted once per change. */
  interface FrameListener {
    /** The first frame drawn since the surface was cleared. */
    void onFirstFrame (XgramVideoSurface surface);
    /** The size of the frames after their rotation. */
    void onFrameSize (XgramVideoSurface surface, int rotatedWidth, int rotatedHeight);
  }

  View view ();

  void setMirror (boolean mirror);

  void setScaleMode (int mode);

  void setFrameListener (@Nullable FrameListener listener);

  int frameWidth ();

  int frameHeight ();

  /** Whether a frame was drawn since {@link #clear}. */
  boolean hasFrame ();

  /** Clears the picture; the next frame counts as the first one again. */
  void clear ();

  void release ();
}
