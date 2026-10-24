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
package com.fahadali.xgram.editor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.fahadali.xgram.appearance.XgramAppMotion;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramExpressiveC;
import com.fahadali.xgram.appearance.XgramIconPack;
import com.fahadali.xgram.appearance.XgramM3MediaChrome;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

/**
 * The photo editor's text editing overlay, over the whole viewer: the text in a field in the middle, as it will look on
 * the photo (its font, plain, outlined or on a bubble behind each line, aligned to the start, the centre or the end,
 * in its colour), Cancel and Done at the top, and above the keyboard the style and alignment buttons, the colour slider
 * and the font chips (Google Sans Flex, Rounded, a serif and a monospace face, each written in itself). A tap around
 * the text and Back are Done too. A new text goes on the photo as big as it is in the field, with the same lines
 * ({@link #getWrapEms()}); an edited one keeps its place and size. A new text's field is no wider than the photo on
 * screen ({@link #setFieldWidthLimit}). The last text's look comes back for the next new one while the app runs. In a
 * short window (landscape, the Fold's cover screen with the keyboard up) Cancel, Done and the font chips join the style
 * row, so the text keeps room for two lines. While it is open TalkBack reads only the editor.
 * <p>
 * Telegram X look: white icons on the dimmed photo, a check mark for Done, outlined chips that fill white when picked.
 * Material 3 Expressive: a filled Done button in the dark scheme's primary, state layers on the icon buttons, and tonal
 * chips (surfaceContainerHigh at 60%) that fill with secondaryContainer as their corners morph from full to 8dp on the
 * spatial fast spring.
 */
public final class XgramTextEditor extends FrameLayoutFix implements FactorAnimator.Target {
  public interface Callback {
    /** Done: {@code layer} (null for a new text) gets {@code text} (trimmed; empty to delete it) in the editor's look. */
    void onTextEditorDone (XgramTextEditor editor, @Nullable XgramTextLayer layer, @NonNull String text);
    /** Closed without a change. */
    void onTextEditorCancel (XgramTextEditor editor, @Nullable XgramTextLayer layer);
  }

  /** The field's text size (sp, at most MAX_TEXT_SIZE_DP with large fonts): a new text is this big on screen. */
  private static final float TEXT_SIZE_SP = 30f, MAX_TEXT_SIZE_DP = 44f;
  private static final int MAX_LENGTH = 1024;
  private static final int SCRIM_COLOR = 0x99000000;
  private static final float FIELD_MARGIN_DP = 16f, ROW_HEIGHT_DP = 48f, TOP_BAR_DP = 56f, CONTROLS_PADDING_DP = 8f;

  // The last text's look, for the next new one
  private static int lastFont = XgramTextLayer.FONT_FLEX, lastStyle = XgramTextLayer.STYLE_PLAIN, lastAlign = XgramTextLayer.ALIGN_CENTER;
  private static float lastColorValue = 0f; // white

  private final boolean expressive;
  private final @Nullable XgramTextLayer layer;
  private final Callback callback;
  private int font, style, align, color;

  private final Field field;
  private final OptionButton styleButton, alignButton;
  private final Chip[] fontChips;
  private final BoolAnimator shownAnimator;
  private boolean dismissed;
  private final XgramOverlayUnderlay underlay = new XgramOverlayUnderlay();
  /** The field's widest (0: the window's), see {@link #setFieldWidthLimit}. */
  private int fieldWidthLimit;

  // The short window's layout: the top bar goes, its Cancel and Done and the chips join the style row
  private final View topBar, compactCancel, compactDone;
  private final LinearLayout controls, row;
  private final HorizontalScrollView chipsScroll;
  private boolean compact;

