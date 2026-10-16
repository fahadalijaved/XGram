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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * M3E round 5, people and groups: the shared pieces of the people rows (BetterChatView, UserView), the contacts list's
 * letter groups, the trailing icon buttons drawn inside custom rows, the empty states of SettingsAdapter lists and
 * the sentence-case labels. Everything here is only used while {@link XgramExpressive#isEnabled()}.
 */
public final class XgramR5People {
  private XgramR5People () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // M3 list item (Compose ListTokens): 16dp padding, a 40dp leading avatar, the text from 72dp

  public static final float PADDING = 16f, AVATAR = 40f, TEXT_START = 72f, ROW_HEIGHT = 72f;

  // Sentence case

  /**
   * {@code tgxRes} in sentence case: {@code sentenceRes} while Telegram X's string is still the bundled English one
   * (a language pack's translation is kept as it is), the Telegram X string otherwise or with Expressive design off.
   */
  @NonNull
  public static String sentence (@StringRes int tgxRes, @StringRes int sentenceRes) {
    final String tgx = Lang.getString(tgxRes);
    if (!on()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return AppContext.get().getString(sentenceRes);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  // Row colors

  /**
   * The color a row's background draws (a segmented tile's surfaceBright, the plain fill outside a group), for the
   * rings cut out of avatars (check marks, the online dot).
   */
  @ColorInt
  public static int rowColor (@NonNull View view) {
    XgramExpressive.MorphDrawable morph = XgramExpressive.findMorph(view.getBackground());
    if (morph != null) {
      return morph.getDrawColor();
    }
    return Theme.fillingColor();
  }

  // Type

  private static @Nullable TextPaint metaPaint, subheaderPaint;

  /** labelSmall with tabular figures (times, durations, counters); the color is set per call. */
  @NonNull
  public static TextPaint metaPaint (@ColorInt int color) {
    TextPaint paint = metaPaint;
    if (paint == null) {
      paint = newPaint(XgramExpressive.TS_LABEL_S);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        paint.setFontFeatureSettings("tnum");
      }
      metaPaint = paint;
    }
    paint.setColor(color);
    return paint;
  }

  /** titleSmall (the section subheader of a list) in the given color. */
  @NonNull
  public static TextPaint subheaderPaint (@ColorInt int color) {
    TextPaint paint = subheaderPaint;
    if (paint == null) {
      paint = newPaint(XgramExpressive.TS_TITLE_S);
      subheaderPaint = paint;
    }
    paint.setColor(color);
    return paint;
  }

  private static TextPaint newPaint (int ts) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    paint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(ts)));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      paint.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
    }
    return paint;
  }

  // Tiles of lists that are not SettingsAdapter lists (the contacts list)

  /**
   * A list tile background on {@code v}: surfaceBright with segment corners (set them with
   * {@link XgramExpressive.MorphDrawable#setSegment}), morphing to 16dp while pressed, under an onSurface state layer
   * at .10 in the same shape. Colors are read at draw time and on every press, so theme switches need no listener.
   */
  @NonNull
  public static XgramExpressive.MorphDrawable bindTile (@NonNull View v) {
    XgramExpressive.MorphDrawable morph = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
    morph.setColorRole(XgramExpressive.SURFACE_BRIGHT);
    morph.setSegment(XgramExpressive.SEGMENT_SINGLE);
    morph.setHost(v);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      v.setBackground(new StateRipple(XgramExpressive.ON_SURFACE, morph, morph.newMask()));
      v.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
    } else {
      v.setBackground(morph);
    }
    return morph;
  }

  /** A ripple in {@code contentRole} at the pressed state-layer opacity, re-read on every state change. */
  private static final class StateRipple extends RippleDrawable {
    private final int contentRole;
    private int color;
    private final boolean ready;

    StateRipple (int contentRole, @Nullable Drawable content, @Nullable Drawable mask) {
      super(ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(XgramExpressive.color(contentRole))), content, mask);
      this.contentRole = contentRole;
      this.color = XgramExpressive.color(contentRole);
      this.ready = true;
    }

    @Override
    protected boolean onStateChange (@NonNull int[] stateSet) {
      if (ready) {
        int now = XgramExpressive.color(contentRole);
        if (now != color) {
          color = now;
          setColor(ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(now)));
        }
      }
      return super.onStateChange(stateSet);
    }
  }

  // Icon buttons drawn by custom rows (the call-back button of a recent call)

  /**
   * A 40dp icon button a custom view draws itself, with its own 48dp hit area: filled tonal (secondaryContainer with
   * the icon in onSecondaryContainer) or standard (no container, the icon in {@code contentRole}). Pressed, its
   * corners spring from full to 8dp (SPATIAL_FAST) under a .10 state layer; the press gives a haptic tick.
   */
  public static final class RowIconButton {
    private final View host;
    private final boolean tonal;
    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final XgramExpressive.Spring press;
    private float centerX, centerY;
    private boolean pressed, visible;

    public RowIconButton (@NonNull View host, boolean tonal) {
      this.host = host;
      this.tonal = tonal;
      this.press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> host.invalidate());
      this.press.setThreshold(.005f);
    }

    /** Centers the button on ({@code cx}, {@code cy}) in the host's coordinates. */
    public void layout (float cx, float cy) {
      this.centerX = cx;
      this.centerY = cy;
      this.visible = true;
    }

    public void hide () {
      this.visible = false;
      setPressed(false);
    }

    public boolean isVisible () {
      return visible;
    }

    /** The 48dp touch target around the 40dp button. */
    public boolean contains (float x, float y) {
      final float half = Screen.dp(XgramM3Sheets.TOUCH_TARGET) / 2f;
      return visible && x >= centerX - half && x <= centerX + half && y >= centerY - half && y <= centerY + half;
    }

    public void setPressed (boolean pressed) {
      if (this.pressed != pressed) {
        this.pressed = pressed;
        press.animateTo(pressed ? 1f : 0f);
        if (pressed) {
          host.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
      }
    }

    public void draw (@NonNull Canvas c, @Nullable Drawable icon, int contentRole) {
      if (!visible) {
        return;
      }
      final float half = Screen.dp(XgramM3Sheets.ICON_BUTTON_SIZE) / 2f;
      rect.set(centerX - half, centerY - half, centerX + half, centerY + half);
      final float radius = XgramExpressiveC.morphCorner(XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S, rect.width(), rect.height(), press.getValue());
      final int content = XgramExpressive.color(tonal ? XgramExpressive.ON_SECONDARY_CONTAINER : contentRole);
      if (tonal) {
        paint.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
        c.drawRoundRect(rect, radius, radius, paint);
      }
      final float state = Math.max(0f, Math.min(1f, press.getValue()));
      if (state > 0f) {
        paint.setColor(XgramExpressive.withAlpha(content, XgramExpressive.STATE_PRESSED * state));
        c.drawRoundRect(rect, radius, radius, paint);
      }
      if (icon != null) {
        Drawables.draw(c, icon, centerX - icon.getMinimumWidth() / 2f, centerY - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(content));
      }
    }
  }

  // Empty and loading rows of SettingsAdapter lists (the custom-row pattern of ForumTopicsController)

  /** The custom view type of the state row; use {@code ListItem.TYPE_CUSTOM - STATE_ROW}. */
  public static final int STATE_ROW = 9051;
  /** The custom view type of a loading row (the contained M3E loading indicator). */
  public static final int LOADING_ROW = 9052;
  /** The custom view type of a sheet's or page's hero button row ({@link #newHeroButton}). */
  public static final int HERO_ROW = 9053;
  /** The custom view type of a note card row ({@link #newNoteCard}). */
  public static final int NOTE_ROW = 9054;

  /**
   * A note in a card (a join request's terms): bodyMedium in onSurfaceVariant, 16dp padding, on a surfaceContainerHigh
   * card with 16dp corners (the card step under Pure black), 16dp from the sheet's sides. Set the text with
   * {@link #bindNoteCard}.
   */
  @NonNull
  public static View newNoteCard (@NonNull Context context) {
    FrameLayout frame = new FrameLayout(context);
    frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    android.widget.TextView text = new android.widget.TextView(context) {
      @Override
      protected void onDraw (Canvas c) {
        final int color = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
        if (getCurrentTextColor() != color) {
          setTextColor(color);
        }
        super.onDraw(c);
      }
    };
    XgramExpressive.applyText(text, XgramExpressive.TS_BODY_M, false);
    text.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    text.setLineSpacing(Screen.dp(XgramExpressive.lineHeightSp(XgramExpressive.TS_BODY_M) - XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)) / 2f, 1f);
    final int padding = Screen.dp(16f);
    text.setPadding(padding, padding, padding, padding);
    text.setId(android.R.id.text1);
    XgramExpressive.MorphDrawable card = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_L, Float.NaN);
    card.setFloatingColorRole(XgramExpressive.SURFACE_CONTAINER_HIGH);
    text.setBackground(card);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = params.rightMargin = Screen.dp(16f);
    params.topMargin = params.bottomMargin = Screen.dp(8f);
    frame.addView(text, params);
    return frame;
  }

  public static void bindNoteCard (@NonNull View card, @Nullable CharSequence text) {
    View view = card.findViewById(android.R.id.text1);
    if (view instanceof android.widget.TextView) {
      ((android.widget.TextView) view).setText(text);
    }
  }

  /** A state row: the M3E empty state or loading indicator, full height while alone in the list. */
  @NonNull
  public static XgramListStateView newStateView (@NonNull Context context) {
    XgramListStateView view = new XgramListStateView(context);
    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return view;
  }

  /**
   * Binds a state row made by {@link #newStateView}: the empty state when {@code iconRes} or {@code title} is set,
   * the contained loading indicator otherwise; as tall as the list while {@code alone}, 240dp otherwise.
   */
  public static void bindState (@NonNull View view, boolean alone, @DrawableRes int iconRes, @Nullable CharSequence title, @Nullable CharSequence text) {
    XgramListStateView stateView = view instanceof XgramListStateView ? (XgramListStateView) view : findState(view);
    if (stateView != null) {
      if (title != null) {
        stateView.setEmpty(iconRes, title, text);
      } else {
        stateView.setLoading(true, 38f);
      }
    }
    int height = alone ? ViewGroup.LayoutParams.MATCH_PARENT : Screen.dp(240f);
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params != null && params.height != height) {
      params.height = height;
      view.setLayoutParams(params);
    }
  }

  @Nullable
  private static XgramListStateView findState (View view) {
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View child = group.getChildAt(i);
        if (child instanceof XgramListStateView) {
          return (XgramListStateView) child;
        }
        XgramListStateView nested = findState(child);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  /**
   * An empty state with a filled tonal action under it ("Start a call"): the state view and a 40dp secondaryContainer
   * pill (labelLarge in onSecondaryContainer), centred together. The action is found again with {@link #actionOf}.
   */
  @NonNull
  public static LinearLayout newStateViewWithAction (@NonNull Context context, @NonNull CharSequence action, @NonNull View.OnClickListener onClick) {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER);
    layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    XgramListStateView stateView = new XgramListStateView(context);
    layout.addView(stateView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(236f)));
    XgramM3Sheets.FilledButton button = new XgramM3Sheets.FilledButton(context, XgramExpressive.SECONDARY_CONTAINER, XgramExpressive.ON_SECONDARY_CONTAINER);
    button.setText(action);
    button.setOnClickListener(onClick);
    button.setId(android.R.id.button1);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(XgramM3Sheets.BUTTON_HEIGHT));
    params.topMargin = Screen.dp(16f);
    params.bottomMargin = Screen.dp(24f);
    layout.addView(button, params);
    return layout;
  }

  @Nullable
  public static View actionOf (@NonNull View stateRow) {
    return stateRow.findViewById(android.R.id.button1);
  }

  // Centred content columns (forms 480dp, hero buttons 400dp)

  public static final float FORM_MAX_WIDTH = 480f, HERO_BUTTON_MAX_WIDTH = 400f;

  /** {@code widthMeasureSpec} limited to {@code maxDp} (exactly that wide when it is capped). */
  public static int capWidthSpec (int widthMeasureSpec, float maxDp) {
    if (View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED) {
      final int max = Screen.dp(maxDp);
      if (View.MeasureSpec.getSize(widthMeasureSpec) > max) {
        return View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.EXACTLY);
      }
    }
    return widthMeasureSpec;
  }

  /** A full-width hero button (Join, Start messaging): a filled pill 48dp tall, at most 400dp wide, centred. */
  @NonNull
  public static FrameLayout newHeroButton (@NonNull Context context, @NonNull CharSequence text, int containerRole, int contentRole, @NonNull View.OnClickListener onClick) {
    FrameLayout frame = new FrameLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        // The pill fills the width less 24dp margins, up to 400dp, centred (the inner Fold screen, landscape)
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child != null && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
          int want = Math.max(0, Math.min(MeasureSpec.getSize(widthMeasureSpec) - Screen.dp(48f), Screen.dp(HERO_BUTTON_MAX_WIDTH)));
          ViewGroup.LayoutParams params = child.getLayoutParams();
          if (params != null && params.width != want) {
            params.width = want;
          }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    XgramM3Sheets.FilledButton button = new XgramM3Sheets.FilledButton(context, containerRole, contentRole);
    button.setText(text);
    button.setOnClickListener(onClick);
    button.setId(android.R.id.button1);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f), Gravity.CENTER);
    params.topMargin = params.bottomMargin = Screen.dp(12f);
    frame.addView(button, params);
    return frame;
  }
}
