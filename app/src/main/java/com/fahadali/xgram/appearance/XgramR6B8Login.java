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
package com.fahadali.xgram.appearance;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.core.ColorUtils;

/**
 * XGram M3E round 6, batch 8: login, account security and form fields. The login column (at most 480dp, centred:
 * an optional 96dp hero on a Cookie9Sided shape, a headlineMedium title that hands over to the small app bar title as
 * it scrolls away, bodyMedium supporting text), the segmented code input (one box per digit), the supporting text line
 * of a filled field, the spring shake of a wrong entry, and the connection pill of the login screens.
 * Everything here is only built while {@link XgramExpressive#isEnabled()}; colours are roles read when drawn, so light,
 * dark and Pure black all apply to views already built. Main thread only.
 */
public final class XgramR6B8Login {
  private XgramR6B8Login () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Login column

  public static final float COLUMN_MAX_WIDTH = XgramR5People.FORM_MAX_WIDTH;
  public static final float HERO_SIZE = 96f, HERO_ICON = 40f;

  /** A vertical column of at most 480dp (centred by {@link #wrapInScroll}), 16dp sides, 24dp above, room for the FAB below. */
  public static LinearLayout newColumn (@NonNull Context context) {
    LinearLayout column = new LinearLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(XgramR5People.capWidthSpec(widthMeasureSpec, COLUMN_MAX_WIDTH), heightMeasureSpec);
      }
    };
    column.setOrientation(LinearLayout.VERTICAL);
    column.setPadding(Screen.dp(16f), Screen.dp(24f), Screen.dp(16f), Screen.dp(104f));
    return column;
  }

  /** {@code column} centred at the top of a vertical scroll view that fills its parent. */
  public static ScrollView wrapInScroll (@NonNull Context context, @NonNull View column) {
    ScrollView scroll = new ScrollView(context);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setClipToPadding(false);
    scroll.setFillViewport(true);
    FrameLayout wrap = new FrameLayout(context);
    scroll.addView(wrap, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    wrap.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS); // the Android 12+ stretch
    }
    return scroll;
  }

  /** The hero of a login or security page: {@code iconRes} (through the icon pack) on a 96dp Cookie9Sided in primaryContainer. */
  public static XgramR5Security.HeroView newHero (@NonNull Context context, @DrawableRes int iconRes) {
    XgramR5Security.HeroView hero = new XgramR5Security.HeroView(context, XgramShapes.COOKIE_9, iconRes, HERO_ICON);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Screen.dp(HERO_SIZE), Screen.dp(HERO_SIZE));
    params.gravity = Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT;
    params.bottomMargin = Screen.dp(24f);
    hero.setLayoutParams(params);
    return hero;
  }

  /** The page headline: headlineMedium in onSurface, start aligned. */
  public static TextView newHeadline (@NonNull Context context) {
    return newHeadline(context, XgramExpressive.TS_HEADLINE_M);
  }

  public static TextView newHeadline (@NonNull Context context, int textStyle) {
    XgramR5Lists.RoleText headline = new XgramR5Lists.RoleText(context, textStyle, XgramExpressive.ON_SURFACE);
    headline.setGravity(Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
    headline.setLineSpacing(Screen.dp(XgramExpressive.lineHeightSp(textStyle) - XgramExpressive.textSizeSp(textStyle)), 1f);
    return headline;
  }

  /** Supporting text of a page: bodyMedium in onSurfaceVariant (links in primary), start aligned. */
  public static TextView newSupporting (@NonNull Context context) {
    XgramR5Lists.RoleText text = new XgramR5Lists.RoleText(context, XgramExpressive.TS_BODY_M, XgramExpressive.ON_SURFACE_VARIANT) {
      @Override
      protected void onDraw (Canvas c) {
        final int link = XgramExpressive.color(XgramExpressive.PRIMARY);
        if (getLinkTextColors() == null || getLinkTextColors().getDefaultColor() != link) {
          setLinkTextColor(link);
        }
        super.onDraw(c);
      }
    };
    styleSupporting(text);
    return text;
  }

  /** Gives an existing text view the page supporting text style (bodyMedium, start aligned, M3 line height). */
  public static void styleSupporting (@NonNull TextView view) {
    XgramExpressive.applyText(view, XgramExpressive.TS_BODY_M, false);
    view.setLineSpacing(Screen.dp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_M) - XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)), 1f);
    view.setGravity(Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
  }

  /**
   * The small app bar title of {@code owner} is the headline's: hidden while the headline shows, fading in over 24dp
   * of scroll once it has scrolled away (as the large title of the settings lists does).
   */
  public static void linkTitle (@NonNull ViewController<?> owner, @NonNull ScrollView scroll, @NonNull View headline) {
    HeaderView.xgramSetTitleAlpha(owner, 0f);
    scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
      HeaderView.xgramSetTitleAlpha(owner, (scrollY - headline.getBottom() + Screen.dp(16f)) / (float) Screen.dp(24f))
    );
  }

  /**
   * Keeps a list's rows inside a centred 480dp column on wide screens (the inner Fold screen): its side padding grows
   * with its width. One layout when the width changes, nothing per frame.
   */
  public static void centerColumn (@NonNull View list) {
    list.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      final int width = right - left;
      if (width <= 0 || width == oldRight - oldLeft) {
        return;
      }
      final int pad = Math.max(0, (width - Screen.dp(COLUMN_MAX_WIDTH)) / 2);
      if (v.getPaddingLeft() != pad || v.getPaddingRight() != pad) {
        v.post(() -> v.setPadding(pad, v.getPaddingTop(), pad, v.getPaddingBottom()));
      }
    });
  }

  /** A view popping in on SPATIAL_DEFAULT (scale .6 to 1 with a fade), a fade only with Reduce motion. */
  public static void popIn (@NonNull View view) {
    if (XgramMotion.reduce()) {
      view.setAlpha(0f);
      final XgramExpressive.Spring alpha = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> view.setAlpha(clamp(value)));
      view.post(() -> alpha.animateTo(1f));
      return;
    }
    view.setAlpha(0f);
    view.setScaleX(.6f);
    view.setScaleY(.6f);
    final XgramMotion.Value scale = new XgramMotion.Value(XgramExpressive.SPATIAL_DEFAULT, .6f, (v, value, velocity) -> {
      view.setScaleX(value);
      view.setScaleY(value);
    });
    final XgramMotion.Value alpha = new XgramMotion.Value(XgramExpressive.EFFECTS_DEFAULT, 0f, (v, value, velocity) -> view.setAlpha(clamp(value)));
    view.post(() -> {
      scale.animateTo(1f);
      alpha.animateTo(1f);
    });
  }

  // Shake

  /**
   * A wrong entry: the target shakes sideways on SPATIAL_FAST legs (10, -8, 5, -2, 0 dp), on its translation only.
   * Nothing with Reduce motion. One spring per target, built once.
   */
  public static final class Shake {
    private static final float[] STEPS = {10f, -8f, 5f, -2f, 0f};
    private final XgramMotion.Value value;
    private int step = STEPS.length - 1;

    public Shake (@NonNull View target) {
      this(target::setTranslationX);
    }

    public interface Target {
      void onShake (float dx);
    }

    public Shake (@NonNull Target target) {
      this.value = new XgramMotion.Value(XgramExpressive.SPATIAL_FAST, 0f, (v, x, velocity) -> {
        target.onShake(x);
        if (step < STEPS.length - 1 && Math.abs(x - Screen.dpf(STEPS[step])) < Screen.dpf(2f)) {
          step++;
          v.animateTo(Screen.dpf(STEPS[step]));
        }
      });
      this.value.setThreshold(.5f);
    }

    public void play (@Nullable View hapticView) {
      if (hapticView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        hapticView.performHapticFeedback(HapticFeedbackConstants.REJECT);
      }
      if (XgramMotion.reduce()) {
        return;
      }
      step = 0;
      value.setSpring(XgramExpressive.SPATIAL_FAST);
      value.animateTo(Screen.dpf(STEPS[0]));
    }
  }

  // Supporting text of a field

  /**
   * The supporting text line of a filled field (or of the code boxes): bodySmall, onSurfaceVariant or error, with
   * links in primary. It fades in and out and changes colour on EFFECTS_FAST; while hidden it is GONE.
   */
  public static final class SupportingText extends NoScrollTextView {
    private final XgramExpressive.Spring errorSpring, alphaSpring;
    private boolean shown;

    public SupportingText (@NonNull Context context) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_BODY_S, false);
      setLineSpacing(Screen.dp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_S) - XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_S)), 1f);
      setIncludeFontPadding(false);
      setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      setLinkTextColor(XgramExpressive.color(XgramExpressive.PRIMARY));
      errorSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
      alphaSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> setAlpha(clamp(value)));
      alphaSpring.setEndListener(() -> {
        if (!shown && getVisibility() != View.GONE) {
          setVisibility(View.GONE);
        }
      });
      setAlpha(0f);
      setVisibility(View.GONE);
      setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    public boolean isError () {
      return shown && errorSpring.getTarget() == 1f;
    }

    /** {@code text} (null or empty hides the line), in error or not. */
    public void set (@Nullable CharSequence text, boolean isError, boolean animated) {
      final boolean show = !TextUtils.isEmpty(text);
      if (show) {
        setText(text);
        final boolean links = text instanceof Spanned && ((Spanned) text).getSpans(0, text.length(), ClickableSpan.class).length > 0;
        if (links) {
          if (!(getMovementMethod() instanceof LinkMovementMethod)) {
            setMovementMethod(LinkMovementMethod.getInstance());
          }
        } else if (getMovementMethod() != null) {
          setMovementMethod(null);
          setFocusable(false);
          setClickable(false);
          setLongClickable(false);
        }
      }
      shown = show;
      if (!animated) {
        errorSpring.setValue(isError && show ? 1f : 0f);
        alphaSpring.setValue(show ? 1f : 0f);
        setAlpha(show ? 1f : 0f);
        setVisibility(show ? View.VISIBLE : View.GONE);
        invalidate();
        return;
      }
      if (show) {
        if (getVisibility() != View.VISIBLE) {
          setVisibility(View.VISIBLE);
        }
        errorSpring.animateTo(isError ? 1f : 0f);
        alphaSpring.animateTo(1f);
      } else if (alphaSpring.getValue() <= 0f && !alphaSpring.isRunning()) {
        setVisibility(View.GONE);
      } else {
        alphaSpring.animateTo(0f);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final float f = clamp(errorSpring.getValue());
      final int rest = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      final int color = f <= 0f ? rest : ColorUtils.fromToArgb(rest, XgramExpressive.color(XgramExpressive.ERROR), f);
      if (getCurrentTextColor() != color) {
        setTextColor(color);
      }
      final int link = XgramExpressive.color(XgramExpressive.PRIMARY);
      if (getLinkTextColors() == null || getLinkTextColors().getDefaultColor() != link) {
        setLinkTextColor(link);
      }
      super.onDraw(c);
    }
  }

  // Segmented code input

  public static final float BOX_WIDTH = 48f, BOX_HEIGHT = 56f, BOX_GAP = 8f;
  public static final int MIN_BOXES = 3, MAX_BOXES = 8;

  /**
   * The code entry of the login and confirmation pages: one 48 x 56dp box per digit (surfaceContainerHighest, CORNER_M,
   * narrower when they don't fit), the digits in headlineSmall with tabular figures, a 2dp primary indicator around the
   * box being typed in that slides between boxes on SPATIAL_FAST, and the supporting text line below. The input stays
   * the page's own (hidden) field: typing moves to the next box by itself, backspace goes back one, a code pasted from
   * the keyboard's clipboard or SMS suggestion fills every box, and a long press on the boxes pastes the digits of the
   * clipboard. A wrong code turns every box to error and shakes them.
   */
  public static final class CodeInput extends LinearLayout implements TextWatcher, MaterialEditTextGroup.FocusListener {
    private final MaterialEditTextGroup field;
    private final Boxes boxes;
    private final SupportingText supporting;
    private final InputFilter digitsOnly = (source, start, end, dest, dstart, dend) -> {
      boolean clean = true;
      for (int i = start; i < end; i++) {
        final char ch = source.charAt(i);
        if (ch < '0' || ch > '9') {
          clean = false;
          break;
        }
      }
      if (clean) {
        return null;
      }
      StringBuilder b = new StringBuilder(end - start);
      for (int i = start; i < end; i++) {
        final char ch = source.charAt(i);
        if (ch >= '0' && ch <= '9') {
          b.append(ch);
        }
      }
      return b;
    };
    private @Nullable InputFilter[] originalFilters;
    private int count;

    public CodeInput (@NonNull Context context, @NonNull MaterialEditTextGroup field) {
      super(context);
      setOrientation(LinearLayout.VERTICAL);
      this.field = field;
      this.boxes = new Boxes(context);
      addView(boxes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(BOX_HEIGHT)));
      this.supporting = new SupportingText(context);
      supporting.setGravity(Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.topMargin = Screen.dp(8f);
      addView(supporting, params);
      field.getEditText().addTextChangedListener(this);
      field.setFocusListener(this);
      setVisibility(View.GONE);
    }

    public int getCount () {
      return count;
    }

    public boolean isActive () {
      return count > 0;
    }

    /** {@code count} boxes; 0 goes back to the plain field (word codes, flash calls). */
    public void setCount (int count) {
      count = count <= 0 ? 0 : Math.max(MIN_BOXES, Math.min(MAX_BOXES, count));
      if (this.count == count) {
        return;
      }
      final boolean wasActive = this.count > 0;
      this.count = count;
      final android.widget.EditText input = field.getEditText();
      if (count > 0) {
        if (!wasActive) {
          originalFilters = input.getFilters();
        }
        InputFilter[] base = originalFilters != null ? originalFilters : new InputFilter[0];
        InputFilter[] filters = new InputFilter[base.length + 2];
        System.arraycopy(base, 0, filters, 0, base.length);
        filters[base.length] = digitsOnly;
        filters[base.length + 1] = new InputFilter.LengthFilter(count);
        input.setFilters(filters);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          input.setAutofillHints("smsOTPCode");
        }
        field.setAlpha(0f);
        setVisibility(View.VISIBLE);
        boxes.setContentDescription(Lang.getString(R.string.XgramR6B8CodeBoxes, count));
      } else {
        if (originalFilters != null) {
          input.setFilters(originalFilters);
          originalFilters = null;
        }
        supporting.set(null, false, false);
        boxes.setError(false);
        field.setAlpha(1f);
        setVisibility(View.GONE);
      }
      boxes.sync(false);
      boxes.invalidate();
    }

    /** The line under the boxes; an error also turns the boxes to error and shakes them. */
    public void setSupporting (@Nullable CharSequence text, boolean isError) {
      final boolean error = isError && !TextUtils.isEmpty(text);
      supporting.set(text, error, isLaidOut());
      boxes.setError(error);
      if (error) {
        boxes.shake.play(boxes);
      }
    }

    public boolean hasError () {
      return boxes.error;
    }

    /** Focuses the hidden input and opens the keyboard on it. */
    public void focusInput () {
      final android.widget.EditText input = field.getEditText();
      input.requestFocus();
      input.setSelection(input.length());
      Keyboard.show(input);
    }

    // TextWatcher

    @Override
    public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

    @Override
    public void onTextChanged (CharSequence s, int start, int before, int count) { }

    @Override
    public void afterTextChanged (Editable s) {
      boxes.sync(true);
    }

    // Focus

    @Override
    public void onFocusChanged (MaterialEditTextGroup v, boolean isFocused) {
      boxes.focus.animateTo(isFocused ? 1f : 0f);
    }

    private final class Boxes extends View {
      private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      private final RectF rect = new RectF();
      private final float textCenterOffset;
      final XgramExpressive.Spring focus, errorSpring, indicator, pop;
      final Shake shake;
      private float shakeX;
      private String text = "";
      private int popIndex = -1;
      boolean error;

      Boxes (Context context) {
        super(context);
        containerPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Screen.dpf(2f));
        textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_HEADLINE_S, false));
        textPaint.setTextSize(Screen.dpf(XgramExpressive.textSizeSp(XgramExpressive.TS_HEADLINE_S)));
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          textPaint.setFontFeatureSettings("tnum");
        }
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        textCenterOffset = -(fm.ascent + fm.descent) / 2f;
        focus = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
        errorSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
        indicator = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
        indicator.setThreshold(.002f);
        pop = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 1f, (s, value) -> invalidate());
        shake = new Shake(dx -> {
          shakeX = dx;
          invalidate();
        });
        setClickable(true);
        setOnClickListener(v -> focusInput());
        setOnLongClickListener(v -> pasteFromClipboard());
      }

      void setError (boolean error) {
        if (this.error != error) {
          this.error = error;
          errorSpring.animateTo(error ? 1f : 0f);
        }
      }

      void sync (boolean animated) {
        final CharSequence source = field.getEditText().getText();
        final String now = source != null ? source.toString() : "";
        final int previous = text.length();
        if (!now.equals(text)) {
          text = now;
        }
        final int length = text.length();
        final float target = count > 0 ? Math.min(length, count - 1) : 0f;
        if (animated && isLaidOut()) {
          indicator.animateTo(target);
          if (length > previous && length > 0) {
            popIndex = length - 1;
            pop.setValue(0f);
            pop.animateTo(1f);
          }
        } else {
          indicator.setValue(target);
          pop.setValue(1f);
        }
        invalidate();
      }

      private boolean pasteFromClipboard () {
        try {
          ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
          if (clip == null || clip.getItemCount() == 0) {
            return false;
          }
          CharSequence value = clip.getItemAt(0).coerceToText(getContext());
          String digits = value != null ? Strings.getNumber(value.toString()) : null;
          if (TextUtils.isEmpty(digits)) {
            return false;
          }
          if (count > 0 && digits.length() > count) {
            digits = digits.substring(0, count);
          }
          performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
          field.getEditText().requestFocus();
          field.setText(digits);
          return true;
        } catch (Throwable ignored) {
          return false;
        }
      }

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(Screen.dp(BOX_WIDTH) * Math.max(1, count), widthMeasureSpec), Screen.dp(BOX_HEIGHT));
      }

      @Override
      protected void onDraw (Canvas c) {
        final int n = count;
        final int width = getWidth(), height = getHeight();
        if (n <= 0 || width <= 0) {
          return;
        }
        final float gap = Screen.dpf(BOX_GAP);
        final float boxW = Math.min(Screen.dpf(BOX_WIDTH), (width - gap * (n - 1)) / n);
        final float total = boxW * n + gap * (n - 1);
        final float startX = Lang.rtl() ? width - total : 0f; // the digits stay left to right, the block starts at the start
        final float radius = Math.min(Screen.dpf(XgramExpressive.CORNER_M), boxW / 2f);
        final float errorF = clamp(errorSpring.getValue());
        final float focusF = clamp(focus.getValue());
        final int container = mix(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), XgramExpressive.color(XgramExpressive.ERROR_CONTAINER), errorF);
        final int content = mix(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER), errorF);
        final int length = Math.min(text.length(), n);
        final int restore = c.save();
        if (shakeX != 0f) {
          c.translate(shakeX, 0f);
        }
        containerPaint.setColor(container);
        textPaint.setColor(content);
        final float half = Screen.dpf(1f);
        for (int i = 0; i < n; i++) {
          final float left = startX + i * (boxW + gap);
          rect.set(left, 0, left + boxW, height);
          c.drawRoundRect(rect, radius, radius, containerPaint);
          if (i < length) {
            final float cx = left + boxW / 2f, cy = height / 2f;
            final float p = i == popIndex ? pop.getValue() : 1f;
            if (p != 1f) {
              final float scale = .6f + .4f * p;
              final int save = c.save();
              c.scale(scale, scale, cx, cy);
              c.drawText(text, i, i + 1, cx, cy + textCenterOffset, textPaint);
              c.restoreToCount(save);
            } else {
              c.drawText(text, i, i + 1, cx, cy + textCenterOffset, textPaint);
            }
          }
          if (errorF > 0f) {
            strokePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ERROR), errorF));
            rect.inset(half, half);
            c.drawRoundRect(rect, radius - half, radius - half, strokePaint);
          }
        }
        if (focusF > 0f && errorF < 1f) {
          final float index = Math.max(0f, Math.min(n - 1, indicator.getValue()));
          final float left = startX + index * (boxW + gap);
          rect.set(left + half, half, left + boxW - half, height - half);
          strokePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), focusF * (1f - errorF)));
          c.drawRoundRect(rect, radius - half, radius - half, strokePaint);
        }
        c.restoreToCount(restore);
      }
    }
  }

  // Links under a form: a text button that turns into a filled tonal one when its action is ready

  /**
   * A text button (labelLarge in primary, 40dp pill state layer in a 48dp target) that can become a filled tonal
   * button (secondaryContainer with its label in onSecondaryContainer); the container and label colours cross over on
   * EFFECTS_DEFAULT. Colours are read when drawn.
   */
  public static final class LinkButton extends NoScrollTextView {
    private final XgramExpressive.MorphDrawable container;
    private final XgramExpressive.Spring tonal;

    public LinkButton (@NonNull Context context) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setGravity(Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
      setMinHeight(Screen.dp(48f));
      setMinimumHeight(Screen.dp(48f));
      setMinWidth(Screen.dp(48f));
      setMinimumWidth(Screen.dp(48f));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        setFontFeatureSettings("tnum"); // countdowns keep their width
      }
      container = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
      container.setHost(this);
      Drawable inset = new InsetDrawable(container, 0, Screen.dp(4f), 0, Screen.dp(4f));
      Drawable mask = new InsetDrawable(container.newMask(), 0, Screen.dp(4f), 0, Screen.dp(4f));
      setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.PRIMARY, inset, mask));
      tonal = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> {
        container.invalidateSelf();
        invalidate();
      });
      setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    }

    public void setTonal (boolean isTonal, boolean animated) {
      final float target = isTonal ? 1f : 0f;
      if (tonal.getTarget() == target && !tonal.isRunning()) {
        return;
      }
      // a tonal button needs its own 16dp label padding; a text button 12dp
      setPadding(Screen.dp(isTonal ? 16f : 12f), getPaddingTop(), Screen.dp(isTonal ? 16f : 12f), getPaddingBottom());
      if (animated && isLaidOut()) {
        tonal.animateTo(target);
      } else {
        tonal.setValue(target);
        container.invalidateSelf();
        invalidate();
      }
    }

    public boolean isTonal () {
      return tonal.getTarget() == 1f;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      // before the background: the container and label colours of this frame (theme changes and the tonal spring)
      final float f = clamp(tonal.getValue());
      container.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), f));
      int text = mix(XgramExpressive.color(XgramExpressive.PRIMARY), XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), f);
      if (!isEnabled()) {
        text = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.DISABLED_CONTENT);
      }
      if (getCurrentTextColor() != text) {
        setTextColor(text);
      }
      super.draw(c);
    }
  }

  // Leading flag and trailing icon of a dropdown field

  /** The leading element of the country field: the country's flag emoji, or the globe icon while there is none. */
  public static final class LeadingFlag extends NoScrollTextView {
    private @Nullable Drawable globe;
    private int globeTint;

    public LeadingFlag (@NonNull Context context) {
      super(context);
      setGravity(Gravity.CENTER);
      setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20f);
      setIncludeFontPadding(false);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setFlag (@Nullable CharSequence flag) {
      setText(flag);
      invalidate();
    }

    @Override
    protected void onDraw (Canvas c) {
      if (TextUtils.isEmpty(getText())) {
        if (globe == null) {
          globe = Drawables.get(getResources(), XgramIconPack.map(R.drawable.baseline_public_24));
        }
        if (globe != null) {
          final int tint = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
          if (tint != globeTint) {
            globeTint = tint;
            globe.setTint(tint);
          }
          final int size = Screen.dp(24f);
          final int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
          globe.setBounds(left, top, left + size, top + size);
          globe.draw(c);
        }
        return;
      }
      super.onDraw(c);
    }
  }

  /** A 48dp standard icon button (40dp state layer) whose icon is tinted with {@code role} when drawn. */
  public static ImageView newIconButton (@NonNull Context context, @DrawableRes int iconRes, int role, @Nullable CharSequence description) {
    ImageView button = new ImageView(context) {
      private int tint;

      @Override
      protected void onDraw (Canvas c) {
        final int color = XgramExpressive.color(role);
        if (tint != color) {
          tint = color;
          setColorFilter(color);
        }
        super.onDraw(c);
      }
    };
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setImageResource(XgramIconPack.map(iconRes));
    XgramAppMotion.iconButton(button, role);
    button.setContentDescription(description);
    button.setMinimumWidth(Screen.dp(48f));
    button.setMinimumHeight(Screen.dp(48f));
    return button;
  }

  // Connection state on the login screens

  /**
   * The connection pill of a login screen (PROGRESS, OFFLINE, STUCK; the same debounce as the chat list) under the app
   * bar, following the screen's own account (an account being added is not the current one). While it is in, the
   * page content moves down by the pill's room on the pill's own spring (translation only).
   */
  public static final class ConnectionWatch implements XgramConnection.Listener {
    private final ViewController<?> owner;
    private final XgramConnection connection;
    private final XgramConnectionPill pill;
    private boolean destroyed;

    private ConnectionWatch (@NonNull ViewController<?> owner, @NonNull FrameLayout parent, @Nullable View content) {
      this.owner = owner;
      this.connection = XgramConnection.track(owner.tdlib());
      this.pill = XgramConnectionPill.attach(parent);
      if (content != null) {
        final int room = pill.getReservedHeight();
        pill.setOffsetListener(shown -> content.setTranslationY(room * shown));
      }
      connection.addListener(this);
      final int state = connection.getState();
      pill.setDisplay(XgramConnection.isProblem(state) ? state : XgramConnection.HIDDEN, connection.getText(), false);
    }

    /** Null with Expressive design off, or when the page has no tdlib. */
    @Nullable
    public static ConnectionWatch attach (@NonNull ViewController<?> owner, @NonNull FrameLayout parent, @Nullable View content) {
      if (!XgramExpressive.isEnabled() || owner.tdlib() == null) {
        return null;
      }
      return new ConnectionWatch(owner, parent, content);
    }

    @Override
    public void onConnectionDisplay (int state, CharSequence text, boolean animated) {
      if (!destroyed) {
        pill.setDisplay(state, text, animated && pill.isLaidOut());
      }
    }

    /**
     * The FAB of a login screen while there is no network: instead of acting it shows a tooltip, as a disabled
     * control would explain itself. True when the press was used up here.
     */
    public boolean blockWhileOffline (@Nullable View fab) {
      final Tdlib tdlib = owner.tdlib();
      if (destroyed || tdlib == null || !tdlib.context().watchDog().isOffline()) {
        return false;
      }
      if (fab != null) {
        owner.showInfoTooltip(fab, Lang.getString(R.string.XgramR6B8Offline));
      }
      return true;
    }

    public void destroy () {
      if (!destroyed) {
        destroyed = true;
        connection.removeListener(this);
        connection.release();
      }
    }
  }

  // Helpers

  static float clamp (float v) {
    return v < 0f ? 0f : v > 1f ? 1f : v;
  }

  static int mix (int from, int to, float f) {
    return f <= 0f ? from : f >= 1f ? to : ColorUtils.fromToArgb(from, to, f);
  }
}
