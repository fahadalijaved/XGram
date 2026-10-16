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
 * File created on 18/05/2015 at 00:42
 */
package org.thunderdog.challegram.loader;

import android.os.Build;
import android.os.Message;
import android.view.Choreographer;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.N;

import java.util.ArrayList;

public class ImageAnimator  {
  private static final long DURATION = 120l;
  private static final long SLOW_DURATION = 100l;
  private static ImageAnimatorHandler handler;

  private static final long FRAME_DELAY = 12l;
  private static final long SLOW_FRAME_DELAY = Build.VERSION.SDK_INT > 20 ? 14l : 17l;

  private ImageReceiver receiver;

  private int tick;
  private long animationStart;
  private boolean isSlow;

  public ImageAnimator (ImageReceiver receiver) {
    if (handler == null) {
      handler = new ImageAnimatorHandler();
    }
    this.receiver = receiver;
  }

  public ImageAnimator (ImageReceiver receiver, boolean isSlow) {
    if (handler == null) {
      handler = new ImageAnimatorHandler();
    }
    this.receiver = receiver;
    this.isSlow = isSlow;
  }

  public void cancel () {
    tick++;
  }

  public void start () {
    tick++;
    animationStart = System.currentTimeMillis();
    handler.sendMessage(Message.obtain(handler, tick, this));
  }

  public void onFrame (int tick) {
    if (this.tick == tick && XgramExpressive.isEnabled()) {
      // XGram: vsync frames and the EFFECTS_DEFAULT spring (about 186 ms) instead of a 12-14 ms handler loop
      xgramStartNanos = System.nanoTime();
      xgramTick = tick;
      receiver.setAlpha(0f);
      XgramVsync.add(this);
      return;
    }
    if (this.tick == tick) {
      float input;

      if (isSlow) {
        input = (float) (System.currentTimeMillis() - animationStart) / (float) SLOW_DURATION;
      } else {
        input = (float) (System.currentTimeMillis() - animationStart) / (float) DURATION;
      }

      if (input <= 0f) {
        receiver.setAlpha(0f);
        if (isSlow) {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), SLOW_FRAME_DELAY);
        } else {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), FRAME_DELAY);
        }
      } else if (input >= 1f) {
        receiver.setAlpha(1f);
      } else {
        receiver.setAlpha(N.iimg(input));
        if (isSlow) {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), SLOW_FRAME_DELAY);
        } else {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), FRAME_DELAY);
        }
      }
    }
  }

  // XGram: one Choreographer callback for every running fade

  private long xgramStartNanos;
  private int xgramTick;
  private boolean xgramQueued;

  /** False when this fade is over or was cancelled. */
  private boolean xgramStep (long frameTimeNanos) {
    if (this.tick != xgramTick) {
      return false;
    }
    float t = (Math.max(frameTimeNanos, System.nanoTime()) - xgramStartNanos) / 1e9f;
    float p = XgramExpressive.springProgress(XgramExpressive.EFFECTS_DEFAULT, t);
    if (t >= .19f || p >= .995f) {
      receiver.setAlpha(1f);
      return false;
    }
    receiver.setAlpha(p <= 0f ? 0f : p);
    return true;
  }

  private static final class XgramVsync implements Choreographer.FrameCallback {
    private static final XgramVsync INSTANCE = new XgramVsync();
    private final ArrayList<ImageAnimator> active = new ArrayList<>();
    private boolean posted;

    static void add (ImageAnimator a) {
      if (!a.xgramQueued) {
        a.xgramQueued = true;
        INSTANCE.active.add(a);
      }
      if (!INSTANCE.posted) {
        INSTANCE.posted = true;
        Choreographer.getInstance().postFrameCallback(INSTANCE);
      }
    }

    @Override
    public void doFrame (long frameTimeNanos) {
      posted = false;
      for (int i = active.size() - 1; i >= 0; i--) {
        ImageAnimator a = active.get(i);
        if (!a.xgramStep(frameTimeNanos)) {
          a.xgramQueued = false;
          active.remove(i);
        }
      }
      if (!active.isEmpty()) {
        posted = true;
        Choreographer.getInstance().postFrameCallback(this);
      }
    }
  }
}