  public XgramTextEditor (Context context, @Nullable XgramTextLayer layer, @NonNull Callback callback) {
    super(context);
    this.expressive = XgramExpressive.isEnabled();
    this.layer = layer;
    this.callback = callback;
    final float colorValue;
    if (layer != null) {
      this.font = layer.getFont();
      this.style = layer.getStyle();
      this.align = layer.getAlign();
      this.color = layer.getColor();
      colorValue = findColorValue(color);
    } else {
      this.font = lastFont;
      this.style = lastStyle;
      this.align = lastAlign;
      colorValue = lastColorValue;
      this.color = XgramColorSlider.colorAt(colorValue);
    }

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      setLayoutDirection(Lang.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }
    setBackground(new ColorDrawable(SCRIM_COLOR));
    // the photo under it gets no touches; the overlay itself is no button for TalkBack
    setClickable(true);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // TalkBack says it opened (the overlay itself is left out, so its content has the title)
    ViewCompat.setAccessibilityPaneTitle(content, Lang.getString(layer != null ? R.string.XgramEditorEditText : R.string.XgramEditorAddText));
    addView(content);

    // Cancel at the start, Done at the end

    FrameLayoutFix topBar = new FrameLayoutFix(context);
    topBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(TOP_BAR_DP)));
    this.topBar = topBar;
    ImageView cancelButton = newIconButton(context, R.drawable.baseline_close_24, R.string.Cancel);
    cancelButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.START | Gravity.CENTER_VERTICAL));
    cancelButton.setOnClickListener(v -> cancel());
    topBar.addView(cancelButton);
    View doneButton = newDoneButton(context);
    doneButton.setOnClickListener(v -> done());
    topBar.addView(doneButton);
    content.addView(topBar);

    // The text, in the middle of the room left

    FrameLayoutFix fieldWrap = new FrameLayoutFix(context);
    fieldWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    fieldWrap.setOnClickListener(v -> done()); // a tap around the text finishes it
    fieldWrap.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    field = new Field(context, this);
    field.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(Screen.sp(TEXT_SIZE_SP), Screen.dp(MAX_TEXT_SIZE_DP)));
    // room for the bubble or the outline around the lines, the same for every style, so a style change keeps the lines
    final float textSize = field.getTextSize();
    final int paddingX = (int) Math.ceil(textSize * XgramTextLayer.decorationPaddingX(XgramTextLayer.STYLE_BUBBLE)) + Screen.dp(4f);
    final int paddingY = (int) Math.ceil(textSize * Math.max(XgramTextLayer.decorationPaddingY(XgramTextLayer.STYLE_BUBBLE), XgramTextLayer.decorationPaddingY(XgramTextLayer.STYLE_OUTLINE))) + Screen.dp(4f);
    field.setPadding(paddingX, paddingY, paddingX, paddingY);
    FrameLayoutFix.LayoutParams fieldParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER); // in the middle when it is narrower
    fieldParams.leftMargin = fieldParams.rightMargin = Screen.dp(FIELD_MARGIN_DP);
    field.setLayoutParams(fieldParams);
    if (layer != null) {
      field.setText(layer.getText());
    }
    fieldWrap.addView(field);
    content.addView(fieldWrap);

    // Above the keyboard: style, alignment and colour, then the fonts

    LinearLayout controls = new LinearLayout(context);
    controls.setOrientation(LinearLayout.VERTICAL);
    controls.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    controls.setPadding(Screen.dp(8f), 0, Screen.dp(8f), Screen.dp(CONTROLS_PADDING_DP));
    this.controls = controls;

    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ROW_HEIGHT_DP)));
    this.row = row;
    compactCancel = newIconButton(context, R.drawable.baseline_close_24, R.string.Cancel);
    compactCancel.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    compactCancel.setOnClickListener(v -> cancel());
    compactCancel.setVisibility(View.GONE);
    row.addView(compactCancel);
    styleButton = new OptionButton(context, this, OptionButton.KIND_STYLE);
    styleButton.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    styleButton.setOnClickListener(v -> setStyle((style + 1) % 3));
    row.addView(styleButton);
    alignButton = new OptionButton(context, this, OptionButton.KIND_ALIGN);
    alignButton.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(48f), Screen.dp(48f)));
    alignButton.setOnClickListener(v -> setAlign((align + 1) % 3));
    row.addView(alignButton);
    XgramColorSlider colorSlider = new XgramColorSlider(context);
    LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    sliderParams.leftMargin = sliderParams.rightMargin = Screen.dp(4f); // the same either way round (RTL)
    colorSlider.setLayoutParams(sliderParams);
    colorSlider.setValue(colorValue);
    colorSlider.setCallback((slider, color, value) -> setColor(color));
    row.addView(colorSlider);
    controls.addView(row);

    HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
    this.chipsScroll = chipsScroll;
    chipsScroll.setHorizontalScrollBarEnabled(false);
    chipsScroll.setFillViewport(true); // the chips in the middle when they fit, scrolling when they don't (large fonts)
    chipsScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ROW_HEIGHT_DP)));
    LinearLayout chips = new LinearLayout(context);
    chips.setOrientation(LinearLayout.HORIZONTAL);
    chips.setGravity(Gravity.CENTER);
    chips.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    fontChips = new Chip[XgramTextLayer.FONTS.length];
    for (int i = 0; i < fontChips.length; i++) {
      Chip chip = new Chip(context, this, XgramTextLayer.FONTS[i]);
      LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
      chipParams.leftMargin = chipParams.rightMargin = Screen.dp(4f);
      chip.setLayoutParams(chipParams);
      chips.addView(chip);
      fontChips[i] = chip;
    }
    chipsScroll.addView(chips);
    controls.addView(chipsScroll);
    compactDone = newDoneButton(context);
    final boolean doneIsIcon = compactDone instanceof ImageView;
    LinearLayout.LayoutParams compactDoneParams = new LinearLayout.LayoutParams(doneIsIcon ? Screen.dp(48f) : ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(doneIsIcon ? 48f : 40f));
    compactDoneParams.gravity = Gravity.CENTER_VERTICAL;
    compactDoneParams.leftMargin = compactDoneParams.rightMargin = doneIsIcon ? 0 : Screen.dp(4f);
    compactDone.setLayoutParams(compactDoneParams);
    compactDone.setOnClickListener(v -> done());
    compactDone.setVisibility(View.GONE);
    controls.addView(compactDone);
    content.addView(controls);

    shownAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    setAlpha(0f);
    applyLook(false);
  }

  private ImageView newIconButton (Context context, @DrawableRes int icon, @StringRes int name) {
    ImageView button = new ImageView(context);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setImageResource(XgramIconPack.map(icon));
    button.setColorFilter(0xffffffff);
    button.setContentDescription(Lang.getString(name));
    Views.setClickable(button);
    if (expressive) {
      XgramM3MediaChrome.iconButton(button);
    } else {
      button.setBackgroundResource(R.drawable.bg_btn_header_light);
    }
    return button;
  }

  /** Telegram X look: a check mark; Expressive: a 40dp filled button (labelLarge) in the dark scheme's primary. */
  private View newDoneButton (Context context) {
    if (!expressive || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { // (Expressive design needs Android 13)
      ImageView button = newIconButton(context, R.drawable.baseline_check_24, R.string.Done);
      button.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), Screen.dp(56f), Gravity.END | Gravity.CENTER_VERTICAL));
      return button;
    }
    TextView button = new TextView(context);
    XgramExpressive.applyText(button, XgramExpressive.TS_LABEL_L, true);
    button.setIncludeFontPadding(false);
    button.setGravity(Gravity.CENTER);
    button.setSingleLine(true);
    button.setText(Lang.getString(R.string.Done));
    final int content = XgramM3MediaChrome.onMedia(XgramExpressive.ON_PRIMARY);
    button.setTextColor(content);
    GradientDrawable pill = new GradientDrawable();
    pill.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.PRIMARY));
    pill.setCornerRadius(Screen.dp(100f)); // full corners
    button.setBackground(new RippleDrawable(ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(content)), pill, null));
    button.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    button.setMinWidth(Screen.dp(64f));
    Views.setClickable(button);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f), Gravity.END | Gravity.CENTER_VERTICAL);
    params.leftMargin = params.rightMargin = Screen.dp(12f);
    button.setLayoutParams(params);
    return button;
  }

  // The look

  public int getFont () {
    return font;
  }

  public int getStyle () {
    return style;
  }

  public int getAlign () {
    return align;
  }

  public int getColor () {
    return color;
  }

  /** The field's text size in pixels: a new text is this big on screen. */
  public float getTextSizePx () {
    return field.getTextSize();
  }

  /** The longest line in font sizes: the field's width, so the text breaks where it broke in the field. */
  public float getWrapEms () {
    final float textSize = field.getTextSize();
    final int width = field.getWidth() - field.getCompoundPaddingLeft() - field.getCompoundPaddingRight();
    return width > 0 && textSize > 0f ? width / textSize : XgramTextLayer.DEFAULT_WRAP_EMS;
  }

  /**
   * A new text's field is no wider than {@code limit} pixels (the photo on screen, 0 for no limit): its lines break where
   * they would on the photo, so it fits there at the field's size.
   */
  public void setFieldWidthLimit (int limit) {
    limit = Math.max(0, limit);
    if (this.fieldWidthLimit != limit) {
      this.fieldWidthLimit = limit;
      field.requestLayout();
    }
  }

  private void setFont (int font) {
    font = XgramTextLayer.clampFont(font);
    if (this.font != font) {
      this.font = font;
      applyLook(true);
    }
  }

  private void setStyle (int style) {
    this.style = XgramTextLayer.clampStyle(style);
    applyLook(true);
    styleButton.announceForAccessibility(styleName(this.style));
  }

  private void setAlign (int align) {
    this.align = XgramTextLayer.clampAlign(align);
    applyLook(true);
    alignButton.announceForAccessibility(alignName(this.align));
  }

  private void setColor (int color) {
    this.color = color | 0xff000000;
    field.setLook(font, style, align, this.color);
  }

  private void applyLook (boolean animated) {
    field.setLook(font, style, align, color);
    styleButton.setContentDescription(Lang.getString(R.string.XgramEditorTextStyle, styleName(style)));
    styleButton.invalidate();
    alignButton.setContentDescription(Lang.getString(R.string.XgramEditorTextAlign, alignName(align)));
    alignButton.invalidate();
    for (Chip chip : fontChips) {
      chip.setChecked(chip.font == font, animated);
    }
  }

  private static String styleName (int style) {
    switch (style) {
      case XgramTextLayer.STYLE_OUTLINE:
        return Lang.getString(R.string.XgramEditorStyleOutline);
      case XgramTextLayer.STYLE_BUBBLE:
        return Lang.getString(R.string.XgramEditorStyleBubble);
    }
    return Lang.getString(R.string.XgramEditorStylePlain);
  }

  private static String alignName (int align) {
    final boolean rtl = Lang.rtl();
    switch (align) {
      case XgramTextLayer.ALIGN_START:
        return Lang.getString(rtl ? R.string.XgramEditorAlignRight : R.string.XgramEditorAlignLeft);
      case XgramTextLayer.ALIGN_END:
        return Lang.getString(rtl ? R.string.XgramEditorAlignLeft : R.string.XgramEditorAlignRight);
    }
    return Lang.getString(R.string.XgramEditorAlignCenter);
  }

  /** The place on the colour slider closest to {@code color} (an edited text's colour). */
  private static float findColorValue (int color) {
    final float exact = XgramColorSlider.valueOf(color);
    if (exact >= 0f) {
      return exact;
    }
    float best = 0f;
    long bestDistance = Long.MAX_VALUE;
    for (int i = 0; i <= 500; i++) {
      final float value = i / 500f;
      final int c = XgramColorSlider.colorAt(value);
      final long dr = Color.red(c) - Color.red(color), dg = Color.green(c) - Color.green(color), db = Color.blue(c) - Color.blue(color);
      final long distance = dr * dr + dg * dg + db * db;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = value;
      }
    }
    return best;
  }

  // Showing and closing

  /** Shows the overlay and the keyboard for the field. */
  public void open () {
    underlay.hide(this); // TalkBack doesn't reach the viewer's buttons under it
    XgramAppMotion.use(shownAnimator, XgramExpressive.EFFECTS_FAST, true);
    shownAnimator.setValue(true, true);
    field.requestFocus();
    field.setSelection(field.length());
    field.post(() -> {
      if (!dismissed) {
        Keyboard.show(field);
      }
    });
  }

  public boolean isDismissed () {
    return dismissed;
  }

  /** Done: the callback gets the text (and dismisses the editor). */
  public void done () {
    if (dismissed) {
      return;
    }
    final String text = field.getText().toString().trim();
    if (!text.isEmpty()) {
      lastFont = font;
      lastStyle = style;
      lastAlign = align;
      lastColorValue = findColorValue(color);
    }
    callback.onTextEditorDone(this, layer, text);
  }

  public void cancel () {
    if (!dismissed) {
      callback.onTextEditorCancel(this, layer);
    }
  }

  /** Hides the keyboard and the overlay, which then leaves its parent. */
  public void dismiss (boolean animated) {
    if (dismissed) {
      return;
    }
    dismissed = true;
    underlay.restore();
    Keyboard.hide(field);
    field.clearFocus();
    if (animated && getParent() != null && shownAnimator.getFloatValue() > 0f) {
      XgramAppMotion.use(shownAnimator, XgramExpressive.EFFECTS_FAST, true);
      shownAnimator.setValue(false, true);
    } else {
      shownAnimator.setValue(false, false);
      removeSelf();
    }
  }

  private void removeSelf () {
    ViewParent parent = getParent();
    if (parent instanceof ViewGroup) {
      ((ViewGroup) parent).removeView(this);
    }
  }

  /** The status bar above it and what is under it (the navigation bar and, edge to edge, the keyboard). */
  public void setInsets (int top, int bottom) {
    View content = getChildAt(0);
    if (content != null && (content.getPaddingTop() != top || content.getPaddingBottom() != bottom)) {
      content.setPadding(0, top, 0, bottom);
    }
  }

  // A short window

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    // what the field would have with the top bar and both rows, against two lines of text
    final View content = getChildAt(0);
    final int height = MeasureSpec.getSize(heightMeasureSpec) - (content != null ? content.getPaddingTop() + content.getPaddingBottom() : 0);
    final int room = height - Screen.dp(TOP_BAR_DP) - Screen.dp(ROW_HEIGHT_DP) * 2 - Screen.dp(CONTROLS_PADDING_DP);
    final int needed = field.getLineHeight() * 2 + field.getCompoundPaddingTop() + field.getCompoundPaddingBottom();
    setCompact(MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && room < needed);
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  /** Short: no top bar, one row of Cancel, style, alignment, colour, the fonts (scrolling when they don't fit) and Done. */
  private void setCompact (boolean compact) {
    if (this.compact == compact) {
      return;
    }
    this.compact = compact;
    topBar.setVisibility(compact ? View.GONE : View.VISIBLE);
    compactCancel.setVisibility(compact ? View.VISIBLE : View.GONE);
    compactDone.setVisibility(compact ? View.VISIBLE : View.GONE);
    controls.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    final LinearLayout.LayoutParams rowParams = (LinearLayout.LayoutParams) row.getLayoutParams();
    final LinearLayout.LayoutParams chipsParams = (LinearLayout.LayoutParams) chipsScroll.getLayoutParams();
    rowParams.width = chipsParams.width = compact ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
    rowParams.weight = chipsParams.weight = compact ? 1f : 0f;
    row.setLayoutParams(rowParams);
    chipsScroll.setLayoutParams(chipsParams);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setAlpha(MathUtils.clamp(factor));
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 0f && dismissed) {
      removeSelf();
    }
  }

  // The field: the text as it will look, its bubbles or outline drawn under the field's own text

  private static final class Field extends org.thunderdog.challegram.v.EditText {
    private final XgramTextEditor editor;
    private int style = XgramTextLayer.STYLE_PLAIN, color = 0xffffffff, cursor;
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    Field (Context context, XgramTextEditor editor) {
      super(context);
      this.editor = editor;
      setBackground(null);
      setHint(Lang.getString(R.string.XgramEditorTextHint));
      setHintTextColor(0x80ffffff);
      setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
      setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
      setFilters(new InputFilter[] {new InputFilter.LengthFilter(MAX_LENGTH)});
      setHorizontallyScrolling(false);
      setMaxLines(Integer.MAX_VALUE);
    }

    void setLook (int font, int style, int align, int color) {
      setTypeface(XgramTextLayer.typeface(font));
      setGravity(XgramTextLayer.gravity(align) | Gravity.TOP);
      setTextColor(XgramTextLayer.fillColor(style, color));
      // a white cursor, but grey on a light bubble
      final int cursor = style == XgramTextLayer.STYLE_BUBBLE && XgramTextLayer.isLight(color) ? R.drawable.cursor_grey : R.drawable.cursor_white;
      if (this.cursor != cursor) {
        this.cursor = cursor;
        Views.setCursorDrawable(this, cursor);
      }
      this.style = style;
      this.color = color;
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int limit = editor.fieldWidthLimit;
      if (limit > 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(widthMeasureSpec) > limit) {
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.EXACTLY);
      }
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onKeyPreIme (int keyCode, KeyEvent e) {
      if (keyCode == KeyEvent.KEYCODE_BACK) {
        // Back with the keyboard up keeps what was typed, as a tap around it does (the viewer's popup would only
        // take the key: it hides keyboards itself)
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
          editor.done();
        }
        return true;
      }
      return super.onKeyPreIme(keyCode, e);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (style != XgramTextLayer.STYLE_PLAIN && length() > 0) {
        final Layout layout = getLayout();
        if (layout != null) {
          final int saveCount = c.save();
          c.translate(getCompoundPaddingLeft(), getExtendedPaddingTop()); // where TextView draws its layout (top gravity)
          XgramTextLayer.drawText(c, layout, style, color, getTextSize(), bubblePaint, tmp, 1f, false);
          c.restoreToCount(saveCount);
        }
      }
      super.onDraw(c);
    }
  }

  // The style and alignment buttons: their glyphs drawn here, as the text looks

  private static final class OptionButton extends View {
    static final int KIND_STYLE = 0, KIND_ALIGN = 1;
    /** The alignment glyph's lines, in dp. */
    private static final float[] LINES = {18f, 12f, 18f, 10f};

    private final XgramTextEditor editor;
    private final int kind;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    OptionButton (Context context, XgramTextEditor editor, int kind) {
      super(context);
      this.editor = editor;
      this.kind = kind;
      Views.setClickable(this);
      if (editor.expressive) {
        XgramM3MediaChrome.iconButton(this);
      } else {
        setBackgroundResource(R.drawable.bg_btn_header_light);
      }
      paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      final float cx = getWidth() / 2f, cy = getHeight() / 2f;
      if (kind == KIND_STYLE) {
        drawStyle(c, cx, cy);
      } else {
        drawAlign(c, cx, cy);
      }
    }

    private void drawStyle (Canvas c, float cx, float cy) {
      final String glyph = "A";
      final int style = editor.style;
      textPaint.setTypeface(XgramTextLayer.typeface(editor.font));
      textPaint.setTextSize(Screen.dpf(style == XgramTextLayer.STYLE_BUBBLE ? 15f : 18f));
      final float width = textPaint.measureText(glyph);
      final Paint.FontMetrics metrics = textPaint.getFontMetrics();
      final float x = cx - width / 2f, baseline = cy - (metrics.ascent + metrics.descent) / 2f;
      switch (style) {
        case XgramTextLayer.STYLE_BUBBLE: {
          final float half = Screen.dpf(11f), radius = Screen.dpf(6f);
          rect.set(cx - half, cy - half, cx + half, cy + half);
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(0xffffffff);
          c.drawRoundRect(rect, radius, radius, paint);
          textPaint.setStyle(Paint.Style.FILL);
          textPaint.setColor(0xff000000);
          c.drawText(glyph, x, baseline, textPaint);
          break;
        }
        case XgramTextLayer.STYLE_OUTLINE: {
          textPaint.setStyle(Paint.Style.STROKE);
          textPaint.setStrokeJoin(Paint.Join.ROUND);
          textPaint.setStrokeWidth(Screen.dpf(1.5f));
          textPaint.setColor(0xffffffff);
          c.drawText(glyph, x, baseline, textPaint);
          textPaint.setStyle(Paint.Style.FILL);
          break;
        }
        default: {
          textPaint.setStyle(Paint.Style.FILL);
          textPaint.setColor(0xffffffff);
          c.drawText(glyph, x, baseline, textPaint);
          break;
        }
      }
    }

    private void drawAlign (Canvas c, float cx, float cy) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Screen.dpf(2f));
      paint.setColor(0xffffffff);
      final float gap = Screen.dpf(4.5f), box = Screen.dpf(LINES[0]);
      final float top = cy - gap * (LINES.length - 1) / 2f;
      final boolean rtl = Lang.rtl();
      for (int i = 0; i < LINES.length; i++) {
        final float width = Screen.dpf(LINES[i]), y = top + gap * i;
        final float left;
        switch (editor.align) {
          case XgramTextLayer.ALIGN_START:
            left = rtl ? cx + box / 2f - width : cx - box / 2f;
            break;
          case XgramTextLayer.ALIGN_END:
            left = rtl ? cx - box / 2f : cx + box / 2f - width;
            break;
          default:
            left = cx - width / 2f;
            break;
        }
        c.drawLine(left, y, left + width, y, paint);
      }
    }
  }

  // A font chip, its name in its own font

  private static final class Chip extends View implements FactorAnimator.Target {
    private static final float HEIGHT_DP = 32f, PADDING_DP = 16f;

    final int font;
    private final XgramTextEditor editor;
    private final String label;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float textWidth;
    private boolean checked;
    // Telegram X look: the fill; Expressive: the fill, its corners, the pressed state
    private @Nullable BoolAnimator checkedAnimator;
    private @Nullable XgramExpressive.Spring checkedSpring, shapeSpring, pressSpring;

    Chip (Context context, XgramTextEditor editor, int font) {
      super(context);
      this.editor = editor;
      this.font = font;
      final int labelRes, nameRes;
      switch (font) {
        case XgramTextLayer.FONT_ROUNDED:
          labelRes = R.string.XgramEditorFontRounded;
          nameRes = R.string.XgramFontRounded;
          break;
        case XgramTextLayer.FONT_SERIF:
          labelRes = nameRes = R.string.XgramEditorFontSerif;
          break;
        case XgramTextLayer.FONT_MONO:
          labelRes = R.string.XgramEditorFontMono;
          nameRes = R.string.XgramEditorFontMonoFull;
          break;
        default:
          labelRes = R.string.XgramEditorFontSans;
          nameRes = R.string.XgramFontFlex;
          break;
      }
      this.label = Lang.getString(labelRes);
      setContentDescription(Lang.getString(nameRes));
      textPaint.setTypeface(XgramTextLayer.typeface(font));
      textPaint.setTextSize(Math.min(Screen.sp(14f), Screen.dp(20f)));
      this.textWidth = textPaint.measureText(label);
      Views.setClickable(this);
      setOnClickListener(v -> editor.setFont(font));
      if (editor.expressive) {
        XgramExpressive.Spring.Listener invalidate = (s, value) -> invalidate();
        checkedSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
        shapeSpring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, invalidate);
        pressSpring = new XgramExpressive.Spring(XgramExpressive.EFFECTS_FAST, 0f, invalidate);
      } else {
        checkedAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      }
    }

    void setChecked (boolean checked, boolean animated) {
      if (this.checked == checked && animated) {
        return;
      }
      this.checked = checked;
      setSelected(checked);
      if (checkedAnimator != null) {
        checkedAnimator.setValue(checked, animated);
      } else if (checkedSpring != null && shapeSpring != null) {
        if (animated) {
          checkedSpring.animateTo(checked ? 1f : 0f);
          shapeSpring.animateTo(checked ? 1f : 0f);
        } else {
          checkedSpring.setValue(checked ? 1f : 0f);
          shapeSpring.setValue(checked ? 1f : 0f);
        }
      }
      invalidate();
    }

    @Override
    public void setPressed (boolean pressed) {
      super.setPressed(pressed);
      if (pressSpring != null) {
        pressSpring.animateTo(pressed ? 1f : 0f);
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      final int width = (int) Math.ceil(textWidth) + Screen.dp(PADDING_DP) * 2;
      setMeasuredDimension(width, getDefaultSize(Screen.dp(ROW_HEIGHT_DP), heightMeasureSpec));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw (@NonNull Canvas c) {
      final float cy = getHeight() / 2f, half = Screen.dpf(HEIGHT_DP) / 2f;
      rect.set(0, cy - half, getWidth(), cy + half);
      final int textColor;
      if (editor.expressive && checkedSpring != null && shapeSpring != null && pressSpring != null) {
        final float checked = MathUtils.clamp(checkedSpring.getValue());
        final float shape = Math.max(0f, Math.min(1.2f, shapeSpring.getValue()));
        final float press = MathUtils.clamp(pressSpring.getValue());
        float radius = half + (Screen.dpf(XgramExpressive.CORNER_S) - half) * shape;
        radius = Math.max(0f, Math.min(half, radius));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.fromToArgb(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGH), .6f), XgramM3MediaChrome.onMedia(XgramExpressive.SECONDARY_CONTAINER), checked));
        c.drawRoundRect(rect, radius, radius, paint);
        textColor = ColorUtils.fromToArgb(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE), XgramM3MediaChrome.onMedia(XgramExpressive.ON_SECONDARY_CONTAINER), checked);
        if (press > 0f) {
          paint.setColor(XgramExpressive.withAlpha(textColor, XgramExpressive.STATE_PRESSED * press));
          c.drawRoundRect(rect, radius, radius, paint);
        }
      } else {
        final float checked = checkedAnimator != null ? checkedAnimator.getFloatValue() : (this.checked ? 1f : 0f);
        if (checked < 1f) {
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(Screen.dpf(1f));
          paint.setColor(ColorUtils.alphaColor(.4f * (1f - checked), 0xffffffff));
          final float inset = Screen.dpf(.5f);
          rect.inset(inset, inset);
          c.drawRoundRect(rect, half, half, paint);
          rect.inset(-inset, -inset);
        }
        if (checked > 0f) {
          paint.setStyle(Paint.Style.FILL);
          paint.setColor(ColorUtils.alphaColor(checked, 0xffffffff));
          c.drawRoundRect(rect, half, half, paint);
        }
        textColor = ColorUtils.fromToArgb(0xffffffff, 0xff000000, checked);
      }
      textPaint.setColor(textColor);
      final Paint.FontMetrics metrics = textPaint.getFontMetrics();
      c.drawText(label, (getWidth() - textWidth) / 2f, cy - (metrics.ascent + metrics.descent) / 2f, textPaint);
    }
  }
}
