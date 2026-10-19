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
package com.fahadali.xgram.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Profile;
import com.fahadali.xgram.appearance.XgramR5People;
import com.fahadali.xgram.layout.XgramTwoPane;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.U;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.lambda.Destroyable;

/**
 * One Activity row, drawn by itself in both looks (as BetterChatView is).
 * <ul>
 *   <li>Expressive design: an M3 list tile (the list makes its filling background a tile): the 40dp avatar at the top
 *   start with a kind badge (an @, a reply arrow or the newest reaction), the sender in titleMedium (Medium while
 *   New), the chat in bodyMedium, the time in labelSmall, two lines of bodyMedium and the quote of your message in a
 *   strip with a primary bar. A primary dot while New.</li>
 *   <li>Telegram X's look: BetterChatView's 52dp avatar and text from 72dp, the title in bold while New, the body and
 *   quote in textLight after a 2dp bar, the time in the chat list's paint and a badge dot while New.</li>
 * </ul>
 * Everything is laid out for the measured width when the row or the width changes; drawing allocates nothing.
 */
@SuppressLint("ViewConstructor")
public final class XgramActivityRowView extends SparseDrawableView implements AttachDelegate, Destroyable, RemoveHelper.RemoveDelegate, XgramTwoPane.HighlightAware {
  private final Tdlib tdlib;
  private final AvatarReceiver avatar, avatarBack;
  private final ComplexReceiver textMedia;

  @Nullable private XgramActivityRows.Row row;
  private boolean twoAvatars;
  private boolean expressive;
  @Nullable private String boundKey;

  @Nullable private Text titleText, contextText, prefixText, bodyText, quoteText, badgeText;
  @Nullable private String time;
  private float timeWidth;
  private int layoutWidth = -1;
  private int bodyTop, quoteTop;
  private int kindIcon;

  private final XgramExpressive.Spring dot;
  private final RectF rect = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  /** The selected row is the one whose chat is open on the right (XgramTwoPane) and that was opened last. */
  public interface SelectionProvider {
    boolean isRowSelected (@NonNull String key, long chatId);
  }

  @Nullable private SelectionProvider selection;

