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

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5Chat;
import com.fahadali.xgram.appearance.XgramSnackbar;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.collection.IntList;
import tgx.td.MessageId;

/**
 * Later: what is due, snoozed chats, reminders and follow-ups, newest due first, then by time; a Done page for the
 * last 30 days. Rows open the chat (at the message), a long press has the item's actions. Drawn in Compose with
 * Expressive design (TgxPagesLater), as Telegram X rows otherwise.
 */
public final class XgramLaterController extends RecyclerViewController<XgramLaterController.Args> implements View.OnClickListener, View.OnLongClickListener, XgramLater.Listener, MoreDelegate {
  public static final int MODE_MAIN = 0, MODE_DONE = 1;
  public static final int SECTION_NONE = 0, SECTION_SNOOZED = 1;

  public static final int BADGE_NONE = 0, BADGE_DUE = 1, BADGE_REPLIED = 2, BADGE_DELETED = 3, BADGE_UNAVAILABLE = 4;

  private static final int SEC_DUE = 1, SEC_SNOOZED = 2, SEC_REMINDERS = 3, SEC_FOLLOWUPS = 4, SEC_DONE = 5;
  private static final long DONE_WINDOW_MS = 30L * 24 * 60 * 60 * 1000;

  public static final class Args {
    final int mode, section;

    public Args (int mode, int section) {
      this.mode = mode;
      this.section = section;
    }
  }

  /** One item of the page. */
  public static final class Row {
    public final XgramLaterRecord record;
    final int section;

    Row (XgramLaterRecord record, int section) {
      this.record = record;
      this.section = section;
    }
  }

  private SettingsAdapter adapter;
  private boolean focusedOnce, rebuildPosted, scrolledToSection;
  /** Live texts of the messages (edits show), by key; "" when the message is gone. */
  private final Map<String, String> previews = new HashMap<>();
  private final Map<String, TdApi.Message> messages = new HashMap<>();
  private final Set<String> loading = new HashSet<>();
  private final Set<Long> chatsLoading = new HashSet<>();
  private final Set<Long> unavailable = new HashSet<>();

  public XgramLaterController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /** The Later page (scrolled to a section): on the right pane in two panes, a normal push otherwise. */
  public static void open (ViewController<?> context, int section) {
    XgramLaterController c = new XgramLaterController(context.context(), context.tdlib());
    c.setArguments(new Args(MODE_MAIN, section));
    if (com.fahadali.xgram.layout.XgramTwoPane.replacesRightPane(context)) {
      com.fahadali.xgram.layout.XgramTwoPane.showOnRight(context, c);
      return;
    }
    NavigationController navigation = context.navigationController();
    if (navigation == null) {
      navigation = context.context().navigation();
    }
    if (navigation != null) {
      navigation.navigateTo(c);
    }
  }

