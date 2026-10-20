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
package com.fahadali.xgram.checkin;

import android.util.Log;

import org.drinkless.tdlib.TdApi;

import java.security.SecureRandom;

/**
 * Debug builds: checks Check In's rules once per process on the engine thread (there is no JVM test source set) and
 * logs PASS or FAIL for each case under the tag XgramCheckIn.
 */
final class CheckInSelfTest {
  private CheckInSelfTest () { }

  private static boolean ran;
  private static int failures;

  private static void check (String name, boolean ok) {
    if (!ok) {
      failures++;
    }
    Log.i(XgramCheckIn.TAG, (ok ? "CheckInSelfTest PASS " : "CheckInSelfTest FAIL ") + name);
  }

  private static CheckInRecord armed (long now) {
    CheckInRecord r = new CheckInRecord();
    r.acc = 0;
    r.chat = 1234;
    r.ref = "7K2M";
    r.title = "Test";
    r.created = now - 600;
    r.eta = now + 3600;
    r.grace = 900;
    r.sendAt = r.eta + r.grace;
    r.armedAt = now - 300;
    r.state = CheckInRecord.ARMED;
    r.alertReal = true;
    r.alertId = 99;
    r.loc = true;
    r.bat = true;
    r.lastFixAt = now - 300;
    r.lastLat = 24.7136;
    r.lastLng = 46.6753;
    r.lastAcc = 20;
    r.lastBat = 50;
    r.lastEditAt = now - 300;
    return r;
  }

  static void run () {
    if (ran) {
      return;
    }
    ran = true;
    failures = 0;
    try {
      runCases();
    } catch (Throwable t) {
      failures++;
      Log.e(XgramCheckIn.TAG, "CheckInSelfTest FAIL crashed", t);
    }
    Log.i(XgramCheckIn.TAG, "CheckInSelfTest done, failures: " + failures);
  }

