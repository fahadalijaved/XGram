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
package com.fahadali.xgram.search;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The universal settings search in Telegram X's look (Expressive design off): the header's search field, open while
 * the page is, and the results in a settings list. Before anything is typed: the results opened last and the pages to
 * go to. A result opens its page in this page's stack and points at its row; back comes here with the query kept. Back
 * from the field closes the page.
 */
public final class XgramUniversalSearchController extends RecyclerViewController<Integer> implements View.OnClickListener {
  public XgramUniversalSearchController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /** [origin]: SettingsSearch.ORIGIN_SETTINGS or ORIGIN_XGRAM. */
  public static XgramUniversalSearchController create (Context context, Tdlib tdlib, int origin) {
    XgramUniversalSearchController c = new XgramUniversalSearchController(context, tdlib);
    c.setArguments(origin);
    return c;
  }

  private SettingsAdapter adapter;
  private @Nullable SearchIndex index;
  private String query = "";
  private boolean searchOpened;
  private View lastTouchView; // where a row with a switch was last touched (switch vs. row)
  private float lastTouchX;

  private int origin () {
    Integer origin = getArguments();
    return origin != null ? origin : SettingsSearch.ORIGIN_SETTINGS;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramUniversalSearch;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramSettingsSearchHint);
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getSearchHint () {
    return R.string.XgramSettingsSearchHint;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        bindResult(item, view, isUpdate);
      }

      @Override
      public void modifySettingView (int viewType, SettingView settingView) {
        if (viewType == ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER) {
          // A tap on the switch flips it, a tap elsewhere opens the row's page.
          settingView.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
              lastTouchView = v;
              lastTouchX = e.getX();
            }
            return false;
          });
        }
      }
    };
    recyclerView.setItemAnimator(null); // the results change on every keystroke
    recyclerView.setAdapter(adapter);
    index = SettingsSearchEngine.build(this, origin());
    showItems();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (!searchOpened) {
      // The field opens with the keyboard once, when the page first shows; not again when coming back.
      openSearchMode();
      searchOpened = inSearchMode(); // the header was busy: again on the next focus
    }
  }

  // Search mode: the page is the search, so leaving the field leaves the page

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    this.query = query;
    if (adapter != null) {
      showItems();
      ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }
  }

  @Override
  public boolean onBeforeLeaveSearchMode () {
    if (!isDestroyed()) {
      postNavigateBack(); // the header's back arrow
    }
    return false;
  }

  @Override
  public boolean closeSearchModeByBackPress (boolean fromTop, boolean commit) {
    if (commit) {
      navigateBack();
    }
    return true;
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (adapter != null) {
      index = SettingsSearchEngine.build(this, origin());
      showItems();
    }
  }

  @Override
  public void onThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) {
    super.onThemeChanged(fromTheme, toTheme);
    updateToggles(); // Night mode turned on or off
  }

  // Rows

  private void showItems () {
    List<ListItem> items = new ArrayList<>();
    SearchIndex index = this.index;
    String q = query.trim();
    if (index == null) {
      adapter.setItems(items, false);
      return;
    }
    if (q.isEmpty()) {
      List<SearchEntry> recents = index.recents();
      if (!recents.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.XgramSearchRecent));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        for (int i = 0; i < recents.size(); i++) {
          if (i > 0) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          items.add(resultItem(recents.get(i), null, R.id.btn_xgramSearchResult, R.drawable.baseline_history_24));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramSearchClearRecents, R.drawable.baseline_close_24, R.string.Clear));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
      List<SearchEntry> pages = index.getShortcuts();
      if (!pages.isEmpty()) {
        items.add(new ListItem(recents.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, R.string.XgramSearchPages));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        for (int i = 0; i < pages.size(); i++) {
          if (i > 0) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          items.add(resultItem(pages.get(i), null, R.id.btn_xgramSearchPage, 0));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    } else {
      List<SearchHit> hits = SettingsSearchEngine.search(index, q);
      if (hits.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.NothingFound));
      } else {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        for (int i = 0; i < hits.size(); i++) {
          if (i > 0) {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR));
          }
          SearchHit hit = hits.get(i);
          items.add(resultItem(hit.getEntry(), hit.getRanges(), R.id.btn_xgramSearchResult, 0));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }
    adapter.setItems(items, false);
  }

  /**
   * A result row: its icon, the title with what matched in the link color and medium, the path as its value (see
   * {@link #bindResult}), and Night mode's switch. Also the Telegram X results of XGram's own classic search.
   */
  public static ListItem resultItem (@NonNull SearchEntry e, @Nullable int[] ranges, @IdRes int id, @DrawableRes int forceIcon) {
    int viewType = e.getToggle() != null ? ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER : ListItem.TYPE_VALUED_SETTING_COMPACT;
    int icon = forceIcon != 0 ? forceIcon : e.getIcon();
    return new ListItem(viewType, id, icon, highlight(e.getTitle(), ranges)).setData(e);
  }

  private static CharSequence highlight (String title, @Nullable int[] ranges) {
    if (ranges == null || ranges.length < 2) {
      return title;
    }
    SpannableStringBuilder b = new SpannableStringBuilder(title);
    for (int i = 0; i + 1 < ranges.length; i += 2) {
      int start = Math.max(0, Math.min(ranges[i], title.length()));
      int end = Math.max(start, Math.min(ranges[i + 1], title.length()));
      if (end > start) {
        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), ColorId.textLink), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
    return b;
  }

  /** A result shows where it lives as its value; Night mode's switch shows whether it is on. */
  public static void bindResult (ListItem item, SettingView view, boolean isUpdate) {
    if (!(item.getData() instanceof SearchEntry)) {
      return;
    }
    SearchEntry e = (SearchEntry) item.getData();
    view.setData(e.getPath());
    InlineToggle toggle = e.getToggle();
    if (toggle != null && view.getToggler() != null) {
      view.getToggler().setRadioEnabled(toggle.getChecked().invoke(), isUpdate);
    }
  }

  private void updateToggles () {
    if (adapter == null) {
      return;
    }
    List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      Object data = items.get(i).getData();
      if (data instanceof SearchEntry && ((SearchEntry) data).getToggle() != null) {
        adapter.updateValuedSettingByPosition(i);
      }
    }
  }

  private boolean isSwitchTouch (View v) {
    if (v != lastTouchView || !(v instanceof SettingView)) {
      return false;
    }
    TogglerView toggler = ((SettingView) v).getToggler();
    if (toggler == null) {
      return false;
    }
    int slop = Screen.dp(12f);
    return lastTouchX >= toggler.getLeft() - slop && lastTouchX <= toggler.getRight() + slop;
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_xgramSearchClearRecents) {
      XgramSettings.instance().clearSearchRecents();
      showItems();
      return;
    }
    if (!(v.getTag() instanceof ListItem)) {
      return;
    }
    Object data = ((ListItem) v.getTag()).getData();
    if (!(data instanceof SearchEntry)) {
      return;
    }
    SearchEntry e = (SearchEntry) data;
    InlineToggle toggle = e.getToggle();
    if (toggle != null && isSwitchTouch(v)) {
      adapter.toggleView(v);
      toggle.getToggle().invoke();
      return;
    }
    preventLeavingSearchMode(); // come back to the results
    hideSoftwareKeyboard();
    SettingsSearchOpen.open(this, e, id != R.id.btn_xgramSearchPage);
  }
}
