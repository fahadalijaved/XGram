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
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMotion;
import com.fahadali.xgram.appearance.XgramR5Chat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;

/**
 * Undo send: every waiting message also shows in the chat, under the newest message and in the order they were sent,
 * as Telegram X's own bubble for it (text with its entities and custom emoji, the reply, files) at half opacity, with a
 * small countdown ring where the sending clock goes. A tap offers Undo and Send now.
 *
 * <p>The bubbles are made from TDLib's preview of the very request that waits ({@link XgramUndoSend}: the same
 * SendMessage with only_preview, which TDLib answers without sending or keeping anything). They are not items of the
 * message list: this item decoration keeps their room under the list's newest message, and a layer under the list
 * (the list's bounds, no touches) draws them there, following the list on every frame. So MessagesAdapter,
 * MessagesManager and everything that reads them (selection, replies and swipes, read state and ghost mode, search,
 * unread counters and the scroll button, the chat list, notifications) never see them; their messages also count as
 * fake ones ({@code TGMessage#isFakeMessage}) and never get a touch.
 *
 * <p>The first bubble is merged under the list's newest message as a sent message would be (that message loses its
 * tail and its bottom padding while bubbles show). When a waiting message is sent, the real (sending) message is added
 * to the list where its bubble was, in the same frame the bubble goes ({@link #beforeNewMessage}): it has the same
 * height, so nothing moves, nothing is shown twice, and the list adds it without its add animation. An album comes in
 * one message at a time: its bubble keeps the room its messages don't take yet until all of them are in. When a bubble
 * comes or goes otherwise (it starts waiting, Undo, a refused send), the room first keeps the list and the other
 * bubbles where they were on screen, then grows or shrinks to the bubbles' heights: the list lays out again on every
 * frame (it never moves out of its own bounds), on the move spring with Expressive design, 200 ms DECELERATE otherwise,
 * as the Undo send bar moves; a new bubble rises from behind the message box and fades in, one that leaves fades where
 * it was. With reduce motion only the fades are left.
 *
 * <p>A round video message has no bubble (Telegram X plays it in a view group of the list): only the bar shows it.
 */
public final class XgramUndoSendBubbles extends RecyclerView.ItemDecoration implements FactorAnimator.Target, RecyclerView.OnItemTouchListener {
  private static final float BUBBLE_ALPHA = .5f;
  private static final long FADE_MS = 150L;
  private static final int ANIMATOR_SETTLE = 0;

  private final MessagesController owner;
  private final MessagesManager manager;
  private final MessagesRecyclerView list;
  private final Layer layer;
  /** The bubbles, oldest first (by send order, part, and place in the part). */
  private final ArrayList<Slot> slots = new ArrayList<>();
  /** Bubbles that left without a message in their place: they fade where they were. */
  private final ArrayList<Ghost> ghosts = new ArrayList<>();
  /** Parts that had their bubbles made (or were sent before): none are made for them again. */
  private final HashSet<Long> handled = new HashSet<>();
  /** The list and the bubbles that stay keep their place on screen, then settle: 1 to 0. */
  private final FactorAnimator settle;
  /**
   * How much less room than {@link #total} the list gets (px, at the settle factor 1, negative for more), so that it
   * keeps its place on screen. The list is never moved: the room itself settles. A message that takes a bubble's place
   * leaves it as it is, so the content above stays where it was then too.
   */
  private float shiftStart;
  /** The bubbles' heights: the room under the list's newest message once settled. */
  private int total;
  private int builtWidth;
  /** The list's newest message the first bubble is merged under (it isn't the list's bottom message meanwhile). */
  @Nullable private TGMessage mergedTop;
  /** What the layer clips to, in its coordinates: the list's bounds (inside its padding when it clips to it). */
  private final RectF clip = new RectF();
  private final int slack = Screen.dp(8f);
  /** A sync waits for the list's pending adapter changes to be laid out first ({@link #sync}). */
  private boolean syncPosted;
  private final Runnable syncLater = () -> {
    syncPosted = false;
    syncNow();
  };

  public XgramUndoSendBubbles (Context context, MessagesController owner, MessagesManager manager, MessagesRecyclerView list) {
    this.owner = owner;
    this.manager = manager;
    this.list = list;
    this.layer = new Layer(context);
    this.layer.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    this.settle = new FactorAnimator(ANIMATOR_SETTLE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L, 0f);
    list.addItemDecoration(this);
    list.addOnItemTouchListener(this);
  }

