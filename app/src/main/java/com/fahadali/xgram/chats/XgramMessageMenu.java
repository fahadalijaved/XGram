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
import org.thunderdog.challegram.core.Lang;
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
  /** The options the user put under More (a comma separated list of keys, like {@link #KEY_HIDDEN}). */
  public static final String KEY_MORE = "xgram_menu_more";
  /** The key of Telegram X's own More option, which opens the options under it. */
  public static final String KEY_MORE_OPTION = "more";
  /**
   * The options Telegram X keeps on More's page ({@link #isTgxMoreOnly}) that the user wants in the menu itself (a comma
   * separated list of keys). Without it they stay where Telegram X puts them, so the default menu does not change.
   */
  public static final String KEY_IN_MENU = "xgram_menu_in_menu";

  public static final class Option {
    /** Stored when hidden: stays the same across builds, unlike the ids. */
    public final String key;
    public final int[] ids;
    @StringRes public final int name;
    @DrawableRes private final int icon, expressiveIcon;
    public final boolean isXgram;

    Option (String key, int name, int icon, boolean isXgram, @IdRes int... ids) {
      this(key, name, icon, 0, isXgram, ids);
    }

    /** {@code expressiveIcon}: another icon with Expressive design on (0: the same one). */
    Option (String key, int name, int icon, int expressiveIcon, boolean isXgram, @IdRes int... ids) {
      this.key = key;
      this.name = name;
      this.icon = icon;
      this.expressiveIcon = expressiveIcon;
      this.isXgram = isXgram;
      this.ids = ids;
    }

    /**
     * Chosen when it is shown, not when the class loads: turning Expressive design on or off only recreates the
     * activity, and this array outlives it.
     */
    @DrawableRes
    public int icon () {
      int res = expressiveIcon != 0 && com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? expressiveIcon : icon;
      return com.fahadali.xgram.xdrive.XDrive.icon(res); // XGram XDrive: Save to XDrive shows its cloud
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
    new Option("bookmark", R.string.XgramBookmark, R.drawable.baseline_star_24, R.drawable.baseline_star_border_24, true, R.id.btn_xgramBookmark),
    new Option("remind", R.string.XgramLaterRemind, R.drawable.baseline_watch_later_24, true, R.id.btn_xgramLaterRemind),
    new Option("xdrive_folder", R.string.XgramXDriveAddToFolder, R.drawable.baseline_create_new_folder_24, true, R.id.btn_xgramXDriveAddToFolder),
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

  /** Whether the option exists in this build and setup (XDrive's Add to folder: only while XDrive's test is on). */
  public static boolean isAvailable (String key) {
    return !"xdrive_folder".equals(key) || com.fahadali.xgram.xdrive.XDrive.on();
  }

  private static volatile Set<String> hidden, more, inMenu;

  private static Set<String> readSet (String settingKey) {
    Set<String> set = new HashSet<>();
    String value = Settings.instance().getString(settingKey, "");
    for (String key : value.split(",")) {
      if (!key.isEmpty()) {
        set.add(key);
      }
    }
    return set;
  }

  private static void writeSet (String settingKey, Set<String> set) {
    StringBuilder b = new StringBuilder();
    for (String k : set) {
      if (b.length() > 0) {
        b.append(',');
      }
      b.append(k);
    }
    if (b.length() == 0) {
      Settings.instance().remove(settingKey);
    } else {
      Settings.instance().putString(settingKey, b.toString());
    }
  }

  private static Set<String> hidden () {
    if (hidden == null) {
      hidden = readSet(KEY_HIDDEN);
    }
    return hidden;
  }

  private static Set<String> more () {
    if (more == null) {
      Set<String> set = readSet(KEY_MORE);
      // Hidden options have no place (setHidden): older builds' Compose page and older backups kept both, so an option
      // shown again came back under More. The menu skips hidden options anyway; the next write stores the cleaned set.
      set.removeAll(hidden());
      more = set;
    }
    return more;
  }

  private static Set<String> inMenu () {
    if (inMenu == null) {
      inMenu = readSet(KEY_IN_MENU);
    }
    return inMenu;
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
    writeSet(KEY_HIDDEN, set);
    if (isHidden && canGoInMore(key)) {
      // Hidden drops the option's place, for every caller: shown again, it comes back where it is by default, on both
      // settings pages alike, never under More from before
      setInMore(key, false);
      setInMenu(key, false);
    }
  }

  /** Whether the option waits under More (the menu's More option opens it) instead of in the menu itself. */
  public static boolean isInMore (String key) {
    return more().contains(key);
  }

  /** Every option but More itself can go under More. */
  public static boolean canGoInMore (String key) {
    return !KEY_MORE_OPTION.equals(key);
  }

  public static void setInMore (String key, boolean inMore) {
    if (!canGoInMore(key)) {
      return;
    }
    Set<String> set = new HashSet<>(more());
    if (inMore) {
      set.add(key);
    } else {
      set.remove(key);
    }
    more = set;
    writeSet(KEY_MORE, set);
  }

  /** Whether some option waits under More: the menu then keeps its More option even while it is hidden (filter). */
  public static boolean hasOptionsUnderMore () {
    for (String key : more()) {
      // Restrict from older builds never keeps More: the menu itself never lists it (canChoosePlace); nor does an option
      // this setup leaves out (XDrive's Add to folder while its test is off), which the settings pages don't list
      if (canChoosePlace(key) && isAvailable(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Options Telegram X only lists on More's page (MessageView.fillMessageOptionsImpl; in the menu only when nothing else
   * is there): Report, and an admin's Restrict and Remove from group. Both settings pages show them under More unless
   * the user put them in the menu ({@link #isInMenu}).
   */
  public static boolean isTgxMoreOnly (String key) {
    return "report".equals(key) || "restrict".equals(key);
  }

  /**
   * Whether the settings pages offer the menu or More for the option. Not for More itself, nor for Restrict and Remove
   * from group: Telegram X only knows whether they apply once More's page has loaded the sender's membership, so they
   * can't be in the menu.
   */
  public static boolean canChoosePlace (String key) {
    return canGoInMore(key) && !"restrict".equals(key);
  }

  /** Whether one of Telegram X's options of More's page ({@link #isTgxMoreOnly}) goes in the menu itself (MessageView). */
  public static boolean isInMenu (String key) {
    return inMenu().contains(key);
  }

  private static void setInMenu (String key, boolean wanted) {
    boolean add = wanted && isTgxMoreOnly(key) && canChoosePlace(key);
    Set<String> current = inMenu();
    if (add == current.contains(key)) {
      return;
    }
    Set<String> set = new HashSet<>(current);
    if (add) {
      set.add(key);
    } else {
      set.remove(key);
    }
    inMenu = set;
    writeSet(KEY_IN_MENU, set);
  }

  /** Where both settings pages show a shown option: under More (put there, or Telegram X's own place) or in the menu. */
  public static boolean showsUnderMore (String key) {
    return isInMore(key) || (isTgxMoreOnly(key) && !isInMenu(key));
  }

  /**
   * Whether a shown option waits under a More the menu leaves out: More hidden, with no option the user put under it to
   * keep it ({@link #filter}). Telegram X's options of More's page (Report, Restrict) then go with it, so both settings
   * pages say so instead of "under More".
   */
  public static boolean isHiddenWithMore (String key) {
    return showsUnderMore(key) && isHidden(KEY_MORE_OPTION) && !hasOptionsUnderMore();
  }

  /**
   * The settings pages' choice between the menu and More. Telegram X's options of More's page are there by default, so
   * for them only the wish to have them in the menu is stored. Putting an option under More turns a hidden More back
   * on: the menu needs it to reach the option, and the settings pages then show More as the menu does.
   */
  public static void setUnderMore (String key, boolean underMore) {
    if (isTgxMoreOnly(key)) {
      setInMore(key, false);
      setInMenu(key, !underMore);
    } else {
      setInMore(key, underMore);
    }
    if (underMore && canGoInMore(key) && isHidden(KEY_MORE_OPTION)) {
      setHidden(KEY_MORE_OPTION, false);
    }
  }

  /** After a backup import or reset. */
  public static void reload () {
    hidden = null;
    more = null;
    inMenu = null;
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

  /**
   * The menu of a message: drops the hidden options and the ones under More, and XGram's "Clear from cache" when
   * Telegram X's own is there too. While some option waits under More, the menu keeps a More option (even one the
   * user hid), so those stay reachable.
   */
  public static void filter (IntList ids, IntList icons, StringList strings) {
    String[] names = strings.get();
    int count = ids.size();
    if (icons.size() != count || names.length != count) {
      return; // not one icon and name per option: leave it as it is
    }
    Set<String> set = hidden(), moreSet = more();
    boolean hasTgxCache = ids.indexOf(R.id.btn_deleteFile) != -1;
    // Telegram X put its only More option, "Messages from X", in the menu itself: Messages by sender (the same screen)
    // under More then adds no More, whose page would only repeat it (filterMore leaves it out there)
    boolean hasTgxSender = ids.indexOf(R.id.btn_messageViewList) != -1;
    int[] idArray = ids.get(), iconArray = icons.get();
    IntList keptIds = new IntList(count), keptIcons = new IntList(count);
    StringList keptNames = new StringList(count);
    int moreIndex = -1;
    boolean anyInMore = false;
    for (int i = 0; i < count; i++) {
      int id = idArray[i];
      if (id == R.id.btn_messageMore) {
        moreIndex = i; // added back below, when it is wanted
        continue;
      }
      if (isHiddenId(id, set) || (hasTgxCache && id == R.id.btn_xgramClearFromCache)) {
        continue;
      }
      if (isHiddenId(id, moreSet)) {
        if (!(hasTgxSender && id == R.id.btn_xgramMessagesBySender)) {
          anyInMore = true;
        }
        continue;
      }
      keptIds.append(id);
      keptIcons.append(iconArray[i]);
      keptNames.append(names[i]);
    }
    if (anyInMore || (moreIndex != -1 && !set.contains(KEY_MORE_OPTION))) {
      keptIds.append(R.id.btn_messageMore);
      keptIcons.append(moreIndex != -1 ? iconArray[moreIndex] : R.drawable.baseline_more_horiz_24);
      keptNames.append(moreIndex != -1 ? names[moreIndex] : Lang.getString(R.string.MoreMessageOptions));
    }
    if (keptIds.isEmpty()) {
      order(ids, icons, strings); // never leave the menu empty
      return;
    }
    replace(ids, icons, strings, keptIds, keptIcons, keptNames);
    order(ids, icons, strings);
  }

  /**
   * The page More opens: the options the user put under More (taken from the menu's own options, {@code menuIds}),
   * then Telegram X's own (moderation: messages from the sender, restrict…) unless hidden or already in the menu.
   */
  public static void filterMore (IntList ids, IntList icons, StringList strings, IntList menuIds, IntList menuIcons, StringList menuStrings) {
    String[] names = strings.get(), menuNames = menuStrings.get();
    int count = ids.size(), menuCount = menuIds.size();
    if (icons.size() != count || names.length != count || menuIcons.size() != menuCount || menuNames.length != menuCount) {
      return; // not one icon and name per option: leave it as it is
    }
    Set<String> set = hidden(), moreSet = more();
    // What the menu itself shows (to skip Telegram X's single More option, which it put in the menu)
    IntList shownIds = new IntList(menuCount);
    IntList scratchIcons = new IntList(menuCount);
    StringList scratchNames = new StringList(menuCount);
    for (int i = 0; i < menuCount; i++) {
      shownIds.append(menuIds.get(i));
      scratchIcons.append(menuIcons.get(i));
      scratchNames.append(menuNames[i]);
    }
    filter(shownIds, scratchIcons, scratchNames);
    // Telegram X's Delete from cache usually is on this page: XGram's Clear from cache then never joins it
    boolean hasTgxCache = menuIds.indexOf(R.id.btn_deleteFile) != -1 || ids.indexOf(R.id.btn_deleteFile) != -1;
    // Telegram X's "Messages from X" here: XGram's Messages by sender (the same screen) stays out
    boolean hasTgxSender = ids.indexOf(R.id.btn_messageViewList) != -1;
    IntList keptIds = new IntList(count + menuCount), keptIcons = new IntList(count + menuCount);
    StringList keptNames = new StringList(count + menuCount);
    if (!moreSet.isEmpty()) {
      int[] menuIdArray = menuIds.get(), menuIconArray = menuIcons.get();
      for (int i = 0; i < menuCount; i++) {
        int id = menuIdArray[i];
        if (id == R.id.btn_messageMore || !isHiddenId(id, moreSet) || isHiddenId(id, set) || (hasTgxCache && id == R.id.btn_xgramClearFromCache) ||
          (hasTgxSender && id == R.id.btn_xgramMessagesBySender) || keptIds.indexOf(id) != -1) {
          continue;
        }
        keptIds.append(id);
        keptIcons.append(menuIconArray[i]);
        keptNames.append(menuNames[i]);
      }
    }
    int[] idArray = ids.get(), iconArray = icons.get();
    for (int i = 0; i < count; i++) {
      int id = idArray[i];
      if (id == R.id.btn_messageMore || isHiddenId(id, set) || shownIds.indexOf(id) != -1 || keptIds.indexOf(id) != -1) {
        continue;
      }
      keptIds.append(id);
      keptIcons.append(iconArray[i]);
      keptNames.append(names[i]);
    }
    // Everything on the page hidden: it stays empty rather than bringing the hidden options back. MessagesController's
    // More opens no page then, and the menu leaves such a More out (dropMore)
    replace(ids, icons, strings, keptIds, keptIcons, keptNames);
    if (!ids.isEmpty()) {
      order(ids, icons, strings);
    }
  }

  /**
   * Leaves Telegram X's More out of a menu (before {@link #filter}) whose page came out empty in {@link #filterMore}:
   * the user hid everything on it, so More would open nothing.
   */
  public static void dropMore (IntList ids, IntList icons, StringList strings) {
    String[] names = strings.get();
    int count = ids.size();
    int index = ids.indexOf(R.id.btn_messageMore);
    if (index == -1 || icons.size() != count || names.length != count) {
      return;
    }
    IntList keptIds = new IntList(count), keptIcons = new IntList(count);
    StringList keptNames = new StringList(count);
    for (int i = 0; i < count; i++) {
      if (i != index) {
        keptIds.append(ids.get(i));
        keptIcons.append(icons.get(i));
        keptNames.append(names[i]);
      }
    }
    replace(ids, icons, strings, keptIds, keptIcons, keptNames);
  }

  /** Whether the page More opens has an option that needs the menu's tag (Save: the files the menu found downloaded). */
  public static boolean needsMenuTag (IntList ids) {
    return ids.indexOf(R.id.btn_saveFile) != -1 || ids.indexOf(R.id.btn_saveGif) != -1;
  }

  /**
   * The tag of the page More opens while it lists Save: the menu's tag, for Save alone. Telegram X's own options there
   * see none, as on its own page (Delete from cache then deletes every file of the message, not just the saved ones).
   */
  public static final class MoreTag {
    public final Object menuTag;

    public MoreTag (Object menuTag) {
      this.menuTag = menuTag;
    }
  }

  /** The tag Save works with: the menu's own, also when Save is on the page More opens. */
  public static Object saveTag (Object tag) {
    return tag instanceof MoreTag ? ((MoreTag) tag).menuTag : tag;
  }

  private static void replace (IntList ids, IntList icons, StringList strings, IntList newIds, IntList newIcons, StringList newNames) {
    String[] newNameArray = newNames.get();
    ids.clear();
    icons.clear();
    strings.clear();
    for (int i = 0; i < newIds.size(); i++) {
      ids.append(newIds.get(i));
      icons.append(newIcons.get(i));
      strings.append(newNameArray[i]);
    }
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
