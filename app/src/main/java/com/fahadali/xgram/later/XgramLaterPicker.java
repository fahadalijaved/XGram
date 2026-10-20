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
package com.fahadali.xgram.later;

import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5Chat;
import com.fahadali.xgram.appearance.XgramSnackbar;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import tgx.td.ChatId;

/**
 * The presets sheet of Later (one builder for a message, a chat or several chats), its feedback, the Snoozed row's
 * menu, the in-chat notices and the notification permission. Expressive design: the M3E grouped sheet, snackbars with
 * Undo; classic: Telegram X's options sheet and toasts.
 */
public final class XgramLaterPicker {
  private XgramLaterPicker () { }

  private static final long MIN_AHEAD_MS = 30_000L;

  /** What the sheet is for: a message, or chats to snooze. */
  private static final class Target {
    final Tdlib tdlib;
    @Nullable final TdApi.Message message;
    final long[] chatIds;
    @Nullable final TdApi.ChatList chatList;
    @Nullable final Runnable after;
    @Nullable String note;

    Target (Tdlib tdlib, @Nullable TdApi.Message message, long[] chatIds, @Nullable TdApi.ChatList chatList, @Nullable Runnable after) {
      this.tdlib = tdlib;
      this.message = message;
      this.chatIds = chatIds;
      this.chatList = chatList;
      this.after = after;
    }

    boolean isMessage () {
      return message != null;
    }

    /** The record of the message, or of the one chat, if any (pending or due). */
    @Nullable
    XgramLaterRecord existing () {
      XgramLaterRecord r;
      if (message != null) {
        r = XgramLater.record(tdlib.id(), message.chatId, message.id);
      } else if (chatIds.length == 1) {
        r = XgramLater.record(tdlib.id(), chatIds[0], 0);
      } else {
        return null;
      }
      return r != null && (r.isPending() || r.isDue()) && r.isVisibleItem() ? r : null;
    }
  }

  // Entry points

  /** The message menu's "Remind me…". */
  public static void showForMessage (MessagesController m, TGMessage msg) {
    TdApi.Message message = msg.getMessage();
    if (message == null || message.id == 0) {
      return;
    }
    showSheet(m, new Target(m.tdlib(), message, new long[] {message.chatId}, null, null));
  }

  /** Snooze for one chat or several (selection mode). {@code after} runs once something was snoozed. */
  public static void showForChats (ViewController<?> c, Tdlib tdlib, long[] chatIds, @Nullable TdApi.ChatList chatList, @Nullable Runnable after) {
    if (chatIds.length == 0) {
      return;
    }
    showSheet(c, new Target(tdlib, null, chatIds, chatList, after));
  }

  /** "Unsnooze" from a chat's menu or the chat screen: back in the lists at once. */
  public static void unsnooze (ViewController<?> c, Tdlib tdlib, long chatId) {
    final String key = XgramLaterRecord.key(tdlib.id(), chatId, 0);
    final XgramLaterRecord previous = XgramLaterEngine.markDone(tdlib.id(), key);
    if (previous != null && !previous.isDone()) {
      feedbackUndo(c, Lang.getString(R.string.XgramLaterUnsnoozed), single(key), single(previous), tdlib.id(), false);
    }
  }

  // The sheet

