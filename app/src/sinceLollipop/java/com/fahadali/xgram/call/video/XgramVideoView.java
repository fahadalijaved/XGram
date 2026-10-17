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
package com.fahadali.xgram.call.video;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.call.XgramVideoSurface;

import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.concurrent.CountDownLatch;

/**
 * XGram: a TextureView that draws one video feed through WebRTC's EglRenderer (on its own EGL thread, sharing the
 * call's EGL context, so the camera's OES textures draw too). A TextureView follows alpha, translation, scale, the
 * outline clip of its parent and the navigation slide; a SurfaceView does not. Not opaque: what the frame does not
 * cover (fit mode) shows the black stage under it.
 * <ul>
 * <li>Fill: the renderer crops the frame to the view's aspect (center crop).</li>
 * <li>Fit: the renderer draws the whole frame over the whole surface and the view's transform scales it back to the
 * frame's aspect around the center.</li>
 * </ul>
 * The first frame counts once it reached the screen (the texture was updated after a frame came in), with a short
 * fallback in case the view was not drawn. Frame callbacks post preallocated runnables, only on a change.
 */
@SuppressLint("ViewConstructor")
final class XgramVideoView extends TextureView implements XgramVideoSurface, VideoSink, TextureView.SurfaceTextureListener {
  private static int counter;
  private static final long FIRST_FRAME_FALLBACK_MS = 160L;

  private final EglRenderer renderer;
  private final Runnable onReleased;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Matrix matrix = new Matrix();

  private @Nullable FrameListener listener;
  private int scaleMode = SCALE_FILL;
  private boolean transformed, released, surfaceReady;

  // Written on the frame thread, read on the main thread
  private volatile int frameWidth, frameHeight;
  private volatile boolean frameSeen;
  // Main thread
  private int notifiedWidth, notifiedHeight;
  private boolean hasFrame;

  private final Runnable frameChanged = this::onFrameChanged;
  private final Runnable firstFrameFallback = this::dispatchFirstFrame;

  XgramVideoView (Context context, @Nullable EglBase.Context eglContext, Runnable onReleased) {
    super(context);
    this.onReleased = onReleased;
    this.renderer = new EglRenderer("XgramVideo" + (++counter));
    this.renderer.init(eglContext, EglBase.CONFIG_PLAIN, new GlRectDrawer());
    setOpaque(false);
    setSurfaceTextureListener(this);
  }

  // XgramVideoSurface

  @Override
  public View view () {
    return this;
  }

  @Override
  public void setMirror (boolean mirror) {
    renderer.setMirror(mirror);
  }

  @Override
  public void setScaleMode (int mode) {
    if (this.scaleMode != mode) {
      this.scaleMode = mode;
      applyScale();
    }
  }

  @Override
  public void setFrameListener (@Nullable FrameListener listener) {
    this.listener = listener;
  }

  @Override
  public int frameWidth () {
    return frameWidth;
  }

  @Override
  public int frameHeight () {
    return frameHeight;
  }

  @Override
  public boolean hasFrame () {
    return hasFrame;
  }

  @Override
  public void clear () {
    main.removeCallbacks(firstFrameFallback);
    hasFrame = false;
    frameSeen = false;
    if (!released) {
      renderer.clearImage();
    }
  }

  @Override
  public void release () {
    if (released) {
      return;
    }
    released = true;
    listener = null;
    main.removeCallbacks(frameChanged);
    main.removeCallbacks(firstFrameFallback);
    renderer.release();
    onReleased.run();
  }

  // VideoSink: a WebRTC thread

  @Keep
  @Override
  public void onFrame (VideoFrame frame) {
    final int w = frame.getRotatedWidth(), h = frame.getRotatedHeight();
    final boolean first = !frameSeen;
    final boolean sizeChanged = w != frameWidth || h != frameHeight;
    if (sizeChanged) {
      frameWidth = w;
      frameHeight = h;
    }
    if (first) {
      frameSeen = true;
    }
    if (first || sizeChanged) {
      main.post(frameChanged);
    }
    renderer.onFrame(frame);
  }

  // Main thread

  private void onFrameChanged () {
    if (released) {
      return;
    }
    final int w = frameWidth, h = frameHeight;
    if (w != notifiedWidth || h != notifiedHeight) {
      notifiedWidth = w;
      notifiedHeight = h;
      applyScale();
      if (listener != null) {
        listener.onFrameSize(this, w, h);
      }
    }
    if (frameSeen && !hasFrame) {
      // Normally the texture update comes first; this covers a view that is not drawn right now
      main.removeCallbacks(firstFrameFallback);
      main.postDelayed(firstFrameFallback, FIRST_FRAME_FALLBACK_MS);
    }
  }

  private void dispatchFirstFrame () {
    main.removeCallbacks(firstFrameFallback);
    if (released || hasFrame || !frameSeen) {
      return;
    }
    hasFrame = true;
    if (listener != null) {
      listener.onFirstFrame(this);
    }
  }

  private void applyScale () {
    final int vw = getWidth(), vh = getHeight();
    if (released || vw <= 0 || vh <= 0) {
      return;
    }
    if (scaleMode == SCALE_FIT) {
      renderer.setLayoutAspectRatio(0f); // no crop: the whole frame over the whole surface
      final int fw = frameWidth, fh = frameHeight;
      if (fw > 0 && fh > 0) {
        final float frameAspect = fw / (float) fh, viewAspect = vw / (float) vh;
        final float sx, sy;
        if (frameAspect > viewAspect) {
          sx = 1f;
          sy = viewAspect / frameAspect;
        } else {
          sx = frameAspect / viewAspect;
          sy = 1f;
        }
        matrix.setScale(sx, sy, vw / 2f, vh / 2f);
        setTransform(matrix);
        transformed = true;
      }
    } else {
      renderer.setLayoutAspectRatio(vw / (float) vh); // center crop
      if (transformed) {
        transformed = false;
        setTransform(null);
      }
    }
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    applyScale();
  }

  // SurfaceTextureListener: main thread

  @Override
  public void onSurfaceTextureAvailable (@NonNull SurfaceTexture surface, int width, int height) {
    if (released) {
      return;
    }
    surfaceReady = true;
    renderer.createEglSurface(surface);
    applyScale();
  }

  @Override
  public void onSurfaceTextureSizeChanged (@NonNull SurfaceTexture surface, int width, int height) {
    applyScale();
  }

  @Override
  public boolean onSurfaceTextureDestroyed (@NonNull SurfaceTexture surface) {
    // WebRTC's TextureView pattern: the EGL surface is released before the texture goes, or EGL draws into a freed one
    if (surfaceReady) {
      surfaceReady = false;
      final CountDownLatch latch = new CountDownLatch(1);
      renderer.releaseEglSurface(latch::countDown);
      ThreadUtils.awaitUninterruptibly(latch);
    }
    return true;
  }

  @Override
  public void onSurfaceTextureUpdated (@NonNull SurfaceTexture surface) {
    if (frameSeen && !hasFrame) {
      dispatchFirstFrame();
    }
  }
}
