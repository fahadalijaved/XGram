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

import org.json.JSONObject;

/**
 * One Check In, as saved (write-ahead) in Settings.pmc() under {@link CheckInStore}'s keys. Times are server unix
 * seconds. Changed only on the engine thread; copies published for other threads are never changed.
 */
final class CheckInRecord {
  static final int VERSION = 1;

  static final int STARTING = 0, ARMED = 1, CLOSING = 2, FIRED = 3, WATCH = 4;
  static final int KIND_SAFE = 0, KIND_ARRIVED = 1, KIND_CANCELLED = 2;

  int acc;
  long chat;
  /** Forum topic id, 0: none (General or not a forum). */
  int topic;
  /** The chat title and your first name when it started: notifications and texts work without TDLib. */
  String title = "", me = "";
  String ref = "";
  long created, eta, grace, sendAt, armedAt;
  /** Server time minus the device clock, seconds, refreshed on every save: alarms and notifications use it. */
  long skew;
  int state = STARTING;
  int closeKind = KIND_SAFE;

  long alertId;
  boolean alertReal;
  long startMsgId;
  boolean startReal;
  long firedMsgId;
  boolean startSent, entitiesOff;

  boolean prompted, lastCall, firedNotified, fired;
  long missingAt;

  long closeAsk;
  boolean deleteAsked;
  long deleteOkAt;
  boolean closeSent, correctionSent;

  boolean loc, bat, follow;

  boolean hasDest;
  double destLat, destLng;
  int destRadius;
  String destLabel = "";

  /** What the alert says now: its location (lastFixAt 0: none), battery (-1: none), and when it was edited. */
  long lastEditAt;
  double lastLat, lastLng;
  float lastAcc;
  long lastFixAt;
  boolean coarse;
  int lastBat = -1;
  boolean lastCharging;
  long offAt, updatedAt;
  long editPausedUntil;
  int followEdits;

  boolean isActive () {
    return state != WATCH;
  }

  long nowFromDevice () {
    return System.currentTimeMillis() / 1000L + skew;
  }

  /** Device clock milliseconds of a server time. */
  long toDeviceMillis (long serverSeconds) {
    return (serverSeconds - skew) * 1000L;
  }

  CheckInRecord copy () {
    CheckInRecord r = fromJson(toJson());
    return r != null ? r : this;
  }

  String toJson () {
    try {
      JSONObject o = new JSONObject();
      o.put("v", VERSION);
      o.put("acc", acc);
      o.put("chat", chat);
      o.put("topic", topic);
      o.put("title", title);
      o.put("me", me);
      o.put("ref", ref);
      o.put("created", created);
      o.put("eta", eta);
      o.put("grace", grace);
      o.put("sendAt", sendAt);
      o.put("armedAt", armedAt);
      o.put("skew", skew);
      o.put("state", state);
      o.put("closeKind", closeKind);
      o.put("alertId", alertId);
      o.put("alertReal", alertReal);
      o.put("startMsgId", startMsgId);
      o.put("startReal", startReal);
      o.put("firedMsgId", firedMsgId);
      o.put("startSent", startSent);
      o.put("entitiesOff", entitiesOff);
      o.put("prompted", prompted);
      o.put("lastCall", lastCall);
      o.put("firedNotified", firedNotified);
      o.put("fired", fired);
      o.put("missingAt", missingAt);
      o.put("closeAsk", closeAsk);
      o.put("deleteAsked", deleteAsked);
      o.put("deleteOkAt", deleteOkAt);
      o.put("closeSent", closeSent);
      o.put("correctionSent", correctionSent);
      o.put("loc", loc);
      o.put("bat", bat);
      o.put("follow", follow);
      if (hasDest) {
        JSONObject d = new JSONObject();
        d.put("lat", destLat);
        d.put("lng", destLng);
        d.put("radius", destRadius);
        d.put("label", destLabel);
        o.put("dest", d);
      }
      o.put("lastEditAt", lastEditAt);
      o.put("lastLat", lastLat);
      o.put("lastLng", lastLng);
      o.put("lastAcc", (double) lastAcc);
      o.put("lastFixAt", lastFixAt);
      o.put("coarse", coarse);
      o.put("lastBat", lastBat);
      o.put("lastCharging", lastCharging);
      o.put("offAt", offAt);
      o.put("updatedAt", updatedAt);
      o.put("editPausedUntil", editPausedUntil);
      o.put("followEdits", followEdits);
      return o.toString();
    } catch (Throwable t) {
      return "{}";
    }
  }

