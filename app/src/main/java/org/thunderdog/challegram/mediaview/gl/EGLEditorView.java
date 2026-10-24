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
 * File created on 11/12/2016
 */
package org.thunderdog.challegram.mediaview.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fahadali.xgram.editor.XgramLayer;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.data.FiltersState;
import org.thunderdog.challegram.mediaview.paint.PaintAction;
import org.thunderdog.challegram.mediaview.paint.PaintMode;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.mediaview.paint.SimpleDrawing;
import org.thunderdog.challegram.mediaview.paint.widget.SimpleDrawingView;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class EGLEditorView extends ViewGroup {
  private SimpleDrawingView paintView;
  private @Nullable View textureView;
  private @Nullable EGLEditorContext editorContext;
  private @Nullable Bitmap sourceBitmap;
  private FiltersState currentFiltersState;
  private PaintState currentPaintState;
  private ImageGalleryFile currentFile;

  private int croppedWidth, croppedHeight;
  private int centerX, centerY;
  private int normalWidth, normalHeight;

  private class OverlayView extends View {
    public OverlayView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      int width = croppedWidth;
      int height = croppedHeight;
      int left = centerX - width / 2;
      int right = left + width;
      int top = centerY -  height / 2;
      int bottom = top + height;

      int viewWidth = getMeasuredWidth();
      int viewHeight = getMeasuredHeight();

      Paint paint = Paints.fillingPaint(0xff000000);

      if (left > 0) {
        c.drawRect(0, top, left, bottom, paint);
      }
      if (right < viewWidth) {
        c.drawRect(right, top, viewWidth, bottom, paint);
      }
      if (top > 0) {
        c.drawRect(0, 0, viewWidth, top, paint);
      }
      if (bottom < viewHeight) {
        c.drawRect(0, bottom, viewWidth, viewHeight, paint);
      }
    }
  }

  private FrameLayoutFix textureWrap;
  private OverlayView overlayView;

  private ContentLayout contentWrap;

  public EGLEditorView (Context context) {
    super(context);
    textureWrap = new FrameLayoutFix(context);
    textureWrap.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(textureWrap);

    contentWrap = new ContentLayout(context);
    contentWrap.setParent(this);
    contentWrap.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    contentWrap.setVisibility(View.INVISIBLE);
    textureWrap.addView(contentWrap);

    overlayView = new OverlayView(context);
    overlayView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(overlayView);
  }

  private static final int TYPE_NONE = 0;
  private static final int TYPE_FILTERS = 1;
  private static final int TYPE_PAINT = 2;

  private int initedType;

  public void init (ImageGalleryFile imageFile, Bitmap bitmap, @Nullable FiltersState filtersState, @Nullable PaintState paintState) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      this.currentFile = imageFile;
      this.sourceBitmap = bitmap;

      final int initType;

      if (filtersState != null) {
        this.currentFiltersState = filtersState;
        initType = TYPE_FILTERS;
      } else if (paintState != null) {
        this.currentPaintState = paintState;
        initType = TYPE_PAINT;
      } else {
        throw new IllegalArgumentException("filtersState == null && paintState == null");
      }


      android.view.TextureView textureView = null;

      switch (initType) {
        case TYPE_FILTERS: {
          textureView = new android.view.TextureView(getContext());
          textureView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
          textureView.setVisibility(textureVisible ? View.VISIBLE : View.INVISIBLE);
          textureView.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
              if (surface != null && editorContext == null && sourceBitmap != null && !sourceBitmap.isRecycled() && currentFiltersState != null) {
                editorContext = new EGLEditorContext(surface, sourceBitmap, currentFiltersState, width, height);
                editorContext.requestRender(true, true);
              }
            }

            @Override
            public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {
              if (editorContext != null) {
                editorContext.setSurfaceTextureSize(width, height);
                editorContext.requestRender(false, true);
                UI.post(() -> editorContext.requestRender(false, true));
              }
            }

            @Override
            public boolean onSurfaceTextureDestroyed (SurfaceTexture surface) {
              destroy();
              return true;
            }

            @Override
            public void onSurfaceTextureUpdated (SurfaceTexture surface) {
              xgramScheduleFilteredBase(); // XGram
            }
          });
          textureView.setScaleX(-1);
          contentWrap.addView(textureView);
          break;
        }
        case TYPE_PAINT: {
          contentWrap.setPaintingGesturesEnabled(true);
          break;
        }
      }
      paintView = new SimpleDrawingView(getContext());
      paintView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      contentWrap.addView(paintView);
      contentWrap.setPaintingState(paintState);
      this.textureView = textureView;
      applyCurrentStyles();
      initedType = initType;
      xgramSetFiltersBase();
    }
  }

  private int appliedRotation;
  private CropState sourceCropState;

  private void applyCurrentStyles () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      if (appliedRotation != sourceRotation) {
        boolean wasRotated = U.isRotated(appliedRotation);
        appliedRotation = sourceRotation;
        boolean nowRotated = U.isRotated(sourceRotation);
        if (wasRotated != nowRotated) {
          contentWrap.requestLayout();
        }
      }
      if (sourceCropState != null && !sourceCropState.isEmpty()) {
        float degrees = sourceCropState.getDegreesAroundCenter();
        contentWrap.setRotation(degrees);

        double rad = Math.toRadians(degrees);
        float sin = (float) Math.abs(Math.sin(rad));
        float cos = (float) Math.abs(Math.cos(rad));

        float w = sourceWidth;
        float h = sourceHeight;

        // W = w·|cos φ| + h·|sin φ|
        // H = w·|sin φ| + h·|cos φ|

        float W = w * cos + h * sin;
        float H = w * sin + h * cos;

        float scale = Math.max(W / w, H / h);
        contentWrap.setScaleX(scale);
        contentWrap.setScaleY(scale);
        if (textureView != null) {
          textureView.setScaleX(sourceCropState.needMirrorHorizontally() ? -1 : 1);
          textureView.setScaleY(sourceCropState.needMirrorVertically() ? -1 : 1);
        }
      } else {
        contentWrap.setRotation(0);
        contentWrap.setScaleX(1f);
        contentWrap.setScaleY(1f);
        contentWrap.setTranslationX(0f);
        contentWrap.setTranslationY(0f);
        if (textureView != null) {
          textureView.setScaleX(1);
          textureView.setScaleY(1);
        }
      }
      textureWrap.setRotation(sourceRotation);
    }
  }

  public void setViewSizes (int width, int height, int croppedWidth, int croppedHeight) {
    this.normalWidth = width;
    this.normalHeight = height;
    this.croppedWidth = croppedWidth;
    this.croppedHeight = croppedHeight;
    overlayView.invalidate();
  }

  public void setCenter (int centerX, int centerY) {
    this.centerX = centerX;
    this.centerY = centerY;
    overlayView.invalidate();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

    if (getChildCount() == 0) {
      return;
    }

    int textureWidth, textureHeight;
    textureWidth = normalWidth;
    textureHeight = normalHeight;

    if (U.isRotated(sourceRotation)) {
      textureWrap.measure(MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY));
    } else {
      textureWrap.measure(MeasureSpec.makeMeasureSpec(textureWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textureHeight, MeasureSpec.EXACTLY));
    }

    float scale = 1f;
    float translateX = 0;
    float translateY = 0;
    if (sourceCropState != null) {
      double left = sourceCropState.getLeft();
      double top = sourceCropState.getTop();
      double right = sourceCropState.getRight();
      double bottom = sourceCropState.getBottom();

      double width = (right - left);
      double height = (bottom - top);

      scale = Math.max((float) croppedWidth / (float) (textureWidth * width), (float) croppedHeight / (float) (textureHeight * height));

      double cx = ((left + right) / 2 - 0.5);
      double cy = ((top + bottom) / 2 - 0.5);

      textureWidth *= scale;
      textureHeight *= scale;

      float textureCx = (float) (cx * (float) textureWidth);
      float textureCy = (float) (cy * (float) textureHeight);

      translateX = -textureCx;
      translateY = -textureCy;
    }
    textureWrap.setScaleX(scale);
    textureWrap.setScaleY(scale);
    textureWrap.setTranslationX(translateX);
    textureWrap.setTranslationY(translateY);

    overlayView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int childCount = getChildCount();

    /*final int parentWidth = getMeasuredWidth();
    final int parentHeight = getMeasuredHeight();*/

    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view == null) {
        continue;
      }

      if (view == overlayView) {
        view.layout(l, t, r, b);
      } else {
        int viewWidth = view.getMeasuredWidth();
        int viewHeight = view.getMeasuredHeight();

        /*if (Utils.isRotated(sourceRotation)) {
          int temp = viewWidth;
          viewWidth = viewHeight;
          viewHeight = temp;
        }*/

        int left, top, right, bottom;

        left = centerX - viewWidth / 2;
        top = centerY - viewHeight / 2;

        right = left + viewWidth;
        bottom = top + viewHeight;

        view.layout(left, top, right, bottom);
      }
    }
  }

  public void requestRenderByChange (boolean isBlur) {
    if (editorContext != null) {
      editorContext.requestRender(isBlur);
    }
  }

  public void destroy () {
    if (editorContext != null) {
      editorContext.destroy();
      editorContext = null;
    }
  }

  public void pause () {
    if (editorContext != null) {
      editorContext.pause();
    }
  }

  private boolean textureVisible;

  public void setEditorVisible (boolean isVisible) {
    this.textureVisible = isVisible;
    if (textureView != null) {
      textureView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    }
    contentWrap.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
    overlayView.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
  }

  public boolean isEditorVisible () {
    return textureVisible;
  }

  private int sourceWidth, sourceHeight, sourceRotation;

  public void reset (ImageGalleryFile imageFile, int sourceWidth, int sourceHeight, @Nullable Bitmap bitmap, @Nullable FiltersState filtersState, @Nullable PaintState paintState) {
    this.sourceWidth = sourceWidth;
    this.sourceHeight = sourceHeight;
    this.sourceRotation = imageFile.getVisualRotationWithCropRotation();
    this.sourceCropState = imageFile.getCropState();
    if (initedType == TYPE_NONE) {
      init(imageFile, bitmap, filtersState, paintState);
      return;
    }
    this.sourceBitmap = bitmap;
    this.currentFiltersState = filtersState;
    this.currentPaintState = paintState;
    this.currentFile = imageFile;
    applyCurrentStyles();
    switch (initedType) {
      case TYPE_FILTERS:
        if (editorContext != null) {
          editorContext.resumeWithData(bitmap, filtersState);
        }
        break;
    }
    contentWrap.setPaintingState(paintState);
    xgramSetFiltersBase();
  }

  // XGram: the photo under the drawing, for the blur and pixelate brushes. Adjust (filters) shows the source under its
  // drawing, mirrored as its texture is; the paint section passes what its image receiver shows (setPaintBase).

  private void xgramSetFiltersBase () {
    removeCallbacks(xgramFilteredBase);
    if (initedType == TYPE_FILTERS && paintView != null) {
      paintView.setBaseBitmap(sourceBitmap, sourceCropState != null && sourceCropState.needMirrorHorizontally(), sourceCropState != null && sourceCropState.needMirrorVertically());
    }
  }

  // XGram: once the filtered frames settle, Adjust's blur and pixelate strokes paint with a small copy of the texture
  // (the filtered photo only: the drawing is another view), as the paint section and the export use the filtered
  // photo; the source until then. A new bitmap each time: XgramPaintBase caches its copies by bitmap.

  private static final float FILTERED_BASE_SIDE = 240f;

  private final Runnable xgramFilteredBase = this::xgramTakeFilteredBase;

  private void xgramScheduleFilteredBase () {
    removeCallbacks(xgramFilteredBase);
    postDelayed(xgramFilteredBase, 150L);
  }

  private void xgramTakeFilteredBase () {
    if (initedType != TYPE_FILTERS || paintView == null || !(textureView instanceof android.view.TextureView) || contentWrap.state == null || !contentWrap.state.hasBaseDrawings()) {
      return;
    }
    android.view.TextureView view = (android.view.TextureView) textureView;
    final int width = view.getWidth(), height = view.getHeight();
    if (!view.isAvailable() || width <= 0 || height <= 0) {
      return;
    }
    final float scale = Math.min(1f, FILTERED_BASE_SIDE / (float) Math.max(width, height));
    Bitmap bitmap;
    try {
      bitmap = view.getBitmap(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
    } catch (Throwable t) {
      bitmap = null;
    }
    if (bitmap != null) {
      paintView.setBaseBitmap(bitmap, sourceCropState != null && sourceCropState.needMirrorHorizontally(), sourceCropState != null && sourceCropState.needMirrorVertically());
    }
  }

  public void setPaintBase (@Nullable Bitmap bitmap, boolean mirrorHorizontally, boolean mirrorVertically) {
    if (paintView != null) {
      paintView.setBaseBitmap(bitmap, mirrorHorizontally, mirrorVertically);
    }
  }

  public void setSizes (int width, int height, int rotation, CropState cropState) {
    this.sourceWidth = width;
    this.sourceCropState = cropState;
    this.sourceHeight = height;
    this.sourceRotation = rotation;
  }

  private boolean isReady () {
    switch (initedType) {
      case TYPE_FILTERS:
        return editorContext != null;
      case TYPE_PAINT:
        return false; // TODO
    }
    return false;
  }

  public void getBitmapAsync (BitmapCallback callback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && isReady()) {
      switch (initedType) {
        case TYPE_FILTERS:
          if (editorContext != null) {
            editorContext.getBitmap(callback);
          } else {
            callback.onBitmapObtained(null);
          }
          break;
      }
    } else {
      callback.onBitmapObtained(null);
    }
  }

  private int paintingMode;

  public void setPaintingMode (int mode) {
    if (this.paintingMode != mode) {
      this.paintingMode = mode;
      contentWrap.setPaintingGesturesEnabled(mode != PaintMode.FREE_MOVEMENT);
      contentWrap.setPaintingMode(mode);
    }
  }

  // ContentLayout

  public ContentLayout getContentWrap () {
    return contentWrap;
  }

  public static class ContentLayout extends FrameLayoutFix implements SimpleDrawing.ChangeListener, Runnable {
    private EGLEditorView parent;

    public ContentLayout (@NonNull Context context) {
      super(context);
    }

    public void setParent (EGLEditorView parent) {
      this.parent = parent;
    }

    private boolean paintingEnabled;

    public void setPaintingGesturesEnabled (boolean isEnabled) {
      this.paintingEnabled = isEnabled;
    }

    private PaintState state;
    private int paintingMode;

    public void setPaintingMode (int mode) {
      this.paintingMode = mode;
    }

    public void setPaintingState (PaintState state) {
      if (heldLayer != null && this.state != state) {
        xgramEndLayerGesture(null, true); // XGram: the layer goes back to where it was in the drawing it was in
      }
      this.state = state;
      parent.paintView.setPaintState(state);
    }

    private int effectiveMode = PaintMode.NONE;
    private boolean needZoomCancel;

    public void cancelDrawingByZoom () {
      needZoomCancel = true;
    }

    public interface DrawingListener {
      void onDrawingStateChanged (View editorView, int paintMode);
    }

    private DrawingListener listener;

    public void setDrawingListener (DrawingListener listener) {
      this.listener = listener;
    }

    public boolean hasEffectiveDrawing () {
      return effectiveMode != PaintMode.NONE;
    }

    private void setEffectiveMode (int mode) {
      if (this.effectiveMode != mode) {
        this.effectiveMode = mode;
        if (this.listener != null) {
          listener.onDrawingStateChanged(this, mode);
        }
      }
    }

    @SuppressWarnings("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (MotionEvent e) {
      int action = e.getActionMasked(); // XGram: masked, so a second finger's events are not read as the first's

      if (state == null) {
        return false;
      }

      if (action == MotionEvent.ACTION_DOWN) {
        needZoomCancel = false;
        if (xgramBeginLayerGesture(e)) { // XGram: a finger on a layer moves it instead of drawing
          return true;
        }
      }

      if (heldLayer != null) { // XGram
        xgramOnLayerTouch(e, action);
        return true;
      }

      if (action != MotionEvent.ACTION_DOWN && effectiveMode == PaintMode.NONE) {
        return false;
      }

      switch (action) {
        case MotionEvent.ACTION_DOWN: {
          if (!paintingEnabled || paintingMode == PaintMode.FREE_MOVEMENT || e.getPointerCount() > 1 || !beginDrawing(paintingMode, e, getMeasuredWidth(), getMeasuredHeight())) {
            setEffectiveMode(PaintMode.NONE);
            return false;
          }
          setEffectiveMode(paintingMode);
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (!moveDrawing(effectiveMode, e, ((MediaCellView) parent.getParent()).getZoomFactor() == 1f)) {
            setEffectiveMode(PaintMode.NONE);
          }
          break;
        }
        case MotionEvent.ACTION_UP: {
          completeDrawing(effectiveMode, e, false);
          setEffectiveMode(PaintMode.NONE);
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          completeDrawing(effectiveMode, e, needZoomCancel);
          setEffectiveMode(PaintMode.NONE);
          needZoomCancel = false;
          break;
        }
      }

      return true;
    }

    private SimpleDrawing currentSimpleDrawing;
    // XGram: the eraser's current gesture
    private PaintAction.EraseData eraseData;

    private int brushColor;
    private float brushRadius;

    /** XGram: how much the drawing is scaled on screen: the crop's straightening and region, and the zoom. */
    private float getScreenScale () {
      float scale = parent.textureWrap.getScaleX() * getScaleX();
      if (parent.getParent() instanceof MediaCellView) {
        scale *= ((MediaCellView) parent.getParent()).getZoomFactor();
      }
      return scale > 0f ? Math.abs(scale) : 1f;
    }

    /** XGram: removes every drawing the eraser touches at (x, y); its size is brushRadius, in dp on screen. */
    private void eraseAt (float x, float y, float tolerance) {
      SimpleDrawing drawing;
      while ((drawing = state.findSimpleDrawing(x, y, getMeasuredWidth(), getMeasuredHeight(), tolerance)) != null) {
        if (!state.eraseSimpleDrawing(drawing, eraseData)) {
          break;
        }
      }
    }

    private float getEraserTolerance () {
      return Screen.dpf(brushRadius) / 2f / getScreenScale();
    }

    public void setBrushParameters (int brushColor, float brushRadius) {
      if (this.brushColor != brushColor || this.brushRadius != brushRadius) {
        this.brushColor = brushColor;
        this.brushRadius = brushRadius;
        if (currentSimpleDrawing != null) {
          currentSimpleDrawing.setBrushParameters(brushColor, brushRadius);
        }
      }
    }

    private boolean beginDrawing (int mode, MotionEvent e, int canvasWidth, int canvasHeight) {
      int type;
      switch (mode) {
        case PaintMode.ARROW:
          type = SimpleDrawing.TYPE_ARROW;
          break;
        case PaintMode.RECTANGLE:
          type = SimpleDrawing.TYPE_RECTANGLE;
          break;
        case PaintMode.PATH:
          type = SimpleDrawing.TYPE_PATH;
          break;
        // XGram
        case PaintMode.MARKER:
          type = SimpleDrawing.TYPE_MARKER;
          break;
        case PaintMode.NEON:
          type = SimpleDrawing.TYPE_NEON;
          break;
        case PaintMode.BLUR:
          type = SimpleDrawing.TYPE_BLUR;
          break;
        case PaintMode.PIXELATE:
          type = SimpleDrawing.TYPE_PIXELATE;
          break;
        case PaintMode.ELLIPSE:
          type = SimpleDrawing.TYPE_ELLIPSE;
          break;
        case PaintMode.RECTANGLE_FILLED:
          type = SimpleDrawing.TYPE_RECTANGLE_FILLED;
          break;
        case PaintMode.ELLIPSE_FILLED:
          type = SimpleDrawing.TYPE_ELLIPSE_FILLED;
          break;
        case PaintMode.ERASER: {
          eraseData = new PaintAction.EraseData();
          float tolerance = getEraserTolerance();
          parent.paintView.setScreenScale(getScreenScale());
          eraseAt(e.getX(), e.getY(), tolerance);
          parent.paintView.setEraser(e.getX(), e.getY(), tolerance);
          return true;
        }
        default:
          return false;
      }
      // XGram: the brush keeps its size on screen whatever the zoom and crop (Telegram X: 1, the size before them)
      SimpleDrawing drawing = new SimpleDrawing(type, canvasWidth, canvasHeight, 1f / getScreenScale(), parent.sourceCropState != null ? parent.sourceCropState.getDegreesAroundCenter() : 0f);
      drawing.startDrawing(e);
      drawing.setBrushParameters(brushColor, brushRadius);
      state.addSimpleDrawing(drawing);
      drawing.addChangeListener(this);
      currentSimpleDrawing = drawing;
      return true;
    }

    private boolean moveDrawing (int mode, MotionEvent e, boolean allowHistory) {
      if (mode == PaintMode.ERASER) { // XGram: along the finger's path since the last event
        if (eraseData == null) {
          return false;
        }
        float tolerance = getEraserTolerance();
        for (int i = 0; i < e.getHistorySize(); i++) {
          eraseAt(e.getHistoricalX(i), e.getHistoricalY(i), tolerance);
        }
        eraseAt(e.getX(), e.getY(), tolerance);
        parent.paintView.setEraser(e.getX(), e.getY(), tolerance);
        return true;
      }
      if (currentSimpleDrawing != null) { // XGram: every drawing tool (Telegram X listed path, arrow and rectangle)
        currentSimpleDrawing.moveDrawing(e, allowHistory);
        return true;
      }
      throw new UnsupportedOperationException("mode == " + mode);
    }

    private void completeDrawing (int mode, MotionEvent e, boolean byPinch) {
      if (mode == PaintMode.ERASER) { // XGram: one action for the gesture, none when a pinch took it over
        if (eraseData != null) {
          if (byPinch) {
            state.restoreErased(eraseData);
          } else {
            state.trackEraseAction(eraseData);
          }
          eraseData = null;
        }
        parent.paintView.setEraser(0f, 0f, 0f);
        return;
      }
      if (currentSimpleDrawing != null) {
        if (currentSimpleDrawing.completeDrawing(e, byPinch)) {
          state.trackSimpleDrawingAction(currentSimpleDrawing);
        } else {
          state.removeSimpleDrawing(currentSimpleDrawing);
        }
        currentSimpleDrawing.removeChangeListener(this);
        currentSimpleDrawing = null;
      }
    }

    public boolean isBusy () {
      return effectiveMode != PaintMode.NONE || heldLayer != null; // XGram: or a layer is held
    }

    // XGram: the movable layers (text, emoji, stickers). A finger on one moves it, two scale and turn it about their
    // middle, a tap opens it (a text to edit), and dragged onto the trash it goes. Its touches are this view's alone
    // while it is held: the cell's zoom and the pager keep out. One action per gesture, so undo puts it back.

    public interface LayerListener {
      /** A layer started ({@code layer}) or stopped (null) being dragged: the trash shows while one is. */
      void onLayerDragChanged (ContentLayout v, @Nullable XgramLayer layer);
      /** Whether (rawX, rawY) on screen is over the trash. */
      boolean isOverLayerTrash (ContentLayout v, float rawX, float rawY);
      /** The dragged layer came over the trash or left it (a second finger takes it off): the trash shows it. */
      void onLayerOverTrashChanged (ContentLayout v, boolean isOver);
      /** A tap on a layer. */
      void onLayerClick (ContentLayout v, XgramLayer layer);
    }

    private @Nullable LayerListener layerListener;

    public void setLayerListener (@Nullable LayerListener listener) {
      this.layerListener = listener;
    }

    /** A layer at least this big on screen (a pinch can't lose it), at most this many times the photo's width. */
    private static final float MIN_LAYER_SCREEN_DP = 16f, MAX_LAYER_SIZE = 4f;
    /** A turn this close to upright on screen (or a quarter from it) settles there. */
    private static final float LAYER_SNAP_DEGREES = 4f;

    private @Nullable XgramLayer heldLayer, heldLayerBefore;
    private boolean heldLayerMoved, heldLayerDragging, heldLayerOverTrash;
    private long heldLayerDownTime;
    private float heldLayerDownX, heldLayerDownY;
    // The pointers, and the layer, when the pointers last changed: the gesture goes on from there
    private int gesturePointer0 = -1, gesturePointer1 = -1;
    private float gestureX0, gestureY0, gestureX1, gestureY1;
    private float gestureCx, gestureCy, gestureSize, gestureRotation;

    public boolean isHoldingLayer () {
      return heldLayer != null;
    }

    private boolean xgramBeginLayerGesture (MotionEvent e) {
      if (heldLayer != null) {
        xgramEndLayerGesture(null, true); // a gesture that never ended
      }
      if (!paintingEnabled || paintingMode == PaintMode.FREE_MOVEMENT || state == null || !state.hasLayers() || e.getPointerCount() > 1) {
        return false;
      }
      final float scale = getScreenScale();
      final XgramLayer layer = state.findLayer(e.getX(), e.getY(), getMeasuredWidth(), getMeasuredHeight(), Screen.dp(8f) / scale);
      if (layer == null) {
        return false;
      }
      heldLayer = layer;
      heldLayerBefore = layer.copy();
      heldLayerMoved = heldLayerDragging = heldLayerOverTrash = false;
      heldLayerDownTime = e.getEventTime();
      heldLayerDownX = e.getX();
      heldLayerDownY = e.getY();
      xgramRebaseLayerGesture(e, -1);
      ViewParent viewParent = getParent();
      if (viewParent != null) {
        viewParent.requestDisallowInterceptTouchEvent(true);
      }
      parent.paintView.setScreenScale(scale);
      parent.paintView.setHeldLayer(layer);
      return true;
    }

    /** The gesture goes on from the pointers down now, but the one at {@code upIndex} (lifting), and the layer's place. */
    private void xgramRebaseLayerGesture (MotionEvent e, int upIndex) {
      gesturePointer0 = gesturePointer1 = -1;
      for (int i = 0; i < e.getPointerCount(); i++) {
        if (i == upIndex) {
          continue;
        }
        if (gesturePointer0 == -1) {
          gesturePointer0 = e.getPointerId(i);
          gestureX0 = e.getX(i);
          gestureY0 = e.getY(i);
        } else {
          gesturePointer1 = e.getPointerId(i);
          gestureX1 = e.getX(i);
          gestureY1 = e.getY(i);
          break;
        }
      }
      if (heldLayer != null) {
        gestureCx = heldLayer.getCenterX();
        gestureCy = heldLayer.getCenterY();
        gestureSize = heldLayer.getSize();
        gestureRotation = heldLayer.getRotation();
      }
    }

    private void xgramOnLayerTouch (MotionEvent e, int action) {
      switch (action) {
        case MotionEvent.ACTION_POINTER_DOWN: {
          heldLayerMoved = true; // two fingers: not a tap
          xgramRebaseLayerGesture(e, -1);
          xgramUpdateLayerTrash(null); // a pinch drops nothing
          break;
        }
        case MotionEvent.ACTION_POINTER_UP: {
          xgramRebaseLayerGesture(e, e.getActionIndex());
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          xgramMoveLayer(e);
          break;
        }
        case MotionEvent.ACTION_UP: {
          xgramEndLayerGesture(e, false);
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          xgramEndLayerGesture(e, true);
          break;
        }
      }
    }

    private void xgramMoveLayer (MotionEvent e) {
      final XgramLayer layer = heldLayer;
      final int index0 = gesturePointer0 != -1 ? e.findPointerIndex(gesturePointer0) : -1;
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      if (layer == null || index0 == -1 || width <= 0 || height <= 0) {
        return;
      }
      final float scale = getScreenScale();
      if (!heldLayerMoved) {
        if (Math.hypot(e.getX(index0) - heldLayerDownX, e.getY(index0) - heldLayerDownY) <= Screen.getTouchSlop() / scale) {
          return;
        }
        heldLayerMoved = true;
      }
      final int index1 = gesturePointer1 != -1 ? e.findPointerIndex(gesturePointer1) : -1;
      float cx, cy, size = gestureSize, rotation = gestureRotation;
      if (index1 != -1) {
        // two fingers: their middle moves it, their distance scales it, their angle turns it
        final float x0 = e.getX(index0), y0 = e.getY(index0), x1 = e.getX(index1), y1 = e.getY(index1);
        cx = gestureCx + ((x0 + x1) - (gestureX0 + gestureX1)) / 2f / width;
        cy = gestureCy + ((y0 + y1) - (gestureY0 + gestureY1)) / 2f / height;
        final double startDistance = Math.hypot(gestureX1 - gestureX0, gestureY1 - gestureY0);
        if (startDistance > 0) {
          size = (float) (gestureSize * Math.hypot(x1 - x0, y1 - y0) / startDistance);
        }
        final double startAngle = Math.atan2(gestureY1 - gestureY0, gestureX1 - gestureX0);
        rotation = xgramSnapLayerRotation(gestureRotation + (float) Math.toDegrees(Math.atan2(y1 - y0, x1 - x0) - startAngle));
      } else {
        cx = gestureCx + (e.getX(index0) - gestureX0) / width;
        cy = gestureCy + (e.getY(index0) - gestureY0) / height;
      }
      final float minSize = Math.min(gestureSize, Screen.dp(MIN_LAYER_SCREEN_DP) / (width * scale));
      size = Math.max(minSize, Math.min(MAX_LAYER_SIZE, size));
      layer.setPosition(cx, cy, size, rotation);
      if (!heldLayerDragging) {
        heldLayerDragging = true;
        if (layerListener != null) {
          layerListener.onLayerDragChanged(this, layer);
        }
      }
      xgramUpdateLayerTrash(index1 == -1 && e.getPointerCount() == 1 ? e : null);
      parent.paintView.invalidate();
    }

    /** The turn, settled upright on screen (or a quarter turn from it) when it is close. */
    private float xgramSnapLayerRotation (float rotation) {
      final float base = parent.textureWrap.getRotation() + getRotation();
      final float onScreen = rotation + base;
      final float nearest = Math.round(onScreen / 90f) * 90f;
      return Math.abs(onScreen - nearest) <= LAYER_SNAP_DEGREES ? nearest - base : rotation;
    }

    private void xgramUpdateLayerTrash (@Nullable MotionEvent e) {
      final boolean over = e != null && heldLayerDragging && layerListener != null && layerListener.isOverLayerTrash(this, e.getRawX(), e.getRawY());
      if (heldLayerOverTrash != over) {
        heldLayerOverTrash = over;
        if (heldLayer != null) {
          heldLayer.setFade(over ? 1f : 0f);
        }
        if (over) {
          performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (layerListener != null) {
          layerListener.onLayerOverTrashChanged(this, over);
        }
      }
    }

    /** Puts a held layer back where it was and lets it go (the section is closing). */
    public void cancelLayerGesture () {
      if (heldLayer != null) {
        xgramEndLayerGesture(null, true);
      }
    }

    private void xgramEndLayerGesture (@Nullable MotionEvent e, boolean cancel) {
      final XgramLayer layer = heldLayer, before = heldLayerBefore;
      final boolean moved = heldLayerMoved, dragging = heldLayerDragging, overTrash = heldLayerOverTrash;
      heldLayer = heldLayerBefore = null;
      heldLayerMoved = heldLayerDragging = heldLayerOverTrash = false;
      gesturePointer0 = gesturePointer1 = -1;
      parent.paintView.setHeldLayer(null);
      if (layer == null || before == null) {
        return;
      }
      layer.setFade(0f);
      if (cancel || state == null) {
        layer.set(before);
      } else if (!moved) {
        if (e != null && e.getEventTime() - heldLayerDownTime <= ViewConfiguration.getLongPressTimeout() && layerListener != null) {
          layerListener.onLayerClick(this, layer);
        }
      } else if (overTrash) {
        layer.set(before); // undo brings it back where it was
        state.removeLayer(layer);
      } else {
        // its middle stays on the photo: it can't get lost off the edge
        layer.setPosition(MathUtils.clamp(layer.getCenterX()), MathUtils.clamp(layer.getCenterY()), layer.getSize(), layer.getRotation());
        state.trackLayerChange(layer, before);
      }
      if (dragging && layerListener != null) {
        layerListener.onLayerDragChanged(this, null);
      }
      parent.paintView.invalidate();
    }

    /**
     * Adds {@code layer} where the middle of the cropped photo is on screen (whatever the zoom), upright on screen and
     * {@code screenSize} pixels big there (a text's font size, a picture's long side), as an action.
     */
    public void addLayer (XgramLayer layer, float screenSize) {
      final int width = getMeasuredWidth(), height = getMeasuredHeight();
      if (state == null || width <= 0 || height <= 0) {
        return;
      }
      final float[] point = {parent.getLeft() + parent.centerX, parent.getTop() + parent.centerY};
      mapIntoChild(parent, point);
      mapIntoChild(parent.textureWrap, point);
      mapIntoChild(this, point);
      final float rotation = -(parent.textureWrap.getRotation() + getRotation());
      layer.setPosition(MathUtils.clamp(point[0] / width), MathUtils.clamp(point[1] / height), screenSize / (width * getScreenScale()), rotation);
      state.addLayer(layer);
    }

    /** How big a new emoji or sticker is on screen: 40% of the cropped photo's shorter side. */
    public float getDefaultLayerScreenSize () {
      return Math.max(Screen.dp(56f), Math.min(parent.croppedWidth, parent.croppedHeight) * .4f);
    }

    private float xgramZoomFactor () {
      final float zoom = parent.getParent() instanceof MediaCellView ? ((MediaCellView) parent.getParent()).getZoomFactor() : 1f;
      return zoom > 0f ? zoom : 1f;
    }

    /** How wide the cropped photo is on screen, with the zoom (a new text's field is no wider). */
    public float getPhotoScreenWidth () {
      return parent.croppedWidth * xgramZoomFactor();
    }

    /**
     * {@code screenSize} for a new {@code layer}, smaller if the layer would be wider or taller than 90% of the cropped
     * photo on screen, so the sent photo has all of it.
     */
    public float fitLayerScreenSize (XgramLayer layer, float screenSize) {
      final float zoom = xgramZoomFactor();
      final float maxWidth = parent.croppedWidth * zoom * .9f, maxHeight = parent.croppedHeight * zoom * .9f;
      final float contentWidth = layer.getContentWidth(), contentHeight = layer.getContentHeight();
      if (maxWidth > 0f && contentWidth > 0f && contentWidth * screenSize > maxWidth) {
        screenSize = maxWidth / contentWidth;
      }
      if (maxHeight > 0f && contentHeight > 0f && contentHeight * screenSize > maxHeight) {
        screenSize = maxHeight / contentHeight;
      }
      return screenSize;
    }

    /** {@code point}, in {@code child}'s parent's coordinates, in {@code child}'s own (through its scale and turn). */
    private static void mapIntoChild (View child, float[] point) {
      point[0] -= child.getLeft();
      point[1] -= child.getTop();
      final android.graphics.Matrix matrix = child.getMatrix();
      if (!matrix.isIdentity()) {
        final android.graphics.Matrix inverse = new android.graphics.Matrix();
        if (matrix.invert(inverse)) {
          inverse.mapPoints(point);
        }
      }
    }

    private boolean scheduled;

    @Override
    public void run () {
      if (scheduled) {
        scheduled = false;
        parent.paintView.invalidate();
      }
    }

    @Override
    public void onDrawingHasChanged (SimpleDrawing drawing, boolean delayed) {
      if (delayed) {
        if (!scheduled) {
          scheduled = true;
          postDelayed(this, 6);
          return;
        }
      } else if (scheduled) {
        removeCallbacks(this);
      }
      parent.paintView.invalidate();
    }
  }
}
