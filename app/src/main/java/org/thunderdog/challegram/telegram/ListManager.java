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
 * File created on 21/12/2019
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.vkryl.core.lambda.Destroyable;

public abstract class ListManager<T> implements Destroyable, Iterable<T>, TdlibProvider {
  private static final int STATE_INITIALIZING = 0;
  private static final int STATE_INITIALIZED = 1;
  private static final int STATE_FULL = 2;
  private static final int STATE_DESTROYED = 3;

  public static final int COUNT_UNKNOWN = -1;

  protected final Tdlib tdlib;
  protected final List<T> items = new ArrayList<>();

  protected static class Response<T> {
    public final List<T> items;
    public final int totalCount;

    public Response (List<T> items, int totalCount) {
      this.items = items;
      this.totalCount = totalCount;
    }
  }

  private int state = STATE_INITIALIZING;
  private boolean isLoading, endReached, reverseEndReached;

  protected abstract void subscribeToUpdates ();
  protected abstract void unsubscribeFromUpdates ();

  protected abstract TdApi.Function<?> nextLoadFunction (boolean reverse, int itemCount, int loadCount);

  private int totalCount = COUNT_UNKNOWN;
  protected final int initialLoadCount, loadCount;

  public ListManager (Tdlib tdlib, int initialLoadCount, int loadCount, boolean canLoadInReverseDirection, @Nullable ListChangeListener<T> listener) {
    this.tdlib = tdlib;
    this.initialLoadCount = initialLoadCount;
    this.loadCount = loadCount;
    this.reverseEndReached = !canLoadInReverseDirection;
    if (listener != null) {
      this.addChangeListener(listener);
    }
    subscribeToUpdates();
  }

  @NonNull
  @Override
  public Tdlib tdlib () {
    return tdlib;
  }

  protected void loadTotalCount (@Nullable Runnable after) {
    // Implement in the child if the count may desync from the list size
  }

  @UiThread
  public final void loadInitialChunk (@Nullable Runnable after) {
    if (items.isEmpty()) {
      loadItems(false, after);
    } else {
      if (after != null)
        after.run();
    }
  }

  @UiThread
  public final void loadItems (boolean reverse, @Nullable Runnable after) {
    loadItems(items.isEmpty() ? initialLoadCount : loadCount, reverse, after);
  }

  @UiThread
  public final void loadAll () {
    loadItems(items.isEmpty() ? initialLoadCount : loadCount, false, new Runnable() {
      @Override
      public void run () {
        if (!isEndReached()) {
          loadItems(loadCount, false, this);
        }
      }
    });
  }

