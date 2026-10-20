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
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import tgx.td.MessageId;

/**
 * The Activity screen: every mention of you, reply to your messages and reaction to them, newest first, in sections
 * (Today, Yesterday, This week, Earlier). Material 3 Expressive with Expressive design (a large title, filter chips,
 * status tiles, M3 list tiles), Telegram X's look otherwise (a dropdown title, description rows, BetterChatView
 * rows). A tap reads the row here and opens its chat at the message (on the right with two panes); nothing is sent
 * from this list itself.
 * <p>
 * What it shows: a filter, Unread, and the chats (all, or the ones chosen, XgramActivityChatFilter). A change swaps
 * the rows below the chips in one motion (XgramActivitySwap; at once in Telegram X's look), the chips row itself
 * staying where it is. Pull down at the top refreshes (XgramActivityPullFrame, XgramActivitySync.refresh).
 */
public final class XgramActivityController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener,
  XgramActivityStore.ChangeListener, XgramActivityBadge.Listener, XgramActivity.DisplayListener, XgramSettings.Listener,
  MoreDelegate, ToggleDelegate, XgramActivityChips.Listener, XgramActivityRowView.SelectionProvider, DateChangeListener,
  XgramActivityChatFilter.Listener {

  // Custom rows (TYPE_CUSTOM - n)
  static final int ROW = 9061, CHIPS = 9062, STATUS = 9063, OFF_ROW = 9065;
  private static final int PAGE = 100;
  private static final String KEY_FILTER = "xgram_actview_filter"; // per viewer, not a setting: never backed up
  /** The list's first item in each look: kept in place (never made again) when the rest is replaced. */
  private static final String KEY_CHIPS = "chips", KEY_OFFSET = "offset";

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
    XgramActivityChatFilter.forget(accountId);
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
  /** The chats shown, sorted (empty: all), as XgramActivityChatFilter keeps them for the account. */
  @NonNull private long[] chats = new long[0];

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
      onQueryChanged();
    }
  }

  private void setUnreadOnly (boolean unreadOnly) {
    if (this.unreadOnly != unreadOnly) {
      this.unreadOnly = unreadOnly;
      saveFilter();
      onQueryChanged();
    }
  }

  @Override
  public void onActivityChatsChanged (int accountId) {
    if (accountId != tdlib.id() || isDestroyed() || adapter == null) {
      return;
    }
    final long[] next = XgramActivityChatFilter.get(accountId);
    if (!Arrays.equals(next, chats)) {
      chats = next;
      onQueryChanged();
    }
  }

  @Nullable
  private long[] onlyChats () {
    return chats.length > 0 ? chats : null;
  }

  // Swapping the rows (the filter, Unread or the chats changed)

  /** Swaps the rows below the chips with Expressive design (null otherwise, and before the list is made). */
  @Nullable private XgramActivitySwap swap;
  /** A change waits for its rows (the swap puts them in once they come; Telegram X's look, at once). */
  private boolean awaitingSwapRows;
  /** The swap puts the new rows in now: past the hold below. */
  private boolean swapBuilding;
  /** The list was to change during a swap: once it ends. */
  private boolean buildDeferred, buildDeferredReplace;
  /** Rows wanted on screen at least for a change to show what the last query had at once (else it waits for its own). */
  private static final int MIN_LOCAL_ROWS = 30;
  /** A change whose rows take this long (the store busy): its loading row shows meanwhile. */
  private static final long SWAP_WAIT_MS = 450L;

  private final XgramActivitySwap.Host swapHost = new XgramActivitySwap.Host() {
    @Override
    public boolean isSwappedChild (@NonNull View child) {
      return !(child instanceof XgramActivityChips) && !(child instanceof XgramActivityStatusView);
    }

    @Override
    public void onSwapPoint () {
      if (!isDestroyed()) {
        swapRowsNow(true);
      }
    }

    @Override
    public void onSwapEnd () {
      if (!isDestroyed()) {
        flushDeferredBuild();
      }
    }
  };

  /** The rows took too long: the swap shows the loading row in their place (they replace it when they come). */
  private final Runnable swapTimeout = () -> {
    if (!isDestroyed() && awaitingSwapRows && swap != null && swap.isWaiting()) {
      awaitingSwapRows = false;
      this.loaded = false; // (qualified: the fields are declared further down)
      this.rows = Collections.emptyList();
      swap.setReady();
    }
  };

  /**
   * What the page shows changed (a filter, Unread, the chats). The chips row stays as it is (only the chip tapped
   * animates, in its own view); the rows below swap: with Expressive design the old ones fade out and the new ones in
   * (XgramActivitySwap), in Telegram X's look at once; the list goes back to its top. The rows come from what the last
   * query had when that covers the new choice (All to Mentions, Unread on, fewer chats), from the store otherwise.
   */
  private void onQueryChanged () {
    limit = PAGE;
    updateHeaderTitle();
    updateMarkAllButton();
    setNewPill(0);
    forEachChips(this::bindChips);
    // The query in flight is for what was shown before: its rows are dropped
    requestId++;
    loading = false;
    reloadQueued = false;
    replaceQueued = false;
    UI.cancel(swapTimeout);
    if (adapter == null || isDestroyed()) {
      return;
    }
    if (!XgramActivity.isOn() || !rowsShown) {
      awaitingSwapRows = false;
      reload(true);
      return;
    }
    awaitingSwapRows = true;
    final CustomRecyclerView recyclerView = getRecyclerView();
    if (swap != null) {
      if (XgramExpressive.isEnabled() && recyclerView != null && recyclerView.isAttachedToWindow() && recyclerView.getHeight() > 0) {
        swap.begin();
      } else if (swap.isActive()) {
        swap.cancel(true); // off screen now (the picker over it): the rows change at once
      }
    }
    final List<XgramActivityItem> local = localItems();
    if (local != null) {
      rows = XgramActivityRows.group(local);
      endReached = lastComplete;
      rowsReadyForSwap();
    } else if (swap != null && swap.isActive()) {
      UI.post(swapTimeout, SWAP_WAIT_MS);
    }
    reload(false); // the store's rows (the rest of the page, or all of them)
  }

  /** The new rows are known: in once the old ones are gone (the swap), at once otherwise. */
  private void rowsReadyForSwap () {
    awaitingSwapRows = false;
    UI.cancel(swapTimeout);
    if (swap != null && swap.isActive()) {
      swap.setReady();
    } else {
      swapRowsNow(false);
    }
  }

  /**
   * The new rows in place of the old ones, at the top, with no item animation: at the swap's point (nothing shows
   * then), or at once (Telegram X's look). The chips row is kept as it is (DiffUtil: the same item, bound in place).
   */
  private void swapRowsNow (boolean fromSwap) {
    final CustomRecyclerView recyclerView = getRecyclerView();
    if (adapter == null || isDestroyed()) {
      return;
    }
    if (recyclerView == null || !rowsShown) {
      applyRows(true);
      return;
    }
    rememberShown();
    final boolean suspend = !fromSwap && recyclerView.getItemAnimator() != null;
    if (suspend) {
      recyclerView.setItemAnimator(null);
    }
    recyclerView.stopScroll();
    swapBuilding = true;
    try {
      buildCells(false);
    } finally {
      swapBuilding = false;
    }
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (manager != null) {
      manager.scrollToPositionWithOffset(0, 0);
    }
    if (suspend) {
      restoreAnimatorAfterLayout(recyclerView);
    }
  }

  /**
   * The list's item animator back after the layout the change just made (posted now, it runs after that traversal);
   * a swap begun meanwhile puts it back itself when it ends.
   */
  private void restoreAnimatorAfterLayout (@NonNull CustomRecyclerView recyclerView) {
    recyclerView.post(() -> {
      if (!isDestroyed() && listAnimator != null && recyclerView.getItemAnimator() == null && (swap == null || !swap.isActive())) {
        recyclerView.setItemAnimator(listAnimator);
      }
    });
  }

  /** The list's own item animator (as RecyclerViewController made it): off while the rows swap. */
  @Nullable private RecyclerView.ItemAnimator listAnimator;

  /** What waited for the swap: the list as it is now (everything below the chips again, at once, when it was asked). */
  private void flushDeferredBuild () {
    if (buildDeferred) {
      final boolean replace = buildDeferredReplace;
      buildDeferred = false;
      buildDeferredReplace = false;
      if (replace && rowsShown) {
        replaceCellsNow();
      } else {
        buildCells(replace);
      }
    }
  }

  /**
   * Everything below the chips again, at once (the date or the language changed: every row reads differently, the
   * sections regroup). Without the list's item animation: replaced with it, each row cross-faded with its old self,
   * or all of them faded out and in. The reading position stays (the row at the top stays at the top).
   */
  private void replaceCellsNow () {
    final CustomRecyclerView recyclerView = getRecyclerView();
    if (adapter == null || isDestroyed()) {
      return;
    }
    if (recyclerView == null || (swap != null && swap.isActive())) {
      buildCells(true); // without a list yet, or after the swap (flushDeferredBuild comes back here)
      return;
    }
    if (recyclerView.isComputingLayout()) {
      recyclerView.post(() -> {
        if (!isDestroyed()) {
          replaceCellsNow();
        }
      });
      return;
    }
    // Where the list is: the first row on screen and its offset (at the top: it stays at the top)
    final LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    String anchorKey = null;
    int anchorOffset = 0;
    final int first = manager != null ? manager.findFirstVisibleItemPosition() : -1;
    if (first > 0) {
      final List<ListItem> items = adapter.getItems();
      for (int i = first; i < items.size(); i++) {
        if (items.get(i).getData() instanceof XgramActivityRows.Row) {
          final View view = manager.findViewByPosition(i);
          anchorKey = ((XgramActivityRows.Row) items.get(i).getData()).key();
          anchorOffset = view != null ? view.getTop() : 0;
          break;
        }
      }
    }
    final boolean suspend = recyclerView.getItemAnimator() != null;
    if (suspend) {
      recyclerView.setItemAnimator(null);
    }
    buildCells(true);
    if (manager != null && anchorKey != null) {
      final int index = indexOfRow(anchorKey);
      if (index != -1) {
        manager.scrollToPositionWithOffset(index, anchorOffset);
      }
    }
    if (suspend) {
      restoreAnimatorAfterLayout(recyclerView);
    }
  }

  // The last query's rows, for a change that only narrows it (local first)

  @NonNull private List<XgramActivityItem> lastItems = Collections.emptyList();
  private int lastFilter = -1;
  private boolean lastUnread, lastComplete;
  @NonNull private long[] lastChats = new long[0];

  /**
   * The newest rows of what is shown now, taken from the last query's items when they cover it: the last one was All
   * or the same filter, Unread was off or is on, and the chats now are some of its chats. Null when they don't, or
   * when too few came out of a page that had more (the store's rows are waited for then).
   */
  @Nullable
  private List<XgramActivityItem> localItems () {
    if (lastFilter < 0) {
      return null;
    }
    final boolean covers = (lastFilter == XgramActivityStore.FILTER_ALL || lastFilter == filter) &&
      (!lastUnread || unreadOnly) && XgramActivityChatFilter.containsAll(lastChats, chats);
    if (!covers) {
      return null;
    }
    final List<XgramActivityItem> result = new ArrayList<>();
    for (XgramActivityItem item : lastItems) {
      if (matches(item)) {
        result.add(item);
      }
    }
    return lastComplete || result.size() >= MIN_LOCAL_ROWS ? result : null;
  }

  /** {@code item} passes what is shown now (the filter, Unread, the chats), as the query decides. */
  private boolean matches (@NonNull XgramActivityItem item) {
    switch (filter) {
      case XgramActivityStore.FILTER_MENTIONS:
        if (!item.isMention()) {
          return false;
        }
        break;
      case XgramActivityStore.FILTER_REPLIES:
        if (!item.isReply()) {
          return false;
        }
        break;
      case XgramActivityStore.FILTER_REACTIONS:
        if (!item.isReaction()) {
          return false;
        }
        break;
    }
    if (unreadOnly && !item.isNew()) {
      return false;
    }
    return chats.length == 0 || XgramActivityChatFilter.contains(chats, item.chatId);
  }

  // The local copy follows what the page itself changes in the store at once: the store tells of a change only a
  // little later (its notify delay and the query), and a filter change meanwhile builds its rows from this copy

  /** Counts the page's own changes to the copy: a query asked before one of them doesn't replace it (reload). */
  private int localEpoch;

  /** Cleared here: out of the copy, so a change right after doesn't bring them back. */
  private void dropLocalItems (@NonNull List<XgramActivityItem> gone) {
    localEpoch++;
    if (lastItems.isEmpty() || gone.isEmpty()) {
      return;
    }
    final java.util.HashSet<String> keys = new java.util.HashSet<>(gone.size());
    for (XgramActivityItem item : gone) {
      keys.add(item.key);
    }
    final List<XgramActivityItem> next = new ArrayList<>(lastItems.size());
    for (XgramActivityItem item : lastItems) {
      if (!keys.contains(item.key)) {
        next.add(item);
      }
    }
    lastItems = next;
  }

  /** A chat hidden here: its items out of the copy. */
  private void dropLocalChat (long chatId) {
    localEpoch++;
    if (lastItems.isEmpty()) {
      return;
    }
    final List<XgramActivityItem> next = new ArrayList<>(lastItems.size());
    for (XgramActivityItem item : lastItems) {
      if (item.chatId != chatId) {
        next.add(item);
      }
    }
    lastItems = next;
  }

  /** Read here (opened): Read in the copy too, as the store makes them (so Unread leaves them out). */
  private void markLocalItemsRead (@NonNull List<XgramActivityItem> read) {
    localEpoch++;
    if (lastItems.isEmpty() || read.isEmpty()) {
      return;
    }
    final java.util.HashSet<String> keys = new java.util.HashSet<>(read.size());
    for (XgramActivityItem item : read) {
      keys.add(item.key);
    }
    final List<XgramActivityItem> next = new ArrayList<>(lastItems.size());
    for (XgramActivityItem item : lastItems) {
      next.add(item.isNew() && keys.contains(item.key) ? item.asRead() : item);
    }
    lastItems = next;
  }

  /** A change of the store the copy can't follow here (it comes back, or what changes is decided there): not used again. */
  private void forgetLocalItems () {
    localEpoch++;
    lastFilter = -1;
  }

  // List

  private SettingsAdapter adapter;
  @Nullable private XgramActivityChips chips;
  @NonNull private List<XgramActivityRows.Row> rows = Collections.emptyList();
  private boolean loaded, loading, reloadQueued, endReached;
  private int limit = PAGE;
  private int requestId;
  private boolean restoreScroll;
  @Nullable private String restoreAnchorKey;
  private int restoreOffset;
  /** Rows a page should show at least: fewer (grouping, muted topics) and there are more, the next page comes at once. */
  private static final int MIN_PAGE_ROWS = 20;
  private final XgramActivityUndoBar classicUndo = new XgramActivityUndoBar(this);

  /** The page's frame: pull down at the top of the list to refresh. */
  @Nullable private XgramActivityPullFrame pullFrame;

  @Override
  protected FrameLayout createFrameLayout (Context context) {
    XgramActivityPullFrame frame = new XgramActivityPullFrame(context, new XgramActivityPullFrame.Listener() {
      @Override
      public boolean canPull () {
        return !isDestroyed() && adapter != null && XgramActivity.isOn();
      }

      @Override
      public void onPullRefresh () {
        refresh();
      }
    });
    pullFrame = frame;
    return frame;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    loadFilter();
    chats = XgramActivityChatFilter.get(tdlib.id());
    listAnimator = recyclerView.getItemAnimator();
    if (XgramExpressive.isEnabled()) {
      swap = new XgramActivitySwap(recyclerView, swapHost);
    }
    UiState state = uiState(tdlib.id());
    restoreScroll = state.restorePending;
    state.restorePending = false;
    // Kept now: the screen this one replaces (moving to the left pane) may save its own while the rows load
    restoreAnchorKey = state.anchorKey;
    restoreOffset = state.offset;
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
              final Status status = (Status) item.getData();
              final XgramActivityStatusView statusView = (XgramActivityStatusView) view;
              statusView.setStatus(status.icon, status.text, status.progress);
              statusView.setCollapsing(status.key, status.collapsing, status.collapsing ? () -> onTileCollapsed(status.key) : null);
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

      @Override
      protected void setValuedSetting (ListItem item, org.thunderdog.challegram.component.base.SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_xgramActivityChats) {
          view.setData(chatsValue());
        } else {
          super.setValuedSetting(item, view, isUpdate);
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
        // Not while the rows swap out (going, or gone): as taps and holds
        return viewHolder.itemView instanceof XgramActivityRowView && ((XgramActivityRowView) viewHolder.itemView).getRow() != null && !rowsHidden();
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
    XgramActivityChatFilter.addListener(this);
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
    final int queryFilter = filter;
    final boolean queryUnread = unreadOnly;
    final long[] queryChats = chats;
    final int queryLocalEpoch = localEpoch;
    // Muted chats (off) is left out by the query itself, so the limit counts rows that are shown
    final long[][] muted = XgramActivityRows.mutedChats(tdlib, XgramActivityStore.instance().summary(tdlib.id()).knownChats());
    XgramActivityStore.instance().query(tdlib.id(), filter, unreadOnly, XgramActivity.reactions(), XgramActivity.discussion(),
      muted != null ? muted[0] : null, muted != null ? muted[1] : null, onlyChats(), requestedLimit, items -> {
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
      // Kept for a next change that only narrows this one (shown at once from here), unless the page changed the
      // store after this query was asked: the copy it updated stays until the next query, which follows that change
      if (queryLocalEpoch == localEpoch) {
        lastItems = visible;
        lastFilter = queryFilter;
        lastUnread = queryUnread;
        lastChats = queryChats;
        lastComplete = endReached;
      }
      rows = XgramActivityRows.group(visible);
      if (awaitingSwapRows) {
        rowsReadyForSwap(); // a change's own rows: in place of the old ones
      } else {
        applyRows(scrollToTop);
      }
      executeScheduledAnimation();
      onRefreshRowsLoaded();
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
    rememberShown();
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
      // A rebuild of the screen (the activity was made again, or it moved to the left pane): back where the last one was
      restoreScroll = false;
      int index = restoreAnchorKey != null ? indexOfRow(restoreAnchorKey) : -1;
      if (index != -1) {
        manager.scrollToPositionWithOffset(index, restoreOffset);
      }
      return;
    }
    if (scrollToTop) {
      manager.scrollToPositionWithOffset(0, 0);
    }
  }

  /** The rows of the store were put in once (before that, the list shows its loading row). */
  private boolean rowsShown;

  /** The rows now are what the list shows: newer ones that come later count for the new items pill. */
  private void rememberShown () {
    shownKeys.clear();
    newestShownDate = 0;
    for (XgramActivityRows.Row row : rows) {
      shownKeys.add(row.key());
      newestShownDate = Math.max(newestShownDate, row.eventDate());
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
    /** Its place above the list ("sync" or "ghost"): the same tile is updated in place. */
    final String key;
    final int icon;
    final CharSequence text;
    final float progress;
    /** Gone, as the tile shown last: it collapses on its spring, then leaves the list (Expressive design). */
    final boolean collapsing;

    Status (String key, int icon, CharSequence text, float progress) {
      this(key, icon, text, progress, false);
    }

    private Status (String key, int icon, CharSequence text, float progress, boolean collapsing) {
      this.key = key;
      this.icon = icon;
      this.text = text;
      this.progress = progress;
      this.collapsing = collapsing;
    }

    @NonNull
    Status toCollapsing () {
      return collapsing ? this : new Status(key, icon, text, progress, true);
    }

    String signature () {
      return (collapsing ? "collapsing:" : "") + icon + ":" + text + ":" + Math.round(progress * 1000f);
    }
  }

  /** The tiles above the list in their order (statuses() gives them in it too). */
  private static final String[] TILE_KEYS = {"sync", "ghost"};
  /** The status tiles in the list now (Expressive design), by key, collapsing ones included. */
  private final java.util.HashMap<String, Status> shownTiles = new java.util.HashMap<>();

  /**
   * The status tiles to show: the current ones, and in its place each one that went since the list was built last,
   * collapsing (SPATIAL_DEFAULT) until {@link #onTileCollapsed} drops it. Not when everything is replaced.
   */
  @NonNull
  private List<Status> tiles (@NonNull List<Status> statuses, boolean replace) {
    final List<Status> result = new ArrayList<>(TILE_KEYS.length);
    for (String key : TILE_KEYS) {
      Status current = null;
      for (Status status : statuses) {
        if (key.equals(status.key)) {
          current = status;
          break;
        }
      }
      final Status before = shownTiles.get(key);
      if (current != null) {
        shownTiles.put(key, current);
        result.add(current);
      } else if (before != null && !replace && cellsBuilt) {
        final Status collapsing = before.toCollapsing();
        shownTiles.put(key, collapsing);
        result.add(collapsing);
      } else {
        shownTiles.remove(key);
      }
    }
    return result;
  }

  /** A tile is shut (its spring ended, or it was off screen): it leaves the list now, without an animation of its own. */
  private void onTileCollapsed (@NonNull String key) {
    final Status status = shownTiles.get(key);
    if (status == null || !status.collapsing || isDestroyed() || adapter == null) {
      return;
    }
    shownTiles.remove(key);
    if (loaded) {
      applyRows(false);
    } else {
      buildCells();
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

  /**
   * {@code replace}: everything below the first item is new (the first rows, a date or a language), otherwise only
   * what changed is told. While the rows swap, the list waits for the swap's end (only the swap itself changes it).
   */
  private void buildCells (boolean replace) {
    if (swap != null && swap.isActive() && !swapBuilding) {
      buildDeferred = true;
      buildDeferredReplace |= replace;
      return;
    }
    final boolean expressive = XgramExpressive.isEnabled();
    final Cells cells = new Cells();
    if (!XgramActivity.isOn()) {
      shownTiles.clear();
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
      cells.add(new ListItem(ListItem.TYPE_CUSTOM - CHIPS), KEY_CHIPS, filter + ":" + unreadOnly + ":" + XgramActivity.reactions() + ":" + chatsValue());
    } else {
      cells.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL), KEY_OFFSET, "");
      // Telegram X's look: the chats shown, as a row of its own (the chips' Chats chip with Expressive design)
      final String value = chatsValue();
      cells.add(new ListItem(ListItem.TYPE_SHADOW_TOP), "chats:top", "");
      cells.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramActivityChats, R.drawable.baseline_forum_24, R.string.XgramActivityChats), "chats", value);
      cells.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM), "chats:bottom", "");
    }
    if (expressive) {
      for (Status status : tiles(statuses(), replace)) {
        cells.add(new ListItem(ListItem.TYPE_CUSTOM - STATUS).setData(status), "status:" + status.key, status.signature());
      }
    } else {
      shownTiles.clear();
      for (Status status : statuses()) {
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
      final boolean keepFirst = cellsBuilt && recyclerView != null && !current.isEmpty() && current.size() == cellKeys.size() &&
        !cells.keys.isEmpty() && cellKeys.get(0).equals(cells.keys.get(0)) &&
        (KEY_CHIPS.equals(cells.keys.get(0)) || KEY_OFFSET.equals(cells.keys.get(0))) &&
        current.get(0).getViewType() == cells.items.get(0).getViewType();
      if (keepFirst) {
        // Everything below is replaced; the chips row (or the offset) stays the same view, bound again in place if
        // it changed: replacing it too made a second chips row fade in over the first one
        final boolean firstChanged = !cellSignatures.get(0).equals(cells.signatures.get(0));
        final int oldCount = current.size();
        current.clear();
        current.addAll(cells.items);
        org.thunderdog.challegram.U.notifyItemsReplaced(adapter, oldCount, 1);
        if (firstChanged) {
          adapter.notifyItemChanged(0, PAYLOAD_REBIND);
        }
      } else {
        adapter.setItems(cells.items, false);
      }
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
    view.setChats(chatsChoice());
    // The counts within the chats shown
    final long[] onlyChats = onlyChats();
    view.setCounts(new int[] {
      0,
      XgramActivityBadge.count(tdlib, XgramActivityStore.FILTER_MENTIONS, onlyChats),
      XgramActivityBadge.count(tdlib, XgramActivityStore.FILTER_REPLIES, onlyChats),
      XgramActivityBadge.count(tdlib, XgramActivityStore.FILTER_REACTIONS, onlyChats)
    });
  }

  /** The chats shown as the Chats chip reads them: a chat's name, or "2 chats"; null for all chats. */
  @Nullable
  private String chatsChoice () {
    if (chats.length == 0) {
      return null;
    }
    if (chats.length == 1 && tdlib.chat(chats[0]) != null) {
      final String title = tdlib.chatTitle(chats[0]);
      if (title != null && !title.isEmpty()) {
        return title;
      }
    }
    return Lang.plural(R.string.xChats, chats.length);
  }

  /** The chats shown, for the classic row's value and the chips' signature: "All chats" or the choice. */
  @NonNull
  private String chatsValue () {
    final String choice = chatsChoice();
    return choice != null ? choice : Lang.getString(R.string.XgramActivityAllChats);
  }

  /** The picker of the chats shown (on the right with two panes). */
  private void openChats () {
    if (!isDestroyed()) {
      openScreen(new XgramActivityChatsController(context, tdlib));
    }
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

  @Override
  public void onChatsClicked () {
    openChats();
  }

  @Override
  public void onChatsCleared () {
    XgramActivityChatFilter.set(tdlib.id(), null); // heard back in onActivityChatsChanged
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
    int[] ids = {R.id.btn_xgramActivityUnreadOnly, R.id.btn_xgramActivityChats, R.id.btn_xgramActivityClearRead, R.id.btn_xgramActivityHidden, R.id.btn_xgramActivitySettings};
    String[] titles = {
      Lang.getString(R.string.XgramActivityUnreadOnly), Lang.getString(R.string.XgramActivityChooseChats), Lang.getString(R.string.XgramActivityClearRead),
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
        unreadOnly ? R.drawable.baseline_check_24 : R.drawable.baseline_check_box_outline_blank_24, R.drawable.baseline_forum_24,
        R.drawable.baseline_delete_sweep_24, R.drawable.baseline_eye_off_24, R.drawable.baseline_settings_24
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
    } else if (id == R.id.btn_xgramActivityChats) {
      openChats();
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
    showUndo(Lang.getString(R.string.XgramActivityCleared), () -> {
      store.restoreItems(tdlib.id(), items);
      if (!isDestroyed()) {
        forgetLocalItems(); // back in the store, not in the copy
      }
    });
  }

  /** Takes a row off the list at once (the store's change follows), and out of the local copy. */
  private void removeRowNow (@NonNull XgramActivityRows.Row row) {
    dropLocalItems(row.items);
    List<XgramActivityRows.Row> next = new ArrayList<>(rows);
    if (next.remove(row)) {
      rows = next;
      buildCells();
    }
  }

  /** Clear read items: the Read rows this filter shows (Reactions, Channel comments and Muted chats as set), with Undo. */
  private void clearRead () {
    final XgramActivityStore store = XgramActivityStore.instance();
    store.queryRead(tdlib.id(), filter, XgramActivity.reactions(), XgramActivity.discussion(), onlyChats(), read -> {
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
      dropLocalItems(cleared);
      String text = cleared.size() == 1 ? Lang.getString(R.string.XgramActivityClearedOne) : Lang.getString(R.string.XgramActivityClearedMany, cleared.size());
      showUndo(text, () -> {
        store.restoreItems(tdlib.id(), cleared);
        if (!isDestroyed()) {
          forgetLocalItems();
        }
      });
    });
  }

  /**
   * The chat's activity hidden for good, and no longer one of the chats shown. Undo shows it again, chosen again if it
   * was (the page goes back to it).
   */
  private void hideChat (@NonNull XgramActivityItem item) {
    final int accountId = tdlib.id();
    final long chatId = item.chatId;
    final String title = chatTitleOf(item);
    final XgramActivityStore store = XgramActivityStore.instance();
    final boolean wasChosen = XgramActivityChatFilter.contains(XgramActivityChatFilter.get(accountId), chatId);
    store.setChatHidden(accountId, chatId, true);
    dropLocalChat(chatId);
    List<XgramActivityRows.Row> next = new ArrayList<>();
    for (XgramActivityRows.Row row : rows) {
      if (row.newest().chatId != chatId) {
        next.add(row);
      }
    }
    rows = next;
    buildCells();
    showUndo(Lang.getString(R.string.XgramActivityChatHidden, title), () -> {
      store.setChatHidden(accountId, chatId, false);
      if (!isDestroyed()) {
        forgetLocalItems(); // its rows are back in the store, not in the copy: a change waits for the store's
        if (wasChosen) {
          XgramActivityChatFilter.toggle(accountId, chatId, true); // heard back in onActivityChatsChanged
        }
      }
    });
    // A hidden chat shows nothing: it is no longer one of the chats shown either
    XgramActivityChatFilter.toggle(accountId, chatId, false);
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
    forgetLocalItems(); // the store decides the new state (ghost mode, a read on the server)
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
      forgetLocalItems();
      showUndo(Lang.getString(R.string.XgramActivityReadLocally), () -> {
        marks.undo();
        XgramActivityStore.instance().markItemsUnread(tdlib.id(), unread, false); // still unread on the server
        if (!isDestroyed()) {
          forgetLocalItems();
        }
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
    forgetLocalItems(); // what is read (and what Telegram stops) is the store's to say
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
    if (v.getId() == R.id.btn_xgramActivityChats) {
      openChats();
      return;
    }
    if (v instanceof XgramActivityRowView) {
      XgramActivityRows.Row row = ((XgramActivityRowView) v).getRow();
      if (row != null && !rowsHidden()) {
        openRow(row);
      }
    }
  }

  /** The rows swap and the old ones are going or gone: taps on them are not taken. */
  private boolean rowsHidden () {
    return swap != null && swap.hidesRows();
  }

  @Override
  public boolean onLongClick (View v) {
    if (v instanceof XgramActivityRowView) {
      XgramActivityRows.Row row = ((XgramActivityRowView) v).getRow();
      if (row != null && !rowsHidden()) {
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
      markLocalItemsRead(unread); // a filter or Unread change right after builds from the copy
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

  // Pull to refresh

  /** The indicator turns at least this long; a catch-up keeps it turning until its searches end, at most this long. */
  private static final long REFRESH_MIN_MS = 600L, REFRESH_MAX_MS = 5000L;
  private static final long STATUS_HOLD_MS = 1200L, STATUS_HOLD_WAIT_MS = 2000L;
  private int refreshRequest;
  private long refreshStartedAt;
  /** The refresh waits for: the store's rows read again, and the searches it queued. */
  private boolean refreshRowsPending, refreshSyncPending, refreshQueued;
  @Nullable private String refreshStatus;
  private long refreshHoldMs;

  private final Runnable refreshTimeout = () -> {
    if (!isDestroyed() && refreshSyncPending) {
      // Still searching: the status tile tells the rest. Waiting (Telegram, or the network): said in its place.
      noteSyncBlocked(XgramActivityStore.instance().summary(tdlib.id()).sync);
      refreshSyncPending = false;
      maybeFinishRefresh();
    }
  };

  /**
   * Released past the threshold: the store's rows again, and a bounded catch-up (XgramActivitySync.refresh: the chats
   * whose counters differ, recently active groups; paced, at most once every 30 seconds, never while Telegram asks to
   * wait). Ends with "Updated", or "Try again in 12s" while Telegram asks to wait, "Waiting for network" with no
   * connection, "Couldn't update" when there is nothing to search with.
   */
  private void refresh () {
    if (isDestroyed()) {
      return;
    }
    final int request = ++refreshRequest;
    refreshStartedAt = SystemClock.uptimeMillis();
    refreshRowsPending = true;
    refreshSyncPending = true;
    refreshQueued = false;
    refreshStatus = Lang.getString(R.string.XgramActivityUpdated);
    refreshHoldMs = STATUS_HOLD_MS;
    UI.cancel(refreshTimeout);
    if (pullFrame != null) {
      pullFrame.announceForAccessibility(Lang.getString(R.string.XgramActivityUpdating));
    }
    if (!loading) {
      reload(false);
    } else {
      reloadQueued = true; // the rows that come next are read after the pull
    }
    XgramActivitySync.refresh(tdlib.id(), onlyChats(), (result, waitMs, jobs) -> {
      if (isDestroyed() || request != refreshRequest) {
        return;
      }
      switch (result) {
        case XgramActivitySync.REFRESH_WAIT:
          refreshStatus = Lang.getString(R.string.XgramActivityTryAgainIn, shortDuration(waitMs));
          refreshHoldMs = STATUS_HOLD_WAIT_MS;
          refreshSyncPending = false;
          break;
        case XgramActivitySync.REFRESH_QUEUED:
          refreshQueued = true;
          UI.post(refreshTimeout, REFRESH_MAX_MS);
          checkRefreshSync();
          break;
        case XgramActivitySync.REFRESH_OFFLINE:
          // No connection: nothing was fetched (what was queued runs once it is back)
          refreshStatus = Lang.getString(R.string.XgramActivityOffline);
          refreshHoldMs = STATUS_HOLD_WAIT_MS;
          refreshSyncPending = false;
          break;
        case XgramActivitySync.REFRESH_OFF:
          // No account or store to search with: only the rows were read again
          refreshStatus = Lang.getString(R.string.XgramActivityCouldNotUpdate);
          refreshHoldMs = STATUS_HOLD_WAIT_MS;
          refreshSyncPending = false;
          break;
        default:
          refreshSyncPending = false;
          break;
      }
      maybeFinishRefresh();
    });
  }

  /** The store's rows came (after the pull). */
  private void onRefreshRowsLoaded () {
    if (refreshRowsPending) {
      refreshRowsPending = false;
      maybeFinishRefresh();
    }
  }

  /** The searches the refresh queued ended (or Telegram asked to wait meanwhile, or the connection went). */
  private void checkRefreshSync () {
    if (!refreshSyncPending || !refreshQueued) {
      return;
    }
    final XgramActivityStore.SyncStatus sync = XgramActivityStore.instance().summary(tdlib.id()).sync;
    if (!noteSyncBlocked(sync) && sync.running) {
      return;
    }
    refreshSyncPending = false;
    UI.cancel(refreshTimeout);
    maybeFinishRefresh();
  }

  /**
   * The searches can't go on now: Telegram asks to wait ("Try again in 12s"), or there is no connection ("Waiting for
   * network"). The refresh ends saying so (true); not "Updated", when nothing was fetched.
   */
  private boolean noteSyncBlocked (@NonNull XgramActivityStore.SyncStatus sync) {
    if (sync.isPaused()) {
      refreshStatus = Lang.getString(R.string.XgramActivityTryAgainIn, shortDuration(sync.pausedUntil - System.currentTimeMillis()));
    } else if (sync.running && sync.offline) {
      refreshStatus = Lang.getString(R.string.XgramActivityOffline);
    } else {
      return false;
    }
    refreshHoldMs = STATUS_HOLD_WAIT_MS;
    return true;
  }

  private void maybeFinishRefresh () {
    if (refreshRowsPending || refreshSyncPending || pullFrame == null || !pullFrame.isRefreshing()) {
      return;
    }
    UI.cancel(refreshTimeout);
    final int request = refreshRequest;
    final long left = REFRESH_MIN_MS - (SystemClock.uptimeMillis() - refreshStartedAt);
    final String status = refreshStatus;
    final long hold = refreshHoldMs;
    final Runnable finish = () -> {
      if (!isDestroyed() && request == refreshRequest && pullFrame != null) {
        pullFrame.finishRefresh(status, hold);
      }
    };
    if (left > 0) {
      UI.post(finish, left);
    } else {
      finish.run();
    }
  }

  /** "12s", "3m", "1h": how long Telegram still asks to wait. */
  @NonNull
  private static String shortDuration (long ms) {
    final long seconds = Math.max(1L, (ms + 999L) / 1000L);
    if (seconds < 60) {
      return Lang.plural(R.string.xSecondsShort, seconds);
    }
    if (seconds < 3600) {
      return Lang.plural(R.string.xMinutesShort, (seconds + 59) / 60);
    }
    return Lang.plural(R.string.xHoursShort, (seconds + 3599) / 3600);
  }

  // Changes

  @Override
  public void onActivityChanged (int accountId) {
    if (accountId == tdlib.id() && !isDestroyed()) {
      reload(false);
      checkRefreshSync();
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
          if (!XgramActivity.isOn() && pullFrame != null && pullFrame.isRefreshing()) {
            refreshRequest++;
            pullFrame.reset();
          }
          reload(false);
        }
      });
    }
  }

  @Override
  public void onDateChanged () {
    if (adapter != null && loaded) {
      XgramActivityRowView.invalidateLayouts(); // the times read as dates now
      replaceCellsNow(); // Today becomes Yesterday
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
      replaceCellsNow();
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, org.thunderdog.challegram.theme.ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    // The chips and the pill read their colours when drawn: repainted, as the rows are (theme invalidate listeners)
    forEachChips(XgramActivityChips::invalidateChips);
    if (pullFrame != null) {
      pullFrame.invalidate(); // the pull indicator reads its colours when drawn
    }
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
    setOnScreen(true); // faster spacing while here, and a look for what is missing
  }

  @Override
  public void onBlur () {
    super.onBlur();
    saveScroll();
    setOnScreen(false);
  }

  /**
   * Activity screens in focus (UI thread). Two can be for a moment (one moving to the left pane, a fold): the sync's
   * faster spacing ends when the last one of the account goes, not when either does.
   */
  private static final List<XgramActivityController> onScreen = new ArrayList<>();

  private void setOnScreen (boolean focused) {
    final int accountId = tdlib.id();
    if (focused) {
      if (!onScreen.contains(this)) {
        onScreen.add(this);
      }
      XgramActivitySync.setVisible(accountId, true);
      return;
    }
    onScreen.remove(this);
    for (XgramActivityController other : onScreen) {
      if (other.tdlib().id() == accountId) {
        return;
      }
    }
    XgramActivitySync.setVisible(accountId, false);
  }

  /**
   * It moves to the left pane (XgramActivity.open): the screen made there opens where this one is. Called right
   * before that screen is made.
   */
  void handOverScroll () {
    if (isFocused()) {
      saveScroll(); // otherwise it was saved when it lost focus
    }
    uiState(tdlib.id()).restorePending = true;
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
    UI.cancel(swapTimeout);
    UI.cancel(refreshTimeout);
    refreshRequest++;
    if (swap != null) {
      swap.cancel(false);
    }
    if (pullFrame != null) {
      pullFrame.reset();
    }
    setOnScreen(false);
    super.destroy();
    XgramActivityStore.instance().removeListener(this);
    XgramActivityBadge.instance().removeListener(this);
    XgramActivity.removeDisplayListener(this);
    XgramSettings.instance().removeListener(this);
    XgramActivityChatFilter.removeListener(this);
    tdlib.context().dateManager().removeListener(this);
  }
}
