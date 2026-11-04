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
    /** Replaces this chat with the target; false when nothing opens (the screen is going away, the chat is gone). */
    boolean xgramOpenNext (Target target);
    /**
     * How much of the list's bottom is out of sight, in px: the Mute / Join bar in front of it (a read-only channel)
     * or the navigation bar. The indicator draws above that, never behind the bar.
     */
    default int xgramPullBottomCover () {
      return 0;
    }
    /**
     * Shows {@code hint} in the chat's bottom bar in place of its label (Mute, Unmute, Join) at {@code shown} (0..1),
     * as Telegram does; null clears it. False when the bar is not in front (the message box shows): the hint then
     * goes in the gap under the target's title.
     */
    default boolean xgramPullHint (@Nullable String hint, float shown, boolean armed) {
      return false;
    }
    /** While the next chat opens: true as long as this screen is still the one in front (nothing opened). */
    default boolean xgramPullInFront () {
      return true;
    }
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

  private float offset; // how far the messages are pulled up, px: the gap that shows above the bottom bar
  private boolean tracking, pulling;
  /** Past the threshold with a target: releasing opens it. */
  private boolean armed;
  /** Released past the threshold: the messages hold still until the next chat covers this screen. */
  private boolean opening;
  /** A touch that came while opening: it neither pulls nor reaches the list. */
  private boolean swallowing;
  /** The bottom bar shows the hint (see {@link Host#xgramPullHint}); the gap then shows only the title. */
  private boolean hintInBar;
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

  /** Telegram's: releasing past 110dp of visible gap opens the next chat. */
  private static int threshold () {
    return Screen.dp(110f);
  }

  /** Only a safety cap: past 160dp the pull's resistance (see {@link #resistance}) is already a soft limit. */
  private static int maxOffset () {
    return Screen.dp(240f);
  }

  /** The hint fades in over the first pixels of the pull. */
  private static final float HINT_FADE_DP = 12f;
  /** On release past the threshold the messages settle at least this far past it and hold there. */
  private static final float HOLD_PAST_DP = 8f;
  /** Nothing covered this screen that long after the release: the messages go back down. */
  private static final long HOLD_TIMEOUT_MS = 1500L, HOLD_RECHECK_MS = 300L;

  /**
   * Telegram's pull resistance, the share of the finger's travel the messages follow: .65 easing to .45 up to the
   * threshold, .45 to .05 over the next 50dp, then .05, a soft limit instead of a wall. Continuous, so the feel does
   * not change at the threshold.
   */
  private static float resistance (float offset) {
    final float threshold = threshold(), soft = Screen.dp(160f);
    if (offset <= threshold) {
      return .65f - .2f * Math.max(0f, offset) / threshold;
    }
    if (offset <= soft) {
      return .45f - .4f * (offset - threshold) / (soft - threshold);
    }
    return .05f;
  }

  /**
   * Moves the pull by the finger's {@code dy} (> 0 up) in small steps through {@link #resistance}, the same way up and
   * down, so moving the finger back retraces the pull.
   */
  private static float pullBy (float offset, float dy) {
    final float step = Math.max(1f, Screen.dp(4f));
    while (dy != 0f) {
      final float d = dy > 0f ? Math.min(dy, step) : Math.max(dy, -step);
      offset += d * resistance(offset);
      dy -= d;
      if (offset <= 0f) {
        return 0f;
      }
    }
    return Math.min(maxOffset(), offset);
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
    final int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      swallowing = opening; // the next chat is opening: this screen holds still, a touch neither pulls nor scrolls
    }
    if (swallowing) {
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        swallowing = false;
      }
      return true;
    }
    switch (action) {
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
          setOffset(pullBy(offset, dy));
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
          setOffset(pullBy(0f, dy));
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
        if (action == MotionEvent.ACTION_UP && armed && target != null && targetIsForHere()) {
          startOpening(target);
        } else {
          animateBack();
        }
        return true;
      }
    }
    return pulling;
  }

  // Release past the threshold: as Telegram, the messages hold still (settling just past the threshold) while the next
  // chat opens, instead of dropping back down first. Telegram X's chat transition then covers this screen; the hold is
  // dropped out of sight once it is hidden (clearHold), or springs back if nothing opened.

  private int holdToken, holdChecks;
  private final Runnable holdTimeout = this::onHoldTimeout;

  private void startOpening (Target t) {
    opening = true;
    holdChecks = 0;
    final int token = ++holdToken;
    animateTo(Math.max(offset, threshold() + Screen.dp(HOLD_PAST_DP)));
    view.removeCallbacks(holdTimeout);
    view.postDelayed(holdTimeout, HOLD_TIMEOUT_MS);
    view.post(() -> { // out of the touch dispatch
      if (opening && token == holdToken && !host.xgramOpenNext(t)) {
        endHold(true);
      }
    });
  }

  private void onHoldTimeout () {
    if (!opening) {
      return;
    }
    if (host.xgramPullInFront()) {
      endHold(true); // nothing covered this screen: back down
    } else if (view.getWindowToken() != null && ++holdChecks < 10) {
      view.postDelayed(holdTimeout, HOLD_RECHECK_MS); // the next chat is still sliding in over it
    } else {
      endHold(false);
    }
  }

  private void endHold (boolean animated) {
    if (!opening) {
      return;
    }
    opening = false;
    holdToken++;
    view.removeCallbacks(holdTimeout);
    if (animated) {
      animateBack();
    } else {
      snapBack();
    }
  }

  /**
   * Released past the threshold and the next chat is on its way (at most until the hold's timeout): Back then waits,
   * or it would close this chat and the next one would still slide in over the chat list once its messages are ready.
   */
  public boolean isOpening () {
    return opening;
  }

  /**
   * The pull move to the next chat began: the hold lasts until this screen is hidden (clearHold), so the timeout can't
   * drop the messages back down mid-move on a slow device. Touches are still swallowed, as opening stays true.
   */
  public void keepHoldUntilHidden () {
    view.removeCallbacks(holdTimeout);
  }

  /** How much of the list's bottom is covered (the Mute / Join bar or the navigation bar), px. */
  public int bottomCover () {
    return host.xgramPullBottomCover();
  }

  /** The chat screen was hidden (the next chat covered it) or is reused: the messages are back in place, no hint. */
  public void clearHold () {
    opening = false;
    holdToken++;
    view.removeCallbacks(holdTimeout);
    if (pulling) {
      pulling = false;
      tracking = false;
    }
    snapBack();
  }

  private void snapBack () {
    if (releaseAnimator != null) {
      releaseAnimator.cancel();
    }
    if (xgramRelease != null) {
      xgramRelease.cancel();
    }
    applyOffset(0f);
    xgramOnReleased();
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

  /** The finger moved the pull. */
  private void setOffset (float offset) {
    applyOffset(offset);
  }

  /** Every change of the pull, from the finger or a spring: the armed state, the hint and the frame follow it. */
  private void applyOffset (float offset) {
    if (this.offset != offset) {
      this.offset = offset;
      updateArmed();
      updateHint();
      view.invalidate();
    }
  }

  /** A finger resting right on the threshold doesn't flip the state (and buzz) with every pixel of jitter. */
  private static final float ARM_HYSTERESIS_DP = 3f;

  /**
   * Haptic once on crossing the threshold with a target, again when the finger takes the pull back under it (a few dp
   * under it, see {@link #ARM_HYSTERESIS_DP}).
   */
  private void updateArmed () {
    final boolean isArmed = target != null && offset >= (armed ? threshold() - Screen.dp(ARM_HYSTERESIS_DP) : threshold());
    if (armed != isArmed) {
      armed = isArmed;
      if (pulling) {
        com.fahadali.xgram.XgramHaptics.gestureThreshold(view, armed); // armed: Android 14's threshold haptic (a tap before); let go: 14+ only
      }
    }
  }

  /** What the bottom bar (or the gap) says: where releasing goes, or that nothing is unread. */
  private String hintText () {
    final boolean topic = host.xgramPullTopicId() != 0;
    if (noTarget) {
      return Lang.getString(topic ? R.string.XgramPullNoTopics : R.string.XgramPullNoChannels);
    }
    if (armed) {
      return Lang.getString(topic ? R.string.XgramPullReleaseTopic : R.string.XgramPullRelease);
    }
    return Lang.getString(topic ? R.string.XgramPullNextTopic : R.string.XgramPullNextChannel);
  }

  /** The hint shows from the first pixel of the pull, in the bottom bar when it is in front, and leaves with it. */
  private void updateHint () {
    final float shown = offset > 0f ? Math.min(1f, offset / Screen.dp(HINT_FADE_DP)) : 0f;
    final String text = shown > 0f ? hintText() : null;
    hintInBar = host.xgramPullHint(text, shown, armed) && text != null;
  }

  private void animateBack () {
    animateTo(0f);
  }

  /** Springs the messages to {@code to}: 0 back in place, or the hold past the threshold while the next chat opens. */
  private void animateTo (float to) {
    if (offset == to) {
      if (to == 0f) {
        xgramOnReleased();
      }
      return;
    }
    releaseFrom = offset;
    releaseTo = to;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E: the messages spring back (SPATIAL_DEFAULT, a snap with Reduce motion)
      if (xgramRelease == null) {
        xgramRelease = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> {
          if (!pulling) {
            applyOffset(Math.max(0f, releaseTo + (releaseFrom - releaseTo) * value));
          }
        });
        xgramRelease.setEndListener(this::xgramOnReleased);
      }
      xgramRelease.setValue(1f);
      xgramRelease.animateTo(0f);
      return;
    }
    if (releaseAnimator == null) {
      releaseAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, 1f);
    }
    releaseAnimator.forceFactor(1f);
    releaseAnimator.animateTo(0f);
  }

  private float releaseFrom, releaseTo;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (!pulling) {
      applyOffset(Math.max(0f, releaseTo + (releaseFrom - releaseTo) * factor));
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    xgramOnReleased();
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
        onTargetChanged();
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
        onTargetChanged();
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
      if (tdlib.hasPasscode(chat)) {
        continue; // it would open its passcode screen instead of the chat
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
    armed = false;
    xgramClearAvatar(); // XGram M3E
  }

  /** The lookup came back while the messages are pulled: releasing may open it now, the hint says so. */
  private void onTargetChanged () {
    updateArmed();
    updateHint();
    view.invalidate();
  }

  // Drawing

  /**
   * Draws the indicator in the strip the messages left free at the bottom of the list, above what covers the list's
   * bottom (the Mute / Join bar, the navigation bar), so none of it is ever behind the bar. The circle sits in the gap,
   * growing with it until all of it fits 8dp under the messages; the target's title comes in under it once it fits
   * whole.
   */
  public void draw (Canvas c, int width, int height) {
    if (offset <= 0f) {
      return;
    }
    height -= host.xgramPullBottomCover();
    if (height <= 0) {
      return;
    }
    final boolean expressive = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    float progress = Math.min(1f, offset / threshold());
    float alpha = Math.min(1f, offset / Screen.dp(24f));
    int bg = host.xgramPullBackgroundColor();
    int textColor = host.xgramPullTextColor();

    int radius = Screen.dp(expressive ? 20f : 18f);
    float cx = width / 2f;
    float stripTop = height - offset;
    float margin = Screen.dp(8f);
    float scale = Math.min(1f, offset / (radius * 2f + margin));
    float cy = stripTop + Math.min(offset / 2f, radius + margin);

    // Circle with an arrow, filled when releasing would open the target.
    Paint fill = Paints.fillingPaint(alphaColor(alpha, bg));
    final int scaleSave = scale < 1f ? c.save() : -1;
    if (scaleSave != -1) {
      c.scale(scale, scale, cx, cy);
    }
    if (expressive) {
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
    if (scaleSave != -1) {
      c.restoreToCount(scaleSave);
    }

    // Text pill under the circle: the target's title, and the hint as a second line only when the bottom bar can't
    // show it (the message box is there). Nothing while the bar says it all (looking, nothing unread).
    final String title, hint;
    if (noTarget) {
      title = hintInBar ? null : hintText();
      hint = null;
    } else if (target == null) {
      title = hintInBar ? null : Lang.getString(R.string.XgramPullLoading);
      hint = null;
    } else {
      title = target.title;
      hint = hintInBar ? null : hintText();
    }
    if (title == null) {
      return;
    }
    float textTop = cy + radius + margin;
    if (expressive) {
      xgramDrawPill(c, width, height, cx, textTop, alpha, title, hint);
      return;
    }
    final float lack = textTop + Screen.dp(hint != null ? 40f : 24f) - height;
    if (lack >= margin) {
      return; // not enough room yet
    }
    if (lack > 0f) {
      // Its last 8dp: pushed up into the margin under the circle as it fades in or out, never behind the bar
      alpha *= 1f - lack / margin;
      textTop -= lack;
      fill = Paints.fillingPaint(alphaColor(alpha, bg));
    }
    TextPaint titlePaint = Paints.getBoldPaint13(false, alphaColor(alpha, textColor));
    TextPaint hintPaint = Paints.getRegularTextPaint(12f, alphaColor(alpha * .8f, textColor));
    int maxTextWidth = width - Screen.dp(64f);
    CharSequence titleText = TextUtils.ellipsize(title, titlePaint, maxTextWidth, TextUtils.TruncateAt.END);
    CharSequence hintText = hint != null ? TextUtils.ellipsize(hint, hintPaint, maxTextWidth, TextUtils.TruncateAt.END) : null;
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
    if (pulling || opening || offset > 0f) {
      return; // still pulled, or holding while the next chat opens
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
    final float margin = Screen.dp(8f), lack = top + Screen.dp(hint != null ? PILL_TWO_LINES : PILL_ONE_LINE) - height;
    if (lack >= margin) {
      return; // not enough room yet (with its hint when it has one)
    }
    final float fit = lack > 0f ? 1f - lack / margin : 1f;
    if (lack > 0f) {
      top -= lack; // its last 8dp: pushed up into the margin under the circle as it fades in or out, never behind the bar
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
    final boolean twoLines = hint != null;
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
    final float a = alpha * shown * fit;
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
