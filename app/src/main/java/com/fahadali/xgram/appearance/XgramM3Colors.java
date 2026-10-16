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

import static com.fahadali.xgram.appearance.XgramExpressive.ERROR;
import static com.fahadali.xgram.appearance.XgramExpressive.ERROR_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.INVERSE_ON_SURFACE;
import static com.fahadali.xgram.appearance.XgramExpressive.INVERSE_PRIMARY;
import static com.fahadali.xgram.appearance.XgramExpressive.INVERSE_SURFACE;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_ERROR;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_ERROR_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_TERTIARY;
import static com.fahadali.xgram.appearance.XgramExpressive.ON_TERTIARY_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.OUTLINE;
import static com.fahadali.xgram.appearance.XgramExpressive.OUTLINE_VARIANT;
import static com.fahadali.xgram.appearance.XgramExpressive.PRIMARY;
import static com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.SECONDARY;
import static com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_LOW;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_LOWEST;
import static com.fahadali.xgram.appearance.XgramExpressive.SURFACE_DIM;
import static com.fahadali.xgram.appearance.XgramExpressive.TERTIARY;
import static com.fahadali.xgram.appearance.XgramExpressive.TERTIARY_CONTAINER;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;

import me.vkryl.core.ColorUtils;

/**
 * Expressive design colors for the whole app: while it is on, Theme.getColor resolves Telegram X's color ids to the
 * Material 3 color roles of the device's dynamic (wallpaper) scheme, so every screen, whatever theme is picked, draws
 * with Material You surfaces, containers and accents. Accent-like colors (avatars, names, attach buttons) keep their
 * hue family but are harmonized towards the primary color (Material Blend.harmonize). Ids that are not listed stay the
 * theme's own color. The theme classes themselves stay raw, so the theme editor and previews show real theme colors.
 */
public final class XgramM3Colors {
  private XgramM3Colors () { }

  private static final int K_ROLE = 1, K_OVER = 2, K_TRANSPARENT = 3, K_HARMONIZE = 4;
  private static final int SIZE = 512;

  /** M3 state layer opacities (androidx.compose.material3 StateTokens): pressed .10, dragged .16. */
  private static final float STATE_LAYER_PRESSED = XgramExpressive.STATE_PRESSED, STATE_LAYER_DRAGGED = XgramExpressive.STATE_DRAGGED;

  /** kind (4 bits) | role << 4 (6 bits) | base role << 10 (6 bits) | alpha << 16 (8 bits); 0 = not mapped. */
  private static final int[] TABLE = new int[SIZE];

  private static int alpha255 (float alpha) {
    return Math.max(0, Math.min(255, Math.round(alpha * 255f)));
  }

  private static void put (int colorId, int entry) {
    if (colorId > 0 && colorId < SIZE) {
      TABLE[colorId] = entry;
    }
  }

  private static void role (int role, int... colorIds) {
    roleAlpha(role, 1f, colorIds);
  }

  private static void roleAlpha (int role, float alpha, int... colorIds) {
    int entry = K_ROLE | (role << 4) | (alpha255(alpha) << 16);
    for (int colorId : colorIds) {
      put(colorId, entry);
    }
  }

  private static void over (int role, float alpha, int baseRole, int... colorIds) {
    int entry = K_OVER | (role << 4) | (baseRole << 10) | (alpha255(alpha) << 16);
    for (int colorId : colorIds) {
      put(colorId, entry);
    }
  }

  private static void transparent (int... colorIds) {
    for (int colorId : colorIds) {
      put(colorId, K_TRANSPARENT);
    }
  }

  private static void harmonize (int... colorIds) {
    for (int colorId : colorIds) {
      put(colorId, K_HARMONIZE);
    }
  }

