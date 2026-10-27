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

import com.fahadali.xgram.XgramReorder;

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
 * Settings → XGram → Messages → Chat menu (Expressive design off; ChatMenuPage with it on): what a chat's ⋮ menu shows,
 * what waits under More…, and in what order.
 */
public final class XgramChatMenuController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private XgramReorder.Ui reorder;

  public XgramChatMenuController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramChatMenu;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramChatMenu);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (reorder != null) {
          reorder.bind(item, view);
        }
        if (item.getData() instanceof XgramChatMenu.Option) {
          XgramChatMenu.Option option = (XgramChatMenu.Option) item.getData();
          boolean hidden = XgramChatMenu.isHidden(option.key);
          view.getToggler().setRadioEnabled(!hidden, isUpdate);
          String name = Lang.getString(option.name);
          view.setName(XgramChatMenu.isUnderMore(option.key) ? Lang.getString(R.string.XgramMenuNameUnderMore, name) : name);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    reorder = new XgramReorder.Ui(recyclerView, adapter, XgramReorder.CHAT_MENU, item -> item.getData() instanceof XgramChatMenu.Option ? ((XgramChatMenu.Option) item.getData()).key : null, null, null);
  }

  /** Rows of this page. The settings search indexes these too. */
  public static List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramChatMenuInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (String key : XgramReorder.ordered(XgramReorder.CHAT_MENU, XgramChatMenu.defaultKeys())) {
      if (!XgramChatMenu.isAvailable(key)) {
        continue;
      }
      for (XgramChatMenu.Option option : XgramChatMenu.OPTIONS) {
        if (option.key.equals(key)) {
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramChatMenuItem, option.icon, option.name).setData(option));
          break;
        }
      }
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramResetOrder, R.drawable.baseline_replay_24, R.string.XgramResetOrder));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramResetOrder) {
      XgramReorder.reset(XgramReorder.CHAT_MENU);
      adapter.setItems(buildItems(), false);
    } else if (v.getId() == R.id.btn_xgramChatMenuItem && tag instanceof ListItem && ((ListItem) tag).getData() instanceof XgramChatMenu.Option) {
      final ListItem item = (ListItem) tag;
      final XgramChatMenu.Option option = (XgramChatMenu.Option) item.getData();
      // In the menu, under More…, or hidden
      final int current = XgramChatMenu.isHidden(option.key) ? 3 : XgramChatMenu.isUnderMore(option.key) ? 2 : 1;
      int[] optionIds = {1, 2, 3};
      String[] titles = new String[optionIds.length];
      for (int i = 0; i < optionIds.length; i++) {
        int optionId = optionIds[i];
        String title = Lang.getString(optionId == 1 ? R.string.XgramMenuInMenu : optionId == 2 ? R.string.XgramMenuUnderMore : R.string.XgramMenuHidden);
        titles[i] = optionId == current ? Lang.getString(R.string.XgramMenuChoiceCurrent, title) : title;
      }
      showOptions(Lang.getString(option.name), optionIds, titles, null, null, (itemView, optionId) -> {
        XgramChatMenu.setHidden(option.key, optionId == 3);
        if (optionId != 3) {
          XgramChatMenu.setUnderMore(option.key, optionId == 2);
        }
        adapter.updateValuedSetting(item);
        return true;
      });
    }
  }
}
