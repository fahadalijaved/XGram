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
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.fahadali.xgram.XgramApp;
import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.util.CustomTypefaceSpan;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

/**
 * The app font (XGram > Appearance > Font): Google Sans Flex, the default; Google Sans Rounded, the same font with its
 * roundness axis (ROND) at 100; or System, Telegram X's own fonts (Roboto, or the device font with Use system fonts
 * on). Google Sans Flex is assets/fonts/googlesansflex.ttf from Google Fonts (SIL Open Font License 1.1, OFL.txt next to
 * it): one variable font, whose weight (wght) and slant (slnt) axes give every weight and the italics.
 * <p>
 * Telegram X's text gets it through {@link Fonts} (getRobotoRegular, Medium, Bold, Italic), the Material 3 type scale
 * through {@link XgramExpressive#typeface} (with the optical size, opsz) and Compose through XgramM3Theme's typography
 * (com.fahadali.xgram.compose.theme.XgramFontFamilies): the same cached typefaces everywhere. The font is copied out of
 * the APK once and mapped once (Android 10+; on 8 and 9 each typeface maps the same file), so it is in memory once, and
 * no typeface is ever created while drawing. Read once per process: Telegram X keeps its paints and measured text for
 * the whole process, so a change restarts the app. Font variations need Android 8; older versions keep System.
 */
public final class XgramFonts {
  private XgramFonts () { }

  public static final int FLEX = 0, ROUNDED = 1, SYSTEM = 2;

  public static final String ASSET_PATH = "fonts/googlesansflex.ttf";

  /** Android 8 (Typeface.Builder with font variations). */
  public static boolean isAvailable () {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
  }

  public static boolean isGoogleSans (int font) {
    return font == FLEX || font == ROUNDED;
  }

  private static volatile int current = -1;

  /**
   * The font in use: the saved choice ({@link XgramSettings#appFont}, Google Sans Flex when nothing is saved), or System
   * where Google Sans can't be used (Android 7 and older, or the font failed to load). Cheap after the first call.
   */
  public static int current () {
    int font = current;
    if (font == -1) {
      synchronized (XgramFonts.class) {
        font = current;
        if (font == -1) {
          font = isAvailable() ? XgramSettings.instance().appFont() : SYSTEM;
          if (isGoogleSans(font) && typeface(font, 400, false, 0f) == null) {
            font = SYSTEM;
          }
          current = font;
        }
      }
    }
    return font;
  }

  public static String name (int font) {
    switch (font) {
      case ROUNDED:
        return Lang.getString(R.string.XgramFontRounded);
      case SYSTEM:
        return Lang.getString(R.string.XgramFontSystem);
      default:
        return Lang.getString(R.string.XgramFontFlex);
    }
  }

  // Typefaces

  /** The font in use at {@code weight} (400 regular, 500 medium, 700 bold), or null with System: the caller's own then. */
  @Nullable
  public static Typeface typeface (int weight, boolean italic) {
    return typeface(weight, italic, 0f);
  }

  /** The same at the optical size {@code opsz} (the text size in sp; 0 keeps the font's default, 18). */
  @Nullable
  public static Typeface typeface (int weight, boolean italic, float opsz) {
    int font = current();
    return isGoogleSans(font) ? typeface(font, weight, italic, opsz) : null;
  }

  private static final SparseArray<Typeface> cache = new SparseArray<>();

