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
package com.fahadali.xgram.topics;

import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Profile;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextStyleProvider;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import tgx.td.Td;

/**
 * Layout of one row in the forum topic list, modeled on {@link org.thunderdog.challegram.data.TGChat}: in the chat list
 * view's mode (One line, Two lines, Three lines, Three lines with bigger text) with the chat list's places and styles.
 */
public final class TGForumTopic implements Counter.Callback, ContentPreview.RefreshCallback, TdlibStatusManager.HelperTarget {
  private final ViewController<?> context;
  private final Tdlib tdlib;
  private final long chatId;
  private TdApi.ForumTopic topic;

  private final MultipleViewProvider currentViews = new MultipleViewProvider();
  private final Counter counter, mentionCounter, reactionsCounter;
  private ForumTopicIconDrawable iconDrawable;
  // What iconDrawable was built from. A rename arrives on the same ForumTopic object the row already holds,
  // so comparing the new info with this.topic.info would never notice it.
  private String iconName;
  private int iconColor;
  private long iconCustomEmojiId;
  private boolean iconIsGeneral;

  private int currentWidth;
  /** The chat list view this row is laid out for (Settings.CHAT_MODE_*). */
  private int listMode;
  private Text title;
  private String time = "";
  private int timeWidth, timeLeft, checksRight, muteLeft, lockLeft;

  private boolean hasPrefix, isDraft;
  private Text prefix, text;
  private String textValue;
  private TextEntity[] textEntities;
  private IntList textIconIds;
  private int textLeft, textIconsPadding;
  private ContentPreview currentPreview;

  public TGForumTopic (ViewController<?> context, long chatId, TdApi.ForumTopic topic) {
    this.context = context;
    this.tdlib = context.tdlib();
    this.chatId = chatId;
    this.topic = topic;
    this.listMode = Settings.instance().getChatListMode();
    this.counter = new Counter.Builder().callback(this).build();
    this.mentionCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_at_16, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    Counter.Builder reactions = new Counter.Builder()
      .drawable(R.drawable.baseline_favorite_14, 16f, 0f, Gravity.CENTER)
      .callback(this);
    if (XgramExpressive.isEnabled()) {
      reactions.colorSet(XGRAM_REACTIONS_COLORS); // XGram M3E: a tertiary container badge, muted or not
    }
    this.reactionsCounter = reactions.build();
    buildIcon(topic.info);
    this.statusHelper = new TdlibStatusManager.Helper(context.context(), tdlib, this, context);
    setCounter(false);
  }

  private static final TextColorSet XGRAM_REACTIONS_COLORS = new TextColorSet() {
    @Override
    public int defaultTextColor () {
      return XgramExpressive.color(XgramExpressive.ON_TERTIARY_CONTAINER);
    }

    @Override
    public int backgroundColor (boolean isPressed) {
      return XgramExpressive.color(XgramExpressive.TERTIARY_CONTAINER);
    }
  };

  private void buildIcon (TdApi.ForumTopicInfo info) {
    iconDrawable = new ForumTopicIconDrawable(info);
    iconName = info.name;
    iconColor = info.icon != null ? info.icon.color : 0;
    iconCustomEmojiId = info.icon != null ? info.icon.customEmojiId : 0;
    iconIsGeneral = info.isGeneral;
  }

  private boolean iconMatches (TdApi.ForumTopicInfo info) {
    return StringUtils.equalsOrBothEmpty(iconName, info.name) &&
      iconColor == (info.icon != null ? info.icon.color : 0) &&
      iconCustomEmojiId == (info.icon != null ? info.icon.customEmojiId : 0) &&
      iconIsGeneral == info.isGeneral;
  }

  public long getChatId () {
    return chatId;
  }

  public int getForumTopicId () {
    return topic.info.forumTopicId;
  }

  public TdApi.ForumTopic getTopic () {
    return topic;
  }

