/*
 * This file is a part of XGram.
 */
package com.fahadali.xgram.maps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.maplibre.android.MapLibre;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.engine.LocationEngine;
import org.maplibre.android.location.engine.LocationEngineCallback;
import org.maplibre.android.location.engine.LocationEngineDefault;
import org.maplibre.android.location.engine.LocationEngineRequest;
import org.maplibre.android.location.engine.LocationEngineResult;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.maps.UiSettings;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.RunnableData;

/**
 * XGram: the maps (attach menu's Location tab, the full screen map) are MapLibre with OpenStreetMap data from
 * OpenFreeMap: no API key and no billing account, unlike Google Maps (whose key had to allow this app, so the map stayed
 * blank). Light and dark styles follow the theme.
 */
public final class XgramMaps {
  private XgramMaps () { }

  /** As Google Maps' maximum, so zoom levels computed from it stay the same as before. */
  public static final double MAX_ZOOM = 21;

  private static final String STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty";
  private static final String STYLE_DARK = "https://tiles.openfreemap.org/styles/dark";

  private static boolean initialized;

  /** Must run before the first MapView is made. */
  public static void init (@NonNull Context context) {
    if (!initialized) {
      MapLibre.getInstance(context.getApplicationContext());
      initialized = true;
    }
  }

  /** A TextureView map: it is clipped to the rounded card and moves with the sheet (a SurfaceView would not). */
  public static org.maplibre.android.maps.MapLibreMapOptions options (@NonNull Context context) {
    init(context);
    return org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true).compassEnabled(false).logoEnabled(false);
  }

  public static String styleUrl () {
    return styleUrl(Theme.isDark());
  }

  public static String styleUrl (boolean dark) {
    return dark ? STYLE_DARK : STYLE_LIGHT;
  }

  /** No compass, rotation or tilt (as the Google maps had); the OpenStreetMap credit stays, small, at the bottom left. */
  public static void setupUi (@NonNull MapLibreMap map, int attributionBottomMargin) {
    map.setMaxZoomPreference(MAX_ZOOM);
    UiSettings ui = map.getUiSettings();
    ui.setCompassEnabled(false);
    ui.setRotateGesturesEnabled(false);
    ui.setTiltGesturesEnabled(false);
    ui.setLogoEnabled(false);
    ui.setAttributionEnabled(true);
    ui.setAttributionGravity(Gravity.BOTTOM | Gravity.LEFT);
    ui.setAttributionMargins(Screen.dp(8f), 0, 0, attributionBottomMargin);
  }

  /** The blue "my location" dot, fed by the user's location; {@code onLocation} gets each fix. Stop it with {@link MyLocation#stop()}. */
  public static final class MyLocation {
    private final LocationEngine engine;
    private final LocationEngineCallback<LocationEngineResult> callback;
    private @Nullable LocationComponent component;
    private boolean started;

    MyLocation (Context context, RunnableData<Location> onLocation) {
      this.engine = LocationEngineDefault.INSTANCE.getDefaultLocationEngine(context.getApplicationContext());
      this.callback = new LocationEngineCallback<LocationEngineResult>() {
        @Override
        public void onSuccess (LocationEngineResult result) {
          Location location = result != null ? result.getLastLocation() : null;
          if (location == null) {
            return;
          }
          if (component != null) {
            try {
              component.forceLocationUpdate(location);
            } catch (Throwable ignored) { }
          }
          onLocation.runWithData(location);
        }

        @Override
        public void onFailure (@NonNull Exception exception) { }
      };
    }

    @SuppressLint("MissingPermission")
    void start (Context context, MapLibreMap map, Style style) {
      if (started) {
        return;
      }
      try {
        LocationComponent component = map.getLocationComponent();
        component.activateLocationComponent(new LocationComponentActivationOptions.Builder(context, style).useDefaultLocationEngine(false).build());
        component.setLocationComponentEnabled(true);
        component.setCameraMode(CameraMode.NONE);
        component.setRenderMode(RenderMode.NORMAL);
        this.component = component;
      } catch (Throwable ignored) { }
      try {
        engine.requestLocationUpdates(new LocationEngineRequest.Builder(1000L).setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY).setFastestInterval(500L).build(), callback, Looper.getMainLooper());
        engine.getLastLocation(callback);
        started = true;
      } catch (Throwable ignored) { }
    }

    public void stop () {
      if (started) {
        started = false;
        try {
          engine.removeLocationUpdates(callback);
        } catch (Throwable ignored) { }
      }
    }
  }

  /** Shows the user's location on {@code map} once its style has loaded (the caller checked the location permission). */
  public static MyLocation showMyLocation (@NonNull Context context, @NonNull MapLibreMap map, @NonNull RunnableData<Location> onLocation) {
    MyLocation myLocation = new MyLocation(context, onLocation);
    map.getStyle(style -> myLocation.start(context, map, style));
    return myLocation;
  }
}
