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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Build;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramMotion;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.reference.ReferenceList;

/**
 * The chat list view (Settings › Themes › Chat list view, XGram › Chat list): One line (XGram's, as in official
 * Telegram and iMe), Two lines, Three lines and Three lines with bigger text (Telegram X's). The picker's tiles, the
 * "Bigger text" switch, Telegram X's radio popup with One line added, and the change itself: when the view changes the
 * rows spring to the new height (SPATIAL_DEFAULT with Expressive design, Telegram X's 220ms decelerate without, at once
 * with Reduce motion or animations off). Every chat list reads the same spring (ChatView.Metrics). Main thread only.
 */
public final class XgramChatListMode {
  private XgramChatListMode () { }

  // The picker

  public static final int TILE_ONE_LINE = 0, TILE_TWO_LINES = 1, TILE_THREE_LINES = 2, TILE_COUNT = 3;

  public static boolean isThreeLines (int mode) {
    return mode == Settings.CHAT_MODE_3LINE || mode == Settings.CHAT_MODE_3LINE_BIG;
  }

  /** The picker's tile of {@code mode}; an unknown mode reads as two lines, as every switch over the mode does. */
  public static int tileOf (int mode) {
    switch (mode) {
      case Settings.CHAT_MODE_1LINE:
        return TILE_ONE_LINE;
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return TILE_THREE_LINES;
      case Settings.CHAT_MODE_2LINE:
      default:
        return TILE_TWO_LINES;
    }
  }

  /** The mode a tile picks: three lines take the "Bigger text" switch with them. */
  public static int modeOfTile (int tile) {
    switch (tile) {
      case TILE_ONE_LINE:
        return Settings.CHAT_MODE_1LINE;
      case TILE_THREE_LINES:
        return XgramSettings.instance().chatListBigText() ? Settings.CHAT_MODE_3LINE_BIG : Settings.CHAT_MODE_3LINE;
      case TILE_TWO_LINES:
      default:
        return Settings.CHAT_MODE_2LINE;
    }
  }

  public static String tileName (int tile) {
    switch (tile) {
      case TILE_ONE_LINE:
        return Lang.getString(R.string.XgramChatListOneLine);
      case TILE_THREE_LINES:
        return Lang.getString(R.string.ChatListStyle2);
      case TILE_TWO_LINES:
      default:
        return Lang.getString(R.string.ChatListStyle1);
    }
  }

  /** The current view's name, as Telegram X's popup lists it. */
  public static String name (int mode) {
    switch (mode) {
      case Settings.CHAT_MODE_1LINE:
        return Lang.getString(R.string.XgramChatListOneLine);
      case Settings.CHAT_MODE_3LINE:
        return Lang.getString(R.string.ChatListStyle2);
      case Settings.CHAT_MODE_3LINE_BIG:
        return Lang.getString(R.string.ChatListStyle3);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Lang.getString(R.string.ChatListStyle1);
    }
  }

  /** The switch's state: on with three lines and bigger text, or (off three lines) the choice three lines will use. */
  public static boolean isBigTextChecked () {
    final int mode = Settings.instance().getChatListMode();
    return mode == Settings.CHAT_MODE_3LINE_BIG || (!isThreeLines(mode) && XgramSettings.instance().chatListBigText());
  }

  /** The switch only changes the rows with three lines; otherwise it is kept for when they are picked. */
  public static boolean isBigTextEnabled () {
    return isThreeLines(Settings.instance().getChatListMode());
  }

  public static void setBigText (boolean bigText) {
    XgramSettings.instance().setChatListBigText(bigText);
    if (isThreeLines(Settings.instance().getChatListMode())) {
      Settings.instance().setChatListMode(bigText ? Settings.CHAT_MODE_3LINE_BIG : Settings.CHAT_MODE_3LINE);
    }
  }

