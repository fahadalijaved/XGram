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
 * File created on 21/05/2015 at 18:26
 */
package org.thunderdog.challegram.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.reference.ReferenceUtils;

public class WatchDog {
  private final TdlibManager context;

  private final List<Reference<Context>> activities;

  private boolean isOnline, isUnavailable;
  private boolean systemDataSaverEnabled;

  /*private boolean hasWifi;
  private boolean hasMobile;
  private boolean isRoaming;*/

  private static final int CONNECTION_TYPE_NONE = -1;
  public static final int CONNECTION_TYPE_MOBILE = 0;
  public static final int CONNECTION_TYPE_WIFI = 1;
  public static final int CONNECTION_TYPE_ROAMING = 2;
  public static final int CONNECTION_TYPE_OTHER = 3;
  private int connectionType = CONNECTION_TYPE_NONE;

  public int getConnectionType () {
    return connectionType;
  }

  WatchDog (TdlibManager context) {
    this.context = context;
    this.activities = new ArrayList<>();
    isOnline = true;
  }

  // Impl

  public boolean isWifi () {
    return connectionType == CONNECTION_TYPE_WIFI;
  }

  public boolean isRoaming () {
    return connectionType == CONNECTION_TYPE_ROAMING;
  }

  public boolean isMobile () {
    return connectionType == CONNECTION_TYPE_MOBILE;
  }

  public boolean isSystemDataSaverEnabled () {
    return systemDataSaverEnabled;
  }

  public void wakeUp () {
    letsHelpDoge();
  }

  private void checkActivities () {
    ReferenceUtils.gcReferenceList(activities);
  }

  public void onActivityCreate (Context context) {
    wakeUp();
    if (ReferenceUtils.addReference(activities, context)) {
      checkActivities();
    }
  }

  public void onActivityDestroy (Context context) {
    if (ReferenceUtils.removeReference(activities, context)) {
      checkActivities();
    }
  }

