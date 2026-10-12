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
  /** Narrowest window (dp) that gets two panes. */
  private static final int MIN_WIDTH_DP = 600;

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
  }

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
    Configuration configuration = context.getResources().getConfiguration();
    return configuration.screenWidthDp >= MIN_WIDTH_DP;
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
    boolean needActive = root != null && XgramSettings.instance().twoPaneLayout() && isWideEnough(context) && !navigation.isDestroyed();
    try {
      if (needActive) {
        if (!isActive || leftMain == null || leftMain.tdlib() != root.tdlib()) {
          activate(root.tdlib());
        } else {
          layout();
        }
        hideRoot(root);
      } else if (isActive) {
        deactivate();
      }
    } catch (Throwable t) {
      // Never take the app down for a layout feature: fall back to Telegram X's single pane.
      Log.e("XGram: two-pane layout failed", t);
      android.util.Log.e("XGram", "two-pane layout failed", t);
      try {
        deactivate();
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

  private int computeLeftWidth () {
    int width = context.getResources().getConfiguration().screenWidthDp;
    int dp = Math.max(320, Math.min(420, Math.round(width * .38f)));
    return Screen.dp(dp);
  }

  private void activate (Tdlib tdlib) {
    if (leftNavigation != null) {
      removeLeft();
    }
    leftWidth = computeLeftWidth();

    leftNavigation = new NavigationController(context);
    leftNavigation.getStack().addChangeListener(stack -> context.notifyBackPressAvailabilityChanged());
    leftMain = new MainController(context, tdlib);
    View leftView = leftNavigation.getValue();
    leftView.setLayoutParams(new FrameLayout.LayoutParams(leftWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    // Behind the main navigation and the drawer, after the focus view.
    container.addView(leftView, Math.min(1, container.getChildCount()));
    leftNavigation.initController(leftMain);

    divider = new DividerView(context);
    divider.setLayoutParams(new FrameLayout.LayoutParams(Math.max(1, Screen.dp(.5f)), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    container.addView(divider, container.indexOfChild(leftView) + 1);

    placeholder = createPlaceholder();
    container.addView(placeholder, container.indexOfChild(navigation.getValue()) + 1);
    underlay = createPlaceholder();
    container.addView(underlay, container.indexOfChild(navigation.getValue()));

    isActive = true;
    layout();
    android.util.Log.i("XGram", "two-pane on, chat list " + leftWidth + "px of " + context.getResources().getConfiguration().screenWidthDp + "dp");
  }

  private void layout () {
    // The panes are measured and placed by onMeasure / onLayout below.
    container.requestLayout();
  }

  /** Called by the activity's content layout after its own measure pass. */
  public void onMeasure (ViewGroup parent) {
    if (!isActive || leftNavigation == null) {
      return;
    }
    int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
    int left = Math.min(leftWidth, width / 2);
    int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
    int rightSpec = View.MeasureSpec.makeMeasureSpec(Math.max(0, width - left), View.MeasureSpec.EXACTLY);
    leftNavigation.getValue().measure(View.MeasureSpec.makeMeasureSpec(left, View.MeasureSpec.EXACTLY), heightSpec);
    navigation.getValue().measure(rightSpec, heightSpec);
    if (placeholder != null) {
      placeholder.measure(rightSpec, heightSpec);
    }
    if (underlay != null) {
      underlay.measure(rightSpec, heightSpec);
    }
    if (divider != null) {
      divider.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, Screen.dp(.5f)), View.MeasureSpec.EXACTLY), heightSpec);
    }
  }

  /** Called by the activity's content layout after its own layout pass. */
  public void onLayout (ViewGroup parent) {
    if (!isActive || leftNavigation == null) {
      return;
    }
    int width = parent.getMeasuredWidth(), height = parent.getMeasuredHeight();
    int left = Math.min(leftWidth, width / 2);
    leftNavigation.getValue().layout(0, 0, left, height);
    navigation.getValue().layout(left, 0, width, height);
    if (placeholder != null) {
      placeholder.layout(left, 0, width, height);
    }
    if (underlay != null) {
      underlay.layout(left, 0, width, height);
    }
    if (divider != null) {
      divider.layout(left - divider.getMeasuredWidth(), 0, left, height);
    }
  }

  private void deactivate () {
    removeLeft();
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
    showRoot();
    if (leftNavigation != null) {
      container.removeView(leftNavigation.getValue());
      leftNavigation.destroy();
      leftNavigation = null;
    }
    leftMain = null;
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
    icon.setImageResource(R.drawable.baseline_forum_96);
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
    removeLeft();
    isActive = false;
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
