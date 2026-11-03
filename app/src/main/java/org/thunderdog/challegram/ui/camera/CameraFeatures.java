/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 24/09/2017
 */
package org.thunderdog.challegram.ui.camera;

public class CameraFeatures {
  public static final int FEATURE_FACING_FRONT = 1;

  public static final int FEATURE_AREA_FOCUS = 1 << 1;
  public static final int FEATURE_AREA_METERING = 1 << 2;

  public static final int FEATURE_FOCUS_AUTO = 1 << 5;
  public static final int FEATURE_FOCUS_CONTINUOUS_PICTURE = 1 << 6;
  public static final int FEATURE_FOCUS_CONTINUOUS_VIDEO = 1 << 7;
  public static final int FEATURE_FOCUS_ANY = FEATURE_FOCUS_AUTO | FEATURE_FOCUS_CONTINUOUS_PICTURE | FEATURE_FOCUS_CONTINUOUS_VIDEO;


  public static final int FEATURE_FLASH_OFF = 1 << 10;
  public static final int FEATURE_FLASH_ON = 1 << 11;
  public static final int FEATURE_FLASH_AUTO = 1 << 12;
  public static final int FEATURE_FLASH_TORCH = 1 << 13;
  public static final int FEATURE_FLASH_FAKE = 1 << 14;

  public static final int FEATURE_ZOOM = 1 << 20;
  public static final int FEATURE_ZOOM_SMOOTH = 1 << 21;

  private final boolean isCamera2Api;
  private int features;

  public static final int MAX_ZOOM_UNKNOWN = -1;
  private int maxZoom = MAX_ZOOM_UNKNOWN;

  public CameraFeatures (boolean isCamera2Api) {
    this.isCamera2Api = isCamera2Api;
  }

  public boolean has (int feature) {
    return (features & feature) != 0;
  }

  public void add (int feature) {
    // TODO blacklist any features here on specific devices
    this.features |= feature;
  }

  public void setMaxZoom (int zoom) {
    this.maxZoom = zoom;
  }

  public int getMaxZoom () {
    return maxZoom;
  }

  // XGram: pinch to zoom on Camera1, whose zoom is an index (0 to maxZoom) into the camera's own ratios
  // (Parameters.getZoomRatios(): 100 is 1×, 200 is 2×, rising, one per index). A pinch scales the ratio.

  /** When a camera gives no ratios, its last index is taken as 4× (indexes spread evenly). */
  private static final float XGRAM_ASSUMED_MAX_RATIO = 4f;
  private int[] xgramZoomRatios;

  /** The ratio of each zoom index (Parameters.getZoomRatios()); ignored unless there is one per index. */
  public void xgramSetZoomRatios (java.util.List<Integer> ratios) {
    xgramZoomRatios = null;
    if (ratios == null || maxZoom <= 0 || ratios.size() != maxZoom + 1) {
      return;
    }
    int[] result = new int[ratios.size()];
    for (int i = 0; i < result.length; i++) {
      Integer ratio = ratios.get(i);
      if (ratio == null || ratio <= 0 || (i > 0 && ratio < result[i - 1])) {
        return; // not a rising list of ratios: the even spread instead
      }
      result[i] = ratio;
    }
    xgramZoomRatios = result;
  }

  /** The zoom ratio (1 = none) at a zoom index; a fraction lies between two of them. */
  public float xgramRatioAt (float index) {
    if (maxZoom <= 0) {
      return 1f;
    }
    index = Math.max(0f, Math.min(maxZoom, index));
    final int[] ratios = xgramZoomRatios;
    if (ratios == null) {
      return 1f + index / maxZoom * (XGRAM_ASSUMED_MAX_RATIO - 1f);
    }
    int i = (int) index;
    if (i >= ratios.length - 1) {
      return ratios[ratios.length - 1] / 100f;
    }
    return (ratios[i] + (ratios[i + 1] - ratios[i]) * (index - i)) / 100f;
  }

  /** The zoom index (a fraction between two of them) closest to a zoom ratio. */
  public float xgramIndexOf (float ratio) {
    if (maxZoom <= 0) {
      return 0f;
    }
    final int[] ratios = xgramZoomRatios;
    if (ratios == null) {
      return Math.max(0f, Math.min(maxZoom, (ratio - 1f) / (XGRAM_ASSUMED_MAX_RATIO - 1f) * maxZoom));
    }
    final float value = ratio * 100f;
    if (value <= ratios[0]) {
      return 0f;
    }
    for (int i = 0; i < ratios.length - 1; i++) {
      // value is above ratios[i] here, so ratios[i + 1] is above ratios[i] when it is reached
      if (value <= ratios[i + 1]) {
        return i + (value - ratios[i]) / (ratios[i + 1] - ratios[i]);
      }
    }
    return ratios.length - 1;
  }

  // Specific

  public boolean canFocus () {
    return has(FEATURE_FOCUS_ANY);
  }

  public boolean canFocusByTap () {
    return has(FEATURE_AREA_FOCUS | FEATURE_AREA_METERING);
  }

  public boolean canZoom () {
    return maxZoom > 0 && has(FEATURE_ZOOM | FEATURE_ZOOM_SMOOTH);
  }

  public boolean canFlash (boolean allowFake) {
    int flashModes = FEATURE_FLASH_ON | FEATURE_FLASH_AUTO;
    if (allowFake) {
      flashModes |= FEATURE_FACING_FRONT;
    }
    return has(flashModes);
  }
}