  public boolean setIsOnline (boolean isOnline) {
    if (this.isOnline == isOnline) {
      return false;
    }
    if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
      Log.i(Log.TAG_NETWORK_STATE, "setIsOnline: %b -> %b", this.isOnline, isOnline);
    }
    this.isOnline = isOnline;
    checkConnectionType(false);
    return true;
  }

  private int lastReportedConnectionType = Integer.MIN_VALUE;
  private long lastReportedConnectionTimeMs;

  private void checkConnectionType (boolean force) {
    int reportConnectionType;
    boolean isOnline;
    if (Config.FAKE_BACKGROUND_CONNECTION_STATE && inBackground) {
      int connectionType = this.connectionType;
      if (connectionType == CONNECTION_TYPE_NONE) {
        connectionType = CONNECTION_TYPE_OTHER;
      }
      reportConnectionType = connectionType;
      isOnline = true;
    } else {
      isOnline = this.isOnline;
      int connectionType = this.connectionType;
      reportConnectionType = isOnline ? connectionType : CONNECTION_TYPE_NONE;
    }
    if (lastReportedConnectionType != reportConnectionType || force) {
      lastReportedConnectionTimeMs = SystemClock.elapsedRealtime();
      sendTGConnectionType(lastReportedConnectionType = reportConnectionType, isOnline);
    }
  }

  private void onNetworkRouteChanged () {
    if (isOnline) {
      checkConnectionType(true);
    }
  }

  public boolean isOnline () {
    return isOnline;
  }

  public boolean isOffline () {
    return !isOnline;
  }

  public boolean isWaitingForNetwork () {
    return connectionType == CONNECTION_TYPE_NONE;
  }

  public void resendNetworkStateIfNeeded () {
    if (inBackground) {
      if (lastReportedConnectionTimeMs == 0 || (SystemClock.elapsedRealtime() - lastReportedConnectionTimeMs >= 1000)) {
        context.onConnectionAwake();
      }
    }
  }

  public void letsHelpDoge () {
    letsHelpDoge(false);
  }

  private static int getConnectionType (NetworkInfo info) {
    if (info.isRoaming()) {
      return CONNECTION_TYPE_ROAMING;
    }
    switch (info.getType()) {
      case ConnectivityManager.TYPE_WIFI:
      case ConnectivityManager.TYPE_ETHERNET: {
        return CONNECTION_TYPE_WIFI;
      }
      case ConnectivityManager.TYPE_MOBILE:
      case ConnectivityManager.TYPE_BLUETOOTH:
      case ConnectivityManager.TYPE_VPN:
      default: {
        return CONNECTION_TYPE_MOBILE;
      }
    }
  }

  /**
   * XGram: the connection type from a network's capabilities (Android 6+), which TDLib's network statistics follow.
   * A VPN lists the transports of the networks under it (Android 9+); when it lists none, the network Android would
   * use without the VPN is taken. Wi-Fi, Ethernet and USB: Wi-Fi; mobile data: mobile or roaming.
   */
  @RequiresApi(Build.VERSION_CODES.M)
  private static final class NetworkTypes {
    // NetworkCapabilities.TRANSPORT_USB (Android 12) and TRANSPORT_SATELLITE (Android 15), by value.
    private static final int TRANSPORT_USB = 8;
    private static final int TRANSPORT_SATELLITE = 10;

    static int detect (ConnectivityManager manager, Object rawNetwork) {
      final Network network = (Network) rawNetwork;
      final NetworkCapabilities capabilities = capabilitiesOf(manager, network);
      if (capabilities == null) {
        return CONNECTION_TYPE_NONE;
      }
      int type = typeOf(manager, network, capabilities);
      if (type == CONNECTION_TYPE_NONE && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
        // NONE: nothing under the VPN, so no internet.
        return best(manager);
      }
      return type != CONNECTION_TYPE_NONE ? type : CONNECTION_TYPE_OTHER;
    }

    static int best (ConnectivityManager manager) {
      final Network[] networks;
      try {
        networks = manager.getAllNetworks();
      } catch (Throwable t) {
        return CONNECTION_TYPE_NONE;
      }
      if (networks == null) {
        return CONNECTION_TYPE_NONE;
      }
      int bestType = CONNECTION_TYPE_NONE;
      int bestRank = -1;
      for (Network network : networks) {
        final NetworkCapabilities capabilities = capabilitiesOf(manager, network);
        if (capabilities == null || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
          continue;
        }
        int type = typeOf(manager, network, capabilities);
        if (type == CONNECTION_TYPE_NONE) {
          // An internet network of a kind not listed in typeOf: it still carries traffic, so it counts, last.
          type = CONNECTION_TYPE_OTHER;
        }
        // As Android picks: a working network first, then Wi-Fi before mobile data.
        final int rank = (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 3 : 0) + (type == CONNECTION_TYPE_WIFI ? 2 : type == CONNECTION_TYPE_OTHER ? 0 : 1);
        if (rank > bestRank) {
          bestRank = rank;
          bestType = type;
        }
      }
      return bestType;
    }

    private static int typeOf (ConnectivityManager manager, Network network, NetworkCapabilities capabilities) {
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || capabilities.hasTransport(TRANSPORT_USB)) {
        return CONNECTION_TYPE_WIFI;
      }
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
        return isRoaming(manager, network, capabilities) ? CONNECTION_TYPE_ROAMING : CONNECTION_TYPE_MOBILE;
      }
      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) || capabilities.hasTransport(TRANSPORT_SATELLITE)) {
        // Bluetooth tethering shares a phone's mobile data.
        return CONNECTION_TYPE_MOBILE;
      }
      return CONNECTION_TYPE_NONE;
    }

    private static boolean isRoaming (ConnectivityManager manager, Network network, NetworkCapabilities capabilities) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // A VPN is roaming when a network under it is.
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
      }
      try {
        final NetworkInfo info = manager.getNetworkInfo(network);
        return info != null && info.isRoaming();
      } catch (Throwable t) {
        return false;
      }
    }

    private static @Nullable NetworkCapabilities capabilitiesOf (ConnectivityManager manager, Network network) {
      try {
        return manager.getNetworkCapabilities(network);
      } catch (Throwable t) {
        // https://issuetracker.google.com/issues/175055271
        return null;
      }
    }
  }

  private CancellableRunnable backgroundStateChecker;
  private boolean inBackground = true;

  // XGram: TDLib's connection state updates call this off the main thread; the check runs on the main thread like the
  // others (the connectivity broadcast and the default network callback), so they don't race over the fields here.
  private final Runnable backgroundCheck = () -> letsHelpDoge(false);

  public void helpDogeIfInBackground () {
    if (inBackground) {
      if (UI.inUiThread()) {
        letsHelpDoge(false);
      } else {
        UI.post(backgroundCheck);
      }
    }
  }

  public boolean onBackgroundStateChanged (final boolean inBackground) {
    if (this.inBackground != inBackground) {
      this.inBackground = inBackground;

      Log.i(Log.TAG_NETWORK_STATE, "inBackground -> %b", inBackground);

      if (backgroundStateChecker != null) {
        backgroundStateChecker.cancel();
        backgroundStateChecker = null;
      }
      if (!inBackground) {
        backgroundStateChecker = new CancellableRunnable() {
          @Override
          public void act () {
            letsHelpDoge(false);
          }
        };
        backgroundStateChecker.removeOnCancel(UI.getAppHandler());
        UI.post(backgroundStateChecker, 300);
      }
      return true;
    }
    return false;
  }

  private NetworkInfo.State lastRouteState;
  private String lastRouteExtra;
  private int lastRouteType;
  private boolean lastRouteAvailable;
  private long lastNetworkHandle;

  private boolean hasRouteStateChanged (NetworkInfo networkInfo) {
    return lastRouteState != null && (!lastRouteState.equals(networkInfo.getState()) || !StringUtils.equalsOrBothEmpty(lastRouteExtra, networkInfo.getExtraInfo()) || lastRouteAvailable != networkInfo.isAvailable() || lastRouteType != networkInfo.getType());
  }

  private void saveConnectionRouteInfo (NetworkInfo networkInfo) {
    lastRouteState = networkInfo.getState();
    lastRouteExtra = networkInfo.getExtraInfo();
    lastRouteAvailable = networkInfo.isAvailable();
    lastRouteType = networkInfo.getType();
    if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
      Log.i(Log.TAG_NETWORK_STATE, "saveConnectionRouteInfo: %s", networkInfo);
    }
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private boolean hasRouteChanged (ConnectivityManager manager, Object rawNetwork) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && manager != null && rawNetwork != null) {
      android.net.Network network = (android.net.Network) rawNetwork;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return network.getNetworkHandle() != lastNetworkHandle;
      }
    }
    return false;
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private void saveRoute (ConnectivityManager manager, Object rawNetwork) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && manager != null && rawNetwork != null) {
      android.net.Network network = (android.net.Network) rawNetwork;
      if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
        // try/catch for https://issuetracker.google.com/issues/175055271?pli=1
        try {
          android.net.NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
          Log.i(Log.TAG_NETWORK_STATE, "saveRoute, network: %s, capabilities: %s", network, capabilities);
        } catch (Throwable t) {
          Log.i(Log.TAG_NETWORK_STATE, "Unable to get network capabilities: %s", t, network);
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        lastNetworkHandle = network.getNetworkHandle();
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private static boolean filter (NetworkInfo info) {
    return info.isConnectedOrConnecting() && info.getType() != ConnectivityManager.TYPE_VPN;
  }

  public void letsHelpDoge (boolean dataSaverOnly) {
    ConnectivityManager manager = (ConnectivityManager) AppContext.get().getSystemService(Context.CONNECTIVITY_SERVICE);

    if (manager == null) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      int restrictBackgroundStatus;
      try {
        restrictBackgroundStatus = manager.getRestrictBackgroundStatus();
      } catch (SecurityException e) {
        Log.w("Unable to fetch background network status", e);
        restrictBackgroundStatus = ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
      }
      boolean dataSaverEnabled = manager.isActiveNetworkMetered() && restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
      setSystemDataSaverEnabled(dataSaverEnabled);
      if (dataSaverOnly) {
        return;
      }
    }

    NetworkInfo activeNetworkInfo = null; // manager.getActiveNetworkInfo();
    Object activeNetwork = null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      android.net.Network foundActiveNetwork = manager.getActiveNetwork();
      if (foundActiveNetwork != null) {
        activeNetwork = foundActiveNetwork;
        activeNetworkInfo = manager.getNetworkInfo(foundActiveNetwork);
      }
    }/* else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // commented out because of possible incorrect detection
      android.net.Network[] networks = manager.getAllNetworks();
      for (android.net.Network network : networks) {
        NetworkInfo info = manager.getNetworkInfo(network);
        if (info.isConnectedOrConnecting()) {
          activeNetwork = network;
          activeNetworkInfo = manager.getNetworkInfo(network);
        }
      }
    }*/ else {
      activeNetworkInfo = manager.getActiveNetworkInfo();
    }

    boolean isOnline = false;
    int connectionType = CONNECTION_TYPE_NONE;
    NetworkInfo chosenNetworkInfo = null;
    Object chosenNetwork = null;

    NetworkInfo availableNetworkInfo = null;
    Object availableNetwork = null;

    if (activeNetworkInfo != null && activeNetworkInfo.isAvailable() && activeNetworkInfo.getType() != ConnectivityManager.TYPE_VPN) {
      availableNetworkInfo = activeNetworkInfo;
      availableNetwork = activeNetwork;
    }

    if (activeNetworkInfo != null && filter(activeNetworkInfo)) {
      isOnline = true;
      chosenNetworkInfo = activeNetworkInfo;
      chosenNetwork = activeNetwork;
      connectionType = getConnectionType(activeNetworkInfo);
    } else {
      // Trying to find active network manually
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        android.net.Network[] networks = manager.getAllNetworks();
        for (android.net.Network network : networks) {
          // XGram: only a network that carries internet traffic counts; IMS (VoLTE/Wi-Fi calling) and MMS networks are
          // connected cellular networks without INTERNET.
          NetworkCapabilities capabilities;
          try {
            capabilities = manager.getNetworkCapabilities(network);
          } catch (Throwable t) {
            capabilities = null; // https://issuetracker.google.com/issues/175055271
          }
          if (capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            continue;
          }
          NetworkInfo info = manager.getNetworkInfo(network);
          if (info != null) {
            if (filter(info)) {
              isOnline = true;
              int type = getConnectionType(info);
              // XGram: Wi-Fi wins, as it does in Android. Mobile data stays connected next to Wi-Fi on most phones,
              // and letting it win here (always the case under a VPN) counted every byte as mobile data.
              if (connectionType == CONNECTION_TYPE_NONE || type == CONNECTION_TYPE_WIFI) {
                connectionType = type;
                chosenNetworkInfo = info;
                chosenNetwork = network;
              }
            } else if (availableNetworkInfo == null && info.isAvailable() && info.getType() != ConnectivityManager.TYPE_VPN) {
              availableNetworkInfo = info;
              availableNetwork = network;
            }
          }
        }
      } else {
        NetworkInfo[] networkInfos = manager.getAllNetworkInfo();
        for (NetworkInfo info : networkInfos) {
          if (info != null) {
            // TODO what happens if we are on VPN on pre-lollipop device?
            if (info.isConnectedOrConnecting()) {
              isOnline = true;
              int type = getConnectionType(info);
              if (connectionType == CONNECTION_TYPE_NONE || type == CONNECTION_TYPE_WIFI) { // XGram: Wi-Fi wins
                connectionType = type;
                chosenNetworkInfo = info;
              }
            } else if (availableNetworkInfo == null && info.isAvailable()) {
              availableNetworkInfo = info;
            }
          }
        }
      }
    }

    if (Settings.instance().forceDisableNetwork()) {
      chosenNetworkInfo = null;
      chosenNetwork = null;
      isOnline = false;
      connectionType = CONNECTION_TYPE_NONE;
    } else if (chosenNetworkInfo == null && activeNetworkInfo != null && activeNetworkInfo.getType() == ConnectivityManager.TYPE_VPN && activeNetworkInfo.isConnected()) {
      // XGram: a VPN that blocks every other network ("Block connections without VPN"): the other networks read as
      // blocked here, but the VPN itself is connected. Its type is the network under it; with nothing under it
      // (Wi-Fi lost and mobile data off while the VPN keeps its tunnel) there is no internet, so waiting for network.
      final int vpnType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? NetworkTypes.detect(manager, activeNetwork) : CONNECTION_TYPE_OTHER;
      if (vpnType != CONNECTION_TYPE_NONE) {
        isOnline = true;
        chosenNetworkInfo = activeNetworkInfo;
        chosenNetwork = activeNetwork;
        connectionType = vpnType;
      }
    } else if (chosenNetworkInfo == null && availableNetworkInfo != null) {
      isOnline = true;
      chosenNetworkInfo = availableNetworkInfo;
      chosenNetwork = availableNetwork;
      connectionType = getConnectionType(availableNetworkInfo);
    }

    boolean isUnavailable = isUnavailable(chosenNetworkInfo);
    setUnavailable(isUnavailable);
    isOnline = isOnline && !isUnavailable;

    if (isOnline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // XGram: TDLib counts traffic under the network type sent to it, so take the type from the network's
      // capabilities: they see through a VPN (its NetworkInfo only says TYPE_VPN) and know roaming.
      int detectedConnectionType = activeNetwork != null ? NetworkTypes.detect(manager, activeNetwork) : NetworkTypes.best(manager);
      if (detectedConnectionType != CONNECTION_TYPE_NONE) {
        connectionType = detectedConnectionType;
      }
    }

    if (isOnline) {
      final int previousConnectionType = this.connectionType;
      setConnectionType(connectionType);
      if (previousConnectionType != CONNECTION_TYPE_NONE && (previousConnectionType != connectionType || hasRouteStateChanged(chosenNetworkInfo) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasRouteChanged(manager, chosenNetwork)))) {
        onNetworkRouteChanged();
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        saveRoute(manager, chosenNetwork);
      }
      saveConnectionRouteInfo(chosenNetworkInfo);
    }
    if (!setIsOnline(isOnline)) {
      checkConnectionType(false);
    }
  }

  private void setUnavailable (boolean isUnavailable) {
    this.isUnavailable = isUnavailable;
  }

  private static boolean isUnavailable (NetworkInfo info) {
    if (info == null) {
      return true;
    }
    switch (info.getState()) {
      case DISCONNECTED:
      case DISCONNECTING:
      case SUSPENDED:
      // case UNKNOWN:
        return true;
    }
    return false;
  }

  private void setSystemDataSaverEnabled (boolean isEnabled) {
    if (connectionType == CONNECTION_TYPE_NONE) {
      this.systemDataSaverEnabled = isEnabled;
      return;
    }
    if (this.systemDataSaverEnabled != isEnabled) {
      this.systemDataSaverEnabled = isEnabled;
      context.onSystemDataSaverStateChanged(isEnabled);
    }
  }

  private void sendTGConnectionType (int type, boolean isOnline) {
    Log.w("sendTGConnectionType type:%d isOnline:%b inBackground:%b this.isOnline:%b", type, isOnline, inBackground, this.isOnline);
    TdApi.NetworkType networkType;
    if (isOnline) {
      switch (type) {
        case CONNECTION_TYPE_ROAMING: {
          networkType = new TdApi.NetworkTypeMobileRoaming();
          break;
        }
        case CONNECTION_TYPE_WIFI: {
          networkType = new TdApi.NetworkTypeWiFi();
          break;
        }
        case CONNECTION_TYPE_MOBILE: {
          networkType = new TdApi.NetworkTypeMobile();
          break;
        }
        default: {
          networkType = new TdApi.NetworkTypeOther();
          break;
        }
      }
    } else {
      networkType = new TdApi.NetworkTypeNone();
    }
    if (Log.isEnabled(Log.TAG_NETWORK_STATE)) {
      Log.i(Log.TAG_NETWORK_STATE, "setConnectionType, networkType: %s", networkType.getClass().getName());
    }
    context.setNetworkType(networkType);
  }

  public void checkNetworkAvailability () {
    if (connectionType == CONNECTION_TYPE_NONE) {
      letsHelpDoge();
    }
  }

  private void setConnectionType (int connectionType) {
    if (this.connectionType != connectionType) {
      boolean online = isOnline || (Config.FAKE_BACKGROUND_CONNECTION_STATE && inBackground);
      int reportType = online ? connectionType : CONNECTION_TYPE_NONE;
      if (online && reportType == CONNECTION_TYPE_NONE && inBackground) {
        reportType = CONNECTION_TYPE_OTHER;
      }
      lastReportedConnectionTimeMs = SystemClock.elapsedRealtime();
      sendTGConnectionType(lastReportedConnectionType = reportType, online);
    }
    if (this.connectionType == CONNECTION_TYPE_NONE) {
      this.connectionType = connectionType;
      return;
    }
    if (this.connectionType != connectionType) {
      int oldConnectionType = this.connectionType;
      this.connectionType = connectionType;
      context.onConnectionTypeChanged(oldConnectionType, connectionType);
    }
  }

  /*public synchronized void setDogeState (int state) {
    if (this.dogState != state) {
      int oldState = dogState;
      this.dogState = state;
      if (Config.USE_CUSTOM_CRASH_MANAGER) {
        if (state == STATE_CONNECTED) {
          CrashManager.instance().check();
        }
      }
      if (oldState == STATE_UPDATING || state == STATE_UPDATING) {
        TGNotificationManager.instance().onWatchDogUpdatingStateChanged(state == STATE_UPDATING);
      }
      if (isOnline && dogState == STATE_CONNECTED) {
        onSynchronized();
      }
    }
  }

  public int getDogeState () {
    return dogState;
  }

  public String translateDogeState () {
    switch (dogState) {
      case STATE_CONNECTED: {
        return UI.getString(R.string.Messages);
      }
      default: {
        return UI.getString(NetworkStatusBarView.getStringForState(dogState));
      }
    }
  }*/
}
