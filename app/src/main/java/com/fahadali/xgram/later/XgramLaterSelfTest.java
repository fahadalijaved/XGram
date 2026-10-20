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

import android.util.Log;

import org.drinkless.tdlib.TdApi;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Debug builds only, once per process: checks the pieces of Later that need no device state (JSON, keys, presets,
 * transitions, the chat list's position math) and logs the result under XGramLater:
 * adb logcat -c, open XGram, then adb logcat -d -s XGramLater
 */
final class XgramLaterSelfTest {
  private XgramLaterSelfTest () { }

  private static int passed, failed;

  private static void check (boolean ok, String what) {
    if (ok) {
      passed++;
    } else {
      failed++;
      Log.e(XgramLaterStore.TAG, "Self-test FAILED: " + what);
    }
  }

  static void run () {
    passed = failed = 0;
    try {
      json();
      keys();
      presets();
      transitions();
      positions();
    } catch (Throwable t) {
      failed++;
      Log.e(XgramLaterStore.TAG, "Self-test crashed", t);
    }
    Log.i(XgramLaterStore.TAG, "Self-test: " + passed + " passed, " + failed + " failed");
  }

  private static void json () {
    String[] notes = {"", "Call \"Alex\" back", "😀 emoji", "مرحبا RTL", "two\nlines"};
    int[] kinds = {XgramLaterRecord.KIND_REMIND, XgramLaterRecord.KIND_FOLLOWUP, XgramLaterRecord.KIND_DISMISS};
    for (int kind : kinds) {
      for (int state = XgramLaterRecord.STATE_PENDING; state <= XgramLaterRecord.STATE_DONE; state++) {
        for (String note : notes) {
          XgramLaterRecord r = new XgramLaterRecord.Builder(3, -1001234567890L, 77L << 20, kind)
            .state(state).wakeAt(1_700_000_000_000L).createdAt(1_699_000_000_000L).dueAt(state > 0 ? 1_700_000_001_000L : 0)
            .doneAt(state == 2 ? 1_700_000_002_000L : 0).doneReason(XgramLaterRecord.DONE_REPLIED).wakeMode(XgramLaterRecord.WAKE_ANY)
            .since(55).mentionsAt(2).topicId(9).flags(127).note(note).title("Design team").preview("Hi there").build();
          XgramLaterRecord back = XgramLaterRecord.fromJson(3, r.chatId, r.messageId, r.toJson());
          check(r.sameAs(back), "JSON round trip kind " + kind + " state " + state);
        }
      }
    }
    XgramLaterRecord snooze = new XgramLaterRecord.Builder(1, 42, 0, XgramLaterRecord.KIND_SNOOZE_UNTIL).build();
    check(snooze.sameAs(XgramLaterRecord.fromJson(1, 42, 0, snooze.toJson())), "JSON round trip snooze");
    check(XgramLaterRecord.fromJson(1, 42, 0, "{\"v\":99,\"kind\":2,\"state\":0}") == null, "a newer version is skipped");
    check(XgramLaterRecord.fromJson(1, 42, 0, "not json") == null, "broken JSON is skipped");
    check(XgramLaterRecord.fromJson(1, 42, 5, snooze.toJson()) == null, "a snooze with a message id is refused");
    StringBuilder longNote = new StringBuilder();
    for (int i = 0; i < 250; i++) {
      longNote.append('x');
    }
    check(new XgramLaterRecord.Builder(1, 1, 1, XgramLaterRecord.KIND_REMIND).note(longNote.toString()).build().note.length() == XgramLaterRecord.NOTE_MAX_LENGTH, "notes are cut at 200");
  }

  private static void keys () {
    String key = XgramLaterRecord.key(10, -1001234567890L, 0);
    long[] parts = XgramLaterRecord.parseKey(key);
    check(parts != null && parts[0] == 10 && parts[1] == -1001234567890L && parts[2] == 0, "key with a negative chat id parses");
    check(!key.startsWith(XgramLaterRecord.PREFIX + 1 + "_"), "account 1's prefix doesn't match account 10");
    check(XgramLaterRecord.parseKey("xgram_lt_1_2") == null, "a short key is refused");
    check(XgramLaterRecord.parseKey("xgram_bm_1_2_3") == null, "another prefix is refused");
  }

  private static long at (TimeZone tz, int year, int month, int day, int hour, int minute) {
    Calendar c = Calendar.getInstance(tz, Locale.US);
    c.clear();
    c.set(year, month, day, hour, minute, 0);
    return c.getTimeInMillis();
  }

  private static void presets () {
    TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
    Locale en = Locale.US;
    check(XgramLaterTimes.tonight(at(berlin, 2026, Calendar.MARCH, 10, 18, 59), berlin, en) == at(berlin, 2026, Calendar.MARCH, 10, 20, 0), "Tonight at 18:59");
    check(XgramLaterTimes.tonight(at(berlin, 2026, Calendar.MARCH, 10, 19, 0), berlin, en) == 0, "no Tonight from 19:00");
    check(XgramLaterTimes.isEarlyMorning(at(berlin, 2026, Calendar.MARCH, 10, 0, 30), berlin, en), "00:30 is early morning");
    check(XgramLaterTimes.tomorrowMorning(at(berlin, 2026, Calendar.MARCH, 10, 0, 30), berlin, en) == at(berlin, 2026, Calendar.MARCH, 10, 9, 0), "This morning, 9:00 at 00:30");
    // DST: Berlin springs forward on 29 March 2026 and falls back on 25 October 2026
    check(XgramLaterTimes.tomorrowMorning(at(berlin, 2026, Calendar.MARCH, 28, 22, 0), berlin, en) == at(berlin, 2026, Calendar.MARCH, 29, 9, 0), "Tomorrow 9:00 over the March change");
    check(XgramLaterTimes.tomorrowMorning(at(berlin, 2026, Calendar.OCTOBER, 24, 22, 0), berlin, en) == at(berlin, 2026, Calendar.OCTOBER, 25, 9, 0), "Tomorrow 9:00 over the October change");
    check(XgramLaterTimes.daysBetween(at(berlin, 2026, Calendar.MARCH, 28, 23, 0), at(berlin, 2026, Calendar.MARCH, 29, 23, 0), berlin, en) == 1, "one day over DST");
    long now = at(berlin, 2026, Calendar.OCTOBER, 20, 10, 0, 7);
    check(XgramLaterTimes.in20Minutes(now) == at(berlin, 2026, Calendar.OCTOBER, 20, 10, 21), "in 20 minutes rounds up to the minute");
    // Next week: the next first day of the week, at least a day ahead (20 October 2026 is a Tuesday)
    Locale[] locales = {Locale.US, Locale.UK, new Locale("ar", "SA")};
    for (Locale locale : locales) {
      long next = XgramLaterTimes.nextWeek(now, berlin, locale);
      Calendar c = Calendar.getInstance(berlin, locale);
      c.setTimeInMillis(next);
      check(c.get(Calendar.DAY_OF_WEEK) == c.getFirstDayOfWeek() && c.get(Calendar.HOUR_OF_DAY) == 9 && next > now + XgramLaterTimes.HOUR, "next week for " + locale);
    }
    long monday = at(berlin, 2026, Calendar.OCTOBER, 19, 10, 0);
    check(XgramLaterTimes.daysBetween(monday, XgramLaterTimes.nextWeek(monday, berlin, Locale.UK), berlin, Locale.UK) == 7, "next week on its first day is a week on");
  }

  private static long at (TimeZone tz, int year, int month, int day, int hour, int minute, int second) {
    return at(tz, year, month, day, hour, minute) + second * 1000L;
  }

  private static void transitions () {
    long now = 1_700_000_000_000L;
    XgramLaterRecord r = new XgramLaterRecord.Builder(1, 5, 100, XgramLaterRecord.KIND_REMIND).wakeAt(now).createdAt(now - 1000).build();
    XgramLaterRecord due = XgramLaterEngine.toDue(r, now);
    check(due.isDue() && due.hasBell(), "pending to due, with the bell");
    XgramLaterRecord done = XgramLaterEngine.toDone(due, XgramLaterRecord.DONE_USER, now + 1);
    check(done.isDone() && !done.hasBell(), "due to done");
    check(XgramLaterEngine.toDone(done, XgramLaterRecord.DONE_USER, now + 1).sameAs(done), "done twice is the same");
    XgramLaterRecord again = XgramLaterEngine.toPending(done.buildUpon().addFlags(XgramLaterRecord.FLAG_NOTIFIED).build(), now + 3600_000L);
    check(again.isPending() && !again.hasFlag(XgramLaterRecord.FLAG_SEEN) && !again.hasFlag(XgramLaterRecord.FLAG_NOTIFIED) && again.wakeAt == now + 3600_000L, "snooze again clears seen and notified");

    // Follow-ups: any incoming message in a private chat, only a reply to the message in a group
    XgramLaterRecord privateFollowUp = new XgramLaterRecord.Builder(1, 5, 100, XgramLaterRecord.KIND_FOLLOWUP).since(100).build();
    TdApi.Message incoming = new TdApi.Message();
    incoming.chatId = 5;
    incoming.id = 101;
    check(XgramLaterEngine.answers(privateFollowUp, incoming), "a private answer completes the follow-up");
    incoming.isOutgoing = true;
    check(!XgramLaterEngine.answers(privateFollowUp, incoming), "your own message doesn't");
    long group = -1001234567890L;
    XgramLaterRecord groupFollowUp = new XgramLaterRecord.Builder(1, group, 200, XgramLaterRecord.KIND_FOLLOWUP).since(200).build();
    TdApi.Message chatter = new TdApi.Message();
    chatter.chatId = group;
    chatter.id = 201;
    check(!XgramLaterEngine.answers(groupFollowUp, chatter), "group chatter doesn't complete it");
    TdApi.MessageReplyToMessage reply = new TdApi.MessageReplyToMessage();
    reply.chatId = group;
    reply.messageId = 200;
    chatter.replyTo = reply;
    check(XgramLaterEngine.answers(groupFollowUp, chatter), "a reply to the message does");

    // Wake rules
    XgramLaterRecord toMe = new XgramLaterRecord.Builder(1, group, 0, XgramLaterRecord.KIND_SNOOZE).since(10).wakeMode(XgramLaterRecord.WAKE_TO_ME).build();
    TdApi.Message message = new TdApi.Message();
    message.chatId = group;
    message.id = 11;
    check(!XgramLaterEngine.qualifies(toMe, message), "group chatter doesn't wake a messages-to-me snooze");
    message.containsUnreadMention = true;
    check(XgramLaterEngine.qualifies(toMe, message), "a mention wakes it");
    XgramLaterRecord timeOnly = toMe.buildUpon().wakeMode(XgramLaterRecord.WAKE_TIME_ONLY).build();
    check(!XgramLaterEngine.qualifies(timeOnly, message), "only at the time set never wakes on messages");

    // Import: overdue becomes due and silent
    XgramLaterRecord overdue = new XgramLaterRecord.Builder(1, 5, 300, XgramLaterRecord.KIND_REMIND).wakeAt(now - 1000).build();
    XgramLaterRecord imported = overdue.buildUpon().state(XgramLaterRecord.STATE_DUE).dueAt(now).addFlags(XgramLaterRecord.FLAG_SILENT).build();
    check(imported.isDue() && imported.hasFlag(XgramLaterRecord.FLAG_SILENT), "an overdue import is due and silent");
  }

  /** The Snoozed row between the archive and the chats, every combination of card, archive, row and chats. */
  private static void positions () {
    for (int s = 0; s <= 1; s++) {
      for (int a = 0; a <= 1; a++) {
        for (int r = 0; r <= 1; r++) {
          for (int n = 0; n <= 3; n += 3) {
            if (a == 1 && n == 0) {
              continue; // the archive needs chats
            }
            int chats = n + a; // the adapter's list holds the archive as index 0
            int count = XgramLaterUi.itemCount(s, r, chats);
            int rowPosition = XgramLaterUi.rowPosition(s, a, r);
            check(r == 0 ? rowPosition == -1 : rowPosition >= 0 && rowPosition < count, "row position s" + s + " a" + a + " r" + r + " n" + n);
            for (int ci = 0; ci < chats; ci++) {
              int position = XgramLaterUi.positionOfChatIndex(ci, s, a, r);
              check(position != rowPosition && position >= s && position < count, "chat " + ci + " in range s" + s + " a" + a + " r" + r);
              check(XgramLaterUi.chatIndexOfPosition(position, s, a, r) == ci, "inverse for chat " + ci + " s" + s + " a" + a + " r" + r);
            }
            if (rowPosition != -1) {
              check(XgramLaterUi.chatIndexOfPosition(rowPosition, s, a, r) == -1, "the row is no chat");
            }
            if (chats > 0) {
              int info = XgramLaterUi.positionOfChatIndex(chats, s, a, r);
              check(info == count - 1, "the info row is last s" + s + " a" + a + " r" + r);
            }
          }
        }
      }
    }
  }
}
