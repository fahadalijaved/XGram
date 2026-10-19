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
package com.fahadali.xgram.search

import android.os.Build
import com.fahadali.xgram.XgramSettingsController
import com.fahadali.xgram.compose.XgramSettingsPages
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.R
import org.thunderdog.challegram.config.Config
import org.thunderdog.challegram.config.Device
import org.thunderdog.challegram.core.BiometricAuthentication
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.theme.Theme
import org.thunderdog.challegram.theme.ThemeInfo
import org.thunderdog.challegram.theme.ThemeManager
import org.thunderdog.challegram.ui.PasscodeSetupController
import org.thunderdog.challegram.unsorted.Passcode
import org.thunderdog.challegram.unsorted.Settings

/**
 * The Telegram X part of the settings search: every Telegram X settings page and the rows people look for, written
 * out by hand. Every title, icon, id and keyword is an R reference, so a renamed or removed string or row breaks the
 * build instead of the search.
 *
 * Rule: a new Telegram X row that a user can look for gets a line here (or its id goes in [IGNORED]).
 * scripts/audit_settings_search.py lists rows of the indexed pages that are in neither; debug builds log rows that a
 * result could not find (XgramSearchHighlight).
 *
 * A page is opened the way its parent page opens it (TgxSearchOpen). Building the index sends nothing to TDLib.
 */
internal object TgxSearchIndex {
  val pages = ArrayList<TgxPage>()
  val rows = ArrayList<TgxRow>()

  /**
   * Rows of the indexed pages left out on purpose: per-chat or per-user lists, radio options of dialogs, rows that need
   * the storage or the network statistics, debug-only, crash screen and source code rows, suggestions, edit fields, rows
   * whose result opens their page directly (XGram, XGram privacy, Push services). Ids starting with btn_secret_,
   * btn_test_, btn_tdlib_ (debug sections) and btn_customChat_ (one chat's notifications) are left out as a whole.
   */
  @JvmField
  val IGNORED: IntArray = intArrayOf(
    R.id.btn_suggestion, R.id.btn_sourceCodeChanges, R.id.btn_tdlib, R.id.btn_session, R.id.btn_currentSession,
    R.id.btn_showMore, R.id.btn_calls_vibrate, R.id.btn_calls_ringtone,
    R.id.btn_showOtherChats, R.id.btn_storagePath, R.id.btn_testingUtils,
    R.id.btn_xgramPushRegisterAgain, R.id.btn_alwaysAllow, R.id.btn_neverAllow, R.id.btn_chatDoNotTranslateAppLang,
    R.id.btn_chatDoNotTranslateSelected, R.id.btn_appLogs, R.id.language, R.id.btn_xgramSettings, R.id.btn_xgramTgxPrivacy,
    R.id.btn_pushService, R.id.btn_experiment, R.id.btn_launchApp, R.id.btn_update, R.id.btn_showError, R.id.btn_shareError,
    R.id.btn_eraseDatabase, R.id.btn_changePhoneNumber, R.id.btn_logout, R.id.btn_checkUpdates, R.id.btn_dataUsageOther,
    R.id.btn_autoNightModeScheduled_timeOn, R.id.btn_autoNightModeScheduled_timeOff, R.id.btn_autoNightModeScheduled_location
  )

  private val ALWAYS: (SearchEnv) -> Boolean = { true }
  private val NONE = IntArray(0)

  private fun kw(vararg res: Int): IntArray = res

  private fun page(
    key: String,
    title: Int,
    icon: Int,
    parent: TgxPage?,
    parentRow: RowRef?,
    sentence: Int = 0,
    kw: IntArray = NONE,
    visible: (SearchEnv) -> Boolean = ALWAYS,
    open: (OpenCtx) -> Opened
  ): TgxPage {
    val p = TgxPage(key, title, sentence, icon, parent, parentRow, kw, visible, open)
    pages.add(p)
    return p
  }

  /** A row of [page] that opens [page] and is pointed at; key "t:" + [key]. */
  private fun row(
    page: TgxPage,
    key: String,
    title: Int,
    row: RowRef?,
    icon: Int = 0,
    group: Int = 0,
    sentence: Int = 0,
    kw: IntArray = NONE,
    visible: (SearchEnv) -> Boolean = ALWAYS,
    soft: Boolean = false,
    open: ((OpenCtx) -> Opened)? = null,
    toggle: InlineToggle? = null,
    titleText: (() -> String)? = null
  ) {
    rows.add(TgxRow(page, "t:$key", title, sentence, row, icon, group, kw, visible, soft, null, open, toggle, titleText))
  }

  /** A page, listed on [on] (its path): it opens [target]; key "t:" + [key]. */
  private fun entry(
    on: TgxPage,
    key: String,
    target: TgxPage,
    title: Int = target.title,
    sentence: Int = target.sentence,
    icon: Int = target.icon,
    group: Int = 0,
    kw: IntArray = target.kw,
    visible: (SearchEnv) -> Boolean = target.visible
  ) {
    rows.add(TgxRow(on, "t:$key", title, sentence, null, icon, group, kw, visible, false, target, null, null, null))
  }

  private fun push(make: (OpenCtx) -> org.thunderdog.challegram.navigation.ViewController<*>): (OpenCtx) -> Opened =
    { ctx -> Opened.Push(make(ctx)) }

  // Pages

  private lateinit var settings: TgxPage
  private lateinit var devices: TgxPage
  private lateinit var websites: TgxPage
  private lateinit var notifications: TgxPage
  private lateinit var archive: TgxPage
  private lateinit var data: TgxPage
  private lateinit var storage: TgxPage
  private lateinit var networkUsage: TgxPage
  private lateinit var proxy: TgxPage
  private lateinit var privacy: TgxPage
  private lateinit var blocked: TgxPage
  private lateinit var passcode: TgxPage
  private lateinit var themes: TgxPage
  private lateinit var tweaks: TgxPage
  private lateinit var stickers: TgxPage
  private lateinit var language: TgxPage
  private lateinit var translation: TgxPage
  private lateinit var folders: TgxPage
  private lateinit var logout: TgxPage
  private lateinit var appLogs: TgxPage

  /** Notifications of private chats, groups and channels. */
  private val scopes = arrayOfNulls<TgxPage>(3)

  /** The privacy rule pages, in the privacy page's order, with the key part of each. */
  private val ruleKeys = arrayOf("status", "photo", "audio", "birthdate", "bio", "phone", "findByPhone", "forwards", "invites", "voice", "calls", "p2p")
  private val rulePages = arrayOfNulls<TgxPage>(ruleKeys.size)

  init {
    definePages()
    settingsRows()
    devicesRows()
    notificationRows()
    dataRows()
    privacyRows()
    passcodeRows()
    themeRows()
    interfaceRows()
    stickerRows()
    languageRows()
    folderRows()
    accountRows()
  }

