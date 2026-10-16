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

import android.os.Build;

import androidx.annotation.StringRes;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.ChatFolderStyle;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Settings that only do something while another setting allows it. Both settings UIs (the Compose pages and
 * XgramSettingsController) grey these rows out, still visible, and some give the reason under the title. Each rule
 * follows the code that reads the setting: a row is only locked where it provably has no effect.
 * Reasons are string ids, 0 when the row is usable.
 */
public final class XgramSettingDeps {
  private XgramSettingDeps () { }

  private static XgramSettings s () {
    return XgramSettings.instance();
  }

  // Appearance

  /**
   * Expressive design resolves Telegram X's colors to the Material You roles whatever theme is in use (Theme.getColor,
   * XgramM3Colors): Themes and the Material You switch change nothing then. The theme only decides day or night, which
   * Telegram X's night mode switches. Nothing is changed or saved: with Expressive design off both work as before.
   */
  public static boolean themesLocked () {
    return XgramExpressive.isEnabled();
  }

  /** Telegram X's auto night mode, as its Themes page names it. */
  public static @StringRes int autoNightModeName () {
    switch (Settings.instance().getNightMode()) {
      case Settings.NIGHT_MODE_SYSTEM:
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? R.string.AutoNightSystemQ : R.string.AutoNightSystem;
      case Settings.NIGHT_MODE_AUTO:
        return R.string.AutoNightAutomatic;
      case Settings.NIGHT_MODE_SCHEDULED:
        return R.string.AutoNightScheduled;
      case Settings.NIGHT_MODE_NONE:
      default:
        return R.string.AutoNightDisabled;
    }
  }

  /** Split evenly, Resize panes and Split panes at the fold only shape the two panes (XgramTwoPane.update). */
  public static boolean twoPaneOptionsEnabled () {
    return s().twoPaneLayout();
  }

  /** Split evenly sets the widths itself: no handle and no saved width (XgramTwoPane.canResize, computeLeftWidth). */
  public static boolean paneResizableEnabled () {
    return s().twoPaneLayout() && !s().paneSplitEvenly();
  }

  public static @StringRes int twoPaneOptionReason () {
    return s().twoPaneLayout() ? 0 : R.string.XgramNeedsTwoPane;
  }

  public static @StringRes int paneResizableReason () {
    if (!s().twoPaneLayout()) {
      return R.string.XgramNeedsTwoPane;
    }
    return s().paneSplitEvenly() ? R.string.XgramPaneResizableEven : 0;
  }

  /** The note under the pane switches: how to drag the handle, or why there is none while split evenly. */
  public static @StringRes int paneInfo () {
    return s().twoPaneLayout() && s().paneSplitEvenly() ? R.string.XgramPaneInfoEven : R.string.XgramPaneInfo;
  }

  // Chat list

  /**
   * The "All chats" label is the first folder tab's (MainController): no tabs while folders are hidden here, and
   * Telegram X's icons-only folder style shows no label on any tab.
   */
  public static boolean allChatsLabelEnabled (Tdlib tdlib) {
    return allChatsLabelReason(tdlib) == 0;
  }

  public static @StringRes int allChatsLabelReason (Tdlib tdlib) {
    if (s().hideFolders(tdlib.accountId())) {
      return R.string.XgramAllChatsLabelNoTabs;
    }
    return tdlib.settings().chatFolderStyle() == ChatFolderStyle.ICON_ONLY ? R.string.XgramAllChatsLabelIcons : 0;
  }

  /**
   * Status on the left only places the connection text over the status bar (NetworkStatusBarView), which Expressive
   * design does not create (BaseActivity): the connection shows in the app then (XgramConnection). The choice is kept.
   */
  public static boolean connectionStatusPositionEnabled () {
    return !XgramExpressive.isEnabled();
  }

  public static @StringRes int connectionStatusPositionReason () {
    return connectionStatusPositionEnabled() ? 0 : R.string.XgramConnectionStatusExpressive;
  }