  private static void showSheet (ViewController<?> c, Target t) {
    if (c == null || c.isDestroyed()) {
      return;
    }
    final Tdlib tdlib = t.tdlib;
    final long now = System.currentTimeMillis();
    final TimeZone tz = TimeZone.getDefault();
    final Locale locale = XgramLaterTimes.locale();
    final XgramLaterRecord existing = t.existing();
    final boolean oneChat = !t.isMessage() && t.chatIds.length == 1;

    // Title and the hint line
    CharSequence title;
    if (existing != null && existing.isPending()) {
      title = recordTitle(existing);
    } else if (t.isMessage()) {
      title = Lang.getString(R.string.XgramLaterSheetMessage);
    } else if (oneChat) {
      title = Lang.getString(R.string.XgramLaterSheetChat, tdlib.chatTitle(t.chatIds[0]));
    } else {
      title = Lang.getString(R.string.XgramLaterSheetChat, Lang.plural(R.string.xChats, t.chatIds.length));
    }
    StringBuilder info = new StringBuilder();
    if (t.isMessage()) {
      String preview = previewOf(tdlib, t.message);
      if (!preview.isEmpty()) {
        info.append(preview).append('\n');
      }
      String note = t.note != null ? t.note : existing != null ? existing.note : "";
      info.append(note.isEmpty() ? Lang.getString(R.string.XgramLaterHintLocal) : Lang.getString(R.string.XgramLaterNoteShown, note));
    } else {
      info.append(Lang.getString(R.string.XgramLaterHintHidden));
      int mode = XgramLater.wakeEarlyMode();
      if (mode == XgramLaterRecord.WAKE_TO_ME) {
        info.append(' ').append(Lang.getString(R.string.XgramLaterHintWakeToMe));
      } else if (mode == XgramLaterRecord.WAKE_ANY) {
        info.append(' ').append(Lang.getString(R.string.XgramLaterHintWakeAny));
      }
    }

    ViewController.Options.Builder b = new ViewController.Options.Builder()
      .title(title)
      .info(info.toString());

    // A: times
    if (t.isMessage()) {
      b.item(item(R.id.btn_xgramLaterP20m, Lang.getString(R.string.XgramLaterIn20Min), R.drawable.baseline_watch_later_24, false));
    }
    b.item(item(R.id.btn_xgramLaterP1h, Lang.getString(R.string.XgramLaterIn1Hour), R.drawable.baseline_watch_later_24, false));
    final long tonight = XgramLaterTimes.tonight(now, tz, locale);
    if (tonight != 0) {
      b.item(item(R.id.btn_xgramLaterPTonight, Lang.getString(R.string.XgramLaterTonight, XgramLaterTimes.time(tonight)), R.drawable.baseline_brightness_2_24, false));
    }
    final long morning = XgramLaterTimes.tomorrowMorning(now, tz, locale);
    b.item(item(R.id.btn_xgramLaterPTomorrow, Lang.getString(XgramLaterTimes.isEarlyMorning(now, tz, locale) ? R.string.XgramLaterThisMorning : R.string.XgramLaterTomorrow, XgramLaterTimes.time(morning)), R.drawable.baseline_access_time_24, false));
    final long nextWeek = XgramLaterTimes.nextWeek(now, tz, locale);
    b.item(item(R.id.btn_xgramLaterPNextWeek, Lang.getString(R.string.XgramLaterNextWeek, XgramLaterTimes.weekday(nextWeek) + " " + XgramLaterTimes.time(nextWeek)), R.drawable.baseline_schedule_24, false));

    // B: conditions
    List<ViewController.OptionItem> conditions = new ArrayList<>(3);
    if (!t.isMessage()) {
      int untilRes = untilLabel(tdlib, t.chatIds);
      if (untilRes != 0) {
        conditions.add(item(R.id.btn_xgramLaterPUntil, Lang.getString(untilRes), R.drawable.baseline_reply_24, false));
      }
    } else if (canFollowUp(tdlib, t.message)) {
      conditions.add(item(R.id.btn_xgramLaterPNoReply1d, Lang.getString(R.string.XgramLaterNoReply1d), R.drawable.baseline_reply_24, false));
      conditions.add(item(R.id.btn_xgramLaterPNoReply3d, Lang.getString(R.string.XgramLaterNoReply3d), R.drawable.baseline_reply_24, false));
      conditions.add(item(R.id.btn_xgramLaterPNoReply1w, Lang.getString(R.string.XgramLaterNoReply1w), R.drawable.baseline_reply_24, false));
    }
    if (!conditions.isEmpty()) {
      b.item(ViewController.OptionItem.SEPARATOR);
      for (ViewController.OptionItem item : conditions) {
        b.item(item);
      }
    }

    // C: any time, a note
    b.item(ViewController.OptionItem.SEPARATOR);
    b.item(item(R.id.btn_xgramLaterPCustom, Lang.getString(R.string.XgramLaterCustom), R.drawable.baseline_date_range_24, false));
    if (t.isMessage() && existing == null) {
      b.item(item(R.id.btn_xgramLaterPNote, Lang.getString(R.string.XgramLaterAddNote), R.drawable.baseline_create_24, false));
    }

    // D: the record that exists
    if (existing != null) {
      b.item(ViewController.OptionItem.SEPARATOR);
      b.item(item(R.id.btn_xgramLaterPEditNote, Lang.getString(R.string.XgramLaterEditNote), R.drawable.baseline_create_24, false));
      if (existing.isDue()) {
        b.item(item(R.id.btn_xgramLaterPMarkDone, Lang.getString(R.string.XgramLaterMarkDone), R.drawable.baseline_check_circle_24, false));
      }
      if (!existing.isSnooze() || existing.isPending()) {
        // A woken chat is back already: Mark as done covers it
        b.item(item(R.id.btn_xgramLaterPCancel, Lang.getString(existing.isSnooze() ? R.string.XgramLaterUnsnooze : R.string.XgramLaterCancelReminder), existing.isSnooze() ? R.drawable.baseline_notifications_24 : R.drawable.baseline_delete_24, true));
      }
    }
    b.cancelItem();

    c.showOptions(b.build(), (view, id) -> {
      onSheetItem(c, t, existing, id, tonight, morning, nextWeek);
      return true;
    });
  }

