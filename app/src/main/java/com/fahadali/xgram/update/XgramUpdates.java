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
package com.fahadali.xgram.update;

import android.app.AlertDialog;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.AppUpdater;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.leveldb.LevelDB;

/**
 * XGram's in-app updates (Settings → XGram → Updates), read from the bot's posts on Telegram:
 * <ul>
 *   <li>Stable: APKs in the Updates topic of @XGramTD, and the "Stable" posts in @XGramci (where CI posts releases).</li>
 *   <li>CI builds: every APK in @XGramci.</li>
 * </ul>
 * The bot's captions come from tools/xgram/telegram/xgram_bot.py (apk_caption): the first line is
 * "📦 XGram 0.29.1.1818.5 · Stable · arm64-v8a" (or "⚙️ … · CI build"), followed by the file name, the size,
 * "Commit: …" and "SHA-256: …". The changelog is the bot's reply to the post ("… · Stable: what's new").
 *
 * {@link AppUpdater} does the rest (prompt, download, install) through its Telegram channel flow.
 */
public final class XgramUpdates {
  private XgramUpdates () { }

  public static final int CHANNEL_STABLE = 0, CHANNEL_CI = 1, CHANNEL_OFF = 2;

  public static final String STABLE_GROUP = "XGramTD", CI_CHANNEL = "XGramci";
  public static final int STABLE_TOPIC_ID = 3;
  /** The bot that posts the APKs (tools/xgram/telegram/xgram_bot.py). */
  private static final String BOT_USERNAME = "XGramcibot";

  private static final String KEY_CHANNEL = "xgram_update_channel"; // a setting (exported by XgramBackup)
  private static final String KEY_LAST_CHECK = "xgram_update_last_check"; // local state, not exported
  private static final String KEY_FOUND_CODE = "xgram_update_found_code";
  // Local state: whether the post of the installed build was a Stable release or a CI build.
  private static final String KEY_INSTALLED_CODE = "xgram_update_installed_code";
  private static final String KEY_INSTALLED_STABLE = "xgram_update_installed_stable";

  /** The kind of the installed build, as its post in @XGramTD or @XGramci said: 1 Stable, 0 CI build, -1 not seen yet. */
  public static int installedKind () {
    if (pmc().getInt(KEY_INSTALLED_CODE, 0) != BuildConfig.ORIGINAL_VERSION_CODE) {
      return -1;
    }
    return pmc().getBoolean(KEY_INSTALLED_STABLE, false) ? 1 : 0;
  }

  private static void noteInstalledPost (boolean isStable) {
    // The same build can be posted as a CI build first and as Stable later: Stable wins.
    if (pmc().getInt(KEY_INSTALLED_CODE, 0) != BuildConfig.ORIGINAL_VERSION_CODE || (isStable && !pmc().getBoolean(KEY_INSTALLED_STABLE, false))) {
      pmc().putInt(KEY_INSTALLED_CODE, BuildConfig.ORIGINAL_VERSION_CODE);
      pmc().putBoolean(KEY_INSTALLED_STABLE, isStable);
    }
  }

  private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
  private static final long RETRY_AFTER_ERROR_MS = 30L * 60L * 1000L;
  private static final int PAGE_SIZE = 50, MAX_PAGES = 3;

  private static volatile boolean checkRequested, checkedThisProcess, verifying;
  private static volatile @Nullable String lastError;

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  // Settings

  public static int channel () {
    int channel = pmc().getInt(KEY_CHANNEL, CHANNEL_STABLE);
    return channel == CHANNEL_CI || channel == CHANNEL_OFF ? channel : CHANNEL_STABLE;
  }

  public static void setChannel (int channel) {
    if (channel == CHANNEL_STABLE) {
      pmc().remove(KEY_CHANNEL);
    } else {
      pmc().putInt(KEY_CHANNEL, channel);
    }
    pmc().remove(KEY_FOUND_CODE);
  }

