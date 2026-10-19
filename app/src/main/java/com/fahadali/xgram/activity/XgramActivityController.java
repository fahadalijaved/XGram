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

  // Per account, across rebuilds (fold, Expressive design, language): where the list was and the row opened last

  static final class UiState {
    int position = RecyclerView.NO_POSITION, offset;
    @Nullable String anchorKey;
    @Nullable String lastOpenedKey;
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
  private boolean restoreScroll = true;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    loadFilter();
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

    XgramActivityStore.instance().addListener(this);
    XgramActivityBadge.instance().addListener(this);
    XgramActivity.addDisplayListener(this);
    XgramSettings.instance().addListener(this);
    tdlib.context().dateManager().addListener(this);
    reload(false);
  }

  /** The list moved (P4: the new items pill). */
  private void onListScrolled (int dy) { }

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
      return;
    }
    if (!XgramActivity.isOn()) {
      loaded = true;
      rows = Collections.emptyList();
      buildCells();
      executeScheduledAnimation();
      return;
    }
    loading = true;
    final int request = ++requestId;
    final int requestedLimit = limit;
    XgramActivityStore.instance().query(tdlib.id(), filter, unreadOnly, XgramActivity.reactions(), XgramActivity.discussion(), requestedLimit, items -> {
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
      if (reloadQueued) {
        reloadQueued = false;
        reload(false);
      }
    });
  }

  private boolean rebuildPosted;

  /** Puts the rows in, keeping what is on screen where it is (or going to the top). */
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
    // The first row on screen and its offset, to find it again in the new list
    String anchorKey = null;
    int anchorOffset = 0;
    LinearLayoutManager manager = recyclerView != null ? (LinearLayoutManager) recyclerView.getLayoutManager() : null;
    if (manager != null && !scrollToTop) {
      int first = manager.findFirstVisibleItemPosition();
      List<ListItem> items = adapter.getItems();
      for (int i = Math.max(0, first); i < items.size() && i <= manager.findLastVisibleItemPosition(); i++) {
        if (items.get(i).getData() instanceof XgramActivityRows.Row) {
          View view = manager.findViewByPosition(i);
          anchorKey = ((XgramActivityRows.Row) items.get(i).getData()).key();
          anchorOffset = view != null ? view.getTop() : 0;
          break;
        }
      }
      if (first == 0) {
        anchorKey = null; // at the top: stay at the top, new rows come in above
      }
    }
    buildCells();
    if (manager == null) {
      return;
    }
    if (restoreScroll) {
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
    } else if (anchorKey != null) {
      int index = indexOfRow(anchorKey);
      if (index != -1) {
        manager.scrollToPositionWithOffset(index, anchorOffset);
      }
    }
  }

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
    final int icon;
    final CharSequence text;
    final float progress;

    Status (int icon, CharSequence text, float progress) {
      this.icon = icon;
      this.text = text;
      this.progress = progress;
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
      result.add(new Status(0, Lang.getString(R.string.XgramActivityPaused, wait), -1f));
      scheduleStatusRefresh();
    } else if (sync.running && sync.offline) {
      result.add(new Status(0, Lang.getString(R.string.XgramActivityOffline), -1f));
    } else if (sync.running && sync.total >= 2) {
      result.add(new Status(0, Lang.getString(R.string.XgramActivityCatchingUp, Math.min(sync.done, sync.total), sync.total), (float) Math.min(sync.done, sync.total) / sync.total));
    }
    if (XgramGhost.dontReadMessages()) {
      result.add(new Status(R.drawable.baseline_ghost_24, Lang.getString(R.string.XgramActivityGhostNote), -1f));
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

  private void buildCells () {
    final boolean expressive = XgramExpressive.isEnabled();
    List<ListItem> items = new ArrayList<>();
    if (!XgramActivity.isOn()) {
      if (expressive) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - OFF_ROW));
      } else {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramActivityOff));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramActivityTurnOn, R.drawable.baseline_notifications_24, R.string.XgramActivityTurnOn));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
      adapter.setItems(items, false);
      return;
    }
    if (expressive) {
      items.add(new ListItem(ListItem.TYPE_CUSTOM - CHIPS));
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    }
    for (Status status : statuses()) {
      if (expressive) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - STATUS).setData(status));
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, status.text, false));
      }
    }
    if (!loaded) {
      items.add(expressive ? new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.LOADING_ROW) : new ListItem(ListItem.TYPE_PROGRESS));
    } else if (rows.isEmpty()) {
      if (expressive) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.STATE_ROW));
      } else {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, emptyTitle(), false));
      }
    } else {
      final long now = XgramActivityStore.nowSeconds();
      int section = -1;
      boolean first = true;
      for (XgramActivityRows.Row row : rows) {
        int rowSection = XgramActivityClassifier.sectionOf(row.eventDate(), now);
        if (rowSection != section) {
          if (section != -1) {
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          }
          section = rowSection;
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, sectionName(rowSection), false));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          first = true;
        }
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        items.add(new ListItem(ListItem.TYPE_CUSTOM - ROW).setData(row));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM)); // every page closes its groups
    }
    adapter.setItems(items, false);
    if (getRecyclerView() != null) {
      getRecyclerView().invalidateItemDecorations();
    }
  }

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

  private boolean isSwiping () {
    return false; // P4: a row being swiped away
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

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_xgramActivity) {
      header.addMoreButton(menu, this);
    } else {
      super.fillMenuItems(id, header, menu);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == org.thunderdog.challegram.R.id.menu_btn_more) {
      showMoreMenu();
    } else {
      super.onMenuItemPressed(id, view);
    }
  }

  private void showMoreMenu () {
    final boolean expressive = XgramExpressive.isEnabled();
    int[] ids = {R.id.btn_xgramActivityUnreadOnly, R.id.btn_xgramActivitySettings};
    String[] titles = {Lang.getString(R.string.XgramActivityUnreadOnly), Lang.getString(R.string.XgramActivitySettings)};
    if (expressive) {
      showMore(ids, titles, 0);
      org.thunderdog.challegram.widget.PopupLayout popup = context().getCurrentPopupWindow();
      if (popup != null && popup.getBoundView() instanceof org.thunderdog.challegram.navigation.MenuMoreWrap) {
        ((org.thunderdog.challegram.navigation.MenuMoreWrap) popup.getBoundView()).xgramSetCheckable(R.id.btn_xgramActivityUnreadOnly, unreadOnly);
      }
    } else {
      int[] icons = {unreadOnly ? R.drawable.baseline_check_24 : R.drawable.baseline_check_box_outline_blank_24, R.drawable.baseline_settings_24};
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
    } else if (id == R.id.btn_xgramActivitySettings) {
      openScreen(com.fahadali.xgram.compose.XgramSettingsPages.open(context, tdlib, XgramSettingsController.SECTION_CHAT_LIST, R.id.btn_xgramActivityShow));
    }
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
    return false; // P4: the row's menu
  }

  @Override
  public boolean isRowSelected (@NonNull String key, long chatId) {
    UiState state = uiStates.get(tdlib.id());
    return state != null && state.lastOpenedKey != null && state.lastOpenedKey.equals(key);
  }

  /** Reads the row here at once, then opens its chat at the message (the oldest New reply of a group). */
  private void openRow (@NonNull XgramActivityRows.Row row) {
    final XgramActivityItem target = row.target();
    uiState(tdlib.id()).lastOpenedKey = row.key();
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
    if (getRecyclerView() != null) {
      getRecyclerView().invalidate(); // the selection follows on the next frames (XgramTwoPane repaints the rows too)
    }
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
      reload(false);
    }
  }

  @Override
  public void onActivityBadgeChanged () {
    if (chips != null && !isDestroyed()) {
      bindChips(chips);
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
          reload(false);
        }
      });
    }
  }

  @Override
  public void onDateChanged () {
    if (adapter != null && loaded) {
      buildCells(); // Today becomes Yesterday
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (adapter != null && loaded && !isDestroyed()) {
      updateHeaderTitle();
      buildCells();
    }
  }

  // Lifecycle

  @Override
  public void onFocus () {
    super.onFocus();
    if (getRecyclerView() != null) {
      getRecyclerView().invalidate();
    }
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
    List<ListItem> items = adapter.getItems();
    for (int i = Math.max(0, first); i < items.size() && first != RecyclerView.NO_POSITION; i++) {
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
