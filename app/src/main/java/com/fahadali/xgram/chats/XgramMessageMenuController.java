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

/** Settings → XGram → Chats → Message menu: which options a long press on a message shows. */
public final class XgramMessageMenuController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public XgramMessageMenuController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramMessageMenu;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramMessageMenu);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof XgramMessageMenu.Option) {
          view.getToggler().setRadioEnabled(!XgramMessageMenu.isHidden(((XgramMessageMenu.Option) data).key), isUpdate);
        }
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramMessageMenuInfo));
    addSection(items, R.string.XgramMenuTelegram, false);
    addSection(items, R.string.XgramMenuXgram, true);
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  private static void addSection (List<ListItem> items, int title, boolean isXgram) {
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, title));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (XgramMessageMenu.Option option : XgramMessageMenu.OPTIONS) {
      if (option.isXgram != isXgram) {
        continue;
      }
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      first = false;
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramMenuItem, option.icon, option.name).setData(option));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramMenuItem && tag instanceof ListItem && ((ListItem) tag).getData() instanceof XgramMessageMenu.Option) {
      XgramMessageMenu.Option option = (XgramMessageMenu.Option) ((ListItem) tag).getData();
      boolean shown = adapter.toggleView(v);
      XgramMessageMenu.setHidden(option.key, !shown);
    }
  }
}
