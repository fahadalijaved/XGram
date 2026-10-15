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

import android.util.LruCache;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tgx.td.ChatId;
import tgx.td.TdConstants;

/**
 * The one place every translation goes through (AyuGram's design): message bubbles, Inline style, the translation
 * popup, the profile bio and the composer's formatting panel all reach it via {@code TranslationsManager}.
 * Telegram is the default. Google Translate and Yandex Translate are opt-in, keyless and plain text; they are never
 * used in secret chats, and when one fails Telegram can translate instead. Telegram is never replaced by a third party.
 */
public final class XgramTranslator {
  private XgramTranslator () { }

  public static final int PROVIDER_TELEGRAM = 0, PROVIDER_GOOGLE = 1, PROVIDER_YANDEX = 2;
  public static final int[] PROVIDERS = {PROVIDER_TELEGRAM, PROVIDER_GOOGLE, PROVIDER_YANDEX};

  public interface Callback {
    /**
     * @param result {@link TdApi.FormattedText} or {@link TdApi.Error}
     * @param provider the service that produced {@code result}
     * @param providerError when Telegram stepped in, why the chosen service failed (with its name), otherwise null
     */
    void onResult (TdApi.Object result, int provider, @Nullable String providerError);
  }

  static final class TranslateException extends Exception {
    /** HTTP status, or 0 for a network error. */
    final int code;

    TranslateException (int code, String message) {
      super(message);
      this.code = code;
    }
  }

