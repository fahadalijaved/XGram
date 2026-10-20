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
package com.fahadali.xgram.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramWavyLinear;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.SparseDrawableView;

import me.vkryl.core.ColorUtils;

/**
 * Activity's status tiles (Expressive design): a tonal card (surfaceContainerHigh, 16dp corners, 16dp from the list's
 * sides) with an optional 24dp icon, bodyMedium text and, while catching up, a determinate wavy progress line. The
 * ghost mode note and the sync's status ("Catching up", "Telegram asked to wait", "Waiting for network"). A tile that
 * goes away collapses on a SPATIAL_DEFAULT spring (its height, card and content to 0) before the list drops it.
 */
@SuppressLint("ViewConstructor")
public final class XgramActivityStatusView extends SparseDrawableView {
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF card = new RectF();
  @Nullable private CharSequence text;
  @DrawableRes private int icon;
  /** 0..1, or -1 for no progress line. */
  private float progress = -1f;
  @Nullable private StaticLayout layout;
  private int layoutWidth = -1;

  // Collapsing: which tile this is (the same one bound again springs, another one snaps), how open it is
  @Nullable private String key;
  /** 1 open, 0 collapsed: the measured height and the drawing follow it. */
  private float shown = 1f;
  @Nullable private XgramExpressive.Spring shownSpring;
  /** Set while it collapses: run once it is shut (the list drops it then). */
  @Nullable private Runnable onCollapsed;

  private static final float SIDE = 16f, VERTICAL = 4f, PADDING = 16f, ICON = 24f, ICON_GAP = 16f, PROGRESS_GAP = 12f, PROGRESS_HEIGHT = 10f;

