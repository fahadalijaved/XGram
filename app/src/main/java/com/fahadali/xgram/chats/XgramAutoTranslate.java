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

import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramAccountData;
import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import me.vkryl.core.StringUtils;
import me.vkryl.leveldb.LevelDB;
import tgx.td.ChatId;

/**
 * Auto-translate (AyuGram): in a chat set to it, incoming messages and captions in another language are translated
 * Inline as they come on screen ("Translate chat" in the chat menu, or "Auto-translate all chats" in Settings, XGram,
 * Chats, Translation). Messages in the target language or in a "Do not translate" language, and the user's own
 * messages, stay as they are; "Show original" works as for any Inline translation.
 *
 * Only messages that are on screen are translated, two requests at a time. Translations are cached (per message
 * and, in {@code TranslationsManager}, for every message with the same text), so scrolling back or opening the chat
 * again sends nothing. After an error (e.g. too many requests) the queue waits, longer each time or as long as asked,
 * and says so once; after Telegram translated because the chosen service failed, Telegram alone translates for a while.
 * A message being edited again and again (a bot writing its answer) is translated once the edits stop.
 * Never in secret chats: the text would leave the end-to-end encrypted chat. Not in Saved Messages (all outgoing).
 *
 * Stored per account slot and chat: true or false when set in the chat menu, nothing when the chat follows
 * "Auto-translate all chats". Backups keep it by Telegram user, like the folder settings.
 */
public final class XgramAutoTranslate {
  private XgramAutoTranslate () { }

  public static final String KEY_PREFIX = "xgram_atr_";

  /** {@link TGMessage#xgramAutoTranslateState}. */
  public static final int STATE_NONE = 0, STATE_CHECKING = 1, STATE_QUEUED = 2, STATE_REQUESTED = 3, STATE_DONE = 4, STATE_SKIPPED = 5, STATE_FAILED = 6, STATE_EDITED = 7;

  private static final int MAX_REQUESTS = 2;
  private static final long FIRST_BACKOFF = 5_000, MAX_BACKOFF = 5 * 60_000, ERROR_INTERVAL = 60_000;
  /** Telegram alone after the chosen service failed: one minute, longer each time it fails again. */
  private static final long FIRST_SERVICE_BACKOFF = 60_000, MAX_SERVICE_BACKOFF = 30 * 60_000;
  /** A message edited again within this time (e.g. a bot writing its answer) is translated once the edits stop. */
  private static final long EDIT_DELAY = 2_500;
  /** A request with no answer by then (no connection) stops holding up the others; its answer is still shown. */
  private static final long REQUEST_TIMEOUT = 30_000;

  // Settings

  private static Map<String, Boolean> chats;

  private static LevelDB pmc () {
    return Settings.instance().pmc();
  }

  private static synchronized Map<String, Boolean> chats () {
    if (chats == null) {
      chats = new ConcurrentHashMap<>();
      for (LevelDB.Entry entry : pmc().find(KEY_PREFIX)) {
        chats.put(entry.key(), entry.asBoolean());
      }
    }
    return chats;
  }

  private static String key (int accountId, long chatId) {
    return KEY_PREFIX + accountId + "_" + chatId;
  }

  /** Whether the chat can be auto-translated at all: not a secret chat, not Saved Messages. */
  public static boolean canUse (@Nullable Tdlib tdlib, long chatId) {
    return tdlib != null && chatId != 0 && !ChatId.isSecret(chatId) && !tdlib.isSelfChat(chatId);
  }

  public static boolean isEnabled (@Nullable Tdlib tdlib, long chatId) {
    if (!canUse(tdlib, chatId)) {
      return false;
    }
    Boolean value = chats().get(key(tdlib.id(), chatId));
    return value != null ? value : XgramSettings.instance().translateAutoAll();
  }