  public static @StringRes int channelName (int channel) {
    switch (channel) {
      case CHANNEL_CI: return R.string.XgramUpdateChannelCi;
      case CHANNEL_OFF: return R.string.XgramUpdateChannelOff;
      default: return R.string.XgramUpdateChannelStable;
    }
  }

  public static @StringRes int modeName (int autoUpdateMode) {
    switch (autoUpdateMode) {
      case Settings.AUTO_UPDATE_MODE_WIFI_ONLY: return R.string.XgramUpdateModeWifi;
      case Settings.AUTO_UPDATE_MODE_ALWAYS: return R.string.XgramUpdateModeAlways;
      case Settings.AUTO_UPDATE_MODE_NEVER: return R.string.XgramUpdateModeNever;
      default: return R.string.XgramUpdateModeAsk;
    }
  }

  /** The error of the last check, or null when it worked (whether or not it found an update). */
  public static @Nullable String lastError () {
    return lastError;
  }

  // When to check

  /** The next check runs now, whatever the 6-hour limit ("Check for updates"). */
  public static void requestCheck () {
    checkRequested = true;
  }

  /**
   * Whether {@link AppUpdater#checkForUpdates()} may start a new check: never when updates are Off; otherwise on
   * request, every 6 hours, and once per app start when the last check found an update that isn't installed yet
   * (so a downloaded update is offered again after a restart).
   */
  public static boolean isCheckDue () {
    if (channel() == CHANNEL_OFF) {
      checkRequested = false;
      return false;
    }
    if (checkRequested) {
      checkRequested = false;
      return true;
    }
    long now = System.currentTimeMillis();
    long last = pmc().getLong(KEY_LAST_CHECK, 0);
    if (last == 0 || now < last || now - last >= CHECK_INTERVAL_MS) {
      return true;
    }
    return !checkedThisProcess && pmc().getInt(KEY_FOUND_CODE, 0) > BuildConfig.ORIGINAL_VERSION_CODE;
  }

  private static void onCheckDone (@Nullable Found found, @Nullable String error) {
    lastError = error;
    long now = System.currentTimeMillis();
    if (error != null) {
      // Try again in half an hour rather than in 6 hours.
      pmc().putLong(KEY_LAST_CHECK, now - CHECK_INTERVAL_MS + RETRY_AFTER_ERROR_MS);
    } else {
      pmc().putLong(KEY_LAST_CHECK, now);
      if (found != null) {
        pmc().putInt(KEY_FOUND_CODE, found.versionCode);
      } else {
        pmc().remove(KEY_FOUND_CODE);
      }
    }
  }

  // Finding the update

  /** The newest post with an APK for this build that is newer than it. */
  public static final class Found {
    public final Tdlib tdlib;
    public final Tdlib.UpdateFileInfo file;
    public final TdApi.Message message;
    public final String versionName, sha256;
    public final int versionCode;
    public final boolean isStable;
    /** The bot's "what's new" reply, or the caption when there is none. */
    public String changelog;

    Found (Tdlib tdlib, Tdlib.UpdateFileInfo file, TdApi.Message message, String versionName, int versionCode, String sha256, boolean isStable) {
      this.tdlib = tdlib;
      this.file = file;
      this.message = message;
      this.versionName = versionName;
      this.versionCode = versionCode;
      this.sha256 = sha256;
      this.isStable = isStable;
    }
  }

  private static final class Source {
    final String username;
    final @Nullable TdApi.MessageTopic topicId;
    final boolean stableOnly;

    Source (String username, @Nullable TdApi.MessageTopic topicId, boolean stableOnly) {
      this.username = username;
      this.topicId = topicId;
      this.stableOnly = stableOnly;
    }
  }

  private static List<Source> sources (int channel) {
    List<Source> sources = new ArrayList<>(2);
    if (channel == CHANNEL_CI) {
      sources.add(new Source(CI_CHANNEL, null, false));
    } else if (channel == CHANNEL_STABLE) {
      sources.add(new Source(STABLE_GROUP, new TdApi.MessageTopicForum(STABLE_TOPIC_ID), true));
      // CI (xgram-release.yml) posts releases to the channel and only announces them in the Updates topic.
      sources.add(new Source(CI_CHANNEL, null, true));
    }
    return sources;
  }

