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
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.Future;

public class CollapseListView extends FrameLayoutFix implements Destroyable {
  public interface Item extends Future<View> {
    int getVisualHeight ();

    default boolean allowCollapse () {
      return true;
    }

    default void onCompletelyHidden () { }
  }

  public static final class ViewItem implements Item {
    private final View view;
    private final int height;

    public ViewItem (View view, int height) {
      this.view = view;
      this.height = height;
    }

    @Override
    public int getVisualHeight () {
      return height;
    }

    @Override
    public View getValue () {
      return view;
    }
  }

  private static class Entry {
    final Item item;
    final View view;
    final ShadowView shadowView;
    final BoolAnimator isVisible;

    int height;
    @Nullable com.fahadali.xgram.appearance.XgramM3Chat.CardDrawable xgramCard; // XGram: Material 3 Expressive card

    public Entry (Item item, View view, ShadowView shadowView, FactorAnimator.Target target) {
      this.item = item;
      this.view = view;
      this.shadowView = shadowView;
      this.isVisible = new BoolAnimator(0, target, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      this.height = item.getVisualHeight();
    }
  }

  private final ArrayList<Entry> entries = new ArrayList<>();
  private final FactorAnimator globalVisibility = new FactorAnimator(0, (id, factor, fraction, callee) -> updatePositions(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 1f);

  public CollapseListView (@NonNull Context context) {
    super(context);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return super.onTouchEvent(event) || event.getAction() != MotionEvent.ACTION_DOWN || event.getY() < getTotalVisualHeight();
  }

  // XGram: Material 3 Expressive. The bars become a segmented card group (surfaceContainerHigh, outer corners 20dp,
  // inner 4dp, 2dp gaps, 8dp side margins) with no shadows; items passed here keep their own full-width look.
  private @Nullable Item[] xgramPlainItems;

  public void xgramSetPlainItems (@Nullable Item... items) {
    this.xgramPlainItems = items;
  }

  private boolean xgramIsPlain (Item item) {
    if (xgramPlainItems != null) {
      for (Item plain : xgramPlainItems) {
        if (plain == item) {
          return true;
        }
      }
    }
    return false;
  }

  public void initWithList (Item[] items, @Nullable ViewController<?> themeProvider) {
    if (!entries.isEmpty())
      throw new IllegalStateException();
    if (items.length == 0)
      throw new IllegalArgumentException();

    final boolean xgramCards = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    entries.ensureCapacity(items.length);
    for (Item item : items) {
      ShadowView shadowView = new ShadowView(getContext());
      shadowView.setSimpleBottomTransparentShadow(false);
      shadowView.setVisibility(View.GONE);
      shadowView.setAlpha(0f);
      shadowView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(7f)));
      addView(shadowView, 0);
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(shadowView);
      }

      View view = item.getValue();
      view.setVisibility(View.GONE);
      addView(view, 0);

