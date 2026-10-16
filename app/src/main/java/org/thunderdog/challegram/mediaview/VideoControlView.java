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
 * File created on 12/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.VideoTimelineView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class VideoControlView extends FrameLayoutFix implements FactorAnimator.Target, Destroyable {
  private final TextView nowView;
  private final TextView totalView;
  private final SliderView sliderView;
  private final PlayPauseButton playPauseButton;
  // XGram: Material 3 Expressive controls row, fixed when the view is built: 16dp start and end insets, a 48dp play
  // target, dark-scheme colours on the media (see XgramM3MediaChrome)
  private final boolean xgramLayout;

  public VideoControlView (Context context) {
    super(context);

    setWillNotDraw(false);
    xgramLayout = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM);

    sliderView = new SliderView(context);
    sliderView.setAnchorMode(SliderView.ANCHOR_MODE_START);
    sliderView.setForceBackgroundColorId(ColorId.videoSliderInactive);
    sliderView.setForceSecondaryColorId(ColorId.videoSliderInactive);
    sliderView.setSlideEnabled(true, false);
    sliderView.setColorId(ColorId.videoSliderActive, false);
    if (xgramLayout) {
      int edge = Screen.dp(56f) + Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.EDGE_DP);
      sliderView.setPadding(edge, 0, edge, 0);
      sliderView.setXgramColors(
        com.fahadali.xgram.appearance.XgramM3MediaChrome.onMedia(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY),
        com.fahadali.xgram.appearance.XgramM3MediaChrome.white(com.fahadali.xgram.appearance.XgramM3MediaChrome.TRACK_INACTIVE_ALPHA),
        com.fahadali.xgram.appearance.XgramM3MediaChrome.white(com.fahadali.xgram.appearance.XgramM3MediaChrome.TRACK_BUFFERED_ALPHA)
      );
    } else {
      sliderView.setPadding(Screen.dp(56f), 0, Screen.dp(56f), 0);
    }
    sliderView.setLayoutParams(params);
    addView(sliderView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM);
    // params.leftMargin = Screen.dp(2f);
    if (xgramLayout) {
      params.leftMargin = Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.EDGE_DP);
    }

    this.nowView = new NoScrollTextView(context);
    styleText(nowView);
    this.nowView.setLayoutParams(params);
    addView(nowView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.RIGHT | Gravity.BOTTOM);
    // params.rightMargin = Screen.dp(2f);
    if (xgramLayout) {
      params.rightMargin = Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.EDGE_DP);
    }

    this.totalView = new NoScrollTextView(context);
    styleText(totalView);
    this.totalView.setLayoutParams(params);
    addView(totalView);

    playPauseButton = new PlayPauseButton(context);
    if (xgramLayout) {
      playPauseButton.setTranslationX(-xgramPlayHiddenX());
      params = FrameLayoutFix.newParams(Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.PLAY_TARGET_WIDTH_DP), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM);
      params.leftMargin = Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.EDGE_DP);
      playPauseButton.setLayoutParams(params);
    } else {
      playPauseButton.setTranslationX(-Screen.dp(44f));
      playPauseButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(44f), Screen.dp(56f), Gravity.LEFT | Gravity.BOTTOM));
    }
    addView(playPauseButton);
  }

  /** XGram: how far the play button slides out to hide: its 48dp target plus the 16dp inset. */
  private static int xgramPlayHiddenX () {
    return Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.PLAY_TARGET_WIDTH_DP + com.fahadali.xgram.appearance.XgramM3MediaChrome.EDGE_DP);
  }

  private @Nullable VideoTimelineView timelineView;

  public void addTrim (VideoTimelineView.TimelineDelegate delegate, ThemeDelegate forcedTheme) {
    setShowPlayPause(true, false);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM);

    timelineView = new VideoTimelineView(getContext());
    timelineView.setShowSlider(true, false);
    timelineView.setColors(ColorId.white, ColorId.black, ColorId.transparentEditor);
    if (xgramLayout) {
      // XGram: the frame sits between the time labels: past the 48dp play target and the 56dp time box at the start
      // (16 + 48 + 56 = 120dp, the frame's stroke then starts at 121dp, clear of the time text) and before the 56dp
      // total box at the end (16 + 56 = 72dp). addTrim always shows the play button, so the time box stays shifted.
      int edge = Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.EDGE_DP);
      timelineView.setPadding(Screen.dp(56f) + Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.PLAY_TARGET_WIDTH_DP) + edge, Screen.dp(6f), Screen.dp(56f) + edge, Screen.dp(6f));
    } else {
      timelineView.setPadding(Screen.dp(54f) + Screen.dp(32f), Screen.dp(6f), Screen.dp(54f), Screen.dp(6f));
    }
    timelineView.setLayoutParams(params);
    timelineView.setDelegate(delegate);
    timelineView.setForcedTheme(forcedTheme);
    addView(timelineView, 0);
    timelineView.setVisibility(View.GONE);
  }

  private boolean timelineVisible;

  private void setTimelineVisible (boolean isVisible) {
    if (this.timelineVisible != isVisible && timelineView != null) {
      this.timelineVisible = isVisible;
      timelineView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      sliderView.setVisibility(isVisible ? View.GONE : View.VISIBLE);
    }
  }

  @Override
  public void performDestroy () {
    setFile(null);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return !Views.onTouchEvent(this, ev) || super.onInterceptTouchEvent(ev);
  }

  private BoolAnimator showPlayPause = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setShowPlayPause (boolean show, boolean animated) {
    if (timelineView == null) {
      if (xgramLayout && animated) {
        // XGram: the button slides in on the spatial spring (clamped: the track padding follows it) and out on effects
        com.fahadali.xgram.appearance.XgramAppMotion.use(showPlayPause, show ? com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT : com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST, true);
      }
      showPlayPause.setValue(show || timelineVisible, animated);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (xgramLayout) {
      // XGram: the time and the track make room for the whole 48dp play target
      int dx = (int) ((float) Screen.dp(com.fahadali.xgram.appearance.XgramM3MediaChrome.PLAY_TARGET_WIDTH_DP) * factor);
      nowView.setTranslationX(dx);
      sliderView.setAddPaddingLeft(dx);
      playPauseButton.setTranslationX(-xgramPlayHiddenX() * (1f - factor));
      return;
    }
    int dx = (int) ((float) Screen.dp(32f) * factor);
    nowView.setTranslationX(dx);
    sliderView.setAddPaddingLeft(dx);
    playPauseButton.setTranslationX(-Screen.dp(44f) * (1f - factor));
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setIsPlaying (boolean isPlaying, boolean animated) {
    playPauseButton.setIsPlaying(isPlaying, animated && showPlayPause.getFloatValue() > 0f);
  }

  public void setOnPlayPauseClick (View.OnClickListener onClickListener) {
    playPauseButton.setOnClickListener(onClickListener);
  }

  public void setSliderListener (SliderView.Listener listener) {
    sliderView.setListener(listener);
  }

  public void setInnerAlpha (float alpha) {
    if (xgramDrawScrim && com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && sliderView.getAlpha() != alpha) {
      invalidate(); // XGram: the scrim follows the controls' alpha (onDraw)
    }
    sliderView.setAlpha(alpha);
    if (timelineView != null) {
      timelineView.setAlpha(alpha);
    }
    nowView.setAlpha(alpha);
    totalView.setAlpha(alpha);
  }

  public float getInnerAlpha () {
    return sliderView.getAlpha();
  }

  private static void styleText (TextView textView) {
    textView.setTextColor(0xffffffff);
    textView.setPadding(Screen.dp(2f), 0, Screen.dp(2f), 0);
    textView.setGravity(Gravity.CENTER);
    textView.setSingleLine(true);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    textView.setText(Strings.buildDuration(0));
  }

  private long totalDurationMs;

  public void resetDuration (long totalDurationMs, long nowDurationMs, boolean canSeek, boolean animated) {
    boolean hasSlider = canSeek && totalDurationMs > 0;
    sliderView.setSlideEnabled(hasSlider, animated);
    if (timelineView != null) {
      timelineView.setCanSlide(hasSlider, animated);
      timelineView.setSliderProgress(totalDurationMs > 0 ? (float) ((double) nowDurationMs / (double) totalDurationMs) : 0f);
      timelineView.invalidate();
    }
    setNow(nowDurationMs, animated);
    setTotalMs(totalDurationMs);
  }

  public void setFile (ImageGalleryFile file) {
    if (timelineView != null) {
      String path = file != null ? file.getFilePath() : null;
      boolean visible = !StringUtils.isEmpty(path);
      double startTime, endTime;
      double duration;
      float newStart, newEnd;
      if (file != null && file.hasTrim()) {
        long durationUs = file.getTotalDurationUs();
        long startUs = file.getStartTimeUs();
        long endUs = file.getEndTimeUs();
        duration = (double) durationUs / 1_000_000.0;
        newStart = (float) ((double) startUs / (double) durationUs);
        startTime = (double) startUs / 1_000_000.0;
        if (endUs == -1) {
          newEnd = 1.0f;
          endTime = duration;
        } else {
          newEnd = (float) ((double) endUs / (double) durationUs);
          endTime = (double) endUs / 1_000_000.0;
        }
      } else {
        newStart = 0f;
        newEnd = 1f;
        startTime = endTime = -1;
        duration = 0;
      }
      timelineView.setVideoPath(path, newStart, newEnd, startTime, endTime, duration, timelineVisible && visible);
      timelineView.setSliderProgress(0f);
      setTimelineVisible(visible);
    }
  }

  private long nowDurationMs;

  public void setNow (long nowMs, boolean animated) {
    setNowMs(nowMs);
    updateSlider(animated);
  }

  private boolean slideEnabled;

  public void setSlideEnabled (boolean isEnabled) {
    if (this.slideEnabled != isEnabled) {
      this.slideEnabled = isEnabled;
      updateSliderAvailability();
    }
  }

  private void updateSliderAvailability () {
    boolean hasSlider = slideEnabled && totalDurationMs > 0;
    sliderView.setSlideEnabled(hasSlider, true);
    if (timelineView != null) {
      timelineView.setCanSlide(hasSlider, true);
    }
  }

  private void updateSlider (boolean animated) {
    float progress = MathUtils.clamp(totalDurationMs > 0 ? (float) ((double) nowDurationMs / (double) totalDurationMs) : 0f);
    sliderView.setValue(progress);
    if (timelineView != null) {
      timelineView.setSliderProgress(progress);
    }
  }

  private void setNowMs (long ms) {
    if (this.nowDurationMs != ms) {
      this.nowDurationMs = ms;
      nowView.setText(Strings.buildDuration(Math.round(ms / 1000.0)));
    }
  }

  private void setTotalMs (long ms) {
    if (this.totalDurationMs != ms) {
      boolean changedState = (ms == 0 || totalDurationMs == 0);
      this.totalDurationMs = ms;
      totalView.setText(Strings.buildDuration(Math.round(ms / 1000.0)));
      if (changedState) {
        updateSliderAvailability();
      }
    }
  }

  public void updateSeek (long nowDurationMs, long durationMs, float progress) {
    setNowMs(nowDurationMs);
    setTotalMs(durationMs);
    progress = MathUtils.clamp(progress);
    if (sliderView != null) {
      sliderView.setValue(progress);
    }
    if (timelineView != null) {
      timelineView.setSliderProgress(progress);
    }
  }

  public void updateSecondarySeek (float offset, float progress) {
    if (sliderView != null) {
      sliderView.setSecondaryValue(offset, progress);
    }
  }

  // XGram: Material 3 Expressive: the controls sit on a vertical scrim gradient instead of a flat band. It shows with
  // the controls only (their inner alpha, the video factor): photos and GIFs keep the view at alpha 1, moved 56dp down,
  // and its top would otherwise stay on screen as a band with a hard edge.
  private @androidx.annotation.Nullable android.graphics.Paint xgramScrimPaint;
  private int xgramScrimHeight;
  private boolean xgramDrawScrim = true;

  /**
   * XGram: false when the parent draws the scrim itself behind its other bottom views (the media viewer does, see
   * XgramW2MediaViewer.ControlsScrimDrawable), so it never covers the caption.
   */
  public void setXgramDrawScrim (boolean drawScrim) {
    if (this.xgramDrawScrim != drawScrim) {
      this.xgramDrawScrim = drawScrim;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      final float alpha = sliderView.getAlpha();
      if (!xgramDrawScrim || alpha <= 0f) {
        return;
      }
      int scrimHeight = Screen.dp(96f);
      if (xgramScrimPaint == null || xgramScrimHeight != scrimHeight) {
        xgramScrimHeight = scrimHeight;
        xgramScrimPaint = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
        xgramScrimPaint.setShader(new android.graphics.LinearGradient(0, 0, 0, scrimHeight, new int[] {0x00000000, 0x59000000, 0x99000000}, new float[] {0f, .55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
      }
      xgramScrimPaint.setAlpha(Math.round(255f * Math.min(1f, alpha))); // the paint alpha scales the shader
      c.save();
      c.translate(0, getMeasuredHeight() - scrimHeight);
      c.drawRect(0, 0, getMeasuredWidth(), scrimHeight, xgramScrimPaint);
      c.restore();
      return;
    }
    c.drawRect(0, getMeasuredHeight() - Screen.dp(56f), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(ColorId.transparentEditor)));
  }
}