  /** Stored only when it differs from "Auto-translate all chats", so the chat follows it otherwise. */
  public static void setEnabled (Tdlib tdlib, long chatId, boolean enabled) {
    if (!canUse(tdlib, chatId)) {
      return;
    }
    String key = key(tdlib.id(), chatId);
    if (enabled == XgramSettings.instance().translateAutoAll()) {
      chats().remove(key);
      pmc().remove(key);
    } else {
      chats().put(key, enabled);
      pmc().putBoolean(key, enabled);
    }
    notifyChanged(tdlib, chatId);
  }

  public static void forgetAccount (int accountId) {
    XgramAccountData.removeKeys(chats().keySet(), KEY_PREFIX + accountId + "_");
  }

  /** The cached values are read again (after a backup import or a reset). */
  public static synchronized void reload () {
    chats = null;
  }

  /** Backup: the chats of an account slot set in their menu, chat id to on or off. */
  public static JSONObject exportAccount (int accountId) throws JSONException {
    JSONObject values = new JSONObject();
    String prefix = KEY_PREFIX + accountId + "_";
    for (LevelDB.Entry entry : pmc().find(prefix)) {
      values.put(entry.key().substring(prefix.length()), entry.asBoolean());
    }
    return values;
  }

  /** Backup: replaces the account slot's chats with {@code values} (from {@link #exportAccount}). */
  public static void importAccount (int accountId, @Nullable JSONObject values) {
    String prefix = KEY_PREFIX + accountId + "_";
    pmc().removeByPrefix(prefix);
    if (values != null) {
      for (Iterator<String> it = values.keys(); it.hasNext(); ) {
        String chatId = it.next();
        try {
          pmc().putBoolean(prefix + Long.parseLong(chatId), values.getBoolean(chatId));
        } catch (NumberFormatException | JSONException ignored) { }
      }
    }
    reload();
    notifyChanged(null, 0);
  }

  // Changes

  public interface Listener {
    /** {@code tdlib} null and {@code chatId} 0: every chat (the default or the target language changed). */
    void onAutoTranslateChanged (@Nullable Tdlib tdlib, long chatId);
  }

  private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

  public static void addListener (Listener listener) {
    listeners.add(listener);
  }

  public static void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  public static void notifyChanged (@Nullable Tdlib tdlib, long chatId) {
    UI.post(() -> {
      for (Listener listener : listeners) {
        listener.onAutoTranslateChanged(tdlib, chatId);
      }
    });
  }

  /** Whether a chat screen auto-translates: an ordinary chat (not a preview, scheduled messages or a secret chat). */
  public static boolean appliesTo (@Nullable MessagesController c, long chatId) {
    return c != null && !c.inPreviewMode() && !c.isInForceTouchMode() && !c.areScheduledOnly() && isEnabled(c.tdlib(), chatId);
  }

  // Languages

  /** "Translate to", or the app's language. */
  public static String targetLanguage () {
    String target = XgramSettings.instance().translateTarget();
    return !StringUtils.isEmpty(target) ? target : appLanguage();
  }

  public static String appLanguage () {
    return Settings.instance().getLanguage().packInfo.pluralCode;
  }

  public static String languageName (@Nullable String code) {
    return code != null ? Lang.getLanguageName(code, code) : "";
  }

  /** What the chat's bar says: the one language translated from so far, or the language translated to. */
  public static String barText (Set<String> translatedFrom) {
    if (translatedFrom.size() == 1) {
      return Lang.getString(R.string.XgramTranslatingFrom, languageName(translatedFrom.iterator().next()));
    }
    return Lang.getString(R.string.XgramTranslatingTo, languageName(targetLanguage()));
  }

  /** Lower case with dashes, and the old codes ML Kit or a language pack may use. */
  private static String normalize (String language) {
    String code = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    switch (base(code)) {
      case "iw": return "he";
      case "in": return "id";
      case "ji": return "yi";
    }
    return code;
  }

