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
 * File created on 30/05/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.Td;

public class TGMessageCall extends TGMessage {
  private final TdApi.MessageCall callRaw;

  public TGMessageCall (MessagesManager context, TdApi.Message msg, TdApi.MessageCall call) {
    super(context, msg);
    this.callRaw = call;
  }

  // private Drawable phoneIcon, callIcon;
  private @DrawableRes int callIconId;
  private @PorterDuffColorId int callIconColorId;
  // private String title, subtitle;

  private String trimmedTitle, trimmedSubtitle;
  private boolean needFakeTitle;
  private float titleWidth, subtitleWidth;

  @Override
  protected void buildContent (int maxWidth) {
    this.callIconId = CallItem.getSubtitleIcon(callRaw, isOutgoing());
    this.callIconColorId = CallItem.getSubtitleIconColorId(callRaw);
    boolean isFullSubtitle = useBubbles() || callRaw.duration > 0;
    final int titleRes = isFullSubtitle ? TD.getCallName(callRaw, isOutgoing(), true) : isOutgoing() ? R.string.OutgoingCall : R.string.IncomingCall;
    this.xgramM3 = com.fahadali.xgram.appearance.XgramR5Calls.on();
    // XGram M3E: sentence case for Telegram X's English call titles ("Canceled call")
    String title = xgramM3 ? com.fahadali.xgram.appearance.XgramR5Calls.callTitle(titleRes) : Lang.getString(titleRes);
    String subtitle = CallItem.getSubtitle(msg, isFullSubtitle, 1);
    if (useBubbles()) {
      StringBuilder b = new StringBuilder();
      b.append(Lang.time(msg.date, TimeUnit.SECONDS));
      if (!StringUtils.isEmpty(subtitle)) {
        b.append(", ");
        b.append(subtitle);
      }
      subtitle = b.toString();
    } else if (!xgramM3) {
      maxWidth -= Screen.dp(40f) + Screen.dp(11f);
    }
    if (xgramM3) {
      if (xgramCard == null) {
        xgramCard = new com.fahadali.xgram.appearance.XgramR5Calls.CallCard(this::invalidate);
      }
      xgramCard.build(title, subtitle, maxWidth, Screen.dp(18f));
      this.xgramCardWidth = xgramCard.width(maxWidth);
      return;
    }
    this.needFakeTitle = Text.needFakeBold(title);
    this.trimmedTitle = TextUtils.ellipsize(title, Paints.getMediumTextPaint(15f, needFakeTitle), maxWidth, TextUtils.TruncateAt.END).toString();
    this.trimmedSubtitle = TextUtils.ellipsize(subtitle, Paints.getSubtitlePaint(), maxWidth - Screen.dp(20f), TextUtils.TruncateAt.END).toString();
    this.titleWidth = U.measureText(trimmedTitle, Paints.getMediumTextPaint(13f, needFakeTitle));
    this.subtitleWidth = U.measureText(trimmedSubtitle, Paints.getSubtitlePaint());
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePadding + xBubblePaddingSmall;
  }

  // XGram M3E: the call as an Expressive card (XgramR5Calls.CallCard), in bubbles and without them

  private boolean xgramM3;
  private @Nullable com.fahadali.xgram.appearance.XgramR5Calls.CallCard xgramCard;
  private int xgramCardWidth;

