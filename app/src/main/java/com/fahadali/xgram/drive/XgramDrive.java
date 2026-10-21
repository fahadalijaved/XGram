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
package com.fahadali.xgram.drive;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.filegen.TdlibFileGenerationManager;
import org.thunderdog.challegram.filegen.ThumbGenerationInfo;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;

/**
 * XGram Drive: the account's files in Telegram's cloud, kept in a private channel only it can see ("XGram Drive",
 * archived and muted), one post per file. A file's caption is its path in the drive ("/Photos/2026/IMG_1.jpg"); a file
 * posted there some other way (no path) sits at the top with its own name. An empty folder is a text post
 * ("📁 /Photos/2026"). The channel's description carries {@link #MARKER}, so a new install finds the drive again.
 * Channel admins edit their posts with no time limit (TDLib's can_edit_message), so renames and moves are caption
 * edits; a post that can't be edited (a forward) is copied with its new path and the original deleted.
 *
 * One instance per account. Everything here runs on the UI thread: TDLib's results and updates are posted to it.
 */
public final class XgramDrive implements MessageListener {
  /** The channel's title (a name: not translated). */
  public static final String TITLE = "XGram Drive";
  /** In the channel's description: how a new install finds the drive. */
  public static final String MARKER = "xgram-drive:v1";
  private static final String FOLDER_MARK = "📁"; // 📁

  public static final int STATE_IDLE = 0, STATE_FINDING = 1, STATE_NONE = 2, STATE_CREATING = 3, STATE_LOADING = 4, STATE_READY = 5, STATE_ERROR = 6;
  public static final int SORT_NAME = 0, SORT_DATE = 1, SORT_SIZE = 2, SORT_TYPE = 3;

  private static final String KEY_SORT = "xgram_drive_sort";
  private static final int PAGE_SIZE = 100;
  private static final int DELETE_CHUNK = 100;
  private static final long REFRESH_AFTER_MS = 60_000L;
  private static final int MAX_NAME_LENGTH = 128;
  /** A caption holds 1024 characters (more with Premium): paths stay under that. */
  public static final int MAX_PATH_LENGTH = 1000;
  /** The error a step reports for a path over {@link #MAX_PATH_LENGTH}. */
  public static final String ERROR_PATH_TOO_LONG = "XGRAM_DRIVE_PATH_TOO_LONG";

  public interface Listener {
    void onDriveChanged ();
  }

  public interface Done {
    /** {@code failed}: how many of the items didn't go through (0: all did); {@code error}: the last error. */
    void onDone (int failed, @Nullable TdApi.Error error);
  }

  /** A post of the drive: a file, or the marker of a folder. */
  public static final class Entry {
    public final TdApi.Message message;
    /** The folder the file is in ("/" at the top); for a marker, the folder its own folder is in. */
    public final String folder;
    /** The file's name; for a marker, its folder's name. */
    public final String name;
    /** The file as a document (made from the media of a photo, a video… post); null for a marker. */
    public final @Nullable TdApi.Document document;
    public final boolean isFolder;

    Entry (TdApi.Message message, String folder, String name, @Nullable TdApi.Document document, boolean isFolder) {
      this.message = message;
      this.folder = folder;
      this.name = name;
      this.document = document;
      this.isFolder = isFolder;
    }

    public long id () {
      return message.id;
    }

    /** The file's path ("/Photos/IMG_1.jpg"); for a marker, its folder's path. */
    public String path () {
      return join(folder, name);
    }

    public @Nullable TdApi.File file () {
      return document != null ? document.document : null;
    }

    public long size () {
      TdApi.File file = file();
      return file != null ? Math.max(file.size, file.expectedSize) : 0;
    }

    public String mimeType () {
      return document != null && !StringUtils.isEmpty(document.mimeType) ? document.mimeType : "application/octet-stream";
    }

    public boolean isSending () {
      return message.sendingState != null && message.sendingState.getConstructor() == TdApi.MessageSendingStatePending.CONSTRUCTOR;
    }

    public boolean isFailed () {
      return message.sendingState != null && message.sendingState.getConstructor() == TdApi.MessageSendingStateFailed.CONSTRUCTOR;
    }
  }

  /** A folder as listed: its path, and what it holds all the way down. */
  public static final class Folder {
    public final String path;
    public int files;
    public long size;
    /** The newest file's date (unix seconds), 0: none. */
    public int date;

    Folder (String path) {
      this.path = path;
    }

    public String name () {
      return nameOf(path);
    }
  }

  // Instances

  private static final SparseArray<XgramDrive> drives = new SparseArray<>();

  /** The drive of {@code tdlib}'s account (UI thread). */
  public static XgramDrive of (Tdlib tdlib) {
    XgramDrive drive = drives.get(tdlib.id());
    if (drive == null || drive.tdlib != tdlib) {
      if (drive != null) {
        drive.release();
      }
      drive = new XgramDrive(tdlib);
      drives.put(tdlib.id(), drive);
    }
    drive.checkAccount();
    return drive;
  }

  /** TDLib thread: the account in this slot logged out (XgramAccountData erases its saved chat). */
  public static void forgetAccount (int accountId) {
    UI.post(() -> {
      XgramDrive drive = drives.get(accountId);
      if (drive != null) {
        drives.remove(accountId);
        drive.release();
      }
    });
  }

  private final Tdlib tdlib;
  private long userId;
  private int state = STATE_IDLE;
  private long chatId, subscribedChatId;
  private @Nullable TdApi.Error error;
  private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
  private final List<Listener> listeners = new ArrayList<>();
  private int generation, loadedPosts, screens;
  private long loadedAt;
  private boolean loading, notifyPosted;
  private final Set<Long> deletedWhileLoading = new HashSet<>();
  /** Posts that changed while a load read the pages: their live state wins over the pages'. */
  private final Set<Long> touchedWhileLoading = new HashSet<>();
  /** A lookup that failed while finding the drive: "no drive" can't be told then (Retry, not Set up). */
  private @Nullable TdApi.Error lookupError;
  /** Copies (saved from a chat, or made for a move): the path they get once posted, shown meanwhile. */
  private final Map<Long, String> pendingPaths = new HashMap<>();
  /** Copies made for a move: the original to delete once the copy is posted. */
  private final Map<Long, Long> deleteAfterSend = new HashMap<>();
  /** Uploads: the picked file whose access is kept until it is up. */
  private final Map<Long, Uri> uploads = new HashMap<>();
  /** Negative keys of pendingPaths: names held for copies not yet sent. */
  private long reservations;

