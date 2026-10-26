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

    private float topInner = -1f, bottomInner = -1f;

    /**
     * Continuous corners while the neighbours slide in and out: 0 gives the edge the outer (20dp) corners, 1 the inner
     * (4dp) ones, anything between a blend; {@code topInner} for the top edge (as far as there is a card above),
     * {@code bottomInner} for the bottom edge. A negative value takes the edge's corners from the segment. True when
     * the shape changed (the host then invalidates its outline).
     */
    public boolean setCornerFactors (float topInner, float bottomInner) {
      if (this.topInner != topInner || this.bottomInner != bottomInner) {
        this.topInner = topInner;
        this.bottomInner = bottomInner;
        invalidateSelf();
        return true;
      }
      return false;
    }

    private void build () {
      Rect b = getBounds();
      int inset = Screen.dp(XgramExpressive.LIST_GAP / 2f);
      rect.set(b.left, b.top + inset, b.right, b.bottom - inset);
      float outer = Screen.dpf(XgramExpressive.LIST_OUTER_CORNER), inner = Screen.dpf(XgramExpressive.LIST_INNER_CORNER);
      boolean roundTop = segment == XgramExpressive.SEGMENT_SINGLE || segment == XgramExpressive.SEGMENT_FIRST;
      boolean roundBottom = segment == XgramExpressive.SEGMENT_SINGLE || segment == XgramExpressive.SEGMENT_LAST;
      float topFactor = topInner >= 0f ? Math.min(1f, topInner) : roundTop ? 0f : 1f;
      float bottomFactor = bottomInner >= 0f ? Math.min(1f, bottomInner) : roundBottom ? 0f : 1f;
      float max = Math.max(0f, Math.min(rect.width(), rect.height()) / 2f);
      float top = Math.min(max, outer + (inner - outer) * topFactor), bottom = Math.min(max, outer + (inner - outer) * bottomFactor);
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

    /** End margin of the composer field in px, possibly mid-animation (see XgramM3Composer.fieldEndPx). */
    default float fieldEndPx () {
      return fieldEnd(hasTrailingButton());
    }
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
      // The block follows the field's end, also while the field makes room for the send button.
      float start = fieldStart(), end = trailing.fieldEndPx();
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

  /**
   * {@link #setTextButtonBackground(View, int)} whose state layer follows the Telegram X colour {@code contentColorId}
   * (the button's text colour) through theme switches: a colour read once kept the old scheme's state layer after a
   * day / night switch.
   */
  public static void setThemedTextButtonBackground (View v, @org.thunderdog.challegram.theme.ColorId int contentColorId) {
    XgramExpressive.MorphDrawable shape = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    shape.setHost(v);
    v.setBackground(XgramAppMotion.ThemedRipple.ofColorId(contentColorId, shape, shape.newMask()));
  }

  /** {@link #setThemedTextButtonBackground} for content in the M3 colour role {@code contentRole}. */
  public static void setTextButtonBackgroundRole (View v, int contentRole) {
    XgramExpressive.MorphDrawable shape = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    shape.setHost(v);
    v.setBackground(XgramAppMotion.ThemedRipple.ofRole(contentRole, shape, shape.newMask()));
  }

  /**
   * An M3 standard icon button that a view without child views draws itself (the close buttons of the chat's bars): a
   * 24dp icon in onSurfaceVariant with a 40dp state layer around it, onSurfaceVariant at the pressed opacity, whose
   * full corners spring to 8dp while pressed (as XgramAppMotion.iconButton). The host forwards its touches
   * ({@link #onTouchEvent}) and draws it ({@link #draw}); a press on it never reaches the host's own click and ripple.
   * Nothing is allocated per frame.
   */
  public static final class DrawnIconButton {
    private final View host;
    private final Drawable icon;
    private final Paint layerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private @Nullable Paint iconPaint;
    private int iconPaintColor;
    private final RectF layerRect = new RectF();
    private final Rect touchRect = new Rect();
    private final XgramExpressive.Spring press, layer;
    private float centerX, centerY;
    private boolean consuming, inside;

    public DrawnIconButton (@NonNull View host, @androidx.annotation.DrawableRes int iconRes) {
      this.host = host;
      this.icon = org.thunderdog.challegram.tool.Drawables.get(host.getResources(), XgramIconPack.map(iconRes));
      XgramExpressive.Spring.Listener invalidate = (s, value) -> this.host.invalidate();
      this.press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate).setThreshold(.005f);
      this.layer = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate).setThreshold(.005f);
    }

    /** The touch target (px, host coordinates); the icon and its state layer are centred in it. */
    public void setBounds (int left, int top, int right, int bottom) {
      touchRect.set(left, top, right, bottom);
      centerX = touchRect.exactCenterX();
      centerY = touchRect.exactCenterY();
    }

    public boolean contains (float x, float y) {
      return touchRect.contains((int) x, (int) y);
    }

    private void setPressed (boolean pressed) {
      press.animateTo(pressed ? 1f : 0f);
      layer.animateTo(pressed ? 1f : 0f);
    }

    /** True while the gesture belongs to the button (it started on it); {@code onClick} runs on a release over it. */
    public boolean onTouchEvent (@NonNull android.view.MotionEvent e, @Nullable Runnable onClick) {
      final float x = e.getX(), y = e.getY();
      switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_DOWN: {
          consuming = inside = contains(x, y);
          if (consuming) {
            setPressed(true);
          }
          return consuming;
        }
        case android.view.MotionEvent.ACTION_MOVE: {
          if (consuming && inside) {
            final float slop = Screen.getTouchSlop();
            if (x < touchRect.left - slop || x > touchRect.right + slop || y < touchRect.top - slop || y > touchRect.bottom + slop) {
              inside = false;
              setPressed(false);
            }
          }
          return consuming;
        }
        case android.view.MotionEvent.ACTION_UP: {
          if (!consuming) {
            return false;
          }
          final boolean click = inside;
          consuming = inside = false;
          setPressed(false);
          if (click && onClick != null) {
            host.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            onClick.run();
          }
          return true;
        }
        case android.view.MotionEvent.ACTION_CANCEL: {
          if (!consuming) {
            return false;
          }
          consuming = inside = false;
          setPressed(false);
          return true;
        }
      }
      return consuming;
    }

    /** Draws the state layer and the icon at {@code alpha} (0..1). */
    public void draw (@NonNull Canvas c, float alpha) {
      if (alpha <= 0f || touchRect.isEmpty()) {
        return;
      }
      final float layerAlpha = Math.max(0f, Math.min(1f, layer.getValue())) * alpha;
      if (layerAlpha > 0f) {
        final float size = Math.min(Screen.dpf(XgramExpressiveC.ICON_BUTTON_BOX), Math.min(touchRect.width(), touchRect.height()));
        layerRect.set(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f);
        final float radius = XgramExpressiveC.morphCorner(XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S, size, size, press.getValue());
        layerPaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), XgramExpressive.STATE_PRESSED * layerAlpha));
        c.drawRoundRect(layerRect, radius, radius, layerPaint);
      }
      final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      if (iconPaint == null || iconPaintColor != color) {
        iconPaintColor = color;
        iconPaint = org.thunderdog.challegram.tool.Paints.createPorterDuffPaint(iconPaint, color);
      }
      iconPaint.setAlpha(Math.round(255f * Math.min(1f, alpha)));
      org.thunderdog.challegram.tool.Drawables.draw(c, icon, centerX - icon.getMinimumWidth() / 2f, centerY - icon.getMinimumHeight() / 2f, iconPaint);
      iconPaint.setAlpha(255);
    }
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
   * onPrimaryContainer with a .10 state layer (a destructive one, delete, in error) and a plain tooltip naming each. It
   * enters with a scale (.8 to 1) and a fade on a spatial spring (SPATIAL_DEFAULT, or the token given), and only fades
   * with Reduce motion. It floats over the content, so it keeps a small elevation like a FAB.
   */
  public static final class FloatingToolbar extends android.widget.LinearLayout {
    private static final long REDUCED_FADE_MS = 150L;

    private final XgramExpressive.Spring visibility;
    private boolean shown;
    private @ColorInt int contentColor, errorColor;
    private final android.util.SparseBooleanArray destructive = new android.util.SparseBooleanArray();
    private final android.util.SparseIntArray icons = new android.util.SparseIntArray();

    public FloatingToolbar (Context context) {
      this(context, XgramExpressive.SPATIAL_DEFAULT);
    }

    /** {@code springToken}: the spatial spring it enters and leaves on (XgramExpressive.SPATIAL_*). */
    public FloatingToolbar (Context context, int springToken) {
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
      errorColor = XgramExpressive.color(XgramExpressive.ERROR);
      visibility = new XgramExpressive.Spring(springToken, 0f, (s, value) -> applyVisibility(value));
      visibility.setEndListener(() -> {
        if (!shown) {
          setVisibility(View.GONE);
        }
      });
    }

    public android.widget.ImageView addButton (int id, int iconRes, CharSequence description, View.OnClickListener onClick) {
      return addButton(id, iconRes, description, onClick, false);
    }

    /** {@code isDestructive}: the icon and its state layer in error (delete). */
    public android.widget.ImageView addButton (int id, int iconRes, CharSequence description, View.OnClickListener onClick, boolean isDestructive) {
      android.widget.ImageView button = new android.widget.ImageView(getContext());
      button.setId(id);
      button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      button.setImageResource(XgramIconPack.map(iconRes));
      icons.put(id, iconRes);
      destructive.put(id, isDestructive);
      final int color = isDestructive ? errorColor : contentColor;
      button.setColorFilter(color);
      button.setContentDescription(description);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        button.setTooltipText(description); // a plain tooltip on long press, never a toast
      }
      button.setBackground(XgramExpressiveC.iconButtonRipple(color, 48f));
      button.setOnClickListener(onClick);
      button.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
      addView(button);
      return button;
    }

    /**
     * Shows or hides the button {@code id} and gives it {@code iconRes} (through the icon pack) and {@code description}.
     * The drawable is only replaced when the icon changes, so a refresh does not flicker.
     */
    public void setButton (int id, boolean visible, int iconRes, @Nullable CharSequence description) {
      View view = findViewById(id);
      if (!(view instanceof android.widget.ImageView)) {
        return;
      }
      android.widget.ImageView button = (android.widget.ImageView) view;
      int visibility = visible ? View.VISIBLE : View.GONE;
      if (button.getVisibility() != visibility) {
        button.setVisibility(visibility);
      }
      if (iconRes != 0 && icons.get(id) != iconRes) {
        icons.put(id, iconRes);
        button.setImageResource(XgramIconPack.map(iconRes));
      }
      if (description != null && !android.text.TextUtils.equals(button.getContentDescription(), description)) {
        button.setContentDescription(description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          button.setTooltipText(description);
        }
      }
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
      if (animated && XgramMotion.reduce()) {
        // Reduce motion: a short crossfade at full size, no scale
        visibility.setValue(shown ? 1f : 0f);
        setScaleX(1f);
        setScaleY(1f);
        animate().cancel();
        animate().alpha(shown ? 1f : 0f).setDuration(REDUCED_FADE_MS).withEndAction(() -> {
          if (!this.shown) {
            setVisibility(View.GONE);
          }
        }).start();
        return;
      }
      animate().cancel();
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
      int error = XgramExpressive.color(XgramExpressive.ERROR);
      if (color != contentColor || error != errorColor) {
        contentColor = color;
        errorColor = error;
        for (int i = 0; i < getChildCount(); i++) {
          View child = getChildAt(i);
          if (child instanceof android.widget.ImageView) {
            int tint = destructive.get(child.getId()) ? error : color;
            ((android.widget.ImageView) child).setColorFilter(tint);
            child.setBackground(XgramExpressiveC.iconButtonRipple(tint, 48f));
          }
        }
      }
      super.dispatchDraw(c);
    }
  }

  // Bottom sheet header (message options)

  /**
   * The top of a modal bottom sheet: the sheet color with 28dp top corners that square off as the sheet reaches the
   * top of the screen, and a 32x4dp drag handle in onSurfaceVariant at .4, horizontally centered, its top
   * {@link #HANDLE_TOP} below the sheet top. The sheet must be one shape: draw it over the whole sheet body, or give it
   * at least the corner radius of height and clip the body below it to the same curve (see {@link #bodyOutline}). A
   * short band over a square body shows the body's corners as a notch under the curve.
   */
  public static final class SheetTopDrawable extends Drawable {
    /** Distance of the drag handle's top from the top of the sheet (dp). */
    public static final float HANDLE_TOP = 8f;

    /** The corner radius for {@code cornerFactor}: 28dp at 1, square at 0. */
    public static float cornerRadius (float cornerFactor) {
      return Screen.dpf(XgramExpressive.CORNER_XL) * Math.max(0f, Math.min(1f, cornerFactor));
    }

    /**
     * The outline that clips a sheet body (a view whose top is {@code sheetTopOffset} px below the sheet's top) to the
     * same 28dp curve as this drawable: rounded top corners, square bottom (the rounding is pushed below the view).
     */
    public static void bodyOutline (@NonNull View view, @NonNull Outline outline, float sheetTopOffset, float cornerFactor) {
      final int width = view.getWidth(), height = view.getHeight();
      final float radius = Math.min(cornerRadius(cornerFactor), width / 2f);
      if (radius <= 0f || width <= 0 || height <= 0) {
        outline.setRect(0, 0, Math.max(width, 0), Math.max(height, 0));
      } else {
        outline.setRoundRect(0, -Math.round(sheetTopOffset), width, height + (int) Math.ceil(radius), radius);
      }
    }

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
      // only the top corners are rounded: the radius may take the whole height (not half of it, which cut a short
      // band's corners to a smaller curve than the 28dp one)
      float r = Math.min(Math.min(rect.height(), rect.width() / 2f), cornerRadius(cornerFactor));
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
      float handleW = Screen.dpf(32f), handleH = Screen.dpf(4f), handleTop = b.top + Screen.dpf(HANDLE_TOP);
      rect.set(b.exactCenterX() - handleW / 2f, handleTop, b.exactCenterX() + handleW / 2f, handleTop + handleH);
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
