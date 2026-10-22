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

import android.widget.Toast;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.history.XgramHistory;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.StringList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.collection.IntList;
import tgx.td.ChatId;

/**
 * Extra message menu items from AyuGram / NagramX / exteraGram: save to Saved Messages, repeat, messages by the
 * sender, edit history, clear from cache. And chat actions: delete all my messages.
 */
public final class XgramMessageActions {
  private XgramMessageActions () { }

  private static long[] idsOf (TGMessage msg) {
    TdApi.Message[] messages = msg.getAllMessages();
    long[] ids = new long[messages.length];
    for (int i = 0; i < messages.length; i++) {
      ids[i] = messages[i].id;
    }
    return ids;
  }

  /** The message's main file; Telegram X keeps its download state up to date. */
  @androidx.annotation.Nullable
  private static TdApi.File mainFile (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.PhotoSize[] sizes = ((TdApi.MessagePhoto) content).photo.sizes;
        return sizes.length > 0 ? sizes[sizes.length - 1].photo : null;
      }
      case TdApi.MessageVideo.CONSTRUCTOR: return ((TdApi.MessageVideo) content).video.video;
      case TdApi.MessageAnimation.CONSTRUCTOR: return ((TdApi.MessageAnimation) content).animation.animation;
      case TdApi.MessageDocument.CONSTRUCTOR: return ((TdApi.MessageDocument) content).document.document;
      case TdApi.MessageAudio.CONSTRUCTOR: return ((TdApi.MessageAudio) content).audio.audio;
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return ((TdApi.MessageVoiceNote) content).voiceNote.voice;
      case TdApi.MessageVideoNote.CONSTRUCTOR: return ((TdApi.MessageVideoNote) content).videoNote.video;
      default: return null;
    }
  }

  /** The downloaded full-size photo of a single photo message whose content may be saved. */
  private static TdApi.File downloadedPhoto (TGMessage msg) {
    TdApi.MessageContent content = msg.getMessage().content;
    // Albums: the menu doesn't say which photo was pressed.
    if (msg.getAllMessages().length != 1 || content.getConstructor() != TdApi.MessagePhoto.CONSTRUCTOR || !msg.canBeSaved()) {
      return null;
    }
    TdApi.File file = mainFile(content);
    return file != null && file.local.isDownloadingCompleted && !file.local.path.isEmpty() ? file : null;
  }

  private static boolean hasDownloadedFile (TGMessage msg) {
    for (TdApi.Message message : msg.getAllMessages()) {
      TdApi.File file = mainFile(message.content);
      if (file != null && file.id > 0 && file.local.isDownloadingCompleted) {
        return true;
      }
    }
    return false;
  }

  /** Adds the items to a message's menu. */
  public static void fill (MessagesController m, TGMessage msg, IntList ids, IntList icons, StringList strings) {
    Tdlib tdlib = msg.tdlib();
    long chatId = msg.getChatId();
    boolean isSent = !msg.isNotSent() && msg.getMessage().id != 0 && !msg.isSponsoredMessage();
    if (!isSent) {
      return;
    }
    if (msg.canBeForwarded() && chatId != tdlib.selfChatId()) {
      ids.append(R.id.btn_xgramSaveToSaved);
      strings.append(R.string.XgramSaveToSaved);
      icons.append(R.drawable.baseline_bookmark_24);
    }
    if (m.getMessageTopicId() == null && msg.getMessage().topicId instanceof TdApi.MessageTopicForum && tdlib.isForum(chatId)) {
      ids.append(R.id.btn_xgramViewInTopic);
      strings.append(R.string.XgramViewInTopic);
      icons.append(R.drawable.baseline_forum_24);
    }
    boolean bookmarked = XgramBookmarks.isBookmarked(tdlib, chatId, msg.getMessage().id);
    ids.append(R.id.btn_xgramBookmark);
    strings.append(bookmarked ? R.string.XgramRemoveBookmark : R.string.XgramBookmark);
    icons.append(bookmarked ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
    if (!msg.isScheduled()) {
      // Later: a reminder on this device (or a follow-up on your own message), with its time once set
      ids.append(R.id.btn_xgramLaterRemind);
      strings.append(com.fahadali.xgram.later.XgramLater.remindLabel(tdlib, chatId, msg.getMessage().id));
      icons.append(R.drawable.baseline_watch_later_24);
    }
    if (com.fahadali.xgram.xdrive.XDrive.on() && chatId == tdlib.selfChatId()) {
      // XGram XDrive: Saved Messages' folders
      ids.append(R.id.btn_xgramXDriveAddToFolder);
      strings.append(R.string.XgramXDriveAddToFolder);
      icons.append(R.drawable.baseline_create_new_folder_24);
    }
    // Repeat sends a copy; ForwardMessages doesn't support reply threads.
    if (msg.xgramCanBeCopied() && m.canWriteMessages() && !(m.getMessageTopicId() instanceof TdApi.MessageTopicThread)) {
      ids.append(R.id.btn_xgramRepeat);
      strings.append(R.string.XgramRepeat);
      icons.append(R.drawable.baseline_repeat_24);
    }
    int contentType = msg.getMessage().content.getConstructor();
    if (contentType == TdApi.MessageVoiceNote.CONSTRUCTOR || contentType == TdApi.MessageVideoNote.CONSTRUCTOR) {
      ids.append(R.id.btn_xgramTranscribe);
      strings.append(R.string.XgramTranscribe);
      icons.append(R.drawable.baseline_format_text_24);
    }
    if (XgramAiTools.summarizable(msg) != null) {
      ids.append(R.id.btn_xgramSummarize);
      strings.append(R.string.XgramSummarize);
      icons.append(R.drawable.baseline_format_list_bulleted_type_24);
    }
    if (msg.isEdited() && XgramSettings.instance().historySaveEdits()) {
      ids.append(R.id.btn_xgramEditHistory);
      strings.append(R.string.XgramEditHistory);
      icons.append(R.drawable.baseline_history_24);
    }
    TdApi.MessageSender sender = msg.getMessage().senderId;
    if (ChatId.isMultiChat(chatId) && !tdlib.isChannel(chatId) && sender != null && !m.inPreviewMode()) {
      ids.append(R.id.btn_xgramMessagesBySender);
      strings.append(R.string.XgramMessagesBySender);
      icons.append(R.drawable.baseline_person_24);
    }
    if (ChatId.isMultiChat(chatId) && !tdlib.isChannel(chatId) && sender instanceof TdApi.MessageSenderUser && !tdlib.isSelfSender(sender)) {
      ids.append(R.id.btn_xgramReplyPrivately);
      strings.append(R.string.XgramReplyPrivately);
      icons.append(R.drawable.baseline_reply_24);
    }
    if (ChatId.isMultiChat(chatId) && !tdlib.isChannel(chatId) && !msg.isOutgoing() && sender != null && !tdlib.isSelfSender(sender)) {
      boolean banned = XgramFilters.isShadowBanned(tdlib, sender);
      ids.append(R.id.btn_xgramShadowBan);
      strings.append(banned ? R.string.XgramShadowUnban : R.string.XgramShadowBan);
      icons.append(R.drawable.baseline_block_24);
    }
    if (downloadedPhoto(msg) != null) {
      ids.append(R.id.btn_xgramCopyPhoto);
      strings.append(R.string.XgramCopyPhoto);
      icons.append(R.drawable.baseline_content_copy_24);
    }
    if (hasDownloadedFile(msg)) {
      ids.append(R.id.btn_xgramClearFromCache);
      strings.append(R.string.XgramClearFromCache);
      icons.append(R.drawable.baseline_delete_sweep_24);
    }
  }

  /** Handles a menu item added by {@link #fill}. Returns false for other items. */
  public static boolean handle (MessagesController m, TGMessage msg, int id) {
    Tdlib tdlib = msg.tdlib();
    long chatId = msg.getChatId();
    if (id == R.id.btn_xgramSaveToSaved) {
      XgramUndoSend.flushBefore(tdlib, tdlib.selfChatId()); // Undo send: what waits in Saved Messages goes first
      tdlib.send(new TdApi.ForwardMessages(tdlib.selfChatId(), null, chatId, idsOf(msg), new TdApi.MessageSendOptions(), false, false), (result, error) -> tdlib.ui().post(() -> {
        if (error != null) {
          UI.showError(error);
        } else {
          UI.showToast(R.string.XgramSavedToSaved, Toast.LENGTH_SHORT);
        }
      }));
      return true;
    }
    if (id == R.id.btn_xgramCopyPhoto) {
      // Copy photo (NagramX, exteraGram): the downloaded image goes to the clipboard as a content URI.
      TdApi.File file = downloadedPhoto(msg);
      android.net.Uri uri = file != null ? org.thunderdog.challegram.U.contentUriFromFile(new java.io.File(file.local.path)) : null;
      if (uri == null) {
        UI.showToast(R.string.XgramPhotoCopyFailed, Toast.LENGTH_SHORT);
      } else {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) UI.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
          clipboard.setPrimaryClip(android.content.ClipData.newUri(UI.getContext().getContentResolver(), "image", uri));
          if (android.os.Build.VERSION.SDK_INT < 33) {
            // Android 13+ shows its own confirmation.
            UI.showToast(R.string.XgramPhotoCopied, Toast.LENGTH_SHORT);
          }
        }
      }
      return true;
    }
    if (id == R.id.btn_xgramViewInTopic) {
      TdApi.Message message = msg.getMessage();
      if (message.topicId instanceof TdApi.MessageTopicForum) {
        com.fahadali.xgram.topics.XgramForums.openTopicLink(m, chatId, (TdApi.MessageTopicForum) message.topicId, message, null);
      }
      return true;
    }
    if (id == R.id.btn_xgramBookmark) {
      long messageId = msg.getMessage().id;
      boolean bookmark = !XgramBookmarks.isBookmarked(tdlib, chatId, messageId);
      XgramBookmarks.setBookmarked(tdlib, chatId, messageId, bookmark);
      UI.showToast(bookmark ? R.string.XgramBookmarked : R.string.XgramBookmarkRemoved, Toast.LENGTH_SHORT);
      return true;
    }
    if (id == R.id.btn_xgramLaterRemind) {
      com.fahadali.xgram.later.XgramLaterPicker.showForMessage(m, msg);
      return true;
    }
    if (id == R.id.btn_xgramXDriveAddToFolder) {
      com.fahadali.xgram.xdrive.XDrive.pickFolder(m, idsOf(msg));
      return true;
    }
    if (id == R.id.btn_xgramRepeat) {
      // "+1": send the same content again as a copy (no "forwarded from" header).
      XgramUndoSend.flushBefore(tdlib, chatId); // Undo send: what waits in this chat goes first
      tdlib.send(new TdApi.ForwardMessages(chatId, m.getMessageTopicId(), chatId, idsOf(msg), new TdApi.MessageSendOptions(), true, false), (result, error) -> {
        if (error != null) {
          tdlib.ui().post(() -> UI.showError(error));
        }
      });
      return true;
    }
    if (id == R.id.btn_xgramMessagesBySender) {
      m.viewMessagesFromSender(msg.getMessage().senderId, true);
      return true;
    }
    if (id == R.id.btn_xgramClearFromCache) {
      for (TdApi.Message message : msg.getAllMessages()) {
        TdApi.File file = mainFile(message.content);
        if (file != null && file.id > 0) {
          tdlib.send(new TdApi.DeleteFile(file.id), tdlib.typedOkHandler());
        }
      }
      UI.showToast(R.string.XgramClearedFromCache, Toast.LENGTH_SHORT);
      return true;
    }
    if (id == R.id.btn_xgramTranscribe) {
      transcribe(m, msg.getMessage());
      return true;
    }
    if (id == R.id.btn_xgramSummarize) {
      TdApi.Message message = XgramAiTools.summarizable(msg);
      if (message != null) {
        XgramAiTools.summarize(m, message, "");
      }
      return true;
    }
    if (id == R.id.btn_xgramReplyPrivately) {
      replyPrivately(m, msg.getMessage());
      return true;
    }
    if (id == R.id.btn_xgramShadowBan) {
      TdApi.MessageSender sender = msg.getMessage().senderId;
      boolean ban = !XgramFilters.isShadowBanned(tdlib, sender);
      XgramFilters.setShadowBanned(tdlib, sender, ban);
      UI.showToast(ban ? R.string.XgramShadowBannedToast : R.string.XgramShadowUnbanned, Toast.LENGTH_SHORT);
      m.reloadData();
      return true;
    }
    if (id == R.id.btn_xgramEditHistory) {
      showEditHistory(m, msg);
      return true;
    }
    return false;
  }

  // Voice and video message transcription (TDLib recognizeSpeech: Premium, or the free trials Telegram gives)

  @androidx.annotation.Nullable
  private static TdApi.SpeechRecognitionResult speechResultOf (TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR: return ((TdApi.MessageVoiceNote) content).voiceNote.speechRecognitionResult;
      case TdApi.MessageVideoNote.CONSTRUCTOR: return ((TdApi.MessageVideoNote) content).videoNote.speechRecognitionResult;
      default: return null;
    }
  }

  private static void transcribe (ViewController<?> context, TdApi.Message message) {
    Tdlib tdlib = context.tdlib();
    TdApi.SpeechRecognitionResult known = speechResultOf(message.content);
    if (known instanceof TdApi.SpeechRecognitionResultText) {
      showTranscription(context, ((TdApi.SpeechRecognitionResultText) known).text);
      return;
    }
    UI.showToast(R.string.XgramTranscribing, Toast.LENGTH_SHORT);
    tdlib.send(new TdApi.RecognizeSpeech(message.chatId, message.id), (ok, error) -> {
      if (error != null) {
        tdlib.ui().post(() -> UI.showError(error));
      } else {
        pollTranscription(context, message.chatId, message.id, 0);
      }
    });
  }

  /** The result arrives as a message content update; asking for the message again is simpler than listening. */
  private static void pollTranscription (ViewController<?> context, long chatId, long messageId, int attempt) {
    Tdlib tdlib = context.tdlib();
    if (attempt > 40 || context.isDestroyed()) {
      return;
    }
    tdlib.ui().postDelayed(() -> tdlib.send(new TdApi.GetMessage(chatId, messageId), (message, error) -> {
      TdApi.SpeechRecognitionResult result = message != null ? speechResultOf(message.content) : null;
      if (result instanceof TdApi.SpeechRecognitionResultText) {
        String text = ((TdApi.SpeechRecognitionResultText) result).text;
        tdlib.ui().post(() -> {
          if (!context.isDestroyed()) {
            showTranscription(context, text);
          }
        });
      } else if (result instanceof TdApi.SpeechRecognitionResultError) {
        TdApi.Error resultError = ((TdApi.SpeechRecognitionResultError) result).error;
        tdlib.ui().post(() -> UI.showError(resultError));
      } else {
        pollTranscription(context, chatId, messageId, attempt + 1);
      }
    }), 750);
  }

  private static void showTranscription (ViewController<?> context, String text) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E r5: the transcription in a surfaceContainerHigh card (as the Quote and AI sheets) with a tonal
      // "Copy", no Cancel row
      com.fahadali.xgram.appearance.XgramR5SheetsInfo.showText(context, Lang.getString(R.string.XgramTranscribe), text,
        new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.Copy), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_TONAL,
          () -> com.fahadali.xgram.appearance.XgramR5SheetsInfo.copy(text)));
      return;
    }
    context.showOptions(text,
      new int[] {R.id.btn_copyText, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.Copy), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_content_copy_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
        if (optionId == R.id.btn_copyText) {
          UI.copyText(text, R.string.CopiedText);
        }
        return true;
      });
  }

  /** Opens a private chat with the sender with a reply to their group message (as official Telegram does). */
  private static void replyPrivately (ViewController<?> context, TdApi.Message message) {
    Tdlib tdlib = context.tdlib();
    long userId = ((TdApi.MessageSenderUser) message.senderId).userId;
    tdlib.send(new TdApi.CreatePrivateChat(userId, false), (chat, error) -> {
      if (chat == null) {
        tdlib.ui().post(() -> UI.showError(error));
        return;
      }
      // Keep what was already typed in that chat.
      TdApi.DraftMessageContent content = chat.draftMessage != null && chat.draftMessage.content != null ?
        chat.draftMessage.content :
        new TdApi.DraftMessageContentText(new TdApi.FormattedText("", new TdApi.TextEntity[0]), null);
      TdApi.DraftMessage draft = new TdApi.DraftMessage(
        new TdApi.InputMessageReplyToExternalMessage(message.chatId, message.id, null, 0, ""),
        (int) (System.currentTimeMillis() / 1000L),
        content,
        0, null
      );
      tdlib.send(new TdApi.SetChatDraftMessage(chat.id, null, draft), (ok, draftError) -> tdlib.ui().post(() -> {
        if (!context.isDestroyed()) {
          tdlib.ui().openChat(context, chat.id, null);
        }
      }));
    });
  }

  private static void showEditHistory (ViewController<?> context, TGMessage msg) {
    Tdlib tdlib = msg.tdlib();
    TdApi.Message message = msg.getMessage();
    XgramHistory.instance().getEdits(tdlib.id(), message.chatId, message.id, edits -> {
      if (context.isDestroyed()) {
        return;
      }
      if (edits.isEmpty()) {
        UI.showToast(R.string.XgramEditHistoryEmpty, Toast.LENGTH_SHORT);
        return;
      }
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        showEditHistoryM3(context, msg, message, edits);
        return;
      }
      StringBuilder b = new StringBuilder();
      for (int i = edits.size() - 1; i >= 0; i--) {
        XgramHistory.Edit edit = edits.get(i);
        if (b.length() > 0) {
          b.append("\n\n");
        }
        b.append(Lang.timeOrDateShort(edit.editDate, TimeUnit.SECONDS)).append(":\n").append(edit.oldText);
      }
      String text = b.toString();
      context.showOptions(Lang.getString(R.string.XgramEditHistory) + "\n\n" + text,
        new int[] {R.id.btn_copyText, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Copy), Lang.getString(R.string.Cancel)},
        new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_content_copy_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
          if (optionId == R.id.btn_copyText) {
            UI.copyText(text, R.string.CopiedText);
          }
          return true;
        });
    });
  }

  /**
   * XGram M3E r5: the edit history sheet, titled "Edit history": every version as a segmented tile, newest first, its
   * time in labelMedium over its text in bodyLarge, the current one marked "Current"; tapping a version copies it.
   * A tonal "Copy" copies the whole history.
   */
  private static void showEditHistoryM3 (ViewController<?> context, TGMessage msg, TdApi.Message message, List<XgramHistory.Edit> edits) {
    java.util.List<com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row> rows = new ArrayList<>(edits.size() + 1);
    StringBuilder all = new StringBuilder();
    TdApi.FormattedText current = tgx.td.Td.textOrCaption(message.content);
    final int lastEdit = edits.get(edits.size() - 1).editDate;
    if (current != null && !me.vkryl.core.StringUtils.isEmpty(current.text)) {
      int currentDate = msg.getEditDate() != 0 ? msg.getEditDate() : lastEdit;
      String time = Lang.timeOrDateShort(currentDate, TimeUnit.SECONDS);
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(current.text, time)
        .supportingFirst()
        .badge(Lang.getString(R.string.XgramR5SheetsCurrent))
        .maxLines(0)
        .copy(current.text));
      all.append(time).append(":\n").append(current.text);
    }
    // a version was written at the edit before it (the message's own date for the first one)
    for (int i = edits.size() - 1; i >= 0; i--) {
      XgramHistory.Edit edit = edits.get(i);
      int writtenAt = i > 0 ? edits.get(i - 1).editDate : message.date;
      String time = Lang.timeOrDateShort(writtenAt != 0 ? writtenAt : edit.editDate, TimeUnit.SECONDS);
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(edit.oldText != null ? edit.oldText : "", time)
        .supportingFirst()
        .maxLines(0)
        .copy(edit.oldText));
      if (all.length() > 0) {
        all.append("\n\n");
      }
      all.append(time).append(":\n").append(edit.oldText);
    }
    final String allText = all.toString();
    com.fahadali.xgram.appearance.XgramR5SheetsInfo.show(context, Lang.getString(R.string.XgramR5SheetsEditHistory), rows,
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.Copy), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_TONAL,
        () -> com.fahadali.xgram.appearance.XgramR5SheetsInfo.copy(allText)));
  }

  // Chat actions

  public static boolean canDeleteMyMessages (Tdlib tdlib, long chatId) {
    return ChatId.isMultiChat(chatId) && !tdlib.isChannel(chatId);
  }

  /** Deletes every message of yours in the chat, for everyone (AyuGram / NagramX "Delete my messages"). */
  public static void confirmDeleteMyMessages (ViewController<?> context, long chatId) {
    Tdlib tdlib = context.tdlib();
    context.showOptions(Lang.getString(R.string.XgramDeleteMyMessagesConfirm, tdlib.chatTitle(chatId)),
      new int[] {R.id.btn_xgramDeleteMyMessages, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramDeleteMyMessages), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_xgramDeleteMyMessages) {
          UI.showToast(R.string.XgramDeletingMyMessages, Toast.LENGTH_SHORT);
          deleteMyMessages(tdlib, chatId, 0, 0);
        }
        return true;
      });
  }

  private static void deleteMyMessages (Tdlib tdlib, long chatId, long fromMessageId, int deletedSoFar) {
    tdlib.send(new TdApi.SearchChatMessages(chatId, null, "", tdlib.mySender(), fromMessageId, 0, 100, null), (found, error) -> {
      if (error != null || found == null || found.messages.length == 0) {
        tdlib.ui().post(() -> UI.showToast(Lang.getString(R.string.XgramDeletedMyMessages, deletedSoFar), Toast.LENGTH_SHORT));
        return;
      }
      List<Long> ids = new ArrayList<>(found.messages.length);
      long oldest = 0;
      for (TdApi.Message message : found.messages) {
        ids.add(message.id);
        oldest = message.id;
      }
      long[] messageIds = new long[ids.size()];
      for (int i = 0; i < messageIds.length; i++) {
        messageIds[i] = ids.get(i);
      }
      tdlib.deleteMessages(chatId, messageIds, true);
      long next = found.nextFromMessageId != 0 ? found.nextFromMessageId : oldest;
      if (next == fromMessageId) {
        tdlib.ui().post(() -> UI.showToast(Lang.getString(R.string.XgramDeletedMyMessages, deletedSoFar + messageIds.length), Toast.LENGTH_SHORT));
        return;
      }
      deleteMyMessages(tdlib, chatId, next, deletedSoFar + messageIds.length);
    });
  }
}
