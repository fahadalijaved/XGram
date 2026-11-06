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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * The Calls page's banner while full-screen calls are off ({@link XgramFullScreenCalls}), Expressive only: an
 * errorContainer card with 16dp corners (CornerLarge), 16dp from the list's sides like its rows, 16dp padding. A 24dp
 * phone icon, then titleSmall "Calls can't ring full screen" and bodyMedium supporting text (another one after an
 * update turned it off), all onErrorContainer and wrapping freely; under them, at the end, an "Allow" button: a 40dp
 * full-corner error pill with labelLarge in onError. The whole card is the one touch target (its state layer is
 * onErrorContainer at 10%) and opens Android's page; screen readers hear the title, the text and an "Allow" action.
 * Colours are read when drawn.
 */
public final class XgramFullScreenCallsBanner extends View {
  /** The custom view type of the banner row: {@code ListItem.TYPE_CUSTOM - VIEW_TYPE}. */
  public static final int VIEW_TYPE = 9062;

  private static final float MARGIN_SIDE = 16f, MARGIN_TOP = 8f, MARGIN_BOTTOM = 8f;
  private static final float PADDING = 16f, ICON = 24f, ICON_GAP = 16f, TEXT_GAP = 4f;
  /** The 40dp pill centred in a 48dp band: 12dp under the text, 16dp above the card's bottom edge. */
  private static final float BUTTON_HEIGHT = 40f, BUTTON_BAND = 48f, BUTTON_PADDING = 16f;

  private final TextPaint titlePaint, bodyPaint, actionPaint;
  private final Drawable icon;
  private final RectF card = new RectF(), button = new RectF();
  private final GradientDrawable mask;
  private final RippleDrawable ripple;
  private int rippleColor;

  @Nullable private StaticLayout titleLayout, bodyLayout;
  @Nullable private String title, body, action;
  @Nullable private CharSequence actionText;
  private float actionWidth;
  private int layoutWidth = -1;

  public XgramFullScreenCallsBanner (@NonNull Context context) {
    super(context);
    titlePaint = XgramFullScreenCallsRow.m3Paint(XgramExpressive.TS_TITLE_S);
    bodyPaint = XgramFullScreenCallsRow.m3Paint(XgramExpressive.TS_BODY_M);
    actionPaint = XgramFullScreenCallsRow.m3Paint(XgramExpressive.TS_LABEL_L);
    icon = Drawables.get(getResources(), R.drawable.baseline_phone_in_talk_24);
    setId(R.id.btn_xgramFullScreenCalls);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    setClickable(true);
    setFocusable(true);
    mask = new GradientDrawable();
    mask.setColor(0xffffffff);
    ripple = new RippleDrawable(ColorStateList.valueOf(0), null, mask);
    ripple.setCallback(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setDefaultFocusHighlightEnabled(false); // keyboard focus shows in the card's state layer
    }
  }

  // Layout

  private int textWidth (int width) {
    return Math.max(Screen.dp(48f), width - Screen.dp(2 * MARGIN_SIDE + 2 * PADDING + ICON + ICON_GAP));
  }

  /** True when the text was laid out again (new width, language or wording). */
  private boolean buildLayouts (int width) {
    final String title = Lang.getString(R.string.XgramFullScreenCallsWarning);
    final String body = Lang.getString(XgramFullScreenCalls.resetByUpdate() ? R.string.XgramFullScreenCallsBannerTextReset : R.string.XgramFullScreenCallsBannerText);
    final String action = Lang.getString(R.string.XgramFullScreenCallsBannerAction);
    final int textWidth = textWidth(width);
    if (titleLayout == null || bodyLayout == null || textWidth != layoutWidth || !title.equals(this.title) || !body.equals(this.body) || !action.equals(this.action)) {
      this.title = title;
      this.body = body;
      this.action = action;
      this.layoutWidth = textWidth;
      titleLayout = XgramFullScreenCallsRow.layout(title, titlePaint, textWidth, XgramFullScreenCallsRow.lineExtra(titlePaint, XgramExpressive.TS_TITLE_S));
      bodyLayout = XgramFullScreenCallsRow.layout(body, bodyPaint, textWidth, XgramFullScreenCallsRow.lineExtra(bodyPaint, XgramExpressive.TS_BODY_M));
      // The pill is at most as wide as the card's inside: a very long translation ends with an ellipsis there
      final float maxLabel = Math.max(0, width - Screen.dp(2 * MARGIN_SIDE + 2 * PADDING + 2 * BUTTON_PADDING));
      actionText = TextUtils.ellipsize(action, actionPaint, maxLabel, TextUtils.TruncateAt.END);
      actionWidth = actionPaint.measureText(actionText, 0, actionText.length());
      setContentDescription(title + ". " + body);
      return true;
    }
    return false;
  }