  /** Telegram X's chat list style popup (Expressive design off) with One line first. */
  public static void showModePicker (ViewController<?> c, @Nullable Runnable after) {
    final int mode = Settings.instance().getChatListMode();
    final int tile = tileOf(mode);
    c.showSettings(new SettingsWrapBuilder(R.id.btn_chatListStyle).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle0, 0, R.string.XgramChatListOneLine, R.id.btn_chatListStyle, mode == Settings.CHAT_MODE_1LINE),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle1, 0, R.string.ChatListStyle1, R.id.btn_chatListStyle, tile == TILE_TWO_LINES),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle2, 0, R.string.ChatListStyle2, R.id.btn_chatListStyle, mode == Settings.CHAT_MODE_3LINE),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatListStyle3, 0, R.string.ChatListStyle3, R.id.btn_chatListStyle, mode == Settings.CHAT_MODE_3LINE_BIG)
    }).setIntDelegate((id, result) -> {
      final int picked = result.get(R.id.btn_chatListStyle);
      final int newMode;
      if (picked == R.id.btn_chatListStyle0) {
        newMode = Settings.CHAT_MODE_1LINE;
      } else if (picked == R.id.btn_chatListStyle1) {
        newMode = Settings.CHAT_MODE_2LINE;
      } else if (picked == R.id.btn_chatListStyle2) {
        newMode = Settings.CHAT_MODE_3LINE;
      } else if (picked == R.id.btn_chatListStyle3) {
        newMode = Settings.CHAT_MODE_3LINE_BIG;
      } else {
        newMode = Settings.instance().getChatListMode();
      }
      Settings.instance().setChatListMode(newMode);
      if (after != null) {
        after.run();
      }
    }).setAllowResize(false));
  }

  // The change

  /** A chat list following the spring: a frame (lay the rows out again) and the end. */
  public interface Listener {
    void onChatListModeFrame ();
    void onChatListModeEnd ();
  }

  private static final ReferenceList<Listener> listeners = new ReferenceList<>();
  /** Where the rows were when the change started (the old mode, or mid-spring of an interrupted change). */
  private static final ChatView.Metrics from = new ChatView.Metrics();
  /** Where they are now, worked out once per frame for every row. */
  private static final ChatView.Metrics current = new ChatView.Metrics();
  private static int targetMode;
  private static float fraction = 1f;
  /** Where the change that the current one interrupted had got to (1 when it interrupted none). */
  private static float interruptedFraction = 1f;
  private static boolean animating;
  private static @Nullable ValueAnimator animator;

  public static void addFrameListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeFrameListener (Listener listener) {
    listeners.remove(listener);
  }

  public static boolean isAnimating () {
    return animating;
  }

  /** The mode the rows spring to. */
  public static int targetMode () {
    return targetMode;
  }

  /** 0 to 1 (a little past 1 at the spring's overshoot). 1 when not animating. */
  public static float fraction () {
    return animating ? fraction : 1f;
  }

  /**
   * For the rows told of a change (TGChat.checkChatListMode, right after {@link #onModeChanged}): the fraction the
   * change it interrupted had reached, 0 to 1, or 1 for a change from rest. {@link #fraction} is 0 by then.
   */
  public static float interruptedFraction () {
    return interruptedFraction;
  }

  /** The geometry of a row in {@code mode} now: the spring's while it goes to that mode, the mode's own otherwise. */
  public static ChatView.Metrics metrics (int mode) {
    return animating && mode == targetMode ? current : ChatView.metricsOf(mode);
  }

  /** The height of a chat list row now (scroll to top, the archive snap, the content height). */
  public static int rowHeightPx () {
    return Math.round(metrics(Settings.instance().getChatListMode()).height);
  }

  private static boolean canAnimate () {
    if (XgramMotion.reduce()) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
      return false;
    }
    return XgramMotion.duration(XgramExpressive.SPATIAL_DEFAULT, 220L) > 0;
  }

  /** Settings.setChatListMode, before it tells the chat lists. */
  public static void onModeChanged (int oldMode, int newMode) {
    // Telegram X's popup and the tiles keep the "Bigger text" switch on the three lines choice last made (the one left,
    // when three lines are left for another view)
    final int threeLines = isThreeLines(newMode) ? newMode : isThreeLines(oldMode) ? oldMode : 0;
    if (threeLines != 0) {
      XgramSettings.instance().setChatListBigText(threeLines == Settings.CHAT_MODE_3LINE_BIG);
    }
    if (!UI.inUiThread()) {
      UI.post(XgramChatListMode::stop); // the rows and the animator live on the main thread
      return;
    }
    if (!canAnimate()) {
      stop();
      return;
    }
    if (animating) {
      from.set(current); // interrupted: on from where the rows are
    } else {
      from.set(ChatView.metricsOf(oldMode));
    }
    final ValueAnimator old = animator;
    animator = null;
    if (old != null) {
      old.cancel(); // its end is ignored: animator no longer points at it
    }
    targetMode = newMode;
    interruptedFraction = animating ? Math.max(0f, Math.min(1f, fraction)) : 1f;
    fraction = 0f;
    current.set(from);
    animating = true;
    final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
    a.setInterpolator(XgramMotion.interp(XgramExpressive.SPATIAL_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR));
    a.setDuration(XgramMotion.duration(XgramExpressive.SPATIAL_DEFAULT, 220L));
    a.addUpdateListener(animation -> {
      if (animator == animation) {
        onFrame((Float) animation.getAnimatedValue());
      }
    });
    a.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (animator == animation) {
          animator = null;
          finish();
        }
      }
    });
    animator = a;
    a.start();
  }

  private static void onFrame (float value) {
    fraction = value;
    current.lerp(from, ChatView.metricsOf(targetMode), value);
    for (Listener listener : listeners) {
      listener.onChatListModeFrame();
    }
  }

  private static void finish () {
    animating = false;
    fraction = 1f;
    for (Listener listener : listeners) {
      listener.onChatListModeEnd();
    }
  }

  /** A change without the spring: a running one ends where the new mode is. */
  private static void stop () {
    final ValueAnimator old = animator;
    animator = null;
    if (old != null) {
      old.cancel();
    }
    if (animating) {
      finish();
    }
  }
}
