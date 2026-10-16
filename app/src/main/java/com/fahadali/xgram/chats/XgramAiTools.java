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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.TranslationControllerV2;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.ChatId;

/**
 * Telegram's own AI tools, which Telegram X never wired up: Summarize for long messages (summarizeMessage), and the
 * AI editor on long-press of Send: fix (fixTextWithAi), rewrite in a style, translate, add emoji (composeTextWithAi).
 * Telegram gives a free daily amount, more with Premium (AICOMPOSE_FLOOD_PREMIUM / SUMMARY_FLOOD_PREMIUM). Never in
 * secret chats.
 */
public final class XgramAiTools {
  private XgramAiTools () { }

  private static final int STYLE_ID_BASE = 1; // style rows of the picker: STYLE_ID_BASE + index

  // Styles: TDLib only sends them as updateTextCompositionStyles

  private static final ConcurrentHashMap<Integer, TdApi.TextCompositionStyle[]> STYLES = new ConcurrentHashMap<>();

  /** TDLib thread. */
  public static void onStylesUpdated (Tdlib tdlib, TdApi.UpdateTextCompositionStyles update) {
    STYLES.put(tdlib.id(), update.styles != null ? update.styles : new TdApi.TextCompositionStyle[0]);
  }

  public static void forgetAccount (int accountId) {
    STYLES.remove(accountId);
  }

  private static TdApi.TextCompositionStyle[] styles (Tdlib tdlib) {
    TdApi.TextCompositionStyle[] styles = STYLES.get(tdlib.id());
    return styles != null ? styles : new TdApi.TextCompositionStyle[0];
  }

  private static String styleTitle (TdApi.TextCompositionStyle style) {
    return StringUtils.isEmpty(style.title) ? style.name : style.title;
  }

  // Summarize

  /** The message of the group that TDLib can summarize (non-empty summaryLanguageCode), or null. */
  @Nullable
  public static TdApi.Message summarizable (TGMessage msg) {
    if (!XgramSettings.instance().aiTools() || ChatId.isSecret(msg.getChatId()) || msg.isSponsoredMessage() || msg.xgramIsDeleted()) {
      return null;
    }
    for (TdApi.Message message : msg.getAllMessages()) {
      if (message.id != 0 && !StringUtils.isEmpty(message.summaryLanguageCode)) {
        return message;
      }
    }
    return null;
  }

  /** @param toLanguageCode "" to keep the message's language. */
  public static void summarize (ViewController<?> c, TdApi.Message message, String toLanguageCode) {
    Tdlib tdlib = c.tdlib();
    Pending pending = showProgress(c, R.string.XgramAiSummarizing);
    tdlib.send(new TdApi.SummarizeMessage(message.chatId, message.id, toLanguageCode, ""), (summary, error) -> tdlib.ui().post(() -> {
      if (!pending.finish(c)) {
        return;
      }
      if (error != null) {
        showError(c, error, true);
        return;
      }
      if (summary == null || StringUtils.isEmpty(summary.text)) {
        return;
      }
      String title = StringUtils.isEmpty(toLanguageCode) ?
        Lang.getString(R.string.XgramAiSummaryTitle) :
        Lang.getString(R.string.XgramAiSummaryTitleIn, Lang.getLanguageName(toLanguageCode, toLanguageCode));
      showSheet(c, title, summary, null, new ViewController.OptionItem[] {
        item(R.id.btn_copyText, R.string.Copy, R.drawable.baseline_content_copy_24),
        item(R.id.btn_xgramAiTranslate, R.string.XgramAiTranslate, R.drawable.baseline_translate_24),
        item(R.id.btn_cancel, R.string.Cancel, R.drawable.baseline_cancel_24)
      }, (v, id) -> {
        if (id == R.id.btn_copyText) {
          UI.copyText(TD.toCopyText(summary), R.string.CopiedText);
        } else if (id == R.id.btn_xgramAiTranslate) {
          pickLanguage(c, language -> summarize(c, message, language));
        }
        return true;
      });
    }));
  }

  // AI editor (long-press on Send)

