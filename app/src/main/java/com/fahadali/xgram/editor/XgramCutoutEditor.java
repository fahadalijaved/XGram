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

import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.Arrays;

/**
 * The photo editor's Cut out tool (XgramPaintTools.TOOL_CUTOUT): removes the photo's background into the drawing's
 * {@link XgramCutout}, without AI. Its row over the tools row picks how:
 * <ul>
 *   <li>Magic wand: a tap removes the area around it of about the tapped colour (CIE Lab distance within the
 *   tolerance), its edge pixels partly by how close they are; with Global on, that colour everywhere (a chroma key).</li>
 *   <li>Background eraser: a brush that removes, under it, the colours close to the one under its middle, so the edge of
 *   something else stays.</li>
 *   <li>Erase and Restore: brushes that remove and bring back whatever is under them.</li>
 * </ul>
 * and Soft edges (feathered about 2 pixels) and Invert. Each tap or stroke is one action of the drawing's undo history.
 * The touches come from EGLEditorView.ContentLayout in PaintMode.CUTOUT, in the drawing's coordinates; the photo's
 * colours are read from the photo the editor shows (a CIE Lab copy at the mask's size, made as the tool is picked).
 */
public final class XgramCutoutEditor {
  public static final int MODE_WAND = 0, MODE_BACKGROUND_ERASER = 1, MODE_ERASE = 2, MODE_RESTORE = 3;
  /** The cut-out row's toggles and action, after the modes. */
  public static final int ITEM_GLOBAL = 10, ITEM_SOFT = 11, ITEM_INVERT = 12;
  /** The cut-out row, in order. */
  public static final int[] ITEMS = {MODE_WAND, MODE_BACKGROUND_ERASER, MODE_ERASE, MODE_RESTORE, ITEM_GLOBAL, ITEM_SOFT, ITEM_INVERT};

  public static final float DEFAULT_TOLERANCE = .3f;
  /** The tolerance slider's range, as a CIE76 colour difference. */
  private static final float MIN_DELTA_E = 2f, MAX_DELTA_E = 60f;
  /** Past the tolerance, up to this many times it, pixels go partly (the soft part of the edge). */
  private static final float BAND = 1.4f;

  public static int clampMode (int mode) {
    return mode >= MODE_WAND && mode <= MODE_RESTORE ? mode : MODE_WAND;
  }

  public static @DrawableRes int icon (int item) {
    switch (item) {
      case MODE_BACKGROUND_ERASER: return R.drawable.xgram_editor_cutout_background;
      case MODE_ERASE: return R.drawable.xgram_editor_cutout_erase;
      case MODE_RESTORE: return R.drawable.xgram_editor_cutout_restore;
      case ITEM_GLOBAL: return R.drawable.xgram_editor_cutout_global;
      case ITEM_SOFT: return R.drawable.xgram_editor_cutout_soft;
      case ITEM_INVERT: return R.drawable.xgram_editor_cutout_invert;
      default: return R.drawable.xgram_editor_cutout_wand;
    }
  }

  public static @StringRes int name (int item) {
    switch (item) {
      case MODE_BACKGROUND_ERASER: return R.string.XgramEditorCutoutBackgroundEraser;
      case MODE_ERASE: return R.string.XgramEditorCutoutErase;
      case MODE_RESTORE: return R.string.XgramEditorCutoutRestore;
      case ITEM_GLOBAL: return R.string.XgramEditorCutoutGlobal;
      case ITEM_SOFT: return R.string.XgramEditorCutoutSoft;
      case ITEM_INVERT: return R.string.XgramEditorCutoutInvert;
      default: return R.string.XgramEditorCutoutWand;
    }
  }

  /** How to use a mode, shown as it is picked. */
  public static @StringRes int hint (int mode, boolean global) {
    switch (mode) {
      case MODE_BACKGROUND_ERASER: return R.string.XgramEditorCutoutBackgroundEraserHint;
      case MODE_ERASE: return R.string.XgramEditorCutoutEraseHint;
      case MODE_RESTORE: return R.string.XgramEditorCutoutRestoreHint;
      default: return global ? R.string.XgramEditorCutoutWandGlobalHint : R.string.XgramEditorCutoutWandHint;
    }
  }

  /** Whether the mode compares colours (the tolerance row shows). */
  public static boolean usesTolerance (int mode) {
    return mode == MODE_WAND || mode == MODE_BACKGROUND_ERASER;
  }

