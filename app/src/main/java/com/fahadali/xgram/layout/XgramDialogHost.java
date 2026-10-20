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
package com.fahadali.xgram.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramPopupBounds;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.AnimatorUtils;

/**
 * A full-window holder for one centred dialog card that lives in the activity's content view (BaseActivity's
 * progress card, "Working on your text…"). The content view runs edge to edge, under the status bar and the keyboard,
 * and FrameLayout centring moves a card up by its whole bottom margin, so a card centred with gravity sat under the
 * status bar while the keyboard was open. This host places the card itself (its gravity and margins are ignored):
 * <ul>
 *   <li>centred in the free band between the status bar / top cutout (plus a gap) and the keyboard, the navigation
 *   bar or Telegram X's own emoji, sticker and formatting panel (plus a gap);</li>
 *   <li>never wider than the window less the gaps (a card built on the inner screen is reused on the cover screen);</li>
 *   <li>when the band is too short, with 8dp gaps instead: as low as it fits above the keyboard (its buttons stay
 *   tappable), never above the status bar (the rest then runs behind the keyboard, nothing is squashed);</li>
 *   <li>moved on a spring (Telegram X's decelerate with Expressive design off) when the keyboard opens or closes
 *   while it shows, instead of jumping.</li>
 * </ul>
 * The host itself is not clickable: taps outside the card reach the scrim under it. Scale and alpha of the card stay
 * the caller's (the move uses translationY only).
 */
public final class XgramDialogHost extends FrameLayout implements RootFrameLayout.InsetsChangeListener {
  private static final long MOVE_DURATION = 220L;

  private final BaseActivity activity;
  private final int[] location = new int[2];

  /** The free band in this view's coordinates, worked out on every measure. */
  private int bandTop, bandBottom;
  /** For a card taller than the band: its highest top (8dp under the status bar) and the band's edge without the gap. */
  private int tightTop, limitBottom;
  /** Bottom of the window in this view's coordinates (a card taller than the band may run behind the keyboard). */
  private int windowBottom;

  /** The last layout of the card, to animate a move caused by a band change; MIN_VALUE: not laid out yet. */
  private int lastTop = Integer.MIN_VALUE, lastWidth, lastBandTop, lastBandBottom;
  private @Nullable ValueAnimator moveAnimator;

