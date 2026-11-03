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

/**
 * Legacy and lollipop builds (minSdk 16 and 21): CameraX 1.4 has no torch strength levels (CameraX 1.5+, Android 15),
 * so the torch of a recording stays at the phone's default level.
 */
public final class XgramTorchStrength {
  private XgramTorchStrength () { }

  public static void setMax (@NonNull Camera camera) { }
}
