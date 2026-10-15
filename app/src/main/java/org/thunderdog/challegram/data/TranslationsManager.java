package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import com.fahadali.xgram.translate.XgramTranslator;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.util.TranslationCounterDrawable;

import java.util.HashMap;

import me.vkryl.core.StringUtils;
import tgx.td.Td;
import tgx.td.TdConstants;

public final class TranslationsManager {

  private final Tdlib tdlib;
  private final Translatable message;
  private final OnChangeTranslatedStatus statusDelegate;
  private final OnChangeTranslatedResult resultDelegate;
  private final OnNewTranslatedError errorDelegate;
  private String currentTranslatedLanguage;
  private String lastTranslatedLanguage;

  public interface OnNewTranslatedError {
    void onError (String string);
  }

  public interface OnChangeTranslatedStatus {
    void setTranslatedStatus (int status, boolean animated);
  }

  public interface OnChangeTranslatedResult {
    void setTranslationResult (TdApi.FormattedText translationResult);
  }

  public interface Translatable {
    @Nullable
    String getOriginalMessageLanguage ();

    TdApi.FormattedText getTextToTranslate ();

    /**
     * XGram: the chat the text is from, 0 if there is none (e.g. a bio), {@code XgramTranslator.CHAT_UNKNOWN} if it can't
     * be told. Third-party translation services are never used in secret chats or for an unknown chat.
     */
    default long xgramChatId () {
      return 0;
    }
  }

  public TranslationsManager (Tdlib tdlib, Translatable message, OnChangeTranslatedStatus statusDelegate, OnChangeTranslatedResult resultDelegate, OnNewTranslatedError errorDelegate) {
    this.tdlib = tdlib;
    this.message = message;
    this.statusDelegate = statusDelegate;
    this.resultDelegate = resultDelegate;
    this.errorDelegate = errorDelegate;
  }

  public void stopTranslation () {
    requestTranslation(null);
  }

  private int lastDispatchedStatus = TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT;
  private TdApi.FormattedText lastDispatchedResult;

  private void dispatchStatus (int status, boolean animated) {
    if (lastDispatchedStatus != status) {
      this.lastDispatchedStatus = status;
      statusDelegate.setTranslatedStatus(status, animated);
    }
  }

  private void dispatchResult (int status, @Nullable TdApi.FormattedText result, boolean animated) {
    dispatchStatus(status, animated);
    if (!Td.equalsTo(lastDispatchedResult, result)) {
      this.lastDispatchedResult = result;
      resultDelegate.setTranslationResult(result);
    }
  }

  /** XGram: how an auto-translate request ended. Its errors go here instead of each message's own tooltip. */
  public interface XgramListener {
    /**
     * @param error null when the translation is shown (or there was nothing to translate), otherwise as received
     * @param errorText the error as shown to the user, null with {@code error}
     * @param notice why the chosen service failed when Telegram translated instead, otherwise null
     */
    void onDone (@Nullable TdApi.Error error, @Nullable String errorText, @Nullable String notice);
  }

  /** XGram: translations shared by every message and screen, so a chat opened again isn't translated again. */
  private static final android.util.LruCache<String, Object[]> xgramSharedCache = new android.util.LruCache<>(400);

  public void requestTranslation (String language) {
    requestTranslation(language, null);
  }

  /** XGram: auto-translate; {@code listener} is told when the request ends, whatever the outcome. */
  public void xgramRequestTranslation (String language, XgramListener listener) {
    requestTranslation(language, listener);
  }

