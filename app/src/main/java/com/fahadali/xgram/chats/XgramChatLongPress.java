package com.fahadali.xgram.chats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramIconPack;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntList;

/**
 * Settings → XGram → Chat list → "Long press on a chat": Telegram X's preview, a small pill of quick actions
 * next to the row, or the same preview in a smaller centred card. All three start from Telegram X's force touch, so
 * they need its Interface › Chat Previews (XgramSettingDeps.chatLongPressReason).
 */
public final class XgramChatLongPress {
  public static final int MODE_PREVIEW = 0, MODE_QUICK = 1, MODE_COMPACT = 2;

  private XgramChatLongPress () { }

  public static int mode () {
    return XgramSettings.instance().chatLongPress();
  }

  public static boolean isCompact () {
    return mode() == MODE_COMPACT;
  }

  public static String modeName (int mode) {
    switch (mode) {
      case MODE_QUICK: return Lang.getString(R.string.XgramChatLongPressQuick);
      case MODE_COMPACT: return Lang.getString(R.string.XgramChatLongPressCompact);
      default: return Lang.getString(R.string.XgramChatLongPressPreview);
    }
  }

  public static void showModePicker (ViewController<?> c, @Nullable Runnable after) {
    int mode = mode();
    c.showSettings(new SettingsWrapBuilder(R.id.btn_xgramChatLongPress).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramChatLongPressPreview, 0, R.string.XgramChatLongPressPreview, R.id.btn_xgramChatLongPress, mode == MODE_PREVIEW),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramChatLongPressQuick, 0, R.string.XgramChatLongPressQuick, R.id.btn_xgramChatLongPress, mode == MODE_QUICK),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramChatLongPressCompact, 0, R.string.XgramChatLongPressCompact, R.id.btn_xgramChatLongPress, mode == MODE_COMPACT)
    }).setIntDelegate((id, result) -> {
      int picked = result.get(R.id.btn_xgramChatLongPress);
      int newMode = picked == R.id.btn_xgramChatLongPressQuick ? MODE_QUICK : picked == R.id.btn_xgramChatLongPressCompact ? MODE_COMPACT : MODE_PREVIEW;
      XgramSettings.instance().setChatLongPress(newMode);
      if (after != null) {
        after.run();
      }
    }));
  }

  // Compact preview: ForceTouchView's own card, only smaller and centred.

  public static int compactVerticalMargin () {
    int height = Screen.currentHeight();
    int card = Math.max(Math.round(height * .6f), Math.min(height - Screen.dp(48f) * 2, Screen.dp(320f)));
    return Math.max(HeaderView.getTopOffset() + Screen.dp(12f), (height - card) / 2);
  }

  public static int compactHorizontalMargin () {
    int width = Screen.currentWidth();
    int card = Math.min(width - Screen.dp(32f) * 2, Screen.dp(480f));
    return Math.max(Screen.dp(16f), (width - card) / 2);
  }

  // Quick actions

  private static QuickActionsView current;

  /** Called instead of opening the chat preview. Returns false when the preview should open as usual. */
  public static boolean showQuickActions (BaseView v, @Nullable BaseView.ActionListProvider provider, float x, float y) {
    if (mode() != MODE_QUICK || current != null) {
      return false;
    }
    Tdlib tdlib = v.tdlib();
    long chatId = v.getPreviewChatId();
    if (tdlib == null || chatId == 0 || v.getPreviewFilter() instanceof TdApi.SearchMessagesFilterPinned) {
      return false;
    }
    ViewController<?> ancestor = ViewController.findAncestor(v);
    if (ancestor == null || ancestor.tdlib() == null || ancestor.tdlib().id() != tdlib.id()) {
      return false;
    }
    ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, v, new View(v.getContext()), null);
    ArrayList<BaseView.ActionItem> items = new ArrayList<>();
    ForceTouchView.ActionListener listener;
    if (provider instanceof ChatsController || provider instanceof XgramCommunityController) {
      listener = provider.onCreateActions(v, context, items, null);
    } else if (provider == null && (ancestor instanceof ChatsController || ancestor instanceof MainController)) {
      IntList ids = new IntList(5);
      IntList icons = new IntList(5);
      StringList strings = new StringList(5);
      listener = tdlib.ui().createSimpleChatActions(ancestor, v.getPreviewChatList(), chatId, null, new TdApi.MessageSourceOther(), ids, icons, strings, true, false, false, null);
      for (int i = 0; i < ids.size(); i++) {
        items.add(new BaseView.ActionItem(ids.get(i), icons.get(i), strings.get()[i]));
      }
    } else {
      return false;
    }
    if (listener == null || items.isEmpty()) {
      return false;
    }
    for (BaseView.ActionItem item : items) {
      if (item.iconRes == 0) {
        return false;
      }
    }

    QuickActionsView view = new QuickActionsView(org.thunderdog.challegram.tool.UI.getContext(v.getContext()), v, context, listener, items, x, y);
    // The row's context can be a theme wrapper (community pages); popups need the activity.
    PopupLayout popup = new PopupLayout(org.thunderdog.challegram.tool.UI.getContext(v.getContext()));
    popup.setOverlayStatusBar(true);
    popup.init(true);
    popup.setNeedFullScreen(Settings.instance().useEdgeToEdge());
    view.popup = popup;
    popup.setDismissListener(p -> {
      if (current == view) {
        current = null;
      }
    });
    current = view;
    popup.showAnimatedPopupView(view, view);
    return true;
  }

  /** The finger that opened the pill is still down and moving (slide onto an icon, release to act). */
  public static void onDragMove (BaseView v, float x, float y) {
    QuickActionsView view = current;
    if (view != null && view.source == v) {
      int[] location = Views.getLocationOnScreen(v);
      view.onDrag(location[0] + x, location[1] + y);
    }
  }

  public static void onDragEnd (BaseView v, boolean cancelled) {
    QuickActionsView view = current;
    if (view != null && view.source == v) {
      view.onDragEnd(cancelled);
    }
  }

  private static final class QuickActionsView extends ViewGroup implements PopupLayout.AnimatedPopupProvider, FactorAnimator.Target {
    private final BaseView source;
    private final ForceTouchView.ForceTouchContext context;
    private final ForceTouchView.ActionListener listener;
    private final ArrayList<BaseView.ActionItem> items;
    private final Drawable[] icons;
    private final PillView pill;
    private final int rowLeft, rowTop, rowRight, rowBottom, startX, startY;
    private final android.graphics.Rect row = new android.graphics.Rect();
    private PopupLayout popup;

    private FactorAnimator animator;
    private float factor;
    private boolean closing, pillBelow, tracking, dragMoved;
    private int highlighted = -1;
    private int actionId;

    // XGram M3E: the floating toolbar of the chat preview (XgramM3Preview): 64dp, 48dp slots, the slot under the finger
    // on a secondaryContainer pill that springs from slot to slot, a plain tooltip, a 32% scrim; it opens with a
    // SPATIAL_FAST scale from .8 about the touch point and an EFFECTS_FAST fade (only a fade with Reduce motion)
    private final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
    private @Nullable com.fahadali.xgram.appearance.XgramExpressive.Spring xgramAlpha, xgramScale, xgramHoverX, xgramHover;
    private @Nullable String xgramTooltip;
    private int xgramTooltipWidth;

    private int padding () {
      return Screen.dp(xgram ? com.fahadali.xgram.appearance.XgramM3Preview.BAR_PADDING : PADDING);
    }

    private int button () {
      return Screen.dp(BUTTON);
    }

    private int pillHeight () {
      return Screen.dp(xgram ? com.fahadali.xgram.appearance.XgramM3Preview.BAR_HEIGHT : HEIGHT);
    }

    private float xgramHoverFactor () {
      return xgramHover != null ? Math.max(0f, Math.min(1.2f, xgramHover.getValue())) : 0f;
    }

    private void xgramApply () {
      final float alpha = xgramAlpha != null ? Math.max(0f, Math.min(1f, xgramAlpha.getValue())) : 0f;
      final float scale = xgramScale != null ? xgramScale.getValue() : 1f;
      this.factor = alpha;
      pill.setScaleX(scale);
      pill.setScaleY(scale);
      pill.setAlpha(alpha);
      invalidate();
    }

    private void xgramOnSettled () {
      if (xgramAlpha == null) {
        return;
      }
      if (xgramAlpha.getTarget() >= 1f) {
        if (popup != null) {
          popup.onCustomShowComplete();
        }
      } else if (closing) {
        if (popup != null) {
          popup.onCustomHideAnimationComplete();
        }
        if (actionId != 0) {
          listener.onAfterForceTouchAction(context, actionId, null);
        }
      }
    }

    private void xgramSetHover (int index) {
      if (xgramHover == null) {
        com.fahadali.xgram.appearance.XgramExpressive.Spring.Listener redraw = (s, value) -> {
          pill.invalidate();
          invalidate();
        };
        xgramHover = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, 0f, redraw);
        xgramHoverX = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, Math.max(0, index), redraw).setThreshold(.002f);
      }
      if (index != -1 && xgramHoverX != null) {
        if (xgramHover.getValue() <= .01f && !xgramHover.isRunning()) {
          xgramHoverX.setValue(index); // a new press starts on its slot
        } else {
          xgramHoverX.animateTo(index);
        }
        String text = items.get(itemAt(index)).title;
        xgramTooltip = text != null && !text.isEmpty() ? text : null;
        xgramTooltipWidth = xgramTooltip != null ? com.fahadali.xgram.appearance.XgramM3Preview.measureTooltip(xgramTooltip) : 0;
      }
      xgramHover.animateTo(index != -1 ? 1f : 0f);
    }

    QuickActionsView (Context context, BaseView source, ForceTouchView.ForceTouchContext forceTouchContext, ForceTouchView.ActionListener listener, ArrayList<BaseView.ActionItem> items, float x, float y) {
      super(context);
      this.source = source;
      this.context = forceTouchContext;
      this.listener = listener;
      this.items = items;
      this.icons = new Drawable[items.size()];
      for (int i = 0; i < icons.length; i++) {
        icons[i] = Drawables.get(getResources(), XgramIconPack.map(items.get(i).iconRes));
      }
      int[] location = Views.getLocationOnScreen(source);
      rowLeft = location[0];
      rowTop = location[1];
      rowRight = location[0] + source.getWidth();
      rowBottom = location[1] + source.getHeight();
      startX = location[0] + Math.round(x);
      startY = location[1] + Math.round(y);
      setWillNotDraw(false);
      pill = new PillView(context);
      pill.setAlpha(0f);
      addView(pill);
    }

    private int itemAt (int displayIndex) {
      return Lang.rtl() ? items.size() - 1 - displayIndex : displayIndex;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      pill.measure(MeasureSpec.makeMeasureSpec(padding() * 2 + button() * items.size(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(pillHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout (boolean changed, int l, int t, int r, int b) {
      int[] own = Views.getLocationOnScreen(this);
      row.set(rowLeft - own[0], rowTop - own[1], rowRight - own[0], rowBottom - own[1]);
      int width = r - l, height = b - t;
      int pw = pill.getMeasuredWidth(), ph = pill.getMeasuredHeight();
      int margin = Screen.dp(8f);
      int cx = startX - own[0];
      int minLeft = Math.max(margin, row.left + margin);
      int maxRight = Math.min(width - margin, row.right - margin);
      if (maxRight - minLeft < pw) {
        minLeft = margin;
        maxRight = width - margin;
      }
      int left = Math.max(minLeft, Math.min(cx - pw / 2, maxRight - pw));
      int topLimit = Math.max(margin, HeaderView.getTopOffset() - own[1] + margin);
      final int anchorGap = Screen.dp(xgram ? 8f : 6f); // XGram M3E: 8dp from the anchor
      int top = row.top - anchorGap - ph;
      pillBelow = top < topLimit;
      if (pillBelow) {
        top = Math.max(topLimit, Math.min(row.bottom + anchorGap, height - ph - margin));
      }
      pill.layout(left, top, left + pw, top + ph);
      pill.setPivotX(Math.max(0, Math.min(pw, cx - left)));
      pill.setPivotY(pillBelow ? 0 : ph);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      int width = getMeasuredWidth(), height = getMeasuredHeight();
      if (xgram) {
        Paint scrim = Paints.fillingPaint(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramM3Preview.scrimColor(), factor));
        c.drawRect(0, 0, width, row.top, scrim);
        c.drawRect(0, row.bottom, width, height, scrim);
        c.drawRect(0, row.top, row.left, row.bottom, scrim);
        c.drawRect(row.right, row.top, width, row.bottom, scrim);
        super.dispatchDraw(c);
        final float hover = Math.min(1f, xgramHoverFactor());
        if (xgramTooltip != null && hover > 0f && factor > 0f && xgramHoverX != null) {
          final float slot = xgramHoverX.getValue();
          final float centerX = pill.getLeft() + padding() + button() * slot + button() / 2f;
          final float gap = Screen.dp(8f);
          final float bottom = pillBelow ? pill.getBottom() + gap + Screen.dp(24f) : pill.getTop() - gap;
          com.fahadali.xgram.appearance.XgramM3Preview.drawTooltip(c, xgramTooltip, xgramTooltipWidth, centerX, bottom, width, hover * factor);
        }
        return;
      }
      Paint dim = Paints.fillingPaint(ColorUtils.alphaColor(factor * (Theme.isDark() ? .45f : .22f), 0xff000000));
      c.drawRect(0, 0, width, row.top, dim);
      c.drawRect(0, row.bottom, width, height, dim);
      c.drawRect(0, row.top, row.left, row.bottom, dim);
      c.drawRect(row.right, row.top, width, row.bottom, dim);
      super.dispatchDraw(c);
      if (highlighted != -1 && factor > 0f) {
        String text = items.get(itemAt(highlighted)).title;
        if (text != null && !text.isEmpty()) {
          boolean fakeBold = Text.needFakeBold(text);
          android.text.TextPaint textPaint = Paints.getMediumTextPaint(12f, ColorUtils.alphaColor(factor, 0xffffffff), fakeBold);
          int textWidth = (int) U.measureText(text, textPaint);
          int paddingHorizontal = Screen.dp(8f), rectHeight = Screen.dp(28f), gap = Screen.dp(6f);
          float centerX = pill.getLeft() + Screen.dp(PADDING) + Screen.dp(BUTTON) * highlighted + Screen.dp(BUTTON) / 2f;
          float rectWidth = textWidth + paddingHorizontal * 2;
          float rectLeft = Math.max(Screen.dp(4f), Math.min(width - Screen.dp(4f) - rectWidth, centerX - rectWidth / 2f));
          float rectTop = pillBelow ? pill.getBottom() + gap : pill.getTop() - gap - rectHeight;
          RectF rect = Paints.getRectF();
          rect.set(rectLeft, rectTop, rectLeft + rectWidth, rectTop + rectHeight);
          c.drawRoundRect(rect, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(ColorUtils.alphaColor(factor, 0x9c000000)));
          c.drawText(text, rect.left + paddingHorizontal, rect.top + Screen.dp(18f), textPaint);
        }
      }
    }

    private int indexAt (float x, float y) {
      float slop = Screen.dp(12f);
      if (x < pill.getLeft() || x > pill.getRight() || y < pill.getTop() - slop || y > pill.getBottom() + slop) {
        return -1;
      }
      int index = (int) ((x - pill.getLeft() - padding()) / button());
      return Math.max(0, Math.min(items.size() - 1, index));
    }

    private void setHighlighted (int index) {
      if (highlighted != index) {
        highlighted = index;
        if (index != -1) {
          UI.forceVibrate(pill, false);
        }
        if (xgram) {
          xgramSetHover(index);
        }
        pill.invalidate();
        invalidate();
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      if (closing) {
        return true;
      }
      float x = e.getX(), y = e.getY();
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          int index = indexAt(x, y);
          if (index == -1) {
            close();
          } else {
            tracking = true;
            setHighlighted(index);
          }
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (tracking) {
            setHighlighted(indexAt(x, y));
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          if (tracking) {
            tracking = false;
            int index = indexAt(x, y);
            setHighlighted(-1);
            if (index != -1) {
              perform(index);
            }
          }
          return true;
        }
        case MotionEvent.ACTION_CANCEL: {
          tracking = false;
          setHighlighted(-1);
          return true;
        }
      }
      return true;
    }

    void onDrag (float screenX, float screenY) {
      if (closing) {
        return;
      }
      if (!dragMoved) {
        if (Math.max(Math.abs(screenX - startX), Math.abs(screenY - startY)) < Screen.getTouchSlop()) {
          return;
        }
        dragMoved = true;
      }
      int[] own = Views.getLocationOnScreen(this);
      setHighlighted(indexAt(screenX - own[0], screenY - own[1]));
    }

    void onDragEnd (boolean cancelled) {
      int index = highlighted;
      setHighlighted(-1);
      if (!cancelled && dragMoved && index != -1) {
        perform(index);
      }
    }

    private void perform (int displayIndex) {
      if (closing) {
        return;
      }
      actionId = items.get(itemAt(displayIndex)).id;
      popup.hideWindow(true);
    }

    private void close () {
      actionId = 0;
      popup.hideWindow(true);
    }

    // Animation

    @Override
    public void prepareShowAnimation () {
      if (xgram) {
        final float from = com.fahadali.xgram.appearance.XgramM3Preview.revealScale();
        xgramAlpha = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.EFFECTS_FAST, 0f, (s, value) -> xgramApply()).setEndListener(this::xgramOnSettled);
        xgramScale = new com.fahadali.xgram.appearance.XgramExpressive.Spring(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_FAST, from, (s, value) -> xgramApply()).setThreshold(.002f);
        pill.setScaleX(from);
        pill.setScaleY(from);
        return;
      }
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    }

    @Override
    public void launchShowAnimation (PopupLayout popup) {
      if (xgram && xgramAlpha != null && xgramScale != null) {
        xgramScale.animateTo(1f);
        xgramAlpha.animateTo(1f);
        return;
      }
      animator.animateTo(1f);
    }

    @Override
    public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
      closing = true;
      tracking = false;
      if (actionId != 0) {
        listener.onForceTouchAction(context, actionId, null);
      }
      if (xgram && xgramAlpha != null && xgramScale != null) {
        xgramScale.animateTo(com.fahadali.xgram.appearance.XgramM3Preview.revealScale());
        if (!xgramAlpha.isRunning() && xgramAlpha.getValue() == 0f) {
          // Idle at 0 (hidden before its show spring ran): animateTo would return without its end listener, so the
          // popup would never hear the hide finish and the static current would stay set
          xgramAlpha.setValue(0f); // the target too, so xgramOnSettled reads a hide
          xgramOnSettled();
        } else {
          xgramAlpha.animateTo(0f);
        }
        return true;
      }
      animator.setDuration(140L);
      animator.animateTo(0f);
      return true;
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      this.factor = factor;
      float scale = .82f + .18f * factor;
      pill.setScaleX(scale);
      pill.setScaleY(scale);
      pill.setAlpha(Math.max(0f, Math.min(1f, factor)));
      invalidate();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (finalFactor == 1f) {
        if (popup != null) {
          popup.onCustomShowComplete();
        }
      } else if (finalFactor == 0f && closing) {
        if (popup != null) {
          popup.onCustomHideAnimationComplete();
        }
        if (actionId != 0) {
          listener.onAfterForceTouchAction(context, actionId, null);
        }
      }
    }

    private static final float PADDING = 4f, BUTTON = 48f, HEIGHT = 52f;

    private final class PillView extends View {
      private final RectF xgramRect = new RectF();
      private final Paint xgramPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

      PillView (Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline (View view, Outline outline) {
              outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
          });
          setElevation(xgram ? com.fahadali.xgram.appearance.XgramM3Preview.cardElevation() : Screen.dp(6f));
        }
      }

      /** XGram M3E: the preview's floating toolbar look. */
      private void xgramDraw (Canvas c) {
        final int width = getWidth(), height = getHeight();
        xgramRect.set(0, 0, width, height);
        xgramPaint.setColor(com.fahadali.xgram.appearance.XgramM3Preview.cardColor());
        c.drawRoundRect(xgramRect, height / 2f, height / 2f, xgramPaint);
        final float cy = height / 2f;
        final int pad = padding(), slot = button();
        final float hover = xgramHoverFactor();
        if (hover > 0f && xgramHoverX != null) {
          final float cx = pad + slot * xgramHoverX.getValue() + slot / 2f;
          final float scale = .7f + .3f * hover;
          final float halfWidth = Math.max(0f, slot / 2f - Screen.dp(2f)) * scale;
          final float halfHeight = Screen.dp(com.fahadali.xgram.appearance.XgramM3Preview.BUTTON_HEIGHT) / 2f * scale;
          xgramRect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
          xgramPaint.setColor(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER), Math.min(1f, hover)));
          c.drawRoundRect(xgramRect, halfHeight, halfHeight, xgramPaint);
        }
        for (int i = 0; i < items.size(); i++) {
          final int itemIndex = itemAt(i);
          final Drawable icon = icons[itemIndex];
          if (icon == null) {
            continue;
          }
          final float cx = pad + slot * i + slot / 2f;
          final int iconColor = com.fahadali.xgram.appearance.XgramR5Calls.isDestructiveAction(items.get(itemIndex).id) ?
            com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ERROR) :
            com.fahadali.xgram.appearance.XgramM3Preview.iconColor(i == highlighted);
          final boolean mirror = Drawables.needMirror(items.get(itemIndex).iconRes);
          final int saveCount = c.save();
          if (mirror) {
            c.scale(-1f, 1f, cx, cy);
          }
          Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(iconColor));
          c.restoreToCount(saveCount);
        }
      }

      @Override
      protected void onDraw (Canvas c) {
        if (xgram) {
          xgramDraw(c);
          return;
        }
        int width = getWidth(), height = getHeight();
        RectF rect = Paints.getRectF();
        rect.set(0, 0, width, height);
        c.drawRoundRect(rect, height / 2f, height / 2f, Paints.fillingPaint(Theme.fillingColor()));
        float cy = height / 2f;
        for (int i = 0; i < items.size(); i++) {
          int itemIndex = itemAt(i);
          float cx = Screen.dp(PADDING) + Screen.dp(BUTTON) * i + Screen.dp(BUTTON) / 2f;
          if (i == highlighted) {
            c.drawCircle(cx, cy, Screen.dp(21f), Paints.fillingPaint(Theme.getColor(ColorId.fillingPressed)));
          }
          Drawable icon = icons[itemIndex];
          if (icon == null) {
            continue;
          }
          int iconColor = items.get(itemIndex).id == R.id.btn_removeChatFromListOrClearHistory ? Theme.getColor(ColorId.iconNegative) : Theme.iconColor();
          boolean mirror = Drawables.needMirror(items.get(itemIndex).iconRes);
          int saveCount = c.save();
          if (mirror) {
            c.scale(-1f, 1f, cx, cy);
          }
          Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2f, cy - icon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(iconColor));
          c.restoreToCount(saveCount);
        }
      }
    }
  }
}
