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

import android.util.Base64;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGInlineKeyboard;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

/**
 * Long-press options for inline keyboard buttons (AyuGram / NagramX): copy the button text, its URL, callback data,
 * inline query or user ID. Telegram X only offered Open / Copy link / Share for URL buttons; those stay.
 */
public final class XgramBotButtons {
  private XgramBotButtons () { }

  private static final int MAX_VALUE_PREVIEW = 32;

  /** Shows the options; false lets Telegram X handle the long press. Called on the UI thread only on long-press. */
  public static boolean showOptions (@NonNull ViewController<?> c, @NonNull TGInlineKeyboard.Button button, @NonNull View view) {
    final TdApi.InlineKeyboardButtonType type = button.getType();
    if (type == null) {
      return false;
    }
    final String text = button.getRawText();
    String url = null;
    boolean canOpen = false;
    byte[] data = null;
    String query = null;
    long userId = 0;
    String value = null;
    switch (type.getConstructor()) {
      case TdApi.InlineKeyboardButtonTypeUrl.CONSTRUCTOR:
        url = ((TdApi.InlineKeyboardButtonTypeUrl) type).url;
        canOpen = true;
        break;
      case TdApi.InlineKeyboardButtonTypeLoginUrl.CONSTRUCTOR:
        url = ((TdApi.InlineKeyboardButtonTypeLoginUrl) type).url;
        canOpen = true;
        break;
      case TdApi.InlineKeyboardButtonTypeWebApp.CONSTRUCTOR:
        // Telegram X can't open web app buttons yet, so no "Open".
        url = ((TdApi.InlineKeyboardButtonTypeWebApp) type).url;
        break;
      case TdApi.InlineKeyboardButtonTypeCallback.CONSTRUCTOR:
        data = ((TdApi.InlineKeyboardButtonTypeCallback) type).data;
        break;
      case TdApi.InlineKeyboardButtonTypeCallbackWithPassword.CONSTRUCTOR:
        data = ((TdApi.InlineKeyboardButtonTypeCallbackWithPassword) type).data;
        break;
      case TdApi.InlineKeyboardButtonTypeSwitchInline.CONSTRUCTOR:
        query = ((TdApi.InlineKeyboardButtonTypeSwitchInline) type).query;
        break;
      case TdApi.InlineKeyboardButtonTypeUser.CONSTRUCTOR:
        userId = ((TdApi.InlineKeyboardButtonTypeUser) type).userId;
        break;
      case TdApi.InlineKeyboardButtonTypeCopyText.CONSTRUCTOR:
        value = ((TdApi.InlineKeyboardButtonTypeCopyText) type).text;
        break;
      default:
        // Buy, CallbackGame, Disabled and future types: button text only.
        break;
    }
    if (StringUtils.isEmpty(url)) {
      url = null;
      canOpen = false;
    }
    if (data != null && data.length == 0) {
      data = null;
    }
    if (StringUtils.isEmpty(query)) {
      query = null;
    }
    if (StringUtils.isEmpty(value)) {
      value = null;
    }
    final boolean hasText = !StringUtils.isEmpty(text);

    final String decoded = data != null ? decodePrintable(data) : null;
    final boolean callbackIsBase64 = data != null && decoded == null;
    final String callbackValue = data == null ? null : decoded != null ? decoded : Base64.encodeToString(data, Base64.NO_WRAP);

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return xgramShowSheet(c, button, view, text, hasText, value, url, canOpen, callbackValue, callbackIsBase64, query, userId);
    }

    IntList ids = new IntList(8);
    StringList strings = new StringList(8);
    IntList icons = new IntList(8);
    if (canOpen) {
      ids.append(R.id.btn_openLink);
      strings.append(R.string.Open);
      icons.append(R.drawable.baseline_open_in_browser_24);
    }
    if (hasText) {
      ids.append(R.id.btn_xgramCopyButtonText);
      strings.append(R.string.XgramBotButtonCopyText);
      icons.append(R.drawable.baseline_content_copy_24);
    }
    if (value != null) {
      ids.append(R.id.btn_copyText);
      strings.append(Lang.getString(R.string.XgramBotButtonCopyValue, preview(value)));
      icons.append(R.drawable.baseline_content_copy_24);
    }
    if (url != null) {
      ids.append(R.id.btn_copyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);
    }
    if (callbackValue != null) {
      ids.append(R.id.btn_xgramCopyCallbackData);
      strings.append(callbackIsBase64 ? R.string.XgramBotButtonCopyCallbackBase64 : R.string.XgramBotButtonCopyCallback);
      icons.append(R.drawable.baseline_code_24);
    }
    if (query != null) {
      ids.append(R.id.btn_xgramCopyInlineQuery);
      strings.append(R.string.XgramBotButtonCopyQuery);
      icons.append(R.drawable.baseline_alternate_email_24);
    }
    if (userId != 0) {
      ids.append(R.id.btn_xgramCopyUserId);
      strings.append(R.string.XgramBotButtonCopyUserId);
      icons.append(R.drawable.baseline_person_24);
    }
    if (canOpen) {
      ids.append(R.id.btn_shareLink);
      strings.append(R.string.Share);
      icons.append(R.drawable.baseline_forward_24);
    }
    if (ids.isEmpty()) {
      return false;
    }

