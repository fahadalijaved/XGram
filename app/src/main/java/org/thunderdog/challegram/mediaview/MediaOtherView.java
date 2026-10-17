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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class MediaOtherView extends FrameLayoutFix implements Destroyable {
  private ImageReceiver receiver;

  public MediaOtherView (Context context) {
    super(context);
    this.receiver = new ImageReceiver(this, 0);
    setWillNotDraw(false);

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E: a 64dp thumbnail with 12dp corners; the remove badge's 48dp target covers its top-end corner
      receiver.setRadius(Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M));
      final int target = Screen.dp(com.fahadali.xgram.appearance.XgramR6B4Media.REMOVE_TARGET_DP);
      DeleteView view = new DeleteView(context);
      view.setId(R.id.btn_removePhoto);
      view.setLayoutParams(FrameLayoutFix.newParams(target, target, Gravity.TOP | (org.thunderdog.challegram.core.Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));
      view.setContentDescription(org.thunderdog.challegram.core.Lang.getString(R.string.XgramR6B4RemoveFromSelection));
      addView(view);
      return;
    }

    int padding = Screen.dp(4f);
    int paddingBig = Screen.dp(16f);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(24f) + padding + paddingBig, Screen.dp(24f) + padding + paddingBig, Gravity.RIGHT | Gravity.TOP);

    DeleteView view = new DeleteView(context);
    view.setId(R.id.btn_removePhoto);
    view.setLayoutParams(params);
    view.setPadding(paddingBig, padding, padding, paddingBig);
    addView(view);
  }

  public void setOnDeleteClick (View.OnClickListener onDeleteClick) {
    getChildAt(0).setOnClickListener(onDeleteClick);
  }

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int size = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? com.fahadali.xgram.appearance.XgramR6B4Media.STRIP_THUMB_DP : 100f); // XGram: 64dp thumbnails
    super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
    receiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
  }

  private ImageFile imageFile;

  public void setImage (ImageFile image) {
    this.imageFile = image;
    receiver.requestFile(image);
  }

  public ImageFile getImage () {
    return imageFile;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram M3E: the placeholder takes the thumbnail's 12dp corners, in the dark scheme's surfaceContainerHigh
      if (receiver.needPlaceholder()) {
        final float radius = Screen.dpf(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M);
        android.graphics.RectF rect = Paints.getRectF();
        rect.set(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
        c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramM3MediaChrome.onMedia(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGH)));
      }
      receiver.draw(c);
      return;
    }
    if (receiver.needPlaceholder()) {
      c.drawRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(0x22ffffff));
    }
    receiver.draw(c);
  }

  private static class DeleteView extends View {
    // XGram M3E: a 24dp error container badge with a close glyph at the thumbnail's top-end corner (4dp in), springing
    // in on SPATIAL_FAST when the strip shows it
    private final @androidx.annotation.Nullable com.fahadali.xgram.appearance.XgramR6B4Media.RemoveBadge xgramBadge;

    public DeleteView (Context context) {
      super(context);
      xgramBadge = com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? new com.fahadali.xgram.appearance.XgramR6B4Media.RemoveBadge(this) : null;
    }

    @Override
    protected void onAttachedToWindow () {
      super.onAttachedToWindow();
      if (xgramBadge != null) {
        xgramBadge.springIn();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (xgramBadge != null) {
        xgramBadge.draw(c, org.thunderdog.challegram.core.Lang.rtl());
        return;
      }
      int cx = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2;
      int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingLeft() - getPaddingRight()) / 2;

      c.drawCircle(cx, cy, Screen.dp(12f), Paints.fillingPaint(0xffffffff));
      c.drawCircle(cx, cy, Screen.dp(10f), Paints.fillingPaint(0xffe45356));

      int width = Screen.dp(5f);
      int height = Screen.dp(3f);
      c.drawRect(cx - width, cy - height / 2, cx + width, cy + height / 2 + height % 2, Paints.fillingPaint(0xffffffff));
    }
  }
}
