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
package com.fahadali.xgram;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.util.StringList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.collection.IntList;

/**
 * The user's own order of XGram's item lists (side menu, message menu, chat long-press actions): dragged by a handle in
 * their settings, stored as stable keys. Items missing from the stored order (new ones) keep their default place.
 */
public final class XgramReorder {
  private XgramReorder () { }

  public static final String DRAWER = "drawer", MESSAGE_MENU = "menu", CHAT_ACTIONS = "chat_actions";
  /** Stored as "xgram_order_" + list, a comma separated list of keys (included in the XGram backup). */
  public static final String KEY_PREFIX = "xgram_order_";

  public interface KeyOf<T> {
    @Nullable String keyOf (T item);
  }

  public static List<String> saved (String list) {
    List<String> keys = new ArrayList<>();
    String value = Settings.instance().getString(KEY_PREFIX + list, "");
    if (value != null) {
      for (String key : value.split(",")) {
        if (!key.isEmpty()) {
          keys.add(key);
        }
      }
    }
    return keys;
  }

  public static boolean hasOrder (String list) {
    return !saved(list).isEmpty();
  }

  public static void save (String list, List<String> keys) {
    StringBuilder b = new StringBuilder();
    for (String key : keys) {
      if (b.length() > 0) {
        b.append(',');
      }
      b.append(key);
    }
    if (b.length() == 0) {
      reset(list);
    } else {
      Settings.instance().putString(KEY_PREFIX + list, b.toString());
    }
  }

  public static void reset (String list) {
    Settings.instance().remove(KEY_PREFIX + list);
  }

