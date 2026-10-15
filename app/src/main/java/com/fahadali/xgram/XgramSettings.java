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
  public static final String KEY_CONNECTION_STATUS_START = "xgram_connection_status_start";
  public static final String KEY_ALL_CHATS_LABEL = "xgram_all_chats_label";
  /** Prefix: followed by the account id (see {@link #hideFolders}). */
  public static final String KEY_HIDE_FOLDERS = "xgram_hide_folders_";
  public static final String KEY_PULL_TO_NEXT = "xgram_pull_to_next";
  public static final String KEY_DOWNLOAD_BOOST = "xgram_download_boost_on";
  public static final String KEY_DOWNLOAD_BOOST_LEVEL = "xgram_download_boost_level";
  public static final String KEY_TAP_TO_RECORD = "xgram_tap_to_record";
  public static final String KEY_SYSTEM_EMOJI = "xgram_system_emoji";
  /** Prefix of the message box buttons the user hid, followed by one of the {@code INPUT_BUTTON_*} names. */
  public static final String KEY_INPUT_BUTTON_HIDDEN = "xgram_input_hide_";

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
  private volatile boolean aiTools, hideFab, pullToNext, allChatsLabel, connectionStatusAtStart;
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

  // Cached: read on every message layout.
  private volatile int stickerScale = -1, emojiScale = -1, bubbleRadius = -2, bubbleMergeRadius = -2, bubbleTail = -1;

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

  // Side menu

  /** Side menu items that can be hidden: contacts, hub, calls, saved, invite, proxy, help, night, ghost. */
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

  private volatile boolean inlineVideo;
  private volatile int inlineVideoStart = -1; // -1: not read yet (read on first use, it's asked while drawing)

  private void loadInlineVideo () {
    if (inlineVideoStart == -1) {
      LevelDB pmc = pmc();
      inlineVideo = pmc.getBoolean(KEY_INLINE_VIDEO, false);
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
}
