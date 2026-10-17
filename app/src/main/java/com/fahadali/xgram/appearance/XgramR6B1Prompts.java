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
package com.fahadali.xgram.appearance;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.popups.ModernOptions;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Intents;

import me.vkryl.core.StringUtils;

/**
 * XGram M3E round 6, batch 1: permission rationales, call notices and voice chat links as Material 3 Expressive
 * prompts. A missing permission is a hero confirm sheet (ModernOptions.XgramHero) with a 96dp cookie holding the
 * permission's icon, a specific sentence-case headline ("Allow microphone access", not the app name), the reason in
 * bodyMedium, and [Not now] [Open settings] with Settings the filled button. Offline and hardware notices use the same
 * sheet with one filled OK. Failures that need no decision are snackbars with an action. Every method returns false
 * when nothing was shown (Expressive design off, or no page to show it from): the caller then keeps Telegram X's alert.
 * Main thread only.
 */
public final class XgramR6B1Prompts {
  private XgramR6B1Prompts () { }

  // Permissions

  /**
   * The rationale for one of Telegram X's "This feature requires …" messages ({@code message}: NoMicrophoneAccess,
   * NoCameraAccess, NoStorageAccess, NoLocationAccess, NoLocationAccessBackground; any other gets a generic headline).
   */
  public static boolean showMissingPermission (@Nullable ViewController<?> c, @StringRes int message) {
    if (c == null || !XgramExpressive.isEnabled()) {
      return false;
    }
    final @DrawableRes int icon;
    final @StringRes int headline;
    if (message == R.string.NoMicrophoneAccess) {
      icon = R.drawable.baseline_mic_24;
      headline = R.string.XgramR6B1AllowMicrophone;
    } else if (message == R.string.NoCameraAccess) {
      icon = R.drawable.baseline_camera_alt_24;
      headline = R.string.XgramR6B1AllowCamera;
    } else if (message == R.string.NoStorageAccess) {
      icon = R.drawable.baseline_folder_24;
      headline = R.string.XgramR6B1AllowStorage;
    } else if (message == R.string.NoLocationAccessBackground) {
      icon = R.drawable.baseline_location_on_24;
      headline = R.string.XgramR6B1AllowLocationAlways;
    } else if (message == R.string.NoLocationAccess) {
      icon = R.drawable.baseline_location_on_24;
      headline = R.string.XgramR6B1AllowLocation;
    } else {
      icon = R.drawable.baseline_settings_24;
      headline = R.string.MicrophonePermission;
    }
    return showSettingsPrompt(c, icon, Lang.getString(headline), Lang.getString(message), Intents::openPermissionSettings);
  }

  /** A permission or setting the user has to turn on in the system settings: [Not now] [Open settings]. */
  public static boolean showSettingsPrompt (@Nullable ViewController<?> c, @DrawableRes int icon, @NonNull CharSequence headline, @NonNull CharSequence reason, @NonNull Runnable openSettings) {
    if (c == null || !XgramExpressive.isEnabled() || c.isDestroyed()) {
      return false;
    }
    return ModernOptions.xgramShowHeroConfirm(c, icon, headline, reason,
      Lang.getString(R.string.NotNow), null,
      Lang.getString(R.string.XgramR6B1OpenSettings), openSettings) != null;
  }

  /** Google Maps is needed: [Not now] [Install]. */
  public static boolean showMissingGoogleMaps (@Nullable ViewController<?> c) {
    if (c == null || !XgramExpressive.isEnabled()) {
      return false;
    }
    return ModernOptions.xgramShowHeroConfirm(c, R.drawable.baseline_map_24, Lang.getString(R.string.XgramR6B1GetMaps), Lang.getString(R.string.NoGoogleMaps),
      Lang.getString(R.string.NotNow), null,
      Lang.getString(R.string.Install), () -> Intents.openGooglePlay("com.google.android.apps.maps")) != null;
  }

  /** A notice with nothing to decide (no microphone, offline): the hero, the text and one filled OK. */
  public static boolean showNotice (@Nullable ViewController<?> c, @DrawableRes int icon, @NonNull CharSequence headline, @NonNull CharSequence text, boolean error) {
    if (c == null || !XgramExpressive.isEnabled() || c.isDestroyed()) {
      return false;
    }
    ModernOptions.XgramHero hero = new ModernOptions.XgramHero(icon, headline, text).confirm(Lang.getOK(), null);
    if (error) {
      hero.destructive();
    }
    return hero.show(c) != null;
  }

  // Calls

  /** The microphone permission was refused for a call. */
  public static boolean showCallMicrophone (@Nullable ViewController<?> c) {
    return showSettingsPrompt(c, R.drawable.baseline_mic_24, Lang.getString(R.string.XgramR6B1AllowMicrophone), Lang.getString(R.string.MicrophonePermissionDesc), Intents::openPermissionSettings);
  }

  /** The device has no microphone. */
  public static boolean showNoMicrophone (@Nullable ViewController<?> c) {
    return showNotice(c, R.drawable.baseline_mic_24, Lang.getString(R.string.XgramR6B1NoMicrophone), Lang.getString(R.string.MicrophoneMissing), true);
  }

  /** No connection for a call: airplane mode ([Not now] [Open settings]) or simply offline (OK). */
  public static boolean showCallOffline (@Nullable ViewController<?> c, boolean airplaneMode) {
    if (airplaneMode) {
      return showSettingsPrompt(c, R.drawable.baseline_airplane_24, Lang.getString(R.string.XgramR6B1AirplaneMode), Lang.getString(R.string.VoipOfflineAirplane), Intents::openAirplaneSettings);
    }
    return showNotice(c, R.drawable.xgram_cloud_off_24, Lang.getString(R.string.XgramR6B1Offline), Lang.getString(R.string.VoipOffline), false);
  }

  /** The call could not start: a snackbar with "Try again" ({@code redial}). False when no snackbar could show. */
  public static boolean showCallFailed (@Nullable Runnable redial) {
    if (!XgramExpressive.isEnabled()) {
      return false;
    }
    return XgramSnackbar.show(Lang.getString(R.string.XgramR6B1CallFailed), Toast.LENGTH_LONG,
      redial != null ? Lang.getString(R.string.XgramR6B1TryAgain) : null, redial);
  }

  // Voice chats

  /**
   * Voice chats (group calls) are not in the app yet: a snackbar saying so, with "Open in browser" for an invitation
   * link ({@code invitation}, a t.me/username?videochat link).
   */
  public static boolean showVoiceChatUnsupported (@Nullable TdApi.InternalLinkTypeVideoChat invitation) {
    if (!XgramExpressive.isEnabled()) {
      return false;
    }
    final String url = invitation != null ? voiceChatUrl(invitation) : null;
    return XgramSnackbar.show(Lang.getString(R.string.XgramR6B1VoiceChatsUnsupported), Toast.LENGTH_LONG,
      url != null ? Lang.getString(R.string.XgramR6B1OpenInBrowser) : null,
      url != null ? () -> Intents.openUriInBrowser(Uri.parse(url)) : null);
  }

  @Nullable
  private static String voiceChatUrl (@NonNull TdApi.InternalLinkTypeVideoChat invitation) {
    if (StringUtils.isEmpty(invitation.chatUsername)) {
      return null;
    }
    StringBuilder b = new StringBuilder("https://t.me/").append(invitation.chatUsername)
      .append(invitation.isLiveStream ? "?livestream" : "?videochat");
    if (!StringUtils.isEmpty(invitation.inviteHash)) {
      b.append('=').append(Uri.encode(invitation.inviteHash));
    }
    return b.toString();
  }
}
