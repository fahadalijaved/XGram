/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 02/02/2016 at 19:20
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ReactionLoadListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class CircleButton extends View implements FactorAnimator.Target, ReactionLoadListener, Destroyable {
  private Drawable icon;
  private int offsetLeft;

  private int iconColor;
  private boolean iconColorIsId;
  private int backgroundColorId;

  private int crossBackgroundColorId, crossIconColorId;

  private final @Nullable Tdlib tdlib;
  private final @Nullable ImageReceiver imageReceiver;
  private final @Nullable GifReceiver gifReceiver;

  public CircleButton (Context context) {
    super(context);
    Views.setClickable(this);

    this.tdlib = null;
    this.imageReceiver = null;
    this.gifReceiver = null;
  }

  public CircleButton (Context context, @NonNull Tdlib tdlib) {
    super(context);
    Views.setClickable(this);

    this.tdlib = tdlib;
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);
  }

  public void setIconColorId (int colorId) {
    xgramIconRole = -1;
    if (!this.iconColorIsId || this.iconColor != colorId) {
      this.iconColorIsId = true;
      this.iconColor = colorId;
      if (this.imageReceiver != null) {
        this.imageReceiver.setThemedPorterDuffColorId(colorId);
      }
      if (this.gifReceiver != null) {
        this.gifReceiver.setThemedPorterDuffColorId(colorId);
      }
      invalidate();
    }
  }

  public void setCustomIconColor (int color) {
    xgramIconRole = -1;
    if (this.iconColorIsId || this.iconColor != color) {
      this.iconColorIsId = false;
      this.iconColor = color;
      if (this.imageReceiver != null) {
        this.imageReceiver.setPorterDuffColorFilter(color);
      }
      if (this.gifReceiver != null) {
        this.gifReceiver.setPorterDuffColorFilter(color);
      }
      invalidate();
    }
  }

  private String bottomText;
  private float bottomTextWidth;

  public void setBottomText (String bottomText) {
    if (bottomText != null && bottomText.isEmpty()) {
      bottomText = null;
    }
    if (!StringUtils.equalsOrBothEmpty(this.bottomText, bottomText)) {
      this.bottomText = bottomText;
      this.bottomTextWidth = U.measureText(bottomText, Paints.getRegularTextPaint(14f));
      invalidate();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN && xgramHasLabel()) {
      // XGram: an extended FAB is laid out at its full width; only its current container takes touches
      final float containerW = Screen.dpf(xgramContainerDp(xgramSizeDp));
      final float x = e.getX();
      if ((Lang.rtl() ? x > containerW : x < getWidth() - containerW) || e.getY() > Screen.dpf(xgramSizeDp)) {
        return false;
      }
    }
    return !(e.getAction() == MotionEvent.ACTION_DOWN && (getAlpha() == 0f || !isEnabled() || (getParent() != null && ((View) getParent()).getAlpha() == 0f))) && super.onTouchEvent(e);
  }

  public final void init (@DrawableRes int icon, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId) {
    init(icon, 0, size, padding, backgroundColorId, iconColorId);
  }

  public void init (@DrawableRes int icon, int offsetLeft, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId) {
    init(icon, offsetLeft, size, padding, backgroundColorId, iconColorId, true);
  }

  public void init (@DrawableRes int icon, int offsetLeft, float size, float padding, @ColorId int backgroundColorId, @ColorId int iconColorId, boolean needShadow) {
    this.icon = Drawables.get(icon);
    this.offsetLeft = offsetLeft;
    this.backgroundColorId = backgroundColorId;
    this.iconColorIsId = true;
    this.iconColor = iconColorId;
    if (xgramInit(size, backgroundColorId, needShadow)) {
      return;
    }
    RippleSupport.setCircleBackground(this, size, padding, backgroundColorId, needShadow, null);
  }

  // XGram M3E. FAB sizes (56, 80, 96) get the FAB container: corner 16/20/28, elevation level 3, ripple only (no press morph).
  // Smaller circles become icon buttons whose corners morph from full to 8dp while pressed (fast spatial spring).
  // Other sizes (the 70.5dp call buttons) keep the old circle.

  private XgramExpressive.MorphDrawable xgramMorph, xgramMask;
  private RippleDrawable xgramRipple;
  private int xgramRippleColor;
  private float xgramSizeDp, xgramZ;
  private boolean xgramFab, xgramToggle;
  private int xgramContainerRole = -1, xgramIconRole = -1;
  private float xgramToggleFactor;
  private Drawable xgramCloseIcon;

  private static float xgramFabCorner (float sizeDp) {
    return sizeDp >= 96f ? XgramExpressive.CORNER_XL : sizeDp >= 80f ? XgramExpressive.CORNER_L_INC : XgramExpressive.CORNER_L;
  }

  private static float xgramFabIcon (float sizeDp) {
    return sizeDp >= 96f ? 36f : sizeDp >= 80f ? 28f : 24f;
  }

  private boolean xgramInit (float size, @ColorId int backgroundColorId, boolean needShadow) {
    if (!XgramExpressive.isEnabled()) {
      return false;
    }
    if (size == 48f && backgroundColorId == ColorId.circleButtonChat) {
      return xgramInitChatFab(needShadow); // the chat's floating buttons (scroll to bottom, mentions, reactions, search)
    }
    final boolean fab;
    if (size < 56f) {
      fab = false;
    } else if (size < 64f || size == 80f || size == 96f) {
      fab = true;
    } else {
      return false;
    }
    this.xgramFab = fab;
    this.xgramSizeDp = size;
    final float resting = fab ? xgramFabCorner(size) : XgramExpressive.CORNER_FULL;
    final float pressed = fab ? resting : XgramExpressive.CORNER_S;
    xgramMorph = new XgramExpressive.MorphDrawable(0, resting, pressed);
    xgramMask = new XgramExpressive.MorphDrawable(0xff000000, resting, pressed);
    xgramMorph.setSize(size, size, Gravity.CENTER);
    xgramMask.setSize(size, size, Gravity.CENTER);
    if (fab && backgroundColorId == ColorId.circleButtonRegular) {
      // The FAB color: primaryContainer / onPrimaryContainer
      xgramContainerRole = XgramExpressive.PRIMARY_CONTAINER;
      xgramIconRole = XgramExpressive.ON_PRIMARY_CONTAINER;
      xgramMorph.setColorRole(xgramContainerRole);
    } else {
      xgramContainerRole = xgramIconRole = -1;
      xgramMorph.applyColor(backgroundColorId, backgroundColorId, 0f);
    }
    xgramMorph.setHost(this);
    xgramMask.setHost(this);
    xgramRippleColor = XgramExpressive.withAlpha(xgramIconColor(), XgramExpressive.STATE_PRESSED);
    // The mask is wrapped so RippleSupport.changeViewColor recolors the container only
    xgramRipple = new RippleDrawable(ColorStateList.valueOf(xgramRippleColor), xgramMorph, new InsetDrawable(xgramMask, 0));
    setBackground(xgramRipple);
    setStateListAnimator(null);
    setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    xgramZ = needShadow ? Screen.dp(fab ? XgramExpressive.ELEVATION_3 : XgramExpressive.ELEVATION_2) : 0;
    setElevation(xgramZ);
    xgramSyncTagZ();
    return true;
  }

  // XGram M3E: the chat's floating buttons (Telegram X's 48dp circles) are M3 secondary FABs: a 48dp container (between
  // the small FAB's 40dp and the FAB's 56dp, Telegram X's own size, so they sit lighter over the messages) with 14dp
  // corners (about .29 of the side, as the small FAB's 12dp and the FAB's 16dp), or the avatars' shape with "Chat
  // buttons match avatar shape" on (xgramChatFabCornerDp), secondaryContainer with a 24dp onSecondaryContainer icon at
  // its own size (crisp), elevation level 3 (6dp), the onSecondaryContainer state layer at 10% (ripple only: M3 FABs
  // keep their shape when pressed, so there is no pressed shape to follow). The container is centred in the view, so a
  // view laid out larger (56dp, still the 48dp touch target and more) leaves room for the badge on its corner.

  public static final float XGRAM_CHAT_FAB_DP = 48f;

  /** The chat FABs' corner (dp) with "Chat buttons match avatar shape" off: the M3E rounded square. */
  public static final float XGRAM_CHAT_FAB_CORNER = 14f;

  /**
   * The resting corner of the chat FABs in dp ({@link XgramExpressive#CORNER_FULL} for a circle): the M3E rounded
   * square, or with "Chat buttons match avatar shape" on (XgramSettings.chatFabsAvatarShape) the avatars' shape, as
   * AvatarReceiver.getDisplayRadius rounds an avatar: half the side times the avatar shape setting's factor, or the
   * theme's avatar radius while that setting follows the theme. Cached values and a theme property only: read while
   * the buttons draw (CircleButton and the bar that collapses into one, ChatBottomBarView), nothing allocated.
   */
  public static float xgramChatFabCornerDp () {
    final com.fahadali.xgram.XgramSettings settings = com.fahadali.xgram.XgramSettings.instance();
    if (!settings.chatFabsAvatarShape()) {
      return XGRAM_CHAT_FAB_CORNER;
    }
    float factor = settings.avatarRadiusFactor();
    if (factor < 0f) {
      factor = Theme.getProperty(PropertyId.AVATAR_RADIUS);
    }
    factor = MathUtils.clamp(factor);
    return factor >= 1f ? XgramExpressive.CORNER_FULL : XGRAM_CHAT_FAB_DP / 2f * factor;
  }

  private boolean xgramChatFab;
  private float xgramChatFabCorner = Float.NaN;

  /** True for a chat floating button drawn as an M3 FAB (Expressive design). */
  public boolean xgramIsChatFab () {
    return xgramChatFab;
  }

  /**
   * A chat FAB takes the chat FAB shape of now, when the option, the avatar shape or the theme's avatar radius changed
   * since it last drew: the container, the ripple's mask and the outline (its shadow) together. Called before the
   * background draws; nothing when the shape is the same (the usual frame).
   */
  private void xgramSyncChatFabShape () {
    final float corner = xgramChatFabCornerDp();
    if (corner == xgramChatFabCorner || xgramMorph == null || xgramMask == null) {
      return;
    }
    xgramChatFabCorner = corner;
    xgramMorph.setCorners(corner, corner, corner, corner);
    xgramMask.setCorners(corner, corner, corner, corner);
    invalidateOutline();
  }

  @Override
  public void draw (@NonNull Canvas c) {
    if (xgramChatFab) {
      xgramSyncChatFabShape(); // XGram: Settings → XGram → Appearance changes open chats' buttons on their next frame
    }
    super.draw(c);
  }

  private boolean xgramInitChatFab (boolean needShadow) {
    this.xgramChatFab = true;
    this.xgramFab = false; // not the main FAB: no toggle, extended or size morphs
    this.xgramSizeDp = XGRAM_CHAT_FAB_DP;
    final float corner = xgramChatFabCornerDp();
    this.xgramChatFabCorner = corner;
    xgramMorph = new XgramExpressive.MorphDrawable(0, corner, Float.NaN);
    xgramMask = new XgramExpressive.MorphDrawable(0xff000000, corner, Float.NaN);
    xgramMorph.setSize(XGRAM_CHAT_FAB_DP, XGRAM_CHAT_FAB_DP, Gravity.CENTER);
    xgramMask.setSize(XGRAM_CHAT_FAB_DP, XGRAM_CHAT_FAB_DP, Gravity.CENTER);
    xgramContainerRole = XgramExpressive.SECONDARY_CONTAINER;
    xgramIconRole = XgramExpressive.ON_SECONDARY_CONTAINER;
    xgramMorph.setColorRole(xgramContainerRole);
    xgramMorph.setHost(this);
    xgramMask.setHost(this);
    xgramRippleColor = XgramExpressive.withAlpha(xgramIconColor(), XgramExpressive.STATE_PRESSED);
    xgramRipple = new RippleDrawable(ColorStateList.valueOf(xgramRippleColor), xgramMorph, new InsetDrawable(xgramMask, 0));
    setBackground(xgramRipple);
    setStateListAnimator(null);
    setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    xgramZ = needShadow ? Screen.dp(XgramExpressive.ELEVATION_3) : 0;
    setElevation(xgramZ);
    xgramSyncTagZ();
    return true;
  }

  // M3 large badge on a chat FAB (Compose BadgeTokens), a size up so the count reads at a glance on the 48dp FAB: 20dp
  // high, at least 20dp wide (one digit is a circle), full corners, 6dp horizontal padding, labelMedium emphasized
  // (12sp, 700, .5sp tracking), up to 4 characters ("999+"). error / onError; a muted count (a muted chat's unread
  // messages, the reactions) primary / onPrimary, which stands off the secondaryContainer FAB and the chat under it in
  // light, dark and Pure black (surfaceContainerHighest was a dark pill on the dark FAB). It sits on the container's
  // top end corner, 4dp outside it: the room the 56dp view leaves around the 48dp container, so the view never clips
  // it. It scales in from the view's top end corner on the spatial fast spring, so the overshoot grows inward. Drawn
  // over the button, no allocation per frame.

  private static final float XGRAM_BADGE_HEIGHT = 20f, XGRAM_BADGE_PADDING = 6f, XGRAM_BADGE_OUTSET = 4f;
  private static final int XGRAM_BADGE_STYLE = XgramExpressive.TS_LABEL_M;
  private static final Paint XGRAM_BADGE_FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static @Nullable android.text.TextPaint xgramBadgePaint;

  private int xgramBadgeCount;
  private boolean xgramBadgeMuted;
  private @Nullable String xgramBadgeText;
  private float xgramBadgeTextWidth;
  private @Nullable XgramExpressive.Spring xgramBadgeShow;
  private final android.graphics.RectF xgramBadgeRect = new android.graphics.RectF();

  private static android.text.TextPaint xgramBadgePaint () {
    if (xgramBadgePaint == null) {
      xgramBadgePaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      xgramBadgePaint.setTypeface(XgramExpressive.typeface(XGRAM_BADGE_STYLE, true));
      xgramBadgePaint.setLetterSpacing(XgramExpressive.trackingSp(XGRAM_BADGE_STYLE) / XgramExpressive.textSizeSp(XGRAM_BADGE_STYLE));
    }
    xgramBadgePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XGRAM_BADGE_STYLE)));
    return xgramBadgePaint;
  }

  /** The count on a chat FAB (0 hides it). Nothing unless this is a chat FAB. */
  public void xgramSetBadge (int count, boolean muted, boolean animated) {
    if (!xgramChatFab) {
      return;
    }
    boolean changed = false;
    if (this.xgramBadgeMuted != muted) {
      this.xgramBadgeMuted = muted;
      changed = true;
    }
    if (this.xgramBadgeCount != count) {
      this.xgramBadgeCount = count;
      if (count > 0) {
        // While it leaves, the old count stays on it
        this.xgramBadgeText = count > 999 ? "999+" : String.valueOf(count);
        this.xgramBadgeTextWidth = xgramBadgePaint().measureText(xgramBadgeText);
      }
      changed = true;
    }
    final float target = count > 0 ? 1f : 0f;
    if (xgramBadgeShow == null) {
      xgramBadgeShow = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, target, (s, value) -> invalidate());
      xgramBadgeShow.setThreshold(.005f);
    } else if (animated && isAttachedToWindow()) {
      xgramBadgeShow.animateTo(target);
    } else if (xgramBadgeShow.getValue() != target || xgramBadgeShow.isRunning()) {
      xgramBadgeShow.setValue(target);
      changed = true;
    }
    if (changed) {
      invalidate();
    }
  }

  private void xgramDrawBadge (Canvas c) {
    final float f = xgramBadgeShow != null ? xgramBadgeShow.getValue() : 0f;
    if (f <= 0f || xgramBadgeText == null) {
      return;
    }
    final float width = getWidth(), height = getHeight();
    final float box = Math.min(Screen.dpf(XGRAM_CHAT_FAB_DP), Math.min(width, height));
    final float boxLeft = (width - box) / 2f, boxTop = (height - box) / 2f;
    final float badgeHeight = Screen.dpf(XGRAM_BADGE_HEIGHT);
    final float badgeWidth = Math.max(badgeHeight, xgramBadgeTextWidth + Screen.dpf(XGRAM_BADGE_PADDING) * 2f);
    final float outset = Screen.dpf(XGRAM_BADGE_OUTSET);
    final boolean rtl = Lang.rtl();
    final float top = Math.max(0f, boxTop - outset);
    final float left = rtl ? Math.max(0f, boxLeft - outset) : Math.min(width, boxLeft + box + outset) - badgeWidth;
    xgramBadgeRect.set(left, top, left + badgeWidth, top + badgeHeight);
    final float scale = Math.max(0f, f), alpha = MathUtils.clamp(f);
    final boolean scaled = scale != 1f;
    final int saveCount = scaled ? Views.save(c) : -1;
    if (scaled) {
      // The outer top end corner: the spring's overshoot grows down and inward, never past the view's top or end edge
      c.scale(scale, scale, rtl ? xgramBadgeRect.left : xgramBadgeRect.right, xgramBadgeRect.top);
    }
    final int fill = XgramExpressive.color(xgramBadgeMuted ? XgramExpressive.PRIMARY : XgramExpressive.ERROR);
    XGRAM_BADGE_FILL.setColor(XgramExpressive.withAlpha(fill, alpha));
    c.drawRoundRect(xgramBadgeRect, badgeHeight / 2f, badgeHeight / 2f, XGRAM_BADGE_FILL);
    final android.text.TextPaint paint = xgramBadgePaint();
    final int text = XgramExpressive.color(xgramBadgeMuted ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_ERROR);
    paint.setColor(XgramExpressive.withAlpha(text, alpha));
    c.drawText(xgramBadgeText, xgramBadgeRect.centerX() - xgramBadgeTextWidth / 2f, xgramBadgeRect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
    if (scaled) {
      Views.restore(c, saveCount);
    }
  }

  /** Room past a chat FAB's container for its level 3 shadow: at 6dp the spot shadow's soft edge ends about 16dp below it. */
  private static final float XGRAM_SHADOW_ROOM = 16f;

  /**
   * XGram M3E: room for this chat FAB's elevation shadow inside its own wrap (MessagesController's scroll, mention and
   * reaction wraps, the search arrows' CircleCounterBadgeView, the forum topics' mention wrap).
   * <p>
   * A view's shadow is drawn in its parent's canvas, under the parent's clip, and each wrap is clipped to its own
   * bounds by the layout holding it (that layout clips its children). The FAB sat in its wrap's right bottom corner
   * with 4dp past the container (the search arrows: none, with the 56dp container of before), so its shadow ended in
   * hard edges there: a translucent rectangle around the button, cut off on its right and bottom. A wrap that stops
   * clipping its own children does not help (that only stops clipping the button's own drawing, not the shadow drawn
   * around it).
   * <p>
   * So the wrap's right and bottom margins move inside it: the wrap grows by the same amount and keeps its left and
   * top, the button and its right or bottom anchored siblings take the amount as extra margins, and nothing moves on
   * screen, until the container has 16dp of room inside the wrap. Call it once the button sits in its wrap and both
   * have their final layout params; calling it again changes nothing. Nothing unless this is a chat FAB with a
   * shadow (Expressive design on).
   */
  public void xgramMakeShadowRoom () {
    if (!xgramChatFab || xgramZ <= 0 || !(getParent() instanceof android.view.ViewGroup)) {
      return;
    }
    final android.view.ViewGroup wrap = (android.view.ViewGroup) getParent();
    if (!(wrap.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) || !(getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams)) {
      return;
    }
    final android.view.ViewGroup.MarginLayoutParams wrapParams = (android.view.ViewGroup.MarginLayoutParams) wrap.getLayoutParams();
    final android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) getLayoutParams();
    if (!xgramAnchoredRight(params) || !xgramAnchoredBottom(params)) {
      return;
    }
    // The container is centred in the view: a 56dp view already leaves 4dp around the 48dp container
    final int inset = Math.max(0, (Math.min(params.width, params.height) - Screen.dp(XGRAM_CHAT_FAB_DP)) / 2);
    final int room = Screen.dp(XGRAM_SHADOW_ROOM);
    final int dx = Math.max(0, Math.min(room - inset - params.rightMargin, wrapParams.rightMargin));
    final int dy = Math.max(0, Math.min(room - inset - params.bottomMargin, wrapParams.bottomMargin));
    if (dx == 0 && dy == 0) {
      return;
    }
    for (int i = 0; i < wrap.getChildCount(); i++) {
      final View child = wrap.getChildAt(i);
      if (child.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
        final android.widget.FrameLayout.LayoutParams childParams = (android.widget.FrameLayout.LayoutParams) child.getLayoutParams();
        final boolean right = xgramAnchoredRight(childParams), bottom = xgramAnchoredBottom(childParams);
        if (right) {
          childParams.rightMargin += dx;
        }
        if (bottom) {
          childParams.bottomMargin += dy;
        }
        if (right || bottom) {
          child.setLayoutParams(childParams);
        }
      }
    }
    wrapParams.rightMargin -= dx;
    wrapParams.bottomMargin -= dy;
    if (wrapParams.width > 0) {
      wrapParams.width += dx;
    }
    if (wrapParams.height > 0) {
      wrapParams.height += dy;
    }
    wrap.setLayoutParams(wrapParams);
  }

  /** Laid out from its parent's right edge (an unspecified gravity, -1, is top left: its masks give neither). */
  private static boolean xgramAnchoredRight (android.widget.FrameLayout.LayoutParams params) {
    return (params.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
  }

  private static boolean xgramAnchoredBottom (android.widget.FrameLayout.LayoutParams params) {
    return (params.gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM;
  }

  /** A counter badge kept in the tag must stay above the button (it used to follow the press animator's Z). */
  private void xgramSyncTagZ () {
    if (xgramMorph != null && getTag() instanceof View) {
      ((View) getTag()).setTranslationZ(xgramZ);
    }
  }

  private int xgramIconColor () {
    if (xgramIconRole >= 0) {
      if (xgramToggle && xgramIconRole == XgramExpressive.ON_PRIMARY_CONTAINER) {
        final float f = MathUtils.clamp(xgramToggleFactor);
        return f == 0f ? XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER) : ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), XgramExpressive.color(XgramExpressive.ON_PRIMARY), f);
      }
      return XgramExpressive.color(xgramIconRole);
    }
    return iconColorIsId ? Theme.getColor(iconColor) : iconColor;
  }

  /** The main FAB of a FAB menu: it morphs into the 56dp round close button (ToggleFloatingActionButton). */
  public void xgramSetToggleFab (boolean isToggle) {
    this.xgramToggle = isToggle && xgramFab;
    if (xgramToggle && xgramCloseIcon == null) {
      xgramCloseIcon = Drawables.get(getResources(), R.drawable.baseline_close_24);
    }
    xgramApplyShape();
  }

  public void xgramSetFabSize (float sizeDp) {
    if (xgramMorph == null || !xgramFab) {
      return;
    }
    this.xgramSizeDp = sizeDp;
    xgramMeasureLabel();
    xgramApplyShape();
  }

  public void xgramSetToggleFactor (float factor) {
    if (this.xgramToggleFactor != factor) {
      this.xgramToggleFactor = factor;
      xgramApplyShape();
    }
  }

  // Extended FAB (icon and label): the view is laid out at the extended width once; the container's width springs
  // inside it, anchored to the end edge, so nothing is laid out per frame.

  private @Nullable String xgramLabel;
  private @Nullable android.text.TextPaint xgramLabelPaint;
  private float xgramLabelWidth, xgramExtend;

  public boolean xgramHasLabel () {
    return xgramLabel != null && xgramFab && (xgramToggle || xgramPlainExtended);
  }

  // XGram M3E round 6: an extended FAB that is not a FAB menu's toggle (the map's "Directions"): icon and label in
  // the FAB's container, anchored to the view's end, laid out at xgramLayoutWidth().

  private boolean xgramPlainExtended;

  /** Makes this FAB a plain extended FAB with {@code label} (null: a plain FAB again). Expressive FAB sizes only. */
  public void xgramSetPlainExtendedLabel (@Nullable CharSequence label) {
    if (xgramMorph == null || !xgramFab || xgramToggle) {
      return;
    }
    this.xgramPlainExtended = !StringUtils.isEmpty(label);
    this.xgramLabel = xgramPlainExtended ? label.toString() : null;
    this.xgramExtend = xgramPlainExtended ? 1f : 0f;
    xgramMeasureLabel();
    xgramApplyShape();
  }

  /**
   * XGram M3E round 6: the container and content roles and the corner of an Expressive button (a 40dp small FAB on
   * surfaceContainerHigh with 12dp corners, a filled tonal icon button on secondaryContainer...). {@code floating}
   * draws the container as a floating surface (the chip step with Pure black). A FAB keeps its corner when pressed;
   * an icon button still morphs to 8dp.
   */
  public void xgramSetContainer (int containerRole, int contentRole, float cornerDp, boolean floating) {
    if (xgramMorph == null || xgramChatFab) {
      return;
    }
    this.xgramContainerRole = containerRole;
    this.xgramIconRole = contentRole;
    if (floating) {
      xgramMorph.setFloatingColorRole(containerRole);
    } else {
      xgramMorph.setColorRole(containerRole);
    }
    xgramMorph.setCorners(cornerDp, cornerDp, cornerDp, cornerDp);
    xgramMask.setCorners(cornerDp, cornerDp, cornerDp, cornerDp);
    if (cornerDp != XgramExpressive.CORNER_FULL) {
      xgramMorph.setPressedCorner(cornerDp);
      xgramMask.setPressedCorner(cornerDp);
    }
    xgramUpdateRippleColor();
    invalidate();
    invalidateOutline();
  }

  /** Makes the toggle FAB an extended FAB with {@code label} (null: a plain FAB again). */
  public void xgramSetExtendedLabel (@Nullable CharSequence label) {
    if (xgramMorph == null || !xgramFab || !xgramToggle) {
      return;
    }
    this.xgramLabel = StringUtils.isEmpty(label) ? null : label.toString();
    xgramMeasureLabel();
    xgramApplyShape();
  }

  private void xgramMeasureLabel () {
    if (xgramLabel == null) {
      xgramLabelWidth = 0f;
      return;
    }
    final int ts = xgramSizeDp >= 80f ? XgramExpressive.TS_TITLE_M : XgramExpressive.TS_LABEL_L;
    if (xgramLabelPaint == null) {
      xgramLabelPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    }
    xgramLabelPaint.setTypeface(XgramExpressive.typeface(ts, false));
    xgramLabelPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(ts)));
    xgramLabelWidth = xgramLabelPaint.measureText(xgramLabel);
    final Paint.FontMetrics fm = xgramLabelPaint.getFontMetrics();
    xgramLabelBaseline = -(fm.ascent + fm.descent) / 2f;
  }

  private float xgramLabelBaseline;

  /** Width in dp of the extended container: 16dp, the icon, 12dp, the label, 20dp. */
  private float xgramExtendedDp () {
    return 16f + xgramFabIcon(xgramSizeDp) + 12f + xgramLabelWidth / Screen.density() + 20f;
  }

  /** The width the view should be laid out at. */
  public int xgramLayoutWidth () {
    return xgramHasLabel() ? (int) Math.ceil(Screen.dpf(Math.max(xgramSizeDp, xgramExtendedDp()))) : Screen.dp(xgramSizeDp);
  }

  /** 0: the plain FAB, 1: extended. Opening the menu collapses it with the toggle morph. */
  public void xgramSetExtendFactor (float factor) {
    if (this.xgramExtend != factor) {
      this.xgramExtend = factor;
      xgramApplyShape();
    }
  }

  private float xgramExtendNow () {
    if (!xgramHasLabel()) {
      return 0f;
    }
    return MathUtils.clamp(xgramExtend) * (1f - MathUtils.clamp(xgramToggleFactor));
  }

  /** The container's width in dp right now. */
  private float xgramContainerDp (float size) {
    final float e = xgramExtendNow();
    return e > 0f ? size + (Math.max(size, xgramExtendedDp()) - size) * e : size;
  }

  private void xgramApplyShape () {
    if (xgramMorph == null || !xgramFab) {
      return;
    }
    final float p = xgramToggle ? xgramToggleFactor : 0f;
    final float fabCorner = xgramFabCorner(xgramSizeDp);
    final float size = Math.min(xgramSizeDp, xgramSizeDp + (56f - xgramSizeDp) * p);
    final float corner = Math.max(0f, Math.min(size / 2f, fabCorner + (28f - fabCorner) * p));
    // The shrinking container stays in the top end corner of the FAB's box
    final int gravity = xgramToggle || xgramPlainExtended ? Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) : Gravity.CENTER;
    final float containerW = xgramContainerDp(size); // XGram: an extended FAB is wider, anchored to the end
    xgramMorph.setSize(containerW, size, gravity);
    xgramMask.setSize(containerW, size, gravity);
    xgramMorph.setCornerTarget(corner, false);
    xgramMask.setCornerTarget(corner, false);
    xgramMorph.setPressedCorner(corner);
    xgramMask.setPressedCorner(corner);
    if (xgramToggle && xgramContainerRole == XgramExpressive.PRIMARY_CONTAINER) {
      final float f = MathUtils.clamp(p);
      if (f == 0f) {
        xgramMorph.setColorRole(XgramExpressive.PRIMARY_CONTAINER);
      } else if (f == 1f) {
        xgramMorph.setColorRole(XgramExpressive.PRIMARY);
      } else {
        xgramMorph.setColor(ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER), XgramExpressive.color(XgramExpressive.PRIMARY), f));
      }
    }
    invalidate();
    invalidateOutline();
  }

  private void xgramUpdateRippleColor () {
    if (xgramRipple != null) {
      final int color = XgramExpressive.withAlpha(xgramIconColor(), XgramExpressive.STATE_PRESSED);
      if (color != xgramRippleColor) {
        xgramRippleColor = color;
        xgramRipple.setColor(ColorStateList.valueOf(color));
      }
    }
  }

  private void xgramDrawToggleIcon (Canvas c, int width) {
    final float p = xgramToggleFactor;
    final float f = MathUtils.clamp(p);
    final Drawable drawable = f >= .5f && xgramCloseIcon != null ? xgramCloseIcon : icon;
    if (drawable == null) {
      return;
    }
    final float boxDp = Math.min(xgramSizeDp, xgramSizeDp + (56f - xgramSizeDp) * p);
    final float box = Screen.dpf(boxDp);
    final float cy = box / 2f;
    final float fabIcon = xgramFabIcon(xgramSizeDp);
    final float cx;
    final float extend = xgramExtendNow();
    if (extend > 0f && xgramLabel != null && xgramLabelPaint != null) {
      // Extended: the icon sits 16dp in from the container's start and the label follows 12dp after it
      final float containerW = Screen.dpf(xgramContainerDp(boxDp));
      final float iconInset = box / 2f + (Screen.dpf(16f + fabIcon / 2f) - box / 2f) * extend;
      final boolean rtl = Lang.rtl();
      final float start = rtl ? containerW : width - containerW;
      cx = rtl ? start - iconInset : start + iconInset;
      final float labelAlpha = MathUtils.clamp((extend - .4f) / .6f);
      if (labelAlpha > 0f) {
        final float gap = Screen.dpf(fabIcon / 2f + 12f);
        final float x = rtl ? cx - gap - xgramLabelWidth : cx + gap;
        xgramLabelPaint.setColor(XgramExpressive.withAlpha(xgramIconColor(), labelAlpha));
        c.save();
        c.clipRect(rtl ? 0f : width - containerW, 0f, rtl ? containerW : width, box);
        c.drawText(xgramLabel, x, cy + xgramLabelBaseline, xgramLabelPaint);
        c.restore();
      }
    } else {
      cx = Lang.rtl() ? box / 2f : width - box / 2f;
    }
    final float iconDp = fabIcon + (20f - fabIcon) * f;
    final float replaceScale = (1f - (replaceFactor <= .5f ? replaceFactor / .5f : 1f - ((replaceFactor - .5f) / .5f))) * (1f - progressFactor);
    final int iconWidth = Math.max(1, drawable.getMinimumWidth());
    final float scale = Screen.dpf(iconDp) / iconWidth * (.6f + .4f * replaceScale);
    final int color = xgramIconColor();
    final Paint paint = getIconPaint(color);
    final int sourceAlpha = paint.getAlpha();
    paint.setAlpha((int) ((float) sourceAlpha * MathUtils.clamp(replaceScale * iconAlpha)));
    c.save();
    c.scale(scale, scale, cx, cy);
    Drawables.draw(c, drawable, cx - drawable.getMinimumWidth() / 2f, cy - drawable.getMinimumHeight() / 2f, paint);
    c.restore();
    paint.setAlpha(sourceAlpha);
    if (progress != null && progressFactor >= .5f) {
      progress.forceColor(color);
      progress.draw(c);
    }
  }

  public void setCrossColorId (@ColorId int backgroundColorId, @ColorId int iconColorId) {
    this.crossBackgroundColorId = backgroundColorId;
    this.crossIconColorId = iconColorId;
  }

  private boolean isCounterClockwise;
  private float factor;

  public void setRotationFactor (boolean isCounterClockwise, float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      this.isCounterClockwise = isCounterClockwise;
      RippleSupport.changeViewColor(this, backgroundColorId, crossBackgroundColorId, factor);
      invalidate();
    }
  }

  private float crossFactor;

  public void setCrossFactor (float factor) {
    if (this.crossFactor != factor) {
      this.crossFactor = factor;
      invalidate();
    }
  }

  private Drawable replaceIcon;
  private float replaceFactor;
  private static final int REPLACE_ANIMATOR = 0;
  private int replaceOffsetLeft;
  private FactorAnimator replaceAnimator;

  public void replaceIcon (@DrawableRes int iconRes) {
    replaceIcon(iconRes, 0);
  }
  public void replaceIcon (@DrawableRes int iconRes, int replaceOffset) {
    this.replaceIcon = Drawables.get(iconRes);
    this.replaceOffsetLeft = replaceOffset;
    if (replaceAnimator == null) {
      replaceAnimator = new FactorAnimator(REPLACE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l);
    }
    if (!replaceAnimator.isAnimating()) {
      replaceAnimator.forceFactor(replaceFactor = 0f);
    } else if (replaceAnimator.getFactor() >= .5f) {
      replaceAnimator.forceFactor(replaceFactor = replaceAnimator.getFactor() - .5f);
    }
    replaceAnimator.animateTo(1f);
  }

  public void setIcon (@DrawableRes int iconRes) {
    setIcon(iconRes, 0);
  }

  public void setIcon (@DrawableRes int iconRes, int offsetLeft) {
    this.icon = Drawables.get(iconRes);
    this.offsetLeft = offsetLeft;
    invalidate();
  }

  private float hideFactor;
  private boolean isHidden;
  private FactorAnimator hideAnimator;
  private static final int HIDE_ANIMATOR_ID = 2;

  public void setIsHidden (boolean isHidden, boolean animated) {
    if (this.isHidden != isHidden) {
      this.isHidden = isHidden;
      final float toFactor = isHidden ? 1f : 0f;
      if (animated) {
        if (hideAnimator == null) {
          hideAnimator = new FactorAnimator(HIDE_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 100l, this.hideFactor);
        }
        if (toFactor == 0f && hideFactor == 1f) {
          hideAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          hideAnimator.setDuration(290l);
        } else {
          hideAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          hideAnimator.setDuration(140l);
        }
        hideAnimator.animateTo(toFactor);
      } else {
        if (hideAnimator != null) {
          hideAnimator.forceFactor(toFactor);
        }
        setHideFactor(toFactor);
      }
    }
  }

  public void setHideFactor (float factor) {
    if (this.hideFactor != factor) {
      this.hideFactor = factor;
      updateButtonScale();
      setAlpha(MathUtils.clamp(1f - factor));
    }
  }

  private float baseScale = 1f;

  public void setBaseScale (float baseScale) {
    if (this.baseScale != baseScale) {
      this.baseScale = baseScale;
      updateButtonScale();
    }
  }

  private void updateButtonScale () {
    float scale = (.6f + .4f * (1f - hideFactor) + .2f * dragFactor * (1f - hideFactor)) * baseScale;
    setScaleX(scale);
    setScaleY(scale);
  }

  private float dragFactor;
  private boolean isDragging;
  private FactorAnimator dragAnimator;
  private static final int DRAG_ANIMATOR_ID = 3;

  public void setIsDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      final float toFactor = isDragging ? 1f : 0f;
      if (dragAnimator == null) {
        dragAnimator = new FactorAnimator(DRAG_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.dragFactor);
      }
      dragAnimator.animateTo(toFactor);
    }
  }

  private void setDragFactor (float factor) {
    if (this.dragFactor != factor) {
      this.dragFactor = factor;
      updateButtonScale();
    }
  }

  private void applyIcon () {
    if (replaceIcon != null) {
      icon = replaceIcon;
      offsetLeft = replaceOffsetLeft;
      replaceIcon = null;
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REPLACE_ANIMATOR: {
        if (this.replaceFactor != factor) {
          this.replaceFactor = factor;
          if (factor > .5f) {
            applyIcon();
          }
          invalidate();
        }
        break;
      }
      case PROGRESS_ANIMATOR: {
        if (this.progressFactor != factor) {
          this.progressFactor = factor;
          progress.setAlpha(MathUtils.clamp(factor < .5f ? 0f : (factor - .5f) / .5f));
          invalidate();
        }
        break;
      }
      case HIDE_ANIMATOR_ID: {
        setHideFactor(factor);
        break;
      }
      case DRAG_ANIMATOR_ID: {
        setDragFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REPLACE_ANIMATOR: {
        applyIcon();
        break;
      }
    }
  }

  private ProgressComponent progress;
  private FactorAnimator progressAnimator;
  private float progressFactor;
  private boolean inProgress;

  private static final int PROGRESS_ANIMATOR = 1;

  public boolean isInProgress () {
    return inProgress;
  }

  public void setInProgress (boolean inProgress) {
    if (this.inProgress == inProgress) {
      return;
    }
    this.inProgress = inProgress;
    if (progress == null) {
      final boolean xgram = XgramExpressive.isEnabled();
      // XGram M3E: a 24dp loading indicator (ProgressComponent draws it for a 12dp radius) in the button's content
      // colour (onPrimaryContainer on the FAB; onDraw keeps it in sync), not Telegram X's white 8dp arc
      progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(xgram ? 12f : 8f));
      progress.setUseLargerPaint();
      progress.setSlowerDurations();
      progress.forceColor(xgram ? xgramIconColor() : 0xffffffff);
      progress.setAlpha(0f);
      if (xgram && getWidth() > 0 && getHeight() > 0) {
        progress.setBounds(0, 0, getWidth(), getHeight());
      } else {
        progress.setBounds(0, 0, getLayoutParams().width, getLayoutParams().height);
      }
      progress.attachToView(this);
    }
    if (progressAnimator == null) {
      progressAnimator = new FactorAnimator(PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l);
    }
    XgramAppMotion.use(progressAnimator, XgramExpressive.EFFECTS_DEFAULT, true); // XGram M3E: the icon fades through on a spring
    progressAnimator.animateTo(inProgress ? 1f : 0f);
  }

  public void destroy () {
    if (progress != null) {
      progress.detachFromView(this);
    }
  }

  private float iconRotation;
  private FactorAnimator iconRotationAnimator;
  private static final int ROTATION_ANIMATOR = 3;

  public void setIconRotation (float rotation, boolean animated) {
    if (animated) {
      if (iconRotationAnimator == null) {
        if (this.iconRotation == rotation) {
          return;
        }
        iconRotationAnimator = new FactorAnimator(ROTATION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.iconRotation);
      }
      iconRotationAnimator.animateTo(rotation);
    } else {
      if (iconRotationAnimator != null) {
        iconRotationAnimator.forceFactor(rotation);
      }
      setIconRotationInternal(rotation);
    }
  }

  public void setFromToColor (@ColorId int fromColorId, @ColorId int toColorId, float factor) {
    RippleSupport.changeViewColor(this, fromColorId, toColorId, factor);
  }

  private void setIconRotationInternal (float rotation) {
    rotation = rotation % 360f;
    if (this.iconRotation != rotation) {
      this.iconRotation = rotation;
      invalidate();
    }
  }

  private static final float STEP_FACTOR = .85f;
  private static final float STEP_FACTOR_2 = .15f;

  private Paint iconPaint;
  private Paint getIconPaint (int color) {
    if (iconPaint == null || iconPaint.getColor() != color)
      iconPaint = Paints.createPorterDuffPaint(iconPaint, color);
    return iconPaint;
  }

  private float iconAlpha = 1f;

  public void setIconAlpha (float iconAlpha) {
    this.iconAlpha = iconAlpha;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    int cx = (int) (width * .5f) + offsetLeft;
    int cy = (int) (height * .5f);

    boolean hasText = !StringUtils.isEmpty(bottomText);

    if (xgramRipple != null) {
      xgramUpdateRippleColor();
      if ((xgramToggle || xgramPlainExtended) && sticker == null && icon != null) {
        xgramDrawToggleIcon(c, width);
        return;
      }
    }

    if (sticker != null && imageReceiver != null && gifReceiver != null) {
      // XGram: on a chat FAB the reaction takes a 28dp box in the 48dp container, inside a circle too (Telegram X: two
      // thirds of the view)
      final int halfW = xgramChatFab ? Screen.dp(14f) : width / 3, halfH = xgramChatFab ? Screen.dp(14f) : height / 3;
      imageReceiver.setBounds(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
      gifReceiver.setBounds(cx - halfW, cy - halfH, cx + halfW, cy + halfH);

      final float MIN_SCALE = .82f;
      final boolean isAnimation = sticker.isAnimated();
      float originalScale = sticker.getDisplayScale();
      boolean saved = originalScale != 1f || factor != 0f;
      int restoreToCount = -1;
      if (saved) {
        restoreToCount = Views.save(c);
        float scale = originalScale * (MIN_SCALE + (1f - MIN_SCALE) * (1f - factor));
        c.scale(scale, scale, cx, cy);
      }
      if (isAnimation) {
        if (gifReceiver.needPlaceholder()) {
          //if (imageReceiver.needPlaceholder()) {
          //  imageReceiver.drawPlaceholderContour(c, contour);
          //}
          imageReceiver.draw(c);
        }
        gifReceiver.draw(c);
      } else {
        //if (imageReceiver.needPlaceholder()) {
        //  imageReceiver.drawPlaceholderContour(c, contour);
        //}
        imageReceiver.draw(c);
      }
      if (saved) {
        Views.restore(c, restoreToCount);
      }
    } else if (icon != null) {
      final int iconColor = xgramIconRole >= 0 ? xgramIconColor() : iconColorIsId ? Theme.getColor(this.iconColor) : this.iconColor;
      final int crossIconColor = ColorUtils.fromToArgb(iconColor, crossIconColorId != 0 ? Theme.getColor(crossIconColorId) : iconColor, factor);
      final Paint bitmapPaint = getIconPaint(iconColor);
      final int sourceAlpha = bitmapPaint.getAlpha();

      if (factor == 0f) {
        final float scaleFactor = (1f - (replaceFactor <= .5f ? replaceFactor / .5f : 1f - ((replaceFactor - .5f) / .5f))) * (1f - progressFactor);
        final boolean savedScale = scaleFactor != 1f;
        if (savedScale) {
          c.save();
          final float scale = .6f + .4f * scaleFactor;
          c.scale(scale, scale, cx, cy);
          bitmapPaint.setAlpha((int) ((float) sourceAlpha * scaleFactor));
        }

        if (iconAlpha != 1f) {
          bitmapPaint.setAlpha((int) ((float) sourceAlpha * scaleFactor * iconAlpha));
        }

        final boolean savedRotation = iconRotation != 0;
        if (savedRotation) {
          c.save();
          c.rotate(iconRotation, cx, cy);
        }
        if (hasText) {
          // TODO scale text down if it does not fit
          c.drawText(bottomText, cx - bottomTextWidth / 2, cy + Screen.dp(17f), Paints.getRegularTextPaint(14f, ColorUtils.alphaColor(scaleFactor, iconColor)));
        }
        Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f - (hasText ? Screen.dp(8f) : 0), bitmapPaint);
        if (savedRotation) {
          c.restore();
        }

        if (iconAlpha != 1f) {
          bitmapPaint.setAlpha(sourceAlpha);
        }

        if (savedScale) {
          bitmapPaint.setAlpha(sourceAlpha);
          c.restore();
        }

        if (progress != null && progressFactor >= .5f) {
          progress.forceColor(iconColor);
          progress.draw(c);
        }
      } else {
        final float rotation = isCounterClockwise ? (-90 - 45f) * factor : (90f + 45f) * factor;
        final boolean saved = rotation != 0f;
        if (saved) {
          c.save();
          c.rotate(rotation, cx, cy);
        }

        if (factor <= STEP_FACTOR) {
          final float factor = this.factor / STEP_FACTOR;

          bitmapPaint.setAlpha((int) ((float) sourceAlpha * (1f - factor)));
          Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, bitmapPaint);
          bitmapPaint.setAlpha(sourceAlpha);
        }

        if (factor >= STEP_FACTOR_2) {
          final float factor = (this.factor - STEP_FACTOR_2) / (1f - STEP_FACTOR_2);
          final int lineWidth = Screen.dp(1f);
          final int lineHeight = (int) ((float) Screen.dp(7f) * factor);

          final Paint paint = Paints.fillingPaint(ColorUtils.alphaColor(factor, crossIconColor));

          c.drawRect(cx - lineWidth, cy - lineHeight, cx + lineWidth, cy + lineHeight, paint);
          c.drawRect(cx - lineHeight, cy - lineWidth, cx + lineHeight, cy + lineWidth, paint);
        }

        if (saved) {
          c.restore();
        }
      }

      if (crossFactor > 0f) {
        DrawAlgorithms.drawCross(c, cx, cy - Screen.dp(6f), crossFactor, iconColor, RippleSupport.getCurrentViewColor(this), Screen.dp(27f));
      }
    }

    if (xgramChatFab) {
      xgramDrawBadge(c); // XGram: over the icon (or the reaction) and the state layer
    }
  }

  @Nullable
  private TdApi.UnreadReaction unreadReactionRaw;
  private TGReaction reaction;
  private TGStickerObj sticker;

  public void setUnreadReaction (TdApi.UnreadReaction unreadReaction) {
    if (tdlib == null || imageReceiver == null || gifReceiver == null || reaction != null && unreadReaction != null && unreadReactionRaw != null && StringUtils.equalsOrBothEmpty(TD.makeReactionKey(unreadReaction.type), TD.makeReactionKey(unreadReactionRaw.type))) {
      return;
    }

    if (unreadReactionRaw != null) {
      tdlib.listeners().removeReactionLoadListener(TD.makeReactionKey(unreadReactionRaw.type), this);
    }

    unreadReactionRaw = unreadReaction;
    reaction = unreadReaction != null ? tdlib.getReaction(unreadReaction.type) : null;
    sticker = reaction != null ? reaction.newCenterAnimationSicker() : null;
    if (unreadReactionRaw != null && reaction == null) {
      tdlib.listeners().addReactionLoadListener(TD.makeReactionKey(unreadReactionRaw.type), this);
    }
    if (sticker != null) {
      if (sticker.getPreviewAnimation() != null && sticker.isEmojiReaction()) {
        sticker.getPreviewAnimation().setPlayOnce(true);
        sticker.getPreviewAnimation().setLooped(true);
      }
      gifReceiver.requestFile(sticker.getPreviewAnimation());
      imageReceiver.requestFile(sticker.getImage());
      if (sticker.needThemedColorFilter()) {
        this.gifReceiver.setThemedPorterDuffColorId(ColorId.iconActive);
        this.imageReceiver.setThemedPorterDuffColorId(ColorId.iconActive);
      } else {
        this.gifReceiver.disablePorterDuffColorFilter();
        this.imageReceiver.disablePorterDuffColorFilter();
      }
    } else {
      gifReceiver.clear();
      imageReceiver.clear();
    }
    invalidate();
  }

  @Override
  public void onReactionLoaded (String reactionKey) {
    UI.execute(() -> {
      if (unreadReactionRaw != null && StringUtils.equalsOrBothEmpty(reactionKey, TD.makeReactionKey(unreadReactionRaw.type))) {
        setUnreadReaction(unreadReactionRaw);
      }
    });
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    xgramSyncTagZ();
    if (gifReceiver != null) {
      gifReceiver.attach();
    }
    if (imageReceiver != null) {
      imageReceiver.attach();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (gifReceiver != null) {
      gifReceiver.detach();
    }
    if (imageReceiver != null) {
      imageReceiver.detach();
    }
  }

  @Override
  public void performDestroy () {
    if (imageReceiver != null) {
      imageReceiver.destroy();
    }
    if (gifReceiver != null) {
      gifReceiver.destroy();
    }
    if (unreadReactionRaw != null && tdlib != null) {
      tdlib.listeners().removeReactionLoadListener(TD.makeReactionKey(unreadReactionRaw.type), this);
    }
    unreadReactionRaw = null;
    reaction = null;
    sticker = null;
  }
}
