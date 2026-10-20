/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/04/2015 at 18:40
 */
package org.thunderdog.challegram.data;

import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ReactionLoadListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.ReactionsCounterDrawable;
import org.thunderdog.challegram.util.ReactionsListAnimator;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.v.MessagesRecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BounceAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.reference.ReferenceList;
import tgx.td.ChatId;
import tgx.td.ChatPosition;
import tgx.td.Td;
import tgx.td.TdExt;

public class TGChat implements TdlibStatusManager.HelperTarget, ContentPreview.RefreshCallback, Counter.Callback, ReactionLoadListener, Destroyable, TdlibUi.MessageProvider {
  private static final int FLAG_HAS_PREFIX = 1;
  private static final int FLAG_TEXT_DRAFT = 1 << 4;
  private static final int FLAG_SHOW_VERIFY = 1 << 5;
  private static final int FLAG_SELF_CHAT = 1 << 7;
  private static final int FLAG_ATTACHED = 1 << 8;
  private static final int FLAG_CONTENT_HIDDEN = 1 << 9;
  private static final int FLAG_CONTENT_STRING = 1 << 10;
  private static final int FLAG_ONLINE = 1 << 12;
  private static final int FLAG_ARCHIVE = 1 << 14;
  private static final int FLAG_SHOW_SCAM = 1 << 15;
  private static final int FLAG_SHOW_FAKE = 1 << 16;
  private static final int FLAG_MESSAGE = 1 << 17;

  private static final int ARCHIVE_PREVIEW_LIMIT = 9;

  private int flags, listMode;

  private final ViewController<?> context;
  private final Tdlib tdlib;
  @Nullable
  private final TdApi.Chat chat;
  private final TdApi.ChatList chatList;
  @Nullable
  private final TdlibChatList archive;
  private long dataId;
  private int dataType;

  private int currentWidth;

  private AvatarPlaceholder.Metadata avatarPlaceholder;

  private String title;
  private Text trimmedTitle;

  private @Nullable EmojiStatusHelper.EmojiStatusDrawable emojiStatusDrawable;

  private String time;
  private int timeWidth;

  private Text prefix;

  private String text;
  private TextEntity[] entities;
  private Text trimmedText;

  private ContentPreview currentPreview;
  private IntList textIconIds;
  private @PorterDuffColorId int textIconColorId;

  private int textLeft;
  private int timeLeft;
  private int muteLeft, verifyLeft;
  private int emojiStatusLeft;
  private int checkRight;
  private int reactionsRight;

  private Text chatMark;

  private final MultipleViewProvider currentViews = new MultipleViewProvider();

  private final BounceAnimator scheduleAnimator;
  private final Counter counter, mentionCounter, reactionsCounter, viewCounter;
  private final Counter xgramLaterCounter;
  private final com.fahadali.xgram.later.XgramLaterUi.BellColors xgramBellColors;

  private final ReactionsCounterDrawable reactionsCounterDrawable;
  private final HashMap<String, TGReactions.MessageReactionEntry> reactionsMapEntry = new HashMap<>();
  private final ReactionsListAnimator reactionsAnimator;
  private final ArrayList<TGReactions.MessageReactionEntry> reactionsListEntry = new ArrayList<>();
  private Set<String> awaitingReactions;