  /** The language without its region or script: {@code zh-Hant} is {@code zh}. */
  public static String base (String code) {
    int dash = code.indexOf('-');
    return dash > 0 ? code.substring(0, dash) : code;
  }

  private static boolean sameLanguage (String a, String b) {
    return base(normalize(a)).equals(base(normalize(b)));
  }

  // Messages

  /** Incoming messages with text or a caption; not bot descriptions, ads or the event log. */
  private static boolean wants (TGMessage m) {
    return !m.isOutgoing() && !m.isSecretChat() && !m.isEventLog() && !m.isSponsoredMessage() && !m.isFakeMessage() && !m.isScheduled() && !(m instanceof TGMessageBotInfo);
  }

  /** The message came on screen. */
  public static void onMessageShown (TGMessage m) {
    if (m.xgramAutoTranslateState != STATE_NONE || !m.manager().xgramAutoTranslate()) {
      return;
    }
    if (!wants(m)) {
      m.xgramAutoTranslateState = STATE_SKIPPED;
      return;
    }
    m.xgramAutoTranslateState = STATE_CHECKING;
    final int token = m.xgramAutoTranslateToken;
    m.checkTranslatableText(() -> {
      if (m.xgramAutoTranslateState != STATE_CHECKING || m.xgramAutoTranslateToken != token) {
        return; // turned off, or edited, meanwhile
      }
      if (!m.manager().xgramAutoTranslate()) {
        m.xgramAutoTranslateState = STATE_NONE;
        return;
      }
      String from = m.getOriginalMessageLanguage(), to = targetLanguage();
      if (!m.xgramHasTextToTranslate() || StringUtils.isEmpty(from) || "und".equals(from) || StringUtils.isEmpty(to) || sameLanguage(from, to) || Settings.instance().isNotTranslatableLanguage(from) || m.isTranslated()) {
        m.xgramAutoTranslateState = STATE_SKIPPED;
        return;
      }
      m.xgramAutoTranslateState = STATE_QUEUED;
      queue.add(m);
      schedulePump();
    });
  }

  /**
   * The message's text changed: the translation was dropped, and an answer still on its way is for the old text.
   * The new text is translated when on screen, once the edits stop for {@link #EDIT_DELAY}.
   */
  public static void onMessageEdited (TGMessage m) {
    final int token = ++m.xgramAutoTranslateToken;
    if (!m.manager().xgramAutoTranslate()) {
      m.xgramAutoTranslateState = STATE_NONE;
      return;
    }
    m.xgramAutoTranslateState = STATE_EDITED;
    UI.post(() -> {
      if (m.xgramAutoTranslateToken == token && m.xgramAutoTranslateState == STATE_EDITED) {
        m.xgramAutoTranslateState = STATE_NONE;
        if (m.isAttachedToView()) {
          onMessageShown(m);
        }
      }
    }, EDIT_DELAY);
  }

  /**
   * The chat's auto-translate was turned on or off: messages on screen are translated, or shown as they are. Turned
   * off, only what auto-translate translated goes back to the original; what the user translated stays.
   */
  public static void onChatChanged (@Nullable List<TGMessage> items, boolean autoTranslate) {
    if (items == null) {
      return;
    }
    for (TGMessage m : items) {
      int state = m.xgramAutoTranslateState;
      m.xgramAutoTranslateToken++;
      m.xgramAutoTranslateState = STATE_NONE;
      if (!autoTranslate && (state == STATE_DONE || state == STATE_REQUESTED) && m.isTranslated()) {
        m.stopTranslated();
      }
    }
    if (autoTranslate) {
      for (TGMessage m : items) {
        if (m.isAttachedToView()) {
          onMessageShown(m);
        }
      }
    }
  }

  // Queue

  private static final ArrayDeque<TGMessage> queue = new ArrayDeque<>();
  private static int requests;
  private static long pausedUntil, backoff, serviceUntil, serviceBackoff, lastErrorTime;
  private static boolean pumping, pumpScheduled, pumpPosted;

