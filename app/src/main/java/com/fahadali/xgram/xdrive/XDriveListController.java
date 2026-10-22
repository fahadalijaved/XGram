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
package com.fahadali.xgram.xdrive;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.history.XgramMedia;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * XDrive's lists: Notes (the texts in Saved Messages, newest first) or one folder's messages. A tap shows the message in
 * the chat; a long press moves it between folders.
 */
public final class XDriveListController extends RecyclerViewController<XDriveListController.Args> implements View.OnClickListener, View.OnLongClickListener, XDriveIndex.Listener {
  public static final int MODE_NOTES = 0, MODE_FOLDER = 1;
  private static final int PAGE = 100, NOTES_TARGET = 60;

  public static final class Args {
    final int mode;
    final @Nullable String folderId;

    public Args (int mode, @Nullable String folderId) {
      this.mode = mode;
      this.folderId = folderId;
    }
  }

  private SettingsAdapter adapter;
  private XDriveIndex index;
  private final LinkedHashMap<Long, TdApi.Message> messages = new LinkedHashMap<>();
  private boolean loading, reachedEnd;
  private long nextFromId;

  public XDriveListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private Args args () {
    Args args = getArguments();
    return args != null ? args : new Args(MODE_NOTES, null);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramXDriveList;
  }

  @Override
  public CharSequence getName () {
    if (args().mode == MODE_FOLDER && args().folderId != null) {
      XDriveIndex.Folder folder = XDriveIndex.of(tdlib).findFolder(args().folderId);
      if (folder != null) {
        return folder.name;
      }
    }
    return Lang.getString(R.string.XgramXDriveNotes);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    index = XDriveIndex.of(tdlib);
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof TdApi.Message) {
          TdApi.Message message = (TdApi.Message) data;
          view.setName(titleOf(message));
          view.setData(com.fahadali.xgram.appearance.XgramR6B5Pages.shortDate(message.date));
        }
      }
    };
    adapter.setOnLongClickListener(this);
    index.addListener(this);
    rebuild();
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView view, int dx, int dy) {
        if (args().mode == MODE_NOTES && !view.canScrollVertically(1)) {
          loadMoreNotes();
        }
      }
    });
    index.load(this::load);
  }

  @Override
  public void destroy () {
    super.destroy();
    if (index != null) {
      index.removeListener(this);
    }
  }

  @Override
  public void onIndexChanged () {
    if (!isDestroyed() && args().mode == MODE_FOLDER) {
      load(); // moved out, deleted, renamed
    }
  }

  // Loading

  private void load () {
    if (args().mode == MODE_FOLDER) {
      loadFolder();
    } else if (messages.isEmpty()) {
      loadMoreNotes();
    }
  }

  private void loadFolder () {
    final String folderId = args().folderId;
    if (folderId == null || index.findFolder(folderId) == null) {
      navigateBack();
      return;
    }
    final long[] ids = index.messagesIn(folderId);
    if (ids.length == 0) {
      messages.clear();
      reachedEnd = true;
      rebuild();
      return;
    }
    loading = true;
    rebuild();
    final long chatId = tdlib.selfChatId();
    final LinkedHashMap<Long, TdApi.Message> found = new LinkedHashMap<>();
    final List<Long> missing = new ArrayList<>();
    loadChunk(chatId, ids, 0, found, missing);
  }

  private void loadChunk (long chatId, long[] ids, int start, LinkedHashMap<Long, TdApi.Message> found, List<Long> missing) {
    int end = Math.min(ids.length, start + PAGE);
    long[] chunk = new long[end - start];
    System.arraycopy(ids, start, chunk, 0, chunk.length);
    tdlib.send(new TdApi.GetMessages(chatId, chunk), (result, error) -> runOnUiThreadOptional(() -> {
      if (result != null) {
        for (int i = 0; i < chunk.length; i++) {
          TdApi.Message message = i < result.messages.length ? result.messages[i] : null;
          if (message != null) {
            found.put(message.id, message);
          } else {
            missing.add(chunk[i]);
          }
        }
      }
      if (end < ids.length) {
        loadChunk(chatId, ids, end, found, missing);
        return;
      }
      if (!missing.isEmpty() && error == null) {
        index.forget(missing); // deleted in the chat (or on another device)
      }
      messages.clear();
      messages.putAll(found);
      loading = false;
      reachedEnd = true;
      rebuild();
    }));
  }

  /** Notes: Saved Messages' texts, a page of history at a time until some are found or the history ends. */
  private void loadMoreNotes () {
    if (loading || reachedEnd) {
      return;
    }
    loading = true;
    rebuild();
    loadNotesPage(0);
  }

  private void loadNotesPage (int foundSoFar) {
    final long chatId = tdlib.selfChatId();
    tdlib.send(new TdApi.GetChatHistory(chatId, nextFromId, 0, PAGE, false), (result, error) -> runOnUiThreadOptional(() -> {
      int added = 0, seen = 0;
      long oldest = nextFromId;
      if (result != null) {
        for (TdApi.Message message : result.messages) {
          if (message == null || messages.containsKey(message.id) || message.id == nextFromId) {
            continue;
          }
          seen++;
          if (oldest == 0 || message.id < oldest) {
            oldest = message.id;
          }
          if (message.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
            messages.put(message.id, message);
            added++;
          }
        }
      }
      if (seen == 0) {
        reachedEnd = true;
      }
      nextFromId = oldest;
      if (!reachedEnd && foundSoFar + added < NOTES_TARGET) {
        loadNotesPage(foundSoFar + added);
        return;
      }
      loading = false;
      rebuild();
    }));
  }

  private void rebuild () {
    if (adapter == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (messages.isEmpty()) {
      if (loading) {
        items.add(new ListItem(ListItem.TYPE_PROGRESS));
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION_CENTERED, 0, 0, args().mode == MODE_FOLDER ? R.string.XgramXDriveEmptyFolder : R.string.XgramXDriveNoNotes));
      }
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      boolean first = true;
      for (TdApi.Message message : messages.values()) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramXDriveItem, iconOf(message), titleOf(message), false).setData(message).setLongId(message.id));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (loading) {
        items.add(new ListItem(ListItem.TYPE_PROGRESS));
      }
    }
    adapter.setItems(items, false);
  }

  /** The first line of the message's text or caption, or what it is ("Photo", a file's name). */
  static String titleOf (TdApi.Message message) {
    TdApi.FormattedText text = XgramMedia.formattedTextOf(message.content);
    if (text != null && !text.text.trim().isEmpty()) {
      String t = text.text.trim();
      int newline = t.indexOf('\n');
      if (newline != -1) {
        t = t.substring(0, newline);
      }
      return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR:
        return ((TdApi.MessageDocument) message.content).document.fileName;
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return Lang.getString(R.string.Photo);
      case TdApi.MessageVideo.CONSTRUCTOR:
        return Lang.getString(R.string.Video);
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return Lang.getString(R.string.Gif);
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
        return !audio.title.isEmpty() ? audio.title : !audio.fileName.isEmpty() ? audio.fileName : Lang.getString(R.string.Audio);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return Lang.getString(R.string.XgramXDriveVoice);
      case TdApi.MessageSticker.CONSTRUCTOR:
        return Lang.getString(R.string.Sticker);
      case TdApi.MessageLocation.CONSTRUCTOR:
        return Lang.getString(R.string.Location);
    }
    return Lang.getString(R.string.File);
  }

  static int iconOf (TdApi.Message message) {
    switch (message.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR:
        return ((TdApi.MessageText) message.content).linkPreview != null ? R.drawable.baseline_link_24 : R.drawable.baseline_format_text_24;
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
        return R.drawable.baseline_image_24;
      case TdApi.MessageAnimation.CONSTRUCTOR:
        return R.drawable.deproko_baseline_gif_24;
      case TdApi.MessageAudio.CONSTRUCTOR:
        return R.drawable.baseline_music_note_24;
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return R.drawable.baseline_mic_24;
    }
    return R.drawable.baseline_insert_drive_file_24;
  }

  // Taps

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (data instanceof TdApi.Message) {
      XDriveController.openChat(this, ((TdApi.Message) data).id);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (!(data instanceof TdApi.Message)) {
      return false;
    }
    final TdApi.Message message = (TdApi.Message) data;
    final long[] ids = {message.id};
    if (args().mode == MODE_FOLDER) {
      showOptions(titleOf(message), new int[] {R.id.btn_xgramXDriveOpen, R.id.btn_xgramXDriveMove, R.id.btn_xgramXDriveRemove},
        new String[] {Lang.getString(R.string.XgramXDriveShowInChat), Lang.getString(R.string.XgramXDriveMoveToFolder), Lang.getString(R.string.XgramXDriveRemoveFromFolder)},
        new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.RED},
        new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_folder_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
          if (id == R.id.btn_xgramXDriveOpen) {
            XDriveController.openChat(this, message.id);
          } else if (id == R.id.btn_xgramXDriveMove) {
            XDriveController.pickFolder(this, ids);
          } else if (id == R.id.btn_xgramXDriveRemove) {
            index.setFolder(ids, null);
          }
          return true;
        });
    } else {
      showOptions(titleOf(message), new int[] {R.id.btn_xgramXDriveOpen, R.id.btn_xgramXDriveAddToFolder},
        new String[] {Lang.getString(R.string.XgramXDriveShowInChat), Lang.getString(R.string.XgramXDriveAddToFolder)},
        new int[] {OptionColor.NORMAL, OptionColor.NORMAL},
        new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_create_new_folder_24}, (itemView, id) -> {
          if (id == R.id.btn_xgramXDriveOpen) {
            XDriveController.openChat(this, message.id);
          } else if (id == R.id.btn_xgramXDriveAddToFolder) {
            XDriveController.pickFolder(this, ids);
          }
          return true;
        });
    }
    return true;
  }
}
