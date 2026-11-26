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

import androidx.annotation.ColorInt;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.theme.ThemeManager;

/**
 * Pure black (AMOLED) night mode for Expressive design. While it is on and the app is dark, the surface roles fold into
 * three levels: every page-level surface (pages, app bars, panes, the rail, sheets, the composer bar, the chat
 * background without a wallpaper, the status and navigation bars) is black, cards on it (list tiles, incoming bubbles,
 * dialogs, pinned and join request bars) one near-black step, and chips, pills and text fields inside those cards, and
 * menus floating over them, the next step, so nothing is grey on grey. Content colors (onSurface, outlines) and the accent containers stay the
 * dynamic scheme's, and the light scheme is never changed. Everything drawn with the surface roles follows: Telegram X's
 * colors (XgramM3Colors through XgramExpressive.colorFor) and the Compose pages (XgramM3Theme).
 */
public final class XgramAmoled {
  private XgramAmoled () { }

  // Switch

  /** Night surfaces: the Material You ones, Pure black, or Night blue (Telegram X's Night Blue theme's blue-grey). */
  public static final int STYLE_DEFAULT = 0, STYLE_PURE_BLACK = 1, STYLE_NIGHT_BLUE = 2;

  /** -1: not read yet, else the style. */
  private static volatile int savedState = -1;

  /** The switch is only offered with Expressive design: its colors are the ones made black. */
  public static boolean isAvailable () {
    return XgramExpressive.isEnabled();
  }

  /** The saved style, kept while Expressive design is off. Cached. */
  public static int savedStyle () {
    int state = savedState;
    if (state == -1) {
      state = XgramSettings.instance().nightSurfaces();
      if (state != STYLE_PURE_BLACK && state != STYLE_NIGHT_BLUE) {
        state = STYLE_DEFAULT;
      }
      savedState = state;
    }
    return state;
  }

  /**
   * Pure black or Night blue is saved: both fold the surface roles into the same three levels (page, card, chip),
   * black and near-black or Telegram X's Night Blue, so everything placed by level works the same with either.
   */
  public static boolean isSaved () {
    return savedStyle() != STYLE_DEFAULT;
  }

  /** Night blue is the saved style (it then draws whenever {@link #isActive()}). */
  public static boolean isNightBlue () {
    return savedStyle() == STYLE_NIGHT_BLUE;
  }

  /** Pure black itself is drawing: for the few spots that are black on purpose (the splash, the maps' land). */
  public static boolean isBlackActive () {
    return isActive() && !isNightBlue();
  }

  /** Pure black applies to the dark scheme: Expressive design and the switch are on. Cheap, safe in onDraw. */
  public static boolean isEnabled () {
    return isSaved() && XgramExpressive.isEnabled();
  }

  /** Pure black is on and the current theme is dark, so the page roles draw black now. Cheap, safe in onDraw. */
  public static boolean isActive () {
    return isEnabled() && ThemeManager.instance().isCurrentThemeDark();
  }

  /**
   * {@code pureBlackRole} while pure black draws ({@link #isActive()}), else {@code role}: for a call site whose M3
   * role lands on the wrong level once the page roles are black. Only for a call site that is always drawn on the
   * same level: a view that can sit on a card (a tile, a dialog) as well as on the page must keep its role.
   */
  public static int role (int role, int pureBlackRole) {
    return isActive() ? pureBlackRole : role;
  }

  /**
   * The role of a floating container (a menu, popup or rich tooltip) that M3 draws in {@code role}: surfaceContainer
   * and surfaceContainerLow are black with pure black, like the page under the container, where no shadow shows, and
   * the card step is what these containers float over (incoming bubbles, the pinned bar, list tiles), so it takes the
   * chip step (surfaceContainerHighest, #1E1E1E) instead, distinct from both. Nothing drawn inside these containers
   * uses that role: their selected pills are secondaryContainer.
   */
  public static int floatingRole (int role) {
    return isActive() ? XgramExpressive.SURFACE_CONTAINER_HIGHEST : role;
  }

  /**
   * Saves the switch and repaints the app at once with the new surfaces, as after a wallpaper color change: the role
   * caches are dropped and every theme listener (views, bars, Compose pages) is told the colors changed.
   */
  public static void setEnabled (boolean on) {
    setStyle(on ? STYLE_PURE_BLACK : STYLE_DEFAULT);
  }

  /** As {@link #setEnabled}, for any style: Pure black and Night blue exclude each other. */
  public static void setStyle (int style) {
    XgramSettings.instance().setNightSurfaces(style);
    savedState = style;
    XgramExpressive.invalidateColors();
    try {
      ThemeManager.xgramNotifyColorsChanged();
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Pure black refresh failed", t);
    }
  }

  // Surfaces

  /** Steps above black keep a trace of the Material You hue: half the dynamic surface's chroma, at most this (HCT). */
  private static final float TINT_CHROMA_MAX = 2f, TINT_CHROMA_SCALE = .5f;

  /** The three pure black levels (sRGB grey): the page, a card on it, a chip inside a card. */
  private static final int GREY_PAGE = 0x00, GREY_CARD = 0x12, GREY_CHIP = 0x1E;

