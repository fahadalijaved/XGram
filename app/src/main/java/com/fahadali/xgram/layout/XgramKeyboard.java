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

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.viewpager.widget.PagerAdapter;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

/**
 * Hardware keyboard support (a Bluetooth keyboard on a foldable or tablet, DeX, Chromebooks):
 * <ul>
 *   <li>Enter to send in the message box (Auto, On, Off), Shift+Enter for a new line, Ctrl+Enter always sends</li>
 *   <li>Up in an empty message box edits the last message</li>
 *   <li>Esc closes what is open (popups, the media viewer, search, selection, a reply) and goes back, but never
 *   closes the app from the chat list</li>
 *   <li>Ctrl+F searches in the chat (or the chat list), Ctrl+K searches chats, Ctrl+Tab and Ctrl+Shift+Tab switch
 *   folders</li>
 *   <li>The list behind Meta+/ (the system keyboard shortcuts helper)</li>
 * </ul>
 * Only keys from a real keyboard are handled; key events sent by on-screen keyboards keep Telegram X's behaviour.
 */
public final class XgramKeyboard {
  private XgramKeyboard () { }

  /** A key event from a physical keyboard, not one an on-screen keyboard (IME) sent. */
  public static boolean isHardware (KeyEvent event) {
    return event != null && (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == 0 && event.getDeviceId() != KeyCharacterMap.VIRTUAL_KEYBOARD;
  }

  /** A typing keyboard is attached and not hidden (a Bluetooth or USB keyboard, a keyboard cover). */
  public static boolean hasHardwareKeyboard (Context context) {
    Configuration configuration = context.getResources().getConfiguration();
    return configuration.keyboard == Configuration.KEYBOARD_QWERTY && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
  }

  // Enter to send

  public static String enterToSendName (int mode) {
    switch (mode) {
      case XgramSettings.ENTER_TO_SEND_ON:
        return Lang.getString(R.string.XgramStateOn);
      case XgramSettings.ENTER_TO_SEND_OFF:
        return Lang.getString(R.string.XgramStateOff);
      default:
        return Lang.getString(R.string.XgramEnterToSendAuto);
    }
  }

  /**
   * Whether a new line typed at the end of the message box sends it (Telegram X's "Send with Enter" path, used by
   * on-screen keyboards). Auto keeps Telegram X's own switch, and sends whenever a hardware keyboard is attached,
   * for keyboards whose Enter arrives as text.
   */
  public static boolean typedEnterSends (Context context) {
    switch (XgramSettings.instance().enterToSend()) {
      case XgramSettings.ENTER_TO_SEND_ON:
        return true;
      case XgramSettings.ENTER_TO_SEND_OFF:
        return false;
      default:
        return Settings.instance().needSendByEnter() || hasHardwareKeyboard(context);
    }
  }

  /** What a key does in the chat's message box: {@link #COMPOSER_DEFAULT} leaves it to the text field. */
  public static final int COMPOSER_DEFAULT = 0, COMPOSER_SEND = 1, COMPOSER_NEW_LINE = 2, COMPOSER_EDIT_LAST = 3;

  public static int composerKey (KeyEvent event, boolean isEmpty) {
    if (!isHardware(event)) {
      return COMPOSER_DEFAULT;
    }
    int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
      if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
        return COMPOSER_SEND;
      }
      if (event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
        return COMPOSER_NEW_LINE;
      }
      if (event.hasNoModifiers()) {
        return XgramSettings.instance().enterToSend() != XgramSettings.ENTER_TO_SEND_OFF ? COMPOSER_SEND : COMPOSER_NEW_LINE;
      }
      return COMPOSER_DEFAULT;
    }
    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isEmpty && event.hasNoModifiers()) {
      return COMPOSER_EDIT_LAST;
    }
    return COMPOSER_DEFAULT;
  }

  // Activity wide shortcuts

  /** Key code whose press was taken here: its release is taken too. */
  private static int consumedKeyCode;

  /** Called first from {@link BaseActivity#dispatchKeyEvent}; true when the key was used. */
  public static boolean dispatchKeyEvent (BaseActivity activity, KeyEvent event) {
    if (!isHardware(event)) {
      return false;
    }
    int keyCode = event.getKeyCode();
    switch (event.getAction()) {
      case KeyEvent.ACTION_DOWN: {
        if (event.getRepeatCount() > 0) {
          return keyCode == consumedKeyCode;
        }
        boolean handled;
        try {
          handled = onShortcut(activity, keyCode, event);
        } catch (Throwable t) {
          android.util.Log.e("XGram", "keyboard shortcut failed", t);
          handled = false;
        }
        consumedKeyCode = handled ? keyCode : 0;
        return handled;
      }
      case KeyEvent.ACTION_UP: {
        if (consumedKeyCode != 0 && keyCode == consumedKeyCode) {
          consumedKeyCode = 0;
          if (keyCode == KeyEvent.KEYCODE_ESCAPE && !event.isCanceled()) {
            try {
              onEscape(activity);
            } catch (Throwable t) {
              android.util.Log.e("XGram", "Esc failed", t);
            }
          }
          return true;
        }
        return false;
      }
    }
    return false;
  }

  private static boolean onShortcut (BaseActivity activity, int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_ESCAPE:
        // Acts on release, like Back. Alt or Meta+Esc stay the system's (Home).
        return event.hasNoModifiers();
      case KeyEvent.KEYCODE_F:
        return event.hasModifiers(KeyEvent.META_CTRL_ON) && search(activity, false);
      case KeyEvent.KEYCODE_K:
        return event.hasModifiers(KeyEvent.META_CTRL_ON) && search(activity, true);
      case KeyEvent.KEYCODE_TAB:
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
          return switchFolder(activity, 1);
        }
        if (event.hasModifiers(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)) {
          return switchFolder(activity, -1);
        }
        return false;
    }
    return false;
  }

  @Nullable
  private static ViewController<?> topController (BaseActivity activity) {
    NavigationController navigation = activity.navigation();
    return navigation != null ? navigation.getCurrentStackItem() : null;
  }

  /** The chat list on screen: the left pane's in two panes, else Telegram X's own when nothing covers it. */
  @Nullable
  private static MainController visibleChatList (BaseActivity activity) {
    XgramTwoPane twoPane = activity.xgramTwoPane();
    MainController left = twoPane != null ? twoPane.leftMain() : null;
    if (left != null) {
      return left;
    }
    ViewController<?> top = topController(activity);
    return top instanceof MainController ? (MainController) top : null;
  }

  private static void onEscape (BaseActivity activity) {
    if (activity.getCurrentPopupWindow() == null) {
      ViewController<?> top = topController(activity);
      if (top instanceof MessagesController && ((MessagesController) top).xgramCloseReplyByKeyboard()) {
        return;
      }
    }
    // At the chat list there is nothing to close: the app stays open (unlike Back).
    activity.handleOnBackPress(false, true);
  }

  private static boolean search (BaseActivity activity, boolean chats) {
    if (activity.getCurrentPopupWindow() != null) {
      return false;
    }
    if (!chats) {
      ViewController<?> top = topController(activity);
      if (top instanceof MessagesController) {
        return ((MessagesController) top).xgramSearchByKeyboard();
      }
    }
    MainController list = visibleChatList(activity);
    if (list == null) {
      return false;
    }
    if (!list.inSearchMode()) {
      list.onMenuItemPressed(R.id.menu_btn_search, null);
    }
    return true;
  }

  private static boolean switchFolder (BaseActivity activity, int step) {
    if (activity.getCurrentPopupWindow() != null) {
      return false;
    }
    MainController list = visibleChatList(activity);
    ViewPager pager = list != null ? list.getViewPager() : null;
    PagerAdapter adapter = pager != null ? pager.getAdapter() : null;
    int count = adapter != null ? adapter.getCount() : 0;
    if (list == null || count < 2 || list.inSearchMode() || list.inSelectMode()) {
      return false;
    }
    int position = list.getCurrentPagerItemPosition();
    list.onPagerItemClick(((position + step) % count + count) % count);
    return true;
  }

  // Meta+/ list

  /** Adds XGram's shortcuts to the system keyboard shortcuts helper. */
  @RequiresApi(Build.VERSION_CODES.N)
  public static void provideShortcuts (List<android.view.KeyboardShortcutGroup> data) {
    Shortcuts.provide(data);
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private static final class Shortcuts {
    static void provide (List<android.view.KeyboardShortcutGroup> data) {
      boolean enterSends = XgramSettings.instance().enterToSend() != XgramSettings.ENTER_TO_SEND_OFF;

      List<android.view.KeyboardShortcutInfo> box = new ArrayList<>();
      // Ctrl+Enter always sends; it is listed when Enter adds a new line.
      box.add(info(R.string.XgramKeySend, KeyEvent.KEYCODE_ENTER, enterSends ? 0 : KeyEvent.META_CTRL_ON));
      box.add(info(R.string.XgramKeyNewLine, KeyEvent.KEYCODE_ENTER, enterSends ? KeyEvent.META_SHIFT_ON : 0));
      box.add(info(R.string.XgramKeyEditLast, KeyEvent.KEYCODE_DPAD_UP, 0));
      data.add(new android.view.KeyboardShortcutGroup(Lang.getString(R.string.XgramMessageBox), box));

      List<android.view.KeyboardShortcutInfo> navigation = new ArrayList<>();
      navigation.add(info(R.string.XgramKeySearch, KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON));
      navigation.add(info(R.string.XgramKeySearchChats, KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON));
      navigation.add(info(R.string.XgramKeyNextFolder, KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON));
      navigation.add(info(R.string.XgramKeyPreviousFolder, KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON));
      navigation.add(info(R.string.XgramKeyBack, KeyEvent.KEYCODE_ESCAPE, 0));
      data.add(new android.view.KeyboardShortcutGroup(Lang.getString(R.string.XgramKeysNavigation), navigation));
    }

    private static android.view.KeyboardShortcutInfo info (int label, int keyCode, int modifiers) {
      return new android.view.KeyboardShortcutInfo(Lang.getString(label), keyCode, modifiers);
    }
  }
}
