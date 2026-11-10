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
 * File created on 08/02/2016 at 08:19
 */
package org.thunderdog.challegram.component.user;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.StretchyHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.HeaderEditText;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

public class BubbleHeaderView extends FrameLayoutFix implements RtlCheckListener, StretchyHeaderView, TextWatcher, HeaderView.OffsetChangeListener {

  private static final int DEFAULT_MAX_BUBBLE_LINES = 2;

  private final HeaderEditText editText;
  private final ScrollView scrollView;
  private final BubbleWrapView bubbleWrap;
  private final Tdlib tdlib;

  Callback callback;
  private final ArrayList<BubbleView.Entry> bubbles;
  private final int maxBubbleHeight;

  public BubbleHeaderView (Context context, @NonNull Tdlib tdlib) {
    this(context, tdlib, DEFAULT_MAX_BUBBLE_LINES);
  }

  public static int maxBubbleHeight (int maxBubbleLines) {
    int bubbleHeight = Screen.dp(BubbleView.RADIUS) * 2;
    return Screen.dp(BubbleWrapView.START_Y) + Screen.dp(BubbleWrapView.SPACING) * (maxBubbleLines - 1) + bubbleHeight * maxBubbleLines;
  }

  public BubbleHeaderView (Context context, @NonNull Tdlib tdlib, int maxBubbleLines) {
    super(context);
    this.tdlib = tdlib;

    bubbles = new ArrayList<>(10);

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    bubbleWrap = new BubbleWrapView(context, tdlib);
    bubbleWrap.setHeaderView(this);
    bubbleWrap.setLayoutParams(params);

    // TODO: expand maxBubbleHeight if users starts scrolling manually, and shrink back with delay once finished
    this.maxBubbleHeight = maxBubbleHeight(maxBubbleLines);
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, maxBubbleHeight);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(60f);
    } else {
      params.leftMargin = Screen.dp(60f);
    }

    scrollView = new ScrollView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent ev) {
        return ev.getAction() == MotionEvent.ACTION_DOWN ? (ev.getY() < lastHeight && super.onTouchEvent(ev)) : super.onTouchEvent(ev);
      }
    };
    // XGram: clipped at its top padding (the status bar's height, onHeaderOffsetChanged): picked chats scrolled up
    // were drawn under the status bar, which is see-through edge to edge (the Remove Fee picker with 5+ people)
    scrollView.setClipToPadding(true);
    scrollView.setVerticalScrollBarEnabled(false);
    scrollView.addView(bubbleWrap);
    scrollView.setLayoutParams(params);
    scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> xgramUpdateScrollClip());

    addView(scrollView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize());
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }
    editText = HeaderEditText.create(this, false, null);
    editText.setPadding(Screen.dp(5f), 0, Screen.dp(5f), 0);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E: the input is the M3 search bar pill (surfaceContainerHigh, bodyLarge), 12dp clear of the end; the
      // picked chats above it are M3 input chips (BubbleView)
      HeaderEditText.xgramApplySearchBar(editText, com.fahadali.xgram.appearance.XgramExpressive.surfaceHeader(), null);
      if (Lang.rtl()) {
        params.leftMargin = Screen.dp(XGRAM_END_MARGIN);
      } else {
        params.rightMargin = Screen.dp(XGRAM_END_MARGIN);
      }
    }
    editText.addTextChangedListener(this);
    editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
    editText.setLayoutParams(params);
    addView(editText);
  }

  private static final float XGRAM_END_MARGIN = 12f;

  private static int xgramEndMargin () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? Screen.dp(XGRAM_END_MARGIN) : 0;
  }

  private int currentTopOffset;

  @Override
  public void onHeaderOffsetChanged (HeaderView headerView, int newOffset) {
    currentTopOffset = newOffset;
    Views.setLayoutHeight(scrollView, maxBubbleHeight + newOffset);
    scrollView.setPadding(0, newOffset, 0, 0);
    Views.setTopMargin(editText, newOffset);
    xgramUpdateClip();
    xgramUpdateScrollClip();
  }

  // XGram: the chip area ends at its first row's top (the status bar plus the wrap's START_Y margin). Scrolled to the
  // newest rows, that margin showed the bottom of the row above it, a cut-off sliver under the status bar, outside the
  // two rows the area holds. With few chips the band is empty, so nothing else moves.
  private final android.graphics.Rect xgramScrollClip = new android.graphics.Rect();

  private void xgramUpdateScrollClip () {
    if (scrollView == null || xgramScrollClip == null) {
      return;
    }
    final int width = scrollView.getWidth(), height = scrollView.getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }
    final int top = Math.min(height, currentTopOffset + Screen.dp(BubbleWrapView.START_Y));
    if (xgramScrollClip.top != top || xgramScrollClip.right != width || xgramScrollClip.bottom != height) {
      xgramScrollClip.set(0, top, width, height);
      scrollView.setClipBounds(xgramScrollClip);
    }
  }

  // XGram: nothing is drawn above the status bar's bottom edge on screen. setScaleFactor moves this whole view up as
  // the list below scrolls (by up to the picked chats' height), so clipping only the scroll view at its padding still
  // let the picked chats and the search bar slide up under the see-through status bar. A clip on the view itself is
  // a property of its render node: moving it costs no redraw.
  private final android.graphics.Rect xgramClip = new android.graphics.Rect();
  private boolean xgramClipSet;

  private void xgramUpdateClip () {
    final int width = getWidth(), height = getHeight();
    if (xgramClip == null) { // a move during construction, before the fields are set
      return;
    }
    if (currentTopOffset <= 0 || width <= 0 || height <= 0) {
      if (xgramClipSet) {
        xgramClipSet = false;
        setClipBounds(null);
      }
      return;
    }
    final int top = Math.min(height, Math.max(0, Math.round(currentTopOffset - getTranslationY())));
    if (!xgramClipSet || xgramClip.top != top || xgramClip.right != width || xgramClip.bottom != height) {
      xgramClip.set(0, top, width, height);
      xgramClipSet = true;
      setClipBounds(xgramClip);
    }
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    xgramUpdateClip();
  }

  // Every vertical move, whoever makes it: setScaleFactor, and HeaderView's own transitions and resets
  @Override
  public void setTranslationY (float translationY) {
    super.setTranslationY(translationY);
    xgramUpdateClip();
  }

  @Override
  public void checkRtl () {
    if (editText != null && editText.getGravity() != (Lang.gravity() | Gravity.CENTER_VERTICAL)) {
      editText.checkRtl();
      if (Views.setMargins((FrameLayout.LayoutParams) editText.getLayoutParams(), Lang.rtl() ? xgramEndMargin() : Screen.dp(68f), currentTopOffset, Lang.rtl() ? Screen.dp(68f) : xgramEndMargin(), 0)) {
        Views.updateLayoutParams(editText);
      }
    }
    if (bubbleWrap != null) {
      bubbleWrap.invalidate();
    }
    if (scrollView != null && Views.setMargins((FrameLayout.LayoutParams) scrollView.getLayoutParams(), Lang.rtl() ? 0 : Screen.dp(60f), 0, Lang.rtl() ? Screen.dp(60f) : 0, 0)) {
      Views.updateLayoutParams(scrollView);
    }
  }

  public void forceUsers (List<TGUser> users) {
    List<BubbleView.Entry> entries = new ArrayList<>(users.size());
    for (TGUser user : users) {
      entries.add(BubbleView.Entry.valueOf(tdlib, user));
    }
    forceBubbles(entries);
  }

  public void forceBubbles (List<BubbleView.Entry> entries) {
    for (BubbleView.Entry entry : entries) {
      this.bubbles.add(entry);
      bubbleWrap.addBubbleForce(entry);
    }

    bubbleWrap.buildLayout();
    ignoreFirstUpdate = true;
    scrollView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
      @Override
      public void onLayoutChange (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        scrollView.scrollTo(0, bubbleWrap.getMeasuredHeight() - scrollView.getMeasuredHeight() + scrollView.getPaddingTop());
        scrollView.removeOnLayoutChangeListener(this);
      }
    });
    // forceHeight(Math.min(maxBubbleHeight, bubbleWrap.getCurrentHeight()));
  }

  public int getCurrentWrapHeight () {
    return Math.min(maxBubbleHeight, bubbleWrap.getCurrentHeight());
  }

  public void destroy () {
    bubbleWrap.destroy();
  }

  public void setHint (@StringRes int hintResId) {
    editText.setHint(Lang.getString(hintResId));
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public HeaderEditText getInput () {
    return editText;
  }

  public boolean areBubblesAnimating () {
    return bubbleWrap.isAnimating();
  }

  // public changers

  public void addBubble (BubbleView.Entry entry) {
    bubbles.add(entry);
    bubbleWrap.addBubble(entry);
  }

  private void removeBubbleAt (int index) {
    BubbleView.Entry removed = bubbles.remove(index);
    bubbleWrap.removeBubble(removed);
  }

  private int indexOfEntry (BubbleView.Entry entry) {
    return bubbles.indexOf(entry);
  }

  public void removeBubble (BubbleView.Entry entry) {
    int index = indexOfEntry(entry);
    if (index != -1) {
      removeBubbleAt(index);
    }
  }

  private int lastHeight;
  private int heightDiff;
  private float factor;
  private boolean overflow;
  private int fromOffset;
  private boolean applyHeight;

  boolean prepareChangeHeight (int height, boolean byTouch) {
    factor = 0f;
    if (lastHeight == maxBubbleHeight && height >= lastHeight) {
      fromOffset = scrollView.getScrollY();
      heightDiff = height - maxBubbleHeight - fromOffset; // 0
      overflow = true;
      if (byTouch && heightDiff > 0) {
        heightDiff = 0;
      }
    } else {
      heightDiff = height - lastHeight;
      overflow = false;
      if (height < lastHeight && callback != null) {
        callback.prepareHeaderOffset(height);
        applyHeight = false;
      } else {
        applyHeight = true;
      }
    }
    return heightDiff != 0;
  }

  void completeChangeHeight () {
    if (!overflow) {
      lastHeight = lastHeight + heightDiff;
      if (applyHeight && callback != null) {
        callback.applyHeaderOffset();
      }
    }
  }

  private boolean ignoreFirstUpdate;

  void forceHeight (int height) {
    if (ignoreFirstUpdate) {
      ignoreFirstUpdate = false;
      lastHeight = Math.min(maxBubbleHeight, height);
      scrollView.scrollTo(0, height);
      editText.setTranslationY(lastHeight);
      return;
    }
    if (prepareChangeHeight(height, false)) {
      setFactor(1f);
      // applyHeight = true;
      completeChangeHeight();
    }
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      if (overflow) {
        scrollView.scrollTo(0, fromOffset + (int) ((float) heightDiff * factor));
      } else {
        int height = lastHeight + (int) ((float) heightDiff * factor);
        editText.setTranslationY(height);
        if (callback != null) {
          callback.setHeaderOffset(height);
        }
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  // TextWatcher

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    if (callback != null) {
      callback.searchForItems(s.toString());
    }
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) {

  }

  @Override
  public void afterTextChanged (Editable s) {

  }

  public void clearSearchInput () {
    editText.setText("");
  }

  public HeaderEditText getSearchInput () {
    return editText;
  }

  // Simple transition

  private float scaleFactor;

  /*@Override
  public float getScaleFactor () {
    return scaleFactor;
  }*/

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    scaleFactor = Size.convertExpandedFactor(scaleFactor);
    if (this.scaleFactor != scaleFactor) {
      this.scaleFactor = scaleFactor;

      if (lastHeight != 0) {
        float origScale = scaleFactor / ((float) lastHeight / (float) Size.getHeaderSizeDifference(false));
        setTranslationY(-lastHeight * (1f - origScale));
      }
    }
  }

  // Callback

  public interface Callback {
    View getTranslationView ();
    void searchForItems (String q);
    void onBubbleRemoved (@NonNull BubbleView.Entry entry);
    void setHeaderOffset (int offset);
    void applyHeaderOffset ();
    void prepareHeaderOffset (int height);
  }
}
