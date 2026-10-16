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
package com.fahadali.xgram.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.RelativeSessionLayout;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.SessionIconKt;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ProgressComponentView;

/**
 * Material 3 Expressive for Settings > Devices: each session is a segmented list tile (surfaceBright, the 16dp inset and
 * segment corners come from the shared list decoration) with a 40dp tonal leading icon for its platform (phone,
 * tablet, desktop, web), the device name in titleMedium, the app and the location in bodyMedium onSurfaceVariant, the
 * last active time as trailing labelMedium, and "Secret chats" / "Calls" as M3 filter chips (sentence case, a leading
 * check when on) that toggle the setting in place. The current session's tile is secondaryContainer. The row keeps the
 * child order Telegram X's SettingsAdapter reads (time, title, app, location, progress, icon, secret, calls). Colors are
 * roles resolved at draw time, so day, night and Pure black all apply to rows already built. Main thread only.
 */
public final class XgramM3Sessions {
  private XgramM3Sessions () { }

  private static final float CHIP_HEIGHT = 32f, CHIP_ICON = 18f, CHIP_ICON_START = 8f, CHIP_ICON_GAP = 8f, CHIP_SPACE = 16f;

  public static boolean isRow (@Nullable View view) {
    return view instanceof SessionRow;
  }

  /** The TYPE_SESSION row (SettingHolder), with Expressive design. */
  public static RelativeLayout newRow (Context context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
    return new SessionRow(context, onClickListener, themeProvider);
  }

  /** Tile color, icon and chips of {@code session}; the texts are set by the controller as before. */
  public static void bindRow (@NonNull RelativeLayout parent, @NonNull TdApi.Session session, boolean current, boolean inProgress) {
    if (!(parent instanceof SessionRow)) {
      return;
    }
    SessionRow row = (SessionRow) parent;
    row.setState(current, !current && session.isPasswordPending);
    row.icon.setImageResource(XgramIconPack.map(iconOf(session)));
    final boolean chips = !session.isPasswordPending;
    row.secret.setVisibility(chips ? View.VISIBLE : View.GONE);
    row.calls.setVisibility(chips ? View.VISIBLE : View.GONE);
    // An incomplete login attempt: an "Unconfirmed" assist chip in the chips' place
    row.unconfirmed.setVisibility(chips ? View.GONE : View.VISIBLE);
    row.secret.setChecked(session.canAcceptSecretChats, current);
    row.calls.setChecked(session.canAcceptCalls, current);
    row.secret.setEnabled(!inProgress);
    row.calls.setEnabled(!inProgress);
  }

  /**
   * The leading icon: with the Classic icon pack (outlined with Expressive design) an outlined phone, tablet, desktop or
   * globe for the platform; any other pack draws its own glyph of Telegram X's platform icon.
   */
  @DrawableRes
  public static int iconOf (@NonNull TdApi.Session session) {
    if (XgramIconPack.current() != XgramIconPack.PACK_TELEGRAM_X) {
      return SessionIconKt.asIcon(session);
    }
    if (session.apiId == 21724) { // Telegram X (and XGram) on Android
      return R.drawable.xgram_m3e_r4pre_device_phone_24;
    }
    switch (session.deviceType.getConstructor()) {
      case TdApi.SessionDeviceTypeAndroid.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeIphone.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeApple.CONSTRUCTOR:
        return R.drawable.xgram_m3e_r4pre_device_phone_24;
      case TdApi.SessionDeviceTypeIpad.CONSTRUCTOR:
        return R.drawable.xgram_m3e_r4pre_device_tablet_24;
      case TdApi.SessionDeviceTypeWindows.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeMac.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeLinux.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeUbuntu.CONSTRUCTOR:
        return R.drawable.xgram_m3e_r4pre_device_desktop_24;
      case TdApi.SessionDeviceTypeFirefox.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeOpera.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeEdge.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeChrome.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeSafari.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeVivaldi.CONSTRUCTOR:
      case TdApi.SessionDeviceTypeBrave.CONSTRUCTOR:
        return R.drawable.xgram_m3e_r4pre_device_web_24;
      default:
        return SessionIconKt.asIcon(session); // consoles and unknown devices keep Telegram X's glyph
    }
  }

  // Row

  public static final class SessionRow extends RelativeSessionLayout {
    final XgramExpressive.MorphDrawable tile;
    final RoleTextView time, title, app, location;
    final ProgressComponentView progress;
    final IconView icon;
    final ChipView secret, calls;
    final AssistChipView unconfirmed;
    private boolean current, pending, bound;

