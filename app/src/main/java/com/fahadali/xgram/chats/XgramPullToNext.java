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
    /**
     * XGram M3E: true over a photo wallpaper, where the label pill takes surfaceContainerHigh instead of
     * secondaryContainer.
     */
    default boolean xgramPullOverPhoto () {
      return false;
    }
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
        if (xgramRelease != null && xgramRelease.isRunning()) { // XGram M3E
          xgramRelease.cancel();
          setOffset(0f);
          xgramOnReleased();
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
        if (dy > 0 && totalUp > Screen.getTouchSlop() && !list.canScrollVertically(1) && host.xgramCanPullToNext() && !XgramTextSelection.isDragging()) { // a finger selecting text never pulls
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
      xgramOnReleased();
      return;
    }
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E: the messages spring back (SPATIAL_DEFAULT, a snap with Reduce motion)
      if (xgramRelease == null) {
        xgramRelease = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> {
          if (!pulling) {
            offset = Math.max(0f, releaseFrom * value);
            view.invalidate();
          }
        });
        xgramRelease.setEndListener(this::xgramOnReleased);
      }
      releaseFrom = offset;
      xgramRelease.setValue(1f);
      xgramRelease.animateTo(0f);
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
        xgramRequestAvatar(); // XGram M3E
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
        xgramRequestAvatar(); // XGram M3E
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
    xgramClearAvatar(); // XGram M3E
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
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      radius = Screen.dp(20f);
      xgramDrawIndicator(c, cx, cy, radius, progress, alpha, bg, textColor);
    } else {
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
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramDrawPill(c, width, height, cx, textTop, alpha, title, hint);
      return;
    }
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

  // XGram: Material 3 Expressive indicator (the PullToRefresh LoadingIndicator pattern)

  @Nullable private Drawable xgramArrow, xgramCheck;
  @Nullable private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramArmed;

  /**
   * A 40dp disk with a wavy determinate ring for the pull progress (4dp, 1.6dp amplitude, primary on primary .24).
   * Past the threshold the disk fills primaryContainer and the glyph turns onPrimaryContainer. Draws with the
   * helper's own paints, so the shared filling paint keeps the pill color.
   */
  private void xgramDrawIndicator (Canvas c, float cx, float cy, int radius, float progress, float alpha, int bg, int textColor) {
    final boolean isArmed = armed && target != null;
    if (xgramArmed == null) {
      xgramArmed = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, isArmed ? 1f : 0f, (s, value) -> view.invalidate());
    } else if (xgramArmed.getTarget() != (isArmed ? 1f : 0f)) {
      xgramArmed.animateTo(isArmed ? 1f : 0f);
    }
    final float armedFactor = Math.max(0f, Math.min(1f, xgramArmed.getValue()));
    final int primary = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
    final int disk = me.vkryl.core.ColorUtils.fromToArgb(bg, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER), armedFactor);
    final int glyph = me.vkryl.core.ColorUtils.fromToArgb(textColor, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), armedFactor);
    com.fahadali.xgram.appearance.XgramM3Content.drawPill(c, cx - radius, cy - radius, cx + radius, cy + radius, alphaColor(alpha, disk));
    if (target != null) {
      final float stroke = Screen.dpf(com.fahadali.xgram.appearance.XgramM3Content.WAVY_STROKE_DP);
      final float amplitude = Screen.dpf(com.fahadali.xgram.appearance.XgramM3Content.WAVY_AMPLITUDE_DP);
      final float ringRadius = radius - stroke / 2f - amplitude - Screen.dpf(.5f);
      com.fahadali.xgram.appearance.XgramWavyProgress.drawCircular(c, cx, cy, ringRadius, stroke, progress,
        alphaColor(alpha, primary), alphaColor(alpha * .24f, primary), amplitude);
      if (pulling && com.fahadali.xgram.appearance.XgramM3Content.needsWaveFrame(progress, 1f)) {
        view.postInvalidateOnAnimation(); // the wave travels while the finger holds the pull
      }
    }
    final Drawable icon;
    if (noTarget) {
      if (xgramCheck == null) {
        xgramCheck = Drawables.get(view.getResources(), R.drawable.baseline_check_24);
      }
      icon = xgramCheck;
    } else {
      if (xgramArrow == null) {
        xgramArrow = Drawables.get(view.getResources(), R.drawable.baseline_arrow_upward_24);
      }
      icon = xgramArrow;
    }
    if (icon != null) {
      Drawables.drawCentered(c, icon, cx, cy, Paints.getPorterDuffPaint(alphaColor(alpha, glyph)));
    }
  }

  private static int alphaColor (float alpha, int color) {
    return me.vkryl.core.ColorUtils.alphaColor(alpha, color);
  }

  // XGram M3E: the label as a full-corner tonal pill (secondaryContainer, surfaceContainerHigh over a photo wallpaper)
  // holding the target chat's 24dp avatar, its title in labelLarge and the hint in labelMedium onSurfaceVariant. It
  // scales in from .8 on EFFECTS_FAST as a pull starts and turns primaryContainer with the disk once releasing opens
  // the target. The texts are measured and ellipsized only when they change, nothing is allocated per frame.

  private static final float PILL_ONE_LINE = 40f, PILL_TWO_LINES = 52f, PILL_AVATAR = 24f, PILL_GAP = 8f;
  private static final float PILL_PAD_AVATAR = 8f, PILL_PAD = 16f;

  @Nullable private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramRelease, xgramPillShown;
  @Nullable private org.thunderdog.challegram.loader.AvatarReceiver xgramAvatar;
  @Nullable private TextPaint xgramTitlePaint, xgramHintPaint;
  private final RectF xgramPill = new RectF();
  @Nullable private String xgramTitleSource, xgramHintSource;
  @Nullable private CharSequence xgramTitleText, xgramHintText;
  private float xgramTitleWidth, xgramHintWidth;
  private int xgramTextMax = -1;

  /** The target chat's avatar for the pill (a channel; a topic of this forum has none), only for the gesture. */
  private void xgramRequestAvatar () {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return;
    }
    if (target == null || target.topic != null) {
      xgramClearAvatar();
      return;
    }
    if (xgramAvatar == null) {
      xgramAvatar = new org.thunderdog.challegram.loader.AvatarReceiver(view);
      xgramAvatar.attach();
    }
    if (!xgramAvatar.isDisplayingChat(target.chatId)) {
      xgramAvatar.requestChat(host.tdlib(), target.chatId, org.thunderdog.challegram.loader.AvatarReceiver.Options.NONE);
    }
  }

  /** Drops the avatar's data and its update listeners (the receiver lives only while a pull shows it). */
  private void xgramClearAvatar () {
    if (xgramAvatar != null) {
      xgramAvatar.destroy();
    }
  }

  /** The messages are back in place: the pill's entrance resets, the avatar lets go of its chat. */
  private void xgramOnReleased () {
    if (pulling) {
      return;
    }
    if (xgramPillShown != null) {
      xgramPillShown.setValue(0f);
    }
    xgramClearAvatar();
  }

  private void xgramEnsurePaints () {
    if (xgramTitlePaint == null) {
      xgramTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      xgramTitlePaint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L, false));
      xgramHintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
      xgramHintPaint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, false));
    }
    xgramTitlePaint.setTextSize(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_L)));
    xgramHintPaint.setTextSize(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M)));
  }

  private void xgramLayoutTexts (String title, @Nullable String hint, int maxWidth) {
    if (maxWidth == xgramTextMax && TextUtils.equals(title, xgramTitleSource) && TextUtils.equals(hint, xgramHintSource)) {
      return;
    }
    xgramTextMax = maxWidth;
    xgramTitleSource = title;
    xgramHintSource = hint;
    xgramTitleText = TextUtils.ellipsize(title, xgramTitlePaint, Math.max(0, maxWidth), TextUtils.TruncateAt.END);
    xgramTitleWidth = xgramTitlePaint.measureText(xgramTitleText, 0, xgramTitleText.length());
    if (hint != null) {
      xgramHintText = TextUtils.ellipsize(hint, xgramHintPaint, Math.max(0, maxWidth), TextUtils.TruncateAt.END);
      xgramHintWidth = xgramHintPaint.measureText(xgramHintText, 0, xgramHintText.length());
    } else {
      xgramHintText = null;
      xgramHintWidth = 0f;
    }
  }

  private void xgramDrawPill (Canvas c, int width, int height, float cx, float top, float alpha, String title, @Nullable String hint) {
    if (top + Screen.dp(PILL_ONE_LINE) > height) {
      return; // not enough room yet
    }
    if (xgramPillShown == null) {
      xgramPillShown = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> view.invalidate());
    }
    if (xgramPillShown.getTarget() != 1f) {
      xgramPillShown.animateTo(1f);
    }
    final float shown = Math.max(0f, Math.min(1f, xgramPillShown.getValue()));
    if (shown <= 0f) {
      return;
    }
    xgramEnsurePaints();
    final boolean twoLines = hint != null && top + Screen.dp(PILL_TWO_LINES) <= height;
    final boolean hasAvatar = xgramAvatar != null && !xgramAvatar.isEmpty() && target != null && target.topic == null;
    final float padStart = Screen.dp(hasAvatar ? PILL_PAD_AVATAR : PILL_PAD), padEnd = Screen.dp(PILL_PAD);
    final float avatarSpace = hasAvatar ? Screen.dp(PILL_AVATAR) + Screen.dp(PILL_GAP) : 0f;
    final int maxText = (int) (width - Screen.dp(32f) - padStart - padEnd - avatarSpace);
    xgramLayoutTexts(title, twoLines ? hint : null, maxText);
    final float textWidth = Math.max(xgramTitleWidth, twoLines ? xgramHintWidth : 0f);
    final float pillWidth = padStart + avatarSpace + textWidth + padEnd;
    final float pillHeight = Screen.dp(twoLines ? PILL_TWO_LINES : PILL_ONE_LINE);

    final float armedFactor = xgramArmed != null ? Math.max(0f, Math.min(1f, xgramArmed.getValue())) : 0f;
    final int restRole = host.xgramPullOverPhoto() ? com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH : com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER;
    final int restContent = host.xgramPullOverPhoto() ? com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE : com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER;
    final int container = me.vkryl.core.ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(restRole), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER), armedFactor);
    final int titleColor = me.vkryl.core.ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(restContent), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), armedFactor);
    final int hintColor = me.vkryl.core.ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), armedFactor);

    final float scale = .8f + .2f * shown;
    final float a = alpha * shown;
    final int saveCount = c.save();
    c.scale(scale, scale, cx, top);
    xgramPill.set(cx - pillWidth / 2f, top, cx + pillWidth / 2f, top + pillHeight);
    com.fahadali.xgram.appearance.XgramM3Content.drawPill(c, xgramPill.left, xgramPill.top, xgramPill.right, xgramPill.bottom, alphaColor(a, container));

    final boolean rtl = Lang.rtl();
    float textStart = rtl ? xgramPill.right - padStart - avatarSpace : xgramPill.left + padStart + avatarSpace;
    if (hasAvatar) {
      final int size = Screen.dp(PILL_AVATAR);
      final int left = Math.round(rtl ? xgramPill.right - padStart - size : xgramPill.left + padStart);
      final int avatarTop = Math.round(xgramPill.centerY() - size / 2f);
      xgramAvatar.setBounds(left, avatarTop, left + size, avatarTop + size);
      xgramAvatar.setPaintAlpha(a);
      if (xgramAvatar.needPlaceholder()) {
        xgramAvatar.drawPlaceholder(c);
      }
      xgramAvatar.draw(c);
      xgramAvatar.restorePaintAlpha();
    }
    final float titleLine = Screen.dp(20f), hintLine = Screen.dp(16f);
    final float blockTop = xgramPill.centerY() - (titleLine + (twoLines && xgramHintText != null ? hintLine : 0f)) / 2f;
    if (xgramTitleText != null) {
      xgramTitlePaint.setColor(alphaColor(a, titleColor));
      final float baseline = blockTop + titleLine / 2f - (xgramTitlePaint.ascent() + xgramTitlePaint.descent()) / 2f;
      c.drawText(xgramTitleText, 0, xgramTitleText.length(), rtl ? textStart - xgramTitleWidth : textStart, baseline, xgramTitlePaint);
    }
    if (twoLines && xgramHintText != null) {
      xgramHintPaint.setColor(alphaColor(a, hintColor));
      final float baseline = blockTop + titleLine + hintLine / 2f - (xgramHintPaint.ascent() + xgramHintPaint.descent()) / 2f;
      c.drawText(xgramHintText, 0, xgramHintText.length(), rtl ? textStart - xgramHintWidth : textStart, baseline, xgramHintPaint);
    }
    c.restoreToCount(saveCount);
  }
}
