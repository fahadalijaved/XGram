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
package com.fahadali.xgram.search;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.EditBioController;
import org.thunderdog.challegram.ui.EditNameController;
import org.thunderdog.challegram.ui.EditUsernameController;
import org.thunderdog.challegram.ui.SettingsArchiveChatListController;
import org.thunderdog.challegram.ui.SettingsBlockedController;
import org.thunderdog.challegram.ui.SettingsBugController;
import org.thunderdog.challegram.ui.SettingsCacheController;
import org.thunderdog.challegram.ui.SettingsController;
import org.thunderdog.challegram.ui.SettingsDataController;
import org.thunderdog.challegram.ui.SettingsFoldersController;
import org.thunderdog.challegram.ui.SettingsLanguageController;
import org.thunderdog.challegram.ui.SettingsLanguageTranslateController;
import org.thunderdog.challegram.ui.SettingsLogOutController;
import org.thunderdog.challegram.ui.SettingsNotificationController;
import org.thunderdog.challegram.ui.SettingsPhoneController;
import org.thunderdog.challegram.ui.SettingsPrivacyController;
import org.thunderdog.challegram.ui.SettingsPrivacyKeyController;
import org.thunderdog.challegram.ui.SettingsProxyController;
import org.thunderdog.challegram.ui.SettingsSessionsController;
import org.thunderdog.challegram.ui.SettingsStickersAndEmojiController;
import org.thunderdog.challegram.ui.SettingsThemeController;
import org.thunderdog.challegram.ui.SettingsWebsitesController;
import org.thunderdog.challegram.unsorted.AppContext;

import me.vkryl.android.AppInstallationUtil;
import tgx.td.Td;

/**
 * How each Telegram X settings page is made, exactly as the page that opens it makes it (its onClick). Search results
 * open pages through these; nothing here sends a TDLib request (pages that need data fetch it themselves).
 */
public final class TgxSearchOpen {
  private TgxSearchOpen () { }

  // Main Settings

  public static ViewController<?> editUsername (Context context, Tdlib tdlib) {
    return new EditUsernameController(context, tdlib);
  }

  public static ViewController<?> editName (Context context, Tdlib tdlib) {
    EditNameController c = new EditNameController(context, tdlib);
    c.setMode(EditNameController.Mode.RENAME_SELF);
    return c;
  }

  /** The bio editor with the bio TDLib already gave (an empty field until it did). */
  public static ViewController<?> editBio (Context context, Tdlib tdlib) {
    TdApi.UserFullInfo full = tdlib.myUserFull();
    String bio = full != null && full.bio != null && full.bio.text != null ? full.bio.text : "";
    EditBioController c = new EditBioController(context, tdlib);
    c.setArguments(new EditBioController.Arguments(bio, 0));
    return c;
  }

  public static ViewController<?> changeNumber (Context context, Tdlib tdlib) {
    return new SettingsPhoneController(context, tdlib);
  }

  public static ViewController<?> mainSettings (Context context, Tdlib tdlib) {
    return new SettingsController(context, tdlib);
  }

  // Devices

  public static ViewController<?> sessions (Context context, Tdlib tdlib) {
    return new SettingsSessionsController(context, tdlib);
  }

  /** Without the privacy page as its arguments the page loads the websites itself. */
  public static ViewController<?> websites (Context context, Tdlib tdlib) {
    return new SettingsWebsitesController(context, tdlib);
  }

  // Notifications

  public static ViewController<?> notifications (Context context, Tdlib tdlib) {
    return new SettingsNotificationController(context, tdlib);
  }

  /** 0: private chats, 1: groups, 2: channels. */
  public static ViewController<?> notificationScope (Context context, Tdlib tdlib, int which) {
    SettingsNotificationController c = new SettingsNotificationController(context, tdlib);
    c.setArguments(new SettingsNotificationController.Args(scope(tdlib, which)));
    return c;
  }

  public static TdApi.NotificationSettingsScope scope (Tdlib tdlib, int which) {
    switch (which) {
      case 1: return tdlib.notifications().scopeGroup();
      case 2: return tdlib.notifications().scopeChannel();
      default: return tdlib.notifications().scopePrivate();
    }
  }

  /** Whether a row's data (the Notifications page's scope rows) is the scope [which]. */
  public static boolean isScope (@Nullable Object data, int which) {
    if (!(data instanceof TdApi.NotificationSettingsScope)) {
      return false;
    }
    int constructor = ((TdApi.NotificationSettingsScope) data).getConstructor();
    switch (which) {
      case 1: return constructor == TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR;
      case 2: return constructor == TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR;
      default: return constructor == TdApi.NotificationSettingsScopePrivateChats.CONSTRUCTOR;
    }
  }

  public static ViewController<?> archiveSettings (Context context, Tdlib tdlib) {
    return new SettingsArchiveChatListController(context, tdlib);
  }

  // Data and storage

  public static ViewController<?> data (Context context, Tdlib tdlib) {
    return new SettingsDataController(context, tdlib);
  }

  /** Storage usage without the Data page as its arguments: it reads the storage statistics itself. */
  public static ViewController<?> storage (Context context, Tdlib tdlib) {
    return new SettingsCacheController(context, tdlib);
  }

  public static ViewController<?> networkUsage (Context context, Tdlib tdlib) {
    return SettingsDataController.xgramNetworkUsage(context, tdlib);
  }

