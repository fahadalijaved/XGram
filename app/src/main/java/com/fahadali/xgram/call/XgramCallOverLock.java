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
package com.fahadali.xgram.call;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.layout.XgramTwoPane;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.navigation.DrawerController;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * The call screen over the lock screen, as in Telegram and WhatsApp: a call that rings while the phone is locked or its
 * screen is off turns the screen on and shows its screen there.
 * <p>
 * Telegram X never let MainActivity show over the lock screen: the full-screen intent of the incoming call notification
 * started it behind the keyguard with the screen off, so only the notification showed until the phone was unlocked. Now
 * a call intent (Intents.ACTION_OPEN_CALL: the full-screen intent, or a tap on a call notification) that comes with the
 * phone locked or its screen off arms this before the activity resumes: show when locked, and turn the screen on while
 * the call rings. The call screen (CallController)
 * that opens then holds it while it is focused. Nothing else of the app shows over the lock screen:
 * <ul>
 *   <li>While the keyguard is up, the popups, dialogs, other windows and PiPs that were open hide (as the popups do
 *   under the passcode; BaseActivity.xgramSetWindowsHiddenForLock), the side menu closes,
 *   two panes and the navigation rail go (XgramTwoPane), and a cover hides the app until the call screen is focused:
 *   no frame of the chat that was open while the call screen slides in. They all come back once the lock screen is
 *   over the app again.</li>
 *   <li>Leaving the call screen drops it. Back lets the lock screen come back over the call screen, which stays under it
 *   (the call goes on, or keeps ringing in its notification). Minimize and Message ask to unlock first (Android 8+;
 *   older ones go behind the lock screen and do it there). The call ending closes the screen behind the lock screen.
 *   Another screen on top, Home, Recents or another app (the activity stopping with the screen on) drop it too.</li>
 *   <li>Any intent other than a call's (a link, a share from outside) waits for the phone to be unlocked (afterUnlock).</li>
 *   <li>The phone unlocked while the call screen is over the lock screen (fingerprint, face or PIN in place, the unlock
 *   Minimize, Message or an intent asked for) ends it at once (release): the flags go, what was hidden comes back, the
 *   call screen stays open and the call goes on. The app is like any other then: locked again, all of it goes behind
 *   the lock screen. Only a new call intent with the phone locked or asleep shows it over the lock screen again.</li>
 *   <li>The app passcode keeps Telegram X's order: the passcode first (now over the lock screen, with the screen on),
 *   then the call screen. That one opens under the passcode at once, so nothing else shows as the passcode goes.</li>
 * </ul>
 * Turn-screen-on is cleared as soon as the window has focus (the screen is on by then) or when this drops, so later
 * launches never wake the screen. The ringing holds no screen wake lock of its own: the full-screen launch is what turns
 * the screen on, once per call (TGCallService posts the incoming notification again without its full-screen intent
 * after the app has shown the call over the lock screen, so Back or the power button on the ringing screen don't bring
 * it straight back; unlocked with the screen on it keeps it, there it only keeps the heads-up on screen).
 */
public final class XgramCallOverLock {
  /** First look for the call screen after a call intent, then again every second while it may still come. */
  private static final long CHECK_DELAY = 1500L, RECHECK_DELAY = 1000L;
  private static final int MAX_RECHECKS = 10;
  /** Longest wait for the activity to go behind the lock screen, and how often that is checked. */
  private static final long HIDE_TIMEOUT = 1000L, HIDE_POLL = 100L;
  private static final long COVER_FADE_DURATION = 150L;

  private final BaseActivity context;
  /** What the activity was last told. */
  private boolean showWhenLocked, turnScreenOn;
  /** A call intent set the flags and no call screen holds them yet. */
  private boolean armed;
  @Nullable private CallController holder;
  @Nullable private View cover;
  private boolean windowsHidden;
  private int recheckCount;
  private boolean isDestroyed;
  /** An intent that waits for the phone to be unlocked (afterUnlock). */
  @Nullable private Runnable unlockAct;
  /** ACTION_USER_PRESENT, registered while the activity shows over the lock screen: unlocked in place (release). */
  @Nullable private BroadcastReceiver unlockReceiver;
  /** USER_PRESENT before KeyguardManager caught up with the unlock: looked at again in a moment. */
  private final Runnable unlockRecheck = this::releaseIfUnlocked;

  private final Runnable check = this::runCheck;

