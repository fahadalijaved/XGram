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

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;

import com.fahadali.xgram.appearance.XgramAmoled;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMaterialYou;
import com.fahadali.xgram.layout.XgramTwoPane;
import com.fahadali.xgram.update.XgramUpdates;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.DeviceTokenRetrieverInstance;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.Crash;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.vkryl.android.SdkVersion;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.leveldb.LevelDB;

/**
 * XGram's logs for bug reports, so that every user has something to send:
 * <ul>
 *   <li>A safety log that is always on: the errors and warnings of Telegram X's {@link Log}, whatever its level, in two
 *   files of at most 160 KB in files/logs ({@link #SAFETY_FILE}, {@link #SAFETY_FILE_OLD}). Lines are rate-limited,
 *   formatted on the caller's thread and written in batches on a background thread (at once while the app crashes).
 *   TDLib objects in the arguments are written as their class name, and what people wrote is left out of TDLib dumps.</li>
 *   <li>Share logs: one .zip with that log, Telegram X's app logs, crash reports, the TDLib log, Android's exit reasons
 *   and a device and build summary, handed to the Android share sheet after a privacy note. Nothing is uploaded by
 *   itself.</li>
 *   <li>Record detailed logs: the app's log level at VERBOSE and TDLib's at 3 (its td_requests dumps muted) for 24
 *   hours, then back to what they were (on the next start at the latest). TDLib's log still holds message text and
 *   names then: the row and the privacy note say so, and Share logs adds that log only while TDLib logging is on or
 *   when it was written in the last 24 hours.</li>
 *   <li>The Testing Utilities gate: members of the XGram group (t.me/XGramTD) get more of them, its admins all.</li>
 * </ul>
 */
public final class XgramLogs {
  private XgramLogs () { }

  // Always-on safety log

  public static final String SAFETY_FILE = "xgram-safety.log", SAFETY_FILE_OLD = "xgram-safety.1.log";
  private static final long SAFETY_MAX_FILE = 160 * 1024;
  private static final int MAX_PENDING_CHARS = 48 * 1024;
  private static final int MAX_MESSAGE_CHARS = 2000, MAX_TRACE_CHARS = 4000;
  /** At most this many lines per window; the rest is counted and written as one "left out" line. */
  private static final long WINDOW_MS = 10_000;
  private static final int WINDOW_LINES = 40;
  private static final long FLUSH_DELAY_MS = 2000, ERROR_FLUSH_DELAY_MS = 250;
  /** At most this much of a line is searched for what people wrote; it is cut to far less afterwards. */
  private static final int MAX_REDACT_CHARS = 64 * 1024;

  private static final class Line {
    final long time;
    final int level;
    final String text;

    Line (long time, int level, String text) {
      this.time = time;
      this.level = level;
      this.text = text;
    }
  }

  private static final Object pendingLock = new Object(), fileLock = new Object();
  private static final ArrayList<Line> pending = new ArrayList<>(); // pendingLock
  private static int pendingChars, windowLines, skippedLines; // pendingLock
  private static long windowStart, flushAt; // pendingLock
  private static boolean flushScheduled, wroteSessionHeader; // pendingLock
  private static @Nullable Handler writer; // pendingLock
  private static @Nullable SimpleDateFormat lineDateFormat; // fileLock

  private static final Runnable FLUSH = XgramLogs::flush;

  /**
   * Telegram X's {@link Log#log} calls this for every assert, error and warning, on any thread. Never calls Log itself.
   *
   * @param sync write now (the app is crashing: {@link Log#RUNTIME_NOT_ASYNC})
   */
  public static void record (int tag, int level, @NonNull String fmt, @Nullable Throwable t, @Nullable Object[] args, boolean sync) {
    final long time = System.currentTimeMillis();
    synchronized (pendingLock) {
      final long now = SystemClock.uptimeMillis();
      if (windowStart == 0 || now - windowStart >= WINDOW_MS) {
        windowStart = now;
        windowLines = 0;
      }
      // A crash line (sync) is always kept: the process dies right after it
      if (!sync && (windowLines >= WINDOW_LINES || pendingChars >= MAX_PENDING_CHARS)) {
        skippedLines++;
        return;
      }
      windowLines++;
    }
    String text;
    try {
      text = format(tag, fmt, t, args);
    } catch (Throwable ignored) {
      text = fmt;
    }
    synchronized (pendingLock) {
      if (skippedLines > 0) {
        pending.add(new Line(time, Log.LEVEL_WARNING, "(" + skippedLines + " more lines left out)"));
        skippedLines = 0;
      }
      pending.add(new Line(time, level, text));
      pendingChars += text.length();
      if (!sync) {
        // Errors are written sooner; a flush already due earlier is never put off by a later line
        long delay = level <= Log.LEVEL_ERROR ? ERROR_FLUSH_DELAY_MS : FLUSH_DELAY_MS;
        long at = SystemClock.uptimeMillis() + delay;
        Handler handler = writer();
        if (handler != null && (!flushScheduled || at < flushAt)) {
          handler.removeCallbacks(FLUSH);
          handler.postAtTime(FLUSH, at);
          flushScheduled = true;
          flushAt = at;
        }
      }
    }
    if (sync) {
      flush();
    }
  }

