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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.fahadali.xgram.XgramApp;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.widget.FrameLayoutFix;

/** Icons: a card per pack with ten of its icons, so packs can be compared before XGram restarts with one. */
public class XgramIconPacksController extends ViewController<Void> {
  public XgramIconPacksController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramIconPacks;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramIconPack);
  }

  @Override
  protected int getBackButton () {
    return org.thunderdog.challegram.navigation.BackHeaderButton.TYPE_BACK;
  }

  private static int[] preview (int pack) {
    return XgramIconPack.preview(pack);
  }

  private static int description (int pack) {
    return XgramIconPack.description(pack);
  }

  // XGram M3E (round 5): a list with the large title, the packs as selectable cards (surfaceBright, secondaryContainer
  // with a check badge for the pack in use; 20dp corners, 16dp pressed), two columns from 600dp, the note as a
  // description, and the restart confirmed in an M3 dialog.

  private static final float XGRAM_MIN_CELL = 280f;
  private static final Object XGRAM_TOP = new Object(), XGRAM_INFO = new Object();

  private View xgramCreateView (Context context) {
    final java.util.List<Object> rows = new java.util.ArrayList<>();
    rows.add(XGRAM_TOP);
    for (int pack : XgramIconPack.PACKS) {
      rows.add(pack);
    }
    rows.add(XGRAM_INFO);
    final int current = XgramIconPack.current();

    androidx.recyclerview.widget.RecyclerView recyclerView = new androidx.recyclerview.widget.RecyclerView(context);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(recyclerView, ColorId.background, this);
    recyclerView.setVerticalScrollBarEnabled(false);
    recyclerView.setItemAnimator(null);
    recyclerView.setClipToPadding(false);
    recyclerView.setPadding(0, 0, 0, Screen.dp(16f));
    final androidx.recyclerview.widget.GridLayoutManager manager = new androidx.recyclerview.widget.GridLayoutManager(context, XgramR5Security.spanCount(Screen.currentWidth(), XGRAM_MIN_CELL, 1, 2));
    manager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return rows.get(position) instanceof Integer ? 1 : manager.getSpanCount();
      }
    });
    recyclerView.setLayoutManager(manager);
    XgramR5Security.attachAdaptiveGrid(recyclerView, manager, XGRAM_MIN_CELL, 1, 2, position -> rows.get(position) instanceof Integer);
    recyclerView.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
      @Override
      public int getItemCount () {
        return rows.size();
      }

      @Override
      public int getItemViewType (int position) {
        Object row = rows.get(position);
        return row == XGRAM_TOP ? 2 : row == XGRAM_INFO ? 1 : 0;
      }

      @androidx.annotation.NonNull
      @Override
      public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder (@androidx.annotation.NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == 2) {
          view = new View(parent.getContext());
          view.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(8f)));
        } else if (viewType == 1) {
          view = XgramR5Security.newDescription(parent.getContext());
        } else {
          XgramPackCard card = new XgramPackCard(parent.getContext());
          card.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(120f)));
          card.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            choose(card.pack);
          });
          view = card;
        }
        return new androidx.recyclerview.widget.RecyclerView.ViewHolder(view) { };
      }

      @Override
      public void onBindViewHolder (@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (row == XGRAM_INFO) {
          XgramR5Security.bindDescription((TextView) holder.itemView, Lang.getString(R.string.XgramIconPackInfo));
        } else if (row instanceof Integer) {
          int pack = (Integer) row;
          ((XgramPackCard) holder.itemView).bind(pack, pack == current);
        }
      }
    });
    XgramM3Bars.attachLargeTitle(this, recyclerView);
    return recyclerView;
  }

  private void xgramChoose (int pack) {
    android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(context(), Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.XgramR5SecurityUseIconPack, Lang.getString(XgramIconPack.name(pack))));
    b.setMessage(Lang.getString(R.string.XgramIconPackInfo));
    b.setPositiveButton(Lang.getString(R.string.AppUpdateRestart), (dialog, which) -> {
      XgramIconPack.set(pack);
      UI.post(() -> XgramApp.restart(context()), 250);
    });
    b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    showAlert(b);
  }

  /** XGram M3E: a pack as a selectable card: titleMedium name, bodyMedium words, ten of its icons, a check badge. */
  private static final class XgramPackCard extends View {
    private static TextPaint titlePaint, subtitlePaint;
    private final XgramR5Security.SelectableCard card;
    private int pack = -1;
    private Drawable[] icons = new Drawable[0];
    private String title = "", subtitle = "";
    private CharSequence titleShown = "", subtitleShown = "";
    private int ellipsizedWidth = -1;

    XgramPackCard (Context context) {
      super(context);
      card = XgramR5Security.attachSelectableCard(this);
      setClickable(true);
      setFocusable(true);
    }

    private static TextPaint titlePaint () {
      if (titlePaint == null) {
        titlePaint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_TITLE_M, false));
        titlePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M)));
        titlePaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_TITLE_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_TITLE_M));
      }
      return titlePaint;
    }

    private static TextPaint subtitlePaint () {
      if (subtitlePaint == null) {
        subtitlePaint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_BODY_M, false));
        subtitlePaint.setTextSize(Screen.dp(XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M)));
        subtitlePaint.setLetterSpacing(XgramExpressive.trackingSp(XgramExpressive.TS_BODY_M) / XgramExpressive.textSizeSp(XgramExpressive.TS_BODY_M));
      }
      return subtitlePaint;
    }

    void bind (int pack, boolean selected) {
      if (this.pack != pack) {
        this.pack = pack;
        int[] res = preview(pack);
        icons = new Drawable[res.length];
        for (int i = 0; i < res.length; i++) {
          // Straight from resources: Drawables.get would turn Telegram X's icons into the current pack's.
          Drawable d = ResourcesCompat.getDrawable(getResources(), res[i], null);
          icons[i] = d != null ? d.mutate() : null;
        }
        title = Lang.getString(XgramIconPack.name(pack));
        subtitle = Lang.getString(description(pack));
        ellipsizedWidth = -1;
        setContentDescription(title + ". " + subtitle);
      }
      setSelected(selected);
      card.setChecked(selected, false);
      invalidate();
    }

    private int textMax () {
      return getWidth() - Screen.dp(16f) * 2 - Screen.dp(XgramR5Security.BADGE_SIZE + 8f);
    }

    @Override
    protected void onDraw (Canvas c) {
      final int width = getWidth(), height = getHeight();
      final int padding = Screen.dp(16f);
      final int max = textMax();
      if (max != ellipsizedWidth && max > 0) {
        // Ellipsized when the width changes, not per frame
        ellipsizedWidth = max;
        titleShown = TextUtils.ellipsize(title, titlePaint(), max, TextUtils.TruncateAt.END);
        subtitleShown = TextUtils.ellipsize(subtitle, subtitlePaint(), max, TextUtils.TruncateAt.END);
      }
      final boolean rtl = Lang.rtl();
      final TextPaint t = titlePaint(), s = subtitlePaint();
      t.setColor(card.contentColor(false));
      s.setColor(card.contentColor(true));
      final float titleX = rtl ? width - padding - t.measureText(titleShown, 0, titleShown.length()) : padding;
      final float subtitleX = rtl ? width - padding - s.measureText(subtitleShown, 0, subtitleShown.length()) : padding;
      c.drawText(titleShown, 0, titleShown.length(), titleX, Screen.dp(36f), t);
      c.drawText(subtitleShown, 0, subtitleShown.length(), subtitleX, Screen.dp(58f), s);

      // The icons, spread over the card's width
      final int iconSize = Screen.dp(24f);
      final int available = width - padding * 2;
      final int count = Math.min(icons.length, Math.max(1, (available + Screen.dp(8f)) / (iconSize + Screen.dp(8f))));
      final float step = count > 1 ? (available - iconSize) / (float) (count - 1) : 0;
      final int top = height - Screen.dp(20f) - iconSize;
      final android.graphics.Paint iconPaint = Paints.getPorterDuffPaint(card.contentColor(true));
      for (int i = 0; i < count; i++) {
        Drawable d = icons[rtl ? count - 1 - i : i];
        if (d == null) {
          continue;
        }
        float x = padding + step * i + (iconSize - d.getMinimumWidth()) / 2f;
        float y = top + (iconSize - d.getMinimumHeight()) / 2f;
        Drawables.draw(c, d, x, y, iconPaint);
      }

      final float inset = Screen.dpf(16f + XgramR5Security.BADGE_SIZE / 2f);
      card.drawBadge(c, rtl ? inset : width - inset, Screen.dpf(16f + XgramR5Security.BADGE_SIZE / 2f));
    }
  }

  @Override
  protected View onCreateView (Context context) {
    if (XgramExpressive.isEnabled()) {
      return xgramCreateView(context);
    }
    ScrollView scrollView = new ScrollView(context);
    scrollView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(scrollView, ColorId.background, this);

    LinearLayout list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    scrollView.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    int current = XgramIconPack.current();
    for (int pack : XgramIconPack.PACKS) {
      PackCard card = new PackCard(context, pack, pack == current);
      addThemeInvalidateListener(card);
      Views.setClickable(card);
      card.setOnClickListener(v -> choose(pack));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(112f));
      params.bottomMargin = Screen.dp(10f);
      list.addView(card, params);
    }

    TextView info = new NoScrollTextView(context);
    info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    info.setTextColor(Theme.textDecentColor());
    addThemeTextDecentColorListener(info);
    info.setPadding(Screen.dp(4f), Screen.dp(2f), Screen.dp(4f), 0);
    info.setText(Lang.getString(R.string.XgramIconPackInfo));
    list.addView(info);
    return scrollView;
  }

  private void choose (int pack) {
    if (pack == XgramIconPack.current()) {
      return;
    }
    if (XgramExpressive.isEnabled()) {
      xgramChoose(pack); // XGram M3E: an M3 dialog, no option sheet with a Cancel row
      return;
    }
    showOptions(Lang.getString(R.string.XgramIconPackInfo), new int[] {R.id.btn_xgramIconPackOption, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramIconPackApply, Lang.getString(XgramIconPack.name(pack))), Lang.getString(R.string.Cancel)},
      (itemView, id) -> {
        if (id == R.id.btn_xgramIconPackOption) {
          XgramIconPack.set(pack);
          UI.post(() -> XgramApp.restart(context()), 250);
        }
        return true;
      });
  }

  /** One pack: name, what it looks like in words, ten of its icons, a ring when it is the one in use. */
  private static final class PackCard extends View {
    private final int pack;
    private final boolean selected;
    private final Drawable[] icons;
    private final String title, subtitle;

    PackCard (Context context, int pack, boolean selected) {
      super(context);
      this.pack = pack;
      this.selected = selected;
      int[] res = preview(pack);
      icons = new Drawable[res.length];
      for (int i = 0; i < res.length; i++) {
        // Straight from resources: Drawables.get would turn Telegram X's icons into the current pack's.
        Drawable d = ResourcesCompat.getDrawable(context.getResources(), res[i], null);
        icons[i] = d != null ? d.mutate() : null;
      }
      title = Lang.getString(XgramIconPack.name(pack));
      subtitle = Lang.getString(description(pack));
    }

    @Override
    protected void onDraw (Canvas c) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      RectF rect = Paints.getRectF();
      float inset = Screen.dp(1f);
      rect.set(inset, inset, width - inset, height - inset);
      float radius = Screen.dp(14f);
      c.drawRoundRect(rect, radius, radius, Paints.fillingPaint(Theme.fillingColor()));
      if (selected) {
        c.drawRoundRect(rect, radius, radius, Paints.getProgressPaint(Theme.getColor(ColorId.checkActive), Screen.dp(2f)));
      }

      int padding = Screen.dp(16f);
      TextPaint titlePaint = Paints.getMediumTextPaint(16f, Theme.textAccentColor(), false);
      TextPaint subtitlePaint = Paints.getRegularTextPaint(13f, Theme.textDecentColor());
      int textMax = width - padding * 2 - Screen.dp(28f);
      CharSequence t = TextUtils.ellipsize(title, titlePaint, textMax, TextUtils.TruncateAt.END);
      CharSequence s = TextUtils.ellipsize(subtitle, subtitlePaint, textMax, TextUtils.TruncateAt.END);
      c.drawText(t, 0, t.length(), padding, Screen.dp(30f), titlePaint);
      c.drawText(s, 0, s.length(), padding, Screen.dp(50f), subtitlePaint);

      if (selected) {
        Drawable check = Drawables.get(getResources(), R.drawable.baseline_check_circle_24);
        if (check != null) {
          Drawables.draw(c, check, width - padding - check.getMinimumWidth(), Screen.dp(14f), Paints.getPorterDuffPaint(Theme.getColor(ColorId.checkActive)));
        }
      }

      // The icons, spread over the card's width.
      int iconSize = Screen.dp(24f);
      int available = width - padding * 2;
      int count = Math.min(icons.length, Math.max(1, (available + Screen.dp(8f)) / (iconSize + Screen.dp(8f))));
      float step = count > 1 ? (available - iconSize) / (float) (count - 1) : 0;
      int top = height - Screen.dp(18f) - iconSize;
      android.graphics.Paint iconPaint = Paints.getPorterDuffPaint(Theme.iconColor());
      for (int i = 0; i < count; i++) {
        Drawable d = icons[i];
        if (d == null) {
          continue;
        }
        float x = padding + step * i + (iconSize - d.getMinimumWidth()) / 2f;
        float y = top + (iconSize - d.getMinimumHeight()) / 2f;
        Drawables.draw(c, d, x, y, iconPaint);
      }
    }
  }
}
