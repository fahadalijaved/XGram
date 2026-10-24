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

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.mediaview.paint.PaintMode;

/**
 * The photo editor's drawing tools (the paint section of MediaViewController): Pen (Telegram X's doodle), Marker,
 * Neon, Arrow, Shapes (rectangle or ellipse, outlined or filled), Blur and Pixelate (paint over the photo to hide what
 * is under the stroke) and Eraser (removes the drawings it touches). Each tool has its own size range and keeps its
 * size and colour; the tool picked last comes back the next time (XgramSettings).
 */
public final class XgramPaintTools {
  private XgramPaintTools () { }

  public static final int TOOL_PEN = 0, TOOL_MARKER = 1, TOOL_NEON = 2, TOOL_ARROW = 3, TOOL_SHAPES = 4,
    TOOL_BLUR = 5, TOOL_PIXELATE = 6, TOOL_ERASER = 7;
  /** In the order of the tools row. */
  public static final int[] TOOLS = {TOOL_PEN, TOOL_MARKER, TOOL_NEON, TOOL_ARROW, TOOL_SHAPES, TOOL_BLUR, TOOL_PIXELATE, TOOL_ERASER};

  public static final int SHAPE_RECTANGLE = 0, SHAPE_ELLIPSE = 1, SHAPE_RECTANGLE_FILLED = 2, SHAPE_ELLIPSE_FILLED = 3;
  public static final int[] SHAPES = {SHAPE_RECTANGLE, SHAPE_ELLIPSE, SHAPE_RECTANGLE_FILLED, SHAPE_ELLIPSE_FILLED};

  /** The size range of each tool, in dp: the stroke width (the eraser: the width it erases). */
  private static final float[] MIN_SIZE = {4f, 10f, 4f, 4f, 3f, 16f, 16f, 12f};
  private static final float[] MAX_SIZE = {20f, 44f, 20f, 20f, 16f, 64f, 64f, 48f};
  /** Where a tool's size starts, 0..1 of its range (the pen: Telegram X's .25). */
  private static final float[] DEFAULT_SIZE = {.25f, .35f, .3f, .25f, .25f, .4f, .4f, .35f};
  /** Telegram X's orange; a highlighter yellow for the marker and cyan for neon. */
  private static final int[] DEFAULT_COLOR = {0xffff3300, 0xffffeb3b, 0xff00e5ff, 0xffff3300, 0xffff3300, 0xffffffff, 0xffffffff, 0xffffffff};

  public static int clampTool (int tool) {
    return tool >= TOOL_PEN && tool <= TOOL_ERASER ? tool : TOOL_PEN;
  }

  public static int clampShape (int shape) {
    return shape >= SHAPE_RECTANGLE && shape <= SHAPE_ELLIPSE_FILLED ? shape : SHAPE_RECTANGLE;
  }

  /** The drawing mode (PaintMode) of a tool; Shapes draws {@code shape}. */
  public static int paintMode (int tool, int shape) {
    switch (tool) {
      case TOOL_MARKER: return PaintMode.MARKER;
      case TOOL_NEON: return PaintMode.NEON;
      case TOOL_ARROW: return PaintMode.ARROW;
      case TOOL_SHAPES: {
        switch (shape) {
          case SHAPE_ELLIPSE: return PaintMode.ELLIPSE;
          case SHAPE_RECTANGLE_FILLED: return PaintMode.RECTANGLE_FILLED;
          case SHAPE_ELLIPSE_FILLED: return PaintMode.ELLIPSE_FILLED;
          default: return PaintMode.RECTANGLE;
        }
      }
      case TOOL_BLUR: return PaintMode.BLUR;
      case TOOL_PIXELATE: return PaintMode.PIXELATE;
      case TOOL_ERASER: return PaintMode.ERASER;
      default: return PaintMode.PATH;
    }
  }

  /** Blur, Pixelate and the eraser have no colour: the colour row hides for them. */
  public static boolean usesColor (int tool) {
    return tool != TOOL_BLUR && tool != TOOL_PIXELATE && tool != TOOL_ERASER;
  }