  private fun definePages() {
    settings = page("settings", R.string.Settings, R.drawable.baseline_settings_24, null, null) { Opened.MainSettings }
    devices = page(
      "devices", R.string.Devices, R.drawable.baseline_devices_other_24, settings, RowRef(R.id.btn_devices),
      kw = kw(R.string.XgramSearchKwSessions), open = push { TgxSearchOpen.sessions(it.context, it.tdlib) }
    )
    notifications = page(
      "notif", R.string.Notifications, R.drawable.baseline_notifications_24, settings, RowRef(R.id.btn_notificationSettings),
      kw = kw(R.string.XgramSearchKwNotify), open = push { TgxSearchOpen.notifications(it.context, it.tdlib) }
    )
    val scopeTitles = intArrayOf(R.string.PrivateChats, R.string.Groups, R.string.Channels)
    val scopeIcons = intArrayOf(R.drawable.baseline_person_24, R.drawable.baseline_group_24, R.drawable.baseline_bullhorn_24)
    val scopeKeys = arrayOf("private", "groups", "channels")
    for (i in 0 until 3) {
      val which = i
      scopes[i] = page(
        "notif/" + scopeKeys[i], scopeTitles[i], scopeIcons[i], notifications,
        RowRef(R.id.btn_notifications_snooze, data = { TgxSearchOpen.isScope(it, which) }),
        kw = kw(R.string.XgramSearchKwNotify), open = push { TgxSearchOpen.notificationScope(it.context, it.tdlib, which) }
      )
    }
    archive = page(
      "archive", R.string.ArchiveSettings, R.drawable.baseline_archive_24, notifications, RowRef(R.id.btn_archiveSettings),
      kw = kw(R.string.XgramSearchKwArchive), open = push { TgxSearchOpen.archiveSettings(it.context, it.tdlib) }
    )
    data = page(
      "data", R.string.DataSettings, R.drawable.baseline_data_usage_24, settings, RowRef(R.id.btn_chatSettings),
      kw = kw(R.string.XgramSearchKwDataUsage, R.string.XgramSearchKwStorage), open = push { TgxSearchOpen.data(it.context, it.tdlib) }
    )
    storage = page(
      "storage", R.string.StorageUsage, R.drawable.baseline_storage_24, data, RowRef(R.id.btn_storageUsage),
      kw = kw(R.string.XgramSearchKwStorage), open = push { TgxSearchOpen.storage(it.context, it.tdlib) }
    )
    networkUsage = page(
      "net", R.string.NetworkUsage, R.drawable.baseline_import_export_24, data, RowRef(R.id.btn_dataUsageTotal),
      kw = kw(R.string.XgramSearchKwDataUsage), open = push { TgxSearchOpen.networkUsage(it.context, it.tdlib) }
    )
    proxy = page(
      "proxy", R.string.Proxy, R.drawable.baseline_security_24, data, RowRef(R.id.btn_proxy),
      kw = kw(R.string.XgramSearchKwProxy), open = push { TgxSearchOpen.proxy(it.context, it.tdlib) }
    )
    privacy = page(
      "privacy", R.string.PrivacySettings, R.drawable.baseline_lock_24, settings, RowRef(R.id.btn_privacySettings),
      kw = kw(R.string.XgramSearchKwSecurity, R.string.XgramSearchKwPrivacyRule), open = push { TgxSearchOpen.privacy(it.context, it.tdlib) }
    )
    websites = page(
      "websites", R.string.WebSessionsTitle, R.drawable.baseline_public_24, privacy, RowRef(R.id.btn_sessions),
      kw = kw(R.string.XgramSearchKwSessions, R.string.XgramSearchKwWebsites), open = push { TgxSearchOpen.websites(it.context, it.tdlib) }
    )
    blocked = page(
      "blocked", R.string.BlockedSenders, R.drawable.baseline_remove_circle_24, privacy, RowRef(R.id.btn_blockedSenders),
      kw = kw(R.string.XgramSearchKwBlocked), open = push { TgxSearchOpen.blocked(it.context, it.tdlib) }
    )
    val rules = TgxSearchOpen.privacyRules()
    for (i in rules.indices) {
      val setting = rules[i]
      val extra = when (setting.getConstructor()) {
        TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR -> R.string.XgramSearchKwLastSeen
        TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR, TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR -> R.string.XgramSearchKwPhone
        TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR, TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR -> R.string.XgramSearchKwCalls
        TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR -> R.string.XgramSearchKwBirthday
        TdApi.UserPrivacySettingShowBio.CONSTRUCTOR -> R.string.XgramSearchKwBio
        TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR -> R.string.XgramSearchKwProfile
        else -> 0
      }
      rulePages[i] = page(
        "rule/" + ruleKeys[i], TgxSearchOpen.privacyRuleName(setting), TgxSearchOpen.privacyRuleIcon(setting), privacy,
        RowRef(R.id.btn_privacyRule, longId = setting.getConstructor().toLong()),
        kw = if (extra != 0) kw(R.string.XgramSearchKwPrivacyRule, extra) else kw(R.string.XgramSearchKwPrivacyRule)
      ) { ctx ->
        // Voice and video messages without Premium: Telegram X's Premium alert on the privacy page's row instead.
        val c = TgxSearchOpen.privacyRule(ctx.context, ctx.tdlib, setting)
        if (c != null) Opened.Push(c) else Opened.Parent
      }
    }
    passcode = page(
      "passcode", R.string.PasscodeTitle, R.drawable.baseline_lock_24, privacy, RowRef(R.id.btn_passcode),
      sentence = R.string.XgramR6B8PasscodeLock, kw = kw(R.string.XgramSearchKwPasscode)
    ) { ctx ->
      // With a passcode set, the unlock screen comes first: the row is pointed at once the settings show.
      Opened.Gated({ it is PasscodeSetupController }) { TgxSearchOpen.openPasscode(ctx.from) }
    }
    themes = page(
      "themes", R.string.ThemeSettings, R.drawable.baseline_palette_24, settings, RowRef(R.id.btn_themeSettings),
      kw = kw(R.string.XgramSearchKwChat, R.string.XgramSearchKwDark, R.string.XgramSearchKwWallpaper), open = push { TgxSearchOpen.themes(it.context, it.tdlib) }
    )
    tweaks = page(
      "ui", R.string.TweakSettings, R.drawable.baseline_extension_24, settings, RowRef(R.id.btn_tweakSettings),
      kw = kw(R.string.XgramSearchKwInterface), open = push { TgxSearchOpen.interfaceOptions(it.context, it.tdlib) }
    )
    stickers = page(
      "stickers", R.string.StickersAndEmoji, R.drawable.deproko_baseline_stickers_filled_24, settings, RowRef(R.id.btn_stickerSettingsAndEmoji),
      kw = kw(R.string.XgramSearchKwStickers, R.string.XgramSearchKwEmoji, R.string.XgramSearchKwReactions)
    ) { ctx ->
      // The page reads the sticker set counts from the main Settings screen: without one, Settings opens instead.
      val c = TgxSearchOpen.stickers(ctx.context, ctx.tdlib, ctx.settings())
      if (c != null) Opened.Push(c) else Opened.Parent
    }
    language = page(
      "language", R.string.Language, R.drawable.baseline_language_24, settings, RowRef(R.id.btn_languageSettings),
      kw = kw(R.string.XgramSearchKwLanguage), open = push { TgxSearchOpen.language(it.context, it.tdlib) }
    )
    translation = page(
      "translate", R.string.Translation, R.drawable.baseline_translate_24, language, RowRef(R.id.btn_chatTranslateStyle),
      kw = kw(R.string.XgramSearchKwTranslate), open = push { TgxSearchOpen.translation(it.context, it.tdlib) }
    )
    folders = page(
      "folders", R.string.ChatFolders, R.drawable.baseline_folder_copy_24, settings, RowRef(R.id.btn_chatFolders),
      kw = kw(R.string.XgramSearchKwFolders), visible = { Settings.instance().chatFoldersEnabled() },
      open = push { TgxSearchOpen.folders(it.context, it.tdlib) }
    )
    logout = page(
      "logout", R.string.LogOut, R.drawable.baseline_logout_24, settings, null,
      kw = kw(R.string.XgramSearchKwAccount, R.string.XgramSearchKwAccounts), open = push { TgxSearchOpen.logOut(it.context, it.tdlib) }
    )
    appLogs = page(
      "logs", R.string.AppLogs, R.drawable.baseline_build_24, settings, RowRef(R.id.btn_build),
      kw = kw(R.string.XgramSearchKwDebug, R.string.XgramSearchKwVersion), open = push { TgxSearchOpen.appLogs(it.context, it.tdlib) }
    )
  }

