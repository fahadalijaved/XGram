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

import android.graphics.drawable.Drawable;
import android.util.SparseArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramIconPack;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import tgx.td.Td;

/**
 * Swipe on a message (Settings, XGram, Chats): what a plain swipe left or right does. Telegram X swipes left to
 * reply, with Translate and the quick reactions a slide up or down away, and right to forward. The chosen action
 * takes the place a plain swipe lands on and Telegram X's own actions stay around it; a message that can't do the
 * chosen action keeps Telegram X's.
 */
public final class XgramSwipeActions {
  private XgramSwipeActions () { }

  public static final int REPLY = 0, QUOTE = 1, TRANSLATE = 2, COPY = 3, FORWARD = 4, SAVE = 5, BOOKMARK = 6, REACT = 7, NOTHING = 8;

  private static final int[] ACTIONS = {REPLY, QUOTE, TRANSLATE, COPY, FORWARD, SAVE, BOOKMARK, REACT, NOTHING};
  private static final int[] OPTION_IDS = {
    R.id.btn_xgramSwipeReply, R.id.btn_xgramSwipeQuote, R.id.btn_xgramSwipeTranslate, R.id.btn_xgramSwipeCopy, R.id.btn_xgramSwipeForward,
    R.id.btn_xgramSwipeSave, R.id.btn_xgramSwipeBookmark, R.id.btn_xgramSwipeReact, R.id.btn_xgramSwipeNothing
  };
  private static final int[] NAMES = {
    R.string.XgramSwipeReply, R.string.XgramQuote, R.string.Translate, R.string.XgramSwipeCopy, R.string.XgramSwipeForward,
    R.string.XgramSaveToSaved, R.string.XgramBookmark, R.string.XgramSwipeReact, R.string.XgramSwipeNothing
  };

  /** Telegram X's: a swipe left replies, a swipe right forwards. */
  public static int defaultAction (boolean toRight) {
    return toRight ? FORWARD : REPLY;
  }

  private static int indexOf (int action) {
    for (int i = 0; i < ACTIONS.length; i++) {
      if (ACTIONS[i] == action) {
        return i;
      }
    }
    return -1;
  }

  public static String name (int action) {
    int index = indexOf(action);
    return Lang.getString(NAMES[index >= 0 ? index : 0]);
  }

  // Settings

