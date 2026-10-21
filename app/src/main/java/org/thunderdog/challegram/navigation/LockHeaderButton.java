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
 * File created on 13/02/2016 at 12:24
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.PasscodeController;
import org.thunderdog.challegram.unsorted.Passcode;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class LockHeaderButton extends HeaderButton implements View.OnClickListener, View.OnLongClickListener, FactorAnimator.Target {
  private static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3f);

  private final BoolAnimator animator = new BoolAnimator(0, this, OVERSHOOT_INTERPOLATOR, 130l);

  private Drawable lock, base;

  // XGram M3E: the lock and the open lock from the icon pack, crossfading as the shackle lifts or drops on a
  // SPATIAL_FAST spring (a crossfade only with Reduce motion), in onSurfaceVariant, on a 48dp icon button; Telegram X's
  // two pieces (lock_top, lock_base) with Expressive design off.
  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private @Nullable Drawable xgramLocked, xgramOpen;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramOpenSpring;
  private final Paint xgramPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private int xgramColor;

  public LockHeaderButton (Context context) {
    super(context);

    if (xgram) {
      xgramLocked = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_lock_24));
      xgramOpen = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.xgram_m3e_r6_b2_lock_open_24));
      xgramOpenSpring = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, isVisuallyLocked() ? 0f : 1f, (s, value) -> invalidate());
      final String label = Lang.getString(R.string.XgramR6B2LockApp);
      setContentDescription(label);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setTooltipText(label);
      }
    } else {
      lock = Drawables.get(getResources(), R.drawable.baseline_lock_top_24);
      base = Drawables.get(getResources(), R.drawable.baseline_lock_base_24);
    }

    setId(R.id.menu_btn_lock);
    setButtonBackground(ThemeDeprecated.headerSelector());
    setVisibility(xgramShown() ? View.VISIBLE : View.GONE);
    setOnClickListener(this);
    setOnLongClickListener(this);
    setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(xgram ? 48f : 49f), ViewGroup.LayoutParams.MATCH_PARENT));
  }

  /** XGram M3E: open (1) or locked (0); the spring snaps when not animated. */
  private void xgramSetOpen (boolean open, boolean animated) {
    if (xgramOpenSpring == null) {
      return;
    }
    final float target = open ? 1f : 0f;
    if (animated) {
      xgramOpenSpring.animateTo(target);
    } else if (xgramOpenSpring.getTarget() != target || xgramOpenSpring.getValue() != target) {
      xgramOpenSpring.setValue(target);
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    animator.setValue(!isVisuallyLocked(), false);
    if (xgramOpenSpring != null && !xgramOpenSpring.isRunning()) {
      xgramSetOpen(!isVisuallyLocked(), false); // XGram M3E
    }
  }

  private static boolean isVisuallyLocked () {
    return Passcode.instance().isLocked() || Passcode.instance().getAutolockMode() == Passcode.AUTOLOCK_MODE_INSTANT;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onClick (View v) {
    if (Passcode.instance().getAutolockMode() == Passcode.AUTOLOCK_MODE_INSTANT) {
      // XGram M3E: an M3 snackbar whose action opens the passcode settings (what holding the button does)
      if (xgram && com.fahadali.xgram.appearance.XgramSnackbar.show(Lang.getString(R.string.XgramR6B2LockInstant), Toast.LENGTH_LONG, Lang.getString(R.string.XgramR6B2LockChange), () -> onLongClick(this))) {
        return;
      }
      UI.showToast(R.string.AutoLockInstantWarn, Toast.LENGTH_SHORT);
      return;
    }
    final boolean open = !Passcode.instance().toggleLock();
    animator.setValue(open, true);
    xgramSetOpen(open, true); // XGram M3E
    UI.getContext(getContext()).checkPasscode(true);
  }

  @Override
  public boolean onLongClick (View v) {
    NavigationController navigation = UI.getContext(getContext()).navigation();
    ViewController<?> current = navigation != null ? navigation.getCurrentStackItem() : null;
    if (current != null) {
      PasscodeController passcode = new PasscodeController(UI.getContext(getContext()), current.tdlib());
      passcode.setPasscodeMode(PasscodeController.MODE_UNLOCK_SETUP);
      navigation.navigateTo(passcode);
      return true;
    }
    return false;
  }

  /** XGram: shown while a passcode is set, unless Passcode lock → Lock button in the chat list is off. */
  private static boolean xgramShown () {
    return Passcode.instance().isEnabled() && !com.fahadali.xgram.XgramSettings.instance().hideLockButton();
  }

  private final com.fahadali.xgram.XgramSettings.Listener xgramSettingsListener = key -> {
    if (com.fahadali.xgram.XgramSettings.KEY_HIDE_LOCK_BUTTON.equals(key)) {
      UI.post(this::update);
    }
  };

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    com.fahadali.xgram.XgramSettings.instance().addListener(xgramSettingsListener);
    update(); // the option may have changed while this header was off screen
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    com.fahadali.xgram.XgramSettings.instance().removeListener(xgramSettingsListener);
  }

  public void update () {
    final int visibility = xgramShown() ? View.VISIBLE : View.GONE;
    final boolean open = !isVisuallyLocked();
    animator.setValue(open, false);
    xgramSetOpen(open, false); // XGram M3E
    if (visibility != getVisibility()) {
      setVisibility(visibility);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (xgram) {
      xgramDraw(c);
      return;
    }
    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;

    Paint paint = Paints.getHeaderIconPaint();
    Drawables.draw(c, lock, cx - lock.getMinimumWidth() / 2 + (int) ((float) Screen.dp(8f) * animator.getFloatValue()), cy - lock.getMinimumHeight() / 2, paint);
    Drawables.draw(c, base, cx - base.getMinimumWidth() / 2, cy - base.getMinimumHeight() / 2, paint);
  }

  /**
   * XGram M3E: the closed lock fades out rising a little as the open one drops into place (and back), both on the
   * shackle's spring, whose overshoot gives the open glyph a small bounce. The icon button's state layer is
   * HeaderButton's (the background); onSurfaceVariant, or the header's icon color where the header sets one.
   */
  private void xgramDraw (Canvas c) {
    super.onDraw(c); // the state layer color sync of HeaderButton (it has no drawable of its own here)
    if (xgramLocked == null || xgramOpen == null || xgramOpenSpring == null) {
      return;
    }
    final int color = getColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT));
    if (xgramColor != color || xgramPaint.getColorFilter() == null) {
      xgramColor = color;
      xgramPaint.setColorFilter(Paints.getColorFilter(color));
    }
    final float value = xgramOpenSpring.getValue();
    final float open = me.vkryl.core.MathUtils.clamp(value);
    final float lift = Screen.dp(2f);
    final float cx = getMeasuredWidth() / 2f, cy = getMeasuredHeight() / 2f;
    if (open < 1f) {
      xgramPaint.setAlpha(Math.round(255 * (1f - open)));
      Drawables.draw(c, xgramLocked, cx - xgramLocked.getMinimumWidth() / 2f, cy - xgramLocked.getMinimumHeight() / 2f - lift * open, xgramPaint);
    }
    if (open > 0f) {
      xgramPaint.setAlpha(Math.round(255 * open));
      Drawables.draw(c, xgramOpen, cx - xgramOpen.getMinimumWidth() / 2f, cy - xgramOpen.getMinimumHeight() / 2f + lift * (1f - value), xgramPaint);
    }
    xgramPaint.setAlpha(255);
  }
}