  static {
    // Surfaces
    role(SURFACE, ColorId.filling, ColorId.chatBackground, ColorId.bubble_chatBackground);
    role(SURFACE_CONTAINER, ColorId.background, ColorId.headerBackground, ColorId.ivHeader, ColorId.iv_header,
      ColorId.statusBarLegacy, ColorId.passcode);
    role(SURFACE_CONTAINER_LOW, ColorId.drawer, ColorId.chatKeyboard);
    role(SURFACE_CONTAINER_HIGH, ColorId.overlayFilling, ColorId.previewBackground, ColorId.headerLightBackground,
      ColorId.headerPickerBackground, ColorId.unread, ColorId.bubble_unread, ColorId.bubble_unread_noWallpaper,
      ColorId.iv_preBlockBackground, ColorId.iv_textCodeBackground, ColorId.iv_chatLinkBackground);
    role(SURFACE_CONTAINER_HIGHEST, ColorId.placeholder, ColorId.headerPlaceholder, ColorId.badgeMuted,
      ColorId.messageSwipeBackground, ColorId.sectionedScrollBar, ColorId.iv_textCodeBackgroundPressed,
      ColorId.chatKeyboardButton);
    roleAlpha(SURFACE_CONTAINER_HIGH, .92f, ColorId.bubble_date, ColorId.bubble_overlay);
    // The time chip under stickers, emoji and round videos (and their duration chip): the date chip's pill with
    // onSurfaceVariant text instead of white on 25% black over the solid surface
    roleAlpha(SURFACE_CONTAINER_HIGH, .92f, ColorId.bubble_mediaTime, ColorId.bubble_mediaTime_noWallpaper);
    role(ON_SURFACE_VARIANT, ColorId.bubble_mediaTimeText, ColorId.bubble_mediaTimeText_noWallpaper);
    transparent(ColorId.bubble_date_noWallpaper, ColorId.bubble_overlay_noWallpaper, ColorId.statusBar,
      ColorId.tooltip_outline, ColorId.bubbleIn_outline, ColorId.bubbleOut_outline);
    // Pressed rows that swap their fill (state list drawables): onSurface .10 over the surface, opaque.
    over(ON_SURFACE, STATE_LAYER_PRESSED, SURFACE, ColorId.fillingPressed);

    // Text and icons
    role(ON_SURFACE, ColorId.text, ColorId.headerText, ColorId.headerIcon, ColorId.headerLightText,
      ColorId.headerPickerText, ColorId.drawerText, ColorId.ivHeaderIcon, ColorId.passcodeText, ColorId.iv_text,
      ColorId.iv_pageTitle, ColorId.iv_textCode, ColorId.blockQuoteText, ColorId.bubbleIn_text,
      ColorId.bubbleIn_blockQuoteText);
    role(ON_SURFACE_VARIANT, ColorId.textLight, ColorId.textPlaceholder, ColorId.background_text,
      ColorId.background_textLight, ColorId.background_icon, ColorId.icon, ColorId.headerLightIcon,
      ColorId.chatListIcon, ColorId.controlInactive, ColorId.inputInactive, ColorId.playerButton,
      ColorId.statusBarContent, ColorId.statusBarLegacyContent, ColorId.passcodeIcon, ColorId.headerTabInactiveText,
      ColorId.badgeMutedText, ColorId.messageSwipeContent, ColorId.bubble_dateText,
      ColorId.bubble_dateText_noWallpaper, ColorId.bubble_overlayText, ColorId.bubble_overlayText_noWallpaper,
      ColorId.bubble_messageCheckOutline, ColorId.bubble_messageCheckOutlineNoWallpaper, ColorId.bubbleIn_time,
      ColorId.iv_pageSubtitle, ColorId.iv_pageAuthor, ColorId.iv_pageFooter, ColorId.iv_caption, ColorId.iv_icon);
    role(OUTLINE, ColorId.iconLight, ColorId.inlineOutline, ColorId.chatListMute, ColorId.togglerInactive,
      ColorId.headerBadgeMuted);
    role(OUTLINE_VARIANT, ColorId.separator, ColorId.chatSeparator, ColorId.bubble_chatSeparator,
      ColorId.shareSeparator, ColorId.iv_separator, ColorId.seekReady, ColorId.waveformInactive, ColorId.introSection,
      ColorId.bubbleIn_separator);

    // Primary
    role(PRIMARY, ColorId.textNeutral, ColorId.textLink, ColorId.textSearchQueryHighlight, ColorId.iconActive,
      ColorId.progress, ColorId.controlActive, ColorId.checkActive, ColorId.sliderActive, ColorId.togglerActive,
      ColorId.togglerActiveBackground, ColorId.togglerPositive, ColorId.togglerPositiveBackground,
      ColorId.inputActive, ColorId.inputPositive, ColorId.inlineText, ColorId.inlineIcon, ColorId.seekDone,
      ColorId.playerButtonActive, ColorId.ticks, ColorId.ticksRead, ColorId.chatListAction, ColorId.chatListVerify,
      ColorId.badge, ColorId.headerBadge, ColorId.chatSendButton, ColorId.unreadText, ColorId.bubble_unreadText,
      ColorId.bubble_unreadText_noWallpaper, ColorId.messageVerticalLine, ColorId.messageNeutralFillingContent,
      ColorId.messageAuthor, ColorId.file, ColorId.fileAttach, ColorId.waveformActive, ColorId.blockQuoteLine,
      ColorId.introSectionActive, ColorId.headerTabActive, ColorId.fillingPositive, ColorId.iv_textLink,
      ColorId.iv_blockQuoteLine, ColorId.bubbleIn_progress, ColorId.bubbleIn_textLink,
      ColorId.bubbleIn_fillingPositive, ColorId.bubbleIn_blockQuoteLine, ColorId.bubbleOut_textLink,
      ColorId.bubbleOut_chatVerticalLine, ColorId.bubbleOut_inlineIcon, ColorId.bubbleOut_inlineOutline,
      ColorId.bubbleOut_waveformActive, ColorId.bubbleOut_file, ColorId.bubbleOut_ticks, ColorId.bubbleOut_ticksRead,
      ColorId.bubbleOut_messageAuthor, ColorId.bubbleOut_fillingPositive,
      ColorId.bubbleOut_blockQuoteLine);
    // State layers and highlights (M3 state layer tokens: hover .08, focus .10, pressed .10, dragged .16)
    // Text selection: Compose material3 TextSelectionColors, primary at .40.
    roleAlpha(PRIMARY, .40f, ColorId.textSelectionHighlight);
    // A pressed link is a pressed state layer in the link's own color (primary) at .10.
    roleAlpha(PRIMARY, STATE_LAYER_PRESSED, ColorId.textLinkPressHighlight, ColorId.iv_textLinkPressHighlight,
      ColorId.bubbleIn_textLinkPressHighlight, ColorId.bubbleOut_textLinkPressHighlight);
    // Selected messages and the jump-to-message flash share these ids: primary at .16 (the dragged/selected layer).
    roleAlpha(PRIMARY, STATE_LAYER_DRAGGED, ColorId.messageSelection, ColorId.bubble_messageSelection,
      ColorId.bubble_messageSelectionNoWallpaper);
    role(ON_PRIMARY, ColorId.controlContent, ColorId.checkContent, ColorId.togglerPositiveContent,
      ColorId.inlineContentActive, ColorId.badgeText, ColorId.fillingPositiveContent, ColorId.fileContent,
      ColorId.bubbleIn_fillingPositiveContent, ColorId.bubbleOut_fileContent,
      ColorId.bubbleOut_fillingPositiveContent);
    role(PRIMARY_CONTAINER, ColorId.circleButtonRegular, ColorId.circleButtonActive, ColorId.circleButtonTheme,
      ColorId.circleButtonNewChat, ColorId.circleButtonNewGroup, ColorId.circleButtonNewChannel,
      ColorId.circleButtonNewSecret, ColorId.headerButton, ColorId.promo, ColorId.bubbleOut_background);
    role(ON_PRIMARY_CONTAINER, ColorId.circleButtonRegularIcon, ColorId.circleButtonActiveIcon,
      ColorId.circleButtonThemeIcon, ColorId.circleButtonNewChatIcon, ColorId.circleButtonNewGroupIcon,
      ColorId.circleButtonNewChannelIcon, ColorId.circleButtonNewSecretIcon, ColorId.headerButtonIcon,
      ColorId.promoContent, ColorId.bubbleOut_text, ColorId.bubbleOut_inlineText,
      ColorId.bubbleOut_chatNeutralFillingContent, ColorId.bubbleOut_blockQuoteText);
    roleAlpha(ON_PRIMARY_CONTAINER, .70f, ColorId.bubbleOut_time, ColorId.bubbleOut_progress);
    roleAlpha(ON_PRIMARY_CONTAINER, .30f, ColorId.bubbleOut_waveformInactive);
    roleAlpha(ON_PRIMARY_CONTAINER, .20f, ColorId.bubbleOut_separator);
    over(ON_PRIMARY_CONTAINER, STATE_LAYER_PRESSED, PRIMARY_CONTAINER, ColorId.bubbleOut_pressed);
    role(SURFACE_CONTAINER_HIGH, ColorId.bubbleIn_background);
    over(ON_SURFACE, STATE_LAYER_PRESSED, SURFACE_CONTAINER_HIGH, ColorId.bubbleIn_pressed);
    // Surface small FAB: container high, primary icon
    role(SURFACE_CONTAINER_HIGH, ColorId.circleButtonChat, ColorId.circleButtonOverlay);
    role(PRIMARY, ColorId.circleButtonChatIcon, ColorId.circleButtonOverlayIcon);

    // Secondary: selected containers (filter chips, chosen reactions, nav and tab indicators) are secondaryContainer
    // with onSecondaryContainer content, in and out of bubbles alike.
    role(SECONDARY_CONTAINER, ColorId.sliderInactive, ColorId.seekEmpty, ColorId.fillingActive,
      ColorId.bubbleIn_fillingActive, ColorId.bubbleOut_fillingActive,
      ColorId.profileSectionActive, ColorId.sectionedScrollBarActive, ColorId.bubble_button,
      ColorId.bubble_button_noWallpaper);
    role(ON_SECONDARY_CONTAINER, ColorId.fillingActiveContent, ColorId.bubbleIn_fillingActiveContent,
      ColorId.bubbleOut_fillingActiveContent, ColorId.headerTabActiveText,
      ColorId.profileSectionActiveContent, ColorId.sectionedScrollBarActiveContent, ColorId.bubble_buttonText,
      ColorId.bubble_buttonText_noWallpaper);
    roleAlpha(ON_SECONDARY_CONTAINER, STATE_LAYER_PRESSED, ColorId.bubble_buttonRipple,
      ColorId.bubble_buttonRipple_noWallpaper);
    // Switch, unselected: track surfaceContainerHighest (handle outline is togglerInactive above).
    role(SURFACE_CONTAINER_HIGHEST, ColorId.togglerInactiveBackground);

    // Error and inverse
    role(ERROR, ColorId.textNegative, ColorId.iconNegative, ColorId.inputNegative, ColorId.togglerNegative,
      ColorId.togglerNegativeBackground, ColorId.badgeFailed, ColorId.headerBadgeFailed, ColorId.fillingNegative,
      ColorId.messageNegativeLine, ColorId.headerRemoveBackgroundHighlight);
    role(ON_ERROR, ColorId.badgeFailedText, ColorId.togglerNegativeContent, ColorId.messageNegativeLineContent);
    role(ERROR_CONTAINER, ColorId.headerRemoveBackground);
    role(INVERSE_SURFACE, ColorId.snackbarUpdate, ColorId.tooltip);
    role(INVERSE_ON_SURFACE, ColorId.snackbarUpdateText, ColorId.tooltip_text);
    role(INVERSE_PRIMARY, ColorId.snackbarUpdateAction, ColorId.tooltip_textLink);
    roleAlpha(INVERSE_PRIMARY, STATE_LAYER_PRESSED, ColorId.tooltip_textLinkPressHighlight);
    role(TERTIARY_CONTAINER, ColorId.iv_textMarkedBackground);
    role(ON_TERTIARY_CONTAINER, ColorId.iv_textMarked);

    // Accents: the theme's own color, its hue turned towards the primary color
    harmonize(ColorId.avatarRed, ColorId.avatarRed_big, ColorId.avatarOrange, ColorId.avatarOrange_big,
      ColorId.avatarYellow, ColorId.avatarYellow_big, ColorId.avatarGreen, ColorId.avatarGreen_big,
      ColorId.avatarCyan, ColorId.avatarCyan_big, ColorId.avatarBlue, ColorId.avatarBlue_big,
      ColorId.avatarViolet, ColorId.avatarViolet_big, ColorId.avatarPink, ColorId.avatarPink_big,
      ColorId.avatarInactive, ColorId.avatarInactive_big);
    // Special avatars are tonal (Google Messages, Contacts): a container with an on-container glyph, see
    // avatarIconColorId. Archive is secondary, Saved Messages and Replies primary.
    role(SECONDARY_CONTAINER, ColorId.avatarArchive, ColorId.avatarArchivePinned);
    role(PRIMARY_CONTAINER, ColorId.avatarSavedMessages, ColorId.avatarSavedMessages_big, ColorId.avatarReplies,
      ColorId.avatarReplies_big);
    harmonize(ColorId.nameRed, ColorId.nameOrange, ColorId.nameYellow, ColorId.nameGreen, ColorId.nameCyan,
      ColorId.nameBlue, ColorId.nameViolet, ColorId.namePink, ColorId.nameInactive);
    harmonize(ColorId.lineRed, ColorId.lineOrange, ColorId.lineYellow, ColorId.lineGreen, ColorId.lineCyan,
      ColorId.lineBlue, ColorId.lineViolet, ColorId.linePink, ColorId.lineInactive);
    harmonize(ColorId.attachPhoto, ColorId.attachFile, ColorId.attachLocation, ColorId.attachContact,
      ColorId.attachInlineBot, ColorId.fileYellow, ColorId.fileGreen, ColorId.fileRed);
    harmonize(ColorId.online, ColorId.iconPositive, ColorId.textSecure);
    harmonize(ColorId.messageCorrectFilling, ColorId.messageCorrectChosenFilling,
      ColorId.bubbleOut_chatCorrectFilling, ColorId.bubbleOut_chatCorrectChosenFilling);
    harmonize(ColorId.messageAuthorPsa, ColorId.bubbleOut_messageAuthorPsa);
    harmonize(ColorId.headerBarCallIncoming, ColorId.headerBarCallActive, ColorId.headerBarCallMuted);
  }

