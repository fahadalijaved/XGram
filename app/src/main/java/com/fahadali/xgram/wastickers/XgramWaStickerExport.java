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
package com.fahadali.xgram.wastickers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibException;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.StringUtils;

/**
 * XGram: Add to WhatsApp, from the sticker pack sheet's ⋮ (StickersListController). Step 1: still (WebP) stickers.
 * <ol>
 *   <li>Checks first, while the sheet is open: at least 3 still stickers (animated packs are coming), and WhatsApp or
 *   WhatsApp Business installed; with both, the user picks one.</li>
 *   <li>The sheet closes and a progress dialog (Cancel, or back) shows while a worker thread fetches the set, downloads
 *   each still sticker with a synchronous DownloadFile and converts it ({@link XgramWaStickerImages}); stickers that
 *   fail to download or convert are left out.</li>
 *   <li>The stickers are split into packs of up to 30 ("Title (1/N)", ids {@code tg_<setId>_<part>}), each with a tray
 *   icon, and put in place for {@link XgramWaStickerProvider} ({@link XgramWaStickerStore#commit}).</li>
 *   <li>For each pack in turn WhatsApp's own Add sheet opens (ENABLE_STICKER_PACK with startActivityForResult, which
 *   WhatsApp needs to know the caller); one WhatsApp already has is skipped (it re-reads the pack for the new
 *   image_data_version). A validation error from WhatsApp is shown, a cancel asks whether to go on to the next part.</li>
 * </ol>
 * One export runs at a time.
 */
public final class XgramWaStickerExport {
  public static final String WHATSAPP = "com.whatsapp";
  public static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";

  // WhatsApp's intent (github.com/WhatsApp/stickers, Android sample)
  private static final String ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK";
  private static final String EXTRA_STICKER_PACK_ID = "sticker_pack_id";
  private static final String EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority";
  private static final String EXTRA_STICKER_PACK_NAME = "sticker_pack_name";
  private static final String EXTRA_VALIDATION_ERROR = "validation_error";
  // Whether WhatsApp already has a pack: its provider <package>.provider.sticker_whitelist_check
  private static final String WHITELIST_PROVIDER = ".provider.sticker_whitelist_check";
  private static final String WHITELIST_PATH = "is_whitelisted";
  private static final String WHITELIST_RESULT = "result";

  /** The startActivityForResult code, next to Intents.ACTIVITY_RESULT_XGRAM_PARTIAL_TAB (10051). */
  private static final int REQUEST_ADD_PACK = 10052;

  private static final long WAIT_SLICE_MS = 250;
  /** Connected time without TDLib's reply before a request is given up. */
  private static final long STALL_MS = 60_000L;
  /** The pause before WhatsApp's sheet for the next part, so the previous one has closed. */
  private static final long NEXT_PART_DELAY_MS = 350;

  /** The running export, main thread only. */
  private static @Nullable XgramWaStickerExport current;

  // Entry

  /** Whether the sticker pack sheet's ⋮ offers Add to WhatsApp: regular sticker sets, not masks or custom emoji. */
  public static boolean canOffer (@Nullable TdApi.StickerSetInfo info) {
    return info != null && info.stickerType != null && info.stickerType.getConstructor() == TdApi.StickerTypeRegular.CONSTRUCTOR;
  }

