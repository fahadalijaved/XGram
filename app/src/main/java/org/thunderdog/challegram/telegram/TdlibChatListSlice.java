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
package org.thunderdog.challegram.telegram;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.ChatPosition;

public final class TdlibChatListSlice implements Destroyable {
  public interface Modifier {
    default boolean modifySlice (TdlibChatListSlice chatListSlice, List<Entry> slice, int currentSize) {
      // Override in children, e.g. additional filtering and ordering
      return false;
    }
  }

  private final Tdlib tdlib;
  private final TdlibChatList sourceList;
  private final Filter<TdApi.Chat> filter;
  private final boolean keepPositions;
  private final @Nullable Modifier modifier;

  private ChatListListener listener, subListener;
  private final List<Entry> filteredList = new ArrayList<>();
  private RunnableData<List<Entry>> subCallback;
  private int maxSize;
  private int displayCount;

  public static class Entry extends TdlibChatList.Entry {
    private long broughtToTopTime;
    private boolean keepPosition;

    public Entry (TdApi.Chat chat, TdApi.ChatList chatList, TdApi.ChatPosition position, boolean keepPosition) {
      super(chat, chatList, position);
      this.keepPosition = keepPosition;
    }

    public void bringToTop () {
      this.broughtToTopTime = SystemClock.uptimeMillis();
      this.keepPosition = true;
    }

    /**
     * XGram Later: when the chat's bell came (it then sits right under the pinned chats, newest first), 0 when it
     * isn't moved up. Set when the entry is inserted (findInsertIndex) and only then, so the sorted list stays valid.
     */
    private long xgramRaised;

    @Override
    public int compareTo (TdlibChatList.Entry other) {
      long otherTime = other instanceof Entry ? ((Entry) other).broughtToTopTime : 0;
      if (this.broughtToTopTime != otherTime) {
        return Long.compare(otherTime, this.broughtToTopTime);
      }
      // XGram Later: pinned and special (sponsored proxy, PSA) chats first, in TDLib's order, then the woken chats (newest
      // first), then the natural order. The tier depends on the entry alone, so the order stays transitive.
      final boolean top = xgramTop(this), otherTop = xgramTop(other);
      if (top != otherTop) {
        return top ? -1 : 1;
      }
      long otherRaised = other instanceof Entry ? ((Entry) other).xgramRaised : 0;
      if (this.xgramRaised != otherRaised) {
        return Long.compare(otherRaised, this.xgramRaised);
      }
      return super.compareTo(other);
    }

    /** XGram Later: above the woken chats (and never moved up itself): pinned, or special as TGChat.isPinnedOrSpecial. */
    static boolean xgramTop (TdlibChatList.Entry entry) {
      return entry.effectivePosition.isPinned || entry.effectivePosition.source != null;
    }
  }

  TdlibChatListSlice (Tdlib tdlib, TdlibChatList chatList, Filter<TdApi.Chat> filter, boolean keepPositions, @Nullable Modifier modifier) {
    this.tdlib = tdlib;
    this.sourceList = chatList;
    this.filter = filter;
    this.keepPositions = keepPositions;
    this.haveCustomModifications = keepPositions;
    this.modifier = modifier;
  }

  private int loadedCount () {
    synchronized (filteredList) {
      return filteredList.size();
    }
  }

  private void ensureTdlibThreadAndList (TdlibChatList chatList) {
    tdlib.ensureTdlibThread();
    if (this.sourceList != chatList) {
      Log.e("Origin of our list:", this.sourceList.origin());
      Log.e("Origin of their list:", chatList.origin());
      throw new IllegalStateException("Received an update from " + chatList + ", expected: " + sourceList);
    }
  }

  public TdApi.ChatList chatList () {
    return sourceList.chatList();
  }

  public boolean needProgressPlaceholder () {
    return displayCount == 0 && !sourceList.isEndReached();
  }

  public boolean canLoad () {
    return displayCount < loadedCount() || sourceList.canLoad();
  }

  public boolean isEndReached () {
    return displayCount == loadedCount() && sourceList.isEndReached();
  }

  @TdlibThread
  private int indexOfChat (long chatId) {
    // TODO lookup without increase in number of operations
    int index = 0;
    for (Entry entry : filteredList) {
      if (entry.chat.id == chatId) {
        return index;
      }
      index++;
    }
    return -1;
  }

