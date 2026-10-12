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

  @Override
  protected View onCreateView (Context context) {
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
