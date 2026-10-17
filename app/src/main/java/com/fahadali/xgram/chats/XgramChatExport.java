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
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      xgramPrompt(context, chatId);
      return;
    }
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

  // XGram M3E r6: the format as two selectable cards and a filled Export in the sheet's actions row

  private static void xgramPrompt (ViewController<?> c, long chatId) {
    final boolean[] html = {true};
    c.showPopup(Lang.getString(R.string.XgramExportChat), true, org.thunderdog.challegram.util.text.Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      final Context context = optionsLayout.getContext();
      final int width = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidth(c.context().getControllerWidth(optionsLayout));
      final int side = org.thunderdog.challegram.tool.Screen.dp(16f);

      android.widget.LinearLayout column = new android.widget.LinearLayout(context);
      column.setOrientation(android.widget.LinearLayout.VERTICAL);
      column.setPadding(side, 0, side, org.thunderdog.challegram.tool.Screen.dp(8f));

      android.widget.TextView info = new org.thunderdog.challegram.widget.NoScrollTextView(context);
      com.fahadali.xgram.appearance.XgramExpressive.applyText(info, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
      info.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT));
      info.setTextAlignment(android.view.View.TEXT_ALIGNMENT_VIEW_START);
      info.setText(Lang.getString(R.string.XgramExportChatInfo));
      android.widget.LinearLayout.LayoutParams infoParams = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
      infoParams.bottomMargin = org.thunderdog.challegram.tool.Screen.dp(16f);
      column.addView(info, infoParams);

      final FormatCard htmlCard = new FormatCard(context, R.drawable.baseline_code_24, Lang.getString(R.string.XgramR6B3ExportHtml), Lang.getString(R.string.XgramR6B3ExportHtmlInfo));
      final FormatCard textCard = new FormatCard(context, R.drawable.baseline_format_text_24, Lang.getString(R.string.XgramExportText), Lang.getString(R.string.XgramR6B3ExportTextInfo));
      htmlCard.setChecked(true, false);
      htmlCard.setOnClickListener(v -> {
        html[0] = true;
        htmlCard.setChecked(true, true);
        textCard.setChecked(false, true);
      });
      textCard.setOnClickListener(v -> {
        html[0] = false;
        htmlCard.setChecked(false, true);
        textCard.setChecked(true, true);
      });
      column.addView(htmlCard, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
      android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
      textParams.topMargin = org.thunderdog.challegram.tool.Screen.dp(8f);
      column.addView(textCard, textParams);
      optionsLayout.addView(column, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
      column.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
      int height = column.getMeasuredHeight();

      android.widget.TextView cancel = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context, Lang.getString(R.string.Cancel), com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
      cancel.setOnClickListener(v -> popupLayout.hideWindow(true));
      android.widget.TextView export = com.fahadali.xgram.appearance.XgramR5Sheets.filledButton(context, Lang.getString(R.string.XgramR6B3Export), false, null);
      export.setOnClickListener(v -> {
        popupLayout.hideWindow(true);
        start(c, chatId, html[0]);
      });
      android.widget.LinearLayout actions = com.fahadali.xgram.appearance.XgramR5Sheets.actionsRow(context, null, cancel, export);
      optionsLayout.addView(actions);
      height += ((android.widget.LinearLayout.LayoutParams) actions.getLayoutParams()).height;
      return height;
    }, null, com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_S);
  }

  /**
   * A format choice: a selectable card (surfaceBright, secondaryContainer with a check badge when chosen, the corners
   * springing to 28dp) with a leading icon, a bodyLarge label and a bodyMedium supporting line, colors read at draw time.
   */
  private static final class FormatCard extends android.widget.LinearLayout {
    private final com.fahadali.xgram.appearance.XgramR5Security.SelectableCard card;
    private final android.widget.ImageView icon;
    private final android.widget.TextView label, supporting;

    FormatCard (Context context, int iconRes, CharSequence labelText, CharSequence supportingText) {
      super(context);
      setOrientation(HORIZONTAL);
      setGravity(android.view.Gravity.CENTER_VERTICAL);
      setMinimumHeight(org.thunderdog.challegram.tool.Screen.dp(72f));
      setPaddingRelative(org.thunderdog.challegram.tool.Screen.dp(16f), org.thunderdog.challegram.tool.Screen.dp(12f), org.thunderdog.challegram.tool.Screen.dp(40f), org.thunderdog.challegram.tool.Screen.dp(12f));
      setWillNotDraw(false);
      card = com.fahadali.xgram.appearance.XgramR5Security.attachSelectableCard(this);
      org.thunderdog.challegram.tool.Views.setClickable(this);

      icon = new android.widget.ImageView(context);
      icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
      icon.setImageDrawable(org.thunderdog.challegram.tool.Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(iconRes)));
      icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(org.thunderdog.challegram.tool.Screen.dp(24f), org.thunderdog.challegram.tool.Screen.dp(24f));
      iconParams.setMarginEnd(org.thunderdog.challegram.tool.Screen.dp(16f));
      addView(icon, iconParams);

      android.widget.LinearLayout texts = new android.widget.LinearLayout(context);
      texts.setOrientation(VERTICAL);
      label = new org.thunderdog.challegram.widget.NoScrollTextView(context);
      com.fahadali.xgram.appearance.XgramExpressive.applyText(label, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false);
      label.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
      label.setText(labelText);
      texts.addView(label);
      supporting = new org.thunderdog.challegram.widget.NoScrollTextView(context);
      com.fahadali.xgram.appearance.XgramExpressive.applyText(supporting, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
      supporting.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
      supporting.setText(supportingText);
      texts.addView(supporting);
      addView(texts, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      syncColors();
    }

    void setChecked (boolean checked, boolean animated) {
      card.setChecked(checked, animated);
      setSelected(checked);
      invalidate();
    }

    private void syncColors () {
      final int content = card.contentColor(false), variant = card.contentColor(true);
      if (label.getCurrentTextColor() != content) {
        label.setTextColor(content);
      }
      if (supporting.getCurrentTextColor() != variant) {
        supporting.setTextColor(variant);
      }
      if (iconColor != variant || iconColor == 0) {
        iconColor = variant;
        icon.setColorFilter(variant, android.graphics.PorterDuff.Mode.SRC_IN);
      }
    }

    private int iconColor;

    @Override
    protected void dispatchDraw (android.graphics.Canvas c) {
      syncColors(); // the label and icon follow the card's color spring and theme changes
      super.dispatchDraw(c);
      final float cx = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? org.thunderdog.challegram.tool.Screen.dp(20f) : getWidth() - org.thunderdog.challegram.tool.Screen.dp(20f);
      card.drawBadge(c, cx, getHeight() / 2f);
    }
  }

  private static void start (ViewController<?> context, long chatId, boolean html) {
    Tdlib tdlib = context.tdlib();
    // XGram M3E r6: a persistent progress bar with Cancel instead of the toast
    final java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean();
    final com.fahadali.xgram.appearance.XgramR6B3Shell.ProgressBar[] bar = new com.fahadali.xgram.appearance.XgramR6B3Shell.ProgressBar[1];
    bar[0] = com.fahadali.xgram.appearance.XgramR6B3Shell.ProgressBar.show(Lang.getString(R.string.XgramR6B3ExportPreparing), Lang.getString(R.string.Cancel), () -> {
      canceled.set(true);
      if (bar[0] != null) {
        bar[0].dismiss();
      }
    });
    if (bar[0] == null) {
      UI.showToast(R.string.XgramExportStarted, Toast.LENGTH_SHORT);
    }
    List<TdApi.Message> collected = new ArrayList<>();
    final long[] newest = {0};
    loadPage(tdlib, chatId, 0, collected, canceled, (count, oldestId) -> {
      if (bar[0] == null) {
        return;
      }
      if (newest[0] == 0) {
        newest[0] = collected.isEmpty() ? 0 : collected.get(0).id;
      }
      // An estimate from the server message ids (consecutive in a channel or supergroup): how far back the oldest one is
      final long top = newest[0] >> 20, now = oldestId >> 20;
      final float fraction = top > 1 ? Math.max(0f, Math.min(1f, (float) (top - now) / (float) (top - 1))) : 0f;
      final String text = Lang.getString(R.string.XgramR6B3ExportProgress, Lang.plural(R.string.xMessages, count));
      tdlib.ui().post(() -> {
        if (!canceled.get() && bar[0] != null) {
          bar[0].setText(text);
          bar[0].setProgress(fraction);
        }
      });
    }, () -> {
      if (canceled.get()) {
        return;
      }
      if (bar[0] != null) {
        tdlib.ui().post(() -> bar[0].setProgress(1f));
      }
      // Writing happens off the TDLib thread.
      new Thread(() -> finish(context, tdlib, chatId, collected, html, canceled, bar[0]), "XgramChatExport").start();
    });
  }

  private interface PageListener {
    void onPage (int count, long oldestMessageId);
  }

  private static void loadPage (Tdlib tdlib, long chatId, long fromMessageId, List<TdApi.Message> out, java.util.concurrent.atomic.AtomicBoolean canceled, @Nullable PageListener onPage, Runnable done) {
    if (canceled.get()) {
      return;
    }
    tdlib.send(new TdApi.GetChatHistory(chatId, fromMessageId, 0, PAGE, false), (messages, error) -> {
      if (canceled.get()) {
        return;
      }
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
        if (onPage != null) {
          onPage.onPage(out.size(), oldest);
        }
        loadPage(tdlib, chatId, oldest, out, canceled, onPage, done);
      }
    });
  }

  /** The export failed: the snackbar with Expressive design (the progress bar goes first), Telegram X's toast otherwise. */
  private static void showFailed (Tdlib tdlib, @Nullable com.fahadali.xgram.appearance.XgramR6B3Shell.ProgressBar bar) {
    tdlib.ui().post(() -> {
      if (bar != null) {
        bar.dismiss();
      }
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        com.fahadali.xgram.appearance.XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportFailed), false);
      } else {
        UI.showToast(R.string.XgramExportFailed, Toast.LENGTH_SHORT);
      }
    });
  }

  private static void finish (ViewController<?> context, Tdlib tdlib, long chatId, List<TdApi.Message> newestFirst, boolean html, java.util.concurrent.atomic.AtomicBoolean canceled, @Nullable com.fahadali.xgram.appearance.XgramR6B3Shell.ProgressBar bar) {
    String title = tdlib.chatTitle(chatId);
    String content;
    try {
      content = html ? buildHtml(tdlib, chatId, title, newestFirst) : buildText(tdlib, chatId, title, newestFirst);
    } catch (Throwable t) {
      showFailed(tdlib, bar);
      return;
    }
    if (canceled.get()) {
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
      showFailed(tdlib, bar);
      return;
    }
    final boolean inDownloads = savedToDownloads;
    final int count = newestFirst.size();
    tdlib.ui().post(() -> {
      if (bar != null) {
        bar.dismiss();
      }
      if (canceled.get()) {
        return;
      }
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram M3E r6: the result in an info sheet (or the snackbar when the chat is gone)
        if (context.isDestroyed()) {
          com.fahadali.xgram.appearance.XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportDone), false);
          return;
        }
        xgramShowDone(context, file, fileName, mime, title, count, inDownloads);
        return;
      }
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

  /**
   * XGram M3E r6: the exported file as an info sheet (file name, message count, where it is; tapping a row copies it)
   * with a tonal Open and a filled Share.
   */
  private static void xgramShowDone (ViewController<?> context, File file, String fileName, String mime, String title, int count, boolean inDownloads) {
    List<com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row> rows = new ArrayList<>(3);
    rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(fileName, Lang.getString(R.string.XgramR6B3ExportFileName)).copy(fileName));
    rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(Lang.formatNumber(count), Lang.getString(R.string.XgramR6B3ExportMessages)).tabular());
    final String location = inDownloads ? "Download/XGram" : Lang.getString(R.string.XgramR6B3ExportAppStorage);
    rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(location, Lang.getString(R.string.XgramR6B3ExportLocation)));
    com.fahadali.xgram.appearance.XgramR5SheetsInfo.show(context, Lang.getString(R.string.XgramExportDone), rows,
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.XgramExportOpen), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_TONAL, () -> openOrShare(context, file, mime, title, false)),
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.Share), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_FILLED, () -> openOrShare(context, file, mime, title, true))
    );
  }

  private static void openOrShare (ViewController<?> context, File file, String mime, String title, boolean share) {
    if (context.isDestroyed()) {
      return;
    }
    Uri uri = U.contentUriFromFile(file);
    if (uri == null) {
      com.fahadali.xgram.appearance.XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportNoApp), false);
      return;
    }
    Intent intent;
    if (share) {
      intent = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title);
      intent = Intent.createChooser(intent, Lang.getString(R.string.Share));
    } else {
      intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try {
      context.context().startActivity(intent);
    } catch (Throwable t) {
      com.fahadali.xgram.appearance.XgramR6B3Shell.snackbar(Lang.getString(R.string.XgramExportNoApp), false);
    }
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
