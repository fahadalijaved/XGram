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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewReactionsCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ViewPager;
import org.thunderdog.challegram.widget.decoration.ItemDecorationFirstViewTop;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutRecyclerController;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class MessageOptionsPagerController extends BottomSheetViewController<OptionDelegate> implements
  FactorAnimator.Target, View.OnClickListener, Menu, DrawableProvider, PopupLayout.TouchDownInterceptor,
  Counter.Callback, TextColorSet {

  private final State state;
  private final ViewPagerTopView.Item[] counters;
  private int startPage = 0;

  public MessageOptionsPagerController (Context context, Tdlib tdlib, Options options, TGMessage message, TdApi.ReactionType defaultReactionType, OptionDelegate optionDelegate) {
    super(context, tdlib);
    if (optionDelegate == null)
      throw new IllegalArgumentException();
    setArguments(optionDelegate);

    this.state = new State(message, options, this::onReactionClick);
    this.state.headerAlwaysVisibleCountersWidth = 0;

    this.counters = new ViewPagerTopView.Item[getPagerItemCount()];

    // XGram: Material 3 Expressive header counters: labelLarge (14sp, 500) with an 8dp icon gap and 16dp on each side
    // of the content: assist chips on the options page (AssistChipTokens: 8dp leading space with the icon, 16dp
    // trailing space, drawn by the header cell), tabs with the 16dp-padded secondaryContainer pill on the other pages
    final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    final float counterTextSize = xgramM3 ? 14f : 13f;
    final float counterIconMargin = xgramM3 ? 8f : 6f;
    final int counterAddWidth = Screen.dp(xgramM3 ? 32f : 16f);

    int i = 0;
    if (state.needShowMessageOptions) {
      OPTIONS_POSITION = i++;
      counters[OPTIONS_POSITION] = new ViewPagerTopView.Item();
    } else {
      OPTIONS_POSITION = -1;
    }

    if (state.needShowMessageReactionSenders) {
      ALL_REACTED_POSITION = i++;
      counters[ALL_REACTED_POSITION] = new ViewPagerTopView.Item(new Counter.Builder()
        .noBackground().allBold(true).textSize(counterTextSize).colorSet(xgramM3 ? new XgramCounterColors(ALL_REACTED_POSITION) : this).callback(this)
        .drawable(R.drawable.baseline_favorite_16, 16f, counterIconMargin, Gravity.LEFT)
        .build(), this, counterAddWidth);
      counters[ALL_REACTED_POSITION].counter.setCount(message.getMessageReactions().getTotalCount(), false);
      int itemWidth = counters[ALL_REACTED_POSITION].calculateWidth(null, Screen.dp(ViewPagerTopView.DEFAULT_ITEM_SPACING));
      state.headerAlwaysVisibleCountersWidth += itemWidth;
      state.xgramChipWidths[state.xgramChipCount++] = itemWidth;
    } else {
      ALL_REACTED_POSITION = -1;
    }

    if (state.needShowMessageViews) {
      SEEN_POSITION = i++;
      counters[SEEN_POSITION] = new ViewPagerTopView.Item(new Counter.Builder()
        .noBackground().allBold(true).textSize(counterTextSize).colorSet(xgramM3 ? new XgramCounterColors(SEEN_POSITION) : this).callback(this).visibleIfZero()
        .drawable(R.drawable.baseline_visibility_16, 16f, counterIconMargin, Gravity.LEFT)
        .build(), this, counterAddWidth);
      counters[SEEN_POSITION].counter.setCount(1, false);
      int itemWidth = counters[SEEN_POSITION].calculateWidth(null, Screen.dp(ViewPagerTopView.DEFAULT_ITEM_SPACING)); // - Screen.dp(16);
      state.headerAlwaysVisibleCountersWidth += itemWidth;
      state.xgramChipWidths[state.xgramChipCount++] = itemWidth;
      counters[SEEN_POSITION].setStaticWidth(itemWidth - counterAddWidth);
      counters[SEEN_POSITION].counter.setCount(Tdlib.CHAT_LOADING, false);
      getMessageOptions();
    } else {
      SEEN_POSITION = -1;
    }

    if (state.needShowMessageReactionSenders) {
      REACTED_START_POSITION = i;
      for (TdApi.MessageReaction reaction : state.messageReactions.reactions) {
        TGReaction tgReaction = tdlib.getReaction(reaction.type);
        counters[i] = new ViewPagerTopView.Item(tgReaction, new Counter.Builder()
          .noBackground().allBold(true).textSize(counterTextSize).colorSet(xgramM3 ? new XgramCounterColors(i) : this).callback(this)
          .build(), this, xgramM3 ? counterAddWidth : Screen.dp(9));
        counters[i].counter.setCount(reaction.totalCount, false);
        if (Td.equalsTo(reaction.type, defaultReactionType)) {
          startPage = i;
        }
        i += 1;
      }
    } else {
      REACTED_START_POSITION = -1;
    }

    if (!state.needShowMessageOptions) {
      state.headerAlwaysVisibleCountersWidth = 0;
      state.xgramChipCount = 0;
    }
    xgramPagerSelection = startPage;
  }

  // XGram: Material 3 Expressive counter colors, from the pager position (onPageScrolled)

  private float xgramPagerSelection;

  /**
   * The label and icon color of a header counter: onSurface on its assist chip (the options page), onSecondaryContainer
   * while it is the selected tab (on the secondaryContainer pill), onSurfaceVariant otherwise, blended while the pager moves.
   */
  private final class XgramCounterColors implements TextColorSet {
    private final int index;

    XgramCounterColors (int index) {
      this.index = index;
    }

    @Override
    public int defaultTextColor () {
      final float selection = xgramPagerSelection;
      final float selected = MathUtils.clamp(1f - Math.abs(selection - index));
      int color = ColorUtils.blendARGB(
        com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT),
        com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER),
        selected
      );
      if (OPTIONS_POSITION == 0) {
        final float chip = MathUtils.clamp(1f - selection);
        if (chip > 0f) {
          color = ColorUtils.blendARGB(color, com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), chip);
        }
      }
      return color;
    }
  }

  /** XGram: M3 tabs and chips show their content at full opacity (Telegram X dims unselected counters to .5). */
  private static final ViewPagerTopView.CounterAlphaProvider XGRAM_OPAQUE_COUNTERS = new ViewPagerTopView.CounterAlphaProvider() {
    @Override
    public float getTextAlpha (Counter counter, float alphaFactor) {
      return 1f;
    }

    @Override
    public float getDrawableAlpha (Counter counter, float alphaFactor) {
      return 1f;
    }

    @Override
    public float getBackgroundAlpha (Counter counter, float alphaFactor) {
      return 1f;
    }
  };

  private ViewPagerHeaderViewReactionsCompact headerCell;

  // Create view

  private float headerViewOverTranslation;

  public void setHeaderViewOverTranslation (float headerViewOvertranslation) {
    this.headerViewOverTranslation = headerViewOvertranslation;
    if (headerView != null) {
      headerView.setTranslationY(headerTranslationY);
    }
  }

  @Override
  protected HeaderView onCreateHeaderView () {
    HeaderView headerView = new HeaderView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MessageOptionsPagerController.this.getHeaderHeight(), MeasureSpec.EXACTLY)); // XGram: 54dp, or none (see getHeaderHeight)
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (startPage != 0) {
          setCurrentPagerPosition(startPage, false);
          startPage = 0;
        }
      }

      private float rTranslationY;

      @Override
      public void setTranslationY (float translationY) {
        super.setTranslationY(translationY + (rTranslationY = headerViewOverTranslation));
      }

      @Override
      public float getTranslationY () {
        return super.getTranslationY() - rTranslationY;
      }
    };
    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    if (state.xgramPickerInHeader || com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: in Material 3 Expressive the header stays transparent: the content view draws the whole sheet under it
      // as one shape (BottomSheetViewController.xgramDrawSheet), a 54dp header band of its own left the content view's
      // square body under its rounded corners. Telegram X: the popup picker's collapsed row draws the header's color;
      // with the reaction bar at the bottom it draws nothing while collapsed, so the header keeps its own (else branch).
      headerView.setBackground(null);
    } else {
      ViewSupport.setThemedBackground(headerView, ColorId.background, this);
    }

    return headerView;
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    if (reactionsPickerController != null) {
      reactionsPickerController.setBottomInset(extraBottomInset, extraBottomInsetWithoutIme);
    }
  }

  @Override
  protected void onBeforeCreateView () {
    headerCell = new ViewPagerHeaderViewReactionsCompact(context, state) {
      @Override
      public void onThemeInvalidate (boolean isTempUpdate) {
        setHeaderBackgroundFactor(headerBackgroundFactor);
        getBackButton().setColor(Theme.getColor(ColorId.headerLightIcon));
        super.onThemeInvalidate(isTempUpdate);
      }
    };
    addThemeInvalidateListener(headerCell);
  }

  @Override
  protected View onCreateView (Context context) {
    ViewGroup vg = (ViewGroup) super.onCreateView(context);

    if (state.needShowReactionsPopupPicker) {
      reactionsPickerWrapper = new FrameLayoutFix(context) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent ev) {
          if (needIgnoreTouchEvent(ev)) {
            return false;
          }
          return super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean onInterceptTouchEvent (MotionEvent ev) {
          if (needIgnoreTouchEvent(ev)) {
            return false;
          }
          return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent (MotionEvent ev) {
          if (needIgnoreTouchEvent(ev)) {
            return false;
          }
          return super.onTouchEvent(ev);
        }

        private boolean needIgnoreTouchEvent (MotionEvent ev) {
          if ((com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || !state.xgramPickerInHeader) && !reactionsPickerVisibility.getValue()) {
            // XGram: the collapsed grid is hidden: the header's reaction strip (or, with the reaction bar at the bottom,
            // the options list's bar row) takes the touches
            return true;
          }
          return (ev.getAction() == MotionEvent.ACTION_DOWN && !between(ev.getY(), getPickerTop()
            - HeaderView.getSize(true) * reactionsPickerController.getTopHeaderVisibility() * reactionsPickerVisibility.getFloatValue(),
            getPickerBottom()));
        }

        private boolean between (float y, float a, float b) {
          return y > a && y < b;
        }

        @Override
        protected void dispatchDraw (Canvas canvas) {
          if (!state.xgramPickerInHeader && reactionsPickerVisibility.getFloatValue() == 0f) {
            // XGram: reaction bar at the bottom: the collapsed picker has no row in the header (the sheet under it draws
            // the header row, its handle band included), it shows only while it opens from the bar's expand button
            return;
          }
          float top = getPickerTop();
          float bottom = getPickerBottom();
          if (bottom <= top) return;

          if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
            // XGram: Material 3 Expressive modal sheet over the reaction row too: 28dp top corners and a drag handle
            // in a band above the row, both kept until the sheet reaches the top of the window, or until the picker's
            // top app bar shows (the sheet is then full screen: square under the bar, no corner gaps). The reaction row
            // itself is the header's strip (its own pill): the grid only shows while the picker expands (its alpha
            // follows the expansion, see onFactorChanged).
            float corner = xgramSheetCornerFactor(top);
            if (reactionsPickerController != null) {
              corner *= 1f - reactionsPickerController.getTopHeaderVisibility() * reactionsPickerVisibility.getFloatValue();
            }
            // the band holds the drag handle (8dp from the top, about 8dp above the reaction row's pill). The sheet top
            // spans the picker's whole height, so its 28dp curve is never cut short; the content view's sheet under it
            // starts at the reaction row and stays inside this curve.
            float band = xgramHandleBand() * corner;
            if (xgramPickerSheetTop == null) {
              xgramPickerSheetTop = new com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable();
            }
            xgramPickerSheetTop.set(Theme.backgroundColor(), corner);
            xgramPickerSheetTop.setHandleAlpha(corner);
            xgramPickerSheetTop.setBounds(0, (int) (top - band), getMeasuredWidth(), (int) Math.ceil(bottom));
            xgramPickerSheetTop.draw(canvas);
            canvas.save();
            canvas.clipRect(0, top, getMeasuredWidth(), bottom);
            super.dispatchDraw(canvas);
            canvas.restore();
            return;
          }

          canvas.save();
          canvas.clipRect(0, top, getMeasuredWidth(), bottom);
          canvas.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.backgroundColor()));
          super.dispatchDraw(canvas);
          canvas.restore();
        }
      };
      vg.addView(reactionsPickerWrapper, 2, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      reactionsPickerWrapper.addView(createReactionsPopupPicker());
    }
    return vg;
  }

  private @Nullable com.fahadali.xgram.appearance.XgramM3Chat.SheetTopDrawable xgramPickerSheetTop;

  /** XGram: 1 while the sheet top is well below the status bar, 0 as it docks (see XgramM3Sheets.cornerFactor). */
  private static float xgramSheetCornerFactor (float sheetTop) {
    return com.fahadali.xgram.appearance.XgramM3Sheets.cornerFactor(sheetTop); // the shared sheet chrome rule
  }

  @Override
  protected float xgramSheetHandleAlpha () {
    // XGram: with the popup picker in the header row its sheet top (above the reaction row) has the handle, the content
    // view's sheet under it only continues the shape. Without it (or with the reaction bar at the bottom, where the
    // collapsed picker draws nothing) the handle fades out with its band (as the picker's does).
    return state.xgramPickerInHeader ? 0f : xgramSheetCorners();
  }

  @Override
  protected float xgramSheetBand () {
    // XGram: without the popup picker in the header row the content view's sheet has the handle band above the 54dp
    // header row (its reaction pill and tabs fill the row), as the picker's sheet top has; or above the options list
    // when the sheet has no header row
    return state.xgramPickerInHeader ? 0f : xgramHandleBand() * xgramSheetCorners();
  }

  /** XGram: the band above the header row that holds the drag handle: 8dp, the 4dp handle, 4dp to the row. */
  private static final float XGRAM_HANDLE_BAND = 16f;
  /**
   * XGram: the band without a header row (reaction bar at the bottom or off, no counters): 8dp, the handle, 8dp to the
   * list, whose first tile starts 8dp lower, where the 28dp curve ends (the pages are clipped above it).
   */
  private static final float XGRAM_HANDLE_BAND_NO_ROW = 20f;

  private float xgramHandleBand () {
    return Screen.dp(state.xgramNoHeaderRow ? XGRAM_HANDLE_BAND_NO_ROW : XGRAM_HANDLE_BAND);
  }

  private void invalidatePickerWrapper () {
    if (reactionsPickerWrapper != null) {
      reactionsPickerWrapper.invalidate();
      setHeaderViewOverTranslation(getPickerTop() - headerTranslationY);
    }
  }

  @Override
  protected void setHeaderPosition (float y) {
    super.setHeaderPosition(y); // XGram: in Material 3 Expressive this also redraws the sheet (the content view)
    checkReactionPickerPosition();
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    pager.setOffscreenPageLimit(1);
    prepareControllerForPosition(startPage, null);
    tdlib.ui().post(this::launchOpenAnimation);

    headerCell.getTopView().setItemPadding(Screen.dp(0));
    headerCell.getTopView().setItems(Arrays.asList(counters));
    headerCell.getTopView().setOnItemClickListener(this);
    headerCell.getTopView().setSelectionColorId(ColorId.text);
    addThemeInvalidateListener(headerCell.getTopView());

    headerCell.getBackButton().setColor(Theme.getColor(ColorId.headerLightIcon));
    headerCell.getBackButton().setOnClickListener((v) -> {
      if (state.needShowMessageOptions) {
        headerCell.getTopView().getOnItemClickListener().onPagerItemClick(0);
      } else {
        hidePopupWindow(true);
      }
    });

    if (headerCell.getMoreButton() != null) {
      headerCell.getMoreButton().setOnClickListener(v -> showReactionPicker());
    }

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive tabs: full opacity content, each reaction tab's content (drawn from its start by
      // ViewPagerTopView) centered in the item so the pill has 16dp on both sides; a swipe up on the reaction strip
      // expands the popup picker
      headerCell.getTopView().setCounterAlphaProvider(XGRAM_OPAQUE_COUNTERS);
      if (REACTED_START_POSITION != -1) {
        for (int i = REACTED_START_POSITION; i < counters.length; i++) {
          headerCell.getTopView().setItemTranslationX(i, Screen.dp(16f));
        }
      }
      if (state.xgramPickerInHeader) {
        headerCell.xgramSetOnExpandRequest(this::showReactionPicker);
      }
    }
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    positionOffset = ViewPager.clampPositionOffset(positionOffset);
    if (this.checkedBasePosition != position) {
      checkedBasePosition = position;
      checkContentScrollY(position);
    }
    if (positionOffset == 0f) {
      checkedPosition = -1;
    }
    if (positionOffset != 0f) {
      int checkPosition = position + 1;
      if (this.checkedPosition != checkPosition) {
        this.checkedPosition = checkPosition;
        checkContentScrollY(checkPosition);
      }
    }
    currentMediaPosition = position;
    currentPositionOffset = positionOffset;
    xgramPagerSelection = position + positionOffset; // XGram: counter colors
    setIgnoreAnyPagerScrollEventsBecauseOfMovements(positionOffset != 0f);

    if (headerCell != null) {
      headerCell.onPageScrolled(position, positionOffset);
    }

    if (position == 0 && positionOffset == 0f && state.needShowMessageOptions) {
      ViewController<?> controller = findCachedControllerByPosition(0);
      if (controller instanceof BottomSheetViewController.BottomSheetBaseControllerPage) {
        ((BottomSheetBaseControllerPage) controller).onScrollToBottomRequested();
      }
    }

    if (position == 0 && state.needShowMessageOptions) {
      float targetPosition = getContentOffset() + HeaderView.getTopOffset();
      float animPosition = targetPosition + (lastHeaderPosition - targetPosition) * positionOffset;
      setHeaderPosition(animPosition);
      if (positionOffset == 0f) {
        lastHeaderPosition = targetPosition;
      }
    }

    if (reactionsPickerRecyclerView != null) {
      reactionsPickerRecyclerView.setTranslationX(-MathUtils.clamp(position + positionOffset) * reactionsPickerRecyclerView.getMeasuredWidth());
      invalidatePickerWrapper();
    }

    super.onPageScrolled(position, positionOffset, positionOffsetPixels);
  }

  @Override
  protected void setHeaderBackgroundFactor (float headerBackgroundFactor) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive modal sheet: one tone with its pages (the background color id, a surface
      // container role), 28dp top corners that square off at the top, a drag handle. The content view draws it as one
      // shape (corners following the sheet top, not this factor); the header stays transparent over it.
      final int sheetColor = Theme.getColor(ColorId.background);
      setLickViewColor(sheetColor);
      if (headerView != null && headerView.getBackground() != null) {
        headerView.setBackground(null);
      }
      if (contentView != null) {
        contentView.invalidate();
      }
      if (headerCell != null) {
        headerCell.updatePaints(sheetColor);
      }
      invalidatePickerWrapper();
      return;
    }
    final int headerBackground = ColorUtils.blendARGB(
      Theme.getColor(ColorId.background),
      Theme.getColor(ColorId.headerLightBackground),
      headerBackgroundFactor
    );
    setLickViewColor(headerBackground);
    if (headerView != null && !state.xgramPickerInHeader) { // XGram: the collapsed picker draws it only in the header row
      headerView.setBackgroundColor(headerBackground);
    }
    if (headerCell != null) {
      headerCell.updatePaints(headerBackground);
    }
    invalidatePickerWrapper();
  }

  //

  private TdApi.MessageViewers messageViewers;
  private void getMessageOptions () {
    tdlib.send(new TdApi.GetMessageViewers(state.message.getChatId(), state.message.getId()), (messageViewers, error) -> {
      if (error != null) {
        return;
      }
      runOnUiThreadOptional(() -> {
        this.messageViewers = messageViewers;
        if (SEEN_POSITION != -1) {
          counters[SEEN_POSITION].counter.setCount(messageViewers.viewers.length, false);
        }
      });
    });
  }



  private CharSequence cachedHint;
  private int cachedHintHeight, cachedHintAvailWidth;

  private int getOptionItemsHeight () {
    int optionItemsHeight = state.options.items != null ? Screen.dp(54) * state.options.items.length : 0;
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && state.options.items != null && state.options.items.length > 0) {
      // XGram: the Expressive list is taller than Telegram X's 54dp rows (56dp tiles in groups, see MessageOptionsController):
      // counted as 54dp rows the sheet opened with its last group (Delete, or the reaction bar above it) below the screen
      optionItemsHeight = MessageOptionsController.xgramTilesHeight(state.options.items);
    }
    if (state.xgramReactionsInList) {
      optionItemsHeight += com.fahadali.xgram.chats.XgramReactionBar.rowSpace(); // XGram: the reaction bar row (Bottom)
    }
    int hintHeight;
    if (!StringUtils.isEmpty(state.options.info)) {
      int availWidth = Screen.currentWidth() - Screen.dp(16f) * 2; // FIXME: rely on parent view width
      if (cachedHint != null && cachedHintAvailWidth == availWidth && cachedHint.equals(state.options.info)) {
        hintHeight = cachedHintHeight;
      } else {
        hintHeight = CustomTextView.measureHeight(this, state.options.info, 0, 15f, availWidth);
        cachedHint = state.options.info;
        cachedHintAvailWidth = availWidth;
        cachedHintHeight = hintHeight;
      }
      hintHeight += Screen.dp(14f) + Screen.dp(6f);
    } else {
      hintHeight = 0;
    }
    if (state.emojiPackIds.length > 0) {
      hintHeight += Screen.dp(40);
    }
    if (state.options.subtitle != null) {
      // FIXME: this works only for single line
      hintHeight += Screen.dp(15f) + Screen.dp(7f) + Screen.dp(8f);
    }
    return optionItemsHeight + hintHeight;
  }

  @Override
  protected int getContentOffset () {
    if (state.needShowMessageOptions) {
      return (getTargetHeight()
        - (getHeaderHeight() + HeaderView.getTopOffset()) // XGram: 54dp, or no header row (see getHeaderHeight)
        - getOptionItemsHeight()
        - Screen.dp(1)
        - (Settings.instance().useEdgeToEdge() ? context().getRootView().getSystemInsetsWithoutIme().bottom : 0)
      );
    } else {
      return Screen.currentHeight() / 2;
    }
  }

  // Pager

  private final int OPTIONS_POSITION;
  private final int SEEN_POSITION;
  private final int ALL_REACTED_POSITION;
  private final int REACTED_START_POSITION;

  public @Nullable MessageOptionsController findOptionsController () {
    return (MessageOptionsController) findCachedControllerByPosition(OPTIONS_POSITION);
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    if (position == OPTIONS_POSITION) {
      View.OnClickListener onClickListener = view -> {
        if (getArgumentsStrict().onOptionItemPressed(view, view.getId())) {
          hidePopupWindow(true);
        }
      };

      MessageOptionsController c = new MessageOptionsController(context, this.tdlib, getThemeListeners());
      c.setArguments(new MessageOptionsController.Args(state.options, onClickListener, state.message.getFirstEmojiId(), state.emojiPackIds, () -> hidePopupWindow(true))
        // XGram: Reaction bar: Bottom: the reactions as a row of the options, its expand button opens the popup picker
        .xgramReactionBar(state.xgramReactionsInList ? state : null, state.xgramReactionsInList && state.needShowReactionsPopupPicker ? this::showReactionPicker : null));
      c.getValue();
      setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
      setDefaultListenersAndDecorators(c);
      return c;
    }

    if (position == ALL_REACTED_POSITION) {
      MessageOptionsReactedController c = new MessageOptionsReactedController(context, this.tdlib, getPopupLayout(), state.message, null);
      c.getValue();
      setDefaultListenersAndDecorators(c);
      return c;
    }

    if (position == SEEN_POSITION) {
      MessageOptionsSeenController c = new MessageOptionsSeenController(context, this.tdlib, getPopupLayout(), state.message);
      c.getValue();
      setDefaultListenersAndDecorators(c);
      return c;
    }

    if (position >= REACTED_START_POSITION && REACTED_START_POSITION != -1) {
      MessageOptionsReactedController c = new MessageOptionsReactedController(context, this.tdlib, getPopupLayout(), state.message, state.messageReactions.reactions[position - REACTED_START_POSITION].type);
      c.getValue();
      if (isFirstCreation && !state.needShowMessageOptions) {
        setHeaderPosition(getContentOffset() + HeaderView.getTopOffset());
        isFirstCreation = false;
      }
      setDefaultListenersAndDecorators(c);
      return c;
    }

    throw new IllegalArgumentException("position == " + position);
  }

  @Override
  protected int getPagerItemCount () {
    return state.getPagesCount();
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }




  // getters

  @Override
  public int getId () {
    return R.id.controller_messageOptionsPager;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  protected int getHeaderHeight () {
    // XGram: no header row when it would be empty: the reaction bar is at the bottom or off, and there are no counters
    // (nor pages to switch to). The sheet then starts with the options, under the handle band.
    return state.xgramNoHeaderRow ? 0 : Screen.dp(54);
  }

  /** XGram: the popup picker's collapsed row is a 54dp row at the sheet top, also on a sheet without a header row. */
  private static int xgramPickerRowHeight () {
    return Screen.dp(54);
  }


  // listeners

  @Override
  public void onClick (View v) {

  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (super.performOnBackPressed(fromTop, commit)) {
      return true;
    }
    if (reactionsPickerVisibility != null && reactionsPickerVisibility.getValue()) {
      if (!reactionsPickerController.performOnBackPressed(fromTop, commit)) {
        if (commit) {
          hideReactionPicker();
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public void hideSoftwareKeyboard () {
    if (reactionsPickerController != null) {
      reactionsPickerController.hideSoftwareKeyboard();
      return;
    }
    super.hideSoftwareKeyboard();
  }

  @Override
  public void destroy () {
    super.destroy();
    if (reactionsPickerController != null) {
      reactionsPickerController.destroy();
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  public void onCounterAppearanceChanged (Counter counter, boolean sizeChanged) {
    if (headerCell != null) {
      headerCell.getTopView().invalidate();
    }
  }


  // Sparse drawable function
  private SparseArrayCompat<Drawable> sparseDrawables;

  @Override
  public final SparseArrayCompat<Drawable> getSparseDrawableHolder () {
    return (sparseDrawables != null ? sparseDrawables : (sparseDrawables = new SparseArrayCompat<>()));
  }

  @Override
  public final Resources getSparseDrawableResources () {
    return context().getResources();
  }


  //

  @Override
  public int defaultTextColor () {
    return Theme.getColor(ColorId.text);
  }

  private Client.ResultHandler handler (View v, Runnable onSuccess) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          tdlib.ui().post(onSuccess);
          break;
        case TdApi.Error.CONSTRUCTOR:
          tdlib.ui().post(() -> onSendError(v, (TdApi.Error) object));
          break;
      }
    };
  }

  private void onSendError (View v, TdApi.Error error) {
    context().tooltipManager().builder(v).show(tdlib, TD.toErrorString(error)).hideDelayed(3500, TimeUnit.MILLISECONDS);
    state.message.cancelScheduledSetReactionAnimation();
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void setupPopupLayout (PopupLayout popupLayout) {
    popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.init(true);
    //popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setTouchProvider(this);
    popupLayout.setIgnoreHorizontal();
    popupLayout.setTouchDownInterceptor(this);

   // super.setupPopupLayout(popupLayout);
  }

  private static final int KEYBOARD_HEIGHT = 2;
  private final FactorAnimator keyboardHeight = new FactorAnimator(KEYBOARD_HEIGHT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, 0);

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    final boolean r = super.onKeyboardStateChanged(visible);
    keyboardHeight.animateTo(getKeyboardState() ? Keyboard.getSize(Keyboard.getSize()) : 0);
    if (reactionsPickerRecyclerView != null) {
      reactionsPickerRecyclerView.invalidateItemDecorations();
    }
    return r;
  }

  /* Reactions popup picker */

  private static final int REACTIONS_PICKER_VISIBILITY_ANIMATOR_ID = 0;

  private BoolAnimator reactionsPickerVisibility;

  private FrameLayoutFix reactionsPickerWrapper;
  private ReactionsPickerController reactionsPickerController;
  private CustomRecyclerView reactionsPickerRecyclerView;
  private PickerOpenerScrollListener reactionsPickerScrollListener;
  private View reactionsPickerBottomHeaderView;
  private boolean doNotUpdateScrollReactionPicker;
  private ItemDecorationFirstViewTop reactionPickerTopDecoration;

  private CustomRecyclerView createReactionsPopupPicker () {
    reactionsPickerVisibility = new BoolAnimator(REACTIONS_PICKER_VISIBILITY_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280L, false);
    reactionsPickerController = new ReactionsPickerController(context, tdlib) {
      @Override
      protected void onBottomHeaderEnterSearchMode () {
        reactionPickerTopDecoration.scheduleDisableDecorationOffset();
      }

      @Override
      protected void onBottomHeaderLeaveSearchMode () {
        reactionPickerTopDecoration.enableDecorationOffset();
      }

      @Override
      protected void onTopHeaderVisibilityChanged (float factor) {
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && reactionsPickerWrapper != null) {
          reactionsPickerWrapper.invalidate(); // XGram: the sheet top's corners follow the top app bar
        }
      }
    };
    reactionsPickerController.setArguments(state);
    reactionsPickerController.setBottomInset(extraBottomInset, extraBottomInsetWithoutIme);
    reactionsPickerController.getValue();

    reactionsPickerRecyclerView = reactionsPickerController.getRecyclerView();
    reactionsPickerRecyclerView.setClipToPadding(false);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || !state.xgramPickerInHeader) {
      // XGram: hidden until expanded (the header's strip is the collapsed row, or the options list's bar row when the
      // reaction bar is at the bottom)
      reactionsPickerRecyclerView.setAlpha(0f);
    }

    reactionPickerTopDecoration = ItemDecorationFirstViewTop.attach(reactionsPickerRecyclerView, this::getReactionPickerOffsetTopReal);
    reactionsPickerRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        final int position = parent.getChildAdapterPosition(view);
        final int itemCount = parent.getAdapter().getItemCount();
        final boolean isUnknown = position == RecyclerView.NO_POSITION;
        final int itemType = position < itemCount && !isUnknown ? parent.getAdapter().getItemViewType(position) : -1;
        int leftRight = 0, bottom = 0;

        if (itemType == MediaStickersAdapter.StickerHolder.TYPE_STICKER || view instanceof StickerSmallView) {
          leftRight = Screen.dp(-1);
        }

        if (position == itemCount - 1) {
          int keyboardHeight = getKeyboardState() ? Keyboard.getSize(Keyboard.getSize()) : 0;
          bottom = Math.max(parent.getMeasuredHeight() - parent.getPaddingBottom() - reactionsPickerController.measureItemsHeight(), keyboardHeight + Screen.dp(64));
        }

        outRect.set(leftRight, 0, leftRight, bottom);
      }
    });
    reactionsPickerRecyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    reactionsPickerRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        invalidatePickerWrapper();
        checkReactionPickerHeaderTopVisibility();
      }

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          invalidatePickerWrapper();
          checkReactionPickerHeaderTopVisibility();
        }
      }
    });
    reactionsPickerRecyclerView.addOnScrollListener(reactionsPickerScrollListener = new PickerOpenerScrollListener());
    reactionsPickerRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> invalidatePickerWrapper());

    return reactionsPickerRecyclerView;
  }

  private void showReactionPicker () {
    fixView.setVisibility(View.GONE);

    if (reactionsPickerBottomHeaderView == null) {
      reactionsPickerBottomHeaderView = reactionsPickerController.getBottomHeaderViewGroup();
      reactionsPickerBottomHeaderView.setAlpha(0f);
      reactionsPickerBottomHeaderView.setVisibility(View.GONE);
      reactionsPickerController.getTopHeaderView().getBackButton().setOnClickListener(v -> {
        hideReactionPicker();
      });

      if (state.needShowCustomEmojiInsidePicker) {
        wrapView.addView(reactionsPickerBottomHeaderView);
      }
      wrapView.addView(reactionsPickerController.getTopHeaderViewGroup());
      reactionsPickerController.prepareToShow();
    }

    reactionsPickerVisibility.setValue(true, true);

  }

  private void hideReactionPicker () {
    doNotUpdateScrollReactionPicker = true;
    reactionsPickerRecyclerView.stopScroll();
    reactionsPickerScrollListener.reset(true);
    reactionsPickerVisibility.setValue(false, true);
    reactionsPickerController.closeBottomHeaderSearchMode(false);
    reactionsPickerController.scrollToDefaultPosition(getReactionPickerOffsetTopReal());
    reactionPickerTopDecoration.enableDecorationOffset();
    contentView.setVisibility(View.VISIBLE);
    if (headerView != null) {
      headerView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected void onCustomShowComplete () {
    super.onCustomShowComplete();
    if (reactionsPickerRecyclerView != null) {
      reactionsPickerRecyclerView.invalidateItemDecorations();
      reactionsPickerRecyclerView.scrollToPosition(0);
    }
  }

  private void checkReactionPickerPosition () {
    if (reactionsPickerWrapper == null) {
      return;
    }

    float y = MathUtils.fromTo(0, Math.min(getReactionPickerOffsetTopDefault() - getReactionPickerOffsetTopReal(), 0), reactionsPickerVisibility.getFloatValue());
    boolean isNotInScroll = reactionsPickerRecyclerView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
    if (isNotInScroll && !doNotUpdateScrollReactionPicker) {
      ((LinearLayoutManager) reactionsPickerRecyclerView.getLayoutManager()).scrollToPositionWithOffset(0, (int) y);
    }

    reactionsPickerRecyclerView.setTranslationY((headerTranslationY - Math.max(xgramPickerRowHeight(), getContentOffset()) - HeaderView.getTopOffset())
      * (1f - reactionsPickerVisibility.getFloatValue()));
    invalidatePickerWrapper();
    checkReactionPickerHeaderTopVisibility();
  }

  private void onReactionClick (View v, TGReaction reaction, boolean isLongClick) {
    if (isLongClick) {
      if (Config.DISABLE_ANONYMOUS_NON_OWNER_REACTIONS && tdlib.isAnonymousAdminNonCreator(state.message.getChatId())) {
        return;
      }
      int[] positionCords = new int[2];
      v.getLocationOnScreen(positionCords);

      int startX = positionCords[0] + v.getMeasuredWidth() / 2;
      int startY = positionCords[1] + v.getMeasuredHeight() / 2;

      if (!Config.PROTECT_ANONYMOUS_REACTIONS || state.message.messagesController().callNonAnonymousProtection(state.message.getId() + reaction.getId(), tooltipManager().builder(v))) {
        if (state.message.getMessageReactions().toggleReaction(reaction.type, true, true, handler(v, () -> {
        }))) {
          state.message.scheduleSetReactionAnimationFullscreenFromBottomSheet(reaction, new Point(startX, startY));
        }
        hidePopupWindow(true);
      }
    } else {
      int[] positionCords = new int[2];
      v.getLocationOnScreen(positionCords);

      int startX = positionCords[0] + v.getMeasuredWidth() / 2;
      int startY = positionCords[1] + v.getMeasuredHeight() / 2;

      boolean hasReaction = state.message.getMessageReactions().hasReaction(reaction.type);
      if (Config.DISABLE_ANONYMOUS_NON_OWNER_REACTIONS && !hasReaction && tdlib.isAnonymousAdminNonCreator(state.message.getChatId())) {
        tooltipManager().builder(v).show(tdlib, R.string.error_ANONYMOUS_REACTIONS_DISABLED).hideDelayed();
        return;
      }
      if (!Config.PROTECT_ANONYMOUS_REACTIONS || hasReaction || state.message.messagesController().callNonAnonymousProtection(state.message.getId() + reaction.getId(), tooltipManager().builder(v))) {
        if (state.message.getMessageReactions().toggleReaction(reaction.type, false, true, handler(v, () -> {
        }))) {
          state.message.scheduleSetReactionAnimationFromBottomSheet(reaction, new Point(startX, startY));
        }
        hidePopupWindow(true);
      }
    }
  }


  /* * */

  @Override
  protected int getTopEdge () {
    return Math.max(0, (int) (getPickerTop() - HeaderView.getTopOffset() - HeaderView.getSize(true) * (reactionsPickerController != null ? reactionsPickerController.getTopHeaderVisibility() * reactionsPickerVisibility.getFloatValue() : 0f)));
  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < getPickerTop() - HeaderView.getSize(true);
  }

  @Override
  public boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e) {
    if (reactionsPickerVisibility != null && reactionsPickerVisibility.getValue()) {
      hideReactionPicker();
      return true;
    }
    return false;
  }

  /* * */

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == REACTIONS_PICKER_VISIBILITY_ANIMATOR_ID) {
      float pickerOffset = Math.min(getOptionItemsHeight(), getTargetHeight());
      invalidatePickerWrapper();
      contentView.setTranslationY(pickerOffset * factor);
      if (headerView != null) {
        headerView.setAlpha(1f - factor);
      }
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || !state.xgramPickerInHeader) {
        reactionsPickerRecyclerView.setAlpha(factor); // XGram: the grid fades in as the header's strip fades out (or the options slide away)
      }
      float alpha = factor * (state.isPremium ? 1f: 0f);
      reactionsPickerBottomHeaderView.setAlpha(alpha);
      reactionsPickerBottomHeaderView.setVisibility(alpha > 0f ? View.VISIBLE: View.GONE);
      checkReactionPickerPosition();
    }

    if (reactionsPickerBottomHeaderView != null) {
      float pickerOffset = Math.min(getOptionItemsHeight(), getTargetHeight());
      reactionsPickerBottomHeaderView.setTranslationY(-pickerOffset * (1f - reactionsPickerVisibility.getFloatValue()) - keyboardHeight.getFactor());
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == REACTIONS_PICKER_VISIBILITY_ANIMATOR_ID) {
      if (finalFactor == 1f) {
        contentView.setVisibility(View.GONE);
        if (headerView != null) {
          headerView.setVisibility(View.GONE);
        }
      }
      reactionsPickerScrollListener.reset(false);
      doNotUpdateScrollReactionPicker = false;
      checkReactionPickerHeaderTopVisibility();
      if (headerView != null) {
        headerView.setTranslationY(headerTranslationY);
      }
    }
  }



  /* Picker layout */

  private float getPickerTop () {
    if (reactionsPickerRecyclerView == null || reactionsPickerWrapper == null || reactionsPickerVisibility == null) {
      return headerTranslationY;
    }

    float top = Views.getRecyclerViewElementTop(reactionsPickerRecyclerView, 1) + reactionsPickerRecyclerView.getTranslationY() - getPickerTopPadding();
    return MathUtils.fromTo(Math.min(top, headerTranslationY), top, reactionsPickerVisibility.getFloatValue());
  }

  private float getPickerBottom () {
    if (reactionsPickerRecyclerView == null || reactionsPickerWrapper == null || reactionsPickerVisibility == null) {
      return headerTranslationY + getHeaderHeight();
    }

    return MathUtils.fromTo(headerTranslationY + xgramPickerRowHeight(), reactionsPickerWrapper.getMeasuredHeight(), reactionsPickerVisibility.getFloatValue());
  }

  public static float getPickerTopPadding () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Screen.dp(3f); // XGram: (54dp header - 48dp grid rows) / 2, see ReactionsPickerController.getItemHeight
    }
    return Screen.dp(4.5f); // ((getHeaderHeight() - Screen.dp(45)) / 2f);
  }

  private int lastReactionPickerOffsetReal;

  private int getReactionPickerOffsetTopReal () {
    int offset = Math.max(0, getContentOffset() - HeaderView.getSize(false) - EmojiLayout.getHeaderPadding() + ((xgramPickerRowHeight() - reactionsPickerController.getItemHeight()) / 2));
    if (offset != lastReactionPickerOffsetReal) {
      lastReactionPickerOffsetReal = offset;
      if (reactionsPickerRecyclerView != null) {
        if (!reactionsPickerRecyclerView.isComputingLayout()) {
          reactionsPickerRecyclerView.invalidateItemDecorations();
          checkReactionPickerPosition();
        } else {
          UI.post(() -> {
            reactionsPickerRecyclerView.invalidateItemDecorations();
            checkReactionPickerPosition();
          });
        }
      }
    }
    return offset;
  }

  private static int getReactionPickerOffsetTopDefault () {
    return (Screen.currentHeight() - Screen.dp(56) - HeaderView.getSize(true)) / 2;
  }

  private void checkReactionPickerHeaderTopVisibility () {
    if (reactionsPickerController != null) {
      reactionsPickerController.setTopHeaderVisibility(reactionsPickerVisibility.getValue() && Views.getRecyclerViewElementTop(reactionsPickerRecyclerView, 1) <= HeaderView.getSize(true) + EmojiLayout.getHeaderPadding());
    }
  }

  public static float getReactionsPickerRightHiddenWidth (State state) {
    int buttonsWidth = state.getRightViewsWidth();

    int emojiPickerWidthWithoutPadding = Screen.currentWidth() - Screen.dp(ReactionsPickerController.RECYCLER_VIEW_LEFT_RIGHT_PADDING * 2);
    int emojiPickerSpanCount = EmojiLayoutRecyclerController.calculateSpanCount(emojiPickerWidthWithoutPadding, 9, Screen.dp(38));
    float emojiPickerItemSize = (float) emojiPickerWidthWithoutPadding / emojiPickerSpanCount;
    return (float) Math.ceil(((float) buttonsWidth - Screen.dp(12)) / emojiPickerItemSize)
      * emojiPickerItemSize + Screen.dp(ReactionsPickerController.RECYCLER_VIEW_LEFT_RIGHT_PADDING) + Screen.dp(1);
  }

  private class PickerOpenerScrollListener extends RecyclerView.OnScrollListener {
    private int totalDy;
    private boolean ignore;

    @Override
    public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (ignore || reactionsPickerRecyclerView.isScrollDisabled() || reactionsPickerVisibility.getFloatValue() > 0f) {
        return;
      }

      totalDy += dy;
      if (Math.abs(totalDy) > Screen.dp(30)) {
        UI.post(() -> {
          doNotUpdateScrollReactionPicker = true;
          reset(true);
          recyclerView.stopScroll();
          showReactionPicker();
        });
      }
    }

    @Override
    public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE || reactionsPickerVisibility.getFloatValue() > 0f) {
        return;
      }

      ignore = false;
      if (totalDy != 0) {
        reactionsPickerRecyclerView.smoothScrollBy(0, -totalDy);
        reset(true);
      }
    }

    public void reset (boolean ignoreNextScrolls) {
      ignore = ignoreNextScrolls;
      totalDy = 0;
    }
  }

  /*  */

  public static class State {
    public final Tdlib tdlib;
    public final Options options;
    public final TGMessage message;
    public final TdApi.MessageReactions messageReactions;
    public final TdApi.AvailableReaction[] availableReactions;
    public final Set<String> chosenReactions;
    public final long[] emojiPackIds;
    public final boolean isPremium;

    public final boolean needShowMessageOptions;
    public final boolean needShowMessageViews;
    public final boolean needShowMessageReactionSenders;
    public final boolean needShowReactionsPopupPicker;
    public final boolean needShowCustomEmojiInsidePicker;
    public final boolean hasNonSelectedCustomReactions;

    public final OnReactionClickListener onReactionClickListener;
    public final int headerButtonsVisibleWidth;

    public int headerAlwaysVisibleCountersWidth;

    /** XGram: item widths of the counters shown on the options page (all reactions, views), for their M3E assist chips. */
    public int xgramChipCount;
    public final int[] xgramChipWidths = new int[2];

    // XGram: Messages > Message menu > Reaction bar (com.fahadali.xgram.chats.XgramReactionBar)

    /** XgramSettings.REACTION_BAR_TOP, _BOTTOM or _OFF; Top on a sheet without the options page. */
    public final int xgramReactionBar;
    /** The quick reaction strip sits in the header row on the options page, as in Telegram X (Top). */
    public final boolean xgramReactionsInHeader;
    /** The quick reaction strip is a row of the options list, just above Delete (Bottom). */
    public final boolean xgramReactionsInList;
    /** The popup picker opens from the header row: its expand button there, its collapsed row is the header's (Top). */
    public final boolean xgramPickerInHeader;
    /** The header row would be empty (no reactions, counters or pages in it): the sheet has none (Bottom, Off). */
    public final boolean xgramNoHeaderRow;

    public interface OnReactionClickListener {
      void onReactionClick (View v, TGReaction reaction, boolean isLongClick);
    }

    public State (TGMessage message, Options options, OnReactionClickListener onReactionClickListener) {
      this.message = message;
      this.options = options;
      this.tdlib = message.tdlib();
      this.emojiPackIds = message.getUniqueEmojiPackIdList();
      this.onReactionClickListener = onReactionClickListener;
      this.isPremium = message.tdlib().hasPremium();

      this.messageReactions = message.getMessageReactions().getReactions();
      this.chosenReactions = message.getMessageReactions().getChosen();
      this.availableReactions = message.getMessageAvailableReactions();
      this.needShowMessageViews = !(!message.canGetViewers() || message.isUnread() || message.noUnread());
      this.needShowMessageOptions = options != null;
      // XGram: Reaction bar: the quick reactions at the top (Telegram X), under the options, or off (no popup picker)
      this.xgramReactionBar = com.fahadali.xgram.chats.XgramReactionBar.mode(needShowMessageOptions);
      this.xgramReactionsInHeader = xgramReactionBar == com.fahadali.xgram.XgramSettings.REACTION_BAR_TOP;
      this.xgramReactionsInList = needShowMessageOptions && xgramReactionBar == com.fahadali.xgram.XgramSettings.REACTION_BAR_BOTTOM;

      this.needShowReactionsPopupPicker = needShowMessageOptions && xgramReactionBar != com.fahadali.xgram.XgramSettings.REACTION_BAR_OFF && message.needShowReactionPopupPicker();
      this.xgramPickerInHeader = needShowReactionsPopupPicker && xgramReactionsInHeader;
      this.needShowMessageReactionSenders = !Td.isEmpty(messageReactions) && message.canGetAddedReactions() && message.getMessageReactions().getTotalCount() > 0;
      this.xgramNoHeaderRow = needShowMessageOptions && !xgramReactionsInHeader && !needShowMessageViews && !needShowMessageReactionSenders;

      this.headerButtonsVisibleWidth = xgramPickerInHeader ? Screen.dp(56): 0; // XGram: the expand button is in the header row only at the top
      this.needShowCustomEmojiInsidePicker = isPremium && message.isCustomEmojiReactionsAvailable();

      boolean hasNonSelectedCustomReactions = false;
      if (availableReactions != null) {
        for (TdApi.AvailableReaction reaction : availableReactions) {
          if (reaction.type.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR && !chosenReactions.contains(TD.makeReactionKey(reaction.type))) {
            hasNonSelectedCustomReactions = true;
            break;
          }
        }
      }
      this.hasNonSelectedCustomReactions = hasNonSelectedCustomReactions;
    }

    public int getPagesCount () {
      return (needShowMessageOptions ? 1 : 0)
        + (needShowMessageViews ? 1 : 0)
        + (needShowMessageReactionSenders ? Td.reactionTypesCount(messageReactions) + 1 : 0);
    }

    public int getRightViewsWidth () {
      return headerAlwaysVisibleCountersWidth + headerButtonsVisibleWidth;
    }
  }
}
