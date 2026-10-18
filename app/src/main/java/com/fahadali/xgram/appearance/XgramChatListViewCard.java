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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.chats.XgramChatListMode;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.ColorUtils;

/**
 * XGram M3E: "Chat list view", a card of three preview tiles (One line, Two lines, Three lines) as in iMe's chat
 * settings, each a tiny chat list drawn from the color roles (so it follows light, dark and Pure black) above a radio
 * and its label. The selected tile is a selectable card: secondaryContainer, its corners grown from 12dp to 28dp, the
 * radio filled. A tap picks the view at once, so the chat list springs to it alongside (in the inner screen's other
 * pane). The host gives the card its background (the list's tile, or the Compose group's card).
 */
public final class XgramChatListViewCard extends ViewGroup {
  public interface Listener {
    void onModeSelected (int mode);
  }

  private static final float PADDING_H = 16f, PADDING_TOP = 16f, PADDING_BOTTOM = 16f, TILE_GAP = 8f, TILE_MAX_WIDTH = 132f;
  private static final float CONTAINER_HEIGHT = 112f, LABEL_GAP = 8f, LABEL_HEIGHT = 20f;
  private static final float TILE_HEIGHT = CONTAINER_HEIGHT + LABEL_GAP + LABEL_HEIGHT;

  private final Tile[] tiles = new Tile[XgramChatListMode.TILE_COUNT];
  private @Nullable Listener listener;
  private int mode;

  public XgramChatListViewCard (Context context) {
    super(context);
    setClipChildren(false); // the focus ring draws 2dp outside a tile
    setClipToPadding(false);
    this.mode = Settings.instance().getChatListMode();
    for (int i = 0; i < tiles.length; i++) {
      Tile tile = new Tile(context, this, i);
      tiles[i] = tile;
      addView(tile);
    }
    sync(false);
  }

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  /** Shows {@code mode}; animates only on screen. Also picks up a change of the "Bigger text" switch. */
  public void setMode (int mode, boolean animated) {
    this.mode = mode;
    sync(animated && getWindowToken() != null);
  }

  public int getMode () {
    return mode;
  }

  /** Repaints the tiles in the theme's roles now (a theme, Pure black or wallpaper color change). */
  public void invalidateTiles () {
    if (tiles == null) {
      return; // View's constructor, before the tiles exist
    }
    for (Tile tile : tiles) {
      if (tile != null) {
        tile.invalidate();
      }
    }
  }

  @Override
  public void invalidate () {
    super.invalidate();
    invalidateTiles(); // a theme listener invalidates the card: its tiles have display lists of their own
  }

  private float bigTextOf (int mode) {
    return mode == Settings.CHAT_MODE_3LINE_BIG || (!XgramChatListMode.isThreeLines(mode) && XgramSettings.instance().chatListBigText()) ? 1f : 0f;
  }

  private void sync (boolean animated) {
    final int selected = XgramChatListMode.tileOf(mode);
    final float bigText = bigTextOf(mode);
    for (Tile tile : tiles) {
      tile.setChecked(tile.index == selected, animated);
      tile.setBigText(bigText, animated);
    }
  }

  private void onTileClicked (Tile tile) {
    if (XgramChatListMode.tileOf(mode) == tile.index) {
      return;
    }
    tile.performHapticFeedback(Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_TICK : HapticFeedbackConstants.CLOCK_TICK);
    final int newMode = XgramChatListMode.modeOfTile(tile.index);
    setMode(newMode, true);
    if (listener != null) {
      listener.onModeSelected(newMode);
    }
  }

  // Layout: equal tiles 8dp apart, at most 132dp wide, centred when capped

