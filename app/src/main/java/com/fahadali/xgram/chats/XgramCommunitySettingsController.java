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
import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.AvatarPickerManager;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A community's settings, like the official apps: photo, name, who can add chats, the admins' counts and the
 * community's chats. What is shown as editable depends on your status in the community. Edits go straight to
 * TDLib; the page, the community page and the merged rows refresh from the updateCommunity that comes back.
 */
public final class XgramCommunitySettingsController extends RecyclerViewController<Long> implements View.OnClickListener, ActivityResultHandler, TdlibCache.CommunityDataChangeListener, XgramCommunities.Listener {
  private static final long REBUILD_DELAY_MS = 200;

  private SettingsAdapter adapter;
  private ComplexHeaderView headerCell;
  private AvatarPickerManager avatarPickerManager;
  /** TDLib's answer to GetCommunityFullInfo, for when its update didn't reach the cache. */
  @Nullable private TdApi.CommunityFullInfo fetchedFull;
  /** Chats asked from TDLib (GetChat), once each. */
  private final Set<Long> requestedChats = new HashSet<>();
  private int chatCount;

  private final Runnable rebuild = () -> {
    if (!isDestroyed()) {
      buildItems();
    }
  };

  public XgramCommunitySettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static void open (ViewController<?> context, long communityId) {
    if (communityId == 0) {
      return;
    }
    XgramCommunitySettingsController c = new XgramCommunitySettingsController(context.context(), context.tdlib());
    c.setArguments(communityId);
    if (com.fahadali.xgram.layout.XgramTwoPane.replacesRightPane(context)) {
      com.fahadali.xgram.layout.XgramTwoPane.showOnRight(context, c); // two panes: replace, don't stack
      return;
    }
    NavigationController navigation = context.navigationController();
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
    return R.id.controller_xgramCommunitySettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Settings);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  // Your status in the community decides what can be changed (TDLib checks the same rights).

  @Nullable
  private TdApi.CommunityAdministratorRights adminRights () {
    TdApi.Community community = tdlib.cache().community(communityId());
    if (community == null || community.status == null) {
      return null;
    }
    switch (community.status.getConstructor()) {
      case TdApi.CommunityMemberStatusCreator.CONSTRUCTOR:
        return new TdApi.CommunityAdministratorRights(true, true, true, true, true);
      case TdApi.CommunityMemberStatusAdministrator.CONSTRUCTOR:
        return ((TdApi.CommunityMemberStatusAdministrator) community.status).rights;
    }
    return null;
  }

  private boolean isAdmin () {
    return adminRights() != null;
  }

  private boolean canChangeInfo () {
    TdApi.CommunityAdministratorRights rights = adminRights();
    return rights != null && rights.canChangeInfo;
  }

  private boolean canChangePermissions () {
    TdApi.CommunityAdministratorRights rights = adminRights();
    return rights != null && rights.canBanMembers;
  }

