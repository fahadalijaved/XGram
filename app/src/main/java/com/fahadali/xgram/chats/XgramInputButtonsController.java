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

/** Settings → XGram → Messages → Message box buttons: which buttons the message box shows. Open chats update at once. */
public final class XgramInputButtonsController extends RecyclerViewController<Void> implements View.OnClickListener {
  // XGram: public for the Compose Message box buttons page (xgram-m3e)
  public static final String[] KEYS = {XgramSettings.INPUT_BUTTON_COMMANDS, XgramSettings.INPUT_BUTTON_SCHEDULED, XgramSettings.INPUT_BUTTON_CAMERA, XgramSettings.INPUT_BUTTON_VOICE};
  public static final int[] NAMES = {R.string.XgramInputCommands, R.string.XgramInputScheduled, R.string.XgramInputCamera, R.string.XgramInputVoice};
  public static final int[] ICONS = {R.drawable.deproko_baseline_bots_command_26, R.drawable.baseline_date_range_24, R.drawable.deproko_baseline_camera_26, R.drawable.deproko_baseline_msg_voice_24};

  private SettingsAdapter adapter;

  public XgramInputButtonsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramInputButtons;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramInputButtons);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (item.getId() == R.id.btn_xgramInputAttachTyping) {
          view.setData(item.getCharSequenceValue());
          view.getToggler().setRadioEnabled(XgramSettings.instance().inputAttachWhileTyping(), isUpdate);
        } else if (data instanceof String) {
          view.getToggler().setRadioEnabled(!XgramSettings.instance().inputButtonHidden((String) data), isUpdate);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
  }

  /** Rows of this page. The settings search indexes these too. */
  public static List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramInputButtonsInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < KEYS.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_xgramInputButton, ICONS[i], NAMES[i]).setData(KEYS[i]));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    // The note is about the buttons above (commands, camera, scheduled), so it stays under them
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramInputButtonsNote));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramInputAttachTyping, R.drawable.deproko_baseline_attach_26, R.string.XgramInputAttachTyping)
      .setStringValue(Lang.getString(R.string.XgramInputAttachTypingInfo)));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    return items;
  }

  /** How many buttons are hidden (Settings → XGram → Messages shows it). */
  public static int hiddenCount () {
    int count = 0;
    for (String key : KEYS) {
      if (XgramSettings.instance().inputButtonHidden(key)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramInputAttachTyping) {
      XgramSettings.instance().setInputAttachWhileTyping(adapter.toggleView(v));
      return;
    }
    if (v.getId() == R.id.btn_xgramInputButton && tag instanceof ListItem && ((ListItem) tag).getData() instanceof String) {
      String key = (String) ((ListItem) tag).getData();
      boolean shown = adapter.toggleView(v);
      XgramSettings.instance().setInputButtonHidden(key, !shown);
    }
  }
}
