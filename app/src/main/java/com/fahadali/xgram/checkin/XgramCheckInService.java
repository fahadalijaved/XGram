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
package com.fahadali.xgram.checkin;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.List;

/**
 * The location service of a running Check In (foreground, type location). It only runs when the record needs a
 * location, location access is granted and Android allows starting it from where the app is. It reads the platform
 * location (fused when there is one, else network, else GPS with precise access), the battery, and the phone switching
 * off, and keeps the account's TDLib connected while it runs. Background location is never asked for.
 */
public final class XgramCheckInService extends Service {
  private static volatile XgramCheckInService instance;

  static boolean isRunning () {
    return instance != null;
  }

  /** The record changed: the location request is checked again (interval, accuracy, permission). Any thread. */
  static void refresh () {
    UI.post(() -> {
      XgramCheckInService service = instance;
      if (service != null) {
        service.updateRequest();
      }
    });
  }

  private static final long TICK_MS = 5 * 60 * 1000L;
  private static final long FIRST_FIX_TIMEOUT_MS = 15_000L;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable tick = new Runnable() {
    @Override
    public void run () {
      XgramCheckIn.onTick();
      handler.postDelayed(this, TICK_MS);
    }
  };

  private boolean foreground;
  private @Nullable Tdlib heldTdlib;
  private @Nullable LocationManager locationManager;
  private @Nullable String provider;
  private long requestedIntervalMs;
  private boolean requestedHigh;
  private boolean firstFixRequested;
  private @Nullable CancellationSignal firstFixSignal;
  private @Nullable BroadcastReceiver batteryReceiver;
  private int lastPercent = -1;
  private boolean lastCharging;

  private final LocationListenerCompat listener = new LocationListenerCompat() {
    @Override
    public void onLocationChanged (@NonNull Location location) {
      onLocation(location);
    }

    @Override
    public void onProviderEnabled (@NonNull String provider) {
      updateRequest();
    }

    @Override
    public void onProviderDisabled (@NonNull String provider) {
      updateRequest();
    }
  };

  @Override
  public void onCreate () {
    super.onCreate();
    instance = this;
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    CheckInRecord r = XgramCheckIn.activeSnapshot();
    Notification notification = XgramCheckInNotifier.buildOngoing(this, r);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(XgramCheckInNotifier.ID_ONGOING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
      } else {
        startForeground(XgramCheckInNotifier.ID_ONGOING, notification);
      }
      foreground = true;
    } catch (Throwable t) {
      XgramCheckIn.log("startForeground refused: %s", t.toString());
      XgramCheckIn.onServiceFailed();
      stopSelf();
      return START_NOT_STICKY;
    }
    if (r == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (heldTdlib == null) {
      Tdlib tdlib = XgramCheckIn.tdlibFor(r.acc);
      if (tdlib != null) {
        tdlib.changeLocationReferenceCount(1);
        heldTdlib = tdlib;
      }
    }
    startBattery();
    updateRequest();
    handler.removeCallbacks(tick);
    handler.postDelayed(tick, TICK_MS);
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy () {
    instance = null;
    handler.removeCallbacks(tick);
    stopLocation();
    if (batteryReceiver != null) {
      try {
        unregisterReceiver(batteryReceiver);
      } catch (Throwable ignored) { }
      batteryReceiver = null;
    }
    if (heldTdlib != null) {
      heldTdlib.changeLocationReferenceCount(-1);
      heldTdlib = null;
    }
    if (foreground) {
      foreground = false;
      // A Check In that still runs keeps its notification (posted again without the service).
      boolean keep = XgramCheckIn.activeSnapshot() != null;
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          stopForeground(keep ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
        } else {
          stopForeground(!keep);
        }
      } catch (Throwable ignored) { }
    }
    XgramCheckIn.onServiceStopped();
    super.onDestroy();
  }

  // Battery and shutdown