  @TdlibThread
  private int findExistingIndex (int originalIndex, long chatId) {
    return needSort() ? indexOfChat(chatId) : originalIndex;
  }

  @TdlibThread
  protected int findInsertIndex (Entry entry) {
    entry.xgramRaised = xgramMerge && !Entry.xgramTop(entry) ? com.fahadali.xgram.later.XgramLater.raisedAt(tdlib, entry.chat.id) : 0; // XGram Later
    final int atIndex = Collections.binarySearch(filteredList, entry);
    if (atIndex >= 0)
      throw new IllegalStateException();
    return -atIndex - 1;
  }

  public void initializeList (@NonNull ChatListListener subListener, @NonNull RunnableData<List<Entry>> subCallback, int initialChunkSize, Runnable onLoadInitialChunk) {
    if (this.listener != null)
      throw new IllegalStateException();

    this.subListener = subListener;
    this.listener = new ChatListListener() {
      @Override
      public void onChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
        ensureTdlibThreadAndList(chatList);
        if (needSort() && changeInfo.orderChanged()) {
          // The order changed without moving the chat in the full list: keep this sorted slice right.
          onChatMoved(chatList, chat, index, index, changeInfo);
          return;
        }
        index = findExistingIndex(index, chat.id);
        if (index != -1) {
          if (index < displayCount) {
            subListener.onChatChanged(chatList, chat, index, changeInfo);
            subListener.onChatListChanged(chatList, ChangeFlags.ITEM_METADATA_CHANGED);
          }
        }
      }

      @Override
      public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, final int originalIndex, Tdlib.ChatChange changeInfo) {
        ensureTdlibThreadAndList(chatList);
        if (filter != null) {
          if (!filter.accept(chat))
            return;
        }
        if (xgramMerge) {
          // XGram: a community shows as one row; the member may already be there as its front.
          if (indexOfChat(chat.id) != -1 || xgramHidden(chat)) {
            xgramRefreshFor(chat.id);
            return;
          }
        }
        Entry entry = new Entry(chat, chatList.chatList(), changeInfo.position, keepPositions);
        final int insertIndex;
        try {
          if (needSort()) {
            final int sortedIndex = findInsertIndex(entry);
            filteredList.add(sortedIndex, entry);
            insertIndex = sortedIndex;
          } else {
            filteredList.add(originalIndex, entry);
            insertIndex = originalIndex;
          }
        } catch (RuntimeException e) {
          Log.e("Chats in source: %d & %d, same: %b", e, sourceList.count(null), chatList.count(null), sourceList == chatList);
          throw e;
        }
        if (insertIndex < displayCount) {
          subListener.onChatAdded(chatList, chat, insertIndex, changeInfo);
          displayCount++;
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_ADDED);
        } else {
          dispatchChats(0);
        }
        xgramRefreshFor(chat.id);
      }

      @Override
      public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
        ensureTdlibThreadAndList(chatList);
        fromIndex = findExistingIndex(fromIndex, chat.id);
        if (fromIndex != -1 && !filteredList.get(fromIndex).keepPosition) {
          /*Entry removedEntry =*/ filteredList.remove(fromIndex);
          if (fromIndex < displayCount) {
            subListener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
            displayCount--;
            dispatchChats(ChangeFlags.ITEM_REMOVED);
          }
        }
        xgramRefreshFor(chat.id);
      }

      @Override
      public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
        ensureTdlibThreadAndList(chatList);
        final boolean needSort = needSort();
        if (needSort) {
          fromIndex = findExistingIndex(fromIndex, chat.id);
          if (fromIndex == -1) { // chat is filtered out
            xgramRefreshFor(chat.id); // XGram: a hidden community member may now be the most recent
            return;
          }
        }
        Entry movedEntry = filteredList.remove(fromIndex);
        if (!movedEntry.keepPosition) {
          movedEntry.effectivePosition.order = changeInfo.position.order;
          movedEntry.effectivePosition.isPinned = changeInfo.position.isPinned;
          movedEntry.effectivePosition.source = changeInfo.position.source;
        }
        if (needSort) {
          toIndex = findInsertIndex(movedEntry);
          if (toIndex == fromIndex) {
            filteredList.add(toIndex, movedEntry);
            if (toIndex < displayCount) {
              subListener.onChatChanged(chatList, chat, toIndex, changeInfo);
              subListener.onChatListChanged(chatList, ChangeFlags.ITEM_METADATA_CHANGED);
            }
            xgramRefreshFor(chat.id);
            return;
          }
        }
        filteredList.add(toIndex, movedEntry);
        if (fromIndex < displayCount && toIndex < displayCount) {
          subListener.onChatMoved(chatList, chat, fromIndex, toIndex, changeInfo);
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_MOVED);
        } else if (fromIndex < displayCount) {
          subListener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
          displayCount--;
          dispatchChats(ChangeFlags.ITEM_REMOVED);
        } else if (toIndex < displayCount) {
          subListener.onChatAdded(chatList, chat, toIndex, changeInfo);
          displayCount++;
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_ADDED);
        } else {
          // Do nothing, as the chat is outside of displaying range
        }
        xgramRefreshFor(chat.id);
      }

      @Override
      public void onChatListChanged (TdlibChatList chatList, int changeFlags) {
        // called when subListener is called
      }

      @Override
      public void onChatListStateChanged (TdlibChatList chatList, int newState, int oldState) {
        ensureTdlibThreadAndList(chatList);
        subListener.onChatListStateChanged(chatList, newState, oldState);
      }

      @Override
      public void onChatListItemChanged (TdlibChatList chatList, TdApi.Chat chat, int changeType) {
        ensureTdlibThreadAndList(chatList);
        final int existingIndex = indexOfChat(chat.id);
        if (existingIndex == -1) {
          if (filter != null && filter.accept(chat)) { // chat became unfiltered
            TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList.chatList());
            if (position != null && position.order != 0) {
              final Entry entry = new Entry(chat, chatList.chatList(), position, keepPositions);
              int atIndex = findInsertIndex(entry);
              if (atIndex == filteredList.size()) {
                filteredList.add(entry);
                dispatchChats(0);
                return;
              }
              filteredList.add(atIndex, entry);
              subListener.onChatAdded(chatList, chat, atIndex, new Tdlib.ChatChange(position, Tdlib.ChatChange.ALL));
              displayCount++;
              subListener.onChatListChanged(chatList, ChangeFlags.ITEM_ADDED);
            }
          }
          return; // do nothing, as chat is not from this list
        }

        if (existingIndex < displayCount) {
          subListener.onChatListItemChanged(chatList, chat, changeType);
          subListener.onChatListChanged(chatList, ChangeFlags.ITEM_METADATA_CHANGED);
        }
      }
    };

    this.subCallback = subCallback;
    final RunnableData<List<TdlibChatList.Entry>> callback = (moreChats) -> {
      tdlib.ensureTdlibThread();
      xgramReady = true;
      synchronized (filteredList) {
        List<Entry> addedEntries = new ArrayList<>(moreChats.size());
        for (TdlibChatList.Entry entry : moreChats) {
          if ((filter == null || filter.accept(entry.chat)) && ((!haveCustomModifications && !xgramMerge) || indexOfChat(entry.chat.id) == -1) && !xgramHidden(entry.chat)) {
            addedEntries.add(new Entry(entry.chat, entry.chatList, entry.effectivePosition, keepPositions));
          }
        }
        if (addedEntries.isEmpty()) {
          return;
        }
        boolean haveCustomModifications = modifier != null && modifier.modifySlice(this, addedEntries, filteredList.size());
        if (xgramMerge) {
          // Sorted insertion: the merge may have put a community's front in already.
          for (Entry entry : addedEntries) {
            final int atIndex = findInsertIndex(entry);
            filteredList.add(atIndex, entry);
            if (atIndex < displayCount) {
              // XGram: sorted above what is shown already (a chat woken by Later, moved up): shown in its place now,
              // the next chunk below it would otherwise start one entry too early
              subListener.onChatAdded(sourceList, entry.chat, atIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
              displayCount++;
              subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
            }
          }
        } else {
          filteredList.addAll(addedEntries);
        }
        if (haveCustomModifications) {
          this.haveCustomModifications = true;
        }
        if (xgramMerge) {
          for (Entry entry : addedEntries) {
            xgramRefreshFor(entry.chat.id);
          }
        }
      }
      dispatchChats(0);
    };

    loadingInitialChunk = true;
    sourceList.initializeList(filter, listener, callback, this.maxSize = initialChunkSize, () -> {
      loadingInitialChunk = false;
      xgramReady = true;
      dispatchChats(0);
      if (!isDestroyed() && onLoadInitialChunk != null) {
        onLoadInitialChunk.run();
      }
    });
  }

  private boolean loadingInitialChunk;

  private boolean isSuspended () {
    return loadingInitialChunk || isDestroyed();
  }

  private boolean isDestroyed () {
    return isDestroyed;
  }

  @TdlibThread
  private int dispatchChats (final int changeFlags) {
    if (isSuspended()) {
      return 0;
    }
    tdlib.ensureTdlibThread();
    final int notifyCount = Math.max(0, Math.min(filteredList.size(), maxSize) - displayCount);
    if (notifyCount > 0) {
      final List<Entry> slice = new ArrayList<>(notifyCount);
      for (int index = 0; index < notifyCount; index++) {
        slice.add(filteredList.get(displayCount + index));
      }
      subCallback.runWithData(slice);
      this.displayCount += slice.size();
      subListener.onChatListChanged(sourceList, changeFlags | ChatListListener.ChangeFlags.ITEM_ADDED);
    }
    return notifyCount;
  }

  public void loadMore (int moreCount, Runnable after) {
    if (isSuspended())
      return;
    tdlib.runOnTdlibThread(() -> {
      maxSize += moreCount;
      if (dispatchChats(0) != 0) {
        if (after != null) {
          after.run();
        }
      } else {
        sourceList.loadMore(moreCount, () -> {
          dispatchChats(0);
          if (after != null) {
            after.run();
          }
        });
      }
    });
  }

  private boolean isDestroyed;

  @Override
  public void performDestroy () {
    if (!isDestroyed) {
      isDestroyed = true;
      sourceList.unsubscribeFromUpdates(this.listener);
      if (xgramMerge) {
        com.fahadali.xgram.chats.XgramCommunities.removeListener(xgramListener);
        com.fahadali.xgram.later.XgramLater.removeListener(xgramLaterListener);
      }
    }
  }

  private boolean haveCustomModifications;

  private boolean needSort () {
    return filter != null || haveCustomModifications || xgramMerge;
  }

  // XGram: one row per Telegram community (see XgramCommunities). Only for the user's own chat lists, not pickers.

  private boolean xgramMerge;
  /** The list has received its chats: before that, merge changes are left to the loading itself. */
  private boolean xgramReady;
  private com.fahadali.xgram.chats.XgramCommunities.Listener xgramListener;
  private com.fahadali.xgram.later.XgramLater.Listener xgramLaterListener;

  /** Call before {@link #initializeList}. */
  public void xgramMergeCommunities () {
    if (!xgramMerge) {
      xgramMerge = true;
      xgramListener = (changedTdlib, communityId, chatId) -> {
        if (changedTdlib == tdlib) {
          tdlib.runOnTdlibThread(() -> {
            xgramRefresh(communityId);
            if (chatId != 0) {
              xgramShowIfVisible(chatId); // e.g. a hidden chat that left its community
            }
          });
        }
      };
      com.fahadali.xgram.chats.XgramCommunities.addListener(xgramListener);
      // XGram Later: snoozed chats leave the user's own lists and come back when they wake; woken chats move up
      xgramLaterListener = (accountId, chatIds, events) -> {
        if (accountId == tdlib.id() && (events & (com.fahadali.xgram.later.XgramLater.EVENT_HIDDEN | com.fahadali.xgram.later.XgramLater.EVENT_RAISE)) != 0) {
          tdlib.runOnTdlibThread(() -> xgramOnLaterChanged(chatIds, events));
        }
      };
      com.fahadali.xgram.later.XgramLater.addListener(xgramLaterListener);
    }
  }

  private boolean xgramHidden (TdApi.Chat chat) {
    return xgramMerge && (com.fahadali.xgram.chats.XgramCommunities.isHidden(tdlib, chat, sourceList.chatList()) || com.fahadali.xgram.later.XgramLater.isChatHidden(tdlib, chat));
  }

  /** XGram Later: chats snoozed or woken (out of the list or back in), or moved up or back. */
  @TdlibThread
  private void xgramOnLaterChanged (long[] chatIds, int events) {
    if (!xgramMerge || !xgramReady || subListener == null || isDestroyed()) {
      return;
    }
    if (chatIds.length == 0) {
      // Every chat of the list (a setting changed): hidden ones go, the others take their place again
      long[] ids;
      synchronized (filteredList) {
        ids = new long[filteredList.size()];
        for (int i = 0; i < ids.length; i++) {
          ids[i] = filteredList.get(i).chat.id;
        }
      }
      chatIds = ids;
    }
    boolean removed = false;
    for (long chatId : chatIds) {
      xgramRefreshFor(chatId);
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat == null) {
        continue;
      }
      if (xgramHidden(chat)) {
        removed |= xgramRemoveIfHidden(chatId);
      } else if (indexOfChat(chatId) == -1) {
        xgramShowIfVisible(chatId);
      } else if ((events & com.fahadali.xgram.later.XgramLater.EVENT_RAISE) != 0) {
        xgramReposition(chatId);
      }
    }
    if (removed) {
      xgramTopUp();
    }
  }

  /**
   * XGram Later: snoozed chats left the list. When what is left of the loaded chats can't fill it, more are loaded:
   * a list shorter than the screen can't be scrolled to ask for them. One LoadChats at a time (the source list's own).
   */
  @TdlibThread
  private void xgramTopUp () {
    if (isSuspended() || subListener == null) {
      return;
    }
    if (displayCount < maxSize && filteredList.size() < maxSize && sourceList.canLoad()) {
      sourceList.loadMore(maxSize - filteredList.size(), () -> dispatchChats(0));
    }
  }

  /** XGram Later: a chat that is hidden now leaves the list (the removal loop of xgramRefresh). Returns whether it left. */
  @TdlibThread
  private boolean xgramRemoveIfHidden (long chatId) {
    if (!xgramMerge || !xgramReady || subListener == null || isDestroyed()) {
      return false;
    }
    boolean removed = false;
    synchronized (filteredList) {
      int i = indexOfChat(chatId);
      Entry entry = i != -1 ? filteredList.get(i) : null;
      if (entry != null && !entry.keepPosition) {
        filteredList.remove(i);
        if (i < displayCount) {
          subListener.onChatRemoved(sourceList, entry.chat, i, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
          displayCount--;
          subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_REMOVED);
        }
        removed = true;
      }
    }
    if (removed) {
      dispatchChats(0);
    }
    return removed;
  }

  /** XGram Later: the chat's bell came or went: out and back in at its sorted place (onChatMoved's dispatch). */
  @TdlibThread
  private void xgramReposition (long chatId) {
    if (!xgramMerge || !xgramReady || subListener == null || isDestroyed()) {
      return;
    }
    synchronized (filteredList) {
      final int fromIndex = indexOfChat(chatId);
      if (fromIndex == -1) {
        return;
      }
      Entry entry = filteredList.get(fromIndex);
      long raised = !Entry.xgramTop(entry) ? com.fahadali.xgram.later.XgramLater.raisedAt(tdlib, chatId) : 0;
      if (entry.keepPosition || entry.xgramRaised == raised) {
        return;
      }
      filteredList.remove(fromIndex);
      final int toIndex = findInsertIndex(entry);
      filteredList.add(toIndex, entry);
      if (toIndex == fromIndex) {
        return;
      }
      Tdlib.ChatChange change = new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL);
      if (fromIndex < displayCount && toIndex < displayCount) {
        subListener.onChatMoved(sourceList, entry.chat, fromIndex, toIndex, change);
        subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_MOVED);
      } else if (fromIndex < displayCount) {
        subListener.onChatRemoved(sourceList, entry.chat, fromIndex, change);
        displayCount--;
        subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_REMOVED);
      } else if (toIndex < displayCount) {
        subListener.onChatAdded(sourceList, entry.chat, toIndex, change);
        displayCount++;
        subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
      }
    }
    dispatchChats(0);
  }

  @TdlibThread
  private void xgramRefreshFor (long chatId) {
    if (xgramMerge) {
      long communityId = com.fahadali.xgram.chats.XgramCommunities.communityOf(tdlib, chatId);
      if (communityId != 0) {
        xgramRefresh(communityId);
      }
    }
  }

  /** Brings a chat of this list back in when nothing hides it any more. */
  @TdlibThread
  private void xgramShowIfVisible (long chatId) {
    if (!xgramMerge || !xgramReady || subListener == null || isDestroyed() || indexOfChat(chatId) != -1) {
      return;
    }
    TdApi.Chat chat = tdlib.chat(chatId);
    TdApi.ChatList chatList = sourceList.chatList();
    TdApi.ChatPosition position = chat != null ? ChatPosition.findPosition(chat, chatList) : null;
    if (position == null || position.order == 0 || xgramHidden(chat) || (filter != null && !filter.accept(chat))) {
      return;
    }
    synchronized (filteredList) {
      Entry entry = new Entry(chat, chatList, position, keepPositions);
      int atIndex = findInsertIndex(entry);
      filteredList.add(atIndex, entry);
      if (atIndex < displayCount) {
        subListener.onChatAdded(sourceList, chat, atIndex, new Tdlib.ChatChange(position, Tdlib.ChatChange.ALL));
        displayCount++;
        subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
      }
    }
    dispatchChats(0);
  }

  /** Leaves out the members that aren't the community's front and brings in the ones that should show. */
  @TdlibThread
  private void xgramRefresh (long communityId) {
    if (!xgramMerge || !xgramReady || communityId == 0 || subListener == null || isDestroyed()) {
      return;
    }
    TdApi.ChatList chatList = sourceList.chatList();
    long front = com.fahadali.xgram.chats.XgramCommunities.frontChat(tdlib, communityId, chatList);
    boolean changed = false;
    synchronized (filteredList) {
      for (int i = filteredList.size() - 1; i >= 0; i--) {
        Entry entry = filteredList.get(i);
        if (front != 0 && entry.chat.id != front && !entry.keepPosition && com.fahadali.xgram.chats.XgramCommunities.communityOf(tdlib, entry.chat.id) == communityId) {
          filteredList.remove(i);
          if (i < displayCount) {
            subListener.onChatRemoved(sourceList, entry.chat, i, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
            displayCount--;
            subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_REMOVED);
          }
          changed = true;
        }
      }
      for (TdApi.Chat chat : com.fahadali.xgram.chats.XgramCommunities.presentMembers(tdlib, communityId, chatList)) {
        if ((front != 0 && chat.id != front) || indexOfChat(chat.id) != -1 || (filter != null && !filter.accept(chat))) {
          continue;
        }
        TdApi.ChatPosition position = ChatPosition.findPosition(chat, chatList);
        if (position == null || position.order == 0) {
          continue;
        }
        Entry entry = new Entry(chat, chatList, position, keepPositions);
        int atIndex = findInsertIndex(entry);
        filteredList.add(atIndex, entry);
        if (atIndex < displayCount) {
          subListener.onChatAdded(sourceList, chat, atIndex, new Tdlib.ChatChange(position, Tdlib.ChatChange.ALL));
          displayCount++;
          subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
        }
        changed = true;
      }
    }
    if (changed) {
      dispatchChats(0);
    }
  }

  public void bringToTop (long chatId, @Nullable Future<TdApi.Function<?>> createFunction, @Nullable Runnable after) {
    if (this.listener == null)
      throw new IllegalStateException();
    tdlib.chat(chatId, createFunction, chat -> {
      tdlib.ensureTdlibThread();
      if (isDestroyed()) {
        return;
      }
      final int fromIndex = indexOfChat(chatId);
      if (fromIndex != -1) {
        /*if (fromIndex == 0) // No need to do anything
          return;*/
        haveCustomModifications = true;
        // Just move item to top
        Entry entry = filteredList.remove(fromIndex);
        entry.bringToTop();
        final int toIndex = findInsertIndex(entry);
        filteredList.add(toIndex, entry);
        if (fromIndex != toIndex) {
          if (fromIndex < displayCount) {
            subListener.onChatMoved(sourceList, entry.chat, fromIndex, toIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
            subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_MOVED);
          } else {
            subListener.onChatAdded(sourceList, entry.chat, toIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
            displayCount++;
            subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
          }
        }
      } else {
        if (chat == null)
          return;
        haveCustomModifications = true;
        // Force add item to top
        Entry entry = new Entry(chat, sourceList.chatList(), ChatPosition.findPosition(chat, sourceList.chatList()), keepPositions);
        entry.bringToTop();
        final int atIndex = findInsertIndex(entry);
        filteredList.add(atIndex, entry);
        subListener.onChatAdded(sourceList, entry.chat, atIndex, new Tdlib.ChatChange(entry.effectivePosition, Tdlib.ChatChange.ALL));
        displayCount++;
        subListener.onChatListChanged(sourceList, ChatListListener.ChangeFlags.ITEM_ADDED);
      }
      if (after != null) {
        after.run();
      }
    });
  }
}