  /** Adds "AI editor" to the send button's long-press menu when something is typed. */
  public static void addSendMenuItem (MessagesController m, @Nullable List<HapticMenuHelper.MenuItem> items) {
    InputView input = m.xgramInputView();
    if (items == null || input == null || !XgramSettings.instance().aiTools() || m.getChatId() == 0 || m.isSecretChat() || input.isEmpty()) {
      return;
    }
    items.add(new HapticMenuHelper.MenuItem(R.id.btn_xgramAiEditor, Lang.getString(R.string.XgramAiEditor), R.drawable.baseline_stars_24));
  }

  public static void openEditor (MessagesController m) {
    InputView input = m.xgramInputView();
    if (input == null || m.isSecretChat()) {
      return;
    }
    TdApi.FormattedText text = input.getOutputText(true);
    if (text == null || StringUtils.isEmpty(text.text) || text.text.trim().isEmpty()) {
      UI.showToast(R.string.XgramAiEmptyText, Toast.LENGTH_SHORT);
      return;
    }
    TdApi.TextCompositionStyle[] styles = styles(m.tdlib());
    List<ViewController.OptionItem> items = new ArrayList<>();
    items.add(item(R.id.btn_xgramAiFix, R.string.XgramAiFix, R.drawable.baseline_check_circle_24));
    if (styles.length > 0) {
      items.add(item(R.id.btn_xgramAiStyle, R.string.XgramAiStyle, R.drawable.baseline_brush_24));
    }
    items.add(item(R.id.btn_xgramAiTranslate, R.string.XgramAiTranslate, R.drawable.baseline_translate_24));
    items.add(item(R.id.btn_xgramAiEmojify, R.string.XgramAiEmojify, R.drawable.baseline_emoticon_outline_24));
    items.add(item(R.id.btn_cancel, R.string.Cancel, R.drawable.baseline_cancel_24));
    m.showOptions(new ViewController.Options(Lang.getString(R.string.XgramAiEditorInfo), Lang.getString(R.string.XgramAiEditor), null, items.toArray(new ViewController.OptionItem[0]), Text.LINE_COUNT_UNLIMITED), (v, id) -> {
      if (id == R.id.btn_xgramAiFix) {
        fix(m, text);
      } else if (id == R.id.btn_xgramAiStyle) {
        pickStyle(m, text, styles);
      } else if (id == R.id.btn_xgramAiTranslate) {
        pickLanguage(m, language -> compose(m, text, language, "", false, Lang.getString(R.string.XgramAiTranslatedTo, Lang.getLanguageName(language, language))));
      } else if (id == R.id.btn_xgramAiEmojify) {
        compose(m, text, "", "", true, Lang.getString(R.string.XgramAiEmojify));
      }
      return true;
    });
  }

  private static void pickStyle (MessagesController m, TdApi.FormattedText text, TdApi.TextCompositionStyle[] styles) {
    ViewController.OptionItem[] rows = new ViewController.OptionItem[styles.length];
    for (int i = 0; i < styles.length; i++) {
      rows[i] = new ViewController.OptionItem(STYLE_ID_BASE + i, styleTitle(styles[i]), ViewController.OptionColor.NORMAL, R.drawable.baseline_brush_24);
    }
    showSheet(m, Lang.getString(R.string.XgramAiChooseStyle), null, rows, new ViewController.OptionItem[] {
      item(R.id.btn_cancel, R.string.Cancel, R.drawable.baseline_cancel_24)
    }, (v, id) -> {
      int index = id - STYLE_ID_BASE;
      if (index >= 0 && index < styles.length) {
        compose(m, text, "", styles[index].name, false, styleTitle(styles[index]));
      }
      return true;
    });
  }

  private static void fix (MessagesController m, TdApi.FormattedText original) {
    Tdlib tdlib = m.tdlib();
    Pending pending = showProgress(m, R.string.XgramAiWorking);
    tdlib.send(new TdApi.FixTextWithAi(original), (fixed, error) -> tdlib.ui().post(() -> {
      if (!pending.finish(m)) {
        return;
      }
      if (error != null) {
        showError(m, error, false);
        return;
      }
      if (fixed == null || fixed.text == null || StringUtils.equalsOrBothEmpty(fixed.text.text, original.text)) {
        UI.showToast(R.string.XgramAiNothingToFix, Toast.LENGTH_SHORT);
        return;
      }
      TdApi.FormattedText preview = fixed.diffText != null && fixed.diffText.entities != null && fixed.diffText.entities.length > 0 ? diffToText(fixed.diffText) : fixed.text;
      showResult(m, Lang.getString(R.string.XgramAiFixTitle), preview, fixed.text);
    }));
  }

