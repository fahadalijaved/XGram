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

import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.leveldb.LevelDB;

/**
 * Where Check Ins are kept: "xgram_ci_" + account + "_active" (the running one) and "_watch" (an ended one whose alert
 * could still go out). Live state, not settings: no backup list matches the prefix, and logging out erases it
 * (XgramAccountData). Writes happen on the engine thread; each is one putString of the whole record.
 */
final class CheckInStore {
  private CheckInStore () { }

  static final String PREFIX = "xgram_ci_";
  private static final String ACTIVE = "_active", WATCH = "_watch";

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  static String key (CheckInRecord r) {
    return PREFIX + r.acc + (r.state == CheckInRecord.WATCH ? WATCH : ACTIVE);
  }

  static String activeKey (int acc) {
    return PREFIX + acc + ACTIVE;
  }

  static String watchKey (int acc) {
    return PREFIX + acc + WATCH;
  }

  static void save (CheckInRecord r) {
    pmc().putString(key(r), r.toJson());
  }

  static void remove (String key) {
    pmc().remove(key);
  }

  @Nullable
  static CheckInRecord load (String key) {
    return CheckInRecord.fromJson(pmc().getString(key, null));
  }

  /** Every saved record, active and watch; unreadable ones are dropped. Any thread (reads only). */
  static List<CheckInRecord> loadAll () {
    List<String> keys = new ArrayList<>();
    List<String> values = new ArrayList<>();
    for (LevelDB.Entry entry : pmc().find(PREFIX)) {
      keys.add(entry.key());
      values.add(entry.asString());
    }
    List<CheckInRecord> result = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      String k = keys.get(i);
      CheckInRecord r = CheckInRecord.fromJson(values.get(i));
      if (r == null || !k.equals(key(r))) {
        // A record this build can't read (a newer version) is left alone, never deleted.
        continue;
      }
      result.add(r);
    }
    return result;
  }
}
