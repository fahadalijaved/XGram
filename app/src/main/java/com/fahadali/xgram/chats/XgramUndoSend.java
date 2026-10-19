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
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.AppContext;

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
 * Undo send (iMessage, Gmail): text sent from the message box waits on this device for the chosen delay before the
 * exact SendMessage requests Telegram X built go to TDLib, so it can be taken back while nothing has reached Telegram.
 * The waiting message is shown by {@link XgramUndoSendBar} above the message box, not in the message list, so the
 * real bubble (the one a sticker or Telegram X's own sending makes) is the only one there is.
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
  /** Records of earlier launches that could not become drafts: put back into their message box when it opens. */
  private static final ArrayList<XgramUndoSendStore.Record> pending = new ArrayList<>();
  private static final HashSet<Integer> restoredAccounts = new HashSet<>();
  private static volatile boolean mayHaveHints = true;
  private static boolean stateListenerAdded;
  private static volatile boolean debugCrashAfterHold, debugCrashAfterSend;

  /** A strong reference: UI keeps its state listeners in a weak list. */
  private static final UI.StateListener STATE = newState -> {
    if (newState == UI.State.PAUSED || newState == UI.State.DESTROYED) {
      flushAll();
    }
  };

  // What the bar shows

  /** One item as the bar shows it. */
  static final class Shown {
    final long order;
    final int kind;
    final CharSequence preview;
    @Nullable final String replyName;
    final boolean silent;
    final int parts;
    final boolean flushing;
    final long dueUptime, totalMs;

    Shown (XgramUndoSendItem item, boolean flushing) {
      this.order = item.order;
      this.kind = item.kind;
      this.preview = item.preview != null ? item.preview : "";
      this.replyName = item.replyName;
      this.silent = item.silent;
      this.parts = item.parts.size();
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
      if (item.kind == XgramUndoSendItem.KIND_VOICE || item.kind == XgramUndoSendItem.KIND_VIDEO) {
        item.preview = Lang.getString(item.kind == XgramUndoSendItem.KIND_VIDEO ? R.string.XgramUndoSendVideoPreview : R.string.XgramUndoSendVoicePreview, Strings.buildDuration(item.durationSeconds));
      } else {
        String text = item.typed.text != null ? item.typed.text : "";
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
    final List<TdApi.SendMessage> parts;
    try {
      XgramSettings settings = XgramSettings.instance();
      int seconds = settings.undoSendSeconds();
      if (seconds <= 0 || !UI.inUiThread() || owner == null || !owner.xgramUndoSendEligible()) {
        return NOT_HELD;
      }
      Tdlib tdlib = owner.tdlib();
      long chatId = owner.getChatId();
      if (settings.isUndoSendExcluded(tdlib.id(), chatId)) {
        return NOT_HELD;
      }
      if (options == null || options.schedulingState != null || functions == null || functions.isEmpty() || Td.isEmpty(typed)) {
        return NOT_HELD;
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
        reply, reply != null ? reply.toInputMessageReply() : null, options.disableNotification, 0, holdMillis(seconds), owner);
      if (!XgramUndoSendStore.put(item, BOOT_ID)) {
        return NOT_HELD;
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: sending right away", t);
      return NOT_HELD;
    }
    // Whether the send clears the chat's draft is decided when it goes (flushUpTo).
    for (TdApi.SendMessage part : parts) {
      ((TdApi.InputMessageText) part.inputMessageContent).clearDraft = false;
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

  /** Voice and video messages may wait (Messages > Delay voice and video messages). */
  @AnyThread
  public static boolean mayHoldNotes () {
    XgramSettings settings = XgramSettings.instance();
    return settings.undoSendSeconds() > 0 && settings.undoSendVoice();
  }

  /**
   * A voice or video message from the recorder: true when it waits (the caller then sends nothing). Only recordings
   * TDLib already has as a file wait; anything else is sent as always.
   */
  @UiThread
  public static boolean tryHoldNote (MessagesController owner, TdApi.SendMessage function, @Nullable MessagesController.ReplyInfo reply, int durationSeconds) {
    final XgramUndoSendItem item;
    try {
      if (!mayHoldNotes() || !UI.inUiThread() || owner == null || function == null || !owner.xgramUndoSendEligible()) {
        return false;
      }
      Tdlib tdlib = owner.tdlib();
      long chatId = owner.getChatId();
      if (function.chatId != chatId || XgramSettings.instance().isUndoSendExcluded(tdlib.id(), chatId)) {
        return false;
      }
      if (function.options == null || function.options.schedulingState != null) {
        return false;
      }
      int kind;
      TdApi.InputMessageContent content = function.inputMessageContent;
      if (content instanceof TdApi.InputMessageVoiceNote) {
        TdApi.InputVoiceNote voiceNote = ((TdApi.InputMessageVoiceNote) content).voiceNote;
        if (voiceNote == null || !(voiceNote.voiceNote instanceof TdApi.InputFileId)) {
          return false;
        }
        kind = XgramUndoSendItem.KIND_VOICE;
      } else if (content instanceof TdApi.InputMessageVideoNote) {
        TdApi.InputVideoNote videoNote = ((TdApi.InputMessageVideoNote) content).videoNote;
        if (videoNote == null || !(videoNote.videoNote instanceof TdApi.InputFileId)) {
          return false;
        }
        kind = XgramUndoSendItem.KIND_VIDEO;
      } else {
        return false;
      }
      if (inSlowModeAsMember(tdlib, chatId) && hasItemsIn(tdlib, chatId)) {
        return false;
      }
      List<TdApi.SendMessage> parts = new ArrayList<>(1);
      parts.add(function);
      item = new XgramUndoSendItem(tdlib, kind, chatId, owner.getMessageThreadChatId(), owner.getMessageTopicId(), parts,
        new TdApi.FormattedText("", new TdApi.TextEntity[0]), reply, function.replyTo, function.options.disableNotification,
        Math.max(0, durationSeconds), holdMillis(XgramSettings.instance().undoSendSeconds()), owner);
      if (!XgramUndoSendStore.put(item, BOOT_ID)) {
        return false;
      }
    } catch (Throwable t) {
      android.util.Log.e("XGram", "Undo send: sending the recording right away", t);
      return false;
    }
    commit(owner, item);
    return true;
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
      flushUpTo(oldest, true);
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
    if (held) {
      flushUpTo(item, true);
    }
  }

  /**
   * Sends {@code upTo} and every older item of its chat (oldest first, back to back, before the caller sends anything
   * else). No lock is held while TDLib is called.
   */
  @AnyThread
  private static void flushUpTo (XgramUndoSendItem upTo, boolean notify) {
    List<XgramUndoSendItem> batch = null;
    synchronized (LOCK) {
      for (XgramUndoSendItem item : items) {
        if (item.state == XgramUndoSendItem.HELD && item.tdlib == upTo.tdlib && item.chatId == upTo.chatId && item.order <= upTo.order) {
          item.state = XgramUndoSendItem.FLUSHING;
          if (batch == null) {
            batch = new ArrayList<>();
          }
          batch.add(item);
        }
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
      XgramUndoSendStore.markFlushing(item, BOOT_ID);
      // As a send from the message box does: the draft is cleared, unless the message box holds something new.
      boolean clearDraft = mainThread && composerEmptyFor(item);
      for (int i = 0; i < item.parts.size(); i++) {
        TdApi.InputMessageContent content = item.parts.get(i).inputMessageContent;
        if (content instanceof TdApi.InputMessageText) {
          ((TdApi.InputMessageText) content).clearDraft = clearDraft && i == 0;
        }
      }
    }
    for (XgramUndoSendItem item : batch) {
      Client client = item.tdlib.client();
      for (int i = 0; i < item.parts.size(); i++) {
        client.send(item.parts.get(i), new PartHandler(item, i));
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

  /** Main thread only (flushUpTo checks). */
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
    XgramUndoSendItem newest = null;
    synchronized (LOCK) {
      for (XgramUndoSendItem item : items) {
        if (item.state == XgramUndoSendItem.HELD && item.tdlib == tdlib && item.chatId == chatId) {
          newest = item;
        }
      }
    }
    if (newest != null) {
      flushUpTo(newest, true);
    }
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
    while (heldCount > 0) {
      XgramUndoSendItem newest = null;
      synchronized (LOCK) {
        for (int i = items.size() - 1; i >= 0; i--) {
          XgramUndoSendItem item = items.get(i);
          if (item.state == XgramUndoSendItem.HELD) {
            newest = item;
            break;
          }
        }
      }
      if (newest == null) {
        break;
      }
      flushUpTo(newest, true);
    }
  }

  /** "Send now": everything that waits in {@code owner}'s message box goes now, oldest first. */
  @UiThread
  public static void sendNow (MessagesController owner) {
    XgramUndoSendItem newest = newestHeld(owner);
    if (newest != null) {
      flushUpTo(newest, true);
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

  /** TDLib's answer to one part: the Message goes where Telegram X's own sends go, so its bubble is the only one. */
  private static final class PartHandler implements Client.ResultHandler {
    private final XgramUndoSendItem item;
    private final int index;

    PartHandler (XgramUndoSendItem item, int index) {
      this.item = item;
      this.index = index;
    }

    @Override
    public void onResult (TdApi.Object result) {
      if (result instanceof TdApi.Message) {
        item.tdlib.messageHandler().onResult(result);
        if (((TdApi.Message) result).schedulingState != null) {
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
      failed = item.failed;
      ghostScheduled = item.ghostScheduled;
      error = item.error;
      item.state = failed != null ? XgramUndoSendItem.FAILED : XgramUndoSendItem.DONE;
      items.remove(item);
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
    final TdApi.FormattedText text = failedText(item, failed);
    final boolean firstFailed = failed[0];
    final MessagesController.ReplyInfo reply = firstFailed ? item.reply : null;
    final TdApi.InputMessageReplyTo replyTo = firstFailed ? item.replyTo : null;
    if (owner != null && owner.xgramRestoreUnsent(item.chatId, item.draftChatId, item.draftTopic, text, reply, replyTo, true)) {
      XgramUndoSendStore.remove(item);
      if (!owner.xgramShowUndoSendHint(TD.toErrorString(error), true)) {
        UI.showError(error);
      }
      return;
    }
    final Tdlib tdlib = item.tdlib;
    saveAsDraft(tdlib, item.draftChatId, item.draftTopic, text, replyTo, ok -> {
      if (ok) {
        XgramUndoSendStore.remove(item);
        UI.showToast(Lang.getString(R.string.XgramUndoSendFailedDraft, tdlib.chatTitle(item.chatId)), Toast.LENGTH_LONG);
      } else {
        // Kept: the message box of that chat takes it back when it opens (or the next launch makes it a draft).
        keepPending(new XgramUndoSendStore.Record(XgramUndoSendStore.key(item), item.accountId, BOOT_ID, true, item.kind,
          item.chatId, item.draftChatId, item.createdWall, item.draftTopic, replyTo, item.silent, text));
        if (error != null) {
          UI.showError(error);
        }
      }
    });
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
      TdApi.InputMessageContent content = item.parts.get(i).inputMessageContent;
      if (content instanceof TdApi.InputMessageText) {
        TdApi.FormattedText part = ((TdApi.InputMessageText) content).text;
        result = result == null ? part : Td.concat(result, part);
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
    synchronized (LOCK) {
      if (item.state != XgramUndoSendItem.HELD) {
        return false; // the deadline won: it is on its way
      }
      item.state = XgramUndoSendItem.UNDONE;
      items.remove(item);
      heldCount = Math.max(0, heldCount - 1);
    }
    if (item.dueTask != null) {
      UI.cancel(item.dueTask);
    }
    // Nothing of it ever reached TDLib.
    TdApi.FormattedText text = item.isNote() ? null : item.typed;
    if (owner.xgramRestoreUnsent(item.chatId, item.draftChatId, item.draftTopic, text, item.reply, item.replyTo, !item.isNote())) {
      XgramUndoSendStore.remove(item);
    } else if (item.isNote()) {
      XgramUndoSendStore.remove(item); // discarded; only the reply had nowhere to go
    } else {
      final XgramUndoSendItem undone = item;
      saveAsDraft(item.tdlib, item.draftChatId, item.draftTopic, item.typed, item.replyTo, ok -> {
        if (ok) {
          XgramUndoSendStore.remove(undone);
        } else {
          keepPending(new XgramUndoSendStore.Record(XgramUndoSendStore.key(undone), undone.accountId, BOOT_ID, false, undone.kind,
            undone.chatId, undone.draftChatId, undone.createdWall, undone.draftTopic, undone.replyTo, undone.silent, undone.typed));
        }
      });
    }
    notifyOwner(owner, false);
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
    synchronized (LOCK) {
      if (items.isEmpty()) {
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
    }
    if (!any) {
      return;
    }
    if (newest != null) {
      flushUpTo(newest, false);
    }
    synchronized (LOCK) {
      for (XgramUndoSendItem item : items) {
        if (item.isOwnedBy(owner)) {
          item.owner = null;
        }
      }
    }
  }

  /**
   * A message box comes to the front: a message an earlier launch could not make a draft goes back into it, and the
   * one-time "your message is back" hint shows.
   */
  @UiThread
  public static void onComposerShown (MessagesController owner) {
    if (owner == null || owner.getChat() == null || !owner.xgramUndoSendEligible()) {
      return;
    }
    final int accountId = owner.tdlib().id();
    final long chatId = owner.getChatId();
    List<XgramUndoSendStore.Record> matches = null;
    synchronized (LOCK) {
      if (!pending.isEmpty()) {
        TdApi.MessageTopic topic = owner.getMessageTopicId();
        for (XgramUndoSendStore.Record record : pending) {
          if (record.accountId == accountId && record.chatId == chatId && Td.equalsTo(record.topic, topic)) {
            if (matches == null) {
              matches = new ArrayList<>();
            }
            matches.add(record);
          }
        }
      }
    }
    boolean restored = false;
    if (matches != null) {
      // Each goes before what the message box holds: the newest first, so they end up oldest first.
      Collections.sort(matches, (a, b) -> Long.compare(b.createdWall, a.createdWall));
      for (XgramUndoSendStore.Record record : matches) {
        if (owner.xgramRestoreUnsent(record.chatId, record.draftChatId, record.topic, record.text, null, record.replyTo, true)) {
          XgramUndoSendStore.remove(record.key);
          synchronized (LOCK) {
            pending.remove(record);
          }
          restored = true;
        }
      }
    }
    boolean hint = false;
    if (mayHaveHints) {
      hint = XgramUndoSendStore.takeHint(accountId, chatId);
      if (!hint && !XgramUndoSendStore.hasAnyHint()) {
        mayHaveHints = false;
      }
    }
    if (restored || hint) {
      UI.post(() -> {
        if (owner.isFocused()) {
          owner.xgramShowUndoSendHint(Lang.getString(R.string.XgramUndoSendRestoredHint), false);
        }
      }, HINT_DELAY_MS);
    }
  }

  private static void keepPending (XgramUndoSendStore.Record record) {
    synchronized (LOCK) {
      for (XgramUndoSendStore.Record existing : pending) {
        if (existing.key.equals(record.key)) {
          return;
        }
      }
      pending.add(record);
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
    synchronized (LOCK) {
      if (!restoredAccounts.add(tdlib.id())) {
        return;
      }
    }
    if (!tdlib.isAuthorized()) {
      return;
    }
    List<XgramUndoSendStore.Record> orphans = XgramUndoSendStore.orphans(tdlib.id(), BOOT_ID, MAX_ORPHANS_PER_LAUNCH);
    if (!orphans.isEmpty()) {
      restoreNext(tdlib, orphans, 0, new int[1]);
    }
  }

  /** One record after the other (each step waits for TDLib's answer). */
  private static void restoreNext (Tdlib tdlib, List<XgramUndoSendStore.Record> orphans, int index, int[] restored) {
    if (index >= orphans.size()) {
      int count = restored[0];
      if (count > 0 && UI.getUiState() == UI.State.RESUMED) {
        UI.showToast(Lang.getString(count == 1 ? R.string.XgramUndoSendRestoredDraft : R.string.XgramUndoSendRestoredDrafts, count), Toast.LENGTH_LONG);
      }
      return;
    }
    final XgramUndoSendStore.Record record = orphans.get(index);
    final Runnable next = () -> restoreNext(tdlib, orphans, index + 1, restored);
    if (record.kind != XgramUndoSendItem.KIND_TEXT) {
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
          restoreAsDraft(tdlib, record, restored, next);
        }
      });
    } else {
      restoreAsDraft(tdlib, record, restored, next);
    }
  }

  private static boolean wasSent (Tdlib tdlib, TdApi.Messages messages, XgramUndoSendStore.Record record) {
    if (messages.messages == null) {
      return false;
    }
    String held = record.text.text;
    long since = record.createdWall / 1000L - 120L;
    for (TdApi.Message message : messages.messages) {
      if (message == null || !(message.content instanceof TdApi.MessageText) || message.date < since) {
        continue;
      }
      if (!message.isOutgoing && !tdlib.isSelfSender(message.senderId)) {
        continue; // Saved Messages: own messages aren't "outgoing" there
      }
      TdApi.FormattedText sent = ((TdApi.MessageText) message.content).text;
      if (sent != null && sent.text != null && !sent.text.isEmpty() && held.startsWith(sent.text)) {
        return true;
      }
    }
    return false;
  }

  private static void restoreAsDraft (Tdlib tdlib, XgramUndoSendStore.Record record, int[] restored, Runnable next) {
    if (record.topic instanceof TdApi.MessageTopicSavedMessages || XgramUndoSendStore.takeDebugFailRestore()) {
      keepPending(record);
      next.run();
      return;
    }
    saveAsDraft(tdlib, record.draftChatId, record.topic, record.text, record.replyTo, ok -> {
      if (ok) {
        XgramUndoSendStore.remove(record.key);
        XgramUndoSendStore.putHint(record.accountId, record.chatId);
        mayHaveHints = true;
        restored[0]++;
      } else {
        keepPending(record); // the message box takes it back when that chat opens
      }
      next.run();
    });
  }

  /** The account in this slot logged out (XgramAccountData erased its records). */
  @AnyThread
  public static void forgetAccount (int accountId) {
    List<XgramUndoSendItem> dropped = new ArrayList<>();
    synchronized (LOCK) {
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
        if (pending.get(i).accountId == accountId) {
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