  /**
   * Night blue's three levels, Telegram X's Night Blue theme: the page its chat and page background (#151D26), the card
   * its list rows and incoming bubbles (#1C2733), the chip its header (#222E3C).
   */
  private static final int BLUE_PAGE = 0xff151d26, BLUE_CARD = 0xff1c2733, BLUE_CHIP = 0xff222e3c;

  /**
   * The grey level (sRGB, the same in red, green and blue) of each surface role with pure black, or -1 where the role
   * is not a surface. The roles fold by what they are drawn as, not by the M3 tone order (stepping each container up
   * from black left the pages, app bars and sheets grey, #121212 to #262626, and the tiles on them lighter still):
   * <ul>
   * <li>Page, black: surface and dim (pages, the chat), surfaceContainerLow (sheets, the drawer, the emoji and keyboard
   * panels) and surfaceContainer (the settings page under its tiles, app bars, the rail, the two-pane gap, the attach
   * bar, the navigation and status bars).</li>
   * <li>Card, #121212: surfaceBright (list tiles) and surfaceContainerHigh (incoming bubbles, the pinned and join
   * request bars, chips on the chat background, dialogs).</li>
   * <li>Chip, #1E1E1E: surfaceContainerHighest (filled text fields, placeholders, muted badges, switch tracks, bot
   * keyboard buttons, and floating menus and popups through {@link #floatingRole}) and
   * surfaceContainerLowest. Nothing is darker than black, so lowest, which M3 uses for a recessed chip inside a raised
   * container (reactions in a bubble), is the chip step above the card instead of a black hole in it.</li>
   * </ul>
   */
  private static int greyOf (int role) {
    switch (role) {
      case XgramExpressive.SURFACE:
      case XgramExpressive.SURFACE_DIM:
      case XgramExpressive.SURFACE_CONTAINER_LOW:
      case XgramExpressive.SURFACE_CONTAINER:
        return GREY_PAGE;
      case XgramExpressive.SURFACE_BRIGHT:
      case XgramExpressive.SURFACE_CONTAINER_HIGH:
        return GREY_CARD;
      case XgramExpressive.SURFACE_CONTAINER_HIGHEST:
      case XgramExpressive.SURFACE_CONTAINER_LOWEST:
        return GREY_CHIP;
      default:
        return -1;
    }
  }

  /** Whether pure black replaces {@code role} (the surface and surface container roles). */
  public static boolean isSurfaceRole (int role) {
    return greyOf(role) != -1;
  }

  /**
   * The color of {@code role} in the light or the dark scheme, {@code color} being the dynamic one: the pure black
   * step while it is on and {@code dark}, else {@code color} itself. Allocates only when the dynamic color changed.
   */
  @ColorInt
  public static int override (int role, @ColorInt int color, boolean dark) {
    if (!dark || !isEnabled()) {
      return color;
    }
    return pureBlack(role, color);
  }

  /** As {@link #override(int, int, boolean)}, in the scheme of the current theme (day or night). */
  @ColorInt
  public static int override (int role, @ColorInt int color) {
    if (!isEnabled()) {
      return color;
    }
    return override(role, color, ThemeManager.instance().isCurrentThemeDark());
  }

  private static final class Step {
    final int in, out;

    Step (int in, int out) {
      this.in = in;
      this.out = out;
    }
  }

  /** The last step worked out per role, for the dynamic color it was made from. */
  private static final Step[] steps = new Step[XgramExpressive.ROLE_COUNT];

  /**
   * The pure black color of {@code role} whatever the switch: black for the page roles, the card or chip step tinted
   * very slightly with the hue of {@code dynamicColor} (the dark scheme's own color of the role) for the others (see
   * {@link #greyOf}). Other roles keep {@code dynamicColor}.
   */
  @ColorInt
  public static int pureBlack (int role, @ColorInt int dynamicColor) {
    int grey = greyOf(role);
    if (grey == -1) {
      return dynamicColor;
    }
    if (isNightBlue()) {
      return grey == GREY_PAGE ? BLUE_PAGE : grey == GREY_CARD ? BLUE_CARD : BLUE_CHIP;
    }
    if (grey == 0) {
      return 0xff000000;
    }
    Step step = steps[role];
    if (step != null && step.in == dynamicColor) {
      return step.out;
    }
    int out = tint(grey, dynamicColor);
    steps[role] = new Step(dynamicColor, out);
    return out;
  }

  /** The grey {@code grey} with the hue of {@code dynamicColor} and a trace of its chroma, at the grey's own tone. */
  @ColorInt
  private static int tint (int grey, @ColorInt int dynamicColor) {
    int plain = 0xff000000 | (grey << 16) | (grey << 8) | grey;
    try {
      float[] hct = new float[3];
      androidx.core.graphics.ColorUtils.colorToM3HCT(plain, hct);
      float tone = hct[2];
      androidx.core.graphics.ColorUtils.colorToM3HCT(dynamicColor | 0xff000000, hct);
      float chroma = Math.min(hct[1] * TINT_CHROMA_SCALE, TINT_CHROMA_MAX);
      if (chroma < .5f) {
        return plain; // a grey wallpaper scheme: nothing to tint with
      }
      return androidx.core.graphics.ColorUtils.M3HCTToColor(hct[0], chroma, tone) | 0xff000000;
    } catch (Throwable t) {
      return plain;
    }
  }
}