  private void requestTranslation (String language, @Nullable XgramListener listener) {
    currentTranslatedLanguage = language;
    if (language == null || StringUtils.equalsOrBothEmpty(language, message.getOriginalMessageLanguage())) {
      dispatchResult(TranslationCounterDrawable.TRANSLATE_STATUS_DEFAULT, null, true);
      currentTranslatedLanguage = null;
      if (listener != null) {
        listener.onDone(null, null, null);
      }
      return;
    }

    if (currentTranslatedLanguage != null) {
      lastTranslatedLanguage = currentTranslatedLanguage;
    }

    TdApi.FormattedText textToTranslate = prepareTextToTranslate(message.getTextToTranslate());
    if (textToTranslate == null) {
      if (listener != null) {
        listener.onDone(null, null, null);
      }
      return;
    }

    // XGram: cached per translation service, so choosing another service translates again
    final String providerKey = xgramProviderKey(language);
    TdApi.FormattedText cachedText = getCachedTranslation(textToTranslate.text, providerKey);
    if (cachedText == null) {
      Object[] shared = xgramSharedCache.get(providerKey + "\u0001" + textToTranslate.text);
      if (shared != null) {
        cachedText = (TdApi.FormattedText) shared[0];
        saveCachedTextTranslation(textToTranslate.text, language, providerKey, cachedText, (Integer) shared[1]);
      }
    }
    if (cachedText != null) {
      xgramTranslatedBy = getCachedTranslationProvider(textToTranslate.text, providerKey);
      dispatchResult(TranslationCounterDrawable.TRANSLATE_STATUS_SUCCESS, cachedText, true);
      if (listener != null) {
        listener.onDone(null, null, null);
      }
      return;
    }

    dispatchStatus(TranslationCounterDrawable.TRANSLATE_STATUS_LOADING, true);
    tdlib.ui().post(() -> requestTranslationImpl(textToTranslate, language, (object, provider, providerError) -> tdlib.ui().post(() -> {
      if (object instanceof TdApi.FormattedText) {
        TdApi.FormattedText text = prepareTranslatedText((TdApi.FormattedText) object);
        // under the key of the service that actually translated: after a fallback to Telegram the chosen service is tried again next time
        saveCachedTextTranslation(textToTranslate.text, language, provider + ":" + language, text, provider);
        xgramSharedCache.put(provider + ":" + language + "\u0001" + textToTranslate.text, new Object[] {text, provider});
        if (StringUtils.equalsOrBothEmpty(currentTranslatedLanguage, language)) {
          if (providerError != null && listener == null) {
            errorDelegate.onError(providerError);
          }
          xgramTranslatedBy = provider;
          dispatchResult(TranslationCounterDrawable.TRANSLATE_STATUS_SUCCESS, text, true);
        }
        if (listener != null) {
          listener.onDone(null, null, providerError);
        }
      } else {
        if (StringUtils.equalsOrBothEmpty(currentTranslatedLanguage, language)) {
          dispatchStatus(TranslationCounterDrawable.TRANSLATE_STATUS_ERROR, true);
          if (object instanceof TdApi.Error && listener == null) {
            // a third-party service's error already reads "Google Translate: ..."
            errorDelegate.onError(XgramTranslator.isThirdParty(provider) ? ((TdApi.Error) object).message : TD.toErrorString(object));
          }
        }
        if (listener != null) {
          TdApi.Error error = object instanceof TdApi.Error ? (TdApi.Error) object : new TdApi.Error(500, TD.toErrorString(object));
          listener.onDone(error, XgramTranslator.isThirdParty(provider) ? error.message : TD.toErrorString(error), null);
        }
      }
    })));
  }

  private int xgramTranslatedBy = -1;

  /** XGram: the service that made the translation shown last ({@code XgramTranslator.PROVIDER_*}), or -1. */
  public int getTranslatedBy () {
    return xgramTranslatedBy;
  }

  private String xgramProviderKey (String language) {
    return XgramTranslator.providerFor(message.xgramChatId()) + ":" + language;
  }

  public String getCurrentTranslatedLanguage () {
    return currentTranslatedLanguage;
  }

  public String getLastTranslatedLanguage () {
    return lastTranslatedLanguage;
  }

  private void requestTranslationImpl (TdApi.FormattedText originalText, String toLanguage, XgramTranslator.Callback callback) {
    // XGram: Telegram, or the third-party service chosen in Settings, XGram, Chats, Translation
    XgramTranslator.translate(tdlib, originalText, message.getOriginalMessageLanguage(), toLanguage, message.xgramChatId(), callback);
  }




  private final HashMap<String, TranslatedCachedValue> mTranslationsCache2 = new HashMap<>();

  private static class TranslatedCachedValue {
    public String originalLanguage;
    public final HashMap<String, TdApi.FormattedText> translationsCache;
    /** XGram: which service made each translation. */
    public final HashMap<String, Integer> translatedBy = new HashMap<>();

