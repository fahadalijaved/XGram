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
package com.fahadali.xgram;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.leveldb.LevelDB;

/**
 * XGram's own preferences, stored next to Telegram X's settings under keys prefixed with {@code xgram_}.
 * Values are cached in memory: some of them are read on every message view.
 */
public final class XgramSettings {
  public interface Listener {
    void onXgramSettingChanged (String key);
  }

  public static final String KEY_GHOST_DONT_READ = "xgram_ghost_dont_read";
  public static final String KEY_GHOST_DONT_TYPE = "xgram_ghost_dont_type";
  public static final String KEY_GHOST_OFFLINE = "xgram_ghost_offline";
  public static final String KEY_GHOST_READ_ON_SEND = "xgram_ghost_read_on_send";
  public static final String KEY_GHOST_LOCAL_READ = "xgram_ghost_local_read";
  public static final String KEY_GHOST_TYPING_FOR_STICKERS = "xgram_ghost_typing_for_stickers";
  public static final String KEY_GHOST_SCHEDULE_SEND = "xgram_ghost_schedule_send";
  public static final String KEY_GHOST_SILENT = "xgram_ghost_silent";
  public static final String KEY_HISTORY_SAVE_DELETED = "xgram_history_save_deleted";
  public static final String KEY_HISTORY_SAVE_EDITS = "xgram_history_save_edits";
  public static final String KEY_HISTORY_SAVE_MEDIA = "xgram_history_save_media";
  public static final String KEY_HISTORY_RED_DELETED = "xgram_history_red_deleted";
  public static final String KEY_HIDE_SPONSORED = "xgram_hide_sponsored";
  public static final String KEY_SHOW_PEER_IDS = "xgram_show_peer_ids";
  public static final String KEY_TWO_PANE = "xgram_two_pane";
  public static final String KEY_ICON_THEME = "xgram_icon_theme";
  public static final String KEY_MERGE_COMMUNITIES = "xgram_merge_communities";
  public static final String KEY_SAVE_LAST_SEEN = "xgram_save_last_seen";
  public static final String KEY_SHOW_SECONDS = "xgram_show_seconds";
  public static final String KEY_TAP_TIME_DETAILS = "xgram_tap_time_details";
  public static final String KEY_CLEAN_LINKS = "xgram_clean_links";
  public static final String KEY_FIX_LINK_PREVIEWS = "xgram_fix_link_previews";
  public static final String KEY_ALLOW_SCREENSHOTS = "xgram_allow_screenshots";
  public static final String KEY_SAVE_PROTECTED = "xgram_save_protected";
  public static final String KEY_PROXY_OFF_VPN = "xgram_proxy_off_vpn";
  public static final String KEY_PROXY_OFF_WIFI = "xgram_proxy_off_wifi";
  public static final String KEY_PROXY_OFF_MOBILE = "xgram_proxy_off_mobile";
  public static final String KEY_HIDE_PHONE = "xgram_hide_phone";
  public static final String KEY_HISTORY_RETENTION = "xgram_history_retention";
  public static final String KEY_BLOCK_CONTENT_CAPTURE = "xgram_block_content_capture";
  public static final String KEY_AI_TOOLS = "xgram_ai_tools";
  public static final String KEY_HIDE_FAB = "xgram_hide_fab";
  /** Debug builds: the Send button's test items (Developer). No other key starts with it (backup and reset match by prefix). */
  public static final String KEY_SEND_MENU_TESTS = "xgram_send_menu_tests";
  public static final String KEY_CONNECTION_STATUS_START = "xgram_connection_status_start";
  public static final String KEY_ALL_CHATS_LABEL = "xgram_all_chats_label";
  /** Prefix: followed by the account id (see {@link #hideFolders}). */
  public static final String KEY_HIDE_FOLDERS = "xgram_hide_folders_";
  /** Prefix: followed by the account id (see {@link #hideAllChats}). */
  public static final String KEY_HIDE_ALL_CHATS = "xgram_hide_all_chats_";
  public static final String KEY_PULL_TO_NEXT = "xgram_pull_to_next";
  public static final String KEY_DOWNLOAD_BOOST = "xgram_download_boost_on";
  public static final String KEY_DOWNLOAD_BOOST_LEVEL = "xgram_download_boost_level";
  public static final String KEY_TAP_TO_RECORD = "xgram_tap_to_record";
  public static final String KEY_SYSTEM_EMOJI = "xgram_system_emoji";
  /** Prefix of the message box buttons the user hid, followed by one of the {@code INPUT_BUTTON_*} names. */
  public static final String KEY_INPUT_BUTTON_HIDDEN = "xgram_input_hide_";
  /** The attach button stays next to the send button while typing (on by default). */
  public static final String KEY_INPUT_ATTACH_TYPING = "xgram_input_attach_typing";
  /** Passcode lock: no lock button in the chat list's header. */
  public static final String KEY_HIDE_LOCK_BUTTON = "xgram_hide_lock_button";
  /** Prefix of the chat list header buttons the user hid (Chat list → Header buttons), followed by a {@code HEADER_BUTTON_*} name. */
  public static final String KEY_HEADER_BUTTON_HIDDEN = "xgram_header_hide_";
  public static final String HEADER_BUTTON_GHOST = "ghost", HEADER_BUTTON_SEARCH = "search";
  /** Message menu: a popup next to the message (Telegram) instead of the sheet. */
  public static final String KEY_MESSAGE_POPUP = "xgram_message_popup";
  /** A bot's profile → Add to group or channel: as an admin in groups too (channels always), with every right. */
  public static final String KEY_ADD_BOT_ADMIN = "xgram_add_bot_admin", KEY_ADD_BOT_ALL_RIGHTS = "xgram_add_bot_all_rights";
  /** XDrive (Saved Messages as the drive) is a test for now: Testing utilities switches it on (debug builds on, CI off). */
  public static final String KEY_XDRIVE_TEST = "xgram_xdrive_test";
  public static final String KEY_XDRIVE_OPENS_CHAT = "xgram_xdrive_opens_chat";
  public static final String KEY_XDRIVE_AI_PROVIDER = "xgram_xdrive_ai_provider";
  /** Followed by the provider's id (claude, gemini, openai). */
  public static final String KEY_XDRIVE_AI_KEY = "xgram_xdrive_ai_key_";
  public static final String KEY_XDRIVE_AI_MODEL = "xgram_xdrive_ai_model_";
  public static final String KEY_XDRIVE_AI_BASE_URL = "xgram_xdrive_ai_base_url";
  /** Camera X is a test for now: the main camera with CameraX and XGram's controls (debug builds on, CI builds off). */
  public static final String KEY_CAMERA_X_TEST = "xgram_camera_x_test";

  public static final String INPUT_BUTTON_COMMANDS = "commands";
  public static final String INPUT_BUTTON_CAMERA = "camera";
  public static final String INPUT_BUTTON_SCHEDULED = "scheduled";
  public static final String INPUT_BUTTON_VOICE = "voice";

  private static XgramSettings instance;

  public static XgramSettings instance () {
    if (instance == null) {
      synchronized (XgramSettings.class) {
        if (instance == null) {
          instance = new XgramSettings();
        }
      }
    }
    return instance;
  }

  private final ReferenceList<Listener> listeners = new ReferenceList<>(true);
  private volatile boolean ghostDontRead, ghostDontType, ghostOffline, ghostReadOnSend, ghostLocalRead, ghostTypingForStickers, ghostScheduleSend, ghostSilent;
  private volatile boolean historySaveDeleted, historySaveEdits, historySaveMedia, historyRedDeleted;
  private volatile boolean hideSponsored, showPeerIds, twoPaneLayout, saveLastSeen, showSeconds, cleanLinks, fixLinkPreviews, allowScreenshots, saveProtected, proxyOffOnVpn, proxyOffOnWifi, proxyOffOnMobile, hidePhone, blockContentCapture, iconTheme, mergeCommunities;
  private volatile boolean aiTools, hideFab, pullToNext, allChatsLabel, connectionStatusAtStart, sendMenuTests;
  private volatile boolean tapTimeForDetails;
  private volatile boolean tapToRecord;
  private volatile boolean systemEmoji;
  private volatile boolean downloadBoost;
  private volatile int downloadBoostLevel;