  private static ViewController.OptionItem item (int id, String name, int icon, boolean red) {
    return new ViewController.OptionItem(id, name, red ? ViewController.OptionColor.RED : ViewController.OptionColor.NORMAL, icon);
  }

  private static void onSheetItem (ViewController<?> c, Target t, @Nullable XgramLaterRecord existing, int id, long tonight, long morning, long nextWeek) {
    if (c.isDestroyed()) {
      return;
    }
    final long now = System.currentTimeMillis();
    if (id == R.id.btn_xgramLaterP20m) {
      apply(c, t, XgramLaterTimes.in20Minutes(now), XgramLaterRecord.KIND_REMIND);
    } else if (id == R.id.btn_xgramLaterP1h) {
      apply(c, t, XgramLaterTimes.in1Hour(now), XgramLaterRecord.KIND_REMIND);
    } else if (id == R.id.btn_xgramLaterPTonight) {
      apply(c, t, tonight, XgramLaterRecord.KIND_REMIND);
    } else if (id == R.id.btn_xgramLaterPTomorrow) {
      apply(c, t, morning, XgramLaterRecord.KIND_REMIND);
    } else if (id == R.id.btn_xgramLaterPNextWeek) {
      apply(c, t, nextWeek, XgramLaterRecord.KIND_REMIND);
    } else if (id == R.id.btn_xgramLaterPUntil) {
      apply(c, t, 0, XgramLaterRecord.KIND_SNOOZE_UNTIL);
    } else if (id == R.id.btn_xgramLaterPNoReply1d) {
      apply(c, t, XgramLaterTimes.ceilToMinute(now + XgramLaterTimes.DAY), XgramLaterRecord.KIND_FOLLOWUP);
    } else if (id == R.id.btn_xgramLaterPNoReply3d) {
      apply(c, t, XgramLaterTimes.ceilToMinute(now + 3 * XgramLaterTimes.DAY), XgramLaterRecord.KIND_FOLLOWUP);
    } else if (id == R.id.btn_xgramLaterPNoReply1w) {
      apply(c, t, XgramLaterTimes.ceilToMinute(now + XgramLaterTimes.WEEK), XgramLaterRecord.KIND_FOLLOWUP);
    } else if (id == R.id.btn_xgramLaterPCustom) {
      pickTime(c, wakeAt -> apply(c, t, wakeAt, XgramLaterRecord.KIND_REMIND));
    } else if (id == R.id.btn_xgramLaterPNote) {
      askNote(c, t.note, note -> {
        t.note = note;
        showSheet(c, t); // the same sheet again, the note in its hint line
      });
    } else if (id == R.id.btn_xgramLaterPEditNote && existing != null) {
      editNote(c, t.tdlib.id(), existing.key);
    } else if (id == R.id.btn_xgramLaterPMarkDone && existing != null) {
      markDone(c, t.tdlib.id(), existing.key);
    } else if (id == R.id.btn_xgramLaterPCancel && existing != null) {
      if (existing.isSnooze()) {
        unsnooze(c, t.tdlib, existing.chatId);
      } else {
        delete(c, t.tdlib.id(), existing.key);
      }
    }
  }

