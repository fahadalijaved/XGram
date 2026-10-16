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
 * File created on 19/04/2024
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.InfiniteRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.lambda.Destroyable;

public final class ColumnDataPicker implements Destroyable {
  public static class Column {
    public static class StylingOptions {
      public final float weight;

      public StylingOptions (float weight) {
        this.weight = weight;
      }

      public boolean noPadding;


      public StylingOptions setNoPadding (boolean noPadding) {
        this.noPadding = noPadding;
        return this;
      }
    }

    public final List<SimpleStringItem> rows;
    public final StylingOptions style;
    public int index;

    public Column (List<SimpleStringItem> rows, @NonNull StylingOptions style, int index) {
      this.rows = rows;
      this.style = style;
      this.index = index;
    }

    public InfiniteRecyclerView<SimpleStringItem> view;

    private @Nullable InfiniteRecyclerView.MinMaxProvider<SimpleStringItem> minMaxProvider;

    public void setMinMaxProvider (@Nullable InfiniteRecyclerView.MinMaxProvider<SimpleStringItem> minMaxProvider) {
      this.minMaxProvider = minMaxProvider;
    }

    private @Nullable InfiniteRecyclerView.ItemChangeListener<SimpleStringItem> itemChangeListener;

    public void setItemChangeListener (@Nullable InfiniteRecyclerView.ItemChangeListener<SimpleStringItem> itemChangeListener) {
      this.itemChangeListener = itemChangeListener;
    }
  }

  private List<Column> columns;
  private float spacingStart, spacingEnd;

  public ColumnDataPicker () { }

  public void setColumns (List<Column> columns) {
    this.columns = columns;
  }

  public void setSpacing (float spacingStart, float spacingEnd) {
    this.spacingStart = spacingStart;
    this.spacingEnd = spacingEnd;
  }

  public interface CommitListener {
    boolean onCommitRequested (ColumnDataPicker picker, View commitButtonView);
  }

  // XGram M3E: an optional third action at the start of the bottom actions (e.g. "Remove birthday")
  private @Nullable CharSequence neutralText;
  private @Nullable CommitListener neutralListener;

  /** Shown with Expressive design only, as a text button at the start of the actions; returns true to dismiss. */
  public void setNeutralAction (@Nullable CharSequence text, @Nullable CommitListener listener) {
    this.neutralText = text;
    this.neutralListener = listener;
  }

  private PopupLayout popupLayout;

  public PopupLayout popupLayout () {
    return popupLayout;
  }

  public boolean hasVisiblePopUp () {
    return popupLayout != null && popupLayout.isBoundWindowShowing();
  }

  public void dismissPopup (boolean animated) {
    if (popupLayout != null) {
      popupLayout.hideWindow(animated);
      popupLayout = null;
    }
  }

  @Override
  public void performDestroy () {
    dismissPopup(false);
  }

  public void showPopup (ViewController<?> c, CharSequence title, CharSequence commitButtonText, @Nullable ThemeDelegate forcedTheme, @Nullable CommitListener commitListener) {
    if (popupLayout != null)
      throw new IllegalStateException();
    final boolean xgramSheet = com.fahadali.xgram.appearance.XgramM3Wheel.on();
    // XGram M3E: the sheet title is drawn here in headlineSmall, so the sheet's own (titleLarge) one stays empty
    this.popupLayout = c.showPopup(xgramSheet ? null : title, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsWrap) -> {
      int contentHeight = 0;
      final int pickerHeight = InfiniteRecyclerView.getItemHeight() * 5;

      final boolean xgramM3 = com.fahadali.xgram.appearance.XgramM3Wheel.on();

      if (xgramM3 && !android.text.TextUtils.isEmpty(title)) {
        TextView titleView = com.fahadali.xgram.appearance.XgramM3Wheel.newTitle(c.context(), title, forcedTheme);
        optionsWrap.addView(titleView);
        contentHeight += com.fahadali.xgram.appearance.XgramM3Wheel.measureHeight(titleView, c.context().getControllerWidth(titleView));
      }

      // XGram M3E: an optional column (its last row is a blank "—", the birthday's year) gets a Show year chip instead
      final Column optionalColumn = xgramM3 ? xgramFindOptionalColumn() : null;

      LinearLayout columnWrap = new LinearLayout(c.context());
      columnWrap.setOrientation(LinearLayout.HORIZONTAL);
      columnWrap.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pickerHeight));
      // XGram M3E: the selected row's secondaryContainer band instead of two hairlines
      ViewUtils.setBackground(columnWrap, xgramM3 ? new com.fahadali.xgram.appearance.XgramM3Wheel.BandDrawable(forcedTheme) : new LinesHighlightDrawable(forcedTheme));
      if (forcedTheme == null) {
        c.addThemeInvalidateListener(columnWrap);
      }

