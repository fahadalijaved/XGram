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

import android.graphics.Point;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ReactionsOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * XGram: message effects (TdApi.Message.effectId), like official Telegram: the effect's animation plays over the chat
 * next to the bubble the first time a message carrying it shows up fresh (unread, or sent in the last two minutes), and
 * again from "Play effect" in the message menu. Effects are fetched once (getMessageEffect) and kept for the session.
 */
public final class XgramMessageEffects {
  private XgramMessageEffects () { }

  private static final LongSparseArray<TdApi.MessageEffect> effects = new LongSparseArray<>();
  private static final HashSet<String> played = new HashSet<>();

  private static String key (TdApi.Message message) {
    return message.chatId + "_" + message.id;
  }

  /** A message just showed: its effect plays once when the message is fresh. */
  public static void onMessageShown (TGMessage msg, MessageView view) {
    final TdApi.Message message = msg.getMessage();
    if (message == null || message.effectId == 0 || message.id == 0 || played.contains(key(message))) {
      return;
    }
    final long ageSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - message.date;
    final TdApi.Chat chat = msg.tdlib().chat(message.chatId);
    final boolean unread = !message.isOutgoing && chat != null && message.id > chat.lastReadInboxMessageId;
    if (ageSeconds > 120 && !unread) {
      played.add(key(message)); // an old message: no surprise animation, the menu can still play it
      return;
    }
    play(msg, view);
  }

  /** Plays the message's effect over the chat, next to its bubble. */
  public static void play (TGMessage msg, @Nullable View view) {
    final TdApi.Message message = msg.getMessage();
    if (message == null || message.effectId == 0 || view == null) {
      return;
    }
    played.add(key(message));
    final Tdlib tdlib = msg.tdlib();
    withEffect(tdlib, message.effectId, effect -> {
      final TdApi.Sticker sticker = animationOf(effect);
      if (sticker == null || !view.isAttachedToWindow()) {
        return;
      }
      final int[] location = new int[2];
      view.getLocationOnScreen(location);
      final int size = Math.min(Screen.currentWidth(), Screen.dp(280f));
      final int bubbleRight = location[0] + msg.getActualRightContentEdge();
      final int cx = message.isOutgoing ? bubbleRight - size / 4 : location[0] + msg.getActualLeftContentEdge() + size / 4;
      final int cy = location[1] + view.getMeasuredHeight() / 2;
      TGStickerObj animation = new TGStickerObj(tdlib, sticker, null, sticker.fullType);
      if (animation.getFullAnimation() != null) {
        animation.getFullAnimation().setPlayOnce(true);
        animation.getFullAnimation().setLooped(false);
      }
      msg.context().reactionsOverlayManager().addOverlay(
        new ReactionsOverlayView.ReactionInfo(msg.context().reactionsOverlayManager())
          .setSticker(animation, true)
          .setPosition(new Point(cx, cy), size)
      );
    });
  }

  private static @Nullable TdApi.Sticker animationOf (TdApi.MessageEffect effect) {
    if (effect.type == null) {
      return null;
    }
    switch (effect.type.getConstructor()) {
      case TdApi.MessageEffectTypeEmojiReaction.CONSTRUCTOR:
        return ((TdApi.MessageEffectTypeEmojiReaction) effect.type).effectAnimation;
      case TdApi.MessageEffectTypePremiumSticker.CONSTRUCTOR:
        return ((TdApi.MessageEffectTypePremiumSticker) effect.type).sticker;
      default:
        return null;
    }
  }

  private interface EffectCallback {
    void onEffect (TdApi.MessageEffect effect);
  }

  private static void withEffect (Tdlib tdlib, long effectId, EffectCallback callback) {
    final TdApi.MessageEffect known = effects.get(effectId);
    if (known != null) {
      callback.onEffect(known);
      return;
    }
    tdlib.send(new TdApi.GetMessageEffect(effectId), (effect, error) -> {
      if (effect != null) {
        UI.post(() -> {
          effects.put(effectId, effect);
          callback.onEffect(effect);
        });
      }
    });
  }
}
