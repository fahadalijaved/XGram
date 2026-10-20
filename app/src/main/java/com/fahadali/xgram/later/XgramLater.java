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
package com.fahadali.xgram.later;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * Later: reminders on messages, follow-ups on your own messages and snoozed chats, all local to this device. This is
 * what the rest of the app calls; the lookups the chat list makes per chat are map reads with no locks.
 */
public final class XgramLater {
  private XgramLater () { }

  // Events: which chats changed and how (several at once)

  /** A snooze started or ended: the chat leaves or comes back to the lists. */
  public static final int EVENT_HIDDEN = 1;
  /** The bell of the chat came or went. */
  public static final int EVENT_BELL = 1 << 1;
  /** The Snoozed rows need counting again (a snooze changed, or a snoozed chat moved between lists). */
  public static final int EVENT_LIST = 1 << 2;
  /** Woken chats moving up changed order. */
  public static final int EVENT_RAISE = 1 << 3;
  /** Any record changed (the Later page). */
  public static final int EVENT_RECORDS = 1 << 4;

  public interface Listener {
    /** Any thread. {@code chatIds} empty: every chat of the account (a restore, a logout, a setting). */
    void onLaterChanged (int accountId, long[] chatIds, int events);
  }

  private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private static final long[] NO_CHATS = new long[0];

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  static void emit (int accountId, long[] chatIds, int events) {
    for (Listener listener : listeners) {
      listener.onLaterChanged(accountId, chatIds, events);
    }
  }

  // Settings (kept here, not in XgramSettings; backed up by XgramBackup)

  public static final String KEY_ROW = "xgram_later_row";
  public static final String KEY_RAISE = "xgram_later_raise";
  public static final String KEY_WAKE_EARLY = "xgram_later_wake_early";
  /** Local only: the notification permission was asked for once. */
  static final String KEY_NOTIF_ASKED = "xgram_later_notif_asked";

  private static volatile int showRow = -1, raise = -1, wakeEarly = -1;

  public static boolean showRow () {
    if (showRow == -1) {
      showRow = Settings.instance().getBoolean(KEY_ROW, true) ? 1 : 0;
    }
    return showRow == 1;
  }

  public static void setShowRow (boolean show) {
    showRow = show ? 1 : 0;
    Settings.instance().putBoolean(KEY_ROW, show);
    emitAllAccounts(EVENT_LIST);
  }

  public static boolean raiseWoken () {
    if (raise == -1) {
      raise = Settings.instance().getBoolean(KEY_RAISE, true) ? 1 : 0;
    }
    return raise == 1;
  }

  public static void setRaiseWoken (boolean on) {
    raise = on ? 1 : 0;
    Settings.instance().putBoolean(KEY_RAISE, on);
    // Every chat with a bell moves up or back
    for (XgramLaterStore.AccountIndex index : XgramLaterStore.allSnapshots()) {
      if (index.bells.isEmpty()) {
        continue;
      }
      long[] chatIds = new long[index.bells.size()];
      int i = 0;
      for (Long chatId : index.bells.keySet()) {
        chatIds[i++] = chatId;
      }
      emit(index.accountId, chatIds, EVENT_RAISE);
    }
  }

  /** XgramLaterRecord.WAKE_TO_ME (default), WAKE_ANY or WAKE_TIME_ONLY: frozen into each new timed snooze. */
  public static int wakeEarlyMode () {
    if (wakeEarly == -1) {
      int mode = Settings.instance().getInt(KEY_WAKE_EARLY, XgramLaterRecord.WAKE_TO_ME);
      wakeEarly = mode >= XgramLaterRecord.WAKE_TO_ME && mode <= XgramLaterRecord.WAKE_TIME_ONLY ? mode : XgramLaterRecord.WAKE_TO_ME;
    }
    return wakeEarly;
  }

  public static void setWakeEarlyMode (int mode) {
    wakeEarly = mode;
    Settings.instance().putInt(KEY_WAKE_EARLY, mode);
  }

