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

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.emoji.EmojiCodes;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.ui.EditEnabledReactionsController;

import me.vkryl.core.StringUtils;

/**
 * Double tap reaction (Messages > Gestures, under Double tap to react): the reaction a double tap on a message sends.
 * It is the account's default reaction (TdApi.SetDefaultReactionType), the one official apps send on a double tap, so
 * it is per account and the same on the account's other devices; TGMessage.xgramDoubleTapReaction sends it first when
 * the message takes it. The picker is Telegram X's reaction grid in one-choice mode
 * (EditEnabledReactionsController.TYPE_XGRAM_DOUBLE_TAP): the active emoji reactions, and Premium's custom emoji.
 */
public final class XgramDoubleTapReaction {
  private XgramDoubleTapReaction () { }

  /** The account's default reaction as the server set it (a custom emoji one too), 👍 before it came. */
  @NonNull
  public static TdApi.ReactionType current (@NonNull Tdlib tdlib) {
    final TdApi.ReactionType reactionType = tdlib.xgramDefaultReactionType();
    return reactionType != null ? reactionType : new TdApi.ReactionTypeEmoji(EmojiCodes.THUMBS_UP);
  }

  /** {@link #current}'s key (TD.makeReactionKey): the Compose row loads it again when it changes. */
  @NonNull
  public static String key (@NonNull Tdlib tdlib) {
    return TD.makeReactionKey(current(tdlib));
  }

  /**
   * The settings row's value: the emoji and its name ("👍 Thumbs Up"; the emoji alone until the reaction is loaded),
   * or a custom emoji's alt ("😎, custom emoji"). Requests nothing: see {@link #load}.
   */
  @UiThread
  @NonNull
  public static String rowValue (@NonNull Tdlib tdlib) {
    final TdApi.ReactionType reactionType = current(tdlib);
    switch (reactionType.getConstructor()) {
      case TdApi.ReactionTypeEmoji.CONSTRUCTOR: {
        final String emoji = ((TdApi.ReactionTypeEmoji) reactionType).emoji;
        final TGReaction reaction = tdlib.getReaction(reactionType, false);
        final String title = reaction != null ? reaction.getTitle() : null;
        return StringUtils.isEmpty(title) ? emoji : Lang.getString(R.string.XgramDoubleTapReactionEmoji, emoji, title);
      }
      case TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR: {
        final TdlibEmojiManager.Entry entry = tdlib.emoji().find(((TdApi.ReactionTypeCustomEmoji) reactionType).customEmojiId);
        final String alt = entry != null && entry.value != null ? entry.value.emoji : null;
        return StringUtils.isEmpty(alt) ? Lang.getString(R.string.XgramDoubleTapReactionCustomEmoji) : Lang.getString(R.string.XgramDoubleTapReactionCustom, alt);
      }
    }
    return "";
  }

  /**
   * Loads {@link #current}'s reaction (its name, or the custom emoji and its alt) for {@link #rowValue}. {@code onLoaded}
   * runs on the UI thread only when it had to be loaded, so a row that calls this while it binds does not loop.
   */
  public static void load (@NonNull Tdlib tdlib, @NonNull Runnable onLoaded) {
    tdlib.ensureReactionsAvailable(new TdApi.ChatAvailableReactionsSome(new TdApi.ReactionType[] {current(tdlib)}, 0), loaded -> {
      if (loaded) {
        tdlib.ui().post(onLoaded);
      }
    });
  }

  /** The picker: Telegram X's reaction grid, one choice, which sets the account's default reaction when tapped. */
  @NonNull
  public static EditEnabledReactionsController newPicker (@NonNull Context context, @NonNull Tdlib tdlib) {
    final EditEnabledReactionsController c = new EditEnabledReactionsController(context, tdlib);
    c.setArguments(new EditEnabledReactionsController.Args(null, EditEnabledReactionsController.TYPE_XGRAM_DOUBLE_TAP));
    return c;
  }
}