  /** Whether the mode is a brush (the size slider shows). */
  public static boolean usesSize (int mode) {
    return mode != MODE_WAND;
  }

  // Settings, kept for next time

  private int mode;
  private float tolerance;
  private boolean global, soft;

  public XgramCutoutEditor () {
    final XgramSettings settings = XgramSettings.instance();
    this.mode = clampMode(settings.editorCutoutMode(MODE_WAND));
    this.tolerance = Math.max(0f, Math.min(1f, settings.editorCutoutTolerance(DEFAULT_TOLERANCE)));
    this.global = settings.editorCutoutGlobal(false);
    this.soft = settings.editorCutoutSoft(true);
  }

  private void save () {
    XgramSettings.instance().setEditorCutout(mode, tolerance, global, soft);
  }

  public int getMode () {
    return mode;
  }

  public void setMode (int mode) {
    mode = clampMode(mode);
    if (this.mode != mode) {
      this.mode = mode;
      save();
    }
  }

  public float getTolerance () {
    return tolerance;
  }

  /** Kept for next time as the editor closes (it changes with every move of the slider). */
  public void setTolerance (float tolerance) {
    this.tolerance = Math.max(0f, Math.min(1f, tolerance));
  }

  public boolean isGlobal () {
    return global;
  }

  public void setGlobal (boolean global) {
    if (this.global != global) {
      this.global = global;
      save();
    }
  }

  // The last wand tap, as long as it is the drawing's last action: a new tolerance or Global does it again instead

  private @Nullable XgramCutout.Edit lastWandEdit;
  private @Nullable PaintState lastWandState;
  private int lastWandX, lastWandY;

  /**
   * Taps the wand again where it was last tapped, with the tolerance and Global now, if that is the last action. The new
   * result replaces the last one once it is worked out (a wand still working: again after it).
   */
  public void redoLastWand (@Nullable PaintState state) {
    if (wandBusy) {
      wandRedoQueued = true;
      return;
    }
    final XgramCutout.Edit edit = lastWandEdit;
    if (state == null || edit == null || lastWandState != state || isGestureActive() || !state.isLastAction(edit)) {
      return;
    }
    final XgramCutout cutout = state.getCutout();
    if (cutout == null || cutout.isEditing()) {
      return;
    }
    final Sampler sampler = readySampler(cutout);
    if (sampler == null) {
      return;
    }
    runWand(state, cutout, sampler, lastWandX, lastWandY, edit);
  }

  /** Soft edges: the drawing's cut-out if it has one, else the choice for a new one. */
  public boolean isSoft (@Nullable PaintState state) {
    final XgramCutout cutout = state != null ? state.getCutout() : null;
    return cutout != null ? cutout.isSoft() : soft;
  }

  /** Soft or hard edges for the drawing's cut-out (not an action: it is how the cut-out looks) and the next ones. */
  public void setSoft (@Nullable PaintState state, boolean soft) {
    if (this.soft != soft) {
      this.soft = soft;
      save();
    }
    final XgramCutout cutout = state != null ? state.getCutout() : null;
    if (cutout != null && cutout.isSoft() != soft) {
      cutout.setSoft(soft);
      state.notifySimpleDrawingChanged();
    }
  }

  /** Swaps what is removed and what is kept, as one action (with nothing removed yet, the whole photo goes). */
  public void invert (@Nullable PaintState state) {
    if (state == null || isGestureActive()) {
      return;
    }
    final XgramCutout cutout = obtainCutout(state);
    if (cutout != null) {
      state.trackCutoutEdit(cutout.invert());
      state.notifySimpleDrawingChanged();
    }
  }

  // The photo

  private @Nullable Bitmap photo;
  private boolean mirrorHorizontally, mirrorVertically;

  /**
   * The photo the editor shows under the drawing (null as it closes), mirrored as it is shown there, as for the blur
   * and pixelate brushes: the cut-out is in its grid, and its colours are what the wand and the background eraser read.
   */
  public void setPhoto (@Nullable Bitmap photo, boolean mirrorHorizontally, boolean mirrorVertically) {
    if (this.photo != photo) {
      this.photo = photo;
      sampler = null;
      samplerGeneration++;
      samplerLoading = false;
      cancelWand(); // a tap on the photo before goes nowhere
    }
    this.mirrorHorizontally = mirrorHorizontally;
    this.mirrorVertically = mirrorVertically;
  }

