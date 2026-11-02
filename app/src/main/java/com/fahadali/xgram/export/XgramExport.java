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
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.ViewCompat;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5SheetsInfo;
import com.fahadali.xgram.appearance.XgramR6B3Shell;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.util.Permissions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import tgx.td.ChatId;

/**
 * Chat ⋮ → Export chat, like Telegram Desktop: the history as messages*.html and result.json in Desktop's format, in
 * one .zip in Download/XGram, then the share sheet. This is the UI side: the menu gate, the confirm, the permissions
 * (storage below Android 10, notifications on 13+), starting the foreground service ({@link XgramChatExportService}),
 * the in-chat progress bar (Expressive) or a toast, the status sheet with Cancel while it runs, and the result (share
 * sheet and snackbar when XGram is in front, a notification otherwise). The work itself is {@link ExportJob}; one
 * export runs at a time, in any account.
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
   * The menu action. While an export runs (this chat's or another's: one at a time) it shows how far it is, with
   * Cancel export. Otherwise Desktop's options (media, size limit, dates), then the export. {@code topic}: the topic
   * the chat is open in (a forum, Saved Messages or direct messages topic), which the options offer to export alone.
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
      track(running); // the bar comes back here if it went with a recreated activity
      showStatus(c, running, running.accountId == tdlib.id() && running.chatId == chatId);
      return;
    }
    cleanupStale();
    final String title = tdlib.chatTitle(chatId);
    ExportOptionsSheet.show(c, chatId, topic, options -> start(c, options, title));
  }

  /**
   * Android 9 and older write to the public Download/XGram only with the storage permission (else to the app's own
   * folder). Android 13+ asks for notifications first: progress and result are notifications, and the export runs
   * without them too. The helpers call back only when they ask: granted already (or not needed) goes on at once.
   */
  private static void start (ViewController<?> c, ExportOptions options, String title) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      if (!c.context().permissions().requestWriteExternalStorage(Permissions.WriteType.DOWNLOADS, granted -> begin(c, options, title, granted))) {
        begin(c, options, title, true);
      }
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationManagerCompat.from(c.context()).areNotificationsEnabled()) {
      if (c.context().permissions().requestPostNotifications(granted -> begin(c, options, title, true))) {
        return;
      }
    }
    begin(c, options, title, true);
  }

  private static void begin (ViewController<?> c, ExportOptions options, String title, boolean publicDownloads) {
    final Tdlib tdlib = c.tdlib();
    final ExportJob job = ExportJob.start(tdlib, options, publicDownloads, title, LISTENER);
    if (job == null) {
      XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportBusy), false);
      return;
    }
    hiddenJobId = null;
    unseenResult = null; // an older result: the user is here and starts a new one
    if (!XgramChatExportService.start(AppContext.get(), job)) {
      job.onForegroundChanged(ExportJob.FOREGROUND_REFUSED); // the tick tells the user to keep XGram open
    } else if (!XgramExpressive.isEnabled()) {
      UI.showToast(R.string.XgramExportStarted, Toast.LENGTH_SHORT); // no bar without Expressive design
    }
    track(job);
  }

  /**
   * Removes what an export killed with the app left behind (never the running one's), and its progress notification
   * when the service had been refused (a plain notification outlives the process). Off the main thread.
   */
  private static void cleanupStale () {
    final Context context = AppContext.get();
    Background.instance().post(() -> {
      ExportOutput.cleanupStale(context);
      if (ExportJob.current() == null) {
        ExportNotifications.cancelProgress(context);
      }
    });
  }

  private static boolean launchCleaned;

  /**
   * MainActivity's first onCreate in a process: what an export killed with the app left (the pending .zip, a temp
   * folder whose JSON spool can be hundreds of MB, a plain progress notification) goes now, not on the next ⋮ →
   * Export chat. Safe for the same reason as {@link #prompt}'s call: the key and the running job are read together.
   */
  @UiThread
  public static void onAppLaunch () {
    if (!launchCleaned) {
      launchCleaned = true;
      if (ExportJob.current() == null) {
        cleanupStale();
      }
    }
  }

  // The status sheet

  /**
   * ⋮ → Export chat while an export runs: how far it is, Cancel export (which asks), and for the exported chat Hide /
   * Show progress for its bar. Without Expressive design: Telegram X's options with the same text, as the confirm.
   */
  private static void showStatus (ViewController<?> c, ExportJob job, boolean thisChat) {
    final ExportProgress progress = job.progress();
    final String title = Lang.getString(R.string.XgramExportRunning, job.title());
    final String info = job.isBackgroundRefused() ? Lang.getString(R.string.XgramExportKeepOpen) :
      thisChat ? Lang.getString(R.string.XgramExportStatusInfo) :
      Lang.getString(R.string.XgramExportOneAtATime);
    if (!XgramExpressive.isEnabled()) {
      confirmCancel(c, job, title + "\n" + progress.text() + "\n\n" + info + "\n\n" + Lang.getString(R.string.XgramExportCancelConfirm, job.title()));
      return;
    }
    final List<XgramR5SheetsInfo.Row> rows = new ArrayList<>(1);
    rows.add(new XgramR5SheetsInfo.Row(progress.text(), info));
    final XgramR5SheetsInfo.Action cancel = new XgramR5SheetsInfo.Action(Lang.getString(R.string.XgramExportCancelExport), XgramR5SheetsInfo.ACTION_TEXT,
      () -> confirmCancel(c, job, null)).atStart();
    final XgramR5SheetsInfo.Action second;
    if (thisChat) {
      final boolean hidden = job.id.equals(hiddenJobId);
      second = new XgramR5SheetsInfo.Action(Lang.getString(hidden ? R.string.XgramExportShowProgress : R.string.XgramExportHideProgress), XgramR5SheetsInfo.ACTION_FILLED, () -> {
        hiddenJobId = hidden ? null : job.id; // sticky for this export: the bar stays away until it ends or is shown again
        tickNow();
      });
    } else {
      second = new XgramR5SheetsInfo.Action(Lang.getString(R.string.OK), XgramR5SheetsInfo.ACTION_FILLED, null);
    }
    XgramR5SheetsInfo.show(c, title, rows, cancel, second);
  }

  /** Cancelling throws away the work so far, so it asks first. {@code info} null: the usual question. */
  private static void confirmCancel (@Nullable ViewController<?> from, ExportJob job, @Nullable String info) {
    final ViewController<?> c = from != null && !from.isDestroyed() ? from : UI.getCurrentStackItem();
    if (c == null || c.isDestroyed() || job.phase() >= ExportJob.PHASE_DONE) {
      return;
    }
    c.xgramShowConfirm(info != null ? info : Lang.getString(R.string.XgramExportCancelConfirm, job.title()), Lang.getString(R.string.XgramExportCancelExport), null,
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

  // The in-chat progress bar (main thread). A tick follows the running export: the bar shows while its chat is the
  // chat on screen (it comes back after the activity is recreated or the chat is opened again, a new controller),
  // floats above that chat's message box, and goes when the chat is left (the notification stays). Hide is sticky.

  private static final long TICK_MS = 250, TICK_BACKGROUND_MS = 1000;
  private static final Handler HANDLER = new Handler(Looper.getMainLooper());
  private static final Runnable TICK = XgramExport::tick;

  @Nullable
  private static ExportJob trackedJob;
  @Nullable
  private static String hiddenJobId, refusalShownJobId;
  @Nullable
  private static XgramR6B3Shell.ProgressBar bar;
  @Nullable
  private static WeakReference<MessagesController> barChat;

  private static void track (ExportJob job) {
    if (trackedJob != job) {
      trackedJob = job;
      tickNow();
    } else if (bar == null || !ViewCompat.isAttachedToWindow(bar)) {
      tickNow();
    }
  }

  private static void tickNow () {
    HANDLER.removeCallbacks(TICK);
    tick();
  }

  private static void tick () {
    final ExportJob job = trackedJob;
    if (job == null || job.phase() >= ExportJob.PHASE_DONE || job != ExportJob.current()) {
      dismissBar();
      return;
    }
    updateBar(job); // first: a snackbar takes its place when it shows, above the bar
    if (job.isBackgroundRefused() && !job.id.equals(refusalShownJobId)) {
      // No foreground service: in the background the process is frozen, so the export needs XGram open
      refusalShownJobId = job.id;
      XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportKeepOpen), true);
    }
    HANDLER.postDelayed(TICK, UI.isResumed() ? TICK_MS : TICK_BACKGROUND_MS);
  }

  /** The chat on screen when it is the exported one (a preview or a closing chat does not count), else null. */
  @Nullable
  private static MessagesController chatOnScreen (ExportJob job) {
    final ViewController<?> top = UI.getCurrentStackItem();
    if (!(top instanceof MessagesController)) {
      return null;
    }
    final MessagesController c = (MessagesController) top;
    if (c.isDestroyed() || !c.isFocused() || c.isInForceTouchMode() || c.tdlib() == null ||
      c.tdlib().id() != job.accountId || c.getChatId() != job.chatId) {
      return null;
    }
    if (c.context().areInlineResultsVisible()) {
      return null; // @mention, hashtag, command and inline bot suggestions open right above the box: the bar steps aside
    }
    return c;
  }

  private static void updateBar (ExportJob job) {
    final MessagesController chat = !XgramExpressive.isEnabled() || job.id.equals(hiddenJobId) ? null : chatOnScreen(job);
    if (chat == null) {
      dismissBar();
      return;
    }
    XgramR6B3Shell.ProgressBar shown = bar;
    final WeakReference<MessagesController> shownChat = barChat;
    if (shown != null && (!ViewCompat.isAttachedToWindow(shown) || shownChat == null || shownChat.get() != chat)) {
      dismissBar(); // its activity was recreated, or the chat was opened again
      shown = null;
    }
    final ExportProgress progress = job.progress();
    if (shown == null) {
      if (!UI.isResumed()) {
        return;
      }
      shown = XgramR6B3Shell.ProgressBar.show(progress.text(), Lang.getString(R.string.Cancel), () -> confirmCancel(null, job, null));
      if (shown == null) {
        return; // Expressive design is off: the notification only
      }
      final WeakReference<MessagesController> ref = new WeakReference<>(chat);
      shown.setBottomAnchor(() -> {
        final MessagesController c = ref.get();
        return c != null ? c.xgramBottomControlsTop() : -1;
      });
      shown.setHorizontalAnchor(chat.getValue()); // the chat's pane, not the whole window (two panes)
      bar = shown;
      barChat = ref;
      chat.xgramSetBottomOverlayRoom(XgramR6B3Shell.ProgressBar.anchoredRoom()); // its floating buttons go above the bar
    }
    shown.setText(progress.text());
    shown.setProgress(progress.fraction);
  }

  private static void dismissBar () {
    final XgramR6B3Shell.ProgressBar shown = bar;
    final MessagesController shownChat = barChat != null ? barChat.get() : null;
    bar = null;
    barChat = null;
    if (shownChat != null) {
      shownChat.xgramSetBottomOverlayRoom(0);
    }
    if (shown != null) {
      shown.dismiss();
    }
  }

  private static final ExportJob.Listener LISTENER = new ExportJob.Listener() {
    @Override
    public void onProgress (ExportJob job, ExportProgress progress) {
      final XgramR6B3Shell.ProgressBar shown = bar;
      if (shown != null && trackedJob == job && ViewCompat.isAttachedToWindow(shown)) {
        shown.setText(progress.text());
        shown.setProgress(progress.fraction);
      }
    }

    @Override
    public void onFinished (ExportJob job) {
      if (trackedJob == job) {
        trackedJob = null;
        HANDLER.removeCallbacks(TICK);
        dismissBar();
      }
      if (job.id.equals(hiddenJobId)) {
        hiddenJobId = null;
      }
      // The service leaves the foreground and takes the progress notification with it; a plain one (service refused
      // or stopped by the time limit) goes here. The worker posts none after it ended.
      XgramChatExportService.onJobFinished();
      final Context context = AppContext.get();
      ExportNotifications.cancelProgress(context);
      final boolean inFront = UI.isResumed();
      switch (job.phase()) {
        case ExportJob.PHASE_DONE:
          if (inFront) {
            onDone(job);
          } else {
            ExportNotifications.showResult(context, job);
            keepIfUnseen(context, job);
          }
          break;
        case ExportJob.PHASE_PARTIAL: {
          // Only after hours in the background: the notification says it is partial even if XGram is in front now
          ExportNotifications.showResult(context, job);
          final Uri uri = job.resultUri();
          if (inFront && uri != null) {
            XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportPartial), true, Lang.getString(R.string.Share), () -> share(uri, job.realTitle()));
          } else if (!inFront) {
            keepIfUnseen(context, job);
          }
          break;
        }
        case ExportJob.PHASE_CANCELED:
          if (inFront) {
            XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportCanceled), false);
          } else {
            ExportNotifications.showResult(context, job);
          }
          break;
        default: {
          if (inFront) {
            final String error = job.errorText();
            XgramR6B3Shell.snackbar(error != null ? error : Lang.getString(R.string.XgramExportFailed), true);
          } else {
            ExportNotifications.showResult(context, job);
            keepIfUnseen(context, job);
          }
          break;
        }
      }
    }
  };

  // A result no one saw: XGram was not in front when the export ended and its notification can't show (notifications
  // are off, which the export allows). It shows as a snackbar when XGram comes back, so hours of work don't end in
  // silence with the .zip sitting unnoticed in Download/XGram.

  @Nullable
  private static ExportJob unseenResult;
  private static boolean resumeListenerAdded;
  private static final UI.StateListener RESULT_ON_RESUME = state -> {
    if (state == UI.State.RESUMED && unseenResult != null) {
      HANDLER.post(XgramExport::showUnseenResult); // once the activity's resume is done
    }
  };

  private static void keepIfUnseen (Context context, ExportJob job) {
    if (ExportNotifications.canShowResult(context)) {
      return;
    }
    unseenResult = job;
    if (!resumeListenerAdded) {
      resumeListenerAdded = true;
      UI.addStateListener(RESULT_ON_RESUME);
    }
  }

  private static void showUnseenResult () {
    final ExportJob job = unseenResult;
    if (job == null || !UI.isResumed()) {
      return;
    }
    unseenResult = null;
    if (job.phase() == ExportJob.PHASE_FAILED) {
      final String error = job.errorText();
      XgramR6B3Shell.snackbar(error != null ? error : Lang.getString(R.string.XgramExportFailedNamed, job.title()), true);
      return;
    }
    final Uri uri = job.resultUri();
    final String text = job.phase() == ExportJob.PHASE_PARTIAL ? Lang.getString(R.string.XgramExportPartial) :
      withNote(Lang.getString(R.string.XgramExportDone) + " · " + savedText(job), job);
    XgramR6B3Shell.snackbar(text, true, uri != null ? Lang.getString(R.string.Share) : null, uri != null ? () -> share(uri, job.realTitle()) : null);
  }

  private static String savedText (ExportJob job) {
    final String location = job.resultLocation();
    return location == null || ExportOutput.PUBLIC_LABEL.equals(location) ?
      Lang.getString(R.string.XgramExportSavedDownloads) :
      Lang.getString(R.string.XgramExportSavedIn, location);
  }

  /** Where it was saved, and the share sheet (XGram is in front: an activity can't be started from the background). */
  private static void onDone (ExportJob job) {
    final String saved = savedText(job);
    final Uri uri = job.resultUri();
    if (uri != null && !share(uri, job.realTitle())) {
      XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportNoApp), false);
      return;
    }
    XgramR6B3Shell.snackbar(withNote(Lang.getString(R.string.XgramExportDone) + " · " + saved, job), true);
  }

  /** {@code text} and what the .zip lacks (media left out for space, Android 6's zip limits, failed downloads). */
  private static String withNote (String text, ExportJob job) {
    final String note = job.resultNote();
    return note != null ? text + " · " + note : text;
  }

  /** The share sheet for the .zip, with read access granted to the app that is picked; also the done notification's Share. */
  @NonNull
  static Intent shareChooser (@NonNull Uri uri, @NonNull String title) {
    final String subject = Lang.getString(R.string.XgramExportShareSubject, title);
    final Intent send = new Intent(Intent.ACTION_SEND)
      .setType(ExportOutput.MIME)
      .putExtra(Intent.EXTRA_STREAM, uri)
      .putExtra(Intent.EXTRA_SUBJECT, subject);
    send.setClipData(ClipData.newRawUri(null, uri));
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    final Intent chooser = Intent.createChooser(send, Lang.getString(R.string.Share));
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return chooser;
  }

  static boolean share (@NonNull Uri uri, @NonNull String title) {
    try {
      UI.startActivity(shareChooser(uri, title));
      return true;
    } catch (Throwable t) {
      return false;
    }
  }
}
