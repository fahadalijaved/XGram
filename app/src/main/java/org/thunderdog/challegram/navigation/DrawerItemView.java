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
 * File created on 09/05/2015 at 13:01
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class DrawerItemView extends BaseView implements FactorAnimator.Target, AttachDelegate, Destroyable, TGLegacyManager.EmojiLoadListener {
  private int iconLeft, iconTop;
  private @Nullable Drawable icon;

  private String text;
  private Text trimmedText;
  private int textLeft;

  private float checkFactor;
  private BoolAnimator checkAnimator;
  private AvatarReceiver receiver;
  private final EmojiStatusHelper emojiStatusHelper;
  private Counter counter;

  public DrawerItemView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    emojiStatusHelper = new EmojiStatusHelper(tdlib, this, null);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  private boolean isChecked () {
    return (checkAnimator != null && checkAnimator.getValue());
  }

  public boolean hasAvatar () {
    return receiver != null;
  }

  public void setChecked (boolean isChecked, boolean animated) {
    if (isChecked != isChecked()) {
      if (checkAnimator == null) {
        checkAnimator = new BoolAnimator(ANIMATOR_CHECK, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      checkAnimator.setValue(isChecked, animated);
    }
  }

  private static final int ANIMATOR_CHECK = 0;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CHECK: {
        if (checkFactor != factor) {
          this.checkFactor = factor;
          invalidate();
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setItemHeight (int height) {
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
  }

  public void setIcon (int left, int top, @DrawableRes int rawResource) {
    iconLeft = left;
    iconTop = top;
    icon = rawResource == 0 ? null : Drawables.get(getResources(), rawResource);
  }

  /*public void setIcon (int left, @DrawableRes int rawResource) {
    iconLeft = left;
    icon = rawResource == 0 ? null : BitmapFactory.decodeResource(getResources(), rawResource);
    iconTop = icon == null ? (int) ((float) getLayoutParams().height * .5f) : (int) ((float) getLayoutParams().height * .5f - (float) icon.getHeight() * .5f);
  }*/

  // XGram: Material 3 Expressive navigation drawer item: a pill inset 12dp, icon at 28dp, label at 64dp.

  private @Nullable Drawable xgramBackground;

  private void xgramCheckBackground () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && (xgramBackground == null || getBackground() != xgramBackground)) {
      xgramBackground = com.fahadali.xgram.appearance.XgramM3Profile.drawerItemBackground(0);
      setBackground(xgramBackground);
    }
  }

  private int xgramLabelColor () {
    return ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER), receiver != null ? checkFactor : 0f);
  }

  public void setText (String text) {
    xgramCheckBackground();
    if (this.text == null || !StringUtils.equalsOrBothEmpty(this.text, text)) {
      this.textLeft = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && receiver == null ? 64f : 72f);
      this.text = text;
      trimText(true);
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    trimText(false);
    layoutReceiver();
  }

  private float lastAvailTextWidth;

  private void trimText (boolean force) {
    if (text == null || StringUtils.isEmpty(text)) {
      trimmedText = null;
      return;
    }
    int availWidth = getMeasuredWidth() - textLeft;
    if (counter != null) {
      availWidth -= counter.getScaledWidth(Screen.dp(24f) + Screen.dp(8f));
    }
    if (emojiStatusHelper.needDrawEmojiStatus()) {
      availWidth -= emojiStatusHelper.getWidth() + Screen.dp(12);
    }
    if (availWidth <= 0) {
      trimmedText = null;
      return;
    }
    if (force || lastAvailTextWidth != availWidth) {
      lastAvailTextWidth = availWidth;
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        trimmedText = new Text.Builder(text, availWidth, com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L, false, 14f), this::xgramLabelColor).singleLine().allBold().build();
      } else {
        trimmedText = new Text.Builder(text, availWidth, Paints.robotoStyleProvider(15), TextColorSets.Regular.NORMAL).singleLine().allBold().build();
      }
    }
  }

  private static final float AVATAR_SIZE = 40f;

  public void addAvatar () {
    receiver = new AvatarReceiver(this);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && text != null) {
      textLeft = Screen.dp(72f); // XGram: account rows keep the text after the avatar
      trimText(true);
    }
    layoutReceiver();
    counter = new Counter.Builder().callback(this).build();
  }

  private void layoutReceiver () {
    if (receiver == null)
      return;
    int left = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? 22f : 12f);
    int size = Screen.dp(AVATAR_SIZE);
    int top = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && getMeasuredHeight() > size ? (getMeasuredHeight() - size) / 2 : Screen.dp(6f); // XGram: centred in the 56dp row
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      receiver.setBounds(viewWidth - left - size, top, viewWidth - left, top + size);
    } else {
      receiver.setBounds(left, top, left + size, top + size);
    }
  }

  public void setAvatar (Tdlib tdlib, long chatId) {
    this.receiver.requestChat(tdlib, chatId, AvatarReceiver.Options.NONE);
  }

  public void setEmojiStatus (TdlibAccount account) {
    TextColorSet colorSet = new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
      @Override
      public long mediaTextComplexColor () {
        return Theme.newComplexColor(true, ColorId.iconActive);
      }
    };
    emojiStatusHelper.setSharedUsageId("account_" + account.id);
    emojiStatusHelper.updateEmoji(account, colorSet);
    trimText(true);
  }

  public void setAvatar (TdlibAccount account) {
    receiver.requestAccount(tdlib, account.id, AvatarReceiver.Options.NONE);
  }

  public void setClickBait (boolean isError, @DrawableRes int iconRes, boolean animated) {
    if (iconRes != 0) {
      setClickBaitIcon(iconRes, isError, animated);
    } else if (isError) {
      setUnreadCount(Tdlib.CHAT_FAILED, counter != null && counter.isMuted(), animated);
    } else if (iconDrawableRes != 0) {
      setClickBaitIcon(0, false, animated);
    } else {
      setUnreadCount(0, false, animated);
    }
  }

  private @DrawableRes int iconDrawableRes;

  public void setClickBaitIcon (@DrawableRes int drawableRes, boolean isError, boolean animated) {
    if (this.iconDrawableRes != drawableRes) {
      this.iconDrawableRes = drawableRes;
      if (drawableRes != 0) {
        // TODO support icon replacement inside Counter without need in re-creating it
        this.counter = new Counter.Builder()
          .drawable(drawableRes, 14f, 0, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)
          .callback((counter, sizeChanged) -> {
          if (sizeChanged) {
            trimText(false);
          }
          invalidate();
        }).build();
        this.counter.setCount(isError ? Tdlib.CHAT_FAILED : Tdlib.CHAT_MARKED_AS_UNREAD, animated);
      } else if (animated && counter != null) {
        this.counter.hide(true);
      } else {
        this.counter = null;
      }
    }
  }

  public void setUnreadCount (int unreadCount, boolean muted, boolean animated) {
    if (unreadCount == 0 && this.counter == null && iconDrawableRes == 0)
      return;
    if (this.counter == null || iconDrawableRes != 0) {
      this.counter = new Counter.Builder()
        .callback((counter, sizeChanged) -> {
        if (sizeChanged) {
          trimText(false);
        }
        invalidate();
      }).build();
    }
    this.counter.setCount(unreadCount, muted, animated);
  }

  @Override
  public void attach () {
    if (receiver != null) {
      receiver.attach();
    }
    emojiStatusHelper.attach();
  }

  @Override
  public void detach () {
    if (receiver != null) {
      receiver.detach();
    }
    emojiStatusHelper.detach();
  }

  @Override
  public void performDestroy () {
    if (receiver != null) {
      receiver.destroy();
    }
    emojiStatusHelper.performDestroy();
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  public boolean toggle (boolean animated) {
    boolean isChecked = !isChecked();
    setChecked(isChecked, animated);
    return isChecked;
  }

  private boolean isDragging;

  public void setIsDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      invalidate();
    }
  }

  @Override
  public void onDraw (Canvas c) {
    boolean rtl = Lang.rtl();
    int viewWidth = getMeasuredWidth();
    layoutReceiver();

    final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    if (isDragging && !xgramM3) {
      c.drawColor(ColorUtils.alphaColor(.85f, Theme.fillingColor()));
    }
    if (xgramM3 && checkFactor > 0f && receiver != null) {
      // XGram: the active item (current account) is a SECONDARY_CONTAINER pill.
      RectF pill = Paints.getRectF();
      pill.set(Screen.dp(12f), 0, viewWidth - Screen.dp(12f), getMeasuredHeight());
      c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), checkFactor)));
    }
    if (icon != null) {
      if (xgramM3) {
        int x = Screen.dp(28f) + (Screen.dp(24f) - icon.getMinimumWidth()) / 2;
        Drawables.draw(c, icon, rtl ? viewWidth - x - icon.getMinimumWidth() : x, iconTop, Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT)));
      } else {
        Drawables.draw(c, icon, rtl ? viewWidth - iconLeft - icon.getMinimumWidth() : iconLeft, iconTop, Paints.getIconGrayPorterDuffPaint());
      }
    }
    if (trimmedText != null) {
      int textTop = xgramM3 ? (getMeasuredHeight() - trimmedText.getLineHeight()) / 2 : Screen.dp(17f);
      trimmedText.draw(c, textLeft, textLeft + trimmedText.getWidth(), 0, textTop);
    }
    emojiStatusHelper.draw(c, textLeft + (trimmedText != null ? trimmedText.getWidth() + Screen.dp(6) : 0), xgramM3 && trimmedText != null ? (getMeasuredHeight() - trimmedText.getLineHeight()) / 2 : Screen.dp(17f));
    if (receiver != null) {
      layoutReceiver();
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholder(c);
      }
      receiver.draw(c);
      if (checkFactor > 0f) {
        final double radians = Math.toRadians(rtl ? 315f : 45f);

        final int x = receiver.centerX() + (int) ((float) receiver.getWidth() / 2 * Math.sin(radians));
        final int y = receiver.centerY() + (int) ((float) receiver.getHeight() / 2 * Math.cos(radians));

        SimplestCheckBox.draw(c, x, y, checkFactor, null);

        RectF rectF = Paints.getRectF();
        int r = Screen.dp(11f);
        rectF.set(x - r, y - r, x + r, y + r);

        c.drawArc(rectF, rtl ? 225f + 170f * (1f - checkFactor) : 135f, 170f * checkFactor, false, Paints.getOuterCheckPaint(xgramM3 ? ColorUtils.compositeColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_LOW), com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), checkFactor)) : Theme.fillingColor()));
      }

    }
    if (counter != null) {
      counter.draw(c, rtl ? Screen.dp(24f) : viewWidth - Screen.dp(24f), getMeasuredHeight() / 2f, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT, 1f, this, ColorId.badgeFailedText);
    }
  }
}
