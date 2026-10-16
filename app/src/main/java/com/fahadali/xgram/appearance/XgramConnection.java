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
package com.fahadali.xgram.appearance;

import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.GlobalConnectionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;

/**
 * XGram M3E: what the app shows about the current account's connection, debounced so short blips never show.
 * Material 3 has no connectivity component; the chat list pill, the titles and the chat subtitle are driven from here,
 * nothing is drawn over the system status bar. Main thread only.
 */
public final class XgramConnection implements GlobalConnectionListener, GlobalAccountListener {
  public static final int HIDDEN = 0, PROGRESS = 1, OFFLINE = 2, STUCK = 3, CONNECTED_FLASH = 4;

  public interface Listener {
    void onConnectionDisplay (int state, CharSequence text, boolean animated);
  }

  private static final long PROGRESS_DELAY = 700L, UPDATING_DELAY = 1000L, OFFLINE_DELAY = 500L;
  private static final long STUCK_DELAY = 10_000L, FLASH_MIN_VISIBLE = 2000L, FLASH_DURATION = 1200L;

  private static XgramConnection instance;

  public static XgramConnection instance () {
    if (instance == null) {
      instance = new XgramConnection();
    }
    return instance;
  }

  /** True for the states that put something on screen in a title or subtitle (the flash is the pill's only). */
  public static boolean isProblem (int state) {
    return state == PROGRESS || state == OFFLINE || state == STUCK;
  }

  private final ArrayList<Listener> listeners = new ArrayList<>();

  private @ConnectionState int raw = ConnectionState.CONNECTED;
  private String rawText = "";
  private long episodeStart; // uptime when the raw state left CONNECTED, 0 while connected
  private long connectingSince; // uptime when the raw state became CONNECTING(_TO_PROXY), 0 otherwise
  private boolean episodeNotJustUpdating;

  private int display = HIDDEN;
  private CharSequence displayText = "";
  private long visibleSince;

  private final Runnable enterRunnable = () -> evaluate(true, true);
  private final Runnable stuckRunnable = () -> evaluate(true, false);
  private final Runnable flashEndRunnable = () -> setDisplay(HIDDEN, displayText, true);
  private final Runnable dispatchRunnable = this::onRawChanged;

  private XgramConnection () {
    TdlibManager.instance().global().addConnectionListener(this);
    TdlibManager.instance().global().addAccountListener(this);
    readRaw(TdlibManager.instance().current());
    if (raw != ConnectionState.CONNECTED) {
      long now = SystemClock.uptimeMillis();
      episodeStart = now;
      connectingSince = isConnecting(raw) ? now : 0;
      episodeNotJustUpdating = raw != ConnectionState.UPDATING;
      scheduleEnter(now);
      scheduleStuck();
    }
  }

  // Public API

  public void addListener (@NonNull Listener l) {
    if (!listeners.contains(l)) {
      listeners.add(l);
    }
  }

  public void removeListener (@NonNull Listener l) {
    listeners.remove(l);
  }

  public int getState () {
    return display;
  }

  public CharSequence getText () {
    return displayText;
  }

  /** Uptime of the last change of the display state. */
  public long getStateSince () {
    return stateSince;
  }

  private long stateSince = SystemClock.uptimeMillis();

  /** The stuck pill's tap: TDLib drops its reconnect back-off, the pill shows progress again for another 10 s. */
  public void retry () {
    TdlibManager.instance().xgramRetryConnection();
    if (display == STUCK) {
      connectingSince = SystemClock.uptimeMillis();
      scheduleStuck();
      setDisplay(PROGRESS, rawText, true);
    }
  }

  // Listeners

  @Override
  public void onConnectionStateChanged (Tdlib tdlib, int newState, boolean isCurrent) {
    if (isCurrent) {
      dispatch();
    }
  }

