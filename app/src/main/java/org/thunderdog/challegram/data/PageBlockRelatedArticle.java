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
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class PageBlockRelatedArticle extends PageBlock {
  private final TdApi.PageBlockRelatedArticle article;

  private TextWrapper title, description, info;
  private ImageFile miniThumbnail, preview, photo;
  @Nullable
  private final TdlibUi.UrlOpenParameters openParameters;

  public PageBlockRelatedArticle (ViewController<?> context, TdApi.PageBlockRelatedArticles articles, int quoteLevel, TdApi.PageBlockRelatedArticle article, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, articles, quoteLevel);
    this.article = article;
    this.openParameters = openParameters;

    if (!StringUtils.isEmpty(article.title)) {
      title = new TextWrapper(article.title, xgram ? com.fahadali.xgram.appearance.XgramM3Profile.flexProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M, false, com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_M)) : PageBlockRichText.getParagraphProvider(), TextColorSets.InstantView.NORMAL)
        .setMaxLines(3)
        .addTextFlags(Text.FLAG_ALL_BOLD | Text.FLAG_ARTICLE);
    }
    if (!StringUtils.isEmpty(article.description)) {
      description = new TextWrapper(article.description, xgram ? com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M)) : PageBlockRichText.getCaptionProvider(), xgram ? TextColorSets.InstantView.CAPTION : TextColorSets.InstantView.NORMAL)
        .setMaxLines(3)
        .addTextFlags(Text.FLAG_ARTICLE);
    }
    if (xgram) {
      // XGram M3E: the article's place in the list, for its tile's corners
      final TdApi.PageBlockRelatedArticle[] all = articles.articles;
      int index = -1;
      for (int i = 0; i < all.length; i++) {
        if (all[i] == article) {
          index = i;
          break;
        }
      }
      xgramFirst = index <= 0;
      xgramLast = index == -1 || index == all.length - 1;
    }
    String info;
    if (article.publishDate != 0 && !StringUtils.isEmptyOrBlank(article.author)) {
      info = Lang.getString(R.string.format_ivRelatedInfo, PageBlockRichText.buildAgo(context.tdlib(), article.publishDate), article.author);
    } else if (article.publishDate != 0) {
      info = PageBlockRichText.buildAgo(context.tdlib(), article.publishDate);
    } else if (!StringUtils.isEmptyOrBlank(article.author)) {
      info = article.author;
    } else {
      info = null;
    }
    if (!StringUtils.isEmpty(info)) {
      this.info = new TextWrapper(info, PageBlockRichText.getCaptionProvider(), TextColorSets.InstantView.CAPTION);
    }
    if (article.photo != null) {
      if (article.photo.minithumbnail != null) {
        miniThumbnail = new ImageFileLocal(article.photo.minithumbnail);
        miniThumbnail.setScaleType(ImageFile.CENTER_CROP);
        miniThumbnail.setDecodeSquare(true);
      }
      TdApi.PhotoSize size = Td.findSmallest(article.photo.sizes);
      if (size != null) {
        preview = new ImageFile(context.tdlib(), size.photo);
        preview.setScaleType(ImageFile.CENTER_CROP);
        preview.setDecodeSquare(true);
        preview.setSize(Screen.dp(50f));
        if (Math.max(size.width, size.height) <= 320) {
          photo = new ImageFile(context.tdlib(), size.photo);
          photo.setScaleType(ImageFile.CENTER_CROP);
          photo.setDecodeSquare(true);
          photo.setSize(Screen.dp(50f));
        }
      }
    }
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    receiver.requestFile(miniThumbnail, preview);
  }

  @Override
  public int getImageContentRadius () {
    return Screen.dp(xgram ? com.fahadali.xgram.appearance.XgramExpressive.CORNER_M : 3f); // XGram M3E: 12dp thumbnail corners
  }

  // XGram M3E: each related article is a segmented tile (surfaceContainer, 20dp outer and 4dp inner corners, 16dp in,
  // 2dp apart) with 16dp padding: the title in titleMedium, the description in bodyMedium onSurfaceVariant, the info
  // in bodySmall, and a 56dp thumbnail with 12dp corners at the end.

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private boolean xgramFirst = true, xgramLast = true;
  private static final float XGRAM_INSET = 16f, XGRAM_PADDING = 16f, XGRAM_THUMB = 56f;
  private final android.graphics.Path xgramTile = new android.graphics.Path();
  private final android.graphics.RectF xgramTileRect = new android.graphics.RectF();
  private final float[] xgramRadii = new float[8];

  private int xgramGap () {
    return xgramFirst ? Screen.dp(4f) : Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.LIST_GAP);
  }

  private int xgramTextX () {
    return Screen.dp(XGRAM_INSET + XGRAM_PADDING);
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(photo);
  }

  @Override
  public int getRelatedViewType () {
    return article.photo != null ? ListItem.TYPE_PAGE_BLOCK_MEDIA : ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  protected int computeHeight (View view, int width) {
    int availLineCount = 3;
    int maxWidth = width - Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) * 2;
    if (article.photo != null) {
      maxWidth -= Screen.dp(50f) + Screen.dp(12f);
    }
    if (xgram) {
      maxWidth = width - xgramTextX() * 2 - (article.photo != null ? Screen.dp(XGRAM_THUMB + 16f) : 0);
      if (title != null) {
        title.get(maxWidth);
        availLineCount -= title.getLineCount();
      }
      if (description != null) {
        description.setMaxLines(availLineCount);
        if (availLineCount > 0)
          description.get(maxWidth);
      }
      if (info != null) {
        info.get(maxWidth);
      }
      return Math.max(article.photo != null ? xgramGap() + Screen.dp(XGRAM_PADDING) * 2 + Screen.dp(XGRAM_THUMB) : 0, getContentHeight() + getContentTop());
    }
    if (title != null) {
      title.get(maxWidth);
      availLineCount -= title.getLineCount();
    }
    if (description != null) {
      description.setMaxLines(availLineCount);
      if (availLineCount > 0)
        description.get(maxWidth);
    }
    if (info != null) {
      info.get(maxWidth);
    }
    return Math.max(article.photo != null ? Screen.dp(SPACING_START) * 2 + Screen.dp(50f) : 0, getContentHeight() + getContentTop());
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return (title != null && title.onTouchEvent(view, e)) || (description != null && description.onTouchEvent(view, e)) || (info != null && info.onTouchEvent(view, e));
  }

  @Override
  protected int getContentTop () {
    if (xgram) {
      return xgramGap() + Screen.dp(XGRAM_PADDING);
    }
    return Screen.dp(SPACING_START);
  }

  @Override
  public boolean isClickable () {
    return true;
  }

  @Override
  public boolean onClick (View view, boolean isLongPress) {
    if (isLongPress) {
      context.tdlib().ui().openUrlOptions(context, article.url, new TdlibUi.UrlOpenParameters(openParameters).forceInstantView());
    } else {
      context.tdlib().ui().openUrl(context, article.url, new TdlibUi.UrlOpenParameters(openParameters).forceInstantView());
    }
    return true;
  }

  private static final float SPACING = 8f, SPACING_START = 12f;

  @Override
  protected int getContentHeight () {
    int totalHeight = 0;
    if (title != null) {
      totalHeight += title.getHeight() + Screen.dp(SPACING);
    }
    if (description != null && description.getMaxLines() > 0) {
      totalHeight += description.getHeight() + Screen.dp(SPACING);
    }
    if (info != null) {
      totalHeight += info.getHeight() + Screen.dp(SPACING);
    }
    if (totalHeight > 0) {
      totalHeight -= Screen.dp(SPACING);
    }
    totalHeight += Screen.dp(xgram ? XGRAM_PADDING : SPACING_START);
    return totalHeight;
  }

  private void xgramDrawTile (Canvas c, int viewWidth, int viewHeight) {
    final float outer = Screen.dpf(com.fahadali.xgram.appearance.XgramExpressive.LIST_OUTER_CORNER), inner = Screen.dpf(com.fahadali.xgram.appearance.XgramExpressive.LIST_INNER_CORNER);
    xgramTileRect.set(Screen.dpf(XGRAM_INSET), xgramGap(), viewWidth - Screen.dpf(XGRAM_INSET), viewHeight);
    final float top = xgramFirst ? outer : inner, bottom = xgramLast ? outer : inner;
    xgramRadii[0] = xgramRadii[1] = xgramRadii[2] = xgramRadii[3] = top;
    xgramRadii[4] = xgramRadii[5] = xgramRadii[6] = xgramRadii[7] = bottom;
    xgramTile.rewind();
    xgramTile.addRoundRect(xgramTileRect, xgramRadii, android.graphics.Path.Direction.CW);
    // Pure black: surfaceContainer is black like the page (filling, surface), so the tile takes the card step
    c.drawPath(xgramTile, org.thunderdog.challegram.tool.Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramAmoled.role(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER, com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH))));
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, ComplexReceiver iconReceiver) {
    if (xgram) {
      xgramDrawTile(c, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    int textX = xgram ? xgramTextX() : Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET);
    int textY = xgram ? getContentTop() : Screen.dp(SPACING_START);
    if (title != null) {
      title.draw(c, textX, textY);
      textY += title.getHeight() + Screen.dp(SPACING);
    }
    if (description != null && description.getMaxLines() > 0) {
      description.draw(c, textX, textY);
      textY += description.getHeight() + Screen.dp(SPACING);
    }
    if (info != null) {
      info.draw(c, textX, textY);
    }

    if (preview != null) {
      int viewWidth = view.getMeasuredWidth();
      int left = viewWidth - Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET) - Screen.dp(50f);
      int top = getContentTop();
      int right = viewWidth - Screen.dp(PageBlockRichText.TEXT_HORIZONTAL_OFFSET);
      int bottom = getContentTop() + Screen.dp(50f);
      if (xgram) {
        // XGram M3E: the 56dp thumbnail 16dp inside the tile's end
        right = viewWidth - xgramTextX();
        left = right - Screen.dp(XGRAM_THUMB);
        bottom = top + Screen.dp(XGRAM_THUMB);
      }
      if (receiver.needPlaceholder()) {
        preview.setBounds(left, top, right, bottom);
        if (preview.needPlaceholder()) {
          preview.drawPlaceholderRounded(c, getImageContentRadius());
        }
        preview.draw(c);
      }
      receiver.setBounds(left, top, right, bottom);
      receiver.draw(c);
    }
  }
}