  public static void showPicker (ViewController<?> c, boolean toRight, @Nullable Runnable after) {
    final int groupId = toRight ? R.id.btn_xgramSwipeRight : R.id.btn_xgramSwipeLeft;
    int current = XgramSettings.instance().swipeAction(toRight);
    ListItem[] items = new ListItem[ACTIONS.length];
    for (int i = 0; i < ACTIONS.length; i++) {
      items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, OPTION_IDS[i], 0, NAMES[i], groupId, ACTIONS[i] == current);
    }
    c.showSettings(new SettingsWrapBuilder(groupId).setRawItems(items).setIntDelegate((id, result) -> {
      int picked = result.get(groupId);
      for (int i = 0; i < OPTION_IDS.length; i++) {
        if (OPTION_IDS[i] == picked) {
          XgramSettings.instance().setSwipeAction(toRight, ACTIONS[i]);
          break;
        }
      }
      if (after != null) {
        after.run();
      }
    }));
  }

  // The swipe

  /**
   * Puts the chosen action where a plain swipe lands. {@code actions} is Telegram X's list for one side and a plain
   * swipe picks {@code position} in it; returns the new position. A swipe right gets Telegram X's swipe left list in
   * {@code leftSwipe} (not yet changed) for React.
   */
  public static int arrange (TGMessage msg, ArrayList<TGMessage.SwipeQuickAction> actions, int position, boolean toRight, @Nullable List<TGMessage.SwipeQuickAction> leftSwipe, int leftSwipePosition) {
    int action = XgramSettings.instance().swipeAction(toRight);
    if (action == defaultAction(toRight) || indexOf(action) < 0) {
      return position;
    }
    if (action == NOTHING) {
      actions.clear();
      return 0;
    }
    if (action == REACT) {
      if (!toRight) {
        // The first quick reaction sits right after Telegram X's own actions, the second right before them.
        for (int i = position; i < actions.size(); i++) {
          if (actions.get(i).isQuickReaction) {
            return i;
          }
        }
        for (int i = Math.min(position, actions.size()) - 1; i >= 0; i--) {
          if (actions.get(i).isQuickReaction) {
            return i;
          }
        }
        return position;
      }
      // The quick reactions laid out as on a swipe left, then Telegram X's forward.
      List<TGMessage.SwipeQuickAction> before = new ArrayList<>(), after = new ArrayList<>();
      if (leftSwipe != null) {
        for (int i = 0; i < leftSwipe.size(); i++) {
          TGMessage.SwipeQuickAction reaction = leftSwipe.get(i);
          if (reaction.isQuickReaction) {
            (i < leftSwipePosition ? before : after).add(reaction);
          }
        }
      }
      if (before.isEmpty() && after.isEmpty()) {
        return position;
      }
      actions.addAll(0, after);
      actions.addAll(0, before);
      return after.isEmpty() ? before.size() - 1 : before.size();
    }
    TGMessage.SwipeQuickAction chosen = build(msg, action);
    if (chosen == null) {
      return position;
    }
    if (action == TRANSLATE) {
      // Telegram X's quick translation is already in the list: it moves rather than shows twice.
      String translate = Lang.getString(R.string.Translate), translateOff = Lang.getString(R.string.TranslateOff);
      for (int i = actions.size() - 1; i >= 0; i--) {
        TGMessage.SwipeQuickAction existing = actions.get(i);
        if (!existing.isQuickReaction && (translate.equals(existing.text) || translateOff.equals(existing.text))) {
          actions.remove(i);
          if (i < position) {
            position--;
          }
        }
      }
    }
    position = Math.max(0, Math.min(position, actions.size()));
    actions.add(position, chosen);
    return position;
  }

  private static boolean canReply (TGMessage msg) {
    MessagesController m = msg.messagesController();
    return m.canWriteMessagesOrWaitingForReply() && !m.needTabs() && msg.canReplyTo();
  }

  /** The action, or null when this message can't do it. */
  @Nullable
  private static TGMessage.SwipeQuickAction build (TGMessage msg, int action) {
    MessagesController m = msg.messagesController();
    Tdlib tdlib = msg.tdlib();
    TdApi.Message message = msg.getMessage();
    boolean isSent = !msg.isNotSent() && message.id != 0 && !msg.isSponsoredMessage();
    switch (action) {
      case REPLY:
        return canReply(msg) ? newAction(R.string.SwipeReply, R.drawable.baseline_reply_24, msg::xgramSwipeReply) : null;
      case QUOTE:
        return canReply(msg) && XgramQuote.canQuote(msg) ? newAction(R.string.XgramQuote, R.drawable.baseline_format_quote_close_24, () -> XgramQuote.open(m, msg)) : null;
      case TRANSLATE:
        if (msg.isTranslated()) {
          return newAction(R.string.TranslateOff, R.drawable.baseline_translate_off_24, msg::stopTranslated);
        }
        if (XgramTranslate.canOffer(msg, true)) {
          return newAction(R.string.Translate, R.drawable.baseline_translate_24, () -> XgramTranslate.start(m, msg));
        }
        return null;
      case COPY:
        return msg.canBeSaved() && XgramQuote.textMessage(msg) != null ? newAction(R.string.XgramSwipeCopy, R.drawable.baseline_content_copy_24, () -> copyText(msg)) : null;
      case FORWARD:
        return !msg.isSecretChat() && msg.canBeForwarded() ? newAction(R.string.SwipeShare, R.drawable.baseline_forward_24, () -> m.shareMessages(msg.getAllMessages(), false)) : null;
      case SAVE:
        return isSent && msg.canBeForwarded() && msg.getChatId() != tdlib.selfChatId() ? newAction(R.string.XgramSwipeSave, R.drawable.baseline_bookmark_24, () -> XgramMessageActions.handle(m, msg, R.id.btn_xgramSaveToSaved)) : null;
      case BOOKMARK:
        return isSent ? bookmark(msg) : null;
    }
    return null;
  }

  private static void copyText (TGMessage msg) {
    TdApi.Message message = XgramQuote.textMessage(msg);
    TdApi.FormattedText text = message != null ? Td.textOrCaption(message.content) : null;
    if (text != null && msg.canBeSaved()) {
      UI.copyText(TD.toCopyText(text), R.string.CopiedText);
    }
  }

  /** Bookmark, or Remove bookmark: the swipe shows the new state once it toggles. */
  private static TGMessage.SwipeQuickAction bookmark (TGMessage msg) {
    return new BookmarkAction(msg);
  }

  private static final class BookmarkAction extends TGMessage.SwipeQuickAction {
    private final Tdlib tdlib;
    private final long chatId, messageId;

    BookmarkAction (TGMessage msg) {
      super(Lang.getString(R.string.XgramBookmark), icon(R.drawable.baseline_star_border_24), null, true, false);
      this.tdlib = msg.tdlib();
      this.chatId = msg.getChatId();
      this.messageId = msg.getMessage().id;
      this.handler = () -> {
        XgramMessageActions.handle(msg.messagesController(), msg, R.id.btn_xgramBookmark);
        refresh();
      };
      refresh();
    }

    void refresh () {
      boolean bookmarked = XgramBookmarks.isBookmarked(tdlib, chatId, messageId);
      text = Lang.getString(bookmarked ? R.string.XgramRemoveBookmark : R.string.XgramBookmark);
      icon = icon(bookmarked ? R.drawable.baseline_star_24 : R.drawable.baseline_star_border_24);
    }
  }

  /** Both swipe settings in one number: a message whose actions were built with other ones builds them again. */
  public static int settingsKey () {
    XgramSettings settings = XgramSettings.instance();
    return settings.swipeAction(false) * 16 + settings.swipeAction(true);
  }

  /** As a swipe starts: Bookmark shows the message's state now, it may have been bookmarked another way since. */
  public static void refresh (List<TGMessage.SwipeQuickAction> actions) {
    for (TGMessage.SwipeQuickAction action : actions) {
      if (action instanceof BookmarkAction) {
        ((BookmarkAction) action).refresh();
      }
    }
  }

  private static TGMessage.SwipeQuickAction newAction (@StringRes int text, @DrawableRes int icon, Runnable handler) {
    return new TGMessage.SwipeQuickAction(Lang.getString(text), icon(icon), handler, true, false);
  }

  // Icons, through the icon pack. Messages ask for them on the UI thread, every time they're built.

  private static final SparseArray<Drawable> icons = new SparseArray<>();
  private static int iconsPack = Integer.MIN_VALUE;

  private static Drawable icon (@DrawableRes int res) {
    int pack = XgramIconPack.current();
    if (pack != iconsPack) {
      icons.clear();
      iconsPack = pack;
    }
    Drawable icon = icons.get(res);
    if (icon == null) {
      icon = Drawables.get(res);
      icons.put(res, icon);
    }
    return icon;
  }
}
