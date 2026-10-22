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
package com.fahadali.xgram.xdrive;

import android.util.SparseArray;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XDrive's folders: one small JSON file in Saved Messages ({@link #FILE_NAME}, caption tagged {@link #TAG}) mapping
 * message ids to folders. Telegram doesn't let anyone edit a forward, so tags can't live on the messages themselves;
 * the file is edited in place (EditMessageMedia: no time limit in Saved Messages) and other devices reload it when it
 * changes. One instance per account; UI thread only.
 */
public final class XDriveIndex implements MessageListener {
  public static final String FILE_NAME = "xdrive-folders.json";
  public static final String TAG = "#xdrivefolders";
  private static final long SAVE_DELAY_MS = 1200L;

  public interface Listener {
    void onIndexChanged ();
  }

  public static final class Folder {
    public final String id;
    public String name;

    Folder (String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  private static final SparseArray<XDriveIndex> indexes = new SparseArray<>();

  public static XDriveIndex of (Tdlib tdlib) {
    XDriveIndex index = indexes.get(tdlib.id());
    if (index == null || index.tdlib != tdlib || index.userId != tdlib.myUserId()) {
      if (index != null) {
        index.release();
      }
      index = new XDriveIndex(tdlib);
      indexes.put(tdlib.id(), index);
    }
    return index;
  }

  /** TDLib thread: the account in this slot logged out. */
  public static void forgetAccount (int accountId) {
    UI.post(() -> {
      XDriveIndex index = indexes.get(accountId);
      if (index != null) {
        indexes.remove(accountId);
        index.release();
      }
    });
  }

  private final Tdlib tdlib;
  private final long userId, chatId;
  private final List<Folder> folders = new ArrayList<>();
  private final LinkedHashMap<Long, String> items = new LinkedHashMap<>();
  /** Copies on their way to Saved Messages: the folder they go into once they have their id. */
  private final Map<Long, String> pending = new HashMap<>();
  private final List<Listener> listeners = new ArrayList<>();
  private final List<Runnable> afterLoad = new ArrayList<>();
  private boolean loaded, loading, savePosted, saving, saveAgain, indexSending;
  private long indexMessageId;
  private int counter;

  private XDriveIndex (Tdlib tdlib) {
    this.tdlib = tdlib;
    this.userId = tdlib.myUserId();
    this.chatId = tdlib.selfChatId();
    if (chatId != 0) {
      tdlib.listeners().subscribeToMessageUpdates(chatId, this);
    }
  }

  private void release () {
    if (chatId != 0) {
      tdlib.listeners().unsubscribeFromMessageUpdates(chatId, this);
    }
    listeners.clear();
  }

  public void addListener (Listener listener) {
    if (!listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  public void removeListener (Listener listener) {
    listeners.remove(listener);
  }

  private void notifyChanged () {
    for (Listener listener : new ArrayList<>(listeners)) {
      listener.onIndexChanged();
    }
  }

  public boolean isLoaded () {
    return loaded;
  }

  /** The index message itself (left out of XDrive's lists). */
  public boolean isIndexMessage (long messageId) {
    return messageId != 0 && messageId == indexMessageId;
  }

  public static boolean isIndexDocument (@Nullable TdApi.Message message) {
    return message != null && message.content != null && message.content.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR &&
      FILE_NAME.equals(((TdApi.MessageDocument) message.content).document.fileName);
  }

  // Loading

  /** Reads the index from Saved Messages once (later calls run {@code after} at once). */
  public void load (@Nullable Runnable after) {
    if (loaded) {
      if (after != null) {
        after.run();
      }
      return;
    }
    if (after != null) {
      afterLoad.add(after);
    }
    if (loading || chatId == 0) {
      return;
    }
    loading = true;
    findAndRead();
  }

  private void findAndRead () {
    tdlib.send(new TdApi.SearchChatMessages(chatId, null, TAG, null, 0, 0, 20, new TdApi.SearchMessagesFilterDocument()), (found, error) -> {
      TdApi.Message newest = null;
      if (found != null) {
        for (TdApi.Message message : found.messages) {
          if (message != null && message.isOutgoing && isIndexDocument(message) && (newest == null || message.id > newest.id)) {
            newest = message;
          }
        }
      }
      if (newest == null) {
        UI.post(() -> finishLoad(0, null));
        return;
      }
      final long messageId = newest.id;
      TdApi.File file = ((TdApi.MessageDocument) newest.content).document.document;
      tdlib.send(new TdApi.DownloadFile(file.id, 32, 0, 0, true), (downloaded, downloadError) -> {
        String json = null;
        if (downloaded != null && downloaded.local.isDownloadingCompleted) {
          json = readFile(downloaded.local.path);
        }
        final String text = json;
        UI.post(() -> finishLoad(messageId, text));
      });
    });
  }

  @Nullable
  private static String readFile (String path) {
    try (java.io.FileInputStream in = new java.io.FileInputStream(path)) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (Throwable t) {
      Log.w("XDrive: cannot read the folders file", t);
      return null;
    }
  }

  private void finishLoad (long messageId, @Nullable String json) {
    indexMessageId = messageId;
    if (json != null && !savePosted && !saving) {
      parse(json); // a change made meanwhile wins (it is saved over the file)
    }
    loaded = true;
    loading = false;
    List<Runnable> callbacks = new ArrayList<>(afterLoad);
    afterLoad.clear();
    for (Runnable callback : callbacks) {
      callback.run();
    }
    notifyChanged();
  }

  private void parse (String json) {
    folders.clear();
    items.clear();
    try {
      JSONObject root = new JSONObject(json);
      JSONArray list = root.optJSONArray("folders");
      if (list != null) {
        for (int i = 0; i < list.length(); i++) {
          JSONObject folder = list.optJSONObject(i);
          if (folder != null && folder.has("id") && folder.has("name")) {
            folders.add(new Folder(folder.getString("id"), folder.getString("name")));
          }
        }
      }
      JSONObject map = root.optJSONObject("items");
      if (map != null) {
        Iterator<String> keys = map.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          String folderId = map.optString(key, null);
          if (folderId != null && findFolder(folderId) != null) {
            try {
              items.put(Long.parseLong(key), folderId);
            } catch (NumberFormatException ignored) { }
          }
        }
      }
    } catch (Throwable t) {
      Log.w("XDrive: the folders file is damaged", t);
    }
  }

  private String toJson () {
    try {
      JSONObject root = new JSONObject();
      root.put("v", 1);
      JSONArray list = new JSONArray();
      for (Folder folder : folders) {
        list.put(new JSONObject().put("id", folder.id).put("name", folder.name));
      }
      root.put("folders", list);
      JSONObject map = new JSONObject();
      for (Map.Entry<Long, String> entry : items.entrySet()) {
        map.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      root.put("items", map);
      return root.toString();
    } catch (Throwable t) {
      return "{\"v\":1,\"folders\":[],\"items\":{}}";
    }
  }

  // Saving: the file replaced in place, a new one when there is none

  private void scheduleSave () {
    if (savePosted) {
      return;
    }
    savePosted = true;
    UI.post(this::save, SAVE_DELAY_MS);
  }

  private void save () {
    savePosted = false;
    if (saving || indexSending) {
      saveAgain = true;
      return;
    }
    saving = true;
    final String json = toJson();
    final long editId = indexMessageId;
    new Thread(() -> {
      File file = writeTemp(json);
      UI.post(() -> {
        if (file == null) {
          saving = false;
          return;
        }
        TdApi.FormattedText caption = new TdApi.FormattedText(Lang.getString(R.string.XgramXDriveIndexCaption) + "\n" + TAG, new TdApi.TextEntity[0]);
        TdApi.InputMessageDocument content = new TdApi.InputMessageDocument(new TdApi.InputDocument(new TdApi.InputFileLocal(file.getPath()), null, true), caption);
        if (editId != 0) {
          tdlib.send(new TdApi.EditMessageMedia(chatId, editId, null, content), (message, error) -> UI.post(() -> {
            saving = false;
            if (error != null) {
              // The file went away (deleted on another device): a new one
              if (indexMessageId == editId) {
                indexMessageId = 0;
              }
              save();
              return;
            }
            afterSave();
          }));
        } else {
          tdlib.send(new TdApi.SendMessage(chatId, null, null, new TdApi.MessageSendOptions(null, true, false, false, false, 0, false, null, 0, 0, false), null, content), (message, error) -> UI.post(() -> {
            saving = false;
            if (message == null) {
              if (error != null) {
                UI.showToast(Lang.getString(R.string.XgramXDriveSaveFailed, error.message), android.widget.Toast.LENGTH_SHORT);
              }
              return;
            }
            indexMessageId = message.id;
            indexSending = message.sendingState != null;
            afterSave();
          }));
        }
      });
    }, "XDriveIndexSave").start();
  }

  private void afterSave () {
    if (saveAgain && !indexSending) {
      saveAgain = false;
      save();
    }
  }

  @Nullable
  private static File writeTemp (String json) {
    try {
      File root = new File(AppContext.get().getCacheDir(), "xdrive");
      File[] old = root.listFiles();
      long now = System.currentTimeMillis();
      if (old != null) {
        for (File dir : old) {
          if (now - dir.lastModified() > 24L * 60 * 60 * 1000) {
            File[] files = dir.listFiles();
            if (files != null) {
              for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
              }
            }
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
          }
        }
      }
      // A directory per save: the file keeps its name (the document's name), TDLib sees a new file each time
      File dir = new File(root, String.valueOf(now));
      if (!dir.exists() && !dir.mkdirs()) {
        return null;
      }
      File file = new File(dir, FILE_NAME);
      try (FileOutputStream out = new FileOutputStream(file)) {
        out.write(json.getBytes(StandardCharsets.UTF_8));
      }
      return file;
    } catch (Throwable t) {
      Log.w("XDrive: cannot write the folders file", t);
      return null;
    }
  }

  // Folders

  public List<Folder> folders () {
    List<Folder> list = new ArrayList<>(folders);
    Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
    return list;
  }

  @Nullable
  public Folder findFolder (String id) {
    for (Folder folder : folders) {
      if (folder.id.equals(id)) {
        return folder;
      }
    }
    return null;
  }

  @Nullable
  public Folder findFolderByName (String name) {
    for (Folder folder : folders) {
      if (folder.name.equalsIgnoreCase(name.trim())) {
        return folder;
      }
    }
    return null;
  }

  public Folder createFolder (String name) {
    Folder folder = new Folder("f" + Long.toString(System.currentTimeMillis(), 36) + (counter++), name.trim());
    folders.add(folder);
    changed();
    return folder;
  }

  public void renameFolder (String id, String name) {
    Folder folder = findFolder(id);
    if (folder != null && !folder.name.equals(name.trim())) {
      folder.name = name.trim();
      changed();
    }
  }

  /** Deletes a folder: its messages stay in XDrive. */
  public void deleteFolder (String id) {
    Folder folder = findFolder(id);
    if (folder == null) {
      return;
    }
    folders.remove(folder);
    items.values().removeIf(id::equals);
    changed();
  }

  @Nullable
  public String folderOf (long messageId) {
    return items.get(messageId);
  }

  /** The messages in a folder, newest first. */
  public long[] messagesIn (String folderId) {
    List<Long> ids = new ArrayList<>();
    for (Map.Entry<Long, String> entry : items.entrySet()) {
      if (folderId.equals(entry.getValue())) {
        ids.add(entry.getKey());
      }
    }
    Collections.sort(ids, Collections.reverseOrder());
    long[] result = new long[ids.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = ids.get(i);
    }
    return result;
  }

  public int count (String folderId) {
    int count = 0;
    for (String value : items.values()) {
      if (folderId.equals(value)) {
        count++;
      }
    }
    return count;
  }

  /** Puts messages into a folder ({@code null}: out of any). A message is in one folder at most. */
  public void setFolder (long[] messageIds, @Nullable String folderId) {
    boolean changed = false;
    for (long id : messageIds) {
      if (id == 0 || id == indexMessageId) {
        continue;
      }
      if (folderId == null) {
        changed |= items.remove(id) != null;
      } else if (!folderId.equals(items.get(id))) {
        items.put(id, folderId);
        changed = true;
      }
    }
    if (changed) {
      changed();
    }
  }

  /** Copies still on their way (Save to XDrive): they go into the folder once posted. */
  public void setFolderWhenSent (long[] tempMessageIds, String folderId) {
    for (long id : tempMessageIds) {
      pending.put(id, folderId);
    }
  }

  /** Drops messages that are no longer there (a folder page found them gone). */
  public void forget (List<Long> messageIds) {
    boolean changed = false;
    for (long id : messageIds) {
      changed |= items.remove(id) != null;
    }
    if (changed) {
      changed();
    }
  }

  private void changed () {
    notifyChanged();
    scheduleSave();
  }

  // Updates of Saved Messages (TDLib thread)

  @Override
  public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    UI.post(() -> {
      if (oldMessageId == indexMessageId) {
        indexMessageId = message.id;
        indexSending = false;
        afterSave();
      }
      String folderId = pending.remove(oldMessageId);
      if (folderId != null && findFolder(folderId) != null) {
        items.put(message.id, folderId);
        changed();
      }
    });
  }

  @Override
  public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
    UI.post(() -> {
      pending.remove(oldMessageId);
      if (oldMessageId == indexMessageId) {
        indexMessageId = 0;
        indexSending = false;
      }
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    UI.post(() -> {
      boolean changed = false;
      for (long id : messageIds) {
        if (id == indexMessageId) {
          indexMessageId = 0; // deleted: the next change writes a new file
        }
        changed |= items.remove(id) != null;
      }
      if (changed) {
        changed();
      }
    });
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    UI.post(() -> {
      if (messageId == indexMessageId && !saving && !savePosted) {
        // Edited on another device: read it again
        loaded = false;
        loading = true;
        findAndRead();
      }
    });
  }
}