  /**
   * Puts the items that have a stored place into the user's order, using only the slots such items already take,
   * so an item without a stored place (or with a null key) stays where it is.
   */
  public static <T> void sort (String list, List<T> items, KeyOf<T> keyOf) {
    List<String> order = saved(list);
    if (order.isEmpty() || items.size() < 2) {
      return;
    }
    Map<String, Integer> rank = new HashMap<>();
    for (int i = 0; i < order.size(); i++) {
      rank.put(order.get(i), i);
    }
    List<Integer> slots = new ArrayList<>();
    List<T> ranked = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      String key = keyOf.keyOf(items.get(i));
      if (key != null && rank.containsKey(key)) {
        slots.add(i);
        ranked.add(items.get(i));
      }
    }
    if (ranked.size() < 2) {
      return;
    }
    //noinspection ConstantConditions
    Collections.sort(ranked, (a, b) -> Integer.compare(rank.get(keyOf.keyOf(a)), rank.get(keyOf.keyOf(b))));
    for (int i = 0; i < slots.size(); i++) {
      items.set(slots.get(i), ranked.get(i));
    }
  }

  /** The same for a Telegram X option list (one id, icon and name per option); keyOfId gives each option id's key. */
  public static void sort (String list, IntList ids, IntList icons, StringList strings, KeyOf<Integer> keyOfId) {
    int count = ids.size();
    String[] names = strings.get();
    if (count < 2 || icons.size() != count || names.length != count) {
      return;
    }
    List<Integer> order = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      order.add(i);
    }
    sort(list, order, index -> keyOfId.keyOf(ids.get(index)));
    boolean changed = false;
    for (int i = 0; i < count; i++) {
      if (order.get(i) != i) {
        changed = true;
        break;
      }
    }
    if (!changed) {
      return;
    }
    int[] idArray = ids.get().clone(), iconArray = icons.get().clone(); // get() may hand out the list's own array
    ids.clear();
    icons.clear();
    strings.clear();
    for (int index : order) {
      ids.append(idArray[index]);
      icons.append(iconArray[index]);
      strings.append(names[index]);
    }
  }

  /** Default keys (in their default order) as the user ordered them, for the settings lists. */
  public static List<String> ordered (String list, String[] defaultKeys) {
    List<String> keys = new ArrayList<>(defaultKeys.length);
    Collections.addAll(keys, defaultKeys);
    sort(list, keys, key -> key);
    return keys;
  }

  // Settings screens

  /** Drag handle, long press and separators for a settings list whose rows can be reordered. */
  public static final class Ui implements View.OnTouchListener {
    private final RecyclerView recyclerView;
    private final SettingsAdapter adapter;
    private final String list;
    private final KeyOf<ListItem> keyOf, groupOf;
    private final Runnable onChanged;
    private final ItemTouchHelper helper;
    private final HandleModifier handle = new HandleModifier();

    /**
     * @param keyOf the stored key of a row that can be moved, null for any other row
     * @param groupOf rows move only among rows of the same group (null: one group)
     * @param onChanged after the user dropped a row in a new place (the order is saved by then)
     */
    public Ui (RecyclerView recyclerView, SettingsAdapter adapter, String list, KeyOf<ListItem> keyOf, @Nullable KeyOf<ListItem> groupOf, @Nullable Runnable onChanged) {
      this.recyclerView = recyclerView;
      this.adapter = adapter;
      this.list = list;
      this.keyOf = keyOf;
      this.groupOf = groupOf;
      this.onChanged = onChanged;
      RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
      if (itemAnimator instanceof SimpleItemAnimator) {
        ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
      }
      this.helper = RemoveHelper.attach(recyclerView, new Callback());
      recyclerView.addItemDecoration(new Separators());
    }

    /** From setValuedSetting (or any bind): rows that can move get the handle, recycled ones lose it. */
    public void bind (ListItem item, SettingView view) {
      boolean movable = keyOf(item) != null;
      view.setDrawModifier(movable ? handle : null);
      view.setOnTouchListener(movable ? this : null);
    }

    @Nullable
    private String keyOf (@Nullable Object tag) {
      return tag instanceof ListItem ? keyOf.keyOf((ListItem) tag) : null;
    }

    @Nullable
    private String groupOf (Object tag) {
      return groupOf != null && tag instanceof ListItem ? groupOf.keyOf((ListItem) tag) : null;
    }

    private boolean sameGroup (Object a, Object b) {
      String ga = groupOf(a), gb = groupOf(b);
      return ga == null ? gb == null : ga.equals(gb);
    }

    private void saveOrder () {
      List<String> keys = new ArrayList<>();
      for (ListItem item : adapter.getItems()) {
        String key = keyOf(item);
        if (key != null) {
          keys.add(key);
        }
      }
      save(list, keys);
      if (onChanged != null) {
        onChanged.run();
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch (View view, MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN && view instanceof SettingView && handle.isOnHandle((SettingView) view, event.getX())) {
        RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(view);
        if (holder != null) {
          helper.startDrag(holder);
        }
      }
      return false;
    }

    private final class Callback implements RemoveHelper.ExtendedCallback {
      @Override
      public boolean isLongPressDragEnabled () {
        return true;
      }

      @Override
      public int makeDragFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        return keyOf(viewHolder.itemView.getTag()) != null ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
      }

      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        return false;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) { }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
        int from = source.getAbsoluteAdapterPosition();
        int to = target.getAbsoluteAdapterPosition();
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || !canDropOver(recyclerView, source, target)) {
          return false;
        }
        adapter.moveItem(from, to, true);
        return true;
      }

      @Override
      public boolean canDropOver (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
        Object a = current.itemView.getTag(), b = target.itemView.getTag();
        return keyOf(a) != null && keyOf(b) != null && sameGroup(a, b);
      }

      @Override
      public void onCompleteMovement (int fromPosition, int toPosition) {
        saveOrder();
      }
    }

    /** Lines between movable rows (separator rows would be dragged along), not under a row being dragged. */
    private final class Separators extends RecyclerView.ItemDecoration {
      @Override
      public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean rtl = Lang.rtl();
        int height = Math.max(1, Screen.dp(.5f));
        Paint paint = Paints.fillingPaint(Theme.separatorColor());
        for (int i = 0; i < parent.getChildCount(); i++) {
          View child = parent.getChildAt(i);
          if (keyOf(child.getTag()) == null || child.getTranslationY() != 0f) {
            continue;
          }
          int position = parent.getChildLayoutPosition(child);
          if (position == RecyclerView.NO_POSITION) {
            continue;
          }
          RecyclerView.ViewHolder next = parent.findViewHolderForLayoutPosition(position + 1);
          if (next == null || keyOf(next.itemView.getTag()) == null || next.itemView.getTranslationY() != 0f || !sameGroup(child.getTag(), next.itemView.getTag())) {
            continue;
          }
          int start = child instanceof SettingView ? (int) ((SettingView) child).getMeasuredNameStart() : Screen.dp(16f);
          int bottom = child.getBottom();
          if (rtl) {
            c.drawRect(0, bottom - height, child.getWidth() - start, bottom, paint);
          } else {
            c.drawRect(start, bottom - height, child.getWidth(), bottom, paint);
          }
        }
      }
    }
  }

  /** The drag handle, drawn at the end of the row (before its toggle when it has one). */
  private static final class HandleModifier implements DrawModifier {
    private Drawable drawable;

    private Drawable drawable () {
      if (drawable == null) {
        drawable = Drawables.get(R.drawable.baseline_drag_handle_24);
      }
      return drawable;
    }

    private static boolean hasToggler (SettingView view) {
      return view.getToggler() != null && view.getToggler().getVisibility() == View.VISIBLE;
    }

    private int x (SettingView view) {
      int size = drawable().getIntrinsicWidth();
      int end = hasToggler(view) ? Screen.dp(66f) + Screen.dp(4f) : Screen.dp(16f);
      return Lang.rtl() ? end : view.getWidth() - end - size;
    }

    boolean isOnHandle (SettingView view, float touchX) {
      int x = x(view);
      return touchX >= x - Screen.dp(12f) && touchX <= x + drawable().getIntrinsicWidth() + Screen.dp(12f);
    }

    @Override
    public void afterDraw (View view, Canvas c) {
      if (view instanceof SettingView) {
        Drawable d = drawable();
        Drawables.draw(c, d, x((SettingView) view), (view.getHeight() - d.getIntrinsicHeight()) / 2f, PorterDuffPaint.get(ColorId.icon));
      }
    }

    @Override
    public int getWidth () {
      return drawable().getIntrinsicWidth() + Screen.dp(12f);
    }
  }
}
