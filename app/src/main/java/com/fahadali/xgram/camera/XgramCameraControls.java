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
package com.fahadali.xgram.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.Range;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ExposureState;
import androidx.camera.extensions.ExtensionMode;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3MediaChrome;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.camera.x.CameraManagerX;

import java.util.Locale;

/**
 * XGram's camera controls over the CameraX preview (Settings → Testing utilities → Camera X): the lenses as chips
 * (".6", "1×", "3×", the zoom while between them), the phone's own camera modes (Night, Photo, Portrait, HDR), pinch to
 * zoom, tap to focus and meter (a ring, with an exposure slider beside it), long press to lock focus and exposure.
 * They sit on the preview's side of the shutter wherever the window turned it, and turn their labels with the phone.
 */
public final class XgramCameraControls extends FrameLayout implements CameraManagerX.XgramListener {
  public interface Host {
    /** The shutter: the controls sit next to it, on the preview's side. */
    @Nullable View shutter ();

    /** Where the shutter is: Gravity.BOTTOM, TOP, LEFT or RIGHT (the window's rotation). */
    int shutterGravity ();

    /** The camera's preview view: taps turn into its coordinates. */
    @Nullable View preview ();

    void showHint (CharSequence text);
  }

  private final Host host;
  private final LensRow lenses;
  private final ModeRow modes;
  private final FocusView focus;
  private final ScaleGestureDetector scaleDetector;
  private final GestureDetector tapDetector;
  private @Nullable CameraManagerX camera;
  private boolean scaling;