  /**
   * Looks for an update for {@link AppUpdater}. {@code onDone} gets null when there is none, when updates are Off,
   * in recovery mode, when logged out or on an error ({@link #lastError()}). It may run on any thread.
   */
  public static void find (@NonNull Tdlib tdlib, @NonNull RunnableData<Found> onDone) {
    // On app start TDLib may still be starting: wait until it knows whether the account is logged in.
    tdlib.awaitReadyOrWaitingForData(() -> {
      int channel = channel();
      if (channel == CHANNEL_OFF || tdlib.context().inRecoveryMode() || !tdlib.isAuthorized()) {
        lastError = null;
        onDone.runWithData(null);
        return;
      }
      checkedThisProcess = true;
      List<Source> sources = sources(channel);
      new Search(tdlib, sources, (found, error) -> {
        if (channel() != channel) {
          // The channel changed during the check: check the new one instead (nothing when it is Off).
          find(tdlib, onDone);
          return;
        }
        onCheckDone(found, error);
        if (error != null) {
          Log.w("XGram update check failed: %s", error);
        }
        onDone.runWithData(found);
      }).next();
    });
  }

  private interface SearchCallback {
    void onDone (@Nullable Found found, @Nullable String error);
  }

  /** Goes through the sources one by one and keeps the newest update found. */
  private static final class Search {
    private final Tdlib tdlib;
    private final List<Source> sources;
    private final SearchCallback callback;
    private int sourceIndex = -1;
    private @Nullable Found best;
    private @Nullable String error;

    // Current source
    private long chatId;
    private int page;
    private @Nullable Found sourceFound;

    Search (Tdlib tdlib, List<Source> sources, SearchCallback callback) {
      this.tdlib = tdlib;
      this.sources = sources;
      this.callback = callback;
    }

    void next () {
      sourceIndex++;
      if (sourceIndex >= sources.size()) {
        finish();
        return;
      }
      Source source = sources.get(sourceIndex);
      page = 0;
      sourceFound = null;
      tdlib.send(new TdApi.SearchPublicChat(source.username), (chat, error) -> {
        if (error != null) {
          fail("@" + source.username + ": " + error.message);
          next();
        } else {
          chatId = chat.id;
          searchPage(source, 0);
        }
      });
    }

    private void fail (String message) {
      if (error == null) {
        error = message;
      }
    }

    private void searchPage (Source source, long fromMessageId) {
      tdlib.send(new TdApi.SearchChatMessages(chatId, source.topicId, "", null, fromMessageId, 0, PAGE_SIZE, new TdApi.SearchMessagesFilterDocument()), (result, error) -> {
        if (error != null) {
          fail("@" + source.username + ": " + error.message);
          next();
          return;
        }
        boolean done = false;
        for (TdApi.Message message : result.messages) {
          int verdict = check(source, message);
          if (verdict == VERDICT_FOUND || verdict == VERDICT_NOT_NEWER) {
            done = true;
            break;
          }
        }
        page++;
        if (!done && result.nextFromMessageId != 0 && result.messages.length > 0 && page < MAX_PAGES) {
          searchPage(source, result.nextFromMessageId);
        } else {
          sourceDone();
        }
      });
    }

    private static final int VERDICT_SKIP = 0, VERDICT_FOUND = 1, VERDICT_NOT_NEWER = 2;

