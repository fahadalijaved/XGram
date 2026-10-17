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
 * File created on 04/11/2016
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class TGMessageGame extends TGMessage implements MediaWrapper.OnClickListener {
  private final TdApi.Game game;

  private @Nullable TextWrapper title;

  private TdApi.FormattedText currentText;
  private @Nullable TextWrapper text;

  public TGMessageGame (MessagesManager context, TdApi.Message msg, TdApi.Game game) {
    super(context, msg);
    this.game = game;
    setText();
  }

  private int getGameMaxWidth () {
    if (xgramCard()) {
      return getSmallestMaxContentWidth() - xgramMediaInset() * 2; // XGram: the media fills the card, 4dp in
    }
    return getSmallestMaxContentWidth() - getImagePaddingLeft();
  }

  // XGram M3E: the game is a link preview card (as TGWebPage's): a tonal container with 12dp corners and no accent
  // line, the media on top 4dp in (8dp corners, the card's minus the inset), then the title in titleSmall and the
  // description, 12dp in from the card's sides.

  private static boolean xgramCard () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  }

  private static int xgramMediaInset () {
    return Screen.dp(4f);
  }

  private static int xgramTextInset () {
    return Screen.dp(12f);
  }

  private int xgramTextMaxWidth () {
    return getSmallestMaxContentWidth() - xgramTextInset() * 2;
  }

  private @Nullable com.fahadali.xgram.appearance.XgramM3Content.TonalCard xgramCardShape;

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePadding + xBubblePaddingSmall;
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(type);
    }
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    if (mediaWrapper != null) {
      mediaWrapper.updateMessageId(oldMessageId, newMessageId, success);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().notifyInvalidateTargetsChanged();
    }
  }

  @Override
  protected void buildContent (int contentMaxWidth) {
    int maxWidth = getGameMaxWidth();
    int maxHeight = maxWidth * 2;
    final int textWidth = xgramCard() ? xgramTextMaxWidth() : maxWidth; // XGram

    if (title != null) {
      title.prepare(textWidth);
    }

    if (text != null) {
      text.prepare(textWidth);
    }

    buildImageOrGif(maxWidth, maxHeight);
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (text != null) {
      text.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private boolean setText () {
    final boolean titleChanged = !StringUtils.isEmpty(game.title) && (text == null || !text.getText().equals(game.title));
    if (titleChanged && xgramCard()) {
      // XGram M3E: titleSmall in the text colour
      title = new TextWrapper(game.title, com.fahadali.xgram.appearance.XgramM3Profile.flexTextProvider(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_S, com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_S)), getTextColorSet())
        .setClickCallback(clickCallback());
    } else if (titleChanged) {
      title = new TextWrapper(game.title, getTextStyleProvider(), getChatAuthorColorSet())
        .addTextFlags(Text.FLAG_ALL_BOLD)
        .setClickCallback(clickCallback());
    }

    final TdApi.FormattedText text;
    if (!Td.isEmpty(game.text)) {
      text = game.text;
    } else {
      text = new TdApi.FormattedText(game.description, Text.findEntities(game.description, Text.ENTITY_FLAGS_EXTERNAL));
    }

    boolean descriptionChanged = false;
    if (Td.isEmpty(text)) {
      if (currentText != null) {
        this.text = null;
        currentText = null;
        descriptionChanged = true;
      }
    } else if (currentText == null || !Td.equalsTo(currentText, text)) {
      descriptionChanged = true;
      currentText = text;
      this.text = new TextWrapper(text.text, getTextStyleProvider(), getTextColorSet())
        .setEntities(TextEntity.valueOf(tdlib, text, openParameters()), (wrapper, text1, specificMedia) -> {
          if (this.text == wrapper) {
            invalidateTextMediaReceiver(text1, specificMedia);
          }
        })
        .setClickCallback(clickCallback());
    }

    if (descriptionChanged) {
      invalidateTextMediaReceiver();
    }

    return titleChanged || descriptionChanged;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.MessageGame messageGame = (TdApi.MessageGame) newContent;
    TdApi.Game newGame = messageGame.game;
    game.text = newGame.text;
    game.title = newGame.title;
    game.description = newGame.description;
    if (setText()) {
      rebuildContent();
      return true;
    }
    return false;
  }

  private MediaWrapper mediaWrapper;

  private void buildImageOrGif (int maxWidth, int maxHeight) {
    if (mediaWrapper == null) {
      if (game.animation != null) {
        mediaWrapper = new MediaWrapper(context(), tdlib, game.animation, msg.chatId, msg.id, this, false);
      } else {
        mediaWrapper = new MediaWrapper(context(), tdlib, game.photo, msg.chatId, msg.id, this, false);
      }
      mediaWrapper.setViewProvider(currentViews);
      // mediaWrapper.setNeedRound(true, true);
      mediaWrapper.setOnClickListener(this);
    }

    int photoWidth = mediaWrapper.getContentWidth();
    int photoHeight = mediaWrapper.getContentHeight();

    float ratio = Math.min((float) maxWidth / (float) photoWidth, (float) maxHeight / (float) photoHeight);
    int cellWidth = (int) (ratio * photoWidth);
    int cellHeight = (int) (ratio * photoHeight);

    mediaWrapper.buildContent(cellWidth, cellHeight);
  }

  @Override
  public boolean needGifReceiver () {
    return mediaWrapper.getTargetGifFile() != null;
  }

  @Override
  protected int getContentWidth () {
    if (xgramCard()) {
      final int textWidth = Math.max(text != null ? text.getWidth() : 0, title != null ? title.getWidth() : 0);
      return Math.max(mediaWrapper.getCellWidth() + xgramMediaInset() * 2, textWidth + xgramTextInset() * 2);
    }
    return getImagePaddingLeft() + Math.max(text != null ? text.getWidth() : 0, mediaWrapper.getCellWidth());
  }

  /** XGram: the text block under the media in the card: 8dp, the title, 4dp, the description, 12dp. */
  private int xgramTextHeight () {
    int height = 0;
    if (title != null) {
      height += Screen.dp(8f) + title.getHeight();
    }
    if (text != null) {
      height += (height != 0 ? Screen.dp(4f) : Screen.dp(8f)) + text.getHeight();
    }
    return height + (height != 0 ? Screen.dp(12f) : xgramMediaInset());
  }

  private int textHeight () {
    int textHeight = 0;
    if (title != null) {
      textHeight += getTextPaddingTop() + title.getHeight();
    }
    if (text != null) {
      textHeight += (textHeight != 0 ? Screen.dp(4f) : getTextPaddingTop()) + text.getHeight();
    }
    if (textHeight > 0) {
      textHeight += getTextPaddingBottom();
    }
    return textHeight;
  }

  @Override
  protected int getContentHeight () {
    if (xgramCard()) {
      return xgramMediaInset() + mediaWrapper.getCellHeight() + xgramTextHeight();
    }
    return mediaWrapper.getCellHeight() + textHeight();
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    if (xgramCard()) {
      return Screen.dp(com.fahadali.xgram.appearance.XgramM3Content.CARD_CORNER_DP) - xgramMediaInset(); // XGram: the nested corner
    }
    return Screen.dp(Theme.getImageRadius());
  }

  private int getGameImageLeft () {
    if (xgramCard()) {
      return getContentX() + xgramMediaInset();
    }
    return getContentX() + getImagePaddingLeft();
  }

  private int getGameImageTop () {
    if (xgramCard()) {
      return getContentY() + xgramMediaInset();
    }
    return getContentY() + textHeight();
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    mediaWrapper.requestPreview(receiver);
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    mediaWrapper.requestImage(receiver);
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    mediaWrapper.requestGif(receiver);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    if (xgramCard()) {
      xgramDrawCard(view, c, startX, startY, preview, receiver);
      return;
    }
    mediaWrapper.draw(view, c, getGameImageLeft(), getGameImageTop(), preview, receiver, 1f);
    drawGameContent(view, c, startX, startY, maxWidth);
  }

  private void xgramDrawCard (MessageView view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver) {
    if (xgramCardShape == null) {
      xgramCardShape = new com.fahadali.xgram.appearance.XgramM3Content.TonalCard();
    }
    // the card's tint as the link preview's: onPrimaryContainer at 8% in outgoing bubbles, the line colour at 10% elsewhere
    final int fillColor = isOutgoingBubble() ?
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER), .08f) :
      com.fahadali.xgram.appearance.XgramExpressive.withAlpha(getVerticalLineColor(), .10f);
    xgramCardShape.draw(c, startX, startY, startX + getContentWidth(), startY + getContentHeight(),
      Screen.dp(com.fahadali.xgram.appearance.XgramM3Content.CARD_CORNER_DP), fillColor, 0, 0f, false);

    mediaWrapper.draw(view, c, getGameImageLeft(), getGameImageTop(), preview, receiver, 1f);

    final int left = startX + xgramTextInset();
    int textY = startY + xgramMediaInset() + mediaWrapper.getCellHeight();
    if (title != null) {
      textY += Screen.dp(8f);
      title.draw(c, left, textY, null, 1f);
      textY += title.getHeight();
    }
    if (text != null) {
      textY += title != null ? Screen.dp(4f) : Screen.dp(8f);
      text.draw(c, left, textY, null, 1f, view.getTextMediaReceiver());
    }
  }

  private static int getTextPaddingBottom () {
    return Screen.dp(7f);
  }

  private int getTextPaddingTop () {
    return text != null || title != null ? Screen.dp(2f) : 0;
  }

  private static int getImagePaddingLeft () {
    return Screen.dp(12f);
  }

  private void drawGameContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    RectF rectF = Paints.getRectF();
    int lineWidth = Screen.dp(3f);
    rectF.set(startX, startY, startX + lineWidth, startY + getContentHeight());
    c.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, Paints.fillingPaint(getVerticalLineColor()));

    final int left = startX + getImagePaddingLeft();
    int textY = 0;

    if (title != null) {
      textY += getTextPaddingTop();
      title.draw(c, left, startY + textY, null, 1f);
      textY += title.getHeight();
    }

    if (text != null) {
      textY += textY != 0 ? Screen.dp(4f) : getTextPaddingTop();
      text.draw(c, left, startY + textY, null, 1f, view.getTextMediaReceiver());
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (text != null) {
      text.performDestroy();
    }
  }

  @Override
  public boolean onClick (View view, MediaWrapper wrapper) {
    if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
      inlineKeyboard.openGame(view);
      return true;
    }
    return false;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return mediaWrapper.performLongPress(view) || res;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e) ||
      mediaWrapper.onTouchEvent(view, e))
      return true;
    int textY;
    if (title != null)
      textY = getTextPaddingTop() + title.getHeight() + Screen.dp(4f);
    else
      textY = getTextPaddingTop();
    return text != null && text.onTouchEvent(view, e);
  }
}
