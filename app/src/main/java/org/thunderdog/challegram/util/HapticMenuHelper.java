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
 */
package org.thunderdog.challegram.util;

import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SendButton;

import java.util.List;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.lambda.Destroyable;

public class HapticMenuHelper implements View.OnTouchListener, View.OnLongClickListener {
  public interface Provider {
    List<MenuItem> onCreateHapticMenu (View view);
    default int getAnchorMode (View view) { return MenuMoreWrap.ANCHOR_MODE_RIGHT; }
  }

  public interface OnItemClickListener {
    boolean onHapticMenuItemClick (View view, View parentView, MenuItem item);
  }

  public interface OnItemMenuListener {
    void onHapticMenuOpen ();
    void onHapticMenuClose ();
  }

  public static class MenuItem implements Destroyable {
    public final int id;
    public final CharSequence title;
    public final int iconResId;
    public final Drawable icon;
    public final CharSequence subtitle;
    public final @Nullable TdApi.MessageSender messageSenderId;
    public final boolean isLocked;

    public boolean isCheckbox, isCheckboxSelected;
    public BoolAnimator isCheckboxSelectedAnimated;
    /**
     * XGram M3E: a single choice among others rather than a checkbox (a checkbox row underneath): the chosen row sits on
     * the secondaryContainer pill with a trailing check, and a tap does not toggle it.
     */
    public boolean isChoice;

    private Tdlib tdlib;
    private long userId;
    private TdlibCache.UserStatusChangeListener userStatusChangeListener;

    private OnItemClickListener onClickListener;

    private long tutorialFlag;

    public MenuItem (int id, CharSequence title, int iconResId) {
      this.id = id;
      this.title = title;
      this.iconResId = iconResId;
      this.icon = null;
      this.subtitle = null;
      this.messageSenderId = null;
      this.isLocked = false;
    }

    public MenuItem (int id, CharSequence title, Drawable icon) {
      this.id = id;
      this.title = title;
      this.iconResId = 0;
      this.icon = icon;
      this.subtitle = null;
      this.messageSenderId = null;
      this.isLocked = false;
    }

    public MenuItem (int id, CharSequence title, CharSequence subtitle, int iconResId) {
      this.id = id;
      this.title = title;
      this.subtitle = subtitle;
      this.iconResId = iconResId;
      this.icon = null;
      this.messageSenderId = null;
      this.isLocked = false;
    }

    public MenuItem (int id, CharSequence title, CharSequence subtitle, Drawable icon) {
      this.id = id;
      this.title = title;
      this.subtitle = subtitle;
      this.iconResId = 0;
      this.icon = icon;
      this.messageSenderId = null;
      this.isLocked = false;
    }

    public MenuItem (int id, CharSequence title, CharSequence subtitle, int iconResId, Tdlib tdlib, TdApi.MessageSender sender, boolean isLocked) {
      this.id = id;
      this.title = title;
      this.subtitle = subtitle;
      this.iconResId = iconResId;
      this.icon = null;
      this.messageSenderId = sender;
      this.isLocked = isLocked;
      this.tdlib = tdlib;
    }

    public MenuItem setIsCheckbox (boolean isCheckbox, boolean checkboxSelected) {
      this.isCheckbox = isCheckbox;
      this.isCheckboxSelected = checkboxSelected;
      if (isCheckboxSelectedAnimated != null) {
        isCheckboxSelectedAnimated.forceValue(checkboxSelected, checkboxSelected ? 1f : 0f);
      }
      return this;
    }

    /** XGram M3E: see {@link #isChoice}. */
    public MenuItem setIsChoice (boolean chosen) {
      this.isChoice = true;
      return setIsCheckbox(true, chosen);
    }

    public MenuItem setOnClickListener (OnItemClickListener onClickListener) {
      this.onClickListener = onClickListener;
      return this;
    }

