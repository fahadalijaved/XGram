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
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramR5Sheets;
import com.fahadali.xgram.topics.XgramForums;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.Td;

/**
 * Forward in Select text (XgramTextSelection) offers what the forward sheet does for messages (XgramForwardOptions):
 * With sender sends the comment, in each picked chat, as a reply quoting the selected words in their own message
 * (official Telegram's reply in another chat: the words arrive with their sender's name), and Text only sends the words
 * as the user's own message, as before. The share sheet (ShareController) shows the choice above its comment field
 * with a preview; Settings → XGram → Messages → Forwarding picks how it starts (Show sender: With sender, Hide sender:
 * Text only, Remember my last choice: the last choice for selected text). With sender needs a comment (it is the
 * message that carries the quote), and is greyed out with the reason in secret chats, for a translation or an edit on
 * its way, and for a message that can't be replied to in another chat.
 */
public final class XgramForwardQuote {
  private XgramForwardQuote () { }

  /** Why With sender can't be used ({@link #blockReason}); BLOCK_NONE when it can. */
  public static final int BLOCK_NONE = 0, BLOCK_SECRET_SOURCE = 1, BLOCK_NOT_ORIGINAL = 2, BLOCK_GONE = 3, BLOCK_CHANGED = 4, BLOCK_CANT_REPLY = 5, BLOCK_SECRET_TARGET = 6;

  /** Whether the share sheet starts with With sender: Forwarding's default, or the last choice for selected text. */
  public static boolean withSenderByDefault () {
    XgramSettings settings = XgramSettings.instance();
    switch (settings.forwardSender()) {
      case XgramSettings.FORWARD_HIDE_SENDER:
        return false;
      case XgramSettings.FORWARD_REMEMBER:
        return !settings.forwardLastTextOnly();
      case XgramSettings.FORWARD_SHOW_SENDER:
      default:
        return true;
    }
  }

  /** After selected text was sent while both choices were open: its choice, for Remember my last choice. */
  public static void remember (boolean withSender) {
    XgramSettings settings = XgramSettings.instance();
    if (settings.forwardSender() == XgramSettings.FORWARD_REMEMBER && settings.forwardLastTextOnly() == withSender) {
      settings.setForwardLastTextOnly(!withSender);
    }
  }

  /** The one-line reason With sender is greyed out; null for BLOCK_NONE. */
  @Nullable
  public static String blockReason (int block) {
    switch (block) {
      case BLOCK_SECRET_SOURCE:
        return Lang.getString(R.string.XgramFwdQuoteSecretSource);
      case BLOCK_SECRET_TARGET:
        return Lang.getString(R.string.XgramFwdQuoteSecretTarget);
      case BLOCK_NOT_ORIGINAL:
        return Lang.getString(R.string.XgramFwdQuoteNotOriginal);
      case BLOCK_GONE:
        return Lang.getString(R.string.XgramFwdQuoteGone);
      case BLOCK_CHANGED:
        return Lang.getString(R.string.XgramQuoteChanged);
      case BLOCK_CANT_REPLY:
        return Lang.getString(R.string.XgramFwdQuoteCantReply);
      case BLOCK_NONE:
      default:
        return null;
    }
  }

  private static int quoteLengthMax (Tdlib tdlib) {
    final int maxLength = tdlib.options().messageReplyQuoteLengthMax;
    return maxLength > 0 ? maxLength : XgramQuoteText.DEFAULT_LENGTH_MAX;
  }

  // The words and their message

  /**
   * The selected words' message and their quote, as the share sheet keeps them (ShareController.Args
   * xgramSetQuoteSource), with what TDLib says of the message: whether it can be replied to in another chat, and
   * whether it still has the words. Main thread only.
   */
  public static final class Source {
    private TdApi.Message message;
    private @Nullable TdApi.InputTextQuote quote;
    private final boolean wasCut;
    private @Nullable TdApi.MessageProperties properties;
    private int loadBlock = BLOCK_NONE;

    /** {@code quote}: the words as XgramQuoteText takes them from the message's own text; null for a translation or an edit on its way. */
    public Source (TdApi.Message message, @Nullable XgramQuoteText.Quote quote) {
      this.message = message;
      this.quote = quote != null ? quote.quote : null;
      this.wasCut = quote != null && quote.wasCut;
    }

