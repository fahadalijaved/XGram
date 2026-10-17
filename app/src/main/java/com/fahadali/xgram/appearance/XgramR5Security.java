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
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E round 5, appearance and security pages: the selectable card of the icon, theme and icon pack pickers (a
 * surfaceBright card that turns secondaryContainer when selected, CORNER_L at rest, CORNER_M pressed, CORNER_XL while
 * selected, with a check badge), the adaptive grid of those pickers, the titleSmall section header and the bodyMedium
 * description, and the Material 3 Expressive lock screen ({@link LockView}). Main thread only; only used while
 * {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramR5Security {
  private XgramR5Security () { }

  // Strings

  /**
   * {@code xgramRes} (XGram's sentence case wording) while Telegram X's {@code tgxRes} is its bundled English string,
   * so a translation is kept as it is; {@code tgxRes} otherwise, and always with Expressive design off.
   */
  @NonNull
  public static String sentence (@StringRes int tgxRes, @StringRes int xgramRes) {
    final String tgx = Lang.getString(tgxRes);
    if (!XgramExpressive.isEnabled()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return Lang.getString(xgramRes);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  // Section header and description

  public static final float GUTTER = 16f;

  /** A titleSmall section header in primary: 32dp sides (the tile content edge), 20dp above (8dp when it opens the page), 8dp below. */
  public static TextView newSectionHeader (Context context) {
    TextView title = new TextView(context);
    XgramExpressive.applyText(title, XgramExpressive.TS_TITLE_S, false);
    title.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    bindSectionHeader(title, null, false);
    return title;
  }

  /** Text, colour (read now, at bind time) and the first-header spacing of a header from {@link #newSectionHeader}. */
  public static void bindSectionHeader (TextView title, CharSequence text, boolean first) {
    if (text != null) {
      title.setText(text);
    }
    title.setTextColor(XgramExpressive.color(XgramExpressive.PRIMARY));
    title.setGravity(Lang.rtl() ? android.view.Gravity.RIGHT : android.view.Gravity.LEFT);
    // At the tile content edge (the 16dp gutter plus the tile's own 16dp), 20dp above like the Compose SettingsHeader
    title.setPadding(Screen.dp(GUTTER + 16f), Screen.dp(first ? 8f : 20f), Screen.dp(GUTTER + 16f), Screen.dp(8f));
  }

  /** A bodyMedium description in onSurfaceVariant at the tile content edge (32dp). */
  public static TextView newDescription (Context context) {
    TextView text = new TextView(context);
    XgramExpressive.applyText(text, XgramExpressive.TS_BODY_M, false);
    text.setLineSpacing(Screen.dp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_M) - XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)), 1f);
    text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    bindDescription(text, null);
    return text;
  }

  public static void bindDescription (TextView text, CharSequence value) {
    if (value != null) {
      text.setText(value);
    }
    text.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    text.setGravity(Lang.rtl() ? android.view.Gravity.RIGHT : android.view.Gravity.LEFT);
    text.setPadding(Screen.dp(GUTTER + 16f), Screen.dp(8f), Screen.dp(GUTTER + 16f), Screen.dp(8f));
  }

  // Adaptive grid

  public static final float GRID_EDGE = 16f, GRID_GAP = 8f;

  /** Columns of at least {@code minCellDp} (gaps and the 16dp gutters included) in {@code widthPx}, never below {@code minSpans}. */
  public static int spanCount (int widthPx, float minCellDp, int minSpans) {
    return spanCount(widthPx, minCellDp, minSpans, Integer.MAX_VALUE);
  }

  /** {@link #spanCount(int, float, int)} with at most {@code maxSpans} columns. */
  public static int spanCount (int widthPx, float minCellDp, int minSpans, int maxSpans) {
    if (widthPx <= 0) {
      widthPx = Screen.currentWidth();
    }
    final float usable = widthPx - Screen.dp(GRID_EDGE) * 2 + Screen.dp(GRID_GAP);
    final int count = (int) (usable / (Screen.dp(minCellDp) + Screen.dp(GRID_GAP)));
    return Math.max(minSpans, Math.min(maxSpans, count));
  }

  /**
   * Keeps the grid's column count adaptive (see {@link #spanCount}) as the list's width changes (cover and inner screen,
   * the two panes), and gives its cells the 16dp gutters and 8dp gaps ({@link GridSpacing}). The first item should
   * span every column (the large title's space sits above it).
   */
  public static void attachAdaptiveGrid (RecyclerView recyclerView, GridLayoutManager manager, float minCellDp, int minSpans) {
    attachAdaptiveGrid(recyclerView, manager, minCellDp, minSpans, Integer.MAX_VALUE);
  }

  public static void attachAdaptiveGrid (RecyclerView recyclerView, GridLayoutManager manager, float minCellDp, int minSpans, int maxSpans) {
    attachAdaptiveGrid(recyclerView, manager, minCellDp, minSpans, maxSpans, null);
  }

  /**
   * {@link #attachAdaptiveGrid(RecyclerView, GridLayoutManager, float, int, int)} where {@code isCell} tells the cells
   * from the full-width rows (needed when a single column makes a cell span every column).
   */
  public static void attachAdaptiveGrid (RecyclerView recyclerView, GridLayoutManager manager, float minCellDp, int minSpans, int maxSpans, @androidx.annotation.Nullable CellTest isCell) {
    recyclerView.addItemDecoration(new GridSpacing(isCell));
    recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      final int width = right - left;
      if (width <= 0 || width == oldRight - oldLeft) {
        return;
      }
      final int spans = spanCount(width, minCellDp, minSpans, maxSpans);
      if (spans != manager.getSpanCount()) {
        v.post(() -> {
          if (manager.getSpanCount() != spans) {
            manager.setSpanCount(spans);
            // The insets cached for the old column count would stay on the views reused from scrap
            recyclerView.invalidateItemDecorations();
          }
        });
      }
    });
  }

  /** Whether the adapter position is a grid cell (not a row placing itself across the grid). */
  public interface CellTest {
    boolean isCell (int position);
  }

  /**
   * Equal cells with {@link #GRID_EDGE} gutters and {@link #GRID_GAP} gaps (RTL mirrored) and the gap below each cell;
   * rows spanning every column get no offsets (they place themselves).
   */
  public static final class GridSpacing extends RecyclerView.ItemDecoration {
    private final @androidx.annotation.Nullable CellTest isCell;

    public GridSpacing () {
      this(null);
    }

    /** {@code isCell} tells the cells from the full-width rows; null infers it from the span size. */
    public GridSpacing (@androidx.annotation.Nullable CellTest isCell) {
      this.isCell = isCell;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      if (!(view.getLayoutParams() instanceof GridLayoutManager.LayoutParams) || !(parent.getLayoutManager() instanceof GridLayoutManager)) {
        return;
      }
      final GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) view.getLayoutParams();
      final int spans = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
      final int position = parent.getChildAdapterPosition(view);
      final boolean cell = isCell != null ? position != RecyclerView.NO_POSITION && isCell.isCell(position) : params.getSpanSize() < spans;
      if (!cell || spans <= 0) {
        return;
      }
      final int column = params.getSpanIndex();
      final float edge = Screen.dp(GRID_EDGE), gap = Screen.dp(GRID_GAP);
      final float perItem = (edge * 2f + gap * (spans - 1)) / spans;
      final int start = Math.round(edge + column * (gap - perItem));
      final int end = Math.round(perItem) - start;
      final boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
      outRect.left = rtl ? end : start;
      outRect.right = rtl ? start : end;
      outRect.bottom = Math.round(gap);
    }
  }

  // Selectable card

  /** Badge of a selected card: a 22dp primary circle with an onPrimary check, ringed 2dp in the card colour. */
  public static final float BADGE_SIZE = 22f, BADGE_RING = 2f;

  /**
   * The M3E selectable card of the pickers: surfaceBright, or secondaryContainer when selected (the colour on
   * EFFECTS_DEFAULT), CORNER_L at rest, CORNER_M pressed, CORNER_XL while selected; disabled at .38 with no press
   * morph. Colours are read at draw time. The owner draws the check badge with {@link #drawBadge}.
   */
  public static final class SelectableCard extends XgramExpressive.MorphDrawable {
    private final View view;
    private final XgramExpressive.Spring colorSpring, badgeSpring;
    private boolean checked, disabled;

    SelectableCard (View view, float restingDp, float pressedDp) {
      super(0, restingDp, pressedDp);
      this.view = view;
      setHost(view);
      this.colorSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidateSelf());
      this.badgeSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> view.invalidate());
      this.badgeSpring.setThreshold(.005f);
    }

    public boolean isChecked () {
      return checked;
    }

    /** Selected or not; springs while the card is on screen, snaps otherwise (binding a recycled cell). */
    public void setChecked (boolean checked, boolean animated) {
      if (this.checked == checked) {
        return;
      }
      this.checked = checked;
      final float target = checked ? 1f : 0f;
      final boolean animate = animated && view.isAttachedToWindow() && view.getWidth() > 0;
      if (animate) {
        colorSpring.animateTo(target);
        badgeSpring.animateTo(target);
      } else {
        colorSpring.setValue(target);
        badgeSpring.setValue(target);
        invalidateSelf();
        view.invalidate();
      }
      setCornerTarget(checked ? XgramExpressive.CORNER_XL : Float.NaN, animate);
    }

    /** Disabled cards (themes while Expressive design locks them) draw at .38 and take no presses. */
    public void setDisabled (boolean disabled) {
      if (this.disabled != disabled) {
        this.disabled = disabled;
        setPressedCorner(disabled ? Float.NaN : XgramExpressive.CORNER_M);
        invalidateSelf();
      }
    }

    /** 0..1 (overshooting on SPATIAL_FAST) for the badge. */
    public float badgeFactor () {
      return badgeSpring.getValue();
    }

    @Override
    @ColorInt
    public int getDrawColor () {
      final int rest = XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT);
      final int selected = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      final float f = MathUtils.clamp(colorSpring.getValue());
      int color = f <= 0f ? rest : f >= 1f ? selected : ColorUtils.fromToArgb(rest, selected, f);
      return color;
    }

    /** The content colour on the card now: onSurface, onSecondaryContainer when selected. */
    @ColorInt
    public int contentColor (boolean variant) {
      final int rest = XgramExpressive.color(variant ? XgramExpressive.ON_SURFACE_VARIANT : XgramExpressive.ON_SURFACE);
      final int selected = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
      final float f = MathUtils.clamp(colorSpring.getValue());
      return f <= 0f ? rest : f >= 1f ? selected : ColorUtils.fromToArgb(rest, selected, f);
    }

    /** The check badge centred on ({@code cx}, {@code cy}), scaled in from .6 (a fade only with Reduce motion). */
    public void drawBadge (Canvas c, float cx, float cy) {
      XgramR5Security.drawCheckBadge(c, cx, cy, badgeFactor(), getDrawColor());
    }
  }

  /**
   * Gives {@code v} a {@link SelectableCard} background: RippleDrawable(onSurface state layer that follows the theme,
   * the card, a mask in the card's animated shape) and the background outline.
   */
  public static SelectableCard attachSelectableCard (View v) {
    return attachSelectableCard(v, XgramExpressive.CORNER_L, XgramExpressive.CORNER_M);
  }

  public static SelectableCard attachSelectableCard (View v, float restingDp, float pressedDp) {
    SelectableCard card = new SelectableCard(v, restingDp, pressedDp);
    v.setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE, card, card.newMask()));
    v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    v.setClipToOutline(false);
    return card;
  }

  private static Paint badgeRingPaint;

  /** The check badge (see {@link #BADGE_SIZE}) at {@code factor} (0 hides it); {@code ringColor} is the card's colour. */
  public static void drawCheckBadge (Canvas c, float cx, float cy, float factor, @ColorInt int ringColor) {
    if (factor <= 0f) {
      return;
    }
    final float alpha = MathUtils.clamp(factor * 2f);
    final float scale = XgramMotion.reduce() ? 1f : .6f + .4f * Math.max(0f, factor);
    final float size = Screen.dpf(BADGE_SIZE);
    if (badgeRingPaint == null) {
      badgeRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      badgeRingPaint.setStyle(Paint.Style.FILL);
    }
    final int saveCount = c.save();
    if (scale != 1f) {
      c.scale(scale, scale, cx, cy);
    }
    badgeRingPaint.setColor(ColorUtils.alphaColor(alpha, ringColor));
    c.drawCircle(cx, cy, size / 2f + Screen.dpf(BADGE_RING), badgeRingPaint);
    XgramM3Controls.drawCheckbox(c, cx - size / 2f, cy - size / 2f, size, .5f + .5f * MathUtils.clamp(factor), 0f, 0f, 1f, alpha);
    c.restoreToCount(saveCount);
  }

  // Hero shape

  /**
   * A hero: an icon (onPrimaryContainer) on a Material 3 Expressive shape in primaryContainer, e.g. the 72dp
   * Cookie9Sided of the lock screen. The shape path is built when the size changes; colours are read when drawn.
   */
  public static final class HeroView extends View {
    private final androidx.graphics.shapes.RoundedPolygon polygon;
    private final android.graphics.Path path = new android.graphics.Path();
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float iconDp;
    private @androidx.annotation.Nullable android.graphics.drawable.Drawable icon;

    public HeroView (Context context, androidx.graphics.shapes.RoundedPolygon polygon, int iconRes, float iconDp) {
      super(context);
      this.polygon = polygon;
      this.iconDp = iconDp;
      paint.setStyle(Paint.Style.FILL);
      setIcon(iconRes);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setIcon (int iconRes) {
      this.icon = iconRes != 0 ? org.thunderdog.challegram.tool.Drawables.get(getResources(), XgramIconPack.map(iconRes)) : null;
      invalidate();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      path.rewind();
      if (w > 0 && h > 0) {
        XgramShapes.toPath(polygon, path);
        XgramShapes.fit(path, w / 2f, h / 2f, Math.min(w, h), 0f, matrix);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      paint.setColor(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER));
      c.drawPath(path, paint);
      if (icon != null) {
        final int size = Screen.dp(iconDp);
        final int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        icon.setBounds(left, top, left + size, top + size);
        icon.setTint(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER));
        icon.draw(c);
      }
    }
  }

  /** A filled tonal icon button of {@code sizeDp} (40 small, 56 medium, 72+ large) centred in a view of at least 48dp. */
  public static android.widget.ImageView newTonalIconButton (Context context, int iconRes, float sizeDp, CharSequence description) {
    android.widget.ImageView button = new android.widget.ImageView(context) {
      private int tint;

      @Override
      protected void onDraw (Canvas c) {
        final int color = XgramM3Sheets.tonalContentColor();
        if (tint != color) {
          tint = color;
          setColorFilter(color); // read when drawn: follows day / night and the wallpaper colours
        }
        super.onDraw(c);
      }
    };
    button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
    button.setImageResource(XgramIconPack.map(iconRes));
    XgramExpressive.MorphDrawable container = XgramM3Sheets.attachTonalIconButton(button);
    container.setSize(sizeDp, sizeDp, android.view.Gravity.CENTER);
    button.setContentDescription(description);
    button.setMinimumWidth(Screen.dp(Math.max(48f, sizeDp)));
    button.setMinimumHeight(Screen.dp(Math.max(48f, sizeDp)));
    return button;
  }

  // Password field

  /**
   * The trailing visibility toggle of a filled password field (a 40dp standard icon button in onSurfaceVariant, 48dp
   * target): it switches the input between dots and plain text and keeps the cursor where it was.
   */
  public static android.widget.ImageView attachVisibilityToggle (org.thunderdog.challegram.widget.MaterialEditTextGroup group) {
    final boolean rtl = Lang.rtl();
    final android.widget.EditText input = group.getEditText();
    input.setPadding(input.getPaddingLeft() + (rtl ? Screen.dp(40f) : 0), input.getPaddingTop(), input.getPaddingRight() + (rtl ? 0 : Screen.dp(40f)), input.getPaddingBottom());
    final boolean[] revealed = {false};
    android.widget.ImageView toggle = new android.widget.ImageView(group.getContext()) {
      private int tint;

      @Override
      protected void onDraw (Canvas c) {
        final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
        if (tint != color) {
          tint = color;
          setColorFilter(color);
        }
        super.onDraw(c);
      }
    };
    toggle.setScaleType(android.widget.ImageView.ScaleType.CENTER);
    toggle.setBackground(XgramExpressiveC.iconButtonRipple(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), 40f));
    final Runnable sync = () -> {
      toggle.setImageResource(XgramIconPack.map(revealed[0] ? org.thunderdog.challegram.R.drawable.baseline_eye_off_24 : org.thunderdog.challegram.R.drawable.baseline_visibility_24));
      toggle.setContentDescription(Lang.getString(revealed[0] ? org.thunderdog.challegram.R.string.XgramR5SecurityHidePassword : org.thunderdog.challegram.R.string.XgramR5SecurityShowPassword));
    };
    sync.run();
    toggle.setOnClickListener(v -> {
      revealed[0] = !revealed[0];
      sync.run();
      final int selection = input.getSelectionEnd();
      input.setTransformationMethod(revealed[0] ? null : android.text.method.PasswordTransformationMethod.getInstance());
      if (selection >= 0 && selection <= input.length()) {
        input.setSelection(selection);
      }
      v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    });
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f), android.view.Gravity.TOP | (rtl ? android.view.Gravity.LEFT : android.view.Gravity.RIGHT));
    params.topMargin = Screen.dp(16f);
    params.leftMargin = params.rightMargin = Screen.dp(4f);
    group.addView(toggle, params);
    return toggle;
  }

  // Connected button group (modes under an app bar)

  /**
   * An M3E connected button group of text segments (40dp, labelLarge, 2dp apart, 20dp outer and 8dp inner corners):
   * the selected one is secondaryContainer with a leading check and morphs into a pill on SPATIAL_FAST, the others are
   * surfaceContainerHighest. It scrolls sideways when the segments are wider than the screen (the cover screen) and is
   * centred otherwise; RTL puts the first segment on the right.
   */
  public static final class SegmentedRow extends android.widget.HorizontalScrollView {
    public interface Listener {
      void onSegmentSelected (int index);
    }

    private final android.widget.LinearLayout row;
    private final TextView[] segments;
    private final XgramExpressive.MorphDrawable[] morphs;
    private int selected = -1;

    public SegmentedRow (Context context, String[] labels, int selected, Listener listener) {
      super(context);
      setHorizontalScrollBarEnabled(false);
      setOverScrollMode(OVER_SCROLL_NEVER);
      setFillViewport(true);
      setClipToPadding(false);
      setPadding(Screen.dp(GUTTER), Screen.dp(8f), Screen.dp(GUTTER), Screen.dp(8f));
      row = new android.widget.LinearLayout(context);
      row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
      row.setGravity(android.view.Gravity.CENTER);
      addView(row, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f)));
      final int count = labels.length;
      segments = new TextView[count];
      morphs = new XgramExpressive.MorphDrawable[count];
      final boolean rtl = Lang.rtl();
      for (int p = 0; p < count; p++) {
        final int index = rtl ? count - 1 - p : p;
        TextView segment = new TextView(context);
        XgramExpressive.applyText(segment, XgramExpressive.TS_LABEL_L, false);
        segment.setText(labels[index]);
        segment.setSingleLine(true);
        segment.setGravity(android.view.Gravity.CENTER);
        segment.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
        segment.setMinWidth(Screen.dp(48f));
        segment.setCompoundDrawablePadding(Screen.dp(8f));
        XgramExpressive.MorphDrawable morph = XgramM3Connected.attachButton(segment, XgramExpressive.SURFACE_CONTAINER_HIGHEST);
        XgramM3Connected.shape(morph, true, true, p, count);
        segment.setOnClickListener(v -> {
          if (index != this.selected) {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            listener.onSegmentSelected(index);
          }
        });
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f));
        if (p > 0) {
          params.leftMargin = Screen.dp(XgramM3Connected.GAP);
        }
        row.addView(segment, params);
        segments[index] = segment;
        morphs[index] = morph;
      }
      setSelectedSegment(selected, false);
    }

    public int getSelectedSegment () {
      return selected;
    }

    public void setSelectedSegment (int index, boolean animated) {
      if (this.selected == index) {
        return;
      }
      this.selected = index;
      final boolean rtl = Lang.rtl();
      for (int i = 0; i < segments.length; i++) {
        final boolean on = i == index;
        morphs[i].setColorRole(on ? XgramExpressive.SECONDARY_CONTAINER : XgramExpressive.SURFACE_CONTAINER_HIGHEST);
        morphs[i].setCornerTarget(on ? XgramExpressive.CORNER_FULL : Float.NaN, animated && isAttachedToWindow());
        segments[i].setSelected(on);
        android.graphics.drawable.Drawable check = null;
        if (on) {
          check = org.thunderdog.challegram.tool.Drawables.get(getResources(), XgramIconPack.map(org.thunderdog.challegram.R.drawable.baseline_check_24));
          if (check != null) {
            final int size = Screen.dp(18f);
            check.setBounds(0, 0, size, size);
          }
        }
        segments[i].setCompoundDrawables(rtl ? null : check, null, rtl ? check : null, null);
      }
      refreshColors();
    }

    /** Label and check colours (the containers read theirs when drawn). */
    public void refreshColors () {
      for (int i = 0; i < segments.length; i++) {
        final int color = XgramExpressive.color(i == selected ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE);
        segments[i].setTextColor(color);
        for (android.graphics.drawable.Drawable d : segments[i].getCompoundDrawables()) {
          if (d != null) {
            d.setTint(color);
          }
        }
      }
    }
  }

  // Lock screen

  /**
   * The Material 3 Expressive passcode lock (PasscodeController while unlocking): a column of at most 360dp, centred
   * on the page: a 72dp Cookie9Sided hero with the lock, the title in headlineSmall, a bodyMedium supporting line that
   * turns into the inline error (wrong code, lockout countdown, biometrics), and the mode's content: 14dp PIN dots
   * over a 3 x 4 keypad of round surfaceContainerHigh keys, the password field and a filled Unlock button, the 3 x 3
   * pattern, or the fingerprint button. The controller keeps the logic (codes, attempts, biometrics); this view only
   * draws and reports input. Colours are read when drawn; motion is on springs (snapped with Reduce motion).
   */
  public static final class LockView extends android.widget.FrameLayout {
    public interface Callback {
      void onLockKey (int digit);
      void onLockBackspace ();
      boolean onLockBackspaceLong ();
      void onLockBiometrics ();
      void onLockPattern (org.thunderdog.challegram.util.PasscodeBuilder pattern);
    }

    public static final int CONTENT_NONE = 0, CONTENT_PIN = 1, CONTENT_PASSWORD = 2, CONTENT_PATTERN = 3, CONTENT_BIOMETRICS = 4, CONTENT_GESTURE = 5;
    private static final float MAX_WIDTH = 360f, KEY_SIZE = 76f, KEY_MIN = 52f, KEY_GAP_H = 20f, KEY_GAP_V = 12f;
    private static final long ERROR_CLEAR_MS = 520L;

    private final Callback callback;
    private final android.widget.ScrollView scroll;
    private final android.widget.FrameLayout wrap;
    private final android.widget.LinearLayout column;
    private final HeroView hero;
    private final TextView title, supporting;
    private final android.widget.FrameLayout slot;
    private @androidx.annotation.Nullable DotsView dots;
    private @androidx.annotation.Nullable KeypadView keypad;
    private @androidx.annotation.Nullable PatternView pattern;
    private @androidx.annotation.Nullable View biometricsButton;
    private final int[] xgramLoc = new int[2];
    private int content = CONTENT_NONE;

    private CharSequence restingText;
    private boolean restingError, transientError;
    private final Runnable clearError = this::clearErrorNow;

    private final XgramExpressive.Spring exit;
    private boolean exiting;
    private @androidx.annotation.Nullable android.animation.ValueAnimator shake;

    public LockView (Context context, Callback callback, boolean fullScreen) {
      super(context);
      this.callback = callback;

      scroll = new android.widget.ScrollView(context);
      scroll.setFillViewport(true);
      scroll.setVerticalScrollBarEnabled(false);
      scroll.setOverScrollMode(OVER_SCROLL_NEVER);
      addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      wrap = new android.widget.FrameLayout(context);
      final int top = (fullScreen ? org.thunderdog.challegram.navigation.HeaderView.getTopOffset() : 0) + Screen.dp(24f);
      wrap.setPadding(Screen.dp(GUTTER), top, Screen.dp(GUTTER), Screen.dp(24f));
      scroll.addView(wrap, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      column = new android.widget.LinearLayout(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          final int max = Screen.dp(MAX_WIDTH);
          if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(widthMeasureSpec) > max) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
          }
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
      };
      column.setOrientation(android.widget.LinearLayout.VERTICAL);
      column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
      wrap.addView(column, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER));

      hero = new HeroView(context, XgramShapes.COOKIE_9, org.thunderdog.challegram.R.drawable.baseline_lock_24, 32f);
      column.addView(hero, new android.widget.LinearLayout.LayoutParams(Screen.dp(72f), Screen.dp(72f)));

      title = new TextView(context);
      XgramExpressive.applyText(title, XgramExpressive.TS_HEADLINE_S, false);
      title.setGravity(android.view.Gravity.CENTER);
      android.widget.LinearLayout.LayoutParams titleParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      titleParams.topMargin = Screen.dp(16f);
      column.addView(title, titleParams);

      supporting = new TextView(context);
      XgramExpressive.applyText(supporting, XgramExpressive.TS_BODY_M, false);
      supporting.setGravity(android.view.Gravity.CENTER);
      supporting.setMaxLines(3);
      supporting.setMinHeight(Screen.dp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_M)));
      supporting.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
      android.widget.LinearLayout.LayoutParams supportingParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      supportingParams.topMargin = Screen.dp(8f);
      column.addView(supporting, supportingParams);

      slot = new android.widget.FrameLayout(context);
      android.widget.LinearLayout.LayoutParams slotParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      slotParams.topMargin = Screen.dp(24f);
      column.addView(slot, slotParams);

      exit = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> applyExit(value));
      refreshColors();
    }

    // Texts

    public void setTitle (CharSequence text) {
      title.animate().cancel();
      title.setAlpha(1f);
      title.setText(text);
    }

    /** A new step ("Confirm your PIN"): the title fades through (out and in on EFFECTS_FAST); set at once with Reduce motion. */
    public void setTitleAnimated (CharSequence text) {
      if (android.text.TextUtils.equals(title.getText(), text)) {
        return;
      }
      if (XgramMotion.reduce() || !isAttachedToWindow() || title.getWidth() == 0) {
        setTitle(text);
        return;
      }
      final long duration = Math.max(60L, XgramAppMotion.springDuration(XgramExpressive.EFFECTS_FAST) / 2);
      title.animate().cancel();
      title.animate().alpha(0f).setDuration(duration).setInterpolator(XgramAppMotion.springClamped(XgramExpressive.EFFECTS_FAST)).withEndAction(() -> {
        title.setText(text);
        title.animate().alpha(1f).setDuration(duration).setInterpolator(XgramAppMotion.springClamped(XgramExpressive.EFFECTS_FAST));
      });
    }

    /** The resting supporting line: the instruction, or the lockout countdown ({@code error}). */
    public void setSupporting (CharSequence text, boolean error) {
      this.restingText = text;
      this.restingError = error;
      if (!transientError || error) {
        // A lockout countdown replaces the last attempt's error at once (no input can clear it while it runs)
        transientError = false;
        applySupporting(text, error);
      }
    }

    private void applySupporting (CharSequence text, boolean error) {
      supporting.setText(text);
      supporting.setTextColor(XgramExpressive.color(error ? XgramExpressive.ERROR : XgramExpressive.ON_SURFACE_VARIANT));
    }

    /**
     * A failed attempt: the error in the supporting line until the next input, the dots (or the pattern) in error
     * and shaken, then cleared.
     */
    public void showError (CharSequence text, boolean shakeContent) {
      transientError = true;
      applySupporting(text, true);
      removeCallbacks(clearError);
      if (shakeContent) {
        if (dots != null) {
          dots.setError(true);
        }
        shake(dots != null ? dots : slot);
        postDelayed(clearError, ERROR_CLEAR_MS);
      }
    }

    private void clearErrorNow () {
      if (dots != null) {
        dots.setFilled(0, true);
        dots.setError(false);
      }
    }

    /** Back to the resting supporting line (the next key, pattern or text after an error). */
    public void onUserInput () {
      if (transientError) {
        transientError = false;
        removeCallbacks(clearError);
        clearErrorNow();
        applySupporting(restingText, restingError);
      }
    }

    private void shake (View target) {
      performHapticFeedback(android.view.HapticFeedbackConstants.REJECT);
      if (XgramMotion.reduce()) {
        return;
      }
      if (shake != null) {
        shake.cancel();
      }
      final float amplitude = Screen.dpf(10f);
      shake = android.animation.ValueAnimator.ofFloat(0f, 1f);
      shake.setDuration(380L);
      shake.setInterpolator(new android.view.animation.LinearInterpolator());
      shake.addUpdateListener(a -> {
        final float t = (float) a.getAnimatedValue();
        target.setTranslationX((float) (amplitude * Math.sin(t * Math.PI * 6) * (1f - t)));
      });
      shake.addListener(new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (android.animation.Animator animation) {
          target.setTranslationX(0f);
        }
      });
      shake.start();
    }

    /** Text colours (the drawn parts read theirs when drawn). Call after a theme change. */
    public void refreshColors () {
      title.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      supporting.setTextColor(XgramExpressive.color(transientError || restingError ? XgramExpressive.ERROR : XgramExpressive.ON_SURFACE_VARIANT));
      invalidateAll(this);
    }

    private static void invalidateAll (View v) {
      v.invalidate();
      if (v instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) v;
        for (int i = 0; i < group.getChildCount(); i++) {
          invalidateAll(group.getChildAt(i));
        }
      }
    }

    // Content

    /**
     * Shows the mode's content. {@code feedback} is the passcode's visibility (an invisible passcode shows no key press
     * and no pattern line); {@code biometrics} adds the fingerprint button (in the keypad's free key with a PIN).
     */
    public void setContent (int content, int pinSize, boolean feedback, boolean biometrics) {
      biometricsButton = null;
      this.content = content;
      slot.removeAllViews();
      dots = null;
      keypad = null;
      pattern = null;
      final Context context = getContext();
      switch (content) {
        case CONTENT_PIN: {
          android.widget.LinearLayout box = new android.widget.LinearLayout(context);
          box.setOrientation(android.widget.LinearLayout.VERTICAL);
          box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
          dots = new DotsView(context, pinSize);
          box.addView(dots, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(24f)));
          keypad = new KeypadView(context, feedback, biometrics);
          android.widget.LinearLayout.LayoutParams keypadParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          keypadParams.topMargin = Screen.dp(32f);
          box.addView(keypad, keypadParams);
          slot.addView(box, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          break;
        }
        case CONTENT_PATTERN: {
          pattern = new PatternView(context, feedback);
          slot.addView(pattern, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER_HORIZONTAL));
          if (biometrics) {
            addBiometricsBelow(context, pattern);
          }
          break;
        }
        case CONTENT_BIOMETRICS: {
          View button = newBiometricsButton(context, 96f);
          slot.addView(button, new android.widget.FrameLayout.LayoutParams(Screen.dp(96f), Screen.dp(96f), android.view.Gravity.CENTER_HORIZONTAL));
          break;
        }
        case CONTENT_GESTURE: {
          // The gesture overlay covers the whole page (PasscodeController): only room and the fingerprint here
          View space = new View(context);
          slot.addView(space, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(160f)));
          if (biometrics) {
            addBiometricsBelow(context, space);
          }
          break;
        }
        case CONTENT_PASSWORD:
        case CONTENT_NONE:
        default:
          break;
      }
    }

    private void addBiometricsBelow (Context context, View above) {
      slot.removeView(above);
      android.widget.LinearLayout box = new android.widget.LinearLayout(context);
      box.setOrientation(android.widget.LinearLayout.VERTICAL);
      box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
      box.addView(above);
      View button = newBiometricsButton(context, 56f);
      android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(Screen.dp(56f), Screen.dp(56f));
      params.topMargin = Screen.dp(24f);
      box.addView(button, params);
      slot.addView(box, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View newBiometricsButton (Context context, float sizeDp) {
      android.widget.ImageView button = newTonalIconButton(context, org.thunderdog.challegram.R.drawable.baseline_fingerprint_24, sizeDp,
        Lang.getString(org.thunderdog.challegram.R.string.XgramR5SecurityBiometrics));
      if (sizeDp >= 72f) {
        // The extra large icon button: a 40dp icon in the 96dp container
        final int padding = Screen.dp((sizeDp - 40f) / 2f);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        button.setPadding(padding, padding, padding, padding);
      }
      button.setOnClickListener(v -> {
        onUserInput();
        callback.onLockBiometrics();
      });
      biometricsButton = button;
      return button;
    }

    /**
     * Whether a screen point is on the fingerprint button, so the gesture overlay laid over this view
     * (PasscodeController) can let a touch there through.
     */
    public boolean isOnBiometricsButton (float rawX, float rawY) {
      final View b = biometricsButton;
      if (b == null || !b.isShown()) {
        return false;
      }
      b.getLocationOnScreen(xgramLoc);
      return rawX >= xgramLoc[0] && rawX < xgramLoc[0] + b.getWidth() && rawY >= xgramLoc[1] && rawY < xgramLoc[1] + b.getHeight();
    }

    /** Password mode: the filled field and the full-width Unlock button (48dp, at most the column's 360dp). */
    public void setPasswordViews (View field, View unlockButton, boolean biometrics) {
      biometricsButton = null;
      this.content = CONTENT_PASSWORD;
      slot.removeAllViews();
      dots = null;
      keypad = null;
      pattern = null;
      // Shown again after another mode (setting a passcode): out of the box they were in
      for (View v : new View[] {field, unlockButton}) {
        if (v.getParent() instanceof ViewGroup) {
          ((ViewGroup) v.getParent()).removeView(v);
        }
      }
      android.widget.LinearLayout box = new android.widget.LinearLayout(getContext());
      box.setOrientation(android.widget.LinearLayout.VERTICAL);
      box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
      box.addView(field, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      android.widget.LinearLayout.LayoutParams buttonParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      buttonParams.topMargin = Screen.dp(24f);
      box.addView(unlockButton, buttonParams);
      if (biometrics) {
        View button = newBiometricsButton(getContext(), 56f);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(Screen.dp(56f), Screen.dp(56f));
        params.topMargin = Screen.dp(24f);
        box.addView(button, params);
      }
      slot.addView(box, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public int getContent () {
      return content;
    }

    /** PIN dots filled so far. */
    public void setFilled (int count, boolean animated) {
      if (dots != null) {
        dots.setFilled(count, animated);
      }
    }

    public int getFilled () {
      return dots != null ? dots.filled : 0;
    }

    /** True while a pattern is being drawn (the page's swipe back waits). */
    public boolean isTracking () {
      return pattern != null && pattern.tracking;
    }

    // Exit: scale to .92 and fade on SPATIAL_DEFAULT

    public boolean isExiting () {
      return exiting;
    }

    public void playExit () {
      if (exiting) {
        return;
      }
      exiting = true;
      if (XgramMotion.reduce()) {
        applyExit(1f);
        return;
      }
      exit.animateTo(1f);
    }

    private void applyExit (float p) {
      final float scale = 1f - .08f * p;
      column.setScaleX(scale);
      column.setScaleY(scale);
      column.setAlpha(MathUtils.clamp(1f - p * 1.25f));
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (keypad == null || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        return;
      }
      // Keys shrink (to 52dp) before the column scrolls: the cover screen in landscape, split screen
      keypad.setMaxKey(Screen.dp(KEY_SIZE));
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      final int available = MeasureSpec.getSize(heightMeasureSpec) - wrap.getPaddingTop() - wrap.getPaddingBottom();
      final int needed = column.getMeasuredHeight();
      if (available > 0 && needed > available) {
        final int key = Math.max(Screen.dp(KEY_MIN), Screen.dp(KEY_SIZE) - (needed - available + 3) / 4);
        if (key != keypad.maxKey) {
          keypad.setMaxKey(key);
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
      }
    }

    // PIN dots

    private static final class DotsView extends View {
      private static final float DOT = 14f, GAP = 16f;
      private final XgramExpressive.Spring[] fill;
      private final XgramExpressive.Spring error;
      private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG), fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private int filled;

      DotsView (Context context, int count) {
        super(context);
        fill = new XgramExpressive.Spring[Math.max(1, count)];
        for (int i = 0; i < fill.length; i++) {
          fill[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate()).setThreshold(.005f);
        }
        error = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidate());
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(Screen.dpf(2f));
        fillPaint.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      }

      void setFilled (int count, boolean animated) {
        this.filled = Math.max(0, Math.min(fill.length, count));
        for (int i = 0; i < fill.length; i++) {
          final float target = i < filled ? 1f : 0f;
          if (animated && isAttachedToWindow()) {
            fill[i].animateTo(target);
          } else {
            fill[i].setValue(target);
          }
        }
        invalidate();
      }

      void setError (boolean isError) {
        if (isAttachedToWindow()) {
          error.animateTo(isError ? 1f : 0f);
        } else {
          error.setValue(isError ? 1f : 0f);
          invalidate();
        }
      }

      @Override
      protected void onDraw (Canvas c) {
        final float r = Screen.dpf(DOT) / 2f, gap = Screen.dpf(GAP);
        final float total = fill.length * r * 2f + (fill.length - 1) * gap;
        float cx = (getWidth() - total) / 2f + r;
        final float cy = getHeight() / 2f;
        final float e = MathUtils.clamp(error.getValue());
        final int primary = ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.PRIMARY), XgramExpressive.color(XgramExpressive.ERROR), e);
        final int outline = ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.OUTLINE), XgramExpressive.color(XgramExpressive.ERROR), e);
        for (XgramExpressive.Spring spring : fill) {
          final float f = spring.getValue();
          if (f < 1f) {
            ringPaint.setColor(outline);
            c.drawCircle(cx, cy, r - ringPaint.getStrokeWidth() / 2f, ringPaint);
          }
          if (f > 0f) {
            fillPaint.setColor(primary);
            c.drawCircle(cx, cy, r * f, fillPaint);
          }
          cx += r * 2f + gap;
        }
      }
    }

    // Keypad

    private static android.text.TextPaint digitPaint, lettersPaint;

    private static android.text.TextPaint digitPaint () {
      if (digitPaint == null) {
        digitPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        digitPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_HEADLINE_L, false));
        digitPaint.setFontFeatureSettings("tnum");
      }
      return digitPaint;
    }

    private static android.text.TextPaint lettersPaint () {
      if (lettersPaint == null) {
        lettersPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        lettersPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_S, false));
        lettersPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_S) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_S));
      }
      return lettersPaint;
    }

    private static String lettersOf (int digit) {
      switch (digit) {
        case 0: return "+";
        case 2: return "ABC";
        case 3: return "DEF";
        case 4: return "GHI";
        case 5: return "JKL";
        case 6: return "MNO";
        case 7: return "PQRS";
        case 8: return "TUV";
        case 9: return "WXYZ";
        default: return "";
      }
    }

    private final class KeyView extends View {
      private final String digit, letters;
      private float scale = -1f, digitWidth, lettersWidth;

      KeyView (Context context, int number, boolean feedback) {
        super(context);
        this.digit = String.valueOf(number);
        this.letters = lettersOf(number);
        setClickable(true);
        setFocusable(true);
        setContentDescription(digit);
        XgramExpressive.MorphDrawable container = XgramExpressive.attachPressMorph(this, XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGH), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_L);
        container.setColorRole(XgramExpressive.SURFACE_CONTAINER_HIGH);
        if (!feedback) {
          // An invisible passcode shows which key was pressed in no way: no state layer, no press morph
          container.setPressedCorner(Float.NaN);
          setBackground(container);
        }
        setOnClickListener(v -> {
          performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
          onUserInput();
          callback.onLockKey(number);
        });
      }

      private void measureTexts () {
        final float s = getWidth() / (float) Screen.dp(KEY_SIZE);
        if (s == scale) {
          return;
        }
        scale = s;
        final android.text.TextPaint d = digitPaint(), l = lettersPaint();
        d.setTextSize(Screen.dpf(XgramExpressive.textSizeSp(XgramExpressive.TS_HEADLINE_L)) * Math.min(1f, s));
        l.setTextSize(Screen.dpf(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_S)) * Math.min(1f, s));
        digitWidth = d.measureText(digit);
        lettersWidth = letters.isEmpty() ? 0f : l.measureText(letters);
      }

      @Override
      protected void onDraw (Canvas c) {
        scale = -1f; // the paints are shared: sizes are set again for this key
        measureTexts();
        final android.text.TextPaint d = digitPaint(), l = lettersPaint();
        final float cx = getWidth() / 2f, cy = getHeight() / 2f;
        final float digitHeight = -(d.ascent() + d.descent());
        final float lettersHeight = letters.isEmpty() ? 0f : -(l.ascent() + l.descent());
        final float block = digitHeight + (letters.isEmpty() ? 0f : Screen.dpf(2f) + lettersHeight);
        final float top = cy - block / 2f;
        d.setColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
        c.drawText(digit, cx - digitWidth / 2f, letters.isEmpty() ? cy + digitHeight / 2f : top + digitHeight, d);
        if (!letters.isEmpty()) {
          l.setColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
          c.drawText(letters, cx - lettersWidth / 2f, top + digitHeight + Screen.dpf(2f) + lettersHeight, l);
        }
      }
    }

    private final class KeypadView extends ViewGroup {
      int maxKey = Screen.dp(KEY_SIZE);

      KeypadView (Context context, boolean feedback, boolean biometrics) {
        super(context);
        for (int number = 1; number <= 9; number++) {
          addView(new KeyView(context, number, feedback));
        }
        if (biometrics) {
          addView(newBiometricsButton(context, 56f));
        } else {
          addView(new View(context));
        }
        addView(new KeyView(context, 0, feedback));
        android.widget.ImageView backspace = newTonalIconButton(context, org.thunderdog.challegram.R.drawable.baseline_backspace_24, 56f,
          Lang.getString(org.thunderdog.challegram.R.string.XgramR5SecurityBackspace));
        backspace.setOnClickListener(v -> {
          performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
          onUserInput();
          callback.onLockBackspace();
        });
        backspace.setOnLongClickListener(v -> {
          onUserInput();
          return callback.onLockBackspaceLong();
        });
        addView(backspace);
      }

      void setMaxKey (int px) {
        if (maxKey != px) {
          maxKey = px;
          requestLayout();
        }
      }

      private int keySize (int widthMeasureSpec) {
        int key = maxKey;
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
          key = Math.min(key, (MeasureSpec.getSize(widthMeasureSpec) - Screen.dp(KEY_GAP_H) * 2) / 3);
        }
        return Math.max(Screen.dp(40f), key);
      }

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        final int key = keySize(widthMeasureSpec);
        final int spec = MeasureSpec.makeMeasureSpec(key, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
          getChildAt(i).measure(spec, spec);
        }
        final float ratio = key / (float) Screen.dp(KEY_SIZE);
        setMeasuredDimension(key * 3 + Screen.dp(KEY_GAP_H) * 2, key * 4 + Math.round(Screen.dp(KEY_GAP_V) * Math.min(1f, ratio)) * 3);
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        // A phone keypad keeps 1 2 3 from the left in every language (Material number pads are not mirrored)
        final int count = getChildCount();
        if (count == 0) {
          return;
        }
        final int key = getChildAt(0).getMeasuredWidth();
        final int hGap = Screen.dp(KEY_GAP_H);
        final int vGap = (getMeasuredHeight() - key * 4) / 3;
        for (int i = 0; i < count; i++) {
          final int row = i / 3, col = i % 3;
          final int x = col * (key + hGap), y = row * (key + vGap);
          getChildAt(i).layout(x, y, x + key, y + key);
        }
      }
    }

    // Pattern

    private final class PatternView extends View {
      private static final float MAX_SIZE = 288f, DOT = 4f, DOT_HIT = 8f, LINE = 6f;
      private final boolean visible;
      private final float[] centerX = new float[9], centerY = new float[9];
      private final boolean[] hit = new boolean[9];
      private final XgramExpressive.Spring[] grow = new XgramExpressive.Spring[9];
      private final XgramExpressive.Spring fade;
      private final org.thunderdog.challegram.util.PasscodeBuilder built = new org.thunderdog.challegram.util.PasscodeBuilder();
      private final int[] order = new int[9];
      private int orderSize;
      private final android.graphics.Path line = new android.graphics.Path();
      private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG), linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private float touchX, touchY;
      boolean tracking;

      PatternView (Context context, boolean visible) {
        super(context);
        this.visible = visible;
        for (int i = 0; i < 9; i++) {
          grow[i] = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate()).setThreshold(.005f);
        }
        fade = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 1f, (s, value) -> invalidate());
        fade.setEndListener(() -> {
          if (!tracking && fade.getTarget() == 0f) {
            reset();
          }
        });
        dotPaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(Screen.dpf(LINE));
        setContentDescription(Lang.getString(org.thunderdog.challegram.R.string.PasscodePattern));
      }

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int size = Screen.dp(MAX_SIZE);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
          size = Math.min(size, MeasureSpec.getSize(widthMeasureSpec));
        }
        setMeasuredDimension(size, size);
      }

      @Override
      protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        final float cell = w / 3f;
        for (int i = 0; i < 9; i++) {
          centerX[i] = cell * (i % 3) + cell / 2f;
          centerY[i] = cell * (i / 3) + cell / 2f;
        }
      }

      private void reset () {
        built.clear();
        orderSize = 0;
        for (int i = 0; i < 9; i++) {
          hit[i] = false;
          grow[i].setValue(0f);
        }
        fade.setValue(1f);
        invalidate();
      }

      private int dotAt (float x, float y) {
        final float radius = getWidth() / 3f * .32f;
        for (int i = 0; i < 9; i++) {
          final float dx = x - centerX[i], dy = y - centerY[i];
          if (!hit[i] && dx * dx + dy * dy <= radius * radius) {
            return i;
          }
        }
        return -1;
      }

      private void select (int dot) {
        if (orderSize > 0) {
          final int last = order[orderSize - 1];
          final int r1 = last / 3, c1 = last % 3, r2 = dot / 3, c2 = dot % 3;
          if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
            final int middle = ((r1 + r2) / 2) * 3 + (c1 + c2) / 2;
            if (middle != last && middle != dot && !hit[middle]) {
              add(middle);
            }
          }
        }
        add(dot);
      }

      private void add (int dot) {
        hit[dot] = true;
        order[orderSize++] = dot;
        built.append(dot);
        if (visible) {
          grow[dot].animateTo(1f);
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
      }

      @Override
      public boolean onTouchEvent (android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
          case android.view.MotionEvent.ACTION_DOWN: {
            reset();
            final int dot = dotAt(e.getX(), e.getY());
            if (dot == -1) {
              return false;
            }
            onUserInput();
            tracking = true;
            getParent().requestDisallowInterceptTouchEvent(true);
            touchX = e.getX();
            touchY = e.getY();
            select(dot);
            invalidate();
            return true;
          }
          case android.view.MotionEvent.ACTION_MOVE: {
            if (!tracking) {
              return false;
            }
            touchX = e.getX();
            touchY = e.getY();
            final int dot = dotAt(touchX, touchY);
            if (dot != -1) {
              select(dot);
            }
            invalidate();
            return true;
          }
          case android.view.MotionEvent.ACTION_UP:
          case android.view.MotionEvent.ACTION_CANCEL: {
            if (!tracking) {
              return false;
            }
            tracking = false;
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP && built.getSize() > 0) {
              callback.onLockPattern(new org.thunderdog.challegram.util.PasscodeBuilder(built));
            }
            fade.animateTo(0f);
            invalidate();
            return true;
          }
        }
        return super.onTouchEvent(e);
      }

      @Override
      protected void onDraw (Canvas c) {
        final float alpha = MathUtils.clamp(fade.getValue());
        final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
        if (visible && orderSize > 0 && alpha > 0f) {
          line.rewind();
          line.moveTo(centerX[order[0]], centerY[order[0]]);
          for (int i = 1; i < orderSize; i++) {
            line.lineTo(centerX[order[i]], centerY[order[i]]);
          }
          if (tracking) {
            line.lineTo(touchX, touchY);
          }
          linePaint.setColor(ColorUtils.alphaColor(alpha, primary));
          c.drawPath(line, linePaint);
        }
        final int rest = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
        for (int i = 0; i < 9; i++) {
          final float g = grow[i].getValue() * (hit[i] ? alpha : 1f);
          final float radius = Screen.dpf(DOT) + (Screen.dpf(DOT_HIT) - Screen.dpf(DOT)) * g;
          dotPaint.setColor(ColorUtils.fromToArgb(rest, primary, MathUtils.clamp(g)));
          c.drawCircle(centerX[i], centerY[i], radius, dotPaint);
        }
      }
    }
  }
}
