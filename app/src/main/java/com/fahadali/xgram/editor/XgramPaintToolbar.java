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
package com.fahadali.xgram.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3MediaChrome;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * A row of the photo editor's toggles over the photo: the tools (Pen to Eraser) and, above them, the Shapes choice.
 * Telegram X look: white icons on the editor's band, the picked one in the editor's blue (EditButton's active colour)
 * on bg_btn_header_light. Material 3 Expressive: a floating toolbar, a full-corner pill (the dark scheme's
 * surfaceContainerHigh at 60%, as XgramR5Media.onMediaPill) holding 40dp tonal toggle icon buttons 2dp apart; the
 * picked one fills with the dark scheme's secondaryContainer (as the connected button groups over media) as its corners
 * morph from full to 12dp on the spatial fast spring, and any button's corners tighten to 8dp while pressed. Fewer than
 * 40dp each on a narrow screen, so all the tools fit.
 */
public final class XgramPaintToolbar extends ViewGroup implements FactorAnimator.Target {
  public interface Callback {
    void onPaintToolbarClick (XgramPaintToolbar toolbar, int id);
    /** A long press shows the button's name (true when it did). */
    boolean onPaintToolbarLongPress (XgramPaintToolbar toolbar, View button, int id);
  }

  /** The tools row's height, both looks. */
  public static final float ROW_HEIGHT_DP = 56f;
  private static final float BUTTON_DP = 40f, GAP_DP = 2f, PILL_PADDING_DP = 4f, EDGE_DP = 8f;
  private static final float CLASSIC_BUTTON_HEIGHT_DP = 48f, CLASSIC_MAX_WIDTH_DP = 56f;
  /** EditButton's active colour. */
  private static final int CLASSIC_ACTIVE_COLOR = 0xff63cefd;

  private final boolean expressive;
  private final boolean classicBand;
  private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF pillRect = new RectF();
  private @Nullable Callback callback;

  /** {@code classicBand}: the Telegram X look draws its own editor band behind (a row floating over the photo). */
  public XgramPaintToolbar (Context context, boolean classicBand) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    this.classicBand = classicBand;
    setWillNotDraw(false);
  }

  public void setCallback (@Nullable Callback callback) {
    this.callback = callback;
  }

  public void setItems (int[] ids, @DrawableRes int[] icons, @StringRes int[] names) {
    removeAllViews();
    for (int i = 0; i < ids.length; i++) {
      addView(new Item(getContext(), this, ids[i], icons[i], Lang.getString(names[i])));
    }
    requestLayout();
  }

  @Nullable
  private Item findItem (int id) {
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view instanceof Item && ((Item) view).itemId == id) {
        return (Item) view;
      }
    }
    return null;
  }

  @Nullable
  public View findButton (int id) {
    return findItem(id);
  }

  public void setIcon (int id, @DrawableRes int icon) {
    Item item = findItem(id);
    if (item != null) {
      item.setIcon(icon);
    }
  }

  public void setChecked (int id, boolean animated) {
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view instanceof Item) {
        ((Item) view).setChecked(((Item) view).itemId == id, animated);
      }
    }
  }

  // Shown (the Shapes row): fades in and rises 8dp

  private BoolAnimator shownAnimator;

  public void setShown (boolean shown, boolean animated) {
    if (shownAnimator == null) {
      shownAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, !shown);
      setShownFactor(shown ? 0f : 1f);
    }
    XgramAppMotion.use(shownAnimator, shown ? XgramExpressive.SPATIAL_FAST : XgramExpressive.EFFECTS_FAST, true);
    shownAnimator.setValue(shown, animated);
    setShownFactor(shownAnimator.getFloatValue());
  }

  private void setShownFactor (float factor) {
    final float alpha = MathUtils.clamp(factor);
    setAlpha(alpha);
    setTranslationY(Screen.dp(8f) * (1f - alpha));
    setVisibility(alpha > 0f ? View.VISIBLE : View.INVISIBLE);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setShownFactor(factor);
  }

  // Layout

  private int buttonWidth, buttonHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int height = MeasureSpec.getSize(heightMeasureSpec);
    setMeasuredDimension(width, height);
    final int count = getChildCount();
    if (count == 0) {
      return;
    }
    if (expressive) {
      final int available = width - Screen.dp(EDGE_DP + PILL_PADDING_DP) * 2 - Screen.dp(GAP_DP) * (count - 1);
      buttonWidth = buttonHeight = Math.max(1, Math.min(Screen.dp(BUTTON_DP), available / count));
    } else {
      buttonWidth = Math.max(1, Math.min(Screen.dp(CLASSIC_MAX_WIDTH_DP), width / count));
      buttonHeight = Math.min(height, Screen.dp(CLASSIC_BUTTON_HEIGHT_DP));
    }
    final int widthSpec = MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY);
    final int heightSpec = MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY);
    for (int i = 0; i < count; i++) {
      getChildAt(i).measure(widthSpec, heightSpec);
    }
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int count = getChildCount();
    if (count == 0) {
      return;
    }
    final int gap = expressive ? Screen.dp(GAP_DP) : 0;
    final int total = buttonWidth * count + gap * (count - 1);
    final int left = (getMeasuredWidth() - total) / 2;
    final int top = (getMeasuredHeight() - buttonHeight) / 2;
    final boolean rtl = Lang.rtl();
    for (int i = 0; i < count; i++) {
      // the first tool at the start: on the right in RTL
      final int index = rtl ? count - 1 - i : i;
      final int x = left + index * (buttonWidth + gap);
      getChildAt(i).layout(x, top, x + buttonWidth, top + buttonHeight);
    }
    final int padding = Screen.dp(PILL_PADDING_DP);
    pillRect.set(left - padding, top - padding, left + total + padding, top + buttonHeight + padding);
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    if (getChildCount() == 0) {
      return;
    }
    if (expressive) {
      pillPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), .6f));
      final float radius = pillRect.height() / 2f;
      c.drawRoundRect(pillRect, radius, radius, pillPaint);
    } else if (classicBand) {
      c.drawColor(Theme.getColor(ColorId.transparentEditor));
    }
  }

  // Buttons

  private static final class Item extends View implements FactorAnimator.Target {
    final int itemId;
    private final XgramPaintToolbar toolbar;
    private @Nullable Drawable icon;
    private @DrawableRes int iconRes;
    private boolean checked;

    // Telegram X look: the icon's colour
    private BoolAnimator checkedAnimator;
    // Expressive: the container's fill, its corners, the pressed state
    private XgramExpressive.Spring checkedSpring, shapeSpring, pressSpring;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    Item (Context context, XgramPaintToolbar toolbar, int itemId, @DrawableRes int iconRes, String name) {
      super(context);
      this.toolbar = toolbar;
      this.itemId = itemId;
      setIcon(iconRes);
      setContentDescription(name);
      Views.setClickable(this);
      setOnClickListener(v -> {
        if (toolbar.callback != null) {
          toolbar.callback.onPaintToolbarClick(toolbar, itemId);
        }
      });
      setOnLongClickListener(v -> toolbar.callback != null && toolbar.callback.onPaintToolbarLongPress(toolbar, v, itemId));
      if (toolbar.expressive) {
        XgramExpressive.Spring.Listener invalidate = (s, value) -> invalidate();
        checkedSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
        shapeSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate);
        pressSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
      } else {
        setBackgroundResource(R.drawable.bg_btn_header_light);
        checkedAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      }
    }

    void setIcon (@DrawableRes int iconRes) {
      if (this.iconRes != iconRes) {
        this.iconRes = iconRes;
        this.icon = Drawables.get(iconRes);
        invalidate();
      }
    }

    void setChecked (boolean checked, boolean animated) {
      if (this.checked == checked && (animated || checkedValue() == (checked ? 1f : 0f))) {
        return;
      }
      this.checked = checked;
      setSelected(checked);
      if (checkedAnimator != null) {
        checkedAnimator.setValue(checked, animated);
      } else if (animated) {
        checkedSpring.animateTo(checked ? 1f : 0f);
        shapeSpring.animateTo(checked ? 1f : 0f);
      } else {
        checkedSpring.setValue(checked ? 1f : 0f);
        shapeSpring.setValue(checked ? 1f : 0f);
      }
      invalidate();
    }

    private float checkedValue () {
      return checkedAnimator != null ? checkedAnimator.getFloatValue() : checkedSpring.getValue();
    }

    @Override
    public void setPressed (boolean pressed) {
      super.setPressed(pressed);
      if (pressSpring != null) {
        pressSpring.animateTo(pressed ? 1f : 0f);
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      invalidate();
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final int width = getWidth(), height = getHeight();
      final int iconColor;
      if (toolbar.expressive) {
        final float checked = MathUtils.clamp(checkedSpring.getValue());
        final float shape = Math.max(0f, Math.min(1.2f, shapeSpring.getValue()));
        final float press = MathUtils.clamp(pressSpring.getValue());
        final float full = Math.min(width, height) / 2f;
        float radius = full + (Screen.dpf(XgramExpressive.CORNER_M) - full) * shape;
        radius += (Screen.dpf(XgramExpressive.CORNER_S) - radius) * press;
        radius = Math.max(0f, Math.min(full, radius));
        rect.set(0, 0, width, height);
        iconColor = ColorUtils.fromToArgb(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE), XgramM3MediaChrome.onMedia(XgramExpressive.ON_SECONDARY_CONTAINER), checked);
        if (checked > 0f) {
          paint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SECONDARY_CONTAINER), checked));
          c.drawRoundRect(rect, radius, radius, paint);
        }
        if (press > 0f) {
          paint.setColor(XgramExpressive.withAlpha(iconColor, XgramExpressive.STATE_PRESSED * press));
          c.drawRoundRect(rect, radius, radius, paint);
        }
      } else {
        iconColor = ColorUtils.fromToArgb(0xffffffff, CLASSIC_ACTIVE_COLOR, checkedAnimator.getFloatValue());
      }
      if (icon != null) {
        Drawables.draw(c, icon, (width - icon.getMinimumWidth()) / 2f, (height - icon.getMinimumHeight()) / 2f, Paints.getPorterDuffPaint(iconColor));
      }
    }
  }
}
