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
package com.fahadali.xgram.call;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * The side menu's warning while full-screen calls are off ({@link XgramFullScreenCalls}): "Calls can't ring full
 * screen" and "Tap to allow full-screen notifications"; a tap opens Android's page. Its owner removes it once they are
 * allowed.
 * <ul>
 *   <li>Expressive: an errorContainer card with 16dp corners (CornerLarge: it wraps, so not a pill, and it reads as a
 *   notice, not a destination), inset like the drawer's items with its 24dp phone icon where theirs are and the text
 *   where their labels start; labelLarge headline, bodySmall supporting text, both onErrorContainer. The press is
 *   onErrorContainer at 10% in the card's shape.</li>
 *   <li>Telegram X look: a full-width row as the side menu's (icon at 18dp, text at 72dp), the icon and headline in the
 *   negative colours, the supporting text in the light text colour, Telegram X's ripple.</li>
 * </ul>
 * Both wrap with no ellipsis and grow (at least 56 / 52dp), so a narrow side menu or the cover screen never clips them.
 * Colours are read when drawn: light, dark, Pure black and a day/night switch from the menu itself.
 */
public final class XgramFullScreenCallsRow extends View {
  /** The custom view type of the side menu's row: {@code ListItem.TYPE_CUSTOM - VIEW_TYPE}. */
  public static final int VIEW_TYPE = 9061;

  /** The card, inside the row: above and below it (the drawer's next pill starts 8dp under it). */
  private static final float CARD_TOP = 4f, CARD_BOTTOM = 8f;
  private static final float PADDING_VERTICAL = 12f, PADDING_END = 16f, ICON = 24f, LINE_GAP = 2f;
  private static final float MIN_CARD = 56f, MIN_CLASSIC = 52f;
  private static final float CLASSIC_ICON_START = 18f, CLASSIC_TEXT_START = 72f, CLASSIC_END = 16f;

  private final boolean expressive;
  /** Expressive: the card's inset from both sides, the icon's start inside it and the gap from the icon to the text. */
  private final float insetDp, leadingDp, iconGapDp;
  private final TextPaint titlePaint, hintPaint;
  private final Drawable icon;
  private final RectF card = new RectF();
  @Nullable private final GradientDrawable mask;
  @Nullable private final RippleDrawable ripple;
  private int rippleColor;

  @Nullable private StaticLayout titleLayout, hintLayout;
  @Nullable private String title, hint;
  private int layoutWidth = -1;

  /** In the side menu (DrawerController): its 12dp item inset, the icon at 28dp and the text at 64dp. */
  public static XgramFullScreenCallsRow forDrawer (@NonNull Context context) {
    return new XgramFullScreenCallsRow(context, 12f, 16f, 12f);
  }

  /** In the expanded navigation rail: its 8dp row inset, the icon at 24dp and the text at 56dp. */
  public static XgramFullScreenCallsRow forRail (@NonNull Context context) {
    return new XgramFullScreenCallsRow(context, 8f, 16f, 8f);
  }