    /** Newest posts come first: the first matching APK decides for this source. */
    private int check (Source source, TdApi.Message message) {
      if (message.content.getConstructor() != TdApi.MessageDocument.CONSTRUCTOR || !isTrustedSender(message)) {
        return VERDICT_SKIP;
      }
      TdApi.MessageDocument content = (TdApi.MessageDocument) message.content;
      ApkName name = ApkName.parse(content.document.fileName);
      if (name == null) {
        return VERDICT_SKIP;
      }
      String caption = content.caption != null ? content.caption.text : "";
      boolean isStable = isStableCaption(caption);
      if (name.versionCode == BuildConfig.ORIGINAL_VERSION_CODE) {
        noteInstalledPost(isStable);
      }
      if (source.stableOnly && !isStable) {
        return VERDICT_SKIP;
      }
      if (!name.matchesThisBuild()) {
        return VERDICT_SKIP; // look further back for a post with this build's ABI
      }
      if (name.versionCode <= BuildConfig.ORIGINAL_VERSION_CODE) {
        return VERDICT_NOT_NEWER;
      }
      String sha256 = captionSha256(caption);
      if (sha256 == null) {
        return VERDICT_SKIP; // can't be checked before installing
      }
      Tdlib.UpdateFileInfo file = new Tdlib.UpdateFileInfo(content.document, name.versionCode, name.versionName, captionCommit(caption));
      sourceFound = new Found(tdlib, file, message, name.versionName, name.versionCode, sha256, isStable);
      sourceFound.changelog = caption;
      return VERDICT_FOUND;
    }

    private boolean isTrustedSender (TdApi.Message message) {
      // Channel posts come from the channel's admins. In the group, only XGram's bot (or the group itself) counts.
      if (message.isChannelPost) {
        return true;
      }
      TdApi.MessageSender sender = message.senderId;
      if (sender instanceof TdApi.MessageSenderChat) {
        return ((TdApi.MessageSenderChat) sender).chatId == message.chatId;
      }
      if (!tdlib.cache().senderBot(sender)) {
        return false;
      }
      String username = tdlib.cache().userUsername(((TdApi.MessageSenderUser) sender).userId);
      return BOT_USERNAME.equalsIgnoreCase(username);
    }

    private void sourceDone () {
      Found found = sourceFound;
      if (found != null && (best == null || found.versionCode > best.versionCode)) {
        best = found;
      }
      next();
    }

    private void finish () {
      Found found = best;
      if (found == null) {
        callback.onDone(null, error);
        return;
      }
      // An update was found: errors from the other source don't matter. Now the "what's new" reply.
      TdApi.Message post = found.message;
      tdlib.send(new TdApi.GetChatHistory(post.chatId, post.id, -30, 31, false), (messages, error) -> {
        if (messages != null) {
          String changelog = findChangelog(messages.messages, found);
          if (changelog != null) {
            found.changelog = changelog;
          }
        }
        callback.onDone(found, null);
      });
    }

    private @Nullable String findChangelog (TdApi.Message[] messages, Found found) {
      TdApi.MessageDocument post = (TdApi.MessageDocument) found.message.content;
      String heading = firstLine(post.caption != null ? post.caption.text : "");
      // Newest come first: walk forward from the post, so the nearest notes win.
      for (int i = messages.length - 1; i >= 0; i--) {
        TdApi.Message message = messages[i];
        if (message.id <= found.message.id || message.content.getConstructor() != TdApi.MessageText.CONSTRUCTOR || !isTrustedSender(message)) {
          continue;
        }
        String text = ((TdApi.MessageText) message.content).text.text;
        // The bot replies to the first APK of a release, which may be another ABI's: the heading finds that one.
        boolean isReply = message.replyTo instanceof TdApi.MessageReplyToMessage &&
          ((TdApi.MessageReplyToMessage) message.replyTo).messageId == found.message.id;
        if (isReply || isNotesFor(firstLine(text), heading)) {
          return stripHeading(text);
        }
      }
      return null;
    }
  }

  /** "XGram-0.29.1.1818.5-arm64-v8a.apk" (arm64) or "XGram-0.29.1.1818.5.apk" (universal). */
  static final class ApkName {
    final String versionName, abi;
    final int versionCode;

    private ApkName (String versionName, String abi, int versionCode) {
      this.versionName = versionName;
      this.abi = abi;
      this.versionCode = versionCode;
    }

