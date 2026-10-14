package com.fahadali.xgram.chats;

import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.EditChatFolderController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import tgx.td.ChatPosition;

/**
 * XGram: "Add to folder" for one chat. A sheet with the user's chat folders, each checked when the chat is in it;
 * tapping a folder adds the chat to it or takes it out. "New folder" opens Telegram X's folder editor with the chat in it.
 */
public final class XgramFolderPicker {
  private XgramFolderPicker () { }

  /** Telegram X's rules: folders are on, the chat is in the main list or the archive, and a folder exists or can be made. */
  public static boolean canOffer (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (tdlib == null || chat == null || !Settings.instance().chatFoldersEnabled() || !tdlib.chatAvailable(chat)) {
      return false;
    }
    if (ChatPosition.findPosition(chat, ChatPosition.CHAT_LIST_MAIN) == null && ChatPosition.findPosition(chat, ChatPosition.CHAT_LIST_ARCHIVE) == null) {
      return false;
    }
    return tdlib.chatFolderCount() > 0 || tdlib.canCreateChatFolder();
  }

  public static boolean canOffer (Tdlib tdlib, long chatId) {
    return tdlib != null && canOffer(tdlib, tdlib.chat(chatId));
  }

  public static void show (ViewController<?> context, long chatId) {
    Tdlib tdlib = context.tdlib();
    TdApi.ChatFolderInfo[] infos = tdlib.chatFolders();
    TdApi.ChatFolder[] folders = new TdApi.ChatFolder[infos.length];
    if (infos.length == 0) {
      UI.post(() -> open(context, chatId, infos, folders));
      return;
    }
    int[] pending = {infos.length};
    for (int i = 0; i < infos.length; i++) {
      final int index = i;
      tdlib.send(new TdApi.GetChatFolder(infos[i].id), (folder, error) -> UI.post(() -> {
        folders[index] = folder;
        if (--pending[0] == 0 && !context.isDestroyed()) {
          open(context, chatId, infos, folders);
        }
      }));
    }
  }