  /** Saves the choice: a reminder or follow-up of the message, or the chats' snooze (wakeAt 0: until). */
  private static void apply (ViewController<?> c, Target t, long wakeAt, int kind) {
    final Tdlib tdlib = t.tdlib;
    final int accountId = tdlib.id();
    if (t.isMessage()) {
      final TdApi.Message message = t.message;
      final String key = XgramLaterRecord.key(accountId, message.chatId, message.id);
      final XgramLaterRecord previous = XgramLaterEngine.remind(tdlib, message, kind, wakeAt, t.note);
      String when = XgramLaterTimes.formatWake(wakeAt);
      String text = kind == XgramLaterRecord.KIND_FOLLOWUP ? Lang.getString(R.string.XgramLaterFollowUpSet, when) : Lang.getString(R.string.XgramLaterSetFor, when);
      feedbackUndo(c, text, single(key), single(previous), accountId, true);
    } else {
      List<Long> allowed = new ArrayList<>(t.chatIds.length);
      int skipped = 0;
      for (long chatId : t.chatIds) {
        if (XgramLater.canSnooze(tdlib, tdlib.chat(chatId), t.chatList)) {
          allowed.add(chatId);
        } else {
          skipped++;
        }
      }
      if (allowed.isEmpty()) {
        UI.showToast(R.string.XgramLaterCommunityNo, Toast.LENGTH_SHORT);
        return;
      }
      long[] chatIds = new long[allowed.size()];
      List<String> keys = new ArrayList<>(chatIds.length);
      for (int i = 0; i < chatIds.length; i++) {
        chatIds[i] = allowed.get(i);
        keys.add(XgramLaterRecord.key(accountId, chatIds[i], 0));
      }
      final List<XgramLaterRecord> previous = XgramLaterEngine.snoozeChats(tdlib, chatIds, kind == XgramLaterRecord.KIND_SNOOZE_UNTIL ? 0 : wakeAt, null);
      String text;
      if (chatIds.length == 1) {
        text = snoozedText(tdlib, chatIds[0], kind == XgramLaterRecord.KIND_SNOOZE_UNTIL ? 0 : wakeAt);
      } else if (kind == XgramLaterRecord.KIND_SNOOZE_UNTIL) {
        text = Lang.getString(R.string.XgramLaterXChatsSnoozedUntil, Lang.plural(R.string.xChats, chatIds.length));
      } else {
        text = Lang.getString(R.string.XgramLaterXChatsSnoozed, Lang.plural(R.string.xChats, chatIds.length), XgramLaterTimes.formatWake(wakeAt));
      }
      if (skipped > 0) {
        UI.showToast(R.string.XgramLaterCommunityNo, Toast.LENGTH_SHORT);
      }
      feedbackUndo(c, text, keys, previous, accountId, true);
      if (t.after != null) {
        t.after.run();
      }
    }
    haptic(c);
    ensureNotificationPermission(c);
  }