  private boolean isCreator () {
    TdApi.Community community = tdlib.cache().community(communityId());
    return community != null && community.status != null && community.status.getConstructor() == TdApi.CommunityMemberStatusCreator.CONSTRUCTOR;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    headerCell = new ComplexHeaderView(context, tdlib, this);
    headerCell.setNoExpand(true);
    headerCell.setPhotoOpenDisabled(true);
    headerCell.initWithController(this, true);
    headerCell.setInnerMargins(Screen.dp(56f), Screen.dp(49f));
    updateHeader();
    avatarPickerManager = new AvatarPickerManager(this);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        int id = item.getId();
        if (id == R.id.btn_xgramCommunityName) {
          TdApi.Community community = tdlib.cache().community(communityId());
          view.setData(community != null ? community.name : "");
          setClickable(view, canChangeInfo());
        } else if (id == R.id.btn_xgramCommunityAdmins || id == R.id.btn_xgramCommunityRequests || id == R.id.btn_xgramCommunityRemoved) {
          TdApi.CommunityFullInfo full = communityFull();
          int count = full == null ? 0 : id == R.id.btn_xgramCommunityAdmins ? full.administratorCount : id == R.id.btn_xgramCommunityRequests ? full.addChatRequestCount : full.bannedCount;
          view.setData(String.valueOf(count));
          setClickable(view, false); // the lists behind the counts can't be opened yet
        } else if (id == R.id.btn_xgramCommunityWhoAll || id == R.id.btn_xgramCommunityWhoAdmins) {
          view.setEnabled(canChangePermissions()); // read-only for everyone else, drawn as disabled
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.cache().subscribeToCommunityUpdates(communityId(), this);
    XgramCommunities.addListener(this);
    tdlib.send(new TdApi.GetCommunityFullInfo(communityId()), (full, error) -> {
      if (full != null) {
        tdlib.cache().xgramOnCommunityFullLoaded(communityId(), full);
        runOnUiThreadOptional(() -> {
          fetchedFull = full;
          buildItems();
        });
      }
    });
    buildItems();
  }

  /** Keeps the row's look, with or without a click: a row that does nothing shows no ripple. */
  private static void setClickable (SettingView view, boolean clickable) {
    view.setIgnoreEnabled(true);
    view.setEnabled(clickable);
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
    if (headerCell != null) {
      headerCell.performDestroy();
    }
  }

  // Header: the community's photo, name and chat count

  private void updateHeader () {
    if (headerCell == null) {
      return;
    }
    TdApi.Community community = tdlib.cache().community(communityId());
    String name = community != null && !community.name.isEmpty() ? community.name : Lang.getString(R.string.XgramCommunity);
    headerCell.setText(name, Lang.plural(R.string.xChats, chatCount));
    if (community != null && community.photo != null) {
      headerCell.getAvatarReceiver().requestSpecific(tdlib, community.photo, AvatarReceiver.Options.FORCE_FORUM);
    } else {
      TdlibAccentColor accentColor = null;
      for (long chatId : XgramCommunityData.chatIds(tdlib, communityId(), communityFull())) {
        accentColor = tdlib.chatAccentColor(chatId);
        break;
      }
      if (accentColor == null) {
        accentColor = tdlib.accentColorForString(name);
      }
      headerCell.getAvatarReceiver().requestPlaceholder(tdlib, new AvatarPlaceholder.Metadata(accentColor, TD.getLetters(name)), AvatarReceiver.Options.FORCE_FORUM);
    }
  }

  // Updates: the community itself, its full info, and chats joining or leaving it

  @Override
  public void onCommunityUpdated (TdApi.Community community) {
    runOnUiThreadOptional(this::buildItems);
  }

  @Override
  public void onCommunityFullUpdated (long id, TdApi.CommunityFullInfo community) {
    runOnUiThreadOptional(this::buildItems);
  }

  @Override
  public void onCommunityMembersChanged (Tdlib tdlib, long communityId, long chatId) {
    if (tdlib == this.tdlib && communityId == communityId()) {
      tdlib.ui().removeCallbacks(rebuild);
      tdlib.ui().postDelayed(rebuild, REBUILD_DELAY_MS);
    }
  }

  // Rows

  private void buildItems () {
    TdApi.Community community = tdlib.cache().community(communityId());
    List<ListItem> items = new ArrayList<>();
    if (community == null) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramCommunityLoading));
      adapter.setItems(items, false);
      return;
    }
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    // Photo and name
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (canChangeInfo()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramCommunityPhoto, R.drawable.baseline_image_24, R.string.XgramCommunityChangePhoto));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramCommunityName, R.drawable.baseline_edit_24, R.string.XgramCommunityName));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Who can add chats
    boolean allMembers = community.permissions != null && community.permissions.canEditChatList;
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramCommunityWhoCanAdd));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramCommunityWhoAll, 0, R.string.AllMembers, null, R.id.btn_xgramCommunityWho, allMembers));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramCommunityWhoAdmins, 0, R.string.OnlyAdmins, null, R.id.btn_xgramCommunityWho, !allMembers));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Counts, for admins (TDLib sends 0 to everyone else)
    if (isAdmin()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramCommunityAdmins, R.drawable.baseline_stars_24, R.string.XgramCommunityAdmins));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramCommunityRequests, R.drawable.baseline_person_add_24, R.string.XgramCommunityRequests));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_xgramCommunityRemoved, R.drawable.baseline_block_24, R.string.XgramCommunityRemoved));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramCommunityCountsInfo));
    }

    // Delete, for the creator
    if (isCreator()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramCommunityDelete, R.drawable.baseline_delete_24, R.string.XgramCommunityDelete).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    // Chats
    List<ListItem> chatRows = new ArrayList<>();
    Set<Long> chatIds = XgramCommunityData.chatIds(tdlib, communityId(), communityFull());
    for (long chatId : chatIds) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        if (requestedChats.add(chatId)) { // not known to TDLib yet: the list is built again once it is
          tdlib.send(new TdApi.GetChat(chatId), (result, error) -> runOnUiThreadOptional(this::buildItems));
        }
        continue;
      }
      DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, true, false);
      wrapper.setForcedSubtitle(XgramCommunityController.subtitleFor(tdlib, chat));
      if (!chatRows.isEmpty()) {
        chatRows.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      chatRows.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_xgramCommunityChat).setData(wrapper).setLongId(chatId));
    }
    if (!chatRows.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Chats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.addAll(chatRows);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    chatCount = chatIds.size();
    updateHeader();
    adapter.setItems(items, false);
  }

  // Taps

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_xgramCommunityChat && v.getTag() instanceof ListItem) {
      long chatId = ((ListItem) v.getTag()).getLongId();
      if (chatId != 0) {
        tdlib.ui().openChat(this, chatId, new TdlibUi.ChatOpenParameters().keepStack());
      }
    } else if (id == R.id.btn_xgramCommunityPhoto) {
      changePhoto();
    } else if (id == R.id.btn_xgramCommunityName) {
      changeName();
    } else if (id == R.id.btn_xgramCommunityWhoAll || id == R.id.btn_xgramCommunityWhoAdmins) {
      changeWhoCanAdd(v, id == R.id.btn_xgramCommunityWhoAll);
    } else if (id == R.id.btn_xgramCommunityDelete) {
      confirmDelete();
    }
  }

  // Edits

  private void changePhoto () {
    TdApi.Community community = tdlib.cache().community(communityId());
    if (community == null || !canChangeInfo() || avatarPickerManager == null) {
      return;
    }
    avatarPickerManager.xgramShowMenuForCommunity(communityId(), community.photo != null);
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (avatarPickerManager != null) { // the camera and the system gallery come back here
      avatarPickerManager.handleActivityResult(requestCode, resultCode, data, AvatarPickerManager.MODE_COMMUNITY, null, null, communityId());
    }
  }

  private void changeName () {
    TdApi.Community community = tdlib.cache().community(communityId());
    if (community == null || !canChangeInfo()) {
      return;
    }
    long communityId = communityId();
    openInputAlert(Lang.getString(R.string.XgramCommunityName), Lang.getString(R.string.XgramCommunityNameHint), R.string.Save, R.string.Cancel, community.name, (inputView, result) -> {
      String name = result.trim();
      if (name.isEmpty()) {
        return false;
      }
      if (!name.equals(community.name)) {
        tdlib.send(new TdApi.SetCommunityName(communityId, name), (ok, error) -> {
          if (error != null) {
            UI.showError(error);
          }
        });
      }
      return true;
    }, true);
  }

  private void changeWhoCanAdd (View v, boolean allMembers) {
    if (!canChangePermissions() || !adapter.processToggle(v)) {
      return;
    }
    tdlib.send(new TdApi.SetCommunityPermissions(communityId(), new TdApi.CommunityPermissions(allMembers)), (ok, error) -> {
      if (error != null) {
        UI.showError(error);
        runOnUiThreadOptional(this::buildItems); // back to what the community really allows
      }
    });
  }

  private void confirmDelete () {
    TdApi.Community community = tdlib.cache().community(communityId());
    if (community == null || !isCreator()) {
      return;
    }
    String name = !community.name.isEmpty() ? community.name : Lang.getString(R.string.XgramCommunity);
    showConfirm(Lang.getString(R.string.XgramCommunityDeleteConfirm, name), Lang.getString(R.string.Delete), R.drawable.baseline_delete_24, OptionColor.RED, this::deleteCommunity);
  }

  private void deleteCommunity () {
    long communityId = communityId();
    tdlib.send(new TdApi.DeleteCommunity(communityId), (ok, error) -> {
      if (error != null) {
        UI.showError(error);
        return;
      }
      XgramCommunities.forgetCommunity(tdlib, communityId); // its merged row splits at once, even if this page is gone
      runOnUiThreadOptional(() -> {
        NavigationController navigation = navigationController();
        if (navigation != null) {
          navigation.getStack().destroyAllById(R.id.controller_xgramCommunity); // the community page under this one
        }
        navigateBack();
      });
    });
  }
}
