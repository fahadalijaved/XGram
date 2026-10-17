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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget.emoji;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.core.Lang;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public abstract class EmojiLayoutSectionPager extends FrameLayout implements FactorAnimator.Target {
  private static final int CHANGE_SECTION_ANIMATOR = 0;

  private View currentSectionView;
  private int nextSection = -1;
  private View nextSectionView;
  private boolean sectionIsLeft;
  private int currentSection;

  private FactorAnimator sectionAnimator;
  private float sectionChangeFactor;

  public EmojiLayoutSectionPager (@NonNull Context context) {
    super(context);
  }

  public void init (int currentSection) {
    this.currentSection = currentSection;
    this.currentSectionView = getSectionView(currentSection);
    addView(currentSectionView);
  }

  public int getCurrentSection () {
    return currentSection;
  }

  public int getNextSection () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return xgramReversing ? -1 : nextSection; // XGram: the running slide's destination (-1 for none)
    }
    return currentSection;
  }

  // XGram M3E: the emoji panel's Emoji / Stickers / GIFs switch shows a section at once (a cut, no slide and no
  // onSectionChangeStart / End) while the page is not idle on screen, and can turn a running slide back. Telegram X
  // never calls any of this, and xgramReversing stays false without it.

  private boolean xgramReversing;

  public boolean xgramHasSection () {
    return currentSectionView != null;
  }

  /** Where the content is going: the running slide's destination, or the current section. */
  public int xgramDestination () {
    return nextSection != -1 && !xgramReversing ? nextSection : currentSection;
  }

  public boolean xgramIsSliding () {
    return nextSection != -1 || (sectionAnimator != null && sectionAnimator.isAnimating());
  }

  /** Shows {@code id} at once: no slide, no onSectionChangeStart / End. Finishes or drops a running slide. */
  public boolean xgramSetSectionNow (int id) {
    if (nextSection == -1 && currentSection == id) {
      return false;
    }
    xgramReversing = false;
    if (sectionAnimator != null) {
      sectionAnimator.cancel(); // cancel() clears isAnimating first: no finished callback
    }
    final View target = getSectionView(id);
    if (nextSectionView != null && nextSectionView != target) {
      removeView(nextSectionView);
    }
    if (currentSectionView != null && currentSectionView != target) {
      removeView(currentSectionView);
    }
    nextSection = -1;
    nextSectionView = null;
    currentSection = id;
    currentSectionView = target;
    if (target.getParent() == null) {
      addView(target);
    }
    sectionChangeFactor = 0f;
    if (sectionAnimator != null) {
      sectionAnimator.forceFactor(0f); // its finished(0) is a no-op (not turning back)
    }
    target.setTranslationX(0f);
    return true;
  }

  /**
   * Slides to {@code id} on the M3E curve, or turns a running slide back when {@code id} is where it started; nothing
   * when the content is there or on its way. False for a third section while a slide runs (the caller cuts).
   */
  public boolean xgramSlideToward (int id, boolean fromLeft) {
    if (nextSection != -1) {
      if (id == nextSection) {
        if (xgramReversing) {
          xgramReversing = false;
          onSectionChangeStart(currentSection, nextSection, -1);
          xgramAnimateSection(1f);
        }
        return true;
      }
      if (id == currentSection) {
        if (!xgramReversing) {
          xgramReversing = true; // set first: the callbacks read the destination
          onSectionChangeStart(nextSection, currentSection, -1);
          xgramAnimateSection(0f);
        }
        return true;
      }
      return false;
    }
    return currentSection == id || changeSection(id, fromLeft, -1);
  }

  private void xgramAnimateSection (float toFactor) {
    com.fahadali.xgram.appearance.XgramAppMotion.use(sectionAnimator, com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT, true);
    sectionAnimator.animateTo(toFactor);
  }

  /** XGram: a slide that turned back is over; {@code section} (where it started) stays. */
  protected void xgramOnSectionChangeReverted (int section) { }

  public boolean canChangeSection () {
    return sectionAnimator == null || (!sectionAnimator.isAnimating() && sectionAnimator.getFactor() == 0f && sectionChangeFactor == 0f);
  }

  public boolean isAnimationNotActive () {
    return sectionAnimator == null || !sectionAnimator.isAnimating();
  }

  public boolean isSectionStable () {
    return sectionAnimator == null || sectionAnimator.getFactor() == 0f;
  }

  public boolean changeSection (int sectionId, boolean fromLeft, int stickerSetSection) {
    if (currentSection == sectionId || !canChangeSection()) {
      return false;
    }

    View sectionView = getSectionView(sectionId);

    this.nextSection = sectionId;
    this.nextSectionView = sectionView;
    this.sectionIsLeft = fromLeft;

    this.addView(sectionView);

    if (this.sectionAnimator == null) {
      this.sectionAnimator = new FactorAnimator(CHANGE_SECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    }

    com.fahadali.xgram.appearance.XgramAppMotion.use(this.sectionAnimator, com.fahadali.xgram.appearance.XgramExpressive.STD_SPATIAL_DEFAULT, true); // XGram: the M3E curve (nothing with Expressive off)
    this.sectionAnimator.animateTo(1f);
    this.onSectionChangeStart(currentSection, nextSection, stickerSetSection);

    return true;
  }

  private void updatePositions () {
    if (sectionIsLeft != Lang.rtl()) {
      currentSectionView.setTranslationX((float) currentSectionView.getMeasuredWidth() * sectionChangeFactor);
      if (nextSectionView != null) {
        nextSectionView.setTranslationX((float) (-nextSectionView.getMeasuredWidth()) * (1f - sectionChangeFactor));
      }
    } else {
      currentSectionView.setTranslationX((float) (-currentSectionView.getMeasuredWidth()) * sectionChangeFactor);
      if (nextSectionView != null) {
        nextSectionView.setTranslationX((float) nextSectionView.getMeasuredWidth() * (1f - sectionChangeFactor));
      }
    }
  }

  private void applySection () {
    removeView(currentSectionView);

    int oldSection = this.currentSection;

    currentSection = nextSection;
    nextSection = -1;
    currentSectionView = nextSectionView;
    nextSectionView = null;
    sectionAnimator.forceFactor(0f);
    sectionChangeFactor = 0f;

    this.onSectionChangeEnd(oldSection, currentSection);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == CHANGE_SECTION_ANIMATOR) {
      this.sectionChangeFactor = factor;
      updatePositions();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == CHANGE_SECTION_ANIMATOR) {
      if (finalFactor == 1f) {
        xgramReversing = false;
        applySection();
      } else if (finalFactor == 0f && xgramReversing) {
        // XGram: the slide turned back and is over: the section it started from stays
        xgramReversing = false;
        if (nextSectionView != null) {
          removeView(nextSectionView);
        }
        nextSectionView = null;
        nextSection = -1;
        sectionChangeFactor = 0f;
        currentSectionView.setTranslationX(0f);
        xgramOnSectionChangeReverted(currentSection);
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updatePositions();
  }

  protected abstract View getSectionView (int section);
  protected abstract void onSectionChangeStart (int prevSection, int nextSection, int stickerSetSection);
  protected abstract void onSectionChangeEnd (int prevSection, int currentSection);
}
