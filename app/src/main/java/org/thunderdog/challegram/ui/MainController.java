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
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.CheckResult;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.collection.SparseArrayCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.os.CancellationSignal;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.filegen.VideoGenerationInfo;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.OverlayButtonWrap;
import org.thunderdog.challegram.navigation.RecyclerViewProvider;
import org.thunderdog.challegram.navigation.SettingsWrap;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ToggleHeaderView2;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.sync.SyncAdapter;
import org.thunderdog.challegram.telegram.ChatFilter;
import org.thunderdog.challegram.telegram.ChatFolderStyle;
import org.thunderdog.challegram.telegram.ChatFoldersListener;
import org.thunderdog.challegram.telegram.CounterChangeListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.telegram.TdlibSettingsManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Test;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.util.FeatureAvailability;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.IconSpan;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.BubbleLayout;
import org.thunderdog.challegram.widget.FillingSpace;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SnackBar;
import org.thunderdog.challegram.widget.ViewPager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.ObjectUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.FutureBool;
import tgx.td.ChatId;
import tgx.td.ChatPosition;
import tgx.td.Td;
import tgx.td.TdConstants;

public class MainController extends ViewPagerController<Void> implements Menu, MoreDelegate, OverlayButtonWrap.Callback, TdlibOptionListener, AppUpdater.Listener, ChatFoldersListener, GlobalCountersListener, Settings.ChatFolderSettingsListener {
  private static final long MAIN_PAGER_ITEM_ID = Long.MIN_VALUE;
  private static final long ARCHIVE_PAGER_ITEM_ID = Long.MIN_VALUE + 1;
  private static final long INVALID_PAGER_ITEM_ID = Long.MAX_VALUE;
  private static final int NO_PENDING_FILTER = -1;

