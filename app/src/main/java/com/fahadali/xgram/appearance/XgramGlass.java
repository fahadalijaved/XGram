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
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.tool.Screen;

/**
 * Liquid glass (a test, Settings › Testing utilities): a panel shows what is behind it, blurred and bent at its edges
 * (refraction), under a light tint, with a bright rim, as iOS 26's glass. Android 13+ gets the refraction (an AGSL
 * shader), Android 12 the blur alone; older Androids keep the solid panel.
 * <p>
 * How: before each frame, the window's root view is recorded into a RenderNode (BlurView's way) with every glass panel
 * left out ({@link #isRecording}: the panels skip their drawing then), the node gets the blur (and the refraction
 * shader, whose uniforms are the panel's rounded rectangle in the root), and the panel draws the node clipped to its
 * shape, moved to where the panel is. It redraws with each frame of the window, so it follows scrolling.
 */
public final class XgramGlass {
  /** Whether the glass is on and this Android can draw it. */
  public static boolean on () {
    // Only with Expressive design: its floating pill is what reads as glass (the classic bar looked foggy, owner 11-25)
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && XgramSettings.instance().liquidGlassTest() && XgramExpressive.isEnabled();
  }

  private static boolean recording;

  /** True while a backdrop is recorded: a glass panel draws nothing then (it would see itself). */
  public static boolean isRecording () {
    return recording;
  }

  // The shaders: Kyant's backdrop (github.com/Kyant0/backdrop, Copyright 2025 Kyant, Apache License 2.0): a rounded
  // rectangle's signed distance, the lens (the edge band bends the backdrop outwards along the shape's normal, a
  // circle's profile, with a touch of colour dispersion) and the rim highlight (light from an angle).

  private static final String SDF =
    "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
    "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
    "  float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
    "  float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
    "  return outside + inside;\n" +
    "}\n" +
    "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
    "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
    "  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
    "    return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
    "  } else {\n" +
    "    float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
    "    return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
    "  }\n" +
    "}\n";

  /** The lens: {@code size} and {@code offset} place the shape in the content's coordinates (offset = -its top left). */
  private static final String LENS =
    "uniform shader content;\n" +
    "uniform float2 size;\n" +
    "uniform float2 offset;\n" +
    "uniform float radius;\n" +
    "uniform float refractionHeight;\n" +
    "uniform float refractionAmount;\n" +
    "uniform float depthEffect;\n" +
    "uniform float chromaticAberration;\n" +
    SDF +
    "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n" +
    "half4 main(float2 coord) {\n" +
    "  float2 halfSize = size * 0.5;\n" +
    "  float2 centeredCoord = (coord + offset) - halfSize;\n" +
    "  float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
    "  if (-sd >= refractionHeight || sd > 0.0) { return content.eval(coord); }\n" +
    "  sd = min(sd, 0.0);\n" +
    "  float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
    "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
    "  float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));\n" +
    "  float2 refractedCoord = coord + d * grad;\n" +
    "  float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));\n" +
    "  float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
    "  half4 color = half4(0.0);\n" +
    "  half4 red = content.eval(refractedCoord + dispersedCoord);\n" +
    "  color.r += red.r / 3.5; color.a += red.a / 7.0;\n" +
    "  half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));\n" +
    "  color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;\n" +
    "  half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));\n" +
    "  color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;\n" +
    "  half4 green = content.eval(refractedCoord);\n" +
    "  color.g += green.g / 3.5; color.a += green.a / 7.0;\n" +
    "  half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));\n" +
    "  color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;\n" +
    "  half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));\n" +
    "  color.b += blue.b / 3.0; color.a += blue.a / 7.0;\n" +
    "  half4 purple = content.eval(refractedCoord - dispersedCoord);\n" +
    "  color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;\n" +
    "  return color;\n" +
    "}\n";

