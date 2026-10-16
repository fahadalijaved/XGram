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
 *
 * Sizes follow androidx.compose.foundation NonInteractiveScrollbarDefaults (Apache License 2.0,
 * Copyright The Android Open Source Project): 4dp thickness, 24dp minimum and 90% maximum thumb length,
 * 2dp track inset, 70% opacity, fading out over 250ms after 400ms.
 */
package com.fahadali.xgram.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import me.vkryl.core.ColorUtils;

/**
 * Material 3 Expressive scroll indicator for a RecyclerView: a rounded thumb at the end edge that fades in while the
 * list scrolls and out shortly after it stops. {@link #MODE_DRAGGABLE} lists (fully loaded and at least three screens
 * long) can be grabbed: the thumb widens and turns primary, the list follows the finger and an optional label pill
 * shows where you are (a month, a letter).
 *
 * The system scrollbar is silenced (transparent thumb) instead of disabled, so every Telegram X toggle of
 * {@link View#setVerticalScrollBarEnabled} still decides whether the indicator may show.
 *
 * Cost: everything is pre-allocated; drawing is one round rect (two with a label) per frame, frames only run while
 * the list scrolls or a spring moves, and nothing here ever requests a layout. Dragging scrolls once per position
 * change, not per frame. Main thread only.
 */
public final class XgramScrollIndicator extends RecyclerView.ItemDecoration implements RecyclerView.OnItemTouchListener, View.OnAttachStateChangeListener {
  public static final int MODE_VISUAL = 0, MODE_DRAGGABLE = 1;

  public interface LabelProvider {
    @Nullable CharSequence labelFor (int adapterPosition);
  }

  /** Asked when the thumb shows: false while the list can still load more (pagination makes the thumb lie). */
  public interface DragCondition {
    boolean canDrag ();
  }

  private static final float THICKNESS_DP = 4f, PRESSED_THICKNESS_DP = 10f, EDGE_DP = 4f, TRACK_INSET_DP = 4f;
  private static final float MIN_VISUAL_DP = 24f, MIN_DRAG_DP = 48f, MAX_FRACTION = .9f;
  private static final float HIT_WIDTH_DP = 48f, HIT_SLOP_DP = 12f, CORNER_TRACK_INSET_DP = 20f;
  private static final float LABEL_HEIGHT_DP = 48f, LABEL_PADDING_DP = 16f, LABEL_GAP_DP = 12f;
  private static final float IDLE_OPACITY = .7f;
  private static final long HIDE_DELAY_VISUAL = 400L, HIDE_DELAY_DRAG = 1200L, EXCLUSION_THROTTLE_MS = 120L;

  /** The indicator of {@code rv} (installed once; a later call can only upgrade VISUAL to DRAGGABLE). */
  public static XgramScrollIndicator install (@NonNull RecyclerView rv, int mode) {
    XgramScrollIndicator existing = find(rv);
    if (existing != null) {
      if (mode > existing.mode) {
        existing.mode = mode;
      }
      return existing;
    }
    XgramScrollIndicator indicator = new XgramScrollIndicator(rv, mode);
    rv.addItemDecoration(indicator);
    rv.addOnItemTouchListener(indicator);
    rv.addOnScrollListener(indicator.scrollListener);
    rv.addOnAttachStateChangeListener(indicator);
    if (rv.getWindowToken() != null) {
      indicator.detectContainer();
    }
    return indicator;
  }

  @Nullable
  public static XgramScrollIndicator find (@Nullable RecyclerView rv) {
    if (rv != null) {
      for (int i = 0, count = rv.getItemDecorationCount(); i < count; i++) {
        RecyclerView.ItemDecoration d = rv.getItemDecorationAt(i);
        if (d instanceof XgramScrollIndicator) {
          return (XgramScrollIndicator) d;
        }
      }
    }
    return null;
  }

  private final RecyclerView rv;
  private int mode;
  private @Nullable LabelProvider labelProvider;
  private @Nullable DragCondition dragCondition;
  private boolean canDrag = true, dragAllowed = true;
  private int topInset = -1, autoTopInset;
  private int fixedLength;
  private boolean manual, ignoreFlag;
  private final boolean silencedByFlag;