  private static void compose (MessagesController m, TdApi.FormattedText original, String toLanguageCode, String styleName, boolean addEmojis, String title) {
    Tdlib tdlib = m.tdlib();
    Pending pending = showProgress(m, R.string.XgramAiWorking);
    tdlib.send(new TdApi.ComposeTextWithAi(original, toLanguageCode, styleName, addEmojis), (result, error) -> tdlib.ui().post(() -> {
      if (!pending.finish(m)) {
        return;
      }
      if (error != null) {
        showError(m, error, false);
        return;
      }
      if (result == null || StringUtils.isEmpty(result.text)) {
        return;
      }
      showResult(m, title, result, result);
    }));
  }

  /** The new text with Apply (replaces what was typed), Copy and Cancel. Nothing is sent. */
  private static void showResult (MessagesController m, String title, TdApi.FormattedText preview, TdApi.FormattedText result) {
    showSheet(m, title, preview, null, new ViewController.OptionItem[] {
      new ViewController.OptionItem(R.id.btn_xgramAiApply, Lang.getString(R.string.Apply), ViewController.OptionColor.BLUE, R.drawable.baseline_check_24),
      item(R.id.btn_copyText, R.string.Copy, R.drawable.baseline_content_copy_24),
      item(R.id.btn_cancel, R.string.Cancel, R.drawable.baseline_cancel_24)
    }, (v, id) -> {
      if (id == R.id.btn_xgramAiApply) {
        InputView input = m.xgramInputView();
        if (input != null && !m.isDestroyed()) {
          // Same as when a message is opened for editing: markdown source if the user edits in markdown.
          boolean asMarkdown = m.isEditingMessage() && Settings.instance().getNewSetting(Settings.SETTING_FLAG_EDIT_MARKDOWN);
          input.setInput(asMarkdown ? TD.toMarkdown(result) : TD.toCharSequence(result), true, true);
        }
      } else if (id == R.id.btn_copyText) {
        UI.copyText(TD.toCopyText(result), R.string.CopiedText);
      }
      return true;
    });
  }

  /** Inserted and replaced parts in bold, removed parts struck through (diffText holds both). */
  private static TdApi.FormattedText diffToText (TdApi.DiffText diff) {
    List<TdApi.TextEntity> entities = new ArrayList<>(diff.entities.length);
    int textLength = diff.text != null ? diff.text.length() : 0;
    for (TdApi.DiffEntity entity : diff.entities) {
      if (entity.type == null || entity.length <= 0 || entity.offset < 0 || entity.offset + entity.length > textLength) {
        continue;
      }
      TdApi.TextEntityType type;
      switch (entity.type.getConstructor()) {
        case TdApi.DiffEntityTypeInsert.CONSTRUCTOR:
        case TdApi.DiffEntityTypeReplace.CONSTRUCTOR:
          type = new TdApi.TextEntityTypeBold();
          break;
        case TdApi.DiffEntityTypeDelete.CONSTRUCTOR:
          type = new TdApi.TextEntityTypeStrikethrough();
          break;
        default:
          continue;
      }
      entities.add(new TdApi.TextEntity(entity.offset, entity.length, type));
    }
    return new TdApi.FormattedText(diff.text != null ? diff.text : "", entities.toArray(new TdApi.TextEntity[0]));
  }

  // Shared UI