  private int textHeight () {
    return (titleLayout != null ? titleLayout.getHeight() : 0) + Screen.dp(TEXT_GAP) + (bodyLayout != null ? bodyLayout.getHeight() : 0);
  }

  /** From the card's top: padding, the text, 8dp (12dp to the pill itself), the button's band, 12dp (16dp from the pill). */
  private int cardHeight () {
    return Screen.dp(PADDING) + textHeight() + Screen.dp(8f) + Screen.dp(BUTTON_BAND) + Screen.dp(PADDING - (BUTTON_BAND - BUTTON_HEIGHT) / 2f);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED ? MeasureSpec.getSize(widthMeasureSpec) : Screen.dp(360f);
    buildLayouts(width);
    setMeasuredDimension(width, desiredHeight());
  }

  private int desiredHeight () {
    return Screen.dp(MARGIN_TOP) + cardHeight() + Screen.dp(MARGIN_BOTTOM);
  }

  // Drawing

  @Override
  protected void onDraw (Canvas c) {
    final int width = getWidth();
    if (buildLayouts(width) && desiredHeight() != getHeight()) {
      requestLayout(); // another language or wording ("after the update"): the card takes its new height, never clips
    }
    final boolean rtl = Lang.rtl();
    final int content = XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER);

    card.set(Screen.dp(MARGIN_SIDE), Screen.dp(MARGIN_TOP), width - Screen.dp(MARGIN_SIDE), Screen.dp(MARGIN_TOP) + cardHeight());
    final float radius = Screen.dpf(XgramExpressive.CORNER_L);
    c.drawRoundRect(card, radius, radius, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.ERROR_CONTAINER)));

    // State layer of the whole card (press, focus, hover)
    final int stateColor = XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED);
    if (stateColor != rippleColor) {
      rippleColor = stateColor;
      ripple.setColor(ColorStateList.valueOf(stateColor));
    }
    mask.setCornerRadius(radius);
    ripple.setBounds(Math.round(card.left), Math.round(card.top), Math.round(card.right), Math.round(card.bottom));
    ripple.draw(c);

    final float padding = Screen.dpf(PADDING);
    final float textTop = card.top + padding;

    // Leading icon, centred on the title's first line
    if (icon != null) {
      final float size = Screen.dpf(ICON);
      final float iconLeft = rtl ? card.right - padding - size : card.left + padding;
      final float firstLine = titleLayout != null && titleLayout.getLineCount() > 0 ? titleLayout.getLineBottom(0) : Screen.dpf(20f);
      final float iconCenterY = textTop + firstLine / 2f;
      Drawables.draw(c, icon, iconLeft + (size - icon.getMinimumWidth()) / 2f, iconCenterY - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(content));
    }

    // Title and supporting text
    final float textLeft = rtl ? card.left + padding : card.left + padding + Screen.dpf(ICON + ICON_GAP);
    final int saveCount = c.save();
    c.translate(textLeft, textTop);
    if (titleLayout != null) {
      titlePaint.setColor(content);
      titleLayout.draw(c);
      c.translate(0, titleLayout.getHeight() + Screen.dp(TEXT_GAP));
    }
    if (bodyLayout != null) {
      bodyPaint.setColor(content);
      bodyLayout.draw(c);
    }
    c.restoreToCount(saveCount);

    // The "Allow" pill at the card's end
    if (actionText != null) {
      final float bandTop = textTop + textHeight() + Screen.dp(8f);
      final float pillTop = bandTop + Screen.dpf((BUTTON_BAND - BUTTON_HEIGHT) / 2f);
      final float pillWidth = actionWidth + Screen.dpf(2 * BUTTON_PADDING);
      final float pillLeft = rtl ? card.left + padding : card.right - padding - pillWidth;
      button.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + Screen.dpf(BUTTON_HEIGHT));
      c.drawRoundRect(button, button.height() / 2f, button.height() / 2f, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.ERROR)));
      actionPaint.setColor(XgramExpressive.color(XgramExpressive.ON_ERROR));
      final android.graphics.Paint.FontMetrics metrics = actionPaint.getFontMetrics();
      final float baseline = button.centerY() - (metrics.ascent + metrics.descent) / 2f;
      c.drawText(actionText, 0, actionText.length(), button.left + Screen.dpf(BUTTON_PADDING), baseline, actionPaint);
    }
  }

  // Screen readers: the title and text, then "Allow" as the action of the one button

  @Override
  public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(android.widget.Button.class.getName());
    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, Lang.getString(R.string.XgramFullScreenCallsBannerAction)));
  }

  @Override
  protected boolean verifyDrawable (@NonNull Drawable who) {
    return (ripple != null && who == ripple) || super.verifyDrawable(who);
  }

  @Override
  protected void drawableStateChanged () {
    super.drawableStateChanged();
    if (ripple != null) { // also from setClickable in the constructor, before the ripple is made
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