  private static final int MAX_CHUNK = 4500, RETRIES = 2;
  private static final LruCache<String, String> cache = new LruCache<>(300);
  private static final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
    Thread thread = new Thread(runnable, "XgramTranslator");
    thread.setDaemon(true);
    return thread;
  });
  private static volatile OkHttpClient client;

  public static boolean isValidProvider (int provider) {
    return provider == PROVIDER_TELEGRAM || provider == PROVIDER_GOOGLE || provider == PROVIDER_YANDEX;
  }

  public static boolean isThirdParty (int provider) {
    return provider != PROVIDER_TELEGRAM;
  }

  /** The service used for text from {@code chatId} (0 when unknown): always Telegram in secret chats. */
  public static int providerFor (long chatId) {
    int provider = XgramSettings.instance().translateProvider();
    return isThirdParty(provider) && chatId != 0 && ChatId.isSecret(chatId) ? PROVIDER_TELEGRAM : provider;
  }

  public static String name (int provider) {
    switch (provider) {
      case PROVIDER_GOOGLE: return Lang.getString(R.string.XgramTrGoogle);
      case PROVIDER_YANDEX: return Lang.getString(R.string.XgramTrYandex);
      default: return Lang.getString(R.string.XgramTrTelegram);
    }
  }

  /** What the settings say about a service. */
  public static String info (int provider) {
    switch (provider) {
      case PROVIDER_GOOGLE: return Lang.getString(R.string.XgramTrThirdPartyInfo, "Google");
      case PROVIDER_YANDEX: return Lang.getString(R.string.XgramTrThirdPartyInfo, "Yandex");
      default: return Lang.getString(R.string.XgramTrTelegramInfo);
    }
  }

  public static void translate (Tdlib tdlib, TdApi.FormattedText text, @Nullable String fromLanguage, String toLanguage, long chatId, Callback callback) {
    final int provider = providerFor(chatId);
    if (!isThirdParty(provider) || text == null || text.text == null) {
      telegram(tdlib, text, toLanguage, callback, null);
      return;
    }
    executor.execute(() -> {
      TdApi.Object result;
      try {
        result = toFormattedText(translateText(provider, text.text, toLanguage));
      } catch (TranslateException e) {
        String error = Lang.getString(R.string.XgramTrError, name(provider), e.getMessage());
        if (XgramSettings.instance().translateFallback()) {
          telegram(tdlib, text, toLanguage, callback, Lang.getString(R.string.XgramTrFellBack, error));
          return;
        }
        result = new TdApi.Error(e.code != 0 ? e.code : 400, error);
      } catch (Throwable t) {
        result = new TdApi.Error(400, Lang.getString(R.string.XgramTrError, name(provider), Lang.getString(R.string.XgramTrErrorAnswer)));
      }
      callback.onResult(result, provider, null);
    });
  }

  private static void telegram (Tdlib tdlib, TdApi.FormattedText text, String toLanguage, Callback callback, @Nullable String providerError) {
    tdlib.client().send(new TdApi.TranslateText(text, toLanguage, TdConstants.TEXT_FORMAT_NEUTRAL), result -> callback.onResult(result, PROVIDER_TELEGRAM, providerError));
  }

  /** Third-party results are plain text: links, mentions and hashtags are found again so they stay clickable. */
  private static TdApi.FormattedText toFormattedText (String text) {
    TdApi.TextEntity[] entities = new TdApi.TextEntity[0];
    try {
      TdApi.TextEntities result = Client.execute(new TdApi.GetTextEntities(text));
      if (result != null && result.entities != null) {
        entities = result.entities;
      }
    } catch (Throwable ignored) { }
    return new TdApi.FormattedText(text, entities);
  }

  private static String translateText (int provider, String text, String toLanguage) throws TranslateException {
    String target = provider == PROVIDER_GOOGLE ? GoogleTranslate.languageCode(toLanguage) : YandexTranslate.languageCode(toLanguage);
    if (target == null) {
      throw new TranslateException(400, Lang.getString(R.string.XgramTrErrorLanguage));
    }
    String key = provider + "\u0001" + target + "\u0001" + text;
    String cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    List<String> parts = new ArrayList<>(), separators = new ArrayList<>();
    split(text, parts, separators);
    StringBuilder b = new StringBuilder(text.length());
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      b.append(part.trim().isEmpty() ? part : request(provider, part, target));
      b.append(separators.get(i));
    }
    String result = b.toString();
    cache.put(key, result);
    return result;
  }

  /** Long text goes in pieces of at most {@link #MAX_CHUNK} characters, cut at a paragraph, a line or a space. */
  private static void split (String text, List<String> parts, List<String> separators) {
    int start = 0;
    while (text.length() - start > MAX_CHUNK) {
      int end = start + MAX_CHUNK;
      String separator = "\n\n";
      int cut = text.lastIndexOf(separator, end - separator.length());
      if (cut <= start) {
        separator = "\n";
        cut = text.lastIndexOf('\n', end - 1);
      }
      if (cut <= start) {
        separator = " ";
        cut = text.lastIndexOf(' ', end - 1);
      }
      if (cut <= start) {
        separator = "";
        cut = end;
        if (Character.isHighSurrogate(text.charAt(cut - 1))) {
          cut--;
        }
      }
      parts.add(text.substring(start, cut));
      separators.add(separator);
      start = cut + separator.length();
    }
    parts.add(text.substring(start));
    separators.add("");
  }

  /** One piece, retried twice with backoff after a network or server error. */
  private static String request (int provider, String text, String target) throws TranslateException {
    TranslateException last = null;
    for (int attempt = 0; attempt <= RETRIES; attempt++) {
      if (attempt > 0) {
        try {
          Thread.sleep(attempt * 1000L);
        } catch (InterruptedException e) {
          break;
        }
      }
      try {
        return provider == PROVIDER_GOOGLE ? GoogleTranslate.translate(text, target) : YandexTranslate.translate(text, target);
      } catch (TranslateException e) {
        last = e;
        if (e.code != 0 && e.code < 500) {
          break; // too many requests or a refused request: trying again right away won't help
        }
      }
    }
    throw last != null ? last : new TranslateException(0, Lang.getString(R.string.XgramTrErrorNetwork));
  }

  private static OkHttpClient client () {
    if (client == null) {
      synchronized (XgramTranslator.class) {
        if (client == null) {
          client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
        }
      }
    }
    return client;
  }

  static String execute (Request request) throws TranslateException {
    try (Response response = client().newCall(request).execute()) {
      int code = response.code();
      if (code == 429) {
        throw new TranslateException(429, Lang.getString(R.string.XgramTrErrorLimit));
      }
      if (!response.isSuccessful()) {
        throw new TranslateException(code, Lang.getString(R.string.XgramTrErrorHttp, code));
      }
      ResponseBody body = response.body();
      return body != null ? body.string() : "";
    } catch (IOException e) {
      throw new TranslateException(0, Lang.getString(R.string.XgramTrErrorNetwork));
    }
  }

  static TranslateException badAnswer () {
    return new TranslateException(502, Lang.getString(R.string.XgramTrErrorAnswer));
  }

  /** Lower case with dashes: Telegram X uses both {@code pt_BR}-like pack codes and {@code zh-CN}-like ones. */
  static String normalize (String language) {
    return language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
  }

  static String base (String code) {
    int dash = code.indexOf('-');
    return dash > 0 ? code.substring(0, dash) : code;
  }
}
