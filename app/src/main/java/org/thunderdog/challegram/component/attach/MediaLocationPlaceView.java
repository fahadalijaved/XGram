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
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.CheckView;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.TimerView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class MediaLocationPlaceView extends FrameLayoutFix implements AttachDelegate, Destroyable, FactorAnimator.Target, TimerView.ActiveStateChangeListener {
  private static final float IMAGE_RADIUS = 20f;

  private static final int FLAG_RED = 1;
  private static final int FLAG_FADED = 1 << 1;
  private static final int FLAG_LIVE_LOCATION = 1 << 2;
  private static final int FLAG_NEED_WAVES = 1 << 3;

  private MediaLocationData location;

  private TextView titleView;
  private TextView addressView;
  private ImageReceiver receiver;
  private CheckView checkView;
  private TimerView timerView;

  private final Drawable iconSmall, iconBig;

  public MediaLocationPlaceView (Context context) {
    super(context);
    iconSmall = Drawables.get(getResources(), R.drawable.baseline_location_on_18);
    iconBig = Drawables.get(getResources(), R.drawable.baseline_location_on_24);
  }

  private int flags;
  private @Nullable ViewController<?> themeProvider;

  private void setIsRed (boolean isRed) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_RED, isRed);
    if (this.flags != flags) {
      this.flags = flags;
      if (themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(titleView);
      }
      int colorId = isRed ? ColorId.textNegative : ColorId.text;
      titleView.setTextColor(Theme.getColor(colorId));
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(titleView, colorId);
      }
    }
  }

  public void setIsFaded (boolean isFaded) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_FADED, isFaded);
    if (this.flags != flags) {
      this.flags = flags;
      float alpha = isFaded ? .6f : 1f;
      titleView.setAlpha(alpha);
      addressView.setAlpha(alpha);
      receiver.invalidate();
    }
  }

  @Override
  public void onActiveStateChanged (TimerView v, boolean isActive) {
    if (xgram) {
      return; // XGram: the remaining time is a ring around the leading visual, the text keeps its 16dp end
    }
    int rightMargin = Screen.dp(IMAGE_RADIUS) + (timerView.isTimerVisible() ? Screen.dp(26f) : 0);
    Views.setRightMargin(titleView, rightMargin);
    Views.setRightMargin(addressView, rightMargin);
  }

  public void init (@Nullable ViewController<?> themeProvider, boolean big) {
    Context context = getContext();

    this.themeProvider = themeProvider;

    int imageRadius = Screen.dp(IMAGE_RADIUS);
    int imagePaddingTop = Screen.dp(8) + (big ? Screen.dp(4f) : 0);
    int iconPadding = 0; // Screen.dp(2f);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.bottomMargin = Screen.dp(10f);
    params.leftMargin = imageRadius * 2 + imageRadius * 2;
    params.rightMargin = imageRadius;

    titleView = new NoScrollTextView(context);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setTextColor(Theme.textAccentColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextAccentColorListener(titleView);
    }
    titleView.setSingleLine();
    titleView.setLayoutParams(params);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    addView(titleView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(10f);
    params.leftMargin = imageRadius * 2 + imageRadius * 2;
    params.rightMargin = imageRadius;

    addressView = new NoScrollTextView(context);
    addressView.setTypeface(Fonts.getRobotoRegular());
    addressView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    addressView.setTextColor(Theme.textDecentColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextDecentColorListener(addressView);
    }
    addressView.setSingleLine();
    addressView.setLayoutParams(params);
    addressView.setEllipsize(TextUtils.TruncateAt.END);
    addView(addressView);

    params = FrameLayoutFix.newParams(Screen.dp(26f), Screen.dp(26f), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
    params.rightMargin = Screen.dp(10f);
    timerView = new TimerView(context);
    timerView.setListener(this);
    timerView.setTextColor(Theme.progressColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(timerView, ColorId.progress);
    }
    timerView.setLayoutParams(params);
    addView(timerView);


    params = FrameLayoutFix.newParams(imageRadius * 2 + Screen.dp(1f) * 2, imageRadius * 2 + Screen.dp(1f) * 2, Gravity.LEFT | Gravity.TOP);
    params.leftMargin = imageRadius - Screen.dp(1f);
    params.topMargin = imagePaddingTop - Screen.dp(1f);
    checkView = new CheckView(context);
    checkView.initWithMode(CheckView.MODE_LOCATION);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(checkView);
    }
    checkView.setLayoutParams(params);
    addView(checkView);

    receiver = new ImageReceiver(this, 0);
    receiver.setBounds(imageRadius + iconPadding, imagePaddingTop + iconPadding, imageRadius + imageRadius * 2 - iconPadding, imagePaddingTop + imageRadius * 2 - iconPadding);

    setWillNotDraw(false);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(big ? 64 : 56f)));

    Views.setClickable(this);
    RippleSupport.setSimpleWhiteBackground(this, themeProvider);

    if (xgram) {
      xgramInit(big);
    }
  }

  // XGram M3E: a two-line list tile (the list decoration makes its background a segmented surfaceBright tile). The
  // leading visual is a 40dp container with 12dp corners: primaryContainer with the venue icon or the pin for a place,
  // tertiaryContainer with the broadcast waves for live location (errorContainer while it offers to stop sharing),
  // or the person's round avatar; a live share's remaining time is a wavy ring around it. Headline titleMedium in
  // onSurface (error to stop sharing), supporting text bodyMedium in onSurfaceVariant. A picked place turns its tile
  // secondaryContainer with the check badge on the leading visual, on a spring.

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private boolean xgramBig, xgramTint;
  private int xgramLivePeriod;
  private long xgramExpiresAt;
  private boolean xgramChecked;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramCheck;
  private @Nullable Drawable xgramPlaceIcon, xgramLiveIcon;

  private void xgramInit (boolean big) {
    this.xgramBig = big;
    xgramPlaceIcon = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_location_on_24));
    xgramLiveIcon = Drawables.get(getResources(), com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_location_on_24));
    com.fahadali.xgram.appearance.XgramExpressive.applyText(titleView, com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, false);
    com.fahadali.xgram.appearance.XgramExpressive.applyText(addressView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
    if (themeProvider != null) {
      themeProvider.removeThemeListenerByTarget(titleView);
      themeProvider.addThemeTextColorListener(titleView, ColorId.text);
    }
    titleView.setTextColor(Theme.getColor(ColorId.text)); // onSurface: the place names are not links
    timerView.setVisibility(GONE);
    checkView.setVisibility(GONE);
    final boolean rtl = Lang.rtl();
    xgramTextParams(titleView, rtl, Screen.dp(10f), 0);
    xgramTextParams(addressView, rtl, 0, Screen.dp(11f));
    titleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
    addressView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
  }

  private static void xgramTextParams (TextView view, boolean rtl, int bottomMargin, int topMargin) {
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) view.getLayoutParams();
    params.leftMargin = Screen.dp(rtl ? 16f : com.fahadali.xgram.appearance.XgramR6B7Places.TEXT_START);
    params.rightMargin = Screen.dp(rtl ? com.fahadali.xgram.appearance.XgramR6B7Places.TEXT_START : 16f);
    params.bottomMargin = bottomMargin;
    params.topMargin = topMargin;
    view.setLayoutParams(params);
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (xgram && w > 0 && h > 0) {
      // The 40dp leading visual, 16dp from the start, centred vertically
      final int size = Screen.dp(com.fahadali.xgram.appearance.XgramR6B7Places.LEADING);
      final int left = Lang.rtl() ? w - Screen.dp(16f) - size : Screen.dp(16f);
      final int top = (h - size) / 2;
      receiver.setBounds(left, top, left + size, top + size);
      if (progressComponent != null) {
        progressComponent.setBounds(left, top, left + size, top + size);
      }
    }
  }

  private float xgramCheckFactor () {
    return xgramCheck != null ? xgramCheck.getValue() : (xgramChecked ? 1f : 0f);
  }

  /** A picked place: its tile in secondaryContainer and the check badge, springing while on screen. */
  private void xgramSetChecked (boolean checked, boolean animated) {
    this.xgramChecked = checked;
    final float target = checked ? 1f : 0f;
    if (xgramCheck == null) {
      if (!checked) {
        return; // never checked: nothing to undo
      }
      xgramCheck = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> {
        xgramApplyTileColor();
        invalidate();
      });
      xgramCheck.setThreshold(.005f);
    }
    if (animated && isAttachedToWindow() && getWidth() > 0) {
      xgramCheck.animateTo(target);
    } else if (xgramCheck.getValue() != target || xgramCheck.isRunning()) {
      xgramCheck.setValue(target);
      xgramApplyTileColor();
      invalidate();
    }
  }

  private void xgramApplyTileColor () {
    com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable morph = com.fahadali.xgram.appearance.XgramExpressive.findMorph(getBackground());
    if (morph == null) {
      return;
    }
    final float f = me.vkryl.core.MathUtils.clamp(xgramCheckFactor());
    if (f <= 0f) {
      morph.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT);
    } else if (f >= 1f) {
      morph.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER);
    } else {
      morph.setColor(ColorUtils.fromToArgb(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT), com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), f));
    }
  }

  private void xgramDraw (Canvas c) {
    if (xgramCheck == null || !xgramCheck.isRunning()) {
      xgramApplyTileColor(); // the list gives the tile its background after binding: settle its colour here
    }
    final float cx = receiver.centerX(), cy = receiver.centerY();
    final float alpha = (flags & FLAG_FADED) != 0 ? .6f : 1f;
    final float leading = com.fahadali.xgram.appearance.XgramR6B7Places.LEADING;
    if ((flags & FLAG_LIVE_LOCATION) != 0) {
      final boolean red = (flags & FLAG_RED) != 0;
      final int containerRole = red ? com.fahadali.xgram.appearance.XgramExpressive.ERROR_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.TERTIARY_CONTAINER;
      final int contentRole = red ? com.fahadali.xgram.appearance.XgramExpressive.ON_ERROR_CONTAINER : com.fahadali.xgram.appearance.XgramExpressive.ON_TERTIARY_CONTAINER;
      final int content = com.fahadali.xgram.appearance.XgramExpressive.color(contentRole);
      final float progressFactor = progressAnimator != null ? progressAnimator.getFloatValue() : 0f;
      com.fahadali.xgram.appearance.XgramR6B7Places.drawContainer(c, cx, cy, leading, com.fahadali.xgram.appearance.XgramR6B7Places.LEADING_CORNER,
        com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(containerRole), alpha));
      if (progressFactor < 1f) {
        com.fahadali.xgram.appearance.XgramR6B7Places.drawIcon(c, xgramLiveIcon, cx, cy, content, (1f - progressFactor) * alpha);
        if ((flags & FLAG_NEED_WAVES) != 0 && !com.fahadali.xgram.appearance.XgramMotion.reduce()) {
          long delay = DrawAlgorithms.drawWaves(c, cx, cy - Screen.dp(2f), ColorUtils.alphaColor((1f - progressFactor) * alpha, content), false, nextScheduleTime);
          if (delay != -1) {
            nextScheduleTime = SystemClock.uptimeMillis() + delay;
            postInvalidateDelayed(delay, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
          }
        }
      }
      if (progressComponent != null) {
        progressComponent.forceColor(content);
        progressComponent.draw(c);
      }
      xgramDrawRing(c, cx, cy, alpha);
      return;
    }

    if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, cx, cy);
      xgramDrawRing(c, cx, cy, alpha);
      return;
    }

    final boolean avatar = !xgramTint && receiver.getCurrentFile() != null;
    if (letters != null && receiver.getCurrentFile() == null) {
      // A person without a photo: their accent circle with the initials, as their avatar elsewhere
      c.drawCircle(cx, cy, Screen.dp(IMAGE_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(alpha, accentColor != null ? accentColor.getPrimaryColor() : Theme.getColor(ColorId.fileAttach))));
      Paints.drawLetters(c, letters, cx - lettersWidth / 2, cy + Screen.dp(6f), 17f, alpha);
    } else if (!avatar) {
      com.fahadali.xgram.appearance.XgramR6B7Places.drawContainer(c, cx, cy, leading, com.fahadali.xgram.appearance.XgramR6B7Places.LEADING_CORNER,
        com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER), alpha));
      final int content = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER);
      if (receiver.needPlaceholder()) {
        com.fahadali.xgram.appearance.XgramR6B7Places.drawIcon(c, xgramPlaceIcon, cx, cy, content, alpha - receiver.getDisplayAlpha());
      }
      receiver.setPorterDuffColorFilter(content, 1f, false); // the venue glyph in onPrimaryContainer
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    } else {
      receiver.setPorterDuffColorFilter(ColorId.NONE, 1f, true); // a photo: no tint
      if (receiver.needPlaceholder()) {
        c.drawCircle(cx, cy, Screen.dp(IMAGE_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.placeholderColor())));
      }
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    }
    xgramDrawRing(c, cx, cy, alpha);
    final float check = xgramCheckFactor();
    if (check > 0f) {
      final float half = Screen.dpf(leading) / 2f;
      final float bx = Lang.rtl() ? cx - half + Screen.dp(2f) : cx + half - Screen.dp(2f);
      com.fahadali.xgram.appearance.XgramR5Security.drawCheckBadge(c, bx, cy + half - Screen.dp(2f), check, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER));
    }
  }

  private void xgramDrawRing (Canvas c, float cx, float cy, float alpha) {
    if (xgramExpiresAt == 0 || xgramLivePeriod <= 0) {
      return;
    }
    long delay = com.fahadali.xgram.appearance.XgramR6B7Places.drawLiveRing(c, cx, cy, com.fahadali.xgram.appearance.XgramR6B7Places.LEADING,
      (flags & FLAG_LIVE_LOCATION) != 0 ? com.fahadali.xgram.appearance.XgramExpressive.TERTIARY : com.fahadali.xgram.appearance.XgramExpressive.PRIMARY,
      xgramLivePeriod, xgramExpiresAt, alpha);
    if (delay != -1) {
      final int pad = Screen.dp(8f);
      postInvalidateDelayed(delay, receiver.getLeft() - pad, receiver.getTop() - pad, receiver.getRight() + pad, receiver.getBottom() + pad);
    }
  }

  // Adapter

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
  }

  // Data

  private TdlibAccentColor accentColor;
  private Letters letters;
  private float lettersWidth;

  public void setLocation (String title, String subtitle, TdlibAccentColor accentColor, Letters letters, boolean isFaded, int livePeriod, long expiresAt) {
    clearLiveLocation();
    setIsFaded(isFaded);
    timerView.setLivePeriod(livePeriod, expiresAt);
    if (xgram) {
      xgramLivePeriod = livePeriod;
      xgramExpiresAt = expiresAt;
      xgramSetChecked(false, false);
    }
    titleView.setText(title);
    addressView.setText(subtitle);
    boolean needInvalidate = false;
    if (this.accentColor != accentColor) {
      this.accentColor = accentColor;
      needInvalidate = true;
    }
    if (!StringUtils.equalsOrBothEmpty(this.letters != null ? this.letters.text : null, letters != null ? letters.text : null)) {
      this.letters = letters;
      this.lettersWidth = Paints.measureLetters(letters, 17f);
      needInvalidate = true;
    }
    if (needInvalidate) {
      receiver.invalidate();
    }
  }

  public void setLocationImage (ImageFile image) {
    xgramTint = true; // XGram: a venue glyph, tinted in its container
    receiver.setRadius(0);
    receiver.requestFile(image);
  }

  public void setRoundedLocationImage (ImageFile image) {
    xgramTint = false; // XGram: a photo
    receiver.setRadius(Screen.dp(IMAGE_RADIUS));
    receiver.requestFile(image);
  }

  private AvatarPlaceholder avatarPlaceholder;

  public void setPlaceholder (AvatarPlaceholder.Metadata metadata) {
    receiver.clear();
    avatarPlaceholder = new AvatarPlaceholder(IMAGE_RADIUS, metadata, null);
  }

  public void setLocation (MediaLocationData location, boolean isChecked) {
    clearLiveLocation();
    if (this.location == null || !this.location.equals(location)) {
      this.location = location;
      receiver.requestFile(location.getIconImage());
      titleView.setText(location.getTitle());
      addressView.setText(location.getAddress());
    }
    checkView.forceSetChecked(isChecked);
    if (xgram) {
      xgramTint = true;
      xgramLivePeriod = 0;
      xgramExpiresAt = 0;
      xgramSetChecked(isChecked, false);
    }
  }

  public void setDefaultLiveLocation (boolean needWaves) {
    setLiveLocation(com.fahadali.xgram.appearance.XgramR5Lists.sentence(R.string.ShareLiveLocation, R.string.XgramR6B7ShareLiveLocation), Lang.getString(R.string.SendLiveLocationInfo), false, needWaves, false, 0, 0); // XGram: sentence case
  }

  private Runnable subtitleUpdater;

  public Runnable getSubtitleUpdater () {
    return subtitleUpdater;
  }

  public void scheduleSubtitleUpdater (final Runnable updater, long delay) {
    if (this.subtitleUpdater != null) {
      removeCallbacks(subtitleUpdater);
    }
    this.subtitleUpdater = updater;
    if (updater != null) {
      postDelayed(updater, delay);
    }
  }

  public void updateSubtitle (CharSequence newSubtitle) {
    addressView.setText(newSubtitle);
  }

  private void clearLiveLocation () {
    this.flags &= ~FLAG_LIVE_LOCATION;
    this.flags &= ~FLAG_NEED_WAVES;
    scheduleSubtitleUpdater(null, -1);
    setIsRed(false);
  }

  public void setLiveLocation (String title, String subtitle, boolean isRed, boolean needWaves, boolean isFaded, int livePeriod, long expiresAt) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_NEED_WAVES, needWaves);
    flags = BitwiseUtils.setFlag(flags, FLAG_FADED, isFaded);
    flags = BitwiseUtils.setFlag(flags, FLAG_LIVE_LOCATION, true);
    this.flags = flags;
    this.subtitleUpdater = null;
    this.letters = null;
    setIsRed(isRed);
    setIsFaded(isFaded);
    timerView.setLivePeriod(livePeriod, expiresAt);
    titleView.setText(title);
    addressView.setText(subtitle);
    receiver.requestFile(null);
    checkView.forceSetChecked(false);
    if (xgram) {
      xgramLivePeriod = livePeriod;
      xgramExpiresAt = expiresAt;
      xgramSetChecked(false, false);
      invalidate(); // the container switches between tertiary and error
    }
    receiver.invalidate();
  }

  private BoolAnimator progressAnimator;
  private ProgressComponent progressComponent;
  private static final int ANIMATOR_PROGRESS = 0;

  public void setInProgress (boolean inProgress, boolean animated) {
    boolean nowInProgress = progressAnimator != null && progressAnimator.getValue();
    if (nowInProgress != inProgress) {
      if (progressAnimator == null) {
        progressAnimator = new BoolAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
        com.fahadali.xgram.appearance.XgramAppMotion.use(progressAnimator, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, true); // XGram
      }
      if (progressComponent == null) {
        progressComponent = ProgressComponent.simpleInstance(this, 5f, receiver.getLeft(), receiver.getTop(), receiver.getWidth(), receiver.getHeight());
      }
      progressAnimator.setValue(inProgress, animated);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    progressComponent.setAlpha(factor);
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public MediaLocationData getLocation () {
    return location;
  }

  // Check

  public void setChecked (boolean isChecked, boolean animated) {
    if (animated) {
      checkView.setChecked(isChecked);
    } else {
      checkView.forceSetChecked(isChecked);
    }
    if (xgram) {
      xgramSetChecked(isChecked, animated);
    }
  }

  // Drawing

  private long nextScheduleTime;

  @Override
  protected void onDraw (Canvas c) {
    if (xgram) {
      xgramDraw(c);
      return;
    }
    int cx = receiver.centerX();
    int cy = receiver.centerY();

    float alpha = (flags & FLAG_FADED) != 0 ? .6f : 1f;

    if ((flags & FLAG_LIVE_LOCATION) != 0) {
      float progressFactor = progressAnimator != null ? progressAnimator.getFloatValue() : 0f;
      c.drawCircle(cx, cy, Screen.dp(IMAGE_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.getColor(ColorId.fileRed))));

      if (progressFactor < 1f) {
        Paint bitmapPaint = Paints.whitePorterDuffPaint();
        bitmapPaint.setAlpha((int) (255f * (1f - progressFactor) * alpha));
        Drawables.draw(c, iconSmall, cx - iconSmall.getMinimumWidth() / 2, cy - iconSmall.getMinimumHeight() / 2, bitmapPaint);
        bitmapPaint.setAlpha(255);

        if ((flags & FLAG_NEED_WAVES) != 0) {
          long delay = DrawAlgorithms.drawWaves(c, cx, cy, ColorUtils.color((int) (255f * (1f - progressFactor) * alpha), 0xffffff), false, nextScheduleTime);
          if (delay != -1) {
            nextScheduleTime = SystemClock.uptimeMillis() + delay;
            postInvalidateDelayed(delay, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
          }
        }
      }

      if (progressComponent != null) {
        progressComponent.draw(c);
      }

      return;
    }

    if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, cx, cy);
      return;
    }

    c.drawCircle(cx, cy, Screen.dp(IMAGE_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(alpha, accentColor != null ? accentColor.getPrimaryColor() : Theme.getColor(ColorId.fileAttach))));
    if (letters != null) {
      Paints.drawLetters(c, letters, cx - lettersWidth / 2, cy + Screen.dp(6f), 17f, alpha);
    }
    if (letters == null || receiver.getCurrentFile() != null) {
      if (receiver.needPlaceholder()) {
        float iconAlpha = alpha - receiver.getDisplayAlpha();
        Paint paint = Paints.whitePorterDuffPaint();
        paint.setAlpha((int) (255f * iconAlpha));
        Drawables.draw(c, iconBig, cx - iconBig.getMinimumWidth() / 2, cy - iconBig.getMinimumHeight() / 2, paint);
        paint.setAlpha(255);
      }
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    }
  }
}
