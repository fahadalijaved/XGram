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
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;

import java.util.List;
import java.util.Locale;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * Material 3 Expressive pieces of the location screens (the map viewer, the attach sheet's location page, live
 * location), the attach sheet's caption field and the map markers. Everything here is only used while Expressive
 * design is on, except the caption field, which also has Telegram X's look; colours are read when drawn, so day, night
 * and Pure black all hold.
 */
public final class XgramR6B7Places {
  private XgramR6B7Places () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Leading visuals of a list tile: a 40dp container with 12dp corners (shape.corner.medium)

  public static final float LEADING = 40f, LEADING_CORNER = XgramExpressive.CORNER_M;
  /** Start of a tile's text: 16dp padding, the 40dp leading container, 16dp. */
  public static final float TEXT_START = 72f;

  private static final RectF rect = new RectF();
  private static @Nullable Paint containerPaint;

  /** A rounded square of {@code sizeDp} with {@code cornerDp} corners centred on (cx, cy). */
  public static void drawContainer (@NonNull Canvas c, float cx, float cy, float sizeDp, float cornerDp, @ColorInt int color) {
    if ((color >>> 24) == 0) {
      return;
    }
    if (containerPaint == null) {
      containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      containerPaint.setStyle(Paint.Style.FILL);
    }
    final float half = Screen.dpf(sizeDp) / 2f;
    rect.set(cx - half, cy - half, cx + half, cy + half);
    final float radius = XgramExpressive.cornerPx(cornerDp, rect.width(), rect.height());
    containerPaint.setColor(color);
    c.drawRoundRect(rect, radius, radius, containerPaint);
  }