  /** The proxy list itself (openProxySettings would ask to add a proxy while there is none). */
  public static ViewController<?> proxy (Context context, Tdlib tdlib) {
    return new SettingsProxyController(context, tdlib);
  }

  // Privacy and security

  public static ViewController<?> privacy (Context context, Tdlib tdlib) {
    return new SettingsPrivacyController(context, tdlib);
  }

  /** The privacy rules in the order of the privacy page. */
  public static TdApi.UserPrivacySetting[] privacyRules () {
    return new TdApi.UserPrivacySetting[] {
      new TdApi.UserPrivacySettingShowStatus(),
      new TdApi.UserPrivacySettingShowProfilePhoto(),
      new TdApi.UserPrivacySettingShowProfileAudio(),
      new TdApi.UserPrivacySettingShowBirthdate(),
      new TdApi.UserPrivacySettingShowBio(),
      new TdApi.UserPrivacySettingShowPhoneNumber(),
      new TdApi.UserPrivacySettingAllowFindingByPhoneNumber(),
      new TdApi.UserPrivacySettingShowLinkInForwardedMessages(),
      new TdApi.UserPrivacySettingAllowChatInvites(),
      new TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages(),
      new TdApi.UserPrivacySettingAllowCalls(),
      new TdApi.UserPrivacySettingAllowPeerToPeerCalls()
    };
  }

  public static int privacyRuleName (@NonNull TdApi.UserPrivacySetting setting) {
    return SettingsPrivacyKeyController.getName(setting, false, false);
  }

  public static int privacyRuleIcon (@NonNull TdApi.UserPrivacySetting setting) {
    return SettingsPrivacyKeyController.getIcon(setting);
  }

  /** The rule's page, or null when Telegram X shows its Premium alert instead (the privacy page points at the row). */
  public static @Nullable ViewController<?> privacyRule (Context context, Tdlib tdlib, @NonNull TdApi.UserPrivacySetting setting) {
    if (Td.requiresPremiumSubscription(setting) && !tdlib.hasPremium()) {
      return null;
    }
    SettingsPrivacyKeyController c = new SettingsPrivacyKeyController(context, tdlib);
    c.setArguments(new SettingsPrivacyKeyController.Args(setting));
    return c;
  }

  public static ViewController<?> blocked (Context context, Tdlib tdlib) {
    SettingsBlockedController c = new SettingsBlockedController(context, tdlib);
    c.setArguments(new TdApi.BlockListMain());
    return c;
  }

  // Themes, interface, stickers, language, folders

  public static ViewController<?> themes (Context context, Tdlib tdlib) {
    return new SettingsThemeController(context, tdlib);
  }

  /** As Interface options decides whether it shows its content restriction rows. */
  public static boolean sideLoaded () {
    return AppInstallationUtil.isAppSideLoaded(AppContext.get());
  }

  public static ViewController<?> interfaceOptions (Context context, Tdlib tdlib) {
    SettingsThemeController c = new SettingsThemeController(context, tdlib);
    c.setArguments(new SettingsThemeController.Args(SettingsThemeController.MODE_INTERFACE_OPTIONS));
    return c;
  }

  /** The page reads the sticker set counts from the main Settings screen; null without one (see TgxSearchIndex). */
  public static @Nullable ViewController<?> stickers (Context context, Tdlib tdlib, @Nullable SettingsController settings) {
    if (settings == null) {
      return null;
    }
    SettingsStickersAndEmojiController c = new SettingsStickersAndEmojiController(context, tdlib);
    c.setArguments(settings);
    return c;
  }

  public static ViewController<?> language (Context context, Tdlib tdlib) {
    return new SettingsLanguageController(context, tdlib);
  }

  public static ViewController<?> translation (Context context, Tdlib tdlib) {
    return new SettingsLanguageTranslateController(context, tdlib);
  }

  public static ViewController<?> folders (Context context, Tdlib tdlib) {
    return new SettingsFoldersController(context, tdlib);
  }

  // Account and logs

  public static ViewController<?> logOut (Context context, Tdlib tdlib) {
    return new SettingsLogOutController(context, tdlib);
  }

  /** Application logs: the version page everyone gets from the build row. */
  public static ViewController<?> appLogs (Context context, Tdlib tdlib) {
    return new SettingsBugController(context, tdlib);
  }

  public static ViewController<?> pushServices (Context context, Tdlib tdlib) {
    SettingsBugController c = new SettingsBugController(context, tdlib);
    c.setArguments(new SettingsBugController.Args(SettingsBugController.Section.PUSH));
    return c;
  }

  public static boolean isAppLogs (ViewController<?> c) {
    return c instanceof SettingsBugController;
  }

  /** Passcode lock: the unlock screen first when a passcode is set, then its settings. */
  public static void openPasscode (ViewController<?> from) {
    from.tdlib().ui().openPasscodeSetup(from);
  }

  /** Experimental settings or TDLib logs: XGram's tester check, then Telegram X's warning, then the page. */
  public static void openTesterPage (ViewController<?> from, boolean tdlibLogs) {
    com.fahadali.xgram.XgramLogs.testerLevel(from.tdlib(), level -> from.runOnUiThreadOptional(() -> {
      if (tdlibLogs) {
        from.openTdlibLogs(level, null);
      } else {
        from.openExperimentalSettings(level);
      }
    }));
  }
}