  // Messages

  /** Start playing is only read for videos played in the message (XgramInlineVideo). */
  public static boolean inlineVideoStartEnabled () {
    return s().inlineVideo();
  }

  /**
   * Starting automatically also needs Telegram X's Autoplay GIFs (XgramInlineVideo.autoplay): with it off, videos
   * only start on a tap. Nothing is changed or saved.
   */
  public static boolean inlineVideoAutoplayBlocked () {
    XgramSettings settings = s();
    return settings.inlineVideo() && settings.inlineVideoStart() == XgramSettings.INLINE_VIDEO_START_AUTO && !Settings.instance().needAutoplayGIFs();
  }

  /** The note under the video rows: what they do, or that Autoplay GIFs keeps videos from starting by themselves. */
  public static @StringRes int inlineVideoInfo () {
    return inlineVideoAutoplayBlocked() ? R.string.XgramInlineVideoInfoNoAutoplay : R.string.XgramInlineVideoInfo;
  }

  /** Tap to record changes the voice and video button, which Message box buttons can hide (MessagesController). */
  public static boolean tapToRecordEnabled () {
    return !s().inputButtonHidden(XgramSettings.INPUT_BUTTON_VOICE);
  }

  public static @StringRes int tapToRecordReason () {
    return tapToRecordEnabled() ? 0 : R.string.XgramTapToRecordNoButton;
  }

  /**
   * Message menu options that XGram only adds while another setting is on (XgramMessageActions): Summarize needs
   * Telegram AI tools, Edit history needs Save edit history. Their switches change nothing until then.
   */
  public static boolean messageMenuOptionEnabled (String key) {
    return messageMenuOptionReason(key) == 0;
  }

  public static @StringRes int messageMenuOptionReason (String key) {
    if ("summarize".equals(key)) {
      return s().aiTools() ? 0 : R.string.XgramNeedsAiTools;
    }
    if ("edit_history".equals(key)) {
      return s().historySaveEdits() ? 0 : R.string.XgramNeedsSaveEdits;
    }
    return 0;
  }

  // Translation

  /** Only a third-party service can fail over to Telegram (XgramTranslator). */
  public static @StringRes int translateFallbackReason () {
    return com.fahadali.xgram.translate.XgramTranslator.isThirdParty(s().translateProvider()) ? 0 : R.string.XgramTrFallbackTelegram;
  }

  // Privacy

  /** Send silently only applies while not reading, not typing or offline is on (XgramSettings.applyGhostSend). */
  public static boolean ghostSilentEnabled () {
    XgramSettings settings = s();
    return settings.ghostDontRead() || settings.ghostDontType() || settings.ghostOffline();
  }

  public static @StringRes int ghostSilentReason () {
    return ghostSilentEnabled() ? 0 : R.string.XgramGhostSilentOff;
  }

  // History

  /** Deleted messages only stay in chats while they are saved (XgramDeleted.keepInChat, merge): nothing to color. */
  public static boolean historyRedDeletedEnabled () {
    return s().historySaveDeleted();
  }

  public static @StringRes int historyRedDeletedReason () {
    return historyRedDeletedEnabled() ? 0 : R.string.XgramNeedsSaveDeleted;
  }

  // Network

  /** The strength is only read while boost is on (XgramDownloadBoost.level). It is kept for when it is. */
  public static boolean downloadBoostLevelEnabled () {
    return s().downloadBoost();
  }

  /** The rules turn a proxy off and back on (XgramProxyRules.apply): without one they do nothing. */
  public static boolean proxyRulesEnabled () {
    return Settings.instance().hasProxyConfiguration();
  }

  /** The note under the proxy rules: what they do, or that there is no proxy for them yet. */
  public static @StringRes int proxyRulesInfo () {
    return proxyRulesEnabled() ? R.string.XgramProxyRulesInfo : R.string.XgramProxyRulesNoProxy;
  }
}
