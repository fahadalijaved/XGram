/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fahadali.xgram.appearance.XgramExpressive;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.HeightChangeListener;
import org.thunderdog.challegram.v.EditText;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

@SuppressWarnings("ViewConstructor")
public class MaterialEditTextGroup extends FrameLayoutFix implements View.OnFocusChangeListener, FactorAnimator.Target, TextWatcher, TextView.OnEditorActionListener {
  public interface EmptyListener {
    void onTextEmptyStateChanged (MaterialEditTextGroup v, boolean isEmpty);
  }

  public interface DoneListener {
    boolean onTextDonePressed (MaterialEditTextGroup v);
  }

  public interface TextChangeListener {
    void onTextChanged (MaterialEditTextGroup v, CharSequence text);
  }

  public interface FocusListener {
    void onFocusChanged (MaterialEditTextGroup v, boolean isFocused);
  }

  private @Nullable TextView hintView;
  private @NonNull MaterialEditText editText;
  private @Nullable TextView lengthCounter;

  private boolean isNotEmpty;
  private @Nullable EmptyListener emptyListener;
  private @Nullable TextChangeListener textListener;
  private @Nullable DoneListener doneListener;
  private @Nullable FocusListener focusListener;
  private @Nullable HeightChangeListener heightChangeListener;

  public MaterialEditTextGroup (Context context, Tdlib tdlib) {
    super(context);
    init(context, tdlib, true);
  }

  public MaterialEditTextGroup (Context context, Tdlib tdlib, boolean needHint) {
    super(context);
    init(context, tdlib, needHint);
  }

  private void setIsNotEmpty (boolean isNotEmpty) {
    if (this.isNotEmpty != isNotEmpty) {
      this.isNotEmpty = isNotEmpty;
      if (emptyListener != null) {
        emptyListener.onTextEmptyStateChanged(this, !isNotEmpty);
      }
    }
  }

  public void setTextListener (@Nullable TextChangeListener textListener) {
    this.textListener = textListener;
  }

  public void setHeightChangeListener (@Nullable HeightChangeListener heightChangeListener) {
    this.heightChangeListener = heightChangeListener;
  }

  @ColorId
  private int textColorId = ColorId.text;

  public void setTextColorId (@ColorId int colorId) {
    if (this.textColorId != colorId) {
      this.textColorId = colorId;
      editText.setTextColor(Theme.getColor(colorId));
      if (themeProvider != null) {
        themeProvider.addOrUpdateThemeTextColorListener(this, colorId);
      }
    }
  }

  public void setInputEnabled (boolean enabled) {
    editText.setEnabled(enabled);
    setTextColorId(enabled ? ColorId.text : ColorId.textLight);
  }

  public interface NextCallback {
    boolean needNextButton (MaterialEditTextGroup v);
  }

  private NextCallback nextCallback;

  public void setNeedNextButton (NextCallback forceNextButton) {
    this.nextCallback = forceNextButton;
  }

