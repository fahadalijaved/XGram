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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramM3Chat;
import com.fahadali.xgram.appearance.XgramM3Content;
import com.fahadali.xgram.appearance.XgramMotion;
import com.fahadali.xgram.appearance.XgramWavyProgress;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

/**
 * The bar of a waiting message (see {@link XgramUndoSend}), above the message box of the chat that sent it: a ring that
 * drains as the time runs out with the seconds left in it, the start of the message, "Sending in 4 sec" with what else
 * matters (the reply, silent, parts, how many more wait), Undo and Send now. The newest waiting message is shown; Undo
 * takes it back, Send now sends everything that waits. Its height moves the message list and the buttons over it, as the
 * reply bar does ({@link #visibleHeight()}).
 *
 * <p>Expressive design: a floating surfaceContainerHigh card of the chat's card group (XgramM3Chat.CardDrawable) with
 * the composer field's side margins, a wavy primary ring, bodyMedium / bodySmall text, an Undo text button and a tonal
 * Send now icon button; it rises on SPATIAL_DEFAULT and leaves with the bubble that replaces it (the list's one-shot
 * 140 ms DECELERATE item animator). Otherwise Telegram X's look: a full-width 48dp bar on the filling color like the
 * reply bar, a progress arc, UNDO and the send icon, 200 ms DECELERATE, and an instant hand-off (the list has no item
 * animator). The look is picked when the bar is made: switching Expressive design restarts the app.
 */
public final class XgramUndoSendBar extends FrameLayoutFix implements FactorAnimator.Target {
  private static final float M3_CARD_DP = 58f; // CardDrawable insets 1dp at the top and the bottom: a 56dp card
  private static final float M3_BAR_DP = 63f; // the card and a 6dp gap above the message box
  private static final float CLASSIC_BAR_DP = 48f;
  private static final String SEPARATOR = " · ";

  private final MessagesController owner;
  private final boolean m3;
  private final int barHeight;
  /** What fades in with Expressive design: the card. */
  private final View content;
  private final Ring ring;
  private final TextView title, status, undo;
  private final ImageView sendNow;
  private final BoolAnimator visible;
  private final Runnable tick = this::onTick;

  @Nullable private XgramUndoSend.Shown shown;
  private int moreCount;
  private long announcedOrder = -1;
  private long flushStartUptime;

  public XgramUndoSendBar (Context context, MessagesController owner) {
    super(context);
    this.owner = owner;
    this.m3 = XgramExpressive.isEnabled();
    this.barHeight = Screen.dp(m3 ? M3_BAR_DP : CLASSIC_BAR_DP);
    this.visible = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
    final boolean rtl = Lang.rtl();

    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      row.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    ring = new Ring(context);
    int ringSize = Screen.dp(m3 ? 28f : 24f);
    LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(ringSize, ringSize);
    ringParams.setMargins(rtl ? 0 : Screen.dp(16f), 0, rtl ? Screen.dp(16f) : 0, 0);
    ring.setLayoutParams(ringParams);
    ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(ring);

    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    texts.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    textsParams.setMargins(rtl ? Screen.dp(8f) : Screen.dp(16f), 0, rtl ? Screen.dp(16f) : Screen.dp(8f), 0);
    texts.setLayoutParams(textsParams);
    row.addView(texts);

    title = new RoleText(context, m3 ? XgramExpressive.ON_SURFACE : -1);
    status = new RoleText(context, m3 ? XgramExpressive.ON_SURFACE_VARIANT : -1);
    for (TextView view : new TextView[] {title, status}) {
      view.setSingleLine(true);
      view.setEllipsize(TextUtils.TruncateAt.END);
      view.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
      view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      texts.addView(view);
    }
    status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      // Announced once per waiting message: the description changes with the message, not every second.
      title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    undo = new RoleText(context, m3 ? XgramExpressive.PRIMARY : -1);
    undo.setText(Lang.getString(R.string.XgramUndoSendUndo));
    undo.setGravity(Gravity.CENTER);
    undo.setSingleLine(true);
    undo.setContentDescription(Lang.getString(R.string.XgramUndoSendA11yUndo));
    undo.setOnClickListener(v -> onUndoClick());
    row.addView(undo);

    sendNow = new RoleImage(context, m3 ? XgramExpressive.ON_SECONDARY_CONTAINER : -1);
    sendNow.setScaleType(ImageView.ScaleType.CENTER);
    sendNow.setImageResource(XgramIconPack.map(R.drawable.baseline_send_24));
    sendNow.setContentDescription(Lang.getString(R.string.XgramUndoSendNow));
    sendNow.setOnClickListener(v -> onSendNowClick());
    row.addView(sendNow);

    if (m3) {
      // A floating card with the composer field's horizontal bounds, 6dp above the message box (and the reply block).
      FrameLayoutFix card = new FrameLayoutFix(context);
      android.widget.FrameLayout.LayoutParams cardParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(M3_CARD_DP), Gravity.TOP);
      cardParams.leftMargin = cardParams.rightMargin = XgramM3Chat.fieldStart();
      card.setLayoutParams(cardParams);
      card.setBackground(new XgramM3Chat.CardDrawable());
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
      }
      owner.addThemeInvalidateListener(card);
      row.setPadding(rtl ? Screen.dp(8f) : 0, 0, rtl ? 0 : Screen.dp(8f), 0);
      row.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      card.addView(row);
      addView(card);
      content = card;

