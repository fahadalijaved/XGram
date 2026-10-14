package com.fahadali.xgram.chats;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.ForceTouchView;

import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;

/**
 * XGram: what a merged community row offers on long press (preview action bar, quick actions pill, options sheet):
 * mark all its chats as read, mute or unmute them all, and add them all to a folder.
 */
public final class XgramCommunityActions {
  private XgramCommunityActions () { }

  /** The community's chats this account knows and can use. */
  public static long[] chats (Tdlib tdlib, long communityId) {
    LongList result = new LongList(10);
    for (Long chatId : XgramCommunityData.chatIds(tdlib, communityId, tdlib.cache().communityFull(communityId))) {
      TdApi.Chat chat = chatId != null ? tdlib.chat(chatId) : null;
      if (chat != null && tdlib.chatAvailable(chat)) {
        result.append(chat.id);
      }
    }
    return result.get();
  }

  private static boolean anyUnread (Tdlib tdlib, long[] chatIds) {
    for (long chatId : chatIds) {
      if (tdlib.canMarkAsRead(tdlib.chat(chatId))) {
        return true;
      }
    }
    return false;
  }

  private static boolean allMuted (Tdlib tdlib, long[] chatIds) {
    for (long chatId : chatIds) {
      if (tdlib.chatNotificationsEnabled(chatId)) {
        return false;
      }
    }
    return chatIds.length > 0;
  }

  /** Fills the actions; null when the community has no usable chats. */
  public static ForceTouchView.ActionListener create (ViewController<?> context, Tdlib tdlib, long communityId, IntList ids, IntList icons, StringList strings) {
    long[] chatIds = chats(tdlib, communityId);
    if (chatIds.length == 0) {
      return null;
    }
    fill(tdlib, chatIds, ids, icons, strings);
    if (ids.size() == 0) {
      return null;
    }
    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext forceTouchContext, int actionId, Object arg) { }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext forceTouchContext, int actionId, Object arg) {
        perform(context, tdlib, communityId, actionId);
      }
    };
  }

  public static void fill (Tdlib tdlib, long[] chatIds, IntList ids, IntList icons, StringList strings) {
    if (anyUnread(tdlib, chatIds)) {
      ids.append(R.id.btn_markChatAsRead);
      strings.append(R.string.XgramCommunityReadAll);
      icons.append(Config.ICON_MARK_AS_READ);
    }
    boolean muted = allMuted(tdlib, chatIds);
    ids.append(R.id.btn_notifications);
    strings.append(muted ? R.string.XgramCommunityUnmuteAll : R.string.XgramCommunityMuteAll);
    icons.append(muted ? R.drawable.baseline_notifications_24 : R.drawable.baseline_notifications_off_24);
    if (XgramFolderPicker.canOffer(tdlib, chatIds)) {
      ids.append(R.id.btn_addChatToFolder);
      strings.append(R.string.AddToFolder);
      icons.append(R.drawable.templarian_baseline_folder_plus_24);
    }
  }

  /** Long press with the preview off: the same actions as an options sheet. */
  public static void showOptions (ViewController<?> context, Tdlib tdlib, long communityId, CharSequence title) {
    long[] chatIds = chats(tdlib, communityId);
    if (chatIds.length == 0) {
      return;
    }
    IntList ids = new IntList(3);
    IntList icons = new IntList(3);
    StringList strings = new StringList(3);
    fill(tdlib, chatIds, ids, icons, strings);
    int[] colors = new int[ids.size()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = ViewController.OptionColor.NORMAL;
    }
    context.showOptions(title, ids.get(), strings.get(), colors, icons.get(), (itemView, id) -> {
      perform(context, tdlib, communityId, id);
      return true;
    });
  }

  public static boolean perform (ViewController<?> context, Tdlib tdlib, long communityId, int actionId) {
    long[] chatIds = chats(tdlib, communityId);
    if (actionId == R.id.btn_markChatAsRead) {
      for (long chatId : chatIds) {
        if (tdlib.canMarkAsRead(tdlib.chat(chatId))) {
          tdlib.markChatAsRead(chatId, new TdApi.MessageSourceChatList(), true, null);
        }
      }
      return true;
    } else if (actionId == R.id.btn_notifications) {
      int muteFor = allMuted(tdlib, chatIds) ? 0 : TdlibUi.MUTE_FOREVER;
      for (long chatId : chatIds) {
        if (tdlib.chat(chatId) != null && tdlib.scopeNotificationSettings(chatId) != null && tdlib.chatNotificationsEnabled(chatId) == (muteFor != 0)) {
          tdlib.setMuteFor(chatId, muteFor);
        }
      }
      return true;
    } else if (actionId == R.id.btn_addChatToFolder) {
      if (!context.isDestroyed()) {
        XgramFolderPicker.show(context, chatIds);
      }
      return true;
    }
    return false;
  }
}
