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
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Screen;

/**
 * Material 3 Expressive docked toolbar for selected messages (MessagesController): it takes the composer row's place
 * while messages are selected (the bar is opaque and at least as tall as what it covers, from the composer's top to
 * the screen's bottom), so nothing floats over the messages and nothing moves. A flat full-width surfaceContainer bar,
 * 64dp of items above the bottom inset: each an icon over its label (labelMedium, onSurfaceVariant; a destructive one
 * in error), evenly spread, with a round state layer. It slides up from the bottom edge as the selection starts and
 * back down as it ends (a fade only with Reduce motion), and takes every touch while it is up.
 */
public final class XgramDockedToolbar extends LinearLayout {
  private static final float ITEMS_HEIGHT = 64f;

  private final SparseBooleanArray destructive = new SparseBooleanArray();
  private @ColorInt int contentColor, errorColor, containerColor;
  private boolean shown;
  private int bottomInset, minCoverHeight;

  public XgramDockedToolbar (Context context) {
    super(context);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.TOP);
    setClickable(true); // the composer under it takes no touch while it is up
    setVisibility(View.GONE);
    setAlpha(0f);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(null); // above the composer's views, without a shadow
      setTranslationZ(Screen.dp(16f));
    }
    readColors();
    setBackgroundColor(containerColor);
  }

  private void readColors () {
    contentColor = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    errorColor = XgramExpressive.color(XgramExpressive.ERROR);
    containerColor = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER);
  }

  /** An item: {@code iconRes} (through the icon pack) over {@code label}; {@code isDestructive} in error (delete). */
  public View addButton (int id, int iconRes, CharSequence label, View.OnClickListener onClick, boolean isDestructive) {
    final LinearLayout item = new LinearLayout(getContext());
    item.setId(id);
    item.setOrientation(VERTICAL);
    item.setGravity(Gravity.CENTER);
    item.setOnClickListener(onClick);
    item.setContentDescription(label);
    destructive.put(id, isDestructive);
    final int color = isDestructive ? errorColor : contentColor;

    final ImageView icon = new ImageView(getContext());
    icon.setScaleType(ImageView.ScaleType.CENTER);
    icon.setImageResource(XgramIconPack.map(iconRes));
    icon.setColorFilter(color);
    item.addView(icon, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));

    final TextView text = new TextView(getContext());
    XgramExpressive.applyText(text, XgramExpressive.TS_LABEL_M, false);
    text.setTextColor(color);
    text.setText(label);
    text.setSingleLine(true);
    text.setEllipsize(android.text.TextUtils.TruncateAt.END);
    text.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    textParams.topMargin = Screen.dp(4f);
    item.addView(text, textParams);

    item.setBackground(ripple(color));
    addView(item, new LinearLayout.LayoutParams(0, Screen.dp(ITEMS_HEIGHT), 1f));
    return item;
  }

  private static RippleDrawable ripple (@ColorInt int color) {
    RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(XgramExpressive.withAlpha(color, XgramExpressive.STATE_PRESSED)), null, null);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      ripple.setRadius(Screen.dp(28f));
    }
    return ripple;
  }

  /** The bottom inset (the navigation bar, the gap under the composer, the keyboard): under the items. */
  public void setBottomInset (int bottomInset) {
    if (this.bottomInset != bottomInset) {
      this.bottomInset = bottomInset;
      requestLayout();
    }
  }

  /** At least this tall: what it covers, from the composer row's top to the bottom. */
  public void setMinCoverHeight (int minCoverHeight) {
    if (this.minCoverHeight != minCoverHeight) {
      this.minCoverHeight = minCoverHeight;
      requestLayout();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int items = Screen.dp(ITEMS_HEIGHT);
    final int height = Math.max(items + bottomInset, minCoverHeight);
    // The items in the top 64dp of the cover (the rest is the composer's gap and the inset)
    final int bottomPadding = height - items;
    if (getPaddingBottom() != bottomPadding) {
      setPadding(0, 0, 0, bottomPadding);
    }
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
  }

  public boolean isToolbarShown () {
    return shown;
  }

  /** Slides up from the bottom edge (spatial default) or down and out (effects fast); a fade with Reduce motion. */
  public void setShown (boolean shown, boolean animated) {
    if (this.shown == shown) {
      return;
    }
    this.shown = shown;
    animate().cancel();
    final boolean reduce = XgramMotion.reduce();
    if (shown) {
      if (getVisibility() != View.VISIBLE) {
        setVisibility(View.VISIBLE);
        setAlpha(0f);
        setTranslationY(reduce || !animated ? 0f : Screen.dp(ITEMS_HEIGHT));
      }
      if (!animated) {
        setAlpha(1f);
        setTranslationY(0f);
        return;
      }
      animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(0)
        .setDuration(XgramAppMotion.springDuration(XgramExpressive.SPATIAL_DEFAULT))
        .setInterpolator(XgramAppMotion.springClamped(XgramExpressive.SPATIAL_DEFAULT))
        .start();
    } else {
      if (getVisibility() == View.GONE) {
        return;
      }
      if (!animated) {
        setAlpha(0f);
        setVisibility(View.GONE);
        return;
      }
      animate()
        .alpha(0f)
        .translationY(reduce ? 0f : getHeight() > 0 ? getHeight() : Screen.dp(ITEMS_HEIGHT))
        .setStartDelay(0)
        .setDuration(XgramAppMotion.springDuration(XgramExpressive.EFFECTS_FAST))
        .setInterpolator(XgramAppMotion.springClamped(XgramExpressive.EFFECTS_FAST))
        .withEndAction(() -> {
          if (!this.shown) {
            setVisibility(View.GONE);
          }
        })
        .start();
    }
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    // A leaving bar takes no new touch (it goes to the composer under it); a gesture already on it still ends
    if (!shown && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      return false;
    }
    return super.dispatchTouchEvent(e);
  }

  @Override
  protected void dispatchDraw (@NonNull Canvas c) {
    final int content = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    final int error = XgramExpressive.color(XgramExpressive.ERROR);
    final int container = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER);
    if (content != contentColor || error != errorColor || container != containerColor) {
      // A theme change: the bar, the icons, the labels and the state layers take the new colors
      contentColor = content;
      errorColor = error;
      containerColor = container;
      setBackgroundColor(container);
      for (int i = 0; i < getChildCount(); i++) {
        final View child = getChildAt(i);
        final int tint = destructive.get(child.getId()) ? error : content;
        if (child instanceof LinearLayout) {
          final LinearLayout item = (LinearLayout) child;
          for (int j = 0; j < item.getChildCount(); j++) {
            final View part = item.getChildAt(j);
            if (part instanceof ImageView) {
              ((ImageView) part).setColorFilter(tint);
            } else if (part instanceof TextView) {
              ((TextView) part).setTextColor(tint);
            }
          }
          child.setBackground(ripple(tint));
        }
      }
    }
    super.dispatchDraw(c);
  }
}
