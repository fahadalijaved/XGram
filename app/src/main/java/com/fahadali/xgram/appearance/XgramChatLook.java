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
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.SliderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.Collections;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

/**
 * Chat look (AyuGram, exteraGram): sticker and emoji sizes, bubble corners and a Telegram-style tail. The rows sit
 * under Telegram X's text size slider, over its chat preview, so every change shows at once.
 */
public final class XgramChatLook {
  private XgramChatLook () { }

  public static final int[] STICKER_SCALES = {50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 165, 180, 200};
  /** 0: emoji stay text-sized inside the bubble (Telegram X's "Big emoji" off). */
  public static final int[] EMOJI_SCALES = {0, 50, 75, 100, 125, 150, 175, 200, 250, 300};
  /** -1: the theme's corners. */
  public static final int[] BUBBLE_RADII = {-1, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24};

  private static final int VALUE_PERCENT = 0, VALUE_RADIUS = 1, VALUE_EMOJI = 2;

  private interface IntSetting {
    int get ();
    void set (int value);
  }

  /**
   * @param rebuild relayouts the preview messages
   * @param reload loads them again (needed when emoji-only messages switch between bubble and big emoji)
   */
  public static void addRows (ViewController<?> c, LinearLayout bottomWrap, Runnable rebuild, Runnable reload) {
    XgramSettings settings = XgramSettings.instance();
    Context context = c.context();
    addSlider(c, bottomWrap, R.string.XgramStickerSize, STICKER_SCALES, new IntSetting() {
      @Override public int get () { return settings.stickerScale(); }
      @Override public void set (int value) { settings.setStickerScale(value); }
    }, VALUE_PERCENT, rebuild);
    addSlider(c, bottomWrap, R.string.XgramEmojiSize, EMOJI_SCALES, new IntSetting() {
      @Override
      public int get () {
        return Settings.instance().useBigEmoji() ? settings.emojiScale() : 0;
      }

      @Override
      public void set (int value) {
        boolean wasBig = Settings.instance().useBigEmoji();
        boolean big = value != 0;
        if (big) {
          settings.setEmojiScale(value);
        }
        if (big != wasBig) {
          Settings.instance().setUseBigEmoji(big); // the same switch as Settings → Stickers and emoji → Big emoji
          reload.run();
        }
      }
    }, VALUE_EMOJI, rebuild);
    addSlider(c, bottomWrap, R.string.XgramBubbleCorners, BUBBLE_RADII, new IntSetting() {
      @Override public int get () { return settings.bubbleRadius(); }
      @Override public void set (int value) { settings.setBubbleRadius(value); }
    }, VALUE_RADIUS, rebuild);
    addSlider(c, bottomWrap, R.string.XgramBubbleMergedCorners, BUBBLE_RADII, new IntSetting() {
      @Override public int get () { return settings.bubbleMergeRadius(); }
      @Override public void set (int value) { settings.setBubbleMergeRadius(value); }
    }, VALUE_RADIUS, rebuild);

    SettingView tail = new SettingView(context, c.tdlib());
    tail.setId(R.id.btn_xgramBubbleTail);
    tail.setType(SettingView.TYPE_RADIO);
    tail.setName(R.string.XgramBubbleTail);
    tail.getToggler().setRadioEnabled(settings.bubbleTail(), false);
    tail.addThemeListeners(c);
    tail.setOnClickListener(v -> {
      settings.setBubbleTail(tail.getToggler().toggle(true));
      rebuild.run();
    });
    tail.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(52f)));
    bottomWrap.addView(tail);
  }

  private static int indexOf (int[] values, int value) {
    int best = 0;
    for (int i = 0; i < values.length; i++) {
      if (Math.abs(values[i] - value) < Math.abs(values[best] - value)) {
        best = i;
      }
    }
    return best;
  }

  private static String valueText (int value, int kind) {
    switch (kind) {
      case VALUE_RADIUS:
        return value < 0 ? Lang.getString(R.string.XgramLookTheme) : Lang.getString(R.string.XgramLookDp, value);
      case VALUE_EMOJI:
        if (value == 0) {
          return Lang.getString(R.string.XgramLookEmojiInBubble);
        }
        return Lang.getString(R.string.XgramLookPercent, value);
      default:
        return Lang.getString(R.string.XgramLookPercent, value);
    }
  }

  private static void addSlider (ViewController<?> c, LinearLayout bottomWrap, int titleRes, int[] values, IntSetting setting, int kind, Runnable rebuild) {
    Context context = c.context();
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    ViewSupport.setThemedBackground(row, ColorId.filling, c);

    // Title on the start side, the value on the end side.
    FrameLayoutFix labels = new FrameLayoutFix(context);
    labels.setPadding(Screen.dp(20f), Screen.dp(12f), Screen.dp(20f), 0);
    TextView title = new NoScrollTextView(context);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    title.setTextColor(Theme.textAccentColor());
    c.addThemeTextAccentColorListener(title);
    title.setText(Lang.getString(titleRes));
    labels.addView(title, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Lang.gravity()));
    TextView value = new NoScrollTextView(context);
    value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    value.setTypeface(Fonts.getRobotoMedium());
    value.setTextColor(Theme.getColor(ColorId.textLink));
    c.addThemeTextColorListener(value, ColorId.textLink);
    value.setText(valueText(setting.get(), kind));
    labels.addView(value, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
    row.addView(labels, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    SliderView slider = new SliderView(context);
    c.addThemeInvalidateListener(slider);
    slider.setSlideEnabled(true, false);
    slider.setValueCount(values.length);
    slider.setValue((float) indexOf(values, setting.get()) / (float) (values.length - 1));
    slider.setListener(new SliderView.Listener() {
      @Override
      public void onSetStateChanged (SliderView view, boolean isSetting) {
        if (!isSetting) {
          int index = Math.round(view.getValue() * (float) (values.length - 1));
          view.animateValue((float) index / (float) (values.length - 1));
        }
      }

      @Override
      public void onValueChanged (SliderView view, float factor) {
        int newValue = values[Math.round(factor * (float) (values.length - 1))];
        if (newValue != setting.get()) {
          setting.set(newValue);
          value.setText(valueText(newValue, kind));
          rebuild.run();
        }
      }

      @Override
      public boolean allowSliderChanges (SliderView view) {
        return true;
      }
    });
    slider.setForceBackgroundColorId(ColorId.sliderInactive);
    slider.setColorId(ColorId.sliderActive, false);
    // Well inside the screen, and the system back gesture leaves the slider alone.
    slider.setPadding(Screen.dp(28f), 0, Screen.dp(28f), 0);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      slider.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
        v.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, right - left, bottom - top)))
      );
    }
    row.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f)));

    bottomWrap.addView(row);
  }

  // Bubble shape

  /** Bubbles differ from what Telegram X's prebaked corner shadows fit. */
  public static boolean hasCustomShape (boolean tail) {
    XgramSettings settings = XgramSettings.instance();
    return tail || settings.bubbleRadius() >= 0 || settings.bubbleMergeRadius() >= 0;
  }

  private static Paint shadowPaint;

  /** A soft shadow around the bubble's own path, drawn outside it only. Call under the path's lock. */
  @RequiresApi(Build.VERSION_CODES.P)
  public static void drawShadow (Canvas c, Path bubblePath, float factor) {
    if (shadowPaint == null) {
      shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      shadowPaint.setStyle(Paint.Style.FILL);
      shadowPaint.setColor(0xff000000);
    }
    shadowPaint.setShadowLayer(Screen.dp(1.5f), 0, Screen.dp(.5f), ColorUtils.alphaColor(Math.min(1f, factor) * .2f, 0xff000000));
    int save = c.save();
    c.clipOutPath(bubblePath);
    c.drawPath(bubblePath, shadowPaint);
    c.restoreToCount(save);
  }

  private static final Path tailPath = new Path();

  /**
   * Adds a Telegram-style tail at the bottom corner on the sender's side of a bubble whose corner there is square.
   * Called under the bubble path's lock.
   */
  public static void addTail (Path bubblePath, RectF rect, boolean right) {
    float w = Screen.dp(7f);
    float h = Screen.dp(16f);
    float overlap = Screen.dp(3f);
    float bottom = rect.bottom;
    synchronized (tailPath) {
      tailPath.reset();
      if (right) {
        float edge = rect.right;
        tailPath.moveTo(edge, bottom - h);
        tailPath.cubicTo(edge, bottom - h * .35f, edge + w * .35f, bottom - Screen.dp(1f), edge + w, bottom);
        tailPath.lineTo(edge - overlap, bottom);
        tailPath.lineTo(edge - overlap, bottom - h);
      } else {
        float edge = rect.left;
        tailPath.moveTo(edge, bottom - h);
        tailPath.cubicTo(edge, bottom - h * .35f, edge - w * .35f, bottom - Screen.dp(1f), edge - w, bottom);
        tailPath.lineTo(edge + overlap, bottom);
        tailPath.lineTo(edge + overlap, bottom - h);
      }
      tailPath.close();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        bubblePath.op(tailPath, Path.Op.UNION);
      } else {
        bubblePath.addPath(tailPath);
      }
    }
  }
}
