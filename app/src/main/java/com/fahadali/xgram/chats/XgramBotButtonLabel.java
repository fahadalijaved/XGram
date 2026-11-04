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
package com.fahadali.xgram.chats;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseIntArray;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Text;

import me.vkryl.core.ColorUtils;

/**
 * What an inline keyboard button (TGInlineKeyboard.Button) shows with Expressive design: an M3 button's content on
 * one line. The label is labelLarge (14dp, weight 500); a long one shrinks down to 12dp and then ends with an
 * ellipsis, it never wraps. An emoji the label starts with is lifted into an 18dp leading slot, 8dp before the
 * label: a common action (✏️ ➡️ ❌ ...) shows as its Material icon when "Material icons on bot buttons" is on,
 * any other emoji is drawn there at 18dp, centred on the button rather than on the text line. The button type's own
 * hint (a link, the lock, @, a person, payment) takes the same 18dp geometry: leading when the label has no emoji,
 * trailing otherwise. The hint is a decoration: it goes first from a cell where the label would not fit whole beside
 * it, and it never widens the keyboard. The emoji's slot is kept with the setting off, so turning it on or off only
 * needs a redraw. A label still too long at 12dp takes the padding down to Telegram X's 4dp (then the icon gap to
 * 4dp) before it ends with an ellipsis, so short labels in dense grids (calendars, numbers) stay whole.
 * <p>
 * Built once per text and type (on the layout thread, like Telegram X's EmojiString), laid out once per cell width,
 * drawn every frame with colours the caller reads from the theme.
 */
public final class XgramBotButtonLabel {
  /** Row height (dp): an M3 small button; the 2dp gaps of the connected block make the touch pitch 42dp. */
  public static final float HEIGHT = 40f;
  /** Space between the cell's edges and its content (dp), as TGInlineKeyboard.getButtonPadding gives with Expressive design. */
  public static final float PADDING = 12f;
  /**
   * The least padding (dp), Telegram X's, for a label that does not fit at 12dp with the usual one; still clear of
   * the 8dp inner corners of a 40dp row, and the outer ones only curve away above and below the label.
   */
  public static final float PADDING_MIN = 4f;
  /** Icon slot (dp), M3 ButtonDefaults.IconSize. */
  public static final float ICON = 18f;
  /** Icon to label (dp), M3 ButtonDefaults.IconSpacing; {@link #GAP_MIN} once the padding is down to its least. */
  public static final float GAP = 8f, GAP_MIN = 4f;
  /** Smallest label size (dp) before the label ends with an ellipsis, and the step it shrinks by. */
  public static final float LABEL_MIN = 12f, LABEL_STEP = .5f;
  /** A type hint beside a label that ends with an ellipsis anyway stays only while it leaves the label this much (dp). */
  private static final float LABEL_MIN_ROOM = 24f;

  private static final int TS = XgramExpressive.TS_LABEL_L;
  private static final int STEPS = Math.max(1, Math.round((XgramExpressive.textSizeSp(TS) - LABEL_MIN) / LABEL_STEP) + 1);

  // Common actions: the emoji (or the plain symbol, which bots often send without U+FE0F) to its Material icon.
  // Every icon goes through Drawables.load, so the user's icon pack applies.
  private static final SparseIntArray TABLE = new SparseIntArray();

  private static void map (@DrawableRes int icon, int... codePoints) {
    for (int codePoint : codePoints) {
      TABLE.put(codePoint, icon);
    }
  }

