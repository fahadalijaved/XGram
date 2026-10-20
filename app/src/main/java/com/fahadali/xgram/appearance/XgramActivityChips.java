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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;

/**
 * Activity's filter chips (Material 3 Expressive): All, Mentions, Replies and Reactions as a single choice, then an
 * independent Unread toggle, then Chats (the chats the page shows: "Chats" with a dropdown arrow, or the choice,
 * "2 chats" or a chat's name, filled, with a clear (x)). M3 filter chips (XgramM3Sessions.ChipView: 32dp, 8dp corners,
 * labelLarge, the check springs in) in a row that scrolls sideways, 16dp from the list's sides; each chip takes taps
 * over the row's whole 48dp height. Counts follow the labels ("Mentions 3"), none at 0.
 */
@SuppressLint("ViewConstructor")
public final class XgramActivityChips extends HorizontalScrollView {
  public interface Listener {
    void onFilterSelected (int filter);
    void onUnreadToggled (boolean unread);
    /** The Chats chip: choose the chats. */
    void onChatsClicked ();
    /** The Chats chip's clear (x): all chats again. */
    void onChatsCleared ();
  }

  /** Filters, as XgramActivityStore.FILTER_*. */
  public static final int FILTER_ALL = 0, FILTER_MENTIONS = 1, FILTER_REPLIES = 2, FILTER_REACTIONS = 3;

  private static final float ROW_HEIGHT = 48f, CHIP_HEIGHT = 32f, GAP = 8f, SIDE = 16f, VERTICAL = 4f;
  private static final int[] LABELS = {R.string.XgramActivityAll, R.string.XgramActivityMentions, R.string.XgramActivityReplies, R.string.XgramActivityReactions};

