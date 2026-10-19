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

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5People;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity's hidden chats (a long press on a row hides a chat's activity): their rows are still collected, so a tap
 * here shows them again, history included (with Undo). Both looks.
 */
public final class XgramActivityHiddenController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private long[] chats = new long[0];
  private boolean loaded;
  private final Set<Long> requested = new HashSet<>();

  public XgramActivityHiddenController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramActivityHidden;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramActivityHiddenChats);
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
          XgramR5People.bindState(view, true, R.drawable.baseline_eye_off_24, Lang.getString(R.string.XgramActivityHiddenEmpty), null);
        } else if (customViewType == XgramR5People.LOADING_ROW) {
          XgramR5People.bindState(view, true, 0, null, null);
        } else {
          super.modifyCustom(holder, position, item, customViewType, view, isUpdate);
        }
      }
    };
    build();
    recyclerView.setAdapter(adapter);
    load();
  }

  private void load () {
    XgramActivityStore.instance().hiddenChats(tdlib.id(), result -> {
      if (isDestroyed()) {
        return;
      }
      chats = result;
      loaded = true;
      build();
    });
  }

  private void build () {
    final boolean expressive = XgramExpressive.isEnabled();
    List<ListItem> items = new ArrayList<>();
    if (!loaded) {
      items.add(expressive ? new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.LOADING_ROW) : new ListItem(ListItem.TYPE_PROGRESS));
    } else if (chats.length == 0) {
      if (expressive) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.STATE_ROW));
      } else {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.XgramActivityHiddenEmpty));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (long chatId : chats) {
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null) {
          if (requested.add(chatId)) { // not loaded yet: built again once it is
            tdlib.send(new TdApi.GetChat(chatId), (result, error) -> runOnUiThreadOptional(this::build));
          }
          continue;
        }
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        first = false;
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
        wrapper.setForcedSubtitle(Lang.getString(R.string.XgramActivityUnhide));
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_xgramActivityHiddenChat).setData(wrapper).setLongId(chatId)
          .setStringValue(Lang.getString(R.string.XgramActivityUnhide)));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() != R.id.btn_xgramActivityHiddenChat || !(v.getTag() instanceof ListItem)) {
      return;
    }
    final long chatId = ((ListItem) v.getTag()).getLongId();
    if (chatId == 0) {
      return;
    }
    final XgramActivityStore store = XgramActivityStore.instance();
    store.setChatHidden(tdlib.id(), chatId, false);
    List<Long> left = new ArrayList<>();
    for (long id : chats) {
      if (id != chatId) {
        left.add(id);
      }
    }
    chats = new long[left.size()];
    for (int i = 0; i < chats.length; i++) {
      chats[i] = left.get(i);
    }
    build();
    final String title = tdlib.chatTitle(chatId);
    Runnable undo = () -> {
      store.setChatHidden(tdlib.id(), chatId, true);
      if (!isDestroyed()) {
        load();
      }
    };
    if (!com.fahadali.xgram.appearance.XgramSnackbar.showUndo(Lang.getString(R.string.XgramActivityChatShown, title), undo)) {
      UI.showToast(Lang.getString(R.string.XgramActivityChatShown, title), Toast.LENGTH_SHORT);
    }
  }
}
