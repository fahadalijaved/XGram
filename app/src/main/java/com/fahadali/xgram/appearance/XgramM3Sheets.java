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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * Material 3 Expressive for Telegram X's own bottom sheets (share, sticker set, the {@code BottomSheetViewController}
 * sheets with the message menu, and the settings sheets): the 28dp sheet top with its drag handle, the filled S button
 * of the sheet actions, the filled tonal icon button, the flat assist chip, the menu button title of a sheet header and
 * the selected-chat check on avatars. Values follow the material3 1.5 tokens (SheetBottomTokens: CornerExtraLargeTop
 * 28dp, 32x4dp handle in onSurfaceVariant; ButtonSmallTokens and SmallIconButtonTokens: 40dp, full corners, CornerSmall
 * 8dp while pressed; AssistChipTokens: 32dp, CornerSmall, 1dp outlineVariant, labelLarge). Main thread only, and only
 * used while {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramM3Sheets {
  private XgramM3Sheets () { }

  // Sheet top

  /**
   * 1 while the sheet top is 28dp or more above the point where the status bar strip starts, 0 from there, so the 28dp
   * corners square off as the sheet docks under the status bar. The strip (the sheets' LickView, square and full width)
   * grows above the sheet over its last status bar height of travel ({@code sheetTop < 2 * topOffset}): the corners
   * are square by then, so the strip never sits over rounded corners as a notched step.
   */
  public static float cornerFactor (float sheetTop) {
    return MathUtils.clamp((sheetTop - HeaderView.getTopOffset() * 2) / Screen.dpf(XgramExpressive.CORNER_XL));
  }

  /**
   * Replaces a Telegram X header's filling with the sheet top in {@code color}. {@code extraBottom} is the part of the
   * header view below its bar (HeaderFilling's shadow height): the sheet top stays out of it, as the filling did.
   * Update the returned drawable with {@link XgramM3Chat.SheetTopDrawable#set} when the sheet moves.
   */
  public static XgramM3Chat.SheetTopDrawable attachHeaderSheetTop (@NonNull View headerView, int extraBottom, @ColorInt int color) {
    XgramM3Chat.SheetTopDrawable sheetTop = new XgramM3Chat.SheetTopDrawable();
    sheetTop.set(color, 1f);
    headerView.setBackground(extraBottom > 0 ? new InsetDrawable(sheetTop, 0, 0, 0, extraBottom) : sheetTop);
    return sheetTop;
  }

  /**
   * The sheet color of every modal bottom sheet (SheetBottomTokens.DockedContainerColor): surfaceContainerLow. The
   * option and settings sheets, the message menu, the share and the sticker set sheets all use it, so a sheet opened
   * from another one never changes tone.
   */
  @ColorInt
  public static int sheetColor () {
    return XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_LOW);
  }

  // Sheet width (M3: a bottom sheet is at most 640dp wide and centered, e.g. on the unfolded Fold or a tablet)

  /** The maximum width of a modal bottom sheet (dp). */
  public static final float SHEET_MAX_WIDTH = 640f;

  /** The width a sheet gets in {@code availableWidth} (px), for layout math done before it is measured. */
  public static int sheetWidth (int availableWidth) {
    return Math.min(availableWidth, Screen.dp(SHEET_MAX_WIDTH));
  }

  /** {@code widthMeasureSpec} of a sheet root limited to {@link #SHEET_MAX_WIDTH} (exactly that wide when it is capped). */
  public static int sheetWidthSpec (int widthMeasureSpec) {
    if (View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED) {
      final int maxWidth = Screen.dp(SHEET_MAX_WIDTH);
      if (View.MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
        return View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.EXACTLY);
      }
    }
    return widthMeasureSpec;
  }

  /** Centers a sheet root horizontally in its popup (a FrameLayout), keeping its vertical gravity. */
  public static void centerSheet (@Nullable ViewGroup.LayoutParams params) {
    if (params instanceof FrameLayout.LayoutParams) {
      FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) params;
      final int vertical = frameParams.gravity == -1 ? Gravity.TOP : (frameParams.gravity & Gravity.VERTICAL_GRAVITY_MASK);
      frameParams.gravity = (vertical != 0 ? vertical : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
    }
  }

  // Buttons

  /**
   * A filled button container on {@code v}: {@code containerRole} with full corners that morph to 8dp while pressed and
   * a state layer in the matching on-role. The container follows theme changes; the caller colors the content.
   */
  public static XgramExpressive.MorphDrawable attachFilledButton (@NonNull View v, int containerRole) {
    XgramExpressive.MorphDrawable container = XgramExpressive.attachPressMorph(v, XgramExpressive.color(containerRole), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    container.setColorRole(containerRole);
    return container;
  }

  /**
   * The filled S button of sheet actions ("Save"): labelLarge, 16dp side padding (the caller gives it 40dp of height).
   * While disabled it is onSurface at .12 with its label in onSurface at .38, never the primary label on the pill.
   */
  public static final class FilledButton extends NoScrollTextView {
    private int containerRole, contentRole;
    private @Nullable XgramExpressive.MorphDrawable container;

    public FilledButton (Context context, int containerRole, int contentRole) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setGravity(Gravity.CENTER);
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setMinWidth(Screen.dp(48f));
      setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
      setRoles(containerRole, contentRole);
    }

    /** Container and label roles, e.g. {@code ERROR} and {@code ON_ERROR} for a destructive action. */
    public void setRoles (int containerRole, int contentRole) {
      if (container != null && this.containerRole == containerRole && this.contentRole == contentRole) {
        return;
      }
      this.containerRole = containerRole;
      this.contentRole = contentRole;
      if (container == null) {
        container = attachFilledButton(this, containerRole);
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
      // Theme changes: the roles are re-read here, and the setters only act (and invalidate once) when a color changed
      syncColors();
      super.onDraw(c);
    }

    private void syncColors () {
      if (container == null) {
        return;
      }
      final int text;
      if (isEnabled()) {
        container.setColorRole(containerRole);
        text = XgramExpressive.color(contentRole);
      } else {
        final int onSurface = XgramExpressive.color(XgramExpressive.ON_SURFACE);
        container.setColor(XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER));
        text = XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT);
      }
      if (getCurrentTextColor() != text) {
        setTextColor(text);
      }
    }
  }

  // Icon buttons, chips and the menu button of sheet headers (androidx.compose.material3 1.5 tokens)

  /** SmallIconButtonTokens: 40dp container, full corners at rest ({@code ContainerShapeRound}), 8dp while pressed. */
  public static final float ICON_BUTTON_SIZE = 40f;
  /** The touch target around a 40dp icon button. */
  public static final float TOUCH_TARGET = 48f;
  /** AssistChipTokens: 32dp container, CornerSmall (8dp), 1dp FlatOutline in outlineVariant, labelLarge, 18dp icon. */
  public static final float CHIP_HEIGHT = 32f, CHIP_LEADING_ICON_SPACE = 8f, CHIP_TRAILING_SPACE = 16f;
  /** ButtonSmallTokens: 40dp container, 16dp leading and trailing space, 20dp icon, 8dp between icon and label. */
  public static final float BUTTON_HEIGHT = 40f, BUTTON_SPACE = 16f, BUTTON_ICON_SIZE = 20f, BUTTON_ICON_LABEL_SPACE = 8f;

  /** The icon color of a filled tonal button: onSecondaryContainer. */
  @ColorInt
  public static int tonalContentColor () {
    return XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
  }

  /**
   * A small filled tonal icon button on {@code v} (FilledTonalIconButtonTokens): a 40dp secondaryContainer circle
   * centered in the view (the touch target), corners morphing to 8dp while pressed, an onSecondaryContainer state
   * layer at .10. The container follows theme changes; the caller tints the icon with {@link #tonalContentColor()}.
   */
  public static XgramExpressive.MorphDrawable attachTonalIconButton (@NonNull View v) {
    XgramExpressive.MorphDrawable container = XgramExpressive.attachPressMorph(v, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    container.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
    container.setSize(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE, Gravity.CENTER);
    return container;
  }

  /**
   * The navigation (close / back) button of a sheet header as an M3E standard icon button: no container, a 40dp state
   * layer in {@code contentColor} at .10 with full corners, 8dp while pressed. HeaderView.initWithSingleController gives
   * it Telegram X's rectangular bg_btn_header highlight instead; call this after it.
   */
  public static void applyNavigationButton (@NonNull HeaderButton button, @ColorInt int contentColor) {
    if (!(button.getBackground() instanceof RippleDrawable)) {
      button.setButtonBackground(R.drawable.bg_btn_header); // HeaderButton's own M3E state layer, unless it has one
    }
    if (!(button.getBackground() instanceof RippleDrawable)) {
      button.setBackground(XgramExpressiveC.iconButtonRipple(contentColor, ICON_BUTTON_SIZE));
    }
  }

  /**
   * The container of a flat assist chip in {@code rect} (32dp tall): the 1dp outlineVariant outline with 8dp corners,
   * drawn inside the bounds. {@code rect} is restored before returning.
   */
  public static void drawAssistChipOutline (@NonNull Canvas c, @NonNull RectF rect, float alpha) {
    if (alpha <= 0f || rect.width() <= 0f || rect.height() <= 0f) {
      return;
    }
    final float half = Screen.dp(1f) / 2f; // Paints.strokeSmallPaint is 1dp wide
    rect.inset(half, half);
    final float radius = Math.max(0f, Screen.dpf(XgramExpressive.CORNER_S) - half);
    c.drawRoundRect(rect, radius, radius, Paints.strokeSmallPaint(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT), MathUtils.clamp(alpha))));
    rect.inset(-half, -half);
  }

  /**
   * The title of a sheet header that opens a menu (the share sheet's chat list picker): an M3E small filled tonal button
   * (ButtonSmallTokens + FilledTonalButtonTokens): 40dp, full corners morphing to 8dp while pressed, 16dp leading space,
   * the label in labelLarge and onSecondaryContainer (give it a theme color id that maps to that role, e.g.
   * {@code ColorId.headerTabActiveText}), then an 8dp gap, a 20dp expand_more icon and 16dp trailing space, centered in
   * the 56dp header. The subtitle line is not shown, and only the button takes touches.
   */
  public static class MenuTitleView extends DoubleHeaderView {
    private final TextView label;
    private final @Nullable Drawable icon;

    public MenuTitleView (Context context) {
      super(context);
      this.label = (TextView) getChildAt(0);
      getChildAt(1).setVisibility(View.GONE);
      XgramExpressive.applyText(label, XgramExpressive.TS_LABEL_L, false);
      label.setDuplicateParentStateEnabled(true);
      XgramExpressive.MorphDrawable container = XgramExpressive.attachPressMorph(label, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
      container.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
      this.icon = Drawables.get(R.drawable.xgram_w2_menu_sheets_expand_more_20);
      layoutLabel();
    }

    private void layoutLabel () {
      final boolean rtl = Lang.rtl();
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) label.getLayoutParams();
      params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
      params.height = Screen.dp(BUTTON_HEIGHT);
      params.topMargin = 0;
      params.gravity = Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT);
      label.setLayoutParams(params);
      label.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
      final int leading = Screen.dp(BUTTON_SPACE);
      final int trailing = Screen.dp(BUTTON_ICON_LABEL_SPACE + BUTTON_ICON_SIZE + BUTTON_SPACE);
      label.setPadding(rtl ? trailing : leading, 0, rtl ? leading : trailing, 0);
    }

    @Override
    public void checkRtl () {
      super.checkRtl();
      layoutLabel(); // DoubleHeaderView puts the title back at the top
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !(e.getX() >= label.getLeft() && e.getX() < label.getRight() && e.getY() >= label.getTop() && e.getY() < label.getBottom())) {
        return false;
      }
      return super.onTouchEvent(e);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      super.dispatchDraw(c);
      if (icon != null && label.getVisibility() == View.VISIBLE && label.getWidth() > 0) {
        final int size = Screen.dp(BUTTON_ICON_SIZE);
        final float x = Lang.rtl() ? label.getLeft() + Screen.dp(BUTTON_SPACE) : label.getRight() - Screen.dp(BUTTON_SPACE) - size;
        final float y = label.getTop() + (label.getHeight() - size) / 2f;
        Drawables.draw(c, icon, x, y, Paints.getPorterDuffPaint(XgramExpressive.withAlpha(label.getCurrentTextColor(), label.getAlpha())));
      }
    }
  }

  // Selection

  /**
   * The check of a selected chat on its avatar: a 22dp M3 checkbox in circle mode (primary with an onPrimary mark) at
   * the avatar's bottom-end, cut out of the avatar by a 2dp ring in {@code ringColor} (the sheet color). {@code factor}
   * is the view's existing check animation: it scales the check from .6 to 1 and draws the mark in.
   */
  public static void drawAvatarCheck (@NonNull Canvas c, @NonNull AvatarReceiver receiver, float factor, @ColorInt int ringColor) {
    if (factor <= 0f) {
      return;
    }
    factor = MathUtils.clamp(factor);
    final double radians = Math.toRadians(Lang.rtl() ? 315f : 45f);
    final float displayRadius = receiver.getDisplayRadius();
    final float centerX = receiver.getRight() - displayRadius;
    final float centerY = receiver.getBottom() - displayRadius;
    final float cx = centerX + (float) ((double) displayRadius * Math.sin(radians));
    final float cy = centerY + (float) ((double) displayRadius * Math.cos(radians));
    final float size = Screen.dpf(22f);
    final float scale = .6f + .4f * factor;
    final float alpha = MathUtils.clamp(factor * 2f);
    final int saveCount = c.save();
    c.scale(scale, scale, cx, cy);
    c.drawCircle(cx, cy, size / 2f + Screen.dpf(2f), Paints.fillingPaint(ColorUtils.alphaColor(alpha, ringColor)));
    XgramM3Controls.drawCheckbox(c, cx - size / 2f, cy - size / 2f, size, .5f + .5f * factor, 0f, 0f, 1f, alpha);
    c.restoreToCount(saveCount);
  }
}
