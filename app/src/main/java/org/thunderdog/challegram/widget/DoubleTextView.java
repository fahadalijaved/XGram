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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.TextColorSets;

import me.vkryl.core.lambda.Destroyable;

public class DoubleTextView extends RelativeLayout implements RtlCheckListener, Destroyable, org.thunderdog.challegram.component.user.RemoveHelper.RemoveDelegate {
  // XGram M3E: an M3 two-line list item: a 40dp leading avatar or set preview 16dp from the start (previews clipped to
  // 12dp corners), the title in bodyLarge and the supporting text in bodyMedium (onSurface / onSurfaceVariant through
  // the theme's text colors), the action a small tonal button (Add, outlined Added once added), no hairlines, and an
  // optional trailing drag handle.
  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private static final float XGRAM_TITLE_TOP = 14f, XGRAM_SUBTITLE_TOP = 37f, XGRAM_HANDLE_SPACE = 36f;
  private @Nullable Runnable xgramOnHandleDown;
  private final Path xgramClip = new Path();
  private final android.graphics.RectF xgramClipRect = new android.graphics.RectF();

  private final TextView titleView, subtitleView;
  private final ComplexReceiver receiver;
  private final EmojiStatusHelper emojiStatusHelper;
  private @Nullable NonMaterialButton button;

  private boolean ignoreStartOffset;
  private int currentStartOffset;
  private float textWidth;

  @Override
  public void checkRtl () {
    if (titleView.getGravity() != Lang.gravity())
      titleView.setGravity(Lang.gravity());
    if (subtitleView.getGravity() != Lang.gravity())
      subtitleView.setGravity(Lang.gravity());
    int leftMargin = Screen.dp(72f) - (ignoreStartOffset ? currentStartOffset / 2 : 0);
    int rightMargin = Screen.dp(16f) + emojiStatusHelper.getWidth(Screen.dp(6));
    if (xgram) {
      leftMargin = Screen.dp(72f); // 16dp + the 40dp leading element + 16dp
      if (xgramOnHandleDown != null) {
        rightMargin += Screen.dp(XGRAM_HANDLE_SPACE);
      }
      updateLayoutParams(titleView, leftMargin, rightMargin, Screen.dp(XGRAM_TITLE_TOP));
      updateLayoutParams(subtitleView, leftMargin, rightMargin, Screen.dp(XGRAM_SUBTITLE_TOP));
      return;
    }
    updateLayoutParams(titleView, leftMargin, rightMargin, Screen.dp(15f));
    updateLayoutParams(subtitleView, leftMargin, rightMargin, Screen.dp(38f));
  }

  /**
   * XGram M3E: a trailing drag handle (a reorderable sticker or emoji set); {@code onHandleDown} runs when a touch
   * starts on it (start the drag there). Null removes it. Expressive design only.
   */
  public void xgramSetDragHandle (@Nullable Runnable onHandleDown) {
    if (!xgram) {
      return;
    }
    boolean changed = (this.xgramOnHandleDown == null) != (onHandleDown == null);
    this.xgramOnHandleDown = onHandleDown;
    if (changed) {
      checkRtl();
    }
  }

  @Override
  public boolean dispatchTouchEvent (android.view.MotionEvent e) {
    if (xgram && xgramOnHandleDown != null && e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && com.fahadali.xgram.appearance.XgramR6B6Lists.isOnDragHandle(this, e.getX(), com.fahadali.xgram.appearance.XgramR6B6Lists.DRAG_HANDLE_CENTER)) {
      xgramOnHandleDown.run();
    }
    return super.dispatchTouchEvent(e);
  }

  // Remove delegate (RemoveHelper.attach reads it on every row it moves; sticker sets are not swiped away)

  @Override
  public void setRemoveDx (float dx) { }

  @Override
  public void onRemoveSwipe () { }

  private static void updateLayoutParams (View view, int leftMargin, int rightMargin, int topMargin) {
    if (Views.setMargins(view, Lang.rtl() ? rightMargin : leftMargin, topMargin, Lang.rtl() ? leftMargin : rightMargin, 0)) {
      RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
      Views.removeRule(params, Lang.rtl() ? RelativeLayout.LEFT_OF : RelativeLayout.RIGHT_OF);
      params.addRule(Lang.rtl() ? RelativeLayout.RIGHT_OF : RelativeLayout.LEFT_OF, R.id.btn_double);
      Views.updateLayoutParams(view);
    }
  }

