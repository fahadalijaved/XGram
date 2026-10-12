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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

/**
 * Official Telegram's "pull up for the next unread channel": at the bottom of a channel, pulling the messages up
 * shows the next unread channel of the same chat list and releasing opens it in place of this one. Inside a forum
 * topic it goes to the next unread topic of the forum.
 */
public final class XgramPullToNext implements FactorAnimator.Target {
  /** The chat screen the gesture lives in. */
  public interface Host {
    /** Pulling makes sense right now: a channel or topic scrolled to its newest message, nothing else going on. */
    boolean xgramCanPullToNext ();
    Tdlib tdlib ();
    long xgramPullChatId ();
    /** Forum topic shown, or 0 for a channel. */
    int xgramPullTopicId ();
    @Nullable TdApi.ChatList xgramPullChatList ();
    /** Replaces this chat with the target. */
    void xgramOpenNext (Target target);
    /** Pill and text colors that follow the wallpaper. */
    int xgramPullBackgroundColor ();
    int xgramPullTextColor ();
  }

  /** What releasing opens. */
  public static final class Target {
    public final long chatId;
    @Nullable public final TdApi.ForumTopic topic;
    public final String title;

    Target (long chatId, @Nullable TdApi.ForumTopic topic, String title) {
      this.chatId = chatId;
      this.topic = topic;
      this.title = title;
    }
  }

  private final Host host;
  private final View view;

  private float offset; // how far the messages are pulled up, px
  private boolean tracking, pulling, armed;
  private float downX, downY, lastY;
  private int generation;
  private boolean loading;
  @Nullable private Target target;
  private boolean noTarget;
  private FactorAnimator releaseAnimator;

  public XgramPullToNext (Host host, View view) {
    this.host = host;
    this.view = view;
  }

  private static int threshold () {
    return Screen.dp(96f);
  }

  private static int maxOffset () {
    return Screen.dp(150f);
  }

  public float getOffset () {
    return offset;
  }

  // Touch