  /** "Snoozed until Tue, 9:00", or the until wording of the chat's kind. */
  static String snoozedText (Tdlib tdlib, long chatId, long wakeAt) {
    if (wakeAt != 0) {
      return Lang.getString(R.string.XgramLaterSnoozedUntil, XgramLaterTimes.formatWake(wakeAt));
    }
    if (ChatId.isUserChat(chatId)) {
      return Lang.getString(R.string.XgramLaterSnoozedUntilReply);
    }
    return Lang.getString(XgramLater.isChannel(tdlib, chatId) ? R.string.XgramLaterSnoozedUntilPost : R.string.XgramLaterSnoozedUntilMention);
  }

  /** The "until" preset of the chats, or 0 (Saved Messages only). */
  private static int untilLabel (Tdlib tdlib, long[] chatIds) {
    if (chatIds.length > 1) {
      for (long chatId : chatIds) {
        if (!tdlib.isSelfChat(chatId)) {
          return R.string.XgramLaterUntilWrite;
        }
      }
      return 0;
    }
    long chatId = chatIds[0];
    if (tdlib.isSelfChat(chatId)) {
      return 0;
    }
    if (ChatId.isUserChat(chatId)) {
      return R.string.XgramLaterUntilReply;
    }
    return XgramLater.isChannel(tdlib, chatId) ? R.string.XgramLaterUntilPost : R.string.XgramLaterUntilMention;
  }

  /** Your own sent message in a private chat or a group: not channels, Saved Messages or secret chats. */
  private static boolean canFollowUp (Tdlib tdlib, @Nullable TdApi.Message message) {
    if (message == null || !message.isOutgoing || message.sendingState != null || message.schedulingState != null) {
      return false;
    }
    long chatId = message.chatId;
    return !ChatId.isSecret(chatId) && !tdlib.isSelfChat(chatId) && !XgramLater.isChannel(tdlib, chatId);
  }

  private static String previewOf (Tdlib tdlib, TdApi.Message message) {
    if (ChatId.isSecret(message.chatId)) {
      return "";
    }
    try {
      String text = org.thunderdog.challegram.data.ContentPreview.getChatListPreview(tdlib, message.chatId, message, true).buildText(false);
      return text != null ? text.replace('\n', ' ') : "";
    } catch (Throwable t) {
      return "";
    }
  }

  /** "Reminder · Tue, 9:00", "Follow-up · Fri, 10:00" or "Snoozed until Tue, 9:00". */
  static String recordTitle (XgramLaterRecord r) {
    if (r.isSnooze()) {
      return r.isTimed() ? Lang.getString(R.string.XgramLaterSnoozedUntil, XgramLaterTimes.formatWake(r.wakeAt)) : untilText(r);
    }
    String when = XgramLaterTimes.formatWake(r.wakeAt);
    return Lang.getString(r.kind == XgramLaterRecord.KIND_FOLLOWUP ? R.string.XgramLaterFollowUpAt : R.string.XgramLaterReminderAt, when);
  }

  /** "Until they reply", "Until I'm mentioned or replied to" or "Until a new post". */
  static String untilText (XgramLaterRecord r) {
    if (ChatId.isUserChat(r.chatId)) {
      return Lang.getString(R.string.XgramLaterUntilReply);
    }
    return Lang.getString(r.wakeMode == XgramLaterRecord.WAKE_ANY ? R.string.XgramLaterUntilPost : R.string.XgramLaterUntilMention);
  }

  // Steps

  public interface TimeCallback {
    void onTime (long wakeAt);
  }

  /** Telegram X's date and time picker (the M3 dialogs with Expressive design), then a check for a time ahead. */
  static void pickTime (ViewController<?> c, TimeCallback callback) {
    c.showDateTimePicker(Lang.getString(R.string.SendReminder), R.string.RemindTodayAt, R.string.RemindTomorrowAt, R.string.RemindDateAt, millis -> {
      // The M3 picker checks against the server's clock: the device's decides here
      if (millis < System.currentTimeMillis() + MIN_AHEAD_MS) {
        UI.showToast(R.string.XgramLaterPastTime, Toast.LENGTH_SHORT);
        return;
      }
      callback.onTime(millis);
    }, null);
  }

