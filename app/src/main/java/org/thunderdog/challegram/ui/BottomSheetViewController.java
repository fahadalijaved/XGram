package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.LickView;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.widget.FrameLayoutFix;

public abstract class BottomSheetViewController<T> extends ViewPagerController<T> implements
  PopupLayout.TouchSectionProvider, PopupLayout.PopupHeightProvider {

  protected FrameLayoutFix wrapView;
  protected RelativeLayout contentView;
  protected View fixView;
  protected View pagerInFrameLayoutFix;

  protected abstract int getHeaderHeight ();
  protected abstract int getContentOffset ();
  protected abstract @Nullable HeaderView onCreateHeaderView ();
  protected void onBeforeCreateView () {};
  protected void onAfterCreateView () {};

  protected int getHideByScrollBorder () {
    return Screen.dp(150);
  }

  protected final int getHeaderHeight (boolean withOffset) {
    return getHeaderHeight() + (withOffset ? HeaderView.getTopOffset() : 0);
  }

  protected final int getContentMinHeight () {
    return (getTargetHeight() - getHeaderHeight(true) - getContentOffset());
  }

  protected final int getContentVisibleHeight () {
    return getTargetHeight() - (getTopEdge() + getHeaderHeight(true));
  }


  public BottomSheetViewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected View onCreateView (Context context) {
    onBeforeCreateView();

    headerView = onCreateHeaderView();

    contentView = new RelativeLayout(context) {
      @Override
      protected void onDraw (Canvas canvas) {
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          // XGram: Material 3 Expressive: the page color as one modal sheet shape from its top to the bottom, with
          // 28dp top corners and a drag handle behind the (transparent) header. Never a square body under the curve:
          // that showed its corners as notches beside the rounded top (e.g. under the message menu's reaction row).
          xgramDrawSheet(canvas, getMeasuredWidth(), getMeasuredHeight());
          super.onDraw(canvas);
          return;
        }
        canvas.drawRect(0, headerTranslationY, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(getBackgroundColorId())));
        super.onDraw(canvas);
      }

      @Override
      protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
        if (child == pagerInFrameLayoutFix) {
          canvas.save();
          // XGram: never below the header's bottom, where the pages start (the message sheet may have no header row)
          float clipTop = headerTranslationY + (headerView != null ? Math.min(HeaderView.getTopOffset(), getHeaderHeight()) : 0);
          if (headerView != null && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
            // XGram: the pages stay out of the rounded corners (the header band above them is the sheet's own surface)
            clipTop = Math.max(clipTop, xgramSheetTopY() + com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable.cornerRadius(xgramSheetCorners()));
          } else if (headerView == null && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
            // XGram: a sheet without a header (the folder suggestions) starts with its page, which is clipped to the
            // sheet's 28dp top corners instead of a square slab over the rounded sheet
            final float radius = com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable.cornerRadius(xgramSheetCorners());
            if (radius > 0f) {
              xgramPageClipRadii[0] = xgramPageClipRadii[1] = xgramPageClipRadii[2] = xgramPageClipRadii[3] = radius;
              xgramPageClipRect.set(0, Math.max(clipTop, xgramSheetTopY()), getMeasuredWidth(), getMeasuredHeight());
              xgramPageClip.rewind();
              xgramPageClip.addRoundRect(xgramPageClipRect, xgramPageClipRadii, Path.Direction.CW);
              canvas.clipPath(xgramPageClip);
            }
          }
          canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
          boolean result = super.drawChild(canvas, child, drawingTime);
          canvas.restore();
          return result;
        } else {
          return super.drawChild(canvas, child, drawingTime);
        }
      }
    };
    contentView.setWillNotDraw(false);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addThemeInvalidateListener(contentView);

    FrameLayout.LayoutParams fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f));
    fp.topMargin = getHeaderHeight();
    fixView = new View(context);
    ViewSupport.setThemedBackground(fixView, ColorId.background, this);
    fixView.setLayoutParams(fp);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: the content view draws the sheet under the header in one piece, so the strip that hid a gap between
      // header and content is not needed; at the top of a sheet without a header row its square corners peeked out
      // beside the rounded ones
      fixView.setVisibility(View.GONE);
    }

    wrapView = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent e) {
        // XGram: a touch above the sheet goes to the popup (it closes the sheet). The handle band the Expressive sheet
        // draws above its header is part of the sheet: it is not taken here, so a drag on the handle reaches the page
        // under it (and drags the sheet), and a tap on it never closes the sheet (see onTouchEvent).
        boolean b = (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < (getTopEdge() + HeaderView.getTopOffset()) - xgramSheetOverhang());
        return b || super.onInterceptTouchEvent(e);
      }

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        final float edge = getTopEdge() + HeaderView.getTopOffset();
        final float sheetTop = edge - xgramSheetOverhang();
        if (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() >= sheetTop && e.getY() < edge) {
          return true; // XGram: the handle band, where nothing under it took the touch: kept, the sheet stays open
        }
        boolean b = (e.getAction() == MotionEvent.ACTION_DOWN && e.getY() < sheetTop);
        return b && super.onTouchEvent(e);
      }

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          widthMeasureSpec = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidthSpec(widthMeasureSpec); // XGram: at most 640dp wide
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }

      @Override
      public void setLayoutParams (ViewGroup.LayoutParams params) {
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          com.fahadali.xgram.appearance.XgramM3Sheets.centerSheet(params); // XGram: centered when narrower than the window
        }
        super.setLayoutParams(params);
      }

      private int oldHeight = -1;

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        post(() -> {
          final int height = getTargetHeight();
          if (height != oldHeight) {
            invalidateAllItemDecorations();
            boolean disallowKeyboardHide = isDisallowKeyboardHideOnPageScrolled();
            setDisallowKeyboardHideOnPageScrolled(true);
            onPageScrolled(currentMediaPosition, currentPositionOffset, 0);
            setDisallowKeyboardHideOnPageScrolled(disallowKeyboardHide);
            oldHeight = height;
          }
        });
      }

      /*
      @Override
      protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int headerHeight = getHeaderHeight(true);
        int DP = Screen.dp(1);

        canvas.drawRect(0, 0, getMeasuredWidth(), getTargetHeight(), Paints.strokeBigPaint(Color.RED));

        canvas.drawRect(DP, headerHeight, getMeasuredWidth() - DP, headerHeight + getContentOffset(), Paints.strokeBigPaint(Color.GREEN));
        canvas.drawRect(DP, headerHeight + getContentOffset(), getMeasuredWidth() - DP, headerHeight + getContentOffset() + getContentMinHeight(), Paints.strokeBigPaint(Color.GREEN));

        canvas.drawRect(DP * 2, getMeasuredHeight() - getHideByScrollBorder(), getMeasuredWidth() - DP * 2, getMeasuredHeight(), Paints.strokeBigPaint(Color.BLUE));
      }
      */
    };

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = getHeaderHeight() + HeaderView.getTopOffset();
    pagerInFrameLayoutFix = super.onCreateView(context);
    pagerInFrameLayoutFix.setLayoutParams(params);
    contentView.addView(pagerInFrameLayoutFix);

    wrapView.addView(fixView);
    wrapView.addView(contentView);
    if (headerView != null) {
      wrapView.addView(headerView);
    }
    wrapView.setWillNotDraw(false);
    addThemeInvalidateListener(wrapView);
    if (HeaderView.getTopOffset() > 0) {
      lickView = new LickView(context);
      addThemeInvalidateListener(lickView);
      lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
      wrapView.addView(lickView);
    }

    onAfterCreateView();

    return wrapView;
  }

  @Nullable
  protected ViewController<?> findCurrentCachedController () {
    return findCachedControllerByPosition(getViewPager().getCurrentItem());
  }

  @Nullable
  protected ViewController<?> findCachedControllerByPosition (int position) {
    if (getViewPager().getAdapter() instanceof ViewPagerController.ViewPagerAdapter) {
      ViewPagerController.ViewPagerAdapter adapter = (ViewPagerController.ViewPagerAdapter) (getViewPager().getAdapter());
      return adapter.getCachedItemByPosition(position);
    }
    return null;
  }


  protected int getBackgroundColorId () {
    // XGram: Material 3 Expressive: every modal sheet is surfaceContainerLow (ColorId.drawer maps to it), the tone of
    // the option, settings, share and sticker set sheets, so a sheet opened from this one keeps its color
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.drawer : ColorId.background;
  }

  private boolean ignoreAnyPagerScrollEventsBecauseOfMovements;

  protected void setIgnoreAnyPagerScrollEventsBecauseOfMovements (boolean ignore) {
    this.ignoreAnyPagerScrollEventsBecauseOfMovements = ignore;
  }

  protected boolean getIgnoreAnyPagerScrollEventsBecauseOfMovements () {
    return ignoreAnyPagerScrollEventsBecauseOfMovements;
  }

  protected boolean canHideByScroll () {
    return false;
  }

  protected int currentMediaPosition;
  protected float currentPositionOffset;
  protected int checkedPosition = -1, checkedBasePosition = -1;

  protected void invalidateCachedPosition () {
    checkedPosition = -1;
    checkedBasePosition = -1;
  }

  protected float lastHeaderPosition;

  protected void checkHeaderPosition (RecyclerView recyclerView) {
    lastHeaderPosition = Math.max(Views.getRecyclerFirstElementTop(recyclerView), 0) + HeaderView.getTopOffset();
    setHeaderPosition(lastHeaderPosition);
    /*
    View view = null;
    if (recyclerView != null) {
      view = recyclerView.getLayoutManager().findViewByPosition(0);
    }
    int top = HeaderView.getTopOffset();
    if (view != null) {
      top = Math.max(view.getTop() + (recyclerView != null ? recyclerView.getTop() : 0) + HeaderView.getTopOffset(), HeaderView.getTopOffset());
    }

    if (headerView != null) {
      setHeaderPosition(lastHeaderPosition = top);
    }
    */
  }

  protected int getTargetHeight () {
    return context().getRootView().getMeasuredHeight();
  }

  @Override
  public abstract boolean supportsBottomInset ();

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    invalidateAllItemDecorations();
  }

  protected void invalidateAllItemDecorations () {
    for (int i = 0; i < getPagerItemCount(); i++) {
      ViewController<?> c = findCachedControllerByPosition(i);
      if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        RecyclerView customRecyclerView = ((BottomSheetBaseControllerPage) c).getRecyclerView();
        if (customRecyclerView != null) {
          customRecyclerView.invalidateItemDecorations();
        }
      }
    }
  }

  protected float headerBackgroundFactor;
  protected float headerTranslationY;

  protected void setHeaderPosition (float y) {
    y = Math.max(y, HeaderView.getTopOffset());
    headerTranslationY = y;
    float realHeaderOffset = y;
    if (headerView != null) {
      headerView.setTranslationY(realHeaderOffset);
    }
    fixView.setTranslationY(realHeaderOffset);
    contentView.invalidate();
    fixView.invalidate();
    if (lickView != null) {
      final int topOffset = HeaderView.getTopOffset();
      final float top = y - topOffset;
      lickView.setTranslationY(realHeaderOffset - topOffset);
      float factor = top > topOffset ? 0f : 1f - (top / (float) topOffset);
      lickView.setFactor(factor);
      onUpdateLickViewFactor(factor);
      // headerView.getFilling().setShadowAlpha(factor);
      setHeaderBackgroundFactor(factor);
    } else if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      setHeaderBackgroundFactor(headerBackgroundFactor); // XGram: keeps the header transparent over the sheet
    }
  }

  protected void setHeaderBackgroundFactor (float headerBackgroundFactor) {
    this.headerBackgroundFactor = headerBackgroundFactor;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && headerView != null) {
      xgramUpdateSheetTop();
    }
  }

  // XGram: Material 3 Expressive modal sheet: the content view draws the whole sheet (see xgramDrawSheet) for every
  // subclass, MessageOptionsPagerController included (it overrides setHeaderBackgroundFactor and keeps its header
  // transparent the same way)

  private @Nullable com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable xgramSheetTop;
  /** XGram: the rounded clip of a headerless sheet's page (see the content view's drawChild), reused every frame. */
  private final Path xgramPageClip = new Path();
  private final RectF xgramPageClipRect = new RectF();
  private final float[] xgramPageClipRadii = new float[8];

  /** XGram: the sheet's corner factor: 28dp corners until the sheet top nears the status bar, then square (M3 full screen). */
  protected float xgramSheetCorners () {
    return com.fahadali.xgram.appearance.XgramM3Sheets.cornerFactor(headerTranslationY);
  }

  /**
   * XGram: the drag handle's opacity on the sheet drawn by the content view (0 when a subclass draws its own). It fades
   * out with the corners: a sheet docked under the status bar is full screen and has no handle (as the message menu and
   * the attach sheet).
   */
  protected float xgramSheetHandleAlpha () {
    return xgramSheetCorners();
  }

  /**
   * XGram: how far the drawn sheet reaches above {@code getTopEdge() + HeaderView.getTopOffset()}, the edge used for
   * touches and for the slide in and out: the handle band of an Expressive sheet ({@link #xgramSheetBand()} here). A
   * touch in it belongs to the sheet, and the sheet slides that much further, so the band starts and ends off screen.
   */
  protected float xgramSheetOverhang () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? Math.max(0f, xgramSheetBand()) : 0f;
  }

  /**
   * XGram: how far above the header the sheet starts, 0 by default: a band for the drag handle over a header row
   * whose controls leave it no room. Scale it with {@link #xgramSheetCorners()} so it is gone once docked.
   */
  protected float xgramSheetBand () {
    return 0f;
  }

  /** XGram: the top of the sheet drawn by the content view. */
  private float xgramSheetTopY () {
    return headerTranslationY - Math.max(0f, xgramSheetBand());
  }

  /**
   * XGram: the sheet in the page color, one shape from its top (the header's, or the band's above it) to the bottom of
   * the content view: 28dp top corners that square off at the top of the window, and the drag handle.
   */
  private void xgramDrawSheet (Canvas c, int width, int height) {
    if (xgramSheetTop == null) {
      xgramSheetTop = new com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable();
    }
    final int top = (int) xgramSheetTopY();
    xgramSheetTop.set(Theme.getColor(getBackgroundColorId()), xgramSheetCorners());
    xgramSheetTop.setHandleAlpha(xgramSheetHandleAlpha());
    xgramSheetTop.setBounds(0, top, width, Math.max(top, height));
    xgramSheetTop.draw(c);
  }

  /**
   * One tone with the pages: the header loses Telegram X's filling (re-checked on every move, in case a page set it
   * again), so the sheet the content view draws under it shows through. The status bar strip takes the same color.
   */
  private void xgramUpdateSheetTop () {
    final HeaderView header = headerView;
    if (header == null) {
      return;
    }
    if (header.getBackground() != null) {
      header.setBackground(null);
    }
    if (lickView != null) {
      lickView.setHeaderBackground(Theme.getColor(getBackgroundColorId()));
    }
    if (contentView != null) {
      contentView.invalidate();
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && headerView != null) {
      xgramUpdateSheetTop(); // XGram: theme changes
    }
  }

  protected int getTopEdge () {
    return Math.max(0, (int) (headerTranslationY - HeaderView.getTopOffset()));
  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return y < headerTranslationY - (headerView != null ? getHeaderHeight(true) : 0);
  }

  @Override
  public int getCurrentPopupHeight () {
    return (getTargetHeight() - getTopEdge() - (int) ((float) HeaderView.getTopOffset())) + Math.max(wrapView.getMeasuredHeight() - getTargetHeight(), 0)
      + (int) Math.ceil(xgramSheetOverhang()); // XGram: the handle band above the header leaves the screen too
  }

  public int maxItemsScrollYOffset () {
    return maxItemsScrollY();
  }

  public int maxItemsScrollY () {
    return getContentOffset();
  }

  public void checkContentScrollY (BottomSheetBaseControllerPage c) {
    int maxScrollY = maxItemsScrollYOffset();
    int scrollY = (int) (getContentOffset() - headerTranslationY + HeaderView.getTopOffset()); //();
    if (c != null) {
      c.ensureMaxScrollY(scrollY, maxScrollY);
    }
  }

  private static final boolean PREVENT_HEADER_ANIMATOR = false; // TODO

  @Override
  protected boolean launchCustomHeaderTransformAnimator (boolean open, int transformMode, Animator.AnimatorListener listener) {
    return PREVENT_HEADER_ANIMATOR && open && getTopEdge() > 0;
  }

  protected int calculateTotalHeight () {
    return getTargetHeight() - (getContentOffset() + HeaderView.getTopOffset())
      + (int) Math.ceil(xgramSheetOverhang()); // XGram: the sheet opens from fully below the window, its handle band included
  }

  protected void checkContentScrollY (int position) {
    if (getViewPager().getAdapter() instanceof ViewPagerController.ViewPagerAdapter) {
      ViewPagerController.ViewPagerAdapter adapter = (ViewPagerController.ViewPagerAdapter) (getViewPager().getAdapter());
      ViewController<?> controller = adapter.getCachedItemByPosition(position);
      if (controller instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        checkContentScrollY((BottomSheetBaseControllerPage) controller);
      }
    }
  }

  @Override
  protected void setCurrentPagerPosition (int position, boolean animated) {
    if (headerCell != null && animated) {
      headerCell.getTopView().setFromTo(getViewPager().getCurrentItem(), position);
    }
    super.setCurrentPagerPosition(position, animated);
  }

  @Override
  protected String[] getPagerSections () {
    return null;
  }

  @Override
  public View getCustomHeaderCell () {
    return null;
  }

  @Override
  public void destroy () {
    super.destroy();
    context().removeFullScreenView(this, false);
  }



  // PopupLayout

  private PopupLayout popupLayout;
  private boolean openLaunched;
  protected boolean isFirstCreation = true;

  public PopupLayout show () {
    if (tdlib == null) {
      return null;
    }
    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
        isFirstCreation = false;
        if (!isDestroyed()) {
          BottomSheetViewController.this.onCustomShowComplete();
          ViewController<?> c = getCurrentPagerItem();
          if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
            RecyclerView r = ((BottomSheetBaseControllerPage) c).getRecyclerView();
            if (r != null) {
              r.invalidateItemDecorations();
              checkHeaderPosition(r);
            }
          }
        }
      }
    };

    setupPopupLayout(popupLayout);
    getValue();
    context().addFullScreenView(this, false);
    return popupLayout;
  }

  protected void onCustomShowComplete () {

  }

  protected void setupPopupLayout (PopupLayout popupLayout) {
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    popupLayout.setNeedFullScreen(true);
    popupLayout.setTouchProvider(this);
  }

  protected void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(getValue(), calculateTotalHeight());
    }
  }

  public void hidePopupWindow (boolean animated) {
    popupLayout.hideWindow(animated);
  }

  public void setDismissListener (PopupLayout.DismissListener l) {
    popupLayout.setDismissListener(l);
  }

  protected final PopupLayout getPopupLayout () {
    return popupLayout;
  }



  // LickView

  private @Nullable LickView lickView;

  protected float getLickViewFactor () {
    return lickView != null ? lickView.getFactor() : 0;
  }

  protected void onUpdateLickViewFactor (float factor) {

  }

  protected void setLickViewColor (@ColorInt int color) {
    if (lickView != null) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        color = Theme.getColor(getBackgroundColorId()); // XGram: the status bar strip continues the Expressive sheet
      }
      lickView.setHeaderBackground(color);
    }
  }





  // Custom tooltip manager, used in case the popup window is opened on top of all content.
  // Might be worth moving this to the ViewController class

  private TooltipOverlayView tooltipOverlayView;

  public TooltipOverlayView tooltipManager () {
    if (tooltipOverlayView == null) {
      tooltipOverlayView = new TooltipOverlayView(context());
      tooltipOverlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      tooltipOverlayView.setAvailabilityListener((overlayView, hasChildren) -> {
        if (hasChildren) {
          if (tooltipOverlayView.getParent() != null)
            return;
          addToViewController(tooltipOverlayView);
        } else {
          removeFromViewController(tooltipOverlayView);
        }
      });
    }
    return tooltipOverlayView;
  }

  public void addToViewController (View view) {
    wrapView.addView(view);
  }

  public void removeFromViewController (View view) {
    wrapView.removeView(view);
  }



  // Default listeners ind decorators

  protected void setDefaultListenersAndDecorators (BottomSheetBaseControllerPage controller) {
    RecyclerView recyclerView = controller.getRecyclerView();
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.addOnScrollListener(new ScrollListener(this));
    recyclerView.addItemDecoration(new ContentDecoration(this, controller));
    recyclerView.addOnLayoutChangeListener(new LayoutChangeListener(this));
    addThemeInvalidateListener(recyclerView);
    checkContentScrollY(controller);
    if (canHideByScroll()) {
      UI.post(() -> recyclerView.scrollBy(0, getContentMinHeight() + getHeaderHeight()));
    }
  }

  public static class ContentDecoration extends RecyclerView.ItemDecoration {
    private final BottomSheetViewController<?> controller;
    private final BottomSheetBaseControllerPage page;

    public ContentDecoration (BottomSheetViewController<?> controller, BottomSheetBaseControllerPage page) {
      this.controller = controller;
      this.page = page;
    }

    @Override
    public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, RecyclerView parent, @NonNull RecyclerView.State state) {
      final int position = parent.getChildAdapterPosition(view);
      final int itemCount = parent.getAdapter().getItemCount();
      final boolean isUnknown = position == RecyclerView.NO_POSITION;
      int top = 0, bottom = 0;

      if (position == 0 || isUnknown) {
        top = controller.canHideByScroll() ?
          (controller.getTargetHeight() - HeaderView.getTopOffset() - (Settings.instance().useEdgeToEdge() ? controller.context().getRootView().getSystemInsetsWithoutIme().bottom : 0)):
          (controller.getContentOffset());
      }
      if (position == itemCount - 1 || isUnknown) {
        final int itemsHeight = isUnknown ? view.getMeasuredHeight() : page.getItemsHeight(parent);
        final int parentHeight = parent.getMeasuredHeight() - parent.getPaddingBottom();
        bottom = parentHeight - itemsHeight;
      }

      outRect.set(
        0, page.needTopDecorationOffsets(parent) ? Math.max(top, 0) : 0,
        0, page.needBottomDecorationOffsets(parent) ? Math.max(0, bottom) : 0);
    }
  }

  public static class ScrollListener extends RecyclerView.OnScrollListener {
    private final BottomSheetViewController<?> controller;

    public ScrollListener (BottomSheetViewController<?> controller) {
      this.controller = controller;
    }

    private boolean ignoreScrollChangeState;

    @Override
    public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
      super.onScrollStateChanged(recyclerView, newState);
      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        if (ignoreScrollChangeState) {
          ignoreScrollChangeState = false;
          return;
        }

        ViewController<?> c = controller.findCurrentCachedController();
        boolean canHideByScroll = controller.canHideByScroll();
        int contentOffset = controller.getContentOffset();
        int topEdge = controller.getTopEdge();

        if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
          BottomSheetBaseControllerPage ci = (BottomSheetBaseControllerPage) c;
          if (ci.getRecyclerView() == recyclerView && !controller.getIgnoreAnyPagerScrollEventsBecauseOfMovements()) {
            if (controller.getLickViewFactor() != 0f && controller.getLickViewFactor() != 1f) {
              ci.onScrollToTopRequested();
            } else if (canHideByScroll && (topEdge > contentOffset)) {
              if (controller.getContentVisibleHeight() > controller.getHideByScrollBorder()) {
                com.fahadali.xgram.appearance.XgramMotion.sheetScrollByOr(ci.getRecyclerView(), 0, topEdge - contentOffset);
                ignoreScrollChangeState = true;
              } else {
                controller.hidePopupWindow(true);
              }
            }
          }
        }
      }
    }

    @Override
    public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
      ViewController<?> c = controller.findCurrentCachedController();
      boolean canHideByScroll = controller.canHideByScroll() && recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING;
      int contentOffset = controller.getContentOffset();
      int topEdge = controller.getTopEdge();

      if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        BottomSheetBaseControllerPage ci = (BottomSheetBaseControllerPage) c;
        if (ci.getRecyclerView() == recyclerView && (!controller.getIgnoreAnyPagerScrollEventsBecauseOfMovements())) {
          controller.checkHeaderPosition(recyclerView);
          if (canHideByScroll && (topEdge > contentOffset)) {
            if (!(controller.getContentVisibleHeight() > controller.getHideByScrollBorder())) {
              controller.hidePopupWindow(true);
            }
          }
        }
      }
    }
  }

  public static class LayoutChangeListener implements View.OnLayoutChangeListener {
    private final BottomSheetViewController<?> controller;

    public LayoutChangeListener (BottomSheetViewController<?> controller) {
      this.controller = controller;
    }

    @Override
    public void onLayoutChange (View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
      ViewController<?> c = controller.findCurrentCachedController();
      if (c instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        BottomSheetBaseControllerPage ci = (BottomSheetBaseControllerPage) c;
        if (ci.getRecyclerView() == view && controller.currentPositionOffset == 0f) {
          controller.checkHeaderPosition((RecyclerView) view);
        }
      }
    }
  }



  //

  public interface BottomSheetBaseControllerPage {
    void onScrollToTopRequested ();
    void onScrollToBottomRequested ();
    void ensureMaxScrollY (int scrollY, int maxScrollY);
    RecyclerView getRecyclerView ();

    int getItemsHeight (RecyclerView parent);
    boolean needTopDecorationOffsets (RecyclerView parent);
    boolean needBottomDecorationOffsets (RecyclerView parent);
  }



  //

  public static abstract class BottomSheetBaseRecyclerViewController<T> extends RecyclerViewController<T> implements BottomSheetBaseControllerPage {
    public BottomSheetBaseRecyclerViewController (Context context, Tdlib tdlib) {
      super(context, tdlib);
    }

    public abstract int getItemsHeight (RecyclerView parent);

    public boolean needTopDecorationOffsets (RecyclerView parent) {
      return true;
    }

    public boolean needBottomDecorationOffsets (RecyclerView parent) {
      return true;
    }

    @Override
    public abstract boolean supportsBottomInset ();

    public final void ensureMaxScrollY (int scrollY, int maxScrollY) {
      CustomRecyclerView recyclerView = getRecyclerView();
      if (recyclerView != null) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (scrollY < maxScrollY) {
          manager.scrollToPositionWithOffset(0, -scrollY);
          return;
        }

        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == 0 || firstVisiblePosition == -1) {
          View view = manager.findViewByPosition(0);
          if (view != null) {
            int top = view.getTop();
            if (top > 0) {
              manager.scrollToPositionWithOffset(0, -maxScrollY);
            }
          } else {
            manager.scrollToPositionWithOffset(0, -maxScrollY);
          }
        }
      }
    }

    public void onScrollToBottomRequested () {
      CustomRecyclerView recyclerView = getRecyclerView();
      if (recyclerView == null) {
        return;
      }

      recyclerView.stopScroll();
      recyclerView.smoothScrollToPosition(0);
    }
  }
}
