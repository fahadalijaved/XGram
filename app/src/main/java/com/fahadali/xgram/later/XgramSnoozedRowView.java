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
package com.fahadali.xgram.later;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;

/**
 * The Snoozed row of a chat list, under the archive: a circle with the snooze icon, "Snoozed" and who is in it with
 * the next wake time, and the count. Expressive design: the archive's container group, tertiaryContainer circle,
 * titleMedium / bodyMedium, a secondaryContainer count chip, a chevron, press morph and a crossfade on changes.
 * Classic: Telegram X's archive row look with a muted counter. 56dp in every chat list view.
 */
public final class XgramSnoozedRowView extends View {
  public static final float ROW_HEIGHT_DP = 56f;
  private static final float CIRCLE_DP = 40f, START_DP = 16f, TEXT_START_DP = 72f, CHEVRON_DP = 24f;

  private final boolean m3;
  private final Counter counter;
  private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG), subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private int iconPaintColor, chevronPaintColor;
  private final Drawable icon;
  @Nullable private final Drawable chevron;

  @Nullable private XgramLater.RowState state;
  private String title = "", subtitle = "";
  private String titleShown = "", subtitleShown = "";
  private float titleWidth, subtitleWidth;
  // The texts fading out after a change (Expressive design)
  @Nullable private String oldTitle, oldSubtitle;
  private float oldTitleWidth, oldSubtitleWidth;
  private float fade = 1f;
  @Nullable private XgramExpressive.Spring fadeSpring;
  private int laidOutWidth;
  private boolean dividerAbove;

  public XgramSnoozedRowView (Context context, @Nullable ViewController<?> themeProvider) {
    super(context);
    this.m3 = XgramExpressive.isEnabled();
    this.icon = Drawables.get(context.getResources(), R.drawable.baseline_notifications_paused_24);
    this.chevron = m3 ? Drawables.get(context.getResources(), R.drawable.round_keyboard_arrow_right_24) : null;
    TextColorSet chipColors = m3 ? new TextColorSet() {
      @Override
      public int defaultTextColor () {
        return XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
      }

      @Override
      public int backgroundColor (boolean isPressed) {
        return XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      }
    } : null;
    Counter.Builder builder = new Counter.Builder().callback(this);
    if (chipColors != null) {
      builder.colorSet(chipColors);
    }
    this.counter = builder.build();
    if (m3) {
      titlePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_TITLE_M, false));
      titlePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)));
      subtitlePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_M, false));
      subtitlePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
      XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(this, Theme.getColor(ColorId.filling), 0f, XgramExpressive.CORNER_L);
      morph.setColorIds(ColorId.filling, ColorId.filling, 0f); // the chat container's surface, as the chat rows
    } else {
      titlePaint.setTypeface(Fonts.getRobotoMedium());
      titlePaint.setTextSize(Screen.dp(16f));
      subtitlePaint.setTypeface(Fonts.getRobotoRegular());
      subtitlePaint.setTextSize(Screen.dp(15f));
      RippleSupport.setSimpleWhiteBackground(this, themeProvider);
    }
    Views.setClickable(this);
    setId(R.id.btn_xgramLaterSnoozedRow);
  }

  public static int measureHeight () {
    return Screen.dp(ROW_HEIGHT_DP);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), measureHeight());
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w != laidOutWidth) {
      layoutTexts();
    }
  }

  /** Under the archive, in its group: a divider between the two (Expressive design; classic draws the archive's own). */
  public void setDividerAbove (boolean dividerAbove) {
    if (this.dividerAbove != dividerAbove) {
      this.dividerAbove = dividerAbove;
      invalidate();
    }
  }

  /** Main thread. A count or text change crossfades with Expressive design. */
  public void setState (@Nullable XgramLater.RowState state, boolean animated) {
    if (state == null) {
      this.state = null;
      return;
    }
    boolean changed = this.state == null || !this.state.equals(state);
    this.state = state;
    if (!changed) {
      return;
    }
    String newTitle = Lang.getString(m3 ? R.string.XgramLaterRow : R.string.XgramLaterRowClassic);
    String when = state.nextWakeAt != 0 ? XgramLaterTimes.formatWake(state.nextWakeAt) : null;
    String newSubtitle;
    if (m3) {
      newSubtitle = when != null ? Lang.getString(R.string.XgramLaterRowSub, state.titles, when) : Lang.getString(R.string.XgramLaterRowSubUntil, state.titles, Lang.getString(R.string.XgramLaterRowUntilReply));
    } else {
      String chats = Lang.plural(R.string.xChats, state.count);
      newSubtitle = when != null ? Lang.getString(R.string.XgramLaterRowSubClassic, chats, when) : Lang.getString(R.string.XgramLaterRowSubUntil, chats, Lang.getString(R.string.XgramLaterRowUntilReply));
    }
    boolean fadeTexts = animated && m3 && getParent() != null && laidOutWidth > 0 && (!newTitle.equals(title) || !newSubtitle.equals(subtitle));
    if (fadeTexts) {
      oldTitle = titleShown;
      oldTitleWidth = titleWidth;
      oldSubtitle = subtitleShown;
      oldSubtitleWidth = subtitleWidth;
    }
    this.title = newTitle;
    this.subtitle = newSubtitle;
    counter.setCount(state.count, !state.unread, animated && getParent() != null);
    layoutTexts();
    setContentDescription(newTitle + ", " + newSubtitle);
    if (fadeTexts) {
      if (fadeSpring == null) {
        fadeSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 1f, (s, value) -> {
          fade = value;
          invalidate();
        });
        fadeSpring.setEndListener(() -> {
          oldTitle = oldSubtitle = null;
          invalidate();
        });
      }
      fadeSpring.setValue(0f);
      fade = 0f;
      fadeSpring.animateTo(1f);
    } else {
      oldTitle = oldSubtitle = null;
      fade = 1f;
      if (fadeSpring != null) {
        fadeSpring.setValue(1f);
      }
    }
    invalidate();
  }

  private int trailingReserve () {
    int reserve = Screen.dp(START_DP) + Math.round(counter.getScaledOrTargetWidth(Screen.dp(8f), true));
    if (chevron != null) {
      reserve += Screen.dp(CHEVRON_DP) + Screen.dp(4f);
    }
    return reserve;
  }

  /** Cuts the texts to the room they have (on a width or text change only, never while drawing). */
  private void layoutTexts () {
    int width = getWidth();
    laidOutWidth = width;
    if (width <= 0) {
      return;
    }
    int avail = Math.max(0, width - Screen.dp(TEXT_START_DP) - trailingReserve());
    titleShown = TextUtils.ellipsize(title, titlePaint, avail, TextUtils.TruncateAt.END).toString();
    subtitleShown = TextUtils.ellipsize(subtitle, subtitlePaint, avail, TextUtils.TruncateAt.END).toString();
    titleWidth = titlePaint.measureText(titleShown);
    subtitleWidth = subtitlePaint.measureText(subtitleShown);
  }

  // Each paint gets a new filter only when its color changes (a theme switch), never per frame
  private Paint iconPaint (int color) {
    if (iconPaintColor != color || iconPaint.getColorFilter() == null) {
      iconPaintColor = color;
      iconPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }
    return iconPaint;
  }

  private Paint chevronPaint (int color) {
    if (chevronPaintColor != color || chevronPaint.getColorFilter() == null) {
      chevronPaintColor = color;
      chevronPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }
    return chevronPaint;
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    if (state == null) {
      return;
    }
    final int width = getWidth(), height = getHeight();
    final boolean rtl = Lang.rtl();
    final float cy = height / 2f;
    if (m3 && dividerAbove) {
      circlePaint.setColor(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT));
      final int inset = Screen.dp(TEXT_START_DP);
      c.drawRect(rtl ? 0 : inset, 0, rtl ? width - inset : width, Screen.separatorSize(), circlePaint);
    }

    // The circle and the snooze icon
    final float radius = Screen.dp(CIRCLE_DP) / 2f;
    final float circleCx = rtl ? width - Screen.dp(START_DP) - radius : Screen.dp(START_DP) + radius;
    final int circleColor, iconColor;
    if (m3) {
      circleColor = XgramExpressive.color(XgramExpressive.TERTIARY_CONTAINER);
      iconColor = XgramExpressive.color(XgramExpressive.ON_TERTIARY_CONTAINER);
    } else {
      iconColor = Theme.getColor(ColorId.iconActive);
      circleColor = me.vkryl.core.ColorUtils.alphaColor(.12f, iconColor);
    }
    circlePaint.setColor(circleColor);
    c.drawCircle(circleCx, cy, radius, circlePaint);
    Drawables.draw(c, icon, circleCx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, iconPaint(iconColor));

    // Title above the middle, the supporting line below it
    final int titleColor = m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE) : Theme.getColor(ColorId.text);
    final int subtitleColor = m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.textLight);
    final float titleBaseline = cy - Screen.dp(3f);
    final float subtitleBaseline = cy + Screen.dp(17f);
    final float textStart = Screen.dp(TEXT_START_DP);
    if (oldTitle != null && fade < 1f) {
      drawText(c, oldTitle, oldTitleWidth, titlePaint, titleColor, 1f - fade, textStart, titleBaseline, rtl, width);
      drawText(c, oldSubtitle, oldSubtitleWidth, subtitlePaint, subtitleColor, 1f - fade, textStart, subtitleBaseline, rtl, width);
    }
    final float alpha = oldTitle != null ? fade : 1f;
    drawText(c, titleShown, titleWidth, titlePaint, titleColor, alpha, textStart, titleBaseline, rtl, width);
    drawText(c, subtitleShown, subtitleWidth, subtitlePaint, subtitleColor, alpha, textStart, subtitleBaseline, rtl, width);

    // The end: the chevron (Expressive design), then the count
    float end = width - Screen.dp(START_DP);
    if (chevron != null) {
      final float chevronLeft = rtl ? width - end : end - Screen.dp(CHEVRON_DP);
      final float chevronTop = cy - chevron.getMinimumHeight() / 2f;
      final Paint paint = chevronPaint(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      if (rtl) {
        c.save();
        c.scale(-1f, 1f, chevronLeft + chevron.getMinimumWidth() / 2f, cy);
        Drawables.draw(c, chevron, chevronLeft, chevronTop, paint);
        c.restore();
      } else {
        Drawables.draw(c, chevron, chevronLeft, chevronTop, paint);
      }
      end -= Screen.dp(CHEVRON_DP) + Screen.dp(4f);
    }
    final float counterRadius = Screen.dp(11f); // DrawAlgorithms: (textSize - 2)dp for the 13dp counter
    if (rtl) {
      counter.draw(c, width - end + counterRadius, cy, Gravity.LEFT, 1f);
    } else {
      counter.draw(c, end - counterRadius, cy, Gravity.RIGHT, 1f);
    }
    if (m3 && state.unread) {
      // A 6dp tertiary dot before the chip: an unmuted snoozed chat has unread messages
      final float chipWidth = counter.getScaledWidth(0);
      final float dotCx = rtl ? width - end + chipWidth + Screen.dp(8f) : end - chipWidth - Screen.dp(8f);
      circlePaint.setColor(XgramExpressive.color(XgramExpressive.TERTIARY));
      c.drawCircle(dotCx, cy, Screen.dp(3f), circlePaint);
    }
  }

  private static void drawText (Canvas c, @Nullable String text, float textWidth, TextPaint paint, int color, float alpha, float start, float baseline, boolean rtl, int width) {
    if (text == null || text.isEmpty() || alpha <= 0f) {
      return;
    }
    paint.setColor(alpha >= 1f ? color : me.vkryl.core.ColorUtils.alphaColor(alpha, color));
    float x = rtl ? width - start - textWidth : start;
    c.drawText(text, x, baseline, paint);
  }
}
