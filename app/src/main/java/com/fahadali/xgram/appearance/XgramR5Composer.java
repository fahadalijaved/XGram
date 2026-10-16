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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.core.ColorUtils;

/**
 * Material 3 Expressive, round 5: the composer overlays and stickers. The floating container shared by the inline
 * result grids and the sticker suggestion strip (an M3 menu surface: surfaceContainer, 16dp corners, elevation level
 * 2, 8dp from the pane edges and the composer), the grid tiles inside it, the floating toolbar of the sticker
 * preview with its plain tooltips, the record overlay's field shape and the vertical toolbar of the round video
 * controls. Everything here is only used while {@link XgramExpressive#isEnabled()} is true; the callers keep
 * Telegram&#160;X's code in their else paths. Main thread only; nothing is allocated per frame.
 */
public final class XgramR5Composer {
  private XgramR5Composer () { }

  public static boolean on () {
    return XgramExpressive.isEnabled();
  }

  // Sentence case

  /**
   * Telegram&#160;X's {@code tgxRes} in sentence case ({@code r5Res}) while the language is Telegram&#160;X's own
   * English and Expressive design is on; a translation, or Expressive design off, keeps the Telegram&#160;X string.
   */
  @NonNull
  public static String sentence (@StringRes int tgxRes, @StringRes int r5Res) {
    final String tgx = Lang.getString(tgxRes);
    if (!XgramExpressive.isEnabled()) {
      return tgx;
    }
    try {
      if (tgx.equals(AppContext.get().getString(tgxRes))) {
        return Lang.getString(r5Res);
      }
    } catch (Throwable ignored) { }
    return tgx;
  }

  /** The sentence-case form of a Telegram&#160;X string used by the sticker menus, or the string itself. */
  @NonNull
  public static String sentence (@StringRes int tgxRes) {
    if (tgxRes == R.string.ViewPackPreview) {
      return sentence(tgxRes, R.string.XgramR5ComposerViewPack);
    } else if (tgxRes == R.string.PasteCustomEmoji) {
      return sentence(tgxRes, R.string.XgramR5ComposerPasteEmoji);
    } else if (tgxRes == R.string.ShareCustomEmoji) {
      return sentence(tgxRes, R.string.XgramR5ComposerShareEmoji);
    } else if (tgxRes == R.string.AddToFavorites) {
      return sentence(tgxRes, R.string.XgramR5ComposerAddToFavorites);
    } else if (tgxRes == R.string.RemoveFromFavorites) {
      return sentence(tgxRes, R.string.XgramR5ComposerRemoveFromFavorites);
    }
    return Lang.getString(tgxRes);
  }

  /**
   * The label of a sticker preview menu item in sentence case: callers (some of them outside this batch) pass
   * Telegram&#160;X's upper-case titles, so a title that is one of the known strings, upper-cased or not, is replaced by
   * its sentence-case form; any other title is kept.
   */
  @NonNull
  public static String menuLabel (@Nullable String title, @IdRes int viewId) {
    if (title == null) {
      return "";
    }
    final int[] candidates;
    if (viewId == R.id.btn_view) {
      candidates = new int[] {R.string.ViewPackPreview};
    } else if (viewId == R.id.btn_send) {
      candidates = new int[] {R.string.SendSticker, R.string.PasteCustomEmoji, R.string.ShareCustomEmoji};
    } else if (viewId == R.id.btn_setEmojiStatus) {
      candidates = new int[] {R.string.SetEmojiAsStatus};
    } else if (viewId == R.id.btn_setEmojiStatusTimed) {
      candidates = new int[] {R.string.SetEmojiAsStatusTimed};
    } else {
      return title;
    }
    for (int res : candidates) {
      final String s = Lang.getString(res);
      if (title.equals(s) || title.equals(Lang.uppercase(s))) {
        return sentence(res);
      }
    }
    return title;
  }

  // Floating container (inline results, sticker suggestions)