  private fun settingsRows() {
    val s = settings
    row(s, "settings/userId", R.string.UserId, RowRef(R.id.btn_peer_id), R.drawable.baseline_identifier_24,
      kw = kw(R.string.XgramSearchKwProfile), visible = { Settings.instance().showPeerIds() })
    row(s, "settings/username", R.string.Username, null, R.drawable.baseline_alternate_email_24,
      kw = kw(R.string.XgramSearchKwUsername), open = push { TgxSearchOpen.editUsername(it.context, it.tdlib) })
    row(s, "settings/phone", R.string.Phone, RowRef(R.id.btn_phone), R.drawable.baseline_phone_24, kw = kw(R.string.XgramSearchKwPhone))
    row(s, "settings/changeNumber", R.string.PhoneNumberChange, null, R.drawable.baseline_sim_card_24,
      kw = kw(R.string.XgramSearchKwPhone), open = push { TgxSearchOpen.changeNumber(it.context, it.tdlib) })
    row(s, "settings/birthday", R.string.Birthdate, RowRef(R.id.btn_birthdate), R.drawable.baseline_cake_variant_24,
      kw = kw(R.string.XgramSearchKwBirthday), visible = { it.tdlib.myUserFull()?.birthdate != null })
    row(s, "settings/bio", R.string.UserBio, null, R.drawable.baseline_info_24,
      kw = kw(R.string.XgramSearchKwBio), open = push { TgxSearchOpen.editBio(it.context, it.tdlib) })
    row(s, "settings/name", R.string.EditName, null, R.drawable.baseline_edit_24,
      kw = kw(R.string.XgramSearchKwProfile), open = push { TgxSearchOpen.editName(it.context, it.tdlib) })
    entry(s, "settings/devices", devices)
    row(s, "settings/xgram", R.string.XgramSettings, null, R.drawable.baseline_stars_24, kw = kw(R.string.XgramSearchKwXgram),
      open = push { XgramSettingsPages.main(it.context, it.tdlib) })
    entry(s, "settings/notifications", notifications)
    entry(s, "settings/data", data)
    entry(s, "settings/privacy", privacy)
    entry(s, "settings/stickers", stickers)
    entry(s, "settings/themes", themes)
    entry(s, "settings/interface", tweaks)
    entry(s, "settings/folders", folders)
    entry(s, "settings/language", language)
    // Outbound rows: the result points at them, it never opens the chat or the page itself.
    row(s, "settings/help", R.string.AskAQuestion, RowRef(R.id.btn_help), R.drawable.baseline_live_help_24, kw = kw(R.string.XgramSearchKwSupport))
    row(s, "settings/faq", R.string.TelegramFAQ, RowRef(R.id.btn_faq), R.drawable.baseline_help_24, kw = kw(R.string.XgramSearchKwFaq))
    row(s, "settings/policy", R.string.PrivacyPolicy, RowRef(R.id.btn_privacyPolicy), R.drawable.baseline_policy_24)
    row(s, "settings/source", R.string.ViewSourceCode, RowRef(R.id.btn_sourceCode, data = { it == null }), R.drawable.baseline_code_24,
      kw = kw(R.string.XgramSearchKwSource))
    row(s, "settings/copyReport", R.string.CopyReportData, RowRef(R.id.btn_copyDebug), R.drawable.baseline_bug_report_24,
      kw = kw(R.string.XgramSearchKwDebug), visible = { Config.SHOW_COPY_REPORT_DETAILS_IN_SETTINGS })
    row(s, "settings/version", R.string.XgramAboutVersion, RowRef(R.id.btn_build), R.drawable.baseline_info_24,
      kw = kw(R.string.XgramSearchKwVersion, R.string.XgramSearchKwDebug))
    // Night mode: its switch flips it right in the results; the row opens Themes at Auto night mode.
    row(
      themes, "settings/nightMode", R.string.NightMode, RowRef(afterHeader = R.string.AutoNightMode), R.drawable.baseline_brightness_2_24,
      kw = kw(R.string.XgramSearchKwDark, R.string.XgramSearchKwPower),
      toggle = InlineToggle({ Theme.isDark() }) { ThemeManager.instance().toggleNightMode() }
    )
    entry(s, "logout", logout)
    entry(s, "logs", appLogs, kw = kw(R.string.XgramSearchKwDebug))
  }

  private fun devicesRows() {
    val d = devices
    row(d, "devices/qr", R.string.ScanQR, RowRef(R.id.btn_qrLogin), R.drawable.xt3000_baseline_qrcode_scan_24,
      kw = kw(R.string.XgramSearchKwSessions), visible = { it.tdlib.allowQrLoginCamera() })
    row(d, "devices/ttl", R.string.SessionTerminateTtl, RowRef(R.id.btn_sessionTtl), R.drawable.baseline_auto_delete_24,
      kw = kw(R.string.XgramSearchKwSessions))
    row(d, "devices/terminateAll", R.string.TerminateAllSessions, RowRef(R.id.btn_terminateAllSessions), R.drawable.baseline_cancel_24,
      sentence = R.string.XgramM3eR4preSbTerminateAll, kw = kw(R.string.XgramSearchKwSessions), soft = true)
    entry(privacy, "websites", websites)
    row(websites, "websites/disconnectAll", R.string.TerminateAllWebSessions, RowRef(R.id.btn_terminateAllSessions), R.drawable.baseline_cancel_24,
      kw = kw(R.string.XgramSearchKwWebsites), soft = true)
  }