    SessionRow (Context context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
      super(context);
      final boolean rtl = Lang.rtl();
      setOnClickListener(onClickListener);
      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
      Views.setClickable(this);

      // The tile: the list decoration sets its segment corners every frame (it finds the morph in the ripple)
      tile = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
      tile.setColorRole(XgramExpressive.SURFACE_BRIGHT);
      tile.setHost(this);
      setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE, tile, tile.newMask()));

      RelativeLayout.LayoutParams params;

      // 0: last active time, trailing, on the title's baseline
      time = new RoleTextView(context, XgramExpressive.TS_LABEL_M, XgramExpressive.ON_SURFACE_VARIANT);
      time.setId(R.id.session_time);
      time.setPadding(rtl ? 0 : Screen.dp(12f), 0, rtl ? Screen.dp(12f) : 0, 0);
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.addRule(rtl ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
      params.addRule(RelativeLayout.ALIGN_BASELINE, R.id.session_title);
      time.setLayoutParams(params);
      addView(time);

      // 1: device, titleMedium
      title = new RoleTextView(context, XgramExpressive.TS_TITLE_M, XgramExpressive.ON_SURFACE);
      title.setId(R.id.session_title);
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (rtl) {
        params.addRule(RelativeLayout.RIGHT_OF, R.id.session_time);
        params.addRule(RelativeLayout.LEFT_OF, R.id.session_icon);
        params.rightMargin = Screen.dp(16f);
      } else {
        params.addRule(RelativeLayout.LEFT_OF, R.id.session_time);
        params.addRule(RelativeLayout.RIGHT_OF, R.id.session_icon);
        params.leftMargin = Screen.dp(16f);
      }
      title.setLayoutParams(params);
      addView(title);

      // 2: app and version, 3: location, bodyMedium onSurfaceVariant
      app = new RoleTextView(context, XgramExpressive.TS_BODY_M, XgramExpressive.ON_SURFACE_VARIANT);
      app.setId(R.id.session_device);
      app.setLayoutParams(belowTitleColumn(R.id.session_title, rtl));
      addView(app);

      location = new RoleTextView(context, XgramExpressive.TS_BODY_M, XgramExpressive.ON_SURFACE_VARIANT);
      location.setId(R.id.session_location);
      location.setLayoutParams(belowTitleColumn(R.id.session_device, rtl));
      addView(location);

      // 4: progress while the session is being terminated (it fades the time out)
      progress = new ProgressComponentView(context);
      progress.initMedium(0f);
      progress.setInverseView(time);
      params = new RelativeLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
      params.addRule(rtl ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
      params.topMargin = Screen.dp(5f);
      progress.setLayoutParams(params);
      addView(progress);

      // 5: 40dp tonal leading icon, top-aligned as in M3 three-line items
      icon = new IconView(context);
      icon.setId(R.id.session_icon);
      params = new RelativeLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f));
      params.addRule(rtl ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT);
      icon.setLayoutParams(params);
      addView(icon);

