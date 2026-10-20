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

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.ghost.XgramGhost;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.AppContext;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.lambda.RunnableBool;
import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Undo send (iMessage, Gmail): what is sent from the message box (text, and the documents and music of its tray) waits
 * on this device for the chosen delay before the exact requests Telegram X built go to TDLib, so it can be taken back
 * while nothing has reached Telegram. The waiting message is shown by {@link XgramUndoSendBar} above the message box,
 * not in the message list, so the real bubble (the one a sticker or Telegram X's own sending makes) is the only one.
 *
 * <p>Waiting messages go out at their deadline, with "Send now", when their chat closes, when the app pauses, and
 * before anything else is sent to their chat ({@link #flushBefore}), oldest first. Each one is journalled
 * ({@link XgramUndoSendStore}); after a crash it becomes the chat's draft instead, so nothing is ever sent unasked and
 * nothing typed is lost. Off by default: until a message waits, every hook returns at once.
 */
public final class XgramUndoSend {
  private XgramUndoSend () { }

  public static final int NOT_HELD = 0, HELD = 1, BLOCKED_SLOW_MODE = 2;
  static final int MAX_PER_COMPOSER = 5;
  static final int MAX_ORPHANS_PER_LAUNCH = 20;
  /** A sent item without a result after this long leaves the bar; its journal record stays until the result. */
  private static final long WATCHDOG_MS = 3000L;
  /** Accessibility timeouts can lengthen the wait, up to this. */
  private static final long MAX_HOLD_MS = 120_000L;
  private static final long HINT_DELAY_MS = 350L;
  /** Files whose requests haven't come this long after their deadline go back (see onFilesTimeout). */
  private static final long PREPARE_TIMEOUT_MS = 30_000L;
  /** After a crash: a message counts as sent when it is this close to the flush (Telegram's time rounds to seconds). */
  private static final long SENT_MARGIN_SECONDS = 2L;

  /** Tells the journal records of this launch from those of an earlier one. */
  static final long BOOT_ID = newBootId();

  private static long newBootId () {
    long id = new Random().nextLong() ^ System.nanoTime();
    return id != 0 ? id : 1;
  }

  private static final Object LOCK = new Object();
  /** Waiting and sending items of every account, oldest first. */
  private static final ArrayList<XgramUndoSendItem> items = new ArrayList<>();
  /** Items that still wait: while there are none, the hooks in the send paths return at once. */
  private static volatile int heldCount;
  /**
   * What goes back into a message box once it can take it: a refused send or an Undo its box couldn't take right then,
   * and records of earlier launches that could not become drafts. Their journal records stay until then.
   */
  private static final ArrayList<Pending> pending = new ArrayList<>();
  private static final HashSet<Integer> restoredAccounts = new HashSet<>();
  /**
   * Per account slot, bumped when its account logs out: items and records of an older epoch are neither written to the
   * journal nor kept, so nothing of the old account reaches the next login in that slot.
   */
  private static final SparseIntArray epochs = new SparseIntArray();
  private static volatile boolean mayHaveHints = true;
  private static boolean stateListenerAdded;
  private static volatile boolean debugCrashAfterHold, debugCrashAfterSend;

  /** A strong reference: UI keeps its state listeners in a weak list. */
  private static final UI.StateListener STATE = newState -> {
    if (newState == UI.State.PAUSED || newState == UI.State.DESTROYED) {
      flushAll();
    }
  };

  /** What goes back into a message box once it can take it (see {@link #pending}). */
  private static final class Pending {
    final Tdlib tdlib;
    final XgramUndoSendStore.Record record;
    final int epoch;
    /** The box that sent it: only that one takes the files and the reply bar's message (memory only) back. */
    @Nullable final WeakReference<MessagesController> owner;
    @Nullable final ArrayList<InlineResult<?>> files;
    @Nullable final MessagesController.ReplyInfo reply;
    /** Not an Undo: "your message is back" shows when it goes back, and a draft is announced as a failed send. */
    final boolean notice;
    final boolean showKeyboard;

    Pending (Tdlib tdlib, XgramUndoSendStore.Record record, int epoch, @Nullable MessagesController owner,
             @Nullable ArrayList<InlineResult<?>> files, @Nullable MessagesController.ReplyInfo reply, boolean notice,
             boolean showKeyboard) {
      this.tdlib = tdlib;
      this.record = record;
      this.epoch = epoch;
      this.owner = owner != null ? new WeakReference<>(owner) : null;
      this.files = owner != null ? files : null;
      this.reply = owner != null ? reply : null;
      this.notice = notice;
      this.showKeyboard = showKeyboard;
    }

    @Nullable
    MessagesController owner () {
      return owner != null ? owner.get() : null;
    }

    /** For any message box of the chat: without what only the sending box can take back. */
    Pending withoutOwner () {
      return owner == null ? this : new Pending(tdlib, record, epoch, null, null, null, notice, showKeyboard);
    }
  }

  // Accounts that log out

  /** The epoch of the account slot (see {@link #epochs}). */
  static int epochOf (int accountId) {
    synchronized (LOCK) {
      return epochs.get(accountId, 0);
    }
  }

  /** Under LOCK. */
  private static boolean isCurrent (int accountId, int epoch) {
    return epochs.get(accountId, 0) == epoch;
  }

  // Journal writes: none once the item's account logged out (the erased records stay erased)

  private static boolean journalPut (XgramUndoSendItem item) {
    synchronized (LOCK) {
      return isCurrent(item.accountId, item.epoch) && XgramUndoSendStore.put(item, BOOT_ID);
    }
  }

  private static void journalFlushing (XgramUndoSendItem item, long flushDate, long lastMessageId) {
    synchronized (LOCK) {
      if (isCurrent(item.accountId, item.epoch)) {
        XgramUndoSendStore.markFlushing(item, BOOT_ID, flushDate, lastMessageId);
      }
    }
  }

  /** False when the item's account logged out: then nothing of it is kept. */
  private static boolean journalRewrite (XgramUndoSendItem item, XgramUndoSendStore.Record left) {
    synchronized (LOCK) {
      if (!isCurrent(item.accountId, item.epoch)) {
        return false;
      }
      XgramUndoSendStore.rewrite(left);
      return true;
    }
  }

  private static void journalHint (int accountId, int epoch, long chatId) {
    synchronized (LOCK) {
      if (isCurrent(accountId, epoch)) {
        XgramUndoSendStore.putHint(accountId, chatId);
      }
    }
  }

  // What the bar shows

  /** One item as the bar shows it. */
  static final class Shown {
    final long order;
    final int kind;
    final CharSequence preview;
    @Nullable final String replyName;
    final boolean silent;
    /** Parts of a long text; files sent together count as one. */
    final int parts;
    final int files;
    final boolean flushing;
    final long dueUptime, totalMs;

    Shown (XgramUndoSendItem item, boolean flushing) {
      this.order = item.order;
      this.kind = item.kind;
      this.preview = item.preview != null ? item.preview : "";
      this.replyName = item.replyName;
      this.silent = item.silent;
      this.parts = item.isFiles() ? 1 : item.parts.size();
      this.files = item.fileCount();
      this.flushing = flushing;
      this.dueUptime = item.dueUptime;
      this.totalMs = item.totalMs;
    }
  }

  /** The items of {@code owner}'s message box the bar shows, oldest first. */
  @UiThread
  static List<Shown> itemsOf (MessagesController owner) {
    List<XgramUndoSendItem> found = null;
    List<Boolean> flushing = null;
    synchronized (LOCK) {
      if (items.isEmpty()) {
        return Collections.emptyList();
      }
      long chatId = owner.getChatId();
      for (XgramUndoSendItem item : items) {
        if (!item.isOwnedBy(owner) || item.chatId != chatId) {
          continue;
        }
        if (item.state == XgramUndoSendItem.HELD || (item.state == XgramUndoSendItem.FLUSHING && !item.hidden)) {
          if (found == null) {
            found = new ArrayList<>();
            flushing = new ArrayList<>();
          }
          found.add(item);
          flushing.add(item.state == XgramUndoSendItem.FLUSHING);
        }
      }
    }
    if (found == null) {
      return Collections.emptyList();
    }
    List<Shown> result = new ArrayList<>(found.size());
    for (int i = 0; i < found.size(); i++) {
      XgramUndoSendItem item = found.get(i);
      preparePreview(item);
      result.add(new Shown(item, flushing.get(i)));
    }
    return result;
  }

  /** True when the bar has something to show for {@code owner}. */
  @UiThread
  public static boolean hasShownItems (MessagesController owner) {
    return !itemsOf(owner).isEmpty();
  }

  @UiThread
  private static void preparePreview (XgramUndoSendItem item) {
    if (item.previewReady) {
      return;
    }
    item.previewReady = true;
    try {
      if (item.isNote()) {
        item.preview = Lang.getString(item.kind == XgramUndoSendItem.KIND_VIDEO ? R.string.XgramUndoSendVideoPreview : R.string.XgramUndoSendVoicePreview, Strings.buildDuration(item.durationSeconds));
      } else {
        String text = item.typed.text != null ? item.typed.text : "";
        if (text.trim().isEmpty() && item.fileNames != null) {
          // Files without a caption: their names
          text = TextUtils.join(", ", item.fileNames);
        }
        // One line: the start of the text is all there is room for.
        int end = Math.min(text.length(), 300);
        if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
          end--;
        }
        text = text.substring(0, end).replace('\n', ' ').trim();
        item.preview = Emoji.instance().replaceEmoji(text);
      }
      if (item.reply != null && item.reply.message != null && item.reply.message.message != null) {
        item.replyName = item.tdlib.senderName(item.reply.message.message.senderId, true);
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot build the preview", t);
      if (item.preview == null) {
        item.preview = item.typed.text != null ? item.typed.text : "";
      }
    }
  }

  // Holding

  /**
   * Called by the message box with the requests a send of text built, right before it would send them. HELD: they wait
   * (the caller clears the message box as after a send); BLOCKED_SLOW_MODE: nothing is sent and the text stays;
   * NOT_HELD: the caller sends as always. Any problem before the message box is cleared gives NOT_HELD.
   */
  @UiThread
  public static int tryHold (MessagesController owner, List<TdApi.Function<?>> functions, TdApi.FormattedText typed,
                             @Nullable MessagesController.ReplyInfo reply, TdApi.MessageSendOptions options,
                             long draftChatId, @Nullable TdApi.MessageTopic draftTopic) {
    final XgramUndoSendItem item;
    final List<TdApi.Function<?>> parts;
    try {
      XgramSettings settings = XgramSettings.instance();
      int seconds = settings.undoSendSeconds();
      if (seconds <= 0 || !UI.inUiThread() || owner == null || !owner.xgramUndoSendEligible()) {
        return NOT_HELD;
      }
      if (UI.getUiState() != UI.State.RESUMED) {
        return NOT_HELD; // the app is in the background (its pause already sent what waited): nothing waits there
      }
      Tdlib tdlib = owner.tdlib();
      long chatId = owner.getChatId();
      if (settings.isUndoSendExcluded(tdlib.id(), chatId)) {
        return NOT_HELD;
      }
      if (options == null || options.schedulingState != null || functions == null || functions.isEmpty() || Td.isEmpty(typed)) {
        return NOT_HELD;
      }
      if (tdlib.shouldSendAsDice(typed)) {
        return NOT_HELD; // a dice emoji, also when "Send as emoji" sends it as text
      }
      parts = new ArrayList<>(functions.size());
      for (TdApi.Function<?> function : functions) {
        if (!(function instanceof TdApi.SendMessage)) {
          return NOT_HELD;
        }
        TdApi.SendMessage send = (TdApi.SendMessage) function;
        if (send.chatId != chatId || !(send.inputMessageContent instanceof TdApi.InputMessageText) || (send.options != null && send.options.schedulingState != null)) {
          return NOT_HELD;
        }
        parts.add(send);
      }
      if (inSlowModeAsMember(tdlib, chatId) && hasItemsIn(tdlib, chatId)) {
        return BLOCKED_SLOW_MODE;
      }
      item = new XgramUndoSendItem(tdlib, XgramUndoSendItem.KIND_TEXT, chatId, draftChatId, draftTopic, parts, copyOf(typed),
        reply, reply != null ? reply.toInputMessageReply() : null, options.disableNotification, 0, null, null, holdMillis(seconds), owner);
      noteLastSeen(item);
      if (!journalPut(item)) {
        return NOT_HELD;
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: sending right away", t);
      return NOT_HELD;
    }
    // Whether the send clears the chat's draft is decided when it goes (flushChat).
    for (TdApi.Function<?> part : parts) {
      ((TdApi.InputMessageText) ((TdApi.SendMessage) part).inputMessageContent).clearDraft = false;
    }
    commit(owner, item);
    if (BuildConfig.DEBUG && debugCrashAfterHold) {
      debugCrashAfterHold = false;
      UI.post(() -> {
        throw new IllegalStateException("XGram debug: crash after hold");
      });
    }
    return HELD;
  }

  /**
   * Documents and music from the tray above the message box: the item that waits, or null (the caller then sends as
   * always). Their requests are made on Telegram X's media thread and arrive with {@link #filesReady}; the wait counts
   * from the tap. {@code files} and {@code fileNames} are in the order the requests carry them.
   */
  @UiThread
  @Nullable
  public static Object beginFiles (MessagesController owner, @Nullable TdApi.MessageSendOptions options, @Nullable TdApi.FormattedText caption,
                                   @Nullable MessagesController.ReplyInfo reply, ArrayList<InlineResult<?>> files, String[] fileNames) {
    final XgramUndoSendItem item;
    try {
      XgramSettings settings = XgramSettings.instance();
      int seconds = settings.undoSendSeconds();
      if (seconds <= 0 || !UI.inUiThread() || owner == null || !owner.xgramUndoSendEligible() || files == null || files.isEmpty()) {
        return null;
      }
      if (UI.getUiState() != UI.State.RESUMED) {
        return null; // the app is in the background: nothing waits there
      }
      Tdlib tdlib = owner.tdlib();
      long chatId = owner.getChatId();
      if (settings.isUndoSendExcluded(tdlib.id(), chatId) || (options != null && options.schedulingState != null)) {
        return null;
      }
      if (inSlowModeAsMember(tdlib, chatId) && hasItemsIn(tdlib, chatId)) {
        return null;
      }
      TdApi.FormattedText text = caption != null ? copyOf(caption) : new TdApi.FormattedText("", new TdApi.TextEntity[0]);
      item = new XgramUndoSendItem(tdlib, XgramUndoSendItem.KIND_FILES, chatId, owner.getMessageThreadChatId(), owner.getMessageTopicId(),
        null, text, reply, reply != null ? reply.toInputMessageReply() : null, options != null && options.disableNotification, 0,
        new ArrayList<>(files), fileNames, holdMillis(seconds), owner);
      item.preparing = true;
      noteLastSeen(item);
      if (!journalPut(item)) {
        return null;
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: sending the files right away", t);
      return null;
    }
    commit(owner, item);
    final XgramUndoSendItem made = item;
    UI.post(() -> onFilesTimeout(made), item.totalMs + PREPARE_TIMEOUT_MS);
    return item;
  }

  /**
   * The requests for files that wait ({@link #beginFiles}), on the main thread; null when Telegram X refused them (it
   * showed why): the files go back to the tray. When their deadline (or a send that must not overtake them) came while
   * they were made, they go now.
   */
  @UiThread
  public static void filesReady (@Nullable Object token, @Nullable List<TdApi.Function<?>> functions) {
    if (!(token instanceof XgramUndoSendItem)) {
      return;
    }
    final XgramUndoSendItem item = (XgramUndoSendItem) token;
    final boolean refused = functions == null || functions.isEmpty();
    final boolean gone;
    long sendUpTo = 0;
    synchronized (LOCK) {
      if (!item.preparing) {
        return;
      }
      item.preparing = false;
      gone = item.state != XgramUndoSendItem.HELD; // taken back while it was made, or its account logged out
      if (gone) {
        sendUpTo = item.flushUpToOrder; // what waited behind it may go now
      } else if (refused) {
        item.state = XgramUndoSendItem.UNDONE;
        items.remove(item);
        heldCount = Math.max(0, heldCount - 1);
        sendUpTo = item.flushUpToOrder;
      } else {
        item.parts = new ArrayList<>(functions);
        if (item.flushUpToOrder != 0 || SystemClock.uptimeMillis() >= item.dueUptime) {
          sendUpTo = Math.max(item.flushUpToOrder, item.order);
        }
      }
    }
    if (!gone && refused) {
      if (item.dueTask != null) {
        UI.cancel(item.dueTask);
      }
      MessagesController owner = item.owner();
      giveBack(owner, item, item.typed, item.files, item.fileNames, item.reply, item.replyTo, null, false);
      if (owner != null) {
        notifyOwner(owner, false);
      }
    }
    if (sendUpTo != 0) {
      flushChat(item.tdlib, item.chatId, sendUpTo, true);
    }
  }

  /**
   * Files whose requests never came (Telegram X dropped its callback, e.g. its screen closed during a refusal): they go
   * back like refused ones, so they can't hold up what waits behind them for good.
   */
  @UiThread
  private static void onFilesTimeout (XgramUndoSendItem item) {
    final long sendUpTo;
    final boolean held;
    synchronized (LOCK) {
      if (!item.preparing) {
        return;
      }
      item.preparing = false;
      sendUpTo = item.flushUpToOrder;
      held = item.state == XgramUndoSendItem.HELD;
      if (held) {
        item.state = XgramUndoSendItem.UNDONE;
        items.remove(item);
        heldCount = Math.max(0, heldCount - 1);
      }
    }
    if (held) {
      if (item.dueTask != null) {
        UI.cancel(item.dueTask);
      }
      MessagesController owner = item.owner();
      giveBack(owner, item, item.typed, item.files, item.fileNames, item.reply, item.replyTo, null, false);
      if (owner != null) {
        notifyOwner(owner, false);
      }
    }
    if (sendUpTo != 0) {
      flushChat(item.tdlib, item.chatId, sendUpTo, true);
    }
  }

  /** Voice and video messages may wait (Messages > Delay voice and video messages). */
  @AnyThread
  public static boolean mayHoldNotes () {
    XgramSettings settings = XgramSettings.instance();
    return settings.undoSendSeconds() > 0 && settings.undoSendVoice();
  }

  /**
   * A recording from the recorder has to go through {@link #tryHoldNote}: it may wait, or something waits that it must
   * not overtake in a slow mode chat. Otherwise it is sent as always.
   */
  @AnyThread
  public static boolean interceptsNotes () {
    if (mayHoldNotes()) {
      return true;
    }
    synchronized (LOCK) {
      return !items.isEmpty();
    }
  }

  /**
   * A voice or video message from the recorder. HELD: it waits; BLOCKED_SLOW_MODE: a message waits in this slow mode
   * chat, so it would only be refused, and the hint says why (nothing is sent); NOT_HELD: the caller sends it as
   * always. Only recordings TDLib already has as a file wait.
   */
  @UiThread
  public static int tryHoldNote (MessagesController owner, TdApi.SendMessage function, @Nullable MessagesController.ReplyInfo reply, int durationSeconds) {
    final XgramUndoSendItem item;
    try {
      if (!UI.inUiThread() || owner == null || function == null) {
        return NOT_HELD;
      }
      Tdlib tdlib = owner.tdlib();
      boolean scheduled = function.options != null && function.options.schedulingState != null;
      if (!scheduled && blocksSlowMode(tdlib, function.chatId)) {
        // Also with the voice switch off: Telegram's slow mode timer only starts once what waits is sent.
        if (!owner.xgramShowUndoSendHint(Lang.getString(R.string.XgramUndoSendSlowMode), true)) {
          UI.showToast(R.string.XgramUndoSendSlowMode, Toast.LENGTH_SHORT);
        }
        return BLOCKED_SLOW_MODE;
      }
      if (!mayHoldNotes() || !owner.xgramUndoSendEligible() || UI.getUiState() != UI.State.RESUMED) {
        return NOT_HELD; // (in the background nothing waits)
      }
      long chatId = owner.getChatId();
      if (function.chatId != chatId || XgramSettings.instance().isUndoSendExcluded(tdlib.id(), chatId)) {
        return NOT_HELD;
      }
      if (function.options == null || scheduled) {
        return NOT_HELD;
      }
      int kind;
      TdApi.InputMessageContent content = function.inputMessageContent;
      if (content instanceof TdApi.InputMessageVoiceNote) {
        TdApi.InputVoiceNote voiceNote = ((TdApi.InputMessageVoiceNote) content).voiceNote;
        if (voiceNote == null || !(voiceNote.voiceNote instanceof TdApi.InputFileId)) {
          return NOT_HELD;
        }
        kind = XgramUndoSendItem.KIND_VOICE;
      } else if (content instanceof TdApi.InputMessageVideoNote) {
        TdApi.InputVideoNote videoNote = ((TdApi.InputMessageVideoNote) content).videoNote;
        if (videoNote == null || !(videoNote.videoNote instanceof TdApi.InputFileId)) {
          return NOT_HELD;
        }
        kind = XgramUndoSendItem.KIND_VIDEO;
      } else {
        return NOT_HELD;
      }
      List<TdApi.Function<?>> parts = new ArrayList<>(1);
      parts.add(function);
      item = new XgramUndoSendItem(tdlib, kind, chatId, owner.getMessageThreadChatId(), owner.getMessageTopicId(), parts,
        new TdApi.FormattedText("", new TdApi.TextEntity[0]), reply, function.replyTo, function.options.disableNotification,
        Math.max(0, durationSeconds), null, null, holdMillis(XgramSettings.instance().undoSendSeconds()), owner);
      noteLastSeen(item);
      if (!journalPut(item)) {
        return NOT_HELD;
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: sending the recording right away", t);
      return NOT_HELD;
    }
    commit(owner, item);
    return HELD;
  }

  @UiThread
  private static void commit (MessagesController owner, XgramUndoSendItem item) {
    // At most MAX_PER_COMPOSER wait per message box: the oldest goes now.
    XgramUndoSendItem oldest = null;
    int count = 0;
    synchronized (LOCK) {
      for (XgramUndoSendItem it : items) {
        if (it.state == XgramUndoSendItem.HELD && it.isOwnedBy(owner)) {
          if (oldest == null) {
            oldest = it;
          }
          count++;
        }
      }
    }
    if (count >= MAX_PER_COMPOSER && oldest != null) {
      flushChat(oldest.tdlib, oldest.chatId, oldest.order, true);
    }
    synchronized (LOCK) {
      items.add(item);
      heldCount++;
    }
    final XgramUndoSendItem due = item;
    item.dueTask = () -> onDue(due);
    UI.post(item.dueTask, item.totalMs);
    if (!stateListenerAdded) {
      stateListenerAdded = true;
      UI.addStateListener(STATE);
    }
    notifyOwner(owner, false);
  }

  /** The chosen delay, or longer when accessibility asks for more time to act (Android 10+). */
  private static long holdMillis (int seconds) {
    long ms = seconds * 1000L;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        AccessibilityManager manager = (AccessibilityManager) AppContext.get().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
          int recommended = manager.getRecommendedTimeoutMillis((int) ms, AccessibilityManager.FLAG_CONTENT_CONTROLS | AccessibilityManager.FLAG_CONTENT_TEXT);
          ms = Math.max(ms, Math.min(MAX_HOLD_MS, recommended));
        }
      } catch (Throwable ignored) { }
    }
    return ms;
  }

  private static TdApi.FormattedText copyOf (TdApi.FormattedText text) {
    TdApi.TextEntity[] entities = text.entities != null ? new TdApi.TextEntity[text.entities.length] : new TdApi.TextEntity[0];
    for (int i = 0; i < entities.length; i++) {
      TdApi.TextEntity entity = text.entities[i];
      entities[i] = new TdApi.TextEntity(entity.offset, entity.length, entity.type);
    }
    return new TdApi.FormattedText(text.text != null ? text.text : "", entities);
  }

  /** A slow mode supergroup where the user isn't an admin: one message at a time. */
  private static boolean inSlowModeAsMember (Tdlib tdlib, long chatId) {
    if (!ChatId.isSupergroup(chatId)) {
      return false;
    }
    TdApi.SupergroupFullInfo full = tdlib.cache().supergroupFull(ChatId.toSupergroupId(chatId), false);
    if (full == null || full.slowModeDelay <= 0) {
      return false;
    }
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    return status == null || !TD.isAdmin(status);
  }

  private static boolean hasItemsIn (Tdlib tdlib, long chatId) {
    synchronized (LOCK) {
      for (XgramUndoSendItem item : items) {
        if (item.tdlib == tdlib && item.chatId == chatId && (item.state == XgramUndoSendItem.HELD || item.state == XgramUndoSendItem.FLUSHING)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * A slow mode chat where the user isn't an admin and a message waits (or is on its way): nothing else can be sent
   * there now, though Telegram's own timer only starts once that message is sent. Telegram X's slow mode checks ask
   * this through {@link Tdlib#getSlowModeRestrictionText}, so every way of sending refuses with the hint instead of
   * sending a second message the server would refuse.
   */
  @AnyThread
  public static boolean blocksSlowMode (@Nullable Tdlib tdlib, long chatId) {
    if (tdlib == null || !ChatId.isSupergroup(chatId)) {
      return false;
    }
    synchronized (LOCK) {
      if (items.isEmpty()) {
        return false;
      }
    }
    return hasItemsIn(tdlib, chatId) && inSlowModeAsMember(tdlib, chatId);
  }

  // Ghost mode "read on send"

  /** Where a message of the item is read on send: its chat, or the topic its requests go to. */
  @Nullable
  private static TdApi.MessageTopic readTopicOf (XgramUndoSendItem item) {
    List<TdApi.Function<?>> parts = item.parts;
    if (!parts.isEmpty() && parts.get(0) instanceof TdApi.SendMessage) {
      return ((TdApi.SendMessage) parts.get(0)).topicId;
    }
    return item.draftTopic;
  }

  /** Main thread, while the item's message box is in sight: the newest message there is what was seen. */
  private static void noteLastSeen (XgramUndoSendItem item) {
    try {
      if (XgramGhost.readsOnSend(item.tdlib, item.chatId)) {
        long id = XgramGhost.lastMessageIdOf(item.tdlib, item.chatId, readTopicOf(item));
        if (id != 0) {
          item.readCap = id;
        }
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot read the last message", t);
    }
  }

  /** The read cap for a send that goes now: none while its message box is in sight (it reads what is shown). */
  private static long readCapFor (XgramUndoSendItem item, boolean mainThread) {
    final long cap = item.readCap;
    if (cap == 0 || !XgramGhost.readsOnSend(item.tdlib, item.chatId)) {
      return 0;
    }
    if (mainThread) {
      MessagesController owner = item.owner();
      if (owner != null && owner.isFocused()) {
        return 0;
      }
    }
    return cap;
  }

  /** The pending messages a send made read only up to {@code capId} once they are sent (XgramGhost's read on send). */
  private static void capReads (Tdlib tdlib, TdApi.Object result, long capId) {
    if (result instanceof TdApi.Message) {
      TdApi.Message message = (TdApi.Message) result;
      if (message.schedulingState == null) {
        XgramGhost.capReadOnSend(tdlib, message.chatId, message.id, capId);
      }
    } else if (result instanceof TdApi.Messages && ((TdApi.Messages) result).messages != null) {
      for (TdApi.Message message : ((TdApi.Messages) result).messages) {
        if (message != null && message.schedulingState == null) {
          XgramGhost.capReadOnSend(tdlib, message.chatId, message.id, capId);
        }
      }
    }
  }

  /**
   * The message box goes out of sight (a screen over it, the chat closing): what a send reads from now on stops at what
   * it showed.
   */
  @UiThread
  public static void onComposerHidden (MessagesController owner) {
    if (owner == null || heldCount == 0) {
      return;
    }
    List<XgramUndoSendItem> owned = null;
    synchronized (LOCK) {
      for (XgramUndoSendItem item : items) {
        if (item.state == XgramUndoSendItem.HELD && item.isOwnedBy(owner)) {
          if (owned == null) {
            owned = new ArrayList<>();
          }
          owned.add(item);
        }
      }
    }
    if (owned != null) {
      for (XgramUndoSendItem item : owned) {
        noteLastSeen(item);
      }
    }
  }

  /** A slow mode chat where a message already waits: Send shows a hint instead of sending (or waiting) another one. */
  @UiThread
  public static boolean blockedBySlowMode (MessagesController owner) {
    if (owner == null || !owner.xgramUndoSendEligible() || XgramSettings.instance().undoSendSeconds() <= 0) {
      return false;
    }
    return inSlowModeAsMember(owner.tdlib(), owner.getChatId()) && hasItemsIn(owner.tdlib(), owner.getChatId());
  }

  /** The long-press menu of Send offers "Send now": what the message box holds would wait. */
  @UiThread
  public static boolean wouldHold (MessagesController owner) {
    if (owner == null || !owner.xgramUndoSendEligible()) {
      return false;
    }
    XgramSettings settings = XgramSettings.instance();
    if (settings.undoSendSeconds() <= 0 || settings.isUndoSendExcluded(owner.tdlib().id(), owner.getChatId())) {
      return false;
    }
    if (inSlowModeAsMember(owner.tdlib(), owner.getChatId()) && hasItemsIn(owner.tdlib(), owner.getChatId())) {
      return false; // Send shows the slow mode hint instead: a second message would only be refused
    }
    return owner.xgramComposerWouldWait();
  }

  /** The name of a delay in the settings: Off, or "5 seconds". */
  public static String delayName (int seconds) {
    return seconds <= 0 ? Lang.getString(R.string.Off) : Lang.plural(R.string.xSeconds, seconds);
  }

  /** "1 file", "3 files". */
  static String filesName (int count) {
    return Lang.getString(count == 1 ? R.string.XgramUndoSendFile : R.string.XgramUndoSendFiles, count);
  }

  /** The chat menu offers "Send right away in this chat" (or the reverse) while Undo send is on. */
  @UiThread
  public static boolean canOfferExclude (MessagesController owner) {
    return owner != null && owner.xgramUndoSendEligible() && XgramSettings.instance().undoSendSeconds() > 0;
  }

  @UiThread
  public static boolean isExcluded (MessagesController owner) {
    return owner != null && owner.getChat() != null && XgramSettings.instance().isUndoSendExcluded(owner.tdlib().id(), owner.getChatId());
  }

  /** "Send right away in this chat": what waits there goes now. */
  @UiThread
  public static void setExcluded (MessagesController owner, boolean excluded) {
    if (owner == null || owner.getChat() == null) {
      return;
    }
    XgramSettings.instance().setUndoSendExcluded(owner.tdlib().id(), owner.getChatId(), excluded);
    if (excluded) {
      flushBefore(owner.tdlib(), owner.getChatId());
    }
  }

  // Sending

  @UiThread
  private static void onDue (XgramUndoSendItem item) {
    boolean held;
    synchronized (LOCK) {
      held = item.state == XgramUndoSendItem.HELD;
    }
    if (!held) {
      return;
    }
    flushChat(item.tdlib, item.chatId, item.order, true);
    synchronized (LOCK) {
      held = item.state == XgramUndoSendItem.HELD;
    }
    if (held) {
      // Files ahead of it (or itself) are still being made: it goes once they are; the bar shows it is due.
      MessagesController owner = item.owner();
      if (owner != null) {
        notifyOwner(owner, false);
      }
    }
  }

  /**
   * Sends what waits in the chat up to {@code maxOrder}, oldest first, back to back, before the caller sends anything
   * else. Files whose requests aren't made yet stop the queue: they and what came after them go once they are, so the
   * order holds. No lock is held while TDLib is called.
   */
  @AnyThread
  private static void flushChat (Tdlib tdlib, long chatId, long maxOrder, boolean notify) {
    List<XgramUndoSendItem> batch = null;
    synchronized (LOCK) {
      if (maxOrder == Long.MAX_VALUE) {
        // Everything that waits now, not what is sent after this (a queue held up by files must not take it along).
        long newest = 0;
        for (XgramUndoSendItem item : items) {
          if (item.state == XgramUndoSendItem.HELD && item.tdlib == tdlib && item.chatId == chatId) {
            newest = Math.max(newest, item.order);
          }
        }
        maxOrder = newest;
      }
      for (XgramUndoSendItem item : items) {
        if (item.state != XgramUndoSendItem.HELD || item.tdlib != tdlib || item.chatId != chatId || item.order > maxOrder) {
          continue;
        }
        if (item.preparing) {
          item.flushUpToOrder = Math.max(item.flushUpToOrder, maxOrder);
          break;
        }
        item.state = XgramUndoSendItem.FLUSHING;
        if (batch == null) {
          batch = new ArrayList<>();
        }
        batch.add(item);
      }
      if (batch != null) {
        heldCount = Math.max(0, heldCount - batch.size());
      }
    }
    if (batch == null) {
      return;
    }
    final boolean mainThread = UI.inUiThread();
    for (XgramUndoSendItem item : batch) {
      if (item.dueTask != null) {
        UI.cancel(item.dueTask);
      }
      // After a crash, the chat's history is searched from here for the message.
      journalFlushing(item, item.tdlib.currentTime(TimeUnit.SECONDS), lastMessageIdOf(item.tdlib, item.chatId));
      // Ghost "read on send": sent while its message box is out of sight, it reads only what that box showed.
      item.flushReadCap = readCapFor(item, mainThread);
      // As a send from the message box does: the draft is cleared, unless the message box holds something new.
      boolean clearDraft = mainThread && item.kind == XgramUndoSendItem.KIND_TEXT && composerEmptyFor(item);
      for (int i = 0; i < item.parts.size(); i++) {
        TdApi.Function<?> part = item.parts.get(i);
        if (part instanceof TdApi.SendMessage && ((TdApi.SendMessage) part).inputMessageContent instanceof TdApi.InputMessageText) {
          ((TdApi.InputMessageText) ((TdApi.SendMessage) part).inputMessageContent).clearDraft = clearDraft && i == 0;
        }
      }
    }
    for (XgramUndoSendItem item : batch) {
      Client client = item.tdlib.client();
      final long readCap = item.flushReadCap;
      for (int i = 0; i < item.parts.size(); i++) {
        TdApi.Function<?> part = item.parts.get(i);
        if (readCap != 0) {
          XgramGhost.capReadOnSend(part, readCap); // ghost "send as scheduled" reads while the request is made
        }
        client.send(part, new PartHandler(item, i));
        if (readCap != 0) {
          XgramGhost.capReadOnSend(part, 0);
        }
      }
    }
    if (BuildConfig.DEBUG && debugCrashAfterSend) {
      debugCrashAfterSend = false;
      UI.post(() -> {
        throw new IllegalStateException("XGram debug: crash after send");
      });
    }
    final List<XgramUndoSendItem> flushed = batch;
    UI.execute(() -> {
      for (XgramUndoSendItem item : flushed) {
        UI.post(() -> onWatchdog(item), WATCHDOG_MS);
      }
      if (notify) {
        notifyOwners(flushed, false);
      }
    });
  }

  private static long lastMessageIdOf (Tdlib tdlib, long chatId) {
    TdApi.Chat chat = tdlib.chat(chatId);
    TdApi.Message last = chat != null ? chat.lastMessage : null;
    return last != null ? last.id : 0;
  }

  /** Main thread only (flushChat checks). */
  private static boolean composerEmptyFor (XgramUndoSendItem item) {
    MessagesController owner = item.owner();
    return owner != null && owner.xgramComposerEmptyFor(item.chatId, item.draftChatId, item.draftTopic);
  }

  /** Sends what waits in {@code chatId} first: anything else sent there must not overtake it. */
  @AnyThread
  public static void flushBefore (@Nullable Tdlib tdlib, long chatId) {
    if (heldCount == 0 || tdlib == null || chatId == 0) {
      return;
    }
    flushChat(tdlib, chatId, Long.MAX_VALUE, true);
  }

  /** {@link #flushBefore(Tdlib, long)} for the chat a send request goes to. */
  @AnyThread
  public static void flushBefore (@Nullable Tdlib tdlib, @Nullable TdApi.Function<?> function) {
    if (heldCount == 0 || tdlib == null || function == null) {
      return;
    }
    final long chatId;
    switch (function.getConstructor()) {
      case TdApi.SendMessage.CONSTRUCTOR:
        chatId = ((TdApi.SendMessage) function).chatId;
        break;
      case TdApi.SendMessageAlbum.CONSTRUCTOR:
        chatId = ((TdApi.SendMessageAlbum) function).chatId;
        break;
      case TdApi.SendInlineQueryResultMessage.CONSTRUCTOR:
        chatId = ((TdApi.SendInlineQueryResultMessage) function).chatId;
        break;
      case TdApi.ForwardMessages.CONSTRUCTOR:
        chatId = ((TdApi.ForwardMessages) function).chatId;
        break;
      case TdApi.ResendMessages.CONSTRUCTOR:
        chatId = ((TdApi.ResendMessages) function).chatId;
        break;
      default:
        return;
    }
    flushBefore(tdlib, chatId);
  }

  /** Everything that waits, in every account: the app went to the background. */
  @AnyThread
  public static void flushAll () {
    if (heldCount == 0) {
      return;
    }
    List<XgramUndoSendItem> chats = new ArrayList<>();
    synchronized (LOCK) {
      for (int i = items.size() - 1; i >= 0; i--) {
        XgramUndoSendItem item = items.get(i);
        if (item.state != XgramUndoSendItem.HELD) {
          continue;
        }
        boolean seen = false;
        for (XgramUndoSendItem chat : chats) {
          if (chat.tdlib == item.tdlib && chat.chatId == item.chatId) {
            seen = true;
            break;
          }
        }
        if (!seen) {
          chats.add(item); // the newest of its chat
        }
      }
    }
    for (XgramUndoSendItem newest : chats) {
      flushChat(newest.tdlib, newest.chatId, newest.order, true);
    }
  }

  /** "Send now": everything that waits in {@code owner}'s message box goes now, oldest first. */
  @UiThread
  public static void sendNow (MessagesController owner) {
    XgramUndoSendItem newest = newestHeld(owner);
    if (newest != null) {
      flushChat(newest.tdlib, newest.chatId, newest.order, true);
    }
  }

  @Nullable
  private static XgramUndoSendItem newestHeld (MessagesController owner) {
    if (owner == null) {
      return null;
    }
    synchronized (LOCK) {
      for (int i = items.size() - 1; i >= 0; i--) {
        XgramUndoSendItem item = items.get(i);
        if (item.state == XgramUndoSendItem.HELD && item.isOwnedBy(owner)) {
          return item;
        }
      }
    }
    return null;
  }

  /** TDLib's answer to one part: the message goes where Telegram X's own sends go, so its bubble is the only one. */
  private static final class PartHandler implements Client.ResultHandler {
    private final XgramUndoSendItem item;
    private final int index;

    PartHandler (XgramUndoSendItem item, int index) {
      this.item = item;
      this.index = index;
    }

    @Override
    public void onResult (TdApi.Object result) {
      if (result instanceof TdApi.Message || result instanceof TdApi.Messages) {
        final long readCap = item.flushReadCap;
        if (readCap != 0) {
          capReads(item.tdlib, result, readCap); // before its "sent" update, which comes after this result
        }
        item.tdlib.messageHandler().onResult(result);
        if (isScheduled(result)) {
          synchronized (LOCK) {
            item.ghostScheduled = true; // Ghost mode "send as scheduled"
          }
        }
      } else {
        // Never through messageHandler: it would show the error a second time.
        TdApi.Error error = result instanceof TdApi.Error ? (TdApi.Error) result : new TdApi.Error(500, "Unexpected result: " + result.getClass().getSimpleName());
        synchronized (LOCK) {
          if (item.failed == null) {
            item.failed = new boolean[item.parts.size()];
          }
          item.failed[index] = true;
          if (item.error == null) {
            item.error = error;
          }
        }
      }
      item.tdlib.ui().post(() -> onPartSettled(item));
    }

    private static boolean isScheduled (TdApi.Object result) {
      if (result instanceof TdApi.Message) {
        return ((TdApi.Message) result).schedulingState != null;
      }
      TdApi.Message[] messages = ((TdApi.Messages) result).messages;
      if (messages != null) {
        for (TdApi.Message message : messages) {
          if (message != null && message.schedulingState != null) {
            return true;
          }
        }
      }
      return false;
    }
  }

  @UiThread
  private static void onPartSettled (XgramUndoSendItem item) {
    item.settled++;
    if (item.settled == item.parts.size()) {
      settle(item);
    }
  }

  /** Every part has a result. Posted after the bubbles were added, so the bar leaves in the frame they come in. */
  @UiThread
  private static void settle (XgramUndoSendItem item) {
    final boolean[] failed;
    final boolean ghostScheduled;
    final TdApi.Error error;
    synchronized (LOCK) {
      if (!items.remove(item)) {
        return; // its account logged out meanwhile (forgetAccount): nothing of it is given back or kept
      }
      failed = item.failed;
      ghostScheduled = item.ghostScheduled;
      error = item.error;
      item.state = failed != null ? XgramUndoSendItem.FAILED : XgramUndoSendItem.DONE;
    }
    MessagesController owner = item.owner();
    if (failed == null) {
      XgramUndoSendStore.remove(item);
      if (owner != null) {
        notifyOwner(owner, true);
        if (ghostScheduled) {
          owner.xgramShowUndoSendHint(Lang.getString(R.string.XgramGhostScheduledHint), false);
        }
      }
      return;
    }
    if (owner != null) {
      notifyOwner(owner, false);
    }
    if (item.isNote()) {
      // A recording can't go back into the message box: the error is all there is to show, as for any send.
      XgramUndoSendStore.remove(item);
      if (owner == null || !owner.xgramShowUndoSendHint(TD.toErrorString(error), true)) {
        UI.showError(error);
      }
      return;
    }
    // What TDLib refused goes back: its text (or caption) and files, with the reply when nothing was sent.
    final boolean firstFailed = failed[0];
    final MessagesController.ReplyInfo reply = firstFailed ? item.reply : null;
    final TdApi.InputMessageReplyTo replyTo = firstFailed ? item.replyTo : null;
    final TdApi.FormattedText text;
    ArrayList<InlineResult<?>> files = null;
    String[] fileNames = null;
    if (item.isFiles()) {
      // The caption is on the last file; part k carries the files after those of the parts before it.
      text = failed[failed.length - 1] ? item.typed : new TdApi.FormattedText("", new TdApi.TextEntity[0]);
      files = new ArrayList<>();
      List<String> names = new ArrayList<>();
      int start = 0;
      for (int i = 0; i < item.parts.size(); i++) {
        int count = XgramUndoSendItem.contentCount(item.parts.get(i));
        if (failed[i]) {
          for (int k = start; k < start + count; k++) {
            if (item.files != null && k < item.files.size()) {
              files.add(item.files.get(k));
            }
            if (item.fileNames != null && k < item.fileNames.length) {
              names.add(item.fileNames[k]);
            }
          }
        }
        start += count;
      }
      fileNames = names.toArray(new String[0]);
    } else {
      text = failedText(item, failed);
    }
    giveBack(owner, item, text, files, fileNames, reply, replyTo, error, true);
  }

  /**
   * Puts {@code text} (and the files) back into the owner's message box when it can take them now. When that box
   * shows the chat but can't right now (out of sight, the app paused, an edit, a send in progress), it goes back there
   * once it can; when the box is gone, the text becomes the chat's draft (the files can't be kept: their names are
   * listed). The journal holds what is given back until it is somewhere. {@code error}: why it wasn't sent, shown too.
   */
  @UiThread
  private static void giveBack (@Nullable MessagesController owner, XgramUndoSendItem item, TdApi.FormattedText text,
                                @Nullable ArrayList<InlineResult<?>> files, @Nullable String[] fileNames,
                                @Nullable MessagesController.ReplyInfo reply, @Nullable TdApi.InputMessageReplyTo replyTo,
                                @Nullable TdApi.Error error, boolean focus) {
    final XgramUndoSendStore.Record left = new XgramUndoSendStore.Record(XgramUndoSendStore.key(item), item.accountId, BOOT_ID, false, item.kind,
      item.chatId, item.draftChatId, item.createdWall, item.draftTopic, replyTo, item.silent, fileNames, text);
    // Until it is back somewhere, the journal holds what is given back (and no longer the parts that were sent).
    if (!journalRewrite(item, left)) {
      return; // its account logged out: nothing of it is kept
    }
    if (owner != null && owner.xgramRestoreUnsent(item.chatId, item.draftChatId, item.draftTopic, text, reply, replyTo, focus, files)) {
      XgramUndoSendStore.remove(item);
      if (error != null) {
        showError(owner, error);
      }
      return;
    }
    // An Undo tap (a refusal has an error, files Telegram X refused have no focus) needs no "your message is back".
    final boolean undo = error == null && focus;
    final Pending later = new Pending(item.tdlib, left, item.epoch, owner, files, reply, !undo, focus);
    if (owner != null && owner.xgramShowsChat(item.chatId, item.draftChatId, item.draftTopic)) {
      // A cloud draft set now would not reach that box (it keeps what it holds) and its next save would overwrite it.
      keepPending(later);
      if (error != null) {
        showError(owner, error);
      }
      return;
    }
    toDraft(later, error);
  }

  /** The error in the chat when it is in front, a toast otherwise. */
  private static void showError (@Nullable MessagesController owner, TdApi.Error error) {
    if (owner == null || !owner.xgramShowUndoSendHint(TD.toErrorString(error), true)) {
      UI.showError(error);
    }
  }

  /** No message box can take it now: the text becomes the chat's draft; files without a caption are only listed. */
  private static void toDraft (Pending p, @Nullable TdApi.Error error) {
    final XgramUndoSendStore.Record record = p.record;
    if (Td.isEmpty(record.text)) {
      // Files without a caption: nothing to keep.
      XgramUndoSendStore.remove(record.key);
      if (error != null) {
        UI.showError(error);
      }
      showFilesNotSent(record.fileNames);
      return;
    }
    final Tdlib tdlib = p.tdlib;
    saveAsDraft(tdlib, record.draftChatId, record.topic, record.text, record.replyTo, ok -> {
      if (ok) {
        XgramUndoSendStore.remove(record.key);
        // A refused send says so; an Undo whose box closed before it could take the text back doesn't.
        UI.showToast(Lang.getString(p.notice ? R.string.XgramUndoSendFailedDraft : R.string.XgramUndoSendSavedDraft, tdlib.chatTitle(record.chatId)), Toast.LENGTH_LONG);
        showFilesNotSent(record.fileNames);
      } else {
        // Kept: a message box of that chat takes it back when it opens (or the next launch makes it a draft).
        keepPending(p.withoutOwner());
        if (error != null) {
          UI.showError(error);
        }
      }
    });
  }

  private static void showFilesNotSent (@Nullable String[] fileNames) {
    if (fileNames != null && fileNames.length > 0) {
      UI.showToast(Lang.getString(R.string.XgramUndoSendFilesNotSent, TextUtils.join(", ", fileNames)), Toast.LENGTH_LONG);
    }
  }

  /** The text of the parts TDLib refused: the whole text when none was sent. */
  private static TdApi.FormattedText failedText (XgramUndoSendItem item, boolean[] failed) {
    boolean all = true;
    for (boolean f : failed) {
      all &= f;
    }
    if (all) {
      return item.typed;
    }
    TdApi.FormattedText result = null;
    for (int i = 0; i < failed.length; i++) {
      if (!failed[i]) {
        continue;
      }
      TdApi.Function<?> part = item.parts.get(i);
      if (part instanceof TdApi.SendMessage && ((TdApi.SendMessage) part).inputMessageContent instanceof TdApi.InputMessageText) {
        TdApi.FormattedText text = ((TdApi.InputMessageText) ((TdApi.SendMessage) part).inputMessageContent).text;
        result = result == null ? text : Td.concat(result, text);
      }
    }
    return result != null ? result : item.typed;
  }

  @UiThread
  private static void onWatchdog (XgramUndoSendItem item) {
    synchronized (LOCK) {
      if (item.state != XgramUndoSendItem.FLUSHING) {
        return;
      }
      item.hidden = true;
    }
    MessagesController owner = item.owner();
    if (owner != null) {
      notifyOwner(owner, false);
    }
  }

  private static void notifyOwners (List<XgramUndoSendItem> list, boolean handoff) {
    MessagesController last = null;
    for (XgramUndoSendItem item : list) {
      MessagesController owner = item.owner();
      if (owner != null && owner != last) {
        notifyOwner(owner, handoff);
        last = owner;
      }
    }
  }

  private static void notifyOwner (MessagesController owner, boolean handoff) {
    try {
      owner.xgramOnUndoSendChanged(handoff);
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: cannot update the bar", t);
    }
  }

  // Undo

  /** Undo: the newest waiting message of {@code owner}'s message box goes back into it. False when there was none. */
  @UiThread
  public static boolean undoNewest (MessagesController owner) {
    if (owner == null || owner.isEditingMessage()) {
      return false;
    }
    XgramUndoSendItem item = newestHeld(owner);
    return item != null && undo(owner, item);
  }

  /** Undo of the item the bar showed (a recording, after the user confirmed to discard it). */
  @UiThread
  public static boolean undo (MessagesController owner, long order) {
    if (owner == null) {
      return false;
    }
    XgramUndoSendItem found = null;
    synchronized (LOCK) {
      for (XgramUndoSendItem item : items) {
        if (item.order == order && item.state == XgramUndoSendItem.HELD && item.isOwnedBy(owner)) {
          found = item;
          break;
        }
      }
    }
    return found != null && undo(owner, found);
  }

  @UiThread
  private static boolean undo (MessagesController owner, XgramUndoSendItem item) {
    final long blockedUpTo;
    synchronized (LOCK) {
      if (item.state != XgramUndoSendItem.HELD) {
        return false; // the deadline won: it is on its way
      }
      item.state = XgramUndoSendItem.UNDONE; // files still being made are dropped when they are ready
      items.remove(item);
      heldCount = Math.max(0, heldCount - 1);
      blockedUpTo = item.preparing ? item.flushUpToOrder : 0;
    }
    if (item.dueTask != null) {
      UI.cancel(item.dueTask);
    }
    // Nothing of it ever reached TDLib.
    if (item.isNote()) {
      // Discarded (the user confirmed); only the reply goes back, when the message box has none.
      owner.xgramRestoreUnsent(item.chatId, item.draftChatId, item.draftTopic, null, item.reply, item.replyTo, false, null);
      XgramUndoSendStore.remove(item);
    } else {
      giveBack(owner, item, item.typed, item.files, item.fileNames, item.reply, item.replyTo, null, true);
    }
    notifyOwner(owner, false);
    if (blockedUpTo != 0) {
      flushChat(item.tdlib, item.chatId, blockedUpTo, true); // what was asked to go while the files were made
    }
    return true;
  }

  // The message box

  /** The message box closes (back, another chat in its place, a reused screen, an account switch): what waits goes now. */
  @UiThread
  public static void onOwnerDestroyed (MessagesController owner) {
    if (owner == null) {
      return;
    }
    XgramUndoSendItem newest = null;
    boolean any = false;
    List<Pending> left = null;
    synchronized (LOCK) {
      if (items.isEmpty() && pending.isEmpty()) {
        return;
      }
      for (XgramUndoSendItem item : items) {
        if (item.isOwnedBy(owner)) {
          any = true;
          if (item.state == XgramUndoSendItem.HELD) {
            newest = item;
          }
        }
      }
      for (int i = pending.size() - 1; i >= 0; i--) {
        Pending p = pending.get(i);
        if (p.owner() == owner) {
          pending.remove(i);
          if (left == null) {
            left = new ArrayList<>();
          }
          left.add(0, p);
        }
      }
    }
    if (newest != null) {
      flushChat(newest.tdlib, newest.chatId, newest.order, false);
    }
    if (any) {
      synchronized (LOCK) {
        for (XgramUndoSendItem item : items) {
          if (item.isOwnedBy(owner)) {
            item.owner = null;
          }
        }
      }
    }
    if (left != null) {
      // What this box was to take back once in sight again: it closed first, so it becomes the chat's draft.
      for (Pending p : left) {
        toDraft(p.withoutOwner(), null);
      }
    }
  }

  /**
   * A message box comes to the front: what waits to go back into it does (a refused send, a message an earlier launch
   * could not make a draft), and the one-time "your message is back" hint shows.
   */
  @UiThread
  public static void onComposerShown (MessagesController owner) {
    if (owner == null || owner.getChat() == null || !owner.xgramUndoSendEligible()) {
      return;
    }
    boolean restored = restorePending(owner);
    boolean hint = false;
    if (mayHaveHints) {
      hint = XgramUndoSendStore.takeHint(owner.tdlib().id(), owner.getChatId());
      if (!hint && !XgramUndoSendStore.hasAnyHint()) {
        mayHaveHints = false;
      }
    }
    if (restored || hint) {
      showRestoredHint(owner);
    }
  }

  /**
   * The message box can take something back again (it is back in sight, the app resumed, an edit closed, in-chat
   * search closed, its own send finished, the prefill prompt closed): what waits to go back into it does.
   */
  @UiThread
  public static void retryPending (MessagesController owner) {
    boolean any;
    synchronized (LOCK) {
      any = !pending.isEmpty();
    }
    if (any && owner != null && owner.getChat() != null && owner.xgramUndoSendEligible() && restorePending(owner)) {
      showRestoredHint(owner);
    }
  }

  private static void showRestoredHint (MessagesController owner) {
    UI.post(() -> {
      if (owner.isFocused()) {
        owner.xgramShowUndoSendHint(Lang.getString(R.string.XgramUndoSendRestoredHint), false);
      }
    }, HINT_DELAY_MS);
  }

  /** Puts back what waits for this message box; true when any of it wants the "your message is back" hint. */
  @UiThread
  private static boolean restorePending (MessagesController owner) {
    final int accountId = owner.tdlib().id();
    final long chatId = owner.getChatId();
    final long draftChatId = owner.getMessageThreadChatId();
    List<Pending> matches = null;
    synchronized (LOCK) {
      if (pending.isEmpty()) {
        return false;
      }
      TdApi.MessageTopic topic = owner.getMessageTopicId();
      for (Pending p : pending) {
        XgramUndoSendStore.Record record = p.record;
        if (record.accountId == accountId && record.chatId == chatId && record.draftChatId == draftChatId && Td.equalsTo(record.topic, topic)) {
          if (matches == null) {
            matches = new ArrayList<>();
          }
          matches.add(p);
        }
      }
    }
    if (matches == null) {
      return false;
    }
    boolean notice = false;
    // Each goes before what the message box holds: the newest first, so they end up oldest first.
    Collections.sort(matches, (a, b) -> Long.compare(b.record.createdWall, a.record.createdWall));
    for (Pending p : matches) {
      XgramUndoSendStore.Record record = p.record;
      // The files and the reply bar's message only come back into the box that sent them.
      boolean sameBox = p.owner() == owner;
      ArrayList<InlineResult<?>> files = sameBox ? p.files : null;
      MessagesController.ReplyInfo reply = sameBox ? p.reply : null;
      if (owner.xgramRestoreUnsent(record.chatId, record.draftChatId, record.topic, record.text, reply, record.replyTo, p.showKeyboard, files)) {
        synchronized (LOCK) {
          for (int i = pending.size() - 1; i >= 0; i--) {
            if (pending.get(i).record.key.equals(record.key)) {
              pending.remove(i);
            }
          }
        }
        XgramUndoSendStore.remove(record.key);
        if (files == null || files.isEmpty()) {
          showFilesNotSent(record.fileNames);
        }
        notice |= p.notice;
      }
    }
    return notice;
  }

  /** Kept until a message box of its chat can take it (the newer one wins: what is left to give back). */
  private static void keepPending (Pending p) {
    synchronized (LOCK) {
      if (!isCurrent(p.record.accountId, p.epoch)) {
        return; // its account logged out
      }
      for (int i = 0; i < pending.size(); i++) {
        if (pending.get(i).record.key.equals(p.record.key)) {
          pending.set(i, p);
          return;
        }
      }
      pending.add(p);
    }
  }

  // Drafts

  private interface DraftCallback {
    void onDraft (@Nullable TdApi.DraftMessage draft, boolean readable);
  }

  private static void readDraft (Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topic, DraftCallback callback) {
    if (topic == null) {
      tdlib.send(new TdApi.GetChat(chatId), (chat, error) -> callback.onDraft(chat != null ? chat.draftMessage : null, chat != null));
    } else if (topic instanceof TdApi.MessageTopicForum) {
      tdlib.send(new TdApi.GetForumTopic(chatId, ((TdApi.MessageTopicForum) topic).forumTopicId), (forumTopic, error) -> callback.onDraft(forumTopic != null ? forumTopic.draftMessage : null, forumTopic != null));
    } else if (topic instanceof TdApi.MessageTopicThread) {
      tdlib.send(new TdApi.GetMessageThread(chatId, ((TdApi.MessageTopicThread) topic).messageThreadId), (thread, error) -> callback.onDraft(thread != null ? thread.draftMessage : null, thread != null));
    } else if (topic instanceof TdApi.MessageTopicDirectMessages) {
      tdlib.send(new TdApi.GetDirectMessagesChatTopic(chatId, ((TdApi.MessageTopicDirectMessages) topic).directMessagesChatTopicId), (dmTopic, error) -> callback.onDraft(dmTopic != null ? dmTopic.draftMessage : null, dmTopic != null));
    } else {
      // A Saved Messages topic's draft can't be read: the message box takes the text back instead.
      callback.onDraft(null, false);
    }
  }

  /**
   * Makes {@code held} the draft of the chat (or topic), before the draft it has: Telegram X's own SetChatDraftMessage.
   * {@code after} runs on a TDLib thread with true once the draft is set.
   */
  private static void saveAsDraft (Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topic, TdApi.FormattedText held,
                                   @Nullable TdApi.InputMessageReplyTo replyTo, RunnableBool after) {
    readDraft(tdlib, chatId, topic, (existing, readable) -> {
      if (!readable) {
        after.runWithBool(false);
        return;
      }
      TdApi.FormattedText existingText = null;
      TdApi.LinkPreviewOptions linkPreviewOptions = null;
      if (existing != null && existing.content != null) {
        if (!(existing.content instanceof TdApi.DraftMessageContentText)) {
          after.runWithBool(false); // a recorded draft would be lost
          return;
        }
        TdApi.DraftMessageContentText content = (TdApi.DraftMessageContentText) existing.content;
        existingText = content.text;
        linkPreviewOptions = content.linkPreviewOptions;
      }
      TdApi.FormattedText merged = Td.isEmpty(existingText) || Td.equalsTo(existingText, held) ?
        held : Td.concat(held, new TdApi.FormattedText("\n", new TdApi.TextEntity[0]), existingText);
      TdApi.InputMessageReplyTo reply = existing != null && existing.replyTo != null ? existing.replyTo : replyTo;
      TdApi.DraftMessage draft = new TdApi.DraftMessage(reply, (int) tdlib.currentTime(TimeUnit.SECONDS), new TdApi.DraftMessageContentText(merged, linkPreviewOptions), 0, null);
      tdlib.send(new TdApi.SetChatDraftMessage(chatId, topic, draft), (ok, error) -> after.runWithBool(error == null));
    });
  }

  // After a crash

  /** Tdlib's constructor: an account with records of an earlier launch makes them drafts once it is logged in. */
  @AnyThread
  public static void attach (Tdlib tdlib) {
    try {
      if (!XgramUndoSendStore.hasRecords(tdlib.id())) {
        return;
      }
    } catch (Throwable t) {
      return;
    }
    tdlib.awaitMyUserOrUnauthorizedState(() -> restoreOrphans(tdlib));
  }

  private static void restoreOrphans (Tdlib tdlib) {
    final int epoch;
    synchronized (LOCK) {
      if (!restoredAccounts.add(tdlib.id())) {
        return;
      }
      epoch = epochs.get(tdlib.id(), 0);
    }
    if (!tdlib.isAuthorized()) {
      return;
    }
    List<XgramUndoSendStore.Record> orphans = XgramUndoSendStore.orphans(tdlib.id(), BOOT_ID, MAX_ORPHANS_PER_LAUNCH);
    if (!orphans.isEmpty()) {
      restoreNext(tdlib, orphans, 0, new int[1], epoch);
    }
  }

  /** One record after the other (each step waits for TDLib's answer). */
  private static void restoreNext (Tdlib tdlib, List<XgramUndoSendStore.Record> orphans, int index, int[] restored, int epoch) {
    if (index >= orphans.size()) {
      int count = restored[0];
      if (count > 0 && UI.getUiState() == UI.State.RESUMED) {
        UI.showToast(Lang.getString(count == 1 ? R.string.XgramUndoSendRestoredDraft : R.string.XgramUndoSendRestoredDrafts, count), Toast.LENGTH_LONG);
      }
      return;
    }
    final XgramUndoSendStore.Record record = orphans.get(index);
    final Runnable next = () -> restoreNext(tdlib, orphans, index + 1, restored, epoch);
    if (record.kind == XgramUndoSendItem.KIND_VOICE || record.kind == XgramUndoSendItem.KIND_VIDEO) {
      // A recording is never sent unasked, and it can't be a draft.
      XgramUndoSendStore.remove(record.key);
      if (UI.getUiState() == UI.State.RESUMED) {
        UI.showToast(R.string.XgramUndoSendNoteLost, Toast.LENGTH_LONG);
      }
      next.run();
      return;
    }
    if (record.flushing) {
      // It was on its way: when TDLib has it, it is sent (or sending) and no draft is needed.
      tdlib.send(new TdApi.GetChatHistory(record.chatId, 0, 0, 30, true), (messages, error) -> {
        if (messages != null && wasSent(tdlib, messages, record)) {
          XgramUndoSendStore.remove(record.key);
          next.run();
        } else {
          restoreAsDraft(tdlib, record, restored, next, epoch);
        }
      });
    } else {
      restoreAsDraft(tdlib, record, restored, next, epoch);
    }
  }

  /**
   * After a crash: TDLib has the message of a record that was on its way. Only an exact match counts (the last part
   * of a long text as Telegram X split it, the parts go in order; for files the last file's name and the caption it
   * carries), sent by the user at the flush or after it and newer than the chat's last message then. When unsure it
   * becomes a draft: a duplicate draft is harmless, a lost message isn't.
   */
  private static boolean wasSent (Tdlib tdlib, TdApi.Messages messages, XgramUndoSendStore.Record record) {
    if (messages.messages == null) {
      return false;
    }
    // Telegram's time. Records of earlier versions only know when the message started to wait (the device's clock).
    final long since = (record.flushDate > 0 ? record.flushDate :
      tdlib.toTdlibTimeMillis(record.createdWall, TimeUnit.MILLISECONDS) / 1000L) - SENT_MARGIN_SECONDS;
    final long newerThan = record.flushLastMessageId;
    if (record.kind == XgramUndoSendItem.KIND_FILES) {
      String lastName = record.fileNames != null && record.fileNames.length > 0 ? record.fileNames[record.fileNames.length - 1] : null;
      if (TextUtils.isEmpty(lastName)) {
        return false;
      }
      final String caption = trimmed(record.text.text);
      for (TdApi.Message message : messages.messages) {
        if (!isOwnSince(tdlib, message, since, newerThan)) {
          continue;
        }
        String fileName = null;
        TdApi.FormattedText sentCaption = null;
        if (message.content instanceof TdApi.MessageDocument) {
          TdApi.MessageDocument document = (TdApi.MessageDocument) message.content;
          fileName = document.document != null ? document.document.fileName : null;
          sentCaption = document.caption;
        } else if (message.content instanceof TdApi.MessageAudio) {
          TdApi.MessageAudio audio = (TdApi.MessageAudio) message.content;
          fileName = audio.audio != null ? audio.audio.fileName : null;
          sentCaption = audio.caption;
        }
        if (lastName.equals(fileName) && caption.equals(trimmed(sentCaption != null ? sentCaption.text : null))) {
          return true;
        }
      }
      return false;
    }
    List<TdApi.InputMessageContent> parts = TD.explodeText(new TdApi.InputMessageText(record.text, null, false), tdlib.maxMessageTextLength());
    TdApi.InputMessageContent lastPart = parts.isEmpty() ? null : parts.get(parts.size() - 1);
    if (!(lastPart instanceof TdApi.InputMessageText) || ((TdApi.InputMessageText) lastPart).text == null) {
      return false;
    }
    final String expected = trimmed(((TdApi.InputMessageText) lastPart).text.text);
    if (expected.isEmpty()) {
      return false;
    }
    for (TdApi.Message message : messages.messages) {
      if (!isOwnSince(tdlib, message, since, newerThan)) {
        continue;
      }
      String sent = null;
      if (message.content instanceof TdApi.MessageText) {
        TdApi.FormattedText text = ((TdApi.MessageText) message.content).text;
        sent = text != null ? text.text : null;
      } else if (message.content instanceof TdApi.MessageAnimatedEmoji) {
        sent = ((TdApi.MessageAnimatedEmoji) message.content).emoji; // a lone emoji
      }
      if (sent != null && expected.equals(trimmed(sent))) {
        return true;
      }
    }
    return false;
  }

  /** The user's own message (in Saved Messages own messages aren't "outgoing"), sent in the window. */
  private static boolean isOwnSince (Tdlib tdlib, @Nullable TdApi.Message message, long since, long newerThan) {
    return message != null && message.date >= since && (newerThan == 0 || message.id > newerThan) &&
      (message.isOutgoing || tdlib.isSelfSender(message.senderId));
  }

  /** The text as Telegram keeps it: no carriage returns, no space or line break at either end. */
  private static String trimmed (@Nullable String text) {
    if (text == null) {
      return "";
    }
    String s = text.indexOf('\r') >= 0 ? text.replace("\r", "") : text;
    int start = 0, end = s.length();
    while (start < end && isBlank(s.charAt(start))) {
      start++;
    }
    while (end > start && isBlank(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(start, end);
  }

  private static boolean isBlank (char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  private static void restoreAsDraft (Tdlib tdlib, XgramUndoSendStore.Record record, int[] restored, Runnable next, int epoch) {
    if (Td.isEmpty(record.text)) {
      // Files without a caption: they can't come back, they are listed.
      XgramUndoSendStore.remove(record.key);
      if (UI.getUiState() == UI.State.RESUMED) {
        showFilesNotSent(record.fileNames);
      }
      next.run();
      return;
    }
    final Pending later = new Pending(tdlib, record, epoch, null, null, null, true, true);
    if (record.topic instanceof TdApi.MessageTopicSavedMessages || XgramUndoSendStore.takeDebugFailRestore()) {
      keepPending(later);
      next.run();
      return;
    }
    saveAsDraft(tdlib, record.draftChatId, record.topic, record.text, record.replyTo, ok -> {
      if (ok) {
        XgramUndoSendStore.remove(record.key);
        journalHint(record.accountId, epoch, record.chatId);
        mayHaveHints = true;
        restored[0]++;
        if (UI.getUiState() == UI.State.RESUMED) {
          showFilesNotSent(record.fileNames);
        }
      } else {
        keepPending(later); // the message box takes it back when that chat opens
      }
      next.run();
    });
  }

  /** The account in this slot logged out (XgramAccountData erased its records). */
  @AnyThread
  public static void forgetAccount (int accountId) {
    final List<XgramUndoSendItem> dropped = new ArrayList<>();
    synchronized (LOCK) {
      // From now on nothing of that account is written to the journal or kept (journal*, keepPending)
      epochs.put(accountId, epochs.get(accountId, 0) + 1);
      for (int i = items.size() - 1; i >= 0; i--) {
        XgramUndoSendItem item = items.get(i);
        if (item.accountId == accountId) {
          if (item.state == XgramUndoSendItem.HELD) {
            heldCount = Math.max(0, heldCount - 1);
          }
          item.state = XgramUndoSendItem.UNDONE;
          items.remove(i);
          dropped.add(item);
        }
      }
      for (int i = pending.size() - 1; i >= 0; i--) {
        if (pending.get(i).record.accountId == accountId) {
          pending.remove(i);
        }
      }
      restoredAccounts.remove(accountId);
    }
    for (XgramUndoSendItem item : dropped) {
      if (item.dueTask != null) {
        UI.cancel(item.dueTask);
      }
    }
    if (!dropped.isEmpty()) {
      UI.post(() -> notifyOwners(dropped, false)); // their bars empty
    }
  }

  // Debug builds: the long-press menu of Send

  public static void debugCrashAfterHold () {
    debugCrashAfterHold = BuildConfig.DEBUG;
  }

  public static void debugCrashAfterSend () {
    debugCrashAfterSend = BuildConfig.DEBUG;
  }

  public static void debugFailDraftRestore () {
    XgramUndoSendStore.setDebugFailRestore();
  }
}
