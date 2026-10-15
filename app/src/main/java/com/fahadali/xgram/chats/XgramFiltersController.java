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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings → XGram → Filters: Word filters (words or patterns to hide) or Hidden people (shadow-banned senders).
 * One controller, two pages: {@link #words} and {@link #people}.
 */
public final class XgramFiltersController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private boolean people;

  public XgramFiltersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /** Word filters: words or patterns that hide a message. */
  public static XgramFiltersController words (Context context, Tdlib tdlib) {
    return new XgramFiltersController(context, tdlib);
  }

  /** Hidden people: senders whose messages don't show in groups. */
  public static XgramFiltersController people (Context context, Tdlib tdlib) {
    XgramFiltersController c = new XgramFiltersController(context, tdlib);
    c.people = true;
    return c;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramFilters;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(people ? R.string.XgramShadowBanned : R.string.XgramWordFilters);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_xgramShadowBanList) {
          view.setData(Integer.toString(XgramFilters.shadowBannedCount(tdlib)));
        }
      }
    };
    rebuild();
    recyclerView.setAdapter(adapter);
  }

  private void rebuild () {
    adapter.setItems(buildItems(people), false);
  }

  /** Rows of one of the two pages. The settings search indexes these too. */
  public static List<ListItem> buildItems (boolean people) {
    List<ListItem> items = new ArrayList<>();
    if (people) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramShadowBanList, 0, R.string.XgramShadowBanned));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramClearShadowBans, 0, R.string.XgramClearShadowBans).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramShadowBanInfo));
      return items;
    }
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.XgramFilterWords));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramAddFilter, R.drawable.baseline_add_24, R.string.XgramAddFilter));
    for (String filter : XgramFilters.getFilters()) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramFilter, 0, filter, false).setData(filter));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramFiltersInfo));
    return items;
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Object tag = v.getTag();
    Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (id == R.id.btn_xgramAddFilter) {
      openInputAlert(Lang.getString(R.string.XgramAddFilter), Lang.getString(R.string.XgramFilterHint), R.string.Save, R.string.Cancel, null, (inputView, result) -> {
        String text = result.trim();
        if (!text.isEmpty()) {
          List<String> filters = XgramFilters.getFilters();
          if (!filters.contains(text)) {
            filters.add(text);
            XgramFilters.setFilters(filters);
          }
          rebuild();
        }
        return true;
      }, true);
    } else if (id == R.id.btn_xgramFilter && data instanceof String) {
      String filter = (String) data;
      showOptions(filter, new int[] {R.id.btn_delete, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.XgramRemoveFilter), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.RED, OptionColor.NORMAL},
        new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_delete) {
            List<String> filters = XgramFilters.getFilters();
            filters.remove(filter);
            XgramFilters.setFilters(filters);
            rebuild();
          }
          return true;
        });
    } else if (id == R.id.btn_xgramClearShadowBans) {
      XgramFilters.clearShadowBans(tdlib);
      adapter.updateValuedSettingById(R.id.btn_xgramShadowBanList);
    }
  }
}