  /** Null on bad data or a version this build doesn't know; never throws. */
  @Nullable
  static CheckInRecord fromJson (@Nullable String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    try {
      JSONObject o = new JSONObject(json);
      if (o.optInt("v", 0) != VERSION) {
        return null;
      }
      CheckInRecord r = new CheckInRecord();
      r.acc = o.getInt("acc");
      r.chat = o.getLong("chat");
      r.topic = o.optInt("topic", 0);
      r.title = o.optString("title", "");
      r.me = o.optString("me", "");
      r.ref = o.getString("ref");
      r.created = o.optLong("created");
      r.eta = o.optLong("eta");
      r.grace = o.optLong("grace");
      r.sendAt = o.optLong("sendAt");
      r.armedAt = o.optLong("armedAt");
      r.skew = o.optLong("skew");
      r.state = o.optInt("state", STARTING);
      if (r.state < STARTING || r.state > WATCH) {
        return null;
      }
      r.closeKind = o.optInt("closeKind", KIND_SAFE);
      r.alertId = o.optLong("alertId");
      r.alertReal = o.optBoolean("alertReal");
      r.startMsgId = o.optLong("startMsgId");
      r.startReal = o.optBoolean("startReal");
      r.firedMsgId = o.optLong("firedMsgId");
      r.startSent = o.optBoolean("startSent");
      r.entitiesOff = o.optBoolean("entitiesOff");
      r.prompted = o.optBoolean("prompted");
      r.lastCall = o.optBoolean("lastCall");
      r.firedNotified = o.optBoolean("firedNotified");
      r.fired = o.optBoolean("fired");
      r.missingAt = o.optLong("missingAt");
      r.closeAsk = o.optLong("closeAsk");
      r.deleteAsked = o.optBoolean("deleteAsked");
      r.deleteOkAt = o.optLong("deleteOkAt");
      r.closeSent = o.optBoolean("closeSent");
      r.correctionSent = o.optBoolean("correctionSent");
      r.loc = o.optBoolean("loc");
      r.bat = o.optBoolean("bat");
      r.follow = o.optBoolean("follow");
      JSONObject d = o.optJSONObject("dest");
      if (d != null) {
        r.hasDest = true;
        r.destLat = d.getDouble("lat");
        r.destLng = d.getDouble("lng");
        r.destRadius = d.optInt("radius", 200);
        r.destLabel = d.optString("label", "");
      }
      r.lastEditAt = o.optLong("lastEditAt");
      r.lastLat = o.optDouble("lastLat", 0);
      r.lastLng = o.optDouble("lastLng", 0);
      r.lastAcc = (float) o.optDouble("lastAcc", 0);
      r.lastFixAt = o.optLong("lastFixAt");
      r.coarse = o.optBoolean("coarse");
      r.lastBat = o.optInt("lastBat", -1);
      r.lastCharging = o.optBoolean("lastCharging");
      r.offAt = o.optLong("offAt");
      r.updatedAt = o.optLong("updatedAt");
      r.editPausedUntil = o.optLong("editPausedUntil");
      r.followEdits = o.optInt("followEdits");
      if (r.ref.isEmpty() || r.chat == 0) {
        return null;
      }
      return r;
    } catch (Throwable t) {
      return null;
    }
  }
}