  static {
    map(R.drawable.baseline_edit_24, 0x270F, 0x1F4DD, 0x1F58A, 0x1F58B);
    map(R.drawable.baseline_arrow_forward_24, 0x27A1, 0x25B6, 0x23E9, 0x23ED, 0x2192);
    map(R.drawable.baseline_arrow_back_24, 0x2B05, 0x25C0, 0x23EA, 0x23EE, 0x1F519, 0x2190);
    map(R.drawable.baseline_close_24, 0x274C, 0x2716, 0x274E);
    map(R.drawable.baseline_block_24, 0x1F6AB, 0x26D4);
    map(R.drawable.baseline_check_24, 0x2705, 0x2714, 0x2611);
    map(R.drawable.baseline_delete_24, 0x1F5D1);
    map(R.drawable.baseline_sync_24, 0x1F504, 0x1F503, 0x267B);
    map(R.drawable.baseline_settings_24, 0x2699, 0x1F527);
    map(R.drawable.baseline_search_24, 0x1F50D, 0x1F50E);
    map(R.drawable.baseline_add_24, 0x2795);
    map(R.drawable.baseline_remove_24, 0x2796);
    map(R.drawable.baseline_content_copy_24, 0x1F4CB);
    map(R.drawable.deproko_baseline_attach_26, 0x1F4CE);
    map(R.drawable.baseline_share_24, 0x1F4E4);
    map(R.drawable.baseline_file_download_24, 0x1F4E5);
    map(R.drawable.baseline_link_24, 0x1F517);
    map(R.drawable.baseline_info_24, 0x2139);
    map(R.drawable.baseline_help_24, 0x2753, 0x2754);
    map(R.drawable.baseline_star_24, 0x2B50, 0x1F31F);
    map(R.drawable.baseline_favorite_24, 0x2764, 0x2665);
    map(R.drawable.baseline_home_24, 0x1F3E0, 0x1F3E1);
    map(R.drawable.baseline_call_24, 0x1F4DE, 0x260E);
    map(R.drawable.baseline_chat_bubble_24, 0x1F4AC);
    map(R.drawable.baseline_camera_alt_24, 0x1F4F7, 0x1F4F8);
    map(R.drawable.baseline_notifications_24, 0x1F514);
    map(R.drawable.baseline_lock_24, 0x1F512, 0x1F510);
    map(R.drawable.xgram_m3e_r6_b2_lock_open_24, 0x1F513);
    map(R.drawable.baseline_person_24, 0x1F464);
    map(R.drawable.baseline_schedule_24, 0x23F0);
    for (int clock = 0x1F550; clock <= 0x1F567; clock++) {
      map(R.drawable.baseline_schedule_24, clock); // every clock face
    }
    map(R.drawable.baseline_inventory_2_24, 0x1F4E6);
    map(R.drawable.baseline_payments_24, 0x1F4B0, 0x1F4B5, 0x1F4B3, 0x1F4B8);
    map(R.drawable.baseline_shopping_cart_24, 0x1F6D2);
    map(R.drawable.baseline_location_on_24, 0x1F4CD);
    map(R.drawable.baseline_language_24, 0x1F310);
    map(R.drawable.baseline_arrow_upward_24, 0x2B06);
    map(R.drawable.baseline_arrow_downward_24, 0x2B07);
  }

  /** ← and → are text, not emoji: with the setting off they are drawn as text in the slot. */
  private static boolean isTextArrow (int codePoint) {
    return codePoint == 0x2190 || codePoint == 0x2192;
  }

