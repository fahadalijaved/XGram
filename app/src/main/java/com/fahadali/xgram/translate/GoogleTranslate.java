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

import org.json.JSONArray;
import org.json.JSONException;

import okhttp3.FormBody;
import okhttp3.Request;

/** Google Translate's free web endpoint: no key, plain text, the source language detected by Google. */
final class GoogleTranslate {
  private GoogleTranslate () { }

  private static final String URL = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&ie=UTF-8&oe=UTF-8&tl=";

  /** Google's code for a Telegram X language code. */
  static String languageCode (String language) {
    String code = XgramTranslator.normalize(language);
    if (code.startsWith("zh")) {
      return code.equals("zh-tw") || code.equals("zh-hant") || code.equals("zh-hk") || code.equals("zh-mo") ? "zh-TW" : "zh-CN";
    }
    String base = XgramTranslator.base(code);
    switch (base) {
      case "he": return "iw";
      case "jv": return "jw";
      case "in": return "id";
      case "ji": return "yi";
      case "nb": case "nn": return "no";
      case "fil": return "tl";
    }
    return base;
  }

  static String translate (String text, String target) throws XgramTranslator.TranslateException {
    Request request = new Request.Builder()
      .url(URL + target)
      .post(new FormBody.Builder().add("q", text).build())
      .build();
    String answer = XgramTranslator.execute(request);
    try {
      // [[["translated piece", "original piece", ...], ...], null, "detected language", ...]
      JSONArray pieces = new JSONArray(answer).getJSONArray(0);
      StringBuilder b = new StringBuilder(text.length());
      for (int i = 0; i < pieces.length(); i++) {
        JSONArray piece = pieces.optJSONArray(i);
        if (piece != null && !piece.isNull(0)) {
          b.append(piece.optString(0));
        }
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
