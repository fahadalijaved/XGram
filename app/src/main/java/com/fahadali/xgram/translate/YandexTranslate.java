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
package com.fahadali.xgram.translate;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.Request;

/** Yandex Translate's app endpoint: no key, plain text, the source language detected by Yandex. */
final class YandexTranslate {
  private YandexTranslate () { }

  /** A random id per app session, as the Yandex app sends. */
  private static final String URL = "https://translate.yandex.net/api/v1/tr.json/translate?srv=android&id=" + UUID.randomUUID().toString().replace("-", "") + "-0-0";
  private static final String USER_AGENT = "ru.yandex.translate/22.11.8.22364114 (Xiaomi Redmi K20 Pro; Android 11)";

  private static final Set<String> LANGUAGES = new HashSet<>(Arrays.asList(
    "af", "am", "ar", "az", "ba", "be", "bg", "bn", "bs", "ca", "ceb", "cs", "cv", "cy", "da", "de", "el", "en", "eo", "es",
    "et", "eu", "fa", "fi", "fr", "ga", "gd", "gl", "gu", "he", "hi", "hr", "ht", "hu", "hy", "id", "is", "it", "ja", "jv",
    "ka", "kk", "km", "kn", "ko", "ky", "la", "lb", "lo", "lt", "lv", "mg", "mhr", "mi", "mk", "ml", "mn", "mr", "mrj", "ms",
    "mt", "my", "ne", "nl", "no", "pa", "pap", "pl", "pt", "ro", "ru", "sah", "si", "sk", "sl", "sq", "sr", "su", "sv", "sw",
    "ta", "te", "tg", "th", "tl", "tr", "tt", "udm", "uk", "ur", "uz", "vi", "xh", "yi", "zh", "zu"
  ));

  /** Yandex's code for a Telegram X language code, or null when Yandex can't translate to it. */
  static @Nullable String languageCode (String language) {
    String code = XgramTranslator.normalize(language);
    String base = code.startsWith("zh") ? "zh" : XgramTranslator.base(code);
    switch (base) {
      case "iw": base = "he"; break;
      case "in": base = "id"; break;
      case "ji": base = "yi"; break;
      case "jw": base = "jv"; break;
      case "nb": case "nn": base = "no"; break;
      case "fil": base = "tl"; break;
    }
    return LANGUAGES.contains(base) ? base : null;
  }

  static String translate (String text, String target) throws XgramTranslator.TranslateException {
    Request request = new Request.Builder()
      .url(URL)
      .header("User-Agent", USER_AGENT)
      .post(new FormBody.Builder().add("lang", target).add("text", text).build())
      .build();
    String answer = XgramTranslator.execute(request);
    try {
      // {"code": 200, "lang": "es-en", "text": ["translated"]}
      JSONObject root = new JSONObject(answer);
      int code = root.optInt("code", 200);
      if (code != 200) {
        throw new XgramTranslator.TranslateException(code, root.optString("message", String.valueOf(code)));
      }
      JSONArray pieces = root.getJSONArray("text");
      StringBuilder b = new StringBuilder(text.length());
      for (int i = 0; i < pieces.length(); i++) {
        b.append(pieces.optString(i));
      }
      if (b.length() == 0) {
        throw XgramTranslator.badAnswer();
      }
      return b.toString();
    } catch (JSONException e) {
      throw XgramTranslator.badAnswer();
    }
  }
}
