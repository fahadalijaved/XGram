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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5Chat;
import com.fahadali.xgram.appearance.XgramR5People;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The chats the Activity page shows: All chats, then every chat with activity (its avatar, its name, how many items
 * and how many new), each with a checkbox, the most recently active first. A tap chooses or unchooses a chat, and the
 * page follows at once (on the left with two panes); All chats chooses none. Holding a chat offers Only this chat and
 * hiding its activity for good (Hidden chats, with Undo). Telegram X's selectable chat rows (the folder and member
 * pickers' TYPE_CHAT_SMALL_SELECTABLE), which are Material 3 list tiles with Expressive design.
 */
public final class XgramActivityChatsController extends RecyclerViewController<Void> implements View.OnClickListener,
  View.OnLongClickListener, XgramActivityChatFilter.Listener {

  private SettingsAdapter adapter;
  @NonNull private List<XgramActivityStore.ChatCount> counts = Collections.emptyList();
  private boolean loaded, loading, reloadQueued;
  private final Set<Long> requested = new HashSet<>();
  private final XgramActivityUndoBar classicUndo = new XgramActivityUndoBar(this);

  public XgramActivityChatsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramActivityChats;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramActivityChatsTitle);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        if (customViewType == XgramR5People.STATE_ROW || customViewType == XgramR5People.LOADING_ROW) {
          return new SettingHolder(XgramR5People.newStateView(parent.getContext()));
        }
        return super.initCustom(parent, customViewType);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        if (customViewType == XgramR5People.STATE_ROW) {
          XgramR5People.bindState(view, true, R.drawable.baseline_forum_24, Lang.getString(R.string.XgramActivityChatsEmpty), null);
        } else if (customViewType == XgramR5People.LOADING_ROW) {
          XgramR5People.bindState(view, true, 0, null, null);
        } else {
          super.modifyCustom(holder, position, item, customViewType, view, isUpdate);
        }
      }

      @Override
      protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
        if (checkBox != null) {
          checkBox.setChecked(isChecked(item), isUpdate);
        }
      }
    };
    build();
    recyclerView.setAdapter(adapter);
    XgramActivityChatFilter.addListener(this);
    load();
  }

  // Loading

  /** Hidden chats when the chats were counted: back from Hidden chats with one shown again, they are counted again. */
  private int hiddenAtLoad = -1;

  @Override
  public void onFocus () {
    super.onFocus();
    if (loaded && XgramActivityStore.instance().summary(tdlib.id()).hiddenCount() != hiddenAtLoad) {
      load();
    }
  }

  private void load () {
    if (loading) {
      reloadQueued = true;
      return;
    }
    loading = true;
    final XgramActivityStore.Summary summary = XgramActivityStore.instance().summary(tdlib.id());
    hiddenAtLoad = summary.hiddenCount();
    final long[][] muted = XgramActivityRows.mutedChats(tdlib, summary.knownChats());
    XgramActivityStore.instance().chatCounts(tdlib.id(), XgramActivity.reactions(), XgramActivity.discussion(),
      muted != null ? muted[0] : null, muted != null ? muted[1] : null, result -> {
      if (isDestroyed()) {
        return;
      }
      loading = false;
      loaded = true;
      counts = result;
      build();
      if (reloadQueued) {
        reloadQueued = false;
        load();
      }
    });
  }

  /** The chats listed: the ones with activity, then chosen ones without any now (so they can still be unchosen). */
  @NonNull
  private List<XgramActivityStore.ChatCount> listed () {
    final long[] chosen = XgramActivityChatFilter.get(tdlib.id());
    if (chosen.length == 0) {
      return counts;
    }
    final Set<Long> seen = new HashSet<>();
    for (XgramActivityStore.ChatCount count : counts) {
      seen.add(count.chatId);
    }
    List<XgramActivityStore.ChatCount> list = null;
    for (long chatId : chosen) {
      if (!seen.contains(chatId)) {
        if (list == null) {
          list = new ArrayList<>(counts);
        }
        list.add(new XgramActivityStore.ChatCount(chatId, 0, 0, 0, null));
      }
    }
    return list != null ? list : counts;
  }

  private void build () {
    if (adapter == null) {
      return;
    }
    final boolean expressive = XgramExpressive.isEnabled();
    final List<ListItem> items = new ArrayList<>();
    final List<XgramActivityStore.ChatCount> chats = loaded ? listed() : Collections.emptyList();
    if (!loaded) {
      items.add(expressive ? new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.LOADING_ROW) : new ListItem(ListItem.TYPE_PROGRESS));
    } else if (chats.isEmpty()) {
      if (expressive) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.STATE_ROW));
      } else {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.XgramActivityChatsEmpty));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      final DoubleTextWrapper all = new DoubleTextWrapper(tdlib, Lang.getString(R.string.XgramActivityAllChats), Lang.plural(R.string.xChats, chats.size()),
        new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.SAVED_MESSAGES), R.drawable.baseline_forum_24));
      items.add(new ListItem(ListItem.TYPE_CHAT_SMALL_SELECTABLE, R.id.btn_xgramActivityChatsAll).setData(all));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramActivityChatsHeader));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (XgramActivityStore.ChatCount count : chats) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        first = false;
        items.add(chatItem(count));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramActivityChatsHint));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramActivityChatsHidden, R.drawable.baseline_eye_off_24, R.string.XgramActivityHiddenChats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
  }

  @NonNull
  private ListItem chatItem (@NonNull XgramActivityStore.ChatCount count) {
    final long chatId = count.chatId;
    final TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null && requested.add(chatId)) {
      // Not loaded yet: listed again once it is (until then, and if TDLib can't find it, by its saved title).
      // TDLib's callback comes on its own thread.
      tdlib.send(new TdApi.GetChat(chatId), (result, error) -> runOnUiThreadOptional(() -> {
        if (result != null) {
          build();
        }
      }));
    }
    final String subtitle = subtitleOf(count);
    final DoubleTextWrapper wrapper;
    if (chat != null) {
      wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
      wrapper.setForcedSubtitle(subtitle);
    } else {
      wrapper = new DoubleTextWrapper(tdlib, titleOf(count), subtitle,
        new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.INACTIVE), R.drawable.baseline_forum_24));
    }
    return new ListItem(ListItem.TYPE_CHAT_SMALL_SELECTABLE, R.id.btn_xgramActivityChat).setData(wrapper).setLongId(chatId);
  }

  /** "12 items · 3 new". */
  @NonNull
  private static String subtitleOf (@NonNull XgramActivityStore.ChatCount count) {
    if (count.count <= 0) {
      return Lang.getString(R.string.XgramActivityChatNoItems);
    }
    final String items = count.count == 1 ? Lang.getString(R.string.XgramActivityChatItemsOne) : Lang.getString(R.string.XgramActivityChatItemsMany, count.count);
    return count.newCount > 0 ? Lang.getString(R.string.XgramActivityChatItemsNew, items, count.newCount) : items;
  }

  /** The chat's title now, else the one its rows saved, else "Chat unavailable". */
  @NonNull
  private String titleOf (@NonNull XgramActivityStore.ChatCount count) {
    String title = tdlib.chat(count.chatId) != null ? tdlib.chatTitle(count.chatId) : null;
    if (title == null || title.isEmpty()) {
      title = count.savedTitle;
    }
    return title != null && !title.isEmpty() ? title : Lang.getString(R.string.XgramActivityChatUnavailable);
  }

  /** The All chats row: checked while no chat is chosen; a chat's row: while it is chosen. */
  private boolean isChecked (@NonNull ListItem item) {
    final long[] chosen = XgramActivityChatFilter.get(tdlib.id());
    if (item.getId() == R.id.btn_xgramActivityChatsAll) {
      return chosen.length == 0;
    }
    return XgramActivityChatFilter.contains(chosen, item.getLongId());
  }

  // Choosing

  @Override
  public void onClick (View v) {
    if (!(v.getTag() instanceof ListItem)) {
      return;
    }
    final ListItem item = (ListItem) v.getTag();
    final int id = item.getId();
    if (id == R.id.btn_xgramActivityChatsAll) {
      XgramActivityChatFilter.set(tdlib.id(), null);
    } else if (id == R.id.btn_xgramActivityChat && item.getLongId() != 0) {
      final long chatId = item.getLongId();
      XgramActivityChatFilter.toggle(tdlib.id(), chatId, !XgramActivityChatFilter.contains(XgramActivityChatFilter.get(tdlib.id()), chatId));
    } else if (id == R.id.btn_xgramActivityChatsHidden) {
      navigateTo(new XgramActivityHiddenController(context, tdlib));
    }
  }

  /** The choice changed (here, or with the Chats chip's clear on the page): the checkboxes follow. */
  /** A change made here lists the chats itself. */
  private boolean holdChoiceUpdates;

  @Override
  public void onActivityChatsChanged (int accountId) {
    if (accountId != tdlib.id() || isDestroyed() || adapter == null || holdChoiceUpdates) {
      return;
    }
    if (listed().size() != countChatRows()) {
      build(); // a chosen chat without activity came or went
      return;
    }
    final CustomRecyclerView recyclerView = getRecyclerView();
    final LinearLayoutManager manager = recyclerView != null ? (LinearLayoutManager) recyclerView.getLayoutManager() : null;
    final List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      final ListItem item = items.get(i);
      if (item.getViewType() != ListItem.TYPE_CHAT_SMALL_SELECTABLE) {
        continue;
      }
      final View view = manager != null ? manager.findViewByPosition(i) : null;
      final CheckBoxView checkBox = view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 1 &&
        ((ViewGroup) view).getChildAt(1) instanceof CheckBoxView ? (CheckBoxView) ((ViewGroup) view).getChildAt(1) : null;
      if (checkBox != null) {
        final boolean checked = isChecked(item);
        if (checkBox.isChecked() != checked) {
          checkBox.setChecked(checked, true);
        }
      } else {
        adapter.notifyItemChanged(i); // off screen (or cached): bound again when it shows
      }
    }
  }

  private int countChatRows () {
    int count = 0;
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_xgramActivityChat) {
        count++;
      }
    }
    return count;
  }

  // Holding a chat: only this chat, or hide its activity for good

  @Override
  public boolean onLongClick (View v) {
    if (!(v.getTag() instanceof ListItem)) {
      return false;
    }
    final ListItem item = (ListItem) v.getTag();
    if (item.getId() != R.id.btn_xgramActivityChat || item.getLongId() == 0) {
      return false;
    }
    final long chatId = item.getLongId();
    final String title = titleOf(countOf(chatId));
    if (XgramExpressive.isEnabled()) {
      List<XgramR5Chat.MenuEntry> entries = new ArrayList<>();
      entries.add(new XgramR5Chat.MenuEntry(R.id.btn_xgramActivityChatOnly, Lang.getString(R.string.XgramActivityOnlyThisChat), R.drawable.baseline_forum_24, () -> {
        if (!isDestroyed()) {
          XgramActivityChatFilter.set(tdlib.id(), new long[] {chatId});
        }
      }));
      entries.add(new XgramR5Chat.MenuEntry(R.id.btn_xgramActivityChatHide, Lang.getString(R.string.XgramActivityHideChat, title), R.drawable.baseline_eye_off_24, () -> {
        if (!isDestroyed()) {
          hideChat(chatId, title);
        }
      }).destructive().dividerAbove());
      XgramR5Chat.showMenu(v, getThemeListeners(), entries);
      return true;
    }
    showOptions(null, new int[] {R.id.btn_xgramActivityChatOnly, R.id.btn_xgramActivityChatHide},
      new String[] {Lang.getString(R.string.XgramActivityOnlyThisChat), Lang.getString(R.string.XgramActivityHideChat, title)},
      new int[] {OptionColor.NORMAL, OptionColor.RED},
      new int[] {R.drawable.baseline_forum_24, R.drawable.baseline_eye_off_24}, (itemView, id) -> {
        if (isDestroyed()) {
          return true;
        }
        if (id == R.id.btn_xgramActivityChatOnly) {
          XgramActivityChatFilter.set(tdlib.id(), new long[] {chatId});
        } else if (id == R.id.btn_xgramActivityChatHide) {
          hideChat(chatId, title);
        }
        return true;
      });
    return true;
  }

  @NonNull
  private XgramActivityStore.ChatCount countOf (long chatId) {
    for (XgramActivityStore.ChatCount count : counts) {
      if (count.chatId == chatId) {
        return count;
      }
    }
    return new XgramActivityStore.ChatCount(chatId, 0, 0, 0, null);
  }

  /** The chat's activity hidden for good (Hidden chats lists it); no longer chosen. Undo shows it again. */
  private void hideChat (long chatId, @NonNull String title) {
    final XgramActivityStore store = XgramActivityStore.instance();
    store.setChatHidden(tdlib.id(), chatId, true);
    final List<XgramActivityStore.ChatCount> left = new ArrayList<>(counts.size());
    for (XgramActivityStore.ChatCount count : counts) {
      if (count.chatId != chatId) {
        left.add(count);
      }
    }
    counts = left;
    // Listed once, after the choice changes too
    holdChoiceUpdates = true;
    try {
      XgramActivityChatFilter.toggle(tdlib.id(), chatId, false);
    } finally {
      holdChoiceUpdates = false;
    }
    build();
    final Runnable undo = () -> {
      store.setChatHidden(tdlib.id(), chatId, false);
      if (!isDestroyed()) {
        load();
      }
    };
    final String text = Lang.getString(R.string.XgramActivityChatHidden, title);
    if (XgramExpressive.isEnabled() ? !com.fahadali.xgram.appearance.XgramSnackbar.showUndo(text, undo) : !classicUndo.show(text, undo, extraBottomInset)) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  @Override
  public void destroy () {
    classicUndo.destroy();
    super.destroy();
    XgramActivityChatFilter.removeListener(this);
  }
}
