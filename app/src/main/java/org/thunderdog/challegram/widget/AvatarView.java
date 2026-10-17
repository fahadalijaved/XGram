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
 * File created on 30/05/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.lambda.Destroyable;

public class AvatarView extends View implements Destroyable, TdlibCache.UserDataChangeListener, ChatListener, AttachDelegate {
  private static final int FLAG_NO_PLACEHOLDERS = 0x01;
  private static final int FLAG_NEED_FULL = 0x02;
  private static final int FLAG_NO_ROUND = 0x04;
  private static final int FLAG_NEED_OVERLAY = 0x08;
  private static final int FLAG_CUSTOM_WINDOW_MANAGEMENT = 0x10;

  private int flags;

  private final ImageReceiver receiver;
  private ImageReceiver preview;
  private Drawable overlayIcon;

  public AvatarView (Context context) {
    super(context);
    this.receiver = new ImageReceiver(this, 1);
    this.receiver.setRadius(0);
  }

  public void setNeedOverlay () {
    flags |= FLAG_NEED_OVERLAY;
    if (overlayIcon == null) {
      overlayIcon = Drawables.get(getResources(), R.drawable.baseline_camera_alt_24);
    }
  }

  public void setUseCustomWindowManagement () {
    flags |= FLAG_CUSTOM_WINDOW_MANAGEMENT;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return !(e.getAction() == MotionEvent.ACTION_DOWN && getAlpha() == 0f) && super.onTouchEvent(e);
  }

