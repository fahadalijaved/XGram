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
package com.fahadali.xgram.checkin;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import tgx.td.ChatId;

/**
 * Check In: you tell a chat when you expect to be back, and XGram schedules an alert there (a scheduled Telegram
 * message) that Telegram sends unless you end the Check In in time. The alert carries your last known location and
 * battery level and is refreshed while it waits. One Check In per device.
 *
 * Everything that changes a Check In runs on one engine thread ("XgramCheckIn"): the record (saved before every network
 * action), every TDLib call and the reaction to its result. TDLib updates, the UI, the service, alarms and the worker
 * post to it. The server is the source of truth: each reconcile re-reads the chat's scheduled messages.
 */
public final class XgramCheckIn {
  private XgramCheckIn () { }

  static final String TAG = "XgramCheckIn";

  static void log (String format, Object... args) {
    try {
      Log.i(TAG, args.length == 0 ? format : String.format(Locale.US, format, args));
    } catch (Throwable ignored) { }
  }

  // Engine thread

  private static HandlerThread thread;
  private static Handler handler;

  private static synchronized Handler engine () {
    if (handler == null) {
      thread = new HandlerThread("XgramCheckIn");
      thread.start();
      handler = new Handler(thread.getLooper());
    }
    return handler;
  }

  static boolean isOwnThread () {
    Handler h = handler;
    return h != null && Looper.myLooper() == h.getLooper();
  }

  private static Runnable guarded (Runnable r) {
    return () -> {
      try {
        r.run();
      } catch (Throwable t) {
        Log.e(TAG, "Check In engine error", t);
      }
    };
  }

  static void post (Runnable r) {
    engine().post(guarded(r));
  }

  private static void postDelayed (Runnable r, long delayMs) {
    engine().postDelayed(guarded(r), Math.max(0, delayMs));
  }

  // Sends tagged as Check In's own (ghost mode and Undo Send leave them alone)

  private static final int SENDING_ID_BASE = 0x5C1C0000;
  private static int sendingSeq;

  static synchronized int nextSendingId () {
    sendingSeq = (sendingSeq + 1) & 0xFFFF;
    return SENDING_ID_BASE | sendingSeq;
  }

  /** Whether a send is one of Check In's: it must never be silenced, delayed, scheduled or held. Any thread. */
  public static boolean isOwnSend (@Nullable TdApi.MessageSendOptions options) {
    return options != null && (options.sendingId & 0xFFFF0000) == SENDING_ID_BASE;
  }

  private static TdApi.MessageSendOptions ownOptions (@Nullable TdApi.MessageSchedulingState schedulingState) {
    return new TdApi.MessageSendOptions(null, false, !UI.isResumed(), false, false, 0, false, schedulingState, 0, nextSendingId(), false);
  }

  /**
   * Check In's sends still pending, as acc_chat_pendingId: a sent message no longer carries the sendingId, so ghost
   * mode's read on send asks here (its messages never read the chat).
   */
  private static final Set<String> ownPending = ConcurrentHashMap.newKeySet();

  private static String ownPendingKey (int acc, long chatId, long messageId) {
    return acc + "_" + chatId + "_" + messageId;
  }

  /** Remembers a pending send of Check In's. TDLib thread, in the send's result handler, so before its send update. */
  private static void rememberOwnSend (Tdlib t, @Nullable TdApi.Object result) {
    if (result instanceof TdApi.Message) {
      TdApi.Message m = (TdApi.Message) result;
      if (m.sendingState instanceof TdApi.MessageSendingStatePending) {
        ownPending.add(ownPendingKey(t.id(), m.chatId, m.id));
      }
    }
  }

  /** Whether the message sent from pending {@code oldMessageId} was one of Check In's; it is forgotten. Any thread. */
  public static boolean takeOwnSend (Tdlib t, long chatId, long oldMessageId) {
    return !ownPending.isEmpty() && ownPending.remove(ownPendingKey(t.id(), chatId, oldMessageId));
  }

  // Published state, read by any thread

  private static final Object SNAPSHOT_LOCK = new Object();
  private static volatile boolean snapshotReady;
  private static volatile CheckInRecord published;
  private static volatile CheckInRecord[] publishedWatches = new CheckInRecord[0];
  /** Alerts of Check Ins that failed to start: deleted if the server confirms them anyway. */
  private static final Set<String> failedRefs = ConcurrentHashMap.newKeySet();

  static volatile boolean locationPaused, locationServicesOff;
  static volatile float distanceToDest = -1;
  private static final Map<Integer, Boolean> offline = new ConcurrentHashMap<>();

  private static void ensureSnapshot () {
    if (snapshotReady) {
      return;
    }
    synchronized (SNAPSHOT_LOCK) {
      if (snapshotReady) {
        return;
      }
      CheckInRecord active = null;
      List<CheckInRecord> w = new ArrayList<>();
      try {
        for (CheckInRecord r : CheckInStore.loadAll()) {
          if (r.isActive()) {
            if (active == null || r.created > active.created) {
              active = r;
            }
          } else {
            w.add(r);
          }
        }
      } catch (Throwable t) {
        Log.e(TAG, "Cannot read Check Ins", t);
      }
      published = active;
      publishedWatches = w.toArray(new CheckInRecord[0]);
      snapshotReady = true;
    }
  }

  /** The active Check In (a copy that must not be changed), or null. Any thread. */
  @Nullable
  static CheckInRecord activeSnapshot () {
    ensureSnapshot();
    return published;
  }

  public static boolean isActiveIn (Tdlib tdlib, long chatId) {
    CheckInRecord r = activeSnapshot();
    return r != null && r.acc == tdlib.id() && r.chat == chatId;
  }

  private static boolean hasRecordFor (int acc) {
    ensureSnapshot();
    CheckInRecord r = published;
    if (r != null && r.acc == acc) {
      return true;
    }
    for (CheckInRecord w : publishedWatches) {
      if (w.acc == acc) {
        return true;
      }
    }
    return false;
  }

  // Engine state (engine thread only)

  private static boolean loaded;
  private static @Nullable CheckInRecord current;
  private static final Map<Integer, CheckInRecord> watches = new HashMap<>();
  private static @Nullable CheckInRules.Fix lastFix;
  private static int battery = -1;
  private static boolean charging;
  private static long insideSince;
  private static boolean heldEdit;
  /** The held edit carries new times (Add time): it goes out even when nothing moved. */
  private static boolean forcedEdit;
  /** Rescheduled by Add time: the alert's id (0: none) changed, edits wait for the scheduled list to give the new one. */
  private static long staleAlertId, staleSince;
  private static long editInFlightSince;
  private static @Nullable StartCallback startCallback;
  private static @Nullable String startCallbackRef;
  private static final Map<Integer, Long> lastConnectedReconcile = new HashMap<>();
  private static final SecureRandom random = new SecureRandom();

  private static void ensureLoaded () {
    if (loaded) {
      return;
    }
    loaded = true;
    CheckInRecord active = null;
    for (CheckInRecord r : CheckInStore.loadAll()) {
      if (r.isActive()) {
        if (active == null || r.created > active.created) {
          active = r;
        }
      } else {
        watches.put(r.acc, r);
      }
    }
    current = active;
    publish();
    if (BuildConfig.DEBUG) {
      CheckInSelfTest.run();
    }
  }

  private static void publish () {
    synchronized (SNAPSHOT_LOCK) {
      published = current != null ? current.copy() : null;
      List<CheckInRecord> w = new ArrayList<>();
      for (CheckInRecord r : watches.values()) {
        w.add(r.copy());
      }
      publishedWatches = w.toArray(new CheckInRecord[0]);
      snapshotReady = true;
    }
  }

  private static void save (CheckInRecord r) {
    Tdlib t = attachedTdlib(r.acc);
    if (t != null) {
      r.skew = t.currentTime(TimeUnit.SECONDS) - System.currentTimeMillis() / 1000L;
    }
    CheckInStore.save(r);
    if (r.state == CheckInRecord.WATCH) {
      watches.put(r.acc, r);
    }
    publish();
  }

  /** Server time: from TDLib when the account is loaded, else from the clock difference saved with the record. */
  private static long now (CheckInRecord r) {
    Tdlib t = attachedTdlib(r.acc);
    return t != null ? t.currentTime(TimeUnit.SECONDS) : r.nowFromDevice();
  }

  // Accounts

  private static final Map<Integer, Tdlib> attached = new ConcurrentHashMap<>();
  /** TdlibListeners keeps weak references: the listeners are held here. */
  private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private static final AtomicBoolean uiListenerAdded = new AtomicBoolean();
  private static final UI.StateListener uiListener = newState -> {
    if (newState == UI.State.RESUMED) {
      post(XgramCheckIn::onUiResumed);
    }
  };

  /**
   * Called for every account from the Tdlib constructor: subscribes to its updates and nothing more (no waiting, no
   * references). The first connection state of an account with a Check In resumes it.
   */
  public static void attach (Tdlib tdlib) {
    try {
      Listener listener = new Listener(tdlib);
      listeners.add(listener);
      attached.put(tdlib.id(), tdlib);
      tdlib.listeners().subscribeForGlobalUpdates(listener);
      if (uiListenerAdded.compareAndSet(false, true)) {
        UI.post(() -> UI.addStateListener(uiListener));
      }
    } catch (Throwable t) {
      Log.e(TAG, "Cannot attach Check In", t);
    }
  }

