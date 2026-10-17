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
 * File created on 06/03/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramM3Profile;
import com.fahadali.xgram.appearance.XgramM3Sheets;
import com.fahadali.xgram.appearance.XgramR5People;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.CallItem;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGFoundMessage;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.concurrent.TimeUnit;

import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.ChatId;
import tgx.td.MessageId;
import tgx.td.Td;

public class BetterChatView extends BaseView implements Destroyable, RemoveHelper.RemoveDelegate, ChatListener, TdlibCache.UserDataChangeListener, TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, NotificationSettingsListener, TdlibCache.UserStatusChangeListener, DrawableProvider, TooltipOverlayView.LocationProvider, TdlibUi.MessageProvider {
  private static final int FLAG_FAKE_TITLE = 1;
  private static final int FLAG_SECRET = 1 << 1;
  private static final int FLAG_ONLINE = 1 << 2;
  private static final int FLAG_SELF_CHAT = 1 << 3;
  private static final int FLAG_NO_SUBTITLE = 1 << 4;

  private int flags;

  private final AvatarReceiver avatarReceiver;
  private final ComplexReceiver subtitleMediaReceiver;
  private final EmojiStatusHelper emojiStatusHelper;
  private @Nullable SimplestCheckBoxHelper checkBoxHelper;

  private FormattedText title;
  private Highlight titleHighlight;
  private Text displayTitle;

  private FormattedText subtitle;
  private Highlight subtitleHighlight;
  private Text displaySubtitle;

  private String time;
  private float timeWidth;

  private final Counter counter = new Counter.Builder().callback((counter, sizeChanged) -> {
    if (sizeChanged) {
      setTrimmedTitle();
      setTrimmedSubtitle();
    }
    invalidate();
  }).build();

  private int subtitleIcon;
  private Drawable subtitleIconDrawable;
  private @ColorId
  int subtitleIconColorId;