  @UiThread
  private void loadItems (int count, boolean reverse, @Nullable Runnable after) {
    if (isLoading || state == STATE_FULL || state == STATE_DESTROYED || (reverse ? reverseEndReached : endReached))
      return;
    isLoading = true;
    tdlib.client().send(nextLoadFunction(reverse, items.size(), count), new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object object) {
        if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          UI.showError(object);
          runOnUiThread(ListManager.this::xgramOnLoadFailed); // XGram: the list can load again (it stayed "loading" for good)
          return;
        }
        Response<T> data = processResponse(object, this, count, reverse);
        if (data != null) {
          runOnUiThread(() -> {
            processData(data, reverse);
            if (after != null) {
              after.run();
            }
          });
        }
      }
    });
  }

  protected final int indexOfItem (T item) {
    int index = 0;
    for (T existingItem : items) {
      if (existingItem.equals(item)) {
        return index;
      }
      index++;
    }
    return -1;
  }

  protected final boolean isReady () {
    return state == STATE_INITIALIZED || state == STATE_FULL;
  }

  protected final boolean isEndReached () {
    return state == STATE_FULL;
  }

  protected final boolean isDestroyed () {
    return state == STATE_DESTROYED;
  }

  @NonNull
  @Override
  public final Iterator<T> iterator () {
    return items.iterator();
  }

  public final T getItem (int index) {
    return items.get(index);
  }

  public final int getCount () {
    return items.size();
  }

  public final int getTotalCount () {
    if (totalCount == COUNT_UNKNOWN) {
      return COUNT_UNKNOWN;
    }
    if (reverseEndReached && endReached) {
      return items.size();
    }
    return Math.max(totalCount, items.size());
  }

  public final boolean hasReceivedInitialChunk () {
    return state != STATE_INITIALIZING;
  }

  public final boolean isAvailable () {
    return isListAvailable();
  }

  protected abstract Response<T> processResponse (TdApi.Object response, Client.ResultHandler retryHandler, int retryLoadCount, boolean reverse); // Return null means retryHandler will be called again

  @UiThread
  private void processData (Response<T> data, boolean reverse) {
    if (isDestroyed())
      return;
    this.isLoading = false;
    boolean wasInitialized = this.state != STATE_INITIALIZING;
    if (data.items.isEmpty()) {
      if (reverse) {
        reverseEndReached = true;
      } else {
        endReached = true;
      }
      if ((endReached && reverseEndReached) || this.items.size() == data.totalCount) {
        this.state = STATE_FULL;
      }
      onEndReached(reverse);
    } else {
      this.state = this.items.size() + data.items.size() == totalCount ? STATE_FULL : STATE_INITIALIZED;
      int startIndex = this.items.size();
      if (startIndex == 0 || !reverse) {
        this.items.addAll(data.items);
        onItemsAdded(data.items, startIndex, wasInitialized);
      } else {
        this.items.addAll(0, data.items);
        onItemsAdded(data.items, 0, wasInitialized);
      }
    }
    if (data.totalCount != COUNT_UNKNOWN) {
      setTotalCount(data.totalCount);
    }
    if (!wasInitialized) {
      notifyInitialChunkReceived();
    }
    xgramRunPendingRevalidation(); // XGram
  }

  // XGram: a list can start from items kept from before (xgramSeed: no listener is told, the owner shows them itself),
  // then load its first page again (xgramRevalidate) and take it as item changes: what stays the same keeps its object,
  // so what is on screen only moves where the data did.

  private boolean xgramRevalidatePending;
  private int xgramRevalidateCause;

  /** XGram: the function that loads the first page, as the first load does ({@code null}: this list can't). */
  @Nullable
  protected TdApi.Function<?> xgramFirstPageFunction (int count) {
    return null;
  }

  /** XGram: the same item of the list (as far as its place goes). */
  protected boolean xgramSameItem (T a, T b) {
    return a.equals(b);
  }

  /** XGram: {@code oldItem} may stay for {@code newItem} (the same item, and nothing that is shown has changed). */
  protected boolean xgramKeepItem (T oldItem, T newItem) {
    return false;
  }

  /**
   * XGram: fills an empty list that has loaded nothing yet with {@code seedItems} (in list order) and their total
   * count, telling no listener. The list counts as loaded: its first load is not sent.
   */
  @UiThread
  protected final boolean xgramSeed (List<T> seedItems, int seedTotalCount) {
    if (state != STATE_INITIALIZING || isLoading || !this.items.isEmpty() || seedItems.isEmpty() || seedTotalCount == COUNT_UNKNOWN) {
      return false;
    }
    this.items.addAll(seedItems);
    this.totalCount = Math.max(seedTotalCount, this.items.size());
    this.state = this.items.size() == this.totalCount ? STATE_FULL : STATE_INITIALIZED;
    this.isListAvailable = true;
    return true;
  }

  /**
   * XGram: loads the first page again and applies it as changes: items no longer on it go, new ones come in at their
   * place, a changed one is replaced ({@code changeCause} for its listeners). After a load that is still running.
   */
  @UiThread
  protected final void xgramRevalidate (int changeCause) {
    if (isDestroyed()) {
      return;
    }
    if (isLoading) {
      xgramRevalidatePending = true;
      xgramRevalidateCause = changeCause;
      return;
    }
    final TdApi.Function<?> function = xgramFirstPageFunction(initialLoadCount);
    if (function == null) {
      return;
    }
    isLoading = true;
    tdlib.client().send(function, new Client.ResultHandler() {
      @Override
      public void onResult (TdApi.Object object) {
        if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          runOnUiThread(ListManager.this::xgramOnLoadFailed); // what the list holds stays
          return;
        }
        Response<T> data = processResponse(object, this, initialLoadCount, false);
        if (data != null) {
          runOnUiThread(() -> xgramApplyFirstPage(data, changeCause));
        }
      }
    });
  }

  @UiThread
  private void xgramOnLoadFailed () {
    isLoading = false;
    xgramRunPendingRevalidation();
  }

  @UiThread
  private void xgramRunPendingRevalidation () {
    if (xgramRevalidatePending && !isLoading && !isDestroyed()) {
      xgramRevalidatePending = false;
      xgramRevalidate(xgramRevalidateCause);
    }
  }

  private int xgramIndexIn (List<T> list, T item) {
    for (int i = 0; i < list.size(); i++) {
      if (xgramSameItem(list.get(i), item)) {
        return i;
      }
    }
    return -1;
  }

  @UiThread
  private void xgramApplyFirstPage (Response<T> data, int changeCause) {
    if (isDestroyed()) {
      return;
    }
    final List<T> page = data.items;
    // Items that are not on the first page any more go (last first, so the indexes stay right)
    for (int i = this.items.size() - 1; i >= 0; i--) {
      T item = this.items.get(i);
      if (xgramIndexIn(page, item) == -1) {
        this.items.remove(i);
        onItemRemoved(item, i);
      }
    }
    // What is left is in the page's order (both are sorted): otherwise it all goes and comes in again
    int lastIndex = -1;
    boolean sameOrder = true;
    for (T item : this.items) {
      int index = xgramIndexIn(page, item);
      if (index <= lastIndex) {
        sameOrder = false;
        break;
      }
      lastIndex = index;
    }
    if (!sameOrder) {
      for (int i = this.items.size() - 1; i >= 0; i--) {
        T item = this.items.remove(i);
        onItemRemoved(item, i);
      }
    }
    // What stays keeps its object unless it changed, what is new comes in at its place
    for (int i = 0; i < page.size(); i++) {
      T fresh = page.get(i);
      if (i < this.items.size() && xgramSameItem(this.items.get(i), fresh)) {
        if (!xgramKeepItem(this.items.get(i), fresh)) {
          this.items.set(i, fresh);
          notifyItemChanged(i, changeCause);
        }
      } else {
        this.items.add(i, fresh);
        onItemAdded(fresh, i);
      }
    }
    // The state a first load of this page leaves
    if (page.isEmpty()) {
      endReached = true;
      this.state = reverseEndReached || data.totalCount == 0 ? STATE_FULL : STATE_INITIALIZED;
    } else {
      endReached = false;
      this.state = data.totalCount != COUNT_UNKNOWN && this.items.size() >= data.totalCount ? STATE_FULL : STATE_INITIALIZED;
    }
    this.isLoading = false;
    if (data.totalCount != COUNT_UNKNOWN) {
      setTotalCount(data.totalCount);
    }
    checkListAvailability();
    xgramRunPendingRevalidation();
  }

  @Override
  @UiThread
  public void performDestroy () {
    if (state != STATE_DESTROYED) {
      unsubscribeFromUpdates();
      state = STATE_DESTROYED;
    }
  }

  protected final void runOnUiThreadIfReady (Runnable act) {
    tdlib.ui().post(() -> {
      if (isReady()) {
        act.run();
      }
    });
  }

  protected final void runOnUiThread (Runnable act) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        act.run();
      }
    });
  }

  // List availability check

  private boolean isListAvailable;

  protected final boolean checkListAvailability () {
    boolean isAvailable = !this.items.isEmpty() || totalCount > 0;
    boolean availabilityChanged = this.isListAvailable != isAvailable;
    if (availabilityChanged) {
      this.isListAvailable = isAvailable;
      notifyAvailabilityChanged(isAvailable);
    }
    if (this.items.size() < initialLoadCount) {
      loadItems(false, null);
    }
    return availabilityChanged;
  }

  protected final boolean isListAvailable () {
    return isListAvailable;
  }

  protected final boolean setTotalCount (int count) {
    if (this.totalCount != count) {
      this.totalCount = count;
      if (count > this.items.size() && this.state == STATE_FULL) {
        this.state = STATE_INITIALIZED;
      }
      notifyTotalCountChanged(count);
      return checkListAvailability();
    }
    return false;
  }

  protected final boolean changeTotalCount (int delta) {
    if (this.totalCount != COUNT_UNKNOWN && delta != 0) {
      if (this.totalCount + delta < 0) {
        boolean listAvailable = setTotalCount(items.size());
        loadTotalCount(null);
        return listAvailable;
      }
      return setTotalCount(this.totalCount + delta);
    }
    return false;
  }

  // List state notifications

  protected interface ListChangeListener<T> {
    default void onItemsAdded (ListManager<T> list, List<T> items, int startIndex, boolean isInitialChunk) {
      onListChanged (list);
    }
    default void onItemAdded (ListManager<T> list, T item, int toIndex) {
      onListChanged (list);
    }
    default void onItemMoved (ListManager<T> list, T item, int fromIndex, int toIndex) {
      onListChanged (list);
    }
    default void onItemRemoved (ListManager<T> list, T removedItem, int fromIndex) {
      onListChanged (list);
    }
    default void onItemChanged (ListManager<T> list, T item, int index, int cause) {
      onListChanged (list);
    }
    default void onListMetadataChanged (ListManager<T> list) {
      onListChanged(list);
    }

    default void onAvailabilityChanged (ListManager<T> list, boolean isAvailable) { }
    default void onTotalCountChanged (ListManager<T> list, int totalCount) { }
    default void onInitialChunkLoaded (ListManager<T> list) { }
    default void onListChanged (ListManager<T> list) { }
  }

  private final List<ListChangeListener<T>> changeListeners = new ArrayList<>();

  public final void addChangeListener (ListChangeListener<T> listener) {
    changeListeners.add(listener);
  }

  public final void removeChangeListener (ListChangeListener<T> listener) {
    changeListeners.remove(listener);
  }

  protected final void notifyItemChanged (int index, int cause) {
    T item = items.get(index);
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onItemChanged(this, item, index, cause);
    }
  }

  protected final void notifyMetadataChanged () {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onListMetadataChanged(this);
    }
  }

  private void notifyAvailabilityChanged (boolean isAvailable) {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onAvailabilityChanged(this, isAvailable);
    }
  }

  private void notifyInitialChunkReceived () {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onInitialChunkLoaded(this);
    }
  }

  private void notifyTotalCountChanged (int totalCount) {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onTotalCountChanged(this, totalCount);
    }
  }

  @UiThread
  protected final void onItemsAdded (List<T> items, int startIndex, boolean isInitialChunk) {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onItemsAdded(this, items, startIndex, isInitialChunk);
    }
    checkListAvailability();
  }

  @UiThread
  protected final void onItemAdded (T item, int toIndex) {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onItemAdded(this, item, toIndex);
    }
    checkListAvailability();
  }

  @UiThread
  protected final void onItemMoved (T item, int fromIndex, int toIndex) {
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onItemMoved(this, item, fromIndex, toIndex);
    }
  }

  @UiThread
  protected final void onItemRemoved (T removedItem, int fromIndex) {
    checkListAvailability();
    for (int i = changeListeners.size() - 1; i >= 0; i--) {
      changeListeners.get(i).onItemRemoved(this, removedItem, fromIndex);
    }
  }

  @UiThread
  protected final void onEndReached (boolean reverse) {
    // TODO notify?
  }
}