  /** The layer that draws the bubbles: under the list, over the wallpaper, as big as the chat's content view. */
  public View getLayer () {
    return layer;
  }

  private static long keyOf (XgramUndoSendItem item, int part) {
    return (item.order << 12) | (part & 0xfff);
  }

  /** The list's newest message, when the list shows the chat's newest messages (the bubbles go under it). */
  @Nullable
  private TGMessage activeTop () {
    if (!manager.xgramShowsNewest()) {
      return null;
    }
    TGMessage top = manager.getAdapter().getBottomMessage();
    return top != null && !top.isDestroyed() && !top.isSponsoredMessage() && !(top instanceof TGMessageBotInfo) ? top : null;
  }

  // What waits

  /** What waits in the owner's message box changed: bubbles come for new waiting messages, leave for the others. */
  @UiThread
  public void sync () {
    if (list.hasPendingAdapterUpdates() && list.getWindowToken() != null) {
      // Never in the layout pass of an adapter change (a sent message joining the list, which may arm the list's
      // one-shot animator): that change is laid out and animated first, then the bubbles change from there. Once only:
      // the posted sync runs whatever the list has pending by then.
      if (!syncPosted) {
        syncPosted = true;
        list.post(syncLater);
      }
      return;
    }
    syncNow();
  }

  private void syncNow () {
    final List<XgramUndoSend.BubblePart> parts = XgramUndoSend.bubblePartsOf(owner);
    if (parts.isEmpty() && slots.isEmpty()) {
      return;
    }
    // Bubbles a message took the place of leave first, on their own: the list doesn't move for them
    for (Slot slot : slots) {
      if (slot.handedOff) {
        afterNewMessage();
        break;
      }
    }
    final HashMap<Long, XgramUndoSend.BubblePart> present = new HashMap<>();
    List<XgramUndoSend.BubblePart> added = null;
    for (XgramUndoSend.BubblePart part : parts) {
      final long key = keyOf(part.item, part.part);
      present.put(key, part);
      if (!handled.contains(key)) {
        handled.add(key);
        if (!part.sent) {
          if (added == null) {
            added = new ArrayList<>();
          }
          added.add(part);
        }
      }
    }
    boolean removes = false;
    for (Slot slot : slots) {
      XgramUndoSend.BubblePart part = present.get(slot.key);
      if (part == null) {
        removes = true;
      } else {
        slot.countdown.setSending(part.sending);
      }
    }
    if (added == null && !removes) {
      return;
    }
    final List<XgramUndoSend.BubblePart> add = added;
    final boolean[] fresh = new boolean[1];
    change(() -> {
      for (int i = slots.size() - 1; i >= 0; i--) {
        Slot slot = slots.get(i);
        if (!present.containsKey(slot.key)) {
          removeSlot(slot, true);
        }
      }
      if (add != null) {
        for (XgramUndoSend.BubblePart part : add) {
          fresh[0] |= addSlots(part);
        }
      }
    }, true);
    if (fresh[0] && activeTop() != null && manager.getLayoutManager().findViewByPosition(0) == null) {
      manager.xgramScrollToNewest(); // as a send does: the list goes to the newest message, where the bubble is
    }
  }

  /** The bubbles of one part (an album may make several); false when none could be made. */
  private boolean addSlots (XgramUndoSend.BubblePart part) {
    final List<TGMessage> parsed;
    try {
      parsed = manager.parseMessages(Arrays.asList(part.messages));
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot make the bubble", t);
      return false;
    }
    final long key = keyOf(part.item, part.part);
    boolean any = false;
    int index = 0;
    // The preview messages each bubble shows, in order: an album may be one bubble (Telegram X combines it)
    int first = 0;
    for (TGMessage msg : parsed) {
      if (msg == null) {
        continue;
      }
      final int firstIndex = first;
      first += Math.max(1, msg.getCombinedMessageCount());
      if (msg.needViewGroup()) {
        // A round video message: Telegram X draws its background, time and overlay from a view group of the list,
        // a plain message view would show it without them. It has no bubble: the bar shows it (as the settings say).
        msg.onDestroy();
        continue;
      }
      try {
        Slot slot = new Slot(part, key, index++, firstIndex, msg);
        int at = 0;
        while (at < slots.size() && slots.get(at).compareTo(slot) < 0) {
          at++;
        }
        slots.add(at, slot);
        any = true;
      } catch (Throwable t) {
        android.util.Log.e("XGram", "Undo send: cannot show the bubble", t);
        msg.onDestroy();
      }
    }
    return any;
  }

