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
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.SparseDrawableView;

import com.fahadali.xgram.appearance.XgramExpressive;

import me.vkryl.core.collection.IntList;

/**
 * One row of the forum topic list: topic icon, name, time, "Sender: last message" preview and badges. It has the
 * chat list's rows' geometry in the chat list view's mode (ChatView.Metrics: One line, Two lines, Three lines, Three
 * lines with bigger text): the topic icon in the photo's place and size, the title row, the lines under it and the
 * badges where a chat's are, springing with the chat list's rows when the view changes (XgramChatListMode).
 */
public final class ForumTopicView extends SparseDrawableView {
  /** The height of a topic row now (it springs while the chat list view changes). */
  public static int getViewHeight () {
    return com.fahadali.xgram.chats.XgramChatListMode.rowHeightPx();
  }

  // The topic icon in the photo's place: Telegram X's design draws it bare at 0.6 of the photo (30dp beside the
  // 52dp photos of two lines); Expressive design in a surfaceContainerHighest circle of the photo's size, the icon
  // at 0.6 and a custom emoji at 0.65 of it (24dp and 26dp in a 40dp container, as the list item spec)
  private static final float ICON_RATIO = .6f;
  private static final float XGRAM_ICON_RATIO = .6f, XGRAM_EMOJI_RATIO = .65f;

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

  /** The row's geometry now: the chat list view's spring while it changes, the topic's mode otherwise. */
  private ChatView.Metrics metrics () {
    return com.fahadali.xgram.chats.XgramChatListMode.metrics(topic != null ? topic.getListMode() : Settings.instance().getChatListMode());
  }