  public XgramDialogHost (@NonNull BaseActivity activity) {
    super(activity);
    this.activity = activity;
    setClipChildren(false); // room for the M3E spring overshoot of the card's scale
    setClickable(false);
    setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  /** The space kept between the card and the status bar, the keyboard and the window's sides. */
  private static int gap () {
    return Screen.dp(XgramExpressive.isEnabled() ? 24f : 16f);
  }

  /** Window y of this view's top: its parent's (the content view, 0 unless it is moved) plus its own top margin. */
  private int windowTop () {
    ViewParent parent = getParent();
    if (!(parent instanceof View)) {
      return 0;
    }
    ((View) parent).getLocationInWindow(location);
    ViewGroup.LayoutParams params = getLayoutParams();
    return location[1] + (params instanceof MarginLayoutParams ? ((MarginLayoutParams) params).topMargin : 0);
  }

  private void computeBand (int height) {
    final int gap = gap();
    final int y0 = windowTop();
    final RootFrameLayout root = activity.getRootView();

    int topInset;
    int bottom = height; // edge to edge: the root already ends this view above the keyboard and the navigation bar
    if (root != null) {
      // the status bar and the top cutout (both are in window coordinates)
      topInset = Math.max(root.getTopInset(), root.getDisplayCutoutTopInset().bottom);
      // without edge to edge the content view's children get no bottom margin: keep above the keyboard by the insets
      root.getLocationInWindow(location);
      final int rootTop = location[1];
      bottom = Math.min(bottom, rootTop + root.getHeight() - root.getBottomInset() - y0);
      windowBottom = rootTop + root.getHeight() - y0;
    } else {
      topInset = HeaderView.getTopOffset();
      windowBottom = Screen.currentHeight() - y0;
    }

    // Telegram X's own emoji / sticker / formatting panel is a view of the chat, not an inset
    NavigationController navigation = activity.navigation();
    ViewController<?> c = navigation != null ? navigation.getCurrentStackItem() : null;
    if (c instanceof MessagesController) {
      int panelTop = ((MessagesController) c).xgramInAppKeyboardTop();
      if (panelTop > 0) {
        bottom = Math.min(bottom, panelTop - y0);
      }
    }

    bandTop = Math.max(0, topInset - y0) + gap;
    bandBottom = Math.max(bandTop, bottom - gap);
    tightTop = Math.max(0, topInset - y0) + Screen.dp(XgramPopupBounds.MARGIN);
    limitBottom = bottom;
    windowBottom = Math.max(windowBottom, height);
  }

  private static int childSpec (int size, int max) {
    if (size >= 0) {
      return MeasureSpec.makeMeasureSpec(Math.min(size, max), MeasureSpec.EXACTLY);
    }
    if (size == ViewGroup.LayoutParams.MATCH_PARENT) {
      return MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
    }
    return MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int height = MeasureSpec.getSize(heightMeasureSpec);
    setMeasuredDimension(width, height);
    computeBand(height);

    final int gap = gap();
    final int maxWidth = Math.max(0, width - gap * 2);
    // a card taller than the band keeps its size (onLayout: as low as it fits above the keyboard, the rest behind it)
    final int maxHeight = Math.max(0, Math.max(bandBottom - bandTop, windowBottom - bandTop - gap));
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child == null || child.getVisibility() == GONE) {
        continue;
      }
      ViewGroup.LayoutParams params = child.getLayoutParams();
      child.measure(childSpec(params.width, maxWidth), childSpec(params.height, maxHeight));
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    final int width = right - left;
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child == null || child.getVisibility() == GONE) {
        continue;
      }
      final int childWidth = child.getMeasuredWidth();
      final int childHeight = child.getMeasuredHeight();
      final int childLeft = (width - childWidth) / 2;
      final int childTop;
      if (childHeight > bandBottom - bandTop) {
        // too tall for the band: 8dp gaps; its bottom 8dp above the keyboard when it fits that way (Cancel stays above
        // the keyboard), never above the status bar + 8dp
        childTop = Math.max(tightTop, Math.min(bandTop, limitBottom - Screen.dp(XgramPopupBounds.MARGIN) - childHeight));
      } else {
        childTop = bandTop + (bandBottom - bandTop - childHeight) / 2;
      }
      // a move caused by the keyboard (the band), not by the card's own size or a different screen, is animated
      final boolean animate = i == 0 && lastTop != Integer.MIN_VALUE && lastWidth == width && childTop != lastTop &&
        (lastBandTop != bandTop || lastBandBottom != bandBottom) && isShown();
      final int previousVisualTop = i == 0 && lastTop != Integer.MIN_VALUE ? lastTop + Math.round(child.getTranslationY()) : 0;
      child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
      if (i == 0) {
        if (animate) {
          animateMove(child, previousVisualTop - childTop);
        } else if (lastWidth != width) {
          // another screen (fold, rotation): straight to the new place
          cancelMove();
          child.setTranslationY(0f);
        }
        lastTop = childTop;
        lastWidth = width;
        lastBandTop = bandTop;
        lastBandBottom = bandBottom;
      }
    }
  }

  private void animateMove (View child, int fromOffset) {
    cancelMove();
    if (fromOffset == 0) {
      child.setTranslationY(0f);
      return;
    }
    final float from = fromOffset;
    child.setTranslationY(from);
    final ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.setInterpolator(XgramAppMotion.interp(XgramExpressive.SPATIAL_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR));
    animator.setDuration(XgramAppMotion.duration(XgramExpressive.SPATIAL_DEFAULT, MOVE_DURATION));
    animator.addUpdateListener(animation -> child.setTranslationY(from * (1f - AnimatorUtils.getFraction(animation))));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (moveAnimator == animator) {
          moveAnimator = null;
          child.setTranslationY(0f);
        }
      }
    });
    moveAnimator = animator;
    animator.start();
  }

  private void cancelMove () {
    ValueAnimator animator = moveAnimator;
    if (animator != null) {
      moveAnimator = null; // the end listener leaves the translation as it is: the next move starts from there
      animator.cancel();
    }
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    RootFrameLayout root = activity.getRootView();
    if (root != null) {
      root.addInsetsChangeListener(this);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    RootFrameLayout root = activity.getRootView();
    if (root != null) {
      root.removeInsetsChangeListener(this);
    }
    // shown again later: placed where it belongs then, without a move from the old place
    cancelMove();
    lastTop = Integer.MIN_VALUE;
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child != null) {
        child.setTranslationY(0f);
      }
    }
    super.onDetachedFromWindow();
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate) {
    // the status bar or the keyboard changed: work the band out again even when this view keeps its size
    requestLayout();
  }

  @Override
  public void onSecondaryInsetsChanged (RootFrameLayout viewGroup, boolean systemGesturesInsetsChanged, boolean displayCutoutInsetsChanged) {
    if (displayCutoutInsetsChanged) {
      requestLayout();
    }
  }
}
