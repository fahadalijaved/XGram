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

import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.AttachDelegate;

import me.vkryl.core.lambda.Destroyable;

/**
 * A full chat row (avatar, time, ticks, badges, typing) inside a settings-style list: the chat list's ChatView, reachable
 * by SettingHolder, which attaches, detaches and recycles custom views only through these two interfaces.
 */
public final class XgramCommunityChatView extends ChatView implements AttachDelegate, Destroyable {
  public XgramCommunityChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /** Recycled: let go of the row, its TGChat lives on in the controller. */
  @Override
  public void performDestroy () {
    setChat(null);
  }
}
