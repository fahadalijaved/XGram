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

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import org.thunderdog.challegram.R;

/**
 * Telegram X's small glyphs next to a title: the mute speaker after a chat's, topic's or group's name (chat list rows
 * in every view, topic rows, community rows, the chat header, the theme editor's preview) and the secret chat lock in
 * front of it. Their drawables are a 24dp (or 18dp) box with a glyph of about 12dp in the middle, the box being what
 * the callers lay out and draw. An icon pack, and the Classic pack with Expressive design (the whole Line table, see
 * XgramIconPackTable.classic), puts its full size icon in their place, which fills the box: a mute bell of about 17dp,
 * far taller than the title's capitals. Here they take the pack's small form (its 16dp bell, its 16dp and 12dp locks)
 * centred in Telegram X's box, so the glyph has Telegram X's size and place and the space after the title stays.
 * Without a pack mapping them, Telegram X's own drawables load as before.
 */
public final class XgramTitleGlyphs {
  private XgramTitleGlyphs () { }

  /** Telegram X's drawable, the Telegram X drawable whose pack icon is the small form, Telegram X's box in dp. */
  private static final int[] GLYPHS = {
    R.drawable.deproko_baseline_notifications_off_24, R.drawable.baseline_notifications_off_16, 24,
    R.drawable.deproko_baseline_lock_24, R.drawable.baseline_lock_16, 24,
    R.drawable.deproko_baseline_lock_18, R.drawable.baseline_lock_14, 18,
  };

  /** Drawables.load: the boxed small form of {@code res}, or null to load {@code res} the usual way. */
  @Nullable
  public static Drawable load (Resources resources, int res) {
    for (int i = 0; i + 2 < GLYPHS.length; i += 3) {
      if (GLYPHS[i] != res) {
        continue;
      }
      if (XgramIconPack.map(res) == res) {
        return null; // Telegram X's own glyph
      }
      final Drawable glyph = ResourcesCompat.getDrawable(resources, XgramIconPack.map(GLYPHS[i + 1]), null);
      if (glyph == null) {
        return null;
      }
      return new Boxed(glyph, Math.round(GLYPHS[i + 2] * resources.getDisplayMetrics().density));
    }
    return null;
  }

  /** A drawable of Telegram X's box size drawing {@code glyph} in its middle, at its own size (scaled with the box). */
  private static final class Boxed extends Drawable {
    private final Drawable glyph;
    private final int box;
    private @Nullable ColorFilter colorFilter;
    private int alpha = 255;

    Boxed (Drawable glyph, int box) {
      this.glyph = glyph;
      this.box = box;
    }

    @Override
    public int getIntrinsicWidth () {
      return box;
    }

    @Override
    public int getIntrinsicHeight () {
      return box;
    }

    @Override
    protected void onBoundsChange (@NonNull Rect bounds) {
      final float scale = box > 0 ? Math.min(bounds.width(), bounds.height()) / (float) box : 1f;
      final int width = Math.round(glyph.getIntrinsicWidth() * scale), height = Math.round(glyph.getIntrinsicHeight() * scale);
      final int left = bounds.left + (bounds.width() - width) / 2, top = bounds.top + (bounds.height() - height) / 2;
      glyph.setBounds(left, top, left + width, top + height);
    }

    @Override
    public void draw (@NonNull Canvas canvas) {
      glyph.draw(canvas);
    }

    @Override
    public void setAlpha (int alpha) {
      this.alpha = alpha;
      glyph.setAlpha(alpha);
    }

    @Override
    public int getAlpha () {
      return alpha;
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      this.colorFilter = colorFilter;
      glyph.setColorFilter(colorFilter);
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter () {
      return colorFilter;
    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }

    @NonNull
    @Override
    public Drawable mutate () {
      glyph.mutate();
      return this;
    }
  }
}
