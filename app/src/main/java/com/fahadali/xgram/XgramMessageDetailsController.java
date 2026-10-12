/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * Message details as in NagramX and moeGramX (moeGramX: Copyright © Kira Roubin, GPLv3).
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
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
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

import me.vkryl.core.StringUtils;
import tgx.td.MessageId;

/**
 * Technical details of a message: IDs, exact dates, forward origin and file information. Every row can be copied.
 */
public final class XgramMessageDetailsController extends RecyclerViewController<XgramMessageDetailsController.Args> implements View.OnClickListener {
  public static final class Args {
    public final TdApi.Message message;

    public Args (TdApi.Message message) {
      this.message = message;
    }
  }

  public XgramMessageDetailsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramMessageDetails;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramMessageDetails);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        view.setData(data instanceof CharSequence ? (CharSequence) data : "");
      }
    };
    adapter.setItems(buildItems(getArgumentsStrict().message), false);
    recyclerView.setAdapter(adapter);
    loadReadDate(getArgumentsStrict().message);
  }

  private SettingsAdapter adapter;
  private ListItem readDateItem;

  // Official Telegram: when the other person read your message (private chats, if both share read times).
  private void loadReadDate (TdApi.Message msg) {
    if (readDateItem == null) {
      return;
    }
    tdlib.send(new TdApi.GetMessageReadDate(msg.chatId, msg.id), (result, error) -> runOnUiThreadOptional(() -> {
      String value;
      if (result == null) {
        value = Lang.getString(R.string.XgramReadDateUnknown);
      } else {
        switch (result.getConstructor()) {
          case TdApi.MessageReadDateRead.CONSTRUCTOR:
            value = formatDate(((TdApi.MessageReadDateRead) result).readDate);
            break;
          case TdApi.MessageReadDateUnread.CONSTRUCTOR:
            value = Lang.getString(R.string.XgramReadDateUnread);
            break;
          case TdApi.MessageReadDateTooOld.CONSTRUCTOR:
            value = Lang.getString(R.string.XgramReadDateTooOld);
            break;
          default:
            value = Lang.getString(R.string.XgramReadDateHidden);
            break;
        }
      }
      readDateItem.setData(value);
      adapter.updateValuedSetting(readDateItem);
    }));
  }

  private List<ListItem> buildItems (TdApi.Message msg) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    add(items, R.drawable.baseline_chat_bubble_24, R.string.XgramDetailsChat, tdlib.chatTitle(msg.chatId) + "\n" + msg.chatId);
    add(items, R.drawable.baseline_identifier_24, R.string.XgramDetailsMessageId, String.valueOf(MessageId.toServerMessageId(msg.id)));
    String sender = tdlib.senderName(msg.senderId);
    long senderId = msg.senderId instanceof TdApi.MessageSenderUser ? ((TdApi.MessageSenderUser) msg.senderId).userId :
      msg.senderId instanceof TdApi.MessageSenderChat ? ((TdApi.MessageSenderChat) msg.senderId).chatId : 0;
    if (!StringUtils.isEmpty(msg.authorSignature)) {
      sender = msg.authorSignature + " (" + sender + ")";
    }
    add(items, R.drawable.baseline_person_24, R.string.XgramDetailsSender, sender + "\n" + senderId);
    add(items, R.drawable.baseline_date_range_24, R.string.XgramDetailsSent, formatDate(msg.date));
    if (msg.editDate != 0 && msg.editDate != msg.date) {
      add(items, R.drawable.baseline_date_range_24, R.string.XgramDetailsEdited, formatDate(msg.editDate));
    }
    if (msg.isOutgoing && msg.chatId > 0 && !tdlib.isSelfChat(msg.chatId) && !tdlib.isBotChat(msg.chatId) && msg.sendingState == null && msg.schedulingState == null) {
      add(items, R.drawable.baseline_done_all_24, R.string.XgramDetailsRead, Lang.getString(R.string.LoadingInformation));
      readDateItem = items.get(items.size() - 2);
    }
    if (msg.topicId instanceof TdApi.MessageTopicForum) {
      add(items, R.drawable.baseline_forum_24, R.string.XgramDetailsTopicId, String.valueOf(((TdApi.MessageTopicForum) msg.topicId).forumTopicId));
    }
    if (msg.forwardInfo != null) {
      add(items, R.drawable.baseline_info_24, R.string.XgramDetailsForwardedFrom, forwardOrigin(msg.forwardInfo.origin) + "\n" + formatDate(msg.forwardInfo.date));
    }
    if (msg.interactionInfo != null) {
      if (msg.interactionInfo.viewCount > 0) {
        add(items, R.drawable.baseline_bar_chart_24, R.string.XgramDetailsViews, String.valueOf(msg.interactionInfo.viewCount));
      }
      if (msg.interactionInfo.forwardCount > 0) {
        add(items, R.drawable.baseline_bar_chart_24, R.string.XgramDetailsForwards, String.valueOf(msg.interactionInfo.forwardCount));
      }
    }

    FileInfo file = fileInfo(msg.content);
    if (file != null) {
      if (!StringUtils.isEmpty(file.name)) {
        add(items, R.drawable.deproko_baseline_text_add_24, R.string.XgramDetailsFileName, file.name);
      }
      if (file.size > 0) {
        add(items, R.drawable.baseline_sd_storage_24, R.string.XgramDetailsFileSize, Formatter.formatFileSize(UI.getContext(), file.size));
      }
      if (!StringUtils.isEmpty(file.mimeType)) {
        add(items, R.drawable.baseline_extension_24, R.string.XgramDetailsMimeType, file.mimeType);
      }
      if (file.width > 0 && file.height > 0) {
        add(items, R.drawable.baseline_crop_original_24, R.string.XgramDetailsResolution, file.width + "×" + file.height);
      }
      if (file.duration > 0) {
        add(items, R.drawable.baseline_access_time_24, R.string.XgramDetailsDuration, DateUtils.formatElapsedTime(file.duration));
      }
      if (!StringUtils.isEmpty(file.localPath)) {
        add(items, R.drawable.baseline_map_24, R.string.XgramDetailsFilePath, file.localPath);
      }
    }

    // Drop the separator after the last row.
    items.remove(items.size() - 1);
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramDetailsInfo));
    return items;
  }

  private void add (List<ListItem> items, @DrawableRes int icon, @StringRes int label, String value) {
    items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_xgramDetailsRow, icon, label).setData(value));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
  }

  private static String formatDate (int unixTime) {
    return com.fahadali.xgram.chats.XgramMessageTime.formatExact(unixTime);
  }

  private String forwardOrigin (TdApi.MessageOrigin origin) {
    switch (origin.getConstructor()) {
      case TdApi.MessageOriginUser.CONSTRUCTOR: {
        long userId = ((TdApi.MessageOriginUser) origin).senderUserId;
        return tdlib.senderName(new TdApi.MessageSenderUser(userId)) + " (" + userId + ")";
      }
      case TdApi.MessageOriginChat.CONSTRUCTOR: {
        long chatId = ((TdApi.MessageOriginChat) origin).senderChatId;
        return tdlib.chatTitle(chatId) + " (" + chatId + ")";
      }
      case TdApi.MessageOriginChannel.CONSTRUCTOR: {
        TdApi.MessageOriginChannel channel = (TdApi.MessageOriginChannel) origin;
        return tdlib.chatTitle(channel.chatId) + " (" + channel.chatId + ", " + MessageId.toServerMessageId(channel.messageId) + ")";
      }
      case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
        return ((TdApi.MessageOriginHiddenUser) origin).senderName;
      default:
        return "";
    }
  }

  private static final class FileInfo {
    String name, mimeType, localPath;
    long size;
    int width, height, duration;
  }

  @Nullable
  private static FileInfo fileInfo (TdApi.MessageContent content) {
    FileInfo info = new FileInfo();
    TdApi.File file;
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) content).photo.sizes;
        if (sizes.length == 0) {
          return null;
        }
        TdApi.PhotoSize biggest = sizes[sizes.length - 1];
        file = biggest.photo;
        info.width = biggest.width;
        info.height = biggest.height;
        info.mimeType = "image/jpeg";
        break;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Video video = ((TdApi.MessageVideo) content).video;
        file = video.video;
        info.name = video.fileName;
        info.mimeType = video.mimeType;
        info.width = video.width;
        info.height = video.height;
        info.duration = video.duration;
        break;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document document = ((TdApi.MessageDocument) content).document;
        file = document.document;
        info.name = document.fileName;
        info.mimeType = document.mimeType;
        break;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) content).audio;
        file = audio.audio;
        info.name = audio.fileName;
        info.mimeType = audio.mimeType;
        info.duration = audio.duration;
        break;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation animation = ((TdApi.MessageAnimation) content).animation;
        file = animation.animation;
        info.name = animation.fileName;
        info.mimeType = animation.mimeType;
        info.width = animation.width;
        info.height = animation.height;
        info.duration = animation.duration;
        break;
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        TdApi.VoiceNote voiceNote = ((TdApi.MessageVoiceNote) content).voiceNote;
        file = voiceNote.voice;
        info.mimeType = voiceNote.mimeType;
        info.duration = voiceNote.duration;
        break;
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) content).videoNote;
        file = videoNote.video;
        info.width = info.height = videoNote.length;
        info.duration = videoNote.duration;
        break;
      }
      case TdApi.MessageSticker.CONSTRUCTOR: {
        TdApi.Sticker sticker = ((TdApi.MessageSticker) content).sticker;
        file = sticker.sticker;
        info.width = sticker.width;
        info.height = sticker.height;
        break;
      }
      default:
        return null;
    }
    info.size = file.size != 0 ? file.size : file.expectedSize;
    info.localPath = file.local != null && file.local.isDownloadingCompleted ? file.local.path : null;
    return info;
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() != R.id.btn_xgramDetailsRow || !(tag instanceof ListItem)) {
      return;
    }
    Object data = ((ListItem) tag).getData();
    if (!(data instanceof String)) {
      return;
    }
    String value = (String) data;
    showOptions(value, new int[] {R.id.btn_copyText, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Copy), Lang.getString(R.string.Cancel)},
      new int[] {OptionColor.NORMAL, OptionColor.NORMAL}, new int[] {R.drawable.baseline_content_copy_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_copyText) {
          UI.copyText(value, R.string.CopiedText);
        }
        return true;
      });
  }
}
