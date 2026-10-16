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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.core.lambda.Destroyable;

/**
 * Material 3 Expressive join request (the join requests sheet over a group and the requests page of an invite link),
 * a segmented list item: a 40dp avatar 16dp from the start, the name as the headline (bodyLarge, onSurface), "Requested
 * today at 7:52 pm" as the supporting text (bodyMedium, onSurfaceVariant) on up to two lines instead of one cut short,
 * and the two actions as 40dp icon buttons at the end, 8dp apart and 16dp from the end: decline a standard icon button
 * (onSurfaceVariant), add a filled tonal one. The row holding it is the tile ({@link #applyTileBackground}): the list
 * decoration gives it its segment corners, and a press anywhere else opens the request's options. Nothing is allocated
 * while drawing. Only used while {@link XgramExpressive#isEnabled()} is true.
 */
public final class XgramM3JoinRequestTile extends ViewGroup implements Destroyable {
  private static final float PADDING_START = 16f, AVATAR_SIZE = 40f, TEXT_START = PADDING_START + AVATAR_SIZE + 16f;
  private static final float PADDING_VERTICAL = 12f, MIN_HEIGHT = 72f;
  /** The buttons' 48dp touch targets: their 40dp containers keep 16dp from the end and 8dp between them. */
  private static final float BUTTON_TOUCH = XgramM3Sheets.TOUCH_TARGET;
  private static final float BUTTON_MARGIN = (BUTTON_TOUCH - XgramM3Sheets.ICON_BUTTON_SIZE) / 2f;
  private static final float BUTTON_END = 16f - BUTTON_MARGIN;
  /** The text ends 16dp before the decline button's container. */
  private static final float TEXT_END = BUTTON_END + BUTTON_TOUCH * 2f - BUTTON_MARGIN + 16f;
  private static final float EMOJI_STATUS_GAP = 6f;

  /** The tallest tile, a three-line list item (the headline and two lines of supporting text), for height estimates. */
  public static final float MAX_HEIGHT = 88f;

  private final EmojiTextView titleView, supportingView;
  private final ImageView declineButton, acceptButton;
  private final ComplexReceiver receiver;
  private final EmojiStatusHelper emojiStatusHelper;
  private float titleTextWidth;
  private int titleColor, supportingColor, declineColor, acceptColor;

  public XgramM3JoinRequestTile (Context context) {
    super(context);
    setWillNotDraw(false);
    receiver = new ComplexReceiver(this);
    emojiStatusHelper = new EmojiStatusHelper(null, this, null);

    titleView = newText(context, XgramExpressive.TS_BODY_L);
    titleView.setSingleLine(true);
    supportingView = newText(context, XgramExpressive.TS_BODY_M);
    supportingView.setMaxLines(2);

    declineButton = newButton(context, R.drawable.baseline_close_24);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      XgramAppMotion.iconButton(declineButton);
    }
    acceptButton = newButton(context, R.drawable.baseline_person_add_24);
    XgramM3Sheets.attachTonalIconButton(acceptButton);

