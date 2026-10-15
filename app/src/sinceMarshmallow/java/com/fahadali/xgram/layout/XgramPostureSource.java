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
package com.fahadali.xgram.layout;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

/**
 * Fold and hinge updates from Jetpack WindowManager, for {@link XgramWindow}. Builds with minSdk 23 and up only
 * (androidx.window needs it); legacy and lollipop builds use the stub in src/preMarshmallow.
 */
final class XgramPostureSource implements Consumer<WindowLayoutInfo> {
  private final Activity activity;
  private final XgramWindow window;
  @Nullable private WindowInfoTrackerCallbackAdapter tracker;
  private boolean isListening;

  XgramPostureSource (Activity activity, XgramWindow window) {
    this.activity = activity;
    this.window = window;
  }

  /** Whether this build can report postures at all. */
  static boolean isSupported () {
    return true;
  }

  void start () {
    if (isListening) {
      return;
    }
    try {
      if (tracker == null) {
        tracker = new WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(activity));
      }
      // The current layout arrives right away, then every change while started.
      tracker.addWindowLayoutInfoListener(activity, ContextCompat.getMainExecutor(activity), this);
      isListening = true;
    } catch (Throwable t) {
      android.util.Log.w("XGram", "WindowInfoTracker unavailable", t);
      window.setFold(null);
    }
  }

  void stop () {
    if (!isListening) {
      return;
    }
    isListening = false;
    try {
      if (tracker != null) {
        tracker.removeWindowLayoutInfoListener(this);
      }
    } catch (Throwable ignored) { }
  }

  @Override
  public void accept (@NonNull WindowLayoutInfo info) {
    if (!isListening) {
      return;
    }
    FoldingFeature folding = null;
    for (DisplayFeature feature : info.getDisplayFeatures()) {
      if (feature instanceof FoldingFeature) {
        folding = (FoldingFeature) feature;
        break;
      }
    }
    if (folding == null) {
      window.setFold(null);
      return;
    }
    window.setFold(new XgramWindow.Fold(
      FoldingFeature.State.HALF_OPENED.equals(folding.getState()),
      FoldingFeature.Orientation.HORIZONTAL.equals(folding.getOrientation()),
      folding.isSeparating(),
      FoldingFeature.OcclusionType.FULL.equals(folding.getOcclusionType()),
      folding.getBounds()
    ));
  }
}
