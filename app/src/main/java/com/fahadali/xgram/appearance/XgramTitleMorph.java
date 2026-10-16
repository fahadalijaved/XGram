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

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

/**
 * XGram M3E: an app bar title that changes with a fade-through (out on effects-fast, in on effects-default with a
 * .92 to 1 scale from its start edge) and an optional leading element (loading indicator or icon). It uses
 * {@link android.view.View#setTransitionAlpha}, which nothing else in the header touches: HeaderView drives alpha
 * and translation for its own transitions and the large-title fade.
 */
public final class XgramTitleMorph {
  public static final int LEADING_NONE = 0, LEADING_PROGRESS = 1, LEADING_OFFLINE = 2, LEADING_STUCK = 3;

  /** A page whose app bar title may carry a leading element (set again whenever the header rebuilds its title). */
  public interface LeadingProvider {
    int xgramTitleLeading ();
  }

  public static int leadingFor (int connectionState) {
    switch (connectionState) {
      case XgramConnection.PROGRESS:
        return LEADING_PROGRESS;
      case XgramConnection.OFFLINE:
        return LEADING_OFFLINE;
      case XgramConnection.STUCK:
        return LEADING_STUCK;
      default:
        return LEADING_NONE;
    }
  }

  private XgramTitleMorph () { }

  @Nullable
  private static Morph get (TextView view) {
    Object tag = view.getTag(R.id.xgram_title_morph);
    return tag instanceof Morph ? (Morph) tag : null;
  }

  private static Morph create (TextView view) {
    Morph m = new Morph(view);
    view.setTag(R.id.xgram_title_morph, m);
    return m;
  }

  /** Changes {@code view}'s text and leading element; a fade-through when {@code animated}. */
  public static void morph (@Nullable TextView view, CharSequence text, int leading, boolean animated) {
    if (view == null) {
      return;
    }
    Morph m = get(view);
    if (m == null) {
      if (leading == LEADING_NONE && TextUtils.equals(view.getText(), text)) {
        return;
      }
      m = create(view);
    }
    m.morph(text, leading, animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && view.isAttachedToWindow() && view.getVisibility() == android.view.View.VISIBLE);
  }

  /** The header gave {@code view} to {@code item}'s title: no animation left over, the item's own leading element. */
  public static void reset (@Nullable TextView view, @Nullable Object item) {
    if (view == null || !XgramExpressive.isEnabled()) {
      return;
    }
    int leading = item instanceof LeadingProvider ? ((LeadingProvider) item).xgramTitleLeading() : LEADING_NONE;
    Morph m = get(view);
    if (m == null) {
      if (leading == LEADING_NONE) {
        return;
      }
      m = create(view);
    }
    m.finish(leading);
  }

  private static final class Morph {
    private final TextView view;
    private final XgramMotion.Value fade, scale;
    private int leading = LEADING_NONE;
    private CharSequence pendingText;
    private int pendingLeading;
    private boolean out;
    private @Nullable XgramLoadingIndicatorDrawable indicator;
    private @Nullable Drawable offlineIcon, stuckIcon;

    Morph (TextView view) {
      this.view = view;
      this.fade = new XgramMotion.Value(XgramExpressive.EFFECTS_FAST, 1f, (v, value, velocity) -> setFade(value));
      this.scale = new XgramMotion.Value(XgramExpressive.SPATIAL_FAST, 1f, (v, value, velocity) -> {
        view.setScaleX(value);
        view.setScaleY(value);
      });
      this.fade.setEndListener(this::onFadeEnd);
    }

    private void setFade (float value) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        view.setTransitionAlpha(Math.max(0f, Math.min(1f, value)));
      }
    }

    void morph (CharSequence text, int leading, boolean animated) {
      if (!animated) {
        apply(text, leading);
        finish(leading);
        return;
      }
      if (out) {
        pendingText = text;
        pendingLeading = leading;
        return;
      }
      if (leading == this.leading && TextUtils.equals(view.getText(), text) && fade.getTarget() == 1f) {
        return;
      }
      out = true;
      pendingText = text;
      pendingLeading = leading;
      fade.setSpring(XgramExpressive.EFFECTS_FAST).animateTo(0f);
    }

    private void onFadeEnd () {
      if (!out) {
        return;
      }
      out = false;
      apply(pendingText, pendingLeading);
      pendingText = null;
      boolean rtl = Lang.rtl();
      view.setPivotX(rtl ? view.getWidth() : 0f);
      view.setPivotY(view.getHeight() / 2f);
      if (!XgramMotion.reduce()) {
        scale.setValue(.92f);
        view.setScaleX(.92f);
        view.setScaleY(.92f);
        scale.setSpring(XgramExpressive.SPATIAL_FAST).animateTo(1f);
      }
      fade.setSpring(XgramExpressive.EFFECTS_DEFAULT).animateTo(1f);
    }

    void finish (int leading) {
      out = false;
      pendingText = null;
      fade.setValue(1f);
      scale.setValue(1f);
      setFade(1f);
      if (view.getScaleX() != 1f || view.getScaleY() != 1f) {
        view.setScaleX(1f);
        view.setScaleY(1f);
      }
      setLeading(leading);
    }

    private void apply (@Nullable CharSequence text, int leading) {
      if (text != null && !TextUtils.equals(view.getText(), text)) {
        Views.setMediumText(view, text);
      }
      setLeading(leading);
    }

    private void setLeading (int leading) {
      if (this.leading == leading) {
        if (leading == LEADING_PROGRESS && indicator != null && !indicator.isRunning()) {
          indicator.start();
        }
        return;
      }
      this.leading = leading;
      Drawable d = drawable(leading);
      if (indicator != null && d != indicator) {
        indicator.stop();
      }
      view.setCompoundDrawablesRelative(d, null, null, null);
      view.setCompoundDrawablePadding(d != null ? Screen.dp(8f) : 0);
      if (d instanceof Animatable) {
        ((Animatable) d).start();
      }
    }

    @Nullable
    private Drawable drawable (int leading) {
      switch (leading) {
        case LEADING_PROGRESS:
          if (indicator == null) {
            indicator = new XgramLoadingIndicatorDrawable(20f, XgramExpressive.ON_SURFACE_VARIANT);
          }
          return indicator;
        case LEADING_OFFLINE:
          if (offlineIcon == null) {
            offlineIcon = icon(R.drawable.xgram_cloud_off_24);
          }
          tint(offlineIcon);
          return offlineIcon;
        case LEADING_STUCK:
          if (stuckIcon == null) {
            stuckIcon = icon(R.drawable.baseline_sync_problem_24);
          }
          tint(stuckIcon);
          return stuckIcon;
        default:
          return null;
      }
    }

    @Nullable
    private Drawable icon (int res) {
      Drawable d = Drawables.get(view.getResources(), XgramIconPack.map(res));
      if (d != null) {
        int size = Screen.dp(20f);
        d.setBounds(0, 0, size, size);
      }
      return d;
    }

    private static void tint (@Nullable Drawable d) {
      if (d != null) {
        d.setColorFilter(new PorterDuffColorFilter(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT), PorterDuff.Mode.SRC_IN));
      }
    }
  }
}
