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
 * File created on 10/03/2018
 */
package org.thunderdog.challegram.tool;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Choreographer;

import androidx.annotation.NonNull;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;

import me.vkryl.core.reference.ReferenceList;

public class Invalidator implements Handler.Callback, ReferenceList.FullnessListener, ViewController.AttachListener, BaseActivity.ActivityListener {
  public interface Target {
    ViewController<?> getTargetParent (Invalidator context);
    long onInvalidateTarget (Invalidator context);
    // void onTargetAttached (Invalidator context, boolean isAttached);
  }

  private final BaseActivity context;
  private final ReferenceList<Target> targets = new ReferenceList<>(false, true, this);
  private final ReferenceList<Target> pendingTargets = new ReferenceList<>();
  private final Handler handler = new Handler(Looper.getMainLooper(), this);
  private boolean isLooping;

  public Invalidator (BaseActivity context) {
    this.context = context;
    context.addActivityListener(this);
  }

  public BaseActivity context () {
    return context;
  }

  public void addTarget (@NonNull Target target) {
    ViewController<?> controller = target.getTargetParent(this);
    if (controller != null) {
      boolean added;
      if (controller.getAttachState()) {
        added = targets.add(target);
        if (added) {
          // target.onTargetAttached(this, true);
        }
      } else {
        added = pendingTargets.add(target);
      }
      if (added) {
        controller.addAttachStateListener(this);
      }
    } else {
      if (targets.add(target)) {
        // target.onTargetAttached(this, true);
      }
    }
  }

  public void removeTarget (@NonNull Target target) {
    ViewController<?> controller = target.getTargetParent(this);
    if (controller != null && !controller.getAttachState()) {
      pendingTargets.remove(target);
    } else {
      if (targets.remove(target)) {
        // target.onTargetAttached(this, false);
      }
    }
  }

  @Override
  public void onAttachStateChanged (ViewController<?> context, NavigationController navigation, boolean isAttached) {
    ReferenceList<Target> fromList, toList;
    if (isAttached) {
      fromList = pendingTargets;
      toList = targets;
    } else {
      fromList = targets;
      toList = pendingTargets;
    }
    for (Target target : fromList) {
      if (target.getTargetParent(this) == context) {
        fromList.remove(target);
        toList.add(target);
        // target.onTargetAttached(this, isAttached);
      }
    }
  }

  private long invalidateTargets () {
    long minFrameDelay = -1;
    for (Target target : targets) {
      long frameDelay = target.onInvalidateTarget(this);
      if (frameDelay != -1) {
        if (minFrameDelay == -1 || frameDelay < minFrameDelay) {
          minFrameDelay = frameDelay;
        }
      }
    }
    return minFrameDelay;
  }

  // XGram: with Expressive design the loop runs on vsync (120 Hz) instead of a handler capped at 15 ms
  private final Choreographer.FrameCallback xgramFrame = this::xgramDoFrame;
  private long xgramLastFrameNanos, xgramRequestedDelay;
  private boolean xgramVsync;

  private final Runnable xgramSyncRunnable = this::xgramSync;

  private void xgramSync () {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.post(xgramSyncRunnable);
      return;
    }
    Choreographer choreographer = Choreographer.getInstance();
    choreographer.removeFrameCallback(xgramFrame);
    xgramVsync = isLooping;
    if (isLooping) {
      xgramRequestedDelay = 0;
      choreographer.postFrameCallback(xgramFrame);
    }
  }

  private void xgramDoFrame (long frameTimeNanos) {
    if (!isLooping || !xgramVsync) {
      return;
    }
    long elapsedMs = (frameTimeNanos - xgramLastFrameNanos) / 1_000_000L;
    if (xgramRequestedDelay > ValueAnimator.getFrameDelay() && elapsedMs + 4 < xgramRequestedDelay) {
      Choreographer.getInstance().postFrameCallback(xgramFrame);
      return;
    }
    xgramLastFrameNanos = frameTimeNanos;
    xgramRequestedDelay = invalidateTargets();
    if (isLooping && xgramVsync) {
      Choreographer.getInstance().postFrameCallback(xgramFrame);
    }
  }

  @Override
  public boolean handleMessage (Message msg) {
    long minFrameDelay = invalidateTargets();
    if (isLooping) {
      handler.sendMessageDelayed(Message.obtain(handler, 0), Math.max(getFrameDelay(), minFrameDelay));
    }
    return true;
  }

  private boolean isFull;

  @Override
  public void onFullnessStateChanged (ReferenceList<?> list, boolean isFull) {
    if (this.isFull != isFull) {
      this.isFull = isFull;
      checkLooping();
    }
  }

  private void checkLooping () {
    boolean isLooping = this.isFull && context.getActivityState() == UI.State.RESUMED;
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (XgramExpressive.isEnabled()) {
        xgramSync();
        return;
      }
      if (isLooping) {
        handler.sendMessageDelayed(Message.obtain(handler, 0), getFrameDelay());
      } else {
        handler.removeMessages(0);
      }
    }
  }

  private static long getFrameDelay () {
    return Math.max(ValueAnimator.getFrameDelay(), 15l);
  }

  @Override
  public void onActivityPause () {
    checkLooping();
  }

  @Override
  public void onActivityResume () {
    checkLooping();
  }

  @Override
  public void onActivityDestroy () {
    checkLooping();
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) { }
}
