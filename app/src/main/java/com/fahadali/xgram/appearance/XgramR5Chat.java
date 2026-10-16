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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

/**
 * XGram M3E round 5, the chat canvas: helpers for the chat screen and its preview modes (wallpaper picker, text size,
 * event log, search) with Expressive design. Everything here is only used while {@link XgramExpressive#isEnabled()}.
 */
public final class XgramR5Chat {
  private XgramR5Chat () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // The chat chip: one spec for the date chips, merged forum topic headers, the unread divider and service pills

  /** Height (dp) of a one-line chat chip: labelMedium centred in it, full corners. */
  public static final float CHIP_HEIGHT = 26f;
  /** Horizontal padding (dp) between the chip's edge and its label. */
  public static final float CHIP_PADDING = 12f;

  // Strings

  /**
   * Telegram X's string {@code tgxRes}, or the sentence-case {@code xgramRes} while Expressive design is on and the
   * language is the bundled English one (Telegram X's English is title case); a translation is kept as it is.
   */
  @NonNull
  public static String sentenceCase (@StringRes int tgxRes, @StringRes int xgramRes) {
    final String tgx = Lang.getString(tgxRes);
    if (!on()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return Lang.getString(xgramRes);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  /** "Scheduled messages" (Telegram X's English is title case), for the app bar subtitle of scheduled messages. */
  @NonNull
  public static String scheduledMessages () {
    return sentenceCase(R.string.ScheduledMessages, R.string.XgramR5ChatScheduledMessages);
  }

  /**
   * A shared media tab label in sentence case (no uppercase with Expressive design): the one title-case English
   * label, "Video Messages", becomes "Video messages"; everything else, and every translation, is kept.
   */
  @NonNull
  public static String tabCase (@Nullable CharSequence name) {
    final String text = name != null ? name.toString() : "";
    try {
      if (text.equals(Lang.getString(R.string.TabVideoMessagesLong)) && text.equals(AppContext.get().getString(R.string.TabVideoMessagesLong))) {
        return Lang.getString(R.string.XgramR5ChatTabVideoMessages);
      }
    } catch (Throwable ignored) { }
    return text;
  }

  // Wallpaper picker

  /**
   * The note above the wallpaper picker while Pure black draws (on, and the theme is dark): bodySmall in
   * onSurfaceVariant on the picker's surface, 16dp from the sides. The picker itself shows the real colors; the chat
   * then shows color backgrounds as black. Null when Pure black does not draw.
   */
  @Nullable
  public static TextView newPureBlackNote (@NonNull Context context, @Nullable ViewController<?> themeProvider) {
    if (!on() || !XgramAmoled.isActive()) {
      return null;
    }
    TextView note = new TextView(context);
    XgramExpressive.applyText(note, XgramExpressive.TS_BODY_S, false);
    note.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(note, ColorId.textLight); // onSurfaceVariant
    }
    note.setText(Lang.getString(R.string.XgramR5ChatPureBlackNote));
    note.setGravity(Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    note.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    int side = Screen.dp(16f);
    note.setPadding(side, Screen.dp(8f), side, Screen.dp(4f));
    ViewSupport.setThemedBackground(note, ColorId.filling, themeProvider);
    note.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return note;
  }

  // Empty chat (MessagesHolder TYPE_CHAT_EMPTY with Expressive design)

  /** Whether the language is Telegram X's bundled English one (checked on one of its strings). */
  public static boolean isBundledEnglish () {
    try {
      return Lang.getString(R.string.NoMessages).equals(AppContext.get().getString(R.string.NoMessages));
    } catch (Throwable t) {
      return false;
    }
  }

  /** {@code xgramRes} while the language is the bundled English one, else Telegram X's {@code tgxRes} (0: none). */
  @Nullable
  public static String englishOr (@StringRes int xgramRes, @StringRes int tgxRes) {
    if (isBundledEnglish()) {
      return Lang.getString(xgramRes);
    }
    return tgxRes != 0 ? Lang.getString(tgxRes) : null;
  }

  /**
   * Splits Telegram X's "**Title**\n\nText" empty state strings into the title and the text (the bold markers
   * dropped); a string without a leading bold part is all text: {@code out[0]} null.
   */
  public static void splitBoldTitle (@NonNull String source, @NonNull String[] out) {
    out[0] = null;
    out[1] = source;
    if (source.startsWith("**")) {
      int end = source.indexOf("**", 2);
      if (end > 2) {
        out[0] = source.substring(2, end).trim();
        out[1] = source.substring(end + 2).trim();
      }
    }
  }

  /**
   * The empty chat: a centred M3 card (surfaceContainerHigh, the card step with Pure black, 28dp corners, at most
   * 320dp wide and 16dp from the sides) holding an icon on a soft burst in primaryContainer, a titleMedium title, a
   * bodyMedium text and an optional "Say hi" assist chip; while the messages load, the contained loading indicator
   * alone. The card is opaque, so it reads over any wallpaper. Colors are read at draw time.
   */
  public static final class EmptyChatView extends android.widget.FrameLayout {
    private static final float CARD_MAX_WIDTH = 320f, BURST = 72f, ICON = 36f, LOADING = 48f;

    private final LinearLayout card;
    private final BurstView burst;
    private final TextView title, body, chip;
    private final LoadingView loading;
    private @Nullable Runnable onSayHi;
    private int titleColor, bodyColor, chipColor;

    public EmptyChatView (Context context) {
      super(context);
      setClipChildren(false);

      card = new LinearLayout(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          int max = Screen.dp(CARD_MAX_WIDTH);
          if (MeasureSpec.getSize(widthMeasureSpec) > max) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY);
          }
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
      };
      card.setOrientation(LinearLayout.VERTICAL);
      card.setGravity(Gravity.CENTER_HORIZONTAL);
      int padding = Screen.dp(24f);
      card.setPadding(padding, padding, padding, Screen.dp(20f));
      XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_XL, Float.NaN);
      background.setColorRole(XgramExpressive.SURFACE_CONTAINER_HIGH);
      background.setHost(card);
      card.setBackground(background);
      android.widget.FrameLayout.LayoutParams cardParams = new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
      cardParams.leftMargin = cardParams.rightMargin = Screen.dp(16f);
      card.setLayoutParams(cardParams);

      burst = new BurstView(context);
      burst.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(BURST), Screen.dp(BURST)));
      burst.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      card.addView(burst);

      title = new TextView(context);
      XgramExpressive.applyText(title, XgramExpressive.TS_TITLE_M, false);
      title.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      titleParams.topMargin = Screen.dp(16f);
      title.setLayoutParams(titleParams);
      card.addView(title);

      body = new TextView(context);
      XgramExpressive.applyText(body, XgramExpressive.TS_BODY_M, false);
      body.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      bodyParams.topMargin = Screen.dp(4f);
      body.setLayoutParams(bodyParams);
      card.addView(body);

      chip = new TextView(context);
      XgramExpressive.applyText(chip, XgramExpressive.TS_LABEL_L, false);
      chip.setGravity(Gravity.CENTER);
      chip.setMinHeight(Screen.dp(48f)); // the 32dp chip in a 48dp touch target
      int chipPadding = Screen.dp(16f);
      chip.setPadding(chipPadding, 0, chipPadding, 0);
      chip.setBackground(new AssistChipDrawable());
      chip.setOnClickListener(v -> {
        if (onSayHi != null) {
          onSayHi.run();
        }
      });
      LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f));
      chipParams.topMargin = Screen.dp(8f);
      chip.setLayoutParams(chipParams);
      card.addView(chip);

      loading = new LoadingView(context);
      loading.setLayoutParams(new android.widget.FrameLayout.LayoutParams(Screen.dp(LOADING), Screen.dp(LOADING), Gravity.CENTER));
      loading.setContentDescription(Lang.getString(R.string.LoadingMessages));

      addView(card);
      addView(loading);
      bindLoading();
    }

    /** While the messages load: the contained loading indicator in place of "Loading messages...". */
    public void bindLoading () {
      card.setVisibility(View.GONE);
      loading.setVisibility(View.VISIBLE);
      onSayHi = null;
    }

    /**
     * The empty state: {@code iconRes} on the burst, {@code titleText} and {@code bodyText} (either may be null),
     * {@code bodyStart} aligns a list-like text to the start, and the "Say hi" chip when {@code onSayHi} is set.
     */
    public void bind (@androidx.annotation.DrawableRes int iconRes, @Nullable CharSequence titleText, @Nullable CharSequence bodyText, boolean bodyStart, @Nullable Runnable onSayHi) {
      loading.setVisibility(View.GONE);
      card.setVisibility(View.VISIBLE);
      burst.setIcon(iconRes);
      title.setText(titleText);
      title.setVisibility(titleText != null && titleText.length() > 0 ? View.VISIBLE : View.GONE);
      body.setText(bodyText);
      body.setVisibility(bodyText != null && bodyText.length() > 0 ? View.VISIBLE : View.GONE);
      body.setGravity(bodyStart ? (Gravity.CENTER_VERTICAL | Gravity.START) : Gravity.CENTER);
      body.setTextAlignment(bodyStart ? View.TEXT_ALIGNMENT_VIEW_START : View.TEXT_ALIGNMENT_CENTER);
      this.onSayHi = onSayHi;
      chip.setText(Lang.getString(R.string.XgramR5ChatSayHi));
      chip.setVisibility(onSayHi != null ? View.VISIBLE : View.GONE);
      updateColors();
    }

    private void updateColors () {
      int titleNow = XgramExpressive.color(XgramExpressive.ON_SURFACE);
      int bodyNow = XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT);
      if (titleNow != titleColor) {
        titleColor = titleNow;
        title.setTextColor(titleNow);
      }
      if (bodyNow != bodyColor) {
        bodyColor = bodyNow;
        body.setTextColor(bodyNow);
      }
      int chipNow = titleNow;
      if (chipNow != chipColor) {
        chipColor = chipNow;
        chip.setTextColor(chipNow);
      }
    }

    @Override
    protected void dispatchDraw (android.graphics.Canvas c) {
      updateColors(); // a theme or Pure black change repaints with the new roles
      super.dispatchDraw(c);
    }
  }

  /** The 72dp soft burst in primaryContainer with a 36dp icon in onPrimaryContainer. Allocation free per frame. */
  private static final class BurstView extends View {
    private final android.graphics.Path path = new android.graphics.Path();
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private @Nullable android.graphics.drawable.Drawable icon;
    private @Nullable android.graphics.Paint iconPaint;
    private int iconRes, iconColor, pathWidth = -1, pathHeight = -1;

    BurstView (Context context) {
      super(context);
    }

    void setIcon (@androidx.annotation.DrawableRes int res) {
      if (this.iconRes != res) {
        this.iconRes = res;
        this.icon = res != 0 ? org.thunderdog.challegram.tool.Drawables.get(getResources(), XgramIconPack.map(res)) : null;
        invalidate();
      }
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      final int w = getWidth(), h = getHeight();
      if (w <= 0 || h <= 0) {
        return;
      }
      if (pathWidth != w || pathHeight != h) {
        pathWidth = w;
        pathHeight = h;
        XgramShapes.toPath(XgramShapes.SOFT_BURST, path);
        XgramShapes.fit(path, w / 2f, h / 2f, Math.min(w, h), 0f, matrix);
      }
      paint.setColor(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER));
      c.drawPath(path, paint);
      if (icon != null && icon.getMinimumWidth() > 0) {
        final int color = XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER);
        if (iconPaint == null || iconColor != color) {
          iconColor = color;
          iconPaint = org.thunderdog.challegram.tool.Paints.createPorterDuffPaint(iconPaint, color);
        }
        final float scale = Screen.dp(EmptyChatView.ICON) / (float) icon.getMinimumWidth();
        c.save();
        c.scale(scale, scale, w / 2f, h / 2f);
        org.thunderdog.challegram.tool.Drawables.draw(c, icon, w / 2f - icon.getMinimumWidth() / 2f, h / 2f - icon.getMinimumHeight() / 2f, iconPaint);
        c.restore();
      }
    }
  }

  /** The M3 contained loading indicator: a 48dp primaryContainer circle with the indicator in onPrimaryContainer. */
  private static final class LoadingView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private long startTime;

    LoadingView (Context context) {
      super(context);
    }

    @Override
    protected void onVisibilityChanged (@NonNull View changedView, int visibility) {
      super.onVisibilityChanged(changedView, visibility);
      startTime = 0L;
      invalidate();
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      final int w = getWidth(), h = getHeight();
      if (w <= 0 || h <= 0) {
        return;
      }
      final float cx = w / 2f, cy = h / 2f, size = Math.min(w, h);
      paint.setColor(XgramExpressive.color(XgramExpressive.PRIMARY_CONTAINER));
      c.drawCircle(cx, cy, size / 2f, paint);
      final long now = android.os.SystemClock.uptimeMillis();
      if (startTime == 0L) {
        startTime = now;
      }
      final boolean reduce = XgramMotion.reduce();
      XgramLoadingIndicator.draw(c, cx, cy, size * XgramLoadingIndicator.ACTIVE_INDICATOR_SCALE,
        XgramExpressive.color(XgramExpressive.ON_PRIMARY_CONTAINER), reduce ? 0L : now - startTime);
      if (!reduce && isShown()) {
        postInvalidateOnAnimation();
      }
    }
  }

  /**
   * The M3 assist chip's container: 32dp tall (centred in its 48dp touch target), 8dp corners, a 1dp outlineVariant
   * border, and the onSurface pressed state layer. Colors are read at draw time.
   */
  private static final class AssistChipDrawable extends android.graphics.drawable.Drawable {
    private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint stroke = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF rect = new android.graphics.RectF();
    private boolean pressed;

    AssistChipDrawable () {
      stroke.setStyle(android.graphics.Paint.Style.STROKE);
    }

    @Override
    public boolean isStateful () {
      return true;
    }

    @Override
    protected boolean onStateChange (@NonNull int[] state) {
      boolean now = false;
      for (int s : state) {
        if (s == android.R.attr.state_pressed) {
          now = true;
          break;
        }
      }
      if (now != pressed) {
        pressed = now;
        invalidateSelf();
        return true;
      }
      return false;
    }

    @Override
    public void draw (@NonNull android.graphics.Canvas c) {
      final android.graphics.Rect b = getBounds();
      final float height = Math.min(b.height(), Screen.dpf(32f));
      final float strokeWidth = Math.max(1f, Screen.dpf(1f));
      rect.set(b.left + strokeWidth / 2f, b.exactCenterY() - height / 2f + strokeWidth / 2f, b.right - strokeWidth / 2f, b.exactCenterY() + height / 2f - strokeWidth / 2f);
      final float radius = Screen.dpf(XgramExpressive.CORNER_S);
      if (pressed) {
        fill.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SURFACE), XgramExpressive.STATE_PRESSED));
        c.drawRoundRect(rect, radius, radius, fill);
      }
      stroke.setStrokeWidth(strokeWidth);
      stroke.setColor(XgramExpressive.color(XgramExpressive.OUTLINE_VARIANT));
      c.drawRoundRect(rect, radius, radius, stroke);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable android.graphics.ColorFilter colorFilter) { }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity () {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }
}