  /**
   * The glyph color id of an avatar placeholder in the accent {@code accentColorId}: with Expressive design the tonal
   * special avatars (Archive, Saved Messages, Replies) take the on-container color of their container (ids that map
   * to ON_SECONDARY_CONTAINER and ON_PRIMARY_CONTAINER above) instead of white; everything else keeps {@code fallback}.
   */
  public static int avatarIconColorId (int accentColorId, int fallback) {
    if (!XgramExpressive.isEnabled()) {
      return fallback;
    }
    switch (accentColorId) {
      case TdlibAccentColor.InternalId.ARCHIVE:
      case TdlibAccentColor.InternalId.ARCHIVE_PINNED:
        return ColorId.text; // PorterDuffPaint only tints the ids it was generated for (fillingActiveContent crashed)
      case TdlibAccentColor.InternalId.SAVED_MESSAGES:
      case TdlibAccentColor.InternalId.REPLIES:
        return ColorId.iconActive; // as above: a PorterDuffPaint id
      default:
        return fallback;
    }
  }

  /** Whether the id resolves to a role while Expressive design is on. Cheap. */
  public static boolean isMapped (int colorId) {
    return colorId > 0 && colorId < SIZE && TABLE[colorId] != 0;
  }

  // Content colors, for state layers (a ripple is the content color of the surface it sits on)