  /**
   * Called from the list's dispatchTouchEvent. Returns true while the gesture belongs to the pull; the caller then
   * cancels the list's own handling once (see {@link #needsCancel()}).
   */
  public boolean onTouchEvent (RecyclerView list, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        if (releaseAnimator != null && releaseAnimator.isAnimating()) {
          releaseAnimator.cancel();
          setOffset(0f);
        }
        tracking = true;
        pulling = false;
        downX = e.getX();
        downY = lastY = e.getY();
        return false;
      }
      case MotionEvent.ACTION_POINTER_DOWN: {
        if (!pulling) {
          tracking = false; // pinch or similar: not ours
        }
        return pulling;
      }
      case MotionEvent.ACTION_MOVE: {
        float y = e.getY();
        float dy = lastY - y; // > 0: finger moves up
        lastY = y;
        if (pulling) {
          float resistance = offset > threshold() ? .25f : .5f;
          setOffset(Math.max(0f, Math.min(maxOffset(), offset + dy * (dy > 0 ? resistance : 1f))));
          return true;
        }
        if (!tracking) {
          return false;
        }
        float totalUp = downY - y;
        if (Math.abs(e.getX() - downX) > Math.abs(totalUp) && Math.abs(e.getX() - downX) > Screen.getTouchSlop()) {
          tracking = false; // horizontal: swipe to reply, back gesture
          return false;
        }
        if (dy > 0 && totalUp > Screen.getTouchSlop() && !list.canScrollVertically(1) && host.xgramCanPullToNext()) {
          pulling = true;
          needsCancel = true;
          armed = false;
          prepareTarget();
          list.getParent().requestDisallowInterceptTouchEvent(true);
          setOffset(Math.min(maxOffset(), dy * .5f));
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        tracking = false;
        if (!pulling) {
          return false;
        }
        pulling = false;
        boolean open = e.getActionMasked() == MotionEvent.ACTION_UP && offset >= threshold() && target != null && targetIsForHere();
        animateBack();
        if (open) {
          Target t = target;
          view.postDelayed(() -> host.xgramOpenNext(t), 60);
        }
        return true;
      }
    }
    return pulling;
  }

  private boolean needsCancel;

  /** True once when a pull starts: the list must get an ACTION_CANCEL so it stops scrolling and pressing. */
  public boolean needsCancel () {
    if (needsCancel) {
      needsCancel = false;
      return true;
    }
    return false;
  }

  private void setOffset (float offset) {
    if (this.offset != offset) {
      boolean wasArmed = this.offset >= threshold();
      this.offset = offset;
      boolean isArmed = offset >= threshold();
      if (pulling && isArmed != wasArmed && isArmed && target != null) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      }
      armed = isArmed;
      view.invalidate();
    }
  }

  private void animateBack () {
    if (offset == 0f) {
      return;
    }
    if (releaseAnimator == null) {
      releaseAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, 1f);
    }
    releaseFrom = offset;
    releaseAnimator.forceFactor(1f);
    releaseAnimator.animateTo(0f);
  }

  private float releaseFrom;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (!pulling) {
      offset = releaseFrom * factor;
      view.invalidate();
    }
  }

  // Target

  /** Where the current target was looked up: Telegram X reuses chat screens, so it can belong to an earlier chat. */
  private long targetChatId;
  private int targetTopicId;

  private boolean targetIsForHere () {
    return targetChatId == host.xgramPullChatId() && targetTopicId == host.xgramPullTopicId();
  }

  /** Looks the next unread chat or topic up again on every pull: unread counts change while reading. */
  private void prepareTarget () {
    long chatId = host.xgramPullChatId();
    int topicId = host.xgramPullTopicId();
    if (!targetIsForHere()) {
      reset(); // found for another chat: never show or open it here
    }
    if (loading) {
      return;
    }
    targetChatId = chatId;
    targetTopicId = topicId;
    loading = true;
    final int gen = ++generation;
    Tdlib tdlib = host.tdlib();
    if (topicId != 0) {
      tdlib.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), (topics, error) -> tdlib.ui().post(() -> {
        if (gen != generation) {
          return;
        }
        loading = false;
        target = topics != null ? pickTopic(tdlib, chatId, topicId, topics.topics) : null;
        noTarget = target == null;
        view.invalidate();
      }));
    } else {
      TdApi.ChatList chatList = host.xgramPullChatList();
      tdlib.send(new TdApi.GetChats(chatList != null ? chatList : new TdApi.ChatListMain(), 200), (chats, error) -> tdlib.ui().post(() -> {
        if (gen != generation) {
          return;
        }
        loading = false;
        target = chats != null ? pickChannel(tdlib, chatId, chats.chatIds) : null;
        noTarget = target == null;
        view.invalidate();
      }));
    }
  }

  /** The topic list's order: the first other topic with unread messages. */
  @Nullable
  private static Target pickTopic (Tdlib tdlib, long chatId, int currentTopicId, TdApi.ForumTopic[] topics) {
    for (TdApi.ForumTopic topic : topics) {
      if (topic.info == null || topic.info.forumTopicId == currentTopicId || topic.info.isHidden) {
        continue;
      }
      com.fahadali.xgram.topics.XgramForums.normalizeUnread(tdlib, topic);
      if (topic.unreadCount > 0) {
        return new Target(chatId, topic, com.fahadali.xgram.topics.XgramForums.topicTitle(topic.info));
      }
    }
    return null;
  }

  /** As Telegram: the first other unread channel in the list, unmuted ones first. */
  @Nullable
  private static Target pickChannel (Tdlib tdlib, long currentChatId, long[] chatIds) {
    long muted = 0;
    for (long id : chatIds) {
      if (id == currentChatId || !tdlib.isChannel(id)) {
        continue;
      }
      TdApi.Chat chat = tdlib.chat(id);
      if (chat == null || chat.unreadCount <= 0) {
        continue;
      }
      if (tdlib.chatNotificationsEnabled(id)) {
        return new Target(id, null, tdlib.chatTitle(id));
      } else if (muted == 0) {
        muted = id;
      }
    }
    return muted != 0 ? new Target(muted, null, tdlib.chatTitle(muted)) : null;
  }

  /** The chat changed (or new messages arrived): look again on the next pull. */
  public void reset () {
    generation++;
    loading = false;
    target = null;
    noTarget = false;
    targetChatId = 0;
    targetTopicId = 0;
  }

  // Drawing

  /** Draws the indicator in the strip the messages left free at the bottom of the list. */
  public void draw (Canvas c, int width, int height) {
    if (offset <= 0f) {
      return;
    }
    float progress = Math.min(1f, offset / threshold());
    float alpha = Math.min(1f, offset / Screen.dp(36f));
    int bg = host.xgramPullBackgroundColor();
    int textColor = host.xgramPullTextColor();

    int radius = Screen.dp(18f);
    float cx = width / 2f;
    float stripTop = height - offset;
    float cy = stripTop + Math.min(offset / 2f, Screen.dp(30f));

    // Circle with an arrow, filled when releasing would open the target.
    Paint fill = Paints.fillingPaint(alphaColor(alpha, bg));
    c.drawCircle(cx, cy, radius, fill);
    if (target != null) {
      RectF rect = Paints.getRectF();
      int inset = Screen.dp(2f);
      rect.set(cx - radius + inset, cy - radius + inset, cx + radius - inset, cy + radius - inset);
      c.drawArc(rect, -90f, 360f * progress, false, Paints.getProgressPaint(alphaColor(alpha, textColor), Screen.dp(2f)));
    }
    Drawable arrow = Drawables.get(view.getResources(), noTarget ? R.drawable.baseline_check_24 : R.drawable.baseline_arrow_upward_24);
    if (arrow != null) {
      Drawables.drawCentered(c, arrow, cx, cy, Paints.getPorterDuffPaint(alphaColor(alpha, textColor)));
    }

    // Text pill under the circle (two lines when there is room).
    String title, hint;
    if (noTarget) {
      title = Lang.getString(host.xgramPullTopicId() != 0 ? R.string.XgramPullNoTopics : R.string.XgramPullNoChannels);
      hint = null;
    } else if (target == null) {
      title = Lang.getString(R.string.XgramPullLoading);
      hint = null;
    } else {
      title = target.title;
      hint = Lang.getString(armed ? R.string.XgramPullRelease : (host.xgramPullTopicId() != 0 ? R.string.XgramPullNextTopic : R.string.XgramPullNextChannel));
    }
    float textTop = cy + radius + Screen.dp(8f);
    if (textTop + Screen.dp(18f) > height) {
      return; // not enough room yet
    }
    TextPaint titlePaint = Paints.getBoldPaint13(false, alphaColor(alpha, textColor));
    TextPaint hintPaint = Paints.getRegularTextPaint(12f, alphaColor(alpha * .8f, textColor));
    int maxTextWidth = width - Screen.dp(64f);
    CharSequence titleText = TextUtils.ellipsize(title, titlePaint, maxTextWidth, TextUtils.TruncateAt.END);
    CharSequence hintText = hint != null && textTop + Screen.dp(40f) <= height ? TextUtils.ellipsize(hint, hintPaint, maxTextWidth, TextUtils.TruncateAt.END) : null;
    float titleWidth = titlePaint.measureText(titleText, 0, titleText.length());
    float hintWidth = hintText != null ? hintPaint.measureText(hintText, 0, hintText.length()) : 0f;
    float pillWidth = Math.max(titleWidth, hintWidth) + Screen.dp(20f);
    float pillHeight = hintText != null ? Screen.dp(40f) : Screen.dp(24f);
    RectF pill = Paints.getRectF();
    pill.set(cx - pillWidth / 2f, textTop, cx + pillWidth / 2f, textTop + pillHeight);
    c.drawRoundRect(pill, Screen.dp(12f), Screen.dp(12f), fill);
    c.drawText(titleText, 0, titleText.length(), cx - titleWidth / 2f, textTop + Screen.dp(16.5f), titlePaint);
    if (hintText != null) {
      c.drawText(hintText, 0, hintText.length(), cx - hintWidth / 2f, textTop + Screen.dp(33f), hintPaint);
    }
  }

  private static int alphaColor (float alpha, int color) {
    return me.vkryl.core.ColorUtils.alphaColor(alpha, color);
  }
}
