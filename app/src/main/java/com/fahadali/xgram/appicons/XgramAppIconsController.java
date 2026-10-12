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
package com.fahadali.xgram.appicons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.v.CustomRecyclerView;

/** Settings → XGram → App icon. */
public final class XgramAppIconsController extends RecyclerViewController<Void> {
  private XgramAppIcons.Icon selected;
  private Adapter adapter;

  public XgramAppIconsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramAppIcons;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramAppIcon);
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, org.thunderdog.challegram.theme.ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (!areTemp && adapter != null) {
      adapter.notifyDataSetChanged(); // section titles, names and the switch take the new theme's colors
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    selected = XgramAppIcons.current(context);
    GridLayoutManager manager = new GridLayoutManager(context, 3);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return rows.get(position) instanceof XgramAppIcons.Icon ? 1 : 3;
      }
    });
    recyclerView.setLayoutManager(manager);
    recyclerView.setItemAnimator(null);
    recyclerView.setPadding(Screen.dp(8f), Screen.dp(12f), Screen.dp(8f), Screen.dp(12f));
    recyclerView.setClipToPadding(false);
    recyclerView.setAdapter(adapter = new Adapter());
  }

  private void select (XgramAppIcons.Icon icon) {
    if (icon == selected) {
      return;
    }
    try {
      XgramAppIcons.apply(context, icon);
      selected = icon;
      adapter.notifyDataSetChanged();
      UI.showToast(R.string.XgramAppIconChanged, android.widget.Toast.LENGTH_LONG);
      if (com.fahadali.xgram.XgramSettings.instance().iconTheme()) {
        com.fahadali.xgram.appearance.XgramThemePack.applyForIcon(this, icon);
      }
    } catch (Throwable t) {
      UI.showToast(R.string.XgramAppIconFailed, android.widget.Toast.LENGTH_SHORT);
    }
  }

  private static final float CORNER = .3f;

  /** The "theme too" switch, section titles (string ids) and icons, in grid order. */
  private final java.util.List<Object> rows = buildRows();
  private static final Object SWITCH = new Object();

  private static java.util.List<Object> buildRows () {
    java.util.List<Object> rows = new java.util.ArrayList<>();
    rows.add(SWITCH);
    rows.add(R.string.XgramIconColors);
    for (XgramAppIcons.Icon icon : XgramAppIcons.ICONS) {
      if (!icon.isStyle) {
        rows.add(icon);
      }
    }
    rows.add(R.string.XgramIconStyles);
    for (XgramAppIcons.Icon icon : XgramAppIcons.ICONS) {
      if (icon.isStyle) {
        rows.add(icon);
      }
    }
    return rows;
  }

  // "Theme too": choosing an icon also switches to its theme (Settings → XGram → Appearance → Themes has them all).

  private View newThemeSwitch (Context context) {
    FrameLayout frame = new FrameLayout(context);
    frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frame.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(8f), Screen.dp(12f));
    RippleSupport.setTransparentSelector(frame);

    LinearLayout texts = new LinearLayout(context);
    texts.setOrientation(LinearLayout.VERTICAL);
    FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    textParams.rightMargin = Screen.dp(74f);
    frame.addView(texts, textParams);

    TextView title = new TextView(context);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    title.setText(Lang.getString(R.string.XgramIconThemeToggle));
    texts.addView(title);
    TextView subtitle = new TextView(context);
    subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    subtitle.setText(Lang.getString(R.string.XgramIconThemeToggleInfo));
    LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    subtitleParams.topMargin = Screen.dp(2f);
    texts.addView(subtitle, subtitleParams);

    org.thunderdog.challegram.component.base.TogglerView toggler = new org.thunderdog.challegram.component.base.TogglerView(context);
    toggler.init(com.fahadali.xgram.XgramSettings.instance().iconTheme());
    frame.addView(toggler, new FrameLayout.LayoutParams(Screen.dp(66f), Screen.dp(48f), Gravity.RIGHT | Gravity.CENTER_VERTICAL));
    frame.setTag(toggler);

    frame.setOnClickListener(v -> {
      com.fahadali.xgram.XgramSettings settings = com.fahadali.xgram.XgramSettings.instance();
      boolean enabled = !settings.iconTheme();
      settings.setIconTheme(enabled);
      bindThemeSwitch(v, true);
      if (enabled && selected != null) {
        com.fahadali.xgram.appearance.XgramThemePack.applyForIcon(this, selected);
      }
    });
    return frame;
  }

  private void bindThemeSwitch (View view, boolean animated) {
    ViewGroup frame = (ViewGroup) view;
    frame.setBackgroundColor(Theme.getColor(ColorId.filling));
    ViewGroup texts = (ViewGroup) frame.getChildAt(0);
    ((TextView) texts.getChildAt(0)).setTextColor(Theme.getColor(ColorId.text));
    ((TextView) texts.getChildAt(1)).setTextColor(Theme.getColor(ColorId.textLight));
    ((org.thunderdog.challegram.component.base.TogglerView) frame.getTag()).setRadioEnabled(com.fahadali.xgram.XgramSettings.instance().iconTheme(), animated);
  }

  private static final class IconCell extends LinearLayout {
    private final FrameLayout iconFrame;
    private final ImageView iconView;
    private final TextView nameView;
    private boolean isSelected;

    IconCell (Context context) {
      super(context);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_HORIZONTAL);
      setPadding(0, Screen.dp(12f), 0, Screen.dp(12f));

      iconFrame = new FrameLayout(context) {
        @Override
        protected void dispatchDraw (Canvas c) {
          super.dispatchDraw(c);
          if (isSelected) {
            // The same rounded square as the preview, a little outside it.
            float inset = Screen.dp(1.5f);
            android.graphics.RectF rect = new android.graphics.RectF(inset, inset, getMeasuredWidth() - inset, getMeasuredHeight() - inset);
            float radius = rect.width() * CORNER;
            c.drawRoundRect(rect, radius, radius, Paints.getProgressPaint(Theme.getColor(ColorId.textNeutral), Screen.dp(3f)));
          }
        }
      };
      iconFrame.setWillNotDraw(false);
      iconView = new ImageView(context);
      iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        iconView.setOutlineProvider(new ViewOutlineProvider() {
          @Override
          public void getOutline (View view, Outline outline) {
            // A rounded square like the launcher's icon shape: a circle cut off the X and the glow.
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() * CORNER);
          }
        });
        iconView.setClipToOutline(true);
      }
      FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(Screen.dp(64f), Screen.dp(64f), Gravity.CENTER);
      iconFrame.addView(iconView, iconParams);
      addView(iconFrame, new LayoutParams(Screen.dp(76f), Screen.dp(76f)));

      nameView = new TextView(context);
      nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      nameView.setGravity(Gravity.CENTER);
      LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      nameParams.topMargin = Screen.dp(6f);
      addView(nameView, nameParams);
    }

    void bind (XgramAppIcons.Icon icon, boolean isSelected) {
      this.isSelected = isSelected;
      iconView.setImageResource(icon.iconRes);
      nameView.setText(Lang.getString(icon.nameRes));
      nameView.setTextColor(Theme.getColor(isSelected ? ColorId.textNeutral : ColorId.text));
      iconFrame.invalidate();
    }
  }

  private static final class Holder extends RecyclerView.ViewHolder {
    Holder (View itemView) {
      super(itemView);
    }
  }

  private final class Adapter extends RecyclerView.Adapter<Holder> {
    @Override
    public int getItemCount () {
      return rows.size();
    }

    @Override
    public int getItemViewType (int position) {
      Object row = rows.get(position);
      return row == SWITCH ? 2 : row instanceof Integer ? 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == 2) {
        return new Holder(newThemeSwitch(parent.getContext()));
      }
      if (viewType == 1) {
        TextView title = new TextView(parent.getContext());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
        title.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
        title.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(4f));
        title.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(title);
      }
      IconCell cell = new IconCell(parent.getContext());
      cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      RippleSupport.setTransparentSelector(cell);
      cell.setOnClickListener(v -> {
        Object tag = v.getTag();
        if (tag instanceof XgramAppIcons.Icon) {
          select((XgramAppIcons.Icon) tag);
        }
      });
      return new Holder(cell);
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position) {
      Object row = rows.get(position);
      if (row == SWITCH) {
        bindThemeSwitch(holder.itemView, false);
        return;
      }
      if (row instanceof Integer) {
        TextView title = (TextView) holder.itemView;
        title.setText(Lang.getString((Integer) row));
        title.setTextColor(Theme.getColor(ColorId.textNeutral));
        return;
      }
      XgramAppIcons.Icon icon = (XgramAppIcons.Icon) row;
      holder.itemView.setTag(icon);
      ((IconCell) holder.itemView).bind(icon, icon == selected);
    }
  }
}