  /** The M3 content role drawn on the container or surface role {@code role}, or -1 when {@code role} is no container. */
  public static int onRole (int role) {
    switch (role) {
      case PRIMARY: return ON_PRIMARY;
      case PRIMARY_CONTAINER: return ON_PRIMARY_CONTAINER;
      case SECONDARY: return ON_SECONDARY;
      case SECONDARY_CONTAINER: return ON_SECONDARY_CONTAINER;
      case TERTIARY: return ON_TERTIARY;
      case TERTIARY_CONTAINER: return ON_TERTIARY_CONTAINER;
      case ERROR: return ON_ERROR;
      case ERROR_CONTAINER: return ON_ERROR_CONTAINER;
      case INVERSE_SURFACE: return INVERSE_ON_SURFACE;
      case SURFACE:
      case SURFACE_DIM:
      case SURFACE_BRIGHT:
      case SURFACE_CONTAINER_LOWEST:
      case SURFACE_CONTAINER_LOW:
      case SURFACE_CONTAINER:
      case SURFACE_CONTAINER_HIGH:
      case SURFACE_CONTAINER_HIGHEST:
        return ON_SURFACE;
      default:
        return -1;
    }
  }

  /**
   * The content role of the background {@code colorId} resolves to while Expressive design is on (onSurface for the
   * surfaces, onPrimary for primary and so on), or -1 when the id is not a container in the table. An id drawn as a
   * layer over a base role (K_OVER) takes the content role of that base.
   */
  public static int contentRoleOf (int colorId) {
    if (colorId <= 0 || colorId >= SIZE) {
      return -1;
    }
    int entry = TABLE[colorId];
    switch (entry & 0xf) {
      case K_ROLE:
        return ((entry >>> 16) & 0xff) == 255 ? onRole((entry >>> 4) & 0x3f) : -1;
      case K_OVER:
        return onRole((entry >>> 10) & 0x3f);
      default:
        return -1;
    }
  }