  private final Anim alpha, press, offset, labelAlpha, labelScale, labelPillWidth;
  private final Runnable hideRunnable = this::hideNow;
  private int scrollState = RecyclerView.SCROLL_STATE_IDLE;

  // Last drawn geometry (hit testing, drag)
  private float trackTop, trackBottom, thumbTop, thumbLen;
  private boolean drawn, drawnDraggable;

  // Drag
  private boolean dragging, releasePending;
  private float grabOffset, dragTop, dragLen, dragTrackTop, dragTrackBottom;
  private int dragSpan, lastPos = RecyclerView.NO_POSITION;
  private @Nullable CharSequence label;

  private final Exclusion exclusion = new Exclusion();

  private final RectF thumbRect = new RectF();

  private XgramScrollIndicator (RecyclerView rv, int mode) {
    this.rv = rv;
    this.mode = mode;
    Runnable invalidate = rv::invalidate;
    this.alpha = new Anim(0f, .002f, invalidate);
    this.press = new Anim(0f, .002f, invalidate);
    this.offset = new Anim(0f, .5f, invalidate);
    this.labelAlpha = new Anim(0f, .002f, invalidate);
    this.labelScale = new Anim(1f, .002f, invalidate);
    this.labelPillWidth = new Anim(0f, .5f, invalidate);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Keep the flag (Telegram X toggles it), draw nothing: the flag becomes "the indicator may show".
      rv.setVerticalScrollbarThumbDrawable(new ColorDrawable(0));
      rv.setVerticalScrollbarTrackDrawable(null);
      rv.setScrollBarFadeDuration(0);
      rv.setScrollBarDefaultDelayBeforeFade(0);
      silencedByFlag = false;
    } else {
      rv.setVerticalScrollBarEnabled(false);
      silencedByFlag = true;
    }
  }

  // Configuration

  public XgramScrollIndicator setLabelProvider (@Nullable LabelProvider provider) {
    this.labelProvider = provider;
    return this;
  }

  public XgramScrollIndicator setCanDrag (boolean canDrag) {
    this.canDrag = canDrag;
    return this;
  }

  public XgramScrollIndicator setDragCondition (@Nullable DragCondition condition) {
    this.dragCondition = condition;
    return this;
  }

  /** Space above the track below the padding; by default 20dp inside a container with 28dp top corners, else 4dp. */
  public XgramScrollIndicator setTopInset (int px) {
    this.topInset = px;
    return this;
  }

  /** A thumb of a fixed length (paginated lists whose length estimate keeps changing). */
  public XgramScrollIndicator setFixedThumbLength (int px) {
    this.fixedLength = px;
    return this;
  }

  /** Visibility only through {@link #setShown}: the owner shows and hides it with its own scroll UI. */
  public XgramScrollIndicator setManualVisibility (boolean manual) {
    this.manual = manual;
    return this;
  }

  /** Lists without a system scrollbar of their own (sticker and GIF grids): show regardless of the scrollbar flag. */
  public XgramScrollIndicator setIgnoreScrollbarFlag (boolean ignore) {
    this.ignoreFlag = ignore;
    return this;
  }

  public void setShown (boolean shown) {
    if (shown) {
      show();
    } else {
      rv.removeCallbacks(hideRunnable);
      hideNow();
    }
  }

  // Visibility

  private boolean allowed () {
    return ignoreFlag || silencedByFlag || rv.isVerticalScrollBarEnabled();
  }

  private boolean canDragNow () {
    return mode == MODE_DRAGGABLE && canDrag && (dragCondition == null || dragCondition.canDrag());
  }

  private void show () {
    if (!allowed()) {
      return;
    }
    rv.removeCallbacks(hideRunnable);
    if (alpha.getTarget() != 1f) {
      dragAllowed = canDragNow();
      alpha.animateTo(1f, XgramExpressive.EFFECTS_FAST);
    }
  }

  private void scheduleHide () {
    rv.removeCallbacks(hideRunnable);
    rv.postDelayed(hideRunnable, drawnDraggable ? HIDE_DELAY_DRAG : HIDE_DELAY_VISUAL);
  }

  private void hideNow () {
    if (!dragging) {
      alpha.animateTo(0f, XgramExpressive.EFFECTS_SLOW);
    }
  }

  private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
      scrollState = newState;
      if (manual || dragging) {
        return;
      }
      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        if (alpha.getTarget() > 0f) {
          dragAllowed = canDragNow();
          scheduleHide();
          rv.invalidate(); // settles the back gesture exclusion on the resting thumb
        }
      } else {
        rv.removeCallbacks(hideRunnable);
      }
    }

    @Override
    public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (dy != 0 && !manual && !dragging) {
        show();
        if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
          scheduleHide();
        }
      }
    }
  };

  @Override
  public void onViewAttachedToWindow (@NonNull View v) {
    detectContainer();
  }

  @Override
  public void onViewDetachedFromWindow (@NonNull View v) {
    rv.removeCallbacks(hideRunnable);
    dragging = false;
    releasePending = false;
    alpha.setValue(0f);
    press.setValue(0f);
    offset.setValue(0f);
    labelAlpha.setValue(0f);
    labelScale.setValue(1f);
    labelPillWidth.setValue(labelPillWidth.getTarget());
    clearExclusion();
  }

  private void detectContainer () {
    int inset = 0;
    ViewParent p = rv.getParent();
    for (int i = 0; i < 4 && p instanceof View; i++, p = p.getParent()) {
      if (p instanceof XgramM3Bars.CornerFrameLayout) {
        inset = Screen.dp(CORNER_TRACK_INSET_DP);
        break;
      }
    }
    autoTopInset = inset;
  }

  // Drawing

  @Override
  public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    float a = alpha.getValue();
    if ((!dragging && a <= 0f) || !allowed() || !layoutGeometry()) {
      drawn = drawnDraggable = false;
      clearExclusion();
      return;
    }
    if (releasePending) {
      // Spring from where the finger left the thumb to where the list now says it is
      releasePending = false;
      float computed = thumbTop;
      offset.setValue(dragTop - computed);
      offset.animateTo(0f, XgramExpressive.SPATIAL_FAST);
    }
    float top;
    if (dragging) {
      top = dragTop;
    } else {
      top = clamp(thumbTop + offset.getValue(), trackTop, trackBottom - thumbLen);
    }
    float pressF = press.getValue();
    boolean rtl = Lang.rtl();
    int width = rv.getWidth();
    float len = dragging ? dragLen : thumbLen;
    layoutThumb(thumbRect, top, top + len, pressF, rtl, width);
    drawThumb(c, thumbRect, clamp(pressF, 0f, 1f), Math.min(1f, a));

    float la = labelAlpha.getValue();
    if (la > 0f && label != null) {
      float cy = labelCenterY(thumbRect, dragging ? dragTrackTop : trackTop, dragging ? dragTrackBottom : trackBottom);
      float scale = XgramMotion.reduce() ? 1f : labelScale.getValue();
      drawLabel(c, labelAnchorX(thumbRect, rtl), cy, label, Math.min(1f, la) * Math.min(1f, a), scale, labelPillWidth.getValue(), rtl);
    }

    drawn = true;
    thumbLen = len;
    if (!dragging) {
      thumbTop = top;
    }
    updateExclusion(drawnDraggable && alpha.getTarget() > 0f, rtl, width);
  }

  /** Track, thumb length and the thumb position the list reports. False when there is nothing to show. */
  private boolean layoutGeometry () {
    int height = rv.getHeight();
    int range = rv.computeVerticalScrollRange();
    int extent = rv.computeVerticalScrollExtent();
    if (height <= 0 || extent <= 0 || range <= extent) {
      return false;
    }
    int topSpace = topInset >= 0 ? topInset : autoTopInset;
    float tTop = rv.getPaddingTop() + Math.max(Screen.dp(TRACK_INSET_DP), topSpace);
    float tBottom = height - rv.getPaddingBottom() - Screen.dp(TRACK_INSET_DP);
    float trackLen = tBottom - tTop;
    boolean draggable = dragging || (dragAllowed && mode == MODE_DRAGGABLE && range >= extent * 3);
    float minLen = Screen.dp(draggable ? MIN_DRAG_DP : MIN_VISUAL_DP);
    if (trackLen < minLen * 1.5f) {
      return false;
    }
    float len;
    if (fixedLength > 0) {
      len = Math.min(fixedLength, trackLen * MAX_FRACTION);
    } else {
      len = clamp((float) extent / (float) range * trackLen, minLen, trackLen * MAX_FRACTION);
    }
    float fraction = clamp((float) rv.computeVerticalScrollOffset() / (float) (range - extent), 0f, 1f);
    trackTop = tTop;
    trackBottom = tBottom;
    thumbTop = tTop + fraction * (trackLen - len);
    if (!dragging) {
      thumbLen = len;
    }
    drawnDraggable = draggable;
    return true;
  }

  // Touch (draggable only)

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView recyclerView, @NonNull MotionEvent e) {
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      if (mode == MODE_DRAGGABLE && drawn && drawnDraggable && alpha.getValue() >= .5f && allowed() && hitTest(e.getX(), e.getY())) {
        startDrag(e.getY());
        return true;
      }
      return false;
    }
    return dragging;
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView recyclerView, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE:
        if (dragging) {
          moveDrag(e.getY());
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        endDrag();
        break;
    }
  }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) { }

  private boolean hitTest (float x, float y) {
    return hitTest(x, y, thumbTop, thumbTop + thumbLen, Lang.rtl(), rv.getWidth());
  }

  private void startDrag (float y) {
    dragging = true;
    releasePending = false;
    dragTop = thumbTop;
    dragLen = thumbLen;
    dragTrackTop = trackTop;
    dragTrackBottom = trackBottom;
    grabOffset = y - thumbTop;
    offset.setValue(0f);
    RecyclerView.Adapter<?> adapter = rv.getAdapter();
    int count = adapter != null ? adapter.getItemCount() : 0;
    int range = rv.computeVerticalScrollRange(), extent = rv.computeVerticalScrollExtent();
    int visible = range > 0 ? Math.round(count * (float) extent / (float) range) : count;
    dragSpan = Math.max(0, count - Math.max(1, visible));
    lastPos = RecyclerView.NO_POSITION;
    rv.stopScroll();
    ViewParent parent = rv.getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(true);
    }
    rv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    rv.removeCallbacks(hideRunnable);
    alpha.animateTo(1f, XgramExpressive.EFFECTS_FAST);
    press.animateTo(1f, XgramExpressive.SPATIAL_FAST);
    int pos = positionFor(dragTop);
    if (pos != RecyclerView.NO_POSITION) {
      lastPos = pos;
      updateLabel(pos, false);
    }
    rv.invalidate();
  }

  private void moveDrag (float y) {
    float top = clamp(y - grabOffset, dragTrackTop, dragTrackBottom - dragLen);
    if (top == dragTop) {
      return;
    }
    dragTop = top;
    int pos = positionFor(top);
    if (pos != RecyclerView.NO_POSITION && pos != lastPos) {
      lastPos = pos;
      RecyclerView.LayoutManager lm = rv.getLayoutManager();
      if (lm instanceof LinearLayoutManager) {
        ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
      } else {
        rv.scrollToPosition(pos);
      }
      updateLabel(pos, true);
    }
    rv.invalidate();
  }

  private void endDrag () {
    if (!dragging) {
      return;
    }
    dragging = false;
    releasePending = true;
    press.animateTo(0f, XgramExpressive.SPATIAL_FAST);
    labelAlpha.animateTo(0f, XgramExpressive.EFFECTS_FAST);
    labelScale.animateTo(.8f, XgramExpressive.EFFECTS_FAST);
    scheduleHide();
    rv.invalidate();
  }

  /** The item to bring to the top (or to the bottom of a reversed list) for a thumb at {@code top}. */
  private int positionFor (float top) {
    float maxOffset = dragTrackBottom - dragTrackTop - dragLen;
    float fraction = maxOffset > 0f ? clamp((top - dragTrackTop) / maxOffset, 0f, 1f) : 0f;
    RecyclerView.LayoutManager lm = rv.getLayoutManager();
    if (lm instanceof LinearLayoutManager && ((LinearLayoutManager) lm).getReverseLayout()) {
      fraction = 1f - fraction;
    }
    RecyclerView.Adapter<?> adapter = rv.getAdapter();
    if (adapter == null || adapter.getItemCount() == 0) {
      return RecyclerView.NO_POSITION;
    }
    return Math.min(adapter.getItemCount() - 1, Math.round(fraction * dragSpan));
  }

  private void updateLabel (int pos, boolean feedback) {
    if (labelProvider == null) {
      return;
    }
    CharSequence text = labelProvider.labelFor(pos);
    if (text == null || text.length() == 0) {
      labelAlpha.animateTo(0f, XgramExpressive.EFFECTS_FAST);
      labelScale.animateTo(.8f, XgramExpressive.EFFECTS_FAST);
      return;
    }
    boolean visible = labelAlpha.getTarget() > 0f && labelAlpha.getValue() > 0f;
    if (!TextUtils.equals(text, label)) {
      label = text;
      float w = labelWidth(text);
      if (visible) {
        labelPillWidth.animateTo(w, XgramExpressive.SPATIAL_FAST);
        if (feedback) {
          labelTick(rv);
        }
      } else {
        labelPillWidth.setValue(w);
      }
    }
    if (labelAlpha.getTarget() < 1f) {
      if (labelAlpha.getValue() <= 0f) {
        labelScale.setValue(.6f);
      }
      labelAlpha.animateTo(1f, XgramExpressive.EFFECTS_FAST);
      labelScale.animateTo(1f, XgramExpressive.SPATIAL_FAST);
    }
  }

  // System back gesture: the thumb sits on the edge the back swipe starts from

  private void updateExclusion (boolean active, boolean rtl, int width) {
    if (!active) {
      clearExclusion();
      return;
    }
    if (dragging) {
      return; // the gesture already started inside the thumb
    }
    exclusion.update(rv, thumbRect, rtl, width, scrollState != RecyclerView.SCROLL_STATE_IDLE);
  }

  private void clearExclusion () {
    exclusion.clear(rv);
  }

  /**
   * Keeps the system back gesture off a thumb on the screen edge (the Fold's gesture edge covers it otherwise): the
   * 48dp wide hit area from 12dp above to 12dp below the thumb. Reuses one list and one rect; skips moves under 1dp
   * and, while the list is moving, refreshes a few times a second (exactly once it rests).
   */
  public static final class Exclusion {
    private final ArrayList<Rect> rects = new ArrayList<>(1);
    private final Rect rect = new Rect();
    private boolean set;
    private long time;

    public void update (@NonNull View view, @NonNull RectF thumb, boolean rtl, int width, boolean moving) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return;
      }
      int hit = Screen.dp(HIT_WIDTH_DP), slop = Screen.dp(HIT_SLOP_DP);
      int l = rtl ? 0 : width - hit, r = rtl ? hit : width;
      int t = (int) (thumb.top - slop), b = (int) (thumb.bottom + slop);
      if (set && rect.left == l && rect.right == r && Math.abs(rect.top - t) <= Screen.dp(1f) && Math.abs(rect.bottom - b) <= Screen.dp(1f)) {
        return;
      }
      long now = SystemClock.uptimeMillis();
      if (set && moving && now - time < EXCLUSION_THROTTLE_MS) {
        return;
      }
      rect.set(l, t, r, b);
      rects.clear();
      rects.add(rect);
      view.setSystemGestureExclusionRects(rects);
      set = true;
      time = now;
    }

    public void clear (@NonNull View view) {
      if (set && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        set = false;
        rects.clear();
        view.setSystemGestureExclusionRects(rects);
      }
    }
  }

  // Shared painters (SectionedRecyclerView draws with these too)

  private static final Paint THUMB_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final Paint LABEL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final RectF LABEL_RECT = new RectF();
  private static @Nullable TextPaint largeText, smallText;
  private static float largeTextOffset, smallTextOffset;
  private static @Nullable CharSequence measuredText;
  private static float measuredWidth;

  /** The tick of a label change under the finger. */
  public static void labelTick (@NonNull View v) {
    v.performHapticFeedback(Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_FREQUENT_TICK : HapticFeedbackConstants.CLOCK_TICK);
  }

  /** The thumb from {@code top} to {@code bottom} at the end edge: 4dp off it, 4dp thick, 10dp while pressed. */
  public static void layoutThumb (@NonNull RectF out, float top, float bottom, float pressF, boolean rtl, int width) {
    float thickness = Math.max(Screen.dp(2f), lerp(Screen.dp(THICKNESS_DP), Screen.dp(PRESSED_THICKNESS_DP), pressF));
    float edge = Screen.dp(EDGE_DP);
    if (rtl) {
      out.set(edge, top, edge + thickness, bottom);
    } else {
      out.set(width - edge - thickness, top, width - edge, bottom);
    }
  }

  /** The end edge of a label pill beside {@code thumb}: 12dp before the thumb's start edge. */
  public static float labelAnchorX (@NonNull RectF thumb, boolean rtl) {
    float gap = Screen.dp(LABEL_GAP_DP);
    return rtl ? thumb.right + gap : thumb.left - gap;
  }

  /** The label pill centered on {@code thumb}, kept inside the track. */
  public static float labelCenterY (@NonNull RectF thumb, float trackTop, float trackBottom) {
    float half = Screen.dp(LABEL_HEIGHT_DP) / 2f;
    return trackBottom - trackTop > half * 2f ? clamp(thumb.centerY(), trackTop + half, trackBottom - half) : thumb.centerY();
  }

  /** Whether {@code (x, y)} grabs a thumb at {@code thumbTop..thumbBottom}: 48dp wide at the edge, 12dp above and below. */
  public static boolean hitTest (float x, float y, float thumbTop, float thumbBottom, boolean rtl, int width) {
    float hit = Screen.dp(HIT_WIDTH_DP), slop = Screen.dp(HIT_SLOP_DP);
    boolean inX = rtl ? x <= hit : x >= width - hit;
    return inX && y >= thumbTop - slop && y <= thumbBottom + slop;
  }

  /** The thumb in {@code r}: onSurfaceVariant at 70% at rest, primary while pressed, times {@code alpha}. */
  public static void drawThumb (@NonNull Canvas c, @NonNull RectF r, float pressF, float alpha) {
    if (alpha <= 0f || r.width() <= 0f || r.height() <= 0f) {
      return;
    }
    int idle = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), IDLE_OPACITY);
    int color = pressF <= 0f ? idle : ColorUtils.fromToArgb(idle, XgramExpressive.color(XgramExpressive.PRIMARY), Math.min(1f, pressF));
    THUMB_PAINT.setColor(XgramExpressive.withAlpha(color, alpha));
    float radius = Math.min(r.width(), r.height()) / 2f;
    c.drawRoundRect(r, radius, radius, THUMB_PAINT);
  }

  /** The label pill with its end edge at {@code anchorX}; {@code f} is the enter factor (alpha, and scale from .6). */
  public static void drawLabel (@NonNull Canvas c, float anchorX, float centerY, @Nullable CharSequence text, float f) {
    if (text == null || f <= 0f) {
      return;
    }
    float alpha = Math.min(1f, f);
    float scale = XgramMotion.reduce() ? 1f : lerp(.6f, 1f, alpha);
    drawLabel(c, anchorX, centerY, text, alpha, scale, labelWidth(text), Lang.rtl());
  }

  /** Width of the label pill for {@code text}: the text plus 16dp on both sides, at least 48dp. Measured on change only. */
  public static float labelWidth (@NonNull CharSequence text) {
    float textWidth = measureLabel(text);
    return Math.max(Screen.dp(LABEL_HEIGHT_DP), textWidth + Screen.dp(LABEL_PADDING_DP) * 2f);
  }

  public static void drawLabel (@NonNull Canvas c, float anchorX, float centerY, @NonNull CharSequence text, float alpha, float scale, float width, boolean rtl) {
    if (alpha <= 0f || text.length() == 0) {
      return;
    }
    float height = Screen.dp(LABEL_HEIGHT_DP);
    float w = Math.max(height, width);
    float left = rtl ? anchorX : anchorX - w;
    LABEL_RECT.set(left, centerY - height / 2f, left + w, centerY + height / 2f);
    int save = -1;
    if (scale != 1f) {
      save = c.save();
      c.scale(scale, scale, rtl ? LABEL_RECT.left : LABEL_RECT.right, centerY);
    }
    LABEL_PAINT.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), alpha));
    c.drawRoundRect(LABEL_RECT, height / 2f, height / 2f, LABEL_PAINT);
    float textWidth = measureLabel(text);
    TextPaint paint = isSingleGlyph(text) ? largeText : smallText;
    if (paint != null) {
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), alpha));
      float baseline = centerY + (paint == largeText ? largeTextOffset : smallTextOffset);
      c.drawText(text, 0, text.length(), LABEL_RECT.centerX() - textWidth / 2f, baseline, paint);
    }
    if (save != -1) {
      c.restoreToCount(save);
    }
  }

  private static boolean isSingleGlyph (CharSequence text) {
    return Character.codePointCount(text, 0, text.length()) == 1;
  }

  private static float measureLabel (CharSequence text) {
    ensureTextPaints();
    if (!TextUtils.equals(text, measuredText)) {
      TextPaint paint = isSingleGlyph(text) ? largeText : smallText;
      measuredWidth = paint != null ? paint.measureText(text, 0, text.length()) : 0f;
      measuredText = text instanceof String ? text : text.toString();
    }
    return measuredWidth;
  }

  private static void ensureTextPaints () {
    if (largeText == null) {
      largeText = newTextPaint(XgramExpressive.TS_TITLE_L);
      Paint.FontMetrics fm = largeText.getFontMetrics();
      largeTextOffset = -(fm.ascent + fm.descent) / 2f;
      smallText = newTextPaint(XgramExpressive.TS_TITLE_S);
      fm = smallText.getFontMetrics();
      smallTextOffset = -(fm.ascent + fm.descent) / 2f;
    }
  }

  private static TextPaint newTextPaint (int ts) {
    TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    p.setTextSize(Screen.dp(XgramExpressive.textSizeSp(ts)));
    p.setTypeface(XgramExpressive.typeface(ts, false));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      p.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
    }
    return p;
  }

  private static float clamp (float v, float min, float max) {
    return v < min ? min : Math.min(v, max);
  }

  private static float lerp (float from, float to, float f) {
    return from + (to - from) * f;
  }

  /**
   * A spring-driven float whose motion token can change per target (show fast, hide slow) and keeps its velocity
   * when retargeted. Spatial tokens become the standard ones with Reduce motion; effects (fades) keep running.
   */
  public static final class Anim implements DynamicAnimation.OnAnimationUpdateListener {
    private final FloatValueHolder holder;
    private final SpringAnimation spring;
    private final Runnable onUpdate;
    private int token = -1;
    private float target;

    public Anim (float initialValue, float minVisibleChange, @NonNull Runnable onUpdate) {
      this.onUpdate = onUpdate;
      this.target = initialValue;
      this.holder = new FloatValueHolder(initialValue);
      this.spring = new SpringAnimation(holder);
      this.spring.setSpring(new SpringForce(initialValue));
      this.spring.setMinimumVisibleChange(Math.max(minVisibleChange, 1e-5f));
      this.spring.addUpdateListener(this);
    }

    public void animateTo (float target, int token) {
      this.target = target;
      token = XgramMotion.token(token);
      if (this.token != token) {
        this.token = token;
        spring.getSpring().setStiffness(XgramExpressive.stiffness(token)).setDampingRatio(XgramExpressive.damping(token));
      }
      if (!spring.isRunning() && holder.getValue() == target) {
        return;
      }
      spring.animateToFinalPosition(target);
    }

    /** Snaps without a callback. */
    public void setValue (float value) {
      if (spring.isRunning()) {
        spring.cancel();
      }
      holder.setValue(value);
      target = value;
    }

    public float getValue () {
      return holder.getValue();
    }

    public float getTarget () {
      return target;
    }

    public boolean isRunning () {
      return spring.isRunning();
    }

    @Override
    public void onAnimationUpdate (DynamicAnimation animation, float value, float velocity) {
      onUpdate.run();
    }
  }
}