  private int tileWidth (int width) {
    final int available = width - Screen.dp(PADDING_H) * 2 - Screen.dp(TILE_GAP) * (tiles.length - 1);
    return Math.max(0, Math.min(Screen.dp(TILE_MAX_WIDTH), available / tiles.length));
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int tileWidth = tileWidth(width);
    final int tileHeight = Screen.dp(TILE_HEIGHT);
    final int childWidthSpec = MeasureSpec.makeMeasureSpec(tileWidth, MeasureSpec.EXACTLY);
    final int childHeightSpec = MeasureSpec.makeMeasureSpec(tileHeight, MeasureSpec.EXACTLY);
    for (Tile tile : tiles) {
      tile.measure(childWidthSpec, childHeightSpec);
    }
    setMeasuredDimension(width, Screen.dp(PADDING_TOP) + tileHeight + Screen.dp(PADDING_BOTTOM));
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int width = r - l;
    final int tileWidth = tileWidth(width);
    final int gap = Screen.dp(TILE_GAP);
    final int total = tileWidth * tiles.length + gap * (tiles.length - 1);
    final int startX = Math.max(0, (width - total) / 2);
    final int top = Screen.dp(PADDING_TOP);
    final boolean rtl = Lang.rtl();
    for (Tile tile : tiles) {
      final int slot = rtl ? tiles.length - 1 - tile.index : tile.index; // RTL: One line on the right
      final int left = startX + slot * (tileWidth + gap);
      tile.layout(left, top, left + tileWidth, top + tile.getMeasuredHeight());
    }
  }

