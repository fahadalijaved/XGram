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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.RoundedCorner;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive chat composer (Google Messages, 2025): a filled text field in surfaceContainerHigh, 40dp on
 * one line with 20dp corners, bodyLarge text with a 16dp end gap, a primary cursor and handles, standard icon buttons
 * inside the field and a filled send button that the record button morphs into. Also the bars that take the
 * composer's place (the chat action button, the in-chat search bar). Everything here is only used while
 * {@link XgramExpressive#isEnabled()} is true; the callers keep Telegram X's code in their else paths.
 */
public final class XgramM3Composer {
  private XgramM3Composer () { }

  /** Height of the composer row: the 40dp field sits centred in it, the icon buttons are centred on it. */
  public static final float ROW_HEIGHT = 49f;
  /** Height of the field on one line. */
  public static final float FIELD_HEIGHT = 40f;
  /** M3 text field: the text keeps 16dp from the field's end edge. */
  public static final float TEXT_END_GAP = 16f;
  /** Text start: the emoji button (a 40dp state layer centred in the first 55dp) plus about 8dp. */
  public static final float TEXT_START = 56f;
  /** Minimum vertical text padding inside the 49dp row. */
  private static final float TEXT_MIN_VERTICAL = 8f;
  /** Gap kept between the hint and the first icon button inside the field. */
  public static final float HINT_END_GAP = 12f;
  /** Extra space between the record button and the other icon buttons, so they stay inside the field. */
  public static final float RECORD_EXTRA_GAP = 6f;
  /** End padding of the icon buttons while the record button is hidden: the field end margin. */
  public static final float ATTACH_END_PADDING = 8f;
  /** M3 space between a bar anchored to the bottom and the navigation bar (or the screen edge). */
  public static final float BOTTOM_GAP = 8f;
  /** Room kept between the resting round ends of the row (the send / record circle, the field's ends) and the screen outline. */
  public static final float CORNER_MARGIN = 4f;
  /** Minimum bottom gap while the display's corners are unknown (before Android 12, or none reported). */
  public static final float CORNER_FALLBACK_GAP = 12f;
  /**
   * Distance from a side edge to the centre of the round end nearest it. The record button's circle (2dp margin, 2dp
   * padding, half of its 45dp) is the closest at 26.5dp; the send button (27.5dp), the field's rounded start and the
   * emoji button (28dp) sit further in, so one value covers both corners, also in RTL.
   */
  public static final float END_CIRCLE_INSET = 26.5f;

  // Field geometry

  public static int textStartPadding () {
    return Screen.dp(TEXT_START);
  }

  /** Text is only typed while the send button shows, so it always ends 16dp before the send slot's field end. */
  public static int textEndPadding () {
    return XgramM3Chat.fieldEnd(true) + Screen.dp(TEXT_END_GAP);
  }

  /**
   * End margin of the field in px. With the record button the field always ends at the send slot; without it the
   * field reaches the edge (8dp) and makes room for the send button as it comes in ({@code sendFactor} 0..1).
   */
  public static float fieldEndPx (boolean recordVisible, float sendFactor) {
    final int withButton = XgramM3Chat.fieldEnd(true);
    if (recordVisible) {
      return withButton;
    }
    final int without = XgramM3Chat.fieldEnd(false);
    final float f = Math.max(0f, Math.min(1f, sendFactor));
    return without + (withButton - without) * f;
  }

  /** Vertical text padding that centres one line of {@code v}'s text in the 49dp row (about 10dp inside the field). */
  public static int textVerticalPadding (TextView v) {
    final Paint.FontMetricsInt fm = v.getPaint().getFontMetricsInt();
    final int line = v.getIncludeFontPadding() ? fm.bottom - fm.top : fm.descent - fm.ascent;
    return Math.max(Screen.dp(TEXT_MIN_VERTICAL), (Screen.dp(ROW_HEIGHT) - line) / 2);
  }

  /**
   * The composer's text: bodyLarge (16sp), the hint paint at the same size and face, paddings that centre one line
   * and keep the text 16dp clear of the field's end, RTL mirrored, and a primary cursor and selection handles.
   * Call once, after the input is created. Colors (onSurface text, onSurfaceVariant hint, primary links and
   * selection) come from the theme roles.
   */
  public static void styleInput (TextView v, @Nullable Paint hintPaint) {
    final float sizeSp = XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_L);
    final Typeface typeface = XgramExpressive.typeface(XgramExpressive.TS_BODY_L, false);
    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    v.setTypeface(typeface);
    if (hintPaint != null) {
      hintPaint.setTypeface(typeface);
      hintPaint.setTextSize(Screen.sp(sizeSp));
    }
    final int vertical = textVerticalPadding(v);
    final boolean rtl = Lang.rtl();
    final int start = textStartPadding(), end = textEndPadding();
    v.setPadding(rtl ? end : start, vertical, rtl ? start : end, vertical);
    tintInput(v, XgramExpressive.color(XgramExpressive.PRIMARY));
  }

  /** Cursor (2dp) and selection handles in {@code color}. Android 10+; Expressive design needs 13+. */
  public static void tintInput (TextView v, @ColorInt int color) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return;
    }
    try {
      GradientDrawable cursor = new GradientDrawable();
      cursor.setShape(GradientDrawable.RECTANGLE);
      cursor.setColor(color);
      cursor.setSize(Screen.dp(2f), Screen.dp(FIELD_HEIGHT) / 2);
      v.setTextCursorDrawable(cursor);
      Drawable handle = v.getTextSelectHandle();
      if (handle != null) {
        handle = handle.mutate();
        handle.setTint(color);
        v.setTextSelectHandle(handle);
      }
      Drawable left = v.getTextSelectHandleLeft();
      if (left != null) {
        left = left.mutate();
        left.setTint(color);
        v.setTextSelectHandleLeft(left);
      }
      Drawable right = v.getTextSelectHandleRight();
      if (right != null) {
        right = right.mutate();
        right.setTint(color);
        v.setTextSelectHandleRight(right);
      }
    } catch (Throwable ignored) {
      // A vendor TextView without the drawables keeps its own cursor
    }
  }

  /** Field container: surfaceContainerHigh, or the M3 disabled field (onSurface at 4% over the surface). */
  @ColorInt
  public static int fieldColor (boolean disabled) {
    if (disabled) {
      return me.vkryl.core.ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SURFACE), XgramExpressive.color(XgramExpressive.ON_SURFACE), .04f);
    }
    return XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH);
  }

  /** Hint opacity while the chat does not let the user write (M3 disabled label, about onSurface at 38%). */
  public static final float DISABLED_HINT_ALPHA = .6f;

  // Bottom gap

  /**
   * The gap (px) under the composer row, and under the bars that take its place, while the keyboard is down: 8dp
   * above the navigation bar, and at least what the display's rounded bottom corners need for the row's round ends
   * to stay inside the screen outline ({@link #cornerGap}). At least 12dp while the corners are unknown. With the
   * gesture hint hidden the navigation inset is 0, and without this the row sat on the very edge, cut by the corners.
   *
   * @param content the chat's view: its own edges once it is laid out in its window and in view
   * @param windowRoot the activity's decor view: the whole window until then
   * @param navigationInset the bottom system inset without the keyboard
   */
  public static int bottomGap (@Nullable View content, @Nullable View windowRoot, int navigationInset) {
    final int corners = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? cornerGap(content, windowRoot) : -1;
    return Math.max(navigationInset + Screen.dp(BOTTOM_GAP), corners >= 0 ? corners : Screen.dp(CORNER_FALLBACK_GAP));
  }

  /**
   * How far (px) the row's bottom has to stay above the bottom of the chat for the display's rounded bottom corners,
   * rounded up to the 4dp grid; 0 when the corners are out of the way, -1 when they are unknown. The resting 40dp
   * circles nearest the sides keep {@link #CORNER_MARGIN} inside the outline, and the pressed shape (the same box with
   * 8dp corners, which reaches further into the corner) stays inside it.
   */
  @RequiresApi(Build.VERSION_CODES.S)
  private static int cornerGap (@Nullable View content, @Nullable View windowRoot) {
    try {
      View window = windowRoot;
      float left = 0f, right = 0f, bottom = 0f;
      boolean placed = false;
      if (content != null && content.isAttachedToWindow() && content.getWidth() > 0 && content.getHeight() > 0) {
        // Where the chat is laid out in its window, not where a running slide draws it (the corners are window-relative)
        window = content.getRootView();
        int x = 0, y = 0;
        View view = content;
        while (view != window) {
          x += view.getLeft();
          y += view.getTop();
          final ViewParent parent = view.getParent();
          if (!(parent instanceof View)) {
            break;
          }
          view = (View) parent;
          x -= view.getScrollX();
          y -= view.getScrollY();
        }
        // Only while it is in view: a chat on a pager page scrolled away (its media tabs) counts as the whole window
        if (x >= 0 && y >= 0 && x + content.getWidth() <= window.getWidth() && y + content.getHeight() <= window.getHeight()) {
          left = x;
          right = x + content.getWidth();
          bottom = y + content.getHeight();
          placed = true;
        }
      }
      if (window == null) {
        return -1;
      }
      if (!placed) {
        if (window.getWidth() <= 0 || window.getHeight() <= 0) {
          return -1;
        }
        right = window.getWidth();
        bottom = window.getHeight();
      }
      final WindowInsets insets = window.getRootWindowInsets();
      if (insets == null) {
        return -1;
      }
      // null when the display has square corners or the window does not reach that corner (split screen)
      final RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
      final RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
      if (bottomLeft == null && bottomRight == null) {
        return -1;
      }
      final float inset = Screen.dpf(END_CIRCLE_INSET);
      float need = 0f;
      if (bottomLeft != null) {
        need = Math.max(need, cornerNeed(bottomLeft, bottom, bottomLeft.getCenter().x - (left + inset)));
      }
      if (bottomRight != null) {
        need = Math.max(need, cornerNeed(bottomRight, bottom, (right - inset) - bottomRight.getCenter().x));
      }
      if (need <= 0f) {
        return 0;
      }
      final float grid = Screen.dpf(4f);
      return (int) Math.ceil(Math.ceil(need / grid) * grid);
    } catch (Throwable ignored) {
      return -1;
    }
  }

  /**
   * The gap one bottom corner needs for the round end whose centre is {@code hx} px from the corner's centre towards
   * the side edge, half the row above the row's bottom.
   */
  @RequiresApi(Build.VERSION_CODES.S)
  private static float cornerNeed (RoundedCorner corner, float bottom, float hx) {
    final float radius = corner.getRadius();
    final float centerY = corner.getCenter().y;
    final float above = Screen.dpf(ROW_HEIGHT) / 2f;
    final float half = Screen.dpf(XgramExpressiveC.ICON_BUTTON_BOX) / 2f; // the field's ends have the same 20dp radius
    // Resting: the full circle, CORNER_MARGIN inside the outline
    float need = discNeed(radius, centerY, bottom, hx, above, half + Screen.dpf(CORNER_MARGIN));
    // Pressed: a box with 8dp corners, whose outer corner is an 8dp disc that must stay inside the outline
    final float pressed = Screen.dpf(XgramExpressive.CORNER_S);
    final float shift = half - pressed;
    need = Math.max(need, discNeed(radius, centerY, bottom, hx + shift, above - shift, pressed));
    return need;
  }

  /**
   * A disc of radius {@code r} lies inside a rounded corner of radius R when its centre is within R - r of the
   * corner's centre. For a disc centred {@code hx} px from the corner's centre towards the side edge and
   * {@code above} px over the row's bottom, returns how far the row's bottom must stay above {@code bottom}: 0 when
   * the straight edges alone keep it in (the corner is smaller than the disc, or the disc is beside it).
   */
  private static float discNeed (float radius, float centerY, float bottom, float hx, float above, float r) {
    final float inner = radius - r;
    if (inner <= 0f || hx <= 0f) {
      return 0f;
    }
    final float lowest = hx < inner ? centerY + (float) Math.sqrt(inner * inner - hx * hx) : centerY;
    return bottom - above - lowest;
  }

  // Bars that take the composer's place

  /** A drawable filled with a color role, resolved at draw time so it follows theme switches. Optional fade. */
  public static final class RoleDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int role;
    private float factor = 1f;

    public RoleDrawable (int role) {
      this.role = role;
    }

    /** Opacity 0..1 (the in-chat search bar fades in over the composer). */
    public void setFactor (float factor) {
      factor = Math.max(0f, Math.min(1f, factor));
      if (this.factor != factor) {
        this.factor = factor;
        invalidateSelf();
      }
    }

    @Override
    public void draw (@NonNull Canvas c) {
      if (factor <= 0f) {
        return;
      }
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(role), factor));
      c.drawRect(getBounds(), paint);
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

  /**
   * The chat action button (Join, Start, Restart, Unblock, Delete chat…) as an M3E full-width filled tonal button:
   * 40dp tall, 16dp from the sides, full corners morphing to 8dp while pressed, labelLarge. Returns the button's
   * background shape so its role can follow the button's state ({@link #setActionButtonActive}).
   */
  public static XgramExpressive.MorphDrawable styleActionButton (View wrap, TextView button) {
    wrap.setBackground(new RoleDrawable(XgramExpressive.SURFACE));
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Screen.dp(FIELD_HEIGHT), Gravity.CENTER_VERTICAL);
    params.leftMargin = params.rightMargin = Screen.dp(16f);
    button.setLayoutParams(params);
    XgramExpressive.applyText(button, XgramExpressive.TS_LABEL_L, false);
    int horizontal = Screen.dp(16f);
    button.setPadding(horizontal, 0, horizontal, 0);
    button.setMaxLines(2); // a status such as a restriction notice can take two lines
    XgramExpressive.MorphDrawable shape = XgramExpressive.attachPressMorph(button, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    shape.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
    return shape;
  }

  /**
   * Active: secondaryContainer (a tappable tonal button). Inactive (a status such as "You can't send messages
   * here"): the field's surfaceContainerHigh, like a disabled composer field.
   */
  public static void setActionButtonActive (@Nullable XgramExpressive.MorphDrawable shape, boolean active) {
    if (shape != null) {
      shape.setColorRole(active ? XgramExpressive.SECONDARY_CONTAINER : XgramExpressive.SURFACE_CONTAINER_HIGH);
    }
  }

  // Loading

  /**
   * The M3E contained loading indicator: a primaryContainer circle with the onPrimaryContainer shape morph, the
   * active indicator at 38/48 of the container. It only animates while it is visible: {@link #setShown} restarts
   * the frame loop when it becomes visible again.
   */
  public static final class ContainedLoadingView extends View {
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float boxDp;
    private long startTime;

    public ContainedLoadingView (Context context, float boxDp) {
      super(context);
      this.boxDp = boxDp;
      setWillNotDraw(false);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Alpha and scale from the caller's animation; restarts the loop when it comes back from 0. */
    public void setShown (float alpha, float scale) {
      boolean wasHidden = getAlpha() <= 0f;
      setAlpha(alpha);
      setScaleX(scale);
      setScaleY(scale);
      if (alpha <= 0f) {
        startTime = 0L;
      } else if (wasHidden) {
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (getAlpha() <= 0f) {
        startTime = 0L;
        return;
      }
      final float size = Math.min(Screen.dp(boxDp), Math.min(getWidth(), getHeight()));
      if (size <= 0f) {
        return;
      }
      final float cx = getWidth() / 2f, cy = getHeight() / 2f;
      containerPaint.setColor(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER));
      c.drawCircle(cx, cy, size / 2f, containerPaint);
      final long now = SystemClock.uptimeMillis();
      if (startTime == 0L) {
        startTime = now;
      }
      XgramLoadingIndicator.draw(c, cx, cy, size * XgramLoadingIndicator.ACTIVE_INDICATOR_SCALE, XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), now - startTime);
      if (isShown()) {
        postInvalidateOnAnimation();
      }
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      startTime = 0L;
    }
  }
}
