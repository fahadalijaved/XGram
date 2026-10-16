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
import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramMessageDetailsController;
import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.collection.IntList;
import tgx.td.MessageId;

/**
 * Message time details (NagramX / exteraGram): tapping the time of a message, or the original date of a forward,
 * shows the exact sent, edited and original dates with seconds and the message ID, with copy actions.
 * Everything here runs on tap, never while drawing.
 */
public final class XgramMessageTime {
  private XgramMessageTime () { }

  /** Cheap: called on touch down to decide whether a tap on the time is ours. */
  public static boolean canShow (TGMessage msg) {
    return XgramSettings.instance().tapTimeForDetails() && msg.getChatId() != 0 && !msg.isSponsoredMessage() && !msg.isEventLog();
  }

  /** E.g. "12 October 2026 at 14:32:05", following the app's 12/24-hour setting. */
  public static String formatExact (int unixTime) {
    String time = new SimpleDateFormat(UI.needAmPm() ? "h:mm:ss a" : "H:mm:ss", Lang.locale()).format(new Date(TimeUnit.SECONDS.toMillis(unixTime)));
    return Lang.getString(R.string.format_dateTime, Lang.dateYearFull(unixTime, TimeUnit.SECONDS), time);
  }

  public static void show (TGMessage msg) {
    final ViewController<?> c = msg.controller();
    if (c == null || c.isDestroyed()) {
      return;
    }
    final TdApi.Message message = msg.getMessage();
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      showM3(c, msg, message);
      return;
    }
    StringBuilder b = new StringBuilder();

    if (message.schedulingState instanceof TdApi.MessageSchedulingStateSendAtDate) {
      row(b, R.string.XgramDetailsScheduledFor, formatExact(((TdApi.MessageSchedulingStateSendAtDate) message.schedulingState).sendDate));
    } else if (message.date != 0) {
      row(b, R.string.XgramDetailsSent, formatExact(message.date));
    }
    int editDate = msg.getEditDate();
    if (editDate != 0 && editDate != message.date) {
      row(b, R.string.XgramDetailsEdited, formatExact(editDate));
    }
    int originalDate = msg.getForwardOrImportDate();
    if (originalDate != 0) {
      row(b, R.string.XgramDetailsOriginallySent, formatExact(originalDate));
    }
    final String ids = serverIds(msg);
    if (ids != null) {
      row(b, R.string.XgramDetailsMessageId, ids);
    }
    if (b.length() == 0) {
      return;
    }
    final String text = b.toString();

    IntList optionIds = new IntList(3);
    StringList strings = new StringList(3);
    IntList icons = new IntList(3);

    optionIds.append(R.id.btn_copyText);
    strings.append(R.string.Copy);
    icons.append(R.drawable.baseline_content_copy_24);

    if (ids != null) {
      optionIds.append(R.id.btn_xgramCopyMessageId);
      strings.append(R.string.XgramCopyMessageId);
      icons.append(R.drawable.baseline_identifier_24);
    }

    if (message.id != 0) {
      optionIds.append(R.id.btn_xgramMessageDetails);
      strings.append(R.string.XgramMessageDetails);
      icons.append(R.drawable.baseline_info_24);
    }

    c.showOptions(text, optionIds.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyText) {
        UI.copyText(text, R.string.CopiedText);
      } else if (id == R.id.btn_xgramCopyMessageId) {
        if (ids != null) {
          UI.copyText(ids, R.string.CopiedText);
        }
      } else if (id == R.id.btn_xgramMessageDetails) {
        XgramMessageDetailsController details = new XgramMessageDetailsController(msg.context(), msg.tdlib());
        details.setArguments(new XgramMessageDetailsController.Args(message));
        msg.navigateTo(details);
      }
      return true;
    });
  }

  /**
   * XGram M3E r5: the exact times and the message ID as M3 list items (the value as the headline, its label as the
   * supporting line, a trailing copy icon button; tapping copies), a tonal "Copy" for all of it and "Details" at the
   * start. No Cancel row.
   */
  private static void showM3 (ViewController<?> c, TGMessage msg, TdApi.Message message) {
    java.util.List<com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row> rows = new java.util.ArrayList<>(4);
    StringBuilder b = new StringBuilder();
    if (message.schedulingState instanceof TdApi.MessageSchedulingStateSendAtDate) {
      m3Row(rows, b, R.string.XgramDetailsScheduledFor, formatExact(((TdApi.MessageSchedulingStateSendAtDate) message.schedulingState).sendDate));
    } else if (message.date != 0) {
      m3Row(rows, b, R.string.XgramDetailsSent, formatExact(message.date));
    }
    int editDate = msg.getEditDate();
    if (editDate != 0 && editDate != message.date) {
      m3Row(rows, b, R.string.XgramDetailsEdited, formatExact(editDate));
    }
    int originalDate = msg.getForwardOrImportDate();
    if (originalDate != 0) {
      m3Row(rows, b, R.string.XgramDetailsOriginallySent, formatExact(originalDate));
    }
    final String ids = serverIds(msg);
    if (ids != null) {
      m3Row(rows, b, R.string.XgramDetailsMessageId, ids);
    }
    if (rows.isEmpty()) {
      return;
    }
    final String text = b.toString();
    com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action details = message.id != 0 ?
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.XgramMessageDetails), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_TEXT, () -> {
        XgramMessageDetailsController controller = new XgramMessageDetailsController(msg.context(), msg.tdlib());
        controller.setArguments(new XgramMessageDetailsController.Args(message));
        msg.navigateTo(controller);
      }).atStart() : null;
    com.fahadali.xgram.appearance.XgramR5SheetsInfo.show(c, null, rows, details,
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.Copy), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_TONAL,
        () -> com.fahadali.xgram.appearance.XgramR5SheetsInfo.copy(text)));
  }

  private static void m3Row (java.util.List<com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row> rows, StringBuilder b, @StringRes int label, String value) {
    rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(value, Lang.getString(label)).tabular().copy(value));
    row(b, label, value);
  }

  private static void row (StringBuilder b, @StringRes int label, String value) {
    if (b.length() > 0) {
      b.append('\n');
    }
    b.append(Lang.getString(R.string.XgramTimeDetailsRow, Lang.getString(label), value));
  }

  /** Server message IDs: one ID, or the first and last of an album. Null when the message only exists locally. */
  @Nullable
  private static String serverIds (TGMessage msg) {
    long min = 0, max = 0;
    for (TdApi.Message m : msg.getAllMessages()) {
      long id = MessageId.toServerMessageId(m.id);
      if (id == 0) {
        continue;
      }
      if (min == 0 || id < min) {
        min = id;
      }
      if (id > max) {
        max = id;
      }
    }
    if (min == 0) {
      return null;
    }
    return min == max ? String.valueOf(min) : min + "–" + max;
  }
}
