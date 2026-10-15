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
package com.fahadali.xgram.layout;

import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackPressMode;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MainController;

/**
 * Two-pane layout for wide screens (foldables unfolded, tablets): the chat list stays on the left and chats open on
 * the right, like official Telegram on tablets.
 *
 * Telegram X's own navigation stays untouched and becomes the right pane. A second chat list in its own
 * {@link NavigationController} is shown on the left, and while the right pane is at its root (Telegram X's chat list)
 * a "select a chat" placeholder covers it. Folding or unfolding only shows or hides the left pane.
 */
public final class XgramTwoPane implements NavigationStack.ChangeListener {
  /**
   * Smallest window (dp) that gets two panes. The height rule keeps one pane on a phone in landscape and on a Fold's
   * cover screen in landscape (both are 800 to 900dp wide but under 480dp high), where the list squashed the chat.
   */
  private static final int MIN_WIDTH_DP = 600, MIN_HEIGHT_DP = 480;
  /** Wide enough for two panes whatever the height (desktop windows). */
  private static final int ANY_HEIGHT_WIDTH_DP = 1200;
  /**
   * Two panes stay on until the window is this much below the line, so dragging a window (DeX, freeform) across
   * it doesn't tear the chat list down and rebuild it on every frame.
   */
  private static final int HYSTERESIS_DP = 20;
  /** Book posture: the hinge only becomes the divider when it leaves both panes at least this wide (dp). */
  private static final int HINGE_MIN_LIST_DP = 260, HINGE_MIN_CHAT_DP = 300;

  private final BaseActivity context;
  private final NavigationController navigation;
  private final ViewGroup container;

  @Nullable private NavigationController leftNavigation;
  @Nullable private MainController leftMain;
  @Nullable private View divider, placeholder;
  /** The placeholder again, behind the right pane: what shows through while the hidden chat list is clipped. */
  @Nullable private View underlay;
  /**
   * The chat list at the root of the right pane (Telegram X's own) is clipped to nothing while two panes are shown,
   * so it never appears while Telegram X animates it (swipe back, opening a chat), when the overlay can't be shown.
   * Telegram X itself changes its visibility, alpha and translation during those animations, but not its clip.
   */
  @Nullable private View hiddenRootView, hiddenHeaderCell;
  private boolean isActive;
  private int leftWidth;

  public XgramTwoPane (BaseActivity context, NavigationController navigation, ViewGroup container) {
    this.context = context;
    this.navigation = navigation;
    this.container = container;
    navigation.getStack().addChangeListener(this);
    org.thunderdog.challegram.theme.ThemeManager.instance().addThemeListener(themeListener);
    XgramWindow window = context.xgramWindow();
    if (window != null) {
      window.addListener(windowListener);
    }
  }

  // Folding, unfolding, resizing and book posture: re-check the panes and where they split.
  private final XgramWindow.Listener windowListener = (window, changes) -> {
    if ((changes & (XgramWindow.CHANGE_SIZE | XgramWindow.CHANGE_POSTURE | XgramWindow.CHANGE_MULTI_WINDOW)) != 0) {
      update();
    }
  };

  // The placeholder, its header strip and the divider take their colours when created: rebuild them when the
  // theme changes (e.g. Material You night), or they stay in the old theme.
  private final org.thunderdog.challegram.theme.ThemeChangeListener themeListener = new org.thunderdog.challegram.theme.ThemeChangeListener() {
    @Override
    public boolean needsTempUpdates () {
      return false;
    }

    @Override
    public void onThemeColorsChanged (boolean areTemp, @Nullable org.thunderdog.challegram.theme.ColorState state) {
      if (!areTemp) {
        XgramTwoPane.this.onThemeChanged();
      }
    }

    @Override
    public void onThemeChanged (org.thunderdog.challegram.theme.ThemeDelegate fromTheme, org.thunderdog.challegram.theme.ThemeDelegate toTheme) {
      XgramTwoPane.this.onThemeChanged();
    }
  };

  public boolean isActive () {
    return isActive;
  }