    /** Why With sender can't be used, whatever the chats; while the properties load, only what is known already counts. */
    public int block () {
      if (ChatId.isSecret(message.chatId)) {
        return BLOCK_SECRET_SOURCE;
      }
      if (quote == null) {
        return BLOCK_NOT_ORIGINAL;
      }
      if (loadBlock != BLOCK_NONE) {
        return loadBlock;
      }
      if (properties != null && !properties.canBeRepliedInAnotherChat && !properties.canBeReplied) {
        return BLOCK_CANT_REPLY;
      }
      return BLOCK_NONE;
    }

    /** Why With sender can't be used in {@code chatId} ({@code topicId}: the topic picked there); BLOCK_NONE when it can. */
    public int blockIn (long chatId, @Nullable TdApi.MessageTopic topicId) {
      if (ChatId.isSecret(chatId)) {
        return BLOCK_SECRET_TARGET; // TDLib takes no quotes and no replies to other chats there
      }
      if (properties != null && replyTo(chatId, topicId) == null) {
        return BLOCK_CANT_REPLY;
      }
      return BLOCK_NONE;
    }

    /**
     * The reply quoting the words in {@code chatId}: in the message's own chat and topic a reply with a quote (the
     * message menu's Quote), anywhere else a reply to a message of another chat. Null where TDLib would send the
     * comment without it: secret chats, or a message that can't be replied to there (or not known yet).
     */
    @Nullable
    public TdApi.InputMessageReplyTo replyTo (long chatId, @Nullable TdApi.MessageTopic topicId) {
      if (quote == null || properties == null || loadBlock != BLOCK_NONE || ChatId.isSecret(chatId) || ChatId.isSecret(message.chatId)) {
        return null;
      }
      // TDLib drops a reply to a message of another topic of the same chat, and files one sent with no topic (a forum's
      // General) in the replied message's topic: those go as a reply to another chat
      final boolean sameTopic = topicId != null ? Td.equalsTo(topicId, message.topicId) :
        !(message.topicId instanceof TdApi.MessageTopicForum) || ((TdApi.MessageTopicForum) message.topicId).forumTopicId == XgramForums.GENERAL_TOPIC_ID;
      if (chatId == message.chatId && properties.canBeReplied && sameTopic) {
        return new TdApi.InputMessageReplyToMessage(message.id, quote, 0, "");
      }
      return properties.canBeRepliedInAnotherChat ? new TdApi.InputMessageReplyToExternalMessage(message.chatId, message.id, quote, 0, "") : null;
    }

    /**
     * Asks TDLib about the message: its properties, and with {@code refresh} (before sending) the message as it is now,
     * where an edit may have moved the words or taken them out. {@code after} runs on the main thread while
     * {@code owner} lives.
     */
    public void load (Tdlib tdlib, boolean refresh, ViewController<?> owner, Runnable after) {
      final long chatId = message.chatId, messageId = message.id;
      final TdApi.InputTextQuote quote = this.quote;
      if (!refresh) {
        tdlib.send(new TdApi.GetMessageProperties(chatId, messageId), (properties, error) -> owner.runOnUiThreadOptional(() -> {
          this.properties = properties;
          this.loadBlock = properties != null ? BLOCK_NONE : BLOCK_GONE;
          after.run();
        }));
        return;
      }
      tdlib.send(new TdApi.GetMessage(chatId, messageId), (current, error) -> {
        if (current == null) {
          owner.runOnUiThreadOptional(() -> {
            this.properties = null;
            this.loadBlock = BLOCK_GONE;
            after.run();
          });
          return;
        }
        final TdApi.InputTextQuote relocated = quote != null ? XgramQuoteText.relocate(quote, Td.textOrCaption(current.content)) : null;
        tdlib.send(new TdApi.GetMessageProperties(chatId, messageId), (properties, propertiesError) -> owner.runOnUiThreadOptional(() -> {
          this.message = current;
          this.properties = properties;
          if (properties == null) {
            this.loadBlock = BLOCK_GONE;
          } else if (quote != null && relocated == null) {
            this.loadBlock = BLOCK_CHANGED; // edited, and the words are no longer in it
          } else {
            this.loadBlock = BLOCK_NONE;
            if (relocated != null) {
              this.quote = relocated;
            }
          }
          after.run();
        }));
      });
    }
  }

  // The share sheet: the options above its comment field, sliding in with it as chats are picked

  /** What the options change; ShareController keeps the choice. */
  public interface Callback {
    void onQuoteSenderChanged (boolean withSender);
  }

