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
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextWrapper;

/**
 * XGram: paid media (TdApi.MessagePaidMedia), like official Telegram. While locked: the blurred preview with
 * "⭐ Unlock for N" over it (a tap asks, then pays the Stars), then the caption. Once bought, the post is shown as the
 * photo or video it holds ({@link #unlocked}), through Telegram X's own media bubble.
 */
public class TGMessagePaidMedia extends TGMessage {
  private final TdApi.MessagePaidMedia paid;
  private @Nullable TextWrapper captionText;
  private @Nullable Text pillText;
  private int mediaWidth, mediaHeight, contentWidth, contentHeight;
  private int lastStartX, lastStartY;

  /** The bought post as Telegram X's photo or video bubble, or null while it is locked. */
  static @Nullable TGMessage unlocked (MessagesManager context, TdApi.Message msg, TdApi.MessagePaidMedia paid) {
    if (paid.media == null || paid.media.length == 0) {
      return null;
    }
    final TdApi.PaidMedia first = paid.media[0];
    switch (first.getConstructor()) {
      case TdApi.PaidMediaPhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto photo = new TdApi.MessagePhoto(((TdApi.PaidMediaPhoto) first).photo, ((TdApi.PaidMediaPhoto) first).video, paid.caption, paid.showCaptionAboveMedia, false, false);
        return new TGMessageMedia(context, copyWithContent(msg, photo), photo, paid.caption);
      }
      case TdApi.PaidMediaVideo.CONSTRUCTOR: {
        TdApi.PaidMediaVideo media = (TdApi.PaidMediaVideo) first;
        TdApi.MessageVideo video = new TdApi.MessageVideo(media.video, new TdApi.AlternativeVideo[0], new TdApi.VideoStoryboard[0], media.cover, media.startTimestamp, paid.caption, paid.showCaptionAboveMedia, false, false);
        return new TGMessageMedia(context, copyWithContent(msg, video), video, paid.caption);
      }
      default:
        return null;
    }
  }

  /** A copy of the message with other content (TdApi objects keep their public fields: TDLib's JNI reads them). */
  private static TdApi.Message copyWithContent (TdApi.Message msg, TdApi.MessageContent content) {
    TdApi.Message copy = new TdApi.Message();
    try {
      for (java.lang.reflect.Field field : TdApi.Message.class.getFields()) {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
          field.set(copy, field.get(msg));
        }
      }
    } catch (IllegalAccessException e) {
      throw new UnsupportedOperationException(e);
    }
    copy.content = content;
    return copy;
  }

  public TGMessagePaidMedia (MessagesManager manager, TdApi.Message msg, TdApi.MessagePaidMedia paid) {
    super(manager, msg);
    this.paid = paid;
    if (paid.caption != null && paid.caption.text != null && !paid.caption.text.isEmpty()) {
      captionText = new TextWrapper(tdlib, paid.caption, getTextStyleProvider(), getTextColorSet(), openParameters(), (wrapper, text, specificMedia) -> invalidateTextMediaReceiver(text, specificMedia));
    }
  }

  private @Nullable TdApi.PaidMediaPreview preview () {
    for (TdApi.PaidMedia media : paid.media) {
      if (media.getConstructor() == TdApi.PaidMediaPreview.CONSTRUCTOR) {
        return (TdApi.PaidMediaPreview) media;
      }
    }
    return null;
  }

  @Override
  protected void buildContent (int maxWidth) {
    final TdApi.PaidMediaPreview preview = preview();
    final int width = preview != null && preview.width > 0 ? preview.width : 1;
    final int height = preview != null && preview.height > 0 ? preview.height : 1;
    mediaWidth = Math.min(maxWidth, Screen.dp(300f));
    mediaHeight = Math.max(Screen.dp(120f), Math.min(Screen.dp(360f), (int) ((long) mediaWidth * height / width)));
    final String label = Lang.getString(R.string.XgramPaidUnlock, Strings.buildCounter(paid.starCount));
    pillText = new Text.Builder(label, mediaWidth, Paints.robotoStyleProvider(14f), org.thunderdog.challegram.util.text.TextColorSets.WHITE).singleLine().allBold(true).build();
    contentWidth = mediaWidth;
    contentHeight = mediaHeight;
    if (captionText != null) {
      captionText.prepare(maxWidth);
      contentWidth = Math.max(contentWidth, Math.min(maxWidth, captionText.getWidth()));
      contentHeight += Screen.dp(8f) + captionText.getHeight();
    }
  }

  @Override
  protected int getContentWidth () {
    return contentWidth;
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    final TdApi.PaidMediaPreview preview = preview();
    if (preview != null && preview.minithumbnail != null) {
      ImageFileLocal file = new ImageFileLocal(preview.minithumbnail);
      receiver.requestFile(file);
    } else {
      receiver.requestFile(null);
    }
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (captionText != null) {
      captionText.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, org.thunderdog.challegram.loader.Receiver preview, org.thunderdog.challegram.loader.Receiver receiver) {
    lastStartX = startX;
    lastStartY = startY;
    receiver.setBounds(startX, startY, startX + mediaWidth, startY + mediaHeight);
    if (receiver.needPlaceholder()) {
      c.drawRect(startX, startY, startX + mediaWidth, startY + mediaHeight, Paints.fillingPaint(getDecentColor()));
    }
    receiver.draw(c);
    c.drawRect(startX, startY, startX + mediaWidth, startY + mediaHeight, Paints.fillingPaint(0x55000000));
    if (pillText != null) {
      final int pillWidth = pillText.getWidth() + Screen.dp(32f), pillHeight = Screen.dp(40f);
      final float cx = startX + mediaWidth / 2f, cy = startY + mediaHeight / 2f;
      final RectF rect = Paints.getRectF();
      rect.set(cx - pillWidth / 2f, cy - pillHeight / 2f, cx + pillWidth / 2f, cy + pillHeight / 2f);
      c.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, Paints.fillingPaint(0x99000000));
      pillText.draw(c, (int) (cx - pillText.getWidth() / 2f), (int) (cy - pillText.getHeight() / 2f));
    }
    if (captionText != null) {
      captionText.draw(c, startX, startX + contentWidth, 0, startY + mediaHeight + Screen.dp(8f), null, 1f, view.getTextMediaReceiver());
    }
  }

  // Unlocking

  private float touchX, touchY;
  private boolean touchMedia;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        touchX = e.getX();
        touchY = e.getY();
        touchMedia = e.getX() >= lastStartX && e.getX() < lastStartX + mediaWidth && e.getY() >= lastStartY && e.getY() < lastStartY + mediaHeight;
        if (touchMedia) {
          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE: {
        final int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        if (Math.abs(e.getX() - touchX) > slop || Math.abs(e.getY() - touchY) > slop) {
          touchMedia = false;
        }
        if (touchMedia) {
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP:
        if (touchMedia) {
          touchMedia = false;
          confirmUnlock();
          return true;
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        touchMedia = false;
        break;
    }
    return super.onTouchEvent(view, e);
  }

  private void confirmUnlock () {
    final String stars = Strings.buildCounter(paid.starCount);
    controller().showOptions(Lang.getString(R.string.XgramPaidUnlockConfirm, stars), new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramPaidUnlock, stars), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_star_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_done) {
          unlock();
        }
        return true;
      });
  }

  private void unlock () {
    final TdApi.InputInvoiceMessage invoice = new TdApi.InputInvoiceMessage(msg.chatId, msg.id);
    tdlib.send(new TdApi.GetPaymentForm(invoice, null), (form, error) -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (form.type.getConstructor() != TdApi.PaymentFormTypeStars.CONSTRUCTOR) {
        UI.showToast(R.string.XgramPaidOfficialApp, android.widget.Toast.LENGTH_LONG);
        return;
      }
      tdlib.send(new TdApi.SendPaymentForm(invoice, form.id, "", "", null, 0), (result, payError) -> {
        if (payError != null) {
          if (payError.message != null && payError.message.contains("BALANCE_TOO_LOW")) {
            UI.showToast(R.string.XgramPaidNoStars, android.widget.Toast.LENGTH_LONG);
          } else {
            UI.showError(payError);
          }
        }
        // the post comes back unlocked as a content update
      });
    });
  }
}
