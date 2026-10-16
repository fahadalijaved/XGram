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
 * File created on 03/02/2016 at 10:27
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.v.HeaderEditText;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.TdConstants;

public class EditHeaderView extends FrameLayoutFix implements RtlCheckListener, Destroyable, StretchyHeaderView, TextWatcher, HeaderView.OffsetChangeListener {
  private final ViewController<?> parent;
  private final HeaderEditText input;
  private final ImageReceiver receiver;
  private final Path clipPath = new Path();

  private Drawable icon;
  private @PorterDuffColorId int iconColorId = ColorId.white;

  public EditHeaderView (Context context, ViewController<?> parent) {
    super(context);
    this.icon = Drawables.get(getResources(), xgram ? com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_camera_alt_24) : R.drawable.baseline_camera_alt_24);
    this.parent = parent;

    setWillNotDraw(false);

    receiver = new ImageReceiver(this, 0);
    receiver.prepareToBeCropped();

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(62f));
    params.topMargin = Screen.dp(62f);

    setLayoutParams(params);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.gravity = Lang.gravity() | Gravity.CENTER_VERTICAL;

    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(20f);
      params.rightMargin = Screen.dp(96f);
    } else {
      params.rightMargin = Screen.dp(20f);
      params.leftMargin = Screen.dp(96f);
    }

    input = HeaderEditText.create(this, false, null);
    input.setHint(Lang.getString(R.string.ChannelName));
    input.addTextChangedListener(this);
    input.checkRtl();
    input.setLayoutParams(params);
    input.setFilters(new InputFilter[] {
      new CodePointCountFilter(TdConstants.MAX_CHAT_TITLE_LENGTH),
      new CharacterStyleFilter()
    });
    if (xgram) {
      com.fahadali.xgram.appearance.XgramExpressive.applyText(input, com.fahadali.xgram.appearance.XgramExpressive.TS_TITLE_L, false); // XGram M3E
    }
    addView(input);
  }

  @Override
  public void onHeaderOffsetChanged (HeaderView headerView, int newOffset) {
    Views.setTopMargin(this, newOffset + Screen.dp(62f));
  }

  @Override
  public void checkRtl () {
    input.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    int leftMargin = Screen.dp(96f);
    int rightMargin = Screen.dp(20f);
    if (Views.setGravity(input, Gravity.CENTER_VERTICAL | Lang.gravity()) && Views.setMargins(input, Lang.rtl() ? rightMargin : leftMargin, 0, Lang.rtl() ? leftMargin : rightMargin, 0)) {
      Views.updateLayoutParams(input);
    }
    invalidate();
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

  @Override
  public void afterTextChanged (Editable s) { }

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    performReadyCallback(s.toString().trim().length() > 0);
  }

  private Runnable onPhotoClickListener;

  public void setOnPhotoClickListener (Runnable onPhotoClickListener) {
    this.onPhotoClickListener = onPhotoClickListener;
  }

  private void onPhotoClicked () {
    if (input.isEnabled()) {
      Keyboard.hide(input);
      if (onPhotoClickListener != null) {
        onPhotoClickListener.run();
      }
      ViewUtils.onClick(this);
    }
  }

  public interface ReadyCallback {
    void onReadyStateChanged (boolean ready);
  }

  private static final int FLAG_READY = 0x01;
  private static final int FLAG_IGNORE_READY = 0x02;
  private int flags;
  private ReadyCallback callback;

  private void performReadyCallback (boolean isReady) {
    if ((isReady && (flags & FLAG_READY) != 0) || !isReady && (flags & FLAG_READY) == 0) {
      return;
    }

    if (isReady) {
      flags |= FLAG_READY;
    } else {
      flags &= ~FLAG_READY;
    }

    if (callback != null && (flags & FLAG_IGNORE_READY) == 0) {
      callback.onReadyStateChanged(isReady);
    }
  }

  public void setReadyCallback (ReadyCallback callback) {
    this.callback = callback;
  }

  public boolean isInputEmpty () {
    return input.getText().toString().trim().length() == 0;
  }

  public String getInput () {
    return input.getText().toString();
  }

  public EditText getInputView () {
    return input;
  }

  public void setInput (CharSequence text) {
    if (text != null) {
      flags |= FLAG_IGNORE_READY;
      input.setText(text);
      input.setSelection(text.length());
      flags &= ~FLAG_IGNORE_READY;
    }
  }

  public void setInputOptions (@StringRes int hintResId, int inputType) {
    if (hintResId != 0) {
      input.setHint(Lang.getString(hintResId));
    }
    if (inputType != 0) {
      input.setInputType(inputType);
    }
  }

  public void setNextField (int id) {
    input.setNextFocusDownId(id);
  }

  public void setImeOptions (int imeOptions) {
    input.setImeOptions(imeOptions);
  }

  private float scaleFactor;

  /*@Override
  public float getScaleFactor () {
    return scaleFactor;
  }*/

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    scaleFactor = Size.convertExpandedFactor(scaleFactor);
    this.scaleFactor = scaleFactor;
    layoutReceiver();
    scaleFactor = (1f - scaleFactor);
    if (scaleFactor == 0f) {
      setTranslationY(0f);
      input.setTranslationX(0f);
      input.setTranslationY(0f);
    } else {
      input.setTranslationX(scaleFactor * Screen.dp(20f));
      input.setTranslationY(scaleFactor * -Screen.dp(10f));
      setTranslationY(-Size.getHeaderPortraitSize() * scaleFactor);
    }
    invalidate();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceiver();
  }

  private int avatarRadius;

  private void layoutReceiver () {
    int margin = Screen.dp(36f);
    int paddingLeft = Screen.dp(4f);
    avatarRadius = Screen.dp(20.5f) + (int) ((float) Screen.dp(10f) * scaleFactor);
    int avatarSize = avatarRadius * 2;

    int avatarCenterXDiff = -Screen.dp(53.5f);
    int avatarLeft = paddingLeft + avatarRadius + margin + (int) ((float) avatarCenterXDiff * scaleFactor);
    if (Lang.rtl()) {
      avatarLeft = getMeasuredWidth() - avatarLeft - avatarSize;
    }

    // receiver.setRadius(avatarRadius);
    receiver.setBounds(avatarLeft, 0, avatarLeft + avatarSize, avatarSize);
    clipPath.reset();
    clipPath.addCircle(receiver.centerX(), receiver.centerY(), avatarSize / 2f, Path.Direction.CW);
    clipPath.close();
  }

  // XGram M3E: the photo picker is an M3 avatar picker: a primaryContainer circle holding the camera (icon pack) in
  // onPrimaryContainer, the picked photo in the same shape; pressed, its corners spring from full to 16dp
  // (SPATIAL_FAST, a snap with Reduce motion); an "add" badge (a primary circle with an onPrimary plus, ringed in the
  // app bar color) at its bottom end. The name is typed in titleLarge.

  private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  private final Path xgramShape = new Path();
  private final android.graphics.RectF xgramRect = new android.graphics.RectF();
  private @androidx.annotation.Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPress;

  private com.fahadali.xgram.appearance.XgramExpressive.Spring xgramPress () {
    if (xgramPress == null) {
      xgramPress = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> invalidate());
      xgramPress.setThreshold(.005f);
    }
    return xgramPress;
  }

  private void xgramSetPressed (boolean pressed) {
    if (xgram) {
      xgramPress().animateTo(pressed ? 1f : 0f);
    }
  }

  private void xgramDraw (Canvas c) {
    final float size = avatarRadius * 2f;
    xgramRect.set(receiver.getLeft(), receiver.getTop(), receiver.getLeft() + size, receiver.getTop() + size);
    final float press = xgramPress != null ? xgramPress.getValue() : 0f;
    final float radius = com.fahadali.xgram.appearance.XgramExpressiveC.morphCorner(com.fahadali.xgram.appearance.XgramExpressive.CORNER_FULL, com.fahadali.xgram.appearance.XgramExpressive.CORNER_L, size, size, press);
    xgramShape.rewind();
    xgramShape.addRoundRect(xgramRect, radius, radius, Path.Direction.CW);
    final boolean hasPhoto = file != null;
    if (hasPhoto) {
      int s = Views.save(c);
      c.clipPath(xgramShape);
      receiver.draw(c);
      Views.restore(c, s);
    } else {
      c.drawPath(xgramShape, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY_CONTAINER)));
      final float cx = xgramRect.centerX(), cy = xgramRect.centerY();
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY_CONTAINER)));
    }
    // The "add" badge at the bottom end
    final float badge = Screen.dp(20f), ring = Screen.dp(2f);
    final float bx = Lang.rtl() ? xgramRect.left + badge / 2f - ring : xgramRect.right - badge / 2f + ring;
    final float by = xgramRect.bottom - badge / 2f + ring;
    c.drawCircle(bx, by, badge / 2f + ring, Paints.fillingPaint(org.thunderdog.challegram.theme.Theme.headerColor()));
    c.drawCircle(bx, by, badge / 2f, Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.PRIMARY)));
    final float arm = Screen.dp(5f), stroke = Screen.dp(1f);
    final android.graphics.Paint plus = Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY));
    c.drawRect(bx - arm, by - stroke, bx + arm, by + stroke, plus);
    c.drawRect(bx - stroke, by - arm, bx + stroke, by + arm, plus);
  }

  @Override
  protected void onDraw (Canvas c) {
    layoutReceiver();
    if (xgram) {
      xgramDraw(c);
      return;
    }
    int s = Views.save(c);
    c.clipPath(clipPath);
    receiver.draw(c);
    Views.restore(c, s);
    int cx = receiver.centerX();
    int cy = receiver.centerY();
    c.drawCircle(cx, cy, avatarRadius, Paints.fillingPaint(0x20000000));
    Drawables.draw(c, icon, cx - (int) (icon.getMinimumWidth() * .5f), cy - (int) (icon.getMinimumHeight() * .5f), PorterDuffPaint.get(iconColorId));
  }

  public void setInputEnabled (boolean enabled) {
    input.setEnabled(enabled);
  }

  private ImageGalleryFile file;

  public void setPhoto (ImageGalleryFile file) {
    this.file = file;
    receiver.requestFile(file);
  }

  public void setIcon (@DrawableRes int iconRes, @PorterDuffColorId int colorId) {
    icon = Drawables.get(getResources(), xgram ? com.fahadali.xgram.appearance.XgramIconPack.map(iconRes) : iconRes);
    iconColorId = colorId;
    invalidate();
  }

  public ImageGalleryFile getImageFile () {
    return file;
  }

  private boolean caughtPhoto;
  private float cx, cy;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (receiver != null) {
      receiver.attach();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (receiver != null) {
      receiver.detach();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX(), y = e.getY();
        if (receiver.isInsideReceiver(x, y)) {
          caughtPhoto = true;
          cx = x;
          cy = y;
          xgramSetPressed(true);
          return true;
        } else {
          caughtPhoto = false;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caughtPhoto) {
          if (Math.max(Math.abs(e.getX() - cx), Math.abs(e.getY() - cy)) > Screen.getTouchSlop()) {
            caughtPhoto = false;
            xgramSetPressed(false);
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (caughtPhoto) {
          onPhotoClicked();
          caughtPhoto = false;
        }
        xgramSetPressed(false);
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        caughtPhoto = false;
        xgramSetPressed(false);
        break;
      }
    }
    return super.onTouchEvent(e);
  }
}
