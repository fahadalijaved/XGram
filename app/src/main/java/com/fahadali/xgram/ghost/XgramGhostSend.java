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
package com.fahadali.xgram.ghost;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager;
import org.thunderdog.challegram.telegram.TdlibManager;

import tgx.td.ChatId;

/**
 * Ghost mode sending (AyuGram). "Send as scheduled": while staying offline, messages go out as scheduled ones a few
 * seconds later, so sending doesn't show you online; uploads, bots, secret chats and Saved Messages are sent normally.
 * "Send silently": messages are sent without notification sound. Installed as {@link Client#queryInterceptor} only
 * while one of them is on.
 */
public final class XgramGhostSend implements Client.QueryInterceptor {
  // TDLib sends at once anything scheduled up to 10 s after the server time: keep a clear margin above that.
  private static final int DELAY_SECONDS = 20;
  private static final XgramGhostSend INSTANCE = new XgramGhostSend();

  private XgramGhostSend () { }

  private static volatile boolean scheduleEnabled, silentEnabled;

  public static boolean isScheduleEnabled () {
    return scheduleEnabled;
  }

  /** Installs or removes the interceptor; called by {@link XgramSettings} when the options change. */
  public static void apply (boolean schedule, boolean silent) {
    scheduleEnabled = schedule;
    silentEnabled = silent;
    Client.queryInterceptor = schedule || silent ? INSTANCE : null;
  }

  @Override
  public void onSend (Client client, TdApi.Function<?> query) {
    final long chatId;
    final TdApi.MessageSendOptions options;
    final TdApi.MessageTopic topicId;
    boolean upload = false;
    switch (query.getConstructor()) {
      case TdApi.SendMessage.CONSTRUCTOR: {
        TdApi.SendMessage f = (TdApi.SendMessage) query;
        upload = needsUpload(f.inputMessageContent) || !canBeScheduled(f.inputMessageContent);
        chatId = f.chatId;
        options = f.options;
        topicId = f.topicId;
        break;
      }
      case TdApi.SendMessageAlbum.CONSTRUCTOR: {
        TdApi.SendMessageAlbum f = (TdApi.SendMessageAlbum) query;
        for (TdApi.InputMessageContent content : f.inputMessageContents) {
          upload |= needsUpload(content) || !canBeScheduled(content);
        }
        chatId = f.chatId;
        options = f.options;
        topicId = f.topicId;
        break;
      }
      case TdApi.SendInlineQueryResultMessage.CONSTRUCTOR: {
        TdApi.SendInlineQueryResultMessage f = (TdApi.SendInlineQueryResultMessage) query;
        chatId = f.chatId;
        options = f.options;
        topicId = f.topicId;
        break;
      }
      case TdApi.ForwardMessages.CONSTRUCTOR: {
        TdApi.ForwardMessages f = (TdApi.ForwardMessages) query;
        chatId = f.chatId;
        options = f.options;
        topicId = f.topicId;
        break;
      }
      default:
        return;
    }
    if (options != null && options.onlyPreview) {
      return;
    }
    boolean silent = silentEnabled && (options == null || !options.disableNotification);
    // Files that still have to be uploaded could miss the scheduled time, so those messages are sent normally.
    boolean schedule = scheduleEnabled && !upload && (options == null || options.schedulingState == null) && !ChatId.isSecret(chatId);
    if (!silent && !schedule) {
      return;
    }
    Tdlib tdlib = findTdlib(client);
    if (tdlib == null || tdlib.isSelfChat(chatId)) {
      return;
    }
    // TDLib can't schedule in bot chats, channel direct messages, comment threads and chats where messages cost Stars.
    if (schedule && (tdlib.isBotChat(chatId) || tdlib.isDirectMessagesChat(chatId) || topicId instanceof TdApi.MessageTopicDirectMessages || topicId instanceof TdApi.MessageTopicThread || (options != null && options.paidMessageStarCount > 0))) {
      schedule = false;
    }
    if (!silent && !schedule) {
      return;
    }
    TdApi.MessageSchedulingState schedulingState = schedule ?
      new TdApi.MessageSchedulingStateSendAtDate((int) tdlib.currentTime(java.util.concurrent.TimeUnit.SECONDS) + DELAY_SECONDS, 0) :
      options != null ? options.schedulingState : null;
    // A copy: Telegram X may share one options object between several sends (e.g. sharing to many chats).
    TdApi.MessageSendOptions copy = copyOptions(options, silent || (options != null && options.disableNotification), schedulingState);
    switch (query.getConstructor()) {
      case TdApi.SendMessage.CONSTRUCTOR:
        ((TdApi.SendMessage) query).options = copy;
        break;
      case TdApi.SendMessageAlbum.CONSTRUCTOR:
        ((TdApi.SendMessageAlbum) query).options = copy;
        break;
      case TdApi.SendInlineQueryResultMessage.CONSTRUCTOR:
        ((TdApi.SendInlineQueryResultMessage) query).options = copy;
        break;
      case TdApi.ForwardMessages.CONSTRUCTOR:
        ((TdApi.ForwardMessages) query).options = copy;
        break;
    }
    // A scheduled message isn't sent yet, so "read on send" can't wait for it: read the chat (or the topic) now.
    if (schedule && XgramSettings.instance().ghostReadOnSend() && XgramGhost.dontReadMessages(tdlib, chatId)) {
      TdApi.Message last = lastMessageOf(tdlib, chatId, topicId);
      if (last != null) {
        // Undo send: a message that waited while its chat was out of sight reads only up to what was on screen
        XgramGhost.readUpTo(tdlib, last, XgramGhost.capped(last.id, XgramGhost.takeQueryReadCap(query)));
      }
    }
  }

