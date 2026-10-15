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

/** Settings → XGram → Side menu: which items the drawer shows (exteraGram custom drawer). */
public final class XgramDrawerItemsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final String[] KEYS = {"contacts", "hub", "calls", "saved", "bookmarks", "saved_by_chat", "invite", "proxy", "help", "night", "ghost"};
  private static final int[] NAMES = {R.string.Contacts, R.string.XgramHub, R.string.Calls, R.string.SavedMessages, R.string.XgramBookmarks, R.string.XgramSavedByChat, R.string.InviteFriends, R.string.Proxy, R.string.Help, R.string.NightMode, R.string.XgramGhostMode};

  private SettingsAdapter adapter;

  public XgramDrawerItemsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramDrawerItems;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramSideMenu);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (item.getId() == R.id.btn_xgramHidePhone) {
          view.getToggler().setRadioEnabled(XgramSettings.instance().hidePhone(), isUpdate);
        } else if (data instanceof String) {
          view.getToggler().setRadioEnabled(!XgramSettings.instance().drawerItemHidden((String) data), isUpdate);
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    // The same option as in Settings → XGram → Privacy: the header shows the @username instead of the number.
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramHidePhone, 0, R.string.XgramHidePhone));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramSideMenuItems));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < KEYS.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramDrawerItem, 0, NAMES[i]).setData(KEYS[i]));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramSideMenuInfo));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramHidePhone) {
      XgramSettings.instance().setHidePhone(adapter.toggleView(v));
      if (context.getDrawer() != null) {
        context.getDrawer().xgramRefreshHeader(); // the side menu shows the @username instead
      }
    } else if (v.getId() == R.id.btn_xgramDrawerItem && tag instanceof ListItem && ((ListItem) tag).getData() instanceof String) {
      String key = (String) ((ListItem) tag).getData();
      boolean shown = adapter.toggleView(v);
      XgramSettings.instance().setDrawerItemHidden(key, !shown);
      if (context.getDrawer() != null) {
        context.getDrawer().xgramUpdateMenuItems();
      }
    }
  }
}
