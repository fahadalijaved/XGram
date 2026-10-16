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

import com.fahadali.xgram.XgramSettingDeps;

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
    xgram = XgramExpressive.isEnabled();
    if (xgram) {
      xgramCreateView(context, recyclerView);
    } else {
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
    }
    XgramThemePack.cleanUpOldPack(tdlib);
    Background.instance().post(() -> {
      List<Object> loaded = new ArrayList<>();
      if (XgramExpressive.isEnabled()) {
        loaded.add(EXPRESSIVE_NOTE);
        if (XgramSettingDeps.themesLocked()) {
          loaded.add(XGRAM_TURN_OFF); // XGram M3E: the way out of the lock, Appearance's own confirm
        }
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
        if (xgram) {
          adapter.notifyItemRangeChanged(0, rows.size(), XGRAM_SELECTION); // rebound in place: the cards spring
        } else {
          adapter.notifyDataSetChanged();
        }
      }
    });
  }

  // XGram M3E (round 5): a large title over an adaptive grid of selectable cards (at least 2 columns of 168dp, 4 on
  // the unfolded Fold), the note as a description, titleSmall headers, "All themes" as a segmented tile with a chevron.
  // While Expressive design locks the themes (XgramSettingDeps.themesLocked) the cards are drawn disabled and a tonal
  // button under the note turns Expressive design off with Appearance's own confirm.

  private boolean xgram;
  private static final Object XGRAM_SELECTION = new Object();
  private static final Object XGRAM_TURN_OFF = new Object();
  private static final float XGRAM_MIN_CELL = 168f;
  private static final int XGRAM_TYPE_TURN_OFF = 4;

  private void xgramCreateView (Context context, CustomRecyclerView recyclerView) {
    final GridLayoutManager manager = new GridLayoutManager(context, XgramR5Security.spanCount(Screen.currentWidth(), XGRAM_MIN_CELL, 2));
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return position < rows.size() && rows.get(position) instanceof Item ? 1 : manager.getSpanCount();
      }
    });
    recyclerView.setLayoutManager(manager);
    recyclerView.setItemAnimator(null);
    recyclerView.setPadding(0, 0, 0, Screen.dp(16f));
    recyclerView.setClipToPadding(false);
    XgramR5Security.attachAdaptiveGrid(recyclerView, manager, XGRAM_MIN_CELL, 2);
    recyclerView.setAdapter(adapter = new Adapter());
    XgramM3Bars.attachLargeTitle(this, recyclerView);
  }

  private void xgramConfirmTurnOff () {
    // Appearance's confirm (AppearancePage.expressive): the app restarts with the classic pages and these themes.
    openAlert(R.string.XgramExpressive, Lang.getString(R.string.XgramExpressiveInfo), Lang.getString(R.string.AppUpdateRestart),
      (dialog, which) -> XgramExpressive.setEnabled(this, false), 0);
  }

  private View xgramNewTurnOff (Context context) {
    android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
    frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frame.setPadding(Screen.dp(16f), Screen.dp(4f), Screen.dp(16f), Screen.dp(8f));
    XgramM3Sheets.FilledButton button = new XgramM3Sheets.FilledButton(context, XgramExpressive.SECONDARY_CONTAINER, XgramExpressive.ON_SECONDARY_CONTAINER);
    button.setText(Lang.getString(R.string.XgramR5SecurityTurnOffExpressive));
    button.setOnClickListener(v -> xgramConfirmTurnOff());
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f), Gravity.CENTER_HORIZONTAL);
    params.topMargin = params.bottomMargin = Screen.dp(4f);
    frame.addView(button, params);
    return frame;
  }

  private View xgramNewAllThemes (Context context) {
    android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
    RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = params.rightMargin = Screen.dp(16f);
    params.topMargin = Screen.dp(16f);
    frame.setLayoutParams(params);
    frame.setMinimumHeight(Screen.dp(56f));
    XgramExpressive.MorphDrawable tile = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
    tile.setColorRole(XgramExpressive.SURFACE_BRIGHT);
    tile.setSegment(XgramExpressive.SEGMENT_SINGLE);
    tile.setHost(frame);
    frame.setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE, tile, tile.newMask()));
    frame.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
    TextView title = new TextView(context);
    XgramExpressive.applyText(title, XgramExpressive.TS_BODY_L, false);
    title.setMaxLines(2);
    frame.addView(title, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
    android.widget.ImageView chevron = new android.widget.ImageView(context);
    chevron.setScaleType(android.widget.ImageView.ScaleType.CENTER);
    chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    frame.addView(chevron, new android.widget.FrameLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f), Gravity.CENTER_VERTICAL));
    frame.setOnClickListener(v -> navigateTo(new SettingsThemeController(context, tdlib)));
    return frame;
  }

  private void xgramBindAllThemes (View view) {
    android.widget.FrameLayout frame = (android.widget.FrameLayout) view;
    final boolean rtl = Lang.rtl();
    TextView title = (TextView) frame.getChildAt(0);
    title.setText(Lang.getString(R.string.XgramAllThemes));
    title.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE));
    title.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
    android.widget.FrameLayout.LayoutParams titleParams = (android.widget.FrameLayout.LayoutParams) title.getLayoutParams();
    titleParams.leftMargin = Screen.dp(rtl ? 56f : 16f);
    titleParams.rightMargin = Screen.dp(rtl ? 16f : 56f);
    titleParams.topMargin = titleParams.bottomMargin = Screen.dp(12f);
    title.setLayoutParams(titleParams);
    android.widget.ImageView chevron = (android.widget.ImageView) frame.getChildAt(1);
    chevron.setImageResource(XgramIconPack.map(rtl ? R.drawable.baseline_keyboard_arrow_left_24 : R.drawable.round_keyboard_arrow_right_24));
    chevron.setColorFilter(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    android.widget.FrameLayout.LayoutParams chevronParams = (android.widget.FrameLayout.LayoutParams) chevron.getLayoutParams();
    chevronParams.gravity = Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT);
    chevronParams.leftMargin = chevronParams.rightMargin = Screen.dp(16f);
    chevron.setLayoutParams(chevronParams);
  }

  /** The preview clipped to CORNER_M (the path is built when the size changes, not per frame). */
  private static final class XgramPreviewView extends PreviewView {
    private final android.graphics.Path clip = new android.graphics.Path();
    private final RectF clipRect = new RectF();

    XgramPreviewView (Context context) {
      super(context);
      setXgram();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      clip.rewind();
      clipRect.set(0, 0, w, h);
      float corner = Math.min(Screen.dpf(XgramExpressive.CORNER_M), Math.min(w, h) / 2f);
      clip.addRoundRect(clipRect, corner, corner, android.graphics.Path.Direction.CW);
    }

    @Override
    android.graphics.Path xgramClip () {
      return clip;
    }
  }

  /** A theme as a selectable card: 8dp padding, the preview, its name in labelLarge, the check badge on the preview. */
  private static final class XgramCell extends LinearLayout {
    final XgramPreviewView preview;
    final TextView name;
    final XgramR5Security.SelectableCard card;

    XgramCell (Context context) {
      super(context);
      setOrientation(VERTICAL);
      setGravity(Gravity.CENTER_HORIZONTAL);
      setPadding(Screen.dp(8f), Screen.dp(8f), Screen.dp(8f), Screen.dp(12f));
      card = XgramR5Security.attachSelectableCard(this);
      preview = new XgramPreviewView(context);
      preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      addView(preview, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(132f)));
      name = new TextView(context);
      XgramExpressive.applyText(name, XgramExpressive.TS_LABEL_L, false);
      name.setGravity(Gravity.CENTER);
      name.setMaxLines(2);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.topMargin = Screen.dp(8f);
      addView(name, params);
      setWillNotDraw(false);
    }

    void bind (Item item, boolean selected, boolean locked, boolean animated) {
      preview.set(item.theme, false);
      name.setText(item.theme.name);
      setContentDescription(item.theme.name);
      card.setChecked(selected, animated);
      card.setDisabled(locked);
      setSelected(selected);
      setEnabled(!locked);
      setClickable(!locked);
      setAlpha(locked ? XgramExpressive.DISABLED_CONTENT : 1f);
      name.setTextColor(XgramExpressive.color(selected ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE));
      invalidate();
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      super.dispatchDraw(c);
      if (card.badgeFactor() > 0f) {
        final float inset = Screen.dpf(XgramR5Security.BADGE_SIZE / 2f + 6f);
        final float cx = Lang.rtl() ? preview.getLeft() + inset : preview.getRight() - inset;
        card.drawBadge(c, cx, preview.getTop() + inset);
      }
    }
  }

  // A small chat: header, an incoming and an outgoing bubble and the round button, in the theme's colors.

  private static class PreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private TdlibUi.ImportedTheme theme;
    private boolean isSelected;
    private boolean xgram; // XGram M3E: no inset or ring, clipped by the card cell's cached path

    PreviewView (Context context) {
      super(context);
    }

    final void setXgram () {
      this.xgram = true;
    }

    /** The clip of the Expressive preview; null for Telegram X's (built in onDraw). */
    android.graphics.Path xgramClip () {
      return null;
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
      float inset = xgram ? 0f : Screen.dp(4f), corner = Screen.dp(14f);
      float l = inset, t = inset, r = w - inset, b = h - inset;
      c.save();
      android.graphics.Path clip = xgram ? xgramClip() : null;
      if (clip == null) {
        clip = new android.graphics.Path();
        rect.set(l, t, r, b);
        clip.addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW);
      }
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
      if (isSelected && !xgram) {
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
      if (row == XGRAM_TURN_OFF) {
        return XGRAM_TYPE_TURN_OFF;
      }
      return row instanceof Item ? 0 : row == FOOTER ? 1 : row == EXPRESSIVE_NOTE ? 3 : 2;
    }

    private Holder xgramCreateHolder (@NonNull ViewGroup parent, int viewType) {
      Context context = parent.getContext();
      switch (viewType) {
        case XGRAM_TYPE_TURN_OFF:
          return new Holder(xgramNewTurnOff(context));
        case 3:
          return new Holder(XgramR5Security.newDescription(context));
        case 2:
          return new Holder(XgramR5Security.newSectionHeader(context));
        case 1:
          return new Holder(xgramNewAllThemes(context));
      }
      XgramCell cell = new XgramCell(context);
      cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      cell.setOnClickListener(v -> {
        Object tag = v.getTag();
        if (tag instanceof Item && !XgramSettingDeps.themesLocked()) {
          v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
          select((Item) tag);
        }
      });
      return new Holder(cell);
    }

    private void xgramBind (@NonNull Holder holder, int position, boolean animated) {
      Object row = rows.get(position);
      if (row == XGRAM_TURN_OFF) {
        return; // the filled button reads its roles when drawn
      }
      if (row == EXPRESSIVE_NOTE) {
        XgramR5Security.bindDescription((TextView) holder.itemView, Lang.getString(R.string.XgramExpressiveThemesNote));
        return;
      }
      if (row == FOOTER) {
        xgramBindAllThemes(holder.itemView);
        return;
      }
      if (row instanceof Integer) {
        XgramR5Security.bindSectionHeader((TextView) holder.itemView, Lang.getString((Integer) row), position == 0);
        return;
      }
      Item item = (Item) row;
      XgramCell cell = (XgramCell) holder.itemView;
      cell.setTag(item);
      cell.bind(item, XgramThemePack.isCurrent(item.key), XgramSettingDeps.themesLocked(), animated);
    }

    @Override
    public void onBindViewHolder (@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
      if (xgram && !payloads.isEmpty()) {
        xgramBind(holder, position, true);
        return;
      }
      super.onBindViewHolder(holder, position, payloads);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (xgram) {
        return xgramCreateHolder(parent, viewType);
      }
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
      if (xgram) {
        xgramBind(holder, position, false);
        return;
      }
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
