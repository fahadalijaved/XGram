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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.StringUtils;

/**
 * vkryl's CounterAnimator (same API, so DrawAlgorithms draws it unchanged) with the digits on the SPATIAL_FAST spring:
 * a new digit slides in from above or below with a small overshoot. Visibility stays clamped by ListAnimator.Entry, so
 * only the slide and the pill width bounce. With Expressive design off it is Telegram X's DECELERATE 180 ms.
 */
public class XgramCounterAnimator<T extends CounterAnimator.TextDrawable> extends CounterAnimator<T> {
  private static final class XPart<T extends CounterAnimator.TextDrawable> extends CounterAnimator.Part<T> {
    XPart (int index, @NonNull T text) {
      super(index, text);
    }

    void setFrom (int position) {
      verticalPosition.setFrom(position);
    }

    void setTo (int position) {
      this.position = position;
    }
  }

  private final Callback<T> callback;
  private final ListAnimator<CounterAnimator.Part<T>> animator;

  private long count;
  private boolean hasCounter;

  public XgramCounterAnimator (Callback<T> callback) {
    super(callback);
    this.callback = callback;
    // The cached spring (one per token, not one per counter: counters are made for every chat row and message)
    this.animator = new ListAnimator<>(listAnimator -> callback.onItemsChanged(this),
      XgramAppMotion.interp(XgramExpressive.SPATIAL_FAST, AnimatorUtils.DECELERATE_INTERPOLATOR),
      XgramAppMotion.duration(XgramExpressive.SPATIAL_FAST, 180L));
  }

  @Override
  public float getWidth () {
    return animator.getMetadata().getTotalWidth();
  }

  @Override
  public void setCounter (long count, String textRepresentation, boolean animated) {
    setCounterImpl(count, toParts(textRepresentation), animated);
  }

  @Override
  public void hideCounter (boolean animated) {
    setCounterImpl(0, null, animated);
  }

  @Nullable
  private List<CounterAnimator.Part<T>> toParts (String textRepresentation) {
    if (StringUtils.isEmpty(textRepresentation)) {
      return null;
    }
    List<CounterAnimator.Part<T>> parts = new ArrayList<>(textRepresentation.length());
    for (int i = 0; i < textRepresentation.length(); ) {
      int codePoint = textRepresentation.codePointAt(i);
      int charCount = Character.charCount(codePoint);
      String part = textRepresentation.substring(i, i + charCount);
      parts.add(new XPart<>(parts.size(), callback.onCreateTextDrawable(part)));
      i += charCount;
    }
    return parts;
  }

  private void setCounterImpl (long count, @Nullable List<CounterAnimator.Part<T>> parts, boolean animated) {
    final boolean hasCounter = parts != null && !parts.isEmpty();
    if (this.count == count && this.hasCounter == hasCounter) {
      return;
    }
    final long prevCount = this.count;
    this.count = count;
    this.hasCounter = hasCounter;
    if (!hasCounter) {
      this.animator.reset(null, animated);
      return;
    }
    this.animator.reset(parts, animated, new ListAnimator.ResetCallback<CounterAnimator.Part<T>>() {
      @Override
      public void onItemAdded (CounterAnimator.Part<T> item, boolean isReturned) {
        XPart<T> part = (XPart<T>) item;
        if (prevCount != 0 && prevCount < count) {
          part.setFrom(CounterAnimator.Part.POSITION_UP);
        } else if (prevCount > count) {
          part.setFrom(CounterAnimator.Part.POSITION_BOTTOM);
        }
        part.setTo(CounterAnimator.Part.POSITION_NORMAL);
      }

      @Override
      public void onItemRemoved (CounterAnimator.Part<T> item) {
        XPart<T> part = (XPart<T>) item;
        if (count == 0) {
          part.setTo(CounterAnimator.Part.POSITION_NORMAL);
        } else if (prevCount < count) {
          part.setTo(CounterAnimator.Part.POSITION_BOTTOM);
        } else if (prevCount > count) {
          part.setTo(CounterAnimator.Part.POSITION_UP);
        }
      }
    });
  }

  @NonNull
  @Override
  public Iterator<ListAnimator.Entry<CounterAnimator.Part<T>>> iterator () {
    return animator.iterator();
  }
}