  private final LinearLayout row;
  private final XgramM3Sessions.ChipView[] filters = new XgramM3Sessions.ChipView[LABELS.length];
  private final FrameLayout[] filterWraps = new FrameLayout[LABELS.length];
  private final XgramM3Sessions.ChipView unread;
  private final ChatsChipView chats;
  private final FrameLayout chatsWrap;
  private final int[] counts = new int[LABELS.length];
  private int selected = FILTER_ALL;
  private boolean unreadOn;
  /** The chats chosen, as the chip reads it ("2 chats", a chat's name); null: all chats ("Chats"). */
  @Nullable private String chatsChoice;
  /** Where the last touch on the Chats chip went down, in the chip (its clear (x) or the rest). */
  private float chatsDownX = -1f;
  private int chatsClearAction = View.NO_ID;
  @Nullable private String chatsClearLabel;
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
    applyDirection();
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
    chats = new ChatsChipView(context);
    chatsWrap = wrap(context, chats, v -> onChatsTap(), true);
    chatsWrap.setOnTouchListener((v, e) -> {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        chatsDownX = e.getX() - chats.getLeft();
      }
      return false; // the wrap's own click follows
    });
    addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    applyStates(false);
    applyChats();
  }

  /**
   * The scroll view itself follows the language too (the window's root is left to right): in RTL the row sits at the
   * right and opens scrolled to its start, All first.
   */
  private void applyDirection () {
    final int direction = Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
    if (getLayoutDirection() != direction) {
      setLayoutDirection(direction);
    }
    if (row.getLayoutDirection() != direction) {
      row.setLayoutDirection(direction);
    }
  }

  /** The chip in a 48dp tall wrap that takes its taps and passes its pressed state to it (its state layer shows). */
  private FrameLayout wrap (Context context, View chip, View.OnClickListener onClick, boolean gapBefore) {
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

  /** The chats chosen, as the chip reads them ("2 chats", a chat's name), or null for all chats. */
  public void setChats (@Nullable String choice) {
    if (!TextUtils.equals(chatsChoice, choice)) {
      chatsChoice = choice;
      applyChats();
    }
  }

  /** A tap on the Chats chip: its clear (x) while chats are chosen, the choice otherwise. */
  private void onChatsTap () {
    final float downX = chatsDownX;
    chatsDownX = -1f; // a click without a touch (a keyboard, TalkBack) opens the choice
    if (listener == null) {
      return;
    }
    if (chatsChoice != null && chats.isClearHit(downX)) {
      listener.onChatsCleared();
    } else {
      listener.onChatsClicked();
    }
  }

  private void applyChats () {
    final boolean chosen = chatsChoice != null;
    final String label = chosen ? chatsChoice : Lang.getString(R.string.XgramActivityChats);
    if (!label.contentEquals(chats.getText())) {
      chats.setText(label);
    }
    chats.setChosen(chosen);
    final String description = chosen ? Lang.getString(R.string.XgramActivityChatsTitle) + ": " + label : label;
    if (!TextUtils.equals(chatsWrap.getContentDescription(), description)) {
      chatsWrap.setContentDescription(description);
    }
    // The clear (x) for TalkBack: an action of the chip (made again only when its label changes language)
    final String clearLabel = chosen ? Lang.getString(R.string.XgramActivityShowAllChats) : null;
    if (!TextUtils.equals(chatsClearLabel, clearLabel)) {
      if (chatsClearAction != View.NO_ID) {
        ViewCompat.removeAccessibilityAction(chatsWrap, chatsClearAction);
        chatsClearAction = View.NO_ID;
      }
      chatsClearLabel = clearLabel;
      if (clearLabel != null) {
        chatsClearAction = ViewCompat.addAccessibilityAction(chatsWrap, clearLabel, (view, arguments) -> {
          if (listener != null && chatsChoice != null) {
            listener.onChatsCleared();
            return true;
          }
          return false;
        });
      }
    }
  }

  /**
   * New counts per filter (FILTER_*); a chip shows its count only when it is above 0. Every bind checks the labels
   * against the current language too (a language change rebinds the row without making it again).
   */
  public void setCounts (@NonNull int[] newCounts) {
    for (int i = 0; i < counts.length && i < newCounts.length; i++) {
      counts[i] = newCounts[i];
    }
    relabel();
  }

  /** Every chip's label (with its count) in the current language; only the ones that differ are set. */
  public void relabel () {
    applyDirection();
    for (int i = 0; i < filters.length; i++) {
      String label = Lang.getString(LABELS[i]);
      setLabel(filters[i], i != FILTER_ALL && counts[i] > 0 ? Lang.getString(R.string.XgramActivityChipCount, label, counts[i]) : label);
    }
    setLabel(unread, Lang.getString(R.string.XgramActivityUnread));
    applyChats();
  }

  private static void setLabel (XgramM3Sessions.ChipView chip, String label) {
    if (!label.contentEquals(chip.getText())) {
      chip.setText(label);
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
    chats.invalidate();
  }

  /**
   * The Chats chip: an M3 filter chip with a trailing icon (FilterChipTokens: 32dp, 8dp corners, labelLarge, 18dp
   * icons 8dp from the end). All chats: an outline (outlineVariant) and a dropdown arrow; chosen chats: the
   * secondaryContainer fill and a clear (x). The two cross on EFFECTS_FAST, the (x) springs in (SPATIAL_FAST).
   */
  private static final class ChatsChipView extends NoScrollTextView {
    private static final float SPACE = 16f, ICON = 18f, ICON_END = 8f, ICON_GAP = 8f, MAX_WIDTH = 200f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    @Nullable private final Drawable arrow, clear;
    private boolean chosen, bound;
    private final XgramExpressive.Spring chosenSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
    private final XgramExpressive.Spring clearSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());

    ChatsChipView (Context context) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setMaxWidth(Screen.dp(MAX_WIDTH));
      setGravity(Gravity.CENTER_VERTICAL);
      arrow = Drawables.get(getResources(), XgramIconPack.map(R.drawable.baseline_keyboard_arrow_down_20));
      clear = Drawables.get(getResources(), XgramIconPack.map(R.drawable.baseline_close_18));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // The state layer goes over the container this view draws itself
        XgramExpressive.MorphDrawable mask = new XgramExpressive.MorphDrawable(0xff000000, XgramExpressive.CORNER_S, Float.NaN);
        setForeground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE_VARIANT, null, mask));
      }
      final int start = Screen.dp(SPACE), end = Screen.dp(ICON_END + ICON + ICON_GAP);
      final boolean rtl = Lang.rtl();
      setPadding(rtl ? end : start, 0, rtl ? start : end, 0);
    }

    void setChosen (boolean chosen) {
      if (this.chosen == chosen && bound) {
        return;
      }
      // Animated only for a change on screen (a new row snaps to its state)
      final boolean animated = bound && getWindowToken() != null && getWidth() > 0;
      this.chosen = chosen;
      this.bound = true;
      if (animated) {
        chosenSpring.animateTo(chosen ? 1f : 0f);
        if (chosen) {
          clearSpring.setValue(0f);
          clearSpring.animateTo(1f);
        }
      } else {
        chosenSpring.setValue(chosen ? 1f : 0f);
        clearSpring.setValue(chosen ? 1f : 0f);
      }
      invalidate();
    }

    /** {@code x} (in this view) is on the trailing clear (x): its icon, its gaps, and as wide as a 40dp target. */
    boolean isClearHit (float x) {
      if (x < 0f || getWidth() <= 0) {
        return false;
      }
      final float area = Math.max(Screen.dp(40f), Screen.dp(ICON_END + ICON + ICON_GAP));
      return Lang.rtl() ? x <= area : x >= getWidth() - area;
    }

    @Override
    protected void onDraw (Canvas c) {
      // 0 = all chats (outline, arrow), 1 = chosen (filled, clear): the springs cross the two
      final float on = me.vkryl.core.MathUtils.clamp(chosenSpring.getValue());
      final int fill = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
      final int outline = XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT);
      final int content = me.vkryl.core.ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT),
        XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), on);
      final float radius = Screen.dpf(XgramExpressive.CORNER_S);
      if (on < 1f) {
        final float stroke = Screen.dpf(1f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(XgramExpressive.withAlpha(outline, 1f - on));
        rect.set(stroke / 2f, stroke / 2f, getWidth() - stroke / 2f, getHeight() - stroke / 2f);
        c.drawRoundRect(rect, radius - stroke / 2f, radius - stroke / 2f, paint);
      }
      if (on > 0f) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(XgramExpressive.withAlpha(fill, on));
        rect.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(rect, radius, radius, paint);
      }
      // The trailing icon: the arrow fades out as the clear (x) springs in
      final float size = Screen.dpf(ICON);
      final float centerX = Lang.rtl() ? Screen.dpf(ICON_END) + size / 2f : getWidth() - Screen.dpf(ICON_END) - size / 2f;
      final float centerY = getHeight() / 2f;
      if (arrow != null && on < 1f) {
        Drawables.draw(c, arrow, centerX - arrow.getMinimumWidth() / 2f, centerY - arrow.getMinimumHeight() / 2f,
          Paints.getPorterDuffPaint(XgramExpressive.withAlpha(content, 1f - on)));
      }
      if (clear != null && on > 0f) {
        final float scale = Math.max(0f, clearSpring.getValue());
        final int save = c.save();
        c.scale(scale, scale, centerX, centerY);
        Drawables.draw(c, clear, centerX - clear.getMinimumWidth() / 2f, centerY - clear.getMinimumHeight() / 2f,
          Paints.getPorterDuffPaint(XgramExpressive.withAlpha(content, on)));
        c.restoreToCount(save);
      }
      if (getCurrentTextColor() != content) {
        setTextColor(content);
      }
      super.onDraw(c);
    }
  }
}