  private static void runCases () {
    long now = 1_800_000_000L;

    // Records
    CheckInRecord r = armed(now);
    r.hasDest = true;
    r.destLat = 1.5;
    r.destLng = 2.5;
    r.destRadius = 200;
    r.destLabel = "Home 🏠";
    CheckInRecord back = CheckInRecord.fromJson(r.toJson());
    check("json round trip", back != null && back.ref.equals(r.ref) && back.sendAt == r.sendAt && back.hasDest && back.destLabel.equals(r.destLabel) && back.lastBat == 50 && back.state == CheckInRecord.ARMED);
    check("json unknown version", CheckInRecord.fromJson(r.toJson().replace("\"v\":1", "\"v\":2")) == null);
    check("json bad data", CheckInRecord.fromJson("{not json") == null && CheckInRecord.fromJson(null) == null && CheckInRecord.fromJson("{}") == null);

    // Edits of the scheduled alert
    CheckInRecord e = armed(now);
    CheckInRules.Fix still = new CheckInRules.Fix(e.lastLat, e.lastLng, 20, now, false);
    CheckInRules.Fix moved = new CheckInRules.Fix(e.lastLat + 0.01, e.lastLng, 20, now, false);
    check("edit when moved", CheckInRules.shouldEdit(e, now, moved, 50, false, false));
    check("no edit when still", !CheckInRules.shouldEdit(e, now, still, 50, false, false));
    check("edit on battery step", CheckInRules.shouldEdit(e, now, still, 44, false, false));
    check("no edit inside battery step", !CheckInRules.shouldEdit(e, now, still, 52, false, false));
    e.lastEditAt = now - 60;
    check("no edit within 120 s", !CheckInRules.shouldEdit(e, now, moved, 50, false, false));
    e.lastEditAt = now - 300;
    check("no edit 60 s before the alert", !CheckInRules.shouldEdit(e, e.sendAt - 59, moved, 50, false, false));
    check("shutdown skips the gap", CheckInRules.shouldEdit(e, now, still, 50, false, true));
    check("shutdown keeps the cutoff", !CheckInRules.shouldEdit(e, e.sendAt - 30, still, 50, false, true));
    e.editPausedUntil = now + 100;
    check("no edit while flood waiting", !CheckInRules.shouldEdit(e, now, moved, 50, false, false));
    e.editPausedUntil = 0;
    e.lastEditAt = now - 1000;
    CheckInRules.Fix newer = new CheckInRules.Fix(e.lastLat, e.lastLng, 20, now, false);
    check("idle refresh after 15 min", CheckInRules.shouldEdit(e, now, newer, 50, false, false));
    CheckInRecord first = armed(now);
    first.lastEditAt = 0;
    first.armedAt = now - 10;
    check("first edit waits 30 s", !CheckInRules.shouldEdit(first, now, moved, 50, false, false));
    first.armedAt = now - 40;
    check("first edit after 30 s", CheckInRules.shouldEdit(first, now, moved, 50, false, false));

    // Follow-up edits after the alert went out
    CheckInRecord f = armed(now);
    f.state = CheckInRecord.FIRED;
    f.follow = true;
    f.firedMsgId = 5;
    f.sendAt = now - 600;
    f.lastEditAt = now - 400;
    check("follow edit when moved", CheckInRules.shouldFollowEdit(f, now, moved, 50, false));
    f.followEdits = CheckInRules.FOLLOW_MAX;
    check("follow edit cap", !CheckInRules.shouldFollowEdit(f, now, moved, 50, false));
    f.followEdits = 0;
    check("follow edit window", !CheckInRules.shouldFollowEdit(f, f.sendAt + 3601, moved, 50, false));

    // Missing alert
    CheckInRecord m = armed(now);
    check("missing found in history", CheckInRules.classifyMissing(m, now, true) == CheckInRules.MISSING_FIRED);
    check("missing at its time", CheckInRules.classifyMissing(m, m.sendAt - 5, false) == CheckInRules.MISSING_FIRED_UNCONFIRMED);
    check("missing first time", CheckInRules.classifyMissing(m, now, false) == CheckInRules.MISSING_RECHECK);
    m.missingAt = now - 61;
    check("missing confirmed", CheckInRules.classifyMissing(m, now, false) == CheckInRules.MISSING_DELETED);

    // Closing decisions
    CheckInRecord c = armed(now);
    c.state = CheckInRecord.CLOSING;
    check("close: delete first", CheckInRules.closeDecision(c, now, true) == CheckInRules.CLOSE_DELETE);
    c.deleteOkAt = now;
    check("close: fast path", CheckInRules.closeDecision(c, now, false) == CheckInRules.CLOSE_NOT_FIRED_FAST);
    c.deleteOkAt = c.sendAt - 30;
    check("close: wait near the alert", CheckInRules.closeDecision(c, c.sendAt - 30, false) == CheckInRules.CLOSE_WAIT);
    check("close: history after it", CheckInRules.closeDecision(c, c.sendAt + 61, false) == CheckInRules.CLOSE_CHECK_HISTORY);
    c.deleteOkAt = 0;
    check("close: gone early", CheckInRules.closeDecision(c, now, false) == CheckInRules.CLOSE_CHECK_HISTORY);
    c.fired = true;
    check("close: false alarm", CheckInRules.closeDecision(c, now, false) == CheckInRules.CLOSE_FALSE_ALARM);
    c.closeSent = true;
    check("close: sent once", CheckInRules.closeDecision(c, now, true) == CheckInRules.CLOSE_FINISH);

    // Closing texts
    CheckInRecord t = armed(now);
    t.closeKind = CheckInRecord.KIND_SAFE;
    String safe = CheckInText.closing(t, false).text, falseAlarm = CheckInText.closing(t, true).text;
    t.closeKind = CheckInRecord.KIND_CANCELLED;
    String cancelled = CheckInText.closing(t, false).text;
    t.closeKind = CheckInRecord.KIND_ARRIVED;
    t.hasDest = true;
    t.destLabel = "Office";
    String arrived = CheckInText.closing(t, false).text, arrivedAfter = CheckInText.closing(t, true).text;
    check("close texts differ", !safe.equals(falseAlarm) && !safe.equals(cancelled) && arrived.contains("Office") && arrivedAfter.contains("Office") && !arrived.equals(arrivedAfter));

    // Arrival
    CheckInRecord a = armed(now);
    a.hasDest = true;
    a.destLat = 24.7136;
    a.destLng = 46.6753;
    a.destRadius = 200;
    CheckInRules.Fix in1 = new CheckInRules.Fix(24.7137, 46.6753, 30, now, false);
    CheckInRules.Fix in2 = new CheckInRules.Fix(24.7137, 46.6754, 30, now + 61, false);
    CheckInRules.Fix far = new CheckInRules.Fix(24.75, 46.6753, 30, now + 61, false);
    CheckInRules.Fix vague = new CheckInRules.Fix(24.7137, 46.6753, 900, now + 61, false);
    check("arrival needs two fixes", !CheckInRules.arrived(a, in1, 0) && CheckInRules.arrived(a, in2, in1.time));
    check("arrival needs 60 s", !CheckInRules.arrived(a, new CheckInRules.Fix(24.7137, 46.6753, 30, now + 30, false), in1.time));
    check("arrival outside", !CheckInRules.arrived(a, far, in1.time));
    check("arrival needs accuracy", !CheckInRules.arrived(a, vague, in1.time));

    // Back-by time limits
    check("eta at least 15 min", CheckInRules.clampEta(now + 60, now, now, false) == now + 900);
    check("eta at most 24 h", CheckInRules.clampEta(now + 100_000, now, now, false) == now + 86400);
    check("eta debug 2 min", CheckInRules.clampEta(now + 60, now, now, true) == now + 120);
    check("lead 120 s", CheckInRules.canSchedule(now + 120, now) && !CheckInRules.canSchedule(now + 119, now));
    CheckInRecord add = armed(now);
    check("add time ok", CheckInRules.canAddTime(add, now, 900, true) == CheckInRules.ADD_OK);
    check("add time offline", CheckInRules.canAddTime(add, now, 900, false) == CheckInRules.ADD_OFFLINE);
    check("add time too late", CheckInRules.canAddTime(add, add.sendAt - 30, 900, true) == CheckInRules.ADD_TOO_LATE);
    check("add time past 24 h", CheckInRules.canAddTime(add, now, 90_000, true) == CheckInRules.ADD_TOO_LONG);

    // Refs
    SecureRandom random = new SecureRandom();
    boolean alphabet = true;
    for (int i = 0; i < 200; i++) {
      String ref = CheckInRules.newRef(random);
      if (ref.length() != CheckInRules.REF_LENGTH) {
        alphabet = false;
      }
      for (int j = 0; j < ref.length(); j++) {
        if (CheckInRules.REF_ALPHABET.indexOf(ref.charAt(j)) < 0) {
          alphabet = false;
        }
      }
    }
    check("ref alphabet", alphabet);
    check("ref at the end", CheckInRules.isAlertText("Automatic Check In alert · 7K2M", "7K2M"));
    check("ref with bidi marks", CheckInRules.isAlertText("تنبيه · 7K2M‏⁩ \n", "7K2M"));
    check("ref not a suffix of a word", !CheckInRules.isAlertText("alert X7K2M", "7K2M"));
    check("ref elsewhere", !CheckInRules.isAlertText("7K2M is not last", "7K2M"));

    // DateTime entity offsets (UTF-16) after emoji, Arabic and CJK text
    String[] labels = {"Home 🏠👍", "المنزل", "我的家 𠮷"};
    boolean offsets = true;
    for (String label : labels) {
      CheckInText.Builder b = new CheckInText.Builder(true);
      CheckInText.Time time = new CheckInText.Time("22:40", now, true);
      b.format("%1$s by %2$s, alert %3$s.", label, time, new CheckInText.Time("22:55", now + 900, false));
      TdApi.FormattedText ft = b.build();
      if (ft.entities.length != 2) {
        offsets = false;
        continue;
      }
      TdApi.TextEntity e1 = ft.entities[0], e2 = ft.entities[1];
      offsets &= ft.text.substring(e1.offset, e1.offset + e1.length).equals("22:40");
      offsets &= ft.text.substring(e2.offset, e2.offset + e2.length).equals("22:55");
      offsets &= e1.type instanceof TdApi.TextEntityTypeDateTime && ((TdApi.TextEntityTypeDateTime) e1.type).unixTime == now;
    }
    check("DateTime offsets", offsets);
    CheckInText.Builder plain = new CheckInText.Builder(false);
    plain.format("%1$s 100%%", new CheckInText.Time("22:40", now, true));
    TdApi.FormattedText pt = plain.build();
    check("entities off", pt.entities.length == 0 && pt.text.equals("22:40 100%"));

    // The alert text ends with the ref
    CheckInRecord al = armed(now);
    al.me = "Fahad";
    String alertText = CheckInText.alert(al, now).text;
    check("alert ends with ref", CheckInRules.isAlertText(alertText, al.ref) && alertText.contains("maps.google.com/?q=24.71360,46.67530"));
  }
}