  public XgramCameraControls (Context context, Host host) {
    super(context);
    this.host = host;
    setClipChildren(false);
    setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    focus = new FocusView(context, this);
    focus.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(focus);

    lenses = new LensRow(context, this);
    lenses.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(lenses);

    modes = new ModeRow(context, this);
    modes.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(modes);

    scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
      private float pinchZoom;

      @Override
      public boolean onScaleBegin (@NonNull ScaleGestureDetector detector) {
        scaling = true;
        pinchZoom = camera != null ? camera.getCurrentZoom() : 0f;
        return camera != null && pinchZoom > 0f;
      }

      @Override
      public boolean onScale (@NonNull ScaleGestureDetector detector) {
        if (camera != null && pinchZoom > 0f) {
          // Follows the fingers, not the camera's state (a frame or more late)
          float max = camera.getMaxZoom(), min = camera.getMinZoom();
          pinchZoom = Math.max(min, Math.min(max > 0f ? max : pinchZoom, pinchZoom * detector.getScaleFactor()));
          lenses.setZoom(pinchZoom, false);
          camera.xgramSetZoom(pinchZoom, true);
        }
        return true;
      }
    });
    tapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onDown (@NonNull MotionEvent e) {
        return true;
      }

      @Override
      public boolean onSingleTapUp (@NonNull MotionEvent e) {
        if (!scaling) {
          focusAt(e.getX(), e.getY(), false);
        }
        return true;
      }

      @Override
      public void onLongPress (@NonNull MotionEvent e) {
        if (!scaling) {
          focusAt(e.getX(), e.getY(), true);
        }
      }
    });
  }

  /** Listens to {@code camera} (null: none, the usual camera without these controls). */
  public void bind (@Nullable CameraManagerX camera) {
    if (this.camera == camera) {
      return;
    }
    if (this.camera != null) {
      this.camera.xgramSetListener(null);
    }
    this.camera = camera;
    focus.hide(false);
    if (camera != null) {
      camera.xgramSetListener(this);
    } else {
      lenses.setStops(new float[] {1f});
      modes.setModes(new int[] {ExtensionMode.NONE}, ExtensionMode.NONE);
    }
  }

  public @Nullable CameraManagerX camera () {
    return camera;
  }

  // Camera events

  @Override
  public void onCameraBound () {
    if (camera == null) {
      return;
    }
    lenses.setStops(camera.xgramLensStops());
    lenses.setZoom(camera.getCurrentZoom(), false);
    modes.setModes(camera.xgramModes(), camera.xgramMode());
    focus.hide(false);
    requestLayout();
  }

  @Override
  public void onZoomRatio (float ratio) {
    lenses.onCameraZoom(ratio);
  }

  @Override
  public void onVideoUnavailable (int mode) {
    host.showHint(Lang.getString(R.string.XgramCameraNoVideo, modeName(mode)));
  }

  // From the camera screen

  /** The phone turned while the window didn't: labels turn with it (degrees, animated by the screen). */
  public void setContentRotation (float rotation) {
    lenses.setContentRotation(rotation);
    modes.setContentRotation(rotation);
  }

  /**
   * The screen's controls appearing ({@code appear}, the shutter's) and the side controls hiding while a video records
   * ({@code controls}): the lenses follow the shutter, the modes the side buttons.
   */
  public void setAppearance (float appear, float controls) {
    lenses.setAlpha(appear);
    lenses.setEnabled(appear >= 1f);
    modes.setAlpha(controls);
    modes.setEnabled(controls >= 1f);
    if (appear < 1f) {
      focus.hide(false);
    }
  }

  /** A touch on the preview (root coordinates): pinch, tap, long press. */
  public void onPreviewTouch (MotionEvent e) {
    if (camera == null || !isShown()) {
      return;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      scaling = false;
    }
    scaleDetector.onTouchEvent(e);
    if (e.getPointerCount() > 1) {
      scaling = true;
    }
    tapDetector.onTouchEvent(e);
  }

  private void focusAt (float x, float y, boolean lock) {
    View preview = host.preview();
    if (camera == null || preview == null || camera.xgramIsBusy()) {
      return;
    }
    // The preview's place in this view (they share a parent chain up to the camera screen's root)
    float px = x, py = y;
    View v = preview;
    while (v != null && v != getParent()) {
      px -= v.getLeft() - v.getScrollX() + v.getTranslationX();
      py -= v.getTop() - v.getScrollY() + v.getTranslationY();
      v = v.getParent() instanceof View ? (View) v.getParent() : null;
    }
    if (px < 0 || py < 0 || px > preview.getWidth() || py > preview.getHeight()) {
      return;
    }
    if (camera.xgramFocusAt(px, py, lock)) {
      performHapticFeedback(lock ? android.view.HapticFeedbackConstants.LONG_PRESS : android.view.HapticFeedbackConstants.CONTEXT_CLICK);
      focus.show(x - getLeft(), y - getTop(), lock, camera.xgramExposure());
    }
  }

  void setZoom (float ratio) {
    if (camera != null && !camera.xgramIsBusy()) {
      lenses.setZoom(ratio, true); // the lens shows at once, the highlight slides to it
      camera.xgramSetZoom(ratio, false);
    }
  }

  void setMode (int mode) {
    if (camera != null && !camera.xgramIsBusy() && camera.xgramMode() != mode) {
      focus.hide(false);
      modes.setModes(camera.xgramModes(), mode);
      camera.xgramSetMode(mode);
    }
  }

  void setExposure (int index) {
    if (camera != null) {
      camera.xgramSetExposure(index);
    }
  }

  static String modeName (int mode) {
    switch (mode) {
      case ExtensionMode.NIGHT:
        return Lang.getString(R.string.XgramCameraNight);
      case ExtensionMode.BOKEH:
        return Lang.getString(R.string.XgramCameraPortrait);
      case ExtensionMode.HDR:
        return Lang.getString(R.string.XgramCameraHdr);
      default:
        return Lang.getString(R.string.XgramCameraPhoto);
    }
  }

  // Layout: on the preview's side of the shutter

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    focus.layout(0, 0, right - left, bottom - top);
    View shutter = host.shutter();
    if (shutter == null || shutter.getWidth() == 0) {
      lenses.layout(0, 0, 0, 0);
      modes.layout(0, 0, 0, 0);
      return;
    }
    final int gravity = host.shutterGravity();
    final boolean vertical = gravity == Gravity.LEFT || gravity == Gravity.RIGHT;
    lenses.setVertical(vertical);
    modes.setVertical(vertical);
    final int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    lenses.measure(unspecified, unspecified);
    modes.measure(unspecified, unspecified);
    final int lw = lenses.getMeasuredWidth(), lh = lenses.getMeasuredHeight();
    final int mw = modes.getMeasuredWidth(), mh = modes.getMeasuredHeight();
    final int cx = (shutter.getLeft() + shutter.getRight()) / 2, cy = (shutter.getTop() + shutter.getBottom()) / 2;
    final int gap = Screen.dp(14f), between = Screen.dp(10f);
    // From the shutter out: the modes (in the band under the preview, as camera apps have them), then the lenses
    switch (gravity) {
      case Gravity.TOP: {
        int mt = shutter.getBottom() + gap;
        modes.layout(cx - mw / 2, mt, cx - mw / 2 + mw, mt + mh);
        int lt = mt + (mh > 0 && modes.getVisibility() == VISIBLE ? mh + between : 0);
        lenses.layout(cx - lw / 2, lt, cx - lw / 2 + lw, lt + lh);
        break;
      }
      case Gravity.RIGHT: {
        int mr = shutter.getLeft() - gap;
        modes.layout(mr - mw, cy - mh / 2, mr, cy - mh / 2 + mh);
        int lr = mr - (mw > 0 && modes.getVisibility() == VISIBLE ? mw + between : 0);
        lenses.layout(lr - lw, cy - lh / 2, lr, cy - lh / 2 + lh);
        break;
      }
      case Gravity.LEFT: {
        int ml = shutter.getRight() + gap;
        modes.layout(ml, cy - mh / 2, ml + mw, cy - mh / 2 + mh);
        int ll = ml + (mw > 0 && modes.getVisibility() == VISIBLE ? mw + between : 0);
        lenses.layout(ll, cy - lh / 2, ll + lw, cy - lh / 2 + lh);
        break;
      }
      case Gravity.BOTTOM:
      default: {
        int mb = shutter.getTop() - gap;
        modes.layout(cx - mw / 2, mb - mh, cx - mw / 2 + mw, mb);
        int lb = mb - (mh > 0 && modes.getVisibility() == VISIBLE ? mh + between : 0);
        lenses.layout(cx - lw / 2, lb - lh, cx - lw / 2 + lw, lb);
        break;
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    final int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    lenses.measure(unspecified, unspecified);
    modes.measure(unspecified, unspecified);
    focus.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
  }

  // Drawing helpers

  private static TextPaint labelPaint (boolean emphasized, float sizeSp) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, emphasized));
    paint.setTextSize(Screen.sp(sizeSp));
    paint.setTextAlign(Paint.Align.CENTER);
    return paint;
  }

  private static float textCenterOffset (Paint paint) {
    Paint.FontMetrics m = paint.getFontMetrics();
    return -(m.ascent + m.descent) / 2f;
  }

  /** ".6", "1×", "3×"; the zoom between lenses with one decimal ("1.8×"). */
  static String zoomLabel (float ratio, boolean selected) {
    float rounded = Math.round(ratio * 10f) / 10f;
    String number = Math.abs(rounded - Math.round(rounded)) < .05f ? String.valueOf(Math.round(rounded)) : String.format(Locale.US, "%.1f", rounded);
    if (rounded < 1f && !selected) {
      return number.startsWith("0") ? number.substring(1) : number; // ".6"
    }
    return number + "×";
  }

  // The lenses: a pill of round chips

  private static final class LensRow extends View {
    private static final float CHIP = 34f, SELECTED = 42f, PADDING = 4f, GAP = 4f;

    private final XgramCameraControls parent;
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG), chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG), selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = labelPaint(false, 12f), selectedLabelPaint = labelPaint(true, 13f);
    private final RectF rect = new RectF();
    private float[] stops = {1f};
    private float zoom = 1f, contentRotation;
    private boolean vertical;
    /** The highlight's place (a chip index, fractional while it slides) and where it slides to. */
    private float highlight = -1f;
    private @Nullable android.animation.ValueAnimator slide;
    /** Until this time the camera's zoom state (late, or between the old ratio and the new) doesn't move the chips. */
    private long holdUntil;

    LensRow (Context context, XgramCameraControls parent) {
      super(context);
      this.parent = parent;
      containerPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER), .6f));
      chipPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGHEST), .7f));
      selectedPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.PRIMARY_CONTAINER));
      labelPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE));
      selectedLabelPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_PRIMARY_CONTAINER));
    }

    void setStops (float[] stops) {
      this.stops = stops != null && stops.length > 0 ? stops : new float[] {1f};
      this.highlight = -1f; // placed again on the next zoom
      setVisibility(this.stops.length > 1 ? VISIBLE : GONE);
      requestLayout();
      invalidate();
    }

    /** The zoom the controls set: a lens tap slides the highlight to its chip, a pinch moves it at once. */
    void setZoom (float zoom, boolean slideToLens) {
      if (zoom <= 0f) {
        return;
      }
      this.zoom = zoom;
      holdUntil = android.os.SystemClock.uptimeMillis() + 600L;
      int target = selectedIndex();
      if (slideToLens && highlight >= 0f && Math.abs(highlight - target) > .01f) {
        if (slide != null) {
          slide.cancel();
        }
        slide = android.animation.ValueAnimator.ofFloat(highlight, target);
        slide.setDuration(220L);
        slide.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        slide.addUpdateListener(a -> {
          highlight = (float) a.getAnimatedValue();
          invalidate();
        });
        slide.start();
      } else {
        if (slide != null) {
          slide.cancel();
          slide = null;
        }
        highlight = target;
      }
      invalidate();
    }

    /** The camera's own zoom state: followed unless the controls just set one (it arrives late, or in between). */
    void onCameraZoom (float zoom) {
      if (zoom <= 0f || android.os.SystemClock.uptimeMillis() < holdUntil || (slide != null && slide.isRunning())) {
        return;
      }
      if (Math.abs(this.zoom - zoom) > .01f) {
        this.zoom = zoom;
        if (slide == null || !slide.isRunning()) {
          highlight = selectedIndex();
        }
        invalidate();
      }
    }

    /** Set while the controls lay out (they measure it right after). */
    void setVertical (boolean vertical) {
      this.vertical = vertical;
    }

    void setContentRotation (float rotation) {
      if (this.contentRotation != rotation) {
        this.contentRotation = rotation;
        invalidate();
      }
    }

    /** The lens the zoom is on: the longest one at or below it. */
    private int selectedIndex () {
      int index = 0;
      for (int i = 0; i < stops.length; i++) {
        if (zoom + .05f >= stops[i]) {
          index = i;
        }
      }
      return index;
    }

    private float slot () {
      return Screen.dp(SELECTED) + Screen.dp(GAP);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int length = (int) (Screen.dp(PADDING) * 2 + stops.length * Screen.dp(SELECTED) + (stops.length - 1) * Screen.dp(GAP));
      int thickness = Screen.dp(SELECTED) + Screen.dp(PADDING) * 2;
      setMeasuredDimension(vertical ? thickness : length, vertical ? length : thickness);
    }

    private float centerOf (int i) {
      return Screen.dp(PADDING) + Screen.dp(SELECTED) / 2f + i * slot();
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      if (stops.length <= 1) {
        return;
      }
      final float radius = Math.min(getWidth(), getHeight()) / 2f;
      rect.set(0, 0, getWidth(), getHeight());
      c.drawRoundRect(rect, radius, radius, containerPaint);
      final int selected = selectedIndex();
      final float at = highlight >= 0f ? highlight : selected;
      // The highlight: a filled circle that slides between the chips
      final float hAlong = Screen.dp(PADDING) + Screen.dp(SELECTED) / 2f + at * slot();
      final float hx = vertical ? getWidth() / 2f : hAlong, hy = vertical ? hAlong : getHeight() / 2f;
      for (int i = 0; i < stops.length; i++) {
        float along = centerOf(i);
        float cx = vertical ? getWidth() / 2f : along;
        float cy = vertical ? along : getHeight() / 2f;
        c.drawCircle(cx, cy, Screen.dp(CHIP) / 2f, chipPaint);
      }
      c.drawCircle(hx, hy, Screen.dp(SELECTED) / 2f, selectedPaint);
      for (int i = 0; i < stops.length; i++) {
        float along = centerOf(i);
        float cx = vertical ? getWidth() / 2f : along;
        float cy = vertical ? along : getHeight() / 2f;
        boolean isSelected = i == selected && Math.abs(at - selected) < .5f;
        TextPaint paint = isSelected ? selectedLabelPaint : labelPaint;
        String label = zoomLabel(isSelected ? zoom : stops[i], isSelected);
        if (contentRotation != 0f) {
          c.save();
          c.rotate(contentRotation, cx, cy);
        }
        c.drawText(label, cx, cy + textCenterOffset(paint), paint);
        if (contentRotation != 0f) {
          c.restore();
        }
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!isEnabled() || stops.length <= 1) {
        return false;
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP) {
        float along = vertical ? e.getY() : e.getX();
        int index = (int) ((along - Screen.dp(PADDING)) / slot());
        index = Math.max(0, Math.min(stops.length - 1, index));
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        parent.setZoom(stops[index]);
      }
      return true;
    }
  }

  // The modes: Night · Photo · Portrait · HDR

  private static final class ModeRow extends View {
    private static final float HEIGHT = 32f, PADDING_H = 14f, GAP = 2f;

    private final XgramCameraControls parent;
    private final TextPaint paint = labelPaint(false, 14f), selectedTextPaint = labelPaint(true, 14f);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] modes = {ExtensionMode.NONE};
    private String[] labels = {""};
    private float[] widths = {0f};
    private int selected = ExtensionMode.NONE;
    private float contentRotation;
    private boolean vertical;

    ModeRow (Context context, XgramCameraControls parent) {
      super(context);
      this.parent = parent;
      paint.setColor(0xffffffff);
      paint.setShadowLayer(Screen.dp(2f), 0, Screen.dp(.5f), 0x99000000);
      selectedPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.SECONDARY_CONTAINER));
      selectedTextPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SECONDARY_CONTAINER));
      setVisibility(GONE);
    }

    void setModes (int[] modes, int selected) {
      this.modes = modes != null && modes.length > 0 ? modes : new int[] {ExtensionMode.NONE};
      this.selected = selected;
      this.labels = new String[this.modes.length];
      this.widths = new float[this.modes.length];
      for (int i = 0; i < this.modes.length; i++) {
        labels[i] = modeName(this.modes[i]);
        widths[i] = Math.max(paint.measureText(labels[i]), selectedTextPaint.measureText(labels[i])) + Screen.dp(PADDING_H) * 2;
      }
      setVisibility(this.modes.length > 1 ? VISIBLE : GONE);
      requestLayout();
      invalidate();
    }

    /** Set while the controls lay out (they measure it right after). */
    void setVertical (boolean vertical) {
      this.vertical = vertical;
    }

    void setContentRotation (float rotation) {
      if (this.contentRotation != rotation) {
        this.contentRotation = rotation;
        invalidate();
      }
    }

    private float maxWidth () {
      float max = 0;
      for (float w : widths) {
        max = Math.max(max, w);
      }
      return max;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (vertical) {
        setMeasuredDimension((int) Math.ceil(maxWidth()), (int) (modes.length * Screen.dp(HEIGHT) + (modes.length - 1) * Screen.dp(GAP)));
      } else {
        float total = 0;
        for (float w : widths) {
          total += w;
        }
        setMeasuredDimension((int) Math.ceil(total + (modes.length - 1) * Screen.dp(GAP)), Screen.dp(HEIGHT));
      }
    }

    /** The cell of mode {@code i}: its place in this view. */
    private void cellOf (int i, RectF out) {
      if (vertical) {
        float top = i * (Screen.dp(HEIGHT) + Screen.dp(GAP));
        float w = maxWidth();
        out.set((getWidth() - w) / 2f, top, (getWidth() + w) / 2f, top + Screen.dp(HEIGHT));
      } else {
        float left = 0;
        for (int j = 0; j < i; j++) {
          left += widths[j] + Screen.dp(GAP);
        }
        out.set(left, 0, left + widths[i], Screen.dp(HEIGHT));
      }
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      for (int i = 0; i < modes.length; i++) {
        cellOf(i, rect);
        boolean isSelected = modes[i] == selected;
        float cx = rect.centerX(), cy = rect.centerY();
        if (isSelected) {
          float radius = rect.height() / 2f;
          c.drawRoundRect(rect, radius, radius, selectedPaint);
        }
        TextPaint textPaint = isSelected ? selectedTextPaint : paint;
        if (contentRotation != 0f) {
          c.save();
          c.rotate(contentRotation, cx, cy);
        }
        c.drawText(labels[i], cx, cy + textCenterOffset(textPaint), textPaint);
        if (contentRotation != 0f) {
          c.restore();
        }
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!isEnabled() || modes.length <= 1) {
        return false;
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP) {
        for (int i = 0; i < modes.length; i++) {
          cellOf(i, rect);
          rect.inset(0, -Screen.dp(8f));
          if (rect.contains(e.getX(), e.getY())) {
            if (modes[i] != selected) {
              performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
              parent.setMode(modes[i]);
            }
            break;
          }
        }
      }
      return true;
    }
  }

  // Focus: a ring where the finger was, the exposure slider beside it

  private static final class FocusView extends View {
    private static final float RING = 36f, TRACK = 128f, TRACK_GAP = 58f, KNOB = 11f;
    private static final long SHOW_MS = 3000L, LOCKED_MS = 6000L;

    private final XgramCameraControls parent;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG), trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG), knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG), pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillTextPaint = labelPaint(true, 12f);
    private final RectF rect = new RectF();
    private final Runnable hideRunnable = () -> hide(true);
    private float x, y, appear;
    private boolean visible, locked, dragging;
    private @Nullable Range<Integer> exposureRange;
    private int exposureIndex;
    private long shownAt;

    FocusView (Context context, XgramCameraControls parent) {
      super(context);
      this.parent = parent;
      ringPaint.setStyle(Paint.Style.STROKE);
      ringPaint.setStrokeWidth(Screen.dp(1.5f));
      trackPaint.setStyle(Paint.Style.STROKE);
      trackPaint.setStrokeWidth(Screen.dp(1.5f));
      trackPaint.setStrokeCap(Paint.Cap.ROUND);
      pillPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.TERTIARY_CONTAINER));
      pillTextPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_TERTIARY_CONTAINER));
    }

    void show (float x, float y, boolean locked, @Nullable ExposureState exposure) {
      this.x = x;
      this.y = y;
      this.locked = locked;
      if (exposure != null && exposure.isExposureCompensationSupported()) {
        Range<Integer> range = exposure.getExposureCompensationRange();
        this.exposureRange = range.getLower() < range.getUpper() ? range : null;
        this.exposureIndex = exposure.getExposureCompensationIndex();
      } else {
        this.exposureRange = null;
      }
      this.visible = true;
      this.shownAt = android.os.SystemClock.uptimeMillis();
      this.appear = 0f;
      removeCallbacks(hideRunnable);
      postDelayed(hideRunnable, locked ? LOCKED_MS : SHOW_MS);
      postInvalidateOnAnimation();
    }

    void hide (boolean animated) {
      removeCallbacks(hideRunnable);
      if (dragging) {
        postDelayed(hideRunnable, SHOW_MS);
        return;
      }
      if (visible) {
        visible = false;
        invalidate();
      }
    }

    private boolean trackOnRight () {
      return x + Screen.dp(TRACK_GAP) + Screen.dp(24f) <= getWidth();
    }

    private float trackX () {
      return trackOnRight() ? x + Screen.dp(TRACK_GAP) : x - Screen.dp(TRACK_GAP);
    }

    private float knobY () {
      if (exposureRange == null) {
        return y;
      }
      float fraction = (exposureIndex - exposureRange.getLower()) / (float) (exposureRange.getUpper() - exposureRange.getLower());
      return y + Screen.dp(TRACK) / 2f - fraction * Screen.dp(TRACK);
    }

    @Override
    protected void onDraw (@NonNull Canvas c) {
      if (!visible) {
        return;
      }
      long elapsed = android.os.SystemClock.uptimeMillis() - shownAt;
      appear = Math.min(1f, elapsed / 200f);
      float eased = 1f - (1f - appear) * (1f - appear);
      int ringColor = locked ? XgramM3MediaChrome.onMedia(XgramExpressive.TERTIARY) : 0xffffffff;
      ringPaint.setColor(ringColor);
      ringPaint.setAlpha((int) (255 * Math.min(1f, eased * 1.4f)));
      float radius = Screen.dp(RING) * (1.35f - .35f * eased);
      c.drawCircle(x, y, radius, ringPaint);
      c.drawCircle(x, y, Screen.dp(2f), ringPaint);
      if (exposureRange != null) {
        float tx = trackX();
        float top = y - Screen.dp(TRACK) / 2f, bottom = y + Screen.dp(TRACK) / 2f;
        float ky = knobY();
        trackPaint.setColor(0xccffffff);
        // The track breaks around the knob
        if (ky - Screen.dp(KNOB) - Screen.dp(3f) > top) {
          c.drawLine(tx, top, tx, ky - Screen.dp(KNOB) - Screen.dp(3f), trackPaint);
        }
        if (ky + Screen.dp(KNOB) + Screen.dp(3f) < bottom) {
          c.drawLine(tx, ky + Screen.dp(KNOB) + Screen.dp(3f), tx, bottom, trackPaint);
        }
        // The knob: a sun
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(ringColor);
        c.drawCircle(tx, ky, Screen.dp(5f), knobPaint);
        knobPaint.setStyle(Paint.Style.STROKE);
        knobPaint.setStrokeWidth(Screen.dp(1.5f));
        knobPaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
          double angle = Math.PI / 4 * i;
          float sx = (float) Math.cos(angle), sy = (float) Math.sin(angle);
          c.drawLine(tx + sx * Screen.dp(7.5f), ky + sy * Screen.dp(7.5f), tx + sx * Screen.dp(KNOB), ky + sy * Screen.dp(KNOB), knobPaint);
        }
      }
      if (locked) {
        String text = Lang.getString(R.string.XgramCameraLocked);
        float w = pillTextPaint.measureText(text) + Screen.dp(24f), h = Screen.dp(28f);
        float top = Math.max(Screen.dp(8f), y - Screen.dp(RING) - Screen.dp(16f) - h);
        float left = Math.max(Screen.dp(8f), Math.min(getWidth() - Screen.dp(8f) - w, x - w / 2f));
        rect.set(left, top, left + w, top + h);
        c.drawRoundRect(rect, h / 2f, h / 2f, pillPaint);
        c.drawText(text, rect.centerX(), rect.centerY() + textCenterOffset(pillTextPaint), pillTextPaint);
      }
      if (appear < 1f) {
        postInvalidateOnAnimation();
      }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!visible || exposureRange == null) {
        return false;
      }
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          float tx = trackX();
          if (Math.abs(e.getX() - tx) > Screen.dp(28f) || e.getY() < y - Screen.dp(TRACK) / 2f - Screen.dp(20f) || e.getY() > y + Screen.dp(TRACK) / 2f + Screen.dp(20f)) {
            return false; // not the slider: the preview's tap or pinch
          }
          dragging = true;
          getParent().requestDisallowInterceptTouchEvent(true);
          removeCallbacks(hideRunnable);
          updateExposure(e.getY());
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (dragging) {
            updateExposure(e.getY());
          }
          return dragging;
        }
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
          if (dragging) {
            dragging = false;
            postDelayed(hideRunnable, locked ? LOCKED_MS : SHOW_MS);
          }
          return true;
        }
      }
      return dragging;
    }

    private void updateExposure (float touchY) {
      if (exposureRange == null) {
        return;
      }
      float fraction = (y + Screen.dp(TRACK) / 2f - touchY) / Screen.dp(TRACK);
      fraction = Math.max(0f, Math.min(1f, fraction));
      int index = Math.round(exposureRange.getLower() + fraction * (exposureRange.getUpper() - exposureRange.getLower()));
      if (index != exposureIndex) {
        exposureIndex = index;
        if (index == 0) {
          performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
        parent.setExposure(index);
        invalidate();
      }
    }
  }
}
