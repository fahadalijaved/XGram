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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.ColorChangeAcceptorDelegate;
import org.thunderdog.challegram.util.CustomStateListDrawable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Material 3 Expressive (Google's Compose material3 tokens, Pixel Android 16 QPR1 SettingsLib) for the views Telegram X
 * draws itself: the switch, motion springs, shape and elevation tokens, color roles, the type scale, a shape-morphing
 * drawable and the segmented list look. Everything sits behind one switch (XGram > Appearance > Expressive design);
 * with it off none of this is used.
 */
public final class XgramExpressive {
  private XgramExpressive () { }

  // Switch

  public static final String KEY = "xgram_m3e";

  private static volatile int enabledState = -1;

  /** Android 13 or newer. */
  public static boolean isAvailable () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
  }

  /** Cheap (cached), safe to call in onDraw. */
  public static boolean isEnabled () {
    int state = enabledState;
    if (state == -1) {
      state = isAvailable() && Settings.instance().getBoolean(KEY, true) ? 1 : 0;
      enabledState = state;
    }
    return state == 1;
  }

  /** Saves the switch, rebuilds the Material You themes when they are in use and restarts the activity. */
  public static void setEnabled (ViewController<?> c, boolean on) {
    Settings.instance().putBoolean(KEY, on);
    enabledState = isAvailable() && on ? 1 : 0;
    invalidateColors();
    if (XgramMaterialYou.isEnabled()) {
      XgramMaterialYou.rebuild(c, c::scheduleActivityRestart);
    } else {
      c.scheduleActivityRestart();
    }
  }

  // Motion (Compose ExpressiveMotionTokens and StandardMotionTokens)

  public static final int SPATIAL_FAST = 0, SPATIAL_DEFAULT = 1, SPATIAL_SLOW = 2;
  public static final int EFFECTS_FAST = 3, EFFECTS_DEFAULT = 4, EFFECTS_SLOW = 5;
  public static final int STD_SPATIAL_FAST = 6, STD_SPATIAL_DEFAULT = 7, STD_SPATIAL_SLOW = 8;

  private static final float[] STIFFNESS = {800f, 380f, 200f, 3800f, 1600f, 800f, 1400f, 700f, 300f};
  private static final float[] DAMPING = {.6f, .8f, .8f, 1f, 1f, 1f, .9f, .9f, .9f};

  public static float stiffness (int token) {
    return STIFFNESS[token];
  }

  public static float damping (int token) {
    return DAMPING[token];
  }

  public static SpringForce springForce (int token, float finalPosition) {
    return new SpringForce(finalPosition).setStiffness(STIFFNESS[token]).setDampingRatio(DAMPING[token]);
  }

  /** Position of a unit-mass spring released from rest at 0 towards 1, after {@code tSeconds}. */
  public static float springProgress (int token, float tSeconds) {
    return springProgress(STIFFNESS[token], DAMPING[token], tSeconds);
  }

  public static float springProgress (float stiffness, float dampingRatio, float tSeconds) {
    if (tSeconds <= 0f) {
      return 0f;
    }
    double w = Math.sqrt(stiffness), z = dampingRatio, t = tSeconds;
    double x;
    if (Math.abs(z - 1.0) < 1e-4) {
      x = 1.0 - Math.exp(-w * t) * (1.0 + w * t);
    } else if (z < 1.0) {
      double wd = w * Math.sqrt(1.0 - z * z);
      x = 1.0 - Math.exp(-z * w * t) * (Math.cos(wd * t) + (z * w / wd) * Math.sin(wd * t));
    } else {
      double s = Math.sqrt(z * z - 1.0);
      double r1 = -w * (z - s), r2 = -w * (z + s);
      x = 1.0 - (r2 * Math.exp(r1 * t) - r1 * Math.exp(r2 * t)) / (r2 - r1);
    }
    return (float) x;
  }

  /** A float driven by a {@link SpringAnimation}. Main thread only. */
  public static final class Spring {
    public interface Listener {
      void onSpringUpdate (Spring s, float value);
    }

    private final FloatValueHolder holder;
    private final SpringAnimation animation;
    private final Listener listener;
    private @Nullable Runnable endListener;
    private float target;

    public Spring (int token, float initialValue, Listener l) {
      this.listener = l;
      this.target = initialValue;
      this.holder = new FloatValueHolder(initialValue);
      this.animation = new SpringAnimation(holder);
      this.animation.setSpring(springForce(token, initialValue));
      this.animation.setMinimumVisibleChange(.001f);
      this.animation.addUpdateListener((animation, value, velocity) -> listener.onSpringUpdate(this, value));
      this.animation.addEndListener((animation, canceled, value, velocity) -> {
        if (!canceled && endListener != null) {
          endListener.run();
        }
      });
    }

    /** Smallest change worth a frame: .001 by default (for 0..1 values); about .5 for pixels. */
    public Spring setThreshold (float minVisibleChange) {
      animation.setMinimumVisibleChange(Math.max(minVisibleChange, 1e-5f));
      return this;
    }

    /** Runs when the spring settles (not when it is canceled). */
    public Spring setEndListener (@Nullable Runnable r) {
      this.endListener = r;
      return this;
    }

    /** Springs to {@code target}; retargeting a running spring keeps its velocity. Snaps when Reduce motion is on. */
    public void animateTo (float target) {
      this.target = target;
      if (Settings.instance().needReduceMotion()) {
        boolean changed = holder.getValue() != target || animation.isRunning();
        animation.cancel();
        holder.setValue(target);
        if (changed) {
          listener.onSpringUpdate(this, target);
        }
        if (endListener != null) {
          endListener.run();
        }
        return;
      }
      if (!animation.isRunning() && holder.getValue() == target) {
        return;
      }
      animation.animateToFinalPosition(target);
    }

    /** Snaps to {@code v} and cancels the animation. The listener is not called. */
    public void setValue (float v) {
      if (animation.isRunning()) {
        animation.cancel();
      }
      holder.setValue(v);
      target = v;
    }

    public float getValue () {
      return holder.getValue();
    }

    public float getTarget () {
      return target;
    }

    public boolean isRunning () {
      return animation.isRunning();
    }

    public void cancel () {
      if (animation.isRunning()) {
        animation.cancel();
      }
    }
  }

  // Shape and elevation tokens (dp)

  public static final float CORNER_NONE = 0f, CORNER_XS = 4f, CORNER_S = 8f, CORNER_M = 12f, CORNER_L = 16f,
    CORNER_L_INC = 20f, CORNER_XL = 28f, CORNER_XL_INC = 32f, CORNER_XXL = 48f, CORNER_FULL = -1f;

  /** Pixel SettingsLib (Android 16 QPR1) and Compose SegmentedListItem. */
  public static final float LIST_OUTER_CORNER = 20f, LIST_INNER_CORNER = 4f, LIST_PRESSED_CORNER = 16f,
    LIST_GAP = 2f, LIST_INSET = 16f;
  /** Extra space above the first group under a collapsing avatar header, so its floating button doesn't cover it. */
  private static final float HEADER_FAB_CLEARANCE = 24f;

  public static final float ELEVATION_1 = 1f, ELEVATION_2 = 3f, ELEVATION_3 = 6f, ELEVATION_4 = 8f, ELEVATION_5 = 12f;

  /** A corner token in pixels for a box of w x h; {@link #CORNER_FULL} is half the shorter side. */
  public static float cornerPx (float dpOrFull, float w, float h) {
    float max = Math.max(0f, Math.min(w, h) / 2f);
    if (dpOrFull < 0f) {
      return max;
    }
    return Math.min(Screen.dpf(dpOrFull), max);
  }

  // Color roles

  public static final int
    PRIMARY = 0, ON_PRIMARY = 1, PRIMARY_CONTAINER = 2, ON_PRIMARY_CONTAINER = 3,
    SECONDARY = 4, ON_SECONDARY = 5, SECONDARY_CONTAINER = 6, ON_SECONDARY_CONTAINER = 7,
    TERTIARY = 8, ON_TERTIARY = 9, TERTIARY_CONTAINER = 10, ON_TERTIARY_CONTAINER = 11,
    ERROR = 12, ON_ERROR = 13, ERROR_CONTAINER = 14, ON_ERROR_CONTAINER = 15,
    SURFACE = 16, SURFACE_DIM = 17, SURFACE_BRIGHT = 18,
    SURFACE_CONTAINER_LOWEST = 19, SURFACE_CONTAINER_LOW = 20, SURFACE_CONTAINER = 21,
    SURFACE_CONTAINER_HIGH = 22, SURFACE_CONTAINER_HIGHEST = 23,
    ON_SURFACE = 24, ON_SURFACE_VARIANT = 25, OUTLINE = 26, OUTLINE_VARIANT = 27,
    INVERSE_SURFACE = 28, INVERSE_ON_SURFACE = 29, INVERSE_PRIMARY = 30, SCRIM = 31,
    ROLE_COUNT = 32;

  public static final float STATE_HOVER = .08f, STATE_PRESSED = .10f, STATE_DRAGGED = .16f,
    DISABLED_CONTENT = .38f, DISABLED_CONTAINER = .12f;

  private static final int P_PRIMARY = 0, P_SECONDARY = 1, P_TERTIARY = 2, P_NEUTRAL = 3, P_NEUTRAL_VARIANT = 4, P_ERROR = 5, P_NONE = -1;

  /** Tonal palette of each role. */
  private static final int[] ROLE_PALETTE = {
    P_PRIMARY, P_PRIMARY, P_PRIMARY, P_PRIMARY,
    P_SECONDARY, P_SECONDARY, P_SECONDARY, P_SECONDARY,
    P_TERTIARY, P_TERTIARY, P_TERTIARY, P_TERTIARY,
    P_ERROR, P_ERROR, P_ERROR, P_ERROR,
    P_NEUTRAL, P_NEUTRAL, P_NEUTRAL,
    P_NEUTRAL, P_NEUTRAL, P_NEUTRAL,
    P_NEUTRAL, P_NEUTRAL,
    P_NEUTRAL, P_NEUTRAL_VARIANT, P_NEUTRAL_VARIANT, P_NEUTRAL_VARIANT,
    P_NEUTRAL, P_NEUTRAL, P_PRIMARY, P_NONE
  };

  /** Tonal spot tones, light scheme. */
  private static final float[] TONE_LIGHT = {
    40, 100, 90, 10,
    40, 100, 90, 10,
    40, 100, 90, 10,
    40, 100, 90, 10,
    98, 87, 98,
    100, 96, 94,
    92, 90,
    10, 30, 50, 80,
    20, 95, 80, 0
  };

  /** Tonal spot tones, dark scheme. */
  private static final float[] TONE_DARK = {
    80, 20, 30, 90,
    80, 20, 30, 90,
    80, 20, 30, 90,
    80, 20, 30, 90,
    6, 6, 24,
    4, 10, 12,
    17, 22,
    90, 80, 60, 30,
    90, 20, 40, 0
  };

  /** android.R.color.system_*_light (Android 14+); 0 where the role is built from the palettes. */
  private static final int[] SYSTEM_LIGHT = {
    android.R.color.system_primary_light, android.R.color.system_on_primary_light,
    android.R.color.system_primary_container_light, android.R.color.system_on_primary_container_light,
    android.R.color.system_secondary_light, android.R.color.system_on_secondary_light,
    android.R.color.system_secondary_container_light, android.R.color.system_on_secondary_container_light,
    android.R.color.system_tertiary_light, android.R.color.system_on_tertiary_light,
    android.R.color.system_tertiary_container_light, android.R.color.system_on_tertiary_container_light,
    android.R.color.system_error_light, android.R.color.system_on_error_light,
    android.R.color.system_error_container_light, android.R.color.system_on_error_container_light,
    android.R.color.system_surface_light, android.R.color.system_surface_dim_light, android.R.color.system_surface_bright_light,
    android.R.color.system_surface_container_lowest_light, android.R.color.system_surface_container_low_light,
    android.R.color.system_surface_container_light,
    android.R.color.system_surface_container_high_light, android.R.color.system_surface_container_highest_light,
    android.R.color.system_on_surface_light, android.R.color.system_on_surface_variant_light,
    android.R.color.system_outline_light, android.R.color.system_outline_variant_light,
    0, 0, 0, 0
  };

  /** android.R.color.system_*_dark (Android 14+). */
  private static final int[] SYSTEM_DARK = {
    android.R.color.system_primary_dark, android.R.color.system_on_primary_dark,
    android.R.color.system_primary_container_dark, android.R.color.system_on_primary_container_dark,
    android.R.color.system_secondary_dark, android.R.color.system_on_secondary_dark,
    android.R.color.system_secondary_container_dark, android.R.color.system_on_secondary_container_dark,
    android.R.color.system_tertiary_dark, android.R.color.system_on_tertiary_dark,
    android.R.color.system_tertiary_container_dark, android.R.color.system_on_tertiary_container_dark,
    android.R.color.system_error_dark, android.R.color.system_on_error_dark,
    android.R.color.system_error_container_dark, android.R.color.system_on_error_container_dark,
    android.R.color.system_surface_dark, android.R.color.system_surface_dim_dark, android.R.color.system_surface_bright_dark,
    android.R.color.system_surface_container_lowest_dark, android.R.color.system_surface_container_low_dark,
    android.R.color.system_surface_container_dark,
    android.R.color.system_surface_container_high_dark, android.R.color.system_surface_container_highest_dark,
    android.R.color.system_on_surface_dark, android.R.color.system_on_surface_variant_dark,
    android.R.color.system_outline_dark, android.R.color.system_outline_variant_dark,
    0, 0, 0, 0
  };

  private static final int[] roleCache = new int[ROLE_COUNT];
  private static long roleValid;
  private static int cacheThemeId = Integer.MIN_VALUE;
  private static boolean cacheDark, cacheMaterialYou, listenerAdded;

  private static final ThemeChangeListener themeListener = new ThemeChangeListener() {
    @Override
    public boolean needsTempUpdates () {
      return true;
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, @Nullable ColorState state) {
      roleValid = 0;
      XgramM3Colors.onThemeColorsChanged();
    }

    @Override
    public void onThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) {
      invalidateColors();
    }
  };

  /** Drops the cached role colors (they are rebuilt on the next {@link #color} call). */
  public static void invalidateColors () {
    roleValid = 0;
    cacheThemeId = Integer.MIN_VALUE;
    systemValid[0] = 0;
    systemValid[1] = 0;
    XgramM3Colors.invalidate();
  }

  // Dynamic (wallpaper) colors while Expressive design is on: one cache per scheme, independent of the theme in use.

  private static final int[][] systemCache = new int[2][ROLE_COUNT];
  private static final long[] systemValid = new long[2];
  private static volatile boolean seedWatcherAdded;
  private static String lastSeed;

  /**
   * The role in the light or the dark scheme. With Expressive design on it is the device's dynamic color (every Telegram
   * X theme then draws with Material You roles); off, the old per-theme role.
   */
  @ColorInt
  public static int colorFor (int role, boolean dark) {
    if (isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (!seedWatcherAdded) {
        addSeedWatcher();
      }
      if (!listenerAdded) {
        ensureColorCache(); // registers the theme listener that resets the theme caches of XgramM3Colors
      }
      int scheme = dark ? 1 : 0;
      long bit = 1L << role;
      if ((systemValid[scheme] & bit) == 0) {
        int value;
        try {
          value = systemRole(AppContext.get(), role, dark);
        } catch (Throwable t) {
          value = themeRole(role, dark);
        }
        systemCache[scheme][role] = value;
        systemValid[scheme] |= bit;
      }
      return systemCache[scheme][role];
    }
    ensureColorCache();
    if (cacheMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      return systemRole(AppContext.get(), role, dark);
    }
    return themeRole(role, dark);
  }

  @RequiresApi(Build.VERSION_CODES.S)
  private static String systemSeed (Context context) {
    return Integer.toHexString(context.getColor(android.R.color.system_accent1_500)) + ":" +
      Integer.toHexString(context.getColor(android.R.color.system_accent2_500)) + ":" +
      Integer.toHexString(context.getColor(android.R.color.system_neutral1_500));
  }

  /**
   * Called when an activity is created or resumed (and from the Material You wallpaper check): when the wallpaper
   * colors changed, drops every cached role and repaints the app with the new ones.
   */
  public static void checkSystemSeed (@Nullable Context context) {
    if (!isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null) {
      return;
    }
    String seed;
    try {
      seed = systemSeed(context);
    } catch (Throwable t) {
      return;
    }
    String previous = lastSeed;
    lastSeed = seed;
    if (previous != null && !previous.equals(seed)) {
      invalidateColors();
      try {
        ThemeManager.xgramNotifyColorsChanged();
      } catch (Throwable t) {
        android.util.Log.e("XGram", "Expressive color refresh failed", t);
      }
    }
  }

  private static synchronized void addSeedWatcher () {
    if (seedWatcherAdded) {
      return;
    }
    seedWatcherAdded = true;
    try {
      Context app = AppContext.get().getApplicationContext();
      if (!(app instanceof android.app.Application)) {
        return;
      }
      ((android.app.Application) app).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated (@NonNull android.app.Activity activity, @Nullable android.os.Bundle savedInstanceState) {
          checkSystemSeed(activity);
        }

        @Override
        public void onActivityStarted (@NonNull android.app.Activity activity) { }

        @Override
        public void onActivityResumed (@NonNull android.app.Activity activity) {
          checkSystemSeed(activity);
        }

        @Override
        public void onActivityPaused (@NonNull android.app.Activity activity) { }

        @Override
        public void onActivityStopped (@NonNull android.app.Activity activity) { }

        @Override
        public void onActivitySaveInstanceState (@NonNull android.app.Activity activity, @NonNull android.os.Bundle outState) { }

        @Override
        public void onActivityDestroyed (@NonNull android.app.Activity activity) { }
      });
      if (lastSeed == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        lastSeed = systemSeed(app);
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Expressive seed watcher failed", t);
    }
  }

  private static void ensureColorCache () {
    ThemeManager themes = ThemeManager.instance();
    if (!listenerAdded) {
      listenerAdded = true;
      themes.addThemeListener(themeListener);
    }
    int themeId = themes.currentThemeId();
    boolean dark = themes.isCurrentThemeDark();
    if (themeId != cacheThemeId || dark != cacheDark) {
      cacheThemeId = themeId;
      cacheDark = dark;
      cacheMaterialYou = XgramMaterialYou.isEnabled();
      roleValid = 0;
    }
  }

  /** The role in the current theme. Cached per theme; cheap enough for onDraw. */
  @ColorInt
  public static int color (int role) {
    ensureColorCache();
    if (isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ThemeDelegate theme = ThemeManager.instance().currentTheme();
      if (theme.getId() == org.thunderdog.challegram.theme.ThemeId.TEMPORARY) {
        // Day/night switch in progress: blend the two schemes like Theme.getColor does (see XgramM3Colors)
        float dark = theme.getProperty(org.thunderdog.challegram.theme.PropertyId.DARK);
        if (dark > 0f && dark < 1f) {
          return me.vkryl.core.ColorUtils.fromToArgb(colorFor(role, false), colorFor(role, true), dark);
        }
        return colorFor(role, dark >= 1f);
      }
      return colorFor(role, cacheDark);
    }
    long bit = 1L << role;
    if ((roleValid & bit) == 0) {
      int value;
      if (cacheMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        value = systemRole(AppContext.get(), role, cacheDark);
      } else {
        value = themeRole(role, cacheDark);
      }
      roleCache[role] = value;
      roleValid |= bit;
    }
    return roleCache[role];
  }

  /**
   * True when the header background is a surface role: with Expressive design every Telegram X color resolves to a
   * Material You role (see {@link XgramM3Colors}), so every app bar is a surface container.
   */
  public static boolean surfaceHeader () {
    return isEnabled();
  }

  /**
   * The role from the device's dynamic colors: android.R.color.system_* on Android 14+ (so the device's own,
   * on Android 16 expressive, scheme), tonal spot tones of the system palettes before that.
   */
  @RequiresApi(Build.VERSION_CODES.S)
  @ColorInt
  public static int systemRole (Context context, int role, boolean dark) {
    if (role == SCRIM) {
      return 0xff000000;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      int res = (dark ? SYSTEM_DARK : SYSTEM_LIGHT)[role];
      if (res != 0) {
        try {
          return context.getColor(res);
        } catch (Throwable ignored) { }
      }
    }
    int paletteRes;
    switch (ROLE_PALETTE[role]) {
      case P_PRIMARY: paletteRes = android.R.color.system_accent1_500; break;
      case P_SECONDARY: paletteRes = android.R.color.system_accent2_500; break;
      case P_TERTIARY: paletteRes = android.R.color.system_accent3_500; break;
      case P_NEUTRAL: paletteRes = android.R.color.system_neutral1_500; break;
      case P_NEUTRAL_VARIANT: paletteRes = android.R.color.system_neutral2_500; break;
      case P_ERROR:
      default:
        return tone(25f, 84f, role, dark);
    }
    float[] hct = new float[3];
    androidx.core.graphics.ColorUtils.colorToM3HCT(context.getColor(paletteRes), hct);
    return tone(hct[0], hct[1], role, dark);
  }

  private static int tone (float hue, float chroma, int role, boolean dark) {
    return androidx.core.graphics.ColorUtils.M3HCTToColor(hue, chroma, (dark ? TONE_DARK : TONE_LIGHT)[role]);
  }

  /** Any other Telegram X theme: accents are a tonal spot of the theme's accent, surfaces are the theme's own. */
  private static int themeRole (int role, boolean dark) {
    // Raw theme colors (never Theme.getColor: with Expressive design on it resolves to these roles)
    ThemeDelegate theme = ThemeManager.instance().currentTheme();
    switch (role) {
      case SURFACE:
      case SURFACE_BRIGHT:
      case SURFACE_CONTAINER_LOWEST:
        return theme.getColor(ColorId.filling);
      case SURFACE_CONTAINER:
        return theme.getColor(ColorId.background);
      case SURFACE_CONTAINER_LOW:
        return androidx.core.graphics.ColorUtils.blendARGB(theme.getColor(ColorId.background), theme.getColor(ColorId.filling), .5f);
      case SURFACE_CONTAINER_HIGH:
        return androidx.core.graphics.ColorUtils.blendARGB(theme.getColor(ColorId.background), theme.getColor(ColorId.text), .04f);
      case SURFACE_CONTAINER_HIGHEST:
        return androidx.core.graphics.ColorUtils.blendARGB(theme.getColor(ColorId.background), theme.getColor(ColorId.text), .08f);
      case SURFACE_DIM:
        return androidx.core.graphics.ColorUtils.blendARGB(theme.getColor(ColorId.background), theme.getColor(ColorId.text), .06f);
      case ON_SURFACE:
        return theme.getColor(ColorId.text);
      case ON_SURFACE_VARIANT:
        return theme.getColor(ColorId.textLight);
      case OUTLINE:
        return theme.getColor(ColorId.iconLight);
      case OUTLINE_VARIANT:
        return theme.getColor(ColorId.separator);
      case SCRIM:
        return 0xff000000;
    }
    float[] hct = new float[3];
    androidx.core.graphics.ColorUtils.colorToM3HCT(theme.getColor(ColorId.progress) | 0xff000000, hct);
    float hue = hct[0];
    switch (ROLE_PALETTE[role]) {
      case P_PRIMARY: return tone(hue, 36f, role, dark);
      case P_SECONDARY: return tone(hue, 16f, role, dark);
      case P_TERTIARY: return tone((hue + 60f) % 360f, 24f, role, dark);
      case P_NEUTRAL: return tone(hue, 6f, role, dark);
      case P_NEUTRAL_VARIANT: return tone(hue, 8f, role, dark);
      case P_ERROR:
      default:
        return tone(25f, 84f, role, dark);
    }
  }

  /** {@code c} with its alpha multiplied by {@code a}. */
  @ColorInt
  public static int withAlpha (@ColorInt int c, float a) {
    int alpha = Math.round(((c >>> 24) & 0xff) * Math.max(0f, Math.min(1f, a)));
    return (c & 0x00ffffff) | (alpha << 24);
  }

  /** The content role drawn on {@code container} (a role color), or -1. */
  private static int onRoleFor (@ColorInt int container) {
    final int[][] pairs = {
      {PRIMARY, ON_PRIMARY}, {PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER},
      {SECONDARY, ON_SECONDARY}, {SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER},
      {TERTIARY, ON_TERTIARY}, {TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER},
      {ERROR, ON_ERROR}, {ERROR_CONTAINER, ON_ERROR_CONTAINER},
      {INVERSE_SURFACE, INVERSE_ON_SURFACE},
      {SURFACE, ON_SURFACE}, {SURFACE_BRIGHT, ON_SURFACE}, {SURFACE_CONTAINER, ON_SURFACE},
      {SURFACE_CONTAINER_LOW, ON_SURFACE}, {SURFACE_CONTAINER_HIGH, ON_SURFACE}, {SURFACE_CONTAINER_HIGHEST, ON_SURFACE}
    };
    for (int[] pair : pairs) {
      if (color(pair[0]) == container) {
        return pair[1];
      }
    }
    return -1;
  }

  // Typography (Compose TypeScaleTokens)

  public static final int TS_DISPLAY_L = 0, TS_DISPLAY_M = 1, TS_DISPLAY_S = 2,
    TS_HEADLINE_L = 3, TS_HEADLINE_M = 4, TS_HEADLINE_S = 5,
    TS_TITLE_L = 6, TS_TITLE_M = 7, TS_TITLE_S = 8,
    TS_BODY_L = 9, TS_BODY_M = 10, TS_BODY_S = 11,
    TS_LABEL_L = 12, TS_LABEL_M = 13, TS_LABEL_S = 14;

  private static final float[] TS_SIZE = {57, 45, 36, 32, 28, 24, 22, 16, 14, 16, 14, 12, 14, 12, 11};
  private static final float[] TS_LINE = {64, 52, 44, 40, 36, 32, 28, 24, 20, 24, 20, 16, 20, 16, 16};
  private static final float[] TS_TRACKING = {-.2f, 0, 0, 0, 0, 0, 0, .2f, .1f, .5f, .2f, .4f, .1f, .5f, .5f};
  private static final int[] TS_WEIGHT = {400, 400, 400, 400, 400, 400, 400, 500, 500, 400, 400, 400, 500, 500, 500};
  private static final int[] TS_WEIGHT_EMPHASIZED = {500, 500, 500, 500, 500, 500, 500, 700, 700, 500, 500, 500, 700, 700, 700};

  private static final String FLEX_PATH = "fonts/GoogleSansFlex.ttf";
  private static int flexState = -1;
  private static final HashMap<Integer, Typeface> flexCache = new HashMap<>();

  public static float textSizeSp (int ts) {
    return TS_SIZE[ts];
  }

  public static float lineHeightSp (int ts) {
    return TS_LINE[ts];
  }

  public static float trackingSp (int ts) {
    return TS_TRACKING[ts];
  }

  public static int weight (int ts, boolean emphasized) {
    return (emphasized ? TS_WEIGHT_EMPHASIZED : TS_WEIGHT)[ts];
  }

  /**
   * Google Sans Flex at the style's weight and optical size when assets/fonts/GoogleSansFlex.ttf is bundled;
   * otherwise the app's own font at that weight (Roboto, or the device font when "Use system fonts" is on).
   */
  public static Typeface typeface (int ts, boolean emphasized) {
    int weight = weight(ts, emphasized);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasFlex()) {
      int key = weight * 1000 + Math.round(TS_SIZE[ts] * 10f);
      Typeface cached = flexCache.get(key);
      if (cached == null) {
        try {
          cached = new Typeface.Builder(AppContext.get().getAssets(), FLEX_PATH)
            .setFontVariationSettings(String.format(Locale.US, "'wght' %d, 'opsz' %.1f", weight, TS_SIZE[ts]))
            .build();
        } catch (Throwable t) {
          cached = null;
        }
        if (cached != null) {
          flexCache.put(key, cached);
        }
      }
      if (cached != null) {
        return cached;
      }
    }
    if (weight >= 700) {
      return Fonts.getRobotoBold();
    } else if (weight >= 500) {
      return Fonts.getRobotoMedium();
    } else {
      return Fonts.getRobotoRegular();
    }
  }

  private static boolean hasFlex () {
    if (flexState == -1) {
      int state = 0;
      try {
        AssetManager assets = AppContext.get().getAssets();
        try (InputStream in = assets.open(FLEX_PATH)) {
          state = in != null ? 1 : 0;
        }
      } catch (Throwable ignored) { }
      flexState = state;
    }
    return flexState == 1;
  }

  /** Size (dp, like the rest of Telegram X's fixed-height rows), typeface and letter spacing of the style. */
  public static void applyText (TextView v, int ts, boolean emphasized) {
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TS_SIZE[ts]);
    v.setTypeface(typeface(ts, emphasized));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      v.setLetterSpacing(TS_TRACKING[ts] / TS_SIZE[ts]);
    }
  }

  // Drawables, press feedback, list segments

  public static final int SEGMENT_NONE = 0, SEGMENT_SINGLE = 1, SEGMENT_FIRST = 2, SEGMENT_MIDDLE = 3, SEGMENT_LAST = 4;

  /** The first {@link MorphDrawable} of a background (itself, or a layer of a RippleDrawable / LayerDrawable). */
  @Nullable
  public static MorphDrawable findMorph (@Nullable Drawable d) {
    if (d instanceof MorphDrawable) {
      return (MorphDrawable) d;
    }
    if (d instanceof LayerDrawable) {
      LayerDrawable layers = (LayerDrawable) d;
      for (int i = 0; i < layers.getNumberOfLayers(); i++) {
        Drawable layer = layers.getDrawable(i);
        if (layer instanceof MorphDrawable) {
          return (MorphDrawable) layer;
        }
      }
    }
    return null;
  }

  /**
   * A rounded rectangle whose corners morph with springs: while pressed every corner goes to the pressed corner
   * (SPATIAL_FAST), and {@link #setCornerTarget} moves the resting shape (selected / toggled). Corners are in dp,
   * {@link #CORNER_FULL} is a pill.
   */
  public static class MorphDrawable extends Drawable implements ColorChangeAcceptorDelegate {
    private static final int COLOR_FIXED = 0, COLOR_ROLE = 1, COLOR_IDS = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int colorMode = COLOR_FIXED;
    private @ColorInt int fixedColor;
    private int colorRole;
    private @ColorId int fromColorId, toColorId;
    private float colorFactor;
    private int alpha = 255;

    private boolean listRow;
    private @ColorId int ungroupedColorId;

    private final float[] restDp = new float[4]; // topStart, topEnd, bottomEnd, bottomStart
    private float pressedDp;
    private float targetDp = Float.NaN;
    private int segment = SEGMENT_NONE;
    private boolean pressed;

    private boolean hasBox;
    private float boxWDp, boxHDp;
    private int boxGravity;

    private @Nullable Spring pressSpring, shapeSpring;
    private final float[] fromPx = new float[4], shapePx = new float[4], drawPx = new float[4];
    private final float[] radii = new float[8];
    private final RectF rect = new RectF();
    private final Rect box = new Rect();
    private final Path path = new Path();

    private @Nullable View host;
    private @Nullable android.graphics.drawable.RippleDrawable ripple;
    private int rippleRole = -1;
    private @ColorInt int rippleColor;

    /** {@code color} 0 draws no fill (a state-layer only button). */
    public MorphDrawable (@ColorInt int color, float restingCornerDp, float pressedCornerDp) {
      this.fixedColor = color;
      this.pressedDp = pressedCornerDp;
      for (int i = 0; i < 4; i++) {
        restDp[i] = restingCornerDp;
      }
    }

    // Color

    public void setColor (@ColorInt int color) {
      if (colorMode != COLOR_FIXED || fixedColor != color) {
        colorMode = COLOR_FIXED;
        fixedColor = color;
        invalidateSelf();
      }
    }

    /** A role resolved at draw time, so it follows theme changes. */
    public void setColorRole (int role) {
      if (colorMode != COLOR_ROLE || colorRole != role) {
        colorMode = COLOR_ROLE;
        colorRole = role;
        invalidateSelf();
      }
    }

    public void setColorIds (@ColorId int fromColorId, @ColorId int toColorId, float factor) {
      applyColor(fromColorId, toColorId, factor);
    }

    @Override
    public void applyColor (@ColorId int fromColorId, @ColorId int toColorId, float factor) {
      if (colorMode != COLOR_IDS || this.fromColorId != fromColorId || this.toColorId != toColorId || this.colorFactor != factor) {
        colorMode = COLOR_IDS;
        this.fromColorId = fromColorId;
        this.toColorId = toColorId;
        this.colorFactor = factor;
        invalidateSelf();
      }
    }

    @Override
    @ColorInt
    public int getDrawColor () {
      if (listRow && segment == SEGMENT_NONE && ungroupedColorId != 0) {
        return Theme.getColor(ungroupedColorId);
      }
      switch (colorMode) {
        case COLOR_ROLE:
          return color(colorRole);
        case COLOR_IDS:
          return me.vkryl.core.ColorUtils.fromToArgb(Theme.getColor(fromColorId), Theme.getColor(toColorId), colorFactor);
        case COLOR_FIXED:
        default:
          return fixedColor;
      }
    }

    // Shape

    /** Resting corners in dp (RTL aware); snaps. */
    public void setCorners (float topStart, float topEnd, float bottomEnd, float bottomStart) {
      if (restDp[0] != topStart || restDp[1] != topEnd || restDp[2] != bottomEnd || restDp[3] != bottomStart) {
        restDp[0] = topStart;
        restDp[1] = topEnd;
        restDp[2] = bottomEnd;
        restDp[3] = bottomStart;
        reshape(false);
      }
    }

    /** List tiles: outer corners 20, inner 4; {@link #SEGMENT_NONE} is a plain rectangle. */
    public void setSegment (int segment) {
      if (this.segment == segment) {
        return;
      }
      this.segment = segment;
      float outer = LIST_OUTER_CORNER, inner = LIST_INNER_CORNER;
      switch (segment) {
        case SEGMENT_SINGLE:
          setCorners(outer, outer, outer, outer);
          break;
        case SEGMENT_FIRST:
          setCorners(outer, outer, inner, inner);
          break;
        case SEGMENT_MIDDLE:
          setCorners(inner, inner, inner, inner);
          break;
        case SEGMENT_LAST:
          setCorners(inner, inner, outer, outer);
          break;
        case SEGMENT_NONE:
        default:
          setCorners(CORNER_NONE, CORNER_NONE, CORNER_NONE, CORNER_NONE);
          break;
      }
      invalidateSelf();
    }

    public int getSegment () {
      return segment;
    }

    public void setPressedCorner (float dp) {
      if (this.pressedDp != dp) {
        this.pressedDp = dp;
        invalidateSelf();
      }
    }

    /** Moves every resting corner to {@code dp} (selected or toggled shape); {@link Float#NaN} goes back to the resting corners. */
    public void setCornerTarget (float dp, boolean animated) {
      boolean same = Float.isNaN(dp) ? Float.isNaN(targetDp) : dp == targetDp;
      if (same) {
        return;
      }
      snapshot();
      targetDp = dp;
      reshape(animated);
    }

    /** A fixed shape box (dp) inside the bounds, e.g. a 40dp icon button state layer in a bigger view. */
    public void setSize (float wDp, float hDp, int gravity) {
      this.hasBox = wDp > 0f && hDp > 0f;
      this.boxWDp = wDp;
      this.boxHDp = hDp;
      this.boxGravity = gravity;
      invalidateSelf();
    }

    /** Spring frames invalidate this view and its outline. */
    public void setHost (@Nullable View v) {
      this.host = v;
    }

    /** Current corner (top start) in pixels. */
    public float cornerPx () {
      computeShape();
      return drawPx[0];
    }

    /** The shape for the current bounds. Do not modify. */
    public Path getPath () {
      computeShape();
      return path;
    }

    /** An opaque mask with the same (animated) shape, for a RippleDrawable. */
    public Drawable newMask () {
      return new MaskDrawable(this);
    }

    private void snapshot () {
      computeShape();
      System.arraycopy(shapePx, 0, fromPx, 0, 4);
    }

    private void reshape (boolean animated) {
      if (animated && !getBounds().isEmpty()) {
        if (shapeSpring == null) {
          shapeSpring = new Spring(SPATIAL_FAST, 1f, (s, value) -> onSpringFrame());
        }
        shapeSpring.setValue(0f);
        shapeSpring.animateTo(1f);
      } else if (shapeSpring != null) {
        shapeSpring.setValue(1f);
      }
      invalidateSelf();
    }

    private boolean pressMorphs () {
      return !Float.isNaN(pressedDp) && !(listRow && segment == SEGMENT_NONE);
    }

    private void computeShape () {
      Rect bounds = getBounds();
      if (hasBox) {
        int layoutDirection = Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        Gravity.apply(boxGravity, Screen.dp(boxWDp), Screen.dp(boxHDp), bounds, box, layoutDirection);
        rect.set(box);
      } else {
        rect.set(bounds);
      }
      float w = rect.width(), h = rect.height();
      float max = Math.max(0f, Math.min(w, h) / 2f);
      float t = shapeSpring != null ? shapeSpring.getValue() : 1f;
      float p = pressSpring != null && pressMorphs() ? pressSpring.getValue() : 0f;
      float pressedPx = Float.isNaN(pressedDp) ? 0f : XgramExpressive.cornerPx(pressedDp, w, h);
      for (int i = 0; i < 4; i++) {
        float target = XgramExpressive.cornerPx(Float.isNaN(targetDp) ? restDp[i] : targetDp, w, h);
        float v = t >= 1f ? target : fromPx[i] + (target - fromPx[i]) * t;
        shapePx[i] = v;
        if (p != 0f) {
          v = v + (pressedPx - v) * p;
        }
        drawPx[i] = Math.max(0f, Math.min(max, v));
      }
      boolean rtl = Lang.rtl();
      float tl = drawPx[rtl ? 1 : 0], tr = drawPx[rtl ? 0 : 1], br = drawPx[rtl ? 3 : 2], bl = drawPx[rtl ? 2 : 3];
      radii[0] = radii[1] = tl;
      radii[2] = radii[3] = tr;
      radii[4] = radii[5] = br;
      radii[6] = radii[7] = bl;
      path.rewind();
      if (w > 0f && h > 0f) {
        path.addRoundRect(rect, radii, Path.Direction.CW);
      }
    }

    private void onSpringFrame () {
      invalidateSelf();
      if (host != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        host.invalidateOutline();
      }
    }

    // State

    @Override
    public boolean isStateful () {
      return true;
    }

    @Override
    protected boolean onStateChange (int[] state) {
      boolean isPressed = false, isEnabled = false;
      for (int s : state) {
        if (s == android.R.attr.state_pressed) {
          isPressed = true;
        } else if (s == android.R.attr.state_enabled) {
          isEnabled = true;
        }
      }
      isPressed = isPressed && isEnabled;
      if (this.pressed == isPressed) {
        return false;
      }
      this.pressed = isPressed;
      if (pressMorphs()) {
        if (pressSpring == null) {
          pressSpring = new Spring(SPATIAL_FAST, 0f, (s, value) -> onSpringFrame());
        }
        pressSpring.animateTo(isPressed ? 1f : 0f);
      }
      invalidateSelf();
      return true;
    }

    @Override
    public void jumpToCurrentState () {
      super.jumpToCurrentState();
      if (pressSpring != null) {
        pressSpring.setValue(pressed ? 1f : 0f);
      }
      if (shapeSpring != null) {
        shapeSpring.setValue(1f);
      }
      invalidateSelf();
    }

    // Drawing

    @Override
    public void draw (@NonNull Canvas c) {
      syncRipple();
      int color = getDrawColor();
      int a = Math.round(((color >>> 24) & 0xff) * (alpha / 255f));
      if (a == 0) {
        return;
      }
      computeShape();
      paint.setColor((color & 0x00ffffff) | (a << 24));
      if (radii[0] == 0f && radii[2] == 0f && radii[4] == 0f && radii[6] == 0f) {
        c.drawRect(rect, paint);
      } else {
        c.drawPath(path, paint);
      }
    }

    private void syncRipple () {
      if (ripple != null && rippleRole >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        int want = withAlpha(color(rippleRole), STATE_PRESSED);
        if (want != rippleColor) {
          rippleColor = want;
          ripple.setColor(ColorStateList.valueOf(want));
        }
      }
    }

    @Override
    public void getOutline (@NonNull Outline outline) {
      computeShape();
      if (rect.isEmpty()) {
        outline.setEmpty();
        return;
      }
      float r = radii[0];
      if (radii[2] == r && radii[4] == r && radii[6] == r) {
        outline.setRoundRect(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom), r);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        outline.setPath(path);
      } else {
        setConvexPath(outline, path);
      }
      int color = getDrawColor();
      outline.setAlpha((((color >>> 24) & 0xff) / 255f) * (alpha / 255f));
    }

    @SuppressWarnings("deprecation")
    private static void setConvexPath (Outline outline, Path path) {
      outline.setConvexPath(path);
    }

    @Override
    protected void onBoundsChange (@NonNull Rect bounds) {
      super.onBoundsChange(bounds);
      invalidateSelf();
    }

    @Override
    public void setAlpha (int alpha) {
      if (this.alpha != alpha) {
        this.alpha = alpha;
        invalidateSelf();
      }
    }

    @Override
    public int getAlpha () {
      return alpha;
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
      invalidateSelf();
    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /** Opaque mask in the shape of a {@link MorphDrawable}, so ripples follow its corners. */
  private static final class MaskDrawable extends Drawable {
    private final MorphDrawable source;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MaskDrawable (MorphDrawable source) {
      this.source = source;
      paint.setColor(0xff000000);
    }

    @Override
    public void draw (@NonNull Canvas c) {
      c.drawPath(source.getPath(), paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /**
   * Background = RippleDrawable(content color at .10, morph, mask in the same shape) and the background outline
   * provider. {@code containerColor} 0 is a standard (unfilled) icon button with an onSurfaceVariant state layer.
   */
  public static MorphDrawable attachPressMorph (View v, @ColorInt int containerColor, float restingDp, float pressedDp) {
    MorphDrawable morph = new MorphDrawable(containerColor, restingDp, pressedDp);
    morph.setHost(v);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      v.setBackground(morph);
      return morph;
    }
    int contentRole = containerColor == 0 ? ON_SURFACE_VARIANT : onRoleFor(containerColor);
    int content;
    if (contentRole >= 0) {
      content = color(contentRole);
    } else {
      content = androidx.core.graphics.ColorUtils.calculateLuminance(containerColor | 0xff000000) > .5 ? 0xff000000 : 0xffffffff;
    }
    int rippleColor = withAlpha(content, STATE_PRESSED);
    android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(rippleColor), morph, morph.newMask());
    morph.ripple = ripple;
    morph.rippleRole = contentRole;
    morph.rippleColor = rippleColor;
    v.setBackground(ripple);
    v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    return morph;
  }

  // Lists (SettingsAdapter). The grouping helpers are public: the Compose renderer of Telegram X pages
  // (com.fahadali.xgram.compose.tgx) groups rows exactly as the list decoration does.

  public static boolean isFrameType (int type) {
    switch (type) {
      case ListItem.TYPE_SHADOW_TOP:
      case ListItem.TYPE_SHADOW_BOTTOM:
      case ListItem.TYPE_SEPARATOR:
      case ListItem.TYPE_SEPARATOR_FULL:
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_PADDED:
      case ListItem.TYPE_HEADER_MULTILINE:
      case ListItem.TYPE_HEADER_WITH_ACTION:
      case ListItem.TYPE_HEADER_WITH_TEXT_BUTTON:
      case ListItem.TYPE_HEADER_WITH_CHECKBOX:
      case ListItem.TYPE_DESCRIPTION:
      case ListItem.TYPE_DESCRIPTION_CENTERED:
        return true;
    }
    return false;
  }

  public static boolean isHeaderType (int type) {
    switch (type) {
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_PADDED:
      case ListItem.TYPE_HEADER_MULTILINE:
      case ListItem.TYPE_HEADER_WITH_ACTION:
      case ListItem.TYPE_HEADER_WITH_TEXT_BUTTON:
      case ListItem.TYPE_HEADER_WITH_CHECKBOX:
        return true;
    }
    return false;
  }

  /** A tile of a group (anything that is not a shadow, separator, header, description or spacing). */
  public static boolean isRowType (int type) {
    if (isFrameType(type)) {
      return false;
    }
    switch (type) {
      case ListItem.TYPE_EMPTY_OFFSET:
      case ListItem.TYPE_EMPTY_OFFSET_SMALL:
      case ListItem.TYPE_EMPTY_OFFSET_NO_HEAD:
      case ListItem.TYPE_PADDING:
        return false;
    }
    return true;
  }

  private static boolean isSeparator (int type) {
    return type == ListItem.TYPE_SEPARATOR || type == ListItem.TYPE_SEPARATOR_FULL;
  }

  /** Opens a group: a SHADOW_TOP, or the empty offset a list (profile, Settings) starts with before its first rows. */
  public static boolean isGroupOpener (int type) {
    return type == ListItem.TYPE_SHADOW_TOP || type == ListItem.TYPE_EMPTY_OFFSET || type == ListItem.TYPE_EMPTY_OFFSET_SMALL;
  }

  /** Between a group opener (see {@link #isGroupOpener}) and a SHADOW_BOTTOM. */
  public static boolean isInGroup (List<ListItem> items, int position) {
    // The list start also opens a group: TGX pages such as Interface Options start straight
    // with rows (no SHADOW_TOP) and close them with a SHADOW_BOTTOM. A list made only of rows
    // (no SHADOW_BOTTOM at all) still stays flat, as before.
    for (int i = position - 1; i >= 0; i--) {
      int type = items.get(i).getViewType();
      if (isGroupOpener(type)) {
        break;
      }
      if (type == ListItem.TYPE_SHADOW_BOTTOM) {
        return false;
      }
    }
    for (int i = position + 1; i < items.size(); i++) {
      int type = items.get(i).getViewType();
      if (type == ListItem.TYPE_SHADOW_BOTTOM) {
        return true;
      }
      if (isGroupOpener(type)) {
        return false;
      }
    }
    return false;
  }

  public static int segmentOf (List<ListItem> items, int position) {
    if (!isRowType(items.get(position).getViewType()) || !isInGroup(items, position)) {
      return SEGMENT_NONE;
    }
    boolean first = true, last = true;
    for (int i = position - 1; i >= 0; i--) {
      int type = items.get(i).getViewType();
      if (!isSeparator(type)) {
        first = !isRowType(type);
        break;
      }
    }
    for (int i = position + 1; i < items.size(); i++) {
      int type = items.get(i).getViewType();
      if (!isSeparator(type)) {
        last = !isRowType(type);
        break;
      }
    }
    if (first && last) {
      return SEGMENT_SINGLE;
    } else if (first) {
      return SEGMENT_FIRST;
    } else if (last) {
      return SEGMENT_LAST;
    } else {
      return SEGMENT_MIDDLE;
    }
  }

  /**
   * The top offset the segmented list decoration gives the item at {@code position}: the 2dp gap above a grouped
   * row that follows another row, the floating button's clearance under a collapsing header, the 8dp a SHADOW_TOP
   * would give a group opened by the list start; 0 for anything else. Shared with the profile's scroll sync
   * (XgramM3Profile.groupGapPx), which measures the base list item by item.
   */
  static int listTopOffsetPx (List<ListItem> items, int position) {
    if (position < 0 || position >= items.size()) {
      return 0;
    }
    int type = items.get(position).getViewType();
    if (type == ListItem.TYPE_SMALL_MEDIA || !isRowType(type) || !isInGroup(items, position)) {
      return 0;
    }
    if (position == 0) {
      // A group opened by the list start: the space a SHADOW_TOP would give.
      return Screen.dp(8f);
    }
    int prevType = items.get(position - 1).getViewType();
    if (isRowType(prevType)) {
      return Screen.dp(LIST_GAP);
    }
    if (prevType == ListItem.TYPE_EMPTY_OFFSET) {
      // Under a collapsing avatar header (Settings, profiles) the floating button sits half below the header's
      // edge: the first group starts clear of it instead of under it.
      return Screen.dp(HEADER_FAB_CLEARANCE);
    }
    return 0;
  }

  private static final class ListDecoration extends RecyclerView.ItemDecoration {
    private final List<ListItem> items;
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tilePath = new Path();
    private final RectF tileRect = new RectF();
    private final float[] tileRadii = new float[8];

    ListDecoration (List<ListItem> items) {
      this.items = items;
    }

    private int positionOf (RecyclerView parent, View view) {
      int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION) {
        Object tag = view.getTag();
        if (tag instanceof ListItem) {
          position = items.indexOf(tag);
        }
      }
      return position >= 0 && position < items.size() ? position : RecyclerView.NO_POSITION;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      int position = positionOf(parent, view);
      if (position == RecyclerView.NO_POSITION) {
        return;
      }
      int type = items.get(position).getViewType();
      if (type == ListItem.TYPE_SMALL_MEDIA) {
        // The shared media grid (Saved Messages' Media tab) is XgramW2ProfileGrid's: no tile insets or gaps here.
        return;
      }
      boolean groupRow = isRowType(type) && isInGroup(items, position);
      if (groupRow || isFrameType(type)) {
        int inset = Screen.dp(LIST_INSET);
        outRect.left = inset;
        outRect.right = inset;
      }
      outRect.top = listTopOffsetPx(items, position);
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      final int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (child == null) {
          continue;
        }
        int position = positionOf(parent, child);
        if (position == RecyclerView.NO_POSITION) {
          continue;
        }
        ListItem item = items.get(position);
        int type = item.getViewType();
        if (type == ListItem.TYPE_SMALL_MEDIA) {
          continue; // the media grid draws its own tiles and group corners (XgramW2ProfileGrid)
        }
        if (isHeaderType(type) || type == ListItem.TYPE_DESCRIPTION || type == ListItem.TYPE_DESCRIPTION_CENTERED) {
          if (item.getTextColorId(ColorId.NONE) == ColorId.NONE) {
            syncTextColor(child, color(isHeaderType(type) ? PRIMARY : ON_SURFACE_VARIANT));
          }
          continue;
        }
        if (!isRowType(type)) {
          continue;
        }
        int segment = segmentOf(items, position);
        MorphDrawable morph = findMorph(child.getBackground());
        if (morph != null) {
          morph.setSegment(segment);
          continue;
        }
        if (segment != SEGMENT_NONE) {
          drawTile(c, child, segment);
        }
      }
    }

    private void drawTile (Canvas c, View child, int segment) {
      float outer = Screen.dpf(LIST_OUTER_CORNER), inner = Screen.dpf(LIST_INNER_CORNER);
      float top, bottom;
      switch (segment) {
        case SEGMENT_SINGLE: top = outer; bottom = outer; break;
        case SEGMENT_FIRST: top = outer; bottom = inner; break;
        case SEGMENT_LAST: top = inner; bottom = outer; break;
        case SEGMENT_MIDDLE:
        default:
          top = inner; bottom = inner; break;
      }
      float tx = child.getTranslationX(), ty = child.getTranslationY();
      tileRect.set(child.getLeft() + tx, child.getTop() + ty, child.getRight() + tx, child.getBottom() + ty);
      float max = Math.min(tileRect.width(), tileRect.height()) / 2f;
      top = Math.min(top, max);
      bottom = Math.min(bottom, max);
      tileRadii[0] = tileRadii[1] = tileRadii[2] = tileRadii[3] = top;
      tileRadii[4] = tileRadii[5] = tileRadii[6] = tileRadii[7] = bottom;
      tilePath.rewind();
      tilePath.addRoundRect(tileRect, tileRadii, Path.Direction.CW);
      tilePaint.setColor(withAlpha(color(SURFACE_BRIGHT), child.getAlpha()));
      c.drawPath(tilePath, tilePaint);
    }

    private static void syncTextColor (View child, @ColorInt int color) {
      TextView textView = null;
      if (child instanceof TextView) {
        textView = (TextView) child;
      } else {
        View text = child.findViewById(android.R.id.text1);
        if (text instanceof TextView) {
          textView = (TextView) text;
        } else if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() > 0 && ((ViewGroup) child).getChildAt(0) instanceof TextView) {
          textView = (TextView) ((ViewGroup) child).getChildAt(0);
        }
      }
      if (textView != null && textView.getCurrentTextColor() != color) {
        textView.setTextColor(color);
      }
    }
  }

  /** Adds the segmented list look (insets, gaps, tile corners, header colors) to a SettingsAdapter list, once. */
  public static void attachListDecoration (RecyclerView recyclerView, List<ListItem> items) {
    if (!isEnabled() || recyclerView.getTag(R.id.xgram_m3e_list) != null) {
      return;
    }
    ListDecoration decoration = new ListDecoration(items);
    recyclerView.addItemDecoration(decoration, 0);
    recyclerView.setTag(R.id.xgram_m3e_list, decoration);
  }

  public static void detachListDecoration (RecyclerView recyclerView) {
    Object decoration = recyclerView.getTag(R.id.xgram_m3e_list);
    if (decoration instanceof RecyclerView.ItemDecoration) {
      recyclerView.removeItemDecoration((RecyclerView.ItemDecoration) decoration);
    }
    recyclerView.setTag(R.id.xgram_m3e_list, null);
  }

  public static boolean hasListDecoration (RecyclerView recyclerView) {
    return recyclerView.getTag(R.id.xgram_m3e_list) != null;
  }

  /**
   * A SettingsAdapter row with Telegram X's white background becomes a surfaceBright tile: RippleDrawable(onSurface
   * at .10, MorphDrawable(outer 20, inner 4, pressed 16), mask in the same shape). The list decoration sets its
   * segment every frame; outside a group it stays a plain rectangle in the theme's filling color.
   */
  public static void bindListRow (View rowView) {
    if (!isEnabled()) {
      return;
    }
    Object tag = rowView.getTag();
    if (!(tag instanceof ListItem) || !isRowType(((ListItem) tag).getViewType())) {
      return;
    }
    Drawable background = rowView.getBackground();
    if (background == null || findMorph(background) != null) {
      return;
    }
    if (Theme.getBackgroundColorId(rowView) != ColorId.filling) {
      return;
    }
    MorphDrawable morph = new MorphDrawable(0, CORNER_NONE, LIST_PRESSED_CORNER);
    morph.setColorRole(SURFACE_BRIGHT);
    morph.listRow = true;
    morph.ungroupedColorId = ColorId.filling;
    morph.setHost(rowView);
    boolean clickable = background instanceof CustomStateListDrawable ||
      (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && background instanceof android.graphics.drawable.RippleDrawable);
    if (clickable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      int rippleColor = withAlpha(color(ON_SURFACE), STATE_PRESSED);
      android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(rippleColor), morph, morph.newMask());
      morph.ripple = ripple;
      morph.rippleRole = ON_SURFACE;
      morph.rippleColor = rippleColor;
      rowView.setBackground(ripple);
    } else {
      rowView.setBackground(morph);
    }
    rowView.setTag(R.id.xgram_m3e_row, morph);
  }
}
