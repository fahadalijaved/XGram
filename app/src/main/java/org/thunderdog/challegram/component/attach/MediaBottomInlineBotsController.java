/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.SimpleUsersAdapter;
import org.thunderdog.challegram.data.TGUser;

import java.util.ArrayList;
import java.util.List;

public class MediaBottomInlineBotsController extends MediaBottomBaseController<Void> implements Client.ResultHandler, SimpleUsersAdapter.Callback {
  public MediaBottomInlineBotsController (MediaLayout context) {
    super(context, R.string.InlineBot);
  }

  @Override
  public int getId () {
    return R.id.controller_media_inlineBots;
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(true);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
    setAdapter(adapter = new SimpleUsersAdapter(this, this, SimpleUsersAdapter.OPTION_CLICKABLE, this));
    RecyclerView.ItemDecoration xgramTiles = SimpleUsersAdapter.xgramNewTileDecoration();
    if (xgramTiles != null) {
      addItemDecoration(xgramTiles); // XGram M3E: the bots as segmented tiles on the sheet
      recyclerView.setItemAnimator(com.fahadali.xgram.appearance.XgramAppMotion.listAnimator());
    }
    tdlib.client().send(new TdApi.GetTopChats(new TdApi.TopChatCategoryInlineBots(), 50), this);
    return contentView;
  }

  private SimpleUsersAdapter adapter;

  @Override
  protected int xgramErrorIcon () {
    return com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.deproko_baseline_bots_56); // XGram: no bots: a bot
  }

  protected void displayBots (final List<TGUser> users) {
    if (users.isEmpty()) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram M3E: the empty state (XgramListStateView) says what is missing, not "Nothing found"
        showError(org.thunderdog.challegram.core.Lang.getString(R.string.XgramR6B7NoInlineBots), null, null, true);
        return;
      }
      showError(R.string.NothingFound, 0, null, true);
    } else {
      hideProgress(() -> {
        adapter.setUsers(users);
        expandStartHeight(adapter);
      });
    }
  }

  /*@Override
  protected int getInitialContentHeight () {
    return asdfScreen.dp(64);
  }*/

  @Override
  public void onUserPicked (TGUser user) {
    mediaLayout.chooseInlineBot(user);
  }

  @Override
  public void onUserSelected (int selectedCount, TGUser user, boolean isSelected) { }

  private static final String[] DEFAULT_BOTS = { "bing", "wiki", "gif", "nephobot", "vid" };

  private ArrayList<TGUser> defaultBots = new ArrayList<>(DEFAULT_BOTS.length);
  private int defaultIndex; // XGram: the next popular bot to look up (one that can't be found is skipped)
  private boolean loadingDefaults;

  private void loadNextBot () {
    if (defaultIndex > DEFAULT_BOTS.length) {
      return;
    }
    if (defaultIndex == DEFAULT_BOTS.length) {
      defaultIndex++;
      runOnUiThread(() -> displayBots(defaultBots));
    } else {
      loadingDefaults = true;
      tdlib.client().send(new TdApi.SearchPublicChat(DEFAULT_BOTS[defaultIndex]), this);
    }
  }

  private void addDefaultBot (@Nullable TdApi.User user) {
    defaultIndex++;
    if (user != null) {
      defaultBots.add(TGUser.createWithUsername(tdlib, user));
    }
    loadNextBot();
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Error.CONSTRUCTOR: {
        // XGram: no raw error here ("#400: Top chat computation is disabled" when Suggest frequent contacts is off):
        // the popular inline bots stand in for your frequent ones, and a popular one that can't be found is skipped
        if (loadingDefaults) {
          addDefaultBot(null);
        } else {
          loadNextBot();
        }
        break;
      }
      case TdApi.Chat.CONSTRUCTOR: {
        addDefaultBot(tdlib.chatUser((TdApi.Chat) object));
        break;
      }
      case TdApi.Chats.CONSTRUCTOR: {
        long[] chatIds = ((TdApi.Chats) object).chatIds;
        if (chatIds.length == 0) {
          loadNextBot();
          break;
        }

        final List<TGUser> users = new ArrayList<>(chatIds.length);
        final List<TdApi.Chat> chats = tdlib.chats(chatIds);
        for (TdApi.Chat chat : chats) {
          TdApi.User user = tdlib.chatUser(chat);
          if (user != null) {
            users.add(TGUser.createWithUsername(tdlib, user));
          }
        }

        if (users.isEmpty()) {
          tdlib.client().send(new TdApi.GetRecentInlineBots(), this);
        } else {
          runOnUiThread(() -> displayBots(users));
        }

        break;
      }
      case TdApi.Users.CONSTRUCTOR: {
        long[] userIds = ((TdApi.Users) object).userIds;
        if (userIds.length == 0) {
          loadNextBot();
          break;
        }
        final ArrayList<TdApi.User> users = tdlib.cache().users(userIds);
        final ArrayList<TGUser> parsedUsers = new ArrayList<>(userIds.length);
        for (TdApi.User user : users) {
          parsedUsers.add(TGUser.createWithUsername(tdlib, user));
        }
        runOnUiThread(() -> displayBots(parsedUsers));
        break;
      }
    }
  }
}
