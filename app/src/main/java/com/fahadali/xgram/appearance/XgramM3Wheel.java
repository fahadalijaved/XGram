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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.InfiniteRecyclerView;
import org.thunderdog.challegram.widget.NoScrollTextView;

/**
 * Material 3 Expressive for Telegram X's wheel pickers (InfiniteRecyclerView: the birthday, schedule and invite link
 * expiry date pickers, the self-destruct timer and the notification repeat dialogs): the selected row sits in a
 * secondaryContainer band with full corners (instead of two hairline dividers), its text in titleLarge and
 * onSecondaryContainer, the rows around it shrink to bodyLarge and fade in onSurfaceVariant; the full-width uppercase
 * commit bar becomes M3 bottom actions: a "Cancel" text button and a filled button at the end, 16dp from the edge, and
 * an optional third action at the start ("Remove birthday"). Colors are resolved at draw time, from the dark scheme
 * when the picker forces a dark theme (camera, media viewer). Main thread only; used while
 * {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramM3Wheel {
  private XgramM3Wheel () { }

  /** Space between the band and the picker's edges (dp). */
  public static final float BAND_INSET = 16f;
  /** The bottom actions row (dp): 40dp buttons with 12dp above and below. */
  public static final float ACTIONS_HEIGHT = 64f;
  /** Space between two buttons of the actions row (dp). */
  private static final float ACTIONS_GAP = 8f;

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  /** The role in the current theme, or in the scheme of {@code forcedTheme} (day or night) when one is forced. */
  @ColorInt
  public static int color (int role, @Nullable ThemeDelegate forcedTheme) {
    return forcedTheme != null ? XgramExpressive.colorFor(role, forcedTheme.isDark()) : XgramExpressive.color(role);
  }

  // Rows

  /** The selected row's text style (titleLarge) on the shared text paint of a picker. */
  public static void setupTextPaint (@NonNull TextPaint paint) {
    paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_TITLE_L, false));
    paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_L)));
  }

  /** Scale of a neighbour row against the selected one: bodyLarge over titleLarge. */
  public static float neighbourScale () {
    return XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_L);
  }

  @ColorInt
  public static int selectedTextColor (@Nullable ThemeDelegate forcedTheme) {
    return color(XgramExpressive.ON_SECONDARY_CONTAINER, forcedTheme);
  }

  @ColorInt
  public static int neighbourTextColor (@Nullable ThemeDelegate forcedTheme) {
    return color(XgramExpressive.ON_SURFACE_VARIANT, forcedTheme);
  }

  // Band

  private static final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final RectF bandRect = new RectF();

  /** The selection band: one row tall, centered on {@code centerY}, {@link #BAND_INSET} inside {@code left..right}. */
  public static void drawBand (@NonNull Canvas c, float left, float right, float centerY, @Nullable ThemeDelegate forcedTheme) {
    final float height = InfiniteRecyclerView.getItemHeight();
    final float inset = Screen.dpf(BAND_INSET);
    bandRect.set(left + inset, centerY - height / 2f, right - inset, centerY + height / 2f);
    if (bandRect.width() <= 0f) {
      return;
    }
    bandPaint.setColor(color(XgramExpressive.SECONDARY_CONTAINER, forcedTheme));
    final float radius = bandRect.height() / 2f;
    c.drawRoundRect(bandRect, radius, radius, bandPaint);
  }

  /** The band behind a row of picker columns (the container's background), in place of Telegram X's two lines. */
  public static final class BandDrawable extends Drawable {
    private final @Nullable ThemeDelegate forcedTheme;

    public BandDrawable (@Nullable ThemeDelegate forcedTheme) {
      this.forcedTheme = forcedTheme;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      drawBand(c, bounds.left, bounds.right, bounds.exactCenterY(), forcedTheme);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  // Bottom actions

  /**
   * An M3 button of the actions row: filled ({@code containerRole} with full corners that morph to 8dp while pressed)
   * or text (no container, a pill state layer in {@code contentRole}); labelLarge, sentence case. While disabled it is
   * onSurface at .12 with its label at .38. Colors follow theme changes (re-read in onDraw).
   */
  public static final class ActionButton extends NoScrollTextView {
    private final int containerRole, contentRole;
    private final @Nullable ThemeDelegate forcedTheme;
    private final @Nullable XgramExpressive.MorphDrawable container;

    public ActionButton (Context context, boolean filled, int containerRole, int contentRole, @Nullable ThemeDelegate forcedTheme) {
      super(context);
      this.containerRole = containerRole;
      this.contentRole = contentRole;
      this.forcedTheme = forcedTheme;
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setGravity(Gravity.CENTER);
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setMinWidth(Screen.dp(48f));
      final int padding = Screen.dp(filled ? XgramM3Sheets.BUTTON_SPACE : 12f);
      setPadding(padding, 0, padding, 0);
      Views.setClickable(this);
      if (filled) {
        container = XgramExpressive.attachPressMorph(this, color(containerRole, forcedTheme), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
      } else {
        container = null;
        XgramM3Chat.setTextButtonBackgroundRole(this, contentRole);
      }
      syncColors();
    }

    @Override
    public void setEnabled (boolean enabled) {
      final boolean changed = enabled != isEnabled();
      super.setEnabled(enabled);
      if (changed) {
        syncColors();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      syncColors(); // theme changes: the setters only act (and invalidate once) when a color changed
      super.onDraw(c);
    }

    private void syncColors () {
      final int text;
      if (isEnabled()) {
        if (container != null) {
          container.setColor(color(containerRole, forcedTheme));
        }
        text = color(contentRole, forcedTheme);
      } else {
        final int onSurface = color(XgramExpressive.ON_SURFACE, forcedTheme);
        if (container != null) {
          container.setColor(XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER));
        }
        text = XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT);
      }
      if (getCurrentTextColor() != text) {
        setTextColor(text);
      }
    }
  }

  /** The filled confirm button ("Save", "Send today at 10:00"): primary with an onPrimary label. */
  public static ActionButton newConfirmButton (Context context, @Nullable ThemeDelegate forcedTheme) {
    return new ActionButton(context, true, XgramExpressive.PRIMARY, XgramExpressive.ON_PRIMARY, forcedTheme);
  }

  /** The bottom actions of a picker sheet. */
  public static final class Actions {
    public final LinearLayout view;
    public final @Nullable TextView neutral;
    public final TextView cancel, confirm;

    Actions (LinearLayout view, @Nullable TextView neutral, TextView cancel, TextView confirm) {
      this.view = view;
      this.neutral = neutral;
      this.cancel = cancel;
      this.confirm = confirm;
    }
  }

  /**
   * [neutral] ... [Cancel] [confirm]: a {@link #ACTIONS_HEIGHT} row with 16dp on both sides, mirrored when the app
   * language is RTL. {@code neutralText} (an error text button at the start, e.g. "Remove birthday") is optional.
   */
  public static Actions newActions (Context context, @Nullable ThemeDelegate forcedTheme, @Nullable CharSequence neutralText, CharSequence cancelText, TextView confirm) {
    final boolean rtl = Lang.rtl();
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // children are added in visual order below
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ACTIONS_HEIGHT)));

    TextView neutral = null;
    if (!TextUtils.isEmpty(neutralText)) {
      neutral = new ActionButton(context, false, XgramExpressive.ERROR, XgramExpressive.ERROR, forcedTheme);
      neutral.setText(neutralText);
    }
    TextView cancel = new ActionButton(context, false, XgramExpressive.PRIMARY, XgramExpressive.PRIMARY, forcedTheme);
    cancel.setText(cancelText);

    View spacer = new View(context);
    spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

    final int buttonHeight = Screen.dp(XgramM3Sheets.BUTTON_HEIGHT);
    final int gap = Screen.dp(ACTIONS_GAP);
    View[] order = rtl ?
      new View[] {confirm, cancel, spacer, neutral} :
      new View[] {neutral, spacer, cancel, confirm};
    for (int i = 0; i < order.length; i++) {
      View child = order[i];
      if (child == null) {
        continue;
      }
      if (child != spacer) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonHeight);
        // the 8dp gap sits between Cancel and the confirm button, on the side facing it
        if (child == confirm && !rtl) {
          params.leftMargin = gap;
        } else if (child == cancel && rtl) {
          params.leftMargin = gap;
        }
        child.setLayoutParams(params);
      }
      row.addView(child);
    }
    return new Actions(row, neutral, cancel, confirm);
  }
}
