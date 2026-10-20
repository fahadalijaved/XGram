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

import androidx.annotation.Nullable;

import java.security.SecureRandom;

/** Check In's decisions, as pure functions of a record and the time (server unix seconds). No Android, no TDLib. */
final class CheckInRules {
  private CheckInRules () { }

  /** The shortest Check In (15 minutes; 2 in debug builds) and the longest, Add time included. */
  static final long MIN_ETA = 900, MIN_ETA_DEBUG = 120, MAX_TOTAL = 86400;
  /** The alert must be scheduled at least this far ahead when it is created. */
  static final long MIN_LEAD = 120;
  /** No edits this close to the alert time, so an edit never races the send. */
  static final long CUTOFF = 60;
  /** Edits of the scheduled alert: at least this far apart; the first one may come this soon after it is confirmed. */
  static final long MIN_GAP = 120, FIRST_EDIT_DELAY = 30;
  /** Without a change, the alert's location time is still refreshed once it is this old. */
  static final long REFRESH_IDLE = 900;
  /** After the alert went out: edits at least 5 minutes apart, at most 12, for an hour. */
  static final long FOLLOW_GAP = 300, FOLLOW_WINDOW = 3600;
  static final int FOLLOW_MAX = 12;
  /** Closing: a delete confirmed this long before the alert time means it can't have gone out. */
  static final long SAFE_DELETE_MARGIN = 120;
  /** Closing: when the delete wasn't in time, the wording waits until this long after the alert time. */
  static final long AFTER_ALERT_WAIT = 60;
  /** A missing alert is taken as deleted elsewhere after two observations this far apart. */
  static final long MISSING_CONFIRM = 60;
  /** Prompts are skipped this close to the alert. */
  static final long PROMPT_SKIP = 30;
  static final long LAST_CALL_LEAD = 120;
  /** Add time is refused this close to the alert. */
  static final long ADD_TIME_CUTOFF = 60;
  /** The watch record (an ended Check In whose alert might still go out) is kept until this long after its alert time. */
  static final long WATCH_KEEP = 600;
  /** A Check In whose alert went out is dropped silently after a day. */
  static final long FIRED_KEEP = 86400;

  static final String REF_ALPHABET = "ACDEFHJKMNPRTUVWXY34679";
  static final int REF_LENGTH = 4;

  /** A location fix: [time] in server seconds. */
  static final class Fix {
    final double lat, lng;
    final float accuracy;
    final long time;
    final boolean coarse;

    Fix (double lat, double lng, float accuracy, long time, boolean coarse) {
      this.lat = lat;
      this.lng = lng;
      this.accuracy = accuracy;
      this.time = time;
      this.coarse = coarse;
    }
  }

  static String newRef (SecureRandom random) {
    StringBuilder b = new StringBuilder(REF_LENGTH);
    for (int i = 0; i < REF_LENGTH; i++) {
      b.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
    }
    return b.toString();
  }

  private static boolean isTrailingIgnorable (char c) {
    return Character.isWhitespace(c) || c == '‎' || c == '‏' || (c >= '⁦' && c <= '⁩') || c == ' ';
  }

  /** Whether a message text is the alert with this ref: the ref is always its last token. */
  static boolean isAlertText (@Nullable String text, @Nullable String ref) {
    if (text == null || ref == null || ref.isEmpty()) {
      return false;
    }
    int end = text.length();
    while (end > 0 && isTrailingIgnorable(text.charAt(end - 1))) {
      end--;
    }
    int start = end - ref.length();
    if (start < 1) {
      return false;
    }
    if (!text.regionMatches(start, ref, 0, ref.length())) {
      return false;
    }
    char before = text.charAt(start - 1);
    return before == ' ' || before == ' ' || before == '‎' || before == '‏';
  }

