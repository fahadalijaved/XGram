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
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive for the chat screen (Google Messages, 2025): the message list in a container with rounded
 * top corners under a surfaceContainer app bar, the bars under the app bar as a segmented card group, the reply
 * preview joined to the composer field, bubble corners and the small text styles the chat draws itself.
 * Everything here is only used while {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramM3Chat {
  private XgramM3Chat () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Container with rounded top corners

  public static final float CONTAINER_CORNER = XgramExpressive.CORNER_XL;

  public interface ColorSource {
    @ColorInt int color ();
  }

  private static final Paint notchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final Path notchPath = new Path();
  private static final RectF notchOval = new RectF();
  private static int notchWidth = -1, notchRadius = -1;

  /**
   * Paints the two top corner notches of a content container (radius 28dp) in {@code color}, the color of the app
   * bar above it: rect(0, top, w, top + r) minus roundRect(0, top, w, top + 2r, r).
   */
  public static void drawTopCorners (Canvas c, int width, float top, @ColorInt int color) {
    int r = Screen.dp(CONTAINER_CORNER);
    if (width < r * 2 || r <= 0 || ((color >>> 24) & 0xff) == 0) {
      return;
    }
    if (notchWidth != width || notchRadius != r) {
      notchWidth = width;
      notchRadius = r;
      notchPath.rewind();
      // Start corner
      notchPath.moveTo(0, 0);
      notchPath.lineTo(r, 0);
      notchOval.set(0, 0, r * 2, r * 2);
      notchPath.arcTo(notchOval, 270f, -90f, false);
      notchPath.close();
      // End corner
      notchPath.moveTo(width, 0);
      notchPath.lineTo(width, r);
      notchOval.set(width - r * 2, 0, width, r * 2);
      notchPath.arcTo(notchOval, 0f, -90f, false);
      notchPath.close();
    }
    notchPaint.setColor(color);
    if (top != 0f) {
      c.save();
      c.translate(0, top);
      c.drawPath(notchPath, notchPaint);
      c.restore();
    } else {
      c.drawPath(notchPath, notchPaint);
    }
  }

  /** A 28dp tall, touch-transparent strip at the top of a content view that rounds its top corners. */
  public static final class CornersView extends View {
    private final ColorSource source;

    public CornersView (Context context, ColorSource source) {
      super(context);
      this.source = source;
      setClickable(false);
      setFocusable(false);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw (Canvas c) {
      drawTopCorners(c, getWidth(), 0, source.color());
    }
  }

  public static int cornersHeight () {
    return Screen.dp(CONTAINER_CORNER);
  }

  // Segmented cards (the bars under the app bar)

  private static void setRadii (float[] radii, float topStart, float topEnd, float bottomEnd, float bottomStart) {
    boolean rtl = Lang.rtl();
    float tl = rtl ? topEnd : topStart, tr = rtl ? topStart : topEnd, br = rtl ? bottomStart : bottomEnd, bl = rtl ? bottomEnd : bottomStart;
    radii[0] = radii[1] = tl;
    radii[2] = radii[3] = tr;
    radii[4] = radii[5] = br;
    radii[6] = radii[7] = bl;
  }

  @SuppressWarnings("deprecation")
  private static void setOutlinePath (Outline outline, Path path, RectF rect, float[] radii) {
    float r = radii[0];
    if (radii[2] == r && radii[4] == r && radii[6] == r) {
      outline.setRoundRect(Math.round(rect.left), Math.round(rect.top), Math.round(rect.right), Math.round(rect.bottom), r);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      outline.setPath(path);
    } else {
      outline.setConvexPath(path);
    }
  }

  /**
   * A tile of the card group under the app bar: surfaceContainerHigh, outer corners 20dp, inner 4dp, inset 1dp at the
   * top and the bottom so neighbours keep a 2dp gap. The height of the view does not change.
   */
  public static final class CardDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private int segment = XgramExpressive.SEGMENT_SINGLE;
    private @Nullable RippleDrawable ripple;
    private @ColorInt int rippleColor;

    public void setSegment (int segment) {
      if (this.segment != segment) {
        this.segment = segment;
        invalidateSelf();
      }
    }

    public int getSegment () {
      return segment;
    }

    private void build () {
      Rect b = getBounds();
      int inset = Screen.dp(XgramExpressive.LIST_GAP / 2f);
      rect.set(b.left, b.top + inset, b.right, b.bottom - inset);
      float outer = Screen.dpf(XgramExpressive.LIST_OUTER_CORNER), inner = Screen.dpf(XgramExpressive.LIST_INNER_CORNER);
      boolean roundTop = segment == XgramExpressive.SEGMENT_SINGLE || segment == XgramExpressive.SEGMENT_FIRST;
      boolean roundBottom = segment == XgramExpressive.SEGMENT_SINGLE || segment == XgramExpressive.SEGMENT_LAST;
      float max = Math.max(0f, Math.min(rect.width(), rect.height()) / 2f);
      float top = Math.min(max, roundTop ? outer : inner), bottom = Math.min(max, roundBottom ? outer : inner);
      setRadii(radii, top, top, bottom, bottom);
      path.rewind();
      if (rect.width() > 0f && rect.height() > 0f) {
        path.addRoundRect(rect, radii, Path.Direction.CW);
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      if (ripple != null) {
        int want = XgramExpressiveC.stateLayerColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
        if (want != rippleColor) {
          rippleColor = want;
          ripple.setColor(ColorStateList.valueOf(want));
        }
      }
      build();
      paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH));
      c.drawPath(path, paint);
    }

    @Override
    public void getOutline (@NonNull Outline outline) {
      build();
      if (rect.isEmpty()) {
        outline.setEmpty();
        return;
      }
      setOutlinePath(outline, path, rect, radii);
      outline.setAlpha(0f);
    }

    @Override
    protected void onBoundsChange (@NonNull Rect bounds) {
      super.onBoundsChange(bounds);
      invalidateSelf();
    }

    @Override
    public void setAlpha (int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /**
   * Makes {@code v} a card of the group: its background becomes the card with an onSurface state layer clipped to
   * the card, and its content is clipped to the card's shape.
   */
  public static CardDrawable applyCard (View v) {
    CardDrawable card = new CardDrawable();
    int rippleColor = XgramExpressiveC.stateLayerColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
    RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(rippleColor), card, null);
    card.ripple = ripple;
    card.rippleColor = rippleColor;
    v.setBackground(ripple);
    v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    v.setClipToOutline(true);
    return card;
  }

  // Composer

  public interface TrailingSource {
    boolean hasTrailingButton ();
  }

  /** Start margin of the composer field. */
  public static int fieldStart () {
    return Screen.dp(8f);
  }

  /** End margin of the composer field: the 40dp send or record button sits in the last 55dp. */
  public static int fieldEnd (boolean hasTrailingButton) {
    return Screen.dp(hasTrailingButton ? 55f : 8f);
  }

  /**
   * The reply, edit or link preview block above the composer field (ReplyBarView background): on the composer's
   * surface, a surfaceContainerHighest block with the field's horizontal bounds, 20dp top corners and 4dp bottom
   * corners. Its bottom edge is the bottom of the bar; the field starts 2dp below it, so the two read as one group.
   */
  public static final class ComposerBlockDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final TrailingSource trailing;

    public ComposerBlockDrawable (TrailingSource trailing) {
      this.trailing = trailing;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect b = getBounds();
      // The composer panel is one surface: the preview row shares it with the field row below.
      paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE));
      c.drawRect(b, paint);
      boolean rtl = Lang.rtl();
      int start = fieldStart(), end = fieldEnd(trailing.hasTrailingButton());
      rect.set(b.left + (rtl ? end : start), b.top + Screen.dp(4f), b.right - (rtl ? start : end), b.bottom);
      if (rect.width() <= 0f || rect.height() <= 0f) {
        return;
      }
      float max = Math.min(rect.width(), rect.height()) / 2f;
      float top = Math.min(max, Screen.dpf(XgramExpressive.CORNER_L_INC));
      float bottom = Math.min(max, Screen.dpf(XgramExpressive.CORNER_XS));
      setRadii(radii, top, top, bottom, bottom);
      path.rewind();
      path.addRoundRect(rect, radii, Path.Direction.CW);
      paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST));
      c.drawPath(path, paint);
    }

    @Override
    public void setAlpha (int alpha) {
      paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {
      paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /** Space the reply bar keeps free at its end, so its buttons and text stay inside the block. */
  public static int composerBlockEndInset () {
    return Screen.dp(55f);
  }

  // Text buttons

  /** A text button background: no container, a full-corner state layer in {@code contentColor} at .10 that morphs to 8dp while pressed. */
  public static void setTextButtonBackground (View v, @ColorInt int contentColor) {
    XgramExpressive.MorphDrawable shape = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    shape.setHost(v);
    // The shape is also the (unfilled) content layer, so it gets the bounds and the pressed state the mask follows.
    v.setBackground(new RippleDrawable(ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(contentColor)), shape, shape.newMask()));
  }

  // Bubbles (Google Messages: 20dp corners, 4dp on the sender side of grouped messages)

  public static final float BUBBLE_RADIUS = XgramExpressive.CORNER_L_INC, BUBBLE_MERGE_RADIUS = XgramExpressive.CORNER_XS;

  /** Default bubble corner in dp; the Chat look value when the user picked one. */
  public static float bubbleRadius () {
    return XgramSettings.instance().bubbleRadius() >= 0 ? Theme.getBubbleDefaultRadius() : BUBBLE_RADIUS;
  }

  /** Grouped bubble corner in dp; the Chat look value when the user picked one. */
  public static float bubbleMergeRadius () {
    return XgramSettings.instance().bubbleMergeRadius() >= 0 ? Theme.getBubbleMergeRadius() : BUBBLE_MERGE_RADIUS;
  }

  // Text

  private static TextPaint dateTextPaint, dateMeasurePaint, labelLargePaint;

  /** The date style for measuring; safe off the main thread (messages are laid out in the background). */
  public static TextPaint dateMeasurePaint () {
    synchronized (XgramM3Chat.class) {
      if (dateMeasurePaint == null) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
        dateMeasurePaint = paint;
      }
      dateMeasurePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
      return dateMeasurePaint;
    }
  }

  /** Date separators: labelMedium (12sp, 500). */
  public static TextPaint datePaint (@ColorInt int color) {
    if (dateTextPaint == null) {
      dateTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      dateTextPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
    }
    dateTextPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
    dateTextPaint.setColor(color);
    return dateTextPaint;
  }

  /** Buttons: labelLarge (14sp, 500). */
  public static TextPaint labelLargePaint (@ColorInt int color) {
    if (labelLargePaint == null) {
      labelLargePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      labelLargePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    }
    labelLargePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L)));
    labelLargePaint.setColor(color);
    return labelLargePaint;
  }

  // Floating toolbar (experimental M3 Expressive component), message selection

  /**
   * The vibrant floating toolbar: a primaryContainer pill, 64dp tall with 8dp padding, holding 48dp icon buttons in
   * onPrimaryContainer with a .10 state layer. It enters with a scale (.8 to 1) and a fade on the spatial default
   * spring. It floats over the messages, so it keeps a small elevation like a FAB.
   */
  public static final class FloatingToolbar extends android.widget.LinearLayout {
    private final XgramExpressive.Spring visibility;
    private boolean shown;
    private @ColorInt int contentColor;

    public FloatingToolbar (Context context) {
      super(context);
      setOrientation(android.widget.LinearLayout.HORIZONTAL);
      setGravity(android.view.Gravity.CENTER_VERTICAL);
      int padding = Screen.dp(8f);
      setPadding(padding, padding, padding, padding);
      XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, Float.NaN);
      background.setColorRole(XgramExpressive.PRIMARY_CONTAINER);
      background.setHost(this);
      setBackground(background);
      setOutlineProvider(ViewOutlineProvider.BACKGROUND);
      setElevation(Screen.dp(XgramExpressive.ELEVATION_3));
      setVisibility(View.GONE);
      setAlpha(0f);
      contentColor = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
      visibility = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyVisibility(value));
      visibility.setEndListener(() -> {
        if (!shown) {
          setVisibility(View.GONE);
        }
      });
    }

    public android.widget.ImageView addButton (int id, int iconRes, CharSequence description, View.OnClickListener onClick) {
      android.widget.ImageView button = new android.widget.ImageView(getContext());
      button.setId(id);
      button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      button.setImageResource(XgramIconPack.map(iconRes));
      button.setColorFilter(contentColor);
      button.setContentDescription(description);
      button.setBackground(XgramExpressiveC.iconButtonRipple(contentColor, 48f));
      button.setOnClickListener(onClick);
      button.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
      addView(button);
      return button;
    }

    /** True while at least one button is visible. */
    public boolean hasVisibleButtons () {
      for (int i = 0; i < getChildCount(); i++) {
        if (getChildAt(i).getVisibility() == View.VISIBLE) {
          return true;
        }
      }
      return false;
    }

    public void setShown (boolean shown, boolean animated) {
      if (this.shown == shown && (animated || visibility.getValue() == (shown ? 1f : 0f))) {
        return;
      }
      this.shown = shown;
      if (shown && getVisibility() != View.VISIBLE) {
        setVisibility(View.VISIBLE);
      }
      if (animated) {
        visibility.animateTo(shown ? 1f : 0f);
      } else {
        visibility.setValue(shown ? 1f : 0f);
        applyVisibility(shown ? 1f : 0f);
        if (!shown) {
          setVisibility(View.GONE);
        }
      }
    }

    public boolean isToolbarShown () {
      return shown;
    }

    private void applyVisibility (float value) {
      float alpha = Math.max(0f, Math.min(1f, value));
      setAlpha(alpha);
      float scale = .8f + .2f * value;
      setScaleX(scale);
      setScaleY(scale);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      int color = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
      if (color != contentColor) {
        contentColor = color;
        for (int i = 0; i < getChildCount(); i++) {
          View child = getChildAt(i);
          if (child instanceof android.widget.ImageView) {
            ((android.widget.ImageView) child).setColorFilter(color);
            child.setBackground(XgramExpressiveC.iconButtonRipple(color, 48f));
          }
        }
      }
      super.dispatchDraw(c);
    }
  }

  // Bottom sheet header (message options)

  /**
   * The top of a modal bottom sheet: the sheet color with 28dp top corners that square off as the sheet reaches the
   * top of the screen, and a 32x4dp drag handle in onSurfaceVariant at .4.
   */
  public static final class SheetTopDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private @ColorInt int color;
    private float cornerFactor = 1f;
    private float handleAlpha = 1f;

    /** Drag handle opacity (1 by default), e.g. faded out with the corners when the handle has no band of its own. */
    public void setHandleAlpha (float alpha) {
      alpha = Math.max(0f, Math.min(1f, alpha));
      if (this.handleAlpha != alpha) {
        this.handleAlpha = alpha;
        invalidateSelf();
      }
    }

    public void set (@ColorInt int color, float cornerFactor) {
      if (this.color != color || this.cornerFactor != cornerFactor) {
        this.color = color;
        this.cornerFactor = cornerFactor;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect b = getBounds();
      rect.set(b);
      float r = Math.min(rect.height() / 2f, Screen.dpf(XgramExpressive.CORNER_XL) * Math.max(0f, Math.min(1f, cornerFactor)));
      paint.setColor(color);
      if (r <= 0f) {
        c.drawRect(rect, paint);
      } else {
        setRadii(radii, r, r, 0, 0);
        path.rewind();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        c.drawPath(path, paint);
      }
      if (handleAlpha <= 0f) {
        return;
      }
      float handleW = Screen.dpf(32f), handleH = Screen.dpf(4f);
      rect.set(b.exactCenterX() - handleW / 2f, b.top + Screen.dpf(6f), b.exactCenterX() + handleW / 2f, b.top + Screen.dpf(6f) + handleH);
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), .4f * handleAlpha));
      c.drawRoundRect(rect, handleH / 2f, handleH / 2f, paint);
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
}
