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
 * File created on 10/08/2015 at 19:56
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;

public class ChatHeaderView extends ComplexHeaderView {
  public interface Callback {
    void onChatHeaderClick ();
  }

  private Callback callback;

  public ChatHeaderView (Context context, Tdlib tdlib, @Nullable ViewController<?> parent) {
    super(context, tdlib, parent);
    setPhotoOpenDisabled(true);
    setOnClickListener(v -> {
      if (callback != null) {
        callback.onChatHeaderClick();
      }
    });
    setUseDefaultClickListener(true);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive: an onSurface state layer on the surface app bar instead of the white header ripple
      int rippleColor = com.fahadali.xgram.appearance.XgramExpressiveC.stateLayerColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE));
      setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(rippleColor), null, new android.graphics.drawable.ColorDrawable(0xffffffff)));
    } else
    setBackgroundResource(ThemeDeprecated.headerSelector());
    setInnerMargins(Screen.dp(56f), Screen.dp(49f));
  }

  private CharSequence forcedSubtitle;

  public void setForcedSubtitle (CharSequence subtitle) {
    if (!StringUtils.equalsOrBothEmpty(forcedSubtitle, subtitle)) {
      this.forcedSubtitle = subtitle;
      setNoStatus(!StringUtils.isEmpty(subtitle));
      if (hasSubtitle()) {
        setSubtitle(subtitle);
      }
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(widthMeasureSpec, HeaderView.getSize(scaleFactor != 0f, true));
  }

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    if (this.scaleFactor != scaleFactor) {
      boolean layout = this.scaleFactor == 0f || scaleFactor == 0f;
      super.setScaleFactor(scaleFactor, fromFactor, toScaleFactor, byScroll);
      if (layout) {
        setEnabled(scaleFactor == 0f);
        requestLayout();
      }
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return callback != null && super.onTouchEvent(e);
  }

  public void setChat (Tdlib tdlib, TdApi.Chat chat, @Nullable ThreadInfo messageThread) {
    this.tdlib = tdlib;

    if (chat == null) {
      setText("Debug controller", "nobody should find this view");
      return;
    }

    getAvatarReceiver().requestChat(tdlib, chat.id, AvatarReceiver.Options.FULL_SIZE);
    setShowVerify(tdlib.chatVerified(chat));
    setShowScam(tdlib.chatScam(chat));
    setShowFake(tdlib.chatFake(chat));
    setShowMute(tdlib.chatNeedsMuteIcon(chat));
    setShowLock(ChatId.isSecret(chat.id));
    if (messageThread != null) {
      setEmojiStatus(null);
      setText(messageThread.chatHeaderTitle(), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : messageThread.chatHeaderSubtitle());
      setExpandedSubtitle(null);
      setUseRedHighlight(false);
      attachChatStatus(messageThread.getChatId(), messageThread.getMessageTopicId());
    } else {
      setEmojiStatus(tdlib.isSelfChat(chat) ? null : tdlib.chatUser(chat));
      setText(tdlib.chatTitle(chat), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
      setUseRedHighlight(tdlib.isRedTeam(chat.id));
      if (xgramNoChatActions) {
        removeChatStatus(); // XGram: the topic list's header (each topic row shows its own "typing…")
      } else {
        attachChatStatus(chat.id, xgramStatusTopic); // XGram: inside a forum topic, only that topic's "typing…"
      }
    }
  }

  // XGram: forum topic whose chat actions the header shows (null: the whole chat).
  private @Nullable TdApi.MessageTopic xgramStatusTopic;
  private boolean xgramNoChatActions;

  /** XGram: no "typing…" in this header at all. */
  public void xgramHideChatActions () {
    this.xgramNoChatActions = true;
    removeChatStatus();
  }

  public void xgramSetStatusTopic (long chatId, @Nullable TdApi.MessageTopic topicId) {
    this.xgramStatusTopic = topicId;
    attachChatStatus(chatId, topicId);
  }

  // XGram M3E: while the app connects (or is offline, or stuck) the subtitle line shows it, with a loading indicator
  // or an icon, instead of the status bar. Frames run only while the spring moves or the indicator is on screen.

  private final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private final com.fahadali.xgram.appearance.XgramConnection.Listener xgramConnListener = this::xgramSetConnection;
  private final org.thunderdog.challegram.util.text.TextColorSet xgramConnColors = () ->
    com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
  private @Nullable com.fahadali.xgram.appearance.XgramMotion.Value xgramConnF;
  private int xgramConnState = com.fahadali.xgram.appearance.XgramConnection.HIDDEN;
  private String xgramConnString = "";
  private @Nullable org.thunderdog.challegram.util.text.Text xgramConnText;
  private int xgramConnTextMaxWidth;
  private long xgramConnStart;
  private @Nullable android.graphics.drawable.Drawable xgramConnIcon;
  private int xgramConnIconState, xgramConnIconColor;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (xgramM3) {
      com.fahadali.xgram.appearance.XgramConnection conn = com.fahadali.xgram.appearance.XgramConnection.instance();
      conn.addListener(xgramConnListener);
      xgramSetConnection(conn.getState(), conn.getText(), false);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (xgramM3) {
      com.fahadali.xgram.appearance.XgramConnection.instance().removeListener(xgramConnListener);
    }
  }

  private void xgramSetConnection (int state, CharSequence text, boolean animated) {
    final boolean show = com.fahadali.xgram.appearance.XgramConnection.isProblem(state);
    if (show) {
      if (state == com.fahadali.xgram.appearance.XgramConnection.PROGRESS && xgramConnState != state) {
        xgramConnStart = android.os.SystemClock.uptimeMillis();
      }
      xgramConnState = state;
      String string = text != null ? text.toString() : "";
      if (!string.equals(xgramConnString)) {
        xgramConnString = string;
        xgramReleaseConnText();
      }
    }
    if (xgramConnF == null) {
      if (!show) {
        return;
      }
      xgramConnF = new com.fahadali.xgram.appearance.XgramMotion.Value(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, 0f, (v, value, velocity) -> invalidate());
    }
    float target = show ? 1f : 0f;
    if (animated && isAttachedToWindow() && getMeasuredWidth() > 0) {
      xgramConnF.animateTo(target);
    } else {
      xgramConnF.setValue(target);
    }
    invalidate();
  }

  private void xgramReleaseConnText () {
    if (xgramConnText != null) {
      xgramConnText.performDestroy();
      xgramConnText = null;
    }
  }

  @Override
  protected float xgramConnectionFactor () {
    return xgramConnF != null ? Math.max(0f, Math.min(1f, xgramConnF.getValue())) : 0f;
  }

  @Override
  protected void xgramDrawConnection (@androidx.annotation.NonNull android.graphics.Canvas c, float left, float subtitleTop, float avatarExpandFactor, int maxWidth) {
    final float f = xgramConnectionFactor();
    if (f <= 0f || xgramConnString.isEmpty()) {
      return;
    }
    final int textMaxWidth = Math.max(Screen.dp(48f), maxWidth - Screen.dp(16f));
    if (xgramConnText == null || xgramConnTextMaxWidth != textMaxWidth) {
      xgramReleaseConnText();
      xgramConnText = new org.thunderdog.challegram.util.text.Text.Builder(xgramConnString, textMaxWidth,
        com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, 12f), xgramConnColors)
        .singleLine()
        .build();
      xgramConnTextMaxWidth = textMaxWidth;
    }
    final float alpha = f * (1f - avatarExpandFactor);
    final float cy = subtitleTop + xgramConnText.getLineHeight() / 2f;
    final float leadCx = left + Screen.dp(6f);
    final float scale = com.fahadali.xgram.appearance.XgramMotion.reduce() ? 1f : .92f + .08f * f;
    final int color = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    final int restore = c.save();
    if (scale != 1f) {
      c.scale(scale, scale, left, cy);
    }
    if (xgramConnState == com.fahadali.xgram.appearance.XgramConnection.PROGRESS) {
      com.fahadali.xgram.appearance.XgramLoadingIndicator.draw(c, leadCx, cy, Screen.dp(12f) * com.fahadali.xgram.appearance.XgramLoadingIndicator.ACTIVE_INDICATOR_SCALE,
        com.fahadali.xgram.appearance.XgramExpressive.withAlpha(color, alpha), android.os.SystemClock.uptimeMillis() - xgramConnStart);
    } else {
      android.graphics.drawable.Drawable icon = xgramConnIcon(color);
      if (icon != null) {
        int size = Screen.dp(14f), x = Math.round(leadCx) - size / 2, y = Math.round(cy) - size / 2;
        icon.setBounds(x, y, x + size, y + size);
        icon.setAlpha(Math.round(255 * alpha));
        icon.draw(c);
      }
    }
    xgramConnText.draw(c, (int) (left + Screen.dp(16f)), (int) subtitleTop, null, alpha);
    c.restoreToCount(restore);
    if (xgramConnState == com.fahadali.xgram.appearance.XgramConnection.PROGRESS) {
      postInvalidateOnAnimation();
    }
  }

  @Nullable
  private android.graphics.drawable.Drawable xgramConnIcon (int color) {
    if (xgramConnIcon == null || xgramConnIconState != xgramConnState) {
      int res = xgramConnState == com.fahadali.xgram.appearance.XgramConnection.OFFLINE ? org.thunderdog.challegram.R.drawable.xgram_cloud_off_24 : org.thunderdog.challegram.R.drawable.baseline_sync_problem_24;
      xgramConnIcon = org.thunderdog.challegram.tool.Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(res));
      xgramConnIconState = xgramConnState;
      xgramConnIconColor = 0;
    }
    if (xgramConnIcon != null && xgramConnIconColor != color) {
      xgramConnIconColor = color;
      xgramConnIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
    }
    return xgramConnIcon;
  }

  // Updates (new)

  private Tdlib tdlib;

  public void updateUserStatus (TdApi.Chat chat) {
    if (StringUtils.isEmpty(forcedSubtitle)) {
      setSubtitle(tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
    }
  }
}