  /** Replaces the topic data; the list keeps rows for the same topic id. */
  public void setTopic (TdApi.ForumTopic topic) {
    this.topic = topic;
    if (!iconMatches(topic.info)) {
      buildIcon(topic.info);
    }
    setCounter(needAnimateChanges());
    if (currentWidth > 0) {
      int width = currentWidth;
      currentWidth = 0;
      checkLayout(width);
    }
    currentViews.invalidate();
  }

  public ForumTopicIconDrawable getIconDrawable () {
    return iconDrawable;
  }

  public long getIconCustomEmojiId () {
    return topic.info.icon != null && !topic.info.isGeneral ? topic.info.icon.customEmojiId : 0;
  }

  // Views

  public void attachToView (View view) {
    if (currentViews.attachToView(view)) {
      setViewAttached(currentViews.hasAnyTargetToInvalidate());
    }
  }

  public void detachFromView (View view) {
    if (currentViews.detachFromView(view)) {
      setViewAttached(currentViews.hasAnyTargetToInvalidate());
    }
  }

  // "Alice is typing…" in the topic row, like chats (only while a row shows the topic).

  private final TdlibStatusManager.Helper statusHelper;
  private boolean isViewAttached;

  private void setViewAttached (boolean isAttached) {
    if (this.isViewAttached != isAttached) {
      this.isViewAttached = isAttached;
      if (isAttached) {
        statusHelper.attachToChat(chatId, new TdApi.MessageTopicForum(topic.info.forumTopicId));
      } else {
        statusHelper.detachFromAnyChat();
      }
    }
  }

  public TdlibStatusManager.Helper statusHelper () {
    return statusHelper;
  }

