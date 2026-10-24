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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramExpressiveC;
import com.fahadali.xgram.appearance.XgramIconPack;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;

/**
 * Select text's layer over the chat (see {@link XgramTextSelection}): the two handles at the selection's ends and the
 * toolbar by it. As big as the chat, it draws only inside the part of the list in sight, and only takes the touches of
 * its handles and its toolbar: everything else goes on to the list and the bars under it. Placed before every frame
 * ({@link XgramTextSelection#onLayerPreDraw}), so it follows the message when the list moves.
 *
 * Handles: Android's drops, primary with Expressive design, Telegram X's input colour without. Toolbar: Expressive, a
 * surfaceContainerHigh pill at elevation level 2 with text buttons, springing in; Telegram X, its popup menu's card.
 * The buttons are labelled when they fit, else icons named for TalkBack and in tooltips (a narrow cover screen).
 */
@SuppressLint("ViewConstructor")
final class XgramTextSelectionLayer extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
  /** A handle: a drop of this radius (dp), taken by a finger this far around it (dp). */
  private static final float HANDLE_RADIUS = 11f, HANDLE_REACH = 14f;
  private static final long CLASSIC_FADE_MS = 150L;

  private final XgramTextSelection selection;
  private final boolean m3;
  private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  /** A drop hanging from its point (at 0, 0) to the left, as the start of left-to-right text has it, or to the right. */
  private final Path dropLeft = new Path(), dropRight = new Path();
  private final float radius;

  private boolean startShown, endShown, startLeft, endLeft;
  private float startX, startY, endX, endY;
  /** The part of the list in sight: the handles are drawn and taken only there. */
  private float clipLeft, clipTop, clipRight, clipBottom;
  private @ColorInt int handleColor;

  private final LinearLayout toolbar;
  private int toolbarActions = -1, toolbarMaxWidth = -1;
  private boolean toolbarShown;
  private @Nullable XgramExpressive.Spring toolbarSpring;

  private boolean touchHandle, touchToolbar;

  XgramTextSelectionLayer (Context context, XgramTextSelection selection) {
    super(context);
    this.selection = selection;
    this.m3 = XgramExpressive.isEnabled();
    setWillNotDraw(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // its toolbar's buttons are still read

    radius = Screen.dp(HANDLE_RADIUS);
    dropLeft.addCircle(-radius, radius, radius, Path.Direction.CW);
    dropLeft.addRect(-radius, 0f, 0f, radius, Path.Direction.CW);
    dropRight.addCircle(radius, radius, radius, Path.Direction.CW);
    dropRight.addRect(0f, 0f, radius, radius, Path.Direction.CW);

    toolbar = new LinearLayout(context);
    toolbar.setOrientation(LinearLayout.HORIZONTAL);
    toolbar.setGravity(Gravity.CENTER_VERTICAL);
    if (m3) {
      // Expressive: a surfaceContainerHigh pill (the chip step with Pure black, as every floating container) at
      // elevation level 2
      final XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, Float.NaN);
      background.setFloatingColorRole(XgramExpressive.SURFACE_CONTAINER_HIGH);
      background.setHost(toolbar);
      toolbar.setBackground(background);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        toolbar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        toolbar.setElevation(Screen.dp(XgramExpressive.ELEVATION_2));
      }
      final int padding = Screen.dp(4f);
      toolbar.setPadding(padding, padding, padding, padding);
    } else {
      // Telegram X: its popup menu's card and shadow
      ViewUtils.setBackground(toolbar, ViewSupport.getDrawableFilter(context, R.drawable.bg_popup_fixed, new PorterDuffColorFilter(Theme.headerFloatBackgroundColor(), PorterDuff.Mode.MULTIPLY)));
      toolbar.setScaleX(.9f);
      toolbar.setScaleY(.9f);
    }
    toolbar.setAlpha(0f);
    toolbar.setVisibility(View.INVISIBLE);
    addView(toolbar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
  }

  // Placed before every frame

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnPreDrawListener(this);
  }

  @Override
  protected void onDetachedFromWindow () {
    getViewTreeObserver().removeOnPreDrawListener(this);
    super.onDetachedFromWindow();
  }

  @Override
  public boolean onPreDraw () {
    selection.onLayerPreDraw();
    return true;
  }

  // Handles

  /** Where the handles are (their points: the selection's ends on their lines' bottoms), and the part of the list in sight. */
  void setHandles (boolean startShown, float startX, float startY, boolean startLeft, boolean endShown, float endX, float endY, boolean endLeft, @ColorInt int color, float left, float top, float right, float bottom) {
    if (this.startShown != startShown || this.startX != startX || this.startY != startY || this.startLeft != startLeft ||
      this.endShown != endShown || this.endX != endX || this.endY != endY || this.endLeft != endLeft || this.handleColor != color ||
      clipLeft != left || clipTop != top || clipRight != right || clipBottom != bottom) {
      this.startShown = startShown;
      this.startX = startX;
      this.startY = startY;
      this.startLeft = startLeft;
      this.endShown = endShown;
      this.endX = endX;
      this.endY = endY;
      this.endLeft = endLeft;
      this.handleColor = color;
      this.clipLeft = left;
      this.clipTop = top;
      this.clipRight = right;
      this.clipBottom = bottom;
      invalidate();
    }
  }

  /** How far a handle hangs below its point. */
  float getHandleHeight () {
    return radius * 2f;
  }

  /** The handle a touch at ({@code x}, {@code y}) takes (the nearest, when both are in reach). */
  int handleAt (float x, float y) {
    if (y < clipTop || y > clipBottom + radius * 2f) {
      return XgramTextSelection.HANDLE_NONE;
    }
    final float start = startShown ? reach(x, y, startX, startY, startLeft) : -1f;
    final float end = endShown ? reach(x, y, endX, endY, endLeft) : -1f;
    if (start >= 0f && (end < 0f || start <= end)) {
      return XgramTextSelection.HANDLE_START;
    }
    return end >= 0f ? XgramTextSelection.HANDLE_END : XgramTextSelection.HANDLE_NONE;
  }

  /** The squared distance from a touch to a drop's centre, -1 when out of reach. */
  private float reach (float x, float y, float pointX, float pointY, boolean left) {
    final float centerX = left ? pointX - radius : pointX + radius, centerY = pointY + radius;
    final float reach = radius + Screen.dp(HANDLE_REACH);
    final float dx = Math.abs(x - centerX), dy = Math.abs(y - centerY);
    return dx <= reach && dy <= reach ? dx * dx + dy * dy : -1f;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (!startShown && !endShown) {
      return;
    }
    handlePaint.setColor(handleColor);
    final int saveCount = c.save();
    c.clipRect(clipLeft, clipTop, clipRight, clipBottom);
    if (startShown) {
      drawDrop(c, startX, startY, startLeft);
    }
    if (endShown) {
      drawDrop(c, endX, endY, endLeft);
    }
    c.restoreToCount(saveCount);
  }

  private void drawDrop (Canvas c, float x, float y, boolean left) {
    c.save();
    c.translate(x, y);
    c.drawPath(left ? dropLeft : dropRight, handlePaint);
    c.restore();
  }

  // Touches: the handles and the toolbar; the rest goes through

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    final int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      touchHandle = touchToolbar = false;
      if (isOnToolbar(e.getX(), e.getY())) {
        touchToolbar = true;
        return super.dispatchTouchEvent(e);
      }
      if (selection.onLayerTouch(e)) {
        touchHandle = true;
        return true;
      }
      selection.onLayerOutsideDown(e.getX() >= clipLeft && e.getX() < clipRight && e.getY() >= clipTop && e.getY() < clipBottom);
      return false; // to the list or the bars under the layer
    }
    final boolean ends = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    if (touchHandle) {
      selection.onLayerTouch(e);
      if (ends) {
        touchHandle = false;
      }
      return true;
    }
    if (touchToolbar) {
      final boolean result = super.dispatchTouchEvent(e);
      if (ends) {
        touchToolbar = false;
      }
      return result;
    }
    return false;
  }

  private boolean isOnToolbar (float x, float y) {
    return toolbarShown && toolbar.getVisibility() == View.VISIBLE &&
      x >= toolbar.getX() && x < toolbar.getX() + toolbar.getWidth() && y >= toolbar.getY() && y < toolbar.getY() + toolbar.getHeight();
  }

  // Toolbar

  /** The toolbar's buttons (XgramTextSelection.ACTION_*), built again only when they or the room for them change. */
  void setToolbarActions (int actions, int maxWidth) {
    if (actions == toolbarActions && maxWidth == toolbarMaxWidth) {
      return;
    }
    if (actions != toolbarActions) {
      overflow = false; // a new selection starts on the main page
    }
    toolbarActions = actions;
    toolbarMaxWidth = maxWidth;
    rebuildToolbar();
  }

  /** The toolbar's buttons as labels, or as icons when the labels don't fit in {@link #toolbarMaxWidth}. */
  private void rebuildToolbar () {
    buildToolbar(false);
    final int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    toolbar.measure(unspecified, unspecified);
    if (toolbar.getMeasuredWidth() > toolbarMaxWidth) {
      buildToolbar(true);
      toolbar.measure(unspecified, unspecified);
    }
  }

  /** ⋮ shows the toolbar's second page (‹ Select all, Share to other apps) in the same pill; ‹ goes back. */
  private boolean overflow;

  private void setOverflow (boolean overflow) {
    if (this.overflow == overflow) {
      return;
    }
    this.overflow = overflow;
    if (com.fahadali.xgram.appearance.XgramMotion.reduce()) {
      rebuildToolbar();
      toolbar.requestLayout();
      return;
    }
    // Fade through: the page fades out, the other one fades in at its own width (the pill is placed again each frame)
    toolbar.animate().cancel();
    toolbar.animate().alpha(0f).setDuration(90).setInterpolator(AnimatorUtils.ACCELERATE_INTERPOLATOR).withEndAction(() -> {
      rebuildToolbar();
      toolbar.requestLayout();
      toolbar.animate().alpha(1f).setDuration(180).setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR).start();
    }).start();
  }

  int getToolbarWidth () {
    return toolbar.getMeasuredWidth();
  }

  int getToolbarHeight () {
    return toolbar.getMeasuredHeight();
  }

  /** The toolbar's top left corner; {@code above}: it is above the selection (it springs in from its bottom edge). */
  void placeToolbar (float x, float y, boolean above) {
    toolbar.setTranslationX(x);
    toolbar.setTranslationY(y);
    toolbar.setPivotX(toolbar.getMeasuredWidth() / 2f);
    toolbar.setPivotY(above ? toolbar.getMeasuredHeight() : 0f);
  }

  void setToolbarShown (boolean shown) {
    if (toolbarShown == shown) {
      return;
    }
    toolbarShown = shown;
    if (shown) {
      toolbar.setVisibility(View.VISIBLE);
    }
    if (m3) {
      if (toolbarSpring == null) {
        toolbarSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (spring, value) -> {
          toolbar.setAlpha(Math.max(0f, Math.min(1f, value)));
          final float scale = .8f + .2f * value;
          toolbar.setScaleX(scale);
          toolbar.setScaleY(scale);
        });
        toolbarSpring.setEndListener(this::onToolbarSettled);
      }
      toolbarSpring.animateTo(shown ? 1f : 0f);
    } else {
      toolbar.animate().cancel();
      toolbar.animate()
        .alpha(shown ? 1f : 0f)
        .scaleX(shown ? 1f : .9f)
        .scaleY(shown ? 1f : .9f)
        .setDuration(CLASSIC_FADE_MS)
        .setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR)
        .withEndAction(this::onToolbarSettled)
        .start();
    }
  }

  private void onToolbarSettled () {
    if (!toolbarShown) {
      toolbar.setVisibility(View.INVISIBLE);
      if (overflow) {
        overflow = false; // shown again on its main page
        rebuildToolbar();
      }
    }
  }

  private void buildToolbar (boolean iconsOnly) {
    toolbar.removeAllViews();
    if (overflow) {
      addButton(~0, R.id.btn_xgramSelBack, R.string.XgramSelBack, R.drawable.baseline_arrow_back_24, true);
      addButton(XgramTextSelection.ACTION_SELECT_ALL, R.id.btn_xgramSelAll, R.string.XgramSelAll, XgramTextSelection.ICON, iconsOnly);
      addButton(XgramTextSelection.ACTION_SHARE, R.id.btn_xgramSelShare, R.string.XgramSelShareApps, R.drawable.baseline_share_24, iconsOnly);
      return;
    }
    addButton(XgramTextSelection.ACTION_COPY, R.id.btn_xgramSelCopy, R.string.Copy, R.drawable.baseline_content_copy_24, iconsOnly);
    addButton(XgramTextSelection.ACTION_QUOTE, R.id.btn_xgramSelQuote, R.string.XgramQuote, R.drawable.baseline_format_quote_close_24, iconsOnly);
    addButton(XgramTextSelection.ACTION_FORWARD, R.id.btn_xgramSelForward, R.string.XgramSelForward, R.drawable.baseline_forward_24, iconsOnly);
    addButton(XgramTextSelection.ACTION_TRANSLATE, R.id.btn_xgramSelTranslate, R.string.Translate, R.drawable.baseline_translate_24, iconsOnly);
    // Select all and Share to other apps under ⋮: the row stays four labels wide, nothing cut off
    if ((toolbarActions & (XgramTextSelection.ACTION_SELECT_ALL | XgramTextSelection.ACTION_SHARE)) != 0) {
      addButton(~0, R.id.btn_xgramSelMore, R.string.XgramSelMore, R.drawable.baseline_more_vert_24, true);
    }
  }



  private void addButton (int action, @IdRes int id, @StringRes int name, @DrawableRes int icon, boolean iconOnly) {
    if ((toolbarActions & action) == 0) {
      return;
    }
    final Context context = getContext();
    final String label = Lang.getString(name);
    final View button;
    final LinearLayout.LayoutParams params;
    if (iconOnly) {
      final ImageView image = new ImageView(context);
      image.setScaleType(ImageView.ScaleType.CENTER);
      image.setImageResource(XgramIconPack.map(icon));
      final int color = m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.getColor(ColorId.icon);
      image.setColorFilter(color);
      image.setContentDescription(label);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        image.setTooltipText(label);
      }
      if (m3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        image.setBackground(XgramExpressiveC.iconButtonRipple(color, XgramExpressiveC.ICON_BUTTON_BOX));
      } else {
        RippleSupport.setTransparentSelector(image);
      }
      button = image;
      params = new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(m3 ? 40f : 48f));
    } else {
      final TextView textView = new TextView(context);
      textView.setText(label);
      textView.setSingleLine(true);
      textView.setGravity(Gravity.CENTER);
      if (m3) {
        // Expressive: labelLarge text buttons in onSurface, a pill state layer that squares off a little when pressed
        XgramExpressive.applyText(textView, XgramExpressive.TS_LABEL_L, false);
        textView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
        textView.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
        XgramExpressive.attachPressMorph(textView, 0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_M);
      } else {
        textView.setTypeface(Fonts.getRobotoMedium());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        textView.setTextColor(Theme.textAccentColor());
        textView.setPadding(Screen.dp(14f), 0, Screen.dp(14f), 0);
        RippleSupport.setTransparentSelector(textView);
      }
      button = textView;
      params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(m3 ? 40f : 48f));
    }
    button.setId(id);
    button.setOnClickListener(v -> {
      if (v.getId() == R.id.btn_xgramSelMore) {
        setOverflow(true);
      } else if (v.getId() == R.id.btn_xgramSelBack) {
        setOverflow(false);
      } else {
        selection.onAction(v.getId());
      }
    });
    toolbar.addView(button, params);
  }
}