  public void setNoPlaceholders (boolean noPlaceholders) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NO_PLACEHOLDERS, noPlaceholders);
  }

  public void setNeedFull (boolean needFull) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NEED_FULL, needFull);
    if (needFull && preview == null) {
      preview = new ImageReceiver(this, 1);
      preview.copyBounds(receiver);
    }
  }

  public void setNoRound (boolean noRound) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NO_ROUND, noRound);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if ((flags & FLAG_CUSTOM_WINDOW_MANAGEMENT) == 0) {
      receiver.attach();
      if (preview != null) {
        preview.attach();
      }
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if ((flags & FLAG_CUSTOM_WINDOW_MANAGEMENT) == 0) {
      receiver.detach();
      if (preview != null) {
        preview.detach();
      }
    }
  }

  @Override
  public void attach () {
    receiver.attach();
    if (preview != null) {
      preview.attach();
    }
  }

  @Override
  public void detach () {
    receiver.detach();
    if (preview != null) {
      preview.detach();
    }
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    if (preview != null) {
      preview.destroy();
    }
    if (this.tdlib != null) {
      if (getUserId() != 0) {
        this.tdlib.cache().removeUserDataListener(getUserId(), this);
      }
      if (getChatId() != 0) {
        this.tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      }
    }
    this.tdlib = null;
    this.chat = null;
    this.user = null;
    this.userId = 0;
    this.chatId = 0;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    final int left = getPaddingLeft();
    final int top = getPaddingTop();
    final int right = getMeasuredWidth() - getPaddingRight();
    final int bottom = getMeasuredHeight() - getPaddingBottom();

    receiver.setBounds(left, top, right, bottom);
    if (needRounds()) {
      receiver.setRadius(Math.min(receiver.getWidth(), receiver.getHeight()) / 2);
    }
    if (xgramPicker) {
      receiver.setRadius(0); // XGram M3E: the picker clips the photo to its own (morphing) shape
    }
    if (needFull()) {
      preview.copyBounds(receiver);
    }
  }

  // XGram M3E: an M3 avatar picker (the photo of a group or channel being edited or created): a primaryContainer
  // circle holding the camera (icon pack) in onPrimaryContainer, or the photo in the same shape; pressed, its corners
  // spring from full to 16dp (SPATIAL_FAST, a snap with Reduce motion); a primary badge at its bottom end (an add mark,
  // or the edit glyph over a photo) ringed in the page color. Expressive design only.

  private boolean xgramPicker;
  private int xgramRingColorId = ColorId.background;
  private boolean xgramHasFile;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPress;
  private final android.graphics.Path xgramShape = new android.graphics.Path();
  private final android.graphics.RectF xgramRect = new android.graphics.RectF();
  private @Nullable Drawable xgramCamera, xgramEdit;

  /** Draws this view as the M3 avatar picker; nothing with Expressive design off. */
  public void xgramSetPicker (boolean picker) {
    picker = picker && com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    if (this.xgramPicker != picker) {
      this.xgramPicker = picker;
      if (picker && xgramCamera == null) {
        xgramCamera = Drawables.get(getResources(), R.drawable.baseline_camera_alt_24);
        xgramEdit = Drawables.get(getResources(), R.drawable.baseline_edit_24);
      }
      requestLayout();
      invalidate();
    }
  }

  /** The color behind the picker (the badge's ring), a theme color id; the page background by default. */
  public void xgramSetRingColorId (int colorId) {
    if (this.xgramRingColorId != colorId) {
      this.xgramRingColorId = colorId;
      invalidate();
    }
  }

  /** A photo that is not uploaded yet (a chat being created), or null for none. */
  public void xgramSetImageFile (@Nullable ImageFile file) {
    this.xgramHasFile = file != null;
    if (file != null) {
      file.setScaleType(ImageFile.CENTER_CROP);
      receiver.requestFile(file);
    } else if (account == null && getUserId() == 0 && getChatId() == 0) {
      receiver.clear();
    }
    invalidate();
  }

  @Override
  public void setPressed (boolean pressed) {
    super.setPressed(pressed);
    if (xgramPicker) {
      if (xgramPress == null) {
        xgramPress = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
        xgramPress.setThreshold(.005f);
      }
      xgramPress.animateTo(pressed ? 1f : 0f);
    }
  }

  private void xgramDrawPicker (Canvas c) {
    xgramRect.set(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
    final float size = Math.min(xgramRect.width(), xgramRect.height());
    if (size <= 0f) {
      return;
    }
    final float press = xgramPress != null ? xgramPress.getValue() : 0f;
    final float radius = com.fahadali.xgram.appearance.XgramExpressiveC.morphCorner(com.fahadali.xgram.appearance.XgramExpressive.CORNER_FULL, com.fahadali.xgram.appearance.XgramExpressive.CORNER_L, size, size, press);
    xgramShape.rewind();
    xgramShape.addRoundRect(xgramRect, radius, radius, android.graphics.Path.Direction.CW);
    final boolean photo = xgramHasFile || (hasPhoto && (account != null || getUserId() != 0 || getChatId() != 0));
    final float cx = xgramRect.centerX(), cy = xgramRect.centerY();
    if (photo) {
      final int saveCount = c.save();
      c.clipPath(xgramShape);
      if (receiver.needPlaceholder()) {
        c.drawRect(xgramRect, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SURFACE_CONTAINER_HIGHEST)));
      }
      receiver.draw(c);
      c.restoreToCount(saveCount);
    } else {
      c.drawPath(xgramShape, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER)));
      if (xgramCamera != null && xgramCamera.getMinimumWidth() > 0) {
        final float scale = (size * .375f) / xgramCamera.getMinimumWidth(); // 36dp in a 96dp picker
        final int saveCount = c.save();
        c.scale(scale, scale, cx, cy);
        Drawables.drawCentered(c, xgramCamera, cx, cy, Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER)));
        c.restoreToCount(saveCount);
      }
    }
    // The badge at the bottom end
    final float badge = Math.max(Screen.dp(20f), size * .29f), ring = Screen.dp(3f);
    final boolean rtl = org.thunderdog.challegram.core.Lang.rtl();
    final float bx = rtl ? xgramRect.left + badge / 2f : xgramRect.right - badge / 2f;
    final float by = xgramRect.bottom - badge / 2f;
    c.drawCircle(bx, by, badge / 2f + ring, Paints.fillingPaint(Theme.getColor(xgramRingColorId)));
    c.drawCircle(bx, by, badge / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY)));
    final int onPrimary = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY);
    if (photo && xgramEdit != null && xgramEdit.getMinimumWidth() > 0) {
      final float scale = (badge * .6f) / xgramEdit.getMinimumWidth();
      final int saveCount = c.save();
      c.scale(scale, scale, bx, by);
      Drawables.drawCentered(c, xgramEdit, bx, by, Paints.getPorterDuffPaint(onPrimary));
      c.restoreToCount(saveCount);
    } else {
      final float arm = badge * .25f, stroke = Math.max(1f, Screen.dp(1f));
      final android.graphics.Paint plus = Paints.fillingPaint(onPrimary);
      c.drawRect(bx - arm, by - stroke, bx + arm, by + stroke, plus);
      c.drawRect(bx - stroke, by - arm, bx + stroke, by + arm, plus);
    }
  }

  public long getUserId () {
    return user != null ? user.id : userId;
  }

  public long getChatId () {
    return chat != null ? chat.id : chatId;
  }

  private TdlibAccount account;
  private TdApi.User user;
  private TdApi.Chat chat;
  private long userId;
  private long chatId;
  private Tdlib tdlib;
  private boolean hasPhoto, allowSavedMessages;
  private AvatarPlaceholder.Metadata avatarPlaceholderMetadata;
  private AvatarPlaceholder avatarPlaceholder;

  public void setUser (TdlibAccount account) {
    this.account = account;
    if (account != null) {
      ImageFile file = account.getAvatarFile(false);
      if (hasPhoto = (file != null)) {
        receiver.requestFile(file);
      } else {
        avatarPlaceholderMetadata = account.getAvatarPlaceholderMetadata();
        avatarPlaceholder = null;
        receiver.clear();
      }
    } else {
      receiver.clear();
      hasPhoto = false;
    }
    invalidate();
  }

  public void setUser (Tdlib tdlib, long userId, boolean allowSavedMessages) {
    setUser(tdlib, tdlib.cache().user(userId), allowSavedMessages);
  }

  public void setUser (Tdlib tdlib, @Nullable TdApi.User user, boolean allowSavedMessages) {
    long newUserId = user != null ? user.id : 0;
    setUser(tdlib, user, newUserId, allowSavedMessages, false);
  }

  private void setUser (Tdlib tdlib, @Nullable TdApi.User user, long newUserId, boolean allowSavedMessages, boolean ignoreChecks) {
    long oldUserId = getUserId();
    if (oldUserId != newUserId || ignoreChecks) {
      if (oldUserId != 0) {
        this.tdlib.cache().removeUserDataListener(oldUserId, this);
      }
      this.user = user;
      this.userId = newUserId;
      this.tdlib = tdlib;
      if (newUserId != 0) {
        tdlib.cache().addUserDataListener(newUserId, this);
      }
      if (user != null) {
        setPhoto(tdlib, user, allowSavedMessages);
      } else {
        receiver.clear();
      }
    }
  }

  public void setChat (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    long newChatId = chat != null ? chat.id : 0;
    setChat(tdlib, chat, newChatId, false);
  }

  private void setChat (Tdlib tdlib, @Nullable TdApi.Chat chat, long newChatId, boolean ignoreChecks) {
    long oldChatId = getChatId();
    if (oldChatId != newChatId || ignoreChecks) {
      if (oldChatId != 0) {
        this.tdlib.listeners().unsubscribeFromChatUpdates(oldChatId, this);
      }
      this.chat = chat;
      this.tdlib = tdlib;
      if (newChatId != 0) {
        tdlib.listeners().subscribeToChatUpdates(newChatId, this);
      }
      if (chat != null) {
        setPhoto(tdlib, chat);
      } else {
        receiver.clear();
      }
    }
  }

  public void setMessageSender (Tdlib tdlib, @Nullable TdApi.MessageSender sender) {
    if (sender == null) {
      receiver.clear();
      return;
    };
    if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
      long userId = ((TdApi.MessageSenderUser) sender).userId;
      setUser(tdlib, tdlib.cache().user(userId), userId, false, true);
      return;
    }
    if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
      long chatId = ((TdApi.MessageSenderChat) sender).chatId;
      setChat(tdlib, tdlib.chat(chatId), chatId, true);
      return;
    }
    receiver.clear();
  }

  private static final int BLURRED_SIZE = 160;

  private boolean needFull () {
    return (flags & FLAG_NEED_FULL) != 0;
  }

  private float alpha;

  public void setMainAlpha (float alpha) {
    if (this.alpha != alpha) {
      if (this.alpha == receiver.getAlpha() && receiver.isLoaded()) {
        receiver.setAlpha(this.alpha = alpha);
      } else {
        this.alpha = alpha;
      }
    }
  }

  private float lettersSizeDp = 17f;

  public void setLettersSizeDp (float dp) {
    this.lettersSizeDp = dp;
  }

  private void setPhoto (Tdlib tdlib, TdApi.Chat chat) {
    if (hasPhoto = (chat.photo != null)) {
      setPhotoImpl(tdlib, chat.photo.small, chat.photo.big);
    } else {
      avatarPlaceholderMetadata = tdlib.chatPlaceholderMetadata(chat, true);
      avatarPlaceholder = null;
      receiver.clear();
    }
    invalidate();
  }

  private void setPhoto (Tdlib tdlib, TdApi.User user, boolean allowSavedMessages) {
    this.allowSavedMessages = allowSavedMessages;
    if (hasPhoto = (user.profilePhoto != null)) {
      setPhotoImpl(tdlib, user.profilePhoto.small, user.profilePhoto.big);
    } else {
      avatarPlaceholderMetadata = tdlib.cache().userPlaceholderMetadata(user, allowSavedMessages);
      avatarPlaceholder = null;
      receiver.clear();
    }
    invalidate();
  }

  private void setPhotoImpl (Tdlib tdlib, TdApi.File photoSmall, TdApi.File photoFull) {
    ImageFile imageFile = new ImageFile(tdlib, photoSmall);
    imageFile.setScaleType(ImageFile.CENTER_CROP);
    if (needFull()) {
      imageFile.setBlur(7);
      imageFile.setNeedFitSize();
      imageFile.setSize(ChatView.getDefaultAvatarCacheSize() == BLURRED_SIZE ? BLURRED_SIZE - 1 : BLURRED_SIZE);
      preview.requestFile(imageFile);
      imageFile = new ImageFile(tdlib, photoFull != null ? photoFull : photoSmall);
      imageFile.setScaleType(ImageFile.CENTER_CROP);
      receiver.requestFile(imageFile);
    } else {
      imageFile.setSize(ChatView.getDefaultAvatarCacheSize());
      receiver.requestFile(imageFile);
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    post(() -> {
      if (tdlib != null && getUserId() == user.id) {
        AvatarView.this.user = user;
        AvatarView.this.userId = user.id;
        setPhoto(tdlib, user, allowSavedMessages);
      }
    });
  }

  @Override
  public void onChatPhotoChanged (final long chatId, @Nullable final TdApi.ChatPhotoInfo photo) {
    post(() -> {
      if (tdlib != null && getChatId() == chatId) {
        setPhoto(tdlib, chat);
      }
    });
  }

  private boolean needPlaceholders () {
    return (flags & FLAG_NO_PLACEHOLDERS) == 0;
  }

  private boolean needRounds () {
    return (flags & FLAG_NO_ROUND) == 0;
  }

  private void drawPlaceholder (Canvas c, @ColorInt int color) {
    if (needRounds()) {
      c.drawCircle(receiver.centerX(), receiver.centerY(), receiver.getRadius(), Paints.fillingPaint(
        color
      ));
    } else {
      c.drawRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(color));
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (xgramPicker) {
      xgramDrawPicker(c); // XGram M3E
      return;
    }
    if (account != null || getUserId() != 0 || getChatId() != 0) {
      if (hasPhoto) {
        if (receiver.needPlaceholder() && (preview == null || preview.needPlaceholder())) {
          drawPlaceholder(c, Theme.placeholderColor());
        }
        if (preview != null && receiver.needPlaceholder()) {
          preview.draw(c);
        }
        receiver.draw(c);
      } else if (needPlaceholders()) {
        if ((flags & FLAG_NEED_OVERLAY) == 0) {
          if (avatarPlaceholderMetadata != null) {
            if (avatarPlaceholder == null)
              avatarPlaceholder = new AvatarPlaceholder(Screen.px(receiver.getWidth() / 2f), avatarPlaceholderMetadata, null);
            avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
          }
        } else {
          drawPlaceholder(c, avatarPlaceholderMetadata != null ? avatarPlaceholderMetadata.accentColor.getPrimaryColor() : Theme.placeholderColor());
        }
      }
    }
    if ((flags & FLAG_NEED_OVERLAY) != 0) {
      if (hasPhoto) {
        drawPlaceholder(c, Theme.getColor(ColorId.statusBar));
      }
      if (overlayIcon != null)
        Drawables.draw(c, overlayIcon, receiver.centerX() - overlayIcon.getMinimumWidth() / 2f, receiver.centerY() - overlayIcon.getMinimumHeight() / 2f, Paints.whitePorterDuffPaint());
    }
  }
}