  /** Distance of the container from the pane edges and from the composer (dp). */
  public static final float MENU_MARGIN = 8f;
  /** The container's corners: shape.corner.large. */
  public static final float MENU_CORNER = XgramExpressive.CORNER_L;
  /** Elevation level 2. */
  public static final float MENU_ELEVATION = XgramExpressive.ELEVATION_2;

  /** The container's colour: surfaceContainer, the chip step with Pure black (see XgramAmoled.floatingRole). */
  @ColorInt
  public static int menuColor () {
    return XgramExpressive.color(XgramAmoled.floatingRole(XgramExpressive.SURFACE_CONTAINER));
  }

  /**
   * Makes {@code v} the floating container: a {@link XgramExpressive.MorphDrawable} in the floating surfaceContainer
   * role with 16dp corners (no press morph) as its background, its content clipped to it, elevation level 2.
   */
  public static XgramExpressive.MorphDrawable applyMenuSurface (@NonNull View v) {
    XgramExpressive.MorphDrawable background = new XgramExpressive.MorphDrawable(0, MENU_CORNER, Float.NaN);
    background.setFloatingColorRole(XgramExpressive.SURFACE_CONTAINER);
    background.setHost(v);
    v.setBackground(background);
    v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    v.setClipToOutline(true);
    v.setElevation(Screen.dpf(MENU_ELEVATION));
    return background;
  }

  /**
   * The room left ({@code out[0]}) and right ({@code out[1]}) of the chat pane in the root view that holds
   * {@code view}: with two panes, or one pane next to the navigation rail, the composer's overlays stay over the chat
   * pane. Both 0 otherwise. Allocates nothing.
   */
  public static void paneInsets (@NonNull View view, @NonNull int[] out) {
    int left = 0, right = 0;
    final RootFrameLayout root = Views.findAncestor(view, RootFrameLayout.class, true);
    if (root != null && root.getMeasuredWidth() > 0) {
      try {
        final BaseActivity context = UI.getContext(view.getContext());
        final com.fahadali.xgram.layout.XgramTwoPane twoPane = context != null ? context.xgramTwoPane() : null;
        final NavigationController navigation = context != null ? context.navigation() : null;
        final View pane = navigation != null ? navigation.getValue() : null;
        if (twoPane != null && (twoPane.isActive() || twoPane.isRailShown()) && pane != null && pane.getWidth() > 0 && pane.getParent() instanceof View) {
          // Views.getLocationInWindow shares one array: each value is copied right away
          final int containerX = Views.getLocationInWindow((View) pane.getParent())[0];
          final int rootX = Views.getLocationInWindow(root)[0];
          final int paneLeft = containerX - rootX + pane.getLeft();
          left = Math.max(0, paneLeft);
          right = Math.max(0, root.getMeasuredWidth() - (paneLeft + pane.getWidth()));
        }
      } catch (Throwable ignored) { }
    }
    out[0] = left;
    out[1] = right;
  }

  // Grid tiles inside the container

  /** Gap between tiles (dp). */
  public static final float TILE_GAP = 2f;
  /** Resting and pressed tile corners: shape.corner.small, morphing to medium. */
  public static final float TILE_CORNER = XgramExpressive.CORNER_S, TILE_PRESSED_CORNER = XgramExpressive.CORNER_M;
  /** Height of the switch-PM row: a 40dp tonal button with 8dp above and below. */
  public static final float BUTTON_ROW_HEIGHT = 56f;

  /**
   * A media tile (GIF, photo): its background is a surfaceContainerHighest placeholder with 8dp corners that spring
   * to 12dp while pressed, and the media is clipped to that shape. The tiles that touch the container's corners are
   * cut to its 16dp corners by the container.
   */
  public static XgramExpressive.MorphDrawable applyTile (@NonNull View v) {
    XgramExpressive.MorphDrawable tile = new XgramExpressive.MorphDrawable(0, TILE_CORNER, TILE_PRESSED_CORNER);
    tile.setColorRole(XgramExpressive.SURFACE_CONTAINER_HIGHEST);
    tile.setHost(v);
    v.setBackground(tile);
    v.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    v.setClipToOutline(true);
    return tile;
  }