  private static @Nullable Handler writer () { // pendingLock
    if (writer == null) {
      try {
        HandlerThread thread = new HandlerThread("XgramLogs", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        writer = new Handler(thread.getLooper());
      } catch (Throwable ignored) {
        return null;
      }
    }
    return writer;
  }

  private static String format (int tag, @NonNull String fmt, @Nullable Throwable t, @Nullable Object[] args) {
    String message = args != null && args.length > 0 ? String.format(Locale.US, fmt, privateArgs(args)) : fmt;
    StringBuilder b = new StringBuilder(Math.min(message.length(), MAX_MESSAGE_CHARS) + 32);
    if (tag != 0) {
      String name;
      try {
        name = Log.getLogTag(tag);
      } catch (Throwable ignored) {
        name = Integer.toString(tag);
      }
      if (!StringUtils.isEmpty(name)) {
        b.append('[').append(name).append("] ");
      }
    }
    // What people wrote goes before the cut, so that a cut never leaves a string open
    appendCapped(b, redact(head(message, MAX_REDACT_CHARS)), MAX_MESSAGE_CHARS);
    if (t != null) {
      String trace = android.util.Log.getStackTraceString(t);
      if (StringUtils.isEmpty(trace)) { // Android leaves out UnknownHostException
        trace = t.getClass().getName() + ": " + t.getMessage();
      }
      if (b.length() > 0) {
        b.append('\n');
      }
      appendCapped(b, redact(head(trace.trim(), MAX_REDACT_CHARS)), MAX_TRACE_CHARS);
    }
    return b.toString();
  }

  /** A TDLib object prints everything in it, messages included: its class name instead, an error's code and text. */
  private static Object[] privateArgs (@NonNull Object[] args) {
    Object[] out = null;
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      if (arg instanceof TdApi.Object) {
        if (out == null) {
          out = args.clone();
        }
        if (arg instanceof TdApi.Error) {
          TdApi.Error error = (TdApi.Error) arg;
          out[i] = "Error " + error.code + ": " + error.message;
        } else {
          out[i] = arg.getClass().getSimpleName();
        }
      }
    }
    return out != null ? out : args;
  }

  private static String head (String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }

  private static void appendCapped (StringBuilder b, String s, int max) {
    if (s.length() <= max) {
      b.append(s);
    } else {
      b.append(s, 0, max).append("… (").append(s.length() - max).append(" more characters)");
    }
  }

  /** Writes the waiting lines: on the writer thread, on a crashing thread through {@link #record}, or before sharing. */
  public static void flush () {
    final ArrayList<Line> lines;
    final boolean header;
    synchronized (pendingLock) {
      flushScheduled = false;
      if (writer != null) {
        writer.removeCallbacks(FLUSH);
      }
      if (pending.isEmpty()) {
        return;
      }
      lines = new ArrayList<>(pending);
      pending.clear();
      pendingChars = 0;
      header = !wroteSessionHeader;
      wroteSessionHeader = true;
    }
    synchronized (fileLock) {
      try {
        writeLines(lines, header);
      } catch (Throwable t) {
        android.util.Log.w(Log.LOG_TAG, "XGram: cannot write the safety log", t);
      }
    }
  }

  // Text people wrote, as TDLib prints it in an object dump (text = "...", first_name = "..."). TDLib escapes neither
  // quotes nor line breaks in a string, so one ends at the quote that the next field or a "}" follows. A string with no
  // such end (a dump cut short, or on one line) is left out up to the end.
  private static final Pattern USER_TEXT = Pattern.compile(
    "\\b(text|caption|question|explanation|first_name|last_name|phone_number|bio|description|address|vcard|query|title|name|username|editable_username|url|email_address|file_name|path) = \"(?:.*?\"(?=\\n\\s*(?:[a-z0-9_]+ = |\\})|$)|.*)",
    Pattern.DOTALL
  );
  // Usernames, which TDLib prints as a vector of bare strings
  private static final Pattern USER_VECTORS = Pattern.compile(
    "\\b(active_usernames|disabled_usernames) = vector\\[\\d+\\] \\{.*?\\n\\s*\\}",
    Pattern.DOTALL
  );

  /** s without what people wrote in the TDLib dumps in it (messages, names, usernames, phone numbers, links). */
  private static String redact (String s) {
    if (s.indexOf(" = ") == -1) {
      return s;
    }
    if (s.contains(" = vector[")) {
      s = USER_VECTORS.matcher(s).replaceAll("$1 = vector { … }");
    }
    if (s.contains(" = \"")) {
      s = USER_TEXT.matcher(s).replaceAll("$1 = \"…\"");
    }
    return s;
  }

