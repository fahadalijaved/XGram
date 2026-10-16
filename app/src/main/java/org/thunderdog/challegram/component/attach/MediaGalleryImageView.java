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
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class MediaGalleryImageView extends BaseView implements Destroyable, FactorAnimator.Target, ClickHelper.Delegate, ImageFile.ChangeListener {
  private static final float SCALE = .24f;

  public interface ClickListener {
    void onClick (View view, boolean isSelect);
  }

  // Children
  private final ImageReceiver receiver;

  // Data
  private ImageFile image;
  private int width, height;

  private final ClickHelper helper;
  private ClickListener listener;

  public MediaGalleryImageView (Context context) {
    super(context, null);

    helper = new ClickHelper(this);

    receiver = new ImageReceiver(this, 0);
    receiver.prepareToBeCropped();
    receiver.setAnimationDisabled(true);
    // XGram: Material 3 Expressive 4dp tiles are clipped in xgramDraw (the receiver's own radius rounds the cropped
    // bitmap's rect, so a photo that is not square kept square corners)

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public void setClickListener (ClickListener listener) {
    this.listener = listener;
  }

  private boolean isInvisible;
  private boolean isAlwaysInvisible;

  public void setAlwaysInvisible (boolean alwaysInvisible) {
    isAlwaysInvisible = alwaysInvisible;
    invalidate();
  }

  public void setInvisible (boolean isInvisible, boolean needInvalidate) {
    if (this.isInvisible != isInvisible) {
      this.isInvisible = isInvisible;
      invalidate();
    }
  }

  public void setAnimationsDisabled (boolean disabled) {
    receiver.setAnimationDisabled(disabled);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return listener != null;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (listener != null) {
      int size = Screen.dp(24f) * 2;
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && Lang.rtl()) {
        listener.onClick(view, y <= size && x <= size); // XGram: the M3E check sits at the tile's top end
        return;
      }
      listener.onClick(view, y <= size && x >= getMeasuredWidth() - size);
    }
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return true;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    return onLongClickListener != null && onLongClickListener.onLongClick(view);
  }

  private OnLongClickListener onLongClickListener;

  @Override
  public void setOnLongClickListener (@Nullable OnLongClickListener l) {
    this.onLongClickListener = l;
  }

  // Attach/Detach

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return helper.onTouchEvent(this, event);
  }

  // Image

  private String duration;
  private int durationWidth;

  private int selectionIndex = -1;
  private String selectionIndexStr;

  private boolean showFavorite;

  @Override
  public void onImageChanged (ImageFile file) {
    if (this.image == file) {
      this.receiver.clear();
      this.receiver.requestFile(image);
    }
  }

  public void setImage (ImageFile image, int selectionIndex, boolean isCheckable) {
    if (this.image == image) {
      return;
    }
    if (this.image != null) {
      this.image.removeChangeListener(this);
    }
    this.image = image;
    if (image != null) {
      image.addChangeListener(this);
    }
    receiver.requestFile(image);
    if (image instanceof ImageGalleryFile && ((ImageGalleryFile) image).isVideo()) {
      ImageGalleryFile videoFile = (ImageGalleryFile) image;
      int trimmedDuration = videoFile.getVideoDuration(false);
      int totalDuration = videoFile.getVideoDuration(false);
      if (trimmedDuration < totalDuration) {
        duration = Lang.getString(R.string.format_trimmedDuration, Strings.buildDuration(trimmedDuration), Strings.buildDuration(totalDuration));
      } else {
        duration = Strings.buildDuration(totalDuration);
      }
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        durationWidth = com.fahadali.xgram.appearance.XgramM3MediaButton.measureChipText(duration); // XGram: labelMedium
      } else {
        durationWidth = (int) U.measureText(duration, Paints.whiteMediumPaint(12f, false, true));
      }
    } else {
      duration = null;
    }
    setChecked(selectionIndex, false);
    setInvisible(!isCheckable, false);
    setSelectionIndex(selectionIndex);
    setShowFavorite(image instanceof ImageGalleryFile && ((ImageGalleryFile) image).isFavorite());
    invalidate();
  }

  public void setSelectionIndex (int index) {
    if (this.selectionIndex != index && index >= 0) {
      this.selectionIndex = index;
      this.selectionIndexStr = String.valueOf(index + 1);
      invalidate();
    }
  }

  public void setShowFavorite (boolean showFavorite) {
    if (this.showFavorite != showFavorite) {
      this.showFavorite = showFavorite;
      invalidate();
    }
  }

  public ImageFile getImage () {
    return image;
  }

  // CheckView

  public void setChecked (int selectionIndex, boolean animated) {
    if (selectionIndex >= 0) {
      setSelectionIndex(selectionIndex);
      setChecked(true, animated);
    } else {
      setChecked(false, animated);
    }
  }

  private boolean isChecked;

  private void setChecked (boolean isChecked, boolean animated) {
    if (this.isChecked != isChecked) {
      this.isChecked = isChecked;
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        xgramSetSelected(isChecked, animated); // XGram: the M3E selection springs (see xgramDraw)
        return;
      }
      if (animated) {
        animateFactor(isChecked ? 1f : 0f);
      } else {
        forceFactor(isChecked ? 1f : 0f);
      }
    }
  }

  private FactorAnimator animator;

  private void animateFactor (float toFactor) {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
    }
    animator.animateTo(toFactor);
  }

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private float factor;
  private SimplestCheckBox checkBox;

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (factor > 0f && checkBox == null) {
        checkBox = SimplestCheckBox.newInstance(factor, selectionIndexStr);
      }
      invalidate();
    }
  }

  // Metrics

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    if (this.width != width || this.height != height) {
      this.width = width;
      this.height = height;
      setReceiverBounds();
    }
  }

  private void setReceiverBounds () {
    receiver.setBounds(0, 0, width, height);
  }

  public int getReceiverOffset () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Math.round(xgramInset(me.vkryl.core.MathUtils.clamp(xgramSelected))); // XGram: the photo inside its frame
    }
    return factor != 0f ? (receiver.getWidth() - (int) ((float) receiver.getWidth() * (1f - factor * SCALE))) / 2 : 0;
  }

  // XGram: Material 3 Expressive photo picker selection, as Google Photos and the system photo picker. Not selected: a
  // 24dp circle with a 2dp white outline (onSurfaceVariant on an empty tile) over a soft scrim at the tile's top end.
  // Selected: the circle fills with primary and shows the onPrimary selection number (or a check), and the photo
  // shrinks into its tile with 12dp corners on a secondaryContainer frame. One fast spatial spring drives it all (it
  // overshoots a little); nothing is allocated while it runs. Telegram X's white ring and SimplestCheckBox stay off.

  private static final float XGRAM_CHECK_RADIUS = 12f, XGRAM_CHECK_MARGIN = 8f, XGRAM_CHECK_STROKE = 2f,
    XGRAM_SCRIM_RADIUS = 40f, XGRAM_SELECTED_INSET = 10f, XGRAM_SELECTED_INSET_MAX = .08f;

  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramSelection;
  /** The spring's value: 0 not selected, 1 selected. */
  private float xgramSelected;
  private @Nullable android.text.TextPaint xgramCheckText;
  private @Nullable android.graphics.Paint.FontMetrics xgramCheckMetrics;
  private @Nullable android.graphics.Path xgramClip;
  private @Nullable RectF xgramClipRect;
  private float xgramClipInset = -1f, xgramClipRadius = -1f;
  private int xgramClipWidth = -1, xgramClipHeight = -1;

  private static @Nullable android.graphics.Paint xgramScrimPaint;
  private static float xgramScrimPaintRadius;

  private void xgramSetSelected (boolean selected, boolean animated) {
    final float target = selected ? 1f : 0f;
    if (animated) {
      if (xgramSelection == null) {
        xgramSelection = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, xgramSelected, (s, value) -> {
          xgramSelected = value;
          invalidate();
        });
      }
      xgramSelection.animateTo(target);
    } else {
      if (xgramSelection != null) {
        xgramSelection.setValue(target);
      }
      if (xgramSelected != target) {
        xgramSelected = target;
        invalidate();
      }
    }
  }

  /** How far the photo moves in from each side of its tile at {@code k} (0..1 and a little past it). */
  private float xgramInset (float k) {
    return Math.min(Screen.dp(XGRAM_SELECTED_INSET), Math.min(width, height) * XGRAM_SELECTED_INSET_MAX) * k;
  }

  /** The photo's rounded clip, rebuilt only when its size, inset or corner changed. */
  private android.graphics.Path xgramClipPath (float inset, float radius) {
    if (xgramClip == null) {
      xgramClip = new android.graphics.Path();
      xgramClipRect = new RectF();
    }
    if (inset != xgramClipInset || radius != xgramClipRadius || width != xgramClipWidth || height != xgramClipHeight) {
      xgramClipInset = inset;
      xgramClipRadius = radius;
      xgramClipWidth = width;
      xgramClipHeight = height;
      xgramClipRect.set(inset, inset, width - inset, height - inset);
      xgramClip.rewind();
      xgramClip.addRoundRect(xgramClipRect, radius, radius, android.graphics.Path.Direction.CW);
    }
    return xgramClip;
  }

  /** One radial scrim for every tile (32% black under the check, fading out), made again only if the density changes. */
  private static android.graphics.Paint xgramScrimPaint (float radius) {
    if (xgramScrimPaint == null || xgramScrimPaintRadius != radius) {
      if (xgramScrimPaint == null) {
        xgramScrimPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.DITHER_FLAG);
      }
      xgramScrimPaint.setShader(new android.graphics.RadialGradient(0f, 0f, radius,
        new int[] {0x52000000, 0x29000000, 0x00000000}, new float[] {0f, .5f, 1f}, android.graphics.Shader.TileMode.CLAMP));
      xgramScrimPaintRadius = radius;
    }
    return xgramScrimPaint;
  }

  private float xgramCheckCenterX () {
    final float offset = Screen.dp(XGRAM_CHECK_MARGIN) + Screen.dp(XGRAM_CHECK_RADIUS);
    return Lang.rtl() ? offset : width - offset; // the top end
  }

  private float xgramCheckCenterY () {
    return Screen.dp(XGRAM_CHECK_MARGIN) + Screen.dp(XGRAM_CHECK_RADIUS);
  }

  private void xgramDraw (Canvas c) {
    final float s = xgramSelected;
    final float k = Math.max(0f, s), alpha = me.vkryl.core.MathUtils.clamp(s);
    final boolean placeholder = receiver.needPlaceholder();
    final boolean hasCheck = !isInvisible && !isAlwaysInvisible;
    final float rest = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_XS);

    if (k > 0f || placeholder) {
      RectF tile = Paints.getRectF();
      tile.set(0, 0, width, height);
      c.drawRoundRect(tile, rest, rest, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(k > 0f ? com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST)));
    }

    final float inset = xgramInset(k);
    final float radius = rest + (Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M) - rest) * k;
    final int clipSave = c.save();
    c.clipPath(xgramClipPath(inset, radius));
    final boolean scaled = inset > 0f && width > 0;
    final int scaleSave = scaled ? c.save() : -1;
    if (scaled) {
      final float scale = (width - inset * 2f) / width;
      c.scale(scale, scale, width / 2f, height / 2f);
    }
    receiver.draw(c);
    final boolean rtl = Lang.rtl();
    if (showFavorite) {
      Drawable drawable = getSparseDrawable(R.drawable.baseline_favorite_12, ColorId.white);
      final int favoriteX = rtl ? receiver.getRight() - drawable.getMinimumWidth() - Screen.dp(6f) : receiver.getLeft() + Screen.dp(6f); // the bottom start
      Drawables.draw(c, drawable, favoriteX, receiver.getBottom() - drawable.getMinimumHeight() - Screen.dp(6f), PorterDuffPaint.get(ColorId.white, .95f));
    }
    if (duration != null && !duration.isEmpty()) {
      xgramDrawDuration(c, rtl);
    }
    if (scaled) {
      c.restoreToCount(scaleSave);
    }
    if (hasCheck && !placeholder && alpha < 1f) {
      // the scrim stays on the photo (inside its clip), so its corner is not darkened outside it
      final float scrimRadius = Screen.dp(XGRAM_SCRIM_RADIUS);
      final android.graphics.Paint scrim = xgramScrimPaint(scrimRadius);
      scrim.setAlpha((int) (255f * (1f - alpha)));
      final int scrimSave = c.save();
      c.translate(xgramCheckCenterX(), xgramCheckCenterY());
      c.drawCircle(0f, 0f, scrimRadius, scrim);
      c.restoreToCount(scrimSave);
    }
    c.restoreToCount(clipSave);

    if (hasCheck) {
      xgramDrawCheck(c, s, placeholder);
    }
  }

  private void xgramDrawCheck (Canvas c, float s, boolean placeholder) {
    final float k = Math.max(0f, s), alpha = me.vkryl.core.MathUtils.clamp(s);
    final float cx = xgramCheckCenterX(), cy = xgramCheckCenterY();
    final float radius = Screen.dp(XGRAM_CHECK_RADIUS), stroke = Screen.dp(XGRAM_CHECK_STROKE);
    if (k > 0f) {
      // the fill grows out of the circle's centre, a little past the outline as the spring overshoots
      final int primary = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
      c.drawCircle(cx, cy, Math.min(radius + Screen.dp(1f), radius * k), Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(primary, Math.min(1f, k * 2f))));
    }
    final int outline = placeholder ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT) : 0xffffffff;
    c.drawCircle(cx, cy, radius - stroke / 2f, Paints.getProgressPaint(outline, stroke));
    if (alpha <= 0f) {
      return;
    }
    final int onPrimary = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY);
    if (selectionIndexStr != null) {
      if (xgramCheckText == null) {
        xgramCheckText = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.SUBPIXEL_TEXT_FLAG);
        xgramCheckText.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, true));
        xgramCheckText.setTextAlign(android.graphics.Paint.Align.CENTER);
        xgramCheckMetrics = new android.graphics.Paint.FontMetrics();
      }
      // labelMedium, popping in with the fill
      xgramCheckText.setTextSize(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M)) * (.5f + .5f * Math.min(k, 1.15f)));
      xgramCheckText.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(onPrimary, alpha));
      xgramCheckText.getFontMetrics(xgramCheckMetrics);
      c.drawText(selectionIndexStr, cx, cy - (xgramCheckMetrics.ascent + xgramCheckMetrics.descent) / 2f, xgramCheckText);
    } else {
      com.fahadali.xgram.appearance.XgramM3MediaButton.drawCheck(c, cx, cy, Screen.dp(20f), onPrimary, alpha);
    }
  }

  // XGram: the video duration as the chat media's chip: a full-corner pill on the 50% chip scrim, labelMedium white,
  // 6dp side padding, 4dp from the tile's top start corner (XgramM3MediaButton.CHIP_SCRIM, chipLabelPaint)
  private final android.graphics.Paint.FontMetrics xgramChipMetrics = new android.graphics.Paint.FontMetrics();

  private void xgramDrawDuration (Canvas c, boolean rtl) {
    final android.text.TextPaint textPaint = com.fahadali.xgram.appearance.XgramM3MediaButton.chipLabelPaint();
    textPaint.getFontMetrics(xgramChipMetrics);
    final int paddingH = Screen.dp(6f), margin = Screen.dp(4f), height = Screen.dp(20f);
    final int chipWidth = paddingH + durationWidth + paddingH;
    final int left = rtl ? receiver.getRight() - margin - chipWidth : receiver.getLeft() + margin, top = receiver.getTop() + margin;
    RectF rectF = Paints.getRectF();
    rectF.set(left, top, left + paddingH + durationWidth + paddingH, top + height);
    final float radius = rectF.height() / 2f;
    c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramM3MediaButton.CHIP_SCRIM));
    final float baseline = rectF.centerY() - (xgramChipMetrics.ascent + xgramChipMetrics.descent) / 2f;
    c.drawText(duration, left + paddingH, baseline, textPaint);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDraw(c); // XGram: Material 3 Expressive tile and selection
      return;
    }
    if (factor != 0f || receiver.needPlaceholder()) {
      c.drawRect(0, 0, width, height, Paints.fillingPaint(Theme.chatSelectionColor()));
    }

    final boolean saved = factor != 0f;
    if (saved) {
      final float scale = 1f - (factor * SCALE);
      c.save();
      c.scale(scale, scale, receiver.centerX(), receiver.centerY());
    }

    receiver.draw(c);
    if (showFavorite) {
      Drawable drawable = getSparseDrawable(R.drawable.baseline_favorite_12, ColorId.white);
      Drawables.draw(c, drawable, receiver.getLeft() + Screen.dp(6f), receiver.getBottom() - drawable.getMinimumHeight() - Screen.dp(6f), PorterDuffPaint.get(ColorId.white, .95f));
    }
    if (duration != null && !duration.isEmpty()) {
      int textLeft = receiver.getLeft() + Screen.dp(7f);
      int textTop = receiver.getTop() + Screen.dp(5f);
      RectF rectF = Paints.getRectF();
      rectF.set(textLeft - Screen.dp(3f), textTop - Screen.dp(2f), textLeft + durationWidth + Screen.dp(3f), textTop + Screen.dp(15f));
      c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(0x4c000000));
      c.drawText(duration, textLeft, textTop + Screen.dp(11f), Paints.whiteMediumPaint(12f, false, false));
    }

    if (saved) {
      c.restore();
    }

    if (!isInvisible && !isAlwaysInvisible) {
      final int centerX = receiver.centerX() + (int) ((float) receiver.getWidth() * (1f - SCALE)) / 2;
      final int centerY = receiver.centerY() - (int) ((float) receiver.getHeight() * (1f - SCALE)) / 2;
      final int radius = Screen.dp(9f + 2f * factor);

      final int toColor = ColorUtils.compositeColor(Theme.fillingColor(), Theme.chatSelectionColor());
      c.drawCircle(centerX, centerY, radius, Paints.getOuterCheckPaint(ColorUtils.alphaColor(1f, ColorUtils.fromToArgb(0xffffffff, toColor, factor))));

      if (factor != 0f && checkBox != null) {
        SimplestCheckBox.draw(c, centerX, centerY, factor, selectionIndexStr, checkBox);
      }
    }
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
    if (checkBox != null) {
      checkBox.destroy();
      checkBox = null;
    }
    factor = 0f;
    if (xgramSelection != null) {
      xgramSelection.setValue(0f); // XGram
    }
    xgramSelected = 0f;
  }
}
