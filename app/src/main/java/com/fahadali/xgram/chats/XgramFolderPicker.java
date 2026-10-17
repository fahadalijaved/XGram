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

  /** Several chats at once (a merged community): offered when any of them can go in a folder. */
  public static boolean canOffer (Tdlib tdlib, long[] chatIds) {
    return tdlib != null && offerable(tdlib, chatIds).length > 0;
  }

  private static long[] offerable (Tdlib tdlib, long[] chatIds) {
    LongList result = new LongList(chatIds != null ? chatIds.length : 0);
    if (chatIds != null) {
      for (long chatId : chatIds) {
        if (canOffer(tdlib, tdlib.chat(chatId)) && !result.contains(chatId)) {
          result.append(chatId);
        }
      }
    }
    return result.get();
  }

  public static void show (ViewController<?> context, long chatId) {
    show(context, new long[] {chatId});
  }

  /** A folder is checked when all the chats are in it; tapping adds the missing ones or takes them all out. */
  public static void show (ViewController<?> context, long[] chatIds) {
    Tdlib tdlib = context.tdlib();
    long[] ids = offerable(tdlib, chatIds);
    if (ids.length == 0) {
      return;
    }
    TdApi.ChatFolderInfo[] infos = tdlib.chatFolders();
    TdApi.ChatFolder[] folders = new TdApi.ChatFolder[infos.length];
    if (infos.length == 0) {
      UI.post(() -> open(context, ids, infos, folders));
      return;
    }
    int[] pending = {infos.length};
    for (int i = 0; i < infos.length; i++) {
      final int index = i;
      tdlib.send(new TdApi.GetChatFolder(infos[i].id), (folder, error) -> UI.post(() -> {
        folders[index] = folder;
        if (--pending[0] == 0 && !context.isDestroyed()) {
          open(context, ids, infos, folders);
        }
      }));
    }
  }

  private static boolean allInFolder (Tdlib tdlib, long[] chatIds, TdApi.ChatFolder folder) {
    if (folder == null) {
      return false;
    }
    int count = 0;
    for (long chatId : chatIds) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        continue;
      }
      if (!isInFolder(tdlib, chat, folder)) {
        return false;
      }
      count++;
    }
    return count > 0;
  }

  private static void open (ViewController<?> context, long[] chatIds, TdApi.ChatFolderInfo[] infos, TdApi.ChatFolder[] folders) {
    Tdlib tdlib = context.tdlib();
    if (chatIds.length == 0) {
      return;
    }
    List<ListItem> items = new ArrayList<>(infos.length + 1);
    boolean allHidden = com.fahadali.xgram.XgramSettings.instance().hideFolders(tdlib.accountId());
    // XGram M3E r6: a hidden folder gets a "Hidden" supporting line (not a suffix), and "New folder" is a tonal button
    // with a + icon in the sheet's actions row instead of a blue row
    final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    final boolean canCreate = tdlib.canCreateChatFolder();
    for (int i = 0; i < infos.length; i++) {
      TdApi.ChatFolderInfo info = infos[i];
      boolean inFolder = allInFolder(tdlib, chatIds, folders[i]);
      CharSequence name = TD.toCharSequence(info.name);
      final boolean hidden = allHidden || !tdlib.settings().isChatFolderEnabled(info.id);
      final int icon = TD.findFolderIcon(info.icon, R.drawable.baseline_folder_24);
      if (xgram && hidden) {
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE, R.id.chatFolder, icon, name, inFolder).setStringValue(Lang.getString(R.string.XgramR6B3FolderHidden)).setIntValue(info.id));
        continue;
      }
      if (hidden) {
        name = Lang.getCharSequence(R.string.XgramFolderHiddenMark, name);
      }
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.chatFolder, icon, name, inFolder).setIntValue(info.id));
    }
    if (canCreate && !xgram) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createNewFolder, R.drawable.baseline_create_new_folder_24, R.string.CreateNewFolder).setTextColorId(ColorId.textNeutral));
    }
    if (items.isEmpty() && !(xgram && canCreate)) {
      return;
    }
    LongSet busy = new LongSet();
    SettingsWrap[] settings = new SettingsWrap[1];
    final boolean xgramActions = xgram && canCreate;
    settings[0] = context.showSettings(new SettingsWrapBuilder(R.id.btn_addChatToFolder)
      .addHeaderItem(Lang.getString(R.string.AddToFolder))
      .setRawItems(items)
      .setNeedSeparators(false)
      .setDisableFooter(!xgramActions)
      .setSaveStr(com.fahadali.xgram.appearance.XgramR5Sheets.sentenceCase(R.string.CreateNewFolder, R.string.XgramR6B3NewFolder))
      .setCancelStr(Lang.getString(R.string.Done))
      .setOnActionButtonClick((wrap, view, isCancel) -> {
        if (isCancel) {
          return false; // Done: the sheet closes
        }
        if (wrap.window != null) {
          wrap.window.hideWindow(true);
        }
        context.context().navigation().navigateTo(EditChatFolderController.newFolder(context.context(), tdlib, TD.newChatFolder(chatIds)));
        return true;
      })
      .setDisableToggles(true)
      .setNeedRootInsets(true)
      .setSettingProcessor((item, view, isUpdate) -> {
        view.setIconColorId(item.getId() == R.id.btn_createNewFolder ? ColorId.inlineIcon : ColorId.NONE);
        if (item.getViewType() == ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE && view.findCheckBox() != null) {
          view.findCheckBox().setChecked(item.isSelected(), isUpdate); // XGram: the hidden folders' two-line rows
        }
      })
      .setOnSettingItemClick((view, id, item, done, adapter, window) -> {
        if (item.getId() == R.id.btn_createNewFolder) {
          if (settings[0] != null) {
            settings[0].window.hideWindow(true);
          }
          context.context().navigation().navigateTo(EditChatFolderController.newFolder(context.context(), tdlib, TD.newChatFolder(chatIds)));
          return;
        }
        int folderId = item.getIntValue();
        long busyKey = folderId;
        if (busy.has(busyKey)) {
          return;
        }
        busy.add(busyKey);
        boolean add = !item.isSelected();
        toggle(tdlib, chatIds, folderId, add, ok -> {
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
    if (xgramActions && settings[0] != null && settings[0].doneButton instanceof com.fahadali.xgram.appearance.XgramM3Sheets.FilledButton) {
      // XGram M3E r6: "New folder" is a tonal button (secondaryContainer) with a leading 20dp +
      com.fahadali.xgram.appearance.XgramM3Sheets.FilledButton button = (com.fahadali.xgram.appearance.XgramM3Sheets.FilledButton) settings[0].doneButton;
      button.setRoles(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER, com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER);
      android.graphics.drawable.Drawable plus = org.thunderdog.challegram.tool.Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_add_24));
      if (plus != null) {
        final int size = org.thunderdog.challegram.tool.Screen.dp(com.fahadali.xgram.appearance.XgramM3Sheets.BUTTON_ICON_SIZE);
        plus.setBounds(0, 0, size, size);
        plus.setColorFilter(org.thunderdog.challegram.tool.Paints.getColorFilter(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER)));
        button.setCompoundDrawablesRelative(plus, null, null, null);
        button.setCompoundDrawablePadding(org.thunderdog.challegram.tool.Screen.dp(com.fahadali.xgram.appearance.XgramM3Sheets.BUTTON_ICON_LABEL_SPACE));
      }
    }
  }

  private interface Done {
    void onDone (boolean ok);
  }

  private static void toggle (Tdlib tdlib, long[] chatIds, int folderId, boolean add, Done done) {
    tdlib.send(new TdApi.GetChatFolder(folderId), (folder, error) -> {
      if (error != null) {
        UI.post(() -> {
          showError(tdlib, error);
          done.onDone(false);
        });
        return;
      }
      if (add) {
        LongList missing = new LongList(chatIds.length);
        for (long chatId : chatIds) {
          TdApi.Chat chat = tdlib.chat(chatId);
          if (chat != null && !isInFolder(tdlib, chat, folder)) {
            missing.append(chatId);
          }
        }
        addAll(tdlib, missing.get(), 0, folderId, done);
        return;
      }
      LongList pinned = new LongList(folder.pinnedChatIds);
      LongSet included = new LongSet(folder.includedChatIds);
      LongSet excluded = new LongSet(folder.excludedChatIds);
      for (long chatId : chatIds) {
        pinned.remove(chatId);
        included.remove(chatId);
      }
      folder.pinnedChatIds = pinned.get();
      folder.includedChatIds = included.toArray();
      for (long chatId : chatIds) {
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat != null && matchesFlags(tdlib, chat, folder)) {
          // Still in the folder through a chat type ("All groups" and so on): exclude it.
          excluded.add(chatId);
        }
      }
      folder.excludedChatIds = excluded.toArray();
      tdlib.send(new TdApi.EditChatFolder(folderId, folder), (info, editError) -> UI.post(() -> {
        if (editError != null) {
          showError(tdlib, editError);
        }
        done.onDone(editError == null);
      }));
    });
  }

  /** One chat after another, stopping at the first error (a full folder and so on). */
  private static void addAll (Tdlib tdlib, long[] chatIds, int index, int folderId, Done done) {
    if (index >= chatIds.length) {
      UI.post(() -> done.onDone(true));
      return;
    }
    tdlib.send(new TdApi.AddChatToList(chatIds[index], new TdApi.ChatListFolder(folderId)), (ok, error) -> {
      if (error != null) {
        UI.post(() -> {
          showError(tdlib, error);
          done.onDone(false);
        });
      } else {
        addAll(tdlib, chatIds, index + 1, folderId, done);
      }
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
    if (!com.fahadali.xgram.appearance.XgramSnackbar.show(text, Toast.LENGTH_LONG)) { // XGram M3E r6: the snackbar
      UI.showToast(text, Toast.LENGTH_LONG);
    }
  }
}