  private void removeSlot (Slot slot, boolean fadeOut) {
    slots.remove(slot);
    slot.fade.cancel();
    if (fadeOut && slot.placed && slot.view.getVisibility() == View.VISIBLE && canFade()) {
      // In the list before its fade starts: with animations off (system setting) the fade ends inside start()
      final Ghost ghost = new Ghost(slot);
      ghosts.add(ghost);
      ghost.start();
    } else {
      destroyView(slot.view);
    }
  }

  private void destroyView (MessageView view) {
    view.onDetachedFromRecyclerView();
    layer.removeView(view);
    owner.removeThemeListenerByTarget(view);
    view.performDestroy();
  }

  /** The chat closes or shows another chat: everything goes at once (the list is cleared too). */
  @UiThread
  public void reset () {
    if (syncPosted) {
      syncPosted = false;
      list.removeCallbacks(syncLater);
    }
    settle.forceFactor(0f);
    shiftStart = 0f;
    for (Slot slot : slots) {
      slot.fade.cancel();
      destroyView(slot.view);
    }
    slots.clear();
    for (Ghost ghost : ghosts) {
      ghost.fade.cancel();
      destroyView(ghost.view);
    }
    ghosts.clear();
    handled.clear();
    total = 0;
    mergedTop = null;
    invalidateDecorations();
  }

  // The list's newest message

  /**
   * MessagesManager, right before a live message joins the bottom of the list: when it is the one a bubble showed
   * (the first of its messages, for a combined album), that bubble gives it its place (it leaves in
   * {@link #afterNewMessage}, in the same frame).
   */
  @UiThread
  public void beforeNewMessage (TGMessage message) {
    if (slots.isEmpty()) {
      return;
    }
    final long id = message.getId();
    if (id == 0) {
      return;
    }
    for (Slot slot : slots) {
      if (!slot.handedOff && XgramUndoSend.sentIdAt(slot.item, slot.part, slot.firstIndex) == id) {
        slot.handedOff = true;
        slot.item.bubbleTookPlace = true; // the bar leaves on its own exit: the list doesn't grow for this message
        message.xgramFromWaiting = true; // the list adds it without the add animation: it is already there
        if (slot.msg.getCombinedMessageCount() > 1) {
          // An album: its messages come one by one (the others combine into this one). Its bubble keeps the room
          // they don't take yet, so nothing above moves while they come.
          slot.taker = message;
          slot.fullHeight = slot.height;
        }
        return;
      }
    }
  }

  /**
   * MessagesManager, after a live message joined the bottom of the list (or combined into it), or the list's newest
   * messages were deleted or replaced: the bubbles a message took the place of leave (an album's once all of it is
   * in), the others go under the newest message. Nothing is compensated: a message is where its bubble was.
   */
  @UiThread
  public void afterNewMessage () {
    if (slots.isEmpty()) {
      return;
    }
    final TGMessage top = activeTop();
    change(() -> {
      for (int i = slots.size() - 1; i >= 0; i--) {
        Slot slot = slots.get(i);
        if (slot.handedOff && !slot.waitsForRest(top)) {
          removeSlot(slot, false); // its message is in the list now
        }
      }
    }, false);
  }

  /** Some bubble is drawn (merged under the list's newest message), not only an album's room. */
  private boolean drawsBubbles () {
    for (Slot slot : slots) {
      if (slot.taker == null) {
        return true;
      }
    }
    return false;
  }

  /**
   * The room a bubble keeps: its message's height. An album its first message took the place of keeps what that message
   * doesn't take yet (none once that message isn't the list's newest any more).
   */
  private static int heightOf (Slot slot, @Nullable TGMessage top) {
    if (slot.taker != null) {
      return slot.taker == top ? Math.max(0, slot.fullHeight - top.getHeight()) : 0;
    }
    return slot.msg.getHeight();
  }

