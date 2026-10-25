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

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.ui.MessagesController;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * Long press on a sender's avatar in a chat (exteraGram): View Profile, Send message, Mention and Search messages.
 * Mention puts the user in the composer at the cursor: "@username", or, with no username, their first name as a name
 * mention (the same entity the @ list inserts), so anyone can be tagged. Users only: channels, anonymous admins and
 * forwards keep the message's own long press.
 */
public final class XgramAvatarMenu {
  private XgramAvatarMenu () { }

  /** Opens the menu when (x, y) is on a user's avatar beside {@code msg}; false leaves the long press to the message. */
  public static boolean show (@NonNull MessageView view, @Nullable TGMessage msg, float x, float y) {
    if (msg == null) {
      return false;
    }
    final MessagesController m = msg.messagesController();
    if (m == null || m.inSelectMode() || m.inPreviewMode() || m.isEditingMessage()) {
      return false;
    }
    final long userId = msg.xgramAvatarUserAt(view, x, y);
    if (userId == 0) {
      return false;
    }
    final Tdlib tdlib = msg.tdlib();
    final TdApi.User user = tdlib.cache().user(userId);
    if (user == null) {
      return false;
    }
    final boolean canMention = m.canWriteMessages() && m.xgramInputView() != null;
    final int count = canMention ? 4 : 3;
    final int[] ids = new int[count];
    final String[] titles = new String[count];
    final int[] icons = new int[count];
    int i = 0;
    ids[i] = R.id.btn_xgramAvatarProfile; titles[i] = Lang.getString(R.string.OpenProfile); icons[i++] = R.drawable.baseline_person_24;
    ids[i] = R.id.btn_xgramAvatarMessage; titles[i] = Lang.getString(R.string.XgramAvatarSendMessage); icons[i++] = R.drawable.baseline_chat_bubble_24;
    if (canMention) {
      ids[i] = R.id.btn_xgramAvatarMention; titles[i] = Lang.getString(R.string.Mention); icons[i++] = R.drawable.baseline_alternate_email_24;
    }
    ids[i] = R.id.btn_xgramAvatarSearch; titles[i] = Lang.getString(R.string.XgramAvatarSearchMessages); icons[i] = R.drawable.baseline_search_24;
    final TdApi.MessageSender sender = new TdApi.MessageSenderUser(userId);
    m.showOptions(TD.getUserName(user.firstName, user.lastName), ids, titles, null, icons, (itemView, id) -> {
      if (id == R.id.btn_xgramAvatarProfile) {
        tdlib.ui().openPrivateProfile(m, userId, msg.openParameters());
      } else if (id == R.id.btn_xgramAvatarMessage) {
        tdlib.ui().openPrivateChat(m, userId, new TdlibUi.ChatOpenParameters().keepStack());
      } else if (id == R.id.btn_xgramAvatarMention) {
        mention(m, user);
      } else if (id == R.id.btn_xgramAvatarSearch) {
        m.viewMessagesFromSender(sender, true);
      }
      return true;
    });
    return true;
  }

  /** Puts the mention at the cursor (in place of a selection), with a space before it when it follows a word. */
  private static void mention (@NonNull MessagesController m, @NonNull TdApi.User user) {
    final InputView input = m.xgramInputView();
    if (input == null || !m.canWriteMessages()) {
      return;
    }
    final String username = Td.primaryUsername(user);
    final SpannableStringBuilder insert;
    if (!StringUtils.isEmpty(username)) {
      insert = new SpannableStringBuilder("@").append(username).append(' ');
    } else {
      final String name = !StringUtils.isEmpty(user.firstName) ? user.firstName : TD.getUserName(user.firstName, user.lastName);
      insert = new SpannableStringBuilder(name).append(' ');
      insert.setSpan(TD.toSpan(new TdApi.TextEntityTypeMentionName(user.id)), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    final CharSequence current = input.getText() != null ? input.getText() : "";
    int start = Math.max(0, Math.min(input.getSelectionStart(), input.getSelectionEnd()));
    int end = Math.max(0, Math.max(input.getSelectionStart(), input.getSelectionEnd()));
    start = Math.min(start, current.length());
    end = Math.min(end, current.length());
    if (start > 0 && !Character.isWhitespace(current.charAt(start - 1))) {
      insert.insert(0, " ");
    }
    final SpannableStringBuilder text = new SpannableStringBuilder(current);
    text.replace(start, end, insert);
    input.setInput(text, false, true);
    input.setSelection(Math.min(text.length(), start + insert.length()));
    Keyboard.show(input);
  }
}