  /** Meters between two points (haversine). */
  static double distance (double lat1, double lng1, double lat2, double lng2) {
    double r = 6371008.8;
    double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  /** The back-by time picked, held to the shortest and longest Check In. */
  static long clampEta (long eta, long now, long created, boolean debug) {
    long min = now + (debug ? MIN_ETA_DEBUG : MIN_ETA);
    long max = created + MAX_TOTAL;
    return Math.min(max, Math.max(min, eta));
  }

  /** Whether the alert can still be created this far ahead. */
  static boolean canSchedule (long sendAt, long now) {
    return sendAt - now >= MIN_LEAD;
  }

  private static boolean changed (CheckInRecord r, @Nullable Fix fix, int battery, boolean charging) {
    if (r.offAt != 0) {
      return true; // the phone is on again: the alert must stop saying it switched off
    }
    if (r.loc && fix != null) {
      if (r.lastFixAt == 0 || fix.coarse != r.coarse) {
        return true;
      }
      double moved = distance(r.lastLat, r.lastLng, fix.lat, fix.lng);
      if (moved >= Math.max(50f, fix.accuracy)) {
        return true;
      }
    }
    if (r.bat && battery >= 0) {
      if (r.lastBat < 0 || battery / 5 != r.lastBat / 5 || charging != r.lastCharging) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the scheduled alert should be edited now with [fix] and [battery]. [shutdown]: the phone is switching off, so
   * the gap between edits is skipped (not the cutoff before the alert time).
   */
  static boolean shouldEdit (CheckInRecord r, long now, @Nullable Fix fix, int battery, boolean charging, boolean shutdown) {
    if (r.state != CheckInRecord.ARMED || !r.alertReal) {
      return false;
    }
    if (now >= r.sendAt - CUTOFF || now < r.editPausedUntil) {
      return false;
    }
    if (shutdown) {
      return true;
    }
    if (r.lastEditAt == 0) {
      if (now - r.armedAt < FIRST_EDIT_DELAY) {
        return false;
      }
    } else if (now - r.lastEditAt < MIN_GAP) {
      return false;
    }
    if (changed(r, fix, battery, charging)) {
      return true;
    }
    // Nothing moved: the location's time is still refreshed now and then, if there is a newer fix.
    long since = now - (r.lastEditAt != 0 ? r.lastEditAt : r.armedAt);
    return r.loc && fix != null && fix.time > r.lastFixAt && since >= REFRESH_IDLE;
  }

  /** Whether the alert that went out should be edited with a newer location (Keep the sent alert updated). */
  static boolean shouldFollowEdit (CheckInRecord r, long now, @Nullable Fix fix, int battery, boolean charging) {
    if (r.state != CheckInRecord.FIRED || !r.follow || !r.loc || r.firedMsgId == 0 || fix == null) {
      return false;
    }
    if (now >= r.sendAt + FOLLOW_WINDOW || r.followEdits >= FOLLOW_MAX || now < r.editPausedUntil) {
      return false;
    }
    if (r.lastEditAt != 0 && now - r.lastEditAt < FOLLOW_GAP) {
      return false;
    }
    return changed(r, fix, battery, charging);
  }

  static final int MISSING_FIRED = 0, MISSING_FIRED_UNCONFIRMED = 1, MISSING_RECHECK = 2, MISSING_DELETED = 3;

  /** An armed alert that the server no longer lists as scheduled. */
  static int classifyMissing (CheckInRecord r, long now, boolean foundInHistory) {
    if (foundInHistory) {
      return MISSING_FIRED;
    }
    if (now >= r.sendAt - 10) {
      // Missing at about its time with nothing found: taken as sent, which is the safe side.
      return MISSING_FIRED_UNCONFIRMED;
    }
    if (r.missingAt == 0 || now - r.missingAt < MISSING_CONFIRM) {
      return MISSING_RECHECK;
    }
    return MISSING_DELETED;
  }

  static final int CLOSE_FINISH = 0, CLOSE_DELETE = 1, CLOSE_FALSE_ALARM = 2, CLOSE_NOT_FIRED_FAST = 3, CLOSE_WAIT = 4, CLOSE_CHECK_HISTORY = 5;

  /** The next step of ending a Check In, from what the server lists ([alertListed]: still scheduled). */
  static int closeDecision (CheckInRecord r, long now, boolean alertListed) {
    if (r.closeSent) {
      return CLOSE_FINISH;
    }
    if (!r.fired && alertListed) {
      return CLOSE_DELETE;
    }
    if (r.fired) {
      return CLOSE_FALSE_ALARM;
    }
    if (r.deleteOkAt != 0 && r.deleteOkAt <= r.sendAt - SAFE_DELETE_MARGIN) {
      return CLOSE_NOT_FIRED_FAST;
    }
    if (r.deleteOkAt == 0 && now < r.sendAt - SAFE_DELETE_MARGIN) {
      // Gone before its time without our delete (deleted elsewhere, or sent with Send now): history tells which.
      return CLOSE_CHECK_HISTORY;
    }
    if (now < r.sendAt + AFTER_ALERT_WAIT) {
      return CLOSE_WAIT;
    }
    return CLOSE_CHECK_HISTORY;
  }

  /** Whether a closing message is sent at all: not when nothing was ever said in the chat. */
  static boolean needsClosingMessage (CheckInRecord r) {
    return r.startSent || r.fired;
  }

  static boolean promptDue (CheckInRecord r, long now) {
    return r.state == CheckInRecord.ARMED && !r.prompted && now >= r.eta && now < r.sendAt - PROMPT_SKIP;
  }

  /**
   * Whether this Check In has a last call at all: only when it comes after the back-by time (an alert delay longer than
   * 2 minutes), so "Are you OK?" is always the first prompt. The 1 minute debug delay has none.
   */
  static boolean hasLastCall (CheckInRecord r) {
    return r.sendAt - LAST_CALL_LEAD > r.eta;
  }

  static boolean lastCallDue (CheckInRecord r, long now) {
    return r.state == CheckInRecord.ARMED && !r.lastCall && hasLastCall(r) && now >= r.sendAt - LAST_CALL_LEAD && now < r.sendAt - PROMPT_SKIP;
  }

  static final int ADD_OK = 0, ADD_OFFLINE = 1, ADD_TOO_LATE = 2, ADD_TOO_LONG = 3, ADD_NOT_ARMED = 4;

  static int canAddTime (CheckInRecord r, long now, long seconds, boolean connected) {
    if (r.state != CheckInRecord.ARMED || !r.alertReal) {
      return ADD_NOT_ARMED;
    }
    if (!connected) {
      return ADD_OFFLINE;
    }
    if (now >= r.sendAt - ADD_TIME_CUTOFF) {
      return ADD_TOO_LATE;
    }
    if (r.eta + seconds > r.created + MAX_TOTAL) {
      return ADD_TOO_LONG;
    }
    return ADD_OK;
  }

  /** Whether a fix counts as being at the destination. */
  static boolean isInside (CheckInRecord r, Fix fix) {
    if (!r.hasDest) {
      return false;
    }
    float radius = Math.max(r.destRadius, 1);
    if (fix.coarse) {
      // Approximate location only: the radius is raised to what it can tell apart.
      radius = Math.max(radius, fix.accuracy);
    }
    if (fix.accuracy > Math.max(50f, radius)) {
      return false;
    }
    return distance(fix.lat, fix.lng, r.destLat, r.destLng) <= radius;
  }

  /** Arrival: inside since [insideSince] (the first inside fix, 0: none) and inside again at least a minute later. */
  static boolean arrived (CheckInRecord r, Fix fix, long insideSince) {
    return insideSince != 0 && isInside(r, fix) && fix.time - insideSince >= 60;
  }

  /** Seconds between location updates: 5 minutes, 1 near the destination or the deadline, 30 s once inside it. */
  static long interval (CheckInRecord r, long now, double distanceToDest, boolean inside) {
    if (inside) {
      return 30;
    }
    if (r.hasDest && distanceToDest >= 0 && distanceToDest <= 1000) {
      return 60;
    }
    if (r.state == CheckInRecord.ARMED && r.eta - now <= 900) {
      return 60;
    }
    return 300;
  }
}