  private fun notificationRows() {
    val n = notifications
    row(n, "notif/mode", R.string.NotificationsMode, RowRef(afterHeader = R.string.NotificationsMode),
      kw = kw(R.string.XgramSearchKwAccounts), visible = { it.tdlib.context().isMultiUser() })
    entry(n, "notif/private", scopes[0]!!)
    entry(n, "notif/groups", scopes[1]!!)
    entry(n, "notif/channels", scopes[2]!!)
    row(n, "notif/fetchMissing", R.string.ForegroundSync, RowRef(R.id.btn_foregroundSync), kw = kw(R.string.XgramSearchKwPower),
      visible = { !Config.FOREGROUND_SYNC_ALWAYS_ENABLED })
    entry(n, "notif/archive", archive)
    row(n, "notif/inChatSounds", R.string.InChatSound, RowRef(R.id.btn_inApp_chatSounds))
    row(n, "notif/newContacts", R.string.EventNewContact2, RowRef(R.id.btn_events_contactJoined), kw = kw(R.string.XgramSearchKwContacts))
    row(n, "notif/scheduled", R.string.EventScheduledMessage, RowRef(R.id.btn_events_sentScheduled))
    row(n, "notif/archiveMute", R.string.ArchiveNonContacts, RowRef(R.id.btn_archiveMuteNonContacts), group = R.string.UnknownChats,
      kw = kw(R.string.XgramSearchKwArchive), visible = { it.tdlib.autoArchiveAvailable() })
    row(n, "notif/silenceNonContacts", R.string.MuteNonContacts, RowRef(R.id.btn_silenceNonContacts), group = R.string.UnknownChats)
    row(n, "notif/badgeMuted", R.string.BadgeCounterMuted, RowRef(R.id.btn_appBadgeCountMuted), group = R.string.BadgeCounter, kw = kw(R.string.XgramSearchKwBadge))
    row(n, "notif/badgeArchived", R.string.BadgeCounterArchive, RowRef(R.id.btn_appBadgeCountArchive), group = R.string.BadgeCounter, kw = kw(R.string.XgramSearchKwBadge))
    row(n, "notif/badgeMessages", R.string.BadgeCounterMessages, RowRef(R.id.btn_appBadgeCountMessages), group = R.string.BadgeCounter, kw = kw(R.string.XgramSearchKwBadge))
    row(n, "notif/merge", R.string.NotificationMerge, RowRef(R.id.btn_mergeCategories), group = R.string.NotificationAdvanced)
    // Replaced by the error row while notifications are blocked
    row(n, "notif/system", R.string.SystemNotificationSettings, RowRef(R.id.btn_showAdvanced, stringRes = R.string.SystemNotificationSettings),
      group = R.string.NotificationAdvanced, kw = kw(R.string.XgramSearchKwSystem), soft = true)

    val scopeKeys = arrayOf("private", "groups", "channels")
    for (i in 0 until 3) {
      val p = scopes[i]!!
      val k = "notif/" + scopeKeys[i] + "/"
      row(p, k + "enabled", R.string.NotificationsSetting, RowRef(R.id.btn_notifications_snooze), kw = kw(R.string.XgramSearchKwMute))
      row(p, k + "preview", R.string.MessagePreview, RowRef(R.id.btn_notifications_preview))
      row(p, k + "imagePreview", R.string.MessageContentPreview, RowRef(R.id.btn_notifications_contentPreview),
        visible = { Config.NEED_NOTIFICATION_CONTENT_PREVIEW }, soft = true)
      row(p, k + "vibrate", R.string.Vibrate, RowRef(R.id.btn_notifications_vibrate), soft = true)
      row(p, k + "sound", R.string.Sound, RowRef(R.id.btn_notifications_sound), soft = true)
      row(p, k + "light", R.string.NotificationsLed, RowRef(R.id.btn_notifications_led))
      row(p, k + "importance", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) R.string.NotificationImportance else R.string.NotificationsPriority,
        RowRef(R.id.btn_notifications_priorityOrImportance), kw = kw(R.string.XgramSearchKwImportance), soft = true)
      row(p, k + "channel", R.string.NotificationChannelMore, RowRef(R.id.btn_notifications_channel),
        visible = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O })
      row(p, k + "dismissed", R.string.IncludeDismissed, RowRef(R.id.btn_notifications_includeDismissed))
      if (i == 0) {
        row(p, k + "secretLock", R.string.ShowSecret, RowRef(R.id.btn_hideSecretChats), kw = kw(R.string.XgramSearchKwSecret))
      } else if (i == 1) {
        row(p, k + "mentions", R.string.Mentions, RowRef(R.id.btn_events_mentions))
        row(p, k + "pinned", R.string.PinnedMessages, RowRef(R.id.btn_events_pinnedMessages))
      }
    }

    val a = archive
    row(a, "archive/unmuted", R.string.ArchiveSettingUnmutedChats, RowRef(R.id.btn_keepUnmutedChatsArchived), group = R.string.ArchiveSettingUnmutedChatsTitle)
    row(a, "archive/folders", R.string.ArchiveSettingFolderChats, RowRef(R.id.btn_keepFolderChatsArchived), group = R.string.ArchiveSettingFolderChatsTitle,
      visible = { it.tdlib.hasFolders() }, soft = true)
    row(a, "archive/unknown", R.string.ArchiveNonContacts, RowRef(R.id.btn_archiveMuteNonContacts), group = R.string.UnknownChats, soft = true)
    row(a, "archive/asFolder", R.string.ArchiveAppearanceToggle, RowRef(R.id.btn_archiveAsFolder), group = R.string.ArchiveAppearanceTitle,
      visible = { it.tdlib.hasFolders() }, soft = true)
  }

  private fun dataRows() {
    val d = data
    entry(d, "data/storage", storage)
    entry(d, "data/network", networkUsage)
    row(d, "data/saver", R.string.DataSaver, RowRef(R.id.btn_dataSaver), kw = kw(R.string.XgramSearchKwPower, R.string.XgramSearchKwDataUsage))
    row(d, "data/saverAuto", R.string.TurnOnAutomatically, RowRef(R.id.btn_dataSaverForce), group = R.string.DataSaver)
    row(d, "data/lessCalls", R.string.VoipUseLessData, RowRef(R.id.btn_lessDataForCalls), group = R.string.Connection, kw = kw(R.string.XgramSearchKwCalls))
    entry(d, "data/proxy", proxy, group = R.string.Connection)
    row(d, "data/autodl", R.string.AutomaticMediaDownload, RowRef(afterHeader = R.string.AutomaticMediaDownload), kw = kw(R.string.XgramSearchKwDownload))
    row(d, "data/autodl/private", R.string.InPrivateChats, RowRef(R.id.btn_inPrivateChats), group = R.string.AutomaticMediaDownload, kw = kw(R.string.XgramSearchKwDownload))
    row(d, "data/autodl/groups", R.string.InGroups, RowRef(R.id.btn_inGroupChats), group = R.string.AutomaticMediaDownload, kw = kw(R.string.XgramSearchKwDownload))
    row(d, "data/autodl/channels", R.string.InChannels, RowRef(R.id.btn_inChannelChats), group = R.string.AutomaticMediaDownload, kw = kw(R.string.XgramSearchKwDownload))
    row(d, "data/limitMobile", R.string.RestrictOverMobile, RowRef(R.id.btn_mediaMobileLimits, reveal = R.id.btn_showAdvanced),
      group = R.string.AutomaticMediaDownload, kw = kw(R.string.XgramSearchKwDownload, R.string.XgramSearchKwSizeLimit))
    row(d, "data/limitWifi", R.string.RestrictOnWiFi, RowRef(R.id.btn_mediaWiFiLimits, reveal = R.id.btn_showAdvanced),
      group = R.string.AutomaticMediaDownload, kw = kw(R.string.XgramSearchKwSizeLimit))
    row(d, "data/limitRoaming", R.string.RestrictOnRoaming, RowRef(R.id.btn_mediaRoamingLimits, reveal = R.id.btn_showAdvanced),
      group = R.string.AutomaticMediaDownload, kw = kw(R.string.XgramSearchKwSizeLimit))

    val st = storage
    row(st, "storage/keepMedia", R.string.KeepMedia, RowRef(R.id.btn_keepMedia), kw = kw(R.string.XgramSearchKwKeepMedia))
    row(st, "storage/settings", R.string.SettingsAndThemes, RowRef(R.id.btn_settings))
    row(st, "storage/localizations", R.string.LanguageDatabase, RowRef(R.id.btn_languageSettings))
    row(st, "storage/localDb", R.string.LocalDatabase, RowRef(R.id.btn_localDatabase), kw = kw(R.string.XgramSearchKwDatabase))
    row(st, "storage/media", R.string.MediaAndFiles, RowRef(R.id.btn_clearCache))

    val net = networkUsage
    row(net, "net/mobile", R.string.MobileUsage, RowRef(R.id.btn_dataUsageMobile), R.drawable.baseline_signal_cellular_alt_24)
    row(net, "net/wifi", R.string.WiFiUsage, RowRef(R.id.btn_dataUsageWiFi), R.drawable.baseline_wifi_24)
    row(net, "net/roaming", R.string.RoamingUsage, RowRef(R.id.btn_dataUsageRoaming), R.drawable.baseline_public_24)
    row(net, "net/reset", R.string.ResetStatistics, RowRef(R.id.btn_resetNetworkStats))

    val p = proxy
    row(p, "proxy/add", R.string.ProxyAdd, RowRef(R.id.btn_addProxy), R.drawable.baseline_add_24)
    row(p, "proxy/autoSwitch", R.string.ProxyAutoSwitch, RowRef(R.id.btn_proxyAutoSwitch), visible = { Settings.instance().availableProxyCount > 0 })
    row(p, "proxy/none", R.string.ProxyNone, RowRef(R.id.btn_noProxy), group = R.string.ProxyConnections)
    row(p, "proxy/rules", R.string.XgramTgxProxyRules, RowRef(R.id.btn_xgramTgxProxyRules), kw = kw(R.string.XgramSearchKwProxy))
    row(p, "proxy/calls", R.string.UseProxyForCalls, RowRef(R.id.btn_useProxyForCalls), group = R.string.ProxyOther, kw = kw(R.string.XgramSearchKwCalls), soft = true)
  }

  private fun privacyRows() {
    val p = privacy
    entry(p, "privacy/sessions", devices, title = R.string.SessionsTitle, group = R.string.SecurityTitle)
    entry(p, "privacy/passcode", passcode, group = R.string.SecurityTitle)
    row(p, "privacy/2fa", R.string.TwoStepVerification, RowRef(R.id.btn_2fa), R.drawable.mrgrigri_baseline_textbox_password_24,
      group = R.string.SecurityTitle, kw = kw(R.string.XgramSearchKw2fa))
    row(p, "privacy/2fa/changePassword", R.string.ChangePassword, RowRef(R.id.btn_2fa), R.drawable.mrgrigri_baseline_textbox_password_24,
      group = R.string.TwoStepVerification, kw = kw(R.string.XgramSearchKw2fa))
    row(p, "privacy/2fa/recoveryEmail", R.string.SetRecoveryEmail, RowRef(R.id.btn_2fa), R.drawable.baseline_alternate_email_24,
      group = R.string.TwoStepVerification, kw = kw(R.string.XgramSearchKw2fa))
    row(p, "privacy/loginEmail", R.string.LoginEmail, RowRef(R.id.btn_loginEmail), R.drawable.baseline_alternate_email_24,
      group = R.string.SecurityTitle, soft = true)
    row(p, "privacy/xgram", R.string.XgramTgxPrivacy, null, R.drawable.baseline_ghost_24, kw = kw(R.string.XgramSearchKwLastSeen),
      open = push { XgramSettingsPages.open(it.context, it.tdlib, XgramSettingsController.SECTION_PRIVACY) })
    entry(p, "privacy/blocked", blocked, group = R.string.PrivacyTitle)
    for (i in rulePages.indices) {
      entry(p, "privacy/rule/" + ruleKeys[i], rulePages[i]!!, group = R.string.PrivacyTitle)
    }
    row(p, "privacy/newChats", R.string.PrivacyMessagePreview, RowRef(R.id.btn_newChatsPrivacy), R.drawable.baseline_chat_bubble_24,
      group = R.string.PrivacyTitle, kw = kw(R.string.XgramSearchKwNewChats))
    row(p, "privacy/suggestContacts", R.string.SuggestContacts, RowRef(R.id.btn_suggestContacts), group = R.string.Contacts, kw = kw(R.string.XgramSearchKwContacts))
    row(p, "privacy/deleteSynced", R.string.SyncContactsDelete, RowRef(R.id.btn_resetContacts), group = R.string.Contacts, kw = kw(R.string.XgramSearchKwContacts))
    row(p, "privacy/syncContacts", R.string.SyncContacts, RowRef(R.id.btn_syncContacts), group = R.string.Contacts, kw = kw(R.string.XgramSearchKwContacts))
    row(p, "privacy/payments", R.string.PrivacyPaymentsClear, RowRef(R.id.btn_clearPaymentAndShipping), group = R.string.PrivacyBots, kw = kw(R.string.XgramSearchKwPayments))
    row(p, "privacy/secretPreviews", R.string.SecretWebPage, RowRef(R.id.btn_secretLinkPreviews), group = R.string.SecretChats, kw = kw(R.string.XgramSearchKwSecret))
    row(p, "privacy/hideSecret", R.string.HideSecret, RowRef(R.id.btn_hideSecretChats), group = R.string.SecretChats, kw = kw(R.string.XgramSearchKwSecret))
    row(p, "privacy/incognitoKeyboard", R.string.IncognitoKeyboard, RowRef(R.id.btn_incognitoMode), group = R.string.SecretChats, kw = kw(R.string.XgramSearchKwKeyboard))
    row(p, "privacy/mapProvider", R.string.MapPreviewProvider, RowRef(R.id.btn_mapProvider), group = R.string.SecretChats, kw = kw(R.string.XgramSearchKwMaps))
    row(p, "privacy/drafts", R.string.PrivacyDeleteCloudDrafts, RowRef(R.id.btn_clearAllDrafts), group = R.string.PrivacyAdvanced, kw = kw(R.string.XgramSearchKwDrafts))
    row(p, "privacy/accountTtl", R.string.DeleteAccountIfAwayFor2, RowRef(R.id.btn_accountTTL), group = R.string.PrivacyAdvanced,
      kw = kw(R.string.XgramSearchKwAccount, R.string.XgramSearchKwSelfDestruct))
    // Destructive: pointed at, never run
    row(p, "privacy/deleteAccount", R.string.DeleteMyAccount, RowRef(R.id.btn_deleteAccount), group = R.string.PrivacyAdvanced, kw = kw(R.string.XgramSearchKwAccount))

    // On the rule pages
    val status = rulePages[0]!!
    row(status, "rule/status/hideReadTime", R.string.HideReadTime, RowRef(R.id.btn_togglePermission, stringRes = R.string.HideReadTime),
      kw = kw(R.string.XgramSearchKwLastSeen), soft = true)
    val photo = rulePages[1]!!
    row(photo, "rule/photo/public", R.string.PublicPhoto, RowRef(R.id.btn_togglePermission, stringRes = R.string.PublicPhoto),
      kw = kw(R.string.XgramSearchKwProfile), soft = true)

    // Expressive design: the "Block user" row above the list, added once the blocked list loads
    row(blocked, "blocked/add", R.string.BlockUser, RowRef(R.id.btn_blockSender), R.drawable.baseline_block_24,
      kw = kw(R.string.XgramSearchKwBlocked), visible = { it.expressive }, soft = true)
    // Telegram X's look: blocking starts from the header's add button; the result opens Blocked users
    row(blocked, "blocked/add", R.string.BlockUser, null, R.drawable.baseline_block_24,
      kw = kw(R.string.XgramSearchKwBlocked), visible = { !it.expressive })
  }

  private fun passcodeRows() {
    val p = passcode
    val enabled: (SearchEnv) -> Boolean = { Passcode.instance().isEnabled }
    row(p, "passcode/lock", R.string.PasscodeItem, RowRef(R.id.btn_passcode), R.drawable.baseline_lock_24, sentence = R.string.XgramR6B8PasscodeLock)
    row(p, "passcode/change", R.string.ChangePasscode, RowRef(R.id.btn_passcode_change), R.drawable.mrgrigri_baseline_textbox_password_24,
      sentence = R.string.XgramR6B8ChangePasscode, visible = enabled)
    if (BiometricAuthentication.ONLY_FINGERPRINT) {
      row(p, "passcode/biometrics", R.string.passcode_fingerprint, RowRef(R.id.btn_biometrics), R.drawable.baseline_fingerprint_24,
        sentence = R.string.XgramR6B8UnlockFingerprint, visible = { Passcode.instance().isEnabled && BiometricAuthentication.isAvailable() }, soft = true)
    } else {
      row(p, "passcode/biometrics", R.string.passcode_biometrics, RowRef(R.id.btn_biometrics), R.drawable.baseline_fingerprint_24,
        sentence = R.string.XgramR6B8UnlockBiometrics, visible = { Passcode.instance().isEnabled && BiometricAuthentication.isAvailable() }, soft = true)
    }
    row(p, "passcode/invisible", R.string.passcode_passcodeInvisibility, RowRef(R.id.btn_pattern), R.drawable.baseline_eye_off_24,
      sentence = R.string.XgramR6B8PasscodeInvisibility, visible = enabled, soft = true)
    row(p, "passcode/autoLock", R.string.AutoLock, RowRef(R.id.btn_passcode_auto), R.drawable.xgram_r6_b8_timer_24,
      sentence = R.string.XgramR6B8AutoLock, kw = kw(R.string.XgramSearchKwAutoLock), visible = enabled)
    row(p, "passcode/notifContent", R.string.AllowNotifications, RowRef(R.id.btn_notificationContent), R.drawable.baseline_notifications_24,
      sentence = R.string.XgramR6B8NotificationsContent, kw = kw(R.string.XgramSearchKwNotify), visible = enabled)
    row(p, "passcode/screenCapture", R.string.ScreenCapture, RowRef(R.id.btn_screenCapture), R.drawable.xgram_r6_b8_screenshot_24,
      sentence = R.string.XgramR6B8ScreenCapture, kw = kw(R.string.XgramSearchKwScreenshot), visible = enabled)
  }

  private fun themeRows() {
    val t = themes
    row(t, "themes/bubbles", R.string.ChatStyleBubbles, RowRef(R.id.theme_chat_classic), group = R.string.ChatMode, kw = kw(R.string.XgramSearchKwBubbles))
    row(t, "themes/bubblesChannels", R.string.ChatStyleBubblesChannel, RowRef(R.id.btn_forcePlainChannels), group = R.string.ChatMode, kw = kw(R.string.XgramSearchKwBubbles))
    row(t, "themes/icons", R.string.Icons, RowRef(R.id.btn_icon), group = R.string.ChatMode, kw = kw(R.string.XgramSearchKwIcons))
    row(t, "themes/chatListStyle", R.string.ChatListStyle, RowRef(R.id.btn_chatListStyle), group = R.string.ChatMode,
      kw = kw(R.string.XgramSearchKwFolders, R.string.XgramSearchKwChatListView), visible = { !it.expressive })
    row(t, "themes/wallpaper", R.string.Wallpaper, RowRef(R.id.btn_chatBackground), group = R.string.ChatMode, kw = kw(R.string.XgramSearchKwWallpaper))
    row(t, "themes/fontSize", R.string.TextSize, RowRef(R.id.btn_chatFontSize), group = R.string.ChatMode, kw = kw(R.string.XgramSearchKwFont))
    row(t, "themes/chatListView", R.string.XgramChatListView, RowRef(R.id.btn_xgramChatListView),
      kw = kw(R.string.XgramSearchKwChatListView), visible = { it.expressive })
    row(t, "themes/biggerText", R.string.XgramChatListBigText, RowRef(R.id.btn_xgramChatListBigText), group = R.string.XgramChatListView,
      kw = kw(R.string.XgramSearchKwFont), visible = { it.expressive })
    row(t, "themes/colorTheme", R.string.ColorTheme, RowRef(afterHeader = R.string.ColorTheme), kw = kw(R.string.XgramSearchKwDark))
    // The built-in themes; their names are read when the index is built
    for (info in ThemeManager.instance().builtinThemes) {
      val themeInfo: ThemeInfo = info
      val themeId = themeInfo.id
      val dark = Theme.isDarkTheme(themeId)
      row(
        t, "themes/theme/$themeId", ThemeManager.getBuiltinThemeName(themeId), RowRef(R.id.btn_theme, data = { (it as? ThemeInfo)?.id == themeId }),
        R.drawable.baseline_palette_24, group = R.string.ColorTheme, kw = if (dark) kw(R.string.XgramSearchKwDark) else NONE
      )
    }
    row(t, "themes/create", R.string.ThemeCreate, RowRef(R.id.btn_themeCreate), group = R.string.ColorTheme, kw = kw(R.string.XgramSearchKwCustomTheme))
    row(t, "themes/autoNight", R.string.AutoNightMode, RowRef(afterHeader = R.string.AutoNightMode), kw = kw(R.string.XgramSearchKwDark, R.string.XgramSearchKwAutoNight))
  }

  private fun interfaceRows() {
    val u = tweaks
    row(u, "ui/autoplayGifs", R.string.AutoplayGifs, RowRef(R.id.btn_autoplayGIFs), kw = kw(R.string.XgramSearchKwMedia, R.string.XgramSearchKwPower))
    row(u, "ui/inAppBrowser", R.string.UseInAppBrowser, RowRef(R.id.btn_useInAppBrowser), kw = kw(R.string.XgramSearchKwBrowser),
      visible = { Config.IN_APP_BROWSER_AVAILABLE })
    row(u, "ui/holdPreview", R.string.HoldToPreview, RowRef(R.id.btn_useHoldToPreview), kw = kw(R.string.XgramSearchKwPeek),
      visible = { Config.FORCE_TOUCH_ENABLED && Config.HOLD_TO_PREVIEW_AVAILABLE })
    row(u, "ui/vibrations", R.string.CustomVibrations, RowRef(R.id.btn_customVibrations), kw = kw(R.string.XgramSearchKwHaptic))
    row(u, "ui/reduceMotion", R.string.ReduceMotion, RowRef(R.id.btn_reduceMotion), kw = kw(R.string.XgramSearchKwPower, R.string.XgramSearchKwAccessibility))
    row(u, "ui/rtl", R.string.RtlLayout, RowRef(R.id.btn_switchRtl), kw = kw(R.string.XgramSearchKwRtl),
      visible = { Lang.rtl() || Lang.getLanguageDirection() != Lang.LANGUAGE_DIRECTION_LTR })
    row(u, "ui/sensitive", R.string.DisplaySensitiveContent, RowRef(R.id.btn_restrictSensitiveContent), group = R.string.Chats,
      kw = kw(R.string.XgramSearchKwSensitive),
      visible = { it.tdlib.canIgnoreSensitiveContentRestriction() && (TgxSearchOpen.sideLoaded() || it.tdlib.ignoreSensitiveContentRestrictions()) })
    row(u, "ui/ignoreRestrictions", R.string.IgnoreRestrictions, RowRef(R.id.btn_ignoreContentRestrictions), group = R.string.Chats,
      kw = kw(R.string.XgramSearchKwSensitive), visible = { TgxSearchOpen.sideLoaded() || !Settings.instance().needRestrictContent() })
    row(u, "ui/explicitDice", R.string.UseExplicitDice, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_EXPLICIT_DICE), group = R.string.Chats,
      visible = { Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPLICIT_DICE) })
    row(u, "ui/swipes", R.string.ChatQuickActions, RowRef(R.id.btn_chatSwipes), group = R.string.Chats, kw = kw(R.string.XgramSearchKwSwipes))
    row(u, "ui/sendByEnter", R.string.SendByEnter, RowRef(R.id.btn_sendByEnter), group = R.string.Chats, kw = kw(R.string.XgramSearchKwKeyboard))
    row(u, "ui/hideKeyboard", R.string.HideChatKeyboard, RowRef(R.id.btn_hideChatKeyboard), group = R.string.Chats, kw = kw(R.string.XgramSearchKwKeyboard))
    row(u, "ui/saveEdited", R.string.SaveOutgoingPhotos, RowRef(R.id.btn_saveToGallery), group = R.string.Chats, kw = kw(R.string.XgramSearchKwGallery))
    row(u, "ui/instantView", R.string.AutoInstantView, RowRef(R.id.btn_instantViewMode), group = R.string.Chats, kw = kw(R.string.XgramSearchKwInstantView))
    row(u, "ui/embeds", R.string.OpenEmbed, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_NO_EMBEDS), group = R.string.Chats,
      kw = kw(R.string.XgramSearchKwEmbeds))
    row(u, "ui/sizeUnit", R.string.SizeUnit, RowRef(R.id.btn_sizeUnit), group = R.string.Chats, kw = kw(R.string.XgramSearchKwSizeUnit))
    row(u, "ui/mosaic", R.string.RememberAlbumSetting, RowRef(R.id.btn_mosaic), group = R.string.Chats, kw = kw(R.string.XgramSearchKwAlbum))
    row(u, "ui/separateMedia", R.string.SeparateMediaTab, RowRef(R.id.btn_separateMedia), group = R.string.Chats, kw = kw(R.string.XgramSearchKwSharedMedia))
    row(u, "ui/markdown", R.string.EditMarkdown, RowRef(R.id.btn_markdown), group = R.string.Chats, kw = kw(R.string.XgramSearchKwMarkdown))
    row(u, "ui/earpieceVoice", R.string.EarpieceMode, RowRef(R.id.btn_earpieceMode), group = R.string.VoiceMessages, kw = kw(R.string.XgramSearchKwEarpiece))
    row(u, "ui/earpieceVideo", R.string.EarpieceMode, RowRef(R.id.btn_earpieceModeVideo), group = R.string.VideoMessages, kw = kw(R.string.XgramSearchKwEarpiece))
    row(u, "ui/rearRounds", R.string.UseRearRoundVideos, RowRef(R.id.btn_rearRounds), group = R.string.VideoMessages, kw = kw(R.string.XgramSearchKwCamera))
    row(u, "ui/hqRounds", R.string.UseHqRoundVideos, RowRef(R.id.btn_hqRounds), group = R.string.VideoMessages, kw = kw(R.string.XgramSearchKwCamera),
      visible = { !Device.NEED_HQ_ROUND_VIDEOS && Config.ROUND_VIDEOS_RECORD_SUPPORTED })
    row(u, "ui/confirmCalls", R.string.ConfirmCalls, RowRef(R.id.btn_confirmCalls), group = R.string.Calls, kw = kw(R.string.XgramSearchKwCalls))
    row(u, "ui/cameraType", R.string.CameraType, RowRef(R.id.btn_cameraType), group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera),
      visible = { Config.CUSTOM_CAMERA_AVAILABLE && Config.CAMERA_X_AVAILABLE })
    row(u, "ui/systemCamera", R.string.CameraUseSystem, RowRef(R.id.btn_cameraType), group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera),
      visible = { Config.CUSTOM_CAMERA_AVAILABLE && !Config.CAMERA_X_AVAILABLE })
    row(u, "ui/cameraKeep", R.string.CameraKeepMedia, RowRef(R.id.btn_cameraSetting, longId = Settings.SETTING_FLAG_CAMERA_KEEP_DISCARDED_MEDIA),
      group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera), visible = { Config.CUSTOM_CAMERA_AVAILABLE })
    val cameraSettings: (SearchEnv) -> Boolean = { Config.CUSTOM_CAMERA_AVAILABLE && Settings.instance().cameraType != Settings.CAMERA_TYPE_SYSTEM }
    row(u, "ui/cameraGrid", R.string.CameraGrid, RowRef(R.id.btn_cameraSetting, longId = Settings.SETTING_FLAG_CAMERA_SHOW_GRID),
      group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera), visible = cameraSettings)
    row(u, "ui/cameraVolume", R.string.CameraVolume, RowRef(R.id.btn_cameraVolume), group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera), visible = cameraSettings)
    row(u, "ui/cameraRatio", R.string.CameraRatio, RowRef(R.id.btn_cameraRatio), group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera), visible = cameraSettings)
    row(u, "ui/cameraFlip", R.string.CameraFlip, RowRef(R.id.btn_cameraSetting, longId = Settings.SETTING_FLAG_CAMERA_NO_FLIP),
      group = R.string.Camera, kw = kw(R.string.XgramSearchKwCamera), visible = cameraSettings)
    row(u, "ui/systemFonts", R.string.UseSystemFonts, RowRef(R.id.btn_systemFonts), group = R.string.Other, kw = kw(R.string.XgramSearchKwFont))
    row(u, "ui/builtinDecoding", R.string.ForceBuiltinDecoding, RowRef(R.id.btn_forceExoPlayerExtensions), group = R.string.Other, kw = kw(R.string.XgramSearchKwCodec))
    row(u, "ui/noHls", R.string.DisableHlsVideo, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_FORCE_DISABLE_HLS_VIDEO),
      group = R.string.Other, kw = kw(R.string.XgramSearchKwCodec), visible = { Config.HLS_VIDEO_ENABLED })
    row(u, "ui/compressAudio", R.string.CompressAudio, RowRef(R.id.btn_audioCompression), group = R.string.Other, kw = kw(R.string.XgramSearchKwMedia))
  }

  private fun stickerRows() {
    val s = stickers
    row(s, "stickers/quickReaction", R.string.QuickReaction, RowRef(R.id.btn_quick_reaction), group = R.string.Reactions, kw = kw(R.string.XgramSearchKwReactions))
    row(s, "stickers/bigReactions", R.string.BigReactions, RowRef(R.id.btn_big_reactions), group = R.string.Reactions, kw = kw(R.string.XgramSearchKwReactions))
    row(s, "stickers/avatarsReactions", R.string.AvatarsInReactions, RowRef(R.id.btn_avatarsInReactions), group = R.string.Reactions, kw = kw(R.string.XgramSearchKwReactions))
    row(s, "stickers/emojiPacks", R.string.EmojiPacks, RowRef(R.id.btn_animatedEmojiSettings), R.drawable.baseline_emoticon_outline_24,
      group = R.string.EmojiHeader, kw = kw(R.string.XgramSearchKwEmoji))
    row(s, "stickers/emojiSet", R.string.Emoji, RowRef(R.id.btn_emoji), group = R.string.EmojiHeader, kw = kw(R.string.XgramSearchKwEmoji, R.string.XgramSearchKwEmojiSet))
    row(s, "stickers/suggestEmoji", R.string.SuggestAnimatedEmoji, RowRef(R.id.btn_emojiSuggestions), group = R.string.EmojiHeader, kw = kw(R.string.XgramSearchKwEmoji))
    row(s, "stickers/animatedEmoji", R.string.AnimatedEmoji, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_NO_ANIMATED_EMOJI),
      group = R.string.EmojiHeader, kw = kw(R.string.XgramSearchKwEmoji, R.string.XgramSearchKwPower))
    row(s, "stickers/bigEmoji", R.string.BigEmoji, RowRef(R.id.btn_useBigEmoji), group = R.string.EmojiHeader, kw = kw(R.string.XgramSearchKwEmoji))
    row(s, "stickers/emojiOrder", R.string.DynamicEmojiPackOrder, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_DYNAMIC_ORDER_EMOJI_PACKS),
      group = R.string.EmojiHeader, kw = kw(R.string.XgramSearchKwEmoji))
    row(s, "stickers/sets", R.string.StickerPacks, RowRef(R.id.btn_stickerSettings), R.drawable.deproko_baseline_insert_sticker_24,
      group = R.string.Stickers, kw = kw(R.string.XgramSearchKwStickers))
    row(s, "stickers/suggest", R.string.SuggestStickers, RowRef(R.id.btn_stickerSuggestions), group = R.string.Stickers, kw = kw(R.string.XgramSearchKwStickers))
    row(s, "stickers/loop", R.string.LoopAnimatedStickers, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP),
      group = R.string.Stickers, kw = kw(R.string.XgramSearchKwStickers, R.string.XgramSearchKwPower))
    row(s, "stickers/order", R.string.DynamicStickerPackOrder, RowRef(R.id.btn_toggleNewSetting, longId = Settings.SETTING_FLAG_DYNAMIC_ORDER_STICKER_PACKS),
      group = R.string.Stickers, kw = kw(R.string.XgramSearchKwStickers))
  }

  private fun languageRows() {
    entry(language, "language/translation", translation)
    val t = translation
    row(t, "translate/style", R.string.ChatTranslateStyle1, RowRef(R.id.btn_chatTranslateStylePopup), kw = kw(R.string.XgramSearchKwTranslateStyle))
    row(t, "translate/service", R.string.XgramTrProvider, RowRef(R.id.btn_xgramTgxTrProvider), kw = kw(R.string.XgramSearchKwTranslateProvider))
    val shown: (SearchEnv) -> Boolean = { Settings.instance().chatTranslateMode != Settings.TRANSLATE_MODE_NONE }
    row(t, "translate/quick", R.string.QuickTranslate, RowRef(R.id.btn_quickTranslate), kw = kw(R.string.XgramSearchKwTranslate), visible = shown)
    row(t, "translate/doNot", R.string.DoNotTranslate, RowRef(afterHeader = R.string.DoNotTranslate), kw = kw(R.string.XgramSearchKwTranslate), visible = shown)
  }

  private fun folderRows() {
    val f = folders
    row(f, "folders/appearance", R.string.ChatFoldersAppearance, RowRef(R.id.btn_chatFolderStyle), group = R.string.ChatFoldersSettings,
      kw = kw(R.string.XgramSearchKwFolderStyle))
    row(f, "folders/badge", R.string.BadgeCounter, RowRef(R.id.btn_appBadge), group = R.string.ChatFoldersSettings, kw = kw(R.string.XgramSearchKwBadge))
    row(f, "folders/archiveAsFolder", R.string.ArchiveAsFolder, RowRef(R.id.btn_archiveAsFolder), group = R.string.ChatFoldersSettings,
      kw = kw(R.string.XgramSearchKwArchive))
    row(f, "folders/hideAllChats", R.string.XgramHideAllChats, RowRef(R.id.btn_xgramHideAllChats), group = R.string.ChatFoldersSettings,
      kw = kw(R.string.XgramSearchKwFolders))
    row(f, "folders/hideTabs", R.string.XgramHideFolders, RowRef(R.id.btn_xgramHideFolders), group = R.string.ChatFoldersSettings,
      kw = kw(R.string.XgramSearchKwFolders))
    row(f, "folders/create", R.string.CreateNewFolder, RowRef(R.id.btn_createNewFolder), R.drawable.baseline_add_24, group = R.string.ChatFolders,
      kw = kw(R.string.XgramSearchKwNewFolder))
    row(f, "folders/recommended", R.string.RecommendedFolders, RowRef(afterHeader = R.string.RecommendedFolders), kw = kw(R.string.XgramSearchKwFolders), soft = true)
  }

  private fun accountRows() {
    val l = logout
    row(l, "logout/addAccount", R.string.SignOutAltAddAccount, RowRef(R.id.btn_addAccount), R.drawable.baseline_person_add_24, kw = kw(R.string.XgramSearchKwAccounts))

    val g = appLogs
    row(g, "logs/share", R.string.XgramLogsShare, RowRef(R.id.btn_xgramLogsShare), R.drawable.baseline_share_24)
    row(g, "logs/detailed", R.string.XgramLogsDetailed, RowRef(R.id.btn_xgramLogsDetailed), R.drawable.baseline_bug_report_24)
    row(g, "logs/verbosity", R.string.DebugVerbosity, RowRef(R.id.btn_log_verbosity), group = R.string.AppLogs)
    row(g, "logs/tags", R.string.DebugLogTags, RowRef(R.id.btn_log_tags), group = R.string.AppLogs)
    row(g, "logs/files", R.string.DebugLogFiles, RowRef(R.id.btn_log_files), group = R.string.AppLogs)
    row(g, "logs/logcat", R.string.DebugLogcat, RowRef(R.id.btn_log_android), group = R.string.AppLogs)
    row(g, "logs/push", R.string.PushServices, null, R.drawable.baseline_sync_24, kw = kw(R.string.XgramSearchKwNotify, R.string.XgramSearchKwPush),
      open = push { TgxSearchOpen.pushServices(it.context, it.tdlib) })
    // XGram's tester check, then Telegram X's warning: the page opens by itself
    row(g, "logs/experimental", R.string.ExperimentalSettings, null, R.drawable.templarian_baseline_flask_24, kw = kw(R.string.XgramSearchKwDebug),
      visible = { Settings.instance().hasLogsEnabled() }, open = { ctx -> TgxSearchOpen.openTesterPage(ctx.from, false); Opened.Done })
    row(g, "logs/tdlib", R.string.TdlibLogs, null, R.drawable.baseline_build_24, kw = kw(R.string.XgramSearchKwDebug),
      visible = { Settings.instance().hasLogsEnabled() }, open = { ctx -> TgxSearchOpen.openTesterPage(ctx.from, true); Opened.Done })
  }

}