  /** The rim's light: brightest where the edge faces {@code angle}. */
  private static final String HIGHLIGHT =
    "uniform float2 size;\n" +
    "uniform float radius;\n" +
    "layout(color) uniform half4 color;\n" +
    "uniform float angle;\n" +
    "uniform float falloff;\n" +
    SDF +
    "half4 main(float2 coord) {\n" +
    "  float2 halfSize = size * 0.5;\n" +
    "  float2 centeredCoord = coord - halfSize;\n" +
    "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
    "  float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);\n" +
    "  float2 normal = float2(cos(angle), sin(angle));\n" +
    "  float d = dot(grad, normal);\n" +
    "  return color * pow(abs(d), falloff);\n" +
    "}\n";

  /** The lens effect for a rounded rectangle at {@code box} (in the content's coordinates), or null below Android 13. */
  @Nullable
  public static RenderEffect lensEffect (@Nullable Object[] cache, RectF box, float radius, float height, float amount, boolean dispersion) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || height <= 0f || amount == 0f) {
      return null;
    }
    RuntimeShader shader = cache != null ? (RuntimeShader) cache[0] : null;
    if (shader == null) {
      shader = new RuntimeShader(LENS);
      if (cache != null) {
        cache[0] = shader;
      }
    }
    shader.setFloatUniform("size", box.width(), box.height());
    shader.setFloatUniform("offset", -box.left, -box.top);
    shader.setFloatUniform("radius", Math.min(radius, Math.min(box.width(), box.height()) / 2f));
    shader.setFloatUniform("refractionHeight", Math.min(height, Math.min(box.width(), box.height()) / 2f));
    shader.setFloatUniform("refractionAmount", -amount);
    shader.setFloatUniform("depthEffect", 1f);
    shader.setFloatUniform("chromaticAberration", dispersion ? 1f : 0f);
    return RenderEffect.createRuntimeShaderEffect(shader, "content");
  }

  private static final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  @Nullable private static Object highlightShader;

  /** The rim: a thin line of light around {@code rect}, lit from the top left (Kyant's Highlight.Default). */
  public static void drawHighlight (Canvas c, RectF rect, float radius, float alpha) {
    if (alpha <= 0f) {
      return;
    }
    final float stroke = Math.max(1f, Screen.dpf(1f));
    highlightPaint.setStyle(Paint.Style.STROKE);
    highlightPaint.setStrokeWidth(stroke);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      RuntimeShader shader = (RuntimeShader) highlightShader;
      if (shader == null) {
        shader = new RuntimeShader(HIGHLIGHT);
        highlightShader = shader;
      }
      shader.setFloatUniform("size", rect.width(), rect.height());
      shader.setFloatUniform("radius", radius);
      shader.setColorUniform("color", 0xFFFFFFFF);
      shader.setFloatUniform("angle", (float) Math.toRadians(45));
      shader.setFloatUniform("falloff", 1f);
      highlightPaint.setShader(shader);
      highlightPaint.setBlendMode(android.graphics.BlendMode.PLUS);
      highlightPaint.setAlpha(Math.round(255 * .5f * alpha));
      final int save = c.save();
      c.translate(rect.left, rect.top);
      final float inset = stroke / 2f;
      c.drawRoundRect(inset, inset, rect.width() - inset, rect.height() - inset, radius - inset, radius - inset, highlightPaint);
      c.restoreToCount(save);
    } else {
      highlightPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x8CFFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP));
      highlightPaint.setAlpha(Math.round(255 * alpha));
      final float inset = stroke / 2f;
      c.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, radius - inset, radius - inset, highlightPaint);
    }
  }

  /** Saturation 1.5 (Kyant's vibrancy): what shows through glass is livelier, as iOS's. */
  private static android.graphics.ColorMatrixColorFilter vibrancy () {
    final android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
    m.setSaturation(1.5f);
    return new android.graphics.ColorMatrixColorFilter(m);
  }

  /** One glass panel: its view, its backdrop node and the root it records. */
  public static final class Panel {
    private final View view;
    @Nullable private Object node; // RenderNode (API 29+), kept as Object for older Androids' class loading
    private final Object[] shaderCache = new Object[1]; // the lens' RuntimeShader (API 33+)
    @Nullable private View root;
    private final int[] rootLocation = new int[2], viewLocation = new int[2];
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private boolean attached;
    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
      record();
      return true;
    };

    @Nullable private View branch;
    private float tintAlpha = .35f, blurDp = 6f;

    /** How much of the tint and how much blur: a text field takes more of both, so its text and icons stay readable. */
    public Panel setLook (float tintAlpha, float blurDp) {
      this.tintAlpha = tintAlpha;
      this.blurDp = blurDp;
      return this;
    }

    /**
     * The backdrop is what is drawn before {@code branch} (an ancestor of the panel's view) instead of before the panel's
     * own nearest ancestor with earlier siblings: the chat's message field records the messages and the wallpaper
     * behind the whole composer, not the composer's own rows above the field.
     */
    public Panel setBranch (@Nullable View branch) {
      this.branch = branch;
      return this;
    }

    public Panel (@NonNull View view) {
      this.view = view;
      rimPaint.setStyle(Paint.Style.STROKE);
    }

    private void attach () {
      if (attached) {
        return;
      }
      attached = true;
      view.getViewTreeObserver().addOnPreDrawListener(preDraw);
    }

    /** Stops recording (the panel is gone, or the glass was switched off). */
    public void detach () {
      if (attached) {
        attached = false;
        view.getViewTreeObserver().removeOnPreDrawListener(preDraw);
      }
    }

    private void record () {
      if (!on() || !view.isAttachedToWindow() || view.getWidth() == 0) {
        return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        recordImpl();
      }
    }

    /**
     * The backdrop: the views drawn before the panel, i.e. the earlier siblings of the panel (or of the first of its
     * parents that has some) in their parent. Never the panel's own branch: its display list draws this node, so a
     * backdrop holding it would hold itself (the renderer recursed until it crashed).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private void recordImpl () {
      View branch = this.branch != null ? this.branch : view;
      // In a popup (menus, the message popup): what is behind the popup, not the popup's own other parts
      for (android.view.ViewParent p = view.getParent(); p instanceof View; p = p.getParent()) {
        if (p instanceof org.thunderdog.challegram.widget.PopupLayout) {
          branch = (View) p;
          break;
        }
      }
      android.view.ViewGroup parent = null;
      int index = -1;
      while (branch.getParent() instanceof android.view.ViewGroup) {
        final android.view.ViewGroup p = (android.view.ViewGroup) branch.getParent();
        final int i = p.indexOfChild(branch);
        if (i > 0) {
          parent = p;
          index = i;
          break;
        }
        branch = p;
      }
      if (parent == null || parent.getWidth() == 0) {
        return;
      }
      this.root = parent;
      RenderNode node = (RenderNode) this.node;
      if (node == null) {
        node = new RenderNode("XgramGlass");
        this.node = node;
      }
      node.setPosition(0, 0, parent.getWidth(), parent.getHeight());
      final RecordingCanvas canvas = node.beginRecording();
      recording = true;
      try {
        final android.graphics.drawable.Drawable background = parent.getBackground();
        if (background != null) {
          background.draw(canvas);
        }
        for (int i = 0; i < index; i++) {
          final View child = parent.getChildAt(i);
          if (child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0f) {
            continue;
          }
          final int save = canvas.save();
          canvas.translate(child.getLeft(), child.getTop());
          canvas.concat(child.getMatrix());
          child.draw(canvas);
          canvas.restoreToCount(save);
        }
      } finally {
        recording = false;
        node.endRecording();
      }
      view.invalidate(); // the panel draws the new backdrop (each frame of the window: it follows scrolling)
    }

    /**
     * Draws the glass in {@code rect} (the panel's coordinates) with corner {@code radius}: the backdrop, then
     * {@code tint} over it and the rim. False when the glass is off here: the caller draws its solid panel instead.
     */
    public boolean draw (@NonNull Canvas c, @NonNull RectF rect, float radius, int tint) {
      if (!on()) {
        detach();
        restoreElevation();
        return false;
      }
      attach();
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !c.isHardwareAccelerated()) {
        return false;
      }
      final boolean drawn = drawImpl(c, rect, radius, tint);
      if (drawn) {
        dropElevation();
      }
      return drawn;
    }

    // Android draws an elevated view's shadow under its whole shape when the view is see-through, and its inner part is
    // a blocky polygon: through glass it showed as a square edge (the New chat button). Glass has no system shadow.

    private float savedElevation = Float.NaN;

    private void dropElevation () {
      if (Float.isNaN(savedElevation) && view.getElevation() != 0f) {
        savedElevation = view.getElevation();
        view.post(() -> view.setElevation(0f));
      }
    }

    private void restoreElevation () {
      if (!Float.isNaN(savedElevation)) {
        final float elevation = savedElevation;
        savedElevation = Float.NaN;
        view.post(() -> view.setElevation(elevation));
      }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private boolean drawImpl (Canvas c, RectF rect, float radius, int tint) {
      final RenderNode node = (RenderNode) this.node;
      final View root = this.root;
      if (node == null || root == null || !node.hasDisplayList()) {
        view.postInvalidateOnAnimation(); // the first backdrop comes with the next frame
        return false;
      }
      root.getLocationInWindow(rootLocation);
      view.getLocationInWindow(viewLocation);
      final float dx = viewLocation[0] - rootLocation[0], dy = viewLocation[1] - rootLocation[1];
      // The panel in the root, for the refraction
      box.set(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy);
      // Kyant's order: vibrancy, a light blur, then the lens (clear glass, as iOS's, not frosted)
      RenderEffect effect = RenderEffect.createChainEffect(
        RenderEffect.createBlurEffect(Screen.dpf(blurDp), Screen.dpf(blurDp), Shader.TileMode.CLAMP),
        RenderEffect.createColorFilterEffect(vibrancy())
      );
      // The bend scales with the panel: 24dp on the bar, less on a button (a 56dp FAB bent the chat list's white cards
      // into a hard rectangle across its middle)
      final float bend = Math.min(Screen.dpf(24f), Math.min(rect.width(), rect.height()) * .22f);
      final RenderEffect lens = lensEffect(shaderCache, box, radius, bend, bend, true);
      if (lens != null) {
        effect = RenderEffect.createChainEffect(lens, effect);
      }
      node.setRenderEffect(effect);

      final int save = c.save();
      clip.reset();
      clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
      c.clipPath(clip);
      c.translate(-dx, -dy);
      c.drawRenderNode(node);
      c.restoreToCount(save);

      // The tint: the panel's own colour, light enough to see through (Kyant's surface at 40 %)
      tintPaint.setColor(XgramExpressive.withAlpha(tint, tintAlpha));
      c.drawRoundRect(rect, radius, radius, tintPaint);
      drawHighlight(c, rect, radius, 1f);
      return true;
    }
  }

  /**
   * A view's background as glass while the glass is on (else {@code color}, as a plain rounded rectangle): for popup
   * containers (the message popup's reaction pill and grid).
   */
  public static android.graphics.drawable.Drawable background (View view, int color, float cornerPx) {
    final Panel panel = new Panel(view);
    final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    solid.setColor(color);
    final RectF bounds = new RectF();
    return new android.graphics.drawable.Drawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        bounds.set(getBounds());
        if (!on() || !panel.draw(c, bounds, cornerPx, color)) {
          c.drawRoundRect(bounds, cornerPx, cornerPx, solid);
        }
      }

      @Override
      public void getOutline (@NonNull android.graphics.Outline outline) {
        outline.setRoundRect(getBounds(), cornerPx);
      }

      @Override
      public void setAlpha (int alpha) {
        solid.setAlpha(alpha);
      }

      @Override
      public void setColorFilter (@Nullable android.graphics.ColorFilter filter) { }

      @Override
      public int getOpacity () {
        return android.graphics.PixelFormat.TRANSLUCENT;
      }
    };
  }

  private XgramGlass () { }
}
