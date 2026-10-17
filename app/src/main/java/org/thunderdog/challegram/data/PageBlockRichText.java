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
 * File created on 23/02/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.PageBlockView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.DateUtils;
import tgx.td.ChatId;
import tgx.td.Td;

public class PageBlockRichText extends PageBlock {
  public static final float TEXT_HORIZONTAL_OFFSET = 16f;
  private static final float TEXT_HORIZONTAL_OFFSET_PRE_BLOCK = 14f;
  private static final float TEXT_HORIZONTAL_OFFSET_PULL_QUOTE = 22f;

  private static final float TEXT_SIZE_PARAGRAPH = 16f;
  private static final float TEXT_SIZE_BLOCK_QUOTE = 16f;

  private static final float TEXT_SIZE_TITLE = 24f;
  private static final float TEXT_SIZE_SUBTITLE = 21f;
  private static final float TEXT_SIZE_HEADER = 21f;
  private static final float TEXT_SIZE_SUBHEADER = 19f;

  private static final float TEXT_SIZE_AUTHOR = 14f;
  private static final float TEXT_SIZE_FOOTER = 14f;
  private static final float TEXT_SIZE_PULL_QUOTE = 19f;
  private static final float TEXT_SIZE_PREFORMATTED = 14f;
  private static final float TEXT_SIZE_LIST_TEXT = 16f;
  private static final float TEXT_SIZE_CAPTION = 14f;
  private static final float TEXT_SIZE_CREDIT = 12f;

  private @ColorId int backgroundColorId = ColorId.filling;
  private boolean isFullyRtl;
  private float textHorizontalOffset = TEXT_HORIZONTAL_OFFSET;
  private ClickHelper clickHelper;

  @Override
  public int getBackgroundColorId () {
    return backgroundColorId;
  }

  private float paddingTop = 6f, paddingBottom = 6f;

  // <h4>: margin: 18px 21px 7px;
  // <address>: margin: 12px 21px;
  // <p>: margin: 0 21px 12px;

