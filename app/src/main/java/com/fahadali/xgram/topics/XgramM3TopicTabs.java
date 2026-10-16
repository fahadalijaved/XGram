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
package com.fahadali.xgram.topics;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramExpressiveC;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive look of the topic tabs ({@link ForumTabsView}), the same as the chat list's folder tabs
 * (ViewPagerTopView): one secondaryContainer pill with full corners behind the selected tab whose edges spring
 * (spatial fast) from tab to tab, labelLarge labels in onSurfaceVariant turning onSecondaryContainer, M3 badges, edges
 * that fade while there is more to scroll to, and a spring that brings a picked tab to the middle. Along the side the
 * strip is a navigation rail: a 56 x 32dp indicator behind a 24dp icon, the label in labelMedium below.
 *
 * Main thread only. Everything is allocated once (the fade ramp again only when the density changes); frames run
 * only while a spring moves or the strip scrolls; nothing here requests a layout. Reduce motion: springs snap.
 */
final class XgramM3TopicTabs extends RecyclerView.ItemDecoration implements RecyclerView.OnItemTouchListener {
  interface Selection {
    /** Adapter position of the selected tab, or -1. */
    int selectedPosition ();
  }

  static final float BAR_HEIGHT_DP = 48f, ICON_BUTTON_WIDTH_DP = 48f;
  static final float TAB_PADDING_DP = 16f, TAB_ICON_DP = 20f, TAB_GAP_DP = 8f;
  static final float PILL_HEIGHT_DP = 32f;
  static final float RAIL_PILL_WIDTH_DP = 56f, RAIL_INDICATOR_TOP_DP = 4f, RAIL_ICON_DP = 24f, RAIL_LABEL_GAP_DP = 4f, RAIL_BOTTOM_DP = 8f;
  static final float RAIL_TOGGLE_HEIGHT_DP = 48f, RAIL_ADD_HEIGHT_DP = 56f;
  private static final float BADGE_HEIGHT_DP = 16f, BADGE_PADDING_DP = 4f;
  private static final float FADE_DP = 24f;

  /** Unread badge: error container (the muted one: surfaceContainerHighest). One place to change. */
  private static final int BADGE = XgramExpressive.ERROR, ON_BADGE = XgramExpressive.ON_ERROR; // M3 Badge: error / onError (BadgeTokens)
  private static final int BADGE_MUTED = XgramExpressive.SURFACE_CONTAINER_HIGHEST, ON_BADGE_MUTED = XgramExpressive.ON_SURFACE_VARIANT;

  private final RecyclerView rv;
  private final LinearLayoutManager manager;
  private final boolean vertical;
  private final Selection selection;

  /** Pill edges as offsets from the selected tab's own edges: they spring to 0, and scrolling moves both together. */
  private final XgramExpressive.Spring pillStart, pillEnd;
  private final XgramExpressive.Spring fadeBefore, fadeAfter;
  private final XgramExpressive.Spring scroll;
  private int scrolled;
  private boolean fadesReady;

  private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint fadePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final RectF rect = new RectF();
  /** An alpha ramp drawn in the paint's colour (see {@link #newFadeRamp}): a new fade colour is a setColor, not a new shader. */
  private @Nullable BitmapShader fadeShader;
  private int fadeLength;
  /** The colour the strip sits on (the app bar's, as it changes); null: the strip's own background colour. */
  private @Nullable com.fahadali.xgram.appearance.XgramM3Chat.ColorSource fadeColor;

