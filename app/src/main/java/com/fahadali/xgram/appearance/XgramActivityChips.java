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

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/**
 * Activity's filter chips (Material 3 Expressive): All, Mentions, Replies and Reactions as a single choice, then an
 * independent Unread toggle. M3 filter chips (XgramM3Sessions.ChipView: 32dp, 8dp corners, labelLarge, the check
 * springs in) in a row that scrolls sideways, 16dp from the list's sides; each chip takes taps over the row's whole
 * 48dp height. Counts follow the labels ("Mentions 3"), none at 0.
 */
@SuppressLint("ViewConstructor")
public final class XgramActivityChips extends HorizontalScrollView {
  public interface Listener {
    void onFilterSelected (int filter);
    void onUnreadToggled (boolean unread);
  }

  /** Filters, as XgramActivityStore.FILTER_*. */
  public static final int FILTER_ALL = 0, FILTER_MENTIONS = 1, FILTER_REPLIES = 2, FILTER_REACTIONS = 3;

  private static final float ROW_HEIGHT = 48f, CHIP_HEIGHT = 32f, GAP = 8f, SIDE = 16f, VERTICAL = 4f;
  private static final int[] LABELS = {R.string.XgramActivityAll, R.string.XgramActivityMentions, R.string.XgramActivityReplies, R.string.XgramActivityReactions};

  private final LinearLayout row;
  private final XgramM3Sessions.ChipView[] filters = new XgramM3Sessions.ChipView[LABELS.length];
  private final FrameLayout[] filterWraps = new FrameLayout[LABELS.length];
  private final XgramM3Sessions.ChipView unread;
  private final int[] counts = new int[LABELS.length];
  private int selected = FILTER_ALL;
  private boolean unreadOn;
  private boolean bound;
  @Nullable private Listener listener;

  public XgramActivityChips (Context context) {
    super(context);
    setHorizontalScrollBarEnabled(false);
    setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    setClipToPadding(false);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ROW_HEIGHT + VERTICAL * 2)));
    row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    int side = Screen.dp(SIDE);
    row.setPadding(side, Screen.dp(VERTICAL), side, Screen.dp(VERTICAL));
    for (int i = 0; i < LABELS.length; i++) {
      final int filter = i;
      XgramM3Sessions.ChipView chip = new XgramM3Sessions.ChipView(context, LABELS[i]);
      filters[i] = chip;
      filterWraps[i] = wrap(context, chip, v -> select(filter, true, true), i > 0);
    }
    unread = new XgramM3Sessions.ChipView(context, R.string.XgramActivityUnread);
    wrap(context, unread, v -> setUnread(!unreadOn, true, true), true);
    addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    applyStates(false);
  }

  /** The chip in a 48dp tall wrap that takes its taps and passes its pressed state to it (its state layer shows). */
  private FrameLayout wrap (Context context, XgramM3Sessions.ChipView chip, View.OnClickListener onClick, boolean gapBefore) {
    FrameLayout wrap = new FrameLayout(context);
    chip.setClickable(false);
    chip.setFocusable(false);
    chip.setDuplicateParentStateEnabled(true);
    wrap.addView(chip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CHIP_HEIGHT), Gravity.CENTER_VERTICAL));
    wrap.setOnClickListener(onClick);
    wrap.setClickable(true);
    wrap.setFocusable(true);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(ROW_HEIGHT));
    if (gapBefore) {
      params.setMarginStart(Screen.dp(GAP));
    }
    row.addView(wrap, params);
    return wrap;
  }

  public void setListener (@Nullable Listener listener) {
    this.listener = listener;
  }

  private void select (int filter, boolean animated, boolean notify) {
    if (selected == filter) {
      return;
    }
    selected = filter;
    applyStates(animated);
    if (notify && listener != null) {
      listener.onFilterSelected(filter);
    }
  }

  public void setSelectedFilter (int filter, boolean animated) {
    select(Math.max(FILTER_ALL, Math.min(FILTER_REACTIONS, filter)), animated, false);
  }

  private void setUnread (boolean unread, boolean animated, boolean notify) {
    if (unreadOn == unread) {
      return;
    }
    unreadOn = unread;
    applyStates(animated);
    if (notify && listener != null) {
      listener.onUnreadToggled(unread);
    }
  }

  public void setUnread (boolean unread, boolean animated) {
    setUnread(unread, animated, false);
  }

  /** New counts per filter (FILTER_*); a chip shows its count only when it is above 0. */
  public void setCounts (@NonNull int[] newCounts) {
    boolean changed = false;
    for (int i = 0; i < counts.length && i < newCounts.length; i++) {
      if (counts[i] != newCounts[i]) {
        counts[i] = newCounts[i];
        changed = true;
      }
    }
    if (changed || !bound) {
      bound = true;
      for (int i = 0; i < filters.length; i++) {
        String label = Lang.getString(LABELS[i]);
        filters[i].setText(i != FILTER_ALL && counts[i] > 0 ? Lang.getString(R.string.XgramActivityChipCount, label, counts[i]) : label);
      }
    }
  }

  /** Reactions off: no Reactions chip (a selection on it goes back to All, through the listener). */
  public void setReactionsShown (boolean shown) {
    FrameLayout wrap = filterWraps[FILTER_REACTIONS];
    int visibility = shown ? View.VISIBLE : View.GONE;
    if (wrap.getVisibility() != visibility) {
      wrap.setVisibility(visibility);
    }
    if (!shown && selected == FILTER_REACTIONS) {
      select(FILTER_ALL, false, true);
    }
  }

  private void applyStates (boolean animated) {
    for (int i = 0; i < filters.length; i++) {
      filters[i].setChecked(i == selected, false);
      filterWraps[i].setSelected(i == selected);
    }
    unread.setChecked(unreadOn, false);
  }

  /** The chips' colours are read when drawn: a theme change only repaints them. */
  public void invalidateChips () {
    for (XgramM3Sessions.ChipView chip : filters) {
      chip.invalidate();
    }
    unread.invalidate();
  }
}
