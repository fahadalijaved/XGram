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
package com.fahadali.xgram;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.TdlibFilesManager;

/**
 * Download boost. TDLib (prebuilt) picks the part size and the number of download connections itself
 * (2 per DC, 8 with Premium; FileDownloadManager / NetQueryDispatcher), and it has no option an app may set
 * for them. What an app does control is the priority of each downloadFile request: per DC, TDLib gives its
 * in-flight budget (2 MB) to the highest priority file first (ResourceManager, Baseline mode), and how far
 * ahead the video player asks TDLib to download. Boost uses only these two knobs. Off = Telegram X.
 */
public final class XgramDownloadBoost {
  private XgramDownloadBoost () { }

  public static final int LEVEL_LOW = 1, LEVEL_MEDIUM = 2, LEVEL_HIGH = 3, LEVEL_MAX = 4;
  public static final int LEVEL_DEFAULT = LEVEL_MEDIUM;

  /** Slider labels, Low to Max. */
  public static final int[] LEVEL_NAMES = {R.string.XgramBoostLow, R.string.XgramBoostMedium, R.string.XgramBoostHigh, R.string.XgramBoostMax};

  /** Level in effect: 0 when boost is off. */
  public static int level () {
    XgramSettings settings = XgramSettings.instance();
    return settings.downloadBoost() ? settings.downloadBoostLevel() : 0;
  }

  /** Priority for a file the user asked for: tap on a download button, open a file, "Download" in the message menu. */
  public static int tappedPriority () {
    switch (level()) {
      case LEVEL_LOW:
      case LEVEL_MEDIUM:
        return 18; // ahead of automatic downloads (16) and chat photos and avatars (15)
      case LEVEL_HIGH:
        return 24; // also ahead of music (20) and videos (21, 22) that are playing
      case LEVEL_MAX:
        return 28; // ahead of everything but wallpapers, service files and notifications (29+)
      default:
        return TdlibFilesManager.PRIORITY_USER_REQUEST_DOWNLOAD;
    }
  }

  /** Whether a request at this priority should raise the priority of a download that is already running. */
  public static boolean isTappedPriority (int priority) {
    return level() != 0 && priority == tappedPriority();
  }

  /** Priority for an automatic download. Photos, voice, GIFs and round videos keep Telegram X's: they are on screen. */
  public static int autoPriority (int mediaType) {
    boolean large = mediaType == TdlibFilesManager.DOWNLOAD_FLAG_VIDEO || mediaType == TdlibFilesManager.DOWNLOAD_FLAG_FILE || mediaType == TdlibFilesManager.DOWNLOAD_FLAG_MUSIC;
    if (large) {
      switch (level()) {
        case LEVEL_MEDIUM:
        case LEVEL_HIGH:
          return 10; // after chat photos and avatars (15)
        case LEVEL_MAX:
          return 2;
      }
    }
    return TdlibFilesManager.PRIORITY_USER_REQUEST_DOWNLOAD;
  }

  /** How many seconds of a streamed video to download ahead of playback; 0 = the whole file. */
  public static long videoPreloadSeconds (long defaultSeconds) {
    switch (level()) {
      case LEVEL_LOW: return 20;
      case LEVEL_MEDIUM: return 30;
      case LEVEL_HIGH: return 60;
      case LEVEL_MAX: return 0;
      default: return defaultSeconds;
    }
  }

  public static int description (int level) {
    switch (level) {
      case LEVEL_LOW: return R.string.XgramDownloadBoostLow;
      case LEVEL_MEDIUM: return R.string.XgramDownloadBoostMedium;
      case LEVEL_HIGH: return R.string.XgramDownloadBoostHigh;
      case LEVEL_MAX: return R.string.XgramDownloadBoostMax;
      default: return R.string.XgramDownloadBoostOff;
    }
  }
}
