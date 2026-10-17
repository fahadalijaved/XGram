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
 * File created on 06/12/2016
 */
package org.thunderdog.challegram.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMotion;
import com.fahadali.xgram.appearance.XgramSheetDrag;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.MenuMoreWrapAbstract;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.RootDrawable;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.SensitiveContentContainer;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class PopupLayout extends RootFrameLayout implements FactorAnimator.Target, BaseActivity.ActivityListener, Keyboard.OnStateChangeListener, ActivityResultHandler, Destroyable, NestedScrollingParent3 {
  public interface TouchSectionProvider {
    boolean shouldTouchOutside (float x, float y);
  }

  private @Nullable PopupWindow window;
  private @Nullable View windowAnchorView;
  private @Nullable BaseActivity.ActivityListener activityListener;
  private @Nullable TouchSectionProvider touchSectionProvider;
  private @Nullable Keyboard.OnStateChangeListener keyboardListener;

  private boolean overlayStatusBar;

  public interface DismissListener {
    void onPopupDismiss (PopupLayout popup);
    default void onPopupDismissPrepare (PopupLayout popup) { }
  }

  public interface ShowListener {
    void onPopupCompletelyShown (PopupLayout popup);
  }

  private @Nullable DismissListener dismissListener;
  private @Nullable ShowListener showListener;
  private @Nullable BackListener backListener;

  public PopupLayout (Context context) {
    super(context);
    setKeyboardListener(this);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    backgroundView = new BackgroundView(context);
    backgroundView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(backgroundView);
  }

  public void setOverlayStatusBar (boolean overlayStatusBar) {
    this.overlayStatusBar = overlayStatusBar;
  }

  public boolean shouldOverlayStatusBar () {
    return overlayStatusBar;
  }

  public void setBackListener (@Nullable BackListener backListener) {
    this.backListener = backListener;
  }

  public void setDismissListener (@Nullable DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public void setShowListener (@Nullable ShowListener showListener) {
    this.showListener = showListener;
  }

  public void setKeyboardChangeListener (@Nullable Keyboard.OnStateChangeListener keyboardListener) {
    this.keyboardListener = keyboardListener;
  }

  private boolean isKeyboardVisible;

  @Override
  public void onKeyboardStateChanged (boolean isVisible) {
    if (this.isKeyboardVisible != isVisible) {
      this.isKeyboardVisible = isVisible;
      if (keyboardListener != null) {
        keyboardListener.onKeyboardStateChanged(isVisible);
      } else if (boundController != null) {
        boundController.onKeyboardStateChanged(isVisible);
      }
    }
  }

  @Override
  public void closeAdditionalKeyboards () {
    if (keyboardListener != null) {
      keyboardListener.closeAdditionalKeyboards();
    } else if (boundController != null && boundController instanceof Keyboard.OnStateChangeListener) {
      ((Keyboard.OnStateChangeListener) boundController).closeAdditionalKeyboards();
    }
  }

  public void setActivityListener (@Nullable BaseActivity.ActivityListener activityListener) {
    this.activityListener = activityListener;
  }

  @Override
  public void onActivityPause () {
    if (activityListener != null) {
      activityListener.onActivityPause();
    }
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (boundController instanceof ActivityResultHandler) {
      ((ActivityResultHandler) boundController).onActivityResult(requestCode, resultCode, data);
    } else if (boundView instanceof ActivityResultHandler) {
      ((ActivityResultHandler) boundView).onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onActivityResume () {
    if (activityListener != null) {
      activityListener.onActivityResume();
    }
  }

  @Override
  public void onActivityDestroy () {
    if (activityListener != null) {
      activityListener.onActivityDestroy();
    }
    dismissWindow();
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {
    if (activityListener != null) {
      activityListener.onActivityPermissionResult(code, granted);
    }
  }

  public void setTouchProvider (@Nullable TouchSectionProvider touchProvider) {
    this.touchSectionProvider = touchProvider;
  }

  private boolean needRootInsets;

  public void setNeedRootInsets () {
    this.needRootInsets = true;
  }

  private boolean needFullScreen;

  public void setNeedFullScreen (boolean needFullScreen) {
    this.needFullScreen = needFullScreen;
  }

  private boolean hideKeyboard;

  public void setHideKeyboard () {
    this.hideKeyboard = true;
  }

  public boolean canHideKeyboard () {
    return needRootInsets || (softInputMode != 0 && isKeyboardVisible);
  }

  public boolean hideSoftwareKeyboard () {
    if (boundController != null) {
      if (boundController.getKeyboardState() || (boundController instanceof MediaViewController && ((MediaViewController) boundController).isEmojiVisible())) {
        boundController.hideSoftwareKeyboard();
        return true;
      }
    }
    return false;
  }

  private ViewController<?> boundController;

  public boolean performOnBackPressed (boolean byHeaderBackPress, boolean commit) {
    return (backListener != null && backListener.onBackPressed(byHeaderBackPress, commit)) || (boundController != null && boundController.performOnBackPressed(false, commit)) || (boundView != null && boundView instanceof BackListener && ((BackListener) boundView).onBackPressed(byHeaderBackPress, commit));
  }

  public void setBoundController (ViewController<?> boundController) {
    this.boundController = boundController;
  }

  public ViewController<?> getBoundController () {
    return boundController;
  }

  private boolean isTemporarilyHidden;

  private void removeWindow () {
    if (needRootInsets) {
      UI.getContext(getContext()).removeFromRoot(this);
    } else if (window != null) {
      try {
        window.dismiss();
      } catch (Throwable ignored) { }
    }
  }

  public void hideTemporarily () {
    if (!isTemporarilyHidden) {
      isTemporarilyHidden = true;
      removeWindow();
    }
  }

  public void restoreIfHidden () {
    if (isTemporarilyHidden) {
      isTemporarilyHidden = false;
      if (needRootInsets) {
        UI.getContext(getContext()).addToRoot(this, shouldOverlayStatusBar());
      } else if (windowAnchorView != null) {
        showSystemWindow(windowAnchorView);
      }
    }
  }

  public boolean dismissOtherPopUps = true;

  public void setDismissOtherPopUps (boolean dismissOtherPopUps) {
    this.dismissOtherPopUps = dismissOtherPopUps;
  }

  public boolean needDismissOtherPopUps () {
    return dismissOtherPopUps;
  }

  private static boolean patchPopupWindow (View container, boolean needFullScreen, boolean disallowScreenshots) {
    WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
    int newFlags = p.flags;
    newFlags = BitwiseUtils.setFlag(newFlags, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, needFullScreen);
    newFlags = BitwiseUtils.setFlag(newFlags, WindowManager.LayoutParams.FLAG_SECURE, disallowScreenshots);
    if (p.flags != newFlags) {
      p.flags = newFlags;
      return true;
    }
    return false;
  }

  public void checkWindowFlags () {
    if (isHidden || isDismissed || windowAnchorView == null || isTemporarilyHidden)
      return;
    if (window != null) {
      View rootView = window.getContentView().getRootView();
      ViewGroup.LayoutParams layoutParams = rootView.getLayoutParams();
      boolean disallowScreenShots = shouldDisallowScreenshots();
      if (!(layoutParams instanceof WindowManager.LayoutParams) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !rootView.isAttachedToWindow())) {
        // TODO: analyze in what situations container parameters become `android.widget.FrameLayout$LayoutParams`
        // after that, uncomment code below, if it's caused by root view, not by window detachment
        /*int windowFlags =
          (needFullScreen ? WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS : 0) |
          (disallowScreenShots ? WindowManager.LayoutParams.FLAG_SECURE : 0);
        if (windowFlags != 0) {
          final BaseActivity context = UI.getContext(getContext());
          WindowManager.LayoutParams newParams = new WindowManager.LayoutParams();
          newParams.flags = windowFlags;
          // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
          context.getWindowManager().updateViewLayout(rootView, newParams);
        }*/
        return;
      }
      if (patchPopupWindow(rootView, needFullScreen, disallowScreenShots)) {
        final BaseActivity context = UI.getContext(getContext());
        // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
        context.getWindowManager().updateViewLayout(rootView, rootView.getLayoutParams());
      }
    }
  }

  private void showSystemWindow (View anchorView) {
    final BaseActivity context = UI.getContext(getContext());
    window = new PopupWindow(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    if (softInputMode != 0) {
      window.setSoftInputMode(softInputMode);
      window.setFocusable(true);
      window.setOutsideTouchable(false);
    } else {
      window.setFocusable(false);
      window.setOutsideTouchable(true);
    }
    UI.post(() -> {
      if (isHidden || isDismissed) {
        return;
      }
      int state = context.getActivityState();
      if (state == UI.State.RESUMED) {
        try {
          window.showAtLocation(windowAnchorView = anchorView, Gravity.NO_GRAVITY, 0, 0);
          window.setBackgroundDrawable(new RootDrawable(UI.getContext(getContext())));
          View rootView = window.getContentView().getRootView();
          boolean updated = patchPopupWindow(rootView, needFullScreen, shouldDisallowScreenshots());
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) rootView.getLayoutParams();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            updated = true;
          }
          if (updated) {
            // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
            context.getWindowManager().updateViewLayout(rootView, rootView.getLayoutParams());
          }
          return;
        } catch (Throwable t) {
          Log.e("Cannot show window", t);
        }
        return;
      }
      context.addSimpleStateListener(new BaseActivity.SimpleStateListener() {
        @Override
        public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
          if (isHidden || isDismissed) {
            context.removeSimpleStateListener(this);
            return;
          }
          if (newState == UI.State.RESUMED) {
            context.removeSimpleStateListener(this);
            if (!isTemporarilyHidden) {
              showSystemWindow(anchorView);
            }
          }
        }
      });
    });
  }

  public void showBoundWindow (View anchorView) {
    if (hideKeyboard || Device.HIDE_POPUP_KEYBOARD) {
      ViewController<?> c = UI.getContext(getContext()).navigation().getCurrentStackItem();
      if (c != null) {
        c.hideSoftwareKeyboard();
      }
    }
    // context.hideContextualPopups();
    if (needRootInsets) {
      UI.getContext(getContext()).addToRoot(this, shouldOverlayStatusBar());
    } else {
      showSystemWindow(anchorView);
    }

    runOnceViewBecomesReady(this, () -> {
      if (!isHidden) {
        launchRevealAnimation();
      }
    });
  }

  private int softInputMode;

  public void setSoftInputMode (int method) {
    this.softInputMode = method;
  }

  public boolean isBoundWindowShowing () {
    return needRootInsets ? getParent() != null : window != null && window.isShowing();
  }

  private boolean isDismissed;

  public void onCustomHideAnimationComplete () {
    dismissWindow();
  }

  private void dismissWindow () {
    if (!isDismissed) {
      isDismissed = true;
      ((BaseActivity) getContext()).removeWindowFromList(this);
      removeWindow();

      if (dismissListener != null) {
        dismissListener.onPopupDismiss(this);
      }

      performDestroy();
    }
  }

  private boolean isDestroyed;

  public boolean isDestroyed () {
    return isDestroyed;
  }

  @Override
  public void performDestroy () {
    isDestroyed = true;
    xgramCancelSprings();
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      } else if (view instanceof ViewGroup) {
        Views.destroy((ViewGroup) view);
      }
      removeViewAt(i);
    }
    if (boundController != null) {
      boundController.destroy();
    }
  }

  @Override
  public View getChildAt (int index) {
    return super.getChildAt(index);
  }

  private boolean disableCancelOnTouchDown;

  public void setDisableCancelOnTouchDown (boolean disable) {
    this.disableCancelOnTouchDown = disable;
  }

  public interface TouchDownInterceptor {
    boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e);
  }

  private TouchDownInterceptor touchDownInterceptor;

  public void setTouchDownInterceptor (TouchDownInterceptor touchDownInterceptor) {
    this.touchDownInterceptor = touchDownInterceptor;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (xgramDragging) {
      xgramOnDragEvent(e); // XGram M3E: the sheet follows the finger
      return true;
    }
    if (XgramExpressive.isEnabled()) {
      if (xgramDragTracking && xgramSheet() != null) {
        return xgramOnSheetTouch(e); // XGram M3E: on the sheet, not on the scrim
      }
      return xgramOnScrimTouch(e);
    }
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      if (factor == 1f && !disableCancelOnTouchDown) {
        if (touchDownInterceptor == null || !touchDownInterceptor.onBackgroundTouchDown(this, e))
          hideWindow(true);
      } else {
        return !(touchSectionProvider == null || touchSectionProvider.shouldTouchOutside(e.getX(), e.getY()));
      }
    }
    return true;
  }

  // XGram M3E: the scrim closes the popup when the finger lifts (as a platform dialog's outside touch), and the bottom
  // sheets follow a vertical drag through XgramSheetDrag, the one sheet drag behaviour of the app (the attach sheet's
  // height drag goes by the same rules): option, confirm, info, picker and settings sheets, and the sheets that say
  // more about themselves through XgramSheetDrag.Sheet (the message menu with its reaction and viewer pages, the
  // BottomSheetViewController sheets, share, sticker set and emoji pack sheets, the embed preview). A drag starts on the
  // handle band, the title or static rows, or on a list once it is at its top; a drag that starts on a sheet's list
  // while it is scrolled reaches the sheet when the list gets to its top (nested scrolling, touch only), and the sheet
  // comes back up before the list scrolls again. On a sheet that grows with its list, a drag on its handle band or title
  // scrolls that list first (the sheet grows and shrinks), then moves the sheet. The scrim fades as the sheet goes
  // down. Released, the sheet closes (XgramSheetDrag.target: a fling down, or below half its height) on the standard
  // spatial fast spring with the finger's velocity, or springs back (SPATIAL_DEFAULT). Reduce motion snaps. Nothing
  // with Expressive design off, and nothing for a popup that disables cancelling on touch.

  private boolean xgramScrimPending;
  private int xgramSheetDrag = -1; // -1: by the kind of sheet, 0: off, 1: on
  private final XgramSheetDrag.Tracker xgramTracker = new XgramSheetDrag.Tracker();
  private boolean xgramGesture; // the touch started while the sheet could follow a drag
  private int xgramDispatchAction = -1;
  private boolean xgramDragTracking, xgramDragging, xgramDragDismiss, xgramDragUp;
  private float xgramDragOriginY, xgramDragLastY, xgramDragStartTranslation, xgramDragFling;
  private @Nullable View xgramDownScroller;
  private @Nullable RecyclerView xgramDragList;
  private @Nullable XgramMotion.Value xgramDragSpring;
  private boolean xgramNested, xgramNestedMoving;
  private final int[] xgramNestedConsumed = new int[2];
  private float xgramScrimScale = 1f;

  /** Turns drag to dismiss on or off for this popup's bottom sheet (by default: the sheets it knows, see xgramCanDragSheet). */
  public void xgramSetSheetDrag (boolean enabled) {
    this.xgramSheetDrag = enabled ? 1 : 0;
  }

  /**
   * XGram M3E: the share of the scrim that shows (1 by default) while a sheet moves away by other means than this
   * popup's own slide: a sheet that hides by scrolling, the attach sheet's height. Multiplies the reveal factor.
   */
  public void xgramSetScrimScale (float scale) {
    scale = XgramMotion.clamp01(scale);
    if (this.xgramScrimScale != scale) {
      this.xgramScrimScale = scale;
      if (XgramExpressive.isEnabled()) {
        backgroundView.setAlpha(XgramMotion.clamp01(factor) * scale);
      }
    }
  }

  /** Whether {@link #getBoundView()} is the settings choice sheet (ViewController.showSettings): a full-height frame holding the list. */
  private boolean xgramIsSettingsSheet () {
    return popupHeightProvider != null && !(popupHeightProvider instanceof View) && !(popupHeightProvider instanceof ViewController) &&
      boundController == null && boundView instanceof RootFrameLayout.MarginModifier && boundView instanceof ViewGroup &&
      ((ViewGroup) boundView).getChildCount() > 0 && ((ViewGroup) boundView).getChildAt(0) instanceof androidx.recyclerview.widget.RecyclerView;
  }

  /**
   * The sheet when it tells more about itself: the bound controller, the height or touch provider (a
   * BottomSheetViewController whose bound controller is its page) or the bound view.
   */
  @Nullable
  private XgramSheetDrag.Sheet xgramSheet () {
    if (boundController instanceof XgramSheetDrag.Sheet) {
      return (XgramSheetDrag.Sheet) boundController;
    }
    if (popupHeightProvider instanceof XgramSheetDrag.Sheet) {
      return (XgramSheetDrag.Sheet) popupHeightProvider;
    }
    if (touchSectionProvider instanceof XgramSheetDrag.Sheet) {
      return (XgramSheetDrag.Sheet) touchSectionProvider;
    }
    return boundView instanceof XgramSheetDrag.Sheet ? (XgramSheetDrag.Sheet) boundView : null;
  }

  private boolean xgramCanDragSheet () {
    if (!XgramExpressive.isEnabled() || animationType != ANIMATION_TYPE_SIMPLE || boundView == null || isHidden || isDismissed ||
      disableCancelOnTouchDown || !revealAnimationLaunched || xgramPendingReveal != null || xgramSheetDrag == 0) {
      return false;
    }
    return xgramSheetDrag == 1 || boundView instanceof OptionsLayout || xgramIsSettingsSheet() || xgramSheet() != null;
  }

  /** The deepest view under ({@code x}, {@code y}) (in {@code view}'s coordinates) that can scroll vertically now, or null. */
  @Nullable
  private static View xgramFindVerticalScroller (View view, float x, float y) {
    if (view instanceof ViewGroup) {
      final ViewGroup group = (ViewGroup) view;
      for (int i = group.getChildCount() - 1; i >= 0; i--) {
        final View child = group.getChildAt(i);
        if (child == null || child.getVisibility() != View.VISIBLE) {
          continue;
        }
        final float cx = x + group.getScrollX() - child.getLeft() - child.getTranslationX();
        final float cy = y + group.getScrollY() - child.getTop() - child.getTranslationY();
        if (cx >= 0f && cy >= 0f && cx < child.getWidth() && cy < child.getHeight()) {
          final View found = xgramFindVerticalScroller(child, cx, cy);
          if (found != null) {
            return found;
          }
          break; // only the topmost child under the finger takes the touch
        }
      }
    }
    return view.canScrollVertically(1) || view.canScrollVertically(-1) ? view : null;
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent e) {
    // XGram M3E: the finger's velocity for every sheet release (the popup's own drag and a list's nested one)
    final int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      xgramGesture = xgramCanDragSheet();
      if (xgramGesture) {
        xgramTracker.down(e);
      } else {
        xgramTracker.end();
      }
    } else if (xgramGesture) {
      xgramTracker.add(e);
    }
    xgramDispatchAction = action;
    final boolean result = super.dispatchTouchEvent(e);
    xgramDispatchAction = -1;
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      xgramGesture = false;
      xgramTracker.end();
    }
    return result;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (xgramInterceptSheetDrag(e)) {
      return true;
    }
    return super.onInterceptTouchEvent(e);
  }

  private boolean xgramInterceptSheetDrag (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        xgramDragging = false;
        xgramDownScroller = null;
        xgramDragList = null;
        xgramDragTracking = xgramGesture;
        if (xgramDragTracking) {
          final View sheet = boundView;
          final float x = e.getX() - sheet.getLeft() - sheet.getTranslationX(), y = e.getY() - sheet.getTop() - sheet.getTranslationY();
          final XgramSheetDrag.Sheet provider = xgramSheet();
          if (provider != null) {
            if (x < 0f || x >= sheet.getWidth() || !provider.xgramSheetContains(x, y)) {
              xgramDragTracking = false; // the scrim above the sheet, or beside it (a sheet narrower than the window)
            } else {
              xgramDragList = provider.xgramSheetList();
              xgramDownScroller = xgramDragList != null && provider.xgramSheetOnList(x, y) && XgramSheetDrag.isUnder(xgramDragList, sheet, x, y) ? xgramDragList : null;
              xgramDragUp = xgramDragList == null || provider.xgramSheetGrabsUp(x, y);
            }
          } else {
            xgramDownScroller = xgramFindVerticalScroller(sheet, x, y);
            xgramDragUp = true;
          }
        }
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        if (xgramDragging) {
          return true;
        }
        return xgramDragTracking && xgramCheckDragStart(e);
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (!xgramDragging) {
          xgramDragTracking = false;
          xgramDownScroller = null;
        }
        return false;
      }
    }
    return false;
  }

  /** Starts the sheet drag once the finger moved past the slop in a direction the sheet follows from where it landed. */
  private boolean xgramCheckDragStart (MotionEvent e) {
    final float dx = xgramTracker.dx(e), dy = xgramTracker.dy(e);
    final int slop = xgramTracker.slop(getContext());
    if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
      final View scroller = xgramDownScroller;
      final boolean start;
      if (scroller != null) {
        // on a list: it scrolls, unless it is at its top and the drag goes down (or, on a sheet of a fixed height,
        // it cannot scroll at all: the rubber band)
        start = dy > 0f ? XgramSheetDrag.atTop(scroller) : xgramDragList == null && !scroller.canScrollVertically(-1) && !scroller.canScrollVertically(1);
      } else {
        // the handle band, the title or static rows: down always, up where the sheet grows from there
        start = dy > 0f || xgramDragUp;
      }
      if (start) {
        xgramStartDrag(e.getY());
        return true;
      }
      xgramDragTracking = false; // the list scrolls this gesture
    } else if (Math.abs(dx) > slop) {
      xgramDragTracking = false; // a sideways gesture
    }
    return false;
  }

  private void xgramStopDragSpring () {
    if (xgramDragSpring != null) {
      xgramDragSpring.setEndListener(null);
      xgramDragSpring.cancel();
    }
  }

  private void xgramStartDrag (float y) {
    xgramDragging = true;
    xgramDragTracking = false;
    xgramDownScroller = null;
    if (xgramDragList != null) {
      // The list never saw this gesture's DOWN: its fling or settling scroll would go on under the finger
      xgramDragList.stopScroll();
    }
    final XgramSheetDrag.Sheet provider = xgramSheet();
    if (provider != null) {
      provider.xgramSheetDragStarted();
    }
    xgramBeginSheetMove();
    xgramDragStartTranslation = xgramSheetTranslation();
    xgramDragOriginY = xgramDragLastY = y;
    final ViewParent parent = getParent();
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(true);
    }
  }

  /** Takes the sheet from its springs: it moves on from where it is now. */
  private void xgramBeginSheetMove () {
    if (xgramShowSpring != null) {
      xgramShowSpring.setEndListener(null);
      xgramShowSpring.cancel();
      xgramShowSpring = null;
    }
    xgramStopDragSpring();
    xgramRebaseHeight();
  }

  /**
   * Measures the sheet as the hide animation will (launchHideAnimation) and keeps its translation, so neither a move
   * nor a release makes it jump (the height of a sheet that grows with its list changes as the list scrolls).
   */
  private void xgramRebaseHeight () {
    final View content = getContentChild();
    final float translation = xgramSheetTranslation();
    currentHeight = Math.max(1, popupHeightProvider != null ? popupHeightProvider.getCurrentPopupHeight() : content != null ? content.getMeasuredHeight() : 1);
    setRevealFactor(1f - translation / currentHeight);
  }

  /** How far the sheet is below its place (px; negative in the rubber band above it). */
  private float xgramSheetTranslation () {
    return currentHeight * (1f - factor);
  }

  private void xgramSetSheetTranslation (float translation) {
    setRevealFactor(1f - translation / Math.max(1, currentHeight));
  }

  private void xgramOnDragEvent (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE: {
        final float y = e.getY();
        final RecyclerView list = xgramDragList;
        if (list == null) {
          // a sheet of a fixed height follows the finger, with a rubber band upwards (an option sheet paints 24dp
          // below its bottom)
          float translation = xgramDragStartTranslation + (y - xgramDragOriginY);
          if (translation < 0f) {
            translation = -XgramSheetDrag.rubber(-translation);
          }
          xgramSetSheetTranslation(translation);
        } else {
          // a sheet that grows with its list: downwards the list goes back to its top first, upwards the sheet comes
          // back up first; the rest moves the other one
          final float delta = y - xgramDragLastY;
          float translation = Math.max(0f, xgramSheetTranslation());
          if (delta > 0f) {
            if (translation <= 0f && list.canScrollVertically(-1)) {
              list.scrollBy(0, -Math.round(delta));
            } else {
              translation += delta;
            }
          } else if (delta < 0f) {
            final float back = Math.min(translation, -delta);
            translation -= back;
            final int rest = Math.round(-delta - back);
            if (rest > 0 && translation <= 0f) {
              list.scrollBy(0, rest);
            }
          }
          xgramSetSheetTranslation(translation);
        }
        xgramDragLastY = y;
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        xgramDragging = false;
        final boolean commit = e.getActionMasked() == MotionEvent.ACTION_UP;
        final float velocity = xgramTracker.velocityY();
        final RecyclerView list = xgramDragList;
        final XgramSheetDrag.Sheet provider = xgramSheet();
        xgramDragList = null;
        if (list != null && provider != null && factor >= 1f) {
          // only the list moved: it flings, or the sheet settles as after any scroll
          if (commit) {
            provider.xgramSheetRelease(list, velocity);
          }
          if (!hasCompletelyShown && !isHidden) {
            xgramOnRevealEnd(1f); // the drag took the sheet from its show spring: the reveal ends here (also on CANCEL)
          }
        } else {
          xgramReleaseSheet(velocity, commit);
        }
        break;
      }
    }
  }

  /** The end of a drag that moved the sheet: away, or back to its place. {@code commit} false never closes it. */
  private void xgramReleaseSheet (float velocity, boolean commit) {
    xgramRebaseHeight();
    final float height = currentHeight;
    final float visible = height - Math.max(0f, xgramSheetTranslation());
    final boolean dismiss = commit && !isHidden && XgramSheetDrag.target(visible, height - Math.max(0f, xgramDragStartTranslation), height, height, velocity, true) == XgramSheetDrag.HIDDEN;
    if (dismiss) {
      xgramDragFling = -velocity / height;
      xgramDragDismiss = true;
      hideWindow(true);
    } else {
      xgramSettleAfterDrag(-velocity / height);
    }
  }

  private XgramMotion.Value xgramDragSpringFor (int token) {
    if (xgramDragSpring == null) {
      xgramDragSpring = new XgramMotion.Value(token, factor, (v, value, velocity) -> setRevealFactor(value));
      xgramDragSpring.setThreshold(.001f);
    } else {
      xgramDragSpring.setSpring(token);
      xgramDragSpring.setValue(factor);
    }
    return xgramDragSpring;
  }

  private void xgramSettleAfterDrag (float factorVelocity) {
    if (XgramMotion.reduce()) {
      setRevealFactor(1f);
      xgramOnRevealEnd(1f);
      return;
    }
    XgramMotion.Value spring = xgramDragSpringFor(XgramExpressive.SPATIAL_DEFAULT);
    spring.setEndListener(() -> {
      setRevealFactor(1f);
      xgramOnRevealEnd(1f);
    });
    spring.animateTo(1f, factorVelocity);
  }

  /** The sheet leaves with the finger's velocity (STD_SPATIAL_FAST, as a plain hide), then the popup goes. */
  private void xgramDismissAfterDrag (float factorVelocity) {
    XgramMotion.Value spring = xgramDragSpringFor(XgramExpressive.STD_SPATIAL_FAST);
    spring.setEndListener(() -> xgramOnRevealEnd(0f));
    spring.animateTo(0f, Math.min(0f, factorVelocity));
  }

  // XGram M3E: nested scrolling (androidx NestedScrollingParent3, touches only, never a fling's momentum): the list of
  // a sheet that grows with it hands the drag to the sheet at its top, and takes it back once the sheet is in place.

  @Override
  public boolean onStartNestedScroll (@NonNull View child, @NonNull View target, int axes, int type) {
    if (type != ViewCompat.TYPE_TOUCH) {
      return false; // a fling's start (before the touch's stop) must not reset the touch's nested state
    }
    boolean nested = false;
    if ((axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0 && xgramGesture && !xgramDragging && xgramCanDragSheet()) {
      final XgramSheetDrag.Sheet provider = xgramSheet();
      nested = provider != null && target == provider.xgramSheetList();
    }
    xgramNested = nested;
    xgramNestedMoving = false;
    return nested;
  }

  @Override
  public void onNestedScrollAccepted (@NonNull View child, @NonNull View target, int axes, int type) {
    // nothing to set up: the sheet moves only once the list reaches its top
  }

  @Override
  public void onNestedPreScroll (@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
    if (type == ViewCompat.TYPE_TOUCH && xgramNested && xgramNestedMoving && dy > 0) {
      // the finger goes back up: the sheet comes back to its place before the list scrolls
      final float translation = xgramSheetTranslation();
      if (translation > 0f) {
        final float back = Math.min(translation, dy);
        xgramSetSheetTranslation(translation - back);
        consumed[1] += Math.round(back);
      }
    }
  }

  @Override
  public void onNestedScroll (@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
    if (type == ViewCompat.TYPE_TOUCH && xgramNested && dyUnconsumed < 0) {
      // the list is at its top and the finger goes on down: the sheet follows it
      if (!xgramNestedMoving) {
        xgramNestedMoving = true;
        xgramBeginSheetMove();
      }
      xgramSetSheetTranslation(Math.max(0f, xgramSheetTranslation()) - dyUnconsumed);
      consumed[1] += dyUnconsumed;
    }
  }

  @Override
  public void onNestedScroll (@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
    xgramNestedConsumed[0] = xgramNestedConsumed[1] = 0;
    onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, xgramNestedConsumed);
  }

  @Override
  public boolean onNestedPreFling (@NonNull View target, float velocityX, float velocityY) {
    // the sheet moved: its release decides (onStopNestedScroll), the list does not fling. Not ViewGroup's: this popup
    // has no nested scrolling parent of its own (and ViewGroup has none of these before Android 5)
    return xgramNested && xgramNestedMoving && xgramSheetTranslation() > 0f;
  }

  @Override
  public boolean onNestedFling (@NonNull View target, float velocityX, float velocityY, boolean consumed) {
    return false;
  }

  @Override
  public void onStopNestedScroll (@NonNull View target, int type) {
    if (type != ViewCompat.TYPE_TOUCH || !xgramNested) {
      return;
    }
    xgramNested = false;
    if (xgramNestedMoving) {
      xgramNestedMoving = false;
      if (factor < 1f) {
        xgramReleaseSheet(xgramTracker.velocityY(), xgramDispatchAction == MotionEvent.ACTION_UP);
      } else if (!hasCompletelyShown && !isHidden) {
        xgramOnRevealEnd(1f); // the move took the sheet from its show spring and it is back in place: the reveal ends
      }
    }
  }

  /** A touch on the sheet that nothing in it took (it came to the popup itself): it may still drag the sheet, never closes it. */
  private boolean xgramOnSheetTouch (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_MOVE: {
        xgramCheckDragStart(e); // once it starts, onTouchEvent hands the drag to xgramOnDragEvent
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        xgramDragTracking = false;
        xgramDownScroller = null;
        return true;
      }
    }
    return true;
  }

  /** The scrim (Expressive design): closes the popup when the finger lifts, not when it lands. */
  private boolean xgramOnScrimTouch (MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        xgramScrimPending = false;
        if (factor == 1f && !disableCancelOnTouchDown) {
          if (touchDownInterceptor == null || !touchDownInterceptor.onBackgroundTouchDown(this, e)) {
            xgramScrimPending = true;
          }
          return true;
        }
        return !(touchSectionProvider == null || touchSectionProvider.shouldTouchOutside(e.getX(), e.getY()));
      }
      case MotionEvent.ACTION_UP: {
        if (xgramScrimPending) {
          xgramScrimPending = false;
          hideWindow(true);
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        xgramScrimPending = false;
        return true;
      }
    }
    return true;
  }

  public void showNonAnimatedView (View view) {
    if (view == null || view.getParent() != null) {
      throw new IllegalArgumentException();
    }

    animationType = ANIMATION_TYPE_NONE;
    addView(view);
    if (getContext() instanceof BaseActivity) {
      ((BaseActivity) getContext()).showPopupWindow(this);
    }
  }

  private View boundView;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  private boolean needRevealStartDelay;

  public void showSimplePopupView (View view, int height) {
    if (view == null || view.getParent() != null) {
      throw new IllegalArgumentException();
    }

    if (view instanceof OptionsLayout && UI.getContext(getContext()).hasMoreWindow()) {
      needRevealStartDelay = true;
    }

    animationType = ANIMATION_TYPE_SIMPLE;
    if (view instanceof OptionsLayout && XgramExpressive.isEnabled()) {
      // XGram: the sheet paints below its bounds to cover the spring overshoot.
      setClipChildren(false);
    }
    view.setTranslationY(currentHeight = height);
    addView(boundView = view);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  public View getBoundView () {
    return boundView;
  }

  public void showMoreView (MenuMoreWrapAbstract menuWrap) {
    if (menuWrap == null) {
      throw new IllegalArgumentException();
    }

    if (menuWrap.getParent() != null) {
      ((ViewGroup) menuWrap.getParent()).removeView(menuWrap);
    }

    final boolean anchorCenter = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER;
    final boolean anchorRight = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT;
    final int padding = Screen.dp(8f);
    final int itemsWidth = menuWrap.getItemsWidth();
    int cx = anchorRight != Lang.rtl() ? itemsWidth - padding : Screen.dp(17f);
    int cy = menuWrap.shouldPivotBottom() ? menuWrap.getItemsHeight() - padding : padding;

    if (XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive menus scale in from the anchor corner, no circular reveal.
      animationType = ANIMATION_TYPE_MORE_SCALE;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(XGRAM_MORE_START_SCALE);
      menuWrap.setScaleY(XGRAM_MORE_START_SCALE);
    } else if (Config.REVEAL_ANIMATION_AVAILABLE && anchorRight) {
      animationType = ANIMATION_TYPE_MORE_REVEAL;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(1f);
      menuWrap.setScaleY(1f);
    } else {
      animationType = ANIMATION_TYPE_MORE_SCALE;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(MenuMoreWrap.START_SCALE);
      menuWrap.setScaleY(MenuMoreWrap.START_SCALE);
    }

    menuWrap.setPivotX(cx);
    menuWrap.setPivotY(cy);

    addView(boundView = menuWrap);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  private void hideMoreWrap () {
    MenuMoreWrapAbstract menuWrap = (MenuMoreWrapAbstract) getContentChild();

    if (menuWrap == null) {
      return;
    }

    if (XgramExpressive.isEnabled()) {
      // XGram: close with alpha only (effects fast spring).
      XgramExpressive.Spring alpha = xgramMoreAlpha(menuWrap);
      alpha.animateTo(0f);
      if (!alpha.isRunning()) {
        dismissWindow();
      }
      return;
    }

    final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        dismissWindow();
      }
    };

    Animator menuAnimator = null;

    if (Config.REVEAL_ANIMATION_AVAILABLE && menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT) {
      try {
        menuAnimator = android.view.ViewAnimationUtils.createCircularReveal(menuWrap, (int) menuWrap.getPivotX(), (int) menuWrap.getPivotY(), menuWrap.getRevealRadius(), 0);
        menuAnimator.setInterpolator(MenuMoreWrap.REVEAL_INTERPOLATOR);
        menuAnimator.setDuration(MenuMoreWrap.REVEAL_DURATION);
      } catch (Throwable t) {
        Log.w("Cannot create circular reveal", t);
        menuAnimator = null;
      }
    }

    if (menuAnimator != null) {
      menuAnimator.addListener(listener);
      menuAnimator.start();
    } else {
      menuWrap.scaleOut(listener);
    }
  }

  public interface AnimatedPopupProvider {
    void prepareShowAnimation ();
    void launchShowAnimation (PopupLayout popup);
    boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator);
  }

  private AnimatedPopupProvider customAnimatorProvider;

  public void setAnimationProvider (AnimatedPopupProvider provider) {
    this.customAnimatorProvider = provider;
  }

  public void showAnimatedPopupView (View view, AnimatedPopupProvider provider) {
    this.animationType = ANIMATION_TYPE_CUSTOM;
    this.customAnimatorProvider = provider;
    provider.prepareShowAnimation();
    addView(boundView = view);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  public boolean shouldDisallowScreenshots () {
    if (com.fahadali.xgram.XgramSettings.instance().allowScreenshots() && !org.thunderdog.challegram.unsorted.Passcode.instance().shouldDisallowScreenshots()) {
      return false; // XGram
    }
    ViewController<?> c = boundController;
    if (c != null && c.shouldDisallowScreenshots()) {
      return true;
    }
    if (boundView instanceof SensitiveContentContainer) {
      return ((SensitiveContentContainer) boundView).shouldDisallowScreenshots();
    }
    return false;
  }

  private boolean isHidden;

  public boolean isWindowHidden () {
    return isHidden;
  }

  public void hideWindow (boolean animated) {
    if (isHidden) {
      return;
    }

    isHidden = true;

    if (animated) {
      launchHideAnimation();
    } else {
      if (dismissListener != null) {
        dismissListener.onPopupDismissPrepare(this);
      }
      dismissWindow();
    }
  }

  // Animation

  @Override
  protected View getMeasureTarget () {
    return boundController != null ? boundController.getValue() : this;
  }

  private static final int ANIMATION_TYPE_NONE = -1;
  private static final int ANIMATION_TYPE_SIMPLE = 0;
  private static final int ANIMATION_TYPE_CUSTOM = 1;
  private static final int ANIMATION_TYPE_MORE_SCALE = 2;
  private static final int ANIMATION_TYPE_MORE_REVEAL = 3;

  private int animationType;
  private boolean revealAnimationLaunched, hideAnimationLaunched;
  private int currentHeight;

  private boolean hasCompletelyShown;

  private void launchRevealAnimation () {
    if (!revealAnimationLaunched) {
      revealAnimationLaunched = true;
      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          animateRevealFactor(1f);
          break;
        }
        case ANIMATION_TYPE_CUSTOM: {
          customAnimatorProvider.launchShowAnimation(this);
          break;
        }
        case ANIMATION_TYPE_MORE_SCALE:
        case ANIMATION_TYPE_MORE_REVEAL: {
          Animator.AnimatorListener listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd (Animator animation) {
              factor = 1f;
              onCustomShowComplete();
            }
          };

          MenuMoreWrapAbstract menuWrap = (MenuMoreWrapAbstract) getContentChild();

          if (menuWrap == null) {
            return;
          }

          if (XgramExpressive.isEnabled()) {
            xgramShowMore(menuWrap);
            return;
          }

          if (animationType == ANIMATION_TYPE_MORE_SCALE) {
            if (menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER) {
              final int itemsWidth = menuWrap.getItemsWidth();
              menuWrap.setPivotX(itemsWidth / 2f);
            }
            menuWrap.scaleIn(listener);
            return;
          }

          final boolean anchorRight = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT;
          final int padding = Screen.dp(8f);
          final int itemsWidth = menuWrap.getItemsWidth();
          int cx = anchorRight != Lang.rtl() ? itemsWidth - padding : Screen.dp(17f);
          int cy = menuWrap.shouldPivotBottom() ? menuWrap.getItemsHeight() - padding : padding;

          if (animationType == ANIMATION_TYPE_MORE_REVEAL && Config.REVEAL_ANIMATION_AVAILABLE) {
            try {
              int newCx = Lang.rtl() ? (int) ((float) Screen.dp(49f) * .5f) : itemsWidth - (int) ((float) Screen.dp(49f) * .5f);
              int newCy = Size.getHeaderPortraitSize() / 2;
              if (menuWrap.shouldPivotBottom()) {
                newCy = menuWrap.getItemsHeight() - newCy;
              }
              Animator animator = android.view.ViewAnimationUtils.createCircularReveal(menuWrap, newCx, newCy, 0, menuWrap.getRevealRadius());
              animator.addListener(listener);
              animator.setInterpolator(MenuMoreWrap.REVEAL_INTERPOLATOR);
              animator.setDuration(MenuMoreWrap.REVEAL_DURATION);
              animationType = ANIMATION_TYPE_MORE_REVEAL;
              cx = newCx;
              cy = newCy;

              menuWrap.setPivotX(cx);
              menuWrap.setPivotY(cy);

              animator.start();
              menuWrap.setAlpha(1f);
              return;
            } catch (Throwable t) {
              Log.w("Cannot create circular reveal", t);
            }
          }

          menuWrap.setAlpha(0f);
          menuWrap.setScaleX(MenuMoreWrap.START_SCALE);
          menuWrap.setScaleY(MenuMoreWrap.START_SCALE);

          menuWrap.setPivotX(cx);
          menuWrap.setPivotY(cy);

          animationType = ANIMATION_TYPE_MORE_SCALE;
          menuWrap.scaleIn(listener);


          break;
        }
      }
    }
  }

  public interface PopupHeightProvider {
    int getCurrentPopupHeight ();
  }

  private @Nullable PopupHeightProvider popupHeightProvider;

  public void setPopupHeightProvider (@Nullable PopupHeightProvider popupHeightProvider) {
    this.popupHeightProvider = popupHeightProvider;
  }

  private void launchHideAnimation () {
    if (!hideAnimationLaunched) {
      hideAnimationLaunched = true;

      if (boundController != null) {
        boundController.onPrepareToDismissPopup();
      }
      if (dismissListener != null) {
        dismissListener.onPopupDismissPrepare(this);
      }

      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          if (customAnimatorProvider != null) {
            animationType = ANIMATION_TYPE_CUSTOM;
            if (customAnimatorProvider.launchHideAnimation(this, animator)) {
              break;
            }
            animationType = ANIMATION_TYPE_SIMPLE;
          }
          if (popupHeightProvider != null) {
            currentHeight = popupHeightProvider.getCurrentPopupHeight();
          } else {
            View view = getContentChild();
            currentHeight = view != null ? view.getMeasuredHeight() : 0;
          }
          animateRevealFactor(0f);
          break;
        }
        case ANIMATION_TYPE_CUSTOM: {
          if (!customAnimatorProvider.launchHideAnimation(this, animator)) {
            if (popupHeightProvider != null) {
              currentHeight = popupHeightProvider.getCurrentPopupHeight();
            } else {
              View view = getContentChild();
              currentHeight = view != null ? view.getMeasuredHeight() : 0;
            }
            animationType = ANIMATION_TYPE_SIMPLE;
            animateRevealFactor(0f);
          }
          break;
        }
        case ANIMATION_TYPE_MORE_REVEAL:
        case ANIMATION_TYPE_MORE_SCALE: {
          hideMoreWrap();
          break;
        }
      }
    }
  }

  private static final int REVEAL_ANIMATOR = 0;
  private FactorAnimator animator;

  // XGram: Material 3 Expressive motion.
  // Sheets (ANIMATION_TYPE_SIMPLE) show with the spatial default spring (about 1.5% overshoot)
  // and hide with the standard spatial fast spring. Menus scale 0.8 to 1 from the anchor corner
  // with the spatial fast spring and fade with the effects fast spring.

  private static final float XGRAM_MORE_START_SCALE = .8f;
  private XgramExpressive.Spring xgramShowSpring, xgramHideSpring;
  private XgramExpressive.Spring xgramMoreScaleSpring, xgramMoreAlphaSpring;
  private Runnable xgramPendingReveal;

  private void xgramAnimateReveal (float toFactor) {
    if (xgramPendingReveal != null) {
      removeCallbacks(xgramPendingReveal);
      xgramPendingReveal = null;
    }
    if (toFactor == 1f && needRevealStartDelay) {
      xgramPendingReveal = () -> {
        xgramPendingReveal = null;
        xgramStartReveal(1f);
      };
      postDelayed(xgramPendingReveal, MenuMoreWrap.REVEAL_DURATION);
      return;
    }
    xgramStartReveal(toFactor);
  }

  private void xgramStartReveal (float toFactor) {
    final boolean show = toFactor == 1f;
    xgramStopDragSpring(); // XGram M3E r6: a drag's settle spring never runs against the reveal or the hide
    if (!show && xgramDragDismiss) {
      // released past the threshold or flung: out with the finger's velocity
      xgramDragDismiss = false;
      if (!XgramMotion.reduce()) {
        if (xgramShowSpring != null) {
          xgramShowSpring.setEndListener(null);
          xgramShowSpring.cancel();
          xgramShowSpring = null;
        }
        xgramDismissAfterDrag(xgramDragFling);
        return;
      }
    }
    XgramExpressive.Spring other = show ? xgramHideSpring : xgramShowSpring;
    if (other != null) {
      other.setEndListener(null);
      other.cancel();
      if (show) {
        xgramHideSpring = null;
      } else {
        xgramShowSpring = null;
      }
    }
    XgramExpressive.Spring spring = show ? xgramShowSpring : xgramHideSpring;
    if (spring == null) {
      spring = new XgramExpressive.Spring(show ? XgramExpressive.SPATIAL_DEFAULT : XgramExpressive.STD_SPATIAL_FAST, factor, (s, value) -> setRevealFactor(value));
      spring.setThreshold(.001f);
      final float target = toFactor;
      spring.setEndListener(() -> xgramOnRevealEnd(target));
      if (show) {
        xgramShowSpring = spring;
      } else {
        xgramHideSpring = spring;
      }
    }
    spring.animateTo(toFactor);
    if (!spring.isRunning()) {
      // Reduced motion snaps without running.
      xgramOnRevealEnd(toFactor);
    }
  }

  private void xgramOnRevealEnd (float target) {
    if (target == 0f) {
      if (!isHidden) {
        return;
      }
      setRevealFactor(0f);
      dismissWindow();
    } else {
      if (isHidden || hasCompletelyShown) {
        return;
      }
      setRevealFactor(1f);
      onCustomShowComplete();
    }
    UI.getContext(getContext()).notifyBackPressAvailabilityChanged();
  }

  private XgramExpressive.Spring xgramMoreAlpha (MenuMoreWrapAbstract menuWrap) {
    if (xgramMoreAlphaSpring == null) {
      xgramMoreAlphaSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, menuWrap.getAlpha(), (s, value) -> menuWrap.setAlpha(Math.max(0f, Math.min(1f, value))));
      xgramMoreAlphaSpring.setThreshold(.004f);
      xgramMoreAlphaSpring.setEndListener(() -> {
        if (hideAnimationLaunched && xgramMoreAlphaSpring != null && xgramMoreAlphaSpring.getTarget() == 0f) {
          dismissWindow();
        }
      });
    }
    return xgramMoreAlphaSpring;
  }

  private void xgramShowMore (MenuMoreWrapAbstract menuWrap) {
    if (menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER) {
      menuWrap.setPivotX(menuWrap.getItemsWidth() / 2f);
    }
    if (xgramMoreScaleSpring == null) {
      xgramMoreScaleSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, menuWrap.getScaleX(), (s, value) -> {
        menuWrap.setScaleX(value);
        menuWrap.setScaleY(value);
      });
      xgramMoreScaleSpring.setThreshold(.002f);
      xgramMoreScaleSpring.setEndListener(this::xgramOnMoreShown);
    }
    XgramExpressive.Spring alpha = xgramMoreAlpha(menuWrap);
    alpha.animateTo(1f);
    xgramMoreScaleSpring.animateTo(1f);
    if (!xgramMoreScaleSpring.isRunning()) {
      menuWrap.setScaleX(1f);
      menuWrap.setScaleY(1f);
      menuWrap.setAlpha(1f);
      xgramOnMoreShown();
    }
  }

  private void xgramOnMoreShown () {
    if (!hideAnimationLaunched && !hasCompletelyShown) {
      factor = 1f;
      onCustomShowComplete();
    }
  }

  private void xgramCancelSprings () {
    if (xgramPendingReveal != null) {
      removeCallbacks(xgramPendingReveal);
      xgramPendingReveal = null;
    }
    XgramExpressive.Spring[] springs = {xgramShowSpring, xgramHideSpring, xgramMoreScaleSpring, xgramMoreAlphaSpring};
    for (XgramExpressive.Spring spring : springs) {
      if (spring != null) {
        spring.setEndListener(null);
        spring.cancel();
      }
    }
    xgramStopDragSpring();
    xgramTracker.recycle();
    xgramGesture = false;
    xgramDragging = false;
    xgramDragTracking = false;
    xgramNested = xgramNestedMoving = false;
    xgramDragList = null;
    xgramDownScroller = null;
  }

  /** XGram: a sheet that slides up from the bottom (predictive back shrinks it). */
  public boolean xgramIsBottomSheet () {
    return animationType == ANIMATION_TYPE_SIMPLE && boundView != null && !isHidden;
  }

  public void animateRevealFactor (float toFactor) {
    if (animationType == ANIMATION_TYPE_SIMPLE && XgramExpressive.isEnabled()) {
      xgramAnimateReveal(toFactor);
      return;
    }
    if (animator == null) {
      animator = new FactorAnimator(REVEAL_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, factor);
    }
    if (toFactor == 1f && needRevealStartDelay) {
      animator.setStartDelay(MenuMoreWrap.REVEAL_DURATION);
    } else {
      animator.setStartDelay(0);
    }
    animator.animateTo(toFactor);
  }

  public View getContentChild () {
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View view = getChildAt(i);
      if (!(view instanceof BackgroundView)) {
        return view;
      }
    }
    return null;
  }

  private BackgroundView backgroundView;

  private class BackgroundView extends View {
    public BackgroundView (Context context) {
      super(context);
      setAlpha(0f);
    }

    @Override
    public void draw (Canvas c) {
      super.draw(c);
      if (useStatusBar) {
        final int color = ColorUtils.color((int) (255f * .3f), 0);
        c.drawRect(0, 0, getMeasuredWidth(), HeaderView.getTopOffset(), Paints.fillingPaint(color));
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final int color = ColorUtils.color((int) (255f * (XgramExpressive.isEnabled() ? .32f : Theme.getPopupOverlayAlpha())), 0);
      int offset = HeaderView.getTopOffset();
      if (useStatusBar && offset != 0) {
        c.drawRect(0, offset, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(color));
      } else {
        c.drawColor(color);
      }
    }
  }

  private float factor;

  public void setRevealFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (XgramExpressive.isEnabled()) {
        // XGram: spring values may leave 0..1. Only Options sheets (which paint below their
        // bounds) show the overshoot; other content stops at its resting position.
        if (animationType == ANIMATION_TYPE_SIMPLE) {
          View view = getContentChild();
          if (view != null) {
            float translation = currentHeight * (1f - factor);
            view.setTranslationY(view instanceof OptionsLayout ? translation : Math.max(0f, translation));
          }
        }
        backgroundView.setAlpha(Math.max(0f, Math.min(1f, factor)) * xgramScrimScale);
        return;
      }
      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          View view = getContentChild();
          if (view != null) {
            view.setTranslationY(currentHeight * (1f - factor));
          }
          break;
        }
      }
      backgroundView.setAlpha(factor);
    }
  }

  public void setHideBackground (boolean hide) {
    backgroundView.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
  }

  public boolean hadCompletelyShown () {
    return hasCompletelyShown;
  }

  /** XGram M3E: the popup's own sheet drag is going on (its release settles the sheet, not the list's idle state). */
  public boolean xgramIsDraggingSheet () {
    return xgramDragging;
  }

  public void onCustomShowComplete () {
    hasCompletelyShown = true;
    if (showListener != null) {
      showListener.onPopupCompletelyShown(this);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        setRevealFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        if (finalFactor == 0f) {
          dismissWindow();
        } else if (finalFactor == 1f) {
          onCustomShowComplete();
        }
        UI.getContext(getContext()).notifyBackPressAvailabilityChanged();
        break;
      }
    }
  }

  private boolean useStatusBar;

  public void addStatusBar () {
    useStatusBar = true;
  }

  private @Nullable TooltipOverlayView tooltipOverlayView;

  public TooltipOverlayView tooltipManager () {
    if (tooltipOverlayView == null) {
      tooltipOverlayView = new TooltipOverlayView(getContext());
      tooltipOverlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      tooltipOverlayView.setAvailabilityListener((overlayView, hasChildren) -> {
        if (hasChildren) {
          if (tooltipOverlayView.getParent() != null)
            return;
          addView(tooltipOverlayView);
        } else {
          removeView(tooltipOverlayView);
        }
      });
    }
    return tooltipOverlayView;
  }

  public final TooltipOverlayView.TooltipInfo showErrorTooltip (ViewController<?> context, View view, CharSequence text) {
    return showTooltip(context, view, R.drawable.baseline_error_24, text);
  }

  public final TooltipOverlayView.TooltipInfo showWarningTooltip (ViewController<?> context, View view, CharSequence text) {
    return showTooltip(context, view, R.drawable.baseline_warning_24, text);
  }

  public final TooltipOverlayView.TooltipInfo showInfoTooltip (ViewController<?> context, View view, CharSequence text) {
    return showTooltip(context, view, R.drawable.baseline_info_24, text);
  }

  public final TooltipOverlayView.TooltipInfo showTooltip (ViewController<?> context, View view, @DrawableRes int icon, CharSequence text) {
    return tooltipManager()
      .builder(view)
      .show(context, null, icon, text);
  }

  public static PopupLayout parentOf (View view) {
    ViewParent parent = view.getParent();
    while (parent != null && !(parent instanceof PopupLayout)) {
      parent = parent.getParent();
    }
    return (PopupLayout) parent;
  }

  // Drawing



  // We're going deeper: popups inside popups


}
