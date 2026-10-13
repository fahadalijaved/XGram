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
import android.os.Build;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageMedia;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.ChatId;
import tgx.td.Td;
import tgx.td.data.MessageWithProperties;

/**
 * Quote (official Telegram's "select to quote"): reply quoting the part of a message's text the user selects. Telegram
 * X shows and sends quotes, but text in bubbles can't be selected, so the text opens in a sheet where it can.
 */
public final class XgramQuote {
  private XgramQuote () { }

  // The message to quote

  private static boolean hasText (Tdlib tdlib, @Nullable TdApi.Message message) {
    if (message == null || message.id == 0 || message.content == null || message.content.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
      return false;
    }
    if (tdlib.messageBeingEdited(message)) {
      // The bubble already shows the new text, the message still has the old one.
      return false;
    }
    TdApi.FormattedText text = Td.textOrCaption(message.content);
    return text != null && !XgramQuoteText.isBlank(text.text);
  }

  /** The album part with the caption, else the newest message with text, as Copy picks it. Null without text. */
  @Nullable
  private static TdApi.Message quotable (TGMessage msg) {
    Tdlib tdlib = msg.tdlib();
    if (msg instanceof TGMessageMedia) {
      long captionMessageId = ((TGMessageMedia) msg).getCaptionMessageId();
      TdApi.Message message = captionMessageId != 0 ? msg.getMessage(captionMessageId) : null;
      if (hasText(tdlib, message)) {
        return message;
      }
    }
    TdApi.Message newest = msg.getNewestMessage();
    if (hasText(tdlib, newest)) {
      return newest;
    }
    TdApi.Message[] messages = msg.getAllMessages();
    for (int i = messages.length - 1; i >= 0; i--) {
      if (hasText(tdlib, messages[i])) {
        return messages[i];
      }
    }
    return null;
  }

  /** Adds Quote right after Reply. TDLib takes no quotes in secret chats. */
  public static void fill (TGMessage msg, IntList ids, IntList icons, StringList strings) {
    if (ChatId.isSecret(msg.getChatId()) || quotable(msg) == null) {
      return;
    }
    ids.append(R.id.btn_xgramQuote);
    strings.append(R.string.XgramQuote);
    icons.append(R.drawable.baseline_format_quote_close_24);
  }

  // Sheet