      XgramExpressive.applyText(title, XgramExpressive.TS_BODY_M, false);
      XgramExpressive.applyText(status, XgramExpressive.TS_BODY_S, false);
      XgramExpressive.applyText(undo, XgramExpressive.TS_LABEL_L, false);
      // Text button: no container, a full-corner primary state layer that morphs to 8dp while pressed.
      LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f));
      undo.setLayoutParams(undoParams);
      undo.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      undo.setMinWidth(Screen.dp(48f));
      XgramM3Chat.setTextButtonBackgroundRole(undo, XgramExpressive.PRIMARY);
      // Tonal icon button: secondaryContainer, full corners springing to 12dp while pressed.
      LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f));
      sendParams.setMargins(rtl ? 0 : Screen.dp(4f), 0, rtl ? Screen.dp(4f) : 0, 0);
      sendNow.setLayoutParams(sendParams);
      XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(sendNow, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), 20f, 12f);
      morph.setColorRole(XgramExpressive.SECONDARY_CONTAINER);
      for (View view : new View[] {title, status, undo, sendNow, ring}) {
        owner.addThemeInvalidateListener(view);
      }
    } else {
      // Telegram X: a full-width bar on the filling color, like the reply bar.
      ViewSupport.setThemedBackground(this, ColorId.filling, owner);
      row.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight));
      addView(row);
      content = row;

      title.setTypeface(Fonts.getRobotoRegular());
      title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      title.setTextColor(Theme.getColor(ColorId.text));
      owner.addThemeTextColorListener(title, ColorId.text);
      status.setTypeface(Fonts.getRobotoRegular());
      status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      status.setTextColor(Theme.getColor(ColorId.textLight));
      owner.addThemeTextColorListener(status, ColorId.textLight);
      undo.setTypeface(Fonts.getRobotoMedium());
      undo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      undo.setAllCaps(true);
      undo.setTextColor(Theme.getColor(ColorId.textNeutral));
      owner.addThemeTextColorListener(undo, ColorId.textNeutral);
      undo.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      undo.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
      RippleSupport.setTransparentSelector(undo);
      owner.addThemeInvalidateListener(undo);
      sendNow.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48f), ViewGroup.LayoutParams.MATCH_PARENT));
      sendNow.setColorFilter(Theme.getColor(ColorId.chatSendButton));
      owner.addThemeFilterListener(sendNow, ColorId.chatSendButton);
      sendNow.setBackgroundResource(R.drawable.bg_btn_header);
      owner.addThemeInvalidateListener(ring);
    }
    Views.setClickable(undo);
    Views.setClickable(sendNow);

    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight));
    setVisibility(View.INVISIBLE);
  }

  /** The bar's full height: its layout height. */
  public int barHeight () {
    return barHeight;
  }

  /** How far the bar pushes up what is above the message box right now. */
  public float visibleHeight () {
    return barHeight * visible.getFloatValue();
  }

  /** Set by the chat on every move of the reply bar, the keyboard and in-chat search. */
  public void applyPosition (float translationY, float searchVisibility) {
    setTranslationY(translationY);
    setAlpha(MathUtils.clamp(searchVisibility));
  }

  /** Hidden at once, without telling anyone (the chat closes or shows another chat). */
  public void reset () {
    removeCallbacks(tick);
    visible.setValue(false, false);
    shown = null;
    setVisibility(View.INVISIBLE);
  }

  /** What waits in the owner's message box changed. */
  public void update (boolean handoff, boolean animated) {
    List<XgramUndoSend.Shown> list = XgramUndoSend.itemsOf(owner);
    if (list.isEmpty()) {
      removeCallbacks(tick);
      if (visible.getValue()) {
        boolean animate = animated && !XgramMotion.reduce();
        if (handoff) {
          if (m3) {
            // In step with the bubble that comes in where the card was (MessagesRecyclerView's one-shot animator).
            setMotion(AnimatorUtils.DECELERATE_INTERPOLATOR, MessagesRecyclerView.ITEM_ANIMATOR_DURATION);
          } else {
            animate = false; // the list has no item animator: the bubble is there in this frame
          }
        } else if (m3) {
          setMotion(XgramAppMotion.springClamped(XgramExpressive.SPATIAL_FAST), XgramAppMotion.springDuration(XgramExpressive.SPATIAL_FAST));
        } else {
          setMotion(AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
        }
        visible.setValue(false, animate);
      }
      return;
    }
    XgramUndoSend.Shown newest = list.get(list.size() - 1);
    boolean changed = shown == null || shown.order != newest.order;
    if (newest.flushing && (changed || !shown.flushing)) {
      flushStartUptime = SystemClock.uptimeMillis();
    }
    shown = newest;
    moreCount = list.size() - 1;
    if (!TextUtils.equals(title.getText(), newest.preview)) {
      title.setText(newest.preview);
    }
    updateStatus();
    if (changed && !newest.flushing && announcedOrder != newest.order) {
      announcedOrder = newest.order;
      title.setContentDescription(Lang.getString(R.string.XgramUndoSendA11y, Lang.plural(R.string.xSeconds, secondsLeft(newest))));
    }
    ring.invalidate();
    scheduleTick();
    if (!visible.getValue()) {
      setVisibility(View.VISIBLE);
      boolean reduce = XgramMotion.reduce();
      if (m3) {
        setMotion(XgramAppMotion.springClamped(XgramExpressive.SPATIAL_DEFAULT), XgramAppMotion.springDuration(XgramExpressive.SPATIAL_DEFAULT));
      } else {
        setMotion(AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
      }
      visible.setValue(true, animated && !reduce);
      if (reduce && animated) {
        // Reduce motion: no movement, a fade.
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(150L).start();
      }
    }
  }

  private void setMotion (Interpolator interpolator, long duration) {
    visible.setInterpolator(interpolator);
    visible.setDuration(duration);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (m3) {
      content.setAlpha(MathUtils.clamp(factor));
    }
    owner.xgramOnUndoBarMoved();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 0f) {
      removeCallbacks(tick);
      shown = null;
      setVisibility(View.INVISIBLE);
    }
  }

  // Text

  private static int secondsLeft (XgramUndoSend.Shown s) {
    long left = s.dueUptime - SystemClock.uptimeMillis();
    return (int) Math.max(1L, (left + 999L) / 1000L);
  }

  private boolean undoEnabled () {
    XgramUndoSend.Shown s = shown;
    return s != null && !s.flushing && !owner.isEditingMessage();
  }

  private void updateStatus () {
    XgramUndoSend.Shown s = shown;
    if (s == null) {
      return;
    }
    int seconds = secondsLeft(s);
    StringBuilder b = new StringBuilder(s.flushing ? Lang.getString(R.string.XgramUndoSendSending) : Lang.getString(R.string.XgramUndoSendIn, Lang.plural(R.string.xSec, seconds)));
    if (s.replyName != null) {
      b.append(SEPARATOR).append(Lang.getString(R.string.XgramUndoSendReplyTo, s.replyName));
    }
    if (s.silent) {
      b.append(SEPARATOR).append(Lang.getString(R.string.XgramUndoSendSilent));
    }
    if (s.parts > 1) {
      b.append(SEPARATOR).append(Lang.getString(R.string.XgramUndoSendParts, s.parts));
    }
    if (moreCount > 0) {
      b.append(SEPARATOR).append(Lang.getString(R.string.XgramUndoSendMoreWaiting, moreCount));
    }
    if (!TextUtils.equals(status.getText(), b)) {
      status.setText(b.toString());
    }
    ring.setSeconds(s.flushing ? 0 : seconds);
    // Undo is dimmed while the message box edits another message, and once the message is on its way.
    boolean enabled = undoEnabled();
    undo.setEnabled(enabled);
    undo.setAlpha(enabled ? 1f : XgramExpressive.DISABLED_CONTENT);
    sendNow.setEnabled(!s.flushing);
    sendNow.setAlpha(s.flushing ? XgramExpressive.DISABLED_CONTENT : 1f);
  }

  private void scheduleTick () {
    removeCallbacks(tick);
    XgramUndoSend.Shown s = shown;
    if (s == null || s.flushing) {
      return;
    }
    long left = s.dueUptime - SystemClock.uptimeMillis();
    if (left <= 0) {
      return;
    }
    long delay = left % 1000L;
    postDelayed(tick, (delay == 0 ? 1000L : delay) + 8L);
  }

  private void onTick () {
    updateStatus();
    scheduleTick();
  }

  // Buttons

  private void onUndoClick () {
    XgramUndoSend.Shown s = shown;
    if (s == null || !undoEnabled()) {
      return;
    }
    if (s.kind != XgramUndoSendItem.KIND_TEXT) {
      // A recording can't go back into the message box: discarding it is asked first.
      final long order = s.order;
      owner.showOptions(Lang.getString(s.kind == XgramUndoSendItem.KIND_VIDEO ? R.string.XgramUndoSendDiscardVideo : R.string.XgramUndoSendDiscardVoice),
        new int[] {R.id.btn_xgramUndoSendDiscard, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Discard), Lang.getString(R.string.Cancel)},
        new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          if (id == R.id.btn_xgramUndoSendDiscard && XgramUndoSend.undo(owner, order)) {
            confirmHaptic();
          }
          return true;
        });
      return;
    }
    if (XgramUndoSend.undoNewest(owner)) {
      confirmHaptic();
    }
  }

  private void confirmHaptic () {
    performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
  }

  private void onSendNowClick () {
    XgramUndoSend.Shown s = shown;
    if (s == null || s.flushing) {
      return;
    }
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    XgramUndoSend.sendNow(owner);
  }

  // Views

  /** Text in an M3 color role read at draw time (theme switches, the animated day / night switch, Material You). */
  private static final class RoleText extends TextView {
    private final int role;

    RoleText (Context context, int role) {
      super(context);
      this.role = role;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (role >= 0) {
        int color = XgramExpressive.color(role);
        if (getCurrentTextColor() != color) {
          setTextColor(color);
        }
      }
      super.onDraw(c);
    }
  }

  /** An icon in an M3 color role read at draw time. */
  private static final class RoleImage extends ImageView {
    private final int role;
    private int appliedColor;
    private boolean applied;

    RoleImage (Context context, int role) {
      super(context);
      this.role = role;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (role >= 0) {
        int color = XgramExpressive.color(role);
        if (!applied || appliedColor != color) {
          applied = true;
          appliedColor = color;
          setColorFilter(color);
        }
      }
      super.onDraw(c);
    }
  }

  /**
   * The countdown: a ring that drains clockwise from 12 o'clock, the whole seconds left inside. Redrawn on every frame
   * only while a message waits and the bar is on screen; nothing is allocated per frame.
   */
  private final class Ring extends View {
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG), trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint digitsPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    private String digits = "", previousDigits = "";
    private float digitsWidth, previousDigitsWidth;
    private int seconds = -1;
    private long rollStartUptime;

    Ring (Context context) {
      super(context);
      arcPaint.setStyle(Paint.Style.STROKE);
      arcPaint.setStrokeCap(Paint.Cap.ROUND);
      trackPaint.setStyle(Paint.Style.STROKE);
      if (m3) {
        digitsPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, false));
        digitsPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M)));
      } else {
        digitsPaint.setTypeface(Fonts.getRobotoMedium());
        digitsPaint.setTextSize(Screen.dp(11f));
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        digitsPaint.setFontFeatureSettings("tnum");
      }
    }

    void setSeconds (int value) {
      if (this.seconds == value) {
        return;
      }
      boolean roll = m3 && this.seconds > 0 && value > 0 && !XgramMotion.reduce();
      this.seconds = value;
      previousDigits = roll ? digits : "";
      previousDigitsWidth = roll ? digitsWidth : 0f;
      digits = value > 0 ? Integer.toString(value) : "";
      digitsWidth = digits.isEmpty() ? 0f : digitsPaint.measureText(digits);
      rollStartUptime = roll ? SystemClock.uptimeMillis() : 0L;
      invalidate();
    }

    @Override
    protected void onDraw (Canvas c) {
      XgramUndoSend.Shown s = shown;
      if (s == null) {
        return;
      }
      final float cx = getWidth() / 2f, cy = getHeight() / 2f;
      final long now = SystemClock.uptimeMillis();
      final boolean held = !s.flushing;
      final float progress = held && s.totalMs > 0 ? MathUtils.clamp((s.dueUptime - now) / (float) s.totalMs) : 0f;
      if (m3) {
        final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
        final float stroke = Screen.dpf(XgramM3Content.WAVY_STROKE_DP);
        final float amplitude = Screen.dpf(XgramM3Content.WAVY_AMPLITUDE_DP);
        final float radius = Math.min(getWidth(), getHeight()) / 2f - stroke / 2f - amplitude - Screen.dpf(.5f);
        final int track = XgramExpressive.withAlpha(primary, .24f);
        if (held) {
          // The arc keeps its end at 12 o'clock: its start moves clockwise as the time runs out.
          c.save();
          c.rotate(360f * (1f - progress), cx, cy);
          XgramWavyProgress.drawCircular(c, cx, cy, radius, stroke, progress, primary, track, XgramMotion.reduce() ? 0f : amplitude);
          c.restore();
          drawDigits(c, cx, cy, primary, now);
        } else {
          XgramWavyProgress.drawCircularIndeterminate(c, cx, cy, radius, stroke, primary, track, now - flushStartUptime);
        }
      } else {
        final int color = Theme.getColor(ColorId.progress);
        final float stroke = Screen.dp(2f);
        final float radius = Math.min(getWidth(), getHeight()) / 2f - stroke;
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(XgramExpressive.withAlpha(color, .25f));
        c.drawCircle(cx, cy, radius, trackPaint);
        if (held && progress > 0f) {
          arcPaint.setStrokeWidth(stroke);
          arcPaint.setColor(color);
          rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
          c.drawArc(rect, -90f + 360f * (1f - progress), 360f * progress, false, arcPaint);
        }
        if (held) {
          drawDigits(c, cx, cy, Theme.getColor(ColorId.textLight), now);
        }
      }
      // Expressive design: the wave travels (and the ring spins while sending); otherwise the arc only drains.
      if ((m3 || held) && isShown()) {
        postInvalidateOnAnimation();
      }
    }

    /** The seconds; a new value rolls in from below on the SPATIAL_FAST spring (as XgramCounterAnimator's digits do). */
    private void drawDigits (Canvas c, float cx, float cy, int color, long now) {
      if (digits.isEmpty()) {
        return;
      }
      final float baseline = cy - (digitsPaint.ascent() + digitsPaint.descent()) / 2f;
      final int alpha = (color >>> 24) & 0xff;
      float f = 1f;
      if (rollStartUptime != 0L) {
        long duration = XgramAppMotion.springDuration(XgramExpressive.SPATIAL_FAST);
        float t = Math.min(1f, (now - rollStartUptime) / (float) Math.max(1L, duration));
        f = XgramAppMotion.spring(XgramExpressive.SPATIAL_FAST).getInterpolation(t);
        if (t >= 1f) {
          rollStartUptime = 0L;
          f = 1f;
        }
      }
      final float shift = (digitsPaint.descent() - digitsPaint.ascent()) * .8f;
      digitsPaint.setColor(color);
      if (f < 1f && !previousDigits.isEmpty()) {
        digitsPaint.setAlpha(Math.round(alpha * MathUtils.clamp(1f - f)));
        c.drawText(previousDigits, cx - previousDigitsWidth / 2f, baseline - shift * f, digitsPaint);
      }
      digitsPaint.setAlpha(Math.round(alpha * MathUtils.clamp(f)));
      c.drawText(digits, cx - digitsWidth / 2f, baseline + shift * (1f - f), digitsPaint);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    removeCallbacks(tick);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (shown != null) {
      updateStatus();
      scheduleTick();
      ring.invalidate();
    }
  }

  @Override
  public boolean onTouchEvent (@NonNull android.view.MotionEvent event) {
    // The bar body has no action, but taps on it must not reach the messages under it.
    return visible.getFloatValue() > 0f || super.onTouchEvent(event);
  }
}