  /** The last message where the message goes: the chat, or its forum topic (General when none is given). */
  @Nullable
  static TdApi.Message lastMessageOf (Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId) {
    if (topicId instanceof TdApi.MessageTopicForum || (topicId == null && tdlib.isForum(chatId))) {
      int forumTopicId = topicId != null ? ((TdApi.MessageTopicForum) topicId).forumTopicId : com.fahadali.xgram.topics.XgramForums.GENERAL_TOPIC_ID;
      if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
        // Client.send runs on the caller's thread. The topic list cache is UI-thread only; the topic manager is synchronized.
        TdlibForumTopicManager.Entry entry = tdlib.topics().find(new TdlibForumTopicManager.Key(chatId, forumTopicId));
        return entry != null && entry.value != null ? entry.value.lastMessage : null;
      }
      TdApi.ForumTopic topic = com.fahadali.xgram.topics.ForumTopicList.peekTopic(tdlib, chatId, forumTopicId);
      return topic != null ? topic.lastMessage : null;
    }
    if (topicId != null) {
      // Threads and other topics: their last message isn't known here.
      return null;
    }
    TdApi.Chat chat = tdlib.chat(chatId);
    return chat != null ? chat.lastMessage : null;
  }

  private static TdApi.MessageSendOptions copyOptions (@Nullable TdApi.MessageSendOptions options, boolean disableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
    if (options == null) {
      TdApi.MessageSendOptions result = new TdApi.MessageSendOptions();
      result.disableNotification = disableNotification;
      result.schedulingState = schedulingState;
      return result;
    }
    return new TdApi.MessageSendOptions(
      options.suggestedPostInfo,
      disableNotification,
      options.fromBackground,
      options.protectContent,
      options.allowPaidBroadcast,
      options.paidMessageStarCount,
      options.updateOrderOfInstalledStickerSets,
      schedulingState,
      options.effectId,
      options.sendingId,
      options.onlyPreview
    );
  }

  @Nullable
  private static Tdlib findTdlib (Client client) {
    for (TdlibAccount account : TdlibManager.instance()) {
      Tdlib tdlib = account.activeTdlib();
      if (tdlib != null && tdlib.xgramOwnsClient(client)) {
        return tdlib;
      }
    }
    return null;
  }

  private static boolean isUpload (@Nullable TdApi.InputFile file) {
    return file instanceof TdApi.InputFileLocal || file instanceof TdApi.InputFileGenerated;
  }

  /** Live locations and self-destructing media can't be scheduled. */
  private static boolean canBeScheduled (TdApi.InputMessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.InputMessageLiveLocation.CONSTRUCTOR:
        return false;
      case TdApi.InputMessagePhoto.CONSTRUCTOR:
        return ((TdApi.InputMessagePhoto) content).selfDestructType == null;
      case TdApi.InputMessageVideo.CONSTRUCTOR:
        return ((TdApi.InputMessageVideo) content).selfDestructType == null;
      default:
        return true;
    }
  }

  /** Files that still have to be uploaded could miss the scheduled time, so those messages are sent normally. */
  private static boolean needsUpload (TdApi.InputMessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR: {
        TdApi.InputPhoto photo = ((TdApi.InputMessagePhoto) content).photo;
        return photo == null || isUpload(photo.photo);
      }
      case TdApi.InputMessageVideo.CONSTRUCTOR: {
        TdApi.InputVideo video = ((TdApi.InputMessageVideo) content).video;
        return video == null || isUpload(video.video);
      }
      case TdApi.InputMessageAnimation.CONSTRUCTOR: {
        TdApi.InputAnimation animation = ((TdApi.InputMessageAnimation) content).animation;
        return animation == null || isUpload(animation.animation);
      }
      case TdApi.InputMessageDocument.CONSTRUCTOR: {
        TdApi.InputDocument document = ((TdApi.InputMessageDocument) content).document;
        return document == null || isUpload(document.document);
      }
      case TdApi.InputMessageAudio.CONSTRUCTOR: {
        TdApi.InputAudio audio = ((TdApi.InputMessageAudio) content).audio;
        return audio == null || isUpload(audio.audio);
      }
      case TdApi.InputMessageSticker.CONSTRUCTOR: {
        TdApi.InputSticker sticker = ((TdApi.InputMessageSticker) content).sticker;
        return sticker == null || isUpload(sticker.sticker);
      }
      case TdApi.InputMessageVoiceNote.CONSTRUCTOR:
      case TdApi.InputMessageVideoNote.CONSTRUCTOR:
      case TdApi.InputMessagePaidMedia.CONSTRUCTOR:
        return true;
      default:
        return false;
    }
  }
}
