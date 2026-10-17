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

import com.fahadali.xgram.history.XgramMedia;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Saved Messages → ⋮ → By chat: Saved Messages grouped by the chat they were saved from (official Telegram). */
public final class XgramSavedTopicsController extends RecyclerViewController<Void> implements View.OnClickListener, XgramSavedTopics.Listener {
  private SettingsAdapter adapter;
  private boolean loading = true;

  public XgramSavedTopicsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramSavedTopics;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramSavedByChat);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this);
    rebuild();
    recyclerView.setAdapter(adapter);
    XgramSavedTopics.addListener(this);
    XgramSavedTopics.loadAll(tdlib, () -> {
      if (!isDestroyed()) {
        loading = false;
        rebuild();
      }
    });
  }

  @Override
  public void onSavedTopicsChanged (Tdlib tdlib) {
    if (tdlib == this.tdlib && !isDestroyed() && !loading) {
      rebuild();
    }
  }

  private void rebuild () {
    List<TdApi.SavedMessagesTopic> topics = XgramSavedTopics.list(tdlib);
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (topics.isEmpty()) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram M3E: the loading indicator, then the empty state (an icon on a soft burst)
        items.add(loading ? new ListItem(ListItem.TYPE_PROGRESS) : new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_bookmark_24, R.string.XgramSavedByChatEmpty));
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, loading ? R.string.LoadingInformation : R.string.XgramSavedByChatEmpty));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      for (TdApi.SavedMessagesTopic topic : topics) {
        ListItem item = new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_xgramSavedTopic).setData(wrap(topic)).setLongId(topic.id);
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          // XGram M3E: what the Compose row shows (TgxChatRowsHints): the last message and its date
          item.setStringValue(preview(topic.lastMessage));
          item.setIntValue(topic.lastMessage != null ? topic.lastMessage.date : 0);
        }
        items.add(item);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    adapter.setItems(items, false);
  }

  private DoubleTextWrapper wrap (TdApi.SavedMessagesTopic topic) {
    String preview = preview(topic.lastMessage);
    switch (topic.type.getConstructor()) {
      case TdApi.SavedMessagesTopicTypeSavedFromChat.CONSTRUCTOR: {
        TdApi.Chat chat = tdlib.chat(((TdApi.SavedMessagesTopicTypeSavedFromChat) topic.type).chatId);
        if (chat != null) {
          DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, false, false);
          wrapper.setForcedSubtitle(preview);
          return wrapper;
        }
        return new DoubleTextWrapper(tdlib, XgramSavedTopics.title(tdlib, topic), preview,
          new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.INACTIVE), R.drawable.baseline_person_24));
      }
      case TdApi.SavedMessagesTopicTypeMyNotes.CONSTRUCTOR:
        return new DoubleTextWrapper(tdlib, XgramSavedTopics.title(tdlib, topic), preview,
          new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.SAVED_MESSAGES), R.drawable.baseline_edit_24));
      case TdApi.SavedMessagesTopicTypeAuthorHidden.CONSTRUCTOR:
      default:
        return new DoubleTextWrapper(tdlib, XgramSavedTopics.title(tdlib, topic), preview,
          new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.INACTIVE), R.drawable.baseline_person_24));
    }
  }

  private static String preview (TdApi.Message message) {
    if (message == null) {
      return "";
    }
    TdApi.FormattedText text = XgramMedia.formattedTextOf(message.content);
    return text != null && !text.text.isEmpty() ? text.text.replace('\n', ' ') : Lang.getString(R.string.XgramBookmarkMedia);
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (!(tag instanceof ListItem) || ((ListItem) tag).getId() != R.id.btn_xgramSavedTopic) {
      return;
    }
    long savedMessagesTopicId = ((ListItem) tag).getLongId();
    TdApi.Chat selfChat = tdlib.chat(tdlib.selfChatId());
    if (selfChat == null) {
      return;
    }
    MessagesController c = new MessagesController(context, tdlib);
    c.setArguments(new MessagesController.Arguments(null, selfChat, new TdApi.MessageTopicSavedMessages(savedMessagesTopicId)));
    navigateTo(c);
  }

  @Override
  public void destroy () {
    super.destroy();
    XgramSavedTopics.removeListener(this);
  }
}