  public XgramActivityStatusView (Context context) {
    super(context);
    // bodyMedium
    textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_M, false));
    textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
    textPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_BODY_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M));
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  /** {@code progress} 0..1 shows the wavy line under the text, -1 none. */
  public void setStatus (@DrawableRes int icon, @Nullable CharSequence text, float progress) {
    boolean textChanged = this.text == null ? text != null : !this.text.toString().equals(text != null ? text.toString() : null);
    boolean shapeChanged = textChanged || this.icon != icon || (this.progress < 0f) != (progress < 0f);
    this.icon = icon;
    this.text = text;
    this.progress = progress;
    if (textChanged) {
      layoutWidth = -1;
    }
    setContentDescription(text);
    if (shapeChanged) {
      requestLayout();
    }
    invalidate();
  }

  /**
   * Bound as tile {@code key}. {@code collapsing}: it springs shut (SPATIAL_DEFAULT), then {@code onCollapsed} runs.
   * The same tile on screen bound open again while it collapses (the sync started again) springs back open. Bound
   * off screen (a recycled or cached view), it takes its state at once: nothing to watch there.
   */
  public void setCollapsing (@NonNull String key, boolean collapsing, @Nullable Runnable onCollapsed) {
    final boolean sameTile = key.equals(this.key) && isAttachedToWindow();
    this.key = key;
    if (!collapsing) {
      this.onCollapsed = null;
      if (sameTile && shownSpring != null && (shownSpring.isRunning() || shown < 1f)) {
        shownSpring.animateTo(1f);
      } else {
        snapShown(1f);
      }
      return;
    }
    if (this.onCollapsed != null && sameTile) {
      this.onCollapsed = onCollapsed; // already collapsing: the spring carries on
      return;
    }
    this.onCollapsed = onCollapsed;
    if (!sameTile || shown <= 0f) {
      snapShown(0f);
      UI.post(this::finishCollapse); // not during the list's bind
      return;
    }
    final XgramExpressive.Spring spring = shownSpring();
    spring.animateTo(0f);
    if (!spring.isRunning()) {
      finishCollapse(); // Reduce motion snapped it (its end listener ran this already: a no-op then)
    }
  }

  @NonNull
  private XgramExpressive.Spring shownSpring () {
    if (shownSpring == null) {
      shownSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, shown, (s, value) -> applyShown(Math.max(0f, Math.min(1f, value))));
      shownSpring.setEndListener(this::finishCollapse);
    }
    return shownSpring;
  }

  private void snapShown (float value) {
    if (shownSpring != null) {
      shownSpring.setValue(value);
    }
    applyShown(value);
  }

  private void applyShown (float value) {
    if (shown != value) {
      shown = value;
      requestLayout();
      invalidate();
    }
  }

  /** Shut: the list may drop it. Nothing when it was bound open again meanwhile. */
  private void finishCollapse () {
    final Runnable callback = onCollapsed;
    final float target = shownSpring != null ? shownSpring.getTarget() : shown;
    if (callback != null && target <= 0f) {
      onCollapsed = null;
      callback.run();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (onCollapsed != null) {
      // Off screen while it collapsed (recycled, or another screen went over the list): it is shut now
      snapShown(0f);
      finishCollapse();
    } else if (shownSpring != null && shownSpring.isRunning()) {
      snapShown(shownSpring.getTarget());
    }
  }

  private int textStart () {
    return Screen.dp(SIDE + PADDING) + (icon != 0 ? Screen.dp(ICON + ICON_GAP) : 0);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int textWidth = Math.max(0, width - textStart() - Screen.dp(SIDE + PADDING));
    if (layoutWidth != textWidth) {
      layoutWidth = textWidth;
      layout = text != null && textWidth > 0 ? new StaticLayout(text, textPaint, textWidth, Lang.rtl() ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL, 1f, Screen.dp(2f), false) : null;
    }
    int contentHeight = Math.max(layout != null ? layout.getHeight() : 0, icon != 0 ? Screen.dp(ICON) : 0);
    if (progress >= 0f) {
      contentHeight += Screen.dp(PROGRESS_GAP + PROGRESS_HEIGHT);
    }
    final int height = contentHeight + Screen.dp(PADDING) * 2 + Screen.dp(VERTICAL) * 2;
    setMeasuredDimension(width, shown >= 1f ? height : Math.round(height * shown));
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight();
    final boolean rtl = Lang.rtl();
    card.set(Screen.dp(SIDE), Screen.dp(VERTICAL), width - Screen.dp(SIDE), height - Screen.dp(VERTICAL));
    if (shown <= 0f || card.height() <= 0f) {
      return;
    }
    // Collapsing: the card shrinks from the bottom and fades, its content (clipped to it) fades faster
    final boolean collapsing = shown < 1f;
    final float contentAlpha = shown * shown;
    final int surface = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH);
    cardPaint.setColor(collapsing ? ColorUtils.alphaColor(shown, surface) : surface);
    final float radius = Math.min(Screen.dp(XgramExpressive.CORNER_L), card.height() / 2f);
    c.drawRoundRect(card, radius, radius, cardPaint);
    if (collapsing) {
      c.save();
      c.clipRect(card);
    }
    final int onSurfaceVariantColor = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    final int onSurfaceVariant = collapsing ? ColorUtils.alphaColor(contentAlpha, onSurfaceVariantColor) : onSurfaceVariantColor;
    final int top = Screen.dp(VERTICAL + PADDING);
    if (icon != 0) {
      Drawable drawable = getSparseDrawable(icon, ColorId.NONE);
      if (drawable != null) {
        final int iconX = rtl ? width - Screen.dp(SIDE + PADDING) - drawable.getMinimumWidth() : Screen.dp(SIDE + PADDING);
        Drawables.draw(c, drawable, iconX, top, Paints.getPorterDuffPaint(onSurfaceVariant));
      }
    }
    int textBottom = top;
    if (layout != null) {
      textPaint.setColor(onSurfaceVariant);
      final int textX = rtl ? Screen.dp(SIDE + PADDING) : textStart();
      final int textTop = icon != 0 && layout.getHeight() < Screen.dp(ICON) ? top + (Screen.dp(ICON) - layout.getHeight()) / 2 : top;
      c.save();
      c.translate(textX, textTop);
      layout.draw(c);
      c.restore();
      textBottom = textTop + layout.getHeight();
    }
    if (progress >= 0f) {
      final float cy = Math.max(textBottom, top + (icon != 0 ? Screen.dp(ICON) : 0)) + Screen.dp(PROGRESS_GAP) + Screen.dp(PROGRESS_HEIGHT) / 2f;
      final float left = Screen.dp(SIDE + PADDING), right = width - Screen.dp(SIDE + PADDING);
      final int indicator = XgramExpressive.color(XgramExpressive.PRIMARY), track = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      XgramWavyLinear.draw(c, left, right, cy, Math.max(0f, Math.min(1f, progress)),
        collapsing ? ColorUtils.alphaColor(contentAlpha, indicator) : indicator, collapsing ? ColorUtils.alphaColor(contentAlpha, track) : track,
        Screen.dp(4f), Screen.dp(3f), Screen.dp(40f), Screen.dp(4f), true);
      if (XgramWavyLinear.isWaveMoving(progress, Screen.dp(3f))) {
        postInvalidateOnAnimation();
      }
    }
    if (collapsing) {
      c.restore();
    }
  }
}
