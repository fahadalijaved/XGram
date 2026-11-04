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
import android.graphics.Rect;
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
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
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
  /** The room a login page keeps free under its field for the FAB: its 16dp margin, its 56dp and 8dp above it. */
  public static final float FAB_CLEARANCE = 80f;

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
  public static LoginScroll wrapInScroll (@NonNull Context context, @NonNull View column) {
    LoginScroll scroll = new LoginScroll(context);
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

  /**
   * The scroll of a login page. While its anchor (the field or the code boxes, with the line under them) has the focus,
   * the anchor and a room under it (the FAB's) stay on screen: when the keyboard opens, when a view in the anchor asks
   * to be shown (the input's cursor) and when the anchor grows (an error line). While content above it shrinks (the hero
   * folding, the connection pill leaving) the anchor keeps its place on screen; while it grows (the pill making room)
   * the page moves down as far as the anchor and that room still fit, and only the rest is scrolled. Without an anchor,
   * or while the focus is elsewhere, it is a plain ScrollView.
   */
  public static final class LoginScroll extends ScrollView {
    private @Nullable View anchor;
    private int clearance;
    private boolean pendingShow;
    private final Rect anchorRect = new Rect(), requestRect = new Rect();

    public LoginScroll (@NonNull Context context) {
      super(context);
    }

    /** {@code anchor} (a descendant), with {@code clearancePx} under it kept on screen as well. */
    public void setAnchor (@Nullable View anchor, int clearancePx) {
      this.anchor = anchor;
      this.clearance = clearancePx;
    }

    private boolean isInside (@Nullable View view, @NonNull View ancestor) {
      View v = view;
      while (v != null) {
        if (v == ancestor) {
          return true;
        }
        if (v == this) {
          return false;
        }
        final ViewParent parent = v.getParent();
        v = parent instanceof View ? (View) parent : null;
      }
      return false;
    }

    private boolean isAnchored () {
      final View anchor = this.anchor;
      return anchor != null && anchor.hasFocus() && isInside(anchor, this);
    }

    /** The anchor in content coordinates (the space of getScrollY), without the room under it. */
    private boolean getAnchorRect (@NonNull Rect rect) {
      final View anchor = this.anchor;
      if (anchor == null || getChildCount() == 0 || anchor.getHeight() <= 0 || !isInside(anchor, this)) {
        return false;
      }
      anchor.getDrawingRect(rect);
      offsetDescendantRectToMyCoords(anchor, rect);
      return true;
    }

    private int viewportHeight () {
      return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private int scrollRange () {
      return getChildCount() == 0 ? 0 : Math.max(0, getChildAt(0).getHeight() - viewportHeight());
    }

    /** The least scroll change that shows the anchor and the room under it (its top wins when both can't fit). */
    private int anchorDelta () {
      if (!getAnchorRect(anchorRect)) {
        return 0;
      }
      final int viewport = viewportHeight();
      final int scrollY = getScrollY();
      int target = scrollY;
      if (anchorRect.bottom + clearance > target + viewport) {
        target = anchorRect.bottom + clearance - viewport;
      }
      if (anchorRect.top < target) {
        target = anchorRect.top;
      }
      target = Math.max(0, Math.min(scrollRange(), target));
      return target - scrollY;
    }

    private boolean showAnchor (boolean smooth) {
      final int delta = anchorDelta();
      if (delta == 0) {
        return false;
      }
      if (smooth) {
        smoothScrollBy(0, delta);
      } else {
        scrollBy(0, delta);
      }
      return true;
    }

    @Override
    public void requestChildFocus (View child, View focused) {
      super.requestChildFocus(child, focused);
      if (anchor != null && isInside(focused, anchor)) {
        if (isLaidOut() && !isLayoutRequested()) {
          showAnchor(false);
        } else {
          pendingShow = true; // after the layout, as ScrollView scrolls to a focused child itself
        }
      }
    }

    @Override
    public boolean requestChildRectangleOnScreen (View child, Rect rectangle, boolean immediate) {
      if (isAnchored() && getAnchorRect(anchorRect)) {
        requestRect.set(rectangle);
        requestRect.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
        if (Rect.intersects(requestRect, anchorRect)) {
          // the input's cursor (or another part of the anchor): the whole anchor and the FAB's room under it instead
          return showAnchor(!immediate);
        }
      }
      return super.requestChildRectangleOnScreen(child, rectangle, immediate);
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      final boolean anchored = isAnchored();
      if (anchored) {
        setSmoothScrollingEnabled(false); // ScrollView's own scroll to the focused input: at once, so ours isn't undone
      }
      super.onSizeChanged(w, h, oldw, oldh);
      if (anchored) {
        setSmoothScrollingEnabled(true);
        showAnchor(false);
      }
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      // Where the anchor is before this layout (the children still have their old positions), while it is on screen
      boolean keep = false;
      int oldTop = 0, oldHeight = 0;
      if (isLaidOut() && isAnchored() && getAnchorRect(anchorRect)) {
        final int scrollY = getScrollY();
        if (anchorRect.bottom > scrollY && anchorRect.top < scrollY + viewportHeight()) {
          keep = true;
          oldTop = anchorRect.top;
          oldHeight = anchorRect.height();
        }
      }
      super.onLayout(changed, l, t, r, b);
      if (keep && getAnchorRect(anchorRect)) {
        final int moved = anchorRect.top - oldTop;
        if (moved != 0) {
          final int scrollY = getScrollY();
          int by = moved;
          if (moved > 0) {
            // Content above grew (the pill's room): it really moves the page down while the anchor and the FAB's room
            // still fit, so only the shortfall is scrolled, never more than it moved
            final int need = anchorRect.bottom + clearance - (scrollY + viewportHeight());
            by = Math.max(0, Math.min(moved, need));
          }
          if (by != 0) {
            scrollTo(getScrollX(), Math.max(0, Math.min(scrollRange(), scrollY + by)));
          }
        }
        if (anchorRect.height() > oldHeight) {
          showAnchor(true); // an error line appeared under the boxes
        }
      }
      if (pendingShow) {
        pendingShow = false;
        showAnchor(false);
      }
    }
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

  /** {@code hero} (a {@link #newHero}) in a {@link Collapse} of its own, so the page can fold it away when it has no room. */
  public static Collapse newHeroCollapse (@NonNull Context context, @NonNull View hero) {
    Collapse collapse = new Collapse(context);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Screen.dp(HERO_SIZE), Screen.dp(HERO_SIZE), Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    params.bottomMargin = Screen.dp(24f);
    collapse.addView(hero, params);
    return collapse;
  }

  /**
   * The hero rule shared by the login pages: the hero (96dp and 24dp under it) shows while it fits in {@code viewportPx}
   * with the content it sits above ({@code contentWithoutHeroPx}: down to the field and the FAB's room under it). 8dp
   * of hysteresis around {@code shownNow}, so a size on the edge can't flip it back and forth.
   */
  public static boolean heroFits (int viewportPx, int contentWithoutHeroPx, boolean shownNow) {
    final int needed = contentWithoutHeroPx + Screen.dp(HERO_SIZE + 24f);
    final int slack = Screen.dp(8f);
    return needed <= viewportPx + (shownNow ? slack : -slack);
  }

  /** The page headline: headlineMedium in onSurface, start aligned. */
  public static TextView newHeadline (@NonNull Context context) {
    return newHeadline(context, XgramExpressive.TS_HEADLINE_M);
  }

  public static TextView newHeadline (@NonNull Context context, int textStyle) {
    XgramR5Lists.RoleText headline = new XgramR5Lists.RoleText(context, textStyle, XgramExpressive.ON_SURFACE);
    headline.setGravity(Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
    applyLineHeight(headline, textStyle);
    return headline;
  }

  /** The type scale line height of {@code textStyle} (Android 9 and later; the font's own line spacing before). */
  public static void applyLineHeight (@NonNull TextView view, int textStyle) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      view.setLineHeight(Math.round(Screen.dpf(XgramExpressive.lineHeightSp(textStyle))));
    }
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
    applyLineHeight(view, XgramExpressive.TS_BODY_M);
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
      com.fahadali.xgram.XgramHaptics.reject(hapticView); // REJECT on Android 11+, a long press before
      if (XgramMotion.reduce()) {
        return;
      }
      step = 0;
      value.setSpring(XgramExpressive.SPATIAL_FAST);
      value.animateTo(Screen.dpf(STEPS[0]));
    }
  }

  // Supporting text of a field

  /** The colour of a supporting text line: onSurfaceVariant, primary with a leading check (good), or error. */
  public static final int TONE_NEUTRAL = 0, TONE_GOOD = 1, TONE_ERROR = 2;

  /**
   * The supporting text line of a filled field (or of the code boxes): bodySmall in onSurfaceVariant, primary with a
   * leading check (a good state: a free username) or error, links in primary. It fades in and out and changes colour
   * on EFFECTS_FAST; while hidden it is GONE.
   */
  public static final class SupportingText extends NoScrollTextView {
    private final XgramExpressive.Spring errorSpring, goodSpring, alphaSpring;
    private boolean shown;
    private int tone = TONE_NEUTRAL;
    private @Nullable Drawable check;
    private int checkTint;

    public SupportingText (@NonNull Context context) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_BODY_S, false);
      applyLineHeight(this, XgramExpressive.TS_BODY_S);
      setIncludeFontPadding(false);
      setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      setLinkTextColor(XgramExpressive.color(XgramExpressive.PRIMARY));
      setCompoundDrawablePadding(Screen.dp(4f));
      errorSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
      goodSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
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
      return shown && tone == TONE_ERROR;
    }

    public boolean isTextShown () {
      return shown;
    }

    /** {@code text} (null or empty hides the line), in error or not. */
    public void set (@Nullable CharSequence text, boolean isError, boolean animated) {
      set(text, isError ? TONE_ERROR : TONE_NEUTRAL, animated);
    }

    /** {@code text} (null or empty hides the line) in {@code tone} ({@link #TONE_NEUTRAL}, {@link #TONE_GOOD}, {@link #TONE_ERROR}). */
    public void set (@Nullable CharSequence text, int tone, boolean animated) {
      final boolean show = !TextUtils.isEmpty(text);
      final boolean isError = tone == TONE_ERROR;
      final boolean isGood = tone == TONE_GOOD;
      this.tone = show ? tone : TONE_NEUTRAL;
      setCheck(show && isGood);
      if (!animated) {
        goodSpring.setValue(show && isGood ? 1f : 0f);
      } else if (show) {
        goodSpring.animateTo(isGood ? 1f : 0f);
      }
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

    private void setCheck (boolean visible) {
      if (visible == (check != null)) {
        return;
      }
      if (visible) {
        Drawable icon = Drawables.get(getResources(), XgramIconPack.map(R.drawable.baseline_check_circle_24));
        if (icon != null) {
          icon = icon.mutate();
          final int size = Screen.dp(16f);
          icon.setBounds(0, 0, size, size);
          checkTint = 0;
        }
        check = icon;
      } else {
        check = null;
      }
      setCompoundDrawablesRelative(check, null, null, null);
    }

    @Override
    protected void onDraw (Canvas c) {
      final float error = clamp(errorSpring.getValue()), good = clamp(goodSpring.getValue());
      final int rest = mix(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), XgramExpressive.color(XgramExpressive.PRIMARY), good);
      final int color = mix(rest, XgramExpressive.color(XgramExpressive.ERROR), error);
      if (getCurrentTextColor() != color) {
        setTextColor(color);
      }
      if (check != null && checkTint != color) {
        checkTint = color;
        check.setTint(color);
      }
      final int link = XgramExpressive.color(XgramExpressive.PRIMARY);
      if (getLinkTextColors() == null || getLinkTextColors().getDefaultColor() != link) {
        setLinkTextColor(link);
      }
      super.onDraw(c);
    }
  }

  // Text that changes with a fade

  /**
   * A text in a type scale role whose changes fade through: the old text out and the new one in on EFFECTS_DEFAULT
   * (the step titles of the two-step verification setup). Snaps before its first layout; Reduce motion snaps too.
   */
  public static final class FadeText extends XgramR5Lists.RoleText {
    private final XgramExpressive.Spring fade;
    private @Nullable CharSequence pending;
    private boolean hasPending;

    public FadeText (@NonNull Context context, int textStyle, int role) {
      super(context, textStyle, role);
      setGravity(Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
      applyLineHeight(this, textStyle);
      fade = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 1f, (s, value) -> setAlpha(clamp(value)));
      fade.setEndListener(() -> {
        if (hasPending && fade.getTarget() == 0f) {
          hasPending = false;
          setText(pending);
          pending = null;
          fade.animateTo(1f);
        }
      });
    }

    public void setTextAnimated (@Nullable CharSequence text) {
      if (!isLaidOut() || getVisibility() != View.VISIBLE) {
        hasPending = false;
        pending = null;
        fade.setValue(1f);
        setAlpha(1f);
        setText(text);
        return;
      }
      if (!hasPending && TextUtils.equals(getText(), text)) {
        if (fade.getTarget() != 1f) {
          fade.animateTo(1f);
        }
        return;
      }
      pending = text;
      hasPending = true;
      if (!fade.isRunning() && fade.getValue() <= 0f) {
        hasPending = false;
        setText(pending);
        pending = null;
        fade.animateTo(1f);
      } else {
        fade.animateTo(0f);
      }
    }
  }

  // A header that folds away

  /**
   * Shows its child at a part of its height (the parent clips the rest) and fades with it, on SPATIAL_DEFAULT (clamped:
   * a height never overshoots): the hero and headline of the phone page fold away while the country list is open.
   * Reduce motion snaps. One layout per spring frame, only while it moves.
   */
  public static final class Collapse extends FrameLayout {
    public interface Listener {
      void onCollapseChanged (float expanded);
    }

    private final XgramExpressive.Spring spring;
    private @Nullable Listener listener;

    public Collapse (@NonNull Context context) {
      super(context);
      spring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 1f, (s, value) -> {
        requestLayout();
        apply();
      });
      spring.setThreshold(.002f);
    }

    public void setListener (@Nullable Listener listener) {
      this.listener = listener;
      if (listener != null) {
        listener.onCollapseChanged(expandedFactor());
      }
    }

    public float expandedFactor () {
      return clamp(spring.getValue());
    }

    public boolean isExpanded () {
      return spring.getTarget() == 1f;
    }

    public void setExpanded (boolean expanded, boolean animated) {
      final float target = expanded ? 1f : 0f;
      if (animated && isLaidOut()) {
        spring.animateTo(target);
      } else {
        spring.setValue(target);
        requestLayout();
        apply();
      }
    }

    private void apply () {
      final float f = expandedFactor();
      setAlpha(f);
      setImportantForAccessibility(f > 0f ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      if (listener != null) {
        listener.onCollapseChanged(f);
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      setMeasuredDimension(getMeasuredWidth(), Math.round(getMeasuredHeight() * expandedFactor()));
    }
  }

  // The trailing arrow of a dropdown field

  /** arrow_drop_down in onSurfaceVariant, turning to point up while the dropdown is open (SPATIAL_FAST). */
  public static final class DropdownArrow extends ImageView {
    private final XgramExpressive.Spring turn;
    private int tint;

    public DropdownArrow (@NonNull Context context) {
      super(context);
      setScaleType(ScaleType.CENTER);
      setImageResource(XgramIconPack.map(R.drawable.xgram_r6_b8_arrow_drop_down_24));
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      turn = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> setRotation(180f * value));
    }

    public void setExpanded (boolean expanded, boolean animated) {
      if (animated && isLaidOut()) {
        turn.animateTo(expanded ? 1f : 0f);
      } else {
        turn.setValue(expanded ? 1f : 0f);
        setRotation(expanded ? 180f : 0f);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      if (tint != color) {
        tint = color;
        setColorFilter(color);
      }
      super.onDraw(c);
    }
  }

  /** The prefix of a filled field ("+" before a calling code): bodyLarge in onSurfaceVariant, read when drawn. */
  public static TextView newPrefix (@NonNull Context context, @NonNull CharSequence text) {
    XgramR5Lists.RoleText prefix = new XgramR5Lists.RoleText(context, XgramExpressive.TS_BODY_L, XgramExpressive.ON_SURFACE_VARIANT);
    prefix.setText(text);
    prefix.setSingleLine(true);
    prefix.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return prefix;
  }

  // Heroes of the login and security pages

  /** The hero icon of a code page: an SMS bubble, a phone for a call, the chat bubble for a code in Telegram. */
  @DrawableRes
  public static int codeHeroIcon (@Nullable TdApi.AuthenticationCodeType type) {
    if (type != null) {
      switch (type.getConstructor()) {
        case TdApi.AuthenticationCodeTypeCall.CONSTRUCTOR:
        case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR:
        case TdApi.AuthenticationCodeTypeMissedCall.CONSTRUCTOR:
          return R.drawable.baseline_call_24;
        case TdApi.AuthenticationCodeTypeTelegramMessage.CONSTRUCTOR:
          return R.drawable.baseline_chat_bubble_24;
      }
    }
    return R.drawable.xgram_r6_b8_sms_24;
  }

  /**
   * How many boxes the code of {@code type} gets: 0 for a word or phrase (the plain field stays) and for a flash call
   * (TDLib checks the caller itself), else its length.
   */
  public static int codeBoxes (@Nullable TdApi.AuthorizationState state) {
    if (state == null) {
      return 0;
    }
    if (state.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
      TdApi.AuthenticationCodeInfo info = ((TdApi.AuthorizationStateWaitCode) state).codeInfo;
      if (info == null || info.type == null) {
        return 0;
      }
      switch (info.type.getConstructor()) {
        case TdApi.AuthenticationCodeTypeSmsWord.CONSTRUCTOR:
        case TdApi.AuthenticationCodeTypeSmsPhrase.CONSTRUCTOR:
        case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR:
          return 0;
      }
      return TD.codeLength(state, 0);
    }
    if (state.getConstructor() == TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR) {
      return TD.codeLength(state, 0);
    }
    return 0;
  }

  // Segmented code input

  /** The boxes fill the column: up to 56dp wide each (at least 32dp), 56dp tall, 8dp apart (4dp when 40dp ones don't fit). */
  public static final float BOX_MAX_WIDTH = 56f, BOX_MIN_WIDTH = 32f, BOX_HEIGHT = 56f, BOX_GAP = 8f, BOX_GAP_TIGHT = 4f;
  public static final int MIN_BOXES = 3, MAX_BOXES = 8;
  /** The caret of the next empty box: a 2 x 24dp full-round bar in primary, on for 500ms and off for 500ms. */
  private static final float CARET_WIDTH = 2f, CARET_HEIGHT = 24f;
  private static final long CARET_BLINK_MS = 500L;

  /**
   * The decimal digits of {@code text} in ASCII, nothing else: a keyboard in another script types its own digits
   * (Arabic-Indic, Persian, Devanagari, full-width), and TDLib wants 0-9.
   */
  @NonNull
  public static String asciiDigits (@Nullable CharSequence text) {
    if (text == null) {
      return "";
    }
    final int length = text.length();
    StringBuilder b = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      final char ch = text.charAt(i);
      if (ch >= '0' && ch <= '9') {
        b.append(ch);
      } else if (Character.isDigit(ch)) {
        final int digit = Character.digit(ch, 10);
        if (digit >= 0 && digit <= 9) {
          b.append((char) ('0' + digit));
        }
      }
    }
    return b.toString();
  }

  /**
   * The code entry of the login and confirmation pages: one box per digit that together fill the column (up to 56 x
   * 56dp each, surfaceContainerHighest, CORNER_M), the digits in headlineSmall onSurface with tabular figures, a 2dp
   * primary indicator around the box being typed in that slides between boxes on SPATIAL_FAST with a blinking caret in
   * it while it is empty, and the supporting text line below. The input stays the page's own (hidden) field: typing
   * moves to the next box by itself, backspace goes back one, a code pasted from the keyboard's clipboard or SMS
   * suggestion fills every box, and a long press on the boxes pastes the digits of the clipboard. Digits of any script
   * count. A wrong code turns every box to error and shakes them.
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
      // the digits of another script become the same ASCII digits (they used to be dropped: nothing showed up);
      // anything else is dropped
      return asciiDigits(source.subSequence(start, end));
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

    /** A theme change invalidates this view: the boxes and the line under them read their roles again. */
    @Override
    public void invalidate () {
      super.invalidate();
      if (boxes != null) {
        boxes.invalidate();
      }
      if (supporting != null) {
        supporting.invalidate();
      }
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
      boxes.setFocused(isFocused);
    }

    private final class Boxes extends View {
      private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
      private final RectF rect = new RectF();
      private final float textCenterOffset;
      final XgramExpressive.Spring focus, errorSpring, indicator, pop;
      final Shake shake;
      private float shakeX;
      private String text = ""; // the last synced text: what the pop and the indicator springs started from
      private int popIndex = -1;
      boolean error;
      private boolean focused, caretOn = true;
      private final Runnable blink = () -> {
        caretOn = !caretOn;
        invalidate();
        if (!scheduleBlink()) {
          caretOn = true;
        }
      };

      Boxes (Context context) {
        super(context);
        containerPaint.setStyle(Paint.Style.FILL);
        caretPaint.setStyle(Paint.Style.FILL);
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

      // The long click's own haptic (a paste: pasteFromClipboard returned true) follows Haptic feedback too, without
      // FLAG_IGNORE_GLOBAL_SETTING (XgramHaptics)
      @Override
      public boolean performHapticFeedback (int feedbackConstant, int flags) {
        return com.fahadali.xgram.XgramHaptics.enabled() && super.performHapticFeedback(feedbackConstant, com.fahadali.xgram.XgramHaptics.viewFlags(flags));
      }

      void setError (boolean error) {
        if (this.error != error) {
          this.error = error;
          errorSpring.animateTo(error ? 1f : 0f);
        }
      }

      void setFocused (boolean focused) {
        if (this.focused != focused) {
          this.focused = focused;
          restartCaret();
        }
      }

      // The caret: on again after every key and focus change, then blinking only while someone can see it (focused,
      // on screen, an empty box left, the window in front); steady with Reduce motion

      private boolean canBlink () {
        return focused && count > 0 && text.length() < count && isAttachedToWindow() && hasWindowFocus() && isShown() && !XgramMotion.reduce();
      }

      private boolean scheduleBlink () {
        removeCallbacks(blink);
        if (canBlink()) {
          postDelayed(blink, CARET_BLINK_MS);
          return true;
        }
        return false;
      }

      void restartCaret () {
        caretOn = true;
        scheduleBlink();
        invalidate();
      }

      @Override
      protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        restartCaret();
      }

      @Override
      protected void onDetachedFromWindow () {
        super.onDetachedFromWindow();
        removeCallbacks(blink);
        caretOn = true;
      }

      @Override
      public void onWindowFocusChanged (boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        restartCaret();
      }

      @Override
      protected void onVisibilityChanged (@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        restartCaret();
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
        restartCaret(); // invalidates
      }

      private boolean pasteFromClipboard () {
        try {
          ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
          if (clip == null || clip.getItemCount() == 0) {
            return false;
          }
          CharSequence value = clip.getItemAt(0).coerceToText(getContext());
          String digits = asciiDigits(value); // the digits of any script, as the input takes them
          if (TextUtils.isEmpty(digits)) {
            return false;
          }
          if (count > 0 && digits.length() > count) {
            digits = digits.substring(0, count);
          }
          // No haptic of its own: returning true makes the long click play LONG_PRESS (it played twice)
          field.getEditText().requestFocus();
          field.setText(digits);
          return true;
        } catch (Throwable ignored) {
          return false;
        }
      }

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // the column's width (the boxes share it); their widest when nothing limits it
        final int n = Math.max(1, count);
        final int natural = Math.round(Screen.dpf(BOX_MAX_WIDTH) * n + Screen.dpf(BOX_GAP) * (n - 1));
        setMeasuredDimension(getDefaultSize(natural, widthMeasureSpec), Screen.dp(BOX_HEIGHT));
      }

      @Override
      protected void onDraw (Canvas c) {
        final int n = count;
        final int width = getWidth(), height = getHeight();
        if (n <= 0 || width <= 0) {
          return;
        }
        // n boxes share the width with 8dp gaps (4dp when even 40dp boxes would not fit, 7 or 8 digits on a phone),
        // each at most 56dp wide
        float gap = Screen.dpf(BOX_GAP);
        if (Screen.dpf(40f) * n + gap * (n - 1) > width) {
          gap = Screen.dpf(BOX_GAP_TIGHT);
        }
        final float boxW = Math.max(Screen.dpf(BOX_MIN_WIDTH), Math.min(Screen.dpf(BOX_MAX_WIDTH), (width - gap * (n - 1)) / n));
        final float total = boxW * n + gap * (n - 1);
        final float startX = Lang.rtl() ? width - total : 0f; // the digits stay left to right, the block starts at the start
        final float radius = Math.min(Screen.dpf(XgramExpressive.CORNER_M), boxW / 2f);
        final float errorF = clamp(errorSpring.getValue());
        final float focusF = clamp(focus.getValue());
        final int container = mix(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), XgramExpressive.color(XgramExpressive.ERROR_CONTAINER), errorF);
        final int content = mix(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER), errorF);
        // the digits as the input holds them now (not a copy that a missed sync could leave behind)
        final CharSequence typed = field.getEditText().getText();
        final int length = typed != null ? Math.min(typed.length(), n) : 0;
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
              c.drawText(typed, i, i + 1, cx, cy + textCenterOffset, textPaint);
              c.restoreToCount(save);
            } else {
              c.drawText(typed, i, i + 1, cx, cy + textCenterOffset, textPaint);
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
          final int primary = XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), focusF * (1f - errorF));
          rect.set(left + half, half, left + boxW - half, height - half);
          strokePaint.setColor(primary);
          c.drawRoundRect(rect, radius - half, radius - half, strokePaint);
          if (caretOn && length < n) {
            // where the next digit goes: the caret in the middle of the box the indicator is on
            final float caretW = Screen.dpf(CARET_WIDTH);
            final float caretH = Math.min(Screen.dpf(CARET_HEIGHT), height - Screen.dpf(16f));
            final float cx = left + boxW / 2f;
            rect.set(cx - caretW / 2f, (height - caretH) / 2f, cx + caretW / 2f, (height + caretH) / 2f);
            caretPaint.setColor(primary);
            c.drawRoundRect(rect, caretW / 2f, caretW / 2f, caretPaint);
          }
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
    private final android.graphics.drawable.RippleDrawable stateLayer;

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
      // The state layer follows the label (draw): primary as a text button, onSecondaryContainer as a tonal one. A plain
      // RippleDrawable, so a theme change cannot reset it to primary behind draw()'s back (ThemedRipple would).
      stateLayer = new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(XgramExpressive.color(XgramExpressive.PRIMARY))), inset, mask);
      setBackground(stateLayer);
      tonal = new XgramExpressive.Spring(XgramExpressive.EFFECTS_DEFAULT, 0f, (s, value) -> {
        container.invalidateSelf();
        invalidate();
        applyShift();
      });
      setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
    }

    private float tonalShiftDp;

    /**
     * A text button whose label lines up with the column sits {@code dp} outside it (its state layer's padding); as a
     * tonal button its container lines up instead, so it moves in by {@code dp} with the colour.
     */
    public void setTonalShift (float dp) {
      this.tonalShiftDp = dp;
      applyShift();
    }

    private void applyShift () {
      final float shift = Screen.dpf(tonalShiftDp) * clamp(tonal.getValue());
      setTranslationX(Lang.rtl() ? -shift : shift);
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
        applyShift();
      }
    }

    public boolean isTonal () {
      return tonal.getTarget() == 1f;
    }

    /** The colour the state layer was last derived from. */
    private int lastStateBase;

    @Override
    public void draw (@NonNull Canvas c) {
      // before the background: the container and label colours of this frame (theme changes and the tonal spring)
      final float f = clamp(tonal.getValue());
      container.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), f));
      // M3: a text button's state layer is primary, a filled tonal button's onSecondaryContainer, as its label
      final int base = mix(XgramExpressive.color(XgramExpressive.PRIMARY), XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), f);
      if (base != lastStateBase) {
        lastStateBase = base;
        stateLayer.setColor(android.content.res.ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(base)));
      }
      int text = base;
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

  /**
   * The leading element of the country field (a 24dp box, an M3 leading icon's size): the country's flag emoji, or the globe
   * icon while there is none or while the flag's emoji page is still loading, so the slot is never empty. It listens
   * for the emoji set itself (a flag drawn before its page loaded stayed blank: only the list below was told).
   */
  public static final class LeadingFlag extends NoScrollTextView implements TGLegacyManager.EmojiLoadListener {
    private @Nullable Drawable globe;
    private int globeTint;
    private @Nullable String flag;

    public LeadingFlag (@NonNull Context context) {
      super(context);
      setGravity(Gravity.CENTER);
      // EmojiSpanImpl draws the flag as a square of the paint's descent - ascent, not of the text size: size the text so
      // that square is 24dp whatever the device font (px, so the font scale doesn't grow the icon either)
      final int box = Screen.dp(24f);
      setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, box);
      final Paint.FontMetricsInt fm = getPaint().getFontMetricsInt();
      final int line = Math.abs(fm.descent - fm.ascent);
      if (line > 0) {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (float) box * box / line);
      }
      setIncludeFontPadding(false);
      setSingleLine(true);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** {@code flag}: the flag emoji as text (Emoji.getEmojiFlagFromCountry), null or empty for the globe. */
    public void setFlag (@Nullable String flag) {
      this.flag = TextUtils.isEmpty(flag) ? null : flag;
      setText(this.flag != null ? Emoji.instance().replaceEmoji(this.flag) : null);
      invalidate();
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      TGLegacyManager.instance().addEmojiListener(this); // a weak reference: removed again when detached
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      TGLegacyManager.instance().removeEmojiListener(this);
    }

    @Override
    public void onEmojiUpdated (boolean isPackSwitch) {
      if (isPackSwitch && flag != null) {
        setText(Emoji.instance().replaceEmoji(flag)); // the new emoji set's flag
      }
      invalidate(); // a page loaded: the flag draws now
    }

    /** False while an emoji of the flag could not be drawn yet (its page is loading). */
    private boolean isFlagDrawn () {
      final CharSequence text = getText();
      if (text instanceof Spanned) {
        for (EmojiSpan span : ((Spanned) text).getSpans(0, text.length(), EmojiSpan.class)) {
          if (span.needRefresh()) {
            return false;
          }
        }
      }
      return true;
    }

    @Override
    protected void onDraw (Canvas c) {
      if (!TextUtils.isEmpty(getText())) {
        super.onDraw(c); // also asks for the flag's emoji page when it isn't loaded yet
        if (isFlagDrawn()) {
          return;
        }
      }
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
   * page makes room for it on the pill's own spring: the content moves down (translation only), or the page's own
   * {@link Room} (the code and password pages grow their column's top padding, so their scroll keeps the field above
   * the keyboard instead of pushing it under).
   */
  public static final class ConnectionWatch implements XgramConnection.Listener {
    /** A page's own way of making room for the pill: {@code room} px when it is fully in, times {@code shown} (0 to 1). */
    public interface Room {
      void onPillRoom (int room, float shown);
    }

    private final ViewController<?> owner;
    private final XgramConnection connection;
    private final XgramConnectionPill pill;
    private boolean destroyed;

    private ConnectionWatch (@NonNull ViewController<?> owner, @NonNull FrameLayout parent, @Nullable Room room) {
      this.owner = owner;
      this.connection = XgramConnection.track(owner.tdlib());
      this.pill = XgramConnectionPill.attach(parent);
      pill.setSource(connection);
      if (room != null) {
        final int reserved = pill.getReservedHeight();
        pill.setOffsetListener(shown -> room.onPillRoom(reserved, shown));
      }
      connection.addListener(this);
      final int state = connection.getState();
      pill.setDisplay(XgramConnection.isProblem(state) ? state : XgramConnection.HIDDEN, connection.getText(), false);
    }

    /** Null with Expressive design off, or when the page has no tdlib. {@code content} moves down while the pill is in. */
    @Nullable
    public static ConnectionWatch attach (@NonNull ViewController<?> owner, @NonNull FrameLayout parent, @Nullable View content) {
      if (!XgramExpressive.isEnabled() || owner.tdlib() == null) {
        return null;
      }
      return new ConnectionWatch(owner, parent, content != null ? (room, shown) -> content.setTranslationY(room * shown) : null);
    }

    /** Null with Expressive design off, or when the page has no tdlib. {@code room} makes the room for the pill. */
    @Nullable
    public static ConnectionWatch attach (@NonNull ViewController<?> owner, @NonNull FrameLayout parent, @NonNull Room room) {
      if (!XgramExpressive.isEnabled() || owner.tdlib() == null) {
        return null;
      }
      return new ConnectionWatch(owner, parent, room);
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
      if (destroyed || fab == null || fab.getAlpha() <= 0f || tdlib == null || !tdlib.context().watchDog().isOffline()) {
        return false; // no FAB on screen: the page's own network prompt
      }
      owner.showInfoTooltip(fab, Lang.getString(R.string.XgramR6B8Offline));
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

  // Terms of service at sign-up

  /**
   * The terms of service as a large M3 sheet instead of a long alert: the 28dp top and handle of the sheets, the title
   * in titleLarge, the text in bodyMedium (links in primary) in a scroll of at most 70% of the screen, and a sticky
   * actions row: [Back] ... [Agree] (filled). Back, the scrim or a drag only close the sheet; Agree closes it and runs
   * {@code onAgree}. Null when the page can't show popups now.
   */
  @Nullable
  public static org.thunderdog.challegram.widget.PopupLayout showTerms (@NonNull ViewController<?> c, @NonNull CharSequence text, @NonNull Runnable onAgree) {
    if (c.isDestroyed()) {
      return null;
    }
    final int maxScrollHeight = Math.round(Screen.currentHeight() * .7f);
    return c.showPopup(XgramR5Lists.sentence(R.string.TermsOfService, R.string.XgramR6B8Terms), true, org.thunderdog.challegram.util.text.Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      final Context context = optionsLayout.getContext();
      final int width = XgramM3Sheets.sheetWidth(c.context().getControllerWidth(optionsLayout));

      XgramR5Lists.RoleText body = new XgramR5Lists.RoleText(context, XgramExpressive.TS_BODY_M, XgramExpressive.ON_SURFACE) {
        @Override
        protected void onDraw (Canvas canvas) {
          final int link = XgramExpressive.color(XgramExpressive.PRIMARY);
          if (getLinkTextColors() == null || getLinkTextColors().getDefaultColor() != link) {
            setLinkTextColor(link);
          }
          super.onDraw(canvas);
        }
      };
      applyLineHeight(body, XgramExpressive.TS_BODY_M);
      body.setGravity(Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
      body.setText(text);
      body.setMovementMethod(LinkMovementMethod.getInstance());
      body.setHighlightColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), XgramExpressive.STATE_PRESSED));
      body.setPadding(Screen.dp(24f), Screen.dp(4f), Screen.dp(24f), Screen.dp(16f));

      ScrollView scroll = new ScrollView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          int maxHeight = maxScrollHeight;
          if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
          }
          super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        }
      };
      scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
      scroll.setVerticalScrollBarEnabled(false);
      scroll.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      optionsLayout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int height = Math.min(body.getMeasuredHeight(), maxScrollHeight);

      TextView back = XgramR5Sheets.textButton(context, Lang.getString(R.string.XgramR6B8Back), XgramExpressive.PRIMARY, null);
      back.setOnClickListener(v -> popupLayout.hideWindow(true));
      TextView agree = XgramR5Sheets.filledButton(context, Lang.getString(R.string.XgramR6B8Agree), false, null);
      agree.setOnClickListener(v -> {
        popupLayout.hideWindow(true);
        onAgree.run();
      });
      LinearLayout row = XgramR5Sheets.actionsRow(context, back, agree);
      optionsLayout.addView(row);
      height += ((LinearLayout.LayoutParams) row.getLayoutParams()).height;
      return height;
    }, null, XgramExpressive.TS_TITLE_L);
  }

  // Helpers

  static float clamp (float v) {
    return v < 0f ? 0f : v > 1f ? 1f : v;
  }

  static int mix (int from, int to, float f) {
    return f <= 0f ? from : f >= 1f ? to : ColorUtils.fromToArgb(from, to, f);
  }
}