      if (spacingStart > 0) {
        View emptyView = new View(c.context());
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, spacingStart));
        columnWrap.addView(emptyView);
      }

      final TextView commitButton;
      if (xgramM3) {
        commitButton = com.fahadali.xgram.appearance.XgramM3Wheel.newConfirmButton(c.context(), forcedTheme);
        commitButton.setText(commitButtonText);
      } else {
        commitButton = newCommitButton(c, forcedTheme);
        Views.setMediumText(commitButton, Lang.uppercase(commitButtonText.toString()));
      }

      for (Column column : columns) {
        InfiniteRecyclerView<SimpleStringItem> picker = new InfiniteRecyclerView<SimpleStringItem>(c.context(), false);
        picker.setNeedSeparators(false);
        picker.setMinMaxProvider(column.minMaxProvider);
        picker.setItemChangeListener(column.itemChangeListener);
        picker.setForcedTheme(forcedTheme);
        picker.addThemeListeners(c);
        if (column.style.noPadding) {
          picker.setItemPadding(0);
        }
        if (column == optionalColumn) {
          // the wheel never shows the blank row: the chip stands for it
          final List<SimpleStringItem> rows = column.rows.subList(0, column.rows.size() - 1);
          final boolean hasValue = column.index >= 0 && column.index < rows.size();
          picker.setTrimItems(false);
          picker.initWithItems(rows, hasValue ? column.index : Math.max(0, rows.size() - 1 - XGRAM_DEFAULT_YEARS_BACK));
          xgramOptionalShown = hasValue;
          if (!hasValue) {
            picker.setVisibility(View.GONE);
          }
        } else {
          picker.initWithItems(column.rows, column.index);
        }
        picker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, column.style.weight));
        columnWrap.addView(picker);
        column.view = picker;
      }
      optionsWrap.addView(columnWrap);
      contentHeight += pickerHeight;

      if (optionalColumn != null) {
        final LinearLayout chipRow = com.fahadali.xgram.appearance.XgramM3Wheel.newColumnChip(c.context(), R.string.XgramR5CallsShowYear, xgramOptionalShown,
          checked -> xgramSetOptionalShown(optionalColumn, columnWrap, checked));
        optionsWrap.addView(chipRow);
        contentHeight += Screen.dp(com.fahadali.xgram.appearance.XgramM3Wheel.CHIP_ROW_HEIGHT);
      }

      if (spacingEnd > 0) {
        View emptyView = new View(c.context());
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, spacingStart));
        columnWrap.addView(emptyView);
      }

      if (xgramM3) {
        // XGram M3E: [Remove birthday] ... [Cancel] [Save] instead of the full-width uppercase bar
        com.fahadali.xgram.appearance.XgramM3Wheel.Actions actions = com.fahadali.xgram.appearance.XgramM3Wheel.newActions(c.context(), forcedTheme, neutralListener != null ? neutralText : null, Lang.getString(R.string.Cancel), commitButton);
        actions.cancel.setOnClickListener(v -> dismissPopup(true));
        if (actions.neutral != null) {
          final CommitListener neutral = neutralListener;
          actions.neutral.setOnClickListener(v -> {
            if (neutral != null && neutral.onCommitRequested(this, v)) {
              dismissPopup(true);
            }
          });
        }
        optionsWrap.addView(actions.view);
        contentHeight += Screen.dp(com.fahadali.xgram.appearance.XgramM3Wheel.ACTIONS_HEIGHT);
      } else {
        optionsWrap.addView(commitButton);
        contentHeight += Screen.dp(56f);
      }

      if (commitListener != null) {
        commitButton.setOnClickListener(v -> {
          if (commitListener.onCommitRequested(this, v)) {
            dismissPopup(true);
          }
        });
      }

      return contentHeight;
    }, forcedTheme);
  }

  // XGram M3E: the optional column (Show year). Hiding it fades it out, then the other columns move to their new
  // places from where they were (they are laid out once, then only translated on a spring); showing it does the
  // reverse. Reduce motion snaps both.

  private static final int XGRAM_DEFAULT_YEARS_BACK = 25;
  private boolean xgramOptionalShown;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramOptionalAlpha, xgramOptionalMove;
  private @Nullable View[] xgramMoveViews;
  private @Nullable float[] xgramMoveFrom, xgramMoveDelta;

  @Nullable
  private Column xgramFindOptionalColumn () {
    if (columns == null) {
      return null;
    }
    for (Column column : columns) {
      final int size = column.rows != null ? column.rows.size() : 0;
      if (size > 1 && "—".equals(column.rows.get(size - 1).toString())) {
        return column;
      }
    }
    return null;
  }

  private void xgramSetOptionalShown (Column column, LinearLayout wrap, boolean show) {
    final InfiniteRecyclerView<SimpleStringItem> view = column.view;
    if (view == null || xgramOptionalShown == show) {
      return;
    }
    xgramOptionalShown = show;
    // The value: the wheel's row, or the blank last row while the column is hidden
    final int index = show ? view.getCurrentIndex() : column.rows.size() - 1;
    final InfiniteRecyclerView.ItemChangeListener<SimpleStringItem> listener = column.itemChangeListener;
    if (listener != null) {
      listener.onCurrentIndexChanged(view, index);
      listener.onCurrentIndexFinalized(view, index);
    } else {
      column.index = index;
    }
    if (xgramOptionalAlpha == null) {
      xgramOptionalAlpha = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST, show ? 0f : 1f, (s, value) -> {
        final float alpha = me.vkryl.core.MathUtils.clamp(value);
        view.setAlpha(alpha);
        final float scale = .9f + .1f * alpha;
        view.setScaleX(scale);
        view.setScaleY(scale);
      });
    }
    if (show) {
      view.setAlpha(0f);
      xgramMoveColumns(wrap, view, true);
      xgramOptionalAlpha.setEndListener(null);
      xgramOptionalAlpha.setValue(0f);
      xgramOptionalAlpha.animateTo(1f);
    } else {
      xgramOptionalAlpha.setEndListener(() -> {
        if (!xgramOptionalShown && view.getVisibility() == View.VISIBLE) {
          xgramMoveColumns(wrap, view, false);
        }
      });
      xgramOptionalAlpha.animateTo(0f);
    }
  }

  /** Lays the columns out with {@code optional} shown or gone, then springs the others from their old places. */
  private void xgramMoveColumns (LinearLayout wrap, View optional, boolean show) {
    final int count = wrap.getChildCount();
    if (xgramMoveViews == null || xgramMoveViews.length != count) {
      xgramMoveViews = new View[count];
      xgramMoveFrom = new float[count];
      xgramMoveDelta = new float[count];
    }
    for (int i = 0; i < count; i++) {
      View child = wrap.getChildAt(i);
      xgramMoveViews[i] = child;
      xgramMoveFrom[i] = child.getLeft() + child.getWidth() / 2f + child.getTranslationX();
      xgramMoveDelta[i] = 0f;
    }
    optional.setVisibility(show ? View.VISIBLE : View.GONE);
    wrap.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw () {
        wrap.getViewTreeObserver().removeOnPreDrawListener(this);
        final View[] views = xgramMoveViews;
        final float[] from = xgramMoveFrom, delta = xgramMoveDelta;
        if (views == null || from == null || delta == null) {
          return true;
        }
        for (int i = 0; i < views.length; i++) {
          View child = views[i];
          if (child == null || child == optional || child.getVisibility() != View.VISIBLE || child.getWidth() == 0) {
            delta[i] = 0f;
            continue;
          }
          delta[i] = from[i] - (child.getLeft() + child.getWidth() / 2f);
          child.setTranslationX(delta[i]);
        }
        if (xgramOptionalMove == null) {
          xgramOptionalMove = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> {
            final View[] moving = xgramMoveViews;
            final float[] moveDelta = xgramMoveDelta;
            if (moving == null || moveDelta == null) {
              return;
            }
            for (int i = 0; i < moving.length; i++) {
              if (moving[i] != null && moveDelta[i] != 0f) {
                moving[i].setTranslationX(moveDelta[i] * value);
              }
            }
          });
        }
        xgramOptionalMove.setValue(1f);
        xgramOptionalMove.animateTo(0f);
        return true;
      }
    });
    wrap.requestLayout();
  }

  private static EmojiTextView newCommitButton (ViewController<?> c, @Nullable ThemeDelegate forcedTheme) {
    final EmojiTextView buttonView = new EmojiTextView(c.context());
    Views.setClickable(buttonView);
    buttonView.setGravity(Gravity.CENTER);
    buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    buttonView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f)));
    FillingDrawable drawable = ViewSupport.setThemedBackground(buttonView, ColorId.fillingPositive, forcedTheme != null ? null : c);
    drawable.setForcedTheme(forcedTheme);
    if (forcedTheme != null) {
      buttonView.setTextColor(forcedTheme.getColor(ColorId.fillingPositiveContent));
    } else {
      buttonView.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
      c.addThemeTextColorListener(buttonView, ColorId.fillingPositiveContent);
    }
    return buttonView;
  }

  private static class LinesHighlightDrawable extends Drawable {
    private final @Nullable ThemeDelegate forcedTheme;

    public LinesHighlightDrawable (@Nullable ThemeDelegate forcedTheme) {
      this.forcedTheme = forcedTheme;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      Rect bounds = getBounds();
      int viewWidth = bounds.width();
      int viewHeight = bounds.height();

      int cy = viewHeight / 2;
      int h2 = InfiniteRecyclerView.getItemHeight() / 2;

      c.drawLine(0, cy - h2, viewWidth, cy - h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
      c.drawLine(0, cy + h2, viewWidth, cy + h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }
}