  public BetterChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.avatarReceiver = new AvatarReceiver(this);
    this.subtitleMediaReceiver = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    this.emojiStatusHelper = new EmojiStatusHelper(tdlib, this, null);
    avatarReceiver.setBounds(Screen.dp(11f), Screen.dp(10f), Screen.dp(11f) + Screen.dp(52f), Screen.dp(10f) + Screen.dp(52f));
  }

  public void attach () {
    avatarReceiver.attach();
    subtitleMediaReceiver.attach();
    emojiStatusHelper.attach();
  }

  public void detach () {
    avatarReceiver.detach();
    subtitleMediaReceiver.detach();
    emojiStatusHelper.detach();
  }

  @Override
  public void performDestroy () {
    avatarReceiver.destroy();
    subtitleMediaReceiver.performDestroy();
    emojiStatusHelper.performDestroy();
    setChatImpl(null);
    setMessageImpl(null);
  }

  public void setIsChecked (boolean isChecked, boolean animated) {
    if (isChecked != (checkBoxHelper != null && checkBoxHelper.isChecked())) {
      if (checkBoxHelper == null) {
        checkBoxHelper = new SimplestCheckBoxHelper(this);
      }
      checkBoxHelper.setIsChecked(isChecked, animated);
    }
  }

  @SuppressWarnings("WrongConstant")
  public void setCallItem (CallItem item) {
    long userId = item.getUserId();
    TdApi.User user = tdlib.cache().user(userId);
    xgramCallRow = true; // XGram M3E: room for the call-back button

    setPreviewChatId(null, item.getChatId(), null, new MessageId(item.getChatId(), item.getLatestMessageId()), null);

    setTime(Lang.time(item.getDate(), TimeUnit.SECONDS));
    setTitle(TD.getUserName(userId, user), null);
    setEmojiStatus(user);
    setSubtitleIcon(item.getSubtitleIcon(), item.getSubtitleIconColorId());
    setSubtitle(xgram() ? item.xgramSubtitle() : item.getSubtitle()); // XGram M3E: "Incoming · 2 min"
    avatarReceiver.requestUser(tdlib, userId, AvatarReceiver.Options.SHOW_ONLINE);
    invalidate();
  }

  private int lastWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    if (lastWidth != width) {
      lastWidth = width;

      layoutReceiver(width);

      setTrimmedTitle();
      setTrimmedSubtitle();
    }
  }

  private void layoutReceiver (int width) {
    if (xgram()) {
      // XGram M3E: the M3 list item's 40dp leading avatar, 16dp from the tile edge, centred in the row
      final int size = Screen.dp(XgramR5People.AVATAR);
      final int height = getMeasuredHeight() > 0 ? getMeasuredHeight() : Screen.dp(XgramR5People.ROW_HEIGHT);
      final int left = Lang.rtl() ? width - Screen.dp(XgramR5People.PADDING) - size : Screen.dp(XgramR5People.PADDING);
      final int top = (height - size) / 2;
      avatarReceiver.setBounds(left, top, left + size, top + size);
      return;
    }
    if (Lang.rtl()) {
      int x = Screen.dp(11f);
      int avatarWidth = Screen.dp(52f);
      avatarReceiver.setBounds(width - x - avatarWidth, Screen.dp(10f), width - x, Screen.dp(10f) + Screen.dp(52f));
    } else {
      avatarReceiver.setBounds(Screen.dp(11f), Screen.dp(10f), Screen.dp(11f) + Screen.dp(52f), Screen.dp(10f) + Screen.dp(52f));
    }
  }

  public void setTime (String time) {
    if (!StringUtils.equalsOrBothEmpty(this.time, time)) {
      this.time = time;
      float newTimeWidth = U.measureText(time, xgram() ? XgramR5People.metaPaint(0) : ChatView.getTimePaint()); // XGram: labelSmall, tabular
      if (this.timeWidth != newTimeWidth) {
        this.timeWidth = newTimeWidth;
        setTrimmedTitle();
      }
    }
  }

  public void setUnreadCount (int unreadCount, boolean muted, boolean animated) {
    counter.setCount(unreadCount, muted, animated);
  }

  public void setSubtitleIcon (int icon, @ColorId int color) {
    if (this.subtitleIcon != icon || this.subtitleIconColorId != color) {
      this.subtitleIconColorId = color;
      if (this.subtitleIcon != icon) {
        boolean prevHadIcon = subtitleIcon != 0;
        this.subtitleIcon = icon;
        this.subtitleIconDrawable = getSparseDrawable(icon, ColorId.NONE);
        boolean nowHasIcon = icon != 0;
        if (prevHadIcon != nowHasIcon) {
          setTrimmedSubtitle();
        }
      }
      invalidate();
    }
  }

  public void setIsOnline (boolean isOnline) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_ONLINE, isOnline);
    if (this.flags != flags) {
      this.flags = flags;
      invalidate();
    }
  }

  public void setIsSecret (boolean isSecret) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_SECRET, isSecret);
    if (this.flags != flags) {
      this.flags = flags;
      setTrimmedTitle();
      invalidate();
    }
  }

  public void setNoSubtitle (boolean noSubtitle) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_NO_SUBTITLE, noSubtitle);
    if (this.flags != flags) {
      this.flags = flags;
      invalidate();
    }
  }

  public void setAvatar (ImageFile avatar, AvatarPlaceholder.Metadata avatarPlaceholderMetadata) {
    if (avatar != null) {
      avatarReceiver.requestSpecific(tdlib, avatar, AvatarReceiver.Options.NONE);
    } else {
      avatarReceiver.requestPlaceholder(tdlib, avatarPlaceholderMetadata, AvatarReceiver.Options.NONE);
    }
  }

  public void setEmojiStatus (@Nullable TdApi.User user) {
    emojiStatusHelper.updateEmoji(user, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
      @Override
      public long mediaTextComplexColor () {
        return Theme.newComplexColor(true, ColorId.iconActive);
      }
    });
    setTrimmedTitle();
  }

  public void setTitle (CharSequence title) {
    setTitle(title, null);
  }

  public void setTitle (CharSequence title, @Nullable Highlight titleHighlight) {
    setTitle(FormattedText.valueOf(this, title, null), titleHighlight);
  }

  public void setTitle (FormattedText title, @Nullable Highlight titleHighlight) {
    if (!FormattedText.equals(this.title, title) || !Highlight.equals(this.titleHighlight, titleHighlight)) {
      this.title = title;
      this.titleHighlight = titleHighlight;
      this.flags = BitwiseUtils.setFlag(flags, FLAG_FAKE_TITLE, title != null && Text.needFakeBold(title.toString()));
      setTrimmedTitle();
    }
  }

  private void setTrimmedTitle () {
    if (xgram()) {
      xgramSetTrimmedTitle();
      return;
    }
    int width = getMeasuredWidth();
    float avail = width - Screen.dp(72f) - ChatView.getTimePaddingRight();
    if (timeWidth != 0) {
      avail -= timeWidth + ChatView.getTimePaddingLeft();
    }
    if ((flags & FLAG_SECRET) != 0) {
      avail -= Screen.dp(15f);
    }
    avail -= counter.getScaledWidth(Screen.dp(8f) + Screen.dp(23f));
    if (emojiStatusHelper.needDrawEmojiStatus()) {
      avail -= emojiStatusHelper.getWidth() + Screen.dp(6);
    }
    if (avail <= 0 || title == null || title.isEmpty()) {
      displayTitle = null;
      return;
    }
    this.displayTitle = new Text.Builder(
      title,
      (int) avail,
      Paints.robotoStyleProvider(17f),
      TextColorSets.Regular.NORMAL,
      null
    ).singleLine()
     .highlight(titleHighlight)
     .allBold()
     .ignoreNewLines()
     .ignoreContinuousNewLines()
     .viewProvider(new SingleViewProvider(this))
     .noClickable()
     .build();
  }

  public void setSubtitle (FormattedText subtitle, @Nullable Highlight subtitleHighlight) {
    if (!FormattedText.equals(this.subtitle, subtitle) || !Highlight.equals(this.subtitleHighlight, subtitleHighlight)) {
      this.subtitle = subtitle;
      this.subtitleHighlight = subtitleHighlight;
      setTrimmedSubtitle();
    }
  }

  public void setSubtitle (TdApi.FormattedText subtitle, @Nullable Highlight highlight) {
    setSubtitle(FormattedText.valueOf(this, subtitle, null), highlight);
  }

  public void setSubtitle (CharSequence subtitle) {
    setSubtitle(subtitle, null);
  }

  public void setSubtitle (CharSequence subtitle, @Nullable Highlight subtitleHighlight) {
    setSubtitle(FormattedText.valueOf(this, subtitle, null), subtitleHighlight);
  }

  private void setTrimmedSubtitle () {
    int width = getMeasuredWidth();
    float avail = width - Screen.dp(72f) - ChatView.getTimePaddingRight();
    if (subtitleIcon != 0) {
      avail -= Screen.dp(18f);
    }
    avail -= counter.getScaledWidth(Screen.dp(8f) + Screen.dp(23f));
    if (xgram()) {
      avail = xgramSubtitleAvail(width);
    }

    if (avail <= 0 || subtitle == null || subtitle.isEmpty()) {
      this.displaySubtitle = null;
      subtitleMediaReceiver.clear();
      return;
    }

    this.displaySubtitle = new Text.Builder(
      subtitle,
      (int) avail,
      xgram() ? XgramM3Profile.listSupportingProvider() : Paints.robotoStyleProvider(16f), // XGram: bodyMedium
      xgram() ? XgramM3Profile.TEXT_ON_SURFACE_VARIANT : TextColorSets.Regular.LIGHT,
      (text, specificMedia) -> {
        if (this.displaySubtitle == text) {
          if (!text.invalidateMediaContent(subtitleMediaReceiver, specificMedia)) {
            text.requestMedia(subtitleMediaReceiver);
          }
        }
      }
    ).singleLine()
     .highlight(subtitleHighlight)
     .textFlags(Text.FLAG_ELLIPSIZE_NEWLINE)
     .ignoreContinuousNewLines()
     .ignoreNewLines()
     .viewProvider(new SingleViewProvider(this))
     .noClickable()
     .build();
    this.displaySubtitle.requestMedia(subtitleMediaReceiver);
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (displayTitle != null && xgram()) {
      final int titleTop = xgramTitleTop();
      final int titleLeft = xgramTextX(getMeasuredWidth(), displayTitle.getWidth(), xgramTitleStartOffset());
      outRect.set(titleLeft, titleTop, titleLeft + displayTitle.getWidth(), titleTop + displayTitle.getHeight());
      return;
    }
    if (displayTitle != null) {
      int titleLeft = Screen.dp(72f);
      int titleTop = Screen.dp(28f) + Screen.dp(1f) - Screen.dp(16f);
      Paint.FontMetricsInt fm = ChatView.getTitlePaint((flags & FLAG_FAKE_TITLE) != 0).getFontMetricsInt();
      int titleBottom = titleTop + U.getLineHeight(fm);
      int titleWidth = displayTitle.getWidth();
      outRect.set(titleLeft, titleTop, titleLeft + titleWidth, titleBottom);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (removeHelper != null) {
      removeHelper.save(c);
    }

    final int width = getMeasuredWidth();
    final boolean rtl = Lang.rtl();

    layoutReceiver(width);

    if (xgram()) {
      xgramDraw(c, width, rtl);
      if (removeHelper != null) {
        removeHelper.restore(c);
        removeHelper.draw(c);
      }
      return;
    }

    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);
    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    if (checkFactor > 0f) {
      DrawAlgorithms.drawSimplestCheckBox(c, avatarReceiver, checkFactor);
    }
    boolean noSubtitle = BitwiseUtils.hasFlag(flags, FLAG_NO_SUBTITLE);
    int titleLeft = Screen.dp(72f);
    int titleTop;
    if (noSubtitle) {
      titleTop = (getHeight() - displayTitle.getHeight()) / 2;
    } else {
      titleTop = Screen.dp(12f) + Screen.dp(1f);
    }
    if (displayTitle != null) {
      boolean isSecret = (flags & FLAG_SECRET) != 0;
      Paint paint = ChatView.getTitlePaint((flags & FLAG_FAKE_TITLE) != 0);
      if (isSecret) {
        Drawables.drawRtl(c, Icons.getSecureDrawable(), titleLeft - Screen.dp(6f), titleTop - Screen.dp(1f), Paints.getGreenPorterDuffPaint(), width, rtl);
        titleLeft += Screen.dp(15f);
        paint.setColor(Theme.getColor(ColorId.textSecure));
      }
      displayTitle.draw(c, titleLeft, titleTop);
      titleLeft += displayTitle.getWidth();
    }
    emojiStatusHelper.draw(c, titleLeft + Screen.dp(6), titleTop);
    if (!noSubtitle) {
      int subtitleOffset = -Screen.dp(1f);
      if (displaySubtitle != null) {
        int subtitleLeft = Screen.dp(72f);
        if (subtitleIcon != 0) {
          subtitleLeft += Screen.dp(20f);
        }
        int subtitleTop = Screen.dp(39f) + subtitleOffset;
        TextColorSet colorSet = BitwiseUtils.hasFlag(flags, FLAG_ONLINE) ? TextColorSets.Regular.NEUTRAL : null;
        displaySubtitle.draw(c, subtitleLeft, subtitleTop, colorSet, 1f, subtitleMediaReceiver);
      }
      if (subtitleIcon != 0) {
        Drawables.drawRtl(c, subtitleIconDrawable, Screen.dp(72f), Screen.dp(subtitleIcon == R.drawable.baseline_call_missed_18 ? 40f : 39f) + subtitleOffset, PorterDuffPaint.get(subtitleIconColorId), width, rtl);
      }
    }
    if (time != null) {
      c.drawText(time, rtl ? ChatView.getTimePaddingRight() : width - ChatView.getTimePaddingRight() - timeWidth, Screen.dp(28f), ChatView.getTimePaint());
    }

    counter.draw(c, rtl ? ChatView.getTimePaddingRight() + Screen.dp(11.5f) : width - ChatView.getTimePaddingRight() - Screen.dp(11.5f), getMeasuredHeight() / 2f, rtl ? Gravity.LEFT : Gravity.RIGHT, 1f);

    if (removeHelper != null) {
      removeHelper.restore(c);
      removeHelper.draw(c);
    }
  }

  // XGram M3E: the M3 list item, as DoubleTextWrapper's: a 40dp avatar 16dp from the tile edge, the text from 72dp
  // (mirrored for RTL), the headline in bodyLarge and onSurface, the supporting text in bodyMedium and onSurfaceVariant
  // (primary while online, the query highlighted in primary), the time in labelSmall with tabular figures at the end
  // of the headline line, the unread counter at the end of the supporting line, a secret chat's lock in primary and,
  // on a recent call, the call direction in error (missed) or primary and a trailing filled tonal call-back button.

  private static boolean xgram () {
    return XgramExpressive.isEnabled();
  }

  private @Nullable View.OnClickListener xgramCallAction;
  private boolean xgramCallRow, xgramButtonCaught;
  private @Nullable XgramR5People.RowIconButton xgramCallButton;

  /** Recent calls: the action of the trailing call-back button (Expressive design only); null hides the button. */
  public void xgramSetCallAction (@Nullable View.OnClickListener action) {
    if (this.xgramCallAction != action) {
      final boolean had = xgramHasCallButton();
      this.xgramCallAction = action;
      if (had != xgramHasCallButton()) {
        setTrimmedTitle();
        setTrimmedSubtitle();
        invalidate();
      }
    }
  }

  private boolean xgramHasCallButton () {
    return xgramCallRow && xgramCallAction != null && xgram();
  }

  /** The room the trailing call-back button takes: the 40dp button and 12dp before it. */
  private int xgramTrailingRoom () {
    return xgramHasCallButton() ? Screen.dp(XgramM3Sheets.ICON_BUTTON_SIZE + 12f) : 0;
  }

  /** A secret chat's lock (16dp) and the 4dp after it, before the name. */
  private int xgramTitleStartOffset () {
    return (flags & FLAG_SECRET) != 0 ? Screen.dp(16f + 4f) : 0;
  }

  /** The x of a text {@code textWidth} wide that starts {@code startOffset} after the 72dp text start, mirrored for RTL. */
  private static int xgramTextX (int width, int textWidth, int startOffset) {
    final int start = Screen.dp(XgramR5People.TEXT_START) + startOffset;
    return Lang.rtl() ? width - start - textWidth : start;
  }

  private boolean xgramTwoLines () {
    return !BitwiseUtils.hasFlag(flags, FLAG_NO_SUBTITLE) && displaySubtitle != null;
  }

  /** The headline's top: the headline and the supporting text are centred in the row as one block. */
  private int xgramTitleTop () {
    final int titleHeight = displayTitle != null ? displayTitle.getHeight() : 0;
    final int subtitleHeight = xgramTwoLines() ? displaySubtitle.getHeight() : 0;
    return (getMeasuredHeight() - titleHeight - subtitleHeight) / 2;
  }

  private void xgramSetTrimmedTitle () {
    final int width = getMeasuredWidth();
    float avail = width - Screen.dp(XgramR5People.TEXT_START) - Screen.dp(XgramR5People.PADDING) - xgramTrailingRoom() - xgramTitleStartOffset();
    if (timeWidth != 0) {
      avail -= timeWidth + Screen.dp(8f);
    }
    if (emojiStatusHelper.needDrawEmojiStatus()) {
      avail -= emojiStatusHelper.getWidth() + Screen.dp(6);
    }
    if (avail <= 0 || title == null || title.isEmpty()) {
      displayTitle = null;
      return;
    }
    // bodyLarge at its own weight: no allBold (and so no fake bold)
    this.displayTitle = new Text.Builder(
      title,
      (int) avail,
      XgramM3Profile.listHeadlineProvider(),
      XgramM3Profile.TEXT_ON_SURFACE,
      null
    ).singleLine()
     .highlight(titleHighlight)
     .ignoreNewLines()
     .ignoreContinuousNewLines()
     .viewProvider(new SingleViewProvider(this))
     .noClickable()
     .build();
  }

  private float xgramSubtitleAvail (int width) {
    float avail = width - Screen.dp(XgramR5People.TEXT_START) - Screen.dp(XgramR5People.PADDING) - xgramTrailingRoom();
    if (subtitleIcon != 0) {
      avail -= Screen.dp(18f + 4f);
    }
    avail -= counter.getScaledWidth(Screen.dp(8f));
    return avail;
  }

  private void xgramDraw (Canvas c, int width, boolean rtl) {
    final int height = getMeasuredHeight();
    final int padding = Screen.dp(XgramR5People.PADDING);
    final int trailing = xgramTrailingRoom();

    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);
    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    if (checkFactor > 0f) {
      XgramM3Sheets.drawAvatarCheck(c, avatarReceiver, checkFactor, XgramR5People.rowColor(this)); // the M3 checkbox
    }

    final int titleTop = xgramTitleTop();
    final int titleHeight = displayTitle != null ? displayTitle.getHeight() : 0;
    final float titleCenterY = displayTitle != null ? titleTop + titleHeight / 2f : height / 2f;
    if (displayTitle != null) {
      if ((flags & FLAG_SECRET) != 0) {
        Drawable lock = getSparseDrawable(R.drawable.baseline_lock_16, ColorId.NONE);
        final int lockX = rtl ? width - Screen.dp(XgramR5People.TEXT_START) - lock.getMinimumWidth() : Screen.dp(XgramR5People.TEXT_START);
        Drawables.draw(c, lock, lockX, titleCenterY - lock.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(XgramExpressive.color(XgramExpressive.PRIMARY)));
      }
      final int titleX = xgramTextX(width, displayTitle.getWidth(), xgramTitleStartOffset());
      displayTitle.draw(c, titleX, titleTop);
      final int statusX = rtl ? titleX - Screen.dp(6) - emojiStatusHelper.getWidth() : titleX + displayTitle.getWidth() + Screen.dp(6);
      emojiStatusHelper.draw(c, statusX, titleTop);
    }

    float counterCenterY = height / 2f;
    if (xgramTwoLines()) {
      final int subtitleTop = titleTop + titleHeight;
      final float subtitleCenterY = subtitleTop + displaySubtitle.getHeight() / 2f;
      counterCenterY = subtitleCenterY;
      int iconRoom = 0;
      if (subtitleIcon != 0 && subtitleIconDrawable != null) {
        // The call direction: error when missed or declined, primary otherwise
        final int role = subtitleIconColorId == ColorId.iconNegative ? XgramExpressive.ERROR : XgramExpressive.PRIMARY;
        final int iconX = rtl ? width - Screen.dp(XgramR5People.TEXT_START) - subtitleIconDrawable.getMinimumWidth() : Screen.dp(XgramR5People.TEXT_START);
        Drawables.draw(c, subtitleIconDrawable, iconX, subtitleCenterY - subtitleIconDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(XgramExpressive.color(role)));
        iconRoom = Screen.dp(18f + 4f);
      }
      final TextColorSet colorSet = BitwiseUtils.hasFlag(flags, FLAG_ONLINE) ? XgramM3Profile.TEXT_PRIMARY : null;
      displaySubtitle.draw(c, xgramTextX(width, displaySubtitle.getWidth(), iconRoom), subtitleTop, colorSet, 1f, subtitleMediaReceiver);
    }

    if (time != null) {
      final Paint paint = XgramR5People.metaPaint(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      final float baseline = titleCenterY - (paint.descent() + paint.ascent()) / 2f;
      c.drawText(time, rtl ? padding + trailing : width - padding - trailing - timeWidth, baseline, paint);
    }

    final int counterRadius = ChatView.getCounterRadius();
    counter.draw(c, rtl ? padding + trailing + counterRadius : width - padding - trailing - counterRadius, counterCenterY, rtl ? Gravity.LEFT : Gravity.RIGHT, 1f);

    if (xgramHasCallButton()) {
      if (xgramCallButton == null) {
        // A standard icon button in primary, as the call message card's call-back button (XgramR5Calls.CallCard)
        xgramCallButton = new XgramR5People.RowIconButton(this, false);
      }
      final float half = Screen.dp(XgramM3Sheets.ICON_BUTTON_SIZE) / 2f;
      xgramCallButton.layout(rtl ? padding + half : width - padding - half, height / 2f);
      xgramCallButton.draw(c, getSparseDrawable(XgramIconPack.map(R.drawable.baseline_call_24), ColorId.NONE), XgramExpressive.PRIMARY);
    } else if (xgramCallButton != null) {
      xgramCallButton.hide();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    // XGram M3E: the call-back button takes its own taps (the row keeps the rest)
    if (xgramCallButton != null && xgramHasCallButton()) {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          if (xgramCallButton.contains(e.getX(), e.getY())) {
            xgramButtonCaught = true;
            xgramCallButton.setPressed(true);
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (xgramButtonCaught) {
            if (!xgramCallButton.contains(e.getX(), e.getY())) {
              xgramCallButton.setPressed(false);
            }
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_UP: {
          if (xgramButtonCaught) {
            xgramButtonCaught = false;
            final boolean inside = xgramCallButton.contains(e.getX(), e.getY());
            xgramCallButton.setPressed(false);
            if (inside && xgramCallAction != null) {
              playSoundEffect(SoundEffectConstants.CLICK);
              xgramCallAction.onClick(this);
            }
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          if (xgramButtonCaught) {
            xgramButtonCaught = false;
            xgramCallButton.setPressed(false);
            return true;
          }
          break;
        }
      }
    }
    return super.onTouchEvent(e);
  }

  // Found chat

  private TGFoundChat lastChat;
  private boolean needStatusUiUpdates;

  private void addListeners (@NonNull TdApi.Chat chat, boolean add, boolean needStatusUiUpdates) {
    if (add) {
      tdlib.listeners().subscribeToChatUpdates(chat.id, this);
      tdlib.listeners().subscribeToSettingsUpdates(chat.id, this);
    } else {
      tdlib.listeners().unsubscribeFromChatUpdates(chat.id, this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(chat.id, this);
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        if (add) {
          this.needStatusUiUpdates = needStatusUiUpdates;
          tdlib.cache().subscribeToUserUpdates(TD.getUserId(chat.type), this);
        } else {
          tdlib.cache().unsubscribeFromUserUpdates(TD.getUserId(chat.type), this);
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(chat.id), this);
        } else {
          tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(chat.id), this);
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToGroupUpdates(ChatId.toBasicGroupId(chat.id), this);
        } else {
          tdlib.cache().unsubscribeFromGroupUpdates(ChatId.toBasicGroupId(chat.id), this);
        }
        break;
      }
    }
  }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    // TODO update?
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    updateChat(chatId);
  }

  private void addListeners (long userId, boolean add, boolean needStatusUiUpdates) {
    if (add) {
      this.needStatusUiUpdates = needStatusUiUpdates;
      tdlib.cache().subscribeToUserUpdates(userId, this);
    } else {
      tdlib.cache().unsubscribeFromUserUpdates(userId, this);
    }
  }

  private void setChatImpl (@Nullable TGFoundChat chat) {
    if (lastChat != null) {
      if (lastChat.getChat() != null) {
        addListeners(lastChat.getChat(), false, needStatusUiUpdates);
      } else if (lastChat.getUserId() != 0) {
        addListeners(lastChat.getUserId(), false, needStatusUiUpdates);
      }
    }
    this.lastChat = chat;
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SELF_CHAT, chat != null && chat.isSelfChat());
    if (chat != null) {
      long chatId = 0;
      setIsSecret(chat.isSecret());
      if (chat.getChat() != null) {
        addListeners(chat.getChat(), true, !chat.isGlobal());
        chatId = chat.getChat().id;
      } else if (chat.getUserId() != 0) {
        addListeners(lastChat.getUserId(), true, !chat.isGlobal());
        chatId = ChatId.fromUserId(lastChat.getUserId());
      }
      setPreviewChatId(chat.getList(), chatId, null);
    } else {
      setPreviewChatId(null, 0, null);
    }
  }

  public void setChat (@Nullable TGFoundChat chat) {
    if (chat == lastChat) {
      return;
    }
    xgramCallRow = false;
    if (lastMessage != null) {
      setMessageImpl(null);
    }
    setChatImpl(chat);
    if (chat != null) {
      updateChat(false);
    }
  }

  private void updateChat (boolean update) {
    if (update) {
      lastChat.updateChat();
    }
    setTitle(lastChat.getTitle(), lastChat.getTitleHighlight());
    setEmojiStatus(lastChat.getChat() != null ? tdlib.chatUser(lastChat.getChat()) : (lastChat.getUserId() != 0 ? tdlib.cache().user(lastChat.getUserId()) : null));
    updateSubtitle();
    lastChat.requestAvatar(avatarReceiver, AvatarReceiver.Options.SHOW_ONLINE);
    setTime(null);
    setUnreadCount(lastChat.getUnreadCount(), !lastChat.notificationsEnabled(), update);
  }

  private void updateSubtitle () {
    if (lastChat != null) {
      if (StringUtils.isEmpty(lastChat.getForcedSubtitle())) {
        final long userId = lastChat.getUserId();
        if (lastChat.isGlobal()) {
          setSubtitle(lastChat.getUsername(), lastChat.getUsernameHighlight());
        } else {
          CharSequence subtitle = (userId != 0 ? tdlib.status().getPrivateChatSubtitle(userId) : tdlib.status().chatStatus(lastChat.getId()));
          if (lastChat.needForceUsername()) {
            String username = userId != 0 ? tdlib.cache().userUsername(userId) : tdlib.chatUsername(lastChat.getId());
            if (!StringUtils.isEmpty(username)) {
              subtitle = new SpannableStringBuilder(subtitle).insert(0, "@" + username + ", ");
            }
          }
          setSubtitle(subtitle);
        }
        setIsOnline(!tdlib.isSelfUserId(userId) && tdlib.cache().isOnline(userId));
      } else {
        setSubtitle(lastChat.getForcedSubtitle());
        setIsOnline(false);
      }
      invalidate();
    }
  }

  // Update listeners

  private void updateChat (final long chatId) {
    tdlib.uiExecute(() -> {
      if (lastChat != null && lastChat.getId() == chatId) {
        updateChat(true);
      }
    });
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    updateChat(chatId);
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    updateChat(chatId);
  }

  @Override
  public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
    updateChat(chatId);
  }

  @Override
  public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    updateChat(chatId);
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.uiExecute(() -> {
      if (lastChat != null && lastChat.getUserId() == user.id) {
        updateChat(true);
      }
    });
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return needStatusUiUpdates;
  }

  @Override
  @UiThread
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (lastChat != null && lastChat.getUserId() == userId) {
      updateSubtitle();
    }
  }

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    updateSubtitleIfNeeded(ChatId.fromBasicGroupId(basicGroup.id));
  }

  @Override
  public void onBasicGroupFullUpdated (long basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    updateSubtitleIfNeeded(ChatId.fromBasicGroupId(basicGroupId));
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    updateSubtitleIfNeeded(chatId);
  }

  @Override
  public void onSupergroupUpdated (final TdApi.Supergroup supergroup) {
    updateSubtitleIfNeeded(ChatId.fromSupergroupId(supergroup.id));
  }

  @Override
  public void onSupergroupFullUpdated (final long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    updateSubtitleIfNeeded(ChatId.fromSupergroupId(supergroupId));
  }

  private void updateSubtitleIfNeeded (long chatId) {
    tdlib.uiExecute(() -> {
      if (lastChat != null && lastChat.getChatId() == chatId) {
        updateSubtitle();
      }
    });
  }

  // Remove delegate

  private RemoveHelper removeHelper;

  @Override
  public void setRemoveDx (float dx) {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    removeHelper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    removeHelper.onSwipe();
  }

  // Message

  private TGFoundMessage lastMessage;

  private void setMessageImpl (TGFoundMessage message) {
    if (lastMessage != null) {
      tdlib.listeners().unsubscribeFromChatUpdates(lastMessage.getChat().getId(), this);
    }
    lastMessage = message;
    if (message != null) {
      final long chatId = message.getChat().getId();
      setIsSecret(message.getChat().isSecret());
      setPreviewChatId(message.getChat().getList(), chatId, null, new MessageId(chatId, message.getId()), null);
      tdlib.listeners().subscribeToChatUpdates(chatId, this);
    } else {
      clearPreviewChat();
    }
  }

  public void setMessage (@Nullable TGFoundMessage foundMessage) {
    if (lastChat != null) {
      setChatImpl(null);
    } else if (lastMessage == foundMessage) {
      return;
    }
    setMessageImpl(foundMessage);
    xgramCallRow = false;
    if (foundMessage != null) {
      TdApi.Message message = foundMessage.getMessage();
      flags = BitwiseUtils.setFlag(flags, FLAG_SELF_CHAT, foundMessage.getChat().isSelfChat());
      setTime(Lang.timeOrDateShort(message.date, TimeUnit.SECONDS));
      TGFoundChat chat = foundMessage.getChat();
      setTitle(chat.getTitle(), chat.getTitleHighlight());
      setSubtitle(foundMessage.getText(), foundMessage.getHighlight());
      setUnreadCount(0, counter.isMuted(), false);
      TdApi.MessageSender sender = chat.getSenderId();
      setEmojiStatus(tdlib.chatUser(Td.getSenderId(sender)));
      avatarReceiver.requestMessageSender(tdlib, sender, AvatarReceiver.Options.NONE);
      invalidate();
    }
  }

  @Override
  public TdApi.Message getVisibleMessage () {
    return lastMessage != null ? lastMessage.getMessage() : null;
  }

  @Override
  public int getVisibleMessageFlags () {
    return TdlibMessageViewer.Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION;
  }
}
