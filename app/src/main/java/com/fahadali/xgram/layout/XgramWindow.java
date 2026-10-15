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
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * The activity window as adaptive layouts need it: Material 3 window size classes, the fold of a foldable and its
 * posture (Jetpack WindowManager, see {@code XgramPostureSource} in src/sinceMarshmallow; legacy and lollipop builds
 * have no postures), the display rotation and multi-window mode.
 *
 * One per {@link BaseActivity} ({@link BaseActivity#xgramWindow()}). Postures are only tracked while the activity is
 * started. Listeners hear about every change, so popups such as the media viewer, which get no configuration callback,
 * can follow folding, unfolding and posture changes too. Everything runs on the main thread.
 */
public final class XgramWindow {
  // Width classes, in dp: compact < 600, medium < 840, expanded < 1200, large < 1600, extra large.
  public static final int WIDTH_COMPACT = 0;
  public static final int WIDTH_MEDIUM = 1;
  public static final int WIDTH_EXPANDED = 2;
  public static final int WIDTH_LARGE = 3;
  public static final int WIDTH_XLARGE = 4;

  // Height classes, in dp: compact < 480, medium < 900, expanded.
  public static final int HEIGHT_COMPACT = 0;
  public static final int HEIGHT_MEDIUM = 1;
  public static final int HEIGHT_EXPANDED = 2;

  // Change flags passed to listeners.
  /** The window size changed (any amount). */
  public static final int CHANGE_SIZE = 1;
  /** The width or height class changed. */
  public static final int CHANGE_SIZE_CLASS = 1 << 1;
  /** The fold appeared, went away, or its state, orientation or bounds changed. */
  public static final int CHANGE_POSTURE = 1 << 2;
  /** The display rotated, 180 degree turns included (they bring no configuration change). */
  public static final int CHANGE_ROTATION = 1 << 3;
  /** Split screen or freeform started or ended. */
  public static final int CHANGE_MULTI_WINDOW = 1 << 4;

  public static int widthClassOf (int widthDp) {
    if (widthDp < 600) {
      return WIDTH_COMPACT;
    }
    if (widthDp < 840) {
      return WIDTH_MEDIUM;
    }
    if (widthDp < 1200) {
      return WIDTH_EXPANDED;
    }
    if (widthDp < 1600) {
      return WIDTH_LARGE;
    }
    return WIDTH_XLARGE;
  }

  public static int heightClassOf (int heightDp) {
    if (heightDp < 480) {
      return HEIGHT_COMPACT;
    }
    if (heightDp < 900) {
      return HEIGHT_MEDIUM;
    }
    return HEIGHT_EXPANDED;
  }

  /** A fold or hinge, as Jetpack WindowManager reports it. {@link #bounds} are in window coordinates. */
  public static final class Fold {
    /** Half open (Flex mode); otherwise flat. */
    public final boolean halfOpened;
    /** Runs left to right (tabletop when half open); otherwise top to bottom (book). */
    public final boolean horizontal;
    /** Splits the window into two logical areas (half open, or a physical hinge between two screens). */
    public final boolean separating;
    /** Hides the content under it (a physical hinge), so nothing should be drawn there. */
    public final boolean occlusionFull;
    public final Rect bounds;

    public Fold (boolean halfOpened, boolean horizontal, boolean separating, boolean occlusionFull, Rect bounds) {
      this.halfOpened = halfOpened;
      this.horizontal = horizontal;
      this.separating = separating;
      this.occlusionFull = occlusionFull;
      this.bounds = new Rect(bounds);
    }

    boolean sameAs (@Nullable Fold other) {
      return other != null &&
        other.halfOpened == halfOpened &&
        other.horizontal == horizontal &&
        other.separating == separating &&
        other.occlusionFull == occlusionFull &&
        other.bounds.equals(bounds);
    }

    @Override
    public String toString () {
      return (halfOpened ? "half-opened" : "flat") + (horizontal ? " horizontal" : " vertical") +
        (separating ? " separating" : "") + (occlusionFull ? " occluding" : "") + " " + bounds.toShortString();
    }
  }

  public interface Listener {
    /** @param changes {@code CHANGE_*} flags */
    void onWindowChanged (XgramWindow window, int changes);
  }

  /** This activity's window, or null (no activity, or one that isn't a {@link BaseActivity}). */
  @Nullable
  public static XgramWindow get (@Nullable Context context) {
    BaseActivity activity = context != null ? UI.getContext(context) : UI.getUiContext();
    return activity != null ? activity.xgramWindow() : null;
  }

  private final BaseActivity activity;
  private final XgramPostureSource postureSource;
  private final List<Listener> listeners = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Nullable private Fold fold;
  private int widthDp, heightDp;
  private int widthClass = WIDTH_COMPACT, heightClass = HEIGHT_MEDIUM;
  private int rotation;
  private boolean inMultiWindow;
  private boolean isStarted, isDestroyed;
  /** DisplayManager.DisplayListener (API 17+), kept untyped so API 16 never loads it. */
  @Nullable private Object rotationWatcher;

  private int configKey = -1;
  private boolean configSizeChanged = true;

  public XgramWindow (BaseActivity activity) {
    this.activity = activity;
    this.postureSource = new XgramPostureSource(activity, this);
    computeSize();
    this.rotation = currentRotation();
    this.configKey = configKey(activity.getResources().getConfiguration());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        this.inMultiWindow = activity.isInMultiWindowMode();
      } catch (Throwable ignored) { }
    }
  }

  // Lifecycle (called by BaseActivity)

  /** Re-checks the size whenever the root view changes size, even when no configuration change arrives. */
  public void watch (View rootView) {
    rootView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
        scheduleRefresh();
      }
    });
  }

  public void onStart () {
    if (isStarted || isDestroyed) {
      return;
    }
    isStarted = true;
    postureSource.start();
    startRotationWatcher();
    refresh();
  }

  public void onStop () {
    if (!isStarted) {
      return;
    }
    isStarted = false;
    postureSource.stop();
    stopRotationWatcher();
  }

  public void destroy () {
    onStop();
    isDestroyed = true;
    handler.removeCallbacksAndMessages(null);
    listeners.clear();
  }

  /** Called first thing in the activity's onConfigurationChanged, before anything reads the window. */
  public void onConfigurationChanged (Configuration newConfig) {
    int key = configKey(newConfig);
    configSizeChanged = key != configKey;
    configKey = key;
    refresh();
  }

  public void onMultiWindowModeChanged (boolean inMultiWindow) {
    if (this.inMultiWindow != inMultiWindow) {
      this.inMultiWindow = inMultiWindow;
      computeSize();
      notifyChanged(CHANGE_MULTI_WINDOW | CHANGE_SIZE);
    }
  }

  private static volatile boolean foldSeen;

  /**
   * Whether this device folds, as far as can be told (a fold was reported, or there is a hinge angle sensor) and this
   * build can follow postures: the posture settings are only shown then.
   */
  public static boolean isPostureSupported () {
    if (!XgramPostureSource.isSupported()) {
      return false;
    }
    if (foldSeen) {
      return true;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        return org.thunderdog.challegram.unsorted.AppContext.get().getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_SENSOR_HINGE_ANGLE);
      } catch (Throwable ignored) { }
    }
    return false;
  }

  /** Called by {@code XgramPostureSource} on the main thread. */
  void setFold (@Nullable Fold fold) {
    if (isDestroyed) {
      return;
    }
    if (fold != null) {
      foldSeen = true;
    }
    boolean changed = fold == null ? this.fold != null : !fold.sameAs(this.fold);
    if (changed) {
      this.fold = fold;
      android.util.Log.i("XGram", "fold: " + (fold != null ? fold.toString() : "none"));
      notifyChanged(CHANGE_POSTURE | refreshFlags());
    }
  }

  // Size classes

  public int widthDp () {
    return widthDp;
  }

  public int heightDp () {
    return heightDp;
  }

  public int widthClass () {
    return widthClass;
  }

  public int heightClass () {
    return heightClass;
  }

  /** Tablet-sized width: the fold-aware replacement for a cached "is tablet" flag. */
  public boolean isAtLeastMedium () {
    return widthClass >= WIDTH_MEDIUM;
  }

  public boolean isAtLeastExpanded () {
    return widthClass >= WIDTH_EXPANDED;
  }

  public boolean isInMultiWindow () {
    return inMultiWindow;
  }

  /** Display rotation, one of {@code Surface.ROTATION_*}. */
  public int rotation () {
    return rotation;
  }

  /**
   * Whether the last configuration change moved the window to another size class or orientation. Things that are
   * sized for the window (such as the emoji panel) only need to be rebuilt then, not on a night mode or locale change.
   */
  public boolean isConfigurationSizeChange () {
    return configSizeChanged;
  }

  // Posture

  @Nullable
  public Fold fold () {
    return fold;
  }

  /** Half open with a horizontal fold: standing like a laptop (Flex mode). */
  public boolean isTabletop () {
    Fold fold = this.fold;
    return fold != null && fold.halfOpened && fold.horizontal;
  }

  /** Half open with a vertical fold: held like a book. */
  public boolean isBook () {
    Fold fold = this.fold;
    return fold != null && fold.halfOpened && !fold.horizontal;
  }

  public boolean isSeparating () {
    Fold fold = this.fold;
    return fold != null && fold.separating;
  }

  /** The fold hides what is under it (a physical hinge). */
  public boolean isOcclusionFull () {
    Fold fold = this.fold;
    return fold != null && fold.occlusionFull;
  }

  /**
   * The fold's bounds in {@code view}'s coordinates, or null when there is no fold or it doesn't cross the view.
   * A fold without a physical hinge is a zero width (or zero height) rectangle.
   */
  @Nullable
  public Rect hingeBoundsIn (@Nullable View view) {
    Fold fold = this.fold;
    if (fold == null || view == null || view.getWidth() == 0 || view.getHeight() == 0) {
      return null;
    }
    int[] location = new int[2];
    view.getLocationInWindow(location);
    Rect viewRect = new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
    Rect hinge = new Rect(fold.bounds);
    if ((hinge.width() == 0 && hinge.height() == 0) || !hinge.intersect(viewRect)) {
      return null;
    }
    hinge.offset(-location[0], -location[1]);
    return hinge;
  }

  /**
   * Tabletop posture (Flex mode): where the fold crosses {@code view}, in its coordinates, or -1. Content goes above
   * this line and controls below it. -1 as well when the setting is off, or when the fold is too close to an edge of
   * the view for a split to help.
   */
  public static int tabletopFoldTop (@Nullable View view) {
    if (view == null) {
      return -1;
    }
    XgramWindow window = get(view.getContext());
    if (window == null || !window.isTabletop() || !XgramSettings.instance().foldTabletop()) {
      return -1;
    }
    Rect hinge = window.hingeBoundsIn(view);
    if (hinge == null) {
      return -1;
    }
    int height = view.getHeight();
    if (hinge.top < height / 4 || hinge.top > height * 3 / 4) {
      return -1;
    }
    return hinge.top;
  }

  // Listeners

  public void addListener (Listener listener) {
    if (!isDestroyed && !listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private void notifyChanged (int changes) {
    if (changes == 0 || listeners.isEmpty()) {
      return;
    }
    Listener[] copy = listeners.toArray(new Listener[0]);
    for (Listener listener : copy) {
      try {
        listener.onWindowChanged(this, changes);
      } catch (Throwable t) {
        // An adaptive layout must never take the app down.
        android.util.Log.e("XGram", "window listener failed", t);
      }
    }
  }

  // Internal

  private boolean refreshScheduled;

  private void scheduleRefresh () {
    if (!refreshScheduled && !isDestroyed) {
      refreshScheduled = true;
      handler.post(() -> {
        refreshScheduled = false;
        refresh();
      });
    }
  }

  /** Re-reads the size and rotation and tells the listeners what changed. */
  public void refresh () {
    if (!isDestroyed) {
      notifyChanged(refreshFlags());
    }
  }

  private int refreshFlags () {
    int changes = computeSize();
    int rotation = currentRotation();
    if (this.rotation != rotation) {
      this.rotation = rotation;
      changes |= CHANGE_ROTATION;
    }
    return changes;
  }

  private int computeSize () {
    int width = 0, height = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        float density = activity.getResources().getDisplayMetrics().density;
        if (density > 0f) {
          width = Math.round(bounds.width() / density);
          height = Math.round(bounds.height() / density);
        }
      } catch (Throwable ignored) { }
    }
    if (width <= 0 || height <= 0) {
      // Window sized as well (UI.getContext returns the activity), only without the system bars.
      Configuration configuration = activity.getResources().getConfiguration();
      width = configuration.screenWidthDp;
      height = configuration.screenHeightDp;
    }
    int changes = 0;
    if (width != widthDp || height != heightDp) {
      widthDp = width;
      heightDp = height;
      changes |= CHANGE_SIZE;
    }
    int widthClass = widthClassOf(width), heightClass = heightClassOf(height);
    if (widthClass != this.widthClass || heightClass != this.heightClass) {
      this.widthClass = widthClass;
      this.heightClass = heightClass;
      changes |= CHANGE_SIZE_CLASS;
    }
    return changes;
  }

  private static int configKey (Configuration configuration) {
    return configuration.orientation * 100 + widthClassOf(configuration.screenWidthDp) * 10 + heightClassOf(configuration.screenHeightDp);
  }

  @SuppressWarnings("deprecation")
  private int currentRotation () {
    try {
      Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? activity.getDisplay() : activity.getWindowManager().getDefaultDisplay();
      return display != null ? display.getRotation() : 0;
    } catch (Throwable t) {
      return 0;
    }
  }

  private void checkRotation () {
    int rotation = currentRotation();
    if (this.rotation != rotation) {
      this.rotation = rotation;
      notifyChanged(CHANGE_ROTATION);
    }
  }

  private void startRotationWatcher () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || rotationWatcher != null) {
      return;
    }
    try {
      DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
      if (manager != null) {
        RotationWatcher watcher = new RotationWatcher(this);
        manager.registerDisplayListener(watcher, handler);
        rotationWatcher = watcher;
      }
    } catch (Throwable t) {
      android.util.Log.w("XGram", "display listener", t);
    }
  }

  private void stopRotationWatcher () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || rotationWatcher == null) {
      return;
    }
    try {
      DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
      if (manager != null) {
        manager.unregisterDisplayListener((DisplayManager.DisplayListener) rotationWatcher);
      }
    } catch (Throwable ignored) { }
    rotationWatcher = null;
  }

  /** 180 degree turns change the rotation without a configuration change, so the camera listens here. */
  private static final class RotationWatcher implements DisplayManager.DisplayListener {
    private final XgramWindow window;

    RotationWatcher (XgramWindow window) {
      this.window = window;
    }

    @Override
    public void onDisplayAdded (int displayId) { }

    @Override
    public void onDisplayRemoved (int displayId) { }

    @Override
    public void onDisplayChanged (int displayId) {
      // Cheap: the rotation comes from the cached display info. Fires often (refresh rate changes, for one).
      window.checkRotation();
    }
  }
}
