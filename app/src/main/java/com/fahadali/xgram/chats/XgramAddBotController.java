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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import tgx.td.ChatId;
import tgx.td.ChatPosition;
import tgx.td.Td;
import tgx.td.TdConstants;

/**
 * A bot's profile → Add to group or channel (Telegram X: groups only, one at a time). Every group and channel where
 * the user can add it, each with a checkbox, so several at once; groups only while the bot may join groups. On top:
 * Add as admin (channels always add bots as admins) and All admin rights: every right the user can give, or else the
 * rights the bot asks for (as Telegram X's own admin editor proposes them). The Done button adds it to each chosen chat
 * in turn and says how it went.
 */
public final class XgramAddBotController extends RecyclerViewController<Long> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private @Nullable TdlibChatListSlice slice;
  private boolean loadingMore, botCanJoinGroups = true, adding;
  private @Nullable TdApi.BotInfo botInfo;
  private final LinkedHashSet<Long> selected = new LinkedHashSet<>();
  private final int chatsHeaderId = ViewCompat.generateViewId(), chatsFooterId = ViewCompat.generateViewId();
  private int chatCount;
  private boolean doneIconSet;

  public XgramAddBotController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private long botUserId () {
    Long id = getArguments();
    return id != null ? id : 0;
  }

  @Override
  public int getId () {
    return R.id.controller_xgramAddBot;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramAddBotTitle);
  }

  // Which chats

  /** The chats the bot can be added to by this user: groups (while the bot may join them) and channels. */
  private boolean accepts (TdApi.Chat chat) {
    if (chat == null || chat.type == null) {
      return false;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
        return botCanJoinGroups && tdlib.canInviteUsers(chat);
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        if (((TdApi.ChatTypeSupergroup) chat.type).isChannel) {
          return canPromote(chat.id); // a bot joins a channel as an admin only
        }
        return botCanJoinGroups && (tdlib.canInviteUsers(chat) || canPromote(chat.id));
    }
    return false;
  }

  /** The user may add admins: the owner, or an admin with that right (in a basic group, the owner only). */
  private boolean canPromote (long chatId) {
    TdApi.ChatMemberStatus status = tdlib.chatStatus(chatId);
    if (status == null) {
      return false;
    }
    if (status.getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR) {
      return true;
    }
    return status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR && ((TdApi.ChatMemberStatusAdministrator) status).rights.canPromoteMembers &&
      !ChatId.isBasicGroup(chatId);
  }

  // View

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final XgramSettings settings = XgramSettings.instance();
        final int id = item.getId();
        if (id == R.id.btn_xgramAddBotAdmin) {
          view.setData(item.getCharSequenceValue());
          view.getToggler().setRadioEnabled(settings.addBotAsAdmin(), isUpdate);
        } else if (id == R.id.btn_xgramAddBotAllRights) {
          view.setData(item.getCharSequenceValue());
          view.getToggler().setRadioEnabled(settings.addBotAllRights(), isUpdate);
        }
      }

      @Override
      protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
        if (checkBox != null) {
          checkBox.setChecked(selected.contains(item.getLongId()), isUpdate);
        }
      }
    };
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramAddBotAdmin, R.drawable.baseline_stars_24, R.string.XgramAddBotAsAdmin)
      .setStringValue(Lang.getString(R.string.XgramAddBotAsAdminInfo)));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_xgramAddBotAllRights, R.drawable.baseline_security_24, R.string.XgramAddBotAllRights)
      .setStringValue(Lang.getString(R.string.XgramAddBotAllRightsInfo)));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.XgramAddBotChats));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP, chatsHeaderId));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM, chatsFooterId));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    // The bot: whether it may join groups (its profile says), and the rights it asks for
    final TdApi.User bot = tdlib.cache().user(botUserId());
    if (bot != null && bot.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR) {
      botCanJoinGroups = ((TdApi.UserTypeBot) bot.type).canJoinGroups;
    }
    TdApi.UserFullInfo fullInfo = tdlib.cache().userFull(botUserId());
    botInfo = fullInfo != null ? fullInfo.botInfo : null;
    if (botInfo == null) {
      tdlib.send(new TdApi.GetUserFullInfo(botUserId()), (info, error) -> {
        if (info != null) {
          runOnUiThreadOptional(() -> botInfo = info.botInfo);
        }
      });
    }

    final int chunk = Screen.calculateLoadingItems(Screen.dp(64f), 25);
    loadingMore = true;
    slice = tdlib.chatList(ChatPosition.CHAT_LIST_MAIN).slice(this::accepts, true, null);
    slice.initializeList(new ChatListListener() { }, this::onChatsLoaded, chunk, () -> runOnUiThreadOptional(() -> onChunkLoaded(chunk)));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView view, int dx, int dy) {
        if (dy != 0 && !loadingMore && slice != null && slice.canLoad()) {
          int last = ((androidx.recyclerview.widget.LinearLayoutManager) view.getLayoutManager()).findLastVisibleItemPosition();
          if (last + 15 >= adapter.getItemCount() - 1) {
            loadMore(chunk);
          }
        }
      }
    });
  }

  private void loadMore (int chunk) {
    if (slice == null || !slice.canLoad()) {
      return;
    }
    loadingMore = true;
    slice.loadMore(chunk, () -> runOnUiThreadOptional(() -> onChunkLoaded(chunk)));
  }

  /**
   * A chunk of the chat list was read. Most chats can't take the bot, so a chunk can add few rows or none: the next
   * one is read at once while the list is short (no scroll would ask for it), until the list ends.
   */
  private void onChunkLoaded (int chunk) {
    loadingMore = false;
    if (isDestroyed() || slice == null) {
      return;
    }
    if (slice.canLoad()) {
      if (chatCount < 20) {
        loadMore(chunk);
      }
    } else if (chatCount == 0) {
      showEmpty();
    }
  }

  private void onChatsLoaded (List<TdlibChatListSlice.Entry> entries) {
    if (entries.isEmpty()) {
      return;
    }
    final List<ListItem> rows = new ArrayList<>(entries.size());
    for (TdlibChatListSlice.Entry entry : entries) {
      DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, entry.chat, false, true);
      wrapper.setAdminSignVisible(false, false);
      wrapper.setForcedSubtitle(Lang.getString(tdlib.isChannel(entry.chat.id) ? R.string.Channel : R.string.Group));
      rows.add(new ListItem(ListItem.TYPE_CHAT_SMALL_SELECTABLE, R.id.chat).setData(wrapper).setLongId(entry.chat.id));
    }
    runOnUiThreadOptional(() -> {
      if (isDestroyed()) {
        return;
      }
      int at = adapter.indexOfViewById(chatsFooterId);
      if (at == -1) {
        return;
      }
      adapter.addItems(at, rows.toArray(new ListItem[0]));
      chatCount += rows.size();
    });
  }

  private void showEmpty () {
    int at = adapter.indexOfViewById(chatsFooterId);
    if (at != -1) {
      adapter.addItems(at + 1, new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, botCanJoinGroups ? R.string.XgramAddBotEmpty : R.string.XgramAddBotEmptyChannels));
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (slice != null) {
      slice.performDestroy();
      slice = null;
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    final XgramSettings settings = XgramSettings.instance();
    if (id == R.id.btn_xgramAddBotAdmin) {
      settings.setAddBotAsAdmin(adapter.toggleView(v));
    } else if (id == R.id.btn_xgramAddBotAllRights) {
      settings.setAddBotAllRights(adapter.toggleView(v));
    } else if (id == R.id.chat && v.getTag() instanceof ListItem && !adding) {
      final long chatId = ((ListItem) v.getTag()).getLongId();
      final boolean checked = !selected.contains(chatId);
      if (checked) {
        selected.add(chatId);
      } else {
        selected.remove(chatId);
      }
      if (v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 1 && ((ViewGroup) v).getChildAt(1) instanceof CheckBoxView) {
        ((CheckBoxView) ((ViewGroup) v).getChildAt(1)).setChecked(checked, true);
      }
      if (!doneIconSet) {
        doneIconSet = true;
        setDoneIcon(R.drawable.baseline_check_24); // the view exists now: the button can be made
      }
      setDoneVisible(!selected.isEmpty(), true);
    }
  }

  // Adding

  @Override
  protected void onDoneClick () {
    if (adding || selected.isEmpty()) {
      return;
    }
    adding = true;
    setDoneProgress(true);
    final List<Long> chats = new ArrayList<>(selected);
    addNext(chats, 0, new ArrayList<>(), new ArrayList<>());
  }

  /** One chat after another (no bursts of requests); then what happened. */
  private void addNext (List<Long> chats, int index, List<Long> added, List<String> failures) {
    if (isDestroyed()) {
      return;
    }
    if (index >= chats.size()) {
      adding = false;
      setDoneProgress(false);
      onAddFinished(added, failures);
      return;
    }
    final long chatId = chats.get(index);
    final List<TdApi.Function<?>> functions = functionsFor(chatId);
    runAll(functions, 0, error -> runOnUiThreadOptional(() -> {
      if (error != null) {
        failures.add(Lang.getString(R.string.XgramAddBotFailed, tdlib.chatTitle(chatId), TD.toErrorString(error)));
      } else {
        added.add(chatId);
      }
      addNext(chats, index + 1, added, failures);
    }));
  }

  private interface Done {
    void onDone (@Nullable TdApi.Error error);
  }

  private void runAll (List<TdApi.Function<?>> functions, int index, Done done) {
    if (index >= functions.size()) {
      done.onDone(null);
      return;
    }
    tdlib.send(functions.get(index), (result, error) -> {
      if (error != null && !(index == 0 && functions.size() > 1 && isAlreadyMember(error))) {
        done.onDone(error);
      } else {
        runAll(functions, index + 1, done);
      }
    });
  }

  /** Adding it as a member before the promotion: being one already is fine. */
  private static boolean isAlreadyMember (TdApi.Error error) {
    return error.message != null && error.message.contains("USER_ALREADY_PARTICIPANT");
  }

  /**
   * What adds the bot to one chat. A member: AddChatMember(s). An admin (always in a channel): a supergroup or a
   * channel takes the bot straight in as an admin; a basic group needs it as a member first (its owner then promotes).
   */
  private List<TdApi.Function<?>> functionsFor (long chatId) {
    final List<TdApi.Function<?>> functions = new ArrayList<>(2);
    final long botId = botUserId();
    final boolean channel = tdlib.isChannel(chatId);
    final boolean basicGroup = ChatId.isBasicGroup(chatId);
    // An admin where asked (always in a channel) and allowed; also where the user can't invite but can add admins
    final TdApi.Chat chat = tdlib.chat(chatId);
    final boolean canInvite = chat != null && tdlib.canInviteUsers(chat);
    final boolean admin = canPromote(chatId) && (channel || XgramSettings.instance().addBotAsAdmin() || !canInvite);
    if (!channel && (basicGroup || !admin)) {
      functions.add(basicGroup ? new TdApi.AddChatMember(chatId, botId, 0) : new TdApi.AddChatMembers(chatId, new long[] {botId}));
    }
    if (admin) {
      functions.add(new TdApi.SetChatMemberStatus(chatId, new TdApi.MessageSenderUser(botId), new TdApi.ChatMemberStatusAdministrator(true, rightsFor(chatId, channel))));
    }
    return functions;
  }

  /**
   * All admin rights: every right this user can give (the owner: all of them but staying anonymous; an admin: their
   * own), minus the ones of the other kind of chat. Off: Telegram X's proposal for a new admin, limited to the rights
   * the bot asks for.
   */
  private TdApi.ChatAdministratorRights rightsFor (long chatId, boolean channel) {
    final boolean all = XgramSettings.instance().addBotAllRights();
    TdApi.ChatAdministratorRights rights;
    if (all) {
      rights = new TdApi.ChatAdministratorRights(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, false);
    } else if (channel) {
      rights = new TdApi.ChatAdministratorRights(true, true, true, true, true, true, true, true, true, false, true, true, true, true, true, true, true, false);
    } else {
      rights = Td.copyOf(TdConstants.BASIC_GROUP_ADMIN_RIGHTS);
    }
    if (!channel) {
      // channel-only rights out; topics only in a forum (as Telegram X's admin editor)
      rights.canPostMessages = false;
      rights.canEditMessages = false;
      rights.canManageDirectMessages = false;
      rights.canManageTopics = tdlib.isForum(chatId);
    }
    if (!all && botInfo != null) {
      TdApi.ChatAdministratorRights asked = channel ? botInfo.defaultChannelAdministratorRights : botInfo.defaultGroupAdministratorRights;
      if (asked != null) {
        Td.limitRights(rights, asked);
      }
    }
    TdApi.ChatMemberStatus me = tdlib.chatStatus(chatId);
    if (me != null && me.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR) {
      Td.limitRights(rights, ((TdApi.ChatMemberStatusAdministrator) me).rights); // an admin gives only what they have
    }
    rights.isAnonymous = false;
    return rights;
  }

  /** "Added to X" / "Added to N chats". */
  private String addedText (List<Long> added) {
    return added.size() == 1 ? Lang.getString(R.string.XgramAddBotDoneOne, tdlib.chatTitle(added.get(0))) : Lang.getString(R.string.XgramAddBotDoneMany, added.size());
  }

  private void onAddFinished (List<Long> added, List<String> failures) {
    if (failures.isEmpty()) {
      final String text = addedText(added);
      if (!com.fahadali.xgram.appearance.XgramSnackbar.show(text, android.widget.Toast.LENGTH_SHORT)) {
        UI.showToast(text, android.widget.Toast.LENGTH_SHORT);
      }
      navigateBack();
      return;
    }
    StringBuilder b = new StringBuilder();
    if (!added.isEmpty()) {
      b.append(addedText(added)).append("\n\n");
    }
    for (String failure : failures) {
      if (b.length() > 0 && b.charAt(b.length() - 1) != '\n') {
        b.append('\n');
      }
      b.append(failure);
    }
    openAlert(R.string.XgramAddBotTitle, b.toString());
  }
}
