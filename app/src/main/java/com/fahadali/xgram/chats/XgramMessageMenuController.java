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

/** Settings → XGram → Messages → Message menu: which options a long press on a message shows, and in what order. */
public final class XgramMessageMenuController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private XgramReorder.Ui reorder;

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
        if (reorder != null) {
          reorder.bind(item, view);
        }
        if (item.getId() == R.id.btn_xgramReactionBar) {
          view.setData(XgramReactionBar.modeName(com.fahadali.xgram.XgramSettings.instance().reactionBar()));
          return;
        }
        Object data = item.getData();
        if (data instanceof XgramMessageMenu.Option) {
          String key = ((XgramMessageMenu.Option) data).key;
          view.getToggler().setRadioEnabled(!XgramMessageMenu.isHidden(key), isUpdate);
          // Summarize and Edit history are only added while their setting is on: greyed, but still touchable, as the
          // row's handle drags it (a disabled view gets no touches). onClick ignores the tap.
          boolean usable = com.fahadali.xgram.XgramSettingDeps.messageMenuOptionEnabled(key);
          view.setIgnoreEnabled(true);
          view.setVisuallyEnabled(usable, isUpdate);
          view.getToggler().setDisabled(!usable, isUpdate);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    reorder = new XgramReorder.Ui(recyclerView, adapter, XgramReorder.MESSAGE_MENU, item -> item.getData() instanceof XgramMessageMenu.Option ? ((XgramMessageMenu.Option) item.getData()).key : null, null, null);
  }

  /** Rows of this page. The settings search indexes these too. */
  public static List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    // Where the quick reactions go (top, under the options, or off): above the list, which can be long.
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramReactionBar, R.drawable.baseline_emoticon_outline_24, R.string.XgramReactionBar));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramReactionBarInfo));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramMessageMenuInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    // One list (Telegram X's options and XGram's mixed, as in the menu); no separator rows, they would be dragged along.
    String[] keys = new String[XgramMessageMenu.OPTIONS.length];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = XgramMessageMenu.OPTIONS[i].key;
    }
    for (String key : XgramReorder.ordered(XgramReorder.MESSAGE_MENU, keys)) {
      for (XgramMessageMenu.Option option : XgramMessageMenu.OPTIONS) {
        if (option.key.equals(key)) {
          items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramMenuItem, option.icon(), option.name).setData(option));
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
    if (v.getId() == R.id.btn_xgramReactionBar) {
      int[] modes = {com.fahadali.xgram.XgramSettings.REACTION_BAR_TOP, com.fahadali.xgram.XgramSettings.REACTION_BAR_BOTTOM, com.fahadali.xgram.XgramSettings.REACTION_BAR_OFF};
      String[] titles = new String[modes.length];
      for (int i = 0; i < modes.length; i++) {
        titles[i] = XgramReactionBar.modeName(modes[i]);
      }
      showOptions(null, new int[] {1, 2, 3}, titles, null, null, (itemView, optionId) -> {
        com.fahadali.xgram.XgramSettings.instance().setReactionBar(modes[optionId - 1]);
        adapter.updateValuedSettingById(R.id.btn_xgramReactionBar);
        return true;
      });
    } else if (v.getId() == R.id.btn_xgramResetOrder) {
      XgramReorder.reset(XgramReorder.MESSAGE_MENU);
      adapter.setItems(buildItems(), false);
    } else if (v.getId() == R.id.btn_xgramMenuItem && tag instanceof ListItem && ((ListItem) tag).getData() instanceof XgramMessageMenu.Option) {
      XgramMessageMenu.Option option = (XgramMessageMenu.Option) ((ListItem) tag).getData();
      if (!com.fahadali.xgram.XgramSettingDeps.messageMenuOptionEnabled(option.key)) {
        return; // greyed: does nothing until its setting is on
      }
      boolean shown = adapter.toggleView(v);
      XgramMessageMenu.setHidden(option.key, !shown);
    }
  }
}