    public MenuItem bindToLastSeenAvailability (Tdlib tdlib, long userId) {
      if (tdlib == null || userId == 0)
        return this;
      this.tdlib = tdlib;
      this.userId = userId;
      this.userStatusChangeListener = ((userId1, status, uiOnly) -> {
        if (userId1 == userId) {
          boolean isVisible = status != null && status.getConstructor() == TdApi.UserStatusOffline.CONSTRUCTOR && ((TdApi.UserStatusOffline) status).wasOnline != 0;
          if (boundView != null) {
            boundView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
          }
        }
      });
      tdlib.cache().subscribeToUserStatusChanges(userId, this.userStatusChangeListener);
      return this;
    }

    public MenuItem bindTutorialFlag (long tutorialFlag) {
      this.tutorialFlag = tutorialFlag;
      return this;
    }

    public boolean isVisible () {
      return userId == 0 || tdlib == null || tdlib.cache().userLastSeenAvailable(userId);
    }

    @Nullable
    private View boundView;

    @Override
    public void performDestroy () {
      if (this.tdlib != null && this.userStatusChangeListener != null) {
        this.tdlib.cache().unsubscribeFromUserStatusChanges(userId, userStatusChangeListener);
        this.tdlib = null; this.userStatusChangeListener = null;
        this.boundView = null;
      }
    }
  }

  private PopupLayout hapticMenu;

  private final Provider provider;
  private final OnItemClickListener onItemClickListener;
  @Nullable
  private final ThemeListenerList themeListeners;
  @Nullable
  private final ThemeDelegate forcedTheme;

  @Nullable private OnItemMenuListener onHapticMenuListener;
  private MenuMoreWrap moreWrap;
  private View hapticView;

  public HapticMenuHelper (Provider provider, OnItemClickListener onItemClickListener, @Nullable ThemeListenerList themeListeners, @Nullable ThemeDelegate forcedTheme) {
    this.provider = provider;
    this.onItemClickListener = onItemClickListener;
    this.themeListeners = themeListeners;
    this.forcedTheme = forcedTheme;
  }

  public HapticMenuHelper attachToView (View view) {
    hapticView = view;
    view.setOnLongClickListener(this);
    return this;
  }

  public HapticMenuHelper hapticListener (OnItemMenuListener listener) {
    this.onHapticMenuListener = listener;
    return this;
  }

  public HapticMenuHelper selectableMode (View view) {
    hapticView = view;
    hapticView.setOnTouchListener(this);
    return this;
  }

  public HapticMenuHelper detachFromView (View view) {
    view.setOnLongClickListener(null);
    return this;
  }

  @Override
  public boolean onLongClick (View v) {
    List<MenuItem> items = provider.onCreateHapticMenu(v);
    if (items != null && !items.isEmpty()) {
      // UI.forceVibrate(v, true);
      openMenu(v, items, provider.getAnchorMode(v));
      return true;
    }
    return false;
  }

  private float startX, startY;

  @Override
  public boolean onTouch (View v, MotionEvent e) {
    final float x = e.getX();
    final float y = e.getY();

    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      this.startX = x;
      this.startY = y;
    }

