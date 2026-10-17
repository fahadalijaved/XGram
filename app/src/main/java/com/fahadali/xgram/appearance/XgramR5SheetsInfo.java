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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

/**
 * XGram M3E round 5: an M3 sheet of list items (exact message times, IDs, edit history, transcriptions): a titleLarge
 * title under the handle, then segmented surfaceBright tiles (16dp inset, 20 / 4dp corners, 2dp apart) holding a
 * headline and a supporting line, an optional badge and a trailing copy icon button, in a scroll capped at 60% of the
 * screen, and an actions row at the bottom. Tapping a tile copies its value and shows the "Copied" snackbar. No Cancel
 * row: the sheet closes by its scrim, a drag or back.
 */
public final class XgramR5SheetsInfo {
  private XgramR5SheetsInfo () { }

  /** One list item of the sheet. */
  public static final class Row {
    final CharSequence headline;
    final @Nullable CharSequence supporting;
    boolean supportingFirst;
    @Nullable String copyValue;
    @Nullable CharSequence badge;
    int headlineMaxLines = 3;

    /** {@code headline} in bodyLarge (onSurface), {@code supporting} in bodyMedium (onSurfaceVariant) under it. */
    public Row (@NonNull CharSequence headline, @Nullable CharSequence supporting) {
      this.headline = headline;
      this.supporting = supporting;
    }

    /** The supporting line above the headline in labelMedium (a timestamp over a version's text). */
    public Row supportingFirst () {
      this.supportingFirst = true;
      return this;
    }

    /** Tapping the tile, or its trailing copy icon button, copies {@code value}. */
    public Row copy (@Nullable String value) {
      this.copyValue = value;
      return this;
    }

    /** A labelSmall badge with full corners in primaryContainer after the supporting line ("Current"). */
    public Row badge (@Nullable CharSequence badge) {
      this.badge = badge;
      return this;
    }

    /** The headline's line limit (3 by default; 0 for no limit). */
    public Row maxLines (int maxLines) {
      this.headlineMaxLines = maxLines;
      return this;
    }

    boolean tabular;

    /** The headline in tabular figures (times, durations, IDs). */
    public Row tabular () {
      this.tabular = true;
      return this;
    }
  }

  public static final int ACTION_TEXT = 0, ACTION_TONAL = 1, ACTION_FILLED = 2;

  /** A button of the sheet's actions row. It closes the sheet, then runs. */
  public static final class Action {
    final CharSequence label;
    final int kind;
    final @Nullable Runnable onClick;
    boolean atStart, keepOpen;

    public Action (@NonNull CharSequence label, int kind, @Nullable Runnable onClick) {
      this.label = label;
      this.kind = kind;
      this.onClick = onClick;
    }

    /** At the start of the row (a neutral action), instead of the end. */
    public Action atStart () {
      this.atStart = true;
      return this;
    }

    /** Leaves the sheet open (a "Copy" with its own snackbar). */
    public Action keepOpen () {
      this.keepOpen = true;
      return this;
    }
  }

  /**
   * Copies {@code value} (formatting kept, as UI.copyText) and confirms it with the "Copied" snackbar (a toast when
   * the app is in the background), unless the system shows its own clipboard confirmation (Android 13+, as UIHandler).
   * Every sheet's Copy goes through here, so they all confirm the same way.
   */
  public static void copy (@Nullable CharSequence value) {
    if (TextUtils.isEmpty(value)) {
      return;
    }
    UI.copyText(value, 0);
    if (org.thunderdog.challegram.config.Device.HAS_BUILTIN_CLIPBOARD_TOASTS) {
      return;
    }
    final String copied = Lang.getString(R.string.XgramR5SheetsCopied);
    if (!XgramSnackbar.show(copied, Toast.LENGTH_SHORT)) {
      UI.showToast(R.string.CopiedText, Toast.LENGTH_SHORT);
    }
  }