  private void startBattery () {
    if (batteryReceiver != null) {
      return;
    }
    batteryReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive (Context context, Intent intent) {
        if (intent == null) {
          return;
        }
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
          XgramCheckIn.onShutdown();
        } else {
          onBatteryChanged(intent);
        }
      }
    };
    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    filter.addAction(Intent.ACTION_SHUTDOWN);
    try {
      Intent sticky = ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
      if (sticky != null) {
        onBatteryChanged(sticky);
      }
    } catch (Throwable t) {
      XgramCheckIn.log("battery receiver: %s", t.toString());
    }
  }

  private void onBatteryChanged (Intent intent) {
    int[] state = readBattery(intent);
    if (state == null) {
      return;
    }
    boolean isCharging = state[1] != 0;
    if (state[0] != lastPercent || isCharging != lastCharging) {
      lastPercent = state[0];
      lastCharging = isCharging;
      XgramCheckIn.onBattery(state[0], isCharging);
    }
  }

  /** {percent, charging (1/0)} from an ACTION_BATTERY_CHANGED intent, or null. */
  @Nullable
  static int[] readBattery (@Nullable Intent intent) {
    if (intent == null) {
      return null;
    }
    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    if (level < 0 || scale <= 0) {
      return null;
    }
    int percent = Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
    boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0;
    return new int[] {percent, isCharging ? 1 : 0};
  }

  /** The battery now, from the sticky broadcast (no receiver kept). */
  @Nullable
  static int[] readBattery (Context context) {
    try {
      return readBattery(context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
    } catch (Throwable t) {
      return null;
    }
  }

  // Location

  @Nullable
  private String chooseProvider (LocationManager lm, boolean fine) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER)) {
        return LocationManager.FUSED_PROVIDER;
      }
      List<String> all = lm.getAllProviders();
      if (all.contains(LocationManager.NETWORK_PROVIDER) && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        return LocationManager.NETWORK_PROVIDER;
      }
      if (fine && all.contains(LocationManager.GPS_PROVIDER)) {
        return LocationManager.GPS_PROVIDER;
      }
      if (all.contains(LocationManager.NETWORK_PROVIDER)) {
        return LocationManager.NETWORK_PROVIDER; // off now: reports once it is turned on
      }
    } catch (Throwable ignored) { }
    return null;
  }

  private void updateRequest () {
    if (instance != this) {
      return;
    }
    if (!XgramCheckIn.hasLocationPermission(this)) {
      stopLocation();
      XgramCheckIn.onLocationState(true, false);
      return;
    }
    if (locationManager == null) {
      locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }
    LocationManager lm = locationManager;
    if (lm == null) {
      return;
    }
    boolean fine = XgramCheckIn.hasFineLocation(this);
    boolean servicesOff = !LocationManagerCompat.isLocationEnabled(lm);
    String p = chooseProvider(lm, fine);
    if (p == null) {
      stopLocation();
      XgramCheckIn.onLocationState(false, true);
      return;
    }
    long interval = XgramCheckIn.desiredInterval() * 1000L;
    boolean high = XgramCheckIn.wantsHighAccuracy() && fine;
    if (p.equals(provider) && interval == requestedIntervalMs && high == requestedHigh) {
      XgramCheckIn.onLocationState(false, servicesOff);
      return;
    }
    try {
      LocationManagerCompat.removeUpdates(lm, listener);
    } catch (Throwable ignored) { }
    LocationRequestCompat request = new LocationRequestCompat.Builder(interval)
      .setQuality(high ? LocationRequestCompat.QUALITY_HIGH_ACCURACY : LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
      .setMinUpdateIntervalMillis(interval / 2)
      .build();
    try {
      LocationManagerCompat.requestLocationUpdates(lm, p, request, ContextCompat.getMainExecutor(this), listener);
      provider = p;
      requestedIntervalMs = interval;
      requestedHigh = high;
      XgramCheckIn.onLocationState(false, servicesOff);
      XgramCheckIn.log("location updates %s every %ds high=%b", p, interval / 1000, high);
    } catch (SecurityException e) {
      provider = null;
      XgramCheckIn.onLocationState(true, servicesOff);
      return;
    } catch (Throwable t) {
      provider = null;
      XgramCheckIn.log("location updates: %s", t.toString());
      return;
    }
    if (!firstFixRequested) {
      firstFixRequested = true;
      requestFirstFix(lm, p);
    }
  }

  private void requestFirstFix (LocationManager lm, String p) {
    try {
      Location last = lm.getLastKnownLocation(p);
      if (last != null && System.currentTimeMillis() - last.getTime() < 2 * 60 * 1000L) {
        onLocation(last);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        CancellationSignal signal = new CancellationSignal();
        firstFixSignal = signal;
        lm.getCurrentLocation(p, signal, ContextCompat.getMainExecutor(this), location -> {
          if (location != null && instance == this) {
            onLocation(location);
          }
        });
        handler.postDelayed(signal::cancel, FIRST_FIX_TIMEOUT_MS);
      }
    } catch (SecurityException e) {
      XgramCheckIn.onLocationState(true, false);
    } catch (Throwable ignored) { }
  }

  private void onLocation (Location location) {
    if (instance != this) {
      return;
    }
    XgramCheckIn.onFix(location, !XgramCheckIn.hasFineLocation(this));
  }

  private void stopLocation () {
    CancellationSignal signal = firstFixSignal;
    if (signal != null) {
      firstFixSignal = null;
      signal.cancel();
    }
    LocationManager lm = locationManager;
    if (lm != null && provider != null) {
      try {
        LocationManagerCompat.removeUpdates(lm, listener);
      } catch (Throwable ignored) { }
    }
    provider = null;
    requestedIntervalMs = 0;
  }
}