    if (hapticMenu == null) return false;

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN:
        break;
      case MotionEvent.ACTION_MOVE:
        processMovement(v, x, y, startX, startY);
        break;
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        dropMenu(v, e.getX(), e.getY(), e.getAction() == MotionEvent.ACTION_UP);
        break;
    }
    return false;
  }

  public boolean openMenu (View view) {
    List<MenuItem> items = provider.onCreateHapticMenu(view);
    if (items != null && !items.isEmpty()) {
      openMenu(view, items, provider.getAnchorMode(view));
      return true;
    }
    return false;
  }

  public boolean isMenuOpened () {
    return hapticMenu != null && !hapticMenu.isWindowHidden();
  }

  private void openMenu (View view, List<MenuItem> items, int anchorMode) {
    if (hapticMenu != null) {
      hapticMenu.hideWindow(false);
      if (onHapticMenuListener != null) {
        onHapticMenuListener.onHapticMenuClose();
      }
    }

    if (onHapticMenuListener != null) {
      onHapticMenuListener.onHapticMenuOpen();
    }

    // XGram: scrollable, so the rows that don't fit between the status bar and the button scroll. The menu places itself
    // at the button (xgramSetPopupAnchor below): the old layout listener read the menu's place while it was still scaled
    // for its opening (Expressive: 0.8), which put it ~90dp too high (under the status bar) and ~45dp too far left, and
    // its Send as fudges (-12dp / +8dp) only fit the 0.56 scale of the legacy design.
    moreWrap = new MenuMoreWrap(view.getContext(), /* scrollable */ true);
    moreWrap.init(themeListeners, forcedTheme);
    for (MenuItem item : items) {
      item.boundView = moreWrap.addItem(item.tdlib, item, itemView -> onMenuItemClick(item, itemView, view));
      item.boundView.setVisibility(item.isVisible() ? View.VISIBLE : View.GONE);
      if (item.tutorialFlag != 0) {
        Settings.instance().markTutorialAsComplete(item.tutorialFlag);
      }
    }
    moreWrap.setAnchorMode(anchorMode);
    moreWrap.setShouldPivotBottom(true);
    moreWrap.setRightNumber(0);
    // XGram: its card 8dp above the button (below it only when not even two rows fit above and below has more room),
    // never above the status bar or the cutout + 8dp nor under the keyboard or the navigation bar; it follows the button
    // while the keyboard or the emoji panel moves it. Send as (ANCHOR_MODE_CENTER): centred, the legacy tail under the
    // button's centre.
    // The input bar's Send with Expressive design: at its 40dp filled circle, not the 55x49dp view around it
    final android.graphics.Rect xgramVisible = view instanceof SendButton ? ((SendButton) view).xgramVisibleBounds() : null;
    if (xgramVisible != null) {
      moreWrap.xgramSetPopupAnchor(view, xgramVisible, MenuMoreWrap.XGRAM_PLACE_PREFER_ABOVE);
    } else {
      moreWrap.xgramSetPopupAnchor(view);
    }

    hapticMenu = new PopupLayout(view.getContext());
    hapticMenu.init(false);
    hapticMenu.setNeedRootInsets();
    hapticMenu.setOverlayStatusBar(true);
    hapticMenu.setDismissListener(popup -> {
      if (onHapticMenuListener != null) {
        onHapticMenuListener.onHapticMenuClose();
      }
      for (MenuItem item : items) {
        item.performDestroy();
      }
      if (this.hapticMenu == popup) {
        this.hapticMenu = null;
      }
    });
    hapticMenu.showMoreView(moreWrap);
  }

  public void hideMenu () {
    if (this.hapticMenu != null) {
      this.hapticMenu.hideWindow(true);
      this.hapticMenu = null;
      if (onHapticMenuListener != null) {
        onHapticMenuListener.onHapticMenuClose();
      }
    }
  }

  private void processMovement (View view, float x, float y, float startX, float startY) {
    if (hapticMenu == null || moreWrap == null || hapticMenu.isWindowHidden()) return;
    moreWrap.processMoveEvent(view, x, y, startX, startY);
  }

  private void dropMenu (View view, float x, float y, boolean apply) {
    if (hapticMenu != null && !hapticMenu.isWindowHidden()) {
      if (apply) {
        moreWrap.onApply();
      }
      hideMenu();
    }
    // TODO handle click ? ? ?
  }

  private void onMenuItemClick (MenuItem item, View view, View parentView) {
    if (hapticMenu != null && !hapticMenu.isWindowHidden()) {
      boolean res = onItemClickListener.onHapticMenuItemClick(view, parentView, item);
      if (item.onClickListener != null && !item.onClickListener.onHapticMenuItemClick(view, parentView, item)) {
        res = false;
      }
      if (!item.isCheckbox && res) {
        hideMenu();
      }
    }
  }
}
