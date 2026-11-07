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
package org.thunderdog.challegram.data;

import android.view.MotionEvent;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.tool.UI;

/**
 * XGram: a group call in a private chat (TdApi.MessageGroupCall), as the same call card a 1:1 call gets, like official
 * Telegram (voice or video, missed, how long it lasted). Telegram X can't join group calls, so a tap says so instead of
 * starting a 1:1 call.
 */
public class TGMessageGroupCall extends TGMessageCall {
  private static TdApi.MessageCall asCall (TdApi.MessageGroupCall call) {
    final TdApi.CallDiscardReason reason = call.wasMissed ? new TdApi.CallDiscardReasonMissed() :
      call.isActive ? new TdApi.CallDiscardReasonEmpty() : new TdApi.CallDiscardReasonHungUp();
    return new TdApi.MessageCall(call.uniqueId, call.isVideo, reason, call.duration);
  }

  public TGMessageGroupCall (MessagesManager context, TdApi.Message msg, TdApi.MessageGroupCall call) {
    super(context, msg, asCall(call));
  }

  private boolean caughtTap;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    final float x = e.getX(), y = e.getY();
    final boolean inside = x >= getContentX() && x <= getContentX() + getContentWidth() && y >= getContentY() && y <= getContentY() + getContentHeight();
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        caughtTap = inside;
        break;
      case MotionEvent.ACTION_MOVE:
        if (!inside) {
          caughtTap = false;
        }
        break;
      case MotionEvent.ACTION_UP:
        if (caughtTap && inside) {
          caughtTap = false;
          UI.showToast(R.string.XgramGroupCallOfficialApp, android.widget.Toast.LENGTH_SHORT);
          return true;
        }
        caughtTap = false;
        break;
      case MotionEvent.ACTION_CANCEL:
        caughtTap = false;
        break;
    }
    return caughtTap || super.onTouchEvent(view, e);
  }
}