  @Nullable
  private static Tdlib attachedTdlib (int acc) {
    return attached.get(acc);
  }

  /** The account's Tdlib, created if needed; null when the account slot is gone or logged out. */
  @Nullable
  static Tdlib tdlibFor (int acc) {
    try {
      TdlibManager manager = TdlibManager.instance();
      if (!manager.hasAccount(acc)) {
        return null;
      }
      TdlibAccount account = manager.account(acc);
      if (account.isUnauthorized()) {
        return null;
      }
      return account.tdlib();
    } catch (Throwable t) {
      return null;
    }
  }

  // Availability

  /** Whether the chat's ⋮ menu offers Check In: chats with people, groups and supergroups that can hold scheduled messages. */
  public static boolean canStart (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null) {
      return false;
    }
    try {
      long chatId = chat.id;
      if (ChatId.isSecret(chatId) || tdlib.isSelfChat(chatId) || tdlib.isServiceNotificationsChat(chatId) || tdlib.isRepliesChat(chatId)) {
        return false;
      }
      if (ChatId.isPrivate(chatId)) {
        if (tdlib.isBotChat(chatId) || tdlib.isDeletedAccountChat(chatId)) {
          return false;
        }
      } else if (ChatId.isSupergroup(chatId)) {
        if (tdlib.isChannel(chatId) || tdlib.isDirectMessagesChat(chatId)) {
          return false;
        }
        TdApi.Supergroup supergroup = tdlib.chatToSupergroup(chatId);
        if (supergroup != null && supergroup.paidMessageStarCount > 0) {
          TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
          if (status == null || !TD.isAdmin(status)) {
            return false;
          }
        }
      } else if (!ChatId.isBasicGroup(chatId)) {
        return false;
      }
      return tdlib.canSendBasicMessage(chat);
    } catch (Throwable t) {
      return false;
    }
  }

  // Starting

  /** What the start sheet decided. Times are server seconds. */
  static final class Request {
    Tdlib tdlib;
    long chatId;
    int topic;
    String title = "";
    long eta, grace;
    boolean loc, bat, follow, debug;
    boolean hasDest;
    double destLat, destLng;
    int destRadius;
    String destLabel = "";
    @Nullable Location lastKnown;
    boolean coarse;
    int battery = -1;
    boolean charging;
  }

  /** Main thread. */
  interface StartCallback {
    void onStarted (long eta);
    void onFailed (String reason);
  }

  private static void deliverStarted (CheckInRecord r) {
    final StartCallback cb = startCallback;
    if (cb != null && r.ref.equals(startCallbackRef)) {
      startCallback = null;
      startCallbackRef = null;
      final long eta = r.eta;
      UI.post(() -> cb.onStarted(eta));
    }
  }

  private static boolean deliverFailed (CheckInRecord r, String reason) {
    final StartCallback cb = startCallback;
    if (cb != null && r.ref.equals(startCallbackRef)) {
      startCallback = null;
      startCallbackRef = null;
      UI.post(() -> cb.onFailed(reason));
      return true;
    }
    return false;
  }

  static void start (Request req, StartCallback callback) {
    post(() -> startNow(req, callback));
  }

  private static void startNow (Request req, StartCallback callback) {
    ensureLoaded();
    Tdlib t = req.tdlib;
    if (current != null) {
      final String conflict = Lang.getString(R.string.XgramCheckInConflict, current.title);
      UI.post(() -> callback.onFailed(conflict));
      return;
    }
    if (!t.isConnected()) {
      UI.post(() -> callback.onFailed(Lang.getString(R.string.XgramCheckInNeedsConnection)));
      return;
    }
    long now = t.currentTime(TimeUnit.SECONDS);
    long eta = CheckInRules.clampEta(req.eta, now, now, req.debug);
    long sendAt = eta + req.grace;
    if (!CheckInRules.canSchedule(sendAt, now)) {
      UI.post(() -> callback.onFailed(Lang.getString(R.string.XgramCheckInMin15)));
      return;
    }
    CheckInRecord r = new CheckInRecord();
    r.acc = t.id();
    r.chat = req.chatId;
    r.topic = req.topic;
    r.title = req.title != null ? req.title : "";
    TdApi.User me = t.myUser();
    r.me = me != null && me.firstName != null ? me.firstName : "";
    r.ref = CheckInRules.newRef(random);
    r.created = now;
    r.eta = eta;
    r.grace = req.grace;
    r.sendAt = sendAt;
    r.state = CheckInRecord.STARTING;
    r.loc = req.loc;
    r.bat = req.bat;
    r.follow = req.follow && req.loc;
    if (req.hasDest) {
      r.hasDest = true;
      r.destLat = req.destLat;
      r.destLng = req.destLng;
      r.destRadius = req.destRadius;
      r.destLabel = req.destLabel != null ? req.destLabel : "";
    }
    long skew = now - System.currentTimeMillis() / 1000L;
    if (req.loc && req.lastKnown != null) {
      Location l = req.lastKnown;
      r.lastLat = l.getLatitude();
      r.lastLng = l.getLongitude();
      r.lastAcc = l.hasAccuracy() ? l.getAccuracy() : 0;
      r.lastFixAt = Math.min(now, l.getTime() / 1000L + skew);
      r.coarse = req.coarse;
      lastFix = new CheckInRules.Fix(r.lastLat, r.lastLng, r.lastAcc, r.lastFixAt, r.coarse);
    } else {
      lastFix = null;
    }
    if (req.bat && req.battery >= 0) {
      r.lastBat = req.battery;
      r.lastCharging = req.charging;
      battery = req.battery;
      charging = req.charging;
    }
    setInside(0);
    heldEdit = false;
    forcedEdit = false;
    staleAlertId = 0;
    current = r;
    startCallback = callback;
    startCallbackRef = r.ref;
    save(r); // write-ahead: the record exists before the alert does
    log("start ref=%s acc=%d chat=%d eta=%d sendAt=%d loc=%b bat=%b dest=%b", r.ref, r.acc, r.chat, r.eta, r.sendAt, r.loc, r.bat, r.hasDest);
    sendAlert(t, r);
    XgramCheckInNotifier.showOngoing(r);
    ensureService(r, false);
    XgramCheckInWork.enqueue(r.acc);
    scheduleReconcile(r.acc, 60_000);
  }

  @Nullable
  private static TdApi.MessageTopic topicOf (CheckInRecord r) {
    return r.topic != 0 ? new TdApi.MessageTopicForum(r.topic) : null;
  }

  /** Creates the alert: its own result handler, so the pending message never reaches the open chat (no jump to Scheduled). */
  private static void sendAlert (Tdlib t, CheckInRecord r) {
    long now = t.currentTime(TimeUnit.SECONDS);
    TdApi.SendMessage f = new TdApi.SendMessage(r.chat, topicOf(r), null,
      ownOptions(new TdApi.MessageSchedulingStateSendAtDate((int) r.sendAt, 0)), null,
      CheckInText.input(CheckInText.alert(r, now)));
    final String ref = r.ref;
    log("SendMessage alert ref=%s entities=%b", ref, !r.entitiesOff);
    t.client().send(f, result -> {
      rememberOwnSend(t, result);
      post(() -> onAlertSendResult(t, ref, result));
    });
  }

  private static void onAlertSendResult (Tdlib t, String ref, TdApi.Object result) {
    CheckInRecord r = current;
    if (r == null || !r.ref.equals(ref) || r.state != CheckInRecord.STARTING) {
      return;
    }
    if (result instanceof TdApi.Message) {
      TdApi.Message m = (TdApi.Message) result;
      log("alert pending ref=%s id=%d", ref, m.id);
      if (!r.alertReal) {
        r.alertId = m.id;
        save(r);
      }
      if (m.sendingState == null) {
        onAlertConfirmed(t, r, m);
      }
    } else if (result instanceof TdApi.Error) {
      TdApi.Error e = (TdApi.Error) result;
      log("alert error ref=%s %d %s", ref, e.code, e.message);
      if (retryWithoutEntities(t, r, e)) {
        return;
      }
      failStart(t, r, startError(e), false);
    }
  }

  /**
   * The alert was refused and nothing exists on the server (an error result, or a send that failed): when the refusal
   * may be about the DateTime entities, it is sent once more as plain text. True when it was.
   */
  private static boolean retryWithoutEntities (Tdlib t, CheckInRecord r, @Nullable TdApi.Error e) {
    String message = e != null && e.message != null ? e.message.toUpperCase(Locale.US) : "";
    if (r.entitiesOff || message.contains("SCHEDULE_DATE") || !(message.contains("ENTIT") || message.contains("DATE"))) {
      return false;
    }
    log("alert again without DateTime entities ref=%s", r.ref);
    r.entitiesOff = true;
    r.alertId = 0;
    r.alertReal = false;
    save(r);
    sendAlert(t, r);
    return true;
  }

  private static String startError (TdApi.Error e) {
    String m = e.message != null ? e.message.toUpperCase(Locale.US) : "";
    if (m.contains("SCHEDULE_TOO_MUCH")) {
      return Lang.getString(R.string.XgramCheckInTooMany);
    }
    if (m.contains("FORBIDDEN") || m.contains("RIGHT") || m.contains("PAID") || m.contains("PAYMENT") || m.contains("STAR") || m.contains("CLOSED") || m.contains("PRIVACY") || m.contains("SCHEDULE")) {
      return Lang.getString(R.string.XgramCheckInUnavailable);
    }
    return TD.toErrorString(e);
  }

  private static void onAlertConfirmed (Tdlib t, CheckInRecord r, TdApi.Message m) {
    long now = t.currentTime(TimeUnit.SECONDS);
    r.alertId = m.id;
    r.alertReal = true;
    if (m.schedulingState instanceof TdApi.MessageSchedulingStateSendAtDate) {
      int sendDate = ((TdApi.MessageSchedulingStateSendAtDate) m.schedulingState).sendDate;
      if (sendDate > 0 && sendDate != r.sendAt) {
        r.sendAt = sendDate;
        r.eta = sendDate - r.grace;
      }
    }
    r.state = CheckInRecord.ARMED;
    r.armedAt = now;
    r.missingAt = 0;
    boolean sendStart = !r.startSent;
    r.startSent = true;
    save(r); // startSent before the send: the start message goes out at most once
    log("armed ref=%s alertId=%d sendAt=%d", r.ref, r.alertId, r.sendAt);
    if (sendStart) {
      sendStartMessage(t, r);
    }
    XgramCheckInNotifier.showOngoing(r);
    armAlarms(r);
    ensureService(r, false);
    deliverStarted(r);
  }

  private static void sendStartMessage (Tdlib t, CheckInRecord r) {
    final String ref = r.ref;
    long now = t.currentTime(TimeUnit.SECONDS);
    log("SendMessage start ref=%s", ref);
    t.sendMessage(r.chat, topicOf(r), null, ownOptions(null), CheckInText.input(CheckInText.start(r, now)), m -> {
      rememberOwnSend(t, m);
      post(() -> onStartMessageResult(ref, m));
    });
  }

  private static void onStartMessageResult (String ref, @Nullable TdApi.Message m) {
    CheckInRecord cur = current;
    if (m == null || cur == null || !cur.ref.equals(ref)) {
      log("start message result ref=%s ok=%b", ref, m != null);
      return;
    }
    if (cur.startMsgId == 0 || !cur.startReal) {
      cur.startMsgId = m.id;
      cur.startReal = m.sendingState == null;
      save(cur);
    }
  }

  /** The alert won't exist (or is removed): the Check In ends before it started. */
  private static void failStart (Tdlib t, CheckInRecord r, String reason, boolean deleteAlert) {
    log("start failed ref=%s: %s", r.ref, reason);
    if (r.alertId != 0 && (deleteAlert || !r.alertReal)) {
      failedRefs.add(r.ref);
      long[] ids = {r.alertId};
      t.send(new TdApi.DeleteMessages(r.chat, ids, true), (ok, error) -> { });
    }
    removeActive(r);
    if (!deliverFailed(r, reason)) {
      XgramCheckInNotifier.notice(r, Lang.getString(R.string.XgramCheckInDidntStartTitle), Lang.getString(R.string.XgramCheckInDidntStartText));
    }
  }

  // Updates

  private static void onSendSucceeded (Tdlib t, TdApi.Message m, long oldId) {
    ensureLoaded();
    CheckInRecord r = current;
    if (r == null || r.acc != t.id() || r.chat != m.chatId) {
      return;
    }
    if (r.state == CheckInRecord.STARTING && m.schedulingState != null && ((r.alertId != 0 && oldId == r.alertId) || CheckInRules.isAlertText(CheckInText.textOf(m), r.ref))) {
      onAlertConfirmed(t, r, m);
      return;
    }
    if (r.startMsgId != 0 && oldId == r.startMsgId && !r.startReal) {
      r.startMsgId = m.id;
      r.startReal = true;
      save(r);
    }
  }

  private static void onSendFailed (Tdlib t, TdApi.Message m, long oldId, TdApi.Error error) {
    ensureLoaded();
    CheckInRecord r = current;
    if (r == null || r.acc != t.id() || r.chat != m.chatId || r.state != CheckInRecord.STARTING) {
      return;
    }
    if ((r.alertId != 0 && oldId == r.alertId) || CheckInRules.isAlertText(CheckInText.textOf(m), r.ref)) {
      // The local failed message: removed for this device only.
      t.send(new TdApi.DeleteMessages(r.chat, new long[] {m.id}, false), (ok, e) -> { });
      log("alert send failed ref=%s %s", r.ref, error != null ? error.message : "");
      if (retryWithoutEntities(t, r, error)) {
        return;
      }
      r.alertId = 0;
      failStart(t, r, error != null ? startError(error) : Lang.getString(R.string.XgramCheckInDidntStartText), false);
    }
  }

  /** An outgoing message ending with a ref was sent: an alert went out. */
  private static void onAlertSeen (Tdlib t, TdApi.Message m) {
    ensureLoaded();
    int acc = t.id();
    String text = CheckInText.textOf(m);
    CheckInRecord r = current;
    if (r != null && r.acc == acc && r.chat == m.chatId && CheckInRules.isAlertText(text, r.ref) && m.date >= r.created - 60) {
      log("alert went out ref=%s id=%d", r.ref, m.id);
      if (!r.fired || r.firedMsgId == 0) {
        markFired(t, r, m);
      }
      if (r.state == CheckInRecord.CLOSING) {
        reconcile(acc, null);
      }
      return;
    }
    CheckInRecord w = watches.get(acc);
    if (w != null && w.chat == m.chatId && CheckInRules.isAlertText(text, w.ref) && m.date >= w.created - 60) {
      log("ended Check In's alert went out ref=%s", w.ref);
      sendCorrection(t, w, m);
    }
  }

  private static void onConnectionState (Tdlib t, int state, boolean first) {
    ensureLoaded();
    int acc = t.id();
    if (first) {
      resume(acc, false);
    }
    boolean isOffline = state == ConnectionState.WAITING_FOR_NETWORK || state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTING_TO_PROXY;
    Boolean was = offline.put(acc, isOffline);
    CheckInRecord r = current;
    if ((was == null || was != isOffline) && r != null && r.acc == acc) {
      XgramCheckInNotifier.showOngoing(r);
    }
    if (state == ConnectionState.CONNECTED) {
      long uptime = SystemClock.elapsedRealtime();
      Long last = lastConnectedReconcile.get(acc);
      if (last == null || uptime - last >= 30_000) {
        lastConnectedReconcile.put(acc, uptime);
        reconcile(acc, null);
      } else if (heldEdit) {
        maybeEdit(false);
      }
    }
  }

  static boolean isOffline (int acc) {
    Boolean value = offline.get(acc);
    return value != null && value;
  }

  private static boolean uiResumedOnce;

  private static void onUiResumed () {
    ensureLoaded();
    if (!uiResumedOnce) {
      // The first open of the app after a force stop (alarms and notifications gone): everything is set again.
      uiResumedOnce = true;
      resume(-1, false);
      return;
    }
    CheckInRecord r = current;
    if (r != null) {
      ensureService(r, false);
      checkPrompts(r);
      maybeEdit(false);
    }
  }

  // Resuming (process start, boot, app update)

  /** Re-arms everything of the account's Check In ([acc] -1: every account). */
  private static void resume (int acc, boolean fromAction) {
    ensureLoaded();
    CheckInRecord r = current;
    if (r != null && (acc < 0 || r.acc == acc)) {
      if (!TdlibManager.instance().hasAccount(r.acc)) {
        dropAccount(r.acc);
      } else {
        log("resume ref=%s state=%d", r.ref, r.state);
        XgramCheckInNotifier.showOngoing(r);
        armAlarms(r);
        ensureService(r, fromAction);
        checkPrompts(r);
        if (r.state == CheckInRecord.FIRED && !r.firedNotified) {
          r.firedNotified = true;
          save(r);
          XgramCheckInNotifier.fired(r);
        }
      }
    }
    for (CheckInRecord w : new ArrayList<>(watches.values())) {
      if (acc < 0 || w.acc == acc) {
        armWatchAlarm(w);
      }
    }
  }

  /** Boot or an app update (receiver): [done] runs once everything is re-armed and the worker is enqueued. */
  static void onBoot (Runnable done) {
    post(() -> {
      try {
        ensureLoaded();
        resume(-1, false);
        CheckInRecord r = current;
        if (r != null) {
          XgramCheckInWork.enqueue(r.acc);
        }
        for (CheckInRecord w : watches.values()) {
          XgramCheckInWork.enqueue(w.acc);
        }
      } finally {
        done.run();
      }
    });
  }

  // Reconcile: the server says what happened

  private static final class ReconcileState {
    boolean running, again;
    long token;
    final List<Runnable> waiters = new ArrayList<>();
  }

  private static final Map<Integer, ReconcileState> reconciles = new HashMap<>();

  /** Re-reads the scheduled messages of the account's Check In and acts on them; [done] runs on the engine thread. */
  static void reconcile (int acc, @Nullable Runnable done) {
    post(() -> reconcileNow(acc, done));
  }

  private static final Map<Integer, Runnable> scheduledReconciles = new HashMap<>();

  /** A reconcile in [delayMs]; an earlier pending one for the account is replaced. */
  private static void scheduleReconcile (int acc, long delayMs) {
    Handler h = engine();
    Runnable old = scheduledReconciles.remove(acc);
    if (old != null) {
      h.removeCallbacks(old);
    }
    Runnable task = guarded(() -> {
      scheduledReconciles.remove(acc);
      reconcileNow(acc, null);
    });
    scheduledReconciles.put(acc, task);
    h.postDelayed(task, Math.max(0, delayMs));
  }

  private static void reconcileNow (int acc, @Nullable Runnable done) {
    ensureLoaded();
    ReconcileState st = reconciles.get(acc);
    if (st == null) {
      st = new ReconcileState();
      reconciles.put(acc, st);
    }
    if (done != null) {
      st.waiters.add(done);
    }
    if (st.running) {
      st.again = true;
      return;
    }
    st.running = true;
    final ReconcileState state = st;
    final long token = ++st.token;
    final AtomicBoolean finished = new AtomicBoolean();
    Runnable finish = () -> {
      if (!finished.compareAndSet(false, true) || state.token != token) {
        return;
      }
      state.running = false;
      if (state.again) {
        state.again = false;
        reconcileNow(acc, null);
        return;
      }
      List<Runnable> waiters = new ArrayList<>(state.waiters);
      state.waiters.clear();
      for (Runnable w : waiters) {
        try {
          w.run();
        } catch (Throwable t) {
          Log.e(TAG, "Check In waiter", t);
        }
      }
    };
    // A query that never returns (TDLib closed) must not block the next reconcile.
    postDelayed(finish, 60_000);
    runReconcile(acc, finish);
  }

  private static void runReconcile (int acc, Runnable next) {
    CheckInRecord r = current;
    boolean hasActive = r != null && r.acc == acc;
    boolean hasWatch = watches.containsKey(acc);
    if (!hasActive && !hasWatch) {
      next.run();
      return;
    }
    Tdlib t = tdlibFor(acc);
    if (t == null) {
      dropAccount(acc);
      next.run();
      return;
    }
    if (!t.isConnected()) {
      next.run();
      return;
    }
    if (hasActive) {
      reconcileActive(t, r, () -> reconcileWatch(t, acc, next));
    } else {
      reconcileWatch(t, acc, next);
    }
  }

  private static boolean isAccessError (TdApi.Error e) {
    if (e == null) {
      return false;
    }
    String m = e.message != null ? e.message.toUpperCase(Locale.US) : "";
    return e.code == 403 || (e.code == 400 && (m.contains("NOT FOUND") || m.contains("PRIVATE") || m.contains("FORBIDDEN") || m.contains("ACCESS")));
  }

  private static void reconcileActive (Tdlib t, CheckInRecord r, Runnable next) {
    final String ref = r.ref;
    final long alertId = r.alertId;
    log("GetChatScheduledMessages ref=%s", ref);
    t.send(new TdApi.GetChatScheduledMessages(r.chat), (messages, error) -> post(() -> {
      CheckInRecord cur = current;
      if (cur == null || !cur.ref.equals(ref)) {
        next.run();
        return;
      }
      if (error != null) {
        log("scheduled list error ref=%s %d %s", ref, error.code, error.message);
        if (isAccessError(error)) {
          endNoAccess(cur);
        }
        next.run();
        return;
      }
      TdApi.Message found = null;
      if (messages != null && messages.messages != null) {
        for (TdApi.Message m : messages.messages) {
          if (m != null && ((alertId != 0 && m.id == alertId) || CheckInRules.isAlertText(CheckInText.textOf(m), ref))) {
            found = m;
            break;
          }
        }
      }
      onScheduledList(t, cur, found, next);
    }));
  }

  private static void onScheduledList (Tdlib t, CheckInRecord r, @Nullable TdApi.Message found, Runnable next) {
    long now = t.currentTime(TimeUnit.SECONDS);
    log("reconcile ref=%s state=%d listed=%b", r.ref, r.state, found != null);
    switch (r.state) {
      case CheckInRecord.STARTING: {
        if (found != null && found.sendingState == null) {
          onAlertConfirmed(t, r, found);
        } else if (found != null && found.sendingState instanceof TdApi.MessageSendingStatePending) {
          if (now >= r.sendAt - CheckInRules.MIN_LEAD) {
            // Still not on the server this close to its time: it would go out without the Check In running.
            r.alertId = found.id;
            failStart(t, r, Lang.getString(R.string.XgramCheckInDidntStartText), true);
          } else {
            scheduleReconcile(r.acc, 60_000);
          }
        } else if (found != null) {
          failStart(t, r, Lang.getString(R.string.XgramCheckInDidntStartText), true);
        } else if (now - r.created > 120) {
          failStart(t, r, Lang.getString(R.string.XgramCheckInDidntStartText), true);
        } else {
          scheduleReconcile(r.acc, 60_000);
        }
        next.run();
        return;
      }
      case CheckInRecord.ARMED: {
        if (found != null) {
          if (staleAlertId != 0 && found.id == staleAlertId && SystemClock.elapsedRealtime() - staleSince < 60_000 &&
            found.schedulingState instanceof TdApi.MessageSchedulingStateSendAtDate &&
            ((TdApi.MessageSchedulingStateSendAtDate) found.schedulingState).sendDate != r.sendAt) {
            // Still listed as it was before Add time: the list hasn't caught up, it is asked again shortly.
            scheduleReconcile(r.acc, 5_000);
            next.run();
            return;
          }
          staleAlertId = 0;
          r.alertId = found.id;
          if (found.schedulingState instanceof TdApi.MessageSchedulingStateSendAtDate) {
            int sendDate = ((TdApi.MessageSchedulingStateSendAtDate) found.schedulingState).sendDate;
            if (sendDate > 0 && sendDate != r.sendAt) {
              // Rescheduled elsewhere (Telegram X's Reschedule, another device): follow the server.
              log("rescheduled ref=%s %d -> %d", r.ref, r.sendAt, sendDate);
              r.sendAt = sendDate;
              r.eta = sendDate - r.grace;
              if (now < r.eta) {
                r.prompted = false;
              }
              if (now < r.sendAt - CheckInRules.LAST_CALL_LEAD) {
                r.lastCall = false;
              }
              XgramCheckInNotifier.cancelPrompt();
              forcedEdit = true; // the alert's own text says the new back-by time too
              heldEdit = true;
            }
          }
          r.missingAt = 0;
          save(r);
          XgramCheckInNotifier.showOngoing(r);
          armAlarms(r);
          checkPrompts(r);
          ensureService(r, false);
          maybeEdit(false); // a held edit, or a battery-only refresh without the service
          next.run();
          return;
        }
        final String ref = r.ref;
        checkFired(t, r, hit -> {
          CheckInRecord cur = current;
          if (cur == null || !cur.ref.equals(ref) || cur.state != CheckInRecord.ARMED) {
            next.run();
            return;
          }
          long now2 = t.currentTime(TimeUnit.SECONDS);
          switch (CheckInRules.classifyMissing(cur, now2, hit != null)) {
            case CheckInRules.MISSING_FIRED:
            case CheckInRules.MISSING_FIRED_UNCONFIRMED:
              markFired(t, cur, hit);
              break;
            case CheckInRules.MISSING_RECHECK:
              if (cur.missingAt == 0) {
                cur.missingAt = now2;
                save(cur);
              }
              scheduleReconcile(cur.acc, CheckInRules.MISSING_CONFIRM * 1000L + 2000);
              break;
            case CheckInRules.MISSING_DELETED:
              endDeletedElsewhere(cur);
              break;
          }
          next.run();
        });
        return;
      }
      case CheckInRecord.CLOSING: {
        if (found != null && (found.id != r.alertId || r.alertReal != (found.sendingState == null))) {
          // Found by its ref (ended before the start was confirmed, or after the app died): this is the one to delete.
          r.alertId = found.id;
          r.alertReal = found.sendingState == null;
          save(r);
        }
        handleClosing(t, r, found != null, next);
        return;
      }
      case CheckInRecord.FIRED: {
        if (now > r.sendAt + CheckInRules.FIRED_KEEP) {
          log("dropping old fired ref=%s", r.ref);
          removeActive(r);
        } else {
          if (!r.firedNotified) {
            r.firedNotified = true;
            save(r);
            XgramCheckInNotifier.fired(r);
          }
          ensureService(r, false);
          maybeEdit(false);
        }
        next.run();
        return;
      }
      default:
        next.run();
    }
  }

  /** Did the alert go out? The chat history around its time, then a search for its ref. [callback]: engine thread. */
  private static void checkFired (Tdlib t, CheckInRecord r, RunnableMessage callback) {
    final long chatId = r.chat;
    final String ref = r.ref;
    final long minDate = r.created - 60;
    log("history check ref=%s", ref);
    t.send(new TdApi.GetChatMessageByDate(chatId, (int) (r.sendAt + 60)), (byDate, error) -> {
      long from = byDate != null ? byDate.id : 0;
      int offset = byDate != null ? -20 : 0;
      t.send(new TdApi.GetChatHistory(chatId, from, offset, 40, false), (history, error2) -> {
        TdApi.Message hit = history != null ? findSent(history.messages, ref, minDate) : null;
        if (hit != null) {
          post(() -> callback.run(hit));
          return;
        }
        t.send(new TdApi.SearchChatMessages(chatId, null, ref, null, 0, 0, 20, null), (found, error3) -> {
          TdApi.Message hit2 = found != null ? findSent(found.messages, ref, minDate) : null;
          log("history check ref=%s hit=%b", ref, hit2 != null);
          post(() -> callback.run(hit2));
        });
      });
    });
  }

  interface RunnableMessage {
    void run (@Nullable TdApi.Message message);
  }

  @Nullable
  private static TdApi.Message findSent (@Nullable TdApi.Message[] messages, String ref, long minDate) {
    if (messages == null) {
      return null;
    }
    for (TdApi.Message m : messages) {
      if (m != null && m.isOutgoing && m.schedulingState == null && m.date >= minDate && CheckInRules.isAlertText(CheckInText.textOf(m), ref)) {
        return m;
      }
    }
    return null;
  }

  private static void markFired (Tdlib t, CheckInRecord r, @Nullable TdApi.Message hit) {
    r.fired = true;
    if (hit != null) {
      r.firedMsgId = hit.id;
    }
    if (r.state == CheckInRecord.ARMED || r.state == CheckInRecord.STARTING) {
      r.state = CheckInRecord.FIRED;
      r.followEdits = 0;
      r.updatedAt = 0;
    }
    boolean notify = r.state == CheckInRecord.FIRED && !r.firedNotified;
    if (notify) {
      r.firedNotified = true;
    }
    save(r);
    log("fired ref=%s msg=%d state=%d", r.ref, r.firedMsgId, r.state);
    XgramCheckInNotifier.cancelPrompt();
    armAlarms(r);
    XgramCheckInNotifier.showOngoing(r);
    if (notify) {
      XgramCheckInNotifier.fired(r);
    }
    ensureService(r, false);
  }

  // Ending

  /** I'm safe, Cancel or arrival: [kind] is a CheckInRecord.KIND_*. A stale [ref] is ignored. Any thread. */
  static void imSafe (int acc, String ref, int kind) {
    post(() -> imSafeNow(acc, ref, kind));
  }

  private static void imSafeNow (int acc, String ref, int kind) {
    ensureLoaded();
    CheckInRecord r = current;
    if (r == null || r.acc != acc || !r.ref.equals(ref)) {
      log("stale end ignored ref=%s", ref);
      return;
    }
    if (r.state != CheckInRecord.CLOSING) {
      r.state = CheckInRecord.CLOSING;
      r.closeKind = kind;
      r.closeAsk = now(r);
      save(r);
      log("closing ref=%s kind=%d fired=%b", r.ref, kind, r.fired);
    }
    XgramCheckInNotifier.cancelPrompt();
    XgramCheckInNotifier.cancelFired();
    XgramCheckInNotifier.showOngoing(r);
    armAlarms(r);
    ensureService(r, false);
    XgramCheckInWork.enqueue(acc);
    reconcile(acc, null);
  }

  private static void handleClosing (Tdlib t, CheckInRecord r, boolean alertListed, Runnable next) {
    long now = t.currentTime(TimeUnit.SECONDS);
    int decision = CheckInRules.closeDecision(r, now, alertListed);
    log("closing ref=%s decision=%d", r.ref, decision);
    final String ref = r.ref;
    switch (decision) {
      case CheckInRules.CLOSE_FINISH:
        finishClosing(r, false);
        next.run();
        return;
      case CheckInRules.CLOSE_DELETE: {
        if (r.deleteOkAt != 0 && now - r.deleteOkAt < 5) {
          // Deleted a moment ago and still listed: asked again once the list had time to catch up.
          scheduleReconcile(r.acc, 5_000);
          next.run();
          return;
        }
        r.deleteAsked = true;
        r.deleteOkAt = 0;
        save(r);
        if (!r.alertReal) {
          failedRefs.add(ref); // a pending alert the server might still confirm: deleted then too
        }
        long[] ids = {r.alertId};
        com.fahadali.xgram.history.XgramDeleted.noteSelfDeleted(t, r.chat, ids);
        log("DeleteMessages alert ref=%s id=%d", ref, r.alertId);
        final int acc = r.acc;
        t.send(new TdApi.DeleteMessages(r.chat, ids, true), (ok, error) -> post(() -> {
          CheckInRecord cur = current;
          if (cur != null && cur.ref.equals(ref)) {
            if (error == null && t.isConnected()) {
              cur.deleteOkAt = t.currentTime(TimeUnit.SECONDS);
              save(cur);
            }
            log("delete result ref=%s ok=%b", ref, error == null);
          }
          next.run();
          // The decision runs again on what the server lists now; after a failed delete, not in a tight loop.
          scheduleReconcile(acc, error == null ? 1_500 : 30_000);
        }));
        return;
      }
      case CheckInRules.CLOSE_FALSE_ALARM:
        sendClosing(t, r, true);
        next.run();
        return;
      case CheckInRules.CLOSE_NOT_FIRED_FAST:
        sendClosing(t, r, false);
        next.run();
        return;
      case CheckInRules.CLOSE_WAIT: {
        long wait = r.sendAt + CheckInRules.AFTER_ALERT_WAIT - now;
        scheduleReconcile(r.acc, wait * 1000L + 2000);
        next.run();
        return;
      }
      case CheckInRules.CLOSE_CHECK_HISTORY:
      default: {
        checkFired(t, r, hit -> {
          CheckInRecord cur = current;
          if (cur == null || !cur.ref.equals(ref) || cur.state != CheckInRecord.CLOSING || cur.closeSent) {
            next.run();
            return;
          }
          if (hit != null) {
            cur.fired = true;
            cur.firedMsgId = hit.id;
          }
          sendClosing(t, cur, hit != null);
          next.run();
        });
      }
    }
  }

  private static void sendClosing (Tdlib t, CheckInRecord r, boolean fired) {
    long now = t.currentTime(TimeUnit.SECONDS);
    // The alert could still go out (a delete the server applied late): keep watching it for a while.
    boolean watch = !fired && now < r.sendAt + CheckInRules.AFTER_ALERT_WAIT;
    if (!CheckInRules.needsClosingMessage(r)) {
      finishClosing(r, watch && r.alertReal);
      return;
    }
    r.closeSent = true;
    save(r); // saved before the send: at most one closing message
    long replyId = fired && r.firedMsgId != 0 ? r.firedMsgId : r.startReal ? r.startMsgId : 0;
    TdApi.InputMessageText content = CheckInText.input(CheckInText.closing(r, fired));
    sendWithReply(t, r.chat, topicOf(r), replyId, content, "closing " + r.ref);
    finishClosing(r, watch);
  }

  /** Sends a reply; if that fails (the message replied to is gone), once more without the reply. */
  private static void sendWithReply (Tdlib t, long chatId, @Nullable TdApi.MessageTopic topic, long replyId, TdApi.InputMessageContent content, String what) {
    TdApi.InputMessageReplyTo replyTo = replyId != 0 ? new TdApi.InputMessageReplyToMessage(replyId, null, 0, null) : null;
    log("SendMessage %s reply=%d", what, replyId);
    t.sendMessage(chatId, topic, replyTo, ownOptions(null), content, m -> {
      rememberOwnSend(t, m);
      if (m == null && replyTo != null) {
        log("SendMessage %s again without reply", what);
        t.sendMessage(chatId, topic, null, ownOptions(null), content, again -> rememberOwnSend(t, again));
      }
    });
  }

  private static void finishClosing (CheckInRecord r, boolean writeWatch) {
    log("ended ref=%s kind=%d fired=%b watch=%b", r.ref, r.closeKind, r.fired, writeWatch);
    if (writeWatch) {
      CheckInRecord w = r.copy();
      w.state = CheckInRecord.WATCH;
      w.correctionSent = false;
      save(w);
      armWatchAlarm(w);
    }
    removeActive(r);
    if (r.closeKind == CheckInRecord.KIND_ARRIVED) {
      XgramCheckInNotifier.notice(r, Lang.getString(R.string.XgramCheckInArrivedTitle, CheckInText.placeLabel(r)), Lang.getString(R.string.XgramCheckInArrivedText, r.title));
    } else {
      // Also when it was ended from the notification shade: the snackbar in the app, else a toast.
      final String text = Lang.getString(R.string.XgramCheckInEnded);
      UI.post(() -> feedback(text));
    }
  }

  private static void endDeletedElsewhere (CheckInRecord r) {
    log("alert deleted elsewhere ref=%s", r.ref);
    removeActive(r);
    XgramCheckInNotifier.notice(r, Lang.getString(R.string.XgramCheckInEnded), Lang.getString(R.string.XgramCheckInEndedDeleted));
  }

  private static void endNoAccess (CheckInRecord r) {
    log("no access ref=%s", r.ref);
    removeActive(r);
    XgramCheckInNotifier.notice(r, Lang.getString(R.string.XgramCheckInEnded), Lang.getString(R.string.XgramCheckInEndedNoAccess, r.title));
  }

  /** Removes the active record and everything that served it. */
  private static void removeActive (CheckInRecord r) {
    CheckInStore.remove(CheckInStore.activeKey(r.acc));
    if (current != null && current.ref.equals(r.ref)) {
      current = null;
    }
    lastFix = null;
    setInside(0);
    heldEdit = false;
    forcedEdit = false;
    staleAlertId = 0;
    distanceToDest = -1;
    locationPaused = false;
    publish();
    cancelAlarms();
    XgramCheckInNotifier.cancelOngoing();
    XgramCheckInNotifier.cancelPrompt();
    stopService();
  }

  private static void reconcileWatch (Tdlib t, int acc, Runnable next) {
    CheckInRecord w = watches.get(acc);
    if (w == null) {
      next.run();
      return;
    }
    long now = t.currentTime(TimeUnit.SECONDS);
    if (w.correctionSent || now > w.sendAt + CheckInRules.WATCH_KEEP) {
      removeWatch(w);
      next.run();
      return;
    }
    if (now < w.sendAt + CheckInRules.AFTER_ALERT_WAIT) {
      armWatchAlarm(w);
      next.run();
      return;
    }
    final String ref = w.ref;
    checkFired(t, w, hit -> {
      CheckInRecord cur = watches.get(acc);
      if (cur != null && cur.ref.equals(ref)) {
        if (hit != null) {
          sendCorrection(t, cur, hit);
        } else {
          removeWatch(cur);
        }
      }
      next.run();
    });
  }

  private static void sendCorrection (Tdlib t, CheckInRecord w, TdApi.Message alert) {
    if (w.correctionSent) {
      return;
    }
    w.correctionSent = true;
    save(w); // at most one correction
    sendWithReply(t, w.chat, topicOf(w), alert.id, CheckInText.input(CheckInText.correction()), "correction " + w.ref);
    removeWatch(w);
  }

  private static void removeWatch (CheckInRecord w) {
    CheckInStore.remove(CheckInStore.watchKey(w.acc));
    watches.remove(w.acc);
    publish();
    XgramCheckInAlarms.cancelWatch();
  }

  /** The account logged out (TDLib thread, before its keys are erased): tracking stops; the server alert stays. */
  public static void forgetAccount (int acc) {
    final CheckInRecord stored;
    CheckInRecord read = null;
    try {
      read = CheckInStore.load(CheckInStore.activeKey(acc));
    } catch (Throwable ignored) { }
    stored = read;
    post(() -> {
      ensureLoaded();
      CheckInRecord r = current != null && current.acc == acc ? current : stored;
      forget(acc, r);
    });
  }

  /** The worker found the account slot gone or logged out. */
  static void onAccountMissing (int acc) {
    post(() -> {
      ensureLoaded();
      dropAccount(acc);
    });
  }

  private static void dropAccount (int acc) {
    CheckInRecord r = current != null && current.acc == acc ? current : null;
    forget(acc, r);
  }

  private static void forget (int acc, @Nullable CheckInRecord r) {
    if (r != null) {
      log("forget account %d ref=%s", acc, r.ref);
      boolean mayStillGoOut = r.state == CheckInRecord.STARTING || r.state == CheckInRecord.ARMED || (r.state == CheckInRecord.CLOSING && !r.closeSent && !r.fired);
      removeActive(r);
      if (mayStillGoOut && r.alertId != 0) {
        XgramCheckInNotifier.notice(r, Lang.getString(R.string.XgramCheckInEnded), Lang.getString(R.string.XgramCheckInLogoutText, r.title));
      }
    } else {
      CheckInStore.remove(CheckInStore.activeKey(acc));
    }
    CheckInRecord w = watches.get(acc);
    if (w != null) {
      removeWatch(w);
    } else {
      CheckInStore.remove(CheckInStore.watchKey(acc));
    }
    XgramCheckInWork.cancel(acc);
    publish();
  }

  // Add time

  /** Main thread: [ok] false with a reason when refused or failed. */
  interface ResultCallback {
    void onResult (boolean ok, String message);
  }

  static void addTime (int acc, String ref, long seconds, @Nullable ResultCallback callback) {
    post(() -> addTimeNow(acc, ref, seconds, callback));
  }

  private static void reply (@Nullable ResultCallback callback, boolean ok, String message) {
    if (callback != null) {
      UI.post(() -> callback.onResult(ok, message));
    }
  }

  private static void addTimeNow (int acc, String ref, long seconds, @Nullable ResultCallback callback) {
    ensureLoaded();
    CheckInRecord r = current;
    if (r == null || r.acc != acc || !r.ref.equals(ref)) {
      reply(callback, false, "");
      return;
    }
    Tdlib t = tdlibFor(acc);
    boolean connected = t != null && t.isConnected();
    long now = t != null ? t.currentTime(TimeUnit.SECONDS) : r.nowFromDevice();
    switch (CheckInRules.canAddTime(r, now, seconds, connected)) {
      case CheckInRules.ADD_OFFLINE:
        reply(callback, false, Lang.getString(R.string.XgramCheckInAddNeedsConnection));
        return;
      case CheckInRules.ADD_TOO_LATE:
        reply(callback, false, Lang.getString(R.string.XgramCheckInTooLate));
        return;
      case CheckInRules.ADD_TOO_LONG:
        reply(callback, false, Lang.getString(R.string.XgramCheckInMax24h));
        return;
      case CheckInRules.ADD_NOT_ARMED:
        reply(callback, false, "");
        return;
      case CheckInRules.ADD_OK:
      default:
        break;
    }
    final long newSendAt = r.sendAt + seconds;
    log("EditMessageSchedulingState ref=%s %d -> %d", ref, r.sendAt, newSendAt);
    t.send(new TdApi.EditMessageSchedulingState(r.chat, r.alertId, new TdApi.MessageSchedulingStateSendAtDate((int) newSendAt, 0)), (ok, error) -> post(() -> {
      CheckInRecord cur = current;
      if (cur == null || !cur.ref.equals(ref)) {
        reply(callback, false, "");
        return;
      }
      if (error != null) {
        log("add time error ref=%s %s", ref, error.message);
        reply(callback, false, TD.toErrorString(error));
        reconcile(cur.acc, null);
        return;
      }
      cur.eta += seconds;
      cur.sendAt = newSendAt;
      cur.prompted = false;
      cur.lastCall = false;
      save(cur);
      XgramCheckInNotifier.cancelPrompt();
      XgramCheckInNotifier.showOngoing(cur);
      armAlarms(cur);
      // A scheduled message's id carries its send date, so the alert has a new id now: the new times go into it once
      // the scheduled list gave that id (also asked for by the deletion of the old id).
      staleAlertId = cur.alertId;
      staleSince = SystemClock.elapsedRealtime();
      forcedEdit = true;
      heldEdit = true;
      scheduleReconcile(cur.acc, 2_000);
      if (cur.startReal && cur.startMsgId != 0) {
        // The start message says the new times; edited quietly, nothing new is sent.
        log("EditMessageText start ref=%s", ref);
        t.send(new TdApi.EditMessageText(cur.chat, cur.startMsgId, null, CheckInText.input(CheckInText.start(cur, t.currentTime(TimeUnit.SECONDS)))), (m, e) -> { });
      }
      reply(callback, true, Lang.getString(R.string.XgramCheckInBackBy, Lang.time(cur.eta, TimeUnit.SECONDS)));
    }));
  }

  // Refreshing the alert

  /** A new location fix from the service (main thread). */
  static void onFix (Location location, boolean coarse) {
    final double lat = location.getLatitude(), lng = location.getLongitude();
    final float accuracy = location.hasAccuracy() ? location.getAccuracy() : 0;
    final long timeMs = location.getTime();
    post(() -> {
      ensureLoaded();
      CheckInRecord r = current;
      if (r == null) {
        return;
      }
      long now = now(r);
      long time = Math.min(now, timeMs / 1000L + r.skew);
      CheckInRules.Fix fix = new CheckInRules.Fix(lat, lng, accuracy, time, coarse);
      lastFix = fix;
      if (r.hasDest) {
        distanceToDest = (float) CheckInRules.distance(lat, lng, r.destLat, r.destLng);
        if (CheckInRules.isInside(r, fix)) {
          if (insideSince == 0) {
            setInside(fix.time);
            XgramCheckInService.refresh();
          } else if (CheckInRules.arrived(r, fix, insideSince) && (r.state == CheckInRecord.ARMED || r.state == CheckInRecord.FIRED)) {
            log("arrived ref=%s", r.ref);
            imSafeNow(r.acc, r.ref, CheckInRecord.KIND_ARRIVED);
            return;
          }
        } else if (insideSince != 0) {
          setInside(0);
        }
        XgramCheckInNotifier.showOngoing(r);
        XgramCheckInService.refresh(); // updates come faster within 1 km (the request only changes when needed)
      }
      checkPrompts(r);
      maybeEdit(false);
    });
  }

  static void onBattery (int percent, boolean isCharging) {
    post(() -> {
      battery = percent;
      charging = isCharging;
      ensureLoaded();
      if (current != null) {
        maybeEdit(false);
      }
    });
  }

  static void onShutdown () {
    post(() -> {
      ensureLoaded();
      CheckInRecord r = current;
      if (r == null || r.state != CheckInRecord.ARMED) {
        return;
      }
      Tdlib t = attachedTdlib(r.acc);
      if (t != null && t.isConnected() && CheckInRules.shouldEdit(r, t.currentTime(TimeUnit.SECONDS), null, -1, false, true)) {
        // Saved first: the app rarely sees the result, and after the next start the first edit drops the line again.
        r.offAt = t.currentTime(TimeUnit.SECONDS);
        save(r);
        editAlert(t, r, true, true);
      }
    });
  }

  /** The service's 5 minute tick: battery-only refreshes, prompts, the end of the follow-up window. */
  static void onTick () {
    post(() -> {
      ensureLoaded();
      CheckInRecord r = current;
      if (r == null) {
        return;
      }
      checkPrompts(r);
      if (r.state == CheckInRecord.FIRED) {
        ensureService(r, false);
      }
      maybeEdit(false);
      XgramCheckInService.refresh();
    });
  }

  /** Seconds between location updates now (the service asks). */
  static long desiredInterval () {
    CheckInRecord r = published;
    if (r == null) {
      return 300;
    }
    return CheckInRules.interval(r, r.nowFromDevice(), distanceToDest, insideSinceVolatile);
  }

  private static volatile boolean insideSinceVolatile;

  private static void setInside (long since) {
    insideSince = since;
    insideSinceVolatile = since != 0;
  }

  static boolean wantsHighAccuracy () {
    return insideSinceVolatile;
  }

  private static void maybeEdit (boolean shutdown) {
    CheckInRecord r = current;
    if (r == null) {
      return;
    }
    if (r.bat && !XgramCheckInService.isRunning()) {
      // No service (location off or not allowed from here): the battery is read whenever the engine wakes up anyway.
      int[] state = XgramCheckInService.readBattery(AppContext.get());
      if (state != null) {
        battery = state[0];
        charging = state[1] != 0;
      }
    }
    Tdlib t = attachedTdlib(r.acc);
    if (r.state == CheckInRecord.ARMED) {
      long now = now(r);
      if (!forcedEdit && !CheckInRules.shouldEdit(r, now, r.loc ? lastFix : null, r.bat ? battery : -1, charging, shutdown)) {
        return;
      }
      if (t == null || !t.isConnected()) {
        heldEdit = true; // sent on reconnect, never left hanging
        return;
      }
      editAlert(t, r, forcedEdit, shutdown);
    } else if (r.state == CheckInRecord.FIRED) {
      long now = now(r);
      if (!CheckInRules.shouldFollowEdit(r, now, lastFix, r.bat ? battery : -1, charging)) {
        return;
      }
      if (t == null || !t.isConnected()) {
        return;
      }
      editFired(t, r);
    }
  }

  private static boolean editInFlight () {
    return editInFlightSince != 0 && SystemClock.elapsedRealtime() - editInFlightSince < 60_000;
  }

  /**
   * The scheduled alert gets the newest location and battery. [force]: its times changed (Add time), so it is sent even
   * when nothing moved, after the edit in flight or the flood wait if there is one (never past the cutoff).
   */
  private static void editAlert (Tdlib t, CheckInRecord r, boolean force, boolean shutdown) {
    long now = t.currentTime(TimeUnit.SECONDS);
    if (r.state != CheckInRecord.ARMED || !r.alertReal || now >= r.sendAt - CheckInRules.CUTOFF) {
      forcedEdit = false;
      return;
    }
    if (editInFlight() || now < r.editPausedUntil || staleAlertId != 0) {
      heldEdit = true;
      forcedEdit |= force;
      return;
    }
    forcedEdit = false;
    CheckInRecord next = r.copy();
    CheckInRules.Fix fix = lastFix;
    if (r.loc && fix != null && fix.time >= r.lastFixAt) {
      next.lastLat = fix.lat;
      next.lastLng = fix.lng;
      next.lastAcc = fix.accuracy;
      next.lastFixAt = fix.time;
      next.coarse = fix.coarse;
    }
    if (r.bat && battery >= 0) {
      next.lastBat = battery;
      next.lastCharging = charging;
    }
    // "Phone switched off" only while it is: the first edit after it came back on drops the line.
    next.offAt = shutdown ? now : 0;
    heldEdit = false;
    editInFlightSince = SystemClock.elapsedRealtime();
    final String ref = r.ref;
    log("EditMessageText alert ref=%s force=%b shutdown=%b", ref, force, shutdown);
    t.send(new TdApi.EditMessageText(r.chat, r.alertId, null, CheckInText.input(CheckInText.alert(next, now))), (m, error) -> post(() -> {
      if (force && error != null && (error.message == null || !error.message.contains("MESSAGE_NOT_MODIFIED")) && current != null && current.ref.equals(ref)) {
        forcedEdit = true; // the new times still have to reach the alert: tried again after the pause
      }
      onEditResult(t, ref, next, now, error, false);
    }));
  }

  /** The alert that went out gets the newest location (Keep the sent alert updated). */
  private static void editFired (Tdlib t, CheckInRecord r) {
    if (editInFlight() || r.firedMsgId == 0) {
      return;
    }
    long now = t.currentTime(TimeUnit.SECONDS);
    CheckInRecord next = r.copy();
    CheckInRules.Fix fix = lastFix;
    if (fix != null) {
      next.lastLat = fix.lat;
      next.lastLng = fix.lng;
      next.lastAcc = fix.accuracy;
      next.lastFixAt = fix.time;
      next.coarse = fix.coarse;
    }
    if (r.bat && battery >= 0) {
      next.lastBat = battery;
      next.lastCharging = charging;
    }
    next.updatedAt = now;
    next.offAt = 0;
    editInFlightSince = SystemClock.elapsedRealtime();
    final String ref = r.ref;
    log("EditMessageText fired ref=%s", ref);
    t.send(new TdApi.EditMessageText(r.chat, r.firedMsgId, null, CheckInText.input(CheckInText.alert(next, now))), (m, error) -> post(() -> onEditResult(t, ref, next, now, error, true)));
  }

  private static int floodWaitSeconds (TdApi.Error e) {
    if (e == null || e.message == null) {
      return 0;
    }
    if (e.code != 429 && !e.message.contains("FLOOD_WAIT")) {
      return 0;
    }
    int value = 0;
    boolean digits = false;
    String m = e.message;
    for (int i = 0; i < m.length(); i++) {
      char c = m.charAt(i);
      if (c >= '0' && c <= '9') {
        value = value * 10 + (c - '0');
        digits = true;
        if (value > 86400) {
          break;
        }
      } else if (digits && value != 429) {
        break;
      } else if (digits) {
        value = 0;
        digits = false;
      }
    }
    return Math.max(value, 30);
  }

  private static void onEditResult (Tdlib t, String ref, CheckInRecord next, long now, @Nullable TdApi.Error error, boolean follow) {
    editInFlightSince = 0;
    CheckInRecord r = current;
    if (r == null || !r.ref.equals(ref)) {
      return;
    }
    boolean notModified = error != null && error.message != null && error.message.contains("MESSAGE_NOT_MODIFIED");
    if (error == null || notModified) {
      r.lastLat = next.lastLat;
      r.lastLng = next.lastLng;
      r.lastAcc = next.lastAcc;
      r.lastFixAt = next.lastFixAt;
      r.coarse = next.coarse;
      r.lastBat = next.lastBat;
      r.lastCharging = next.lastCharging;
      r.offAt = next.offAt;
      r.updatedAt = next.updatedAt;
      r.lastEditAt = now;
      if (follow) {
        r.followEdits++;
      }
      save(r);
      log("edit ok ref=%s follow=%b", ref, follow);
    } else {
      int wait = floodWaitSeconds(error);
      log("edit error ref=%s %d %s", ref, error.code, error.message);
      if (wait > 0) {
        r.editPausedUntil = now + wait + 5 + random.nextInt(11);
      } else if (follow) {
        // The alert that went out can't be edited (deleted, or too old): a failed try counts, and a gone message ends it.
        String m = error.message != null ? error.message.toUpperCase(Locale.US) : "";
        r.followEdits = m.contains("MESSAGE_ID_INVALID") || m.contains("NOT FOUND") || m.contains("NOT_FOUND") ? CheckInRules.FOLLOW_MAX : r.followEdits + 1;
        r.editPausedUntil = now + CheckInRules.FOLLOW_GAP;
      } else {
        // Never the same refused edit at every fix: the server says what happened, and edits wait a little.
        r.editPausedUntil = now + CheckInRules.MIN_GAP;
        reconcile(r.acc, null);
      }
      save(r);
    }
    if (follow) {
      ensureService(r, false); // the service stops once nothing is left to follow
    } else if (heldEdit) {
      maybeEdit(false);
    }
  }

  // Prompts and alarms

  private static void checkPrompts (CheckInRecord r) {
    long now = now(r);
    // The last call always comes after the back-by time (CheckInRules.hasLastCall), so "Are you OK?" posts first. Both
    // are due at once only after a late wake-up: then the last call (same title, the closer time) stands for both.
    if (CheckInRules.lastCallDue(r, now)) {
      r.lastCall = true;
      r.prompted = true;
      save(r);
      XgramCheckInNotifier.prompt(r, true);
    } else if (CheckInRules.promptDue(r, now)) {
      r.prompted = true;
      save(r);
      XgramCheckInNotifier.prompt(r, false);
    }
  }

  private static void armAlarms (CheckInRecord r) {
    XgramCheckInAlarms.arm(r);
  }

  private static void armWatchAlarm (CheckInRecord w) {
    XgramCheckInAlarms.armWatch(w);
  }

  private static void cancelAlarms () {
    XgramCheckInAlarms.cancelActive();
  }

  /** An alarm or a notification action (receiver): [done] runs once it was handled. */
  static void onAction (String action, int acc, @Nullable String ref, Runnable done) {
    post(() -> {
      boolean finishLater = false;
      try {
        ensureLoaded();
        CheckInRecord r = current;
        boolean matches = r != null && r.acc == acc && ref != null && r.ref.equals(ref);
        switch (action) {
          case XgramCheckInReceiver.ACTION_PROMPT:
          case XgramCheckInReceiver.ACTION_LAST_CALL:
            if (matches) {
              checkPrompts(r);
              armAlarms(r);
            }
            break;
          case XgramCheckInReceiver.ACTION_AFTER:
            if (matches) {
              reconcile(acc, null);
              XgramCheckInWork.enqueue(acc);
            }
            break;
          case XgramCheckInReceiver.ACTION_WATCH:
            if (watches.containsKey(acc)) {
              reconcile(acc, null);
              XgramCheckInWork.enqueue(acc);
            }
            break;
          case XgramCheckInReceiver.ACTION_SAFE:
            if (matches) {
              imSafeNow(acc, ref, CheckInRecord.KIND_SAFE);
            }
            break;
          case XgramCheckInReceiver.ACTION_ADD15:
            if (matches) {
              ensureService(r, true);
              finishLater = addTimeFromNotification(acc, ref, done);
            }
            break;
        }
      } finally {
        if (!finishLater) {
          done.run();
        }
      }
    });
  }

  /**
   * Add 15 min from a notification: the app may have just been started for it, so TDLib is kept awake and the
   * connection is waited for (a few seconds) before the reschedule. True when [done] runs later.
   */
  private static boolean addTimeFromNotification (int acc, String ref, Runnable done) {
    Tdlib t = tdlibFor(acc);
    if (t == null) {
      return false;
    }
    t.changeLocationReferenceCount(1);
    final AtomicBoolean released = new AtomicBoolean();
    Runnable release = () -> {
      if (released.compareAndSet(false, true)) {
        t.changeLocationReferenceCount(-1);
        done.run();
      }
    };
    final AtomicBoolean started = new AtomicBoolean();
    Runnable go = () -> post(() -> {
      if (!started.compareAndSet(false, true)) {
        return;
      }
      addTimeNow(acc, ref, 900, (ok, message) -> {
        // Tapped on a notification, so likely from outside the app: the snackbar, else a toast says what happened.
        feedback(message);
        release.run();
      });
    });
    t.awaitConnection(go);
    postDelayed(go, 7_000);
    // Never held longer than this, whatever TDLib does.
    postDelayed(release, 30_000);
    return true;
  }

  /** For the worker: whether nothing is left to finish for the account (engine thread). */
  static boolean isStable (int acc) {
    CheckInRecord r = current;
    boolean activeStable = r == null || r.acc != acc ||
      (r.state == CheckInRecord.ARMED && r.alertReal && r.missingAt == 0) ||
      (r.state == CheckInRecord.FIRED && r.firedNotified);
    // A watch record waiting for its alert time is left to its alarm, which enqueues the work again.
    CheckInRecord w = watches.get(acc);
    boolean watchStable = w == null || now(w) < w.sendAt + CheckInRules.AFTER_ALERT_WAIT;
    return activeStable && watchStable;
  }

  // Location service

  static boolean hasLocationPermission (Context context) {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean hasFineLocation (Context context) {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean hasBackgroundLocation (Context context) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  private static boolean needsLocation (CheckInRecord r, long now) {
    if (!r.loc && !r.hasDest) {
      return false;
    }
    switch (r.state) {
      case CheckInRecord.STARTING:
      case CheckInRecord.ARMED:
        return true;
      case CheckInRecord.FIRED:
        // For an hour after the alert went out: Keep the sent alert updated, and arriving still ends it ("I've arrived").
        return now < r.sendAt + CheckInRules.FOLLOW_WINDOW && ((r.follow && r.loc && r.followEdits < CheckInRules.FOLLOW_MAX) || r.hasDest);
      default:
        return false;
    }
  }

  private static void setPaused (CheckInRecord r, boolean paused) {
    if (locationPaused != paused) {
      locationPaused = paused;
      XgramCheckInNotifier.showOngoing(r);
    }
  }

  /** Starts or stops the location service as the record needs, if the system allows it from here. */
  private static void ensureService (CheckInRecord r, boolean fromAction) {
    Context context = AppContext.get();
    long now = now(r);
    if (!needsLocation(r, now)) {
      stopService();
      setPaused(r, false);
      return;
    }
    if (!hasLocationPermission(context)) {
      // Denied from the start: the alert just says "not available". Revoked during the trip (a location was had, which
      // needs the permission): updates are paused. An alert edit alone (battery, Add time) proves nothing.
      stopService();
      setPaused(r, lastFix != null || r.lastFixAt != 0);
      return;
    }
    if (XgramCheckInService.isRunning()) {
      XgramCheckInService.refresh();
      return;
    }
    boolean resumed = UI.isResumed();
    if (!resumed && !hasBackgroundLocation(context) && !fromAction) {
      // A location service can't start from the background without "Allow all the time": the next app open resumes it.
      setPaused(r, true);
      return;
    }
    Intent intent = new Intent(context, XgramCheckInService.class);
    try {
      if (resumed) {
        context.startService(intent);
      } else {
        ContextCompat.startForegroundService(context, intent);
      }
      log("service start ref=%s", r.ref);
      setPaused(r, false);
    } catch (Throwable t) {
      log("service start refused: %s", t.toString());
      setPaused(r, true);
    }
  }

  private static void stopService () {
    if (XgramCheckInService.isRunning()) {
      try {
        Context context = AppContext.get();
        context.stopService(new Intent(context, XgramCheckInService.class));
      } catch (Throwable ignored) { }
    }
  }

  /** The service could not go to the foreground (main thread). */
  static void onServiceFailed () {
    post(() -> {
      CheckInRecord r = current;
      if (r != null) {
        setPaused(r, true);
      }
    });
  }

  /** The service stopped: the ongoing notification stays (posted again without it) while the Check In runs. */
  static void onServiceStopped () {
    post(() -> {
      CheckInRecord r = current;
      if (r != null) {
        XgramCheckInNotifier.showOngoing(r);
      }
    });
  }

  static void onLocationState (boolean paused, boolean servicesOff) {
    post(() -> {
      CheckInRecord r = current;
      boolean changed = locationPaused != paused || locationServicesOff != servicesOff;
      locationPaused = paused;
      locationServicesOff = servicesOff;
      if (changed && r != null) {
        XgramCheckInNotifier.showOngoing(r);
      }
    });
  }

  // Feedback

  /** A snackbar with Expressive design, else a toast. Main thread. */
  static void feedback (CharSequence text) {
    if (text == null || text.length() == 0) {
      return;
    }
    if (!com.fahadali.xgram.appearance.XgramSnackbar.show(text, Toast.LENGTH_SHORT)) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  // TDLib updates (TDLib thread): filtered against the published record, then handled on the engine thread

  private static boolean contains (long[] ids, long id) {
    if (ids == null || id == 0) {
      return false;
    }
    for (long value : ids) {
      if (value == id) {
        return true;
      }
    }
    return false;
  }

  private static final class Listener implements MessageListener, ConnectionListener {
    private final Tdlib tdlib;
    private volatile boolean sawConnectionState;

    Listener (Tdlib tdlib) {
      this.tdlib = tdlib;
    }

    @Override
    public void onNewMessage (TdApi.Message message) {
      if (!message.isOutgoing || message.schedulingState != null || !(message.content instanceof TdApi.MessageText) || message.sendingState != null) {
        return;
      }
      if (!hasRecordFor(tdlib.id())) {
        return;
      }
      String text = CheckInText.textOf(message);
      int acc = tdlib.id();
      CheckInRecord r = published;
      boolean match = r != null && r.acc == acc && r.chat == message.chatId && CheckInRules.isAlertText(text, r.ref);
      if (!match) {
        for (CheckInRecord w : publishedWatches) {
          if (w.acc == acc && w.chat == message.chatId && CheckInRules.isAlertText(text, w.ref)) {
            match = true;
            break;
          }
        }
      }
      if (match) {
        post(() -> onAlertSeen(tdlib, message));
      }
    }

    @Override
    public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
      if (!failedRefs.isEmpty() && message.schedulingState != null) {
        String text = CheckInText.textOf(message);
        for (String ref : failedRefs) {
          if (CheckInRules.isAlertText(text, ref)) {
            // A Check In that failed to start: its alert must not wait on the server unattended.
            failedRefs.remove(ref);
            log("deleting orphaned alert ref=%s", ref);
            tdlib.send(new TdApi.DeleteMessages(message.chatId, new long[] {message.id}, true), (ok, error) -> { });
            return;
          }
        }
      }
      CheckInRecord r = activeSnapshot();
      if (r != null && r.acc == tdlib.id() && r.chat == message.chatId) {
        post(() -> onSendSucceeded(tdlib, message, oldMessageId));
      }
    }

    @Override
    public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
      CheckInRecord r = activeSnapshot();
      if (r != null && r.acc == tdlib.id() && r.chat == message.chatId) {
        post(() -> onSendFailed(tdlib, message, oldMessageId, error));
      }
    }

    @Override
    public void onMessagesDeleted (long chatId, long[] messageIds) {
      CheckInRecord r = activeSnapshot();
      if (r != null && r.acc == tdlib.id() && r.chat == chatId && contains(messageIds, r.alertId)) {
        // Sent, or deleted somewhere: the scheduled list tells which, shortly.
        final int acc = r.acc;
        post(() -> scheduleReconcile(acc, 5_000));
      }
    }

    @Override
    public void onConnectionStateChanged (int newState, int oldState) {
      boolean first = !sawConnectionState;
      sawConnectionState = true;
      if (hasRecordFor(tdlib.id())) {
        post(() -> onConnectionState(tdlib, newState, first));
      }
    }
  }
}