  public XgramActivityRowView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.avatar = new AvatarReceiver(this);
    this.avatarBack = new AvatarReceiver(this);
    this.textMedia = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    this.dot = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
  }

  public void setSelectionProvider (@Nullable SelectionProvider selection) {
    this.selection = selection;
  }

  @Nullable
  XgramActivityRows.Row getRow () {
    return row;
  }

  // Binding

  /** Always rebinds (a recycled or destroyed view too). */
  void setRow (@NonNull XgramActivityRows.Row row) {
    final boolean sameKey = boundKey != null && boundKey.equals(row.key());
    if (!sameKey) {
      resetSwipe();
    }
    final boolean wasNew = this.row != null && this.row.isNew();
    this.row = row;
    this.boundKey = row.key();
    this.expressive = XgramExpressive.isEnabled();
    final boolean isNew = row.isNew();
    if (sameKey && wasNew && !isNew && getWindowToken() != null) {
      dot.animateTo(0f); // read now: the dot scales away
    } else if (sameKey && !wasNew && isNew && getWindowToken() != null) {
      dot.animateTo(1f);
    } else {
      dot.setValue(isNew ? 1f : 0f);
    }
    requestAvatars();
    layoutWidth = -1; // laid out again for this row
    requestLayout();
    invalidate();
    setContentDescription(describe());
  }

  private void requestAvatars () {
    XgramActivityRows.Row row = this.row;
    if (row == null) {
      return;
    }
    XgramActivityItem item = row.newest();
    twoAvatars = false;
    if (item.isReaction()) {
      List<Long> senders = item.reactorSenders();
      if (senders.size() >= 2) {
        twoAvatars = true;
        avatar.requestMessageSender(tdlib, XgramActivityClassifier.decodeSender(senders.get(0)), AvatarReceiver.Options.NONE);
        avatarBack.requestMessageSender(tdlib, XgramActivityClassifier.decodeSender(senders.get(1)), AvatarReceiver.Options.NONE);
        return;
      }
      if (senders.size() == 1) {
        avatar.requestMessageSender(tdlib, XgramActivityClassifier.decodeSender(senders.get(0)), AvatarReceiver.Options.NONE);
      } else {
        avatar.requestChat(tdlib, item.chatId, AvatarReceiver.Options.NONE);
      }
      avatarBack.clear();
      return;
    }
    TdApi.MessageSender sender = item.senderId();
    if (sender != null) {
      avatar.requestMessageSender(tdlib, sender, AvatarReceiver.Options.NONE);
    } else {
      avatar.requestChat(tdlib, item.chatId, AvatarReceiver.Options.NONE);
    }
    avatarBack.clear();
  }

  @Override
  public void attach () {
    avatar.attach();
    avatarBack.attach();
    textMedia.attach();
  }

  @Override
  public void detach () {
    avatar.detach();
    avatarBack.detach();
    textMedia.detach();
  }

  @Override
  public void performDestroy () {
    avatar.destroy();
    avatarBack.destroy();
    textMedia.performDestroy();
    // Bound again from scratch if the view comes back (setRow)
    boundKey = null;
  }

  // Text

  private static final TextColorSet M3_BODY = new TextColorSet() {
    @Override
    public int defaultTextColor () {
      return XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    }

    @Override
    public int clickableTextColor (boolean isPressed) {
      return XgramExpressive.color(XgramExpressive.PRIMARY);
    }
  };

  private static final TextColorSet CLASSIC_BODY = new TextColorSet() {
    @Override
    public int defaultTextColor () {
      return Theme.getColor(ColorId.textLight);
    }

    @Override
    public int clickableTextColor (boolean isPressed) {
      return Theme.getColor(ColorId.textLink);
    }
  };

  private static final TextColorSet CLASSIC_TITLE = () -> Theme.getColor(ColorId.text);

  private static TextStyleProvider titleProvider (boolean expressive, boolean isNew) {
    if (expressive) {
      // titleMedium (Medium) while New, the same size in Regular once read
      return isNew ? XgramM3Profile.flexProvider(XgramExpressive.TS_TITLE_M, false, XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)) :
        XgramM3Profile.flexProvider(XgramExpressive.TS_BODY_L, false, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_L));
    }
    return Paints.robotoStyleProvider(16f);
  }

  private static TextStyleProvider bodyProvider (boolean expressive) {
    return expressive ? XgramM3Profile.listSupportingProvider() : Paints.robotoStyleProvider(15f);
  }

  private static TextStyleProvider quoteProvider (boolean expressive) {
    return expressive ? XgramM3Profile.flexTextProvider(XgramExpressive.TS_BODY_S, XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_S)) : Paints.robotoStyleProvider(15f);
  }

  /** "Anna", "Anna and Ben", "Anna, Ben and 3 others". */
  @NonNull
  private String senderLine (@NonNull XgramActivityRows.Row row) {
    XgramActivityItem item = row.newest();
    List<String> names = new ArrayList<>();
    int extra = 0;
    if (item.isReaction()) {
      List<Long> senders = item.reactorSenders();
      for (int i = 0; i < senders.size() && names.size() < 2; i++) {
        names.add(nameOf(senders.get(i), null));
      }
      extra = Math.max(0, item.reactorCount() - names.size());
    } else {
      List<XgramActivityItem> people = row.distinctSenders();
      for (int i = 0; i < people.size() && names.size() < 2; i++) {
        names.add(nameOf(people.get(i).sender, people.get(i).senderName));
      }
      extra = Math.max(0, people.size() - names.size());
    }
    if (names.isEmpty()) {
      return Lang.getString(R.string.XgramActivitySomeone);
    }
    if (names.size() == 1 && extra == 0) {
      return names.get(0);
    }
    if (extra == 0) {
      return Lang.getString(R.string.XgramActivityTwo, names.get(0), names.get(1));
    }
    if (names.size() == 1) {
      return Lang.getString(extra == 1 ? R.string.XgramActivityOneAndOther : R.string.XgramActivityOneAndOthers, names.get(0), extra);
    }
    return Lang.getString(extra == 1 ? R.string.XgramActivityTwoAndOther : R.string.XgramActivityTwoAndOthers, names.get(0), names.get(1), extra);
  }

  @NonNull
  private String nameOf (long sender, @Nullable String saved) {
    TdApi.MessageSender senderId = XgramActivityClassifier.decodeSender(sender);
    if (senderId != null) {
      try {
        String name = tdlib.senderName(senderId);
        if (name != null && !name.isEmpty()) {
          return name;
        }
      } catch (Throwable ignored) { }
    }
    return saved != null && !saved.isEmpty() ? saved : Lang.getString(R.string.XgramActivitySomeone);
  }

  /** "Design Team" or "Design Team › General". */
  @NonNull
  private String chatLine (@NonNull XgramActivityItem item) {
    String chat = null;
    if (tdlib.chat(item.chatId) != null) {
      chat = tdlib.chatTitle(item.chatId);
    }
    if (chat == null || chat.isEmpty()) {
      chat = item.chatTitle != null ? item.chatTitle : "";
    }
    if (item.topicTitle != null && !item.topicTitle.isEmpty() && !item.hasFlag(XgramActivityItem.FLAG_PRIVATE)) {
      return Lang.getString(R.string.XgramActivityInTopic, chat, item.topicTitle);
    }
    return chat;
  }

  /** Formatted into a localized string to find where its argument goes: what is before it is the prefix. */
  private static final String MARKER = "\u0000";

  @NonNull
  private TdApi.FormattedText reactionBody (@NonNull XgramActivityItem item, @NonNull List<TdApi.TextEntity> entities) {
    String format = Lang.getString(R.string.XgramActivityReacted, MARKER);
    int at = format.indexOf(MARKER);
    StringBuilder b = new StringBuilder(at >= 0 ? format.substring(0, at) : "");
    List<String> seen = new ArrayList<>();
    for (XgramActivityItem.Reactor reactor : item.reactors) {
      if (seen.contains(reactor.reaction) || seen.size() >= 3) {
        continue;
      }
      seen.add(reactor.reaction);
      String emoji = XgramActivityClassifier.reactionEmoji(reactor.reaction);
      long customEmojiId = XgramActivityClassifier.reactionCustomEmojiId(reactor.reaction);
      if (emoji != null) {
        b.append(emoji);
      } else if (customEmojiId != 0) {
        int offset = b.length();
        b.append("⭐");
        entities.add(new TdApi.TextEntity(offset, 1, new TdApi.TextEntityTypeCustomEmoji(customEmojiId)));
      } else {
        b.append("⭐"); // a paid reaction: a star
      }
    }
    if (at >= 0) {
      b.append(format.substring(at + MARKER.length()));
    }
    return new TdApi.FormattedText(b.toString(), null);
  }

  /** Lays every text out for {@code width}. */
  private void layoutTexts (int width) {
    XgramActivityRows.Row row = this.row;
    titleText = contextText = prefixText = bodyText = quoteText = badgeText = null;
    textMedia.clear();
    if (row == null || width <= 0) {
      return;
    }
    final boolean expressive = this.expressive;
    final XgramActivityItem item = row.newest();
    final boolean isNew = row.isNew();
    final int textStart = Screen.dp(72f);
    final int end = expressive ? Screen.dp(XgramR5People.PADDING) : ChatView.getTimePaddingRight();
    final int available = Math.max(0, width - textStart - end);

    // Time
    time = Lang.timeOrDateShort(item.eventDate, TimeUnit.SECONDS);
    timeWidth = U.measureText(time, expressive ? XgramR5People.metaPaint(0) : ChatView.getTimePaint());

    // Title: the sender(s), then the chat
    final int titleWidth = Math.max(0, available - (int) timeWidth - Screen.dp(8f));
    final String sender = senderLine(row);
    final String chat = chatLine(item);
    if (expressive) {
      titleText = new Text.Builder(sender, titleWidth, titleProvider(true, isNew), XgramM3Profile.TEXT_ON_SURFACE)
        .singleLine().ignoreNewLines().noClickable().build();
      int rest = titleWidth - titleText.getWidth();
      if (!chat.isEmpty() && rest > Screen.dp(24f)) {
        contextText = new Text.Builder(" · " + chat, rest, bodyProvider(true), XgramM3Profile.TEXT_ON_SURFACE_VARIANT)
          .singleLine().ignoreNewLines().noClickable().build();
      }
    } else {
      String title = chat.isEmpty() ? sender : sender + " · " + chat;
      Text.Builder b = new Text.Builder(title, titleWidth, titleProvider(false, isNew), CLASSIC_TITLE).singleLine().ignoreNewLines().noClickable();
      if (isNew) {
        b.allBold();
      }
      titleText = b.build();
    }

    // Body: a prefix in onSurface ("Mentioned you: "), the rest in onSurfaceVariant
    final TextColorSet bodyColors = expressive ? M3_BODY : CLASSIC_BODY;
    final TextColorSet prefixColors = expressive ? XgramM3Profile.TEXT_ON_SURFACE : CLASSIC_TITLE;
    String prefix;
    TdApi.FormattedText body;
    List<TdApi.TextEntity> entities = new ArrayList<>();
    if (item.isDeleted()) {
      String saved = item.text != null ? item.text : "";
      String deleted = Lang.getString(R.string.XgramActivityDeleted);
      String value = saved.isEmpty() ? deleted : deleted + " · " + saved;
      prefix = null;
      body = new TdApi.FormattedText(value, new TdApi.TextEntity[] {new TdApi.TextEntity(0, value.length(), new TdApi.TextEntityTypeItalic())});
    } else if (item.isReaction()) {
      prefix = null;
      body = reactionBody(item, entities);
      body.entities = entities.toArray(new TdApi.TextEntity[0]);
    } else {
      String text = item.text != null ? item.text : "";
      String format = Lang.getString(item.isMention() ? R.string.XgramActivityMentioned : R.string.XgramActivityReplied, MARKER);
      int at = format.indexOf(MARKER);
      prefix = at > 0 ? format.substring(0, at) : null;
      String after = at >= 0 ? format.substring(at + MARKER.length()) : "";
      String value = text + after;
      if (item.isMention() && item.mentionFrom >= 0 && item.mentionLength > 0 && item.mentionFrom + item.mentionLength <= text.length()) {
        // Your @ in primary, Medium
        entities.add(new TdApi.TextEntity(item.mentionFrom, item.mentionLength, new TdApi.TextEntityTypeMentionName(tdlib.myUserId())));
        entities.add(new TdApi.TextEntity(item.mentionFrom, item.mentionLength, new TdApi.TextEntityTypeBold()));
      }
      body = new TdApi.FormattedText(value, entities.toArray(new TdApi.TextEntity[0]));
    }
    int prefixWidth = 0;
    if (prefix != null && !prefix.isEmpty()) {
      prefixText = new Text.Builder(prefix, available, bodyProvider(expressive), prefixColors).singleLine().noClickable().build();
      prefixWidth = prefixText.getWidth();
      if (prefixWidth >= available - Screen.dp(24f)) {
        prefixText = null;
        prefixWidth = 0;
      }
    }
    final int firstLineMargin = prefixWidth;
    final boolean prefixIsBody = item.isReaction(); // "Reacted ❤️ to your message" reads as one phrase in onSurface
    bodyText = new Text.Builder(tdlib, body, null, available, bodyProvider(expressive), prefixIsBody ? prefixColors : bodyColors, (text, specificMedia) -> {
      if (bodyText == text && text != null) {
        if (!text.invalidateMediaContent(textMedia, specificMedia)) {
          text.requestMedia(textMedia, 0, 64);
        }
      }
    })
      .maxLineCount(2)
      .ignoreNewLines()
      .ignoreContinuousNewLines()
      .lineMarginProvider(firstLineMargin > 0 ? (lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? firstLineMargin : 0 : null)
      .build();
    bodyText.requestMedia(textMedia, 0, 64);

    // The quote of your message
    String quote = quoteOf(item);
    if (quote != null) {
      String you = Lang.getString(R.string.XgramActivityYou, quote);
      int quoteWidth = expressive ? available - Screen.dp(12f + 3f + 8f) : available - Screen.dp(8f);
      quoteText = new Text.Builder(you, Math.max(0, quoteWidth), quoteProvider(expressive), expressive ? XgramM3Profile.TEXT_ON_SURFACE_VARIANT : CLASSIC_BODY)
        .singleLine().ignoreNewLines().noClickable().build();
    }

    // The kind badge: the newest reaction's emoji, or an icon
    kindIcon = 0;
    if (item.isReaction()) {
      String newest = !item.reactors.isEmpty() ? item.reactors.get(0).reaction : null;
      String emoji = XgramActivityClassifier.reactionEmoji(newest);
      long customEmojiId = XgramActivityClassifier.reactionCustomEmojiId(newest);
      if (expressive && (emoji != null || customEmojiId != 0 || "p".equals(newest))) {
        TdApi.FormattedText value = customEmojiId != 0 ?
          new TdApi.FormattedText("⭐", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(customEmojiId))}) :
          new TdApi.FormattedText(emoji != null ? emoji : "⭐", null);
        badgeText = new Text.Builder(tdlib, value, null, Screen.dp(20f), Paints.robotoStyleProvider(11f), XgramM3Profile.TEXT_ON_SURFACE, (text, specificMedia) -> {
          if (badgeText == text && text != null) {
            if (!text.invalidateMediaContent(textMedia, specificMedia)) {
              text.requestMedia(textMedia, 64, 4);
            }
          }
        }).singleLine().noClickable().build();
        badgeText.requestMedia(textMedia, 64, 4);
      } else {
        kindIcon = R.drawable.baseline_favorite_14;
      }
    } else if (item.isMention()) {
      kindIcon = R.drawable.baseline_alternate_email_14;
    } else {
      kindIcon = expressive ? R.drawable.baseline_reply_24 : R.drawable.baseline_reply_16;
    }

    // Vertical layout
    if (expressive) {
      bodyTop = Screen.dp(12f + 24f);
      quoteTop = bodyTop + bodyText.getHeight() + Screen.dp(8f);
    } else {
      bodyTop = Screen.dp(12f + 24f);
      quoteTop = bodyTop + bodyText.getHeight() + Screen.dp(2f);
    }
  }

  @Nullable
  private static String quoteOf (@NonNull XgramActivityItem item) {
    if (item.quoteText == null || item.quoteText.isEmpty()) {
      return null;
    }
    if (item.isMention() && !item.hasFlag(XgramActivityItem.FLAG_ALSO_REPLY)) {
      return null;
    }
    return item.quoteText;
  }

  private int contentHeight () {
    if (bodyText == null) {
      return Screen.dp(72f);
    }
    int height;
    if (expressive) {
      height = Screen.dp(12f + 24f) + bodyText.getHeight() + (quoteText != null ? Screen.dp(8f + 28f) : 0) + Screen.dp(12f);
    } else {
      height = Screen.dp(12f + 24f) + bodyText.getHeight() + (quoteText != null ? Screen.dp(2f + 20f) : 0) + Screen.dp(12f);
    }
    return Math.max(Screen.dp(72f), height);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    if (width != layoutWidth) {
      layoutWidth = width;
      layoutTexts(width);
    }
    setMeasuredDimension(width, contentHeight());
  }

  // Drawing

  private void layoutAvatars (int width, boolean rtl) {
    final int size = Screen.dp(expressive ? XgramR5People.AVATAR : 52f);
    final int left = expressive ? Screen.dp(XgramR5People.PADDING) : Screen.dp(11f);
    final int top = expressive ? Screen.dp(12f) : Screen.dp(10f);
    final int x = rtl ? width - left - size : left;
    if (twoAvatars) {
      // Two overlapping avatars inside the box: the newest at the bottom end, in front
      final int small = Math.round(size * .75f);
      final int backX = rtl ? x + size - small : x;
      final int frontX = rtl ? x : x + size - small;
      avatarBack.setBounds(backX, top, backX + small, top + small);
      avatar.setBounds(frontX, top + size - small, frontX + small, top + size);
    } else {
      avatar.setBounds(x, top, x + size, top + size);
    }
  }

  private int tileColor () {
    return expressive ? XgramR5People.rowColor(this) : Theme.fillingColor();
  }

  @Override
  protected void onDraw (Canvas c) {
    final XgramActivityRows.Row row = this.row;
    if (row == null) {
      return;
    }
    if (removeHelper != null) {
      removeHelper.save(c);
    }
    final int width = getMeasuredWidth();
    final boolean rtl = Lang.rtl();
    final XgramActivityItem item = row.newest();

    // Selected: the row whose chat is open on the right
    if (selection != null && selection.isRowSelected(row.key(), item.chatId) && XgramTwoPane.isHighlighted(tdlib, item.chatId)) {
      if (expressive) {
        XgramM3Profile.drawSelectionTile(c, width, getMeasuredHeight(), 1f);
      } else {
        c.drawColor(me.vkryl.core.ColorUtils.alphaColor(.6f, Theme.getColor(ColorId.fillingPressed)));
      }
    }

    layoutAvatars(width, rtl);
    if (twoAvatars) {
      if (avatarBack.needPlaceholder()) {
        avatarBack.drawPlaceholder(c);
      }
      avatarBack.draw(c);
      // The front one is cut out of the back one by a ring in the tile's colour
      paint.setColor(tileColor());
      c.drawCircle(avatar.centerX(), avatar.centerY(), avatar.getWidth() / 2f + Screen.dp(2f), paint);
    }
    if (avatar.needPlaceholder()) {
      avatar.drawPlaceholder(c);
    }
    avatar.draw(c);
    drawKindBadge(c, width, rtl);

    final int textStart = Screen.dp(72f);
    // Title line
    final int lineTop = Screen.dp(12f);
    final int lineHeight = Screen.dp(24f);
    if (titleText != null) {
      final int titleTop = lineTop + (lineHeight - titleText.getHeight()) / 2;
      final int titleX = rtl ? width - textStart - titleText.getWidth() : textStart;
      titleText.draw(c, titleX, titleTop);
      if (contextText != null) {
        final int contextTop = lineTop + (lineHeight - contextText.getHeight()) / 2;
        final int contextX = rtl ? titleX - contextText.getWidth() : titleX + titleText.getWidth();
        contextText.draw(c, contextX, contextTop);
      }
    }
    // Time and the New dot
    if (time != null) {
      final Paint timePaint;
      final float baseline;
      final float timeX;
      if (expressive) {
        timePaint = XgramR5People.metaPaint(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
        baseline = lineTop + lineHeight / 2f - (timePaint.descent() + timePaint.ascent()) / 2f;
        timeX = rtl ? Screen.dp(XgramR5People.PADDING) : width - Screen.dp(XgramR5People.PADDING) - timeWidth;
      } else {
        timePaint = ChatView.getTimePaint();
        baseline = Screen.dp(28f);
        timeX = rtl ? ChatView.getTimePaddingRight() : width - ChatView.getTimePaddingRight() - timeWidth;
      }
      c.drawText(time, timeX, baseline, timePaint);
      final float dotScale = Math.max(0f, Math.min(1f, dot.getValue()));
      if (dotScale > 0f) {
        final float radius = Screen.dp(4f) * dotScale;
        final float cx = rtl ? timeX + Screen.dp(4f) : timeX + timeWidth - Screen.dp(4f);
        final float cy = expressive ? lineTop + lineHeight + Screen.dp(6f) + Screen.dp(4f) : baseline + Screen.dp(10f) + Screen.dp(4f);
        paint.setColor(expressive ? XgramExpressive.color(XgramExpressive.PRIMARY) : Theme.getColor(ColorId.badge));
        c.drawCircle(cx, cy, radius, paint);
      }
    }
    // Body
    if (bodyText != null) {
      final int bodyX = rtl ? width - textStart - bodyText.getWidth() : textStart;
      if (prefixText != null) {
        final int prefixX = rtl ? width - textStart - prefixText.getWidth() : textStart;
        prefixText.draw(c, prefixX, bodyTop);
      }
      bodyText.draw(c, rtl ? Math.min(bodyX, width - textStart - bodyText.getWidth()) : bodyX, bodyTop, null, 1f, textMedia);
    }
    // Quote
    if (quoteText != null) {
      drawQuote(c, width, rtl, textStart);
    }

    if (removeHelper != null) {
      removeHelper.restore(c);
      removeHelper.draw(c);
    }
  }

  private void drawQuote (Canvas c, int width, boolean rtl, int textStart) {
    final Text quote = quoteText;
    if (quote == null) {
      return;
    }
    final int end = expressive ? Screen.dp(XgramR5People.PADDING) : ChatView.getTimePaddingRight();
    if (expressive) {
      // A 28dp strip with 12dp corners on surfaceContainerHighest, a 3dp primary bar at its start
      final float left = rtl ? end : textStart;
      final float right = rtl ? width - textStart : width - end;
      rect.set(left, quoteTop, right, quoteTop + Screen.dp(28f));
      final float radius = Screen.dp(12f);
      paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST));
      c.drawRoundRect(rect, radius, radius, paint);
      paint.setColor(XgramExpressive.color(XgramExpressive.PRIMARY));
      final float barLeft = rtl ? right - Screen.dp(3f) : left;
      c.save();
      c.clipRect(rect.left, rect.top, rect.right, rect.bottom);
      rect.set(barLeft, quoteTop, barLeft + Screen.dp(3f), quoteTop + Screen.dp(28f));
      c.drawRect(rect, paint);
      c.restore();
      final int textTop = quoteTop + (Screen.dp(28f) - quote.getHeight()) / 2;
      final int textX = rtl ? (int) right - Screen.dp(3f + 8f) - quote.getWidth() : (int) left + Screen.dp(3f + 8f);
      quote.draw(c, textX, textTop);
    } else {
      // Telegram X's reply line: a 2dp bar, the text after it
      final int barX = rtl ? width - textStart - Screen.dp(2f) : textStart;
      paint.setColor(Theme.getColor(ColorId.messageVerticalLine));
      c.drawRect(barX, quoteTop + Screen.dp(2f), barX + Screen.dp(2f), quoteTop + Screen.dp(18f), paint);
      final int textX = rtl ? width - textStart - Screen.dp(8f) - quote.getWidth() : textStart + Screen.dp(8f);
      quote.draw(c, textX, quoteTop + (Screen.dp(20f) - quote.getHeight()) / 2);
    }
  }

  /** The kind badge at the avatar's bottom end, in a ring of the tile's colour. */
  private void drawKindBadge (Canvas c, int width, boolean rtl) {
    final int size = Screen.dp(expressive ? XgramR5People.AVATAR : 52f);
    final int left = expressive ? Screen.dp(XgramR5People.PADDING) : Screen.dp(11f);
    final int top = expressive ? Screen.dp(12f) : Screen.dp(10f);
    final float badgeRadius = Screen.dp(expressive ? 10f : 9f);
    final float ring = expressive ? Screen.dp(2f) : Screen.dpf(1.5f);
    final float inset = Screen.dp(expressive ? 4f : 6f);
    final float cx = rtl ? width - left - size + inset : left + size - inset;
    final float cy = top + size - inset;
    paint.setColor(tileColor());
    c.drawCircle(cx, cy, badgeRadius + ring, paint);
    final XgramActivityRows.Row row = this.row;
    final XgramActivityItem item = row != null ? row.newest() : null;
    int container, content;
    if (expressive) {
      if (item != null && item.isMention()) {
        container = XgramExpressive.color(XgramExpressive.TERTIARY_CONTAINER);
        content = XgramExpressive.color(XgramExpressive.ON_TERTIARY_CONTAINER);
      } else if (item != null && item.isReaction()) {
        container = XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
        content = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      } else {
        container = XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER);
        content = XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER);
      }
    } else {
      container = Theme.getColor(ColorId.badge);
      content = Theme.getColor(ColorId.badgeText);
    }
    paint.setColor(container);
    c.drawCircle(cx, cy, badgeRadius, paint);
    if (badgeText != null) {
      badgeText.draw(c, Math.round(cx - badgeText.getWidth() / 2f), Math.round(cy - badgeText.getHeight() / 2f), null, 1f, textMedia);
    } else if (kindIcon != 0) {
      Drawable icon = getSparseDrawable(kindIcon, ColorId.NONE);
      if (icon != null) {
        final float glyph = Screen.dp(14f);
        final int iconWidth = Math.max(1, icon.getMinimumWidth()), iconHeight = Math.max(1, icon.getMinimumHeight());
        final float scale = glyph / Math.max(iconWidth, iconHeight);
        final int save = c.save();
        c.translate(cx, cy);
        if (Math.abs(scale - 1f) > .02f) {
          c.scale(scale, scale);
        }
        Drawables.draw(c, icon, -iconWidth / 2f, -iconHeight / 2f, Paints.getPorterDuffPaint(content));
        c.restoreToCount(save);
      }
    }
  }

  // Accessibility

  @NonNull
  private String describe () {
    XgramActivityRows.Row row = this.row;
    if (row == null) {
      return "";
    }
    XgramActivityItem item = row.newest();
    String body = item.isReaction() ? Lang.getString(R.string.XgramActivityReacted, "") :
      Lang.getString(item.isMention() ? R.string.XgramActivityMentioned : R.string.XgramActivityReplied, item.text != null ? item.text : "");
    String value = Lang.getString(R.string.XgramActivityA11yRow, senderLine(row) + ", " + chatLine(item), body, Lang.timeOrDateShort(item.eventDate, TimeUnit.SECONDS));
    return row.isNew() ? Lang.getString(R.string.XgramActivityA11yUnread, value) : value;
  }

  // Swipe to clear

  private RemoveHelper removeHelper;
  private float swipeDx;

  @Override
  public void setRemoveDx (float dx) {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    swipeDx = dx;
    removeHelper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    swipeDx = 0f;
    removeHelper.onSwipe();
  }

  /** A swipe is under way on this row: the list waits with rebuilding. */
  boolean isSwiping () {
    return swipeDx != 0f;
  }

  /** A row bound again starts without a swipe in progress. */
  void resetSwipe () {
    swipeDx = 0f;
    if (removeHelper != null) {
      removeHelper.reset();
    }
  }

  @Override
  public boolean hasOverlappingRendering () {
    return false;
  }
}
