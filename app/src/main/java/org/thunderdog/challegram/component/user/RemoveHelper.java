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
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.component.user;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class RemoveHelper implements FactorAnimator.Target {
  public interface RemoveDelegate {
    void setRemoveDx (float dx);
    void onRemoveSwipe ();
  }

  private View view;
  private Drawable icon;

  private float dx;

  public RemoveHelper (View view, @DrawableRes int icon) {
    this.view = view;
    this.icon = Drawables.get(view.getResources(), icon);
  }

  public void setDx (float dx) {
    if (this.dx != dx) {
      this.dx = dx;
      if (XgramExpressive.isEnabled()) {
        xgramCheckThreshold();
      }
      view.invalidate();
    }
  }

  public void reset () {
    this.dx = 0;
    if (animator != null) {
      animator.forceFactor(0f);
    }
    fadeFactor = 0f;
    xgramPastThreshold = false;
    if (xgramIconSpring != null) {
      xgramIconSpring.setValue(0f);
    }
  }

  // XGram M3E: the reveal is errorContainer in the row's own tile shape, drawn behind the row as it slides; the
  // onErrorContainer icon scales in (SPATIAL_FAST) once the swipe passes the remove threshold, with a tick

  /** The remove threshold of the swipe in progress (ItemTouchHelper swipes one row at a time), set by {@link #attach}. */
  private static float xgramThresholdPx;

  private boolean xgramPastThreshold;
  private @Nullable XgramExpressive.Spring xgramIconSpring;
  private @Nullable Paint xgramIconPaint;

  private void xgramCheckThreshold () {
    final float threshold = xgramThresholdPx > 0f ? xgramThresholdPx : Screen.dp(68f);
    final boolean past = Math.abs(dx) >= threshold;
    if (past == xgramPastThreshold) {
      return;
    }
    xgramPastThreshold = past;
    if (xgramIconSpring == null) {
      xgramIconSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> view.invalidate());
    }
    xgramIconSpring.animateTo(past ? 1f : 0f);
    if (past) {
      view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
  }

  /** The row's tile shape: its MorphDrawable's path (segment corners, the pressed morph), or null for a plain row. */
  @Nullable
  private Path xgramTilePath () {
    XgramExpressive.MorphDrawable morph = XgramExpressive.findMorph(view.getBackground());
    return morph != null ? morph.getPath() : null;
  }

  /** errorContainer in the tile's shape (or the vacated part of a plain row), the icon at its end. */
  private void xgramDrawReveal (Canvas c, float alpha, float iconScale, int left, int right) {
    final int width = view.getMeasuredWidth();
    final int height = view.getMeasuredHeight();
    if (width <= 0 || height <= 0 || alpha <= 0f || left >= right) {
      return;
    }
    final Path tile = xgramTilePath();
    c.save();
    if (tile != null) {
      c.clipPath(tile);
    }
    c.clipRect(left, 0, right, height);
    c.drawColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ERROR_CONTAINER), alpha));
    if (icon != null && iconScale > 0f) {
      if (xgramIconPaint == null) {
        xgramIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      }
      final android.graphics.ColorFilter filter = Paints.getColorFilter(XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER));
      if (xgramIconPaint.getColorFilter() != filter) {
        xgramIconPaint.setColorFilter(filter);
      }
      xgramIconPaint.setAlpha((int) (255f * Math.max(0f, Math.min(1f, alpha))));
      final int iconWidth = icon.getMinimumWidth(), iconHeight = icon.getMinimumHeight();
      final float iconX = Lang.rtl() ? Screen.dp(24f) : width - Screen.dp(24f) - iconWidth;
      final float iconY = height / 2f - iconHeight / 2f;
      final boolean scaled = iconScale != 1f;
      if (scaled) {
        c.save();
        c.scale(iconScale, iconScale, iconX + iconWidth / 2f, iconY + iconHeight / 2f);
      }
      Drawables.draw(c, icon, iconX, iconY, xgramIconPaint);
      if (scaled) {
        c.restore();
      }
    }
    c.restore();
  }

  private float xgramIconScale () {
    return xgramIconSpring != null ? Math.max(0f, xgramIconSpring.getValue()) : 0f;
  }

  private FactorAnimator animator;
  private boolean fadingOut;

  public void onSwipe () {
    ViewUtils.onClick(view);
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else {
      animator.forceFactor(0f);
    }
    fadingOut = true;
    dx = 0f;
    xgramPastThreshold = false; // XGram M3E: the fade draws the icon in full; the next swipe starts without it
    if (xgramIconSpring != null) {
      xgramIconSpring.setValue(0f);
    }
    animator.animateTo(1f);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.fadeFactor != factor) {
      this.fadeFactor = factor;
      view.invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    animator.forceFactor(0f);
    fadingOut = false;
    fadeFactor = 0f;
  }

  private boolean saved = false;

  public void save (Canvas c) {
    if ((saved = dx != 0 && fadeFactor == 0f)) {
      final XgramExpressive.MorphDrawable morph = XgramExpressive.isEnabled() ? XgramExpressive.findMorph(view.getBackground()) : null;
      if (morph != null) {
        // XGram M3E: the reveal in the tile's shape, then the tile itself slid over it (views that call this from
        // onDraw drew their background in place already: the reveal covers it)
        xgramDrawReveal(c, 1f, xgramIconScale(), 0, view.getMeasuredWidth());
        c.save();
        c.translate(dx, 0);
        morph.draw(c);
        return;
      }
      c.save();
      c.translate(dx, 0);
    }
  }

  public void restore (Canvas c) {
    if (saved) {
      saved = false;
      c.restore();
    }
  }

  private float fadeFactor;

  public void draw (Canvas c) {
    final int width = view.getMeasuredWidth();
    final int height = view.getMeasuredHeight();

    if (XgramExpressive.isEnabled()) {
      if (fadeFactor > 0f || fadingOut) {
        // XGram M3E: removed: the reveal covers the tile and fades out
        xgramDrawReveal(c, 1f - fadeFactor, 1f, 0, width);
      } else if (dx != 0 && xgramTilePath() == null) {
        // a row without a tile shape (its background is not a morph): the vacated part only, as Telegram X
        final int left = Lang.rtl() ? 0 : Math.max(0, Math.min(width, (int) (width + dx)));
        final int right = Lang.rtl() ? Math.max(0, Math.min(width, (int) dx)) : width;
        xgramDrawReveal(c, 1f, xgramIconScale(), left, right);
      }
      return;
    }

    final int left;
    final int right;

    if (fadeFactor > 0f || fadingOut) {
      left = 0;
      right = width;
    } else if (Lang.rtl()) {
      left = 0;
      right = Math.max(0, Math.min(width, (int) dx));
    } else {
      left = Math.max(0, Math.min(width, (int) (width + dx)));
      right = width;
    }

    if (left == right) {
      return;
    }


    float alpha = (1f - fadeFactor);
    Paint bitmapPaint = Paints.whitePorterDuffPaint();
    if (alpha < 1f) {
      bitmapPaint.setAlpha((int) (255f * alpha));
    }
    int color = ColorUtils.alphaColor(alpha, Theme.getColor(ColorId.fillingNegative));
    int iconX = Lang.rtl() ? Screen.dp(18f) : right - Screen.dp(18f) - icon.getMinimumWidth();
    int iconY = height / 2 - icon.getMinimumHeight() / 2;

    if (left == 0 && right == width) {
      c.drawColor(color);
      Drawables.draw(c, icon, iconX, iconY, bitmapPaint);
    } else {
      c.save();
      c.clipRect(left, 0, right, height);
      c.drawRect(left, 0, right, height, Paints.fillingPaint(color));
      Drawables.draw(c, icon, iconX, iconY, bitmapPaint);
      c.restore();
    }

    if (alpha < 1f) {
      bitmapPaint.setAlpha(255);
    }
  }

  /**
   * XGram M3E: the reorder lift. The dragged tile's corners go to 16dp, a surfaceBright tile becomes
   * surfaceContainerHigh (so it stays apart from the tiles under it with Pure black too, where no shadow shows), it rises
   * to elevation 3 and grows to 1.02, all on one SPATIAL_DEFAULT spring; on drop it settles back. Reduce motion snaps.
   */
  private static final class XgramLift {
    private @Nullable View view;
    private @Nullable XgramExpressive.MorphDrawable morph;
    private boolean recolored;
    private @Nullable XgramExpressive.Spring spring;

    private XgramExpressive.Spring spring () {
      if (spring == null) {
        spring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_DEFAULT, 0f, (s, value) -> apply());
        spring.setEndListener(() -> {
          if (spring != null && spring.getTarget() == 0f) {
            apply();
            view = null;
            morph = null;
          }
        });
      }
      return spring;
    }

    void lift (View v) {
      if (view != null && view != v) {
        settle();
      }
      view = v;
      morph = XgramExpressive.findMorph(v.getBackground());
      if (morph != null) {
        morph.setCornerTarget(XgramExpressive.CORNER_L, true);
        if (!recolored && morph.getDrawColor() == XgramExpressive.color(XgramExpressive.SURFACE_BRIGHT)) {
          morph.setColorRole(XgramExpressive.SURFACE_CONTAINER_HIGH);
          recolored = true;
        }
      }
      v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      spring().animateTo(1f);
    }

    void drop (View v) {
      if (view != v) {
        return;
      }
      if (morph != null) {
        morph.setCornerTarget(Float.NaN, true);
        if (recolored) {
          morph.setColorRole(XgramExpressive.SURFACE_BRIGHT);
        }
      }
      recolored = false;
      spring().animateTo(0f);
    }

    void apply () {
      if (view == null || spring == null) {
        return;
      }
      final float value = spring.getValue();
      final float scale = 1f + .02f * value;
      view.setScaleX(scale);
      view.setScaleY(scale);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        view.setElevation(Screen.dp(XgramExpressive.ELEVATION_3) * Math.max(0f, value));
      }
    }

    /** The previous tile at rest at once (a new drag started while it settled). */
    private void settle () {
      if (view != null) {
        view.setScaleX(1f);
        view.setScaleY(1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          view.setElevation(0f);
        }
      }
      if (morph != null) {
        morph.setCornerTarget(Float.NaN, false);
        if (recolored) {
          morph.setColorRole(XgramExpressive.SURFACE_BRIGHT);
        }
      }
      recolored = false;
      morph = null;
      view = null;
      if (spring != null) {
        spring.setValue(0f);
      }
    }
  }

  public interface Callback {
    boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position);
    void onRemove (RecyclerView.ViewHolder viewHolder);
    default float getRemoveThresholdWidth () {
      return 68f;
    }
  }

  public interface ExtendedCallback extends Callback {
    int makeDragFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder);
    boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target);
    void onCompleteMovement (int fromPosition, int toPosition);
    boolean isLongPressDragEnabled ();
    default boolean canDropOver (RecyclerView recyclerView, RecyclerView.ViewHolder current, RecyclerView.ViewHolder target) {
      return true;
    }
  }

  public static ItemTouchHelper attach (RecyclerView recyclerView, final Callback callback) {
    final ItemTouchHelper[] itemTouchHelper = new ItemTouchHelper[1];

    itemTouchHelper[0] = new ItemTouchHelper(new ItemTouchHelper.Callback() {
      @Override
      public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int dragFlags = callback instanceof ExtendedCallback ? ((ExtendedCallback) callback).makeDragFlags(recyclerView, viewHolder) : 0;
        int movementFlags = recyclerView.getLayoutManager() != null && recyclerView.getAdapter() != null && recyclerView.getAdapter().getItemCount() > 0 && callback.canRemove(recyclerView, viewHolder, viewHolder.getBindingAdapterPosition()) ? (Lang.rtl() ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT) : 0;
        return dragFlags != 0 || movementFlags != 0 ? makeMovementFlags(dragFlags, movementFlags) : 0;
      }

      @Override
      public boolean isItemViewSwipeEnabled () {
        return true;
      }

      // XGram M3E: the dragged tile lifts and settles back on a spring
      private final XgramLift xgramLift = new XgramLift();

      @Override
      public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
          super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
          if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && XgramExpressive.isEnabled()) {
            xgramLift.apply(); // over the elevation ItemTouchHelper gives the dragged row on its first frame
          }
        } else if (XgramExpressive.isEnabled()) {
          xgramThresholdPx = Screen.dp(callback.getRemoveThresholdWidth());
        }
        RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
        removeView.setRemoveDx(dX);
      }

      @Override
      public void onSelectedChanged (@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG && XgramExpressive.isEnabled()) {
          xgramLift.lift(viewHolder.itemView);
        }
      }

      @Override
      public float getSwipeThreshold (RecyclerView.ViewHolder viewHolder) {
        return (float) Screen.dp(callback.getRemoveThresholdWidth()) / (float) viewHolder.itemView.getMeasuredWidth();
      }

      @Override
      public void onSwiped (RecyclerView.ViewHolder viewHolder, int direction) {
        itemTouchHelper[0].onChildViewDetachedFromWindow(viewHolder.itemView);
        if (direction == (Lang.rtl() ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT)) {
          RemoveHelper.RemoveDelegate removeView = (RemoveHelper.RemoveDelegate) viewHolder.itemView;
          removeView.onRemoveSwipe();
          callback.onRemove(viewHolder);
        }
      }

      @Override
      public boolean isLongPressDragEnabled () {
        return callback instanceof ExtendedCallback && ((ExtendedCallback) callback).isLongPressDragEnabled();
      }

      private int dragFrom = -1;
      private int dragTo = -1;

      @Override
      public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
        viewHolder.itemView.invalidate();
        target.itemView.invalidate();
        if (XgramExpressive.isEnabled()) {
          viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); // XGram M3E: a tick on each new slot
        }
      }

      @Override
      public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getBindingAdapterPosition();
        int toPosition = target.getBindingAdapterPosition();

        if (callback instanceof ExtendedCallback && ((ExtendedCallback) callback).onMove(recyclerView, viewHolder, target)) {
          if (dragFrom == -1)
            dragFrom = fromPosition;
          dragTo = toPosition;
          return true;
        }

        return false;
      }

      @Override
      public boolean canDropOver (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
        if (callback instanceof ExtendedCallback) {
          return ((ExtendedCallback) callback).canDropOver(recyclerView, current, target);
        }
        return super.canDropOver(recyclerView, current, target);
      }

      private void reallyMoved (int from, int to) {
        if (callback instanceof ExtendedCallback) {
          ((ExtendedCallback) callback).onCompleteMovement(from, to);
        }
      }

      @Override
      public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        xgramLift.drop(viewHolder.itemView);
        if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
          reallyMoved(dragFrom, dragTo);
        }
        dragFrom = dragTo = -1;
      }
    });
    itemTouchHelper[0].attachToRecyclerView(recyclerView);

    return itemTouchHelper[0];
  }
}