  @Override
  public void onConnectionDisplayStatusChanged (Tdlib tdlib, boolean isCurrent) {
    if (isCurrent) {
      dispatch();
    }
  }

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      UI.post(this::resetForAccount);
    } else {
      resetForAccount();
    }
  }

  private void dispatch () {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      UI.cancel(dispatchRunnable);
      UI.post(dispatchRunnable);
    } else {
      onRawChanged();
    }
  }

  // State machine

  private static boolean isConnecting (int raw) {
    return raw == ConnectionState.CONNECTING || raw == ConnectionState.CONNECTING_TO_PROXY;
  }

  private void readRaw (@Nullable Tdlib tdlib) {
    if (tdlib == null) {
      raw = ConnectionState.CONNECTED;
      rawText = "";
      return;
    }
    raw = tdlib.connectionState();
    String text = tdlib.connectionStateText();
    rawText = text != null ? text : "";
  }

  private void resetForAccount () {
    cancelTimers();
    episodeStart = connectingSince = 0;
    episodeNotJustUpdating = false;
    raw = ConnectionState.CONNECTED;
    setDisplay(HIDDEN, "", false);
    onRawChanged();
  }

  private void cancelTimers () {
    UI.cancel(enterRunnable);
    UI.cancel(stuckRunnable);
    UI.cancel(flashEndRunnable);
  }

  private void onRawChanged () {
    final int prevRaw = raw;
    readRaw(TdlibManager.instance().current());
    final long now = SystemClock.uptimeMillis();
    if (raw == ConnectionState.CONNECTED) {
      UI.cancel(enterRunnable);
      UI.cancel(stuckRunnable);
      episodeStart = connectingSince = 0;
      boolean notJustUpdating = episodeNotJustUpdating;
      episodeNotJustUpdating = false;
      if (display == HIDDEN || display == CONNECTED_FLASH) {
        return;
      }
      if (now - visibleSince >= FLASH_MIN_VISIBLE && notJustUpdating) {
        setDisplay(CONNECTED_FLASH, Lang.getString(R.string.Connected), true);
        UI.cancel(flashEndRunnable);
        UI.post(flashEndRunnable, FLASH_DURATION);
      } else {
        setDisplay(HIDDEN, displayText, true);
      }
      return;
    }
    if (episodeStart == 0) {
      episodeStart = now;
    }
    if (raw != ConnectionState.UPDATING) {
      episodeNotJustUpdating = true;
    }
    if (isConnecting(raw)) {
      if (connectingSince == 0 || !isConnecting(prevRaw)) {
        connectingSince = now;
        scheduleStuck();
      }
    } else {
      connectingSince = 0;
      UI.cancel(stuckRunnable);
    }
    if (display == HIDDEN) {
      scheduleEnter(now);
    } else {
      UI.cancel(flashEndRunnable);
      evaluate(true, false);
    }
  }

  private long enterDelay () {
    switch (raw) {
      case ConnectionState.WAITING_FOR_NETWORK:
        return OFFLINE_DELAY;
      case ConnectionState.UPDATING:
        return UPDATING_DELAY;
      default:
        return PROGRESS_DELAY;
    }
  }

  private void scheduleEnter (long now) {
    UI.cancel(enterRunnable);
    long delay = Math.max(0L, episodeStart + enterDelay() - now);
    UI.post(enterRunnable, delay);
  }

  private void scheduleStuck () {
    UI.cancel(stuckRunnable);
    if (connectingSince != 0) {
      UI.post(stuckRunnable, Math.max(0L, connectingSince + STUCK_DELAY - SystemClock.uptimeMillis()));
    }
  }

  /** Applies the state the raw one maps to. {@code fromHidden}: the debounce has passed. */
  private void evaluate (boolean animated, boolean fromHidden) {
    if (raw == ConnectionState.CONNECTED) {
      return;
    }
    if (fromHidden && display != HIDDEN && display != CONNECTED_FLASH) {
      return;
    }
    final int target;
    if (raw == ConnectionState.WAITING_FOR_NETWORK) {
      target = OFFLINE;
    } else if (isConnecting(raw) && connectingSince != 0 && SystemClock.uptimeMillis() - connectingSince >= STUCK_DELAY) {
      target = STUCK;
    } else {
      target = PROGRESS;
    }
    setDisplay(target, rawText, animated);
  }

  private void setDisplay (int state, CharSequence text, boolean animated) {
    if (text == null) {
      text = "";
    }
    if (display == state && displayText.toString().contentEquals(text)) {
      return;
    }
    final boolean wasVisible = display != HIDDEN;
    display = state;
    displayText = text;
    stateSince = SystemClock.uptimeMillis();
    if (state != HIDDEN && !wasVisible) {
      visibleSince = stateSince;
    }
    for (int i = listeners.size() - 1; i >= 0; i--) {
      if (i < listeners.size()) {
        listeners.get(i).onConnectionDisplay(state, text, animated);
      }
    }
  }
}