  @Override
  public void layoutChatAction () {
    if (listMode == Settings.CHAT_MODE_1LINE) {
      // One line draws only the action's icon, in the time's place (ForumTopicView); drawingState works without a text
      statusHelper.setDrawingText(null);
      return;
    }
    String chatActionText = statusHelper.fullText();
    Text trimmed = null;
    if (!StringUtils.isEmpty(chatActionText)) {
      int avail = currentWidth - ChatView.getLeftPadding(listMode) - ForumTopicView.getRightPaddingPx() - getCounterAddWidth();
      int iconWidth = statusHelper.actionIconWidth();
      if (avail > 0) {
        Text.Builder b = new Text.Builder(chatActionText, avail, textStyleProvider(listMode), TextColorSets.Regular.LIGHT).maxLineCount(listMode == Settings.CHAT_MODE_2LINE ? 1 : 2);
        if (iconWidth > 0) {
          b.lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? iconWidth : 0);
        }
        trimmed = b.build();
      }
    }
    statusHelper.setDrawingText(trimmed);
  }

  @Override
  public void invalidateTypingPart (boolean onlyIcon) {
    currentViews.invalidate();
  }

  @Override
  public boolean canLoop () {
    return isViewAttached;
  }

  @Override
  public boolean canAnimate () {
    return isViewAttached && currentViews.hasAnyTargetToInvalidate() && context.getAttachState();
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (sizeChanged && currentWidth > 0) {
      // As TGChat.layoutContent: with three lines the sender's line ends at the badges too (setPrefix lays the text
      // out after it); in one line layoutText lays the title out again when the badges' room changed
      if (listMode == Settings.CHAT_MODE_2LINE || listMode == Settings.CHAT_MODE_1LINE) {
        layoutText();
      } else {
        setPrefix();
      }
    }
    currentViews.invalidate();
  }

  @Override
  public boolean needAnimateChanges (Counter counter) {
    return needAnimateChanges();
  }

  private boolean needAnimateChanges () {
    View view = currentViews.findAnyTarget();
    return view != null && context.isAttachedToNavigationController();
  }

  // State

  public boolean isMuted () {
    TdApi.ChatNotificationSettings settings = topic.notificationSettings;
    if (settings == null || settings.useDefaultMuteFor) {
      return !tdlib.chatNotificationsEnabled(chatId);
    }
    return settings.muteFor > 0;
  }

  public boolean isClosed () {
    return topic.info.isClosed;
  }

  public boolean isPinned () {
    return topic.isPinned;
  }

  private boolean showDraft () {
    return topic.draftMessage != null && topic.draftMessage.content instanceof TdApi.DraftMessageContentText &&
      !Td.isEmpty(((TdApi.DraftMessageContentText) topic.draftMessage.content).text);
  }

  public boolean isOutgoing () {
    return !showDraft() && topic.lastMessage != null && topic.lastMessage.isOutgoing;
  }

  /** Outgoing last message that the other side hasn't read yet. */
  public boolean isUnread () {
    return topic.lastMessage != null && topic.lastMessage.id > topic.lastReadOutboxMessageId;
  }

  public boolean isSending () {
    return topic.lastMessage != null && tdlib.messageSending(topic.lastMessage);
  }

  private void setCounter (boolean animated) {
    boolean muted = isMuted();
    boolean hasMentions = topic.unreadMentionCount > 0;
    reactionsCounter.setCount(topic.unreadReactionCount > 0 ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, muted, animated);
    mentionCounter.setCount(hasMentions ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, false, animated);
    counter.setCount(hasMentions && topic.unreadCount == 1 ? 0 : topic.unreadCount, muted, animated);
  }

  public Counter getCounter () {
    return counter;
  }

  public Counter getMentionCounter () {
    return mentionCounter;
  }

  public Counter getReactionsCounter () {
    return reactionsCounter;
  }

  // Layout

  public boolean checkLayout (int width) {
    if (width > 0 && currentWidth != width) {
      currentWidth = width;
      setTime();
      layoutTitle();
      setText();
      layoutChatAction();
      return true;
    }
    return false;
  }

  public int getListMode () {
    return listMode;
  }

  /**
   * Follows the chat list view (Settings.setChatListMode). The next measure lays the row out again; while the view
   * springs (XgramChatListMode) a row on screen keeps its old lines to fade them out. True when the mode changed.
   */
  public boolean checkListMode () {
    final int mode = Settings.instance().getChatListMode();
    if (listMode == mode) {
      return false;
    }
    if (currentWidth > 0 && isViewAttached && com.fahadali.xgram.chats.XgramChatListMode.isAnimating()) {
      // A change interrupting another: the lines still fading out keep fading from where they are; otherwise the
      // lines on screen now (the interrupted target's, at the alpha they had reached; all of them for a fresh change),
      // as TGChat.checkChatListMode
      final float f = com.fahadali.xgram.chats.XgramChatListMode.interruptedFraction();
      final float oldStashAlpha = fadeMode != 0 ? fadeStartAlpha * Math.max(0f, Math.min(1f, 1f - 2f * f)) : 0f;
      if (oldStashAlpha > 0f) {
        fadeStartAlpha = oldStashAlpha;
      } else {
        stashLines();
        fadeStartAlpha = Math.max(0f, Math.min(1f, 2f * f - 1f));
      }
    } else {
      dropStash();
    }
    listMode = mode;
    currentWidth = 0;
    currentViews.requestLayout();
    currentViews.invalidate();
    return true;
  }

  // The old mode's lines, fading out while the chat list view changes (as TGChat's)

  private int fadeMode;
  @Nullable private Text fadePrefix, fadeText;
  private int fadeTextLeft;
  @Nullable private IntList fadeIcons;
  private float fadeStartAlpha = 1f;

  private void stashLines () {
    fadeMode = listMode;
    fadePrefix = prefix;
    fadeText = text;
    fadeTextLeft = textLeft;
    if (textIconIds != null && textIconIds.size() > 0) {
      if (fadeIcons == null) {
        fadeIcons = new IntList(textIconIds.size());
      } else {
        fadeIcons.clear();
      }
      for (int i = 0; i < textIconIds.size(); i++) {
        fadeIcons.append(textIconIds.get(i));
      }
    } else if (fadeIcons != null) {
      fadeIcons.clear();
    }
    if (fadePrefix == null && fadeText == null) {
      dropStash(); // one line: nothing under the title to fade out
    }
  }

  private void dropStash () {
    fadeMode = 0;
    fadePrefix = fadeText = null;
    if (fadeIcons != null) {
      fadeIcons.clear();
    }
  }

  /** The view change ended: the old lines go (the views ask for the new text's media again, ForumTopicsController). */
  public void endModeTransition () {
    if (fadeMode != 0) {
      dropStash();
      currentViews.invalidate();
    }
  }

  /** The mode whose lines fade out while the view changes, or 0. */
  public int getFadeMode () {
    return fadeMode;
  }

  @Nullable
  public Text getFadePrefix () {
    return fadeMode != 0 ? fadePrefix : null;
  }

  @Nullable
  public Text getFadeText () {
    return fadeMode != 0 ? fadeText : null;
  }

  public int getFadeTextLeft () {
    return fadeTextLeft;
  }

  @Nullable
  public IntList getFadeIcons () {
    return fadeMode != 0 && fadeIcons != null && fadeIcons.size() > 0 ? fadeIcons : null;
  }

  /** The stashed lines' alpha when the change began: below 1 after an interrupted one (as TGChat.xgramFadeStartAlpha). */
  public float getFadeStartAlpha () {
    return fadeStartAlpha;
  }

  /** Has unread messages: with Expressive design the title, time and preview are emphasized then (as chats). */
  public boolean hasUnread () {
    return topic.unreadCount > 0;
  }

  private TextStyleProvider titleStyleProvider () {
    if (XgramExpressive.isEnabled()) {
      // XGram M3E: the chat list's title style, Google Sans Flex titleMedium, weight 700 while unread
      return XgramM3Profile.flexProvider(XgramExpressive.TS_TITLE_M, hasUnread(), 16f);
    }
    // The chat list's (TGChat.getTitleStyleProvider): 16 with three lines and in one line, 17 otherwise
    return Paints.robotoStyleProvider(listMode == Settings.CHAT_MODE_3LINE || listMode == Settings.CHAT_MODE_1LINE ? 16 : 17);
  }

  /** The chat list's (TGChat.getTextStyleProvider): 15 with three lines, 16 otherwise. */
  private static TextStyleProvider textStyleProvider (int listMode) {
    return Paints.robotoStyleProvider(listMode == Settings.CHAT_MODE_3LINE ? 15 : 16);
  }

  /** What the time keeps at the end: in one line at least 24dp, where the typing icon takes its place, so nothing reflows. */
  private int timeReserve () {
    return listMode == Settings.CHAT_MODE_1LINE ? Math.max(timeWidth, Screen.dp(24f)) : timeWidth;
  }

  private void setTime () {
    int date = showDraft() ? topic.draftMessage.date : topic.lastMessage != null ? topic.lastMessage.date : topic.info.creationDate;
    time = date != 0 ? Lang.timeOrDateShort(date, TimeUnit.SECONDS) : "";
    timeWidth = (int) U.measureText(time, ForumTopicView.getTimePaint());
    timeLeft = currentWidth - ForumTopicView.getRightPaddingPx() - timeWidth;
    final int trailingStart = currentWidth - ForumTopicView.getRightPaddingPx() - timeReserve();
    checksRight = trailingStart - Screen.dp(7f);
    lockLeft = trailingStart - Screen.dp(4f) - Screen.dp(16f);
  }

  // One line: the title leaves room at its end for the badges at their target width (so an animating badge never
  // builds the title again per frame), or for the pin of a pinned topic when there is no badge.

  private int oneLineReserve = -1;

  private int computeOneLineReserve () {
    final int gap = Screen.dp(7f);
    final float badges = counter.getScaledOrTargetWidth(gap, true) + mentionCounter.getScaledOrTargetWidth(gap, true) + reactionsCounter.getScaledOrTargetWidth(gap, true);
    if (badges > 0f) {
      return Math.round(badges) + Screen.dp(8f);
    }
    return isPinned() ? Screen.dp(16f) + Screen.dp(8f) : 0;
  }

  private void checkOneLineReserve () {
    if (listMode == Settings.CHAT_MODE_1LINE && currentWidth > 0 && computeOneLineReserve() != oneLineReserve) {
      layoutTitle();
    }
  }

  private void layoutTitle () {
    final int left = ChatView.getLeftPadding(listMode);
    int avail = currentWidth - left - timeReserve() - ForumTopicView.getRightPaddingPx() - Screen.dp(7f);
    if (isClosed()) {
      avail -= Screen.dp(20f);
    }
    if (isOutgoing() || isSending()) {
      avail -= Screen.dp(20f);
    }
    if (isMuted()) {
      avail -= Screen.dp(1f) + Icons.getChatMuteDrawableWidth();
    }
    if (listMode == Settings.CHAT_MODE_1LINE) {
      oneLineReserve = computeOneLineReserve();
      avail -= oneLineReserve;
    }
    String name = StringUtils.isEmpty(topic.info.name) && topic.info.isGeneral ? Lang.getString(R.string.XgramGeneralTopic) : topic.info.name;
    if (avail > 0 && !StringUtils.isEmpty(name)) {
      title = new Text.Builder(name, avail, titleStyleProvider(), TextColorSets.Regular.NORMAL)
        .singleLine()
        .allBold()
        .clipTextArea()
        .build();
    } else {
      title = null;
    }
    muteLeft = left + (title != null ? title.getWidth() : 0) + Screen.dp(1f);
  }

  private void setText () {
    hasPrefix = false;
    isDraft = false;
    currentPreview = null;
    if (textIconIds != null) {
      textIconIds.clear();
    }
    if (showDraft()) {
      isDraft = true;
      hasPrefix = true;
      TdApi.FormattedText draft = ((TdApi.DraftMessageContentText) topic.draftMessage.content).text;
      setTextValue(draft.text, TextEntity.valueOf(tdlib, draft.text, draft.entities, null));
      setPrefix();
      return;
    }
    TdApi.Message msg = topic.lastMessage;
    if (msg == null) {
      setTextValue(Lang.getString(R.string.XgramTopicNoMessages), null);
      setPrefix();
      return;
    }
    setContentPreview(ContentPreview.getChatListPreview(tdlib, chatId, msg, false));
  }

  private void setContentPreview (ContentPreview preview) {
    currentPreview = preview;
    if (textIconIds != null) {
      textIconIds.clear();
    }
    String value = preview.buildText(true);
    setTextValue(value, preview.formattedText != null ? TextEntity.valueOf(tdlib, value, preview.formattedText.entities, null) : null);
    if (preview.parentEmoji != null) {
      addIcon(preview.parentEmoji.iconRepresentation);
    }
    if (preview.emoji != null) {
      addIcon(preview.emoji.iconRepresentation);
    }
    hasPrefix = !preview.hideAuthor && topic.lastMessage != null && !Td.isProximityAlertTriggered(topic.lastMessage.content);
    setPrefix();
    if (preview.hasRefresher()) {
      preview.refreshContent(this);
    }
  }

  @Override
  public void onContentPreviewChanged (long chatId, long messageId, ContentPreview newPreview, ContentPreview oldPreview) {
    tdlib.ui().post(() -> {
      if (currentPreview == oldPreview) {
        setContentPreview(newPreview);
        currentViews.invalidate();
      }
    });
  }

  private void addIcon (int icon) {
    if (icon != 0) {
      if (textIconIds == null) {
        textIconIds = new IntList(2);
      }
      textIconIds.append(icon);
    }
  }

  private void setTextValue (String value, @Nullable TextEntity[] entities) {
    this.textValue = value;
    this.textEntities = entities != null && entities.length > 0 ? entities : null;
  }

  private void setPrefix () {
    String prefixValue = null;
    final boolean twoLines = listMode == Settings.CHAT_MODE_2LINE;
    if (hasPrefix && listMode != Settings.CHAT_MODE_1LINE) { // one line shows no sender
      if (isDraft) {
        prefixValue = Lang.getString(R.string.Draft);
      } else if (topic.lastMessage != null) {
        // As the chat list: the short name in front of the text with two lines, the full name on its own line with three
        prefixValue = topic.lastMessage.isOutgoing ? Lang.getString(R.string.FromYou) : tdlib.senderName(topic.lastMessage, false, twoLines);
      }
    }
    final int avail = twoLines ? Screen.dp(120f) : currentWidth - ChatView.getLeftPadding(listMode) - ForumTopicView.getRightPaddingPx() - getCounterAddWidth();
    if (!StringUtils.isEmpty(prefixValue) && avail > 0) {
      Text.Builder b = new Text.Builder(prefixValue, avail, textStyleProvider(listMode), isDraft ? TextColorSets.Regular.NEGATIVE : TextColorSets.Regular.NORMAL)
        .singleLine()
        .textFlags(Text.FLAG_ELLIPSIZE_NO_FILL);
      if (twoLines) {
        b.suffix(": ");
      }
      prefix = b.build();
    } else {
      prefix = null;
    }
    layoutText();
  }

  private int getCounterAddWidth () {
    int padding = Screen.dp(7f);
    return Math.round(
      counter.getScaledWidth(padding) +
      mentionCounter.getScaledWidth(padding) +
      reactionsCounter.getScaledWidth(padding)
    );
  }

  private void layoutText () {
    textLeft = ChatView.getLeftPadding(listMode);
    if (listMode == Settings.CHAT_MODE_1LINE) {
      // One line shows no preview: setText still keeps the preview (its refresher), only no Text is built
      textIconsPadding = 0;
      text = null;
      layoutChatAction();
      checkOneLineReserve();
      return;
    }
    int avail = currentWidth - textLeft - ForumTopicView.getRightPaddingPx() - getCounterAddWidth();
    if (prefix != null && listMode == Settings.CHAT_MODE_2LINE) {
      // Two lines: "Sender: text" on one line; three lines: the sender on its own line over the text
      avail -= prefix.getWidth();
      textLeft += prefix.getWidth();
    }
    // One line of text under the sender, up to two without one (three lines), as the chat list's
    final boolean singleLine = listMode == Settings.CHAT_MODE_2LINE || prefix != null;
    textIconsPadding = textIconIds != null && textIconIds.size() > 0 ? Screen.dp(2f) + Screen.dp(18f) * textIconIds.size() : 0;
    if (avail > 0 && !StringUtils.isEmpty(textValue)) {
      boolean xgramUnread = XgramExpressive.isEnabled() && hasUnread(); // XGram M3E: unread previews in onSurface
      text = new Text.Builder(textValue, avail, textStyleProvider(listMode), xgramUnread ? TextColorSets.Regular.NORMAL : TextColorSets.Regular.LIGHT)
        .maxLineCount(singleLine ? 1 : 2)
        .textFlags(Text.FLAG_ELLIPSIZE_NEWLINE)
        .ignoreContinuousNewLines()
        .ignoreNewLines(singleLine)
        .lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? textIconsPadding : 0)
        .viewProvider(currentViews)
        .entities(textEntities, (text, specificMedia) -> {
          if (this.text == text) {
            for (View view : currentViews) {
              ComplexReceiver receiver = ((ForumTopicView) view).getTextMediaReceiver();
              if (!text.invalidateMediaContent(receiver, specificMedia)) {
                text.requestMedia(receiver);
              }
            }
          }
        })
        .noClickable()
        .build();
      currentViews.invalidateContent(this);
    } else {
      text = null;
    }
    layoutChatAction();
  }

  // Getters for drawing

  @Nullable
  public Text getTitle () {
    return title;
  }

  public String getTime () {
    return time;
  }

  public int getTimeLeft () {
    return timeLeft;
  }

  public int getTimeWidth () {
    return timeWidth;
  }

  public int getChecksRight () {
    return checksRight;
  }

  public int getMuteLeft () {
    return muteLeft;
  }

  public int getLockLeft () {
    return lockLeft;
  }

  @Nullable
  public Text getPrefix () {
    return prefix;
  }

  @Nullable
  public Text getText () {
    return text;
  }

  public int getTextLeft () {
    return textLeft;
  }

  @Nullable
  public IntList getTextIconIds () {
    return textIconIds;
  }
}
