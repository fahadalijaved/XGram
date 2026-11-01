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

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.view.ViewCompat;

import com.fahadali.xgram.appearance.XgramR6B3Shell;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.util.Permissions;

import tgx.td.ChatId;

/**
 * Chat ⋮ → Export chat, like Telegram Desktop: the history as messages*.html and result.json in Desktop's format, in
 * one .zip in Download/XGram, then the share sheet. This is the UI side: the menu gate, the confirm, the storage
 * permission below Android 10, the progress bar (Expressive) or a toast, cancel, and the result. The work itself is
 * {@link ExportJob}.
 */
public final class XgramExport {
  private XgramExport () { }

  /**
   * Whether to offer the export for {@code chat}. Protected chats are refused, as Desktop does; secret chats are
   * local-only history. An empty or not yet loaded chat still passes (its last message can be unknown): the export
   * then writes Desktop's "No exported messages".
   */
  public static boolean canOffer (@Nullable Tdlib tdlib, @Nullable TdApi.Chat chat) {
    return chat != null && !ChatId.isSecret(chat.id) && !chat.hasProtectedContent;
  }

  /**
   * The menu action. While this chat is exporting it offers to cancel; while another one is, it says so. Otherwise a
   * confirm, then the export. {@code topic}: the topic the chat is open in; for now the whole chat is exported.
   */
  @UiThread
  public static void prompt (ViewController<?> c, long chatId, @Nullable TdApi.MessageTopic topic) {
    final Tdlib tdlib = c.tdlib();
    final TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || ChatId.isSecret(chatId)) {
      return;
    }
    if (chat.hasProtectedContent) {
      XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportProtected), true);
      return;
    }
    final ExportJob running = ExportJob.current();
    if (running != null) {
      if (running.accountId == tdlib.id() && running.chatId == chatId) {
        if (bar == null || barJob != running || !ViewCompat.isAttachedToWindow(bar)) {
          showProgress(running, false); // the bar went with a recreated activity; no second "started" toast
        }
        confirmCancel(c, running);
      } else {
        XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportBusy), false);
      }
      return;
    }
    cleanupStale();
    final String title = tdlib.chatTitle(chatId);
    String info = Lang.getString(R.string.XgramExportOptionsInfo);
    if (topic != null) {
      info = info + "\n\n" + Lang.getString(R.string.XgramExportWholeChat);
    }
    c.xgramShowConfirm(info, Lang.getString(R.string.XgramExportChat), null,
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramExportStart), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_file_download_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          start(c, chatId, title);
        }
        return true;
      });
  }

  /** Android 9 and older write to the public Download/XGram only with the storage permission; else to the app's own folder. */
  private static void start (ViewController<?> c, long chatId, String title) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      begin(c, chatId, title, true);
      return;
    }
    // The helper calls back only when it asks: granted already (or Android 5) runs at once
    if (!c.context().permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> begin(c, chatId, title, granted))) {
      begin(c, chatId, title, true);
    }
  }

  private static void begin (ViewController<?> c, long chatId, String title, boolean publicDownloads) {
    final Tdlib tdlib = c.tdlib();
    final ExportJob job = ExportJob.start(tdlib, ExportOptions.textOnly(tdlib.id(), chatId), publicDownloads, title, LISTENER);
    if (job == null) {
      XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportBusy), false);
      return;
    }
    showProgress(job, true);
  }

  /** Removes what an export killed with the app left behind, off the main thread. */
  private static void cleanupStale () {
    final Context context = AppContext.get();
    Background.instance().post(() -> ExportOutput.cleanupStale(context));
  }

  // Progress (main thread)

  @Nullable
  private static XgramR6B3Shell.ProgressBar bar;
  @Nullable
  private static ExportJob barJob;

  /**
   * The floating progress bar with Cancel while Expressive design is on; Telegram X's toast otherwise, only when the
   * export starts ({@code toastIfNoBar}).
   */
  private static void showProgress (ExportJob job, boolean toastIfNoBar) {
    dismissBar();
    final ExportProgress progress = job.progress();
    bar = XgramR6B3Shell.ProgressBar.show(progress.text(), Lang.getString(R.string.Cancel), () -> confirmCancel(null, job));
    barJob = job;
    if (bar != null) {
      bar.setProgress(progress.fraction);
    } else if (toastIfNoBar) {
      UI.showToast(R.string.XgramExportStarted, Toast.LENGTH_SHORT);
    }
  }

  private static void dismissBar () {
    final XgramR6B3Shell.ProgressBar shown = bar;
    bar = null;
    barJob = null;
    if (shown != null) {
      shown.dismiss();
    }
  }

  /** Cancelling throws away the work so far, so it asks first. */
  private static void confirmCancel (@Nullable ViewController<?> from, ExportJob job) {
    final ViewController<?> c = from != null ? from : UI.getCurrentStackItem();
    if (c == null || c.isDestroyed() || job.phase() >= ExportJob.PHASE_DONE) {
      return;
    }
    c.xgramShowConfirm(Lang.getString(R.string.XgramExportCancelConfirm, job.title()), Lang.getString(R.string.XgramExportCancelExport), null,
      new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramExportCancelExport), Lang.getString(R.string.XgramExportKeepGoing)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_cancel_24, R.drawable.baseline_file_download_24},
      (itemView, id) -> {
        if (id == R.id.btn_done) {
          job.cancel();
        }
        return true;
      });
  }

  private static final ExportJob.Listener LISTENER = new ExportJob.Listener() {
    @Override
    public void onProgress (ExportJob job, ExportProgress progress) {
      final XgramR6B3Shell.ProgressBar shown = bar;
      if (shown != null && barJob == job && !ViewCompat.isAttachedToWindow(shown)) {
        // Its activity was recreated: let it go (it holds the old activity) until a new bar is shown
        bar = null;
        barJob = null;
        return;
      }
      if (shown != null && barJob == job) {
        shown.setText(progress.text());
        shown.setProgress(progress.fraction);
      }
    }

    @Override
    public void onFinished (ExportJob job) {
      if (barJob == job) {
        dismissBar();
      }
      switch (job.phase()) {
        case ExportJob.PHASE_DONE:
          onDone(job);
          break;
        case ExportJob.PHASE_CANCELED:
          XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportCanceled), false);
          break;
        default: {
          final String error = job.errorText();
          XgramR6B3Shell.snackbar(error != null ? error : Lang.getString(R.string.XgramExportFailed), true);
          break;
        }
      }
    }
  };

  /** Where it was saved, and the share sheet when XGram is in front (an activity can't be started from the background). */
  private static void onDone (ExportJob job) {
    final String location = job.resultLocation();
    final String saved = location == null || ExportOutput.PUBLIC_LABEL.equals(location) ?
      Lang.getString(R.string.XgramExportSavedDownloads) :
      Lang.getString(R.string.XgramExportSavedIn, location);
    final Uri uri = job.resultUri();
    if (uri != null && UI.isResumed() && !share(uri, job.realTitle())) {
      XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportNoApp), false);
      return;
    }
    XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportDone) + " · " + saved, true);
  }

  /** The share sheet for the .zip, with read access granted to the app that is picked. */
  static boolean share (@NonNull Uri uri, @NonNull String title) {
    final String subject = Lang.getString(R.string.XgramExportShareSubject, title);
    final Intent send = new Intent(Intent.ACTION_SEND)
      .setType(ExportOutput.MIME)
      .putExtra(Intent.EXTRA_STREAM, uri)
      .putExtra(Intent.EXTRA_SUBJECT, subject);
    send.setClipData(ClipData.newRawUri(null, uri));
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    final Intent chooser = Intent.createChooser(send, Lang.getString(R.string.Share));
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try {
      UI.startActivity(chooser);
      return true;
    } catch (Throwable t) {
      return false;
    }
  }
}
