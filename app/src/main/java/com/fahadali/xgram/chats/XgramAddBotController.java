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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.core.StringUtils;
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
 * <p>
 * The chats come from the main list, then the archive, read a chunk at a time: each chunk's rows show as soon as it is
 * read. Search filters the chats by title or username (and reads on to the end while a query is typed).
 */
public final class XgramAddBotController extends RecyclerViewController<Long> implements View.OnClickListener {
  /** The chat lists read in turn: archived groups and channels can take the bot too. */
  private static final TdApi.ChatList[] SOURCES = {ChatPosition.CHAT_LIST_MAIN, ChatPosition.CHAT_LIST_ARCHIVE};
  /** Chats asked of TDLib at a time: most can't take the bot, so a big chunk finds the few that can sooner. */
  private static final int LOAD_CHUNK = 100;
  /** Rows wanted before reading waits for the list to be scrolled near its end. */
  private static final int PAGE_ROWS = 20;

  private SettingsAdapter adapter;
  private int sourceIndex;
  private boolean loading, allLoaded, botCanJoinGroups = true, adding;
  private @Nullable TdApi.BotInfo botInfo;
  private final LinkedHashSet<Long> selected = new LinkedHashSet<>();
  /** Every chat found that can take the bot, in the order found; the rows shown are the ones matching the search. */
  private final List<ListItem> chatRows = new ArrayList<>();
  private final Set<Long> foundChatIds = new HashSet<>();
  private String searchQuery = "";
  private final int chatsHeaderId = ViewCompat.generateViewId(), chatsFooterId = ViewCompat.generateViewId();
  private final int loadingRowId = ViewCompat.generateViewId(), emptyRowId = ViewCompat.generateViewId();
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

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        // The loading row, a row of the chats' card: Expressive draws its tile, the classic card needs the filling
        if (!com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && infoView.getBackground() == null) {
          ViewSupport.setThemedBackground(infoView, ColorId.filling, XgramAddBotController.this);
        }
        infoView.showProgress();
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
    // Room for the Done button (56dp and 16dp around it) under the last row: without it, the button covers the last
    // chat's checkbox, and a tap there adds the bot
    items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(56f) + Screen.dp(16f) * 2));
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

    loadMore();
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView view, int dx, int dy) {
        // Also called after a layout (dx = dy = 0), so a page the rows don't fill reads on by itself. Posted: a scroll
        // callback may run inside a layout, where the list can't change.
        if (!loading && !allLoaded && isNearEnd()) {
          UI.post(() -> loadMore());
        }
      }
    });
  }

  // Loading

  /** Reads on, unless reading already or everything is read: the rows of each chunk show as soon as it is read. */
  private void loadMore () {
    if (loading || allLoaded || isDestroyed()) {
      return;
    }
    loading = true;
    updateStateRows();
    readChats(tdlib.chatList(SOURCES[sourceIndex]));
  }

  /** The chats of {@code list} TDLib has loaded so far that can take the bot. */
  private void readChats (TdlibChatList list) {
    list.getChats(this::accepts, entries -> {
      final boolean endReached = list.isEndReached();
      final List<TdApi.Chat> chats = new ArrayList<>(entries.size());
      for (TdlibChatList.Entry entry : entries) {
        chats.add(entry.chat);
      }
      runOnUiThreadOptional(() -> onChatsRead(list, chats, endReached));
    });
  }

  /**
   * Most chats can't take the bot, so a chunk can add few rows or none: the next one is read at once while rows are
   * wanted (a short list, the end of the list in sight, a search), then the archive once the main list ends.
   */
  private void onChatsRead (TdlibChatList list, List<TdApi.Chat> chats, boolean endReached) {
    addChats(chats);
    if (endReached) {
      if (++sourceIndex < SOURCES.length) {
        readChats(tdlib.chatList(SOURCES[sourceIndex]));
        return;
      }
      allLoaded = true;
    } else if (!searchQuery.isEmpty() || rowsEnd() - rowsStart() < PAGE_ROWS || isNearEnd()) {
      list.loadMore(LOAD_CHUNK, () -> readChats(list));
      return;
    }
    loading = false;
    updateStateRows();
  }

  private boolean isNearEnd () {
    final RecyclerView recyclerView = getRecyclerView();
    final RecyclerView.LayoutManager manager = recyclerView != null ? recyclerView.getLayoutManager() : null;
    if (!(manager instanceof LinearLayoutManager)) {
      return true;
    }
    final int last = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
    return last == RecyclerView.NO_POSITION || last + 15 >= adapter.getItemCount() - 1;
  }

  /** Rows for the chats not found before, the ones matching the search shown under the rows already there. */
  private void addChats (List<TdApi.Chat> chats) {
    final List<ListItem> shown = new ArrayList<>();
    for (TdApi.Chat chat : chats) {
      if (!foundChatIds.add(chat.id)) {
        continue;
      }
      DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, chat, false, true);
      wrapper.setAdminSignVisible(false, false);
      wrapper.setForcedSubtitle(Lang.getString(tdlib.isChannel(chat.id) ? R.string.Channel : R.string.Group));
      ListItem row = new ListItem(ListItem.TYPE_CHAT_SMALL_SELECTABLE, R.id.chat).setData(wrapper).setLongId(chat.id);
      chatRows.add(row);
      if (matchesSearch(chat.id)) {
        shown.add(row);
      }
    }
    if (!shown.isEmpty()) {
      adapter.addItems(rowsEnd(), shown.toArray(new ListItem[0]));
    }
  }

  /** Where the first chat row goes: under the top of the chats' card. */
  private int rowsStart () {
    return adapter.indexOfViewById(chatsHeaderId) + 1;
  }

  /** Just after the last chat row: the loading row, or else the bottom of the chats' card. */
  private int rowsEnd () {
    final int loadingAt = adapter.indexOfViewById(loadingRowId);
    return loadingAt != -1 ? loadingAt : adapter.indexOfViewById(chatsFooterId);
  }

  /**
   * The loading row in the chats' card while reading; once everything is read and no row shows, why: no chat can take
   * the bot, or none matches the search.
   */
  private void updateStateRows () {
    final int loadingAt = adapter.indexOfViewById(loadingRowId);
    if (loading && loadingAt == -1) {
      adapter.addItems(adapter.indexOfViewById(chatsFooterId), new ListItem(ListItem.TYPE_LIST_INFO_VIEW, loadingRowId));
    } else if (!loading && loadingAt != -1) {
      adapter.removeItem(loadingAt);
    }
    final int emptyRes;
    if (loading || !allLoaded || rowsEnd() > rowsStart()) {
      emptyRes = 0;
    } else if (!searchQuery.isEmpty()) {
      emptyRes = R.string.NothingFound;
    } else {
      emptyRes = botCanJoinGroups ? R.string.XgramAddBotEmpty : R.string.XgramAddBotEmptyChannels;
    }
    int emptyAt = adapter.indexOfViewById(emptyRowId);
    if (emptyAt != -1 && adapter.getItems().get(emptyAt).getStringResource() != emptyRes) {
      adapter.removeItem(emptyAt);
      emptyAt = -1;
    }
    if (emptyRes != 0 && emptyAt == -1) {
      adapter.addItems(adapter.indexOfViewById(chatsFooterId) + 1, new ListItem(ListItem.TYPE_DESCRIPTION, emptyRowId, 0, emptyRes));
    }
  }

  // Search: the chats found whose title or username has the query (everything is read while there is one)

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    // Not Lang.lowercase: it keeps the case in languages whose UI text isn't lowercased, and the search would then
    // tell "news" from "News"
    final String trimmed = query.trim().toLowerCase();
    if (!trimmed.equals(searchQuery)) {
      searchQuery = trimmed;
      showMatchingRows();
      loadMore();
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    if (!searchQuery.isEmpty()) {
      searchQuery = "";
      showMatchingRows();
    }
  }

  private boolean matchesSearch (long chatId) {
    if (searchQuery.isEmpty()) {
      return true;
    }
    final TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return false;
    }
    final String title = tdlib.chatTitle(chat);
    if (title != null && title.toLowerCase().contains(searchQuery)) {
      return true;
    }
    final String username = tdlib.chatUsername(chat);
    return !StringUtils.isEmpty(username) && username.toLowerCase().contains(searchQuery);
  }

  /** The rows of the chats found that match the search, in the order found (the ticks stay, chosen or not). */
  private void showMatchingRows () {
    final int start = rowsStart(), end = rowsEnd();
    if (end > start) {
      adapter.removeRange(start, end - start);
    }
    final List<ListItem> shown = new ArrayList<>();
    for (ListItem row : chatRows) {
      if (matchesSearch(row.getLongId())) {
        shown.add(row);
      }
    }
    adapter.addItems(start, shown.toArray(new ListItem[0]));
    updateStateRows();
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
    addNext(chats, 0, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

  /**
   * One chat after another (no bursts of requests); then what happened. {@code memberOnly}: the chats it joined as a
   * member while Add as admin is on (where this user can't add admins).
   */
  private void addNext (List<Long> chats, int index, List<Long> added, List<Long> memberOnly, List<String> failures) {
    if (isDestroyed()) {
      return;
    }
    if (index >= chats.size()) {
      adding = false;
      setDoneProgress(false);
      onAddFinished(added, memberOnly, failures);
      return;
    }
    final long chatId = chats.get(index);
    final List<TdApi.Function<?>> functions = functionsFor(chatId);
    boolean promotes = false;
    for (TdApi.Function<?> function : functions) {
      if (function instanceof TdApi.SetChatMemberStatus) {
        promotes = true;
        break;
      }
    }
    final boolean adminMissed = !promotes && XgramSettings.instance().addBotAsAdmin();
    runAll(functions, 0, error -> runOnUiThreadOptional(() -> {
      if (error != null) {
        failures.add(Lang.getString(R.string.XgramAddBotFailed, tdlib.chatTitle(chatId), TD.toErrorString(error)));
      } else {
        added.add(chatId);
        if (adminMissed) {
          memberOnly.add(chatId);
        }
      }
      addNext(chats, index + 1, added, memberOnly, failures);
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
      if (error != null && !(isMemberAdd(functions.get(index)) && isAlreadyMember(error))) {
        done.onDone(error);
      } else {
        runAll(functions, index + 1, done);
      }
    });
  }

  private static boolean isMemberAdd (TdApi.Function<?> function) {
    return function instanceof TdApi.AddChatMember || function instanceof TdApi.AddChatMembers;
  }

  /**
   * Adding it as a member when it is one already is fine: before the promotion, and as all there was to do (a group
   * it is in already, or a retry).
   */
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

  private void onAddFinished (List<Long> added, List<Long> memberOnly, List<String> failures) {
    // The chats it was added to are done: unticked, so Done after a failure tries only the others again
    for (long chatId : added) {
      if (selected.remove(chatId)) {
        uncheckRow(chatId);
      }
    }
    setDoneVisible(!selected.isEmpty(), true);
    if (failures.isEmpty() && memberOnly.isEmpty()) {
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
    List<String> notes = new ArrayList<>(memberOnly.size() + failures.size());
    for (long chatId : memberOnly) {
      notes.add(Lang.getString(R.string.XgramAddBotMemberOnly, tdlib.chatTitle(chatId)));
    }
    notes.addAll(failures);
    for (String note : notes) {
      if (b.length() > 0 && b.charAt(b.length() - 1) != '\n') {
        b.append('\n');
      }
      b.append(note);
    }
    android.app.AlertDialog dialog = openAlert(R.string.XgramAddBotTitle, b.toString());
    if (failures.isEmpty() && dialog != null) {
      // Added everywhere, some as a member only: back to the bot once that is read, as without the note
      dialog.setOnDismissListener(d -> {
        if (!isDestroyed()) {
          navigateBack();
        }
      });
    }
  }

  private void uncheckRow (long chatId) {
    final int position = adapter.indexOfViewByLongId(chatId);
    if (position == -1) {
      return;
    }
    final RecyclerView.LayoutManager manager = getRecyclerView().getLayoutManager();
    final View view = manager != null ? manager.findViewByPosition(position) : null;
    if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 1 && ((ViewGroup) view).getChildAt(1) instanceof CheckBoxView) {
      ((CheckBoxView) ((ViewGroup) view).getChildAt(1)).setChecked(false, true);
    } else {
      adapter.notifyItemChanged(position);
    }
  }
}