  private XgramDrive (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  private String chatKey () {
    return "xgram_drive_" + tdlib.id() + "_chat";
  }

  /** A new login in this account slot gets its own drive. */
  private void checkAccount () {
    long me = tdlib.myUserId();
    if (me == 0) {
      return;
    }
    if (userId != 0 && userId != me) {
      reset();
    }
    userId = me;
  }

  private void reset () {
    generation++;
    closeChat();
    unsubscribe();
    chatId = 0;
    entries.clear();
    pendingPaths.clear();
    deleteAfterSend.clear();
    for (Uri uri : uploads.values()) {
      releaseAccess(uri);
    }
    uploads.clear();
    deletedWhileLoading.clear();
    touchedWhileLoading.clear();
    loading = false;
    loadedAt = 0;
    setState(STATE_IDLE, null);
  }

  private void release () {
    generation++;
    closeChat();
    unsubscribe();
    listeners.clear();
  }

  // State

  public Tdlib tdlib () {
    return tdlib;
  }

  public int state () {
    return state;
  }

  public long chatId () {
    return chatId;
  }

  public @Nullable TdApi.Error error () {
    return error;
  }

  /** How many posts a load in progress has read. */
  public int loadedPosts () {
    return loadedPosts;
  }

  /** Reading the channel again while the drive is shown. */
  public boolean isRefreshing () {
    return loading && state == STATE_READY;
  }

  public void addListener (Listener listener) {
    if (!listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private void setState (int state, @Nullable TdApi.Error error) {
    this.state = state;
    this.error = error;
    scheduleNotify();
  }

  /** Listeners hear once per frame however many posts changed (an upload of fifty files). */
  private void scheduleNotify () {
    if (notifyPosted) {
      return;
    }
    notifyPosted = true;
    UI.post(() -> {
      notifyPosted = false;
      for (Listener listener : new ArrayList<>(listeners)) {
        listener.onDriveChanged();
      }
    });
  }

  /** A Drive screen is shown: while any is, TDLib keeps the channel up to date (OpenChat). */
  public void onScreenShown () {
    if (screens++ == 0 && chatId != 0) {
      tdlib.client().send(new TdApi.OpenChat(chatId), tdlib.silentHandler());
    }
  }

  public void onScreenHidden () {
    if (screens > 0 && --screens == 0) {
      closeChat();
    }
  }

  private void closeChat () {
    if (screens == 0 && chatId != 0) {
      tdlib.client().send(new TdApi.CloseChat(chatId), tdlib.silentHandler());
    }
  }

  // Finding, creating and loading

  /** Finds or loads the drive when that hasn't happened yet; reads it again when it was loaded a while ago. */
  public void start () {
    checkAccount();
    if (state == STATE_IDLE || (state == STATE_ERROR && chatId == 0)) {
      find();
    } else if (state == STATE_ERROR) {
      load();
    } else if (state == STATE_READY && !loading && SystemClock.uptimeMillis() - loadedAt > REFRESH_AFTER_MS) {
      load();
    }
  }

  /** Reads the channel again (Refresh, Retry). */
  public void refresh () {
    if (chatId != 0) {
      if (!loading) {
        load();
      }
    } else if (state != STATE_FINDING && state != STATE_CREATING) {
      find();
    }
  }

  private void find () {
    final int gen = ++generation;
    lookupError = null;
    setState(STATE_FINDING, null);
    final long saved = Settings.instance().pmc().getLong(chatKey(), 0);
    if (saved != 0) {
      check(saved, ok -> {
        if (gen != generation) {
          return;
        }
        if (ok) {
          use(saved);
        } else {
          Settings.instance().pmc().remove(chatKey());
          search(gen, false);
        }
      });
    } else {
      search(gen, false);
    }
  }

  /** Whether {@code chatId} is a channel this account created and is still in. */
  private void check (long chatId, RunnableBool after) {
    tdlib.send(new TdApi.GetChat(chatId), (chat, error) -> {
      if (chat == null || chat.type.getConstructor() != TdApi.ChatTypeSupergroup.CONSTRUCTOR || !((TdApi.ChatTypeSupergroup) chat.type).isChannel) {
        UI.post(() -> after.runWithBool(false));
        return;
      }
      tdlib.send(new TdApi.GetSupergroup(((TdApi.ChatTypeSupergroup) chat.type).supergroupId), (supergroup, error2) -> {
        boolean ok = supergroup != null && supergroup.status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR && ((TdApi.ChatMemberStatusCreator) supergroup.status).isMember;
        UI.post(() -> after.runWithBool(ok));
      });
    });
  }

  /** Channels titled like the drive, among known chats and then on the server; the first with the marker is it. */
  private void search (int gen, boolean onServer) {
    TdApi.Function<TdApi.Chats> function = onServer ? new TdApi.SearchChatsOnServer(TITLE, null, 50) : new TdApi.SearchChats(TITLE, null, 50);
    tdlib.send(function, (chats, error) -> UI.post(() -> {
      if (gen != generation) {
        return;
      }
      if (chats == null && error != null) {
        lookupError = error;
      }
      probe(gen, chats != null ? chats.chatIds : new long[0], 0, onServer);
    }));
  }

  private void probe (int gen, long[] chatIds, int index, boolean onServer) {
    if (gen != generation) {
      return;
    }
    if (index >= chatIds.length) {
      if (!onServer) {
        search(gen, true);
      } else if (lookupError != null) {
        setState(STATE_ERROR, lookupError); // a lookup failed: Retry rather than make a second drive
      } else {
        setState(STATE_NONE, null);
      }
      return;
    }
    final long chatId = chatIds[index];
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null || chat.type.getConstructor() != TdApi.ChatTypeSupergroup.CONSTRUCTOR || !((TdApi.ChatTypeSupergroup) chat.type).isChannel) {
      probe(gen, chatIds, index + 1, onServer);
      return;
    }
    tdlib.send(new TdApi.GetSupergroupFullInfo(((TdApi.ChatTypeSupergroup) chat.type).supergroupId), (info, error) -> UI.post(() -> {
      if (gen != generation) {
        return;
      }
      if (info == null && error != null) {
        lookupError = error;
      }
      if (info != null && info.description != null && info.description.contains(MARKER)) {
        check(chatId, ok -> {
          if (gen != generation) {
            return;
          }
          if (ok) {
            use(chatId);
          } else {
            probe(gen, chatIds, index + 1, onServer);
          }
        });
      } else {
        probe(gen, chatIds, index + 1, onServer);
      }
    }));
  }

  /** Makes the drive's channel (the intro's button): private, archived and muted. */
  public void create (Done done) {
    if (state != STATE_NONE && state != STATE_ERROR) {
      return;
    }
    final int gen = ++generation;
    setState(STATE_CREATING, null);
    String about = Lang.getString(R.string.XgramDriveChannelAbout);
    if (about.length() > 200) {
      about = about.substring(0, 200);
    }
    about = about + "\n\n" + MARKER;
    tdlib.send(new TdApi.CreateNewSupergroupChat(TITLE, false, true, about, null, 0, false), (chat, error) -> UI.post(() -> {
      if (gen != generation) {
        return;
      }
      if (chat == null) {
        setState(STATE_ERROR, error);
        done.onDone(1, error);
        return;
      }
      tdlib.send(new TdApi.AddChatToList(chat.id, new TdApi.ChatListArchive()), (ok, archiveError) -> { });
      tdlib.setMuteFor(chat.id, TdlibUi.MUTE_FOREVER);
      use(chat.id);
      done.onDone(0, null);
    }));
  }

  private void use (long chatId) {
    this.chatId = chatId;
    Settings.instance().pmc().putLong(chatKey(), chatId);
    if (subscribedChatId != chatId) {
      unsubscribe();
      subscribedChatId = chatId;
      tdlib.listeners().subscribeToMessageUpdates(chatId, this);
    }
    if (screens > 0) {
      tdlib.client().send(new TdApi.OpenChat(chatId), tdlib.silentHandler());
    }
    load();
  }

  private void unsubscribe () {
    if (subscribedChatId != 0) {
      tdlib.listeners().unsubscribeFromMessageUpdates(subscribedChatId, this);
      subscribedChatId = 0;
    }
  }

  private void load () {
    final int gen = ++generation;
    loading = true;
    loadedPosts = 0;
    deletedWhileLoading.clear();
    touchedWhileLoading.clear();
    if (state != STATE_READY) {
      setState(STATE_LOADING, null);
    } else {
      scheduleNotify();
    }
    loadPage(gen, chatId, 0, new LinkedHashMap<>(), new HashSet<>());
  }

  /** The channel's posts, newest first, 100 at a time, until a page brings nothing new. */
  private void loadPage (int gen, long chatId, long fromMessageId, LinkedHashMap<Long, Entry> found, Set<Long> seen) {
    tdlib.send(new TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE_SIZE, false), (messages, error) -> UI.post(() -> {
      if (gen != generation) {
        return;
      }
      if (messages == null) {
        loadFailed(gen, chatId, error);
        return;
      }
      long oldest = fromMessageId;
      int added = 0;
      for (TdApi.Message message : messages.messages) {
        if (message == null || !seen.add(message.id)) {
          continue;
        }
        added++;
        if (oldest == 0 || message.id < oldest) {
          oldest = message.id;
        }
        Entry entry = parse(message);
        if (entry != null) {
          found.put(message.id, entry);
        }
      }
      loadedPosts = seen.size();
      if (added == 0) {
        finishLoad(found);
      } else {
        scheduleNotify();
        loadPage(gen, chatId, oldest, found, seen);
      }
    }));
  }

  private void loadFailed (int gen, long chatId, @Nullable TdApi.Error error) {
    loading = false;
    if (state == STATE_READY) {
      scheduleNotify(); // a refresh failed: what is shown stays
      return;
    }
    // The channel may be gone (deleted, left): then the drive is found again or made anew
    check(chatId, ok -> {
      if (gen != generation) {
        return;
      }
      if (ok) {
        setState(STATE_ERROR, error);
      } else {
        Settings.instance().pmc().remove(chatKey());
        closeChat();
        unsubscribe();
        this.chatId = 0;
        entries.clear();
        find();
      }
    });
  }

  private void finishLoad (LinkedHashMap<Long, Entry> found) {
    // What changed while the pages were read wins over what they said: posts sent, edited, moved or deleted meanwhile
    for (long id : deletedWhileLoading) {
      found.remove(id);
    }
    for (long id : touchedWhileLoading) {
      Entry live = entries.get(id);
      if (live != null) {
        found.put(id, live);
      } else {
        found.remove(id);
      }
    }
    deletedWhileLoading.clear();
    touchedWhileLoading.clear();
    entries.clear();
    entries.putAll(found);
    loading = false;
    loadedAt = SystemClock.uptimeMillis();
    setState(STATE_READY, null);
  }

  // Updates of the channel (TDLib thread)

  @Override
  public void onNewMessage (TdApi.Message message) {
    UI.post(() -> {
      if (message.chatId == chatId) {
        put(message);
      }
    });
  }

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    UI.post(() -> {
      if (message.chatId != chatId) {
        return;
      }
      entries.remove(oldMessageId);
      if (loading) {
        deletedWhileLoading.add(oldMessageId);
      }
      releaseAccess(uploads.remove(oldMessageId));
      String path = pendingPaths.remove(oldMessageId);
      Long original = deleteAfterSend.remove(oldMessageId);
      if (path != null) {
        pendingPaths.put(message.id, path);
        if (original != null) {
          deleteAfterSend.put(message.id, original); // deleted once the copy has its path
        }
        applyPath(message.id, path);
      }
      put(message);
    });
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
    UI.post(() -> {
      if (message.chatId != chatId) {
        return;
      }
      entries.remove(oldMessageId);
      if (loading) {
        deletedWhileLoading.add(oldMessageId);
      }
      // Kept for Retry
      moveKey(uploads, oldMessageId, message.id);
      moveKey(pendingPaths, oldMessageId, message.id);
      moveKey(deleteAfterSend, oldMessageId, message.id);
      put(message);
    });
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    UI.post(() -> {
      if (chatId != this.chatId) {
        return;
      }
      Entry entry = entries.get(messageId);
      if (entry == null) {
        return;
      }
      entry.message.content = newContent;
      String pending = pendingPaths.get(messageId);
      if (pending != null) {
        TdApi.FormattedText caption = Td.textOrCaption(newContent);
        if (caption != null && pending.equals(pathOf(caption.text))) {
          pendingPaths.remove(messageId);
        }
      }
      put(entry.message);
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    UI.post(() -> {
      if (chatId != this.chatId) {
        return;
      }
      boolean changed = false;
      for (long messageId : messageIds) {
        if (entries.remove(messageId) != null) {
          changed = true;
        }
        if (loading) {
          deletedWhileLoading.add(messageId);
        }
        releaseAccess(uploads.remove(messageId)); // a cancelled or removed upload (Retry takes its own out first)
        pendingPaths.remove(messageId);
        deleteAfterSend.remove(messageId);
      }
      if (changed) {
        scheduleNotify();
      }
    });
  }

  private static <T> void moveKey (Map<Long, T> map, long from, long to) {
    T value = map.remove(from);
    if (value != null) {
      map.put(to, value);
    }
  }

  private void put (TdApi.Message message) {
    Entry entry = parse(message);
    if (entry != null) {
      entries.put(message.id, entry);
    } else {
      entries.remove(message.id);
    }
    if (loading) {
      touchedWhileLoading.add(message.id);
    }
    scheduleNotify();
  }

  // Lists

  public @Nullable Entry entry (long messageId) {
    return entries.get(messageId);
  }

  /** The files right in {@code folder}, sorted. */
  public List<Entry> filesIn (String folder, int sort) {
    List<Entry> list = new ArrayList<>();
    for (Entry entry : entries.values()) {
      if (!entry.isFolder && entry.folder.equals(folder)) {
        list.add(entry);
      }
    }
    sortFiles(list, sort);
    return list;
  }

  /** The folders right in {@code folder}, by name, with what each holds all the way down. */
  public List<Folder> foldersIn (String folder) {
    Map<String, Folder> map = new HashMap<>();
    for (Entry entry : entries.values()) {
      String child = childOf(folder, entry.isFolder ? entry.path() : entry.folder);
      if (child == null) {
        continue;
      }
      Folder item = map.get(child);
      if (item == null) {
        item = new Folder(child);
        map.put(child, item);
      }
      if (!entry.isFolder) {
        item.files++;
        item.size += entry.size();
        item.date = Math.max(item.date, entry.message.date);
      }
    }
    List<Folder> list = new ArrayList<>(map.values());
    Collections.sort(list, (a, b) -> compareNames(a.name(), b.name()));
    return list;
  }

  /** Every file whose name has {@code query}, anywhere in the drive. */
  public List<Entry> searchFiles (String query, int sort) {
    String q = query.trim().toLowerCase(Locale.ROOT);
    List<Entry> list = new ArrayList<>();
    if (q.isEmpty()) {
      return list;
    }
    for (Entry entry : entries.values()) {
      if (!entry.isFolder && entry.name.toLowerCase(Locale.ROOT).contains(q)) {
        list.add(entry);
      }
    }
    sortFiles(list, sort);
    return list;
  }

  /** Every folder whose name has {@code query}, with what it holds. */
  public List<Folder> searchFolders (String query) {
    String q = query.trim().toLowerCase(Locale.ROOT);
    List<Folder> list = new ArrayList<>();
    if (q.isEmpty()) {
      return list;
    }
    Map<String, Folder> map = new HashMap<>();
    for (Entry entry : entries.values()) {
      // Every folder on the way to this post
      String path = entry.isFolder ? entry.path() : entry.folder;
      while (!"/".equals(path)) {
        if (nameOf(path).toLowerCase(Locale.ROOT).contains(q)) {
          Folder item = map.get(path);
          if (item == null) {
            item = new Folder(path);
            map.put(path, item);
          }
          if (!entry.isFolder) {
            item.files++;
            item.size += entry.size();
            item.date = Math.max(item.date, entry.message.date);
          }
        }
        path = parentOf(path);
      }
    }
    list.addAll(map.values());
    Collections.sort(list, (a, b) -> compareNames(a.name(), b.name()));
    return list;
  }

  public boolean folderExists (String path) {
    if ("/".equals(path)) {
      return true;
    }
    for (Entry entry : entries.values()) {
      String p = entry.isFolder ? entry.path() : entry.folder;
      if (p.equals(path) || isInside(p, path)) {
        return true;
      }
    }
    return false;
  }

  /** Every file and marker in {@code folder} or below it. */
  public List<Entry> entriesUnder (String folder) {
    List<Entry> list = new ArrayList<>();
    for (Entry entry : entries.values()) {
      String p = entry.isFolder ? entry.path() : entry.folder;
      if (p.equals(folder) || isInside(p, folder)) {
        list.add(entry);
      }
    }
    return list;
  }

  public int fileCount () {
    int count = 0;
    for (Entry entry : entries.values()) {
      if (!entry.isFolder) {
        count++;
      }
    }
    return count;
  }

  public long totalSize () {
    long size = 0;
    for (Entry entry : entries.values()) {
      if (!entry.isFolder) {
        size += entry.size();
      }
    }
    return size;
  }

  public static int sortOrder () {
    int sort = Settings.instance().pmc().getInt(KEY_SORT, SORT_NAME);
    return sort >= SORT_NAME && sort <= SORT_TYPE ? sort : SORT_NAME;
  }

  public static void setSortOrder (int sort) {
    Settings.instance().pmc().putInt(KEY_SORT, sort);
  }

  private static void sortFiles (List<Entry> list, int sort) {
    Comparator<Entry> byName = (a, b) -> compareNames(a.name, b.name);
    switch (sort) {
      case SORT_DATE:
        Collections.sort(list, (a, b) -> {
          int c = Integer.compare(b.message.date, a.message.date);
          return c != 0 ? c : Long.compare(b.id(), a.id());
        });
        break;
      case SORT_SIZE:
        Collections.sort(list, (a, b) -> {
          int c = Long.compare(b.size(), a.size());
          return c != 0 ? c : byName.compare(a, b);
        });
        break;
      case SORT_TYPE:
        Collections.sort(list, (a, b) -> {
          int c = extensionOf(a.name).compareTo(extensionOf(b.name));
          return c != 0 ? c : byName.compare(a, b);
        });
        break;
      default:
        Collections.sort(list, byName);
        break;
    }
  }

  /** Names as people read them: case aside, numbers by value ("IMG_2" before "IMG_10"). */
  public static int compareNames (String a, String b) {
    int i = 0, j = 0;
    final int na = a.length(), nb = b.length();
    while (i < na && j < nb) {
      char ca = a.charAt(i), cb = b.charAt(j);
      if (Character.isDigit(ca) && Character.isDigit(cb)) {
        int si = i, sj = j;
        while (si < na && a.charAt(si) == '0') {
          si++;
        }
        while (sj < nb && b.charAt(sj) == '0') {
          sj++;
        }
        int ei = si, ej = sj;
        while (ei < na && Character.isDigit(a.charAt(ei))) {
          ei++;
        }
        while (ej < nb && Character.isDigit(b.charAt(ej))) {
          ej++;
        }
        if (ei - si != ej - sj) {
          return (ei - si) - (ej - sj);
        }
        for (int k = 0; k < ei - si; k++) {
          int d = a.charAt(si + k) - b.charAt(sj + k);
          if (d != 0) {
            return d;
          }
        }
        i = ei;
        j = ej;
      } else {
        int d = Character.toLowerCase(Character.toUpperCase(ca)) - Character.toLowerCase(Character.toUpperCase(cb));
        if (d != 0) {
          return d;
        }
        i++;
        j++;
      }
    }
    return (na - i) - (nb - j);
  }

  private static String extensionOf (String name) {
    int i = name.lastIndexOf('.');
    return i > 0 && i < name.length() - 1 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
  }

  // Changes

  /** Uploads picked files (content uris) into {@code folder}, as documents: originals, names kept. */
  public void upload (List<Uri> uris, String folder, @Nullable RunnableData<String> notice) {
    if (chatId == 0 || uris.isEmpty()) {
      return;
    }
    final long chatId = this.chatId;
    final String target = normalize(folder);
    final long maxSize = (tdlib.hasPremium() ? 4000L : 2000L) << 20;
    final Set<String> taken = namesIn(target);
    new Thread(() -> {
      final List<TdApi.InputMessageContent> contents = new ArrayList<>(uris.size());
      final List<Uri> sources = new ArrayList<>(uris.size());
      int tooBig = 0, unreadable = 0, tooDeep = 0;
      for (Uri uri : uris) {
        Picked picked = describe(uri);
        if (picked == null) {
          unreadable++;
          continue;
        }
        if (picked.size > maxSize) {
          tooBig++;
          continue;
        }
        if (join(target, picked.name).length() > MAX_PATH_LENGTH) {
          tooDeep++;
          continue;
        }
        keepAccess(uri);
        String name = uniqueName(picked.name, taken);
        long size = Math.max(0, picked.size);
        // Telegram X's content conversion: the file generator copies the stream while TDLib uploads it
        TdApi.InputFile file = new TdApi.InputFileGenerated(name, uri + "," + size + "_" + picked.modified, size);
        TdApi.InputThumbnail thumbnail = thumbnailOf(uri, picked.mimeType);
        contents.add(new TdApi.InputMessageDocument(new TdApi.InputDocument(file, thumbnail, true), caption(join(target, name))));
        sources.add(uri);
      }
      final int tooBigCount = tooBig, unreadableCount = unreadable, tooDeepCount = tooDeep;
      UI.post(() -> {
        for (int i = 0; i < contents.size(); i++) {
          send(chatId, contents.get(i), sources.get(i));
        }
        if (notice != null && tooDeepCount > 0) {
          notice.runWithData(Lang.getString(R.string.XgramDrivePathTooLong));
        } else if (notice != null && tooBigCount > 0) {
          notice.runWithData(Lang.getString(R.string.XgramDriveTooBig, Lang.plural(R.string.xFiles, tooBigCount), Strings.buildSize(maxSize)));
        } else if (notice != null && unreadableCount > 0) {
          notice.runWithData(Lang.getString(R.string.XgramDriveUnreadable, Lang.plural(R.string.xFiles, unreadableCount)));
        }
      });
    }, "XgramDriveUpload").start();
  }

  private static TdApi.MessageSendOptions sendOptions () {
    return new TdApi.MessageSendOptions(null, true, false, false, false, 0, false, null, 0, 0, false);
  }

  private void send (long chatId, TdApi.InputMessageContent content, @Nullable Uri source) {
    tdlib.send(new TdApi.SendMessage(chatId, null, null, sendOptions(), null, content), (message, error) -> UI.post(() -> {
      if (message == null) {
        releaseAccess(source);
        if (error != null) {
          UI.showError(error);
        }
        return;
      }
      if (source != null) {
        uploads.put(message.id, source);
      }
      if (message.chatId == this.chatId) {
        put(message);
      }
    }));
  }

  /** Makes an empty folder (a marker post); nothing when it is already there. */
  public void createFolder (String parent, String name, Done done) {
    String clean = cleanName(name);
    if (clean == null || chatId == 0) {
      done.onDone(1, null);
      return;
    }
    String path = join(normalize(parent), clean);
    if (folderExists(path)) {
      done.onDone(0, null);
      return;
    }
    if (path.length() > MAX_PATH_LENGTH) {
      done.onDone(1, new TdApi.Error(400, ERROR_PATH_TOO_LONG));
      return;
    }
    runSteps(Collections.singletonList(markerStep(path)), done);
  }

  public void renameFile (Entry entry, String newName, Done done) {
    String name = cleanName(newName);
    if (name == null || name.equals(entry.name)) {
      done.onDone(0, null);
      return;
    }
    runSteps(Collections.singletonList(pathStep(entry, join(entry.folder, name))), done);
  }

  /** Moves files into {@code target}; a name already there gets " (2)". Folders left empty stay. */
  public void moveFiles (Collection<Entry> files, String target, Done done) {
    final String to = normalize(target);
    final Set<String> taken = namesIn(to);
    final List<Step> steps = new ArrayList<>();
    final Set<String> sources = new HashSet<>();
    final List<Entry> moving = new ArrayList<>();
    for (Entry entry : files) {
      if (entry.isFolder || entry.folder.equals(to)) {
        continue;
      }
      moving.add(entry);
      sources.add(entry.folder);
      steps.add(pathStep(entry, join(to, uniqueName(entry.name, taken))));
    }
    steps.addAll(0, keepFolderSteps(sources, moving, Collections.emptySet()));
    runSteps(steps, done);
  }

  public void renameFolder (String path, String newName, Done done) {
    String name = cleanName(newName);
    if (name == null || "/".equals(path)) {
      done.onDone(0, null);
      return;
    }
    moveFolderTo(path, join(parentOf(path), name), done);
  }

  /** Moves a folder (and all in it) into {@code targetParent}; not into itself. */
  public void moveFolder (String path, String targetParent, Done done) {
    String to = normalize(targetParent);
    if ("/".equals(path) || to.equals(path) || isInside(to, path)) {
      done.onDone(0, null);
      return;
    }
    moveFolderTo(path, join(to, nameOf(path)), done);
  }

  private void moveFolderTo (String from, String to, Done done) {
    if (from.equals(to)) {
      done.onDone(0, null);
      return;
    }
    List<Entry> moving = entriesUnder(from);
    List<Step> steps = new ArrayList<>(keepFolderSteps(Collections.singleton(parentOf(from)), moving, Collections.emptySet()));
    for (Entry entry : moving) {
      steps.add(pathStep(entry, to + entry.path().substring(from.length())));
    }
    runSteps(steps, done);
  }

  /** Deletes files and folders (with everything in them) for good; their folders, left empty, stay. */
  public void delete (Collection<Entry> files, Collection<String> folders, Done done) {
    final LinkedHashMap<Long, Entry> doomed = new LinkedHashMap<>();
    final Set<String> sources = new HashSet<>();
    for (Entry entry : files) {
      doomed.put(entry.id(), entry);
      sources.add(entry.isFolder ? parentOf(entry.path()) : entry.folder);
    }
    for (String folder : folders) {
      for (Entry entry : entriesUnder(folder)) {
        doomed.put(entry.id(), entry);
      }
      sources.add(parentOf(folder));
    }
    if (doomed.isEmpty()) {
      done.onDone(0, null);
      return;
    }
    final long chatId = this.chatId;
    List<Step> steps = new ArrayList<>(keepFolderSteps(sources, doomed.values(), new HashSet<>(folders)));
    long[] ids = new long[doomed.size()];
    int i = 0;
    for (long id : doomed.keySet()) {
      ids[i++] = id;
    }
    for (int start = 0; start < ids.length; start += DELETE_CHUNK) {
      final long[] chunk = new long[Math.min(DELETE_CHUNK, ids.length - start)];
      System.arraycopy(ids, start, chunk, 0, chunk.length);
      steps.add(handler -> tdlib.send(new TdApi.DeleteMessages(chatId, chunk, true), handler::onResult));
    }
    runSteps(steps, done);
  }

  /** Saves a chat's post (a file, a photo, a video…) to {@code folder}: a copy, nothing is uploaded again. */
  public void saveFromChat (TdApi.Message source, String folder, Done done) {
    if (chatId == 0) {
      done.onDone(1, null);
      return;
    }
    final long chatId = this.chatId;
    final String path = join(normalize(folder), uniqueName(defaultName(source), namesIn(normalize(folder))));
    final long reservation = --reservations; // holds the name until the copy has its own id
    pendingPaths.put(reservation, path);
    tdlib.send(new TdApi.ForwardMessages(chatId, null, source.chatId, new long[] {source.id}, sendOptions(), true, true), (messages, error) -> UI.post(() -> {
      pendingPaths.remove(reservation);
      if (messages == null || messages.messages.length == 0) {
        done.onDone(1, error);
        return;
      }
      for (TdApi.Message copy : messages.messages) {
        if (copy != null) {
          pendingPaths.put(copy.id, path);
          if (copy.chatId == this.chatId) {
            put(copy);
          }
        }
      }
      done.onDone(0, null);
    }));
  }

  /** Whether a chat's post can be saved to the drive: a file, a photo, a video, a GIF, music or a voice message. */
  public static boolean canSave (@Nullable TdApi.Message message) {
    if (message == null || message.content == null || !message.canBeSaved || message.sendingState != null) {
      return false;
    }
    switch (message.content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  /** Sends a failed upload (or copy) again. */
  public void retry (Entry entry) {
    final long chatId = this.chatId;
    final long oldId = entry.id();
    // TDLib deletes the failed post before it answers: what it carries is taken out first, and given to the new post
    final Uri source = uploads.remove(oldId);
    final String path = pendingPaths.remove(oldId);
    final Long original = deleteAfterSend.remove(oldId);
    tdlib.send(new TdApi.ResendMessages(chatId, new long[] {oldId}, null, 0), (messages, error) -> UI.post(() -> {
      if (messages == null) {
        // Not sent again: the failed post stays as it was
        if (source != null) {
          uploads.put(oldId, source);
        }
        if (path != null) {
          pendingPaths.put(oldId, path);
        }
        if (original != null) {
          deleteAfterSend.put(oldId, original);
        }
        if (error != null) {
          UI.showError(error);
        }
        return;
      }
      for (TdApi.Message message : messages.messages) {
        if (message == null) {
          continue;
        }
        if (source != null) {
          uploads.put(message.id, source);
        }
        if (path != null) {
          pendingPaths.put(message.id, path);
        }
        if (original != null) {
          deleteAfterSend.put(message.id, original);
        }
        if (message.chatId == this.chatId) {
          put(message);
        }
      }
    }));
  }

  /**
   * A copy is up: it gets its path (shown meanwhile). A move's original goes once the copy has it; a move whose copy
   * can't take it keeps the original (the copy goes); a saved copy that can't stays at the top under its own name.
   */
  private void applyPath (long messageId, String path) {
    final long chatId = this.chatId;
    runSteps(Collections.singletonList(handler -> tdlib.send(new TdApi.EditMessageCaption(chatId, messageId, null, caption(path), false), (message, error) -> {
      putLater(message);
      handler.onResult(message, error);
    })), (failed, error) -> {
      Long original = deleteAfterSend.remove(messageId);
      if (failed == 0) {
        if (original != null) {
          tdlib.send(new TdApi.DeleteMessages(chatId, new long[] {original}, true), (ok, deleteError) -> { });
        }
        return;
      }
      pendingPaths.remove(messageId);
      if (original != null) {
        tdlib.send(new TdApi.DeleteMessages(chatId, new long[] {messageId}, true), (ok, deleteError) -> { });
      } else {
        Entry entry = entries.get(messageId);
        if (entry != null) {
          put(entry.message);
        }
      }
    });
  }

  /** An edited post as TDLib returned it (any thread): shown at once, its update may come later. */
  private void putLater (@Nullable TdApi.Message message) {
    if (message != null) {
      UI.post(() -> {
        if (message.chatId == chatId && entries.containsKey(message.id)) {
          put(message);
        }
      });
    }
  }

  // Steps: one request after another (edits are rate limited), waiting when the server asks to

  private interface Step {
    void run (Tdlib.ResultHandler<TdApi.Object> handler);
  }

  private void runSteps (List<Step> steps, @Nullable Done done) {
    runStep(steps, 0, 0, null, done);
  }

  private void runStep (List<Step> steps, int index, int failed, @Nullable TdApi.Error lastError, @Nullable Done done) {
    if (index >= steps.size()) {
      if (done != null) {
        done.onDone(failed, lastError);
      }
      return;
    }
    steps.get(index).run((result, error) -> UI.post(() -> {
      if (error != null && error.code == 429) {
        UI.post(() -> runStep(steps, index, failed, lastError, done), retryAfterMs(error));
        return;
      }
      boolean ok = error == null || isNotModified(error);
      runStep(steps, index + 1, ok ? failed : failed + 1, ok ? lastError : error, done);
    }));
  }

  private static boolean isNotModified (TdApi.Error error) {
    return error.message != null && error.message.contains("MESSAGE_NOT_MODIFIED");
  }

  /** "Too Many Requests: retry after 7": 7 seconds (at most 2 minutes). */
  private static long retryAfterMs (TdApi.Error error) {
    int seconds = 3;
    String message = error.message;
    int i = message != null ? message.lastIndexOf(' ') : -1;
    if (i != -1) {
      int value = toInt(message.substring(i + 1).trim(), 0);
      if (value > 0) {
        seconds = value;
      }
    }
    return Math.min(seconds, 120) * 1000L + 250L;
  }

  private static int toInt (@Nullable String value, int defaultValue) {
    if (StringUtils.isEmpty(value)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** The file (or marker) gets {@code newPath}: a caption edit, or for a forward (it can't be edited) a copy. */
  private Step pathStep (Entry entry, String newPath) {
    final long chatId = this.chatId;
    return handler -> {
      if (newPath.length() > MAX_PATH_LENGTH) {
        handler.onResult(null, new TdApi.Error(400, ERROR_PATH_TOO_LONG));
        return;
      }
      if (entry.isFolder) {
        tdlib.send(new TdApi.EditMessageText(chatId, entry.id(), null, markerContent(newPath)), (message, error) -> {
          putLater(message);
          handler.onResult(message, error);
        });
        return;
      }
      tdlib.send(new TdApi.EditMessageCaption(chatId, entry.id(), null, caption(newPath), false), (message, error) -> {
        if (message == null && error != null && error.code != 429 && !isNotModified(error) && entry.message.forwardInfo != null) {
          copyForMove(chatId, entry, newPath, handler);
          return;
        }
        putLater(message);
        handler.onResult(message, error);
      });
    };
  }

  /** A forward can't be edited: a copy of it gets the path, the original goes once the copy is up. */
  private void copyForMove (long chatId, Entry entry, String newPath, Tdlib.ResultHandler<TdApi.Object> handler) {
    tdlib.send(new TdApi.ForwardMessages(chatId, null, chatId, new long[] {entry.id()}, sendOptions(), true, true), (messages, error) -> {
      if (messages != null) {
        UI.post(() -> {
          for (TdApi.Message copy : messages.messages) {
            if (copy != null) {
              pendingPaths.put(copy.id, newPath);
              deleteAfterSend.put(copy.id, entry.id());
              if (copy.chatId == this.chatId) {
                put(copy);
              }
            }
          }
        });
      }
      handler.onResult(messages, error);
    });
  }

  private Step markerStep (String path) {
    final long chatId = this.chatId;
    return handler -> tdlib.send(new TdApi.SendMessage(chatId, null, null, sendOptions(), null, markerContent(path)), handler::onResult);
  }

  /**
   * A folder whose last post leaves would vanish (folders are their posts' paths): a marker keeps it, unless the
   * folder itself, or one it is in, is going too.
   */
  private List<Step> keepFolderSteps (Collection<String> folders, Collection<Entry> leaving, Collection<String> deletedFolders) {
    Set<Long> gone = new HashSet<>();
    for (Entry entry : leaving) {
      gone.add(entry.id());
    }
    List<Step> steps = new ArrayList<>();
    for (String folder : folders) {
      if ("/".equals(folder)) {
        continue;
      }
      boolean deleted = false;
      for (String deletedFolder : deletedFolders) {
        if (folder.equals(deletedFolder) || isInside(folder, deletedFolder)) {
          deleted = true;
          break;
        }
      }
      if (deleted) {
        continue;
      }
      boolean kept = false;
      for (Entry entry : entries.values()) {
        if (gone.contains(entry.id())) {
          continue;
        }
        String p = entry.isFolder ? entry.path() : entry.folder;
        if (p.equals(folder) || isInside(p, folder)) {
          kept = true;
          break;
        }
      }
      if (!kept) {
        steps.add(markerStep(folder));
      }
    }
    return steps;
  }

  /** Whether another file in {@code folder} is already named {@code name} (case aside). */
  public boolean fileNameTaken (String folder, String name, long exceptMessageId) {
    for (Entry entry : entries.values()) {
      if (!entry.isFolder && entry.id() != exceptMessageId && entry.folder.equals(folder) && entry.name.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Saves a file to Downloads under its name in the drive, downloading it first when needed; the download goes on
   * after the page that asked is closed.
   */
  public void saveToDownloads (Entry entry) {
    final TdApi.File file = entry.file();
    if (file == null || entry.isSending() || entry.isFailed()) {
      return;
    }
    final String name = entry.name, mimeType = entry.mimeType();
    final java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean();
    final org.thunderdog.challegram.telegram.TdlibFilesManager.FileListener[] listener = new org.thunderdog.challegram.telegram.TdlibFilesManager.FileListener[1];
    final RunnableData<TdApi.File> save = loaded -> {
      if (loaded != null && org.thunderdog.challegram.data.TD.isFileLoaded(loaded) && saved.compareAndSet(false, true)) {
        UI.post(() -> {
          tdlib.files().unsubscribe(file.id, listener[0]);
          org.thunderdog.challegram.data.TD.saveToDownloads(new java.io.File(loaded.local.path), mimeType, name);
        });
      }
    };
    listener[0] = new org.thunderdog.challegram.telegram.TdlibFilesManager.FileListener() {
      @Override
      public void onFileLoadProgress (TdApi.File file) { }

      @Override
      public void onFileLoadStateChanged (Tdlib tdlib, int fileId, int state, @Nullable TdApi.File downloadedFile) {
        if (state == org.thunderdog.challegram.telegram.TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
          save.runWithData(downloadedFile);
        } else if (state == org.thunderdog.challegram.telegram.TdlibFilesManager.STATE_FAILED) {
          UI.post(() -> tdlib.files().unsubscribe(fileId, listener[0]));
        }
      }
    };
    tdlib.files().subscribe(file, listener[0]);
    tdlib.files().downloadFile(file, com.fahadali.xgram.XgramDownloadBoost.tappedPriority(), (result, error) -> save.runWithData(result));
  }

  /** The names in {@code folder}, lowercase (copies on their way too: an album's photos share a date). */
  private Set<String> namesIn (String folder) {
    Set<String> names = new HashSet<>();
    for (Entry entry : entries.values()) {
      if (!entry.isFolder && entry.folder.equals(folder)) {
        names.add(entry.name.toLowerCase(Locale.ROOT));
      }
    }
    for (String path : pendingPaths.values()) {
      if (parentOf(path).equals(folder)) {
        names.add(nameOf(path).toLowerCase(Locale.ROOT));
      }
    }
    return names;
  }

  /** {@code name}, or "name (2).ext", "name (3).ext"… when {@code taken} has it; the result is taken from then on. */
  static String uniqueName (String name, Set<String> taken) {
    String result = name;
    if (taken.contains(result.toLowerCase(Locale.ROOT))) {
      int dot = name.lastIndexOf('.');
      String base = dot > 0 ? name.substring(0, dot) : name;
      String extension = dot > 0 ? name.substring(dot) : "";
      for (int n = 2; ; n++) {
        result = base + " (" + n + ")" + extension;
        if (!taken.contains(result.toLowerCase(Locale.ROOT))) {
          break;
        }
      }
    }
    taken.add(result.toLowerCase(Locale.ROOT));
    return result;
  }

  // Posts

  public static TdApi.FormattedText caption (String path) {
    return new TdApi.FormattedText(path, new TdApi.TextEntity[0]);
  }

  private static TdApi.InputMessageText markerContent (String path) {
    return new TdApi.InputMessageText(new TdApi.FormattedText(FOLDER_MARK + " " + path, new TdApi.TextEntity[0]), new TdApi.LinkPreviewOptions(true, "", false, false, false), false);
  }

  @Nullable
  private Entry parse (TdApi.Message message) {
    if (message.content == null) {
      return null;
    }
    if (message.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR) {
      String path = markerPathOf(((TdApi.MessageText) message.content).text.text);
      return path != null ? new Entry(message, parentOf(path), nameOf(path), null, true) : null;
    }
    String path = pendingPaths.get(message.id);
    if (path == null) {
      TdApi.FormattedText caption = Td.textOrCaption(message.content);
      path = pathOf(caption != null ? caption.text : null);
    }
    final String folder, name;
    if (path != null) {
      folder = parentOf(path);
      name = nameOf(path);
    } else {
      folder = "/";
      name = defaultName(message);
    }
    TdApi.Document document = documentOf(message.content, name);
    return document != null ? new Entry(message, folder, name, document, false) : null;
  }

  /** A caption that is a path ("/Photos/IMG_1.jpg"): the path; anything else (a note, nothing): null. */
  @Nullable
  static String pathOf (@Nullable String text) {
    if (text == null) {
      return null;
    }
    String t = text.trim();
    if (t.length() < 2 || t.charAt(0) != '/' || t.indexOf('\n') != -1 || t.endsWith("/")) {
      return null;
    }
    String path = normalize(t);
    return "/".equals(path) ? null : path;
  }

  /** "📁 /Photos/2026": the folder's path; anything else: null. */
  @Nullable
  static String markerPathOf (@Nullable String text) {
    if (text == null) {
      return null;
    }
    String t = text.trim();
    if (!t.startsWith(FOLDER_MARK)) {
      return null;
    }
    t = t.substring(FOLDER_MARK.length()).trim();
    if (t.isEmpty() || t.charAt(0) != '/' || t.indexOf('\n') != -1) {
      return null;
    }
    String path = normalize(t);
    return "/".equals(path) ? null : path;
  }

  /** The name a post without a path goes by: its file's own name, or one made from its date ("IMG_20261022_0130.jpg"). */
  public static String defaultName (TdApi.Message message) {
    final String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(message.date * 1000L));
    switch (message.content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document document = ((TdApi.MessageDocument) message.content).document;
        return nameOr(document.fileName, "File_" + stamp, document.mimeType);
      }
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return "IMG_" + stamp + ".jpg";
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Video video = ((TdApi.MessageVideo) message.content).video;
        return nameOr(video.fileName, "VID_" + stamp, StringUtils.isEmpty(video.mimeType) ? "video/mp4" : video.mimeType);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation animation = ((TdApi.MessageAnimation) message.content).animation;
        return nameOr(animation.fileName, "GIF_" + stamp, StringUtils.isEmpty(animation.mimeType) ? "video/mp4" : animation.mimeType);
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) message.content).audio;
        String base;
        if (!StringUtils.isEmpty(audio.performer) && !StringUtils.isEmpty(audio.title)) {
          base = audio.performer + " - " + audio.title;
        } else if (!StringUtils.isEmpty(audio.title)) {
          base = audio.title;
        } else {
          base = "Audio_" + stamp;
        }
        return nameOr(audio.fileName, cleanNameOr(base, "Audio_" + stamp), StringUtils.isEmpty(audio.mimeType) ? "audio/mpeg" : audio.mimeType);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return "Voice_" + stamp + ".ogg";
      case TdApi.MessageVideoNote.CONSTRUCTOR:
        return "Round_" + stamp + ".mp4";
    }
    return "File_" + stamp;
  }

  private static String nameOr (@Nullable String fileName, String base, @Nullable String mimeType) {
    String clean = cleanName(fileName);
    if (clean != null) {
      return clean;
    }
    String extension = StringUtils.isEmpty(mimeType) ? null : TGMimeType.extensionForMimeType(mimeType);
    return StringUtils.isEmpty(extension) ? base : base + "." + extension;
  }

  private static String cleanNameOr (String name, String fallback) {
    String clean = cleanName(name);
    return clean != null ? clean : fallback;
  }

  /** The post's file as a document: a document as it is under {@code name}, the media of other posts made into one. */
  @Nullable
  public static TdApi.Document documentOf (TdApi.MessageContent content, String name) {
    switch (content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.Document document = ((TdApi.MessageDocument) content).document;
        return new TdApi.Document(name, document.mimeType, document.minithumbnail, document.thumbnail, document.document);
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.Photo photo = ((TdApi.MessagePhoto) content).photo;
        TdApi.PhotoSize biggest = null, smallest = null;
        for (TdApi.PhotoSize size : photo.sizes) {
          if (biggest == null || (long) size.width * size.height > (long) biggest.width * biggest.height) {
            biggest = size;
          }
          if (smallest == null || (long) size.width * size.height < (long) smallest.width * smallest.height) {
            smallest = size;
          }
        }
        if (biggest == null) {
          return null;
        }
        TdApi.Thumbnail thumbnail = smallest != null && smallest != biggest ? new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), smallest.width, smallest.height, smallest.photo) : null;
        return new TdApi.Document(name, "image/jpeg", photo.minithumbnail, thumbnail, biggest.photo);
      }
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.Video video = ((TdApi.MessageVideo) content).video;
        return new TdApi.Document(name, StringUtils.isEmpty(video.mimeType) ? "video/mp4" : video.mimeType, video.minithumbnail, video.thumbnail, video.video);
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.Animation animation = ((TdApi.MessageAnimation) content).animation;
        return new TdApi.Document(name, StringUtils.isEmpty(animation.mimeType) ? "video/mp4" : animation.mimeType, animation.minithumbnail, animation.thumbnail, animation.animation);
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.Audio audio = ((TdApi.MessageAudio) content).audio;
        return new TdApi.Document(name, StringUtils.isEmpty(audio.mimeType) ? "audio/mpeg" : audio.mimeType, audio.albumCoverMinithumbnail, audio.albumCoverThumbnail, audio.audio);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        TdApi.VoiceNote voiceNote = ((TdApi.MessageVoiceNote) content).voiceNote;
        return new TdApi.Document(name, StringUtils.isEmpty(voiceNote.mimeType) ? "audio/ogg" : voiceNote.mimeType, null, null, voiceNote.voice);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        TdApi.VideoNote videoNote = ((TdApi.MessageVideoNote) content).videoNote;
        return new TdApi.Document(name, "video/mp4", videoNote.minithumbnail, videoNote.thumbnail, videoNote.video);
      }
    }
    return null;
  }

  // Paths

  /** "/a//b/ " → "/a/b"; "" → "/". */
  public static String normalize (@Nullable String path) {
    if (path == null) {
      return "/";
    }
    StringBuilder b = new StringBuilder();
    for (String segment : path.split("/")) {
      String s = segment.trim();
      if (s.isEmpty() || ".".equals(s) || "..".equals(s)) {
        continue;
      }
      b.append('/').append(s);
    }
    return b.length() == 0 ? "/" : b.toString();
  }

  public static String join (String folder, String name) {
    return "/".equals(folder) ? "/" + name : folder + "/" + name;
  }

  public static String parentOf (String path) {
    int i = path.lastIndexOf('/');
    return i <= 0 ? "/" : path.substring(0, i);
  }

  public static String nameOf (String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /** Whether {@code path} is inside {@code folder} (not the folder itself). */
  public static boolean isInside (String path, String folder) {
    return "/".equals(folder) ? !"/".equals(path) : path.startsWith(folder + "/");
  }

  /** The folder right in {@code folder} on the way to {@code path} ("/a/b/c" in "/a": "/a/b"); null when not inside. */
  @Nullable
  static String childOf (String folder, String path) {
    if (!isInside(path, folder)) {
      return null;
    }
    int start = "/".equals(folder) ? 1 : folder.length() + 1;
    int end = path.indexOf('/', start);
    return end == -1 ? path : path.substring(0, end);
  }

  /** A name a file or folder can have: no slashes or control characters, trimmed, at most 128 characters; null if none is left. */
  @Nullable
  public static String cleanName (@Nullable String name) {
    if (name == null) {
      return null;
    }
    StringBuilder b = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      b.append(c < 0x20 || c == '/' ? ' ' : c);
    }
    String result = b.toString().trim();
    if (result.length() > MAX_NAME_LENGTH) {
      String extension = extensionOf(result);
      int keep = Math.max(1, MAX_NAME_LENGTH - (extension.isEmpty() ? 0 : extension.length() + 1));
      result = result.substring(0, keep).trim() + (extension.isEmpty() ? "" : "." + extension);
    }
    return result.isEmpty() || ".".equals(result) || "..".equals(result) ? null : result;
  }

  // Picked files (a worker thread)

  private static final class Picked {
    String name, mimeType;
    long size = -1, modified;
  }

  @Nullable
  private static Picked describe (Uri uri) {
    ContentResolver resolver = AppContext.get().getContentResolver();
    Picked picked = new Picked();
    try (Cursor c = resolver.query(uri, null, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (i != -1) {
          picked.name = c.getString(i);
        }
        i = c.getColumnIndex(OpenableColumns.SIZE);
        if (i != -1 && !c.isNull(i)) {
          picked.size = c.getLong(i);
        }
        i = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
        if (i != -1 && !c.isNull(i)) {
          picked.modified = c.getLong(i);
        }
      }
    } catch (Throwable t) {
      Log.w("XGram Drive: cannot read a picked file", t);
      return null;
    }
    String mimeType = null;
    try {
      mimeType = resolver.getType(uri);
    } catch (Throwable ignored) { }
    String name = cleanName(picked.name);
    if (StringUtils.isEmpty(mimeType) || "application/octet-stream".equals(mimeType)) {
      String byName = name != null ? TGMimeType.mimeTypeForExtension(U.getExtension(name)) : null;
      if (!StringUtils.isEmpty(byName)) {
        mimeType = byName;
      }
    }
    if (StringUtils.isEmpty(mimeType)) {
      mimeType = "application/octet-stream";
    }
    String extension = TGMimeType.extensionForMimeType(mimeType);
    if (name == null) {
      name = StringUtils.isEmpty(extension) ? "File" : "File." + extension;
    } else if (StringUtils.isEmpty(U.getExtension(name)) && !StringUtils.isEmpty(extension)) {
      name = name + "." + extension;
    }
    picked.name = name;
    picked.mimeType = mimeType;
    if (picked.modified <= 0) {
      picked.modified = System.currentTimeMillis();
    }
    return picked;
  }

  /** A file opened with the system picker stays readable after a restart, so an upload TDLib resumes still works. */
  private static void keepAccess (Uri uri) {
    try {
      AppContext.get().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Throwable ignored) {
      // Only documents from the system picker can keep their access
    }
  }

  private static void releaseAccess (@Nullable Uri uri) {
    if (uri == null) {
      return;
    }
    try {
      AppContext.get().getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Throwable ignored) { }
  }

  /** A preview for photos and videos, made from the picked file by Telegram X's generator (no copy first). */
  @Nullable
  private static TdApi.InputThumbnail thumbnailOf (Uri uri, String mimeType) {
    final int resolution = TdlibFileGenerationManager.BIG_THUMB_RESOLUTION;
    int width = 0, height = 0;
    String conversion = null;
    try {
      if (mimeType.startsWith("image/")) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = AppContext.get().getContentResolver().openInputStream(uri)) {
          BitmapFactory.decodeStream(in, null, options);
        }
        width = options.outWidth;
        height = options.outHeight;
        if (U.isExifRotated(U.getExifOrientation(uri.toString()))) {
          int w = width;
          width = height;
          height = w;
        }
        conversion = PhotoGenerationInfo.makeConversion(0, 0, false, resolution);
      } else if (mimeType.startsWith("video/")) {
        MediaMetadataRetriever retriever = null;
        try {
          retriever = U.openRetriever(uri.toString());
          width = toInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 0);
          height = toInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
          int rotation = toInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION), 0);
          if (rotation == 90 || rotation == 270) {
            int w = width;
            width = height;
            height = w;
          }
        } finally {
          U.closeRetriever(retriever);
        }
        conversion = ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_VIDEO, null, resolution);
      }
    } catch (Throwable t) {
      Log.i("XGram Drive: no preview for a picked file", t);
      return null;
    }
    if (conversion == null || width <= 0 || height <= 0) {
      return null;
    }
    float scale = Math.min(1f, resolution / (float) Math.max(width, height));
    return new TdApi.InputThumbnail(new TdApi.InputFileGenerated(uri.toString(), conversion, 0), Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
  }
}
