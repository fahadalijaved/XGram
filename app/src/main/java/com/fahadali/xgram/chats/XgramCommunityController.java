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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.appearance.XgramListStateView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import tgx.td.ChatId;
import tgx.td.ChatPosition;

/**
 * A Telegram community (groups and channels, with or without topics, gathered under one name): its chats, those you
 * are in first, like the official apps. Opened from "added to the community" messages and the chat menu.
 * <p>
 * Each chat is a full chat-list row (avatar, time, ticks, unread and mention badges, mute icon, typing) kept up to
 * date from TDLib's chat, message and notification updates. Long-pressing a row shows the chat list's menu; holding
 * one shows the chat preview.
 */
public final class XgramCommunityController extends RecyclerViewController<Long> implements View.OnClickListener, View.OnLongClickListener, BaseView.ActionListProvider, TdlibCache.CommunityDataChangeListener, XgramCommunities.Listener, MoreDelegate {
  private static final int TYPE_CHAT_ROW = 1;
  private static final int TYPE_LOADING_ROW = 2;
  /** XGram M3E r6: the empty state (no chats, nothing found) and the card of a failed full-info request. */
  private static final int TYPE_EMPTY_ROW = 3, TYPE_ERROR_CARD = 4;
  private static final long REBUILD_DELAY_MS = 200;
  /** notifyItemChanged payload: the item animator keeps the bound view instead of fading a new one in. */
  private static final Object REBIND = new Object();

  private SettingsAdapter adapter;
  private ComplexHeaderView headerCell;
  private final Set<Long> requestedChats = new HashSet<>();
  /** Chats asked from TDLib (GetChat) that haven't answered yet. */
  private final Set<Long> pendingChats = new HashSet<>();
  /** TDLib's answer to GetCommunityFullInfo, when it is an error. */
  @Nullable private String loadError;
  /** TDLib's answer to GetCommunityFullInfo, for when its update didn't reach the cache. */
  @Nullable private TdApi.CommunityFullInfo fetchedFull;
  /** Waited long enough for the full info: show the chats known on this device. */
  private boolean useKnownChats;
  /** The chats' own info was asked for (once per page, see XgramCommunityData.refresh). */
  private boolean refreshed;

  /** One row per chat, UI thread. A row is replaced, never changed, once its chat changes. */
  private final Map<Long, TGChat> rows = new HashMap<>();
  /** Chats whose row is out of date (any thread); the next build makes them a new row. */
  private final Set<Long> dirtyChats = Collections.newSetFromMap(new ConcurrentHashMap<>());
  /** Every row is out of date (scope-wide notification settings). */
  private volatile boolean dirtyAll;
  /** Chats (and their supergroups) this page listens to. */
  private final Set<Long> subscribedChats = new HashSet<>();
  private boolean subscribedToScope;
  private final RowListener rowListener = new RowListener();
  /** The first topics of each forum on the page, for the second line of its row. */
  private XgramCommunityTopics topics;
  /** Search Chats: the lower-case query, empty when not searching. Rows matching it by title or username are listed flat. */
  private String searchQuery = "";

  private final Runnable rebuild = () -> {
    if (!isDestroyed()) {
      buildItems();
    }
  };

  public XgramCommunityController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static void open (ViewController<?> context, long communityId) {
    if (communityId == 0) {
      return;
    }
    XgramCommunityController c = new XgramCommunityController(context.context(), context.tdlib());
    c.setArguments(communityId);
    if (com.fahadali.xgram.layout.XgramTwoPane.replacesRightPane(context)) {
      com.fahadali.xgram.layout.XgramTwoPane.showOnRight(context, c); // two panes: replace, don't stack
      return;
    }
    // Chat lists live in the main screen's pager, where navigateTo() does nothing: use the activity's navigation.
    org.thunderdog.challegram.navigation.NavigationController navigation = context.navigationController();
    if (navigation == null) {
      navigation = context.context().navigation();
    }
    if (navigation != null) {
      navigation.navigateTo(c);
    }
  }

  private long communityId () {
    Long id = getArguments();
    return id != null ? id : 0;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramCommunity;
  }

