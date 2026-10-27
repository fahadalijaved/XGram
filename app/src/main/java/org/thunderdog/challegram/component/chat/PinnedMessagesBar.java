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
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.helper.LinkPreview;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ListManager;
import org.thunderdog.challegram.telegram.MessageListManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.MessageId;

public class PinnedMessagesBar extends ViewGroup implements Destroyable, MessageListManager.ChangeListener, View.OnClickListener {
  private CustomRecyclerView recyclerView;
  private SettingsAdapter messagesAdapter;
  private TdlibMessageViewer.Viewport messageViewport;
  private CollapseButton collapseButton;
  private TopBarView showAllButton;

  private boolean animationsDisabled;

  private class CollapseButton extends View {
    public CollapseButton (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      DrawAlgorithms.drawCollapse(c, getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2f, getMeasuredHeight() / 2f, Theme.iconColor(), isExpanded.getFloatValue(), 1f - canExpand.getFloatValue());
    }
  }

  private int lastKnownViewHeight;

  private final RecyclerView.ItemAnimator itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private final boolean reverseLayout;

  public PinnedMessagesBar (@NonNull Context context, boolean reverseLayout) {
    super(context);

    this.reverseLayout = reverseLayout;

    showAllButton = new TopBarView(context);
    showAllButton.setAlpha(0f);
    showAllButton.setCanDismiss(true);
    showAllButton.setDismissListener(barView -> {
      if (messageListener != null) {
        messageListener.onDismissRequest(this);
      }
    });
    showAllButton.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, Screen.dp(36f))); // XGram: before the items, which size their buttons to it
    showAllButton.setItems(new TopBarView.Item(R.id.btn_showPinnedMessage, R.string.ShowPinnedList, v -> {
      if (messageListener != null) {
        messageListener.onShowAllRequest(this);
      }
    }));
    addView(showAllButton);

    recyclerView = (CustomRecyclerView) Views.inflate(context, R.layout.recycler_custom, null);
    recyclerView.setHasFixedSize(true); // XGram: the bar measures it exactly (onMeasure), so item changes don't lay out the whole window
    recyclerView.setItemAnimator(itemAnimator);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS : RecyclerView.OVER_SCROLL_NEVER);
    recyclerView.setVerticalScrollBarEnabled(false);
    Views.setScrollBarPosition(recyclerView);
    recyclerView.setLayoutManager(new XgramPinnedLayoutManager(context, reverseLayout));
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void onDrawOver (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (messagesAdapter.getItems().isEmpty()) {
          setOverScrollDisabled(true);
          return;
        }

        int viewportHeight = getRecyclerHeight();

        int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
        int scrollItemCount = messageList != null ? messageList.getTotalCount() : messagesAdapter.getItems().size();
        if (scrollItemCount <= 0) {
          setOverScrollDisabled(true);
          return;
        }

        final float alpha = 1f;

        //

        final int defaultFillColor = ColorUtils.alphaColor(alpha, Theme.chatVerticalLineColor());
        final int defaultBackgroundColor = ColorUtils.alphaColor(.3f * alpha, defaultFillColor);

        //

        final float visibleItemCount = Math.min(scrollItemCount, (float) viewportHeight / (float) itemHeight);

        //

        float focusPosition = getFocusPosition();
        if (!reverseLayout) {
          focusPosition = scrollItemCount - 1 - focusPosition;
        }

        //

        final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
        // XGram: keep the line at least 20dp from the card's start, clear of its 20dp outer corner
        final int lineX = xgramM3 ? Math.max(Screen.dp(12f), Screen.dp(20f) - parent.getLeft()) : Screen.dp(10f);
        final float strokeWidth = Screen.dp(xgramM3 ? 2f : 1.5f); // XGram: a 4dp primary pill line
        final int outerSpacing = Screen.dp(xgramM3 ? 8f : 6f); // spacing before the first and the last item
        final int minSpacingBetweenItems = Screen.dp(3f);
        final float lineStartX = lineX - strokeWidth;
        final float lineEndX = lineX + strokeWidth;

        final int maxLineHeight = itemHeight - outerSpacing * 2;
        final int minLineHeight = (maxLineHeight - minSpacingBetweenItems * 3) / 4;

        final int lineHeight = Math.min(maxLineHeight, Math.max(minLineHeight, ((viewportHeight - outerSpacing * 2 - minSpacingBetweenItems * (scrollItemCount - 1))) / scrollItemCount));
        final int spacingBetweenItems = scrollItemCount <= 1 ? 0 : MathUtils.fromTo(minSpacingBetweenItems, outerSpacing * 2, (visibleItemCount - 1) / (float) (scrollItemCount - 1));

        final int visibleLineCount = Math.min(scrollItemCount, viewportHeight / lineHeight + 1);

        final float scrollFactor = scrollItemCount == visibleItemCount ? 1f : focusPosition / (scrollItemCount - visibleItemCount);

        final int fromPosition = Math.max(0, (int) (focusPosition - Math.ceil(visibleLineCount * scrollFactor)));
        final int toPosition = Math.min(scrollItemCount, (int) Math.ceil(focusPosition) + visibleLineCount + 1);

        setOverScrollDisabled(fromPosition == 0 && toPosition == scrollItemCount);

        RectF rectF = Paints.getRectF();

        float lineScrollY = Math.max(0, lineHeight * scrollItemCount + spacingBetweenItems * (scrollItemCount - 1) + outerSpacing * 2 - viewportHeight);

        float lineBeginY = viewportHeight - outerSpacing + lineScrollY * scrollFactor;

        for (int position = fromPosition; position < toPosition; position++) {
          float lineEndY = lineBeginY - (float) Math.ceil((lineHeight + spacingBetweenItems) * position);
          float lineStartY = lineEndY - lineHeight;

          int dataPosition;
          if (reverseLayout) {
            dataPosition = position;
          } else {
            dataPosition = scrollItemCount - 1 - position;
          }
          Entry entry = null;
          if (messagesAdapter != null && dataPosition >= 0 && dataPosition < messagesAdapter.getItemCount()) {
            entry = (Entry) messagesAdapter.getItems().get(dataPosition).getData();
          }
          final int fillColor, backgroundColor;
          if (xgramM3) {
            fillColor = ColorUtils.alphaColor(alpha, xgramSegmentColor(entry, dataPosition));
            backgroundColor = ColorUtils.alphaColor(.3f * alpha, fillColor);
          } else if (entry != null && entry.accentColor != null) {
            fillColor = ColorUtils.alphaColor(alpha, entry.accentColor.getVerticalLineColor());
            backgroundColor = ColorUtils.alphaColor(.3f * alpha, fillColor);
          } else {
            fillColor = defaultFillColor;
            backgroundColor = defaultBackgroundColor;
          }

          boolean isFull = focusPosition == position || (position > (int) focusPosition && position < (int) (focusPosition + visibleItemCount));

          rectF.set(lineStartX, lineStartY, lineEndX, lineEndY);
          drawSegment(c, rectF, strokeWidth, isFull ? fillColor : backgroundColor, xgramM3, outerSpacing, viewportHeight - outerSpacing);

          if (!isFull) {
            if (position == (int) focusPosition && focusPosition > position) {
              rectF.set(lineStartX, lineStartY, lineEndX, lineEndY + (lineStartY - lineEndY) * (focusPosition - (float) position));
              drawSegment(c, rectF, strokeWidth, fillColor, xgramM3, outerSpacing, viewportHeight - outerSpacing);
            } else {
              float remain = focusPosition + visibleItemCount - position;
              if (remain > 0f && remain < 1f) {
                rectF.set(lineStartX, lineEndY + (lineStartY - lineEndY) * remain, lineEndX, lineEndY);
                drawSegment(c, rectF, strokeWidth, fillColor, xgramM3, outerSpacing, viewportHeight - outerSpacing);
              }
            }
          }
        }

        if (!xgramM3) {
          c.drawRect(0, parent.getMeasuredHeight() - Screen.separatorSize(), parent.getMeasuredWidth(), parent.getMeasuredHeight(), Paints.fillingPaint(ColorUtils.alphaColor(getExpandFactor() * MathUtils.clamp(focusPosition), Theme.separatorColor())));
        }
      }

      // XGram: in Material 3 Expressive, segments that scroll past the card's inner padding shrink into short pills instead of running into the rounded card edge
      private void drawSegment (Canvas c, RectF rect, float radius, int color, boolean clamp, float clipTop, float clipBottom) {
        if (clamp) {
          rect.top = Math.max(rect.top, clipTop);
          rect.bottom = Math.min(rect.bottom, clipBottom);
          if (rect.bottom - rect.top <= 0f) {
            return;
          }
          radius = Math.min(radius, (rect.bottom - rect.top) / 2f);
        }
        c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(color));
      }
    });
    addView(recyclerView);

    collapseButton = new CollapseButton(context);
    collapseButton.setOnClickListener(v -> {
      if (canExpand.getValue() || isExpanded.getValue()) {
        isExpanded.toggleValue(true);
      } else {
        if (messageListener != null) {
          messageListener.onDismissRequest(this);
        }
      }
    });
    collapseButton.setBackgroundResource(R.drawable.bg_btn_header);
    Views.setClickable(collapseButton);
    addView(collapseButton);

    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) { // XGram: Material 3 Expressive draws the card (or the composer block) behind
      ViewSupport.setThemedBackground(this, ColorId.filling, null);
      ViewSupport.setThemedBackground(recyclerView, ColorId.filling, null);
    } else {
      com.fahadali.xgram.appearance.XgramAppMotion.iconButton(collapseButton); // XGram: the 40dp state layer, following theme switches
    }
    setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    setWillNotDraw(false);
  }

  private boolean ignoreAlbums;

  public void setIgnoreAlbums (boolean ignoreAlbums) {
    this.ignoreAlbums = ignoreAlbums;
  }

  public void setCollapseButtonVisible (boolean isVisible) {
    collapseButton.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    updateContentInsets();
  }

  public void setAnimationsDisabled (boolean animationsDisabled) {
    if (this.animationsDisabled != animationsDisabled) {
      this.animationsDisabled = animationsDisabled;
      recyclerView.setItemAnimator(animationsDisabled ? null : itemAnimator);
    }
  }

  public boolean areAnimationsDisabled () {
    return animationsDisabled;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return; // XGram: the Material 3 Expressive card is the background
    }
    c.drawRect(0, getRecyclerHeight(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
  }

  /**
   * XGram M3E: the segment takes the colour MessagePreviewView gives the author's name, which is primary for every
   * sender under Expressive, so the line and the name always match, in the pinned bar and in the composer's reply
   * preview.
   */
  private int xgramSegmentColor (@Nullable Entry entry, int dataPosition) {
    return com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY);
  }

  private int getContentInset () {
    return collapseButton.getVisibility() == View.VISIBLE ? Screen.dp(28f) : 0;
  }

  private float getFocusPosition () {
    int viewportHeight = recyclerView.getMeasuredHeight();
    int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
    int childCount = recyclerView.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View view = recyclerView.getChildAt(i);
      if (view instanceof MessagePreviewView) {
        int adapterPosition = recyclerView.getChildAdapterPosition(view);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          int scrollY;
          if (reverseLayout) {
            scrollY = view.getBottom() - viewportHeight;
          } else {
            scrollY = -view.getTop();
          }
          return adapterPosition + (float) scrollY / (float) itemHeight;
        }
      }
    }
    return 0f;
  }

  private void updateContentInsets () {
    float focusPosition = getFocusPosition();
    final float collapse = 1f - getExpandFactor();
    final int contentInset = getContentInset();
    for (int i = 0; i < recyclerView.getChildCount(); i++) {
      View view = recyclerView.getChildAt(i);
      if (view instanceof MessagePreviewView) {
        int adapterPosition = recyclerView.getChildAdapterPosition(view);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          updateContentInset((MessagePreviewView) view, adapterPosition, focusPosition, contentInset, collapse);
        }
      }
    }
  }

  private boolean overScrollDisabled;

  private void setOverScrollDisabled (boolean isDisabled) {
    if (this.overScrollDisabled != isDisabled) {
      this.overScrollDisabled = isDisabled;
      recyclerView.setOverScrollMode(isDisabled ? RecyclerView.OVER_SCROLL_NEVER : RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    }
  }

  @CallSuper
  protected void onViewportChanged () {
    updateContentInsets();
  }

  private final FactorAnimator.Target animationTarget = new FactorAnimator.Target() {
    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      checkHeight();
      recyclerView.invalidate();
      collapseButton.invalidate();
      showAllButton.setAlpha(getExpandFactor());
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == 1 && finalFactor == 0f) {
        isExpanded.setValue(false, false);
      }
    }
  };
  private final BoolAnimator canExpand = new BoolAnimator(1, animationTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private final BoolAnimator isExpanded = new BoolAnimator(0, animationTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private void checkHeight () {
    int newHeight = getTotalHeight();
    if (newHeight != lastKnownViewHeight) {
      lastKnownViewHeight = newHeight;
      // XGram: the bar takes its new height in place (xgramResizeInPlace) instead of a requestLayout() on every frame of
      // the expand and count animations, which measured and laid out the whole window each frame
      final boolean inPlace = xgramCanResizeInPlace(newHeight);
      if (!inPlace) {
        requestLayout();
      }
      onViewportChanged();
      if (inPlace) {
        xgramResizeInPlace();
      }

      float expand = getExpandFactor();
      if (expand == 1f || expand == 0f) {
        updateContentInsets();
      }
    }
  }

  /**
   * XGram: the bar can size itself without a layout pass: it is laid out and shown in the chat's bar stack
   * (CollapseListView, which places its bars by translation and is as tall as the chat), and the new height fits in it.
   */
  private boolean xgramCanResizeInPlace (int newHeight) {
    if (getVisibility() != View.VISIBLE || !(getParent() instanceof org.thunderdog.challegram.widget.CollapseListView) ||
      isLayoutRequested() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2 && isInLayout()) ||
      !androidx.core.view.ViewCompat.isLaidOut(this) || getWidth() <= 0) {
      return false;
    }
    final View parent = (View) getParent();
    return getTop() + newHeight <= parent.getHeight();
  }

  /** XGram: what the next layout pass would do for the bar alone: measure it (and its children) and lay it out where it is. */
  private void xgramResizeInPlace () {
    final int left = getLeft(), top = getTop(), right = getRight();
    forceLayout(); // measures of past sizes are cached; the flag goes with the layout() below and reaches no parent
    measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(lastKnownViewHeight, MeasureSpec.EXACTLY));
    layout(left, top, right, top + getMeasuredHeight());
  }

  private float getExpandFactor () {
    return isExpanded.getFloatValue() * canExpand.getFloatValue();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int newHeight = getTotalHeight();
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY));
    // XGram: the "Show pinned list" row spans the whole bar (its label is centred on it), the list stays inside the padding
    final int fullWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
    int horizontalPadding = getPaddingLeft() + getPaddingRight();
    if (horizontalPadding != 0) {
      int width = Math.max(0, getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec) - horizontalPadding);
      widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
    }
    recyclerView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getRecyclerHeight(), MeasureSpec.EXACTLY));
    showAllButton.measure(fullWidthMeasureSpec, MeasureSpec.makeMeasureSpec(Screen.dp(36f), MeasureSpec.EXACTLY));

    collapseButton.measure(MeasureSpec.makeMeasureSpec(Screen.dp(40f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MathUtils.fromTo(SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW), Screen.dp(36f), getExpandFactor()), MeasureSpec.EXACTLY));
    if (newHeight != lastKnownViewHeight) {
      lastKnownViewHeight = newHeight;
      onViewportChanged();
    }
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    // XGram: children are laid out in this view's own coordinates. l..r is where the bar sits in its parent: Telegram X
    // used it as is, which only worked while the bar sat at its parent's left edge (8dp in as a Material 3 Expressive
    // card, or 55dp in the RTL composer's reply block, it shifted the list by that much and cut its end off).
    final int width = r - l, height = b - t;
    final int left = getPaddingLeft(), right = width - getPaddingRight();
    showAllButton.layout(0, height - Screen.dp(36f), width, height);
    recyclerView.layout(left, 0, right, getRecyclerHeight());
    collapseButton.layout(right - collapseButton.getMeasuredWidth(), height - collapseButton.getMeasuredHeight(), right, height);
  }

  private void updateContentInset (MessagePreviewView view, int position) {
    updateContentInset(view, position, getFocusPosition(), getContentInset(), 1f - getExpandFactor());
  }

  private void updateContentInset (MessagePreviewView view, int position, float focusPosition, int contentInset, float collapse) {
    if (contentInset == 0) {
      view.setContentInset(0);
      return;
    }
    int inset = collapse == 1f ?
      contentInset :
      collapse == 0f ? 0 :
        Math.round((float) contentInset * (1f - MathUtils.clamp(Math.abs((float) position - focusPosition))) * collapse);
    view.setContentInset(inset);
  }

  /**
   * XGram: the pinned rows' layout. RecyclerView stopped the app with "Inconsistency detected" (a row at a position past
   * the adapter's end) whenever the bar showed, on the owner's Fold (2026-10-27), with every change of the rows matched
   * by its notification. No predictive animations (their pre-layout is where it threw), and should a layout still find
   * the rows out of step, they are laid out again from the adapter in the next frame instead of crashing (logged).
   */
  private final class XgramPinnedLayoutManager extends LinearLayoutManager {
    XgramPinnedLayoutManager (Context context, boolean reverseLayout) {
      super(context, LinearLayoutManager.VERTICAL, reverseLayout);
    }

    @Override
    public boolean supportsPredictiveItemAnimations () {
      return false;
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state) {
      try {
        super.onLayoutChildren(recycler, state);
      } catch (IndexOutOfBoundsException e) {
        android.util.Log.e("XGram", "Pinned bar: rows out of step with the adapter, laying them out again", e);
        recyclerView.post(() -> {
          if (messagesAdapter != null) {
            messagesAdapter.notifyDataSetChanged();
          }
        });
      }
    }
  }

  public void initialize (@NonNull ViewController<?> viewController) {
    messageViewport = viewController.tdlib().messageViewer().createViewport(new TdApi.MessageSourceSearch(), viewController);
    messagesAdapter = new SettingsAdapter(viewController, this, viewController) {
      @Nullable
      @Override
      protected View createModifiedView (ViewGroup parent, int viewType, View view) {
        if (viewType == ListItem.TYPE_MESSAGE_PREVIEW && messageListener != null) {
          messageListener.onCreateMessagePreview(PinnedMessagesBar.this, (MessagePreviewView) view);
        }
        return null;
      }

      @Override
      protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
        Entry data = (Entry) item.getData();
        if (data.isLinkPreview()) {
          LinkPreview linkPreview = data.linkPreviewContext.getLinkPreview(data.linkPreviewUrl);
          previewView.setLinkPreview(linkPreview, (v, currentLinkPreview) -> {
            if (messageListener != null && messageListener.onToggleLargeMedia(PinnedMessagesBar.this, v, data.linkPreviewContext, currentLinkPreview)) {
              v.updateShowSmallMedia(true);
            }
          });
        } else if (data.isMessage()) {
          TdApi.Message message = data.message;
          TdApi.InputTextQuote quote = data.quote;
          previewView.setMessage(message, quote, new TdApi.SearchMessagesFilterPinned(), item.getStringValue(), ignoreAlbums ? MessagePreviewView.Options.IGNORE_ALBUM_REFRESHERS : MessagePreviewView.Options.NONE, data.localPickedFile);
          if (messageList == null) {
            // override message preview
            MessageId highlightMessageId;
            //noinspection ConstantConditions
            if (data.tdlib.isChannelAutoForward(message) && message.forwardInfo.source != null && message.forwardInfo.source.chatId == contextChatId) {
              highlightMessageId = new MessageId(message.forwardInfo.source);
            } else {
              highlightMessageId = new MessageId(message.chatId, message.id);
            }
            previewView.setPreviewChatId(null, highlightMessageId.getChatId(), null, highlightMessageId, null);
          }
          if (messageListener != null) {
            messageListener.onMessageDisplayed(PinnedMessagesBar.this, previewView, message);
          }
        } else {
          throw new UnsupportedOperationException();
        }
        updateContentInset(previewView, position);
      }

      @Override
      public void onViewAttachedToWindow (SettingHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder.itemView instanceof MessagePreviewView) {
          int position = holder.getBindingAdapterPosition();
          updateContentInset((MessagePreviewView) holder.itemView, position);
        }
      }
    };
    recyclerView.setAdapter(messagesAdapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
          int firstVisiblePosition = manager.findFirstVisibleItemPosition();
          if (firstVisiblePosition != RecyclerView.NO_POSITION) {
            View view = manager.findViewByPosition(firstVisiblePosition);
            if (view != null) {
              if (reverseLayout) {
                int offsetY = Math.max(0, view.getBottom() - recyclerView.getMeasuredHeight());
                if (offsetY != 0) {
                  if (offsetY > view.getMeasuredHeight() / 2) {
                    recyclerView.smoothScrollBy(0, offsetY - view.getMeasuredHeight());
                  } else {
                    recyclerView.smoothScrollBy(0, offsetY);
                  }
                }
              } else {
                int offsetY = Math.min(0, view.getTop());
                if (offsetY != 0) {
                  if (-offsetY > view.getMeasuredHeight() / 2) {
                    recyclerView.smoothScrollBy(0, view.getMeasuredHeight() + offsetY);
                  } else {
                    recyclerView.smoothScrollBy(0, offsetY);
                  }
                }
              }
            }
          }
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (manager != null) {
          int lastVisiblePosition = manager.findLastVisibleItemPosition();
          int firstVisiblePosition = manager.findFirstVisibleItemPosition();
          if (messageList != null && dy != 0) {
            if (lastVisiblePosition != RecyclerView.NO_POSITION && lastVisiblePosition + 15 >= messageList.getCount()) {
              messageList.loadItems(false, null);
            } else if (firstVisiblePosition != RecyclerView.NO_POSITION && firstVisiblePosition - 5 <= 0) {
              messageList.loadItems(true, null);
            }
          }

          int focusIndex = firstVisiblePosition;
          View view = manager.findViewByPosition(firstVisiblePosition);
          if (view != null) {
            if (reverseLayout) {
              int offsetY = Math.max(0, view.getBottom() - recyclerView.getMeasuredHeight());
              if (offsetY > view.getMeasuredHeight() / 2) {
                focusIndex++;
              }
            } else {
              int offsetY = Math.min(0, view.getTop());
              if (-offsetY > view.getMeasuredHeight() / 2) {
                focusIndex++;
              }
            }
          }
          setFocusIndex(focusIndex);
        }
        float expand = getExpandFactor();
        if (expand > 0f && expand < 1f) {
          updateContentInsets();
        }
      }
    });
    viewController.tdlib().ui().attachViewportToRecyclerView(messageViewport, recyclerView);
    viewController.addThemeInvalidateListener(this);
    viewController.addThemeInvalidateListener(recyclerView);
    viewController.addThemeInvalidateListener(collapseButton);
    showAllButton.addThemeListeners(viewController);
  }

  private int focusIndex = RecyclerView.NO_POSITION;

  private void setFocusIndex (int focusIndex) {
    if (this.focusIndex != focusIndex) {
      this.focusIndex = focusIndex;
      ListItem item = messagesAdapter.getItem(focusIndex);
      if (item != null) {
        Entry entry = (Entry) item.getData();
        if (entry.isLinkPreview()) {
          messageListener.onSelectLinkPreviewUrl(this, entry.linkPreviewContext, entry.linkPreviewUrl);
        }
      }
    }
  }

  public interface MessageListener {
    void onMessageClick (PinnedMessagesBar view, TdApi.Message message, @Nullable TdApi.InputTextQuote quote);
    default void onSelectLinkPreviewUrl (PinnedMessagesBar view, MessagesController.MessageInputContext messageContext, String url) { }
    default boolean onToggleLargeMedia (PinnedMessagesBar view, MessagePreviewView previewView, MessagesController.MessageInputContext messageContext, LinkPreview linkPreview) {
      return false;
    }
    default void onDismissRequest (PinnedMessagesBar view) { }
    default void onShowAllRequest (PinnedMessagesBar view) { }
    default void onCreateMessagePreview (PinnedMessagesBar view, MessagePreviewView previewView) { }
    default void onMessageDisplayed (PinnedMessagesBar view, MessagePreviewView previewView, TdApi.Message message) { }
  }

  private MessageListener messageListener;

  public void setMessageListener (MessageListener messageListener) {
    this.messageListener = messageListener;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.message) {
      ListItem item = ((ListItem) v.getTag());
      Entry entry = (Entry) item.getData();
      if (messageListener != null) {
        if (entry.isLinkPreview()) {
          messageListener.onSelectLinkPreviewUrl(this, entry.linkPreviewContext, entry.linkPreviewUrl);
        } else if (entry.isMessage()) {
          messageListener.onMessageClick(this, entry.message, entry.quote);
        } else {
          // TODO
        }
      }
    }
  }

  private final FactorAnimator countAnimator = new FactorAnimator(2, animationTarget, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  @Override
  public void onTotalCountChanged (ListManager<TdApi.Message> list, int totalCount) {
    if (xgramHoldCount(totalCount)) {
      return; // XGram
    }
    xgramApplyTotalCount(totalCount);
  }

  private void xgramApplyTotalCount (int totalCount) {
    if (totalCount != 0 || isExpanded.getFloatValue() == 0f) {
      canExpand.setValue(totalCount > 1, !animationsDisabled); // XGram: no animation while the chat isn't focused (the chevron redrew mid-slide)
    }
    float toCount = Math.max(0f, Math.min(3f, totalCount - 1));
    if (getExpandFactor() > 0f && !animationsDisabled) { // XGram: same
      countAnimator.animateTo(toCount);
    } else {
      countAnimator.forceFactor(toCount);
    }
  }

  // XGram: held while its chat isn't focused (it slides in, or another screen covers it). The bar keeps what it shows:
  // changes of its list wait, and it catches up with the list when released (MessagesController.onFocusStateChanged),
  // so nothing in the chat's layer is drawn again mid-slide. Bars that nobody holds (the composer's) work as before.
  private boolean xgramHeld, xgramHeldItemsChanged, xgramHeldCountChanged;
  private int xgramHeldCount;

  public void xgramSetHeld (boolean held) {
    if (this.xgramHeld != held) {
      this.xgramHeld = held;
      if (!held) {
        xgramCatchUp();
      }
    }
  }

  private boolean xgramHoldItems () {
    if (xgramHeld) {
      xgramHeldItemsChanged = true;
      return true;
    }
    return false;
  }

  private boolean xgramHoldCount (int totalCount) {
    if (xgramHeld) {
      xgramHeldCountChanged = true;
      xgramHeldCount = totalCount;
      return true;
    }
    return false;
  }

  /** The bar was just set from its list: nothing waits. */
  private void xgramResetHeld () {
    xgramHeldItemsChanged = false;
    xgramHeldCountChanged = false;
  }

  private void xgramCatchUp () {
    final boolean itemsChanged = xgramHeldItemsChanged, countChanged = xgramHeldCountChanged;
    xgramResetHeld();
    if (messageList == null) {
      return;
    }
    if (itemsChanged) {
      xgramSyncItems();
    }
    if (countChanged) {
      xgramApplyTotalCount(xgramHeldCount);
    }
  }

  /** The rows become the list's messages again as changes (a row whose message object is still there stays as it is). */
  private void xgramSyncItems () {
    final List<ListItem> shown = messagesAdapter.getItems();
    if (messageList.getCount() == 0) {
      needsClear = !shown.isEmpty(); // as onItemRemoved does with the last row: it stays until the next one comes
      return;
    }
    final List<ListItem> oldItems = new ArrayList<>(shown);
    final List<ListItem> newItems = new ArrayList<>(messageList.getCount());
    for (TdApi.Message message : messageList) {
      ListItem kept = null;
      if (!needsClear) {
        for (ListItem item : oldItems) {
          Entry entry = (Entry) item.getData();
          if (entry != null && entry.message == message && entry.quote == null) {
            kept = item;
            break;
          }
        }
      }
      newItems.add(kept != null ? kept : itemOf(messageList.tdlib(), message));
    }
    DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      @Override
      public int getOldListSize () {
        return oldItems.size();
      }

      @Override
      public int getNewListSize () {
        return newItems.size();
      }

      @Override
      public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
        Entry a = (Entry) oldItems.get(oldItemPosition).getData();
        Entry b = (Entry) newItems.get(newItemPosition).getData();
        return a != null && b != null && a.message != null && b.message != null &&
          a.message.chatId == b.message.chatId && a.message.id == b.message.id;
      }

      @Override
      public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
        return oldItems.get(oldItemPosition) == newItems.get(newItemPosition);
      }
    }, false);
    needsClear = false;
    shown.clear();
    shown.addAll(newItems);
    diff.dispatchUpdatesTo(messagesAdapter);
  }

  public int getTotalHeight () {
    return getRecyclerHeight() + getBottomBarHeight();
  }

  private int getBottomBarHeight () {
    return (int) (Screen.dp(36f) * getExpandFactor());
  }

  private int getRecyclerHeight () {
    int itemHeight = SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW);
    return itemHeight + Math.round((float) itemHeight * countAnimator.getFactor() * getExpandFactor());
  }

  public static class Entry implements Destroyable {
    public final Tdlib tdlib;

    public final TdApi.Message message;
    public final @Nullable TdApi.InputTextQuote quote;
    public final TdlibAccentColor accentColor;

    public final MessagesController.MessageInputContext linkPreviewContext;
    public final String linkPreviewUrl;
    public @Nullable MediaToReplacePickerManager.LocalPickedFile localPickedFile;

    public Entry (Tdlib tdlib, TdApi.Message message, @Nullable TdApi.InputTextQuote quote) {
      this.tdlib = tdlib;
      this.message = message;
      this.linkPreviewContext = null;
      this.linkPreviewUrl = null;
      this.quote = quote;
      this.accentColor = tdlib.messageAccentColor(message);
    }

    public Entry (Tdlib tdlib, @NonNull MessagesController.MessageInputContext linkPreviewContext, @NonNull String linkPreviewUrl) {
      this.tdlib = tdlib;
      this.linkPreviewContext = linkPreviewContext;
      this.linkPreviewUrl = linkPreviewUrl;
      this.message = null;
      this.accentColor = null;
      this.quote = null;
    }

    public Entry setForceLocalPicledFile (@Nullable MediaToReplacePickerManager.LocalPickedFile localPickedFile) {
      this.localPickedFile = localPickedFile;
      return this;
    }

    public boolean isLinkPreview () {
      return linkPreviewUrl != null;
    }

    public boolean isMessage () {
      return message != null;
    }

    public void subscribeToUpdates () {
      // TODO subscribe to updates
    }

    @Override
    public void performDestroy () {
      // TODO
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Entry)) {
        return false;
      }
      Entry b = (Entry) obj;
      if (isMessage() != b.isMessage() || isLinkPreview() != b.isLinkPreview()) {
        return false;
      }
      if (isLinkPreview()) {
        return StringUtils.equalsTo(this.linkPreviewUrl, b.linkPreviewUrl) && (this.linkPreviewContext == b.linkPreviewContext);
      } else if (isMessage()) {
        // XGram: a new quote of the same message is a different entry, else the bar keeps showing the old one
        return this.message.chatId == b.message.chatId && this.message.id == b.message.id && tgx.td.Td.equalsTo(this.quote, b.quote);
      } else {
        throw new UnsupportedOperationException();
      }
    }
  }

  private long contextChatId;
  private @Nullable List<Entry> staticList;
  private @Nullable MessageListManager messageList;

  public void collapse (boolean animated) {
    this.isExpanded.setValue(false, animated);
  }

  private void clearMessageList () {
    if (this.staticList != null) {
      for (Entry entry : staticList) {
        entry.performDestroy();
      }
      this.staticList = null;
    }
    if (this.messageList != null) {
      this.messageList.removeChangeListener(this);
      this.messageList = null;
    }
    this.focusIndex = RecyclerView.NO_POSITION;
  }

  public void setMessageList (@Nullable MessageListManager messageList) {
    if (this.messageList == messageList)
      return;
    clearMessageList();
    xgramResetHeld(); // XGram: the bar is set from its list below
    this.messageList = messageList;
    collapse(false);
    canExpand.setValue(messageList != null && messageList.getTotalCount() > 1, false);
    countAnimator.forceFactor(Math.max(0f, Math.min(3f, messageList != null ? messageList.getTotalCount() - 1 : 0)));
    if (messageList != null) {
      messageList.addChangeListener(this);
      List<ListItem> items = new ArrayList<>(messageList.getCount());
      for (TdApi.Message message : messageList) {
        items.add(itemOf(messageList.tdlib(), message));
      }
      messagesAdapter.setItems(items, false);
      messageList.loadInitialChunk(null);
    } else {
      messagesAdapter.setItems(new ListItem[0], false);
    }
  }

  public void setMessage (@Nullable Tdlib tdlib, @Nullable TdApi.Message message) {
    setMessage(tdlib, message, null);
  }

  public void setStaticMessageList (@Nullable List<Entry> entries, int scrollToPosition) {
    if (entries == null || entries.isEmpty()) {
      setMessageList(null);
      return;
    }
    if (this.staticList != null && this.staticList.size() == entries.size()) {
      boolean contentEquals = true;
      for (int i = 0; i < entries.size(); i++) {
        Entry oldEntry = this.staticList.get(i);
        Entry newEntry = entries.get(i);
        // TODO better check
        if (!oldEntry.equals(newEntry)) {
          contentEquals = false;
          break;
        }
      }
      if (contentEquals) {
        if (scrollToPosition != RecyclerView.NO_POSITION) {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          // TODO smooth?
          manager.scrollToPositionWithOffset(scrollToPosition, 0);
        }
        return;
      }
    }
    clearMessageList();
    xgramResetHeld(); // XGram: a fixed list, nothing to catch up with
    staticList = entries;
    collapse(false);
    canExpand.setValue(false, false);
    countAnimator.forceFactor(0);
    List<ListItem> items = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      entry.subscribeToUpdates();
      items.add(itemOf(entry));
    }
    messagesAdapter.setItems(items, false);
    if (scrollToPosition != RecyclerView.NO_POSITION) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      manager.scrollToPositionWithOffset(scrollToPosition, 0);
    }
  }

  public void setMessage (@Nullable Tdlib tdlib, @Nullable TdApi.Message message, @Nullable TdApi.InputTextQuote quote) {
    setMessage(tdlib, message, quote, null);
  }

  public void setMessage (@Nullable Tdlib tdlib, @Nullable TdApi.Message message, @Nullable TdApi.InputTextQuote quote, @Nullable MediaToReplacePickerManager.LocalPickedFile localPickedFile) {
    if (tdlib != null && message != null) {
      setStaticMessageList(Collections.singletonList(new Entry(tdlib, message, quote).setForceLocalPicledFile(localPickedFile)), RecyclerView.NO_POSITION);
    } else {
      setMessageList(null);
    }
  }

  public void setContextChatId (long contextChatId) {
    this.contextChatId = contextChatId;
  }

  private long maxFocusMessageId;

  public void setMaxFocusMessageId (long maxFocusMessageId) {
    if (this.maxFocusMessageId != maxFocusMessageId) {
      this.maxFocusMessageId = maxFocusMessageId;
      // TODO animate focus bar on the left
      // TODO ensure availability of pinned messages
    }
  }

  private static ListItem itemOf (@NonNull Entry entry) {
    return new ListItem(ListItem.TYPE_MESSAGE_PREVIEW, R.id.message).setData(entry);
  }

  private static ListItem itemOf (Tdlib tdlib, TdApi.Message message) {
    return new ListItem(ListItem.TYPE_MESSAGE_PREVIEW, R.id.message).setData(new Entry(tdlib, message, null));
  }

  @Override
  public void performDestroy () {
    setMessageList(null);
    messageViewport.clear();
  }

  public void completeDestroy () {
    messageViewport.performDestroy();
  }

  // XGram: the rows follow the list as one diff (xgramSyncItems) rather than an add, move or remove at the list's index.
  // SettingsAdapter postpones an add or remove that comes during a layout (UI.post) with the index of that moment; a
  // sync or another change in between made it land on the wrong row, and RecyclerView stopped with "Inconsistency
  // detected" (owner's Fold, 2026-10-27). A change during a layout now syncs in the next frame, once.

  private boolean xgramSyncPosted;

  private void xgramRequestSync () {
    if (recyclerView != null && recyclerView.isComputingLayout()) {
      if (!xgramSyncPosted) {
        xgramSyncPosted = true;
        recyclerView.post(() -> {
          xgramSyncPosted = false;
          if (messageList != null && !xgramHeld) {
            xgramSyncItems();
          }
        });
      }
      return;
    }
    xgramSyncItems();
  }

  @Override
  public void onItemsAdded (ListManager<TdApi.Message> list, List<TdApi.Message> items, int startIndex, boolean isInitialChunk) {
    if (xgramHoldItems()) {
      return; // XGram: shown when the bar is released (xgramSyncItems)
    }
    xgramRequestSync();
  }

  @Override
  public void onItemAdded (ListManager<TdApi.Message> list, TdApi.Message item, int toIndex) {
    if (xgramHoldItems()) {
      return; // XGram
    }
    boolean scrollBy = !messagesAdapter.getItems().isEmpty() && ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition() == 0;
    xgramRequestSync();
    if (scrollBy && toIndex == 0) {
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }
  }

  @Override
  public void onItemMoved (ListManager<TdApi.Message> list, TdApi.Message item, int fromIndex, int toIndex) {
    if (xgramHoldItems()) {
      return; // XGram
    }
    xgramRequestSync();
  }

  private boolean needsClear;

  @Override
  public void onItemRemoved (ListManager<TdApi.Message> list, TdApi.Message removedItem, int fromIndex) {
    if (xgramHoldItems()) {
      return; // XGram
    }
    // The last row stays until the next one comes (xgramSyncItems keeps it when the list is empty)
    xgramRequestSync();
  }

  @Override
  public void onItemChanged (ListManager<TdApi.Message> list, TdApi.Message item, int index, int cause) {
    if (xgramHoldItems()) {
      return; // XGram
    }
    // XGram: the list put a new object of this message in its place (it changed while the chat was closed, see
    // ListManager.xgramRevalidate): the row takes it, so its preview is built again
    if (index < 0 || index >= messagesAdapter.getItemCount()) {
      xgramRequestSync(); // XGram: the rows and the list disagree here: they follow the list
      return;
    }
    ListItem shown = messagesAdapter.getItem(index);
    Entry shownEntry = shown != null && shown.getData() instanceof Entry ? (Entry) shown.getData() : null;
    if (shownEntry != null && shownEntry.message != null && shownEntry.message != item &&
      shownEntry.message.chatId == item.chatId && shownEntry.message.id == item.id) {
      messagesAdapter.getItems().set(index, itemOf(list.tdlib(), item));
      messagesAdapter.notifyItemChanged(index);
      return;
    }
    if (cause == MessageListManager.CAUSE_CONTENT_CHANGED) {
      messagesAdapter.notifyItemChanged(index);
    }
  }
}
