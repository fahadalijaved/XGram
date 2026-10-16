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

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.support.CircleDrawable;
import org.thunderdog.challegram.support.RectDrawable;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive helpers for the header, sheet and input bar parts.
 *
 * A standard icon button has no container: only its state layer shows. The state layer is a
 * box (40dp for the small icon button) centered in the touch target. It has full corners at
 * rest and morphs to small (8dp) corners while pressed, with the spatial fast spring.
 */
public final class XgramExpressiveC {
  private XgramExpressiveC () { }

  public static final float ICON_BUTTON_BOX = 40f;

  /** Ripple whose mask is the morphing state layer box. The ripple color is the content color at the pressed state-layer opacity. */
  public static RippleDrawable iconButtonRipple (@ColorInt int contentColor, float boxDp) {
    return new RippleDrawable(ColorStateList.valueOf(stateLayerColor(contentColor)), null, new PressMorphMask(boxDp, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S));
  }

  public static @ColorInt int stateLayerColor (@ColorInt int contentColor) {
    return XgramExpressive.withAlpha(contentColor, XgramExpressive.STATE_PRESSED);
  }

  // State layer selectors: Material 3 replacements for Telegram X's grey ripples (Theme.RIPPLE_COLOR, 0x50a0a0a0 and
  // friends). The ripple is the content color of the surface under it (onSurface on surfaces, onPrimary on primary,
  // onSecondaryContainer on secondaryContainer...) at the pressed state-layer opacity, .10. Expressive design needs
  // Android 13, where the framework ripple draws its color's alpha as is (androidx.compose.material.ripple doubles the
  // alpha only below API 28), so .10 shows as .10, like Compose's own ripples on the Compose settings pages.

  /** List tile press, the shape of XgramM3Profile.drawSelectionTile: inset 8dp / 2dp, large (16dp) corners. */
  private static final float TILE_INSET_X = 8f, TILE_INSET_Y = 2f;

  /**
   * A ripple in the M3 pressed state layer. Its content color is read again on every state change, so day/night,
   * theme and dynamic color switches need no listener. With a {@link FillingDrawable} the content color follows that
   * fill's color id (Theme.changeBackgroundColorId keeps working, the fill stays the first layer).
   */
  public static final class StateLayerRipple extends RippleDrawable {
    private final int fallbackRole;
    private final int backgroundColorId;
    private final @Nullable FillingDrawable filling;
    private int contentColor;

    private StateLayerRipple (int fallbackRole, int backgroundColorId, @Nullable FillingDrawable filling, @Nullable Drawable content, @Nullable Drawable mask) {
      this(fallbackRole, backgroundColorId, filling, content, mask, contentColorOf(fallbackRole, filling != null ? filling.getColorId() : backgroundColorId));
    }

    private StateLayerRipple (int fallbackRole, int backgroundColorId, @Nullable FillingDrawable filling, @Nullable Drawable content, @Nullable Drawable mask, @ColorInt int contentColor) {
      super(ColorStateList.valueOf(stateLayerColor(contentColor)), content, mask);
      this.fallbackRole = fallbackRole;
      this.backgroundColorId = backgroundColorId;
      this.filling = filling;
      this.contentColor = contentColor;
    }

    @Override
    protected boolean onStateChange (@NonNull int[] stateSet) {
      int now = contentColorOf(fallbackRole, filling != null ? filling.getColorId() : backgroundColorId);
      if (now != contentColor) {
        contentColor = now;
        setColor(ColorStateList.valueOf(stateLayerColor(now)));
      }
      return super.onStateChange(stateSet);
    }
  }

  /**
   * The content color drawn on the Telegram X background {@code backgroundColorId}: its M3 on-role (see
   * XgramM3Colors.contentRoleOf), black or white by luminance for an id outside the table, {@code fallbackRole} without
   * a background.
   */
  @ColorInt
  public static int contentColorOf (int fallbackRole, int backgroundColorId) {
    if (backgroundColorId != 0) {
      int role = XgramM3Colors.contentRoleOf(backgroundColorId);
      if (role >= 0) {
        return XgramExpressive.color(role);
      }
      int background = Theme.getColor(backgroundColorId);
      if ((background >>> 24) != 0) {
        return androidx.core.graphics.ColorUtils.calculateLuminance(background | 0xff000000) > .5 ? 0xff000000 : 0xffffffff;
      }
    }
    return XgramExpressive.color(fallbackRole);
  }

  private static Drawable roundMask (float radiusDp) {
    if (radiusDp <= 0f) {
      return new ColorDrawable(0xffffffff);
    }
    GradientDrawable mask = new GradientDrawable();
    mask.setColor(0xffffffff);
    mask.setCornerRadius(Screen.dp(radiusDp));
    return mask;
  }

