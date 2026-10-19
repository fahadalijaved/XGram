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
 * File created on 27/02/2017
 */
package org.thunderdog.challegram.core;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

public class WatchDogContext {
  private static final IntentFilter INTENT_FILTER;

  static {
    INTENT_FILTER = new IntentFilter();
    INTENT_FILTER.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    INTENT_FILTER.addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    // INTENT_FILTER.addAction(Airplane mode?);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      INTENT_FILTER.addAction(android.media.AudioManager.ACTION_HEADSET_PLUG);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      INTENT_FILTER.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
    }
  }

  private final Context context;
  private final WatchDog watchDog;
  private final WatchDogHelper receiver;
  private final WatchDogObserver observer;
  private boolean registered;

  public WatchDogContext (Context context, TdlibManager manager) {
    this.context = context;
    this.watchDog = new WatchDog(manager);
    this.receiver = new WatchDogHelper(watchDog);
    this.observer = new WatchDogObserver();
  }

  public WatchDog get () {
    return watchDog;
  }

  public void register () {
    if (!registered) {
      registered = true;
      try {
        context.registerReceiver(receiver, INTENT_FILTER);
      } catch (Throwable t) {
        Log.w("Cannot register intent receiver", t);
      }
      registerNetworkCallback();
      observer.register();
    }
  }

  public void unregister () {
    if (registered) {
      registered = false;
      try {
        context.unregisterReceiver(receiver);
      } catch (Throwable t) {
        Log.w("Cannot unregister intent receiver", t);
      }
      unregisterNetworkCallback();
      observer.unregister();
    }
  }

  // XGram: the connectivity broadcast doesn't come when only the network under a VPN changes (the VPN stays the
  // default network), and comes late to a cached app. The default network callback reports every change, so the
  // network type TDLib counts traffic under follows it.

  private Object networkCallback; // DefaultNetworkCallback, Android 7+

  private void registerNetworkCallback () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || networkCallback != null) {
      return;
    }
    try {
      ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (manager != null) {
        DefaultNetworkCallback callback = new DefaultNetworkCallback(watchDog);
        manager.registerDefaultNetworkCallback(callback);
        networkCallback = callback;
      }
    } catch (Throwable t) {
      Log.w("Cannot register network callback", t);
    }
  }

  private void unregisterNetworkCallback () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || networkCallback == null) {
      return;
    }
    try {
      ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (manager != null) {
        manager.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
      }
    } catch (Throwable t) {
      Log.w("Cannot unregister network callback", t);
    }
    networkCallback = null;
  }

  @RequiresApi(Build.VERSION_CODES.N)
  private static final class DefaultNetworkCallback extends ConnectivityManager.NetworkCallback {
    private static final long CHECK_DELAY_MS = 100; // one check for the burst a network change brings
    private static final int MAX_TRANSPORT = 15;

    private final Runnable check;
    private long lastState = Long.MIN_VALUE; // read and written on the ConnectivityThread only

    DefaultNetworkCallback (WatchDog watchDog) {
      this.check = () -> watchDog.letsHelpDoge();
    }

    private void scheduleCheck () {
      UI.cancel(check);
      UI.post(check, CHECK_DELAY_MS);
    }

    @Override
    public void onAvailable (@NonNull Network network) {
      lastState = Long.MIN_VALUE;
      scheduleCheck();
    }

    @Override
    public void onCapabilitiesChanged (@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
      // Signal strength and bandwidth change often on mobile data: only what the network type and the data saver
      // state read counts.
      long state = (long) network.hashCode() << 32;
      for (int transport = 0; transport <= MAX_TRANSPORT; transport++) {
        if (capabilities.hasTransport(transport)) {
          state |= 1L << transport;
        }
      }
      if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
        state |= 1L << 16;
      }
      if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
        state |= 1L << 17;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
        state |= 1L << 18;
      }
      if (state != lastState) {
        lastState = state;
        scheduleCheck();
      }
    }

    @Override
    public void onBlockedStatusChanged (@NonNull Network network, boolean blocked) {
      scheduleCheck();
    }

    @Override
    public void onLost (@NonNull Network network) {
      lastState = Long.MIN_VALUE;
      scheduleCheck();
    }
  }

}