  // Theme darkness

  private static final class DarkCache {
    final ThemeDelegate theme;
    final boolean dark;

    DarkCache (ThemeDelegate theme, boolean dark) {
      this.theme = theme;
      this.dark = dark;
    }
  }

  private static volatile DarkCache darkCache;

  private static boolean isDark (ThemeDelegate theme) {
    DarkCache cache = darkCache;
    if (cache == null || cache.theme != theme) {
      cache = new DarkCache(theme, theme.isDark());
      darkCache = cache;
    }
    return cache.dark;
  }

  /**
   * The color of {@code colorId} in {@code theme} (the theme Theme.getColor would read). During a theme switch (the
   * temporary theme) the light and dark schemes are blended by the theme's darkness, so day/night animates.
   */
  @ColorInt
  public static int resolve (ThemeDelegate theme, int colorId) {
    int entry = TABLE[colorId];
    if ((entry & 0xf) == K_TRANSPARENT) {
      return 0;
    }
    if (theme.getId() == ThemeId.TEMPORARY) {
      float dark = theme.getProperty(PropertyId.DARK);
      if (dark <= 0f) {
        return resolve(theme, colorId, entry, false);
      }
      if (dark >= 1f) {
        return resolve(theme, colorId, entry, true);
      }
      return ColorUtils.fromToArgb(resolve(theme, colorId, entry, false), resolve(theme, colorId, entry, true), dark);
    }
    return resolve(theme, colorId, entry, isDark(theme));
  }

