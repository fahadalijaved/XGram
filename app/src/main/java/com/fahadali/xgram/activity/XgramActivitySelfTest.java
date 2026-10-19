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
package com.fahadali.xgram.activity;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Debug builds only: run once on the store thread when the store opens, logged under XGramActivity. Checks the
 * classifier, the flood wait parsing and backoff, and the state machine on an in-memory database. (The app has no
 * unit test source set.)
 */
final class XgramActivitySelfTest {
  private XgramActivitySelfTest () { }

  private static final String TAG = XgramActivityStore.TAG;
  private static boolean ran;
  private static int failures;

  static void run (XgramActivityStore store) {
    if (ran) {
      return;
    }
    ran = true;
    failures = 0;
    classifier();
    flood();
    stateMachine();
    if (failures == 0) {
      Log.i(TAG, "Self test passed");
    } else {
      Log.e(TAG, "Self test: " + failures + " failed");
    }
  }

  private static void check (boolean ok, String what) {
    if (!ok) {
      failures++;
      Log.e(TAG, "Self test failed: " + what);
    }
  }

  // Classifier

  private static final long ME = 1000L, OTHER = 2000L, CHAT = -100123L;

  private static TdApi.Message message (long id, String text, TdApi.TextEntity[] entities, boolean flagged, TdApi.MessageReplyTo replyTo, boolean outgoing) {
    TdApi.Message m = new TdApi.Message();
    m.id = id;
    m.chatId = CHAT;
    m.senderId = new TdApi.MessageSenderUser(OTHER);
    m.isOutgoing = outgoing;
    m.containsUnreadMention = flagged;
    m.replyTo = replyTo;
    m.content = new TdApi.MessageText(new TdApi.FormattedText(text, entities != null ? entities : new TdApi.TextEntity[0]), null, null);
    return m;
  }

  private static TdApi.MessageReplyToMessage replyTo (long chatId, long messageId) {
    TdApi.MessageReplyToMessage r = new TdApi.MessageReplyToMessage();
    r.chatId = chatId;
    r.messageId = messageId;
    return r;
  }