  /**
   * Add to WhatsApp pressed. {@code loadedStickers}: the sheet's stickers when they've loaded (for the checks before
   * anything starts); {@code hideSheet} closes the sticker pack sheet, so the progress dialog under it shows.
   */
  @UiThread
  public static void start (@NonNull ViewController<?> c, @NonNull TdApi.StickerSetInfo info, @Nullable TdApi.Sticker[] loadedStickers, @NonNull Runnable hideSheet) {
    if (!canOffer(info)) {
      return;
    }
    endLostExport();
    if (current != null) {
      UI.showToast(R.string.XgramWaBusy, Toast.LENGTH_SHORT);
      return;
    }
    if (loadedStickers != null) {
      String problem = countProblem(countStill(loadedStickers), loadedStickers.length);
      if (problem != null) {
        c.openAlert(R.string.XgramWaAddToWhatsApp, problem);
        return;
      }
    }
    final Context context = c.context();
    final boolean consumer = isInstalled(context, WHATSAPP);
    final boolean business = isInstalled(context, WHATSAPP_BUSINESS);
    if (!consumer && !business) {
      c.openAlert(R.string.XgramWaAddToWhatsApp, Lang.getString(R.string.XgramWaNotInstalled));
      return;
    }
    if (consumer && business) {
      c.showOptions(
        Lang.getString(R.string.XgramWaChooseApp),
        new int[] {R.id.btn_xgramWhatsApp, R.id.btn_xgramWhatsAppBusiness},
        new String[] {Lang.getString(R.string.XgramWaWhatsApp), Lang.getString(R.string.XgramWaWhatsAppBusiness)},
        new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_chat_bubble_24, R.drawable.baseline_work_24},
        (itemView, id) -> {
          if (id == R.id.btn_xgramWhatsApp) {
            begin(c.tdlib(), info, WHATSAPP, hideSheet);
          } else if (id == R.id.btn_xgramWhatsAppBusiness) {
            begin(c.tdlib(), info, WHATSAPP_BUSINESS, hideSheet);
          }
          return true;
        }
      );
    } else {
      begin(c.tdlib(), info, consumer ? WHATSAPP : WHATSAPP_BUSINESS, hideSheet);
    }
  }

  private static int countStill (TdApi.Sticker[] stickers) {
    int count = 0;
    for (TdApi.Sticker sticker : stickers) {
      if (isStill(sticker)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isStill (TdApi.Sticker sticker) {
    return sticker != null && sticker.format != null && sticker.format.getConstructor() == TdApi.StickerFormatWebp.CONSTRUCTOR;
  }

  /** Why a set with {@code still} still stickers of {@code total} can't go to WhatsApp, or null when it can. */
  @Nullable
  private static String countProblem (int still, int total) {
    if (still >= XgramWaStickerStore.MIN_STICKERS) {
      return null;
    }
    if (still == 0 && total > 0) {
      return Lang.getString(R.string.XgramWaAnimatedSoon);
    }
    if (still < total) {
      return Lang.getString(R.string.XgramWaTooFewStill, still);
    }
    return Lang.getString(R.string.XgramWaTooFew, total);
  }

  private static boolean isInstalled (Context context, String packageName) {
    try {
      ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
      return info.enabled;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    } catch (Throwable t) {
      Log.w("XGram WhatsApp stickers: cannot check %s", t, packageName);
      return false;
    }
  }

  /** Whether WhatsApp ({@code packageName}) already has the pack, through its whitelist check provider. */
  @WorkerThread
  private static boolean isAddedTo (Context context, String packageName, String identifier) {
    try {
      final String authority = packageName + WHITELIST_PROVIDER;
      ProviderInfo provider = context.getPackageManager().resolveContentProvider(authority, PackageManager.GET_META_DATA);
      if (provider == null) {
        return false; // an older WhatsApp
      }
      Uri uri = new Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(authority)
        .appendPath(WHITELIST_PATH)
        .appendQueryParameter("authority", XgramWaStickerStore.authority(context))
        .appendQueryParameter("identifier", identifier)
        .build();
      try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int column = cursor.getColumnIndex(WHITELIST_RESULT);
          return column >= 0 && cursor.getInt(column) == 1;
        }
      }
    } catch (Throwable t) {
      Log.w("XGram WhatsApp stickers: whitelist check failed", t);
    }
    return false;
  }

  /**
   * The user can tap Add to WhatsApp again while an export is at the WhatsApp step ({@code packs} set: the worker is
   * done) only when its WhatsApp result or next-part dialog was lost with a recreated activity (the result handler
   * belongs to the activity instance): that export is over, so it ends instead of blocking every later one. A late
   * result from it is ignored ({@code current != this}). Still preparing: busy, as before.
   */
  @UiThread
  private static void endLostExport () {
    if (current != null && current.packs != null) {
      current.end();
    }
  }

  @UiThread
  private static void begin (Tdlib tdlib, TdApi.StickerSetInfo info, String targetPackage, Runnable hideSheet) {
    endLostExport();
    if (current != null) {
      UI.showToast(R.string.XgramWaBusy, Toast.LENGTH_SHORT);
      return;
    }
    XgramWaStickerExport export = new XgramWaStickerExport(tdlib, info, targetPackage);
    current = export;
    hideSheet.run();
    export.showProgress(Lang.getString(R.string.XgramWaPreparing));
    new Thread(export::work, "XGram WhatsApp stickers").start();
  }

  // The export

  private static final class Canceled extends RuntimeException { }

  private static final class Failure extends Exception {
    Failure (String message) {
      super(message);
    }
  }

  private static final class Converted {
    final String file;
    final String[] emojis;

    Converted (String file, String[] emojis) {
      this.file = file;
      this.emojis = emojis;
    }
  }

  private final Tdlib tdlib;
  private final long setId;
  private final String targetPackage;
  private volatile boolean canceled;

  // Main thread
  private boolean preparing = true;
  private @Nullable BaseActivity progressActivity;
  private final BaseActivity.ProgressPopupListener progressListener = this::cancel;
  private List<XgramWaStickerStore.Pack> packs;
  private boolean[] alreadyAdded;
  private int index, added, skipped;
  private int failedStickers, leftOutAnimated;
  private boolean waitingForResume;
  /** Held here: UI keeps its state listeners weakly. */
  private final UI.StateListener resumeListener = this::onUiStateChanged;

  private XgramWaStickerExport (Tdlib tdlib, TdApi.StickerSetInfo info, String targetPackage) {
    this.tdlib = tdlib;
    this.setId = info.id;
    this.targetPackage = targetPackage;
  }

  /** XGram came back while a pack waits for WhatsApp's sheet; the listener goes after UI's loop over listeners. */
  private void onUiStateChanged (int newState) {
    if (newState == UI.State.RESUMED && waitingForResume) {
      waitingForResume = false;
      UI.post(() -> {
        UI.removeStateListener(resumeListener);
        launchNext();
      });
    }
  }

  // Progress (main thread)

  @UiThread
  private void showProgress (String text) {
    if (canceled || !preparing) {
      return;
    }
    BaseActivity activity = progressActivity != null ? progressActivity : UI.getUiContext();
    if (activity == null || activity.isFinishing()) {
      return;
    }
    progressActivity = activity;
    activity.showProgress(text, progressListener); // shown already: only the text changes
  }

  @UiThread
  private void hideProgress () {
    preparing = false;
    if (progressActivity != null) {
      progressActivity.hideProgress(false);
      progressActivity = null;
    }
  }

  /** The progress dialog's Cancel or back: the worker stops at its next step and removes what it made. */
  @UiThread
  private void cancel () {
    canceled = true;
    preparing = false;
    progressActivity = null;
  }

  private void postProgress (int done, int total, int parts, int leftOutAnimated) {
    // One short second line at most: Telegram X's progress dialog shows 2 lines, the M3E one 3
    StringBuilder b = new StringBuilder(Lang.getString(R.string.XgramWaPreparingCount, done, total));
    if (parts > 1) {
      b.append('\n').append(Lang.getString(R.string.XgramWaPreparingParts, parts));
    } else if (leftOutAnimated > 0) {
      b.append('\n').append(Lang.getString(R.string.XgramWaPreparingAnimated, leftOutAnimated));
    }
    final String text = b.toString();
    UI.post(() -> showProgress(text));
  }

  // Worker thread

  private void checkCanceled () {
    if (canceled) {
      throw new Canceled();
    }
  }

  /** A blocking TDLib request, waited for in slices that notice a cancel; offline time doesn't count. */
  @NonNull
  @WorkerThread
  @SuppressWarnings("unchecked")
  private <T extends TdApi.Object> T call (TdApi.Function<T> function) {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();
    tdlib.client().send(function, object -> {
      response.set(object);
      latch.countDown();
    });
    long connectedWaitMs = 0;
    try {
      while (!latch.await(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)) {
        checkCanceled(); // a late reply goes to the handler above and is dropped
        if (tdlib.isConnected()) {
          connectedWaitMs += WAIT_SLICE_MS;
          if (connectedWaitMs >= STALL_MS) {
            throw new TdlibException(new TdApi.Error(408, "No reply to " + function.getClass().getSimpleName()));
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Canceled();
    }
    TdApi.Object result = response.get();
    if (result == null) {
      throw new TdlibException(new TdApi.Error(500, "No result"));
    }
    if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
      throw new TdlibException((TdApi.Error) result);
    }
    return (T) result;
  }

  /** The file, downloaded (at once when it is already), or null when it can't be. */
  @Nullable
  @WorkerThread
  private File download (@Nullable TdApi.File file) {
    if (file == null) {
      return null;
    }
    try {
      TdApi.File result = call(new TdApi.DownloadFile(file.id, TdlibFilesManager.PRIORITY_USER_REQUEST_SHARE, 0, 0, true));
      if (result.local != null && result.local.isDownloadingCompleted && !StringUtils.isEmpty(result.local.path)) {
        File local = new File(result.local.path);
        if (local.isFile()) {
          return local;
        }
      }
    } catch (TdlibException e) {
      Log.w("XGram WhatsApp stickers: download of file %d failed: %s", file.id, TD.toErrorString(e.getError()));
    }
    return null;
  }

  /** 1-3 emojis for sticker {@code i}: the set's list for it, else the sticker's own emoji. */
  private static String[] emojis (TdApi.StickerSet set, int i, TdApi.Sticker sticker) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (set.emojis != null && i < set.emojis.length && set.emojis[i] != null && set.emojis[i].emojis != null) {
      for (String emoji : set.emojis[i].emojis) {
        if (!StringUtils.isEmpty(emoji)) {
          result.add(emoji);
          if (result.size() == XgramWaStickerStore.MAX_EMOJIS) {
            break;
          }
        }
      }
    }
    if (result.isEmpty() && !StringUtils.isEmpty(sticker.emoji)) {
      result.add(sticker.emoji);
    }
    if (result.isEmpty()) {
      result.add("🙂"); // WhatsApp needs one; Telegram stickers always have one, so this is a safety net
    }
    return result.toArray(new String[0]);
  }

  /** At most 128 characters (WhatsApp's limit for names and publishers), not splitting a surrogate pair. */
  private static String clip (String text) {
    if (text.length() <= XgramWaStickerStore.MAX_TEXT_LENGTH) {
      return text;
    }
    int end = XgramWaStickerStore.MAX_TEXT_LENGTH;
    if (Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(0, end);
  }

  /** The set's own thumbnail as the tray icon when it is a still image. */
  @Nullable
  @WorkerThread
  private byte[] trayFromThumbnail (TdApi.StickerSet set) {
    if (set.thumbnail == null || set.thumbnail.format == null) {
      return null;
    }
    switch (set.thumbnail.format.getConstructor()) {
      case TdApi.ThumbnailFormatWebp.CONSTRUCTOR:
      case TdApi.ThumbnailFormatPng.CONSTRUCTOR:
        break;
      default:
        return null;
    }
    File file = download(set.thumbnail.file);
    if (file == null) {
      return null;
    }
    try {
      return XgramWaStickerImages.toTray(XgramWaStickerStore.readFully(file));
    } catch (Throwable t) {
      Log.w("XGram WhatsApp stickers: thumbnail unreadable", t);
      return null;
    }
  }

  @WorkerThread
  private void work () {
    final Context context = AppContext.get();
    File staging = null;
    try {
      final TdApi.StickerSet set = call(new TdApi.GetStickerSet(setId));
      if (set.stickerType == null || set.stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR) {
        throw new Failure(Lang.getString(R.string.XgramWaNotStickers));
      }
      final List<Integer> still = new ArrayList<>();
      for (int i = 0; i < set.stickers.length; i++) {
        if (isStill(set.stickers[i])) {
          still.add(i);
        }
      }
      final int leftOutAnimated = set.stickers.length - still.size();
      String problem = countProblem(still.size(), set.stickers.length);
      if (problem != null) {
        throw new Failure(problem);
      }
      final int expectedParts = (still.size() + XgramWaStickerStore.MAX_STICKERS - 1) / XgramWaStickerStore.MAX_STICKERS;

      staging = XgramWaStickerStore.newStagingDir(context, setId);
      final List<Converted> converted = new ArrayList<>(still.size());
      int failed = 0;
      for (int k = 0; k < still.size(); k++) {
        checkCanceled();
        postProgress(k, still.size(), expectedParts, leftOutAnimated);
        final int i = still.get(k);
        final TdApi.Sticker sticker = set.stickers[i];
        byte[] webp = null;
        File file = download(sticker.sticker);
        checkCanceled();
        if (file != null) {
          try {
            webp = XgramWaStickerImages.toSticker(XgramWaStickerStore.readFully(file));
          } catch (Throwable e) { // any decode or compress failure leaves out only this sticker
            Log.w("XGram WhatsApp stickers: sticker %d unreadable", e, sticker.id);
          }
        }
        if (webp == null) {
          failed++;
          continue;
        }
        final String name = String.format(Locale.US, "%03d.webp", k + 1);
        XgramWaStickerStore.writeFully(new File(staging, name), webp);
        converted.add(new Converted(name, emojis(set, i, sticker)));
      }
      postProgress(still.size(), still.size(), expectedParts, leftOutAnimated);
      if (converted.size() < XgramWaStickerStore.MIN_STICKERS) {
        throw new Failure(Lang.getString(R.string.XgramWaNotEnough));
      }
      checkCanceled();

      // Parts of up to 30, as even as they can be (31 stickers: 16 and 15, never 30 and 1)
      final int parts = (converted.size() + XgramWaStickerStore.MAX_STICKERS - 1) / XgramWaStickerStore.MAX_STICKERS;
      final byte[] setTray = trayFromThumbnail(set);
      final String title = StringUtils.isEmpty(set.title) ? set.name : set.title;
      final String publisher = clip(tdlib.tMeStickerSetUrl(set.name).replaceFirst("^https?://", ""));
      final List<XgramWaStickerStore.Pack> newPacks = new ArrayList<>(parts);
      final List<File> dirs = new ArrayList<>(parts);
      for (int p = 0; p < parts; p++) {
        checkCanceled();
        final int from = p * converted.size() / parts, to = (p + 1) * converted.size() / parts;
        final String identifier = XgramWaStickerStore.identifier(setId, p + 1);
        final File dir = new File(staging, identifier);
        if (!dir.mkdirs() && !dir.isDirectory()) {
          throw new IOException("Cannot create " + dir);
        }
        final XgramWaStickerStore.Sticker[] stickers = new XgramWaStickerStore.Sticker[to - from];
        for (int j = from; j < to; j++) {
          Converted c = converted.get(j);
          if (!new File(staging, c.file).renameTo(new File(dir, c.file))) {
            throw new IOException("Cannot move " + c.file);
          }
          stickers[j - from] = new XgramWaStickerStore.Sticker(c.file, c.emojis);
        }
        // One pack: the set's thumbnail. Parts: each its own first sticker, so they look different in WhatsApp's tray
        byte[] tray = parts == 1 ? setTray : null;
        if (tray == null) {
          tray = XgramWaStickerImages.toTray(XgramWaStickerStore.readFully(new File(dir, stickers[0].file)));
        }
        if (tray == null) {
          tray = setTray;
        }
        if (tray == null) {
          throw new IOException("No tray icon");
        }
        XgramWaStickerStore.writeFully(new File(dir, XgramWaStickerStore.TRAY_FILE), tray);
        final String name = parts == 1 ? title : Lang.getString(R.string.XgramWaPartName, title, p + 1, parts);
        newPacks.add(new XgramWaStickerStore.Pack(identifier, setId, p + 1, parts, clip(name), publisher, XgramWaStickerStore.TRAY_FILE, 0, 0, stickers));
        dirs.add(dir);
      }
      checkCanceled();

      final List<XgramWaStickerStore.Pack> committed = XgramWaStickerStore.commit(context, setId, newPacks, dirs);
      try {
        context.getContentResolver().notifyChange(XgramWaStickerStore.metadataUri(context), null);
      } catch (Throwable ignored) { }
      final boolean[] alreadyAdded = new boolean[committed.size()];
      for (int i = 0; i < committed.size(); i++) {
        alreadyAdded[i] = isAddedTo(context, targetPackage, committed.get(i).identifier);
      }
      final int failedFinal = failed;
      UI.post(() -> onPrepared(committed, alreadyAdded, failedFinal, leftOutAnimated));
    } catch (Canceled e) {
      UI.post(this::end);
    } catch (Failure e) {
      final String message = e.getMessage();
      UI.post(() -> onFailed(message));
    } catch (TdlibException e) {
      final String message = Lang.getString(R.string.XgramWaFailed, TD.toErrorString(e.getError()));
      UI.post(() -> onFailed(message));
    } catch (Throwable t) {
      Log.e("XGram WhatsApp stickers: export failed", t);
      final String message = Lang.getString(R.string.XgramWaFailed, t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
      UI.post(() -> onFailed(message));
    } finally {
      if (staging != null) {
        XgramWaStickerStore.deleteRecursively(staging);
      }
    }
  }

  // Adding (main thread)

  @UiThread
  private void onFailed (String message) {
    hideProgress();
    if (!canceled) {
      alert(message);
    }
    end();
  }

  @UiThread
  private void onPrepared (List<XgramWaStickerStore.Pack> packs, boolean[] alreadyAdded, int failedStickers, int leftOutAnimated) {
    hideProgress();
    if (canceled) {
      end();
      return;
    }
    this.packs = packs;
    this.alreadyAdded = alreadyAdded;
    this.failedStickers = failedStickers;
    this.leftOutAnimated = leftOutAnimated;
    this.index = 0;
    launchNext();
  }

  /** WhatsApp's Add sheet for the next pack it doesn't have yet, or the summary after the last. */
  @UiThread
  private void launchNext () {
    if (current != this) {
      return;
    }
    while (index < packs.size() && alreadyAdded[index]) {
      skipped++;
      index++;
    }
    if (index >= packs.size()) {
      finish();
      return;
    }
    final BaseActivity activity = UI.getUiContext();
    if (activity == null || activity.isFinishing()) {
      end();
      return;
    }
    if (!UI.isResumed()) {
      // Ready while XGram is in the background: Android blocks starting WhatsApp from there, so wait to come back
      if (!waitingForResume) {
        waitingForResume = true;
        UI.addStateListener(resumeListener);
      }
      return;
    }
    final XgramWaStickerStore.Pack pack = packs.get(index);
    Intent intent = new Intent(ACTION_ENABLE_STICKER_PACK);
    intent.putExtra(EXTRA_STICKER_PACK_ID, pack.identifier);
    intent.putExtra(EXTRA_STICKER_PACK_AUTHORITY, XgramWaStickerStore.authority(activity));
    intent.putExtra(EXTRA_STICKER_PACK_NAME, pack.name);
    intent.setPackage(targetPackage);
    activity.putActivityResultHandler(REQUEST_ADD_PACK, (requestCode, resultCode, data) -> onAddResult(resultCode, data));
    try {
      //noinspection deprecation
      activity.startActivityForResult(intent, REQUEST_ADD_PACK);
    } catch (ActivityNotFoundException | SecurityException e) {
      activity.putActivityResultHandler(REQUEST_ADD_PACK, null);
      alert(Lang.getString(R.string.XgramWaOpenFailed));
      end();
    }
  }

  @UiThread
  private void onAddResult (int resultCode, @Nullable Intent data) {
    if (current != this || packs == null || index >= packs.size()) {
      return;
    }
    final XgramWaStickerStore.Pack pack = packs.get(index);
    if (resultCode == Activity.RESULT_OK) {
      added++;
      index++;
      UI.post(this::launchNext, NEXT_PART_DELAY_MS);
      return;
    }
    final String validationError = data != null ? data.getStringExtra(EXTRA_VALIDATION_ERROR) : null;
    if (!StringUtils.isEmpty(validationError)) {
      Log.w("XGram WhatsApp stickers: %s rejected: %s", pack.identifier, validationError);
      alert(Lang.getString(R.string.XgramWaRejected, pack.name, validationError));
      end();
      return;
    }
    // Canceled in WhatsApp's sheet, or WhatsApp said no without a reason: it may still have taken it
    final Context context = AppContext.get();
    new Thread(() -> {
      final boolean has = isAddedTo(context, targetPackage, pack.identifier);
      UI.post(() -> onCanceledInWhatsApp(has));
    }, "XGram WhatsApp stickers check").start();
  }

  @UiThread
  private void onCanceledInWhatsApp (boolean addedAnyway) {
    if (current != this) {
      return;
    }
    if (addedAnyway) {
      added++;
      index++;
      UI.post(this::launchNext, NEXT_PART_DELAY_MS);
      return;
    }
    final XgramWaStickerStore.Pack pack = packs.get(index);
    // The next part WhatsApp doesn't have yet; none left: launchNext counts the rest as there already, then the summary
    int n = index + 1;
    while (n < packs.size() && alreadyAdded[n]) {
      n++;
    }
    if (n >= packs.size()) {
      index++;
      launchNext();
      return;
    }
    final XgramWaStickerStore.Pack next = packs.get(n);
    BaseActivity activity = UI.getUiContext();
    if (activity == null || activity.isFinishing()) {
      end();
      return;
    }
    AlertDialog.Builder b = new AlertDialog.Builder(activity, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.XgramWaAddToWhatsApp));
    b.setMessage(Lang.getString(R.string.XgramWaNextPart, pack.name, next.name));
    b.setPositiveButton(Lang.getString(R.string.XgramWaNextPartAdd), (dialog, which) -> {
      dialog.dismiss();
      index++;
      launchNext();
    });
    b.setNegativeButton(Lang.getString(R.string.XgramWaStop), (dialog, which) -> {
      dialog.dismiss();
      finish();
    });
    b.setCancelable(false);
    if (activity.showAlert(b) == null) {
      finish();
    }
  }

  @UiThread
  private void finish () {
    if (current != this) {
      return;
    }
    final int total = packs != null ? packs.size() : 0;
    final int inWhatsApp = added + skipped;
    StringBuilder b = new StringBuilder();
    if (inWhatsApp == 0) {
      b.append(Lang.getString(R.string.XgramWaNotAdded));
    } else if (added == 0 && skipped == total) {
      b.append(Lang.getString(R.string.XgramWaAlreadyAdded));
    } else if (total == 1) {
      b.append(Lang.getString(R.string.XgramWaAdded));
    } else {
      b.append(Lang.getString(R.string.XgramWaAddedParts, inWhatsApp, total));
    }
    // Two lines at most (Android 12+ toasts and the M3E snackbar): the result, then what was left out
    if (inWhatsApp > 0) {
      if (failedStickers > 0 && leftOutAnimated > 0) {
        b.append('\n').append(Lang.getString(R.string.XgramWaLeftOutBoth, leftOutAnimated, failedStickers));
      } else if (failedStickers > 0) {
        b.append('\n').append(Lang.getString(R.string.XgramWaLeftOutFailed, failedStickers));
      } else if (leftOutAnimated > 0) {
        b.append('\n').append(Lang.getString(R.string.XgramWaLeftOutAnimated, leftOutAnimated));
      }
    }
    UI.showToast(b.toString(), Toast.LENGTH_LONG);
    end();
  }

  @UiThread
  private void end () {
    if (waitingForResume) {
      waitingForResume = false;
      UI.removeStateListener(resumeListener);
    }
    if (current == this) {
      current = null;
    }
  }

  @UiThread
  private void alert (CharSequence message) {
    BaseActivity activity = UI.getUiContext();
    if (activity == null || activity.isFinishing()) {
      UI.showToast(message, Toast.LENGTH_LONG);
      return;
    }
    AlertDialog.Builder b = new AlertDialog.Builder(activity, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.XgramWaAddToWhatsApp));
    b.setMessage(message);
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    activity.showAlert(b);
  }
}