  /**
   * Google Sans {@code font} ({@link #FLEX} or {@link #ROUNDED}), whichever is in use (the Font pickers' previews show
   * the others too). Each variation is built once and kept (a failed one as null, not tried again); null for System,
   * before Android 8 or when the font can't be loaded.
   */
  @Nullable
  public static synchronized Typeface typeface (int font, int weight, boolean italic, float opsz) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isGoogleSans(font)) {
      return null;
    }
    weight = Math.max(1, Math.min(1000, weight));
    // Android 8 and 9 map the file once per typeface: the default optical size there keeps them to a few
    int opszTenths = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Math.max(0, Math.min(1440, Math.round(opsz * 10f))) : 0;
    int key = ((font * 2 + (italic ? 1 : 0)) * 1001 + weight) * 1441 + opszTenths;
    int index = cache.indexOfKey(key);
    if (index >= 0) {
      return cache.valueAt(index);
    }
    Typeface typeface = build(font, weight, italic, opszTenths / 10f);
    cache.put(key, typeface);
    return typeface;
  }

  /** The regular typeface of {@code font}: Google Sans, or Telegram X's own with System (the pickers' previews). */
  @NonNull
  public static Typeface previewTypeface (int font) {
    Typeface typeface = isGoogleSans(font) ? typeface(font, 400, false, 0f) : null;
    return typeface != null ? typeface : Fonts.getSystemRegular();
  }

  @RequiresApi(Build.VERSION_CODES.O)
  @Nullable
  private static Typeface build (int font, int weight, boolean italic, float opsz) {
    StringBuilder axes = new StringBuilder("'wght' ").append(weight);
    if (font == ROUNDED) {
      axes.append(", 'ROND' 100");
    }
    if (italic) {
      axes.append(", 'slnt' -10");
    }
    if (opsz > 0f) {
      axes.append(String.format(Locale.US, ", 'opsz' %.1f", opsz));
    }
    String settings = axes.toString();
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ByteBuffer buffer = fontBuffer();
        return buffer != null ? build(buffer, settings, weight, italic) : null;
      }
      // Android 8 and 9: each typeface maps the file (the same pages in memory), or reads the asset if it isn't there
      File file = fontFile();
      Typeface typeface = file != null ? configure(new Typeface.Builder(file), settings, weight, italic).build() : null;
      if (typeface == null) {
        typeface = configure(new Typeface.Builder(AppContext.get().getAssets(), ASSET_PATH), settings, weight, italic).build();
      }
      return typeface;
    } catch (Throwable t) {
      Log.e("XgramFonts: unable to load Google Sans Flex", t);
      return null;
    }
  }

  // The weight and italic are the typeface's style as well: the fallback fonts (scripts Google Sans doesn't cover,
  // such as Cyrillic, Greek or Arabic) follow it, and no fake bold is added on top of the wght axis.

  @RequiresApi(Build.VERSION_CODES.O)
  private static Typeface.Builder configure (Typeface.Builder builder, String settings, int weight, boolean italic) {
    return builder.setFontVariationSettings(settings).setWeight(weight).setItalic(italic);
  }

  /** Android 10: every variation reads the one buffer (fonts take a direct buffer as it is, without copying it). */
  @RequiresApi(Build.VERSION_CODES.Q)
  private static Typeface build (ByteBuffer buffer, String settings, int weight, boolean italic) throws IOException {
    int slant = italic ? android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC : android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT;
    android.graphics.fonts.Font font = new android.graphics.fonts.Font.Builder(buffer)
      .setFontVariationSettings(settings)
      .setWeight(weight)
      .setSlant(slant)
      .build();
    // No setSystemFallback: the default fallback (sans-serif), as Typeface.Builder has it
    return new Typeface.CustomFallbackBuilder(new android.graphics.fonts.FontFamily.Builder(font).build())
      .setStyle(new android.graphics.fonts.FontStyle(weight, slant))
      .build();
  }

  private static ByteBuffer buffer;
  private static boolean bufferChecked;

  /**
   * Android 10: the font file mapped once (shared, read-only pages); if it couldn't be written, the asset read into
   * memory once. Null if neither works.
   */
  @RequiresApi(Build.VERSION_CODES.Q)
  @Nullable
  private static ByteBuffer fontBuffer () {
    if (bufferChecked) {
      return buffer;
    }
    bufferChecked = true;
    File file = fontFile();
    if (file != null) {
      try (FileInputStream in = new FileInputStream(file); FileChannel channel = in.getChannel()) {
        buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()); // stays valid once closed
        return buffer;
      } catch (Throwable t) {
        Log.e("XgramFonts: unable to map the font", t);
      }
    }
    try (InputStream in = AppContext.get().getAssets().open(ASSET_PATH)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(4 * 1024 * 1024);
      byte[] chunk = new byte[64 * 1024];
      int count;
      while ((count = in.read(chunk)) != -1) {
        bytes.write(chunk, 0, count);
      }
      byte[] data = bytes.toByteArray();
      ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
      direct.put(data);
      direct.flip();
      buffer = direct;
    } catch (Throwable t) {
      Log.e("XgramFonts: unable to read the font", t);
    }
    return buffer;
  }

  private static File file;
  private static boolean fileChecked;

  /**
   * The font as a file in code_cache, copied out of the APK on first use after each install or update (the system
   * clears code_cache then), so it is mapped rather than held in memory; null if it can't be written.
   */
  @RequiresApi(Build.VERSION_CODES.O)
  @Nullable
  private static File fontFile () {
    if (fileChecked) {
      return file;
    }
    fileChecked = true;
    File temp = null;
    try {
      Context context = AppContext.get();
      File dir = new File(context.getCodeCacheDir(), "xgram_fonts");
      File target = new File(dir, "googlesansflex.ttf");
      if (!target.isFile() || target.length() == 0) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
          throw new IOException("Unable to create " + dir);
        }
        // Written under a name of this process's own and then renamed: the file is either whole or not there.
        temp = new File(dir, "googlesansflex." + android.os.Process.myPid() + ".tmp");
        try (InputStream in = context.getAssets().open(ASSET_PATH); OutputStream out = new FileOutputStream(temp)) {
          byte[] buffer = new byte[64 * 1024];
          int count;
          while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
          }
        }
        if (!temp.renameTo(target)) {
          throw new IOException("Unable to rename " + temp);
        }
        temp = null;
      }
      file = target;
    } catch (Throwable t) {
      Log.e("XgramFonts: unable to copy the font", t);
      if (temp != null) {
        //noinspection ResultOfMethodCallIgnored
        temp.delete();
      }
    }
    return file;
  }

  // Choosing

  /** Saves {@code font} and restarts the app: Telegram X keeps its paints and measured text for the whole process. */
  public static void apply (@NonNull Context context, int font) {
    XgramSettings.instance().setAppFont(font);
    UI.post(() -> XgramApp.restart(context), 300); // as Use system fonts (SettingsThemeController)
  }

  /**
   * Telegram X's look (Expressive design off): the three in a popup, each name drawn in its own font, the restart noted
   * above them; OK applies a new choice and restarts. Expressive design has the Compose sheet (AppearancePage).
   */
  public static void showPicker (@NonNull ViewController<?> c) {
    final int saved = XgramSettings.instance().appFont();
    c.showSettings(new SettingsWrapBuilder(R.id.btn_xgramFont)
      .addHeaderItem(Lang.getString(R.string.XgramFontInfo))
      .setRawItems(new ListItem[] {
        option(R.id.btn_xgramFontFlex, FLEX, saved),
        option(R.id.btn_xgramFontRounded, ROUNDED, saved),
        option(R.id.btn_xgramFontSystem, SYSTEM, saved)
      })
      .setIntDelegate((id, result) -> {
        final int picked = result.get(R.id.btn_xgramFont);
        final int font = picked == R.id.btn_xgramFontRounded ? ROUNDED : picked == R.id.btn_xgramFontSystem ? SYSTEM : picked == R.id.btn_xgramFontFlex ? FLEX : saved;
        if (font != saved && !c.isDestroyed()) {
          apply(c.context(), font);
        }
      })
      .setAllowResize(false));
  }

  private static ListItem option (int id, int font, int saved) {
    SpannableString name = new SpannableString(name(font));
    name.setSpan(new CustomTypefaceSpan(previewTypeface(font), 0), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return new ListItem(ListItem.TYPE_RADIO_OPTION, id, 0, name, R.id.btn_xgramFont, font == saved);
  }
}
