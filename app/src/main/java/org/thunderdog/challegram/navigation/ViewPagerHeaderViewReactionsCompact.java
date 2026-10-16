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
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.navigation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3Sheets;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessageOptionsPagerController;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.ReactionsSelectorRecyclerView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
public class ViewPagerHeaderViewReactionsCompact extends FrameLayoutFix implements PagerHeaderView, StretchyHeaderView, ViewPagerTopView.SelectionChangeListener, ThemeInvalidateListener {
  private static class VH extends RecyclerView.ViewHolder {
    public VH (View itemView) {
      super(itemView);
      setIsRecyclable(false);
    }
  }

  @Override
  public void checkRtl () {
    getTopView().checkRtl();
  }

  private static class A extends RecyclerView.Adapter<VH> {
    private final ViewPagerTopView topView;

    public A (ViewPagerTopView topView) {
      this.topView = topView;
    }

    @Override
    @NonNull
    public VH onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (topView.getParent() != null) {
        Log.w("ViewPagerHeaderViewCompact: topView is already attached to another cel");
        ((ViewGroup) topView.getParent()).removeView(topView);
      }
      return new VH(topView);
    }

    @Override
    public void onBindViewHolder (@NonNull VH holder, int position) {
    }

    @Override
    public int getItemCount () {
      return 1;
    }
  }

  private final A adapter;
  private final RecyclerView recyclerView;
  @Nullable
  private final ReactionsSelectorRecyclerView reactionsSelectorRecyclerView;
  private final BackHeaderButton backButton;
  private final @Nullable ImageView moreButton;
  private boolean isScrollEnabled = true;

  private final MessageOptionsPagerController.State state;

  private final boolean needReactionSelector;
  private final boolean needShowReactions, needShowViews;

  // XGram: Material 3 Expressive layout of the options page header row (16dp sheet insets, 8dp between controls):
  // [reaction pill: 40dp targets side by side] [40dp filled tonal expand_more button] [assist chip per counter].
  // The counters are the tab strip's items (their content with 16dp on each side, see MessageOptionsPagerController):
  // their chips are drawn under them here and fade out while the pager moves to the reaction pages, where they are tabs.
  private final boolean xgramM3;
  /** The sheet's side inset, the gap between the controls of the row, and the reaction strip's own pill inset. */
  private static final float XGRAM_INSET = XgramExpressive.LIST_INSET, XGRAM_GAP = 8f, XGRAM_STRIP_PILL_INSET = 4f;
  private float xgramSelection;
  private final RectF xgramRect = new RectF();

  public ViewPagerHeaderViewReactionsCompact (Context context, MessageOptionsPagerController.State state) {
    super(context);
    this.state = state;
    this.xgramM3 = XgramExpressive.isEnabled();

    this.needReactionSelector = state.needShowMessageOptions;
    this.needShowReactions = state.needShowMessageReactionSenders;
    this.needShowViews = state.needShowMessageViews;

    final boolean needShowMoreButton = state.xgramPickerInHeader; // XGram: the reaction bar's expand button is in this row only at the top
    final int rightOffset = state.headerAlwaysVisibleCountersWidth;

    ViewPagerTopView topView = new ViewPagerTopView(context);
    topView.setSelectionColorId(ColorId.headerTabActive);
    topView.setTextFromToColorId(ColorId.headerTabInactiveText, ColorId.headerTabActiveText);
    topView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    topView.setSelectionChangeListener(this);

    adapter = new A(topView);

    if (this.needReactionSelector) {
      reactionsSelectorRecyclerView = new ReactionsSelectorRecyclerView(context, state);
      if (xgramM3) {
        // XGram: the reactions in their own surfaceContainerHigh pill from the 16dp inset to 8dp before the expand button
        // (or the first chip), the 40dp reaction targets side by side. It also stays with the popup picker: the picker's
        // grid only shows once it is expanded.
        reactionsSelectorRecyclerView.setLayoutParams(FrameLayoutFix.newParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          Gravity.CENTER_VERTICAL, Screen.dp(XGRAM_INSET - XGRAM_STRIP_PILL_INSET), 0, xgramStripEndMargin(needShowMoreButton), 0));
        reactionsSelectorRecyclerView.setNeedDrawBorderGradient(true);
        reactionsSelectorRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
          @Override
          public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State rvState) {
            // cancels the strip's own -1dp overlap of its 40dp reaction views: a 40dp pitch, no overlapping state layers
            outRect.set(Screen.dp(1f), 0, Screen.dp(1f), 0);
          }
        });
        addView(reactionsSelectorRecyclerView);
      } else {
        final int rightOffsetR = state.headerAlwaysVisibleCountersWidth + Screen.dp(needShowMoreButton ? 56: 0);
        reactionsSelectorRecyclerView.setLayoutParams(FrameLayoutFix.newParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          Gravity.CENTER_VERTICAL, 0, 0, rightOffsetR, 0));
        reactionsSelectorRecyclerView.setNeedDrawBorderGradient(rightOffset > 0);
        addView(reactionsSelectorRecyclerView);
        if (state.needShowReactionsPopupPicker) {
          reactionsSelectorRecyclerView.setVisibility(GONE);
        }
      }
      if (!state.xgramReactionsInHeader) {
        // XGram: Reaction bar at the bottom (a row of the options list) or off: this row keeps only the counters,
        // from the 16dp inset (see xgramChipsOrigin and onPageScrolled)
        reactionsSelectorRecyclerView.setVisibility(GONE);
      }
    } else {
      reactionsSelectorRecyclerView = null;
    }

    backButton = new BackHeaderButton(context);
    backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
    backButton.setButtonFactor(this.needReactionSelector ? BackHeaderButton.TYPE_BACK : BackHeaderButton.TYPE_CLOSE);
    if (xgramM3) {
      backButton.setButtonBackground(R.drawable.bg_btn_header); // XGram: HeaderButton's M3E standard icon button state layer (40dp)
    } else {
      RippleSupport.setTransparentSelector(backButton);
    }
    Views.setClickable(backButton);
    addView(backButton);

    if (needShowMoreButton) {
      moreButton = new ImageView(context);
      if (xgramM3) {
        // XGram: Material 3 Expressive small filled tonal icon button: 40dp secondaryContainer circle (8dp while
        // pressed) in a 48dp target, the 24dp expand_more icon in onSecondaryContainer
        moreButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(XgramM3Sheets.TOUCH_TARGET), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        moreButton.setImageResource(R.drawable.xgram_w2_menu_sheets_expand_more_24);
        moreButton.setScaleType(ImageView.ScaleType.CENTER);
        moreButton.setColorFilter(Paints.getColorFilter(XgramM3Sheets.tonalContentColor()));
        moreButton.setContentDescription(Lang.getString(R.string.XgramM3W2MoreReactions));
        XgramM3Sheets.attachTonalIconButton(moreButton);
      } else {
        moreButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        moreButton.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_small_arrow_down_24));
        moreButton.setScaleType(ImageView.ScaleType.CENTER);
        moreButton.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
        RippleSupport.setTransparentSelector(moreButton);
      }
      Views.setClickable(moreButton);
      addView(moreButton);
    } else {
      moreButton = null;
    }

    setBackButtonAlpha(this.needReactionSelector ? 0f: 1f);

    recyclerView = new RecyclerView(context) {
      @Override
      protected void dispatchDraw (Canvas c) {
        super.dispatchDraw(c);
        if (shadowPaint1 == null) {
          return;
        }
        int width = getMeasuredWidth();
        float translationX = getTranslationX();
        int alpha1 = (int) ((1f - MathUtils.clamp(translationX / (width / 2f))) * 255);
        int alpha2 = (int) (MathUtils.clamp((float) computeHorizontalScrollOffset() / Screen.dp(20f)) * 255);

        shadowPaint1.setAlpha(Math.min(alpha1, alpha2));
        c.drawRect(0, 0, shadowSize, Screen.dp(52), shadowPaint1);
        shadowPaint1.setAlpha(255);
      }

      @Override
      public void onScrolled (int dx, int dy) {
        invalidate();
      }
    };
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
      Gravity.TOP, Screen.dp(56), 0, 0, 0));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
      @Override
      protected boolean isLayoutRTL () {
        return false;
      }

      @Override
      public boolean canScrollHorizontally () {
        return isScrollEnabled && super.canScrollHorizontally();
      }
    });
    recyclerView.setAdapter(adapter);
    addView(recyclerView);
    if (xgramM3 && moreButton != null) {
      moreButton.bringToFront(); // XGram: its 48dp target overlaps the strip's start by 4dp: the button keeps those touches
    }

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54)));
    updatePaints(Theme.backgroundColor());
  }

  private float getReactionPickerHiddenZoneLeft () {
    final int x = (int) (reactionsSelectorRecyclerView != null ? reactionsSelectorRecyclerView.getTranslationX() : 0);
    final float width = MessageOptionsPagerController.getReactionsPickerRightHiddenWidth(state);
    return getMeasuredWidth() - width + x;
  }

  // XGram: geometry of the Expressive options page row (see the field comment)

  /**
   * Where the tab strip starts on the options page: its counters (whose chips span from 8dp after this point to the end
   * of their items) end at the 16dp inset. The expand button ends here, 8dp before the first chip.
   */
  private int xgramChipsOrigin (int width) {
    if (!state.xgramReactionsInHeader) {
      // the reaction bar is at the bottom or off: the counters alone, their first chip at the 16dp inset
      return Screen.dp(XGRAM_INSET - XgramM3Sheets.CHIP_LEADING_ICON_SPACE);
    }
    return width - Screen.dp(XGRAM_INSET) - state.headerAlwaysVisibleCountersWidth;
  }

  /** End margin of the reaction strip, so its pill ends 8dp before the expand button (or the first chip, or the inset). */
  private int xgramStripEndMargin (boolean needShowMoreButton) {
    return Screen.dp(XGRAM_INSET) + state.headerAlwaysVisibleCountersWidth
      + (needShowMoreButton ? Screen.dp(XgramM3Sheets.ICON_BUTTON_SIZE + XGRAM_GAP) : 0)
      - Screen.dp(XGRAM_STRIP_PILL_INSET);
  }

  private void xgramLayoutForPage (int width, int position, float positionOffset) {
    if (position == 0) {
      final float remaining = 1f - positionOffset;
      final int origin = xgramChipsOrigin(width);
      final float offset = (origin - Screen.dp(56)) * remaining;
      recyclerView.setTranslationX(offset);
      if (reactionsSelectorRecyclerView != null) {
        reactionsSelectorRecyclerView.setTranslationX(-width * positionOffset);
      }
      backButton.setTranslationX(offset);
      if (moreButton != null) {
        // the 40dp circle ends at the origin, centered in the 48dp view
        moreButton.setTranslationX((origin - Screen.dp((XgramM3Sheets.ICON_BUTTON_SIZE + XgramM3Sheets.TOUCH_TARGET) / 2f)) * remaining);
      }
      setBackButtonAlpha(positionOffset);
      isScrollEnabled = false;
    } else {
      recyclerView.setTranslationX(0);
      if (reactionsSelectorRecyclerView != null) {
        reactionsSelectorRecyclerView.setTranslationX(-width);
      }
      backButton.setTranslationX(0);
      if (moreButton != null) {
        moreButton.setTranslationX(0);
      }
      setBackButtonAlpha(1f);
      isScrollEnabled = true;
    }
  }

  private int xgramLastPosition;
  private float xgramLastPositionOffset;

  /**
   * The counters' width changed (the views count arrived, see MessageOptionsPagerController.xgramMeasureSeenChip): the
   * reaction strip's end, the expand button and the chips move to the new origin.
   */
  public void xgramOnCountersWidthChanged () {
    if (!xgramM3) {
      return;
    }
    if (reactionsSelectorRecyclerView != null) {
      ViewGroup.LayoutParams params = reactionsSelectorRecyclerView.getLayoutParams();
      if (params instanceof MarginLayoutParams) {
        final int endMargin = xgramStripEndMargin(state.xgramPickerInHeader);
        if (((MarginLayoutParams) params).rightMargin != endMargin) {
          ((MarginLayoutParams) params).rightMargin = endMargin;
          reactionsSelectorRecyclerView.setLayoutParams(params);
        }
      }
    }
    final int width = getMeasuredWidth();
    if (needReactionSelector && reactionsSelectorRecyclerView != null && width > 0) {
      xgramLayoutForPage(width, xgramLastPosition, xgramLastPositionOffset);
    }
    recyclerView.invalidate();
    invalidate();
  }

  /** The assist chips of the counters on the options page: 32dp, centered, 8dp leading space, 16dp trailing space. */
  private void xgramDrawChips (Canvas c) {
    if (!state.needShowMessageOptions || state.xgramChipCount == 0) {
      return;
    }
    final float alpha = MathUtils.clamp(1f - xgramSelection) * recyclerView.getAlpha();
    if (alpha <= 0f) {
      return;
    }
    final View strip = recyclerView.getChildCount() > 0 ? recyclerView.getChildAt(0) : null;
    float x = recyclerView.getLeft() + recyclerView.getTranslationX() + (strip != null ? strip.getLeft() : 0);
    final float height = Screen.dp(XgramM3Sheets.CHIP_HEIGHT);
    final float top = recyclerView.getTop() + recyclerView.getTranslationY() + (recyclerView.getMeasuredHeight() - height) / 2f;
    // the tab strip's first item (the options page) is empty: the counters start at its origin
    for (int i = 0; i < state.xgramChipCount; i++) {
      final int itemWidth = state.xgramChipWidths[i];
      xgramRect.set(x + Screen.dp(XgramM3Sheets.CHIP_LEADING_ICON_SPACE), top, x + itemWidth, top + height);
      XgramM3Sheets.drawAssistChipOutline(c, xgramRect, alpha);
      x += itemWidth;
    }
  }

  // XGram: with the popup picker's grid hidden until expanded, a swipe up on the reaction strip expands it (the picker
  // did this for drags on its first row)

  private @Nullable Runnable xgramExpandRequest;
  private boolean xgramExpandTracking, xgramExpandConsumed;
  private float xgramDownX, xgramDownY;

  public void xgramSetOnExpandRequest (@Nullable Runnable request) {
    this.xgramExpandRequest = request;
  }

  private boolean xgramTrackExpand (MotionEvent e) {
    if (!xgramM3 || xgramExpandRequest == null || !state.xgramPickerInHeader || xgramSelection != 0f || reactionsSelectorRecyclerView == null) {
      xgramExpandTracking = false;
      return false;
    }
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: {
        xgramDownX = e.getX();
        xgramDownY = e.getY();
        xgramExpandTracking = e.getX() < reactionsSelectorRecyclerView.getRight() + reactionsSelectorRecyclerView.getTranslationX();
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (xgramExpandTracking) {
          final float dx = e.getX() - xgramDownX;
          final float dy = e.getY() - xgramDownY;
          final float slop = Screen.getTouchSlop();
          if (Math.abs(dx) > slop && Math.abs(dx) >= Math.abs(dy)) {
            xgramExpandTracking = false;
          } else if (-dy > slop && -dy > Math.abs(dx)) {
            xgramExpandTracking = false;
            xgramExpandRequest.run();
            return true;
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        xgramExpandTracking = false;
        break;
      }
    }
    return false;
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    if (xgramM3) {
      xgramDrawChips(c);
    } else if (state.xgramPickerInHeader) {
      c.drawRect(getReactionPickerHiddenZoneLeft(), 0, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.backgroundColor()));
    }
    super.dispatchDraw(c);
  }

  LinearGradient shader1;
  private Paint shadowPaint1;
  private final int shadowSize = Screen.dp(35);

  private int oldPaintsColor = 0;

  public void updatePaints (int color) {
    if (oldPaintsColor != color) {
      oldPaintsColor = color;
    } else return;

    shader1 = new LinearGradient(0, 0, shadowSize / 2f, 0, color, 0, Shader.TileMode.CLAMP);
    if (shadowPaint1 == null) {
      shadowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint1.setShader(shader1);
    if (reactionsSelectorRecyclerView != null) {
      reactionsSelectorRecyclerView.invalidate();
    }
    recyclerView.invalidate();
    invalidate();
  }

  public void onPageScrolled (int position, float positionOffset) {
    adapter.topView.setSelectionFactor((float) position + positionOffset);
    xgramSelection = (float) position + positionOffset;
    xgramLastPosition = position;
    xgramLastPositionOffset = positionOffset;

    if (needReactionSelector && reactionsSelectorRecyclerView != null) {
      int width = getMeasuredWidth();
      if (xgramM3) {
        xgramLayoutForPage(width, position, positionOffset);
      } else if (position == 0) {
        // XGram: with the reaction bar at the bottom or off the counters start at the 16dp inset instead of ending at the edge
        final int countersStart = state.xgramReactionsInHeader ? width - state.headerAlwaysVisibleCountersWidth : Screen.dp(16f);
        float offset = (countersStart - Screen.dp(56)) * (1f - positionOffset);
        if (needShowReactions && needShowViews) {
          getTopView().setItemTranslationX(1, (int) (Screen.dp(-8) * (1f - positionOffset)));
          getTopView().setItemTranslationX(2, (int) (Screen.dp(-8) * (1f - positionOffset)));
        } else if (needShowReactions || needShowViews) {
          getTopView().setItemTranslationX(1, (int) (Screen.dp(-8) * (1f - positionOffset)));
        }
        recyclerView.setTranslationX(offset);
        reactionsSelectorRecyclerView.setTranslationX(-width * positionOffset);
        backButton.setTranslationX(offset);
        if (moreButton != null) {
          moreButton.setTranslationX(offset);
        }
        setBackButtonAlpha(positionOffset);
        isScrollEnabled = false;
      } else {
        getTopView().setItemTranslationX(1, 0);
        getTopView().setItemTranslationX(2, 0);
        recyclerView.setTranslationX(0);
        reactionsSelectorRecyclerView.setTranslationX(-width);
        backButton.setTranslationX(0);
        if (moreButton != null) {
          moreButton.setTranslationX(0);
        }
        setBackButtonAlpha(1f);
        isScrollEnabled = true;
      }
      recyclerView.invalidate();
      invalidate();
    }
  }

  float backButtonAlpha = 0.5f;

  private void setBackButtonAlpha (float alpha) {
    if (alpha != backButtonAlpha) {
      backButtonAlpha = alpha;
      backButton.setAlpha(alpha);
      if (alpha > 0f && backButton.getVisibility() != VISIBLE) {
        backButton.setVisibility(View.VISIBLE);
      }
      if (alpha == 0f && backButton.getVisibility() != GONE) {
        backButton.setVisibility(View.GONE);
      }

      if (moreButton != null) {
        final float alphaMore = 1f - alpha;
        moreButton.setAlpha(alphaMore);
        if (alphaMore > 0f && moreButton.getVisibility() != VISIBLE) {
          moreButton.setVisibility(View.VISIBLE);
        }
        if (alphaMore == 0f && moreButton.getVisibility() != GONE) {
          moreButton.setVisibility(View.GONE);
        }
      }
    }
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    if (xgramM3 && moreButton != null) {
      moreButton.setColorFilter(Paints.getColorFilter(XgramM3Sheets.tonalContentColor())); // XGram: theme changes
    }
    if (reactionsSelectorRecyclerView != null) {
      reactionsSelectorRecyclerView.invalidate();
    }
    recyclerView.invalidate();
    invalidate();
  }

  @Override
  public void onSelectionChanged (int selectionLeft, int selectionWidth, int firstItemWidth, int lastItemWidth, float totalFactor, boolean animated) {
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    if (view == null) {
      return;
    }
    final int viewWidth = view.getMeasuredWidth();
    final int parentWidth = recyclerView.getMeasuredWidth();
    if (viewWidth <= parentWidth) {
      return;
    }
    if (recyclerView.isComputingLayout()) {
      return;
    }

    final int availScrollX = viewWidth - parentWidth;
    final int scrolledX;
    scrolledX = -view.getLeft();
    int viewX = -scrolledX;

    if ((getParent() != null && ((View) getParent()).getMeasuredWidth() > getMeasuredWidth()) || (viewWidth - parentWidth) < lastItemWidth / 2) {
      int desiredViewLeft = (int) ((float) -(viewWidth - parentWidth) * totalFactor);
      if (viewX != desiredViewLeft) {
        recyclerView.stopScroll();
        int diff = (desiredViewLeft - viewX) * -1;
        if (animated) {
          recyclerView.smoothScrollBy(diff, 0);
        } else {
          recyclerView.scrollBy(diff, 0);
        }
      }
    } else {
      int visibleSelectionX = selectionLeft + viewX;
      int desiredSelectionX = (int) ((float) Screen.dp(16f) * (selectionLeft >= selectionWidth ? 1f : (float) selectionLeft / (float) selectionWidth));

      if (visibleSelectionX != desiredSelectionX) {
        int newViewX = viewX + (desiredSelectionX - visibleSelectionX);
        int maxX = parentWidth - viewWidth;
        if (newViewX < maxX) {
          newViewX = maxX;
        }
        if (newViewX != viewX) {
          recyclerView.stopScroll();
          int offset = (viewX - newViewX);
          if (animated) {
            recyclerView.smoothScrollBy(offset, 0);
          } else {
            recyclerView.scrollBy(offset, 0);
          }
        }
      }
    }/* else {
      int visibleSelectionLeft = selectionLeft + viewOffset;

      int desiredLeft = (int) ((float) Screen.dp(16f) * (selectionLeft >= selectionWidth ? 1f : (float) selectionLeft / (float) selectionWidth));

      if (visibleSelectionLeft != desiredLeft) {
        int newViewLeft = viewOffset + (desiredLeft - visibleSelectionLeft);

        int maxLeft = parentWidth - viewWidth;
        if (newViewLeft < maxLeft) {
          newViewLeft = maxLeft;
        }


        if (newViewLeft != viewOffset) {
          recyclerView.stopScroll();
          int offset = viewOffset - newViewLeft;
          if (animated) {
            recyclerView.smoothScrollBy(offset, 0);
          } else {
            recyclerView.scrollBy(offset, 0);
          }
        }
      }
    }*/
  }

  public boolean canScrollLeft () {
    int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != 0) {
      return true;
    }
    View view = recyclerView.getLayoutManager().findViewByPosition(0);
    return view == null || view.getLeft() < 0;
  }

  public RecyclerView getRecyclerView () {
    return recyclerView;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (xgramExpandConsumed) {
      // XGram: the rest of the swipe that expanded the picker
      final int action = e.getActionMasked();
      if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        xgramExpandConsumed = false;
      }
      return true;
    }
    if (xgramM3) {
      // XGram: no hidden picker zone to guard: the collapsed picker grid takes no touches
      return !(e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) && super.onTouchEvent(e);
    }
    return !(e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) && super.onTouchEvent(e)
      || (state.xgramPickerInHeader && (e.getX() > getReactionPickerHiddenZoneLeft()));
  }

  private boolean canTouchAt (float x, float y) {
    y -= recyclerView.getTop() + (int) recyclerView.getTranslationY();
    return y >= 0 && y < adapter.topView.getMeasuredHeight();
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (xgramTrackExpand(e)) {
      xgramExpandConsumed = true;
      return true;
    }
    return (e.getAction() == MotionEvent.ACTION_DOWN && !canTouchAt(e.getX(), e.getY())) || super.onInterceptTouchEvent(e);
  }

  @Override
  public View getView () {
    return this;
  }

  @Override
  public ViewPagerTopView getTopView () {
    return adapter.topView;
  }

  public BackHeaderButton getBackButton () {
    return backButton;
  }

  @Nullable public ImageView getMoreButton () {
    return moreButton;
  }

  private static final float TOP_SCALE_LIMIT = .25f;

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    //final float totalScale = (float) Size.HEADER_DRAWER_SIZE / (float) Size.HEADER_SIZE_DIFFERENCE;
    //scaleFactor = scaleFactor / totalScale;
    scaleFactor = 1f - scaleFactor;

    //noinspection Range
    recyclerView.setAlpha(scaleFactor <= TOP_SCALE_LIMIT ? 0f : (scaleFactor - TOP_SCALE_LIMIT) / TOP_SCALE_LIMIT);
    recyclerView.setTranslationY(Size.getHeaderSizeDifference(true) * (1f - scaleFactor));
  }
}