  @Override
  public CharSequence getName () {
    TdApi.Community community = tdlib.cache().community(communityId());
    return community != null && !community.name.isEmpty() ? community.name : Lang.getString(R.string.XgramCommunity);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  // Header menu: Search Chats and the 3-dot menu (none in a preview)

  @Override
  protected int getMenuId () {
    return isInForceTouchMode() ? 0 : R.id.menu_xgramCommunity;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected int getSearchHint () {
    return R.string.XgramCommunitySearchHint;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, android.widget.LinearLayout menu) {
    if (id == R.id.menu_xgramCommunity) {
      header.addSearchButton(menu, this);
      header.addMoreButton(menu, this);
    } else {
      super.fillMenuItems(id, header, menu);
    }
  }

  @Override
  protected void openMoreMenu () {
    long communityId = communityId();
    if (communityId == 0 || isInForceTouchMode()) {
      return;
    }
    IntList ids = new IntList(2);
    StringList titles = new StringList(2);
    ids.append(R.id.btn_xgramCommunitySettings);
    titles.append(R.string.Settings);
    ids.append(R.id.btn_xgramCommunityMerge);
    final boolean merged = XgramCommunities.isMergeEnabled(tdlib, communityId);
    if (XgramExpressive.isEnabled()) {
      // XGram M3E r6: "Show as one chat" as a checkable menu item (checked while the community is one chat)
      titles.append(R.string.XgramCommunityShowOne);
      showMore(ids.get(), titles.get(), 0);
      org.thunderdog.challegram.widget.PopupLayout popup = context().getCurrentPopupWindow();
      if (popup != null && popup.getBoundView() instanceof org.thunderdog.challegram.navigation.MenuMoreWrap) {
        ((org.thunderdog.challegram.navigation.MenuMoreWrap) popup.getBoundView()).xgramSetCheckable(R.id.btn_xgramCommunityMerge, merged);
      }
      return;
    }
    titles.append(merged ? R.string.XgramCommunityShowSeparate : R.string.XgramCommunityShowOne);
    showMore(ids.get(), titles.get(), 0);
  }

  @Override
  public void onMoreItemPressed (int id) {
    long communityId = communityId();
    if (id == R.id.btn_xgramCommunitySettings) {
      openSettings();
    } else if (id == R.id.btn_xgramCommunityMerge && communityId != 0) {
      boolean merged = XgramCommunities.isMergeEnabled(tdlib, communityId);
      XgramCommunities.setMergeMode(tdlib, communityId, merged ? XgramCommunities.MODE_SEPARATE : XgramCommunities.MODE_ONE_CHAT);
      if (!com.fahadali.xgram.appearance.XgramSnackbar.show(Lang.getString(R.string.XgramCommunityMergeLocal), android.widget.Toast.LENGTH_SHORT)) {
        UI.showToast(R.string.XgramCommunityMergeLocal, android.widget.Toast.LENGTH_SHORT); // XGram M3E r6: the snackbar
      }
    }
  }

  /** The settings page: from the 3-dot menu and a tap on the header (not from a preview). */
  private void openSettings () {
    if (!isInForceTouchMode()) {
      XgramCommunitySettingsController.open(this, communityId());
    }
  }

  // Search Chats: the rows whose title or username contains the query, in one list

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    String trimmed = Lang.lowercase(query.trim());
    if (!trimmed.equals(searchQuery)) {
      searchQuery = trimmed;
      buildItems();
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    if (!searchQuery.isEmpty()) {
      searchQuery = "";
      buildItems();
    }
  }

  private boolean matchesSearch (TdApi.Chat chat) {
    if (Lang.lowercase(tdlib.chatTitle(chat)).contains(searchQuery)) {
      return true;
    }
    String username = tdlib.chatUsername(chat);
    return !StringUtils.isEmpty(username) && Lang.lowercase(username).contains(searchQuery);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    headerCell = new ComplexHeaderView(context, tdlib, this);
    headerCell.setNoExpand(true);
    headerCell.setPhotoOpenDisabled(true);
    headerCell.initWithController(this, true);
    headerCell.setInnerMargins(Screen.dp(56f), Screen.dp(49f) * 2);
    headerCell.setOnClickListener(v -> openSettings());
    updateHeader(0);
    topics = new XgramCommunityTopics(tdlib, this::markDirty);

    adapter = new SettingsAdapter(this) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        if (customViewType == TYPE_CHAT_ROW) {
          XgramCommunityChatView view = new XgramCommunityChatView(parent.getContext(), tdlib);
          view.setNeedBackground(true);
          view.setOnClickListener(XgramCommunityController.this);
          view.setOnLongClickListener(XgramCommunityController.this);
          view.setPreviewActionListProvider(XgramCommunityController.this);
          addThemeInvalidateListener(view);
          return new SettingHolder(view);
        }
        if (customViewType == TYPE_LOADING_ROW) {
          // XGram M3E: the contained loading indicator in place of the "Loading the community…" line
          XgramListStateView view = new XgramListStateView(parent.getContext());
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          view.setLoading(true, 38f);
          return new SettingHolder(view);
        }
        if (customViewType == TYPE_EMPTY_ROW) {
          // XGram M3E r6: the M3E empty state (a burst with an icon, a headline, supporting text)
          XgramListStateView view = new XgramListStateView(parent.getContext());
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          view.setWrapContent(true);
          return new SettingHolder(view);
        }
        if (customViewType == TYPE_ERROR_CARD) {
          return new SettingHolder(newErrorCard(parent.getContext()));
        }
        throw new IllegalArgumentException("customViewType=" + customViewType);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        if (customViewType == TYPE_CHAT_ROW) {
          ((XgramCommunityChatView) view).setChat(rows.get(item.getLongId()));
          ((XgramCommunityChatView) view).xgramSetTopicsLoading(xgramTopicsLoading.contains(item.getLongId()));
        } else if (customViewType == TYPE_LOADING_ROW) {
          // Alone, centred on the page; above the note about the chats this device has seen, a block of its own.
          int height = getItems().size() == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : Screen.dp(160f);
          ViewGroup.LayoutParams params = view.getLayoutParams();
          if (params != null && params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
          }
        } else if (customViewType == TYPE_EMPTY_ROW) {
          XgramListStateView state = (XgramListStateView) view;
          if (item.getStringResource() == R.string.NothingFound) {
            state.setEmpty(R.drawable.baseline_search_24, Lang.getString(R.string.NothingFound), null);
          } else {
            state.setEmpty(R.drawable.baseline_forum_24, null, Lang.getString(R.string.XgramCommunityEmpty));
          }
          // alone, centred on the page; above the note about the chats this device has seen, its own height
          int height = getItems().size() == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
          ViewGroup.LayoutParams params = view.getLayoutParams();
          if (params != null && params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
          }
        } else if (customViewType == TYPE_ERROR_CARD) {
          bindErrorCard(view);
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.cache().subscribeToCommunityUpdates(communityId(), this);
    XgramCommunities.addListener(this);
    requestFullInfo();
    tdlib.ui().postDelayed(() -> {
      if (!isDestroyed()) {
        if (communityFull() == null) {
          useKnownChats = true;
        }
        buildItems();
      }
    }, 2500);
    buildItems();
  }

  /** Asks TDLib for the community's chat list (on opening, and on Retry in the error card). */
  private void requestFullInfo () {
    tdlib.send(new TdApi.GetCommunityFullInfo(communityId()), (full, error) -> {
      if (full != null) {
        tdlib.cache().xgramOnCommunityFullLoaded(communityId(), full);
      }
      if (error != null) {
        android.util.Log.w("XGram", "GetCommunityFullInfo " + communityId() + ": " + error.code + " " + error.message);
      }
      runOnUiThreadOptional(() -> {
        retrying = false;
        if (error != null) {
          loadError = error.message;
          useKnownChats = true;
        } else {
          loadError = null;
          fetchedFull = full;
        }
        buildItems();
        // the error card stays when the structure is the same: bind it again (its text and the Retry button)
        List<ListItem> shown = adapter.getItems();
        for (int i = 0; i < shown.size(); i++) {
          if (shown.get(i).getViewType() == ListItem.TYPE_CUSTOM - TYPE_ERROR_CARD) {
            adapter.notifyItemChanged(i);
          }
        }
      });
    });
  }

  // XGram M3E r6: the error card (TDLib didn't send the chat list): surfaceContainerHigh with large corners 16dp in from
  // the sides, an error icon, the reason in bodyMedium and a Retry text button that asks again.

  private boolean retrying;

  private View newErrorCard (Context context) {
    android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
    frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frame.setPadding(Screen.dp(16f), Screen.dp(8f), Screen.dp(16f), Screen.dp(8f));

    android.widget.LinearLayout card = new android.widget.LinearLayout(context);
    card.setOrientation(android.widget.LinearLayout.VERTICAL);
    card.setPaddingRelative(Screen.dp(16f), Screen.dp(16f), Screen.dp(8f), Screen.dp(4f));
    com.fahadali.xgram.appearance.XgramR5Sheets.applyCard(card, XgramExpressive.SURFACE_CONTAINER_HIGH, XgramExpressive.CORNER_L);

    android.widget.LinearLayout row = new android.widget.LinearLayout(context);
    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    RoleIconView icon = new RoleIconView(context, XgramExpressive.ERROR);
    icon.setImageDrawable(org.thunderdog.challegram.tool.Drawables.get(com.fahadali.xgram.appearance.XgramIconPack.map(R.drawable.baseline_error_24)));
    android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f));
    iconParams.setMarginEnd(Screen.dp(16f));
    row.addView(icon, iconParams);
    android.widget.TextView text = new org.thunderdog.challegram.widget.NoScrollTextView(context);
    text.setId(R.id.text_title);
    XgramExpressive.applyText(text, XgramExpressive.TS_BODY_M, false);
    text.setTextColor(XgramExpressive.color(XgramExpressive.ON_SURFACE_VARIANT));
    addThemeTextColorListener(text, org.thunderdog.challegram.theme.ColorId.textLight);
    text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    textParams.setMarginEnd(Screen.dp(8f));
    row.addView(text, textParams);
    card.addView(row, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    android.widget.TextView retry = com.fahadali.xgram.appearance.XgramR5Sheets.textButton(context, Lang.getString(R.string.XgramR6B3Retry), XgramExpressive.PRIMARY, null);
    retry.setId(R.id.btn_retry);
    retry.setOnClickListener(v -> {
      if (!retrying && !isDestroyed()) {
        retrying = true;
        v.setEnabled(false);
        requestFullInfo();
      }
    });
    android.widget.LinearLayout.LayoutParams retryParams = new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(40f));
    retryParams.gravity = android.view.Gravity.END;
    retryParams.topMargin = Screen.dp(8f);
    retryParams.bottomMargin = Screen.dp(4f);
    card.addView(retry, retryParams);
    frame.addView(card, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return frame;
  }

  private void bindErrorCard (View view) {
    View text = view.findViewById(R.id.text_title);
    if (text instanceof android.widget.TextView) {
      ((android.widget.TextView) text).setText(loadError != null ? Lang.getString(R.string.XgramCommunityPartialError, loadError) : Lang.getString(R.string.XgramCommunityPartial));
    }
    View retry = view.findViewById(R.id.btn_retry);
    if (retry != null) {
      retry.setEnabled(!retrying);
    }
  }

  /** An icon tinted with a color role, read at draw time (theme changes). */
  private static final class RoleIconView extends android.widget.ImageView {
    private final int role;
    private int appliedColor;

    RoleIconView (Context context, int role) {
      super(context);
      this.role = role;
      setScaleType(ScaleType.CENTER_INSIDE);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw (android.graphics.Canvas canvas) {
      final int color = XgramExpressive.color(role);
      if (color != appliedColor) {
        appliedColor = color;
        setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
      }
      super.onDraw(canvas);
    }
  }

  // Like chats and topic lists: slide in once the chats are there, not on "Loading" swapped out mid-slide.
  @Override
  public boolean needAsynchronousAnimation () {
    return communityFull() == null && !useKnownChats;
  }

  // XGram M3E: a slow answer no longer holds the tap without feedback (up to 2.5 s): the page slides in with the
  // loading indicator after 150 ms.
  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return XgramExpressive.isEnabled() ? 150L : super.getAsynchronousAnimationTimeout(fastAnimation);
  }

  /** The loading row: the M3E indicator, or Telegram X's description line. */
  private static ListItem loadingItem () {
    if (XgramExpressive.isEnabled()) {
      return new ListItem(ListItem.TYPE_CUSTOM - TYPE_LOADING_ROW);
    }
    return new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramCommunityLoading);
  }

  @Nullable
  private TdApi.CommunityFullInfo communityFull () {
    TdApi.CommunityFullInfo full = tdlib.cache().communityFull(communityId());
    return full != null ? full : fetchedFull;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().unsubscribeFromCommunityUpdates(communityId(), this);
    XgramCommunities.removeListener(this);
    tdlib.ui().removeCallbacks(rebuild);
    if (topics != null) {
      topics.destroy();
    }
    for (long chatId : subscribedChats) {
      unsubscribeFromChat(chatId);
    }
    subscribedChats.clear();
    if (subscribedToScope) {
      tdlib.listeners().unsubscribeFromSettingsUpdates(rowListener);
      subscribedToScope = false;
    }
    for (TGChat row : rows.values()) {
      row.performDestroy();
    }
    rows.clear();
    if (headerCell != null) {
      headerCell.performDestroy();
    }
  }

  // Header

  private void updateHeader (int chatCount) {
    if (headerCell == null) {
      return;
    }
    TdApi.Community community = tdlib.cache().community(communityId());
    String name = getName().toString();
    headerCell.setText(name, Lang.plural(R.string.xChats, chatCount));
    if (community != null && community.photo != null) {
      headerCell.getAvatarReceiver().requestSpecific(tdlib, community.photo, AvatarReceiver.Options.FORCE_FORUM);
    } else {
      TdlibAccentColor accentColor = null;
      for (long chatId : rows.keySet()) {
        accentColor = tdlib.chatAccentColor(chatId);
        break;
      }
      if (accentColor == null) {
        accentColor = tdlib.accentColorForString(name);
      }
      headerCell.getAvatarReceiver().requestPlaceholder(tdlib, new AvatarPlaceholder.Metadata(accentColor, TD.getLetters(name)), AvatarReceiver.Options.FORCE_FORUM);
    }
  }

  // Community updates

  @Override
  public void onCommunityMembersChanged (Tdlib tdlib, long communityId, long chatId) {
    if (tdlib == this.tdlib && communityId == communityId()) {
      // Any thread, often several in a row: rebuild once, on the UI thread.
      scheduleRebuild();
    }
  }

  @Override
  public void onCommunityUpdated (TdApi.Community community) {
    runOnUiThreadOptional(this::buildItems);
  }

  @Override
  public void onCommunityFullUpdated (long id, TdApi.CommunityFullInfo community) {
    runOnUiThreadOptional(this::buildItems);
  }

  private void scheduleRebuild () {
    tdlib.ui().removeCallbacks(rebuild);
    tdlib.ui().postDelayed(rebuild, REBUILD_DELAY_MS);
  }

  // Row updates: TDLib's chat, message and notification updates reach the rows through this one listener.

  private final class RowListener implements ChatListener, MessageListener, NotificationSettingsListener, TdlibCache.SupergroupDataChangeListener {
    @Override
    public void onChatTopMessageChanged (long chatId, @Nullable TdApi.Message topMessage) {
      runOnUiThreadOptional(() -> topics.onTopMessage(chatId, topMessage));
      markDirty(chatId);
    }

    // Topics: the forum row's second line.

    @Override
    public void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) {
      runOnUiThreadOptional(() -> topics.onTopicInfo(info));
    }

    @Override
    public void onForumTopicUpdated (TdApi.UpdateForumTopic update) {
      runOnUiThreadOptional(() -> topics.onTopicUpdated(update));
    }

    @Override
    public void onChatPositionChanged (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) {
      markDirty(chatId);
    }

    @Override
    public void onChatAddedToList (long chatId, TdApi.ChatList chatList) {
      markDirty(chatId);
    }

    @Override
    public void onChatRemovedFromList (long chatId, TdApi.ChatList chatList) {
      markDirty(chatId);
    }

    @Override
    public void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) {
      markDirty(chatId);
    }