  private static boolean isSpace (char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  // Paints: one per size step (and fake bold), shared by every button. Measured on the layout thread and coloured
  // right before drawing on the UI thread, like Telegram X's Paints.getBoldPaint14.

  private static final TextPaint[] PAINTS = new TextPaint[STEPS * 2];

  private static float sizeDp (int step) {
    return XgramExpressive.textSizeSp(TS) - step * LABEL_STEP;
  }

  private static TextPaint paint (int step, boolean fakeBold) {
    synchronized (PAINTS) {
      final int index = step * 2 + (fakeBold ? 1 : 0);
      TextPaint paint = PAINTS[index];
      if (paint == null) {
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        if (fakeBold) {
          paint.setFakeBoldText(true);
        }
        PAINTS[index] = paint;
      }
      // Size (dp, like the rest of the chat's chrome), the app font and the tracking are checked every time: the
      // density and the app font can change while the app runs
      final float size = Screen.dpf(sizeDp(step));
      if (paint.getTextSize() != size) {
        paint.setTextSize(size);
      }
      final Typeface typeface = fakeBold ? Fonts.getRobotoRegular() : XgramExpressive.typeface(TS, false);
      if (paint.getTypeface() != typeface) {
        paint.setTypeface(typeface);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        final float tracking = XgramExpressive.trackingSp(TS) / XgramExpressive.textSizeSp(TS);
        if (paint.getLetterSpacing() != tracking) {
          paint.setLetterSpacing(tracking);
        }
      }
      return paint;
    }
  }

  private final @DrawableRes int typeIconRes;

  private final boolean hasLead;
  private final @DrawableRes int leadIconRes;
  private final @Nullable EmojiInfo leadInfo;
  private final @Nullable String leadGlyph;

  private final CharSequence label;
  private final boolean labelEmpty, rtl, fakeBold;

  private float naturalMax = -1f, naturalMin = -1f, leastMin = -1f;

  /** The label laid out for one cell width; replaced as a whole, so the UI thread never sees half of it. */
  private static final class Line {
    final int cellWidth;
    final @Nullable Layout layout;
    final float left, width;
    /** Whether the type's hint is shown (leading without an emoji, trailing with one), and the icons' gap (px). */
    final boolean typeShown;
    final float gap;

    Line (int cellWidth, @Nullable Layout layout, float left, float width, boolean typeShown, float gap) {
      this.cellWidth = cellWidth;
      this.layout = layout;
      this.left = left;
      this.width = width;
      this.typeShown = typeShown;
      this.gap = gap;
    }
  }

  private volatile @Nullable Line line;
  private final Rect emojiRect = new Rect();
  private final Paint.FontMetrics glyphMetrics = new Paint.FontMetrics();

  /**
   * @param text the button's text (Telegram X's cleanButtonText); line breaks become spaces
   * @param typeIconRes the button type's hint ({@link #ICON} slot), 0 for none
   */
  public XgramBotButtonLabel (@NonNull String text, @DrawableRes int typeIconRes) {
    final String s = text.replace('\n', ' ');
    this.typeIconRes = typeIconRes;

    final int length = s.length();
    int start = 0;
    while (start < length && isSpace(s.charAt(start))) {
      start++;
    }

    // The leading emoji: a mapped symbol with its selectors and skin tones, else any emoji Telegram X can draw
    int end = -1;
    int iconRes = 0;
    int codePoint = 0;
    if (start < length) {
      codePoint = s.codePointAt(start);
      iconRes = TABLE.get(codePoint, 0);
      if (iconRes != 0) {
        end = start + Character.charCount(codePoint);
        while (end < length) {
          final int next = s.codePointAt(end);
          if (next == 0xFE0F || next == 0xFE0E || (next >= 0x1F3FB && next <= 0x1F3FF)) {
            end += Character.charCount(next);
          } else {
            break;
          }
        }
        if (end < length && s.charAt(end) == '\u200D') {
          // A sequence of its own (❤️‍🔥 is not ❤️): drawn as the emoji it is
          iconRes = 0;
          end = -1;
        }
      }
      if (end == -1) {
        final int emojiEnd = XgramFolderIcons.emojiEnd(s, start);
        if (emojiEnd != -1 && Emoji.instance().getEmojiInfo(s.substring(start, emojiEnd)) != null) {
          end = emojiEnd;
        }
      }
    }

    this.hasLead = end != -1;
    this.leadIconRes = iconRes;
    EmojiInfo info = null;
    String glyph = null;
    if (hasLead) {
      final String lead = s.substring(start, end);
      if (iconRes != 0 && isTextArrow(codePoint)) {
        glyph = lead;
      } else {
        info = Emoji.instance().getEmojiInfo(lead);
        if (info == null && iconRes != 0 && lead.indexOf('\uFE0F') == -1) {
          // A symbol sent without its emoji variation selector (✔, ✖): the emoji set has it with one
          info = Emoji.instance().getEmojiInfo(new String(Character.toChars(codePoint)) + '\uFE0F');
        }
        if (info == null) {
          glyph = lead;
        }
      }
    }
    this.leadInfo = info;
    this.leadGlyph = glyph;

    // The rest of the label; emoji in its middle or at its end stay inline
    int labelStart = hasLead ? end : start;
    while (labelStart < length && isSpace(s.charAt(labelStart))) {
      labelStart++;
    }
    int labelEnd = length;
    while (labelEnd > labelStart && isSpace(s.charAt(labelEnd - 1))) {
      labelEnd--;
    }
    final String plain = s.substring(labelStart, labelEnd);
    this.labelEmpty = plain.isEmpty();
    this.label = labelEmpty ? "" : Emoji.instance().replaceEmoji(plain);
    this.fakeBold = Text.needFakeBold(plain);

    // Reading order: the label's own direction, the interface's when it has no letters (digits, emoji)
    boolean rtl = Lang.rtl();
    if (!labelEmpty && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      final boolean asLtr = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(plain, 0, plain.length());
      final boolean asRtl = TextDirectionHeuristics.FIRSTSTRONG_RTL.isRtl(plain, 0, plain.length());
      if (asLtr == asRtl) {
        rtl = asLtr;
      }
    }
    this.rtl = rtl;
  }

  public @DrawableRes int typeIconRes () {
    return typeIconRes;
  }

  /**
   * The cell width (px) the button wants with the usual padding: the label at 14dp with every icon, or with
   * {@code smallest} the label at 12dp without the type's hint (a decoration never widens the keyboard).
   */
  public float naturalWidth (boolean smallest) {
    float value = smallest ? naturalMin : naturalMax;
    if (value < 0f) {
      value = measure(smallest ? STEPS - 1 : 0, Screen.dpf(PADDING), Screen.dpf(GAP), !smallest);
      if (smallest) {
        naturalMin = value;
      } else {
        naturalMax = value;
      }
    }
    return value;
  }

  /** The least cell width (px) that still shows the whole label: 12dp, {@link #PADDING_MIN}, {@link #GAP_MIN}, no type hint. */
  public float leastWidth () {
    float value = leastMin;
    if (value < 0f) {
      leastMin = value = measure(STEPS - 1, Screen.dpf(PADDING_MIN), Screen.dpf(GAP_MIN), false);
    }
    return value;
  }

  private float measure (int step, float padding, float gap, boolean withType) {
    final float icon = Screen.dpf(ICON);
    float value = padding * 2f;
    if (labelEmpty) {
      // Icons only: without an emoji the type's hint is the button's content, so it is always counted then
      final int slots = (hasLead ? 1 : 0) + (typeIconRes != 0 && (withType || !hasLead) ? 1 : 0);
      value += slots * icon + Math.max(0, slots - 1) * gap;
    } else {
      final int slots = (hasLead ? 1 : 0) + (withType && typeIconRes != 0 ? 1 : 0);
      value += slots * (icon + gap) + Layout.getDesiredWidth(label, paint(step, fakeBold));
    }
    // Whole pixels: a cell given exactly this width (cellWidths keeps whole widths whole) still fits the label
    return (float) Math.ceil(value);
  }

  /** The label's room (px) in a cell with {@code padding} on both sides and {@code slots} icons, each {@code gap} from it. */
  private static float labelRoom (int cellWidth, float padding, float gap, int slots) {
    return cellWidth - padding * 2f - slots * (Screen.dpf(ICON) + gap);
  }

  /**
   * Lays the label out for a cell of {@code cellWidth} px: the largest size that fits on one line with the usual
   * padding, else 12dp with the padding (then the icon gap) down to its least, else 12dp with an ellipsis.
   */
  public void layout (int cellWidth) {
    final Line current = line;
    if (current == null || current.cellWidth != cellWidth) {
      line = buildLine(cellWidth);
    }
  }

  private Line buildLine (int cellWidth) {
    final float padding = Screen.dpf(PADDING), paddingMin = Screen.dpf(PADDING_MIN);
    final float gap = Screen.dpf(GAP), gapMin = Screen.dpf(GAP_MIN);
    final boolean hasType = typeIconRes != 0;
    final int lead = hasLead ? 1 : 0;
    if (labelEmpty) {
      // Icons only: without an emoji the type's hint is the content; beside one it goes from a cell too narrow for both
      final boolean typeShown = hasType && (!hasLead || cellWidth - paddingMin * 2f + .5f >= Screen.dpf(ICON) * 2f + gap);
      return new Line(cellWidth, null, 0f, 0f, typeShown, gap);
    }
    final TextPaint smallestPaint = paint(STEPS - 1, fakeBold);
    final float smallestWidth = Layout.getDesiredWidth(label, smallestPaint);

    // The type's hint is a decoration, wherever it sits. It stays while the label fits whole at 12dp beside it with
    // the usual padding. Otherwise it goes when that keeps the label whole (before the padding gives way), and beside
    // a label that ends with an ellipsis anyway it stays only while it leaves the label some room
    boolean typeShown = hasType;
    if (typeShown && labelRoom(cellWidth, padding, gap, lead + 1) + .01f < smallestWidth) {
      if (labelRoom(cellWidth, paddingMin, gapMin, lead) + .01f >= smallestWidth ||
        labelRoom(cellWidth, paddingMin, gapMin, lead + 1) + .5f < Math.min(Screen.dpf(LABEL_MIN_ROOM), smallestWidth)) {
        typeShown = false;
      }
    }
    final int slots = lead + (typeShown ? 1 : 0);

    CharSequence text = label;
    TextPaint paint = null;
    float lineGap = gap;
    float room = labelRoom(cellWidth, padding, gap, slots);
    for (int step = 0; step < STEPS; step++) {
      final TextPaint candidate = paint(step, fakeBold);
      // Measured for real at every size: text does not scale linearly (hinting, the font's optical size). A hair
      // of tolerance: a cell given exactly its natural width keeps the full size
      if (Layout.getDesiredWidth(label, candidate) <= room + .01f) {
        paint = candidate;
        break;
      }
    }
    if (paint == null) {
      // Too long at 12dp: the padding gives way first, down to Telegram X's, then the icons' gap. draw() centres the
      // content, so whatever is left stays as equal padding on both sides
      paint = smallestPaint;
      room = labelRoom(cellWidth, paddingMin, gap, slots);
      if (room + .01f < smallestWidth && slots > 0) {
        lineGap = gapMin;
        room = labelRoom(cellWidth, paddingMin, gapMin, slots);
      }
      if (room + .01f < smallestWidth) {
        // Still too long: it ends with an ellipsis
        text = room >= 1f ? TextUtils.ellipsize(label, paint, room, TextUtils.TruncateAt.END) : "";
        if (text.length() == 0) {
          if (hasLead) {
            // Not even a letter and an ellipsis fit beside the emoji: the emoji alone
            return new Line(cellWidth, null, 0f, 0f, false, lineGap);
          }
          // Nor in the whole cell: the whole label, cut at the cell's edges by draw(), rather than a blank button
          text = label;
          typeShown = false;
        }
      }
    }
    // As wide as the room (never narrower than the text), so it never breaks a line; the line's own bounds place it
    // next to the icons
    final int layoutWidth = Math.max(1, (int) Math.ceil(Math.max(room, Layout.getDesiredWidth(text, paint))));
    final Layout layout = build(text, paint, layoutWidth);
    if (layout.getLineCount() == 0) {
      return new Line(cellWidth, null, 0f, 0f, typeShown, lineGap);
    }
    final float left = layout.getLineLeft(0), right = layout.getLineRight(0);
    return new Line(cellWidth, layout, left, Math.max(0f, Math.min(layoutWidth, right - left)), typeShown, lineGap);
  }

  private static Layout build (CharSequence text, TextPaint paint, int width) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setIncludePad(false)
        .setMaxLines(1)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setEllipsizedWidth(width)
        .build();
    }
    //noinspection deprecation
    return new StaticLayout(text, 0, text.length(), paint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false, TextUtils.TruncateAt.END, width);
  }

  /**
   * Draws the content centred in the cell, [lead][label][type] in the label's reading order.
   *
   * @param icons the button, whose drawable cache holds the icons
   * @param content the content colour (onSecondaryContainer), read from the theme by the caller every frame
   * @param alpha the content's opacity (it fades out while the button waits for an answer)
   * @param materialIcons "Material icons on bot buttons"
   */
  public void draw (@NonNull Canvas c, @NonNull DrawableProvider icons, int cellLeft, int cellTop, int cellWidth, int cellHeight, @ColorInt int content, float alpha, boolean materialIcons) {
    final Line line = this.line;
    if (line == null || alpha <= 0f) {
      return;
    }
    alpha = Math.min(1f, alpha);
    final int color = ColorUtils.alphaColor(alpha, content);
    final float icon = Screen.dpf(ICON), gap = line.gap;

    // Offsets in reading order; the type's hint leads without an emoji and trails beside one
    float unit = 0f, leadAt = -1f, labelAt = -1f, trailAt = -1f;
    if (hasLead || line.typeShown) {
      leadAt = 0f;
      unit = icon;
    }
    if (line.layout != null) {
      if (unit > 0f) {
        unit += gap;
      }
      labelAt = unit;
      unit += line.width;
    }
    if (hasLead && line.typeShown) {
      if (unit > 0f) {
        unit += gap;
      }
      trailAt = unit;
      unit += icon;
    }
    if (unit <= 0f) {
      return;
    }

    final float startX = cellLeft + Math.max(0f, (cellWidth - unit) / 2f);
    final float centerY = cellTop + cellHeight / 2f;
    // Only icons with no label, or a label that could not even end with an ellipsis, can be wider than a very narrow
    // cell: they never draw over the next one
    final boolean clip = unit > cellWidth;
    final int saveCount = clip ? c.save() : -1;
    if (clip) {
      c.clipRect(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight);
    }

    if (leadAt >= 0f) {
      final float cx = x(startX, unit, leadAt, icon) + icon / 2f;
      if (hasLead) {
        drawLead(c, icons, line, cx, centerY, color, alpha, materialIcons);
      } else {
        drawIcon(c, icons, typeIconRes, cx, centerY, color);
      }
    }
    if (labelAt >= 0f && line.layout != null) {
      final Layout layout = line.layout;
      layout.getPaint().setColor(color);
      final int labelSave = c.save();
      // Centred on the cell from the layout's own height, not placed on a fixed baseline
      c.translate(x(startX, unit, labelAt, line.width) - line.left, centerY - layout.getHeight() / 2f);
      layout.draw(c);
      c.restoreToCount(labelSave);
    }
    if (trailAt >= 0f) {
      drawIcon(c, icons, typeIconRes, x(startX, unit, trailAt, icon) + icon / 2f, centerY, color);
    }

    if (clip) {
      c.restoreToCount(saveCount);
    }
  }

  /** The left edge of a part at {@code offset} (reading order) in the unit; mirrored for a right-to-left label. */
  private float x (float startX, float unit, float offset, float width) {
    return rtl ? startX + unit - offset - width : startX + offset;
  }

  private void drawLead (Canvas c, DrawableProvider icons, Line line, float cx, float cy, int color, float alpha, boolean materialIcons) {
    if (materialIcons && leadIconRes != 0) {
      drawIcon(c, icons, leadIconRes, cx, cy, color);
      return;
    }
    final float half = Screen.dpf(ICON) / 2f;
    if (leadInfo != null) {
      // The emoji itself at 18dp (the emoji set, or System emoji); one not loaded yet is redrawn by the emoji listener
      emojiRect.set(Math.round(cx - half), Math.round(cy - half), Math.round(cx + half), Math.round(cy + half));
      Emoji.instance().draw(c, leadInfo, emojiRect, Math.round(255f * alpha));
    } else if (leadGlyph != null) {
      final TextPaint paint = line.layout != null ? line.layout.getPaint() : paint(0, fakeBold);
      paint.setColor(color);
      paint.getFontMetrics(glyphMetrics);
      final float width = paint.measureText(leadGlyph);
      c.drawText(leadGlyph, cx - width / 2f, cy - (glyphMetrics.ascent + glyphMetrics.descent) / 2f, paint);
    }
  }

  /** A Material icon in the 18dp slot, tinted with the content colour (the pattern of XgramR6B4Media's icons). */
  private static void drawIcon (Canvas c, DrawableProvider icons, @DrawableRes int res, float cx, float cy, int color) {
    if (res == 0) {
      return;
    }
    final Drawable drawable = icons.getSparseDrawable(res, ColorId.NONE);
    if (drawable == null) {
      return;
    }
    final int width = drawable.getMinimumWidth(), height = drawable.getMinimumHeight();
    final int size = Math.max(width, height);
    if (size <= 0) {
      return;
    }
    final float scale = Screen.dpf(ICON) / size;
    final int saveCount = c.save();
    if (scale != 1f) {
      c.scale(scale, scale, cx, cy);
    }
    Drawables.draw(c, drawable, cx - width / 2f, cy - height / 2f, Paints.getPorterDuffPaint(color));
    c.restoreToCount(saveCount);
  }

  /**
   * Splits {@code total} px between a row's cells into {@code out}, whole pixels that add up to {@code total} (the
   * last cell takes the remainder, so every row ends at the keyboard's edge under the shared outer corner).
   * Equal while every button fits its share, Telegram's grid. Otherwise each gets its natural width plus an equal
   * share of what is left; when even that is too much, the short ones keep their natural width and the long ones
   * share the rest, never below their 12dp width with the usual padding while that fits. When even that is too much,
   * every cell gives up the same part of its padding (down to {@link #leastWidth}), so labels shrink and the padding
   * gives way evenly before any label ends with an ellipsis.
   *
   * @param least each cell's {@link #leastWidth}
   * @param smallest each cell's {@link #naturalWidth} at 12dp
   * @param natural each cell's {@link #naturalWidth} at 14dp
   */
  public static void cellWidths (@NonNull float[] least, @NonNull float[] smallest, @NonNull float[] natural, int total, @NonNull int[] out) {
    final int count = natural.length;
    if (count == 0) {
      return;
    }
    float maxNatural = 0f, sumNatural = 0f, sumSmallest = 0f, sumLeast = 0f;
    for (int i = 0; i < count; i++) {
      maxNatural = Math.max(maxNatural, natural[i]);
      sumNatural += natural[i];
      sumSmallest += smallest[i];
      sumLeast += least[i];
    }
    final float[] widths = new float[count];
    if (maxNatural * count <= total) {
      for (int i = 0; i < count; i++) {
        widths[i] = total / (float) count;
      }
    } else if (sumNatural <= total) {
      final float extra = (total - sumNatural) / count;
      for (int i = 0; i < count; i++) {
        widths[i] = natural[i] + extra;
      }
    } else {
      // The floor no cell goes below while the floors fit the row: the 12dp width with the usual padding, else the
      // same share of it over the least width for every cell; none when even the least widths are too much
      final float[] floors = new float[count];
      if (sumLeast <= total) {
        final float share = sumSmallest > sumLeast ? Math.min(1f, (total - sumLeast) / (sumSmallest - sumLeast)) : 1f;
        for (int i = 0; i < count; i++) {
          // Whole pixels, so the rounding below never takes a pixel a label needs
          floors[i] = Math.min(natural[i], (float) Math.floor(least[i] + (smallest[i] - least[i]) * share));
        }
      }
      // The cap where the widths add up to the row: found by bisection (a row has a few cells)
      float low = 0f, high = maxNatural;
      for (int iteration = 0; iteration < 24; iteration++) {
        final float cap = (low + high) / 2f;
        float sum = 0f;
        for (int i = 0; i < count; i++) {
          sum += capped(floors[i], natural[i], cap);
        }
        if (sum > total) {
          high = cap;
        } else {
          low = cap;
        }
      }
      for (int i = 0; i < count; i++) {
        widths[i] = capped(floors[i], natural[i], low);
      }
    }
    float position = 0f;
    int x = 0;
    for (int i = 0; i < count; i++) {
      position += widths[i];
      final int end = i == count - 1 ? total : Math.min(total, Math.round(position));
      out[i] = Math.max(0, end - x);
      x = Math.max(x, end);
    }
  }

  private static float capped (float floor, float natural, float cap) {
    return Math.max(floor, Math.min(natural, cap));
  }
}