  /** {@code icon} centred on (cx, cy), tinted {@code color} at {@code alpha}. */
  public static void drawIcon (@NonNull Canvas c, @Nullable Drawable icon, float cx, float cy, @ColorInt int color, float alpha) {
    if (icon == null || alpha <= 0f) {
      return;
    }
    final Paint paint = Paints.getPorterDuffPaint(color);
    final int saved = paint.getAlpha();
    paint.setAlpha((int) (255f * MathUtils.clamp(alpha) * (((color >>> 24) & 0xff) / 255f)));
    Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, paint);
    paint.setAlpha(saved);
  }

  // Live location: the remaining time as a wavy ring around the leading visual

  /** Ring around a 40dp leading visual: 3dp out, 2.5dp stroke. */
  public static final float RING_GAP = 3f, RING_STROKE = 2.5f;

  /** The share's remaining part, 0..1 ({@code expiresAt} in uptime millis, 0: not live). */
  public static float liveProgress (int livePeriod, long expiresAt) {
    if (expiresAt == 0 || livePeriod <= 0) {
      return 0f;
    }
    final long remaining = expiresAt - SystemClock.uptimeMillis();
    if (remaining <= 0) {
      return 0f;
    }
    return MathUtils.clamp((float) ((double) remaining / ((double) livePeriod * 1000d)));
  }

  /**
   * Draws the remaining-time ring around a {@code visualDp} leading visual centred on (cx, cy) in {@code role}, its
   * track at 24%. Returns the delay until the ring moves by about a degree, or -1 when there is nothing to draw.
   */
  public static long drawLiveRing (@NonNull Canvas c, float cx, float cy, float visualDp, int role, int livePeriod, long expiresAt, float alpha) {
    final float progress = liveProgress(livePeriod, expiresAt);
    if (progress <= 0f || alpha <= 0f) {
      return -1;
    }
    final int color = XgramExpressive.color(role);
    final float stroke = Screen.dpf(RING_STROKE);
    final float radius = Screen.dpf(visualDp) / 2f + Screen.dpf(RING_GAP) + stroke / 2f;
    XgramWavyProgress.drawCircular(c, cx, cy, radius, stroke, progress,
      XgramExpressive.withAlpha(color, alpha), XgramExpressive.withAlpha(color, .24f * alpha), Screen.dpf(1f));
    return Math.max(1000L, (long) livePeriod * 1000L / 360L);
  }

  // Maps

  /** A role of the dark scheme: the theme's own while it is dark (Pure black included), else the dark scheme's. */
  private static int dark (int role, boolean themeDark) {
    return themeDark ? XgramExpressive.color(role) : XgramExpressive.colorFor(role, true);
  }

  private static String hex (@ColorInt int color) {
    return String.format(Locale.US, "#%06x", color & 0xffffff);
  }

  private static void styler (StringBuilder b, @Nullable String feature, @NonNull String element, @ColorInt int color) {
    if (b.length() > 1) {
      b.append(',');
    }
    b.append('{');
    if (feature != null) {
      b.append("\"featureType\":\"").append(feature).append("\",");
    }
    b.append("\"elementType\":\"").append(element).append("\",\"stylers\":[{\"color\":\"").append(hex(color)).append("\"}]}");
  }

  /**
   * The night map in the Material You dark scheme: the land in surfaceContainerLow (black with Pure black), streets
   * in the container steps above it, water tinted with the primary hue, labels in onSurfaceVariant. Built when the map
   * is styled, not per frame.
   */
  @NonNull
  public static String nightMapStyle () {
    final boolean themeDark = Theme.isDark();
    final boolean pureBlack = XgramAmoled.isActive();
    final int land = pureBlack ? 0xff000000 : dark(XgramExpressive.SURFACE_CONTAINER_LOW, themeDark);
    final int landAlt = pureBlack ? 0xff0b0b0b : dark(XgramExpressive.SURFACE_CONTAINER, themeDark);
    final int road = pureBlack ? 0xff1e1e1e : dark(XgramExpressive.SURFACE_CONTAINER_HIGHEST, themeDark);
    final int roadMinor = pureBlack ? 0xff141414 : dark(XgramExpressive.SURFACE_CONTAINER_HIGH, themeDark);
    final int highway = ColorUtils.fromToArgb(road, dark(XgramExpressive.SECONDARY_CONTAINER, themeDark), .6f);
    final int water = ColorUtils.fromToArgb(land, dark(XgramExpressive.PRIMARY_CONTAINER, themeDark), pureBlack ? .45f : .55f);
    final int park = ColorUtils.fromToArgb(land, dark(XgramExpressive.TERTIARY_CONTAINER, themeDark), .35f);
    final int label = dark(XgramExpressive.ON_SURFACE_VARIANT, themeDark);
    final int labelStrong = dark(XgramExpressive.ON_SURFACE, themeDark);
    final int outline = dark(XgramExpressive.OUTLINE_VARIANT, themeDark);
    StringBuilder b = new StringBuilder(1600);
    b.append('[');
    styler(b, null, "geometry", land);
    styler(b, null, "labels.text.fill", label);
    styler(b, null, "labels.text.stroke", land);
    styler(b, "administrative", "geometry.stroke", outline);
    styler(b, "administrative.locality", "labels.text.fill", labelStrong);
    styler(b, "landscape.man_made", "geometry", landAlt);
    styler(b, "poi", "geometry", landAlt);
    styler(b, "poi", "labels.text.fill", label);
    styler(b, "poi.park", "geometry", park);
    styler(b, "road", "geometry", roadMinor);
    styler(b, "road", "geometry.stroke", land);
    styler(b, "road.arterial", "geometry", road);
    styler(b, "road.highway", "geometry", highway);
    styler(b, "road.highway", "geometry.stroke", land);
    styler(b, "road", "labels.text.fill", label);
    styler(b, "transit", "geometry", roadMinor);
    styler(b, "transit.station", "labels.text.fill", label);
    styler(b, "water", "geometry", water);
    styler(b, "water", "labels.text.fill", label);
    b.append(']');
    return b.toString();
  }

  // Live location markers: a 44dp avatar in a 3dp primary ring, on a teardrop pin pointing at the spot

  public static final float MARKER_WIDTH = 56f, MARKER_HEIGHT = 72f, MARKER_AVATAR = 44f, MARKER_RING = 3f;
  private static final float MARKER_CENTER_Y = 28f, MARKER_TIP_Y = 68f;

  /** The marker's anchor along its height (the teardrop's tip). */
  public static float markerAnchorY () {
    return MARKER_TIP_Y / MARKER_HEIGHT;
  }

  /** Draws the teardrop (primary) on a marker bitmap's canvas. Called once per marker, never per frame. */
  public static void drawMarkerPin (@NonNull Canvas c, float alpha) {
    final float cx = Screen.dpf(MARKER_WIDTH) / 2f, cy = Screen.dpf(MARKER_CENTER_Y);
    final float r = Screen.dpf(MARKER_AVATAR / 2f + MARKER_RING);
    final float tipY = Screen.dpf(MARKER_TIP_Y);
    final float d = tipY - cy;
    final double a = Math.toDegrees(Math.acos(Math.min(1.0, r / d)));
    final Path path = new Path();
    final RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
    path.moveTo(cx, tipY);
    path.arcTo(oval, (float) (90.0 - a), (float) -(360.0 - 2.0 * a), false);
    path.close();
    final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    shadow.setColor(XgramExpressive.withAlpha(0xff000000, .22f * alpha));
    final float shadowW = Screen.dpf(8f), shadowH = Screen.dpf(3f);
    c.drawOval(new RectF(cx - shadowW / 2f, tipY - shadowH / 2f, cx + shadowW / 2f, tipY + shadowH / 2f), shadow);
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setStyle(Paint.Style.FILL);
    paint.setPathEffect(new CornerPathEffect(Screen.dpf(3f)));
    paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), alpha));
    c.drawPath(path, paint);
  }

  /** The avatar photo in the pin's 44dp circle. Safe off the main thread (allocates its own paint). */
  public static void drawMarkerAvatar (@NonNull Canvas c, @NonNull Bitmap bitmap) {
    final float size = Screen.dpf(MARKER_AVATAR);
    final float left = Screen.dpf(MARKER_WIDTH) / 2f - size / 2f, top = Screen.dpf(MARKER_CENTER_Y) - size / 2f;
    final BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    final Matrix matrix = new Matrix();
    final float scale = size / (float) Math.max(1, Math.min(bitmap.getWidth(), bitmap.getHeight()));
    matrix.postScale(scale, scale);
    matrix.postTranslate(left - (bitmap.getWidth() * scale - size) / 2f, top - (bitmap.getHeight() * scale - size) / 2f);
    shader.setLocalMatrix(matrix);
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setShader(shader);
    c.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint);
  }

  /** Initials in the pin's circle (an account without a photo): labelLarge in onPrimaryContainer on primaryContainer. */
  public static void drawMarkerLetters (@NonNull Canvas c, @Nullable String letters) {
    final float size = Screen.dpf(MARKER_AVATAR);
    final float cx = Screen.dpf(MARKER_WIDTH) / 2f, cy = Screen.dpf(MARKER_CENTER_Y);
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setColor(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER));
    c.drawCircle(cx, cy, size / 2f, fill);
    if (letters == null || letters.isEmpty()) {
      return;
    }
    final android.text.TextPaint text = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    text.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
    text.setTextSize(Screen.dpf(16f));
    text.setColor(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER));
    text.setTextAlign(Paint.Align.CENTER);
    c.drawText(letters, cx, cy - (text.ascent() + text.descent()) / 2f, text);
  }

  // Map viewer: the list is a standard bottom sheet over the map

  /** Space the sheet adds over Telegram X's peek: 12dp of band above the 8dp spacer (the handle) and 8dp below the tile. */
  public static final float SHEET_BAND_EXTRA = 12f, SHEET_BOTTOM_GAP = 8f;

  public static int sheetPeekExtra () {
    return Screen.dp(SHEET_BAND_EXTRA + SHEET_BOTTOM_GAP);
  }

  /**
   * The map viewer's list as an M3 standard bottom sheet: surfaceContainerLow with 28dp top corners and the 32x4 drag
   * handle, drawn from the first item (Telegram X's top spacer) down; its rows as segmented surfaceBright tiles 16dp
   * in, 2dp apart, the run of rows between two headers being one group; section headers in primary. Replaces Telegram
   * X's background decoration (and the shared list decoration, which needs SHADOW_BOTTOM closed groups).
   */
  public static final class MapSheetDecoration extends RecyclerView.ItemDecoration {
    private final List<ListItem> items;
    private final XgramM3Chat.SheetTopDrawable sheet = new XgramM3Chat.SheetTopDrawable();
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tilePath = new Path();
    private final RectF tileRect = new RectF();
    private final float[] tileRadii = new float[8];

    public MapSheetDecoration (@NonNull List<ListItem> items) {
      this.items = items;
    }

    private static boolean isRow (int type) {
      return type == ListItem.TYPE_ATTACH_LOCATION_BIG || type == ListItem.TYPE_ATTACH_LOCATION;
    }

    private int segmentOf (int position) {
      final boolean first = position == 0 || !isRow(items.get(position - 1).getViewType());
      final boolean last = position == items.size() - 1 || !isRow(items.get(position + 1).getViewType());
      if (first && last) {
        return XgramExpressive.SEGMENT_SINGLE;
      }
      return first ? XgramExpressive.SEGMENT_FIRST : last ? XgramExpressive.SEGMENT_LAST : XgramExpressive.SEGMENT_MIDDLE;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      outRect.set(0, 0, 0, 0);
      final int position = parent.getChildAdapterPosition(view);
      if (position == RecyclerView.NO_POSITION || position >= items.size()) {
        return;
      }
      final int type = items.get(position).getViewType();
      if (position == 0) {
        outRect.top = Math.max(0, parent.getMeasuredHeight()
          - SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP)
          - SettingHolder.measureHeightForType(ListItem.TYPE_ATTACH_LOCATION_BIG)
          - sheetPeekExtra());
      }
      if (isRow(type) || XgramExpressive.isHeaderType(type)) {
        outRect.left = outRect.right = Screen.dp(XgramExpressive.LIST_INSET);
      }
      if (isRow(type) && position > 0 && isRow(items.get(position - 1).getViewType())) {
        outRect.top += Screen.dp(XgramExpressive.LIST_GAP);
      }
      if (position == items.size() - 1) {
        outRect.bottom = Screen.dp(SHEET_BOTTOM_GAP);
      }
    }

    @Override
    public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
      final int childCount = parent.getChildCount();
      if (childCount == 0) {
        return;
      }
      // The sheet: from the first item's top (minus the band) down past the list's bottom
      float sheetTop = Float.NaN;
      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        if (parent.getChildAdapterPosition(child) == 0) {
          sheetTop = child.getTop() + child.getTranslationY() - Screen.dpf(SHEET_BAND_EXTRA);
          break;
        }
      }
      if (Float.isNaN(sheetTop)) {
        sheetTop = 0f; // scrolled past the first item: the sheet fills the list
      }
      final float corner = MathUtils.clamp(sheetTop / Screen.dpf(XgramExpressive.CORNER_XL));
      sheet.set(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_LOW), corner);
      sheet.setHandleAlpha(corner);
      sheet.setBounds(0, (int) sheetTop, parent.getWidth(), parent.getHeight() + Screen.dp(XgramExpressive.CORNER_XL));
      sheet.draw(c);

      for (int i = 0; i < childCount; i++) {
        View child = parent.getChildAt(i);
        final int position = parent.getChildAdapterPosition(child);
        if (position == RecyclerView.NO_POSITION || position >= items.size()) {
          continue;
        }
        final int type = items.get(position).getViewType();
        if (XgramExpressive.isHeaderType(type)) {
          final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
          if (child instanceof TextView && ((TextView) child).getCurrentTextColor() != primary) {
            ((TextView) child).setTextColor(primary);
          }
          continue;
        }
        if (!isRow(type)) {
          continue;
        }
        final int segment = segmentOf(position);
        XgramExpressive.MorphDrawable morph = XgramExpressive.findMorph(child.getBackground());
        if (morph != null) {
          morph.setSegment(segment);
        } else {
          drawTile(c, child, segment);
        }
      }
    }

    private void drawTile (Canvas c, View child, int segment) {
      final float outer = Screen.dpf(XgramExpressive.LIST_OUTER_CORNER), inner = Screen.dpf(XgramExpressive.LIST_INNER_CORNER);
      final float top = segment == XgramExpressive.SEGMENT_SINGLE || segment == XgramExpressive.SEGMENT_FIRST ? outer : inner;
      final float bottom = segment == XgramExpressive.SEGMENT_SINGLE || segment == XgramExpressive.SEGMENT_LAST ? outer : inner;
      final float tx = child.getTranslationX(), ty = child.getTranslationY();
      tileRect.set(child.getLeft() + tx, child.getTop() + ty, child.getRight() + tx, child.getBottom() + ty);
      tileRadii[0] = tileRadii[1] = tileRadii[2] = tileRadii[3] = top;
      tileRadii[4] = tileRadii[5] = tileRadii[6] = tileRadii[7] = bottom;
      tilePath.rewind();
      tilePath.addRoundRect(tileRect, tileRadii, Path.Direction.CW);
      tilePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT), child.getAlpha()));
      c.drawPath(tilePath, tilePaint);
    }
  }

  // Instant View slideshows: the M3E page indicator over the photos

  /**
   * A 16x6dp primary pill for the current page and 6dp dots at 38% for the others, 6dp apart, on a soft dark pill so it
   * reads over any photo; the pill stretches between dots as the pager scrolls. Over media, so the dark scheme's roles.
   */
  public static final class PageIndicator extends org.thunderdog.challegram.widget.ViewPagerPositionView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF item = new RectF();
    private int count;
    private float position;

    public PageIndicator (Context context) {
      super(context);
    }

    @Override
    public void reset (int count, float factor) {
      super.reset(count, factor);
      if (this.count != count || this.position != factor) {
        this.count = count;
        this.position = factor;
        invalidate();
      }
    }

    @Override
    public void setPositionFactor (float factor) {
      super.setPositionFactor(factor);
      if (this.position != factor) {
        this.position = factor;
        invalidate();
      }
    }

    private float selectedOf (int i) {
      return MathUtils.clamp(1f - Math.abs(position - i));
    }

    @Override
    protected void onDraw (Canvas c) {
      if (count <= 1) {
        return;
      }
      final float dot = Screen.dpf(6f), active = Screen.dpf(16f), gap = Screen.dpf(6f);
      float total = gap * (count - 1);
      for (int i = 0; i < count; i++) {
        total += dot + (active - dot) * selectedOf(i);
      }
      final float cy = getHeight() / 2f;
      float x = (getWidth() - total) / 2f;
      final float pad = Screen.dpf(6f);
      item.set(x - pad, cy - dot / 2f - pad, x + total + pad, cy + dot / 2f + pad);
      paint.setColor(0x3d000000);
      c.drawRoundRect(item, item.height() / 2f, item.height() / 2f, paint);
      final int primary = XgramExpressive.colorFor(XgramExpressive.PRIMARY, true);
      final int rest = XgramExpressive.withAlpha(XgramExpressive.colorFor(XgramExpressive.ON_SURFACE, true), .38f);
      for (int i = 0; i < count; i++) {
        final float s = selectedOf(i);
        final float w = dot + (active - dot) * s;
        item.set(x, cy - dot / 2f, x + w, cy + dot / 2f);
        paint.setColor(ColorUtils.fromToArgb(rest, primary, s));
        c.drawRoundRect(item, dot / 2f, dot / 2f, paint);
        x += w + gap;
      }
    }
  }

  // Attach sheet: the caption field that rises above the navigation bar while photos are selected

  /**
   * The attach sheet's caption field, in either design (the one piece here also used with Expressive design off).
   * Expressive: a pill in surfaceContainerHighest (24dp corners, level 2 elevation) holding a 48dp M3E icon button for
   * emoji and the bodyLarge input (up to 4 lines), with the send button the owner adds ({@link #setSendButton}) 48dp at
   * the row's end. Telegram X: the chat composer's flat bar in the filling colour under its top shadow, with its 55 x 49dp
   * emoji and send buttons and its 18sp input. The owner gives the input (an InputView: custom emoji and formatting) and
   * opens the emoji panel from {@link #setEmojiButtonListener}; the button shows what a tap does ({@link #setEmojiState}).
   * The button lies over the input, whose padding leaves it room (under the input, the input took its taps). Colours are
   * synced when drawn.
   */
  public static final class CaptionPill extends FrameLayoutFix {
    public static final float ROW_MIN_HEIGHT = 56f, PILL_MIN_HEIGHT = 48f, PILL_CORNER = 24f, SEND_SIZE = 48f;
    /** Telegram X's composer: the bar's height, its buttons' width and the view of the top shadow over the bar. */
    public static final float FLAT_BAR_HEIGHT = 49f, FLAT_BUTTON_WIDTH = 55f, FLAT_SHADOW_HEIGHT = 6f;

    /** What a tap on the emoji button does, as the composer's: open the emoji panel, swap it for the keyboard, close it. */
    public static final int EMOJI_STATE_EMOJI = 0, EMOJI_STATE_KEYBOARD = 1, EMOJI_STATE_HIDE = 2;

    private final boolean expressive;
    private final FrameLayoutFix pill;
    private final ImageView emojiButton;
    private final EditText input;
    private final @Nullable XgramExpressive.MorphDrawable pillBackground;
    private final @Nullable android.graphics.drawable.ColorDrawable flatBackground;
    private final @Nullable View shadowView;
    private @Nullable View sendButton;
    private int emojiState = -1;
    private int textColor, hintColor, iconColor, fillColor;

    public CaptionPill (@NonNull Context context, @NonNull EditText input) {
      super(context);
      this.expressive = XgramExpressive.isEnabled();
      this.input = input;
      final boolean rtl = Lang.rtl();

      if (expressive) {
        shadowView = null;
      } else {
        org.thunderdog.challegram.widget.ShadowView shadow = new org.thunderdog.challegram.widget.ShadowView(context);
        shadow.setSimpleTopShadow(true);
        shadow.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(FLAT_SHADOW_HEIGHT), Gravity.TOP));
        addView(shadow);
        shadowView = shadow;
      }

      pill = new FrameLayoutFix(context);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
      if (expressive) {
        params.topMargin = params.bottomMargin = Screen.dp(4f);
        params.leftMargin = Screen.dp(rtl ? 8f + SEND_SIZE + 4f : 12f);
        params.rightMargin = Screen.dp(rtl ? 12f : 8f + SEND_SIZE + 4f);
      } else {
        params.topMargin = Screen.dp(FLAT_SHADOW_HEIGHT);
      }
      pill.setLayoutParams(params);
      pill.setMinimumHeight(Screen.dp(expressive ? PILL_MIN_HEIGHT : FLAT_BAR_HEIGHT));
      if (expressive) {
        pillBackground = new XgramExpressive.MorphDrawable(0, PILL_CORNER, Float.NaN);
        pillBackground.setColorRole(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
        pillBackground.setHost(pill);
        pill.setBackground(pillBackground);
        pill.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        pill.setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
        flatBackground = null;
      } else {
        pillBackground = null;
        flatBackground = new android.graphics.drawable.ColorDrawable(Theme.fillingColor());
        pill.setBackground(flatBackground);
      }
      addView(pill);

      input.setBackground(null);
      input.setSingleLine(false);
      input.setMaxLines(4);
      input.setHint(Lang.getString(R.string.AddCaption));
      if (expressive) {
        input.setMinHeight(Screen.dp(PILL_MIN_HEIGHT));
        XgramExpressive.applyText(input, XgramExpressive.TS_BODY_L, false);
        input.setPadding(Screen.dp(rtl ? 16f : 48f), Screen.dp(12f), Screen.dp(rtl ? 48f : 16f), Screen.dp(12f));
      } else {
        // The composer's input as it is (18sp), with room for both buttons
        input.setMinHeight(Screen.dp(FLAT_BAR_HEIGHT));
        input.setPadding(Screen.dp(FLAT_BUTTON_WIDTH), Screen.dp(12f), Screen.dp(FLAT_BUTTON_WIDTH), Screen.dp(12f));
      }
      input.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
      pill.addView(input);

      emojiButton = new ImageView(context);
      emojiButton.setScaleType(ImageView.ScaleType.CENTER);
      emojiButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(expressive ? 48f : FLAT_BUTTON_WIDTH), Screen.dp(expressive ? 48f : FLAT_BAR_HEIGHT), (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM));
      XgramAppMotion.iconButton(emojiButton); // the M3E standard icon button; none with Expressive design off, as the composer's
      setEmojiState(EMOJI_STATE_EMOJI);
      pill.addView(emojiButton); // over the input

      syncColors();
      setMinimumHeight(getMinRowHeight());
      setClipChildren(false);
      setClipToPadding(false);
    }

    /** The row's height with one line of text. */
    public int getMinRowHeight () {
      return expressive ? Screen.dp(ROW_MIN_HEIGHT) : Screen.dp(FLAT_SHADOW_HEIGHT) + Screen.dp(FLAT_BAR_HEIGHT);
    }

    public void setEmojiButtonListener (@Nullable View.OnClickListener listener) {
      emojiButton.setOnClickListener(listener);
    }

    /** Shows what a tap on the emoji button does now: one of the {@code EMOJI_STATE_} values. */
    public void setEmojiState (int state) {
      if (this.emojiState == state) {
        return;
      }
      this.emojiState = state;
      final int icon, description;
      if (state == EMOJI_STATE_KEYBOARD) {
        icon = R.drawable.baseline_keyboard_24;
        description = R.string.XgramCaptionKeyboard;
      } else if (state == EMOJI_STATE_HIDE) {
        icon = R.drawable.baseline_direction_arrow_down_24;
        description = R.string.XgramCaptionHideEmoji;
      } else {
        icon = expressive ? R.drawable.baseline_emoticon_outline_24 : R.drawable.deproko_baseline_insert_emoticon_26;
        description = R.string.XgramR6B7Emoji;
      }
      emojiButton.setImageResource(XgramIconPack.map(icon));
      emojiButton.setContentDescription(Lang.getString(description));
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        emojiButton.setTooltipText(emojiButton.getContentDescription());
      }
    }

    /** The row's send button at its end: {@link #SEND_SIZE} square on the pill's line, or the composer's 55 x 49dp one. */
    public void setSendButton (@NonNull View button) {
      if (sendButton != null) {
        removeView(sendButton);
      }
      this.sendButton = button;
      final int gravity = (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM;
      final FrameLayoutFix.LayoutParams params;
      if (expressive) {
        params = FrameLayoutFix.newParams(Screen.dp(SEND_SIZE), Screen.dp(SEND_SIZE), gravity);
        params.leftMargin = params.rightMargin = Screen.dp(8f);
        params.bottomMargin = Screen.dp(4f);
      } else {
        params = FrameLayoutFix.newParams(Screen.dp(FLAT_BUTTON_WIDTH), Screen.dp(FLAT_BAR_HEIGHT), gravity);
      }
      button.setLayoutParams(params);
      addView(button);
    }

    public EditText getInput () {
      return input;
    }

    private void syncColors () {
      final int text, hint, icon;
      if (expressive) {
        text = XgramExpressive.color(XgramExpressive.ON_SURFACE);
        hint = icon = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      } else {
        text = Theme.textAccentColor();
        hint = Theme.textPlaceholderColor();
        icon = Theme.iconColor();
        final int fill = Theme.fillingColor();
        if (fillColor != fill && flatBackground != null) {
          fillColor = fill;
          flatBackground.setColor(fill);
          if (shadowView != null) {
            shadowView.invalidate(); // the shadow's depth follows the theme too
          }
        }
      }
      if (textColor != text) {
        textColor = text;
        input.setTextColor(text);
      }
      if (hintColor != hint) {
        hintColor = hint;
        input.setHintTextColor(hint);
      }
      if (iconColor != icon) {
        iconColor = icon;
        emojiButton.setColorFilter(icon);
      }
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      syncColors(); // follows day and night without a theme listener; changes only when the scheme does
      super.dispatchDraw(c);
    }
  }
}
