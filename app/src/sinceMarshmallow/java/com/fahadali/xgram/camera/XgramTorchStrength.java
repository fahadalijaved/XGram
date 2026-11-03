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
package com.fahadali.xgram.camera;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;

import org.thunderdog.challegram.Log;

/**
 * The torch of a CameraX recording at its brightest (Android 15's torch strength levels, CameraX 1.5+). The legacy and
 * lollipop builds (CameraX 1.4, src/preMarshmallow) have no torch strength and leave the torch as it is.
 */
public final class XgramTorchStrength {
  private XgramTorchStrength () { }

  public static void setMax (@NonNull Camera camera) {
    try {
      CameraInfo info = camera.getCameraInfo();
      if (info.isTorchStrengthSupported()) {
        int max = info.getMaxTorchStrengthLevel();
        if (max != CameraInfo.TORCH_STRENGTH_LEVEL_UNSUPPORTED) {
          camera.getCameraControl().setTorchStrengthLevel(max);
        }
      }
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "XGram: cannot set the torch strength", t);
    }
  }
}
