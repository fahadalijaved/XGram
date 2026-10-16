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
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.MaterialEditText;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

/**
 * XGram M3E: the New poll / New quiz page (CreatePollController) as one Settings-style page. Its question, options and
 * description are filled text fields whose container is the list tile itself (surfaceBright, the 16dp inset and the
 * 20/4dp segment corners of the shared list decoration, which sets each tile's segment), so the page is one surface
 * with tiles on it, like the switches below them:
 * <ul>
 *   <li>Question and description: bodyLarge input, the floating label in labelMedium, the length counter as the
 *   supporting text at the bottom end of the tile, a 2dp primary active indicator while focused.</li>
 *   <li>Options: 56dp rows with a leading drag handle (reorders the options) and a trailing remove icon button;
 *   the quiz answer check box sits between the handle and the text.</li>
 *   <li>Add an option: a list row with a leading add icon, pressed like Settings rows.</li>
 * </ul>
 * Views are styled once when created and per bind; nothing here runs per frame.
 */
public final class XgramM3PollCreate {
  private XgramM3PollCreate () { }

  /** What the page does for its rows. */
  public interface Host {
    int optionCount ();

    /** Moves the option at adapter position {@code fromPosition} to {@code toPosition} (both options); false: no move. */
    boolean moveOption (int fromPosition, int toPosition);

    /** The remove button of the option row {@code row} was tapped. */
    void removeOption (View row);
  }

  private static final float ROW_HEIGHT = 56f, BUTTON_SIZE = 48f, BUTTON_MARGIN = 4f, ICON_BUTTON_STATE = 40f;
  /** Option text starts after the handle (16dp icon inset + 24dp icon + 16dp) and ends before the remove button. */
  private static final float TEXT_START = 56f, TEXT_END = 52f, TEXT_INSET = 16f;
  /** Room Telegram X's remaining-characters counter (shown near the limit) keeps at the end of an option. */
  private static final float COUNTER_SPACE = 32f;
  /** The quiz answer check box: Telegram X's 56dp box moved past the handle (it slides the text 40dp further). */
  private static final float QUIZ_CHECK_START = 40f;
  /** MaterialEditTextGroup floats its label at this scale: the label's size that floats to labelMedium. */
  private static final float LABEL_FLOATED_SCALE = 1f - .23076923f;

  // Creation

  /** From SettingsAdapter.createModifiedView: the M3E look of the page's input rows (Telegram X's views, restyled). */
  public static void styleRow (@Nullable ViewController<?> themeProvider, View row, int viewType, Host host) {
    if (!(row instanceof FrameLayout)) {
      return;
    }
    FrameLayout frame = (FrameLayout) row;
    if (frame.getChildCount() == 0 || !(frame.getChildAt(0) instanceof MaterialEditTextGroup)) {
      return;
    }
    MaterialEditTextGroup group = (MaterialEditTextGroup) frame.getChildAt(0);
    switch (viewType) {
      case ListItem.TYPE_EDITTEXT:
      case ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE:
        styleField(frame, group);
        break;
      case ListItem.TYPE_EDITTEXT_POLL_OPTION:
        styleOption(themeProvider, frame, group, host);
        break;
      case ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD:
        styleAdd(themeProvider, frame, group);
        break;
    }
  }