    addView(titleView);
    addView(supportingView);
    addView(declineButton);
    addView(acceptButton);
    syncColors();
  }

  private static EmojiTextView newText (Context context, int textStyle) {
    EmojiTextView view = new EmojiTextView(context);
    view.setScrollDisabled(true);
    XgramExpressive.applyText(view, textStyle, false);
    view.setEllipsize(TextUtils.TruncateAt.END);
    view.setGravity(Lang.gravity());
    return view;
  }

  private static ImageView newButton (Context context, int iconRes) {
    ImageView view = new ImageView(context);
    view.setScaleType(ImageView.ScaleType.CENTER);
    view.setImageResource(XgramIconPack.map(iconRes));
    return view;
  }

  /**
   * Makes {@code row}, the list row holding the tile, the tile itself: a surfaceBright list row background (corners
   * by segment, 16dp while pressed) with an onSurface state layer in the same shape. The list decoration finds it and
   * sets its segment every frame, as for the rows of the settings lists.
   */
  public static void applyTileBackground (@NonNull View row) {
    XgramExpressive.MorphDrawable tile = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
    tile.setColorRole(XgramExpressive.SURFACE_BRIGHT);
    tile.setSegment(XgramExpressive.SEGMENT_SINGLE);
    tile.setHost(row);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      row.setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE, tile, tile.newMask()));
    } else {
      row.setBackground(tile);
    }
  }

  // Content

  public void setRequest (@NonNull Tdlib tdlib, @NonNull TdApi.MessageSender sender, @Nullable TdApi.User user, @Nullable CharSequence name, @Nullable CharSequence supporting) {
    receiver.getAvatarReceiver(0).requestMessageSender(tdlib, sender, AvatarReceiver.Options.SHOW_ONLINE);
    titleView.setText(name);
    supportingView.setText(supporting);
    titleTextWidth = U.measureText(titleView.getText(), titleView.getPaint());
    emojiStatusHelper.updateEmoji(tdlib, user, TextColorSets.Regular.NORMAL);
    requestLayout(); // the emoji status changes the headline's room
    invalidate();
  }

  public void setActions (@Nullable View.OnClickListener onDecline, @Nullable CharSequence declineLabel, @Nullable View.OnClickListener onAccept, @Nullable CharSequence acceptLabel) {
    declineButton.setOnClickListener(onDecline);
    declineButton.setContentDescription(declineLabel);
    acceptButton.setOnClickListener(onAccept);
    acceptButton.setContentDescription(acceptLabel);
  }

  public void checkRtl () {
    int gravity = Lang.gravity();
    if (titleView.getGravity() != gravity) {
      titleView.setGravity(gravity);
    }
    if (supportingView.getGravity() != gravity) {
      supportingView.setGravity(gravity);
    }
    requestLayout();
  }

  public void attach () {
    receiver.attach();
    emojiStatusHelper.attach();
  }

  public void detach () {
    receiver.detach();
    emojiStatusHelper.detach();
  }

  @Override
  public void performDestroy () {
    receiver.performDestroy();
    emojiStatusHelper.performDestroy();
    titleView.performDestroy();
    supportingView.performDestroy();
  }

  // Layout

  @Override
  public boolean shouldDelayChildPressedState () {
    return false; // as a FrameLayout: the buttons show their press at once
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int buttonSpec = MeasureSpec.makeMeasureSpec(Screen.dp(BUTTON_TOUCH), MeasureSpec.EXACTLY);
    declineButton.measure(buttonSpec, buttonSpec);
    acceptButton.measure(buttonSpec, buttonSpec);
    final int textWidth = Math.max(0, width - Screen.dp(TEXT_START) - Screen.dp(TEXT_END));
    final int titleWidth = Math.max(0, textWidth - emojiStatusHelper.getWidth(Screen.dp(EMOJI_STATUS_GAP)));
    final int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    titleView.measure(MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.EXACTLY), unspecified);
    supportingView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), unspecified);
    final int textHeight = titleView.getMeasuredHeight() + supportingView.getMeasuredHeight();
    final int height = Math.max(Screen.dp(MIN_HEIGHT), textHeight + Screen.dp(PADDING_VERTICAL) * 2);
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int width = r - l, height = b - t;
    final boolean rtl = Lang.rtl();

    final int textStart = Screen.dp(TEXT_START);
    final int textTop = (height - titleView.getMeasuredHeight() - supportingView.getMeasuredHeight()) / 2;
    layoutChild(titleView, rtl, width, textStart, textTop);
    layoutChild(supportingView, rtl, width, textStart, textTop + titleView.getMeasuredHeight());

    final int touch = Screen.dp(BUTTON_TOUCH);
    final int buttonTop = (height - touch) / 2;
    final int acceptStart = width - Screen.dp(BUTTON_END) - touch;
    layoutChild(acceptButton, rtl, width, acceptStart, buttonTop);
    layoutChild(declineButton, rtl, width, acceptStart - touch, buttonTop);

    final int size = Screen.dp(AVATAR_SIZE);
    final int avatarStart = Screen.dp(PADDING_START);
    final int avatarLeft = rtl ? width - avatarStart - size : avatarStart;
    final int avatarTop = (height - size) / 2;
    receiver.getAvatarReceiver(0).setBounds(avatarLeft, avatarTop, avatarLeft + size, avatarTop + size);
  }

  /** Lays {@code child} out at {@code start} (from the start edge, mirrored in RTL) and {@code top}. */
  private static void layoutChild (View child, boolean rtl, int width, int start, int top) {
    final int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
    final int left = rtl ? width - start - w : start;
    child.layout(left, top, left + w, top + h);
  }

  // Drawing

  private void syncColors () {
    final int title = XgramExpressive.color(XgramExpressive.ON_SURFACE);
    if (title != titleColor) {
      titleColor = title;
      titleView.setTextColor(title);
    }
    final int supporting = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
    if (supporting != supportingColor) {
      supportingColor = supporting;
      supportingView.setTextColor(supporting);
    }
    if (supporting != declineColor) {
      declineColor = supporting;
      declineButton.setColorFilter(supporting);
    }
    final int accept = XgramM3Sheets.tonalContentColor();
    if (accept != acceptColor) {
      acceptColor = accept;
      acceptButton.setColorFilter(accept);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    syncColors(); // theme switches (the row registers this view for theme invalidation)
    AvatarReceiver avatar = receiver.getAvatarReceiver(0);
    if (avatar.needPlaceholder()) {
      avatar.drawPlaceholder(c);
    }
    avatar.draw(c);
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    super.dispatchDraw(c);
    if (emojiStatusHelper.needDrawEmojiStatus()) {
      final int gap = Screen.dp(EMOJI_STATUS_GAP);
      final int textWidth = (int) Math.min(titleTextWidth, titleView.getMeasuredWidth());
      final int x = Lang.rtl() ? titleView.getRight() - textWidth - gap - emojiStatusHelper.getWidth() : titleView.getLeft() + textWidth + gap;
      emojiStatusHelper.draw(c, x, titleView.getTop());
    }
  }
}