  public static boolean isWideEnough (BaseActivity context) {
    return isWideEnough(context, false);
  }

  /** @param isActive two panes are shown now: they stay a little below the line (see {@link #HYSTERESIS_DP}) */
  private static boolean isWideEnough (BaseActivity context, boolean isActive) {
    int width, height;
    XgramWindow window = context.xgramWindow();
    if (window != null && window.widthDp() > 0 && window.heightDp() > 0) {
      width = window.widthDp();
      height = window.heightDp();
    } else {
      Configuration configuration = context.getResources().getConfiguration();
      width = configuration.screenWidthDp;
      height = configuration.screenHeightDp;
    }
    int slack = isActive ? HYSTERESIS_DP : 0;
    if (width >= ANY_HEIGHT_WIDTH_DP - slack) {
      return true;
    }
    return width >= MIN_WIDTH_DP - slack && height >= MIN_HEIGHT_DP - slack;
  }

  @Nullable
  private MainController mainRoot () {
    NavigationStack stack = navigation.getStack();
    ViewController<?> root = stack.size() > 0 ? stack.get(0) : null;
    return root instanceof MainController ? (MainController) root : null;
  }

  /**
   * The right pane's own chat list, kept under the placeholder. It shows no ☰ and no menu: otherwise they slide
   * into the header for a moment whenever a chat on the right is opened or swiped away.
   */
  public boolean isHiddenRoot (@Nullable ViewController<?> c) {
    return isActive && c != null && c != leftMain && c == mainRoot();
  }

  @Nullable private MainController headerRoot;
  private boolean headerRootHidden;

  /**
   * Re-applies the root's header when it starts or stops being hidden while it is on screen. When a chat is on top,
   * the header picks it up by itself on the way back.
   */
  private void syncRootHeader () {
    MainController root = mainRoot();
    boolean hidden = isHiddenRoot(root);
    if (root == headerRoot && hidden == headerRootHidden) {
      return;
    }
    if (root != null && navigation.getCurrentStackItem() == root) {
      if (navigation.isAnimating()) {
        return; // the next stack change tries again
      }
      org.thunderdog.challegram.navigation.HeaderView header = navigation.getHeaderView();
      if (header != null) {
        header.setTitle(root);
      }
    }
    headerRoot = root;
    headerRootHidden = hidden;
  }

  /** Re-evaluates the layout: setting, window width and whether the app is past login. */
  public void update () {
    MainController root = mainRoot();
    boolean isAllowed = root != null && XgramSettings.instance().twoPaneLayout() && !navigation.isDestroyed();
    boolean needActive = isAllowed && isWideEnough(context, isActive);
    try {
      if (needActive) {
        if (!isActive || leftMain == null || leftMain.tdlib() != root.tdlib()) {
          activate(root.tdlib());
        } else {
          leftWidth = computeLeftWidth(); // the pane settings may have changed
          layout();
        }
        hideRoot(root);
      } else if (isActive) {
        // Folded or too small: the chat list is kept (scroll position, folder, open archive) for the next unfold.
        // Setting off, logged out: it goes.
        deactivate(isAllowed);
      } else if (leftNavigation != null && (!isAllowed || leftMain == null || leftMain.tdlib() != root.tdlib())) {
        removeLeft();
      }
    } catch (Throwable t) {
      // Never take the app down for a layout feature: fall back to Telegram X's single pane.
      Log.e("XGram: two-pane layout failed", t);
      android.util.Log.e("XGram", "two-pane layout failed", t);
      try {
        deactivate(false);
      } catch (Throwable ignored) { }
    }
    updatePlaceholder();
    updateHighlight();
    syncRootHeader();
  }

  // The chat open on the right is highlighted in the left chat list, as on tablets.

  private static volatile long highlightedChatId;
  private static volatile int highlightedAccountId = -1;

  /** Draw path of chat list rows: one comparison when nothing is highlighted. */
  public static boolean isHighlighted (Tdlib tdlib, long chatId) {
    return chatId != 0 && chatId == highlightedChatId && tdlib != null && tdlib.id() == highlightedAccountId;
  }

