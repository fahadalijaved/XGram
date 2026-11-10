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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.HashSet;

import tgx.td.ChatId;
import tgx.td.Td;

/**
 * XGram: voice messages turned into text in their bubble (Telegram Premium), like official Telegram's "→A": a button at
 * the end of the waveform opens the transcript above the caption, and closes it again. The first open asks Telegram
 * for it (RecognizeSpeech); the text then comes as the message's content update, partial while it's being made.
 */
public final class XgramSpeech {
  private XgramSpeech () { }

  /** Messages whose transcript is open, as "chatId_messageId", for as long as the app runs. */
  private static final java.util.Set<String> opened = java.util.Collections.synchronizedSet(new HashSet<>()); // read while TDLib's thread builds messages

  private static String key (long chatId, long messageId) {
    return chatId + "_" + messageId;
  }

  /** The button shows with Telegram Premium (owner's rule), on a sent voice message that isn't view-once. */
  public static boolean isAvailable (Tdlib tdlib, @Nullable TdApi.Message message) {
    return message != null && message.id > 0 && message.chatId != 0 && message.sendingState == null && message.schedulingState == null &&
      message.selfDestructType == null && !ChatId.isSecret(message.chatId) && tdlib.hasPremium();
  }

  public static boolean isOpen (long chatId, long messageId) {
    return opened.contains(key(chatId, messageId));
  }

  /** Telegram is still making the text (the button shows dots). */
  public static boolean isWorking (long chatId, long messageId, @Nullable TdApi.VoiceNote voice) {
    if (!isOpen(chatId, messageId) || voice == null) {
      return false;
    }
    final TdApi.SpeechRecognitionResult result = voice.speechRecognitionResult;
    return result == null || result instanceof TdApi.SpeechRecognitionResultPending;
  }

  /** The caption a voice message shows: its transcript while open, then its own caption. */
  public static @Nullable TdApi.FormattedText withTranscript (long chatId, long messageId, @Nullable TdApi.VoiceNote voice, @Nullable TdApi.FormattedText caption) {
    if (voice == null || !isOpen(chatId, messageId)) {
      return caption;
    }
    final TdApi.SpeechRecognitionResult result = voice.speechRecognitionResult;
    final String text;
    if (result instanceof TdApi.SpeechRecognitionResultText) {
      final String found = ((TdApi.SpeechRecognitionResultText) result).text;
      text = found != null && !found.trim().isEmpty() ? found : Lang.getString(R.string.XgramTranscriptEmpty);
    } else if (result instanceof TdApi.SpeechRecognitionResultPending) {
      final String partial = ((TdApi.SpeechRecognitionResultPending) result).partialText;
      text = (partial != null && !partial.isEmpty() ? partial + " " : "") + "…";
    } else if (result instanceof TdApi.SpeechRecognitionResultError) {
      text = Lang.getString(R.string.XgramTranscriptFailed);
    } else {
      text = "…"; // asked for, not started yet
    }
    if (Td.isEmpty(caption)) {
      return new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
    }
    final String separator = "\n\n";
    final int shift = text.length() + separator.length();
    final TdApi.TextEntity[] entities = new TdApi.TextEntity[caption.entities != null ? caption.entities.length : 0];
    for (int i = 0; i < entities.length; i++) {
      final TdApi.TextEntity entity = caption.entities[i];
      entities[i] = new TdApi.TextEntity(entity.offset + shift, entity.length, entity.type);
    }
    return new TdApi.FormattedText(text + separator + caption.text, entities);
  }

  /** The button: opens the transcript (asking Telegram for it the first time) or closes it. */
  public static void toggle (TGMessage owner, TdApi.Message message, @Nullable TdApi.VoiceNote voice) {
    final String key = key(message.chatId, message.id);
    if (opened.remove(key)) {
      refresh(owner, message.id);
      return;
    }
    opened.add(key);
    final TdApi.SpeechRecognitionResult result = voice != null ? voice.speechRecognitionResult : null;
    if (!(result instanceof TdApi.SpeechRecognitionResultText) && !(result instanceof TdApi.SpeechRecognitionResultPending)) {
      owner.tdlib().send(new TdApi.RecognizeSpeech(message.chatId, message.id), (ok, error) -> {
        if (error != null) {
          UI.post(() -> {
            opened.remove(key);
            UI.showError(error);
            refresh(owner, message.id);
          });
        }
      });
    }
    refresh(owner, message.id);
  }

  private static void refresh (TGMessage owner, long messageId) {
    if (owner instanceof TGMessageFile) {
      ((TGMessageFile) owner).xgramUpdateTranscript(messageId);
    }
  }
}