  /**
   * The share sheet's options for selected text, as the forward sheet's (XgramForwardOptions.Panel): a preview of what
   * arrives, With sender | Text only (an M3E connected button group with Expressive design, Telegram X's radio buttons
   * otherwise), and why With sender is greyed out, or that the quote was cut. The sheet's own color behind it; the
   * classic look ends it with a separator.
   */
  public static final class Panel extends LinearLayout {
    private final Tdlib tdlib;
    private final Source source;
    private final boolean m3;
    private final QuotePreview preview;
    private final XgramForwardOptions.SenderGroup sender;
    private final TextView info;

    private boolean typing, hasInfo;

    public Panel (Context context, Tdlib tdlib, @Nullable ViewController<?> themeProvider, Source source, TdApi.FormattedText words, int backgroundColorId, Callback callback) {
      super(context);
      this.tdlib = tdlib;
      this.source = source;
      this.m3 = XgramExpressive.isEnabled();
      setOrientation(VERTICAL);
      ViewSupport.setThemedBackground(this, backgroundColorId, themeProvider);

      preview = new QuotePreview(context, tdlib, source, words, m3);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (m3) {
        // a surfaceContainerHigh card inside the sheet, as the forward sheet's preview
        params.leftMargin = params.rightMargin = Screen.dp(16f);
        params.topMargin = Screen.dp(8f);
      }
      addView(preview, params);

      // With sender is the one that can be greyed out
      sender = new XgramForwardOptions.SenderGroup(context, m3, new int[] {R.string.XgramFwdQuoteWithSender, R.string.XgramFwdQuoteTextOnly}, 0, index -> callback.onQuoteSenderChanged(index == 0));
      addView(sender, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      info = new TextView(context);
      if (m3) {
        XgramExpressive.applyText(info, XgramExpressive.TS_BODY_S, false);
        info.setGravity(Gravity.CENTER); // under the centered buttons
      } else {
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
        info.setTypeface(Fonts.getRobotoRegular());
        info.setGravity(Lang.gravity());
      }
      info.setMaxLines(2);
      info.setEllipsize(TextUtils.TruncateAt.END);
      info.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(8f));
      addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      setState(XgramForwardQuote.withSenderByDefault(), source.block(), false);
      refreshColors();
    }

    /** The choice as ShareController has it; {@code block}: why With sender can't be used for the chats picked now. */
    public void setState (boolean withSender, int block, boolean animated) {
      final boolean open = block == BLOCK_NONE;
      final boolean quoted = withSender && open;
      sender.setState(quoted ? 0 : 1, open, animated);
      final String reason;
      if (!open) {
        reason = blockReason(block);
      } else if (quoted && source.wasCut) {
        reason = Lang.getString(R.string.XgramQuoteTooLong, quoteLengthMax(tdlib));
      } else {
        reason = null;
      }
      info.setText(reason);
      hasInfo = reason != null;
      // TalkBack: why With sender is greyed out is read with the button, not as a line after it
      final String disabledReason = open ? null : reason;
      sender.setDisabledReason(disabledReason);
      info.setImportantForAccessibility(disabledReason != null ? IMPORTANT_FOR_ACCESSIBILITY_NO : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
      preview.setState(quoted, animated);
      updateVisibility();
    }

    /** The comment field's text: With sender's preview shows it under the quote. */
    public void setComment (@Nullable String comment) {
      preview.setComment(comment);
    }

    /** While the keyboard (or the emoji keyboard) is up. */
    public void setTyping (boolean typing) {
      if (this.typing != typing) {
        this.typing = typing;
        updateVisibility();
      }
    }

    private void updateVisibility () {
      // As the forward sheet's options: no preview in a short window (a phone in landscape) or while typing, and
      // while typing only the choice stays, unless the window is tall (a tablet, an unfolded Fold)
      final int heightDp = getResources().getConfiguration().screenHeightDp;
      final boolean shortWindow = heightDp < XgramForwardOptions.SHORT_WINDOW_DP;
      final boolean shown = !(typing && shortWindow);
      final boolean extras = shown && (!typing || heightDp >= XgramForwardOptions.TALL_WINDOW_DP);
      setVisible(preview, shown && !shortWindow && !typing);
      setVisible(sender, shown);
      setVisible(info, extras && hasInfo);
    }

    private static void setVisible (View view, boolean visible) {
      final int visibility = visible ? View.VISIBLE : View.GONE;
      if (view.getVisibility() != visibility) {
        view.setVisibility(visibility);
      }
    }

    @Override
    protected void onConfigurationChanged (Configuration newConfig) {
      super.onConfigurationChanged(newConfig);
      updateVisibility(); // turned, folded or unfolded
    }

    /** Theme changes (ShareController.onThemeColorsChanged). */
    public void refreshColors () {
      preview.refreshColors();
      sender.refreshColors();
      info.setTextColor(m3 ? XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT) : Theme.textDecentColor());
      invalidate();
    }

