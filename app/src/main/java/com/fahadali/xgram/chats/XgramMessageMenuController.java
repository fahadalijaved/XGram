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
          // The popup menu always has the reactions above the message: there the bar is only on or off
          int mode = com.fahadali.xgram.XgramSettings.instance().reactionBar();
          view.setData(com.fahadali.xgram.XgramSettings.instance().messagePopup() ? XgramReactionBar.popupModeName(mode) : XgramReactionBar.modeName(mode));
          return;
        }
        if (item.getId() == R.id.btn_xgramMessagePopup) {
          view.setData(item.getCharSequenceValue());
          view.getToggler().setRadioEnabled(com.fahadali.xgram.XgramSettings.instance().messagePopup(), isUpdate);
          return;
        }
        Object data = item.getData();
        if (data instanceof XgramMessageMenu.Option) {
          XgramMessageMenu.Option option = (XgramMessageMenu.Option) data;
          String key = option.key;
          boolean hidden = XgramMessageMenu.isHidden(key);
          view.getToggler().setRadioEnabled(!hidden, isUpdate);
          // Under More: "Reply · under More" (Report and Restrict by default, as in Telegram X). A hidden More that
          // still shows while options wait under it (XgramMessageMenu.filter) says so.
          String name = Lang.getString(option.name);
          if (!hidden && XgramMessageMenu.showsUnderMore(key)) {
            name = Lang.getString(R.string.XgramMenuNameUnderMore, name);
          } else if (hidden && XgramMessageMenu.KEY_MORE_OPTION.equals(key) && XgramMessageMenu.hasOptionsUnderMore()) {
            name = Lang.getString(R.string.XgramMenuNameMoreKept, name);
          }
          view.setName(name);
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
    // The menu as a popup next to the message (Telegram) or as the sheet
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    // A bubble, not ⋯: on this page ⋯ is the More option, a few rows below
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramMessagePopup, R.drawable.baseline_chat_bubble_24, R.string.XgramMessagePopup)
      .setStringValue(Lang.getString(R.string.XgramMessagePopupInfo)));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramReactionBar, R.drawable.baseline_emoticon_outline_24, R.string.XgramReactionBar));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, reactionBarInfo()));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramMessageMenuInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    // One list (Telegram X's options and XGram's mixed, as in the menu); no separator rows, they would be dragged along.
    String[] keys = new String[XgramMessageMenu.OPTIONS.length];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = XgramMessageMenu.OPTIONS[i].key;
    }
    for (String key : XgramReorder.ordered(XgramReorder.MESSAGE_MENU, keys)) {
      if (!XgramMessageMenu.isAvailable(key)) {
        continue;
      }
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

  /** The reaction bar's description: the popup menu has its own, as there the bar is only on or off. */
  private static int reactionBarInfo () {
    return com.fahadali.xgram.XgramSettings.instance().messagePopup() ? R.string.XgramReactionBarPopupInfo : R.string.XgramReactionBarInfo;
  }

  /** The reaction bar's row and description follow the Popup menu switch. */
  private void updateReactionBar () {
    adapter.updateValuedSettingById(R.id.btn_xgramReactionBar);
    int index = adapter.indexOfView(item -> item.getViewType() == ListItem.TYPE_DESCRIPTION &&
      (item.getStringResource() == R.string.XgramReactionBarInfo || item.getStringResource() == R.string.XgramReactionBarPopupInfo));
    if (index != -1 && adapter.getItems().get(index).setStringIfChanged(reactionBarInfo())) {
      adapter.updateItem(index);
    }
  }

  /** The More row's name says when More shows though hidden, which the other rows' choices change. */
  private void updateMoreRow () {
    for (XgramMessageMenu.Option option : XgramMessageMenu.OPTIONS) {
      if (XgramMessageMenu.KEY_MORE_OPTION.equals(option.key)) {
        adapter.updateValuedSettingByData(option);
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramMessagePopup) {
      com.fahadali.xgram.XgramSettings.instance().setMessagePopup(adapter.toggleView(v));
      updateReactionBar();
      return;
    }
    if (v.getId() == R.id.btn_xgramReactionBar) {
      int stored = com.fahadali.xgram.XgramSettings.instance().reactionBar();
      final boolean popup = com.fahadali.xgram.XgramSettings.instance().messagePopup();
      // In the popup menu only On or Off: On keeps the stored Top or Bottom, which the sheet still follows
      int[] modes = popup ?
        new int[] {stored != com.fahadali.xgram.XgramSettings.REACTION_BAR_OFF ? stored : com.fahadali.xgram.XgramSettings.REACTION_BAR_TOP, com.fahadali.xgram.XgramSettings.REACTION_BAR_OFF} :
        new int[] {com.fahadali.xgram.XgramSettings.REACTION_BAR_TOP, com.fahadali.xgram.XgramSettings.REACTION_BAR_BOTTOM, com.fahadali.xgram.XgramSettings.REACTION_BAR_OFF};
      String[] titles = new String[modes.length];
      int[] optionIds = new int[modes.length];
      for (int i = 0; i < modes.length; i++) {
        titles[i] = popup ? XgramReactionBar.popupModeName(modes[i]) : XgramReactionBar.modeName(modes[i]);
        optionIds[i] = i + 1;
      }
      showOptions(null, optionIds, titles, null, null, (itemView, optionId) -> {
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
      if (!XgramMessageMenu.canGoInMore(option.key)) {
        boolean shown = adapter.toggleView(v);
        XgramMessageMenu.setHidden(option.key, !shown);
        adapter.updateValuedSetting((ListItem) tag); // More's name: whether it still shows while hidden
        return;
      }
      // In the menu, under the menu's More, or hidden. Restrict can only be under More or hidden, as Telegram X only
      // knows on More's page whether it applies.
      final ListItem item = (ListItem) tag;
      final int current = XgramMessageMenu.isHidden(option.key) ? 3 : XgramMessageMenu.showsUnderMore(option.key) ? 2 : 1;
      final boolean canBeInMenu = XgramMessageMenu.canChoosePlace(option.key);
      int[] optionIds = canBeInMenu ? new int[] {1, 2, 3} : new int[] {2, 3};
      String[] titles = new String[optionIds.length];
      for (int i = 0; i < optionIds.length; i++) {
        int optionId = optionIds[i];
        String title = Lang.getString(optionId == 1 ? R.string.XgramMenuInMenu : optionId == 2 ? R.string.XgramMenuUnderMore : R.string.XgramMenuHidden);
        titles[i] = optionId == current ? Lang.getString(R.string.XgramMenuChoiceCurrent, title) : title;
      }
      showOptions(Lang.getString(option.name), optionIds, titles, null, null, (itemView, optionId) -> {
        XgramMessageMenu.setHidden(option.key, optionId == 3);
        if (optionId != 3) {
          XgramMessageMenu.setUnderMore(option.key, optionId == 2);
        }
        adapter.updateValuedSetting(item);
        updateMoreRow(); // Under More turns More back on; More's name follows what waits under it
        return true;
      });
    }
  }
}
