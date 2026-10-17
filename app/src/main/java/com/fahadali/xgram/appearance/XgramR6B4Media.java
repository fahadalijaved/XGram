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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E round 6, batch 4: the media viewer and editor, view-once media, the music player and web pages drawn with
 * Material 3 Expressive. Over media the colours come from the dark scheme ({@link XgramM3MediaChrome#onMedia}); on
 * pages they are the theme's roles. Nothing is allocated while drawing. Every helper is only used while
 * {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramR6B4Media {
  private XgramR6B4Media () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  private static float clamp01 (float v) {
    return Math.max(0f, Math.min(1f, v));
  }

  private static TextPaint newTextPaint (int ts, boolean tabular) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(ts)));
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      paint.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
      if (tabular) {
        paint.setFontFeatureSettings("tnum");
      }
    }
    return paint;
  }

  /** Baseline that centres {@code paint}'s glyphs on {@code cy}. */
  private static float centeredBaseline (TextPaint paint, float cy) {
    return cy - (paint.ascent() + paint.descent()) / 2f;
  }

  /** A glyph tinted {@code color} at {@code alpha}; the filter comes from Paints' cache and changes only with the colour. */
  private static final class GlyphPaint {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private int color;
    private boolean hasColor;

    Paint get (@ColorInt int color, float alpha) {
      final int opaque = color | 0xff000000;
      if (!hasColor || this.color != opaque) {
        hasColor = true;
        this.color = opaque;
        paint.setColorFilter(Paints.getColorFilter(opaque));
      }
      paint.setAlpha(Math.round(255f * clamp01(alpha) * ((color >>> 24) / 255f)));
      return paint;
    }
  }

  // Send screen: the strip of selected media

  /** Thumbnails, the gaps between them, the strip's side inset, its padding under the status bar and the scrim's fade below. */
  public static final float STRIP_THUMB_DP = 64f, STRIP_GAP_DP = 4f, STRIP_SIDE_DP = 16f, STRIP_TOP_DP = 8f, STRIP_FADE_DP = 32f;
  /** The remove badge, its touch target (the thumbnail's top-end corner) and its inset from that corner. */
  public static final float REMOVE_BADGE_DP = 24f, REMOVE_TARGET_DP = 48f, REMOVE_BADGE_INSET_DP = 4f;

  /** The strip's height under the status bar: 8dp, the 64dp thumbnails, then the top scrim's fade. */
  public static int stripHeight () {
    return Screen.dp(STRIP_TOP_DP + STRIP_THUMB_DP + STRIP_FADE_DP);
  }

  /**
   * The remove badge of a selected item: a 24dp errorContainer circle (dark scheme) with an onErrorContainer close glyph,
   * 4dp in from the thumbnail's top-end corner. It springs in (SPATIAL_FAST) when the strip shows it; static with
   * Reduce motion.
   */
  public static final class RemoveBadge {
    private final View host;
    private final XgramExpressive.Spring scale;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlyphPaint glyph = new GlyphPaint();
    private final @Nullable Drawable icon;

    public RemoveBadge (@NonNull View host) {
      this.host = host;
      this.icon = Drawables.get(XgramIconPack.map(R.drawable.baseline_close_18));
      this.scale = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 1f, (s, value) -> host.invalidate());
    }

    public void springIn () {
      if (XgramMotion.reduce()) {
        scale.setValue(1f);
        host.invalidate();
        return;
      }
      scale.setValue(0f);
      scale.animateTo(1f);
    }

    /** Draws in the host, a box at the thumbnail's top-end corner (top-start with {@code rtl}). */
    public void draw (@NonNull Canvas c, boolean rtl) {
      final float s = scale.getValue();
      if (s <= 0f) {
        return;
      }
      final float r = Screen.dpf(REMOVE_BADGE_DP) / 2f, inset = Screen.dpf(REMOVE_BADGE_INSET_DP);
      final float cx = rtl ? inset + r : host.getWidth() - inset - r;
      final float cy = inset + r;
      final int saveCount = c.save();
      if (s != 1f) {
        c.scale(s, s, cx, cy);
      }
      fill.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ERROR_CONTAINER));
      c.drawCircle(cx, cy, r, fill);
      if (icon != null) {
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, glyph.get(XgramM3MediaChrome.onMedia(XgramExpressive.ON_ERROR_CONTAINER), 1f));
      }
      c.restoreToCount(saveCount);
    }
  }

  // Self-destructing photo: the countdown chip

  /**
   * The self-destruct countdown over a secret photo, like the send screen's receiver chip: 32dp tall, full corners, the
   * dark scheme's surfaceContainerHigh at 60%, 16dp in from the start under the status bar. Inside, an 18dp
   * determinate wavy ring in primary (the time left) and the countdown in labelLarge with tabular figures. It fades in on
   * the effects spring; the wave is flat and the chip static with Reduce motion.
   */
  public static final class TimerChip {
    public static final float HEIGHT_DP = 32f, RING_DP = 18f, RING_STROKE_DP = 2f, EDGE_DP = 16f;
    private final View host;
    private final TextPaint textPaint = newTextPaint(XgramExpressive.TS_LABEL_L, true);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final XgramExpressive.Spring alpha;
    private @Nullable String text;
    private float textWidth;

    public TimerChip (@NonNull View host) {
      this.host = host;
      this.alpha = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> host.invalidate());
    }

    public void setText (@Nullable String text) {
      if (text == null ? this.text != null : !text.equals(this.text)) {
        this.text = text;
        this.textWidth = text != null ? textPaint.measureText(text) : 0f;
      }
    }

    /** Fades the chip in (shown at once with Reduce motion). */
    public void show () {
      if (XgramMotion.reduce()) {
        alpha.setValue(1f);
        host.invalidate();
      } else {
        alpha.animateTo(1f);
      }
    }

    /** The chip's bottom edge, for a host that sizes itself to it. */
    public static int bottom (int topOffset) {
      return topOffset + Screen.dp(EDGE_DP + HEIGHT_DP);
    }

    /** @param progress the time left, 1 to 0 */
    public void draw (@NonNull Canvas c, int topOffset, float progress) {
      final float a = clamp01(alpha.getValue());
      if (a <= 0f || text == null) {
        return;
      }
      final boolean rtl = Lang.rtl();
      final float h = Screen.dpf(HEIGHT_DP), pad = Screen.dpf(8f), ring = Screen.dpf(RING_DP), gap = Screen.dpf(8f), end = Screen.dpf(16f);
      final float width = pad + ring + gap + textWidth + end;
      final float left = rtl ? host.getWidth() - Screen.dpf(EDGE_DP) - width : Screen.dpf(EDGE_DP);
      final float top = topOffset + Screen.dpf(EDGE_DP);
      rect.set(left, top, left + width, top + h);
      fill.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), .6f * a));
      c.drawRoundRect(rect, h / 2f, h / 2f, fill);

      final float cy = top + h / 2f;
      final float ringCx = rtl ? rect.right - pad - ring / 2f : rect.left + pad + ring / 2f;
      final float stroke = Screen.dpf(RING_STROKE_DP);
      final boolean wave = !XgramMotion.reduce();
      final float amplitude = wave ? stroke * .4f : 0f;
      progress = clamp01(progress);
      XgramWavyProgress.drawCircular(c, ringCx, cy, ring / 2f - stroke / 2f, stroke, progress,
        XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.PRIMARY), a),
        XgramExpressive.withAlpha(XgramM3MediaChrome.white(XgramM3MediaChrome.TRACK_INACTIVE_ALPHA), a), amplitude);

      textPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE), a));
      final float textX = rtl ? ringCx - ring / 2f - gap - textWidth : ringCx + ring / 2f + gap;
      c.drawText(text, textX, centeredBaseline(textPaint, cy), textPaint);

      if (wave && progress > 0f && progress < 1f && host.getWindowToken() != null) {
        host.postInvalidateOnAnimation(); // the wave travels one wavelength a second
      }
    }
  }

  // View-once voice and round video

  /** The view-once scrim: black at 75%. */
  public static final float ONCE_SCRIM_ALPHA = .75f;

  /**
   * The view-once label: the fire glyph in a 40dp tertiaryContainer circle next to a "Plays once" chip (32dp, full
   * corners, the dark scheme's surfaceContainerHigh, labelLarge), centred on a point. Mirrored in RTL.
   */
  public static final class OnceRow {
    public static final float CIRCLE_DP = 40f, CHIP_DP = 32f, GAP_DP = 8f;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlyphPaint glyph = new GlyphPaint();
    private final TextPaint textPaint = newTextPaint(XgramExpressive.TS_LABEL_L, false);
    private final RectF rect = new RectF();
    private final @Nullable Drawable icon;
    private final String text;
    private final float textWidth;

    public OnceRow (@DrawableRes int fireIcon) {
      this.icon = Drawables.get(XgramIconPack.map(fireIcon));
      this.text = Lang.getString(R.string.XgramR6B4PlaysOnce);
      this.textWidth = textPaint.measureText(text);
    }

    public static int heightPx () {
      return Screen.dp(CIRCLE_DP);
    }

    public void draw (@NonNull Canvas c, float cx, float cy, float alpha) {
      final float a = clamp01(alpha);
      if (a <= 0f) {
        return;
      }
      final boolean rtl = Lang.rtl();
      final float circle = Screen.dpf(CIRCLE_DP), chipH = Screen.dpf(CHIP_DP), gap = Screen.dpf(GAP_DP), pad = Screen.dpf(16f);
      final float chipW = pad + textWidth + pad;
      final float total = circle + gap + chipW;
      final float start = cx - total / 2f;
      final float circleCx = rtl ? start + total - circle / 2f : start + circle / 2f;
      final float chipLeft = rtl ? start : start + circle + gap;

      fill.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.TERTIARY_CONTAINER), a));
      c.drawCircle(circleCx, cy, circle / 2f, fill);
      if (icon != null) {
        Drawables.draw(c, icon, circleCx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, glyph.get(XgramM3MediaChrome.onMedia(XgramExpressive.ON_TERTIARY_CONTAINER), a));
      }

      rect.set(chipLeft, cy - chipH / 2f, chipLeft + chipW, cy + chipH / 2f);
      fill.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), a));
      c.drawRoundRect(rect, chipH / 2f, chipH / 2f, fill);
      textPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE), a));
      c.drawText(text, chipLeft + pad, centeredBaseline(textPaint, cy), textPaint);
    }
  }

  /**
   * The card a view-once voice message sits on: the theme's floating surfaceContainerHigh (the message keeps the
   * theme's own colours, so the card does too), 28dp corners.
   */
  public static void drawOnceCard (@NonNull Canvas c, @NonNull RectF rect, float alpha, @NonNull Paint paint) {
    final float a = clamp01(alpha);
    if (a <= 0f || rect.isEmpty()) {
      return;
    }
    final float radius = Math.min(Screen.dpf(XgramExpressive.CORNER_XL), Math.min(rect.width(), rect.height()) / 2f);
    paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramAmoled.floatingRole(XgramExpressive.SURFACE_CONTAINER_HIGH)), a));
    c.drawRoundRect(rect, radius, radius, paint);
  }

  // Music player

  /**
   * The music player's controls as an M3E button group drawn in one pass (the player's own buttons stay on top of it,
   * transparent, for touches and accessibility): shuffle and repeat are toggle icon buttons (a 40dp secondaryContainer
   * container with 12dp corners while on), previous and next 56dp filled tonal icon buttons, play a 96 x 72dp primary
   * button whose corners go from full while paused to 24dp while playing, its glyph morphing. The pressed button grows
   * by 15% of its width while its neighbours give way, and its corners tighten; widths, shapes and colours spring. Not
   * mirrored in RTL (playback controls keep their direction).
   */
  public static final class PlayerControls {
    public static final int SHUFFLE = 0, PREVIOUS = 1, PLAY = 2, NEXT = 3, REPEAT = 4, COUNT = 5;
    public static final float HEIGHT_DP = 72f;
    private static final float[] BASE_W = {48f, 56f, 96f, 56f, 48f};
    private static final float[] BASE_H = {48f, 56f, 72f, 56f, 48f};
    private static final float GROW = .15f;

    private final View host;
    private final XgramExpressive.Spring[] weight = new XgramExpressive.Spring[COUNT];
    private final XgramExpressive.Spring[] press = new XgramExpressive.Spring[COUNT];
    private final XgramExpressive.Spring shuffleOn, repeatOn, playing;
    private final @Nullable Drawable shuffleIcon, previousIcon, nextIcon, repeatIcon, repeatOneIcon;
    private boolean repeatOne;
    private int pressedIndex = -1;

    private final RectF[] slots = new RectF[COUNT], base = new RectF[COUNT];
    private final RectF box = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlyphPaint glyph = new GlyphPaint();
    private final android.graphics.Path playPath = new android.graphics.Path();
    private float playDrawFactor = -1f;

    public PlayerControls (@NonNull View host, boolean shuffle, boolean repeat, boolean repeatOne, boolean isPlaying) {
      this.host = host;
      XgramExpressive.Spring.Listener invalidate = (s, value) -> host.invalidate();
      for (int i = 0; i < COUNT; i++) {
        weight[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 1f, invalidate).setThreshold(.002f);
        press[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate);
        slots[i] = new RectF();
        base[i] = new RectF();
      }
      shuffleOn = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, shuffle ? 1f : 0f, invalidate);
      repeatOn = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, repeat ? 1f : 0f, invalidate);
      playing = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, isPlaying ? 1f : 0f, invalidate);
      this.repeatOne = repeatOne;
      shuffleIcon = Drawables.get(R.drawable.round_shuffle_24);
      previousIcon = Drawables.get(R.drawable.round_skip_previous_36);
      nextIcon = Drawables.get(R.drawable.round_skip_next_36);
      repeatIcon = Drawables.get(R.drawable.round_repeat_24);
      repeatOneIcon = Drawables.get(R.drawable.round_repeat_one_24);
    }

    private static void set (XgramExpressive.Spring spring, boolean on, boolean animated) {
      if (animated) {
        spring.animateTo(on ? 1f : 0f);
      } else {
        spring.setValue(on ? 1f : 0f);
      }
    }

    public void setShuffle (boolean on, boolean animated) {
      set(shuffleOn, on, animated);
      host.invalidate();
    }

    public void setRepeat (boolean on, boolean one, boolean animated) {
      set(repeatOn, on, animated);
      this.repeatOne = one;
      host.invalidate();
    }

    public void setPlaying (boolean on, boolean animated) {
      set(playing, on, animated);
      host.invalidate();
    }

    /** 0 paused, 1 playing (spring), for the seek bar's wave. */
    public float playingFactor () {
      return clamp01(playing.getValue());
    }

    /** The pressed button ({@link #SHUFFLE} ... {@link #REPEAT}), or -1. */
    public void setPressed (int index) {
      if (this.pressedIndex == index) {
        return;
      }
      this.pressedIndex = index;
      for (int i = 0; i < COUNT; i++) {
        float target = 1f;
        if (index != -1) {
          if (i == index) {
            target = 1f + GROW;
          } else if (Math.abs(i - index) == 1) {
            int neighbours = (index > 0 ? 1 : 0) + (index < COUNT - 1 ? 1 : 0);
            target = 1f - GROW / neighbours;
          }
        }
        weight[i].animateTo(target);
        press[i].animateTo(i == index ? 1f : 0f);
      }
    }

    /** Lays the buttons out centred on (cx, cy) within {@code availableWidth}. */
    public void layout (float cx, float cy, float availableWidth) {
      float sumBase = 0f, sum = 0f;
      for (int i = 0; i < COUNT; i++) {
        sumBase += Screen.dpf(BASE_W[i]);
        sum += Screen.dpf(BASE_W[i]) * Math.max(.1f, weight[i].getValue());
      }
      final float gap = Math.max(Screen.dpf(2f), Math.min(Screen.dpf(24f), (availableWidth - sumBase) / (COUNT - 1)));
      float x = cx - (sum + gap * (COUNT - 1)) / 2f;
      float bx = cx - (sumBase + gap * (COUNT - 1)) / 2f;
      for (int i = 0; i < COUNT; i++) {
        final float w = Screen.dpf(BASE_W[i]) * Math.max(.1f, weight[i].getValue()), h = Screen.dpf(BASE_H[i]);
        slots[i].set(x, cy - h / 2f, x + w, cy + h / 2f);
        x += w + gap;
        final float bw = Screen.dpf(BASE_W[i]);
        base[i].set(bx, cy - h / 2f, bx + bw, cy + h / 2f);
        bx += bw + gap;
      }
    }

    /** The resting bounds of a button (where its touch target goes). */
    @NonNull
    public RectF slot (int index) {
      return base[index];
    }

    private static float lerp (float from, float to, float f) {
      return from + (to - from) * f;
    }

    private void drawContainer (Canvas c, RectF rect, float radius, @ColorInt int container, @ColorInt int content, float pressed) {
      if ((container >>> 24) != 0) {
        fill.setColor(container);
        c.drawRoundRect(rect, radius, radius, fill);
      }
      if (pressed > 0f) {
        fill.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * pressed));
        c.drawRoundRect(rect, radius, radius, fill);
      }
    }

    private void drawIcon (Canvas c, @Nullable Drawable icon, float cx, float cy, float sizeDp, @ColorInt int color) {
      if (icon == null || icon.getMinimumWidth() <= 0) {
        return;
      }
      final float scale = Screen.dpf(sizeDp) / icon.getMinimumWidth();
      final int saveCount = c.save();
      if (scale != 1f) {
        c.scale(scale, scale, cx, cy);
      }
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, glyph.get(color, 1f));
      c.restoreToCount(saveCount);
    }

    public void draw (@NonNull Canvas c) {
      final int onSurfaceVariant = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      final int secondaryContainer = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      final int onSecondaryContainer = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
      for (int i = 0; i < COUNT; i++) {
        final RectF slot = slots[i];
        final float pressed = clamp01(press[i].getValue());
        final float cx = slot.centerX(), cy = slot.centerY();
        switch (i) {
          case SHUFFLE:
          case REPEAT: {
            final float on = clamp01((i == SHUFFLE ? shuffleOn : repeatOn).getValue());
            final float size = Math.min(Screen.dpf(XgramExpressiveC.ICON_BUTTON_BOX), slot.width());
            box.set(cx - size / 2f, cy - Screen.dpf(XgramExpressiveC.ICON_BUTTON_BOX) / 2f, cx + size / 2f, cy + Screen.dpf(XgramExpressiveC.ICON_BUTTON_BOX) / 2f);
            float radius = lerp(box.height() / 2f, Screen.dpf(XgramExpressive.CORNER_M), on);
            radius = lerp(radius, Screen.dpf(XgramExpressive.CORNER_S), pressed);
            final int content = me.vkryl.core.ColorUtils.fromToArgb(onSurfaceVariant, onSecondaryContainer, on);
            drawContainer(c, box, radius, XgramExpressive.withAlpha(secondaryContainer, on), content, pressed);
            drawIcon(c, i == SHUFFLE ? shuffleIcon : repeatOne ? repeatOneIcon : repeatIcon, cx, cy, 24f, content);
            break;
          }
          case PREVIOUS:
          case NEXT: {
            final float radius = lerp(slot.height() / 2f, Screen.dpf(XgramExpressive.CORNER_L), pressed);
            drawContainer(c, slot, radius, secondaryContainer, onSecondaryContainer, pressed);
            drawIcon(c, i == PREVIOUS ? previousIcon : nextIcon, cx, cy, 28f, onSecondaryContainer);
            break;
          }
          case PLAY: {
            final float isPlaying = Math.max(0f, Math.min(1.2f, playing.getValue()));
            float radius = Math.max(0f, Math.min(slot.height() / 2f, lerp(slot.height() / 2f, Screen.dpf(24f), isPlaying)));
            radius = lerp(radius, Screen.dpf(XgramExpressive.CORNER_L), pressed);
            final int container = XgramExpressive.color(XgramExpressive.PRIMARY), content = XgramExpressive.color(XgramExpressive.ON_PRIMARY);
            drawContainer(c, slot, radius, container, content, pressed);
            final float factor = clamp01(playing.getValue());
            org.thunderdog.challegram.tool.DrawAlgorithms.drawPlayPause(c, Math.round(cx), Math.round(cy), Math.round(Screen.dpf(72f) * .2f), playPath, playDrawFactor, playDrawFactor = factor, 1f, content);
            break;
          }
        }
      }
    }
  }

  // Web pages

  /**
   * The flat linear progress under a web page's app bar (M3 LinearProgressIndicator: 4dp, primary over a
   * secondaryContainer track with a gap and a stop dot). The progress springs forward, never back within one load;
   * at 100% the bar fades out, and a later load fades it in again.
   */
  public static final class LinearProgressView extends View {
    public static final float HEIGHT_DP = 4f;
    private final XgramExpressive.Spring progress, alpha;

    public LinearProgressView (Context context) {
      super(context);
      alpha = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidate());
      progress = new XgramExpressive.Spring(XgramExpressive.STD_SPATIAL_DEFAULT, 0f, (s, value) -> invalidate());
      progress.setEndListener(() -> {
        if (progress.getTarget() >= 1f) {
          alpha.animateTo(0f);
        }
      });
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setProgress (float value) {
      value = clamp01(value);
      if (alpha.getTarget() == 0f) {
        if (value >= 1f) {
          return; // a load that is already done: nothing to show
        }
        progress.setValue(0f);
        alpha.animateTo(1f);
      } else if (value < progress.getTarget()) {
        return;
      }
      progress.animateTo(value);
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw (Canvas c) {
      final float a = clamp01(alpha.getValue());
      if (a <= 0f) {
        return;
      }
      XgramWavyLinear.draw(c, 0f, getWidth(), getHeight() / 2f, progress.getValue(),
        XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), a),
        XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), a),
        Screen.dpf(HEIGHT_DP), 0f, 0f, Screen.dpf(4f), true);
    }
  }

  /**
   * A page that failed to load: the empty state (icon on a soft burst, a headline, the reason) with a tonal Retry under
   * it, on the page surface. It fades in on the effects spring.
   */
  public static final class LoadErrorView extends FrameLayout {
    private final XgramListStateView stateView;
    private final TextView retryButton;

    public LoadErrorView (Context context) {
      super(context);
      setWillNotDraw(false);
      setClickable(true); // nothing under it takes touches while it shows

      LinearLayout column = new LinearLayout(context);
      column.setOrientation(LinearLayout.VERTICAL);
      column.setGravity(Gravity.CENTER_HORIZONTAL);
      column.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

      stateView = new XgramListStateView(context);
      stateView.setWrapContent(true);
      stateView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      column.addView(stateView);

      retryButton = XgramR5Sheets.tonalButton(context, Lang.getString(R.string.XgramR6B4Retry), null);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(XgramM3Sheets.BUTTON_HEIGHT));
      params.topMargin = Screen.dp(16f);
      params.bottomMargin = Screen.dp(24f);
      retryButton.setLayoutParams(params);
      column.addView(retryButton);

      addView(column);
      setAlpha(0f);
      setVisibility(View.GONE);
    }

    private @Nullable XgramExpressive.Spring fade;

    private XgramExpressive.Spring fade () {
      if (fade == null) {
        fade = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, getAlpha(), (s, value) -> setAlpha(clamp01(value)));
        fade.setEndListener(() -> {
          if (fade != null && fade.getTarget() == 0f) {
            setVisibility(View.GONE);
          }
        });
      }
      return fade;
    }

    public void setOnRetry (@Nullable View.OnClickListener listener) {
      retryButton.setOnClickListener(listener);
    }

    public void show (@Nullable CharSequence reason) {
      stateView.setEmpty(XgramIconPack.map(R.drawable.baseline_language_24), Lang.getString(R.string.XgramR6B4WebErrorTitle),
        reason != null && reason.length() > 0 ? reason : Lang.getString(R.string.XgramR6B4WebErrorText));
      setVisibility(View.VISIBLE);
      fade().animateTo(1f);
    }

    public void hide () {
      if (getVisibility() == View.VISIBLE) {
        fade().animateTo(0f);
      }
    }

    public boolean isShowing () {
      return getVisibility() == View.VISIBLE && fade().getTarget() == 1f;
    }

    @Override
    protected void onDraw (Canvas c) {
      c.drawColor(XgramExpressive.color(XgramExpressive.SURFACE));
    }
  }
}
