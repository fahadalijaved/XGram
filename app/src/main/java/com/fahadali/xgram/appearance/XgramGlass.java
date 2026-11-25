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
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && XgramSettings.instance().liquidGlassTest();
  }

  private static boolean recording;

  /** True while a backdrop is recorded: a glass panel draws nothing then (it would see itself). */
  public static boolean isRecording () {
    return recording;
  }

  private static final String REFRACTION =
    "uniform shader content;\n" +
    "uniform float4 box;\n" +       // the panel in the root: left, top, right, bottom
    "uniform float radius;\n" +
    "uniform float depth;\n" +      // how far the edge bends, in px
    "uniform float band;\n" +       // how wide the bending band along the edge is, in px
    "half4 main(float2 p) {\n" +
    "  float2 c = (box.xy + box.zw) * 0.5;\n" +
    "  float2 h = (box.zw - box.xy) * 0.5;\n" +
    "  float2 q = abs(p - c) - h + radius;\n" +
    "  float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n" + // < 0 inside
    "  if (d > 0.0) { return content.eval(p); }\n" +
    "  float t = clamp(1.0 + d / band, 0.0, 1.0);\n" +   // 1 at the edge, 0 a band's width in
    "  float2 n = normalize(max(abs(p - c) - (h - radius), 0.0001) * sign(p - c));\n" +
    "  float2 offset = -n * depth * t * t * t;\n" +
    "  half4 col = content.eval(p + offset);\n" +
    "  col.rgb += half3(0.06) * half(t * t);\n" +         // the rim catches a little light
    "  return col;\n" +
    "}\n";

  /** One glass panel: its view, its backdrop node and the root it records. */
  public static final class Panel {
    private final View view;
    @Nullable private Object node; // RenderNode (API 29+), kept as Object for older Androids' class loading
    @Nullable private Object shader; // RuntimeShader (API 33+)
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

    @RequiresApi(Build.VERSION_CODES.S)
    private void recordImpl () {
      final View root = view.getRootView();
      if (root == null || root.getWidth() == 0) {
        return;
      }
      this.root = root;
      RenderNode node = (RenderNode) this.node;
      if (node == null) {
        node = new RenderNode("XgramGlass");
        this.node = node;
      }
      node.setPosition(0, 0, root.getWidth(), root.getHeight());
      final RecordingCanvas canvas = node.beginRecording();
      recording = true;
      try {
        root.draw(canvas);
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
        return false;
      }
      attach();
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !c.isHardwareAccelerated()) {
        return false;
      }
      return drawImpl(c, rect, radius, tint);
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
      RenderEffect effect = RenderEffect.createBlurEffect(Screen.dpf(18f), Screen.dpf(18f), Shader.TileMode.CLAMP);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader shader = (RuntimeShader) this.shader;
        if (shader == null) {
          shader = new RuntimeShader(REFRACTION);
          this.shader = shader;
        }
        shader.setFloatUniform("box", box.left, box.top, box.right, box.bottom);
        shader.setFloatUniform("radius", radius);
        shader.setFloatUniform("depth", Screen.dpf(10f));
        shader.setFloatUniform("band", Math.min(Screen.dpf(22f), Math.min(rect.width(), rect.height()) / 2f));
        effect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), effect);
      }
      node.setRenderEffect(effect);

      final int save = c.save();
      clip.reset();
      clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
      c.clipPath(clip);
      c.translate(-dx, -dy);
      c.drawRenderNode(node);
      c.restoreToCount(save);

      // The tint: the panel's own colour, light enough to see through
      tintPaint.setColor(XgramExpressive.withAlpha(tint, .42f));
      c.drawRoundRect(rect, radius, radius, tintPaint);
      // The rim: bright at the top, fading down (light from above)
      final float stroke = Math.max(1f, Screen.dpf(1f));
      rimPaint.setStrokeWidth(stroke);
      rimPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x8CFFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP));
      final float inset = stroke / 2f;
      c.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, radius - inset, radius - inset, rimPaint);
      return true;
    }
  }

  private XgramGlass () { }
}