  public interface NoteCallback {
    void onNote (String note);
  }

  /** "Add note": sentence capitals, at most 200 characters. */
  static void askNote (ViewController<?> c, @Nullable String current, NoteCallback callback) {
    org.thunderdog.challegram.widget.MaterialEditTextGroup input = c.openInputAlert(Lang.getString(R.string.XgramLaterNoteTitle), Lang.getString(R.string.XgramLaterNoteHint), R.string.Save, R.string.Cancel,
      current, (inputView, result) -> {
        callback.onNote(result.trim());
        return true;
      }, true);
    if (input != null) {
      input.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
      input.getEditText().setFilters(new InputFilter[] {new InputFilter.LengthFilter(XgramLaterRecord.NOTE_MAX_LENGTH)});
    }
  }

  static void editNote (ViewController<?> c, int accountId, String key) {
    XgramLaterRecord r = XgramLaterEngine.current(accountId, key);
    if (r == null) {
      return;
    }
    askNote(c, r.note, note -> XgramLaterEngine.setNote(accountId, key, note));
  }

  static void markDone (ViewController<?> c, int accountId, String key) {
    XgramLaterRecord previous = XgramLaterEngine.markDone(accountId, key);
    if (previous != null && !previous.isDone()) {
      feedbackUndo(c, Lang.getString(R.string.XgramLaterMarkedDone), single(key), single(previous), accountId, false);
    }
  }

  static void delete (ViewController<?> c, int accountId, String key) {
    XgramLaterRecord previous = XgramLaterEngine.delete(accountId, key);
    if (previous != null) {
      feedbackUndo(c, Lang.getString(R.string.XgramLaterDeleted), single(key), single(previous), accountId, false);
    }
  }

  /** A new time for an existing record (Change time, Snooze again, Remind again): back to pending at it. */
  static void showReschedule (ViewController<?> c, Tdlib tdlib, XgramLaterRecord r) {
    final long now = System.currentTimeMillis();
    final TimeZone tz = TimeZone.getDefault();
    final Locale locale = XgramLaterTimes.locale();
    final long tonight = XgramLaterTimes.tonight(now, tz, locale);
    final long morning = XgramLaterTimes.tomorrowMorning(now, tz, locale);
    final long nextWeek = XgramLaterTimes.nextWeek(now, tz, locale);
    ViewController.Options.Builder b = new ViewController.Options.Builder()
      .title(r.title.isEmpty() ? tdlib.chatTitle(r.chatId) : r.title)
      .info(r.isTimed() ? recordTitle(r) : null);
    if (!r.isSnooze()) {
      b.item(item(R.id.btn_xgramLaterP20m, Lang.getString(R.string.XgramLaterIn20Min), R.drawable.baseline_watch_later_24, false));
    }
    b.item(item(R.id.btn_xgramLaterP1h, Lang.getString(R.string.XgramLaterIn1Hour), R.drawable.baseline_watch_later_24, false));
    if (tonight != 0) {
      b.item(item(R.id.btn_xgramLaterPTonight, Lang.getString(R.string.XgramLaterTonight, XgramLaterTimes.time(tonight)), R.drawable.baseline_brightness_2_24, false));
    }
    b.item(item(R.id.btn_xgramLaterPTomorrow, Lang.getString(XgramLaterTimes.isEarlyMorning(now, tz, locale) ? R.string.XgramLaterThisMorning : R.string.XgramLaterTomorrow, XgramLaterTimes.time(morning)), R.drawable.baseline_access_time_24, false));
    b.item(item(R.id.btn_xgramLaterPNextWeek, Lang.getString(R.string.XgramLaterNextWeek, XgramLaterTimes.weekday(nextWeek) + " " + XgramLaterTimes.time(nextWeek)), R.drawable.baseline_schedule_24, false));
    if (r.isSnooze() && !tdlib.isSelfChat(r.chatId)) {
      b.item(ViewController.OptionItem.SEPARATOR);
      b.item(item(R.id.btn_xgramLaterPUntil, untilText(r), R.drawable.baseline_reply_24, false));
    }
    b.item(ViewController.OptionItem.SEPARATOR);
    b.item(item(R.id.btn_xgramLaterPCustom, Lang.getString(R.string.XgramLaterCustom), R.drawable.baseline_date_range_24, false));
    b.cancelItem();
    final int accountId = tdlib.id();
    final String key = r.key;
    c.showOptions(b.build(), (view, id) -> {
      long at = 0;
      boolean until = false;
      long nowTap = System.currentTimeMillis();
      if (id == R.id.btn_xgramLaterP20m) {
        at = XgramLaterTimes.in20Minutes(nowTap);
      } else if (id == R.id.btn_xgramLaterP1h) {
        at = XgramLaterTimes.in1Hour(nowTap);
      } else if (id == R.id.btn_xgramLaterPTonight) {
        at = tonight;
      } else if (id == R.id.btn_xgramLaterPTomorrow) {
        at = morning;
      } else if (id == R.id.btn_xgramLaterPNextWeek) {
        at = nextWeek;
      } else if (id == R.id.btn_xgramLaterPUntil) {
        until = true;
      } else if (id == R.id.btn_xgramLaterPCustom) {
        pickTime(c, wakeAt -> reschedule(c, accountId, key, wakeAt));
        return true;
      }
      if (at != 0 || until) {
        reschedule(c, accountId, key, at);
      }
      return true;
    });
  }

