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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Colors;
import com.fahadali.xgram.appearance.XgramMotion;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

/**
 * Telegram's pull for the hidden archive (Chat list &gt; Archive: Hidden, Telegram X's Hide from Chat List). While the
 * chat list is pulled down from the top, the archive row coming into sight is covered by "Swipe down for archive" and
 * an arrow in the archive photo's place, on a neutral color. Past {@link #THRESHOLD} of the row in sight the archive's
 * color fills the row from the photo, the arrow turns into the archive icon, the text reads "Release for archive" and
 * a tick is felt (and again when the pull goes back under it). Let go past it and the archive stays: the color shrinks
 * into the archive's photo and the row shows, a row like any other until it is scrolled away (Telegram X's rule). Let
 * go before it and the archive hides again. The pull is harder the more of the row shows ({@link #pullFactor}), as in
 * Telegram. With Reduce motion its springs snap: the arrow, text and color swap at once, and a release past the
 * threshold shows the row straight away.
 * <p>
 * One per chat list that can hide its archive (ChatsController, which decides when it is pulled, shown or gone);
 * the archive row's ChatView draws it over itself ({@link #draw}). Main thread only.
 */
public final class XgramArchivePull {
  /** Telegram's snap height: the share of the archive row in sight that keeps it once the finger lets go. */
  public static final float THRESHOLD = .85f;
  /** Telegram's pull parallax: the list follows .45 of the finger at first, .2 with the whole row in sight. */
  private static final float PARALLAX_START = .45f, PARALLAX_END = .25f;

  private final View list;
  @Nullable private View target;
  /** Let go past the threshold: a row again, until it leaves the screen. */
  private boolean shown;
  /** Past the threshold in the current pull (kept from the moment the finger lets go). */
  private boolean ready;
  /** 0 neutral with the arrow, 1 the ready color (the archive's; primary with Expressive design) with its icon. */
  private final XgramExpressive.Spring readyFactor;
  /** After a release past the threshold: 0 the whole row in the ready color, 1 shrunk into the photo (and gone). */
  private final XgramExpressive.Spring settleFactor;
  /** Let go before the color filled the row: the shrink waits for {@link #readyFactor} to reach 1 (no jump). */
  private boolean settlePending;

