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
 * File created on 28/04/2015 at 18:58
 */
package org.thunderdog.challegram.component.dialogs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibStatusManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextMedia;
import org.thunderdog.challegram.widget.BaseView;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;
import tgx.td.ChatPosition;

public class ChatView extends BaseView implements TdlibSettingsManager.PreferenceChangeListener, InvalidateContentProvider, EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate, TdlibUi.MessageProvider {
  private static Paint timePaint;
  private static TextPaint titlePaint, titlePaintFake; // counterTextPaint

  public static void reset () {
    if (titlePaint != null)
      titlePaint.setTextSize(Screen.dp(17f));
    if (titlePaintFake != null)
      titlePaintFake.setTextSize(Screen.dp(17f));
    if (timePaint != null)
      timePaint.setTextSize(Screen.dp(12f));
  }

  private static void initPaints () {
    titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    titlePaint.setColor(Theme.textAccentColor());
    titlePaint.setTextSize(Screen.dp(17f));
    titlePaint.setTypeface(Fonts.getRobotoMedium());
    ThemeManager.addThemeListener(titlePaint, ColorId.text);

    titlePaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    titlePaintFake.setColor(Theme.textAccentColor());
    titlePaintFake.setTextSize(Screen.dp(17f));
    titlePaintFake.setTypeface(Fonts.getRobotoRegular());
    titlePaintFake.setFakeBoldText(true);
    ThemeManager.addThemeListener(titlePaintFake, ColorId.text);

    timePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    timePaint.setColor(Theme.textDecentColor());
    timePaint.setTextSize(Screen.dp(12f));
    timePaint.setTypeface(Fonts.getRobotoRegular());
    ThemeManager.addThemeListener(timePaint, ColorId.textLight);
  }

  public static TextPaint getTitlePaint (boolean needFake) {
    if (titlePaintFake == null || titlePaint == null) {
      initPaints();
    }
    return needFake ? titlePaintFake : titlePaint;
  }

  // XGram: Material 3 Expressive time: labelMedium, PRIMARY and weight 700 while unread.
  private static Paint xgramTimePaint, xgramTimePaintUnread;

