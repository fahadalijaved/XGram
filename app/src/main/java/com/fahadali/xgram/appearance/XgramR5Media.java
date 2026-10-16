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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/**
 * XGram M3E round 5, media and attach: shared pieces of the media editor, the camera and the viewer drawn with Material
 * 3 Expressive. Media stays dark whatever the app theme, so colours come from the dark scheme
 * ({@link XgramM3MediaChrome#onMedia}). Every helper does nothing with Expressive design off.
 */
public final class XgramR5Media {
  private XgramR5Media () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Text over media

  /** Tabular figures, for times, durations, counters and values. */
  public static void tabular (@Nullable TextView v) {
    if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      v.setFontFeatureSettings("tnum");
    }
  }

  /** {@code ts} type style in white at {@code alpha}, for a label over media. */
  public static void onMediaLabel (@Nullable TextView v, int ts, float alpha) {
    if (v == null || !on()) {
      return;
    }
    XgramExpressive.applyText(v, ts, false);
    v.setTextColor(XgramM3MediaChrome.white(alpha));
    v.setIncludeFontPadding(false);
  }

  /**
   * A value chip over media: labelMedium with tabular figures in the dark scheme's onPrimary, on a full-corner primary
   * container with 8dp side and 2dp vertical padding.
   */
  public static void onMediaValueChip (@Nullable TextView v) {
    if (v == null || !on()) {
      return;
    }
    XgramExpressive.applyText(v, XgramExpressive.TS_LABEL_M, false);
    tabular(v);
    v.setIncludeFontPadding(false);
    v.setTextColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_PRIMARY));
    GradientDrawable chip = new GradientDrawable();
    chip.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.PRIMARY));
    chip.setCornerRadius(Screen.dp(100f)); // clamped to half the height: full corners
    v.setBackground(chip);
    v.setPadding(Screen.dp(8f), Screen.dp(2f), Screen.dp(8f), Screen.dp(2f));
  }

  /** A full-corner container over media: the dark scheme's surfaceContainerHigh at 60%. */
  @NonNull
  public static GradientDrawable onMediaPill () {
    GradientDrawable pill = new GradientDrawable();
    pill.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), .6f));
    pill.setCornerRadius(Screen.dp(100f));
    return pill;
  }

  // Colour swatches

  /**
   * A 32dp colour swatch (the photo editor's tint colours): a circle at rest; selected, its corners morph to 12dp
   * (CORNER_M) on the spatial fast spring and a 2dp white ring shows 3dp around it. Snaps with Reduce motion.
   */
  public static final class Swatch extends View {
    public static final float SIZE_DP = 32f, RING_GAP_DP = 3f, RING_WIDTH_DP = 2f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final XgramExpressive.Spring selected;
    private @ColorId int colorId = ColorId.white;
    private @ColorInt int fixedColor;
    private boolean useFixedColor;
    private boolean checked;

    public Swatch (Context context) {
      super(context);
      ring.setStyle(Paint.Style.STROKE);
      ring.setStrokeWidth(Screen.dp(RING_WIDTH_DP));
      selected = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    }

    public void setColorId (@ColorId int colorId) {
      if (this.colorId != colorId || useFixedColor) {
        this.colorId = colorId;
        this.useFixedColor = false;
        invalidate();
      }
    }

    public void setColor (@ColorInt int color) {
      if (this.fixedColor != color || !useFixedColor) {
        this.fixedColor = color;
        this.useFixedColor = true;
        invalidate();
      }
    }

    public boolean isChecked () {
      return checked;
    }

    public void setChecked (boolean checked, boolean animated) {
      if (this.checked != checked) {
        this.checked = checked;
        if (animated) {
          selected.animateTo(checked ? 1f : 0f);
        } else {
          selected.setValue(checked ? 1f : 0f);
          invalidate();
        }
      }
      setSelected(checked);
    }

    @Override
    protected void onDraw (Canvas c) {
      final float size = Math.min(Screen.dpf(SIZE_DP), Math.min(getWidth(), getHeight()) - Screen.dpf(RING_GAP_DP + RING_WIDTH_DP) * 2f);
      if (size <= 0f) {
        return;
      }
      final float cx = getWidth() / 2f, cy = getHeight() / 2f;
      final float s = Math.max(0f, Math.min(1.2f, selected.getValue()));
      final float full = size / 2f, morphed = Screen.dpf(XgramExpressive.CORNER_M);
      final float radius = Math.max(0f, Math.min(full, full + (morphed - full) * s));
      rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
      fill.setColor(useFixedColor ? fixedColor : Theme.getColor(colorId));
      c.drawRoundRect(rect, radius, radius, fill);
      final float ringAlpha = Math.max(0f, Math.min(1f, s));
      if (ringAlpha > 0f) {
        final float out = Screen.dpf(RING_GAP_DP) + Screen.dpf(RING_WIDTH_DP) / 2f;
        rect.inset(-out, -out);
        ring.setColor(XgramM3MediaChrome.white(ringAlpha));
        c.drawRoundRect(rect, radius + out, radius + out, ring);
      }
    }
  }
}
