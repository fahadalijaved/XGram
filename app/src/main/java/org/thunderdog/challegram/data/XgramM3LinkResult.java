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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.fahadali.xgram.appearance.XgramM3SharedMedia;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.net.URLDecoder;

import me.vkryl.core.StringUtils;
import tgx.td.Td;
import tgx.td.TdExt;

/**
 * XGram: a shared link (the profile's Links tab) as a Material 3 Expressive list item, used in place of
 * {@link InlineResultMultiline} while Expressive design is on: 16dp padding, a 40dp leading element (the link's
 * thumbnail with 12dp corners, or a tonal tile with its letters), the headline in titleMedium onSurface (2 lines at
 * most), the description in bodyMedium onSurfaceVariant (2 lines) and the link in bodyMedium primary (2 lines).
 * The row is a tile of a segmented list inset {@code listInset} on each side; it is measured at the list's width.
 * The link is found exactly as {@link InlineResultMultiline} finds it.
 */
public class XgramM3LinkResult extends InlineResult<TdApi.InlineQueryResult> implements Text.ClickCallback {
  private static final float PADDING_VERTICAL = 12f, LINE_GAP = 2f;

  private String title, description;
  private TdApi.TextEntity[] descriptionEntities;
  private boolean isEmail;
  private String url;
  private TdApi.LinkPreview linkPreview;

  private final int listInset;
  private final TdlibAccentColor accentColor;
  private final @Nullable Letters letters;
  private float lettersWidth = -1f;
  private final RectF tileRect = new RectF();

  private TextWrapper titleWrap, descWrap, urlWrap;

