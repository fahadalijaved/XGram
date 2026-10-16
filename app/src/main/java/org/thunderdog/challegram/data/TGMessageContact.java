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
 * File created on 03/05/2015 at 11:21
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.MotionEvent;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;

public class TGMessageContact extends TGMessage implements TdlibCache.UserDataChangeListener {
  private String name;
  private boolean nameFake;
  private String phone;

  private long userId;
  private TdApi.User user;

  private ImageFile avatar;
  private final TdlibAccentColor accentColor;
  private final Letters letters;

  private String tName;
  private String tPhone;

  private int nameWidth, phoneWidth;
  private int lettersWidth; // int pContactRight = maxTextWidth + getContentX() + Screen.dp(10f) + avatarSize + Screen.dp(6f);

  public TGMessageContact (MessagesManager context, TdApi.Message msg, TdApi.MessageContact rawContact) {
    super(context, msg);

    if (contactHeight == 0) {
      initSizes();
    }

    TdApi.Contact contact = rawContact.contact;
    this.xgramContact = contact; // XGram: the Add button
    this.name = TD.getUserName(contact.firstName, contact.lastName);
    this.nameFake = Text.needFakeBold(name);
    this.letters = TD.getLetters(contact.firstName, contact.lastName);
    this.phone = Strings.formatPhone(contact.phoneNumber, contact.userId != 0, true);
    this.userId = contact.userId;

    if (contact.userId != 0) {
      this.user = tdlib.cache().user(contact.userId);
      this.accentColor = tdlib.cache().userAccentColor(contact.userId);
      tdlib.cache().addUserDataListener(contact.userId, this);
    } else {
      this.accentColor = tdlib.accentColorForString(phone);
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (userId != 0) {
      tdlib.cache().removeUserDataListener(userId, this);
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        TGMessageContact.this.user = user;
        buildAvatar();
        // buildName();
        invalidateContent(this);
        invalidate();
      }
    });
  }

  private int lastMaxWidth;

  @Override
  protected void buildContent (int maxWidth) {
    buildAvatar();
    lettersWidth = Paints.measureLetters(letters, LETTERS_SIZE);
    lastMaxWidth = maxWidth - textLeft;
    buildName();
    if (com.fahadali.xgram.appearance.XgramW2Cards.on()) {
      xgramBuild(maxWidth);
    }
  }

  // XGram M3E wave 2: the contact as an inner card (12dp corners): a 40dp avatar, the name in titleMedium and the
  // number in bodyMedium, then the actions as a connected button group (size S, tonal): Message and Add for a
  // Telegram user who is not a contact yet, Message alone once they are, Call for a number without an account.
  // The card body keeps Telegram X's tap (profile or dialer).

  private static final int XGRAM_ACTIONS_NONE = 0, XGRAM_ACTIONS_MESSAGE = 1, XGRAM_ACTIONS_CALL = 2;

  private final TdApi.Contact xgramContact;
  private int xgramActions;
  private boolean xgramAddFits;
  private String xgramName, xgramPhone, xgramPrimaryLabel, xgramAddLabel;
  private int xgramCardWidth, xgramCardHeight;
  private android.graphics.RectF xgramCardRect;
  private android.graphics.RectF[] xgramButtonRects;
  private android.graphics.Path xgramPath;
  private float[] xgramCornersDp, xgramRadii;
  private final com.fahadali.xgram.appearance.XgramExpressive.Spring[] xgramPress = new com.fahadali.xgram.appearance.XgramExpressive.Spring[2];
  private int xgramTouchButton = -1;
  private boolean xgramTouchCard;

  private void xgramBuild (int maxWidth) {
    if (xgramCardRect == null) {
      xgramCardRect = new android.graphics.RectF();
      xgramButtonRects = new android.graphics.RectF[] {new android.graphics.RectF(), new android.graphics.RectF()};
      xgramPath = new android.graphics.Path();
      xgramCornersDp = new float[4];
      xgramRadii = new float[8];
    }
    if (userId != 0) {
      xgramActions = tdlib.isSelfUserId(userId) ? XGRAM_ACTIONS_NONE : XGRAM_ACTIONS_MESSAGE;
    } else {
      xgramActions = me.vkryl.core.StringUtils.isEmpty(phone) ? XGRAM_ACTIONS_NONE : XGRAM_ACTIONS_CALL;
    }
    final int pad = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.CARD_PADDING);
    final int textStart = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.AVATAR + com.fahadali.xgram.appearance.XgramW2Cards.AVATAR_GAP);
    final int innerMax = Math.max(0, maxWidth - pad * 2);
    final int textMax = Math.max(0, innerMax - textStart);
    xgramName = com.fahadali.xgram.appearance.XgramW2Cards.ellipsizeTitle(name != null ? name : "", nameFake, textMax);
    xgramPhone = com.fahadali.xgram.appearance.XgramW2Cards.ellipsizeBody(phone != null ? phone : "", textMax);
    final int textWidth = (int) Math.ceil(Math.max(com.fahadali.xgram.appearance.XgramW2Cards.measureTitle(xgramName, nameFake), com.fahadali.xgram.appearance.XgramW2Cards.measureBody(xgramPhone)));
    final int buttonPadding = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.BUTTON_PADDING) * 2;
    int buttonsMin = 0;
    xgramAddFits = false;
    xgramPrimaryLabel = xgramAddLabel = null;
    if (xgramActions == XGRAM_ACTIONS_MESSAGE) {
      xgramPrimaryLabel = Lang.getString(R.string.Message);
      xgramAddLabel = Lang.getString(R.string.XgramW2ContactAdd);
      final int primary = (int) Math.ceil(com.fahadali.xgram.appearance.XgramW2Cards.measureLabel(xgramPrimaryLabel)) + buttonPadding;
      final int add = (int) Math.ceil(com.fahadali.xgram.appearance.XgramW2Cards.measureLabel(xgramAddLabel)) + buttonPadding;
      // The two buttons share the card's width equally
      final int both = Math.max(primary, add) * 2 + Screen.dp(com.fahadali.xgram.appearance.XgramM3Connected.GAP);
      if (both <= innerMax) {
        xgramAddFits = true;
        buttonsMin = both;
      } else {
        buttonsMin = primary;
      }
    } else if (xgramActions == XGRAM_ACTIONS_CALL) {
      xgramPrimaryLabel = Lang.getString(R.string.Call);
      buttonsMin = (int) Math.ceil(com.fahadali.xgram.appearance.XgramW2Cards.measureLabel(xgramPrimaryLabel)) + buttonPadding;
    }
    final int inner = Math.min(innerMax, Math.max(textStart + textWidth, buttonsMin));
    xgramCardWidth = inner + pad * 2;
    xgramCardHeight = pad * 2 + Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.TITLE_LINE + com.fahadali.xgram.appearance.XgramW2Cards.BODY_LINE) +
      (xgramActions != XGRAM_ACTIONS_NONE ? Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.BUTTONS_GAP + com.fahadali.xgram.appearance.XgramW2Cards.BUTTON_HEIGHT) : 0);
  }

  private boolean xgramShowAdd () {
    return xgramActions == XGRAM_ACTIONS_MESSAGE && xgramAddFits && user != null && !TD.isContact(user);
  }

  private int xgramButtonCount () {
    if (xgramActions == XGRAM_ACTIONS_NONE) {
      return 0;
    }
    return xgramShowAdd() ? 2 : 1;
  }

  private void xgramSetPressed (int button, boolean pressed) {
    if (button < 0 || button >= xgramPress.length) {
      return;
    }
    if (xgramPress[button] == null) {
      if (!pressed) {
        return;
      }
      xgramPress[button] = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    }
    xgramPress[button].animateTo(pressed ? 1f : 0f);
  }

  private void xgramDraw (Canvas c, int startX, int startY, Receiver receiver) {
    final boolean outgoing = isOutgoingBubble();
    final float pad = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.CARD_PADDING);
    final float left = startX, top = startY, right = startX + xgramCardWidth, bottom = startY + xgramCardHeight;
    xgramCardRect.set(left, top, right, bottom);
    com.fahadali.xgram.appearance.XgramW2Cards.drawCard(c, left, top, right, bottom, com.fahadali.xgram.appearance.XgramW2Cards.cardColor(outgoing));

    // Leading avatar, centred on the two text lines
    final int avatarPx = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.AVATAR);
    final float rowTop = top + pad;
    final float titleLine = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.TITLE_LINE), bodyLine = Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.BODY_LINE);
    final int avatarLeft = (int) (left + pad);
    final int avatarTop = (int) (rowTop + (titleLine + bodyLine - avatarPx) / 2f);
    final float avatarRadiusPx = avatarPx / 2f;
    if (avatar == null) {
      c.drawCircle(avatarLeft + avatarRadiusPx, avatarTop + avatarRadiusPx, avatarRadiusPx, Paints.fillingPaint(accentColor.getPrimaryColor()));
      c.drawText(letters.text, avatarLeft + avatarRadiusPx - lettersWidth / 2f, avatarTop + avatarRadiusPx + Screen.dp(5.5f), Paints.getMediumTextPaint(LETTERS_SIZE, accentColor.getPrimaryContentColor(), letters.needFakeBold));
    } else {
      receiver.setBounds(avatarLeft, avatarTop, avatarLeft + avatarPx, avatarTop + avatarPx);
      if (receiver.needPlaceholder()) {
        c.drawCircle(avatarLeft + avatarRadiusPx, avatarTop + avatarRadiusPx, avatarRadiusPx, Paints.getPlaceholderPaint());
      }
      receiver.draw(c);
    }

    // Headline and supporting text
    final float textX = left + pad + Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.AVATAR + com.fahadali.xgram.appearance.XgramW2Cards.AVATAR_GAP);
    if (xgramName != null) {
      android.text.TextPaint namePaint = com.fahadali.xgram.appearance.XgramW2Cards.titlePaint(getTextColor(), nameFake);
      c.drawText(xgramName, textX, com.fahadali.xgram.appearance.XgramW2Cards.baseline(namePaint, rowTop, titleLine), namePaint);
    }
    if (xgramPhone != null) {
      android.text.TextPaint phonePaint = com.fahadali.xgram.appearance.XgramW2Cards.bodyPaint(getDecentColor());
      c.drawText(xgramPhone, textX, com.fahadali.xgram.appearance.XgramW2Cards.baseline(phonePaint, rowTop + titleLine, bodyLine), phonePaint);
    }

    // Connected button group
    final int count = xgramButtonCount();
    if (count == 0) {
      xgramButtonRects[0].setEmpty();
      xgramButtonRects[1].setEmpty();
      return;
    }
    final float buttonsTop = rowTop + titleLine + bodyLine + Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.BUTTONS_GAP);
    final float buttonsBottom = buttonsTop + Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.BUTTON_HEIGHT);
    final float buttonsLeft = left + pad, buttonsRight = right - pad;
    if (count == 2) {
      final float gap = Screen.dpf(com.fahadali.xgram.appearance.XgramM3Connected.GAP);
      final float width = (buttonsRight - buttonsLeft - gap) / 2f;
      xgramButtonRects[0].set(buttonsLeft, buttonsTop, buttonsLeft + width, buttonsBottom);
      xgramButtonRects[1].set(buttonsRight - width, buttonsTop, buttonsRight, buttonsBottom);
    } else {
      xgramButtonRects[0].set(buttonsLeft, buttonsTop, buttonsRight, buttonsBottom);
      xgramButtonRects[1].setEmpty();
    }
    final int container = com.fahadali.xgram.appearance.XgramW2Cards.buttonColor();
    final int content = com.fahadali.xgram.appearance.XgramW2Cards.buttonContentColor();
    for (int i = 0; i < count; i++) {
      android.graphics.RectF rect = xgramButtonRects[i];
      final float press = xgramPress[i] != null ? xgramPress[i].getValue() : 0f;
      com.fahadali.xgram.appearance.XgramW2Cards.drawConnectedButton(c, rect, i, count, press, container, content, xgramPath, xgramCornersDp, xgramRadii);
      final String label = i == 0 ? xgramPrimaryLabel : xgramAddLabel;
      if (label != null) {
        android.text.TextPaint labelPaint = com.fahadali.xgram.appearance.XgramW2Cards.labelPaint(content);
        c.drawText(label, rect.centerX(), com.fahadali.xgram.appearance.XgramW2Cards.baseline(labelPaint, rect.top, rect.height()), labelPaint);
      }
    }
  }

  private void xgramPerformAction (int button) {
    if (xgramActions == XGRAM_ACTIONS_MESSAGE) {
      if (button == 0) {
        tdlib.ui().openPrivateChat(controller(), userId, new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().urlOpenParameters(openParameters()));
      } else if (xgramContact != null) {
        tdlib.ui().addContact(controller(), xgramContact);
      }
    } else if (xgramActions == XGRAM_ACTIONS_CALL) {
      UI.openNumber(phone);
    }
  }

  private void xgramReleaseTouch () {
    if (xgramTouchButton != -1) {
      xgramSetPressed(xgramTouchButton, false);
      xgramTouchButton = -1;
    }
    xgramTouchCard = false;
  }

  private boolean xgramOnTouchEvent (MessageView view, MotionEvent e) {
    if (xgramCardRect == null) {
      return false;
    }
    final float x = e.getX(), y = e.getY();
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        xgramReleaseTouch();
        currentTouchX = x;
        currentTouchY = y;
        final int count = xgramButtonCount();
        for (int i = 0; i < count; i++) {
          if (xgramButtonRects[i].contains(x, y)) {
            xgramTouchButton = i;
            xgramSetPressed(i, true);
            return true;
          }
        }
        if (xgramCardRect.contains(x, y)) {
          xgramTouchCard = true;
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_MOVE: {
        if (Math.abs(x - currentTouchX) >= Screen.getTouchSlop() || Math.abs(y - currentTouchY) >= Screen.getTouchSlop()) {
          if (xgramTouchButton != -1 && !xgramButtonRects[xgramTouchButton].contains(x, y)) {
            xgramSetPressed(xgramTouchButton, false);
            xgramTouchButton = -1;
          }
          xgramTouchCard = false;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        xgramReleaseTouch();
        break;
      }
      case MotionEvent.ACTION_UP: {
        final int button = xgramTouchButton;
        final boolean card = xgramTouchCard;
        xgramReleaseTouch();
        if (button != -1 && button < xgramButtonCount() && xgramButtonRects[button].contains(x, y)) {
          me.vkryl.android.ViewUtils.onClick(view);
          xgramPerformAction(button);
          return true;
        }
        if (card && Math.abs(x - currentTouchX) < Screen.getTouchSlop() && Math.abs(y - currentTouchY) < Screen.getTouchSlop()) {
          if (userId != 0) {
            tdlib.ui().openPrivateProfile(controller(), userId, openParameters());
          } else {
            UI.openNumber(phone);
          }
          return true;
        }
        break;
      }
    }
    return false;
  }

  private void buildName () {
    if (lastMaxWidth > 0) {
      tName = TextUtils.ellipsize(name, Paints.getBoldPaint15(nameFake), lastMaxWidth, TextUtils.TruncateAt.END).toString();
      nameWidth = (int) U.measureText(tName, Paints.getBoldPaint15(nameFake));

      tPhone = TextUtils.ellipsize(phone, Paints.getTextPaint15(), lastMaxWidth, TextUtils.TruncateAt.END).toString();
      phoneWidth = (int) U.measureText(tPhone, Paints.getTextPaint15());
    }
  }

  private void buildAvatar () {
    if (user == null || TD.isPhotoEmpty(user.profilePhoto)) {
      avatar = null;
    } else {
      avatar = new ImageFile(tdlib, user.profilePhoto.small);
      avatar.setSize(avatarSize);
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    if (com.fahadali.xgram.appearance.XgramW2Cards.on() && xgramCardRect != null) {
      xgramDraw(c, startX, startY, receiver);
      return;
    }
    if (!useBubbles()) {
      c.drawRect(startX, startY, startX + Screen.dp(3f), startY + contactHeight, Paints.fillingPaint(Theme.chatVerticalLineColor()));
      startX += Screen.dp(10f);
    }
    startY += Screen.dp(1f);
    if (avatar == null) {
      c.drawCircle(startX + avatarRadius, startY + avatarRadius, avatarRadius, Paints.fillingPaint(accentColor.getPrimaryColor()));
      c.drawText(letters.text, startX + avatarRadius - (int) (lettersWidth / 2f), startY + lettersTop, Paints.getMediumTextPaint(LETTERS_SIZE, accentColor.getPrimaryContentColor(), letters.needFakeBold));
    } else {
      receiver.setBounds(startX, startY, startX + avatarSize, startY + avatarSize);
      if (receiver.needPlaceholder()) {
        c.drawCircle(startX + avatarRadius, startY + avatarRadius, avatarRadius, Paints.getPlaceholderPaint());
      }
      receiver.draw(c);
    }

    startX += avatarSize + Screen.dp(6f);

    c.drawText(tName, startX, startY + nameTop, Paints.getBoldPaint15(nameFake, getChatAuthorColor())); // Paints.getAuthorPaint15(TGDataCache.instance().getMyUserId() == userId, userId)
    c.drawText(tPhone, startX, startY + phoneTop, Paints.getRegularTextPaint(15f, getDecentColor()));
  }

  @Override
  protected int getContentWidth () {
    if (com.fahadali.xgram.appearance.XgramW2Cards.on() && xgramCardRect != null) {
      return xgramCardWidth; // XGram
    }
    return (useBubbles() ? 0 : Screen.dp(10f)) + avatarSize + Screen.dp(12f) + Math.max(nameWidth, phoneWidth);
  }

  @Override
  protected int getBottomLineContentWidth () {
    if (com.fahadali.xgram.appearance.XgramW2Cards.on()) {
      return BOTTOM_LINE_EXPAND_HEIGHT; // XGram: the time goes under the card
    }
    return Screen.dp(1f) + avatarSize + Screen.dp(6f) + phoneWidth;
  }

  @Override
  protected int getContentHeight () {
    if (com.fahadali.xgram.appearance.XgramW2Cards.on() && xgramCardRect != null) {
      return xgramCardHeight; // XGram
    }
    return contactHeight;
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    if (com.fahadali.xgram.appearance.XgramW2Cards.on()) {
      return Screen.dp(com.fahadali.xgram.appearance.XgramW2Cards.AVATAR) / 2; // XGram: the 40dp avatar
    }
    return avatarRadius;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(avatar);
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  private float currentTouchX, currentTouchY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) return true;
    if (com.fahadali.xgram.appearance.XgramW2Cards.on() && xgramCardRect != null) {
      return xgramOnTouchEvent(view, e); // XGram
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();

        int startX = getContentX() + Screen.dp(10f);
        if (x >= startX && x <= startX + avatarSize + Screen.dp(6f) + Math.max(nameWidth, phoneWidth) && y >= getContentY() + Screen.dp(1f) && y <= getContentY() + Screen.dp(1f) + avatarSize) {
          currentTouchX = x;
          currentTouchY = y;
          return true;
        }

        currentTouchX = 0f;
        currentTouchY = 0f;

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        currentTouchX = 0f;
        currentTouchY = 0f;

        break;
      }
      case MotionEvent.ACTION_UP: {
        if (currentTouchX != 0f && currentTouchY != 0f) {
          if (Math.abs(e.getX() - currentTouchX) < Screen.getTouchSlop() && Math.abs(e.getY()  - currentTouchY) < Screen.getTouchSlop()) {
            if (userId != 0) {
              tdlib.ui().openPrivateProfile(controller(), userId, openParameters());
            } else {
              UI.openNumber(phone);
            }
            return true;
          }
          currentTouchX = 0f;
          currentTouchY = 0f;
        }
        break;
      }
    }
    return false;
  }

  // Sizes

  private static int contactHeight, avatarRadius, avatarSize, textLeft, nameTop, phoneTop, lettersTop;

  private static void initSizes () {
    textLeft = Screen.dp(57f);
    contactHeight = Screen.dp(43f);
    avatarRadius = Screen.dp(20.5f);
    avatarSize = avatarRadius * 2;
    nameTop = Screen.dp(16f);
    phoneTop = Screen.dp(36f);
    lettersTop = Screen.dp(26f);
  }
}