  private static Drawable tileMask () {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(0xffffffff);
    shape.setCornerRadius(Screen.dp(XgramExpressive.CORNER_L));
    int x = Screen.dp(TILE_INSET_X), y = Screen.dp(TILE_INSET_Y);
    return new InsetDrawable(shape, x, y, x, y);
  }

  /** Theme.fillingSelector: the fill {@code backgroundColorId} (corner {@code radiusDp}) under its content color's state layer. */
  public static Drawable fillingSelector (int backgroundColorId, float radiusDp) {
    FillingDrawable filling = radiusDp > 0f ? new FillingDrawable(backgroundColorId, radiusDp) : new FillingDrawable(backgroundColorId);
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, backgroundColorId, filling, filling, roundMask(radiusDp));
  }

  /** A filled list row whose press is the list tile (chat rows): see {@link #listTileSelector}. */
  public static Drawable fillingTileSelector (int backgroundColorId) {
    FillingDrawable filling = new FillingDrawable(backgroundColorId);
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, backgroundColorId, filling, filling, tileMask());
  }

  /** Theme.transparentSelector / transparentRoundSelector: an onSurface state layer over whatever is behind. */
  public static Drawable transparentSelector (float radiusDp) {
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, 0, null, null, roundMask(radiusDp));
  }

  /** Chat list rows: an onSurface state layer in the selection tile's shape, so pressed and selected rows match. */
  public static Drawable listTileSelector () {
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, 0, null, null, tileMask());
  }

  /** M3 standard icon button: a 40dp onSurfaceVariant state layer, full corners springing to 8dp while pressed. */
  public static Drawable iconButtonSelector () {
    return new StateLayerRipple(XgramExpressive.ON_SURFACE_VARIANT, 0, null, null, new PressMorphMask(ICON_BUTTON_BOX, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S));
  }

  /** Theme.circleSelector: the circle {@code colorId}, its content color's state layer clipped to it. */
  public static Drawable circleSelector (float size, int colorId) {
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, colorId, null, new CircleDrawable(colorId, size, false), null);
  }

  /** Theme.rectSelector: the rounded rect {@code colorId}, its content color's state layer clipped to it. */
  public static Drawable rectSelector (float size, float padding, int colorId) {
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, colorId, null, new RectDrawable(colorId, size, padding, false), null);
  }

  /** Theme.customSelector: {@code content} under an onSurface state layer clipped to it. */
  public static Drawable customSelector (@Nullable Drawable content) {
    return new StateLayerRipple(XgramExpressive.ON_SURFACE, 0, null, content, null);
  }

  /** Corner radius in px between resting and pressed, for views that draw their own container. progress 0 = resting, 1 = pressed; may overshoot. */
  public static float morphCorner (float restingDp, float pressedDp, float width, float height, float progress) {
    float resting = XgramExpressive.cornerPx(restingDp, width, height);
    float pressed = XgramExpressive.cornerPx(pressedDp, width, height);
    float radius = resting + (pressed - resting) * progress;
    return Math.max(0f, Math.min(radius, Math.min(width, height) / 2f));
  }

  /** Opaque mask shape for a RippleDrawable: a centered box whose corners spring between two tokens on press. */
  public static final class PressMorphMask extends Drawable {
    private final float boxDp, restingDp, pressedDp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final XgramExpressive.Spring progress;
    private boolean pressed;

    public PressMorphMask (float boxDp, float restingDp, float pressedDp) {
      this.boxDp = boxDp;
      this.restingDp = restingDp;
      this.pressedDp = pressedDp;
      this.paint.setColor(0xffffffff);
      this.progress = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidateSelf());
      this.progress.setThreshold(.005f);
    }

    @Override
    public boolean isStateful () {
      return true;
    }

    @Override
    protected boolean onStateChange (@NonNull int[] state) {
      boolean isPressed = false;
      for (int item : state) {
        if (item == android.R.attr.state_pressed) {
          isPressed = true;
          break;
        }
      }
      if (this.pressed != isPressed) {
        this.pressed = isPressed;
        progress.animateTo(isPressed ? 1f : 0f);
        return true;
      }
      return false;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      float size = Screen.dp(boxDp);
      float width = Math.min(size, bounds.width());
      float height = Math.min(size, bounds.height());
      float cx = bounds.exactCenterX(), cy = bounds.exactCenterY();
      rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
      float radius = morphCorner(restingDp, pressedDp, width, height, progress.getValue());
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha (int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