    static @Nullable ApkName parse (@Nullable String fileName) {
      final String prefix = "XGram-", suffix = ".apk";
      if (StringUtils.isEmpty(fileName) || !fileName.startsWith(prefix) || !fileName.toLowerCase().endsWith(suffix) || fileName.length() <= prefix.length() + suffix.length()) {
        return null;
      }
      String middle = fileName.substring(prefix.length(), fileName.length() - suffix.length());
      int dash = middle.indexOf('-');
      String versionName = dash == -1 ? middle : middle.substring(0, dash);
      String abi = dash == -1 ? "" : middle.substring(dash + 1);
      if (abi.contains("debug")) {
        return null; // debug builds are never posted, but don't offer one if it is
      }
      int code = versionCode(versionName);
      return code > 0 ? new ApkName(versionName, abi, code) : null;
    }

    boolean matchesThisBuild () {
      if (!BuildConfig.LATEST_FLAVOR) {
        return false; // only the latest-SDK APKs are posted; legacy builds can't take them
      }
      if (BuildConfig.ARM64_FLAVOR) {
        return "arm64-v8a".equals(abi);
      }
      // Universal builds (and anything else) take the universal APK.
      return abi.isEmpty() || "universal".equals(abi);
    }
  }

  /** 0.29.1.1818.5 → 1818 * 1000 + 5, the same number as BuildConfig.ORIGINAL_VERSION_CODE. */
  static int versionCode (String versionName) {
    int lastDot = versionName.lastIndexOf('.');
    if (lastDot <= 0) {
      return 0;
    }
    int prevDot = versionName.lastIndexOf('.', lastDot - 1);
    String app = versionName.substring(prevDot + 1, lastDot), build = versionName.substring(lastDot + 1);
    if (!isDigits(app) || !isDigits(build) || build.length() > 3 || app.length() > 6) {
      return 0;
    }
    return Integer.parseInt(app) * 1000 + Integer.parseInt(build);
  }

  private static boolean isDigits (String s) {
    if (s.isEmpty()) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static String firstLine (String text) {
    int newLine = text.indexOf('\n');
    return newLine == -1 ? text : text.substring(0, newLine);
  }

  static boolean isStableCaption (String caption) {
    return firstLine(caption).contains("· Stable");
  }

  /**
   * Whether "📦 XGram 1.0 · Stable: what's new" is the notes line for the caption heading "📦 XGram 1.0 · Stable · arm64-v8a"
   * (or "… · Stable" for the universal APK). The heading carries the release name, which may differ from the file's version.
   */
  static boolean isNotesFor (String notesLine, String captionHeading) {
    final String suffix = ": what's new";
    if (!notesLine.endsWith(suffix) || StringUtils.isEmpty(captionHeading)) {
      return false;
    }
    String prefix = notesLine.substring(0, notesLine.length() - suffix.length());
    return !prefix.isEmpty() && (captionHeading.equals(prefix) || captionHeading.startsWith(prefix + " · "));
  }

  /** The 64 hex digits after "SHA-256:", lower case, or null. */
  static @Nullable String captionSha256 (String caption) {
    String value = captionValue(caption, "SHA-256:");
    if (value == null || value.length() != 64) {
      return null;
    }
    for (int i = 0; i < value.length(); i++) {
      if (Character.digit(value.charAt(i), 16) == -1) {
        return null;
      }
    }
    return value.toLowerCase();
  }

  static @Nullable String captionCommit (String caption) {
    String value = captionValue(caption, "Commit:");
    return StringUtils.isEmpty(value) ? null : value;
  }

  /** The letters and digits after a label like "Commit:". */
  private static @Nullable String captionValue (String caption, String label) {
    int index = caption.indexOf(label);
    if (index == -1) {
      return null;
    }
    int start = index + label.length();
    while (start < caption.length() && Character.isWhitespace(caption.charAt(start))) {
      start++;
    }
    int end = start;
    while (end < caption.length() && Character.isLetterOrDigit(caption.charAt(end))) {
      end++;
    }
    return end > start ? caption.substring(start, end) : null;
  }

  /** Drops the bot's "📦 XGram … · Stable: what's new" line; the dialog's title says it. */
  private static String stripHeading (String text) {
    String first = firstLine(text);
    if (first.contains("what's new") && first.length() < text.length()) {
      return text.substring(first.length()).trim();
    }
    return text.trim();
  }

  // Prompt actions

  /** The changelog, with Download (while the update isn't downloaded yet) and Open post. */
  public static void showWhatsNew (ViewController<?> c, @Nullable Found found, @Nullable Runnable download) {
    if (found == null || c.isDestroyed()) {
      return;
    }
    AlertDialog.Builder b = new AlertDialog.Builder(c.context(), Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.XgramUpdateWhatsNewTitle, found.versionName));
    b.setMessage(StringUtils.isEmpty(found.changelog) ? Lang.getString(R.string.XgramUpdateNoChangelog) : found.changelog);
    // Two short buttons in one row: a third (Open post, already in the update prompt) made the theme stack them in a
    // scrolling area, one of them drawn without text.
    if (download != null && c.context().appUpdater().state() == AppUpdater.State.AVAILABLE) {
      b.setPositiveButton(Lang.getString(R.string.XgramUpdateDownload), (dialog, which) -> download.run());
    } else {
      b.setPositiveButton(Lang.getString(R.string.XgramUpdateOpenPost), (dialog, which) -> openPost(c, found));
    }
    b.setNegativeButton(Lang.getString(R.string.XgramUpdateClose), (dialog, which) -> dialog.dismiss());
    c.showAlert(b);
  }

