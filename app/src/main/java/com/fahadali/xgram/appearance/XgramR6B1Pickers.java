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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * XGram M3E round 6, batch 1: one choice from a short fixed list (the self-destruct timer, the secret chat timer, the
 * media viewer's timer, the notification repeat interval, the translation service) as an M3 single-choice list in the
 * M3 sheet, in place of Telegram X's wheel dialogs: a headlineSmall title, optional bodyMedium supporting text, the
 * choices as segmented surfaceBright tiles (20dp outer and 4dp inner corners, 2dp apart, 16dp from the sides, pressed
 * corners morphing to 16dp) each with an M3 radio button, the current one a secondaryContainer tile scrolled into view,
 * and [Cancel] [Done] at the end (or apply on tap). Colors are roles read at draw time, from the dark scheme of a forced
 * theme (the media viewer). Main thread only; used while {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramR6B1Pickers {
  private XgramR6B1Pickers () { }

  public interface ChoiceCallback {
    void onChoice (int index);
  }

  /** Row geometry (dp): a one-line list item with a leading radio button. */
  private static final float ROW_HEIGHT = 56f, ROW_PADDING = 16f, RADIO_SLOT = 24f, RADIO_TEXT_GAP = 16f;
  /** Share of the window the choices may take before they scroll. */
  private static final float MAX_LIST_SHARE = .55f;

  /**
   * The page an M3 sheet opened from {@code context} belongs to: the navigation's current page of the activity of
   * {@code context}, or of the activity on screen, or null when there is none (no sheet can be shown then).
   */
  @Nullable
  public static ViewController<?> host (@Nullable Context context) {
    BaseActivity activity = context != null ? UI.getContext(context) : null;
    if (activity == null) {
      activity = UI.getUiContext();
    }
    if (activity == null || activity.isFinishing()) {
      return null;
    }
    final NavigationController navigation = activity.navigation();
    final ViewController<?> c = navigation != null ? navigation.getCurrentStackItem() : null;
    return c != null && !c.isDestroyed() ? c : null;
  }

  /**
   * Shows the single-choice sheet from {@code host}. {@code checkedIndex} (or -1) is checked and scrolled into view;
   * with {@code applyOnTap} a tap picks and closes (only Cancel stays), otherwise a tap moves the check and Done picks.
   * {@code callback} gets the picked index (also when it is the current one). Returns false when nothing was shown.
   */
  public static boolean showChoiceSheet (@Nullable ViewController<?> host, @NonNull CharSequence title, @Nullable CharSequence text, @NonNull CharSequence[] items, int checkedIndex, boolean applyOnTap, @Nullable ThemeDelegate forcedTheme, @NonNull ChoiceCallback callback) {
    if (host == null || items.length == 0 || !XgramExpressive.isEnabled()) {
      return false;
    }
    final int[] checked = {checkedIndex >= 0 && checkedIndex < items.length ? checkedIndex : -1};
    final PopupLayout popup = host.showPopup(null, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsWrap) -> {
      final Context ctx = optionsWrap.getContext();
      final int availableWidth = host.context().getControllerWidth(optionsWrap);
      int contentHeight = 0;

      TextView titleView = XgramM3Wheel.newTitle(ctx, title, forcedTheme);
      optionsWrap.addView(titleView);
      contentHeight += XgramM3Wheel.measureHeight(titleView, availableWidth);
      if (!TextUtils.isEmpty(text)) {
        TextView textView = XgramM3Wheel.newSupportingText(ctx, text, forcedTheme);
        optionsWrap.addView(textView);
        contentHeight += XgramM3Wheel.measureHeight(textView, availableWidth);
      }

      // The choices
      final LinearLayout list = new LinearLayout(ctx);
      list.setOrientation(LinearLayout.VERTICAL);
      list.setPadding(Screen.dp(XgramExpressive.LIST_INSET), 0, Screen.dp(XgramExpressive.LIST_INSET), Screen.dp(8f));
      final ChoiceRow[] rows = new ChoiceRow[items.length];
      final int rowHeight = Screen.dp(ROW_HEIGHT), gap = Screen.dp(XgramExpressive.LIST_GAP);
      for (int i = 0; i < items.length; i++) {
        final int index = i;
        final ChoiceRow row = new ChoiceRow(ctx, items[i], segmentOf(i, items.length), i == checked[0], forcedTheme);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
        if (i > 0) {
          params.topMargin = gap;
        }
        row.setLayoutParams(params);
        row.setOnClickListener(v -> {
          if (checked[0] != index) {
            if (checked[0] != -1) {
              rows[checked[0]].setChecked(false, true);
            }
            checked[0] = index;
            row.setChecked(true, true);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          }
          if (applyOnTap) {
            popupLayout.hideWindow(true);
            callback.onChoice(index);
          }
        });
        rows[i] = row;
        list.addView(row);
      }
      final int listHeight = items.length * rowHeight + (items.length - 1) * gap + list.getPaddingBottom();
      final int maxListHeight = Math.max(rowHeight * 3, (int) (Screen.currentHeight() * MAX_LIST_SHARE));
      final int scrollHeight = Math.min(listHeight, maxListHeight);
      final ScrollView scrollView = new ScrollView(ctx);
      scrollView.setVerticalScrollBarEnabled(listHeight > scrollHeight);
      scrollView.setOverScrollMode(listHeight > scrollHeight ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
      scrollView.setClipToPadding(false);
      scrollView.addView(list, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, scrollHeight));
      if (checked[0] > 0 && listHeight > scrollHeight) {
        // the current choice in the middle of the visible part, once laid out
        final int target = checked[0];
        scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
          @Override
          public void onLayoutChange (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (bottom - top <= 0) {
              return;
            }
            scrollView.removeOnLayoutChangeListener(this);
            final int rowTop = target * (rowHeight + gap);
            scrollView.scrollTo(0, Math.max(0, rowTop - (scrollView.getHeight() - rowHeight) / 2));
          }
        });
      }
      optionsWrap.addView(scrollView);
      contentHeight += scrollHeight;

      // [Cancel] [Done]
      TextView cancel = XgramR5Sheets.textButton(ctx, Lang.getString(R.string.Cancel), XgramExpressive.PRIMARY, forcedTheme);
      cancel.setOnClickListener(v -> popupLayout.hideWindow(true));
      TextView done = null;
      if (!applyOnTap) {
        done = XgramR5Sheets.filledButton(ctx, Lang.getString(R.string.Done), false, forcedTheme);
        done.setOnClickListener(v -> {
          final int index = checked[0];
          popupLayout.hideWindow(true);
          if (index != -1) {
            callback.onChoice(index);
          }
        });
      }
      LinearLayout actions = XgramR5Sheets.actionsRow(ctx, null, cancel, done);
      LinearLayout.LayoutParams actionsParams = (LinearLayout.LayoutParams) actions.getLayoutParams();
      actionsParams.bottomMargin = Screen.dp(8f);
      actions.setLayoutParams(actionsParams);
      optionsWrap.addView(actions);
      contentHeight += actionsParams.height + actionsParams.bottomMargin;
      return contentHeight;
    }, forcedTheme, XgramExpressive.TS_HEADLINE_S);
    return popup != null;
  }

  private static int segmentOf (int index, int count) {
    if (count == 1) {
      return XgramExpressive.SEGMENT_SINGLE;
    }
    if (index == 0) {
      return XgramExpressive.SEGMENT_FIRST;
    }
    return index == count - 1 ? XgramExpressive.SEGMENT_LAST : XgramExpressive.SEGMENT_MIDDLE;
  }

  @ColorInt
  private static int role (int role, @Nullable ThemeDelegate forcedTheme) {
    return XgramR5Sheets.role(role, forcedTheme);
  }

  /**
   * One choice: a segmented tile (surfaceBright, secondaryContainer while checked) with an M3 radio button at the start
   * and the label in bodyLarge. The check springs in (the dot on SPATIAL_FAST, which overshoots a little; the tile and
   * label colors follow it, clamped), or snaps with Reduce motion. No allocation while drawing.
   */
  private static final class ChoiceRow extends View {
    private final CharSequence label;
    private final @Nullable ThemeDelegate forcedTheme;
    private final XgramExpressive.MorphDrawable tile;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final XgramExpressive.Spring checkSpring;
    private boolean checked;
    private @Nullable CharSequence ellipsized;
    private float textBaseline;

    ChoiceRow (Context context, CharSequence label, int segment, boolean checked, @Nullable ThemeDelegate forcedTheme) {
      super(context);
      this.label = label;
      this.forcedTheme = forcedTheme;
      this.checked = checked;
      textPaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_L, false));
      textPaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_L)));
      textPaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_BODY_L) / XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_L));
      tile = XgramExpressive.attachPressMorph(this, role(XgramExpressive.SURFACE_BRIGHT, forcedTheme), XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
      tile.setSegment(segment);
      checkSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, checked ? 1f : 0f, (s, value) -> invalidate());
      setClickable(true);
      setFocusable(true);
      setContentDescription(label);
      setWillNotDraw(false);
    }

    void setChecked (boolean checked, boolean animated) {
      if (this.checked == checked) {
        return;
      }
      this.checked = checked;
      if (animated) {
        checkSpring.animateTo(checked ? 1f : 0f);
      } else {
        checkSpring.setValue(checked ? 1f : 0f);
      }
      sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
      invalidate();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      final int textStart = Screen.dp(ROW_PADDING + RADIO_SLOT + RADIO_TEXT_GAP);
      final int available = Math.max(0, w - textStart - Screen.dp(ROW_PADDING));
      ellipsized = TextUtils.ellipsize(label, textPaint, available, TextUtils.TruncateAt.END).toString();
      textWidth = textPaint.measureText(ellipsized.toString());
      final Paint.FontMetrics metrics = textPaint.getFontMetrics();
      textBaseline = h / 2f - (metrics.ascent + metrics.descent) / 2f;
    }

    private float textWidth;

    @Override
    public void draw (@NonNull Canvas c) {
      // the tile (the background MorphDrawable): surfaceBright, secondaryContainer while checked, set before it draws
      tile.setColor(ColorUtils.fromToArgb(role(XgramExpressive.SURFACE_BRIGHT, forcedTheme), role(XgramExpressive.SECONDARY_CONTAINER, forcedTheme), MathUtils.clamp(checkSpring.getValue())));
      super.draw(c);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final float value = checkSpring.getValue();
      final float f = MathUtils.clamp(value);
      super.onDraw(c);
      final boolean rtl = Lang.rtl();
      final int width = getWidth();
      final float cx = rtl ? width - Screen.dp(ROW_PADDING + RADIO_SLOT / 2f) : Screen.dp(ROW_PADDING + RADIO_SLOT / 2f);
      final float cy = getHeight() / 2f;
      final int primary = role(XgramExpressive.PRIMARY, forcedTheme);
      final int ring = ColorUtils.fromToArgb(role(XgramExpressive.ON_SURFACE_VARIANT, forcedTheme), primary, f);
      XgramM3Controls.drawRadio(c, cx, cy, ring, primary, Math.max(0f, value));
      if (ellipsized != null) {
        textPaint.setColor(ColorUtils.fromToArgb(role(XgramExpressive.ON_SURFACE, forcedTheme), role(XgramExpressive.ON_SECONDARY_CONTAINER, forcedTheme), f));
        final float textStart = Screen.dp(ROW_PADDING + RADIO_SLOT + RADIO_TEXT_GAP);
        final float x = rtl ? width - textStart - textWidth : textStart;
        c.drawText(ellipsized, 0, ellipsized.length(), x, textBaseline, textPaint);
      }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setClassName(android.widget.RadioButton.class.getName());
      info.setCheckable(true);
      info.setChecked(checked);
    }
  }
}