  public XgramCallOverLock (BaseActivity context) {
    this.context = context;
  }

  // State

  public static boolean isKeyguardLocked (Context context) {
    KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    return keyguard != null && keyguard.isKeyguardLocked();
  }

  @SuppressWarnings("deprecation")
  private static boolean isInteractive (Context context) {
    PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (power == null) {
      return true;
    }
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH ? power.isInteractive() : power.isScreenOn();
  }

  /**
   * The phone is unlocked and in use: the screen is on, the keyguard is gone (occluded by the call screen it still counts
   * as locked) and the device no longer needs its PIN, pattern or password. The screen off doesn't count: a call intent
   * in the lock delay (the keyguard not up yet) still has to turn the screen on (see onActivityResume).
   */
  private static boolean isUnlocked (Context context) {
    if (!isInteractive(context) || isKeyguardLocked(context)) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
      return keyguard == null || !keyguard.isDeviceLocked();
    }
    return true;
  }

  /** The call screen is over the keyguard right now: nothing else of the app may show (XgramTwoPane asks). */
  public static boolean hidesApp (@Nullable BaseActivity context) {
    return context != null && context.xgramCallOverLock().hidesApp();
  }

  public boolean hidesApp () {
    return showWhenLocked && !isDestroyed && isKeyguardLocked(context);
  }

  /** The activity is shown over the lock screen whenever the keyguard is up (it comes back over it by itself). */
  public boolean showsWhenLocked () {
    return showWhenLocked && !isDestroyed;
  }

  /**
   * TGCallService: the app has shown the ringing call where it comes back by itself, over the lock screen. A call shown
   * with the phone unlocked doesn't count: its full-screen intent stays, so the screen turns on with the call screen
   * once the phone locks (screen timeout) while it still rings. Nor does a call intent in the lock delay (screen off,
   * no keyguard yet): the screen turns on without the lock screen, and that ends the state (release).
   */
  public static boolean showsCallOverLock () {
    final BaseActivity context = UI.getUiContext();
    return context != null && context.xgramCallOverLock().hidesApp();
  }

  /**
   * TGCallService: a full-screen intent launches the app only on the lock screen or with the screen off. Unlocked with
   * the screen on, it only keeps the incoming call's heads-up on screen until it is answered.
   */
  public static boolean isLockedOrAsleep (Context context) {
    return isKeyguardLocked(context) || !isInteractive(context);
  }

  /**
   * MainActivity.handleIntent: over the lock screen the call screen opens under the app passcode at once (Telegram X
   * opens it once the passcode is gone, which showed the screen that was open before for a moment).
   */
  public boolean opensUnderPasscode () {
    return armed && hidesApp();
  }

  /** BaseActivity: the popups hidden for the lock screen stay hidden when the passcode goes. */
  public boolean hidesWindows () {
    return windowsHidden;
  }

  /**
   * BaseActivity.xgramTrackDialog: the call screen holds it and shows (no cover), so a dialog that opens now comes from
   * it (Accept with no connection, a missing microphone permission) and may show. One that opens before hides.
   */
  public boolean showsCallScreen () {
    return !isDestroyed && holder != null && cover == null && !holder.isDestroyed() && holder.isFocused();
  }

  // Call intent

  /**
   * MainActivity.onCreate. Android keeps show-when-locked and turn-screen-on (8.1+) in its own record of the activity,
   * which outlives this instance: a relaunch (a configuration change the manifest doesn't handle) or a restart after
   * the process died would show the new instance over the lock screen without anything guarding it. It starts cleared.
   */
  public void onCreate (@Nullable Intent intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      try {
        context.setShowWhenLocked(false);
        context.setTurnScreenOn(false);
      } catch (Throwable t) {
        Log.e("XGram: call screen over the lock screen", t);
      }
    }
    onIntent(intent);
  }

  /**
   * MainActivity.onCreate / onNewIntent, before the intent is handled: a call intent while a call is on and the phone
   * is locked or asleep arms it. This is the only way in: once the phone is unlocked (release), nothing else shows the
   * app over the lock screen again.
   */
  public void onIntent (@Nullable Intent intent) {
    if (isDestroyed) {
      return;
    }
    final String action = intent != null ? Intents.getCleanAction(intent.getAction()) : null;
    if (!Intents.ACTION_OPEN_CALL.equals(action)) {
      return;
    }
    if (!isLockedOrAsleep(context)) {
      // Unlocked with the screen on (a tap on the call's notification): the call screen opens as any screen does. The
      // full-screen intent stays (showsCallOverLock), so the lock screen brings the call back over it if it locks
      releaseIfUnlocked();
      return;
    }
    final TdApi.Call call = TdlibManager.instance().calls().getCurrentCall();
    if (call == null) {
      return; // MainActivity tells the call is no longer active
    }
    final boolean ringing = !call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR;
    armed = true;
    recheckCount = 0;
    setFlags(true, turnScreenOn || ringing);
    if (isKeyguardLocked(context)) {
      clearStage();
    }
    UI.removePendingRunnable(check);
    UI.post(check, CHECK_DELAY);
  }

  /** MainActivity.openCallController: the call is no longer active, there is nothing to show over the lock screen. */
  public void onCallGone () {
    if (armed && holder == null) {
      drop();
    }
  }

  /** MainActivity.openCallController: the call intent went to the call screen already on top. */
  public void onCallScreenShown (CallController c) {
    if (armed && c.isFocused() && !c.isDestroyed()) {
      hold(c);
    }
  }

  // Call screen

  /** CallController.onFocusStateChanged. */
  public void onCallScreenFocus (CallController c, boolean isFocused) {
    if (isDestroyed) {
      return;
    }
    if (isFocused) {
      if (armed && !c.isDestroyed()) {
        hold(c);
      }
      return;
    }
    if (holder != c || context.isPasscodeShowing()) {
      return; // the passcode coming up over the call screen covers everything: it stays over the lock screen
    }
    ViewController<?> top = currentScreen();
    if (top instanceof CallController && top != c && !top.isDestroyed()) {
      // Another call's screen comes on top of this one (Call again): it takes over once it is focused
      holder = null;
      armed = true;
      recheckCount = 0;
      UI.removePendingRunnable(check);
      UI.post(check, CHECK_DELAY);
      return;
    }
    drop();
  }

  /** CallController.destroy. */
  public void onCallScreenDestroy (CallController c) {
    if (isDestroyed) {
      return;
    }
    if (holder == c) {
      drop();
    } else if (armed && holder == null) {
      // Never shown (navigation busy) or closed before it was focused: unless another call screen is coming
      ViewController<?> top = currentScreen();
      if (!(top instanceof CallController && top != c && !top.isDestroyed())) {
        drop();
      }
    }
  }

  /**
   * CallController.performOnBackPressed: over the lock screen Back only lets the lock screen come back. The call
   * screen stays under it (the call goes on, or keeps ringing in its notification), so nothing else shows on the way.
   */
  public boolean onCallScreenBack (CallController c, boolean commit) {
    if (!hidesApp() || (holder != null && holder != c)) {
      return false;
    }
    if (commit) {
      drop();
    }
    return true;
  }

  /**
   * Minimize and Message: they go somewhere else in the app, which over the lock screen needs the phone unlocked
   * first. Android 8+ asks for it and goes on once it is unlocked (stays on the call screen otherwise); older versions
   * let the lock screen come back and go on behind it.
   */
  public void leave (CallController c, Runnable act) {
    if (!hidesApp()) {
      releaseIfUnlocked(); // unlocked in place, not seen yet: the state ends before the screen goes
      act.run();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
      if (keyguard != null) {
        try {
          keyguard.requestDismissKeyguard(context, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded () {
              release(); // unlocked: an app like any other now, before it goes somewhere else
              if (!c.isDestroyed() && !context.isFinishing()) {
                act.run();
              }
            }
          });
          return;
        } catch (Throwable t) {
          Log.e("XGram: unlock request from the call screen failed", t);
        }
      }
    }
    drop();
    whenHidden(() -> {
      if (!c.isDestroyed()) {
        act.run();
      }
    });
  }

  /**
   * MainActivity.handleIntent: over the lock screen only the call screen shows, so any other intent (a t.me link, a
   * share, an invite or proxy link from a watch, an NFC tag or another app) waits for the phone to be unlocked. Android
   * 8+ asks for it and goes on once it is (the intent is dropped when the unlock is cancelled, or when the call screen
   * leaves the lock screen first); older versions let the lock screen come back and go on behind it. False when
   * nothing is over the lock screen: the intent goes on now.
   */
  public boolean afterUnlock (Runnable act) {
    if (!hidesApp()) {
      releaseIfUnlocked(); // unlocked in place, not seen yet: the state ends before the intent opens anything
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
      if (keyguard != null) {
        try {
          unlockAct = act; // one at a time: a newer intent replaces it (Android cancels the older request too)
          keyguard.requestDismissKeyguard(context, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded () {
              release(); // unlocked: the state ends first, so what the intent opens is the app's like any other
              runUnlockAct(act);
            }

            @Override
            public void onDismissCancelled () {
              if (unlockAct == act) {
                unlockAct = null;
              }
            }

            @Override
            public void onDismissError () {
              // It can't ask now: the lock screen is on its way out already (a notification tapped and unlocked for)
              // or the screen is off. It goes on once the lock screen is gone: now, or checked again in a moment (the
              // unlock seen meanwhile, release, runs it too). Still locked then, it is dropped: nothing would ask for
              // the unlock any more, and it must not run much later (unlocked in place on another occasion)
              if (releaseIfUnlocked()) {
                runUnlockAct(act);
              } else {
                UI.post(() -> {
                  if (releaseIfUnlocked()) {
                    runUnlockAct(act);
                  } else if (unlockAct == act) {
                    unlockAct = null;
                  }
                }, HIDE_TIMEOUT);
              }
            }
          });
          return true;
        } catch (Throwable t) {
          if (unlockAct == act) {
            unlockAct = null;
          }
          Log.e("XGram: unlock request for an intent failed", t);
        }
      }
    }
    drop();
    whenHidden(() -> {
      if (!isDestroyed && !context.isFinishing()) {
        act.run();
      }
    });
    return true;
  }

  private void runUnlockAct (Runnable act) {
    if (unlockAct != act) {
      return; // ran already, replaced, or dropped with the state
    }
    unlockAct = null;
    if (!isDestroyed && !context.isFinishing()) {
      act.run();
    }
  }

  /** CallController.closeCall: over the lock screen the lock screen comes back first, the screen closes behind it. */
  public void closeBehindLock (CallController c, Runnable close) {
    if ((holder != c && !armed) || !hidesApp()) {
      close.run();
      return;
    }
    drop();
    whenHidden(() -> {
      if (!c.isDestroyed()) {
        close.run();
      }
    });
  }

  /** CallController.swipeNavigationEnabled: no swipe that shows the screen under the call screen. */
  public boolean blocksSwipeBack () {
    return hidesApp();
  }

  // Activity

  /** BaseActivity.onStart: shown over the lock screen again (the screen turned back on during the call). */
  public void onActivityStart () {
    if (isDestroyed || !showWhenLocked || !isKeyguardLocked(context)) {
      return;
    }
    // Every time, also when hidden already: what opened since (a window the call screen opened, one that came with the
    // screen off) must not show over the lock screen either. Each step skips what is hidden already (restoreStage
    // brings all of them back)
    windowsHidden = true;
    context.xgramSetWindowsHiddenForLock(true);
    if (holder == null) {
      showCover(); // the call screen is not focused yet: nothing under it shows meanwhile (hold or drop removes it)
    }
    closeDrawer();
    updatePanes();
  }

  /**
   * BaseActivity.onStop: stopped with the screen on is Home, Recents, Back on the passcode or another app: the lock
   * screen is no place for the app any more. Stopped by the screen turning off keeps it, the call screen comes back
   * over the lock screen with the screen.
   */
  public void onActivityStop () {
    if (!isDestroyed && (showWhenLocked || armed) && isInteractive(context)) {
      drop();
    }
  }

  /**
   * BaseActivity.onResume: unlocked meanwhile (from the screen off with a fingerprint, or the screen turned on in the
   * lock delay with no keyguard up) ends the state. Not while the screen is still off: turn-screen-on works as the
   * window shows, after this (isUnlocked).
   */
  public void onActivityResume () {
    if (!releaseIfUnlocked() && showWhenLocked && !windowsHidden) {
      onActivityStart(); // the keyguard came up in the lock delay after onStart saw none: hide now
    }
  }

  /**
   * BaseActivity.onWindowFocusChanged: the screen is on now, later launches must not turn it on. Unlocked with the app
   * on screen (back from the unlock it asked for, whose callback may not come when the lock screen was going already)
   * ends the state, and an intent that waited for the unlock goes on.
   */
  public void onWindowFocused () {
    if (isDestroyed) {
      return;
    }
    if (turnScreenOn) {
      setFlags(showWhenLocked, false);
    }
    if (!releaseIfUnlocked() && showWhenLocked && !windowsHidden) {
      onActivityStart();
    }
  }

  /** BaseActivity.onDestroy. */
  public void destroy () {
    setFlags(false, false); // the system keeps them for the next instance otherwise (see onCreate)
    isDestroyed = true;
    armed = false;
    holder = null;
    unlockAct = null;
    UI.removePendingRunnable(check);
    UI.removePendingRunnable(unlockRecheck);
    cover = null;
    updateUnlockReceiver(); // gone with the flags already: this makes sure
  }

  // Internal

  @Nullable
  private ViewController<?> currentScreen () {
    NavigationController navigation = context.navigation();
    return navigation != null ? navigation.getCurrentStackItem() : null;
  }

  private void hold (CallController c) {
    armed = false;
    holder = c;
    UI.removePendingRunnable(check);
    if (!showWhenLocked) {
      setFlags(true, turnScreenOn);
    }
    hideCover(true);
  }

  /** The call intent's screen did not come (yet): wait while it may, drop otherwise. */
  private void runCheck () {
    if (isDestroyed || !armed || holder != null) {
      return;
    }
    NavigationController navigation = context.navigation();
    ViewController<?> top = navigation != null ? navigation.getCurrentStackItem() : null;
    if (top instanceof CallController && !top.isDestroyed() && top.isFocused()) {
      hold((CallController) top);
      return;
    }
    boolean callOn = TdlibManager.instance().calls().getCurrentCall() != null;
    boolean passcode = context.isPasscodeShowing();
    boolean mayStillCome = callOn && (passcode || (top instanceof CallController && !top.isDestroyed()) ||
      navigation == null || navigation.isEmpty() || navigation.isAnimating());
    // The passcode waits as long as the call is on (the call screen opens under it once it's typed)
    if (mayStillCome && (passcode || recheckCount++ < MAX_RECHECKS)) {
      UI.post(check, RECHECK_DELAY);
    } else {
      drop();
    }
  }

  /**
   * Back to normal. The flags go at once (the lock screen comes back over the app), what was hidden for the lock
   * screen comes back once the app is behind it, so nothing of it shows as the lock screen comes.
   */
  private void drop () {
    armed = false;
    holder = null;
    unlockAct = null; // an intent still waiting for the unlock goes with the state (it came over the lock screen)
    UI.removePendingRunnable(check);
    if (isDestroyed || (!showWhenLocked && !turnScreenOn && cover == null && !windowsHidden)) {
      return;
    }
    final boolean locked = isKeyguardLocked(context);
    setFlags(false, false);
    if (locked) {
      whenHidden(this::restoreStage);
    } else {
      restoreStage();
    }
  }

  /** release, when the activity shows over the lock screen (or an intent waits) and the phone is unlocked now. */
  private boolean releaseIfUnlocked () {
    if (isDestroyed || (!showWhenLocked && unlockAct == null) || !isUnlocked(context)) {
      return false;
    }
    release();
    return true;
  }

  /**
   * Unlocked with the activity over the lock screen (fingerprint, face or PIN in place; the unlock Minimize, Message or
   * an intent asked for): it is an app like any other now. Show-when-locked and turn-screen-on go, what was hidden comes
   * back at once (unlocked, nothing of it can show over the lock screen), the call screen stays open and the call goes
   * on. Locked again, the whole app goes behind the lock screen, and only a new call intent with the phone locked shows
   * it there again (onIntent: armed and holder go). An intent that waited for the unlock goes on, once.
   */
  private void release () {
    if (isDestroyed) {
      return;
    }
    armed = false;
    holder = null;
    UI.removePendingRunnable(check);
    setFlags(false, false);
    restoreStage();
    final Runnable act = unlockAct;
    if (act != null) {
      UI.post(() -> runUnlockAct(act)); // runs once: the unlock callback may run it first (identity in runUnlockAct)
    }
  }

  /**
   * ACTION_USER_PRESENT while the activity shows over the lock screen: the keyguard went (unlocked in place) without a
   * focus change or a resume. Registered and unregistered with show-when-locked (setFlags) and on destroy.
   */
  private void updateUnlockReceiver () {
    final boolean needReceiver = showWhenLocked && !isDestroyed;
    if (needReceiver == (unlockReceiver != null)) {
      return;
    }
    if (needReceiver) {
      final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive (Context c, Intent intent) {
          if (unlockReceiver == this && intent != null && Intent.ACTION_USER_PRESENT.equals(intent.getAction()) && !releaseIfUnlocked()) {
            // The keyguard and trust state can lag behind the broadcast for a moment: look again
            UI.removePendingRunnable(unlockRecheck);
            UI.post(unlockRecheck, 250L);
            UI.post(unlockRecheck, 1000L);
          }
        }
      };
      try {
        // A system broadcast (protected): no exported flag needed, as BaseActivity's time receiver
        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        unlockReceiver = receiver;
      } catch (Throwable t) {
        Log.e("XGram: unlock receiver for the call screen over the lock screen", t);
      }
    } else {
      final BroadcastReceiver receiver = unlockReceiver;
      unlockReceiver = null;
      try {
        context.unregisterReceiver(receiver);
      } catch (Throwable t) {
        Log.e("XGram: unlock receiver for the call screen over the lock screen", t);
      }
    }
  }

  private void restoreStage () {
    if (isDestroyed || showWhenLocked) {
      return; // another call intent shows it over the lock screen again meanwhile
    }
    hideCover(false);
    if (windowsHidden) {
      windowsHidden = false;
      context.xgramSetWindowsHiddenForLock(false);
    }
    updatePanes();
  }

  /** Over the keyguard: only the call screen may show. */
  private void clearStage () {
    windowsHidden = true;
    context.xgramSetWindowsHiddenForLock(true); // again too: what opened since the last pass hides as well
    closeDrawer();
    showCover();
    updatePanes();
  }

  private void closeDrawer () {
    DrawerController drawer = context.getDrawer();
    if (drawer != null && (drawer.isVisible() || drawer.getShowFactor() > 0f)) {
      drawer.close(0f, null);
    }
  }

  private void updatePanes () {
    XgramTwoPane twoPane = context.xgramTwoPane();
    if (twoPane != null) {
      twoPane.update();
    }
  }

  /** Runs once the activity is no longer resumed (behind the lock screen), or after a second at the latest. */
  private void whenHidden (Runnable act) {
    final long start = SystemClock.uptimeMillis();
    UI.post(new Runnable() {
      @Override
      public void run () {
        if (context.getActivityState() != UI.State.RESUMED || context.isFinishing() || SystemClock.uptimeMillis() - start >= HIDE_TIMEOUT) {
          act.run();
        } else {
          UI.post(this, HIDE_POLL);
        }
      }
    });
  }

  private void showCover () {
    if (cover != null) {
      return;
    }
    View view = new View(context);
    // The call screen's own background (CallController.getPopupRestoreColor)
    view.setBackgroundColor(XgramExpressive.isEnabled() ? XgramExpressive.color(XgramExpressive.SURFACE) : 0xff000000);
    view.setClickable(true); // nothing under it takes a touch
    view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    cover = view;
    context.addToRoot(view, true); // under the passcode, when it shows
  }

  private void hideCover (boolean animated) {
    final View view = cover;
    if (view == null) {
      return;
    }
    cover = null;
    view.animate().cancel();
    if (animated && view.getParent() != null) {
      view.animate().alpha(0f).setDuration(COVER_FADE_DURATION).withEndAction(() -> context.removeFromRoot(view));
    } else {
      context.removeFromRoot(view);
    }
  }

  @SuppressWarnings("deprecation")
  private void setFlags (boolean showWhenLocked, boolean turnScreenOn) {
    if (this.showWhenLocked == showWhenLocked && this.turnScreenOn == turnScreenOn) {
      return;
    }
    this.showWhenLocked = showWhenLocked;
    this.turnScreenOn = turnScreenOn;
    updateUnlockReceiver(); // watches for the unlock while it shows over the lock screen
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        context.setShowWhenLocked(showWhenLocked);
        context.setTurnScreenOn(turnScreenOn);
      } else {
        final int mask = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        final int flags = (showWhenLocked ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0) | (turnScreenOn ? WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON : 0);
        context.getWindow().setFlags(flags, mask);
      }
    } catch (Throwable t) {
      Log.e("XGram: call screen over the lock screen", t);
    }
  }

  /**
   * Android 10+ only, in a class of its own so older versions never verify a reference to it (BaseActivity is checked
   * as a whole when it loads).
   */
  @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
  public static final class Api29 {
    private Api29 () { }

    /** BaseActivity.xgramHideOtherWindowsForLock: the root views of every window of the process. */
    public static java.util.List<View> windowViews () {
      return android.view.inspector.WindowInspector.getGlobalWindowViews();
    }
  }
}