  // Title of the page.
  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockTitle title, int quoteLevel, boolean isFirst, boolean hasKicker, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, title, quoteLevel);
    setText(new TdApi.RichTextBold(title.title), getTitleProvider(), TextColorSets.InstantView.TITLE, openParameters);
    this.paddingTop = hasKicker ? 10f : isFirst ? 20f : 16f;
    // setPadding(16f);
    // <h1>: margin: 21px 21px 12px;
  }

  // Subtitle of the page
  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockSubtitle subtitle, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, subtitle, quoteLevel);
    this.paddingTop = 0;
    setText(subtitle.subtitle, getSubtitleProvider(), TextColorSets.InstantView.SUBTITLE, openParameters);
    // setPadding(8f);
  }

  // <h1> in the text
  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockHeader header, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, header, quoteLevel);
    setText(new TdApi.RichTextBold(header.header), getHeaderProvider(), TextColorSets.InstantView.HEADER, Text.FLAG_ARTICLE, openParameters);
    this.paddingTop = 14f;
    this.paddingBottom = 8f;
    // setPadding(14f);
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockSubheader header, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, header, quoteLevel);
    setText(new TdApi.RichTextBold(header.subheader), getSubheaderProvider(), TextColorSets.InstantView.SUBHEADER, Text.FLAG_ARTICLE, openParameters);
    this.paddingTop = 8f;
    // setPadding(14f);
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockKicker kicker, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, kicker, quoteLevel);
    this.paddingTop = 16f;
    this.paddingBottom = 3f;
    if (xgram()) {
      // XGram M3E: the kicker in labelMedium, primary
      setText(new TdApi.RichTextBold(kicker.kicker), xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, true, false), com.fahadali.xgram.appearance.XgramM3Profile.TEXT_PRIMARY, Text.FLAG_ARTICLE, openParameters);
      return;
    }
    setText(new TdApi.RichTextBold(kicker.kicker), getCaptionProvider(), TextColorSets.InstantView.CAPTION, Text.FLAG_ARTICLE, openParameters);
    // setPadding(10f);
  }

  // Author & publication date of the page

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockAuthorDate authorDate, int quoteLevel, int viewCount, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, authorDate, quoteLevel);
    String author = TD.getText(authorDate.author).trim();
    this.paddingTop = this.paddingBottom = 8f;
    if (author.isEmpty() && authorDate.publishDate == 0) {
      return;
    }
    List<TdApi.RichText> richTexts = new ArrayList<>();
    if (!author.isEmpty()) {
      richTexts.add(authorDate.author);
    }
    if (authorDate.publishDate != 0) {
      if (!richTexts.isEmpty()) {
        richTexts.add(new TdApi.RichTextPlain(Lang.getString(R.string.format_ivAuthorDateSeparator)));
      }
      richTexts.add(new TdApi.RichTextPlain(buildAgo(context.tdlib(), authorDate.publishDate)));
    }
    if (viewCount != 0) {
      if (!richTexts.isEmpty()) {
        richTexts.add(new TdApi.RichTextPlain(Lang.getString(R.string.format_ivAuthorDateSeparator)));
      }
      richTexts.add(new TdApi.RichTextPlain(Lang.plural(R.string.xViews, viewCount)));
    }
    TdApi.RichText richText = richTexts.size() == 1 ? richTexts.get(0) : new TdApi.RichTexts(richTexts.toArray(new TdApi.RichText[0]));
    setText(richText, getAuthorProvider(), TextColorSets.InstantView.AUTHOR, openParameters);
  }

  public static long buildAgoUpdateTime (Tdlib tdlib, int publishDate) { // TODO use this to refresh 1 minute -> 2 minutes, today -> yesterday, etc
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    long ms = TimeUnit.SECONDS.toMillis(publishDate);
    c.setTimeInMillis(ms);
    int hour = c.get(Calendar.HOUR_OF_DAY);
    int minute = c.get(Calendar.MINUTE);
    int seconds = c.get(Calendar.SECOND);
    if (hour != 0 || minute != 0 || seconds != 0) {
      return Lang.getNextRelativeDateUpdateMs(ms, TimeUnit.MILLISECONDS, tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS, false, 0);
    } else {
      c.add(Calendar.DAY_OF_MONTH, 1);
      return DateUtils.getStartOfDay(c) - ms;
    }
  }

  public static String buildAgo (Tdlib tdlib, int publishDate) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    long ms = TimeUnit.SECONDS.toMillis(publishDate);
    c.setTimeInMillis(ms);
    int hour = c.get(Calendar.HOUR_OF_DAY);
    int minute = c.get(Calendar.MINUTE);
    int seconds = c.get(Calendar.SECOND);
    if (hour != 0 || minute != 0 || seconds != 0) {
      return Lang.getRelativeTimestamp(ms, TimeUnit.MILLISECONDS, tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS, false, 0);
    } else if (DateUtils.isToday(ms, TimeUnit.SECONDS)) {
      return Lang.getString(R.string.Today);
    } else if (DateUtils.isYesterday(ms, TimeUnit.SECONDS)) {
      return Lang.getString(R.string.Yesterday);
    } else {
      return Lang.getDate(ms, TimeUnit.MILLISECONDS);
    }
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockParagraph paragraph, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, paragraph, quoteLevel);
    setText(paragraph.text, getParagraphProvider(), TextColorSets.InstantView.NORMAL, Text.FLAG_ARTICLE, openParameters);
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockPreformatted preformatted, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, preformatted, quoteLevel);
    setText(preformatted.text, getPreformattedProvider(), TextColorSets.InstantView.NORMAL, Text.FLAG_ARTICLE, openParameters);
    this.backgroundColorId = ColorId.iv_preBlockBackground;
    this.textHorizontalOffset = TEXT_HORIZONTAL_OFFSET_PRE_BLOCK;
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockRelatedArticles articles, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, articles, quoteLevel);
    if (xgram()) {
      // XGram M3E: a section header (titleSmall, primary) over the related article tiles
      setText(new TdApi.RichTextBold(articles.header), xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_S, true, false), com.fahadali.xgram.appearance.XgramM3Profile.TEXT_PRIMARY, openParameters);
      this.paddingTop = 16f;
      this.paddingBottom = 8f;
      return;
    }
    setText(new TdApi.RichTextBold(articles.header), getParagraphProvider(), TextColorSets.InstantView.CAPTION, openParameters);
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockTable table, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, table, quoteLevel);
    this.paddingTop = 15f;
    this.paddingBottom = 2f;
    setText(table.caption, getCaptionProvider(), TextColorSets.InstantView.CAPTION, Text.FLAG_ARTICLE | Text.FLAG_ALIGN_CENTER, openParameters);
  }

  private BoolAnimator detailsOpened;

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockDetails details, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, details, quoteLevel);
    this.detailsOpened = new BoolAnimator(0, (id, factor, fraction, callee) -> currentViews.invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, details.isOpen);
    this.paddingTop = 15f;
    this.paddingBottom = 12f;
    setText(details.header, getParagraphProvider(), TextColorSets.InstantView.NORMAL, Text.FLAG_ARTICLE, openParameters);
    if (xgram()) {
      // XGram M3E: an expandable tile (surfaceContainer, 20dp corners, 16dp in) whose chevron turns on a spring; no
      // full-width separator under it
      com.fahadali.xgram.appearance.XgramAppMotion.use(detailsOpened, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, false);
      this.xgramCard = XGRAM_CARD_SINGLE;
      this.textHorizontalOffset = XGRAM_CARD_INSET + 16f;
      this.paddingTop += XGRAM_CARD_MARGIN;
      this.paddingBottom += XGRAM_CARD_MARGIN;
    }
  }

  public boolean toggleDetailsOpened () {
    return detailsOpened.toggleValue(true);
  }

  @Override
  public boolean isClickable () {
    return detailsOpened != null;
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockList list, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, list, quoteLevel);
    setText(new TdApi.RichTextPlain(""), getParagraphProvider(), TextColorSets.InstantView.NORMAL, openParameters);
  }

  private boolean forceBackground;
  private BoolAnimator subtitleVisible;
  private ImageFile avatarFile;
  private AvatarPlaceholder avatarPlaceholder;

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockChatLink chatLink, int quoteLevel, boolean isOverlay, int viewCount, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, chatLink, quoteLevel);
    this.paddingTop = this.paddingBottom = 16f;
    setText(new TdApi.RichTextBold(new TdApi.RichTextPlain(chatLink.title)), xgram() ? xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, true, false) : getParagraphProvider(), isOverlay ? TextColorSets.InstantView.CHAT_LINK_OVERLAY : TextColorSets.InstantView.NORMAL, openParameters); // XGram: the channel's name in titleMedium
    this.forceBackground = isOverlay;
    this.backgroundColorId = isOverlay ? ColorId.iv_chatLinkOverlayBackground : ColorId.iv_chatLinkBackground;
    this.clickHelper = new ClickHelper(new ClickHelper.Delegate() {
      @Override
      public boolean needClickAt (View view, float x, float y) {
        return y >= 0 && y < getComputedHeight();
      }

      @Override
      public void onClickAt (View view, float x, float y) {
        context.tdlib().ui().openPublicChat(context, chatLink.username, openParameters);
      }
    });
    if (block.getConstructor() == TdApi.PageBlockChatLink.CONSTRUCTOR) {
      long time = SystemClock.uptimeMillis();
      context.tdlib().client().send(new TdApi.SearchPublicChat(((TdApi.PageBlockChatLink) block).username), result -> {
        if (result.getConstructor() == TdApi.Chat.CONSTRUCTOR) {
          TdApi.Chat publicChat = ((TdApi.Chat) result);
          if (ChatId.isUserChat(publicChat.id))
            return;
          int memberCount = context.tdlib().chatMemberCount(publicChat.id);
          if (memberCount > 1 || context.tdlib().isBotChat(publicChat)) {
            showChatLinkSubtitle(publicChat, time, openParameters);
          } else if (publicChat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
            context.tdlib().cache().supergroupFull(ChatId.toSupergroupId(publicChat.id), fullInfo -> {
              int fullMemberCount = fullInfo != null ? fullInfo.memberCount : 0;
              if (fullMemberCount > 1) {
                showChatLinkSubtitle(publicChat, time, openParameters);
              }
            });
          }
        }
      });
    }
  }

  private void showChatLinkSubtitle (TdApi.Chat chat, long time, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    TdApi.ChatMemberStatus status = context.tdlib().chatStatus(chat.id);
    boolean needJoinButton = status != null && !TD.isMember(status);
    /*CharSequence text;
    if (viewCount > 1) {
      text = new SpannableStringBuilder(title).insert(0, " • ").insert(0, Lang.pluralBold(R.string.xViews, viewCount));
    } else {
      text = title;
    }*/
    context.runOnUiThreadOptional(() -> {
      int avatarSize = xgram() ? Screen.dp(40f) : (getComputedHeight() - Screen.dp(8f) * 2); // XGram M3E: 40dp
      avatarFile = context.tdlib().chatAvatar(chat.id);
      avatarPlaceholder = context.tdlib().chatPlaceholder(chat, false, Screen.px(avatarSize / 2f), null);
      currentViews.invalidateContent(this);

      CharSequence text = context.tdlib().status().chatStatus(chat);
      subtitle = new TextWrapper(text.toString(), getCreditProvider(), this.text.getTextColorSet())
        .setEntities(TextEntity.valueOf(context.tdlib(), text.toString(), TD.toEntities(text, false), openParameters), null)
        .setMaxLines(1);
      subtitle.prepare(getMaxWidth() - getTextPaddingLeft() - getTextPaddingRight() - (quoteLevel * Screen.dp(QUOTE_OFFSET)) - avatarSize);
      if (currentViews.hasAnyTargetToInvalidate() && context.isAttachedToNavigationController() && SystemClock.uptimeMillis() - time > 50) {
        subtitleVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
          currentViews.invalidate();
        }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
        subtitleVisible.setValue(true, true);
      } else {
        currentViews.invalidate();
      }
    });
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockFooter footer, int quoteLevel, boolean isPost, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, footer, quoteLevel);
    setText(footer.footer, getFooterProvider(), TextColorSets.InstantView.FOOTER, openParameters);
    if (!isPost) {
      this.backgroundColorId = ColorId.NONE;
    } else {
      this.paddingTop = 3f;
    }
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockBlockQuote blockQuote, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, blockQuote, quoteLevel);
    setText(blockQuote.credit, getCaptionProvider(), TextColorSets.InstantView.CAPTION, Text.FLAG_ARTICLE, openParameters);
    if (blockQuote.blocks.length > 0) {
      paddingTop = 3f;
    } else {
      paddingTop = 12f;
    }
    paddingBottom = 12f;
    // TextColorSets.InstantView.BLOCK_QUOTE
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockPullQuote pullQuote, int quoteLevel, boolean isCredit, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, pullQuote, quoteLevel);
    if (isCredit) {
      setText(pullQuote.credit, getCaptionProvider(), TextColorSets.InstantView.CAPTION, Text.FLAG_ALIGN_CENTER | Text.FLAG_ARTICLE, openParameters);
      paddingTop = 3f;
    } else {
      setText(pullQuote.text, getPullQuoteProvider(), TextColorSets.InstantView.PULL_QUOTE, Text.FLAG_ALIGN_CENTER | Text.FLAG_ARTICLE, openParameters);
      if (!Td.isEmpty(pullQuote.credit)) {
        paddingBottom = 3f;
      }
    }
    this.textHorizontalOffset = TEXT_HORIZONTAL_OFFSET_PULL_QUOTE;
    if (xgram()) {
      // XGram M3E: the pull quote (and its credit) in a surfaceContainer card with 20dp corners, 16dp in
      final boolean hasCredit = !Td.isEmpty(pullQuote.credit);
      this.xgramCard = isCredit ? XGRAM_CARD_BOTTOM : hasCredit ? XGRAM_CARD_TOP : XGRAM_CARD_SINGLE;
      this.textHorizontalOffset = XGRAM_CARD_INSET + 16f;
      if (xgramCard != XGRAM_CARD_BOTTOM) {
        this.paddingTop += XGRAM_CARD_MARGIN + 12f;
      }
      if (xgramCard != XGRAM_CARD_TOP) {
        this.paddingBottom += XGRAM_CARD_MARGIN + 12f;
      }
    }
  }

  // XGram M3E: cards and tiles drawn behind a block (the pull quote's card, a details header's tile)

  private static final int XGRAM_CARD_NONE = 0, XGRAM_CARD_SINGLE = 1, XGRAM_CARD_TOP = 2, XGRAM_CARD_BOTTOM = 3;
  private static final float XGRAM_CARD_INSET = 16f, XGRAM_CARD_MARGIN = 4f;
  private int xgramCard = XGRAM_CARD_NONE;
  private final float[] xgramRadii = new float[8];
  private final android.graphics.Path xgramPath = new android.graphics.Path();
  private final RectF xgramRect = new RectF();

  /** The card part of this block in {@code role}: rounded {@code cornerDp} on the open sides, 16dp in from the sides. */
  private void xgramDrawCard (Canvas c, int viewWidth, int viewHeight, int role, float cornerDp) {
    final float margin = Screen.dpf(XGRAM_CARD_MARGIN);
    final float top = xgramCard == XGRAM_CARD_BOTTOM ? 0f : margin;
    final float bottom = xgramCard == XGRAM_CARD_TOP ? viewHeight : viewHeight - margin;
    xgramRect.set(Screen.dpf(XGRAM_CARD_INSET), top, viewWidth - Screen.dpf(XGRAM_CARD_INSET), bottom);
    final float r = Math.min(Screen.dpf(cornerDp), Math.min(xgramRect.width(), xgramRect.height()) / 2f);
    final float rTop = xgramCard == XGRAM_CARD_BOTTOM ? 0f : r, rBottom = xgramCard == XGRAM_CARD_TOP ? 0f : r;
    xgramRadii[0] = xgramRadii[1] = xgramRadii[2] = xgramRadii[3] = rTop;
    xgramRadii[4] = xgramRadii[5] = xgramRadii[6] = xgramRadii[7] = rBottom;
    xgramPath.rewind();
    xgramPath.addRoundRect(xgramRect, xgramRadii, android.graphics.Path.Direction.CW);
    c.drawPath(xgramPath, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(role)));
  }

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlock mediaBlock, int quoteLevel, TdApi.PageBlockCaption caption, boolean isCredit, boolean isCover, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, mediaBlock, quoteLevel);
    if (isCredit) {
      if (!Td.isEmpty(caption.text)) {
        paddingTop = 2f;
      } else {
        paddingTop = 10f;
      }
      setText(caption.credit, getCreditProvider(), TextColorSets.InstantView.CAPTION, Text.FLAG_ARTICLE, openParameters);
      paddingBottom = isCover ? 2f : 8f;
    } else {
      if (!Td.isEmpty(caption.credit)) {
        paddingBottom = 2f;
      } else {
        paddingBottom = isCover ? 2f : 8f;
      }
      paddingTop = 10f;
      setText(caption.text, getCaptionProvider(), TextColorSets.InstantView.CAPTION, Text.FLAG_ARTICLE, openParameters);
    }
  }

  private ImageFile avatarMiniThumbnail, avatarPreview, avatarFull;
  private boolean needAvatar;
  private TdlibAccentColor accentColor;

  public PageBlockRichText (ViewController<?> context, TdApi.PageBlockEmbeddedPost embeddedPost, int quoteLevel, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, embeddedPost, quoteLevel);
    setText(new TdApi.RichTexts(new TdApi.RichText[]{
      new TdApi.RichTextPlain(embeddedPost.author),
      new TdApi.RichTextPlain("\n"),
      new TdApi.RichTextPlain(buildAgo(context.tdlib(), embeddedPost.date))
    }), getFooterProvider(), TextColorSets.InstantView.AUTHOR, Text.FLAG_ARTICLE, openParameters);

    this.needAvatar = true;
    TdApi.PhotoSize size = Td.findSmallest(embeddedPost.authorPhoto);
    accentColor = context.tdlib().accentColorForString(embeddedPost.author);
    if (size != null) {
      if (embeddedPost.authorPhoto.minithumbnail != null) {
        avatarMiniThumbnail = new ImageFileLocal(embeddedPost.authorPhoto.minithumbnail);
      } else {
        avatarMiniThumbnail = null;
      }

      avatarPreview = new ImageFile(context.tdlib(), size.photo);
      avatarPreview.setSize(ChatView.getDefaultAvatarCacheSize());

      size = TD.findSmallest(embeddedPost.authorPhoto, size);
      if (size != null) {
        avatarFull = new ImageFile(context.tdlib(), size.photo);
        avatarFull.setSize(ChatView.getDefaultAvatarCacheSize());//FIXME?
        avatarFull.setNoBlur();
      } else {
        avatarPreview.setNoBlur();
      }
    }
  }

  @Override
  public int getChildAnchorTop (@NonNull String anchor, int viewWidth) {
    Text text = this.text != null ? this.text.get(viewWidth) : null;
    if (text != null) {
      int lineIndex = text.findAnchorLineIndex(anchor);
      if (lineIndex > 0) {
        return getContentTop() + text.getLineHeight() * lineIndex;
      }
    }
    return 0;
  }

  private @Nullable TextWrapper text, subtitle;

  private void setText (TdApi.RichText richText, TextStyleProvider textStyleProvider, TextColorSet colorSet, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    setText(richText, textStyleProvider, colorSet, 0, openParameters);
  }

  private void setText (TdApi.RichText richText, TextStyleProvider textStyleProvider, TextColorSet colorSet, int flags, @Nullable  TdlibUi.UrlOpenParameters openParameters) {
    this.text = TextWrapper.parseRichText(context, context instanceof Text.ClickCallback ? (Text.ClickCallback) context : null, richText, textStyleProvider, colorSet, openParameters, (wrapper, text, specificMedia) -> {
      if (this.text == wrapper) {
        for (View view : currentViews) {
          if (view instanceof PageBlockView) {
            if (!text.invalidateMediaContent(((PageBlockView) view).getIconReceiver(), specificMedia)) {
              ((PageBlockView) view).invalidateIconsContent(this);
            }
          }
        }
      }
    });
    this.text.setViewProvider(currentViews);
    if (flags != 0) {
      this.text.addTextFlags(flags);
    }
  }

  @Override
  public int getRelatedViewType () {
    return needAvatar && avatarPreview != null ? ListItem.TYPE_PAGE_BLOCK_AVATAR : ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  public void requestIcons (ComplexReceiver receiver) {
    if (text != null) {
      text.requestMedia(receiver);
    } else {
      receiver.clear();
    }
    if (avatarFile != null) {
      receiver.getImageReceiver(Integer.MAX_VALUE).requestFile(avatarFile);
    }
  }

  private static final float QUOTE_OFFSET = 12f;

  private int getTextPaddingLeft () {
    return Math.max(getMinimumContentPadding(true), !isPost && listItemInfo != null ? 0 : Screen.dp(textHorizontalOffset)) + (needAvatar ? Screen.dp(40f) + Screen.dp(14f) : 0) + (detailsOpened != null ? Screen.dp(24f) : 0);
  }

  private int getTextPaddingRight () {
    return Math.max(getMinimumContentPadding(false), Screen.dp(textHorizontalOffset));
  }

  @Override
  protected int computeHeight (View view, int width) {
    if (text != null) {
      final int paddingLeft = getTextPaddingLeft();
      final int paddingRight = getTextPaddingRight();
      final int textMaxWidth = width - paddingLeft - paddingRight - (quoteLevel * Screen.dp(QUOTE_OFFSET));
      Text text = this.text.prepare(textMaxWidth);
      if (subtitle != null) {
        subtitle.prepare(textMaxWidth);
      }
      this.isFullyRtl = text.isFullyRtl();
      return this.text.getHeight() + getContentTop() + getTextPaddingBottom();
    }
    return 0;
  }

  @Override
  public boolean hasChildAnchor (@NonNull String anchor) {
    if (text != null) {
      TextEntity[] entities = text.getEntities();
      if (entities != null) {
        for (TextEntity entity : entities) {
          if (entity.hasAnchor(anchor))
            return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return (clickHelper != null && clickHelper.onTouchEvent(view, e)) || (text != null && text.onTouchEvent(view, e));
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (avatarFull == null || !TD.isFileLoaded(avatarFull.getFile())) {
      receiver.requestFile(avatarMiniThumbnail, avatarPreview);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(avatarFull);
  }

  private int getTextPaddingTop () {
    return Screen.dp(paddingTop);
  }

  private int getTextPaddingBottom () {
    return Screen.dp(paddingBottom);
  }

  @Override
  protected int getContentTop () {
    return getTextPaddingTop();
  }

  @Override
  protected int getContentHeight () {
    return text != null ? text.getHeight() : 0;
  }

  @Override
  public void drawInternal (View view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {
    if (text != null) {
      int viewWidth = view.getMeasuredWidth();
      int textLeft = getTextPaddingLeft();
      int textTop = getContentTop();

      if (forceBackground && backgroundColorId != ColorId.NONE) {
        c.drawRect(0, 0, viewWidth, getComputedHeight(), Paints.fillingPaint(Theme.getColor(backgroundColorId)));
      }
      if (xgramCard != XGRAM_CARD_NONE) {
        // XGram M3E: the details tile (20dp, as list tiles) or the pull quote card (20dp)
        xgramDrawCard(c, viewWidth, view.getMeasuredHeight(), com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER,
          detailsOpened != null ? com.fahadali.xgram.appearance.XgramExpressive.LIST_OUTER_CORNER : com.fahadali.xgram.appearance.XgramExpressive.CORNER_L_INC);
      }

      if (needAvatar) {
        int avatarSize = Screen.dp(40f);
        int avatarLeft = textLeft - avatarSize - Screen.dp(14f);
        int avatarTop = textTop - Screen.dp(4f);
        if (avatarPreview != null) {
          if (receiver.needPlaceholder()) {
            if (preview.needPlaceholder()) {
              c.drawCircle(avatarLeft + avatarSize / 2f, avatarTop + avatarSize / 2f, avatarSize / 2f, Paints.fillingPaint(Theme.placeholderColor()));
            }
            preview.setBounds(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize);
            preview.draw(c);
          }
          receiver.setBounds(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize);
          receiver.draw(c);
        } else {
          c.drawCircle(avatarLeft + avatarSize / 2f, avatarTop + avatarSize / 2f, avatarSize / 2f, Paints.fillingPaint(accentColor.getPrimaryColor()));
          // TODO letters
        }
      }

      for (int extraLevel = 0; extraLevel < quoteLevel; extraLevel++) {
        final int lineColor = Theme.getColor(ColorId.iv_blockQuoteLine);
        RectF rectF = Paints.getRectF();
        int lineWidth = Screen.dp(xgram() ? 4f : 3f); // XGram M3E: a 4dp full-corner primary bar
        int linePadding = Screen.dp(8f) / 2;
        rectF.top = textTop - linePadding;
        rectF.bottom = textTop + linePadding + text.getHeight();
        if (isFullyRtl) {
          rectF.right = viewWidth - textLeft - Screen.dp(QUOTE_OFFSET) * extraLevel;
          rectF.left = rectF.right - lineWidth;
        } else {
          rectF.left = textLeft + Screen.dp(QUOTE_OFFSET) * extraLevel;
          rectF.right = rectF.left + lineWidth;
        }
        c.drawRoundRect(rectF, lineWidth / 2f, lineWidth / 2f, Paints.fillingPaint(lineColor));

        if (mergeTop) {
          c.drawRect(rectF.left, 0, rectF.right, rectF.top + lineWidth, Paints.fillingPaint(lineColor));
        }
        if (mergeBottom) {
          c.drawRect(rectF.left, rectF.bottom - lineWidth, rectF.right, view.getMeasuredHeight(), Paints.fillingPaint(lineColor));
        }
      }

      int textX = textLeft;
      int textEndX;
      if (quoteLevel > 0) {
        if (isFullyRtl) {
          textEndX = viewWidth - textX - Screen.dp(QUOTE_OFFSET) * quoteLevel;
        } else {
          textX += Screen.dp(QUOTE_OFFSET) * quoteLevel;
          textEndX = textX + text.getWidth();
        }
      } else {
        textEndX = viewWidth - textX;
      }
      textLeft += Screen.dp(QUOTE_OFFSET) * quoteLevel;

      if (detailsOpened != null) {
        int iconLeft = textLeft - Screen.dp(18f);
        int iconTop = textTop + text.getLineCenterY();
        DrawAlgorithms.drawCollapse(c, iconLeft, iconTop, Theme.getColor(ColorId.iv_icon), detailsOpened.getFloatValue(), 0f);
        if (!xgram()) // XGram M3E: the tile is the boundary, no separator
        c.drawRect(0, view.getMeasuredHeight() - Screen.separatorSize(), viewWidth, view.getMeasuredHeight(), Paints.fillingPaint(Theme.getColor(ColorId.iv_separator)));
      }

      if (subtitle != null) {
        float subtitleFactor = (subtitleVisible != null ? subtitleVisible.getFloatValue() : 1f);
        textTop -= subtitleFactor * subtitle.getHeight() / 2f;
        int restoreCount = Views.save(c);
        int avatarPadding = Screen.dp(8f);
        int avatarSize = xgram() ? Screen.dp(40f) : getComputedHeight() - avatarPadding * 2; // XGram M3E: the 40dp list avatar
        int cx = (int) (textLeft + avatarSize / 2f - (forceBackground ? avatarPadding / 2 : 0) - (avatarSize + avatarPadding) * (1f - subtitleFactor));
        int cy = getComputedHeight() / 2;
        c.drawCircle(cx, cy, avatarSize / 2f, Paints.fillingPaint(ColorUtils.alphaColor(subtitleFactor, Theme.placeholderColor())));
        if (avatarFile != null && iconReceiver != null) {
          ImageReceiver r = iconReceiver.getImageReceiver(Integer.MAX_VALUE);
          r.setBounds(cx - avatarSize / 2, cy - avatarSize / 2, cx + avatarSize / 2, cy + avatarSize / 2);
          r.setRadius(avatarSize / 2f);
          r.setPaintAlpha(r.getPaintAlpha() * subtitleFactor);
          r.draw(c);
          r.restorePaintAlpha();
        } else if (avatarPlaceholder != null) {
          avatarPlaceholder.draw(c, cx, cy, subtitleFactor);
        }
        textLeft += (avatarSize + (forceBackground ? avatarPadding / 2 : avatarPadding)) * subtitleFactor;
        Views.restore(c, restoreCount);
      }
      text.draw(c, textLeft, textEndX, 0,  textTop, null, 1f, iconReceiver);
      if (subtitle != null) {
        subtitle.draw(c, textLeft, textEndX, 0, textTop + text.getHeight(), null, subtitleVisible != null ? subtitleVisible.getFloatValue() * .8f : .8f);
      }
    }
  }

  // Utils

  // Page title

  private static TextStyleProvider titleProvider, subtitleProvider, authorProvider, footerProvider, paragraphProvider, headerProvider, subheaderProvider, paragraphMonoProvider, listTextProvider, blockQuoteProvider, pullQuoteProvider, captionProvider, creditProvider;

  // XGram M3E: Instant View in Google Sans Flex on the type scale. Title headlineMedium, subtitle titleMedium, header
  // titleLarge, subheader titleMedium, paragraphs and lists bodyLarge, captions and credits bodySmall, author and
  // footer labelMedium, kicker labelMedium in primary, pull quotes titleLarge. Bold-only blocks (title, headers,
  // kicker) keep their style's weight; texts with entities get the emphasized weight for bold.

  private static boolean xgram () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  }

  private static TextStyleProvider xgramProvider (int ts, boolean boldOnly, boolean emphasized) {
    final float size = com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(ts);
    return boldOnly ? com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(ts, emphasized, size) : com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(ts, size);
  }

  public static TextStyleProvider getTitleProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_HEADLINE_M, true, true);
    }
    if (titleProvider == null) {
      titleProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      titleProvider.setTextSize(TEXT_SIZE_TITLE);
    }
    return titleProvider;
  }

  public static TextStyleProvider getSubtitleProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, false, false);
    }
    if (subtitleProvider == null) {
      subtitleProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      subtitleProvider.setTextSize(TEXT_SIZE_SUBTITLE);
    }
    return subtitleProvider;
  }

  public static TextStyleProvider getAuthorProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, false, false);
    }
    if (authorProvider == null) {
      authorProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      authorProvider.setTextSize(TEXT_SIZE_AUTHOR);
    }
    return authorProvider;
  }

  public static TextStyleProvider getFooterProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_LABEL_M, false, false);
    }
    if (footerProvider == null) {
      footerProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      footerProvider.setTextSize(TEXT_SIZE_FOOTER);
    }
    return footerProvider;
  }

  public static TextStyleProvider getBlockQuoteProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false, false);
    }
    if (blockQuoteProvider == null) {
      blockQuoteProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      blockQuoteProvider.setTextSize(TEXT_SIZE_BLOCK_QUOTE);
    }
    return blockQuoteProvider;
  }

  public static TextStyleProvider getPullQuoteProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_L, false, false);
    }
    if (pullQuoteProvider == null) {
      pullQuoteProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      pullQuoteProvider.setTextSize(TEXT_SIZE_PULL_QUOTE);
    }
    return pullQuoteProvider;
  }

  public static TextStyleProvider getHeaderProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_L, true, false);
    }
    if (headerProvider == null) {
      headerProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      headerProvider.setTextSize(TEXT_SIZE_HEADER);
    }
    return headerProvider;
  }

  public static TextStyleProvider getSubheaderProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, true, false);
    }
    if (subheaderProvider == null) {
      subheaderProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      subheaderProvider.setTextSize(TEXT_SIZE_SUBHEADER);
    }
    return subheaderProvider;
  }

  public static TextStyleProvider getParagraphProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false, false);
    }
    if (paragraphProvider == null) {
      paragraphProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      paragraphProvider.setTextSize(TEXT_SIZE_PARAGRAPH);
    }
    return paragraphProvider;
  }

  public static TextStyleProvider getPreformattedProvider () {
    if (paragraphMonoProvider == null) {
      paragraphMonoProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      paragraphMonoProvider.setTextSize(TEXT_SIZE_PREFORMATTED);
    }
    return paragraphMonoProvider;
  }

  public static TextStyleProvider getListTextProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false, false);
    }
    if (listTextProvider == null) {
      listTextProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      listTextProvider.setTextSize(TEXT_SIZE_LIST_TEXT);
    }
    return listTextProvider;
  }

  public static TextStyleProvider getCaptionProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_S, false, false);
    }
    if (captionProvider == null) {
      captionProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      captionProvider.setTextSize(TEXT_SIZE_CAPTION);
    }
    return captionProvider;
  }

  public static TextStyleProvider getCreditProvider () {
    if (xgram()) {
      return xgramProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_S, false, false);
    }
    if (creditProvider == null) {
      creditProvider = new TextStyleProvider(Fonts.newRobotoStorage());
      creditProvider.setTextSize(TEXT_SIZE_CREDIT);
    }
    return creditProvider;
  }
}