  private static float clamp01 (float value) {
    return value < 0f ? 0f : value > 1f ? 1f : value;
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
        topic.checkListMode(); // a row made before the chat list view changed lays itself out for the new one
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
    if (text == null && topic != null) {
      text = topic.getFadeText(); // the old lines fade out with their custom emoji while the new mode has no text
    }
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
    // The chat list row's height, springing while the view changes (the list asks for a layout each frame)
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.round(metrics().height), MeasureSpec.EXACTLY));
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

    // The row's geometry (ChatView.Metrics). While the chat list view changes (XgramChatListMode) it springs from the
    // old mode to the new one, as the chat list's rows: the icon, the title row and the badges move, and the lines
    // under the title that only one of the two modes has fade through (the old mode's out over the first half, the
    // new mode's in over the second).
    final int mode = topic.getListMode();
    final ChatView.Metrics m = metrics();
    final boolean oneLineMode = mode == Settings.CHAT_MODE_1LINE;
    final float oneLine = clamp01(m.oneLine);
    final boolean modeTransition = com.fahadali.xgram.chats.XgramChatListMode.isAnimating() && mode == com.fahadali.xgram.chats.XgramChatListMode.targetMode();
    final float modeFraction = modeTransition ? clamp01(com.fahadali.xgram.chats.XgramChatListMode.fraction()) : 1f;
    final float linesInAlpha = modeTransition ? clamp01(modeFraction * 2f - 1f) : 1f;
    final float linesOutAlpha = modeTransition && topic.getFadeMode() != 0 ? clamp01(1f - modeFraction * 2f) : 0f;
    // The title row and the lines under it move together: the title's shift from where the topic's own mode puts it
    final int textLeft = ChatView.getLeftPadding(mode);
    final int titleDx = Math.round(m.textLeft) - textLeft;
    final int titleTop = Math.round(m.titleTop);
    final float timeBaseline = m.timeBaseline;
    final int clipSaveCount;
    if (modeTransition) {
      clipSaveCount = org.thunderdog.challegram.tool.Views.save(c);
      c.clipRect(0, 0, viewWidth, getMeasuredHeight());
    } else {
      clipSaveCount = -1;
    }

    // Icon, in the photo's place and at its scale
    Drawable icon = emojiIcon != null ? emojiIcon : topic.getIconDrawable();
    final float leadingSize = m.avatar;
    final float cx = rtl ? viewWidth - m.avatarLeft - leadingSize / 2f : m.avatarLeft + leadingSize / 2f;
    final float cy = m.avatarTop + leadingSize / 2f;
    final int iconSize;
    if (xgramM3) {
      // In a surfaceContainerHighest circle, like a list item's leading avatar (on the page's surface while the
      // row is a lifted card of that color)
      xgramLeadingPaint.setColor(me.vkryl.core.ColorUtils.fromToArgb(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), XgramExpressive.color(XgramExpressive.SURFACE), me.vkryl.core.MathUtils.clamp(xgramDragValue)));
      c.drawCircle(cx, cy, leadingSize / 2f, xgramLeadingPaint);
      iconSize = Math.round(leadingSize * (emojiIcon != null ? XGRAM_EMOJI_RATIO : XGRAM_ICON_RATIO));
    } else {
      iconSize = Math.round(leadingSize * ICON_RATIO);
    }
    final int iconLeft = Math.round(cx - iconSize / 2f), iconTop = Math.round(cy - iconSize / 2f);
    icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
    icon.draw(c);

    // Who is typing in the topic
    final org.thunderdog.challegram.telegram.TdlibStatusManager.Helper status = topic.statusHelper();
    final org.thunderdog.challegram.telegram.TdlibStatusManager.ChatState state = status.drawingState();
    final float statusVisibility = state != null ? state.visibility() : 0f;
    // In one line the typing icon takes the time's place (there is no line under the title for it): a fade through
    // with Expressive design, Telegram X's crossfade otherwise (as ChatView)
    float timeAlpha = 1f, oneLineStatusAlpha = 0f;
    if (oneLineMode && state != null && statusVisibility > 0f) {
      final float visibility = clamp01(statusVisibility);
      if (xgramM3) {
        timeAlpha = visibility >= .5f ? 0f : 1f - visibility * 2f;
        oneLineStatusAlpha = visibility <= .5f ? 0f : visibility * 2f - 1f;
      } else {
        timeAlpha = 1f - visibility;
        oneLineStatusAlpha = visibility;
      }
    }

    // Time
    final float xgramTrailing = xgramM3 ? 1f - xgramHandleFactor() : 1f; // XGram M3E: the reorder handle takes their place
    final float timeDrawAlpha = xgramTrailing * timeAlpha;
    String time = topic.getTime();
    if (!time.isEmpty() && timeDrawAlpha > 0f) {
      if (xgramM3) {
        boolean unread = topic.hasUnread();
        Paint paint = xgramTimePaint(unread);
        float timeWidth = unread ? paint.measureText(time) : topic.getTimeWidth();
        float timeX = rtl ? viewWidth - topic.getTimeLeft() - topic.getTimeWidth() : topic.getTimeLeft() + topic.getTimeWidth() - timeWidth;
        if (timeDrawAlpha < 1f) {
          paint.setColor(XgramExpressive.withAlpha(paint.getColor(), timeDrawAlpha));
        }
        c.drawText(time, timeX, timeBaseline, paint);
      } else {
        // The shared paint: its alpha is put back after
        Paint paint = getTimePaint();
        final int paintAlpha = paint.getAlpha();
        if (timeDrawAlpha < 1f) {
          paint.setAlpha(Math.round(paintAlpha * timeDrawAlpha));
        }
        c.drawText(time, rtl ? viewWidth - topic.getTimeLeft() - topic.getTimeWidth() : topic.getTimeLeft(), timeBaseline, paint);
        if (timeDrawAlpha < 1f) {
          paint.setAlpha(paintAlpha);
        }
      }
    }
    if (oneLineStatusAlpha > 0f && xgramTrailing > 0f) {
      final TdApi.ChatAction action = state.action();
      if (action != null) {
        // Its end edge on the time's: the title never reflows, one line keeps 24dp for the time (TGForumTopic.timeReserve)
        final float startX = topic.getTimeLeft() + topic.getTimeWidth() - org.thunderdog.challegram.tool.DrawAlgorithms.getStatusWidth(action);
        final float alpha = oneLineStatusAlpha * xgramTrailing;
        final int color = xgramM3 ? XgramExpressive.color(XgramExpressive.PRIMARY) : org.thunderdog.challegram.theme.Theme.getColor(ColorId.textLight);
        org.thunderdog.challegram.tool.DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - startX : startX, timeBaseline - Screen.dp(4f), me.vkryl.core.ColorUtils.alphaColor(alpha, color), this, !xgramM3 && alpha == 1f ? ColorId.textLight : ColorId.NONE);
      }
    }

    // Title, mute and lock. In RTL Telegram X draws the title from the start without mirroring it (as ChatView); one
    // line has the ticks and badges on the title's row, so there the whole row is mirrored
    final boolean mirrorTitle = rtl && oneLineMode;
    Text title = topic.getTitle();
    if (title != null) {
      final int titleX = textLeft + titleDx;
      title.draw(c, mirrorTitle ? viewWidth - titleX - title.getWidth() : titleX, titleTop);
    }
    if (topic.isMuted()) {
      Drawables.drawRtl(c, Icons.getChatMuteDrawable(ColorId.chatListMute), topic.getMuteLeft() + titleDx, Math.round(m.muteTop), Paints.getChatsMutePaint(), viewWidth, rtl);
    }
    int checksRight = topic.getChecksRight();
    if (topic.isClosed()) {
      Drawable lock = getSparseDrawable(R.drawable.baseline_lock_16, ColorId.iconLight);
      Drawables.drawRtl(c, lock, topic.getLockLeft(), Math.round(timeBaseline) - Screen.dp(12f), PorterDuffPaint.get(ColorId.iconLight), viewWidth, rtl);
      checksRight -= Screen.dp(20f);
    }

    // Sending state and read ticks. trailingX: where the cluster at the end (time, lock, ticks) starts, one line's
    // badges end 8dp before it
    final int clockTop = Math.round(m.clockTop);
    int trailingX = checksRight + Screen.dp(7f);
    if (topic.isSending()) {
      int x = checksRight - Screen.dp(10f) - Screen.dp(Icons.CLOCK_SHIFT_X);
      Drawables.drawRtl(c, Icons.getClockIcon(ColorId.iconLight), x, clockTop - Screen.dp(Icons.CLOCK_SHIFT_Y), Paints.getIconLightPorterDuffPaint(), viewWidth, rtl);
      trailingX = checksRight - Screen.dp(20f);
    } else if (topic.isOutgoing()) {
      boolean unread = topic.isUnread();
      int right = checksRight + (unread ? Screen.dp(4f) : 0);
      int x = right - Screen.dp(Icons.TICKS_SHIFT_X) - Screen.dp(14f);
      Drawables.drawRtl(c, unread ? Icons.getSingleTick(ColorId.ticks) : Icons.getDoubleTick(ColorId.ticks), x, clockTop - Screen.dp(Icons.TICKS_SHIFT_Y), unread ? Paints.getTicksPaint() : Paints.getTicksReadPaint(), viewWidth, rtl);
      trailingX = right - Screen.dp(24 - 8 + 3);
    }

    // Counters, on the second line at the end or in one line before the ticks, sliding between the two while the view
    // changes (as ChatView); a pin for pinned topics without unread messages. Right to left: unread, mention, reaction.
    final float counterRadius = ChatView.getCounterRadius();
    final float counterCenterY = m.counterCenterY;
    final float multiLineRight = viewWidth - getRightPaddingPx();
    final float oneLineRight = trailingX - Screen.dp(8f);
    final int counterGap = Screen.dp(7f);
    Counter counter = topic.getCounter();
    Counter mentionCounter = topic.getMentionCounter();
    Counter reactionsCounter = topic.getReactionsCounter();
    // XGram M3E: the heart takes the badge's own (tertiary) content color
    final int reactionsColorId = xgramM3 ? ColorId.NONE : topic.isMuted() ? ColorId.badgeMutedText : ColorId.badgeText;
    if (rtl && oneLine >= .5f) {
      // Mirrored in one line: from the start edge of the slot towards the title
      float counterLeft = viewWidth - oneLineRight;
      counter.draw(c, counterLeft + counterRadius, counterCenterY, Gravity.LEFT, xgramTrailing);
      counterLeft += counter.getScaledWidth(counterGap);
      mentionCounter.draw(c, counterLeft + counterRadius, counterCenterY, Gravity.LEFT, xgramTrailing, this, ColorId.badgeText);
      counterLeft += mentionCounter.getScaledWidth(counterGap);
      reactionsCounter.draw(c, counterLeft + counterRadius, counterCenterY, Gravity.LEFT, xgramTrailing, this, reactionsColorId);
    } else {
      float counterRight = rtl || oneLine <= 0f ? multiLineRight : multiLineRight + (oneLineRight - multiLineRight) * oneLine;
      counter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, xgramTrailing);
      counterRight -= counter.getScaledWidth(counterGap);
      mentionCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, xgramTrailing, this, ColorId.badgeText);
      counterRight -= mentionCounter.getScaledWidth(counterGap);
      reactionsCounter.draw(c, counterRight - counterRadius, counterCenterY, Gravity.RIGHT, xgramTrailing, this, reactionsColorId);
    }
    final float badgesWidth = counter.getScaledWidth(counterGap) + mentionCounter.getScaledWidth(counterGap) + reactionsCounter.getScaledWidth(counterGap);
    if (topic.isPinned() && badgesWidth == 0f && xgramTrailing > 0f) {
      // XGram M3E: the pin through the icon pack
      Drawable pin = getSparseDrawable(xgramM3 ? com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.deproko_baseline_pin_16) : R.drawable.deproko_baseline_pin_16, ColorId.iconLight);
      final float pinRight = multiLineRight + (oneLineRight - multiLineRight) * oneLine;
      int pinLeft = Math.round(pinRight) - pin.getMinimumWidth();
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
      Drawables.drawRtl(c, handle, handleLeft, (int) (getMeasuredHeight() / 2f - handle.getMinimumHeight() / 2f), xgramHandlePaint, viewWidth, rtl);
      xgramHandlePaint.setAlpha(255);
    }

    // The lines under the title: "Sender: text" (two lines) or the sender over the text (three lines), none in one
    // line; sliding away while someone is typing in the topic (as in chats)
    final float textAlpha = 1f - statusVisibility;
    if (linesOutAlpha > 0f) {
      // The old mode's lines, at the old mode's places shifted with the title row; custom emoji only while the new
      // mode has no text of its own
      final int fadeMode = topic.getFadeMode();
      final ChatView.Metrics fadeMetrics = ChatView.metricsOf(fadeMode);
      drawLines(c, fadeMode, topic.getFadePrefix(), topic.getFadeText(), topic.getFadeTextLeft(), topic.getFadeIcons(), linesOutAlpha, true,
        topic.getText() == null ? textMediaReceiver : null, Math.round(m.textLeft - fadeMetrics.textLeft), Math.round(m.titleTop - fadeMetrics.titleTop), viewWidth, rtl);
    }
    final ChatView.Metrics ownMetrics = modeTransition ? ChatView.metricsOf(mode) : m;
    final int linesDy = modeTransition ? Math.round(m.titleTop - ownMetrics.titleTop) : 0;
    final float linesAlpha = textAlpha * linesInAlpha;
    if (linesAlpha > 0f && !oneLineMode) {
      final int dy = (int) (Screen.dp(14f) * statusVisibility);
      // Mid-change the icons go through a layer, as the old lines may draw the same drawable at another alpha
      drawLines(c, mode, topic.getPrefix(), topic.getText(), topic.getTextLeft(), topic.getTextIconIds(), linesAlpha, modeTransition,
        textMediaReceiver, titleDx, linesDy + dy, viewWidth, rtl);
    }
    final float statusLinesAlpha = statusVisibility * linesInAlpha;
    if (statusLinesAlpha > 0f && !oneLineMode) {
      Text statusText = status.drawingText();
      if (statusText != null) {
        float top = ChatView.getTextTop(mode) + linesDy - Screen.dp(14f) * textAlpha;
        int x = textLeft + titleDx;
        org.thunderdog.challegram.tool.DrawAlgorithms.drawStatus(c, state, rtl ? viewWidth - x : x, top + statusText.getLineHeight() / 2f, me.vkryl.core.ColorUtils.alphaColor(statusLinesAlpha, statusText.getTextColor()), this, statusLinesAlpha == 1f ? ColorId.textLight : ColorId.NONE);
        statusText.draw(c, x, (int) top, null, statusLinesAlpha);
      }
    }

    if (clipSaveCount != -1) {
      org.thunderdog.challegram.tool.Views.restore(c, clipSaveCount);
    }
  }

  /**
   * The lines under the title of {@code mode}, as ChatView's: the prefix (sender, "Draft") with the text after it (two
   * lines) or under it (three lines), and the text's icons, at {@code alpha}, shifted by {@code dx} (in the row's own
   * start-based x, as every x here) and {@code dy}. {@code iconLayers}: each icon at full alpha in a layer of
   * {@code alpha}, for frames where another pass may draw the same drawable at another alpha.
   */
  private void drawLines (Canvas c, int mode, @Nullable Text prefix, @Nullable Text text, int textLeft, @Nullable IntList icons, float alpha, boolean iconLayers, @Nullable ComplexReceiver receiver, int dx, int dy, int viewWidth, boolean rtl) {
    if (alpha <= 0f || (prefix == null && text == null)) {
      return;
    }
    int textTop = ChatView.getTextTop(mode) + dy;
    if (prefix != null) {
      prefix.draw(c, ChatView.getLeftPadding(mode) + dx, textTop, null, alpha);
    }
    if (text == null) {
      return;
    }
    if (mode != Settings.CHAT_MODE_2LINE) {
      if (prefix != null) {
        textTop += prefix.getNextLineHeight();
      } else if (text.getLineCount() == 1) {
        textTop += Screen.dp(2f); // ChatView.getSingleLineOffset
      }
    }
    if (icons != null) {
      int x = textLeft + dx;
      for (int i = 0; i < icons.size(); i++) {
        Drawable d = getSparseDrawable(icons.get(i), ColorId.NONE);
        int y = textTop + text.getLineHeight(false) / 2 - d.getMinimumHeight() / 2;
        if (iconLayers) {
          final float iconCx = (rtl ? viewWidth - x - d.getMinimumWidth() : x) + d.getMinimumWidth() / 2f;
          com.fahadali.xgram.appearance.XgramIconCrossfade.drawSingle(c, d, PorterDuffPaint.get(ColorId.chatListIcon), iconCx, y + d.getMinimumHeight() / 2f, alpha);
        } else {
          Drawables.drawRtl(c, d, x, y, PorterDuffPaint.get(ColorId.chatListIcon, alpha), viewWidth, rtl);
        }
        x += Screen.dp(18f);
      }
    }
    text.draw(c, textLeft + dx, textTop, null, alpha, receiver);
  }

  @Nullable
  public TdApi.ForumTopic getForumTopic () {
    return topic != null ? topic.getTopic() : null;
  }
}
