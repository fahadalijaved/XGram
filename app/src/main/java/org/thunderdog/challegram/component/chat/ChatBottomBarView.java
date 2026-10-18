/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/12/2019
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.SimpleDrawable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class ChatBottomBarView extends BaseView {
  private Drawable drawable;

  // XGram: Material 3 Expressive. The bar keeps the surface color and the action (Join, Mute, Unmute) is a full-width
  // filled tonal button: secondaryContainer, 40dp tall (the small button), 16dp side margins, labelLarge in
  // onSecondaryContainer, full corners that morph to 8dp (shape.corner.small, the small button's pressed shape) while
  // pressed, the onSecondaryContainer state layer at 10%. When the scroll button shows it collapses into one of the
  // chat's M3 FABs: CircleButton.XGRAM_CHAT_FAB_DP (48dp, the bar's height), their corners (CircleButton
  // .xgramChatFabCornerDp: the 14dp rounded square or the avatars' shape, read while drawing, so the outline and the
  // ripple's mask follow a change), the same secondaryContainer, the icon in onSecondaryContainer, elevation level 3
  // (6dp) whose shadow only shows while it floats.
  private final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPress;
  private boolean xgramPressed;
  private static Paint xgramIconPaint;

  /** The collapsed icon in onSecondaryContainer at {@code alpha}; one paint, rebuilt only when the color changes. */
  private static Paint xgramIconPaint (float alpha) {
    int color = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER);
    if (xgramIconPaint == null || (xgramIconPaint.getColor() | 0xff000000) != (color | 0xff000000)) {
      xgramIconPaint = Paints.createPorterDuffPaint(xgramIconPaint, color);
    }
    xgramIconPaint.setAlpha((int) (255f * MathUtils.clamp(alpha)));
    return xgramIconPaint;
  }

  private void xgramInit () {
    Drawable content = new SimpleDrawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        float barAlpha = 1f - collapseFactor;
        if (barAlpha > 0f) {
          // From the top padding: the room above the bar for the collapsed FAB (MessagesController.xgramBottomBarRoom)
          c.drawRect(0, getPaddingTop(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(ColorUtils.alphaColor(barAlpha, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE))));
        }
        int color = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER);
        RectF rectF = buildRectF();
        float radius = xgramRadius(rectF);
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
      }
    };
    Drawable mask = new SimpleDrawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        RectF rectF = buildRectF();
        float radius = xgramRadius(rectF);
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(0xff000000));
      }
    };
    // The onSecondaryContainer state layer follows theme switches (a colour read once kept the old scheme's)
    ViewUtils.setBackground(this, this.drawable = com.fahadali.xgram.appearance.XgramAppMotion.ThemedRipple.ofRole(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER, content, mask));
    xgramPress = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> update());
    setOutlineProvider(new android.view.ViewOutlineProvider() {
      @Override
      public void getOutline (View view, android.graphics.Outline outline) {
        RectF rectF = buildRectF();
        outline.setRoundRect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom, xgramRadius(rectF));
        outline.setAlpha(collapseFactor);
      }
    });
    // Level 3 like the other chat FABs (no Telegram X press lift); the outline alpha keeps it flat while it is a bar
    setStateListAnimator(null);
    setElevation(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.ELEVATION_3));
  }

  private float xgramFabCornerDp = Float.NaN;

  private float xgramRadius (RectF rectF) {
    float full = Math.min(rectF.width(), rectF.height()) / 2f;
    // The button's full corners go to the chat FAB's as it collapses
    float fabSize = Screen.dp(org.thunderdog.challegram.widget.CircleButton.XGRAM_CHAT_FAB_DP);
    float fabCorner = com.fahadali.xgram.appearance.XgramExpressive.cornerPx(org.thunderdog.challegram.widget.CircleButton.xgramChatFabCornerDp(), fabSize, fabSize);
    float resting = full + (Math.min(full, fabCorner) - full) * collapseFactor;
    float pressed = Math.min(resting, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_S));
    float press = xgramPress != null ? xgramPress.getValue() * (1f - collapseFactor) : 0f;
    return Math.max(0f, resting + (pressed - resting) * press);
  }

  @Override
  protected void drawableStateChanged () {
    super.drawableStateChanged();
    if (xgramM3 && xgramPress != null) {
      boolean pressed = isPressed() && isEnabled();
      if (xgramPressed != pressed) {
        xgramPressed = pressed;
        xgramPress.animateTo(pressed ? 1f : 0f);
      }
    }
  }

  public ChatBottomBarView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    Views.setClickable(this);
    if (xgramM3) {
      xgramInit();
      return;
    }
    Drawable drawable = new SimpleDrawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        RectF rectF = buildRectF();
        int radius = calculateRadius();
        int color = ColorUtils.fromToArgb(Theme.fillingColor(), Theme.getColor(ColorId.circleButtonChat), collapseFactor);
        if (radius == 0) {
          c.drawRect(rectF.left, rectF.top, rectF.right, rectF.bottom, Paints.fillingPaint(color));
        } else {
          c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
        }
      }
    };
    Drawable legacyPressedDrawable = new SimpleDrawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        RectF rectF = buildRectF();
        int radius = calculateRadius();
        int color = Theme.getColor(ColorId.fillingPressed);
        if (radius == 0) {
          c.drawRect(rectF.left, rectF.top, rectF.right, rectF.bottom, Paints.fillingPaint(color));
        } else {
          c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
        }
      }
    };
    ViewUtils.setBackground(this, this.drawable = Theme.customSelector(drawable, legacyPressedDrawable));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void getOutline (View view, android.graphics.Outline outline) {
          RectF rectF = buildRectF();
          int radius = calculateRadius();
          if (radius == 0) {
            outline.setRect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
          } else {
            outline.setRoundRect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom, radius);
          }
        }
      });
      Views.setSimpleStateListAnimator(this);
    }
  }

  private int calculateRadius () {
    return (int) (Screen.dp(48f) / 2f * collapseFactor);
  }

  private final RectF fromRect = new RectF(), toRect = new RectF();

  private RectF buildRectF () {
    int fromWidth = getMeasuredWidth();
    int fromHeight = getMeasuredHeight();
    int toSize = Screen.dp(xgramM3 ? org.thunderdog.challegram.widget.CircleButton.XGRAM_CHAT_FAB_DP : 48f); // XGram: the chat's M3 FAB

    RectF rectF = Paints.getRectF();

    float centerX = fromWidth / 2f;
    float centerY = getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) / 2f;

    if (xgramM3) {
      float pillHalf = Screen.dp(40f) / 2f;
      fromRect.set(Screen.dp(16f), centerY - pillHalf, fromWidth - Screen.dp(16f), centerY + pillHalf);
    } else
    fromRect.set(0, 0, fromWidth, fromHeight);
    toRect.set(
      centerX - toSize / 2f,
      centerY - toSize / 2f,
      centerX + toSize / 2f,
      centerY + toSize / 2f
    );

    rectF.set(
      MathUtils.fromTo(fromRect.left, toRect.left, collapseFactor),
      MathUtils.fromTo(fromRect.top, toRect.top, collapseFactor),
      MathUtils.fromTo(fromRect.right, toRect.right, collapseFactor),
      MathUtils.fromTo(fromRect.bottom, toRect.bottom, collapseFactor)
    );
    return rectF;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      RectF rectF = buildRectF();
      float x = e.getX();
      float y = e.getY();
      if (x < rectF.left || x > rectF.right || y < rectF.top || y > rectF.bottom) {
        return false;
      }
    }
    return super.onTouchEvent(e);
  }

  private static class State {
    private String text;
    private int iconRes;

    private float factor;
    private State prevState;

    private Drawable drawable;
    private Text drawingText;

    public State (String text, @DrawableRes int iconRes) {
      this.text = text;
      this.iconRes = iconRes;
      this.drawable = Drawables.get(iconRes);
    }

    private String xgramText; // XGram: labelLarge in sentence case
    private float xgramTextWidth;

    public void layout (int width) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        int available = width - Screen.dp(16f) * 2 - Screen.dp(24f) * 2;
        android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramM3Chat.labelLargePaint(0xff000000);
        this.xgramText = available > 0 ? android.text.TextUtils.ellipsize(this.text, paint, available, android.text.TextUtils.TruncateAt.END).toString() : null;
        this.xgramTextWidth = xgramText != null ? paint.measureText(xgramText) : 0f;
        this.drawingText = null;
        return;
      }
      width -= Screen.dp(8f) * 2;
      this.drawingText = width > 0 ? new Text.Builder(Lang.uppercase(this.text), width - Screen.dp(8f), Paints.robotoStyleProvider(16), TextColorSets.Regular.NEUTRAL).allBold().singleLine().build() : null;
    }

    private static final float SCALE = .8f;

    public void draw (Canvas c, View view, float collapseFactor, float factor, float rectX, float rectY) {
      float fromCx = view.getPaddingLeft() + (view.getMeasuredWidth() - view.getPaddingRight() - view.getPaddingLeft()) / 2f;
      float fromCy = view.getPaddingTop() + (view.getMeasuredHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2f;

      float cx = fromCx + (rectX - fromCx) * collapseFactor;
      float cy = fromCy + (rectY - fromCy) * collapseFactor;

      if (prevState != null) {
        c.save();
        float displayFactor = 1f - this.factor;
        float scale = SCALE + (1f - SCALE) * displayFactor;
        c.scale(scale, scale, cx, cy);
        prevState.draw(c, view, collapseFactor, displayFactor, cx, cy);
        c.restore();
      }
      factor *= this.factor;
      final int saveCount;
      final boolean needScale = factor != 1f;
      if (needScale) {
        saveCount = Views.save(c);
        float scale = SCALE + (1f - SCALE) * factor;
        c.scale(scale, scale, cx, cy);
      } else {
        saveCount = -1;
      }
      if (drawingText != null && collapseFactor < 1f) {
        drawingText.draw(c, (int) (cx - drawingText.getWidth() / 2f), (int) (cy - drawingText.getHeight() / 2f), null, factor * (1f - collapseFactor));
      }
      if (xgramText != null && collapseFactor < 1f) {
        int textColor = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER);
        android.text.TextPaint paint = com.fahadali.xgram.appearance.XgramM3Chat.labelLargePaint(ColorUtils.alphaColor(factor * (1f - collapseFactor), textColor));
        c.drawText(xgramText, cx - xgramTextWidth / 2f, cy - (paint.ascent() + paint.descent()) / 2f, paint);
      }
      if (collapseFactor > 0f && drawable != null) {
        Paint paint = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? xgramIconPaint(factor * collapseFactor) : PorterDuffPaint.get(ColorId.circleButtonChatIcon, factor * collapseFactor);
        Drawables.drawCentered(c, drawable, cx, cy, paint);
      }
      if (needScale) {
        Views.restore(c, saveCount);
      }
    }
  }

  private State state;
  private final BoolAnimator replaceAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    if (this.state != null) {
      this.state.factor = factor;
      if (factor == 1f) {
        this.state.prevState = null;
      }
      invalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setAction (int id, String text, @DrawableRes int iconRes, boolean animated) {
    if (this.state != null && this.state.text.equals(text) && this.state.iconRes == iconRes) {
      return;
    }
    setId(id);

    State newState = new State(text, iconRes);
    newState.layout(getMeasuredWidth());

    if (!animated || this.state == null) {
      replaceAnimator.setValue(false, false);
      this.state = newState;
      this.state.factor = 1f;
      invalidate();
      return;
    }

    State prevState = this.state;
    this.state = null;
    replaceAnimator.setValue(false, false);
    newState.prevState = prevState;
    this.state = newState;
    replaceAnimator.setValue(true, true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (state != null) {
      state.layout(getMeasuredWidth());
    }
  }

  private float collapseFactor;

  public void setCollapseFactor (float collapseFactor) {
    if (this.collapseFactor != collapseFactor) {
      this.collapseFactor = collapseFactor;
      update();
    }
  }

  @Override
  public void setPadding (int left, int top, int right, int bottom) {
    boolean bottomUpdated = bottom != getPaddingBottom();
    super.setPadding(left, top, right, bottom);
    if (bottomUpdated) {
      update();
    }
  }

  public void update () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      invalidateOutline();
    }
    drawable.invalidateSelf();
    invalidate();
  }

  @Override
  public void draw (@NonNull Canvas c) {
    if (xgramM3) {
      // XGram: the chat FAB shape changed (Settings: the option, the avatar shape) since the last frame. The fill and
      // the mask read it while drawing; the outline (the FAB's shadow) and the ripple's cached mask are rebuilt here,
      // before the background draws. Nothing on the usual frame.
      final float corner = org.thunderdog.challegram.widget.CircleButton.xgramChatFabCornerDp();
      if (corner != xgramFabCornerDp) {
        final boolean changed = !Float.isNaN(xgramFabCornerDp);
        xgramFabCornerDp = corner;
        if (changed) {
          update();
        }
      }
    }
    super.draw(c);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (state != null) {
      RectF rectF = buildRectF();
      c.save();
      c.clipRect(rectF.left, rectF.top, rectF.right, rectF.bottom);
      state.draw(c, this, collapseFactor, 1f, rectF.centerX(), rectF.centerY());
      c.restore();
    }
  }
}
