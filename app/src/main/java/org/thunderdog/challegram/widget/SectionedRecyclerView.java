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
 * File created on 04/02/2016 at 15:47
 */
package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;

public class SectionedRecyclerView extends RecyclerView implements Runnable {
  private LinearLayoutManager manager;
  private SectionedAdapter adapter;

  private final Paint letterPaint, bubbleLetterPaint;
  private int textLeft;
  private int textOffset;

  public SectionedRecyclerView (Context context) {
    super(context);
    setLayoutManager(manager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        scrollY += dy;
        layoutSectionStuff();
        if (xgram && dy != 0) {
          xgramShow();
        }
      }
    });
    letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    letterPaint.setTypeface(Fonts.getRobotoBold());
    letterPaint.setTextSize(Screen.dp(20f));

    bubbleLetterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    bubbleLetterPaint.setColor(Theme.headerTextColor());
    bubbleLetterPaint.setTypeface(Fonts.getRobotoMedium());
    bubbleLetterPaint.setTextSize(Screen.dp(32f));

    textLeft = Screen.dp(25f);
    textOffset = Screen.dp(7f);

    if (xgram) {
      Runnable invalidate = this::invalidate;
      xgramAlpha = new com.fahadali.xgram.appearance.XgramScrollIndicator.Anim(0f, .002f, invalidate);
      xgramPress = new com.fahadali.xgram.appearance.XgramScrollIndicator.Anim(0f, .002f, invalidate);
      xgramLabelAlpha = new com.fahadali.xgram.appearance.XgramScrollIndicator.Anim(0f, .002f, invalidate);
      xgramLabelScale = new com.fahadali.xgram.appearance.XgramScrollIndicator.Anim(1f, .002f, invalidate);
    }
  }

  // XGram: with Expressive design the fast scroller looks and moves like XgramScrollIndicator: a thumb that fades in
  // while scrolling and out 1.2s after, widens and turns primary when grabbed, with the letter in a label pill.
  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private com.fahadali.xgram.appearance.XgramScrollIndicator.Anim xgramAlpha, xgramPress, xgramLabelAlpha, xgramLabelScale;
  private final com.fahadali.xgram.appearance.XgramScrollIndicator.Exclusion xgramExclusion = new com.fahadali.xgram.appearance.XgramScrollIndicator.Exclusion();
  private final RectF xgramThumb = new RectF();
  private String xgramLabel;
  private final Runnable xgramHide = () -> {
    if (!this.caughtScrollbar && this.xgramAlpha != null) {
      this.xgramAlpha.animateTo(0f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_SLOW);
    }
  };

  private void xgramShow () {
    removeCallbacks(xgramHide);
    xgramAlpha.animateTo(1f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST);
    if (getScrollState() == SCROLL_STATE_IDLE && !caughtScrollbar) {
      postDelayed(xgramHide, 1200L);
    }
  }

  @Override
  public void onScrollStateChanged (int state) {
    super.onScrollStateChanged(state);
    if (xgram && state == SCROLL_STATE_IDLE && !caughtScrollbar && xgramAlpha.getTarget() > 0f) {
      removeCallbacks(xgramHide);
      postDelayed(xgramHide, 1200L);
      invalidate(); // settles the back gesture exclusion on the resting thumb
    }
  }

  private void xgramCheckLabel () {
    String key = visibleSections > 0 ? largeKeys[0] : null;
    if (key != null && !key.equals(xgramLabel)) {
      if (xgramLabel != null && caughtScrollbar) {
        com.fahadali.xgram.appearance.XgramScrollIndicator.labelTick(this);
      }
      xgramLabel = key;
    }
  }

  private void xgramDrawScrollbar (Canvas c) {
    float alpha = xgramAlpha.getValue();
    if (!scrollbarEnabled || scrollbarSpace <= 0 || alpha <= 0f) {
      xgramExclusion.clear(this);
      return;
    }
    boolean rtl = Lang.rtl();
    int width = getMeasuredWidth();
    float press = xgramPress.getValue();
    com.fahadali.xgram.appearance.XgramScrollIndicator.layoutThumb(xgramThumb, scrollbarRect.top, scrollbarRect.bottom, press, rtl, width);
    com.fahadali.xgram.appearance.XgramScrollIndicator.drawThumb(c, xgramThumb, Math.max(0f, Math.min(1f, press)), Math.min(1f, alpha));
    float labelAlpha = xgramLabelAlpha.getValue();
    String key = visibleSections > 0 ? largeKeys[0] : null;
    if (labelAlpha > 0f && key != null && key.length() > 0) {
      float cy = com.fahadali.xgram.appearance.XgramScrollIndicator.labelCenterY(xgramThumb, 0, getScrollBarViewportHeight());
      float scale = com.fahadali.xgram.appearance.XgramMotion.reduce() ? 1f : xgramLabelScale.getValue();
      com.fahadali.xgram.appearance.XgramScrollIndicator.drawLabel(c, com.fahadali.xgram.appearance.XgramScrollIndicator.labelAnchorX(xgramThumb, rtl), cy, key, Math.min(1f, labelAlpha) * Math.min(1f, alpha), scale, com.fahadali.xgram.appearance.XgramScrollIndicator.labelWidth(key), rtl);
    }
    if (xgramAlpha.getTarget() <= 0f) {
      xgramExclusion.clear(this);
    } else if (!caughtScrollbar) {
      xgramExclusion.update(this, xgramThumb, rtl, width, getScrollState() != SCROLL_STATE_IDLE);
    }
  }

  private boolean showSections = true;

  public void setHideSections () {
    showSections = false;
  }

  public void setSectionedAdapter (SectionedAdapter adapter) {
    this.adapter = adapter;
    this.itemHeight = adapter.getItemHeight();
    setAdapter(adapter);
  }

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    if (adapter != null && adapter.isReady()) {
      post(this);
    }
  }

  @Override
  public void run () {
    layoutSectionStuff();
    invalidate();
  }

  // Sections stuff

  public void layoutSectionStuff () {
    layoutSections();
    layoutScrollbar();
  }

  private int itemHeight;
  private int visibleSections;
  private char[] keys = new char[5];
  private int[] pos = new int[5];
  private String[] largeKeys = new String[5];
  private float[] widths = new float[5];
  private float topFactor;

  private void layoutSections () {
    if (adapter == null || adapter.getSectionCount() == 0) {
      visibleSections = 0;
      return;
    }

    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int lastVisiblePosition = manager.findLastVisibleItemPosition();

    int i = firstVisiblePosition;
    int firstVisibleSection;
    while ((firstVisibleSection = adapter.getSectionForPosition(i)) == -1 && i <= lastVisiblePosition) {
      i++;
    }
    i = lastVisiblePosition;
    int lastVisibleSection;
    while ((lastVisibleSection = adapter.getSectionForPosition(i)) == -1 && i >= firstVisiblePosition) {
      i--;
    }

    if (firstVisibleSection == -1 || lastVisibleSection == -1) {
      visibleSections = 0;
      return;
    }

    visibleSections = lastVisibleSection - firstVisibleSection + 1;

    if (largeKeys.length < visibleSections) {
      largeKeys = new String[visibleSections];
      pos = new int[visibleSections];
      widths = new float[visibleSections];
    }

    for (i = 0; i < visibleSections; i++) {
      largeKeys[i] = adapter.getSectionName(firstVisibleSection + i);
      widths[i] = U.measureText(largeKeys[i], letterPaint);

      Section section = adapter.sections[firstVisibleSection + i];
      View v = manager.findViewByPosition(section.startIndex == 0 || !adapter.needSeparators ? section.startIndex : section.startIndex + 1);

      if (i == 0) {
        int y = (int) ((float) itemHeight * .5f);
        int minY = v == null ? 0 : v.getTop() + y;

        if (y < minY || (adapter.needSeparators && section.count == 2) || (!adapter.needSeparators && section.count == 1)) {
          pos[i] = minY;
          topFactor = 1f;
          continue;
        }

        v = manager.findViewByPosition(section.startIndex + section.count - 1);

        if (v == null) {
          pos[i] = y;
          topFactor = 1f;
          continue;
        }

        int maxY = v.getTop() + y;

        if (y < maxY) {
          pos[i] = y;
          topFactor = 1f;
        } else if ((adapter.needSeparators && section.count == 2) || (!adapter.needSeparators && section.count == 1)) {
          pos[i] = maxY;
          topFactor = 1f;
        } else {
          pos[i] = maxY;
          topFactor = 1f - ((float) Math.abs(maxY - y) / (float) itemHeight);
        }
      } else {
        pos[i] = v == null ? -1 : v.getTop() + (int) ((float) itemHeight * .5f);
      }
    }
  }

  // Scrollbar stuff

  private boolean scrollbarEnabled;
  private int scrollbarSpace, scrollbarHeight;

  private final RectF scrollbarRect = new RectF();
  private final int scrollbarRadius = Screen.dp(2f);
  private final int scrollbarWidth = scrollbarRadius + scrollbarRadius;
  private final int scrollbarPadding = Screen.dp(9f);
  private final int scrollbarMinHeight = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? 48f : 32f);

  private int getScrollBarViewportHeight () {
    return getMeasuredHeight() - getPaddingBottom();
  }

  private void layoutScrollbar () {
    if (adapter == null || adapter.getSectionCount() == 0) {
      scrollbarEnabled = false;
      return;
    }

    final int totalHeight = getScrollBarViewportHeight();
    if (totalHeight <= 0) {
      scrollbarEnabled = false;
      return;
    }

    int topItem = manager.findFirstCompletelyVisibleItemPosition();
    int bottomItem = manager.findLastCompletelyVisibleItemPosition();

    if (topItem == 0 && bottomItem == adapter.getItemCount() - 1) {
      scrollbarEnabled = false;
      return;
    }

    scrollbarEnabled = true;

    final int totalWidth = getMeasuredWidth();
    final float factor = calculateScrollbarFactor();

    final int maxScrollbarHeight = totalHeight - scrollbarPadding - scrollbarPadding;
    scrollbarHeight = Math.max((int) ((float) maxScrollbarHeight * Math.min(1f, ((float) totalHeight / (float) totalScrollY))), scrollbarMinHeight);

    scrollbarSpace = maxScrollbarHeight - scrollbarHeight;
    scrollbarRect.top = scrollbarPadding + (int) ((float) scrollbarSpace * factor);
    scrollbarRect.bottom = scrollbarRect.top + scrollbarHeight;
    scrollbarRect.right = Lang.rtl() ? scrollbarPadding + scrollbarWidth : totalWidth - scrollbarPadding;
    scrollbarRect.left = scrollbarRect.right - scrollbarWidth;
  }

  private int scrollY;
  private int totalScrollY;
  private int scrollCount;

  private boolean scrollbarInvalidated;

  public void invalidateScrollbarFactor () {
    scrollbarInvalidated = true;
  }

  private float calculateScrollbarFactor () {
    if (manager.findFirstVisibleItemPosition() == 0) {
      View view = manager.findViewByPosition(0);
      if (view != null) {
        scrollY = -view.getTop();
      } else {
        scrollY = 0;
      }
    }

    final int itemCount = adapter.getItemCount();
    if (itemCount != scrollCount || scrollbarInvalidated) {
      scrollbarInvalidated = false;
      final int separatorHeight = adapter.getSeparatorHeight();
      scrollCount = itemCount;
      totalScrollY = 0;
      for (int i = 0, j = 0; i < itemCount; i++) {
        if (adapter.isSeparator(i)) {
          totalScrollY += separatorHeight;
        } else {
          totalScrollY += adapter.getItemHeight(j);
          j++;
        }
      }
    }

    return (float) scrollY / (float) (totalScrollY - getScrollBarViewportHeight());
  }

  private final RectF bubbleRect = new RectF();
  private final int bubbleRadius = Screen.dp(44f);
  private final int bubbleTextOffset = Screen.dp(13f);
  private final int bubbleMinRadius = Screen.dp(3f);

  private float bubbleFactor;
  private char lastBubble;
  private String lastBigBubble;
  private int bubbleTextWidth;
  private boolean caughtScrollbar;

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      return checkScrollbar(e.getX(), e.getY()) || super.onInterceptTouchEvent(e);
    }
    return caughtScrollbar || super.onInterceptTouchEvent(e);
  }

  private boolean checkScrollbar (float x, float y) {
    if (xgram) {
      return visibleSections > 0 && scrollbarEnabled && xgramAlpha.getValue() >= .5f && com.fahadali.xgram.appearance.XgramScrollIndicator.hitTest(x, y, scrollbarRect.top, scrollbarRect.bottom, Lang.rtl(), getMeasuredWidth());
    }
    return visibleSections > 0 && scrollbarEnabled && x >= scrollbarRect.left - scrollbarPadding && x <= scrollbarRect.right + scrollbarPadding && y >= scrollbarRect.top - scrollbarPadding && y <= scrollbarRect.bottom + scrollbarPadding;
  }

  public void setFactor (float factor) {
    if (this.bubbleFactor != factor) {
      this.bubbleFactor = factor;
      // int color = bubbleChanger.getColor(factor);
      // scrollbarPaint.setColor(scrollbarChanger.getColor(factor));
      // bubblePaint.setColor(color);
      // bubbleLetterPaint.setAlpha((int) (255f * bubbleFactor));
      invalidate();
    }
  }

  public float getFactor () {
    return bubbleFactor;
  }

  private static final long DURATION = 150l;

  private void openScrollbar () {
    if (xgram) {
      removeCallbacks(xgramHide);
      xgramAlpha.animateTo(1f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST);
      xgramPress.animateTo(1f, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST);
      if (xgramLabelAlpha.getValue() <= 0f) {
        xgramLabelScale.setValue(.6f);
      }
      xgramLabelAlpha.animateTo(1f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST);
      xgramLabelScale.animateTo(1f, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST);
      xgramLabel = visibleSections > 0 ? largeKeys[0] : null;
      android.view.ViewParent parent = getParent();
      if (parent != null) {
        parent.requestDisallowInterceptTouchEvent(true);
      }
      performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
      stopScroll();
      return;
    }
    ValueAnimator obj;
    final float startFactor = getFactor();
    final float diffFactor = 1f - startFactor;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    obj.setDuration(DURATION);
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.start();
    stopScroll();
  }

  private void closeScrollbar (boolean animated) {
    if (xgram) {
      if (animated) {
        xgramPress.animateTo(0f, com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST);
        xgramLabelAlpha.animateTo(0f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST);
        xgramLabelScale.animateTo(.8f, com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST);
      } else {
        xgramPress.setValue(0f);
        xgramLabelAlpha.setValue(0f);
        invalidate();
      }
      removeCallbacks(xgramHide);
      postDelayed(xgramHide, 1200L);
      return;
    }
    if (animated) {
      ValueAnimator obj;
      obj = AnimatorUtils.simpleValueAnimator();
      final float startFactor = getFactor();
      obj.addUpdateListener(animation -> setFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
      obj.setDuration(DURATION);
      obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      obj.start();
    } else {
      setFactor(0f);
    }
  }

  private float lastScrollY;
  private void moveScrollbar (float y) {
    float dy = y - lastScrollY;
    lastScrollY = y;
    float scale = (float) totalScrollY / (float) getScrollBarViewportHeight();
    int by = (int) (dy * scale);
    if (by != 0) {
      scrollBy(0, by);
      if (xgram) {
        xgramCheckLabel();
      }
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (caughtScrollbar = checkScrollbar(e.getX(), lastScrollY = e.getY())) {
          openScrollbar();
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caughtScrollbar) {
          moveScrollbar(e.getY());
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (caughtScrollbar) {
          closeScrollbar(true);
          caughtScrollbar = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (caughtScrollbar) {
          closeScrollbar(false);
          caughtScrollbar = false;
          return true;
        }
        break;
      }
    }
    return caughtScrollbar || super.onTouchEvent(e);
  }

  @Override
  public void draw (Canvas c) {
    super.draw(c);

    // Sections drawing
    if (showSections) {
      letterPaint.setColor(Theme.textDecentColor());
      boolean rtl = Lang.rtl();
      int viewWidth = getMeasuredWidth();
      if (topFactor != 1f && visibleSections > 0) {
        letterPaint.setAlpha((int) (255f * topFactor));
        c.drawText(largeKeys[0], rtl ? viewWidth - textLeft - widths[0] : textLeft, pos[0] + textOffset, letterPaint);
        letterPaint.setAlpha(255);
        for (int i = 1; i < visibleSections; i++) {
          c.drawText(largeKeys[i], rtl ? viewWidth - textLeft - widths[i] : textLeft, pos[i] + textOffset, letterPaint);
        }
      } else {
        for (int i = 0; i < visibleSections; i++) {
          c.drawText(largeKeys[i], rtl ? viewWidth - textLeft - widths[i] : textLeft, pos[i] + textOffset, letterPaint);
        }
      }
    }

    // Scrollbar drawing
    if (xgram) {
      xgramDrawScrollbar(c);
      return;
    }
    if (scrollbarEnabled && scrollbarSpace > 0) {
      int inactiveColor = Theme.getColor(ColorId.sectionedScrollBar);
      int activeColor = ColorUtils.compositeColor(Theme.headerColor(), Theme.getColor(ColorId.sectionedScrollBarActive));
      int activeTextColor = ColorUtils.compositeColor(Theme.headerTextColor(), Theme.getColor(ColorId.sectionedScrollBarActiveContent));

      c.drawRoundRect(scrollbarRect, scrollbarRadius, scrollbarRadius, Paints.fillingPaint(ColorUtils.fromToArgb(inactiveColor, activeColor, bubbleFactor)));

      if (visibleSections <= 0 || bubbleFactor <= 0f || (largeKeys[0] == null || largeKeys[0].length() == 0)) {
        return;
      }

      if (lastBigBubble == null || !lastBigBubble.equals(largeKeys[0])) {
        lastBigBubble = largeKeys[0];
        bubbleTextWidth = (int) U.measureText(lastBigBubble, bubbleLetterPaint);
      }

      final int scrollbarAnchorHeight = scrollbarHeight < bubbleRadius ? scrollbarHeight : bubbleRadius;
      final int cx = (int) (Lang.rtl() ? scrollbarRect.right + scrollbarPadding + bubbleRadius : scrollbarRect.left - scrollbarPadding - bubbleRadius);
      final int cy = (int) Math.max(scrollbarRect.top + scrollbarAnchorHeight - bubbleRadius, bubbleRadius + scrollbarPadding);

      final int rectLeft = Lang.rtl() ? cx - bubbleRadius : cx;
      final int scrollbarCenter;

      float rectFactor;

      int rectTop, rectBottom;

      scrollbarCenter = (int) (scrollbarRect.top + (float) scrollbarAnchorHeight * .5f);
      if (scrollbarCenter < cy) {
        rectTop = (int) scrollbarRect.top;
        rectBottom = cy;
      } else {
        rectTop = cy;
        rectBottom = (int) scrollbarRect.top + scrollbarAnchorHeight;
      }

      int rectHeight = rectBottom - rectTop;
      if (rectHeight < bubbleRadius) {
        rectFactor = 1f - Math.min(1f, ((float) (bubbleRadius - rectHeight) / ((float) bubbleRadius * .5f)));
      } else {
        rectFactor = 1f;
      }

      final boolean saved = bubbleFactor != 1f;
      if (saved) {
        final float scale = bubbleFactor;
        final float scaleY = scrollbarRect.top + ((float) scrollbarAnchorHeight * .5f);
        c.save();
        c.translate((float) (Lang.rtl() ? -scrollbarPadding : scrollbarPadding) * (1f - bubbleFactor), 0);
        c.scale(scale, scale, Lang.rtl() ? rectLeft : rectLeft + bubbleRadius, scaleY);
        rectFactor *= bubbleFactor;
      }

      int bubbleColor = ColorUtils.fromToArgb(ColorUtils.compositeColor(Theme.fillingColor(), inactiveColor), activeColor, bubbleFactor);
      if (rectFactor != 0f) {
        bubbleRect.top = cy - bubbleRadius;
        bubbleRect.bottom = cy + bubbleRadius;
        bubbleRect.left = cx - bubbleRadius;
        bubbleRect.right = cx + bubbleRadius;

        c.save();
        if (scrollbarCenter < cy) {
          c.clipRect(rectLeft, cy - bubbleRadius, rectLeft + bubbleRadius, cy);
        } else {
          c.clipRect(rectLeft, cy, rectLeft + bubbleRadius, cy + bubbleRadius);
        }
        float rectRadius = Math.max(bubbleMinRadius, bubbleRadius * (1f - rectFactor));
        c.drawRoundRect(bubbleRect, rectRadius, rectRadius, Paints.fillingPaint(bubbleColor));
        c.restore();
      }

      c.drawCircle(cx, cy, bubbleRadius, Paints.fillingPaint(bubbleColor));

      bubbleLetterPaint.setColor(ColorUtils.alphaColor(bubbleFactor, activeTextColor));
      if (lastBigBubble.length() <= 2 || bubbleTextWidth <= bubbleRadius) {
        c.drawText(lastBigBubble, cx - (int) ((float) bubbleTextWidth * .5f), cy + bubbleTextOffset, bubbleLetterPaint);
      } else {
        float scale = (float) bubbleRadius / (float) bubbleTextWidth;
        c.save();
        c.scale(scale, scale, cx, cy);
        c.drawText(lastBigBubble, cx - (int) ((float) bubbleTextWidth * .5f), cy + bubbleTextOffset, bubbleLetterPaint);
        c.restore();
      }

      if (saved) {
        c.restore();
      }
    }
  }

  // Sectioned stuff

  public static class SectionViewHolder extends RecyclerView.ViewHolder {
    public SectionViewHolder (View itemView) {
      super(itemView);
    }
  }

  public static abstract class SectionedAdapter extends RecyclerView.Adapter<SectionViewHolder> {
    private Section[] sections;
    private int totalCount;

    protected SectionedRecyclerView parent;
    protected Context context;
    private boolean needSeparators;

    public SectionedAdapter (SectionedRecyclerView parentView) {
      this.parent = parentView;
      this.context = parentView.getContext();

      registerAdapterDataObserver(new AdapterDataObserver() {
        @Override
        public void onItemRangeRemoved (int positionStart, int itemCount) {
          postUpdate();
        }

        @Override
        public void onItemRangeInserted (int positionStart, int itemCount) {
          postUpdate();
        }

        @Override
        public void onChanged () {
          updateSections();
          postUpdate();
        }

        private void postUpdate () {
          parent.post(parent);
        }
      });
    }

    public void setNeedSeparators () {
      needSeparators = true;
    }

    public boolean needSeparators () {
      return needSeparators;
    }

    public boolean isReady () {
      return sections != null && sections.length == getSectionCount();
    }

    public void updateSections () {
      boolean updated = false;
      if (sections == null || sections.length != getSectionCount()) {
        sections = new Section[getSectionCount()];
        updated = true;
      }

      int i = 0, pos = 0, count; Section s;
      for (Section unused : sections) {
        if (updated) {
          s = new Section();
          sections[i] = s;
        } else {
          s = unused;
        }

        count = getRowsInSection(i);

        s.startIndex = pos;
        s.count = pos == 0 || !needSeparators ? count : count + 1;

        pos += s.count;
        i++;
      }
      totalCount = pos;
    }

    public int getDataPosition (int section, int sourcePosition) {
      return needSeparators ? (section == -1 || (sourcePosition != 0 && sections[section].startIndex == sourcePosition) ? -1 : sourcePosition - section) : sourcePosition;
    }

    public int getDataPosition (int sourcePosition) {
      return needSeparators ? getDataPosition(getSectionForPosition(sourcePosition), sourcePosition) : sourcePosition;
    }

    public int getSectionedPosition (int position) {
      int section = getSectionForPosition(position);
      if (section == -1)
        return -1;
      if (section == 0)
        return position;
      if (needSeparators)
        return position - sections[section].startIndex - 1;
      else
        return position - sections[section].startIndex;
    }

    public int getSectionForPosition (int pos) {
      int i = 0;
      for (Section s : sections) {
        if (pos == s.startIndex)
          return needSeparators ? (i == 0 ? 0 : -1) : i;
        if (pos > s.startIndex && pos < s.startIndex + s.count)
          return i;
        i++;
      }
      return -1;
    }

    public abstract int getSectionCount ();
    public abstract int getRowsInSection (int section);
    public abstract int getItemHeight ();
    public int getItemHeight (int adapterPosition) {
      return getItemHeight();
    }
    public abstract View createView (int viewType);
    public abstract void updateView (SectionViewHolder holder, int position, boolean isUpdate);
    public final int getSeparatorHeight () {
      return Screen.dp(22f);
    }

    public void attachViewToWindow (SectionViewHolder holder) { }
    public void detachViewFromWindow (SectionViewHolder holder) { }

    @Override
    public void onViewAttachedToWindow (SectionViewHolder holder) {
      if (holder.getItemViewType() == 0) {
        attachViewToWindow(holder);
      }
    }

    @Override
    public void onViewDetachedFromWindow (SectionViewHolder holder) {
      if (holder.getItemViewType() == 0) {
        detachViewFromWindow(holder);
      }
    }

    @NonNull
    public abstract String getSectionName (int section);

    public boolean isSeparator (int index) {
      return needSeparators && getSectionedPosition(index) == -1;
    }

    @Override
    public final int getItemCount () {
      return totalCount;
    }

    @Override
    public SectionViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      if (viewType == 0) {
        SeparatorView view = new SeparatorView(context);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getSeparatorHeight()));
        view.setOffsets(Screen.dpf(72f), Screen.dpf(22f));
        return new SectionViewHolder(view);
      } else {
        return new SectionViewHolder(createView(viewType));
      }
    }

    public void updateViewByPosition (int position) {
      View view = parent.getLayoutManager().findViewByPosition(position);
      if (view != null) {
        SectionViewHolder holder = (SectionViewHolder) parent.getChildViewHolder(view);
        if (holder != null) {
          updateView(holder, position, true);
          return;
        }
      }
      notifyItemChanged(position);
    }

    @Override
    public void onBindViewHolder (@NonNull SectionViewHolder holder, int position) {
      if (!isSeparator(position)) {
        updateView(holder, position, false);
      }
    }

    @Override
    public int getItemViewType (int position) {
      return isSeparator(position) ? 0 : 1;
    }
  }

  private static class Section {
    public int startIndex, count;
  }
}