  private void xgramDrawContent (MessageView view, Canvas c, int startX, int startY, @NonNull com.fahadali.xgram.appearance.XgramR5Calls.CallCard card) {
    final boolean bubble = useBubbles();
    final boolean outBubble = bubble && isOutgoingBubble();
    final boolean errorish = CallItem.isMissedOrCancelled(callRaw);
    final boolean missed = errorish && !isOutgoing(); // a missed or declined incoming call
    final int cardColor;
    if (outBubble) {
      cardColor = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), .08f);
    } else if (bubble) {
      cardColor = com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), .06f);
    } else {
      cardColor = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH); // the card step, also with Pure black
    }
    // The container is primary in outgoing bubbles, which are primaryContainer themselves
    final int boxRole = missed ? com.fahadali.xgram.appearance.XgramExpressive.ERROR_CONTAINER : outBubble ? com.fahadali.xgram.appearance.XgramExpressive.PRIMARY : com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER;
    final int boxIconRole = missed ? com.fahadali.xgram.appearance.XgramExpressive.ON_ERROR_CONTAINER : outBubble ? com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY : com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER;
    final int titleColor = bubble ? getTextColor() : com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE);
    final int subtitleColor = bubble ? getDecentColor() : com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    final int directionColor = com.fahadali.xgram.appearance.XgramExpressive.color(errorish ? com.fahadali.xgram.appearance.XgramExpressive.ERROR : com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
    final int buttonColor = com.fahadali.xgram.appearance.XgramExpressive.color(outBubble ? com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
    Drawable kindIcon = view.getSparseDrawable(callRaw.isVideo ? R.drawable.baseline_videocam_24 : R.drawable.baseline_phone_24, ColorId.NONE);
    Drawable directionIcon = view.getSparseDrawable(callIconId, ColorId.NONE);
    Drawable buttonIcon = view.getSparseDrawable(callRaw.isVideo ? R.drawable.baseline_videocam_24 : R.drawable.baseline_call_24, ColorId.NONE);
    card.draw(c, startX, startY, xgramCardWidth,
      cardColor, com.fahadali.xgram.appearance.XgramExpressive.color(boxRole), com.fahadali.xgram.appearance.XgramExpressive.color(boxIconRole),
      titleColor, subtitleColor, directionColor, buttonColor,
      kindIcon, directionIcon, buttonIcon);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    final com.fahadali.xgram.appearance.XgramR5Calls.CallCard xgramCard = this.xgramCard;
    if (xgramM3 && xgramCard != null) {
      xgramDrawContent(view, c, startX, startY, xgramCard);
      return;
    }
    Drawable phoneIcon = view.getSparseDrawable(callRaw.isVideo ? R.drawable.baseline_videocam_24 : R.drawable.baseline_phone_24, ColorId.NONE);
    Drawable callIcon = view.getSparseDrawable(callIconId, ColorId.NONE);
    if (useBubbles()) {
      int colorId = isOutgoingBubble() ? ColorId.bubbleOut_file : ColorId.file;
      Drawables.draw(c, phoneIcon, startX + getContentWidth() - getContentHeight() / 2f - phoneIcon.getMinimumWidth() / 2f, startY + getContentHeight() / 2f - phoneIcon.getMinimumHeight() / 2f, PorterDuffPaint.get(colorId));
    } else {
      int radius = Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS);
      c.drawCircle(startX + radius, startY + radius, radius, Paints.fillingPaint(Theme.getColor(ColorId.file)));
      Drawables.draw(c, phoneIcon, startX + radius - phoneIcon.getMinimumWidth() / 2f, startY + radius - phoneIcon.getMinimumHeight() / 2f, Paints.whitePorterDuffPaint());
      startX += radius * 2 + Screen.dp(11f);
    }
    if (useBubbles()) {
      startY -= Screen.dp(4f);
    }
    c.drawText(trimmedTitle, startX, startY + Screen.dp(21f), Paints.getMediumTextPaint(15f, getTextColor(), needFakeTitle));
    Drawables.draw(c, callIcon, startX, startY + Screen.dp(callIconId == R.drawable.baseline_call_missed_18 ? 27.5f : callIconId == R.drawable.baseline_call_made_18 ? 26.5f : 27f), PorterDuffPaint.get(callIconColorId));
    c.drawText(trimmedSubtitle, startX + Screen.dp(20f), startY + Screen.dp(41f), Paints.getRegularTextPaint(13f, getDecentColor()));
  }

  private boolean caught;
  private float startX, startY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) {
      return true;
    }

    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        caught = x >= getContentX() && x <= getContentX() + getContentWidth() && y >= getContentY() && y <= getContentY() + getContentHeight();
        startX = x;
        startY = y;
        if (caught && xgramM3 && xgramCard != null) { // XGram M3E: the state layer of the card or of its call back button
          xgramCard.setPressed(com.fahadali.xgram.appearance.XgramR5Calls.CallCard.targetAt(x, y, getContentX(), getContentY(), xgramCardWidth));
        }
        return caught;
      }
      case MotionEvent.ACTION_CANCEL: {
        xgramReleasePress();
        if (caught) {
          caught = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caught && Math.abs(x - startX) > Screen.getTouchSlop() && Math.abs(y - startY) > Screen.getTouchSlop()) {
          caught = false;
          xgramReleasePress();
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        xgramReleasePress();
        if (caught) {
          caught = true;
          long userId;
          if (isOutgoing()) {
            userId = ChatId.toUserId(msg.chatId);
          } else {
            userId = Td.getSenderUserId(msg);
          }
          if (userId == 0) {
            return false;
          }
          performClickSoundFeedback();
          // XGram: call back the way it was (a video call card calls back with video)
          tdlib.context().calls().makeVideoCall(controller(), userId, null, callRaw.isVideo);
          return true;
        }
        break;
      }
    }

    return caught;
  }

  private void xgramReleasePress () {
    if (xgramCard != null) {
      xgramCard.setPressed(com.fahadali.xgram.appearance.XgramR5Calls.CallCard.TARGET_NONE);
    }
  }

  @Override
  protected int getContentWidth () {
    if (xgramM3 && xgramCard != null) {
      return xgramCardWidth;
    }
    return (int) Math.max(Math.max(titleWidth, subtitleWidth + Screen.dp(20f)), useBubbles() ? Screen.dp(182f) : 0) + Screen.dp(40f) + Screen.dp(11f);
  }

  @Override
  protected int getContentHeight () {
    if (xgramM3 && xgramCard != null) {
      return com.fahadali.xgram.appearance.XgramR5Calls.CallCard.height();
    }
    return useBubbles() ? Screen.dp(46f) : Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2;
  }
}