  private XgramSettings () {
    LevelDB pmc = pmc();
    ghostDontRead = pmc.getBoolean(KEY_GHOST_DONT_READ, false);
    ghostDontType = pmc.getBoolean(KEY_GHOST_DONT_TYPE, false);
    ghostOffline = pmc.getBoolean(KEY_GHOST_OFFLINE, false);
    ghostReadOnSend = pmc.getBoolean(KEY_GHOST_READ_ON_SEND, true);
    ghostLocalRead = pmc.getBoolean(KEY_GHOST_LOCAL_READ, true);
    ghostTypingForStickers = pmc.getBoolean(KEY_GHOST_TYPING_FOR_STICKERS, false);
    historySaveDeleted = pmc.getBoolean(KEY_HISTORY_SAVE_DELETED, true);
    historySaveEdits = pmc.getBoolean(KEY_HISTORY_SAVE_EDITS, true);
    historySaveMedia = pmc.getBoolean(KEY_HISTORY_SAVE_MEDIA, true);
    historyRedDeleted = pmc.getBoolean(KEY_HISTORY_RED_DELETED, false);
    hideSponsored = pmc.getBoolean(KEY_HIDE_SPONSORED, true);
    showPeerIds = pmc.getBoolean(KEY_SHOW_PEER_IDS, true);
    twoPaneLayout = pmc.getBoolean(KEY_TWO_PANE, true);
    iconTheme = pmc.getBoolean(KEY_ICON_THEME, false);
    mergeCommunities = pmc.getBoolean(KEY_MERGE_COMMUNITIES, true);
    saveLastSeen = pmc.getBoolean(KEY_SAVE_LAST_SEEN, true);
    showSeconds = pmc.getBoolean(KEY_SHOW_SECONDS, false);
    tapTimeForDetails = pmc.getBoolean(KEY_TAP_TIME_DETAILS, false); // off by default (as NagramX): the time pill sits over media corners
    videoAutoHide = pmc.getBoolean(KEY_VIDEO_AUTO_HIDE, true);
    cleanLinks = pmc.getBoolean(KEY_CLEAN_LINKS, true);
    fixLinkPreviews = pmc.getBoolean(KEY_FIX_LINK_PREVIEWS, true);
    allowScreenshots = pmc.getBoolean(KEY_ALLOW_SCREENSHOTS, false);
    saveProtected = pmc.getBoolean(KEY_SAVE_PROTECTED, false);
    proxyOffOnVpn = pmc.getBoolean(KEY_PROXY_OFF_VPN, false);
    proxyOffOnWifi = pmc.getBoolean(KEY_PROXY_OFF_WIFI, false);
    proxyOffOnMobile = pmc.getBoolean(KEY_PROXY_OFF_MOBILE, false);
    hidePhone = pmc.getBoolean(KEY_HIDE_PHONE, false);
    blockContentCapture = pmc.getBoolean(KEY_BLOCK_CONTENT_CAPTURE, true);
    ghostScheduleSend = pmc.getBoolean(KEY_GHOST_SCHEDULE_SEND, false);
    ghostSilent = pmc.getBoolean(KEY_GHOST_SILENT, false);
    aiTools = pmc.getBoolean(KEY_AI_TOOLS, true);
    hideFab = pmc.getBoolean(KEY_HIDE_FAB, false);
    sendMenuTests = pmc.getBoolean(KEY_SEND_MENU_TESTS, false);
    connectionStatusAtStart = pmc.getBoolean(KEY_CONNECTION_STATUS_START, false);
    allChatsLabel = pmc.getBoolean(KEY_ALL_CHATS_LABEL, false);
    pullToNext = pmc.getBoolean(KEY_PULL_TO_NEXT, true);
    tapToRecord = pmc.getBoolean(KEY_TAP_TO_RECORD, false);
    systemEmoji = pmc.getBoolean(KEY_SYSTEM_EMOJI, false);
    downloadBoost = pmc.getBoolean(KEY_DOWNLOAD_BOOST, false);
    downloadBoostLevel = Math.max(XgramDownloadBoost.LEVEL_LOW, Math.min(XgramDownloadBoost.LEVEL_MAX, pmc.getInt(KEY_DOWNLOAD_BOOST_LEVEL, XgramDownloadBoost.LEVEL_DEFAULT)));
    applyGhostSend();
  }

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  public void addListener (Listener listener) {
    listeners.add(listener);
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private void putInt (String key, int value) {
    pmc().putInt(key, value);
    for (Listener listener : listeners) {
      listener.onXgramSettingChanged(key);
    }
  }

  private void put (String key, boolean value) {
    pmc().putBoolean(key, value);
    for (Listener listener : listeners) {
      listener.onXgramSettingChanged(key);
    }
  }

  // Ghost mode

  /** Don't mark messages as read while viewing them (chats open in TDLib's history preview mode). */
  public boolean ghostDontRead () {
    return ghostDontRead;
  }

  public void setGhostDontRead (boolean value) {
    ghostDontRead = value;
    put(KEY_GHOST_DONT_READ, value);
    applyGhostSend();
    if (!value) {
      com.fahadali.xgram.ghost.XgramLocalRead.clearAll();
    }
  }

  /** Don't send typing, recording or uploading status. */
  public boolean ghostDontType () {
    return ghostDontType;
  }

  public void setGhostDontType (boolean value) {
    ghostDontType = value;
    put(KEY_GHOST_DONT_TYPE, value);
    applyGhostSend();
  }

  /** Stay offline: never set the account's online status. */
  public boolean ghostOffline () {
    return ghostOffline;
  }

  public void setGhostOffline (boolean value) {
    ghostOffline = value;
    put(KEY_GHOST_OFFLINE, value);
    applyGhostSend();
  }

  /** While staying offline, send messages as scheduled a few seconds later so sending doesn't show you online (AyuGram). */
  public boolean ghostScheduleSend () {
    return ghostScheduleSend;
  }

  /** While any ghost option is on, send messages without notification sound (AyuGram). */
  public boolean ghostSilent () {
    return ghostSilent;
  }

  public void setGhostSilent (boolean value) {
    ghostSilent = value;
    put(KEY_GHOST_SILENT, value);
    applyGhostSend();
  }

  private void applyGhostSend () {
    com.fahadali.xgram.ghost.XgramGhostSend.apply(ghostOffline && ghostScheduleSend, ghostSilent && (ghostDontRead || ghostDontType || ghostOffline));
  }

  public void setGhostScheduleSend (boolean value) {
    ghostScheduleSend = value;
    put(KEY_GHOST_SCHEDULE_SEND, value);
    applyGhostSend();
  }

  /** While not reading messages, still mark a chat as read when you send a message to it. */
  public boolean ghostReadOnSend () {
    return ghostReadOnSend;
  }

  public void setGhostReadOnSend (boolean value) {
    ghostReadOnSend = value;
    put(KEY_GHOST_READ_ON_SEND, value);
  }

  /** While not reading messages, chats you viewed still look read on this device (server keeps them unread). */
  public boolean ghostLocalRead () {
    return ghostLocalRead;
  }

  public void setGhostLocalRead (boolean value) {
    ghostLocalRead = value;
    put(KEY_GHOST_LOCAL_READ, value);
    if (!value) {
      com.fahadali.xgram.ghost.XgramLocalRead.clearAll();
    }
  }

  /** Others see "typing" instead of "choosing a sticker". */
  public boolean ghostTypingForStickers () {
    return ghostTypingForStickers;
  }

  public void setGhostTypingForStickers (boolean value) {
    ghostTypingForStickers = value;
    put(KEY_GHOST_TYPING_FOR_STICKERS, value);
  }

  public boolean isGhostModeEnabled () {
    return ghostDontRead && ghostDontType && ghostOffline;
  }

  /** Show when people who hide their last seen were last active, as far as this device saw. */
  public boolean saveLastSeen () {
    return saveLastSeen;
  }

  public void setSaveLastSeen (boolean value) {
    saveLastSeen = value;
    put(KEY_SAVE_LAST_SEEN, value);
  }

  /** Message times with seconds. */
  public boolean showSeconds () {
    return showSeconds;
  }

  public void setShowSeconds (boolean value) {
    showSeconds = value;
    put(KEY_SHOW_SECONDS, value);
  }

  /** Tapping a message's time shows the exact dates and the message ID (NagramX). Read on touch down. */
  public boolean tapTimeForDetails () {
    return tapTimeForDetails;
  }

  public void setTapTimeForDetails (boolean value) {
    tapTimeForDetails = value;
    put(KEY_TAP_TIME_DETAILS, value);
  }

  /** Remove tracking parameters (utm_*, fbclid, si…) from links before opening them. */
  public boolean cleanLinks () {
    return cleanLinks;
  }

  public void setCleanLinks (boolean value) {
    cleanLinks = value;
    put(KEY_CLEAN_LINKS, value);
  }

  /** Links you send to X, Instagram, TikTok, Reddit, Bluesky and Pixiv posts get the preview of an embed-friendly mirror (NagramX). */
  public boolean fixLinkPreviews () {
    return fixLinkPreviews;
  }

  public void setFixLinkPreviews (boolean value) {
    fixLinkPreviews = value;
    put(KEY_FIX_LINK_PREVIEWS, value);
  }

  /** Screenshots and screen recording in secret and protected chats (AyuGram). */
  public boolean allowScreenshots () {
    return allowScreenshots;
  }

  public void setAllowScreenshots (boolean value) {
    allowScreenshots = value;
    put(KEY_ALLOW_SCREENSHOTS, value);
  }

  /** Copy and save messages of chats with protected content (forwarding stays blocked by the server). */
  public boolean saveProtected () {
    return saveProtected;
  }

  public void setSaveProtected (boolean value) {
    saveProtected = value;
    put(KEY_SAVE_PROTECTED, value);
  }

  /** Own phone number hidden in Settings. */
  public boolean hidePhone () {
    return hidePhone;
  }

  public void setHidePhone (boolean value) {
    hidePhone = value;
    put(KEY_HIDE_PHONE, value);
  }

  /** Opt out of Android content capture (system intelligence reading screen text). */
  public boolean blockContentCapture () {
    return blockContentCapture;
  }

  public void setBlockContentCapture (boolean value) {
    blockContentCapture = value;
    put(KEY_BLOCK_CONTENT_CAPTURE, value);
  }

  /** Days to keep saved copies of messages that were never deleted or edited (0: forever). */
  public int historyRetentionDays () {
    return pmc().getInt(KEY_HISTORY_RETENTION, 30);
  }

  public void setHistoryRetentionDays (int days) {
    pmc().putInt(KEY_HISTORY_RETENTION, days);
  }

  /** Forwarding starts with "Send as copy" ticked (NagramX). */
  public boolean forwardWithoutAuthor () {
    return pmc().getBoolean("xgram_forward_without_author", false);
  }

  public void setForwardWithoutAuthor (boolean value) {
    pmc().putBoolean("xgram_forward_without_author", value);
  }

  /** Pre-tick "Delete for everyone" in the delete dialog, also for messages of others (NagramX). */
  public boolean deleteForEveryoneByDefault () {
    return pmc().getBoolean("xgram_delete_for_everyone", false);
  }

  public void setDeleteForEveryoneByDefault (boolean value) {
    pmc().putBoolean("xgram_delete_for_everyone", value);
  }

  /** Telegram's AI tools: Summarize in the message menu, the AI editor on long-press of Send. */
  public boolean aiTools () {
    return aiTools;
  }

  public void setAiTools (boolean value) {
    aiTools = value;
    put(KEY_AI_TOOLS, value);
  }

  // Translation

  public static final String KEY_TR_ALWAYS = "xgram_tr_always";

  private int trAlways = -1;

  /** Translate in the message menu for every message, not only those outside Telegram X's "Do not translate" languages. */
  public boolean translateAlways () {
    if (trAlways < 0) {
      trAlways = pmc().getBoolean(KEY_TR_ALWAYS, true) ? 1 : 0;
    }
    return trAlways == 1;
  }

  public void setTranslateAlways (boolean value) {
    trAlways = value ? 1 : 0;
    put(KEY_TR_ALWAYS, value);
  }

  public static final String KEY_TR_PROVIDER = "xgram_tr_provider";
  public static final String KEY_TR_FALLBACK = "xgram_tr_fallback";
  /** Followed by the provider: the user has read that the text goes to that service. Kept out of backups. */
  private static final String KEY_TR_NOTICE = "xgram_trnotice_";

  private int trProvider = -1, trFallback = -1;

  /**
   * The translation service, one of {@code XgramTranslator.PROVIDER_*}: Telegram by default, and Telegram while a
   * third-party service's notice hasn't been accepted on this device (e.g. it came from a backup import).
   */
  public int translateProvider () {
    if (trProvider < 0) {
      int value = pmc().getInt(KEY_TR_PROVIDER, com.fahadali.xgram.translate.XgramTranslator.PROVIDER_TELEGRAM);
      trProvider = com.fahadali.xgram.translate.XgramTranslator.isValidProvider(value) ? value : com.fahadali.xgram.translate.XgramTranslator.PROVIDER_TELEGRAM;
    }
    if (com.fahadali.xgram.translate.XgramTranslator.isThirdParty(trProvider) && !translateNoticeShown(trProvider)) {
      return com.fahadali.xgram.translate.XgramTranslator.PROVIDER_TELEGRAM;
    }
    return trProvider;
  }

  public void setTranslateProvider (int provider) {
    trProvider = provider;
    putInt(KEY_TR_PROVIDER, provider);
  }

  /** When the chosen service fails, Telegram translates instead. */
  public boolean translateFallback () {
    if (trFallback < 0) {
      trFallback = pmc().getBoolean(KEY_TR_FALLBACK, true) ? 1 : 0;
    }
    return trFallback == 1;
  }

  public void setTranslateFallback (boolean value) {
    trFallback = value ? 1 : 0;
    put(KEY_TR_FALLBACK, value);
  }

  public boolean translateNoticeShown (int provider) {
    return pmc().getBoolean(KEY_TR_NOTICE + provider, false);
  }

  public void setTranslateNoticeShown (int provider) {
    pmc().putBoolean(KEY_TR_NOTICE + provider, true);
  }

  public static final String KEY_TR_AUTO_ALL = "xgram_tr_auto_all";
  public static final String KEY_TR_TARGET = "xgram_tr_target";

  private int trAutoAll = -1;
  private String trTarget;

  /** Auto-translate in every chat not set otherwise in its menu. Off by default. */
  public boolean translateAutoAll () {
    if (trAutoAll < 0) {
      trAutoAll = pmc().getBoolean(KEY_TR_AUTO_ALL, false) ? 1 : 0;
    }
    return trAutoAll == 1;
  }

  public void setTranslateAutoAll (boolean value) {
    trAutoAll = value ? 1 : 0;
    put(KEY_TR_AUTO_ALL, value);
    com.fahadali.xgram.chats.XgramAutoTranslate.notifyChanged(null, 0);
  }

  /** "Translate to": a language code, or empty for the app's language. */
  public String translateTarget () {
    if (trTarget == null) {
      String value = pmc().getString(KEY_TR_TARGET, "");
      trTarget = value != null ? value : "";
    }
    return trTarget;
  }

  public void setTranslateTarget (@androidx.annotation.Nullable String language) {
    trTarget = language != null ? language : "";
    if (trTarget.isEmpty()) {
      pmc().remove(KEY_TR_TARGET);
    } else {
      pmc().putString(KEY_TR_TARGET, trTarget);
    }
    com.fahadali.xgram.chats.XgramAutoTranslate.notifyChanged(null, 0);
  }

  // Message box

  /** A tap on the microphone records hands-free, a long press switches between voice and video messages. */
  public boolean tapToRecord () {
    return tapToRecord;
  }

  public void setTapToRecord (boolean value) {
    tapToRecord = value;
    put(KEY_TAP_TO_RECORD, value);
  }

  // Emoji

  /** Emoji drawn with the phone's own emoji font (see {@link com.fahadali.xgram.appearance.XgramSystemEmoji}). Read on every emoji draw. */
  public boolean systemEmoji () {
    return systemEmoji;
  }

  public void setSystemEmoji (boolean value) {
    systemEmoji = value;
    put(KEY_SYSTEM_EMOJI, value);
  }

  /** Message box buttons the user hid ({@code INPUT_BUTTON_*}). Telegram X still decides when a shown one appears. */
  public boolean inputButtonHidden (String button) {
    return pmc().getBoolean(KEY_INPUT_BUTTON_HIDDEN + button, false);
  }

  public void setInputButtonHidden (String button, boolean hidden) {
    String key = KEY_INPUT_BUTTON_HIDDEN + button;
    if (hidden) {
      pmc().putBoolean(key, true);
    } else {
      pmc().remove(key);
    }
    for (Listener listener : listeners) {
      listener.onXgramSettingChanged(key);
    }
  }

  /** The attach button stays next to the send button while typing (Message box buttons). Read when a chat's composer updates. */
  public boolean inputAttachWhileTyping () {
    return pmc().getBoolean(KEY_INPUT_ATTACH_TYPING, true);
  }

  public void setInputAttachWhileTyping (boolean value) {
    put(KEY_INPUT_ATTACH_TYPING, value);
  }

  /** Passcode lock: the chat list's header has no lock button (the app still locks with Auto-lock). */
  public boolean hideLockButton () {
    return pmc().getBoolean(KEY_HIDE_LOCK_BUTTON, false);
  }

  public void setHideLockButton (boolean value) {
    put(KEY_HIDE_LOCK_BUTTON, value);
  }

  /**
   * Chat list → Header buttons: a button of the chat list's header the user hid ({@code HEADER_BUTTON_*}). The lock
   * and Activity's bell keep their own keys ({@link #hideLockButton()}, {@link #activityHeader()}).
   */
  public boolean headerButtonHidden (String button) {
    return pmc().getBoolean(KEY_HEADER_BUTTON_HIDDEN + button, false);
  }

  public void setHeaderButtonHidden (String button, boolean hidden) {
    String key = KEY_HEADER_BUTTON_HIDDEN + button;
    if (hidden) {
      pmc().putBoolean(key, true);
    } else {
      pmc().remove(key);
    }
    for (Listener listener : listeners) {
      listener.onXgramSettingChanged(key);
    }
  }

  /** Message menu → Popup menu: a long press opens the menu next to the message, the reactions above it (Telegram). */
  public boolean messagePopup () {
    return pmc().getBoolean(KEY_MESSAGE_POPUP, false);
  }

  public void setMessagePopup (boolean value) {
    put(KEY_MESSAGE_POPUP, value);
  }

  /** Add to group or channel: the bot joins groups as an admin too (channels always take bots as admins). */
  public boolean addBotAsAdmin () {
    return pmc().getBoolean(KEY_ADD_BOT_ADMIN, false);
  }

  public void setAddBotAsAdmin (boolean value) {
    put(KEY_ADD_BOT_ADMIN, value);
  }

  /** Add to group or channel: an admin bot gets every right the user can give (on by default), or the ones it asks for. */
  public boolean addBotAllRights () {
    return pmc().getBoolean(KEY_ADD_BOT_ALL_RIGHTS, true);
  }

  public void setAddBotAllRights (boolean value) {
    put(KEY_ADD_BOT_ALL_RIGHTS, value);
  }

  /** XDrive (a test): Saved Messages renamed XDrive, opening as the drive (com.fahadali.xgram.xdrive). */
  public boolean xdriveTest () {
    return pmc().getBoolean(KEY_XDRIVE_TEST, org.thunderdog.challegram.BuildConfig.DEBUG);
  }

  public void setXdriveTest (boolean value) {
    put(KEY_XDRIVE_TEST, value);
    com.fahadali.xgram.xdrive.XDrive.onSettingChanged(value);
    org.thunderdog.challegram.core.Lang.xgramNotifyStringsChanged(); // names on screen follow
  }

  /** With XDrive on: Saved Messages still opens as the chat (XDrive is in its ⋮ menu). */
  public boolean xdriveOpensChat () {
    return pmc().getBoolean(KEY_XDRIVE_OPENS_CHAT, false);
  }

  public void setXdriveOpensChat (boolean value) {
    put(KEY_XDRIVE_OPENS_CHAT, value);
  }

  /** XDrive's cloud AI for Organize: the provider's id (claude, gemini, openai), empty: none set up. */
  public String xdriveAiProvider () {
    String value = pmc().getString(KEY_XDRIVE_AI_PROVIDER, "");
    return value != null ? value : "";
  }

  /** The user's API key, model and (OpenAI-compatible) base URL; kept in this app's private storage only. */
  public String xdriveAiKey (String provider) {
    String value = pmc().getString(KEY_XDRIVE_AI_KEY + provider, "");
    return value != null ? value : "";
  }

  public String xdriveAiModel (String provider) {
    String value = pmc().getString(KEY_XDRIVE_AI_MODEL + provider, "");
    return value != null ? value : "";
  }

  public String xdriveAiBaseUrl () {
    String value = pmc().getString(KEY_XDRIVE_AI_BASE_URL, "");
    return value != null ? value : "";
  }

  public void setXdriveAi (String provider, String key, String model, @androidx.annotation.Nullable String baseUrl) {
    pmc().putString(KEY_XDRIVE_AI_PROVIDER, provider);
    pmc().putString(KEY_XDRIVE_AI_KEY + provider, key);
    pmc().putString(KEY_XDRIVE_AI_MODEL + provider, model);
    if (baseUrl != null) {
      pmc().putString(KEY_XDRIVE_AI_BASE_URL, baseUrl);
    }
  }

  /** Forgets the provider's key (and the provider, when it was the chosen one). */
  public void removeXdriveAi (String provider) {
    pmc().remove(KEY_XDRIVE_AI_KEY + provider);
    if (provider.equals(xdriveAiProvider())) {
      pmc().remove(KEY_XDRIVE_AI_PROVIDER);
    }
  }

  /** Camera X (a test): the in-app camera uses CameraX with lenses, modes, focus and exposure. */
  public boolean cameraXTest () {
    return pmc().getBoolean(KEY_CAMERA_X_TEST, org.thunderdog.challegram.BuildConfig.DEBUG);
  }

  public void setCameraXTest (boolean value) {
    put(KEY_CAMERA_X_TEST, value);
  }

  // Network

  /** Download boost (see {@link XgramDownloadBoost}). Read on every download request, so it applies at once to every account. */
  public boolean downloadBoost () {
    return downloadBoost;
  }

  public void setDownloadBoost (boolean value) {
    downloadBoost = value;
    put(KEY_DOWNLOAD_BOOST, value);
  }

  /** {@link XgramDownloadBoost#LEVEL_LOW} .. {@link XgramDownloadBoost#LEVEL_MAX}, kept while boost is off. */
  public int downloadBoostLevel () {
    return downloadBoostLevel;
  }

  public void setDownloadBoostLevel (int level) {
    downloadBoostLevel = Math.max(XgramDownloadBoost.LEVEL_LOW, Math.min(XgramDownloadBoost.LEVEL_MAX, level));
    putInt(KEY_DOWNLOAD_BOOST_LEVEL, downloadBoostLevel);
  }

  public boolean preferIpv6 () {
    return pmc().getBoolean("xgram_prefer_ipv6", false);
  }

  public void setPreferIpv6 (boolean value) {
    pmc().putBoolean("xgram_prefer_ipv6", value);
    for (org.thunderdog.challegram.telegram.TdlibAccount account : org.thunderdog.challegram.telegram.TdlibManager.instance()) {
      org.thunderdog.challegram.telegram.Tdlib tdlib = account.activeTdlib();
      if (tdlib != null) {
        tdlib.send(new org.drinkless.tdlib.TdApi.SetOption("prefer_ipv6", new org.drinkless.tdlib.TdApi.OptionValueBoolean(value)), tdlib.typedOkHandler());
      }
    }
  }

  /** Messages are at most 600dp wide on wide screens. Cached: read for every message layout. */
  private volatile Boolean limitMessageWidth;

  public boolean limitMessageWidth () {
    Boolean value = limitMessageWidth;
    if (value == null) {
      limitMessageWidth = value = pmc().getBoolean("xgram_limit_message_width", true);
    }
    return value;
  }

  public void setLimitMessageWidth (boolean value) {
    limitMessageWidth = value;
    pmc().putBoolean("xgram_limit_message_width", value);
  }

  // Drag and drop, side by side (adaptive plan part 2)

  public static final String KEY_DRAG_DROP = "xgram_drag_drop";
  public static final String KEY_OPEN_BESIDE = "xgram_open_beside";

  /** Drop files and text onto chats, drag media out of the media viewer. Cached: read on every drag. */
  private volatile Boolean dragDrop;

  public boolean dragDrop () {
    Boolean value = dragDrop;
    if (value == null) {
      dragDrop = value = pmc().getBoolean(KEY_DRAG_DROP, true);
    }
    return value;
  }

  public void setDragDrop (boolean value) {
    dragDrop = value;
    pmc().putBoolean(KEY_DRAG_DROP, value);
  }

  /** External links and files open in a window next to XGram in split screen and on large screens. */
  public boolean openBeside () {
    return pmc().getBoolean(KEY_OPEN_BESIDE, false);
  }

  public void setOpenBeside (boolean value) {
    pmc().putBoolean(KEY_OPEN_BESIDE, value);
  }

  /** Hide messages from people you blocked in groups (AyuGram, NagramX). Cached: checked for every message. */
  private volatile Boolean hideBlocked;

  public boolean hideBlocked () {
    Boolean value = hideBlocked;
    if (value == null) {
      hideBlocked = value = pmc().getBoolean("xgram_hide_blocked", false);
    }
    return value;
  }

  public void setHideBlocked (boolean value) {
    hideBlocked = value;
    pmc().putBoolean("xgram_hide_blocked", value);
  }

  // Chat look: sticker and emoji sizes, bubble shape (AyuGram, exteraGram)

  public static final String KEY_STICKER_SCALE = "xgram_look_sticker";
  public static final String KEY_EMOJI_SCALE = "xgram_look_emoji";
  public static final String KEY_BUBBLE_RADIUS = "xgram_look_bubble";
  public static final String KEY_BUBBLE_MERGE_RADIUS = "xgram_look_bubble_merged";
  public static final String KEY_BUBBLE_TAIL = "xgram_bubble_tail";
  public static final String KEY_AVATAR_AT_LAST = "xgram_avatar_last_message";

  // Cached: read on every message layout.
  private volatile int stickerScale = -1, emojiScale = -1, bubbleRadius = -2, bubbleMergeRadius = -2, bubbleTail = -1, avatarAtLast = -1;

  /** Stickers in messages, percent of Telegram X's size. */
  public int stickerScale () {
    int value = stickerScale;
    if (value < 0) {
      stickerScale = value = pmc().getInt(KEY_STICKER_SCALE, 100);
    }
    return value;
  }

  public void setStickerScale (int percent) {
    stickerScale = percent;
    putInt(KEY_STICKER_SCALE, percent);
  }

  /** Messages of only emoji (big emoji and animated emoji), percent of Telegram X's size. */
  public int emojiScale () {
    int value = emojiScale;
    if (value < 0) {
      emojiScale = value = pmc().getInt(KEY_EMOJI_SCALE, 100);
    }
    return value;
  }

  public void setEmojiScale (int percent) {
    emojiScale = percent;
    putInt(KEY_EMOJI_SCALE, percent);
  }

  /** Bubble corner radius in dp, or -1 to follow the theme. */
  public int bubbleRadius () {
    int value = bubbleRadius;
    if (value == -2) {
      bubbleRadius = value = pmc().getInt(KEY_BUBBLE_RADIUS, -1);
    }
    return value;
  }

  public void setBubbleRadius (int dp) {
    bubbleRadius = dp;
    putInt(KEY_BUBBLE_RADIUS, dp);
  }

  /** Radius of the corners where bubbles of one sender meet, in dp, or -1 to follow the theme. */
  public int bubbleMergeRadius () {
    int value = bubbleMergeRadius;
    if (value == -2) {
      bubbleMergeRadius = value = pmc().getInt(KEY_BUBBLE_MERGE_RADIUS, -1);
    }
    return value;
  }

  public void setBubbleMergeRadius (int dp) {
    bubbleMergeRadius = dp;
    putInt(KEY_BUBBLE_MERGE_RADIUS, dp);
  }

  /** The last bubble of a group gets a tail, as in Telegram. */
  public boolean bubbleTail () {
    int value = bubbleTail;
    if (value < 0) {
      bubbleTail = value = pmc().getBoolean(KEY_BUBBLE_TAIL, false) ? 1 : 0;
    }
    return value == 1;
  }

  public void setBubbleTail (boolean value) {
    bubbleTail = value ? 1 : 0;
    put(KEY_BUBBLE_TAIL, value);
  }

  /** With bubbles, the sender's avatar sits next to the last message of a group (Telegram), not the first. Read on every message draw. */
  public boolean avatarAtLastMessage () {
    int value = avatarAtLast;
    if (value < 0) {
      avatarAtLast = value = pmc().getBoolean(KEY_AVATAR_AT_LAST, false) ? 1 : 0;
    }
    return value == 1;
  }

  public void setAvatarAtLastMessage (boolean value) {
    avatarAtLast = value ? 1 : 0;
    put(KEY_AVATAR_AT_LAST, value);
  }

  // Avatars

  /** Avatar corners as a percentage of a circle (100 = circle, 0 = square), or -1 to follow the theme. */
  public static final int[] AVATAR_CORNERS = {-1, 100, 65, 40, 20};
  private volatile float avatarRadiusFactor = Float.NaN;

  public int avatarCorners () {
    return pmc().getInt("xgram_avatar_corners", -1);
  }

  /** Cached for {@code AvatarReceiver.getDisplayRadius}, which runs on every avatar draw. Negative: theme default. */
  public float avatarRadiusFactor () {
    float factor = avatarRadiusFactor;
    if (Float.isNaN(factor)) {
      int corners = avatarCorners();
      avatarRadiusFactor = factor = corners < 0 ? -1f : corners / 100f;
    }
    return factor;
  }

  public void setAvatarCorners (int percent) {
    pmc().putInt("xgram_avatar_corners", percent);
    avatarRadiusFactor = percent < 0 ? -1f : percent / 100f;
  }

  /** "Chat buttons match avatar shape" (Appearance, Expressive design): the chat's floating buttons take the avatars' shape. */
  public static final String KEY_CHAT_FABS_AVATAR_SHAPE = "xgram_chat_fabs_avatar_shape";
  private volatile int chatFabsAvatarShape = -1; // -1: not read yet

  /** Off by default: the M3E rounded square. Cached for CircleButton.xgramChatFabCornerDp, read while those buttons draw. */
  public boolean chatFabsAvatarShape () {
    int value = chatFabsAvatarShape;
    if (value < 0) {
      chatFabsAvatarShape = value = pmc().getBoolean(KEY_CHAT_FABS_AVATAR_SHAPE, false) ? 1 : 0;
    }
    return value == 1;
  }

  public void setChatFabsAvatarShape (boolean value) {
    chatFabsAvatarShape = value ? 1 : 0;
    pmc().putBoolean(KEY_CHAT_FABS_AVATAR_SHAPE, value);
  }

  private int chatLongPress = -1;

  /** What holding a chat in the chat list does: 0 Telegram X preview, 1 quick actions, 2 compact preview. */
  public int chatLongPress () {
    if (chatLongPress < 0) {
      chatLongPress = pmc().getInt("xgram_chat_long_press", 0);
    }
    return chatLongPress;
  }

  public void setChatLongPress (int mode) {
    chatLongPress = mode;
    pmc().putInt("xgram_chat_long_press", mode);
  }

  // Swipe on a message

  private int swipeLeft = -1, swipeRight = -1;

  /** What a plain swipe on a message does, one of {@link com.fahadali.xgram.chats.XgramSwipeActions}' actions. */
  public int swipeAction (boolean toRight) {
    int action = toRight ? swipeRight : swipeLeft;
    if (action < 0) {
      action = pmc().getInt(toRight ? "xgram_swipe_right" : "xgram_swipe_left", com.fahadali.xgram.chats.XgramSwipeActions.defaultAction(toRight));
      if (toRight) {
        swipeRight = action;
      } else {
        swipeLeft = action;
      }
    }
    return action;
  }

  public void setSwipeAction (boolean toRight, int action) {
    if (toRight) {
      swipeRight = action;
    } else {
      swipeLeft = action;
    }
    pmc().putInt(toRight ? "xgram_swipe_right" : "xgram_swipe_left", action);
  }

  // Enter to send (Messages page, see com.fahadali.xgram.layout.XgramKeyboard)

  public static final String KEY_ENTER_TO_SEND = "xgram_enter_to_send";
  /** Auto: Enter sends on a hardware keyboard; the on-screen keyboard follows Telegram X's Send with Enter. */
  public static final int ENTER_TO_SEND_AUTO = 0;
  public static final int ENTER_TO_SEND_ON = 1;
  public static final int ENTER_TO_SEND_OFF = 2;

  private volatile int enterToSend = -1;

  public int enterToSend () {
    int value = enterToSend;
    if (value < 0) {
      value = pmc().getInt(KEY_ENTER_TO_SEND, ENTER_TO_SEND_AUTO);
      if (value != ENTER_TO_SEND_ON && value != ENTER_TO_SEND_OFF) {
        value = ENTER_TO_SEND_AUTO;
      }
      enterToSend = value;
    }
    return value;
  }

  public void setEnterToSend (int value) {
    enterToSend = value;
    putInt(KEY_ENTER_TO_SEND, value);
  }

  // Undo send (Messages > Message box, see com.fahadali.xgram.chats.XgramUndoSend)

  public static final String KEY_UNDO_SEND = "xgram_undo_send";
  /**
   * Not "xgram_undo_send_voice": {@link #KEY_UNDO_SEND} is an int that backups find by prefix, which would also take
   * this boolean as an int.
   */
  public static final String KEY_UNDO_SEND_VOICE = "xgram_undosend_voice";
  /** Prefix of the chats where messages are sent right away, followed by the account id, "_" and the chat id. */
  public static final String KEY_UNDO_SEND_EXCLUDE = "xgram_usx_";
  /** The delays offered, in seconds; 0 is Off. */
  public static final int[] UNDO_SEND_SECONDS = {0, 3, 5, 10, 20};

  /** Seconds a message from the message box waits before it is sent (0: Off). Any other stored value reads as Off. */
  public int undoSendSeconds () {
    int value = pmc().getInt(KEY_UNDO_SEND, 0);
    for (int allowed : UNDO_SEND_SECONDS) {
      if (allowed == value) {
        return value;
      }
    }
    return 0;
  }

  public void setUndoSendSeconds (int seconds) {
    int value = 0;
    for (int allowed : UNDO_SEND_SECONDS) {
      if (allowed == seconds) {
        value = seconds;
        break;
      }
    }
    putInt(KEY_UNDO_SEND, value);
  }

  /** Voice and video messages wait too (only while Undo send is on). */
  public boolean undoSendVoice () {
    return pmc().getBoolean(KEY_UNDO_SEND_VOICE, false);
  }

  public void setUndoSendVoice (boolean value) {
    put(KEY_UNDO_SEND_VOICE, value);
  }

  /** Not under "xgram_undo_send" either (see {@link #KEY_UNDO_SEND_VOICE}). */
  public static final String KEY_UNDO_SEND_BUBBLES = "xgram_undosend_bubbles";
  public static final String KEY_UNDO_SEND_NEWEST = "xgram_undosend_newest";

  /** Waiting messages also show in the chat, as faded bubbles under the newest message (on by default). */
  public boolean undoSendBubbles () {
    return pmc().getBoolean(KEY_UNDO_SEND_BUBBLES, true);
  }

  public void setUndoSendBubbles (boolean value) {
    put(KEY_UNDO_SEND_BUBBLES, value);
  }

  /** Only the newest message waits: a new one sends those that wait before it in that chat right away (off by default). */
  public boolean undoSendNewestOnly () {
    return pmc().getBoolean(KEY_UNDO_SEND_NEWEST, false);
  }

  public void setUndoSendNewestOnly (boolean value) {
    put(KEY_UNDO_SEND_NEWEST, value);
  }

  public static String undoSendExcludeKey (int accountId, long chatId) {
    return KEY_UNDO_SEND_EXCLUDE + accountId + "_" + chatId;
  }

  /** "Send right away in this chat": messages of this chat never wait. */
  public boolean isUndoSendExcluded (int accountId, long chatId) {
    return pmc().getBoolean(undoSendExcludeKey(accountId, chatId), false);
  }

  public void setUndoSendExcluded (int accountId, long chatId, boolean excluded) {
    if (excluded) {
      pmc().putBoolean(undoSendExcludeKey(accountId, chatId), true);
    } else {
      pmc().remove(undoSendExcludeKey(accountId, chatId));
    }
  }

  // Reaction bar of the message menu (Messages > Message menu, see com.fahadali.xgram.chats.XgramReactionBar)

  public static final String KEY_REACTION_BAR = "xgram_reaction_bar";
  /** The quick reactions at the top of the message menu, as in Telegram X. */
  public static final int REACTION_BAR_TOP = 0;
  /** The quick reactions under the options, just above Delete. */
  public static final int REACTION_BAR_BOTTOM = 1;
  /** No quick reactions: who reacted and who viewed stay at the top of the menu. */
  public static final int REACTION_BAR_OFF = 2;

  private volatile int reactionBar = -1; // -1: not read yet

  /** {@link #REACTION_BAR_TOP}, {@link #REACTION_BAR_BOTTOM} or {@link #REACTION_BAR_OFF}. */
  public int reactionBar () {
    int value = reactionBar;
    if (value < 0) {
      value = pmc().getInt(KEY_REACTION_BAR, REACTION_BAR_TOP);
      if (value != REACTION_BAR_BOTTOM && value != REACTION_BAR_OFF) {
        value = REACTION_BAR_TOP;
      }
      reactionBar = value;
    }
    return value;
  }

  public void setReactionBar (int value) {
    if (value != REACTION_BAR_BOTTOM && value != REACTION_BAR_OFF) {
      value = REACTION_BAR_TOP;
    }
    reactionBar = value;
    putInt(KEY_REACTION_BAR, value);
  }

  // Reactions in channels (Messages > Reactions): off hides the reactions of broadcast channel posts
  // (TGMessage.xgramHidesReactions), their quick reactions on a swipe and the message menu's reaction bar.

  public static final String KEY_CHANNEL_REACTIONS = "xgram_channel_reactions";

  private volatile int channelReactions = -1; // -1: not read yet. Cached: read whenever a message's reactions are set.

  /** Reactions under posts of broadcast channels, and reacting to them. On by default, as in Telegram X. */
  public boolean channelReactions () {
    int value = channelReactions;
    if (value < 0) {
      channelReactions = value = pmc().getBoolean(KEY_CHANNEL_REACTIONS, true) ? 1 : 0;
    }
    return value == 1;
  }

  /** Open chats follow at once: MessagesController listens for {@link #KEY_CHANNEL_REACTIONS}. */
  public void setChannelReactions (boolean value) {
    channelReactions = value ? 1 : 0;
    put(KEY_CHANNEL_REACTIONS, value);
  }

  // Notification icon (Appearance > Theme, next to App icon). Separate from the app icon.

  public static final String KEY_NOTIFICATION_ICON = "xgram_notification_icon";
  /** XGram's X glyph in the status bar. */
  public static final int NOTIFICATION_ICON_XGRAM = 0;
  /** Telegram X's original glyph in the status bar. */
  public static final int NOTIFICATION_ICON_TELEGRAM_X = 1;

  private volatile int notificationIcon = -1; // -1: not read yet (read on first use, while building a notification)

  /** {@link #NOTIFICATION_ICON_XGRAM} or {@link #NOTIFICATION_ICON_TELEGRAM_X}. */
  public int notificationIcon () {
    int value = notificationIcon;
    if (value < 0) {
      value = pmc().getInt(KEY_NOTIFICATION_ICON, NOTIFICATION_ICON_XGRAM) == NOTIFICATION_ICON_TELEGRAM_X ? NOTIFICATION_ICON_TELEGRAM_X : NOTIFICATION_ICON_XGRAM;
      notificationIcon = value;
    }
    return value;
  }

  public void setNotificationIcon (int value) {
    notificationIcon = value == NOTIFICATION_ICON_TELEGRAM_X ? NOTIFICATION_ICON_TELEGRAM_X : NOTIFICATION_ICON_XGRAM;
    putInt(KEY_NOTIFICATION_ICON, notificationIcon);
  }

  /**
   * Small icon of message notifications and their summary: the glyph picked in Notification icon. Read for every
   * notification built, so a change shows on the next one without a restart.
   */
  public static @DrawableRes int notificationSmallIcon () {
    return instance().notificationIcon() == NOTIFICATION_ICON_TELEGRAM_X ? R.mipmap.app_notification : R.drawable.xgram_notification;
  }

  // App font (Appearance > Theme, com.fahadali.xgram.appearance.XgramFonts). Not cached: XgramFonts reads it once per
  // process, as Telegram X keeps its paints and measured text for the whole process (a change restarts the app).

  public static final String KEY_APP_FONT = "xgram_app_font";

  /**
   * XgramFonts.FLEX (also with nothing stored: fresh installs and updates), XgramFonts.ROUNDED or XgramFonts.SYSTEM.
   * The saved choice; {@link com.fahadali.xgram.appearance.XgramFonts#current} is the one in use.
   */
  public int appFont () {
    int value = pmc().getInt(KEY_APP_FONT, com.fahadali.xgram.appearance.XgramFonts.FLEX);
    return value == com.fahadali.xgram.appearance.XgramFonts.ROUNDED || value == com.fahadali.xgram.appearance.XgramFonts.SYSTEM ? value : com.fahadali.xgram.appearance.XgramFonts.FLEX;
  }

  public void setAppFont (int value) {
    putInt(KEY_APP_FONT, value);
  }

  // Check In (Messages): the defaults of the start sheet. Read when used, not cached. Live Check Ins are not
  // settings: they are kept under "xgram_ci_" (com.fahadali.xgram.checkin), which no backup list matches.

  public static final String KEY_CHECKIN_TIMER = "xgram_checkin_timer";
  public static final String KEY_CHECKIN_GRACE = "xgram_checkin_grace";
  public static final String KEY_CHECKIN_RADIUS = "xgram_checkin_radius";
  public static final String KEY_CHECKIN_LOCATION = "xgram_checkin_location";
  public static final String KEY_CHECKIN_BATTERY = "xgram_checkin_battery";
  public static final String KEY_CHECKIN_FOLLOW = "xgram_checkin_follow";
  /** The one-time explainer was shown; not a setting, left out of backups. */
  private static final String KEY_CHECKIN_EXPLAINED = "xgram_checkin_explained";

  /** Default timer choices, minutes. */
  public static final int[] CHECK_IN_TIMERS = {15, 30, 60, 120, 240, 480};
  /** Alert delay choices, minutes. */
  public static final int[] CHECK_IN_GRACES = {5, 10, 15, 30};
  /** Arrival radius choices, meters. */
  public static final int[] CHECK_IN_RADII = {100, 200, 500};

  private static int checkInChoice (int value, int[] choices, int fallback) {
    for (int choice : choices) {
      if (choice == value) {
        return value;
      }
    }
    return fallback;
  }

  /** The start sheet's preselected timer, minutes. */
  public int checkInTimer () {
    return checkInChoice(pmc().getInt(KEY_CHECKIN_TIMER, 60), CHECK_IN_TIMERS, 60);
  }

  public void setCheckInTimer (int minutes) {
    putInt(KEY_CHECKIN_TIMER, checkInChoice(minutes, CHECK_IN_TIMERS, 60));
  }

  /** Alert delay: how long after the back-by time the alert is sent, minutes. */
  public int checkInGrace () {
    return checkInChoice(pmc().getInt(KEY_CHECKIN_GRACE, 15), CHECK_IN_GRACES, 15);
  }

  public void setCheckInGrace (int minutes) {
    putInt(KEY_CHECKIN_GRACE, checkInChoice(minutes, CHECK_IN_GRACES, 15));
  }

  /** How close to the destination counts as arrived, meters. */
  public int checkInRadius () {
    return checkInChoice(pmc().getInt(KEY_CHECKIN_RADIUS, 200), CHECK_IN_RADII, 200);
  }

  public void setCheckInRadius (int meters) {
    putInt(KEY_CHECKIN_RADIUS, checkInChoice(meters, CHECK_IN_RADII, 200));
  }

  public boolean checkInLocation () {
    return pmc().getBoolean(KEY_CHECKIN_LOCATION, true);
  }

  public void setCheckInLocation (boolean value) {
    put(KEY_CHECKIN_LOCATION, value);
  }

  public boolean checkInBattery () {
    return pmc().getBoolean(KEY_CHECKIN_BATTERY, true);
  }

  public void setCheckInBattery (boolean value) {
    put(KEY_CHECKIN_BATTERY, value);
  }

  /** Keep the sent alert updated: only applies while Include my location is on. */
  public boolean checkInFollowUp () {
    return pmc().getBoolean(KEY_CHECKIN_FOLLOW, true);
  }

  public void setCheckInFollowUp (boolean value) {
    put(KEY_CHECKIN_FOLLOW, value);
  }

  public boolean checkInExplained () {
    return pmc().getBoolean(KEY_CHECKIN_EXPLAINED, false);
  }

  public void setCheckInExplained () {
    pmc().putBoolean(KEY_CHECKIN_EXPLAINED, true);
  }

  // Side menu

  /** Side menu items that can be hidden (XgramDrawerItemsController.KEYS and TOGGLE_KEYS, all but settings), shown by default. */
  public boolean drawerItemHidden (String item) {
    return pmc().getBoolean("xgram_drawer_hide_" + item, false);
  }

  public void setDrawerItemHidden (String item, boolean hidden) {
    if (hidden) {
      pmc().putBoolean("xgram_drawer_hide_" + item, true);
    } else {
      pmc().remove("xgram_drawer_hide_" + item);
    }
  }

  // Proxy rules

  public boolean proxyOffOnVpn () {
    return proxyOffOnVpn;
  }

  public void setProxyOffOnVpn (boolean value) {
    proxyOffOnVpn = value;
    put(KEY_PROXY_OFF_VPN, value);
  }

  public boolean proxyOffOnWifi () {
    return proxyOffOnWifi;
  }

  public void setProxyOffOnWifi (boolean value) {
    proxyOffOnWifi = value;
    put(KEY_PROXY_OFF_WIFI, value);
  }

  public boolean proxyOffOnMobile () {
    return proxyOffOnMobile;
  }

  public void setProxyOffOnMobile (boolean value) {
    proxyOffOnMobile = value;
    put(KEY_PROXY_OFF_MOBILE, value);
  }

  // Layout

  /** Chat list and chat side by side on wide screens (unfolded foldables, tablets). */
  public boolean twoPaneLayout () {
    return twoPaneLayout;
  }

  /** Chat list: one row per Telegram community, like the official apps. */
  public boolean mergeCommunities () {
    return mergeCommunities;
  }

  public void setMergeCommunities (boolean value) {
    mergeCommunities = value;
    put(KEY_MERGE_COMMUNITIES, value);
  }

  /** App icon picker: choosing an icon also switches to its theme. */
  public boolean iconTheme () {
    return iconTheme;
  }

  public void setIconTheme (boolean value) {
    iconTheme = value;
    put(KEY_ICON_THEME, value);
  }

  /** Chat list without the floating new-chat button (new chats from the side menu). */
  public boolean hideFab () {
    return hideFab;
  }

  /** At the bottom of a channel or topic, pull up for the next unread one (official Telegram). */
  public boolean pullToNext () {
    return pullToNext;
  }

  public void setPullToNext (boolean value) {
    pullToNext = value;
    put(KEY_PULL_TO_NEXT, value);
  }

  public static final String KEY_CHAT_LIST_BIG_TEXT = "xgram_chat_list_big_text";
  private volatile int chatListBigText = -1;

  /**
   * Chat list view: three lines with bigger photos and text (Telegram X's "Three lines (bigger text)"), kept while
   * another view is picked. Until first set, whether the chat list uses it now.
   */
  public boolean chatListBigText () {
    if (chatListBigText == -1) {
      LevelDB pmc = pmc();
      chatListBigText = pmc.contains(KEY_CHAT_LIST_BIG_TEXT) ?
        (pmc.getBoolean(KEY_CHAT_LIST_BIG_TEXT, false) ? 1 : 0) :
        (Settings.instance().getChatListMode() == Settings.CHAT_MODE_3LINE_BIG ? 1 : 0);
    }
    return chatListBigText == 1;
  }

  public void setChatListBigText (boolean value) {
    if (chatListBigText != -1 && (chatListBigText == 1) == value && pmc().contains(KEY_CHAT_LIST_BIG_TEXT)) {
      return;
    }
    chatListBigText = value ? 1 : 0;
    put(KEY_CHAT_LIST_BIG_TEXT, value);
  }

  /** "Connecting..." in the status bar on the start side (off: Telegram X's centred position). The only thing that decides it. */
  public boolean connectionStatusAtStart () {
    return connectionStatusAtStart;
  }

  public void setConnectionStatusAtStart (boolean value) {
    connectionStatusAtStart = value;
    put(KEY_CONNECTION_STATUS_START, value);
  }

  /** First run only: a phone with a centred camera hole gets the start side as its default; once stored, the stored value rules. */
  public void presetConnectionStatusAtStart () {
    if (!connectionStatusAtStart && !pmc().contains(KEY_CONNECTION_STATUS_START)) {
      setConnectionStatusAtStart(true);
    }
  }

  public void setHideFab (boolean value) {
    hideFab = value;
    put(KEY_HIDE_FAB, value);
  }

  /**
   * Debug builds only (Settings &gt; XGram &gt; Developer): the Send button's long-press menu shows its test items (Show
   * toast, Send LTR emoji, and with Undo send the crash and draft restore tests). Off by default; release builds never
   * show them, whatever is stored.
   */
  public boolean sendMenuTests () {
    return sendMenuTests;
  }

  public void setSendMenuTests (boolean value) {
    sendMenuTests = value;
    put(KEY_SEND_MENU_TESTS, value);
  }

  /** The "All chats" tab shows its label like the other folders (off: Telegram X's bare icon when it's first). */
  public boolean allChatsLabel () {
    return allChatsLabel;
  }

  public void setAllChatsLabel (boolean value) {
    allChatsLabel = value;
    put(KEY_ALL_CHATS_LABEL, value);
  }

  /** Key of {@link #hideFolders} for one account. */
  public static String hideFoldersKey (int accountId) {
    return KEY_HIDE_FOLDERS + accountId;
  }

  /** No folder tabs in XGram for this account: only the main list shows. Nothing changes on the server, so other devices keep the folders. */
  public boolean hideFolders (int accountId) {
    return pmc().getBoolean(hideFoldersKey(accountId), false);
  }

  public void setHideFolders (int accountId, boolean value) {
    put(hideFoldersKey(accountId), value);
  }

  /** Key of {@link #hideAllChats} for one account. */
  public static String hideAllChatsKey (int accountId) {
    return KEY_HIDE_ALL_CHATS + accountId;
  }

  /**
   * No "All chats" tab in XGram for this account while another folder has a tab (XgramSettingDeps.allChatsHidden): the
   * first tab opens instead and keeps the archive row. Nothing changes on the server.
   */
  public boolean hideAllChats (int accountId) {
    return pmc().getBoolean(hideAllChatsKey(accountId), false);
  }

  public void setHideAllChats (int accountId, boolean value) {
    put(hideAllChatsKey(accountId), value);
  }

  public void setTwoPaneLayout (boolean value) {
    twoPaneLayout = value;
    put(KEY_TWO_PANE, value);
  }

  // Forum tabs

  /** Last tab opened in a forum with tabs: a forum topic id, or 0 for "All". */
  public int lastForumTab (int accountId, long chatId, int defaultValue) {
    return pmc().getInt("xgram_tab_" + accountId + "_" + chatId, defaultValue);
  }

  public void setLastForumTab (int accountId, long chatId, int forumTopicId) {
    pmc().putInt("xgram_tab_" + accountId + "_" + chatId, forumTopicId);
  }

  /** Forum tabs along the side of the messages instead of above them (the user's choice per chat). */
  public boolean forumTabsOnSide (int accountId, long chatId) {
    return pmc().getBoolean("xgram_tabs_side_" + accountId + "_" + chatId, false);
  }

  public void setForumTabsOnSide (int accountId, long chatId, boolean onSide) {
    pmc().putBoolean("xgram_tabs_side_" + accountId + "_" + chatId, onSide);
  }

  /** True if any ghost option is on. The drawer switch shows this, so a partly enabled ghost mode stays visible. */
  public boolean isAnyGhostEnabled () {
    return ghostDontRead || ghostDontType || ghostOffline;
  }

  public void setGhostModeEnabled (boolean enabled) {
    setGhostDontRead(enabled);
    setGhostDontType(enabled);
    setGhostOffline(enabled);
  }

  // Message history

  /** Keep a local copy of incoming messages so they stay readable after the sender deletes them. */
  public boolean historySaveDeleted () {
    return historySaveDeleted;
  }

  public void setHistorySaveDeleted (boolean value) {
    historySaveDeleted = value;
    put(KEY_HISTORY_SAVE_DELETED, value);
  }

  /** Keep previous versions of edited messages. */
  /** Copy the downloaded files of deleted messages, so photos and files stay viewable. */
  public boolean historySaveMedia () {
    return historySaveMedia;
  }

  public void setHistorySaveMedia (boolean value) {
    historySaveMedia = value;
    put(KEY_HISTORY_SAVE_MEDIA, value);
  }

  public boolean historySaveEdits () {
    return historySaveEdits;
  }

  public void setHistorySaveEdits (boolean value) {
    historySaveEdits = value;
    put(KEY_HISTORY_SAVE_EDITS, value);
  }

  /** Draw the text of messages kept after the sender deleted them in red, besides the trash icon. */
  public boolean historyRedDeleted () {
    return historyRedDeleted;
  }

  public void setHistoryRedDeleted (boolean value) {
    historyRedDeleted = value;
    put(KEY_HISTORY_RED_DELETED, value);
  }

  // Chats

  /** Don't load sponsored messages in channels. */
  public boolean hideSponsored () {
    return hideSponsored;
  }

  public void setHideSponsored (boolean value) {
    hideSponsored = value;
    put(KEY_HIDE_SPONSORED, value);
  }

  /** Show user, group and channel IDs in profiles (Telegram X's hidden "peer IDs" experiment). */
  public boolean showPeerIds () {
    return showPeerIds;
  }

  public void setShowPeerIds (boolean value) {
    showPeerIds = value;
    put(KEY_SHOW_PEER_IDS, value);
  }

  // Play videos in chat (Messages page, see com.fahadali.xgram.chats.XgramInlineVideo)

  public static final String KEY_INLINE_VIDEO = "xgram_inline_video";
  public static final String KEY_INLINE_VIDEO_START = "xgram_inline_video_start";
  public static final int INLINE_VIDEO_START_AUTO = 0;
  public static final int INLINE_VIDEO_START_TAP = 1;

  /** Set once Play videos in chat became on by default: it was off by default in the first builds that had it.
   *  A restored backup sets it too ({@link XgramBackup#importJson}), so the restored choice is kept. */
  public static final String KEY_INLINE_VIDEO_V2 = "xgram_inline_video_v2";

  private volatile boolean inlineVideo;
  private volatile int inlineVideoStart = -1; // -1: not read yet (read on first use, it's asked while drawing)

  private void loadInlineVideo () {
    if (inlineVideoStart == -1) {
      LevelDB pmc = pmc();
      if (!pmc.getBoolean(KEY_INLINE_VIDEO_V2, false)) {
        // One time: the switch is turned on, also where an earlier build saved it off. A choice made after this wins.
        put(KEY_INLINE_VIDEO, true);
        put(KEY_INLINE_VIDEO_V2, true);
      }
      inlineVideo = pmc.getBoolean(KEY_INLINE_VIDEO, true);
      inlineVideoStart = pmc.getInt(KEY_INLINE_VIDEO_START, INLINE_VIDEO_START_AUTO) == INLINE_VIDEO_START_TAP ? INLINE_VIDEO_START_TAP : INLINE_VIDEO_START_AUTO;
    }
  }

  /** Video messages play inside the chat instead of in the media viewer. */
  public boolean inlineVideo () {
    loadInlineVideo();
    return inlineVideo;
  }

  public void setInlineVideo (boolean value) {
    loadInlineVideo();
    inlineVideo = value;
    put(KEY_INLINE_VIDEO, value);
  }

  /** {@link #INLINE_VIDEO_START_AUTO} (muted, while on screen) or {@link #INLINE_VIDEO_START_TAP}. */
  public int inlineVideoStart () {
    loadInlineVideo();
    return inlineVideoStart;
  }

  public void setInlineVideoStart (int value) {
    loadInlineVideo();
    inlineVideoStart = value == INLINE_VIDEO_START_TAP ? INLINE_VIDEO_START_TAP : INLINE_VIDEO_START_AUTO;
    putInt(KEY_INLINE_VIDEO_START, inlineVideoStart);
  }

  // Hide controls while playing (Messages > Videos, see com.fahadali.xgram.appearance.XgramAutoHide). Read in the
  // constructor: asked while drawing the in-chat player.

  public static final String KEY_VIDEO_AUTO_HIDE = "xgram_video_auto_hide";

  private volatile boolean videoAutoHide;

  /**
   * While a video plays (the media viewer, in chat, the viewer's picture in picture), its title, caption and controls
   * hide after a few seconds without interaction, with the system bars. On by default.
   */
  public boolean videoAutoHide () {
    return videoAutoHide;
  }

  public void setVideoAutoHide (boolean value) {
    videoAutoHide = value;
    put(KEY_VIDEO_AUTO_HIDE, value);
  }

  // Foldables (Appearance > Layout, see com.fahadali.xgram.layout.XgramWindow). Read on first use: asked while
  // measuring the two panes.

  public static final String KEY_FOLD_HINGE_SPLIT = "xgram_fold_hinge_split";
  public static final String KEY_FOLD_TABLETOP = "xgram_fold_tabletop";

  private volatile Boolean foldHingeSplit, foldTabletop;

  /** Book posture (half open like a book): the two panes meet at the hinge. */
  public boolean foldHingeSplit () {
    Boolean value = foldHingeSplit;
    if (value == null) {
      foldHingeSplit = value = pmc().getBoolean(KEY_FOLD_HINGE_SPLIT, true);
    }
    return value;
  }

  public void setFoldHingeSplit (boolean value) {
    foldHingeSplit = value;
    put(KEY_FOLD_HINGE_SPLIT, value);
  }

  /** Tabletop posture (Flex mode): media, calls and the camera show above the fold and their controls below it. */
  public boolean foldTabletop () {
    Boolean value = foldTabletop;
    if (value == null) {
      foldTabletop = value = pmc().getBoolean(KEY_FOLD_TABLETOP, true);
    }
    return value;
  }

  public void setFoldTabletop (boolean value) {
    foldTabletop = value;
    put(KEY_FOLD_TABLETOP, value);
  }

  // Telegram X pages in Compose (Appearance, under Expressive design; see com.fahadali.xgram.compose.tgx). Off: every
  // Telegram X page keeps its classic list. Read when a page is opened.

  public static final String KEY_COMPOSE_TGX_PAGES = "xgram_compose_tgx";

  private volatile Boolean composeTgxPages;

  public boolean composeTgxPages () {
    Boolean value = composeTgxPages;
    if (value == null) {
      composeTgxPages = value = pmc().getBoolean(KEY_COMPOSE_TGX_PAGES, true);
    }
    return value;
  }

  public void setComposeTgxPages (boolean value) {
    composeTgxPages = value;
    put(KEY_COMPOSE_TGX_PAGES, value);
  }

  // Pure black (Appearance, under Expressive design; see com.fahadali.xgram.appearance.XgramAmoled): black Material You
  // surfaces in night mode. Off by default. Cached there too: read while colors are resolved.

  public static final String KEY_PURE_BLACK = "xgram_pure_black";

  private volatile Boolean pureBlack;

  public boolean pureBlack () {
    Boolean value = pureBlack;
    if (value == null) {
      pureBlack = value = pmc().getBoolean(KEY_PURE_BLACK, false);
    }
    return value;
  }

  public void setPureBlack (boolean value) {
    pureBlack = value;
    put(KEY_PURE_BLACK, value);
  }

  // Colours (Appearance, under Expressive design; see com.fahadali.xgram.appearance.XgramColorSource): where Expressive
  // design's colours come from, the wallpaper (the default) or a colour picked in XGram, and the style of the scheme
  // made from it. Cached: read while colours are resolved.

  public static final String KEY_COLOR_SOURCE = "xgram_colors_source", KEY_COLOR_SEED = "xgram_colors_seed", KEY_COLOR_STYLE = "xgram_colors_style";
  public static final int COLOR_SOURCE_WALLPAPER = 0, COLOR_SOURCE_CUSTOM = 1;
  public static final int COLOR_STYLE_TONAL_SPOT = 0, COLOR_STYLE_VIBRANT = 1, COLOR_STYLE_EXPRESSIVE = 2, COLOR_STYLE_NEUTRAL = 3;
  /** The colour before one is picked: the first swatch (Google blue). */
  public static final int COLOR_SEED_DEFAULT = 0xff4285f4;

  private volatile int colorSource = -1, colorStyle = -1;
  private volatile Integer colorSeed;

  /** {@link #COLOR_SOURCE_WALLPAPER} or {@link #COLOR_SOURCE_CUSTOM}. */
  public int colorSource () {
    int value = colorSource;
    if (value < 0) {
      colorSource = value = pmc().getInt(KEY_COLOR_SOURCE, COLOR_SOURCE_WALLPAPER) == COLOR_SOURCE_CUSTOM ? COLOR_SOURCE_CUSTOM : COLOR_SOURCE_WALLPAPER;
    }
    return value;
  }

  public void setColorSource (int source) {
    colorSource = source == COLOR_SOURCE_CUSTOM ? COLOR_SOURCE_CUSTOM : COLOR_SOURCE_WALLPAPER;
    putInt(KEY_COLOR_SOURCE, colorSource);
  }

  /** The picked colour (opaque ARGB): its hue is what the scheme is made from. */
  public int colorSeed () {
    Integer value = colorSeed;
    if (value == null) {
      colorSeed = value = pmc().getInt(KEY_COLOR_SEED, COLOR_SEED_DEFAULT) | 0xff000000;
    }
    return value;
  }

  public void setColorSeed (int argb) {
    colorSeed = argb | 0xff000000;
    putInt(KEY_COLOR_SEED, colorSeed);
  }

  /** COLOR_STYLE_*: Tonal spot (Android's default), Vibrant, Expressive or Neutral. */
  public int colorStyle () {
    int value = colorStyle;
    if (value < 0) {
      value = pmc().getInt(KEY_COLOR_STYLE, COLOR_STYLE_TONAL_SPOT);
      colorStyle = value = value >= COLOR_STYLE_TONAL_SPOT && value <= COLOR_STYLE_NEUTRAL ? value : COLOR_STYLE_TONAL_SPOT;
    }
    return value;
  }

  public void setColorStyle (int style) {
    colorStyle = style >= COLOR_STYLE_TONAL_SPOT && style <= COLOR_STYLE_NEUTRAL ? style : COLOR_STYLE_TONAL_SPOT;
    putInt(KEY_COLOR_STYLE, colorStyle);
  }

  // Two panes (Appearance > Layout, see com.fahadali.xgram.layout.XgramTwoPane): how wide the chat list pane is.
  // Read while measuring the panes, so cached.

  public static final String KEY_PANE_SPLIT_EVENLY = "xgram_split_evenly";
  public static final String KEY_PANE_RESIZABLE = "xgram_pane_drag";
  public static final String KEY_PANE_LIST_WIDTH = "xgram_pane_width";

  private volatile Boolean paneSplitEvenly, paneResizable;
  private volatile int paneListWidthDp = -1;

  /** The chat list and the chat get half the screen each, split at the hinge when the screen folds there. */
  public boolean paneSplitEvenly () {
    Boolean value = paneSplitEvenly;
    if (value == null) {
      paneSplitEvenly = value = pmc().getBoolean(KEY_PANE_SPLIT_EVENLY, false);
    }
    return value;
  }

  public void setPaneSplitEvenly (boolean value) {
    paneSplitEvenly = value;
    put(KEY_PANE_SPLIT_EVENLY, value);
  }

  /** The line between the panes has a handle that drags it; the width is kept. */
  public boolean paneResizable () {
    Boolean value = paneResizable;
    if (value == null) {
      paneResizable = value = pmc().getBoolean(KEY_PANE_RESIZABLE, true);
    }
    return value;
  }

  public void setPaneResizable (boolean value) {
    paneResizable = value;
    put(KEY_PANE_RESIZABLE, value);
  }

  // Navigation rail (Appearance > Layout, see com.fahadali.xgram.layout.XgramNavRail): Expressive design's rail on
  // wide windows. Read while the layout is checked, so cached.

  public static final String KEY_NAV_RAIL = "xgram_nav_rail";
  /** Prefix, followed by the account id: the date of the newest missed call seen (the rail's Calls badge). Local. */
  private static final String KEY_MISSED_CALLS_SEEN = "xgram_missed_seen_";

  private volatile Boolean navRail;

  /** On wide windows the chat list's Chats and Calls tabs and the side menu are a navigation rail at the start. */
  public boolean navRail () {
    Boolean value = navRail;
    if (value == null) {
      navRail = value = pmc().getBoolean(KEY_NAV_RAIL, true);
    }
    return value;
  }

  public void setNavRail (boolean value) {
    navRail = value;
    put(KEY_NAV_RAIL, value);
  }

  /** Unix time of the newest missed call already seen on account {@code accountId}, or 0 before the first look. */
  public int railCallsSeen (int accountId) {
    return pmc().getInt(KEY_MISSED_CALLS_SEEN + accountId, 0);
  }

  public void setRailCallsSeen (int accountId, int date) {
    pmc().putInt(KEY_MISSED_CALLS_SEEN + accountId, date);
  }

  // Navigation bar (Appearance > Layout, see com.fahadali.xgram.layout.XgramNavBar): the floating bar at the bottom of
  // the chat list on narrow windows (wide ones keep the rail). Read while the layout is checked, so cached.

  public static final String KEY_BOTTOM_NAV = "xgram_bottom_nav";

  private volatile Boolean bottomNav;

  /**
   * On narrow windows (a phone, a Fold's cover screen) the chat list's Chats and Calls tabs are a bar at the bottom. On
   * by default with Expressive design (the Stable after 0.29.1.1818.105): its floating toolbar is the Chats, Activity,
   * Settings and profile bar. Without it (Android 12 and older, or switched off) the default stays off, as the classic
   * bar has other tabs.
   */
  public boolean bottomNav () {
    Boolean value = bottomNav;
    if (value == null) {
      bottomNav = value = pmc().getBoolean(KEY_BOTTOM_NAV, com.fahadali.xgram.appearance.XgramExpressive.isEnabled());
    }
    return value;
  }

  /** Expressive design was switched on or off: the navigation bar's default follows it (the activity restarts next). */
  public void onExpressiveChanged () {
    bottomNav = null;
  }

  public void setBottomNav (boolean value) {
    bottomNav = value;
    put(KEY_BOTTOM_NAV, value);
  }

  // The floating navigation toolbar of Expressive design (com.fahadali.xgram.layout.XgramNavToolbar, Appearance >
  // Layout > Navigation bar): its tabs (Chats always), where the profile photo goes and its colours. The tabs' order
  // is XgramReorder.NAV_BAR. Read while the layout is checked, so cached.

  public static final String KEY_NAV_TABS = "xgram_nav_tabs", KEY_NAV_PROFILE = "xgram_nav_profile", KEY_NAV_STYLE = "xgram_nav_style";
  /** The photo: a button beside the bar, a tab in it (default), or not at all. */
  public static final int NAV_PROFILE_BUTTON = 0, NAV_PROFILE_TAB = 1, NAV_PROFILE_OFF = 2;
  /** The bar's colours: Standard (surface container, default) or Vibrant (primary container). */
  public static final int NAV_STYLE_STANDARD = 0, NAV_STYLE_VIBRANT = 1;
  /**
   * Tabs by bit of XgramNavToolbar.DEST_* (Chats 0, Calls 1, Contacts 2, Saved 3, Settings 4, Activity 6; the profile
   * has its own setting): XGram's set, Chats, Activity and Settings (with the profile, a tab by default, in the order
   * of XgramNavToolbar.DEFAULT_ORDER). Activity's bit counts only while Activity is on.
   */
  public static final int NAV_TABS_DEFAULT = (1 << 0) | (1 << 4) | (1 << 6);

  private volatile Integer navTabs, navProfile, navStyle;

  /** The destinations that are tabs, a bit each (Chats always set). */
  public int navTabs () {
    Integer value = navTabs;
    if (value == null) {
      navTabs = value = pmc().getInt(KEY_NAV_TABS, NAV_TABS_DEFAULT) | 1;
    }
    return value;
  }

  public void setNavTabOn (int dest, boolean on) {
    if (dest <= 0 || dest == 5 || dest > 6) {
      return; // Chats is always a tab; the profile (5) has its own setting; 6 is Activity
    }
    int value = on ? navTabs() | (1 << dest) : navTabs() & ~(1 << dest);
    if (!pmc().contains(KEY_NAV_PROFILE)) {
      pmc().putInt(KEY_NAV_PROFILE, navProfile()); // its default follows whether the tabs were chosen: keep the one in use
    }
    navTabs = value;
    putInt(KEY_NAV_TABS, value);
  }

  public int navProfile () {
    Integer value = navProfile;
    if (value == null) {
      int stored = pmc().getInt(KEY_NAV_PROFILE, -1);
      if (stored < NAV_PROFILE_BUTTON || stored > NAV_PROFILE_OFF) {
        // Never chosen: a tab, unless the user set up their own tabs while the photo was beside the bar by default
        // (with 4 of them the photo would have no room and be gone)
        stored = pmc().contains(KEY_NAV_TABS) ? NAV_PROFILE_BUTTON : NAV_PROFILE_TAB;
      }
      navProfile = value = stored;
    }
    return value;
  }

  public void setNavProfile (int mode) {
    navProfile = mode;
    putInt(KEY_NAV_PROFILE, mode);
  }

  public int navStyle () {
    Integer value = navStyle;
    if (value == null) {
      navStyle = value = pmc().getInt(KEY_NAV_STYLE, NAV_STYLE_STANDARD) == NAV_STYLE_VIBRANT ? NAV_STYLE_VIBRANT : NAV_STYLE_STANDARD;
    }
    return value;
  }

  public void setNavStyle (int style) {
    navStyle = style;
    putInt(KEY_NAV_STYLE, style);
  }

  // With the floating navigation toolbar: where the chat list's compose button goes, and whether the side menu (its ☰
  // and edge swipe) stays. Both only apply while the toolbar is shown.

  public static final String KEY_NAV_FAB = "xgram_nav_fab", KEY_NAV_HIDE_DRAWER = "xgram_nav_hide_drawer";
  /**
   * The compose button: at the window's end edge whatever the bar's width, staying there while the bar hides (default);
   * or end-aligned with the bar and its photo, hiding with them.
   */
  public static final int NAV_FAB_SIDE = 0, NAV_FAB_WITH_BAR = 1;

  private volatile Integer navFab;
  private volatile Boolean navHideDrawer;

  public int navFab () {
    Integer value = navFab;
    if (value == null) {
      navFab = value = pmc().getInt(KEY_NAV_FAB, NAV_FAB_SIDE) == NAV_FAB_WITH_BAR ? NAV_FAB_WITH_BAR : NAV_FAB_SIDE;
    }
    return value;
  }

  public void setNavFab (int mode) {
    navFab = mode;
    putInt(KEY_NAV_FAB, mode);
  }

  /** No side menu (no ☰, no edge swipe) while the toolbar is shown: its items are in the chat list's ⋮ menu. */
  public boolean navHideDrawer () {
    Boolean value = navHideDrawer;
    if (value == null) {
      navHideDrawer = value = pmc().getBoolean(KEY_NAV_HIDE_DRAWER, false);
    }
    return value;
  }

  public void setNavHideDrawer (boolean value) {
    navHideDrawer = value;
    put(KEY_NAV_HIDE_DRAWER, value);
  }

  /**
   * XGram's layout: Chats, Activity, Settings and the profile as tabs in that order, Standard colours, the compose
   * button at the side and the side menu.
   */
  public void resetNavBar () {
    XgramReorder.reset(XgramReorder.NAV_BAR);
    navTabs = NAV_TABS_DEFAULT;
    navProfile = NAV_PROFILE_TAB;
    navStyle = NAV_STYLE_STANDARD;
    navFab = NAV_FAB_SIDE;
    navHideDrawer = false;
    putInt(KEY_NAV_TABS, NAV_TABS_DEFAULT);
    putInt(KEY_NAV_PROFILE, NAV_PROFILE_TAB);
    putInt(KEY_NAV_STYLE, NAV_STYLE_STANDARD);
    putInt(KEY_NAV_FAB, NAV_FAB_SIDE);
    put(KEY_NAV_HIDE_DRAWER, false);
  }

  // Activity (Chat list > Activity, com.fahadali.xgram.activity): every mention, reply and reaction in one place.
  // Read by the TDLib thread for every new message, so cached. The items themselves are in the activity's own store.

  public static final String KEY_ACTIVITY_ON = "xgram_activity_on", KEY_ACTIVITY_REACTIONS = "xgram_activity_reactions",
    KEY_ACTIVITY_MUTED = "xgram_activity_muted", KEY_ACTIVITY_DISCUSSION = "xgram_activity_discussion",
    KEY_ACTIVITY_LOOKBACK = "xgram_activity_lookback", KEY_ACTIVITY_BADGE = "xgram_activity_badge",
    KEY_ACTIVITY_HEADER = "xgram_activity_header", KEY_ACTIVITY_BAR = "xgram_activity_bar";
  /** The classic bottom bar (XgramNavBar): no Activity (default), or Activity in Calls' place. */
  public static final int ACTIVITY_BAR_OFF = 0, ACTIVITY_BAR_CALLS = 1;
  /** Prefix of every Activity setting (listeners follow them all). */
  public static final String KEY_ACTIVITY_PREFIX = "xgram_activity_";

  private volatile Boolean activityOn, activityReactions, activityMuted, activityDiscussion, activityLookBack, activityBadge, activityHeader;

  private Boolean readBoolean (String key, boolean defaultValue) {
    return pmc().getBoolean(key, defaultValue);
  }

  /** Activity is collected, synced and shown. Off: nothing is collected, every way in is hidden, saved items stay. */
  public boolean activityOn () {
    Boolean value = activityOn;
    if (value == null) {
      activityOn = value = readBoolean(KEY_ACTIVITY_ON, true);
    }
    return value;
  }

  public void setActivityOn (boolean value) {
    activityOn = value;
    put(KEY_ACTIVITY_ON, value);
  }

  /** Reactions to your messages are collected and shown. */
  public boolean activityReactions () {
    Boolean value = activityReactions;
    if (value == null) {
      activityReactions = value = readBoolean(KEY_ACTIVITY_REACTIONS, true);
    }
    return value;
  }

  public void setActivityReactions (boolean value) {
    activityReactions = value;
    put(KEY_ACTIVITY_REACTIONS, value);
  }

  /** Also from muted chats (mentions that still notify you always show). Applied when shown, not when collected. */
  public boolean activityMuted () {
    Boolean value = activityMuted;
    if (value == null) {
      activityMuted = value = readBoolean(KEY_ACTIVITY_MUTED, false);
    }
    return value;
  }

  public void setActivityMuted (boolean value) {
    activityMuted = value;
    put(KEY_ACTIVITY_MUTED, value);
  }

  /** Channels' discussion groups and the Replies chat. Applied when shown. */
  public boolean activityDiscussion () {
    Boolean value = activityDiscussion;
    if (value == null) {
      activityDiscussion = value = readBoolean(KEY_ACTIVITY_DISCUSSION, true);
    }
    return value;
  }

  public void setActivityDiscussion (boolean value) {
    activityDiscussion = value;
    put(KEY_ACTIVITY_DISCUSSION, value);
  }

  /** Once, a bounded search for the last month's mentions in the most active groups. */
  public boolean activityLookBack () {
    Boolean value = activityLookBack;
    if (value == null) {
      activityLookBack = value = readBoolean(KEY_ACTIVITY_LOOKBACK, true);
    }
    return value;
  }

  public void setActivityLookBack (boolean value) {
    activityLookBack = value;
    put(KEY_ACTIVITY_LOOKBACK, value);
  }

  /** A count of new items on every way into Activity. */
  public boolean activityBadge () {
    Boolean value = activityBadge;
    if (value == null) {
      activityBadge = value = readBoolean(KEY_ACTIVITY_BADGE, true);
    }
    return value;
  }

  public void setActivityBadge (boolean value) {
    activityBadge = value;
    put(KEY_ACTIVITY_BADGE, value);
  }

  /** A bell in the chat list's header while the navigation has no Activity. */
  public boolean activityHeader () {
    Boolean value = activityHeader;
    if (value == null) {
      activityHeader = value = readBoolean(KEY_ACTIVITY_HEADER, true);
    }
    return value;
  }

  public void setActivityHeader (boolean value) {
    activityHeader = value;
    put(KEY_ACTIVITY_HEADER, value);
  }

  private volatile Integer activityBar;

  /** Activity on the classic bottom bar: ACTIVITY_BAR_OFF (default) or ACTIVITY_BAR_CALLS (in Calls' place). */
  public int activityBar () {
    Integer value = activityBar;
    if (value == null) {
      activityBar = value = pmc().getInt(KEY_ACTIVITY_BAR, ACTIVITY_BAR_OFF) == ACTIVITY_BAR_CALLS ? ACTIVITY_BAR_CALLS : ACTIVITY_BAR_OFF;
    }
    return value;
  }

  public void setActivityBar (int mode) {
    int value = mode == ACTIVITY_BAR_CALLS ? ACTIVITY_BAR_CALLS : ACTIVITY_BAR_OFF;
    activityBar = value;
    putInt(KEY_ACTIVITY_BAR, value);
  }

  // Settings search: the results opened last (search hit keys, newest first), shown before anything is typed.
  // Not a setting: left out of backups.

  private static final String KEY_SEARCH_RECENT = "xgram_search_recent";
  private static final int SEARCH_RECENT_MAX = 8;

  public java.util.List<String> searchRecents () {
    String value = pmc().getString(KEY_SEARCH_RECENT, "");
    java.util.List<String> list = new java.util.ArrayList<>();
    if (value != null && !value.isEmpty()) {
      for (String key : value.split("\n")) {
        if (!key.isEmpty()) {
          list.add(key);
        }
      }
    }
    return list;
  }

  public void addSearchRecent (String key) {
    if (key == null || key.isEmpty() || key.indexOf('\n') >= 0) {
      return;
    }
    java.util.List<String> list = searchRecents();
    list.remove(key);
    list.add(0, key);
    while (list.size() > SEARCH_RECENT_MAX) {
      list.remove(list.size() - 1);
    }
    pmc().putString(KEY_SEARCH_RECENT, android.text.TextUtils.join("\n", list));
  }

  public void clearSearchRecents () {
    pmc().remove(KEY_SEARCH_RECENT);
  }

  /** The chat list pane width (dp) last set by dragging, or 0: the usual width. */
  public int paneListWidthDp () {
    int value = paneListWidthDp;
    if (value < 0) {
      paneListWidthDp = value = Math.max(0, pmc().getInt(KEY_PANE_LIST_WIDTH, 0));
    }
    return value;
  }

  public void setPaneListWidthDp (int value) {
    paneListWidthDp = value = Math.max(0, value);
    if (value == 0) {
      pmc().remove(KEY_PANE_LIST_WIDTH);
      for (Listener listener : listeners) {
        listener.onXgramSettingChanged(KEY_PANE_LIST_WIDTH);
      }
    } else {
      putInt(KEY_PANE_LIST_WIDTH, value);
    }
  }
}
