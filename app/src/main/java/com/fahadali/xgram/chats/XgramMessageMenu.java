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

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.core.collection.IntList;

/**
 * The message menu (long press on a message), trimmed to what the user wants, like exteraGram and NagramX allow:
 * Settings → XGram → Chats → Message menu. Options shown only in their own situation (send now, stop poll, …) stay.
 */
public final class XgramMessageMenu {
  private XgramMessageMenu () { }

  private static final String KEY_HIDDEN = "xgram_menu_hidden";

  public static final class Option {
    /** Stored when hidden: stays the same across builds, unlike the ids. */
    public final String key;
    public final int[] ids;
    @StringRes public final int name;
    @DrawableRes public final int icon;
    public final boolean isXgram;

    Option (String key, int name, int icon, boolean isXgram, @IdRes int... ids) {
      this.key = key;
      this.name = name;
      this.icon = icon;
      this.isXgram = isXgram;
      this.ids = ids;
    }
  }

  public static final Option[] OPTIONS = {
    // In the order Telegram X (and XGram, in between) usually shows them: the default order of the list in settings.
    new Option("thread", R.string.ViewThread, R.drawable.outline_forum_24, false, R.id.btn_messageReplies),
    new Option("reply", R.string.Reply, R.drawable.baseline_reply_24, false, R.id.btn_messageReply),
    new Option("quote", R.string.XgramQuote, R.drawable.baseline_format_quote_close_24, true, R.id.btn_xgramQuote),
    new Option("share", R.string.Share, R.drawable.baseline_forward_24, false, R.id.btn_messageShare),
    new Option("pin", R.string.Pin, R.drawable.deproko_baseline_pin_24, false, R.id.btn_messagePin, R.id.btn_messageUnpin),
    new Option("stats", R.string.ViewStats, R.drawable.baseline_bar_chart_24, false, R.id.btn_viewStatistics),
    new Option("edit", R.string.edit, R.drawable.baseline_edit_24, false, R.id.btn_messageEdit),
    new Option("copy_link", R.string.CopyLink, R.drawable.baseline_link_24, false, R.id.btn_messageCopyLink),
    new Option("read_up_to_here", R.string.XgramReadUpToHere, R.drawable.baseline_done_all_24, true, R.id.btn_xgramReadUpToHere),
    new Option("details", R.string.XgramMessageDetails, R.drawable.baseline_info_24, true, R.id.btn_xgramMessageDetails),
    new Option("save_to_saved", R.string.XgramSaveToSaved, R.drawable.baseline_bookmark_24, true, R.id.btn_xgramSaveToSaved),
    new Option("view_in_topic", R.string.XgramViewInTopic, R.drawable.baseline_forum_24, true, R.id.btn_xgramViewInTopic),
    new Option("bookmark", R.string.XgramBookmark, (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? R.drawable.baseline_star_border_24 : R.drawable.baseline_star_24), true, R.id.btn_xgramBookmark),
    new Option("repeat", R.string.XgramRepeat, R.drawable.baseline_repeat_24, true, R.id.btn_xgramRepeat),
    new Option("transcribe", R.string.XgramTranscribe, R.drawable.baseline_format_text_24, true, R.id.btn_xgramTranscribe),
    new Option("summarize", R.string.XgramSummarize, R.drawable.baseline_format_list_bulleted_type_24, true, R.id.btn_xgramSummarize),
    new Option("edit_history", R.string.XgramEditHistory, R.drawable.baseline_history_24, true, R.id.btn_xgramEditHistory),
    new Option("by_sender", R.string.XgramMessagesBySender, R.drawable.baseline_person_24, true, R.id.btn_xgramMessagesBySender),
    new Option("reply_privately", R.string.XgramReplyPrivately, R.drawable.baseline_reply_24, true, R.id.btn_xgramReplyPrivately),
    new Option("shadow_ban", R.string.XgramShadowBan, R.drawable.baseline_block_24, true, R.id.btn_xgramShadowBan),
    new Option("copy_photo", R.string.XgramCopyPhoto, R.drawable.baseline_content_copy_24, true, R.id.btn_xgramCopyPhoto),
    new Option("copy", R.string.Copy, R.drawable.baseline_content_copy_24, false, R.id.btn_messageCopy, R.id.btn_copyTranslation),
    new Option("translate", R.string.Translate, R.drawable.baseline_translate_24, false, R.id.btn_chatTranslate, R.id.btn_chatTranslateOff),
    new Option("sticker_set", R.string.OpenStickerSet, R.drawable.deproko_baseline_stickers_24, false, R.id.btn_messageStickerSet),
    new Option("save", R.string.SaveToDownloads, R.drawable.baseline_file_download_24, false, R.id.btn_saveFile, R.id.btn_saveGif),
    new Option("cache", R.string.DeleteFromCache, R.drawable.templarian_baseline_broom_24, false, R.id.btn_deleteFile, R.id.btn_xgramClearFromCache),
    new Option("report", R.string.MessageReport, R.drawable.baseline_report_24, false, R.id.btn_messageReport),
    new Option("delete", R.string.Delete, R.drawable.baseline_delete_24, false, R.id.btn_messageDelete),
    new Option("restrict", R.string.XgramMenuRestrict, R.drawable.baseline_block_24, false, R.id.btn_messageRestrictMember, R.id.btn_messageBlockUser),
    new Option("more", R.string.MoreMessageOptions, R.drawable.baseline_more_horiz_24, false, R.id.btn_messageMore)
  };