  /** The tile is the row's own background, so it follows the row (alpha, drag elevation); the decoration sets its segment. */
  private static void setTile (View row) {
    XgramExpressive.MorphDrawable tile = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, Float.NaN);
    tile.setColorRole(XgramExpressive.SURFACE_BRIGHT);
    tile.setHost(row);
    row.setBackground(tile);
  }

  /** Question, description and quiz explanation. */
  private static void styleField (FrameLayout frame, MaterialEditTextGroup group) {
    setTile(frame);
    frame.setPadding(0, Screen.dp(4f), 0, Screen.dp(8f));
    group.setWillNotDraw(true); // the tile is the field's container (no second filled box inside it)

    MaterialEditText input = group.getEditText();
    XgramExpressive.applyText(input, XgramExpressive.TS_BODY_L, false);
    // Inset with margins rather than padding, so the active indicator runs under the text only
    ViewGroup.LayoutParams params = input.getLayoutParams();
    if (params instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
      margins.leftMargin = margins.rightMargin = Screen.dp(TEXT_INSET);
      input.setLayoutParams(margins);
      input.setPadding(0, input.getPaddingTop(), 0, input.getPaddingBottom());
    }

    TextView label = labelOf(group);
    if (label != null) {
      XgramExpressive.applyText(label, XgramExpressive.TS_LABEL_M, false);
      label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, XgramExpressive.textSizeSp(XgramExpressive.TS_LABEL_M) / LABEL_FLOATED_SCALE);
    }
  }

  @Nullable
  private static TextView labelOf (MaterialEditTextGroup group) {
    MaterialEditText input = group.getEditText();
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child instanceof TextView && child != input) {
        return (TextView) child;
      }
    }
    return null;
  }

  private static void styleOption (@Nullable ViewController<?> themeProvider, FrameLayout frame, MaterialEditTextGroup group, Host host) {
    final boolean rtl = Lang.rtl();
    setTile(frame);
    frame.setMinimumHeight(Screen.dp(ROW_HEIGHT));
    group.setWillNotDraw(true);

    ViewGroup.LayoutParams params = group.getLayoutParams();
    if (params instanceof FrameLayout.LayoutParams) {
      FrameLayout.LayoutParams groupParams = (FrameLayout.LayoutParams) params;
      groupParams.leftMargin = Screen.dp(rtl ? TEXT_END : TEXT_START);
      groupParams.rightMargin = Screen.dp(rtl ? TEXT_START : TEXT_END);
      groupParams.topMargin = groupParams.bottomMargin = 0;
      groupParams.gravity = Gravity.CENTER_VERTICAL;
      group.setLayoutParams(groupParams);
    }

    MaterialEditText input = group.getEditText();
    XgramExpressive.applyText(input, XgramExpressive.TS_BODY_L, false);
    ViewGroup.LayoutParams inputParams = input.getLayoutParams();
    if (inputParams instanceof ViewGroup.MarginLayoutParams) {
      ((ViewGroup.MarginLayoutParams) inputParams).topMargin = 0; // centered in the 56dp row
      input.setLayoutParams(inputParams);
    }
    final int counterSpace = Screen.dp(COUNTER_SPACE);
    input.setPadding(rtl ? counterSpace : 0, input.getPaddingTop(), rtl ? 0 : counterSpace, input.getPaddingBottom());
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child instanceof TextView && child != input && child.getLayoutParams() instanceof FrameLayout.LayoutParams) {
        // The remaining-characters counter, on the text's line
        FrameLayout.LayoutParams counterParams = (FrameLayout.LayoutParams) child.getLayoutParams();
        counterParams.topMargin = 0;
        counterParams.gravity = (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
        child.setLayoutParams(counterParams);
      }
    }

    Context context = frame.getContext();
    ImageView handle = newIcon(themeProvider, context, R.drawable.baseline_drag_handle_24, ColorId.icon, rtl ? Gravity.RIGHT : Gravity.LEFT);
    handle.setId(R.id.xgram_m3e_poll_drag);
    handle.setContentDescription(Lang.getString(R.string.XgramM3PollReorderOption));
    frame.addView(handle);

    ImageView remove = newIcon(themeProvider, context, R.drawable.baseline_close_24, ColorId.icon, rtl ? Gravity.LEFT : Gravity.RIGHT);
    remove.setId(R.id.xgram_m3e_poll_remove);
    remove.setContentDescription(Lang.getString(R.string.XgramM3PollRemoveOption));
    XgramExpressive.attachPressMorph(remove, 0, XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_FULL).setSize(ICON_BUTTON_STATE, ICON_BUTTON_STATE, Gravity.CENTER);
    remove.setOnClickListener(v -> host.removeOption(frame));
    frame.addView(remove);
  }

  @SuppressLint("ClickableViewAccessibility")
  private static void styleAdd (@Nullable ViewController<?> themeProvider, FrameLayout frame, MaterialEditTextGroup group) {
    final boolean rtl = Lang.rtl();
    ViewGroup.LayoutParams rowParams = frame.getLayoutParams();
    if (rowParams != null) {
      rowParams.height = Screen.dp(ROW_HEIGHT);
      frame.setLayoutParams(rowParams);
    }
    // Telegram X's field stays (focusing it adds an option) but shows nothing: the row's own label says it
    group.setWillNotDraw(true);
    group.setEmptyHint(0);

    // A Settings row: the tile with the pressed shape and state layer. Telegram X's click target on top of the field
    // presses the row (its states and touch point go to the tile).
    XgramExpressive.MorphDrawable tile = XgramExpressive.attachPressMorph(frame, XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT), XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
    tile.setColorRole(XgramExpressive.SURFACE_BRIGHT);
    frame.setAddStatesFromChildren(true);
    for (int i = 1; i < frame.getChildCount(); i++) {
      View target = frame.getChildAt(i);
      if (target.getId() == R.id.optionAdd) {
        target.setBackground(null);
        target.setContentDescription(Lang.getString(R.string.XgramM3PollAddOption));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          target.setOnTouchListener((v, e) -> {
            frame.drawableHotspotChanged(e.getX() + v.getLeft(), e.getY() + v.getTop());
            return false;
          });
        }
      }
    }

    Context context = frame.getContext();
    frame.addView(newIcon(themeProvider, context, R.drawable.baseline_add_24, ColorId.iconActive, rtl ? Gravity.RIGHT : Gravity.LEFT));

    TextView label = new TextView(context);
    XgramExpressive.applyText(label, XgramExpressive.TS_BODY_L, false);
    label.setTextColor(Theme.getColor(ColorId.textNeutral));
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(label, ColorId.textNeutral);
    }
    label.setSingleLine(true);
    label.setEllipsize(TextUtils.TruncateAt.END);
    label.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
    label.setText(Lang.getString(R.string.XgramM3PollAddOption));
    label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); // the click target says it
    FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    labelParams.leftMargin = Screen.dp(rtl ? TEXT_INSET : TEXT_START);
    labelParams.rightMargin = Screen.dp(rtl ? TEXT_START : TEXT_INSET);
    label.setLayoutParams(labelParams);
    frame.addView(label);
  }

  /** A 48dp icon (24dp drawn) at the start or end of a row, its 24dp icon 16dp from the tile's edge. */
  private static ImageView newIcon (@Nullable ViewController<?> themeProvider, Context context, int iconRes, @ColorId int colorId, int horizontalGravity) {
    ImageView view = new ImageView(context);
    view.setScaleType(ImageView.ScaleType.CENTER);
    view.setImageResource(XgramIconPack.map(iconRes));
    view.setColorFilter(Theme.getColor(colorId));
    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(view, colorId);
    }
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Screen.dp(BUTTON_SIZE), Screen.dp(BUTTON_SIZE), horizontalGravity | Gravity.CENTER_VERTICAL);
    params.leftMargin = params.rightMargin = Screen.dp(BUTTON_MARGIN);
    view.setLayoutParams(params);
    return view;
  }

  // Binding

  /** From modifyEditText, after Telegram X's own setup of the row. */
  public static void bindRow (View row, int itemId, int optionCount, boolean isQuiz) {
    if (!(row instanceof ViewGroup) || ((ViewGroup) row).getChildCount() == 0) {
      return;
    }
    ViewGroup frame = (ViewGroup) row;
    View first = frame.getChildAt(0);
    if (!(first instanceof MaterialEditTextGroup)) {
      return;
    }
    syncCheckbox(frame, itemId, isQuiz);
    if (itemId == R.id.option) {
      updateRowButtons(frame, optionCount);
    }
    if (itemId != R.id.optionAdd) {
      MaterialEditTextGroup group = (MaterialEditTextGroup) first;
      group.getEditText().setLineDisabled(!group.hasFocusInternal());
    }
  }

  /**
   * Telegram X's check box of the row: an option's quiz answer box sits past the handle and takes taps only while it
   * is shown (hidden, it would cover the handle); the add row and the question never show theirs.
   */
  public static void syncCheckbox (View row, boolean isQuiz) {
    Object tag = row.getTag();
    if (row instanceof ViewGroup && tag instanceof ListItem) {
      syncCheckbox((ViewGroup) row, ((ListItem) tag).getId(), isQuiz);
    }
  }

  private static void syncCheckbox (ViewGroup row, int itemId, boolean isQuiz) {
    CheckBoxView box = checkboxOf(row);
    if (box == null) {
      return;
    }
    if (itemId == R.id.option) {
      ViewGroup.LayoutParams params = box.getLayoutParams();
      int margin = Screen.dp(Lang.rtl() ? TEXT_END : QUIZ_CHECK_START);
      if (params instanceof FrameLayout.LayoutParams && ((FrameLayout.LayoutParams) params).leftMargin != margin) {
        ((FrameLayout.LayoutParams) params).leftMargin = margin;
        box.setLayoutParams(params);
      }
      if (box.isClickable() != isQuiz) {
        box.setClickable(isQuiz);
      }
    } else if (itemId == R.id.optionAdd || itemId == R.id.title) {
      if (box.getVisibility() != View.GONE) {
        box.setVisibility(View.GONE);
      }
    }
  }

  @Nullable
  private static CheckBoxView checkboxOf (ViewGroup row) {
    for (int i = row.getChildCount() - 1; i > 0; i--) {
      View child = row.getChildAt(i);
      if (child instanceof CheckBoxView) {
        return (CheckBoxView) child;
      }
    }
    return null;
  }

  /** The last option can't be removed or moved: its remove button hides and its handle dims. */
  private static void updateRowButtons (ViewGroup row, int optionCount) {
    final boolean several = optionCount > 1;
    View remove = row.findViewById(R.id.xgram_m3e_poll_remove);
    if (remove != null) {
      int visibility = several ? View.VISIBLE : View.INVISIBLE;
      if (remove.getVisibility() != visibility) {
        remove.setVisibility(visibility);
      }
    }
    View handle = row.findViewById(R.id.xgram_m3e_poll_drag);
    if (handle != null) {
      float alpha = several ? 1f : XgramExpressive.DISABLED_CONTENT;
      if (handle.getAlpha() != alpha) {
        handle.setAlpha(alpha);
      }
    }
  }

  /** After an option was added or removed: the option rows on screen. */
  public static void updateButtons (@Nullable RecyclerView recyclerView, int optionCount) {
    if (recyclerView == null) {
      return;
    }
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View child = recyclerView.getChildAt(i);
      if (child instanceof ViewGroup && isOption(child)) {
        updateRowButtons((ViewGroup) child, optionCount);
      }
    }
  }

  /** Focus: the 2dp primary active indicator under the focused field's text (none at rest, the tile is the field). */
  public static void onFocusChanged (MaterialEditTextGroup group, boolean isFocused) {
    ViewParent parent = group.getParent();
    if (parent instanceof View && ((View) parent).getId() == R.id.optionAdd) {
      return;
    }
    group.getEditText().setLineDisabled(!isFocused);
  }

  private static boolean isOption (View row) {
    Object tag = row.getTag();
    return tag instanceof ListItem && ((ListItem) tag).getId() == R.id.option;
  }

  // Reordering

  /** Options move by their handle: a touch on it picks the row up at once (no long press, the field keeps its taps). */
  public static void attachReorder (RecyclerView recyclerView, Host host) {
    ItemTouchHelper helper = new ItemTouchHelper(new ReorderCallback(host));
    helper.attachToRecyclerView(recyclerView); // first, so it has seen the touch down when the handle starts the drag
    recyclerView.addOnItemTouchListener(new HandleListener(helper, host));
  }

  private static final class ReorderCallback extends ItemTouchHelper.Callback {
    private final Host host;

    ReorderCallback (Host host) {
      this.host = host;
    }

    @Override
    public boolean isLongPressDragEnabled () {
      return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled () {
      return false;
    }

    @Override
    public int getMovementFlags (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
      return isOption(viewHolder.itemView) && host.optionCount() > 1 ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
    }

    @Override
    public boolean canDropOver (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
      return isOption(target.itemView);
    }

    @Override
    public boolean onMove (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
      int from = viewHolder.getBindingAdapterPosition(), to = target.getBindingAdapterPosition();
      return from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION && host.moveOption(from, to);
    }

    @Override
    public void onSwiped (@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

    @Override
    public void onSelectedChanged (@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
      super.onSelectedChanged(viewHolder, actionState);
      if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
        setDragged(viewHolder.itemView, true);
      }
    }

    @Override
    public void clearView (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
      super.clearView(recyclerView, viewHolder);
      setDragged(viewHolder.itemView, false);
    }
  }

  /** The dragged option: lifted (elevation 3) with every corner at the pressed 16dp, settling back on drop. */
  private static void setDragged (View row, boolean dragged) {
    XgramExpressive.MorphDrawable tile = XgramExpressive.findMorph(row.getBackground());
    if (tile != null) {
      tile.setCornerTarget(dragged ? XgramExpressive.LIST_PRESSED_CORNER : Float.NaN, true);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      row.setTranslationZ(dragged ? Screen.dp(XgramExpressive.ELEVATION_3) : 0f);
    }
    if (dragged) {
      row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
  }

  private static final class HandleListener extends RecyclerView.SimpleOnItemTouchListener {
    private final ItemTouchHelper helper;
    private final Host host;

    HandleListener (ItemTouchHelper helper, Host host) {
      this.helper = helper;
      this.host = host;
    }

    @Override
    public boolean onInterceptTouchEvent (@NonNull RecyclerView recyclerView, @NonNull MotionEvent e) {
      if (e.getActionMasked() != MotionEvent.ACTION_DOWN || host.optionCount() < 2) {
        return false;
      }
      View row = recyclerView.findChildViewUnder(e.getX(), e.getY());
      if (!(row instanceof ViewGroup) || !isOption(row)) {
        return false;
      }
      View handle = row.findViewById(R.id.xgram_m3e_poll_drag);
      if (handle == null || handle.getVisibility() != View.VISIBLE) {
        return false;
      }
      float x = e.getX() - row.getLeft() - row.getTranslationX();
      if (x < handle.getLeft() || x >= handle.getRight()) {
        return false;
      }
      CheckBoxView box = checkboxOf((ViewGroup) row);
      if (box != null && box.isClickable() && box.getVisibility() == View.VISIBLE && box.getAlpha() > 0f &&
        x >= box.getLeft() + box.getTranslationX() && x < box.getRight() + box.getTranslationX()) {
        return false; // the quiz answer check box next to the handle takes the tap
      }
      RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(row);
      if (holder != null) {
        helper.startDrag(holder);
      }
      return false;
    }
  }
}
