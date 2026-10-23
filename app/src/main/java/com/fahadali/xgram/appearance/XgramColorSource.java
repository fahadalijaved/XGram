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

import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.ColorSpec;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.DynamicColor;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.DynamicScheme;
import com.fahadali.xgram.appearance.mcu.dynamiccolor.MaterialDynamicColors;
import com.fahadali.xgram.appearance.mcu.hct.Hct;
import com.fahadali.xgram.appearance.mcu.palettes.TonalPalette;
import com.fahadali.xgram.appearance.mcu.scheme.SchemeExpressive;
import com.fahadali.xgram.appearance.mcu.scheme.SchemeNeutral;
import com.fahadali.xgram.appearance.mcu.scheme.SchemeTonalSpot;
import com.fahadali.xgram.appearance.mcu.scheme.SchemeVibrant;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where Expressive design's colours come from (Appearance > Colours): the wallpaper's dynamic colours (the default), or
 * a colour picked in XGram. A picked colour is made into a scheme by Google's material-color-utilities (vendored in
 * {@code .mcu}) the way Android 16's Material 3 Expressive themes are (color spec 2025, phone), in one of four styles:
 * Tonal spot (Android's default), Vibrant, Expressive or Neutral. Only the colour's hue counts: each style sets its own
 * chroma, as Android's do. With a picked colour every place that reads the wallpaper's colours reads the generated
 * scheme instead: the Expressive roles (XgramExpressive.systemRole, so every Telegram X color through XgramM3Colors),
 * the Material You themes (XgramMaterialYou) and the Compose pages (XgramM3Theme). Only with Expressive design on; with
 * it off the wallpaper's colours are used and the choice is kept for later.
 */
public final class XgramColorSource {
  private XgramColorSource () { }

  // Swatches and styles

  /** The swatches: twelve hues around the wheel, from Google blue (the default). Opaque ARGB. */
  public static final int[] PRESETS = {
    0xff4285f4, 0xff039be5, 0xff00acc1, 0xff009688, 0xff34a853, 0xff9e9d24,
    0xfffbbc04, 0xfff57c00, 0xffea4335, 0xffe91e63, 0xff9c27b0, 0xff7e57c2
  };

  /** The swatches' names, for TalkBack. */
  @StringRes
  public static final int[] PRESET_NAMES = {
    R.string.XgramColorBlue, R.string.XgramColorSky, R.string.XgramColorCyan, R.string.XgramColorTeal,
    R.string.XgramColorGreen, R.string.XgramColorOlive, R.string.XgramColorYellow, R.string.XgramColorOrange,
    R.string.XgramColorRed, R.string.XgramColorPink, R.string.XgramColorPurple, R.string.XgramColorViolet
  };

  /** The style chips, in order (XgramSettings.COLOR_STYLE_*). */
  public static final int[] STYLES = {
    XgramSettings.COLOR_STYLE_TONAL_SPOT, XgramSettings.COLOR_STYLE_VIBRANT,
    XgramSettings.COLOR_STYLE_EXPRESSIVE, XgramSettings.COLOR_STYLE_NEUTRAL
  };

  @StringRes
  public static final int[] STYLE_NAMES = {
    R.string.XgramColorsStyleTonalSpot, R.string.XgramColorsStyleVibrant,
    R.string.XgramColorsStyleExpressive, R.string.XgramColorsStyleNeutral
  };

  /** A colour from More colours: its hue at this chroma and tone (the schemes only read the hue). */
  private static final double SEED_CHROMA = 48.0, SEED_TONE = 60.0;

  private static final ColorSpec.SpecVersion SPEC = ColorSpec.SpecVersion.SPEC_2025;
  private static final DynamicScheme.Platform PLATFORM = DynamicScheme.Platform.PHONE;

  // State

  /** The choice only applies with Expressive design (Android 13+), whose colours these are. */
  public static boolean isAvailable () {
    return XgramExpressive.isEnabled();
  }

  /** A picked colour is in use: Expressive design is on and Colours is Custom. Cheap (cached), safe in onDraw. */
  public static boolean isCustom () {
    return XgramExpressive.isEnabled() && XgramSettings.instance().colorSource() == XgramSettings.COLOR_SOURCE_CUSTOM;
  }

  /**
   * The picked colour and style as a seed key, never equal to a wallpaper's: XgramExpressive and XgramMaterialYou
   * compare it as they compare the wallpaper's colours, so a change made while XGram was away repaints on resume.
   */
  @NonNull
  public static String key () {
    XgramSettings settings = XgramSettings.instance();
    return "custom:" + Integer.toHexString(settings.colorSeed()) + ":" + settings.colorStyle();
  }

  /** The index of {@code seed} in {@link #PRESETS}, or -1 (a colour from More colours). */
  public static int presetIndex (int seed) {
    seed |= 0xff000000;
    for (int i = 0; i < PRESETS.length; i++) {
      if (PRESETS[i] == seed) {
        return i;
      }
    }
    return -1;
  }

  /** The HCT hue of a colour, 0 to 360. (Hct is plain math: safe on any thread.) */
  public static float hueOf (int argb) {
    return (float) Hct.fromInt(argb | 0xff000000).getHue();
  }

  /** The colour More colours saves for a hue. */
  public static int seedForHue (float hue) {
    double h = hue % 360.0;
    if (h < 0) {
      h += 360.0;
    }
    return Hct.from(h, SEED_CHROMA, SEED_TONE).toInt() | 0xff000000;
  }

  /** An opaque colour of HCT {@code hue} at one chroma and tone, for the hue slider's band. */
  public static int hueColor (float hue) {
    return Hct.from(hue, 60.0, 62.0).toInt() | 0xff000000;
  }

  /** The name TalkBack reads for a colour: the swatch's, or "Your colour" for one from More colours. */
  @StringRes
  public static int nameOf (int seed) {
    int index = presetIndex(seed);
    return index != -1 ? PRESET_NAMES[index] : R.string.XgramColorsPicked;
  }

  /** The value line of Colours on the classic page: Wallpaper or Custom, or why it is off. */
  @StringRes
  public static int summary () {
    if (!isAvailable()) {
      return R.string.XgramColorsOff;
    }
    return XgramSettings.instance().colorSource() == XgramSettings.COLOR_SOURCE_CUSTOM ? R.string.XgramColorsCustom : R.string.XgramColorsWallpaper;
  }

  // Changes (applied at once, as a wallpaper change is)

  /** Colours: {@link XgramSettings#COLOR_SOURCE_WALLPAPER} or {@link XgramSettings#COLOR_SOURCE_CUSTOM}. */
  public static void setSource (ViewController<?> c, int source) {
    XgramSettings settings = XgramSettings.instance();
    if (settings.colorSource() == source) {
      return;
    }
    settings.setColorSource(source);
    apply(c);
  }

  /** A swatch or More colours: the colour (Custom from now on) and, when {@code style} is not -1, the style too. */
  public static void setSeed (ViewController<?> c, int seed, int style) {
    XgramSettings settings = XgramSettings.instance();
    boolean changed = settings.colorSeed() != (seed | 0xff000000) || settings.colorSource() != XgramSettings.COLOR_SOURCE_CUSTOM ||
      (style != -1 && settings.colorStyle() != style);
    if (!changed) {
      return;
    }
    settings.setColorSeed(seed);
    if (style != -1) {
      settings.setColorStyle(style);
    }
    settings.setColorSource(XgramSettings.COLOR_SOURCE_CUSTOM);
    apply(c);
  }

  /** A style chip: XgramSettings.COLOR_STYLE_*. */
  public static void setStyle (ViewController<?> c, int style) {
    XgramSettings settings = XgramSettings.instance();
    if (settings.colorStyle() == style) {
      return;
    }
    settings.setColorStyle(style);
    apply(c);
  }

  private static boolean rebuilding, rebuildAgain;

  /**
   * The path a wallpaper change takes: every cached role is dropped and the app repaints (views, bars, Compose pages),
   * then the Material You themes, when they are in use, are built again from the new palettes. Taps that come while
   * the themes are being built are folded into one more build at the end.
   */
  private static void apply (ViewController<?> c) {
    XgramExpressive.onColorSourceChanged(c.context());
    if (XgramMaterialYou.isEnabled()) {
      rebuildThemes(c);
    }
  }

  private static void rebuildThemes (ViewController<?> c) {
    if (rebuilding) {
      rebuildAgain = true;
      return;
    }
    rebuilding = true;
    XgramMaterialYou.rebuild(c, () -> {
      rebuilding = false;
      if (rebuildAgain) {
        rebuildAgain = false;
        if (!c.isDestroyed()) {
          rebuildThemes(c);
        }
      }
    });
  }

  // Schemes. material-color-utilities builds a new DynamicColor on every MaterialDynamicColors call, and a scheme's
  // palettes and a DynamicColor keep caches in plain HashMaps: the scheme in use and its colours are only touched
  // holding LOCK; a preview builds its own scheme and colours, so it needs no lock and may run on any thread.

  private static final Object LOCK = new Object();
  private static final MaterialDynamicColors COLORS = new MaterialDynamicColors();

  @NonNull
  private static DynamicScheme newScheme (int seed, int style, boolean dark) {
    Hct source = Hct.fromInt(seed | 0xff000000);
    switch (style) {
      case XgramSettings.COLOR_STYLE_VIBRANT:
        return new SchemeVibrant(source, dark, 0.0, SPEC, PLATFORM);
      case XgramSettings.COLOR_STYLE_EXPRESSIVE:
        return new SchemeExpressive(source, dark, 0.0, SPEC, PLATFORM);
      case XgramSettings.COLOR_STYLE_NEUTRAL:
        return new SchemeNeutral(source, dark, 0.0, SPEC, PLATFORM);
      case XgramSettings.COLOR_STYLE_TONAL_SPOT:
      default:
        return new SchemeTonalSpot(source, dark, 0.0, SPEC, PLATFORM);
    }
  }

  /** The dynamic color of an Expressive role (XgramExpressive.PRIMARY ... SCRIM). */
  @NonNull
  private static DynamicColor roleColor (int role) {
    MaterialDynamicColors m = COLORS;
    switch (role) {
      case XgramExpressive.PRIMARY: return m.primary();
      case XgramExpressive.ON_PRIMARY: return m.onPrimary();
      case XgramExpressive.PRIMARY_CONTAINER: return m.primaryContainer();
      case XgramExpressive.ON_PRIMARY_CONTAINER: return m.onPrimaryContainer();
      case XgramExpressive.SECONDARY: return m.secondary();
      case XgramExpressive.ON_SECONDARY: return m.onSecondary();
      case XgramExpressive.SECONDARY_CONTAINER: return m.secondaryContainer();
      case XgramExpressive.ON_SECONDARY_CONTAINER: return m.onSecondaryContainer();
      case XgramExpressive.TERTIARY: return m.tertiary();
      case XgramExpressive.ON_TERTIARY: return m.onTertiary();
      case XgramExpressive.TERTIARY_CONTAINER: return m.tertiaryContainer();
      case XgramExpressive.ON_TERTIARY_CONTAINER: return m.onTertiaryContainer();
      case XgramExpressive.ERROR: return m.error();
      case XgramExpressive.ON_ERROR: return m.onError();
      case XgramExpressive.ERROR_CONTAINER: return m.errorContainer();
      case XgramExpressive.ON_ERROR_CONTAINER: return m.onErrorContainer();
      case XgramExpressive.SURFACE: return m.surface();
      case XgramExpressive.SURFACE_DIM: return m.surfaceDim();
      case XgramExpressive.SURFACE_BRIGHT: return m.surfaceBright();
      case XgramExpressive.SURFACE_CONTAINER_LOWEST: return m.surfaceContainerLowest();
      case XgramExpressive.SURFACE_CONTAINER_LOW: return m.surfaceContainerLow();
      case XgramExpressive.SURFACE_CONTAINER: return m.surfaceContainer();
      case XgramExpressive.SURFACE_CONTAINER_HIGH: return m.surfaceContainerHigh();
      case XgramExpressive.SURFACE_CONTAINER_HIGHEST: return m.surfaceContainerHighest();
      case XgramExpressive.ON_SURFACE: return m.onSurface();
      case XgramExpressive.ON_SURFACE_VARIANT: return m.onSurfaceVariant();
      case XgramExpressive.OUTLINE: return m.outline();
      case XgramExpressive.OUTLINE_VARIANT: return m.outlineVariant();
      case XgramExpressive.INVERSE_SURFACE: return m.inverseSurface();
      case XgramExpressive.INVERSE_ON_SURFACE: return m.inverseOnSurface();
      case XgramExpressive.INVERSE_PRIMARY: return m.inversePrimary();
      case XgramExpressive.SCRIM:
      default:
        return m.scrim();
    }
  }

  // The scheme in use: built once per colour, style and light or dark; its roles and Compose colours cached.

  private static int currentSeed, currentStyle = -1;
  private static final DynamicScheme[] currentScheme = new DynamicScheme[2];
  private static final int[][] currentRoles = new int[2][XgramExpressive.ROLE_COUNT];
  private static final long[] currentValid = new long[2];
  private static final int[][] currentCompose = new int[2][];

  /** Holding LOCK. */
  @NonNull
  private static DynamicScheme current (boolean dark) {
    XgramSettings settings = XgramSettings.instance();
    int seed = settings.colorSeed(), style = settings.colorStyle();
    if (seed != currentSeed || style != currentStyle) {
      currentSeed = seed;
      currentStyle = style;
      currentScheme[0] = currentScheme[1] = null;
      currentValid[0] = currentValid[1] = 0;
      currentCompose[0] = currentCompose[1] = null;
    }
    int index = dark ? 1 : 0;
    DynamicScheme scheme = currentScheme[index];
    if (scheme == null) {
      scheme = newScheme(seed, style, dark);
      currentScheme[index] = scheme;
    }
    return scheme;
  }

  /** An Expressive role (XgramExpressive.PRIMARY ... SCRIM) of the picked colour's scheme. Cached per role. */
  @ColorInt
  public static int role (int role, boolean dark) {
    if (role < 0 || role >= XgramExpressive.ROLE_COUNT) {
      return 0xff000000;
    }
    synchronized (LOCK) {
      DynamicScheme scheme = current(dark);
      int index = dark ? 1 : 0;
      long bit = 1L << role;
      if ((currentValid[index] & bit) == 0) {
        currentRoles[index][role] = roleColor(role).getArgb(scheme);
        currentValid[index] |= bit;
      }
      return currentRoles[index][role];
    }
  }

  /** {@link #currentComposeColors}: how many there are. */
  public static final int COMPOSE_COUNT = 48;

  /**
   * Every colour of a Compose ColorScheme for the picked colour and style, in the order of material3's
   * lightColorScheme parameters (XgramSeedScheme.kt reads them by index): primary, onPrimary, primaryContainer,
   * onPrimaryContainer, inversePrimary; secondary, onSecondary, secondaryContainer, onSecondaryContainer; tertiary,
   * onTertiary, tertiaryContainer, onTertiaryContainer; background, onBackground, surface, onSurface, surfaceVariant,
   * onSurfaceVariant, surfaceTint, inverseSurface, inverseOnSurface; error, onError, errorContainer, onErrorContainer;
   * outline, outlineVariant, scrim; surfaceBright, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
   * surfaceContainerLow, surfaceContainerLowest, surfaceDim; then primaryFixed, primaryFixedDim, onPrimaryFixed,
   * onPrimaryFixedVariant and the same four of secondary and tertiary. Cached until the colour or style changes; do
   * not change the array.
   */
  @NonNull
  public static int[] currentComposeColors (boolean dark) {
    synchronized (LOCK) {
      DynamicScheme s = current(dark);
      int index = dark ? 1 : 0;
      int[] cached = currentCompose[index];
      if (cached != null) {
        return cached;
      }
      MaterialDynamicColors m = COLORS;
      DynamicColor[] colors = {
        m.primary(), m.onPrimary(), m.primaryContainer(), m.onPrimaryContainer(), m.inversePrimary(),
        m.secondary(), m.onSecondary(), m.secondaryContainer(), m.onSecondaryContainer(),
        m.tertiary(), m.onTertiary(), m.tertiaryContainer(), m.onTertiaryContainer(),
        m.background(), m.onBackground(), m.surface(), m.onSurface(), m.surfaceVariant(),
        m.onSurfaceVariant(), m.surfaceTint(), m.inverseSurface(), m.inverseOnSurface(),
        m.error(), m.onError(), m.errorContainer(), m.onErrorContainer(),
        m.outline(), m.outlineVariant(), m.scrim(),
        m.surfaceBright(), m.surfaceContainer(), m.surfaceContainerHigh(), m.surfaceContainerHighest(),
        m.surfaceContainerLow(), m.surfaceContainerLowest(), m.surfaceDim(),
        m.primaryFixed(), m.primaryFixedDim(), m.onPrimaryFixed(), m.onPrimaryFixedVariant(),
        m.secondaryFixed(), m.secondaryFixedDim(), m.onSecondaryFixed(), m.onSecondaryFixedVariant(),
        m.tertiaryFixed(), m.tertiaryFixedDim(), m.onTertiaryFixed(), m.onTertiaryFixedVariant()
      };
      int[] result = new int[COMPOSE_COUNT];
      for (int i = 0; i < COMPOSE_COUNT; i++) {
        result[i] = colors[i].getArgb(s);
      }
      currentCompose[index] = result;
      return result;
    }
  }

  // Android's system palettes, for XgramMaterialYou: android.R.color.system_{accent1,accent2,accent3,neutral1,neutral2}
  // _{0,10,50,100,...,1000}, and the tone of each shade (Android's monet: 1000 - shade, / 10; 500 is 49.6).

  private static final int[][] SYSTEM_PALETTES = {
    {
      android.R.color.system_accent1_0, android.R.color.system_accent1_10, android.R.color.system_accent1_50,
      android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300,
      android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600,
      android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900,
      android.R.color.system_accent1_1000
    },
    {
      android.R.color.system_accent2_0, android.R.color.system_accent2_10, android.R.color.system_accent2_50,
      android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300,
      android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600,
      android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900,
      android.R.color.system_accent2_1000
    },
    {
      android.R.color.system_accent3_0, android.R.color.system_accent3_10, android.R.color.system_accent3_50,
      android.R.color.system_accent3_100, android.R.color.system_accent3_200, android.R.color.system_accent3_300,
      android.R.color.system_accent3_400, android.R.color.system_accent3_500, android.R.color.system_accent3_600,
      android.R.color.system_accent3_700, android.R.color.system_accent3_800, android.R.color.system_accent3_900,
      android.R.color.system_accent3_1000
    },
    {
      android.R.color.system_neutral1_0, android.R.color.system_neutral1_10, android.R.color.system_neutral1_50,
      android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_300,
      android.R.color.system_neutral1_400, android.R.color.system_neutral1_500, android.R.color.system_neutral1_600,
      android.R.color.system_neutral1_700, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900,
      android.R.color.system_neutral1_1000
    },
    {
      android.R.color.system_neutral2_0, android.R.color.system_neutral2_10, android.R.color.system_neutral2_50,
      android.R.color.system_neutral2_100, android.R.color.system_neutral2_200, android.R.color.system_neutral2_300,
      android.R.color.system_neutral2_400, android.R.color.system_neutral2_500, android.R.color.system_neutral2_600,
      android.R.color.system_neutral2_700, android.R.color.system_neutral2_800, android.R.color.system_neutral2_900,
      android.R.color.system_neutral2_1000
    }
  };
  private static final double[] SYSTEM_TONES = {100, 99, 95, 90, 80, 70, 60, 49.6, 40, 30, 20, 10, 0};

  /**
   * The colour the picked colour's scheme has where Android has android.R.color.system_* {@code res}: the same tone of
   * the matching palette (primary, secondary, tertiary, neutral, neutral variant) in the light or dark scheme. 0 when
   * {@code res} is not one of the system palettes' shades.
   */
  @RequiresApi(Build.VERSION_CODES.S)
  @ColorInt
  public static int systemPaletteColor (int res, boolean dark) {
    for (int palette = 0; palette < SYSTEM_PALETTES.length; palette++) {
      int[] shades = SYSTEM_PALETTES[palette];
      for (int shade = 0; shade < shades.length; shade++) {
        if (shades[shade] == res) {
          synchronized (LOCK) {
            return paletteOf(current(dark), palette).getHct(SYSTEM_TONES[shade]).toInt() | 0xff000000;
          }
        }
      }
    }
    return 0;
  }

  @NonNull
  private static TonalPalette paletteOf (DynamicScheme scheme, int palette) {
    switch (palette) {
      case 1: return scheme.secondaryPalette;
      case 2: return scheme.tertiaryPalette;
      case 3: return scheme.neutralPalette;
      case 4: return scheme.neutralVariantPalette;
      case 0:
      default:
        return scheme.primaryPalette;
    }
  }

  // Previews: the swatches and More colours' card. Each takes a few milliseconds (more before the JIT warms up), so the
  // pages make them off the main thread and keep them here.

  /** {@link #preview}: what a swatch and the preview card draw. */
  public static final int
    PREVIEW_PRIMARY = 0, PREVIEW_ON_PRIMARY = 1, PREVIEW_PRIMARY_CONTAINER = 2, PREVIEW_ON_PRIMARY_CONTAINER = 3,
    PREVIEW_SECONDARY = 4, PREVIEW_SECONDARY_CONTAINER = 5, PREVIEW_ON_SECONDARY_CONTAINER = 6,
    PREVIEW_TERTIARY = 7, PREVIEW_TERTIARY_CONTAINER = 8, PREVIEW_ON_TERTIARY_CONTAINER = 9,
    PREVIEW_SURFACE_CONTAINER_HIGH = 10, PREVIEW_COUNT = 11;

  private static final int PREVIEW_CACHE_SIZE = 160;
  private static final Map<Long, int[]> previews = new LinkedHashMap<Long, int[]>(32, .75f, true) {
    @Override
    protected boolean removeEldestEntry (Map.Entry<Long, int[]> eldest) {
      return size() > PREVIEW_CACHE_SIZE;
    }
  };

  private static long previewKey (int seed, int style, boolean dark) {
    return ((seed & 0xffffffffL) << 8) | ((long) (style & 0x7f) << 1) | (dark ? 1 : 0);
  }

  /** {@link #preview} if it was made before, else null (never builds one: safe on the main thread). */
  @Nullable
  public static int[] cachedPreview (int seed, int style, boolean dark) {
    synchronized (previews) {
      return previews.get(previewKey(seed, style, dark));
    }
  }

  /** The colours (PREVIEW_*) of the scheme {@code seed} and {@code style} make. Cached; do not change the array. */
  @WorkerThread
  @NonNull
  public static int[] preview (int seed, int style, boolean dark) {
    long key = previewKey(seed, style, dark);
    synchronized (previews) {
      int[] cached = previews.get(key);
      if (cached != null) {
        return cached;
      }
    }
    // Its own scheme and DynamicColors: nothing shared, so no lock while it is made
    DynamicScheme scheme = newScheme(seed, style, dark);
    MaterialDynamicColors m = COLORS;
    int[] colors = {
      m.primary().getArgb(scheme), m.onPrimary().getArgb(scheme),
      m.primaryContainer().getArgb(scheme), m.onPrimaryContainer().getArgb(scheme),
      m.secondary().getArgb(scheme), m.secondaryContainer().getArgb(scheme), m.onSecondaryContainer().getArgb(scheme),
      m.tertiary().getArgb(scheme), m.tertiaryContainer().getArgb(scheme), m.onTertiaryContainer().getArgb(scheme),
      m.surfaceContainerHigh().getArgb(scheme)
    };
    synchronized (previews) {
      previews.put(key, colors);
    }
    return colors;
  }
}
