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
 * File created on 10/11/2017
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramExpressiveC;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class VoiceVideoButtonView extends View implements FactorAnimator.Target, Settings.VideoModePreferenceListener, Destroyable, TooltipOverlayView.LocationProvider {
  private boolean hasTouchControls;

  private final Drawable sendIcon, micIcon, videoIcon, searchIcon;

  public VoiceVideoButtonView (Context context) {
    super(context);
    sendIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_send_24);
    micIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_msg_voice_24);
    videoIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_msg_video_24);
    searchIcon = Drawables.get(getResources(), R.drawable.baseline_search_24);
    setInVideoMode(Settings.instance().preferVideoMode(), false);
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    outRect.top += Screen.dp(8f);
    outRect.bottom -= Screen.dp(8f);
  }

  public void setHasTouchControls (boolean hasTouchControls) {
    if (this.hasTouchControls != hasTouchControls) {
      this.hasTouchControls = hasTouchControls;
      if (hasTouchControls) {
        Settings.instance().addVideoPreferenceChangeListener(this);
      } else {
        Settings.instance().removeVideoPreferenceChangeListener(this);
      }
      invalidate();
    }
  }

  private static final int ANIMATOR_VIDEO_MODE = 0, ANIMATOR_SEARCH_MODE = 1;
  private final BoolAnimator inVideoMode = new BoolAnimator(ANIMATOR_VIDEO_MODE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private final BoolAnimator inSearchMode = new BoolAnimator(ANIMATOR_SEARCH_MODE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  @Override
  public void onPreferVideoModeChanged (boolean preferVideoMode) {
    setInVideoMode(preferVideoMode, hasTouchControls);
  }

  public void setInVideoMode (boolean inVideoMode, boolean animated) {
    this.inVideoMode.setValue(inVideoMode, animated);
  }

  public void setInSearchMode (boolean inSearchMode, boolean animated) {
    this.inSearchMode.setValue(inSearchMode, animated);
  }

  private float sendFactor;

  public void setSendFactor (float factor) {
    if (this.sendFactor != factor) {
      this.sendFactor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  public void performDestroy () {
    Settings.instance().removeVideoPreferenceChangeListener(this);
  }

  private Paint paint;

  private Paint getIconPaint () {
    int color = Theme.getColor(ColorId.circleButtonRegularIcon);
    if (paint == null || paint.getColor() != color)
      paint = Paints.createPorterDuffPaint(paint, color);
    return paint;
  }

  // XGram: Material 3 Expressive filled tonal icon button in the input bar: a 40dp
  // primaryContainer circle (the size of the Send button) with an onPrimaryContainer
  // Material Symbols mic or videocam, morphing to small (8dp) corners while pressed
  // (spatial fast spring).
  private XgramExpressive.Spring xgramPress;
  private Paint xgramIconPaint;
  private final RectF xgramRect = new RectF();
  private Drawable xgramMicIcon, xgramVideoIcon;

  private Paint xgramIconPaint () {
    int color = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
    if (xgramIconPaint == null || xgramIconPaint.getColor() != color)
      xgramIconPaint = Paints.createPorterDuffPaint(xgramIconPaint, color);
    return xgramIconPaint;
  }

  private void xgramLoadIcons () {
    if (xgramMicIcon == null) {
      // An icon pack keeps its own mic and camera; Telegram X icons get Material Symbols Rounded
      boolean pack = com.fahadali.xgram.appearance.XgramIconPack.current() != com.fahadali.xgram.appearance.XgramIconPack.PACK_TELEGRAM_X;
      xgramMicIcon = Drawables.get(getResources(), pack ? R.drawable.baseline_mic_24 : R.drawable.xgram_m3e_s4_chat_mic);
      xgramVideoIcon = Drawables.get(getResources(), pack ? R.drawable.baseline_videocam_24 : R.drawable.xgram_m3e_s4_chat_videocam);
    }
  }

  private void xgramDrawContainer (Canvas c, int cx, int cy) {
    final float size = Math.min(Screen.dp(XgramExpressiveC.ICON_BUTTON_BOX), getMeasuredHeight());
    final float radius = XgramExpressiveC.morphCorner(XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S, size, size, xgramPress != null ? xgramPress.getValue() : 0f);
    xgramRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    c.drawRoundRect(xgramRect, radius, radius, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER)));
  }

  @Override
  protected void onDraw (Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    final int cx = getPaddingLeft() + (viewWidth - getPaddingLeft() - getPaddingRight()) / 2;
    final int cy = viewHeight / 2;
    final float videoFactor = inVideoMode.getFloatValue();
    final float searchFactor = inSearchMode.getFloatValue();

    final boolean xgramExpressive = hasTouchControls && XgramExpressive.isEnabled();
    if (xgramExpressive) {
      xgramDrawContainer(c, cx, cy);
      xgramLoadIcons();
    }
    final Drawable micIcon = xgramExpressive ? xgramMicIcon : this.micIcon;
    final Drawable videoIcon = xgramExpressive ? xgramVideoIcon : this.videoIcon;
    final Paint paint = xgramExpressive ? xgramIconPaint() : hasTouchControls ? Paints.getIconGrayPorterDuffPaint() : getIconPaint();
    final int savedAlpha = paint.getAlpha();
    final float generalFactor = (1f - sendFactor);
    if (generalFactor > 0f) {
      float alpha = generalFactor * (1f - searchFactor);
      if (alpha > 0f) {
        final float scale = Config.DEFAULT_ICON_SWITCH_SCALE + (1f - Config.DEFAULT_ICON_SWITCH_SCALE) * (1f - searchFactor);
        if (scale != 1f) {
          c.save();
          c.scale(scale, scale, cx, cy);
        }

        int y;

        y = (int) (cy + (cy + videoIcon.getMinimumHeight() / 2) * videoFactor);
        paint.setAlpha((int) ((float) savedAlpha * ((1f - videoFactor) * alpha)));
        Drawables.drawCentered(c, micIcon, cx, y, paint);

        y = (int) (cy - (cy + videoIcon.getMinimumHeight() / 2) * (1f - videoFactor));
        paint.setAlpha((int) ((float) savedAlpha * videoFactor * alpha));
        Drawables.drawCentered(c, videoIcon, cx, y, paint);

        if (scale != 1f) {
          c.restore();
        }
      }
      alpha = generalFactor * searchFactor;
      if (alpha > 0f) {
        final float scale = Config.DEFAULT_ICON_SWITCH_SCALE + (1f - Config.DEFAULT_ICON_SWITCH_SCALE) * searchFactor;
        if (scale != 1f) {
          c.save();
          c.scale(scale, scale, cx, cy);
        }

        paint.setAlpha((int) ((float) savedAlpha * alpha));
        Drawables.drawCentered(c, searchIcon, cx, cy, paint);

        if (scale != 1f) {
          c.restore();
        }
      }
    }

    paint.setAlpha((int) ((float) savedAlpha * sendFactor));
    Drawables.drawCentered(c, sendIcon, cx, cy, paint);

    paint.setAlpha(savedAlpha);
  }

  private float trackDownX, trackDownY;

  private boolean inLongTap;

  private boolean draggingUp, draggingLeft;

  private boolean isDown;

  private void setIsDown (boolean isDown) {
    if (this.isDown != isDown) {
      this.isDown = isDown;
      if (hasTouchControls && XgramExpressive.isEnabled()) {
        if (xgramPress == null) {
          xgramPress = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
          xgramPress.setThreshold(.005f);
        }
        xgramPress.animateTo(isDown ? 1f : 0f);
      }
      if (isDown) {
        scheduleLongTap();
      } else {
        cancelLongTap();
      }
    }
  }

  private CancellableRunnable scheduledLongTap;

  private void scheduleLongTap () {
    cancelLongTap();
    final boolean xgramToggle = xgramTapToRecord;
    scheduledLongTap = new CancellableRunnable() {
      @Override
      public void act () {
        if (scheduledLongTap == this) {
          if (xgramToggle) {
            // XGram: Tap to record, a long press switches between voice and video.
            scheduledLongTap = null;
            xgramToggled = true;
            UI.hapticVibrate(VoiceVideoButtonView.this, true);
            performTap();
            return;
          }
          setInLongTap(true, false);
          scheduledLongTap = null;
        }
      }
    };
    postDelayed(scheduledLongTap, xgramToggle ? ViewConfiguration.getLongPressTimeout() : 120l);
  }

  // XGram: Settings → XGram → Chats → Tap to record. Read on touch down.
  private boolean xgramTapToRecord, xgramToggled;

  private void xgramStartRecordingLocked () {
    UI.getContext(getContext()).getRecordAudioVideoController().startRecording(this, false, true);
  }

  private void cancelLongTap () {
    if (scheduledLongTap != null) {
      scheduledLongTap.cancel();
      scheduledLongTap = null;
    }
  }

  private void setInLongTap (boolean inLongTap, boolean byCancel) {
    if (this.inLongTap != inLongTap) {
      boolean success;
      if (inLongTap) {
        success = UI.getContext(getContext()).getRecordAudioVideoController().startRecording(this, false);
      } else {
        if (!cancelLongTap) {
          UI.getContext(getContext()).getRecordAudioVideoController().finishRecording(byCancel);
        }
        success = true;
      }
      if (success) {
        this.inLongTap = inLongTap;
      } else if (inLongTap) {
        setIsDown(false);
      }
    }
  }

  private void performTap () {
    ViewUtils.onClick(this);
    Settings.instance().setPreferVideoMode(!inVideoMode.getValue());
  }

  private void checkDraggingCircle () {
    boolean isDragging = draggingUp && isDown;
    UI.getContext(getContext()).getRecordAudioVideoController().setApplyVerticalDrag(isDragging);
  }

  private boolean cancelLongTap;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!hasTouchControls) {
      return false;// e.getAction() != MotionEvent.ACTION_DOWN || (getAlpha() > 0f && getVisibility() == View.VISIBLE);
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        trackDownX = e.getX();
        trackDownY = e.getY();
        draggingLeft = draggingUp = false;
        cancelLongTap = false;
        xgramTapToRecord = com.fahadali.xgram.XgramSettings.instance().tapToRecord();
        xgramToggled = false;

        setIsDown(true);
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isDown) {
          float x = e.getX();
          float y = e.getY();
          if (inLongTap && !(cancelLongTap || (cancelLongTap = !UI.getContext(getContext()).getRecordAudioVideoController().isOpen()))) {
            float diffX = Math.max(0f, (trackDownX - x));
            float diffY = Math.max(0f, (trackDownY - y));
            boolean draggingUp = diffY >= Screen.getTouchSlop() && diffY >= diffX - Screen.getTouchSlop();
            boolean draggingLeft = diffX >= Screen.getTouchSlop() && !draggingUp;
            if (!draggingLeft && !draggingUp) {
              draggingLeft = this.draggingLeft;
              draggingUp = this.draggingUp;
            }
            if (draggingLeft || draggingUp) {
              if (!this.draggingLeft && !this.draggingUp) {
                trackDownX = x;
                trackDownY = y;
                diffX = diffY = 0;
              }
              this.draggingLeft = draggingLeft;
              this.draggingUp = draggingUp;
              checkDraggingCircle();
              if (!UI.getContext(getContext()).getRecordAudioVideoController().setTranslations(-diffX, -diffY)) {
                cancelLongTap = true;
              }
            }
          } else {
            trackDownX = x;
            trackDownY = y;
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (isDown) {
          if (inLongTap) {
            setInLongTap(false, false);
          } else if (xgramTapToRecord) {
            cancelLongTap();
            if (!xgramToggled) {
              xgramStartRecordingLocked();
            }
          } else {
            performTap();
          }
          setIsDown(false);
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isDown) {
          setInLongTap(false, true);
          setIsDown(false);
        }
        break;
      }
    }

    return true;
  }
}
