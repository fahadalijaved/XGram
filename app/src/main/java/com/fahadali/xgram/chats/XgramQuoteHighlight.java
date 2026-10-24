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

import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMotion;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

/**
 * Quote highlight: a jump from a reply that quotes part of a message also shows the quoted words in that message for a
 * moment, behind their glyphs, on top of Telegram X's flash of the whole message. The colour: Material 3 Expressive, a
 * state layer of the bubble's own text colour (the glyphs keep their colour, so an opaque container would hide them in
 * dark mode); Telegram X, its search highlight. In a message taller than the list (Telegram X shows its top) a quote
 * out of sight is scrolled into view, and held once the list is at rest. With no match (the quote was edited away, a
 * translation is shown, or an album shows another item's caption) only the flash runs.
 *
 * The jump arms the quote here ({@link #prepareJump}); the message takes it when Telegram X flashes it after the jump
 * ({@link #onJump}, from TGMessage.highlight), whether it was loaded already or only after the jump, in this chat or
 * another. The state lives on the TGMessage: a recycled view or scrolling away never leaves a highlight behind.
 */
public final class XgramQuoteHighlight implements FactorAnimator.Target {
  private static final long FADE_IN_MS = 150L, HOLD_MS = 2500L, FADE_OUT_MS = 600L;
  /** How long a jump waits for its message to be flashed (opening another chat, loading history around it). */
  private static final long JUMP_TIMEOUT_MS = 15000L;
  /** In view, the highlight waits for the list to come to rest up to SETTLE_MAX_MS; never in view, it gives up after WAIT_MAX_MS. */
  private static final long SETTLE_MAX_MS = 2000L, WAIT_MAX_MS = 6000L, SETTLE_CHECK_MS = 50L;
  /** Expressive: the state layer of the bubble's text colour the quote shows with. */
  private static final float EXPRESSIVE_ALPHA = .18f;
  /** Frames the scroll check waits for the range to be drawn (it isn't until it has some alpha). */
  private static final int SCROLL_CHECK_FRAMES = 10;

  // The jump in progress (one at a time): set on the UI thread, taken where Telegram X flashes the message, which is
  // the history loader's thread for a message loaded only after the jump

  private static final Object LOCK = new Object();
  private static int jumpAccountId;
  private static long jumpChatId, jumpMessageId, jumpTime;
  private static @Nullable TdApi.TextQuote jumpQuote;

  /** A jump to {@code messageId} starts; {@code quote} is the part of it the reply quoted (null: none, a plain jump). */
  public static void prepareJump (Tdlib tdlib, long chatId, long messageId, @Nullable TdApi.TextQuote quote) {
    final boolean hasQuote = quote != null && quote.text != null && !StringUtils.isEmpty(quote.text.text) && chatId != 0 && messageId != 0;
    synchronized (LOCK) {
      jumpAccountId = tdlib.id();
      jumpChatId = hasQuote ? chatId : 0;
      jumpMessageId = hasQuote ? messageId : 0;
      jumpQuote = hasQuote ? quote : null;
      jumpTime = SystemClock.uptimeMillis();
    }
  }

  /** The composer's reply bar: the quote picked for the reply being written. */
  public static void prepareJump (Tdlib tdlib, long chatId, long messageId, @Nullable TdApi.InputTextQuote quote) {
    prepareJump(tdlib, chatId, messageId, quote != null ? new TdApi.TextQuote(quote.text, quote.position, true) : null);
  }

