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
package com.fahadali.xgram.topics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.emoji.AnimatedEmojiDrawable;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.SparseDrawableView;

import com.fahadali.xgram.appearance.XgramExpressive;

import me.vkryl.core.collection.IntList;

/**
 * One row of the forum topic list: topic icon, name, time, "Sender: last message" preview and badges.
 */
public final class ForumTopicView extends SparseDrawableView {
  public static int getViewHeight () {
    return Screen.dp(64f);
  }

  private static int getIconSize () {
    return Screen.dp(30f);
  }

  private static int getIconLeft () {
    return Screen.dp(14f);
  }

  private static int getIconTop () {
    return Screen.dp(9f);
  }

  public static int getTextLeft () {
    return Screen.dp(XgramExpressive.isEnabled() ? XGRAM_TEXT_LEFT : 58f);
  }

  // XGram M3E: a 40dp leading container 16dp from the start, the text 16dp after it (list item spec)
  private static final float XGRAM_LEADING_LEFT = 16f, XGRAM_LEADING_SIZE = 40f, XGRAM_TEXT_LEFT = 72f;
  private static final float XGRAM_ICON_SIZE = 24f, XGRAM_EMOJI_SIZE = 26f;

  // XGram M3E: labelMedium time, primary and weight 700 while the topic has unread messages (as ChatView)
  private static Paint xgramTimePaint, xgramTimePaintUnread;

