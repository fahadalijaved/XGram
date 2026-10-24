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

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramReorder;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.IntList;

/**
 * Settings → XGram → Chat list → Chat action order: the order of the actions a long press on a chat shows, in the chat
 * preview's bar and in XGram's quick actions pill. Telegram X's order by default.
 */
public final class XgramChatActionsController extends RecyclerViewController<Void> implements View.OnClickListener {
  // XGram: public for the Compose Chat actions page (xgram-m3e)
  public static final String[] KEYS = {"call", "notifications", "pin", "read", "archive", "snooze", "folder", "delete", "select"};
  public static final int[] NAMES = {R.string.Call, R.string.Mute, R.string.Pin, R.string.MarkAsRead, R.string.Archive, R.string.XgramLaterSnoozeShort, R.string.AddToFolder, R.string.Delete, R.string.MoreChatOptions};
  public static final int[] ICONS = {R.drawable.baseline_call_24, R.drawable.baseline_notifications_off_24, R.drawable.deproko_baseline_pin_24, Config.ICON_MARK_AS_READ, R.drawable.baseline_archive_24, R.drawable.baseline_notifications_paused_24, R.drawable.templarian_baseline_folder_plus_24, R.drawable.baseline_delete_24, R.drawable.baseline_more_horiz_24};

  @Nullable
  private static String keyOfId (Integer id) {
    if (id == R.id.btn_phone_call) {
      return "call";
    } else if (id == R.id.btn_notifications) {
      return "notifications";
    } else if (id == R.id.btn_pinUnpinChat) {
      return "pin";
    } else if (id == R.id.btn_markChatAsRead || id == R.id.btn_markChatAsUnread) {
      return "read";
    } else if (id == R.id.btn_archiveUnarchiveChat) {
      return "archive";
    } else if (id == R.id.btn_xgramLaterSnoozeChat) {
      return "snooze"; // Later
    } else if (id == R.id.btn_addChatToFolder) {
      return "folder";
    } else if (id == R.id.btn_removeChatFromListOrClearHistory) {
      return "delete";
    } else if (id == R.id.btn_selectChat) {
      return "select";
    }
    return null;
  }

  /** From TdlibUi.createSimpleChatActions: puts a chat's actions in the user's order. */
  public static void order (IntList ids, IntList icons, StringList strings) {
    XgramReorder.sort(XgramReorder.CHAT_ACTIONS, ids, icons, strings, XgramChatActionsController::keyOfId);
  }

  private SettingsAdapter adapter;
  private XgramReorder.Ui reorder;

  public XgramChatActionsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramChatActionsOrder;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramChatActionsOrder);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (reorder != null) {
          reorder.bind(item, view);
        }
      }
    };
    adapter.setItems(buildItems(), false);
    recyclerView.setAdapter(adapter);
    reorder = new XgramReorder.Ui(recyclerView, adapter, XgramReorder.CHAT_ACTIONS, item -> item.getId() == R.id.btn_xgramChatAction ? (String) item.getData() : null, null, null);
  }

  /** Rows of this page. The settings search indexes these too. */
  public static List<ListItem> buildItems () {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramChatActionsOrderInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (String key : XgramReorder.ordered(XgramReorder.CHAT_ACTIONS, KEYS)) {
      for (int i = 0; i < KEYS.length; i++) {
        if (KEYS[i].equals(key)) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramChatAction, ICONS[i], NAMES[i]).setData(key));
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
    if (v.getId() == R.id.btn_xgramResetOrder) {
      XgramReorder.reset(XgramReorder.CHAT_ACTIONS);
      adapter.setItems(buildItems(), false);
    }
  }
}
