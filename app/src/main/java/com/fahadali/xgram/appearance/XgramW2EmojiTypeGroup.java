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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E: the Emoji / Stickers / GIFs switch of the emoji panel, a Material 3 Expressive single-select connected
 * button group (as XGram's Compose settings ButtonsRow) in a full-width row right under the section tabs.
 * <ul>
 *   <li>Row: 48dp, filled with the panel's own surfaceContainerLow, no elevation. The group is the row's width less
 *   12dp each side (412dp at most), centred: three 40dp segments 2dp apart. The grids start the row's height lower
 *   ({@link #CONTENT_CLEARANCE_DP}).</li>
 *   <li>Segments: surfaceContainerHighest, the group's two outer sides full, the inner corners 8dp. Content in
 *   onSurfaceVariant: a 20dp outlined icon and the label (labelLarge), or the label only, or the icon only, one look
 *   for all three, whatever fits the narrowest.</li>
 *   <li>Selection: a primary pill (full corners) with the filled icon and the label in onPrimary. It slides on the
 *   standard spatial default spring, one way only (a reversal starts from rest). Whatever the pill covers is drawn in
 *   onPrimary (the content is masked by the pill's shape, so it always reads), and only the two segments of a change
 *   go from outlined to filled and back, with the pill's progress (XgramIconCrossfade). While a finger drags the
 *   panel's pager, the pill follows the pages instead ({@link #followPages}).</li>
 *   <li>Press: the pressed segment's inner corners (the pill's, when it is the selected one) spring to 4dp under the
 *   10% pressed state layer; 8% on hover.</li>
 *   <li>Accessibility: three radio buttons in one single-select collection (ExploreByTouchHelper), keyboard too.</li>
 * </ul>
 * It follows the panel header as that slides up ({@link #setFollowY}), and hides on scroll down and comes back on
 * scroll up ({@link #setHidden}, spatial default spring): it slides up behind the section tabs and fades (never while
 * TalkBack explores by touch). Nothing is allocated while drawing: the icon paints are rebuilt only when the theme
 * colors change.
 */
@SuppressLint("ViewConstructor")
public final class XgramW2EmojiTypeGroup extends View {
  public static final int TYPE_EMOJI = 0, TYPE_STICKERS = 1, TYPE_GIFS = 2;
  private static final int COUNT = 3;

  public interface Callback {
    void onEmojiTypeSelected (int type);
    /** A long press where the segments show no labels: {@code bounds} is the segment's touch column, in this view. */
    void onEmojiTypeLongPressed (int type, @NonNull Rect bounds);
  }

  /** The row's height, and how much further down the grids start under it: the row exactly. */
  public static final float ROW_HEIGHT_DP = 48f, CONTENT_CLEARANCE_DP = ROW_HEIGHT_DP;
  private static final float ROW_SEGMENT_HEIGHT_DP = 40f, ROW_SIDE_MARGIN_DP = 12f, ROW_MAX_GROUP_DP = 412f;
  private static final float CONTENT_PAD_DP = 12f, ICON_DP = 20f, ICON_GAP_DP = 8f;
  private static final int MODE_ICON_LABEL = 0, MODE_LABEL = 1, MODE_ICON = 2;
  /** A same-direction retarget keeps the pill's speed up to this fraction of omega x distance (no overshoot). */
  private static final float ENTRY_SPEED_CAP = .5f;
  /** Coverage of a segment by the pill past which it counts as covered (not drawn) or uncovered. */
  private static final float COVERED = .999f, UNCOVERED = .001f;

  private static final int[] ICONS = {R.drawable.baseline_emoticon_outline_24, R.drawable.deproko_baseline_stickers_24, R.drawable.deproko_baseline_gif_24};
  private static final int[] ICONS_CHECKED = {R.drawable.baseline_emoticon_24, R.drawable.deproko_baseline_stickers_24, R.drawable.deproko_baseline_gif_filled_24};
  private static final int[] LABELS = {R.string.XgramW2EmojiTypeEmoji, R.string.Stickers, R.string.GIFs};

  /** One drawable per state, also where both states use the same resource (see XgramIconCrossfade). */
  private final Drawable[] icons = new Drawable[COUNT], checkedIcons = new Drawable[COUNT];
  private final String[] labels = new String[COUNT];
  private final float[] labelWidths = new float[COUNT];

  /**
   * The selection pill's position in segment indexes (0 .. COUNT - 1). Everything else that moves with a selection
   * (the icons' outlined to filled crossfade, the pill's press shape) is derived from it, so they cannot disagree.
   */
  private final PillSpring indicator;
  private final XgramExpressive.Spring[] pressSprings = new XgramExpressive.Spring[COUNT];
  /** 0 shown, 1 hidden. */
  private final XgramExpressive.Spring hideSpring;

  /** The current trip: where the pill started, where it goes, and each segment's filled state when it started. */
  private float travelFrom;
  private int travelTarget;
  private final float[] checkFrom = new float[COUNT];
  private float lastValue;
  /** Following a pager drag: the page factor (0 emoji .. 1 media) and the media page's type. */
  private boolean following;
  private float followFactor;
  private int followType = TYPE_STICKERS;

  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
  private @Nullable Paint restIconPaint, pillIconPaint;
  private int restIconColor, pillIconColor;
  private final Path[] segmentPaths = new Path[COUNT];
  private final Path pillPath = new Path();
  private final float[] radii = new float[8];
  private final RectF rect = new RectF();
  private final float[] coverage = new float[COUNT], fill = new float[COUNT];

  // Geometry in px (layoutGeometry)
  private float groupLeft, segmentWidth, segmentHeight, segmentTop, pitch, full, gap, baseline;
  private int mode = MODE_ICON_LABEL;
  /** Icon centre and label start from the segment's centre (the icon on the leading side). */
  private final float[] iconDx = new float[COUNT], labelDx = new float[COUNT];

  private int selectedType = TYPE_EMOJI;
  private int pressedIndex = -1, hoverIndex = -1;
  private float downX, downY;
  private boolean hidden;
  private float hideFactor, followY;
  private @Nullable Callback callback;
  private final @Nullable AccessibilityManager accessibilityManager;
  private final A11y a11y;

  public XgramW2EmojiTypeGroup (@NonNull Context context) {
    super(context);
    for (int i = 0; i < COUNT; i++) {
      icons[i] = Drawables.get(getResources(), ICONS[i]);
      checkedIcons[i] = Drawables.get(getResources(), ICONS_CHECKED[i]);
      pressSprings[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      segmentPaths[i] = new Path();
      checkFrom[i] = i == selectedType ? 1f : 0f;
    }
    indicator = new PillSpring(XgramExpressive.STD_SPATIAL_DEFAULT, selectedType, this::onIndicatorFrame);
    travelFrom = lastValue = selectedType;
    travelTarget = selectedType;
    hideSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyHide(value));
    hideSpring.setThreshold(.002f);
    // labelLarge, as the navigation rail's labels
    final int ts = XgramExpressive.TS_LABEL_L;
    textPaint.setTypeface(XgramExpressive.typeface(ts, false));
    textPaint.setTextSize(Screen.dpf(XgramExpressive.textSizeSp(ts)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      textPaint.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
    }
    loadLabels();
    accessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    a11y = new A11y(this);
    ViewCompat.setAccessibilityDelegate(this, a11y);
    setFocusable(true);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  public int getSelectedType () {
    return selectedType;
  }

  private void loadLabels () {
    for (int i = 0; i < COUNT; i++) {
      labels[i] = Lang.getString(LABELS[i]);
      labelWidths[i] = textPaint.measureText(labels[i]);
    }
  }

  /** The labels or the direction changed. */
  public void onLanguageChanged () {
    loadLabels();
    layoutGeometry();
    a11y.invalidateRoot();
    invalidate();
  }

  // Selection

  /**
   * Sends the pill to {@code type} (EmojiLayout owns when: a tap, the content it follows, or a reset); this also ends
   * following a drag. Nothing when it is already there or on its way there.
   */
  public void setSelectedType (int type, boolean animated) {
    if (type < 0 || type >= COUNT) {
      return;
    }
    if (!following && type == selectedType && indicator.getTarget() == type && (animated || (!indicator.isRunning() && indicator.getValue() == type))) {
      return;
    }
    captureChecks();
    following = false;
    travel(type, animated);
  }

  private void travel (int type, boolean animated) {
    final int old = selectedType;
    selectedType = type;
    final float from = indicator.getValue();
    travelFrom = from;
    travelTarget = type;
    if (!animated || getWidth() == 0 || Math.abs(from - type) < .001f) {
      indicator.setValue(type);
      travelFrom = type;
      for (int i = 0; i < COUNT; i++) {
        checkFrom[i] = i == type ? 1f : 0f;
      }
    } else {
      // A reversal (or a start from rest) begins at rest, so the pill only ever travels one way; in the same
      // direction it keeps its speed, capped so that it cannot overshoot
      final float d = type - from, v = indicator.getVelocity();
      final float omega = (float) Math.sqrt(XgramExpressive.stiffness(XgramExpressive.STD_SPATIAL_DEFAULT));
      final float v0 = v * d > 0f ? Math.signum(d) * Math.min(Math.abs(v), ENTRY_SPEED_CAP * omega * Math.abs(d)) : 0f;
      indicator.animateTo(type, v0);
    }
    lastValue = indicator.getValue();
    if (old != type) {
      onSelectionChanged(old);
    }
    xgramLog("pill -> " + type + (animated ? "" : " (snap)") + " from " + from);
    invalidate();
  }

  private void onSelectionChanged (int oldType) {
    a11y.invalidateVirtualView(oldType);
    a11y.invalidateVirtualView(selectedType);
  }

  /**
   * While a finger drags the panel's pager: the pill sits at {@code pageFactor} (0 Emoji .. 1 the media page) of the
   * way to the media page's segment ({@code mediaType}), catching up on its spring if a trip was still running.
   */
  public void followPages (float pageFactor, int mediaType) {
    following = true;
    followFactor = MathUtils.clamp(pageFactor);
    followType = mediaType == TYPE_GIFS ? TYPE_GIFS : TYPE_STICKERS;
    final int shown = followFactor >= .5f ? followType : TYPE_EMOJI;
    if (shown != selectedType) {
      final int old = selectedType;
      selectedType = shown;
      onSelectionChanged(old);
    }
    final float position = followFactor * followType;
    if (indicator.isRunning()) {
      indicator.animateTo(position);
    } else {
      indicator.setValue(position);
    }
    invalidate();
  }

  /** The drag is over and the pager rests on {@code type}: the pill settles there from where it is. */
  public void endFollow (int type) {
    if (!following) {
      setSelectedType(type, true); // a drag that never moved the pages
      return;
    }
    captureChecks();
    following = false;
    travel(type, true);
  }

  private void captureChecks () {
    for (int i = 0; i < COUNT; i++) {
      checkFrom[i] = checkOf(i);
    }
  }

  /**
   * Segment {@code i}'s filled state (0 outlined, 1 filled), from the pill's progress on its current trip: only the
   * segments the trip leaves and reaches change shape, and only as the pill moves.
   */
  private float checkOf (int i) {
    if (following) {
      return i == TYPE_EMOJI ? 1f - followFactor : (i == followType ? followFactor : 0f);
    }
    final float span = travelTarget - travelFrom;
    final float p = Math.abs(span) < 1e-4f ? 1f : MathUtils.clamp((indicator.getValue() - travelFrom) / span);
    return checkFrom[i] + ((i == travelTarget ? 1f : 0f) - checkFrom[i]) * p;
  }

  private void onIndicatorFrame (float v) {
    if (BuildConfig.DEBUG && !following) {
      final float dir = Math.signum(travelTarget - travelFrom);
      if (dir != 0f && (v - lastValue) * dir < -.02f && Math.abs(travelTarget - v) > .05f) {
        Log.w(LOG_TAG, "backwards " + lastValue + " -> " + v + " target " + travelTarget);
      }
    }
    lastValue = v;
    invalidate();
  }

  private static final String LOG_TAG = "XgramSwitch";

  private static void xgramLog (String s) {
    if (BuildConfig.DEBUG) {
      Log.d(LOG_TAG, s);
    }
  }

  // Placement

  /** The panel header's translation: the switch sits under the section tabs, so it moves up with them. */
  public void setFollowY (float y) {
    if (this.followY != y) {
      this.followY = y;
      updatePlacement();
    }
  }

  private boolean isTouchExploring () {
    return accessibilityManager != null && accessibilityManager.isEnabled() && accessibilityManager.isTouchExplorationEnabled();
  }

  /** Hides (on scroll down) or shows (on scroll up) the switch, on the spatial default spring; never hides for TalkBack. */
  public void setHidden (boolean hidden, boolean animated) {
    if (hidden && isTouchExploring()) {
      return;
    }
    final float target = hidden ? 1f : 0f;
    if (this.hidden == hidden && (hideSpring.getTarget() == target) && (animated || hideSpring.getValue() == target)) {
      return;
    }
    final boolean changed = this.hidden != hidden;
    this.hidden = hidden;
    if (hidden) {
      releasePress();
    }
    if (animated && getWidth() > 0 && getVisibility() == View.VISIBLE) {
      hideSpring.animateTo(target);
    } else {
      hideSpring.setValue(target);
      applyHide(target);
    }
    if (changed) {
      a11y.invalidateRoot(); // no segments for TalkBack while hidden
    }
  }

  public boolean isHidden () {
    return hidden;
  }

  /** 0 shown, 1 hidden. */
  public float getHideFraction () {
    return MathUtils.clamp(hideFactor);
  }

  /** The bottom of the switch as drawn, in the parent's coordinates; 0 while it is not shown. */
  public int visibleBottom () {
    if (getVisibility() != View.VISIBLE || getAlpha() == 0f) {
      return 0;
    }
    return Math.round(getTop() + getTranslationY() + getHeight());
  }

  private void applyHide (float value) {
    if (this.hideFactor != value) {
      this.hideFactor = value;
      updatePlacement();
    }
  }

  /** Properties only, no layout: it slides up behind the section tabs by its own height and fades. */
  private void updatePlacement () {
    final float hide = MathUtils.clamp(hideFactor, -.2f, 1f); // a little past its place on the spring's way back
    setAlpha(1f - MathUtils.clamp(hideFactor));
    setTranslationY(followY - Screen.dp(ROW_HEIGHT_DP) * hide);
  }

  // Layout

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    // EmojiLayout gives exact sizes (the row: its width x 48dp)
    setMeasuredDimension(getDefaultSize(0, widthMeasureSpec), getDefaultSize(0, heightMeasureSpec));
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    layoutGeometry();
  }

  /** The group, the segments and one content look for all three (icon and label, label, or icon), measured. */
  private void layoutGeometry () {
    final int width = getWidth(), height = getHeight();
    gap = Screen.dpf(XgramM3Connected.GAP);
    final float group = Math.min(width - 2f * Screen.dpf(ROW_SIDE_MARGIN_DP), Screen.dpf(ROW_MAX_GROUP_DP));
    segmentWidth = Math.max(0f, (group - gap * (COUNT - 1)) / COUNT);
    segmentHeight = Math.min(height, Screen.dpf(ROW_SEGMENT_HEIGHT_DP));
    groupLeft = (width - (segmentWidth * COUNT + gap * (COUNT - 1))) / 2f;
    segmentTop = (height - segmentHeight) / 2f;
    pitch = segmentWidth + gap;
    full = segmentHeight / 2f;

    final float pad = Screen.dpf(CONTENT_PAD_DP), icon = Screen.dpf(ICON_DP), iconGap = Screen.dpf(ICON_GAP_DP);
    boolean iconAndLabel = true, labelOnly = true;
    for (int i = 0; i < COUNT; i++) {
      if (pad + icon + iconGap + labelWidths[i] + pad > segmentWidth) {
        iconAndLabel = false;
      }
      if (pad + labelWidths[i] + pad > segmentWidth) {
        labelOnly = false;
      }
    }
    mode = iconAndLabel ? MODE_ICON_LABEL : labelOnly ? MODE_LABEL : MODE_ICON;
    final boolean rtl = Lang.rtl();
    for (int i = 0; i < COUNT; i++) {
      switch (mode) {
        case MODE_ICON_LABEL: {
          // Centred as a unit, the icon on the leading side
          final float unit = icon + iconGap + labelWidths[i];
          if (rtl) {
            iconDx[i] = unit / 2f - icon / 2f;
            labelDx[i] = -unit / 2f;
          } else {
            iconDx[i] = -unit / 2f + icon / 2f;
            labelDx[i] = -unit / 2f + icon + iconGap;
          }
          break;
        }
        case MODE_LABEL: {
          iconDx[i] = 0f;
          labelDx[i] = -labelWidths[i] / 2f;
          break;
        }
        default: {
          iconDx[i] = 0f;
          labelDx[i] = 0f;
          break;
        }
      }
    }
    textPaint.getFontMetrics(fontMetrics);
    baseline = segmentTop + segmentHeight / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f;
  }

  /** The slot (left to right) of a (fractional) segment index, mirrored under RTL. */
  private static float slotOf (float position, boolean rtl) {
    return rtl ? (COUNT - 1) - position : position;
  }

  private float segmentLeft (int i, boolean rtl) {
    return groupLeft + slotOf(i, rtl) * pitch;
  }

  /** Segment {@code type}'s touch column: split at the gaps' centres, the outer ones running to the view's edges. */
  private void columnBounds (int type, @NonNull Rect out) {
    final int slot = Math.round(slotOf(type, Lang.rtl()));
    final float left = slot == 0 ? 0f : groupLeft + slot * pitch - gap / 2f;
    final float right = slot == COUNT - 1 ? getWidth() : groupLeft + slot * pitch + segmentWidth + gap / 2f;
    out.set(Math.round(left), 0, Math.round(right), getHeight());
  }

  private int findIndex (float x, float y) {
    final float slop = Screen.dpf(4f);
    if (segmentWidth <= 0f || x < -slop || x > getWidth() + slop || y < -slop || y > getHeight() + slop) {
      return -1;
    }
    final float b1 = groupLeft + segmentWidth + gap / 2f, b2 = b1 + pitch;
    final int slot = x < b1 ? 0 : x < b2 ? 1 : 2;
    return Lang.rtl() ? (COUNT - 1) - slot : slot;
  }

  private boolean isInteractive () {
    return getVisibility() == View.VISIBLE && isEnabled() && getAlpha() >= .5f && getHideFraction() <= .5f && segmentWidth > 0f;
  }

  // Touch

  private void releasePress () {
    if (pressedIndex != -1) {
      pressSprings[pressedIndex].animateTo(0f);
      pressedIndex = -1;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    final int action = e.getActionMasked();
    if (!isInteractive()) {
      releasePress();
      return action != MotionEvent.ACTION_DOWN;
    }
    final float x = e.getX(), y = e.getY();
    switch (action) {
      case MotionEvent.ACTION_DOWN: {
        releasePress();
        final int index = findIndex(x, y);
        if (index == -1) {
          return false;
        }
        downX = x;
        downY = y;
        pressedIndex = index;
        pressSprings[index].animateTo(1f);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (pressedIndex != -1 && Math.max(Math.abs(x - downX), Math.abs(y - downY)) > Screen.getTouchSlop() && findIndex(x, y) != pressedIndex) {
          releasePress();
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        releasePress();
        return true;
      }
      case MotionEvent.ACTION_UP: {
        final int index = pressedIndex;
        releasePress();
        if (index != -1 && findIndex(x, y) == index) {
          ViewUtils.onClick(this);
          if (callback != null) {
            callback.onEmojiTypeSelected(index);
          }
        }
        return true;
      }
    }
    return true;
  }

  @Override
  public boolean onHoverEvent (MotionEvent e) {
    final int action = e.getActionMasked();
    final int index = (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) && isInteractive() ? findIndex(e.getX(), e.getY()) : -1;
    if (hoverIndex != index) {
      hoverIndex = index;
      invalidate();
    }
    return true;
  }

  @Override
  public boolean dispatchHoverEvent (MotionEvent e) {
    return a11y.dispatchHoverEvent(e) || super.dispatchHoverEvent(e);
  }

  @Override
  public boolean dispatchKeyEvent (KeyEvent e) {
    return a11y.dispatchKeyEvent(e) || super.dispatchKeyEvent(e);
  }

  @Override
  protected void onFocusChanged (boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    a11y.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    releasePress();
    hoverIndex = -1;
  }

  // Drawing

  private void setRadii (float left, float right) {
    radii[0] = radii[1] = left; // top left
    radii[2] = radii[3] = right; // top right
    radii[4] = radii[5] = right; // bottom right
    radii[6] = radii[7] = left; // bottom left
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }

    final int zoneColor = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_LOW);
    final int containerColor = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
    final int pillColor = XgramExpressive.color(XgramExpressive.PRIMARY);
    final int pillContent = XgramExpressive.color(XgramExpressive.ON_PRIMARY);
    final int restContent = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    if (restIconPaint == null || restIconColor != restContent) {
      restIconPaint = Paints.createPorterDuffPaint(restIconPaint, restIconColor = restContent);
    }
    if (pillIconPaint == null || pillIconColor != pillContent) {
      pillIconPaint = Paints.createPorterDuffPaint(pillIconPaint, pillIconColor = pillContent);
    }

    // The row: the panel's own surface, so the grids scroll under it
    fillPaint.setColor(zoneColor);
    c.drawRect(0, 0, width, height, fillPaint);
    if (segmentWidth <= 0f) {
      return;
    }

    // Where the pill is, and how much of each segment it covers
    final boolean rtl = Lang.rtl();
    final float position = MathUtils.clamp(indicator.getValue(), 0f, COUNT - 1);
    final float pillLeft = groupLeft + slotOf(position, rtl) * pitch, pillRight = pillLeft + segmentWidth;
    for (int i = 0; i < COUNT; i++) {
      final float left = segmentLeft(i, rtl);
      final float overlap = Math.max(0f, Math.min(pillRight, left + segmentWidth) - Math.max(pillLeft, left));
      coverage[i] = overlap / segmentWidth;
      final float t = MathUtils.clamp((coverage[i] - .75f) / .25f);
      fill[i] = t * t * (3f - 2f * t);
    }

    // Segment containers: the outer sides full, the inner corners 8dp, rounding up to full as the pill covers them
    // (so nothing shows around the pill at rest), 4dp while pressed
    final float innerCorner = Math.min(full, Screen.dpf(XgramM3Connected.INNER));
    final float pressedCorner = Math.min(full, Screen.dpf(XgramM3Connected.PRESSED));
    fillPaint.setColor(containerColor);
    for (int i = 0; i < COUNT; i++) {
      final int slot = Math.round(slotOf(i, rtl));
      final float left = groupLeft + slot * pitch;
      final float rest = innerCorner + (full - innerCorner) * fill[i];
      final float inner = MathUtils.clamp(rest + (pressedCorner - rest) * pressSprings[i].getValue(), 0f, full);
      setRadii(slot == 0 ? full : inner, slot == COUNT - 1 ? full : inner);
      rect.set(left, segmentTop, left + segmentWidth, segmentTop + segmentHeight);
      final Path path = segmentPaths[i];
      path.rewind();
      path.addRoundRect(rect, radii, Path.Direction.CW);
      if (coverage[i] < COVERED) {
        c.drawPath(path, fillPaint);
      }
    }

    // The pill: full corners, its inner sides squashed to 4dp while its own segment is pressed under it
    final int targetSlot = Math.round(slotOf(selectedType, rtl));
    final float squash = MathUtils.clamp(pressSprings[selectedType].getValue()) * fill[selectedType];
    final float pillInner = MathUtils.clamp(full + (pressedCorner - full) * squash, 0f, full);
    setRadii(targetSlot == 0 ? full : pillInner, targetSlot == COUNT - 1 ? full : pillInner);
    rect.set(pillLeft, segmentTop, pillRight, segmentTop + segmentHeight);
    pillPath.rewind();
    pillPath.addRoundRect(rect, radii, Path.Direction.CW);
    fillPaint.setColor(pillColor);
    c.drawPath(pillPath, fillPaint);

    // State layers: the content color under the finger, 10% pressed, 8% hovered
    for (int i = 0; i < COUNT; i++) {
      final float press = MathUtils.clamp(pressSprings[i].getValue());
      float alpha = press > .001f ? XgramExpressive.STATE_PRESSED * press : 0f;
      if (i == hoverIndex) {
        alpha = Math.max(alpha, XgramExpressive.STATE_HOVER);
      }
      if (alpha <= 0f) {
        continue;
      }
      fillPaint.setColor(XgramExpressive.withAlpha(ColorUtils.fromToArgb(restContent, pillContent, fill[i]), alpha));
      c.drawPath(i == selectedType && coverage[i] >= COVERED ? pillPath : segmentPaths[i], fillPaint);
    }

    // Content: onPrimary wherever the pill covers it, onSurfaceVariant elsewhere (masked by the pill's shape). Each
    // drawable is drawn at most twice a frame, always at full alpha, with a different color filter each time
    for (int i = 0; i < COUNT; i++) {
      if (coverage[i] <= UNCOVERED) {
        drawContent(c, i, rtl, false, restContent, pillContent);
      } else if (coverage[i] >= COVERED) {
        drawContent(c, i, rtl, true, restContent, pillContent);
      } else {
        int save = c.save();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          c.clipOutPath(pillPath);
        } else {
          c.clipPath(pillPath, Region.Op.DIFFERENCE);
        }
        drawContent(c, i, rtl, false, restContent, pillContent);
        c.restoreToCount(save);
        save = c.save();
        c.clipPath(pillPath);
        drawContent(c, i, rtl, true, restContent, pillContent);
        c.restoreToCount(save);
      }
    }
  }

  private void drawContent (Canvas c, int i, boolean rtl, boolean onPill, int restContent, int pillContent) {
    final float centerX = segmentLeft(i, rtl) + segmentWidth / 2f;
    if (mode != MODE_LABEL) {
      // 24dp vectors at 20dp, centred on whole pixels
      final Paint iconPaint = onPill ? pillIconPaint : restIconPaint;
      final float cx = Math.round(centerX + iconDx[i]), cy = Math.round(segmentTop + segmentHeight / 2f);
      final float scale = ICON_DP / 24f;
      final int save = c.save();
      c.scale(scale, scale, cx, cy);
      XgramIconCrossfade.draw(c, icons[i], iconPaint, checkedIcons[i], iconPaint, cx, cy, checkOf(i), 1f);
      c.restoreToCount(save);
    }
    if (mode != MODE_ICON) {
      textPaint.setColor(onPill ? pillContent : restContent);
      c.drawText(labels[i], centerX + labelDx[i], baseline, textPaint);
    }
  }

  // Accessibility: three radio buttons in a single-select collection

  private final class A11y extends ExploreByTouchHelper {
    private final Rect bounds = new Rect();

    A11y (@NonNull View host) {
      super(host);
    }

    @Override
    protected int getVirtualViewAt (float x, float y) {
      if (!isInteractive()) {
        return INVALID_ID;
      }
      final int index = findIndex(x, y);
      return index == -1 ? INVALID_ID : index;
    }

    @Override
    protected void getVisibleVirtualViews (List<Integer> virtualViewIds) {
      if (!isInteractive()) {
        return;
      }
      for (int i = 0; i < COUNT; i++) {
        virtualViewIds.add(i);
      }
    }

    @Override
    protected void onPopulateNodeForVirtualView (int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
      final boolean checked = virtualViewId == selectedType;
      node.setClassName("android.widget.RadioButton");
      node.setCheckable(true);
      node.setChecked(checked);
      node.setText(labels[virtualViewId]);
      columnBounds(virtualViewId, bounds);
      //noinspection deprecation
      node.setBoundsInParent(bounds);
      node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
      //noinspection deprecation
      node.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(0, 1, virtualViewId, 1, false, checked));
    }

    @Override
    protected void onPopulateNodeForHost (@NonNull AccessibilityNodeInfoCompat node) {
      //noinspection deprecation
      node.setCollectionInfo(AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(1, COUNT, false, AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE));
    }

    @Override
    protected boolean onPerformActionForVirtualView (int virtualViewId, int action, @Nullable Bundle arguments) {
      if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
        if (callback != null) {
          callback.onEmojiTypeSelected(virtualViewId);
        }
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
      }
      return false;
    }
  }

  /**
   * The pill's spring: a unit-mass androidx spring on an M3E motion token, as XgramExpressive.Spring, that also
   * reports its velocity and can start from a velocity of the caller's choice. Snaps under Reduce motion. Main thread
   * only.
   */
  private static final class PillSpring {
    interface Listener {
      void onFrame (float value);
    }

    private final FloatValueHolder holder;
    private final SpringAnimation animation;
    private final Listener listener;
    private float target, velocity;

    PillSpring (int token, float initialValue, Listener listener) {
      this.listener = listener;
      this.target = initialValue;
      this.holder = new FloatValueHolder(initialValue);
      this.animation = new SpringAnimation(holder);
      this.animation.setSpring(XgramExpressive.springForce(token, initialValue));
      this.animation.setMinimumVisibleChange(.001f);
      this.animation.addUpdateListener((a, value, v) -> {
        velocity = v;
        listener.onFrame(value);
      });
      this.animation.addEndListener((a, canceled, value, v) -> velocity = 0f);
    }

    float getValue () {
      return holder.getValue();
    }

    float getTarget () {
      return target;
    }

    boolean isRunning () {
      return animation.isRunning();
    }

    /** Units per second while running, else 0. */
    float getVelocity () {
      return animation.isRunning() ? velocity : 0f;
    }

    /** Snaps to {@code v} and cancels the animation. The listener is not called. */
    void setValue (float v) {
      if (animation.isRunning()) {
        animation.cancel();
      }
      holder.setValue(v);
      target = v;
      velocity = 0f;
    }

    /** Retargets a running spring (its velocity kept), or starts from rest. */
    void animateTo (float target) {
      if (animation.isRunning() && !Settings.instance().needReduceMotion()) {
        this.target = target;
        animation.animateToFinalPosition(target);
      } else {
        animateTo(target, 0f);
      }
    }

    /** Springs to {@code target} from {@code startVelocity} (units per second) instead of the running velocity. */
    void animateTo (float target, float startVelocity) {
      if (Settings.instance().needReduceMotion()) {
        final boolean changed = holder.getValue() != target || animation.isRunning();
        setValue(target);
        if (changed) {
          listener.onFrame(target);
        }
        return;
      }
      this.target = target;
      if (animation.isRunning()) {
        animation.cancel();
      }
      if (holder.getValue() == target && startVelocity == 0f) {
        velocity = 0f;
        return;
      }
      velocity = startVelocity;
      animation.setStartVelocity(startVelocity);
      animation.animateToFinalPosition(target);
    }
  }
}
