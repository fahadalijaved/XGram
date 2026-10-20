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
 * File created on 25/09/2015 at 14:40
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.CustomTouchHelper;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.chat.MessagesTouchHelperCallback;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

public class MessagesRecyclerView extends RecyclerView implements FactorAnimator.Target {
  public static final long ITEM_ANIMATOR_DURATION = Config.DEBUG_REACTIONS_ANIMATIONS ? 1400l : 140L;

  private MessagesManager manager;
  private CustomTouchHelper touchHelper;
  private MessagesTouchHelperCallback callback;

  public MessagesRecyclerView (Context context) {
    super(context);
    init();
  }

  public MessagesRecyclerView (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public MessagesRecyclerView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  private CustomItemAnimator itemAnimator;

  private boolean animatorEnabled;

  public void setMessageAnimatorEnabled (boolean isEnabled) {
    if (this.animatorEnabled != isEnabled) {
      this.animatorEnabled = isEnabled;
      // setItemAnimator(isEnabled ? itemAnimator : null);
    }
  }

  // XGram: with Expressive design a sent, arrived or deleted message animates once on springs (the new bubble grows
  // from its tail while older messages slide up), then the list runs without an item animator again, as in Telegram X,
  // so history loads, edits and bubble merges never animate.

  private @Nullable CustomItemAnimator xgramArmed;
  private boolean xgramArmWaiting;
  private @Nullable android.view.ViewTreeObserver xgramArmObserver; // the one xgramArmCheck is registered on
  private final android.view.ViewTreeObserver.OnPreDrawListener xgramArmCheck = this::xgramCheckArmed;
  private final Runnable xgramDisarmNow = this::xgramDisarmIfIdle;
  // Never swapped from inside the animator's own callback: that can come from ItemAnimator.endAnimation in the middle
  // of a layout pass, where RecyclerView keeps using the animator (NPE) and moves recorded on it would never run.
  private final ItemAnimator.ItemAnimatorFinishedListener xgramDisarm = () -> {
    removeCallbacks(xgramDisarmNow);
    post(xgramDisarmNow);
  };

  private void xgramDisarmIfIdle () {
    if (xgramArmWaiting || xgramArmed == null || getItemAnimator() != xgramArmed) {
      return;
    }
    if (xgramArmed.isRunning()) {
      xgramArmed.isRunning(xgramDisarm); // something new started in between: wait for it too
    } else {
      setItemAnimator(null);
    }
  }

  /** Call right before the adapter inserts or removes one live message. */
  public void xgramArmOnce () {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || !UI.inUiThread() || getWindowToken() == null || getWidth() == 0) {
      return;
    }
    ItemAnimator current = getItemAnimator();
    if (current != null && current != xgramArmed) {
      return; // the list already has an animator of its own (event log)
    }
    removeCallbacks(xgramDisarmNow);
    if (xgramArmed == null) {
      xgramArmed = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, ITEM_ANIMATOR_DURATION) {
        @Override
        public boolean animateAdd (RecyclerView.ViewHolder holder) {
          if (xgramTakesWaitingPlace(holder.itemView)) {
            // Undo send: a sent message where its faded bubble was (XgramUndoSendBubbles) is already in place
            holder.itemView.setAlpha(1f);
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            dispatchAddFinished(holder);
            return false;
          }
          xgramTailPivot(holder.itemView);
          return super.animateAdd(holder);
        }

        @Override
        public void onAddFinished (RecyclerView.ViewHolder item) {
          super.onAddFinished(item); // a no-op today: ItemSprings resets alpha and scale before it dispatches
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            item.itemView.resetPivot();
          }
        }
      };
      xgramArmed.setSupportsChangeAnimations(false);
    }
    if (getItemAnimator() != xgramArmed) {
      setItemAnimator(xgramArmed);
    }
    if (!xgramArmWaiting) {
      xgramArmWaiting = true;
      xgramArmObserver = getViewTreeObserver();
      xgramArmObserver.addOnPreDrawListener(xgramArmCheck);
    }
  }

  /** Takes xgramArmCheck off the observer it was added to (not whatever getViewTreeObserver returns by then). */
  private void xgramRemoveArmCheck () {
    android.view.ViewTreeObserver observer = xgramArmObserver;
    xgramArmObserver = null;
    xgramArmWaiting = false;
    if (observer != null && observer.isAlive()) {
      observer.removeOnPreDrawListener(xgramArmCheck); // xgramArmOnce only arms while attached: the window's observer
    }
  }

  private boolean xgramCheckArmed () {
    if (isLayoutRequested() || hasPendingAdapterUpdates()) {
      return true; // the pass that records the animations has not run yet
    }
    xgramRemoveArmCheck();
    if (xgramArmed != null && getItemAnimator() == xgramArmed) {
      xgramArmed.isRunning(xgramDisarm);
    }
    return true;
  }

  /** XGram: the message took the place of an Undo send faded bubble (once: a later add of it animates as always). */
  private static boolean xgramTakesWaitingPlace (View view) {
    org.thunderdog.challegram.component.chat.MessageView messageView = org.thunderdog.challegram.component.chat.MessagesHolder.findMessageView(view);
    TGMessage msg = messageView != null ? messageView.getMessage() : null;
    if (msg != null && msg.xgramFromWaiting) {
      msg.xgramFromWaiting = false;
      return true;
    }
    return false;
  }

  /** XGram: how far pull-to-next draws the list's content up (draw); Undo send's faded bubbles follow it. */
  public float xgramContentOffset () {
    return xgramPull != null ? xgramPull.getOffset() : 0f;
  }

  /** The new bubble grows from its tail: the bottom corner on its own side. */
  private static void xgramTailPivot (View view) {
    org.thunderdog.challegram.component.chat.MessageView messageView = org.thunderdog.challegram.component.chat.MessagesHolder.findMessageView(view);
    TGMessage msg = messageView != null ? messageView.getMessage() : null;
    boolean rtl = org.thunderdog.challegram.core.Lang.rtl();
    boolean right = msg != null && msg.useBubbles() ? msg.isOutgoingBubble() != rtl : rtl;
    int w = view.getWidth();
    view.setPivotX(right ? w - Screen.dp(16f) : Screen.dp(16f));
    view.setPivotY(view.getHeight());
  }

  private int prevWidth, prevHeight;

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    if (manager != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      if (prevWidth != width || prevHeight != height) {
        prevWidth = width;
        prevHeight = height;
        manager.controller().onMessagesFrameChanged();
      } else {
        manager.onViewportMeasure();
      }
    }
  }

  private MessagesItemDecoration itemDecoration;

  private void init () {
    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, ITEM_ANIMATOR_DURATION);
    itemAnimator.setSupportsChangeAnimations(false);
    setItemAnimator(null);
    callback = new MessagesTouchHelperCallback();
    touchHelper = new CustomTouchHelper(callback);
    callback.setTouchHelper(touchHelper);
    touchHelper.attachToRecyclerView(this);
    addItemDecoration(itemDecoration = new MessagesItemDecoration());
  }

  public MessagesTouchHelperCallback getMessagesTouchHelper () {
    return callback;
  }

  public void setManager (MessagesManager manager) {
    this.manager = manager;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && (xgramScroll = com.fahadali.xgram.appearance.XgramScrollIndicator.find(this)) != null) {
      // XGram: history is paginated, so a fixed-length thumb that shows and hides with the date pill,
      // clear of the 28dp corners of the messages container. Never grabbed: the chat's end edge belongs to the
      // system edge gestures (back swipe, Samsung's Edge panel), and to swipe-to-reply.
      xgramScroll.setFixedThumbLength(Screen.dp(40f)).setManualVisibility(true).setTopInset(Screen.dp(20f)).setCanDrag(false);
      xgramScrollTopInset = -1; // below the bars over the list from the first draw on (xgramUpdateScrollTopInset)
    }
  }

  public void setController (MessagesController controller) {
    callback.setController(controller);
  }

  public void startSwipe (View view) {
    if (view == null || !xgramInGesture) {
      return; // XGram: no finger on the list any more (see xgramStartSwipe)
    }
    RecyclerView.ViewHolder holder;
    try {
      holder = getChildViewHolder(view);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (holder != null && holder.getItemViewType() != ListItem.TYPE_CHAT_EMPTY) {
      touchHelper.startSwipe(holder);
    }
  }

  public MessagesManager getManager () {
    return manager;
  }

  // XGram: the touch on the list, from its ACTION_DOWN to its ACTION_UP or ACTION_CANCEL (dispatchTouchEvent).
  private int xgramGestureId;
  private boolean xgramInGesture;

  /** XGram: the touch now on the list. It changes with every new touch. */
  public int xgramGestureId () {
    return xgramGestureId;
  }

  /**
   * XGram: {@link #startSwipe}, only while the touch {@code gestureId} is still on the list. MessageView asks TDLib
   * (the reactions) and ML Kit (the text's language) before a swipe starts, and their answers can come after the
   * finger has lifted: a swipe started then was selected with no touch to end it, so the list's next touch went to
   * that message (the touch helper took its ACTION_DOWN) instead of where the finger was.
   */
  public void xgramStartSwipe (View view, int gestureId) {
    if (xgramInGesture && gestureId == xgramGestureId) {
      startSwipe(view);
    }
  }

  private boolean ignore;

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (!ignore) {
      ignore = true;
      manager.rebuildFirstItem(true, changed);
      ignore = false;
    }
    if (manager != null) {
      manager.controller().onMessagesFrameChanged();
    }
  }

  /**
   * Whether a child asked the list not to intercept the current touch (MessageView then starts no swipe). XGram: kept
   * for the current touch only, cleared on every ACTION_DOWN and when the touch ends (dispatchTouchEvent), as
   * ViewGroup clears its own flag. It used to change only when someone called requestDisallowInterceptTouchEvent, so
   * one "true" whose "false" never came stopped every swipe in this list for good: the in-chat video controls, a long
   * pressed message the list let go of before the finger lifted, a "false" that a parent view group (MessageViewGroup)
   * swallowed because it had already cleared its own flag. Telegram X reuses this list for every chat it opens, so
   * swipe to reply stayed off in all chats until the activity or the app was restarted.
   */
  private boolean disallowIntercept;

  @Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    this.disallowIntercept = disallowIntercept;
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }

  public boolean disallowInterceptTouchEvent () {
    return disallowIntercept;
  }

  public void invalidateAll () {
    final LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
      int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
      for (int i = first; i <= last; i++) {
        View v = manager.findViewByPosition(i);
        if (v != null) {
          v.invalidate();
        }
      }
    } else {
      for (int i = 0; i < getChildCount(); i++) {
        View v = getChildAt(i);
        if (v != null) {
          v.invalidate();
        }
      }
    }
  }

  private boolean isScrolling;
  private float scrollFactor;
  private FactorAnimator scrollAnimator;

  private boolean startAnimatorOnAnimationEnd;

  // XGram: with Expressive design the date pill fades on springs (in fast, out slow) and the scroll thumb follows it
  private @Nullable com.fahadali.xgram.appearance.XgramScrollIndicator xgramScroll;
  private @Nullable com.fahadali.xgram.appearance.XgramScrollIndicator.Anim xgramDateSpring;
  private final Runnable xgramDateHide = () -> {
    if (!this.isScrolling && xgramDateSpring != null) {
      xgramDateSpring.animateTo(0f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_SLOW);
      if (xgramScroll != null) {
        xgramScroll.setShown(false);
      }
    }
  };

  private void xgramSetIsScrolling (boolean isScrolling) {
    if (xgramDateSpring == null) {
      xgramDateSpring = new com.fahadali.xgram.appearance.XgramScrollIndicator.Anim(scrollFactor, .002f, () -> {
        float factor = MathUtils.clamp(xgramDateSpring.getValue());
        if (this.scrollFactor != factor) {
          this.scrollFactor = factor;
          invalidateDate();
        }
      });
    }
    removeCallbacks(xgramDateHide);
    if (isScrolling) {
      xgramDateSpring.animateTo(1f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST);
      if (xgramScroll != null) {
        xgramScroll.setShown(true);
      }
    } else {
      postDelayed(xgramDateHide, 1500);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow(); // ends running item animations and stops the scroll first
    // XGram: nothing armed, pending or springing outlives the window (callbacks and the pre-draw listener hold the list)
    if (xgramArmWaiting) {
      xgramRemoveArmCheck();
    }
    removeCallbacks(xgramDisarmNow);
    if (xgramArmed != null && getItemAnimator() == xgramArmed) {
      setItemAnimator(null);
    }
    if (xgramDateSpring != null) {
      removeCallbacks(xgramDateHide);
      float factor = isScrolling ? 1f : 0f;
      xgramDateSpring.setValue(factor);
      scrollFactor = factor;
    }
    // XGram: a screen opened over the chat, or the activity is recreated (a theme, Expressive design): the touch on
    // the list is over, and a swipe still in progress puts its message back instead of waiting for a finger or a
    // frame that never come.
    xgramInGesture = false;
    disallowIntercept = false;
    touchHelper.xgramDropGesture();
  }

  public void setIsScrolling (boolean isScrolling) {
    if (this.isScrolling != isScrolling) {
      this.isScrolling = isScrolling;
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        xgramSetIsScrolling(isScrolling);
        return;
      }
      if (scrollAnimator == null) {
        scrollAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.scrollFactor);
      }
      if (scrollAnimator.isAnimating() && !isScrolling) {
        startAnimatorOnAnimationEnd = true;
        return;
      }
      startAnimatorOnAnimationEnd = false;
      scrollAnimator.setStartDelay(isScrolling ? 0 : 1500);
      scrollAnimator.setDuration(isScrolling ? 120l : 180l);
      scrollAnimator.animateTo(isScrolling ? 1f : 0f);
    }
  }

  public final void invalidateDate () {
    int topOffset = getTopOffset();
    invalidate(topOffset, 0, getMeasuredWidth(), topOffset + TGMessage.getDateHeight(manager.useBubbles()));
  }

  public void showDateForcely () {
    if (!isScrolling && false) {
      isScrolling = true;
      if (scrollFactor != 0f) {
        scrollFactor = 1f;
        if (scrollAnimator != null) {
          scrollAnimator.forceFactor(1f);
        }
      }
      invalidateDate();
      setIsScrolling(false);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.scrollFactor != factor) {
      this.scrollFactor = factor;
      invalidateDate();
      if (factor == 1f && startAnimatorOnAnimationEnd && !isScrolling) {
        startAnimatorOnAnimationEnd = false;
        scrollAnimator.setStartDelay(1500);
        scrollAnimator.setDuration(180l);
        scrollAnimator.animateTo(0f);
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  public void setTranslationY (float translationY) {
    if (scrollFactor == 0f) {
      super.setTranslationY(translationY);
    } else if (getTranslationY() != translationY) {
      super.setTranslationY(translationY);
      invalidateDate();
    }
  }

  private int getTopOffset () {
    int topOffset = manager.getTopOverlayOffset();
    HeaderView headerView = UI.getHeaderView(getContext());
    if (headerView != null) {
      topOffset += headerView.getFilling().getPlayerOffset();
    }
    return topOffset;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    boolean res = super.onInterceptTouchEvent(e);
    if (scrollFactor > 0f && e.getAction() == MotionEvent.ACTION_DOWN && isInsideDate(e.getX(), e.getY())) {
      return true;
    }
    return res;
  }

  // XGram: pull up for the next unread channel / topic
  @Nullable private com.fahadali.xgram.chats.XgramPullToNext xgramPull;

  public void xgramSetPullToNext (@Nullable com.fahadali.xgram.chats.XgramPullToNext pull) {
    this.xgramPull = pull;
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    // XGram: one touch at a time (xgramStartSwipe), and nothing a child asked during an earlier touch stays
    final int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      xgramGestureId++;
      xgramInGesture = true;
      disallowIntercept = false;
    }
    final boolean handled = xgramDispatchTouchEvent(e);
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      xgramInGesture = false;
      disallowIntercept = false;
    }
    return handled;
  }

  private boolean xgramDispatchTouchEvent (MotionEvent e) {
    if (xgramPull != null && xgramPull.onTouchEvent(this, e)) {
      if (xgramPull.needsCancel()) {
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel); // stop scrolling, pressing and swipe-to-reply
        cancel.recycle();
      }
      return true;
    }
    return super.dispatchTouchEvent(e);
  }

  private int xgramScrollTopInset = -1;

  /**
   * XGram M3E: the scroll thumb's track starts 20dp below what covers the top of the list (the pinned message, join
   * requests, translate and topic bars with their backing, the part hidden while the list moves up for a reply), where
   * the date pill sits, not 20dp below the list's own top: the thumb went under the bars at the top of its travel.
   * Only an int for the next draw of the indicator, set when it changes.
   */
  private void xgramUpdateScrollTopInset () {
    if (xgramScroll != null && manager != null) {
      int inset = getTopOffset() + Screen.dp(20f);
      if (inset != xgramScrollTopInset) {
        xgramScrollTopInset = inset;
        xgramScroll.setTopInset(inset);
      }
    }
  }

  @Override
  public void draw (android.graphics.Canvas c) {
    xgramUpdateScrollTopInset(); // XGram: before the indicator (an item decoration) draws
    float offset = xgramPull != null ? xgramPull.getOffset() : 0f;
    if (offset > 0f) {
      int save = c.save();
      c.translate(0, -offset);
      super.draw(c);
      c.restoreToCount(save);
      xgramPull.draw(c, getMeasuredWidth(), getMeasuredHeight());
    } else {
      super.draw(c);
    }
  }

  private boolean listeningClick;
  private float listenClickX, listenClickY;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();
        if (listeningClick = scrollFactor > 0f && isInsideDate(x, y)) {
          listenClickX = x;
          listenClickY = y;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listeningClick && (Math.abs(listenClickX - e.getX()) > Screen.getTouchSlop() || Math.abs(listenClickY - e.getY()) > Screen.getTouchSlop())) {
          listeningClick = false;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        listeningClick = false;
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (listeningClick) {
          if (onDateClick()) {
            ViewUtils.onClick(this);
          }
          listeningClick = false;
        }
        break;
      }
    }
    return super.onTouchEvent(e);
  }

  private int jumpToBeginningOfTheDay;

  private boolean isInsideDate (float x, float y) {
    if (itemDecoration == null) {
      return false;
    }
    if (manager.controller().inPreviewMode()) {
      return false;
    }
    if (itemDecoration.lastMessage == null || itemDecoration.lastAlpha == 0f) {
      return false;
    }
    int dateHeight = Screen.dp(26f);
    final int cx = getMeasuredWidth() / 2;
    final int cy = itemDecoration.lastTop + Screen.dp(manager.useBubbles() ? 5f : 8f) + dateHeight / 2; // + dateHeight / 2;
    int dateWidth = itemDecoration.lastMessage.getDateWidth();
    int padding = itemDecoration.lastMessage.getDatePadding();
    int bound = Screen.dp(4f);
    jumpToBeginningOfTheDay = itemDecoration.lastMessage.getDate();
    int boundHorizontal = dateWidth / 2 + padding + bound;
    int boundVertical = dateHeight / 2 + bound;

    return x >= cx - boundHorizontal && x < cx + boundHorizontal && y >= cy - boundVertical && y < cy + boundVertical;

    // return x >= cx - padding - dateWidth / 2 - bound && x < cx + padding + dateWidth / 2 + bound && y >= cy - padding - dateHeight / 2 - bound && y < cy + padding + dateHeight / 2 + bound;
  }

  private boolean onDateClick () {
    if (jumpToBeginningOfTheDay != 0) {
      manager.controller().jumpToBeginningOfTheDay(jumpToBeginningOfTheDay);
      return true;
    }
    return false;
  }

  private class MessagesItemDecoration extends ItemDecoration {
    protected TGMessage lastMessage;
    protected int lastTop;
    protected float lastAlpha;

    @Override
    public void onDrawOver (Canvas c, RecyclerView parent, State state) {
      this.lastMessage = null;
      this.lastTop = 0;
      this.lastAlpha = 0f;

      MessagesAdapter adapter = (MessagesAdapter) parent.getAdapter();
      if (adapter.getMessageCount() == 0) {
        return;
      }

      final int centerX = parent.getMeasuredWidth() / 2;

      // int lastTop = 0;
      boolean hasDrawn = false;

      int dateHeight = TGMessage.getDateHeight(manager.useBubbles());
      int topOffset = getTopOffset();
      // TGMessage lastMessage = null;

      int lastIndex = parent.getChildCount() - 1;
      while (lastIndex >= 0) {
        View lastView = parent.getChildAt(lastIndex);
        if (lastView.getTop() + lastView.getMeasuredHeight() > topOffset) {
          break;
        }
        lastIndex--;
      }

      // this will iterate over every visible view
      for (int i = 0; i <= lastIndex; i++) {
        // get the view
        final View view = parent.getChildAt(i);
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();

        // get the position
        final int position = params.getViewAdapterPosition();

        // and finally draw the separator
        if (position < state.getItemCount()) {
          final TGMessage msg = adapter.getMessage(position);
          if (msg != null) {
            int viewTop = view.getTop();
            if (msg.hasDate()) {
              viewTop += msg.getDrawDateY();
            }
            int top = viewTop;
            if (top < topOffset) {
              if (hasDrawn) {
                int maxY = lastTop - dateHeight;
                if (lastMessage != null) {
                  maxY -= lastMessage.getDrawDateY();
                }
                top = Math.min(maxY, topOffset);
              } else {
                top = topOffset;
              }
            }
            boolean isLast = i == lastIndex;
            int checkTop = viewTop - topOffset;
            float detachFactor = !isLast || checkTop >= 0 ? 0f : MathUtils.clamp((float) -checkTop / (float) dateHeight);
            float alpha = isLast ? scrollFactor : 1f;
            float drawAlpha = detachFactor == 1f ? alpha : 1f;
            if (msg.drawDate(c, centerX, top, detachFactor, drawAlpha)) {
              hasDrawn = true;
              lastTop = top;
              lastMessage = msg;
              lastAlpha = drawAlpha;
              continue;
            }
            if (isLast) {
              Object tag = msg.getTag();
              if (tag != null) {
                TGMessage tagMsg = (TGMessage) tag;
                if (!tagMsg.isDestroyed() && tagMsg.drawDate(c, centerX, top, 1f, alpha)) {
                  hasDrawn = true;
                  lastTop = top;
                  lastMessage = tagMsg;
                  lastAlpha = alpha;
                  continue;
                }
                msg.setTag(null);
              }
              for (int j = position + 1; j < adapter.getMessageCount(); j++) {
                TGMessage olderMessage = adapter.getMessage(j);
                if (olderMessage != null && olderMessage.drawDate(c, centerX, top, 1f, alpha)) {
                  msg.setTag(olderMessage);
                  hasDrawn = true;
                  lastTop = top;
                  lastMessage = msg;
                  lastAlpha = alpha;
                  break;
                }
              }
            }
          }
        }
      }
    }
  }
}