  public XgramArchivePull (View list) {
    this.list = list;
    this.readyFactor = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> invalidate());
    this.settleFactor = new XgramExpressive.Spring(XgramExpressive.EFFECTS_SLOW, 1f, (s, value) -> invalidate());
    this.settleFactor.setEndListener(this::invalidate);
    this.readyFactor.setEndListener(() -> {
      if (settlePending) {
        settlePending = false;
        settleFactor.animateTo(1f);
      }
    });
  }

  /**
   * The share of the finger's way the list moves while {@code fraction} of the row shows (pulling down). The caller
   * carries the rest of each step over to the next one, so a slow pull meets the same resistance as a fast one.
   */
  public static float pullFactor (float fraction) {
    return PARALLAX_START - PARALLAX_END * XgramMotion.clamp01(fraction);
  }

  /** Let go past the threshold: the archive stays. */
  public boolean isShown () {
    return shown;
  }

  /** Past the threshold (while pulling, or when the finger let go). */
  public boolean isReady () {
    return ready;
  }

  private boolean active;

  /** The list hides its archive now (Hidden, and nothing above the archive row): only then is there a pull. */
  public void setActive (boolean active) {
    if (this.active != active) {
      this.active = active;
      reset();
      invalidate();
    }
  }

  public boolean isActive () {
    return active;
  }

  /** Something to draw over the archive row: the pull, or the release still shrinking into the photo. */
  public boolean isDrawing () {
    return active && (!shown || settleFactor.getValue() < 1f);
  }

  /** The finger pulls with {@code fraction} (0..1) of the archive row in sight; {@code row}: the row, when laid out. */
  public void onPull (float fraction, @Nullable View row) {
    if (row != null) {
      target = row;
    }
    if (!active || shown) {
      return;
    }
    final boolean ready = fraction >= THRESHOLD;
    if (this.ready != ready) {
      this.ready = ready;
      com.fahadali.xgram.XgramHaptics.gestureThreshold(list, ready); // armed: Android 14's threshold haptic (a tap before); let go: 14+ only
      readyFactor.animateTo(ready ? 1f : 0f);
    }
    invalidate();
  }

  /** The list moved without the finger (a snap or a fling): the row redraws where it is now. */
  public void onMoved (@Nullable View row) {
    if (row != null) {
      target = row;
      if (isDrawing()) {
        row.invalidate();
      }
    }
  }

  /**
   * The archive stays: let go past the threshold ({@code animated}, the color shrinks into the photo), or it was in
   * sight already (hidden while showing: no pull is drawn while it goes).
   */
  public void show (boolean animated) {
    if (shown) {
      return;
    }
    shown = true;
    if (animated) {
      ready = true;
      settleFactor.setValue(0f);
      if (readyFactor.getValue() >= 1f) {
        settleFactor.animateTo(1f);
      } else {
        // Let go before the color filled the row: the pull goes on until it has, then shrinks into the photo
        settlePending = true;
        readyFactor.animateTo(1f);
      }
    } else {
      settlePending = false;
      settleFactor.setValue(1f);
    }
    invalidate();
  }

  /**
   * The archive left the screen, or hiding it was turned on or off: the next pull starts over. Returns whether there
   * was something to start over (kept, or past the threshold).
   */
  public boolean reset () {
    if (!shown && !ready && readyFactor.getValue() == 0f) {
      return false;
    }
    shown = false;
    ready = false;
    settlePending = false;
    readyFactor.setValue(0f);
    settleFactor.setValue(1f);
    invalidate();
    return true;
  }

  private void invalidate () {
    if (target != null) {
      target.invalidate();
    }
  }

  // Drawing

  @Nullable private Drawable arrow, archive;
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private int iconPaintColor;
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  // The two texts, ellipsized for the last width and language
  @Nullable private CharSequence pullText, releaseText;
  private float pullTextWidth, releaseTextWidth;
  private int textCacheWidth = -1;
  private boolean textCacheExpressive;
  @Nullable private String textCacheSource;

  /**
   * Draws the pull over the archive row {@code view} ({@code width} x {@code height}), whose photo is at
   * avatarLeft..avatarBottom with corners of {@code avatarRadius}, its title starting {@code textStart} from the start
   * edge, its photo's glyph at {@code badgeScale} (smaller with the one line photo). Only the part of the row below the
   * list's top is in sight; the pull fits itself in it.
   */
  public void draw (View view, Canvas c, int width, int height, float avatarLeft, float avatarTop, float avatarRight, float avatarBottom,
                    float avatarRadius, int textStart, boolean rtl, float badgeScale) {
    // Let go before the color filled the row: still the pull until it has (settlePending), then the shrink
    final boolean settling = shown && !settlePending;
    final float settle = settling ? XgramMotion.clamp01(settleFactor.getValue()) : 0f;
    if (settle >= 1f || width <= 0 || height <= 0) {
      return;
    }
    target = view;
    final View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
    final float visibleTop = parent != null ? Math.max(0f, Math.min(height, parent.getPaddingTop() - view.getTop() - view.getTranslationY())) : 0f;
    final float visible = height - visibleTop;
    if (visible <= 0f) {
      return;
    }
    final boolean expressive = XgramExpressive.isEnabled();
    final boolean reduceMotion = XgramMotion.reduce();
    final float readyF = XgramMotion.clamp01(readyFactor.getValue());

    // Colors: the archive's photo (its color and glyph, both looks map them), on a neutral color until it is ready.
    // Expressive design's tonal archive photo is as light as the neutral color, so its pull is in the primary color
    // (Telegram's gray to blue) and only the shrink turns it into the photo's.
    final int neutralColor = expressive ? XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST) : Theme.getColor(ColorId.avatarArchivePinned);
    final int archiveColor = Theme.getColor(ColorId.avatarArchive);
    final int glyphColor = Theme.getColor(XgramM3Colors.avatarIconColorId(TdlibAccentColor.InternalId.ARCHIVE, ColorId.avatar_content));
    final int readyColor = expressive ? XgramExpressive.color(XgramExpressive.PRIMARY) : archiveColor;
    final int readyContent = expressive ? XgramExpressive.color(XgramExpressive.ON_PRIMARY) : glyphColor;
    final int neutralTextColor = expressive ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.avatar_content);
    final int textColor = ColorUtils.fromToArgb(neutralTextColor, readyContent, readyF);

    // The photo's place: the circle sits in the middle of what shows until it reaches the photo, growing with it; on
    // release it grows into the photo and moves onto it
    final float fullRadius = Math.max(1f, (avatarRight - avatarLeft) / 2f);
    final float cx = (avatarLeft + avatarRight) / 2f;
    final float avatarCy = (avatarTop + avatarBottom) / 2f;
    final float pullCy = Math.max(avatarCy, visibleTop + visible / 2f);
    final float pullRadius = Math.max(0f, Math.min(fullRadius, visible / 2f - Screen.dp(6f)));
    final float cy = settling ? XgramMotion.lerp(pullCy, avatarCy, settle) : pullCy;
    final float radius = settling ? XgramMotion.lerp(pullRadius, fullRadius, settle) : pullRadius;
    // The photo's own glyph size (AvatarReceiver: 24dp at the row's badge scale)
    final float glyphScale = (radius / fullRadius) * badgeScale;

    // Released: everything fades out over the last part, when the color has become the photo under it
    final float alpha = settle <= .7f ? 1f : 1f - (settle - .7f) / .3f;
    final int saveCount = c.save();
    c.clipRect(0, visibleTop, width, height);

    if (settling) {
      // The row's color shrinks into the photo, its corners turning into the photo's and its color into the photo's
      rect.set(
        XgramMotion.lerp(0f, avatarLeft, settle), XgramMotion.lerp(visibleTop, avatarTop, settle),
        XgramMotion.lerp(width, avatarRight, settle), XgramMotion.lerp(height, avatarBottom, settle)
      );
      final float corner = avatarRadius * settle;
      fillPaint.setColor(ColorUtils.alphaColor(alpha, ColorUtils.fromToArgb(ColorUtils.fromToArgb(neutralColor, readyColor, readyF), archiveColor, settle)));
      c.drawRoundRect(rect, corner, corner, fillPaint);
      c.clipRect(rect); // the text and icon stay on it, never over the row coming out around it
    } else {
      fillPaint.setColor(neutralColor);
      c.drawRect(0, visibleTop, width, height, fillPaint);
      // The photo's circle, then the ready color filling the row from it once ready
      fillPaint.setColor(readyColor);
      if (radius > 0f) {
        final float corner = avatarRadius * (radius / fullRadius);
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawRoundRect(rect, corner, corner, fillPaint);
      }
      if (readyF > 0f) {
        final float far = farthestCorner(cx, cy, width, visibleTop, height);
        c.drawCircle(cx, cy, XgramMotion.lerp(radius, far, readyF), fillPaint);
      }
    }

    // The arrow spins out and the archive icon spins in (swapped at once with Reduce motion, its spring snaps)
    loadGlyphs(view);
    final float arrowAlpha = XgramMotion.clamp01(1f - readyF * 2f);
    final float archiveAlpha = XgramMotion.clamp01(readyF * 2f - 1f);
    if (settling || reduceMotion) {
      final int color = settling ? ColorUtils.fromToArgb(readyContent, glyphColor, settle) : readyContent;
      drawGlyph(c, arrow, cx, cy, glyphScale, 0f, color, arrowAlpha * alpha);
      drawGlyph(c, archive, cx, cy, glyphScale, 0f, color, archiveAlpha * alpha);
    } else {
      drawGlyph(c, arrow, cx, cy, glyphScale * (1f - .4f * Math.min(1f, readyF * 2f)), 90f * Math.min(1f, readyF * 2f), readyContent, arrowAlpha);
      drawGlyph(c, archive, cx, cy, glyphScale * (.6f + .4f * archiveAlpha), -90f * (1f - archiveAlpha), readyContent, archiveAlpha);
    }

    // The text at the title's place: in once there is room, the old one up and out, the new one in from below
    final float textAlpha = XgramMotion.clamp01((visible - Screen.dp(28f)) / Screen.dp(16f)) * (settling ? XgramMotion.clamp01(1f - settle * 3f) : 1f);
    if (textAlpha > 0f) {
      prepareTexts(width - textStart - Screen.dp(16f), expressive);
      final Paint.FontMetrics fm = textPaint.getFontMetrics();
      final float baseline = cy - (fm.ascent + fm.descent) / 2f;
      final float shift = reduceMotion ? 0f : Screen.dp(10f);
      // Expressive design: a fade through (the old one out over the first half); Telegram X's crossfade otherwise
      final float pullAlpha = expressive ? XgramMotion.clamp01(1f - readyF * 2f) : 1f - readyF;
      final float releaseAlpha = expressive ? XgramMotion.clamp01(readyF * 2f - 1f) : readyF;
      drawText(c, pullText, pullTextWidth, textStart, baseline - shift * readyF, width, rtl, textColor, pullAlpha * textAlpha);
      drawText(c, releaseText, releaseTextWidth, textStart, baseline + shift * (1f - readyF), width, rtl, textColor, releaseAlpha * textAlpha);
    }
    c.restoreToCount(saveCount);
  }

  private static float farthestCorner (float cx, float cy, float width, float top, float bottom) {
    final float dx = Math.max(cx, width - cx);
    final float dy = Math.max(cy - top, bottom - cy);
    return (float) Math.hypot(dx, dy);
  }

  private void loadGlyphs (View view) {
    if (arrow == null) {
      arrow = Drawables.get(view.getResources(), R.drawable.baseline_arrow_downward_24);
    }
    if (archive == null) {
      archive = Drawables.get(view.getResources(), R.drawable.baseline_archive_24);
    }
  }

  private void drawGlyph (Canvas c, @Nullable Drawable d, float cx, float cy, float scale, float rotation, int color, float alpha) {
    if (d == null || alpha <= 0f || scale <= 0f) {
      return;
    }
    if (iconPaintColor != color || iconPaint.getColorFilter() == null) {
      iconPaintColor = color;
      iconPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }
    iconPaint.setAlpha(Math.round(255 * XgramMotion.clamp01(alpha)));
    final int saveCount = c.save();
    c.translate(cx, cy);
    if (rotation != 0f) {
      c.rotate(rotation);
    }
    if (scale != 1f) {
      c.scale(scale, scale);
    }
    Drawables.draw(c, d, -d.getMinimumWidth() / 2f, -d.getMinimumHeight() / 2f, iconPaint);
    c.restoreToCount(saveCount);
  }

  private void prepareTexts (int maxWidth, boolean expressive) {
    final String pull = Lang.getString(R.string.XgramArchivePullDown);
    final String release = Lang.getString(R.string.XgramArchivePullRelease);
    final String source = pull + '\n' + release;
    if (maxWidth == textCacheWidth && expressive == textCacheExpressive && source.equals(textCacheSource)) {
      return;
    }
    textCacheWidth = maxWidth;
    textCacheExpressive = expressive;
    textCacheSource = source;
    textPaint.setTextSize(Screen.dp(16f));
    textPaint.setTypeface(expressive ? XgramExpressive.typeface(XgramExpressive.TS_TITLE_M, false) : Fonts.getRobotoMedium());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      textPaint.setLetterSpacing(expressive ? XgramExpressive.trackingSp(XgramExpressive.TS_TITLE_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M) : 0f);
    }
    final int width = Math.max(0, maxWidth);
    pullText = TextUtils.ellipsize(pull, textPaint, width, TextUtils.TruncateAt.END);
    releaseText = TextUtils.ellipsize(release, textPaint, width, TextUtils.TruncateAt.END);
    pullTextWidth = textPaint.measureText(pullText, 0, pullText.length());
    releaseTextWidth = textPaint.measureText(releaseText, 0, releaseText.length());
  }

  private void drawText (Canvas c, @Nullable CharSequence text, float textWidth, int textStart, float baseline, int width, boolean rtl, int color, float alpha) {
    if (text == null || text.length() == 0 || alpha <= 0f) {
      return;
    }
    textPaint.setColor(ColorUtils.alphaColor(XgramMotion.clamp01(alpha), color));
    final float x = rtl ? width - textStart - textWidth : textStart;
    c.drawText(text, 0, text.length(), x, baseline, textPaint);
  }
}