  private static Paint xgramTimePaint (boolean unread) {
    Paint paint = unread ? xgramTimePaintUnread : xgramTimePaint;
    if (paint == null) {
      paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      paint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, unread));
      if (unread) {
        xgramTimePaintUnread = paint;
      } else {
        xgramTimePaint = paint;
      }
    }
    paint.setTextSize(Screen.dp(12f));
    paint.setColor(com.fahadali.xgram.appearance.XgramExpressive.color(unread ? com.fahadali.xgram.appearance.XgramExpressive.PRIMARY : com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT));
    return paint;
  }

  public static Paint getTimePaint () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return xgramTimePaint(false);
    }
    if (timePaint == null) {
      synchronized (ChatView.class) {
        if (timePaint == null) {
          initPaints();
        }
      }
    }
    return timePaint;
  }

  private static int getTextOffset () {
    return Screen.dp(12f);
  }

  public static int getAvatarLeft (int chatListMode) {
    return Screen.dp(chatListMode == Settings.CHAT_MODE_1LINE ? 10f : /*inMultiLineMode ? 6f : */7f);
  }

  public static int getAvatarRadius (int chatListMode) {
    return getAvatarSize(chatListMode) / 2;
  }

  public static int getDefaultAvatarCacheSize () {
    // XGram: never below the two lines size, so the small one line photos don't make every avatar in the app blurry
    return Math.max(getAvatarSize(Settings.instance().getChatListMode()), getAvatarSize(Settings.CHAT_MODE_2LINE));
  }

  public static int getCounterRadius () {
    return Screen.dp(11f);
  }

  public static int getLeftPadding (int chatListMode) {
    if (chatListMode == Settings.CHAT_MODE_1LINE) {
      return getAvatarLeft(chatListMode) + getAvatarSize(chatListMode) + Screen.dp(12f); // XGram: 10 + 36 + 12
    }
    return chatListMode != Settings.CHAT_MODE_2LINE ? getAvatarLeft(chatListMode) + getAvatarSize(chatListMode) + Screen.dp(11f) : getViewHeight(chatListMode);
  }

  public static int getRightPadding () {
    return getTimePadding();
  }

  public static int getMuteOffset () {
    return Screen.dp(1f);
  }

  public static int getMutePadding () {
    return getMuteOffset();
  }

  public static int getTimePaddingRight () {
    return getTimePadding();
  }

  public static int getTimePaddingLeft () {
    return Screen.dp(7f);
  }

  public Receiver getAvatarReceiver () {
    return avatarReceiver;
  }

  public ComplexReceiver getTextMediaReceiver () {
    return textMediaReceiver;
  }

  private TGChat chat;
  private final AvatarReceiver avatarReceiver;
  private final android.graphics.RectF xgramCardRect = new android.graphics.RectF();
  private final ComplexReceiver emojiStatusReceiver;
  private final ComplexReceiver textMediaReceiver;
  private final ComplexReceiver reactionsReceiver;

  private final BoolAnimator isSelected = new BoolAnimator(this, com.fahadali.xgram.appearance.XgramMotion.interp(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR), com.fahadali.xgram.appearance.XgramMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_DEFAULT, 180l));
  private final RefreshRateLimiter refreshRateLimiter;

  public ChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    if (titlePaint == null) {
      initPaints();
    }
    this.refreshRateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    setId(R.id.chat);
    RippleSupport.setTransparentSelector(this);
    int chatListMode = getChatListMode();
    emojiStatusReceiver = new ComplexReceiver(this)
      .setUpdateListener(refreshRateLimiter);
    reactionsReceiver = new ComplexReceiver(this)
      .setUpdateListener(refreshRateLimiter);
    avatarReceiver = new AvatarReceiver(this)
      .setUpdateListener(refreshRateLimiter.passThroughUpdateListener());
    avatarReceiver.setAvatarRadiusPropertyIds(PropertyId.AVATAR_RADIUS_CHAT_LIST, PropertyId.AVATAR_RADIUS_CHAT_LIST_FORUM);
    avatarReceiver.setBounds(getAvatarLeft(chatListMode), getAvatarTop(chatListMode), getAvatarLeft(chatListMode) + getAvatarSize(chatListMode), getAvatarTop(chatListMode) + getAvatarSize(chatListMode));
    textMediaReceiver = new ComplexReceiver(this)
      .setUpdateListener(refreshRateLimiter);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  public void setIsSelected (boolean isSelected, boolean animated) {
    this.isSelected.setValue(isSelected, animated);
  }

  public void setAnimationsDisabled (boolean disabled) {
    avatarReceiver.setAnimationDisabled(disabled);
    textMediaReceiver.setAnimationDisabled(disabled);
    emojiStatusReceiver.setAnimationDisabled(disabled);
    reactionsReceiver.setAnimationDisabled(disabled);
  }

  public static int getViewHeight (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_1LINE:
        return Screen.dp(48f); // XGram
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(82f);
      case Settings.CHAT_MODE_3LINE:
        return Screen.dp(78f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(72f);
    }
  }

  public static float getAvatarSizeDp (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_1LINE:
        return 36f; // XGram
      case Settings.CHAT_MODE_3LINE_BIG:
        return 60f;
      case Settings.CHAT_MODE_3LINE:
        return 58f;
      case Settings.CHAT_MODE_2LINE:
      default:
        return 52f;
    }
  }

  public static int getAvatarSize (int chatListMode) {
    return Screen.dp(getAvatarSizeDp(chatListMode));
  }

  private static int getAvatarTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_1LINE:
        return Screen.dp(6f); // XGram
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(11f);
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(10f);
    }
  }

  private static int getTimePadding () {
    return Screen.dp(15f);
  }

  private static int getCounterOffset (int chatsListMode) {
    switch (chatsListMode) {
      case Settings.CHAT_MODE_1LINE:
        return Screen.dp(13f); // XGram: centred on the one row, 24dp
      case Settings.CHAT_MODE_3LINE:
        return Screen.dp(42f);
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(44f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(38f);
    }
  }

  public static int getCounterTop (int chatsListMode) {
    return getCounterOffset(chatsListMode) + getCounterRadius();
  }

  public static int getTextTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
        return Screen.dp(32f);
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(33f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(39.5f);
    }
  }

  private static int getSingleLineOffset (int chatListMode) {
    return Screen.dp(chatListMode == Settings.CHAT_MODE_3LINE ? 2f : 2f);
  }

  public static int getTitleTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return getTextOffset() + Screen.dp(14f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return getTextOffset() + Screen.dp(16f);
    }
  }

  private static int getClockTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(15f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(17f);
    }
  }

  private static int getMuteTop (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_1LINE:
        return Screen.dp(12f); // XGram: the 24dp icons centred on the row
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(9f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(11f);
    }
  }

  public static int getTitleTop2 (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_1LINE:
        // XGram: the title centred on the 48dp row, from the real title style (Roboto or Google Sans Flex metrics).
        // Measures the font: draw code reads it from metricsOf instead.
        return Screen.dp(24f) - Text.getLineHeight(TGChat.xgramOneLineTitleProvider(), false) / 2;
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return Screen.dp(10f);
      case Settings.CHAT_MODE_2LINE:
      default:
        return Screen.dp(12f);
    }
  }

  public static int getAvatarLeftFull (int chatListMode) {
    return getAvatarLeft(chatListMode) + getAvatarRadius(chatListMode);
  }

  public static int getAvatarTopFull (int chatListMode) {
    return getAvatarTop(chatListMode) + getAvatarRadius(chatListMode);
  }

  // XGram: a row's geometry as one object, so a change of the chat list view (XgramChatListMode) can spring between two.

  /** A row's geometry in px. {@link #oneLine} is 1 in one line and 0 otherwise (between the two while it changes). */
  public static final class Metrics {
    public float height, avatar, avatarLeft, avatarTop, textLeft, titleTop, timeBaseline, muteTop, clockTop,
      counterCenterY, textTop, badgeScale, oneLine;

    public void set (Metrics m) {
      height = m.height;
      avatar = m.avatar;
      avatarLeft = m.avatarLeft;
      avatarTop = m.avatarTop;
      textLeft = m.textLeft;
      titleTop = m.titleTop;
      timeBaseline = m.timeBaseline;
      muteTop = m.muteTop;
      clockTop = m.clockTop;
      counterCenterY = m.counterCenterY;
      textTop = m.textTop;
      badgeScale = m.badgeScale;
      oneLine = m.oneLine;
    }

    /** {@code from} to {@code to} at {@code f} (a spring may take it a little past 1). */
    public void lerp (Metrics from, Metrics to, float f) {
      height = from.height + (to.height - from.height) * f;
      avatar = from.avatar + (to.avatar - from.avatar) * f;
      avatarLeft = from.avatarLeft + (to.avatarLeft - from.avatarLeft) * f;
      avatarTop = from.avatarTop + (to.avatarTop - from.avatarTop) * f;
      textLeft = from.textLeft + (to.textLeft - from.textLeft) * f;
      titleTop = from.titleTop + (to.titleTop - from.titleTop) * f;
      timeBaseline = from.timeBaseline + (to.timeBaseline - from.timeBaseline) * f;
      muteTop = from.muteTop + (to.muteTop - from.muteTop) * f;
      clockTop = from.clockTop + (to.clockTop - from.clockTop) * f;
      counterCenterY = from.counterCenterY + (to.counterCenterY - from.counterCenterY) * f;
      textTop = from.textTop + (to.textTop - from.textTop) * f;
      badgeScale = from.badgeScale + (to.badgeScale - from.badgeScale) * f;
      oneLine = from.oneLine + (to.oneLine - from.oneLine) * f;
    }
  }

  private static final Metrics[] xgramModeMetrics = new Metrics[Settings.CHAT_MODE_1LINE + 1];
  private static float xgramMetricsDensity;
  private static boolean xgramMetricsExpressive;

  private static int xgramKnownMode (int chatListMode) {
    switch (chatListMode) {
      case Settings.CHAT_MODE_1LINE:
      case Settings.CHAT_MODE_3LINE:
      case Settings.CHAT_MODE_3LINE_BIG:
        return chatListMode;
      case Settings.CHAT_MODE_2LINE:
      default:
        return Settings.CHAT_MODE_2LINE; // as every switch over the mode does
    }
  }

  /** The resting geometry of a mode. Cached (per density and design), so it is cheap in onDraw. Do not modify. */
  public static Metrics metricsOf (int chatListMode) {
    final float density = Screen.density();
    final boolean expressive = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    if (density != xgramMetricsDensity || expressive != xgramMetricsExpressive) {
      xgramMetricsDensity = density;
      xgramMetricsExpressive = expressive;
      java.util.Arrays.fill(xgramModeMetrics, null);
    }
    final int mode = xgramKnownMode(chatListMode);
    Metrics m = xgramModeMetrics[mode];
    if (m == null) {
      m = new Metrics();
      m.height = getViewHeight(mode);
      m.avatar = getAvatarSize(mode);
      m.avatarLeft = getAvatarLeft(mode);
      m.avatarTop = getAvatarTop(mode);
      m.textLeft = getLeftPadding(mode);
      m.titleTop = getTitleTop2(mode);
      m.timeBaseline = getTitleTop(mode);
      m.muteTop = getMuteTop(mode);
      m.clockTop = getClockTop(mode);
      m.counterCenterY = getCounterTop(mode);
      m.textTop = mode == Settings.CHAT_MODE_1LINE ? getViewHeight(mode) : getTextTop(mode);
      // The online dot, schedule clock, check and community stack: at the two lines size from 52dp photos up, 3/4 at most smaller
      m.badgeScale = Math.max(.75f, Math.min(1f, m.avatar / (float) getAvatarSize(Settings.CHAT_MODE_2LINE)));
      m.oneLine = mode == Settings.CHAT_MODE_1LINE ? 1f : 0f;
      xgramModeMetrics[mode] = m;
    }
    return m;
  }

  /** This row's geometry now: the chat list view's spring while it changes, the row's own mode otherwise. */
  private Metrics xgramMetrics () {
    final TGChat chat = this.chat;
    if (chat == null || chat.followsGlobalMode()) {
      return com.fahadali.xgram.chats.XgramChatListMode.metrics(getChatListMode());
    }
    return metricsOf(getChatListMode());
  }

  /** XGram: the row's height now (it springs while the chat list view changes), for the list's decorations. */
  public static int xgramCurrentHeight (@Nullable TGChat chat) {
    if (chat == null || chat.followsGlobalMode()) {
      return Math.round(com.fahadali.xgram.chats.XgramChatListMode.metrics(chat != null ? chat.getListMode() : Settings.instance().getChatListMode()).height);
    }
    return getViewHeight(chat.getListMode());
  }

  /** XGram: where Telegram X's row separator starts: 72dp, the text start (58dp) in one line. */
  public int xgramSeparatorLeft () {
    final float oneLine = Math.max(0f, Math.min(1f, xgramMetrics().oneLine));
    final int multi = Screen.dp(72f), single = getLeftPadding(Settings.CHAT_MODE_1LINE);
    return Math.round(multi + (single - multi) * oneLine);
  }

  public void attach () {
    avatarReceiver.attach();
    textMediaReceiver.attach();
    emojiStatusReceiver.attach();
    reactionsReceiver.attach();
  }

  public void detach () {
    avatarReceiver.detach();
    textMediaReceiver.detach();
    emojiStatusReceiver.detach();
    reactionsReceiver.detach();
  }

  public void setChat (TGChat chat) {
    if (this.chat != chat) {
      if (this.chat != null) {
        this.chat.detachFromView(this);
        if (this.chat.isArchive()) {
          this.tdlib.settings().removeUserPreferenceChangeListener(this);
        }
      }
      this.chat = chat;
      this.isPinnedArchive.setValue(chat != null && chat.isArchive() && !tdlib.settings().needHideArchive(), false);
      if (chat != null) {
        chat.checkLayout(getMeasuredWidth());
        chat.attachToView(this);
        if (chat.isArchive()) {
          this.tdlib.settings().addUserPreferenceChangeListener(this);
        }
      }
      if (chat != null) {
        chat.onAttachToView();
      }
    }
    // XGram: every time, not only for another chat: a row can become (or stop being) its community's row in place.
    updatePreviewProvider();
    requestContent();
  }

  /** XGram: the row became or stopped being its community's row; preview, avatar and texts follow. */
  public void xgramRebind () {
    updatePreviewProvider();
    requestContent();
    invalidate();
  }

  private void updatePreviewProvider () {
    TGChat chat = this.chat;
    if (chat != null && chat.xgramCommunityId() == 0) {
      setPreviewChatId(chat.getChatList(), chat.getChatId(), null);
    } else {
      setPreviewChatId(null, 0, null);
    }
    if (chat != null && chat.xgramCommunityId() != 0) {
      // XGram: holding a community row previews the community, not one of its chats.
      final long communityId = chat.xgramCommunityId();
      setCustomControllerProvider(new CustomControllerProvider() {
        @Override
        public boolean needsForceTouch (BaseView v, float x, float y) {
          return Settings.instance().needPreviewChatOnHold();
        }

        @Override
        public boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview) {
          return false;
        }

        @Override
        public ViewController<?> createForceTouchPreview (BaseView v, float x, float y) {
          com.fahadali.xgram.chats.XgramCommunityController c = new com.fahadali.xgram.chats.XgramCommunityController(getContext(), tdlib);
          c.setArguments(communityId);
          return c;
        }
      });
    } else if (chat != null && chat.isArchive()) {
      setCustomControllerProvider(new CustomControllerProvider() {
        @Override
        public boolean needsForceTouch (BaseView v, float x, float y) {
          return Settings.instance().needPreviewChatOnHold();
        }

        @Override
        public boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview) {
          return false;
        }

        @Override
        public ViewController<?> createForceTouchPreview (BaseView v, float x, float y) {
          ChatsController c = new ChatsController(getContext(), tdlib);
          c.setArguments(new ChatsController.Arguments(ChatPosition.CHAT_LIST_ARCHIVE).setNeedMessagesSearch(true));
          return c;
        }
      });
    } else {
      setCustomControllerProvider(null);
    }
  }

  @Override
  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    requestEmojiStatus();
  }

  private void requestEmojiStatus () {
    EmojiStatusHelper.EmojiStatusDrawable text = chat != null ? chat.getEmojiStatus() : null;
    if (text != null) {
      text.requestMedia(emojiStatusReceiver);
    } else {
      emojiStatusReceiver.clear();
    }
  }

  public ComplexReceiver getReactionsReceiver () {
    return reactionsReceiver;
  }

  public void requestReactionFiles () {
    if (chat != null) {
      chat.requestReactionFiles(reactionsReceiver);
    } else {
      reactionsReceiver.clear();
    }
  }

  private void requestTextContent () {
    Text text = chat != null ? chat.getText() : null;
    if (text == null && chat != null) {
      text = chat.xgramFadeText(); // XGram: the old lines fade out with their custom emoji while the new mode has no text
    }
    if (text != null) {
      text.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private void requestContent () {
    requestTextContent();
    requestEmojiStatus();
    requestReactionFiles();
    if (chat != null && chat.xgramCommunityId() != 0) {
      xgramRequestCommunityAvatar();
    } else if (chat != null) {
      AvatarPlaceholder.Metadata avatarPlaceholder = chat.getAvatarPlaceholder();
      if (avatarPlaceholder != null) {
        avatarReceiver.requestPlaceholder(tdlib, avatarPlaceholder, AvatarReceiver.Options.SHOW_ONLINE);
      } else {
        avatarReceiver.requestChat(tdlib, chat.getChatId(), AvatarReceiver.Options.SHOW_ONLINE);
      }
    } else {
      avatarReceiver.clear();
    }
  }

  public boolean needAnimateChanges () {
    ViewController<?> c = ViewController.findAncestor(ChatView.this);
    return c == null || c.getParentOrSelf().getAttachState();
  }

  // XGram: a community row shows the community's photo (or its letters) in a rounded square.
  private void xgramRequestCommunityAvatar () {
    TdApi.Community community = tdlib.cache().community(chat.xgramCommunityId());
    if (community != null && community.photo != null) {
      avatarReceiver.requestSpecific(tdlib, community.photo, AvatarReceiver.Options.FORCE_FORUM);
    } else {
      String name = community != null && !community.name.isEmpty() ? community.name : tdlib.chatTitle(chat.getChatId());
      avatarReceiver.requestPlaceholder(tdlib, new AvatarPlaceholder.Metadata(tdlib.chatAccentColor(chat.getChatId()), org.thunderdog.challegram.data.TD.getLetters(name)), AvatarReceiver.Options.FORCE_FORUM);
    }
  }

  public void invalidateAvatarReceiver () {
    if (chat != null && chat.xgramCommunityId() != 0) {
      xgramRequestCommunityAvatar();
    } else if (chat != null) {
      avatarReceiver.requestChat(tdlib, chat.getChatId(), AvatarReceiver.Options.SHOW_ONLINE);
    } else {
      avatarReceiver.clear();
    }
    invalidate();
  }

  private boolean needBackground;

  public void setNeedBackground (boolean needBackground) {
    if (this.needBackground != needBackground) {
      this.needBackground = needBackground;
      if (needBackground) {
        RippleSupport.setSimpleWhiteBackground(this);
      } else {
        RippleSupport.setTransparentSelector(this);
      }
    }
  }

  private boolean isDragging;

  public void setIsDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        // XGram M3E: the dragged row is a raised tile (see xgramDrawDragTile): its shadow takes the tile's shape
        if (isDragging) {
          setOutlineProvider(xgramDragOutline());
          setTranslationZ(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.ELEVATION_3));
        } else {
          setTranslationZ(0f);
          setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }
      }
      invalidate();
    }
  }

  /**
   * XGram M3E: the press and selection tile's horizontal inset (XgramM3Profile.drawSelectionTile), so the dragged
   * tile lines up with it.
   */
  private static final float XGRAM_TILE_INSET_X = 4f, XGRAM_TILE_INSET_Y = 2f;
  private static @Nullable android.view.ViewOutlineProvider xgramDragOutline;

  private static android.view.ViewOutlineProvider xgramDragOutline () {
    if (xgramDragOutline == null) {
      xgramDragOutline = new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (android.view.View view, android.graphics.Outline outline) {
          int insetX = Screen.dp(XGRAM_TILE_INSET_X), insetY = Screen.dp(XGRAM_TILE_INSET_Y);
          outline.setRoundRect(insetX, insetY, view.getWidth() - insetX, view.getHeight() - insetY, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L));
        }
      };
    }
    return xgramDragOutline;
  }

  /** XGram M3E: the dragged row, a tile in surfaceContainerHigh with the 16% dragged state layer. */
  private static int xgramDragTileColor () {
    return ColorUtils.compositeColor(
      com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH),
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), com.fahadali.xgram.appearance.XgramExpressive.STATE_DRAGGED)
    );
  }

  private void xgramDrawDragTile (Canvas c, int viewWidth, int color) {
    RectF rect = Paints.getRectF();
    rect.set(Screen.dp(XGRAM_TILE_INSET_X), Screen.dp(XGRAM_TILE_INSET_Y), viewWidth - Screen.dp(XGRAM_TILE_INSET_X), getMeasuredHeight() - Screen.dp(XGRAM_TILE_INSET_Y));
    float radius = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_L);
    c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color));
  }

  public boolean isDragging () {
    return isDragging;
  }

  private int getChatListMode () {
    return chat != null ? chat.getListMode() : Settings.instance().getChatListMode();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    // XGram: the height springs while the chat list view changes (XgramChatListMode); the list asks for a layout each frame
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.round(xgramMetrics().height), MeasureSpec.EXACTLY));
    layoutReceiver();
    if (chat != null && chat.checkLayout(getMeasuredWidth())) {
      requestContent();
    }
  }

  public boolean canStartDrag (float x, float y) {
    // XGram: the photo's column (2 x its inset + the photo), 56dp in one line: the pinned drag and the long press selection
    final Metrics m = xgramMetrics();
    int threshold = Math.round(m.avatarLeft * 2f + m.avatar);
    if (Lang.rtl()) {
      return x >= getMeasuredWidth() - threshold;
    } else {
      return x <= threshold;
    }
  }

  private void layoutReceiver () {
    final Metrics m = xgramMetrics();
    final int size = Math.round(m.avatar), avatarLeft = Math.round(m.avatarLeft), top = Math.round(m.avatarTop);
    int left = Lang.rtl() ? getMeasuredWidth() - avatarLeft - size : avatarLeft;
    avatarReceiver.setBounds(left, top, left + size, top + size);
    avatarReceiver.xgramSetBadgeScale(m.badgeScale); // XGram: the online dot follows the smaller one line photo
  }

  public TGChat getChat () {
    return chat;
  }

  public long getChatId () {
    return chat != null ? chat.getChatId() : 0;
  }

  public void invalidateTypingPart (boolean onlyIcon) {
    invalidate(); // TODO invalidate only needed part
  }

  @Override
  public void onPreferenceChanged(Tdlib tdlib, long key, boolean value) {
    if (chat != null && chat.isArchive() && key == TdlibSettingsManager.PREFERENCE_HIDE_ARCHIVE) {
      isPinnedArchive.setValue(!value, true);
    }
  }

  private final BoolAnimator isPinnedArchive = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  @Override
  public boolean invalidateContent (Object cause) {
    if (this.chat == cause) {
      requestTextContent();
      requestEmojiStatus();
      requestReactionFiles();
      return true;
    }
    return false;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (chat == null) {
      return;
    }

    final int chatListMode = getChatListMode();

    boolean rtl = Lang.rtl();
    int viewWidth = getMeasuredWidth();

    // XGram: the row's geometry (see Metrics). While the chat list view changes (XgramChatListMode) it springs from the
    // old mode to the new one: the photo, the title row and the counters move, and the lines under the title that only
    // one of the two modes has fade through (the old mode's out over the first half, the new mode's in over the second).
    final Metrics xgramMetrics = xgramMetrics();
    final boolean oneLineMode = chatListMode == Settings.CHAT_MODE_1LINE;
    final float oneLine = clamp01(xgramMetrics.oneLine);
    final boolean modeTransition = chat.followsGlobalMode() && com.fahadali.xgram.chats.XgramChatListMode.isAnimating() &&
      chatListMode == com.fahadali.xgram.chats.XgramChatListMode.targetMode();
    final float modeFraction = modeTransition ? clamp01(com.fahadali.xgram.chats.XgramChatListMode.fraction()) : 1f;
    final float linesInAlpha = modeTransition ? clamp01(modeFraction * 2f - 1f) : 1f;
    // An interrupted change starts from where the old lines were (TGChat.xgramFadeStartAlpha)
    final float linesOutAlpha = modeTransition && chat.xgramFadeMode() != 0 ? chat.xgramFadeStartAlpha() * clamp01(1f - modeFraction * 2f) : 0f;
    // The title row and the lines under it move together: the title's shift from where the row's own mode puts it
    final int titleDx = Math.round(xgramMetrics.textLeft) - getLeftPadding(chatListMode);
    final int titleTop = Math.round(xgramMetrics.titleTop);
    final int clipSaveCount;
    if (modeTransition) {
      clipSaveCount = Views.save(c);
      c.clipRect(0, 0, viewWidth, getMeasuredHeight());
    } else {
      clipSaveCount = -1;
    }

    final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    // XGram M3E: what lies under the photo, for the cut-outs of its online dot, schedule clock and check: the row's
    // surface, the selection / open-chat tile over it, or the dragged tile
    int xgramCutOut = 0;
    if (xgramM3) {
      if (isDragging) {
        xgramCutOut = xgramDragTileColor();
      } else {
        float tile = Math.max(com.fahadali.xgram.layout.XgramTwoPane.isHighlighted(tdlib, chat.getChatId()) ? 1f : 0f, Math.max(0f, Math.min(1f, isSelected.getFloatValue())));
        xgramCutOut = tile > 0f ?
          ColorUtils.compositeColor(Theme.fillingColor(), com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), tile)) :
          Theme.fillingColor();
      }
    }
    if (isDragging) {
      if (xgramM3) {
        xgramDrawDragTile(c, viewWidth, xgramCutOut); // XGram M3E: a raised tile, as the press and selection tiles
      } else {
        c.drawColor(ColorUtils.alphaColor(.8f, Theme.fillingColor()));
      }
    } else if (com.fahadali.xgram.layout.XgramTwoPane.isHighlighted(tdlib, chat.getChatId())) {
      if (xgramM3) {
        com.fahadali.xgram.appearance.XgramM3Profile.drawSelectionTile(c, viewWidth, getMeasuredHeight(), 1f); // XGram: a rounded tile
      } else {
        c.drawColor(ColorUtils.alphaColor(.6f, Theme.getColor(ColorId.fillingPressed))); // XGram: chat open in the right pane, the ripple stays visible
      }
    }
    if (xgramM3 && !isDragging) {
      com.fahadali.xgram.appearance.XgramM3Profile.drawSelectionTile(c, viewWidth, getMeasuredHeight(), isSelected.getFloatValue());
    }

    TdlibStatusManager.Helper status = chat.statusHelper();
    TdlibStatusManager.ChatState state = status != null ? status.drawingState() : null;
    float statusVisibility = state != null ? state.visibility() : 0f;

    // XGram: in one line the typing icon takes the time's place (there is no line under the title for it): a fade
    // through with Expressive design, Telegram X's crossfade otherwise
    float timeAlpha = 1f, oneLineStatusAlpha = 0f;
    if (oneLineMode && state != null && statusVisibility > 0f) {
      final float visibility = clamp01(statusVisibility);
      if (xgramM3) {
        timeAlpha = visibility >= .5f ? 0f : 1f - visibility * 2f;
        oneLineStatusAlpha = visibility <= .5f ? 0f : visibility * 2f - 1f;
      } else {
        timeAlpha = 1f - visibility;
        oneLineStatusAlpha = visibility;
      }
    }
    final float timeBaseline = xgramMetrics.timeBaseline;
    if (timeAlpha > 0f) {
      if (xgramM3) {
        boolean unread = chat.xgramHasUnread();
        Paint paint = xgramTimePaint(unread);
        float timeWidth = unread ? paint.measureText(chat.getTime()) : chat.getTimeWidth();
        float timeX = rtl ? viewWidth - chat.getTimeLeft() - chat.getTimeWidth() : chat.getTimeLeft() + chat.getTimeWidth() - timeWidth;
        xgramDrawTime(c, chat.getTime(), timeX, timeBaseline, paint, timeAlpha);
      } else {
        xgramDrawTime(c, chat.getTime(), (rtl ? viewWidth - chat.getTimeLeft() - chat.getTimeWidth() : chat.getTimeLeft()), timeBaseline, timePaint, timeAlpha);
      }
    }
    if (oneLineStatusAlpha > 0f) {
      final TdApi.ChatAction action = state.action();
      if (action != null) {
        // Its end edge on the time's: the title never reflows, one line keeps 24dp for the time (TGChat.layoutTime)
        final float timeEnd = chat.getTimeLeft() + chat.getTimeWidth();
        final int statusWidth = DrawAlgorithms.getStatusWidth(action);
        final float reserve = timeEnd - chat.getChecksRight() - getTimePaddingLeft(); // TGChat.xgramTimeReserve
        final float startX = timeEnd - statusWidth, cy = timeBaseline - Screen.dp(4f);
        final int color = xgramM3 ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY) : Theme.getColor(ColorId.textLight);
        // Choosing a location (36dp of waves) would reach over the ticks, the views or the draft pencil: it is made
        // to fit the reserve instead (the 26dp upload bars draw within it)
        final int save = statusWidth > reserve && reserve > 0f && action.getConstructor() == TdApi.ChatActionChoosingLocation.CONSTRUCTOR ? Views.save(c) : -1;
        if (save != -1) {
          final float s = reserve / statusWidth;
          c.scale(s, s, rtl ? viewWidth - timeEnd : timeEnd, cy);
        }
        DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - startX : startX, cy, ColorUtils.alphaColor(oneLineStatusAlpha, color), this, !xgramM3 && oneLineStatusAlpha == 1f ? ColorId.textLight : ColorId.NONE);
        if (save != -1) {
          Views.restore(c, save);
        }
      }
    }

    // XGram: in RTL Telegram X draws the title and its emoji status from the start without mirroring them, and mirrors
    // the rest; one line has the ticks and badges on the title's row, so there the whole row is mirrored. While the view
    // changes the mirroring follows the spring (0 unmirrored, 1 mirrored), so nothing jumps across the row.
    final float mirror = rtl ? oneLine : 0f;
    Text title = chat.getTitle();
    if (title != null) {
      int titleX = getLeftPadding(chatListMode) + titleDx;
      if (chat.isSecretChat()) {
        Drawable d = Icons.getSecureDrawable();
        Drawables.drawRtl(c, d, titleX - Screen.dp(7f), titleTop + title.getHeight() / 2 - d.getMinimumHeight() / 2, Paints.getGreenPorterDuffPaint(), viewWidth, rtl);
        titleX += Screen.dp(14f);
      }

      title.draw(c, mirror > 0f ? Math.round(titleX + (viewWidth - 2f * titleX - title.getWidth()) * mirror) : titleX, titleTop);
    }

    EmojiStatusHelper.EmojiStatusDrawable emojiStatus = chat.getEmojiStatus();
    if (emojiStatus != null) {
      final int emojiStatusLeft = chat.getEmojiStatusLeft() + titleDx;
      emojiStatus.draw(c, mirror > 0f ? Math.round(emojiStatusLeft + (viewWidth - 2f * emojiStatusLeft - emojiStatus.getWidth()) * mirror) : emojiStatusLeft, titleTop, 1f, emojiStatusReceiver);
    }

    final int muteTop = Math.round(xgramMetrics.muteTop);
    if (chat.showVerify()) {
      Drawables.drawRtl(c, Icons.getChatVerifyDrawable(), chat.getVerifyLeft() + titleDx, muteTop, Paints.getVerifyPaint(), viewWidth, rtl);
    }

    if ((chat.showScam() || chat.showFake()) && chat.getChatMark() != null) {
      int additionalPadding = Screen.dp(4f);

      int chatMarkLeft = chat.getVerifyLeft() + titleDx + Screen.dp(10f);
      if (mirror > 0f) {
        chatMarkLeft = Math.round(chatMarkLeft + (viewWidth - 2f * chatMarkLeft - chat.getChatMark().getWidth()) * mirror);
      }
      int chatMarkY = titleTop + Screen.dp(0.5f);

      RectF rct = Paints.getRectF();
      rct.set(chatMarkLeft - additionalPadding, chatMarkY, chatMarkLeft + chat.getChatMark().getWidth() + additionalPadding, chatMarkY + chat.getChatMark().getLineHeight(true));
      c.drawRoundRect(rct, Screen.dp(2f), Screen.dp(2f), Paints.getProgressPaint(Theme.getColor(ColorId.textNegative), Screen.dp(1.5f)));

      chat.getChatMark().draw(c, chatMarkLeft, chatMarkY + Screen.dp(1f));
    }

    if (chat.showMute()) {
      Drawables.drawRtl(c, Icons.getChatMuteDrawable(ColorId.chatListMute), chat.getMuteLeft() + titleDx, muteTop, Paints.getChatsMutePaint(), viewWidth, rtl);
    }

    // The ticks (or the sending clock, the views, the draft in one line) and the reactions preview, from the time to
    // the start; trailingX ends at the start edge of what was drawn, where one line's counters go
    final int clockTop = Math.round(xgramMetrics.clockTop);
    int trailingX = chat.getChecksRight() + getTimePaddingLeft();
    if (chat.isSending()) {
      int x = chat.getChecksRight() - Screen.dp(10f) - Screen.dp(Icons.CLOCK_SHIFT_X);
      Drawables.drawRtl(c, Icons.getClockIcon(ColorId.iconLight), x, clockTop - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint(), viewWidth, rtl);
      trailingX = chat.getChecksRight() - Screen.dp(20f);
    } else {
      int x = chat.getChecksRight();
      int y = clockTop;
      boolean drawn = false;
      if (chat.isOutgoing() && !chat.isSelfChat()) {
        if (chat.showViews()) {
          y -= Screen.dp(.5f);
        } else if (chat.isUnread()) {
          x += Screen.dp(4f);
        }
        if (chat.showViews()) {
          chat.getViewCounter().draw(c, x + Screen.dp(3f), y + Screen.dp(14f) / 2f, Gravity.RIGHT, 1f, this, ColorId.ticksRead);
          x -= chat.getViewCounter().getScaledWidth(Screen.dp(3));
        } else {
          int iconX = x - Screen.dp(Icons.TICKS_SHIFT_X) - Screen.dp(14f);
          boolean unread = chat.isUnread();
          Drawables.drawRtl(c, unread ? Icons.getSingleTick(ColorId.ticks) : Icons.getDoubleTick(ColorId.ticks), iconX, y - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint(), viewWidth, rtl);
          x -= Screen.dp(24 - 8 + 3);
        }
        drawn = true;
      } else if (oneLineMode && chat.showDraft()) {
        // XGram: one line has no "Draft:" line, a pencil in the error color stands where the ticks would
        Drawable d = getSparseDrawable(R.drawable.baseline_edit_12, ColorId.NONE);
        if (d != null) {
          final float iconLeft = x - d.getMinimumWidth();
          final float cx = (rtl ? viewWidth - iconLeft - d.getMinimumWidth() : iconLeft) + d.getMinimumWidth() / 2f;
          final Paint paint = xgramM3 ? xgramIconPaint(XGRAM_PAINT_DRAFT, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ERROR)) : PorterDuffPaint.get(ColorId.textNegative);
          // On the title row with the ticks and the time (24dp down at rest), fading in with one line as its pin does
          com.fahadali.xgram.appearance.XgramIconCrossfade.drawSingle(c, d, paint, cx, xgramMetrics.clockTop + Screen.dp(7f), oneLine);
          x -= d.getMinimumWidth();
          drawn = true;
        }
      }
      if (chat.needDrawReactionsPreview()) {
        float reactionsX = x - chat.getReactionsWidth();
        reactionsX += (viewWidth - x - reactionsX) * mirror;
        chat.getReactionsCounterDrawable().draw(c, Math.round(reactionsX), y + Screen.dp(6f));
        int reactionsWidth = chat.getReactionsWidth();
        if (reactionsWidth > 0) {
          x -= reactionsWidth + Screen.dp(3f);
          drawn = true;
        }
      }
      if (drawn) {
        trailingX = x;
      }
    }

    // Counters: on the second line at the end, or in one line before the ticks, 8dp from them (from the time when
    // there are none), sliding between the two while the view changes. Right to left: unread, mention, reaction.
    Counter counter = chat.getCounter();
    Counter mentionCounter = chat.getMentionCounter();
    Counter reactionCounter = chat.getReactionsCounter();
    final float counterRadius = getCounterRadius();
    final float counterCenterY = xgramMetrics.counterCenterY;
    final float oneLineRight = trailingX - Screen.dp(8f);
    final int reactionColorId = chat.notificationsEnabled() ? ColorId.badgeText : ColorId.badgeMutedText;
    if (rtl) {
      // Mirrored in one line: from the start edge of the slot towards the title. Each badge slides between its place
      // at the end of the second line and its one line place (both as Gravity.RIGHT anchors), so none jumps mid-change.
      float multiLineRight = viewWidth - getRightPadding(), counterLeft = viewWidth - oneLineRight;
      counter.draw(c, xgramRtlCounterX(counter, multiLineRight, counterLeft, counterRadius, oneLine), counterCenterY, Gravity.RIGHT, 1f);
      multiLineRight -= counter.getScaledWidth(getTimePaddingLeft());
      counterLeft += counter.getScaledWidth(getTimePaddingLeft());
      mentionCounter.draw(c, xgramRtlCounterX(mentionCounter, multiLineRight, counterLeft, counterRadius, oneLine), counterCenterY, Gravity.RIGHT, 1f, this, ColorId.badgeText);
      multiLineRight -= mentionCounter.getScaledWidth(getTimePaddingLeft());
      counterLeft += mentionCounter.getScaledWidth(getTimePaddingLeft());
      reactionCounter.draw(c, xgramRtlCounterX(reactionCounter, multiLineRight, counterLeft, counterRadius, oneLine), counterCenterY, Gravity.RIGHT, 1f, this, reactionColorId);
      multiLineRight -= reactionCounter.getScaledWidth(getTimePaddingLeft());
      counterLeft += reactionCounter.getScaledWidth(getTimePaddingLeft());
      Counter bellCounter = chat.xgramLaterCounter(); // XGram Later: the bell, colored by its own set
      bellCounter.draw(c, xgramRtlCounterX(bellCounter, multiLineRight, counterLeft, counterRadius, oneLine), counterCenterY, Gravity.RIGHT, 1f, this, ColorId.NONE);
    } else {
      final float multiLineRight = viewWidth - getRightPadding();
      float counterRight = oneLine <= 0f ? multiLineRight : multiLineRight + (oneLineRight - multiLineRight) * oneLine;
      counter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f);
      counterRight -= counter.getScaledWidth(getTimePaddingLeft());

      mentionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f, this, ColorId.badgeText);
      counterRight -= mentionCounter.getScaledWidth(getTimePaddingLeft());

      reactionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f, this, reactionColorId);
      counterRight -= reactionCounter.getScaledWidth(getTimePaddingLeft());

      Counter bellCounter = chat.xgramLaterCounter(); // XGram Later: the bell, colored by its own set
      bellCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, 1f, this, ColorId.NONE);
    }

    if (oneLine > 0f && chat.xgramIsPinnedRow()) {
      // XGram: one line shows a pin where there is no badge (official Telegram); it gives way as a badge comes in
      final float badges = Math.max(Math.max(counter.getVisibility(), chat.xgramLaterCounter().getVisibility()), Math.max(mentionCounter.getVisibility(), reactionCounter.getVisibility())); // XGram Later: the bell too
      final float pinAlpha = (1f - badges) * oneLine;
      Drawable d = pinAlpha > 0f ? getSparseDrawable(R.drawable.deproko_baseline_pin_14, ColorId.NONE) : null;
      if (d != null) {
        final float iconLeft = oneLineRight - d.getMinimumWidth();
        final float cx = (rtl ? viewWidth - iconLeft - d.getMinimumWidth() : iconLeft) + d.getMinimumWidth() / 2f;
        final Paint paint = xgramM3 ? xgramIconPaint(XGRAM_PAINT_PIN, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT)) : PorterDuffPaint.get(ColorId.iconLight);
        // Full alpha inside a layer: a VectorDrawable drawn at a per-draw alpha takes the frame's last one (XgramIconCrossfade)
        com.fahadali.xgram.appearance.XgramIconCrossfade.drawSingle(c, d, paint, cx, counterCenterY, pinAlpha);
      }
    }

    float textAlpha = 1f - statusVisibility;
    float statusAlpha = statusVisibility;
    float statusShift = 1f;
    if (xgramM3) {
      // XGram M3E: the typing status replaces the lines under it with a fade through (M3 content swap): the topic /
      // sender and preview lines fade out over the first half, the status fades in over the second, so the two are
      // never drawn over each other, and each ends at exactly 0 or 1. Reduce motion: fades only, no slide.
      statusVisibility = statusVisibility <= 0f ? 0f : statusVisibility >= 1f ? 1f : statusVisibility;
      textAlpha = statusVisibility >= .5f ? 0f : 1f - statusVisibility * 2f;
      statusAlpha = statusVisibility <= .5f ? 0f : statusVisibility * 2f - 1f;
      if (com.fahadali.xgram.appearance.XgramMotion.reduce()) {
        statusShift = 0f;
      }
    }
    // XGram: the old mode's lines fading out while the chat list view changes (TGChat keeps them until it ends), at the
    // old mode's places shifted with the title row. Custom emoji only while the new mode has no text of its own. While
    // the chat is typing the old mode hid them as well (textAlpha), so they stay hidden instead of flashing in.
    final float stashAlpha = linesOutAlpha * clamp01(textAlpha);
    if (stashAlpha > 0f) {
      final Metrics fadeMetrics = metricsOf(chat.xgramFadeMode());
      xgramDrawLines(c, chat.xgramFadeMode(), chat.xgramFadePrefix(), chat.xgramFadeText(), chat.xgramFadeTextLeft(),
        chat.xgramFadeIcons(), chat.xgramFadeIconColorId(), stashAlpha, true, chat.getText() == null ? textMediaReceiver : null,
        Math.round(xgramMetrics.textLeft - fadeMetrics.textLeft), Math.round(xgramMetrics.titleTop - fadeMetrics.titleTop), viewWidth, rtl);
    }
    final Metrics ownMetrics = modeTransition ? metricsOf(chatListMode) : xgramMetrics;
    final int linesDy = modeTransition ? Math.round(xgramMetrics.titleTop - ownMetrics.titleTop) : 0;
    final float linesAlpha = textAlpha * linesInAlpha;
    if (linesAlpha > 0f && !oneLineMode) {
      final int dy = (int) (Screen.dp(14f) * statusVisibility * statusShift);
      // Mid-change the icons go through a layer, as the old lines may draw the same drawable at another alpha
      xgramDrawLines(c, chatListMode, chat.getPrefix(), chat.getText(), chat.getTextLeft(), chat.getTextIconIds(), chat.getTextIconColorId(),
        linesAlpha, modeTransition, textMediaReceiver, titleDx, linesDy + dy, viewWidth, rtl);
    }
    final float statusLinesAlpha = statusAlpha * linesInAlpha;
    if (statusLinesAlpha > 0f && !oneLineMode) {
      Text text = status.drawingText();
      if (text != null) {
        float top = getTextTop(chatListMode) + linesDy - Screen.dp(14f) * (xgramM3 ? (1f - statusVisibility) * statusShift : textAlpha);
        if (chatListMode != Settings.CHAT_MODE_2LINE && text.getLineCount() == 1) {
          top += getSingleLineOffset(chatListMode);
        }
        final int x = getLeftPadding(chatListMode) + titleDx;
        DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - x : x, top + text.getLineHeight() / 2f, ColorUtils.alphaColor(statusLinesAlpha, text.getTextColor()), this, statusLinesAlpha == 1f ? ColorId.textLight : ColorId.NONE);
        text.draw(c, x, (int) top, null, statusLinesAlpha);
      }
    }

    avatarReceiver.forceAllowOnline(!isSelected.getValue(), 1f - isSelected.getFloatValue());
    layoutReceiver();
    if (chat.xgramCommunityId() != 0) {
      // XGram: two cards behind the photo, the community's other chats (as in Telegram).
      float radius = avatarReceiver.getDisplayRadius();
      int color = Theme.getColor(ColorId.text);
      // M3E: a single tonal card, see xgramDrawCommunityStack.
      boolean m3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
      final float badgeScale = xgramMetrics.badgeScale; // XGram: smaller with the one line photo
      if (m3) {
        xgramDrawCommunityStack(c, radius, rtl, badgeScale);
      } else
      for (int i = m3 ? 1 : 2; i >= 1; i--) {
        float shift = m3 ? Screen.dp(6f) * (rtl ? -1 : 1) : Screen.dp(4f) * i * badgeScale * (rtl ? 1 : -1), inset = m3 ? Screen.dp(4f) : Screen.dp(3.5f) * i * badgeScale;
        xgramCardRect.set(avatarReceiver.getLeft() + shift, avatarReceiver.getTop() + inset, avatarReceiver.getRight() + shift, avatarReceiver.getBottom() - inset);
        int cardColor = m3 ?
          com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER) :
          ColorUtils.alphaColor(i == 1 ? .14f : .07f, color);
        c.drawRoundRect(xgramCardRect, radius, radius, Paints.fillingPaint(cardColor));
      }
    }
    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.setContentCutOutColor(xgramCutOut);
    avatarReceiver.draw(c);

    final int cutOutColor = xgramCutOut != 0 ? xgramCutOut : Theme.fillingColor();
    // XGram: the schedule clock and the selection check at the photo's scale (3/4 with the one line photo)
    DrawAlgorithms.drawIcon(c, avatarReceiver, 315f, chat.getScheduleAnimator().getFloatValue(), cutOutColor, getSparseDrawable(R.drawable.baseline_watch_later_10, ColorId.badgeMuted), PorterDuffPaint.get(ColorId.badgeMuted, chat.getScheduleAnimator().getFloatValue()), xgramMetrics.badgeScale);
    DrawAlgorithms.drawSimplestCheckBox(c, avatarReceiver, isSelected.getFloatValue(), cutOutColor, xgramMetrics.badgeScale);

    if (clipSaveCount != -1) {
      Views.restore(c, clipSaveCount);
    }
  }

  private static float clamp01 (float value) {
    return value < 0f ? 0f : value > 1f ? 1f : value;
  }

  /**
   * XGram, right to left: a badge's Gravity.RIGHT anchor between its multi-line place ({@code multiLineRight}, its end
   * edge) and its mirrored one line place ({@code oneLineLeft}, its start edge), at {@code oneLine}.
   */
  private static float xgramRtlCounterX (Counter badge, float multiLineRight, float oneLineLeft, float radius, float oneLine) {
    final float multiLine = multiLineRight - radius;
    if (oneLine <= 0f) {
      return multiLine;
    }
    final float single = oneLineLeft + badge.getWidth() - radius;
    return multiLine + (single - multiLine) * oneLine;
  }

  /** XGram: the time at {@code alpha} (one line fades it for the typing icon), the shared paint's alpha put back after. */
  private static void xgramDrawTime (Canvas c, String time, float x, float baseline, Paint paint, float alpha) {
    if (alpha >= 1f) {
      c.drawText(time, x, baseline, paint);
      return;
    }
    final int paintAlpha = paint.getAlpha();
    paint.setAlpha(Math.round(paintAlpha * alpha));
    c.drawText(time, x, baseline, paint);
    paint.setAlpha(paintAlpha);
  }

  private static final int XGRAM_PAINT_DRAFT = 0, XGRAM_PAINT_PIN = 1;
  private static final Paint[] xgramIconPaints = new Paint[2];
  private static final int[] xgramIconPaintColors = new int[2];

  /**
   * XGram M3E: a color filter paint for a role color (PorterDuffPaint only takes the color ids it was made for), one
   * per icon, so its filter is made again only when the theme changes the role, never per frame.
   */
  private static Paint xgramIconPaint (int slot, int color) {
    Paint paint = xgramIconPaints[slot];
    if (paint == null) {
      paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
      paint.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
      xgramIconPaints[slot] = paint;
      xgramIconPaintColors[slot] = color;
    } else if (xgramIconPaintColors[slot] != color) {
      xgramIconPaintColors[slot] = color;
      paint.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
    }
    return paint;
  }

  /**
   * The lines under the title: the prefix (sender, topic, "Draft"), the icons and the text of {@code mode}, at
   * {@code alpha}, shifted by {@code dx} (in the row's own start-based x, as every x here) and {@code dy}.
   * {@code iconLayers}: each icon at full alpha in a layer of {@code alpha}, for frames where another pass may draw the
   * same drawable at another alpha.
   */
  private void xgramDrawLines (Canvas c, int mode, @Nullable Text prefix, @Nullable Text text, int textLeft, @Nullable IntList icons, int iconColorId, float alpha, boolean iconLayers, @Nullable ComplexReceiver receiver, int dx, int dy, int viewWidth, boolean rtl) {
    if (alpha <= 0f || (prefix == null && text == null)) {
      return;
    }
    int textTop = getTextTop(mode) + dy;
    if (prefix != null) {
      prefix.draw(c, getLeftPadding(mode) + dx, textTop, null, alpha);
    }
    if (text == null) {
      return;
    }
    if (mode != Settings.CHAT_MODE_2LINE) {
      if (prefix != null) {
        textTop += prefix.getNextLineHeight();
      } else if (text.getLineCount() == 1) {
        textTop += getSingleLineOffset(mode);
      }
    }
    if (icons != null) {
      int x = textLeft + dx;
      for (int i = 0; i < icons.size(); i++) {
        int iconId = icons.get(i);
        Drawable d = getSparseDrawable(iconId, ColorId.NONE);
        int y = textTop + text.getLineHeight(false) / 2 - d.getMinimumHeight() / 2;
        if (iconId == R.drawable.baseline_camera_alt_16) {
          y += Screen.dp(.5f);
        }
        if (iconLayers) {
          final float cx = (rtl ? viewWidth - x - d.getMinimumWidth() : x) + d.getMinimumWidth() / 2f;
          com.fahadali.xgram.appearance.XgramIconCrossfade.drawSingle(c, d, PorterDuffPaint.get(iconColorId), cx, y + d.getMinimumHeight() / 2f, alpha);
        } else {
          Drawables.drawRtl(c, d, x, y, PorterDuffPaint.get(iconColorId, alpha), viewWidth, rtl);
        }
        x += Screen.dp(18f); //  + d.getMinimumWidth();
      }
    }
    text.draw(c, textLeft + dx, textTop, null, alpha, receiver);
  }

  /**
   * XGram M3E: the community's stack: one tonal card the size of the photo, 4dp up and 4dp toward the end (the start
   * side is clipped by the screen edge with the M3E avatar inset), and a 2dp ring in the row's surface color around the
   * photo, so the card reads as a card behind it rather than a stray shadow. On the selection tile (secondaryContainer,
   * the card's own color) the two colors swap: a surface card and a ring in the tile's color. Scaled with the photo
   * ({@code scale}, 3/4 in one line).
   */
  private void xgramDrawCommunityStack (Canvas c, float radius, boolean rtl, float scale) {
    final float shift = Screen.dp(4f) * scale * (rtl ? -1 : 1), lift = Screen.dp(4f) * scale, ring = Screen.dp(2f) * scale;
    final int surface = Theme.fillingColor();
    final int tonal = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER);
    final float onTile = isDragging ? 0f : com.fahadali.xgram.layout.XgramTwoPane.isHighlighted(tdlib, chat.getChatId()) ? 1f : Math.max(0f, Math.min(1f, isSelected.getFloatValue()));
    final int cardColor = ColorUtils.fromToArgb(tonal, surface, onTile);
    final int ringColor = ColorUtils.fromToArgb(surface, tonal, onTile);
    xgramCardRect.set(avatarReceiver.getLeft() + shift, avatarReceiver.getTop() - lift, avatarReceiver.getRight() + shift, avatarReceiver.getBottom() - lift);
    c.drawRoundRect(xgramCardRect, radius, radius, Paints.fillingPaint(cardColor));
    xgramCardRect.set(avatarReceiver.getLeft() - ring, avatarReceiver.getTop() - ring, avatarReceiver.getRight() + ring, avatarReceiver.getBottom() + ring);
    c.drawRoundRect(xgramCardRect, radius + ring, radius + ring, Paints.fillingPaint(ringColor));
  }

  @Override
  public boolean isMediaGroup () {
    return chat != null && chat.isMediaGroup();
  }

  @Override
  public List<TdApi.Message> getVisibleMediaGroup () {
    return chat != null ? chat.getVisibleMediaGroup() : null;
  }

  @Override
  public TdApi.Message getVisibleMessage () {
    return chat != null ? chat.getVisibleMessage() : null;
  }

  @Override
  public int getVisibleMessageFlags () {
    return TdlibMessageViewer.Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION | (chat != null && chat.needRefreshInteractionInfo() ? TdlibMessageViewer.Flags.REFRESH_INTERACTION_INFO : 0);
  }
}