  /** Telegram X flashes {@code msg} (any thread): when the jump in progress was to it, its quote shows. */
  public static void onJump (TGMessage msg) {
    final TdApi.TextQuote quote;
    final int accountId;
    final long chatId, messageId, time;
    synchronized (LOCK) {
      quote = jumpQuote;
      accountId = jumpAccountId;
      chatId = jumpChatId;
      messageId = jumpMessageId;
      time = jumpTime;
    }
    if (quote == null) {
      return;
    }
    if (SystemClock.uptimeMillis() - time > JUMP_TIMEOUT_MS) {
      synchronized (LOCK) {
        if (jumpQuote == quote) {
          jumpQuote = null;
        }
      }
      return;
    }
    if (accountId != msg.tdlib().id() || chatId != msg.getChatId() || !msg.isDescendantOrSelf(messageId)) {
      return;
    }
    synchronized (LOCK) {
      if (jumpQuote != quote) {
        return;
      }
      jumpQuote = null;
    }
    if (UI.inUiThread()) {
      msg.xgramQuoteHighlight().show(quote, messageId);
    } else {
      UI.post(() -> msg.xgramQuoteHighlight().show(quote, messageId));
    }
  }

  /**
   * Where {@code quote} is in {@code text}: at its position when the text there is the quote, else its first
   * occurrence (trimmed; same case first, then any case). Null when it isn't there.
   */
  public static @Nullable int[] findRange (@Nullable String text, @Nullable TdApi.TextQuote quote) {
    if (StringUtils.isEmpty(text) || quote == null || quote.text == null || StringUtils.isEmpty(quote.text.text)) {
      return null;
    }
    final String quoteText = quote.text.text;
    final int position = quote.position;
    if (position >= 0 && position + quoteText.length() <= text.length() && text.regionMatches(position, quoteText, 0, quoteText.length())) {
      return new int[] {position, position + quoteText.length()};
    }
    final String trimmed = quoteText.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    int index = text.indexOf(trimmed);
    if (index == -1) {
      for (int i = 0, last = text.length() - trimmed.length(); i <= last; i++) {
        if (text.regionMatches(true, i, trimmed, 0, trimmed.length())) {
          index = i;
          break;
        }
      }
    }
    return index != -1 ? new int[] {index, index + trimmed.length()} : null;
  }

  // Per message

  private static final int STATE_NONE = 0, STATE_WAITING = 1, STATE_IN = 2, STATE_HOLD = 3, STATE_OUT = 4;