  private static void writeLines (List<Line> lines, boolean header) throws IOException { // fileLock
    File dir = Log.getLogDir();
    if (dir == null) {
      return;
    }
    File file = new File(dir, SAFETY_FILE);
    if (file.length() >= SAFETY_MAX_FILE) {
      File old = new File(dir, SAFETY_FILE_OLD);
      //noinspection ResultOfMethodCallIgnored
      old.delete();
      if (!file.renameTo(old)) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }
    if (lineDateFormat == null) {
      lineDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    }
    StringBuilder b = new StringBuilder(4096);
    if (header) {
      b.append("=== XGram ").append(BuildConfig.VERSION_NAME).append(", process ").append(Process.myPid()).append(" ===\n");
    }
    Date date = new Date();
    for (Line line : lines) {
      date.setTime(line.time);
      b.append(lineDateFormat.format(date)).append(' ').append(levelLetter(line.level)).append(' ');
      appendIndented(b, line.text);
      b.append('\n');
    }
    try (FileOutputStream out = new FileOutputStream(file, true)) {
      out.write(b.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  /** text, which {@link #format} already made private. */
  private static void appendIndented (StringBuilder b, String s) {
    // Lines after the first (stack traces) are indented under their line
    int start = 0, end;
    while ((end = s.indexOf('\n', start)) != -1) {
      b.append(s, start, end).append("\n    ");
      start = end + 1;
    }
    b.append(s, start, s.length());
  }

  private static char levelLetter (int level) {
    switch (level) {
      case Log.LEVEL_ASSERT: return 'A';
      case Log.LEVEL_ERROR: return 'E';
      case Log.LEVEL_WARNING: return 'W';
      default: return 'I';
    }
  }

  private static String levelName (int level) {
    switch (level) {
      case Log.LEVEL_ASSERT: return "ASSERT";
      case Log.LEVEL_ERROR: return "ERROR";
      case Log.LEVEL_WARNING: return "WARNING";
      case Log.LEVEL_INFO: return "INFO";
      case Log.LEVEL_DEBUG: return "DEBUG";
      case Log.LEVEL_VERBOSE: return "VERBOSE";
      default: return Integer.toString(level);
    }
  }

  // Record detailed logs

  public static final int DETAILED_APP_LEVEL = Log.LEVEL_VERBOSE, DETAILED_TDLIB_LEVEL = 3;
  public static final long DETAILED_DURATION_MS = TimeUnit.HOURS.toMillis(24);
  private static final long DETAILED_MAX_LEFT_MS = DETAILED_DURATION_MS + TimeUnit.HOURS.toMillis(1); // more: the clock moved
  private static final long EXPIRY_CHECK_MS = TimeUnit.MINUTES.toMillis(30);

  private static final String KEY_DETAILED_UNTIL = "xgram_logs_detailed_until";
  private static final String KEY_DETAILED_PREV_LEVEL = "xgram_logs_detailed_prev_level";
  private static final String KEY_DETAILED_PREV_TDLIB = "xgram_logs_detailed_prev_tdlib";
  /** Settings' key for TDLib's verbosity (Settings.KEY_TDLIB_VERBOSITY, private): read before Settings exists. */
  private static final String KEY_TDLIB_VERBOSITY = "settings_tdlib_verbosity";
  private static final String TDLIB_REQUESTS_TAG = "td_requests";
  /** TDLib's VERBOSITY_NAME(NEVER), the highest level a tag can have. */
  private static final int TDLIB_TAG_NEVER = 1024;

  /** Pages that show the detailed logs row (the version page). Main thread. */
  public interface Listener {
    void onDetailedLogsChanged ();
  }

  private static final ReferenceList<Listener> listeners = new ReferenceList<>(true);

  public static void addListener (@NonNull Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (@NonNull Listener listener) {
    listeners.remove(listener);
  }

  private static void notifyListeners () {
    for (Listener listener : listeners) {
      listener.onDetailedLogsChanged();
    }
  }

  private static @Nullable Handler mainHandler;
  private static final Runnable EXPIRY_CHECK = XgramLogs::checkExpiry;

  private static Handler mainHandler () {
    synchronized (XgramLogs.class) {
      if (mainHandler == null) {
        mainHandler = new Handler(Looper.getMainLooper());
      }
      return mainHandler;
    }
  }

  /** When detailed logs stop (wall clock ms), or 0 when they are off. */
  public static long detailedUntil () {
    long until = Settings.instance().pmc().getLong(KEY_DETAILED_UNTIL, 0);
    long left = until - System.currentTimeMillis();
    return left > 0 && left <= DETAILED_MAX_LEFT_MS ? until : 0;
  }

  public static boolean isDetailedOn () {
    return detailedUntil() != 0;
  }

  /** Turns detailed logs on for 24 hours (again from now when on), or off at once. Main thread. */
  public static void setDetailed (boolean on) {
    LevelDB pmc = Settings.instance().pmc();
    Settings.TdlibLogSettings tdlibLogs = Settings.instance().getTdlibLogSettings();
    if (on) {
      if (pmc.getLong(KEY_DETAILED_UNTIL, 0) == 0) {
        // The levels to go back to: the user's own
        pmc.putInt(KEY_DETAILED_PREV_LEVEL, Log.getLogLevel());
        pmc.putInt(KEY_DETAILED_PREV_TDLIB, Math.max(0, tdlibLogs.getVerbosity(null)));
      }
      pmc.putLong(KEY_DETAILED_UNTIL, System.currentTimeMillis() + DETAILED_DURATION_MS);
      if (Log.getLogLevel() < DETAILED_APP_LEVEL) {
        Log.setLogLevel(DETAILED_APP_LEVEL);
      }
      if (tdlibLogs.getVerbosity(null) < DETAILED_TDLIB_LEVEL) {
        tdlibLogs.setVerbosity(null, DETAILED_TDLIB_LEVEL);
      }
      muteTdlibRequests(tdlibLogs, true);
      scheduleExpiryCheck(DETAILED_DURATION_MS);
    } else {
      stopDetailed();
    }
    notifyListeners();
  }

  private static void stopDetailed () {
    LevelDB pmc = Settings.instance().pmc();
    mainHandler().removeCallbacks(EXPIRY_CHECK);
    if (pmc.getLong(KEY_DETAILED_UNTIL, 0) == 0) {
      return;
    }
    int prevLevel = pmc.getInt(KEY_DETAILED_PREV_LEVEL, Log.LEVEL_ASSERT);
    int prevTdlib = pmc.getInt(KEY_DETAILED_PREV_TDLIB, 0);
    pmc.remove(KEY_DETAILED_UNTIL);
    pmc.remove(KEY_DETAILED_PREV_LEVEL);
    pmc.remove(KEY_DETAILED_PREV_TDLIB);
    // Back to the user's levels, unless they changed them meanwhile
    if (Log.getLogLevel() == DETAILED_APP_LEVEL && prevLevel < DETAILED_APP_LEVEL) {
      Log.setLogLevel(prevLevel);
    }
    Settings.TdlibLogSettings tdlibLogs = Settings.instance().getTdlibLogSettings();
    if (tdlibLogs.getVerbosity(null) == DETAILED_TDLIB_LEVEL && prevTdlib < DETAILED_TDLIB_LEVEL) {
      tdlibLogs.setVerbosity(null, prevTdlib);
    }
    muteTdlibRequests(tdlibLogs, false);
  }

  /**
   * TDLib's td_requests tag writes every request, update and result in full (messages, names, phone numbers): detailed
   * logs keep it at NEVER, a level TDLib's own levels never reach. Settings stores it (settings_tdlib_verbosity_td_requests),
   * so it stays muted after a restart; it goes back to TDLib's default only when it is still muted.
   */
  private static void muteTdlibRequests (@NonNull Settings.TdlibLogSettings tdlibLogs, boolean mute) {
    try {
      int verbosity = tdlibLogs.getVerbosity(TDLIB_REQUESTS_TAG);
      if (mute) {
        if (verbosity < TDLIB_TAG_NEVER) {
          tdlibLogs.setVerbosity(TDLIB_REQUESTS_TAG, TDLIB_TAG_NEVER);
        }
      } else if (verbosity == TDLIB_TAG_NEVER) {
        tdlibLogs.setVerbosity(TDLIB_REQUESTS_TAG, tdlibLogs.getDefaultVerbosity(TDLIB_REQUESTS_TAG));
      }
    } catch (Throwable t) {
      android.util.Log.w(Log.LOG_TAG, "XGram: cannot change TDLib's td_requests log", t);
    }
  }

  private static void scheduleExpiryCheck (long left) {
    Handler handler = mainHandler();
    handler.removeCallbacks(EXPIRY_CHECK);
    // uptime stops in deep sleep: look again every half hour rather than once in 24 hours
    handler.postDelayed(EXPIRY_CHECK, Math.max(1000, Math.min(left, EXPIRY_CHECK_MS)));
  }

  private static void checkExpiry () {
    long until = Settings.instance().pmc().getLong(KEY_DETAILED_UNTIL, 0);
    if (until == 0) {
      return;
    }
    long left = until - System.currentTimeMillis();
    if (left <= 0 || left > DETAILED_MAX_LEFT_MS) {
      stopDetailed();
      notifyListeners();
    } else {
      scheduleExpiryCheck(left);
    }
  }

  /**
   * Telegram X's {@link Log#load(SharedPreferences)}, before it reads the levels (Settings is still being made): detailed
   * logs that ran out while XGram was closed go back to the user's levels here, TDLib's before Settings applies them.
   */
  public static void expireOnLoad (@NonNull SharedPreferences prefs, int defaultLogLevel) {
    try {
      long until = prefs.getLong(KEY_DETAILED_UNTIL, 0);
      if (until == 0) {
        return;
      }
      long left = until - System.currentTimeMillis();
      if (left > 0 && left <= DETAILED_MAX_LEFT_MS) {
        scheduleExpiryCheck(left); // still on
        return;
      }
      int prevLevel = prefs.getInt(KEY_DETAILED_PREV_LEVEL, Log.LEVEL_ASSERT);
      int prevTdlib = prefs.getInt(KEY_DETAILED_PREV_TDLIB, 0);
      boolean restoreLevel = prefs.getInt(Settings.KEY_LOG_LEVEL, defaultLogLevel) == DETAILED_APP_LEVEL && prevLevel < DETAILED_APP_LEVEL;
      boolean restoreTdlib = prefs.getInt(KEY_TDLIB_VERBOSITY, 0) == DETAILED_TDLIB_LEVEL && prevTdlib < DETAILED_TDLIB_LEVEL;
      SharedPreferences.Editor editor = prefs instanceof LevelDB ? (LevelDB) prefs : prefs.edit(); // LevelDB: written at once, no batch
      editor.remove(KEY_DETAILED_UNTIL);
      editor.remove(KEY_DETAILED_PREV_LEVEL);
      editor.remove(KEY_DETAILED_PREV_TDLIB);
      String requestsKey = KEY_TDLIB_VERBOSITY + "_" + TDLIB_REQUESTS_TAG;
      if (prefs.getInt(requestsKey, 0) == TDLIB_TAG_NEVER) {
        editor.remove(requestsKey); // TDLib's default, before Settings applies it
      }
      if (restoreLevel) {
        editor.putInt(Settings.KEY_LOG_LEVEL, prevLevel);
      }
      if (restoreTdlib) {
        if (prevTdlib == 0) {
          editor.remove(KEY_TDLIB_VERBOSITY); // Telegram X's default, as Settings stores it
        } else {
          editor.putInt(KEY_TDLIB_VERBOSITY, prevTdlib);
        }
      }
      if (!(prefs instanceof LevelDB)) {
        editor.apply();
      }
    } catch (Throwable t) {
      android.util.Log.w(Log.LOG_TAG, "XGram: cannot end detailed logs", t);
    }
  }

  /** The detailed logs row's second line: when they stop, or what the option does. */
  public static String detailedSubtitle () {
    long until = detailedUntil();
    if (until == 0) {
      return Lang.getString(R.string.XgramLogsDetailedOff);
    }
    Calendar now = Calendar.getInstance();
    Calendar end = Calendar.getInstance();
    end.setTimeInMillis(until);
    boolean today = now.get(Calendar.YEAR) == end.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR);
    return Lang.getString(today ? R.string.XgramLogsDetailedUntil : R.string.XgramLogsDetailedUntilTomorrow, Lang.time(until, TimeUnit.MILLISECONDS));
  }

  // Testing Utilities

  /** The XGram group, t.me/XGramTD (supergroup 4300155486; tools/xgram/telegram, XGRAM_GROUP_ID). */
  public static final long XGRAM_GROUP_CHAT_ID = -1004300155486L;

  /**
   * XGram's gate for Testing Utilities, in place of Telegram X's (subscribers of its channel and members of its testers
   * group): everyone gets the basic ones (READER), members of the XGram group more (TESTER), its admins all (ADMIN).
   * Offline only (the group must be in the chat list), so the version menu never waits for the network. The callback
   * may run on a TDLib thread.
   */
  public static void testerLevel (@NonNull Tdlib tdlib, @NonNull RunnableInt callback) {
    if (tdlib.inRecoveryMode() || tdlib.account().isDebug()) { // a test DC account: the XGram group is not there
      callback.runWithInt(Tdlib.TesterLevel.TESTER);
      return;
    }
    tdlib.chat(XGRAM_GROUP_CHAT_ID, chat -> {
      TdApi.ChatMemberStatus status = chat != null ? tdlib.chatStatus(chat.id) : null;
      int level;
      if (status == null) {
        level = Tdlib.TesterLevel.READER;
      } else if (TD.isAdmin(status)) {
        level = Tdlib.TesterLevel.ADMIN;
      } else if (TD.isMember(status, false)) {
        level = Tdlib.TesterLevel.TESTER;
      } else {
        level = Tdlib.TesterLevel.READER;
      }
      callback.runWithInt(level);
    });
  }

  /**
   * The group's link under Testing utilities ("Members of @XGramTD…"). A slot that is still logging in can't look the
   * group up (TDLib answers 401 Unauthorized), so the link switches to a logged in account and opens it there; with
   * none, it says to log in first. False: this account opens it the usual way.
   */
  public static boolean openGroupLink (@NonNull ViewController<?> c, @Nullable String clickedText) {
    final Tdlib tdlib = c.tdlib();
    if (tdlib == null || tdlib.isAuthorized()) {
      return false;
    }
    final String username = clickedText != null && clickedText.startsWith("@") ? clickedText.substring(1) : clickedText;
    if (username == null || username.isEmpty()) {
      return false;
    }
    final TdlibManager manager = TdlibManager.instance();
    int target = -1;
    for (org.thunderdog.challegram.telegram.TdlibAccount account : manager) {
      // the group is on the production servers: a test DC account can't open it either
      if (account.id != tdlib.id() && !account.isUnauthorized() && !account.isDebug()) {
        target = account.id;
        break;
      }
    }
    if (target == -1) {
      UI.showToast(Lang.getString(R.string.XgramLogsTestingLogIn, username), Toast.LENGTH_SHORT);
      return true;
    }
    final int accountId = target;
    final BaseActivity activity = UI.getContext(c.context());
    if (activity == null) {
      return true;
    }
    if (manager.preferredAccountId() == accountId) {
      openInAccount(activity, accountId, username, 0); // already the account on screen (this page belongs to another slot)
      return true;
    }
    manager.changePreferredAccountId(accountId, TdlibManager.SWITCH_REASON_USER_CLICK, success -> {
      if (success) {
        openInAccount(activity, accountId, username, 0);
      } else {
        UI.showToast(Lang.getString(R.string.XgramLogsTestingLogIn, username), Toast.LENGTH_SHORT);
      }
    });
    return true;
  }

  /** After an account switch the account's chat list comes up: once it is on screen, opens the chat there. */
  private static void openInAccount (@NonNull BaseActivity activity, int accountId, @NonNull String username, int attempt) {
    UI.post(() -> {
      if (activity.isFinishing()) {
        return;
      }
      ViewController<?> top = UI.getCurrentStackItem(activity);
      if (top != null && top.tdlib() != null && top.tdlib().id() == accountId && top.tdlib().isAuthorized()) {
        top.tdlib().ui().openPublicChat(top, username, null);
      } else if (attempt < 10) {
        openInAccount(activity, accountId, username, attempt + 1);
      }
    }, attempt == 0 ? 0 : 150);
  }

  // Share logs

  private static final String SHARE_DIR = "xgram_logs_share";
  private static final long MAX_APP_FILE = 2L * 1024 * 1024, MAX_APP_TOTAL = 6L * 1024 * 1024;
  private static final long MAX_CRASH_FILE = 256 * 1024, MAX_TDLIB_FILE = 8L * 1024 * 1024;
  private static final int MAX_TRACE_BYTES = 512 * 1024;
  private static final int MAX_CRASH_FILES = 5, MAX_STORED_CRASHES = 3, MAX_EXITS = 10;
  private static final int MAX_SHARE_TEXT_CHARS = 100_000;

  private static final AtomicBoolean sharing = new AtomicBoolean();

  /** The privacy note, then {@link #share}. Main thread. */
  public static void confirmAndShare (@NonNull ViewController<?> c) {
    String text = Lang.getString(R.string.XgramLogsShareConfirmText);
    boolean withTdlibLog;
    try {
      withTdlibLog = willAddTdlibLog();
    } catch (Throwable t) {
      withTdlibLog = true; // say so rather than not
    }
    if (withTdlibLog) {
      text = text + "\n\n" + Lang.getString(R.string.XgramLogsShareConfirmTdlib);
    }
    c.xgramShowConfirm(
      text,
      Lang.getString(R.string.XgramLogsShareConfirmTitle),
      null,
      new int[] {R.id.btn_xgramLogsShare, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramLogsShareAction), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_share_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramLogsShare) {
          share(c);
        }
        return true;
      }
    );
  }

  /**
   * Makes the .zip on a background thread and opens the share sheet with it. Main thread; a second tap while the file
   * is being made does nothing. Files made earlier (over 10 minutes ago) are deleted first.
   */
  public static void share (@NonNull ViewController<?> c) {
    if (!sharing.compareAndSet(false, true)) {
      return;
    }
    String head;
    try {
      head = summary(c); // main thread: the look and the panes are read where they are set
    } catch (Throwable t) {
      head = "XGram " + BuildConfig.VERSION_NAME + "\n(the summary failed: " + t + ")\n";
    }
    final String summary = head;
    UI.showToast(R.string.XgramLogsPreparing, Toast.LENGTH_SHORT);
    Thread thread = new Thread(() -> {
      File file = null;
      String fallback = null;
      try {
        flush();
        file = buildZip(summary);
      } catch (Throwable t) {
        android.util.Log.e(Log.LOG_TAG, "XGram: cannot make the log file", t);
        fallback = textFallback(summary, t);
      }
      final File result = file;
      final String text = fallback;
      UI.post(() -> {
        sharing.set(false);
        if (result != null) {
          if (!openShareSheet(result)) {
            UI.showToast(R.string.XgramLogsShareFailed, Toast.LENGTH_SHORT);
          }
        } else if (text != null) {
          // No file (the storage is full, most of the time): the summary and the crashes as text
          org.thunderdog.challegram.tool.Intents.shareText(text);
        } else {
          UI.showToast(R.string.XgramLogsShareFailed, Toast.LENGTH_SHORT);
        }
      });
    }, "XgramLogsShare");
    thread.setPriority(Thread.MIN_PRIORITY);
    try {
      thread.start();
    } catch (Throwable t) {
      sharing.set(false);
      UI.showToast(R.string.XgramLogsShareFailed, Toast.LENGTH_SHORT);
    }
  }

  /** What Share logs sends when the .zip cannot be made. Short enough for an Intent (no TransactionTooLargeException). */
  private static @Nullable String textFallback (@NonNull String summary, @NonNull Throwable error) {
    try {
      StringBuilder b = new StringBuilder(summary);
      b.append("\n[Files]\nThe log file couldn't be made (").append(error.getClass().getSimpleName()).append("): only this text.\n");
      String stored = storedCrashes();
      if (stored != null) {
        b.append("\n[Crashes]\n").append(redact(stored));
      }
      if (b.length() > MAX_SHARE_TEXT_CHARS) {
        b.setLength(MAX_SHARE_TEXT_CHARS);
        b.append("…");
      }
      return b.toString();
    } catch (Throwable t) {
      return null;
    }
  }

  private static boolean openShareSheet (@NonNull File file) {
    Uri uri;
    try {
      uri = org.thunderdog.challegram.FileProvider.getUriForFile(AppContext.get(), Config.FILE_PROVIDER_AUTHORITY, file);
    } catch (Throwable t) {
      return false;
    }
    if (uri == null) {
      return false;
    }
    String subject = Lang.getString(R.string.XgramLogsShareSubject, BuildConfig.VERSION_NAME);
    Intent send = new Intent(Intent.ACTION_SEND)
      .setType("application/zip")
      .putExtra(Intent.EXTRA_STREAM, uri)
      .putExtra(Intent.EXTRA_SUBJECT, subject)
      .putExtra(Intent.EXTRA_TEXT, subject);
    send.setClipData(ClipData.newRawUri(null, uri));
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    Intent chooser = Intent.createChooser(send, Lang.getString(R.string.XgramLogsShare));
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try {
      UI.startActivity(chooser);
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  // The summary (main thread)

  private static String summary (@NonNull ViewController<?> c) {
    Context context = AppContext.get();
    StringBuilder b = new StringBuilder(4096);
    b.append("XGram logs, made ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n');
    b.append("Shared by the user from the app. XGram uploads nothing by itself.\n");

    b.append("\n[App]\n");
    row(b, "Version", BuildConfig.VERSION_NAME);
    row(b, "Build", buildKind());
    row(b, "Commit", BuildConfig.COMMIT);
    row(b, "Package", context.getPackageName());

    b.append("\n[Device]\n");
    row(b, "Android", SdkVersion.getPrettyName() + " (API " + Build.VERSION.SDK_INT + ")");
    row(b, "Model", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
    row(b, "Screen", Screen.widestSide() + "x" + Screen.smallestSide() + ", density " + Screen.density());

    b.append("\n[Look]\n");
    row(b, "Expressive design", onOff(XgramExpressive.isEnabled()));
    row(b, "Two panes", twoPanes(c));
    row(b, "Dark theme", onOff(Theme.isDark()));
    row(b, "Pure black", onOff(XgramAmoled.isEnabled()));
    row(b, "Material You", onOff(XgramMaterialYou.isEnabled()));
    row(b, "Language", Lang.packId() + (Lang.rtl() ? ", right to left" : ""));

    b.append("\n[Notifications]\n");
    try {
      notifications(b, context);
    } catch (Throwable t) {
      row(b, "Error", t.toString());
    }

    b.append("\n[Logs]\n");
    long until = detailedUntil();
    row(b, "Detailed logs", until != 0 ? "on until " + dateTime(until) : "off");
    row(b, "App log level", levelName(Log.getLogLevel()));
    row(b, "App log tags", logTags());
    row(b, "TDLib log level", Integer.toString(Settings.instance().getTdlibLogSettings().getVerbosity(null)));
    row(b, "Safety log", "errors and warnings, always on");

    b.append("\n[Report details]\n").append(U.getUsefulMetadata(c.tdlib())).append('\n');
    return b.toString();
  }

  private static void row (StringBuilder b, String name, @Nullable String value) {
    b.append(name).append(": ").append(StringUtils.isEmpty(value) ? "-" : value).append('\n');
  }

  private static String onOff (boolean on) {
    return on ? "on" : "off";
  }

  private static String yesNo (boolean yes) {
    return yes ? "yes" : "no";
  }

  private static String dateTime (long ms) {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(ms));
  }

  private static String buildKind () {
    if (BuildConfig.DEBUG) {
      return "debug";
    }
    switch (XgramUpdates.installedKind()) {
      case 1: return "Stable";
      case 0: return "CI build";
      default: return "not known yet (no update check since it was installed)";
    }
  }

  private static String twoPanes (@NonNull ViewController<?> c) {
    if (!XgramSettings.instance().twoPaneLayout()) {
      return "off";
    }
    BaseActivity activity = c.context();
    XgramTwoPane panes = activity != null ? activity.xgramTwoPane() : null;
    return panes != null && panes.isActive() ? "on, shown" : "on, not shown (the window is too narrow)";
  }

  private static String logTags () {
    StringBuilder b = new StringBuilder();
    for (int tag : Log.TAGS) {
      if (Log.isEnabled(tag)) {
        if (b.length() > 0) {
          b.append(", ");
        }
        b.append(Log.getLogTag(tag));
      }
    }
    return b.length() > 0 ? b.toString() : "none";
  }

  private static void notifications (StringBuilder b, Context context) {
    row(b, "Allowed by Android", yesNo(NotificationManagerCompat.from(context).areNotificationsEnabled()));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
      if (power != null) {
        row(b, "Battery optimization", power.isIgnoringBatteryOptimizations(context.getPackageName()) ? "off for XGram" : "on");
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      ActivityManager activities = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      if (activities != null) {
        row(b, "Background restricted", yesNo(activities.isBackgroundRestricted()));
      }
    }
    TdlibManager manager = TdlibManager.instance();
    String token;
    switch (manager.getTokenState()) {
      case TdlibManager.TokenState.OK:
        token = "OK";
        break;
      case TdlibManager.TokenState.ERROR: {
        String error = manager.getTokenError();
        token = StringUtils.isEmpty(error) ? "error" : "error: " + error;
        break;
      }
      case TdlibManager.TokenState.INITIALIZING:
        token = "being made";
        break;
      case TdlibManager.TokenState.NONE:
      default:
        token = "none";
        break;
    }
    row(b, "Push token", token);
    String service;
    try {
      service = DeviceTokenRetrieverInstance.get().getConfiguration();
    } catch (Throwable t) {
      service = null;
    }
    row(b, "Push service", StringUtils.isEmpty(service) ? "unavailable" : service);
    Settings settings = Settings.instance();
    row(b, "Pushes received", settings.getPushMessageStats().trim());
    long last = settings.getLastReceivedPushMessageReceivedTime();
    row(b, "Last push", last != 0 ? dateTime(last) : "none yet");
  }

  // The file (background thread)

  /** One entry of the .zip: the end of a file (at most maxBytes), or bytes made here. */
  private static final class Part {
    final String name;
    final @Nullable File file;
    final long maxBytes;
    final @Nullable byte[] data;

    private Part (String name, @Nullable File file, long maxBytes, @Nullable byte[] data) {
      this.name = name;
      this.file = file;
      this.maxBytes = maxBytes;
      this.data = data;
    }

    static Part ofFile (String name, File file, long maxBytes) {
      return new Part(name, file, maxBytes, null);
    }

    static Part ofText (String name, String text) {
      return new Part(name, null, 0, text.getBytes(StandardCharsets.UTF_8));
    }

    static Part ofBytes (String name, byte[] data) {
      return new Part(name, null, 0, data);
    }

    long size () {
      return data != null ? data.length : file != null ? Math.min(file.length(), maxBytes) : 0;
    }

    boolean isCut () {
      return file != null && file.length() > maxBytes;
    }
  }

  private static File buildZip (String summary) throws IOException {
    Context context = AppContext.get();
    File dir = new File(context.getFilesDir(), SHARE_DIR);
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("Cannot create " + dir);
    }
    // Earlier files: gone, unless just made (an app may still be reading one it was handed)
    File[] old = dir.listFiles();
    if (old != null) {
      long keepAfter = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
      for (File file : old) {
        if (file.lastModified() < keepAfter) {
          //noinspection ResultOfMethodCallIgnored
          file.delete();
        }
      }
    }

    List<Part> parts = new ArrayList<>();
    // The safety log, Telegram X's app logs and crash files (files/logs), newest first
    Log.LogFiles logFiles = Log.getLogFiles();
    if (logFiles != null) {
      int crashes = 0;
      long appLeft = MAX_APP_TOTAL;
      for (File file : logFiles.files) {
        if (!file.isFile() || file.length() == 0) {
          continue;
        }
        String name = file.getName();
        if (name.startsWith(Log.CRASH_PREFIX)) {
          if (crashes < MAX_CRASH_FILES) {
            String text = readTail(file, MAX_CRASH_FILE);
            if (text != null) {
              crashes++;
              // A crash's message can be a TDLib dump of the message being shown
              parts.add(Part.ofText("crashes/" + name, redact(text)));
            }
          }
        } else if (appLeft > 0) {
          long max = Math.min(MAX_APP_FILE, appLeft);
          parts.add(Part.ofFile("app/" + name, file, max));
          appLeft -= Math.min(file.length(), max);
        }
      }
    }
    // TDLib's log, while TDLib logging is on or when it was written lately (never an old dump again and again); the
    // previous one too when the current one is short (it just started over)
    final TdlibLogChoice tdlibLogs = chooseTdlibLogs();
    if (tdlibLogs.current != null) {
      parts.add(Part.ofFile("tdlib/" + tdlibLogs.current.getName(), tdlibLogs.current, MAX_TDLIB_FILE));
    }
    if (tdlibLogs.old != null) {
      parts.add(Part.ofFile("tdlib/" + tdlibLogs.old.getName(), tdlibLogs.old, MAX_TDLIB_FILE / 2));
    }
    String stored = storedCrashes();
    if (stored != null) {
      parts.add(Part.ofText("crashes/stored.txt", redact(stored)));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      addExitReasons(context, parts);
    }

    StringBuilder b = new StringBuilder(summary);
    b.append("\n[Files]\n");
    if (parts.isEmpty()) {
      b.append("No log files yet: only this summary.\n");
    }
    for (Part part : parts) {
      b.append(part.name).append(", ").append(Math.max(1, (part.size() + 1023) / 1024)).append(" KB");
      if (part.isCut()) {
        b.append(" (its newest part)");
      }
      b.append('\n');
    }
    if (tdlibLogs.leftOutModified != 0) {
      b.append("TDLib log: not added (TDLib logging is off; last written ").append(dateTime(tdlibLogs.leftOutModified)).append(")\n");
    }

    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    File out = new File(dir, "xgram-logs-" + stamp + ".zip");
    byte[] buffer = new byte[64 * 1024];
    boolean ok = false;
    try {
      try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out), 64 * 1024))) {
        putBytes(zip, "summary.txt", b.toString().getBytes(StandardCharsets.UTF_8));
        for (Part part : parts) {
          if (part.data != null) {
            putBytes(zip, part.name, part.data);
          } else if (part.file != null) {
            putTail(zip, part.name, part.file, part.maxBytes, buffer);
          }
        }
      }
      ok = true;
    } finally {
      if (!ok) {
        // A part-written file (the storage is full) would only hold on to the last free space
        //noinspection ResultOfMethodCallIgnored
        out.delete();
      }
    }
    return out;
  }

  /** Which of TDLib's two log files go into the .zip, and when a file left out was last written (0: none left out). */
  private static final class TdlibLogChoice {
    @Nullable File current, old;
    long leftOutModified;
  }

  private static TdlibLogChoice chooseTdlibLogs () {
    TdlibLogChoice choice = new TdlibLogChoice();
    boolean tdlibOn = Settings.instance().getTdlibLogSettings().isEnabled() || isDetailedOn();
    long recent = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
    File current = TdlibManager.getLogFile(false);
    long currentSize = current != null && current.isFile() ? current.length() : 0;
    if (currentSize > 0) {
      if (tdlibOn || current.lastModified() >= recent) {
        choice.current = current;
      } else {
        choice.leftOutModified = current.lastModified();
      }
    }
    if (currentSize < MAX_TDLIB_FILE / 4) {
      File old = TdlibManager.getLogFile(true);
      if (old != null && old.isFile() && old.length() > 0) {
        if (tdlibOn || old.lastModified() >= recent) {
          choice.old = old;
        } else {
          choice.leftOutModified = Math.max(choice.leftOutModified, old.lastModified());
        }
      }
    }
    return choice;
  }

  /** Whether Share logs adds TDLib's log, which holds what people wrote: the privacy note says so then. */
  public static boolean willAddTdlibLog () {
    TdlibLogChoice choice = chooseTdlibLogs();
    return choice.current != null || choice.old != null;
  }

  private static void putBytes (ZipOutputStream zip, String name, byte[] data) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(data);
    zip.closeEntry();
  }

  /** The last maxBytes of file (a log keeps growing at its end). A file gone meanwhile is left out. */
  private static void putTail (ZipOutputStream zip, String name, File file, long maxBytes, byte[] buffer) throws IOException {
    RandomAccessFile in;
    try {
      in = new RandomAccessFile(file, "r");
    } catch (IOException e) {
      return;
    }
    try {
      long length = in.length();
      long skip = Math.max(0, length - maxBytes);
      ZipEntry entry = new ZipEntry(name);
      entry.setTime(file.lastModified());
      zip.putNextEntry(entry);
      if (skip > 0) {
        in.seek(skip);
        zip.write(("[" + skip + " older bytes left out]\n").getBytes(StandardCharsets.UTF_8));
      }
      long left = length - skip;
      while (left > 0) {
        int read = in.read(buffer, 0, (int) Math.min(buffer.length, left));
        if (read <= 0) {
          break;
        }
        zip.write(buffer, 0, read);
        left -= read;
      }
      zip.closeEntry();
    } finally {
      in.close();
    }
  }

  /** The last maxBytes of a text file, or null when it cannot be read. */
  private static @Nullable String readTail (File file, long maxBytes) {
    try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
      long length = in.length();
      long skip = Math.max(0, length - maxBytes);
      byte[] data = new byte[(int) (length - skip)];
      in.seek(skip);
      in.readFully(data);
      String text = new String(data, StandardCharsets.UTF_8);
      return skip > 0 ? "[" + skip + " older bytes left out]\n" + text : text;
    } catch (Throwable t) {
      return null;
    }
  }

  /** Crashes Telegram X keeps in its settings (TDLib errors, launch failures), newest first. */
  private static @Nullable String storedCrashes () {
    List<Crash> crashes = Settings.instance().xgramRecentCrashes(MAX_STORED_CRASHES);
    if (crashes.isEmpty()) {
      return null;
    }
    StringBuilder b = new StringBuilder();
    for (Crash crash : crashes) {
      b.append('#').append(crash.id).append(' ').append(crashTypeName(crash.getType()))
        .append(", ").append(crash.date != 0 ? dateTime(crash.date) : "no date")
        .append(", after ").append(TimeUnit.MILLISECONDS.toSeconds(crash.uptime)).append(" s")
        .append(", app ").append(crash.appVersionCode)
        .append(", API ").append(crash.sdkVersion)
        .append('\n');
      if (!StringUtils.isEmpty(crash.message)) {
        b.append(crash.message.trim()).append('\n');
      }
      b.append('\n');
    }
    return b.toString();
  }

  private static String crashTypeName (@Crash.Type int type) {
    switch (type) {
      case Crash.Type.TDLIB: return "TDLib error";
      case Crash.Type.DISK_FULL: return "disk full";
      case Crash.Type.TDLIB_EXTERNAL_ERROR: return "TDLib external error";
      case Crash.Type.TDLIB_DATABASE_BROKEN: return "database broken";
      case Crash.Type.TDLIB_INITIALIZATION_FAILURE: return "TDLib start failed";
      case Crash.Type.UNCAUGHT_EXCEPTION: return "uncaught exception";
      case Crash.Type.UNKNOWN:
      default:
        return "unknown";
    }
  }

  /** Android's record of how XGram's processes ended (crashes, native crashes, ANRs, kills), with the newest ANR trace. */
  @RequiresApi(Build.VERSION_CODES.R)
  private static void addExitReasons (Context context, List<Part> parts) {
    try {
      ActivityManager activities = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      if (activities == null) {
        return;
      }
      List<ApplicationExitInfo> exits = activities.getHistoricalProcessExitReasons(null, 0, MAX_EXITS);
      if (exits == null || exits.isEmpty()) {
        return;
      }
      String packageName = context.getPackageName();
      StringBuilder b = new StringBuilder("How XGram's processes ended, newest first (from Android)\n\n");
      Part trace = null;
      for (ApplicationExitInfo exit : exits) {
        b.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(exit.getTimestamp())))
          .append("  ").append(exitReasonName(exit.getReason()))
          .append("  status ").append(exit.getStatus())
          .append("  importance ").append(exit.getImportance())
          .append("  pss ").append(exit.getPss()).append(" KB");
        String processName = exit.getProcessName();
        if (processName != null && !processName.equals(packageName)) {
          b.append("  ").append(processName);
        }
        String description = exit.getDescription();
        if (!StringUtils.isEmpty(description)) {
          b.append("\n  ").append(description);
        }
        b.append('\n');
        if (trace == null && exit.getReason() == 6 /*REASON_ANR*/) {
          byte[] data = readTrace(exit);
          if (data != null && data.length > 0) {
            trace = Part.ofBytes("exits/anr-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(exit.getTimestamp())) + ".txt", data);
          }
        }
      }
      parts.add(Part.ofText("exits/reasons.txt", b.toString()));
      if (trace != null) {
        parts.add(trace);
      }
    } catch (Throwable ignored) { }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private static @Nullable byte[] readTrace (ApplicationExitInfo exit) {
    try (InputStream in = exit.getTraceInputStream()) {
      if (in == null) {
        return null;
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[16 * 1024];
      int read;
      while (out.size() < MAX_TRACE_BYTES && (read = in.read(buffer)) > 0) {
        out.write(buffer, 0, Math.min(read, MAX_TRACE_BYTES - out.size()));
      }
      return out.toByteArray();
    } catch (Throwable t) {
      return null;
    }
  }

  private static String exitReasonName (int reason) {
    switch (reason) {
      case 1: return "exited by itself";
      case 2: return "signaled";
      case 3: return "low memory";
      case 4: return "CRASH";
      case 5: return "NATIVE CRASH";
      case 6: return "ANR";
      case 7: return "start failed";
      case 8: return "permission changed";
      case 9: return "too much resource use";
      case 10: return "user requested";
      case 11: return "user stopped";
      case 12: return "dependency died";
      case 13: return "other";
      case 14: return "freezer";
      case 15: return "package state changed";
      case 16: return "package updated";
      case 0:
      default:
        return "unknown (" + reason + ")";
    }
  }
}