  public void ignoreStartOffset (boolean value) {
    this.ignoreStartOffset = value;
    checkRtl();
    invalidate();
  }

  public DoubleTextView (Context context) {
    super(context);

    this.emojiStatusHelper = new EmojiStatusHelper(null, this, null);

    int viewHeight = Screen.dp(72f);
    setPadding(0, Math.max(1, Screen.dp(.5f)), 0, 0);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));

    RelativeLayout.LayoutParams params;

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(15f);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(72f);
      params.leftMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_double);
    } else {
      params.leftMargin = Screen.dp(72f);
      params.rightMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_double);
    }

    titleView = new EmojiTextView(context);
    titleView.setScrollDisabled(true);
    titleView.setId(R.id.text_title);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setTextColor(Theme.textAccentColor());
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    titleView.setSingleLine(true);
    titleView.setLayoutParams(params);
    titleView.setGravity(Lang.gravity());

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(38f);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(72f);
      params.leftMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_double);
    } else {
      params.leftMargin = Screen.dp(72f);
      params.rightMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_double);
    }

    subtitleView = new EmojiTextView(context);
    subtitleView.setScrollDisabled(true);
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    subtitleView.setTextColor(Theme.textDecentColor());
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    subtitleView.setSingleLine(true);
    subtitleView.setGravity(Lang.gravity());
    subtitleView.setLayoutParams(params);

    int imageSize = viewHeight - Screen.dp(12f) * 2;
    int offset = currentStartOffset = viewHeight / 2 - imageSize / 2;

    this.receiver = new ComplexReceiver(this);

    if (xgram) {
      // XGram M3E: bodyLarge title, bodyMedium supporting text (the theme's text colors are onSurface / onSurfaceVariant)
      com.fahadali.xgram.appearance.XgramExpressive.applyText(titleView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false);
      com.fahadali.xgram.appearance.XgramExpressive.applyText(subtitleView, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
      ((RelativeLayout.LayoutParams) titleView.getLayoutParams()).topMargin = Screen.dp(XGRAM_TITLE_TOP);
      ((RelativeLayout.LayoutParams) subtitleView.getLayoutParams()).topMargin = Screen.dp(XGRAM_SUBTITLE_TOP);
    }

    addView(titleView);
    addView(subtitleView);
    setWillNotDraw(false);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceiver(receiver.getImageReceiver(0));
    layoutReceiver(receiver.getGifReceiver(0));
    layoutReceiver(receiver.getAvatarReceiver(0));
  }

  private void layoutReceiver (Receiver receiver) {
    int viewHeight = Screen.dp(72f);
    int imageSize = viewHeight - Screen.dp(12f) * 2;
    int offset = currentStartOffset = viewHeight / 2 - imageSize / 2;
    int startOffset = ignoreStartOffset ? offset / 2 : offset;
    if (xgram) {
      // XGram M3E: the 40dp leading element, 16dp from the start, centered on the row
      viewHeight = getMeasuredHeight() > 0 ? getMeasuredHeight() : viewHeight;
      imageSize = Screen.dp(com.fahadali.xgram.appearance.XgramR5People.AVATAR);
      offset = viewHeight / 2 - imageSize / 2;
      startOffset = Screen.dp(com.fahadali.xgram.appearance.XgramR5People.PADDING);
    }
    if (Lang.rtl()) {
      int x = getMeasuredWidth() - startOffset - imageSize;
      receiver.setBounds(x, offset, x + imageSize, offset + imageSize);
    } else {
      receiver.setBounds(startOffset, offset, startOffset + imageSize, offset + imageSize);
    }
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextAccentColorListener(titleView);
      themeProvider.addThemeTextDecentColorListener(subtitleView);
      themeProvider.addThemeInvalidateListener(this);
      themeProvider.addThemeInvalidateListener(button);
    }
  }

  private boolean isRounded;

  public void setIsRounded (boolean isRounded) {
    if (this.isRounded != isRounded) {
      this.isRounded = isRounded;
      receiver.getAvatarReceiver(0).setFullScreen(!isRounded, false);
    }
  }

  private void checkButton () {
    if (button == null) {
      RelativeLayout.LayoutParams params;
      params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(xgram ? 40f : 28f)); // XGram M3E: a small (40dp) button, 16dp from the end
      params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
      params.addRule(RelativeLayout.CENTER_VERTICAL);
      params.rightMargin = params.leftMargin = Screen.dp(xgram ? 16f : 19f);

      button = new NonMaterialButton(getContext());
      button.setId(R.id.btn_double);
      button.setLayoutParams(params);

      addView(button);
    }
  }

  public void setButton (@StringRes int string, View.OnClickListener onClickListener) {
    checkButton();
    button.setText(string);
    button.setOnClickListener(onClickListener);
    if (xgram && string == R.string.Add) {
      button.xgramSetAddStyle(R.string.XgramR5PeopleAdded); // XGram M3E: a filled tonal "Add", an outlined "Added" once added
    }
  }

  public void setIcon (@DrawableRes int icon, View.OnClickListener onClickListener) {
    checkButton();
    button.setIcon(icon);
    button.setOnClickListener(onClickListener);
    button.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
  }

  public @Nullable NonMaterialButton getButton () {
    return button;
  }

  @Override
  public void performDestroy () {
    receiver.performDestroy();
    emojiStatusHelper.performDestroy();
  }

  public void attach () {
    receiver.attach();
    emojiStatusHelper.attach();
  }

  public void detach () {
    receiver.detach();
    emojiStatusHelper.detach();
  }

  private @Nullable TGStickerSetInfo stickerSetInfo;
  private @Nullable Path stickerSetContour;
  private boolean useAvatarReceiver;

  public void setStickerSet (@NonNull TGStickerSetInfo stickerSet, String highlight) {
    needPlaceholder = false;
    titleView.setText(Highlight.toSpannable(stickerSet.getTitle(), highlight));
    subtitleView.setText(Lang.plural(stickerSet.isMasks() ? R.string.xMasks : stickerSet.isEmoji() ? R.string.xEmoji : R.string.xStickers, stickerSet.getSize()));
    receiver.getImageReceiver(0).requestFile(stickerSet.getPreviewImage());
    receiver.getGifReceiver(0).requestFile(stickerSet.getPreviewAnimation());
    receiver.getAvatarReceiver(0).clear();
    stickerSetContour = stickerSet.getPreviewContour(xgram ? Screen.dp(com.fahadali.xgram.appearance.XgramR5People.AVATAR) : Screen.dp(72f) - Screen.dp(12f) * 2);
    stickerSetInfo = stickerSet;
  }

  private boolean needPlaceholder;

  public void setText (CharSequence title, CharSequence subtitle) {
    titleView.setText(title);
    subtitleView.setText(subtitle);
    textWidth = U.measureText(titleView.getText(), Paints.getMediumTextPaint(16, false));
    checkRtl();
  }

  public void setEmojiStatus (Tdlib tdlib, @Nullable TdApi.User user) {
    emojiStatusHelper.updateEmoji(tdlib, user, TextColorSets.Regular.NORMAL);
    checkRtl();
  }

  public void setTitleColorId (@ColorId int colorId) {
    titleView.setTextColor(Theme.getColor(colorId));
  }

  public void setUserAvatar (Tdlib tdlib, long userId) {
    useAvatarReceiver = true;
    receiver.getAvatarReceiver(0).requestUser(tdlib, userId, AvatarReceiver.Options.SHOW_ONLINE);
    receiver.getGifReceiver(0).clear();
    receiver.getImageReceiver(0).clear();
  }

  public void setSenderAvatar (Tdlib tdlib, TdApi.MessageSender sender) {
    useAvatarReceiver = true;
    receiver.getAvatarReceiver(0).requestMessageSender(tdlib, sender, AvatarReceiver.Options.SHOW_ONLINE);
    receiver.getGifReceiver(0).clear();
    receiver.getImageReceiver(0).clear();
  }

  public void setChatAvatar (Tdlib tdlib, long chatId) {
    useAvatarReceiver = true;
    receiver.getAvatarReceiver(0).requestChat(tdlib, chatId, AvatarReceiver.Options.SHOW_ONLINE);
    receiver.getGifReceiver(0).clear();
    receiver.getImageReceiver(0).clear();
  }

  @Override
  protected void onDraw (Canvas c) {
    final boolean needThemedColorFilter = stickerSetInfo != null && stickerSetInfo.needThemedColorFilter();

    // XGram M3E: a set's preview sits in a 40dp box with 12dp corners
    final int xgramSave;
    if (xgram && !useAvatarReceiver && stickerSetInfo != null) {
      Receiver bounds = receiver.getImageReceiver(0);
      xgramClipRect.set(bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom());
      xgramClip.rewind();
      xgramClip.addRoundRect(xgramClipRect, Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M), Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M), Path.Direction.CW);
      xgramSave = Views.save(c);
      c.clipPath(xgramClip);
    } else {
      xgramSave = -1;
    }

    if (useAvatarReceiver) {
      AvatarReceiver avatarReceiver = receiver.getAvatarReceiver(0);
      if (avatarReceiver.needPlaceholder()) {
        avatarReceiver.drawPlaceholder(c);
      }
      avatarReceiver.draw(c);
    } else if (stickerSetInfo != null && stickerSetInfo.isPreviewAnimated()) {
      GifReceiver gifReceiver = receiver.getGifReceiver(0);
      if (needThemedColorFilter) {
        gifReceiver.setThemedPorterDuffColorId(ColorId.iconActive);
      } else {
        gifReceiver.disablePorterDuffColorFilter();
      }
      if (gifReceiver.needPlaceholder()) {
        gifReceiver.drawPlaceholderContour(c, stickerSetContour);
      }
      gifReceiver.draw(c);
    } else {
      ImageReceiver imageReceiver = receiver.getImageReceiver(0);
      if (needThemedColorFilter) {
        imageReceiver.setThemedPorterDuffColorId(ColorId.iconActive);
      } else {
        imageReceiver.disablePorterDuffColorFilter();
      }
      if (imageReceiver.needPlaceholder()) {
        if (stickerSetContour != null) {
          imageReceiver.drawPlaceholderContour(c, stickerSetContour);
        } else if (needPlaceholder) {
          imageReceiver.drawPlaceholderRounded(c, imageReceiver.getWidth() / 2f);
        }
      }
      imageReceiver.draw(c);
    }
    if (xgramSave != -1) {
      Views.restore(c, xgramSave);
    }
    if (xgram) {
      if (xgramOnHandleDown != null) {
        com.fahadali.xgram.appearance.XgramR6B6Lists.drawDragHandle(this, c, com.fahadali.xgram.appearance.XgramR6B6Lists.DRAG_HANDLE_CENTER, 1f);
      }
      int x = (int) (titleView.getX() + Math.min((textWidth) + Screen.dp(6), titleView.getMeasuredWidth() + Screen.dp(6)));
      emojiStatusHelper.draw(c, x, (int) titleView.getY());
      return; // XGram M3E: no hairline between the tiles
    }
    if (stickerSetInfo != null && stickerSetInfo.needSeparatorOnTop()) {
      int height = Math.max(1, Screen.dp(.5f));
      int offset = Screen.dp(72f);
      if (Lang.rtl()) {
        int viewWidth = getMeasuredWidth();
        c.drawRect(viewWidth - offset, 0, viewWidth, height, Paints.fillingPaint(Theme.fillingColor()));
        c.drawRect(0, 0, viewWidth - offset, height, Paints.fillingPaint(Theme.separatorColor()));
      } else {
        c.drawRect(0, 0, offset, height, Paints.fillingPaint(Theme.fillingColor()));
        c.drawRect(offset, 0, getMeasuredWidth(), height, Paints.fillingPaint(Theme.separatorColor()));
      }
    }

    int x = (int) (titleView.getX() + Math.min((textWidth) + Screen.dp(6), titleView.getMeasuredWidth() + Screen.dp(6)));
    emojiStatusHelper.draw(c, x, (int) titleView.getY());
  }
}
