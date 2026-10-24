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

import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramSettingDeps;
import com.fahadali.xgram.XgramSettings;

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
 * Settings → XGram → Chat list → Header buttons: which buttons the chat list's top row shows next to the folder tabs.
 * The only switches for the lock (XgramSettings.hideLockButton; Passcode lock has a note pointing here) and Activity's
 * bell (XgramSettings.activityHeader; so has Activity and reminders). The ⋮ is not here: while the side menu is hidden
 * it is the only way to the side menu's items. The chat list follows at once.
 */
public final class XgramHeaderButtonsController extends RecyclerViewController<Void> implements View.OnClickListener {
  // XGram: public for the Compose Header buttons page (xgram-m3e)
  public static final String LOCK = "lock", GHOST = XgramSettings.HEADER_BUTTON_GHOST, ACTIVITY = "activity", SEARCH = XgramSettings.HEADER_BUTTON_SEARCH;
  public static final String[] KEYS = {LOCK, GHOST, ACTIVITY, SEARCH};
  public static final int[] NAMES = {R.string.XgramHeaderLock, R.string.XgramHeaderGhost, R.string.XgramHeaderActivity, R.string.XgramHeaderSearch};
  public static final int[] INFOS = {R.string.XgramHeaderLockInfo, R.string.XgramHeaderGhostInfo, R.string.XgramHeaderActivityInfo, R.string.XgramHeaderSearchInfo};
  public static final int[] ICONS = {R.drawable.baseline_lock_24, R.drawable.baseline_ghost_24, R.drawable.baseline_notifications_24, R.drawable.baseline_search_24};

  private SettingsAdapter adapter;

  public XgramHeaderButtonsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramHeaderButtons;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramHeaderButtons);
  }

  /** Whether the chat list's header may show the button (it still shows only when it applies: a passcode, ghost mode...). */
  public static boolean isShown (String key) {
    XgramSettings settings = XgramSettings.instance();
    switch (key) {
      case LOCK: return !settings.hideLockButton();
      case ACTIVITY: return settings.activityHeader();
      default: return !settings.headerButtonHidden(key);
    }
  }

  public static void setShown (String key, boolean shown) {
    XgramSettings settings = XgramSettings.instance();
    switch (key) {
      case LOCK: settings.setHideLockButton(!shown); break;
      case ACTIVITY: settings.setActivityHeader(shown); break;
      default: settings.setHeaderButtonHidden(key, !shown); break;
    }
  }

  /** Why a button's switch can't be changed now (Activity's bell while Activity is off), or 0. */
  public static @StringRes int reason (String key) {
    return ACTIVITY.equals(key) ? XgramSettingDeps.activityOptionsReason() : 0;
  }

  /** How many buttons are hidden (Settings → XGram → Chat list shows it). */
  public static int hiddenCount () {
    int count = 0;
    for (String key : KEYS) {
      if (!isShown(key)) {
        count++;
      }
    }
    return count;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (!(data instanceof String)) {
          return;
        }
        String key = (String) data;
        int reason = reason(key);
        view.setData(reason != 0 ? Lang.getString(reason) : item.getCharSequenceValue());
        view.setEnabled(reason == 0);
        view.getToggler().setRadioEnabled(isShown(key), isUpdate);
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  /** Rows of this page. The settings search indexes these too. */
  public static List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramHeaderButtonsInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < KEYS.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramHeaderButton, ICONS[i], NAMES[i])
        .setStringValue(Lang.getString(INFOS[i]))
        .setData(KEYS[i]));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramHeaderMoreNote));
    return items;
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramHeaderButton && tag instanceof ListItem && ((ListItem) tag).getData() instanceof String) {
      String key = (String) ((ListItem) tag).getData();
      if (reason(key) != 0) {
        return; // the reason is under the title
      }
      setShown(key, adapter.toggleView(v));
    }
  }
}