  XgramM3TopicTabs (@NonNull RecyclerView rv, @NonNull LinearLayoutManager manager, boolean vertical, @NonNull Selection selection) {
    this.rv = rv;
    this.manager = manager;
    this.vertical = vertical;
    this.selection = selection;
    XgramExpressive.Spring.Listener invalidate = (s, value) -> rv.invalidate();
    this.pillStart = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate).setThreshold(.5f);
    this.pillEnd = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate).setThreshold(.5f);
    this.fadeBefore = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
    this.fadeAfter = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
    this.scroll = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> scrollStep(value)).setThreshold(.5f);
    rv.addItemDecoration(this);
    rv.addOnItemTouchListener(this);
  }

  // Selection

  /** Call when the selection moves, before the tabs are rebound: the pill springs from where it is to the new tab. */
  void moveSelection (int fromPosition, int toPosition) {
    View to = toPosition != -1 ? manager.findViewByPosition(toPosition) : null;
    if (to == null) {
      pillStart.setValue(0f);
      pillEnd.setValue(0f);
      rv.invalidate();
      return;
    }
    float toStart = start(to), toEnd = end(to);
    View from = fromPosition != -1 ? manager.findViewByPosition(fromPosition) : null;
    float currentStart, currentEnd;
    if (from != null) {
      currentStart = start(from) + pillStart.getValue();
      currentEnd = end(from) + pillEnd.getValue();
    } else {
      // The old tab is off screen: the pill grows from the middle of the new one
      currentStart = currentEnd = (toStart + toEnd) / 2f;
    }
    pillStart.setValue(currentStart - toStart);
    pillEnd.setValue(currentEnd - toEnd);
    pillStart.animateTo(0f);
    pillEnd.animateTo(0f);
    rv.invalidate();
  }

  /** Another forum, or the strip is gone: everything at rest. */
  void reset () {
    pillStart.setValue(0f);
    pillEnd.setValue(0f);
    scroll.cancel();
    fadesReady = false;
    rv.invalidate();
  }

  private float start (View v) {
    return vertical ? v.getTop() + v.getTranslationY() + Screen.dp(RAIL_INDICATOR_TOP_DP) : v.getLeft() + v.getTranslationX();
  }

  private float end (View v) {
    return vertical ? start(v) + Screen.dp(PILL_HEIGHT_DP) : v.getRight() + v.getTranslationX();
  }

  // Scroll into view

  /**
   * Springs the strip so the tab at {@code position} sits in the middle (as far as the ends allow). False when that
   * tab is not laid out: the caller scrolls to it the usual way.
   */
  boolean scrollToCenter (int position) {
    View v = position != -1 ? manager.findViewByPosition(position) : null;
    if (v == null || rv.getWidth() == 0 || rv.getHeight() == 0) {
      return false;
    }
    float center, viewportCenter;
    if (vertical) {
      center = (v.getTop() + v.getBottom()) / 2f;
      viewportCenter = (rv.getPaddingTop() + rv.getHeight() - rv.getPaddingBottom()) / 2f;
    } else {
      center = (v.getLeft() + v.getRight()) / 2f;
      viewportCenter = (rv.getPaddingLeft() + rv.getWidth() - rv.getPaddingRight()) / 2f;
    }
    int delta = Math.round(center - viewportCenter);
    int count = itemCount();
    if (delta < 0) {
      View first = manager.findViewByPosition(visualFirst(count));
      if (first != null) {
        int room = vertical ? first.getTop() - rv.getPaddingTop() : first.getLeft() - rv.getPaddingLeft();
        delta = Math.max(delta, Math.min(0, room));
      }
    } else if (delta > 0) {
      View last = manager.findViewByPosition(visualLast(count));
      if (last != null) {
        int room = vertical ? last.getBottom() - (rv.getHeight() - rv.getPaddingBottom()) : last.getRight() - (rv.getWidth() - rv.getPaddingRight());
        delta = Math.min(delta, Math.max(0, room));
      }
    }
    if (delta == 0) {
      return true;
    }
    rv.stopScroll();
    scrolled = 0;
    scroll.setValue(0f);
    scroll.animateTo(delta);
    return true;
  }

  private void scrollStep (float value) {
    int to = Math.round(value);
    int step = to - scrolled;
    if (step != 0) {
      scrolled = to;
      if (vertical) {
        rv.scrollBy(0, step);
      } else {
        rv.scrollBy(step, 0);
      }
    }
  }

  private int itemCount () {
    RecyclerView.Adapter<?> adapter = rv.getAdapter();
    return adapter != null ? adapter.getItemCount() : 0;
  }

  /** Position of the item at the top or the visual left (a right-to-left strip is reversed). */
  private int visualFirst (int count) {
    return !vertical && manager.getReverseLayout() ? count - 1 : 0;
  }

  private int visualLast (int count) {
    return !vertical && manager.getReverseLayout() ? 0 : count - 1;
  }

  private boolean moreBefore (int count) {
    if (count == 0) {
      return false;
    }
    View v = manager.findViewByPosition(visualFirst(count));
    if (v == null) {
      return true;
    }
    return vertical ? v.getTop() < rv.getPaddingTop() - 1 : v.getLeft() < rv.getPaddingLeft() - 1;
  }

  private boolean moreAfter (int count) {
    if (count == 0) {
      return false;
    }
    View v = manager.findViewByPosition(visualLast(count));
    if (v == null) {
      return true;
    }
    return vertical ? v.getBottom() > rv.getHeight() - rv.getPaddingBottom() + 1 : v.getRight() > rv.getWidth() - rv.getPaddingRight() + 1;
  }

  // Touch: a finger on the strip stops the scroll spring

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView recyclerView, @NonNull MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      scroll.cancel();
    }
    return false;
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView recyclerView, @NonNull MotionEvent e) { }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }

  // Drawing

  /** The pill, under the tabs. */
  @Override
  public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    int position = selection.selectedPosition();
    View v = position != -1 ? manager.findViewByPosition(position) : null;
    if (v == null) {
      return;
    }
    float start = start(v) + pillStart.getValue(), end = end(v) + pillEnd.getValue();
    if (end - start <= 0f) {
      return;
    }
    float height = Screen.dp(PILL_HEIGHT_DP);
    if (vertical) {
      float cx = (v.getLeft() + v.getRight()) / 2f + v.getTranslationX();
      float width = Math.min(Screen.dp(RAIL_PILL_WIDTH_DP), v.getWidth() - Screen.dp(4f));
      rect.set(cx - width / 2f, start, cx + width / 2f, end);
    } else {
      float cy = (v.getTop() + v.getBottom()) / 2f + v.getTranslationY();
      rect.set(start, cy - height / 2f, end, cy + height / 2f);
    }
    float radius = Math.min(rect.width(), rect.height()) / 2f;
    pillPaint.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
    c.drawRoundRect(rect, radius, radius, pillPaint);
  }

  /** The colour the edges fade from: the colour the strip sits on (null: the strip's own background colour). */
  void setFadeColor (@Nullable com.fahadali.xgram.appearance.XgramM3Chat.ColorSource source) {
    this.fadeColor = source;
    rv.invalidate();
  }

  /**
   * The fade as an ALPHA_8 ramp ({@code length} px from opaque to clear, along the strip). An alpha-only bitmap shader
   * draws in the paint's colour, so the colour can change every frame without a new shader.
   */
  private static BitmapShader newFadeRamp (int length, boolean vertical) {
    length = Math.max(1, length);
    final int[] pixels = new int[length];
    for (int i = 0; i < length; i++) {
      pixels[i] = Math.round(255f * (1f - (i + .5f) / length)) << 24;
    }
    final int width = vertical ? 1 : length, height = vertical ? length : 1;
    final Bitmap ramp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
    ramp.setPixels(pixels, 0, width, 0, 0, width, height);
    return new BitmapShader(ramp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
  }

  /** The fading edges, over the tabs, while there is more to scroll to on that side. */
  @Override
  public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    int count = itemCount();
    float before = moreBefore(count) ? 1f : 0f, after = moreAfter(count) ? 1f : 0f;
    if (!fadesReady) {
      fadesReady = true;
      fadeBefore.setValue(before);
      fadeAfter.setValue(after);
    } else {
      if (fadeBefore.getTarget() != before) {
        fadeBefore.animateTo(before);
      }
      if (fadeAfter.getTarget() != after) {
        fadeAfter.animateTo(after);
      }
    }
    float a = Math.max(0f, Math.min(1f, fadeBefore.getValue())), b = Math.max(0f, Math.min(1f, fadeAfter.getValue()));
    if (a <= 0f && b <= 0f) {
      return;
    }
    int length = Screen.dp(FADE_DP);
    if (fadeShader == null || fadeLength != length) {
      fadeLength = length;
      fadeShader = newFadeRamp(length, vertical);
      fadePaint.setShader(fadeShader);
    }
    // The colour the strip sits on: the app bar's (it changes frame by frame while the header fades into selection or
    // search), else the strip's own background
    int color = fadeColor != null ? fadeColor.color() : Theme.getColor(ColorId.background);
    int colorAlpha = (color >>> 24) & 0xff;
    fadePaint.setColor(color);
    int width = parent.getWidth(), height = parent.getHeight();
    if (a > 0f) {
      fadePaint.setAlpha(Math.round(colorAlpha * a));
      if (vertical) {
        c.drawRect(0, 0, width, length, fadePaint);
      } else {
        c.drawRect(0, 0, length, height, fadePaint);
      }
    }
    if (b > 0f) {
      fadePaint.setAlpha(Math.round(colorAlpha * b));
      int save = c.save();
      if (vertical) {
        c.translate(0, height);
        c.scale(1f, -1f);
        c.drawRect(0, 0, width, length, fadePaint);
      } else {
        c.translate(width, 0);
        c.scale(-1f, 1f);
        c.drawRect(0, 0, length, height, fadePaint);
      }
      c.restoreToCount(save);
    }
  }

  // Rail geometry

  static float railIconCenterY () {
    return Screen.dp(RAIL_INDICATOR_TOP_DP) + Screen.dp(PILL_HEIGHT_DP) / 2f;
  }

  static int railLineHeight () {
    return Screen.sp(XgramExpressive.lineHeightSp(XgramExpressive.TS_LABEL_M));
  }

  static float railLabelTop () {
    return Screen.dp(RAIL_INDICATOR_TOP_DP) + Screen.dp(PILL_HEIGHT_DP) + Screen.dp(RAIL_LABEL_GAP_DP);
  }

  static int railItemHeight (int lines) {
    return Math.round(railLabelTop()) + railLineHeight() * Math.max(1, lines) + Screen.dp(RAIL_BOTTOM_DP);
  }

  // Text

  private static @Nullable TextPaint labelPaint, railLabelPaint, badgePaint;

  /** labelLarge, as the folder tabs. The caller sets the color. */
  static TextPaint labelPaint () {
    if (labelPaint == null) {
      labelPaint = newTextPaint(XgramExpressive.TS_LABEL_L);
    }
    return labelPaint;
  }

  /** labelMedium, the rail labels. */
  static TextPaint railLabelPaint () {
    if (railLabelPaint == null) {
      railLabelPaint = newTextPaint(XgramExpressive.TS_LABEL_M);
    }
    return railLabelPaint;
  }

  private static TextPaint badgePaint () {
    if (badgePaint == null) {
      badgePaint = newTextPaint(XgramExpressive.TS_LABEL_S);
    }
    return badgePaint;
  }

  private static TextPaint newTextPaint (int ts) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    float sizeSp = XgramExpressive.textSizeSp(ts);
    paint.setTextSize(Screen.sp(sizeSp));
    if (sizeSp > 0f) {
      paint.setLetterSpacing(XgramExpressive.trackingSp(ts) / sizeSp);
    }
    return paint;
  }

  /** The baseline that centers one line of {@code paint} on {@code centerY}. */
  static float baseline (TextPaint paint, float centerY) {
    return centerY - (paint.descent() + paint.ascent()) / 2f;
  }

  // Badges: M3 large badge, 16dp high, labelSmall, full corners

  private static final Paint BADGE_FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final RectF BADGE_RECT = new RectF();

  static float badgeTextWidth (@NonNull String text) {
    return text.isEmpty() ? 0f : badgePaint().measureText(text);
  }

  static float badgeWidth (float textWidth) {
    return Math.max(Screen.dp(BADGE_HEIGHT_DP), textWidth + Screen.dp(BADGE_PADDING_DP) * 2);
  }

  static void drawBadge (@NonNull Canvas c, float left, float centerY, @NonNull String text, float textWidth, boolean muted) {
    float width = badgeWidth(textWidth), height = Screen.dp(BADGE_HEIGHT_DP);
    BADGE_RECT.set(left, centerY - height / 2f, left + width, centerY + height / 2f);
    BADGE_FILL.setColor(XgramExpressive.color(muted ? BADGE_MUTED : BADGE));
    c.drawRoundRect(BADGE_RECT, height / 2f, height / 2f, BADGE_FILL);
    TextPaint paint = badgePaint();
    paint.setColor(XgramExpressive.color(muted ? ON_BADGE_MUTED : ON_BADGE));
    c.drawText(text, left + (width - textWidth) / 2f, baseline(paint, centerY), paint);
  }

  // Press feedback of a tab: an onSurface state layer in the pill's shape

  static Drawable tabBackground (boolean vertical) {
    return new StateRipple(XgramExpressive.ON_SURFACE, new PillMask(vertical));
  }

  /** A ripple whose color follows the theme (checked on every press). */
  private static final class StateRipple extends RippleDrawable {
    private final int role;
    private int color;

    StateRipple (int role, Drawable mask) {
      this(role, XgramExpressive.color(role), mask);
    }

    private StateRipple (int role, int color, Drawable mask) {
      super(ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(color)), null, mask);
      this.role = role;
      this.color = color;
    }

    @Override
    protected boolean onStateChange (int[] stateSet) {
      int now = XgramExpressive.color(role);
      if (now != color) {
        color = now;
        setColor(ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(now)));
      }
      return super.onStateChange(stateSet);
    }
  }

  /** The tab's pill (or the rail's indicator) as a ripple mask. */
  private static final class PillMask extends Drawable {
    private final boolean vertical;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    PillMask (boolean vertical) {
      this.vertical = vertical;
      this.paint.setColor(0xffffffff);
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect b = getBounds();
      float height = Screen.dp(PILL_HEIGHT_DP);
      if (vertical) {
        float width = Math.min(Screen.dp(RAIL_PILL_WIDTH_DP), b.width() - Screen.dp(4f));
        float top = b.top + Screen.dp(RAIL_INDICATOR_TOP_DP);
        bounds.set(b.exactCenterX() - width / 2f, top, b.exactCenterX() + width / 2f, top + height);
      } else {
        bounds.set(b.left, b.exactCenterY() - height / 2f, b.right, b.exactCenterY() + height / 2f);
      }
      float radius = Math.min(bounds.width(), bounds.height()) / 2f;
      if (radius > 0f) {
        c.drawRoundRect(bounds, radius, radius, paint);
      }
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
