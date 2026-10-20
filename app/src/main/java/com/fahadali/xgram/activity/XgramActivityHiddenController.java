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

import com.fahadali.xgram.appearance.XgramExpressive;
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
  @NonNull private List<XgramActivityStore.HiddenChat> chats = new ArrayList<>();
  private boolean loaded;
  private final Set<Long> requested = new HashSet<>();
  private final XgramActivityUndoBar classicUndo = new XgramActivityUndoBar(this);

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
    } else if (chats.isEmpty()) {
      addEmpty(items, expressive);
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (XgramActivityStore.HiddenChat hidden : chats) {
        final long chatId = hidden.chatId;
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null && requested.add(chatId)) {
          // Not loaded yet: built again once it is (until then, and if TDLib can't find it, by its saved title)
          tdlib.send(new TdApi.GetChat(chatId), (result, error) -> runOnUiThreadOptional(this::build));
        }
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        first = false;
        final DoubleTextWrapper wrapper;
        if (chat != null) {
          wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
          wrapper.setForcedSubtitle(Lang.getString(R.string.XgramActivityUnhide));
        } else {
          // A chat this device doesn't have (left, or after a new login): it can still be shown again
          wrapper = new DoubleTextWrapper(tdlib, titleOf(hidden), Lang.getString(R.string.XgramActivityUnhide),
            new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.INACTIVE), R.drawable.baseline_eye_off_24));
        }
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_xgramActivityHiddenChat).setData(wrapper).setLongId(chatId)
          .setStringValue(Lang.getString(R.string.XgramActivityUnhide)));
      }
      if (first) {
        // No row came out of it: the empty state, not an empty group
        items.clear();
        addEmpty(items, expressive);
      } else {
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }
    adapter.setItems(items, false);
  }

  private static void addEmpty (List<ListItem> items, boolean expressive) {
    if (expressive) {
      items.add(new ListItem(ListItem.TYPE_CUSTOM - XgramR5People.STATE_ROW));
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.XgramActivityHiddenEmpty));
    }
  }

  /** The chat's title now, else the one its items saved, else "Chat unavailable". */
  @NonNull
  private String titleOf (@NonNull XgramActivityStore.HiddenChat hidden) {
    String title = tdlib.chat(hidden.chatId) != null ? tdlib.chatTitle(hidden.chatId) : null;
    if (title == null || title.isEmpty()) {
      title = hidden.savedTitle;
    }
    return title != null && !title.isEmpty() ? title : Lang.getString(R.string.XgramActivityChatUnavailable);
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
    String title = Lang.getString(R.string.XgramActivityChatUnavailable);
    List<XgramActivityStore.HiddenChat> left = new ArrayList<>();
    for (XgramActivityStore.HiddenChat hidden : chats) {
      if (hidden.chatId != chatId) {
        left.add(hidden);
      } else {
        title = titleOf(hidden);
      }
    }
    chats = left;
    build();
    Runnable undo = () -> {
      store.setChatHidden(tdlib.id(), chatId, true);
      if (!isDestroyed()) {
        load();
      }
    };
    // Undo in both looks: Material 3's snackbar, or Telegram X's SnackBar
    final String text = Lang.getString(R.string.XgramActivityChatShown, title);
    if (XgramExpressive.isEnabled() ? !com.fahadali.xgram.appearance.XgramSnackbar.showUndo(text, undo) : !classicUndo.show(text, undo, extraBottomInset)) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  @Override
  public void destroy () {
    classicUndo.destroy();
    super.destroy();
  }
}