  public MainController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_main;
  }

  private FrameLayoutFix mainWrap;
  private FrameLayoutFix pagerWrap;
  private OverlayButtonWrap composeWrap;

  @Override
  protected View onCreateView (Context context) {
    initPagerSections();
    return super.onCreateView(context);
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    if (composeWrap != null) {
      checkComposeWrapPaddings();
    }
    // XGram M3E: the update snackbar floats above the bottom folder tabs too, as the connection one does
    Views.setPaddingBottom(updateSnackBar, xgramUpdateSnackInset());
    if (xgramConnSnackBar != null) {
      Views.setPaddingBottom(xgramConnSnackBar, xgramConnSnackInset()); // XGram
    }
    updateBottomBarMargins();
    checkMargins();
  }

  private void updateBottomBarMargins () {
    if (bottomBar != null) {
      // XGram M3E: the floating update snackbar leaves the bottom bar where it is, above the navigation bar
      float snackFactor = updateSnackBar != null && xgramSnackPushes() ? updateSnackBar.getVisibilityFactor() : 0f;
      int height = Math.round(extraBottomInsetWithoutIme * (1f - snackFactor));
      Views.setBottomMargin(bottomBar, height);
      bottomBarSpace.setLayoutHeight(height, true);
    }
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    com.fahadali.xgram.XgramSettings.instance().addListener(xgramSettingsListener); // XGram
    com.fahadali.xgram.activity.XgramActivityBadge.instance().addListener(xgramActivityBadgeListener); // XGram: the bell's dot
    if (BuildConfig.DEBUG) {
      Test.execute();
    }

    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: Material 3 Expressive: the chat list is a container with rounded top corners on the app bar's layer
      com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout cornerWrap = new com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout(context);
      cornerWrap.setCornerAnchor(pager);
      addThemeInvalidateListener(cornerWrap); // XGram: the notches take the new app bar color on a theme switch
      pagerWrap = cornerWrap;
    } else {
      pagerWrap = new FrameLayoutFix(context);
    }
    pagerWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    pagerWrap.addView(pager);
    xgramUpdatePaneInset(); // XGram

    mainWrap = new FrameLayoutFix(context);
    mainWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    mainWrap.addView(pagerWrap);
    generateChatSearchView(mainWrap);
    com.fahadali.xgram.appearance.XgramM3ChatSearch.attach(this, getChatSearchView()); // XGram M3E: People row as a tile, Clear button

    contentView.addView(mainWrap);

    UI.setSoftInputMode(UI.getContext(context), Config.DEFAULT_WINDOW_PARAMS);

    composeWrap = new OverlayButtonWrap(context);
    // XGram M3E: a resize (unfolding, a pop-up window) measured it to the other FAB size, also while a tab screen was on
    // top (not measured then): the paddings and the pages' room follow, after the measure pass (no loop: the next
    // measure keeps the size)
    composeWrap.xgramOnFabSizeChanged = () -> UI.post(() -> {
      if (!isDestroyed()) {
        checkComposeWrapPaddings();
      }
    });
    checkComposeWrapPaddings();
    composeWrap.initWithList(this,
      ColorId.circleButtonActive,
      ColorId.circleButtonActiveIcon,
      new int[]{
        R.id.btn_float_compose,
        R.id.btn_float_newSecretChat,
        R.id.btn_float_newChannel,
        R.id.btn_float_newGroup,
        R.id.btn_float_newChat
      }, new int[]{
        R.drawable.baseline_create_24,
        R.drawable.baseline_lock_24,
        R.drawable.baseline_bullhorn_24,
        R.drawable.baseline_group_24,
        R.drawable.baseline_person_24
      }, new int[]{
        ColorId.circleButtonRegular,
        ColorId.circleButtonNewSecret,
        ColorId.circleButtonNewChannel,
        ColorId.circleButtonNewGroup,
        ColorId.circleButtonNewChat
      }, new int[] {
        ColorId.circleButtonRegularIcon,
        ColorId.circleButtonNewSecretIcon,
        ColorId.circleButtonNewChannelIcon,
        ColorId.circleButtonNewGroupIcon,
        ColorId.circleButtonNewChatIcon
      }, new int[]{
        R.string.NewSecretChat,
        R.string.NewChannel,
        R.string.NewGroup,
        R.string.NewChat
      }, false);
    composeWrap.setCallback(this);
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      // XGram: an extended FAB (icon and "New chat") at the top of the chat list that shrinks once the list scrolls
      composeWrap.xgramSetExtendedLabel(Lang.getString(R.string.XgramFabNewChat));
      com.fahadali.xgram.appearance.XgramMotion.addLiftListener(xgramFabLift);
    }
    contentView.addView(composeWrap);
    xgramUpdateComposeVisibility();
    xgramConnSetup(); // XGram M3E: connection pill and snackbar
    xgramContentView = contentView;
    xgramUpdateNavBar(); // XGram: the bottom navigation bar of narrow windows (Appearance > Layout)
    // XGram M3E: the floating navigation toolbar's morph follows a finger dragging the pages (XgramNavToolbar)
    pager.addOnPageChangeListener(new androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageScrollStateChanged (int state) {
        xgramOnPagerScrollState(state);
      }
    });

    makeStartupChecks();

    if (shareIntent != null) {
      shareIntentImpl();
    }

    tdlib.wallpaper().ensureWallpaperAvailability();
    tdlib.listeners().addOptionsListener(this);
    tdlib.listeners().subscribeToChatFoldersUpdates(this);
    tdlib.context().global().addCountersListener(this);
    Settings.instance().addChatFolderSettingsListener(this);
    if (Settings.instance().chatFoldersEnabled()) {
      if (this.chatFolderInfos != tdlib.chatFolders()) {
        updatePagerSections(true);
      }
      tdlib.settings().addChatListPositionListener(chatListPositionListener = new ChatListPositionListener());
    }

    prepareControllerForPosition(0, this::executeScheduledAnimation);

    if (headerCell != null) {
      headerCell.getTopView().setOnSlideOffListener(new ViewPagerTopView.OnSlideOffListener() {

        private long selectedPagerItemId = INVALID_PAGER_ITEM_ID;

        @Override
        public boolean onSlideOffPrepare (View view, MotionEvent event, int index) {
          if (hasFolders()) {
            return !useGlobalFilter() || globalFilter == FILTER_NONE;
          }
          return getPagerItemId(index) == MAIN_PAGER_ITEM_ID;
        }

        @Override
        public void onSlideOffStart (View view, MotionEvent event, int index) {
          selectedPagerItemId = getPagerItemId(index);
          setMenuVisible(view, selectedPagerItemId, true);
          if (composeWrap != null && displayTabsAtBottom()) {
            composeWrap.hide();
          }
          xgramFolderMenu = true; // XGram M3E: the connection pill (3dp up) would draw over the folder menu
          xgramConnUpdateSuppressed();
          xgramUpdateNavBarHidden(); // XGram: so would the bottom navigation bar, over the bottom folder tabs' menu
        }

        @Override
        public void onSlideOffFinish (View view, MotionEvent event, int index, boolean apply) {
          float offsetX = getOffsetX(view);
          float offsetY = getOffsetX(view);
          if (apply && selectedSection != -1) {
            View selectedView = menu.getChildAt(selectedSection);
            int selectedFilter = (int) selectedView.getTag(R.id.btn_filter);
            requestChatsFilter(selectedFilter, selectedPagerItemId);
            setSelectedSection(-1, event, offsetX, offsetY, true);
          } else {
            setSelectedSection(-1, event, offsetX, offsetY, false);
          }
          setMenuVisible(view, selectedPagerItemId, false);
          selectedPagerItemId = INVALID_PAGER_ITEM_ID;
          xgramFolderMenu = false;
          xgramConnUpdateSuppressed();
          xgramUpdateNavBarHidden(); // XGram
        }

        @Override
        public void onSlideOffMovement (View view, MotionEvent event, int index) {
          float x = event.getX();
          float y = event.getY();
          float offsetX = getOffsetX(view);
          float offsetY = getOffsetY(view);
          x -= offsetX;
          y -= offsetY + mainWrap.getTranslationY() + pagerWrap.getTranslationY();
          int selectedSection = -1;
          if (x >= 0 && x < menu.getMeasuredWidth()) {
            int firstItemEnd = menu.getChildAt(0).getBottom();
            if (y <= firstItemEnd) {
              selectedSection = 0;
            } else if (y < menu.getHeight()) {
              for (int section = 1; section < menu.getChildCount(); section++) {
                View child = menu.getChildAt(section);
                if (y >= child.getTop() && y < child.getBottom() && child.getVisibility() != View.GONE) {
                  selectedSection = section;
                  break;
                }
              }
            }
          }
          setSelectedSection(selectedSection, event, offsetX, offsetY, false);
        }

        private float getOffsetX (View view) {
          return menu.getX() - getX(view, headerCell.getView());
        }

        private float getOffsetY (View view) {
          FrameLayoutFix.LayoutParams lp = (FrameLayoutFix.LayoutParams) menu.getLayoutParams();
          int verticalGravity = Gravity.VERTICAL_GRAVITY_MASK & lp.gravity;
          return verticalGravity == Gravity.BOTTOM ? -menu.getHeight() : view.getHeight();
        }
      });
      if (Settings.instance().chatFoldersEnabled()) {
        headerCell.getTopView().setCounterAlphaProvider(new ViewPagerTopView.CounterAlphaProvider() {
          @Override
          public float getTextAlpha (Counter counter, float alphaFactor) {
            return MathUtils.fromTo(1f, .5f + .5f * alphaFactor, counter.getMuteFactor());
          }
        });
      }
    }

    // tdlib.awaitConnection(this::unlockTabs);

    checkTabs();
    checkMargins();

    context().appUpdater().addListener(this);
    if (context().appUpdater().state() == AppUpdater.State.READY_TO_INSTALL) {
      onAppUpdateAvailable(context().appUpdater().flowType() == AppUpdater.FlowType.TELEGRAM_CHANNEL, true);
    }
  }

  @Override
  public CharSequence getName () {
    if (useGlobalFilter()) {
      return Lang.getString(getGlobalFilterName(globalFilter));
    }
    if (xgramRailTitle()) {
      // XGram M3E: the app bar title instead of the Chats / Calls tabs (now on the navigation rail): the page shown
      return xgramOnCallsPage() ? Lang.getString(R.string.Calls) : getMenuSectionName(MAIN_PAGER_ITEM_ID, POSITION_CHATS, /* hasFolders */ false, ChatFolderStyle.LABEL_ONLY, /* upperCase */ false);
    }
    return Lang.getString(R.string.Chats);
  }

  private @Nullable ToggleHeaderView2 toggleHeaderView;

  @Override
  public View getCustomHeaderCell () {
    if (displayTabsAtBottom()) {
      if (useGlobalFilter()) {
        if (toggleHeaderView == null) {
          toggleHeaderView = new ToggleHeaderView2(context);
          toggleHeaderView.setTextTop(17f);
          toggleHeaderView.setTriangleTop(16f);
          toggleHeaderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.TOP, Screen.dp(56f), 0, Screen.dp(68f), 0));
          toggleHeaderView.setOnClickListener(v -> showGlobalFilter());
          toggleHeaderView.setOnLongClickListener(v -> applyGlobalFilter(FILTER_NONE));
          toggleHeaderView.setTitle(Lang.getString(getGlobalFilterName(globalFilter)), /* animated */ false);
          toggleHeaderView.setColorSet(new ToggleHeaderView2.ColorSet() {
            @Override
            public int titleColor () {
              return getHeaderTextColor();
            }

            @SuppressLint("WrongConstant")
            @Override
            public int iconColorId () {
              return getHeaderIconColorId();
            }
          });
          addThemeInvalidateListener(toggleHeaderView);
        }
        xgramFitToggleHeader();
        return toggleHeaderView;
      }
      return null;
    }
    if (xgramRailTitle()) {
      return null; // XGram M3E: Chats and Calls are on the navigation rail, the app bar shows the page's title (getName)
    }
    if (headerCell != null) {
      View headerCellView = headerCell.getView();
      if (bottomBar != null && headerCellView.getParent() == bottomBar) {
        return null;
      }
      return headerCellView;
    }
    return null;
  }

  // Menu

  private int selectedSection = -1;
  private long downTime;

  private void setSelectedSection (int section, MotionEvent event, float xOffset, float yOffset, boolean apply) {
    float eventX = event.getX() - xOffset;
    float eventY = event.getY() - yOffset;
    if (this.selectedSection != section) {
      if (this.selectedSection != -1) {
        View view = menu.getChildAt(this.selectedSection);
        view.dispatchTouchEvent(MotionEvent.obtain(this.downTime, event.getEventTime(), apply ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL, eventX, eventY - view.getTop(), event.getMetaState()));
      }
      this.selectedSection = section;
      if (section != -1) {
        View view = menu.getChildAt(this.selectedSection);
        view.dispatchTouchEvent(MotionEvent.obtain(this.downTime = SystemClock.uptimeMillis(), event.getEventTime(), MotionEvent.ACTION_DOWN, eventX, eventY - view.getTop(), event.getMetaState()));
      }
    } else if (section != -1) {
      View view = menu.getChildAt(this.selectedSection);
      view.dispatchTouchEvent(MotionEvent.obtain(this.downTime, event.getEventTime(), MotionEvent.ACTION_MOVE, eventX, eventY - view.getTop(), event.getMetaState()));
    }
  }

  private @Nullable BubbleLayout menu;
  private boolean menuNeedArchive;
  private View menuAnchor;

  private @PendingFilter int pendingFilter = NO_PENDING_FILTER;
  private long pendingPagerItemId = INVALID_PAGER_ITEM_ID;
  private @Nullable ChatsController pendingChatsController;

  private void cancelPendingFilter () {
    if (pendingChatsController != null) {
      pendingChatsController.destroy();
      pendingChatsController = null;
    }
    this.pendingFilter = NO_PENDING_FILTER;
    this.pendingPagerItemId = INVALID_PAGER_ITEM_ID;
  }

  private int applyPendingFilter (@PendingFilter int filter, long pagerItemId, ChatsController chatsController) {
    if (this.pendingFilter != filter || this.pendingPagerItemId != pagerItemId || this.pendingChatsController != chatsController) {
      chatsController.destroy();
      return NO_POSITION;
    }

    this.pendingFilter = NO_PENDING_FILTER;
    this.pendingPagerItemId = INVALID_PAGER_ITEM_ID;
    this.pendingChatsController = null;

    int pagerItemPosition = getPagerItemPosition(pagerItemId);
    if (pagerItemPosition == NO_POSITION) {
      chatsController.destroy();
      return NO_POSITION;
    }
    if (!useGlobalFilter() || filter != globalFilter || filter == FILTER_NONE) {
      setSelectedFilter(pagerItemId, filter);
      replaceSectionItem(pagerItemId, pagerItemPosition);
      if (getCurrentPagerItemPosition() == pagerItemPosition) {
        // XGram M3E: the app bar title of the navigation rail follows (moving to the page shown fires no page change)
        xgramRailPageChanged();
      }
    }
    replaceController(pagerItemId, chatsController);
    if (getCurrentPagerItemPosition() == pagerItemPosition) {
      showComposeWrap(/* controller */ null);
    }
    return pagerItemPosition;
  }

  private void replaceSectionItem (long pagerItemId, int pagerItemPosition) {
    if (headerCell == null) {
      return;
    }
    ViewPagerTopView.Item item = buildSectionItem(pagerItemId, pagerItemPosition);
    if (item == null) {
      return;
    }
    if (!hasFolders()) {
      getDefaultSectionItems().set(0, item);
    }
    headerCell.getTopView().setItemAt(pagerItemPosition, item, isFocused());
  }

  private @Nullable ViewPagerTopView.Item buildSectionItem (long pagerItemId, int pagerItemPosition) {
    final ViewPagerTopView.Item item;
    if (hasFolders()) {
      int chatFolderStyle = tdlib.settings().chatFolderStyle();
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        item = buildMainSectionItem(pagerItemPosition, chatFolderStyle);
      } else if (pagerItemId == ARCHIVE_PAGER_ITEM_ID) {
        item = buildArchiveSectionItem(pagerItemPosition, chatFolderStyle);
      } else {
        TdApi.ChatList chatList = pagerChatLists.get(pagerItemPosition);
        if (TD.isChatListFolder(chatList)) {
          TdApi.ChatListFolder chatListFolder = (TdApi.ChatListFolder) chatList;
          TdApi.ChatFolderInfo chatFolderInfo = tdlib.chatFolderInfo(chatListFolder.chatFolderId);
          if (chatFolderInfo == null) {
            return null;
          }
          item = buildSectionItem(pagerItemId, pagerItemPosition, chatList, chatFolderInfo, chatFolderStyle);
        } else {
          throw new UnsupportedOperationException("chatList = " + chatList);
        }
      }
    } else {
      int filter = getSelectedFilter(pagerItemId);
      if (filter == FILTER_NONE && !menuNeedArchive) {
        item = getDefaultMainItem();
      } else {
        CharSequence sectionName = getMenuSectionName(pagerItemId, pagerItemPosition, /* hasFolders */ false, ChatFolderStyle.LABEL_ONLY, /* upperCase */ true);
        item = new ViewPagerTopView.Item(sectionName);
      }
    }
    return item;
  }

  private void setNeedArchive (boolean needArchive) {
    if (this.menuNeedArchive != needArchive) {
      this.menuNeedArchive = needArchive;
    }
  }

  private void requestChatsFilter (@Filter int requestedFilter, long pagerItemId) {
    if (pagerItemId == INVALID_PAGER_ITEM_ID)
      return;
    int selectedFilter = getSelectedFilter(pagerItemId);
    if ((selectedFilter == requestedFilter && (requestedFilter != FILTER_NONE || (pagerItemId == MAIN_PAGER_ITEM_ID && !menuNeedArchive))) ||
        (pagerItemId == MAIN_PAGER_ITEM_ID && menuNeedArchive && selectedFilter == FILTER_NONE && requestedFilter == FILTER_ARCHIVE)) {
      cancelPendingFilter();
      int pagerItemPosition = getPagerItemPosition(pagerItemId);
      if (pagerItemPosition != NO_POSITION) {
        setCurrentPagerPosition(pagerItemPosition, true);
      }
      return;
    }
    if (this.pendingFilter == requestedFilter && this.pendingPagerItemId == pagerItemId) {
      return; // Still waiting to switch, do nothing
    }
    cancelPendingFilter();

    int position = getPagerItemPosition(pagerItemId);
    if (position == NO_POSITION)
      return;
    int filter;
    if (requestedFilter == FILTER_NONE) {
      filter = FILTER_NONE;
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        setNeedArchive(false);
      }
    } else if (requestedFilter == FILTER_ARCHIVE) {
      filter = FILTER_NONE;
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        setNeedArchive(selectedFilter != FILTER_NONE || !menuNeedArchive);
      }
    } else {
      filter = requestedFilter;
    }
    TdApi.ChatList chatList;
    if (pagerItemId == MAIN_PAGER_ITEM_ID) {
      chatList = menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN;
    } else {
      chatList = pagerChatLists.get(position);
    }
    postApplyFilter(pagerItemId, chatList, filter);
  }

  private void postApplyFilter (long pagerItemId, TdApi.ChatList chatList, @Filter int filter) {
    ChatsController chatsController = newChatsController(chatList, filter);
    modifyNewPagerItemController(chatsController);

    this.pendingFilter = filter;
    this.pendingPagerItemId = pagerItemId;
    this.pendingChatsController = chatsController;

    chatsController.postOnAnimationExecute(() -> {
      int pagerItemPosition = applyPendingFilter(filter, pagerItemId, chatsController);
      if (pagerItemPosition != NO_POSITION && (!useGlobalFilter() || filter != globalFilter)) {
        setCurrentPagerPosition(pagerItemPosition, /* animated */ true);
      }
    });
  }

  private void layoutMenu (@NonNull View view) {
    if (menu == null || headerCell == null)
      return;
    menuAnchor = view;
    int menuWidth = menu.getMeasuredWidth();
    if (menuWidth == 0)
      return;
    float x = getX(view, headerCell.getView());
    boolean displayTabsAtBottom = displayTabsAtBottom();
    int viewWidth = headerCell.getTopView().getItemWidth(view);
    float translationX = x - menuWidth / 2 + viewWidth / 2;
    int dx = displayTabsAtBottom ? 0 : Screen.dp(7f);
    // XGram: kept inside its own parent (the chat list pane of the two-pane layout is narrower than the screen), and
    // with Expressive design clear of the pane's side margins
    View menuParent = (View) menu.getParent();
    int parentWidth = menuParent != null && menuParent.getWidth() > 0 ? menuParent.getWidth() : Screen.currentWidth();
    int sideInset = pagerWrap instanceof com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout ? ((com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout) pagerWrap).getSideInset() : 0;
    int minX = dx + sideInset;
    menu.setTranslationX(MathUtils.clamp(translationX, minX, Math.max(minX, parentWidth - menuWidth - dx - sideInset)));
    int cornerCenterX = menuWidth / 2 + Math.round(translationX - menu.getTranslationX());
    menu.setCornerCenterX(MathUtils.clamp(cornerCenterX, Screen.dp(18f), menuWidth - Screen.dp(18f)));
  }

  private static float getX (View view, View parent) {
    float x = view.getX();
    View currentView = view;
    do {
      currentView = (View) currentView.getParent();
      if (currentView == null)
        break;
      x += currentView.getX();
    } while (currentView != parent);
    return x;
  }

  private void setMenuVisible (View anchorView, long pagerItemId, boolean visible) {
    if (!visible && menu == null)
      return;
    View parent = null;
    if (menu == null) {
      boolean top = !displayTabsAtBottom();
      final boolean xgramM3 = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
      menu = new BubbleLayout(context, this, top) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          if (menuAnchor != null) {
            layoutMenu(menuAnchor);
          }
        }
      };
      if (xgramM3) {
        menu.xgramSetMaterialMenu(true); // XGram M3E: a Material 3 menu, not the sticker panel's bubble with an arrow
      }
      int gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.LEFT;
      int marginBottom = top ? 0 : getHeaderHeight();
      menu.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity, 0, 0, 0, marginBottom));

      if (!xgramM3) {
        menu.setPadding(menu.getPaddingLeft(), menu.getPaddingTop() + Screen.dp(14f), menu.getPaddingRight(), menu.getPaddingBottom() + Screen.dp(13f));
      }
      xgramMenuPills = xgramM3 ? new android.graphics.drawable.GradientDrawable[FILTERS.length] : null;
      for (int filter : FILTERS) {
        int filterName = getFilterName(filter);
        int filterIcon = getFilterIcon(filter, false);

        TextView sectionView = new AppCompatTextView(context);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;

        sectionView.setId(R.id.btn_filter);
        sectionView.setLayoutParams(params);
        if (xgramM3) {
          sectionView.setTag(R.id.btn_filter, filter);
          sectionView.setOnClickListener(v -> { });
          xgramStyleFilterRow(sectionView, filter, filterName, filterIcon);
          menu.addView(sectionView);
          continue;
        }
        sectionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        sectionView.setGravity(Lang.gravity(Gravity.CENTER_VERTICAL));
        sectionView.setPadding(Screen.dp(Lang.rtl()  ? 24f : 16f), Screen.dp(11.5f), Screen.dp(Lang.rtl() ? 16f : 24f), Screen.dp(11.5f));
        sectionView.setTypeface(Fonts.getRobotoMedium());
        sectionView.setTag(R.id.btn_filter, filter);
        sectionView.setOnClickListener(v -> { });
        sectionView.setCompoundDrawablePadding(Screen.dp(16));
        if (!Lang.rtl()) {
          sectionView.setCompoundDrawablesWithIntrinsicBounds(com.fahadali.xgram.appearance.XgramIconPack.map(filterIcon), ResourcesCompat.ID_NULL, ResourcesCompat.ID_NULL, ResourcesCompat.ID_NULL);
        } else {
          sectionView.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.ID_NULL, ResourcesCompat.ID_NULL, com.fahadali.xgram.appearance.XgramIconPack.map(filterIcon), ResourcesCompat.ID_NULL);
        }
        Views.setMediumText(sectionView, Lang.getString(bindLocaleChanger(filterName, sectionView, false, true)));
        RippleSupport.setTransparentBlackSelector(sectionView);
        menu.addView(sectionView);
      }
      pagerWrap.addView(menu, 1);
      parent = pagerWrap;
    }
    if (visible) {
      int selectedFilter = getSelectedFilter(pagerItemId);

      boolean needArchive = pagerItemId == MAIN_PAGER_ITEM_ID && (menuNeedArchive || (!xgramArchiveTabEnabled() && tdlib.hasArchivedChats()));
      menu.getChildAt(FILTER_ARCHIVE).setVisibility(needArchive ? View.VISIBLE : View.GONE);

      TdApi.ChatList chatList;
      if (pagerItemId == MAIN_PAGER_ITEM_ID) {
        chatList = selectedFilter == FILTER_ARCHIVE || menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN;
      } else {
        int pagerItemPosition = getPagerItemPosition(pagerItemId);
        chatList = hasFolders() && pagerItemPosition < pagerChatLists.size() ? pagerChatLists.get(pagerItemPosition) : null;;
      }

      boolean hasUnreadChats = chatList != null && tdlib.hasUnreadChats(chatList);
      boolean needUnread = selectedFilter == FILTER_UNREAD || hasUnreadChats;
      menu.getChildAt(FILTER_UNREAD).setVisibility(needUnread ? View.VISIBLE : View.GONE);

      for (int index = 0; index < menu.getChildCount(); index++) {
        TextView sectionView = (TextView) menu.getChildAt(index);
        if (sectionView.getVisibility() != View.VISIBLE)
          continue;
        int filter = (int) sectionView.getTag(R.id.btn_filter);
        boolean isSelected;
        if (filter == FILTER_NONE && pagerItemId == MAIN_PAGER_ITEM_ID) {
          isSelected = selectedFilter == FILTER_NONE && !menuNeedArchive;
        } else if (filter == FILTER_ARCHIVE && pagerItemId == MAIN_PAGER_ITEM_ID) {
          isSelected = menuNeedArchive;
        } else {
          isSelected = selectedFilter == filter;
        }
        if (xgramMenuPills != null) {
          xgramColorFilterRow(sectionView, filter, isSelected);
          continue;
        }
        int textColorId = isSelected ? ColorId.textNeutral : ColorId.textLight;
        sectionView.setTextColor(Theme.getColor(textColorId));
        int iconColorId = isSelected ? ColorId.iconActive : ColorId.iconLight;
        TextViewCompat.setCompoundDrawableTintList(sectionView, ColorStateList.valueOf(Theme.getColor(iconColorId)));
        addThemeTextColorListener(sectionView, textColorId);
        addThemeCompoundDrawableColorListener(sectionView, iconColorId);
      }
      layoutMenu(anchorView);
    } else {
      for (int index = 0; index < menu.getChildCount(); index++) {
        removeThemeListenerByTarget(menu.getChildAt(index));
      }
    }
    menu.setBubbleVisible(visible, parent);
  }

  /** XGram M3E: the folder filter menu's selected-row pills, by filter (null: Telegram X's bubble menu). */
  private @Nullable android.graphics.drawable.GradientDrawable[] xgramMenuPills;

  /**
   * XGram M3E: a folder filter menu row as the other Material 3 menus (MenuMoreWrap): bodyLarge, a 24dp icon, 12dp
   * paddings and icon gap, at least 48dp tall, a press ripple and a secondaryContainer pill (selected) 4dp inside it
   * with medium corners.
   */
  private void xgramStyleFilterRow (TextView row, @Filter int filter, @StringRes int filterName, @DrawableRes int filterIcon) {
    com.fahadali.xgram.appearance.XgramExpressive.applyText(row, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_L, false);
    row.setGravity(Lang.gravity(Gravity.CENTER_VERTICAL));
    row.setSingleLine(true);
    row.setMinHeight(Screen.dp(48f));
    row.setCompoundDrawablePadding(Screen.dp(12f));
    int icon = com.fahadali.xgram.appearance.XgramIconPack.map(filterIcon);
    if (!Lang.rtl()) {
      row.setCompoundDrawablesWithIntrinsicBounds(icon, ResourcesCompat.ID_NULL, ResourcesCompat.ID_NULL, ResourcesCompat.ID_NULL);
    } else {
      row.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.ID_NULL, ResourcesCompat.ID_NULL, icon, ResourcesCompat.ID_NULL);
    }
    row.setText(Lang.getString(bindLocaleChanger(filterName, row, false, false)));
    final float radius = Screen.dp(com.fahadali.xgram.appearance.XgramExpressive.CORNER_M);
    final int inset = Screen.dp(4f);
    android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
    pill.setCornerRadius(radius);
    pill.setColor(0);
    android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
    mask.setColor(0xffffffff);
    mask.setCornerRadius(radius);
    android.graphics.drawable.RippleDrawable ripple = new android.graphics.drawable.RippleDrawable(
      ColorStateList.valueOf(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE), com.fahadali.xgram.appearance.XgramExpressive.STATE_PRESSED)),
      new android.graphics.drawable.InsetDrawable(pill, inset), new android.graphics.drawable.InsetDrawable(mask, inset));
    row.setBackground(ripple);
    row.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0); // after the background: it would take the 4dp inset as padding
    if (xgramMenuPills != null && filter >= 0 && filter < xgramMenuPills.length) {
      xgramMenuPills[filter] = pill;
    }
  }

  /** XGram M3E: colors of a folder filter menu row each time the menu opens (they follow the theme then). */
  private void xgramColorFilterRow (TextView row, @Filter int filter, boolean isSelected) {
    final int onSurface = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE);
    final int onSelected = com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SECONDARY_CONTAINER);
    row.setTextColor(isSelected ? onSelected : onSurface);
    int iconColor = isSelected ? onSelected : com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT);
    TextViewCompat.setCompoundDrawableTintList(row, ColorStateList.valueOf(iconColor));
    if (xgramMenuPills != null && filter >= 0 && filter < xgramMenuPills.length && xgramMenuPills[filter] != null) {
      xgramMenuPills[filter].setColor(isSelected ? com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.SECONDARY_CONTAINER) : 0);
    }
    if (row.getBackground() instanceof android.graphics.drawable.RippleDrawable) {
      ((android.graphics.drawable.RippleDrawable) row.getBackground()).setColor(ColorStateList.valueOf(com.fahadali.xgram.appearance.XgramExpressive.withAlpha(onSurface, com.fahadali.xgram.appearance.XgramExpressive.STATE_PRESSED)));
    }
  }

  // Language

  @Override
  public void onSuggestedLanguagePackChanged (String languagePackId, TdApi.LanguagePackInfo languagePackInfo) {
    executeOnUiThreadOptional(() -> {
      if (isFocused()) {
        showSuggestions();
      }
    });
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (composeWrap != null)
      composeWrap.updateRtl();
    checkHeaderMargins();
  }

  public void setComposeAlpha (float alpha) {
    if (composeWrap != null) {
      composeWrap.close();
      composeWrap.setAlpha(alpha);
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    UI.startNotificationService();
    showAnnoyingAlertsForCompliance(true);
  }

  private void makeStartupChecks () {
    tdlib.context().checkDeviceToken();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    ViewController<?> c = getCachedControllerForPosition(0);
    return c == null || c.needAsynchronousAnimation();
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (composeWrap != null) {
      composeWrap.close();
    }
  }

  @Override
  protected boolean useCenteredTitle () {
    return true;
  }

  // Search

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    composeWrap.forceHide();
    xgramSearching = true; // XGram
    xgramConnUpdateSuppressed();
    xgramUpdateNavBarHidden();
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    composeWrap.showIfWasHidden();
    xgramSearching = false; // XGram
    xgramConnUpdateSuppressed();
    xgramUpdateNavBarHidden();
  }

  // XGram M3E: no connection pill while chats are selected (the selection app bar is up)

  @Override
  protected void onEnterSelectMode () {
    super.onEnterSelectMode();
    xgramSelecting = true;
    xgramConnUpdateSuppressed();
    xgramUpdateNavBarHidden(); // XGram: the selection takes the bottom (its floating toolbar, the counter)
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && composeWrap != null && !inSearchMode()) {
      // XGram M3E: the selection's floating toolbar (ChatsController) takes the bottom: the compose FAB steps aside
      composeWrap.forceHide();
    }
  }

  @Override
  public void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    xgramSelecting = false;
    xgramConnUpdateSuppressed();
    xgramUpdateNavBarHidden();
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && composeWrap != null && !inSearchMode()) {
      composeWrap.showIfWasHidden(); // XGram M3E
    }
  }

  /**
   * XGram M3E: what a floating toolbar over the chat list keeps clear of at the bottom (the navigation bar and, when
   * the folder tabs are at the bottom, their bar), as the snackbars do. Not the bottom navigation bar (XgramNavBar):
   * it steps aside while chats are selected.
   */
  public int xgramFloatingBottomInset () {
    return xgramBottomBarsInset();
  }

  /*@Override
  protected void onEnterSelectMode () {
    super.onEnterSelectMode();
    composeWrap.forceHide();
  }

  @Override
  protected void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    composeWrap.showIfWasHidden();
  }*/

  @Override
  protected View getSearchAntagonistView () {
    return pagerWrap;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_NEED_MESSAGES | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
  }

  @Override
  protected TdApi.ChatList getChatMessagesSearchChatList () {
    ChatsController c = findChatsControllerForSearchMessages();
    if (c != null && TD.isChatListArchive(c.chatList())) {
      return ChatPosition.CHAT_LIST_ARCHIVE;
    }
    return null;
  }

  @Nullable
  public final ChatsController findMainChatsController () {
    return (ChatsController) getCachedControllerForItemId(MAIN_PAGER_ITEM_ID);
  }

  @Nullable
  public final ChatsController getCurrentChatsController () {
    ViewController<?> current = getCurrentPagerItem();
    return current instanceof ChatsController ? (ChatsController) current : null;
  }

  @Nullable
  public final ChatsController findChatsControllerForSearchMessages () {
    return hasFolders() ? getCurrentChatsController() : findMainChatsController();
  }

  @Override
  protected boolean filterChatMessageSearchResult (TdApi.Chat chat) {
    ChatsController c = findChatsControllerForSearchMessages();
    if (c != null) {
      TdApi.ChatList chatList = c.chatList();
      if (TD.isChatListFolder(chatList) && Config.SEARCH_MESSAGES_ONLY_IN_SELECTED_FOLDER) {
        if (ChatPosition.findPosition(chat, chatList) == null)
          return false;
      }
      ChatFilter filter = c.getFilter();
      if (filter != null && filter.canFilterMessages()) {
        return filter.accept(chat);
      }
    }
    return super.filterChatMessageSearchResult(chat);
  }

  @Override
  protected int getChatMessagesSearchTitle () {
    ChatsController c = findChatsControllerForSearchMessages();
    ChatFilter filter = c != null ? c.getFilter() : null;
    boolean isArchive = c != null && TD.isChatListArchive(c.chatList());
    if (filter != null && filter.canFilterMessages())
      return filter.getMessagesStringRes(isArchive);
    if (isArchive)
      return R.string.MessagesArchive;
    return super.getChatMessagesSearchTitle();
  }

  // Main

  @Override
  protected boolean overridePagerParent () {
    return true;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return mainWrap;
  }

  // Overlay wrap

  @Override
  public boolean isIntercepted () {
    return composeWrap != null && composeWrap.isShowing();
  }

  @Override
  public void dismissIntercept () {
    if (composeWrap != null) {
      composeWrap.close();
    }
  }

  @Override
  public boolean onOverlayButtonClick (int id, View view) {
    // TODO check if viewPager is scrolling
    if (id == R.id.user) {
      TdApi.User user = ((TdApi.User) view.getTag());
      if (user != null) {
        tdlib.ui().openPrivateChat(this, user.id, null);
      }
    } else if (id == R.id.btn_float_addContact) {
      navigateTo(new PhoneController(context, tdlib).setMode(PhoneController.MODE_ADD_CONTACT));
    } else if (id == R.id.btn_float_call) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_CALL);
      navigateTo(c);
    } else if (id == R.id.btn_float_compose) {
      if (navigationController != null && headerView != null && !navigationController.isAnimating() && !headerView.isAnimating() && composeWrap != null) {
        composeWrap.toggle();
      }
      return false;
    } else if (id == R.id.btn_float_newChannel) {
      navigateTo(new CreateChannelController(context, tdlib));
    } else if (id == R.id.btn_float_newGroup) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_NEW_GROUP);
      navigateTo(c);
    } else if (id == R.id.btn_float_newSecretChat) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_NEW_SECRET_CHAT);
      navigateTo(c);
    } else if (id == R.id.btn_float_newChat) {
      ContactsController c = new ContactsController(context, tdlib);
      c.initWithMode(ContactsController.MODE_NEW_CHAT);
      navigateTo(c);
    }
    return true;
  }

  @Override
  public void onPageScrolled (int position, int actualPosition, float actualPositionOffset, int actualPositionOffsetPixels) {
    super.onPageScrolled(position, actualPosition, actualPositionOffset, actualPositionOffsetPixels);
    if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL && displayTabsAtBottom()) {
      showBottomBar();
    }
    // XGram M3E: the floating navigation toolbar: its band follows the page, its morph a dragging finger. The offset
    // is the pager's own (right to left, the pages are reversed: the logical position goes down as it grows)
    xgramPagerPosition = position == actualPosition ? position + actualPositionOffset : position - actualPositionOffset;
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    if (toolbar != null) {
      toolbar.onPagerScrolled(xgramCallsFraction(), xgramPagerDragged && !hasFolders());
    }
  }

  /** XGram M3E: where the pages are (position and offset), for the toolbar's band. */
  private float xgramPagerPosition;
  /** XGram M3E: a finger dragged the pages (until they stop): the toolbar's morph follows the pager directly. */
  private boolean xgramPagerDragged;

  private void xgramOnPagerScrollState (int state) {
    if (state == androidx.viewpager.widget.ViewPager.SCROLL_STATE_DRAGGING) {
      xgramPagerDragged = true;
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar != null) {
        toolbar.onPagerDragStarted(); // the finger takes over from a tap's springs
      }
    } else if (state == androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE) {
      xgramPagerDragged = false;
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar != null) {
        toolbar.onPagerIdle();
      }
    }
  }

  /** XGram M3E: 0 on Chats, 1 on Calls, in between while the pages move (0 with folders). */
  public float xgramCallsFraction () {
    return hasFolders() ? 0f : MathUtils.clamp(xgramPagerPosition - POSITION_CHATS);
  }

  /**
   * XGram M3E: the band behind the floating navigation toolbar on this screen: the page's colour (the chats' filling,
   * the calls' background), mixed while the pages move.
   */
  public int xgramBarBandColor () {
    return ColorUtils.fromToArgb(Theme.getColor(ColorId.filling), Theme.getColor(ColorId.background), xgramCallsFraction());
  }

  /** XGram M3E: Chats or Calls without an animation (the toolbar's tab screen goes back to that page). */
  public void xgramSetPageSilently (boolean calls) {
    if (getViewPager() == null || hasFolders()) {
      return;
    }
    int position = calls ? POSITION_CALLS : POSITION_CHATS;
    if (getCurrentPagerItemPosition() != position) {
      setCurrentPagerPosition(position, false);
    }
  }

  @Override
  public void onPageSelected (int position, int actualPosition) {
    com.fahadali.xgram.appearance.XgramMotion.notifyActive(getCurrentPagerItem());
    xgramRailPageChanged(); // XGram M3E: the app bar title and the rail's pill follow the page
    if (!inSearchMode()) {
      composeWrap.show();
      if (xgramNavBar != null) {
        xgramNavBar.setScrollHidden(false); // XGram: another page brings the bottom navigation bar back, as the FAB
      }
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar != null) {
        toolbar.setScrollHidden(false); // XGram M3E: and the floating navigation toolbar
      }
    }
    if (hasFolders()) {
      if (composeWrap.getMainButtonId() != R.id.btn_float_compose) {
        composeWrap.replaceMainButton(R.id.btn_float_compose, R.drawable.baseline_create_24);
      }
      xgramSetFabLabel(R.string.XgramFabNewChat);
      return;
    }
    switch (position) {
      case POSITION_CHATS: {
        composeWrap.replaceMainButton(R.id.btn_float_compose, R.drawable.baseline_create_24);
        xgramSetFabLabel(R.string.XgramFabNewChat);
        break;
      }
      case POSITION_CALLS: {
        composeWrap.replaceMainButton(R.id.btn_float_call, R.drawable.baseline_phone_24);
        xgramSetFabLabel(R.string.Call);
        break;
      }
      case POSITION_PEOPLE: {
        composeWrap.replaceMainButton(R.id.btn_float_addContact, R.drawable.baseline_person_add_24);
        xgramSetFabLabel(0);
        break;
      }
    }
    context.notifyBackPressAvailabilityChanged();
  }

  /** XGram M3E: the extended FAB's label, the page's own action ("New chat", "Call"); 0 for a plain FAB. */
  private @StringRes int xgramFabLabelRes = R.string.XgramFabNewChat;

  private void xgramSetFabLabel (@StringRes int labelRes) {
    if (composeWrap == null || !com.fahadali.xgram.appearance.XgramExpressive.isEnabled() || xgramFabLabelRes == labelRes) {
      return;
    }
    xgramFabLabelRes = labelRes;
    composeWrap.xgramSetExtendedLabel(labelRes != 0 ? Lang.getString(labelRes) : null);
    com.fahadali.xgram.appearance.XgramMotion.notifyActive(getCurrentPagerItem()); // the page's lift again: extended at its top
    if (xgramToolbar() != null) {
      checkComposeWrapPaddings(); // XGram M3E: a wider or narrower FAB may fit beside the floating toolbar, or not
    }
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (needUpdatePagerSections) {
      needUpdatePagerSections = false;
      updatePagerSections(true);
      if (headerCell != null) {
        ViewUtils.runJustBeforeBeingDrawn(headerCell.getView(), () ->
          headerCell.getTopView().updateAnchorPosition(false)
        );
      }
    }
    if (headerView != null) {
      headerView.updateLockButton(getMenuId());
    }
    checkTabs();
    checkMenu();
    checkMargins();
  }

  private void checkMargins () {
    checkPagerMargins();
    checkHeaderMargins();
    checkComposeWrapPaddings();
  }

  private void checkComposeWrapPaddings () {
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    if (composeWrap != null && toolbar != null) {
      xgramPlaceFabByToolbar(toolbar);
    } else if (composeWrap != null) {
      // XGram: the FAB floats above the bottom navigation bar (XgramNavBar), which floats above the folder tabs
      int paddingBottom = xgramBottomBarsInset() + xgramNavBarRoom();
      composeWrap.setPadding(0, composeWrap.getPaddingTop(), 0, paddingBottom);
      composeWrap.setClipToPadding(paddingBottom == 0);
    }
    if (xgramNavBar != null) {
      xgramNavBar.setBottomOffset(xgramBottomBarsInset()); // XGram
    }
  }

  /**
   * XGram M3E: the compose button with the floating navigation toolbar (XgramNavToolbar). Above it, 16dp over the
   * pill: at its usual end margin from the window's edge whatever the toolbar's width (New chat button: At the side),
   * or end-aligned with the profile button so the collapsed FAB stands right over it (With the navigation bar). On a
   * wide and low window where the extended FAB clears the toolbar by 16dp at its usual end margin, on the toolbar's
   * row instead (either way). The pages keep the room it takes (xgramToolbarPagesRoom).
   */
  private void xgramPlaceFabByToolbar (com.fahadali.xgram.layout.XgramNavToolbar toolbar) {
    final int margin = composeWrap.xgramFabMarginPx();
    final int fabSize = xgramFabSizePx(margin);
    final int endInset = toolbar.groupEndInsetPx();
    final boolean inline = endInset > margin + xgramFabExtendedWidthPx() + Screen.dp(16f);
    int paddingBottom, end = 0;
    if (inline) {
      // Centred on the pill's row: 16 + 32dp above the navigation bar
      paddingBottom = Math.max(0, xgramBottomBarsInset() + Screen.dp(48f) - fabSize / 2 - margin);
    } else {
      paddingBottom = xgramBottomBarsInset() + com.fahadali.xgram.layout.XgramNavToolbar.room();
      if (!xgramFabAtSide()) {
        end = Math.max(0, endInset - margin);
      }
    }
    final boolean rtl = Lang.rtl();
    composeWrap.setPadding(rtl ? end : 0, composeWrap.getPaddingTop(), rtl ? 0 : end, paddingBottom);
    composeWrap.setClipToPadding(paddingBottom == 0);
    toolbar.setInline(inline);
    xgramSetNavBarRoom(xgramToolbarPagesRoom(inline)); // a FAB that stays above the rows: the last one ends above it
  }

  /** XGram M3E: the FAB's size for its end margin: the Medium FAB (80dp) keeps 24dp from the edges, the FAB 16dp. */
  private static int xgramFabSizePx (int marginPx) {
    return Screen.dp(marginPx >= Screen.dp(24f) ? 80f : 56f);
  }

  /** XGram M3E: New chat button: At the side (the default), not With the navigation bar. */
  private static boolean xgramFabAtSide () {
    return com.fahadali.xgram.XgramSettings.instance().navFab() == com.fahadali.xgram.XgramSettings.NAV_FAB_SIDE;
  }

  /**
   * XGram M3E: the floating navigation toolbar shows here and the compose button is at the side: it stays where it is
   * when the toolbar slides away on scroll (it still shrinks to the plain FAB once the list leaves its top).
   */
  private boolean xgramFabStaysOnScroll () {
    return xgramFabAtSide() && xgramToolbar() != null;
  }

  /**
   * XGram M3E: what the pages keep clear at the bottom for the floating navigation toolbar: its band, so their last row
   * ends where it starts; with the compose button at the side above the toolbar (it stays when the toolbar hides), the
   * FAB too, so it never covers the last row. On the toolbar's row (inline) or hidden (Hide the compose button) the
   * band is enough.
   */
  private int xgramToolbarPagesRoom (boolean inline) {
    int room = com.fahadali.xgram.layout.XgramNavToolbar.listRoom();
    if (!inline && composeWrap != null && composeWrap.getVisibility() != View.GONE && xgramFabAtSide()) {
      final int margin = composeWrap.xgramFabMarginPx();
      room = Math.max(room, com.fahadali.xgram.layout.XgramNavToolbar.room() + margin + xgramFabSizePx(margin));
    }
    return room;
  }

  /** XGram M3E: a FAB hidden by a scroll comes back once it stays on scroll (the option, or the toolbar came). */
  private void xgramShowFabIfStays () {
    if (composeWrap != null && xgramFabStaysOnScroll() && !xgramSearching && !xgramSelecting && !inSearchMode() && !inSelectMode()) {
      composeWrap.show(); // nothing when it is shown, or held back (forceHide)
    }
  }

  /**
   * XGram M3E: the extended FAB's full width ("New chat", "Call"), also while it is collapsed. A hidden FAB (Hide the
   * compose button) counts as the usual "New chat" one, so the toolbar's band does not depend on it.
   */
  private int xgramFabExtendedWidthPx () {
    if (composeWrap != null && composeWrap.getVisibility() != View.GONE) {
      for (int i = composeWrap.getChildCount() - 1; i >= 0; i--) {
        View child = composeWrap.getChildAt(i);
        if (child instanceof org.thunderdog.challegram.widget.CircleButton) {
          return ((org.thunderdog.challegram.widget.CircleButton) child).xgramLayoutWidth();
        }
      }
    }
    return Screen.dp(121f);
  }

  /** XGram M3E: the floating navigation toolbar moved its group (a new width, tabs, profile button): the FAB follows. */
  public void xgramOnToolbarLayout () {
    if (!isDestroyed()) {
      checkComposeWrapPaddings();
    }
  }

  private void checkPagerMargins () {
    if (!Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL && displayTabsAtBottom()) {
      Views.setBottomMargin(getViewPager(), getHeaderHeight());
      // XGram M3E: the floating update snackbar does not push the content up
      Views.setBottomMargin(pagerWrap, updateSnackBar != null && xgramSnackPushes() ? Math.round(updateSnackBar.getHeight() * updateSnackBar.getVisibilityFactor()) : 0); // FIXME
    } else {
      Views.setBottomMargin(getViewPager(), 0);
      Views.setBottomMargin(pagerWrap, 0);
    }
  }

  private void checkHeaderMargins () {
    if (headerCell == null)
      return;
    ViewPagerHeaderViewCompact headerCellView = ((ViewPagerHeaderViewCompact) headerCell.getView());
    RecyclerView recyclerView = headerCellView.getRecyclerView();
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) recyclerView.getLayoutParams();
    if (params.rightMargin != 0 || params.leftMargin != 0) {
      params.leftMargin = 0;
      params.rightMargin = 0;
      recyclerView.setLayoutParams(params);
    }
    int paddingLeft, paddingRight;
    if (displayTabsAtBottom()) {
      paddingLeft = paddingRight = 0;
      recyclerView.setPadding(0, 0, 0, 0);
    } else {
      recyclerView.setClipToPadding(false);
      recyclerView.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);
      int menuWidth = Screen.dp(44f);
      if (Passcode.instance().isEnabled()) {
        menuWidth += Screen.dp(48f);
      }
      if (xgramGhostButton != null && xgramGhostButton.getVisibility() == View.VISIBLE) {
        menuWidth += Screen.dp(49f); // XGram: the ghost mode button, or the last folder tab slides under it
      }
      if (xgramActivityButton != null && xgramActivityButton.getVisibility() == View.VISIBLE) {
        menuWidth += Screen.dp(49f); // XGram: Activity's bell
      }
      if (xgramHidesDrawer()) {
        menuWidth += Screen.dp(49f); // XGram M3E: the ⋮ that has the side menu's items
      }
      // XGram M3E: no ☰ while the navigation rail shows or the side menu is hidden: the first folder tab lines up with
      // the list (4 + 12dp)
      int navWidth = Screen.dp(xgramNoMenuButton() ? 4f : 44f);
      if (Lang.rtl()) {
        paddingLeft = menuWidth;
        paddingRight = navWidth;
      } else {
        paddingLeft = navWidth;
        paddingRight = menuWidth;
      }
    }
    if (headerCellView.getPaddingLeft() != paddingLeft || headerCellView.getPaddingRight() != paddingRight) {
      headerCellView.setPadding(paddingLeft, headerCellView.getPaddingTop(), paddingRight, headerCellView.getPaddingBottom());
    }
  }

  // XGram M3E: the connection in the app instead of over the status bar: a pill at the top of the chat list and, when
  // offline for 3 s or stuck connecting, one snackbar per episode (gone on connect, or for good once swiped away).

  private @Nullable com.fahadali.xgram.appearance.XgramConnectionPill xgramConnPill;
  private @Nullable SnackBar xgramConnSnackBar;
  private int xgramConnSnackState;
  private boolean xgramConnSnackDismissed, xgramSearching, xgramSelecting;
  private final Runnable xgramConnSnackCheck = this::xgramConnCheckSnackBar;
  private final com.fahadali.xgram.appearance.XgramConnection.Listener xgramConnListener = (state, text, animated) -> {
    if (xgramConnPill != null) {
      xgramConnPill.setDisplay(state, text, animated);
    }
    xgramConnCheckSnackBar();
  };

  private void xgramConnSetup () {
    if (!(pagerWrap instanceof com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout)) {
      return;
    }
    xgramConnPill = com.fahadali.xgram.appearance.XgramConnectionPill.attach(pagerWrap);
    addThemeInvalidateListener(xgramConnPill); // a theme switch redraws it in the new colors
    xgramConnSetupOffset(xgramConnPill);
    com.fahadali.xgram.appearance.XgramConnection conn = com.fahadali.xgram.appearance.XgramConnection.instance();
    conn.addListener(xgramConnListener);
    xgramConnPill.setDisplay(conn.getState(), conn.getText(), false);
    if (composeWrap != null) {
      composeWrap.xgramOnToggle = () -> {
        xgramConnUpdateSuppressed();
        xgramUpdateNavBarHidden(); // XGram M3E: the floating navigation toolbar steps aside while the FAB menu is open
      };
    }
    xgramConnCheckSnackBar();
  }

  /**
   * While the pill is in, the chat list moves down by its room (pill height and 8dp above and below) instead of being
   * covered, following the pill's own spring. Only the pager's translation changes: no padding or layout per frame and no
   * layer (the list keeps changing while it moves). The strip it uncovers takes the app bar color, under the pill: it is
   * pagerWrap's background, so the order of pagerWrap's children (the folder menu goes in right above the pager) stays.
   */
  private void xgramConnSetupOffset (com.fahadali.xgram.appearance.XgramConnectionPill pill) {
    final View pagerView = getViewPager();
    final int reserved = pill.getReservedHeight();
    final android.graphics.drawable.Drawable band = new android.graphics.drawable.Drawable() {
      @Override
      public void draw (@NonNull android.graphics.Canvas c) {
        final int top = pagerView.getTop();
        final float bottom = top + pagerView.getTranslationY();
        if (bottom > top) {
          c.drawRect(getBounds().left, top, getBounds().right, bottom, Paints.fillingPaint(Theme.getColor(ColorId.headerBackground)));
        }
      }

      @Override
      public void setAlpha (int alpha) { }

      @Override
      public void setColorFilter (@Nullable android.graphics.ColorFilter colorFilter) { }

      @Override
      public int getOpacity () {
        return android.graphics.PixelFormat.TRANSLUCENT;
      }
    };
    pagerWrap.setBackground(band); // nothing is drawn while the pill is out (a theme switch invalidates pagerWrap)
    pill.setOffsetListener(offset -> {
      final float translationY = Math.round(offset * reserved);
      if (pagerView.getTranslationY() != translationY) {
        pagerView.setTranslationY(translationY);
        band.invalidateSelf();
        pagerWrap.invalidate(); // the container's top corners are drawn by pagerWrap at the pager's moved top
      }
      // The moved pager keeps its height, so its bottom goes past the screen: the pages scroll that much further
      // while the pill is in (one relayout as it starts to come in, one once it is gone, none per frame)
      xgramSetPillRoom(offset > 0f ? reserved : 0);
    });
  }

  /** Bottom padding the pages add while the connection pill is in (the pager is moved down by it). */
  private int xgramPillRoom;

  private void xgramSetPillRoom (int px) {
    if (xgramPillRoom == px) {
      return;
    }
    xgramPillRoom = px;
    xgramApplyPageRooms();
  }

  /** XGram: bottom padding the pages add for the bottom navigation bar (XgramNavBar), so their last row clears it. */
  private int xgramPagesNavBarRoom;

  private void xgramSetNavBarRoom (int px) {
    if (xgramPagesNavBarRoom == px) {
      return;
    }
    xgramPagesNavBarRoom = px;
    xgramApplyPageRooms();
  }

  private void xgramApplyPageRooms () {
    SparseArrayCompat<ViewController<?>> controllers = getAllCachedControllers();
    if (controllers != null) {
      for (int i = 0; i < controllers.size(); i++) {
        xgramApplyPillRoom(controllers.valueAt(i));
      }
    }
    xgramApplyPillRoom(pendingChatsController);
  }

  /** The connection pill's room and the bottom navigation bar's, both as the pages' extra bottom padding. */
  private void xgramApplyPillRoom (@Nullable ViewController<?> c) {
    final int room = xgramPillRoom + xgramPagesNavBarRoom;
    if (c instanceof ChatsController) {
      ((ChatsController) c).xgramSetPillRoom(room);
    } else if (c instanceof CallListController) {
      ((CallListController) c).xgramSetPillRoom(room);
    }
  }

  private void xgramConnDestroy () {
    if (xgramConnPill != null) {
      com.fahadali.xgram.appearance.XgramConnection.instance().removeListener(xgramConnListener);
      UI.cancel(xgramConnSnackCheck);
    }
  }

  private void xgramConnUpdateSuppressed () {
    if (xgramConnPill != null) {
      xgramConnPill.setSuppressed(xgramSearching || xgramSelecting || xgramFolderMenu || (composeWrap != null && composeWrap.xgramIsOpen()), true);
    }
  }

  /** The folder tab's filter menu is open (press and drag on a tab). */
  private boolean xgramFolderMenu;

  /** XGram M3E: how far the FAB goes up for a floating snackbar: 8dp above it (it floats BOTTOM_GAP_DP above the same inset). */
  private float xgramFabLiftFor (SnackBar v) {
    int fabMargin = composeWrap != null ? composeWrap.xgramFabMarginPx() : Screen.dp(16f);
    return v.getMeasuredHeight() + Screen.dp(com.fahadali.xgram.appearance.XgramSnackbar.BOTTOM_GAP_DP + 8f) - fabMargin;
  }

  /** The connection snackbar swiped away: it goes at once, the FAB springs back down instead of jumping. */
  private @Nullable SnackBar xgramConnSwiped;
  private boolean xgramFabSettling;

  private void xgramSettleFab () {
    if (composeWrap == null || updateSnackBar != null || composeWrap.getTranslationY() == 0f) {
      return;
    }
    xgramFabSettling = true;
    composeWrap.animate().cancel();
    composeWrap.animate()
      .translationY(0f)
      .setInterpolator(com.fahadali.xgram.appearance.XgramMotion.interpolator(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT))
      .setDuration(com.fahadali.xgram.appearance.XgramMotion.duration(com.fahadali.xgram.appearance.XgramExpressive.SPATIAL_DEFAULT))
      .withEndAction(() -> xgramFabSettling = false)
      .start();
  }

  /** A snackbar moves the FAB again: the spring back from a swiped one stops where it is. */
  private void xgramStopFabSettle () {
    if (xgramFabSettling) {
      xgramFabSettling = false;
      if (composeWrap != null) {
        composeWrap.animate().cancel();
      }
    }
  }

  /** Where the floating snackbars sit: above the navigation bar, the bottom folder tabs and the bottom navigation bar. */
  private int xgramConnSnackInset () {
    // XGram M3E: above the floating navigation toolbar, the floating snackbar (12dp gap) ends where its band starts
    return xgramBottomBarsInset() + xgramNavBarRoom() + (xgramToolbar() != null ? Screen.dp(4f) : 0);
  }

  /** XGram: the navigation bar and, when the folder tabs are at the bottom, their bar. */
  private int xgramBottomBarsInset () {
    return extraBottomInsetWithoutIme + (displayTabsAtBottom() ? getHeaderHeight() : 0);
  }

  /**
   * XGram: the update snackbar's bottom padding. It floats (above the bottom folder tabs and the bottom navigation
   * bar) with Expressive design or while the bottom navigation bar shows; otherwise it sits on the navigation bar and
   * pushes the bottom folder tabs and the list up, as in Telegram X.
   */
  private int xgramUpdateSnackInset () {
    return xgramSnackPushes() ? extraBottomInsetWithoutIme : xgramConnSnackInset();
  }

  /** XGram: the classic update snackbar pushes the bottom folder tabs and the list up (not over the bottom bar). */
  private boolean xgramSnackPushes () {
    return !com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && xgramNavBar == null;
  }

  private void xgramConnCheckSnackBar () {
    if (xgramConnPill == null) {
      return;
    }
    UI.cancel(xgramConnSnackCheck);
    final com.fahadali.xgram.appearance.XgramConnection conn = com.fahadali.xgram.appearance.XgramConnection.instance();
    final int state = conn.getState();
    if (!com.fahadali.xgram.appearance.XgramConnection.isProblem(state)) {
      xgramConnSnackDismissed = false;
      xgramConnHideSnackBar();
      return;
    }
    int want = 0;
    if (state == com.fahadali.xgram.appearance.XgramConnection.STUCK) {
      want = state;
    } else if (state == com.fahadali.xgram.appearance.XgramConnection.OFFLINE) {
      long wait = conn.getStateSince() + 3000L - android.os.SystemClock.uptimeMillis();
      if (wait > 0) {
        UI.post(xgramConnSnackCheck, wait);
      } else {
        want = state;
      }
    }
    if (want == 0 || xgramConnSnackDismissed || !isFocused() || updateSnackBar != null || isDestroyed()) {
      return; // PROGRESS keeps a snackbar that is already up: at most one per episode
    }
    if (xgramConnSnackBar == null) {
      SnackBar bar = new SnackBar(context);
      bar.setCallback(new SnackBar.Callback() {
        @Override
        public void onSnackBarTransition (SnackBar v, float factor) {
          // The update snackbar moves the FAB while it is up; a swiped one leaves it to its spring back
          if (composeWrap != null && updateSnackBar == null && v != xgramConnSwiped) {
            xgramStopFabSettle();
            composeWrap.setTranslationY(-xgramFabLiftFor(v) * factor); // the same lift as for the update snackbar
          }
        }

        @Override
        public void onDestroySnackBar (SnackBar v) {
          if (composeWrap != null && updateSnackBar == null && v != xgramConnSwiped) {
            composeWrap.setTranslationY(0f);
          }
          if (xgramConnSnackBar == v) {
            xgramConnRemoveSnackBar();
          }
        }
      });
      bar.addThemeListeners(this);
      Views.setPaddingBottom(bar, xgramConnSnackInset());
      mainWrap.addView(bar);
      com.fahadali.xgram.appearance.XgramConnectionPill.attachSwipeDismiss(bar, () -> {
        if (xgramConnSnackBar == bar) { // not one already gone on connect (that would hold back the next episode's)
          xgramConnSnackDismissed = true;
          xgramConnSwiped = bar;
          xgramSettleFab();
          bar.dismissSnackBar(false);
          xgramConnRemoveSnackBar();
        }
      });
      xgramConnSwiped = null;
      xgramConnSnackBar = bar;
      xgramConnSnackState = 0;
    }
    if (xgramConnSnackState != want) {
      xgramConnSnackState = want;
      if (want == com.fahadali.xgram.appearance.XgramConnection.OFFLINE) {
        xgramConnSnackBar.setText(Lang.getString(R.string.XgramNetworkOffline));
        xgramConnSnackBar.setAction(Lang.getString(R.string.XgramNetworkSettings), () -> {
          xgramConnSnackDismissed = true;
          com.fahadali.xgram.appearance.XgramConnectionPill.openInternetPanel(context);
        }, true);
      } else {
        xgramConnSnackBar.setText(Lang.getString(R.string.XgramNetworkCantConnect));
        xgramConnSnackBar.setAction(Lang.getString(R.string.Proxy), () -> {
          xgramConnSnackDismissed = true;
          navigateTo(new SettingsProxyController(context, tdlib));
        }, true);
      }
    }
    xgramConnSnackBar.showSnackBar(true);
  }

  private void xgramConnHideSnackBar () {
    xgramConnSnackState = 0;
    if (xgramConnSnackBar != null) {
      if (isFocused() && xgramConnSnackBar.getVisibilityFactor() > 0f) {
        xgramConnSnackBar.dismissSnackBar(true);
      } else {
        xgramConnSnackBar.dismissSnackBar(false);
        xgramConnRemoveSnackBar();
      }
    }
  }

  private void xgramConnRemoveSnackBar () {
    SnackBar bar = xgramConnSnackBar;
    if (bar != null) {
      xgramConnSnackBar = null;
      xgramConnSnackState = 0;
      mainWrap.removeView(bar);
      bar.removeThemeListeners(this);
      if (composeWrap != null && updateSnackBar == null && bar != xgramConnSwiped) {
        composeWrap.setTranslationY(0f);
      }
    }
  }

  private SnackBar updateSnackBar;

  private void onAppUpdateAvailable (boolean isApk, boolean immediate) {
    if (updateSnackBar == null) {
      updateSnackBar = new SnackBar(context);
      // XGram M3E: it floats above the bottom folder tabs as the connection snackbar does (see onBottomInsetChanged)
      Views.setPaddingBottom(updateSnackBar, xgramUpdateSnackInset());
      updateSnackBar.setCallback(new SnackBar.Callback() {
        @Override
        public void onSnackBarTransition (SnackBar v, float factor) {
          if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
            // XGram M3E: the floating snackbar leaves the bottom bar and the list where they are: only the FAB makes
            // room, 8dp above it, as for the connection snackbar
            xgramStopFabSettle();
            composeWrap.setTranslationY(-xgramFabLiftFor(v) * factor);
            return;
          }
          if (xgramNavBar != null) {
            // XGram: above the bottom navigation bar (see xgramUpdateSnackInset) it leaves the folder tabs and the
            // list where they are; the FAB, already above the bar, makes room for the snackbar itself
            composeWrap.setTranslationY(-Math.max(0, v.getMeasuredHeight() - v.getPaddingBottom()) * factor);
            return;
          }
          float offsetBySnackBar = v.getMeasuredHeight() * factor;
          composeWrap.setTranslationY(-offsetBySnackBar);
          boolean tabsAtBottom = displayTabsAtBottom();
          if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
            bottomBarOffsetBySnackBar = -offsetBySnackBar;
            invalidateBottomBarOffset();
          } else if (tabsAtBottom) {
            Views.setBottomMargin(pagerWrap, Math.round(offsetBySnackBar)); // FIXME
          }
          if (tabsAtBottom) {
            updateBottomBarMargins();
          }
        }

        @Override
        public void onDestroySnackBar (SnackBar v) {
          if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
            bottomBarOffsetBySnackBar = 0;
            invalidateBottomBarOffset();
          } else {
            Views.setBottomMargin(pagerWrap, 0); // FIXME
          }
          if (updateSnackBar == v) {
            mainWrap.removeView(updateSnackBar);
            updateSnackBar.removeThemeListeners(MainController.this);
            updateSnackBar = null;
          }
        }
      });
      updateSnackBar.addThemeListeners(this);
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram M3E: XGram's own words, sentence case ("XGram 1.2.3 is ready to install"), never "Telegram X"
        updateSnackBar.setText(com.fahadali.xgram.appearance.XgramR6B2Chat.updateReadyText(context().appUpdater().displayVersion()));
      } else {
        updateSnackBar.setText(Lang.getString(R.string.AppUpdateReady));
      }
      mainWrap.addView(updateSnackBar);
      xgramConnHideSnackBar(); // XGram M3E: one snackbar at a time, the update one wins
    }
    updateSnackBar.setAction(Lang.getString(isApk ? R.string.AppUpdateInstall : R.string.AppUpdateRestart), context().appUpdater()::installUpdate, !isApk);
    updateSnackBar.showSnackBar(!immediate && isFocused());
  }

  @Override
  public void onAppUpdateStateChanged (int state, int oldState, boolean isApk) {
    if (state == AppUpdater.State.READY_TO_INSTALL) {
      onAppUpdateAvailable(isApk, false);
    } else if (updateSnackBar != null) {
      updateSnackBar.dismissSnackBar(isFocused());
    }
  }

  private final com.fahadali.xgram.appearance.XgramMotion.LiftListener xgramFabLift = (c, lift, collapse) -> {
    if (composeWrap != null && c == getCurrentPagerItem()) {
      composeWrap.xgramSetExtended(lift < .5f);
    }
  };

  @Override
  public void destroy () {
    super.destroy();
    com.fahadali.xgram.appearance.XgramMotion.removeLiftListener(xgramFabLift); // XGram: extended FAB
    com.fahadali.xgram.XgramSettings.instance().removeListener(xgramSettingsListener); // XGram
    com.fahadali.xgram.activity.XgramActivityBadge.instance().removeListener(xgramActivityBadgeListener); // XGram
    xgramConnDestroy(); // XGram
    if (xgramNavBar != null) {
      xgramNavBar.destroy(); // XGram: its badge and theme listeners go
    }
    tdlib.listeners().removeOptionListener(this);
    context().appUpdater().removeListener(this);
    tdlib.context().global().removeCountersListener(this);
    Settings.instance().removeChatFolderSettingsListener(this);
    tdlib.listeners().unsubscribeFromChatFoldersUpdates(this);
    if (chatListUnreadCountListener != null) {
      tdlib.listeners().removeTotalChatCounterListener(chatListUnreadCountListener);
      chatListUnreadCountListener = null;
    }
    if (defaultCounterListener != null) {
      tdlib.listeners().removeTotalChatCounterListener(defaultCounterListener);
      defaultCounterListener = null;
      defaultMainItem = null;
    }
    if (chatListPositionListener != null) {
      tdlib.settings().removeChatListPositionListener(chatListPositionListener);
      chatListPositionListener = null;
    }
  }

  private void showSuggestions () {
    if (!UI.inUiThread())
      throw new IllegalStateException();
    if (showLanguageSuggestion()) {
      return;
    }
    if (showEmojiUpdateSuggestion()) {
      return;
    }
    if (showFoldersSetupSuggestion()) {
      return;
    }
  }

  private TdApi.LanguagePackInfo loadingLanguagePack;

  private boolean showLanguageSuggestion () {
    String languagePackId = tdlib.suggestedLanguagePackId();
    TdApi.LanguagePackInfo languagePack = Settings.instance().suggestedLanguagePackInfo(languagePackId, tdlib);
    if (languagePack == null) {
      loadingLanguagePack = null;
      return false;
    }
    if (loadingLanguagePack != null && loadingLanguagePack.id.equals(languagePack.id))
      return true;
    loadingLanguagePack = languagePack;
    String[] keys = Lang.getKeys(new int[] {
      R.string.language_continueInLanguage,
      R.string.language_continueInLanguagePopupText,
      R.string.language_appliedLanguage
    });
    tdlib.getStrings(languagePack, keys, cloudStrings -> {
      boolean isBadLanguage = cloudStrings == null || cloudStrings.size() < keys.length;
      if (isBadLanguage) {
        Log.w("Suggested language is bad, ignoring. languagePackId:%s", languagePack.id);
        if (loadingLanguagePack != null && languagePack.id.equals(loadingLanguagePack.id))
          loadingLanguagePack = null;
        return;
      }
      tdlib.ui().post(() -> {
        if (!isDestroyed() && loadingLanguagePack != null && languagePack.id.equals(loadingLanguagePack.id)) {
          IntList ids = new IntList(3);
          StringList strings = new StringList(3);
          IntList colors = new IntList(3);
          IntList icons = new IntList(3);

          ids.append(R.id.btn_done);
          strings.append(TD.findOrdinary(cloudStrings, Lang.getResourceEntryName(R.string.language_continueInLanguage), () -> Lang.getString(R.string.language_continueInLanguage)));
          colors.append(OptionColor.BLUE);
          icons.append(R.drawable.baseline_check_24);

          ids.append(R.id.btn_cancel);
          icons.append(R.drawable.baseline_cancel_24);
          colors.append(OptionColor.NORMAL);
          strings.append(R.string.Cancel);

          ids.append(R.id.btn_languageSettings);
          strings.append(R.string.MoreLanguages);
          colors.append(OptionColor.NORMAL);
          icons.append(R.drawable.baseline_language_24);

          CharSequence text = Strings.buildMarkdown(this, TD.findOrdinary(cloudStrings, Lang.getResourceEntryName(R.string.language_continueInLanguagePopupText), () -> Lang.getString(R.string.language_continueInLanguagePopupText)), null);

          PopupLayout popupLayout = showOptions(text, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
            if (id == R.id.btn_done) {
              tdlib.applyLanguage(languagePack, applySuccess -> {
                if (applySuccess) {
                  UI.showToast(R.string.language_appliedLanguage, Toast.LENGTH_SHORT);
                  Settings.instance().setRecommendedLanguagePackId(languagePackId);
                }
              }, true);
            } else if (id == R.id.btn_cancel) {
              Settings.instance().setRecommendedLanguagePackId(languagePackId);
            } else if (id == R.id.btn_languageSettings) {
              Settings.instance().setRecommendedLanguagePackId(languagePackId);
              navigateTo(new SettingsLanguageController(context, tdlib));
            }
            return true;
          });
          if (popupLayout != null) {
            popupLayout.setDisableCancelOnTouchDown(true);
          }
          loadingLanguagePack = null;
        }
      });
    });
    return true;
  }

  private boolean showEmojiUpdateSuggestion () {
    Settings.EmojiPack emojiPack = Settings.instance().getOutdatedEmojiPack();
    if (emojiPack == null || com.fahadali.xgram.appearance.XgramSystemEmoji.isEnabled()) // XGram: no set update prompt while System emoji is on
      return false;
    tdlib.ui().postDelayed(() -> {
      if (!isFocused() || isDestroyed() || context.isPasscodeShowing())
        return;
      showOptions(Lang.getStringBold(R.string.EmojiSetUpdated, emojiPack.displayName), new int[] {R.id.btn_downloadFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.EmojiSetUpdate), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.BLUE, OptionColor.NORMAL}, new int[] {R.drawable.baseline_sync_24, R.drawable.baseline_cancel_24}, (v, id) -> {
        if (id == R.id.btn_downloadFile) {
          SettingsCloudEmojiController c = new SettingsCloudEmojiController(context, tdlib);
          c.setArguments(new SettingsCloudController.Args<>(emojiPack));
          navigateTo(c);
        } else if (id == R.id.btn_cancel) {
          Settings.instance().revokeOutdatedEmojiPack();
        }
        return true;
      });
    }, 100);
    return true;
  }

  private boolean foldersAlertShown;

  private boolean showFoldersSetupSuggestion () {
    if (isFocused() && hasFolders() && Settings.instance().hasPendingFeatureAddedNotification(FeatureAvailability.Feature.CHAT_FOLDERS) && !foldersAlertShown) {
      foldersAlertShown = true;
      ChatFoldersFeatureController c = new ChatFoldersFeatureController(context(), tdlib());
      c.show();
      c.setDismissListener(popup -> {
        if (!isNavigationAnimating()) {
          Settings.instance().revokeFeatureNotifications(FeatureAvailability.Feature.CHAT_FOLDERS);
          showFolderTooltip(ChatPosition.CHAT_LIST_MAIN, Lang.getString(R.string.HoldToEditChatFoldersHint));
        }
      });
      return true;
    }
    return false;
  }

  private CancellationSignal suggestionsSignal;

  @Override
  protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    if (suggestionsSignal != null) {
      suggestionsSignal.cancel();
      suggestionsSignal = null;
    }
    if (isFocused()) {
      CancellationSignal signal = new CancellationSignal();
      suggestionsSignal = signal;
      tdlib.awaitConnection(() -> {
        executeOnUiThreadOptional(() -> {
          if (isFocused() && !signal.isCanceled()) {
            signal.cancel();
            showSuggestions();
          }
        });
      });
    }
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    xgramUpdateGhostButton(); // XGram
    xgramConnCheckSnackBar(); // XGram M3E: a connection snackbar held back while another page was on top
    com.fahadali.xgram.XgramWhatsNewController.showIfNeeded(this); // XGram: once after an update with new features
    // FIXME check tdlib.isUnauthorized()
    tdlib.context().changePreferredAccountId(tdlib.id(), TdlibManager.SWITCH_REASON_NAVIGATION);
    if (UI.TEST_MODE == UI.TEST_MODE_USER) {
      UI.TEST_MODE = UI.TEST_MODE_NONE;
    }
    if (!oneShot) {
      oneShot = true;
      ((MainActivity) context()).getMessagesController(tdlib, true);
    }
    showAnnoyingAlertsForCompliance(false);
  }

  private boolean syncContactsInitiated;

  private void syncContacts (Runnable after) {
    if (!syncContactsInitiated) {
      tdlib.contacts().startSyncIfNeeded(context(), false, () -> {
        syncContactsInitiated = true;
        U.run(after);
      });
    } else {
      U.run(after);
    }
  }

  private void addStartupMarker () {
    if (Config.ENABLE_BASELINE_PROFILE_HOOKS) {
      tdlib.awaitConnection(() -> runOnUiThreadOptional(() -> {
        ViewGroup group = (ViewGroup) getWrapUnchecked();
        if (group.findViewById(R.id.startup_marker) == null) {
          group.addView(newStartupMarker());
        }
      }));
    }
  }

  private boolean notificationsRequested;

  private void showAnnoyingAlertsForCompliance (boolean fromAppResume) {
    if (Config.ENABLE_BASELINE_PROFILE_HOOKS) {
      addStartupMarker();
    }
    if (checkSyncAlert()) {
      return;
    }
    tdlib.checkDeadlocks(() -> runOnUiThreadOptional(() -> {
      if (isFocused() && context.getActivityState() == UI.State.RESUMED) {
        boolean needNotifications = fromAppResume || !notificationsRequested;
        if (needNotifications && !notificationsRequested) {
          notificationsRequested = true;
        }
        if (needNotifications && !context().permissions().requestPostNotifications(granted -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted) {
            tdlib.notifications().onNotificationPermissionGranted();
          }
          syncContacts(null);
        })) {
          syncContacts(null);
        }
      }
    }, null, 1000L));
  }

  @Override
  protected int getBackButton () {
    return xgramIsHiddenRoot() || xgramNoMenuButton() ? BackHeaderButton.TYPE_NONE : BackHeaderButton.TYPE_MENU;
  }

  // XGram: in the two-pane layout this chat list sits under the placeholder on the right, header included.
  private boolean xgramIsHiddenRoot () {
    com.fahadali.xgram.layout.XgramTwoPane twoPane = context.xgramTwoPane();
    return twoPane != null && twoPane.isHiddenRoot(this);
  }

  /**
   * XGram M3E: the navigation rail at the start of a wide window (XgramNavRail) is the side menu and has the Chats and
   * Calls tabs: no ☰, no drawer swipe, and a plain app bar title when the pages are Chats and Calls (no folders).
   * The compose button stays the chat list's own.
   */
  private boolean xgramRailShown () {
    return com.fahadali.xgram.layout.XgramTwoPane.isRailShown(context);
  }

  /**
   * XGram M3E: Hide the side menu (Navigation bar) while the floating navigation toolbar shows for this chat list: no
   * ☰, no edge swipe and no side menu from anywhere; its items are in the app bar's ⋮ (xgramShowSideMenuItems). With
   * the rail, two panes, the toolbar off or Expressive design off the side menu is as it was.
   */
  private boolean xgramHidesDrawer () {
    return com.fahadali.xgram.XgramSettings.instance().navHideDrawer() && xgramToolbar() != null;
  }

  /** XGram M3E: no ☰ on this chat list: the rail stands in for the side menu, or the toolbar's option hides it. */
  private boolean xgramNoMenuButton () {
    return xgramRailShown() || xgramHidesDrawer();
  }

  /**
   * XGram M3E: the app bar shows the page's title, not the Chats / Calls tabs (never on the hidden right pane root):
   * they are on the navigation rail, or on the bottom navigation bar (XgramNavBar, also in the classic look).
   */
  private boolean xgramRailTitle () {
    return (xgramRailShown() || xgramNavBar != null || xgramToolbarHasCallsTab()) && !hasFolders() && !xgramIsHiddenRoot();
  }

  /** XGram: the navigation rail came or went (XgramTwoPane re-applies the header for the ☰ and the tabs). */
  public void xgramOnNavRailChanged () {
    xgramUpdatePaneInset();
    checkHeaderMargins();
    xgramFitToggleHeader();
    xgramUpdateActivityButton(); // the rail may hold Activity: the bell goes, or comes back
  }

  /** XGram M3E: the header again (folder tabs came or went), for the navigation rail's (or bar's) title or tabs. */
  private void xgramReapplyHeader () {
    if ((xgramRailShown() || xgramNavBar != null || xgramToolbarHasCallsTab()) && headerView != null && isFocused() && !inTransformMode()) {
      headerView.setTitle(this);
    }
  }

  /** XGram M3E: the folder title at the top (folders at the bottom) starts 16dp in while there is no ☰. */
  private void xgramFitToggleHeader () {
    if (toggleHeaderView == null || !(toggleHeaderView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
      return;
    }
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) toggleHeaderView.getLayoutParams();
    boolean rail = xgramNoMenuButton(); // no ☰: the rail, or the side menu hidden with the floating toolbar
    int more = xgramHidesDrawer() ? Screen.dp(49f) : 0; // the ⋮ beside the search button
    int left = Lang.rtl() ? Screen.dp(56f) + more : Screen.dp(rail ? 16f : 56f);
    int right = Lang.rtl() ? Screen.dp(rail ? 16f : 68f) : Screen.dp(68f) + more;
    if (params.leftMargin != left || params.rightMargin != right) {
      params.leftMargin = left;
      params.rightMargin = right;
      toggleHeaderView.setLayoutParams(params);
    }
  }

  /**
   * XGram M3E: the page changed: the app bar title (Chats or Calls) and the navigation rail's pill (or the bottom
   * navigation bar's indicator) follow it.
   */
  private void xgramRailPageChanged () {
    if (!xgramRailShown() && !xgramHasBottomNav()) {
      return;
    }
    if (xgramRailTitle() && headerView != null) {
      headerView.xgramMorphTitle(getId(), getName(), com.fahadali.xgram.appearance.XgramTitleMorph.LEADING_NONE, true);
    }
    com.fahadali.xgram.layout.XgramTwoPane twoPane = context.xgramTwoPane();
    if (twoPane != null) {
      twoPane.onRailPageChanged(this);
    }
    if (xgramNavBar != null) {
      xgramNavBar.onPageChanged();
    }
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    if (toolbar != null) {
      toolbar.onPageChanged(); // XGram M3E: the floating navigation toolbar's selection
    }
  }

  // XGram: the floating navigation bar at the bottom of the chat list on narrow windows (XgramNavBar, Appearance >
  // Layout > Navigation bar). Chats and Calls are its pages, as with the navigation rail of wide windows; the ☰ and
  // the side menu stay.

  private @Nullable FrameLayoutFix xgramContentView;
  private @Nullable com.fahadali.xgram.layout.XgramNavBar xgramNavBar;
  /** Whether the header was last built with the bar (no Chats / Calls tabs) or without it. */
  private boolean xgramNavBarHeader;

  /** On a narrow window with one pane and no rail, for the chat list on screen (never the panes' own chat list). */
  private boolean xgramNeedNavBar () {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      return false; // XGram M3E: Expressive design has the floating navigation toolbar instead (XgramNavToolbar)
    }
    if (isDestroyed() || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !com.fahadali.xgram.XgramSettings.instance().bottomNav()) {
      return false; // (its state layers are ripples, its shadow an elevation)
    }
    if (xgramIsHiddenRoot() || com.fahadali.xgram.layout.XgramTwoPane.isPaneChatList(this)) {
      return false;
    }
    return com.fahadali.xgram.layout.XgramTwoPane.isNavBarWindow(context);
  }

  /**
   * XGram: the bottom navigation bar comes or goes (its setting, the window, the rail, two panes; XgramTwoPane#update
   * on every change) and follows the destinations, the page and the badges.
   */
  public void xgramUpdateNavBar () {
    if (xgramContentView == null || composeWrap == null) {
      return; // no view yet: onCreateView checks it
    }
    final boolean need = xgramNeedNavBar();
    boolean changed = false;
    if (need && xgramNavBar == null) {
      com.fahadali.xgram.layout.XgramNavBar bar = new com.fahadali.xgram.layout.XgramNavBar(context, this);
      bar.setBottomOffset(xgramBottomBarsInset());
      int index = xgramContentView.indexOfChild(composeWrap); // under the FAB and its menu
      bar.attach(xgramContentView, index != -1 ? index : xgramContentView.getChildCount());
      xgramNavBar = bar;
      changed = true;
    } else if (!need && xgramNavBar != null) {
      com.fahadali.xgram.layout.XgramNavBar bar = xgramNavBar;
      xgramNavBar = null;
      bar.destroy();
      xgramContentView.removeView(bar.view());
      changed = true;
    }
    if (changed) {
      xgramLayoutNavBar();
    }
    xgramSyncNavBarHeader();
    if (xgramNavBar != null) {
      xgramNavBar.setForceHidden(xgramNavBarForceHidden(), false);
      xgramNavBar.sync();
    }
    xgramUpdateActivityButton(); // the bar, the rail or the toolbar may hold Activity (or no longer)
  }

  /** Search, chat selection and the bottom folder tabs' menu take the bottom of the list: the bar steps aside. */
  private boolean xgramNavBarForceHidden () {
    return xgramSearching || xgramSelecting || (xgramFolderMenu && displayTabsAtBottom());
  }

  /** The FAB, the snackbars and the pages make room for the bar (or take it back); the bar sits on what is below. */
  private void xgramLayoutNavBar () {
    checkComposeWrapPaddings(); // places the bar too
    Views.setPaddingBottom(updateSnackBar, xgramUpdateSnackInset());
    if (xgramConnSnackBar != null) {
      Views.setPaddingBottom(xgramConnSnackBar, xgramConnSnackInset());
    }
    updateBottomBarMargins();
    checkPagerMargins();
    // XGram M3E: the pages' last row ends where the floating navigation toolbar's band starts (or above a FAB at the side)
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    xgramSetNavBarRoom(toolbar != null ? xgramToolbarPagesRoom(toolbar.isInline()) : xgramNavBarRoom());
  }

  /** What the FAB, the snackbars and the pages keep clear for the bottom navigation bar, in px. */
  private int xgramNavBarRoom () {
    if (xgramToolbar() != null) {
      return com.fahadali.xgram.layout.XgramNavToolbar.room(); // XGram M3E: the floating navigation toolbar
    }
    return xgramNavBar != null ? com.fahadali.xgram.layout.XgramNavBar.room() : 0;
  }

  // XGram M3E: the floating navigation toolbar of narrow windows (XgramNavToolbar, owned by XgramTwoPane above the
  // screens). Its tab screens (Contacts, Settings, the profile) open right above this chat list.

  /** The floating navigation toolbar, while it shows for this chat list. */
  private @Nullable com.fahadali.xgram.layout.XgramNavToolbar xgramToolbar () {
    com.fahadali.xgram.layout.XgramTwoPane twoPane = context.xgramTwoPane();
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = twoPane != null ? twoPane.toolbar() : null;
    return toolbar != null && toolbar.isRootOf(this) ? toolbar : null;
  }

  private boolean xgramToolbarShownHere () {
    return xgramToolbar() != null;
  }

  /** Calls is one of the toolbar's tabs: the Chats / Calls header tabs give way to the page's title. */
  private boolean xgramToolbarHasCallsTab () {
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    return toolbar != null && toolbar.hasTab(com.fahadali.xgram.layout.XgramNavToolbar.DEST_CALLS);
  }

  /** A bottom navigation of either kind: the classic bar or the floating toolbar. */
  private boolean xgramHasBottomNav () {
    return xgramNavBar != null || xgramToolbar() != null;
  }

  /**
   * XGram M3E: the floating navigation toolbar came, went or changed its tabs for this chat list: the folder tabs go
   * to the top (or back), the header shows the page's title or the Chats / Calls tabs, the FAB, the snackbars and the
   * pages make room.
   */
  public void xgramOnToolbarChanged () {
    if (isDestroyed() || pagerWrap == null) {
      return;
    }
    checkTabs();
    checkMenu();
    checkMargins();
    xgramFitToggleHeader(); // the ☰ may have come or gone with the side menu (Hide the side menu)
    xgramReapplyHeader();
    xgramSyncNavBarHeader();
    xgramLayoutNavBar();
    xgramUpdateNavBarHidden();
    xgramCloseHiddenDrawer();
    xgramShowFabIfStays(); // a FAB hidden by a scroll, now set to stay at the side
    xgramUpdateMoreBadge(false); // the ☰'s badge on the ⋮ (no photo on the toolbar), or not
    xgramUpdateActivityButton(); // an Activity tab: the bell goes, or comes back
  }

  /** XGram M3E: the chat list's reasons for the floating toolbar to step aside (XgramNavToolbar.FORCE_*). */
  private int xgramToolbarForceMask () {
    int mask = 0;
    if (xgramSearching) {
      mask |= com.fahadali.xgram.layout.XgramNavToolbar.FORCE_SEARCH;
    }
    if (xgramSelecting) {
      mask |= com.fahadali.xgram.layout.XgramNavToolbar.FORCE_SELECT;
    }
    if (xgramFolderMenu && displayTabsAtBottom()) {
      mask |= com.fahadali.xgram.layout.XgramNavToolbar.FORCE_FOLDER_MENU;
    }
    if (composeWrap != null && composeWrap.xgramIsOpen()) {
      mask |= com.fahadali.xgram.layout.XgramNavToolbar.FORCE_FAB_MENU;
    }
    return mask;
  }

  /**
   * The header again when the bar came or went since it was built: the Chats / Calls tabs, or the page's title. A
   * header built later (on the way back from a chat, or once search or selection ends) picks it up by itself.
   */
  private void xgramSyncNavBarHeader () {
    final boolean shown = xgramNavBar != null || xgramToolbarHasCallsTab();
    final boolean noDrawer = xgramHidesDrawer(); // XGram M3E: the ☰ goes and the ⋮ comes, or back
    if (xgramNavBarHeader == shown && xgramNoDrawerHeader == noDrawer) {
      return;
    }
    if (headerView == null || !isFocused() || inTransformMode()) {
      xgramNavBarHeader = shown;
      xgramNoDrawerHeader = noDrawer;
      return;
    }
    if (navigationController != null && navigationController.isAnimating()) {
      return; // the next stack change tries again
    }
    headerView.setTitle(this);
    xgramNavBarHeader = shown;
    xgramNoDrawerHeader = noDrawer;
  }

  /** XGram M3E: whether the header was last built with the side menu hidden (no ☰, the ⋮) or not. */
  private boolean xgramNoDrawerHeader;

  /** XGram M3E: the side menu hidden while it is open (it opened before the toolbar came for this chat list): it closes. */
  private void xgramCloseHiddenDrawer () {
    if (!xgramHidesDrawer()) {
      return;
    }
    org.thunderdog.challegram.navigation.DrawerController drawer = context.getDrawer();
    if (drawer != null && (drawer.isVisible() || drawer.getShowFactor() > 0f)) {
      drawer.close(0f, null);
    }
  }

  /** XGram M3E: New chat button or Hide the side menu changed on the Navigation bar page. */
  private void xgramOnToolbarOptionsChanged () {
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    if (!isDestroyed() && toolbar != null) {
      toolbar.refreshMenuBadge(); // the ☰'s badge on the photo while the side menu is hidden
      xgramOnToolbarChanged();
    }
  }

  /** The bar steps aside while search, chat selection or the bottom folder tabs' menu last (and comes back after). */
  private void xgramUpdateNavBarHidden () {
    if (xgramNavBar != null) {
      xgramNavBar.setForceHidden(xgramNavBarForceHidden(), isFocused());
    }
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    if (toolbar != null) {
      toolbar.setForceHidden(xgramToolbarForceMask(), isFocused()); // XGram M3E
    }
  }

  /** XGram M3E: Calls is one of this chat list's pages (no folders: Chats and Calls), for the navigation rail. */
  public boolean xgramHasCallsPage () {
    return !hasFolders();
  }

  /** XGram M3E: the Calls page is on screen. */
  public boolean xgramOnCallsPage () {
    return !hasFolders() && getViewPager() != null && getCurrentPagerItemPosition() == POSITION_CALLS;
  }

  /**
   * XGram M3E: the navigation rail's Chats or Calls, as a tap on their tab: another page switches to it, the page
   * shown scrolls to the top ({@code scrollIfShown}). With folders, Chats keeps the folder shown.
   */
  public void xgramShowRailPage (boolean calls, boolean scrollIfShown) {
    if (getViewPager() == null) {
      return;
    }
    int current = getCurrentPagerItemPosition();
    if (!calls && scrollIfShown && current == POSITION_CHATS && xgramHasRailFilter()) {
      // Chats again, on Chats, while a filter (Unread, Groups...) or the archive shows: back to all chats, as there are
      // no tabs to slide it off from here (a screen closed on the way keeps it). requestChatsFilter keeps the page.
      requestChatsFilter(FILTER_NONE, MAIN_PAGER_ITEM_ID);
      return;
    }
    int position = hasFolders() ? current : calls ? POSITION_CALLS : POSITION_CHATS;
    if (position != current || scrollIfShown) {
      onPagerItemClick(position);
    }
  }

  /** XGram M3E: the Chats page shows a filter or the archive (set from its tab, or from the rail's Chats). */
  private boolean xgramHasRailFilter () {
    return !hasFolders() && !useGlobalFilter() && (getSelectedFilter(MAIN_PAGER_ITEM_ID) != FILTER_NONE || menuNeedArchive);
  }

  /**
   * XGram M3E: long press on the navigation rail's Chats while there are no tabs (no folders): the filters of the
   * Chats tab's slide-off menu (Archive and Unread only when there are some, as there). False when it does not apply.
   */
  public boolean xgramShowRailFilters () {
    if (getViewPager() == null || hasFolders() || useGlobalFilter() || isDestroyed()) {
      return false;
    }
    final int selectedFilter = getSelectedFilter(MAIN_PAGER_ITEM_ID);
    final boolean needArchive = menuNeedArchive || (!xgramArchiveTabEnabled() && tdlib.hasArchivedChats());
    final boolean needUnread = selectedFilter == FILTER_UNREAD || tdlib.hasUnreadChats(menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN);
    IntList ids = new IntList(FILTERS.length);
    StringList strings = new StringList(FILTERS.length);
    IntList icons = new IntList(FILTERS.length);
    IntList colors = new IntList(FILTERS.length);
    for (int filter : FILTERS) {
      if ((filter == FILTER_ARCHIVE && !needArchive) || (filter == FILTER_UNREAD && !needUnread)) {
        continue;
      }
      boolean isSelected;
      if (filter == FILTER_NONE) {
        isSelected = selectedFilter == FILTER_NONE && !menuNeedArchive;
      } else if (filter == FILTER_ARCHIVE) {
        isSelected = menuNeedArchive;
      } else {
        isSelected = selectedFilter == filter;
      }
      ids.append(XGRAM_RAIL_FILTER_ID + filter);
      strings.append(getFilterName(filter));
      icons.append(getFilterIcon(filter, false));
      colors.append(isSelected ? OptionColor.BLUE : OptionColor.NORMAL);
    }
    showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
      int filter = id - XGRAM_RAIL_FILTER_ID;
      if (filter >= FILTER_NONE && filter <= FILTER_UNREAD && !isDestroyed()) {
        if (getCurrentPagerItemPosition() != POSITION_CHATS) {
          setCurrentPagerPosition(POSITION_CHATS, true);
        }
        requestChatsFilter(filter, MAIN_PAGER_ITEM_ID);
      }
      return true;
    });
    return true;
  }

  /** Option ids of {@link #xgramShowRailFilters}: this plus the filter (an option id of 0 would read as none). */
  private static final int XGRAM_RAIL_FILTER_ID = 1000;

  @Override
  protected int getSelectHeaderColorId () {
    // XGram M3E: the selection app bar stays on the app bar's layer, so the container corners and the side margins
    // under it (XgramM3Bars.CornerFrameLayout, also headerBackground) keep its tone
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? ColorId.headerBackground : super.getSelectHeaderColorId();
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    super.handleLanguagePackEvent(event, arg1);
    if (event == Lang.EVENT_PACK_CHANGED || event == Lang.EVENT_STRING_CHANGED) {
      // XGram M3E: the app bar title of the navigation rail (Chats, Calls) is a text title of getName(), not a tab
      if (xgramRailTitle() && headerView != null && !inTransformMode()) {
        headerView.xgramMorphTitle(getId(), getName(), com.fahadali.xgram.appearance.XgramTitleMorph.LEADING_NONE, false);
      }
      if (composeWrap != null && xgramFabLabelRes != 0 && com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        composeWrap.xgramSetExtendedLabel(Lang.getString(xgramFabLabelRes));
      }
    }
    if (xgramNavBar != null) {
      xgramNavBar.onLanguageChanged(); // XGram: its labels, and the order of its destinations for the text direction
    }
    com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    if (toolbar != null) {
      toolbar.onLanguageChanged(); // XGram M3E: its labels and the order of its tabs, fitted again
    }
  }

  /**
   * XGram: as the chat list pane of the two-pane layout (or the only one beside the navigation rail), the list keeps
   * clear of the screen edge or the rail, and the chat.
   */
  public void xgramUpdatePaneInset () {
    if (pagerWrap instanceof com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout) {
      boolean pane = com.fahadali.xgram.layout.XgramTwoPane.isPaneChatList(this) || (xgramRailShown() && !xgramIsHiddenRoot());
      ((com.fahadali.xgram.appearance.XgramM3Bars.CornerFrameLayout) pagerWrap).setSideInset(pane ? Screen.dp(com.fahadali.xgram.layout.XgramTwoPane.PANE_INSET_DP) : 0);
    }
  }

  @Override
  protected boolean useDrawer () {
    // XGram M3E: the rail's menu button opens the expanded rail instead; with the floating toolbar the side menu can be
    // hidden (DrawerController neither opens nor prepares for a screen without it)
    return !xgramNoMenuButton();
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    if (xgramNoMenuButton() && navigationController != null && navigationController.getStackSize() <= 1) {
      return false; // XGram M3E: nothing to slide in from the chat list while the rail shows or the side menu is hidden
    }
    if (!super.canSlideBackFrom(navigationController, x, y)) {
      return false;
    }
    if (headerCell != null) {
      int top, bottom;
      if (displayTabsAtBottom()) {
        top = Views.getLocationInWindow(headerCell.getView())[1];
        bottom = top + headerCell.getView().getHeight();
      } else {
        top = HeaderView.getTopOffset();
        bottom = HeaderView.getSize(true);
      }
      if (y < bottom && y >= top && x < headerCell.getView().getMeasuredWidth()) {
        return !((ViewPagerHeaderViewCompact) headerCell.getView()).canScrollLeft();
      }
    }
    return true;
  }

  @Override
  protected int getMenuButtonsWidth () {
    return 0; // disable header margins
  }

  public boolean showComposeWrap (ViewController<?> controller) {
    if (!inSearchMode() && (controller == null || getCurrentPagerItem() == controller)) {
      composeWrap.show();
      if (xgramNavBar != null) {
        xgramNavBar.setScrollHidden(false); // XGram: the bottom navigation bar comes back with the FAB
      }
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar != null) {
        toolbar.setScrollHidden(false); // XGram M3E: in the same frame as the FAB
      }
      return true;
    }
    return false;
  }

  private boolean showHideCompose (ViewController<?> controller, boolean show) {
    if (getCurrentPagerItem() == controller) {
      if (show) {
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          showBottomBar();
        }
        return showComposeWrap(null);
      } else {
        if (getCurrentPagerItemId() == MAIN_PAGER_ITEM_ID) {
          ChatsController c = findMainChatsController();
          if (c != null && c.isPullingArchive()) {
            return false;
          }
        }
        if (!xgramFabStaysOnScroll()) {
          composeWrap.hide(); // XGram M3E: a FAB at the side stays where it is while the floating toolbar slides away
        }
        if (xgramNavBar != null) {
          xgramNavBar.setScrollHidden(true); // XGram: scrolling down hides the bottom navigation bar with the FAB
        }
        com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
        if (toolbar != null) {
          toolbar.setScrollHidden(true); // XGram M3E: the floating navigation toolbar slides away (with a FAB set to go with it)
        }
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          hideBottomBar();
        }
        return true;
      }
    }
    return false;
  }

  // Controllers

  private static final int POSITION_CHATS = 0;
  private static final int POSITION_CALLS = 1;
  private static final int POSITION_PEOPLE = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FILTER_NONE, FILTER_ARCHIVE, FILTER_PRIVATE, FILTER_GROUPS, FILTER_CHANNELS, FILTER_BOTS, FILTER_UNREAD})
  private @interface Filter { }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FILTER_NONE, FILTER_PRIVATE, FILTER_GROUPS, FILTER_CHANNELS, FILTER_BOTS, FILTER_UNREAD, NO_PENDING_FILTER})
  private @interface PendingFilter { }

  private static final int FILTER_NONE = 0;
  private static final int FILTER_ARCHIVE = 1;
  private static final int FILTER_PRIVATE = 2;
  private static final int FILTER_GROUPS = 3;
  private static final int FILTER_CHANNELS = 4;
  private static final int FILTER_BOTS = 5;
  private static final int FILTER_UNREAD = 6;

  private static final int[] FILTERS = {
    FILTER_NONE,
    FILTER_ARCHIVE,
    FILTER_PRIVATE,
    FILTER_GROUPS,
    FILTER_CHANNELS,
    FILTER_BOTS,
    FILTER_UNREAD,
  };

  private static final int[] GLOBAL_FILTERS = {
    FILTER_NONE,
    FILTER_PRIVATE,
    FILTER_GROUPS,
    FILTER_CHANNELS,
    FILTER_BOTS,
    FILTER_UNREAD,
  };

  private boolean useGlobalFilter () {
    return displayTabsAtBottom();
  }

  private @Filter int getSelectedFilter (long pagerItemId) {
    if (useGlobalFilter() && globalFilter != FILTER_NONE) {
      return globalFilter;
    }
    return pagerChatListFilters.get(pagerItemId, FILTER_NONE);
  }

  private void setSelectedFilter (long pagerItemId, @Filter int filter) {
    if (filter != FILTER_NONE) {
      pagerChatListFilters.put(pagerItemId, filter);
    } else {
      pagerChatListFilters.delete(pagerItemId);
    }
  }

  private boolean hasSelectedFilters () {
    for (int i = 0; i < pagerChatListFilters.size(); i++) {
      if (pagerChatListFilters.valueAt(i) != FILTER_NONE) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected int getPagerItemCount () {
    return hasFolders() ? pagerChatLists.size() : 2;
  }

  protected long getPagerItemId (int position) {
    if (hasFolders()) {
      return getPagerItemId(pagerChatLists.get(position));
    }
    if (position == POSITION_CHATS) {
      return MAIN_PAGER_ITEM_ID;
    }
    return position;
  }

  private long getPagerItemId (TdApi.ChatList chatList) {
    switch (chatList.getConstructor()) {
      case TdApi.ChatListMain.CONSTRUCTOR:
        return MAIN_PAGER_ITEM_ID;
      case TdApi.ChatListArchive.CONSTRUCTOR:
        return ARCHIVE_PAGER_ITEM_ID;
      case TdApi.ChatListFolder.CONSTRUCTOR:
        return ((TdApi.ChatListFolder) chatList).chatFolderId;
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public boolean onPagerItemLongClick (int index) {
    if (hasFolders()) {
      TdApi.ChatList chatList = pagerChatLists.get(index);
      showChatListOptions(chatList);
      return true;
    } else if (Config.ENABLE_DELETE_CALL_HISTORY && index == POSITION_CALLS) {
      ViewController<?> controller = getCachedControllerForPosition(index);
      if (controller instanceof CallListController && ((CallListController) controller).hasRecentCalls()) {
        tdlib.ui().showClearCallHistoryOptions(this);
        return true;
      }
    }
    return false;
  }

  private ChatsController newChatsController (TdApi.ChatList chatList, @Filter int filter) {
    ChatsController chats = new ChatsController(this.context, tdlib).setParent(this);
    ChatFilter chatFilter;
    switch (filter) {
      case FILTER_NONE:
        chatFilter = null;
        break;
      case FILTER_UNREAD:
        chatFilter = ChatFilter.unreadFilter(tdlib);
        break;
      case FILTER_PRIVATE:
        chatFilter = ChatFilter.privateFilter(tdlib);
        break;
      case FILTER_GROUPS:
        chatFilter = ChatFilter.groupsFilter(tdlib);
        break;
      case FILTER_CHANNELS:
        chatFilter = ChatFilter.channelsFilter(tdlib);
        break;
      case FILTER_BOTS:
        chatFilter = ChatFilter.botsFilter(tdlib);
        break;
      case FILTER_ARCHIVE:
      default:
        throw new IllegalArgumentException();
    }
    if (chatFilter != null) {
      chats.setArguments(new ChatsController.Arguments(chatFilter).setTag(filter).setChatList(chatList).setIsBase(true));
    } else if (chatList != null) {
      chats.setArguments(new ChatsController.Arguments(chatList).setTag(filter).setIsBase(true));
    }
    return chats;
  }

  /** XGram: tab labels are uppercase in Telegram X, sentence case with Material 3 Expressive. */
  private static String xgramTabLabel (String label) {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled() ? label : Lang.uppercase(label);
  }

  private CharSequence getMenuSectionName (long pagerItemId, int pagerItemPosition, boolean hasFolders, @ChatFolderStyle int chatFolderStyle, boolean upperCase) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
      upperCase = false; // XGram: Material 3 tabs are in sentence case
    }
    int selectedFilter = getSelectedFilter(pagerItemId);
    boolean isMain = pagerItemId == MAIN_PAGER_ITEM_ID;
    boolean isArchive = pagerItemId == ARCHIVE_PAGER_ITEM_ID;
    if (!isMain && !isArchive) {
      throw new UnsupportedOperationException();
    }
    boolean showAsArchive = (isMain && menuNeedArchive) || isArchive;
    if (hasFolders) {
      String source;
      // XGram: optionally, "All chats" follows the chosen style like every folder (Telegram X shows it as a bare icon first).
      boolean iconOnly = chatFolderStyle == ChatFolderStyle.ICON_ONLY ||
        (isMain && pagerItemPosition == 0 && !com.fahadali.xgram.XgramSettings.instance().allChatsLabel());
      if (iconOnly) {
        source = "";
      } else if (showAsArchive) {
        source = Lang.getString(getArchiveSectionNameRes(FILTER_NONE, chatFolderStyle));
      } else {
        source = Lang.getString(getMainSectionNameRes(FILTER_NONE, /* hasFolders */ true));
      }
      if (upperCase) {
        source = Lang.uppercase(source);
      }
      if (useGlobalFilter() && selectedFilter == globalFilter || selectedFilter == FILTER_NONE) {
        return source;
      }
      return appendFilterIcon(source, selectedFilter);
    }
    String sectionName;
    if (showAsArchive) {
      sectionName = Lang.getString(getArchiveSectionNameRes(selectedFilter, chatFolderStyle));
    } else {
      sectionName = Lang.getString(getMainSectionNameRes(selectedFilter, hasFolders));
    }
    return upperCase ? Lang.uppercase(sectionName) : sectionName;
  }

  @StringRes
  private int getMainSectionNameRes (int selectedFilter, boolean hasFolders) {
    if (selectedFilter != FILTER_NONE) {
      return getFilterName(selectedFilter);
    }
    return hasFolders ? R.string.CategoryMain : R.string.Chats;
  }

  @StringRes
  private int getArchiveSectionNameRes (int selectedFilter, @ChatFolderStyle int chatFolderStyle) {
    if (chatFolderStyle == ChatFolderStyle.LABEL_ONLY) {
      switch (selectedFilter) {
        case FILTER_UNREAD:
          return R.string.CategoryArchiveUnread;
        case FILTER_PRIVATE:
          return R.string.CategoryArchivePrivate;
        case FILTER_GROUPS:
          return R.string.CategoryArchiveGroup;
        case FILTER_CHANNELS:
          return R.string.CategoryArchiveChannels;
        case FILTER_BOTS:
          return R.string.CategoryArchiveBots;
        case FILTER_NONE:
        case FILTER_ARCHIVE:
          return R.string.CategoryArchive;
        default:
          throw new UnsupportedOperationException("selectedFilter=" + selectedFilter);
      }
    }
    return selectedFilter == FILTER_NONE ? R.string.CategoryArchive : getFilterName(selectedFilter);
  }

  private CharSequence getFolderSectionName (long pagerItemId, CharSequence folderName, @ChatFolderStyle int chatFolderStyle) {
    int selectedFilter = getSelectedFilter(pagerItemId);
    CharSequence sectionName;
    if (selectedFilter != FILTER_NONE) {
      CharSequence source = chatFolderStyle == ChatFolderStyle.ICON_ONLY ? "" : folderName;
      if (useGlobalFilter() && selectedFilter == globalFilter) {
        sectionName = source;
      } else {
        sectionName = appendFilterIcon(source, selectedFilter);
      }
    } else {
      sectionName = chatFolderStyle == ChatFolderStyle.ICON_ONLY ? "" : folderName;
    }
    return Emoji.instance().replaceEmoji(sectionName);
  }

  @CheckResult
  private CharSequence appendFilterIcon (CharSequence source, @Filter int selectedFilter) {
    SpannableString string = new SpannableString(source + (StringUtils.isEmpty(source) ? "∇" : " ∇"));
    int filterIcon = getFilterVariantIcon(selectedFilter);
    string.setSpan(new IconSpan(filterIcon, ColorId.NONE), string.length() - 1, string.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return string;
  }

  private @StringRes int getGlobalFilterName (@Filter int filter) {
    return filter == FILTER_NONE ? R.string.Chats : getFilterName(filter);
  }

  private @StringRes int getFilterName (@Filter int filter) {
    switch (filter) {
      case FILTER_UNREAD:
        return R.string.CategoryUnread;
      case FILTER_PRIVATE:
        return R.string.CategoryPrivate;
      case FILTER_GROUPS:
        return R.string.CategoryGroup;
      case FILTER_CHANNELS:
        return R.string.CategoryChannels;
      case FILTER_BOTS:
        return R.string.CategoryBots;
      case FILTER_ARCHIVE:
        return R.string.CategoryArchive;
      case FILTER_NONE:
        return R.string.NoFilter;
      default:
        throw new UnsupportedOperationException("filter=" + filter);
    }
  }

  @DrawableRes
  private int getFilterIcon (@Filter int filter, boolean isGlobal) {
    switch (filter) {
      case FILTER_NONE:
        return isGlobal ? R.drawable.baseline_forum_24 : R.drawable.baseline_filter_variant_remove_24;
      case FILTER_PRIVATE:
        return R.drawable.baseline_person_24;
      case FILTER_GROUPS:
        return R.drawable.baseline_group_24;
      case FILTER_CHANNELS:
        return R.drawable.baseline_bullhorn_24;
      case FILTER_ARCHIVE:
        return R.drawable.baseline_archive_24;
      case FILTER_BOTS:
        return R.drawable.deproko_baseline_bots_24;
      case FILTER_UNREAD:
        return R.drawable.baseline_mark_chat_unread_24;
      default:
        throw new UnsupportedOperationException("filter = " + filter);
    }
  }

  @DrawableRes
  private int getFilterVariantIcon (@Filter int filter) {
    switch (filter) {
      case FILTER_NONE:
      case FILTER_ARCHIVE:
        return ResourcesCompat.ID_NULL;
      case FILTER_PRIVATE:
        return R.drawable.baseline_filter_variant_user_18;
      case FILTER_GROUPS:
        return R.drawable.baseline_filter_variant_group_18;
      case FILTER_CHANNELS:
        return R.drawable.baseline_filter_variant_channel_18;
      case FILTER_BOTS:
        return R.drawable.baseline_filter_variant_bot_18;
      case FILTER_UNREAD:
        return R.drawable.baseline_filter_variant_unread_18;
      default:
        throw new UnsupportedOperationException("filter = " + filter);
    }
  }

  private void modifyNewPagerItemController (final ViewController<?> c) {
    if (c instanceof RecyclerViewProvider) {
      c.getValue();
      ((RecyclerViewProvider) c).provideRecyclerView().addOnScrollListener(new RecyclerView.OnScrollListener() {
        private float lastY;
        private float lastShowY;

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          lastY += dy;
          if (dy < 0 && lastShowY - lastY >= Screen.getTouchSlop()) {
            showHideCompose(c, true);
            lastShowY = lastY;
          } else if (lastY - lastShowY > Screen.getTouchSlopBig()) {
            showHideCompose(c, false);
            lastShowY = lastY;
          }
          if (Math.abs(lastY - lastShowY) > Screen.getTouchSlopBig()) {
            lastY = 0;
            lastShowY = 0;
          }
        }
      });
    }
    xgramApplyPillRoom(c); // XGram M3E: a page made while the connection pill is in gets its room too
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, final int position) {
    final ViewController<?> c;
    long pagerItemId = getPagerItemId(position);
    if (pagerItemId == MAIN_PAGER_ITEM_ID) {
      TdApi.ChatList chatList = this.menuNeedArchive ? ChatPosition.CHAT_LIST_ARCHIVE : ChatPosition.CHAT_LIST_MAIN;
      c = newChatsController(chatList, getSelectedFilter(MAIN_PAGER_ITEM_ID));
    } else if (hasFolders()) {
      TdApi.ChatList chatList = pagerChatLists.get(position);
      c = newChatsController(chatList, getSelectedFilter(pagerItemId));
    } else if (position == POSITION_CALLS) {
      c = new CallListController(this.context, tdlib);
    } else if (position == POSITION_PEOPLE) {
      c = new PeopleController(this.context, tdlib);
    } else {
      throw new IllegalArgumentException("position == " + position);
    }
    modifyNewPagerItemController(c);
    return c;
  }

  private @Nullable TdApi.ChatFolderInfo[] chatFolderInfos;
  private List<ViewPagerTopView.Item> pagerSections = Collections.emptyList();
  private List<TdApi.ChatList> pagerChatLists = Collections.emptyList();
  private final LongSparseIntArray pagerChatListFilters = new LongSparseIntArray();
  private @Filter int globalFilter = FILTER_NONE;

  private boolean hasFolders () {
    return Settings.instance().chatFoldersEnabled() && !pagerChatLists.isEmpty();
  }

  @Override
  protected CharSequence[] getPagerSections () {
    if (hasFolders()) {
      throw new UnsupportedOperationException();
    }
    return new CharSequence[] {
      getMenuSectionName(MAIN_PAGER_ITEM_ID, /* pagerItemPosition */ 0, /* hasFolders */ false, ChatFolderStyle.LABEL_ONLY, /* upperCase */ true),
      xgramTabLabel(Lang.getString(R.string.Calls))/*, UI.getString(R.string.Contacts).toUpperCase()*/
    };
  }

  private List<ViewPagerTopView.Item> defaultSectionItems;
  private ViewPagerTopView.Item defaultMainItem;
  private CounterChangeListener defaultCounterListener;

  private ViewPagerTopView.Item getDefaultMainItem () {
    if (defaultMainItem == null) {
      CharSequence mainItem = getMenuSectionName(MAIN_PAGER_ITEM_ID, 0, false, ChatFolderStyle.LABEL_ONLY,  /* upperCase */ true);
      UnreadCounterColorSet unreadCounterColorSet = UnreadCounterColorSet.create(this);
      Counter unreadCounter = new Counter.Builder()
        .textSize(12f)
        .backgroundPadding(4f)
        .outlineAffectsBackgroundSize(false)
        .colorSet(unreadCounterColorSet)
        .callback(unreadCounterCallback(MAIN_PAGER_ITEM_ID))
        .build();
      unreadCounterColorSet.setCounter(unreadCounter);
      updateCounter(ChatPosition.CHAT_LIST_MAIN, unreadCounter, tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN), false);
      defaultCounterListener = new CounterChangeListener() {
        @Override
        public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
          handleCounterChange(chatList, counter, false);
        }

        @Override
        public void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
          handleCounterChange(chatList, counter, true);
        }

        private void handleCounterChange (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean areMessages) {
          int badgeFlags = tdlib.settings().getChatFolderBadgeFlags();
          if (areMessages != BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MESSAGES)) {
            return;
          }
          if (chatList.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
            executeOnUiThreadOptional(() -> {
              updateCounter(chatList, unreadCounter, counter, isFocused());
            });
          } else if (chatList.getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR && BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_ARCHIVED)) {
            executeOnUiThreadOptional(() -> {
              updateCounter(ChatPosition.CHAT_LIST_MAIN, unreadCounter, tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN), isFocused());
            });
          }
        }
      };
      defaultMainItem = new ViewPagerTopView.Item(mainItem, unreadCounter);
      tdlib.listeners().addTotalChatCounterListener(defaultCounterListener);
    }
    return defaultMainItem;
  }

  private List<ViewPagerTopView.Item> getDefaultSectionItems () {
    if (defaultSectionItems == null) {
      String callsItem = xgramTabLabel(Lang.getString(R.string.Calls));
      defaultSectionItems = Arrays.asList(
        getDefaultMainItem(),
        new ViewPagerTopView.Item(callsItem)
      );
    }
    return defaultSectionItems;
  }

  @Override
  protected List<ViewPagerTopView.Item> getPagerSectionItems () {
    return hasFolders() ? pagerSections : getDefaultSectionItems();
  }

  // Menu

  @Override
  protected int getMenuId () {
    if (xgramIsHiddenRoot()) {
      return 0;
    }
    // XGram M3E: its own id while the side menu is hidden (the header fills the menu again when the id changes)
    return xgramHidesDrawer() ? R.id.menu_xgramMainNoDrawer : R.id.menu_main;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_main || id == R.id.menu_xgramMainNoDrawer) {
      header.addLockButton(menu);
      // XGram: ghost mode indicator, tap for its settings.
      xgramGhostButton = header.addButton(menu, R.id.menu_btn_xgramGhost, R.drawable.baseline_ghost_24, getHeaderIconColorId(), this, Screen.dp(49f));
      xgramUpdateGhostButton();
      // XGram: Activity (every mention, reply and reaction), with a dot while it has new items
      xgramActivityButton = header.addButton(menu, R.id.menu_btn_xgramActivity, R.drawable.baseline_notifications_24, getHeaderIconColorId(), this, Screen.dp(49f));
      xgramUpdateActivityButton();
      header.addSearchButton(menu, this);
      if (id == R.id.menu_xgramMainNoDrawer) {
        xgramMoreButton = header.addMoreButton(menu, this); // XGram M3E: the side menu's items while it is hidden
        xgramUpdateMoreBadge(false);
      } else {
        xgramMoreButton = null;
      }
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, getSearchHeaderIconColorId(), getSearchBackButtonResource());
    } else {
      super.fillMenuItems(id, header, menu);
    }
  }

  private HeaderButton xgramGhostButton;
  /** XGram: Activity's bell (the one made last; a menu the header kept has its own). */
  @Nullable private HeaderButton xgramActivityButton;

  /**
   * XGram: the bell shows while Activity is on, In the chat list header is on and the navigation has no Activity (the
   * rail, the floating toolbar, or this chat list's bottom bar with Activity in Calls' place).
   */
  private boolean xgramActivityButtonShown () {
    return com.fahadali.xgram.activity.XgramActivity.isOn() && com.fahadali.xgram.activity.XgramActivity.inHeader() &&
      !com.fahadali.xgram.activity.XgramActivity.navigationHasActivity(context) &&
      !(xgramNavBar != null && com.fahadali.xgram.activity.XgramActivity.barHasActivity());
  }

  /** XGram: the bell's visibility, its dot and description, on every copy of the menu the header keeps. */
  public void xgramUpdateActivityButton () {
    if (isDestroyed()) {
      return;
    }
    final boolean shown = xgramActivityButtonShown();
    final int count = shown ? com.fahadali.xgram.activity.XgramActivityBadge.badgeCount(tdlib) : 0;
    xgramApplyActivityButton(xgramActivityButton, shown, count);
    if (headerView != null) {
      for (int i = 0; i < headerView.getChildCount(); i++) {
        View child = headerView.getChildAt(i);
        if (child instanceof ViewGroup && (child.getId() == R.id.menu_main || child.getId() == R.id.menu_xgramMainNoDrawer)) {
          View button = child.findViewById(R.id.menu_btn_xgramActivity);
          if (button instanceof HeaderButton && button != xgramActivityButton) {
            xgramApplyActivityButton((HeaderButton) button, shown, count);
          }
        }
      }
    }
    checkHeaderMargins(); // the folder tabs make room for the bell, or take it back
  }

  private static void xgramApplyActivityButton (@Nullable HeaderButton button, boolean shown, int count) {
    if (button == null) {
      return;
    }
    button.setVisibility(shown ? View.VISIBLE : View.GONE);
    button.xgramSetDot(count > 0 ? ColorId.badge : ColorId.NONE, button.isAttachedToWindow());
    button.setContentDescription(count > 0 ? Lang.getString(R.string.XgramActivityNew, count) : Lang.getString(R.string.XgramActivity));
  }

  private final com.fahadali.xgram.activity.XgramActivityBadge.Listener xgramActivityBadgeListener = this::xgramUpdateActivityButton;

  /** XGram M3E: the app bar's ⋮ while the side menu is hidden (the one made last). */
  @Nullable private HeaderButton xgramMoreButton;

  /**
   * XGram M3E: the ☰'s badge (MainActivity#updateCounter: another account's unread chats, or a problem to resolve) on
   * the ⋮ while the side menu is hidden and the floating toolbar has no photo (Your profile: Off): the accounts are in
   * the ⋮ then. With a photo, the photo shows it (XgramNavToolbar#setMenuBadge). From the toolbar when the badge or
   * its settings change, and when the ⋮ is made.
   */
  public void xgramUpdateMoreBadge (boolean animated) {
    if (isDestroyed()) {
      return;
    }
    final com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    final int colorId = toolbar != null && !toolbar.hasProfile() && xgramHidesDrawer() && context instanceof MainActivity ?
      ((MainActivity) context).getXgramMenuBadgeColorId() : ColorId.NONE;
    final HeaderButton more = xgramMoreButton;
    if (more != null) {
      more.xgramSetDot(colorId, animated);
    }
    if (headerView != null) {
      // A menu the header kept (reused on the way back) has its own ⋮
      for (int i = 0; i < headerView.getChildCount(); i++) {
        View child = headerView.getChildAt(i);
        if (child instanceof ViewGroup && child.getId() == R.id.menu_xgramMainNoDrawer) {
          View button = child.findViewById(R.id.menu_btn_more);
          if (button instanceof HeaderButton && button != more) {
            ((HeaderButton) button).xgramSetDot(colorId, animated);
          }
        }
      }
    }
  }

  private final com.fahadali.xgram.XgramSettings.Listener xgramSettingsListener = key -> {
    if (key.startsWith("xgram_ghost_")) {
      UI.post(this::xgramUpdateGhostButton);
    } else if (com.fahadali.xgram.XgramSettings.KEY_ACTIVITY_ON.equals(key) || com.fahadali.xgram.XgramSettings.KEY_ACTIVITY_BAR.equals(key)) {
      // XGram: Activity came or went, or its place on the bottom bar changed: the rail's destinations, the toolbar's
      // tabs and the bar's items follow at once (XgramTwoPane#update syncs all three), then the bell
      UI.post(() -> {
        com.fahadali.xgram.layout.XgramTwoPane twoPane = context.xgramTwoPane();
        if (twoPane != null && !isDestroyed()) {
          twoPane.update();
        }
        xgramUpdateActivityButton();
      });
    } else if (key.startsWith(com.fahadali.xgram.XgramSettings.KEY_ACTIVITY_PREFIX) || key.startsWith("xgram_nav_")) {
      UI.post(this::xgramUpdateActivityButton); // XGram: Activity's bell (its settings, or the navigation that may hold it)
    } else if (com.fahadali.xgram.XgramSettings.KEY_HIDE_FAB.equals(key)) {
      UI.post(this::xgramUpdateComposeVisibility);
    } else if (com.fahadali.xgram.XgramSettings.KEY_ALL_CHATS_LABEL.equals(key)) {
      UI.post(this::onFoldersAppearanceChanged);
    } else if (com.fahadali.xgram.XgramSettings.KEY_BOTTOM_NAV.equals(key)) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        // XGram M3E: the floating navigation toolbar belongs to the window (XgramTwoPane)
        UI.post(() -> {
          com.fahadali.xgram.layout.XgramTwoPane twoPane = context.xgramTwoPane();
          if (twoPane != null && !isDestroyed()) {
            twoPane.update();
          }
        });
      } else {
        UI.post(this::xgramUpdateNavBar); // the bottom navigation bar comes or goes at once
      }
    } else if (com.fahadali.xgram.XgramSettings.KEY_NAV_FAB.equals(key) || com.fahadali.xgram.XgramSettings.KEY_NAV_HIDE_DRAWER.equals(key)) {
      // XGram M3E: the FAB's place and the pages' room, the ☰ and the ⋮ follow at once
      UI.post(this::xgramOnToolbarOptionsChanged);
    } else if (com.fahadali.xgram.XgramSettings.hideFoldersKey(tdlib.accountId()).equals(key)) {
      UI.post(() -> {
        if (!isDestroyed() && Settings.instance().chatFoldersEnabled()) {
          if (globalFilter != FILTER_NONE && com.fahadali.xgram.XgramSettings.instance().hideFolders(tdlib.accountId())) {
            applyGlobalFilter(FILTER_NONE);
          }
          updatePagerSections(false);
        }
      });
    }
  };

  /** XGram: the archive gets a tab only while folders aren't hidden in XGram (Chat folders, Hide folders in XGram). */
  private boolean xgramArchiveTabEnabled () {
    return tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE) && !com.fahadali.xgram.XgramSettings.instance().hideFolders(tdlib.accountId());
  }

  private void xgramUpdateComposeVisibility () {
    if (composeWrap != null) {
      composeWrap.setVisibility(com.fahadali.xgram.XgramSettings.instance().hideFab() ? View.GONE : View.VISIBLE);
      if (xgramToolbar() != null) {
        checkComposeWrapPaddings(); // XGram M3E: the pages keep room for a FAB at the side only while there is one
      }
    }
  }

  private void xgramUpdateGhostButton () {
    if (xgramGhostButton != null) {
      xgramGhostButton.setVisibility(com.fahadali.xgram.XgramSettings.instance().isAnyGhostEnabled() ? View.VISIBLE : View.GONE);
    }
    checkHeaderMargins(); // the folder tabs make room for the button, or take it back
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_xgramGhost) {
      navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.ghost(context, tdlib)); // XGram: Privacy, Ghost mode first
      return;
    }
    if (id == R.id.menu_btn_xgramActivity) {
      com.fahadali.xgram.activity.XgramActivity.open(this); // XGram: Activity (the chat list pane with two panes)
      return;
    }
    if (id == R.id.menu_btn_search) {
      tdlib.checkDeadlocks(() -> runOnUiThreadOptional(() -> {
        if (isFocused()) {
          openSearchMode();
        }
      }));
      /*case R.id.menu_btn_more: {
        showMore(new int[] {
          R.id.more_btn_settings,
          R.id.more_btn_help
        }, new String[] {Lang.getString(R.string.Settings), Lang.getString(R.string.Help)}, 0);
        break;
      }*/
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.menu_btn_more && !inSelectMode() && !inSearchMode()) {
      xgramShowSideMenuItems(); // XGram M3E: the app bar's ⋮ while the side menu is hidden (a selection's ⋮ is its own)
    } else {
      super.onMenuItemPressed(id, view);
    }
  }

  // XGram M3E: Hide the side menu (Navigation bar): what the side menu had, in the app bar's ⋮ menu. The side menu's
  // order and hidden items (Settings > XGram > Side menu) apply; what is a tab of the floating navigation toolbar (or
  // the photo's long press: the accounts) is left out. Destinations first, then the switches as checkable items (a
  // check and a tinted pill while on, as the M3E selection menus). The menu scrolls in a low window (the cover screen
  // in landscape).

  private static final int XGRAM_MENU_ROW = 0, XGRAM_MENU_CHECK_OFF = 1, XGRAM_MENU_CHECK_ON = 2;

  private void xgramShowSideMenuItems () {
    if (headerView == null || isDestroyed() || !isFocused() || context.isNavigationBusy() || (navigationController != null && navigationController.isAnimating())) {
      return;
    }
    final com.fahadali.xgram.XgramSettings xgram = com.fahadali.xgram.XgramSettings.instance();
    final com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
    final IntList ids = new IntList(16);
    final IntList kinds = new IntList(16);
    final StringList titles = new StringList(16);
    if (toolbar != null && !toolbar.hasProfile()) {
      // No photo on the toolbar to long press: the accounts, as at the top of the side menu
      ids.append(R.id.btn_xgramNavSwitchAccount);
      titles.append(R.string.XgramNavSwitchAccount);
      kinds.append(XGRAM_MENU_ROW);
      if (TdlibManager.instance().getActiveAccounts().size() >= 2) {
        // The side menu's drag to reorder and its preview's Log out
        ids.append(R.id.btn_xgramNavManageAccounts);
        titles.append(R.string.XgramNavAccountsManage);
        kinds.append(XGRAM_MENU_ROW);
      }
    }
    for (String key : com.fahadali.xgram.XgramReorder.ordered(com.fahadali.xgram.XgramReorder.DRAWER, com.fahadali.xgram.XgramDrawerItemsController.KEYS)) {
      if (!com.fahadali.xgram.XgramDrawerItemsController.KEY_SETTINGS.equals(key) && xgram.drawerItemHidden(key)) {
        continue; // Settings can't be hidden
      }
      final int id, title;
      int kind = XGRAM_MENU_ROW;
      switch (key) {
        case "activity":
          // XGram: Activity, unless it is off or a tab of the toolbar
          if (!com.fahadali.xgram.activity.XgramActivity.isOn() || com.fahadali.xgram.activity.XgramActivity.toolbarHasActivity(toolbar)) {
            continue;
          }
          id = R.id.btn_xgramActivity;
          title = R.string.XgramActivity;
          break;
        case "contacts":
          if (xgramToolbarHasTab(toolbar, com.fahadali.xgram.layout.XgramNavToolbar.DEST_CONTACTS)) {
            continue;
          }
          id = R.id.btn_contacts;
          title = R.string.Contacts;
          break;
        case "hub":
          id = R.id.btn_xgramHub;
          title = R.string.XgramHub;
          break;
        case "calls":
          // As in the side menu: with folders on (without, Calls is a page of the chat list and its header's tab)
          if (!Settings.instance().chatFoldersEnabled() || xgramToolbarHasTab(toolbar, com.fahadali.xgram.layout.XgramNavToolbar.DEST_CALLS)) {
            continue;
          }
          id = R.id.btn_calls;
          title = R.string.Calls;
          break;
        case "saved":
          if (xgramToolbarHasTab(toolbar, com.fahadali.xgram.layout.XgramNavToolbar.DEST_SAVED)) {
            continue;
          }
          id = R.id.btn_savedMessages;
          title = R.string.XgramNavRailSavedMessages;
          break;
        case "bookmarks":
          id = R.id.btn_xgramBookmarks;
          title = R.string.XgramBookmarks;
          break;
        case "saved_by_chat":
          id = R.id.btn_xgramSavedByChat;
          title = R.string.XgramSavedByChat;
          break;
        case "settings":
          if (xgramToolbarHasTab(toolbar, com.fahadali.xgram.layout.XgramNavToolbar.DEST_SETTINGS)) {
            continue;
          }
          id = R.id.btn_settings;
          title = R.string.Settings;
          break;
        case "invite":
          id = R.id.btn_invite;
          title = R.string.XgramNavRailInviteFriends;
          break;
        case "proxy":
          if (Settings.instance().getAvailableProxyCount() <= 0) {
            continue;
          }
          id = R.id.btn_proxy;
          title = R.string.Proxy;
          kind = Settings.instance().getEffectiveProxyId() != Settings.PROXY_ID_NONE ? XGRAM_MENU_CHECK_ON : XGRAM_MENU_CHECK_OFF;
          break;
        case "help":
          id = R.id.btn_help;
          title = R.string.Help;
          break;
        default:
          continue;
      }
      ids.append(id);
      titles.append(title);
      kinds.append(kind);
    }
    for (String key : com.fahadali.xgram.XgramReorder.ordered(com.fahadali.xgram.XgramReorder.DRAWER, com.fahadali.xgram.XgramDrawerItemsController.TOGGLE_KEYS)) {
      if (xgram.drawerItemHidden(key) || !com.fahadali.xgram.XgramDrawerItemsController.isAvailable(key)) {
        continue;
      }
      final int id, title;
      final boolean on;
      switch (key) {
        case "night":
          id = R.id.btn_night;
          title = R.string.XgramNavRailNightMode;
          on = Theme.isDark();
          break;
        case "expressive":
          id = R.id.btn_xgramExpressive;
          title = R.string.XgramExpressive;
          on = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
          break;
        case "ghost":
          id = R.id.btn_xgramGhostMode;
          title = R.string.XgramGhostMode;
          on = xgram.isAnyGhostEnabled();
          break;
        default:
          continue;
      }
      ids.append(id);
      titles.append(title);
      kinds.append(on ? XGRAM_MENU_CHECK_ON : XGRAM_MENU_CHECK_OFF);
    }
    // The developer entries the side menu has in those builds and modes
    if (Test.NEED_CLICK) {
      ids.append(R.id.btn_reportBug);
      titles.append(Test.CLICK_NAME);
      kinds.append(XGRAM_MENU_ROW);
    }
    if (BuildConfig.EXPERIMENTAL) {
      ids.append(R.id.btn_featureToggles);
      titles.append("Feature Toggles");
      kinds.append(XGRAM_MENU_ROW);
    }
    if (Settings.instance().inDeveloperMode()) {
      ids.append(R.id.btn_tdlib_clearLogs);
      titles.append("Clear TDLib logs");
      kinds.append(XGRAM_MENU_ROW);
      ids.append(R.id.btn_tdlib_shareLogs);
      titles.append("Send TDLib log");
      kinds.append(XGRAM_MENU_ROW);
    }
    if (ids.size() == 0) {
      // Everything else is hidden or a tab: Settings, which the side menu never hides (a tab: the tab's screen). The ⋮
      // is always there while the side menu is hidden, so a tap on it always opens something.
      ids.append(R.id.btn_settings);
      titles.append(R.string.Settings);
      kinds.append(XGRAM_MENU_ROW);
    }
    final int count = ids.size();

    final MenuMoreWrap menu = new MenuMoreWrap(context, /* scrollable */ true);
    menu.init(null); // made for this one showing: colours of the theme now, no listeners left behind
    final View.OnClickListener onClick = v -> {
      PopupLayout popup = PopupLayout.parentOf(v);
      if (popup != null) {
        popup.hideWindow(true);
      }
      xgramOnSideMenuItem(v.getId());
    };
    final int[] idArray = ids.get();
    final String[] titleArray = titles.get();
    final int[] kindArray = kinds.get();
    final TextView[] rows = new TextView[count];
    for (int i = 0; i < count; i++) {
      rows[i] = menu.addItem(idArray[i], titleArray[i], 0, null, onClick);
    }
    for (int i = 0; i < count; i++) {
      if (kindArray[i] != XGRAM_MENU_ROW) {
        menu.xgramSetCheckable(idArray[i], kindArray[i] == XGRAM_MENU_CHECK_ON);
      }
    }
    // The rows are in a scroll view here: the menu takes the widest row's width itself (at most the app bar's, less
    // the 8dp on each side)
    int widest = 0;
    for (TextView row : rows) {
      if (row != null && row.getTag() instanceof Integer) {
        widest = Math.max(widest, (Integer) row.getTag());
      }
    }
    final int padding = Screen.dp(MenuMoreWrap.PADDING);
    final int maxWidth = Math.max(Screen.dp(196f), headerView.getMeasuredWidth() - 2 * padding);
    menu.setMinimumWidth(Math.min(maxWidth, Math.max(Screen.dp(196f), widest + 2 * padding)));
    menu.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_RIGHT);
    menu.setRightNumber(0);
    menu.xgramSetAnchorView(headerView, 0); // under the ⋮, at the app bar's end
    final float top = headerView.getTranslationY() + headerView.getCurrentHeaderOffset();
    menu.setTranslationY(top);
    if (menu.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
      // Moved down by the status bar: it ends that much (and 8dp) above the window's bottom, and scrolls the rest
      ((ViewGroup.MarginLayoutParams) menu.getLayoutParams()).bottomMargin = Math.max(0, Math.round(top)) + Screen.dp(8f);
    }
    PopupLayout popupLayout = new PopupLayout(context);
    popupLayout.init(true);
    popupLayout.setNeedRootInsets();
    popupLayout.setOverlayStatusBar(true);
    popupLayout.showMoreView(menu);
  }

  private static boolean xgramToolbarHasTab (@Nullable com.fahadali.xgram.layout.XgramNavToolbar toolbar, int dest) {
    return toolbar != null && toolbar.hasTab(dest);
  }

  /** XGram M3E: an item of the app bar's ⋮ (xgramShowSideMenuItems), as the side menu does it (DrawerController#onClick). */
  private void xgramOnSideMenuItem (int id) {
    if (isDestroyed()) {
      return;
    }
    if (id == R.id.btn_xgramNavSwitchAccount) {
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar != null) {
        toolbar.showAccountMenu();
      }
    } else if (id == R.id.btn_xgramNavManageAccounts) {
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar != null) {
        toolbar.showManageAccountsMenu();
      }
    } else if (id == R.id.btn_xgramActivity) {
      com.fahadali.xgram.activity.XgramActivity.open(this);
    } else if (id == R.id.btn_contacts) {
      tdlib.contacts().startSyncIfNeeded(context, true, () -> {
        if (!isDestroyed()) {
          PeopleController c = new PeopleController(context, tdlib);
          c.setNeedSearch();
          navigateTo(c);
        }
      });
    } else if (id == R.id.btn_xgramHub) {
      navigateTo(com.fahadali.xgram.compose.XgramSettingsPages.main(context, tdlib));
    } else if (id == R.id.btn_calls) {
      navigateTo(new CallListController(context, tdlib));
    } else if (id == R.id.btn_savedMessages) {
      xgramOpenSavedMessages();
    } else if (id == R.id.btn_xgramBookmarks) {
      navigateTo(new com.fahadali.xgram.chats.XgramBookmarksController(context, tdlib));
    } else if (id == R.id.btn_xgramSavedByChat) {
      navigateTo(new com.fahadali.xgram.chats.XgramSavedTopicsController(context, tdlib));
    } else if (id == R.id.btn_settings) {
      // A tab (the menu's last resort): the tab's screen, as the side menu opens it, not a second Settings on top
      com.fahadali.xgram.layout.XgramNavToolbar toolbar = xgramToolbar();
      if (toolbar == null || !toolbar.openFromDrawer(com.fahadali.xgram.layout.XgramNavToolbar.DEST_SETTINGS)) {
        navigateTo(new SettingsController(context, tdlib));
      }
    } else if (id == R.id.btn_invite) {
      tdlib.cache().getInviteText(text -> {
        if (text != null) {
          final String value = text.text;
          UI.post(() -> org.thunderdog.challegram.tool.Intents.shareText(value));
        }
      });
    } else if (id == R.id.btn_proxy) {
      Settings.instance().toggleProxySetting(Settings.PROXY_FLAG_ENABLED);
    } else if (id == R.id.btn_help) {
      tdlib.ui().openSupport(this);
    } else if (id == R.id.btn_night) {
      org.thunderdog.challegram.theme.ThemeManager.instance().toggleNightMode();
    } else if (id == R.id.btn_xgramExpressive) {
      // Appearance's confirm, as the side menu's switch: the app restarts in the other look
      if (com.fahadali.xgram.appearance.XgramExpressive.isAvailable()) {
        final boolean on = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
        openAlert(R.string.XgramExpressive, Lang.getString(R.string.XgramExpressiveInfo), Lang.getString(R.string.AppUpdateRestart), (dialog, which) ->
          com.fahadali.xgram.appearance.XgramExpressive.setEnabled(this, !on), 0);
      }
    } else if (id == R.id.btn_xgramGhostMode) {
      // The side menu's quick switch: off turns every ghost option off (the app bar's ghost button follows)
      com.fahadali.xgram.XgramSettings settings = com.fahadali.xgram.XgramSettings.instance();
      settings.setGhostModeEnabled(!settings.isAnyGhostEnabled());
      com.fahadali.xgram.ghost.XgramGhost.applyOnlineState();
    } else if (id == R.id.btn_reportBug) {
      if (Test.NEED_CLICK) {
        Test.onClick(context);
      }
    } else if (id == R.id.btn_featureToggles) {
      navigateTo(new FeatureToggles.Controller(context, tdlib));
    } else if (id == R.id.btn_tdlib_clearLogs) {
      TdlibUi.clearLogs(true, size -> TdlibUi.clearLogs(false, size2 -> UI.showToast("Logs Cleared", Toast.LENGTH_SHORT)));
    } else if (id == R.id.btn_tdlib_shareLogs) {
      TdlibUi.sendTdlibLogs(this, false, false);
    }
  }

  /** XGram M3E: the ⋮'s Saved messages: the chat (made first when it does not exist yet), over the chat list. */
  private void xgramOpenSavedMessages () {
    final long userId = tdlib.myUserId();
    if (userId == 0) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(ChatId.fromUserId(userId));
    if (chat != null) {
      tdlib.ui().openChat(this, chat.id, new TdlibUi.ChatOpenParameters().keepStack());
      return;
    }
    tdlib.send(new TdApi.CreatePrivateChat(userId, true), (remoteChat, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        final long chatId = remoteChat.id;
        runOnUiThreadOptional(() -> tdlib.ui().openChat(this, chatId, new TdlibUi.ChatOpenParameters().keepStack()));
      }
    });
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (composeWrap != null && composeWrap.isShowing()) {
      if (commit) {
        composeWrap.close();
      }
      return true;
    }
    if (commit) {
      if (scrollToFirstPosition()) {
        return true;
      }
    } else {
      if (!isAtFirstPosition()) {
        return true;
      }
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  // Intent process

  private Tdlib shareTdlib;
  private String shareIntentAction;
  private Intent shareIntent;

  public void shareIntent (final Tdlib tdlib, final String intentAction, Intent intent) {
    this.shareTdlib = tdlib;
    this.shareIntentAction = intentAction;
    this.shareIntent = intent;
    shareIntentImpl();
  }

  private void shareIntentImpl () {
    final Tdlib tdlib = shareTdlib;
    final Intent intent = shareIntent;
    final String intentAction = shareIntentAction;
    if (intent == null) {
      return;
    }

    BaseActivity activity = context();
    if (activity.isPasscodeShowing()) {
      activity.addPasscodeListener(new BaseActivity.PasscodeListener() {
        @Override
        public void onPasscodeShowing (BaseActivity context, boolean isShowing) {
          if (isShowing) {
            activity.removePasscodeListener(this);
            shareIntentImpl();
          }
        }
      });
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      if (context().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        context().requestCustomPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, (code, permissions, grantResults, grantCount) -> {
          if (grantCount == permissions.length) {
            shareIntentImpl();
          } else {
            UI.showToast(R.string.NoStorageAccess, Toast.LENGTH_SHORT);
          }
        });
        return;
      }
    }

    shareTdlib = null;
    shareIntent = null;
    shareIntentAction = null;
    new Thread(() -> {
      CancellableRunnable runnable = new CancellableRunnable() {
        @Override
        public void act () {
          UI.showToast(R.string.ProcessingFileWait, Toast.LENGTH_SHORT);
        }
      };
      runnable.removeOnCancel(UI.getAppHandler());
      UI.post(runnable, 1000l);
      try {
        switch (intentAction) {
          case Intent.ACTION_SEND: {
            shareIntentImplSingle(tdlib, intent);
            break;
          }
          case Intent.ACTION_SEND_MULTIPLE: {
            shareIntentImplMultiple(tdlib, intent);
            break;
          }
        }
      } catch (Throwable t) {
        Log.e(t);
        UI.showToast(R.string.ShareContentUnsupported, Toast.LENGTH_SHORT);
      } finally {
        runnable.cancel();
      }
    }).start();
  }

  @SuppressWarnings("deprecation")
  @Nullable
  private static Uri getUri (@NonNull Bundle bundle, @NonNull String key) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return bundle.getParcelable(key, Uri.class);
    } else {
      return (Uri) bundle.get(key);
    }
  }

  private void shareIntentImplSingle (Tdlib tdlib, final Intent intent) throws Throwable {
    String type = intent.getType();
    final ArrayList<TdApi.InputMessageContent> out = new ArrayList<>();

    if (!StringUtils.isEmpty(type) && ContactsContract.Contacts.CONTENT_VCARD_TYPE.equals(type)) {
      Bundle extras = intent.getExtras();
      if (extras == null) {
        throw new IllegalArgumentException("extras == null");
      }
      Uri uri = getUri(extras, Intent.EXTRA_STREAM);
      if (uri == null) {
        throw new IllegalArgumentException("uri == null");
      }
      ContentResolver cr = UI.getContext().getContentResolver();
      try (InputStream stream = cr.openInputStream(uri)) {
        if (stream == null) {
          throw new IllegalArgumentException("stream == null (vcard)");
        }

        boolean vCardStarted = false;
        String currentName = null;

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StringUtils.UTF_8))) {
          String line;
          while ((line = bufferedReader.readLine()) != null) {
            String[] args = line.split(":");
            if (args.length != 2) {
              continue;
            }
            if (args[0].equals("BEGIN") && args[1].equals("VCARD")) {
              vCardStarted = true;
              currentName = null;
            } else if (args[0].equals("END") && args[1].equals("VCARD")) {
              vCardStarted = false;
            }
            if (!vCardStarted) {
              continue;
            }
            if (args[0].startsWith("FN") || (args[0].startsWith("ORG") && StringUtils.isEmpty(currentName))) {
              String nameEncoding = null;
              String nameCharset = "UTF-8";
              String[] params = args[0].split(";");
              for (String param : params) {
                String[] args2 = param.split("=");
                if (args2.length != 2) {
                  continue;
                }
                if (args2[0].equals("CHARSET")) {
                  nameCharset = args2[1];
                } else if (args2[0].equals("ENCODING")) {
                  nameEncoding = args2[1];
                }
              }
              currentName = args[1];
              if (nameEncoding != null && nameEncoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
                while (currentName.endsWith("=")) {
                  currentName = currentName.substring(0, currentName.length() - 1);
                  line = bufferedReader.readLine();
                  if (line == null) {
                    break;
                  }
                  currentName += line;
                }
                byte[] bytes = U.decodeQuotedPrintable(currentName.getBytes());
                if (bytes != null && bytes.length != 0) {
                  currentName = new String(bytes, nameCharset);
                }
              }
            } else if (args[0].startsWith("TEL")) {
              String phone = Strings.getNumber(args[1]);
              if (!phone.isEmpty()) {
                boolean found = false;
                if (!out.isEmpty()) {
                  for (TdApi.InputMessageContent content : out) {
                    if (content instanceof TdApi.InputMessageContact && StringUtils.equalsOrBothEmpty(((TdApi.InputMessageContact) content).contact.phoneNumber, phone)) {
                      found = true;
                      break;
                    }
                  }
                }
                if (!found) {
                  out.add(new TdApi.InputMessageContact(new TdApi.Contact(phone, currentName, null, null, 0))); // TODO VCARD sharing
                }
              }
            }
          }
        }
        if (out.isEmpty()) {
          UI.showToast("No phone number available to share", Toast.LENGTH_SHORT);
          return;
        }
      }
    } else {
      // First, obtain text
      String sendingText = obtainText(intent);

      // Second, obtain media
      Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      Uri uri = U.getUri(parcelable);
      if (uri != null) {
        if (U.isInternalUri(uri)) {
          throw new IllegalArgumentException("Tried to share internal file: " + uri.toString());
        }
        if (addShareUri(tdlib, out, type, uri, sendingText)) {
          sendingText = null;
        }
      }

      // If sendingText still unused, then add it to the beginning of send queue
      if (!StringUtils.isEmpty(sendingText)) {
        out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(sendingText, null), null, false), tdlib.maxMessageTextLength()));
      }
    }
    shareContents(tdlib, type, out, false);
  }

  private static String obtainText (Intent intent) {
    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
    if (text == null) {
      CharSequence textSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
      if (textSequence != null) {
        text = textSequence.toString();
      }
    }
    String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
    String result = getShareText(subject, text);
    return result != null ? result.trim() : null;
  }

  private void shareIntentImplMultiple (Tdlib tdlib, Intent intent) {
    ArrayList<Parcelable> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    ArrayList<TdApi.InputMessageContent> out = new ArrayList<>(uris.size());
    String type = intent.getType();

    // First, obtain text
    String sendingText = obtainText(intent);

    for (Parcelable parcelable : uris) {
      Uri uri = U.getUri(parcelable);
      if (uri == null) {
        throw new IllegalArgumentException("Unknown parcelable type: " + parcelable);
      }
      if (addShareUri(tdlib, out, type, uri, sendingText)) {
        sendingText = null;
      }
    }
    // If sendingText still unused, then add it to the beginning of send queue
    if (!StringUtils.isEmpty(sendingText)) {
      out.addAll(0, TD.explodeText(new TdApi.InputMessageText(new TdApi.FormattedText(sendingText, null), null, false), tdlib.maxMessageTextLength()));
    }

    shareContents(tdlib, type, out, true);
  }

  private static String getShareText (String subject, String text) {
    if (StringUtils.isEmpty(text) && StringUtils.isEmpty(subject)) {
      return null;
    }
    StringBuilder b = new StringBuilder(StringUtils.length(text) + 1 + StringUtils.length(subject));
    if (!StringUtils.isEmpty(subject) && (StringUtils.isEmpty(text) || text.startsWith("https://") || text.startsWith("http://"))) {
      b.append(subject);
    }
    if (!StringUtils.isEmpty(text)) {
      if (b.length() > 0) {
        b.append('\n');
      }
      b.append(text);
    }
    String res = b.toString().trim();
    return res.isEmpty() ? null : res;
  }

  /**
   *
   * @return true if provided caption parameter has been used as caption
   */
  // XGram: public, dropped and pasted files are sent the same way (XgramDragDrop)
  public static boolean addShareUri (Tdlib tdlib, ArrayList<TdApi.InputMessageContent> out, String mimeType, Uri uri, final @Nullable String rawCaption) {
    if (uri == null) {
      return false;
    }

    String filePath = U.tryResolveFilePath(uri);

    if (StringUtils.isEmpty(filePath)) {
      throw new IllegalArgumentException("filePath cannot be resolved for type " + mimeType + ", uri: " + uri);
    }

    if (mimeType.isEmpty()) {
      String extension = U.getExtension(filePath);
      if (extension != null) {
        mimeType = TGMimeType.mimeTypeForExtension(extension);
      }
    }

    if (!U.canReadFile(filePath)) {
      filePath = uri.toString();

      if (!U.canReadContentUri(uri)) {
        return false;
      }
    }

    final int captionCodePointCount = rawCaption != null ? rawCaption.codePointCount(0, rawCaption.length()) : 0;
    final TdApi.FormattedText messageCaption = captionCodePointCount > 0 && captionCodePointCount <= tdlib.maxCaptionLength() ? new TdApi.FormattedText(rawCaption, null) : null;

    if (!StringUtils.isEmpty(mimeType)) {
      if (mimeType.equals("image/webp")) {
        BitmapFactory.Options options = ImageReader.getImageWebpSize(filePath);
        out.add(new TdApi.InputMessageSticker(new TdApi.InputSticker(TD.createInputFile(filePath), null, options.outWidth, options.outHeight), null));
        return false;
      }

      if (mimeType.equals("image/gif")) {
        BitmapFactory.Options options = ImageReader.getImageSize(filePath);
        out.add(new TdApi.InputMessageAnimation(new TdApi.InputAnimation(TD.createInputFile(filePath), null, null, 0, options.outWidth, options.outHeight), messageCaption, false, false));
        return messageCaption != null;
      }

      if (mimeType.startsWith("image/")) {
        BitmapFactory.Options opts = ImageReader.getImageSize(filePath);

        int rotation = U.getRotation(filePath);
        int inSampleSize = ImageReader.calculateInSampleSize(opts, 1280, 1280);
        int width = rotation == 90 || rotation == 270 ? opts.outHeight / inSampleSize : opts.outWidth / inSampleSize;
        int height = rotation == 90 || rotation == 270 ? opts.outWidth / inSampleSize : opts.outHeight / inSampleSize;

        TdApi.InputFileGenerated inputFile = PhotoGenerationInfo.newFile(filePath, rotation);
        out.add(new TdApi.InputMessagePhoto(new TdApi.InputPhoto(inputFile, null, null, null, width, height), messageCaption, false, null, false));
        return messageCaption != null;
      }

      if (mimeType.startsWith("video/")) {
        MediaMetadataRetriever media = null;
        try {
          media = U.openRetriever(filePath);
        } catch (Throwable ignored) { }
        if (media != null) {

          String rawDuration = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
          int duration = StringUtils.isNumeric(rawDuration) ? (int) (Long.parseLong(rawDuration) / 1000l) : 0;
          String rawWidth = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
          int width = StringUtils.isNumeric(rawWidth) ? StringUtils.parseInt(rawWidth) : 0;
          String rawHeight = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
          int height = StringUtils.isNumeric(rawHeight) ? StringUtils.parseInt(rawHeight) : 0;

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            String rawRotation = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int rotation = StringUtils.isNumeric(rawRotation) ? StringUtils.parseInt(rawRotation) : 0;
            if (rotation == 90 || rotation == 270) {
              int temp = height;
              height = width;
              width = temp;
            }
          }


          TdApi.InputFile inputVideo;
          if (Config.USE_VIDEO_COMPRESSION) {
            inputVideo = VideoGenerationInfo.newFile(filePath, null, false);
          } else {
            inputVideo = TD.createInputFile(filePath);
          }

          U.closeRetriever(media);

          out.add(new TdApi.InputMessageVideo(new TdApi.InputVideo(inputVideo, null, null, 0, null, duration, width, height, U.canStreamVideo(inputVideo)), messageCaption, false, null, false));
          return messageCaption != null;
        }
      }
    }

    TD.FileInfo info = new TD.FileInfo();
    TdApi.InputFile file = TD.createInputFile(filePath, mimeType, info);

    out.add(TD.toInputMessageContent(filePath, file, info, messageCaption, false, false));
    return messageCaption != null;
  }

  private void shareContents (final Tdlib tdlib, final String type, final ArrayList<TdApi.InputMessageContent> contents, boolean mergeAlbum) {
    if (contents.isEmpty()) {
      throw new IllegalArgumentException("Unsupported content type: " + type);
    }
    tdlib.ui().post(() -> tdlib.awaitInitialization(() -> tdlib.ui().post(() -> {
      ShareController c = new ShareController(context, tdlib);

      ShareController.ShareProviderDelegate shareDelegate = new ShareController.ShareProviderDelegate() {
        @Override
        public void generateFunctionsForChat (long chatId, TdApi.Chat chat, TdApi.MessageSendOptions sendOptions, ArrayList<TdApi.Function<?>> functions) {
          List<TdApi.InputMessageContent> album = null;
          for (TdApi.InputMessageContent content : contents) {
            content = tdlib.filegen().createThumbnail(content, ChatId.isSecret(chatId));
            content = com.fahadali.xgram.chats.XgramLinkFixer.applyToContent(chatId, content, tdlib.maxMessageTextLength()); // XGram: link preview fixer
            if (mergeAlbum) {
              int combineMode = TD.getCombineMode(content);
              if (combineMode == TD.COMBINE_MODE_MEDIA) {
                if (album == null)
                  album = new ArrayList<>();
                album.add(content);
                if (album.size() == TdConstants.MAX_MESSAGE_GROUP_SIZE)
                  TD.processAlbum(tdlib, chatId, sendOptions, functions, album);
              } else {
                TD.processAlbum(tdlib, chatId, sendOptions, functions, album);
                TD.processSingle(tdlib, chatId, sendOptions, functions, content);
              }
            } else {
              TD.processSingle(tdlib, chatId, sendOptions, functions, content);
            }
          }
          if (mergeAlbum) {
            TD.processAlbum(tdlib, chatId, sendOptions, functions, album);
          }
        }

        @Override
        public CharSequence generateErrorMessageForChat (long chatId) {
          for (TdApi.InputMessageContent content : contents) {
            CharSequence errorMessage = tdlib.getRestrictionText(tdlib.chatStrict(chatId), content);
            if (errorMessage != null)
              return errorMessage;
          }
          return null;
        }
      };

      c.setArguments(new ShareController.Args(shareDelegate).setNeedOpenChat(true));
      c.show();
    })));
  }

  // Sync alert

  private SettingsWrap syncAlertWrap;
  private boolean syncShown;

  private boolean showSyncAlert () {
    if (syncShown || (syncAlertWrap != null && syncAlertWrap.window != null && !syncAlertWrap.window.isWindowHidden()))
      return true;
    if (!Settings.instance().needTutorial(Settings.TUTORIAL_SYNC_SETTINGS))
      return false;
    syncAlertWrap = showSettings(new SettingsWrapBuilder(R.id.btn_notificationSettings).setIntDelegate((id, result) -> {
      SyncAdapter.turnOnSync(context, tdlib, true);
    }).setDismissListener(popup -> {
      syncAlertWrap = null;
      showAnnoyingAlertsForCompliance(false);
    }).setOnActionButtonClick((wrap, view, isCancel) -> {
      if (isCancel) {
        int i = wrap.adapter.indexOfViewById(R.id.btn_neverAllow);
        if (i != -1 && wrap.adapter.getItems().get(i).isSelected()) {
          Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_SYNC_SETTINGS);
        }
      }
      return false;
    }).setCancelStr(R.string.NotificationSyncDecline).setSaveStr(R.string.NotificationSyncAccept).setAllowResize(false).setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_neverAllow, 0, R.string.NeverShowAgain, false)
    }).addHeaderItem(Lang.getMarkdownString(this, R.string.NotificationSyncOffWarn)));
    if (syncAlertWrap != null) {
      syncShown = true;
      return true;
    }
    return false;
  }

  private void hideSyncAlert () {
    if (syncAlertWrap != null) {
      if (syncAlertWrap.window != null)
        syncAlertWrap.window.hideWindow(true);
      syncAlertWrap = null;
      syncShown = false;
    }
  }

  private boolean checkSyncAlert () {
    if (tdlib.notifications().needSyncAlert()) {
      return showSyncAlert();
    } else {
      hideSyncAlert();
      return false;
    }
  }

  // Chat folders

  private void showChatListOptions (TdApi.ChatList chatList) {
    boolean isMain = TD.isChatListMain(chatList);
    boolean isFolder = TD.isChatListFolder(chatList);
    boolean isArchive = TD.isChatListArchive(chatList);
    int chatFolderId = isFolder ? ((TdApi.ChatListFolder) chatList).chatFolderId : 0;
    TdApi.ChatFolderInfo chatFolderInfo;
    CharSequence title;
    if (isMain) {
      title = Lang.getString(R.string.CategoryMain);
      chatFolderInfo = null;
    } else if (isArchive) {
      title = Lang.getString(R.string.CategoryArchive);
      chatFolderInfo = null;
    } else if (isFolder) {
      chatFolderInfo = tdlib.chatFolderInfo(chatFolderId);
      title = chatFolderInfo != null ? TD.toCharSequence(chatFolderInfo.name) : null;
    } else {
      title = null;
      chatFolderInfo = null;
    }
    Options.Builder options = new Options.Builder();
    if (!StringUtils.isEmptyOrBlank(title)) {
      options.info(title);
    }
    if (tdlib.hasUnreadChats(chatList)) {
      options.item(new OptionItem(R.id.btn_markFolderAsRead, Lang.getString(R.string.MarkFolderAsRead), OptionColor.NORMAL, R.drawable.baseline_done_all_24));
    }
    if (isFolder) {
      options.item(new OptionItem(R.id.btn_editFolder, Lang.getString(R.string.EditFolder), OptionColor.NORMAL, R.drawable.baseline_edit_24));
      int chatFolderStyle = tdlib.settings().chatFolderStyle();
      if (hasIcon(chatFolderStyle)) {
        options.item(new OptionItem(R.id.btn_changeFolderIcon, Lang.getString(R.string.ChatFolderChangeIcon), OptionColor.NORMAL, R.drawable.baseline_image_24));
      }
      options.item(new OptionItem(R.id.btn_folderIncludeChats, Lang.getString(R.string.ChatFolderAddChats), OptionColor.NORMAL, R.drawable.baseline_add_24));
      if (chatFolderInfo != null) {
        options.item(new OptionItem(R.id.btn_shareFolder, Lang.getString(R.string.ShareFolder), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
      }
    }
    if (isMain) {
      if (!Config.RESTRICT_HIDING_MAIN_LIST) {
        options.item(new OptionItem(R.id.btn_hideFolder, Lang.getString(R.string.HideAllChats), OptionColor.NORMAL, R.drawable.baseline_eye_off_24));
      }
    } else if (isFolder || isArchive) {
      options.item(new OptionItem(R.id.btn_hideFolder, Lang.getString(R.string.HideFolder), OptionColor.NORMAL, R.drawable.baseline_eye_off_24));
    }
    if (isFolder) {
      options.item(new OptionItem(R.id.btn_removeFolder, Lang.getString(R.string.RemoveFolder), OptionColor.RED, R.drawable.baseline_delete_24));
    }
    if (options.itemCount() > 0) {
      options.item(OptionItem.SEPARATOR);
    }
    options.item(new OptionItem(R.id.btn_chatFolders, Lang.getString(R.string.EditFolders), OptionColor.NORMAL, R.drawable.baseline_edit_folders_24));
    showOptions(options.build(), (v, id) -> {
      if (id == R.id.btn_editFolder) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            EditChatFolderController controller = new EditChatFolderController(context, tdlib);
            controller.setArguments(new EditChatFolderController.Arguments(chatFolderId, chatFolder));
            navigateTo(controller);
          }
        }));
      } else if (id == R.id.btn_hideFolder) {
        tdlib.settings().setChatListEnabled(chatList, false);
        if (headerCell != null && !StringUtils.isEmptyOrBlank(title)) {
          CharSequence text = TD.isChatListArchive(chatList) ?
            Lang.getString(R.string.HideArchiveFolderInfo) :
            Lang.getString(R.string.HideFolderInfo2, title);
          context()
            .tooltipManager()
            .builder(headerCell.getTopView())
            .locate((targetView, outRect) -> outRect.left = outRect.right = Screen.dp(56f) - (int) targetView.getX())
            .controller(this)
            .show(tdlib, text)
            .hideDelayed(3500, TimeUnit.MILLISECONDS);
        }
      } else if (id == R.id.btn_folderIncludeChats) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            boolean showChatTypes = !chatFolder.isShareable;
            SelectChatsController controller = new SelectChatsController(context, tdlib);
            controller.setArguments(SelectChatsController.Arguments.includedChats(chatFolderId, chatFolder, showChatTypes));
            navigateTo(controller);
          }
        }));
      } else if (id == R.id.btn_changeFolderIcon) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            ChatFolderIconSelector.show(this, chatFolder, selectedIcon ->
              tdlib.setChatFolderIcon(chatFolderId, selectedIcon, Config.CHAT_FOLDERS_UNSET_DEFAULT_ICONS)
            );
          }
        }));
      } else if (id == R.id.btn_shareFolder) {
        TdApi.ChatFolderInfo info = ObjectsCompat.requireNonNull(chatFolderInfo);
        if (info.hasMyInviteLinks) {
          tdlib.send(new TdApi.GetChatFolderInviteLinks(chatFolderId), (result, error) -> {
            if (error != null) {
              UI.showError(error);
            } else {
              runOnUiThreadOptional(() -> showChatFolderInviteLinks(chatFolderId, title, result.inviteLinks));
            }
          });
        } else {
          return onCreateChatFolderInviteLinkClick(v, chatFolderId, info.isShareable, /* chatFolderInviteLinkCount */ 0L);
        }
      } else if (id == R.id.btn_removeFolder) {
        tdlib.ui().showDeleteChatFolderOrLeaveChats(this, chatFolderId);
      } else if (id == R.id.btn_chatFolders) {
        navigateTo(new SettingsFoldersController(context, tdlib));
      } else if (id == R.id.btn_markFolderAsRead) {
        tdlib.readAllChats(chatList, /* after */ null);
      }
      return true;
    });
  }

  private void showChatFolderInviteLinks (int chatFolderId, @Nullable CharSequence title, TdApi.ChatFolderInviteLink[] inviteLinks) {
    Options.Builder options = new Options.Builder();
    if (!StringUtils.isEmptyOrBlank(title)) {
      options.info(title);
    }
    long chatFolderInviteLinkCount = inviteLinks.length;
    long chatFolderInviteLinkCountMax = tdlib.chatFolderInviteLinkCountMax();
    boolean canCreateInviteLink = chatFolderInviteLinkCount < chatFolderInviteLinkCountMax;
    int createNewLinkOptionColor = canCreateInviteLink ? OptionColor.BLUE : OptionColor.INACTIVE;
    SparseArrayCompat<TdApi.ChatFolderInviteLink> inviteLinkById = new SparseArrayCompat<>(inviteLinks.length);
    for (TdApi.ChatFolderInviteLink inviteLink : inviteLinks) {
      int id = ViewCompat.generateViewId();
      inviteLinkById.append(id, inviteLink);
      String name = StringUtils.isEmptyOrInvisible(inviteLink.name) ? StringUtils.urlWithoutProtocol(inviteLink.inviteLink) : inviteLink.name;
      options.item(new OptionItem(id, name, OptionColor.NORMAL, R.drawable.baseline_link_24));
    }
    options.item(new OptionItem(R.id.btn_createInviteLink, Lang.getString(R.string.CreateANewLink), createNewLinkOptionColor, R.drawable.baseline_add_link_24));
    PopupLayout popupLayout = showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_createInviteLink) {
        return onCreateChatFolderInviteLinkClick(view, chatFolderId, true, chatFolderInviteLinkCount);
      }
      TdApi.ChatFolderInviteLink inviteLink = ObjectsCompat.requireNonNull(inviteLinkById.get(id));
      showChatFolderInviteLinkOptions(chatFolderId, inviteLink);
      return true;
    });
    if (!canCreateInviteLink) {
      View button = popupLayout.getBoundView().findViewById(R.id.btn_createInviteLink);
      if (button instanceof TextView) {
        TextView textView = (TextView) button;
        Views.setMediumText(textView, textView.getText());
        Drawable[] drawables = textView.getCompoundDrawables();
        Drawable lock = Drawables.get(R.drawable.baseline_lock_16);
        lock.setColorFilter(Paints.getColorFilter(Theme.textAccentColor()));
        addThemeFilterListener(lock, ColorId.text);
        button.setTag(R.id.tag_tooltip_location_provider, (TooltipOverlayView.LocationProvider) (targetView, outRect) -> {
          outRect.set(lock.getBounds());
          int top = (targetView.getHeight() - outRect.height()) / 2;
          int left = Lang.rtl() ? targetView.getPaddingLeft() : targetView.getWidth() - targetView.getPaddingRight() - outRect.width();
          outRect.offsetTo(left, top);
          outRect.inset(0, -Screen.dp(10f));
        });
        if (Lang.rtl()) {
          textView.setCompoundDrawablesWithIntrinsicBounds(lock, null, drawables[2], null);
        } else {
          textView.setCompoundDrawablesWithIntrinsicBounds(drawables[0], null, lock, null);
        }
      }
    }
  }

  private boolean onCreateChatFolderInviteLinkClick (View view, int chatFolderId, boolean isChatFolderShareable, long chatFolderInviteLinkCount) {
    if (chatFolderInviteLinkCount == 0 && !isChatFolderShareable && !tdlib.canAddShareableFolder()) {
      showShareableFoldersLimitReached(view);
      return false;
    }
    if (chatFolderInviteLinkCount >= tdlib.chatFolderInviteLinkCountMax()) {
      showChatFolderInviteLinksLimitReached(view);
      return false;
    }
    createChatFolderInviteLink(chatFolderId);
    return true;
  }

  private void showShareableFoldersLimitReached (View view) {
    UI.forceVibrateError(view);
    PopupLayout popupLayout = PopupLayout.parentOf(view);
    tdlib.ui().showLimitReachedInfo(this, popupLayout.tooltipManager(), view, TdlibUi.PremiumLimit.SHAREABLE_FOLDER_COUNT);
  }

  private void showChatFolderInviteLinksLimitReached (View view) {
    UI.forceVibrateError(view);
    PopupLayout popupLayout = PopupLayout.parentOf(view);
    tdlib.ui().showLimitReachedInfo(this, popupLayout.tooltipManager(), view, TdlibUi.PremiumLimit.CHAT_FOLDER_INVITE_LINK_COUNT);
  }

  private void showChatFolderInviteLinkOptions (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {
    String title = StringUtils.isEmptyOrInvisible(inviteLink.name) ? StringUtils.urlWithoutProtocol(inviteLink.inviteLink) : inviteLink.name;
    Options.Builder options = new Options.Builder();
    options.info(title);
    options.item(new OptionItem(R.id.btn_shareLink, Lang.getString(R.string.ShareLink), OptionColor.NORMAL, R.drawable.baseline_share_arrow_24));
    options.item(new OptionItem(R.id.btn_copyLink, Lang.getString(R.string.InviteLinkCopy), OptionColor.NORMAL, R.drawable.baseline_content_copy_24));
    options.item(new OptionItem(R.id.btn_edit, Lang.getString(R.string.InviteLinkEdit), OptionColor.NORMAL, R.drawable.baseline_edit_24));
    showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_shareLink) {
        tdlib.ui().shareUrl(this, inviteLink.inviteLink);
      } else if (id == R.id.btn_copyLink) {
        UI.copyText(inviteLink.inviteLink, R.string.CopiedLink);
      } else if (id == R.id.btn_edit) {
        tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) ->  runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
            controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, chatFolder, inviteLink));
            navigateTo(controller);
          }
        }));
      } else {
        throw new UnsupportedOperationException();
      }
      return true;
    });
  }

  @AnyThread
  private void createChatFolderInviteLink (int chatFolderId) {
    tdlib.send(new TdApi.GetChatFolder(chatFolderId), (chatFolder, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        createChatFolderInviteLink(chatFolderId, chatFolder);
      }
    });
  }

  @AnyThread
  private void createChatFolderInviteLink (int chatFolderId, TdApi.ChatFolder chatFolder) {
    if (TD.countIncludedChatTypes(chatFolder) > 0 || TD.countExcludedChatTypes(chatFolder) > 0) {
      runOnUiThreadOptional(() -> {
        CharSequence message = Lang.getMarkdownString(this, R.string.ChatFolderInviteLinkChatTypesNotSupported);
        if (showFolderTooltip(chatFolderId, message)) {
          View topView = headerCell != null ? headerCell.getTopView() : null;
          if (topView != null) {
            UI.forceVibrateError(topView);
          }
        } else if (!com.fahadali.xgram.appearance.XgramSnackbar.show(message, Toast.LENGTH_LONG)) { // XGram M3E: a snackbar
          UI.showCustomToast(message, Toast.LENGTH_LONG, 0);
        }
      });
      return;
    }
    tdlib.send(new TdApi.GetChatsForChatFolderInviteLink(chatFolderId), (chats, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        createChatFolderInviteLink(chatFolderId, chatFolder, chats.chatIds);
      }
    });
  }

  @AnyThread
  private void createChatFolderInviteLink (int chatFolderId, TdApi.ChatFolder chatFolder, long[] shareableChatIds) {
    if (shareableChatIds.length == 0) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
        UI.showToast(R.string.ChatFolderInviteLinkNoChatsToShare, Toast.LENGTH_SHORT); // XGram M3E: a snackbar (any thread)
      } else {
        UI.showCustomToast(R.string.ChatFolderInviteLinkNoChatsToShare, Toast.LENGTH_SHORT, 0);
      }
      return;
    }
    tdlib.createChatFolderInviteLink(chatFolderId, /* name */ "", shareableChatIds, (inviteLink, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        EditChatFolderInviteLinkController controller = new EditChatFolderInviteLinkController(context, tdlib);
        controller.setArguments(new EditChatFolderInviteLinkController.Arguments(chatFolderId, chatFolder, shareableChatIds, inviteLink));
        navigateTo(controller);
      }
    }));
  }

  private @Nullable ViewGroup bottomBar;
  private @Nullable FillingSpace bottomBarSpace;
  private @Px float bottomBarOffsetByPlayer;
  private @Px float bottomBarOffsetBySnackBar;
  private final BoolAnimator isBottomBarVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    invalidateBottomBarOffset();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l, true);

  private void showBottomBar () {
    if (bottomBar != null) {
      isBottomBarVisible.setValue(true, isFocused());
    }
  }

  private void hideBottomBar () {
    if (bottomBar != null) {
      isBottomBarVisible.setValue(false, isFocused());
    }
  }

  private void invalidateBottomBarOffset () {
    if (bottomBar != null) {
      float bottomBarOffsetByScroll = (getHeaderHeight() + Screen.dp(2f) + Views.getBottomMargin(bottomBar)) * (1f - isBottomBarVisible.getFloatValue());
      float y = bottomBarOffsetByPlayer + bottomBarOffsetByScroll + bottomBarOffsetBySnackBar;
      bottomBar.setTranslationY(y);
      bottomBarSpace.setTranslationY(Math.max(0, y));
    }
  }

  @Override
  protected boolean applyPlayerOffset (float factor, float top) {
    boolean result = super.applyPlayerOffset(factor, top);
    if (result) {
      bottomBarOffsetByPlayer = factor < 1f ? -top : 0;
      invalidateBottomBarOffset();
    }
    return result;
  }

  private boolean showFolderTooltip (int chatFolderId, CharSequence message) {
    return showFolderTooltip(new TdApi.ChatListFolder(chatFolderId), message);
  }

  private boolean showFolderTooltip (TdApi.ChatList chatList, CharSequence message) {
    if (headerCell == null) {
      return false;
    }
    int index = indexOfChatList(chatList);
    if (index == -1) {
      return false;
    }
    ViewPagerTopView topView = headerCell.getTopView();
    Rect itemRect = new Rect();
    if (topView == null || !topView.getItemRect(index, itemRect)) {
      return false;
    }
    int topViewX = Math.round(topView.getX());
    int topViewParentWidth = ((View) topView.getParent()).getWidth();
    int horizontalInset = Screen.dp(16f);
    itemRect.left = Math.max(itemRect.left, horizontalInset - topViewX);
    itemRect.right = Math.min(itemRect.right, topViewParentWidth - topViewX - horizontalInset);
    if (itemRect.isEmpty()) {
      return false;
    }
    context()
      .tooltipManager()
      .builder(topView)
      .locate((targetView, outRect) -> outRect.set(itemRect))
      .controller(this)
      .show(tdlib, message)
      .hideDelayed(3500, TimeUnit.MILLISECONDS);
    return true;
  }

  private int indexOfChatList (TdApi.ChatList chatList) {
    for (int i = 0; i < pagerChatLists.size(); i++) {
      if (Td.equalsTo(chatList, pagerChatLists.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private void checkTabs () {
    if (headerCell == null)
      return;
    ViewPagerHeaderViewCompact headerCellView = (ViewPagerHeaderViewCompact) headerCell.getView();
    boolean hasFolders = hasFolders();
    boolean displayTabsAtBottom = displayTabsAtBottom();
    headerCell.getTopView().setAnimateItemChanges(!Settings.instance().needReduceMotion());
    headerCell.getTopView().setShowLabelOnActiveOnly(hasFolders && tdlib.settings().chatFolderStyle() == ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE_FOLDER);
    headerCell.getTopView().setUseDarkBackground(displayTabsAtBottom);
    headerCell.getTopView().setDrawSelectionAtTop(displayTabsAtBottom);
    headerCell.getTopView().setSlideOffDirection(displayTabsAtBottom ? ViewPagerTopView.SLIDE_OFF_DIRECTION_TOP : ViewPagerTopView.SLIDE_OFF_DIRECTION_BOTTOM);
    headerCell.getTopView().setItemPadding(Screen.dp(hasFolders ? ViewPagerTopView.xgramCompactItemPadding() : ViewPagerTopView.DEFAULT_ITEM_PADDING)); // XGram: 16dp in Expressive
    headerCell.getTopView().setItemSpacing(Screen.dp(hasFolders ? ViewPagerTopView.COMPACT_ITEM_SPACING : ViewPagerTopView.DEFAULT_ITEM_SPACING));
    headerCellView.setFadingEdgeLength(displayTabsAtBottom ? 0f : 16f);

    if (displayTabsAtBottom) {
      if (bottomBar == null) {
        ViewGroup parent = (ViewGroup) headerCellView.getParent();
        if (parent != null) {
          if (headerView != null && isFocused() && !inTransformMode()) {
            headerView.setTitle(this);
            View titleView = headerView.findViewById(getId());
            if (titleView != null) {
              titleView.setAlpha(1f);
              titleView.setTranslationX(0);
              titleView.setTranslationY(0);
              titleView.setVisibility(View.VISIBLE);
            }
          } else {
            parent.removeView(headerCellView);
          }
        }
        int shadowHeight = Screen.dp(3f);
        ShadowView shadowView = new ShadowView(context);
        shadowView.setVerticalShadow(new int[]{0x00000000, 0x40000000}, null, shadowHeight);
        shadowView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, shadowHeight));
        if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled()) {
          shadowView.setVisibility(View.INVISIBLE); // XGram: Material 3 Expressive bars are separated by tone, not shadows
        }
        Views.setTopMargin(headerCellView, shadowHeight - Screen.dp(1f));

        ViewSupport.setThemedBackground(headerCellView, ColorId.headerLightBackground, this);
        headerCell.getTopView().setSelectionColorId(ColorId.headerLightText, .9f);
        headerCell.getTopView().setTextFromToColorId(ColorId.headerLightText, ColorId.headerLightText, PropertyId.SUBTITLE_ALPHA);

        int headerHeight = getHeaderHeight();
        bottomBar = new FrameLayoutFix(context);
        bottomBar.addView(shadowView);
        bottomBar.addView(headerCellView);
        pagerWrap.addView(bottomBar, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight + Views.getTopMargin(headerCellView), Gravity.BOTTOM));

        bottomBarSpace = new FillingSpace(context);
        bottomBarSpace.setThemedBackground(ColorId.headerLightBackground, this);
        pagerWrap.addView(bottomBarSpace, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));

        updateBottomBarMargins();

        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          isBottomBarVisible.setValue(true, false);
          invalidateBottomBarOffset();
        }
      }
      headerCellView.setAlpha(1f);
      headerCellView.setTranslationX(0f);
      headerCellView.setTranslationY(0f);
      headerCellView.setVisibility(View.VISIBLE);
      headerCellView.setScaleFactor(0f, 0f, 0f, false);
    } else {
      if (bottomBar != null) {
        pagerWrap.removeView(bottomBar);
        pagerWrap.removeView(bottomBarSpace);
        bottomBarSpace.performDestroy();
        bottomBar.removeView(headerCellView);
        bottomBar = null;
        bottomBarSpace = null;
        if (Config.CHAT_FOLDERS_HIDE_BOTTOM_BAR_ON_SCROLL) {
          isBottomBarVisible.setValue(false, false);
        }
        headerCell.getTopView().setSelectionColorId(ColorId.headerTabActive);
        headerCell.getTopView().setTextFromToColorId(ColorId.headerTabInactiveText, ColorId.headerTabActiveText);
        ViewUtils.setBackground(headerCellView, null);
        Views.setTopMargin(headerCellView, 0);
        if (headerView != null && isFocused() && !inTransformMode()) {
          headerView.setTitle(this);
        }
      }
    }
  }

  private void checkMenu () {
    if (menu != null) {
      FrameLayoutFix.LayoutParams layoutParams = (FrameLayoutFix.LayoutParams) menu.getLayoutParams();
      boolean displayTabsAtBottom = displayTabsAtBottom();
      int gravity = (displayTabsAtBottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.LEFT;
      int bottomMargin = displayTabsAtBottom ? getHeaderHeight() : 0;
      if (menu.setTop(!displayTabsAtBottom)) {
        menu.setDefaultPadding();
        if (xgramMenuPills == null) { // XGram M3E: the Material 3 menu keeps its own 8dp padding
          menu.setPadding(menu.getPaddingLeft(), menu.getPaddingTop() + Screen.dp(14f), menu.getPaddingRight(), menu.getPaddingBottom() + Screen.dp(13f));
        }
      }
      if (gravity != layoutParams.gravity || bottomMargin != layoutParams.bottomMargin) {
        layoutParams.gravity = gravity;
        layoutParams.bottomMargin = bottomMargin;
      }
    }
  }

  private boolean displayTabsAtBottom () {
    // XGram M3E: with the floating navigation toolbar the folder tabs are at the top, as in Telegram (the setting stays)
    return hasFolders() && !tdlib.settings().displayFoldersAtTop() && !xgramToolbarShownHere();
  }

  private void initPagerSections () {
    if (Settings.instance().chatFoldersEnabled()) {
      updatePagerSections(true);
    }
  }

  private boolean needUpdatePagerSections;

  private void updatePagerSections (boolean force) {
    if (force || isAttachedToNavigationController()) {
      updatePagerSections(tdlib.chatFolders(), tdlib.mainChatListPosition(), tdlib.settings().archiveChatListPosition());
    } else {
      needUpdatePagerSections = true;
    }
  }

  private void updatePagerSections (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition, int archiveChatListPosition) {
    boolean oldHasFolders = hasFolders();
    List<TdApi.ChatList> chatLists;
    List<ViewPagerTopView.Item> sections;
    if (com.fahadali.xgram.XgramSettings.instance().hideFolders(tdlib.accountId())) {
      chatFolders = new TdApi.ChatFolderInfo[0]; // XGram: folder tabs hidden here only, the folders stay on the account
    }
    boolean xgramArchiveTab = xgramArchiveTabEnabled();
    if (chatFolders.length > 0 || xgramArchiveTab) {
      int chatListCount = chatFolders.length;
      if (tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_MAIN)) {
        mainChatListPosition = MathUtils.clamp(mainChatListPosition, 0, chatListCount);
        chatListCount++;
      } else {
        mainChatListPosition = NO_POSITION;
      }
      if (xgramArchiveTab) {
        archiveChatListPosition = MathUtils.clamp(archiveChatListPosition, 0, chatListCount);
        chatListCount++;
        if (mainChatListPosition >= archiveChatListPosition) {
          mainChatListPosition++;
        }
      } else {
        archiveChatListPosition = NO_POSITION;
      }
      @ChatFolderStyle int chatFolderStyle = tdlib.settings().chatFolderStyle();
      chatLists = new ArrayList<>(chatListCount);
      sections = new ArrayList<>(chatListCount);
      int chatFolderIndex = 0;
      for (int position = 0; position < chatListCount; position++) {
        TdApi.ChatList chatList;
        ViewPagerTopView.Item sectionItem;
        if (position == mainChatListPosition) {
          chatList = ChatPosition.CHAT_LIST_MAIN;
          sectionItem = buildMainSectionItem(position, chatFolderStyle);
        } else if (position == archiveChatListPosition) {
          chatList = ChatPosition.CHAT_LIST_ARCHIVE;
          sectionItem = buildArchiveSectionItem(position, chatFolderStyle);
        } else {
          TdApi.ChatFolderInfo chatFolder = chatFolders[chatFolderIndex++];
          if (!tdlib.settings().isChatFolderEnabled(chatFolder.id))
            continue;
          chatList = new TdApi.ChatListFolder(chatFolder.id);
          sectionItem = buildSectionItem(getPagerItemId(chatList), position, chatList, chatFolder, chatFolderStyle);
        }
        chatLists.add(chatList);
        sections.add(sectionItem);
      }
    } else {
      sections = Collections.emptyList();
      chatLists = Collections.emptyList();
    }
    if (chatLists.size() > 1 || (!chatLists.isEmpty() && !TD.isChatListMain(chatLists.get(0)))) {
      this.pagerSections = sections;
      this.pagerChatLists = chatLists;
      this.chatFolderInfos = chatFolders;
      if (chatListUnreadCountListener == null) {
        chatListUnreadCountListener = new ChatListUnreadCountListener();
        tdlib.listeners().addTotalChatCounterListener(chatListUnreadCountListener);
      }
    } else {
      this.pagerSections = Collections.emptyList();
      this.pagerChatLists = Collections.emptyList();
      this.chatFolderInfos = null;
      if (chatListUnreadCountListener != null) {
        tdlib.listeners().removeTotalChatCounterListener(chatListUnreadCountListener);
        chatListUnreadCountListener = null;
      }
    }
    notifyPagerItemPositionsChanged();
    ViewPager pager = getViewPager();
    if (pager != null) {
      onPageSelected(pager.getCurrentItem());
    }
    if (oldHasFolders != hasFolders() && pagerWrap != null) {
      checkTabs();
      checkMenu();
      checkMargins();
      showFoldersSetupSuggestion();
      xgramReapplyHeader(); // XGram M3E: with the navigation rail, folder tabs or the page's title
      com.fahadali.xgram.layout.XgramTwoPane twoPane = context.xgramTwoPane();
      if (twoPane != null) {
        twoPane.onRailDestinationsChanged(); // Calls is a page of this list only without folders
      }
      xgramUpdateNavBar(); // XGram: the same for the bottom navigation bar; the folder tabs may be at the bottom now
      xgramLayoutNavBar();
    }
  }

  private ViewPagerTopView.Item buildMainSectionItem (int pagerItemPosition, @ChatFolderStyle int chatFolderStyle) {
    CharSequence sectionName = getMenuSectionName(MAIN_PAGER_ITEM_ID, pagerItemPosition, /* hasFolders */ true, chatFolderStyle, /* upperCase */ false);
    int iconResource = menuNeedArchive ? R.drawable.baseline_archive_24 : R.drawable.baseline_forum_24;
    return buildSectionItem(MAIN_PAGER_ITEM_ID, pagerItemPosition, ChatPosition.CHAT_LIST_MAIN, sectionName, iconResource, chatFolderStyle);
  }

  private ViewPagerTopView.Item buildArchiveSectionItem (int pagerItemPosition, @ChatFolderStyle int chatFolderStyle) {
    CharSequence sectionName = getMenuSectionName(ARCHIVE_PAGER_ITEM_ID, pagerItemPosition, /* hasFolders */ true, chatFolderStyle, /* upperCase */ false);
    int iconResource = R.drawable.baseline_archive_24;
    return buildSectionItem(ARCHIVE_PAGER_ITEM_ID, pagerItemPosition, ChatPosition.CHAT_LIST_ARCHIVE, sectionName, iconResource, chatFolderStyle);
  }

  private ViewPagerTopView.Item buildSectionItem (long pagerItemId, int pagerItemPosition, TdApi.ChatList chatList, TdApi.ChatFolderInfo chatFolderInfo, @ChatFolderStyle int chatFolderStyle) {
    CharSequence sectionName = getFolderSectionName(pagerItemId, TD.toCharSequence(chatFolderInfo.name), chatFolderStyle);
    int iconResource = TD.findFolderIcon(chatFolderInfo.icon, R.drawable.baseline_folder_24);
    return buildSectionItem(pagerItemId, pagerItemPosition, chatList, sectionName, iconResource, chatFolderStyle);
  }

  private ViewPagerTopView.Item buildSectionItem (long pagerItemId, int pagerItemPosition, TdApi.ChatList chatList, CharSequence sectionName, @DrawableRes int iconResource, @ChatFolderStyle int chatFolderStyle) {
    int selectedFilter = getSelectedFilter(pagerItemId);
    Counter unreadCounter = buildUnreadCounter(pagerItemId, selectedFilter);
    if (unreadCounter != null) {
      TdlibCounter counter = tdlib.getCounter(chatList);
      updateCounter(chatList, unreadCounter, counter, /* animated */ false);
    }
    boolean showIcon = hasIcon(chatFolderStyle) || (pagerItemId == MAIN_PAGER_ITEM_ID && pagerItemPosition == 0);
    if (!showIcon) {
      iconResource = ResourcesCompat.ID_NULL;
    }
    if (TextUtils.isEmpty(sectionName)) {
      sectionName = null;
    }
    ViewPagerTopView.Item item = new ViewPagerTopView.Item(sectionName, iconResource, unreadCounter);
    item.setMinWidth(Screen.dp(56f - ViewPagerTopView.xgramCompactItemPadding() * 2)); // XGram: a tab stays at least 56dp with either padding
    return item;
  }

  private @Nullable Counter buildUnreadCounter (long pagerItemId, int selectedFilter) {
    if (selectedFilter != FILTER_NONE || (pagerItemId == MAIN_PAGER_ITEM_ID && menuNeedArchive)) {
      return null;
    }
    UnreadCounterColorSet unreadCounterColorSet = UnreadCounterColorSet.create(this);
    Counter unreadCounter = new Counter.Builder()
      .textSize(12f)
      .backgroundPadding(4f)
      .outlineAffectsBackgroundSize(false)
      .colorSet(unreadCounterColorSet)
      .callback(unreadCounterCallback(pagerItemId))
      .build();
    unreadCounterColorSet.setCounter(unreadCounter);
    return unreadCounter;
  }

  private Counter.Callback unreadCounterCallback (long pagerItemId) {
    return (counter, sizeChanged) -> {
      if (headerCell != null) {
        int position = getPagerItemPosition(pagerItemId);
        if (position != NO_POSITION) {
          ViewPagerTopView topView = headerCell.getTopView();
          if (sizeChanged) {
            topView.requestItemLayoutAt(position);
          }
          topView.invalidate();
        }
      }
    };
  }

  private void updateCounter (TdApi.ChatList chatList, Counter target, TdlibCounter counter, boolean animated) {
    updateCounter(tdlib, chatList, target, counter, animated);
  }

  public static void updateCounter (Tdlib tdlib, TdApi.ChatList chatList, Counter target, TdlibCounter counter, boolean animated) {
    int mutedCount, unmutedCount;
    int badgeFlags = tdlib.settings().getChatFolderBadgeFlags();
    boolean countMessages = BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MESSAGES);
    if (countMessages) {
      mutedCount = Math.max(0, counter.messageCount);
      unmutedCount = Math.max(0, counter.messageUnmutedCount);
    } else {
      mutedCount = Math.max(0, counter.chatCount);
      unmutedCount = Math.max(0, counter.chatUnmutedCount);
    }
    if (BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_ARCHIVED) && chatList.getConstructor() == TdApi.ChatListMain.CONSTRUCTOR) {
      TdlibCounter archiveCounter = tdlib.getCounter(ChatPosition.CHAT_LIST_ARCHIVE);
      if (countMessages) {
        mutedCount += Math.max(0, archiveCounter.messageCount);
        unmutedCount += Math.max(0, archiveCounter.messageUnmutedCount);
      } else {
        mutedCount += Math.max(0, archiveCounter.chatCount);
        unmutedCount += Math.max(0, archiveCounter.chatUnmutedCount);
      }
    }
    if (BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MUTED)) {
      boolean muted = mutedCount > 0 ? unmutedCount == 0 : target.isMuted();
      target.setCount(mutedCount, muted, animated);
    } else {
      target.setCount(unmutedCount, false, animated);
    }
  }

  public static class UnreadCounterColorSet implements TextColorSet {
    private @Nullable Counter counter;
    private final FutureBool displayTabsAtBottom;

    public UnreadCounterColorSet (FutureBool displayTabsAtBottom) {
      this.displayTabsAtBottom = displayTabsAtBottom;
    }

    private static UnreadCounterColorSet create(MainController parent) {
      return new UnreadCounterColorSet(parent::displayTabsAtBottom);
    }

    public void setCounter (@Nullable Counter counter) {
      this.counter = counter;
    }

    @ColorInt
    @Override
    public int defaultTextColor () {
      return counter != null ? ColorUtils.fromToArgb(foregroundColor(), backgroundColor(), counter.getMuteFactor()) : foregroundColor();
    }

    @ColorInt
    @Override
    public int backgroundColor (boolean isPressed) {
      return counter != null ? ColorUtils.alphaColor(1f - counter.getMuteFactor(), backgroundColor()) : backgroundColor();
    }

    @ColorInt
    @Override
    public int outlineColor (boolean isPressed) {
      return counter != null ? backgroundColor() : Color.TRANSPARENT;
    }

    @ColorInt
    private int foregroundColor () {
      return Theme.getColor(displayTabsAtBottom.getBoolValue() ? ColorId.headerLightBackground : ColorId.headerBackground);
    }

    @ColorInt
    private int backgroundColor() {
      return Theme.getColor(displayTabsAtBottom.getBoolValue() ? ColorId.headerLightText : ColorId.headerText);
    }
  }

  private @Nullable ChatListPositionListener chatListPositionListener;
  private @Nullable ChatListUnreadCountListener chatListUnreadCountListener;

  private class ChatListPositionListener implements TdlibSettingsManager.ChatListPositionListener {
    @Override
    public void onArchiveChatListPositionChanged (Tdlib tdlib, int archiveChatListPosition) {
      if (tdlib.settings().isChatListEnabled(ChatPosition.CHAT_LIST_ARCHIVE)) {
        updatePagerSections(false);
      }
    }

    @Override
    public void onChatListStateChanged (Tdlib tdlib, TdApi.ChatList chatList, boolean isEnabled) {
      updatePagerSections(false);
    }

    @Override
    public void onBadgeFlagsChanged (Tdlib tdlib, int newFlags) {
      if (hasFolders()) {
        updatePagerCounters();
      }
    }

    @Override
    public void onChatFolderStyleChanged (Tdlib tdlib, int newStyle) {
      onFoldersAppearanceChanged();
    }

    @Override
    public void onChatFolderOptionsChanged (Tdlib tdlib, int newOptions) {
      onFoldersAppearanceChanged();
    }
  }

  @Override
  public void onBadgeSettingsChanged () {
    if (hasFolders()) {
      updatePagerCounters();
    }
  }

  private void onFoldersAppearanceChanged () {
    if (hasFolders()) {
      if (!displayTabsAtBottom() && globalFilter != FILTER_NONE) {
        applyGlobalFilter(FILTER_NONE);
      }
      updatePagerSections(false);
    }
  }

  @Override
  public void onChatFolderOptionsChanged (int newOptions) {
    onFoldersAppearanceChanged();
  }

  @Override
  public void onChatFolderStyleChanged (int newStyle) {
    onFoldersAppearanceChanged();
  }

  private class ChatListUnreadCountListener implements CounterChangeListener {
    @Override
    public void onChatCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
      dispatchCounterChange(chatList, counter, false);
      /*TODO update visibility?
      if (chatList instanceof TdApi.ChatListArchive && availabilityChanged) {
        runOnUiThreadOptional(() -> {
          if (menu != null) {
            menu.getChildAt(FILTER_ARCHIVE).setVisibility(tdlib.hasArchivedChats() ? View.VISIBLE : View.GONE);
          }
        });
      }*/
    }

    @Override
    public void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
      dispatchCounterChange(chatList, counter, true);
    }

    private void dispatchCounterChange (TdApi.ChatList chatList, TdlibCounter counter, boolean areMessages) {
      runOnUiThreadOptional(() -> {
        int badgeFlags = tdlib.settings().getChatFolderBadgeFlags();
        if (areMessages != BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_MESSAGES)) {
          return;
        }
        dispatchCounter(chatList, counter);
        if (chatList.getConstructor() == TdApi.ChatListArchive.CONSTRUCTOR && BitwiseUtils.hasFlag(badgeFlags, Settings.BADGE_FLAG_ARCHIVED)) {
          dispatchCounter(ChatPosition.CHAT_LIST_MAIN, tdlib.getCounter(ChatPosition.CHAT_LIST_MAIN));
        }
      });
    }

    private void dispatchCounter (TdApi.ChatList chatList, TdlibCounter counter) {
      long itemId = getPagerItemId(chatList);
      int positionToUpdate = getPagerItemPosition(itemId);
      if (positionToUpdate != NO_POSITION) {
        ViewPagerTopView.Item pagerSection = pagerSections.get(positionToUpdate);
        if (pagerSection.counter != null) {
          updateCounter(chatList, pagerSection.counter, counter, isFocused());
        }
      }
    }
  }

  private void updatePagerCounters () {
    for (int i = 0; i < pagerSections.size(); i++) {
      TdApi.ChatList chatList = pagerChatLists.get(i);
      ViewPagerTopView.Item section = pagerSections.get(i);
      if (section.counter != null) {
        TdlibCounter counter = tdlib.getCounter(chatList);
        updateCounter(chatList, section.counter, counter, isFocused());
      }
    }
  }

  @Override
  public void onChatFoldersChanged (TdApi.ChatFolderInfo[] chatFolders, int mainChatListPosition) {
    if (Settings.instance().chatFoldersEnabled()) {
      runOnUiThreadOptional(() ->
        updatePagerSections(false)
      );
    }
  }

  private @Nullable PopupLayout globalFilterLayout;

  private void showGlobalFilter () {
    if (headerView == null) return;
    MenuMoreWrap menu = new MenuMoreWrap(context, /* scrollable */ true);
    menu.init(getThemeListeners());
    View.OnClickListener onFilterClickListener = v -> {
      PopupLayout popupLayout = PopupLayout.parentOf(v);
      popupLayout.hideWindow(true);
      int selectedFilter = (int) v.getTag(R.id.btn_filter);
      applyGlobalFilter(selectedFilter);
    };
    for (int globalFilter : GLOBAL_FILTERS) {
      if (globalFilter == FILTER_UNREAD && !tdlib.hasUnreadChats(pagerChatLists)) {
        continue;
      }
      int name = getGlobalFilterName(globalFilter);
      int icon = getFilterIcon(globalFilter, true);
      TextView itemView = menu.addItem(View.NO_ID, Lang.getString(name), icon, null, onFilterClickListener);
      itemView.setTag(R.id.btn_filter, globalFilter);
    }
    menu.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_HEADER);
    menu.setTranslationY(headerView.getTranslationY() + headerView.getCurrentHeaderOffset());
    globalFilterLayout = new PopupLayout(context);
    globalFilterLayout.init(true);
    globalFilterLayout.setNeedRootInsets();
    globalFilterLayout.setOverlayStatusBar(true);
    globalFilterLayout.setDismissListener((popup) -> globalFilterLayout = null);
    globalFilterLayout.showMoreView(menu);
  }

  private void hideGlobalFilter () {
    if (globalFilterLayout != null) {
      globalFilterLayout.hideWindow(/* animated */ isFocused());
      globalFilterLayout = null;
    }
  }

  private boolean applyGlobalFilter (@Filter int filterToApply) {
    if ((!useGlobalFilter() && filterToApply != FILTER_NONE) || (globalFilter == filterToApply && !menuNeedArchive && !hasSelectedFilters())) {
      return false;
    }
    globalFilter = filterToApply;
    pagerChatListFilters.clear();
    cancelPendingFilter();
    setNeedArchive(false);
    if (toggleHeaderView != null) {
      String title = Lang.getString(getGlobalFilterName(filterToApply));
      toggleHeaderView.setTitle(title, /* animated */ isFocused());
    }
    int itemCount = getPagerItemCount();
    List<ViewPagerTopView.Item> sections = new ArrayList<>(itemCount);
    int currentItemPosition = getCurrentPagerItemPosition();
    for (int itemPosition = 0; itemPosition < itemCount; itemPosition++) {
      // replace item
      long itemId = getPagerItemId(itemPosition);
      sections.add(buildSectionItem(itemId, itemPosition));
      ViewController<?> cachedController = getCachedControllerForPosition(itemPosition);
      TdApi.ChatList chatList = pagerChatLists.get(itemPosition);
      if (sameChatsController(cachedController, chatList, filterToApply)) {
        continue;
      }
      if (currentItemPosition == itemPosition) {
        postApplyFilter(itemId, chatList, filterToApply);
        continue;
      }
      // replace controller (if cached)
      if (cachedController != null) {
        ViewController<?> controller = newChatsController(chatList, filterToApply);
        modifyNewPagerItemController(controller);
        replaceController(itemId, controller, /* notifyAdapter */ false);
      }
    }
    notifyPagerItemsChanged();
    pagerSections = sections;
    if (headerCell != null) {
      headerCell.getTopView().setItems(sections);
    } else {
      needUpdatePagerSections = true;
    }
    return true;
  }

  private boolean hasIcon (@ChatFolderStyle int chatFolderStyle) {
    switch (chatFolderStyle) {
      case ChatFolderStyle.ICON_ONLY:
      case ChatFolderStyle.LABEL_AND_ICON:
      case ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE_FOLDER:
        return true;
      case ChatFolderStyle.LABEL_ONLY:
        return false;
      default:
        throw new IllegalArgumentException("chatFolderStyle = " + chatFolderStyle);
    }
  }

  private boolean sameChatsController (@Nullable ViewController<?> controller, TdApi.ChatList chatList, @Filter int filter) {
      if (!(controller instanceof ChatsController)) {
          return false;
      }
      ChatsController c = (ChatsController) controller;
      return Td.equalsTo(c.chatList(), chatList) && ObjectUtils.equals(filter, c.getArgumentsStrict().tag);
  }
}