  private static void classifier () {
    String[] usernames = {"Fahad"};
    // A mention by name
    TdApi.Message m = message(1, "hi Fahad", new TdApi.TextEntity[] {new TdApi.TextEntity(3, 5, new TdApi.TextEntityTypeMentionName(ME))}, true, null, false);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == XgramActivityItem.KIND_MENTION, "mention name");
    // An @username in another case
    m = message(2, "@fahad look", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 6, new TdApi.TextEntityTypeMention())}, true, null, false);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == XgramActivityItem.KIND_MENTION, "mention username case");
    int[] span = XgramActivityClassifier.findMentionSpan(((TdApi.MessageText) m.content).text, ME, usernames);
    check(span != null && span[0] == 0 && span[1] == 6, "mention span");
    // Someone else's username is no mention
    m = message(3, "@other look", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 6, new TdApi.TextEntityTypeMention())}, false, null, false);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == 0, "other username");
    // A reply that also mentions you
    m = message(4, "@Fahad yes", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 6, new TdApi.TextEntityTypeMention())}, true, replyTo(CHAT, 77), false);
    int r = XgramActivityClassifier.classify(m, ME, usernames, false);
    check(XgramActivityClassifier.kindOf(r) == XgramActivityItem.KIND_MENTION && (XgramActivityClassifier.flagsOf(r) & XgramActivityItem.FLAG_ALSO_REPLY) != 0, "reply and mention");
    // A flagged reply without an entity
    m = message(5, "yes", null, true, replyTo(0, 77), false);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == XgramActivityItem.KIND_REPLY, "flagged reply");
    // Flagged without an entity or a reply: a mention by name or an old username
    m = message(6, "hey you", null, true, null, false);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == XgramActivityItem.KIND_MENTION, "flagged mention");
    // A reply to another chat (a quote from elsewhere), not flagged
    m = message(7, "look", null, false, replyTo(-100999L, 5), false);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == 0, "external reply");
    // Your own message
    m = message(8, "@Fahad", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 6, new TdApi.TextEntityTypeMention())}, true, null, true);
    check(XgramActivityClassifier.classify(m, ME, usernames, false) == 0, "outgoing");
    // A search result counts as flagged
    m = message(9, "ok", null, false, replyTo(CHAT, 77), false);
    check(XgramActivityClassifier.classify(m, ME, usernames, true) == XgramActivityItem.KIND_REPLY, "search reply");
    // Spoilers are masked, the length is capped without cutting a pair
    TdApi.FormattedText spoiler = new TdApi.FormattedText("ab secret", new TdApi.TextEntity[] {new TdApi.TextEntity(3, 6, new TdApi.TextEntityTypeSpoiler())});
    String masked = XgramActivityClassifier.snapshotText(spoiler, 300);
    check(masked != null && !masked.contains("secret") && masked.startsWith("ab "), "spoiler masked");
    check("e:\u2764".equals(XgramActivityClassifier.reactionKey(new TdApi.ReactionTypeEmoji("\u2764"))), "reaction key");
    check(XgramActivityClassifier.decodeSender(XgramActivityClassifier.encodeSender(new TdApi.MessageSenderChat(CHAT))) instanceof TdApi.MessageSenderChat, "sender chat");
  }

  // Flood waits

  private static void flood () {
    check(XgramActivityClassifier.retryAfterSeconds(new TdApi.Error(429, "Too Many Requests: retry after 1200")) == 1200, "retry after, no cap");
    check(XgramActivityClassifier.retryAfterSeconds(new TdApi.Error(420, "FLOOD_WAIT_45")) == 45, "flood wait");
    check(XgramActivityClassifier.retryAfterSeconds(new TdApi.Error(400, "CHANNEL_PRIVATE")) == -1, "not a flood");
    check(XgramActivityClassifier.nextBackoffMs(10_000L, 1) == 60_000L, "backoff floor");
    check(XgramActivityClassifier.nextBackoffMs(100_000L, 1) == 120_000L, "backoff 1.2");
    check(XgramActivityClassifier.nextBackoffMs(100_000L, 2) == 240_000L, "backoff doubles");
    check(XgramActivityClassifier.nextBackoffMs(100_000L, 30) == XgramActivityClassifier.MAX_BACKOFF_MS, "backoff cap");
  }

  // State machine

  private static final int ACCOUNT = 0;

  private static XgramActivityStore.Snapshot snapshot (long messageId, int kind, int state, int date) {
    XgramActivityStore.Snapshot s = new XgramActivityStore.Snapshot();
    s.accountId = ACCOUNT;
    s.chatId = CHAT;
    s.messageId = messageId;
    s.kind = kind;
    s.state = state;
    s.messageDate = date;
    s.eventDate = date;
    s.text = "text " + messageId;
    return s;
  }

  private static int state (SQLiteDatabase db, long messageId, int kind) {
    return XgramActivityStore.stateOf(db, ACCOUNT, CHAT, messageId, kind);
  }

  private static XgramActivityStore.Reactions reactions (long messageId, int source, long... senders) {
    XgramActivityStore.Reactions r = new XgramActivityStore.Reactions();
    r.accountId = ACCOUNT;
    r.chatId = CHAT;
    r.messageId = messageId;
    r.source = source;
    r.messageDate = (int) XgramActivityStore.nowSeconds();
    for (long sender : senders) {
      r.reactors.add(new XgramActivityItem.Reactor(sender, "e:x", false, 0));
    }
    return r;
  }

  private static void stateMachine () {
    SQLiteDatabase db = null;
    try {
      db = SQLiteDatabase.create(null);
      XgramActivityStore.createSchema(db);
      final int now = (int) XgramActivityStore.nowSeconds();
      final int M = XgramActivityItem.KIND_MENTION, REACTION = XgramActivityItem.KIND_REACTION;

      // New, then read: Read; a later New upsert stays Read
      XgramActivityStore.upsert(db, snapshot(1, M, XgramActivityItem.STATE_NEW, now));
      check(state(db, 1, M) == XgramActivityItem.STATE_NEW, "insert new");
      XgramActivityStore.markRead(db, ACCOUNT, CHAT, 1, XgramActivityItem.MASK_MENTION_GROUP, false);
      check(state(db, 1, M) == XgramActivityItem.STATE_READ, "mark read");
      XgramActivityStore.upsert(db, snapshot(1, M, XgramActivityItem.STATE_NEW, now));
      check(state(db, 1, M) == XgramActivityItem.STATE_READ, "upsert keeps read");

      // A message is one item: a reply after a mention of the same message joins it
      XgramActivityStore.upsert(db, snapshot(1, XgramActivityItem.KIND_REPLY, XgramActivityItem.STATE_NEW, now));
      check(state(db, 1, XgramActivityItem.KIND_REPLY) == -1, "one item per message");

      // Clear, then undo
      List<XgramActivityItem> items = XgramActivityStore.queryItems(db, ACCOUNT, XgramActivityStore.FILTER_ALL, false, true, true, 10);
      check(items.size() == 1, "query");
      XgramActivityStore.setState(db, items, -1, XgramActivityItem.STATE_CLEARED, false);
      check(state(db, 1, M) == XgramActivityItem.STATE_CLEARED, "clear");
      check(XgramActivityStore.queryItems(db, ACCOUNT, XgramActivityStore.FILTER_ALL, false, true, true, 10).isEmpty(), "cleared hidden");
      XgramActivityStore.restoreStates(db, items);
      check(state(db, 1, M) == XgramActivityItem.STATE_READ, "undo");

      // A live new reactor revives a Cleared row; a search reactor does not
      XgramActivityStore.reactions(db, reactions(10, XgramActivityItem.SOURCE_LIVE, 5));
      check(state(db, 10, REACTION) == XgramActivityItem.STATE_NEW, "reaction new");
      List<XgramActivityItem> reaction = Collections.singletonList(XgramActivityStore.queryItems(db, ACCOUNT, XgramActivityStore.FILTER_REACTIONS, false, true, true, 10).get(0));
      XgramActivityStore.setState(db, reaction, -1, XgramActivityItem.STATE_CLEARED, false);
      XgramActivityStore.reactions(db, reactions(10, XgramActivityItem.SOURCE_UNREAD_SYNC, 5, 6));
      check(state(db, 10, REACTION) == XgramActivityItem.STATE_CLEARED, "sync reactor keeps cleared");
      XgramActivityStore.reactions(db, reactions(10, XgramActivityItem.SOURCE_LIVE, 5, 6, 7));
      check(state(db, 10, REACTION) == XgramActivityItem.STATE_NEW, "live reactor revives");
      // Taken back while New: the reactor goes
      XgramActivityStore.reactions(db, reactions(10, XgramActivityItem.SOURCE_LIVE, 5));
      check(XgramActivityStore.count(db, "SELECT COUNT(*) FROM reactors WHERE msg = 10") == 1, "withdrawn reactors");

      // Older than the horizon: ignored
      XgramActivityStore.upsert(db, snapshot(2, M, XgramActivityItem.STATE_NEW, now - (int) XgramActivityStore.HORIZON_SECONDS - 100));
      check(state(db, 2, M) == -1, "horizon");

      // Prune never removes New rows, and removes old Read ones
      XgramActivityStore.upsert(db, snapshot(3, M, XgramActivityItem.STATE_NEW, now));
      db.execSQL("UPDATE items SET event_date = ? WHERE msg = 3", new Object[] {now - (int) XgramActivityStore.HORIZON_SECONDS - 100});
      XgramActivityStore.upsert(db, snapshot(4, M, XgramActivityItem.STATE_READ, now));
      db.execSQL("UPDATE items SET event_date = ? WHERE msg = 4", new Object[] {now - (int) XgramActivityStore.HORIZON_SECONDS - 100});
      XgramActivityStore.prune(db);
      check(state(db, 3, M) == XgramActivityItem.STATE_NEW, "prune keeps new");
      check(state(db, 4, M) == -1, "prune drops old read");

      // Reconcile skips items stored after the request went out
      long sentAt = System.currentTimeMillis();
      db.execSQL("UPDATE items SET updated = ? WHERE msg = 3", new Object[] {sentAt - 1000});
      XgramActivityStore.upsert(db, snapshot(5, M, XgramActivityItem.STATE_NEW, now));
      db.execSQL("UPDATE items SET updated = ? WHERE msg = 5", new Object[] {sentAt + 1000});
      XgramActivityStore.reconcile(db, ACCOUNT, CHAT, XgramActivityItem.MASK_MENTION_GROUP, new long[0], sentAt, true, 0);
      check(state(db, 3, M) == XgramActivityItem.STATE_READ, "reconcile reads missing");
      check(state(db, 5, M) == XgramActivityItem.STATE_NEW, "reconcile skips newer");

      // Clear Activity: tombstones without text, nothing comes back from a search
      XgramActivityStore.clearAllRows(db, ACCOUNT);
      check(XgramActivityStore.count(db, "SELECT COUNT(*) FROM items WHERE state != 2") == 0, "clear all");
      check(XgramActivityStore.count(db, "SELECT COUNT(*) FROM items WHERE text IS NOT NULL") == 0, "clear all text");
      XgramActivityStore.upsert(db, snapshot(5, M, XgramActivityItem.STATE_NEW, now));
      check(state(db, 5, M) == XgramActivityItem.STATE_CLEARED, "cleared stays");
      check(XgramActivityStore.count(db, "SELECT COUNT(*) FROM items WHERE msg = 5 AND text IS NOT NULL") == 0, "cleared stays empty");

      // Summary
      XgramActivityStore.upsert(db, snapshot(20, M, XgramActivityItem.STATE_NEW, now));
      XgramActivityStore.Summary summary = XgramActivityStore.buildSummary(db, ACCOUNT);
      check(summary.chatHasNew(CHAT) && summary.knows(CHAT) && summary.newCount(CHAT, XgramActivityItem.MASK_MENTION_GROUP) == 1, "summary");
      List<Long> none = new ArrayList<>();
      check(none.isEmpty(), "noop");
    } catch (Throwable t) {
      failures++;
      Log.e(TAG, "Self test crashed", t);
    } finally {
      if (db != null) {
        try {
          db.close();
        } catch (Throwable ignored) { }
      }
    }
  }
}