    @Override
    public void onChatTitleChanged (long chatId, String title) {
      markDirty(chatId);
    }

    @Override
    public void onChatEmojiStatusChanged (long chatId, @Nullable TdApi.EmojiStatus emojiStatus) {
      markDirty(chatId);
    }

    @Override
    public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
      markDirty(chatId);
    }

    @Override
    public void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
      runOnUiThreadOptional(() -> topics.onReadInbox(chatId, lastReadInboxMessageId));
      markDirty(chatId);
    }

    @Override
    public void onChatHasScheduledMessagesChanged (long chatId, boolean hasScheduledMessages) {
      markDirty(chatId);
    }

    @Override
    public void onChatReadOutbox (long chatId, long lastReadOutboxMessageId) {
      markDirty(chatId);
    }

    @Override
    public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
      markDirty(chatId);
    }

    @Override
    public void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) {
      markDirty(chatId);
    }

    @Override
    public void onChatVideoChatChanged (long chatId, TdApi.VideoChat videoChat) {
      markDirty(chatId);
    }

    @Override
    public void onChatViewAsTopics (long chatId, boolean viewAsTopics) {
      markDirty(chatId);
    }

    @Override
    public void onChatDraftMessageChanged (long chatId, @Nullable TdApi.DraftMessage draftMessage) {
      markDirty(chatId);
    }

    @Override
    public void onChatUnreadMentionCount (long chatId, int unreadMentionCount, boolean availabilityChanged) {
      markDirty(chatId);
    }

    @Override
    public void onChatUnreadReactionCount (long chatId, int unreadReactionCount, boolean availabilityChanged) {
      markDirty(chatId);
    }

    @Override
    public void onChatDefaultDisableNotifications (long chatId, boolean defaultDisableNotifications) {
      markDirty(chatId);
    }

    @Override
    public void onChatClientDataChanged (long chatId, @Nullable String clientData) {
      markDirty(chatId);
    }

    @Override
    public void onChatAccentColorsChanged (long chatId, int accentColorId, long backgroundCustomEmojiId, int profileAccentColorId, long profileBackgroundCustomEmojiId) {
      markDirty(chatId);
    }

    // Messages: an edit, a deletion, a reaction or a sent message changes the row's last line.

    @Override
    public void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) {
      markDirty(message.chatId);
    }

    @Override
    public void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) {
      markDirty(message.chatId);
    }

    @Override
    public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
      markDirty(chatId);
    }

    @Override
    public void onMessageEdited (long chatId, long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) {
      markDirty(chatId);
    }

    @Override
    public void onMessageInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) {
      markDirty(chatId);
    }

    @Override
    public void onMessagesDeleted (long chatId, long[] messageIds) {
      markDirty(chatId);
    }

    // Notification settings: the mute icon and the badge colour.

    @Override
    public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
      markDirty(chatId);
    }

    @Override
    public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
      markAllDirty();
    }

    @Override
    public void onNotificationGlobalSettingsChanged () {
      markAllDirty();
    }

    // Supergroups: membership decides the section, the username and counts decide the second line.

    @Override
    public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
      markDirty(ChatId.fromSupergroupId(supergroup.id));
    }

    @Override
    public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
      markDirty(ChatId.fromSupergroupId(supergroupId));
    }
  }

  private void markDirty (long chatId) {
    dirtyChats.add(chatId);
    scheduleRebuild();
  }

  private void markAllDirty () {
    dirtyAll = true;
    scheduleRebuild();
  }

  private void subscribeToChat (long chatId) {
    tdlib.listeners().subscribeToChatUpdates(chatId, rowListener);
    tdlib.listeners().subscribeToMessageUpdates(chatId, rowListener);
    tdlib.listeners().subscribeToSettingsUpdates(chatId, rowListener);
    if (ChatId.isSupergroup(chatId)) {
      tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(chatId), rowListener);
    }
  }

  private void unsubscribeFromChat (long chatId) {
    tdlib.listeners().unsubscribeFromChatUpdates(chatId, rowListener);
    tdlib.listeners().unsubscribeFromMessageUpdates(chatId, rowListener);
    tdlib.listeners().unsubscribeFromSettingsUpdates(chatId, rowListener);
    if (ChatId.isSupergroup(chatId)) {
      tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(chatId), rowListener);
    }
  }

  /** Listens to exactly the chats on the page. */
  private void syncSubscriptions (List<Long> chatIds) {
    if (!subscribedToScope) {
      tdlib.listeners().subscribeToSettingsUpdates(rowListener);
      subscribedToScope = true;
    }
    Set<Long> wanted = new HashSet<>(chatIds);
    for (long chatId : new ArrayList<>(subscribedChats)) {
      if (!wanted.contains(chatId)) {
        unsubscribeFromChat(chatId);
        subscribedChats.remove(chatId);
      }
    }
    for (long chatId : wanted) {
      if (subscribedChats.add(chatId)) {
        subscribeToChat(chatId);
      }
    }
  }

  // Rows

  private static TdApi.ChatList listFor (TdApi.Chat chat) {
    return chat.positions != null && chat.positions.length > 0 ? chat.positions[0].list : new TdApi.ChatListMain();
  }

  /** The chat's row, a new one when the chat changed since it was built. */
  private TGChat rowFor (TdApi.Chat chat, boolean rebuildAll, List<TGChat> retired) {
    TGChat row = rows.get(chat.id);
    boolean dirty = dirtyChats.remove(chat.id); // taken, not cleared: a mark set during this build is kept for the next
    if (row != null && !rebuildAll && !dirty) {
      return row;
    }
    if (row != null) {
      retired.add(row);
    }
    boolean forum = tdlib.isForum(chat.id);
    if (forum) {
      topics.ensure(chat.id);
    }
    List<TextEntity> topicsEntities = new ArrayList<>();
    String topicsLine = forum ? topics.line(this, chat.id, topicsEntities) : null;
    if (topicsLine == null && forum && XgramExpressive.isEnabled() && topics.isLoading(chat.id)) {
      // XGram M3E: the second line keeps its place while the topics load (an invisible stand-in the row draws a
      // placeholder bar over), so the row doesn't rearrange itself once they are here.
      topicsLine = TOPICS_PLACEHOLDER;
      topicsEntities.add(new TextEntityCustom(this, tdlib, null, 0, TOPICS_PLACEHOLDER.length(), 0, null).setCustomColorSet(INVISIBLE_TEXT));
      xgramTopicsLoading.add(chat.id);
    } else {
      xgramTopicsLoading.remove(chat.id);
    }
    String forcedText = XgramCommunityData.isMember(tdlib, chat) ? null : subtitleFor(tdlib, chat);
    row = new TGChat(this, listFor(chat), chat, false);
    // Before the first layout: the row keeps three lines (topics, then "sender: message") whatever the chat list's mode.
    row.xgramSetCommunityRow(rowMode(), topicsLine, topicsEntities.toArray(new TextEntity[0]), forcedText);
    rows.put(chat.id, row);
    return row;
  }

  /** Forums whose row shows the topics placeholder (UI thread). */
  private final Set<Long> xgramTopicsLoading = new HashSet<>();
  private static final String TOPICS_PLACEHOLDER = "—————————";
  private static final TextColorSet INVISIBLE_TEXT = () -> 0;

  private static int rowMode () {
    int mode = Settings.instance().getChatListMode();
    // One line too: the topics and "sender: message" need the two lines under the title
    return mode == Settings.CHAT_MODE_2LINE || mode == Settings.CHAT_MODE_1LINE ? Settings.CHAT_MODE_3LINE : mode;
  }

  /** "@username, 1,234 subscribers" (members, or monthly users for a bot): rows for chats you are not in, and the settings page. */
  static String subtitleFor (Tdlib tdlib, TdApi.Chat chat) {
    List<String> parts = new ArrayList<>(2);
    String username = tdlib.chatUsername(chat);
    if (!StringUtils.isEmpty(username)) {
      parts.add("@" + username);
    }
    if (chat.type instanceof TdApi.ChatTypePrivate) {
      TdApi.User user = tdlib.chatUser(chat);
      int users = user != null && user.type instanceof TdApi.UserTypeBot ? ((TdApi.UserTypeBot) user.type).activeUserCount : 0;
      if (users > 0) {
        parts.add(Lang.plural(R.string.xBotUsers, users));
      } else if (parts.isEmpty()) {
        parts.add(Lang.getString(R.string.Bot));
      }
    } else {
      boolean channel = tdlib.isChannel(chat.id);
      int members = tdlib.chatMemberCount(chat.id);
      if (members > 0) {
        parts.add(Lang.plural(channel ? R.string.xSubscribers : R.string.xMembers, members));
      } else if (parts.isEmpty()) {
        parts.add(Lang.getString(channel ? R.string.Channel : R.string.Group));
      }
    }
    return android.text.TextUtils.join(Lang.getConcatSeparator(), parts);
  }

  private static int compareRows (TGChat a, TGChat b) {
    int order = Long.compare(ChatPosition.getOrder(b.getChat(), b.getChatList()), ChatPosition.getOrder(a.getChat(), a.getChatList()));
    if (order != 0) {
      return order;
    }
    TdApi.Message am = a.getChat().lastMessage, bm = b.getChat().lastMessage;
    return Integer.compare(bm != null ? bm.date : 0, am != null ? am.date : 0);
  }

  private void buildItems () {
    TdApi.CommunityFullInfo full = communityFull();
    List<ListItem> items = new ArrayList<>();
    List<Long> chatIds = new ArrayList<>();
    if (full != null) {
      // Every chat, is_hidden included: TDLib reports it as true whenever the server leaves the flag out, which it does
      // for members who aren't admins, so filtering on it emptied the page. Admins see hidden chats in their own section.
      for (TdApi.CommunityChat communityChat : full.chats) {
        if (!XgramCommunities.isConfirmed(tdlib, communityChat.chatId)) {
          XgramCommunities.remember(tdlib, communityChat.chatId, communityId());
        }
      }
      // TDLib's copy can be old: chats whose own (fresher) info says they left or moved are dropped, and chats known
      // to have joined since are added.
      chatIds.addAll(XgramCommunityData.chatIds(tdlib, communityId(), full));
    } else if (useKnownChats) {
      // TDLib didn't send the community's chat list: the chats this device saw being added to it.
      chatIds.addAll(XgramCommunities.chatsOf(tdlib, communityId()));
    } else {
      items.add(loadingItem());
      adapter.setItems(items, false);
      return;
    }
    if (!refreshed && !isInForceTouchMode() && !chatIds.isEmpty()) {
      refreshed = true;
      XgramCommunityData.refresh(tdlib, communityId(), chatIds);
    }
    syncSubscriptions(chatIds);

    boolean rebuildAll = dirtyAll;
    dirtyAll = false;
    List<TGChat> retired = new ArrayList<>();
    boolean honourHidden = XgramCommunityData.honourHidden(full, XgramCommunityData.isAdmin(tdlib, communityId()));
    List<TGChat> joined = new ArrayList<>(), others = new ArrayList<>(), hidden = new ArrayList<>();
    Set<Long> present = new HashSet<>();
    for (long chatId : chatIds) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        // Not known to TDLib yet: fetch it, the list is built again once it's there.
        if (requestedChats.add(chatId)) {
          pendingChats.add(chatId);
          tdlib.send(new TdApi.GetChat(chatId), (result, error) -> runOnUiThreadOptional(() -> {
            // On failure it stays requested: building again would ask again at once. The next opening tries again.
            pendingChats.remove(chatId);
            buildItems();
          }));
        }
        continue;
      }
      present.add(chatId);
      TGChat row = rowFor(chat, rebuildAll, retired);
      if (honourHidden && XgramCommunityData.isHidden(full, chat.id)) {
        hidden.add(row);
      } else {
        (XgramCommunityData.isMember(tdlib, chat) ? joined : others).add(row);
      }
    }
    for (Iterator<Long> it = dirtyChats.iterator(); it.hasNext(); ) { // marks for chats not on the page
      if (!present.contains(it.next())) {
        it.remove();
      }
    }
    // Chats that left the community: their rows go.
    for (long chatId : new ArrayList<>(rows.keySet())) {
      if (!present.contains(chatId)) {
        retired.add(rows.remove(chatId));
        topics.forget(chatId);
        xgramTopicsLoading.remove(chatId);
      }
    }
    if (!searchQuery.isEmpty()) {
      // Searching: the matching rows, in the sections' order, with no headers.
      List<TGChat> found = new ArrayList<>();
      for (List<TGChat> section : java.util.Arrays.asList(joined, others, hidden)) {
        Collections.sort(section, XgramCommunityController::compareRows);
        for (TGChat row : section) {
          if (matchesSearch(row.getChat())) {
            found.add(row);
          }
        }
      }
      addRows(items, found);
      if (items.isEmpty()) {
        items.add(XgramExpressive.isEnabled() ? new ListItem(ListItem.TYPE_CUSTOM - TYPE_EMPTY_ROW, 0, 0, R.string.NothingFound) : new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NothingFound));
      }
    } else {
      addSection(items, R.string.XgramCommunityChatsIn, joined);
      addSection(items, R.string.XgramCommunityOtherChats, others);
      addSection(items, R.string.XgramCommunityHiddenChats, hidden);
    }
    if (items.isEmpty()) {
      if (!pendingChats.isEmpty()) {
        items.add(loadingItem());
      } else if (XgramExpressive.isEnabled()) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - TYPE_EMPTY_ROW, 0, 0, R.string.XgramCommunityEmpty)); // XGram M3E r6
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramCommunityEmpty));
      }
    }
    if (full == null && searchQuery.isEmpty()) {
      if (XgramExpressive.isEnabled() && loadError != null) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - TYPE_ERROR_CARD)); // XGram M3E r6: the error card with Retry
      } else {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, loadError != null ? Lang.getString(R.string.XgramCommunityPartialError, loadError) : Lang.getString(R.string.XgramCommunityPartial)));
      }
    }
    updateHeader(chatIds.size());
    if (sameStructure(adapter.getItems(), items)) {
      // The same rows in the same order: only the changed ones are bound again, in place, with no list animation.
      rebindRows(retired);
    } else {
      adapter.setItems(items, false);
    }
    for (TGChat row : retired) {
      row.performDestroy();
    }
    executeScheduledAnimation(); // the chats are in: start the slide if it was waiting for them
  }

  private static boolean sameStructure (List<ListItem> current, List<ListItem> fresh) {
    if (current.size() != fresh.size()) {
      return false;
    }
    for (int i = 0; i < current.size(); i++) {
      ListItem a = current.get(i), b = fresh.get(i);
      if (a.getViewType() != b.getViewType() || a.getId() != b.getId() || a.getLongId() != b.getLongId() || a.getStringResource() != b.getStringResource() || !StringUtils.equalsOrBothEmpty(a.getString(), b.getString())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Rows whose chat was replaced are bound again: the ones on screen and the ones the list keeps detached in its cache,
   * which would otherwise come back showing the retired chat. The payload keeps the same view, so there is no fade.
   */
  private void rebindRows (List<TGChat> retired) {
    if (retired.isEmpty()) {
      return;
    }
    Set<Long> changed = new HashSet<>();
    for (TGChat row : retired) {
      changed.add(row.getChatId());
    }
    List<ListItem> items = adapter.getItems();
    for (int i = 0; i < items.size(); i++) {
      ListItem item = items.get(i);
      if (item.getViewType() == ListItem.TYPE_CUSTOM - TYPE_CHAT_ROW && changed.contains(item.getLongId())) {
        adapter.notifyItemChanged(i, REBIND);
      }
    }
  }

  private static void addSection (List<ListItem> items, int title, List<TGChat> sectionRows) {
    if (sectionRows.isEmpty()) {
      return;
    }
    Collections.sort(sectionRows, XgramCommunityController::compareRows);
    items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, title));
    addRows(items, sectionRows);
  }

  private static void addRows (List<ListItem> items, List<TGChat> sectionRows) {
    if (sectionRows.isEmpty()) {
      return;
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < sectionRows.size(); i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_CUSTOM - TYPE_CHAT_ROW, R.id.btn_xgramCommunityChat).setLongId(sectionRows.get(i).getChatId()));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
  }

  // Taps, long-presses and previews

  @Nullable
  private static TGChat rowOf (View v) {
    return v instanceof ChatView ? ((ChatView) v).getChat() : null;
  }

  @Override
  public void onClick (View v) {
    TGChat row = rowOf(v);
    if (row != null && row.getChatId() != 0) {
      tdlib.ui().openChat(this, row.getChatId(), new TdlibUi.ChatOpenParameters().keepStack());
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (isInForceTouchMode()) {
      return false;
    }
    TGChat row = rowOf(v);
    if (row == null) {
      return false;
    }
    // The chat list's menu. For chats you are not in it shows nothing, which is what the chat list does too.
    tdlib.ui().showChatOptions(this, row.getChatList(), row.getChatId(), null, new TdApi.MessageSourceChatList(), false, false, null);
    return true;
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    BaseView view = (BaseView) v;
    return tdlib.ui().createSimpleChatActions(this, view.getPreviewChatList(), view.getPreviewChatId(), null, new TdApi.MessageSourceChatList(), ids, icons, strings, true, false, false, null);
  }

  /** The community a group or channel belongs to, from its full info (0 if none or not loaded yet). */
  public static long communityOf (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    if (chat == null) {
      return 0;
    }
    if (chat.type instanceof TdApi.ChatTypePrivate) {
      return botCommunityOf(tdlib, chat.id); // communities hold bot chats too
    }
    if (!(chat.type instanceof TdApi.ChatTypeSupergroup)) {
      return 0;
    }
    TdApi.SupergroupFullInfo full = tdlib.cache().supergroupFull(((TdApi.ChatTypeSupergroup) chat.type).supergroupId);
    return full != null ? full.communityId : XgramCommunities.communityOf(tdlib, chat.id);
  }

  /** The community a bot chat belongs to, from the bot's full info, else as far as this device has seen. */
  public static long botCommunityOf (Tdlib tdlib, long chatId) {
    TdApi.UserFullInfo full = tdlib.cache().userFull(ChatId.toUserId(chatId), false);
    return full != null && full.communityId != 0 ? full.communityId : XgramCommunities.communityOf(tdlib, chatId);
  }
}
