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
import com.fahadali.xgram.topics.XgramForums;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tgx.td.MessageId;

/** Settings → XGram → Bookmarks: messages bookmarked on this device. */
public final class XgramBookmarksController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener {
  private SettingsAdapter adapter;
  private final Map<String, String> previews = new HashMap<>();
  private final Map<String, TdApi.Message> messages = new HashMap<>();
  private final java.util.Set<String> loadingPreviews = new java.util.HashSet<>();
  private boolean focusedOnce;

  public XgramBookmarksController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramBookmarks;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramBookmarks);
  }

  private static String keyOf (XgramBookmarks.Bookmark bookmark) {
    return bookmark.chatId + "_" + bookmark.messageId;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof XgramBookmarks.Bookmark) {
          XgramBookmarks.Bookmark bookmark = (XgramBookmarks.Bookmark) data;
          view.setName(tdlib.chatTitle(bookmark.chatId));
          String preview = previews.get(keyOf(bookmark));
          view.setData(preview != null ? preview : Lang.getString(R.string.LoadingInformation));
        }
      }
    };
    adapter.setOnLongClickListener(this);
    rebuild();
    recyclerView.setAdapter(adapter);
  }

  // Coming back (e.g. after removing a bookmark from a chat) shows the current list.
  @Override
  public void onFocus () {
    super.onFocus();
    if (!focusedOnce) {
      focusedOnce = true;
    } else if (adapter != null) {
      rebuild();
    }
  }

  private void rebuild () {
    List<XgramBookmarks.Bookmark> bookmarks = XgramBookmarks.list(tdlib);
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (bookmarks.isEmpty() && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E: the empty state (an icon on a soft burst, the hint under it)
      items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_bookmark_24, R.string.XgramBookmarksEmpty));
    } else if (bookmarks.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramBookmarksEmpty));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (XgramBookmarks.Bookmark bookmark : bookmarks) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        first = false;
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_xgramBookmarkItem, R.drawable.baseline_bookmark_24, "", false).setData(bookmark));
        loadPreview(bookmark);
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramBookmarksInfo));
    }
    adapter.setItems(items, false);
  }

  private void loadPreview (XgramBookmarks.Bookmark bookmark) {
    String key = keyOf(bookmark);
    if (previews.containsKey(key) || !loadingPreviews.add(key)) {
      return;
    }
    tdlib.send(new TdApi.GetMessage(bookmark.chatId, bookmark.messageId), (message, error) -> runOnUiThreadOptional(() -> {
      String preview;
      if (message != null) {
        messages.put(key, message);
        TdApi.FormattedText text = XgramMedia.formattedTextOf(message.content);
        preview = text != null && !text.text.isEmpty() ? text.text.replace('\n', ' ') : Lang.getString(R.string.XgramBookmarkMedia);
      } else {
        preview = Lang.getString(R.string.XgramBookmarkUnavailable);
      }
      previews.put(key, preview);
      loadingPreviews.remove(key);
      // The list may have been rebuilt meanwhile: update the row of this message, whichever object it holds.
      int index = adapter.indexOfView(item -> item.getData() instanceof XgramBookmarks.Bookmark && keyOf((XgramBookmarks.Bookmark) item.getData()).equals(key));
      if (index != -1) {
        adapter.updateValuedSettingByPosition(index);
      }
    }));
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (!(data instanceof XgramBookmarks.Bookmark)) {
      return;
    }
    XgramBookmarks.Bookmark bookmark = (XgramBookmarks.Bookmark) data;
    TdApi.Message message = messages.get(keyOf(bookmark));
    if (message != null && message.topicId instanceof TdApi.MessageTopicForum) {
      XgramForums.openTopicLink(this, bookmark.chatId, (TdApi.MessageTopicForum) message.topicId, message, null);
    } else {
      tdlib.ui().openChat(this, bookmark.chatId, new TdlibUi.ChatOpenParameters()
        .highlightMessage(new MessageId(bookmark.chatId, bookmark.messageId))
        .ensureHighlightAvailable());
    }
  }

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (!(data instanceof XgramBookmarks.Bookmark)) {
      return false;
    }
    XgramBookmarks.Bookmark bookmark = (XgramBookmarks.Bookmark) data;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramShowMenu(v, bookmark);
      return true;
    }
    showOptions(null, new int[] {R.id.btn_delete, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramRemoveBookmark), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.RED, OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_delete) {
          XgramBookmarks.setBookmarked(tdlib, bookmark.chatId, bookmark.messageId, false);
          rebuild();
        }
        return true;
      });
    return true;
  }

  // XGram M3E (round 6): a long press opens the M3 menu at the row (Remove bookmark in error), then a snackbar with
  // Undo; the Compose rows read the chat, the message's preview and date here (TgxPagesBatch2).

  private void xgramShowMenu (View anchor, XgramBookmarks.Bookmark bookmark) {
    List<com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry> entries = new ArrayList<>();
    entries.add(new com.fahadali.xgram.appearance.XgramR5Chat.MenuEntry(R.id.btn_delete, Lang.getString(R.string.XgramRemoveBookmark), R.drawable.baseline_delete_24, () -> {
      if (isDestroyed()) {
        return;
      }
      XgramBookmarks.setBookmarked(tdlib, bookmark.chatId, bookmark.messageId, false);
      rebuild();
      com.fahadali.xgram.appearance.XgramSnackbar.showUndo(Lang.getString(R.string.XgramR6B5BookmarkRemoved), () -> {
        // Back where it was in the list, with its own date while the message loads
        XgramBookmarks.setBookmarked(tdlib, bookmark.chatId, bookmark.messageId, bookmark.addedAt);
        if (!isDestroyed()) {
          rebuild();
        }
      });
    }).destructive());
    com.fahadali.xgram.appearance.XgramR5Chat.showMenu(anchor, getThemeListeners(), entries);
  }

  /** The message's text (or "Media"), or null while it loads. */
  @androidx.annotation.Nullable
  public String xgramPreviewOf (XgramBookmarks.Bookmark bookmark) {
    return previews.get(keyOf(bookmark));
  }

  /** When the message was sent (unix seconds), or when it was bookmarked while the message loads. */
  public long xgramDateOf (XgramBookmarks.Bookmark bookmark) {
    TdApi.Message message = messages.get(keyOf(bookmark));
    return message != null ? message.date : bookmark.addedAt / 1000L;
  }
}
