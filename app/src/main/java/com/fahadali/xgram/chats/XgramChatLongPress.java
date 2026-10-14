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
 * Settings → XGram → Chats → "Long press on a chat": Telegram X's preview, a small pill of quick actions
 * next to the row, or the same preview in a smaller centred card.
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

    QuickActionsView view = new QuickActionsView(v.getContext(), v, context, listener, items, x, y);
    PopupLayout popup = new PopupLayout(v.getContext());
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
      pill.measure(MeasureSpec.makeMeasureSpec(Screen.dp(PADDING) * 2 + Screen.dp(BUTTON) * items.size(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(HEIGHT), MeasureSpec.EXACTLY));
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
      int top = row.top - Screen.dp(6f) - ph;
      pillBelow = top < topLimit;
      if (pillBelow) {
        top = Math.max(topLimit, Math.min(row.bottom + Screen.dp(6f), height - ph - margin));
      }
      pill.layout(left, top, left + pw, top + ph);
      pill.setPivotX(Math.max(0, Math.min(pw, cx - left)));
      pill.setPivotY(pillBelow ? 0 : ph);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      int width = getMeasuredWidth(), height = getMeasuredHeight();
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
      int index = (int) ((x - pill.getLeft() - Screen.dp(PADDING)) / Screen.dp(BUTTON));
      return Math.max(0, Math.min(items.size() - 1, index));
    }

    private void setHighlighted (int index) {
      if (highlighted != index) {
        highlighted = index;
        if (index != -1) {
          UI.forceVibrate(pill, false);
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
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
    }

    @Override
    public void launchShowAnimation (PopupLayout popup) {
      animator.animateTo(1f);
    }

    @Override
    public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
      closing = true;
      tracking = false;
      if (actionId != 0) {
        listener.onForceTouchAction(context, actionId, null);
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
      PillView (Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline (View view, Outline outline) {
              outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
          });
          setElevation(Screen.dp(6f));
        }
      }

      @Override
      protected void onDraw (Canvas c) {
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