  private static final class Request implements Runnable {
    final TGMessage message;
    final int token;
    final String language;
    final boolean telegramOnly, sent;
    boolean released;

    Request (TGMessage message, int token, String language, boolean telegramOnly, boolean sent) {
      this.message = message;
      this.token = token;
      this.language = language;
      this.telegramOnly = telegramOnly;
      this.sent = sent;
    }

    /** {@link #REQUEST_TIMEOUT} passed. */
    @Override
    public void run () {
      if (release(this)) {
        pump();
      }
    }
  }

  /** The request stops counting against {@link #MAX_REQUESTS}. */
  private static boolean release (Request r) {
    if (!r.sent || r.released) {
      return false;
    }
    r.released = true;
    UI.removePendingRunnable(r);
    requests = Math.max(0, requests - 1);
    return true;
  }

  /** The chat's screen closed: its messages leave the queue. */
  public static void forget (MessagesManager manager) {
    for (Iterator<TGMessage> it = queue.iterator(); it.hasNext(); ) {
      TGMessage m = it.next();
      if (m.manager() == manager) {
        it.remove();
        if (m.xgramAutoTranslateState == STATE_QUEUED) {
          m.xgramAutoTranslateState = STATE_NONE;
        }
      }
    }
  }

  /** Not while the list binds the message: a cached translation would change its layout in the middle of it. */
  private static void schedulePump () {
    if (!pumpPosted) {
      pumpPosted = true;
      UI.post(() -> {
        pumpPosted = false;
        pump();
      });
    }
  }

  /**
   * Cached translations are shown right away, even during a pause or with every request taken; the others wait for a
   * free request and the end of the pause.
   */
  private static void pump () {
    if (pumping) {
      return;
    }
    pumping = true;
    try {
      long now = SystemClock.uptimeMillis();
      long wait = pausedUntil - now;
      boolean telegramOnly = now < serviceUntil;
      String language = targetLanguage();
      int free = MAX_REQUESTS - requests;
      List<Request> ready = null;
      for (Iterator<TGMessage> it = queue.iterator(); it.hasNext(); ) {
        TGMessage m = it.next();
        if (m.xgramAutoTranslateState != STATE_QUEUED) {
          it.remove();
          continue;
        }
        if (!m.isAttachedToView() || !m.manager().xgramAutoTranslate()) {
          it.remove();
          m.xgramAutoTranslateState = STATE_NONE; // translated when it comes on screen again
          continue;
        }
        if (m.isTranslated()) {
          it.remove();
          m.xgramAutoTranslateState = STATE_SKIPPED; // the user translated it meanwhile
          continue;
        }
        boolean sent = !m.xgramIsAutoTranslateCached(language);
        if (sent && (wait > 0 || free <= 0)) {
          continue;
        }
        it.remove();
        if (sent) {
          free--;
        }
        if (ready == null) {
          ready = new ArrayList<>();
        }
        ready.add(new Request(m, ++m.xgramAutoTranslateToken, language, telegramOnly, sent));
      }
      if (ready != null) {
        for (Request r : ready) {
          r.message.xgramAutoTranslateState = STATE_REQUESTED;
          if (r.sent) {
            requests++;
            UI.post(r, REQUEST_TIMEOUT);
          }
          r.message.xgramAutoTranslate(r.language, r.telegramOnly, (error, errorText, notice, sent) -> onDone(r, error, errorText, notice, sent));
        }
      }
      if (wait > 0 && !queue.isEmpty() && !pumpScheduled) {
        pumpScheduled = true;
        UI.post(() -> {
          pumpScheduled = false;
          pump();
        }, wait);
      }
    } finally {
      pumping = false;
    }
  }

