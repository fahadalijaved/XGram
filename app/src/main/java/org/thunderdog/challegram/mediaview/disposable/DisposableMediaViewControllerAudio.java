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
 * File created on 30/06/2024
 */
package org.thunderdog.challegram.mediaview.disposable;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.ExoPlayer;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageFile;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import tgx.td.Td;

class DisposableMediaViewControllerAudio extends DisposableMediaViewController {
  public DisposableMediaViewControllerAudio(@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private MessageView messageView;

  @Override
  protected FrameLayoutFix onCreateContentView (Context context) {
    contentView = new ContentView(context);

    messageView = new MessageView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return false;
      }

      @Override
      protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        onAttachedToRecyclerView();
      }

      @Override
      protected void onDetachedFromWindow () {
        super.onDetachedFromWindow();
        onDetachedFromRecyclerView();
      }
    };
    messageView.setParentOnMeasureDisabled(true);
    messageView.setUseComplexReceiver();
    messageView.setManager(copy.manager());
    messageView.setMessage(copy);
    contentView.addView(messageView);

    return contentView;
  }

  @Override
  protected void onExoPlayerCreated (ExoPlayer exoPlayer) {

  }

  private float revealStartPosition;
  private float revealEndPosition;

  private static final int[] tmpCords = new int[2];

  @Override
  public void prepareShowAnimation () {
    if (anchorView != null) {
      anchorView.getLocationOnScreen(tmpCords);
      revealStartPosition = tmpCords[1];
    } else {
      revealStartPosition = 0;
    }

    onChangeRevealFactor(getRevealFactor());
    super.prepareShowAnimation();
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    revealStartPosition = revealEndPosition;
    return super.launchHideAnimation(popup, originalAnimator);
  }

  @Override
  protected void onChangeRevealFactor (float factor) {
    final int viewWidth = contentView.getMeasuredWidth();
    final int viewHeight = contentView.getMeasuredHeight();
    final int messageHeight = messageView.getMeasuredHeight();
    if (viewWidth == 0 || viewHeight == 0 || messageHeight == 0) {
      return;
    }

    revealEndPosition = (viewHeight - messageHeight) / 2f;
    if (anchorView == null) {
      revealStartPosition = revealEndPosition;
    }

    if (xgram()) {
      // XGram M3E: the message rises from its bubble to the middle on the spatial spring, scaling from 90% as it fades
      // in, on a 75% scrim
      final float alpha = MathUtils.clamp(factor);
      final float scale = .9f + .1f * factor;
      messageView.setAlpha(alpha);
      messageView.setTranslationY(Math.round(MathUtils.fromTo(revealStartPosition, revealEndPosition, factor)));
      messageView.setScaleX(scale);
      messageView.setScaleY(scale);
      context.setPhotoRevealFactor(alpha);
      contentView.setBackgroundColor(ColorUtils.alphaColor(alpha * com.fahadali.xgram.appearance.XgramR6B4Media.ONCE_SCRIM_ALPHA, Color.BLACK));
      contentView.invalidate();
      return;
    }

    messageView.setAlpha(factor);
    messageView.setTranslationY(Math.round(MathUtils.fromTo(revealStartPosition, revealEndPosition, factor)));

    context.setPhotoRevealFactor(factor);
    contentView.setBackgroundColor(ColorUtils.alphaColor(factor * 0.9f, Color.BLACK));
    contentView.invalidate();
  }

  @Override
  protected void onChangeVisualProgress (float progress) {
    if (fileProgressComponent != null) {
      // send fake progress event
      fileProgressComponent.onTrackPlayProgress(tdlib, msgCopy.chatId, msgCopy.id, TD.getFileId(msgCopy), progress, getExoPlayerCurrentPosition(), getExoPlayerDuration(), false);
    }
    contentView.invalidate();
  }

  @Override
  protected long calculateProgressTickDelay (long playDuration) {
    return U.calculateDelayForDiameter(getVideoSize(), playDuration);
  }

  @Override
  public int getId () {
    return 0;
  }

  @Override
  public void destroy () {
    messageView.performDestroy();
    super.destroy();
  }



  /* Content */

  public class ContentView extends FrameLayoutFix {
    // XGram M3E: the voice message sits on a 28dp card, under the fire glyph and the "Plays once" chip
    private final @androidx.annotation.Nullable com.fahadali.xgram.appearance.XgramR6B4Media.OnceRow xgramOnce;
    private final android.graphics.Paint xgramCardPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF xgramCard = new android.graphics.RectF();

    public ContentView (@NonNull Context context) {
      super(context);
      xgramOnce = xgram() ? new com.fahadali.xgram.appearance.XgramR6B4Media.OnceRow(org.thunderdog.challegram.R.drawable.baseline_hot_once_24) : null;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      onChangeRevealFactor(getRevealFactor());
    }

    /** The card around the message, in this view's coordinates, following the message's translation and scale. */
    private void xgramLayoutCard () {
      final float pad = Screen.dpf(12f);
      float left, top, right, bottom;
      if (copy != null && copy.useBubbles()) {
        left = copy.getActualLeftContentEdge() - pad;
        right = copy.getActualRightContentEdge() + pad;
        top = copy.getTopContentEdge() - pad;
        bottom = copy.getBottomContentEdge() + pad;
      } else {
        left = Screen.dpf(16f);
        right = messageView.getMeasuredWidth() - Screen.dpf(16f);
        top = 0f;
        bottom = messageView.getMeasuredHeight();
      }
      final float px = messageView.getPivotX(), py = messageView.getPivotY();
      final float sx = messageView.getScaleX(), sy = messageView.getScaleY();
      final float dx = messageView.getLeft() + messageView.getTranslationX(), dy = messageView.getTop() + messageView.getTranslationY();
      xgramCard.set(
        dx + px + (left - px) * sx,
        dy + py + (top - py) * sy,
        dx + px + (right - px) * sx,
        dy + py + (bottom - py) * sy
      );
    }

    @Override
    protected void dispatchDraw (@NonNull android.graphics.Canvas canvas) {
      if (xgramOnce != null && messageView != null && messageView.getMeasuredHeight() > 0) {
        final float alpha = MathUtils.clamp(getRevealFactor());
        xgramLayoutCard();
        com.fahadali.xgram.appearance.XgramR6B4Media.drawOnceCard(canvas, xgramCard, alpha, xgramCardPaint);
        xgramOnce.draw(canvas, getMeasuredWidth() / 2f, xgramCard.top - Screen.dp(16f) - com.fahadali.xgram.appearance.XgramR6B4Media.OnceRow.heightPx() / 2f, alpha * alpha);
      }
      super.dispatchDraw(canvas);
    }
  }

  private static int getVideoSize () {
    return Math.min(Screen.smallestSide() - Screen.dp(80), Screen.dp(640));
  }


  private TGMessageFile copy;
  protected TdApi.Message msgCopy;
  private FileProgressComponent fileProgressComponent;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);

    msgCopy = Td.copyOf(tgMessage.getMessage());
    msgCopy.chatId = -1;  // set fake chat id and mark as listened
    if (msgCopy.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR) {
      ((TdApi.MessageVoiceNote) msgCopy.content).isListened = true;
    }

    copy = (TGMessageFile) TGMessage.valueOf(tgMessage.manager(), msgCopy);

    final var file = copy.findFileComponent(msgCopy.id);
    fileProgressComponent = file != null ? file.getFileProgress() : null;
    if (fileProgressComponent != null) {
      // send fake play event
      fileProgressComponent.onTrackStateChanged(tdlib, msgCopy.chatId, msgCopy.id, TD.getFileId(msgCopy), TGPlayerController.STATE_PLAYING);
      tdlib.files().unsubscribe(TD.getFileId(msgCopy), fileProgressComponent);
    }
  }
}
