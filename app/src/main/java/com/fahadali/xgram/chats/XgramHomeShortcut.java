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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibNotificationUtils;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;

/**
 * Add to Home Screen (Telegram's, in a profile's ⋮ menu and the chat's): a launcher shortcut that opens the chat, with
 * its photo (round) or its letters and its name. The shortcut carries the account and the chat's own id, so it keeps
 * working after the app restarts.
 */
public final class XgramHomeShortcut {
  private XgramHomeShortcut () { }

  /** The launcher can take a shortcut (asked before offering the option). */
  public static boolean isSupported (Context context) {
    return ShortcutManagerCompat.isRequestPinShortcutSupported(context);
  }

  /** A user's (or bot's) private chat, created first when there is none yet. */
  public static void addUser (Tdlib tdlib, long userId) {
    tdlib.send(new TdApi.CreatePrivateChat(userId, false), (chat, error) -> UI.post(() -> {
      if (chat != null) {
        add(tdlib, chat.id);
      }
    }));
  }

  public static void add (Tdlib tdlib, long chatId) {
    final TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return;
    }
    final TdApi.File photo = chat.photo != null && !tdlib.isSelfChat(chat) ? chat.photo.small : null;
    if (photo != null && !TD.isFileLoaded(photo)) {
      // The photo first (small: 160px), then the shortcut; its letters when it can't come
      tdlib.send(new TdApi.DownloadFile(photo.id, 32, 0, 0, true), (file, error) -> UI.post(() ->
        pin(tdlib, chat, file != null && TD.isFileLoaded(file) ? file.local.path : null)
      ));
    } else {
      pin(tdlib, chat, photo != null ? photo.local.path : null);
    }
  }

  private static void pin (Tdlib tdlib, TdApi.Chat chat, @Nullable String photoPath) {
    final Context context = UI.getContext();
    if (!isSupported(context)) {
      UI.showToast(R.string.XgramHomeScreenUnsupported, Toast.LENGTH_SHORT);
      return;
    }
    Bitmap icon = photoPath != null ? round(BitmapFactory.decodeFile(photoPath)) : null;
    if (icon == null) {
      icon = TdlibNotificationUtils.buildLargeIcon(tdlib, chat, false); // the letters (or Saved Messages' bookmark)
    }
    final String title = tdlib.chatTitle(chat.id);
    final Intent intent = new Intent(context, MainActivity.class);
    Intents.secureIntent(intent, true);
    intent.setAction(Intents.ACTION_OPEN_CHAT + "." + tdlib.id() + "." + chat.id);
    intent.putExtra("account_id", tdlib.id());
    intent.putExtra("chat_id", chat.id);
    final ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, "xgram_home_" + tdlib.id() + "_" + chat.id)
      .setShortLabel(title)
      .setLongLabel(title)
      .setIntent(intent)
      .setIcon(icon != null ? IconCompat.createWithBitmap(icon) : IconCompat.createWithResource(context, R.drawable.baseline_chat_bubble_24))
      .build();
    if (!ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
      UI.showToast(R.string.XgramHomeScreenUnsupported, Toast.LENGTH_SHORT);
    }
  }

  /** The photo cut round, as launchers show app icons, at the size it comes in. */
  @Nullable
  private static Bitmap round (@Nullable Bitmap source) {
    if (source == null) {
      return null;
    }
    final int size = Math.min(source.getWidth(), source.getHeight());
    if (size <= 0) {
      return null;
    }
    final Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    final Canvas c = new Canvas(out);
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    final android.graphics.Matrix matrix = new android.graphics.Matrix();
    matrix.setTranslate(-(source.getWidth() - size) / 2f, -(source.getHeight() - size) / 2f);
    shader.setLocalMatrix(matrix);
    paint.setShader(shader);
    c.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    return out;
  }
}