  private static void onDone (Request r, @Nullable TdApi.Error error, @Nullable String errorText, @Nullable String notice, boolean sent) {
    release(r);
    long now = SystemClock.uptimeMillis();
    if (error != null) {
      // every error pauses the queue, longer each time or as long as asked, so a failing service isn't flooded
      backoff = backoff == 0 ? FIRST_BACKOFF : Math.min(MAX_BACKOFF, backoff * 2);
      pausedUntil = Math.max(pausedUntil, now + Math.max(backoff, retryAfter(error)));
    } else if (sent) {
      backoff = 0; // a request went through (not a cached translation)
      if (notice != null) {
        // the chosen service failed and Telegram translated instead: Telegram alone for a while, longer each time
        serviceBackoff = serviceBackoff == 0 ? FIRST_SERVICE_BACKOFF : Math.min(MAX_SERVICE_BACKOFF, serviceBackoff * 2);
        serviceUntil = now + serviceBackoff;
      } else if (!r.telegramOnly) {
        serviceBackoff = 0;
      }
    }
    TGMessage m = r.message;
    if (m.xgramAutoTranslateToken == r.token && m.xgramAutoTranslateState == STATE_REQUESTED) {
      if (!StringUtils.equalsOrBothEmpty(m.getCurrentTranslatedLanguage(), r.language)) {
        // the user showed the original or picked another language meanwhile, or there was nothing to translate
        m.xgramAutoTranslateState = STATE_SKIPPED;
        if (m.getCurrentTranslatedLanguage() == null && m.isTranslated()) {
          m.stopTranslated();
        }
      } else if (error == null) {
        m.xgramAutoTranslateState = STATE_DONE;
        MessagesController c = m.messagesController();
        if (c != null && m.isTranslated()) {
          c.xgramOnAutoTranslated(m.getOriginalMessageLanguage());
        }
      } else {
        m.stopTranslated();
        if (isTemporary(error) && m.isAttachedToView()) {
          m.xgramAutoTranslateState = STATE_QUEUED;
          queue.addFirst(m);
        } else {
          m.xgramAutoTranslateState = isTemporary(error) ? STATE_NONE : STATE_FAILED;
        }
      }
    }
    showError(m, error != null ? errorText : notice);
    pump();
  }

  /** Too many requests, a quota, a server or network error: worth trying again later. */
  private static boolean isTemporary (TdApi.Error error) {
    String message = error.message != null ? error.message.toUpperCase(Locale.ROOT) : "";
    return error.code == 429 || error.code == 420 || error.code >= 500 || error.code <= 0 || message.contains("FLOOD") || message.contains("TOO MANY") || message.contains("QUOTA") || message.contains("RETRY");
  }

  /** "Too Many Requests: retry after 17" or FLOOD_WAIT_17, in milliseconds, otherwise 0. */
  private static long retryAfter (TdApi.Error error) {
    String message = error.message != null ? error.message.toUpperCase(Locale.ROOT) : "";
    int start = -1;
    for (String marker : new String[] {"RETRY AFTER ", "FLOOD_WAIT_"}) {
      int index = message.indexOf(marker);
      if (index != -1) {
        start = index + marker.length();
        break;
      }
    }
    if (start == -1) {
      return 0;
    }
    int end = start;
    while (end < message.length() && end - start < 6 && Character.isDigit(message.charAt(end))) {
      end++;
    }
    return end > start ? Long.parseLong(message.substring(start, end)) * 1000L : 0;
  }

  /** One message at most every minute, however many translations failed. */
  private static void showError (TGMessage m, @Nullable String text) {
    if (StringUtils.isEmpty(text)) {
      return;
    }
    long now = SystemClock.uptimeMillis();
    if (lastErrorTime != 0 && now - lastErrorTime < ERROR_INTERVAL) {
      return;
    }
    lastErrorTime = now;
    MessagesController c = m.messagesController();
    if (c != null && c.xgramShowAutoTranslateError(text)) {
      return;
    }
    UI.showToast(text, Toast.LENGTH_SHORT);
  }
}