  /** The tool's size in dp at {@code factor} (0..1) of its range. */
  public static float size (int tool, float factor) {
    tool = clampTool(tool);
    return MIN_SIZE[tool] + (MAX_SIZE[tool] - MIN_SIZE[tool]) * Math.max(0f, Math.min(1f, factor));
  }

  public static @DrawableRes int icon (int tool, int shape) {
    switch (tool) {
      case TOOL_MARKER: return R.drawable.xgram_editor_marker;
      case TOOL_NEON: return R.drawable.xgram_editor_neon;
      case TOOL_ARROW: return R.drawable.xgram_editor_arrow;
      case TOOL_SHAPES: return shapeIcon(shape);
      case TOOL_BLUR: return R.drawable.xgram_editor_blur;
      case TOOL_PIXELATE: return R.drawable.xgram_editor_pixelate;
      case TOOL_ERASER: return R.drawable.xgram_editor_eraser;
      default: return R.drawable.xgram_editor_pen;
    }
  }

  public static @DrawableRes int shapeIcon (int shape) {
    switch (shape) {
      case SHAPE_ELLIPSE: return R.drawable.xgram_editor_ellipse;
      case SHAPE_RECTANGLE_FILLED: return R.drawable.xgram_editor_rectangle_filled;
      case SHAPE_ELLIPSE_FILLED: return R.drawable.xgram_editor_ellipse_filled;
      default: return R.drawable.xgram_editor_rectangle;
    }
  }

  public static @StringRes int name (int tool) {
    switch (tool) {
      case TOOL_MARKER: return R.string.XgramEditorMarker;
      case TOOL_NEON: return R.string.XgramEditorNeon;
      case TOOL_ARROW: return R.string.XgramEditorArrow;
      case TOOL_SHAPES: return R.string.XgramEditorShapes;
      case TOOL_BLUR: return R.string.XgramEditorBlur;
      case TOOL_PIXELATE: return R.string.XgramEditorPixelate;
      case TOOL_ERASER: return R.string.XgramEditorEraser;
      default: return R.string.XgramEditorPen;
    }
  }

  public static @StringRes int shapeName (int shape) {
    switch (shape) {
      case SHAPE_ELLIPSE: return R.string.XgramEditorEllipse;
      case SHAPE_RECTANGLE_FILLED: return R.string.XgramEditorRectangleFilled;
      case SHAPE_ELLIPSE_FILLED: return R.string.XgramEditorEllipseFilled;
      default: return R.string.XgramEditorRectangle;
    }
  }

  /** What the colour row says while a tool without a colour is picked, or 0. */
  public static @StringRes int hint (int tool) {
    switch (tool) {
      case TOOL_BLUR: return R.string.XgramEditorBlurHint;
      case TOOL_PIXELATE: return R.string.XgramEditorPixelateHint;
      case TOOL_ERASER: return R.string.XgramEditorEraserHint;
    }
    return 0;
  }

  // Remembered

  public static int savedTool () {
    return clampTool(XgramSettings.instance().editorTool(TOOL_PEN));
  }

  public static int savedShape () {
    return clampShape(XgramSettings.instance().editorShape(SHAPE_RECTANGLE));
  }

  public static void saveTool (int tool, int shape) {
    XgramSettings.instance().setEditorTool(clampTool(tool), clampShape(shape));
  }

  /** The tool's size, 0..1 of its range. */
  public static float savedSize (int tool) {
    tool = clampTool(tool);
    return Math.max(0f, Math.min(1f, XgramSettings.instance().editorSize(tool, DEFAULT_SIZE[tool])));
  }

  public static int savedColor (int tool) {
    tool = clampTool(tool);
    return XgramSettings.instance().editorColor(tool, DEFAULT_COLOR[tool]) | 0xff000000;
  }

  /** The hue track position the tool's colour was picked at, or -1: found from the colour. */
  public static float savedHue (int tool) {
    float hue = XgramSettings.instance().editorHue(clampTool(tool));
    return hue >= 0f && hue <= 1f ? hue : -1f;
  }

  public static void saveBrush (int tool, float size, int color, float hue) {
    XgramSettings.instance().setEditorBrush(clampTool(tool), Math.max(0f, Math.min(1f, size)), color, hue);
  }
}
