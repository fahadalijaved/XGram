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
 */
package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.DetachedChatHeaderView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import tgx.td.Td;

public class JoinDialogController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private final int DESCRIPTION_PADDING = Screen.dp(16f);

  private SettingsAdapter adapter;
  private SettingsAdapter adapterMembers;

  private final Runnable onJoinClicked;
  private final TdApi.ChatInviteLinkInfo inviteLinkInfo;
  private int measuredRecyclerHeight;

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (super.performOnBackPressed(fromTop, commit)) {
      return true;
    }
    if (commit) {
      mediaLayout.hide(false);
    }
    return true;
  }

  public JoinDialogController (MediaLayout context, TdApi.ChatInviteLinkInfo inviteLinkInfo, Runnable onJoinClicked) {
    super(context, "");
    this.inviteLinkInfo = inviteLinkInfo;
    this.onJoinClicked = onJoinClicked;
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    adapterMembers = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, VerticalChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
        chatView.clearPreviewChat();
      }
    };

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_join) {
          view.setIconColorId(ColorId.textNeutral);
        } else {
          view.setIconColorId(ColorId.icon);
        }
      }

      @Override
      protected void setText (ListItem item, CustomTextView view, boolean isUpdate) {
        if (item.getId() == R.id.description) {
          if (xgram()) {
            // XGram M3E: bodyMedium with the hero's 24dp sides
            view.setPadding(Screen.dp(24f), Screen.dp(8f), Screen.dp(24f), Screen.dp(8f));
          } else {
            view.setPadding(DESCRIPTION_PADDING, DESCRIPTION_PADDING, DESCRIPTION_PADDING, DESCRIPTION_PADDING / 2);
          }
          view.setText(item.getString(), TextEntity.valueOf(tdlib, item.getString().toString(), Td.findEntities(item.getString().toString()), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(view))), false);
          view.setTextSize(xgram() ? com.fahadali.xgram.appearance.XgramExpressive.textSizeSp(com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M) : 15f);
        } else {
          view.setPadding(0, 0, 0, 0);
          super.setText(item, view, isUpdate);
        }
      }

      @Override
      protected void setChatHeader (ListItem item, int position, DetachedChatHeaderView headerView) {
        headerView.bindWith(tdlib, inviteLinkInfo);
      }

      @Override
      protected void setRecyclerViewData (ListItem item, RecyclerView recyclerView, boolean isInitialization) {
        recyclerView.setAdapter(adapterMembers);
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        if (customViewType == XGRAM_JOIN_ROW) {
          // XGram M3E: the sheet's one call to action, a full-width filled button (no Cancel row: scrim, drag or back)
          android.widget.FrameLayout row = com.fahadali.xgram.appearance.XgramR5People.newHeroButton(parent.getContext(), xgramJoinLabel(),
            com.fahadali.xgram.appearance.XgramExpressive.PRIMARY, com.fahadali.xgram.appearance.XgramExpressive.ON_PRIMARY, v -> {
              mediaLayout.hide(false);
              onJoinClicked.run();
            });
          row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new SettingHolder(row);
        }
        if (customViewType == XGRAM_NOTE_ROW) {
          // XGram M3E: the request note in a surfaceContainerHigh card
          View card = com.fahadali.xgram.appearance.XgramR5People.newNoteCard(parent.getContext());
          return new SettingHolder(card);
        }
        return super.initCustom(parent, customViewType);
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        if (customViewType == XGRAM_NOTE_ROW) {
          com.fahadali.xgram.appearance.XgramR5People.bindNoteCard(view, item.getString());
        } else if (customViewType != XGRAM_JOIN_ROW) {
          super.modifyCustom(holder, position, item, customViewType, view, isUpdate);
        }
      }
    };

    // XGram M3E: the sheet color (surfaceContainerLow), not Telegram X's filling
    ViewSupport.setThemedBackground(recyclerView, xgram() ? ColorId.drawer : ColorId.filling);

    boolean isChannel = TD.isChannel(inviteLinkInfo.type);

    ArrayList<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_CHAT_HEADER_LARGE));

    if (inviteLinkInfo.description != null && inviteLinkInfo.description.length() > 0) {
      items.add(new ListItem(ListItem.TYPE_TEXT_VIEW, R.id.description, 0, inviteLinkInfo.description, false));
    }

    if (inviteLinkInfo.memberUserIds != null && inviteLinkInfo.memberUserIds.length > 0) {
      items.add(new ListItem(ListItem.TYPE_RECYCLER_HORIZONTAL));

      ArrayList<ListItem> itemsMembers = new ArrayList<>();
      for (int i = 0; i < inviteLinkInfo.memberUserIds.length; i++) {
        TdApi.User user = tdlib.cache().user(inviteLinkInfo.memberUserIds[i]);
        if (user != null) {
          itemsMembers.add(new ListItem(ListItem.TYPE_CHAT_VERTICAL, R.id.user).setLongId(inviteLinkInfo.memberUserIds[i]).setData(new TGFoundChat(tdlib, user, null, false).setNoUnread()));
        }
      }
      adapterMembers.setItems(itemsMembers.toArray(new ListItem[0]), false);
    }

    if (inviteLinkInfo.createsJoinRequest) {
      if (xgram()) {
        items.add(new ListItem(ListItem.TYPE_CUSTOM - XGRAM_NOTE_ROW, R.id.message, 0, Lang.getString(isChannel ? R.string.RequestToJoinChannelInfo : R.string.RequestToJoinGroupInfo), false));
      } else {
        items.add(new ListItem(ListItem.TYPE_INFO, R.id.message, 0, Lang.getString(isChannel ? R.string.RequestToJoinChannelInfo : R.string.RequestToJoinGroupInfo), false));
      }
    }

    if (xgram()) {
      items.add(new ListItem(ListItem.TYPE_CUSTOM - XGRAM_JOIN_ROW, R.id.btn_join)); // XGram M3E: the filled Join button
    } else {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_join, R.drawable.baseline_person_add_24, inviteLinkInfo.createsJoinRequest ? Lang.getString(isChannel ? R.string.RequestJoinChannelBtn : R.string.RequestJoinGroupBtn) : Lang.getString(isChannel ? R.string.JoinChannel : R.string.JoinChat), false).setTextColorId(ColorId.textNeutral));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_cancel, R.drawable.baseline_cancel_24, R.string.Cancel));
    }

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = HeaderView.getSize(false);
    params.bottomMargin = HeaderView.getTopOffset();
    recyclerView.setLayoutParams(params);

    adapter.setItems(items.toArray(new ListItem[0]), false);
    initMetrics();

    recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        recyclerView.removeOnLayoutChangeListener(this);
        measuredRecyclerHeight = recyclerView.getMeasuredHeight();
        initMetrics();
      }
    });

    setAdapter(adapter);

    return contentView;
  }

  // XGram M3E

  private static final int XGRAM_JOIN_ROW = com.fahadali.xgram.appearance.XgramR5People.HERO_ROW;
  private static final int XGRAM_NOTE_ROW = com.fahadali.xgram.appearance.XgramR5People.NOTE_ROW;

  private static boolean xgram () {
    return com.fahadali.xgram.appearance.XgramExpressive.isEnabled();
  }

  /** "Join group", "Join channel", "Request to join" in sentence case (the English strings are Title Case). */
  private String xgramJoinLabel () {
    final boolean isChannel = TD.isChannel(inviteLinkInfo.type);
    if (inviteLinkInfo.createsJoinRequest) {
      return Lang.getString(isChannel ? R.string.RequestJoinChannelBtn : R.string.RequestJoinGroupBtn); // already in sentence case
    }
    return com.fahadali.xgram.appearance.XgramR5People.sentence(isChannel ? R.string.JoinChannel : R.string.JoinChat, isChannel ? R.string.XgramR5PeopleJoinChannel : R.string.XgramR5PeopleJoinGroup);
  }

  @Override
  protected int getInitialContentHeight () {
    if (measuredRecyclerHeight != 0) {
      return measuredRecyclerHeight;
    }

    return super.getInitialContentHeight();
  }
  
  @Override
  public boolean ignoreStartHeightLimits () {
    return true;
  }

  @Override
  protected boolean canExpandHeight () {
    return false;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    return new FrameLayout(context);
  }

  @Override
  public int getId () {
    return R.id.controller_joinDialog;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_join) {
      mediaLayout.hide(false);
      onJoinClicked.run();
    } else if (v.getId() == R.id.btn_cancel) {
      mediaLayout.hide(false);
    } else if (v.getId() == R.id.user) {
      mediaLayout.hide(false);
      tdlib.ui().openPrivateProfile(this, ((ListItem) v.getTag()).getLongId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }
}