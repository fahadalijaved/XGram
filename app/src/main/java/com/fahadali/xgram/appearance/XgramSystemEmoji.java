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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiCode;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import me.vkryl.core.lambda.RunnableBool;

/**
 * System emoji: emoji drawn with the phone's own emoji font instead of an emoji set's images.
 * Telegram X still finds emoji in text as before, so layout, big emoji and sticker suggestions don't change:
 * only {@code Emoji.draw} asks {@link #draw} first. An emoji the phone can't draw as one picture keeps the set's
 * image, and the set chosen before System stays underneath for that. Shown as "System" in the emoji set screen.
 */
public final class XgramSystemEmoji {
  private XgramSystemEmoji () { }

  public static final String PACK_ID = "xgram_system";

  /** Paint.hasGlyph (Android 6) tells which emoji the phone has; without it missing ones would come out as boxes. */
  public static boolean isSupported () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
  }

  /** Read on every emoji draw. */
  public static boolean isEnabled () {
    return XgramSettings.instance().systemEmoji() && isSupported();
  }

  public static void setEnabled (boolean enabled) {
    if (XgramSettings.instance().systemEmoji() == enabled)
      return;
    XgramSettings.instance().setSystemEmoji(enabled);
    if (enabled) {
      Emoji.instance().releasePackImages();
    }
    TGLegacyManager.instance().notifyEmojiChanged(true);
  }

  /** An emoji set picked in the emoji set screen: System goes off and the set comes in. */
  public static void applyPack (Settings.EmojiPack pack) {
    boolean wasEnabled = XgramSettings.instance().systemEmoji();
    boolean samePack = pack.identifier.equals(Settings.instance().getEmojiPackIdentifier());
    if (wasEnabled) {
      XgramSettings.instance().setSystemEmoji(false);
    }
    Emoji.instance().changeEmojiPack(pack);
    if (wasEnabled && samePack) {
      // changeEmojiPack only redraws when the set changes
      TGLegacyManager.instance().notifyEmojiChanged(true);
    }
  }

  public static boolean isSystemPack (@Nullable Settings.CloudSetting setting) {
    return setting != null && PACK_ID.equals(setting.identifier);
  }

  private static SystemPack pack;

  public static synchronized Settings.EmojiPack pack () {
    if (pack == null) {
      pack = new SystemPack();
    }
    pack.displayName = Lang.getString(R.string.XgramEmojiSystem);
    return pack;
  }

  /** Adds System right after Default. Runs on the thread that fetched the emoji sets. */
  public static List<Settings.EmojiPack> addTo (List<Settings.EmojiPack> packs) {
    if (!isSupported())
      return packs;
    List<Settings.EmojiPack> result = packs != null ? new ArrayList<>(packs) : new ArrayList<>();
    int index = 0;
    boolean foundBuiltIn = false;
    for (int i = 0; i < result.size(); i++) {
      Settings.EmojiPack setting = result.get(i);
      if (isSystemPack(setting))
        return result;
      if (!foundBuiltIn && setting.isBuiltIn()) {
        foundBuiltIn = true;
        index = i + 1;
      }
    }
    result.add(index, pack());
    return result;
  }

  private static final class SystemPack extends Settings.EmojiPack {
    SystemPack () {
      super(PACK_ID);
      this.version = BuildConfig.EMOJI_VERSION;
    }

    @Override
    public boolean isBuiltIn () {
      return false;
    }

    @Override
    public int getInstallState (boolean fast) {
      return STATE_INSTALLED;
    }

    @Override
    public void install (@NonNull RunnableBool callback) {
      callback.runWithBool(true);
    }
  }

  // Drawing

  /** Glyphs are measured once at this size and scaled on draw. */
  private static final float BASE_SIZE = 100f;
  /** Share of the emoji square that the phone's 😀 takes, close to how the built-in set fills it. */
  private static final float FILL = .95f;
  private static final String REFERENCE = "\uD83D\uDE00";
  private static final String[] PREVIEW = {"\uD83D\uDE00", "\uD83D\uDE09", "\uD83D\uDE14", "\uD83D\uDE28"}; // as R.string.EmojiPreview

  private static final class Glyph {
    final String text;
    final float centerX, size;

    Glyph (String text, float centerX, float size) {
      this.text = text;
      this.centerX = centerX;
      this.size = size;
    }
  }

  private static final Glyph MISSING = new Glyph("", 0, 0);
  private static final IdentityHashMap<EmojiInfo, Glyph> glyphs = new IdentityHashMap<>();
  private static final Rect bounds = new Rect(), previewRect = new Rect();
  private static TextPaint paint;
  private static float referenceSize, referenceCenterY;

  /**
   * Draws the emoji with the phone's font into the square Telegram X laid out for it.
   * Returns false when the phone has no single glyph for it, and the caller draws the set's image.
   */
  public static synchronized boolean draw (@NonNull Canvas c, @Nullable EmojiInfo info, @NonNull Rect rect, int alpha) {
    Glyph glyph = glyph(info);
    if (glyph == null)
      return false;
    drawGlyph(c, glyph, rect, alpha);
    return true;
  }

  /** The System row's preview: the phone's emoji even while a set is in use. */
  public static synchronized void drawPreview (@NonNull Canvas c, int left, int top, int right, int bottom) {
    int gap = Screen.dp(2f);
    int cellWidth = (right - left - gap) / 2;
    int cellHeight = (bottom - top - gap) / 2;
    if (cellWidth <= 0 || cellHeight <= 0)
      return;
    for (int i = 0; i < PREVIEW.length; i++) {
      Glyph glyph = glyph(Emoji.instance().getEmojiInfo(PREVIEW[i]));
      if (glyph != null) {
        int x = left + (i % 2) * (cellWidth + gap);
        int y = top + (i / 2) * (cellHeight + gap);
        previewRect.set(x, y, x + cellWidth, y + cellHeight);
        drawGlyph(c, glyph, previewRect, 255);
      }
    }
  }

  private static void drawGlyph (Canvas c, Glyph glyph, Rect rect, int alpha) {
    int box = Math.min(rect.width(), rect.height());
    if (box <= 0 || alpha <= 0)
      return;
    TextPaint paint = paint();
    // Phone emoji keep their sizes relative to each other; only one wider than the square is fitted into it
    float scale = FILL * box / referenceSize;
    if (glyph.size * scale > box) {
      scale = box / glyph.size;
    }
    paint.setTextSize(BASE_SIZE * scale);
    // Colour emoji ignore the colour; a plain one (rare) takes the text colour
    paint.setColor((Theme.textAccentColor() & 0xffffff) | (Math.min(alpha, 255) << 24));
    c.drawText(glyph.text, rect.exactCenterX() - glyph.centerX * scale, rect.exactCenterY() - referenceCenterY * scale, paint);
  }

  @Nullable
  private static Glyph glyph (@Nullable EmojiInfo info) {
    if (info == null)
      return null;
    Glyph glyph = glyphs.get(info);
    if (glyph == null) {
      glyph = measure(code(info));
      glyphs.put(info, glyph);
    }
    return glyph != MISSING ? glyph : null;
  }

  /** Telegram X's emoji table entry behind the info (the inverse of the Emoji constructor). */
  @Nullable
  private static String code (EmojiInfo info) {
    if (info.section < 0 || info.section >= EmojiData.data.length)
      return null;
    String[] section = EmojiData.data[info.section];
    int perPage = (int) Math.ceil(section.length / (float) EmojiCode.SPLIT_COUNT);
    int index = info.page * perPage + info.position;
    return index >= 0 && index < section.length ? section[index] : null;
  }

  private static Glyph measure (@Nullable String code) {
    if (code == null || code.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
      return MISSING;
    TextPaint paint = paint();
    String qualified = qualify(code);
    String text;
    if (paint.hasGlyph(qualified)) {
      text = qualified;
    } else if (!qualified.equals(code) && paint.hasGlyph(code)) {
      text = code;
    } else {
      return MISSING;
    }
    paint.setTextSize(BASE_SIZE);
    paint.getTextBounds(text, 0, text.length(), bounds);
    float advance = paint.measureText(text);
    float size = Math.max(bounds.width(), bounds.height());
    if (size <= 0) {
      size = advance > 0 ? advance : BASE_SIZE;
    }
    float centerX = advance > 0 ? advance / 2f : bounds.exactCenterX();
    return new Glyph(text, centerX, size);
  }

  private static TextPaint paint () {
    if (paint == null) {
      TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      p.setTypeface(Typeface.DEFAULT);
      p.setTextSize(BASE_SIZE);
      p.getTextBounds(REFERENCE, 0, REFERENCE.length(), bounds);
      float size = Math.max(bounds.width(), bounds.height());
      if (size > 0) {
        referenceSize = size;
        referenceCenterY = bounds.exactCenterY();
      } else {
        Paint.FontMetrics metrics = p.getFontMetrics();
        referenceSize = BASE_SIZE;
        referenceCenterY = (metrics.ascent + metrics.descent) / 2f;
      }
      paint = p;
    }
    return paint;
  }

  // Telegram X's emoji table has no U+FE0F, so ☺, ❤, © or keycaps would come out as plain text symbols
  // and some joined emoji would fall apart. This builds the fully-qualified form the phone's font knows.

  static String qualify (String code) {
    StringBuilder b = new StringBuilder(code.length() + 4);
    int length = code.length();
    for (int i = 0; i < length; ) {
      int codePoint = code.codePointAt(i);
      i += Character.charCount(codePoint);
      b.appendCodePoint(codePoint);
      int next = i < length ? code.codePointAt(i) : 0;
      if (needsEmojiSelector(codePoint) && next != 0xFE0F && !isSkinTone(next)) {
        b.append('\uFE0F');
      }
    }
    return b.toString();
  }

  private static boolean isSkinTone (int codePoint) {
    return codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
  }

  private static boolean needsEmojiSelector (int codePoint) {
    if (codePoint == 0x200D || codePoint == 0x20E3 || codePoint == 0xFE0F || isSkinTone(codePoint) ||
      (codePoint >= 0xE0020 && codePoint <= 0xE007F) || // tags (flags of England, Scotland, Wales)
      (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)) { // regional indicators (flags)
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return !android.icu.lang.UCharacter.hasBinaryProperty(codePoint, android.icu.lang.UProperty.EMOJI_PRESENTATION);
    }
    // Close enough before Android 9: Paint.hasGlyph has the last word and the raw form is tried next
    return codePoint < 0x1F000;
  }
}
