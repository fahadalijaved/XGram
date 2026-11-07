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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;

/**
 * XGram: drawables for menu rows that show more than an icon: an account's profile photo (its initials on its colour
 * when it has none) and an unread count badge, drawn as they are (not tinted like the icons).
 */
public final class XgramOptionDrawables {
  private XgramOptionDrawables () { }

  private static BitmapDrawable wrap (Resources res, Bitmap bitmap) {
    bitmap.setDensity(res.getDisplayMetrics().densityDpi);
    BitmapDrawable drawable = new BitmapDrawable(res, bitmap);
    drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
    return drawable;
  }

  /** The account's profile photo in a circle, its initials on its colour when there is none. */
  public static Drawable accountAvatar (Context context, TdlibAccount account) {
    final int size = Screen.dp(28f);
    final Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    final Canvas c = new Canvas(out);
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    Bitmap photo = null;
    final ImageFile file = account.getAvatarFile(false);
    if (file instanceof ImageFileLocal) {
      final String path = ((ImageFileLocal) file).getPath();
      if (path != null) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
          sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        photo = BitmapFactory.decodeFile(path, options);
      }
    }
    if (photo != null) {
      BitmapShader shader = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
      Matrix matrix = new Matrix();
      final float scale = (float) size / Math.min(photo.getWidth(), photo.getHeight());
      matrix.setScale(scale, scale);
      matrix.postTranslate((size - photo.getWidth() * scale) / 2f, (size - photo.getHeight() * scale) / 2f);
      shader.setLocalMatrix(matrix);
      paint.setShader(shader);
      c.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    } else {
      final AvatarPlaceholder.Metadata metadata = account.getAvatarPlaceholderMetadata();
      paint.setColor(metadata != null ? metadata.accentColor.getPrimaryColor() : 0xff8e8e93);
      c.drawCircle(size / 2f, size / 2f, size / 2f, paint);
      final String letters = metadata != null && metadata.letters != null ? metadata.letters.text : "";
      if (!letters.isEmpty()) {
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xffffffff);
        text.setTextSize(Screen.dp(12f));
        text.setTypeface(Fonts.getRobotoMedium());
        text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(letters, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, text);
      }
    }
    return wrap(context.getResources(), out);
  }

  /** An unread count as a badge (a pill in the primary colour), like Telegram's account list. */
  public static Drawable unreadBadge (Context context, int count) {
    final String text = Strings.buildCounter(count);
    final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTextSize(Screen.dp(12f));
    textPaint.setTypeface(Fonts.getRobotoMedium());
    textPaint.setTextAlign(Paint.Align.CENTER);
    final int height = Screen.dp(20f);
    final int width = Math.max(height, (int) Math.ceil(textPaint.measureText(text)) + Screen.dp(12f));
    final Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas c = new Canvas(out);
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final boolean m3 = XgramExpressive.isEnabled();
    fill.setColor(m3 ? XgramExpressive.color(XgramExpressive.PRIMARY) : org.thunderdog.challegram.theme.Theme.getColor(org.thunderdog.challegram.theme.ColorId.badge));
    c.drawRoundRect(new RectF(0, 0, width, height), height / 2f, height / 2f, fill);
    textPaint.setColor(m3 ? XgramExpressive.color(XgramExpressive.ON_PRIMARY) : 0xffffffff);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    c.drawText(text, width / 2f, height / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
    return wrap(context.getResources(), out);
  }

  /** Puts a row's photo (leading) and badge (trailing) on it, untinted, mirrored with RTL. */
  public static void apply (TextView row, @Nullable Drawable leading, @Nullable Drawable trailing) {
    if (leading == null && trailing == null) {
      return;
    }
    final Drawable[] current = row.getCompoundDrawables();
    final boolean rtl = Lang.rtl();
    final Drawable start = leading != null ? leading : (rtl ? current[2] : current[0]);
    final Drawable end = trailing != null ? trailing : (rtl ? current[0] : current[2]);
    row.setCompoundDrawablesWithIntrinsicBounds(rtl ? end : start, null, rtl ? start : end, null);
  }
}