  private static void reschedule (ViewController<?> c, int accountId, String key, long wakeAt) {
    XgramLaterRecord previous = XgramLaterEngine.reschedule(accountId, key, wakeAt);
    if (previous == null) {
      return;
    }
    XgramLaterRecord now = XgramLaterEngine.current(accountId, key);
    feedbackUndo(c, now != null ? recordTitle(now) : Lang.getString(R.string.XgramLater), single(key), single(previous), accountId, true);
    haptic(c);
  }

  // Feedback

  private static <T> List<T> single (T value) {
    List<T> list = new ArrayList<>(1);
    list.add(value);
    return list;
  }

  /**
   * Expressive design: a snackbar whose Undo puts back exactly what was there (or nothing). Classic: a toast with
   * " · Find it in Later" when it was a new item, and no Undo.
   */
  private static void feedbackUndo (ViewController<?> c, String text, List<String> keys, List<XgramLaterRecord> previous, int accountId, boolean pointToLater) {
    Runnable undo = () -> XgramLaterEngine.restore(accountId, keys, previous);
    if (!XgramSnackbar.showUndo(text, undo)) {
      UI.showToast(pointToLater && !XgramExpressive.isEnabled() ? text + Lang.getString(R.string.XgramLaterFindInLater) : text, Toast.LENGTH_SHORT);
    }
  }

