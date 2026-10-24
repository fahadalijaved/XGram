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
package com.fahadali.xgram.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3MediaChrome;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * The photo editor's trash for layers: it shows at the bottom while a layer (a text, an emoji, a sticker) is dragged,
 * where the tools were, and a layer let go over it is deleted. Telegram X look: a 48dp dark circle ringed in white with
 * a white bin, turning red while a layer is over it. Material 3 Expressive: a 56dp circle in the dark scheme's
 * surfaceContainerHigh with an onSurface bin, which grows on the spatial fast spring and turns errorContainer
 * (onErrorContainer bin) while a layer is over it.
 */
public final class XgramLayerTrash extends View implements FactorAnimator.Target {
  /** The view's side: room for the circle to grow. */
  public static final float VIEW_DP = 80f;
  private static final float CLASSIC_RADIUS_DP = 24f, EXPRESSIVE_RADIUS_DP = 28f;
  /** A finger this close to the circle's middle is over it (more than the circle: a layer hides it under the finger). */
  private static final float HIT_RADIUS_DP = 52f;

  private final boolean expressive;
  private final BoolAnimator shownAnimator;
  // Telegram X look: the red; Expressive: the colour and the growth, on a spring
  private @Nullable BoolAnimator activeAnimator;
  private @Nullable XgramExpressive.Spring activeSpring;
  private boolean active;
  private final @Nullable Drawable icon;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] location = new int[2];

  public XgramLayerTrash (Context context) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    this.shownAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    if (expressive) {
      activeSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    } else {
      activeAnimator = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 150L);
    }
    this.icon = Drawables.get(R.drawable.baseline_delete_24);
    setContentDescription(Lang.getString(R.string.XgramEditorTrash));
    setVisibility(View.INVISIBLE);
  }

  public void setShown (boolean shown, boolean animated) {
    if (!shown) {
      setActive(false);
    }
    XgramAppMotion.use(shownAnimator, shown ? XgramExpressive.SPATIAL_FAST : XgramExpressive.EFFECTS_FAST, true);
    shownAnimator.setValue(shown, animated);
    updateVisibility();
    invalidate();
  }

  public void setActive (boolean active) {
    if (this.active != active) {
      this.active = active;
      if (activeSpring != null) {
        activeSpring.animateTo(active ? 1f : 0f);
      } else if (activeAnimator != null) {
        activeAnimator.setValue(active, true);
      }
    }
  }

  /** Whether (rawX, rawY) on screen is over the trash while it shows. */
  public boolean isOver (float rawX, float rawY) {
    if (getVisibility() != View.VISIBLE || !shownAnimator.getValue() || getWidth() <= 0) {
      return false;
    }
    getLocationOnScreen(location);
    final float cx = location[0] + getWidth() / 2f, cy = location[1] + getHeight() / 2f;
    return Math.hypot(rawX - cx, rawY - cy) <= Screen.dp(HIT_RADIUS_DP);
  }

  private void updateVisibility () {
    setVisibility(shownAnimator.getFloatValue() > 0f ? View.VISIBLE : View.INVISIBLE);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == 0) {
      updateVisibility();
    }
    invalidate();
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final float shown = MathUtils.clamp(shownAnimator.getFloatValue());
    if (shown <= 0f) {
      return;
    }
    final float active = activeSpring != null ? Math.max(0f, Math.min(1.2f, activeSpring.getValue())) : (activeAnimator != null ? activeAnimator.getFloatValue() : 0f);
    final float colorFactor = MathUtils.clamp(active);
    final float cx = getWidth() / 2f, cy = getHeight() / 2f;
    final float radius = Screen.dpf(expressive ? EXPRESSIVE_RADIUS_DP : CLASSIC_RADIUS_DP) * (.6f + .4f * shown) * (1f + (expressive ? .2f : .15f) * active);
    final int iconColor;
    if (expressive) {
      final int fill = ColorUtils.fromToArgb(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), .9f), XgramM3MediaChrome.onMedia(XgramExpressive.ERROR_CONTAINER), colorFactor);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(ColorUtils.alphaColor(shown, fill));
      c.drawCircle(cx, cy, radius, paint);
      iconColor = ColorUtils.fromToArgb(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE), XgramM3MediaChrome.onMedia(XgramExpressive.ON_ERROR_CONTAINER), colorFactor);
    } else {
      final int fill = ColorUtils.fromToArgb(0x66000000, Theme.getColor(ColorId.textNegative, ThemeId.NIGHT_BLACK), colorFactor);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(ColorUtils.alphaColor(shown, fill));
      c.drawCircle(cx, cy, radius, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dpf(1.5f));
      paint.setColor(ColorUtils.alphaColor(shown * (1f - colorFactor), 0xffffffff));
      c.drawCircle(cx, cy, radius, paint);
      iconColor = 0xffffffff;
    }
    if (icon != null) {
      final Paint iconPaint = Paints.getPorterDuffPaint(iconColor);
      final int alpha = iconPaint.getAlpha();
      iconPaint.setAlpha((int) (255f * shown));
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, iconPaint);
      iconPaint.setAlpha(alpha);
    }
  }
}
