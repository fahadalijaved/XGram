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
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.CustomTypefaceSpan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import me.vkryl.core.ObjectUtils;

/**
 * Settings search (NagramX). The index is read from the pages' own row lists
 * ({@link XgramSettingsController#buildSectionItems} and {@link XgramSettingsController#buildSubPageItems}),
 * so a row is searchable as soon as a page shows it, also on the pages a section opens (Side Menu, Message menu, ...).
 */
final class XgramSettingsSearch {
  private XgramSettingsSearch () { }

  /** One searchable row: which page holds it, and what a result needs to draw it. */
  static final class Entry {
    final int section;
    /** 0: a row of the section's own page; otherwise the page it opens ({@link XgramSettingsController#SUB_SIDE_MENU}, ...). */
    final int subPage;
    final @IdRes int id;
    /** Tells apart rows that share an id on a page (side menu items, message menu options, ...). */
    final @Nullable String key;
    final boolean isToggle;
    final int iconRes, textColorId;
    final String title, titleLower, path;

    Entry (int section, int subPage, @IdRes int id, @Nullable String key, boolean isToggle, int iconRes, int textColorId, String title, String path) {
      this.section = section;
      this.subPage = subPage;
      this.id = id;
      this.key = key;
      this.isToggle = isToggle;
      this.iconRes = iconRes;
      this.textColorId = textColorId;
      this.title = title;
      this.titleLower = title.toLowerCase();
      this.path = path;
    }
  }

  /** A few dozen rows: built when search opens, so it follows the current language. Main page order, sub-pages last. */
  static List<Entry> buildIndex (XgramSettingsController controller) {
    List<Entry> entries = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int section : XgramSettingsController.SEARCH_SECTIONS) {
      String sectionName = Lang.getString(XgramSettingsController.sectionTitleRes(section));
      addEntries(entries, seen, controller.buildSectionItems(section), section, 0, sectionName, XgramSettingsController.sectionIcon(section), true);
    }
    for (int subPage : XgramSettingsController.SEARCH_SUB_PAGES) {
      int parent = XgramSettingsController.subPageParent(subPage);
      String pageName = Lang.getString(XgramSettingsController.subPageTitleRes(subPage));
      boolean topLevel = parent == XgramSettingsController.SECTION_MAIN;
      String base = topLevel ? pageName : Lang.getString(R.string.XgramSettingsSearchPath, Lang.getString(XgramSettingsController.sectionTitleRes(parent)), pageName);
      // A page opened from a section is two levels deep already: its own headers would make the path too long.
      addEntries(entries, seen, controller.buildSubPageItems(subPage), parent, subPage, base, XgramSettingsController.subPageIcon(subPage), topLevel);
    }
    return entries;
  }

  private static void addEntries (List<Entry> entries, Set<String> seen, List<ListItem> items, int section, int subPage, String base, int defaultIcon, boolean withGroups) {
    String group = null;
    for (ListItem item : items) {
      int viewType = item.getViewType();
      if (viewType == ListItem.TYPE_HEADER || viewType == ListItem.TYPE_HEADER_PADDED) {
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
      if (title == null || title.length() == 0) {
        continue;
      }
      String key = keyOf(item);
      String rowKey = item.getId() + ":" + key;
      // A row shown on a section page and on a page further in (Hide phone number) is found once, on the section page.
      boolean isNew = subPage == 0 ? seen.add(rowKey) : !seen.contains(rowKey) && seen.add(subPage + "/" + rowKey);
      if (!isNew) {
        continue;
      }
      String path = withGroups && group != null ? Lang.getString(R.string.XgramSettingsSearchPath, base, group) : base;
      // Switches of the pages further in belong to those pages: their results open the page instead.
      boolean isToggle = subPage == 0 && (viewType == ListItem.TYPE_RADIO_SETTING || viewType == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER);
      int icon = item.getIconResource() != 0 ? item.getIconResource() : defaultIcon;
      entries.add(new Entry(section, subPage, item.getId(), key, isToggle, icon, item.getTextColorId(ColorId.NONE), title.toString(), path));
    }
  }

  /** The key that tells apart rows sharing an id: a side menu item, message box button or chat action key, or a message menu option. */
  @Nullable
  private static String keyOf (ListItem item) {
    Object data = item.getData();
    if (data instanceof String) {
      return (String) data;
    }
    if (data instanceof com.fahadali.xgram.chats.XgramMessageMenu.Option) {
      return ((com.fahadali.xgram.chats.XgramMessageMenu.Option) data).key;
    }
    return null;
  }

  /** Position of the row a result stands for, or -1. */
  static int indexOf (SettingsAdapter adapter, Entry e) {
    List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      ListItem item = items.get(i);
      if (item.getId() == e.id && !(item.getData() instanceof Entry) && ObjectUtils.equals(keyOf(item), e.key)) {
        return i;
      }
    }
    return -1;
  }

  /** A result opened a page further in: flash its row once that page shows. */
  static void flashWhenShown (RecyclerViewController<?> c, Entry e) {
    c.addOneShotFocusListener(() -> {
      RecyclerView recyclerView = c.getRecyclerView();
      if (!c.isDestroyed() && recyclerView != null && recyclerView.getAdapter() instanceof SettingsAdapter) {
        flash(recyclerView, indexOf((SettingsAdapter) recyclerView.getAdapter(), e));
      }
    });
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
