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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.graphics.shapes.RoundedPolygon;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * XGram M3E round 5, dialogs and sheets: the shared pieces of the Material 3 Expressive alerts, option sheets, confirm
 * sheets and the XGram popups (colors in a forced theme, the actions row of a sheet, filled / tonal / text buttons, the
 * hero shape, cards inside sheets, sentence case labels). Main thread only; used while
 * {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramR5Sheets {
  private XgramR5Sheets () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Colors

  /** {@code role} in the current theme, or in the light / dark scheme of {@code forcedTheme} (camera, media viewer). */
  @ColorInt
  public static int role (int role, @Nullable ThemeDelegate forcedTheme) {
    return forcedTheme != null ? XgramExpressive.colorFor(role, forcedTheme.isDark()) : XgramExpressive.color(role);
  }

  /**
   * A Telegram X color in the current theme, or in {@code forcedTheme}; with Expressive design a mapped id resolves to
   * its M3 role in that theme's scheme (Pure black included), not to the forced theme's own Telegram X color.
   */
  @ColorInt
  public static int themeColor (@Nullable ThemeDelegate forcedTheme, int colorId) {
    if (forcedTheme == null) {
      return Theme.getColor(colorId);
    }
    if (XgramExpressive.isEnabled() && XgramM3Colors.isMapped(colorId)) {
      return XgramM3Colors.resolve(forcedTheme, colorId);
    }
    return forcedTheme.getColor(colorId);
  }

  // Case

  /**
   * {@code r5Res} (a sentence case label) while Expressive design is on and the app language still shows Telegram X's
   * own English for {@code tgxRes} (a Title Case one); the Telegram X string otherwise, so translations are untouched.
   */
  @NonNull
  public static String sentenceCase (@StringRes int tgxRes, @StringRes int r5Res) {
    final String tgx = Lang.getString(tgxRes);
    if (!XgramExpressive.isEnabled()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return Lang.getString(r5Res);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  // Option sheets

  /** The plain "Cancel" tile of a Telegram X option sheet (btn_cancel, the Cancel label, no icon or the cancel icon). */
  public static boolean isPlainCancel (@Nullable ViewController.OptionItem item) {
    if (item == null || item == ViewController.OptionItem.SEPARATOR || item.id != R.id.btn_cancel) {
      return false;
    }
    if (item.icon != 0 && item.icon != R.drawable.baseline_cancel_24) {
      return false;
    }
    return item.name == null || TextUtils.equals(item.name, Lang.getString(R.string.Cancel));
  }

  /** How many of {@code items} are rows (no separators), and how many of them are plain Cancel tiles. */
  public static int countRows (@NonNull ViewController.OptionItem[] items, boolean cancelOnly) {
    int count = 0;
    for (ViewController.OptionItem item : items) {
      if (item == null || item == ViewController.OptionItem.SEPARATOR) {
        continue;
      }
      if (!cancelOnly || isPlainCancel(item)) {
        count++;
      }
    }
    return count;
  }

  /**
   * The index of the action of a confirm sheet (exactly two rows: one action and one plain Cancel), or -1 when
   * {@code items} is any other sheet.
   */
  public static int confirmActionIndex (@NonNull ViewController.OptionItem[] items) {
    if (countRows(items, false) != 2 || countRows(items, true) != 1) {
      return -1;
    }
    for (int i = 0; i < items.length; i++) {
      ViewController.OptionItem item = items[i];
      if (item != null && item != ViewController.OptionItem.SEPARATOR && !isPlainCancel(item)) {
        return i;
      }
    }
    return -1;
  }

  /** The index of the plain Cancel tile in {@code items}, or -1. */
  public static int cancelIndex (@NonNull ViewController.OptionItem[] items) {
    for (int i = 0; i < items.length; i++) {
      if (isPlainCancel(items[i])) {
        return i;
      }
    }
    return -1;
  }

  // Buttons (ButtonSmallTokens: 40dp, full corners that morph to 8dp while pressed, labelLarge, sentence case)

  /** A filled button: primary, or error when {@code destructive}. Follows theme changes. */
  public static TextView filledButton (@NonNull Context context, @Nullable CharSequence text, boolean destructive, @Nullable ThemeDelegate forcedTheme) {
    final int container = destructive ? XgramExpressive.ERROR : XgramExpressive.PRIMARY;
    final int content = destructive ? XgramExpressive.ON_ERROR : XgramExpressive.ON_PRIMARY;
    final TextView button;
    if (forcedTheme == null) {
      button = new XgramM3Sheets.FilledButton(context, container, content);
      Views.setClickable(button);
    } else {
      button = new XgramM3Wheel.ActionButton(context, true, container, content, forcedTheme);
    }
    button.setText(text);
    return button;
  }

  /** A filled tonal button: secondaryContainer with its label in onSecondaryContainer. */
  public static TextView tonalButton (@NonNull Context context, @Nullable CharSequence text, @Nullable ThemeDelegate forcedTheme) {
    final TextView button;
    if (forcedTheme == null) {
      button = new XgramM3Sheets.FilledButton(context, XgramExpressive.SECONDARY_CONTAINER, XgramExpressive.ON_SECONDARY_CONTAINER);
      Views.setClickable(button);
    } else {
      button = new XgramM3Wheel.ActionButton(context, true, XgramExpressive.SECONDARY_CONTAINER, XgramExpressive.ON_SECONDARY_CONTAINER, forcedTheme);
    }
    button.setText(text);
    return button;
  }

  /** A text button in {@code role} (primary, or error for a destructive one) with a pill state layer. */
  public static TextView textButton (@NonNull Context context, @Nullable CharSequence text, int role, @Nullable ThemeDelegate forcedTheme) {
    TextView button = new XgramM3Wheel.ActionButton(context, false, role, role, forcedTheme);
    button.setText(text);
    return button;
  }

  /** The bottom actions row of a sheet (dp): 40dp buttons with 12dp above and below. */
  public static final float ACTIONS_HEIGHT = XgramM3Wheel.ACTIONS_HEIGHT;
  private static final float ACTIONS_GAP = 8f;

  /**
   * [start] ... [end buttons]: the sheet color (no hairline or shadow), 16dp on both sides, 40dp buttons 8dp apart,
   * {@link #ACTIONS_HEIGHT} tall, mirrored when the app language is RTL. {@code start} (a neutral text button) and
   * every entry of {@code end} may be null.
   */
  public static LinearLayout actionsRow (@NonNull Context context, @Nullable View start, @Nullable View... end) {
    final boolean rtl = Lang.rtl();
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // children are added in visual order below
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ACTIONS_HEIGHT)));

    View spacer = new View(context);
    spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

    final int count = end != null ? end.length : 0;
    final View[] order = new View[count + 2];
    int n = 0;
    if (rtl) {
      for (int i = count - 1; i >= 0; i--) {
        order[n++] = end[i];
      }
      order[n++] = spacer;
      order[n++] = start;
    } else {
      order[n++] = start;
      order[n++] = spacer;
      for (int i = 0; i < count; i++) {
        order[n++] = end[i];
      }
    }
    final int buttonHeight = Screen.dp(XgramM3Sheets.BUTTON_HEIGHT);
    final int gap = Screen.dp(ACTIONS_GAP);
    View previous = null;
    for (View child : order) {
      if (child == null) {
        continue;
      }
      if (child != spacer) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, buttonHeight);
        if (previous != null && previous != spacer) {
          params.leftMargin = gap;
        }
        child.setLayoutParams(params);
      }
      row.addView(child);
      previous = child;
    }
    return row;
  }

  // Cards (a card inside a sheet or dialog: surfaceContainerHigh)

  /** {@code v}'s background: {@code role} with {@code cornerDp} corners, read at draw time (theme changes, Pure black). */
  public static XgramExpressive.MorphDrawable applyCard (@NonNull View v, int role, float cornerDp) {
    XgramExpressive.MorphDrawable card = new XgramExpressive.MorphDrawable(0, cornerDp, Float.NaN);
    card.setColorRole(role);
    v.setBackground(card);
    return card;
  }

  // Alerts

  /** Makes button {@code which} of a shown M3 alert a destructive one (its label in error). */
  public static void markDestructive (@Nullable AlertDialog dialog, int which) {
    if (dialog == null || !XgramExpressive.isEnabled()) {
      return;
    }
    View button = dialog.getButton(which);
    if (button != null) {
      XgramM3Controls.styleButton(button, true);
    }
  }

  /** {@link #markDestructive} on the positive button. */
  public static void markPositiveDestructive (@Nullable AlertDialog dialog) {
    markDestructive(dialog, DialogInterface.BUTTON_POSITIVE);
  }

  // Hero

  /**
   * The hero of a sheet or page: an icon centered on an Expressive shape (a 9-sided cookie by default) in
   * primaryContainer, the icon in onPrimaryContainer. Colors are read at draw time; the path is built on layout only.
   */
  public static final class ShapeIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final float shapeDp, iconDp;
    private @Nullable Drawable icon;
    private RoundedPolygon polygon = XgramShapes.COOKIE_9;
    private int containerRole = XgramExpressive.PRIMARY_CONTAINER, contentRole = XgramExpressive.ON_PRIMARY_CONTAINER;
    private @Nullable ThemeDelegate forcedTheme;
    private int pathWidth = -1, pathHeight = -1;

    public ShapeIconView (Context context, float shapeDp, float iconDp) {
      super(context);
      this.shapeDp = shapeDp;
      this.iconDp = iconDp;
      paint.setStyle(Paint.Style.FILL);
    }

    public ShapeIconView setIcon (@DrawableRes int iconRes) {
      this.icon = iconRes != 0 ? Drawables.get(XgramIconPack.map(iconRes)) : null;
      invalidate();
      return this;
    }

    public ShapeIconView setShape (@NonNull RoundedPolygon polygon) {
      this.polygon = polygon;
      this.pathWidth = -1;
      invalidate();
      return this;
    }

    public ShapeIconView setRoles (int containerRole, int contentRole) {
      this.containerRole = containerRole;
      this.contentRole = contentRole;
      invalidate();
      return this;
    }

    public ShapeIconView setForcedTheme (@Nullable ThemeDelegate forcedTheme) {
      this.forcedTheme = forcedTheme;
      invalidate();
      return this;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int size = Screen.dp(shapeDp);
      setMeasuredDimension(
        getDefaultSize(size + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
        size + getPaddingTop() + getPaddingBottom()
      );
    }

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      if (width <= 0 || height <= 0) {
        return;
      }
      final float cx = getPaddingLeft() + (width - getPaddingLeft() - getPaddingRight()) / 2f;
      final float cy = getPaddingTop() + (height - getPaddingTop() - getPaddingBottom()) / 2f;
      if (pathWidth != width || pathHeight != height) {
        pathWidth = width;
        pathHeight = height;
        XgramShapes.toPath(polygon, path);
        XgramShapes.fit(path, cx, cy, Screen.dp(shapeDp), 0f, matrix);
      }
      paint.setColor(role(containerRole, forcedTheme));
      c.drawPath(path, paint);
      if (icon != null && icon.getMinimumWidth() > 0) {
        final float scale = Screen.dp(iconDp) / (float) icon.getMinimumWidth();
        final int saveCount = c.save();
        c.scale(scale, scale, cx, cy);
        iconPaint.setColorFilter(Paints.getColorFilter(role(contentRole, forcedTheme)));
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, iconPaint);
        c.restoreToCount(saveCount);
      }
    }
  }
}