  @ColorInt
  private static int resolve (ThemeDelegate theme, int colorId, int entry, boolean dark) {
    int role = (entry >>> 4) & 0x3f;
    int alpha = (entry >>> 16) & 0xff;
    switch (entry & 0xf) {
      case K_ROLE: {
        int color = XgramExpressive.colorFor(role, dark);
        return alpha == 255 ? color : multiplyAlpha(color, alpha);
      }
      case K_OVER: {
        int base = XgramExpressive.colorFor((entry >>> 10) & 0x3f, dark);
        int color = multiplyAlpha(XgramExpressive.colorFor(role, dark), alpha);
        return androidx.core.graphics.ColorUtils.compositeColors(color, base);
      }
      case K_HARMONIZE:
        return harmonize(theme.getColor(colorId), colorId, dark);
      case K_TRANSPARENT:
        return 0;
    }
    return theme.getColor(colorId);
  }

  private static int multiplyAlpha (int color, int alpha) {
    int a = ((color >>> 24) * alpha + 127) / 255;
    return (color & 0x00ffffff) | (a << 24);
  }

  // Harmonize (Material Color Utilities, Blend.harmonize): the hue turns towards primary by min(difference / 2, 15).

  private static final int[][] harmonizedIn = new int[2][SIZE];
  private static final int[][] harmonizedOut = new int[2][SIZE];
  private static final boolean[][] harmonizedValid = new boolean[2][SIZE];
  private static final float[] primaryHue = new float[2];
  private static final boolean[] primaryHueValid = new boolean[2];