  private static void open (ViewController<?> context, long chatId, TdApi.ChatFolderInfo[] infos, TdApi.ChatFolder[] folders) {
    Tdlib tdlib = context.tdlib();
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return;
    }
    List<ListItem> items = new ArrayList<>(infos.length + 1);
    for (int i = 0; i < infos.length; i++) {
      TdApi.ChatFolderInfo info = infos[i];
      boolean inFolder = folders[i] != null && isInFolder(tdlib, chat, folders[i]);
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.chatFolder, TD.findFolderIcon(info.icon, R.drawable.baseline_folder_24), TD.toCharSequence(info.name), inFolder).setIntValue(info.id));
    }
    if (tdlib.canCreateChatFolder()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createNewFolder, R.drawable.baseline_create_new_folder_24, R.string.CreateNewFolder).setTextColorId(ColorId.textNeutral));
    }
    if (items.isEmpty()) {
      return;
    }
    LongSet busy = new LongSet();
    SettingsWrap[] settings = new SettingsWrap[1];
    settings[0] = context.showSettings(new SettingsWrapBuilder(R.id.btn_addChatToFolder)
      .addHeaderItem(Lang.getString(R.string.AddToFolder))
      .setRawItems(items)
      .setNeedSeparators(false)
      .setDisableFooter(true)
      .setDisableToggles(true)
      .setNeedRootInsets(true)
      .setSettingProcessor((item, view, isUpdate) -> {
        view.setIconColorId(item.getId() == R.id.btn_createNewFolder ? ColorId.inlineIcon : ColorId.NONE);
      })
      .setOnSettingItemClick((view, id, item, done, adapter, window) -> {
        if (item.getId() == R.id.btn_createNewFolder) {
          if (settings[0] != null) {
            settings[0].window.hideWindow(true);
          }
          context.context().navigation().navigateTo(EditChatFolderController.newFolder(context.context(), tdlib, TD.newChatFolder(new long[] {chatId})));
          return;
        }
        int folderId = item.getIntValue();
        long busyKey = folderId;
        if (busy.has(busyKey)) {
          return;
        }
        busy.add(busyKey);
        boolean add = !item.isSelected();
        toggle(tdlib, chatId, folderId, add, ok -> {
          busy.remove(busyKey);
          if (ok) {
            item.setSelected(add);
            int position = adapter.indexOfView(item);
            if (position != -1) {
              adapter.updateValuedSettingByPosition(position);
            }
          }
        });
      }));
  }

  private interface Done {
    void onDone (boolean ok);
  }

  private static void toggle (Tdlib tdlib, long chatId, int folderId, boolean add, Done done) {
    if (add) {
      tdlib.send(new TdApi.AddChatToList(chatId, new TdApi.ChatListFolder(folderId)), (ok, error) -> UI.post(() -> {
        if (error != null) {
          showError(tdlib, error);
        }
        done.onDone(error == null);
      }));
      return;
    }
    tdlib.send(new TdApi.GetChatFolder(folderId), (folder, error) -> {
      if (error != null) {
        UI.post(() -> {
          showError(tdlib, error);
          done.onDone(false);
        });
        return;
      }
      LongList pinned = new LongList(folder.pinnedChatIds);
      LongSet included = new LongSet(folder.includedChatIds);
      LongSet excluded = new LongSet(folder.excludedChatIds);
      pinned.remove(chatId);
      included.remove(chatId);
      folder.pinnedChatIds = pinned.get();
      folder.includedChatIds = included.toArray();
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null && matchesFlags(tdlib, chat, folder)) {
        // Still in the folder through a chat type ("All groups" and so on): exclude it.
        excluded.add(chatId);
        folder.excludedChatIds = excluded.toArray();
      }
      tdlib.send(new TdApi.EditChatFolder(folderId, folder), (info, editError) -> UI.post(() -> {
        if (editError != null) {
          showError(tdlib, editError);
        }
        done.onDone(editError == null);
      }));
    });
  }

  private static boolean contains (long[] ids, long id) {
    if (ids != null) {
      for (long value : ids) {
        if (value == id) return true;
      }
    }
    return false;
  }

  static boolean isInFolder (Tdlib tdlib, TdApi.Chat chat, TdApi.ChatFolder folder) {
    if (contains(folder.pinnedChatIds, chat.id) || contains(folder.includedChatIds, chat.id)) {
      return true;
    }
    if (contains(folder.excludedChatIds, chat.id)) {
      return false;
    }
    return matchesFlags(tdlib, chat, folder);
  }

  /** Whether the folder takes the chat in by its type and filters alone, without listing it. */
  private static boolean matchesFlags (Tdlib tdlib, TdApi.Chat chat, TdApi.ChatFolder folder) {
    boolean isBot = tdlib.isBotChat(chat);
    boolean isUser = tdlib.isUserChat(chat) && !isBot;
    boolean byType;
    if (isBot) {
      byType = folder.includeBots;
    } else if (isUser) {
      byType = tdlib.isContactChat(chat) ? folder.includeContacts : folder.includeNonContacts;
    } else if (tdlib.isChannelChat(chat)) {
      byType = folder.includeChannels;
    } else {
      byType = TD.isMultiChat(chat) && folder.includeGroups;
    }
    if (!byType) {
      return false;
    }
    if (folder.excludeMuted && !tdlib.chatNotificationsEnabled(chat.id)) {
      return false;
    }
    if (folder.excludeRead && chat.unreadCount == 0 && !chat.isMarkedAsUnread) {
      return false;
    }
    return !(folder.excludeArchived && tdlib.chatArchived(chat));
  }

  private static void showError (Tdlib tdlib, TdApi.Error error) {
    String message = error.message != null ? error.message : "";
    String upper = message.toUpperCase(java.util.Locale.ROOT);
    CharSequence text;
    if (upper.contains("TOO_MUCH") || upper.contains("LIMIT") || upper.contains("MAXIMUM") || upper.contains("TOO MANY")) {
      int max = tdlib.chatFolderChosenChatCountMax();
      text = Lang.getString(tdlib.hasPremium() ? R.string.XgramFolderFull : R.string.XgramFolderFullPremium, max);
    } else if (upper.contains("INVALID") || upper.contains("CAN'T") || upper.contains("CANNOT")) {
      text = Lang.getString(R.string.XgramFolderChatNotAllowed);
    } else {
      text = Lang.getString(R.string.XgramFolderError, TD.toErrorString(error));
    }
    UI.showToast(text, Toast.LENGTH_LONG);
  }
}