  private XgramFullScreenCallsRow (@NonNull Context context, float insetDp, float leadingDp, float iconGapDp) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    this.insetDp = insetDp;
    this.leadingDp = leadingDp;
    this.iconGapDp = iconGapDp;
    if (expressive) {
      titlePaint = m3Paint(XgramExpressive.TS_LABEL_L);
      hintPaint = m3Paint(XgramExpressive.TS_BODY_S);
    } else {
      titlePaint = classicPaint(Fonts.getRobotoMedium(), 15f);
      hintPaint = classicPaint(Fonts.getRobotoRegular(), 13f);
    }
    icon = Drawables.get(getResources(), R.drawable.baseline_phone_in_talk_24);
    setId(R.id.btn_xgramFullScreenCalls);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    setClickable(true);
    setFocusable(true);
    if (expressive) {
      mask = new GradientDrawable();
      mask.setColor(0xffffffff);
      ripple = new RippleDrawable(ColorStateList.valueOf(0), null, mask);
      ripple.setCallback(this);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setDefaultFocusHighlightEnabled(false); // keyboard focus shows in the card's state layer
      }
    } else {
      mask = null;
      ripple = null;
      RippleSupport.setTransparentSelector(this);
    }
    updateDescription();
  }

  // Text

  static TextPaint m3Paint (int ts) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    paint.setTextSize(Screen.dpf(XgramExpressive.textSizeSp(ts)));
    paint.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
    return paint;
  }

  private static TextPaint classicPaint (Typeface typeface, float sizeDp) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(typeface);
    paint.setTextSize(Screen.dpf(sizeDp));
    return paint;
  }

  /** M3 type scale line height for {@code ts} (spacing added between lines, none after the last). */
  static float lineExtra (TextPaint paint, int ts) {
    return Math.max(0f, Screen.dpf(XgramExpressive.lineHeightSp(ts)) - paint.getFontSpacing());
  }

  /** Wraps freely, never ellipsized; the paragraph follows the app's language direction. */
  static StaticLayout layout (@NonNull CharSequence text, @NonNull TextPaint paint, int width, float lineExtra) {
    width = Math.max(1, width);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(Lang.rtl() ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.LTR)
        .setLineSpacing(lineExtra, 1f)
        .setIncludePad(false)
        .build();
    }
    //noinspection deprecation
    return new StaticLayout(text, paint, width, Lang.rtl() ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL, 1f, lineExtra, false);
  }

  private void updateDescription () {
    setContentDescription(Lang.getString(R.string.XgramFullScreenCallsWarning) + ". " + Lang.getString(R.string.XgramFullScreenCallsWarningHint));
  }

  /** Where the text column starts, from the start edge. */
  private int textStart () {
    return expressive ? Screen.dp(insetDp + leadingDp + ICON + iconGapDp) : Screen.dp(CLASSIC_TEXT_START);
  }

  /** Where the text column ends, from the end edge. */
  private int textEnd () {
    return expressive ? Screen.dp(insetDp + PADDING_END) : Screen.dp(CLASSIC_END);
  }

  /** True when the text was laid out again (new width, language or wording). */
  private boolean buildLayouts (int width) {
    final String title = Lang.getString(R.string.XgramFullScreenCallsWarning);
    final String hint = Lang.getString(R.string.XgramFullScreenCallsWarningHint);
    final int textWidth = Math.max(Screen.dp(48f), width - textStart() - textEnd());
    if (titleLayout == null || hintLayout == null || textWidth != layoutWidth || !title.equals(this.title) || !hint.equals(this.hint)) {
      this.title = title;
      this.hint = hint;
      this.layoutWidth = textWidth;
      titleLayout = layout(title, titlePaint, textWidth, expressive ? lineExtra(titlePaint, XgramExpressive.TS_LABEL_L) : 0f);
      hintLayout = layout(hint, hintPaint, textWidth, expressive ? lineExtra(hintPaint, XgramExpressive.TS_BODY_S) : 0f);
      updateDescription();
      return true;
    }
    return false;
  }

  private int textHeight () {
    return (titleLayout != null ? titleLayout.getHeight() : 0) + Screen.dp(LINE_GAP) + (hintLayout != null ? hintLayout.getHeight() : 0);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED ? MeasureSpec.getSize(widthMeasureSpec) : Screen.dp(300f);
    buildLayouts(width);
    setMeasuredDimension(width, desiredHeight());
  }

  private int desiredHeight () {
    final int content = textHeight() + 2 * Screen.dp(PADDING_VERTICAL);
    return expressive ?
      Screen.dp(CARD_TOP) + Math.max(Screen.dp(MIN_CARD), content) + Screen.dp(CARD_BOTTOM) :
      Math.max(Screen.dp(MIN_CLASSIC), content);
  }

  // Drawing

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth(), height = getHeight();
    if (buildLayouts(width) && desiredHeight() != height) {
      requestLayout(); // another language or wording: the row takes its new height, never clips
    }
    final boolean rtl = Lang.rtl();
    final float top, bottom;
    final int iconColor, titleColor, hintColor;
    float iconStart;
    if (expressive) {
      top = Screen.dp(CARD_TOP);
      bottom = height - Screen.dp(CARD_BOTTOM);
      card.set(Screen.dp(insetDp), top, width - Screen.dp(insetDp), bottom);
      final float radius = Screen.dpf(XgramExpressive.CORNER_L);
      c.drawRoundRect(card, radius, radius, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.ERROR_CONTAINER)));
      drawState(c, radius);
      iconColor = titleColor = hintColor = XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER);
      iconStart = Screen.dp(insetDp + leadingDp);
    } else {
      top = 0;
      bottom = height;
      iconColor = Theme.getColor(ColorId.iconNegative);
      titleColor = Theme.getColor(ColorId.textNegative);
      hintColor = Theme.getColor(ColorId.textLight);
      iconStart = Screen.dp(CLASSIC_ICON_START);
    }

    final int textHeight = textHeight();
    final float textTop = top + (bottom - top - textHeight) / 2f;
    // Centred on a two-line block; with more lines (a narrow menu, a long translation) it stays by the headline
    final float titleLine = titleLayout != null && titleLayout.getLineCount() > 0 ? titleLayout.getLineBottom(0) : Screen.dp(20f);
    final boolean twoLines = titleLayout != null && hintLayout != null && titleLayout.getLineCount() + hintLayout.getLineCount() <= 2;
    final float iconCenterY = twoLines ? (top + bottom) / 2f : textTop + titleLine / 2f;
    if (icon != null) {
      final float size = Screen.dpf(ICON);
      final float iconLeft = rtl ? width - iconStart - size : iconStart;
      Drawables.draw(c, icon, iconLeft + (size - icon.getMinimumWidth()) / 2f, iconCenterY - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(iconColor));
    }

    final float textLeft = rtl ? textEnd() : textStart();
    final int saveCount = c.save();
    c.translate(textLeft, textTop);
    if (titleLayout != null) {
      titlePaint.setColor(titleColor);
      titleLayout.draw(c);
      c.translate(0, titleLayout.getHeight() + Screen.dp(LINE_GAP));
    }
    if (hintLayout != null) {
      hintPaint.setColor(hintColor);
      hintLayout.draw(c);
    }
    c.restoreToCount(saveCount);
  }

  /** M3 state layer (press, focus, hover): onErrorContainer at 10%, in the card's shape. */
  private void drawState (Canvas c, float radius) {
    if (ripple == null || mask == null) {
      return;
    }
    final int color = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER), XgramExpressive.STATE_PRESSED);
    if (color != rippleColor) {
      rippleColor = color;
      ripple.setColor(ColorStateList.valueOf(color));
    }
    mask.setCornerRadius(radius);
    ripple.setBounds(Math.round(card.left), Math.round(card.top), Math.round(card.right), Math.round(card.bottom));
    ripple.draw(c);
  }

  @Override
  protected boolean verifyDrawable (@NonNull Drawable who) {
    return (ripple != null && who == ripple) || super.verifyDrawable(who);
  }

  @Override
  protected void drawableStateChanged () {
    super.drawableStateChanged();
    if (ripple != null) {
      ripple.setState(getDrawableState());
    }
  }

  @Override
  public void jumpDrawablesToCurrentState () {
    super.jumpDrawablesToCurrentState();
    if (ripple != null) {
      ripple.jumpToCurrentState();
    }
  }

  @Override
  public void drawableHotspotChanged (float x, float y) {
    super.drawableHotspotChanged(x, y);
    if (ripple != null) {
      ripple.setHotspot(x, y);
    }
  }

  // Night mode switched from the side menu itself: the colours follow every frame of it

  private final ThemeChangeListener themeListener = new ThemeChangeListener() {
    @Override
    public boolean needsTempUpdates () {
      return true;
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, @Nullable ColorState state) {
      invalidate();
    }
  };

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    ThemeManager.instance().addThemeListener(themeListener);
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    ThemeManager.instance().removeThemeListener(themeListener);
  }
}