  /** The editor closed: a stroke in progress is undone, and the colour copy and the edit buffers are freed. */
  public void release (@Nullable PaintState state) {
    cancelGesture();
    cancelWand();
    save();
    lastWandEdit = null;
    lastWandState = null;
    setPhoto(null, false, false);
    final XgramCutout cutout = state != null ? state.getCutout() : null;
    if (cutout != null) {
      cutout.releaseEditBuffers();
    }
  }

  @Nullable
  private XgramCutout obtainCutout (@NonNull PaintState state) {
    final XgramCutout cutout = state.getCutout();
    if (cutout != null) {
      return cutout;
    }
    final Bitmap photo = this.photo;
    if (photo == null || photo.isRecycled()) {
      return null;
    }
    return state.obtainCutout(photo.getWidth(), photo.getHeight(), soft);
  }

  // The photo's colours: CIE Lab at the mask's size, L (0..100 as 0..255), a + 128, b + 128 for each pixel

  private static final class Sampler {
    final Bitmap photo;
    final int width, height;
    final byte[] lab;

    Sampler (Bitmap photo, int width, int height, byte[] lab) {
      this.photo = photo;
      this.width = width;
      this.height = height;
      this.lab = lab;
    }
  }

  private @Nullable Sampler sampler;
  private int samplerGeneration;
  private boolean samplerLoading;

  /** Makes the colour copy in the background (the tool was picked), so the first tap doesn't wait for it. */
  public void prepare (@Nullable PaintState state) {
    final Bitmap photo = this.photo;
    if (photo == null || photo.isRecycled() || samplerLoading) {
      return;
    }
    final XgramCutout cutout = state != null ? state.getCutout() : null;
    final int[] size = cutout != null ? new int[] {cutout.width, cutout.height} : XgramCutout.maskSize(photo.getWidth(), photo.getHeight());
    final Sampler sampler = this.sampler;
    if (sampler != null && sampler.photo == photo && sampler.width == size[0] && sampler.height == size[1]) {
      return;
    }
    // a copy of its own to read in the background (as XgramPaintBase copies on this thread): the viewer's photo is
    // recycled under the editor as the viewer closes
    final Bitmap source;
    try {
      source = photo.copy(Bitmap.Config.ARGB_8888, false);
    } catch (Throwable t) {
      Log.e("Unable to copy the photo for the cut-out", t);
      return;
    }
    if (source == null) {
      return;
    }
    samplerLoading = true;
    final int generation = ++samplerGeneration;
    final Thread thread = new Thread(() -> {
      Sampler made = null;
      try {
        made = makeSampler(photo, source, size[0], size[1]);
      } finally {
        source.recycle();
      }
      final Sampler result = made;
      UI.post(() -> {
        if (samplerGeneration == generation) {
          samplerLoading = false;
          if (result != null) {
            this.sampler = result;
          }
          runPendingWand(); // a tap that came while it was being made
        }
      });
    }, "XgramCutoutColors");
    thread.setPriority(Thread.NORM_PRIORITY - 1);
    thread.start();
  }

  /** The colour copy for {@code cutout} if it is ready. */
  @Nullable
  private Sampler readySampler (@NonNull XgramCutout cutout) {
    final Sampler sampler = this.sampler;
    return sampler != null && sampler.photo == photo && sampler.width == cutout.width && sampler.height == cutout.height ? sampler : null;
  }

  /**
   * The colour copy for {@code cutout}: null while one is being made in the background (it isn't thrown away and made
   * again here), made now only when none is.
   */
  @Nullable
  private Sampler obtainSampler (@NonNull XgramCutout cutout) {
    Sampler sampler = readySampler(cutout);
    if (sampler != null) {
      return sampler;
    }
    final Bitmap photo = this.photo;
    if (photo == null || photo.isRecycled() || samplerLoading) {
      return null;
    }
    sampler = makeSampler(photo, photo, cutout.width, cutout.height);
    this.sampler = sampler;
    return sampler;
  }

  private static final float[] LINEAR = new float[256];

  static {
    for (int i = 0; i < 256; i++) {
      final double c = i / 255.0;
      LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
    }
  }

  private static float labF (float t) {
    return t > 216f / 24389f ? (float) Math.cbrt(t) : (24389f / 27f * t + 16f) / 116f;
  }

