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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * One Later item, stored on this device only: a message reminder, a follow-up on your own message or a chat snooze
 * (message id 0). Immutable; changes go through {@link Builder}. Stored as JSON under {@link #key}.
 */
public final class XgramLaterRecord {
  public static final String PREFIX = "xgram_lt_";
  public static final int VERSION = 1;

  public static final int KIND_REMIND = 1, KIND_SNOOZE = 2, KIND_SNOOZE_UNTIL = 3, KIND_FOLLOWUP = 4, KIND_DISMISS = 5;
  public static final int STATE_PENDING = 0, STATE_DUE = 1, STATE_DONE = 2;
  public static final int DONE_USER = 0, DONE_OPENED = 1, DONE_REPLIED = 2, DONE_WOKE = 3, DONE_EXPIRED = 4;
  public static final int WAKE_TO_ME = 0, WAKE_ANY = 1, WAKE_TIME_ONLY = 2;

  public static final int FLAG_NO_PREVIEW = 1, FLAG_SEEN = 2, FLAG_NOTIFIED = 4, FLAG_MSG_GONE = 8, FLAG_SILENT = 16,
    FLAG_SECRET = 32, FLAG_PROTECTED = 64;
  /**
   * A snooze started again without its chat at hand (no TDLib in this process): its last message and mention count are
   * taken when the chat list first sees the chat, and nothing wakes it by the chat's state before that.
   */
  public static final int FLAG_RESYNC = 128;

  public static final int NOTE_MAX_LENGTH = 200;

  public final int accountId;
  public final long chatId, messageId;
  public final String key;
  public final int kind, state;
  /** Epoch ms, device clock; 0: no time (a snooze until something happens). */
  public final long wakeAt;
  public final long createdAt, dueAt, doneAt;
  public final int doneReason, wakeMode;
  /** The chat's last message when snoozed, or the message a follow-up waits on a reply to. */
  public final long since;
  public final int mentionsAt;
  public final int topicId;
  public final int flags;
  @NonNull public final String note, title, preview;

  private XgramLaterRecord (Builder b) {
    this.accountId = b.accountId;
    this.chatId = b.chatId;
    this.messageId = b.messageId;
    this.key = key(b.accountId, b.chatId, b.messageId);
    this.kind = b.kind;
    this.state = b.state;
    this.wakeAt = b.wakeAt;
    this.createdAt = b.createdAt;
    this.dueAt = b.dueAt;
    this.doneAt = b.doneAt;
    this.doneReason = b.doneReason;
    this.wakeMode = b.wakeMode;
    this.since = b.since;
    this.mentionsAt = b.mentionsAt;
    this.topicId = b.topicId;
    this.flags = b.flags;
    this.note = b.note != null ? b.note : "";
    this.title = b.title != null ? b.title : "";
    this.preview = b.preview != null ? b.preview : "";
  }

  public static String key (int accountId, long chatId, long messageId) {
    return PREFIX + accountId + "_" + chatId + "_" + messageId;
  }

  /** {accountId, chatId, messageId}, or null when {@code key} is not a Later key. Negative chat ids parse too. */
  @Nullable
  public static long[] parseKey (@Nullable String key) {
    if (key == null || !key.startsWith(PREFIX)) {
      return null;
    }
    String[] parts = key.substring(PREFIX.length()).split("_");
    if (parts.length != 3) {
      return null;
    }
    try {
      return new long[] {Integer.parseInt(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public boolean isChatSnooze () {
    return messageId == 0;
  }

  public boolean isSnooze () {
    return kind == KIND_SNOOZE || kind == KIND_SNOOZE_UNTIL;
  }

  public boolean isPending () {
    return state == STATE_PENDING;
  }

  public boolean isDue () {
    return state == STATE_DUE;
  }

  public boolean isDone () {
    return state == STATE_DONE;
  }

  public boolean isTimed () {
    return wakeAt != 0;
  }

  public boolean hasFlag (int flag) {
    return (flags & flag) != 0;
  }

  /** Due and not seen yet: the chat shows the bell and moves up. */
  public boolean hasBell () {
    return state == STATE_DUE && (flags & FLAG_SEEN) == 0 && kind != KIND_DISMISS;
  }

  /** Message reminders, follow-ups and snoozes can be woken; DISMISS only hides a suggestion. */
  public boolean isVisibleItem () {
    return kind != KIND_DISMISS;
  }

  public Builder buildUpon () {
    return new Builder(this);
  }

  // JSON

  public String toJson () {
    try {
      JSONObject o = new JSONObject();
      o.put("v", VERSION);
      o.put("chat", chatId);
      o.put("msg", messageId);
      o.put("kind", kind);
      o.put("state", state);
      o.put("wakeAt", wakeAt);
      o.put("createdAt", createdAt);
      o.put("dueAt", dueAt);
      o.put("doneAt", doneAt);
      o.put("doneReason", doneReason);
      o.put("wakeMode", wakeMode);
      o.put("since", since);
      o.put("mentionsAt", mentionsAt);
      o.put("topicId", topicId);
      o.put("flags", flags);
      o.put("note", note);
      o.put("title", title);
      o.put("preview", preview);
      return o.toString();
    } catch (Throwable t) {
      return "{}";
    }
  }

  /**
   * The record stored under ({@code accountId}, {@code chatId}, {@code messageId}), or null when the JSON can't be read
   * or comes from a newer version (the caller keeps the raw value and skips it).
   */
  @Nullable
  public static XgramLaterRecord fromJson (int accountId, long chatId, long messageId, @Nullable String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    try {
      JSONObject o = new JSONObject(json);
      return fromJsonObject(accountId, chatId, messageId, o);
    } catch (Throwable t) {
      return null;
    }
  }

  @Nullable
  static XgramLaterRecord fromJsonObject (int accountId, long chatId, long messageId, JSONObject o) {
    int version = o.optInt("v", 0);
    if (version < 1 || version > VERSION) {
      return null;
    }
    int kind = o.optInt("kind", 0);
    int state = o.optInt("state", -1);
    if (kind < KIND_REMIND || kind > KIND_DISMISS || state < STATE_PENDING || state > STATE_DONE) {
      return null;
    }
    if ((kind == KIND_SNOOZE || kind == KIND_SNOOZE_UNTIL) != (messageId == 0)) {
      return null;
    }
    return new Builder(accountId, chatId, messageId, kind)
      .state(state)
      .wakeAt(o.optLong("wakeAt", 0))
      .createdAt(o.optLong("createdAt", 0))
      .dueAt(o.optLong("dueAt", 0))
      .doneAt(o.optLong("doneAt", 0))
      .doneReason(o.optInt("doneReason", DONE_USER))
      .wakeMode(o.optInt("wakeMode", WAKE_TO_ME))
      .since(o.optLong("since", 0))
      .mentionsAt(o.optInt("mentionsAt", 0))
      .topicId(o.optInt("topicId", 0))
      .flags(o.optInt("flags", 0))
      .note(o.optString("note", ""))
      .title(o.optString("title", ""))
      .preview(o.optString("preview", ""))
      .build();
  }

  /** The same record (same key) with every field equal. */
  public boolean sameAs (@Nullable XgramLaterRecord other) {
    return other != null && key.equals(other.key) && kind == other.kind && state == other.state && wakeAt == other.wakeAt &&
      createdAt == other.createdAt && dueAt == other.dueAt && doneAt == other.doneAt && doneReason == other.doneReason &&
      wakeMode == other.wakeMode && since == other.since && mentionsAt == other.mentionsAt && topicId == other.topicId &&
      flags == other.flags && note.equals(other.note) && title.equals(other.title) && preview.equals(other.preview);
  }

  public static final class Builder {
    private final int accountId;
    private final long chatId, messageId;
    private int kind;
    private int state = STATE_PENDING;
    private long wakeAt, createdAt, dueAt, doneAt;
    private int doneReason = DONE_USER, wakeMode = WAKE_TO_ME;
    private long since;
    private int mentionsAt, topicId, flags;
    private String note = "", title = "", preview = "";

    public Builder (int accountId, long chatId, long messageId, int kind) {
      this.accountId = accountId;
      this.chatId = chatId;
      this.messageId = messageId;
      this.kind = kind;
    }

    public Builder (XgramLaterRecord r) {
      this.accountId = r.accountId;
      this.chatId = r.chatId;
      this.messageId = r.messageId;
      this.kind = r.kind;
      this.state = r.state;
      this.wakeAt = r.wakeAt;
      this.createdAt = r.createdAt;
      this.dueAt = r.dueAt;
      this.doneAt = r.doneAt;
      this.doneReason = r.doneReason;
      this.wakeMode = r.wakeMode;
      this.since = r.since;
      this.mentionsAt = r.mentionsAt;
      this.topicId = r.topicId;
      this.flags = r.flags;
      this.note = r.note;
      this.title = r.title;
      this.preview = r.preview;
    }

    /** A copy for another account slot (a restored backup). */
    public Builder (int accountId, XgramLaterRecord r) {
      this(accountId, r.chatId, r.messageId, r.kind);
      this.state = r.state;
      this.wakeAt = r.wakeAt;
      this.createdAt = r.createdAt;
      this.dueAt = r.dueAt;
      this.doneAt = r.doneAt;
      this.doneReason = r.doneReason;
      this.wakeMode = r.wakeMode;
      this.since = r.since;
      this.mentionsAt = r.mentionsAt;
      this.topicId = r.topicId;
      this.flags = r.flags;
      this.note = r.note;
      this.title = r.title;
      this.preview = r.preview;
    }

    public Builder kind (int kind) {
      this.kind = kind;
      return this;
    }

    public Builder state (int state) {
      this.state = state;
      return this;
    }

    public Builder wakeAt (long wakeAt) {
      this.wakeAt = wakeAt;
      return this;
    }

    public Builder createdAt (long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder dueAt (long dueAt) {
      this.dueAt = dueAt;
      return this;
    }

    public Builder doneAt (long doneAt) {
      this.doneAt = doneAt;
      return this;
    }

    public Builder doneReason (int doneReason) {
      this.doneReason = doneReason;
      return this;
    }

    public Builder wakeMode (int wakeMode) {
      this.wakeMode = wakeMode;
      return this;
    }

    public Builder since (long since) {
      this.since = since;
      return this;
    }

    public Builder mentionsAt (int mentionsAt) {
      this.mentionsAt = mentionsAt;
      return this;
    }

    public Builder topicId (int topicId) {
      this.topicId = topicId;
      return this;
    }

    public Builder flags (int flags) {
      this.flags = flags;
      return this;
    }

    public Builder addFlags (int flags) {
      this.flags |= flags;
      return this;
    }

    public Builder clearFlags (int flags) {
      this.flags &= ~flags;
      return this;
    }

    public Builder note (@Nullable String note) {
      String value = note != null ? note.trim() : "";
      if (value.length() > NOTE_MAX_LENGTH) {
        int end = NOTE_MAX_LENGTH;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
          end--; // never half an emoji
        }
        value = value.substring(0, end);
      }
      this.note = value;
      return this;
    }

    public Builder title (@Nullable String title) {
      this.title = title != null ? title : "";
      return this;
    }

    public Builder preview (@Nullable String preview) {
      this.preview = preview != null ? preview : "";
      return this;
    }

    public XgramLaterRecord build () {
      return new XgramLaterRecord(this);
    }
  }
}