  /** Opens the post with the APK (in the Updates topic, or in the channel). */
  public static void openPost (ViewController<?> c, @Nullable Found found) {
    if (found == null || c.isDestroyed()) {
      return;
    }
    TdlibDelegate context = c.tdlib() == found.tdlib ? c : new TdlibContext(c.context(), found.tdlib);
    TdApi.Message post = found.message;
    found.tdlib.send(new TdApi.GetMessageLink(post.chatId, post.id, 0, 0, "", false, post.topicId instanceof TdApi.MessageTopicForum), (link, error) -> found.tdlib.ui().post(() -> {
      if (c.isDestroyed()) {
        return;
      }
      if (link != null && !StringUtils.isEmpty(link.link)) {
        found.tdlib.ui().openUrl(context, link.link, new TdlibUi.UrlOpenParameters().disableInstantView());
      } else {
        found.tdlib.ui().openMessage(context, post, null);
      }
    }));
  }

  // Installing

  /**
   * Runs {@code install} only when the downloaded file's SHA-256 equals the post's "SHA-256:" value. On a mismatch
   * the file is deleted (the update can be downloaded again) and an error is shown.
   */
  public static void verifyThenInstall (@Nullable Tdlib tdlib, @Nullable Found found, @NonNull Runnable install) {
    if (tdlib == null || found == null) {
      UI.showToast(R.string.XgramUpdateBadFile, Toast.LENGTH_LONG);
      return;
    }
    if (verifying) {
      return;
    }
    verifying = true;
    TdApi.File file = found.file.document.document;
    String path = file.local.path;
    new Thread(() -> {
      String actual = sha256(path);
      boolean ok = actual != null && actual.equals(found.sha256);
      UI.post(() -> {
        verifying = false;
        if (ok) {
          install.run();
        } else {
          Log.w("XGram update: SHA-256 mismatch, expected %s, got %s", found.sha256, actual);
          tdlib.send(new TdApi.DeleteFile(file.id), (result, error) -> { });
          UI.showToast(R.string.XgramUpdateBadFile, Toast.LENGTH_LONG);
        }
      });
    }, "XgramUpdateHash").start();
  }

  private static @Nullable String sha256 (@Nullable String path) {
    if (StringUtils.isEmpty(path)) {
      return null;
    }
    File file = new File(path);
    if (!file.isFile()) {
      return null;
    }
    try (InputStream in = new FileInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      byte[] hash = digest.digest();
      StringBuilder b = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        b.append(Character.forDigit((value >> 4) & 0xf, 16)).append(Character.forDigit(value & 0xf, 16));
      }
      return b.toString();
    } catch (Throwable t) {
      Log.e("XGram update: can't read the downloaded file", t);
      return null;
    }
  }
}