  /** "On messages to me", "On any new message" or "Only at the time set". */
  public static String wakeEarlyName (int mode) {
    switch (mode) {
      case XgramLaterRecord.WAKE_ANY:
        return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterWakeAny);
      case XgramLaterRecord.WAKE_TIME_ONLY:
        return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterWakeTimeOnly);
      case XgramLaterRecord.WAKE_TO_ME:
      default:
        return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterWakeToMe);
    }
  }

  /** The classic settings page's choice (Telegram X's radio popup). */
  public static void showWakeEarlyPicker (ViewController<?> c, @Nullable Runnable after) {
    final int mode = wakeEarlyMode();
    final int group = org.thunderdog.challegram.R.id.btn_xgramLaterWakeEarly;
    c.showSettings(new org.thunderdog.challegram.navigation.SettingsWrapBuilder(group).setRawItems(new org.thunderdog.challegram.ui.ListItem[] {
      new org.thunderdog.challegram.ui.ListItem(org.thunderdog.challegram.ui.ListItem.TYPE_RADIO_OPTION, org.thunderdog.challegram.R.id.btn_xgramLaterWakeToMe, 0, org.thunderdog.challegram.R.string.XgramLaterWakeToMe, group, mode == XgramLaterRecord.WAKE_TO_ME),
      new org.thunderdog.challegram.ui.ListItem(org.thunderdog.challegram.ui.ListItem.TYPE_RADIO_OPTION, org.thunderdog.challegram.R.id.btn_xgramLaterWakeAny, 0, org.thunderdog.challegram.R.string.XgramLaterWakeAny, group, mode == XgramLaterRecord.WAKE_ANY),
      new org.thunderdog.challegram.ui.ListItem(org.thunderdog.challegram.ui.ListItem.TYPE_RADIO_OPTION, org.thunderdog.challegram.R.id.btn_xgramLaterWakeTimeOnly, 0, org.thunderdog.challegram.R.string.XgramLaterWakeTimeOnly, group, mode == XgramLaterRecord.WAKE_TIME_ONLY)
    }).setIntDelegate((id, result) -> {
      int picked = result.get(group);
      setWakeEarlyMode(picked == org.thunderdog.challegram.R.id.btn_xgramLaterWakeAny ? XgramLaterRecord.WAKE_ANY :
        picked == org.thunderdog.challegram.R.id.btn_xgramLaterWakeTimeOnly ? XgramLaterRecord.WAKE_TIME_ONLY : XgramLaterRecord.WAKE_TO_ME);
      if (after != null) {
        after.run();
      }
    }));
  }

  /** Exact alarms may be set (always below Android 12 and with USE_EXACT_ALARM on 13+). */
  public static boolean exactAllowed (android.content.Context ctx) {
    return XgramLaterAlarms.canExact(ctx);
  }

  /** Android 12+: the system page that allows exact alarms. */
  public static void openExactSettings (ViewController<?> c) {
    XgramLaterAlarms.openExactSettings(c);
  }

  /**
   * Battery optimization is on for XGram: Samsung's sleeping apps and similar can hold the alarm back until the app is
   * opened again. "Unrestricted" in the app's battery settings turns it off.
   */
  public static boolean batteryRestricted (android.content.Context ctx) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
      return false;
    }
    try {
      android.os.PowerManager power = (android.os.PowerManager) ctx.getSystemService(android.content.Context.POWER_SERVICE);
      return power != null && !power.isIgnoringBatteryOptimizations(ctx.getPackageName());
    } catch (Throwable t) {
      return false;
    }
  }

  /** The value of the "Exact reminder times" row: allowed, allowed with the battery hint, or not allowed. */
  public static String exactRowValue (android.content.Context ctx) {
    if (!exactAllowed(ctx)) {
      return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterExactNotAllowed);
    }
    if (batteryRestricted(ctx)) {
      return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterExactBattery);
    }
    return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterExactAllowed);
  }

  /** A tap on that row: the exact alarm page when they aren't allowed, else the app's page for its battery setting. */
  public static void openExactRow (ViewController<?> c) {
    if (!exactAllowed(c.context()) || !batteryRestricted(c.context())) {
      XgramLaterAlarms.openExactSettings(c);
    } else {
      XgramLaterAlarms.openBatterySettings(c.context());
    }
  }

  /** The system settings of the Reminders channel. */
  public static void openReminderChannelSettings (android.content.Context ctx) {
    XgramLaterNotifications.openChannelSettings(ctx);
  }

  /** After a settings import or reset: read the values again. */
  public static void reloadSettings () {
    showRow = raise = wakeEarly = -1;
    emitAllAccounts(EVENT_LIST | EVENT_RAISE | EVENT_RECORDS);
  }

  private static void emitAllAccounts (int events) {
    for (XgramLaterStore.AccountIndex index : XgramLaterStore.allSnapshots()) {
      emit(index.accountId, NO_CHATS, events);
    }
  }

  // Attach (Tdlib constructor, right after XgramGhost.attach: listeners exist, the cache does not yet)

  private static final AtomicBoolean caughtUp = new AtomicBoolean();

  public static void attach (Tdlib tdlib) {
    XgramLaterEngine.register(tdlib);
    tdlib.listeners().subscribeForGlobalUpdates(new Live(tdlib));
    if (caughtUp.compareAndSet(false, true)) {
      // Catch-up after a force stop, a reboot or a late inexact alarm: whatever is overdue fires now
      XgramLaterEngine.post(() -> {
        long now = System.currentTimeMillis();
        XgramLaterStore.ensureLoaded();
        XgramLaterStore.prune(now);
        XgramLaterEngine.processDue(now, false);
        XgramLaterAlarms.arm(XgramLaterEngine.context());
        if (org.thunderdog.challegram.BuildConfig.DEBUG) {
          XgramLaterSelfTest.run();
        }
      });
    }
    // No awaitInitialization here: a job added in the constructor takes a reference that would wake this account's
    // TDLib early. Answered follow-ups are found when they come due (processDue) and when Later opens (reconcile).
  }

  /** New messages, mentions and deletions of watched chats; position changes of snoozed ones. TDLib thread, O(1) otherwise. */
  private static final class Live implements MessageListener, ChatListener {
    private final Tdlib tdlib;

    Live (Tdlib tdlib) {
      this.tdlib = tdlib;
    }

    @Override
    public void onNewMessage (TdApi.Message message) {
      if (message.isOutgoing || !XgramLaterStore.isLoaded()) {
        return;
      }
      if (XgramLaterStore.snapshot(tdlib.id()).watchers.contains(message.chatId)) {
        XgramLaterEngine.post(() -> XgramLaterEngine.onIncoming(tdlib, message));
      }
    }

    @Override
    public void onMessagesDeleted (long chatId, long[] messageIds) {
      if (XgramLaterStore.isLoaded() && XgramLaterStore.snapshot(tdlib.id()).chats.contains(chatId)) {
        XgramLaterEngine.post(() -> XgramLaterEngine.onDeleted(tdlib, chatId, messageIds));
      }
    }

    @Override
    public void onChatUnreadMentionCount (long chatId, int unreadMentionCount, boolean availabilityChanged) {
      if (!XgramLaterStore.isLoaded()) {
        return;
      }
      XgramLaterRecord snooze = XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(chatId);
      if (snooze == null) {
        return;
      }
      if (snooze.mentionsAt > unreadMentionCount) {
        XgramLaterEngine.post(() -> XgramLaterEngine.onMentionCount(tdlib, chatId, unreadMentionCount));
      } else if (unreadMentionCount > snooze.mentionsAt && snooze.wakeMode != XgramLaterRecord.WAKE_TIME_ONLY && !snooze.hasFlag(XgramLaterRecord.FLAG_RESYNC)) {
        // A new mention or reply to you, also one that came without updateNewMessage (a gap after a long time offline)
        XgramLaterEngine.wakeAsync(tdlib.id(), snooze, true);
      }
    }

    @Override
    public void onChatTopMessageChanged (long chatId, @Nullable TdApi.Message topMessage) {
      if (topMessage == null || topMessage.isOutgoing || !XgramLaterStore.isLoaded()) {
        return;
      }
      if (XgramLaterStore.snapshot(tdlib.id()).watchers.contains(chatId)) {
        XgramLaterEngine.post(() -> XgramLaterEngine.onTopMessage(tdlib, chatId));
      }
    }

    private void onListChange (long chatId) {
      if (XgramLaterStore.isLoaded() && XgramLaterStore.snapshot(tdlib.id()).chatSnooze.containsKey(chatId)) {
        emit(tdlib.id(), new long[] {chatId}, EVENT_LIST);
      }
    }

    @Override
    public void onChatPositionChanged (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) {
      if (position.order == 0 || sourceChanged || pinStateChanged) {
        onListChange(chatId);
      }
    }

    @Override
    public void onChatAddedToList (long chatId, TdApi.ChatList chatList) {
      onListChange(chatId);
    }

    @Override
    public void onChatRemovedFromList (long chatId, TdApi.ChatList chatList) {
      onListChange(chatId);
    }

    @Override
    public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
      onListChange(chatId); // the Snoozed row's unread dot
    }
  }

  // Hot paths (TDLib thread and draw paths: map lookups only)

  /**
   * Whether the chat list leaves this chat out: a pending snooze that hasn't woken. A snooze whose time passed, or
   * whose chat already shows an answer, wakes here (on the Later thread) and the chat stays in. TDLib thread.
   */
  public static boolean isChatHidden (Tdlib tdlib, TdApi.Chat chat) {
    XgramLaterRecord r = XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(chat.id);
    if (r == null) {
      return false;
    }
    if (r.isTimed() && System.currentTimeMillis() >= r.wakeAt) {
      XgramLaterEngine.wakeAsync(tdlib.id(), r, false);
      return false;
    }
    if (r.hasFlag(XgramLaterRecord.FLAG_RESYNC)) {
      XgramLaterEngine.resyncAsync(tdlib, r); // started again while TDLib wasn't running: take the chat's state now
      return true;
    }
    if (XgramLaterEngine.wokenByState(r, chat)) {
      XgramLaterEngine.wakeAsync(tdlib.id(), r, true);
      return false;
    }
    return true;
  }

  /** A pending snooze, without side effects (merged communities leave such members out). */
  public static boolean isSnoozedPending (Tdlib tdlib, long chatId) {
    return XgramLaterStore.isLoaded() && XgramLaterStore.snapshot(tdlib.id()).chatSnooze.containsKey(chatId);
  }

  @Nullable
  public static XgramLaterRecord pendingSnooze (Tdlib tdlib, long chatId) {
    return XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(chatId);
  }

  /** Something due and not seen yet in the chat: the bell next to the counters. */
  public static boolean hasBell (Tdlib tdlib, long chatId) {
    return XgramLaterStore.isLoaded() && XgramLaterStore.snapshot(tdlib.id()).bells.containsKey(chatId);
  }

  /** When the chat's bell came (it then sits under the pinned chats), or 0. */
  public static long raisedAt (Tdlib tdlib, long chatId) {
    if (!raiseWoken() || !XgramLaterStore.isLoaded()) {
      return 0;
    }
    Long at = XgramLaterStore.snapshot(tdlib.id()).raised.get(chatId);
    return at != null ? at : 0;
  }

  /** A Telegram notification of a snoozed chat is dropped, unless the message wakes the snooze. */
  public static boolean shouldDropNotification (Tdlib tdlib, TdApi.Message message) {
    if (!XgramLaterStore.isLoaded()) {
      return false;
    }
    XgramLaterRecord r = XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(message.chatId);
    if (r == null || (r.isTimed() && System.currentTimeMillis() >= r.wakeAt)) {
      return false;
    }
    return !XgramLaterEngine.qualifies(r, message);
  }

  /**
   * {@link #shouldDropNotification} for a notification TDLib built from a push while XGram wasn't running: there is no
   * message, only its id and whether it is outgoing. {@code mentionsGroup}: it is in the mentions and replies group.
   */
  public static boolean shouldDropPush (Tdlib tdlib, long chatId, TdApi.NotificationTypeNewPushMessage push, boolean mentionsGroup) {
    if (!XgramLaterStore.isLoaded()) {
      return false;
    }
    XgramLaterRecord r = XgramLaterStore.snapshot(tdlib.id()).chatSnooze.get(chatId);
    if (r == null || (r.isTimed() && System.currentTimeMillis() >= r.wakeAt)) {
      return false;
    }
    return !XgramLaterEngine.qualifies(r, push.isOutgoing, push.messageId, mentionsGroup);
  }

  // The chat lists' first load

  private static final java.util.Set<Integer> reconciled = java.util.concurrent.ConcurrentHashMap.newKeySet();

  /**
   * Once per account and process, after a chat list has loaded (its chats are cached now): private follow-ups whose
   * reply came while XGram was closed are done, and a false "No reply" notification of the catch-up goes.
   */
  public static void reconcileOnce (Tdlib tdlib) {
    if (reconciled.add(tdlib.id())) {
      XgramLaterEngine.reconcile(tdlib);
    }
  }

  // Snooze availability

  /**
   * Whether the chat can be snoozed from {@code chatList} (null: from inside the chat, any of its lists): available, in
   * the list, not a PSA chat and not a member of a merged community.
   */
  public static boolean canSnooze (Tdlib tdlib, @Nullable TdApi.Chat chat, @Nullable TdApi.ChatList chatList) {
    if (chat == null || !tdlib.chatAvailable(chat)) {
      return false;
    }
    boolean listed = false;
    if (chatList != null) {
      TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
      if (position == null || position.order == 0 || position.source instanceof TdApi.ChatSourcePublicServiceAnnouncement) {
        return false;
      }
      listed = true;
    } else if (chat.positions != null) {
      for (TdApi.ChatPosition position : chat.positions) {
        if (position.order != 0 && !(position.source instanceof TdApi.ChatSourcePublicServiceAnnouncement)) {
          listed = true;
          break;
        }
      }
    }
    if (!listed) {
      return false;
    }
    long communityId = com.fahadali.xgram.chats.XgramCommunities.communityOf(tdlib, chat.id);
    return communityId == 0 || !com.fahadali.xgram.chats.XgramCommunities.isMergeEnabled(tdlib, communityId);
  }

  /**
   * {@link #canSnooze} from a screen: only your own chat lists (not pickers, share lists or the FILTER_* tabs, which
   * don't leave snoozed chats out) and the community page.
   */
  public static boolean canSnoozeFrom (@Nullable ViewController<?> context, Tdlib tdlib, @Nullable TdApi.Chat chat, @Nullable TdApi.ChatList chatList) {
    if (context == null || context.isInForceTouchMode()) {
      return false;
    }
    if (context instanceof ChatsController) {
      if (!((ChatsController) context).xgramMergesCommunities()) {
        return false;
      }
    } else if (!(context instanceof MainController) && !(context instanceof com.fahadali.xgram.chats.XgramCommunityController)) {
      return false;
    }
    return canSnooze(tdlib, chat, chatList);
  }

  // Opening a chat

  /** MessagesController.onFocus: the chat is on screen for real (not a preview or a prewarmed screen). */
  public static void onChatOpened (MessagesController c) {
    try {
      XgramLaterEngine.onChatOpened(c);
    } catch (Throwable t) {
      android.util.Log.e(XgramLaterStore.TAG, "onChatOpened failed", t);
    }
  }

  // The Snoozed row of a chat list

  /** What the Snoozed row of one list shows. Equal states draw the same. */
  public static final class RowState {
    public final int count;
    /** An unmuted snoozed chat has unread messages. */
    public final boolean unread;
    /** The first two titles, then "+n". */
    @NonNull public final String titles;
    /** The next timed wake, 0 when none is timed. */
    public final long nextWakeAt;
    public final long[] chatIds;
    /** The local day it was made on: "Tomorrow, 9:00" reads "Today, 9:00" once the date changes. */
    final int day;

    RowState (int count, boolean unread, @NonNull String titles, long nextWakeAt, long[] chatIds) {
      this.count = count;
      this.unread = unread;
      this.titles = titles;
      this.nextWakeAt = nextWakeAt;
      this.chatIds = chatIds;
      java.util.Calendar c = java.util.Calendar.getInstance();
      this.day = c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR);
    }

    @Override
    public boolean equals (Object o) {
      if (!(o instanceof RowState)) {
        return false;
      }
      RowState other = (RowState) o;
      return count == other.count && unread == other.unread && nextWakeAt == other.nextWakeAt && day == other.day && titles.equals(other.titles);
    }

    @Override
    public int hashCode () {
      return count * 31 + titles.hashCode();
    }
  }

  /** The Snoozed row of {@code chatList}, or null when it has no snoozed chat (or the row is turned off). */
  @Nullable
  public static RowState snoozedIn (Tdlib tdlib, TdApi.ChatList chatList) {
    if (!showRow()) {
      return null;
    }
    XgramLaterStore.AccountIndex index = XgramLaterStore.snapshot(tdlib.id());
    if (index.chatSnooze.isEmpty()) {
      return null;
    }
    List<XgramLaterRecord> present = new ArrayList<>();
    boolean unread = false;
    for (XgramLaterRecord r : index.chatSnooze.values()) {
      TdApi.Chat chat = tdlib.chat(r.chatId);
      TdApi.ChatPosition position = chat != null ? ChatPosition.findPosition(chat, chatList) : null;
      if (position == null || position.order == 0) {
        continue;
      }
      present.add(r);
      if ((chat.unreadCount > 0 || chat.isMarkedAsUnread) && tdlib.chatNotificationsEnabled(chat)) {
        unread = true;
      }
    }
    if (present.isEmpty()) {
      return null;
    }
    // Soonest first, untimed last; ties by title
    java.util.Collections.sort(present, (a, b) -> {
      long x = a.isTimed() ? a.wakeAt : Long.MAX_VALUE, y = b.isTimed() ? b.wakeAt : Long.MAX_VALUE;
      return x != y ? Long.compare(x, y) : Long.compare(a.chatId, b.chatId);
    });
    long next = present.get(0).isTimed() ? present.get(0).wakeAt : 0;
    StringBuilder titles = new StringBuilder();
    long[] chatIds = new long[present.size()];
    for (int i = 0; i < present.size(); i++) {
      XgramLaterRecord r = present.get(i);
      chatIds[i] = r.chatId;
      if (i < 2) {
        if (i > 0) {
          titles.append(", ");
        }
        titles.append(tdlib.chatTitle(r.chatId));
      }
    }
    if (present.size() > 2) {
      titles.append(" +").append(present.size() - 2);
    }
    return new RowState(present.size(), unread, titles.toString(), next, chatIds);
  }

  // Accounts

  /** Logout: the slot's records, notifications and summary go; the alarm is armed again. */
  public static void forgetAccount (int accountId) {
    final List<XgramLaterRecord> removed = XgramLaterStore.forgetAccount(accountId);
    XgramLaterEngine.post(() -> {
      android.content.Context ctx = XgramLaterEngine.context();
      for (XgramLaterRecord r : removed) {
        XgramLaterNotifications.cancel(ctx, r.key);
      }
      XgramLaterNotifications.cancelSummary(ctx, accountId);
      XgramLaterAlarms.arm(ctx);
    });
    emit(accountId, NO_CHATS, EVENT_HIDDEN | EVENT_BELL | EVENT_LIST | EVENT_RAISE | EVENT_RECORDS);
  }

  public static JSONArray exportAccount (int accountId) {
    return XgramLaterStore.exportAccount(accountId);
  }

  /** Backup restore: missing records are added (local ones win), overdue ones come back due and quiet. */
  public static void importAccount (int accountId, @Nullable JSONArray records) {
    XgramLaterStore.ensureLoaded();
    final List<XgramLaterRecord> added;
    synchronized (XgramLaterStore.LOCK) {
      added = XgramLaterStore.importAccountLocked(accountId, records, System.currentTimeMillis());
    }
    if (added.isEmpty()) {
      return;
    }
    emit(accountId, NO_CHATS, EVENT_HIDDEN | EVENT_BELL | EVENT_LIST | EVENT_RAISE | EVENT_RECORDS);
    int due = 0;
    for (XgramLaterRecord r : added) {
      if (r.isDue()) {
        due++;
      }
    }
    final int dueCount = due;
    XgramLaterEngine.post(() -> {
      android.content.Context ctx = XgramLaterEngine.context();
      if (dueCount > 0) {
        XgramLaterNotifications.postSummary(ctx, accountId, dueCount);
      }
      XgramLaterAlarms.arm(ctx);
    });
  }

  /** The message menu's item: "Remind me…", or "Reminder · Tue, 9:00" / "Follow-up · Fri, 10:00" once set. */
  public static String remindLabel (Tdlib tdlib, long chatId, long messageId) {
    XgramLaterRecord r = record(tdlib.id(), chatId, messageId);
    if (r != null && (r.isPending() || r.isDue()) && r.isVisibleItem() && r.isTimed()) {
      return XgramLaterPicker.recordTitle(r);
    }
    return org.thunderdog.challegram.core.Lang.getString(org.thunderdog.challegram.R.string.XgramLaterRemind);
  }

  // Reading records (the Later page)

  public static List<XgramLaterRecord> records (int accountId) {
    return XgramLaterEngine.recordsOf(accountId);
  }

  @Nullable
  public static XgramLaterRecord record (int accountId, long chatId, long messageId) {
    return XgramLaterStore.get(accountId, XgramLaterRecord.key(accountId, chatId, messageId));
  }

  /** Whether a chat is a channel (for the wording of "until" snoozes). */
  static boolean isChannel (Tdlib tdlib, long chatId) {
    return ChatId.isSupergroup(chatId) && tdlib.isChannel(chatId);
  }
}
