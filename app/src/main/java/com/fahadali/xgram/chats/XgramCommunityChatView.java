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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.AnimationUtils;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.AttachDelegate;

import me.vkryl.core.lambda.Destroyable;

/**
 * A full chat row (avatar, time, ticks, badges, typing) inside a settings-style list: the chat list's ChatView, reachable
 * by SettingHolder, which attaches, detaches and recycles custom views only through these two interfaces.
 */
public final class XgramCommunityChatView extends ChatView implements AttachDelegate, Destroyable {
  public XgramCommunityChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /** Recycled: let go of the row, its TGChat lives on in the controller. */
  @Override
  public void performDestroy () {
    setChat(null);
  }

  // XGram M3E: while a forum's topics load, a pulsing surfaceContainerHighest bar stands on the topics line.

  private static final long PULSE_MS = 1200L;
  private static final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF placeholderRect = new RectF();
  private boolean topicsLoading;

  public void xgramSetTopicsLoading (boolean loading) {
    if (this.topicsLoading != loading) {
      this.topicsLoading = loading;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    super.onDraw(c);
    if (!topicsLoading || !XgramExpressive.isEnabled()) {
      return;
    }
    TGChat chat = getChat();
    Text line = chat != null ? chat.getPrefix() : null;
    // The topics line is the second line only with three lines (TGChat.xgramSetTopicsPrefix): in two lines the prefix
    // is the sender, one line has none
    if (line == null || !XgramChatListMode.isThreeLines(chat.getListMode())) {
      return;
    }
    int mode = chat.getListMode();
    // Where ChatView draws the line now: shifted with the title row while the chat list view springs, fading in over
    // the second half as the new mode's lines do
    final ChatView.Metrics own = ChatView.metricsOf(mode);
    final ChatView.Metrics now = chat.followsGlobalMode() ? XgramChatListMode.metrics(mode) : own;
    final boolean modeTransition = chat.followsGlobalMode() && XgramChatListMode.isAnimating() && mode == XgramChatListMode.targetMode();
    final float alpha = modeTransition ? Math.max(0f, Math.min(1f, XgramChatListMode.fraction() * 2f - 1f)) : 1f;
    if (alpha <= 0f) {
      return;
    }
    int left = ChatView.getLeftPadding(mode) + Math.round(now.textLeft - own.textLeft);
    int width = Math.min(line.getWidth(), Math.max(0, getMeasuredWidth() - left - ChatView.getRightPadding()));
    if (width <= 0) {
      return;
    }
    float centerY = ChatView.getTextTop(mode) + Math.round(now.titleTop - own.titleTop) + line.getLineHeight() / 2f;
    float halfHeight = Screen.dp(6f);
    if (Lang.rtl()) {
      placeholderRect.set(getMeasuredWidth() - left - width, centerY - halfHeight, getMeasuredWidth() - left, centerY + halfHeight);
    } else {
      placeholderRect.set(left, centerY - halfHeight, left + width, centerY + halfHeight);
    }
    // XGram M3E r6: Reduce motion keeps the bar still
    final boolean still = com.fahadali.xgram.appearance.XgramMotion.reduce();
    long t = AnimationUtils.currentAnimationTimeMillis() % PULSE_MS;
    float pulse = still ? .5f : .5f + .5f * (float) Math.cos(2.0 * Math.PI * t / PULSE_MS);
    placeholderPaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), (.55f + .45f * pulse) * alpha));
    c.drawRoundRect(placeholderRect, halfHeight, halfHeight, placeholderPaint);
    if (!still && getWindowToken() != null) {
      postInvalidateOnAnimation();
    }
  }
}