  private static Paint xgramTimePaint (boolean unread) {
    Paint paint = unread ? xgramTimePaintUnread : xgramTimePaint;
    if (paint == null) {
      paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_M, unread));
      if (unread) {
        xgramTimePaintUnread = paint;
      } else {
        xgramTimePaint = paint;
      }
    }
    paint.setTextSize(Screen.dp(12f));
    paint.setColor(XgramExpressive.color(unread ? XgramExpressive.PRIMARY : XgramExpressive.ON_SURFACE_VARIANT));
    return paint;
  }

  private static final Paint xgramLeadingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public static int getRightPaddingPx () {
    return Screen.dp(15f);
  }

  public static Paint getTimePaint () {
    return ChatView.getTimePaint();
  }

  private static int getTitleTop () {
    return Screen.dp(9f);
  }

  private static int getTimeBaseline () {
    return Screen.dp(25f);
  }

  private static int getIconRowTop () {
    return Screen.dp(9f);
  }

  private static int getTextTop () {
    return Screen.dp(35f);
  }

  private static int getCounterCenterY () {
    return Screen.dp(45f);
  }

  private final Tdlib tdlib;
  private final ComplexReceiver textMediaReceiver;
  private TGForumTopic topic;

  private AnimatedEmojiDrawable emojiIcon;
  private long emojiIconId;
  private boolean isAttached;

  public ForumTopicView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.textMediaReceiver = new ComplexReceiver(this);
    // Opaque, so rows don't show through each other while the list animates a re-sort.
    if (XgramExpressive.isEnabled()) {
      setBackground(xgramRowBackground());
    } else {
      RippleSupport.setSimpleWhiteBackground(this);
    }
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getViewHeight()));
  }

  // XGram M3E: an opaque surface row whose press state layer is a rounded tile (inset 8dp / 2dp, 16dp corners), the
  // same shape as the selection tile, instead of Telegram X's full-bleed rectangle.

  private @Nullable android.graphics.drawable.RippleDrawable xgramRipple;
  private int xgramRippleColor;

  private Drawable xgramRowBackground () {
    // The row's own outline: the lifted card's while it is dragged (no shadow at rest, there is no elevation then)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline (android.view.View view, android.graphics.Outline outline) {
          final float lift = me.vkryl.core.MathUtils.clamp(xgramDragValue);
          final int inset = Math.round(Screen.dp(XGRAM_CARD_INSET) * lift);
          outline.setRoundRect(inset, 0, Math.max(inset, view.getWidth() - inset), view.getHeight(), Screen.dp(XgramExpressive.CORNER_L) * lift);
        }
      });
    }
    Drawable surface = new Drawable() {
      private final Paint paint = new Paint();

      @Override
      public void draw (@androidx.annotation.NonNull Canvas c) {
        // While the row is dragged its card is drawn instead (ForumTopicView.onDraw): the square surface fades out
        paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SURFACE), 1f - me.vkryl.core.MathUtils.clamp(xgramDragValue)));
        c.drawRect(getBounds(), paint);
      }

      @Override
      public void setAlpha (int alpha) { }

      @Override
      public void setColorFilter (@Nullable android.graphics.ColorFilter colorFilter) { }

      @Override
      public int getOpacity () {
        return android.graphics.PixelFormat.OPAQUE;
      }
    };
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
      return surface;
    }
    android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
    mask.setColor(0xff000000);
    mask.setCornerRadius(Screen.dp(XgramExpressive.CORNER_L));
    int insetX = Screen.dp(8f), insetY = Screen.dp(2f);
    xgramRippleColor = xgramRippleColor();
    xgramRipple = new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(xgramRippleColor), null,
      new android.graphics.drawable.InsetDrawable(mask, insetX, insetY, insetX, insetY));
    return new android.graphics.drawable.LayerDrawable(new Drawable[] {surface, xgramRipple});
  }

  private static int xgramRippleColor () {
    return XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.STATE_PRESSED);
  }

  /**
   * A pinned topic being dragged. With Expressive design it lifts as a card: surfaceContainerHighest, 8dp in from the
   * sides, corners to 16dp, elevation level 3 and a 1.02 scale, all on one SPATIAL_FAST spring (a snap with Reduce
   * motion); Telegram X's drag otherwise.
   */
  private boolean xgramDragging;
  private float xgramDragValue;
  private @Nullable XgramExpressive.Spring xgramDrag;
  private static final float XGRAM_CARD_INSET = 8f, XGRAM_DRAG_SCALE = .02f;
  private static final Paint xgramCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.RectF xgramCardRect = new android.graphics.RectF();

  public void xgramSetDragging (boolean dragging) {
    if (this.xgramDragging != dragging) {
      this.xgramDragging = dragging;
      if (XgramExpressive.isEnabled()) {
        if (xgramDrag == null) {
          xgramDrag = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> xgramApplyDrag(value));
        }
        xgramDrag.animateTo(dragging ? 1f : 0f);
      }
      invalidate();
    }
  }

  private void xgramApplyDrag (float value) {
    xgramDragValue = value;
    final float scale = 1f + XGRAM_DRAG_SCALE * value;
    setScaleX(scale);
    setScaleY(scale);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      setTranslationZ(Screen.dp(XgramExpressive.ELEVATION_3) * Math.max(0f, value));
      invalidateOutline();
    }
    invalidate();
  }

  /** A recycled row starts flat: no lift, no handle animation left over from the topic it showed. */
  private void xgramResetRow () {
    xgramDragging = false;
    if (xgramDrag != null) {
      xgramDrag.setValue(0f);
    }
    if (xgramDragValue != 0f) {
      xgramApplyDrag(0f);
    }
  }

  // XGram M3E: the trailing drag handle of a pinned topic in reorder mode (it takes the place of the time and the
  // counters, crossfading on EFFECTS_FAST)

  private boolean xgramHandle;
  private @Nullable XgramExpressive.Spring xgramHandleSpring;
  private static final Paint xgramHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private static int xgramHandleColor;
  private static final Paint xgramPinPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private static int xgramPinColor;

  public void xgramSetReorderHandle (boolean show, boolean animated) {
    if (!XgramExpressive.isEnabled()) {
      return;
    }
    final float target = show ? 1f : 0f;
    if (xgramHandleSpring == null) {
      if (!show) {
        xgramHandle = false;
        return;
      }
      xgramHandleSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> invalidate());
    }
    if (xgramHandle == show && (animated ? xgramHandleSpring.getTarget() == target : xgramHandleSpring.getValue() == target)) {
      return;
    }
    this.xgramHandle = show;
    if (animated) {
      xgramHandleSpring.animateTo(show ? 1f : 0f);
    } else {
      xgramHandleSpring.setValue(show ? 1f : 0f);
      invalidate();
    }
  }

  private float xgramHandleFactor () {
    return xgramHandleSpring != null ? me.vkryl.core.MathUtils.clamp(xgramHandleSpring.getValue()) : 0f;
  }

  public ComplexReceiver getTextMediaReceiver () {
    return textMediaReceiver;
  }

  @Nullable
  public TGForumTopic getTopic () {
    return topic;
  }

  public void setTopic (@Nullable TGForumTopic topic) {
    if (this.topic != topic) {
      if (this.topic != null) {
        this.topic.detachFromView(this);
      }
      if (topic == null) {
        xgramResetRow(); // XGram M3E
      }
      this.topic = topic;
      if (topic != null) {
        topic.checkLayout(getMeasuredWidth());
        topic.attachToView(this);
      }
    }
    requestContent();
    invalidate();
  }

  /** Called when the bound topic's data changed. */
  public void onTopicChanged () {
    requestContent();
    invalidate();
  }

  private void requestContent () {
    Text text = topic != null ? topic.getText() : null;
    if (text != null) {
      text.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
    requestEmojiIcon(topic != null ? topic.getIconCustomEmojiId() : 0);
  }

  private void requestEmojiIcon (long customEmojiId) {
    if (emojiIconId == customEmojiId) {
      return;
    }
    emojiIconId = customEmojiId;
    if (emojiIcon != null) {
      emojiIcon.detach();
      emojiIcon.performDestroy();
      emojiIcon = null;
    }
    if (customEmojiId == 0) {
      return;
    }
    tdlib.emoji().findOrRequest(customEmojiId, (entry, inPlace) -> {
      Runnable act = () -> {
        if (emojiIconId != customEmojiId || entry.value == null) {
          return;
        }
        AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(this);
        drawable.setSticker(new TGStickerObj(tdlib, entry.value, null, entry.value.fullType), false);
        if (isAttached) {
          drawable.attach();
        }
        emojiIcon = drawable;
        invalidate();
      };
      if (inPlace) {
        act.run();
      } else {
        tdlib.ui().post(act);
      }
    });
  }

  public void attach () {
    isAttached = true;
    textMediaReceiver.attach();
    if (emojiIcon != null) {
      emojiIcon.attach();
    }
  }

  public void detach () {
    isAttached = false;
    textMediaReceiver.detach();
    if (emojiIcon != null) {
      emojiIcon.detach();
    }
  }

  public void performDestroy () {
    setTopic(null);
    textMediaReceiver.performDestroy();
    if (emojiIcon != null) {
      emojiIcon.performDestroy();
      emojiIcon = null;
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getViewHeight(), MeasureSpec.EXACTLY));
    if (topic != null && topic.checkLayout(getMeasuredWidth())) {
      requestContent();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (topic == null) {
      return;
    }
    final boolean rtl = Lang.rtl();
    final int viewWidth = getMeasuredWidth();
    final boolean xgramM3 = XgramExpressive.isEnabled();

    if (xgramM3) {
      if (xgramRipple != null && xgramRippleColor != xgramRippleColor()) {
        xgramRippleColor = xgramRippleColor();
        xgramRipple.setColor(android.content.res.ColorStateList.valueOf(xgramRippleColor));
      }
      final float lift = me.vkryl.core.MathUtils.clamp(xgramDragValue);
      if (lift > 0f) {
        // The lifted card (its square surface fades out in the background drawable meanwhile)
        final float inset = Screen.dp(XGRAM_CARD_INSET) * lift, radius = Screen.dp(XgramExpressive.CORNER_L) * lift;
        xgramCardRect.set(inset, 0, viewWidth - inset, getMeasuredHeight());
        xgramCardPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SURFACE), XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), lift));
        c.drawRoundRect(xgramCardRect, radius, radius, xgramCardPaint);
      }
    }

    // Icon
    Drawable icon = emojiIcon != null ? emojiIcon : topic.getIconDrawable();
    if (xgramM3) {
      // In a 40dp surfaceContainerHighest circle, like a list item's leading avatar (on the page's surface while the
      // row is a lifted card of that color)
      float leadingSize = Screen.dp(XGRAM_LEADING_SIZE);
      float leadingLeft = Screen.dp(XGRAM_LEADING_LEFT);
      float cx = rtl ? viewWidth - leadingLeft - leadingSize / 2f : leadingLeft + leadingSize / 2f;
      float cy = getViewHeight() / 2f;
      xgramLeadingPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), XgramExpressive.color(XgramExpressive.SURFACE), me.vkryl.core.MathUtils.clamp(xgramDragValue)));
      c.drawCircle(cx, cy, leadingSize / 2f, xgramLeadingPaint);
      int size = Screen.dp(emojiIcon != null ? XGRAM_EMOJI_SIZE : XGRAM_ICON_SIZE);
      int left = Math.round(cx - size / 2f), top = Math.round(cy - size / 2f);
      icon.setBounds(left, top, left + size, top + size);
    } else {
      int iconSize = getIconSize();
      int iconLeft = rtl ? viewWidth - getIconLeft() - iconSize : getIconLeft();
      icon.setBounds(iconLeft, getIconTop(), iconLeft + iconSize, getIconTop() + iconSize);
    }
    icon.draw(c);

    // Time
    final float xgramTrailing = xgramM3 ? 1f - xgramHandleFactor() : 1f; // XGram M3E: the reorder handle takes their place
    String time = topic.getTime();
    if (!time.isEmpty()) {
      if (xgramM3) {
        boolean unread = topic.hasUnread();
        Paint paint = xgramTimePaint(unread);
        float timeWidth = unread ? paint.measureText(time) : topic.getTimeWidth();
        float timeX = rtl ? viewWidth - topic.getTimeLeft() - topic.getTimeWidth() : topic.getTimeLeft() + topic.getTimeWidth() - timeWidth;
        if (xgramTrailing < 1f) {
          paint.setColor(XgramExpressive.withAlpha(paint.getColor(), xgramTrailing));
        }
        if (xgramTrailing > 0f) {
          c.drawText(time, timeX, getTimeBaseline(), paint);
        }
      } else {
        c.drawText(time, rtl ? viewWidth - topic.getTimeLeft() - topic.getTimeWidth() : topic.getTimeLeft(), getTimeBaseline(), getTimePaint());
      }
    }

    // Title, mute and lock
    Text title = topic.getTitle();
    if (title != null) {
      title.draw(c, getTextLeft(), getTitleTop());
    }
    if (topic.isMuted()) {
      Drawables.drawRtl(c, Icons.getChatMuteDrawable(ColorId.chatListMute), topic.getMuteLeft(), getIconRowTop() + Screen.dp(2f), Paints.getChatsMutePaint(), viewWidth, rtl);
    }
    int checksRight = topic.getChecksRight();
    if (topic.isClosed()) {
      Drawable lock = getSparseDrawable(R.drawable.baseline_lock_16, ColorId.iconLight);
      Drawables.drawRtl(c, lock, topic.getLockLeft(), getTimeBaseline() - Screen.dp(12f), PorterDuffPaint.get(ColorId.iconLight), viewWidth, rtl);
      checksRight -= Screen.dp(20f);
    }

    // Sending state and read ticks
    if (topic.isSending()) {
      int x = checksRight - Screen.dp(10f) - Screen.dp(Icons.CLOCK_SHIFT_X);
      Drawables.drawRtl(c, Icons.getClockIcon(ColorId.iconLight), x, getTimeBaseline() - Screen.dp(11f) - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint(), viewWidth, rtl);
    } else if (topic.isOutgoing()) {
      boolean unread = topic.isUnread();
      int x = checksRight + (unread ? Screen.dp(4f) : 0) - Screen.dp(Icons.TICKS_SHIFT_X) - Screen.dp(14f);
      Drawables.drawRtl(c, unread ? Icons.getSingleTick(ColorId.ticks) : Icons.getDoubleTick(ColorId.ticks), x, getTimeBaseline() - Screen.dp(11f) - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint(), viewWidth, rtl);
    }

    // Counters, or a pin for pinned topics without unread messages
    float counterRight = viewWidth - getRightPaddingPx();
    float counterRadius = ChatView.getCounterRadius();
    float counterCenterY = getCounterCenterY();
    Counter counter = topic.getCounter();
    counter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, xgramTrailing);
    counterRight -= counter.getScaledWidth(Screen.dp(7f));
    Counter mentionCounter = topic.getMentionCounter();
    mentionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, xgramTrailing, this, ColorId.badgeText);
    counterRight -= mentionCounter.getScaledWidth(Screen.dp(7f));
    Counter reactionsCounter = topic.getReactionsCounter();
    // XGram M3E: the heart takes the badge's own (tertiary) content color
    reactionsCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, xgramTrailing, this, xgramM3 ? ColorId.NONE : topic.isMuted() ? ColorId.badgeMutedText : ColorId.badgeText);
    counterRight -= reactionsCounter.getScaledWidth(Screen.dp(7f));
    if (topic.isPinned() && counterRight == viewWidth - getRightPaddingPx() && xgramTrailing > 0f) {
      // XGram M3E: the pin through the icon pack
      Drawable pin = getSparseDrawable(xgramM3 ? com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.deproko_baseline_pin_16) : R.drawable.deproko_baseline_pin_16, ColorId.iconLight);
      int pinLeft = viewWidth - getRightPaddingPx() - pin.getMinimumWidth();
      Paint pinPaint = PorterDuffPaint.get(ColorId.iconLight);
      if (xgramTrailing < 1f) {
        // XGram M3E: fading out for the reorder handle (a paint of its own: the shared one keeps its alpha)
        final int pinColor = org.thunderdog.challegram.theme.Theme.getColor(ColorId.iconLight);
        if (xgramPinColor != pinColor || xgramPinPaint.getColorFilter() == null) {
          xgramPinColor = pinColor;
          xgramPinPaint.setColorFilter(Paints.getColorFilter(pinColor));
        }
        xgramPinPaint.setAlpha(Math.round(255 * xgramTrailing));
        pinPaint = xgramPinPaint;
      }
      Drawables.drawRtl(c, pin, pinLeft, (int) (counterCenterY - pin.getMinimumHeight() / 2f), pinPaint, viewWidth, rtl);
    }
    if (xgramM3 && xgramTrailing < 1f) {
      // XGram M3E: the drag handle (reorder mode), onSurfaceVariant, vertically centred at the trailing edge
      final int handleColor = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      if (xgramHandleColor != handleColor || xgramHandlePaint.getColorFilter() == null) {
        xgramHandleColor = handleColor;
        xgramHandlePaint.setColorFilter(Paints.getColorFilter(handleColor));
      }
      xgramHandlePaint.setAlpha(Math.round(255 * (1f - xgramTrailing)));
      Drawable handle = getSparseDrawable(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_drag_handle_24), ColorId.NONE);
      int handleLeft = viewWidth - Screen.dp(16f) - handle.getMinimumWidth();
      Drawables.drawRtl(c, handle, handleLeft, (int) (getViewHeight() / 2f - handle.getMinimumHeight() / 2f), xgramHandlePaint, viewWidth, rtl);
      xgramHandlePaint.setAlpha(255);
    }

    // "Sender: text", sliding away while someone is typing in the topic (as in chats)
    org.thunderdog.challegram.telegram.TdlibStatusManager.Helper status = topic.statusHelper();
    org.thunderdog.challegram.telegram.TdlibStatusManager.ChatState state = status.drawingState();
    float statusVisibility = state != null ? state.visibility() : 0f;
    float textAlpha = 1f - statusVisibility;
    int textTop = getTextTop();
    if (textAlpha > 0f) {
      int dy = (int) (Screen.dp(14f) * statusVisibility);
      int saveCount = dy != 0 ? org.thunderdog.challegram.tool.Views.save(c) : -1;
      if (dy != 0) {
        c.translate(0, dy);
      }
      Text prefix = topic.getPrefix();
      if (prefix != null) {
        prefix.draw(c, getTextLeft(), textTop, null, textAlpha);
      }
      Text text = topic.getText();
      if (text != null) {
        IntList iconIds = topic.getTextIconIds();
        if (iconIds != null) {
          int x = topic.getTextLeft();
          for (int i = 0; i < iconIds.size(); i++) {
            Drawable d = getSparseDrawable(iconIds.get(i), ColorId.NONE);
            int y = textTop + text.getLineHeight(false) / 2 - d.getMinimumHeight() / 2;
            Drawables.drawRtl(c, d, x, y, PorterDuffPaint.get(ColorId.chatListIcon, textAlpha), viewWidth, rtl);
            x += Screen.dp(18f);
          }
        }
        text.draw(c, topic.getTextLeft(), textTop, null, textAlpha, textMediaReceiver);
      }
      if (saveCount != -1) {
        org.thunderdog.challegram.tool.Views.restore(c, saveCount);
      }
    }
    if (statusVisibility > 0f) {
      Text statusText = status.drawingText();
      if (statusText != null) {
        float top = textTop - Screen.dp(14f) * textAlpha;
        int x = getTextLeft();
        org.thunderdog.challegram.tool.DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - x : x, top + statusText.getLineHeight() / 2f, me.vkryl.core.ColorUtils.alphaColor(statusVisibility, statusText.getTextColor()), this, statusVisibility == 1f ? ColorId.textLight : ColorId.NONE);
        statusText.draw(c, x, (int) top, null, statusVisibility);
      }
    }
  }

  @Nullable
  public TdApi.ForumTopic getForumTopic () {
    return topic != null ? topic.getTopic() : null;
  }
}