  private final TGMessage msg;
  private final FactorAnimator animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, FADE_IN_MS);
  private final Runnable fadeOut = this::fadeOut;
  private final Runnable settleCheck = this::onSettleCheck;
  private final Runnable redraw = this::onRedraw;
  private final Runnable scrollWait = this::onScrollWait;
  private final RectF bounds = new RectF();

  private int state = STATE_NONE;
  private long waitingSince;
  private boolean settleCheckPosted;

  // Scrolling a quote out of sight into view: checked once, the hold waits while the list moves there
  private boolean scrollChecked;
  private int scrollCheckFrames;
  private @Nullable RecyclerView scrollList;
  private long scrollSince;

  private @Nullable TdApi.TextQuote quote;
  private long quoteMessageId;
  private @Nullable TextWrapper wrapper;
  private int rangeStart, rangeEnd;

  public XgramQuoteHighlight (TGMessage msg) {
    this.msg = msg;
  }

  /** Shows {@code quote} in the text or caption of {@code messageId} once the message is on screen; a new jump restarts it. */
  @UiThread
  public void show (TdApi.TextQuote quote, long messageId) {
    stop();
    this.quote = quote;
    this.quoteMessageId = messageId;
    this.state = STATE_WAITING;
    this.waitingSince = SystemClock.uptimeMillis();
    msg.invalidate();
  }

  /** Every frame the message is drawn, before its content. */
  @UiThread
  public void beforeDraw (View view) {
    if (state == STATE_NONE) {
      return;
    }
    if (state == STATE_WAITING) {
      final long waited = SystemClock.uptimeMillis() - waitingSince;
      if (waited > WAIT_MAX_MS) {
        // Drawn again only long after the jump (the chat was left): too late to point at anything
        stop();
        return;
      }
      final int place = findPlace(view);
      if (place == PLACE_OUT_OF_VIEW || (place == PLACE_MOVING && waited < SETTLE_MAX_MS)) {
        // Not in view yet, or the list still scrolls to it: the highlight starts once it is there at rest
        if (!settleCheckPosted) {
          settleCheckPosted = true;
          UI.post(settleCheck, SETTLE_CHECK_MS);
        }
        return;
      }
      if (!start()) {
        return;
      }
    }
    apply(view);
  }

  private void onSettleCheck () {
    settleCheckPosted = false;
    if (state == STATE_WAITING) {
      msg.invalidate();
    }
  }

  private static final int PLACE_OUT_OF_VIEW = 0, PLACE_MOVING = 1, PLACE_AT_REST = 2;

  /** Where the message's view is: in the list's view or not, and the list at rest or scrolling. */
  private static int findPlace (View view) {
    View child = view;
    ViewParent parent = view.getParent();
    for (int i = 0; i < 3 && parent instanceof View && !(parent instanceof RecyclerView); i++) {
      child = (View) parent;
      parent = parent.getParent();
    }
    if (!(parent instanceof RecyclerView)) {
      return PLACE_AT_REST; // not in a list
    }
    final RecyclerView list = (RecyclerView) parent;
    if (child.getBottom() <= 0 || child.getTop() >= list.getHeight()) {
      return PLACE_OUT_OF_VIEW;
    }
    return list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE ? PLACE_AT_REST : PLACE_MOVING;
  }

  /** Finds the quote in what the message shows now and fades it in; false when it isn't there (only the flash runs). */
  private boolean start () {
    final TextWrapper target = quote != null ? msg.xgramQuotedTextWrapper(quoteMessageId) : null;
    final int[] range = target != null ? findRange(target.getText(), quote) : null;
    if (range == null) {
      stop();
      return false;
    }
    setWrapper(target);
    rangeStart = range[0];
    rangeEnd = range[1];
    scrollChecked = false;
    scrollCheckFrames = 0;
    if (XgramMotion.reduce()) {
      // Reduced motion: shown at full, then removed, without fading
      state = STATE_HOLD;
      animator.forceFactor(1f);
      UI.cancel(fadeOut);
      UI.post(fadeOut, FADE_IN_MS + HOLD_MS);
    } else {
      state = STATE_IN;
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.setDuration(FADE_IN_MS);
      animator.animateTo(1f);
    }
    return true;
  }

  /** Passes the range and its colour at the current alpha to the text drawn now (found again if it was replaced). */
  private void apply (View view) {
    final TextWrapper target = msg.xgramQuotedTextWrapper(quoteMessageId);
    if (target != wrapper) {
      // Edited, translated or laid out anew since: the quote is looked up again in what is shown now
      final int[] range = target != null ? findRange(target.getText(), quote) : null;
      if (range == null) {
        stop();
        return;
      }
      setWrapper(target);
      rangeStart = range[0];
      rangeEnd = range[1];
    }
    if (wrapper != null) {
      // Expressive: a state layer of the text colour, readable under the glyphs (which keep their colour) on both
      // bubbles in light and dark; tertiaryContainer, which search matches use with recoloured glyphs, would not be
      final int color = XgramExpressive.isEnabled() ? ColorUtils.alphaColor(EXPRESSIVE_ALPHA, msg.getTextColor()) : msg.getSearchHighlightColorSet().backgroundColor(false);
      wrapper.setXgramRange(rangeStart, rangeEnd, ColorUtils.alphaColor(animator.getFactor(), color));
      if (!scrollChecked && (state == STATE_IN || state == STATE_HOLD)) {
        checkScroll(view);
      }
    }
  }

  /**
   * Once the range is drawn: a quote out of sight (a message taller than the list, whose top Telegram X shows) is
   * scrolled into view, and its hold starts when the list comes to rest there.
   */
  private void checkScroll (View view) {
    if (wrapper == null || !wrapper.getXgramRangeBounds(bounds)) {
      // Not drawn with the range yet (at no alpha it isn't): looked at again on the next frame
      if (++scrollCheckFrames >= SCROLL_CHECK_FRAMES) {
        scrollChecked = true;
      } else {
        UI.post(redraw);
      }
      return;
    }
    scrollChecked = true;
    if (bounds.isEmpty()) {
      return;
    }
    float y = 0f;
    View child = view;
    ViewParent parent = view.getParent();
    for (int i = 0; i < 3 && parent instanceof View && !(parent instanceof RecyclerView); i++) {
      y += child.getY();
      child = (View) parent;
      parent = parent.getParent();
    }
    if (!(parent instanceof RecyclerView)) {
      return; // not in a list
    }
    final RecyclerView list = (RecyclerView) parent;
    if (list.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
      return; // moved by someone else (still Telegram X's jump, or a finger): left to them
    }
    y += child.getY();
    final float top = y + bounds.top, bottom = y + bounds.bottom;
    // The list's top is under the pinned message and other bars, as Telegram X's jump has it
    final MessagesManager manager = msg.manager();
    final int viewTop = list.getPaddingTop() + (manager != null && manager.controller() != null ? manager.controller().getTopOffset() : 0);
    final int viewBottom = list.getHeight() - list.getPaddingBottom();
    final int visible = viewBottom - viewTop;
    if (visible <= 0) {
      return;
    }
    final float dy;
    if (bottom - top <= visible) {
      // Fits: centred, when any of it is out of sight
      dy = top >= viewTop && bottom <= viewBottom ? 0f : (top + bottom) / 2f - (viewTop + viewBottom) / 2f;
    } else {
      // Taller than the list: its start near the top, unless it is there already
      dy = top >= viewTop && top <= viewTop + visible / 2f ? 0f : top - viewTop - Screen.dp(16f);
    }
    final int scrollBy = Math.round(dy);
    if (scrollBy == 0) {
      return;
    }
    UI.cancel(fadeOut); // the hold starts once the list is at rest there
    scrollList = list;
    scrollSince = SystemClock.uptimeMillis();
    if (XgramMotion.reduce()) {
      list.scrollBy(0, scrollBy);
    } else {
      XgramMotion.smoothScrollByOr(list, 0, scrollBy);
    }
    UI.post(scrollWait, SETTLE_CHECK_MS);
  }

  private void onRedraw () {
    if (state == STATE_IN || state == STATE_HOLD) {
      msg.invalidate();
    }
  }

  private void onScrollWait () {
    final RecyclerView list = scrollList;
    if (list == null) {
      return;
    }
    if (list.getScrollState() != RecyclerView.SCROLL_STATE_IDLE && SystemClock.uptimeMillis() - scrollSince < SETTLE_MAX_MS) {
      UI.post(scrollWait, SETTLE_CHECK_MS);
      return;
    }
    scrollList = null;
    if (state == STATE_HOLD) {
      UI.cancel(fadeOut);
      UI.post(fadeOut, HOLD_MS);
    }
    // Still fading in: the hold starts when it ends
  }

  private void fadeOut () {
    if (state != STATE_HOLD) {
      return;
    }
    state = STATE_OUT;
    if (XgramMotion.reduce()) {
      animator.forceFactor(0f);
    } else {
      animator.setInterpolator(AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR);
      animator.setDuration(FADE_OUT_MS);
      animator.animateTo(0f);
    }
  }

  private void setWrapper (@Nullable TextWrapper wrapper) {
    if (this.wrapper != wrapper) {
      if (this.wrapper != null) {
        this.wrapper.setXgramRange(-1, -1, 0);
      }
      this.wrapper = wrapper;
    }
  }

  private void stop () {
    state = STATE_NONE;
    quote = null;
    UI.cancel(fadeOut);
    UI.cancel(scrollWait);
    scrollList = null;
    animator.forceFactor(0f); // ignored by onFactorChangeFinished: the state is already none
    setWrapper(null);
    msg.invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    msg.invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f && state == STATE_IN) {
      state = STATE_HOLD;
      UI.cancel(fadeOut);
      if (scrollList == null) {
        UI.post(fadeOut, HOLD_MS);
      } // else posted once the list is at rest (onScrollWait)
    } else if (finalFactor == 0f && state == STATE_OUT) {
      stop();
    }
  }
}