  /**
   * One change of the bubbles. {@code compensate}: the list and the bubbles that stay keep their place on screen (the
   * room under the newest message and the newest message's own height change in this frame, so the room starts at
   * what keeps the list's content as tall as it was), then the room settles to the bubbles' heights. Without it (a
   * message took a bubble's place, the newest message changed, a new width) the list lays out as it is.
   */
  private void change (@Nullable Runnable mutation, boolean compensate) {
    final TGMessage top = activeTop();
    final View topView = top != null ? manager.getLayoutManager().findViewByPosition(0) : null;
    final float factor = settle.getFactor();
    final int contentBefore = total + (top != null ? top.getHeight() : 0);
    int below = 0;
    for (int i = slots.size() - 1; i >= 0; i--) {
      Slot slot = slots.get(i);
      below += slot.height;
      slot.sBefore = below;
      slot.carryNow = slot.carryStart * factor;
      slot.fresh = false;
    }
    if (mutation != null) {
      mutation.run();
    }
    remerge(top);
    if (compensate) {
      if (topView != null && canMove()) {
        final int delta = total + (top != null ? top.getHeight() : 0) - contentBefore;
        int after = 0, oldBelow = 0;
        for (int i = slots.size() - 1; i >= 0; i--) {
          Slot slot = slots.get(i);
          after += slot.height;
          if (slot.fresh) {
            // From where its room opens: right below the bubbles that were there (behind the message box at the end)
            slot.carryStart = -delta + (after - oldBelow);
          } else {
            slot.carryStart = slot.carryNow - delta + (after - slot.sBefore);
            oldBelow = slot.sBefore;
          }
        }
        shiftStart = shiftStart * factor + delta;
        if (XgramExpressive.isEnabled()) {
          settle.setInterpolator(XgramAppMotion.springClamped(XgramExpressive.SPATIAL_DEFAULT));
          settle.setDuration(XgramAppMotion.springDuration(XgramExpressive.SPATIAL_DEFAULT));
        } else {
          settle.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          settle.setDuration(200L);
        }
        settle.forceFactor(1f);
        settle.animateTo(0f);
      } else {
        shiftStart = 0f;
        for (Slot slot : slots) {
          slot.carryStart = 0f;
        }
        settle.forceFactor(0f);
      }
      for (Slot slot : slots) {
        if (slot.fresh) {
          slot.fadeIn(canFade() && topView != null);
        }
      }
    }
    invalidateDecorations(); // the room: the list lays out again in this frame
    layer.invalidate();
  }

  /** Fades run only for the chat in front (with reduce motion too: they are all that is left). */
  private boolean canFade () {
    return owner.isFocused() && list.getWindowToken() != null && list.getWidth() > 0;
  }

  /** The list and the bubbles move to their place only with motion on. */
  private boolean canMove () {
    return canFade() && !XgramMotion.reduce();
  }

  /**
   * Merges the bubbles as the list merges its messages: the first under the list's newest message (which then isn't
   * the bottom one), each next under the one before it, the last one is the bottom. Then their heights.
   */
  private void remerge (@Nullable TGMessage top) {
    if (mergedTop != null && mergedTop != top) {
      mergedTop = null; // the list's newest changed: the list merged its messages itself
    }
    total = 0;
    if (slots.isEmpty()) {
      if (mergedTop != null) {
        restoreBottom(mergedTop);
        mergedTop = null;
      }
      return;
    }
    final int width = list.getMeasuredWidth() > 0 ? list.getMeasuredWidth() : manager.getRecyclerWidth();
    // An album coming in isn't drawn (only its room counts) and isn't merged: its first message, the list's newest,
    // stands for it (the bottom one with its tail when nothing is drawn under it)
    int lastDrawn = -1;
    for (int i = 0; i < slots.size(); i++) {
      if (slots.get(i).taker == null) {
        lastDrawn = i;
      }
    }
    TGMessage above = top;
    for (int i = 0; i < slots.size(); i++) {
      Slot slot = slots.get(i);
      if (slot.taker == null) {
        slot.msg.mergeWith(above, i == lastDrawn);
        above = slot.msg;
      }
    }
    for (Slot slot : slots) {
      if (slot.taker == null) {
        if (width > 0 && slot.msg.getWidth() != width) {
          slot.msg.buildLayout(width);
        } else {
          slot.msg.rebuildLayout();
        }
      }
      final int height = heightOf(slot, top); // (an album coming in: after its first message's merge above)
      if (slot.height != height) {
        slot.height = height;
        if (slot.taker == null) {
          slot.view.requestLayout();
        }
      }
      total += slot.height;
    }
    builtWidth = width;
    if (top != null) {
      mergedTop = top;
      notifyTopChanged(top);
    }
  }

  /** The bubbles are gone: the list's newest message is its bottom one again (its tail and bottom padding). */
  private void restoreBottom (TGMessage top) {
    if (top.isDestroyed() || top != activeTop()) {
      return;
    }
    top.mergeWith(manager.getAdapter().getMessage(1), true);
    top.rebuildLayout();
    notifyTopChanged(top);
  }

  /**
   * The newest message's height or bubble changed (merged or not): its view measures it again, as for any change of
   * a message's height. Not an adapter change: the list's item animator (armed right after a live message) mustn't
   * move what the room already keeps in place.
   */
  private void notifyTopChanged (TGMessage top) {
    top.requestLayout();
    top.invalidate();
  }

