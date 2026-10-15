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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsThemeController;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.support.RippleSupport;

import java.util.ArrayList;
import java.util.List;

/** Settings → XGram → Appearance → Themes: the themes pack as preview cards, tap to switch. */
public final class XgramThemesController extends RecyclerViewController<Void> {
  private static final class Item {
    final String key;
    final TdlibUi.ImportedTheme theme;

    Item (String key, TdlibUi.ImportedTheme theme) {
      this.key = key;
      this.theme = theme;
    }
  }

  /** Section titles (string ids), theme items, and FOOTER for "All themes", in grid order. */
  private final List<Object> rows = new ArrayList<>();
  private static final Object FOOTER = new Object();
  private static final Object EXPRESSIVE_NOTE = new Object(); // Expressive design draws with the wallpaper colors
  private Adapter adapter;

  public XgramThemesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramThemes;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramThemes);
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
    GridLayoutManager manager = new GridLayoutManager(context, 2);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return rows.get(position) instanceof Item ? 1 : 2;
      }
    });
    recyclerView.setLayoutManager(manager);
    recyclerView.setItemAnimator(null);
    recyclerView.setPadding(Screen.dp(10f), Screen.dp(10f), Screen.dp(10f), Screen.dp(16f));
    recyclerView.setClipToPadding(false);
    recyclerView.setAdapter(adapter = new Adapter());
    XgramThemePack.cleanUpOldPack(tdlib);
    Background.instance().post(() -> {
      List<Object> loaded = new ArrayList<>();
      if (XgramExpressive.isEnabled()) {
        loaded.add(EXPRESSIVE_NOTE);
      }
      loaded.add(R.string.XgramIconThemes);
      addThemes(context, loaded, XgramThemePack.ICON_KEYS);
      loaded.add(R.string.XgramThemesPack);
      addThemes(context, loaded, XgramThemePack.PACK_KEYS);
      loaded.add(FOOTER);
      runOnUiThreadOptional(() -> {
        rows.clear();
        rows.addAll(loaded);
        adapter.notifyDataSetChanged();
      });
    });
  }

  private static void addThemes (Context context, List<Object> out, String[] keys) {
    for (String key : keys) {
      TdlibUi.ImportedTheme theme = XgramThemePack.read(context, key);
      if (theme != null) {
        out.add(new Item(key, theme));
      }
    }
  }

  private void select (Item item) {
    XgramThemePack.apply(this, item.key, item.theme, () -> {
      if (!isDestroyed()) {
        adapter.notifyDataSetChanged();
      }
    });
  }

  // A small chat: header, an incoming and an outgoing bubble and the round button, in the theme's colors.

  private static final class PreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private TdlibUi.ImportedTheme theme;
    private boolean isSelected;

    PreviewView (Context context) {
      super(context);
    }

    void set (TdlibUi.ImportedTheme theme, boolean isSelected) {
      this.theme = theme;
      this.isSelected = isSelected;
      invalidate();
    }

    private void fill (int color, float l, float t, float r, float b, float radius, Canvas c) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(color);
      rect.set(l, t, r, b);
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (theme == null) {
        return;
      }
      float w = getWidth(), h = getHeight();
      float inset = Screen.dp(4f), corner = Screen.dp(14f);
      float l = inset, t = inset, r = w - inset, b = h - inset;
      c.save();
      android.graphics.Path clip = new android.graphics.Path();
      rect.set(l, t, r, b);
      clip.addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW);
      c.clipPath(clip);
      fill(theme.getColor(ColorId.chatBackground), l, t, r, b, 0, c);
      float header = (b - t) * .24f;
      fill(theme.getColor(ColorId.headerBackground), l, t, r, t + header, 0, c);
      float u = Screen.dp(1f);
      // title and avatar in the header
      paint.setColor(theme.getColor(ColorId.headerText));
      c.drawCircle(l + header / 2f + u * 4, t + header / 2f, header * .26f, paint);
      fill(theme.getColor(ColorId.headerText), l + header + u * 6, t + header * .38f, l + (r - l) * .62f, t + header * .38f + u * 4, u * 2, c);
      // incoming bubble
      float bubbleH = (b - t) * .2f, radius = u * 9;
      float y1 = t + header + u * 10;
      fill(theme.getColor(ColorId.bubbleIn_background), l + u * 8, y1, l + (r - l) * .66f, y1 + bubbleH, radius, c);
      fill(theme.getColor(ColorId.bubbleIn_text), l + u * 16, y1 + bubbleH * .42f, l + (r - l) * .52f, y1 + bubbleH * .42f + u * 3, u * 1.5f, c);
      // outgoing bubble
      float y2 = y1 + bubbleH + u * 8;
      fill(theme.getColor(ColorId.bubbleOut_background), l + (r - l) * .38f, y2, r - u * 8, y2 + bubbleH, radius, c);
      fill(theme.getColor(ColorId.bubbleOut_text), l + (r - l) * .46f, y2 + bubbleH * .42f, r - u * 18, y2 + bubbleH * .42f + u * 3, u * 1.5f, c);
      // the round button
      paint.setColor(theme.getColor(ColorId.circleButtonRegular));
      float br = u * 11;
      c.drawCircle(r - br - u * 8, b - br - u * 8, br, paint);
      c.restore();
      if (isSelected) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Screen.dp(2.5f));
        paint.setColor(Theme.getColor(ColorId.textNeutral));
        rect.set(Screen.dp(1.5f), Screen.dp(1.5f), w - Screen.dp(1.5f), h - Screen.dp(1.5f));
        c.drawRoundRect(rect, corner + Screen.dp(2f), corner + Screen.dp(2f), paint);
      }
    }
  }

  private static final class Cell extends LinearLayout {
    final PreviewView preview;
    final TextView name;

    Cell (Context context) {
      super(context);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_HORIZONTAL);
      setPadding(Screen.dp(6f), Screen.dp(6f), Screen.dp(6f), Screen.dp(10f));
      preview = new PreviewView(context);
      addView(preview, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(132f)));
      name = new TextView(context);
      name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      name.setGravity(Gravity.CENTER);
      LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.topMargin = Screen.dp(6f);
      addView(name, params);
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
      return row instanceof Item ? 0 : row == FOOTER ? 1 : row == EXPRESSIVE_NOTE ? 3 : 2;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == 3) {
        TextView note = new TextView(parent.getContext());
        XgramExpressive.applyText(note, XgramExpressive.TS_BODY_M, false);
        note.setPadding(Screen.dp(10f), Screen.dp(6f), Screen.dp(10f), Screen.dp(6f));
        note.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(note);
      }
      if (viewType == 2) {
        TextView title = new TextView(parent.getContext());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
        title.setTypeface(Fonts.getRobotoMedium());
        title.setPadding(Screen.dp(10f), Screen.dp(14f), Screen.dp(10f), Screen.dp(2f));
        title.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(title);
      }
      if (viewType == 1) {
        TextView more = new TextView(parent.getContext());
        more.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        more.setTypeface(Fonts.getRobotoMedium());
        more.setGravity(Gravity.CENTER);
        more.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(14f));
        more.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        RippleSupport.setTransparentSelector(more);
        more.setOnClickListener(v -> navigateTo(new SettingsThemeController(context, tdlib)));
        return new Holder(more);
      }
      Cell cell = new Cell(parent.getContext());
      cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      RippleSupport.setTransparentSelector(cell);
      cell.setOnClickListener(v -> {
        Object tag = v.getTag();
        if (tag instanceof Item) {
          select((Item) tag);
        }
      });
      return new Holder(cell);
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position) {
      Object row = rows.get(position);
      if (row == EXPRESSIVE_NOTE) {
        TextView note = (TextView) holder.itemView;
        note.setText(Lang.getString(R.string.XgramExpressiveThemesNote));
        note.setTextColor(Theme.getColor(ColorId.textLight));
        return;
      }
      if (row == FOOTER) {
        TextView more = (TextView) holder.itemView;
        more.setText(Lang.getString(R.string.XgramAllThemes));
        more.setTextColor(Theme.getColor(ColorId.textNeutral));
        return;
      }
      if (row instanceof Integer) {
        TextView title = (TextView) holder.itemView;
        title.setText(Lang.getString((Integer) row));
        title.setTextColor(Theme.getColor(ColorId.textNeutral));
        return;
      }
      Item item = (Item) row;
      Cell cell = (Cell) holder.itemView;
      cell.setTag(item);
      boolean selected = XgramThemePack.isCurrent(item.key);
      cell.preview.set(item.theme, selected);
      cell.name.setText(item.theme.name);
      cell.name.setTextColor(Theme.getColor(selected ? ColorId.textNeutral : ColorId.text));
    }
  }
}
