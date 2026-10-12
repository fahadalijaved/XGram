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
package com.fahadali.xgram;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Proxy rules (NagramX / exteraGram): turn the proxy off while on Wi-Fi, on mobile data or while a VPN is connected,
 * and back on when that ends. Only a proxy XGram turned off is turned back on.
 */
public final class XgramProxyRules {
  private XgramProxyRules () { }

  private static final String KEY_PAUSED = "xgram_proxy_paused";
  private static boolean registered;

  public static void init (Context context) {
    if (registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      return;
    }
    registered = true;
    try {
      ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
      if (manager == null) {
        return;
      }
      manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged (@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
          boolean vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
          boolean wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
          boolean cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
          // Called often on mobile data: skip when there are no rules and the proxy isn't paused by one.
          if (!hasRules() && !isPaused()) {
            return;
          }
          UI.post(() -> apply(vpn, wifi, cellular));
        }
      });
    } catch (Throwable ignored) { }
  }

  private static volatile Boolean paused;

  private static boolean isPaused () {
    Boolean value = paused;
    if (value == null) {
      paused = value = Settings.instance().pmc().getBoolean(KEY_PAUSED, false);
    }
    return value;
  }

  public static boolean hasRules () {
    XgramSettings settings = XgramSettings.instance();
    return settings.proxyOffOnVpn() || settings.proxyOffOnWifi() || settings.proxyOffOnMobile();
  }

  private static void apply (boolean vpn, boolean wifi, boolean cellular) {
    XgramSettings settings = XgramSettings.instance();
    Settings tgx = Settings.instance();
    boolean paused = tgx.pmc().getBoolean(KEY_PAUSED, false);
    boolean turnOff = (settings.proxyOffOnVpn() && vpn) || (settings.proxyOffOnWifi() && wifi) || (settings.proxyOffOnMobile() && cellular && !wifi);
    boolean enabled = tgx.checkProxySetting(Settings.PROXY_FLAG_ENABLED);
    if (turnOff) {
      if (enabled) {
        tgx.setProxySetting(Settings.PROXY_FLAG_ENABLED, false);
        tgx.pmc().putBoolean(KEY_PAUSED, true);
        XgramProxyRules.paused = true;
      }
    } else if (paused) {
      tgx.pmc().remove(KEY_PAUSED);
      XgramProxyRules.paused = false;
      if (!enabled && tgx.hasProxyConfiguration()) {
        tgx.setProxySetting(Settings.PROXY_FLAG_ENABLED, true);
      }
    }
  }
}