      FactorAnimator.Target target = new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          updatePositions();
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
          if (finalFactor == 0f) {
            item.onCompletelyHidden();
          }
        }
      };

      Entry entry = new Entry(item, view, shadowView, target);
      if (xgramCards && !xgramIsPlain(item)) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof MarginLayoutParams) {
          ((MarginLayoutParams) params).leftMargin = Screen.dp(8f);
          ((MarginLayoutParams) params).rightMargin = Screen.dp(8f);
          view.setLayoutParams(params);
        }
        entry.xgramCard = com.fahadali.xgram.appearance.XgramM3Chat.applyCard(view);
      }
      entries.add(entry);
    }
    entries.trimToSize();
  }

  public void setGlobalVisibility (float globalVisibility, boolean animated) {
    if (animated) {
      this.globalVisibility.animateTo(globalVisibility);
    } else {
      this.globalVisibility.forceFactor(globalVisibility);
      updatePositions();
    }
  }

  public void hideAll (boolean animated) {
    for (Entry entry : entries) {
      entry.isVisible.setValue(false, animated);
    }
  }

  public void setItemVisible (Item item, boolean isVisible, boolean animated) {
    int i = indexOf(item);
    if (i != -1) {
      entries.get(i).isVisible.setValue(isVisible, animated);
    }
  }

  public void forceItemVisibility (Item item, boolean isVisible, float visibilityFactor) {
    int i = indexOf(item);
    if (i != -1) {
      entries.get(i).isVisible.forceValue(isVisible, visibilityFactor);
      updatePositions();
    }
  }

  public boolean isVisible (Item item) {
    int i = indexOf(item);
    return i != -1 && entries.get(i).isVisible.getFloatValue() > 0f;
  }

  public void notifyItemHeightChanged (Item item) {
    int i = indexOf(item);
    if (i != -1) {
      Entry entry = entries.get(i);
      int newHeight = item.getVisualHeight();
      if (entry.height != newHeight) {
        entry.height = newHeight;
        updatePositions();
      }
    }
  }

  private int indexOf (Item item) {
    int i = 0;
    for (Entry entry : entries) {
      if (entry.item == item)
        return i;
      i++;
    }
    return -1;
  }

  private void updatePositions () {
    final boolean xgramCards = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    if (xgramCards) {
      xgramUpdateSegments();
    }
    float y = 0;
    for (Entry entry : entries) {
      float visibility = entry.isVisible.getFloatValue();
      if (entry.item.allowCollapse()) {
        visibility *= globalVisibility.getFactor();
      }
      int height = entry.height;

      float positionY = y - (float) height * (1f - visibility);

      int viewVisibility = visibility > 0f ? View.VISIBLE : View.GONE;

      entry.view.setTranslationY(positionY);
      if (entry.view.getVisibility() != viewVisibility) {
        entry.view.setVisibility(viewVisibility);
      }

      positionY += height;

      entry.shadowView.setAlpha(visibility);
      entry.shadowView.setTranslationY(positionY);
      int shadowVisibility = xgramCards ? View.GONE : viewVisibility; // XGram: no shadows in Material 3 Expressive
      if (entry.shadowView.getVisibility() != shadowVisibility) {
        entry.shadowView.setVisibility(shadowVisibility);
      }

      y += height * visibility;
    }
    notifyTotalHeightChanged(Math.round(y));
  }

  /** XGram: segment of each visible card over the visible cards (a plain item breaks the group). */
  private void xgramUpdateSegments () {
    final int count = entries.size();
    for (int i = 0; i < count; i++) {
      Entry entry = entries.get(i);
      if (entry.xgramCard == null || !xgramIsShown(entry)) {
        continue;
      }
      boolean first = true, last = true;
      for (int j = i - 1; j >= 0; j--) {
        Entry other = entries.get(j);
        if (xgramIsShown(other)) {
          first = other.xgramCard == null;
          break;
        }
      }
      for (int j = i + 1; j < count; j++) {
        Entry other = entries.get(j);
        if (xgramIsShown(other)) {
          last = other.xgramCard == null;
          break;
        }
      }
      int segment;
      if (first && last) {
        segment = com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_SINGLE;
      } else if (first) {
        segment = com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_FIRST;
      } else if (last) {
        segment = com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_LAST;
      } else {
        segment = com.fahadali.xgram.appearance.XgramExpressive.SEGMENT_MIDDLE;
      }
      if (entry.xgramCard.getSegment() != segment) {
        entry.xgramCard.setSegment(segment);
        entry.view.invalidateOutline();
      }
    }
  }

  private boolean xgramIsShown (Entry entry) {
    float visibility = entry.isVisible.getFloatValue();
    if (entry.item.allowCollapse()) {
      visibility *= globalVisibility.getFactor();
    }
    return visibility > 0f && entry.height > 0;
  }

  private int lastHeight;

  public int getTotalVisualHeight () {
    int height = 0;
    for (Entry entry : entries) {
      float visibility = entry.isVisible.getFloatValue();
      if (entry.item.allowCollapse()) {
        visibility *= globalVisibility.getFactor();
      }
      height += entry.height * visibility;
    }
    notifyTotalHeightChanged(height);
    return height;
  }

  public interface TotalHeightChangeListener {
    void onTotalHeightChanged (CollapseListView listView);
  }

  private TotalHeightChangeListener heightChangeListener;

  public void setTotalHeightChangeListener (TotalHeightChangeListener totalHeightChangeListener) {
    this.heightChangeListener = totalHeightChangeListener;
  }

  private void notifyTotalHeightChanged (int height) {
    if (this.lastHeight != height) {
      this.lastHeight = height;
      if (heightChangeListener != null) {
        heightChangeListener.onTotalHeightChanged(this);
      }
    }
  }

  @Override
  public void performDestroy () {
    for (Entry entry : entries) {
      if (entry.item instanceof Destroyable) {
        ((Destroyable) entry.item).performDestroy();
      }
      if (entry.view instanceof Destroyable) {
        ((Destroyable) entry.view).performDestroy();
      }
    }
    removeAllViews();
  }
}
