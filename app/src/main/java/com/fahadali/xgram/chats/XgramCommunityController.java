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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tgx.td.ChatId;

/**
 * A Telegram community (groups and channels, with or without topics, gathered under one name): its chats, those you
 * are in first, like the official apps. Opened from "added to the community" messages and the chat menu.
 */
public final class XgramCommunityController extends RecyclerViewController<Long> implements View.OnClickListener, TdlibCache.CommunityDataChangeListener, XgramCommunities.Listener {
  private SettingsAdapter adapter;
  private final Set<Long> requestedChats = new HashSet<>();
  /** Chats asked from TDLib (GetChat) that haven't answered yet. */
  private final Set<Long> pendingChats = new HashSet<>();
  /** TDLib's answer to GetCommunityFullInfo, when it is an error. */
  @Nullable private String loadError;
  /** TDLib's answer to GetCommunityFullInfo, for when its update didn't reach the cache. */
  @Nullable private TdApi.CommunityFullInfo fetchedFull;
  /** Waited long enough for the full info: show the chats known on this device. */
  private boolean useKnownChats;
  /** The chats' own info was asked for (once per page, see XgramCommunityData.refresh). */
  private boolean refreshed;
  private final Runnable rebuild = () -> {
    if (!isDestroyed()) {
      buildItems();
    }
  };

  public XgramCommunityController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static void open (ViewController<?> context, long communityId) {
    if (communityId == 0) {
      return;
    }
    XgramCommunityController c = new XgramCommunityController(context.context(), context.tdlib());
    c.setArguments(communityId);
    if (com.fahadali.xgram.layout.XgramTwoPane.replacesRightPane(context)) {
      com.fahadali.xgram.layout.XgramTwoPane.showOnRight(context, c); // two panes: replace, don't stack
      return;
    }
    // Chat lists live in the main screen's pager, where navigateTo() does nothing: use the activity's navigation.
    org.thunderdog.challegram.navigation.NavigationController navigation = context.navigationController();
    if (navigation == null) {
      navigation = context.context().navigation();
    }
    if (navigation != null) {
      navigation.navigateTo(c);
    }
  }

  private long communityId () {
    Long id = getArguments();
    return id != null ? id : 0;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramCommunity;
  }

  @Override
  public CharSequence getName () {
    TdApi.Community community = tdlib.cache().community(communityId());
    return community != null && !community.name.isEmpty() ? community.name : Lang.getString(R.string.XgramCommunity);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    recyclerView.setAdapter(adapter);
    tdlib.cache().subscribeToCommunityUpdates(communityId(), this);
    XgramCommunities.addListener(this);
    tdlib.send(new TdApi.GetCommunityFullInfo(communityId()), (full, error) -> {
      if (full != null) {
        tdlib.cache().xgramOnCommunityFullLoaded(communityId(), full);
      }
      if (error != null) {
        android.util.Log.w("XGram", "GetCommunityFullInfo " + communityId() + ": " + error.code + " " + error.message);
      }
      runOnUiThreadOptional(() -> {
        if (error != null) {
          loadError = error.message;
          useKnownChats = true;
        } else {
          fetchedFull = full;
        }
        buildItems();
      });
    });
    tdlib.ui().postDelayed(() -> {
      if (!isDestroyed()) {
        if (communityFull() == null) {
          useKnownChats = true;
        }
        buildItems();
      }
    }, 2500);
    buildItems();
  }

  // Like chats and topic lists: slide in once the chats are there, not on "Loading" swapped out mid-slide.
  @Override
  public boolean needAsynchronousAnimation () {
    return communityFull() == null && !useKnownChats;
  }

  @Nullable
  private TdApi.CommunityFullInfo communityFull () {
    TdApi.CommunityFullInfo full = tdlib.cache().communityFull(communityId());
    return full != null ? full : fetchedFull;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().unsubscribeFromCommunityUpdates(communityId(), this);
    XgramCommunities.removeListener(this);
    tdlib.ui().removeCallbacks(rebuild);
  }

  @Override
  public void onCommunityMembersChanged (Tdlib tdlib, long communityId, long chatId) {
    if (tdlib == this.tdlib && communityId == communityId()) {
      // Any thread, often several in a row: rebuild once, on the UI thread.
      this.tdlib.ui().removeCallbacks(rebuild);
      this.tdlib.ui().postDelayed(rebuild, 150);
    }
  }

  @Override
  public void onCommunityUpdated (TdApi.Community community) {
    runOnUiThreadOptional(() -> {
      if (headerView != null) {
        headerView.updateTextTitle(getId(), getName());
      }
      buildItems();
    });
  }

  @Override
  public void onCommunityFullUpdated (long id, TdApi.CommunityFullInfo community) {
    runOnUiThreadOptional(this::buildItems);
  }