  @ColorInt
  private static int harmonize (@ColorInt int color, int colorId, boolean dark) {
    int alpha = color >>> 24;
    if (alpha == 0) {
      return color;
    }
    int scheme = dark ? 1 : 0;
    if (harmonizedValid[scheme][colorId] && harmonizedIn[scheme][colorId] == color) {
      return harmonizedOut[scheme][colorId];
    }
    if (!primaryHueValid[scheme]) {
      float[] hct = new float[3];
      androidx.core.graphics.ColorUtils.colorToM3HCT(XgramExpressive.colorFor(PRIMARY, dark) | 0xff000000, hct);
      primaryHue[scheme] = hct[0];
      primaryHueValid[scheme] = true;
    }
    float[] hct = new float[3];
    androidx.core.graphics.ColorUtils.colorToM3HCT(color | 0xff000000, hct);
    float from = hct[0], to = primaryHue[scheme];
    float difference = 180f - Math.abs(Math.abs(from - to) - 180f);
    float rotation = Math.min(difference * .5f, 15f);
    float increasing = sanitizeDegrees(to - from);
    float hue = sanitizeDegrees(from + rotation * (increasing <= 180f ? 1f : -1f));
    int result = androidx.core.graphics.ColorUtils.M3HCTToColor(hue, hct[1], hct[2]);
    result = (result & 0x00ffffff) | (alpha << 24);
    harmonizedIn[scheme][colorId] = color;
    harmonizedOut[scheme][colorId] = result;
    harmonizedValid[scheme][colorId] = true;
    return result;
  }

  private static float sanitizeDegrees (float degrees) {
    degrees = degrees % 360f;
    if (degrees < 0f) {
      degrees += 360f;
    }
    return degrees;
  }

  // Invalidation

  /** The theme's colors or darkness changed (theme editor, theme switch). */
  static void onThemeColorsChanged () {
    darkCache = null;
  }

  /** The dynamic colors changed: drops everything built from them. */
  public static void invalidate () {
    darkCache = null;
    for (int scheme = 0; scheme < 2; scheme++) {
      primaryHueValid[scheme] = false;
      java.util.Arrays.fill(harmonizedValid[scheme], false);
    }
  }
}
