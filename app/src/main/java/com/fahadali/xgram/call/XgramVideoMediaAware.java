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

/**
 * XGram: a part of the call screen that follows video. The screen calls it on views that implement it (the incoming
 * swipe pill, XgramM3CallSwipe): the hint turns white over video, and the answer glyph becomes the camera while the
 * call is answered with video. Main thread.
 */
public interface XgramVideoMediaAware {
  /** 0: the surface colors, 1: over video. Called while the colors spring: no allocation. */
  void setVideoMediaFactor (float factor);

  /** The answer glyph: the camera ({@code true}) or the phone. */
  default void setVideoAnswer (boolean video) { }
}
