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
package com.fahadali.xgram.activity;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.XgramSettingsController;
import com.fahadali.xgram.appearance.XgramActivityChips;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5People;
import com.fahadali.xgram.ghost.XgramGhost;
import com.fahadali.xgram.layout.XgramTwoPane;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ToggleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.DateChangeListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.ToggleDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tgx.td.MessageId;

/**
 * The Activity screen: every mention of you, reply to your messages and reaction to them, newest first, in sections
 * (Today, Yesterday, This week, Earlier). Material 3 Expressive with Expressive design (a large title, filter chips,
 * status tiles, M3 list tiles), Telegram X's look otherwise (a dropdown title, description rows, BetterChatView
 * rows). A tap reads the row here and opens its chat at the message (on the right with two panes); nothing is sent
 * from this list itself.
 */
public final class XgramActivityController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener,
  XgramActivityStore.ChangeListener, XgramActivityBadge.Listener, XgramActivity.DisplayListener, XgramSettings.Listener,
  MoreDelegate, ToggleDelegate, XgramActivityChips.Listener, XgramActivityRowView.SelectionProvider, DateChangeListener {

  // Custom rows (TYPE_CUSTOM - n)
  static final int ROW = 9061, CHIPS = 9062, STATUS = 9063, OFF_ROW = 9065;
  private static final int PAGE = 100;
  private static final String KEY_FILTER = "xgram_actview_filter"; // per viewer, not a setting: never backed up

  // Per account, across rebuilds (the activity made again: Expressive design, a language direction): where the list
  // was, and the item opened last

  static final class UiState {
    int offset;
    @Nullable String anchorKey;
    /** The item opened last (its row is selected while its chat is on the right). */
    @Nullable String lastOpenedKey;
    /** The last screen went with its activity (a rebuild): the next one comes back where it was. Otherwise it opens at the top. */
    boolean restorePending;
  }

  private static final SparseArray<UiState> uiStates = new SparseArray<>();

  @NonNull
  static UiState uiState (int accountId) {
    UiState state = uiStates.get(accountId);
    if (state == null) {
      state = new UiState();
      uiStates.put(accountId, state);
    }
    return state;
  }

  static void forgetAccount (int accountId) {
    UI.post(() -> uiStates.remove(accountId));
  }

  public XgramActivityController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramActivity;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramActivity);
  }

  // Filter (remembered per viewer)

  private int filter = XgramActivityStore.FILTER_ALL;
  private boolean unreadOnly;

  private void loadFilter () {
    int value = Settings.instance().pmc().getInt(KEY_FILTER, 0);
    filter = Math.max(XgramActivityStore.FILTER_ALL, Math.min(XgramActivityStore.FILTER_REACTIONS, value & 0xf));
    unreadOnly = (value & 0x10) != 0;
    if (filter == XgramActivityStore.FILTER_REACTIONS && !XgramActivity.reactions()) {
      filter = XgramActivityStore.FILTER_ALL;
    }
  }

  private void saveFilter () {
    Settings.instance().pmc().putInt(KEY_FILTER, filter | (unreadOnly ? 0x10 : 0));
  }

  private void setFilter (int filter) {
    if (this.filter != filter) {
      this.filter = filter;
      saveFilter();
      limit = PAGE;
      updateHeaderTitle();
      updateMarkAllButton();
      reload(true);
    }
  }

  private void setUnreadOnly (boolean unreadOnly) {
    if (this.unreadOnly != unreadOnly) {
      this.unreadOnly = unreadOnly;
      saveFilter();
      limit = PAGE;
      reload(true);
    }
  }

  // List

  private SettingsAdapter adapter;
  @Nullable private XgramActivityChips chips;
  @NonNull private List<XgramActivityRows.Row> rows = Collections.emptyList();
  private boolean loaded, loading, reloadQueued, endReached;
  private int limit = PAGE;
  private int requestId;
  private boolean restoreScroll;
  /** Rows a page should show at least: fewer (grouping, muted topics) and there are more, the next page comes at once. */
  private static final int MIN_PAGE_ROWS = 20;
  private final XgramActivityUndoBar classicUndo = new XgramActivityUndoBar(this);

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    loadFilter();
    UiState state = uiState(tdlib.id());
    restoreScroll = state.restorePending;
    state.restorePending = false;
    adapter = new SettingsAdapter(this) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        switch (customViewType) {
          case ROW: {
            XgramActivityRowView view = new XgramActivityRowView(parent.getContext(), tdlib);
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            RippleSupport.setSimpleWhiteBackground(view, XgramActivityController.this);
            view.setOnClickListener(XgramActivityController.this);
            view.setOnLongClickListener(XgramActivityController.this);
            view.setSelectionProvider(XgramActivityController.this);
            addThemeInvalidateListener(view);
            return new SettingHolder(view);
          }
          case CHIPS: {
            XgramActivityChips view = new XgramActivityChips(parent.getContext());
            view.setListener(XgramActivityController.this);
            chips = view;
            return new SettingHolder(view);
          }
          case STATUS: {
            XgramActivityStatusView view = new XgramActivityStatusView(parent.getContext());
            addThemeInvalidateListener(view);
            return new SettingHolder(view);
          }
          case OFF_ROW:
            return new SettingHolder(XgramR5People.newStateViewWithAction(parent.getContext(), Lang.getString(R.string.XgramActivityTurnOn), v -> turnOn()));
          case XgramR5People.STATE_ROW:
          case XgramR5People.LOADING_ROW:
            return new SettingHolder(XgramR5People.newStateView(parent.getContext()));
        }
        return super.initCustom(parent, customViewType);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        switch (customViewType) {
          case ROW: {
            if (item.getData() instanceof XgramActivityRows.Row) {
              ((XgramActivityRowView) view).setRow((XgramActivityRows.Row) item.getData());
            }
            break;
          }
          case CHIPS: {
            chips = (XgramActivityChips) view;
            bindChips((XgramActivityChips) view);
            break;
          }
          case STATUS: {
            if (item.getData() instanceof Status) {
              Status status = (Status) item.getData();
              ((XgramActivityStatusView) view).setStatus(status.icon, status.text, status.progress);
            }
            break;
          }
          case OFF_ROW:
            XgramR5People.bindState(view, true, R.drawable.baseline_notifications_24, Lang.getString(R.string.XgramActivityOff), null);
            break;
          case XgramR5People.STATE_ROW:
            XgramR5People.bindState(view, false, R.drawable.baseline_notifications_24, emptyTitle(), emptyText());
            break;
          case XgramR5People.LOADING_ROW:
            XgramR5People.bindState(view, false, 0, null, null);
            break;
          default:
            super.modifyCustom(holder, position, item, customViewType, view, isUpdate);
            break;
        }
      }
    };
    buildCells();
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager != null && loaded && !loading && !endReached && manager.findLastVisibleItemPosition() >= adapter.getItems().size() - 8) {
          limit += PAGE;
          reload(false);
        }
        onListScrolled(dy);
      }
    });

    // Swipe from the end to clear a row (Undo puts it back)
    org.thunderdog.challegram.component.user.RemoveHelper.attach(recyclerView, new org.thunderdog.challegram.component.user.RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        return viewHolder.itemView instanceof XgramActivityRowView && ((XgramActivityRowView) viewHolder.itemView).getRow() != null;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        if (viewHolder.itemView instanceof XgramActivityRowView) {
          XgramActivityRows.Row row = ((XgramActivityRowView) viewHolder.itemView).getRow();
          if (row != null && !isDestroyed()) {
            clearRow(row);
          }
        }
      }
    });

    XgramActivityStore.instance().addListener(this);
    XgramActivityBadge.instance().addListener(this);
    XgramActivity.addDisplayListener(this);
    XgramSettings.instance().addListener(this);
    tdlib.context().dateManager().addListener(this);
    reload(false);
  }

  /** Back at the top: the new items pill has nothing to point at. */
  private void onListScrolled (int dy) {
    if (newPillCount > 0 && isAtTop()) {
      setNewPill(0);
    }
  }

  private boolean isAtTop () {
    CustomRecyclerView recyclerView = getRecyclerView();
    LinearLayoutManager manager = recyclerView != null ? (LinearLayoutManager) recyclerView.getLayoutManager() : null;
    return manager == null || manager.findFirstVisibleItemPosition() <= 0;
  }

  // The new items pill (Expressive design): rows that came in above while the list is scrolled down. Telegram X's look
  // keeps the position and shows nothing.

  @Nullable private android.widget.TextView newPill;
  @Nullable private XgramExpressive.Spring newPillSpring;
  private int newPillCount;

  private void setNewPill (int count) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    newPillCount = count;
    if (count > 0) {
      android.widget.TextView pill = ensureNewPill();
      if (pill == null) {
        return;
      }
      pill.setText(count == 1 ? Lang.getString(R.string.XgramActivityNewPillOne) : Lang.getString(R.string.XgramActivityNewPillMany, count));
      pill.setTextColor(XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER));
      pill.setVisibility(View.VISIBLE);
    }
    if (newPillSpring != null) {
      newPillSpring.animateTo(count > 0 ? 1f : 0f);
    }
  }

  @Nullable
  private android.widget.TextView ensureNewPill () {
    if (newPill != null) {
      return newPill;
    }
    View value = getValue();
    if (!(value instanceof android.widget.FrameLayout)) {
      return null;
    }
    android.widget.TextView pill = new android.widget.TextView(context);
    XgramExpressive.applyText(pill, XgramExpressive.TS_LABEL_L, false);
    pill.setGravity(android.view.Gravity.CENTER);
    int padding = Screen.dp(16f);
    pill.setPadding(padding, 0, padding, 0);
    pill.setSingleLine(true);
    XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, Float.NaN);
    background.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
    background.setHost(pill);
    pill.setBackground(background);
    pill.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
    pill.setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
    pill.setOnClickListener(v -> {
      setNewPill(0);
      scrollToTopOrReload();
    });
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f), android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
    params.topMargin = Screen.dp(12f);
    ((android.widget.FrameLayout) value).addView(pill, params);
    pill.setVisibility(View.GONE);
    newPill = pill;
    newPillSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, v) -> {
      float scale = .6f + .4f * Math.max(0f, v);
      pill.setScaleX(scale);
      pill.setScaleY(scale);
      pill.setAlpha(Math.max(0f, Math.min(1f, v)));
      if (v <= 0f && newPillCount == 0) {
        pill.setVisibility(View.GONE);
      }
    });
    return pill;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return !loaded;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 400L;
  }

  /** Asks the store for the rows; the list is rebuilt when they come. */
  private void reload (boolean scrollToTop) {
    if (adapter == null || isDestroyed()) {
      return;
    }
    if (loading) {
      reloadQueued = true;
      replaceQueued |= scrollToTop;
      return;
    }
    if (!XgramActivity.isOn()) {
      loaded = true;
      rows = Collections.emptyList();
      buildCells(scrollToTop);
      executeScheduledAnimation();
      return;
    }
    loading = true;
    final int request = ++requestId;
    final int requestedLimit = limit;
    // Muted chats (off) is left out by the query itself, so the limit counts rows that are shown
    final long[][] muted = XgramActivityRows.mutedChats(tdlib, XgramActivityStore.instance().summary(tdlib.id()).knownChats());
    XgramActivityStore.instance().query(tdlib.id(), filter, unreadOnly, XgramActivity.reactions(), XgramActivity.discussion(),
      muted != null ? muted[0] : null, muted != null ? muted[1] : null, requestedLimit, items -> {
      if (isDestroyed() || request != requestId) {
        return;
      }
      loading = false;
      loaded = true;
      endReached = items.size() < requestedLimit;
      List<XgramActivityItem> visible = new ArrayList<>(items.size());
      for (XgramActivityItem item : items) {
        if (XgramActivityRows.visible(tdlib, item)) {
          visible.add(item);
        }
      }
      rows = XgramActivityRows.group(visible);
      applyRows(scrollToTop);
      executeScheduledAnimation();
      if (!endReached && rows.size() < MIN_PAGE_ROWS && limit < XgramActivityStore.ROW_CAP) {
        // Too few rows to scroll (grouped replies, muted topics), so no scroll would ask for more: the next page now
        limit += PAGE;
        reloadQueued = true;
      }
      if (reloadQueued) {
        final boolean replace = replaceQueued;
        reloadQueued = false;
        replaceQueued = false;
        reload(replace);
      }
    });
  }

  private boolean replaceQueued;

  private boolean rebuildPosted;
  private final java.util.HashSet<String> shownKeys = new java.util.HashSet<>();
  private int newestShownDate;

  /**
   * Puts the rows in. Only what changed is told to the list (new rows animate in, the rest stays where it is on
   * screen); a filter change, the first rows and a language change replace everything and go to the top.
   */
  private void applyRows (boolean scrollToTop) {
    CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView != null && !scrollToTop && (recyclerView.isComputingLayout() || isSwiping())) {
      // A swipe or a layout pass in progress: after it
      if (!rebuildPosted) {
        rebuildPosted = true;
        recyclerView.postDelayed(() -> {
          rebuildPosted = false;
          if (!isDestroyed()) {
            applyRows(false);
          }
        }, 120L);
      }
      return;
    }
    LinearLayoutManager manager = recyclerView != null ? (LinearLayoutManager) recyclerView.getLayoutManager() : null;
    final boolean atTop = scrollToTop || manager == null || manager.findFirstVisibleItemPosition() <= 0;
    // Rows newer than everything shown so far: the pill counts them while the list is scrolled down
    int newcomers = 0;
    if (!shownKeys.isEmpty()) {
      for (XgramActivityRows.Row row : rows) {
        if (row.eventDate() < newestShownDate) {
          break;
        }
        if (!shownKeys.contains(row.key())) {
          newcomers++;
        }
      }
    }
    shownKeys.clear();
    newestShownDate = 0;
    for (XgramActivityRows.Row row : rows) {
      shownKeys.add(row.key());
      newestShownDate = Math.max(newestShownDate, row.eventDate());
    }
    final boolean firstRows = !rowsShown;
    rowsShown = true;
    buildCells(scrollToTop || firstRows);
    if (!atTop && newcomers > 0) {
      setNewPill(newPillCount + newcomers);
    } else if (atTop && newPillCount > 0) {
      setNewPill(0);
    }
    if (manager == null) {
      return;
    }
    if (restoreScroll && firstRows) {
      // A rebuild of the screen (the activity was made again): back where the last one was
      restoreScroll = false;
      UiState state = uiState(tdlib.id());
      int index = state.anchorKey != null ? indexOfRow(state.anchorKey) : -1;
      if (index != -1) {
        manager.scrollToPositionWithOffset(index, state.offset);
      }
      return;
    }
    if (scrollToTop) {
      manager.scrollToPositionWithOffset(0, 0);
    }
  }

  /** The rows of the store were put in once (before that, the list shows its loading row). */
  private boolean rowsShown;

  private int indexOfRow (String key) {
    List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      Object data = items.get(i).getData();
      if (data instanceof XgramActivityRows.Row && ((XgramActivityRows.Row) data).contains(key)) {
        return i;
      }
    }
    return -1;
  }

  /** A status tile (Expressive design) or note (Telegram X's look). */
  static final class Status {
    /** Its place above the list ("sync" or "ghost"): the same tile is updated in place. */
    final String key;
    final int icon;
    final CharSequence text;
    final float progress;

    Status (String key, int icon, CharSequence text, float progress) {
      this.key = key;
      this.icon = icon;
      this.text = text;
      this.progress = progress;
    }

    String signature () {
      return icon + ":" + text + ":" + Math.round(progress * 1000f);
    }
  }

  /** The notes above the list: the sync's status (one at most), then ghost mode. */
  @NonNull
  private List<Status> statuses () {
    List<Status> result = new ArrayList<>();
    XgramActivityStore.SyncStatus sync = XgramActivityStore.instance().summary(tdlib.id()).sync;
    if (sync.isPaused()) {
      long seconds = Math.max(1, (sync.pausedUntil - System.currentTimeMillis() + 999) / 1000L);
      String wait = seconds < 60 ? Lang.plural(R.string.xSeconds, seconds) : seconds < 3600 ? Lang.plural(R.string.xMinutes, (seconds + 59) / 60) : Lang.plural(R.string.xHours, (seconds + 3599) / 3600);
      result.add(new Status("sync", 0, Lang.getString(R.string.XgramActivityPaused, wait), -1f));
      scheduleStatusRefresh();
    } else if (sync.running && sync.offline) {
      result.add(new Status("sync", 0, Lang.getString(R.string.XgramActivityOffline), -1f));
    } else if (sync.running && sync.total >= 2) {
      result.add(new Status("sync", 0, Lang.getString(R.string.XgramActivityCatchingUp, Math.min(sync.done, sync.total), sync.total), (float) Math.min(sync.done, sync.total) / sync.total));
    }
    if (XgramGhost.dontReadMessages()) {
      result.add(new Status("ghost", R.drawable.baseline_ghost_24, Lang.getString(R.string.XgramActivityGhostNote), -1f));
    }
    return result;
  }

  // The paused tile counts down
  private boolean statusRefreshPosted;
  private final Runnable statusRefresh = () -> {
    statusRefreshPosted = false;
    if (!isDestroyed() && adapter != null && loaded) {
      applyRows(false);
    }
  };

  private void scheduleStatusRefresh () {
    if (!statusRefreshPosted) {
      statusRefreshPosted = true;
      UI.post(statusRefresh, 30_000L);
    }
  }

  /** The list's items with, for each, who it is across rebuilds (its key) and what it shows (its signature). */
  private static final class Cells {
    final List<ListItem> items = new ArrayList<>();
    final List<String> keys = new ArrayList<>(), signatures = new ArrayList<>();

    void add (ListItem item, String key, String signature) {
      items.add(item);
      keys.add(key);
      signatures.add(signature);
    }
  }

  /** The keys and signatures of what the adapter holds now, in its order. */
  private final List<String> cellKeys = new ArrayList<>(), cellSignatures = new ArrayList<>();
  private boolean cellsBuilt;
  /** A changed item is bound again in its own holder: rows keep their layout and springs, the chips their scroll. */
  private static final Object PAYLOAD_REBIND = new Object();

  private void buildCells () {
    buildCells(false);
  }

  /** {@code replace}: everything is new (the first rows, a filter, a language), otherwise only what changed is told. */
  private void buildCells (boolean replace) {
    final boolean expressive = XgramExpressive.isEnabled();
    final Cells cells = new Cells();
    if (!XgramActivity.isOn()) {
      if (expressive) {
        cells.add(new ListItem(ListItem.TYPE_CUSTOM - OFF_ROW), "off", "");
      } else {
        cells.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL), "off:offset", "");
        cells.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramActivityOff), "off:text", Lang.getString(R.string.XgramActivityOff));
        cells.add(new ListItem(ListItem.TYPE_SHADOW_TOP), "off:top", "");
        cells.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramActivityTurnOn, R.drawable.baseline_notifications_24, R.string.XgramActivityTurnOn), "off:button", Lang.getString(R.string.XgramActivityTurnOn));
        cells.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM), "off:bottom", "");
      }
      applyCells(cells, replace);
      return;
    }
    if (expressive) {
      cells.add(new ListItem(ListItem.TYPE_CUSTOM - CHIPS), "chips", filter + ":" + unreadOnly + ":" + XgramActivity.reactions());
    } else {
      cells.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL), "offset", "");
    }
    for (Status status : statuses()) {
      if (expressive) {
        cells.add(new ListItem(ListItem.TYPE_CUSTOM - STATUS).setData(status), "status:" + status.key, status.signature());
      } else {
        cells.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, status.text, false), "status:" + status.key, status.text.toString());
      }
    }
    final XgramActivityStore.Summary summary = XgramActivityStore.instance().summary(tdlib.id());
    if (!loaded || (rows.isEmpty() && summary.sync.running && !summary.hasAny)) {
      // Loading, or the first catch-up still running with nothing stored yet
      cells.add(expressive ? new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.LOADING_ROW) : new ListItem(ListItem.TYPE_PROGRESS), "loading", "");
    } else if (rows.isEmpty()) {
      final String text = emptyText();
      if (expressive) {
        cells.add(new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.STATE_ROW), "empty", emptyTitle() + ":" + text);
      } else {
        cells.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, emptyTitle(), false), "empty", emptyTitle());
      }
    } else {
      final long now = XgramActivityStore.nowSeconds();
      int section = -1;
      boolean first = true;
      for (XgramActivityRows.Row row : rows) {
        int rowSection = XgramActivityClassifier.sectionOf(row.eventDate(), now);
        if (rowSection != section) {
          if (section != -1) {
            cells.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM), "bottom:" + section, "");
          }
          section = rowSection;
          final String name = sectionName(rowSection);
          cells.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, name, false), "header:" + rowSection, name);
          cells.add(new ListItem(ListItem.TYPE_SHADOW_TOP), "top:" + rowSection, "");
          first = true;
        }
        final String rowKey = row.stableKey();
        if (!first) {
          cells.add(new ListItem(ListItem.TYPE_SEPARATOR), "separator:" + rowKey, "");
        }
        first = false;
        cells.add(new ListItem(ListItem.TYPE_CUSTOM - ROW).setData(row), "row:" + rowKey, row.signature());
      }
      cells.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM), "bottom:" + section, ""); // every page closes its groups
    }
    applyCells(cells, replace);
  }

  /**
   * Gives the adapter the new items: everything at once when {@code replace} (or nothing to compare with), otherwise
   * only the differences (DiffUtil): rows in and out with their own animation, changed ones bound again in place.
   */
  private void applyCells (@NonNull Cells cells, boolean replace) {
    final CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView != null && recyclerView.isComputingLayout()) {
      // Not during a layout pass: right after it
      replacePending |= replace;
      if (!applyPosted) {
        applyPosted = true;
        recyclerView.post(() -> {
          applyPosted = false;
          final boolean pending = replacePending;
          replacePending = false;
          if (!isDestroyed() && adapter != null) {
            buildCells(pending);
          }
        });
      }
      return;
    }
    final List<ListItem> current = adapter.getItems();
    if (replace || !cellsBuilt || recyclerView == null || current.isEmpty() || cells.items.isEmpty() || current.size() != cellKeys.size()) {
      adapter.setItems(cells.items, false);
    } else {
      final List<String> oldKeys = new ArrayList<>(cellKeys), oldSignatures = new ArrayList<>(cellSignatures);
      final List<ListItem> oldItems = new ArrayList<>(current);
      final DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
        @Override
        public int getOldListSize () {
          return oldKeys.size();
        }

        @Override
        public int getNewListSize () {
          return cells.keys.size();
        }

        @Override
        public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
          return oldKeys.get(oldItemPosition).equals(cells.keys.get(newItemPosition)) &&
            oldItems.get(oldItemPosition).getViewType() == cells.items.get(newItemPosition).getViewType();
        }

        @Override
        public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
          return oldSignatures.get(oldItemPosition).equals(cells.signatures.get(newItemPosition));
        }

        @Nullable
        @Override
        public Object getChangePayload (int oldItemPosition, int newItemPosition) {
          return PAYLOAD_REBIND;
        }
      }, true);
      current.clear();
      current.addAll(cells.items);
      diff.dispatchUpdatesTo(adapter);
    }
    cellKeys.clear();
    cellKeys.addAll(cells.keys);
    cellSignatures.clear();
    cellSignatures.addAll(cells.signatures);
    cellsBuilt = true;
    if (recyclerView != null) {
      recyclerView.invalidateItemDecorations();
    }
  }

  private boolean applyPosted, replacePending;

  @NonNull
  private static String sectionName (int section) {
    switch (section) {
      case XgramActivityClassifier.SECTION_TODAY:
        return Lang.getString(R.string.Today);
      case XgramActivityClassifier.SECTION_YESTERDAY:
        return Lang.getString(R.string.Yesterday);
      case XgramActivityClassifier.SECTION_THIS_WEEK:
        return Lang.getString(R.string.XgramActivityThisWeek);
      case XgramActivityClassifier.SECTION_EARLIER:
      default:
        return Lang.getString(R.string.XgramActivityEarlier);
    }
  }

  @NonNull
  private String emptyTitle () {
    if (unreadOnly) {
      return Lang.getString(R.string.XgramActivityCaughtUp);
    }
    switch (filter) {
      case XgramActivityStore.FILTER_MENTIONS:
        return Lang.getString(R.string.XgramActivityEmptyMentions);
      case XgramActivityStore.FILTER_REPLIES:
        return Lang.getString(R.string.XgramActivityEmptyReplies);
      case XgramActivityStore.FILTER_REACTIONS:
        return Lang.getString(R.string.XgramActivityEmptyReactions);
      default:
        return Lang.getString(R.string.XgramActivityEmpty);
    }
  }

  @Nullable
  private String emptyText () {
    return filter == XgramActivityStore.FILTER_ALL && !unreadOnly ? Lang.getString(R.string.XgramActivityEmptyText) : null;
  }

  private void bindChips (XgramActivityChips view) {
    view.setReactionsShown(XgramActivity.reactions());
    view.setSelectedFilter(filter, false);
    view.setUnread(unreadOnly, false);
    view.setCounts(new int[] {
      0,
      XgramActivityBadge.count(tdlib, XgramActivityStore.FILTER_MENTIONS),
      XgramActivityBadge.count(tdlib, XgramActivityStore.FILTER_REPLIES),
      XgramActivityBadge.count(tdlib, XgramActivityStore.FILTER_REACTIONS)
    });
  }

  /** A row is being swiped: the list waits with rebuilding until it is let go. */
  private boolean isSwiping () {
    CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView == null) {
      return false;
    }
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View child = recyclerView.getChildAt(i);
      if (child instanceof XgramActivityRowView && ((XgramActivityRowView) child).isSwiping()) {
        return true;
      }
    }
    return false;
  }

  /** Back at the top (the rail, the header bell or the side menu again while it is on screen). */
  public void scrollToTopOrReload () {
    CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView == null) {
      return;
    }
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (manager != null && manager.findFirstVisibleItemPosition() > 20) {
      manager.scrollToPositionWithOffset(0, 0);
    } else {
      recyclerView.smoothScrollToPosition(0);
    }
  }

  /** Its tab tapped again in the navigation bar or the toolbar: the same jump as the rail and the bell. */
  @Override
  public void onScrollToTopRequested () {
    scrollToTopOrReload();
  }

  // Chips

  @Override
  public void onFilterSelected (int filter) {
    setFilter(filter);
  }

  @Override
  public void onUnreadToggled (boolean unread) {
    setUnreadOnly(unread);
  }

  // Classic title: a dropdown

  @Nullable private ToggleHeaderView headerCell;

  @Override
  public View getCustomHeaderCell () {
    if (XgramExpressive.isEnabled()) {
      return null; // Expressive design: the large title
    }
    if (headerCell == null) {
      NavigationController navigation = navigationController();
      HeaderView header = navigation != null ? navigation.getHeaderView() : context.navigation().getHeaderView();
      if (header == null) {
        return null;
      }
      headerCell = header.genToggleTitle(context(), this);
    }
    updateHeaderTitle();
    return headerCell;
  }

  private void updateHeaderTitle () {
    if (headerCell != null) {
      headerCell.setText(filterTitle(filter));
    }
  }

  @NonNull
  private static String filterTitle (int filter) {
    switch (filter) {
      case XgramActivityStore.FILTER_MENTIONS:
        return Lang.getString(R.string.XgramActivityMentions);
      case XgramActivityStore.FILTER_REPLIES:
        return Lang.getString(R.string.XgramActivityReplies);
      case XgramActivityStore.FILTER_REACTIONS:
        return Lang.getString(R.string.XgramActivityReactions);
      default:
        return Lang.getString(R.string.XgramActivityAllTitle);
    }
  }

  @Override
  public String[] getToggleSections () {
    if (XgramActivity.reactions()) {
      return new String[] {filterTitle(0), filterTitle(1), filterTitle(2), filterTitle(3)};
    }
    return new String[] {filterTitle(0), filterTitle(1), filterTitle(2)};
  }

  @Override
  public void onToggle (int section) {
    setFilter(section);
  }

  // Header buttons

  @Override
  protected int getMenuId () {
    return R.id.menu_xgramActivity;
  }

  @Nullable private org.thunderdog.challegram.navigation.HeaderButton markAllButton;

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_xgramActivity) {
      markAllButton = header.addButton(menu, R.id.menu_btn_xgramActivityMarkAll, R.drawable.baseline_done_all_24, getHeaderIconColorId(), this, Screen.dp(49f));
      updateMarkAllButton();
      header.addMoreButton(menu, this);
    } else {
      super.fillMenuItems(id, header, menu);
    }
  }

  /** Mark all as read says what it reads: the filter's kind. */
  private void updateMarkAllButton () {
    if (markAllButton == null) {
      return;
    }
    final int label;
    switch (filter) {
      case XgramActivityStore.FILTER_MENTIONS:
        label = R.string.XgramActivityMarkAllMentions;
        break;
      case XgramActivityStore.FILTER_REPLIES:
        label = R.string.XgramActivityMarkAllReplies;
        break;
      case XgramActivityStore.FILTER_REACTIONS:
        label = R.string.XgramActivityMarkAllReactions;
        break;
      default:
        label = R.string.XgramActivityMarkAllRead;
        break;
    }
    markAllButton.setContentDescription(Lang.getString(label));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      markAllButton.setTooltipText(Lang.getString(label));
    }
    markAllButton.setVisibility(XgramActivity.isOn() ? View.VISIBLE : View.GONE);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_xgramActivityMarkAll) {
      markAll();
    } else if (id == org.thunderdog.challegram.R.id.menu_btn_more) {
      showMoreMenu();
    } else {
      super.onMenuItemPressed(id, view);
    }
  }

  private void showMoreMenu () {
    final boolean expressive = XgramExpressive.isEnabled();
    int[] ids = {R.id.btn_xgramActivityUnreadOnly, R.id.btn_xgramActivityClearRead, R.id.btn_xgramActivityHidden, R.id.btn_xgramActivitySettings};
    String[] titles = {
      Lang.getString(R.string.XgramActivityUnreadOnly), Lang.getString(R.string.XgramActivityClearRead),
      Lang.getString(R.string.XgramActivityHiddenChats), Lang.getString(R.string.XgramActivitySettings)
    };
    if (expressive) {
      showMore(ids, titles, 0);
      org.thunderdog.challegram.widget.PopupLayout popup = context().getCurrentPopupWindow();
      if (popup != null && popup.getBoundView() instanceof org.thunderdog.challegram.navigation.MenuMoreWrap) {
        ((org.thunderdog.challegram.navigation.MenuMoreWrap) popup.getBoundView()).xgramSetCheckable(R.id.btn_xgramActivityUnreadOnly, unreadOnly);
      }
    } else {
      int[] icons = {
        unreadOnly ? R.drawable.baseline_check_24 : R.drawable.baseline_check_box_outline_blank_24, R.drawable.baseline_delete_sweep_24,
        R.drawable.baseline_eye_off_24, R.drawable.baseline_settings_24
      };
      showMore(ids, titles, icons, 0);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_xgramActivityUnreadOnly) {
      setUnreadOnly(!unreadOnly);
      if (chips != null) {
        chips.setUnread(unreadOnly, true);
      }
    } else if (id == R.id.btn_xgramActivityClearRead) {
      clearRead();
    } else if (id == R.id.btn_xgramActivityHidden) {
      openScreen(new XgramActivityHiddenController(context, tdlib));
    } else if (id == R.id.btn_xgramActivitySettings) {
      openScreen(com.fahadali.xgram.compose.XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_CHAT_LIST, R.id.btn_xgramActivityShow));
    }
  }

  // Actions (each with Undo, except opening and reading one row)

  /** A snackbar with Undo: Material 3 with Expressive design, Telegram X's SnackBar otherwise. */
  void showUndo (@NonNull String text, @NonNull Runnable onUndo) {
    if (XgramExpressive.isEnabled()) {
      if (!com.fahadali.xgram.appearance.XgramSnackbar.showUndo(text, onUndo)) {
        UI.showToast(text, Toast.LENGTH_SHORT);
      }
      return;
    }
    if (!classicUndo.show(text, onUndo, extraBottomInset)) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  /** Clear: the rows go (tombstones, so nothing brings them back), Undo puts them back as they were. */
  private void clearRow (@NonNull XgramActivityRows.Row row) {
    final List<XgramActivityItem> items = new ArrayList<>(row.items);
    final XgramActivityStore store = XgramActivityStore.instance();
    store.clearItems(tdlib.id(), items);
    removeRowNow(row);
    showUndo(Lang.getString(R.string.XgramActivityCleared), () -> store.restoreItems(tdlib.id(), items));
  }

  /** Takes a row off the list at once (the store's change follows). */
  private void removeRowNow (@NonNull XgramActivityRows.Row row) {
    List<XgramActivityRows.Row> next = new ArrayList<>(rows);
    if (next.remove(row)) {
      rows = next;
      buildCells();
    }
  }

  /** Clear read items: the Read rows this filter shows (Reactions, Channel comments and Muted chats as set), with Undo. */
  private void clearRead () {
    final XgramActivityStore store = XgramActivityStore.instance();
    store.queryRead(tdlib.id(), filter, XgramActivity.reactions(), XgramActivity.discussion(), read -> {
      if (isDestroyed()) {
        return;
      }
      final List<XgramActivityItem> cleared = new ArrayList<>(read.size());
      for (XgramActivityItem item : read) {
        if (XgramActivityRows.visible(tdlib, item)) {
          cleared.add(item);
        }
      }
      if (cleared.isEmpty()) {
        showMessage(Lang.getString(R.string.XgramActivityNothingRead), null, null);
        return;
      }
      store.clearReadItems(tdlib.id(), cleared);
      String text = cleared.size() == 1 ? Lang.getString(R.string.XgramActivityClearedOne) : Lang.getString(R.string.XgramActivityClearedMany, cleared.size());
      showUndo(text, () -> store.restoreItems(tdlib.id(), cleared));
    });
  }

  private void hideChat (@NonNull XgramActivityItem item) {
    final long chatId = item.chatId;
    final String title = chatTitleOf(item);
    final XgramActivityStore store = XgramActivityStore.instance();
    store.setChatHidden(tdlib.id(), chatId, true);
    List<XgramActivityRows.Row> next = new ArrayList<>();
    for (XgramActivityRows.Row row : rows) {
      if (row.newest().chatId != chatId) {
        next.add(row);
      }
    }
    rows = next;
    buildCells();
    showUndo(Lang.getString(R.string.XgramActivityChatHidden, title), () -> store.setChatHidden(tdlib.id(), chatId, false));
  }

  @NonNull
  private String chatTitleOf (@NonNull XgramActivityItem item) {
    if (tdlib.chat(item.chatId) != null) {
      String title = tdlib.chatTitle(item.chatId);
      if (title != null && !title.isEmpty()) {
        return title;
      }
    }
    return item.chatTitle != null ? item.chatTitle : "";
  }

  private void toggleRead (@NonNull XgramActivityRows.Row row) {
    if (row.isNew()) {
      XgramActivityRead.markOne(tdlib, row);
    } else {
      // Read on the server: New here only, and kept so (counters and searches leave it alone). Items read on this
      // device only (ghost mode) are still unread there: plainly New again.
      List<XgramActivityItem> readHere = new ArrayList<>(), readThere = new ArrayList<>();
      for (XgramActivityItem item : row.items) {
        (item.hasFlag(XgramActivityItem.FLAG_READ_LOCAL) ? readHere : readThere).add(item);
      }
      if (!readHere.isEmpty()) {
        XgramActivityStore.instance().markItemsUnread(tdlib.id(), readHere, false);
      }
      if (!readThere.isEmpty()) {
        XgramActivityStore.instance().markItemsUnread(tdlib.id(), readThere, true);
      }
    }
  }

  // Mark all as read

  private void markAll () {
    if (!XgramActivity.isOn()) {
      return;
    }
    final XgramActivityRead.Plan plan = XgramActivityRead.plan(tdlib, XgramActivityRows.itemsOf(rows), filter);
    if (plan.unread.isEmpty()) {
      showMessage(Lang.getString(R.string.XgramActivityCaughtUp), null, null);
      return;
    }
    if (plan.serverChats() == 0) {
      // Ghost mode applies to every chat: read on this device only, nothing sent, so it can be undone (the rows, and
      // the chat list's @ badges ghost mode lowered for them)
      final List<XgramActivityItem> unread = new ArrayList<>(plan.unread);
      final XgramActivityRead.LocalMarks marks = XgramActivityRead.run(tdlib, plan, null);
      showUndo(Lang.getString(R.string.XgramActivityReadLocally), () -> {
        marks.undo();
        XgramActivityStore.instance().markItemsUnread(tdlib.id(), unread, false); // still unread on the server
      });
      return;
    }
    final int chats = plan.chats.size();
    StringBuilder info = new StringBuilder(Lang.getString(R.string.XgramActivityMarkAllConfirm)).append("\n\n");
    info.append(chats == 1 ? Lang.getString(R.string.XgramActivityMarkAllTextOne) : Lang.getString(R.string.XgramActivityMarkAllTextMany, chats));
    if (filter == XgramActivityStore.FILTER_MENTIONS || filter == XgramActivityStore.FILTER_REPLIES) {
      info.append(' ').append(Lang.getString(R.string.XgramActivityMarkAllTogether));
    }
    if (plan.ghosted > 0) {
      info.append(' ').append(plan.ghosted == 1 ? Lang.getString(R.string.XgramActivityMarkAllMixedOne) : Lang.getString(R.string.XgramActivityMarkAllMixedMany, plan.ghosted));
    }
    showOptions(info.toString(), new int[] {R.id.btn_xgramActivityMarkAllConfirm, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramActivityMarkAllButton), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.BLUE, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_done_all_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_xgramActivityMarkAllConfirm && !isDestroyed()) {
          runMarkAll(plan);
        }
        return true;
      });
  }

  private void runMarkAll (@NonNull XgramActivityRead.Plan plan) {
    final int serverChats = Math.min(plan.serverChats(), XgramActivityRead.MAX_CHATS);
    if (serverChats >= 30) {
      showMessage(Lang.getString(R.string.XgramActivityMarking, serverChats), null, null);
    }
    XgramActivityRead.run(tdlib, plan, (stoppedChats, flood) -> {
      if (flood && stoppedChats > 0) {
        showMessage(stoppedChats == 1 ? Lang.getString(R.string.XgramActivityMarkPartialOne) : Lang.getString(R.string.XgramActivityMarkPartialMany, stoppedChats), null, null);
      } else if (stoppedChats > 0) {
        // More chats than one tap reads: those stay unread, here too, for the next tap
        showMessage(stoppedChats == 1 ? Lang.getString(R.string.XgramActivityMarkLeftOne) : Lang.getString(R.string.XgramActivityMarkLeftMany, stoppedChats), null, null);
      } else {
        showMessage(Lang.getString(R.string.XgramActivityMarked), null, null);
      }
    });
  }

  // Long press: the row's menu

  private void showRowMenu (@NonNull View anchor, @NonNull XgramActivityRows.Row row) {
    final XgramActivityItem item = row.newest();
    final String title = chatTitleOf(item);
    final boolean isNew = row.isNew();
    if (XgramExpressive.isEnabled()) {
      List<com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry> entries = new ArrayList<>();
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(R.id.btn_xgramActivityOpen, Lang.getString(R.string.XgramActivityOpen), R.drawable.baseline_chat_bubble_24, () -> {
        if (!isDestroyed()) {
          openRow(row);
        }
      }));
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(R.id.btn_xgramActivityToggleRead,
        Lang.getString(isNew ? R.string.XgramActivityMarkRead : R.string.XgramActivityMarkUnread),
        isNew ? R.drawable.baseline_done_all_24 : R.drawable.baseline_mark_chat_unread_24, () -> {
        if (!isDestroyed()) {
          toggleRead(row);
        }
      }));
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(R.id.btn_xgramActivityHideChat, Lang.getString(R.string.XgramActivityHideChat, title), R.drawable.baseline_eye_off_24, () -> {
        if (!isDestroyed()) {
          hideChat(item);
        }
      }));
      entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(R.id.btn_xgramActivityClear, Lang.getString(R.string.XgramActivityClear), R.drawable.baseline_delete_24, () -> {
        if (!isDestroyed()) {
          clearRow(row);
        }
      }).destructive().dividerAbove());
      com.fahadali.xgram.appearance.XgramR5Chat.showMenu(anchor, getThemeListeners(), entries);
      return;
    }
    showOptions(null,
      new int[] {R.id.btn_xgramActivityOpen, R.id.btn_xgramActivityToggleRead, R.id.btn_xgramActivityHideChat, R.id.btn_xgramActivityClear},
      new String[] {
        Lang.getString(R.string.XgramActivityOpen), Lang.getString(isNew ? R.string.XgramActivityMarkRead : R.string.XgramActivityMarkUnread),
        Lang.getString(R.string.XgramActivityHideChat, title), Lang.getString(R.string.XgramActivityClear)
      },
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.RED},
      new int[] {R.drawable.baseline_chat_bubble_24, isNew ? R.drawable.baseline_done_all_24 : R.drawable.baseline_mark_chat_unread_24, R.drawable.baseline_eye_off_24, R.drawable.baseline_delete_24},
      (itemView, id) -> {
        if (isDestroyed()) {
          return true;
        }
        if (id == R.id.btn_xgramActivityOpen) {
          openRow(row);
        } else if (id == R.id.btn_xgramActivityToggleRead) {
          toggleRead(row);
        } else if (id == R.id.btn_xgramActivityHideChat) {
          hideChat(item);
        } else if (id == R.id.btn_xgramActivityClear) {
          clearRow(row);
        }
        return true;
      });
  }

  /** A screen opened from here: on the right with two panes (this list stays on the left), over it otherwise. */
  private void openScreen (ViewController<?> c) {
    if (XgramTwoPane.replacesRightPane(this)) {
      XgramTwoPane.showOnRight(this, c);
    } else {
      navigateTo(c);
    }
  }

  private void turnOn () {
    XgramSettings.instance().setActivityOn(true);
  }

  // Taps

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_xgramActivityTurnOn) {
      turnOn();
      return;
    }
    if (v instanceof XgramActivityRowView) {
      XgramActivityRows.Row row = ((XgramActivityRowView) v).getRow();
      if (row != null) {
        openRow(row);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v instanceof XgramActivityRowView) {
      XgramActivityRows.Row row = ((XgramActivityRowView) v).getRow();
      if (row != null) {
        showRowMenu(v, row);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isRowSelected (@NonNull XgramActivityRows.Row row) {
    UiState state = uiStates.get(tdlib.id());
    return state != null && state.lastOpenedKey != null && row.contains(state.lastOpenedKey);
  }

  /**
   * The rows draw their selection themselves: each is repainted (invalidating the list alone keeps the rows' last
   * drawing), e.g. when another row of the chat already on the right is opened.
   */
  private void invalidateRows () {
    CustomRecyclerView recyclerView = getRecyclerView();
    if (recyclerView == null) {
      return;
    }
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View child = recyclerView.getChildAt(i);
      if (child instanceof XgramActivityRowView) {
        child.invalidate();
      }
    }
  }

  /** Reads the row here at once, then opens its chat at the message (the oldest New reply of a group). */
  private void openRow (@NonNull XgramActivityRows.Row row) {
    final XgramActivityItem target = row.target();
    uiState(tdlib.id()).lastOpenedKey = target.key; // the item: its row stays selected when newer replies join it
    invalidateRows();
    List<XgramActivityItem> unread = new ArrayList<>();
    for (XgramActivityItem item : row.items) {
      if (item.isNew()) {
        unread.add(item);
      }
    }
    if (!unread.isEmpty()) {
      XgramActivityStore.instance().markItemsRead(tdlib.id(), unread, false);
    }
    if (target.isDeleted()) {
      showDeleted(target);
      return;
    }
    if (tdlib.chat(target.chatId) != null) {
      openAt(target);
      return;
    }
    // A chat TDLib doesn't have loaded (left, or never opened on this device)
    tdlib.send(new TdApi.GetChat(target.chatId), (chat, error) -> runOnUiThreadOptional(() -> {
      if (chat != null) {
        openAt(target);
      } else {
        showMessage(Lang.getString(R.string.XgramActivityChatUnavailable), null, null);
      }
    }));
  }

  private void openAt (@NonNull XgramActivityItem target) {
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters()
      .keepStack()
      .highlightMessage(new MessageId(target.chatId, target.messageId))
      .ensureHighlightAvailable();
    if (target.topicId != 0 && tdlib.isForum(target.chatId)) {
      params.messageTopicId = new TdApi.MessageTopicForum(target.topicId);
    }
    tdlib.ui().openChat(this, target.chatId, params);
    invalidateRows(); // the selection follows the chat now on the right (XgramTwoPane repaints the rows too)
  }

  private void showDeleted (@NonNull XgramActivityItem target) {
    showMessage(Lang.getString(R.string.XgramActivityMessageDeleted), Lang.getString(R.string.XgramActivityOpenChat), () -> {
      if (!isDestroyed()) {
        tdlib.ui().openChat(this, target.chatId, new TdlibUi.ChatOpenParameters().keepStack());
      }
    });
  }

  /** A snackbar with Expressive design (with its action), a toast otherwise. */
  void showMessage (@NonNull CharSequence text, @Nullable CharSequence action, @Nullable Runnable onAction) {
    if (com.fahadali.xgram.appearance.XgramSnackbar.show(text, Toast.LENGTH_LONG, action, onAction)) {
      return;
    }
    UI.showToast(text, Toast.LENGTH_SHORT);
  }

  // Changes

  @Override
  public void onActivityChanged (int accountId) {
    if (accountId == tdlib.id() && !isDestroyed()) {
      reload(false);
    }
  }

  @Override
  public void onActivityDisplayChanged (int accountId) {
    if ((accountId == -1 || accountId == tdlib.id()) && !isDestroyed()) {
      updateMarkAllButton();
      reload(false);
    }
  }

  /** The chips on screen (the list may hold another one in its pool): counts and labels again. */
  private void forEachChips (@NonNull me.vkryl.core.lambda.RunnableData<XgramActivityChips> act) {
    CustomRecyclerView recyclerView = getRecyclerView();
    boolean storedSeen = false;
    if (recyclerView != null) {
      for (int i = 0; i < recyclerView.getChildCount(); i++) {
        View child = recyclerView.getChildAt(i);
        if (child instanceof XgramActivityChips) {
          act.runWithData((XgramActivityChips) child);
          storedSeen |= child == chips;
        }
      }
    }
    if (chips != null && !storedSeen) {
      act.runWithData(chips);
    }
  }

  @Override
  public void onActivityBadgeChanged () {
    if (!isDestroyed()) {
      forEachChips(this::bindChips);
    }
  }

  @Override
  public void onXgramSettingChanged (String key) {
    if (key == null) {
      return;
    }
    if (key.startsWith(XgramSettings.KEY_ACTIVITY_PREFIX) || key.startsWith("xgram_ghost_")) {
      UI.post(() -> {
        if (!isDestroyed()) {
          if (filter == XgramActivityStore.FILTER_REACTIONS && !XgramActivity.reactions()) {
            filter = XgramActivityStore.FILTER_ALL;
            updateHeaderTitle();
          }
          updateMarkAllButton(); // shown only while Activity is on
          reload(false);
        }
      });
    }
  }

  @Override
  public void onDateChanged () {
    if (adapter != null && loaded) {
      XgramActivityRowView.invalidateLayouts(); // the times read as dates now
      buildCells(true); // Today becomes Yesterday
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (adapter != null && loaded && !isDestroyed()) {
      updateHeaderTitle();
      updateMarkAllButton();
      forEachChips(XgramActivityChips::relabel);
      XgramActivityRowView.invalidateLayouts(); // prefixes, times and names in the new language or format
      buildCells(true);
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, org.thunderdog.challegram.theme.ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    // The chips and the pill read their colours when drawn: repainted, as the rows are (theme invalidate listeners)
    forEachChips(XgramActivityChips::invalidateChips);
    if (newPill != null) {
      newPill.setTextColor(XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER));
      newPill.invalidate();
      if (newPill.getBackground() != null) {
        newPill.getBackground().invalidateSelf();
      }
    }
  }

  // Lifecycle

  @Override
  public void onFocus () {
    super.onFocus();
    invalidateRows();
    XgramActivitySync.setVisible(tdlib.id(), true); // faster spacing while here, and a look for what is missing
  }

  @Override
  public void onBlur () {
    super.onBlur();
    saveScroll();
    XgramActivitySync.setVisible(tdlib.id(), false);
  }

  private void saveScroll () {
    CustomRecyclerView recyclerView = getRecyclerView();
    LinearLayoutManager manager = recyclerView != null ? (LinearLayoutManager) recyclerView.getLayoutManager() : null;
    if (manager == null || adapter == null) {
      return;
    }
    UiState state = uiState(tdlib.id());
    state.anchorKey = null;
    int first = manager.findFirstVisibleItemPosition();
    if (first <= 0) {
      return; // at the top (or nothing laid out): a rebuild opens at the top, where new rows come in
    }
    List<ListItem> items = adapter.getItems();
    for (int i = first; i < items.size(); i++) {
      if (items.get(i).getData() instanceof XgramActivityRows.Row) {
        View view = manager.findViewByPosition(i);
        state.anchorKey = ((XgramActivityRows.Row) items.get(i).getData()).key();
        state.offset = view != null ? view.getTop() : 0;
        break;
      }
    }
  }

  @Override
  public void destroy () {
    saveScroll();
    // Gone with its activity (being made again, or stopped): the next screen comes back here. Closed by the user
    // (Back, another destination) while the app is in front: the next one opens at the top.
    uiState(tdlib.id()).restorePending = context.getActivityState() != UI.State.RESUMED ||
      (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && context.isDestroyed());
    classicUndo.destroy();
    UI.cancel(statusRefresh);
    XgramActivitySync.setVisible(tdlib.id(), false);
    super.destroy();
    XgramActivityStore.instance().removeListener(this);
    XgramActivityBadge.instance().removeListener(this);
    XgramActivity.removeDisplayListener(this);
    XgramSettings.instance().removeListener(this);
    tdlib.context().dateManager().removeListener(this);
  }
}