  /**
   * The press state of a sticker cell (the long-press preview, the grids of the sticker sheets and the suggestion
   * strip): a surfaceContainerHighest tile with 12dp corners that grows in behind the shrinking sticker as
   * {@code factor} goes from 0 to 1.
   */
  public static void drawStickerPressTile (@NonNull Canvas c, int width, int height, float factor) {
    final float f = Math.max(0f, Math.min(1f, factor));
    if (f <= 0f || width <= 0 || height <= 0) {
      return;
    }
    final float inset = Screen.dpf(4f) * (1f - f);
    tileRect.set(inset, inset, width - inset, height - inset);
    final float radius = Math.min(Screen.dpf(TILE_PRESSED_CORNER), Math.min(tileRect.width(), tileRect.height()) / 2f);
    tilePaint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SURFACE_CONTAINER_HIGHEST), f));
    c.drawRoundRect(tileRect, radius, radius, tilePaint);
  }

  private static final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private static final RectF tileRect = new RectF();

  // Sticker preview: the floating toolbar

  public static final float TOOLBAR_HEIGHT = 64f, TOOLBAR_PADDING = 8f, TOOLBAR_BUTTON = 48f;
  /** Distance of the toolbar from the sticker above it (dp). */
  public static final float TOOLBAR_GAP = 16f;

  /** A toolbar button's colours and tooltip, kept in the button's tag. */
  private static final class ToolbarItem {
    final int contentRole;
    final boolean toggled;
    final @Nullable String tooltip;
    final int tooltipWidth;
    final HoverDrawable hover;
    @ColorInt int appliedColor;

    ToolbarItem (int contentRole, boolean toggled, @Nullable String tooltip, HoverDrawable hover) {
      this.contentRole = contentRole;
      this.toggled = toggled;
      this.tooltip = tooltip;
      this.tooltipWidth = tooltip != null ? XgramM3Preview.measureTooltip(tooltip) : 0;
      this.hover = hover;
    }
  }

  /**
   * The slot behind a toolbar button: a secondaryContainer pill that scales and fades in while the button is under
   * the finger (SPATIAL_FAST); a toggled button (favourite) keeps the pill and gets an onSecondaryContainer state
   * layer instead.
   */
  private static final class HoverDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean toggled;
    private final XgramExpressive.Spring spring;
    private @Nullable Toolbar toolbar;
    private boolean pressed;

    HoverDrawable (boolean toggled) {
      this.toggled = toggled;
      this.spring = new XgramExpressive.Spring(XgramExpressive.SPATIAL_FAST, 0f, (s, value) -> {
        invalidateSelf();
        if (toolbar != null) {
          toolbar.onHoverChanged();
        }
      }).setThreshold(.005f);
    }

    float factor () {
      return spring.getValue();
    }

    @Override
    public boolean isStateful () {
      return true;
    }

    @Override
    protected boolean onStateChange (@NonNull int[] state) {
      boolean isPressed = false;
      for (int s : state) {
        if (s == android.R.attr.state_pressed) {
          isPressed = true;
          break;
        }
      }
      if (this.pressed != isPressed) {
        this.pressed = isPressed;
        spring.animateTo(isPressed ? 1f : 0f);
        return true;
      }
      return false;
    }

    @Override
    public void jumpToCurrentState () {
      super.jumpToCurrentState();
      spring.setValue(pressed ? 1f : 0f);
      invalidateSelf();
    }

    @Override
    public void draw (@NonNull Canvas c) {
      final Rect b = getBounds();
      if (b.isEmpty()) {
        return;
      }
      final float f = spring.getValue();
      if (toggled) {
        rect.set(b);
        float radius = Math.min(rect.width(), rect.height()) / 2f;
        paint.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
        c.drawRoundRect(rect, radius, radius, paint);
        if (f > 0f) {
          paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.ON_SECONDARY_CONTAINER), XgramExpressive.STATE_PRESSED * Math.min(1f, f)));
          c.drawRoundRect(rect, radius, radius, paint);
        }
        return;
      }
      if (f <= 0f) {
        return;
      }
      final float scale = .7f + .3f * f;
      final float halfWidth = b.width() / 2f * scale, halfHeight = b.height() / 2f * scale;
      rect.set(b.exactCenterX() - halfWidth, b.exactCenterY() - halfHeight, b.exactCenterX() + halfWidth, b.exactCenterY() + halfHeight);
      final float radius = Math.min(rect.width(), rect.height()) / 2f;
      paint.setColor(XgramExpressive.withAlpha(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), Math.min(1f, f)));
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /**
   * The sticker preview's floating toolbar (M3E FloatingToolbar): a full-corner pill in the floating
   * surfaceContainerHigh role, 64dp tall with 8dp padding and a level 3 shadow, holding 48dp icon buttons (with M3
   * plain tooltips, see {@link #drawTooltips}) or labelLarge text buttons.
   */
  public static final class Toolbar extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final @Nullable View tooltipHost;

    /** {@code tooltipHost}: the view that draws the tooltips over the toolbar (it is redrawn while one changes). */
    public Toolbar (Context context, @Nullable View tooltipHost) {
      super(context);
      this.tooltipHost = tooltipHost;
      setOrientation(LinearLayout.HORIZONTAL);
      setGravity(Gravity.CENTER_VERTICAL);
      final int padding = Screen.dp(TOOLBAR_PADDING);
      setPadding(padding, padding, padding, padding);
      setWillNotDraw(false);
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
        }
      });
      setElevation(Screen.dpf(XgramExpressive.ELEVATION_3));
    }

    void onHoverChanged () {
      if (tooltipHost != null) {
        tooltipHost.invalidate();
      }
    }

    /** A 48dp icon button in {@code contentRole} (ERROR for a destructive action); {@code toggled} fills its slot. */
    public ImageView addIconButton (@IdRes int id, @DrawableRes int iconRes, int contentRole, boolean toggled, @Nullable String tooltip, @Nullable View.OnClickListener onClick) {
      ImageView button = new ImageView(getContext());
      button.setId(id);
      button.setScaleType(ImageView.ScaleType.CENTER);
      button.setImageResource(XgramIconPack.map(iconRes));
      button.setContentDescription(tooltip);
      button.setOnClickListener(onClick);
      button.setClickable(true);
      HoverDrawable hover = new HoverDrawable(toggled);
      hover.toolbar = this;
      button.setBackground(hover);
      ToolbarItem item = new ToolbarItem(contentRole, toggled, tooltip, hover);
      button.setTag(item);
      applyItemColor(button, item);
      button.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(TOOLBAR_BUTTON), Screen.dp(TOOLBAR_BUTTON)));
      if (Lang.rtl()) {
        addView(button, 0);
      } else {
        addView(button);
      }
      return button;
    }

    /** A labelLarge text button, 48dp tall with 16dp sides, in {@code contentRole}. */
    public TextView addTextButton (@IdRes int id, @NonNull String label, int contentRole, @Nullable View.OnClickListener onClick, @Nullable View.OnLongClickListener onLongClick) {
      TextView button = new NoScrollTextView(getContext());
      button.setId(id);
      XgramExpressive.applyText(button, XgramExpressive.TS_LABEL_L, false);
      button.setGravity(Gravity.CENTER);
      button.setSingleLine(true);
      button.setEllipsize(TextUtils.TruncateAt.END);
      final int horizontal = Screen.dp(16f);
      button.setPadding(horizontal, 0, horizontal, 0);
      button.setMinWidth(Screen.dp(TOOLBAR_BUTTON));
      button.setText(label);
      button.setOnClickListener(onClick);
      if (onLongClick != null) {
        button.setOnLongClickListener(onLongClick);
      }
      button.setClickable(true);
      HoverDrawable hover = new HoverDrawable(false);
      hover.toolbar = this;
      button.setBackground(hover);
      ToolbarItem item = new ToolbarItem(contentRole, false, null, hover);
      button.setTag(item);
      applyItemColor(button, item);
      button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(TOOLBAR_BUTTON)));
      if (Lang.rtl()) {
        addView(button, 0);
      } else {
        addView(button);
      }
      return button;
    }

    private static void applyItemColor (View view, ToolbarItem item) {
      final int role = item.toggled ? XgramExpressive.PRIMARY : item.contentRole;
      final int color = XgramExpressive.color(role);
      if (item.appliedColor == color) {
        return;
      }
      item.appliedColor = color;
      if (view instanceof ImageView) {
        ((ImageView) view).setColorFilter(color);
      } else if (view instanceof TextView) {
        ((TextView) view).setTextColor(color);
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final float height = getHeight();
      rect.set(0, 0, getWidth(), height);
      paint.setColor(XgramExpressive.color(XgramAmoled.floatingRole(XgramExpressive.SURFACE_CONTAINER_HIGH)));
      c.drawRoundRect(rect, height / 2f, height / 2f, paint);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      // Theme changes: the content colours are read again, and set only when they changed
      for (int i = 0; i < getChildCount(); i++) {
        final View child = getChildAt(i);
        if (child.getTag() instanceof ToolbarItem) {
          applyItemColor(child, (ToolbarItem) child.getTag());
        }
      }
      super.dispatchDraw(c);
    }

    /**
     * Draws the plain tooltip of the button under the finger above the toolbar, in the coordinates of the toolbar's
     * parent ({@code parentWidth} wide): the tooltip keeps 8dp from the toolbar and from the edges.
     */
    public void drawTooltips (@NonNull Canvas c, int parentWidth) {
      final float alpha = getAlpha();
      if (alpha <= 0f || getVisibility() != View.VISIBLE) {
        return;
      }
      final float bottom = getY() - Screen.dp(8f);
      for (int i = 0; i < getChildCount(); i++) {
        final View child = getChildAt(i);
        final Object tag = child.getTag();
        if (!(tag instanceof ToolbarItem) || child.getVisibility() != View.VISIBLE) {
          continue;
        }
        final ToolbarItem item = (ToolbarItem) tag;
        if (item.tooltip == null) {
          continue;
        }
        final float f = item.hover.factor() * alpha;
        if (f <= 0f) {
          continue;
        }
        final float centerX = getX() + child.getLeft() + child.getWidth() / 2f;
        XgramM3Preview.drawTooltip(c, item.tooltip, item.tooltipWidth, centerX, bottom, parentWidth, f);
      }
    }
  }

  // Voice and video recording

  /**
   * The record overlay (it covers the composer while recording): the composer panel's surface with the field's own
   * shape on it, surfaceContainerHigh, 40dp tall with 20dp corners, from the field's start to the record button's
   * slot (see XgramM3Chat.fieldStart / fieldEnd). Colours are read at draw time.
   */
  public static final class RecordFieldDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    @Override
    public void draw (@NonNull Canvas c) {
      final Rect b = getBounds();
      paint.setColor(XgramExpressive.color(XgramExpressive.SURFACE));
      c.drawRect(b, paint);
      final boolean rtl = Lang.rtl();
      final float start = XgramM3Chat.fieldStart(), end = XgramM3Chat.fieldEnd(true);
      final float height = Math.min(b.height(), Screen.dpf(XgramM3Composer.FIELD_HEIGHT));
      final float top = b.exactCenterY() - height / 2f;
      rect.set(b.left + (rtl ? end : start), top, b.right - (rtl ? start : end), top + height);
      if (rect.width() <= 0f || rect.height() <= 0f) {
        return;
      }
      final float radius = Math.min(Screen.dpf(XgramExpressive.CORNER_L_INC), rect.height() / 2f);
      paint.setColor(XgramM3Composer.fieldColor(false));
      c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }

  /**
   * The vertical floating toolbar behind the round video controls (switch camera, view once): a full-corner pill in
   * the floating surfaceContainerHigh role with a level 3 shadow, around the rect set with {@link #setPill}. It fills
   * the record overlay's root and draws only the pill, so moving it costs no layout.
   */
  public static final class PillView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();

    public PillView (Context context) {
      super(context);
      setWillNotDraw(false);
      setClickable(false);
      setFocusable(false);
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      setOutlineProvider(new ViewOutlineProvider() {
        @Override
        public void getOutline (View view, Outline outline) {
          if (pill.isEmpty()) {
            outline.setEmpty();
          } else {
            outline.setRoundRect(Math.round(pill.left), Math.round(pill.top), Math.round(pill.right), Math.round(pill.bottom), pill.width() / 2f);
          }
        }
      });
      setElevation(Screen.dpf(XgramExpressive.ELEVATION_3));
    }

    /** The pill's bounds in this view (px); an empty rect hides it. */
    public void setPill (float left, float top, float right, float bottom) {
      if (pill.left != left || pill.top != top || pill.right != right || pill.bottom != bottom) {
        pill.set(left, top, right, bottom);
        invalidateOutline();
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (pill.isEmpty()) {
        return;
      }
      final float radius = Math.min(pill.width(), pill.height()) / 2f;
      paint.setColor(XgramExpressive.color(XgramAmoled.floatingRole(XgramExpressive.SURFACE_CONTAINER_HIGH)));
      c.drawRoundRect(pill, radius, radius, paint);
    }
  }

  /**
   * A 48dp filled tonal button of the round video controls: secondaryContainer, primary while selected ({@code active}
   * 0..1), full corners that spring to 8dp while pressed, the icon in the matching content colour. Returns the
   * container so the caller can update its colour with {@link #syncTonal}.
   */
  public static XgramExpressive.MorphDrawable applyTonal (@NonNull View v) {
    XgramExpressive.MorphDrawable container = XgramExpressive.attachPressMorph(v, XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER), XgramExpressive.CORNER_FULL, XgramExpressive.CORNER_S);
    container.setColor(XgramExpressive.color(XgramExpressive.SECONDARY_CONTAINER));
    container.setSize(TOOLBAR_BUTTON, TOOLBAR_BUTTON, Gravity.CENTER);
    return container;
  }

  /** The container colour for {@code active} (0..1): secondaryContainer to primary. Set only when it changed. */
  public static void syncTonal (@Nullable XgramExpressive.MorphDrawable container, float active) {
    if (container != null) {
      container.setColor(tonalColor(XgramExpressive.SECONDARY_CONTAINER, XgramExpressive.PRIMARY, active));
    }
  }

  /** The icon colour for {@code active} (0..1): onSecondaryContainer to onPrimary. */
  @ColorInt
  public static int tonalContentColor (float active) {
    return tonalColor(XgramExpressive.ON_SECONDARY_CONTAINER, XgramExpressive.ON_PRIMARY, active);
  }

  @ColorInt
  private static int tonalColor (int fromRole, int toRole, float active) {
    final float f = Math.max(0f, Math.min(1f, active));
    final int from = XgramExpressive.color(fromRole);
    if (f <= 0f) {
      return from;
    }
    return ColorUtils.fromToArgb(from, XgramExpressive.color(toRole), f);
  }

  /** The state layer colour of a tonal button: its content colour at the pressed opacity. */
  public static ColorStateList stateLayer (@ColorInt int contentColor) {
    return ColorStateList.valueOf(XgramExpressiveC.stateLayerColor(contentColor));
  }

  /** The round video ring's colour: primary, turning to error over the last seconds before the limit. */
  @ColorInt
  public static int recordRingColor (float progress) {
    final float f = Math.max(0f, Math.min(1f, (progress - RING_WARNING_FROM) / (RING_WARNING_TO - RING_WARNING_FROM)));
    final int primary = XgramExpressive.color(XgramExpressive.PRIMARY);
    if (f <= 0f) {
      return primary;
    }
    return ColorUtils.fromToArgb(primary, XgramExpressive.color(XgramExpressive.ERROR), f);
  }

  /** Progress at which the ring starts turning to the error colour, and where it is fully error (about 6 s to 3 s left of 59.5 s). */
  private static final float RING_WARNING_FROM = .9f, RING_WARNING_TO = .95f;
}
