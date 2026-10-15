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
package com.fahadali.xgram.layout;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextPaint;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.chats.XgramLinkFixer;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.MediaView;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Drag and drop and paste (adaptive plan, part 2):
 * - photos, videos, files and text dropped from another app (split screen, Edge panel, DeX) onto an open chat, or onto
 *   a chat row in the chat list, are sent there after a short confirmation; dropped text goes into the message box;
 * - images and files pasted into the message box are sent the same way ({@link ViewCompat#setOnReceiveContentListener});
 * - in the media viewer, holding a photo or video and then moving the finger drags it out to another app as a file.
 *
 * Dropped and pasted content is copied into the cache first: drag and drop and clipboard read access can end before
 * TDLib reads the file, and a copy also works for every content provider.
 */
public final class XgramDragDrop {
  private XgramDragDrop () { }

  /** Besides text: what the message box takes as files to send. */
  private static final String[] RECEIVE_MIME_TYPES = {"image/*", "video/*", "audio/*", "application/*"};
  /** Local state of a drag that starts in the media viewer, so it is never dropped back into a chat of this app. */
  private static final Object DRAG_OUT_STATE = new Object();

  private static final String CACHE_DIR = "xgram_drop";
  /** Copies are kept this long: TDLib uploads from them, possibly after being offline for a while. */
  private static final long KEEP_MS = TimeUnit.DAYS.toMillis(3);

  /** Drag and drop needs Android 7.0 (dragging between apps) and the setting. Paste works everywhere. */
  public static boolean isEnabled () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && XgramSettings.instance().dragDrop();
  }

  // Open chat

  /** Called once the chat's views exist: drop onto the chat, drop into and paste into the message box. */
  public static void attachChat (MessagesController c, View root, InputView input) {
    ViewCompat.setOnReceiveContentListener(input, RECEIVE_MIME_TYPES, (view, payload) -> receiveContent(c, input, payload));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      ChatDropTarget target = new ChatDropTarget(c, root, input);
      root.setOnDragListener(target);
      // Files dropped on the message box are sent too. Text is left to the message box itself (see onDrag).
      input.setOnDragListener(target);
    }
  }

  /** {@link androidx.core.view.OnReceiveContentListener}: files are sent, the rest (text) is inserted as usual. */
  @Nullable
  private static ContentInfoCompat receiveContent (MessagesController c, InputView input, ContentInfoCompat payload) {
    if (payload.getSource() == ContentInfoCompat.SOURCE_DRAG_AND_DROP) {
      // Drops with files are taken by ChatDropTarget. One that reaches the message box is either text, or a drop
      // while drag and drop is off or not allowed in this chat: left as it is.
      return payload;
    }
    ClipData clip = payload.getClip();
    List<Uri> uris = new ArrayList<>();
    List<ClipData.Item> rest = new ArrayList<>();
    for (int i = 0; i < clip.getItemCount(); i++) {
      ClipData.Item item = clip.getItemAt(i);
      if (isFileUri(item.getUri())) {
        uris.add(item.getUri());
      } else {
        rest.add(item);
      }
    }
    if (uris.isEmpty() || !canSendFiles(c, input)) {
      return payload;
    }
    receiveFiles(c, input, uris, clip.getDescription(), null);
    if (rest.isEmpty()) {
      return null;
    }
    ClipData restClip = new ClipData(clip.getDescription(), rest.get(0));
    for (int i = 1; i < rest.size(); i++) {
      restClip.addItem(rest.get(i));
    }
    return new ContentInfoCompat.Builder(payload).setClip(restClip).build();
  }

  /**
   * Hook for the paste item of the text menu ({@code v.EditText}): a clip with files goes through the view's
   * receive-content listener on every Android version (before Android 12 the platform would paste their addresses).
   */
  public static boolean onPaste (EditText view) {
    if (ViewCompat.getOnReceiveContentMimeTypes(view) == null) {
      return false;
    }
    ClipData clip;
    try {
      ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      clip = clipboard != null ? clipboard.getPrimaryClip() : null;
    } catch (Throwable t) {
      return false;
    }
    if (clip == null || !hasFileUri(clip)) {
      return false;
    }
    ContentInfoCompat rest = ViewCompat.performReceiveContent(view, new ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_CLIPBOARD).build());
    if (rest != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      // Before Android 12 nothing inserts what the listener leaves: only the text parts, never the file addresses.
      StringBuilder text = new StringBuilder();
      ClipData restClip = rest.getClip();
      for (int i = 0; i < restClip.getItemCount(); i++) {
        CharSequence itemText = restClip.getItemAt(i).getText();
        if (!StringUtils.isEmpty(itemText)) {
          if (text.length() > 0) {
            text.append('\n');
          }
          text.append(itemText);
        }
      }
      if (text.length() > 0) {
        insertText(view, text);
      }
    }
    return true;
  }

  private static boolean canSendFiles (MessagesController c, InputView input) {
    return !c.isDestroyed() && !c.inPreviewMode() && !c.isEditingMessage() && c.xgramInputView() == input && c.getChat() != null;
  }

  private static boolean canDropInto (MessagesController c, View root) {
    if (c.isDestroyed() || c.inPreviewMode() || !root.isShown()) {
      return false;
    }
    TdApi.Chat chat = c.getChat();
    return chat != null && (c.tdlib().canSendBasicMessage(chat) || c.tdlib().canSendSendSomeMedia(chat));
  }

  /** Copies the files, asks, then sends them to the open chat with its reply, topic and silent mode. */
  private static void receiveFiles (MessagesController c, InputView input, List<Uri> uris, @Nullable ClipDescription description, @Nullable Object permissions) {
    final long chatId = c.getChatId();
    copyAsync(uris, description, permissions, files -> {
      if (files.isEmpty()) {
        UI.showToast(R.string.XgramDropFailed, Toast.LENGTH_SHORT);
        return;
      }
      if (c.isDestroyed() || c.getChatId() != chatId) {
        deleteFiles(files);
        return;
      }
      confirm(c, chatId, files, null, asFiles -> sendToOpenChat(c, input, chatId, files, asFiles));
    });
  }

  private static void sendToOpenChat (MessagesController c, InputView input, long chatId, List<Dropped> files, boolean asFiles) {
    if (c.isDestroyed() || c.getChatId() != chatId || c.showSlowModeRestriction(input, null)) {
      return;
    }
    final Tdlib tdlib = c.tdlib();
    final MessagesController.ReplyInfo replyInfo = c.obtainReplyTo();
    final TdApi.InputMessageReplyTo replyTo = replyInfo != null ? replyInfo.toInputMessageReply() : null;
    final TdApi.MessageTopic topicId = c.getMessageTopicId(replyInfo);
    final TdApi.InputSuggestedPostInfo suggestedPostInfo = c.getInputSuggestedPostInfo(replyInfo);
    final boolean silent = c.obtainSilentMode();
    final boolean scheduledOnly = c.areScheduledOnly();
    Background.instance().post(() -> {
      List<TdApi.InputMessageContent> contents = buildContents(tdlib, chatId, files, null, asFiles);
      UI.post(() -> {
        if (contents.isEmpty()) {
          UI.showToast(R.string.XgramDropFailed, Toast.LENGTH_SHORT);
          return;
        }
        if (c.isDestroyed() || c.getChatId() != chatId) {
          return;
        }
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null) {
          return;
        }
        for (TdApi.InputMessageContent content : contents) {
          if (c.showRestriction(input, tdlib.getRestrictionText(chat, content))) {
            return;
          }
        }
        if (scheduledOnly) {
          tdlib.ui().showScheduleOptions(c, chatId, false, (sendOptions, disableMarkdown) ->
            send(tdlib, chatId, topicId, replyTo, Td.newSendOptions(sendOptions, suggestedPostInfo, silent), contents),
            null, null);
        } else {
          send(tdlib, chatId, topicId, replyTo, Td.newSendOptions(suggestedPostInfo, silent), contents);
        }
      });
    });
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private static final class ChatDropTarget implements View.OnDragListener {
    private static final int HOVER_ROOT = 1, HOVER_INPUT = 2;

    private final MessagesController c;
    private final View root;
    private final InputView input;
    private final Highlight highlight;
    private int hovered;
    private boolean withFiles;
    private final Runnable hide;

    ChatDropTarget (MessagesController c, View root, InputView input) {
      this.c = c;
      this.root = root;
      this.input = input;
      this.highlight = new Highlight(root, true);
      this.hide = highlight::hide;
    }

    @Override
    public boolean onDrag (View v, DragEvent event) {
      int bit = v == input ? HOVER_INPUT : HOVER_ROOT;
      switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED: {
          ClipDescription description = event.getClipDescription();
          boolean files = hasFiles(description);
          withFiles = files;
          if (v == input && !files) {
            return false; // Plain text: the message box shows its cursor and inserts it itself.
          }
          return isEnabled() && event.getLocalState() != DRAG_OUT_STATE && (files || hasText(description)) && canDropInto(c, root);
        }
        case DragEvent.ACTION_DRAG_ENTERED: {
          hovered |= bit;
          update();
          return true;
        }
        case DragEvent.ACTION_DRAG_EXITED: {
          hovered &= ~bit;
          update();
          return true;
        }
        case DragEvent.ACTION_DRAG_LOCATION: {
          return true;
        }
        case DragEvent.ACTION_DROP: {
          hovered = 0;
          root.removeCallbacks(hide);
          highlight.hide();
          return onDrop(v, event);
        }
        case DragEvent.ACTION_DRAG_ENDED: {
          hovered = 0;
          root.removeCallbacks(hide);
          highlight.hide();
          return true;
        }
      }
      return false;
    }

    private void update () {
      root.removeCallbacks(hide);
      if (hovered != 0) {
        // Text goes into the message box, so only files get "Drop here to send".
        highlight.show(0, 0, root.getWidth(), root.getHeight(), withFiles ? Lang.getString(R.string.XgramDropHere) : null);
      } else {
        // Moving between the chat and the message box exits one and enters the other: no flicker in between.
        root.post(hide);
      }
    }

    private boolean onDrop (View v, DragEvent event) {
      ClipData clip = event.getClipData();
      if (clip == null || !canDropInto(c, root)) {
        return false;
      }
      List<Uri> uris = new ArrayList<>();
      StringBuilder text = new StringBuilder();
      split(v.getContext(), clip, uris, text);
      if (uris.isEmpty() && text.length() == 0) {
        return false;
      }
      if (text.length() > 0) {
        insertText(input, text);
        input.requestFocus();
      }
      if (!uris.isEmpty()) {
        if (!canSendFiles(c, input)) {
          return text.length() > 0;
        }
        receiveFiles(c, input, uris, clip.getDescription(), requestPermissions(v, event));
      }
      return true;
    }
  }

  // Chat list

  /** A chat row in the chat list takes drops too: they are sent to that chat. */
  public static void attachChatList (ChatsController c, RecyclerView list) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      list.setOnDragListener(new ChatListDropTarget(c, list));
    }
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private static final class ChatListDropTarget implements View.OnDragListener {
    private final ChatsController c;
    private final RecyclerView list;
    private final Highlight highlight;

    ChatListDropTarget (ChatsController c, RecyclerView list) {
      this.c = c;
      this.list = list;
      this.highlight = new Highlight(list, false);
    }

    private boolean isVisible () {
      if (c.isDestroyed() || !list.isShown()) {
        return false;
      }
      // In two panes, Telegram X's own chat list stays under the right pane, hidden: never drop there.
      XgramTwoPane twoPane = c.context().xgramTwoPane();
      return twoPane == null || !twoPane.isHiddenRoot(c.getParentOrSelf());
    }

    @Nullable
    private ChatView rowAt (float x, float y) {
      View child = list.findChildViewUnder(x, y);
      if (child instanceof ChatView && ((ChatView) child).getChatId() != 0) {
        return (ChatView) child;
      }
      return null;
    }

    private void highlightRow (@Nullable ChatView row) {
      if (row == null) {
        highlight.hide();
      } else {
        int dy = Math.round(row.getTranslationY());
        highlight.show(row.getLeft(), row.getTop() + dy, row.getRight(), row.getBottom() + dy, null);
      }
    }

    @Override
    public boolean onDrag (View v, DragEvent event) {
      switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED: {
          ClipDescription description = event.getClipDescription();
          return isEnabled() && event.getLocalState() != DRAG_OUT_STATE && (hasFiles(description) || hasText(description)) && isVisible();
        }
        case DragEvent.ACTION_DRAG_ENTERED: {
          return true;
        }
        case DragEvent.ACTION_DRAG_LOCATION: {
          highlightRow(rowAt(event.getX(), event.getY()));
          return true;
        }
        case DragEvent.ACTION_DRAG_EXITED:
        case DragEvent.ACTION_DRAG_ENDED: {
          highlight.hide();
          return true;
        }
        case DragEvent.ACTION_DROP: {
          highlight.hide();
          ChatView row = rowAt(event.getX(), event.getY());
          return row != null && onDrop(v, row.getChatId(), event);
        }
      }
      return false;
    }

    private boolean onDrop (View v, long chatId, DragEvent event) {
      ClipData clip = event.getClipData();
      if (clip == null || !isVisible() || c.tdlib().chat(chatId) == null) {
        return false;
      }
      List<Uri> uris = new ArrayList<>();
      StringBuilder text = new StringBuilder();
      split(v.getContext(), clip, uris, text);
      if (uris.isEmpty() && text.length() == 0) {
        return false;
      }
      final String caption = text.length() > 0 ? text.toString() : null;
      if (uris.isEmpty()) {
        confirm(c, chatId, Collections.emptyList(), caption, asFiles -> sendToChat(c.tdlib(), chatId, Collections.emptyList(), caption, false));
        return true;
      }
      copyAsync(uris, clip.getDescription(), requestPermissions(v, event), files -> {
        if (files.isEmpty()) {
          UI.showToast(R.string.XgramDropFailed, Toast.LENGTH_SHORT);
        } else if (c.isDestroyed()) {
          deleteFiles(files);
        } else {
          confirm(c, chatId, files, caption, asFiles -> sendToChat(c.tdlib(), chatId, files, caption, asFiles));
        }
      });
      return true;
    }
  }

  /** Sends to a chat that isn't open: no reply, topic or scheduling; the dropped text becomes the caption. */
  private static void sendToChat (Tdlib tdlib, long chatId, List<Dropped> files, @Nullable String caption, boolean asFiles) {
    CharSequence slowMode = tdlib.getSlowModeRestrictionText(chatId);
    if (slowMode != null) {
      UI.showToast(slowMode, Toast.LENGTH_SHORT);
      return;
    }
    Background.instance().post(() -> {
      List<TdApi.InputMessageContent> contents = buildContents(tdlib, chatId, files, caption, asFiles);
      UI.post(() -> {
        if (contents.isEmpty()) {
          UI.showToast(R.string.XgramDropFailed, Toast.LENGTH_SHORT);
          return;
        }
        TdApi.Chat chat = tdlib.chat(chatId);
        if (chat == null) {
          return;
        }
        for (TdApi.InputMessageContent content : contents) {
          CharSequence restriction = tdlib.getRestrictionText(chat, content);
          if (restriction != null) {
            UI.showToast(restriction, Toast.LENGTH_SHORT);
            return;
          }
        }
        send(tdlib, chatId, null, null, Td.newSendOptions(), contents);
      });
    });
  }

  // Shared parts

  /** The files, ready to send. */
  private static final class Dropped {
    final File file;
    final String mimeType;

    Dropped (File file, String mimeType) {
      this.file = file;
      this.mimeType = mimeType;
    }

    boolean isMedia () {
      return mimeType.startsWith("image/") || mimeType.startsWith("video/");
    }
  }

  private interface SendAction {
    void send (boolean asFiles);
  }

  private interface FilesCallback {
    void onFiles (List<Dropped> files);
  }

  /** Files must be content URIs of other apps: never this app's own files (a crafted drop could point at them). */
  private static boolean isFileUri (@Nullable Uri uri) {
    return uri != null && ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme()) && !Config.FILE_PROVIDER_AUTHORITY.equals(uri.getAuthority());
  }

  private static boolean hasFileUri (ClipData clip) {
    for (int i = 0; i < clip.getItemCount(); i++) {
      if (isFileUri(clip.getItemAt(i).getUri())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasFiles (@Nullable ClipDescription description) {
    if (description != null) {
      for (int i = 0; i < description.getMimeTypeCount(); i++) {
        String mimeType = description.getMimeType(i);
        if (mimeType != null && (!mimeType.startsWith("text/") || ClipDescription.MIMETYPE_TEXT_URILIST.equals(mimeType))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasText (@Nullable ClipDescription description) {
    if (description != null) {
      for (int i = 0; i < description.getMimeTypeCount(); i++) {
        String mimeType = description.getMimeType(i);
        if (mimeType != null && mimeType.startsWith("text/")) {
          return true;
        }
      }
    }
    return false;
  }

  /** Files go to {@code uris}; text items (and links) to {@code text}. Other content URIs are skipped, not read as text. */
  private static void split (Context context, ClipData clip, List<Uri> uris, StringBuilder text) {
    for (int i = 0; i < clip.getItemCount(); i++) {
      ClipData.Item item = clip.getItemAt(i);
      Uri uri = item.getUri();
      if (isFileUri(uri)) {
        uris.add(uri);
        continue;
      }
      if (uri != null && ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme()) && item.getText() == null) {
        continue;
      }
      CharSequence itemText;
      try {
        itemText = item.coerceToText(context);
      } catch (Throwable t) {
        itemText = item.getText();
      }
      if (!StringUtils.isEmpty(itemText)) {
        if (text.length() > 0) {
          text.append('\n');
        }
        text.append(itemText);
      }
    }
  }

  private static void insertText (EditText input, CharSequence text) {
    Editable editable = input.getText();
    if (editable == null) {
      return;
    }
    int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
    int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
    if (start < 0 || end > editable.length()) {
      start = end = editable.length();
    }
    editable.replace(start, end, text);
  }

  /** Read access to the dropped files, kept until they are copied. */
  @Nullable
  @RequiresApi(Build.VERSION_CODES.N)
  private static Object requestPermissions (View v, DragEvent event) {
    BaseActivity activity = UI.getContext(v.getContext());
    if (activity == null) {
      return null;
    }
    try {
      return activity.requestDragAndDropPermissions(event);
    } catch (Throwable t) {
      Log.w("Unable to get drag and drop permissions", t);
      return null;
    }
  }

  private static void releasePermissions (@Nullable Object permissions) {
    if (permissions != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        ((DragAndDropPermissions) permissions).release();
      } catch (Throwable ignored) { }
    }
  }

  /** Copies the content into the cache on a worker thread, then calls back on the UI thread. */
  private static void copyAsync (List<Uri> uris, @Nullable ClipDescription description, @Nullable Object permissions, FilesCallback callback) {
    final Context context = AppContext.get();
    final String fallbackMimeType = singleFileMimeType(description);
    final AtomicBoolean done = new AtomicBoolean();
    UI.post(() -> {
      if (!done.get()) {
        UI.showToast(R.string.ProcessingFileWait, Toast.LENGTH_SHORT);
      }
    }, 1000L);
    new Thread(() -> {
      List<Dropped> files = new ArrayList<>(uris.size());
      try {
        File root = new File(context.getCacheDir(), CACHE_DIR);
        deleteOld(root);
        File dir = new File(root, String.valueOf(System.currentTimeMillis()));
        for (int i = 0; i < uris.size(); i++) {
          Dropped file = copy(context, new File(dir, String.valueOf(i)), uris.get(i), fallbackMimeType);
          if (file != null) {
            files.add(file);
          }
        }
      } catch (Throwable t) {
        Log.e("Unable to copy dropped files", t);
      } finally {
        done.set(true);
        UI.post(() -> {
          releasePermissions(permissions);
          callback.onFiles(files);
        });
      }
    }, "XgramDrop").start();
  }

  @Nullable
  private static String singleFileMimeType (@Nullable ClipDescription description) {
    if (description != null && description.getMimeTypeCount() == 1) {
      String mimeType = description.getMimeType(0);
      if (mimeType != null && !mimeType.contains("*") && !mimeType.startsWith("text/")) {
        return mimeType;
      }
    }
    return null;
  }

  @Nullable
  private static Dropped copy (Context context, File dir, Uri uri, @Nullable String fallbackMimeType) {
    ContentResolver resolver = context.getContentResolver();
    String name = null;
    try (Cursor c = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        name = c.getString(0);
      }
    } catch (Throwable ignored) { }
    name = cleanName(name);
    String mimeType = null;
    try {
      mimeType = resolver.getType(uri);
    } catch (Throwable ignored) { }
    if (StringUtils.isEmpty(mimeType) || "application/octet-stream".equals(mimeType)) {
      String byName = name != null ? TGMimeType.mimeTypeForExtension(U.getExtension(name)) : null;
      if (!StringUtils.isEmpty(byName)) {
        mimeType = byName;
      } else if (StringUtils.isEmpty(mimeType)) {
        mimeType = fallbackMimeType;
      }
    }
    if (StringUtils.isEmpty(mimeType)) {
      mimeType = "application/octet-stream";
    }
    String extension = TGMimeType.extensionForMimeType(mimeType);
    if (name == null) {
      name = StringUtils.isEmpty(extension) ? "file" : "file." + extension;
    } else if (StringUtils.isEmpty(U.getExtension(name)) && !StringUtils.isEmpty(extension)) {
      name = name + "." + extension;
    }
    if (!dir.exists() && !dir.mkdirs()) {
      return null;
    }
    File target = new File(dir, name);
    try (InputStream in = resolver.openInputStream(uri)) {
      if (in == null) {
        return null;
      }
      try (OutputStream out = new FileOutputStream(target)) {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
          out.write(buffer, 0, count);
        }
      }
    } catch (Throwable t) {
      Log.e("Unable to copy a dropped file", t);
      //noinspection ResultOfMethodCallIgnored
      target.delete();
      return null;
    }
    return target.length() > 0 ? new Dropped(target, mimeType) : null;
  }

  @Nullable
  private static String cleanName (@Nullable String name) {
    if (name == null) {
      return null;
    }
    StringBuilder b = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      b.append(c < 0x20 || "/\\:*?\"<>|".indexOf(c) != -1 ? '_' : c);
    }
    String result = b.toString().trim();
    if (result.length() > 120) {
      String extension = U.getExtension(result);
      int keep = Math.max(1, 120 - (StringUtils.isEmpty(extension) ? 0 : extension.length() + 1));
      result = result.substring(0, keep) + (StringUtils.isEmpty(extension) ? "" : "." + extension);
    }
    return result.isEmpty() || result.equals(".") || result.equals("..") ? null : result;
  }

  private static void deleteOld (File root) {
    File[] dirs = root.listFiles();
    if (dirs == null) {
      return;
    }
    long now = System.currentTimeMillis();
    for (File dir : dirs) {
      long time = StringUtils.parseLong(dir.getName());
      if (time <= 0 || now - time > KEEP_MS) {
        deleteRecursive(dir);
      }
    }
  }

  private static void deleteRecursive (File file) {
    File[] children = file.isDirectory() ? file.listFiles() : null;
    if (children != null) {
      for (File child : children) {
        deleteRecursive(child);
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  private static void deleteFiles (List<Dropped> files) {
    Background.instance().post(() -> {
      for (Dropped file : files) {
        deleteRecursive(file.file.getParentFile());
      }
    });
  }

  /** "Send to Chat?" with the file name or count: Send, Send as files (photos and videos only), Cancel. */
  private static void confirm (ViewController<?> c, long chatId, List<Dropped> files, @Nullable String text, SendAction action) {
    StringBuilder info = new StringBuilder(Lang.getString(R.string.XgramDropSendTo, c.tdlib().chatTitle(chatId)));
    String what;
    if (files.isEmpty()) {
      what = text != null && text.length() > 100 ? text.substring(0, 100) + "\u2026" : text;
    } else if (files.size() == 1) {
      what = files.get(0).file.getName();
    } else {
      what = Lang.plural(R.string.xFiles, files.size());
    }
    if (!StringUtils.isEmpty(what)) {
      info.append('\n').append(what);
    }
    boolean hasMedia = false;
    for (Dropped file : files) {
      if (file.isMedia()) {
        hasMedia = true;
        break;
      }
    }
    int[] ids, colors, icons;
    String[] titles;
    if (hasMedia) {
      ids = new int[] {R.id.btn_send, R.id.btn_sendAsFile, R.id.btn_cancel};
      titles = new String[] {
        Lang.getString(R.string.Send),
        files.size() == 1 ? Lang.getString(R.string.SendAsFile) : Lang.plural(R.string.SendAsXFiles, files.size()),
        Lang.getString(R.string.Cancel)
      };
      colors = new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL};
      icons = new int[] {R.drawable.baseline_send_24, R.drawable.baseline_insert_drive_file_24, R.drawable.baseline_cancel_24};
    } else {
      ids = new int[] {R.id.btn_send, R.id.btn_cancel};
      titles = new String[] {Lang.getString(R.string.Send), Lang.getString(R.string.Cancel)};
      colors = new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL};
      icons = new int[] {R.drawable.baseline_send_24, R.drawable.baseline_cancel_24};
    }
    c.showOptions(info.toString(), ids, titles, colors, icons, (optionView, id) -> {
      if (id == R.id.btn_send) {
        action.send(false);
      } else if (id == R.id.btn_sendAsFile) {
        action.send(true);
      } else if (!files.isEmpty()) {
        deleteFiles(files);
      }
      return true;
    });
  }

  /** Worker thread: message contents the same way as files shared to XGram from another app. */
  private static List<TdApi.InputMessageContent> buildContents (Tdlib tdlib, long chatId, List<Dropped> files, @Nullable String text, boolean asFiles) {
    ArrayList<TdApi.InputMessageContent> out = new ArrayList<>();
    String caption = StringUtils.isEmpty(text) ? null : text;
    for (Dropped file : files) {
      try {
        if (asFiles) {
          TdApi.FormattedText fileCaption = null;
          if (caption != null && caption.codePointCount(0, caption.length()) <= tdlib.maxCaptionLength()) {
            fileCaption = new TdApi.FormattedText(caption, null);
            caption = null;
          }
          out.add(new TdApi.InputMessageDocument(new TdApi.InputDocument(TD.createInputFile(file.file.getPath(), file.mimeType), null, false), fileCaption));
        } else if (MainController.addShareUri(tdlib, out, file.mimeType, Uri.fromFile(file.file), caption)) {
          caption = null;
        }
      } catch (Throwable t) {
        Log.e("Unable to prepare a dropped file", t);
      }
    }
    if (caption != null) {
      out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(caption, null), null, false), tdlib.maxMessageTextLength()));
    }
    boolean isSecretChat = ChatId.isSecret(chatId);
    for (int i = 0; i < out.size(); i++) {
      TdApi.InputMessageContent content = tdlib.filegen().createThumbnail(out.get(i), isSecretChat);
      out.set(i, XgramLinkFixer.applyToContent(chatId, content, tdlib.maxMessageTextLength()));
    }
    return out;
  }

  private static void send (Tdlib tdlib, long chatId, @Nullable TdApi.MessageTopic topicId, @Nullable TdApi.InputMessageReplyTo replyTo, TdApi.MessageSendOptions sendOptions, List<TdApi.InputMessageContent> contents) {
    List<TdApi.Function<?>> functions = TD.toFunctions(chatId, topicId, replyTo, sendOptions, contents.toArray(new TdApi.InputMessageContent[0]), true);
    for (TdApi.Function<?> function : functions) {
      tdlib.client().send(function, tdlib.messageHandler());
    }
  }

  /** Drop target highlight in the view's overlay: the whole chat with a label, or one chat row. */
  @RequiresApi(Build.VERSION_CODES.N)
  private static final class Highlight extends Drawable {
    private final View host;
    private final boolean wholeView;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    @Nullable private String label;
    private boolean isShown;

    Highlight (View host, boolean wholeView) {
      this.host = host;
      this.wholeView = wholeView;
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeWidth(Screen.dp(2f));
      if (wholeView) {
        stroke.setPathEffect(new DashPathEffect(new float[] {Screen.dp(8f), Screen.dp(6f)}, 0));
      }
      labelPaint.setTypeface(Fonts.getRobotoMedium());
      labelPaint.setTextSize(Screen.dp(17f));
    }

    void show (int left, int top, int right, int bottom, @Nullable String label) {
      int accent = Theme.getColor(ColorId.textLink);
      if (wholeView) {
        int filling = Theme.getColor(ColorId.filling);
        fill.setColor((filling & 0x00ffffff) | 0xe0000000);
      } else {
        fill.setColor((accent & 0x00ffffff) | 0x29000000);
      }
      stroke.setColor(accent);
      labelPaint.setColor(accent);
      this.label = label;
      setBounds(left, top, right, bottom);
      if (!isShown) {
        isShown = true;
        host.getOverlay().add(this);
      }
      invalidateSelf();
    }

    void hide () {
      if (isShown) {
        isShown = false;
        host.getOverlay().remove(this);
        host.invalidate();
      }
    }

    @Override
    public void draw (@NonNull Canvas canvas) {
      Rect bounds = getBounds();
      float inset = Screen.dp(wholeView ? 12f : 4f);
      float radius = Screen.dp(wholeView ? 16f : 12f);
      rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
      if (rect.width() <= 0 || rect.height() <= 0) {
        return;
      }
      canvas.drawRoundRect(rect, radius, radius, fill);
      canvas.drawRoundRect(rect, radius, radius, stroke);
      if (label != null) {
        float x = rect.centerX() - labelPaint.measureText(label) / 2f;
        float y = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f;
        canvas.drawText(label, x, y, labelPaint);
      }
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  // Drag out of the media viewer

  /**
   * Hold a photo or video in the media viewer, then move the finger: it is dragged out as a file to another app
   * (split screen, freeform, DeX). Only for media that can be saved, never secret or view-once media, and only when
   * another app can be next to XGram.
   */
  public static final class DragOut implements Runnable {
    /** Results of {@link #onTouchEvent}: dispatch as usual, cancel the gesture underneath, or swallow the event. */
    public static final int PASS = 0, CANCEL = 1, CONSUME = 2;

    private final MediaView view;
    private float downX, downY;
    private boolean down, armed, dragging;

    public DragOut (MediaView view) {
      this.view = view;
    }

    public int onTouchEvent (MotionEvent e) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        return PASS;
      }
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          reset();
          dragging = false;
          if (canStart()) {
            down = true;
            downX = e.getX();
            downY = e.getY();
            view.postDelayed(this, ViewConfiguration.getLongPressTimeout());
          }
          return PASS;
        }
        case MotionEvent.ACTION_MOVE: {
          if (dragging) {
            return CONSUME;
          }
          if (down && Math.max(Math.abs(e.getX() - downX), Math.abs(e.getY() - downY)) > Screen.getTouchSlop()) {
            boolean wasArmed = armed;
            reset();
            if (wasArmed && startDrag()) {
              dragging = true;
              return CANCEL;
            }
          }
          return PASS;
        }
        case MotionEvent.ACTION_POINTER_DOWN: {
          reset();
          return dragging ? CONSUME : PASS;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          boolean wasArmed = armed;
          reset();
          if (dragging) {
            dragging = false;
            return CONSUME;
          }
          // Held without moving: no tap underneath (it would show or hide the controls).
          return wasArmed && e.getActionMasked() == MotionEvent.ACTION_UP ? CANCEL : PASS;
        }
      }
      return dragging ? CONSUME : PASS;
    }

    private void reset () {
      down = armed = false;
      view.removeCallbacks(this);
    }

    @Override
    public void run () {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && down && canStart()) {
        armed = true;
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private boolean canStart () {
      if (!isEnabled() || view.getBaseCell() == null || view.isZoomed() || view.isMovingItem() || file() == null) {
        return false;
      }
      BaseActivity activity = UI.getContext(view.getContext());
      return activity != null && (activity.isInMultiWindowMode() || activity.getResources().getConfiguration().smallestScreenWidthDp >= 600);
    }

    @Nullable
    private TdApi.File file () {
      MediaCellView cell = view.getBaseCell();
      MediaItem item = cell != null ? cell.getMedia() : null;
      if (item == null || item.isSecret() || item.isViewOnce() || !item.canBeSaved()) {
        return null;
      }
      if (item.getSourceChatId() != 0 && ChatId.isSecret(item.getSourceChatId())) {
        return null;
      }
      TdApi.File file = item.getShareFile();
      return file != null && file.local != null && !StringUtils.isEmpty(file.local.path) ? file : null;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private boolean startDrag () {
      MediaCellView cell = view.getBaseCell();
      TdApi.File tdFile = file();
      if (cell == null || tdFile == null) {
        return false;
      }
      File file = new File(tdFile.local.path);
      Uri uri = U.contentUriFromFile(file);
      if (uri == null) {
        return false;
      }
      String mimeType = cell.getMedia().getShareMimeType();
      if (mimeType == null || mimeType.endsWith("/*")) {
        String byName = TGMimeType.mimeTypeForExtension(U.getExtension(file.getName()));
        if (!StringUtils.isEmpty(byName)) {
          mimeType = byName;
        } else if (mimeType == null) {
          mimeType = "application/octet-stream";
        }
      }
      ClipData clip = new ClipData(new ClipDescription(file.getName(), new String[] {mimeType}), new ClipData.Item(uri));
      try {
        return view.startDragAndDrop(clip, new Shadow(cell), DRAG_OUT_STATE, View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ);
      } catch (Throwable t) {
        Log.e("Unable to start dragging media", t);
        return false;
      }
    }
  }

  /** A small copy of the photo or video frame under the finger. */
  private static final class Shadow extends View.DragShadowBuilder {
    private final View cell;
    private final Rect source = new Rect();
    private final float scale;
    private final int width, height;

    Shadow (MediaCellView cell) {
      super(cell);
      this.cell = cell;
      Receiver receiver = cell.getReceiver();
      if (receiver != null) {
        source.set(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
      }
      if (source.isEmpty() || !source.intersect(0, 0, cell.getWidth(), cell.getHeight())) {
        source.set(0, 0, Math.max(1, cell.getWidth()), Math.max(1, cell.getHeight()));
      }
      int maxSize = Screen.dp(160f);
      scale = Math.min(1f, maxSize / (float) Math.max(source.width(), source.height()));
      width = Math.max(1, Math.round(source.width() * scale));
      height = Math.max(1, Math.round(source.height() * scale));
    }

    @Override
    public void onProvideShadowMetrics (Point outShadowSize, Point outShadowTouchPoint) {
      outShadowSize.set(width, height);
      outShadowTouchPoint.set(width / 2, height / 2);
    }

    @Override
    public void onDrawShadow (@NonNull Canvas canvas) {
      int saveCount = canvas.save();
      try {
        canvas.scale(scale, scale);
        canvas.translate(-source.left, -source.top);
        canvas.clipRect(source);
        cell.draw(canvas);
      } catch (Throwable t) {
        Log.w("Unable to draw the drag shadow", t);
      } finally {
        canvas.restoreToCount(saveCount);
      }
    }
  }
}