  private int mode () {
    Args args = getArguments();
    return args != null ? args.mode : MODE_MAIN;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramLater;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(mode() == MODE_DONE ? R.string.XgramLaterDoneTitle : R.string.XgramLater);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof Row) {
          Row row = (Row) data;
          view.setName(xgramTitleOf(row));
          view.setData(classicData(row));
        }
      }
    };
    adapter.setOnLongClickListener(this);
    rebuild();
    recyclerView.setAdapter(adapter);
    XgramLater.addListener(this);
    XgramLaterEngine.reconcile(tdlib); // private follow-ups answered while XGram was closed (cached chats only)
    Args args = getArguments();
    if (args != null && args.section == SECTION_SNOOZED) {
      UI.post(this::scrollToSection);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    XgramLater.removeListener(this);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (!focusedOnce) {
      focusedOnce = true;
    } else if (adapter != null) {
      rebuild();
    }
  }

  @Override
  public void onLaterChanged (int accountId, long[] chatIds, int events) {
    if (accountId != tdlib.id() || (events & XgramLater.EVENT_RECORDS) == 0) {
      return;
    }
    UI.post(() -> {
      if (isDestroyed() || adapter == null || rebuildPosted) {
        return;
      }
      rebuildPosted = true;
      UI.post(() -> {
        rebuildPosted = false;
        if (!isDestroyed()) {
          rebuild();
        }
      });
    });
  }

  private void scrollToSection () {
    if (isDestroyed() || adapter == null || scrolledToSection) {
      return;
    }
    scrolledToSection = true;
    int index = adapter.indexOfViewById(R.id.btn_xgramLaterSecSnoozed);
    if (index != -1 && getRecyclerView() != null && getRecyclerView().getLayoutManager() instanceof LinearLayoutManager) {
      ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(index, 0);
    }
  }

  // Items

  private void rebuild () {
    final boolean done = mode() == MODE_DONE;
    final long now = System.currentTimeMillis();
    List<XgramLaterRecord> due = new ArrayList<>(), snoozed = new ArrayList<>(), reminders = new ArrayList<>(), followUps = new ArrayList<>(), finished = new ArrayList<>();
    for (XgramLaterRecord r : XgramLater.records(tdlib.id())) {
      if (!r.isVisibleItem()) {
        continue;
      }
      if (r.isDone()) {
        if (now - (r.doneAt != 0 ? r.doneAt : r.createdAt) <= DONE_WINDOW_MS) {
          finished.add(r);
        }
      } else if (r.isDue()) {
        due.add(r);
      } else if (r.isSnooze()) {
        snoozed.add(r);
      } else if (r.kind == XgramLaterRecord.KIND_FOLLOWUP) {
        followUps.add(r);
      } else {
        reminders.add(r);
      }
    }
    Collections.sort(due, (a, b) -> Long.compare(b.dueAt, a.dueAt));
    Collections.sort(snoozed, (a, b) -> Long.compare(a.isTimed() ? a.wakeAt : Long.MAX_VALUE, b.isTimed() ? b.wakeAt : Long.MAX_VALUE));
    Collections.sort(reminders, (a, b) -> Long.compare(a.wakeAt, b.wakeAt));
    Collections.sort(followUps, (a, b) -> Long.compare(a.wakeAt, b.wakeAt));
    Collections.sort(finished, (a, b) -> Long.compare(b.doneAt, a.doneAt));

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (!done) {
      boolean anyTimed = !reminders.isEmpty() || !followUps.isEmpty();
      for (XgramLaterRecord r : snoozed) {
        anyTimed |= r.isTimed();
      }
      addStatusRows(items, anyTimed);
    }
    boolean any;
    if (done) {
      any = addSection(items, finished, SEC_DONE, 0);
    } else {
      any = addSection(items, due, SEC_DUE, R.string.XgramLaterSecDue);
      any |= addSection(items, snoozed, SEC_SNOOZED, R.string.XgramLaterSecSnoozed);
      any |= addSection(items, reminders, SEC_REMINDERS, R.string.XgramLaterSecReminders);
      any |= addSection(items, followUps, SEC_FOLLOWUPS, R.string.XgramLaterSecFollowUps);
      if (!finished.isEmpty()) {
        if (any) {
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramLaterDoneLink, R.drawable.baseline_check_circle_24, Lang.getString(R.string.XgramLaterDoneLink, finished.size())));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        any = true;
      }
    }
    if (!any) {
      if (XgramExpressive.isEnabled()) {
        items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_watch_later_24, R.string.XgramLaterEmpty));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramLaterEmptyHint));
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.XgramLaterEmpty) + "\n" + Lang.getString(R.string.XgramLaterEmptyHint)));
      }
    }
    adapter.setItems(items, false);
  }

  private void addStatusRows (List<ListItem> items, boolean anyTimed) {
    Context ctx = context();
    boolean notificationsOff = XgramLaterNotifications.remindersBlocked(ctx);
    boolean exactOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT <= 32 && !XgramLaterAlarms.canExact(ctx);
    // Battery optimization (Samsung's sleeping apps) can hold the alarm back: said while something waits for a time
    boolean batteryOn = anyTimed && !exactOff && XgramLater.batteryRestricted(ctx);
    if (!notificationsOff && !exactOff && !batteryOn) {
      return;
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (notificationsOff) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramLaterFixNotifications, R.drawable.baseline_notifications_off_24, R.string.XgramLaterNotifOffRow).setStringValue(Lang.getString(R.string.XgramLaterTurnOn)));
    }
    if (exactOff) {
      if (notificationsOff) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramLaterFixExact, R.drawable.baseline_watch_later_24, R.string.XgramLaterExactOffRow).setStringValue(Lang.getString(R.string.XgramLaterAllow)));
    }
    if (batteryOn) {
      if (notificationsOff) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramLaterFixBattery, R.drawable.baseline_watch_later_24, R.string.XgramLaterBatteryRow).setStringValue(Lang.getString(R.string.XgramLaterBatteryFix)));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  private boolean addSection (List<ListItem> items, List<XgramLaterRecord> records, int section, int headerRes) {
    if (records.isEmpty()) {
      return false;
    }
    if (headerRes != 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, section == SEC_SNOOZED ? R.id.btn_xgramLaterSecSnoozed : 0, 0, headerRes));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (XgramLaterRecord r : records) {
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      first = false;
      Row row = new Row(r, section);
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_xgramLaterItem, iconOf(r), "", false).setData(row).setLongId(r.chatId));
      load(r);
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return true;
  }

  private static int iconOf (XgramLaterRecord r) {
    if (r.isSnooze()) {
      return R.drawable.baseline_notifications_paused_24;
    }
    return r.kind == XgramLaterRecord.KIND_FOLLOWUP ? R.drawable.baseline_reply_24 : R.drawable.baseline_watch_later_24;
  }

  /** The chat (GetChat on a cache miss) and the message's live text (GetMessage, local first). */
  private void load (XgramLaterRecord r) {
    if (tdlib.chat(r.chatId) == null && chatsLoading.add(r.chatId)) {
      tdlib.send(new TdApi.GetChat(r.chatId), (chat, error) -> runOnUiThreadOptional(() -> {
        if (chat == null) {
          unavailable.add(r.chatId);
        }
        updateRowsOf(r.chatId, null);
      }));
    }
    if (r.isChatSnooze() || previews.containsKey(r.key)) {
      return;
    }
    if (r.hasFlag(XgramLaterRecord.FLAG_MSG_GONE)) {
      previews.put(r.key, ""); // deleted: the snapshot, nothing to ask for
      return;
    }
    if (!loading.add(r.key)) {
      return;
    }
    final String key = r.key;
    // Local first: only a message this device doesn't have asks the server, through the paced queue below
    tdlib.send(new TdApi.GetMessageLocally(r.chatId, r.messageId), (local, localError) -> {
      if (local != null) {
        onMessageLoaded(r, key, local);
      } else {
        runOnUiThreadOptional(() -> queueServerFetch(r));
      }
    });
  }

  // Messages this device doesn't have: one GetMessage at a time, 300 ms apart, at most 10 per page. The first error
  // other than "not found" stops it; the rest show their snapshot. A message not found is marked deleted, so it isn't
  // asked for again (a backup restored on a new device has none of its messages here).

  private static final int SERVER_FETCH_MAX = 10;
  private static final long SERVER_FETCH_GAP_MS = 300L;
  private final java.util.ArrayDeque<XgramLaterRecord> serverQueue = new java.util.ArrayDeque<>();
  private int serverFetches;
  private boolean serverBusy, serverStopped;

  private void queueServerFetch (XgramLaterRecord r) {
    if (serverStopped || serverFetches + serverQueue.size() >= SERVER_FETCH_MAX) {
      onMessageLoaded(r, r.key, null);
      return;
    }
    serverQueue.add(r);
    nextServerFetch();
  }

  private void nextServerFetch () {
    if (serverBusy || isDestroyed()) {
      return;
    }
    final XgramLaterRecord r = serverQueue.poll();
    if (r == null) {
      return;
    }
    serverBusy = true;
    serverFetches++;
    tdlib.send(new TdApi.GetMessage(r.chatId, r.messageId), (message, error) -> {
      final boolean notFound = error != null && error.code == 404;
      if (notFound) {
        XgramLaterEngine.post(() -> XgramLaterEngine.onDeleted(tdlib, r.chatId, new long[] {r.messageId}));
      }
      onMessageLoaded(r, r.key, message);
      runOnUiThreadOptional(() -> {
        serverBusy = false;
        if (error != null && !notFound) {
          serverStopped = true;
          XgramLaterRecord rest;
          while ((rest = serverQueue.poll()) != null) {
            onMessageLoaded(rest, rest.key, null);
          }
          return;
        }
        if (!serverQueue.isEmpty()) {
          UI.post(() -> {
            if (!isDestroyed()) {
              nextServerFetch();
            }
          }, SERVER_FETCH_GAP_MS);
        }
      });
    });
  }

  private void onMessageLoaded (XgramLaterRecord r, String key, @Nullable TdApi.Message message) {
    runOnUiThreadOptional(() -> {
      loading.remove(key);
      if (message != null) {
        messages.put(key, message);
        String text;
        try {
          text = org.thunderdog.challegram.data.ContentPreview.getChatListPreview(tdlib, message.chatId, message, true).buildText(false);
        } catch (Throwable t) {
          text = null;
        }
        previews.put(key, text != null ? text.replace('\n', ' ') : "");
      } else {
        previews.put(key, ""); // gone or out of reach: the snapshot (if any) is shown
      }
      updateRowsOf(r.chatId, key);
    });
  }

  /** Updates the rows of a chat (or of one key), whichever objects the list holds now. */
  private void updateRowsOf (long chatId, @Nullable String key) {
    if (adapter == null) {
      return;
    }
    List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      Object data = items.get(i).getData();
      if (data instanceof Row) {
        XgramLaterRecord r = ((Row) data).record;
        if (r.chatId == chatId && (key == null || key.equals(r.key))) {
          adapter.updateValuedSettingByPosition(i);
        }
      }
    }
  }

  // Row texts (classic and Compose)

  /** The chat's title (the stored one when the chat isn't known). */
  public CharSequence xgramTitleOf (Row row) {
    TdApi.Chat chat = tdlib.chat(row.record.chatId);
    return chat != null ? tdlib.chatTitle(chat.id) : row.record.title;
  }

  /**
   * The note, else the message's live text, else the snapshot taken when it was set (also while the live text loads,
   * which offline can take until the connection is back). Null only while there is nothing to show yet (a shimmer).
   */
  @Nullable
  private String aboutOf (XgramLaterRecord r) {
    if (!r.note.isEmpty()) {
      return r.note;
    }
    if (r.isChatSnooze()) {
      return "";
    }
    String live = previews.get(r.key);
    if (live == null) {
      return r.preview.isEmpty() ? null : r.preview;
    }
    return !live.isEmpty() ? live : r.preview;
  }

  /** Nothing to show but a shimmer: no note, no snapshot and the live text still loading. */
  public boolean xgramIsLoading (Row row) {
    XgramLaterRecord r = row.record;
    return !r.isChatSnooze() && r.note.isEmpty() && r.preview.isEmpty() && !previews.containsKey(r.key);
  }

  /** The supporting text: up to two lines in Compose. */
  public String xgramSubtitleOf (Row row) {
    XgramLaterRecord r = row.record;
    String about = aboutOf(r);
    if (about == null) {
      about = "";
    }
    switch (row.section) {
      case SEC_DUE: {
        String ago = Lang.getString(R.string.XgramLaterAgo, XgramLaterTimes.formatAge(System.currentTimeMillis() - r.dueAt));
        String what = r.isSnooze() ? (r.doneReason == XgramLaterRecord.DONE_WOKE ? Lang.getString(R.string.XgramLaterDoneWoke) : Lang.getString(R.string.XgramLaterNotifBack)) : about;
        return join(ago, what);
      }
      case SEC_SNOOZED: {
        String when = r.isTimed() ? Lang.getString(R.string.XgramLaterSnoozedUntil, XgramLaterTimes.formatWake(r.wakeAt)) : XgramLaterPicker.untilText(r);
        return join(when, r.note);
      }
      case SEC_FOLLOWUPS: {
        String by = Lang.getString(R.string.XgramLaterIfNoReplyBy, XgramLaterTimes.formatWake(r.wakeAt));
        String note = r.note;
        String preview = previews.get(r.key);
        if (preview == null || preview.isEmpty()) {
          preview = r.preview;
        }
        return join(by, join(note, preview.isEmpty() ? "" : Lang.getString(R.string.XgramLaterYou, preview)));
      }
      case SEC_DONE: {
        return join(doneText(r), about);
      }
      case SEC_REMINDERS:
      default: {
        String note = r.note;
        String preview = previews.get(r.key);
        if (preview == null || preview.isEmpty()) {
          preview = r.preview;
        }
        return note.isEmpty() ? preview : join(note, preview);
      }
    }
  }

  private String doneText (XgramLaterRecord r) {
    switch (r.doneReason) {
      case XgramLaterRecord.DONE_REPLIED:
        return Lang.getString(R.string.XgramLaterRepliedAfter, XgramLaterTimes.formatAge(r.doneAt - r.createdAt));
      case XgramLaterRecord.DONE_OPENED:
        return Lang.getString(R.string.XgramLaterDoneOpened);
      case XgramLaterRecord.DONE_WOKE:
        return Lang.getString(R.string.XgramLaterDoneWoke);
      case XgramLaterRecord.DONE_EXPIRED:
        return Lang.getString(R.string.XgramLaterDoneDeleted);
      case XgramLaterRecord.DONE_USER:
      default:
        return Lang.getString(R.string.XgramLaterDoneUser);
    }
  }

  private static String join (String a, String b) {
    if (a == null || a.isEmpty()) {
      return b != null ? b : "";
    }
    if (b == null || b.isEmpty()) {
      return a;
    }
    return a + " · " + b;
  }

  /** The trailing badge of the row, BADGE_*. */
  public int xgramBadgeOf (Row row) {
    XgramLaterRecord r = row.record;
    if (unavailable.contains(r.chatId)) {
      return BADGE_UNAVAILABLE;
    }
    if (r.hasFlag(XgramLaterRecord.FLAG_MSG_GONE)) {
      return BADGE_DELETED;
    }
    if (r.isDue()) {
      return BADGE_DUE;
    }
    if (r.isDone() && r.doneReason == XgramLaterRecord.DONE_REPLIED) {
      return BADGE_REPLIED;
    }
    return BADGE_NONE;
  }

  /** The trailing time label when there is no badge (a pending reminder's time, a done item's day), or null. */
  @Nullable
  public String xgramTrailingOf (Row row) {
    XgramLaterRecord r = row.record;
    if (row.section == SEC_REMINDERS && r.isTimed()) {
      return XgramLaterTimes.formatWake(r.wakeAt);
    }
    if (row.section == SEC_DONE && r.doneAt != 0) {
      return com.fahadali.xgram.appearance.XgramR6B5Pages.shortDate(r.doneAt / 1000L);
    }
    return null;
  }

  public static String badgeText (int badge) {
    switch (badge) {
      case BADGE_DUE:
        return Lang.getString(R.string.XgramLaterBadgeDue);
      case BADGE_REPLIED:
        return Lang.getString(R.string.XgramLaterBadgeReplied);
      case BADGE_DELETED:
        return Lang.getString(R.string.XgramLaterBadgeDeleted);
      case BADGE_UNAVAILABLE:
        return Lang.getString(R.string.XgramLaterBadgeUnavailable);
      default:
        return "";
    }
  }

  /** Classic: "Tue, 9:00 · note", with "Due · 2h ago · …" or "Deleted · …" in front. */
  private String classicData (Row row) {
    XgramLaterRecord r = row.record;
    String text;
    if (row.section == SEC_REMINDERS && r.isTimed()) {
      text = join(XgramLaterTimes.formatWake(r.wakeAt), xgramSubtitleOf(row));
    } else {
      text = xgramSubtitleOf(row);
    }
    int badge = xgramBadgeOf(row);
    if (badge != BADGE_NONE) {
      text = join(badgeText(badge), text);
    }
    if (text.isEmpty() && xgramIsLoading(row)) {
      text = Lang.getString(R.string.LoadingInformation);
    }
    return text;
  }

  // Taps

  @Nullable
  private static Row rowOf (View v) {
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    return data instanceof Row ? (Row) data : null;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_xgramLaterFixNotifications) {
      fixNotifications();
      return;
    }
    if (id == R.id.btn_xgramLaterFixExact) {
      XgramLaterAlarms.openExactSettings(this);
      return;
    }
    if (id == R.id.btn_xgramLaterFixBattery) {
      XgramLaterAlarms.openBatterySettings(context());
      return;
    }
    if (id == R.id.btn_xgramLaterDoneLink) {
      XgramLaterController c = new XgramLaterController(context, tdlib);
      c.setArguments(new Args(MODE_DONE, SECTION_NONE));
      navigateTo(c);
      return;
    }
    Row row = rowOf(v);
    if (row != null) {
      openRow(row);
    }
  }

  private void fixNotifications () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !androidx.core.app.NotificationManagerCompat.from(context()).areNotificationsEnabled()) {
      boolean asked = context().permissions().requestPostNotifications(granted -> {
        if (!isDestroyed()) {
          if (!granted) {
            XgramLaterNotifications.openAppNotificationSettings(context());
          }
          rebuild();
        }
      });
      if (asked) {
        return;
      }
      XgramLaterNotifications.openAppNotificationSettings(context());
      return;
    }
    XgramLaterNotifications.openChannelSettings(context());
  }

  private void openRow (Row row) {
    XgramLaterRecord r = row.record;
    if (r.isChatSnooze()) {
      tdlib.ui().openChat(this, r.chatId, new TdlibUi.ChatOpenParameters().keepStack());
      return;
    }
    TdApi.Message message = messages.get(r.key);
    TdApi.Chat chat = tdlib.chat(r.chatId);
    if (r.hasFlag(XgramLaterRecord.FLAG_MSG_GONE)) {
      tdlib.ui().openChat(this, r.chatId, new TdlibUi.ChatOpenParameters().keepStack());
    } else if (message != null && message.topicId instanceof TdApi.MessageTopicForum) {
      com.fahadali.xgram.topics.XgramForums.openTopicLink(this, r.chatId, (TdApi.MessageTopicForum) message.topicId, message, null);
    } else if (r.topicId != 0 && chat != null) {
      // The message isn't loaded (yet, or offline): its topic from the record, at the message
      TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters()
        .keepStack()
        .highlightMessage(new MessageId(r.chatId, r.messageId))
        .ensureHighlightAvailable();
      params.messageTopicId = new TdApi.MessageTopicForum(r.topicId);
      tdlib.ui().openChat(this, chat, params);
    } else {
      tdlib.ui().openChat(this, r.chatId, new TdlibUi.ChatOpenParameters()
        .highlightMessage(new MessageId(r.chatId, r.messageId))
        .ensureHighlightAvailable()
        .keepStack());
    }
  }

  @Override
  public boolean onLongClick (View v) {
    Row row = rowOf(v);
    if (row == null) {
      return false;
    }
    showItemMenu(v, row);
    return true;
  }

  // Item menu (the M3 menu at the row with Expressive design, the options sheet otherwise)

  private static final class Action {
    final int id, title, icon;
    final boolean destructive;
    final Runnable run;

    Action (int id, int title, int icon, boolean destructive, Runnable run) {
      this.id = id;
      this.title = title;
      this.icon = icon;
      this.destructive = destructive;
      this.run = run;
    }
  }

  private void showItemMenu (View anchor, Row row) {
    final XgramLaterRecord r = row.record;
    final int accountId = tdlib.id();
    final String key = r.key;
    List<Action> actions = new ArrayList<>();
    actions.add(new Action(R.id.btn_xgramLaterMOpen, R.string.XgramLaterOpen, R.drawable.baseline_forum_24, false, () -> openRow(row)));
    if (r.isDone()) {
      actions.add(new Action(R.id.btn_xgramLaterMRemindAgain, R.string.XgramLaterRemindAgain, R.drawable.baseline_watch_later_24, false, () -> reschedule(r)));
      actions.add(new Action(R.id.btn_xgramLaterMDelete, R.string.XgramLaterDelete, R.drawable.baseline_delete_24, true, () -> confirmDelete(key)));
    } else if (r.isSnooze()) {
      if (r.isPending()) {
        actions.add(new Action(R.id.btn_xgramLaterMChangeTime, R.string.XgramLaterChangeTime, R.drawable.baseline_schedule_24, false, () -> reschedule(r)));
        actions.add(new Action(R.id.btn_xgramLaterMEditNote, R.string.XgramLaterEditNote, R.drawable.baseline_create_24, false, () -> XgramLaterPicker.editNote(this, accountId, key)));
        actions.add(new Action(R.id.btn_xgramLaterMUnsnooze, R.string.XgramLaterUnsnoozeNow, R.drawable.baseline_notifications_24, false, () -> XgramLaterPicker.unsnooze(this, tdlib, r.chatId)));
      } else {
        actions.add(new Action(R.id.btn_xgramLaterMSnoozeAgain, R.string.XgramLaterSnoozeAgain, R.drawable.baseline_notifications_paused_24, false, () -> reschedule(r)));
        actions.add(new Action(R.id.btn_xgramLaterMMarkDone, R.string.XgramLaterMarkDone, R.drawable.baseline_check_circle_24, false, () -> XgramLaterPicker.markDone(this, accountId, key)));
      }
    } else {
      if (r.isPending()) {
        actions.add(new Action(R.id.btn_xgramLaterMChangeTime, R.string.XgramLaterChangeTime, R.drawable.baseline_schedule_24, false, () -> reschedule(r)));
      } else {
        actions.add(new Action(R.id.btn_xgramLaterMSnoozeAgain, R.string.XgramLaterSnoozeAgain, R.drawable.baseline_watch_later_24, false, () -> reschedule(r)));
      }
      actions.add(new Action(R.id.btn_xgramLaterMEditNote, R.string.XgramLaterEditNote, R.drawable.baseline_create_24, false, () -> XgramLaterPicker.editNote(this, accountId, key)));
      actions.add(new Action(R.id.btn_xgramLaterMMarkDone, R.string.XgramLaterMarkDone, R.drawable.baseline_check_circle_24, false, () -> XgramLaterPicker.markDone(this, accountId, key)));
      actions.add(new Action(R.id.btn_xgramLaterMDelete, R.string.XgramLaterDelete, R.drawable.baseline_delete_24, true, () -> confirmDelete(key)));
    }
    if (XgramExpressive.isEnabled()) {
      List<XgramR5Chat.MenuEntry> entries = new ArrayList<>(actions.size());
      for (Action action : actions) {
        XgramR5Chat.MenuEntry entry = new XgramR5Chat.MenuEntry(action.id, Lang.getString(action.title), action.icon, () -> {
          if (!isDestroyed()) {
            action.run.run();
          }
        });
        entries.add(action.destructive ? entry.destructive() : entry);
      }
      XgramR5Chat.showMenu(anchor, getThemeListeners(), entries);
      return;
    }
    int[] ids = new int[actions.size() + 1];
    String[] titles = new String[ids.length];
    int[] colors = new int[ids.length];
    int[] icons = new int[ids.length];
    for (int i = 0; i < actions.size(); i++) {
      Action action = actions.get(i);
      ids[i] = action.id;
      titles[i] = Lang.getString(action.title);
      colors[i] = action.destructive ? OptionColor.RED : OptionColor.NORMAL;
      icons[i] = action.icon;
    }
    ids[actions.size()] = R.id.btn_cancel;
    titles[actions.size()] = Lang.getString(R.string.Cancel);
    colors[actions.size()] = OptionColor.NORMAL;
    icons[actions.size()] = R.drawable.baseline_cancel_24;
    showOptions(xgramTitleOf(row), ids, titles, colors, icons, (itemView, id) -> {
      for (Action action : actions) {
        if (action.id == id) {
          action.run.run();
          break;
        }
      }
      return true;
    });
  }

  private void reschedule (XgramLaterRecord r) {
    XgramLaterRecord current = XgramLaterEngine.current(tdlib.id(), r.key);
    if (current != null) {
      XgramLaterPicker.showReschedule(this, tdlib, current);
    }
  }

  /** Expressive design: deleted with Undo. Classic: a confirm sheet first. */
  private void confirmDelete (String key) {
    if (XgramExpressive.isEnabled()) {
      XgramLaterPicker.delete(this, tdlib.id(), key);
      return;
    }
    showOptions(Lang.getString(R.string.XgramLaterDeleteConfirm), new int[] {R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramLaterDelete), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          XgramLaterEngine.delete(tdlib.id(), key);
        }
        return true;
      });
  }

  // Header menu

  @Override
  protected int getMenuId () {
    return isInForceTouchMode() ? 0 : R.id.menu_xgramLater;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_xgramLater) {
      header.addMoreButton(menu, this);
    } else {
      super.fillMenuItems(id, header, menu);
    }
  }

  @Override
  protected void openMoreMenu () {
    IntList ids = new IntList(3);
    StringList titles = new StringList(3);
    boolean anyDue = false, anyDone = false;
    for (XgramLaterRecord r : XgramLater.records(tdlib.id())) {
      anyDue |= r.isDue() && r.isVisibleItem();
      anyDone |= r.isDone();
    }
    if (mode() == MODE_MAIN && anyDue) {
      ids.append(R.id.btn_xgramLaterMMarkAllDone);
      titles.append(R.string.XgramLaterMarkAllDone);
    }
    if (mode() == MODE_DONE && anyDone) {
      ids.append(R.id.btn_xgramLaterMClearDone);
      titles.append(R.string.XgramLaterClearDone);
    }
    ids.append(R.id.btn_xgramLaterMSettings);
    titles.append(R.string.XgramLaterSettings);
    showMore(ids.get(), titles.get(), 0);
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_xgramLaterMMarkAllDone) {
      final List<XgramLaterRecord> before = XgramLaterEngine.markAllDueDone(tdlib.id());
      if (!before.isEmpty()) {
        List<String> keys = new ArrayList<>(before.size());
        for (XgramLaterRecord r : before) {
          keys.add(r.key);
        }
        if (!XgramSnackbar.showUndo(Lang.getString(R.string.XgramLaterMarkedDone), () -> XgramLaterEngine.restore(tdlib.id(), keys, before))) {
          UI.showToast(R.string.XgramLaterMarkedDone, android.widget.Toast.LENGTH_SHORT); // classic: no Undo, a toast as every other Later action
        }
      }
    } else if (id == R.id.btn_xgramLaterMClearDone) {
      showOptions(Lang.getString(R.string.XgramLaterClearDoneConfirm), new int[] {R.id.btn_delete, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.XgramLaterClearDone), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.RED, OptionColor.NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_delete) {
            XgramLaterEngine.clearDone(tdlib.id());
          }
          return true;
        });
    } else if (id == R.id.btn_xgramLaterMSettings) {
      navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.open(context(), tdlib, com.fahadali.xgram.XgramSettingsController.SECTION_ACTIVITY, R.id.btn_xgramLaterRow));
    }
  }
}
