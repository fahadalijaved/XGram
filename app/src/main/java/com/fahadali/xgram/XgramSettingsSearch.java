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

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.CustomTypefaceSpan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Settings search (NagramX). The index is read from the pages' own row lists
 * ({@link XgramSettingsController#buildSectionItems}), so a row is searchable as soon as a page shows it.
 */
final class XgramSettingsSearch {
  private XgramSettingsSearch () { }

  /** One searchable row: which page holds it, and what a result needs to draw it. */
  static final class Entry {
    final int section;
    final @IdRes int id;
    final boolean isToggle;
    final int iconRes, textColorId;
    final String title, titleLower, path;

    Entry (int section, @IdRes int id, boolean isToggle, int iconRes, int textColorId, String title, String path) {
      this.section = section;
      this.id = id;
      this.isToggle = isToggle;
      this.iconRes = iconRes;
      this.textColorId = textColorId;
      this.title = title;
      this.titleLower = title.toLowerCase();
      this.path = path;
    }
  }

  /** A few dozen rows: built when search opens, so it follows the current language. */
  static List<Entry> buildIndex (XgramSettingsController controller) {
    List<Entry> entries = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (int section = XgramSettingsController.SECTION_MAIN; section <= XgramSettingsController.SECTION_LAST; section++) {
      String sectionName = Lang.getString(XgramSettingsController.sectionTitleRes(section));
      int sectionIcon = XgramSettingsController.sectionIcon(section);
      String group = null;
      for (ListItem item : controller.buildSectionItems(section)) {
        int viewType = item.getViewType();
        if (viewType == ListItem.TYPE_HEADER) {
          CharSequence header = item.getString();
          group = header != null && header.length() > 0 ? header.toString() : null;
          continue;
        }
        if (viewType == ListItem.TYPE_SHADOW_BOTTOM) {
          group = null; // a header names only the card below it
          continue;
        }
        if (item.getId() == 0 || !isSearchable(viewType)) {
          continue;
        }
        CharSequence title = item.getString();
        if (title == null || title.length() == 0 || !seen.add(item.getId())) {
          continue;
        }
        String path = group != null ? Lang.getString(R.string.XgramSettingsSearchPath, sectionName, group) : sectionName;
        boolean isToggle = viewType == ListItem.TYPE_RADIO_SETTING || viewType == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER;
        int icon = item.getIconResource() != 0 ? item.getIconResource() : sectionIcon;
        entries.add(new Entry(section, item.getId(), isToggle, icon, item.getTextColorId(ColorId.NONE), title.toString(), path));
      }
    }
    return entries;
  }

  private static boolean isSearchable (int viewType) {
    switch (viewType) {
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
        return true;
    }
    return false;
  }

  /** Titles that contain every word (any case); titles starting with the first word come first. */
  static List<ListItem> buildResults (List<Entry> index, String query) {
    List<String> words = words(query);
    List<Entry> found = new ArrayList<>(), more = new ArrayList<>();
    if (!words.isEmpty()) {
      for (Entry e : index) {
        if (containsAll(e.titleLower, words)) {
          (startsWithWord(e.titleLower, words.get(0)) ? found : more).add(e);
        }
      }
    }
    found.addAll(more);
    List<ListItem> items = new ArrayList<>(found.size() * 2 + 3);
    if (found.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NothingFound));
      return items;
    }
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < found.size(); i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(resultItem(found.get(i), words));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  // StringTokenizer instead of a regex split: no backslashes to lose when editing through the shell.
  private static List<String> words (String query) {
    List<String> words = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(query.toLowerCase());
    while (tokenizer.hasMoreTokens()) {
      words.add(tokenizer.nextToken());
    }
    return words;
  }

  private static boolean containsAll (String title, List<String> words) {
    for (String word : words) {
      if (!title.contains(word)) {
        return false;
      }
    }
    return true;
  }

  private static boolean startsWithWord (String title, String word) {
    return title.startsWith(word) || title.contains(" " + word);
  }

  private static ListItem resultItem (Entry e, List<String> words) {
    int viewType = e.isToggle ? ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER : ListItem.TYPE_VALUED_SETTING_COMPACT;
    ListItem item = new ListItem(viewType, e.id, e.iconRes, highlight(e, words)).setData(e);
    if (e.textColorId != ColorId.NONE) {
      item.setTextColorId(e.textColorId);
    }
    return item;
  }

  private static CharSequence highlight (Entry e, List<String> words) {
    if (e.titleLower.length() != e.title.length()) {
      return e.title; // lower-casing changed the length: don't mark
    }
    SpannableStringBuilder b = new SpannableStringBuilder(e.title);
    for (String word : words) {
      int from = 0, index;
      while ((index = e.titleLower.indexOf(word, from)) != -1) {
        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), ColorId.textLink), index, index + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        from = index + word.length();
      }
    }
    return b;
  }

  /**
   * Last step of the page adapter's setValuedSetting. A result shows its path, not its value.
   * A row locked by another switch stays tappable (it opens its page) with a greyed switch.
   */
  static void bindResult (ListItem item, SettingView view, boolean isUpdate) {
    if (!(item.getData() instanceof Entry)) {
      return;
    }
    view.setData(((Entry) item.getData()).path);
    boolean locked = !view.isEnabled();
    item.setBoolValue(locked);
    if (locked) {
      view.setEnabled(true);
    }
    if (view.getToggler() != null) {
      view.getToggler().setDisabled(locked, isUpdate);
    }
  }

  /** Scrolls a row into view and flashes it, so a setting opened from search is easy to spot. */
  static void flash (RecyclerView recyclerView, int position) {
    if (recyclerView == null || position == RecyclerView.NO_POSITION || !(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) {
      return;
    }
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    View view = manager.findViewByPosition(position);
    if (view != null && view.getTop() >= 0 && view.getBottom() <= recyclerView.getHeight()) {
      pulse(view);
      return;
    }
    manager.scrollToPositionWithOffset(position, Screen.dp(72f));
    // Runs after the layout pass that the scroll scheduled.
    recyclerView.post(() -> {
      View target = manager.findViewByPosition(position);
      if (target != null) {
        pulse(target);
      }
    });
  }

  private static void pulse (View view) {
    Drawable background = view.getBackground();
    if (background != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      background.setHotspot(view.getWidth() / 2f, view.getHeight() / 2f);
    }
    view.setPressed(true);
    view.postDelayed(() -> view.setPressed(false), 650);
  }
}