  private void buildItems () {
    TdApi.CommunityFullInfo full = communityFull();
    List<ListItem> items = new ArrayList<>();
    List<Long> chatIds = new ArrayList<>();
    if (full != null) {
      // Every chat, is_hidden included: TDLib reports it as true whenever the server leaves the flag out, which it does
      // for members who aren't admins, so filtering on it emptied the page. Admins see hidden chats in their own section.
      for (TdApi.CommunityChat communityChat : full.chats) {
        if (!XgramCommunities.isConfirmed(tdlib, communityChat.chatId)) {
          XgramCommunities.remember(tdlib, communityChat.chatId, communityId());
        }
      }
      // TDLib's copy can be old: chats whose own (fresher) info says they left or moved are dropped, and chats known
      // to have joined since are added.
      chatIds.addAll(XgramCommunityData.chatIds(tdlib, communityId(), full));
    } else if (useKnownChats) {
      // TDLib didn't send the community's chat list: the chats this device saw being added to it.
      chatIds.addAll(XgramCommunities.chatsOf(tdlib, communityId()));
    } else {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramCommunityLoading));
      adapter.setItems(items, false);
      return;
    }
    if (!refreshed && !isInForceTouchMode() && !chatIds.isEmpty()) {
      refreshed = true;
      XgramCommunityData.refresh(tdlib, communityId(), chatIds);
    }
    boolean honourHidden = XgramCommunityData.honourHidden(full, XgramCommunityData.isAdmin(tdlib, communityId()));
    List<ListItem> joined = new ArrayList<>(), others = new ArrayList<>(), hidden = new ArrayList<>();
    for (long chatId : chatIds) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        // Not known to TDLib yet: fetch it, the list is built again once it's there.
        if (requestedChats.add(chatId)) {
          pendingChats.add(chatId);
          tdlib.send(new TdApi.GetChat(chatId), (result, error) -> runOnUiThreadOptional(() -> {
            pendingChats.remove(chatId);
            if (result == null) {
              requestedChats.remove(chatId); // a later build asks once more
            }
            buildItems();
          }));
        }
        continue;
      }
      ListItem item = new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_xgramCommunityChat).setData(new DoubleTextWrapper(tdlib, chat, true, false));
      item.setLongId(chat.id);
      if (honourHidden && XgramCommunityData.isHidden(full, chat.id)) {
        hidden.add(item);
      } else {
        (XgramCommunityData.isMember(tdlib, chat) ? joined : others).add(item);
      }
    }
    addSection(items, R.string.XgramCommunityChatsIn, joined);
    addSection(items, R.string.XgramCommunityOtherChats, others);
    addSection(items, R.string.XgramCommunityHiddenChats, hidden);
    if (items.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, pendingChats.isEmpty() ? R.string.XgramCommunityEmpty : R.string.XgramCommunityLoading));
    }
    if (full == null) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, loadError != null ? Lang.getString(R.string.XgramCommunityPartialError, loadError) : Lang.getString(R.string.XgramCommunityPartial)));
    }
    adapter.setItems(items, false);
    executeScheduledAnimation(); // the chats are in: start the slide if it was waiting for them
  }

  private static void addSection (List<ListItem> items, int title, List<ListItem> rows) {
    if (rows.isEmpty()) {
      return;
    }
    items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, title));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(rows.get(i));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramCommunityChat && tag instanceof ListItem) {
      long chatId = ((ListItem) tag).getLongId();
      if (chatId != 0) {
        tdlib.ui().openChat(this, chatId, new TdlibUi.ChatOpenParameters().keepStack());
      }
    }
  }

  /** The community a group or channel belongs to, from its full info (0 if none or not loaded yet). */
  public static long communityOf (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null) {
      return 0;
    }
    if (chat.type instanceof TdApi.ChatTypePrivate) {
      return botCommunityOf(tdlib, chat.id); // communities hold bot chats too
    }
    if (!(chat.type instanceof TdApi.ChatTypeSupergroup)) {
      return 0;
    }
    TdApi.SupergroupFullInfo full = tdlib.cache().supergroupFull(((TdApi.ChatTypeSupergroup) chat.type).supergroupId);
    return full != null ? full.communityId : XgramCommunities.communityOf(tdlib, chat.id);
  }

  /** The community a bot chat belongs to, from the bot's full info, else as far as this device has seen. */
  public static long botCommunityOf (Tdlib tdlib, long chatId) {
    TdApi.UserFullInfo full = tdlib.cache().userFull(ChatId.toUserId(chatId), false);
    return full != null && full.communityId != 0 ? full.communityId : XgramCommunities.communityOf(tdlib, chatId);
  }
}
