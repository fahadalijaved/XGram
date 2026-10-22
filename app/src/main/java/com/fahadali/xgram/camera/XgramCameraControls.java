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
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Range;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ExposureState;
import androidx.camera.extensions.ExtensionMode;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramM3MediaChrome;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.camera.x.CameraManagerX;

import java.util.List;
import java.util.Locale;

/**
 * XGram's camera controls over the CameraX preview (Settings → Testing utilities → Camera X): the lenses as chips
 * (".6", "1×", "3×", the zoom while between them), the phone's own camera modes (Night, Photo, Portrait, HDR), pinch to
 * zoom, tap to focus and meter (a ring, with an exposure slider beside it), long press to lock focus and exposure.
 * They sit on the preview's side of the shutter wherever the window turned it; the lens labels turn with the phone
 * (the mode names stay level, as in camera apps: turned, they would not fit their pills).
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
  /** The screen's appearance (see setAppearance) and the modes dimmed while the screen is blocked (setBlocked). */
  private float appear = 1f, controls = 1f, blockFactor;
  private boolean blocked;
  private @Nullable android.animation.ValueAnimator blockAnimator;
  /**
   * The shutter is laid out after these controls (it is above them in the camera screen), so when it moves (Flex mode,
   * unfolding, a turn) the rows were placed from where it was: they follow its new place in the same layout pass.
   */
  private @Nullable View observedShutter;
  private final View.OnLayoutChangeListener shutterLayoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
      checkFocusPlace();
      layoutRows();
    }
  };
  /**
   * Where the preview was (in this view's parent) when the focus marks were placed. A lock or a brightness stays as a
   * mark until the next tap, so when the preview moves or resizes without a new binding (Flex mode, unfolding with the
   * 16:9 ratio) the mark would sit over another part of the picture: it goes then, and a lock with it (checkFocusPlace).
   */
  private float focusPreviewLeft, focusPreviewTop;
  private int focusPreviewWidth, focusPreviewHeight;

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

  /**
   * The phone turned while the window didn't: the lens labels turn with it (degrees, animated by the screen). The mode
   * names stay level, as camera apps keep them: turned, a name like "Portrait" stood far out of its 32dp pill.
   */
  public void setContentRotation (float rotation) {
    lenses.setContentRotation(rotation);
  }

  /**
   * The screen's controls appearing ({@code appear}, the shutter's) and the side controls hiding while a video records
   * ({@code controls}): the lenses follow the shutter, the modes the side buttons.
   */
  public void setAppearance (float appear, float controls) {
    this.appear = appear;
    this.controls = controls;
    updateAppearance();
    if (appear < 1f) {
      focus.hide(false);
    }
  }

  /**
   * The camera screen is blocked: a photo is on its way, a video records or is being saved. The modes wait, dimmed as
   * a disabled control (a mode's capture takes seconds, and binding the camera again would throw the photo away). The
   * dimming starts after a moment, so a quick photo doesn't flicker the row.
   */
  public void setBlocked (boolean blocked) {
    if (this.blocked == blocked) {
      return;
    }
    this.blocked = blocked;
    if (blockAnimator != null) {
      blockAnimator.cancel();
    }
    blockAnimator = android.animation.ValueAnimator.ofFloat(blockFactor, blocked ? 1f : 0f);
    blockAnimator.setDuration(blocked ? 180L : 120L);
    blockAnimator.setStartDelay(blocked ? 250L : 0L);
    blockAnimator.addUpdateListener(a -> {
      blockFactor = (float) a.getAnimatedValue();
      updateAppearance();
    });
    blockAnimator.start();
    updateAppearance();
  }

  private void updateAppearance () {
    lenses.setAlpha(appear);
    lenses.setEnabled(appear >= 1f);
    modes.setAlpha(controls * (1f - .62f * blockFactor)); // 38% at most: a disabled control
    modes.setEnabled(controls >= 1f && !blocked);
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
    // Not while the camera is bound again or a photo is on its way; a recording video focuses too (CameraX meters
    // while it records)
    if (camera == null || preview == null || camera.xgramIsSwitching() || camera.isTakingPhoto()) {
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
      focusPreviewLeft = x - px;
      focusPreviewTop = y - py;
      focusPreviewWidth = preview.getWidth();
      focusPreviewHeight = preview.getHeight();
      focus.show(x - getLeft(), y - getTop(), lock, camera.xgramExposure());
      if (lock) {
        announceForAccessibility(Lang.getString(R.string.XgramCameraLocked));
      }
    }
  }

  /** A lens chip: false when the camera can't zoom now (no tick then). */
  boolean setZoom (float ratio) {
    // Only while the camera is bound again: a recording video zooms too (CameraX zooms while it records, as the pinch
    // and the shutter's drag already did)
    if (camera == null || camera.xgramIsSwitching()) {
      return false;
    }
    if (focus.isLocked() && Math.abs(camera.xgramZoomTarget() - ratio) > .05f) {
      // Another lens lets go of the focus and exposure lock (as camera apps do): it was set through the old one
      camera.xgramCancelFocus();
      focus.unlock();
    }
    lenses.setZoom(ratio, true); // the lens shows at once, the highlight slides to it
    camera.xgramSetZoom(ratio, false);
    return true;
  }

  /** A mode: false when the camera can't switch now (no tick then). */
  boolean setMode (int mode) {
    if (camera == null || camera.xgramIsBusy() || camera.xgramMode() == mode) {
      return false;
    }
    focus.hide(false);
    modes.setModes(camera.xgramModes(), mode);
    camera.xgramSetMode(mode);
    modes.announceForAccessibility(modeName(mode));
    return true;
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
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    View shutter = host.shutter();
    if (shutter != null && shutter != observedShutter) {
      if (observedShutter != null) {
        observedShutter.removeOnLayoutChangeListener(shutterLayoutListener);
      }
      observedShutter = shutter;
      shutter.addOnLayoutChangeListener(shutterLayoutListener);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (observedShutter != null) {
      observedShutter.removeOnLayoutChangeListener(shutterLayoutListener);
      observedShutter = null;
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    focus.layout(0, 0, right - left, bottom - top);
    checkFocusPlace();
    layoutRows();
  }

  /**
   * The focus marks go once the preview moved or resized under them (see focusPreviewLeft). The preview comes before
   * these controls in the camera screen, so its new place is known here and in the shutter's layout.
   */
  private void checkFocusPlace () {
    if (!focus.isPlaced()) {
      return;
    }
    final View preview = host.preview();
    float left = 0, top = 0;
    View v = preview;
    while (v != null && v != getParent()) {
      left += v.getLeft() - v.getScrollX() + v.getTranslationX();
      top += v.getTop() - v.getScrollY() + v.getTranslationY();
      v = v.getParent() instanceof View ? (View) v.getParent() : null;
    }
    if (preview == null || preview.getWidth() != focusPreviewWidth || preview.getHeight() != focusPreviewHeight || Math.abs(left - focusPreviewLeft) > .5f || Math.abs(top - focusPreviewTop) > .5f) {
      if (focus.isLocked() && camera != null) {
        camera.xgramCancelFocus(); // the lock goes with its mark (as with another lens): never a lock with nothing shown
      }
      focus.hide(false);
    }
  }

  /** The rows next to the shutter where it is now (also from the shutter's own layout: see shutterLayoutListener). */
  private void layoutRows () {
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
    // A row of modes wider than the screen (large fonts, a cover screen) shrinks to fit, 16dp from each edge
    modes.setMaxLength((vertical ? getHeight() : getWidth()) - Screen.dp(16f) * 2);
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

  /** {@code sizeSp} with the font size setting, up to {@code maxDp}: the chips and pills it sits in are fixed in dp. */
  private static TextPaint labelPaint (boolean emphasized, float sizeSp, float maxDp) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(XgramExpressive.typeface(XgramExpressive.TS_LABEL_L, emphasized));
    paint.setTextSize(Math.min(Screen.sp(sizeSp), Screen.dp(maxDp)));
    paint.setTextAlign(Paint.Align.CENTER);
    return paint;
  }

  private static float textCenterOffset (Paint paint) {
    Paint.FontMetrics m = paint.getFontMetrics();
    return -(m.ascent + m.descent) / 2f;
  }

  /** {@code text} centered on (cx, cy), {@code scale} of the paint's size and smaller still if wider than maxWidth. */
  private static void drawLabel (Canvas c, String text, float cx, float cy, TextPaint paint, float scale, float maxWidth) {
    final float size = paint.getTextSize();
    float target = size * scale;
    final float width = paint.measureText(text) * scale;
    if (maxWidth > 0f && width > maxWidth) {
      target *= maxWidth / width;
    }
    if (target != size) {
      paint.setTextSize(target);
    }
    c.drawText(text, cx, cy + textCenterOffset(paint), paint);
    if (target != size) {
      paint.setTextSize(size);
    }
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

  // TalkBack: the rows paint their chips and modes themselves, so each is a virtual button

  /** What a row paints, one item each (its place in the row, its name, whether it is in use, its tap). */
  private interface Items {
    int itemCount ();

    void itemBounds (int index, Rect out);

    CharSequence itemLabel (int index);

    boolean isItemSelected (int index);

    /** False: nothing happened (the camera is busy). */
    boolean clickItem (int index);
  }

  /** A row's items as buttons (selected: the one in use), listed while the row shows, tappable while it is enabled. */
  private static final class RowA11y extends ExploreByTouchHelper {
    private final View row;
    private final Items items;
    private final Rect bounds = new Rect();

    RowA11y (@NonNull View row, @NonNull Items items) {
      super(row);
      this.row = row;
      this.items = items;
    }

    private boolean isListed () {
      return row.isShown() && row.getAlpha() > 0f;
    }

    @Override
    protected int getVirtualViewAt (float x, float y) {
      if (!isListed()) {
        return INVALID_ID;
      }
      for (int i = 0; i < items.itemCount(); i++) {
        items.itemBounds(i, bounds);
        if (bounds.contains((int) x, (int) y)) {
          return i;
        }
      }
      return INVALID_ID;
    }

    @Override
    protected void getVisibleVirtualViews (List<Integer> virtualViewIds) {
      if (!isListed()) {
        return;
      }
      for (int i = 0; i < items.itemCount(); i++) {
        virtualViewIds.add(i);
      }
    }

    @Override
    protected void onPopulateNodeForVirtualView (int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
      if (virtualViewId < 0 || virtualViewId >= items.itemCount()) {
        // Gone since TalkBack asked (the row changed): a node needs a description and bounds all the same
        node.setContentDescription("");
        bounds.setEmpty();
        //noinspection deprecation
        node.setBoundsInParent(bounds);
        return;
      }
      node.setClassName("android.widget.Button");
      node.setContentDescription(items.itemLabel(virtualViewId));
      node.setSelected(items.isItemSelected(virtualViewId));
      node.setEnabled(row.isEnabled());
      items.itemBounds(virtualViewId, bounds);
      //noinspection deprecation
      node.setBoundsInParent(bounds);
      if (row.isEnabled()) {
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
      }
    }

    @Override
    protected boolean onPerformActionForVirtualView (int virtualViewId, int action, @Nullable Bundle arguments) {
      if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !row.isEnabled() || virtualViewId < 0 || virtualViewId >= items.itemCount()) {
        return false;
      }
      if (items.clickItem(virtualViewId)) {
        invalidateRoot();
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
      }
      return true;
    }
  }

  // The lenses: a pill of round chips

  private static final class LensRow extends View implements Items {
    private static final float CHIP = 34f, SELECTED = 42f, PADDING = 4f, GAP = 4f;

    private final XgramCameraControls parent;
    private final RowA11y a11y;
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG), chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG), selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = labelPaint(false, 12f, 15f), selectedLabelPaint = labelPaint(true, 13f, 16f);
    private final RectF rect = new RectF();
    private float[] stops = {1f};
    private float zoom = 1f, contentRotation;
    private boolean vertical;
    /** The highlight's place (a chip index, fractional while it slides) and where it slides to. */
    private float highlight = -1f;
    private @Nullable android.animation.ValueAnimator slide;
    /** Until this time the camera's zoom state (late, or between the old ratio and the new) doesn't move the chips. */
    private long holdUntil;
    /** The lens TalkBack last heard of as selected. */
    private int a11ySelected = -1;

    LensRow (Context context, XgramCameraControls parent) {
      super(context);
      this.parent = parent;
      containerPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER), .6f));
      chipPaint.setColor(XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER_HIGHEST), .7f));
      selectedPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.PRIMARY_CONTAINER));
      labelPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE));
      selectedLabelPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_PRIMARY_CONTAINER));
      a11y = new RowA11y(this, this);
      ViewCompat.setAccessibilityDelegate(this, a11y);
    }

    void setStops (float[] stops) {
      this.stops = stops != null && stops.length > 0 ? stops : new float[] {1f};
      this.highlight = -1f; // placed again on the next zoom
      setVisibility(this.stops.length > 1 ? VISIBLE : GONE);
      requestLayout();
      invalidate();
      a11ySelected = -1;
      a11y.invalidateRoot();
    }

    /** TalkBack hears of the lens in use when it changes (not on every zoom step). */
    private void updateA11ySelected () {
      int selected = selectedIndex();
      if (a11ySelected != selected) {
        a11ySelected = selected;
        a11y.invalidateRoot();
      }
    }

    @Override
    public void setEnabled (boolean enabled) {
      if (enabled != isEnabled()) {
        super.setEnabled(enabled);
        if (a11y != null) {
          a11y.invalidateRoot();
        }
      }
    }

    @Override
    protected boolean dispatchHoverEvent (MotionEvent e) {
      return a11y.dispatchHoverEvent(e) || super.dispatchHoverEvent(e);
    }

    @Override
    public int itemCount () {
      return stops.length > 1 ? stops.length : 0;
    }

    @Override
    public void itemBounds (int index, Rect out) {
      final int along = Math.round(centerOf(index)), half = Math.round(slot() / 2f);
      if (vertical) {
        out.set(0, along - half, getWidth(), along + half);
      } else {
        out.set(along - half, 0, along + half, getHeight());
      }
    }

    @Override
    public CharSequence itemLabel (int index) {
      final boolean isSelected = index == selectedIndex();
      return Lang.getString(R.string.XgramCameraLensA11y, zoomLabel(isSelected ? zoom : stops[index], true));
    }

    @Override
    public boolean isItemSelected (int index) {
      return index == selectedIndex();
    }

    @Override
    public boolean clickItem (int index) {
      return parent.setZoom(stops[index]);
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
      updateA11ySelected();
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
        updateA11ySelected();
      }
    }

    /** Set while the controls lay out (they measure it right after). */
    void setVertical (boolean vertical) {
      if (this.vertical != vertical) {
        this.vertical = vertical;
        forceLayout(); // measure() with the same spec would keep the old size otherwise
      }
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
        // Inside its circle whatever the label ("12.5×") and the font size
        drawLabel(c, label, cx, cy, paint, 1f, Screen.dp(isSelected ? SELECTED : CHIP) - Screen.dp(8f));
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
        if (parent.setZoom(stops[index])) {
          performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); // no tick for a tap the camera can't take
        }
      }
      return true;
    }
  }

  // The modes: Night · Photo · Portrait · HDR

  private static final class ModeRow extends View implements Items {
    private static final float HEIGHT = 32f, PADDING_H = 14f, GAP = 2f;

    private final XgramCameraControls parent;
    private final RowA11y a11y;
    private final TextPaint paint = labelPaint(false, 14f, 18f), selectedTextPaint = labelPaint(true, 14f, 18f);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] modes = {ExtensionMode.NONE};
    private String[] labels = {""};
    private float[] widths = {0f};
    private int selected = ExtensionMode.NONE;
    private boolean vertical;
    /** The room along a horizontal row (0: any), and the share of its full size the row takes to fit in it. */
    private int maxLength;
    private float fit = 1f;

    ModeRow (Context context, XgramCameraControls parent) {
      super(context);
      this.parent = parent;
      paint.setColor(0xffffffff);
      paint.setShadowLayer(Screen.dp(2f), 0, Screen.dp(.5f), 0x99000000);
      selectedPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.SECONDARY_CONTAINER));
      selectedTextPaint.setColor(XgramM3MediaChrome.onMedia(XgramExpressive.ON_SECONDARY_CONTAINER));
      setVisibility(GONE);
      a11y = new RowA11y(this, this);
      ViewCompat.setAccessibilityDelegate(this, a11y);
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
      a11y.invalidateRoot();
    }

    /** Set while the controls lay out (they measure it right after). */
    void setVertical (boolean vertical) {
      if (this.vertical != vertical) {
        this.vertical = vertical;
        forceLayout(); // measure() with the same spec would keep the old size otherwise
      }
    }

    /** Set while the controls lay out (they measure it right after); 0 or less: any length. */
    void setMaxLength (int maxLength) {
      if (this.maxLength != maxLength) {
        this.maxLength = maxLength;
        forceLayout();
      }
    }

    @Override
    public void setEnabled (boolean enabled) {
      if (enabled != isEnabled()) {
        super.setEnabled(enabled);
        if (a11y != null) {
          a11y.invalidateRoot();
        }
      }
    }

    @Override
    protected boolean dispatchHoverEvent (MotionEvent e) {
      return a11y.dispatchHoverEvent(e) || super.dispatchHoverEvent(e);
    }

    @Override
    public int itemCount () {
      return modes.length > 1 ? modes.length : 0;
    }

    @Override
    public void itemBounds (int index, Rect out) {
      cellOf(index, rect);
      rect.roundOut(out);
    }

    @Override
    public CharSequence itemLabel (int index) {
      return labels[index];
    }

    @Override
    public boolean isItemSelected (int index) {
      return modes[index] == selected;
    }

    @Override
    public boolean clickItem (int index) {
      return modes[index] != selected && parent.setMode(modes[index]);
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
        fit = 1f;
        setMeasuredDimension((int) Math.ceil(maxWidth()), (int) (modes.length * Screen.dp(HEIGHT) + (modes.length - 1) * Screen.dp(GAP)));
      } else {
        float total = (modes.length - 1) * Screen.dp(GAP);
        for (float w : widths) {
          total += w;
        }
        // Wider than the room (large fonts, a narrow cover screen): names, padding and gaps shrink together
        fit = maxLength > 0 && total > maxLength ? maxLength / total : 1f;
        setMeasuredDimension((int) Math.ceil(total * fit), Screen.dp(HEIGHT));
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
          left += (widths[j] + Screen.dp(GAP)) * fit;
        }
        out.set(left, 0, left + widths[i] * fit, Screen.dp(HEIGHT));
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
        // Level whatever the phone's turn (see setContentRotation), inside its pill
        drawLabel(c, labels[i], cx, cy, isSelected ? selectedTextPaint : paint, fit, rect.width() - Screen.dp(8f) * fit);
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
            if (modes[i] != selected && parent.setMode(modes[i])) {
              performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); // no tick for a tap the camera can't take
            }
            break;
          }
        }
      }
      return true;
    }
  }

  // Focus: a ring where the finger was, the exposure slider beside it. The slider goes after a while; what still holds
  // stays as a mark until the next tap, a mode, another lens or a new binding: a lock as a small ring with its pill (it
  // held on with nothing on screen), a brightness other than 0 as its value where the slider was.

  private static final class FocusView extends View {
    private static final float RING = 36f, REST_RING = 12f, TRACK = 128f, TRACK_GAP = 58f, KNOB = 11f;
    private static final long SHOW_MS = 3000L, LOCKED_MS = 6000L, MOTION_MS = 200L;

    private final XgramCameraControls parent;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG), trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG), knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG), pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillTextPaint = labelPaint(true, 12f, 16f), badgeTextPaint = labelPaint(true, 12f, 16f);
    private final int badgeColor = XgramExpressive.withAlpha(XgramM3MediaChrome.onMedia(XgramExpressive.SURFACE_CONTAINER), .7f), badgeTextColor = XgramM3MediaChrome.onMedia(XgramExpressive.ON_SURFACE);
    private final RectF rect = new RectF();
    private final Runnable restRunnable = this::rest;
    private float x, y, appear;
    /** Shown; resting: the slider gone, only a mark of what still holds (a lock, a brightness other than 0). */
    private boolean visible, resting, locked, dragging;
    private @Nullable Range<Integer> exposureRange;
    private int exposureIndex;
    /** EV per exposure index (a third, a half or a sixth on most cameras). */
    private float exposureStep;
    private long shownAt, restAt;

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
        this.exposureStep = exposure.getExposureCompensationStep().floatValue();
      } else {
        this.exposureRange = null;
        this.exposureIndex = 0;
      }
      this.visible = true;
      this.resting = false;
      this.shownAt = android.os.SystemClock.uptimeMillis();
      this.appear = 0f;
      removeCallbacks(restRunnable);
      postDelayed(restRunnable, locked ? LOCKED_MS : SHOW_MS);
      postInvalidateOnAnimation();
    }

    /** The slider's time is up: what still holds stays as a mark, anything else goes. */
    private void rest () {
      if (dragging) {
        postDelayed(restRunnable, SHOW_MS);
        return;
      }
      if (!visible || resting) {
        return;
      }
      if (locked || hasExposureBadge()) {
        resting = true;
        restAt = android.os.SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
      } else {
        hide(true);
      }
    }

    /** Everything goes: a mode, a new binding, the screen hiding (the camera keeps no lock or brightness through them). */
    void hide (boolean animated) {
      removeCallbacks(restRunnable);
      dragging = false;
      locked = false;
      resting = false;
      if (visible) {
        visible = false;
        invalidate();
      }
    }

    boolean isLocked () {
      return visible && locked;
    }

    /** Something is on screen (the ring and slider, or a resting mark). */
    boolean isPlaced () {
      return visible;
    }

    /** The lock was let go (another lens): its marks go, a brightness other than 0 keeps its own. */
    void unlock () {
      if (!locked) {
        return;
      }
      locked = false;
      if (resting && !hasExposureBadge()) {
        hide(true);
      } else {
        invalidate();
      }
    }

    private boolean hasExposureBadge () {
      return exposureIndex != 0 && exposureStep > 0f;
    }

    /** "+0.7", "−1.3": the brightness in EV. */
    private String exposureLabel () {
      return String.format(Locale.US, "%+.1f", exposureIndex * exposureStep).replace('-', '−');
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
      final long now = android.os.SystemClock.uptimeMillis();
      appear = Math.min(1f, (now - shownAt) / (float) MOTION_MS);
      float eased = 1f - (1f - appear) * (1f - appear);
      // Resting: the ring shrinks to a mark (a lock) or fades (none), the slider fades, the brightness badge comes
      final float rest = resting ? Math.min(1f, (now - restAt) / (float) MOTION_MS) : 0f;
      final float restEased = 1f - (1f - rest) * (1f - rest);
      int ringColor = locked ? XgramM3MediaChrome.onMedia(XgramExpressive.TERTIARY) : 0xffffffff;
      float ringAlpha = Math.min(1f, eased * 1.4f) * (locked ? 1f : 1f - restEased);
      if (ringAlpha > 0f) {
        ringPaint.setColor(ringColor);
        ringPaint.setAlpha((int) (255 * ringAlpha));
        float radius = Screen.dp(RING) * (1.35f - .35f * eased);
        radius += (Screen.dp(REST_RING) - radius) * restEased;
        c.drawCircle(x, y, radius, ringPaint);
        c.drawCircle(x, y, Screen.dp(2f), ringPaint);
      }
      final float sliderAlpha = 1f - restEased;
      if (exposureRange != null && sliderAlpha > 0f) {
        float tx = trackX();
        float top = y - Screen.dp(TRACK) / 2f, bottom = y + Screen.dp(TRACK) / 2f;
        float ky = knobY();
        trackPaint.setColor(XgramExpressive.withAlpha(0xccffffff, sliderAlpha));
        // The track breaks around the knob
        if (ky - Screen.dp(KNOB) - Screen.dp(3f) > top) {
          c.drawLine(tx, top, tx, ky - Screen.dp(KNOB) - Screen.dp(3f), trackPaint);
        }
        if (ky + Screen.dp(KNOB) + Screen.dp(3f) < bottom) {
          c.drawLine(tx, ky + Screen.dp(KNOB) + Screen.dp(3f), tx, bottom, trackPaint);
        }
        // The knob: a sun
        drawSun(c, tx, ky, Screen.dp(5f), Screen.dp(7.5f), Screen.dp(KNOB), XgramExpressive.withAlpha(ringColor, sliderAlpha));
      }
      if (resting && hasExposureBadge()) {
        drawExposureBadge(c, restEased);
      }
      if (locked) {
        String text = Lang.getString(R.string.XgramCameraLocked);
        float w = pillTextPaint.measureText(text) + Screen.dp(24f), h = Screen.dp(28f);
        // Above the ring, at rest too
        float ring = Screen.dp(RING) + (Screen.dp(REST_RING) - Screen.dp(RING)) * restEased;
        float top = Math.max(Screen.dp(8f), y - ring - Screen.dp(16f) - h);
        float left = Math.max(Screen.dp(8f), Math.min(getWidth() - Screen.dp(8f) - w, x - w / 2f));
        rect.set(left, top, left + w, top + h);
        c.drawRoundRect(rect, h / 2f, h / 2f, pillPaint);
        c.drawText(text, rect.centerX(), rect.centerY() + textCenterOffset(pillTextPaint), pillTextPaint);
      }
      if (appear < 1f || (resting && rest < 1f)) {
        postInvalidateOnAnimation();
      }
    }

    private void drawSun (Canvas c, float cx, float cy, float core, float rayFrom, float rayTo, int color) {
      knobPaint.setStyle(Paint.Style.FILL);
      knobPaint.setColor(color);
      c.drawCircle(cx, cy, core, knobPaint);
      knobPaint.setStyle(Paint.Style.STROKE);
      knobPaint.setStrokeWidth(Screen.dp(1.5f));
      knobPaint.setStrokeCap(Paint.Cap.ROUND);
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI / 4 * i;
        float sx = (float) Math.cos(angle), sy = (float) Math.sin(angle);
        c.drawLine(cx + sx * rayFrom, cy + sy * rayFrom, cx + sx * rayTo, cy + sy * rayTo, knobPaint);
      }
    }

    /** At rest, the brightness the slider left: a small sun and its value in a pill where the slider was. */
    private void drawExposureBadge (Canvas c, float alpha) {
      final String text = exposureLabel();
      final float h = Screen.dp(24f), sun = Screen.dp(6.5f), textWidth = badgeTextPaint.measureText(text);
      final float w = Screen.dp(8f) + sun * 2f + Screen.dp(5f) + textWidth + Screen.dp(10f);
      final float left = Math.max(Screen.dp(8f), Math.min(getWidth() - Screen.dp(8f) - w, trackX() - w / 2f));
      final float top = Math.max(Screen.dp(8f), Math.min(getHeight() - Screen.dp(8f) - h, y - h / 2f));
      rect.set(left, top, left + w, top + h);
      badgePaint.setColor(XgramExpressive.withAlpha(badgeColor, alpha));
      c.drawRoundRect(rect, h / 2f, h / 2f, badgePaint);
      final int contentColor = XgramExpressive.withAlpha(badgeTextColor, alpha);
      drawSun(c, left + Screen.dp(8f) + sun, rect.centerY(), Screen.dp(2.5f), Screen.dp(4.5f), sun, contentColor);
      badgeTextPaint.setColor(contentColor);
      c.drawText(text, left + Screen.dp(8f) + sun * 2f + Screen.dp(5f) + textWidth / 2f, rect.centerY() + textCenterOffset(badgeTextPaint), badgeTextPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (!visible || resting || exposureRange == null) {
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
          removeCallbacks(restRunnable);
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
            postDelayed(restRunnable, locked ? LOCKED_MS : SHOW_MS);
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