    public TranslatedCachedValue (String originalLanguage) {
      this.originalLanguage = originalLanguage;
      this.translationsCache = new HashMap<>();
    }
  }

  public @Nullable String getCachedTextLanguage (String text) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    return (cachedValue != null ? cachedValue.originalLanguage : null);
  }

  public void saveCachedTextLanguage (String text, String language) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    if (cachedValue == null) {
      mTranslationsCache2.put(text, new TranslatedCachedValue(language));
    } else if (cachedValue.originalLanguage == null) {
      cachedValue.originalLanguage = language; // XGram: an entry made by a translation before the language was known
    }
  }

  /** The latest translation to {@code language}, whichever service made it (what is shown, e.g. for Copy). */
  public @Nullable TdApi.FormattedText getCachedTextTranslation (String text, String language) {
    return getCachedTranslation(text, language);
  }

  private @Nullable TdApi.FormattedText getCachedTranslation (String text, String key) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    return (cachedValue != null ? cachedValue.translationsCache.get(key) : null);
  }

  private int getCachedTranslationProvider (String text, String key) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    Integer provider = cachedValue != null ? cachedValue.translatedBy.get(key) : null;
    return provider != null ? provider : -1;
  }

  /** Saved under the language (the latest) and under the service's own key {@code providerKey}. */
  private void saveCachedTextTranslation (String text, String language, String providerKey, TdApi.FormattedText translated, int provider) {
    TranslatedCachedValue cachedValue = mTranslationsCache2.get(text);
    if (cachedValue == null) {
      // XGram: also cached when the language wasn't detected, so toggling Translate doesn't send a new request
      cachedValue = new TranslatedCachedValue(null);
      mTranslationsCache2.put(text, cachedValue);
    }
    cachedValue.translationsCache.put(language, translated);
    cachedValue.translationsCache.put(providerKey, translated);
    cachedValue.translatedBy.put(language, provider);
    cachedValue.translatedBy.put(providerKey, provider);
  }



  public static TdApi.FormattedText prepareTextToTranslate (TdApi.FormattedText text) {
    if (text == null || text.entities == null || text.entities.length == 0) {
      return text;
    }
    try {
      TdApi.TextEntity[] entities = new TdApi.TextEntity[text.entities.length];
      for (int a = 0; a < entities.length; a++) {
        TdApi.TextEntity entity = text.entities[a];
        if (entity.type instanceof TdApi.TextEntityTypeUrl) {
          String url = text.text.substring(entity.offset, entity.offset + entity.length);
          TdApi.TextEntityType newEntityTypeUrl = new TdApi.TextEntityTypeTextUrl(url);
          entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityTypeUrl);
        } else if (entity.type instanceof TdApi.TextEntityTypeMention) {
          String username = text.text.substring(entity.offset + 1, entity.offset + entity.length);
          TdApi.TextEntityType newEntityTypeUrl = new TdApi.TextEntityTypeTextUrl("https://t.me/" + username);
          entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityTypeUrl);
        }  else if (entity.type instanceof TdApi.TextEntityTypeHashtag) {
          TdApi.TextEntityType newEntityTypeUrl = new TdApi.TextEntityTypeCode();
          entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityTypeUrl);
        } else {
          entities[a] = entity;
        }
      }

      return new TdApi.FormattedText(text.text, entities);
    } catch (Exception e) {
      return text;
    }
  }

  public static TdApi.FormattedText prepareTranslatedText (TdApi.FormattedText text) {
    if (text == null || text.entities == null || text.entities.length == 0) {
      return text;
    }
    try {
      TdApi.TextEntity[] entities = new TdApi.TextEntity[text.entities.length];
      for (int a = 0; a < entities.length; a++) {
        TdApi.TextEntity entity = entities[a] = text.entities[a];
        if (entity.type instanceof TdApi.TextEntityTypeCode) {
          String code = text.text.substring(entity.offset, entity.offset + entity.length);
          if (code.startsWith("#")) {
            TdApi.TextEntityType newEntityType = new TdApi.TextEntityTypeHashtag();
            entities[a] = new TdApi.TextEntity(entity.offset, entity.length, newEntityType);
          }
        }
      }

      return new TdApi.FormattedText(text.text, entities);
    } catch (Exception e) {
      return text;
    }
  }
}