  private void init (Context context, Tdlib tdlib, boolean needHint) {
    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(needHint ? 20f : 8f);

    editText = new MaterialEditText(context, tdlib) {
      @Override
      public InputConnection createInputConnection (EditorInfo outAttrs) {
        InputConnection conn = super.createInputConnection(outAttrs);
        if (nextCallback != null && nextCallback.needNextButton(MaterialEditTextGroup.this)) {
          outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        return conn;
      }
    };
    editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    editText.setTypeface(Fonts.getRobotoRegular());
    editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    editText.setBackgroundResource(R.drawable.transparent);
    editText.setTextColor(Theme.getColor(textColorId));
    editText.setHintTextColor(Theme.textPlaceholderColor());
    editText.setLayoutParams(params);
    editText.addTextChangedListener(this);
    editText.setPadding(Screen.dp(1.5f), Screen.dp(8f), Screen.dp(1.5f), Screen.dp(9f));
    // editText.setMinimumHeight(Screen.dp(40f));
    addView(editText);

    if (needHint) {
      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f));
      params.topMargin = Screen.dp(20f);

      hintView = new NoScrollTextView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          if (isRtl) {
            setPivotX(xgram ? getMeasuredWidth() - getPaddingRight() : getMeasuredWidth());
          } else {
            setPivotX(xgram ? getPaddingLeft() : 0f);
          }
        }
      };
      hintView.setPivotY(0f);
      hintView.setTypeface(Fonts.getRobotoRegular());
      hintView.setSingleLine(true);
      hintView.setEllipsize(TextUtils.TruncateAt.END);
      hintView.setTextColor(Theme.textPlaceholderColor());
      hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
      hintView.setPadding(Screen.dp(1.5f), 0, Screen.dp(1.5f), 0);
      hintView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
      hintView.setLayoutParams(params);
      addView(hintView);
    }

    editText.setOnFocusChangeListener(this);

    if (xgram) {
      xgramInit();
    }
  }

  // XGram M3E: filled Material 3 text field (surfaceContainerHighest container with 4dp top corners behind the label
  // and the input, 16dp side padding, the label in primary while focused); heights stay as they were

  private final boolean xgram = XgramExpressive.isEnabled();
  private Paint xgramPaint;
  private final Path xgramPath = new Path();
  private final RectF xgramRect = new RectF();
  private final float[] xgramRadii = new float[8];

  private void xgramInit () {
    setWillNotDraw(false);
    final int pad = Screen.dp(16f);
    editText.setPadding(pad, editText.getPaddingTop(), pad, editText.getPaddingBottom());
    // XGram M3E r6: the input and the label in bodyLarge (Google Sans Flex); the label floats to bodySmall (see updateScale)
    XgramExpressive.applyText(editText, XgramExpressive.TS_BODY_L, false);
    if (hintView != null) {
      hintView.setPadding(pad, 0, pad, 0);
      XgramExpressive.applyText(hintView, XgramExpressive.TS_BODY_L, false);
      hintView.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    }
  }

  /** The label's role now: error while the field is in error, primary while focused, onSurfaceVariant otherwise. */
  private int xgramHintRole () {
    return inErrorState ? XgramExpressive.ERROR : hasFocus ? XgramExpressive.PRIMARY : XgramExpressive.ON_SURFACE_VARIANT;
  }

  private void xgramUpdateHintColor () {
    if (xgram && hintView != null) {
      hintView.setTextColor(XgramExpressive.color(xgramHintRole()));
    }
  }

  // XGram M3E r6: the supporting text line under the filled field (bodySmall: a hint, a check result or an error) and a
  // leading element, a trailing one and a prefix inside it (the country field's flag and arrow, the "+" of a calling code)

  private @Nullable com.fahadali.xgram.appearance.XgramR6B8Login.SupportingText xgramSupporting;
  private @Nullable View xgramLeading, xgramTrailing;
  private @Nullable TextView xgramPrefix;

  /** {@code text} under the field (null or empty hides it); an error also turns the indicator and the label to error. */
  public void xgramSetSupportingText (@Nullable CharSequence text, boolean isError) {
    xgramSetSupportingText(text, isError ? com.fahadali.xgram.appearance.XgramR6B8Login.TONE_ERROR : com.fahadali.xgram.appearance.XgramR6B8Login.TONE_NEUTRAL);
  }

  /** {@link #xgramSetSupportingText(CharSequence, boolean)} in a tone of XgramR6B8Login (neutral, good with a check, error). */
  public void xgramSetSupportingText (@Nullable CharSequence text, int tone) {
    if (!xgram) {
      return;
    }
    final boolean empty = TextUtils.isEmpty(text);
    if (xgramSupporting == null) {
      if (empty) {
        setInErrorState(false);
        setInGoodState(false);
        return;
      }
      xgramSupporting = new com.fahadali.xgram.appearance.XgramR6B8Login.SupportingText(getContext());
      final int counterRoom = Screen.dp(lengthCounter != null ? 64f : 16f);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | (isRtl ? Gravity.RIGHT : Gravity.LEFT));
      params.leftMargin = isRtl ? counterRoom : Screen.dp(16f);
      params.rightMargin = isRtl ? Screen.dp(16f) : counterRoom;
      xgramSupporting.setLayoutParams(params);
      xgramSupporting.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
      xgramSupporting.setPadding(0, Screen.dp(4f), 0, 0);
      addView(xgramSupporting);
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(xgramSupporting);
      }
    }
    xgramSupporting.set(text, tone, isLaidOut());
    setInErrorState(!empty && tone == com.fahadali.xgram.appearance.XgramR6B8Login.TONE_ERROR);
    setInGoodState(!empty && tone == com.fahadali.xgram.appearance.XgramR6B8Login.TONE_GOOD);
  }

  /** True while the supporting text shows an error. */
  public boolean xgramHasSupportingError () {
    return xgramSupporting != null && xgramSupporting.isError();
  }

  /** {@code view} (48dp wide) at the start of the field, e.g. a flag; the input and the label start 52dp in. */
  public void xgramSetLeading (@Nullable View view) {
    if (!xgram) {
      return;
    }
    if (xgramLeading != null) {
      removeView(xgramLeading);
    }
    xgramLeading = view;
    if (view != null) {
      addView(view, FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.TOP | (isRtl ? Gravity.RIGHT : Gravity.LEFT)));
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(view);
      }
    }
    xgramUpdatePaddings();
  }

  /** {@code view} (48dp wide) at the end of the field, e.g. a dropdown arrow; the input and the label end 52dp before it. */
  public void xgramSetTrailing (@Nullable View view) {
    if (!xgram) {
      return;
    }
    if (xgramTrailing != null) {
      removeView(xgramTrailing);
    }
    xgramTrailing = view;
    if (view != null) {
      addView(view, FrameLayoutFix.newParams(Screen.dp(48f), Screen.dp(48f), Gravity.TOP | (isRtl ? Gravity.LEFT : Gravity.RIGHT)));
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(view);
      }
    }
    xgramUpdatePaddings();
  }

  /** {@code text} (e.g. "+") before the input, shown while the label is up. */
  public void xgramSetPrefix (@Nullable CharSequence text) {
    if (!xgram) {
      return;
    }
    if (xgramPrefix != null) {
      removeView(xgramPrefix);
      xgramPrefix = null;
    }
    if (!TextUtils.isEmpty(text)) {
      xgramPrefix = com.fahadali.xgram.appearance.XgramR6B8Login.newPrefix(getContext(), text);
      xgramPrefix.setPadding(0, editText.getPaddingTop(), 0, editText.getPaddingBottom());
      xgramPrefix.setGravity(Gravity.CENTER_VERTICAL);
      xgramPrefix.setAlpha(factor);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (isRtl ? Gravity.RIGHT : Gravity.LEFT));
      params.topMargin = ((FrameLayoutFix.LayoutParams) editText.getLayoutParams()).topMargin;
      addView(xgramPrefix, params);
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(xgramPrefix);
      }
    }
    xgramUpdatePaddings();
  }

  private void xgramUpdatePaddings () {
    final int start = Screen.dp(xgramLeading != null ? 52f : 16f);
    final int end = Screen.dp(xgramTrailing != null ? 52f : 16f);
    int prefix = 0;
    if (xgramPrefix != null) {
      prefix = (int) Math.ceil(xgramPrefix.getPaint().measureText(xgramPrefix.getText().toString())) + Screen.dp(2f);
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) xgramPrefix.getLayoutParams();
      params.leftMargin = isRtl ? 0 : start;
      params.rightMargin = isRtl ? start : 0;
      xgramPrefix.setLayoutParams(params);
    }
    editText.setPadding(isRtl ? end : start + prefix, editText.getPaddingTop(), isRtl ? start + prefix : end, editText.getPaddingBottom());
    if (hintView != null) {
      hintView.setPadding(isRtl ? end : start, 0, isRtl ? start : end, 0);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (xgram && (xgramSupporting != null || xgramSupportingCounter)) {
      // the room under the input for the supporting text row: the counter's line, or the supporting text's lines
      int room = xgramSupportingCounter ? Screen.dp(20f) : 0;
      if (xgramSupporting != null && xgramSupporting.getVisibility() != View.GONE) {
        FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) xgramSupporting.getLayoutParams();
        final int widthSpec;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
          widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else {
          widthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight() - params.leftMargin - params.rightMargin), MeasureSpec.EXACTLY);
        }
        xgramSupporting.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        room = Math.max(room, xgramSupporting.getMeasuredHeight());
      }
      FrameLayoutFix.LayoutParams editParams = (FrameLayoutFix.LayoutParams) editText.getLayoutParams();
      editParams.bottomMargin = room;
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (xgram && (xgramLeading != null || xgramTrailing != null)) {
      // the leading and trailing elements sit in the middle of the container (the label and the input), not of the
      // whole group (the supporting text is under it)
      final int center = editText.getBottom() / 2;
      for (View v : new View[] {xgramLeading, xgramTrailing}) {
        if (v != null && v.getVisibility() != View.GONE) {
          final int h = v.getMeasuredHeight();
          v.layout(v.getLeft(), center - h / 2, v.getRight(), center - h / 2 + h);
        }
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    super.onDraw(c);
    if (!xgram) {
      return;
    }
    if (hintView != null) {
      final int hintColor = XgramExpressive.color(xgramHintRole());
      if (hintView.getCurrentTextColor() != hintColor) {
        hintView.setTextColor(hintColor); // a theme change set the placeholder colour again
      }
    }
    final int bottom = editText.getBottom();
    if (bottom <= 0 || getWidth() <= 0) {
      return;
    }
    if (xgramPaint == null) {
      xgramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      xgramPaint.setStyle(Paint.Style.FILL);
    }
    final float r = Screen.dpf(XgramExpressive.CORNER_XS);
    xgramRadii[0] = xgramRadii[1] = xgramRadii[2] = xgramRadii[3] = r;
    xgramRect.set(editText.getLeft() + editText.getTranslationX(), 0, editText.getRight() + editText.getTranslationX(), bottom);
    xgramPath.rewind();
    xgramPath.addRoundRect(xgramRect, xgramRadii, Path.Direction.CW);
    xgramPaint.setColor(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST));
    c.drawPath(xgramPath, xgramPaint);
  }

  private boolean isRtl;

  public void applyRtl (boolean rtl) {
    if (this.isRtl != rtl) {
      this.isRtl = rtl;
      editText.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
      if (hintView != null)
        hintView.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
    }
  }

  private int maxLength = -1;
  private boolean calculateMaxLengthInCodePoints;

  private int getTextLength () {
    if (calculateMaxLengthInCodePoints) {
      CharSequence text = editText.getText();
      return Character.codePointCount(text, 0, text.length());
    } else {
      return editText.getText().length();
    }
  }

  private void updateRemainingCharCount () {
    if (lengthCounter != null && xgramSupportingCounter) {
      // XGram M3E: "length/max" always shown, bodySmall onSurfaceVariant (error when over the limit)
      if (maxLength == -1) {
        lengthCounter.setText("");
      } else {
        final int length = getTextLength();
        lengthCounter.setText(length + "/" + maxLength);
        lengthCounter.setTextColor(XgramExpressive.color(length > maxLength ? XgramExpressive.ERROR : XgramExpressive.ON_SURFACE_VARIANT));
      }
      return;
    }
    if (lengthCounter != null) {
      if (maxLength == -1) {
        lengthCounter.setText("");
      } else {
        final int remaining = maxLength - getTextLength();
        if (remaining > 50) {
          lengthCounter.setText("");
        } else {
          lengthCounter.setText(Strings.buildCounter(remaining));
          lengthCounter.setTextColor(Theme.getColor(remaining <= 0 ? ColorId.textNegative : ColorId.textLight));
        }
      }
    }
  }

  public void setMaxLength (int maxLength) {
    setMaxLength(maxLength, true);
  }

  public void setMaxLength (int maxLength, boolean calculateMaxLengthInCodePoints) {
    if (this.maxLength != maxLength || this.calculateMaxLengthInCodePoints != calculateMaxLengthInCodePoints) {
      this.maxLength = maxLength;
      this.calculateMaxLengthInCodePoints = calculateMaxLengthInCodePoints;
      addLengthCounter(false);
      updateRemainingCharCount();
    }
  }

  public void onInputActiveFactorChange (float factor) {
    if (lengthCounter != null && !xgramSupportingCounter) {
      lengthCounter.setAlpha(factor);
    }
  }

  // XGram M3E: the counter sits in the supporting-text row under the filled field, aligned to the end
  private boolean xgramSupportingCounter;

  public void addLengthCounter (boolean reduceOffset) {
    if (lengthCounter == null && xgram && !reduceOffset) {
      editText.setParent(this);
      xgramSupportingCounter = true;

      final int supportingRowHeight = Screen.dp(20f); // 4dp padding + 16dp bodySmall line
      FrameLayoutFix.LayoutParams editParams = (FrameLayoutFix.LayoutParams) editText.getLayoutParams();
      editParams.bottomMargin = supportingRowHeight;
      editText.setLayoutParams(editParams);

      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.leftMargin = params.rightMargin = Screen.dp(16f);
      params.gravity = Gravity.BOTTOM | (isRtl ? Gravity.LEFT : Gravity.RIGHT);

      lengthCounter = new NoScrollTextView(getContext());
      lengthCounter.setTypeface(Fonts.getRobotoRegular());
      lengthCounter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
      XgramExpressive.applyText(lengthCounter, XgramExpressive.TS_BODY_S, false);
      lengthCounter.setSingleLine(true);
      lengthCounter.setIncludeFontPadding(false);
      lengthCounter.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
      lengthCounter.setLayoutParams(params);
      addView(lengthCounter);
      updateRemainingCharCount();
      return;
    }
    if (lengthCounter == null) {
      editText.setParent(this);

      FrameLayoutFix.LayoutParams params;
      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.topMargin = reduceOffset ? Screen.dp(19f) : Screen.dp(20f) + Screen.dp(11f);
      params.leftMargin = params.rightMargin = Screen.dp(6f);
      params.gravity = isRtl ? Gravity.LEFT : Gravity.RIGHT;

      lengthCounter = new NoScrollTextView(getContext());
      lengthCounter.setTextColor(Theme.textDecentColor());
      lengthCounter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      lengthCounter.setTypeface(Fonts.getRobotoRegular());
      lengthCounter.setAlpha(0f);
      lengthCounter.setLayoutParams(params);

      params = hintView != null ? (FrameLayoutFix.LayoutParams) hintView.getLayoutParams() : null;
      if (isRtl) {
        if (params != null)
          params.leftMargin = Screen.dp(40f);
        editText.setPadding(editText.getPaddingLeft() + Screen.dp(32f), editText.getPaddingTop(), editText.getPaddingRight(), editText.getPaddingBottom());
      } else {
        if (params != null)
          params.rightMargin = Screen.dp(40f);
        editText.setPadding(editText.getPaddingLeft(), editText.getPaddingTop(), editText.getPaddingRight() + Screen.dp(32f), editText.getPaddingBottom());
      }

      addView(lengthCounter);
    }
  }

  private @Nullable ViewController<?> themeProvider;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(editText, textColorId);
      if (hintView != null)
        themeProvider.addThemeTextColorListener(hintView, ColorId.textPlaceholder);
      themeProvider.addThemeInvalidateListener(editText);
      themeProvider.addThemeHighlightColorListener(editText, ColorId.textSelectionHighlight);
      themeProvider.addThemeHintTextColorListener(editText, ColorId.textPlaceholder);
      if (lengthCounter != null && !xgramSupportingCounter) {
        themeProvider.addThemeTextColorListener(lengthCounter, ColorId.textLight);
      }
      if (checkboxView != null) {
        themeProvider.addThemeInvalidateListener(checkboxView);
      }
    }
  }

  private static final int ANIMATOR_RADIO_VISIBILITY = 6;
  private CheckBoxView checkboxView;
  private BoolAnimator checkboxVisible;
  private boolean checkboxAtRight;

  public void setCheckboxAtRight (boolean atRight) {
    this.checkboxAtRight = atRight;
  }

  public void setCheckboxVisible (boolean visible, boolean circular, boolean animated) {
    if (checkboxView != null || visible) {
      addCheckbox();
      checkboxView.setCircular(circular, animated && checkboxVisible.getFloatValue() > 0f);
      checkboxVisible.setValue(visible, animated);
    }
  }

  private TooltipOverlayView.TooltipInfo radioTooltip;

  public void showCheckboxHint (ViewController<?> controller, Tdlib tdlib, int stringRes) {
    if (checkboxView == null) {
      addCheckbox();
    }
    if (radioTooltip != null)
      radioTooltip.hide(true);
    radioTooltip = UI.getContext(getContext())
      .tooltipManager()
      .builder(checkboxView)
      .controller(controller)
      .offset(outRect ->
        outRect.offset((checkboxAtRight ? 1f : -1f) * Screen.dp(40f) * (1f - checkboxVisible.getFloatValue()), 0)
      )
      .show(tdlib, stringRes)
      .hideDelayed();
  }

  public void setCheckboxSelected (boolean isActive, boolean animated) {
    if (checkboxVisible != null || isActive) {
      addCheckbox();
      checkboxView.setChecked(isActive, animated);
    }
  }

  public interface RadioClickListener {
    void onCheckboxClick (MaterialEditTextGroup editText, CheckBoxView radioView);
  }

  private RadioClickListener radioClickListener;

  public void setOnRadioClickListener(RadioClickListener radioClickListener) {
    this.radioClickListener = radioClickListener;
  }

  public CheckBoxView addCheckbox () {
    if (checkboxView == null) {
      int size = Screen.dp(18f);
      int padding = Screen.dp(19f);
      checkboxView = CheckBoxView.simpleCheckBox(getContext(), true);
      checkboxView.setLayoutParams(FrameLayoutFix.newParams(size + padding * 2, size + padding * 2, (checkboxAtRight ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
      checkboxView.setPadding(padding, padding, padding, padding);
      checkboxView.setOnClickListener(v -> {
        if (radioClickListener != null) {
          radioClickListener.onCheckboxClick(this, checkboxView);
        }
      });
      checkboxView.setAlpha(0f);
      ((ViewGroup) getParent()).addView(checkboxView);

      checkboxVisible = new BoolAnimator(ANIMATOR_RADIO_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    return checkboxView;
  }

  private void setRadioVisibility (float factor) {
    checkboxView.setAlpha(factor);
    final float dir = checkboxAtRight ? -1f : 1f;
    checkboxView.setTranslationX(-dir * Screen.dp(40f) * (1f - factor));
    editText.setTranslationX(dir * Screen.dp(40f) * factor);
    if (radioTooltip != null) {
      radioTooltip.reposition();
    }
  }

  public void setFocusListener (@Nullable FocusListener focusListener) {
    this.focusListener = focusListener;
  }

  public void setEmptyListener (@Nullable EmptyListener emptyListener) {
    this.emptyListener = emptyListener;
  }

  public void setDoneListener (@Nullable DoneListener listener) {
    boolean hadListener = this.doneListener != null;
    this.doneListener = listener;
    if (!hadListener && listener != null) {
      editText.setOnEditorActionListener(this);
    } else if (hadListener && listener == null) {
      editText.setOnEditorActionListener(null);
    }
  }

  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    return (keyCode == KeyEvent.KEYCODE_ENTER && doneListener != null && doneListener.onTextDonePressed(this)) || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
    return U.isImeDone(actionId, event) && doneListener != null && doneListener.onTextDonePressed(this);
  }

  public CharSequence getText () {
    return editText.getText();
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

  private @Nullable CharSequence blockedText;

  public void setBlockedText (CharSequence blockedText) {
    setBlockedText(blockedText, false);
  }

  public void setBlockedText (CharSequence blockedText, boolean animated) {
    this.blockedText = blockedText;
    if (blockedText != null && !getText().toString().equals(blockedText)) {
      this.blockedText = null;
      setText(blockedText, animated);
      this.blockedText = blockedText;
    }
  }

  private CharSequence lastInput;
  private boolean ignoreChanges;

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    if (ignoreChanges) {
      return;
    }
    if (blockedText != null && !blockedText.equals(s)) {
      ignoreChanges = true;
      setText(blockedText);
      ignoreChanges = false;
      return;
    }

    if (!StringUtils.isEmpty(s)) {
      updateIsActive(true);
    }

    updateRemainingCharCount();
    if (lastInput == null || !lastInput.equals(s)) {
      this.lastInput = EditText.nonModifiableCopy(s);
      if (useTextChangeAnimations && hasFocus) {
        forceAlphaFactor(StringUtils.trim(s).length() > 0 ? 1f : 0f);
      }
      setIsNotEmpty(!StringUtils.isEmpty(s));
      if (textListener != null) {
        textListener.onTextChanged(this, s);
      }
    }
  }

  @Override
  public void afterTextChanged (Editable s) { }

  public @NonNull MaterialEditText getEditText () {
    return editText;
  }

  public void setHint (@StringRes int hintResId) {
    if (hintView != null)
      hintView.setText(Lang.getString(hintResId));
  }

  public void setHint (CharSequence hint) {
    if (hintView != null)
      hintView.setText(hint);
  }

  public void setEmptyHint (@StringRes int hintResId) {
    editText.setHint(hintResId != 0 ? Lang.getString(hintResId) : null);
  }

  public boolean isEmpty () {
    return editText.getText().length() == 0;
  }

  private boolean useTextChangeAnimations;

  public void setUseTextChangeAnimations () {
    useTextChangeAnimations = true;
    editText.setTextColor(calculateTextColor(0f));
  }

  public void setText (CharSequence text) {
    setText(text, false);
  }

  private void setTextImpl (CharSequence text) {
    editText.setText(text);
    editText.setSelection(text != null ? text.length() : 0);
  }

  public void setText (CharSequence text, boolean animated) {
    boolean isActive = (text != null && text.length() > 0) || hasFocus || !StringUtils.isEmpty(editText.getHint());
    if (animated && useTextChangeAnimations) {
      if (isActive) {
        setTextImpl(text);
      }
      setIsActive(isActive);
    } else {
      setTextImpl(text);
      this.isActive = isActive;
      forceFactor(isActive ? 1f : 0f);
    }
  }

  private static int calculateTextColor (float alpha) {
    return ColorUtils.color((int) (255f * alpha), Theme.textAccentColor());
  }

  private boolean alwaysActive;

  public void setAlwaysActive (boolean alwaysActive) {
    if (this.alwaysActive != alwaysActive) {
      this.alwaysActive = alwaysActive;
      updateIsActive(false);
    }
  }

  private boolean hasFocus;

  private void updateIsActive (boolean animated) {
    setIsActive(alwaysActive || hasFocus || !isEmpty() || !StringUtils.isEmpty(editText.getHint()), animated);
  }

  @Override
  public void onFocusChange (View v, boolean hasFocus) {
    if (this.hasFocus != hasFocus) {
      this.hasFocus = hasFocus;
      xgramUpdateHintColor();
      updateIsActive(true);
      if (focusListener != null) {
        focusListener.onFocusChanged(this, hasFocus);
      }
    }
  }

  public boolean hasFocusInternal () {
    return hasFocus;
  }

  private boolean isActive;

  public void setIsActive (boolean isActive) {
    setIsActive(isActive, true);
  }

  public void setIsActive (boolean isActive, boolean animated) {
    if (this.isActive != isActive) {
      this.isActive = isActive;
      editText.setIsActive(hasFocus, false);
      if (useTextChangeAnimations && !isActive && editText.getText().toString().trim().length() > 0) {
        animateAlphaFactor(0f);
      } else if (animated) {
        animateFactor(isActive ? 1f : 0f);
      } else {
        forceFactor(isActive ? 1f : 0f);
      }
    } else {
      editText.setIsActive(hasFocus, animated);
    }
  }

  private FactorAnimator animator;

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
    if (useTextChangeAnimations) {
      forceAlphaFactor(factor);
    }
  }

  private static final int ANIMATOR_TEXT_ALPHA = 5;
  private FactorAnimator textAlphaAnimator;
  private float textAlpha;

  private void forceAlphaFactor (float factor) {
    if (textAlphaAnimator != null) {
      textAlphaAnimator.forceFactor(factor);
    }
    setTextAlphaFactor(factor);
  }

  private void animateAlphaFactor (float factor) {
    if (textAlphaAnimator == null) {
      textAlphaAnimator = new FactorAnimator(ANIMATOR_TEXT_ALPHA, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION - 20l, textAlpha);
      com.fahadali.xgram.appearance.XgramAppMotion.use(textAlphaAnimator, XgramExpressive.EFFECTS_FAST, true); // XGram M3E r6
    }
    textAlphaAnimator.animateTo(factor);
  }

  private void setTextAlphaFactor (float factor) {
    if (this.textAlpha != factor) {
      this.textAlpha = factor;
      editText.setTextColor(calculateTextColor(factor));
    }
  }

  private float factor;

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      updateScale();
      editText.applyActiveFactor(factor);
      if (xgramPrefix != null) {
        xgramPrefix.setAlpha(Math.max(0f, Math.min(1f, factor)));
      }
    }
  }

  private void updateScale () {
    if (hintView != null && xgram) {
      // XGram M3E r6: bodyLarge up to bodySmall (16 to 12)
      final float scaleFactor = factor * (1f - reverseScaleFactor);
      float scale = 1f - .25f * scaleFactor;
      hintView.setScaleX(scale);
      hintView.setScaleY(scale);
      hintView.setTranslationY((float) -Screen.dp(20f) * scaleFactor);
      return;
    }
    if (hintView != null) {
      final float scaleFactor = factor * (1f - reverseScaleFactor);
      float scale = 1f - .23076923f * scaleFactor;
      hintView.setScaleX(scale);
      hintView.setScaleY(scale);
      hintView.setTranslationY((float) -Screen.dp(20f) * scaleFactor);
    }
  }

  private static final int ANIMATOR_FACTOR = 0;
  private static final long DURATION = 150l;

  private void animateFactor (float toFactor) {
    if (animator == null) {
      animator = new FactorAnimator(ANIMATOR_FACTOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION, factor);
      com.fahadali.xgram.appearance.XgramAppMotion.use(animator, XgramExpressive.STD_SPATIAL_FAST, true); // XGram M3E r6: the label floats on a spring
    }
    animator.animateTo(toFactor);
  }

  private static final int ANIMATOR_REVERSE_SCALE = 2;

  private float reverseScaleFactor;
  private FactorAnimator reverseScaleAnimator;

  private void animateReverseScaleFactor () {
    if (reverseScaleAnimator == null) {
      reverseScaleAnimator = new FactorAnimator(ANIMATOR_REVERSE_SCALE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);
    } else {
      reverseScaleAnimator.forceFactor(0f);
    }
    reverseScaleAnimator.animateTo(1f);
  }

  private void setReverseScaleFactor (float factor) {
    if (this.reverseScaleFactor != factor) {
      this.reverseScaleFactor = factor;
      updateScale();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FACTOR: {
        setFactor(factor);
        break;
      }
      case ANIMATOR_FADE: {
        setFadeFactor(factor);
        break;
      }
      case ANIMATOR_REVERSE_SCALE: {
        setReverseScaleFactor(1f - factor);
        break;
      }
      case ANIMATOR_ERROR: {
        editText.setErrorFactor(factor);
        break;
      }
      case ANIMATOR_GOOD: {
        editText.setGoodFactor(factor);
        break;
      }
      case ANIMATOR_TEXT_ALPHA: {
        setTextAlphaFactor(factor);
        break;
      }
      case ANIMATOR_RADIO_VISIBILITY: {
        setRadioVisibility(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FACTOR: {
        if (finalFactor == 1f && useTextChangeAnimations && editText.getText().toString().trim().length() > 0) {
          animateAlphaFactor(1f);
        }
        break;
      }
      case ANIMATOR_TEXT_ALPHA: {
        if (finalFactor == 0f && useTextChangeAnimations && !isActive) {
          setTextImpl("");
          animateFactor(isActive ? 1f : 0f);
        }
        break;
      }
    }
  }

  private static final int ANIMATOR_FADE = 1;
  private FactorAnimator fadeAnimator;
  private float fadeFactor;

  private void setFadeFactor (float factor) {
    if (this.fadeFactor != factor) {
      this.fadeFactor = factor;

      final float easeFactor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor);
      final float alphaFactor = easeFactor <= .5f ? easeFactor / .5f : 1f - ((easeFactor - .5f) / .5f);
      if (easeFactor > .5f && !pendingApplied) {
        applyPending();
      }

      editText.setTextColor(calculateTextColor(1f - alphaFactor));
      if (hintView != null)
        hintView.setAlpha(1f - alphaFactor);
    }
  }

  private CharSequence pendingHint, pendingText;
  private boolean pendingTextIsPassword;
  private @Nullable Runnable pendingBetween;

  public void resetWithHint (@StringRes int pendingHint, boolean isPassword, Runnable between) {
    resetWithHint(Lang.getString(pendingHint), null, isPassword, between);
  }

  public void resetWithHint (@StringRes int pendingHint, @Nullable String text, boolean isPassword, Runnable between) {
    resetWithHint(Lang.getString(pendingHint), text, isPassword, between);
  }

  private boolean pendingApplied;

  private void applyPending () {
    if (!pendingApplied) {
      pendingApplied = true;
      editText.setIsPassword(pendingTextIsPassword);
      if (!StringUtils.isEmpty(pendingText)) {
        editText.setText(pendingText);
        editText.setSelection(pendingText.length());
      } else {
        editText.setText("");
      }
      if (hintView != null)
        hintView.setText(pendingHint);
      if (pendingBetween != null) {
        pendingBetween.run();
      }
    }
  }

  public void resetWithHint (final CharSequence pendingHint, final @Nullable CharSequence text, boolean isPassword, Runnable between) {
    if (fadeAnimator == null) {
      fadeAnimator = new FactorAnimator(ANIMATOR_FADE, this, AnimatorUtils.LINEAR_INTERPOLATOR, 360l, fadeFactor);
      com.fahadali.xgram.appearance.XgramAppMotion.use(fadeAnimator, XgramExpressive.EFFECTS_SLOW, true); // XGram M3E r6: out and in on one spring
    } else {
      fadeAnimator.forceFactor(0f);
      fadeFactor = 0f;
    }

    this.pendingApplied = false;
    this.pendingHint = pendingHint;
    this.pendingText = text;
    this.pendingTextIsPassword = isPassword;
    this.pendingBetween = between;
    fadeAnimator.animateTo(1f);
  }

  private FactorAnimator errorAnimator;
  private static final int ANIMATOR_ERROR = 3;

  private boolean inErrorState;

  public void setInErrorState (boolean inErrorState) {
    if (this.inErrorState != inErrorState) {
      this.inErrorState = inErrorState;
      animateErrorState(inErrorState ? 1f : 0f);
      xgramUpdateHintColor(); // XGram M3E r6: the label in error too
    }
  }

  public boolean inErrorState () {
    return inErrorState;
  }

  private void animateErrorState (float toFactor) {
    if (errorAnimator == null) {
      errorAnimator = new FactorAnimator(ANIMATOR_ERROR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      com.fahadali.xgram.appearance.XgramAppMotion.use(errorAnimator, XgramExpressive.EFFECTS_FAST, true); // XGram M3E r6
    }
    errorAnimator.animateTo(toFactor);
  }

  private FactorAnimator goodAnimator;
  private static final int ANIMATOR_GOOD = 4;

  private boolean inGoodState;

  public void setInGoodState (boolean inGoodState) {
    if (this.inGoodState != inGoodState) {
      this.inGoodState = inGoodState;
      animateGoodState(inGoodState ? 1f : 0f);
    }
  }

  private void animateGoodState (float toFactor) {
    if (goodAnimator == null) {
      goodAnimator = new FactorAnimator(ANIMATOR_GOOD, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      com.fahadali.xgram.appearance.XgramAppMotion.use(goodAnimator, XgramExpressive.EFFECTS_FAST, true); // XGram M3E r6
    }
    goodAnimator.animateTo(toFactor);
  }
}