  private static volatile Set<String> hidden;

  private static Set<String> hidden () {
    if (hidden == null) {
      Set<String> set = new HashSet<>();
      String value = Settings.instance().getString(KEY_HIDDEN, "");
      for (String key : value.split(",")) {
        if (!key.isEmpty()) {
          set.add(key);
        }
      }
      hidden = set;
    }
    return hidden;
  }

  public static boolean isHidden (String key) {
    return hidden().contains(key);
  }

  public static void setHidden (String key, boolean isHidden) {
    Set<String> set = new HashSet<>(hidden());
    if (isHidden) {
      set.add(key);
    } else {
      set.remove(key);
    }
    hidden = set;
    StringBuilder b = new StringBuilder();
    for (String k : set) {
      if (b.length() > 0) {
        b.append(',');
      }
      b.append(k);
    }
    if (b.length() == 0) {
      Settings.instance().remove(KEY_HIDDEN);
    } else {
      Settings.instance().putString(KEY_HIDDEN, b.toString());
    }
  }

  /** After a backup import or reset. */
  public static void reload () {
    hidden = null;
  }

  private static boolean isHiddenId (int id, Set<String> set) {
    if (set.isEmpty()) {
      return false;
    }
    for (Option option : OPTIONS) {
      for (int optionId : option.ids) {
        if (optionId == id) {
          return set.contains(option.key);
        }
      }
    }
    return false;
  }

  /** Drops the hidden options, and XGram's "Clear from cache" when Telegram X's own is there too. */
  public static void filter (IntList ids, IntList icons, StringList strings) {
    String[] names = strings.get();
    int count = ids.size();
    if (icons.size() != count || names.length != count) {
      return; // not one icon and name per option: leave it as it is
    }
    Set<String> set = hidden();
    boolean hasTgxCache = ids.indexOf(R.id.btn_deleteFile) != -1;
    int[] idArray = ids.get(), iconArray = icons.get();
    IntList keptIds = new IntList(count), keptIcons = new IntList(count);
    StringList keptNames = new StringList(count);
    for (int i = 0; i < count; i++) {
      int id = idArray[i];
      if (isHiddenId(id, set) || (hasTgxCache && id == R.id.btn_xgramClearFromCache)) {
        continue;
      }
      keptIds.append(id);
      keptIcons.append(iconArray[i]);
      keptNames.append(names[i]);
    }
    if (keptIds.isEmpty() || keptIds.size() == count) {
      order(ids, icons, strings); // never leave the menu empty
      return;
    }
    String[] keptNameArray = keptNames.get();
    ids.clear();
    icons.clear();
    strings.clear();
    for (int i = 0; i < keptIds.size(); i++) {
      ids.append(keptIds.get(i));
      icons.append(keptIcons.get(i));
      strings.append(keptNameArray[i]);
    }
    order(ids, icons, strings);
  }

  private static String[] defaultKeys;

  /**
   * The user's order (Settings → XGram → Chats → Message menu). Only the options the user moved move: not every menu
   * follows the default order (a deleted message's, XGram's Clear from cache before Copy), and those keep their own.
   */
  private static void order (IntList ids, IntList icons, StringList strings) {
    if (defaultKeys == null) {
      String[] keys = new String[OPTIONS.length];
      for (int i = 0; i < keys.length; i++) {
        keys[i] = OPTIONS[i].key;
      }
      defaultKeys = keys;
    }
    com.fahadali.xgram.XgramReorder.sort(com.fahadali.xgram.XgramReorder.MESSAGE_MENU, ids, icons, strings, XgramMessageMenu::keyOfId, defaultKeys);
  }

  // Material 3 Expressive menu groups (segmented tiles): consecutive options of one group share a tile group

  public static final int GROUP_PRIMARY = 0, GROUP_SECONDARY = 1, GROUP_MODERATION = 2, GROUP_DELETE = 3;

  /** The tile group of an option: actions on the message, copy / info / tools, moderation, and Delete on its own. */
  public static int menuGroup (@IdRes int id) {
    if (id == R.id.btn_messageReplies || id == R.id.btn_messageReply || id == R.id.btn_xgramQuote ||
      id == R.id.btn_messageShare || id == R.id.btn_messagePin || id == R.id.btn_messageUnpin ||
      id == R.id.btn_messageEdit || id == R.id.btn_xgramReplyPrivately) {
      return GROUP_PRIMARY;
    }
    if (id == R.id.btn_messageDelete) {
      return GROUP_DELETE;
    }
    if (id == R.id.btn_messageReport || id == R.id.btn_messageRestrictMember || id == R.id.btn_messageBlockUser ||
      id == R.id.btn_xgramShadowBan) {
      return GROUP_MODERATION;
    }
    return GROUP_SECONDARY;
  }

  /** Whether two neighbouring options share a tile group (Delete never does). */
  public static boolean sameMenuGroup (@IdRes int a, @IdRes int b) {
    int group = menuGroup(a);
    return group != GROUP_DELETE && group == menuGroup(b);
  }

  @androidx.annotation.Nullable
  private static String keyOfId (Integer id) {
    for (Option option : OPTIONS) {
      for (int optionId : option.ids) {
        if (optionId == id) {
          return option.key;
        }
      }
    }
    return null;
  }
}