    final String fUrl = url, fQuery = query, fValue = value;
    final long fUserId = userId;
    final CharSequence info = hasText ? (fUrl != null ? text + "\n" + fUrl : text) : fUrl;
    final boolean[] shared = {false};
    c.showOptions(info, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      if (id == R.id.btn_openLink) {
        button.performOpen(view);
      } else if (id == R.id.btn_xgramCopyButtonText) {
        UI.copyText(text, R.string.CopiedText);
      } else if (id == R.id.btn_copyText) {
        UI.copyText(fValue, R.string.CopiedText);
      } else if (id == R.id.btn_copyLink) {
        UI.copyText(fUrl, R.string.CopiedLink);
      } else if (id == R.id.btn_xgramCopyCallbackData) {
        UI.copyText(callbackValue, R.string.XgramBotButtonCallbackCopied);
      } else if (id == R.id.btn_xgramCopyInlineQuery) {
        UI.copyText(fQuery, R.string.CopiedText);
      } else if (id == R.id.btn_xgramCopyUserId) {
        UI.copyText(Long.toString(fUserId), R.string.CopiedPeerId);
      } else if (id == R.id.btn_shareLink) {
        if (!shared[0]) {
          shared[0] = true;
          TD.shareLink(c, fUrl);
        }
      }
      return true;
    });
    return true;
  }

  /**
   * XGram M3E r6: the button as an info sheet titled with its text: a row per value (button text, text to copy, link,
   * callback data in tabular figures, inline query, user ID), each copied with the "Copied" snackbar on a tap, and a
   * filled Open with a tonal Share for a link the app can open. No Cancel row: the scrim, a drag or back closes it.
   */
  private static boolean xgramShowSheet (@NonNull ViewController<?> c, @NonNull TGInlineKeyboard.Button button, @NonNull View view, @Nullable String text, boolean hasText,
                                         @Nullable String value, @Nullable String url, boolean canOpen, @Nullable String callbackValue, boolean callbackIsBase64,
                                         @Nullable String query, long userId) {
    java.util.List<com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row> rows = new java.util.ArrayList<>(6);
    if (hasText) {
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(text, Lang.getString(R.string.XgramR6B3BotButtonText)).copy(text));
    }
    if (value != null) {
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(value, Lang.getString(R.string.XgramR6B3BotCopyText)).copy(value));
    }
    if (url != null) {
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(url, Lang.getString(R.string.XgramR5SheetsLinkLabel)).copy(url));
    }
    if (callbackValue != null) {
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(callbackValue, Lang.getString(callbackIsBase64 ? R.string.XgramR6B3BotCallbackBase64 : R.string.XgramR6B3BotCallback)).tabular().copy(callbackValue));
    }
    if (query != null) {
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(query, Lang.getString(R.string.XgramR6B3BotInlineQuery)).copy(query));
    }
    if (userId != 0) {
      final String id = Long.toString(userId);
      rows.add(new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Row(id, Lang.getString(R.string.XgramR6B3BotUserId)).tabular().copy(id));
    }
    if (rows.isEmpty() && !canOpen) {
      return false;
    }
    final String fUrl = url;
    final boolean[] shared = {false};
    final com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action open = canOpen ?
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.Open), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_FILLED, () -> button.performOpen(view)) : null;
    final com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action share = canOpen ?
      new com.fahadali.xgram.appearance.XgramR5SheetsInfo.Action(Lang.getString(R.string.Share), com.fahadali.xgram.appearance.XgramR5SheetsInfo.ACTION_TONAL, () -> {
        if (!shared[0]) {
          shared[0] = true;
          TD.shareLink(c, fUrl);
        }
      }) : null;
    final CharSequence title = hasText ? text : fUrl;
    if (canOpen) {
      com.fahadali.xgram.appearance.XgramR5SheetsInfo.show(c, title, rows, share, open);
    } else {
      com.fahadali.xgram.appearance.XgramR5SheetsInfo.show(c, title, rows);
    }
    return true;
  }

  /** Strict UTF-8 decode; null when the bytes aren't valid UTF-8 or contain control characters (then Base64 is used). */
  @Nullable
  static String decodePrintable (@NonNull byte[] data) {
    final String s;
    try {
      s = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(data))
        .toString();
    } catch (CharacterCodingException e) {
      return null;
    }
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (Character.isISOControl(cp) && cp != '\n' && cp != '\t' && cp != '\r') {
        return null;
      }
      i += Character.charCount(cp);
    }
    return s;
  }

  private static String preview (String value) {
    String oneLine = value.replace('\n', ' ');
    return oneLine.length() > MAX_VALUE_PREVIEW ? oneLine.substring(0, MAX_VALUE_PREVIEW) + "…" : oneLine;
  }
}
