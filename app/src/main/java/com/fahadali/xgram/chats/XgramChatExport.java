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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.history.XgramMedia;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Chat ⋮ → Export chat (like Telegram Desktop): the whole history as a styled HTML page or plain text, saved to
 * Download/XGram and offered to share.
 */
public final class XgramChatExport {
  private XgramChatExport () { }

  private static final int PAGE = 100;
  private static final int MAX_MESSAGES = 50_000;

  public static void prompt (ViewController<?> context, long chatId) {
    context.showOptions(Lang.getString(R.string.XgramExportChatInfo),
      new int[] {R.id.btn_xgramExportHtml, R.id.btn_xgramExportText, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramExportHtml), Lang.getString(R.string.XgramExportText), Lang.getString(R.string.Cancel)},
      null,
      new int[] {R.drawable.baseline_code_24, R.drawable.baseline_format_text_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramExportHtml || id == R.id.btn_xgramExportText) {
          start(context, chatId, id == R.id.btn_xgramExportHtml);
        }
        return true;
      });
  }

  private static void start (ViewController<?> context, long chatId, boolean html) {
    Tdlib tdlib = context.tdlib();
    UI.showToast(R.string.XgramExportStarted, Toast.LENGTH_SHORT);
    List<TdApi.Message> collected = new ArrayList<>();
    loadPage(tdlib, chatId, 0, collected, () -> {
      // Writing happens off the TDLib thread.
      new Thread(() -> finish(context, tdlib, chatId, collected, html), "XgramChatExport").start();
    });
  }

  private static void loadPage (Tdlib tdlib, long chatId, long fromMessageId, List<TdApi.Message> out, Runnable done) {
    tdlib.send(new TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE, false), (messages, error) -> {
      if (messages == null || messages.messages.length == 0 || out.size() >= MAX_MESSAGES) {
        done.run();
        return;
      }
      long oldest = fromMessageId;
      for (TdApi.Message message : messages.messages) {
        if (message != null && (fromMessageId == 0 || message.id < fromMessageId)) {
          out.add(message);
          oldest = message.id;
        }
      }
      if (oldest == fromMessageId) {
        done.run();
      } else {
        loadPage(tdlib, chatId, oldest, out, done);
      }
    });
  }

  private static void finish (ViewController<?> context, Tdlib tdlib, long chatId, List<TdApi.Message> newestFirst, boolean html) {
    String title = tdlib.chatTitle(chatId);
    String content;
    try {
      content = html ? buildHtml(tdlib, chatId, title, newestFirst) : buildText(tdlib, chatId, title, newestFirst);
    } catch (Throwable t) {
      tdlib.ui().post(() -> UI.showToast(R.string.XgramExportFailed, Toast.LENGTH_SHORT));
      return;
    }
    String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
    String fileName = sanitize(title) + "_" + stamp + (html ? ".html" : ".txt");
    String mime = html ? "text/html" : "text/plain";
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    Context app = org.thunderdog.challegram.unsorted.AppContext.get();

    // A copy in app storage for sharing, and one in Download/XGram (Android 10+, no permission needed).
    File dir = new File(app.getFilesDir(), "xgram_exports");
    File file = new File(dir, fileName);
    boolean savedToDownloads = false;
    try {
      if (!dir.exists() && !dir.mkdirs()) {
        throw new IllegalStateException();
      }
      try (FileOutputStream stream = new FileOutputStream(file)) {
        stream.write(bytes);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        savedToDownloads = saveToDownloads(app.getContentResolver(), fileName, mime, bytes);
      }
    } catch (Throwable t) {
      tdlib.ui().post(() -> UI.showToast(R.string.XgramExportFailed, Toast.LENGTH_SHORT));
      return;
    }
    final boolean inDownloads = savedToDownloads;
    final int count = newestFirst.size();
    tdlib.ui().post(() -> {
      if (context.isDestroyed()) {
        UI.showToast(R.string.XgramExportDone, Toast.LENGTH_SHORT);
        return;
      }
      String info = Lang.getString(R.string.XgramExportDoneInfo, count, fileName) + (inDownloads ? "\n" + Lang.getString(R.string.XgramExportSavedDownloads) : "");
      context.showOptions(info,
        new int[] {R.id.btn_xgramExportShare, R.id.btn_xgramExportOpen, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Share), Lang.getString(R.string.XgramExportOpen), Lang.getString(R.string.Done)},
        null,
        new int[] {R.drawable.baseline_share_24, R.drawable.baseline_open_in_browser_24, R.drawable.baseline_cancel_24},
        (itemView, id) -> {
          Uri uri = U.contentUriFromFile(file);
          if (uri == null) {
            return true;
          }
          Intent intent;
          if (id == R.id.btn_xgramExportShare) {
            intent = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title);
            intent = Intent.createChooser(intent, Lang.getString(R.string.Share));
          } else if (id == R.id.btn_xgramExportOpen) {
            intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
          } else {
            return true;
          }
          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          try {
            context.context().startActivity(intent);
          } catch (Throwable t) {
            UI.showToast(R.string.XgramExportNoApp, Toast.LENGTH_SHORT);
          }
          return true;
        });
    });
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
  private static boolean saveToDownloads (ContentResolver resolver, String fileName, String mime, byte[] bytes) {
    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
    values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
    values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/XGram");
    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    if (uri == null) {
      return false;
    }
    try (OutputStream stream = resolver.openOutputStream(uri)) {
      if (stream == null) {
        return false;
      }
      stream.write(bytes);
      return true;
    } catch (Throwable t) {
      resolver.delete(uri, null, null);
      return false;
    }
  }

  private static String sanitize (String name) {
    String clean = name == null ? "chat" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    if (clean.length() > 40) {
      clean = clean.substring(0, 40);
    }
    return clean.isEmpty() ? "chat" : clean;
  }

  // Content

  private static String messageText (Tdlib tdlib, long chatId, TdApi.Message message) {
    TdApi.FormattedText text = XgramMedia.formattedTextOf(message.content);
    String body = text != null ? text.text : "";
    if (message.content.getConstructor() != TdApi.MessageText.CONSTRUCTOR) {
      String label;
      try {
        label = ContentPreview.getChatListPreview(tdlib, chatId, message, false).buildText(false);
      } catch (Throwable t) {
        label = "";
      }
      if (body.isEmpty()) {
        body = "[" + label + "]";
      } else if (!label.isEmpty() && !label.equals(body)) {
        body = "[" + label.replace(body, "").trim().replaceAll("[,:]+$", "") + "] " + body;
      }
    }
    return body;
  }

  @Nullable
  private static String note (TdApi.Message message) {
    List<String> notes = new ArrayList<>(2);
    if (message.forwardInfo != null) {
      notes.add(Lang.getString(R.string.XgramExportForwarded));
    }
    if (message.replyTo instanceof TdApi.MessageReplyToMessage) {
      notes.add(Lang.getString(R.string.XgramExportReply));
    }
    if (message.editDate != 0) {
      notes.add(Lang.getString(R.string.XgramExportEdited));
    }
    return notes.isEmpty() ? null : android.text.TextUtils.join(" · ", notes);
  }

  private static String buildText (Tdlib tdlib, long chatId, String title, List<TdApi.Message> newestFirst) {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
    StringBuilder b = new StringBuilder();
    b.append(title).append('\n').append(Lang.getString(R.string.XgramExportHeader, newestFirst.size(), format.format(new Date()))).append("\n\n");
    for (int i = newestFirst.size() - 1; i >= 0; i--) {
      TdApi.Message message = newestFirst.get(i);
      String sender = message.isOutgoing ? Lang.getString(R.string.FromYou) : tdlib.senderName(message.senderId);
      b.append('[').append(format.format(new Date(message.date * 1000L))).append("] ").append(sender);
      String note = note(message);
      if (note != null) {
        b.append(" (").append(note).append(')');
      }
      b.append(": ").append(messageText(tdlib, chatId, message)).append('\n');
    }
    return b.toString();
  }

  private static String escape (String text) {
    StringBuilder b = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&': b.append("&amp;"); break;
        case '<': b.append("&lt;"); break;
        case '>': b.append("&gt;"); break;
        case '"': b.append("&quot;"); break;
        case '\n': b.append("<br>"); break;
        default: b.append(c);
      }
    }
    return b.toString();
  }

  private static final String[] NAME_COLORS = {"#e17076", "#7bc862", "#e5ca77", "#65aadd", "#a695e7", "#ee7aae", "#6ec9cb", "#faa774"};

  private static String buildHtml (Tdlib tdlib, long chatId, String title, List<TdApi.Message> newestFirst) {
    SimpleDateFormat day = new SimpleDateFormat("EEEE, d MMMM yyyy", Lang.locale());
    SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.US);
    StringBuilder b = new StringBuilder(newestFirst.size() * 220 + 2048);
    b.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
      .append("<title>").append(escape(title)).append("</title><style>")
      .append(":root{--bg:#eef1f5;--card:#fff;--text:#111;--muted:#7a8591;--out:#e1f3d2;--accent:#3390ec}")
      .append("@media(prefers-color-scheme:dark){:root{--bg:#0e1621;--card:#182533;--text:#e9eef3;--muted:#8796a5;--out:#2b5278;--accent:#5eb5f7}}")
      .append("body{margin:0;background:var(--bg);color:var(--text);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}")
      .append("header{position:sticky;top:0;background:var(--card);padding:14px 18px;box-shadow:0 1px 3px rgba(0,0,0,.15)}")
      .append("header h1{margin:0;font-size:18px}header p{margin:2px 0 0;color:var(--muted);font-size:13px}")
      .append("main{max-width:760px;margin:0 auto;padding:12px}")
      .append(".day{text-align:center;margin:18px 0 8px}.day span{background:rgba(0,0,0,.25);color:#fff;padding:3px 10px;border-radius:12px;font-size:13px}")
      .append(".m{display:flex;margin:3px 0}.m.out{justify-content:flex-end}")
      .append(".b{max-width:78%;background:var(--card);padding:6px 10px 5px;border-radius:14px;box-shadow:0 1px 1px rgba(0,0,0,.08);word-wrap:break-word}")
      .append(".out .b{background:var(--out)}.n{font-weight:600;font-size:14px;margin-bottom:1px}")
      .append(".note{color:var(--muted);font-size:12px;font-style:italic}.t{color:var(--muted);font-size:11px;text-align:right;margin-top:2px}")
      .append(".media{color:var(--accent)}footer{text-align:center;color:var(--muted);font-size:12px;padding:20px}")
      .append("</style></head><body><header><h1>").append(escape(title)).append("</h1><p>")
      .append(escape(Lang.getString(R.string.XgramExportHeader, newestFirst.size(), new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()))))
      .append("</p></header><main>");
    String lastDay = null;
    for (int i = newestFirst.size() - 1; i >= 0; i--) {
      TdApi.Message message = newestFirst.get(i);
      Date date = new Date(message.date * 1000L);
      String dayText = day.format(date);
      if (!dayText.equals(lastDay)) {
        b.append("<div class=\"day\"><span>").append(escape(dayText)).append("</span></div>");
        lastDay = dayText;
      }
      boolean out = message.isOutgoing;
      b.append("<div class=\"m").append(out ? " out" : "").append("\"><div class=\"b\">");
      if (!out) {
        long senderKey = message.senderId instanceof TdApi.MessageSenderUser ? ((TdApi.MessageSenderUser) message.senderId).userId : chatId;
        String color = NAME_COLORS[(int) (Math.abs(senderKey) % NAME_COLORS.length)];
        b.append("<div class=\"n\" style=\"color:").append(color).append("\">").append(escape(tdlib.senderName(message.senderId))).append("</div>");
      }
      String note = note(message);
      if (note != null) {
        b.append("<div class=\"note\">").append(escape(note)).append("</div>");
      }
      String text = messageText(tdlib, chatId, message);
      if (text.startsWith("[")) {
        int end = text.indexOf(']');
        if (end > 0) {
          b.append("<span class=\"media\">").append(escape(text.substring(0, end + 1))).append("</span>");
          text = text.substring(end + 1);
        }
      }
      b.append(escape(text)).append("<div class=\"t\">").append(time.format(date)).append("</div></div></div>");
    }
    b.append("</main><footer>").append(escape(Lang.getString(R.string.XgramExportFooter))).append("</footer></body></html>");
    return b.toString();
  }
}
