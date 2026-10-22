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
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.chats.XgramSavedTopics;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * XDrive: Saved Messages as a drive. The chat itself, sections over what is already there (photos and videos, files,
 * links, music, voice, GIFs: Saved Messages' own media pages; notes; from chats), and the user's folders (XDriveIndex).
 */
public final class XDriveController extends RecyclerViewController<Void> implements View.OnClickListener, View.OnLongClickListener, XDriveIndex.Listener, XgramSavedTopics.Listener {
  private static final int[] SECTION_FILTERS = {
    TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR,
    TdApi.SearchMessagesFilterDocument.CONSTRUCTOR,
    TdApi.SearchMessagesFilterUrl.CONSTRUCTOR,
    TdApi.SearchMessagesFilterAudio.CONSTRUCTOR,
    TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR,
    TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR,
  };
  private static final int[] SECTION_NAMES = {
    R.string.XgramXDrivePhotos, R.string.XgramXDriveFiles, R.string.XgramXDriveLinks, R.string.XgramXDriveMusic, R.string.XgramXDriveVoice, R.string.XgramXDriveGifs,
  };
  private static final int[] SECTION_ICONS = {
    R.drawable.baseline_image_24, R.drawable.baseline_insert_drive_file_24, R.drawable.baseline_link_24, R.drawable.baseline_music_note_24, R.drawable.baseline_mic_24, R.drawable.deproko_baseline_gif_24,
  };

  private SettingsAdapter adapter;
  private XDriveIndex index;
  /** Each section's count (filter constructor → count; missing: still loading). */
  private final SparseIntArray counts = new SparseIntArray();

  public XDriveController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramXDrive;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramXDrive);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    index = XDriveIndex.of(tdlib);
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_xgramXDriveChat) {
          view.setData(R.string.XgramXDriveChatInfo);
        } else if (id == R.id.btn_xgramXDriveSection) {
          int filter = (int) item.getLongId();
          int count = counts.get(filter, -1);
          view.setData(count >= 0 ? Lang.plural(R.string.xMessages, count) : Lang.getString(R.string.XgramXDriveLoading));
        } else if (id == R.id.btn_xgramXDriveNotes) {
          view.setData(R.string.XgramXDriveNotesInfo);
        } else if (id == R.id.btn_xgramXDriveFromChats) {
          view.setData(R.string.XgramXDriveFromChatsInfo);
        } else if (id == R.id.btn_xgramXDriveFolder && item.getData() instanceof XDriveIndex.Folder) {
          view.setData(Lang.plural(R.string.xMessages, index.count(((XDriveIndex.Folder) item.getData()).id)));
        }
      }
    };
    adapter.setOnLongClickListener(this);
    index.addListener(this);
    XgramSavedTopics.addListener(this);
    rebuild();
    recyclerView.setAdapter(adapter);
    index.load(this::rebuild);
    loadCounts();
  }

  @Override
  public void destroy () {
    super.destroy();
    if (index != null) {
      index.removeListener(this);
    }
    XgramSavedTopics.removeListener(this);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    loadCounts(); // something may have been saved meanwhile
  }

  private void loadCounts () {
    final long chatId = tdlib.selfChatId();
    if (chatId == 0) {
      return;
    }
    for (int filter : SECTION_FILTERS) {
      tdlib.send(new TdApi.GetChatMessageCount(chatId, null, filterOf(filter), false), (count, error) -> runOnUiThreadOptional(() -> {
        if (count != null && counts.get(filter, -1) != count.count) {
          counts.put(filter, count.count);
          int position = adapter.indexOfViewByLongId(filter);
          if (position != -1) {
            adapter.updateValuedSettingByPosition(position);
          }
        }
      }));
    }
  }

  static TdApi.SearchMessagesFilter filterOf (int constructor) {
    switch (constructor) {
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterDocument();
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterUrl();
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterAudio();
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterVoiceNote();
      case TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR:
        return new TdApi.SearchMessagesFilterAnimation();
      case TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR:
      default:
        return new TdApi.SearchMessagesFilterPhotoAndVideo();
    }
  }

  @Override
  public void onIndexChanged () {
    if (!isDestroyed()) {
      rebuild();
    }
  }

  @Override
  public void onSavedTopicsChanged (Tdlib tdlib) {
    // "From chats" says nothing that changes with the topics: nothing to redraw
  }

  private void rebuild () {
    if (adapter == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramXDriveChat, R.drawable.baseline_chat_bubble_24, R.string.XgramXDriveChat));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramXDriveSections));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < SECTION_FILTERS.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramXDriveSection, SECTION_ICONS[i], SECTION_NAMES[i]).setLongId(SECTION_FILTERS[i]));
    }
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramXDriveNotes, R.drawable.baseline_format_text_24, R.string.XgramXDriveNotes));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramXDriveFromChats, R.drawable.baseline_forum_24, R.string.XgramXDriveFromChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramXDriveFolders));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    List<XDriveIndex.Folder> folders = index != null && index.isLoaded() ? index.folders() : new ArrayList<>();
    for (XDriveIndex.Folder folder : folders) {
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramXDriveFolder, R.drawable.baseline_folder_24, folder.name, false).setData(folder));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramXDriveNewFolder, R.drawable.baseline_create_new_folder_24, R.string.XgramXDriveNewFolder));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, folders.isEmpty() ? R.string.XgramXDriveNoFolders : R.string.XgramXDriveInfo));
    adapter.setItems(items, false);
  }

  // Taps

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_xgramXDriveChat) {
      openChat(this, 0);
    } else if (id == R.id.btn_xgramXDriveSection) {
      Object tag = v.getTag();
      if (tag instanceof ListItem) {
        openSection(this, (int) ((ListItem) tag).getLongId());
      }
    } else if (id == R.id.btn_xgramXDriveNotes) {
      XDriveListController c = new XDriveListController(context(), tdlib);
      c.setArguments(new XDriveListController.Args(XDriveListController.MODE_NOTES, null));
      navigateTo(c);
    } else if (id == R.id.btn_xgramXDriveFromChats) {
      navigateTo(new com.fahadali.xgram.chats.XgramSavedTopicsController(context(), tdlib));
    } else if (id == R.id.btn_xgramXDriveFolder) {
      Object tag = v.getTag();
      Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
      if (data instanceof XDriveIndex.Folder) {
        XDriveListController c = new XDriveListController(context(), tdlib);
        c.setArguments(new XDriveListController.Args(XDriveListController.MODE_FOLDER, ((XDriveIndex.Folder) data).id));
        navigateTo(c);
      }
    } else if (id == R.id.btn_xgramXDriveNewFolder) {
      newFolder(this, null);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (!(data instanceof XDriveIndex.Folder)) {
      return false;
    }
    final XDriveIndex.Folder folder = (XDriveIndex.Folder) data;
    showOptions(folder.name, new int[] {R.id.btn_xgramXDriveRename, R.id.btn_xgramXDriveDelete},
      new String[] {Lang.getString(R.string.XgramXDriveRename), Lang.getString(R.string.XgramXDriveDeleteFolder)},
      new int[] {OptionColor.NORMAL, OptionColor.RED},
      new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_delete_24}, (itemView, optionId) -> {
        if (optionId == R.id.btn_xgramXDriveRename) {
          openInputAlert(Lang.getString(R.string.XgramXDriveRename), Lang.getString(R.string.XgramXDriveFolderName), R.string.Save, R.string.Cancel, folder.name, (inputView, result) -> {
            String name = result.trim();
            if (name.isEmpty()) {
              return false;
            }
            XDriveIndex.Folder existing = index.findFolderByName(name);
            if (existing != null && existing != folder) {
              UI.showToast(Lang.getString(R.string.XgramXDriveFolderExists, name), android.widget.Toast.LENGTH_SHORT);
              return false;
            }
            index.renameFolder(folder.id, name);
            return true;
          }, true);
        } else if (optionId == R.id.btn_xgramXDriveDelete) {
          showOptions(Lang.getString(R.string.XgramXDriveDeleteFolderConfirm, folder.name), new int[] {R.id.btn_xgramXDriveDelete, R.id.btn_cancel},
            new String[] {Lang.getString(R.string.XgramXDriveDeleteFolder), Lang.getString(R.string.Cancel)},
            new int[] {OptionColor.RED, OptionColor.NORMAL},
            new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (confirmView, confirmId) -> {
              if (confirmId == R.id.btn_xgramXDriveDelete) {
                index.deleteFolder(folder.id);
              }
              return true;
            });
        }
        return true;
      });
    return true;
  }

  // Shared with the list pages and the message menu

  /** The real Saved Messages chat (past XDrive's routing), at a message when {@code messageId} isn't 0. */
  static void openChat (org.thunderdog.challegram.navigation.ViewController<?> context, long messageId) {
    Tdlib tdlib = context.tdlib();
    long chatId = tdlib.selfChatId();
    TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters().keepStack().xgramSkipDriveRouting();
    if (messageId != 0) {
      params.highlightMessage(new tgx.td.MessageId(chatId, messageId)).ensureHighlightAvailable();
    }
    tdlib.ui().openChat(context, chatId, params);
  }

  /** Saved Messages on one of its media pages (Telegram X's own: grid, viewer, selection). */
  static void openSection (org.thunderdog.challegram.navigation.ViewController<?> context, int filterConstructor) {
    Tdlib tdlib = context.tdlib();
    tdlib.privateChat(tdlib.myUserId(), chat -> {
      if (chat == null || context.isDestroyed()) {
        return;
      }
      MessagesController c = new MessagesController(context.context(), tdlib);
      c.setArguments(new MessagesController.Arguments(tdlib, null, chat, null, null, null));
      c.getValue();
      c.xgramSelectMediaTab(filterConstructor);
      context.navigateTo(c);
    });
  }

  /** Asks for a folder name and makes the folder; {@code after} gets it (null: cancelled). */
  static void newFolder (org.thunderdog.challegram.navigation.ViewController<?> context, @Nullable me.vkryl.core.lambda.RunnableData<XDriveIndex.Folder> after) {
    XDriveIndex index = XDriveIndex.of(context.tdlib());
    context.openInputAlert(Lang.getString(R.string.XgramXDriveNewFolder), Lang.getString(R.string.XgramXDriveFolderName), R.string.XgramXDriveCreate, R.string.Cancel, null, (inputView, result) -> {
      String name = result.trim();
      if (name.isEmpty()) {
        return false;
      }
      if (index.findFolderByName(name) != null) {
        UI.showToast(Lang.getString(R.string.XgramXDriveFolderExists, name), android.widget.Toast.LENGTH_SHORT);
        return false;
      }
      XDriveIndex.Folder folder = index.createFolder(name);
      if (after != null) {
        after.runWithData(folder);
      }
      return true;
    }, true);
  }

  /**
   * Add to folder (Saved Messages' message menu): the folders, a new one, or out of the folder the messages are in.
   */
  public static void pickFolder (org.thunderdog.challegram.navigation.ViewController<?> context, long[] messageIds) {
    final XDriveIndex index = XDriveIndex.of(context.tdlib());
    index.load(() -> {
      if (context.isDestroyed()) {
        return;
      }
      final List<XDriveIndex.Folder> folders = index.folders();
      final String current = messageIds.length > 0 ? index.folderOf(messageIds[0]) : null;
      final int count = folders.size() + 1 + (current != null ? 1 : 0);
      int[] ids = new int[count];
      String[] titles = new String[count];
      int[] colors = new int[count];
      int[] icons = new int[count];
      final SparseArray<XDriveIndex.Folder> byId = new SparseArray<>();
      int i = 0;
      for (XDriveIndex.Folder folder : folders) {
        int optionId = 1000 + i;
        ids[i] = optionId;
        titles[i] = folder.name;
        colors[i] = OptionColor.NORMAL;
        icons[i] = folder.id.equals(current) ? R.drawable.baseline_check_24 : R.drawable.baseline_folder_24;
        byId.put(optionId, folder);
        i++;
      }
      ids[i] = R.id.btn_xgramXDriveNewFolder;
      titles[i] = Lang.getString(R.string.XgramXDriveNewFolder);
      colors[i] = OptionColor.NORMAL;
      icons[i] = R.drawable.baseline_create_new_folder_24;
      i++;
      if (current != null) {
        ids[i] = R.id.btn_xgramXDriveRemove;
        titles[i] = Lang.getString(R.string.XgramXDriveRemoveFromFolder);
        colors[i] = OptionColor.RED;
        icons[i] = R.drawable.baseline_delete_24;
      }
      context.showOptions(Lang.getString(R.string.XgramXDriveChooseFolder), ids, titles, colors, icons, (itemView, optionId) -> {
        if (optionId == R.id.btn_xgramXDriveNewFolder) {
          newFolder(context, folder -> {
            index.setFolder(messageIds, folder.id);
            UI.showToast(Lang.getString(R.string.XgramXDriveAddedTo, folder.name), android.widget.Toast.LENGTH_SHORT);
          });
        } else if (optionId == R.id.btn_xgramXDriveRemove) {
          index.setFolder(messageIds, null);
        } else {
          XDriveIndex.Folder folder = byId.get(optionId);
          if (folder != null) {
            index.setFolder(messageIds, folder.id);
            UI.showToast(Lang.getString(R.string.XgramXDriveAddedTo, folder.name), android.widget.Toast.LENGTH_SHORT);
          }
        }
        return true;
      });
    });
  }
}