      // 6, 7: filter chips
      secret = new ChipView(context, R.string.XgramM3eR4preSbSecretChats);
      secret.setId(R.id.session_secret);
      secret.setOnClickListener(onClickListener);
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CHIP_HEIGHT));
      params.addRule(RelativeLayout.BELOW, R.id.session_location);
      params.addRule(rtl ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, R.id.session_title);
      params.topMargin = Screen.dp(8f);
      secret.setLayoutParams(params);
      addView(secret);

      calls = new ChipView(context, R.string.XgramM3eR4preSbCalls);
      calls.setId(R.id.session_calls);
      calls.setOnClickListener(onClickListener);
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CHIP_HEIGHT));
      params.addRule(RelativeLayout.BELOW, R.id.session_location);
      params.addRule(rtl ? RelativeLayout.LEFT_OF : RelativeLayout.RIGHT_OF, R.id.session_secret);
      params.topMargin = Screen.dp(8f);
      if (rtl) {
        params.rightMargin = Screen.dp(8f);
      } else {
        params.leftMargin = Screen.dp(8f);
      }
      calls.setLayoutParams(params);
      addView(calls);

      // 8: "Unconfirmed" (incomplete login attempts), after the children SettingsAdapter reads by index
      unconfirmed = new AssistChipView(context, R.string.XgramR5ListsUnconfirmed, R.drawable.baseline_warning_18);
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(CHIP_HEIGHT));
      params.addRule(RelativeLayout.BELOW, R.id.session_location);
      params.addRule(rtl ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, R.id.session_title);
      params.topMargin = Screen.dp(8f);
      unconfirmed.setLayoutParams(params);
      unconfirmed.setVisibility(View.GONE);
      addView(unconfirmed);

      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(unconfirmed);
        // Roles are re-read in onDraw: a theme change only has to redraw every part
        themeProvider.addThemeInvalidateListener(this);
        themeProvider.addThemeInvalidateListener(time);
        themeProvider.addThemeInvalidateListener(title);
        themeProvider.addThemeInvalidateListener(app);
        themeProvider.addThemeInvalidateListener(location);
        themeProvider.addThemeInvalidateListener(icon);
        themeProvider.addThemeInvalidateListener(secret);
        themeProvider.addThemeInvalidateListener(calls);
      }
    }

    private static RelativeLayout.LayoutParams belowTitleColumn (int anchorId, boolean rtl) {
      RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.addRule(RelativeLayout.BELOW, anchorId);
      params.addRule(rtl ? RelativeLayout.ALIGN_RIGHT : RelativeLayout.ALIGN_LEFT, R.id.session_title);
      return params;
    }

    /** This device: a secondaryContainer tile with on-container text and a primary icon. */
    void setCurrent (boolean current) {
      setState(current, false);
    }

    /**
     * This device: a secondaryContainer tile with on-container text and a primary icon. An incomplete login attempt
     * ({@code pending}): an errorContainer tile, the device name in error, an error icon circle.
     */
    void setState (boolean current, boolean pending) {
      if (bound && this.current == current && this.pending == pending) {
        return;
      }
      this.bound = true;
      this.current = current;
      this.pending = pending;
      if (pending) {
        tile.setColorRole(XgramExpressive.ERROR_CONTAINER);
        title.setRole(XgramExpressive.ERROR);
        app.setRole(XgramExpressive.ON_ERROR_CONTAINER);
        location.setRole(XgramExpressive.ON_ERROR_CONTAINER);
        time.setRole(XgramExpressive.ON_ERROR_CONTAINER);
        icon.setRoles(XgramExpressive.ERROR, XgramExpressive.ON_ERROR);
        return;
      }
      tile.setColorRole(current ? XgramExpressive.SECONDARY_CONTAINER : XgramExpressive.SURFACE_BRIGHT);
      final int content = current ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE;
      final int variant = current ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT;
      title.setRole(content);
      app.setRole(variant);
      location.setRole(variant);
      time.setRole(variant);
      icon.setRoles(current ? XgramExpressive.PRIMARY : XgramExpressive.PRIMARY_CONTAINER, current ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_PRIMARY_CONTAINER);
    }
  }

  // Websites (TYPE_SESSION_WITH_AVATAR)

  public static boolean isAvatarRow (@Nullable View view) {
    return view instanceof SessionAvatarRow;
  }

  /** The TYPE_SESSION_WITH_AVATAR row (Settings > Devices > Websites), with Expressive design. */
  public static RelativeLayout newAvatarRow (Context context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
    return new SessionAvatarRow(context, onClickListener, themeProvider);
  }

  /**
   * A connected website as a segmented list tile: the bot's 40dp avatar, the domain in titleMedium, the bot, browser and
   * platform in bodyMedium onSurfaceVariant (two lines at most), the IP address and location in bodySmall, the last
   * active time as trailing labelMedium. Child order as Telegram X's (time, title, text, location, progress, avatar):
   * SettingsAdapter and SettingHolder read them by index.
   */
  public static final class SessionAvatarRow extends RelativeSessionLayout {
    SessionAvatarRow (Context context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
      super(context);
      final boolean rtl = Lang.rtl();
      setOnClickListener(onClickListener);
      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(12f));
      setMinimumHeight(Screen.dp(72f));
      Views.setClickable(this);

      XgramExpressive.MorphDrawable tile = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_NONE, XgramExpressive.LIST_PRESSED_CORNER);
      tile.setColorRole(XgramExpressive.SURFACE_BRIGHT);
      tile.setHost(this);
      setBackground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE, tile, tile.newMask()));

      RelativeLayout.LayoutParams params;

      // 0: last active time, trailing, on the title's baseline
      RoleTextView time = new RoleTextView(context, XgramExpressive.TS_LABEL_M, XgramExpressive.ON_SURFACE_VARIANT);
      time.setId(R.id.session_time);
      time.setPadding(rtl ? 0 : Screen.dp(12f), 0, rtl ? Screen.dp(12f) : 0, 0);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        time.setFontFeatureSettings("tnum");
      }
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.addRule(rtl ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
      params.addRule(RelativeLayout.ALIGN_BASELINE, R.id.session_title);
      time.setLayoutParams(params);
      addView(time);

      // 1: domain, titleMedium
      RoleTextView title = new RoleTextView(context, XgramExpressive.TS_TITLE_M, XgramExpressive.ON_SURFACE);
      title.setId(R.id.session_title);
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      if (rtl) {
        params.addRule(RelativeLayout.RIGHT_OF, R.id.session_time);
        params.addRule(RelativeLayout.LEFT_OF, R.id.session_icon);
        params.rightMargin = Screen.dp(16f);
      } else {
        params.addRule(RelativeLayout.LEFT_OF, R.id.session_time);
        params.addRule(RelativeLayout.RIGHT_OF, R.id.session_icon);
        params.leftMargin = Screen.dp(16f);
      }
      title.setLayoutParams(params);
      addView(title);

      // 2: bot, browser and platform, bodyMedium, two lines at most
      RoleTextView text = new RoleTextView(context, XgramExpressive.TS_BODY_M, XgramExpressive.ON_SURFACE_VARIANT);
      text.setId(R.id.session_device);
      text.setSingleLine(false);
      text.setMaxLines(2);
      text.setLayoutParams(SessionRow.belowTitleColumn(R.id.session_title, rtl));
      addView(text);

      // 3: IP address and location, bodySmall
      RoleTextView location = new RoleTextView(context, XgramExpressive.TS_BODY_S, XgramExpressive.ON_SURFACE_VARIANT);
      location.setId(R.id.session_location);
      location.setLayoutParams(SessionRow.belowTitleColumn(R.id.session_device, rtl));
      addView(location);

      // 4: progress while the website is being disconnected (it fades the time out)
      ProgressComponentView progress = new ProgressComponentView(context);
      progress.initMedium(0f);
      progress.setInverseView(time);
      params = new RelativeLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
      params.addRule(rtl ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
      params.topMargin = Screen.dp(5f);
      progress.setLayoutParams(params);
      addView(progress);

      // 5: the bot's 40dp avatar, top-aligned as in M3 three-line items
      org.thunderdog.challegram.widget.AvatarView avatar = new org.thunderdog.challegram.widget.AvatarView(context);
      avatar.setId(R.id.session_icon);
      avatar.setUseCustomWindowManagement();
      avatar.setLettersSizeDp(16f);
      params = new RelativeLayout.LayoutParams(Screen.dp(40f), Screen.dp(40f));
      params.addRule(rtl ? RelativeLayout.ALIGN_PARENT_RIGHT : RelativeLayout.ALIGN_PARENT_LEFT);
      avatar.setLayoutParams(params);
      addView(avatar);

      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(this);
        themeProvider.addThemeInvalidateListener(time);
        themeProvider.addThemeInvalidateListener(title);
        themeProvider.addThemeInvalidateListener(text);
        themeProvider.addThemeInvalidateListener(location);
        themeProvider.addThemeInvalidateListener(avatar);
      }
    }
  }

  /** A single line of text in a type scale style and a color role. */
  static final class RoleTextView extends NoScrollTextView {
    private int role;

    RoleTextView (Context context, int textStyle, int role) {
      super(context);
      this.role = role;
      XgramExpressive.applyText(this, textStyle, false);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        setLineHeight(Screen.dp(XgramExpressive.lineHeightSp(textStyle)));
      }
      setGravity(Lang.gravity());
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);
      setTextColor(XgramExpressive.color(role));
    }

    void setRole (int role) {
      if (this.role != role) {
        this.role = role;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final int color = XgramExpressive.color(role);
      if (getCurrentTextColor() != color) {
        setTextColor(color);
      }
      super.onDraw(c);
    }
  }

  /** The 40dp tonal circle with the platform icon in its on-role. */
  static final class IconView extends ImageView {
    private final XgramExpressive.MorphDrawable circle;
    private int contentRole = XgramExpressive.ON_PRIMARY_CONTAINER;
    private int tint;
    private boolean tinted;

    IconView (Context context) {
      super(context);
      setScaleType(ScaleType.CENTER);
      circle = new XgramExpressive.MorphDrawable(0, XgramExpressive.CORNER_FULL, Float.NaN);
      circle.setColorRole(XgramExpressive.PRIMARY_CONTAINER);
      circle.setHost(this);
      setBackground(circle);
    }

    void setRoles (int containerRole, int contentRole) {
      circle.setColorRole(containerRole);
      if (this.contentRole != contentRole) {
        this.contentRole = contentRole;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final int color = XgramExpressive.color(contentRole);
      if (!tinted || tint != color) {
        tinted = true;
        tint = color;
        setColorFilter(color, PorterDuff.Mode.SRC_IN);
      }
      super.onDraw(c);
    }
  }

  /**
   * An M3 filter chip (FilterChipTokens): 32dp, 8dp corners, labelLarge. Selected: a container with an 18dp leading
   * check (secondaryContainer, or primary on the current session's secondaryContainer tile); unselected: a 1dp outline
   * (outlineVariant, or outline on the current tile). Disabled: content .38, container .12.
   */
  static final class ChipView extends NoScrollTextView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final @Nullable Drawable check;
    private boolean checked, onTile;

    ChipView (Context context, @StringRes int textRes) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setSingleLine(true);
      setGravity(Gravity.CENTER_VERTICAL);
      setText(Lang.getString(textRes));
      Views.setClickable(this);
      check = Drawables.get(getResources(), R.drawable.baseline_check_18);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // The state layer goes over the container this view draws itself
        XgramExpressive.MorphDrawable mask = new XgramExpressive.MorphDrawable(0xff000000, XgramExpressive.CORNER_S, Float.NaN);
        setForeground(XgramAppMotion.ThemedRipple.ofRole(XgramExpressive.ON_SURFACE_VARIANT, null, mask));
      }
      updatePadding();
    }

    void setChecked (boolean checked, boolean onTile) {
      if (this.checked != checked || this.onTile != onTile) {
        final boolean paddingChanged = this.checked != checked;
        this.checked = checked;
        this.onTile = onTile;
        if (paddingChanged) {
          updatePadding();
        }
        invalidate();
      }
    }

    private void updatePadding () {
      final int start = Screen.dp(checked ? CHIP_ICON_START + CHIP_ICON + CHIP_ICON_GAP : CHIP_SPACE);
      final int end = Screen.dp(CHIP_SPACE);
      final boolean rtl = Lang.rtl();
      setPadding(rtl ? end : start, 0, rtl ? start : end, 0);
    }

    @Override
    public void setEnabled (boolean enabled) {
      if (enabled != isEnabled()) {
        super.setEnabled(enabled);
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      int container, content;
      if (checked) {
        container = XgramExpressive.color(onTile ? XgramExpressive.PRIMARY : XgramExpressive.SECONDARY_CONTAINER);
        content = XgramExpressive.color(onTile ? XgramExpressive.ON_PRIMARY : XgramExpressive.ON_SECONDARY_CONTAINER);
      } else {
        container = XgramExpressive.color(onTile ? XgramExpressive.OUTLINE : XgramExpressive.OUTLINE_VARIANT);
        content = XgramExpressive.color(onTile ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE_VARIANT);
      }
      if (!isEnabled()) {
        final int onSurface = XgramExpressive.color(onTile ? XgramExpressive.ON_SECONDARY_CONTAINER : XgramExpressive.ON_SURFACE);
        container = XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTAINER);
        content = XgramExpressive.withAlpha(onSurface, XgramExpressive.DISABLED_CONTENT);
      }
      final float radius = Screen.dpf(XgramExpressive.CORNER_S);
      paint.setColor(container);
      if (checked) {
        paint.setStyle(Paint.Style.FILL);
        rect.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(rect, radius, radius, paint);
        if (check != null) {
          final int size = Screen.dp(CHIP_ICON);
          final float x = Lang.rtl() ? getWidth() - Screen.dp(CHIP_ICON_START) - size : Screen.dp(CHIP_ICON_START);
          final float y = (getHeight() - size) / 2f;
          Drawables.draw(c, check, x, y, Paints.getPorterDuffPaint(content));
        }
      } else {
        final float stroke = Screen.dpf(1f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        rect.set(stroke / 2f, stroke / 2f, getWidth() - stroke / 2f, getHeight() - stroke / 2f);
        c.drawRoundRect(rect, radius - stroke / 2f, radius - stroke / 2f, paint);
      }
      if (getCurrentTextColor() != content) {
        setTextColor(content);
      }
      super.onDraw(c);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo (AccessibilityNodeInfo info) {
      super.onInitializeAccessibilityNodeInfo(info);
      info.setCheckable(true);
      info.setChecked(checked);
    }
  }

  /**
   * An M3 assist chip that only tells a state (AssistChipTokens: 32dp, 8dp corners, labelLarge, an 18dp leading icon,
   * a 1dp outline), on the errorContainer tile of an incomplete login attempt: outline and icon in error, the label in
   * onErrorContainer. Not clickable: the row's tap opens the attempt.
   */
  static final class AssistChipView extends NoScrollTextView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final @Nullable Drawable icon;

    AssistChipView (Context context, @StringRes int textRes, @DrawableRes int iconRes) {
      super(context);
      XgramExpressive.applyText(this, XgramExpressive.TS_LABEL_L, false);
      setAllCaps(false);
      setSingleLine(true);
      setGravity(Gravity.CENTER_VERTICAL);
      setText(Lang.getString(textRes));
      setClickable(false);
      setFocusable(false);
      icon = Drawables.get(getResources(), XgramIconPack.map(iconRes));
      final int start = Screen.dp(CHIP_ICON_START + CHIP_ICON + CHIP_ICON_GAP), end = Screen.dp(CHIP_SPACE);
      final boolean rtl = Lang.rtl();
      setPadding(rtl ? end : start, 0, rtl ? start : end, 0);
      paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw (Canvas c) {
      final int outline = XgramExpressive.color(XgramExpressive.ERROR);
      final int content = XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER);
      final float stroke = Screen.dpf(1f);
      final float radius = Screen.dpf(XgramExpressive.CORNER_S);
      paint.setColor(outline);
      paint.setStrokeWidth(stroke);
      rect.set(stroke / 2f, stroke / 2f, getWidth() - stroke / 2f, getHeight() - stroke / 2f);
      c.drawRoundRect(rect, radius - stroke / 2f, radius - stroke / 2f, paint);
      if (icon != null) {
        final int size = Screen.dp(CHIP_ICON);
        final float x = Lang.rtl() ? getWidth() - Screen.dp(CHIP_ICON_START) - size : Screen.dp(CHIP_ICON_START);
        final float y = (getHeight() - size) / 2f;
        Drawables.draw(c, icon, x, y, Paints.getPorterDuffPaint(outline));
      }
      if (getCurrentTextColor() != content) {
        setTextColor(content);
      }
      super.onDraw(c);
    }
  }

  // Terminate all other sessions

  /** SettingView's icon top (dp): 20 for TYPE_INFO_COMPACT rows (64dp), 16 for TYPE_SETTING rows (56dp). */
  public static final float ICON_TOP_COMPACT = 20f, ICON_TOP_SETTING = 16f;

  /**
   * The leading icon of an error row (a SettingView): an errorContainer circle with the icon in onErrorContainer, drawn
   * over SettingView's own primaryContainer circle. Its geometry is SettingView's with Expressive design on (the 24dp
   * icon box 24dp in from the start and {@code iconTopDp} from the top, a 40dp circle around it).
   */
  public static final class ErrorIconModifier implements DrawModifier {
    private final @DrawableRes int iconRes;
    private final float iconTopDp;
    private @Nullable Drawable icon;

    public ErrorIconModifier (@DrawableRes int iconRes, float iconTopDp) {
      this.iconRes = iconRes;
      this.iconTopDp = iconTopDp;
    }

    @Override
    public void afterDraw (View view, Canvas c) {
      if (!XgramExpressive.isEnabled()) {
        return;
      }
      if (icon == null) {
        icon = Drawables.get(view.getResources(), iconRes);
        if (icon == null) {
          return;
        }
      }
      final float iconBox = Screen.dp(24f);
      final float cx = Lang.rtl() ? view.getWidth() - Screen.dp(24f) - iconBox / 2f : Screen.dp(24f) + iconBox / 2f;
      final float cy = Screen.dp(iconTopDp) + icon.getMinimumHeight() / 2f;
      // one pixel wider than SettingView's circle, so its antialiased edge does not show around this one
      c.drawCircle(cx, cy, Screen.dp(20f) + 1, Paints.fillingPaint(XgramExpressive.color(XgramExpressive.ERROR_CONTAINER)));
      Drawables.drawCentered(c, icon, cx, cy, Paints.getPorterDuffPaint(XgramExpressive.color(XgramExpressive.ON_ERROR_CONTAINER)));
    }
  }
}
