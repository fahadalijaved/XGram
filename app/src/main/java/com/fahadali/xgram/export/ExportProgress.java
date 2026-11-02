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
package com.fahadali.xgram.export;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

/** One immutable look at a running export: the progress bar's (posted to the main thread) and the notification's. */
public final class ExportProgress {
  public final int phase;
  /** Messages found while reading the history (pass 1). */
  public final int indexed;
  /** Messages written so far, and how many there are (pass 2). */
  public final int written, total;
  public final int mediaDone, mediaTotal;
  public final long bytesWritten;
  /** Seconds left of a flood wait Telegram asked for, 0 when there is none. */
  public final int waitSeconds;
  public final boolean waitingForNetwork;
  /** 0..1 for the whole job: reading the history is the first fifth, writing the rest. */
  public final float fraction;
  @NonNull
  public final String title;

  ExportProgress (int phase, int indexed, int written, int total, int mediaDone, int mediaTotal, long bytesWritten, int waitSeconds, boolean waitingForNetwork, float fraction, @NonNull String title) {
    this.phase = phase;
    this.indexed = indexed;
    this.written = written;
    this.total = total;
    this.mediaDone = mediaDone;
    this.mediaTotal = mediaTotal;
    this.bytesWritten = bytesWritten;
    this.waitSeconds = waitSeconds;
    this.waitingForNetwork = waitingForNetwork;
    this.fraction = fraction;
    this.title = title;
  }

  /** The one line the progress bar, the notification and the status sheet show: a wait first, then what the job is doing. */
  @NonNull
  public String text () {
    if (waitSeconds > 0) {
      return Lang.getString(R.string.XgramExportWaitFlood, Lang.getDuration(waitSeconds));
    }
    if (waitingForNetwork) {
      return Lang.getString(R.string.XgramExportWaitNetwork);
    }
    switch (phase) {
      case ExportJob.PHASE_INDEXING:
      case ExportJob.PHASE_CHECKING_SPACE:
        return Lang.getString(R.string.XgramExportIndexing, Lang.plural(R.string.xMessages, indexed));
      case ExportJob.PHASE_WRITING:
        return Lang.getString(R.string.XgramExportWriting, Lang.formatNumber(written), Lang.formatNumber(total));
      default:
        return Lang.getString(R.string.XgramExportFinishing);
    }
  }
}