  private void invalidateDecorations () {
    if (list.isComputingLayout()) {
      list.post(this::invalidateDecorationsNow);
    } else {
      invalidateDecorationsNow();
    }
  }

  private void invalidateDecorationsNow () {
    if (!list.isComputingLayout()) {
      list.invalidateItemDecorations();
    }
  }

  // The room under the newest message

  @Override
  public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    outRect.setEmpty();
    // (with no bubbles left, the room of the last one still settles to nothing)
    if ((slots.isEmpty() && shiftStart == 0f) || parent.getChildLayoutPosition(view) != 0) {
      return;
    }
    final TGMessage top = activeTop();
    if (top == null) {
      return;
    }
    final int width = parent.getMeasuredWidth();
    if (width > 0 && width != builtWidth && !slots.isEmpty()) {
      // A new width (a fold, a pane resized): the bubbles are laid out for it in this pass
      builtWidth = width;
      total = 0;
      for (Slot slot : slots) {
        if (slot.taker == null) {
          slot.msg.buildLayout(width);
        }
        slot.height = heightOf(slot, top);
        total += slot.height;
      }
      layer.post(layer::requestLayout);
    }
    // The room settles instead of the list moving: the list's layout manager keeps the lower edge of the newest
    // message's room where it is, so the content above moves (and is filled in again at the top) inside the list.
    // It is below zero only when a message took the place of a bubble that was still rising: that message then rises
    // the rest of the way from behind the message box, as its bubble would have (never more than its own height).
    outRect.bottom = Math.max(-top.getHeight(), total - Math.round(shiftStart * settle.getFactor()));
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidateDecorations(); // the room: the list lays out again; the bubbles follow it on that draw
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 0f) {
      shiftStart = 0f;
      for (Slot slot : slots) {
        slot.carryStart = 0f;
      }
    }
  }

  // Placing, every frame

  private float lastClipTop, lastClipBottom, lastClipLeft, lastClipRight;

  /** Before every draw: the bubbles follow the list (scrolling, its animations, the bar and the keyboard moving it). */
  private void position () {
    if (slots.isEmpty() && ghosts.isEmpty()) {
      return;
    }
    final TGMessage top = activeTop();
    if (!slots.isEmpty() && top != null) {
      // A message came or went, or the list merged it again (it has bottom padding while a bubble is drawn under it)
      boolean remerge = top != mergedTop || (top.getExtraPadding() != 0 && drawsBubbles());
      if (!remerge) {
        for (Slot slot : slots) {
          if (heightOf(slot, top) != slot.height) {
            remerge = true; // e.g. its reply loaded, or an album's newest message grew
            break;
          }
        }
      }
      if (remerge) {
        change(null, false);
      }
    }
    final View topView = top != null ? manager.getLayoutManager().findViewByPosition(0) : null;
    final float factor = settle.getFactor();
    final float listY = list.getTranslationY();
    final boolean clipToPadding = list.getClipToPadding();
    final float left = list.getLeft() + list.getTranslationX() - layer.getLeft();
    // The list's place: a bubble never draws over the bar or the message box
    final float clipTop = list.getTop() + listY - layer.getTop() + (clipToPadding ? list.getPaddingTop() : 0);
    final float clipBottom = list.getBottom() + listY - layer.getTop() - (clipToPadding ? list.getPaddingBottom() : 0);
    final float clipRight = left + list.getWidth();
    boolean invalidate = false;
    if (clipTop != lastClipTop || clipBottom != lastClipBottom || left != lastClipLeft || clipRight != lastClipRight) {
      lastClipTop = clipTop;
      lastClipBottom = clipBottom;
      lastClipLeft = left;
      lastClipRight = clipRight;
      clip.set(left, clipTop, clipRight, clipBottom);
      invalidate = true;
    }
    final float baseY = list.getTop() + listY - list.xgramContentOffset() - layer.getTop();
    final float baseX = left + list.getPaddingLeft();
    final boolean show = topView != null;
    float y = show ? topView.getBottom() : 0;
    for (Slot slot : slots) {
      final float slotTop = y + slot.carryStart * factor;
      final float layerY = baseY + slotTop;
      // (one a message took the place of isn't drawn: an album coming in keeps only its room)
      final boolean visible = show && !slot.handedOff && layerY < clip.bottom && layerY + slot.height > clip.top;
      if (slot.msg.hasDate() && (visible != slot.placed || layerY != slot.layerY)) {
        invalidate = true; // the layer draws its date
      }
      slot.topInList = slotTop;
      slot.layerY = layerY;
      slot.placed = visible;
      if (visible) {
        slot.view.setTranslationX(baseX);
        slot.view.setTranslationY(layerY);
        slot.view.setAlpha(BUBBLE_ALPHA * slot.fade.getFactor());
      }
      setShown(slot.view, visible);
      y += slot.height;
    }
    if (invalidate) {
      layer.invalidate();
    }
  }

  private static void setShown (View view, boolean shown) {
    int visibility = shown ? View.VISIBLE : View.INVISIBLE;
    if (view.getVisibility() != visibility) {
      view.setVisibility(visibility);
    }
  }

  // Taps

  @Nullable private Slot downSlot;
  private float downX, downY;

  @Override
  public boolean onInterceptTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        downSlot = slotAt(e.getX(), e.getY());
        downX = e.getX();
        downY = e.getY();
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (downSlot != null && (Math.abs(e.getX() - downX) > Screen.getTouchSlop() || Math.abs(e.getY() - downY) > Screen.getTouchSlop())) {
          downSlot = null; // a scroll
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        final Slot slot = downSlot;
        downSlot = null;
        if (slot != null && slotAt(e.getX(), e.getY()) == slot) {
          rv.post(() -> onTap(slot));
        }
        break;
      }
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_CANCEL: {
        downSlot = null;
        break;
      }
    }
    return false; // never takes the touch: the list scrolls as always
  }

  @Override
  public void onTouchEvent (@NonNull RecyclerView rv, @NonNull MotionEvent e) { }

  @Override
  public void onRequestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    if (disallowIntercept) {
      downSlot = null;
    }
  }

  /** The bubble under a point of the list (its bubble, not the whole row, with the bubbles look). */
  @Nullable
  private Slot slotAt (float x, float y) {
    for (Slot slot : slots) {
      if (!slot.placed || slot.handedOff || y < slot.topInList || y >= slot.topInList + slot.height) {
        continue;
      }
      final float start = list.getPaddingLeft();
      if (slot.msg.useBubbles()) {
        final float left = start + slot.msg.getActualLeftContentEdge() - slack;
        final float right = start + slot.msg.getActualRightContentEdge() + slack;
        if (x < left || x > right) {
          return null;
        }
      }
      return slot;
    }
    return null;
  }

  private void onTap (Slot slot) {
    if (!slots.contains(slot) || slot.handedOff || !owner.isFocused() || !XgramUndoSend.isWaiting(slot.item)) {
      return; // on its way already
    }
    final XgramUndoSendItem item = slot.item;
    final boolean canUndo = !owner.isEditingMessage(); // as the bar's Undo: not while another message is edited
    if (XgramExpressive.isEnabled()) {
      List<XgramR5Chat.MenuEntry> entries = new ArrayList<>(2);
      if (canUndo) {
        entries.add(new XgramR5Chat.MenuEntry(R.id.btn_xgramUndoSendBubbleUndo, Lang.getString(R.string.XgramUndoSendUndo), R.drawable.baseline_undo_24, () -> undo(item)));
      }
      entries.add(new XgramR5Chat.MenuEntry(R.id.btn_xgramUndoSendBubbleNow, Lang.getString(R.string.XgramUndoSendNow), R.drawable.baseline_send_24, () -> sendNow(item)));
      XgramR5Chat.showMenu(slot.view, owner.getThemeListeners(), entries);
    } else {
      final int[] ids = canUndo ? new int[] {R.id.btn_xgramUndoSendBubbleUndo, R.id.btn_xgramUndoSendBubbleNow} : new int[] {R.id.btn_xgramUndoSendBubbleNow};
      final String[] titles = canUndo ?
        new String[] {Lang.getString(R.string.XgramUndoSendUndo), Lang.getString(R.string.XgramUndoSendNow)} :
        new String[] {Lang.getString(R.string.XgramUndoSendNow)};
      final int[] icons = canUndo ? new int[] {R.drawable.baseline_undo_24, R.drawable.baseline_send_24} : new int[] {R.drawable.baseline_send_24};
      owner.showOptions(null, ids, titles, null, icons, (itemView, id) -> {
        if (id == R.id.btn_xgramUndoSendBubbleUndo) {
          undo(item);
        } else if (id == R.id.btn_xgramUndoSendBubbleNow) {
          sendNow(item);
        }
        return true;
      });
    }
  }

  /** Undo of that one message: it goes back into the message box (a recording is discarded, after asking). */
  private void undo (XgramUndoSendItem item) {
    if (item.isNote()) {
      owner.showOptions(Lang.getString(item.kind == XgramUndoSendItem.KIND_VIDEO ? R.string.XgramUndoSendDiscardVideo : R.string.XgramUndoSendDiscardVoice),
        new int[] {R.id.btn_xgramUndoSendDiscard, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Discard), Lang.getString(R.string.Cancel)},
        new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.btn_xgramUndoSendDiscard && XgramUndoSend.undo(owner, item.order)) {
            confirmHaptic();
          }
          return true;
        });
      return;
    }
    if (XgramUndoSend.undo(owner, item.order)) {
      confirmHaptic();
    }
  }

  private void sendNow (XgramUndoSendItem item) {
    if (XgramUndoSend.sendNow(owner, item)) {
      list.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
  }

  private void confirmHaptic () {
    list.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
  }

  // Views

  /** One faded bubble: Telegram X's message view for the preview message, in the layer. */
  private final class Slot implements FactorAnimator.Target, Comparable<Slot> {
    final XgramUndoSendItem item;
    /** Its part, its place among the part's bubbles, and the index of its first message in the part's preview. */
    final int part, index, firstIndex;
    final long key;
    final TGMessage msg;
    final MessageView view;
    final Countdown countdown;
    /** Its fade in: 0 to 1. */
    final FactorAnimator fade;
    int height;
    /** How far it is moved (px, at the settle factor 1) to keep its place on screen. */
    float carryStart;
    /** A sent message took its place. */
    boolean handedOff;
    /** An album only: the first of its messages, which took its place (the others combine into it), and its height then. */
    @Nullable TGMessage taker;
    int fullHeight;
    // For one change: its distance from the end before it, its carry then, and whether it came in this change
    int sBefore;
    float carryNow;
    boolean fresh;
    // Its last place: in the list's coordinates (taps), in the layer's (drawing)
    float topInList, layerY;
    boolean placed;

    Slot (XgramUndoSend.BubblePart part, long key, int index, int firstIndex, TGMessage msg) {
      this.item = part.item;
      this.part = part.part;
      this.index = index;
      this.firstIndex = firstIndex;
      this.key = key;
      this.msg = msg;
      this.countdown = new Countdown(part.item.dueUptime, part.item.totalMs);
      this.countdown.setSending(part.sending);
      // Before it is shown anywhere: nothing may take it for a message
      msg.xgramWaiting = countdown;
      this.fade = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, FADE_MS, 0f);
      this.view = SettingHolder.createMessageView(layer.getContext(), MessagesAdapter.xgramViewTypeOf(msg), manager, owner);
      view.setParentOnMeasureDisabled(true); // measured at the list's width by the layer
      view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      view.setVisibility(View.INVISIBLE);
      view.setAlpha(0f);
      view.setMessage(msg);
      layer.addView(view);
      view.onAttachedToRecyclerView();
      this.height = msg.getHeight();
      this.fresh = true;
    }

    /**
     * An album whose first message took its place and is still the list's newest message, while the rest of it isn't
     * combined into that message yet: its room stays. When the rest doesn't come there (another message came first, it
     * was deleted), it goes; its part's result removes it anyway ({@link #sync}).
     */
    boolean waitsForRest (@Nullable TGMessage top) {
      return taker != null && taker == top && taker.getCombinedMessageCount() < msg.getCombinedMessageCount();
    }

    void fadeIn (boolean animated) {
      if (animated) {
        if (XgramExpressive.isEnabled()) {
          fade.setInterpolator(XgramAppMotion.springClamped(XgramExpressive.EFFECTS_DEFAULT));
          fade.setDuration(XgramAppMotion.springDuration(XgramExpressive.EFFECTS_DEFAULT));
        }
        fade.animateTo(1f);
      } else {
        fade.forceFactor(1f);
      }
      layer.invalidate();
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      if (placed) {
        view.setAlpha(BUBBLE_ALPHA * MathUtils.clamp(factor));
      }
      if (msg.hasDate()) {
        layer.invalidate();
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    public int compareTo (Slot other) {
      if (item.order != other.item.order) {
        return item.order < other.item.order ? -1 : 1;
      }
      if (part != other.part) {
        return part < other.part ? -1 : 1;
      }
      return Integer.compare(index, other.index);
    }
  }

  /** A bubble that left with no message in its place: it fades out where it was, while the list closes the gap over it. */
  private final class Ghost implements FactorAnimator.Target {
    final MessageView view;
    final FactorAnimator fade;
    final float from;

    Ghost (Slot slot) {
      this.view = slot.view;
      this.from = MathUtils.clamp(slot.fade.getFactor());
      slot.countdown.setSending(true);
      this.fade = new FactorAnimator(0, this, AnimatorUtils.LINEAR_INTERPOLATOR, FADE_MS, 1f);
    }

    /** Once it is in {@link #ghosts}: with animations off the fade ends (and the view goes) right in here. */
    void start () {
      fade.animateTo(0f);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      view.setAlpha(BUBBLE_ALPHA * from * MathUtils.clamp(factor));
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (finalFactor == 0f && ghosts.remove(this)) {
        destroyView(view);
      }
    }
  }

  /** Under the list, with no touches: draws the bubbles where the list keeps their room, and their dates. */
  private final class Layer extends ViewGroup {
    @Nullable private ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
      position();
      return true;
    };

    Layer (Context context) {
      super(context);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      setWillNotDraw(true);
    }

    /**
     * The list's width, which the bubbles' messages are laid out for (as the list's own: getItemOffsets, remerge). Before
     * the list is measured: the layer's (the chat's content view) less the list's side margins (topic tabs).
     */
    private int listWidth (int layerWidth) {
      if (list.getMeasuredWidth() > 0) {
        return list.getMeasuredWidth();
      }
      ViewGroup.LayoutParams params = list.getLayoutParams();
      if (params instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        return Math.max(0, layerWidth - margins.leftMargin - margins.rightMargin);
      }
      return layerWidth;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
      setMeasuredDimension(width, height);
      final int childWidth = MeasureSpec.makeMeasureSpec(listWidth(width), MeasureSpec.EXACTLY);
      final int childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
      for (int i = 0; i < getChildCount(); i++) {
        getChildAt(i).measure(childWidth, childHeight);
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
      }
    }

    @Override
    public boolean dispatchTouchEvent (MotionEvent event) {
      return false; // the list above takes every touch (taps on a bubble: onInterceptTouchEvent)
    }

    @Override
    public boolean hasOverlappingRendering () {
      return false;
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (getChildCount() == 0) {
        return;
      }
      final int saveCount = c.save();
      c.clipRect(clip);
      super.dispatchDraw(c);
      // Their dates, as the list's decoration draws them over its messages
      final int centerX = Math.round(clip.left + list.getMeasuredWidth() / 2f);
      for (Slot slot : slots) {
        if (slot.placed && slot.msg.hasDate()) {
          slot.msg.drawDate(c, centerX, Math.round(slot.layerY + slot.msg.getDrawDateY()), 0f, BUBBLE_ALPHA * MathUtils.clamp(slot.fade.getFactor()));
        }
      }
      c.restoreToCount(saveCount);
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      observer = getViewTreeObserver();
      observer.addOnPreDrawListener(preDraw);
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      if (observer != null && observer.isAlive()) {
        observer.removeOnPreDrawListener(preDraw);
      }
      observer = null;
    }
  }

  /**
   * The countdown a faded bubble draws where the sending clock goes (TGMessage): a ring that drains clockwise from 12
   * o'clock, in the clock's color, over a track of it. Redrawn every frame only while it drains; nothing is allocated
   * per frame.
   */
  public static final class Countdown {
    private final long dueUptime, totalMs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private volatile boolean sending;

    Countdown (long dueUptime, long totalMs) {
      this.dueUptime = dueUptime;
      this.totalMs = totalMs;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setSending (boolean sending) {
      this.sending = sending;
    }

    /** Draws the ring centered at ({@code cx}, {@code cy}); true: the clock isn't drawn. */
    public boolean draw (Canvas c, @Nullable View view, float cx, float cy, int color) {
      final float progress = sending || totalMs <= 0 ? 0f : MathUtils.clamp((dueUptime - SystemClock.uptimeMillis()) / (float) totalMs);
      final float radius = Screen.dp(5f);
      paint.setStrokeWidth(Screen.dp(1.5f));
      paint.setColor(XgramExpressive.withAlpha(color, .3f));
      c.drawCircle(cx, cy, radius, paint);
      if (progress > 0f) {
        paint.setColor(color);
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        // The arc keeps its end at 12 o'clock: its start moves clockwise as the time runs out
        c.drawArc(rect, -90f + 360f * (1f - progress), 360f * progress, false, paint);
        if (view != null) {
          view.postInvalidateOnAnimation();
        }
      }
      return true;
    }
  }
}