  public TGChat (ViewController<?> context, TdApi.ChatList chatList, TdApi.Chat chat, boolean makeMeasures) {
    this.context = context;
    this.statusHelper = new TdlibStatusManager.Helper(context.context(), context.tdlib(), this, context);
    this.tdlib = context.tdlib();
    this.chatList = chatList;
    this.chat = Td.copyOf(chat);
    com.fahadali.xgram.topics.XgramForumUnread.requestIfNeeded(tdlib, chat); // XGram: badge of groups with topics
    com.fahadali.xgram.chats.XgramCommunities.onLastMessage(tdlib, chat.lastMessage); // XGram: which community it's in
    this.xgramCommunityId = xgramComputeCommunity();
    this.archive = null;
    this.listMode = Settings.instance().getChatListMode();
    this.dataType = chat.type.getConstructor();
    switch (dataType) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        this.dataId = ((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId;
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        this.dataId = ((TdApi.ChatTypeSupergroup) chat.type).supergroupId;
        break;
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        this.dataId = TD.getUserId(chat.type);
        break;
      }
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        this.dataId = ((TdApi.ChatTypePrivate) chat.type).userId;
        if (dataId != 0 && tdlib.isSelfUserId(dataId)) {
          flags |= FLAG_SELF_CHAT;
        }
        break;
      }
    }
    this.scheduleAnimator = new BounceAnimator(currentViews);
    this.reactionsAnimator = new ReactionsListAnimator(
      (a) -> currentViews.invalidate(),
      AnimatorUtils.DECELERATE_INTERPOLATOR,
      MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 50L);
    this.reactionsCounterDrawable = new ReactionsCounterDrawable(reactionsAnimator);
    this.counter = new Counter.Builder().callback(this).build();
    this.mentionCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_at_16, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    this.reactionsCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_favorite_14, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    // XGram Later: the bell (a woken snooze, a due reminder or follow-up not seen yet), after the @ and the heart
    this.xgramBellColors = new com.fahadali.xgram.later.XgramLaterUi.BellColors();
    this.xgramLaterCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_notification_important_14, 16f, 0f, Gravity.CENTER)
      .colorSet(xgramBellColors)
      .callback(this)
      .build();
    xgramBellColors.attach(xgramLaterCounter);
    this.viewCounter = new Counter.Builder()
      .textSize(11f)
      .callback(this)
      .noBackground()
      .allBold(false)
      .textColor(ColorId.ticksRead)
      .drawable(R.drawable.baseline_visibility_14, 14f, 3f, Gravity.RIGHT)
      .build();
    setCounter(false);
    setReactions(false);
    setViews();
    this.tdlib.singleUnreadReactionsManager().checkChat(chat);
    this.scheduleAnimator.setValue(hasScheduledMessages(), false);
    checkOnline();
    if (makeMeasures) {
      buildLayout(Screen.currentWidth());
    }
  }

  public TGChat (ViewController<?> context, TdlibChatList list, boolean makeMeasures) {
    this.context = context;
    this.statusHelper = null;
    this.tdlib = context.tdlib();
    this.chat = null;
    this.chatList = null;
    this.archive = list;
    this.listMode = Settings.instance().getChatListMode();
    this.scheduleAnimator = new BounceAnimator(currentViews);
    this.reactionsAnimator = new ReactionsListAnimator(
      (a) -> currentViews.invalidate(),
      AnimatorUtils.DECELERATE_INTERPOLATOR,
      MessagesRecyclerView.ITEM_ANIMATOR_DURATION + 50L);
    this.reactionsCounterDrawable = new ReactionsCounterDrawable(reactionsAnimator);
    this.counter = new Counter.Builder().callback(this).build();
    this.mentionCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_at_16, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    this.reactionsCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_favorite_14, 16f, 0f, Gravity.CENTER)
      .callback(this)
      .build();
    // XGram Later: the bell (a woken snooze, a due reminder or follow-up not seen yet), after the @ and the heart
    this.xgramBellColors = new com.fahadali.xgram.later.XgramLaterUi.BellColors();
    this.xgramLaterCounter = new Counter.Builder()
      .drawable(R.drawable.baseline_notification_important_14, 16f, 0f, Gravity.CENTER)
      .colorSet(xgramBellColors)
      .callback(this)
      .build();
    xgramBellColors.attach(xgramLaterCounter);
    this.viewCounter = null;
    this.flags |= FLAG_ARCHIVE;
    setCounter(false);
    this.scheduleAnimator.setValue(hasScheduledMessages(), false);
    if (makeMeasures) {
      buildLayout(Screen.currentWidth());
    }
  }

  private int lastCounterAddWidth;

  private void layoutContent () {
    if (listMode == Settings.CHAT_MODE_2LINE || listMode == Settings.CHAT_MODE_1LINE) { // XGram: one line has no text to lay out
      layoutText();
    } else {
      setPrefix();
    }
  }

  public void onAttachToView () {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.onAppear();
    }
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (sizeChanged) {
      if (counter == viewCounter) {
        layoutTitle(false);
      } else {
        int newWidth = getCounterAddWidth();
        if (lastCounterAddWidth != newWidth) {
          layoutContent();
        }
        xgramCheckOneLineReserve(); // XGram: one line's title makes room for the badges (their target width, once)
      }
    }
    currentViews.invalidate();
  }

  @Override
  public boolean needAnimateChanges (Counter counter) {
    return needAnimateChanges();
  }

  public void onArchiveCounterChanged () {
    setCounter(true);
    scheduleAnimator.setValue(hasScheduledMessages(), true);
  }

  public void onArchiveMessageChanged () {
    setTime();
  }

  public void onArchiveChanged () {
    setCounter(true);
    setTime();
    setText();
  }

  public int getListMode () {
    return listMode;
  }

  public void checkChatListMode () {
    int newMode = xgramForcedMode != 0 ? xgramForcedMode : Settings.instance().getChatListMode(); // XGram: the community page sets its own
    if (listMode != newMode) {
      // XGram: while the chat list view springs to the new mode, a row on screen keeps its old lines to fade them out
      if (xgramForcedMode == 0 && currentWidth > 0 && (flags & FLAG_ATTACHED) != 0 && com.fahadali.xgram.chats.XgramChatListMode.isAnimating()) {
        // A change interrupting another: the lines still fading out keep fading from where they are; otherwise the
        // lines on screen now (the interrupted target's, at the alpha they had reached; all of them for a fresh change)
        final float f = com.fahadali.xgram.chats.XgramChatListMode.interruptedFraction();
        final float oldStashAlpha = xgramFadeMode != 0 ? xgramFadeStartAlpha * Math.max(0f, Math.min(1f, 1f - 2f * f)) : 0f;
        if (oldStashAlpha > 0f) {
          xgramFadeStartAlpha = oldStashAlpha;
        } else {
          xgramStashLines();
          xgramFadeStartAlpha = Math.max(0f, Math.min(1f, 2f * f - 1f));
        }
      } else {
        xgramDropStash();
      }
      listMode = newMode;
      currentWidth = 0;
      if (avatarPlaceholder != null) {
        setAvatar();
      }
      currentViews.requestLayout();
      currentViews.invalidate();
    }
  }

  // XGram: one line chat list view (Settings.CHAT_MODE_1LINE) and the spring between views (XgramChatListMode).

  /** Follows the chat list's view: false for a row with a mode of its own (the community page). */
  public boolean followsGlobalMode () {
    return xgramForcedMode == 0;
  }

  /** A pinned chat (not the archive): one line marks it with a pin where there is no badge. */
  public boolean xgramIsPinnedRow () {
    return !isArchive() && chat != null && chatList != null && isPinned();
  }

  private int xgramFadeMode;
  @Nullable private Text xgramFadePrefix, xgramFadeText;
  private int xgramFadeTextLeft;
  @Nullable private IntList xgramFadeIcons;
  private @PorterDuffColorId int xgramFadeIconColorId;
  private float xgramFadeStartAlpha = 1f;

  private void xgramStashLines () {
    xgramFadeMode = listMode;
    xgramFadePrefix = getPrefix();
    xgramFadeText = trimmedText;
    xgramFadeTextLeft = textLeft;
    xgramFadeIconColorId = textIconColorId;
    if (textIconIds != null && textIconIds.size() > 0) {
      if (xgramFadeIcons == null) {
        xgramFadeIcons = new IntList(textIconIds.size());
      } else {
        xgramFadeIcons.clear();
      }
      for (int i = 0; i < textIconIds.size(); i++) {
        xgramFadeIcons.append(textIconIds.get(i));
      }
    } else if (xgramFadeIcons != null) {
      xgramFadeIcons.clear();
    }
    if (xgramFadePrefix == null && xgramFadeText == null) {
      xgramDropStash(); // one line: nothing under the title to fade out
    }
  }

  private void xgramDropStash () {
    xgramFadeMode = 0;
    xgramFadePrefix = xgramFadeText = null;
    if (xgramFadeIcons != null) {
      xgramFadeIcons.clear();
    }
  }

  /** The mode whose lines fade out while the view changes, or 0. */
  public int xgramFadeMode () {
    return xgramFadeMode;
  }

  public @Nullable Text xgramFadePrefix () {
    return xgramFadeMode != 0 ? xgramFadePrefix : null;
  }

  public @Nullable Text xgramFadeText () {
    return xgramFadeMode != 0 ? xgramFadeText : null;
  }

  public int xgramFadeTextLeft () {
    return xgramFadeTextLeft;
  }

  public @Nullable IntList xgramFadeIcons () {
    return xgramFadeMode != 0 && xgramFadeIcons != null && xgramFadeIcons.size() > 0 ? xgramFadeIcons : null;
  }

  public @PorterDuffColorId int xgramFadeIconColorId () {
    return xgramFadeIconColorId;
  }

  /** The stashed lines' alpha when the change began: below 1 after an interrupted one. */
  public float xgramFadeStartAlpha () {
    return xgramFadeStartAlpha;
  }

  /** The view change ended: the old lines go, and the views ask for the new text's media (custom emoji) again. */
  public void xgramEndModeTransition () {
    if (xgramFadeMode != 0) {
      xgramDropStash();
      currentViews.invalidateContent(this);
      currentViews.invalidate();
    }
  }

  /** The style of one line's title (the row centres it with this style's line height). */
  public static TextStyleProvider xgramOneLineTitleProvider () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, false, 16f);
    }
    return getTitleStyleProvider(Settings.CHAT_MODE_1LINE);
  }

  private int xgramOneLineReserve = -1;

  /**
   * What one line's title leaves at its end for the trailing cluster beyond what layoutTitle subtracts for every mode:
   * the draft pencil, the badges at their target width (so an animating badge never rebuilds the title per frame), or
   * the pin when there is no badge.
   */
  private int xgramComputeOneLineReserve () {
    int reserve = 0;
    if (showDraft()) {
      reserve += Screen.dp(12f) + ChatView.getTimePaddingLeft();
    }
    final int gap = ChatView.getTimePaddingLeft();
    final float badges = counter.getScaledOrTargetWidth(gap, true) + mentionCounter.getScaledOrTargetWidth(gap, true) + reactionsCounter.getScaledOrTargetWidth(gap, true) +
      xgramLaterCounter.getScaledOrTargetWidth(gap, true); // XGram Later
    if (badges > 0f) {
      reserve += Math.round(badges) + Screen.dp(8f);
    } else if (xgramIsPinnedRow()) {
      reserve += Screen.dp(14f) + Screen.dp(8f);
    }
    return reserve;
  }

  /** One line: lays the title out again when what it has to leave for the badges, pin or draft changed. */
  private void xgramCheckOneLineReserve () {
    if (listMode == Settings.CHAT_MODE_1LINE && currentWidth > 0 && title != null && xgramComputeOneLineReserve() != xgramOneLineReserve) {
      layoutTitle(false);
    }
  }

  public boolean needRefreshInteractionInfo () {
    return chat != null && chat.lastMessage != null && isChannel() && showViews();
  }

  public boolean checkOnline () {
    boolean isOnline = !isArchive() && ChatId.isUserChat(chat.id) && tdlib.status().isOnline(TD.getUserId(chat));
    int newFlags = BitwiseUtils.setFlag(this.flags, FLAG_ONLINE, isOnline);
    if (this.flags != newFlags) {
      this.flags = newFlags;
      return true;
    }
    return false;
  }

  public void makeMeasures () {
    buildLayout(Screen.currentWidth());
  }

  private void buildLayout (int width) {
    if (width > 0) {
      currentWidth = width;
      setCounter(true);
      setTime();
      setTitle();
      setText();
      setAvatar();
    }
  }

  public boolean isOnline () {
    return (flags & FLAG_ONLINE) != 0;
  }

  public boolean checkLayout (int width) {
    if (currentWidth == 0) {
      boolean changed = width > 0;
      buildLayout(width);
      return changed;
    } else {
      if (currentWidth != width && width > 0) {
        currentWidth = width;
        layoutTime();
        layoutTitle(false);
        layoutContent();
        return true;
      }
    }
    return false;
  }

  public boolean isArchive () {
    return (flags & FLAG_ARCHIVE) != 0;
  }

  public TdApi.Chat getChat () {
    return tdlib.chat(getChatId());
  }

  public long getChatUserId () {
    return TD.getUserId(this.chat);
  }

  public long getChatId () {
    return chat != null ? chat.id : 0;
  }

  public TdApi.ChatList getChatList () {
    return chatList;
  }

  public long getChatOrder () {
    return isArchive() ? Long.MAX_VALUE : ChatPosition.getOrder(chat, chatList);
  }

  private int getTotalUnreadCount () {
    if (isArchive()) {
      TdlibCounter counter = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      int unreadCount = counter.chatCount;
      int markedAsUnreadCount = counter.markedAsUnreadCount;
      unreadCount -= markedAsUnreadCount;
      return unreadCount > 0 ? unreadCount : markedAsUnreadCount > 0 ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    } else {
      return chat.unreadCount > 0 ? chat.unreadCount : chat.isMarkedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    }
  }

  public long getPrivateId () {
    return dataType == TdApi.ChatTypePrivate.CONSTRUCTOR ? dataId : 0;
  }

  public long getGroupId () {
    return dataType == TdApi.ChatTypeBasicGroup.CONSTRUCTOR ? dataId : 0;
  }

  public long getChannelId () {
    return dataType == TdApi.ChatTypeSupergroup.CONSTRUCTOR ? dataId : 0;
  }

  public int getSecretChatId () {
    return TD.getSecretChatId(chat);
  }

  public boolean updateDraftMessage (long chatId, TdApi.DraftMessage draftMessage) {
    if (getChatId() == chatId) {
      chat.draftMessage = draftMessage;
      setText();
      setTime();
      layoutTitle(false);
      return true;
    }
    return false;
  }

  public boolean updateTopMessage (long chatId, @Nullable TdApi.Message message) {
    if (getChatId() == chatId) {
      TdApi.Message oldMessage = chat.lastMessage;
      chat.lastMessage = message;
      if ((oldMessage == null && message == null) || (oldMessage != null && message != null && oldMessage.id == message.id))
        return false;
      setCounter(true);
      setTime();
      setText();
      layoutTitle(false);
      return true;
    }
    return false;
  }

  public boolean updateChatPermissions (long chatId, TdApi.ChatPermissions permissions) {
    if (getChatId() == chatId) {
      chat.permissions = permissions;
    }
    return false;
  }

  public boolean updateChatClientData (long chatId, String clientData) {
    if (getChatId() == chatId) {
      boolean hadPasscode = tdlib.hasPasscode(chat);
      chat.clientData = clientData;
      boolean hasPasscode = tdlib.hasPasscode(chat);
      if (hadPasscode != hasPasscode) {
        setText();
        return true;
      }
    }
    return false;
  }

  public boolean updateChatPosition (long chatId, TdApi.ChatPosition position, final boolean sourceChanged, final boolean pinStateChanged) {
    if (getChatId() == chatId) {
      TdApi.ChatPosition prevPosition = ChatPosition.findPosition(chat, chatList);
      if (Tdlib.updateChatPosition(chat, position) && Td.equalsTo(chatList, position.list)) {
        if (sourceChanged) {
          setTime();
          setCounter(true);
          if (position.source instanceof TdApi.ChatSourcePublicServiceAnnouncement || (prevPosition != null && prevPosition.source instanceof TdApi.ChatSourcePublicServiceAnnouncement)) {
            setText();
          }
          layoutTitle(false);
        } else if (pinStateChanged) {
          xgramCheckOneLineReserve(); // XGram: one line's pin takes room at the title's end
        }
        return true;
      }
    }
    return false;
  }

  public TdApi.ChatPosition getPosition () {
    return ChatPosition.findPosition(chat, chatList);
  }

  public boolean isPinned () {
    TdApi.ChatPosition position = getPosition();
    return position != null && position.isPinned;
  }

  public boolean isPinnedOrSpecial () {
    TdApi.ChatPosition position = getPosition();
    return position != null && (position.isPinned || position.source != null);
  }

  public TdApi.ChatSource getSource () {
    TdApi.ChatPosition position = getPosition();
    return position != null ? position.source : null;
  }

  public boolean checkLastMessageId (long messageId) {
    if (chat != null && chat.lastMessage != null && chat.lastMessage.id == messageId) {
      return true;
    }
    if (currentPreview != null) {
      Tdlib.Album album = currentPreview.getAlbum();
      if (album != null) {
        for (TdApi.Message message : album.messages) {
          if (message.id == messageId) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean updateMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
    if (checkLastMessageId(oldMessageId)) {
      return updateTopMessage(message.chatId, message);
    }
    return false;
  }

  public boolean updateMessageInteractionInfo (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
    if (getChatId() == chatId && checkLastMessageId(messageId)) {
      chat.lastMessage.interactionInfo = interactionInfo;
      setViews();
      boolean r = setReactions(true);
      return showViews() || r;
    }
    return false;
  }

  public boolean updateMessageContent (long chatId, long messageId, TdApi.MessageContent newContent) {
    if (getChatId() == chatId && chat.lastMessage != null) {
      if (currentPreview != null) {
        Tdlib.Album album = currentPreview.getAlbum();
        if (album != null) {
          boolean updatedAlbum = false;
          for (TdApi.Message message : album.messages) {
            if (message.id == messageId) {
              message.content = newContent;
              updatedAlbum = true;
              break;
            }
          }
          if (updatedAlbum) {
            setContentPreview(ContentPreview.getAlbumPreview(tdlib, chat.lastMessage, album, true));
            return true;
          }
        }
        if (currentPreview.updateRelatedMessage(chatId, messageId, newContent, this)) {
          return true;
        }
      }
      if (chat.lastMessage.id == messageId) {
        chat.lastMessage.content = newContent;
        setText();
        return true;
      }
    }
    return false;
  }

  public boolean updateMessagesDeleted (long chatId, long[] messageIds) {
    if (chat.id == chatId && chat.lastMessage != null) {
      if (ArrayUtils.contains(messageIds, chat.lastMessage.id)) {
        chat.lastMessage = null;
        setText();
        return true;
      }

      if (currentPreview != null) {
        Tdlib.Album album = currentPreview.getAlbum();
        if (album != null) {
          boolean albumChanged = false;
          for (int index = album.messages.size() - 1; index >= 0; index--) {
            if (ArrayUtils.contains(messageIds, album.messages.get(index).id)) {
              album.messages.remove(index);
              albumChanged = true;
            }
          }
          if (album.messages.size() <= 1) {
            setText();
            return true;
          }
          if (albumChanged) {
            setContentPreview(ContentPreview.getAlbumPreview(tdlib, chat.lastMessage, album, true));
          }
        }
        if (currentPreview.belongsToRelatedMessage(chatId, messageIds)) {
          setText();
          return true;
        }
      }
      return true;
    }
    return false;
  }

  public boolean updateChatReadOutbox (long chatId, final long lastReadOutboxMessageId) {
    if (chat.id == chatId) {
      chat.lastReadOutboxMessageId = lastReadOutboxMessageId;
      return chat.lastMessage != null && TD.isOut(chat.lastMessage);
    }
    return false;
  }

  public boolean updateChatReadInbox (long chatId, final long lastReadInboxMessageId, final int unreadCount) {
    if (chat.id == chatId) {
      boolean showDraft = showDraft();
      chat.lastReadInboxMessageId = lastReadInboxMessageId;
      chat.unreadCount = unreadCount;
      boolean newShowDraft = showDraft();
      setCounter(true);
      if (showDraft != newShowDraft) {
        setText();
      }
      return true;
    }
    return false;
  }

  public boolean updateChatUnreadReactionCount (long chatId, int unreadReactionCount) {
    if (chat.id == chatId) {
      boolean hadBadge = chat.unreadReactionCount > 0;
      chat.unreadReactionCount = unreadReactionCount;
      boolean hasBadge = unreadReactionCount > 0;
      if (hasBadge != hadBadge) {
        setCounter(needAnimateChanges());
        return true;
      }
    }
    return false;
  }

  public boolean updateChatUnreadMentionCount (long chatId, int unreadMentionCount) {
    if (chat.id == chatId) {
      boolean hadBadge = chat.unreadMentionCount > 0;
      chat.unreadMentionCount = unreadMentionCount;
      boolean hasBadge = unreadMentionCount > 0;
      if (hasBadge != hadBadge) {
        setCounter(needAnimateChanges());
        return true;
      }
    }
    return false;
  }

  public boolean updateChatHasScheduledMessages (long chatId, boolean hasScheduledMessages) {
    if (chat.id == chatId && chat.hasScheduledMessages != hasScheduledMessages) {
      chat.hasScheduledMessages = hasScheduledMessages;
      scheduleAnimator.setValue(hasScheduledMessages, needAnimateChanges());
      return true;
    }
    return false;
  }

  public boolean updateUser (TdApi.User user) {
    if (chat == null)
      return false;
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        if (getPrivateId() == user.id) {
          setTitle();
          setAvatar();
          return true;
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        if (Td.getSenderUserId(chat.lastMessage) == user.id) {
          setText();
          return true;
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (tdlib.isSupergroupChat(chat) && Td.getSenderId(chat.lastMessage) == user.id) {
          setText();
          return true;
        }
        break;
      }
    }
    return false;
  }

  public boolean updateChatTitle (long chatId, String newTitle) {
    if (getChatId() == chatId) {
      chat.title = newTitle;
      setTitleImpl(xgramCommunityId != 0 ? xgramCommunityTitle() : tdlib.chatTitle(chat)); // XGram: a merged row keeps the community name
      layoutTitle(true);
      if (xgramCommunityId != 0 || (tdlib.isMultiChat(chat) && !ChatId.isUserChat(chatId) && Td.getSenderId(chat.lastMessage) == chatId)) {
        setText();
      }
      return true;
    }
    return false;
  }

  public boolean updateMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    if (getChatId() == chatId) {
      chat.isMarkedAsUnread = isMarkedAsUnread;
      setCounter(true);
      return true;
    }
    return false;
  }

  public boolean updateChatPhoto (long chatId, TdApi.ChatPhotoInfo photo) {
    if (getChatId() == chatId) {
      chat.photo = photo;
      setAvatar();
      return true;
    }
    return false;
  }

  public boolean updateChatSettings (long chatId, final TdApi.ChatNotificationSettings settings) {
    if (getChatId() == chatId) {
      chat.notificationSettings = settings;
      layoutTitle(false);
      setCounter(true);
      return true;
    }
    return false;
  }

  private void setAvatar () {
    if (isArchive()) {
      avatarPlaceholder = new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.ARCHIVE), R.drawable.baseline_archive_24);
    } else {
      avatarPlaceholder = null;
    }
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholder () {
    return avatarPlaceholder;
  }

  public boolean isOutgoing () {
    return !isArchive() && !showDraft() && chat.lastMessage != null && chat.lastMessage.isOutgoing;
  }

  public boolean showDraft () {
    // TODO rich message
    return !isArchive() && chat.unreadCount == 0 && chat.draftMessage != null && chat.draftMessage.content.getConstructor() == TdApi.DraftMessageContentText.CONSTRUCTOR;
  }

  public boolean isUnread () {
    if (!isArchive() && chat.lastMessage != null && !tdlib.isChannelChat(chat)) {
      if (isOutgoing()) {
        return chat.lastMessage.id > chat.lastReadOutboxMessageId;
      } else {
        return chat.lastMessage.id > chat.lastReadInboxMessageId;
      }
    }
    return false;
  }

  public boolean isPrivate () {
    return ChatId.isPrivate(getChatId());
  }

  public boolean isSecretChat () {
    return ChatId.isSecret(getChatId());
  }

  public boolean isGroup () {
    return ChatId.isBasicGroup(getChatId());
  }

  public boolean isChannel () {
    return ChatId.isSupergroup(getChatId()) && !tdlib.isSupergroupChat(chat);
  }

  public boolean showViews () {
    return !isSending() && isOutgoing() && isChannel() && chat.lastMessage != null && TD.getViewCount(chat.lastMessage.interactionInfo) > 0;
  }

  public boolean isSupergroup () {
    return ChatId.isSupergroup(getChatId()) && tdlib.isSupergroupChat(chat);
  }

  public boolean showMute () {
    return tdlib.chatNeedsMuteIcon(chat);
  }

  public boolean showVerify () {
    return (flags & FLAG_SHOW_VERIFY) != 0;
  }

  public boolean showScam () {
    return (flags & FLAG_SHOW_SCAM) != 0;
  }

  public boolean showFake () {
    return (flags & FLAG_SHOW_FAKE) != 0;
  }

  public boolean isSelfChat () {
    return (flags & FLAG_SELF_CHAT) != 0;
  }

  public int getCurrentWidth () {
    return currentWidth;
  }

  private void setCounter (boolean allowAnimation) {
    boolean hasReactions = hasUnreadReactions();
    boolean hasMentions = hasUnreadMentions();
    int unreadCount = getUnreadCount();

    reactionsCounter.setCount(hasReactions ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, !notificationsEnabled(), allowAnimation && needAnimateChanges());
    mentionCounter.setCount(hasMentions ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, false, allowAnimation && needAnimateChanges());
    counter.setCount(hasMentions && unreadCount == 1 ? 0 : unreadCount, !notificationsEnabled(), allowAnimation && needAnimateChanges());
    xgramLaterCounter.setCount(xgramHasBell() ? Tdlib.CHAT_MARKED_AS_UNREAD : 0, !notificationsEnabled(), allowAnimation && needAnimateChanges()); // XGram Later
    xgramCheckUnreadStyle();
    xgramCheckOneLineReserve();
  }

  public boolean needAnimateChanges () {
    View view = currentViews.findAnyTarget();
    ViewController<?> c = ViewController.findRoot(view);
    return view != null && (c == null || c.isAttachedToNavigationController());
  }

  public int getUnreadCount () {
    if (isFailed()) {
      return Tdlib.CHAT_FAILED;
    } else if (isArchive()) {
      return getTotalUnreadCount();
    } else if (getSource() != null) {
      return 0;
    } else if (xgramCommunityId != 0) {
      // The badge counts what notifies you: the unmuted chats' unread when they have any (coloured badge),
      // otherwise everything that is unread (muted badge). A muted group never lends its count to a coloured badge.
      int unreadCount = 0, unmutedUnreadCount = 0;
      boolean markedAsUnread = false, unmutedMarkedAsUnread = false;
      for (TdApi.Chat member : xgramMembers()) {
        unreadCount += member.unreadCount;
        markedAsUnread |= member.isMarkedAsUnread;
        if (tdlib.chatNotificationsEnabled(member)) {
          unmutedUnreadCount += member.unreadCount;
          unmutedMarkedAsUnread |= member.isMarkedAsUnread;
        }
      }
      if (unmutedUnreadCount > 0) {
        return unmutedUnreadCount;
      }
      if (unmutedMarkedAsUnread) {
        return Tdlib.CHAT_MARKED_AS_UNREAD;
      }
      return unreadCount > 0 ? unreadCount : markedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    } else {
      return chat.unreadCount > 0 ? chat.unreadCount : chat.isMarkedAsUnread ? Tdlib.CHAT_MARKED_AS_UNREAD : 0;
    }
  }

  public boolean hasUnreadReactions () {
    if (isArchive()) {
      return archive != null && archive.hasUnreadReactions();
    } else if (xgramCommunityId != 0) {
      for (TdApi.Chat member : xgramMembers()) {
        if (member.unreadReactionCount > 0) {
          return true;
        }
      }
      return false;
    } else {
      return chat != null && chat.unreadReactionCount > 0;
    }
  }

  public boolean hasUnreadMentions () {
    if (isArchive()) {
      return archive.hasUnreadMentions();
    } else if (xgramCommunityId != 0) {
      for (TdApi.Chat member : xgramMembers()) {
        if (member.unreadMentionCount > 0) {
          return true;
        }
      }
      return false;
    } else {
      return chat.unreadMentionCount > 0;
    }
  }

  public boolean hasScheduledMessages () {
    if (isArchive()) {
      return archive.hasScheduledMessages();
    } else {
      return chat.hasScheduledMessages;
    }
  }

  public boolean isFailed () {
    if (isArchive()) {
      return archive.hasFailedMessages();
    } else {
      return TD.isFailed(chat.lastMessage);
    }
  }

  public boolean isSending () {
    return chat != null && (tdlib.messageSending(chat.lastMessage) || tdlib.messageBeingEdited(chat.lastMessage) || tdlib.albumBeingEdited(currentPreview != null ? currentPreview.getAlbum() : null));
  }

  public boolean notificationsEnabled () {
    if (isArchive()) {
      TdlibCounter counter = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      return counter.chatUnmutedCount > 0;
    } else if (xgramCommunityId != 0) {
      for (TdApi.Chat member : xgramMembers()) {
        if (tdlib.chatNotificationsEnabled(member) && (member.unreadCount > 0 || member.isMarkedAsUnread || member.unreadReactionCount > 0)) {
          return true; // coloured only for the unread of chats that notify you, never for a muted chat's unread
        }
      }
      return false;
    } else {
      return tdlib.chatNotificationsEnabled(chat);
    }
  }

  public boolean updateSecretChat (TdApi.SecretChat secretChat) {
    if (isSecretChat() && TD.getSecretChatId(chat) == secretChat.id) {
      if (chat.lastMessage == null && !showDraft()) {
        setText();
        return true;
      }
    }
    return false;
  }

  private int lastAvailWidth = -1;

  public void layoutTitle (boolean changed) {
    int avail = currentWidth - ChatView.getLeftPadding(listMode) - xgramTimeReserve() - ChatView.getTimePaddingRight() - ChatView.getTimePaddingLeft();
    final boolean isSecret = isSecretChat();
    if (showMute()) {
      avail = avail - ChatView.getMuteOffset() - Icons.getChatMuteDrawableWidth();
    }
    boolean showVerify = tdlib.chatVerified(chat);
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_VERIFY, showVerify);
    if (showVerify) {
      avail = avail - Screen.dp(20f);
    }
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_SCAM, tdlib.chatScam(chat));
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SHOW_FAKE, tdlib.chatFake(chat));
    boolean showChatMark = showFake() || showScam();
    if (showChatMark) {
      chatMark = new Text.Builder(Lang.getString(showFake() ? R.string.FakeMark : R.string.ScamMark), avail, Paints.robotoStyleProvider(12f), TextColorSets.Regular.NEGATIVE)
        .singleLine()
        .allBold()
        .clipTextArea()
        .build();
      avail -= chatMark.getWidth() + (Screen.dp(4f) * 2);
    }
    if (showViews()) {
      avail -= viewCounter.getScaledWidth(Screen.dp(3f));
    } else if (isSending() || isOutgoing()) {
      avail = avail - ChatView.getTimePaddingLeft() - Screen.dp(20f);
    }
    if (isSecret) {
      avail = avail - Screen.dp(14f);
    }

    if (!tdlib.isSelfChat(chat)) {
      emojiStatusDrawable = EmojiStatusHelper.makeDrawable(null, tdlib, chat != null ? tdlib.chatUser(chat) : null, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.iconActive);
        }
      }, this::invalidateEmojiStatusReceiver);
      emojiStatusDrawable.invalidateTextMedia();
      avail -= emojiStatusDrawable.getWidth(Screen.dp(6));
    }
    if (needDrawReactionsPreview()) {
      int reactionsWidth = reactionsCounterDrawable.getTargetWidth();
      avail -= reactionsWidth + (reactionsWidth > 0 ? Screen.dp(3) : 0);
    }
    if (listMode == Settings.CHAT_MODE_1LINE) {
      // XGram: one line keeps the badges (or the pin) and the draft pencil on the title's row
      xgramOneLineReserve = xgramComputeOneLineReserve();
      avail = Math.max(0, avail - xgramOneLineReserve);
    }

    boolean xgramUnread = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && xgramHasUnread();
    if (xgramTitleUnread != xgramUnread) {
      xgramTitleUnread = xgramUnread; // XGram: unread titles are bolder
      changed = true;
    }
    if (changed || lastAvailWidth != avail) {
      lastAvailWidth = avail;
      if (StringUtils.isEmpty(title) || (listMode == Settings.CHAT_MODE_1LINE && avail < Screen.dp(1f))) { // XGram: no room on a very narrow one line row
        trimmedTitle = null;
      } else {
        trimmedTitle = new Text.Builder(title, avail, xgramTitleStyleProvider(), isSecretChat() ? TextColorSets.Regular.SECURE : TextColorSets.Regular.NORMAL)
          .singleLine()
          .allBold()
          .clipTextArea()
          .build();
      }
    }
    int titleWidth = getTitleWidth();
    verifyLeft = ChatView.getLeftPadding(listMode) + titleWidth;
    muteLeft = ChatView.getLeftPadding(listMode) + titleWidth + ChatView.getMutePadding();
    emojiStatusLeft = ChatView.getLeftPadding(listMode) + titleWidth + Screen.dp(3);
    if (emojiStatusDrawable != null) {
      muteLeft += emojiStatusDrawable.getWidth(Screen.dp(6));
      verifyLeft += emojiStatusDrawable.getWidth(Screen.dp(6));
    }
    if (showVerify) {
      muteLeft += Screen.dp(20f);
    }
    if (showChatMark) {
      muteLeft += chatMark.getWidth() + Screen.dp(14f);
    }
    if (isSecret) {
      verifyLeft += Screen.dp(14f);
      muteLeft += Screen.dp(14f);
    }
    if (changed && avatarPlaceholder != null) {
      setAvatar();
    }
  }

  public boolean needDrawReactionsPreview () {
    return isPrivate() && !isSelfChat() && !showDraft();
  }

  public @Nullable EmojiStatusHelper.EmojiStatusDrawable getEmojiStatus () {
    return emojiStatusDrawable;
  }

  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    currentViews.performWithViews(view -> {
      if (view instanceof EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate) {
        ((EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate) view).invalidateEmojiStatusReceiver(text, specificMedia);
      }
    });
  }

  public void invalidateReactionsReceiver () {
    currentViews.performWithViews(view -> {
      if (view instanceof ChatView) {
        requestReactionFiles(((ChatView) view).getReactionsReceiver());
      }
    });
    currentViews.invalidate();
  }

  public void requestReactionFiles (ComplexReceiver complexReceiver) {
    for (Map.Entry<String, TGReactions.MessageReactionEntry> pair : reactionsMapEntry.entrySet()) {
      TGReactions.MessageReactionEntry entry = pair.getValue();
      entry.requestReactionFiles(complexReceiver);
    }
  }

  private void setTitleImpl (String title) {
    this.title = title;
  }

  public void setTitle () {
    setTitleImpl(isArchive() ? Lang.getString(R.string.ArchiveTitleList) : xgramCommunityId != 0 ? xgramCommunityTitle() : tdlib.chatTitle(chat));
    layoutTitle(true);
  }

  // XGram: this row stands for a whole Telegram community, its chat most recently active in the list (XgramCommunities).

  private long xgramCommunityId;
  private boolean xgramMergeList;

  /** Set by the chat list that owns the row (pickers don't merge). */
  public void xgramSetMergeCommunities (boolean merge) {
    if (xgramMergeList == merge) {
      return;
    }
    xgramMergeList = merge;
    long communityId = xgramComputeCommunity();
    if (communityId != xgramCommunityId) {
      xgramCommunityId = communityId;
      xgramPrepareCommunity();
      if (currentWidth > 0) {
        setTitle();
        setText();
      }
      setCounter(false);
    }
  }

  /** The hidden members never get a row of their own: their forum badges and the community's look are asked for here. */
  private void xgramPrepareCommunity () {
    if (xgramCommunityId == 0) {
      return;
    }
    com.fahadali.xgram.chats.XgramCommunities.requestLookIfNeeded(tdlib, xgramCommunityId);
    for (TdApi.Chat member : com.fahadali.xgram.chats.XgramCommunities.presentMembers(tdlib, xgramCommunityId, chatList)) {
      com.fahadali.xgram.topics.XgramForumUnread.requestIfNeeded(tdlib, member);
    }
  }

  private long xgramComputeCommunity () {
    if (!xgramMergeList || chatList == null || archive != null || chat == null) {
      return 0;
    }
    return com.fahadali.xgram.chats.XgramCommunities.mergedCommunity(tdlib, chat.id, chatList);
  }

  public long xgramCommunityId () {
    return xgramCommunityId;
  }

  private String xgramCommunityTitle () {
    TdApi.Community community = tdlib.cache().community(xgramCommunityId);
    return community != null && !StringUtils.isEmpty(community.name) ? community.name : tdlib.chatTitle(chat);
  }

  // XGram Later: the bell

  private boolean xgramHasBell () {
    if (isArchive() || chat == null) {
      return false; // the archive row never has it
    }
    if (xgramCommunityId != 0) {
      for (TdApi.Chat member : xgramMembers()) {
        if (com.fahadali.xgram.later.XgramLater.hasBell(tdlib, member.id)) {
          return true; // a merged community: any member's
        }
      }
      return false;
    }
    return com.fahadali.xgram.later.XgramLater.hasBell(tdlib, chat.id);
  }

  public Counter xgramLaterCounter () {
    return xgramLaterCounter;
  }

  /** The bell came or went (UI thread): returns whether it changed. */
  public boolean xgramRefreshLater () {
    boolean before = xgramLaterCounter.getVisibilityTarget();
    setCounter(true);
    return before != xgramLaterCounter.getVisibilityTarget();
  }

  /** Members came or went, or one of the others changed: title, counters and preview again. */
  public boolean xgramRefreshCommunity () {
    long communityId = xgramComputeCommunity();
    if (communityId == 0 && xgramCommunityId == 0) {
      return false;
    }
    xgramCommunityId = communityId;
    xgramPrepareCommunity();
    setTitle();
    setText();
    setCounter(true);
    return true;
  }

  private List<TdApi.Chat> xgramMembers () {
    List<TdApi.Chat> members = com.fahadali.xgram.chats.XgramCommunities.presentMembers(tdlib, xgramCommunityId, chatList);
    for (int i = 0; i < members.size(); i++) {
      if (members.get(i).id == chat.id) {
        members.set(i, chat); // this row's own copy is the up to date one
      }
    }
    return members;
  }

  // XGram: a row on the community page: its own row height, the forum's topics in place of the sender (who then moves
  // into the text), and for chats you are not in a text of its own ("@username, N members").

  private int xgramForcedMode;
  @Nullable private String xgramTopicsLine, xgramForcedText;
  @Nullable private TextEntity[] xgramTopicsEntities;

  /** Call before the row is laid out (before {@link ChatView#setChat}); later calls need a requestLayout of the view. */
  public void xgramSetCommunityRow (int forcedMode, @Nullable String topicsLine, @Nullable TextEntity[] topicsEntities, @Nullable String forcedText) {
    this.xgramForcedMode = forcedMode;
    this.xgramTopicsLine = topicsLine;
    this.xgramTopicsEntities = topicsEntities != null && topicsEntities.length > 0 ? topicsEntities : null;
    this.xgramForcedText = forcedText;
    if (forcedMode != 0 && listMode != forcedMode) {
      listMode = forcedMode;
      currentWidth = 0; // the next checkLayout builds the row again
    }
  }

  /** The topics line as the prefix (the second line). True when it took the place of the usual sender prefix. */
  private boolean xgramSetTopicsPrefix () {
    // Only three lines have a line of their own for the topics; two lines and one line are the chat list's rows
    if (xgramTopicsLine == null || showDraft() || !com.fahadali.xgram.chats.XgramChatListMode.isThreeLines(listMode)) {
      return false;
    }
    flags |= FLAG_HAS_PREFIX;
    int avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - getCounterAddWidth();
    if (avail > 0) {
      this.prefix = new Text.Builder(xgramTopicsLine, avail, getTextStyleProvider(listMode), TextColorSets.Regular.LIGHT)
        .singleLine()
        .textFlags(Text.FLAG_ELLIPSIZE_NO_FILL)
        .entities(xgramTopicsEntities, null)
        .build();
    } else {
      this.prefix = null;
    }
    layoutText();
    return true;
  }

  /** With the topics on the second line, the message's sender goes in front of the text: "Sender: message". */
  private void xgramPrependSender (ContentPreview preview) {
    // A preview that names its author itself ("Alice pinned ...") keeps the name out of the front.
    if (xgramTopicsLine == null || !com.fahadali.xgram.chats.XgramChatListMode.isThreeLines(listMode) || preview.hideAuthor || (flags & FLAG_MESSAGE) == 0 || chat == null || chat.lastMessage == null || showDraft() || StringUtils.isEmpty(text)) {
      return;
    }
    final String sender;
    if (isOutgoing()) {
      sender = Lang.getString(tdlib.isMultiChat(chat) && Td.getSenderId(chat.lastMessage) == chat.id ? R.string.FromYouAnonymous : R.string.FromYou);
    } else if (tdlib.isMultiChat(chat) && !tdlib.isChannel(chat.id) && !Td.isProximityAlertTriggered(chat.lastMessage.content)) {
      sender = tdlib.senderName(chat.lastMessage, false, false);
    } else {
      sender = null;
    }
    if (StringUtils.isEmpty(sender)) {
      return;
    }
    String head = sender + ": ";
    List<TextEntity> shifted = new ArrayList<>();
    shifted.add(new TextEntityCustom(context, tdlib, null, 0, sender.length(), 0, null).setCustomColorSet(TextColorSets.Regular.NORMAL));
    if (entities != null) {
      for (TextEntity entity : entities) {
        entity.offset(head.length());
        shifted.add(entity);
      }
    }
    this.text = head + text;
    this.entities = shifted.toArray(new TextEntity[0]);
  }

  public Text getTitle () {
    return trimmedTitle;
  }

  public boolean setTime () {
    String time;
    if (isArchive()) {
      int maxDate = archive.maxDate();
      time = maxDate != 0 ? Lang.timeOrDateShort(maxDate, TimeUnit.SECONDS) : "";
    } else {
      TdApi.ChatSource source = getSource();
      if (source != null) {
        switch (source.getConstructor()) {
          case TdApi.ChatSourceMtprotoProxy.CONSTRUCTOR:
            time = Lang.getString(R.string.ProxySponsor);
            break;
          case TdApi.ChatSourcePublicServiceAnnouncement.CONSTRUCTOR: {
            time = Lang.getPsaType(((TdApi.ChatSourcePublicServiceAnnouncement) source));
            break;
          }
          default: {
            Td.assertChatSource_12b21238();
            throw Td.unsupported(source);
          }
        }
      } else {
        int date = chat.draftMessage != null && showDraft() ? chat.draftMessage.date : chat.lastMessage != null ? chat.lastMessage.date : 0;
        time = date != 0 ? Lang.timeOrDateShort(date, TimeUnit.SECONDS) : "";
      }
    }
    boolean changed = !time.equals(this.time);
    if (changed) {
      this.time = time;
      this.timeWidth = (int) U.measureText(time, ChatView.getTimePaint());
    }
    layoutTime();
    setReactions(UI.inUiThread());
    setViews();
    return changed;
  }

  private int getViewCount () {
    return chat == null || !TD.isOut(chat.lastMessage) ? 0 : TD.getViewCount(chat.lastMessage.interactionInfo);
  }

  private void setViews () {
    if (viewCounter != null) {
      int count = getViewCount();
      viewCounter.setCount(count, needAnimateChanges());
    }
  }

  public void updateDate () {
    if (setTime()) {
      layoutTitle(false);
    }
  }

  public void updateLocale (boolean forceText) {
    setTime();
    setCounter(true);
    if (forceText || (flags & FLAG_CONTENT_STRING) != 0 || (flags & FLAG_TEXT_DRAFT) != 0) {
      setText();
    }
    if ((flags & FLAG_SELF_CHAT) != 0) {
      setTitle();
    } else {
      layoutTitle(false);
    }
  }

  private void layoutTime () {
    timeLeft = currentWidth - ChatView.getTimePaddingRight() - timeWidth;
    // XGram: one line keeps at least 24dp for the time, where the typing icon takes its place, so nothing reflows
    checkRight = currentWidth - ChatView.getTimePaddingRight() - xgramTimeReserve() - ChatView.getTimePaddingLeft();
    reactionsRight = checkRight - ChatView.getTimePaddingLeft();
  }

  private int xgramTimeReserve () {
    return listMode == Settings.CHAT_MODE_1LINE ? Math.max(timeWidth, Screen.dp(24f)) : timeWidth;
  }

  public String getTime () {
    return time;
  }

  public int getTimeLeft () {
    return timeLeft;
  }

  public int getTitleWidth () {
    return trimmedTitle != null ? trimmedTitle.getWidth() : 0;
  }

  public int getTextWidth () {
    return trimmedText != null ? trimmedText.getWidth() : 0;
  }

  public int getTimeWidth () {
    return timeWidth;
  }

  public Counter getViewCounter () {
    return viewCounter;
  }

  public int getMuteLeft() {
    return muteLeft;
  }

  public int getVerifyLeft () {
    return verifyLeft;
  }

  public int getEmojiStatusLeft () {
    return emojiStatusLeft + (isSecretChat() ? Screen.dp(12) : 0);
  }

  public int getChecksRight () {
    return checkRight;
  }

  public int getReactionsWidth () {
    return reactionsCounterDrawable.getMinimumWidth();
  }

  private int getCounterAddWidth () {
    return Math.round(
      counter.getScaledWidth(ChatView.getTimePaddingLeft()) +
      mentionCounter.getScaledWidth(ChatView.getTimePaddingLeft()) +
      reactionsCounter.getScaledWidth(ChatView.getTimePaddingLeft()) +
      xgramLaterCounter.getScaledWidth(ChatView.getTimePaddingLeft()) // XGram Later
    );
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

  public BounceAnimator getScheduleAnimator () {
    return scheduleAnimator;
  }

  private void layoutText () {
    if (listMode == Settings.CHAT_MODE_1LINE) {
      // XGram: one line shows no message preview. setText still keeps the flags, the preview and the visible message
      // (read receipts, refreshes), only no Text is built.
      lastCounterAddWidth = getCounterAddWidth();
      textLeft = ChatView.getLeftPadding(listMode);
      textIconsPadding = 0;
      xgramTextUnread = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && xgramHasUnread();
      if (trimmedText != null) {
        trimmedText = null;
        currentViews.invalidateContent(this);
      }
      layoutChatAction();
      xgramCheckOneLineReserve(); // a draft came or went
      return;
    }
    int avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - (lastCounterAddWidth = getCounterAddWidth());
    textLeft = ChatView.getLeftPadding(listMode);
    if ((flags & FLAG_HAS_PREFIX) != 0 && prefix != null && listMode == Settings.CHAT_MODE_2LINE) {
      int prefixWidth = prefix.getWidth();
      avail -= prefixWidth;
      textLeft += prefixWidth;
    }

    this.textIconsPadding = textIconIds != null && textIconIds.size() > 0 ? Screen.dp(2f) + Screen.dp(18f) * textIconIds.size() : 0;

    xgramTextUnread = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && xgramHasUnread();
    if (avail > 0 && !StringUtils.isEmpty(text)) {
      trimmedText = new Text.Builder(text, avail, getTextStyleProvider(listMode), xgramTextUnread ? TextColorSets.Regular.NORMAL : TextColorSets.Regular.LIGHT)
        .maxLineCount(isSingleLine() ? 1 : 2)
        .textFlags(Text.FLAG_ELLIPSIZE_NEWLINE)
        .ignoreContinuousNewLines()
        .ignoreNewLines(isSingleLine())
        .lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? textIconsPadding : 0)
        .viewProvider(currentViews)
        .entities(entities, (text, specificMedia) -> {
          if (this.trimmedText == text) {
            for (View view : currentViews) {
              ComplexReceiver receiver = ((ChatView) view).getTextMediaReceiver();
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
      trimmedText = null;
    }

    layoutChatAction();
  }

  private int textIconsPadding;

  private static TextStyleProvider getTextStyleProvider (int chatListMode) {
    return Paints.robotoStyleProvider(chatListMode == Settings.CHAT_MODE_3LINE ? 15 : 16); // XGram: 16 in one line (no text drawn there)
  }

  // XGram: Material 3 Expressive chat list typography: Google Sans Flex titleMedium, weight 700 while unread.

  private boolean xgramTitleUnread, xgramTextUnread;

  /** Has unread messages (or is marked as unread). */
  public boolean xgramHasUnread () {
    int count = getUnreadCount();
    return count > 0 || count == Tdlib.CHAT_MARKED_AS_UNREAD;
  }

  private TextStyleProvider xgramTitleStyleProvider () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, xgramTitleUnread, 16f);
    }
    return getTitleStyleProvider(listMode);
  }

  private void xgramCheckUnreadStyle () {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || currentWidth <= 0) {
      return;
    }
    boolean unread = xgramHasUnread();
    if (unread != xgramTitleUnread) {
      layoutTitle(false);
    }
    if (unread != xgramTextUnread) {
      layoutText();
    }
  }

  private static TextStyleProvider getTitleStyleProvider (int chatListMode) {
    return Paints.robotoStyleProvider(chatListMode == Settings.CHAT_MODE_3LINE || chatListMode == Settings.CHAT_MODE_1LINE ? 16 : 17); // XGram: 16 in one line
  }

  public @Nullable IntList getTextIconIds () {
    return textIconIds;
  }

  public @PorterDuffColorId int getTextIconColorId () {
    return textIconColorId;
  }

  private void setPrefix () {
    if (listMode == Settings.CHAT_MODE_1LINE) {
      this.prefix = null; // XGram: one line shows neither the sender nor the topic
      layoutText();
      return;
    }
    if (xgramSetTopicsPrefix()) { // XGram: the community page shows the forum's topics here
      return;
    }
    if ((flags & FLAG_HAS_PREFIX) != 0) {
      final String prefix;
      boolean needSuffix = true;
      if (showDraft()) {
        TdApi.DraftMessage draftMessage = chat.draftMessage;
        Td.assertDraftMessageContent_f690069b();
        needSuffix = draftMessage != null && draftMessage.content.getConstructor() == TdApi.DraftMessageContentText.CONSTRUCTOR && !Td.isEmpty(((TdApi.DraftMessageContentText) draftMessage.content).text);
        prefix = Lang.getString(R.string.Draft);
        flags |= FLAG_CONTENT_STRING;
      } else if (isOutgoing()) {
        prefix = Lang.getString(listMode != Settings.CHAT_MODE_2LINE && tdlib.isMultiChat(chat) && Td.getSenderId(chat.lastMessage) == chat.id ? R.string.FromYouAnonymous : R.string.FromYou);
        flags |= FLAG_CONTENT_STRING;
      } else if (chat.lastMessage != null && !Td.isProximityAlertTriggered(chat.lastMessage.content)) {
        prefix = listMode == Settings.CHAT_MODE_2LINE && Td.getMessageAuthorId(chat.lastMessage) == chat.lastMessage.chatId && StringUtils.isEmpty(chat.lastMessage.authorSignature) ?
          Lang.getString(R.string.FromAnonymous) :
          tdlib.senderName(chat.lastMessage, false, listMode == Settings.CHAT_MODE_2LINE);
      } else {
        prefix = null;
      }
      // XGram: in forums, show the topic of the last message before its sender.
      final String topicName = !showDraft() && chat.lastMessage != null ?
        com.fahadali.xgram.topics.XgramForums.chatListTopicName(tdlib, chat, chat.lastMessage, this::onForumTopicNameLoaded) : null;
      final String fullPrefix = topicName != null ? (StringUtils.isEmpty(prefix) ? topicName : topicName + " › " + prefix) : prefix;

      if (!StringUtils.isEmpty(fullPrefix)) {
        int avail;
        if (listMode != Settings.CHAT_MODE_2LINE) {
          avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - getCounterAddWidth();
        } else {
          avail = Screen.dp(topicName != null ? 190f : 120f);
        }
        if (avail > 0) {
          Text.Builder b = new Text.Builder(fullPrefix, avail, getTextStyleProvider(listMode), BitwiseUtils.hasFlag(flags, FLAG_TEXT_DRAFT) ? TextColorSets.Regular.NEGATIVE : TextColorSets.Regular.NORMAL)
            .singleLine()
            .textFlags(Text.FLAG_ELLIPSIZE_NO_FILL);
          if (needSuffix && listMode == Settings.CHAT_MODE_2LINE) {
            b.suffix(": ");
          }
          this.prefix = b.build();
        } else {
          this.prefix = null;
        }
      } else {
        this.prefix = null;
      }
    } else {
      this.prefix = null;
    }
    layoutText();
  }

  // XGram: the topic name for the forum preview arrived after the row was laid out.
  private void onForumTopicNameLoaded () {
    if (!isDestroyed && currentWidth > 0) {
      setText();
      currentViews.invalidate();
    }
  }

  private void setTextValue (@StringRes int resId) {
    setTextValue(Lang.getString(resId), true);
  }

  private void setTextValue (String text, boolean isTranslatable) {
    setTextValue(text, (TextEntity[]) null, isTranslatable);
  }

  private boolean isSingleLine () {
    return listMode == Settings.CHAT_MODE_2LINE || listMode == Settings.CHAT_MODE_1LINE || (flags & FLAG_HAS_PREFIX) != 0;
  }

  private void setTextValue (String text, TextEntity[] entities, boolean isTranslatable) {
    this.text = text;
    this.entities = entities != null && entities.length > 0 ? entities : null;;
    this.flags = BitwiseUtils.setFlag(this.flags, FLAG_CONTENT_STRING, isTranslatable);
  }

  private void setTextValue (String text, TdApi.TextEntity[] entities, boolean isTranslatable) {
    setTextValue(text, TextEntity.valueOf(tdlib, text, entities, null), isTranslatable);
  }

  public void setText () {
    flags &= ~FLAG_HAS_PREFIX;
    flags &= ~FLAG_TEXT_DRAFT;
    flags &= ~FLAG_CONTENT_HIDDEN;
    flags &= ~FLAG_CONTENT_STRING;
    flags &= ~FLAG_MESSAGE;
    if (textIconIds != null) {
      textIconIds.clear();
    }
    visibleMessage = null;
    currentPreview = null;

    if (tdlib.hasPasscode(chat)) {
      flags |= FLAG_CONTENT_HIDDEN;
      setContentPreview(new ContentPreview(ContentPreview.EMOJI_LOCK, R.string.ChatContentProtected));
      return;
    }

    String restrictionText = Lang.getRestrictionText(tdlib.chatRestriction(chat));
    if (!StringUtils.isEmpty(restrictionText)) {
      setContentPreview(new ContentPreview(ContentPreview.EMOJI_ERROR, 0, restrictionText, false));
      return;
    }

    if (xgramForcedText != null) { // XGram: a chat you are not in, on the community page
      setTextValue(xgramForcedText, (TextEntity[]) null, false);
      setPrefix();
      return;
    }

    if (xgramCommunityId != 0) {
      // XGram: like Telegram, a community lists its chats, the most recent first, unread ones highlighted.
      List<TdApi.Chat> members = xgramMembers();
      java.util.Collections.sort(members, (a, b) -> Long.compare(ChatPosition.getOrder(b, chatList), ChatPosition.getOrder(a, chatList)));
      List<TextEntity> entities = new ArrayList<>();
      StringBuilder b = new StringBuilder();
      for (TdApi.Chat member : members) {
        if (b.length() > 0) {
          b.append(Lang.getConcatSeparator());
        }
        int startIndex = b.length();
        b.append(tdlib.chatTitle(member));
        if (member.unreadCount > 0 || member.isMarkedAsUnread) {
          entities.add(new TextEntityCustom(context, tdlib, null, startIndex, b.length(), 0, null)
            .setCustomColorSet(TextColorSets.Regular.NORMAL)
          );
        }
      }
      setTextValue(b.toString(), !entities.isEmpty() ? entities.toArray(new TextEntity[0]) : null, false);
      setPrefix();
      return;
    }

    if (isArchive()) {
      List<TextEntity> entities = new ArrayList<>();
      StringBuilder b = new StringBuilder();
      archive.iterate(chat -> {
        if (b.length() > 0) {
          b.append(Lang.getConcatSeparator());
        }
        int startIndex = b.length();
        b.append(tdlib.chatTitle(chat));
        if (chat.unreadCount > 0) {
          entities.add(new TextEntityCustom(context, tdlib, null, startIndex, b.length(), 0, null)
            .setCustomColorSet(TextColorSets.Regular.NORMAL)
          );
        }
      }, ARCHIVE_PREVIEW_LIMIT, (remainingCount) -> {
        b.append(Lang.getConcatSeparatorLast(false));
        b.append(Lang.plural(R.string.xOtherChatTitles, remainingCount));
      });
      if (b.length() == 0) {
        b.append(Lang.pluralBold(R.string.xChats, archive.totalCount()));
      }
      setTextValue(b.toString(), !entities.isEmpty() ? entities.toArray(new TextEntity[0]) : null, false);
      setPrefix();
      return;
    }

    if (chat.draftMessage != null && showDraft()) {
      flags |= FLAG_TEXT_DRAFT | FLAG_HAS_PREFIX;
      // TODO rich message
      Td.assertDraftMessageContent_f690069b();
      TdApi.FormattedText text = ((TdApi.DraftMessageContentText) chat.draftMessage.content).text;
      setTextValue(text.text, text.entities, false);
      setPrefix();
      return;
    }

    TdApi.ChatSource source = getSource();
    String psaText = source instanceof TdApi.ChatSourcePublicServiceAnnouncement ? ((TdApi.ChatSourcePublicServiceAnnouncement) source).text : null;
    if (!StringUtils.isEmpty(psaText)) {
      setContentPreview(new ContentPreview(ContentPreview.EMOJI_INFO, 0, psaText, false));
      return;
    }

    if (chat.lastMessage == null && ChatId.isSecret(chat.id)) {
      boolean specialState = true;
      TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chat.id);
      if (secretChat != null) {
        switch (secretChat.state.getConstructor()) {
          case TdApi.SecretChatStatePending.CONSTRUCTOR: { // not yet started
            setTextValue(secretChat.isOutbound ? Lang.getString(R.string.AwaitingEncryption, tdlib.cache().userFirstName(secretChat.userId)) : Lang.getString(R.string.VoipExchangingKeys), true);
            break;
          }
          case TdApi.SecretChatStateReady.CONSTRUCTOR: { // active
            if (secretChat.isOutbound)
              setTextValue(Lang.getString(R.string.XJoinedSecretChat, tdlib.cache().userFirstName(secretChat.userId)), true);
            else
              setTextValue(Lang.getString(R.string.YouJoinedSecretChat), true);
            break;
          }
          case TdApi.SecretChatStateClosed.CONSTRUCTOR: {
            setTextValue(R.string.SecretChatCancelled);
            break;
          }
          default:
            throw new RuntimeException();
        }
      } else {
        specialState = false;
      }

      if (specialState) {
        setPrefix();
        return;
      }
    }

    TdApi.Message msg = chat.lastMessage;
    if (msg != null) {
      flags |= FLAG_MESSAGE;
      // No need to check tdlib.chatRestrictionReason, because it's already handled above
      ContentPreview preview = com.fahadali.xgram.chats.XgramFilters.isHidden(tdlib, msg) ?
        new ContentPreview(ContentPreview.EMOJI_INFO, R.string.XgramHiddenMessage) : // XGram: message filters and shadow bans
        ContentPreview.getChatListPreview(tdlib, msg.chatId, msg, false);
      setContentPreview(preview);
      visibleMessage = msg;
    } else {
      setTextValue(R.string.DeletedMessage);
      setPrefix();
    }
  }

  private TdApi.Message visibleMessage;

  @Override
  public boolean isMediaGroup () {
    return currentPreview != null && currentPreview.getAlbum() != null;
  }

  @Override
  public List<TdApi.Message> getVisibleMediaGroup () {
    Tdlib.Album album = currentPreview != null ? currentPreview.getAlbum() : null;
    return album != null ? album.messages : null;
  }

  @Override
  public TdApi.Message getVisibleMessage () {
    return visibleMessage;
  }

  private void addIcon (int icon) {
    if (icon != 0) {
      if (textIconIds == null)
        textIconIds = new IntList(2);
      textIconIds.append(icon);
    }
  }

  private void setContentPreview (ContentPreview preview) {
    if (textIconIds != null)
      textIconIds.clear();
    setTextValue(preview.buildText(true), preview.formattedText != null ? preview.formattedText.entities : null, preview.isTranslatable);
    xgramPrependSender(preview); // XGram: community page, the sender's place is taken by the topics
    this.currentPreview = preview;
    TdApi.Message lastMessage = chat != null ? chat.lastMessage : null;
    if (preview.parentEmoji != null) {
      addIcon(preview.parentEmoji.iconRepresentation);
    } else if (lastMessage != null && (lastMessage.isChannelPost || getPrefixIconCount() == 0)) {
      boolean needShareIcon = false;
      if (lastMessage.forwardInfo != null) {
        TdApi.MessageOrigin origin = lastMessage.forwardInfo.origin;
        switch (origin.getConstructor()) {
          case TdApi.MessageOriginChannel.CONSTRUCTOR:
            needShareIcon = chat.id != ((TdApi.MessageOriginChannel) origin).chatId;
            break;
          case TdApi.MessageOriginHiddenUser.CONSTRUCTOR:
            needShareIcon = true;
            break;
          case TdApi.MessageOriginUser.CONSTRUCTOR:
            needShareIcon = Td.getSenderUserId(chat.lastMessage) != ((TdApi.MessageOriginUser) origin).senderUserId;
            break;
          case TdApi.MessageOriginChat.CONSTRUCTOR:
            needShareIcon = Td.getSenderId(chat.lastMessage) != ((TdApi.MessageOriginChat) origin).senderChatId;
            break;
          default:
            Td.assertMessageOrigin_f2224a59();
            throw Td.unsupported(origin);
        }
      }
      if (needShareIcon) {
        addIcon(R.drawable.baseline_share_arrow_16);
      } else if (lastMessage.importInfo != null) {
        addIcon(R.drawable.templarian_baseline_import_16);
      } else if (lastMessage.replyTo != null && lastMessage.replyTo.getConstructor() == TdApi.MessageReplyToMessage.CONSTRUCTOR) {
        TdApi.MessageReplyToMessage replyToMessage = (TdApi.MessageReplyToMessage) lastMessage.replyTo;
        if (replyToMessage.chatId != lastMessage.chatId) {
          addIcon(R.drawable.baseline_reply_16);
        }
      }
    }
    if (preview.emoji != null) {
      addIcon(preview.emoji.iconRepresentation);
    }
    this.textIconColorId = ColorId.chatListIcon;

    if ((isGroup() || isSupergroup()) && !preview.hideAuthor) {
      flags |= FLAG_HAS_PREFIX;
    } else if (chat.lastMessage != null && Td.isCall(chat.lastMessage.content)) {
      if (textIconIds != null)
        textIconIds.clear();
      addIcon(CallItem.getSubtitleIcon((TdApi.MessageCall) chat.lastMessage.content, TD.isOut(chat.lastMessage)));
      textIconColorId = CallItem.getSubtitleIconColorId((TdApi.MessageCall) chat.lastMessage.content);
    }

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

  public int getPrefixIconCount () {
    return textIconIds != null ? textIconIds.size() : 0;
  }

  public Text getPrefix () {
    return (flags & FLAG_HAS_PREFIX) != 0 ? prefix : null;
  }

  @Nullable
  public Text getText () {
    return trimmedText;
  }

  @Nullable
  public Text getChatMark () {
    return chatMark;
  }

  public int getTextLeft () {
    return textLeft;
  }

  // Typing utils

  @Nullable
  private final TdlibStatusManager.Helper statusHelper;

  public void attachToView (View view) {
    if (currentViews.attachToView(view)) {
      setViewAttached(true);
    }
  }

  public void detachFromView (View view) {
    if (currentViews.detachFromView(view)) {
      setViewAttached(currentViews.hasAnyTargetToInvalidate());
    }
  }

  private void setViewAttached (boolean isAttached) {
    boolean nowIsAttached = (flags & FLAG_ATTACHED) != 0;
    if (isAttached != nowIsAttached) {
      flags = BitwiseUtils.setFlag(flags, FLAG_ATTACHED, isAttached);
      if (statusHelper != null) {
        if (isAttached) {
          statusHelper.attachToChat(chat.id, null);
        } else {
          statusHelper.detachFromAnyChat();
        }
      }
    }
  }

  @Nullable
  public final TdlibStatusManager.Helper statusHelper () {
    return statusHelper;
  }

  @Override
  public void layoutChatAction () {
    if (statusHelper == null)
      return;
    if (listMode == Settings.CHAT_MODE_1LINE) {
      // XGram: one line draws only the action's icon, in the time's place (ChatView); drawingState works without a text
      statusHelper.setDrawingText(null);
      return;
    }
    String chatActionText = statusHelper.fullText();
    Text trimmedChatAction;
    if (StringUtils.isEmpty(chatActionText)) {
      trimmedChatAction = null;
    } else {
      int avail = currentWidth - ChatView.getLeftPadding(listMode) - ChatView.getRightPadding() - getCounterAddWidth();
      int iconWidth = statusHelper.actionIconWidth();
      if (avail > 0) {
        Text.Builder b = new Text.Builder(chatActionText, avail, getTextStyleProvider(listMode), TextColorSets.Regular.LIGHT).maxLineCount(listMode == Settings.CHAT_MODE_2LINE ? 1 : 2);
        if (iconWidth > 0) {
          b.lineMarginProvider((lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? iconWidth : 0);
        }
        trimmedChatAction = b.build();
      } else {
        trimmedChatAction = null;
      }
    }
    statusHelper.setDrawingText(trimmedChatAction);
  }

  @Override
  public boolean canLoop () {
    return BitwiseUtils.hasFlag(flags, FLAG_ATTACHED);
  }

  @Override
  public void invalidateTypingPart (boolean onlyIcon) {
    ReferenceList<View> views = currentViews.getViewsList();
    for (View view : views) {
      ((ChatView) view).invalidateTypingPart(onlyIcon);
    }
  }

  @Override
  public boolean canAnimate () {
    return ((flags & FLAG_ATTACHED) != 0) && currentViews.hasAnyTargetToInvalidate() && context.getParentOrSelf().getAttachState();
  }

  private boolean isDestroyed;

  @Override
  public void performDestroy () {
    currentViews.detachFromAllViews();
    setViewAttached(false);
    isDestroyed = true;
    if (awaitingReactions != null) {
      for (String reactionKey : awaitingReactions) {
        tdlib.listeners().removeReactionLoadListener(reactionKey, this);
      }
      awaitingReactions.clear();
    }
  }

  public ReactionsCounterDrawable getReactionsCounterDrawable () {
    return reactionsCounterDrawable;
  }

  private boolean setReactions (boolean animated) {
    return setReactions(chat != null && chat.lastMessage != null && chat.lastMessage.interactionInfo != null ?
      chat.lastMessage.interactionInfo.reactions : null, animated);
  }

  private boolean setReactions (@Nullable TdApi.MessageReactions reactions, boolean animated) {
    this.reactionsListEntry.clear();

    if (!Td.isEmpty(reactions) && !isDestroyed) {
      for (TdApi.MessageReaction reaction : reactions.reactions) {
        if (TdExt.isUnsupported(reaction.type))
          continue;
        String reactionKey = TD.makeReactionKey(reaction.type);

        TGReaction reactionObj = tdlib.getReaction(reaction.type);
        if (reactionObj == null) {
          if (awaitingReactions == null) {
            awaitingReactions = new LinkedHashSet<>();
          }
          if (awaitingReactions.add(reactionKey)) {
            tdlib.listeners().addReactionLoadListener(reactionKey, this);
          }
          continue;
        }
        TGReactions.MessageReactionEntry entry = getMessageReactionEntry(reactionObj);
        entry.setMessageReaction(reaction);
        reactionsListEntry.add(entry);
        if (awaitingReactions != null && awaitingReactions.remove(reactionKey)) {
          tdlib.listeners().removeReactionLoadListener(reactionKey, this);
        }
      }
    }

    boolean r = !reactionsAnimator.compareContents(reactionsListEntry);
    reactionsAnimator.reset(reactionsListEntry, animated);
    layoutTitle(false);
    currentViews.invalidate();
    invalidateReactionsReceiver();
    return r;
  }

  private TGReactions.MessageReactionEntry getMessageReactionEntry (TGReaction reactionObj) {
    final TGReactions.MessageReactionEntry entry;
    if (!reactionsMapEntry.containsKey(reactionObj.key)) {
      entry = new TGReactions.MessageReactionEntry(tdlib, null, null, reactionObj, null) {
        @Override
        public int getBubbleTargetWidth () {
          return 0;
        }

        @Override
        public int getBubbleWidth () {
          return 0;
        }

        @Override
        public void invalidate () {
          currentViews.invalidate();
        }
      };
      reactionsMapEntry.put(reactionObj.key, entry);
    } else {
      entry = reactionsMapEntry.get(reactionObj.key);
    }
    return entry;
  }

  @Override
  public void onReactionLoaded (String reactionKey) {
    if (awaitingReactions != null && awaitingReactions.remove(reactionKey)) {
      UI.post(() -> {
        setReactions(true);
        invalidateReactionsReceiver();
      });
    }
  }
}
