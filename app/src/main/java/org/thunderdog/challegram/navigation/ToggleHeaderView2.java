package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;

public class ToggleHeaderView2 extends View {
  // XGram M3E r6: the title and subtitle changes slide on the SPATIAL_DEFAULT spring (clamped: it fades them too)
  private final ReplaceAnimator<TrimmedText> titleR = new ReplaceAnimator<>((r) -> invalidate(), com.fahadali.xgram.appearance.XgramAppMotion.interpClamped(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR), com.fahadali.xgram.appearance.XgramAppMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, 180L));
  private final ReplaceAnimator<TrimmedText> subtitleR = new ReplaceAnimator<>((r) -> invalidate(), com.fahadali.xgram.appearance.XgramAppMotion.interpClamped(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, AnimatorUtils.DECELERATE_INTERPOLATOR), com.fahadali.xgram.appearance.XgramAppMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT, 180L));
  private final Drawable arrowDrawable;

  @Dimension(unit = Dimension.DP)
  private float textPaddingDp = 10f;
  @Dimension(unit = Dimension.DP)
  private float textTopDp = 8.5f;
  @Dimension(unit = Dimension.DP)
  private float triangleTopDp = 8.5f;

  private ColorSet colorSet = ColorSet.DEFAULT;

  public interface ColorSet {
    ColorSet DEFAULT = new ColorSet() {
    };

    @ColorInt default int titleColor () {
      return Theme.getColor(ColorId.text);
    }

    @ColorInt default int subtitleColor () {
      return Theme.getColor(ColorId.textLight);
    }

    @PorterDuffColorId default int iconColorId () {
      return ColorId.icon;
    }
  }

  public ToggleHeaderView2 (Context context) {
    super(context);
    arrowDrawable = Drawables.get(R.drawable.baseline_keyboard_arrow_down_20);
  }

  public void setTitle (String title, boolean animated) {
    titleR.replace(new TrimmedText(title), animated);
    trimTexts();
    invalidate();
  }

  public void setSubtitle (String subtitle, boolean animated) {
    subtitleR.replace(new TrimmedText(subtitle), animated);
    trimTexts();
    invalidate();
  }

  public void setTextTop (@Dimension(unit = Dimension.DP) float textTop) {
    textTopDp = textTop;
  }

  public void setTriangleTop (@Dimension(unit = Dimension.DP) float triangleTop) {
    triangleTopDp = triangleTop;
  }

  public void setTextPadding (@Dimension(unit = Dimension.DP) float textPadding) {
    textPaddingDp = textPadding;
  }

  public void setColorSet (ColorSet colorSet) {
    this.colorSet = colorSet;
  }

  // XGram M3E: the title in the app bar's titleLarge (22sp, emphasized), as every other Expressive app bar title
  // (HeaderView), a 28sp line centered in the 56dp bar; Telegram X's Roboto Medium 18 otherwise.
  private static TextPaint xgramTitlePaint;
  private static float xgramTitleCenter;

  private static TextPaint titlePaint (@ColorInt int color) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Paints.getMediumTextPaint(18f, color, false);
    }
    TextPaint paint = xgramTitlePaint;
    int ts = com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_L;
    float size = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(ts));
    if (paint == null || paint.getTextSize() != size) {
      paint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      paint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(ts, true));
      paint.setTextSize(size);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        paint.setLetterSpacing(com.fahadali.xgram.appearance.XgramExpressive.trackingSp(ts) / com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(ts));
      }
      android.graphics.Paint.FontMetrics metrics = paint.getFontMetrics();
      xgramTitleCenter = -(metrics.ascent + metrics.descent) / 2f;
      xgramTitlePaint = paint;
    }
    paint.setColor(color);
    return paint;
  }

  // XGram M3E round 5: the subtitle ("Translated from …") in bodySmall, as the supporting line of an M3 app bar title
  private static TextPaint xgramSubtitlePaint;

  private static TextPaint subtitlePaint (@ColorInt int color) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return Paints.getRegularTextPaint(14f, color);
    }
    TextPaint paint = xgramSubtitlePaint;
    final int ts = com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_S;
    final float size = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(ts));
    if (paint == null || paint.getTextSize() != size) {
      paint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      paint.setTypeface(com.fahadali.xgram.appearance.XgramExpressive.typeface(ts, false));
      paint.setTextSize(size);
      paint.setLetterSpacing(com.fahadali.xgram.appearance.XgramExpressive.trackingSp(ts) / com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(ts));
      xgramSubtitlePaint = paint;
    }
    paint.setColor(color);
    return paint;
  }

  // XGram M3E r6: an M3 menu button: the title then the 24dp arrow_drop_down, which turns over while the menu is open

  private static final float XGRAM_ARROW_SIZE = 24f, XGRAM_ARROW_GAP = 4f;
  private @Nullable Drawable xgramArrow;
  private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramArrowSpring;

  /** The language menu opened or closed: the arrow turns over on SPATIAL_FAST (snaps with Reduce motion). */
  public void xgramSetExpanded (boolean expanded, boolean animated) {
    if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return;
    }
    if (xgramArrowSpring == null) {
      xgramArrowSpring = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
    }
    if (animated) {
      xgramArrowSpring.animateTo(expanded ? 1f : 0f);
    } else {
      xgramArrowSpring.setValue(expanded ? 1f : 0f);
      invalidate();
    }
  }

  private void xgramDrawArrow (@NonNull Canvas c, float titleCenterY) {
    if (xgramArrow == null) {
      xgramArrow = Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.xgram_r6_b1_arrow_drop_down_24));
    }
    if (xgramArrow == null) {
      return;
    }
    final float size = Screen.dp(XGRAM_ARROW_SIZE);
    final float cx = getPaddingLeft() + getTitleWidth() + Screen.dp(XGRAM_ARROW_GAP) + size / 2f;
    final float rotation = xgramArrowSpring != null ? 180f * xgramArrowSpring.getValue() : 0f;
    final int saveCount = c.save();
    if (rotation != 0f) {
      c.rotate(rotation, cx, titleCenterY);
    }
    Drawables.draw(c, xgramArrow, cx - xgramArrow.getMinimumWidth() / 2f, titleCenterY - xgramArrow.getMinimumHeight() / 2f, PorterDuffPaint.get(colorSet.iconColorId()));
    c.restoreToCount(saveCount);
  }

  private void trimTexts () {
    int avail = getMeasuredWidth() - Screen.dp(textPaddingDp) - Screen.dp(12f);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      avail = getMeasuredWidth() - getPaddingLeft() - Screen.dp(XGRAM_ARROW_GAP + XGRAM_ARROW_SIZE + 8f); // room for the arrow
    }
    for (ListAnimator.Entry<TrimmedText> entry : titleR) {
      entry.item.measure(avail, titlePaint(colorSet.titleColor()));
    }
    for (ListAnimator.Entry<TrimmedText> entry : subtitleR) {
      entry.item.measure(avail, subtitlePaint(colorSet.subtitleColor()));
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    trimTexts();
  }

  public float getTitleWidth () {
    float width = 0f;
    for (ListAnimator.Entry<TrimmedText> entry : titleR) {
      width += entry.item.getWidth() * entry.getVisibility();
    }

    return width;
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int textTop = Screen.dp(textTopDp) + Screen.dp(17);
    final int triangleTop = Screen.dp(triangleTopDp) + Screen.dp(3);
    final TextPaint titlePaint = titlePaint(colorSet.titleColor());
    final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    // M3E: the line's center where Telegram X's 18dp title had it (textTop 17 + 11 = the 56dp bar's middle)
    final int titleBaseline = xgramM3 ? Math.round(Screen.dp(textTopDp) + Screen.dp(11f) + xgramTitleCenter) : textTop;
    final int titleShift = xgramM3 ? Math.round(titlePaint.getTextSize()) : Screen.dp(18);
    for (ListAnimator.Entry<TrimmedText> entry : titleR) {
      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * titleShift) :
        ((1f - entry.getVisibility()) * titleShift)));
      entry.item.draw(c, getPaddingLeft(), getPaddingTop() + titleBaseline + offset2, entry.getVisibility(), titlePaint);
    }
    for (ListAnimator.Entry<TrimmedText> entry : subtitleR) {
      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * Screen.dp(14)) :
        ((1f - entry.getVisibility()) * Screen.dp(14))));
      entry.item.draw(c, getPaddingLeft(), getPaddingTop() + textTop + Screen.dp(19) + offset2, entry.getVisibility(), subtitlePaint(colorSet.subtitleColor()));
    }

    if (xgramM3) {
      xgramDrawArrow(c, getPaddingTop() + Screen.dp(textTopDp) + Screen.dp(11f)); // the title line's middle
      return;
    }
    Drawables.draw(c, arrowDrawable, getTitleWidth() + Screen.dp(2), getPaddingTop() + triangleTop, PorterDuffPaint.get(colorSet.iconColorId()));
  }


  private static class TrimmedText {
    private final String text;
    private String textTrimmed;
    private float textTrimmedWidth;
    private float textWidth;

    public TrimmedText (String text) {
      this.text = text;
    }

    public void measure (int width, TextPaint paint) {
      textWidth = U.measureText(text, paint);
      if (textWidth <= width) {
        textTrimmed = null;
        textTrimmedWidth = 0;
      } else {
        textTrimmed = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString();
        textTrimmedWidth = U.measureText(textTrimmed, paint);
      }
    }

    public float getWidth () {
      return textTrimmed != null ? textTrimmedWidth : textWidth;
    }

    public void draw (Canvas canvas, int x, int y, float alpha, TextPaint paint) {
      paint.setAlpha((int) (alpha * 255));
      canvas.drawText(textTrimmed != null ? textTrimmed : text, x, y, paint);
    }
  }
}
