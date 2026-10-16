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

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

import me.vkryl.core.MathUtils;

/**
 * XGram M3E round 5: calls, audio and pickers. Shared pieces of the call message card (TGMessageCall), the ongoing
 * call chip and the mini player under the app bar (HeaderFilling), and the unsupported link feedback (TdlibUi,
 * TGInlineKeyboard). Everything here is used only while {@link XgramExpressive#isEnabled()} is true. Drawing helpers
 * allocate nothing per call and run on the main thread; the measure paints may also be used where Telegram X builds
 * message layouts.
 */
public final class XgramR5Calls {
  private XgramR5Calls () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Sentence case (rule h): only while the language is Telegram X's bundled English

  /** {@code xgramRes} while {@code tgxRes} is Telegram X's own English string, else the (translated) {@code tgxRes}. */
  @NonNull
  public static String sentence (@StringRes int tgxRes, @StringRes int xgramRes) {
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

  /** The sentence-case string for a Telegram X call title ("Canceled Call"), or 0 when it has none. */
  @StringRes
  public static int callTitleRes (@StringRes int tgxRes) {
    if (tgxRes == R.string.OutgoingCall) {
      return R.string.XgramR5CallsOutgoingCall;
    } else if (tgxRes == R.string.IncomingCall) {
      return R.string.XgramR5CallsIncomingCall;
    } else if (tgxRes == R.string.MissedCall) {
      return R.string.XgramR5CallsMissedCall;
    } else if (tgxRes == R.string.CallMessageOutgoingMissed) {
      return R.string.XgramR5CallsCanceledCall;
    } else if (tgxRes == R.string.CallMessageIncomingDeclined) {
      return R.string.XgramR5CallsDeclinedCall;
    }
    return 0;
  }

  /** A call title in sentence case for English (see {@link #sentence}). */
  @NonNull
  public static String callTitle (@StringRes int tgxRes) {
    final int res = callTitleRes(tgxRes);
    return res != 0 ? sentence(tgxRes, res) : Lang.getString(tgxRes);
  }

  // Type (rule g)

  private static TextPaint titleSmall, bodySmall, labelLarge, labelMedium, titleSmallTabular;
  private static TextPaint measureTitleSmall, measureBodySmall, measureLabelLarge, measureLabelMedium, measureTitleSmallTabular;

  private static TextPaint newPaint (int ts, boolean tabular) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    paint.setTypeface(XgramExpressive.typeface(ts, false));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      paint.setLetterSpacing(XgramExpressive.trackingSp(ts) / XgramExpressive.textSizeSp(ts));
      if (tabular) {
        paint.setFontFeatureSettings("tnum");
      }
    }
    return paint;
  }

  private static TextPaint sized (TextPaint paint, int ts) {
    final float size = Screen.dp(XgramExpressive.textSizeSp(ts));
    if (paint.getTextSize() != size) {
      paint.setTextSize(size);
    }
    return paint;
  }

  /** titleSmall (14, 500) for drawing. Main thread. */
  public static TextPaint titleSmallPaint (@ColorInt int color) {
    if (titleSmall == null) {
      titleSmall = newPaint(XgramExpressive.TS_TITLE_S, false);
    }
    sized(titleSmall, XgramExpressive.TS_TITLE_S).setColor(color);
    return titleSmall;
  }

  /** titleSmall with tabular figures (durations), for drawing. Main thread. */
  public static TextPaint titleSmallTabularPaint (@ColorInt int color) {
    if (titleSmallTabular == null) {
      titleSmallTabular = newPaint(XgramExpressive.TS_TITLE_S, true);
    }
    sized(titleSmallTabular, XgramExpressive.TS_TITLE_S).setColor(color);
    return titleSmallTabular;
  }

  /** bodySmall (12, 400) for drawing. Main thread. */
  public static TextPaint bodySmallPaint (@ColorInt int color) {
    if (bodySmall == null) {
      bodySmall = newPaint(XgramExpressive.TS_BODY_S, true);
    }
    sized(bodySmall, XgramExpressive.TS_BODY_S).setColor(color);
    return bodySmall;
  }

  /** labelLarge (14, 500) for drawing. Main thread. */
  public static TextPaint labelLargePaint (@ColorInt int color) {
    if (labelLarge == null) {
      labelLarge = newPaint(XgramExpressive.TS_LABEL_L, false);
    }
    sized(labelLarge, XgramExpressive.TS_LABEL_L).setColor(color);
    return labelLarge;
  }

  /** labelMedium (12, 500) with tabular figures (speeds), for drawing. Main thread. */
  public static TextPaint labelMediumPaint (@ColorInt int color) {
    if (labelMedium == null) {
      labelMedium = newPaint(XgramExpressive.TS_LABEL_M, true);
    }
    sized(labelMedium, XgramExpressive.TS_LABEL_M).setColor(color);
    return labelMedium;
  }

  /** The paints used to measure and ellipsize the texts drawn with the paints above (never recoloured). */
  public static synchronized TextPaint measurePaint (int ts) {
    switch (ts) {
      case XgramExpressive.TS_TITLE_S:
        if (measureTitleSmall == null) {
          measureTitleSmall = newPaint(ts, false);
        }
        return sized(measureTitleSmall, ts);
      case XgramExpressive.TS_BODY_S:
        if (measureBodySmall == null) {
          measureBodySmall = newPaint(ts, true);
        }
        return sized(measureBodySmall, ts);
      case XgramExpressive.TS_LABEL_L:
        if (measureLabelLarge == null) {
          measureLabelLarge = newPaint(ts, false);
        }
        return sized(measureLabelLarge, ts);
      case XgramExpressive.TS_LABEL_M:
      default:
        if (measureLabelMedium == null) {
          measureLabelMedium = newPaint(XgramExpressive.TS_LABEL_M, true);
        }
        return sized(measureLabelMedium, XgramExpressive.TS_LABEL_M);
    }
  }

  /** The measure paint of {@link #titleSmallTabularPaint}. */
  public static synchronized TextPaint measureTabularTitlePaint () {
    if (measureTitleSmallTabular == null) {
      measureTitleSmallTabular = newPaint(XgramExpressive.TS_TITLE_S, true);
    }
    return sized(measureTitleSmallTabular, XgramExpressive.TS_TITLE_S);
  }

  /** {@code text} ellipsized to {@code maxWidth} px with {@code paint}; null for an empty text. */
  @Nullable
  public static String ellipsize (@Nullable CharSequence text, TextPaint paint, float maxWidth) {
    if (TextUtils.isEmpty(text) || maxWidth <= 0f) {
      return null;
    }
    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString();
  }

  /** The baseline that centres one line of {@code paint} on {@code centerY}. */
  public static float baseline (TextPaint paint, float centerY) {
    return centerY - (paint.ascent() + paint.descent()) / 2f;
  }

  // Shapes

  private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final RectF rect = new RectF();

  /** A rounded rectangle with {@code radius} corners (clamped to a pill). */
  public static void drawRoundRect (Canvas c, float left, float top, float right, float bottom, float radius, @ColorInt int color) {
    if (right <= left || bottom <= top || (color >>> 24) == 0) {
      return;
    }
    radius = Math.min(radius, Math.min(right - left, bottom - top) / 2f);
    fill.setColor(color);
    rect.set(left, top, right, bottom);
    c.drawRoundRect(rect, radius, radius, fill);
  }

  /** A 1dp outline (an outlined chip) inside {@code left..right x top..bottom}. */
  public static void drawOutline (Canvas c, float left, float top, float right, float bottom, float radius, @ColorInt int color) {
    if (right <= left || bottom <= top || (color >>> 24) == 0) {
      return;
    }
    final float stroke = Screen.dpf(1f);
    fill.setColor(color);
    fill.setStyle(Paint.Style.STROKE);
    fill.setStrokeWidth(stroke);
    rect.set(left + stroke / 2f, top + stroke / 2f, right - stroke / 2f, bottom - stroke / 2f);
    radius = Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
    c.drawRoundRect(rect, radius, radius, fill);
    fill.setStyle(Paint.Style.FILL);
  }

  /** {@code icon} tinted {@code color}, centred on {@code cx, cy}. */
  public static void drawIcon (Canvas c, @Nullable Drawable icon, float cx, float cy, @ColorInt int color) {
    if (icon == null || (color >>> 24) == 0) {
      return;
    }
    Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(color));
  }

  /**
   * A toggle icon button drawn on a Canvas ({@code sizePx} box centred on {@code cx, cy}), as XgramM3CallButton's toggle:
   * a full-corner container in {@code offColor} that morphs to CORNER_M in {@code onColor} as {@code selected} goes to 1,
   * its corners springing to CORNER_S under a press ({@code press} 0..1) with a 10% state layer in the content color.
   */
  public static void drawToggleButton (Canvas c, float cx, float cy, float sizePx, float selected, float press,
                                       @ColorInt int offColor, @ColorInt int onColor, @ColorInt int contentColor) {
    final float half = sizePx / 2f;
    final float full = half;
    final float on = Math.min(full, Screen.dpf(XgramExpressive.CORNER_M));
    final float rest = full + (on - full) * selected;
    final float pressed = Math.min(full, Screen.dpf(XgramExpressive.CORNER_S));
    final float corner = MathUtils.clamp(rest + (pressed - rest) * MathUtils.clamp(press, 0f, 1.2f), 0f, full);
    final int container = me.vkryl.core.ColorUtils.fromToArgb(offColor, onColor, MathUtils.clamp(selected));
    drawRoundRect(c, cx - half, cy - half, cx + half, cy + half, corner, container);
    if (press > 0f) {
      drawRoundRect(c, cx - half, cy - half, cx + half, cy + half, corner, XgramExpressive.withAlpha(contentColor, XgramExpressive.STATE_PRESSED * MathUtils.clamp(press)));
    }
  }

  // Call message card (TGMessageCall)

  /**
   * The Expressive call message: a tonal card (12dp corners that spring to 20dp under a press, with a state layer)
   * holding a 40dp CORNER_M container with the phone or videocam icon, the title in titleSmall, the supporting line in
   * bodySmall after the 18dp direction icon, and a trailing 40dp call back icon button. One per message. Geometry in
   * dp; RTL mirrored.
   */
  public static final class CallCard {
    public static final float PAD = 8f, BOX = 40f, GAP = 12f, BUTTON = 40f, HEIGHT = 56f, DIRECTION_GAP = 4f, MIN_TEXT = 96f, END_GAP = 4f;
    private static final float REST_CORNER = XgramExpressive.CORNER_M, PRESSED_CORNER = XgramExpressive.CORNER_L_INC;
    public static final int TARGET_NONE = 0, TARGET_CARD = 1, TARGET_BUTTON = 2;

    private final Runnable invalidate;
    private final XgramM3Content.TonalCard card = new XgramM3Content.TonalCard();
    private @Nullable XgramExpressive.Spring press;
    private int pressTarget;

    private @Nullable String title, subtitle;
    private float titleWidth, subtitleWidth, subtitleTextWidth;

    public CallCard (@NonNull Runnable invalidate) {
      this.invalidate = invalidate;
    }

    /** Space taken by everything but the texts (px). */
    public static int fixedWidth () {
      return Screen.dp(PAD + BOX + GAP + END_GAP + BUTTON + PAD);
    }

    /** Lays out the texts for a card at most {@code maxWidth} px wide. Any thread. */
    public void build (@Nullable String title, @Nullable String subtitle, int maxWidth, int directionIconWidth) {
      final float textMax = Math.max(Screen.dp(48f), maxWidth - fixedWidth());
      final TextPaint titlePaint = measurePaint(XgramExpressive.TS_TITLE_S);
      final TextPaint subtitlePaint = measurePaint(XgramExpressive.TS_BODY_S);
      this.title = ellipsize(title, titlePaint, textMax);
      this.titleWidth = this.title != null ? titlePaint.measureText(this.title) : 0f;
      final float directionSpace = directionIconWidth + Screen.dp(DIRECTION_GAP);
      this.subtitle = ellipsize(subtitle, subtitlePaint, textMax - directionSpace);
      this.subtitleTextWidth = this.subtitle != null ? subtitlePaint.measureText(this.subtitle) : 0f;
      this.subtitleWidth = subtitleTextWidth + directionSpace;
    }

    /** The card's width (px): as wide as its texts (at least {@link #MIN_TEXT}), at most {@code maxWidth}. */
    public int width (int maxWidth) {
      final int text = (int) Math.ceil(Math.max(Screen.dp(MIN_TEXT), Math.max(titleWidth, subtitleWidth)));
      return Math.min(Math.max(maxWidth, fixedWidth() + Screen.dp(48f)), fixedWidth() + text);
    }

    public static int height () {
      return Screen.dp(HEIGHT);
    }

    // Touch

    /** Which part of a card at {@code left, top} ({@code width} px) is under {@code x, y}. */
    public static int targetAt (float x, float y, int left, int top, int width) {
      if (x < left || x > left + width || y < top || y > top + height()) {
        return TARGET_NONE;
      }
      final boolean rtl = Lang.rtl();
      final float buttonSpace = Screen.dp(PAD + BUTTON);
      final boolean onButton = rtl ? x <= left + buttonSpace : x >= left + width - buttonSpace;
      return onButton ? TARGET_BUTTON : TARGET_CARD;
    }

    public void setPressed (int target) {
      if (target != TARGET_NONE) {
        pressTarget = target;
      }
      if (press == null) {
        if (target == TARGET_NONE) {
          return;
        }
        press = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate.run());
      }
      press.animateTo(target != TARGET_NONE ? 1f : 0f);
    }

    // Drawing

    /**
     * Draws the card at {@code left, top}, {@code width} px wide.
     *
     * @param cardColor the card's tonal fill
     * @param boxColor / boxIconColor the leading container and its icon
     * @param directionColor the direction icon (error when missed, primary otherwise)
     */
    public void draw (Canvas c, int left, int top, int width,
                      @ColorInt int cardColor, @ColorInt int boxColor, @ColorInt int boxIconColor,
                      @ColorInt int titleColor, @ColorInt int subtitleColor, @ColorInt int directionColor, @ColorInt int buttonColor,
                      @Nullable Drawable kindIcon, @Nullable Drawable directionIcon, @Nullable Drawable buttonIcon) {
      final boolean rtl = Lang.rtl();
      final int right = left + width;
      final int bottom = top + height();
      final float p = press != null ? MathUtils.clamp(press.getValue(), 0f, 1.2f) : 0f;
      final float cardPress = pressTarget == TARGET_CARD ? p : 0f;
      final float corner = Screen.dpf(REST_CORNER) + (Screen.dpf(PRESSED_CORNER) - Screen.dpf(REST_CORNER)) * cardPress;
      card.draw(c, left, top, right, bottom, corner, cardColor, 0, 0f, false);
      if (cardPress > 0f) {
        XgramM3Content.drawRoundRect(c, left, top, right, bottom, corner, XgramExpressive.withAlpha(titleColor, XgramExpressive.STATE_PRESSED * MathUtils.clamp(cardPress)));
      }

      // Leading container
      final float pad = Screen.dpf(PAD), box = Screen.dpf(BOX);
      final float boxLeft = rtl ? right - pad - box : left + pad;
      final float boxTop = top + (height() - box) / 2f;
      drawRoundRect(c, boxLeft, boxTop, boxLeft + box, boxTop + box, Screen.dpf(XgramExpressive.CORNER_M), boxColor);
      drawIcon(c, kindIcon, boxLeft + box / 2f, boxTop + box / 2f, boxIconColor);

      // Texts: title (20dp line) over the supporting line (16dp), centred on the container
      final float textStart = rtl ? boxLeft - Screen.dpf(GAP) : boxLeft + box + Screen.dpf(GAP);
      final float titleCenter = boxTop + Screen.dpf(2f + 10f);
      final float subtitleCenter = boxTop + Screen.dpf(2f + 20f + 8f);
      if (title != null) {
        final TextPaint paint = titleSmallPaint(titleColor);
        c.drawText(title, rtl ? textStart - titleWidth : textStart, baseline(paint, titleCenter), paint);
      }
      float subtitleX = textStart;
      if (directionIcon != null) {
        final float iconWidth = directionIcon.getMinimumWidth();
        drawIcon(c, directionIcon, rtl ? textStart - iconWidth / 2f : textStart + iconWidth / 2f, subtitleCenter, directionColor);
        subtitleX = rtl ? textStart - iconWidth - Screen.dpf(DIRECTION_GAP) : textStart + iconWidth + Screen.dpf(DIRECTION_GAP);
      }
      if (subtitle != null) {
        final TextPaint paint = bodySmallPaint(subtitleColor);
        c.drawText(subtitle, rtl ? subtitleX - subtitleTextWidth : subtitleX, baseline(paint, subtitleCenter), paint);
      }

      // Trailing call back icon button (40dp state layer)
      final float button = Screen.dpf(BUTTON);
      final float buttonCx = rtl ? left + pad + button / 2f : right - pad - button / 2f;
      final float buttonCy = top + height() / 2f;
      final float buttonPress = pressTarget == TARGET_BUTTON ? MathUtils.clamp(p) : 0f;
      if (buttonPress > 0f) {
        drawRoundRect(c, buttonCx - button / 2f, buttonCy - button / 2f, buttonCx + button / 2f, buttonCy + button / 2f, button / 2f, XgramExpressive.withAlpha(buttonColor, XgramExpressive.STATE_PRESSED * buttonPress));
      }
      drawIcon(c, buttonIcon, buttonCx, buttonCy, buttonColor);
    }
  }

  // Dialogs

  /**
   * Shows {@code b} as Telegram X's {@code BaseActivity.modifyAlert(context, b.show(), null)} sites do; with Expressive
   * design, through {@code BaseActivity.showAlert} when {@code context} is the activity, so the M3 window motion is set
   * before the dialog shows and covers its entrance.
   */
  @Nullable
  public static android.app.AlertDialog showAlert (@NonNull android.content.Context context, @NonNull android.app.AlertDialog.Builder b) {
    if (on()) {
      final org.thunderdog.challegram.BaseActivity activity = org.thunderdog.challegram.tool.UI.getContext(context);
      if (activity != null) {
        return activity.showAlert(b);
      }
    }
    return org.thunderdog.challegram.BaseActivity.modifyAlert(context, b.show(), null);
  }

  // Floating toolbars (chat preview, quick actions)

  /** Whether a chat action is destructive (drawn in error in the floating toolbars). */
  public static boolean isDestructiveAction (int id) {
    return id == R.id.btn_removeChatFromListOrClearHistory;
  }

  // Unsupported links

  /**
   * Transient feedback for a link Telegram X can't open (a Mini App, a story, an invoice): an M3 snackbar. Returns false
   * when no snackbar could be shown, so the caller keeps its Telegram X feedback.
   */
  public static boolean showUnsupported (boolean miniApp) {
    return showUnsupported(miniApp, null);
  }

  /**
   * {@link #showUnsupported(boolean)} with an "Open in browser" action while the link has a web address
   * ({@code originalUrl}, http or https or a bare t.me link; never a tg: link, which would only come back here).
   */
  public static boolean showUnsupported (boolean miniApp, @Nullable String originalUrl) {
    if (!on()) {
      return false;
    }
    try {
      // Telegram X's own (translated) text for the other links
      final CharSequence text = Lang.getString(miniApp ? R.string.XgramR5CallsMiniAppsUnsupported : R.string.InternalUrlUnsupported);
      final android.net.Uri webUri = webUri(originalUrl);
      if (webUri != null) {
        return XgramSnackbar.show(text, android.widget.Toast.LENGTH_LONG, Lang.getString(R.string.XgramR5SheetsOpenInBrowser), () ->
          org.thunderdog.challegram.tool.Intents.openUriInBrowser(webUri)
        );
      }
      return XgramSnackbar.show(text, android.widget.Toast.LENGTH_LONG);
    } catch (Throwable t) {
      return false;
    }
  }

  @Nullable
  private static android.net.Uri webUri (@Nullable String url) {
    if (TextUtils.isEmpty(url)) {
      return null;
    }
    final android.net.Uri uri = org.thunderdog.challegram.tool.Strings.wrapHttps(url);
    final String scheme = uri != null ? uri.getScheme() : null;
    return "https".equals(scheme) || "http".equals(scheme) ? uri : null;
  }

  /** A link to a Mini App (a web app, the main app of a bot, an attachment menu bot). */
  public static boolean isMiniAppLink (@Nullable org.drinkless.tdlib.TdApi.InternalLinkType linkType) {
    if (linkType == null) {
      return false;
    }
    switch (linkType.getConstructor()) {
      case org.drinkless.tdlib.TdApi.InternalLinkTypeWebApp.CONSTRUCTOR:
      case org.drinkless.tdlib.TdApi.InternalLinkTypeMainWebApp.CONSTRUCTOR:
      case org.drinkless.tdlib.TdApi.InternalLinkTypeAttachmentMenuBot.CONSTRUCTOR:
        return true;
    }
    return false;
  }
}