  public XgramM3LinkResult (BaseActivity context, Tdlib tdlib, TdApi.Message message, int listInsetPx) {
    super(context, tdlib, TYPE_ARTICLE, null, null);
    this.listInset = listInsetPx;

    setMessage(message);

    TdApi.FormattedText text = Td.textOrCaption(message.content);
    TdApi.LinkPreview linkPreview = Td.isText(message.content) ? ((TdApi.MessageText) message.content).linkPreview : null;

    if (linkPreview != null) {
      this.title = TdExt.getContentTitle(linkPreview);
      this.description = linkPreview.description.text;
      this.descriptionEntities = linkPreview.description.entities;
      this.linkPreview = linkPreview;
      String urlInText = Td.findUrl(text, linkPreview.url, true);
      this.url = !StringUtils.isEmpty(urlInText) ? urlInText : linkPreview.url;
    } else if (text != null) {
      TdApi.TextEntity effectiveEntity = null;
      main: for (TdApi.TextEntity entity : text.entities) {
        //noinspection SwitchIntDef
        switch (entity.type.getConstructor()) {
          case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
            if (effectiveEntity == null) {
              this.url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
              effectiveEntity = entity;
            }
            break; // Don't break main to find link that is visible in the text
          }
          case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR: {
            if (effectiveEntity == null) {
              this.url = Td.substring(text.text, entity);
              this.isEmail = true;
              effectiveEntity = entity;
            }
            break main;
          }
          case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
          case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR: {
            break;
          }
          case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {
            String url = Td.substring(text.text, entity);
            boolean isDomain = StringUtils.isDomain(url);
            if (effectiveEntity == null || !isDomain) {
              this.url = url;
              effectiveEntity = entity;
              if (!isDomain) {
                break main;
              }
            }
            break;
          }
        }
      }
      if (effectiveEntity != null) {
        if (Td.isUrl(effectiveEntity.type)) {
          TdApi.FormattedText part1 = effectiveEntity.offset > 0 ? Td.substring(text, 0, effectiveEntity.offset) : null;
          TdApi.FormattedText part2 = effectiveEntity.offset + effectiveEntity.length < text.text.length() ? Td.substring(text, effectiveEntity.offset + effectiveEntity.length) : null;
          TdApi.FormattedText finalText = Td.trim(part1 != null && part2 != null ? Td.concat(part1, new TdApi.FormattedText("…", new TdApi.TextEntity[]{new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeTextUrl(url))}), part2) : part1 != null ? part1 : part2);
          if (finalText != null && !StringUtils.equalsOrBothEmpty(url, finalText.text)) {
            this.description = finalText.text;
            this.descriptionEntities = finalText.entities;
          }
        } else {
          this.description = text.text;
          this.descriptionEntities = text.entities;
        }
      }
    }

    final int leading = Screen.dp(XgramM3SharedMedia.LEADING_SIZE);
    setMediaPreview(MediaPreview.valueOf(tdlib, message, null, leading, Screen.dp(XgramM3SharedMedia.LEADING_CORNER)));

    if (StringUtils.isEmpty(title)) {
      this.title = isEmail ? Lang.getString(R.string.EMail) :
        StringUtils.isEmpty(url) ? Lang.getString(R.string.Link) :
        StringUtils.domainOf(url);
    }
    if (StringUtils.isEmpty(url)) {
      this.url = "";
    }

    this.accentColor = tdlib.accentColorForString(url);
    this.letters = TD.getLetters(title);

    layoutInternal(Screen.currentWidth());
  }

  @Override
  protected int getContentHeight () {
    int textHeight = 0;
    boolean hadText = false;
    if (titleWrap != null) {
      textHeight += titleWrap.getHeight();
      hadText = true;
    }
    if (descWrap != null) {
      if (hadText) {
        textHeight += Screen.dp(LINE_GAP);
      }
      hadText = true;
      textHeight += descWrap.getHeight();
    }
    if (urlWrap != null) {
      if (hadText) {
        textHeight += Screen.dp(LINE_GAP);
      }
      textHeight += urlWrap.getHeight();
    }
    return Math.max(Screen.dp(72f), textHeight + Screen.dp(PADDING_VERTICAL) * 2);
  }

  private int textHeight () {
    return getContentHeight() - Screen.dp(PADDING_VERTICAL) * 2;
  }

  @Override
  public void requestContent (ComplexReceiver receiver, boolean isInvalidate) {
    if (getMediaPreview() != null) {
      getMediaPreview().requestFiles(receiver, isInvalidate);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (descWrap != null) {
      descWrap.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  @Override
  protected void layoutInternal (int contentWidth) {
    if (titleWrap == null) {
      titleWrap = new TextWrapper(title, XgramM3SharedMedia.titleProvider(), TextColorSets.Regular.NORMAL)
        .setMaxLines(2);
      titleWrap.addTextFlags(Text.FLAG_ALL_BOLD);
    }
    if (descWrap == null && !StringUtils.isEmpty(description)) {
      descWrap = new TextWrapper(description, XgramM3SharedMedia.bodyProvider(), TextColorSets.Regular.LIGHT)
        .setMaxLines(2);
      if (descriptionEntities != null && descriptionEntities.length > 0) {
        descWrap.setEntities(TextEntity.valueOf(tdlib, new TdApi.FormattedText(description, descriptionEntities), null), (wrapper, text, specificMedia) -> {
          if (descWrap == wrapper) {
            currentViews.performWithViews(view -> {
              if (!text.invalidateMediaContent(((CustomResultView) view).getTextMediaReceiver(), specificMedia)) {
                ((CustomResultView) view).invalidateTextMedia(this);
              }
            });
          }
        });
      }
      descWrap.setViewProvider(currentViews);
      descWrap.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS);
    }
    if (urlWrap == null && url != null && !url.isEmpty()) {
      String displayUrl;
      try {
        displayUrl = URLDecoder.decode(url, "UTF-8");
      } catch (Throwable ignored) {
        displayUrl = url;
      }
      urlWrap = new TextWrapper(displayUrl, XgramM3SharedMedia.bodyProvider(), TextColorSets.Regular.NORMAL)
        .setEntities(TextEntity.valueOf(tdlib, displayUrl, new TdApi.TextEntity[] {new TdApi.TextEntity(0, displayUrl.length(), isEmail ? new TdApi.TextEntityTypeEmailAddress() : new TdApi.TextEntityTypeUrl())}, null), null);
      urlWrap.setMaxLines(2);
      if (linkPreview != null) {
        urlWrap.setClickCallback(this);
      }
      urlWrap.setViewProvider(currentViews);
      urlWrap.addTextFlags(Text.FLAG_CUSTOM_LONG_PRESS);
    }

    int availWidth = Math.max(Screen.dp(24f), contentWidth - listInset * 2 - Screen.dp(XgramM3SharedMedia.TEXT_START) - Screen.dp(XgramM3SharedMedia.ROW_PADDING));

    titleWrap.prepare(availWidth);
    if (descWrap != null) {
      descWrap.prepare(availWidth);
    }
    if (urlWrap != null) {
      urlWrap.prepare(availWidth);
    }
  }

  @Override
  public TdApi.LinkPreview findLinkPreview (String link) {
    if (linkPreview != null && TGWebPage.isPreviewOf(linkPreview.url, link)) {
      return linkPreview;
    }
    return null;
  }

  @Override
  public boolean onTouchEvent (View view, MotionEvent e) {
    return (titleWrap != null && titleWrap.onTouchEvent(view, e)) || (descWrap != null && descWrap.onTouchEvent(view, e)) || (urlWrap != null && urlWrap.onTouchEvent(view, e));
  }

  /** Centred in a two-line row; at the top (16dp) when the text runs to three lines or more, as M3 list items do. */
  private int leadingTop (int startY) {
    final int contentHeight = getContentHeight();
    final int size = Screen.dp(XgramM3SharedMedia.LEADING_SIZE);
    return startY + (contentHeight <= Screen.dp(72f) ? (contentHeight - size) / 2 : Screen.dp(16f));
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    final int size = Screen.dp(XgramM3SharedMedia.LEADING_SIZE);
    final int corner = Screen.dp(XgramM3SharedMedia.LEADING_CORNER);
    final int left = Screen.dp(XgramM3SharedMedia.ROW_PADDING);
    final int top = leadingTop(startY);
    MediaPreview preview = getMediaPreview();
    if (preview != null) {
      preview.draw(view, c, receiver, left, top, size, size, corner, 1f);
    } else {
      tileRect.set(left, top, left + size, top + size);
      lettersWidth = XgramM3SharedMedia.drawLetterTile(c, tileRect, corner, XgramM3SharedMedia.tonalIndex(accentColor.getId()), letters != null ? letters.text : null, lettersWidth);
    }

    final int textX = Screen.dp(XgramM3SharedMedia.TEXT_START);
    int textY = startY + Math.max(Screen.dp(PADDING_VERTICAL), (getContentHeight() - textHeight()) / 2);

    boolean hadText = false;
    if (titleWrap != null) {
      titleWrap.draw(c, textX, textY, null, 1f);
      textY += titleWrap.getHeight();
      hadText = true;
    }
    if (descWrap != null) {
      if (hadText) {
        textY += Screen.dp(LINE_GAP);
      }
      hadText = true;
      descWrap.draw(c, textX, textY, null, 1f, view.getTextMediaReceiver());
      textY += descWrap.getHeight();
    }
    if (urlWrap != null) {
      if (hadText) {
        textY += Screen.dp(LINE_GAP);
      }
      urlWrap.draw(c, textX, textY, null, 1f);
    }
  }

  @Override
  public void onDrawSelectionOver (Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, float anchorTouchX, float anchorTouchY, float selectFactor, String counter, @Nullable SimplestCheckBox checkBox) {
    final int radius = Screen.dp(11f);
    final int size = Screen.dp(XgramM3SharedMedia.LEADING_SIZE);
    final int x = Screen.dp(XgramM3SharedMedia.ROW_PADDING) + size - radius / 2;
    final int y = leadingTop(getStartY()) + size - radius / 2;

    SimplestCheckBox.draw(c, x, y, selectFactor, counter, checkBox);

    RectF rectF = Paints.getRectF();
    rectF.set(x - radius, y - radius, x + radius, y + radius);
    c.drawArc(rectF, 135f, 170f * selectFactor, false, Paints.getOuterCheckPaint(getSelectionRingColor(selectFactor))); // the selected tile under the check
  }
}