  /** Shows the sheet; {@code title} may be null. Returns null when the controller cannot show popups now. */
  @Nullable
  public static PopupLayout show (@NonNull ViewController<?> c, @Nullable CharSequence title, @NonNull List<Row> rows, @Nullable Action... actions) {
    if (c.isDestroyed()) {
      return null;
    }
    final int maxScrollHeight = Math.round(Screen.currentHeight() * .6f);
    return c.showPopup(title, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      final Context context = optionsLayout.getContext();
      final int width = XgramM3Sheets.sheetWidth(c.context().getControllerWidth(optionsLayout));

      LinearLayout list = new LinearLayout(context);
      list.setOrientation(LinearLayout.VERTICAL);
      list.setPadding(0, Screen.dp(4f), 0, Screen.dp(8f));
      final int count = rows.size();
      for (int i = 0; i < count; i++) {
        final int segment = count == 1 ? XgramExpressive.SEGMENT_SINGLE :
          i == 0 ? XgramExpressive.SEGMENT_FIRST :
          i == count - 1 ? XgramExpressive.SEGMENT_LAST : XgramExpressive.SEGMENT_MIDDLE;
        View tile = newTile(context, rows.get(i), segment);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = params.rightMargin = Screen.dp(XgramExpressive.LIST_INSET);
        params.topMargin = i == 0 ? 0 : Screen.dp(XgramExpressive.LIST_GAP);
        list.addView(tile, params);
      }

      ScrollView scrollView = new ScrollView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          int maxHeight = maxScrollHeight;
          if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
          }
          super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        }
      };
      scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
      scrollView.setVerticalScrollBarEnabled(false);
      scrollView.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      optionsLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int height = Math.min(list.getMeasuredHeight(), maxScrollHeight);
      height += addActions(context, popupLayout, optionsLayout, actions);
      return height;
    }, null, XgramExpressive.TS_TITLE_L);
  }

  /**
   * A text sheet (a transcription): the text in bodyLarge in a 16dp inset surfaceContainerHigh card with 16dp corners
   * and padding (as the Quote and AI sheets), scrolling within 60% of the screen, then the actions row.
   */
  @Nullable
  public static PopupLayout showText (@NonNull ViewController<?> c, @Nullable CharSequence title, @NonNull CharSequence text, @Nullable Action... actions) {
    if (c.isDestroyed()) {
      return null;
    }
    final int maxScrollHeight = Math.round(Screen.currentHeight() * .6f);
    return c.showPopup(title, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      final Context context = optionsLayout.getContext();
      final int width = XgramM3Sheets.sheetWidth(c.context().getControllerWidth(optionsLayout));

      TextView textView = new NoScrollTextView(context);
      XgramExpressive.applyText(textView, XgramExpressive.TS_BODY_L, false);
      textView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      c.addThemeTextColorListener(textView, org.thunderdog.challegram.theme.ColorId.text); // onSurface
      textView.setHighlightColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.PRIMARY), .40f)); // ColorId.textSelectionHighlight
      textView.setGravity(Lang.gravity());
      textView.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
      textView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
      textView.setTextIsSelectable(true);
      textView.setText(text);

      ScrollView scrollView = new ScrollView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          int maxHeight = maxScrollHeight;
          if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
          }
          super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        }
      };
      scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
      scrollView.setVerticalScrollBarEnabled(true);
      scrollView.addView(textView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      XgramR5Sheets.applyCard(scrollView, XgramExpressive.SURFACE_CONTAINER_HIGH, XgramExpressive.CORNER_L);
      scrollView.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
      scrollView.setClipToOutline(true);
      LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      cardParams.leftMargin = cardParams.rightMargin = Screen.dp(16f);
      cardParams.topMargin = Screen.dp(8f);
      cardParams.bottomMargin = Screen.dp(8f);
      optionsLayout.addView(scrollView, cardParams);

      textView.measure(View.MeasureSpec.makeMeasureSpec(width - Screen.dp(32f), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int height = Math.min(textView.getMeasuredHeight(), maxScrollHeight) + cardParams.topMargin + cardParams.bottomMargin;
      height += addActions(context, popupLayout, optionsLayout, actions);
      return height;
    }, null, XgramExpressive.TS_TITLE_L);
  }

  /** The actions row of a sheet; the height it takes (0 without actions). */
  private static int addActions (Context context, PopupLayout popupLayout, ViewGroup optionsLayout, @Nullable Action[] actions) {
    if (actions == null || actions.length == 0) {
      return 0;
    }
    View start = null;
    View[] end = new View[actions.length];
    for (int i = 0; i < actions.length; i++) {
      final Action action = actions[i];
      if (action == null) {
        continue;
      }
      TextView button;
      switch (action.kind) {
        case ACTION_FILLED:
          button = XgramR5Sheets.filledButton(context, action.label, false, null);
          break;
        case ACTION_TONAL:
          button = XgramR5Sheets.tonalButton(context, action.label, null);
          break;
        case ACTION_TEXT:
        default:
          button = XgramR5Sheets.textButton(context, action.label, XgramExpressive.PRIMARY, null);
          break;
      }
      button.setOnClickListener(v -> {
        if (!action.keepOpen) {
          popupLayout.hideWindow(true);
        }
        if (action.onClick != null) {
          action.onClick.run();
        }
      });
      if (action.atStart && start == null) {
        start = button;
      } else {
        end[i] = button;
      }
    }
    LinearLayout row = XgramR5Sheets.actionsRow(context, start, end);
    optionsLayout.addView(row);
    return ((LinearLayout.LayoutParams) row.getLayoutParams()).height + ((LinearLayout.LayoutParams) row.getLayoutParams()).bottomMargin;
  }

  private static View newTile (Context context, Row row, int segment) {
    final boolean rtl = Lang.rtl();
    LinearLayout tile = new LinearLayout(context);
    tile.setOrientation(LinearLayout.HORIZONTAL);
    tile.setGravity(Gravity.CENTER_VERTICAL);
    tile.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    tile.setMinimumHeight(Screen.dp(56f));
    tile.setPaddingRelative(Screen.dp(16f), Screen.dp(12f), Screen.dp(row.copyValue != null ? 4f : 16f), Screen.dp(12f));
    XgramExpressive.MorphDrawable morph = XgramExpressive.attachPressMorph(tile, XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT), XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
    morph.setColorRole(XgramExpressive.SURFACE_BRIGHT);
    morph.setSegment(segment);

    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);

    TextView headline = new NoScrollTextView(context);
    XgramExpressive.applyText(headline, XgramExpressive.TS_BODY_L, false);
    headline.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
    headline.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    if (row.headlineMaxLines > 0) {
      headline.setMaxLines(row.headlineMaxLines);
      headline.setEllipsize(TextUtils.TruncateAt.END);
    }
    if (row.tabular) {
      headline.setFontFeatureSettings("tnum");
    }
    headline.setText(row.headline);

    View supportingView = null;
    if (!TextUtils.isEmpty(row.supporting) || !TextUtils.isEmpty(row.badge)) {
      LinearLayout line = new LinearLayout(context);
      line.setOrientation(LinearLayout.HORIZONTAL);
      line.setGravity(Gravity.CENTER_VERTICAL);
      if (!TextUtils.isEmpty(row.supporting)) {
        TextView supporting = new NoScrollTextView(context);
        // a timestamp over a version: labelMedium with tabular figures; a label under a value: bodyMedium
        XgramExpressive.applyText(supporting, row.supportingFirst ? XgramExpressive.TS_LABEL_M : XgramExpressive.TS_BODY_M, false);
        if (row.supportingFirst) {
          supporting.setFontFeatureSettings("tnum");
        }
        supporting.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
        supporting.setSingleLine(true);
        supporting.setEllipsize(TextUtils.TruncateAt.END);
        supporting.setText(row.supporting);
        line.addView(supporting, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
      }
      if (!TextUtils.isEmpty(row.badge)) {
        // The sticker sets' "New" badge spec (TrendingPackHeaderView): 16dp, 6dp sides, primaryContainer; the label's
        // colour is read when drawn, like the container's
        TextView badge = new XgramR5Lists.RoleText(context, XgramExpressive.TS_LABEL_S, XgramExpressive.ON_PRIMARY_CONTAINER);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setIncludeFontPadding(false);
        badge.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
        badge.setText(row.badge);
        XgramR5Sheets.applyCard(badge, XgramExpressive.PRIMARY_CONTAINER, XgramExpressive.CORNER_FULL);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(16f));
        if (!TextUtils.isEmpty(row.supporting)) {
          badgeParams.setMarginStart(Screen.dp(8f));
        }
        line.addView(badge, badgeParams);
      }
      supportingView = line;
    }
    if (supportingView != null && row.supportingFirst) {
      texts.addView(supportingView);
      LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      headlineParams.topMargin = Screen.dp(4f);
      texts.addView(headline, headlineParams);
    } else {
      texts.addView(headline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      if (supportingView != null) {
        texts.addView(supportingView);
      }
    }
    tile.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    if (row.copyValue != null) {
      final String value = row.copyValue;
      ImageView copyButton = new ImageView(context);
      copyButton.setScaleType(ImageView.ScaleType.CENTER);
      copyButton.setImageDrawable(Drawables.get(XgramIconPack.map(R.drawable.baseline_content_copy_24)));
      copyButton.setColorFilter(Paints.getColorFilter(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT)));
      copyButton.setContentDescription(Lang.getString(R.string.XgramR5SheetsCopyValue));
      XgramAppMotion.iconButton(copyButton, XgramExpressive.ON_SURFACE_VARIANT);
      copyButton.setOnClickListener(v -> copy(value));
      Views.setClickable(copyButton);
      LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f));
      copyParams.setMarginStart(Screen.dp(4f));
      tile.addView(copyButton, copyParams);
      tile.setOnClickListener(v -> copy(value));
      Views.setClickable(tile);
    }
    return tile;
  }
}
