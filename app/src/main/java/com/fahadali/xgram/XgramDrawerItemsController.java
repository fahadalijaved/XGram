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

import androidx.annotation.Nullable;

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

/** Settings → XGram → Side menu: which items the drawer shows (exteraGram custom drawer), and in what order. */
public final class XgramDrawerItemsController extends RecyclerViewController<Void> implements View.OnClickListener {
  // Telegram X's order. Settings can't be hidden, only moved; Night mode and Ghost mode sit below the line.
  // XGram: public for the Compose Side menu page (xgram-m3e)
  public static final String[] KEYS = {"contacts", "hub", "calls", "saved", "bookmarks", "saved_by_chat", "settings", "invite", "proxy", "help"};
  public static final int[] NAMES = {R.string.Contacts, R.string.XgramHub, R.string.Calls, R.string.SavedMessages, R.string.XgramBookmarks, R.string.XgramSavedByChat, R.string.Settings, R.string.InviteFriends, R.string.Proxy, R.string.Help};
  public static final String[] TOGGLE_KEYS = {"night", "ghost"};
  public static final int[] TOGGLE_NAMES = {R.string.NightMode, R.string.XgramGhostMode};
  public static final String KEY_SETTINGS = "settings";

  /** The stored key of a side menu item, in the drawer itself or on this screen; null for anything else. */
  @Nullable
  public static String keyOf (ListItem item) {
    int id = item.getId();
    if (id == R.id.btn_xgramDrawerItem) {
      return item.getData() instanceof String ? (String) item.getData() : null;
    } else if (id == R.id.btn_contacts) {
      return "contacts";
    } else if (id == R.id.btn_xgramHub) {
      return "hub";
    } else if (id == R.id.btn_calls) {
      return "calls";
    } else if (id == R.id.btn_savedMessages) {
      return "saved";
    } else if (id == R.id.btn_xgramBookmarks) {
      return "bookmarks";
    } else if (id == R.id.btn_xgramSavedByChat) {
      return "saved_by_chat";
    } else if (id == R.id.btn_settings) {
      return KEY_SETTINGS;
    } else if (id == R.id.btn_invite) {
      return "invite";
    } else if (id == R.id.btn_proxy) {
      return "proxy";
    } else if (id == R.id.btn_help) {
      return "help";
    } else if (id == R.id.btn_night) {
      return "night";
    } else if (id == R.id.btn_xgramGhostMode) {
      return "ghost";
    }
    return null;
  }

  private SettingsAdapter adapter;
  private XgramReorder.Ui reorder;

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
        if (reorder != null) {
          reorder.bind(item, view);
        }
        Object data = item.getData();
        if (item.getId() == R.id.btn_xgramHidePhone) {
          view.setData(R.string.XgramHidePhoneSub);
          view.getToggler().setRadioEnabled(XgramSettings.instance().hidePhone(), isUpdate);
        } else if (data instanceof String && view.getToggler() != null) {
          view.getToggler().setRadioEnabled(!XgramSettings.instance().drawerItemHidden((String) data), isUpdate);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    reorder = new XgramReorder.Ui(recyclerView, adapter, XgramReorder.DRAWER, XgramDrawerItemsController::keyOf, item -> {
      String key = keyOf(item);
      return "night".equals(key) || "ghost".equals(key) ? "toggles" : "main"; // the line in the side menu stays between them
    }, this::updateDrawer);
  }

  /** Rows of this page. The settings search indexes these too. */
  static List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    // The same option as in Settings → XGram → Privacy: the header shows the @username instead of the number.
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramHidePhone, 0, R.string.XgramHidePhone));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramSideMenuItems));
    addSection(items, KEYS, NAMES);
    addSection(items, TOGGLE_KEYS, TOGGLE_NAMES);
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramSideMenuInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramResetOrder, R.drawable.baseline_replay_24, R.string.XgramResetOrder));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  /** How many items the side menu hides (Settings → XGram shows it). Settings can't be hidden. */
  static int hiddenCount () {
    int count = 0;
    for (String key : KEYS) {
      if (!KEY_SETTINGS.equals(key) && XgramSettings.instance().drawerItemHidden(key)) {
        count++;
      }
    }
    for (String key : TOGGLE_KEYS) {
      if (XgramSettings.instance().drawerItemHidden(key)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_xgramHidePhone); // also a switch in Privacy
    }
  }

  private static void addSection (List<ListItem> items, String[] keys, int[] names) {
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    // No separator rows: they would be dragged along. XgramReorder draws the lines.
    for (String key : XgramReorder.ordered(XgramReorder.DRAWER, keys)) {
      int name = 0;
      for (int i = 0; i < keys.length; i++) {
        if (keys[i].equals(key)) {
          name = names[i];
        }
      }
      items.add(new ListItem(KEY_SETTINGS.equals(key) ? ListItem.TYPE_SETTING : ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramDrawerItem, 0, name).setData(key));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  private void updateDrawer () {
    if (context.getDrawer() != null) {
      context.getDrawer().xgramUpdateMenuItems();
    }
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramHidePhone) {
      XgramSettings.instance().setHidePhone(adapter.toggleView(v));
      if (context.getDrawer() != null) {
        context.getDrawer().xgramRefreshHeader(); // the side menu shows the @username instead
      }
    } else if (v.getId() == R.id.btn_xgramResetOrder) {
      XgramReorder.reset(XgramReorder.DRAWER);
      adapter.setItems(buildItems(), false);
      updateDrawer();
    } else if (v.getId() == R.id.btn_xgramDrawerItem && tag instanceof ListItem && ((ListItem) tag).getData() instanceof String) {
      String key = (String) ((ListItem) tag).getData();
      if (KEY_SETTINGS.equals(key)) {
        return; // always shown, can only be moved
      }
      boolean shown = adapter.toggleView(v);
      XgramSettings.instance().setDrawerItemHidden(key, !shown);
      updateDrawer();
    }
  }
}