  private static void haptic (ViewController<?> c) {
    View view = c.getValue();
    if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }
  }

  // In the chat

  /** A due reminder or follow-up in the chat on screen: "Reminder: …" with Done (Expressive) or a toast. UI thread. */
  static void showInChat (Tdlib tdlib, XgramLaterRecord r) {
    String about = !r.note.isEmpty() ? r.note : !r.preview.isEmpty() ? r.preview : Lang.getString(R.string.XgramLaterNotifGeneric);
    String text = Lang.getString(R.string.XgramLaterInChatReminder, about);
    final int accountId = r.accountId;
    final String key = r.key;
    if (!XgramSnackbar.show(text, Toast.LENGTH_LONG, Lang.getString(R.string.XgramLaterNotifDone), () -> XgramLaterEngine.markDone(accountId, key))) {
      UI.showToast(text, Toast.LENGTH_LONG);
    }
  }

  /** Opening a snoozed chat: "Snoozed until …" with Unsnooze (Expressive) or a toast. UI thread. */
  static void showSnoozedInChat (MessagesController c, XgramLaterRecord r) {
    String text = r.isTimed() ? Lang.getString(R.string.XgramLaterSnoozedUntil, XgramLaterTimes.formatWake(r.wakeAt)) : snoozedText(c.tdlib(), r.chatId, 0);
    final Tdlib tdlib = c.tdlib();
    final long chatId = r.chatId;
    if (!XgramSnackbar.show(text, Toast.LENGTH_LONG, Lang.getString(R.string.XgramLaterUnsnooze), () -> unsnooze(c, tdlib, chatId))) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  // The Snoozed row's long press

  public static void showSnoozedRowMenu (ChatsController c, View anchor, TdApi.ChatList chatList) {
    final Tdlib tdlib = c.tdlib();
    Runnable wakeAll = () -> XgramLaterEngine.wakeAllNow(tdlib, chatList);
    Runnable hide = () -> {
      XgramLater.setShowRow(false);
      if (!XgramSnackbar.showUndo(Lang.getString(R.string.XgramLaterRowHidden), () -> XgramLater.setShowRow(true))) {
        UI.showToast(R.string.XgramLaterRowHiddenHint, Toast.LENGTH_LONG);
      }
    };
    if (XgramExpressive.isEnabled()) {
      List<XgramR5Chat.MenuEntry> entries = new ArrayList<>(2);
      entries.add(new XgramR5Chat.MenuEntry(R.id.btn_xgramLaterRowWakeAll, Lang.getString(R.string.XgramLaterRowWakeAll), R.drawable.baseline_notifications_active_24, wakeAll));
      entries.add(new XgramR5Chat.MenuEntry(R.id.btn_xgramLaterRowHide, Lang.getString(R.string.XgramLaterRowHide), R.drawable.baseline_close_24, hide));
      XgramR5Chat.showMenu(anchor, c.getThemeListeners(), entries);
      return;
    }
    c.showOptions(null,
      new int[] {R.id.btn_xgramLaterRowWakeAll, R.id.btn_xgramLaterRowHide, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramLaterRowWakeAll), Lang.getString(R.string.XgramLaterRowHide), Lang.getString(R.string.Cancel)},
      null,
      new int[] {R.drawable.baseline_notifications_active_24, R.drawable.baseline_close_24, R.drawable.baseline_cancel_24},
      (view, id) -> {
        if (id == R.id.btn_xgramLaterRowWakeAll) {
          wakeAll.run();
        } else if (id == R.id.btn_xgramLaterRowHide) {
          hide.run();
        }
        return true;
      });
  }

  // Notification permission (Android 13+), asked once; the item is saved either way

  static void ensureNotificationPermission (ViewController<?> c) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || c.isDestroyed()) {
      return;
    }
    if (NotificationManagerCompat.from(c.context()).areNotificationsEnabled()) {
      return;
    }
    if (Settings.instance().getBoolean(XgramLater.KEY_NOTIF_ASKED, false)) {
      return;
    }
    Settings.instance().putBoolean(XgramLater.KEY_NOTIF_ASKED, true);
    boolean asked = c.context().permissions().requestPostNotifications(granted -> {
      if (!granted && !c.isDestroyed()) {
        showNotificationPrompt(c);
      }
    });
    if (!asked) {
      showNotificationPrompt(c);
    }
  }

  static void showNotificationPrompt (ViewController<?> c) {
    Runnable open = () -> XgramLaterNotifications.openAppNotificationSettings(c.context());
    String headline = Lang.getString(R.string.XgramLaterNotifPromptTitle);
    String reason = Lang.getString(R.string.XgramLaterNotifPromptReason);
    if (!com.fahadali.xgram.appearance.XgramR6B1Prompts.showSettingsPrompt(c, R.drawable.baseline_notifications_24, headline, reason, open)) {
      c.openAlert(R.string.XgramLaterNotifPromptTitle, reason, (dialog, which) -> open.run(), true);
    }
  }

}