    public void performDestroy () {
      preview.performDestroy();
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      super.dispatchDraw(c);
      if (!m3 && getHeight() > 0) {
        // Telegram X's separator between the options and the comment field
        final int size = Math.max(1, Screen.separatorSize());
        c.drawRect(0, getHeight() - size, getWidth(), getHeight(), Paints.fillingPaint(Theme.separatorColor()));
      }
    }
  }

  // The preview

  /**
   * What arrives: With sender, the quote (the sender's name and up to three lines of the words) and the comment under
   * it; Text only, the words alone, as a message of the user's own. The two cross-fade; the card keeps the height of
   * the taller one, so the sheet doesn't move as the choice changes.
   */
  private static final class QuotePreview extends FrameLayout {
    private final boolean m3;
    private final String name, quoteText, plainText;
    private final LinearLayout quoted;
    private final QuoteBlock block;
    private final EmojiTextView nameView, quoteView, commentView, plainView;
    private final XgramForwardOptions.Factor senderShown;
    private boolean withSender = true;
    private String comment = "";

    QuotePreview (Context context, Tdlib tdlib, Source source, TdApi.FormattedText words, boolean m3) {
      super(context);
      this.m3 = m3;
      final boolean rtl = Lang.rtl();

      String name = tdlib.senderName(source.message, true, false); // the original sender of a message forwarded before
      if (StringUtils.isEmpty(name)) {
        name = tdlib.chatTitle(source.message.chatId);
      }
      this.name = name != null ? name : "";
      this.plainText = words.text != null ? words.text : "";
      this.quoteText = source.quote != null && source.quote.text != null ? source.quote.text.text : plainText;

      if (m3) {
        XgramR5Sheets.applyCard(this, XgramExpressive.SURFACE_CONTAINER_HIGH, XgramExpressive.CORNER_L);
        setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
      } else {
        setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));
      }

