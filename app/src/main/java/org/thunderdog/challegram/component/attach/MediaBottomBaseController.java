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
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.EmptyTextView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public abstract class MediaBottomBaseController<T> extends ViewController<T> {
  protected final MediaLayout mediaLayout;
  private final int titleRes;
  private String titleString;

  protected MediaBottomBaseController (MediaLayout context, int titleResource) {
    super(context.getContext(), context.tdlib());
    this.titleRes = titleResource;
    this.titleString = "";
    this.mediaLayout = context;
    initMetrics();
  }

  protected MediaBottomBaseController (MediaLayout context, String titleString) {
    super(context.getContext(), context.tdlib());
    this.titleRes = 0;
    this.titleString = titleString;
    this.mediaLayout = context;
    initMetrics();
  }

  public @TdApi.ChatAction.Constructors int getBroadcastingAction () {
    return TdApi.ChatActionCancel.CONSTRUCTOR;
  }

  @Override
  public CharSequence getName () {
    return titleRes != 0 ? Lang.getString(titleRes) : titleString;
  }

  public void setName (String name) {
    titleString = name;
    mediaLayout.getHeaderView().setTitle(this);
  }

  // Settings

  public boolean allowSpoiler () {
    return false;
  }

  @Override
  protected final int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected final int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected final int getHeaderIconColorId () {
    return ColorId.headerLightIcon;
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (inSearchMode()) {
      if (commit) {
        mediaLayout.getHeaderView().closeSearchMode(true, null);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  public boolean canMoveRecycler () {
    return !inTransformMode(); // aka isAnimating
  }

  public boolean supportsMediaGrouping () {
    return false;
  }

  public boolean ignoreStartHeightLimits() {
    return false;
  }

  // Metrics

  private int contentHeight, startHeight, currentHeight;

  private int lastHeightIncreaseCheck;
  private boolean isHeightIncreasing; // true if it increased

  protected final void initMetrics () { // FIXME make private again and move calling from constructor to some proper place
    contentHeight = getInitialContentHeight();
    resetStartHeights(true);
  }

  private int getBarHeightIfAvailable () {
    return mediaLayout.inSpecificMode() && mediaLayout.getMode() != MediaLayout.MODE_CUSTOM_ADAPTER ? 0 : MediaBottomBar.getBarHeight() + (Settings.instance().useEdgeToEdge() ? context.getRootView().getSystemInsetsWithoutIme().bottom : 0);
  }

  private void resetStartHeights (boolean initial) {
    startHeight = Math.min(contentHeight + getBarHeightIfAvailable() + HeaderView.getSize(false), Math.min(getMaxStartHeight(), getMaxHeight()));
    setCurrentHeight(getRecyclerScrollY() > 0 || lastIsExpanded ? getMaxHeight() : startHeight, !initial);
  }

  public final int getStartHeight () {
    return startHeight;
  }

  public final int getCurrentWidth () {
    return mediaLayout.getCurrentContentWidth();
  }

  public final int getCurrentHeight () {
    return currentHeight;
  }

  public final int getContentHeight () {
    return contentHeight;
  }

  // Metrics (internal)

  protected int getInitialContentHeight () {
    return getMaxInitialContentHeight();
  }

  protected final int getMaxInitialContentHeight () {
    return Screen.smallestSide() - HeaderView.getSize(false);
  }

  private int getMaxStartHeight () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return Math.min(getContentHeight() + getBarHeightIfAvailable() + HeaderView.getSize(false), Math.min(getCurrentWidth() + getBarHeightIfAvailable(), getMaxStartHeightLimit()));
  }

  protected int getMaxStartHeightLimit () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return getMaxHeight() - MediaBottomBar.getBarHeight() / 4;
  }

  protected int getRecyclerHeaderOffset () {
    return 0;
  }

  protected boolean canExpandHeight () {
    return true;
  }

  protected boolean canMinimizeHeight () {
    return true;
  }

  public boolean showExitWarning (boolean isExitingSelection, boolean commit) {
    // override
    return false;
  }

  private int findYForStaticView (int viewHeight) {
    return (int) recyclerView.getTranslationY() + getRecyclerHeaderOffset() + (Math.max(startHeight, getCurrentHeight()) - getRecyclerHeaderOffset()) / 2 - getBarHeightIfAvailable() - viewHeight / 2;
  }

  // Recycler position

  public void onViewportChanged (int width, int height) {
    if (recyclerView != null) {
      resetStartHeights(false);
      updateRecyclerTop(height);
    }
  }

  private void updateRecyclerTop () {
    if (contentView != null) {
      int currentHeight = contentView.getMeasuredHeight();
      updateRecyclerTop(currentHeight == 0 ? getTargetHeight() : currentHeight);
    }
  }

  private int getTargetHeight () {
    return context.getRootView().getMeasuredHeight() - HeaderView.getTopOffset();
  }

  private void updateRecyclerTop (int height) {
    if (recyclerView != null) {
      float top = height - (xgramTransitionHeight >= 0 ? xgramTransitionHeight : currentHeight); // XGram: a tab switch's interpolated height
      recyclerView.setTranslationY(top);
      if (contentView != null && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        contentView.invalidate(); // XGram: the sheet top follows the list
      }
      onRecyclerTopUpdate(top);
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        xgramInvalidateSheetClip(); // XGram: the clipped corners follow the sheet top (after the views moved with it)
      }
    }
  }

  // General pattern

  protected MediaContentView contentView;
  private View progressView;
  protected MediaBottomBaseRecyclerView recyclerView;
  private EmptyTextView emptyView;

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    Views.applyBottomInset(recyclerView, extraBottomInsetWithoutIme);
  }

  protected final MediaContentView buildContentView (boolean needProgress) {
    contentView = new MediaContentView(context());
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.setBoundController(this);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = HeaderView.getSize(false);
    params.bottomMargin = HeaderView.getTopOffset();

    recyclerView = new MediaBottomBaseRecyclerView(context());
    Views.applyBottomInset(recyclerView, extraBottomInsetWithoutIme);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position != RecyclerView.NO_POSITION && recyclerView.getAdapter() != null && position == recyclerView.getAdapter().getItemCount() - 1 && mediaLayout.getCounterFactor() == 1f) {
          outRect.set(0, 0, 0, MediaBottomBar.getBarHeight() + mediaLayout.xgramCaptionPillHeight()); // XGram: and the caption field above the bar
        } else {
          outRect.setEmpty();
        }
      }
    });
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    com.fahadali.xgram.appearance.XgramMotion.stretch(recyclerView, com.fahadali.xgram.appearance.XgramMotion.EDGE_BOTTOM);
    ViewSupport.setThemedBackground(recyclerView, getRecyclerBackgroundColorId());
    // XGram: the list (its square background and the items at its top) is clipped to the sheet top's 28dp curve, which
    // starts in the band above it (see xgramDrawSheetTop): one shape, no square list corners under the curve
    xgramClipToSheet(recyclerView);
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 150l));
    recyclerView.setLayoutParams(params);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          hideSoftwareKeyboard();
        }
      }
    });
    contentView.addView(recyclerView);

    if (needProgress) {
      params = FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      params.topMargin = HeaderView.getSize(false);
      progressView = Views.simpleProgressView(context(), params);
      progressView.setTranslationY(findYForStaticView(Screen.dp(48f)));
      contentView.addView(progressView);
    }

    updateRecyclerTop();

    return contentView;
  }

  public void onRecyclerFirstMovement () {

  }

  // XGram: Material 3 Expressive modal sheet top above the list: 28dp corners and a drag handle in a 20dp band (8dp,
  // the 4dp handle, 8dp to the list), both fading out as the sheet reaches the top (where the header takes over). The
  // curve runs past the band into the list, which is clipped to it (see buildContentView): a band only 16dp tall drew
  // a curve cut down to its height over the square list.
  private @Nullable com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable xgramSheetTop;

  private static final float XGRAM_SHEET_BAND = 20f;

  private float xgramSheetCorner () {
    return recyclerView != null ? me.vkryl.core.MathUtils.clamp(recyclerView.getTranslationY() / Screen.dpf(com.fahadali.xgram.appearance.XgramExpressive.CORNER_XL)) : 0f;
  }

  private static float xgramSheetBand (float corner) {
    return Screen.dp(XGRAM_SHEET_BAND) * corner;
  }

  /** XGram: true while the sheet top is drawn above the list (an opaque list color). */
  private boolean xgramHasSheetTop () {
    return recyclerView != null && com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && (org.thunderdog.challegram.theme.Theme.getColor(getRecyclerBackgroundColorId()) >>> 24) != 0;
  }

  private @Nullable java.util.ArrayList<View> xgramSheetClipped;

  /**
   * XGram: clips {@code view}, the list or a view of {@link #contentView} that covers the list's top and moves with it
   * (the location map), to the sheet top's curve: the outline starts at the top of the sheet (the band above the list)
   * with its 28dp corners. It follows the list's moves; when the view moves on its own, call
   * {@link View#invalidateOutline()}.
   */
  protected final void xgramClipToSheet (View view) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    view.setOutlineProvider(new android.view.ViewOutlineProvider() {
      @Override
      public void getOutline (View v, android.graphics.Outline outline) {
        final float corner = xgramHasSheetTop() ? xgramSheetCorner() : 0f;
        final float sheetTop = recyclerView != null ? recyclerView.getTop() + recyclerView.getTranslationY() - xgramSheetBand(corner) : 0f;
        com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable.bodyOutline(v, outline, v.getTop() + v.getTranslationY() - sheetTop, corner);
      }
    });
    view.setClipToOutline(true);
    if (view != recyclerView) {
      if (xgramSheetClipped == null) {
        xgramSheetClipped = new java.util.ArrayList<>();
      }
      xgramSheetClipped.add(view);
    }
  }

  private void xgramInvalidateSheetClip () {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
      return;
    }
    if (recyclerView != null) {
      recyclerView.invalidateOutline();
    }
    if (xgramSheetClipped != null) {
      for (View view : xgramSheetClipped) {
        view.invalidateOutline();
      }
    }
  }

  // XGram: Material 3 Expressive tab switch (MediaLayout#onBottomPrepareSectionChange). Both pages sit at one
  // interpolated sheet height, MediaLayout draws the one sheet (top, handle and body) under them, the pages crossfade
  // over it, item animations are frozen and a start height that grows meanwhile waits for the end.

  private int xgramTransitionHeight = -1;
  private boolean xgramSwitching;
  private @Nullable RecyclerView.ItemAnimator xgramFrozenAnimator;
  private @Nullable MeasuredAdapterDelegate xgramPendingExpand;

  /** XGram: true between {@code xgramSetSwitching(true)} and {@code xgramSetSwitching(false)}. */
  public final boolean xgramIsSwitching () {
    return xgramSwitching;
  }

  /**
   * XGram: starts or ends a tab switch. While switching the list keeps no item animator, the sheet top is not drawn here
   * and a start height expansion waits; at the end the page goes back to its own height.
   */
  public final void xgramSetSwitching (boolean switching) {
    if (this.xgramSwitching == switching) {
      return;
    }
    this.xgramSwitching = switching;
    if (recyclerView != null) {
      if (switching) {
        xgramFrozenAnimator = recyclerView.getItemAnimator();
        if (xgramFrozenAnimator != null) {
          recyclerView.setItemAnimator(null);
        }
      } else {
        if (xgramFrozenAnimator != null && recyclerView.getItemAnimator() == null) {
          recyclerView.setItemAnimator(xgramFrozenAnimator);
        }
        xgramFrozenAnimator = null;
      }
    }
    xgramOnSwitch(switching);
    if (!switching) {
      xgramTransitionHeight = -1;
      updateRecyclerTop();
      MeasuredAdapterDelegate pending = xgramPendingExpand;
      xgramPendingExpand = null;
      if (pending != null) {
        xgramExpandStartHeight(pending);
      }
    }
  }

  /** XGram: the sheet height to show during a tab switch (does not touch the media layout's bar or header factors). */
  public final void xgramSetTransitionHeight (int height) {
    if (this.xgramTransitionHeight != height) {
      this.xgramTransitionHeight = height;
      updateRecyclerTop();
    }
  }

  /** XGram: the list's top in this page's view (the sheet edge, under the 20dp handle band). */
  public final float xgramListTop () {
    return recyclerView != null ? recyclerView.getTop() + recyclerView.getTranslationY() : 0f;
  }

  /** XGram: the sheet color of this page (its list's background). */
  public final @androidx.annotation.ColorInt int xgramSheetColor () {
    return org.thunderdog.challegram.theme.Theme.getColor(getRecyclerBackgroundColorId());
  }

  /**
   * XGram: during a tab switch, draws the whole sheet (28dp top corners, the handle and the body down to
   * {@code height}) in {@code color} at full opacity, at this page's current sheet top. Canvas in this page's view
   * coordinates.
   */
  public final void xgramDrawSwitchSheet (android.graphics.Canvas c, int width, int height, @androidx.annotation.ColorInt int color) {
    if (recyclerView == null || !com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return;
    }
    float corner = xgramSheetCorner();
    float top = recyclerView.getTop() + recyclerView.getTranslationY() - xgramSheetBand(corner);
    if (top >= height) {
      return;
    }
    if (xgramSheetTop == null) {
      xgramSheetTop = new com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable();
    }
    xgramSheetTop.set(color, corner);
    xgramSheetTop.setHandleAlpha(corner);
    xgramSheetTop.setBounds(0, (int) top, width, height);
    xgramSheetTop.draw(c);
  }

  /** XGram: called when a tab switch starts and ends (e.g. a map drops its outline clip meanwhile). */
  protected void xgramOnSwitch (boolean switching) {
    // override
  }

  /** XGram: {@link #expandStartHeight} after a tab switch, on the standard spatial spring (snaps with Reduce motion). */
  private void xgramExpandStartHeight (MeasuredAdapterDelegate adapter) {
    this.contentHeight = adapter.measureHeight(-1);
    int newStartHeight = getMaxStartHeight();
    if (newStartHeight <= startHeight) {
      return;
    }
    if (isMoving || animatingHeight || currentHeight > startHeight) {
      startHeight = newStartHeight;
      return;
    }
    final float fromHeight = startHeight;
    final float heightDiff = newStartHeight - startHeight;
    com.fahadali.xgram.appearance.XgramExpressive.Spring spring = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT, 0f, (s, value) -> {
      if (!xgramSwitching && !isMoving && !animatingHeight) {
        applyStartHeight(Math.round(fromHeight + heightDiff * value));
      }
    });
    spring.setThreshold(.5f / Math.max(1f, Math.abs(heightDiff)));
    spring.animateTo(1f);
  }

  public void xgramDrawSheetTop (android.graphics.Canvas c, int width) {
    if (recyclerView == null || recyclerView.getVisibility() != View.VISIBLE || !xgramHasSheetTop() || xgramSwitching) {
      return;
    }
    int color = org.thunderdog.challegram.theme.Theme.getColor(getRecyclerBackgroundColorId());
    float corner = xgramSheetCorner();
    if (corner <= 0f) {
      return;
    }
    float top = recyclerView.getTop() + recyclerView.getTranslationY();
    float band = xgramSheetBand(corner);
    if (xgramSheetTop == null) {
      xgramSheetTop = new com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable();
    }
    xgramSheetTop.set(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(color, recyclerView.getAlpha()), corner);
    xgramSheetTop.setHandleAlpha(corner * recyclerView.getAlpha());
    final boolean listClipped = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    if (listClipped) {
      // the full curve (at least a radius tall), painted only above the list (1px into it, no seam): the clipped list
      // continues the same curve below
      final float radius = com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable.cornerRadius(corner);
      xgramSheetTop.setBounds(0, (int) (top - band), width, (int) Math.ceil(top - band + radius) + 1);
      final int saveCount = c.save();
      c.clipRect(0, 0, width, (float) Math.ceil(top) + 1);
      xgramSheetTop.draw(c);
      c.restoreToCount(saveCount);
    } else {
      xgramSheetTop.setBounds(0, (int) (top - band), width, (int) Math.ceil(top) + 1);
      xgramSheetTop.draw(c);
    }
  }

  protected @ColorId int getRecyclerBackgroundColorId () {
    return ColorId.filling;
  }

  public void dispatchRecyclerTouchEvent (MotionEvent e) {
    recyclerView.processEvent(e);
  }

  public int getRecyclerScrollY () {
    if (recyclerView == null) {
      return 0;
    }
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && !recyclerView.canScrollVertically(-1)) {
      return 0; // XGram: at the top, whatever offsets the decorations add (the sheet drag needs exactly 0)
    }
    RecyclerView.LayoutManager manager = getLayoutManager();
    if (!(manager instanceof LinearLayoutManager)) {
      return 0;
    }
    RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
    if (!(adapter instanceof MeasuredAdapterDelegate)) {
      return 0;
    }
    int firstPosition = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
    if (firstPosition == RecyclerView.NO_POSITION) {
      return 0;
    }
    int scrollTop = ((MeasuredAdapterDelegate) adapter).measureScrollTop(firstPosition);
    View view = manager.findViewByPosition(firstPosition);
    if (view != null) {
      scrollTop -= view.getTop();
    }
    return scrollTop;
  }

  protected final void setLayoutManager (RecyclerView.LayoutManager manager) {
    recyclerView.setLayoutManager(manager);
  }

  protected final RecyclerView.LayoutManager getLayoutManager () {
    return recyclerView.getLayoutManager();
  }

  protected final void addItemDecoration (RecyclerView.ItemDecoration decoration) {
    recyclerView.addItemDecoration(decoration);
  }

  protected final void setAdapter (RecyclerView.Adapter<?> adapter) {
    recyclerView.setAdapter(adapter);
  }

  protected final void dispatchError (final String error, final String resolveErrorButtonText, final View.OnClickListener onResolveButtonClick, final boolean animated) {
    runOnUiThread(() -> showError(error, resolveErrorButtonText, onResolveButtonClick, animated));
  }

  public void updateExtraSpacingDecoration () {
    recyclerView.invalidateItemDecorations();
  }

  protected final void showError (@StringRes int errorRes, @StringRes int resolveErrorButtonRes, View.OnClickListener onResolveButtonClick, boolean animated) {
    showError(Lang.getString(errorRes), resolveErrorButtonRes != 0 ? Lang.getString(resolveErrorButtonRes) : null, onResolveButtonClick, animated);
  }

  protected void hideError () {
    if (emptyView != null) {
      emptyView.setAlpha(0f);
    }
    if (xgramEmptyView != null) {
      xgramEmptyView.setAlpha(0f);
    }
  }

  // XGram: with Expressive design the empty / error state is the M3E one (XgramListStateView: an icon on a soft burst,
  // a titleLarge headline), as the chat list's, instead of Telegram X's EmptyTextView. It is centred on the visible part
  // of the sheet like the progress view (findYForStaticView with its fixed height).
  private @Nullable com.fahadali.xgram.appearance.XgramListStateView xgramEmptyView;
  private static final float XGRAM_EMPTY_HEIGHT = 216f;

  /** XGram: the icon of this tab's empty / error state. */
  protected @androidx.annotation.DrawableRes int xgramErrorIcon () {
    return org.thunderdog.challegram.R.drawable.baseline_search_96;
  }

  private View xgramShowError (String error) {
    if (xgramEmptyView == null) {
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(XGRAM_EMPTY_HEIGHT), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
      params.topMargin = HeaderView.getSize(false);
      xgramEmptyView = new com.fahadali.xgram.appearance.XgramListStateView(context());
      xgramEmptyView.setLayoutParams(params);
      xgramEmptyView.setTranslationY(findYForStaticView(Screen.dp(XGRAM_EMPTY_HEIGHT)));
      addThemeInvalidateListener(xgramEmptyView);
      contentView.addView(xgramEmptyView);
    }
    xgramEmptyView.setEmpty(xgramErrorIcon(), error, null);
    return xgramEmptyView;
  }

  // Called when removed from view tree
  public final void resetState () {
    // force scroll to top
  }

  protected void showError (String error, @Nullable String resolveButtonText, @Nullable View.OnClickListener onResolveButtonClick, boolean animated) {
    final View errorView;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      if (xgramEmptyView != null) {
        animated = false;
      }
      errorView = xgramShowError(error);
    } else {
      if (emptyView == null) {
        FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = HeaderView.getSize(false);

        emptyView = new EmptyTextView(context());
        emptyView.setLayoutParams(params);
        emptyView.setTranslationY(findYForStaticView(Screen.dp(18f)));
        contentView.addView(emptyView);
      } else {
        animated = false;
      }
      emptyView.setText(error);
      errorView = emptyView;
    }
    if (!animated || progressView == null) {
      errorView.setAlpha(1f);
      return;
    }
    errorView.setAlpha(0f);
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> {
      float factor = AnimatorUtils.getFraction(animation);
      if (factor <= .5f) {
        factor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor / .5f);
        progressView.setAlpha(1f - factor);
      } else {
        if (progressView.getAlpha() != 0f) {
          progressView.setAlpha(0f);
        }
        factor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((factor - .5f) / .5f);
        errorView.setAlpha(factor);
      }
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        contentView.removeView(progressView);
        progressView = null;
      }
    });
    animator.setDuration(300l);
    animator.start();
  }

  protected static final long HIDE_PROGRESS_DURATION = 140l;

  protected void hideProgress () {
    hideProgress(null);
  }

  protected void hideProgress (final Runnable after) {
    if (progressView == null) {
      return;
    }
    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> progressView.setAlpha(1f - AnimatorUtils.getFraction(animation)));
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        contentView.removeView(progressView);
        progressView = null;
        if (after != null) {
          after.run();
        }
      }
    });
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(HIDE_PROGRESS_DURATION);
    animator.start();
  }

  private void applyStartHeight (int newStartHeight) {
    if (this.startHeight != newStartHeight) {
      this.startHeight = this.currentHeight = newStartHeight;
      updateRecyclerTop();
      mediaLayout.onContentHeightChanged();
    }
  }

  protected void expandStartHeight (MeasuredAdapterDelegate adapter) {
    if (xgramSwitching) {
      xgramPendingExpand = adapter; // XGram: applied when the tab switch ends, on its spring
      return;
    }
    this.contentHeight = adapter.measureHeight(-1);
    int newStartHeight = getMaxStartHeight();

    if (newStartHeight <= startHeight) {
      return;
    }

    if (isMoving || animatingHeight || currentHeight > startHeight) {
      startHeight = newStartHeight;
      return;
    }

    final float fromHeight = startHeight;
    final float heightDiff = newStartHeight - startHeight;

    ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> applyStartHeight(Math.round(fromHeight + heightDiff * AnimatorUtils.getFraction(animation))));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(150l);
    animator.start();
  }

  private boolean lastIsExpanded;

  private void setCurrentHeight (int height, boolean updateMediaLayout) {
    if (this.currentHeight != height) {
      this.currentHeight = height;

      final int maxHeight = getMaxHeight();

      lastIsExpanded = height == maxHeight;

      if (Math.abs(height - lastHeightIncreaseCheck) >= Screen.getTouchSlop()) {
        isHeightIncreasing = height > lastHeightIncreaseCheck;
        lastHeightIncreaseCheck = height;
      }

      updateRecyclerTop();
      mediaLayout.setContentVisible(currentHeight < maxHeight);

      if (updateMediaLayout) {
        if (currentHeight == startHeight) {
          mediaLayout.setBottomBarFactor(1f);
          mediaLayout.setHeaderFactor(0f);
        } else if (currentHeight < startHeight) {
          mediaLayout.setBottomBarFactor((float) currentHeight / (float) startHeight);
          mediaLayout.setHeaderFactor(0f);
        } else {
          float barFactor = (float) (getMaxHeight() - currentHeight) / (float) (maxHeight - startHeight);
          mediaLayout.setBottomBarFactor(barFactor);
          mediaLayout.setHeaderFactor(1f - barFactor);
        }
      }
    }
  }

  protected void onUpdateBottomBarFactor (float bottomBarFactor, float counterFactor, float y) {
  }

  private boolean animatingHeight;
  private ValueAnimator heightAnimator;

  protected void animateCurrentHeight (int toHeight, boolean fast) {
    if (animatingHeight) {
      animatingHeight = false;
      if (heightAnimator != null) {
        heightAnimator.cancel();
        heightAnimator = null;
      }
    }

    if (currentHeight == toHeight) {
      return;
    }

    animatingHeight = true;

    final float fromHeight = this.currentHeight;
    final float heightDiff = toHeight - fromHeight;

    heightAnimator = AnimatorUtils.simpleValueAnimator();
    heightAnimator.addUpdateListener(animation -> {
      if (animatingHeight) {
        float factor = AnimatorUtils.getFraction(animation);
        setCurrentHeight(Math.round(fromHeight + heightDiff * factor), true);
      }
    });
    heightAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        animatingHeight = false;
      }
    });
    heightAnimator.setInterpolator(com.fahadali.xgram.appearance.XgramMotion.interpClamped(com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR));
    heightAnimator.setDuration(com.fahadali.xgram.appearance.XgramMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT, fast ? 150l : MediaLayout.REVEAL_DURATION));
    heightAnimator.start();
  }

  // Height expanding

  public boolean isAnimating () {
    return animatingHeight || useBottomBarChange || xgramSwitching; // XGram: no drags during a tab switch
  }

  public boolean isInsideRecyclerView (float x, float y) {
    return y >= recyclerView.getTranslationY() && y <= recyclerView.getTranslationY() + recyclerView.getMeasuredHeight();
  }

  public final int getMaxHeight () {
    return getTargetHeight(); //  - HeaderView.getSize();
  }

  private int moveStartHeight;
  private boolean isMoving;

  private boolean useBottomBarChange;
  private int barChangeFromHeight;
  private int barChangeHeightDiff;

  public void onBottomBarAnimationStart (boolean beforeExit) {
    if (beforeExit) {
      useBottomBarChange = true;
      barChangeFromHeight = currentHeight;
      barChangeHeightDiff = -currentHeight;
    }
  }

  public void onBottomBarAnimationFraction (float factor) {
    if (useBottomBarChange) {
      setCurrentHeight(barChangeFromHeight + (int) ((float) barChangeHeightDiff * factor), false);
    }
  }

  public void onBottomBarAnimationComplete () {
    useBottomBarChange = false;
  }

  public void onRecyclerMovementStarted () {
    moveStartHeight = currentHeight;
    isMoving = true;
  }

  public boolean handleFling (boolean toUp) {
    if (isMoving) {
      isMoving = false;
      useBottomBarChange = false;

      float expandFactor = currentHeight < startHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - startHeight);

      mediaLayout.prepareHeader();
      if (toUp) {
        if (canExpandHeight()) {
          animateCurrentHeight(getMaxHeight(), true);
        } else {
          animateCurrentHeight(startHeight, true);
        }
      } else if (expandFactor >= .2f || !canMinimizeHeight()) {
        animateCurrentHeight(startHeight, false);
      } else {
        useBottomBarChange = true;
        barChangeFromHeight = currentHeight;
        barChangeHeightDiff = -currentHeight;
        mediaLayout.hideBottomBarAndDismiss();
      }

      return true;
    }
    return false;
  }

  public void onRecyclerMovementFinished () {
    if (isMoving) {
      isMoving = false;
      useBottomBarChange = false;

      float openFactor = currentHeight >= startHeight ? 1f : (float) currentHeight / (float) startHeight;
      float expandFactor = currentHeight < startHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - startHeight);

      mediaLayout.prepareHeader();

      if (openFactor <= .45f && !isHeightIncreasing) {
        useBottomBarChange = true;
        barChangeFromHeight = currentHeight;
        barChangeHeightDiff = -currentHeight;
        mediaLayout.hideBottomBarAndDismiss();
      } else if (expandFactor >= .35f && isHeightIncreasing) {
        animateCurrentHeight(getMaxHeight(), true);
      } else {
        animateCurrentHeight(startHeight, false);
      }
    }
  }

  private boolean recyclerEverMoved;

  public boolean moveRecyclerView (float diffY) {
    int maxHeight = canExpandHeight() ? getMaxHeight() : startHeight;
    int newHeight = Math.min(maxHeight, moveStartHeight - (int) diffY);

    if (newHeight < startHeight && !canMinimizeHeight()) {
      return false;
    }

    if (currentHeight == newHeight) {
      return newHeight == maxHeight;
    }

    if (!recyclerEverMoved && newHeight > startHeight) {
      recyclerEverMoved = true;
      onRecyclerFirstMovement();
    }

    if (currentHeight > startHeight) {
      mediaLayout.prepareHeader();
    }
    setCurrentHeight(newHeight, true);

    return newHeight == maxHeight;
  }

  // Override

  protected void preload (Runnable after, long timeout) {
    // do heavy load
    if (after != null) {
      after.run();
    }
  }

  @CallSuper
  protected void onRecyclerTopUpdate (float top) {
    if (progressView != null) {
      progressView.setTranslationY(findYForStaticView(progressView.getLayoutParams().height));
    }
    if (emptyView != null) {
      emptyView.setTranslationY(findYForStaticView(Screen.dp(18f)));
    }
    if (xgramEmptyView != null) {
      xgramEmptyView.setTranslationY(findYForStaticView(Screen.dp(XGRAM_EMPTY_HEIGHT)));
    }
    // Use to align overlay views
  }

  public void expandFully () {
    mediaLayout.prepareHeader();
    animateCurrentHeight(getMaxHeight(), false);
  }

  public boolean isExpanded () {
    return currentHeight == getMaxHeight();
  }

  public void collapseToStart () {
    int scrollY = getRecyclerScrollY();
    if (scrollY != 0) {
      com.fahadali.xgram.appearance.XgramMotion.sheetScrollByOr(recyclerView, 0, -scrollY);
    }
    animateCurrentHeight(startHeight, false);
  }

  protected void forceScrollRecyclerToTop () {
    RecyclerView.LayoutManager manager = getLayoutManager();
    if (manager != null && manager instanceof LinearLayoutManager) {
      ((LinearLayoutManager) manager).scrollToPositionWithOffset(0, 0);
    }
  }

  protected void onCompleteShow (boolean isPopup) {
    // Do all heavy work like layout or etc, no animation will lag
  }

  protected void onMultiSendPress (View view, @NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    // Send all selected shit
  }

  protected void addCustomItems (View view, @NonNull List<HapticMenuHelper.MenuItem> hapticItems) {
    // Add specific items
  }

  protected boolean canRemoveMarkdown () {
    return false;
  }

  protected void onCancelMultiSelection () {
    // unselect all selected shit
  }

  protected ViewGroup createCustomBottomBar () {
    return null;
  }

  @Override
  public void destroy () {
    super.destroy();
    if (recyclerView != null) {
      Views.destroyRecyclerView(recyclerView);
    }
  }
}