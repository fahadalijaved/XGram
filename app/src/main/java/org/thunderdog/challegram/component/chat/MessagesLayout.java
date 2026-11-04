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
 * File created on 21/02/2016 at 21:28
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.KeyboardFrameLayout;

import me.vkryl.android.animator.Animated;

public class MessagesLayout extends RelativeLayout implements Animated {
  private MessagesController controller;

  public MessagesLayout (Context context) {
    super(context);
  }

  public void setController (MessagesController controller) {
    this.controller = controller;
  }

  boolean changedMin;
  int lastMeasuredWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    boolean emojiState = controller.getEmojiState();
    KeyboardFrameLayout emojiLayout = controller.getEmojiKeyboardLayout();

    boolean commandsState = controller.getCommandsState();
    CommandKeyboardLayout keyboardLayout = controller.getKeyboardLayout();

    int height = getMeasuredHeight();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    changedMin = height > getMeasuredHeight() && ((emojiState && emojiLayout != null) || (commandsState && keyboardLayout != null)) && getMeasuredWidth() == lastMeasuredWidth;
    lastMeasuredWidth = getMeasuredWidth();
  }

  // XGram: the pull to the next channel's move (MessagesController): while it runs, some children draw only inside a
  // band (the next chat's messages between the bars' edges, the old chat's bars outside them). Null otherwise.

  public interface XgramChildClip {
    /** True with {@code out} set (this layout's coordinates) to clip {@code child} to it, false to draw it whole. */
    boolean xgramClipChild (View child, RectF out);
  }

  private @Nullable XgramChildClip xgramChildClip;
  private final RectF xgramChildClipRect = new RectF();

  public void xgramSetChildClip (@Nullable XgramChildClip clip) {
    if (xgramChildClip != clip) {
      xgramChildClip = clip;
      invalidate();
    }
  }

  @Override
  protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    if (xgramChildClip != null && xgramChildClip.xgramClipChild(child, xgramChildClipRect)) {
      final int save = canvas.save();
      canvas.clipRect(xgramChildClipRect);
      final boolean result = super.drawChild(canvas, child, drawingTime);
      canvas.restoreToCount(save);
      return result;
    }
    return super.drawChild(canvas, child, drawingTime);
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (changedMin) {
      boolean emojiState = controller.getEmojiState();
      KeyboardFrameLayout emojiLayout = controller.getEmojiKeyboardLayout();

      boolean commandsState = controller.getCommandsState();
      CommandKeyboardLayout keyboardLayout = controller.getKeyboardLayout();

      if (emojiState && emojiLayout != null) {
        emojiLayout.onKeyboardStateChanged(true);
      }
      if (commandsState && keyboardLayout != null) {
        keyboardLayout.onKeyboardStateChanged(true);
      }
    }
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