  @Override
  public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      info.setCollectionInfo(AccessibilityNodeInfo.CollectionInfo.obtain(1, tiles.length, false, AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE));
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    for (Tile tile : tiles) {
      tile.settle();
    }
  }

  /**
   * One tile: a 112dp container (the preview box inset 12dp, the radio centred at 90dp) and its label 8dp under it in
   * labelLarge. Colors and the selection follow EFFECTS_DEFAULT, the selected corners SPATIAL_DEFAULT, the press squish
   * (to 8dp) and the radio's dot SPATIAL_FAST (the dot shrinks on EFFECTS_FAST), the bigger text preview SPATIAL_FAST.
   * Every spring snaps with Reduce motion.
   */
  private static final class Tile extends View {
    private static final float[] ONE_LINE_ROWS = {22f, 42f, 62f}, ONE_LINE_LENGTHS = {1f, .72f, .86f};
    private static final float[] MULTI_LINE_ROWS = {27f, 57f};
    private static final float[] TWO_LINES_TITLE = {.58f, .48f}, TWO_LINES_TEXT = {.88f, .78f};
    private static final float[] THREE_LINES_TITLE = {.52f, .52f}, THREE_LINES_TEXT = {.86f, .86f}, THREE_LINES_TEXT2 = {.64f, .64f};

    final XgramChatListViewCard card;
    final int index;

    private final XgramExpressive.Spring selection, corner, press, dot, bigText;
    private boolean checked, hasState;
    private float bigTextTarget = -1f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG), labelPaintBold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private float pathCorner = -1f;
    private int pathWidth = -1;

    private final String label;
    private @Nullable CharSequence labelText, labelTextBold;
    private int labelWidth = -1;

    private @Nullable android.graphics.drawable.RippleDrawable ripple;
    private @Nullable PathMask rippleMask;
    private int rippleColor;

    Tile (Context context, XgramChatListViewCard card, int index) {
      super(context);
      this.card = card;
      this.index = index;
      this.label = XgramChatListMode.tileName(index);
      this.selection = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidate());
      this.corner = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> invalidate());
      this.press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      this.dot = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      this.bigText = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      stroke.setStyle(Paint.Style.STROKE);
      final float labelSize = Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L));
      labelPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, false));
      labelPaintBold.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, true));
      labelPaint.setTextSize(labelSize);
      labelPaintBold.setTextSize(labelSize);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        final float tracking = XgramExpressive.trackingSp(XgramExpressive.TS_LABEL_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_L);
        labelPaint.setLetterSpacing(tracking);
        labelPaintBold.setLetterSpacing(tracking);
      }
      labelPaint.setTextAlign(Paint.Align.CENTER);
      labelPaintBold.setTextAlign(Paint.Align.CENTER);
      setFocusable(true);
      setClickable(true);
      setContentDescription(label);
      setOnClickListener(v -> card.onTileClicked(this));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // The state layer: content at .10 pressed and .08 hovered or focused, in the container's (animated) shape
        rippleMask = new PathMask(this);
        rippleColor = contentColor();
        ripple = new android.graphics.drawable.RippleDrawable(rippleColors(rippleColor), null, rippleMask);
        setForeground(ripple);
      }
    }

    // State

    void setChecked (boolean checked, boolean animated) {
      if (hasState && this.checked == checked) {
        return;
      }
      final boolean wasChecked = hasState && this.checked;
      this.hasState = true;
      this.checked = checked;
      final float target = checked ? 1f : 0f;
      if (animated) {
        selection.animateTo(target);
        corner.animateTo(target);
        // The dot pops in with a bounce and goes quickly
        dot.setToken(checked ? XgramExpressive.SPATIAL_FAST : XgramExpressive.EFFECTS_FAST);
        dot.animateTo(target);
      } else {
        selection.setValue(target);
        corner.setValue(target);
        dot.setValue(target);
        invalidate();
      }
      if (wasChecked != checked) {
        updateRippleColor();
      }
    }

    void setBigText (float value, boolean animated) {
      if (bigTextTarget == value) {
        return;
      }
      bigTextTarget = value;
      if (animated) {
        bigText.animateTo(value);
      } else {
        bigText.setValue(value);
        invalidate();
      }
    }

    /** Off screen: every spring at its end. */
    void settle () {
      selection.setValue(selection.getTarget());
      corner.setValue(corner.getTarget());
      press.setValue(0f);
      dot.setValue(dot.getTarget());
      bigText.setValue(bigText.getTarget());
    }

    @Override
    protected void drawableStateChanged () {
      super.drawableStateChanged();
      if (press != null) { // null while View's constructor runs
        final boolean pressed = isPressed() && isEnabled();
        press.animateTo(pressed ? 1f : 0f);
      }
    }

    // Colors (roles, read at draw time)

    private static int lerp (int from, int to, float f) {
      return f <= 0f ? from : f >= 1f ? to : ColorUtils.fromToArgb(from, to, f);
    }

    /** The content role of the tile's resting state, for the state layer (not lerped, so it changes only with the selection or the theme). */
    private int contentColor () {
      return XgramExpressive.color(checked ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
    }

    private static ColorStateList rippleColors (int content) {
      final int pressed = XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED);
      final int hovered = XgramExpressive.withAlpha(content, XgramExpressive.STATE_HOVER);
      return new ColorStateList(
        new int[][] {{android.R.attr.state_pressed}, {android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}},
        new int[] {pressed, hovered, hovered, pressed}
      );
    }

    private void updateRippleColor () {
      if (ripple != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        final int content = contentColor();
        if (content != rippleColor) {
          rippleColor = content;
          ripple.setColor(rippleColors(content));
        }
      }
    }

    // Shape

    private float cornerPx () {
      final float rest = XgramExpressive.CORNER_M + (XgramExpressive.CORNER_XL - XgramExpressive.CORNER_M) * corner.getValue();
      final float pressed = rest + (XgramExpressive.CORNER_S - rest) * press.getValue();
      return Math.max(0f, Screen.dpf(pressed));
    }

    /** The container (the tile's top 112dp) in its current shape. */
    Path containerPath () {
      final float radius = cornerPx();
      final int width = getWidth();
      if (radius != pathCorner || width != pathWidth) {
        pathCorner = radius;
        pathWidth = width;
        rect.set(0f, 0f, width, Screen.dp(CONTAINER_HEIGHT));
        final float r = Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
        path.rewind();
        if (width > 0) {
          path.addRoundRect(rect, r, r, Path.Direction.CW);
        }
        if (rippleMask != null) {
          rippleMask.invalidateSelf();
        }
      }
      return path;
    }

    // Drawing

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth();
      if (width <= 0) {
        return;
      }
      updateRippleColor(); // a theme change since
      final float sel = Math.max(0f, Math.min(1f, selection.getValue()));
      final int onSurfaceVariant = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      final int content = lerp(onSurfaceVariant, XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), sel);
      final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);

      // Container: the chip step on the card (#1E1E1E on #121212 with Pure black), secondaryContainer when selected
      fill.setColor(lerp(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), sel));
      c.drawPath(containerPath(), fill);
      if (ripple == null && press.getValue() > 0f) {
        fill.setColor(XgramExpressive.withAlpha(contentColor(), XgramExpressive.STATE_PRESSED * Math.min(1f, press.getValue())));
        c.drawPath(containerPath(), fill);
      }

      // The preview, mirrored in RTL (avatars on the right)
      final boolean rtl = Lang.rtl();
      final int saveCount = c.save();
      if (rtl) {
        c.scale(-1f, 1f, width / 2f, 0f);
      }
      drawPreview(c, width, sel, content, onSurfaceVariant, primary);
      c.restoreToCount(saveCount);

      // Radio: a 20dp ring (2dp) in onSurfaceVariant to primary, the 10dp dot in primary
      final float cx = width / 2f, cy = Screen.dpf(90f);
      stroke.setStrokeWidth(Screen.dpf(2f));
      stroke.setColor(lerp(onSurfaceVariant, primary, sel));
      c.drawCircle(cx, cy, Screen.dpf(9f), stroke);
      final float dotScale = Math.max(0f, dot.getValue());
      if (dotScale > 0f) {
        fill.setColor(primary);
        c.drawCircle(cx, cy, Screen.dpf(5f) * dotScale, fill);
      }

      // Label: 500 onSurfaceVariant, 700 onSurface once selected
      final boolean bold = sel >= .5f;
      final TextPaint paint = bold ? labelPaintBold : labelPaint;
      if (labelWidth != width) {
        labelWidth = width;
        labelText = TextUtils.ellipsize(label, labelPaint, width, TextUtils.TruncateAt.END);
        labelTextBold = TextUtils.ellipsize(label, labelPaintBold, width, TextUtils.TruncateAt.END);
      }
      final CharSequence text = bold ? labelTextBold : labelText;
      if (text != null && text.length() > 0) {
        paint.setColor(lerp(onSurfaceVariant, XgramExpressive.color(XgramExpressive.ON_SURFACE), sel));
        paint.getFontMetrics(fontMetrics);
        final float lineTop = Screen.dpf(CONTAINER_HEIGHT + LABEL_GAP);
        final float baseline = lineTop + (Screen.dpf(LABEL_HEIGHT) - (fontMetrics.descent - fontMetrics.ascent)) / 2f - fontMetrics.ascent;
        c.drawText(text, 0, text.length(), width / 2f, baseline, paint);
      }

      // Focus ring: 3dp secondary, 2dp outside the container
      if (isFocused()) {
        final float outset = Screen.dpf(2f) + Screen.dpf(1.5f);
        rect.set(-outset, -outset, width + outset, Screen.dpf(CONTAINER_HEIGHT) + outset);
        stroke.setStrokeWidth(Screen.dpf(3f));
        stroke.setColor(XgramExpressive.color(XgramExpressive.SECONDARY));
        final float r = cornerPx() + outset;
        c.drawRoundRect(rect, r, r, stroke);
      }
    }

    private void bar (Canvas c, float left, float centerY, float length, float height, int color) {
      if (length <= 0f) {
        return;
      }
      final float h = Screen.dpf(height), half = h / 2f;
      rect.set(left, centerY - half, left + length, centerY + half);
      fill.setColor(color);
      c.drawRoundRect(rect, half, half, fill);
    }

    private void avatar (Canvas c, float left, float centerY, float diameterDp, int color) {
      final float radius = Screen.dpf(diameterDp) / 2f;
      fill.setColor(color);
      c.drawCircle(left + radius, centerY, radius, fill);
    }

    /** A tiny chat list of the tile's view, in the container's coordinates (the box: 12dp in, 12..72dp high). */
    private void drawPreview (Canvas c, int width, float sel, int content, int onSurfaceVariant, int primary) {
      final int avatarColor = lerp(XgramExpressive.withAlpha(onSurfaceVariant, .38f), primary, sel);
      final int titleColor = XgramExpressive.withAlpha(content, .64f);
      final int lineColor = XgramExpressive.withAlpha(content, .32f);
      final int badgeColor = lerp(XgramExpressive.withAlpha(content, .38f), primary, sel);
      final float boxLeft = Screen.dpf(12f), boxRight = width - Screen.dpf(12f);
      final float badgeWidth = Screen.dpf(12f), gap = Screen.dpf(6f);
      switch (index) {
        case XgramChatListMode.TILE_ONE_LINE: {
          // Small photos, the name and the time on one row, an unread badge on the first
          final float timeWidth = Screen.dpf(10f), textLeft = Screen.dpf(30f);
          final float bodyRight = boxRight - timeWidth - gap;
          for (int row = 0; row < ONE_LINE_ROWS.length; row++) {
            final float cy = Screen.dpf(ONE_LINE_ROWS[row]);
            avatar(c, boxLeft, cy, 12f, avatarColor);
            bar(c, boxRight - timeWidth, cy, timeWidth, 4f, lineColor);
            float titleRight = bodyRight;
            if (row == 0) {
              bar(c, bodyRight - badgeWidth, cy, badgeWidth, 8f, badgeColor);
              titleRight = bodyRight - badgeWidth - gap;
            }
            bar(c, textLeft, cy, (titleRight - textLeft) * ONE_LINE_LENGTHS[row], 6f, titleColor);
          }
          break;
        }
        case XgramChatListMode.TILE_TWO_LINES: {
          final float timeWidth = Screen.dpf(8f), textLeft = Screen.dpf(38f);
          final float titleSpan = boxRight - timeWidth - gap - textLeft;
          for (int row = 0; row < MULTI_LINE_ROWS.length; row++) {
            final float cy = Screen.dpf(MULTI_LINE_ROWS[row]);
            avatar(c, boxLeft, cy, 20f, avatarColor);
            bar(c, textLeft, cy - Screen.dpf(5f), titleSpan * TWO_LINES_TITLE[row], 6f, titleColor);
            bar(c, boxRight - timeWidth, cy - Screen.dpf(5f), timeWidth, 4f, lineColor);
            float textSpan = boxRight - textLeft;
            if (row == 0) {
              bar(c, boxRight - badgeWidth, cy + Screen.dpf(5f), badgeWidth, 8f, badgeColor);
              textSpan -= badgeWidth + gap;
            }
            bar(c, textLeft, cy + Screen.dpf(5f), textSpan * TWO_LINES_TEXT[row], 4f, lineColor);
          }
          break;
        }
        case XgramChatListMode.TILE_THREE_LINES:
        default: {
          // Bigger text grows the photo and the bars a little
          final float big = Math.max(0f, bigText.getValue());
          final float timeWidth = Screen.dpf(8f), textLeft = Screen.dpf(40f + 2f * big);
          final float titleSpan = boxRight - timeWidth - gap - textLeft;
          for (int row = 0; row < MULTI_LINE_ROWS.length; row++) {
            final float cy = Screen.dpf(MULTI_LINE_ROWS[row]);
            avatar(c, boxLeft, cy, 22f + 3f * big, avatarColor);
            bar(c, textLeft, cy - Screen.dpf(8f), titleSpan * THREE_LINES_TITLE[row], 5f + big, titleColor);
            bar(c, boxRight - timeWidth, cy - Screen.dpf(8f), timeWidth, 4f, lineColor);
            float textSpan = boxRight - textLeft;
            if (row == 0) {
              bar(c, boxRight - badgeWidth, cy + Screen.dpf(4f), badgeWidth, 8f, badgeColor);
              textSpan -= badgeWidth + gap;
            }
            bar(c, textLeft, cy, textSpan * THREE_LINES_TEXT[row], 4f + .5f * big, lineColor);
            bar(c, textLeft, cy + Screen.dpf(8f), textSpan * THREE_LINES_TEXT2[row], 4f + .5f * big, lineColor);
          }
          break;
        }
      }
    }

    // Accessibility: a radio button of a one-row collection

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName(RadioButton.class.getName());
      info.setCheckable(true);
      info.setChecked(checked);
      info.setText(label);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, index, 1, false, checked));
      }
    }
  }

  /** The ripple's mask: the tile's container in its current (springing) shape. */
  private static final class PathMask extends Drawable {
    private final Tile tile;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    PathMask (Tile tile) {
      this.tile = tile;
      paint.setColor(0xff000000);
    }

    @Override
    public void draw (@NonNull Canvas c) {
      c.drawPath(tile.containerPath(), paint);
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
