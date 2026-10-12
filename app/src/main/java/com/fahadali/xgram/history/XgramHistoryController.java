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
package com.fahadali.xgram.history;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

/**
 * Deleted and edited messages saved by {@link XgramHistory}: a list of chats, then the messages of one chat.
 */
public final class XgramHistoryController extends RecyclerViewController<XgramHistoryController.Args> implements View.OnClickListener {
  public static final class Args {
    /** 0 lists chats; otherwise the messages of this chat. */
    public final long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  private SettingsAdapter adapter;

  public XgramHistoryController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long chatId () {
    Args args = getArguments();
    return args != null ? args.chatId : 0;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramHistory;
  }

  @Override
  public CharSequence getName () {
    long chatId = chatId();
    return chatId != 0 ? tdlib.chatTitle(chatId) : Lang.getString(R.string.XgramHistoryView);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        if (data instanceof XgramHistory.ChatEntry) {
          view.setData(Lang.getString(R.string.XgramHistoryCount, ((XgramHistory.ChatEntry) data).count));
        } else if (data instanceof XgramHistory.Entry) {
          view.setData(previewOf((XgramHistory.Entry) data));
        }
      }
    };
    recyclerView.setAdapter(adapter);
    load();
  }

  private void load () {
    long chatId = chatId();
    if (chatId == 0) {
      XgramHistory.instance().getChats(tdlib.id(), chats -> {
        if (isDestroyed()) {
          return;
        }
        List<ListItem> items = new ArrayList<>();
        if (chats.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.XgramHistoryEmpty));
        } else {
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          boolean first = true;
          for (XgramHistory.ChatEntry chat : chats) {
            if (!first) {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR));
            }
            first = false;
            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramHistoryChat, 0, tdlib.chatTitle(chat.chatId), false).setData(chat));
          }
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramHistoryInfo));
        }
        adapter.setItems(items, false);
        executeScheduledAnimation();
      });
    } else {
      XgramHistory.instance().getMessages(tdlib.id(), chatId, messages -> {
        if (isDestroyed()) {
          return;
        }
        List<ListItem> items = new ArrayList<>();
        if (messages.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.XgramHistoryEmpty));
        } else {
          items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          boolean first = true;
          for (XgramHistory.Entry entry : messages) {
            if (!first) {
              items.add(new ListItem(ListItem.TYPE_SEPARATOR));
            }
            first = false;
            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramHistoryMessage, 0, titleOf(entry), false).setData(entry));
          }
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramHistoryClear, R.drawable.baseline_delete_24, R.string.XgramHistoryClear).setTextColorId(org.thunderdog.challegram.theme.ColorId.textNegative));
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }
        adapter.setItems(items, false);
        executeScheduledAnimation();
      });
    }
  }

  // Formatting

  private String senderName (long senderId) {
    if (senderId > 0) {
      return tdlib.senderName(new org.drinkless.tdlib.TdApi.MessageSenderUser(senderId));
    } else if (senderId < 0) {
      return tdlib.senderName(new org.drinkless.tdlib.TdApi.MessageSenderChat(senderId));
    }
    return "";
  }

  /** "Sender · Deleted 12:30" or "Sender · Edited 2×". */
  private CharSequence titleOf (XgramHistory.Entry entry) {
    String sender = senderName(entry.senderId);
    String state;
    if (entry.deletedDate != 0) {
      state = Lang.getString(R.string.XgramHistoryDeletedAt, Lang.timeOrDateShort(entry.deletedDate, TimeUnit.SECONDS));
    } else {
      state = Lang.getString(R.string.XgramHistoryEditedTimes, entry.edits.size());
    }
    return StringUtils.isEmpty(sender) ? state : sender + " · " + state;
  }

  private static String kindLabel (String kind) {
    switch (kind) {
      case XgramHistory.Kind.PHOTO: return Lang.getString(R.string.XgramKindPhoto);
      case XgramHistory.Kind.VIDEO: return Lang.getString(R.string.XgramKindVideo);
      case XgramHistory.Kind.GIF: return Lang.getString(R.string.XgramKindGif);
      case XgramHistory.Kind.FILE: return Lang.getString(R.string.XgramKindFile);
      case XgramHistory.Kind.MUSIC: return Lang.getString(R.string.XgramKindMusic);
      case XgramHistory.Kind.VOICE: return Lang.getString(R.string.XgramKindVoice);
      case XgramHistory.Kind.ROUND: return Lang.getString(R.string.XgramKindRound);
      case XgramHistory.Kind.STICKER: return Lang.getString(R.string.XgramKindSticker);
      default: return null;
    }
  }

  private static String previewOf (XgramHistory.Entry entry) {
    String label = kindLabel(entry.kind);
    String text = entry.text.replace('\n', ' ');
    if (label == null) {
      return text;
    }
    return StringUtils.isEmpty(text) ? label : label + ": " + text;
  }

  private static String fullTextOf (XgramHistory.Entry entry) {
    StringBuilder b = new StringBuilder();
    String label = kindLabel(entry.kind);
    if (label != null) {
      b.append('[').append(label).append("] ");
    }
    b.append(entry.text);
    if (!entry.edits.isEmpty()) {
      b.append("\n\n").append(Lang.getString(R.string.XgramHistoryPreviousVersions));
      for (int i = entry.edits.size() - 1; i >= 0; i--) {
        XgramHistory.Edit edit = entry.edits.get(i);
        b.append("\n\n").append(Lang.timeOrDateShort(edit.editDate, TimeUnit.SECONDS)).append(": ").append(edit.oldText);
      }
    }
    return b.toString();
  }

  // Clicks

  @Override
  public void onClick (View v) {
    int id = v.getId();
    Object tag = v.getTag();
    @Nullable Object data = tag instanceof ListItem ? ((ListItem) tag).getData() : null;
    if (id == R.id.btn_xgramHistoryChat && data instanceof XgramHistory.ChatEntry) {
      XgramHistoryController c = new XgramHistoryController(context, tdlib);
      c.setArguments(new Args(((XgramHistory.ChatEntry) data).chatId));
      navigateTo(c);
    } else if (id == R.id.btn_xgramHistoryMessage && data instanceof XgramHistory.Entry) {
      XgramHistory.Entry entry = (XgramHistory.Entry) data;
      String fullText = fullTextOf(entry);
      if (entry.hasSavedMedia()) {
        showOptions(fullText, new int[] {R.id.btn_xgramHistoryOpenMedia, R.id.btn_copyText, R.id.btn_cancel},
          new String[] {Lang.getString(R.string.XgramHistoryOpenMedia), Lang.getString(R.string.Copy), Lang.getString(R.string.Cancel)},
          new int[] {OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.NORMAL},
          new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_content_copy_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
            if (optionId == R.id.btn_xgramHistoryOpenMedia) {
              UI.openFile(this, StringUtils.isEmpty(entry.fileName) ? null : entry.fileName, new java.io.File(entry.mediaPath), entry.mimeType, 0);
            } else if (optionId == R.id.btn_copyText) {
              UI.copyText(entry.text, R.string.CopiedText);
            }
            return true;
          });
        return;
      }
      showOptions(fullText, new int[] {R.id.btn_copyText, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Copy), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.NORMAL, OptionColor.NORMAL}, new int[] {R.drawable.baseline_content_copy_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_copyText) {
            UI.copyText(entry.text, R.string.CopiedText);
          }
          return true;
        });
    } else if (id == R.id.btn_xgramHistoryClear) {
      long chatId = chatId();
      showOptions(Lang.getString(R.string.XgramHistoryClearConfirm), new int[] {R.id.btn_xgramHistoryClear, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.XgramHistoryClear), Lang.getString(R.string.Cancel)},
        new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_xgramHistoryClear) {
            XgramHistory.instance().clear(tdlib.id(), chatId, () -> {
              if (!isDestroyed()) {
                navigateBack();
              }
            });
          }
          return true;
        });
    }
  }
}