  /** The message text, selectable, with Reply with quote, Copy and Cancel. */
  public static void open (MessagesController m, TGMessage msg) {
    TdApi.Message message = quotable(msg);
    if (message == null || m.isDestroyed()) {
      return;
    }
    TdApi.FormattedText text = Td.textOrCaption(message.content);
    if (text == null) {
      return;
    }
    boolean canCopy = msg.canBeSaved();
    int maxScrollHeight = Math.round(Screen.currentHeight() * .55f);
    m.showPopup(Lang.getString(R.string.XgramQuoteSelectHint), false, Text.LINE_COUNT_UNLIMITED, (popupLayout, optionsLayout) -> {
      // In the activity's own window: a popup window can't take focus, and text selection needs it.
      popupLayout.setNeedRootInsets();
      popupLayout.setHideKeyboard();
      Context context = optionsLayout.getContext();

      TextView textView = newTextView(m, context, text, canCopy);
      // Read when a button is touched, in case the selection is gone by the time it is clicked.
      int[] touchedSelection = {-1, -1};
      Runnable dropSelection = () -> {
        CharSequence current = textView.getText();
        if (current instanceof Spannable) {
          Selection.removeSelection((Spannable) current);
        }
      };
      RunnableData<int[]> replyWith = selection -> {
        dropSelection.run();
        popupLayout.hideWindow(true);
        reply(m, message, text, selection[0], selection[1]);
      };
      textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode (ActionMode mode, Menu menu) {
          menu.add(Menu.NONE, R.id.btn_xgramQuoteReply, 0, Lang.getString(R.string.XgramQuote));
          return true;
        }

        @Override
        public boolean onPrepareActionMode (ActionMode mode, Menu menu) {
          if (!canCopy) {
            // Protected content: no copy, share or other apps.
            menu.clear();
            menu.add(Menu.NONE, R.id.btn_xgramQuoteReply, 0, Lang.getString(R.string.XgramQuote));
            menu.add(Menu.NONE, android.R.id.selectAll, 1, android.R.string.selectAll);
          }
          return true;
        }

        @Override
        public boolean onActionItemClicked (ActionMode mode, MenuItem item) {
          if (item.getItemId() == R.id.btn_xgramQuoteReply) {
            // Read it first: finishing the toolbar collapses the selection.
            int[] selection = selectionOf(textView, null);
            mode.finish();
            replyWith.runWithData(selection);
            return true;
          }
          return false;
        }

        @Override
        public void onDestroyActionMode (ActionMode mode) { }
      });

      popupLayout.setDismissListener(new PopupLayout.DismissListener() {
        @Override
        public void onPopupDismissPrepare (PopupLayout popup) {
          dropSelection.run();
        }

        @Override
        public void onPopupDismiss (PopupLayout popup) {
          m.getThemeListeners().removeThemeListenerByTarget(textView);
          if (textView instanceof EmojiTextView) {
            ((EmojiTextView) textView).performDestroy();
          }
        }
      });

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
      scrollView.addView(textView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      optionsLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      textView.measure(View.MeasureSpec.makeMeasureSpec(Screen.currentWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int totalHeight = Math.min(textView.getMeasuredHeight(), maxScrollHeight); // only used for the slide-in animation

      View.OnTouchListener onTouch = (v, e) -> {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
          touchedSelection[0] = textView.getSelectionStart();
          touchedSelection[1] = textView.getSelectionEnd();
        }
        return false;
      };
      View.OnClickListener onClick = v -> {
        int id = v.getId();
        if (id == R.id.btn_xgramQuoteReply) {
          replyWith.runWithData(selectionOf(textView, touchedSelection));
          return;
        }
        if (id == R.id.btn_copyText) {
          int[] selection = selectionOf(textView, touchedSelection);
          int[] range = XgramQuoteText.range(text, selection[0], selection[1]);
          if (range != null) {
            UI.copyText(TD.toCopyText(Td.substring(text, range[0], range[1])), R.string.CopiedText);
          }
        }
        dropSelection.run();
        popupLayout.hideWindow(true);
      };
      ViewController.OptionItem[] items = canCopy ? new ViewController.OptionItem[] {
        new ViewController.OptionItem(R.id.btn_xgramQuoteReply, Lang.getString(R.string.XgramQuoteReply), ViewController.OptionColor.BLUE, R.drawable.baseline_format_quote_close_24),
        new ViewController.OptionItem(R.id.btn_copyText, Lang.getString(R.string.Copy), ViewController.OptionColor.NORMAL, R.drawable.baseline_content_copy_24),
        new ViewController.OptionItem(R.id.btn_cancel, Lang.getString(R.string.Cancel), ViewController.OptionColor.NORMAL, R.drawable.baseline_cancel_24)
      } : new ViewController.OptionItem[] {
        new ViewController.OptionItem(R.id.btn_xgramQuoteReply, Lang.getString(R.string.XgramQuoteReply), ViewController.OptionColor.BLUE, R.drawable.baseline_format_quote_close_24),
        new ViewController.OptionItem(R.id.btn_cancel, Lang.getString(R.string.Cancel), ViewController.OptionColor.NORMAL, R.drawable.baseline_cancel_24)
      };
      for (ViewController.OptionItem item : items) {
        TextView view = OptionsLayout.genOptionView(m.context(), item.id, item.name, item.textColor, item.icon, item.iconColor, onClick, m.getThemeListeners(), null);
        RippleSupport.setTransparentSelector(view);
        view.setOnTouchListener(onTouch);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f)));
        optionsLayout.addView(view);
        totalHeight += Screen.dp(54f);
      }
      return totalHeight;
    }, null);
  }

  /** The current selection, or the one when a button was touched if it has gone since. */
  private static int[] selectionOf (TextView textView, @Nullable int[] touchedSelection) {
    int start = textView.getSelectionStart(), end = textView.getSelectionEnd();
    if (start == end && touchedSelection != null && touchedSelection[0] != touchedSelection[1] && touchedSelection[0] >= 0 && touchedSelection[1] >= 0) {
      start = touchedSelection[0];
      end = touchedSelection[1];
    }
    if (start < 0 || end < 0) {
      start = end = 0;
    }
    return new int[] {Math.min(start, end), Math.max(start, end)};
  }

  /** The text with its formatting; spoilers shown with a background. Same characters as the message, so offsets match. */
  private static CharSequence displayText (TdApi.FormattedText text) {
    CharSequence formatted = TD.toDisplayCharSequence(text);
    if (formatted == null) {
      formatted = text.text;
    }
    SpannableStringBuilder b = new SpannableStringBuilder(formatted);
    if (text.entities != null) {
      for (TdApi.TextEntity entity : text.entities) {
        if (entity.type != null && Td.isSpoiler(entity.type) && entity.offset >= 0 && entity.offset + entity.length <= b.length()) {
          b.setSpan(new CustomTypefaceSpan(null, 0).setBackgroundColorId(ColorId.placeholder), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
    }
    return b;
  }

  private static TextView newTextView (MessagesController m, Context context, TdApi.FormattedText text, boolean canCopy) {
    CharSequence display = displayText(text);
    TextView textView = canCopy ? new EmojiTextView(context) : new EmojiTextView(context) {
      @Override
      public boolean onTextContextMenuItem (int id) {
        return !isCopyOut(id) && super.onTextContextMenuItem(id);
      }
    };
    setUpTextView(m, textView, display);
    if (!text.text.contentEquals(textView.getText())) {
      // Selection offsets must be the message's own: drop the emoji images if they changed the text.
      ((EmojiTextView) textView).performDestroy();
      textView = canCopy ? new TextView(context) : new TextView(context) {
        @Override
        public boolean onTextContextMenuItem (int id) {
          return !isCopyOut(id) && super.onTextContextMenuItem(id);
        }
      };
      setUpTextView(m, textView, display);
    }
    if (!canCopy) {
      keepInApp(textView);
    }
    // The text is set and measured before the sheet is in a window, and TextView only checks whether it can show
    // selection handles then ("TextView does not support text selection"). Check again once it is attached.
    TextView selectable = textView;
    textView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View v) {
        selectable.post(() -> {
          if (selectable.isAttachedToWindow()) {
            selectable.setTextIsSelectable(false);
            selectable.setTextIsSelectable(true);
          }
        });
      }

      @Override
      public void onViewDetachedFromWindow (View v) { }
    });
    return textView;
  }

  /** Copy, cut and share, from the text menus, keyboard shortcuts or accessibility services. */
  private static boolean isCopyOut (int id) {
    return id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.shareText;
  }

  /** Protected content: the selected text can't be dragged into another app, and copy isn't offered to accessibility. */
  private static void keepInApp (TextView textView) {
    float[] down = new float[2];
    textView.setOnTouchListener((v, e) -> {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
        down[0] = e.getX();
        down[1] = e.getY();
      }
      return false;
    });
    // A long press on the selected text starts dragging it, and the drop can be in any app.
    textView.setOnLongClickListener(v -> {
      int start = Math.min(textView.getSelectionStart(), textView.getSelectionEnd());
      int end = Math.max(textView.getSelectionStart(), textView.getSelectionEnd());
      if (start < 0 || start == end) {
        return false;
      }
      int offset = textView.getOffsetForPosition(down[0], down[1]);
      return offset >= start && offset <= end;
    });
    textView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
      @Override
      public void onInitializeAccessibilityNodeInfo (View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY);
          info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT);
        }
      }
    });
  }

  private static void setUpTextView (MessagesController m, TextView textView, CharSequence display) {
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Settings.instance().getChatFontSize());
    textView.setLineSpacing(Screen.dp(2f), 1f);
    textView.setGravity(Gravity.START);
    textView.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(14f));
    textView.setTextColor(Theme.getColor(ColorId.text));
    m.addThemeTextColorListener(textView, ColorId.text);
    textView.setLinkTextColor(Theme.textLinkColor());
    m.addThemeLinkTextColorListener(textView, ColorId.textLink);
    textView.setHighlightColor(Theme.fillingTextSelectionColor());
    m.addThemeHighlightColorListener(textView, ColorId.textSelectionHighlight);
    textView.setTextIsSelectable(true);
    textView.setText(display);
  }

  // Reply

  private static void reply (MessagesController m, TdApi.Message message, TdApi.FormattedText text, int selectionStart, int selectionEnd) {
    Tdlib tdlib = m.tdlib();
    int maxLength = tdlib.options().messageReplyQuoteLengthMax;
    if (maxLength <= 0) {
      maxLength = XgramQuoteText.DEFAULT_LENGTH_MAX;
    }
    XgramQuoteText.Quote quote = XgramQuoteText.quote(text, selectionStart, selectionEnd, maxLength);
    if (quote == null) {
      return;
    }
    int lengthMax = maxLength;
    // The sheet can stay open a while: the message may have been edited or deleted since, so read it again.
    tdlib.send(new TdApi.GetMessage(message.chatId, message.id), (current, error) -> {
      TdApi.InputTextQuote inputQuote = current != null ? XgramQuoteText.relocate(quote.quote, text, Td.textOrCaption(current.content)) : null;
      if (inputQuote == null) {
        m.runOnUiThreadOptional(() -> UI.showToast(current != null ? R.string.XgramQuoteChanged : R.string.MessageNotFound, Toast.LENGTH_SHORT));
        return;
      }
      // Same as Reply.
      tdlib.send(new TdApi.GetMessageProperties(current.chatId, current.id), (properties, propertiesError) -> m.runOnUiThreadOptional(() -> {
        if (properties == null) {
          return;
        }
        if (quote.wasCut) {
          UI.showToast(Lang.getString(R.string.XgramQuoteTooLong, lengthMax), Toast.LENGTH_SHORT);
        }
        m.showReply(new MessageWithProperties(current, properties), inputQuote, 0, "", true, true);
        InputView inputView = m.xgramInputView();
        if (inputView != null && inputView.isEmpty()) {
          Keyboard.show(inputView);
        }
      }));
    });
  }
}