      quoted = new LinearLayout(context);
      quoted.setOrientation(LinearLayout.VERTICAL);
      quoted.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      block = new QuoteBlock(context, m3);
      // The text starts after the line: M3E's 3dp line in its block, Telegram X's 2dp bar 10dp before the text
      final int blockStart = Screen.dp(12f), blockEnd = Screen.dp(m3 ? 10f : 0f), blockVertical = Screen.dp(m3 ? 6f : 1f);
      block.setPadding(rtl ? blockEnd : blockStart, blockVertical, rtl ? blockStart : blockEnd, blockVertical);
      nameView = newLine(context, m3 ? XgramExpressive.TS_LABEL_L : -1, 14f, true, 1);
      nameView.setText(this.name);
      block.addView(nameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      quoteView = newLine(context, m3 ? XgramExpressive.TS_BODY_M : -1, 15f, false, 3);
      quoteView.setText(quoteText);
      block.addView(quoteView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      quoted.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      commentView = newLine(context, m3 ? XgramExpressive.TS_BODY_M : -1, 15f, false, 1);
      LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      commentParams.topMargin = Screen.dp(m3 ? 8f : 6f);
      quoted.addView(commentView, commentParams);
      addView(quoted, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

      plainView = newLine(context, m3 ? XgramExpressive.TS_BODY_M : -1, 15f, false, 3);
      plainView.setText(plainText);
      plainView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      addView(plainView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

      senderShown = new XgramForwardOptions.Factor(m3, true, this::applyFactors);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
      setComment(null);
      applyFactors();
      refreshColors();
    }

    private static EmojiTextView newLine (Context context, int ts, float classicSize, boolean classicMedium, int maxLines) {
      EmojiTextView view = new EmojiTextView(context);
      if (ts >= 0) {
        XgramExpressive.applyText(view, ts, false);
      } else {
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, classicSize);
        view.setTypeface(classicMedium ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
      }
      view.setGravity(Lang.gravity());
      if (maxLines == 1) {
        view.setSingleLine(true);
      } else {
        view.setMaxLines(maxLines);
      }
      view.setEllipsize(TextUtils.TruncateAt.END);
      return view;
    }

    void setState (boolean withSender, boolean animated) {
      this.withSender = withSender;
      senderShown.set(withSender, animated && isAttachedToWindow());
      applyFactors();
      updateDescription();
    }

    /** The comment under the quote; while there is none, what With sender needs, in the placeholder colour. */
    void setComment (@Nullable String comment) {
      this.comment = comment != null ? comment.trim() : "";
      commentView.setText(this.comment.isEmpty() ? Lang.getString(R.string.XgramFwdQuoteNeedsComment) : this.comment);
      refreshCommentColor();
      updateDescription();
    }

    private void applyFactors () {
      final float shown = senderShown.get();
      quoted.setAlpha(shown);
      plainView.setAlpha(1f - shown);
    }

    /** TalkBack reads the card as one line. */
    private void updateDescription () {
      final String text;
      if (withSender) {
        text = Lang.getString(R.string.XgramFwdQuotePreviewQuote, name, cut(quoteText, 200)) + ". " + (comment.isEmpty() ? Lang.getString(R.string.XgramFwdQuoteNeedsComment) : cut(comment, 200));
      } else {
        text = cut(plainText, 200);
      }
      setContentDescription(Lang.getString(R.string.XgramFwdPreview, text));
    }

    private void refreshCommentColor () {
      final boolean empty = comment.isEmpty();
      if (m3) {
        commentView.setTextColor(XgramExpressive.color(empty ? XgramExpressive.ON_SURFACE_VARIANT : XgramExpressive.ON_SURFACE));
      } else {
        commentView.setTextColor(empty ? Theme.textPlaceholderColor() : Theme.textAccentColor());
      }
    }

    void refreshColors () {
      if (m3) {
        nameView.setTextColor(XgramExpressive.color(XgramExpressive.PRIMARY));
        quoteView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
        plainView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
      } else {
        nameView.setTextColor(Theme.textLinkColor());
        quoteView.setTextColor(Theme.textAccentColor());
        plainView.setTextColor(Theme.textAccentColor());
      }
      refreshCommentColor();
      block.invalidate();
      invalidate();
    }

    void performDestroy () {
      nameView.performDestroy();
      quoteView.performDestroy();
      commentView.performDestroy();
      plainView.performDestroy();
    }

    private static String cut (String text, int max) {
      if (text.length() <= max) {
        return text;
      }
      int end = max;
      if (Character.isHighSurrogate(text.charAt(end - 1))) {
        end--;
      }
      return text.substring(0, end) + "…";
    }
  }

  /**
   * The quote: the M3E reply of a bubble (a block tinted with primary, 12dp corners, the line along its start edge)
   * with Expressive design, Telegram X's quote bar otherwise.
   */
  private static final class QuoteBlock extends LinearLayout {
    private final boolean m3;
    private final RectF rect = new RectF();
    private final Path path = new Path();

    QuoteBlock (Context context, boolean m3) {
      super(context);
      this.m3 = m3;
      setOrientation(VERTICAL);
      setWillNotDraw(false);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final int width = getWidth(), height = getHeight();
      if (width > 0 && height > 0) {
        final boolean rtl = Lang.rtl();
        if (m3) {
          final int lineColor = XgramExpressive.color(XgramExpressive.PRIMARY);
          final float radius = Math.min(Screen.dp(12f), Math.min(width, height) / 2f);
          rect.set(0, 0, width, height);
          c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(XgramExpressive.withAlpha(lineColor, .10f)));
          path.rewind();
          path.addRoundRect(rect, radius, radius, Path.Direction.CW);
          final int saveCount = c.save();
          c.clipPath(path);
          final int lineWidth = Screen.dp(3f);
          c.drawRect(rtl ? width - lineWidth : 0, 0, rtl ? width : lineWidth, height, Paints.fillingPaint(lineColor));
          c.restoreToCount(saveCount);
        } else {
          final int lineWidth = Screen.dp(2f);
          c.drawRect(rtl ? width - lineWidth : 0, 0, rtl ? width : lineWidth, height, Paints.fillingPaint(Theme.getColor(ColorId.messageVerticalLine)));
        }
      }
      super.onDraw(c);
    }
  }
}