  private static void pickLanguage (ViewController<?> c, RunnableData<String> after) {
    if (c.isDestroyed()) {
      return;
    }
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      pickLanguageM3(c, after);
      return;
    }
    TranslationControllerV2.LanguageSelectorPopup popup = new TranslationControllerV2.LanguageSelectorPopup(c.context(), c, after::runWithData, null, null);
    popup.languageRecyclerWrap.setTranslationY(HeaderView.getSize(true));
    popup.show();
  }

  /**
   * XGram M3E r5: the language as a radio sheet (the settings choice sheet), the recent languages first; a tap picks
   * one and closes it, and it becomes the most recent (as the translation screen's own list keeps them).
   */
  private static void pickLanguageM3 (ViewController<?> c, RunnableData<String> after) {
    ArrayList<String> recents = Settings.instance().getTranslateLanguageRecents();
    List<org.thunderdog.challegram.ui.ListItem> items = new ArrayList<>();
    java.util.HashSet<String> added = new java.util.HashSet<>();
    int id = 1;
    for (String lang : recents) {
      if (lang != null && added.add(lang)) {
        items.add(new org.thunderdog.challegram.ui.ListItem(org.thunderdog.challegram.ui.ListItem.TYPE_RADIO_OPTION, id++, 0, Lang.getLanguageName(lang, lang), R.id.btn_xgramAiTranslate, false).setStringValue(lang));
      }
    }
    for (String lang : Lang.getSupportedLanguagesForTranslate()) {
      if (lang != null && added.add(lang)) {
        items.add(new org.thunderdog.challegram.ui.ListItem(org.thunderdog.challegram.ui.ListItem.TYPE_RADIO_OPTION, id++, 0, Lang.getLanguageName(lang, lang), R.id.btn_xgramAiTranslate, false).setStringValue(lang));
      }
    }
    if (items.isEmpty()) {
      return;
    }
    org.thunderdog.challegram.navigation.SettingsWrapBuilder b = new org.thunderdog.challegram.navigation.SettingsWrapBuilder(R.id.btn_xgramAiTranslate)
      .setRawItems(items)
      .setDisableFooter(true)
      .addHeaderItem(Lang.getString(R.string.XgramAiTranslate))
      .setOnSettingItemClick((view, settingsId, item, doneButton, adapter, window) -> {
        String code = item.getStringValue();
        if (StringUtils.isEmpty(code)) {
          return;
        }
        ArrayList<String> current = Settings.instance().getTranslateLanguageRecents();
        current.remove(code);
        current.add(0, code);
        while (current.size() > 4) {
          current.remove(current.size() - 1);
        }
        Settings.instance().setTranslateLanguageRecents(current);
        if (window != null) {
          window.hideWindow(true);
        }
        after.runWithData(code);
      });
    c.showSettings(b);
  }

  private static boolean isPremiumFlood (TdApi.Error error) {
    return error.message != null && (error.message.contains("AICOMPOSE_FLOOD_PREMIUM") || error.message.contains("SUMMARY_FLOOD_PREMIUM"));
  }

  private static void showError (ViewController<?> c, TdApi.Error error, boolean isSummary) {
    if (!isPremiumFlood(error)) {
      UI.showError(error);
      return;
    }
    if (c.tdlib().hasPremium()) {
      UI.showToast(R.string.XgramAiLimitPremium, Toast.LENGTH_LONG);
      return;
    }
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E r5: the limit notice as an M3 dialog (headlineSmall title, bodyMedium text, an OK text button)
      c.openAlert(R.string.XgramAiLimitTitle, Lang.getMarkdownString(c, isSummary ? R.string.XgramAiSummaryLimitText : R.string.XgramAiLimitText),
        Lang.getOK(), (dialog, which) -> dialog.dismiss(), ViewController.ALERT_NO_CANCEL | ViewController.ALERT_HAS_LINKS);
      return;
    }
    c.showOptions(new ViewController.Options(
      Lang.getMarkdownString(c, isSummary ? R.string.XgramAiSummaryLimitText : R.string.XgramAiLimitText),
      Lang.getString(R.string.XgramAiLimitTitle), null,
      new ViewController.OptionItem[] {
        new ViewController.OptionItem(R.id.btn_done, Lang.getString(R.string.OK), ViewController.OptionColor.NORMAL, R.drawable.baseline_premium_star_24)
      }, Text.LINE_COUNT_UNLIMITED), (v, id) -> true);
  }

  /** Full-screen progress (after 150 ms); Back cancels it and the result is then dropped. */
  private static final class Pending {
    private boolean cancelled;

    /** Hides the progress; false when it was cancelled or the screen is gone. */
    boolean finish (ViewController<?> c) {
      if (cancelled) {
        return false;
      }
      c.context().hideProgress(false);
      return !c.isDestroyed();
    }
  }

  private static Pending showProgress (ViewController<?> c, int textRes) {
    Pending pending = new Pending();
    c.context().showProgressDelayed(Lang.getString(textRes), () -> pending.cancelled = true, 150);
    return pending;
  }

  private static ViewController.OptionItem item (int id, int name, int icon) {
    return new ViewController.OptionItem(id, Lang.getString(name), ViewController.OptionColor.NORMAL, icon);
  }

  /**
   * A sheet like Telegram X's options: a title, then a part that scrolls (the text, or a long list) capped to half
   * the screen, then buttons that stay visible. Views are built once when it opens; nothing runs while scrolling.
   */
  private static void showSheet (ViewController<?> c, CharSequence title, @Nullable TdApi.FormattedText body, @Nullable ViewController.OptionItem[] scrollingItems, ViewController.OptionItem[] items, OptionDelegate delegate) {
    if (c.isDestroyed()) {
      return;
    }
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      showSheetM3(c, title, body, scrollingItems, items, delegate);
      return;
    }
    Tdlib tdlib = c.tdlib();
    final int maxScrollHeight = Math.round(Screen.currentHeight() * .5f);
    c.showPopup(title, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      Context context = optionsLayout.getContext();
      View.OnClickListener onClick = v -> {
        if (delegate.onOptionItemPressed(v, v.getId())) {
          popupLayout.hideWindow(true);
        }
      };
      LinearLayout content = new LinearLayout(context);
      content.setOrientation(LinearLayout.VERTICAL);
      int contentHeight = 0;
      if (body != null && !StringUtils.isEmpty(body.text)) {
        CustomTextView textView = new CustomTextView(context, tdlib);
        textView.setPadding(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(12f));
        textView.setTextColorId(ColorId.text);
        textView.setText(body.text, TextEntity.valueOf(tdlib, body, null), false);
        textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(textView);
        contentHeight += textView.getCurrentHeight(c.context().getControllerWidth(textView));
      }
      if (scrollingItems != null) {
        for (ViewController.OptionItem item : scrollingItems) {
          content.addView(optionView(c, item, onClick));
          contentHeight += Screen.dp(54f);
        }
      }
      int totalHeight = 0;
      if (content.getChildCount() > 0) {
        ScrollView scrollView = new ScrollView(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            int maxHeight = maxScrollHeight;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
              maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
          }
        };
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        optionsLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        totalHeight += Math.min(contentHeight, maxScrollHeight); // only used for the slide-in animation
      }
      for (ViewController.OptionItem item : items) {
        optionsLayout.addView(optionView(c, item, onClick));
        totalHeight += Screen.dp(54f);
      }
      return totalHeight;
    }, null);
  }

  /**
   * XGram M3E r5: the AI sheet. Under the titleLarge title the result (or summary) sits in a 16dp inset
   * surfaceContainerHigh card with 16dp corners and padding (bodyLarge), or the choices (styles) are segmented
   * surfaceBright tiles; the part scrolls, capped to half the screen. Then the actions row: text "Translate", tonal
   * "Copy" and the filled "Apply" at the end. No Cancel row: the sheet closes by its scrim, a drag or back.
   */
  private static void showSheetM3 (ViewController<?> c, CharSequence title, @Nullable TdApi.FormattedText body, @Nullable ViewController.OptionItem[] scrollingItems, ViewController.OptionItem[] items, OptionDelegate delegate) {
    final Tdlib tdlib = c.tdlib();
    final int maxScrollHeight = Math.round(Screen.currentHeight() * .5f);
    c.showPopup(title, true, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      final Context context = optionsLayout.getContext();
      final int width = com.fahadali.xgram.appearance.XgramM3Sheets.sheetWidth(c.context().getControllerWidth(optionsLayout));
      View.OnClickListener onClick = v -> {
        if (delegate.onOptionItemPressed(v, v.getId())) {
          popupLayout.hideWindow(true);
        }
      };
      LinearLayout content = new LinearLayout(context);
      content.setOrientation(LinearLayout.VERTICAL);
      int contentHeight = 0;
      if (body != null && !StringUtils.isEmpty(body.text)) {
        CustomTextView textView = new CustomTextView(context, tdlib);
        textView.setTextStyleProvider(com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L,
          com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L)));
        textView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
        textView.setTextColorId(ColorId.text);
        textView.setText(body.text, TextEntity.valueOf(tdlib, body, null), false);
        com.fahadali.xgram.appearance.XgramR5Sheets.applyCard(textView, com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH, com.fahadali.xgram.appearance.XgramExpressive.CORNER_L);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.leftMargin = cardParams.rightMargin = Screen.dp(16f);
        cardParams.topMargin = Screen.dp(4f);
        cardParams.bottomMargin = Screen.dp(8f);
        textView.setLayoutParams(cardParams);
        content.addView(textView);
        contentHeight += textView.getCurrentHeight(width - Screen.dp(32f)) + cardParams.topMargin + cardParams.bottomMargin;
      }
      if (scrollingItems != null && scrollingItems.length > 0) {
        for (int i = 0; i < scrollingItems.length; i++) {
          final int segment = scrollingItems.length == 1 ? com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_SINGLE :
            i == 0 ? com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_FIRST :
            i == scrollingItems.length - 1 ? com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_LAST : com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_MIDDLE;
          android.widget.TextView view = OptionsLayout.genOptionView(c.context(), scrollingItems[i].id, scrollingItems[i].name, scrollingItems[i].textColor, scrollingItems[i].icon, scrollingItems[i].iconColor, onClick, c.getThemeListeners(), null);
          com.fahadali.xgram.appearance.XgramExpressive.MorphDrawable morph = com.fahadali.xgram.appearance.XgramExpressive.attachPressMorph(view,
            com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT),
            com.fahadali.xgram.appearance.XgramExpressive.CORNER_NONE, com.fahadali.xgram.appearance.XgramExpressive.LIST_PRESSED_CORNER);
          morph.setColorRole(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_BRIGHT);
          morph.setSegment(segment);
          LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f));
          params.leftMargin = params.rightMargin = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_INSET);
          params.topMargin = i == 0 ? Screen.dp(4f) : Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_GAP);
          params.bottomMargin = i == scrollingItems.length - 1 ? Screen.dp(8f) : 0;
          content.addView(view, params);
          contentHeight += params.height + params.topMargin + params.bottomMargin;
        }
      }
      int totalHeight = 0;
      if (content.getChildCount() > 0) {
        ScrollView scrollView = new ScrollView(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            int maxHeight = maxScrollHeight;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
              maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
          }
        };
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        optionsLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        totalHeight += Math.min(contentHeight, maxScrollHeight); // only used for the slide-in animation
      }
      // the actions row: Translate (text), Copy (tonal), Apply (filled) at the end, in that order; Cancel goes
      android.widget.TextView translate = null, copy = null, apply = null;
      List<View> others = new ArrayList<>();
      for (ViewController.OptionItem item : items) {
        if (com.fahadali.xgram.appearance.XgramR5Sheets.isPlainCancel(item)) {
          continue;
        }
        android.widget.TextView button;
        if (item.id == R.id.btn_xgramAiApply) {
          button = apply = com.fahadali.xgram.appearance.XgramR5Sheets.filledButton(context, item.name, false, null);
        } else if (item.id == R.id.btn_copyText) {
          button = copy = com.fahadali.xgram.appearance.XgramR5Sheets.tonalButton(context, item.name, null);
        } else if (item.id == R.id.btn_xgramAiTranslate) {
          button = translate = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context, item.name, com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
        } else {
          button = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context, item.name, com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, null);
          others.add(button);
        }
        button.setId(item.id);
        button.setOnClickListener(onClick);
      }
      if (translate != null || copy != null || apply != null || !others.isEmpty()) {
        List<View> end = new ArrayList<>(others);
        end.add(translate);
        end.add(copy);
        end.add(apply);
        LinearLayout actions = com.fahadali.xgram.appearance.XgramR5Sheets.actionsRow(context, null, end.toArray(new View[0]));
        optionsLayout.addView(actions);
        totalHeight += ((LinearLayout.LayoutParams) actions.getLayoutParams()).height;
      } else {
        View spacer = new View(context);
        optionsLayout.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(8f)));
        totalHeight += Screen.dp(8f);
      }
      return totalHeight;
    }, null);
  }

  private static View optionView (ViewController<?> c, ViewController.OptionItem item, View.OnClickListener onClick) {
    android.widget.TextView view = OptionsLayout.genOptionView(c.context(), item.id, item.name, item.textColor, item.icon, item.iconColor, onClick, c.getThemeListeners(), null);
    RippleSupport.setTransparentSelector(view);
    view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f)));
    return view;
  }
}