  /** The colour copy of {@code source} (the photo itself, or a copy of it) for {@code photo}, at width x height. */
  @Nullable
  private static Sampler makeSampler (@NonNull Bitmap photo, @NonNull Bitmap source, int width, int height) {
    Bitmap readable = null, scaled = null;
    try {
      if (source.isRecycled()) {
        return null;
      }
      readable = source;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.getConfig() == Bitmap.Config.HARDWARE) {
        readable = source.copy(Bitmap.Config.ARGB_8888, false);
        if (readable == null) {
          return null;
        }
      }
      scaled = readable.getWidth() == width && readable.getHeight() == height ? readable : Bitmap.createScaledBitmap(readable, width, height, true);
      final int[] row = new int[width];
      final byte[] lab = new byte[width * height * 3];
      for (int y = 0; y < height; y++) {
        scaled.getPixels(row, 0, width, 0, y, width, 1);
        int o = y * width * 3;
        for (int x = 0; x < width; x++) {
          final int color = row[x];
          final float r = LINEAR[(color >> 16) & 0xff], g = LINEAR[(color >> 8) & 0xff], b = LINEAR[color & 0xff];
          // sRGB to XYZ (D65), then to CIE Lab
          final float fx = labF((0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f);
          final float fy = labF(0.2126f * r + 0.7152f * g + 0.0722f * b);
          final float fz = labF((0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f);
          final float l = 116f * fy - 16f, a = 500f * (fx - fy), bb = 200f * (fy - fz);
          lab[o] = (byte) Math.max(0, Math.min(255, Math.round(l * 2.55f)));
          lab[o + 1] = (byte) Math.max(0, Math.min(255, Math.round(a) + 128));
          lab[o + 2] = (byte) Math.max(0, Math.min(255, Math.round(bb) + 128));
          o += 3;
        }
      }
      return new Sampler(photo, width, height, lab);
    } catch (Throwable t) {
      Log.e("Unable to read the photo for the cut-out", t);
      return null;
    } finally {
      if (scaled != null && scaled != readable && scaled != source) {
        scaled.recycle();
      }
      if (readable != null && readable != source) {
        readable.recycle();
      }
    }
  }

  /** The colour difference the tolerance allows (CIE76). */
  private float threshold () {
    return MIN_DELTA_E + (MAX_DELTA_E - MIN_DELTA_E) * tolerance;
  }

  // Touches (EGLEditorView.ContentLayout, PaintMode.CUTOUT), in the drawing's coordinates

  private @Nullable PaintState gestureState;
  private @Nullable XgramCutout gestureCutout;
  private int gestureMode = -1;
  private boolean gestureMoved;
  private float gestureDownX, gestureDownY, gestureSlop;
  /** Where the brush was last, in the mask's pixels. */
  private float lastX, lastY;

  /** A finger is on the photo, or a wand tap is still being worked out (Invert and a new touch wait for it). */
  public boolean isGestureActive () {
    return gestureState != null || wandBusy;
  }

  /**
   * A finger went down at (x, y) on a drawing width x height; {@code radius} is the brush's there. The ring to show
   * under the finger (0: none), or -1 when the touch isn't taken.
   */
  public float onTouchDown (@NonNull PaintState state, float x, float y, int width, int height, float radius, float screenScale) {
    cancelGesture();
    if (width <= 0 || height <= 0 || photo == null || wandBusy) {
      return -1f;
    }
    final XgramCutout cutout = obtainCutout(state);
    if (cutout == null) {
      return -1f;
    }
    gestureState = state;
    gestureMode = mode;
    gestureMoved = false;
    gestureDownX = x;
    gestureDownY = y;
    gestureSlop = Screen.getTouchSlop() / Math.max(.01f, screenScale);
    if (gestureMode == MODE_WAND) {
      return 0f; // a tap, on its way up
    }
    if (gestureMode == MODE_BACKGROUND_ERASER && obtainSampler(cutout) == null) {
      // the colours not ready yet (still being made in the background, a moment after the tool is picked)
      gestureState = null;
      gestureMode = -1;
      return -1f;
    }
    gestureCutout = cutout;
    cutout.beginEdit();
    lastX = maskX(cutout, x, width);
    lastY = maskY(cutout, y, height);
    stamp(cutout, lastX, lastY, maskRadius(cutout, radius, width));
    state.notifySimpleDrawingChanged();
    return radius;
  }

  /** The finger moved to (x, y): the brush goes along the way. The ring to show, as onTouchDown. */
  public float onTouchMove (@NonNull PaintState state, float x, float y, int width, int height, float radius) {
    if (gestureState != state || width <= 0 || height <= 0) {
      return -1f;
    }
    if (!gestureMoved && Math.hypot(x - gestureDownX, y - gestureDownY) > gestureSlop) {
      gestureMoved = true;
    }
    final XgramCutout cutout = gestureCutout;
    if (gestureMode == MODE_WAND || cutout == null) {
      return 0f;
    }
    final float toX = maskX(cutout, x, width), toY = maskY(cutout, y, height);
    final float r = maskRadius(cutout, radius, width);
    final float distance = (float) Math.hypot(toX - lastX, toY - lastY);
    final float step = Math.max(.5f, r * .2f);
    final int steps = (int) Math.ceil(distance / step);
    for (int i = 1; i <= steps; i++) {
      final float t = (float) i / (float) steps;
      stamp(cutout, lastX + (toX - lastX) * t, lastY + (toY - lastY) * t, r);
    }
    lastX = toX;
    lastY = toY;
    if (steps > 0) {
      state.notifySimpleDrawingChanged();
    }
    return radius;
  }

  /**
   * The finger went up at (x, y). {@code cancel}: a pinch took the gesture over, the stroke is undone; {@code
   * cancelEvent}: the touch was cancelled (a stroke stays, as the eraser's), which is no wand tap.
   */
  public void onTouchUp (@NonNull PaintState state, float x, float y, int width, int height, boolean cancel, boolean cancelEvent) {
    if (gestureState != state) {
      cancelGesture();
      return;
    }
    final XgramCutout cutout = gestureCutout;
    final int mode = gestureMode;
    final boolean moved = gestureMoved;
    gestureState = null;
    gestureCutout = null;
    gestureMode = -1;
    if (mode == MODE_WAND) {
      if (!cancel && !cancelEvent && !moved && width > 0 && height > 0) {
        wand(state, x, y, width, height);
      }
      return;
    }
    if (cutout == null) {
      return;
    }
    if (cancel) {
      cutout.cancelEdit();
    } else {
      state.trackCutoutEdit(cutout.commitEdit());
    }
    state.notifySimpleDrawingChanged();
  }

  private void cancelGesture () {
    final PaintState state = gestureState;
    final XgramCutout cutout = gestureCutout;
    gestureState = null;
    gestureCutout = null;
    gestureMode = -1;
    if (cutout != null) {
      cutout.cancelEdit();
      if (state != null) {
        state.notifySimpleDrawingChanged();
      }
    }
  }

  // From the drawing to the mask: the photo is drawn over the whole drawing, mirrored around its middle as shown

  private float maskX (XgramCutout cutout, float x, int width) {
    float u = x / (float) width;
    if (mirrorHorizontally) {
      u = 1f - u;
    }
    return u * cutout.width;
  }

  private float maskY (XgramCutout cutout, float y, int height) {
    float v = y / (float) height;
    if (mirrorVertically) {
      v = 1f - v;
    }
    return v * cutout.height;
  }

  private static float maskRadius (XgramCutout cutout, float radius, int width) {
    return Math.max(.75f, radius * cutout.width / (float) width);
  }

  // The brushes

  /** One dab of the brush at (cx, cy), radius r, in the mask's pixels. */
  private void stamp (@NonNull XgramCutout cutout, float cx, float cy, float r) {
    final int w = cutout.width, h = cutout.height;
    final int x0 = Math.max(0, (int) Math.floor(cx - r - 1f)), x1 = Math.min(w - 1, (int) Math.ceil(cx + r + 1f));
    final int y0 = Math.max(0, (int) Math.floor(cy - r - 1f)), y1 = Math.min(h - 1, (int) Math.ceil(cy + r + 1f));
    if (x1 < x0 || y1 < y0) {
      return;
    }
    final byte[] mask = cutout.mask;
    // a slightly soft rim: smooth strokes without blurring the brush's size
    final float edge = 1f + r * .08f;
    final int mode = gestureMode;
    if (mode == MODE_BACKGROUND_ERASER) {
      final Sampler sampler = this.sampler;
      if (sampler == null || sampler.width != w || sampler.height != h) {
        return;
      }
      final byte[] lab = sampler.lab;
      final float[] reference = new float[3];
      averageColor(sampler, (int) cx, (int) cy, reference);
      final float rl = reference[0], ra = reference[1], rb = reference[2];
      final float threshold = threshold(), band = threshold * BAND + 1f;
      final float threshold2 = threshold * threshold, band2 = band * band;
      for (int y = y0; y <= y1; y++) {
        final float dy = y + .5f - cy;
        for (int x = x0; x <= x1; x++) {
          final float dx = x + .5f - cx;
          final float coverage = Math.min(1f, (r - (float) Math.sqrt(dx * dx + dy * dy)) / edge + .5f);
          if (coverage <= 0f) {
            continue;
          }
          final int i = y * w + x;
          final int o = i * 3;
          final float dl = ((lab[o] & 0xff) - rl) / 2.55f, da = (lab[o + 1] & 0xff) - ra, db = (lab[o + 2] & 0xff) - rb;
          final float d2 = dl * dl + da * da + db * db;
          if (d2 >= band2) {
            continue;
          }
          final float closeness = d2 <= threshold2 ? 1f : (band - (float) Math.sqrt(d2)) / (band - threshold);
          final int value = (int) (255f * coverage * closeness + .5f);
          if (value > (mask[i] & 0xff)) {
            mask[i] = (byte) value;
          }
        }
      }
    } else {
      final boolean restore = mode == MODE_RESTORE;
      for (int y = y0; y <= y1; y++) {
        final float dy = y + .5f - cy;
        for (int x = x0; x <= x1; x++) {
          final float dx = x + .5f - cx;
          final float coverage = Math.min(1f, (r - (float) Math.sqrt(dx * dx + dy * dy)) / edge + .5f);
          if (coverage <= 0f) {
            continue;
          }
          final int i = y * w + x;
          final int value = (int) (255f * coverage + .5f);
          final int current = mask[i] & 0xff;
          if (restore) {
            if (255 - value < current) {
              mask[i] = (byte) (255 - value);
            }
          } else if (value > current) {
            mask[i] = (byte) value;
          }
        }
      }
    }
    cutout.markChanged(x0, y0, x1 + 1, y1 + 1);
  }

  /** The average colour of the 3 x 3 pixels around (x, y): less noise than one pixel. */
  private static void averageColor (Sampler sampler, int x, int y, float[] out) {
    final int w = sampler.width, h = sampler.height;
    final byte[] lab = sampler.lab;
    float l = 0f, a = 0f, b = 0f;
    int count = 0;
    for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++) {
      for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
        final int o = (yy * w + xx) * 3;
        l += lab[o] & 0xff;
        a += lab[o + 1] & 0xff;
        b += lab[o + 2] & 0xff;
        count++;
      }
    }
    if (count == 0) {
      out[0] = out[1] = out[2] = 0f;
    } else {
      out[0] = l / count;
      out[1] = a / count;
      out[2] = b / count;
    }
  }

  // The magic wand: the fill is worked out off the UI thread (it reads only the colour copy, which never changes), then
  // goes into the mask on it as one action

  /** What a wand tap removes: how much (0..255) of each pixel in [left, right) x [top, bottom), rows at the mask's width. */
  private static final class WandResult {
    final int left, top, right, bottom;
    final byte[] values;

    WandResult (int left, int top, int right, int bottom, byte[] values) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      this.values = values;
    }
  }

  /** A wand tap is being worked out, or waits for the colours: new touches and Invert wait. */
  private boolean wandBusy;
  /** Bumped as the photo changes and as the editor closes: a result worked out before goes nowhere. */
  private int wandGeneration;
  /** A new tolerance or Global while the wand was working: the last tap again once it is done. */
  private boolean wandRedoQueued;
  /** A tap that came before the colours were ready: done once they are (prepare). */
  private @Nullable PaintState pendingWandState;
  private int pendingWandX, pendingWandY;

  private void cancelWand () {
    wandGeneration++;
    wandBusy = false;
    wandRedoQueued = false;
    pendingWandState = null;
  }

  private void wand (@NonNull PaintState state, float x, float y, int width, int height) {
    if (wandBusy) {
      return;
    }
    final XgramCutout cutout = obtainCutout(state);
    if (cutout == null) {
      return;
    }
    final int sx = (int) maskX(cutout, x, width), sy = (int) maskY(cutout, y, height);
    final Sampler sampler = readySampler(cutout);
    if (sampler != null) {
      runWand(state, cutout, sampler, sx, sy, null);
      return;
    }
    // the colours are still being made (or are to be): the tap is done once they are, not by making them again here
    prepare(state);
    if (samplerLoading) {
      pendingWandState = state;
      pendingWandX = sx;
      pendingWandY = sy;
      wandBusy = true;
    }
  }

  /** The tap that waited for the colours, now that they are made (or couldn't be: then it goes). */
  private void runPendingWand () {
    final PaintState state = pendingWandState;
    if (state == null) {
      return;
    }
    pendingWandState = null;
    wandBusy = false;
    final XgramCutout cutout = state.getCutout();
    final Sampler sampler = cutout != null ? readySampler(cutout) : null;
    if (sampler != null && !cutout.isEditing()) {
      runWand(state, cutout, sampler, pendingWandX, pendingWandY, null);
    } else {
      wandRedoQueued = false;
    }
  }

  /** Works out the wand at the mask's pixel (sx, sy) in the background; {@code redo}: the last tap's action it replaces. */
  private void runWand (@NonNull PaintState state, @NonNull XgramCutout cutout, @NonNull Sampler sampler, int sx, int sy, @Nullable XgramCutout.Edit redo) {
    final int x = Math.max(0, Math.min(cutout.width - 1, sx)), y = Math.max(0, Math.min(cutout.height - 1, sy));
    final float threshold = threshold();
    final boolean global = this.global;
    final int generation = ++wandGeneration;
    wandBusy = true;
    final Thread thread = new Thread(() -> {
      WandResult result = null;
      try {
        result = fill(sampler, x, y, threshold, global);
      } catch (Throwable t) {
        Log.e("Unable to work out the cut-out's magic wand", t);
      }
      final WandResult made = result;
      UI.post(() -> finishWand(generation, state, cutout, x, y, redo, made));
    }, "XgramCutoutWand");
    thread.start();
  }

  /** The wand's result into the mask as one action, in place of {@code redo}'s if that is still the last one. */
  private void finishWand (int generation, @NonNull PaintState state, @NonNull XgramCutout cutout, int sx, int sy, @Nullable XgramCutout.Edit redo, @Nullable WandResult result) {
    if (generation != wandGeneration) {
      return; // the editor closed or the photo changed
    }
    wandBusy = false;
    if (state.getCutout() == cutout && !cutout.isEditing() && (redo == null || state.isLastAction(redo))) {
      if (redo != null) {
        state.undoLastAction();
      }
      XgramCutout.Edit edit = null;
      if (result != null) {
        final byte[] mask = cutout.mask, values = result.values;
        final int w = cutout.width;
        cutout.beginEdit();
        for (int y = result.top; y < result.bottom; y++) {
          int i = y * w + result.left;
          for (int x = result.left; x < result.right; x++, i++) {
            final int value = values[i] & 0xff;
            if (value > (mask[i] & 0xff)) {
              mask[i] = (byte) value;
            }
          }
        }
        cutout.markChanged(result.left, result.top, result.right, result.bottom);
        edit = cutout.commitEdit();
      }
      if (edit != null) {
        state.trackCutoutEdit(edit);
        lastWandEdit = edit;
        lastWandState = state;
        lastWandX = sx;
        lastWandY = sy;
      } else if (redo != null) {
        // nothing at this tolerance (the tapped pixel already removed): the last result stays, to try again from
        state.redoLastAction();
        lastWandEdit = redo;
        lastWandState = state;
      } else {
        lastWandEdit = null;
        lastWandState = null;
      }
      state.notifySimpleDrawingChanged();
    }
    if (wandRedoQueued) {
      wandRedoQueued = false;
      redoLastWand(state);
    }
  }

  /** The wand at the pixel (sx, sy) of the colour copy: what it removes, or null for nothing. On any thread. */
  @Nullable
  private static WandResult fill (@NonNull Sampler sampler, int sx, int sy, float threshold, boolean global) {
    final int w = sampler.width, h = sampler.height;
    final float[] reference = new float[3];
    averageColor(sampler, sx, sy, reference);
    final float band = threshold * BAND + 1f;
    final float threshold2 = threshold * threshold, band2 = band * band;
    final float rl = reference[0], ra = reference[1], rb = reference[2];
    final byte[] lab = sampler.lab;
    final byte[] values = new byte[w * h];

    int minX = w, minY = h, maxX = -1, maxY = -1;
    if (global) {
      // the colour everywhere: a chroma key, its near colours partly
      for (int py = 0; py < h; py++) {
        int i = py * w;
        for (int px = 0; px < w; px++, i++) {
          final int o = i * 3;
          final float dl = ((lab[o] & 0xff) - rl) / 2.55f, da = (lab[o + 1] & 0xff) - ra, db = (lab[o + 2] & 0xff) - rb;
          final float d2 = dl * dl + da * da + db * db;
          if (d2 >= band2) {
            continue;
          }
          final int value = d2 <= threshold2 ? 255 : (int) (255f * (band - (float) Math.sqrt(d2)) / (band - threshold) + .5f);
          if (value > 0) {
            values[i] = (byte) Math.min(255, value);
            if (px < minX) minX = px;
            if (px > maxX) maxX = px;
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
          }
        }
      }
    } else {
      // the area around the tap: a scanline flood fill through the pixels within the tolerance
      // (per pixel: 0 not looked at, 1 taken, 2 too different)
      final byte[] visited = new byte[w * h];
      int[] stack = new int[1024];
      int size = 0;
      stack[size++] = sy * w + sx;
      while (size > 0) {
        final int p = stack[--size];
        final int py = p / w, row = py * w;
        final int px = p - row;
        if (visited[p] != 0) {
          continue;
        }
        if (!matches(lab, p, rl, ra, rb, threshold2)) {
          visited[p] = 2;
          continue;
        }
        int left = px, right = px;
        while (left > 0 && visited[row + left - 1] == 0 && matches(lab, row + left - 1, rl, ra, rb, threshold2)) {
          left--;
        }
        while (right < w - 1 && visited[row + right + 1] == 0 && matches(lab, row + right + 1, rl, ra, rb, threshold2)) {
          right++;
        }
        for (int i = row + left; i <= row + right; i++) {
          visited[i] = 1;
          values[i] = (byte) 255;
        }
        if (left < minX) minX = left;
        if (right > maxX) maxX = right;
        if (py < minY) minY = py;
        if (py > maxY) maxY = py;
        // the runs above and below to go on from
        for (int dir = -1; dir <= 1; dir += 2) {
          final int ny = py + dir;
          if (ny < 0 || ny >= h) {
            continue;
          }
          final int nrow = ny * w;
          boolean inRun = false;
          for (int nx = left; nx <= right; nx++) {
            final int q = nrow + nx;
            boolean open = visited[q] == 0;
            if (open && !matches(lab, q, rl, ra, rb, threshold2)) {
              visited[q] = 2;
              open = false;
            }
            if (open && !inRun) {
              if (size == stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
              }
              stack[size++] = q;
              inRun = true;
            } else if (!open) {
              inRun = false;
            }
          }
        }
      }
      // the area's edge: the pixels next to it go by how close they are, for a smooth edge
      if (maxX >= 0) {
        final int x0 = Math.max(0, minX - 1), x1 = Math.min(w - 1, maxX + 1);
        final int y0 = Math.max(0, minY - 1), y1 = Math.min(h - 1, maxY + 1);
        for (int py = y0; py <= y1; py++) {
          for (int px = x0; px <= x1; px++) {
            final int i = py * w + px;
            if (visited[i] == 1) {
              continue;
            }
            if (!((px > 0 && visited[i - 1] == 1) || (px < w - 1 && visited[i + 1] == 1) || (py > 0 && visited[i - w] == 1) || (py < h - 1 && visited[i + w] == 1))) {
              continue;
            }
            final int o = i * 3;
            final float dl = ((lab[o] & 0xff) - rl) / 2.55f, da = (lab[o + 1] & 0xff) - ra, db = (lab[o + 2] & 0xff) - rb;
            final float d2 = dl * dl + da * da + db * db;
            if (d2 >= band2) {
              continue;
            }
            final int value = (int) (255f * (band - (float) Math.sqrt(d2)) / (band - threshold) + .5f);
            if (value > 0) {
              values[i] = (byte) Math.min(255, value);
            }
          }
        }
        minX = x0;
        maxX = x1;
        minY = y0;
        maxY = y1;
      }
    }
    return maxX >= 0 ? new WandResult(minX, minY, maxX + 1, maxY + 1, values) : null;
  }

  private static boolean matches (byte[] lab, int i, float rl, float ra, float rb, float threshold2) {
    final int o = i * 3;
    final float dl = ((lab[o] & 0xff) - rl) / 2.55f, da = (lab[o + 1] & 0xff) - ra, db = (lab[o + 2] & 0xff) - rb;
    return dl * dl + da * da + db * db <= threshold2;
  }
}