  private void updateHighlight () {
    long chatId = 0;
    int accountId = -1;
    if (isActive) {
      NavigationStack stack = navigation.getStack();
      for (int i = stack.size() - 1; i >= 1; i--) {
        ViewController<?> c = stack.get(i);
        long id = c != null ? c.getChatId() : 0;
        if (id != 0) {
          chatId = id;
          accountId = c.tdlib() != null ? c.tdlib().id() : -1;
          break;
        }
      }
    }
    if (chatId != highlightedChatId || accountId != highlightedAccountId) {
      highlightedChatId = chatId;
      highlightedAccountId = accountId;
      if (leftNavigation != null) {
        invalidateChatViews(leftNavigation.getValue());
      }
    }
  }

  private static void invalidateChatViews (@Nullable View view) {
    if (view instanceof org.thunderdog.challegram.component.dialogs.ChatView) {
      view.invalidate();
    } else if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        invalidateChatViews(group.getChildAt(i));
      }
    }
  }

  /** The chat list pane width: the one last set with the handle, or 38% of the screen within 320 to 420dp. */
  private int computeLeftWidth () {
    XgramSettings settings = XgramSettings.instance();
    int saved = settings.paneResizable() ? settings.paneListWidthDp() : 0;
    if (saved > 0) {
      return Screen.dp(saved);
    }
    int width = context.getResources().getConfiguration().screenWidthDp;
    int dp = Math.max(320, Math.min(420, Math.round(width * .38f)));
    return Screen.dp(dp);
  }

  /**
   * The chat list's own pane, while two panes are shown or kept for the next unfold: it keeps clear of the screen
   * edge and the divider (see MainController#xgramUpdatePaneInset).
   */
  public static boolean isPaneChatList (@Nullable ViewController<?> c) {
    if (c == null) {
      return false;
    }
    XgramTwoPane twoPane = c.context().xgramTwoPane();
    return twoPane != null && twoPane.leftMain != null && twoPane.leftMain == c;
  }

  /** Space under the last chat of a chat list page in the narrow pane, so the compose button doesn't cover it. */
  public static int fabSpace (@Nullable ViewController<?> c) {
    return c != null && isPaneChatList(c.getParentOrSelf()) ? Screen.dp(56f + 16f + 8f) : 0;
  }

  private void activate (Tdlib tdlib) {
    // The chat list kept from the last fold comes back as it was, if it is still the same account's.
    boolean reuse = leftNavigation != null && leftMain != null && leftMain.tdlib() == tdlib && !leftNavigation.isDestroyed() && !leftMain.isDestroyed();
    if (reuse) {
      detachLeft();
    } else if (leftNavigation != null) {
      removeLeft();
    }
    leftWidth = computeLeftWidth();

    if (!reuse) {
      leftNavigation = new NavigationController(context);
      leftNavigation.getStack().addChangeListener(stack -> context.notifyBackPressAvailabilityChanged());
      leftMain = new MainController(context, tdlib);
    }
    View leftView = leftNavigation.getValue();
    leftView.setLayoutParams(new FrameLayout.LayoutParams(leftWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    // Behind the main navigation and the drawer, after the focus view.
    container.addView(leftView, Math.min(1, container.getChildCount()));
    if (!reuse) {
      leftNavigation.initController(leftMain);
    }

    divider = new DividerView(context);
    divider.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1, Screen.dp(.5f)), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    container.addView(divider, container.indexOfChild(leftView) + 1);

    placeholder = createPlaceholder();
    container.addView(placeholder, container.indexOfChild(navigation.getValue()) + 1);
    underlay = createPlaceholder();
    container.addView(underlay, container.indexOfChild(navigation.getValue()));

    // Over both panes (and the placeholder), so it gets touches before them.
    handle = new HandleView(context);
    handle.setLayoutParams(new FrameLayout.LayoutParams(Screen.dp(HANDLE_TOUCH_WIDTH_DP), Screen.dp(HANDLE_TOUCH_HEIGHT_DP), Gravity.LEFT | Gravity.TOP));
    container.addView(handle, container.indexOfChild(placeholder) + 1);

    isActive = true;
    leftMain.xgramUpdatePaneInset();
    layout();
    android.util.Log.i("XGram", "two-pane on, chat list " + leftWidth + "px of " + context.getResources().getConfiguration().screenWidthDp + "dp");
  }

  private void layout () {
    // The panes are measured and placed by onMeasure / onLayout below.
    container.requestLayout();
  }

  // Where the panes go, in the container: set by computeSplit in onMeasure and used by onLayout.
  private int splitWidth = -1;
  private int listStart, listEnd, chatStart, chatEnd;
  /** Book posture with a physical hinge: the space it hides, kept empty. Equal values: no gutter. */
  private int gutterStart, gutterEnd;

  /**
   * Splits {@code width} between the chat list and the chat.
   * <ul>
   *   <li>The navigation views carry the left and right system bar and cutout insets as margins
   *   (RootFrameLayout#applyNavigationInsets): the list gets the inset on its side, the chat the one on its side,
   *   so neither runs under a side navigation bar or a cutout.</li>
   *   <li>Right-to-left languages put the list on the right. The root is always laid out left to right, so this
   *   is mirrored here by hand.</li>
   *   <li>Book posture (half open, vertical fold): the panes meet at the hinge instead of at the usual width.</li>
   * </ul>
   */
  private void computeSplit (ViewGroup parent, int width) {
    int insetLeft = 0, insetRight = 0;
    ViewGroup.LayoutParams params = navigation.getValue().getLayoutParams();
    if (params instanceof ViewGroup.MarginLayoutParams) {
      insetLeft = Math.max(0, ((ViewGroup.MarginLayoutParams) params).leftMargin);
      insetRight = Math.max(0, ((ViewGroup.MarginLayoutParams) params).rightMargin);
    }
    int start = Math.min(insetLeft, width), end = Math.max(start, width - insetRight);
    boolean rtl = Lang.rtl();
    splitWidth = width;

    android.graphics.Rect hinge = hingeSplit(parent);
    if (hinge != null && hinge.left >= start && hinge.right <= end) {
      int before = hinge.left - start, after = end - hinge.right;
      int listSize = rtl ? after : before, chatSize = rtl ? before : after;
      if (listSize >= Screen.dp(HINGE_MIN_LIST_DP) && chatSize >= Screen.dp(HINGE_MIN_CHAT_DP)) {
        if (rtl) {
          chatStart = start;
          chatEnd = hinge.left;
          listStart = hinge.right;
          listEnd = end;
        } else {
          listStart = start;
          listEnd = hinge.left;
          chatStart = hinge.right;
          chatEnd = end;
        }
        gutterStart = hinge.left;
        gutterEnd = hinge.right;
        return;
      }
    }

    int total = end - start;
    XgramSettings settings = XgramSettings.instance();
    int list;
    if (settings.paneSplitEvenly()) {
      list = evenListWidth(parent, start, end, rtl);
    } else if (dragListWidth >= 0 || (settings.paneResizable() && settings.paneListWidthDp() > 0)) {
      // Set with the handle: anything that leaves both panes usable.
      int min = Math.min(Screen.dp(HINGE_MIN_LIST_DP), total / 2);
      int max = Math.max(total / 2, total - Screen.dp(HINGE_MIN_CHAT_DP));
      list = Math.max(min, Math.min(max, dragListWidth >= 0 ? dragListWidth : leftWidth));
    } else {
      list = Math.min(leftWidth, total / 2);
    }
    if (rtl) {
      chatStart = start;
      chatEnd = end - list;
      listStart = chatEnd;
      listEnd = end;
      gutterStart = gutterEnd = chatEnd;
    } else {
      listStart = start;
      listEnd = start + list;
      chatStart = listEnd;
      chatEnd = end;
      gutterStart = gutterEnd = listEnd;
    }
  }

  /**
   * Split evenly: half the width each, meeting at the fold when the screen folds down the middle (a Fold lying flat
   * reports its fold too), so the line between the panes sits on the crease.
   */
  private int evenListWidth (ViewGroup parent, int start, int end, boolean rtl) {
    XgramWindow window = context.xgramWindow();
    XgramWindow.Fold fold = window != null ? window.fold() : null;
    if (fold != null && !fold.horizontal) {
      android.graphics.Rect hinge = window.hingeBoundsIn(parent);
      if (hinge != null) {
        int center = (hinge.left + hinge.right) / 2;
        int total = end - start;
        int list = rtl ? end - center : center - start;
        // Only a fold near the middle: anything else is not "even".
        if (list > total * 2 / 5 && list < total * 3 / 5) {
          return list;
        }
      }
    }
    return (end - start) / 2;
  }

  /** Book posture: the hinge in {@code parent}, when the panes should meet there. */
  @Nullable
  private android.graphics.Rect hingeSplit (ViewGroup parent) {
    XgramWindow window = context.xgramWindow();
    XgramWindow.Fold fold = window != null ? window.fold() : null;
    if (fold == null || !fold.separating || fold.horizontal || !XgramSettings.instance().foldHingeSplit()) {
      return null;
    }
    return window.hingeBoundsIn(parent);
  }

  private int dividerWidth () {
    return gutterEnd > gutterStart ? gutterEnd - gutterStart : Math.max(1, Screen.dp(.5f));
  }

  /** Called by the activity's content layout after its own measure pass. */
  public void onMeasure (ViewGroup parent) {
    if (!isActive || leftNavigation == null) {
      return;
    }
    int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
    computeSplit(parent, width);
    int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
    int chatSpec = View.MeasureSpec.makeMeasureSpec(Math.max(0, chatEnd - chatStart), View.MeasureSpec.EXACTLY);
    leftNavigation.getValue().measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, listEnd - listStart), View.MeasureSpec.EXACTLY), heightSpec);
    navigation.getValue().measure(chatSpec, heightSpec);
    if (placeholder != null) {
      placeholder.measure(chatSpec, heightSpec);
    }
    if (underlay != null) {
      underlay.measure(chatSpec, heightSpec);
    }
    if (divider != null) {
      divider.measure(View.MeasureSpec.makeMeasureSpec(dividerWidth(), View.MeasureSpec.EXACTLY), heightSpec);
    }
    if (handle != null) {
      handle.measure(
        View.MeasureSpec.makeMeasureSpec(Screen.dp(HANDLE_TOUCH_WIDTH_DP), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(Screen.dp(HANDLE_TOUCH_HEIGHT_DP), View.MeasureSpec.EXACTLY)
      );
    }
  }

  /** Called by the activity's content layout after its own layout pass. */
  public void onLayout (ViewGroup parent) {
    if (!isActive || leftNavigation == null) {
      return;
    }
    int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
    if (splitWidth != width) {
      computeSplit(parent, width);
    }
    leftNavigation.getValue().layout(listStart, 0, listEnd, height);
    navigation.getValue().layout(chatStart, 0, chatEnd, height);
    if (placeholder != null) {
      placeholder.layout(chatStart, 0, chatEnd, height);
    }
    if (underlay != null) {
      underlay.layout(chatStart, 0, chatEnd, height);
    }
    if (divider != null) {
      int dividerWidth = divider.getMeasuredWidth();
      if (gutterEnd > gutterStart) {
        divider.layout(gutterStart, 0, gutterEnd, height); // covers the hinge
      } else if (listStart < chatStart) {
        divider.layout(listEnd - dividerWidth, 0, listEnd, height); // list on the left
      } else {
        divider.layout(listStart, 0, listStart + dividerWidth, height); // list on the right
      }
    }
    if (handle != null) {
      // Centred on the line, in the middle of the space under the headers.
      int line = listStart < chatStart ? listEnd : listStart;
      int w = handle.getMeasuredWidth(), h = handle.getMeasuredHeight();
      int headerHeight = org.thunderdog.challegram.navigation.HeaderView.getSize(true);
      int top = headerHeight + Math.max(0, (height - headerHeight - h) / 2);
      handle.layout(line - w / 2, top, line - w / 2 + w, top + h);
      boolean visible = canResize();
      handle.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
      if (!visible) {
        handle.cancelDrag();
      }
    }
  }

  // Resizing: the handle on the line between the panes drags it; the width is kept (in dp) for the next time.

  private static final float HANDLE_TOUCH_WIDTH_DP = 24f, HANDLE_TOUCH_HEIGHT_DP = 72f;

  @Nullable private HandleView handle;
  /** The chat list width while the handle is being dragged, else -1. */
  private int dragListWidth = -1;

  /** Not when split evenly, nor in book posture (the hinge is the line), nor with the setting off. */
  private boolean canResize () {
    XgramSettings settings = XgramSettings.instance();
    return isActive && settings.paneResizable() && !settings.paneSplitEvenly() && gutterEnd <= gutterStart;
  }

  private void onDragged (int listWidth) {
    if (dragListWidth != listWidth) {
      dragListWidth = listWidth;
      container.requestLayout();
    }
  }

  private void onDragEnd (boolean commit) {
    if (dragListWidth < 0) {
      return;
    }
    dragListWidth = -1;
    if (commit && listEnd > listStart) {
      XgramSettings.instance().setPaneListWidthDp(Math.round((listEnd - listStart) / Screen.density()));
    }
    leftWidth = computeLeftWidth();
    container.requestLayout();
  }

  /** Double tap on the handle: back to the usual width. */
  private void resetWidth () {
    dragListWidth = -1;
    XgramSettings.instance().setPaneListWidthDp(0);
    leftWidth = computeLeftWidth();
    container.requestLayout();
  }

  /**
   * The Material 3 pane drag handle: a 4dp pill on the line between the panes (wider while held), in a 24 x 72dp touch
   * area. It takes the gesture from the panes and the back swipe as soon as it is touched.
   */
  private final class HandleView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF pill = new android.graphics.RectF();
    private final int touchSlop;
    private float downRawX;
    private int downList;
    private boolean tracking, dragging;
    private long lastTapUp;

    HandleView (android.content.Context context) {
      super(context);
      touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
      setContentDescription(Lang.getString(R.string.XgramPaneResizable));
    }

    void cancelDrag () {
      if (tracking) {
        tracking = dragging = false;
        setPressed(false);
        onDragEnd(false);
        invalidate();
      }
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      boolean held = tracking;
      float w = Screen.dpf(held ? 8f : 4f), h = Screen.dpf(held ? 52f : 48f);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      pill.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
      paint.setColor(Theme.getColor(held ? ColorId.text : ColorId.iconLight));
      c.drawRoundRect(pill, w / 2f, w / 2f, paint);
    }

    @Override
    public boolean onTouchEvent (android.view.MotionEvent e) {
      switch (e.getActionMasked()) {
        case android.view.MotionEvent.ACTION_DOWN: {
          if (!canResize()) {
            return false;
          }
          ViewGroup parent = (ViewGroup) getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
          downRawX = e.getRawX();
          downList = listEnd - listStart;
          tracking = true;
          dragging = false;
          setPressed(true);
          invalidate();
          return true;
        }
        case android.view.MotionEvent.ACTION_MOVE: {
          if (!tracking) {
            return false;
          }
          float dx = e.getRawX() - downRawX;
          if (!dragging && Math.abs(dx) > touchSlop) {
            dragging = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
          }
          if (dragging) {
            onDragged(Math.round(Lang.rtl() ? downList - dx : downList + dx));
          }
          return true;
        }
        case android.view.MotionEvent.ACTION_UP: {
          if (!tracking) {
            return false;
          }
          tracking = false;
          setPressed(false);
          if (dragging) {
            dragging = false;
            onDragEnd(true);
          } else {
            long now = android.os.SystemClock.uptimeMillis();
            if (lastTapUp != 0 && now - lastTapUp <= android.view.ViewConfiguration.getDoubleTapTimeout()) {
              lastTapUp = 0;
              performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
              resetWidth();
            } else {
              lastTapUp = now;
            }
          }
          invalidate();
          return true;
        }
        case android.view.MotionEvent.ACTION_CANCEL: {
          cancelDrag();
          return true;
        }
      }
      return tracking;
    }
  }

  /** @param keepLeft keep the chat list (detached) for the next time two panes are shown */
  private void deactivate (boolean keepLeft) {
    if (keepLeft) {
      detachLeft();
    } else {
      removeLeft();
    }
    isActive = false;
    container.requestLayout();
  }

  private void hideRoot (MainController root) {
    View rootView = root.getValue();
    View headerCell = root.getCustomHeaderCell();
    if (rootView == hiddenRootView && headerCell == hiddenHeaderCell) {
      return;
    }
    showRoot();
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
      rootView.setClipBounds(new android.graphics.Rect());
      if (headerCell != null) {
        headerCell.setClipBounds(new android.graphics.Rect());
      }
    }
    hiddenRootView = rootView;
    hiddenHeaderCell = headerCell;
  }

  private void showRoot () {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
      if (hiddenRootView != null) {
        hiddenRootView.setClipBounds(null);
      }
      if (hiddenHeaderCell != null) {
        hiddenHeaderCell.setClipBounds(null);
      }
    }
    hiddenRootView = null;
    hiddenHeaderCell = null;
  }

  private void removeLeft () {
    detachLeft();
    if (leftNavigation != null) {
      leftNavigation.destroy();
      leftNavigation = null;
    }
    leftMain = null;
  }

  /** Takes the panes off screen. The chat list itself stays alive, so unfolding brings it back where it was. */
  private void detachLeft () {
    showRoot();
    if (leftNavigation != null) {
      container.removeView(leftNavigation.getValue());
    }
    if (divider != null) {
      container.removeView(divider);
      divider = null;
    }
    if (placeholder != null) {
      container.removeView(placeholder);
      placeholder = null;
    }
    if (underlay != null) {
      container.removeView(underlay);
      underlay = null;
    }
    if (handle != null) {
      handle.cancelDrag();
      container.removeView(handle);
      handle = null;
    }
  }

  // Placeholder

  private View createPlaceholder () {
    FrameLayout layout = new FrameLayout(context);
    layout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    layout.setBackgroundColor(Theme.getColor(ColorId.background));
    layout.setClickable(true); // the hidden chat list behind must not get touches

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    content.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

    ImageView icon = new ImageView(context);
    icon.setImageResource(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_forum_96));
    icon.setColorFilter(Theme.getColor(ColorId.iconLight));
    content.addView(icon, new LinearLayout.LayoutParams(Screen.dp(96f), Screen.dp(96f)));

    TextView text = new TextView(context);
    text.setText(Lang.getString(R.string.XgramSelectChat));
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    text.setTextColor(Theme.getColor(ColorId.textLight));
    text.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    textParams.topMargin = Screen.dp(12f);
    content.addView(text, textParams);

    layout.addView(content);

    // An empty header bar, so the status bar area matches the chat list pane's header.
    View header = new View(context);
    header.setBackgroundColor(Theme.headerColor());
    layout.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, org.thunderdog.challegram.navigation.HeaderView.getSize(true), Gravity.TOP));
    return layout;
  }

  private void updatePlaceholder () {
    if (placeholder != null) {
      boolean atRoot = navigation.getStack().size() <= 1 && navigation.getCurrentStackItem() instanceof MainController;
      placeholder.setVisibility(isActive && atRoot ? View.VISIBLE : View.GONE);
    }
  }

  public void onConfigurationChanged (Configuration newConfig) {
    if (leftNavigation != null) {
      leftNavigation.configurationChanged(newConfig);
    }
    leftWidth = computeLeftWidth();
    update();
  }

  public void onThemeChanged () {
    if (placeholder != null) {
      boolean visible = placeholder.getVisibility() == View.VISIBLE;
      container.removeView(placeholder);
      placeholder = createPlaceholder();
      placeholder.setVisibility(visible ? View.VISIBLE : View.GONE);
      container.addView(placeholder, container.indexOfChild(navigation.getValue()) + 1);
      layout();
    }
    if (divider != null) {
      divider.invalidate();
    }
    if (handle != null) {
      handle.invalidate();
    }
    if (underlay != null) {
      container.removeView(underlay);
      underlay = createPlaceholder();
      container.addView(underlay, container.indexOfChild(navigation.getValue()));
      layout();
    }
  }

  @Override
  public void onStackChanged (NavigationStack stack) {
    // The stack also changes on login, logout and account switches: re-check everything.
    context.runOnUiThread(this::update);
  }

  // Back press

  /** Back press when the right pane has nothing left to close: go back in the left pane. */
  public @BackPressMode int performOnBackPressed (boolean fromTop, boolean commit) {
    if (isActive && leftNavigation != null) {
      return leftNavigation.performOnBackPressed(fromTop, commit);
    }
    return BackPressMode.SYSTEM_ACTION_REQUIRED;
  }

  public boolean isLeftNavigation (@Nullable NavigationController navigation) {
    return navigation != null && navigation == leftNavigation;
  }

  /** The chat list in the left pane while two panes are shown (keyboard shortcuts act on it), else null. */
  @Nullable
  public MainController leftMain () {
    return isActive ? leftMain : null;
  }

  /**
   * Whether a screen opened from {@code context} should replace what the right pane shows instead of stacking on
   * it: in two panes the chat list stays on the left, so Back never needs to return to an earlier chat or topic
   * list opened from there. True for the left pane's screens and for the right pane's own chat list.
   */
  public static boolean replacesRightPane (@Nullable ViewController<?> context) {
    if (context == null) {
      return false;
    }
    XgramTwoPane twoPane = context.context().xgramTwoPane();
    if (twoPane == null || !twoPane.isActive) {
      return false;
    }
    // A chat list inside the main screen's pager has no navigation of its own: its parent (the MainController) does.
    ViewController<?> owner = context.getParentOrSelf();
    NavigationController own = owner.navigationController();
    return twoPane.isLeftNavigation(own) || twoPane.isHiddenRoot(owner);
  }

  /** Puts {@code c} on the right pane, replacing anything above the chat list there. */
  public static void showOnRight (ViewController<?> context, ViewController<?> c) {
    NavigationController navigation = context.context().navigation();
    if (navigation == null) {
      return;
    }
    if (navigation.getStackSize() > 1) {
      navigation.setControllerAnimated(c, true, true);
    } else {
      navigation.navigateTo(c);
    }
  }

  public void destroy () {
    navigation.getStack().removeChangeListener(this);
    org.thunderdog.challegram.theme.ThemeManager.instance().removeThemeListener(themeListener);
    XgramWindow window = context.xgramWindow();
    if (window != null) {
      window.removeListener(windowListener);
    }
    removeLeft();
    isActive = false;
  }

  /** The left chat list's screens hear about split screen too (the activity only tells the right pane's). */
  public void onMultiWindowModeChanged (boolean inMultiWindowMode) {
    if (leftNavigation != null) {
      for (ViewController<?> c : leftNavigation.getStack().getAll()) {
        c.onMultiWindowModeChanged(inMultiWindowMode);
      }
    }
  }

  /**
   * Line between the panes. Across the status bar and headers it is a slightly darker header colour (a light
   * separator there looked like a white line between the two headers); below them, the usual separator.
   */
  private static final class DividerView extends View {
    private final android.graphics.Paint paint = new android.graphics.Paint();

    DividerView (android.content.Context context) {
      super(context);
    }

    @Override
    protected void onDraw (android.graphics.Canvas c) {
      int headerHeight = Math.min(getHeight(), org.thunderdog.challegram.navigation.HeaderView.getSize(true));
      paint.setColor(me.vkryl.core.ColorUtils.fromToArgb(Theme.getColor(ColorId.headerBackground), 0xFF000000, .14f));
      c.drawRect(0, 0, getWidth(), headerHeight, paint);
      paint.setColor(Theme.getColor(ColorId.separator));
      c.drawRect(0, headerHeight, getWidth(), getHeight(), paint);
    }
  }
}
