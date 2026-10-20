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

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.fahadali.xgram.XgramSettings;
import com.fahadali.xgram.appearance.XgramExpressive;
import com.fahadali.xgram.compose.XgramR5ChatPickers;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.popups.ModernOptions;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.lambda.RunnableBool;
import tgx.td.ChatId;

/**
 * Check In's screens, all built from existing sheets so they get the M3E look with Expressive design and Telegram X's
 * without: the pre-check, the one-time explainer, the conflict sheet, the start sheet (ViewController.showSettings),
 * the time picker, the permission chain and the manage sheet. Nothing is written before Start.
 */
public final class XgramCheckInSheet {
  private XgramCheckInSheet () { }

  /** A place picked in the location picker (MediaLayout in MODE_LOCATION) for "End when I arrive". */
  public interface PickListener {
    void onPick (TdApi.Location location, @Nullable String title);
    void onCancel ();
  }

  /** "15 minutes", "2 hours": the timer and alert delay choices. */
  public static String durationName (int minutes) {
    return minutes < 60 || minutes % 60 != 0 ? Lang.plural(R.string.xMinutes, minutes) : Lang.plural(R.string.xHours, minutes / 60);
  }

  /** "200 m": the arrival radius choices. */
  public static String radiusName (int meters) {
    return Lang.getString(R.string.XgramCheckInMeters, meters);
  }

  private static final int[] PRESET_IDS = {
    R.id.btn_xgramCheckIn15m, R.id.btn_xgramCheckIn30m, R.id.btn_xgramCheckIn1h,
    R.id.btn_xgramCheckIn2h, R.id.btn_xgramCheckIn4h, R.id.btn_xgramCheckIn8h
  };

  /** The ⋮ menu item: manage the Check In of this chat, or start one. */
  public static void open (MessagesController c, long chatId, @Nullable TdApi.MessageTopic topic) {
    Tdlib tdlib = c.tdlib();
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat == null) {
      return;
    }
    // Only a forum topic is kept; with "All messages" or no topic open, messages go to General.
    final int topicId = topic instanceof TdApi.MessageTopicForum ? ((TdApi.MessageTopicForum) topic).forumTopicId : 0;
    CheckInRecord active = XgramCheckIn.activeSnapshot();
    if (active != null) {
      if (active.acc == tdlib.id() && active.chat == chatId) {
        showManage(c, active);
      } else {
        showConflict(c, active);
      }
      return;
    }
    if (!XgramCheckIn.canStart(tdlib, chat)) {
      XgramCheckIn.feedback(Lang.getString(R.string.XgramCheckInUnavailable));
      return;
    }
    final String title = tdlib.chatTitle(chat);
    preCheck(c, chat, title, () -> explainer(c, title, () -> showStartSheet(c, chatId, topicId, title)));
  }

  // Before the start sheet

  /** Private chats: whether the person accepts messages from you without Stars (3 s at most, else it goes on). */
  private static void preCheck (MessagesController c, TdApi.Chat chat, String title, Runnable next) {
    Tdlib tdlib = c.tdlib();
    if (!ChatId.isPrivate(chat.id)) {
      next.run();
      return;
    }
    long userId = tdlib.chatUserId(chat);
    if (userId == 0) {
      next.run();
      return;
    }
    final AtomicBoolean done = new AtomicBoolean();
    tdlib.send(new TdApi.CanSendMessageToUser(userId, false), (result, error) -> UI.post(() -> {
      if (!done.compareAndSet(false, true) || c.isDestroyed()) {
        return;
      }
      String text = null;
      if (result instanceof TdApi.CanSendMessageToUserResultUserHasPaidMessages) {
        text = Lang.getString(R.string.XgramCheckInPaid, title);
      } else if (result instanceof TdApi.CanSendMessageToUserResultUserRestrictsNewChats) {
        text = Lang.getString(R.string.XgramCheckInRestricted, title);
      } else if (result instanceof TdApi.CanSendMessageToUserResultUserIsDeleted) {
        text = Lang.getString(R.string.XgramCheckInDeletedAccount);
      }
      if (text != null) {
        showCantStart(c, text);
      } else {
        next.run();
      }
    }));
    UI.post(() -> {
      if (done.compareAndSet(false, true) && !c.isDestroyed()) {
        next.run();
      }
    }, 3000);
  }

  private static void showCantStart (ViewController<?> c, String text) {
    if (XgramExpressive.isEnabled()) {
      ModernOptions.xgramShowHeroConfirm(c, R.drawable.baseline_warning_24, Lang.getString(R.string.XgramCheckInCantStart), text, null, null, Lang.getString(R.string.OK), null);
    } else {
      c.showOptions(Lang.getString(R.string.XgramCheckInCantStart) + "\n\n" + text, new int[] {R.id.btn_done}, new String[] {Lang.getString(R.string.OK)}, new int[] {ViewController.OptionColor.NORMAL}, new int[] {R.drawable.baseline_warning_24}, (v, id) -> true);
    }
  }

  /** Once, before the first Check In: what it does and doesn't do. */
  private static void explainer (MessagesController c, String title, Runnable next) {
    XgramSettings settings = XgramSettings.instance();
    if (settings.checkInExplained()) {
      next.run();
      return;
    }
    String text = Lang.getString(R.string.XgramCheckInExplainer, title);
    Runnable onContinue = () -> {
      settings.setCheckInExplained();
      next.run();
    };
    if (XgramExpressive.isEnabled()) {
      ModernOptions.xgramShowHeroConfirm(c, R.drawable.baseline_location_on_48, Lang.getString(R.string.XgramCheckIn), text, Lang.getString(R.string.XgramCheckInNotNow), null, Lang.getString(R.string.Continue), onContinue);
    } else {
      c.showOptions(text, new int[] {R.id.btn_done, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Continue), Lang.getString(R.string.XgramCheckInNotNow)},
        new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_check_circle_24, 0}, (v, id) -> {
          if (id == R.id.btn_done) {
            onContinue.run();
          }
          return true;
        });
    }
  }

  private static void showConflict (MessagesController c, CheckInRecord active) {
    c.showOptions(Lang.getString(R.string.XgramCheckInConflict, active.title),
      new int[] {R.id.btn_xgramCheckInOpenChat, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramCheckInOpenChat), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_chat_bubble_24, R.drawable.baseline_cancel_24}, (v, id) -> {
        if (id == R.id.btn_xgramCheckInOpenChat) {
          openChat(c, active);
        }
        return true;
      });
  }

  /** The Check In's chat: on this account in place, on another one as a notification tap does. */
  private static void openChat (MessagesController c, CheckInRecord r) {
    if (r.acc == c.tdlib().id()) {
      TdlibUi.ChatOpenParameters params = new TdlibUi.ChatOpenParameters();
      if (r.topic != 0) {
        params.messageTopicId = new TdApi.MessageTopicForum(r.topic);
      }
      c.tdlib().ui().openChat(c, r.chat, params);
      return;
    }
    try {
      Context context = c.context();
      Intent intent = new Intent(context, MainActivity.class);
      Intents.secureIntent(intent, true);
      intent.setAction(Intents.ACTION_OPEN_CHAT + ".ci." + r.acc + "." + r.chat);
      intent.putExtra("account_id", r.acc);
      intent.putExtra("chat_id", r.chat);
      intent.putExtra("message_id", r.startReal ? r.startMsgId : 0L);
      intent.putExtra(com.fahadali.xgram.topics.XgramForums.EXTRA_NOTIFICATION_TOPIC_ID, r.topic);
      context.startActivity(intent);
    } catch (Throwable t) {
      XgramCheckIn.log("open chat: %s", t.toString());
    }
  }

  // The start sheet

  private static final class StartState {
    final MessagesController c;
    final Tdlib tdlib;
    final long chatId;
    final int topic;
    final String title;
    long customEta; // server seconds, 0: not picked
    boolean loc, bat, arrive, debug;
    int minutes;
    boolean locationDenied, arrivalDropped;
    boolean hasDest;
    double destLat, destLng;
    String destLabel = "";

    StartState (MessagesController c, long chatId, int topic, String title) {
      this.c = c;
      this.tdlib = c.tdlib();
      this.chatId = chatId;
      this.topic = topic;
      this.title = title;
    }

    long graceSeconds () {
      return debug ? 60 : XgramSettings.instance().checkInGrace() * 60L;
    }
  }

  private static String infoText (StartState s, boolean loc, boolean bat, boolean debug) {
    long grace = debug ? 1 : XgramSettings.instance().checkInGrace();
    StringBuilder b = new StringBuilder(Lang.getString(R.string.XgramCheckInHeaderInfo, s.title, Lang.plural(R.string.xMinutes, grace), CheckInText.contents(loc, bat)));
    if (XgramSettings.instance().ghostOffline()) {
      b.append("\n\n").append(Lang.getString(R.string.XgramCheckInGhostNote));
    }
    if (!NotificationManagerCompat.from(s.c.context()).areNotificationsEnabled()) {
      b.append("\n\n").append(Lang.getString(R.string.XgramCheckInNoNotifications));
    }
    return b.toString();
  }

  private static boolean checked (SettingsAdapter adapter, int id) {
    return adapter.getCheckIntResults().get(id) == id;
  }

  private static void updateInfo (StartState s, SettingsAdapter adapter) {
    int selected = adapter.getCheckIntResults().get(R.id.btn_xgramCheckIn);
    String text = infoText(s, checked(adapter, R.id.btn_xgramCheckInLocOpt), checked(adapter, R.id.btn_xgramCheckInBatOpt), selected == R.id.btn_xgramCheckInDebug2m);
    int index = adapter.indexOfViewById(R.id.btn_xgramCheckInInfo);
    if (index != -1) {
      ListItem item = adapter.getItems().get(index);
      if (item.setStringIfChanged(text)) {
        adapter.updateItem(index);
      }
    }
  }

  private static void showStartSheet (MessagesController c, long chatId, int topic, String title) {
    if (c.isDestroyed()) {
      return;
    }
    final StartState s = new StartState(c, chatId, topic, title);
    XgramSettings settings = XgramSettings.instance();
    int defaultMinutes = settings.checkInTimer();
    List<ListItem> items = new ArrayList<>();
    for (int i = 0; i < XgramSettings.CHECK_IN_TIMERS.length && i < PRESET_IDS.length; i++) {
      int minutes = XgramSettings.CHECK_IN_TIMERS[i];
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, PRESET_IDS[i], 0, durationName(minutes), R.id.btn_xgramCheckIn, minutes == defaultMinutes));
    }
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramCheckInCustom, 0, Lang.getString(R.string.XgramCheckInBackByTime), R.id.btn_xgramCheckIn, false));
    if (BuildConfig.DEBUG) {
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_xgramCheckInDebug2m, 0, Lang.getString(R.string.XgramCheckInDebug2m), R.id.btn_xgramCheckIn, false));
    }
    boolean loc = settings.checkInLocation(), bat = settings.checkInBattery();
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_xgramCheckInLocOpt, 0, Lang.getString(R.string.XgramCheckInIncludeLocation), loc));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_xgramCheckInBatOpt, 0, Lang.getString(R.string.XgramCheckInIncludeBattery), bat));
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_xgramCheckInArriveOpt, 0, Lang.getString(R.string.XgramCheckInArriveOpt), false));

    String headline = Lang.getString(R.string.XgramCheckInWith, title);
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_xgramCheckIn);
    b.xgramSetHeadline(R.id.btn_xgramCheckIn, headline);
    if (!XgramExpressive.isEnabled()) {
      b.addHeaderItem(headline);
    }
    b.addHeaderItem(new ListItem(ListItem.TYPE_INFO, R.id.btn_xgramCheckInInfo, 0, infoText(s, loc, bat, false), false));
    b.setRawItems(items);
    b.setAllowResize(false);
    b.setSaveStr(R.string.XgramCheckInStart);
    b.setCancelStr(R.string.Cancel);
    b.setOnSettingItemClick((view, settingsId, item, doneButton, adapter, window) -> {
      if (item.getId() == R.id.btn_xgramCheckInCustom) {
        pickTime(s, adapter);
      }
      updateInfo(s, adapter);
    });
    b.setOnActionButtonClick((wrap, view, isCancel) -> {
      if (isCancel) {
        return false;
      }
      SettingsAdapter adapter = wrap.adapter;
      int selected = adapter.getCheckIntResults().get(R.id.btn_xgramCheckIn);
      if (selected == R.id.btn_xgramCheckInCustom && s.customEta == 0) {
        pickTime(s, adapter); // the time comes first; the sheet stays open
        return true;
      }
      s.debug = selected == R.id.btn_xgramCheckInDebug2m;
      s.minutes = 0;
      for (int i = 0; i < PRESET_IDS.length && i < XgramSettings.CHECK_IN_TIMERS.length; i++) {
        if (PRESET_IDS[i] == selected) {
          s.minutes = XgramSettings.CHECK_IN_TIMERS[i];
        }
      }
      if (s.minutes == 0 && !s.debug && selected != R.id.btn_xgramCheckInCustom) {
        s.minutes = defaultMinutes;
      }
      s.loc = checked(adapter, R.id.btn_xgramCheckInLocOpt);
      s.bat = checked(adapter, R.id.btn_xgramCheckInBatOpt);
      s.arrive = checked(adapter, R.id.btn_xgramCheckInArriveOpt);
      if (wrap.window != null) {
        wrap.window.hideWindow(true);
      }
      afterStart(s);
      return true;
    });
    c.showSettings(b);
  }

  /** "Back by a time…": the M3 dial with Expressive design, Telegram X's time picker without. */
  private static void pickTime (StartState s, SettingsAdapter adapter) {
    Calendar cal = Calendar.getInstance();
    if (s.customEta != 0) {
      cal.setTimeInMillis((s.customEta - skew(s)) * 1000L);
    } else {
      cal.add(Calendar.HOUR_OF_DAY, 1);
      cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / 5) * 5);
    }
    final int hour = cal.get(Calendar.HOUR_OF_DAY), minute = cal.get(Calendar.MINUTE);
    final String title = Lang.getString(R.string.XgramCheckInPickerTitle);
    if (XgramExpressive.isEnabled() && XgramR5ChatPickers.showTimePicker(s.c.context(), title, hour, minute, !UI.needAmPm(), (h, m) -> onTimePicked(s, adapter, h, m))) {
      return;
    }
    TimePickerDialog dialog = new TimePickerDialog(s.c.context(), Theme.dialogTheme(), (view, h, m) -> onTimePicked(s, adapter, h, m), hour, minute, !UI.needAmPm());
    dialog.setTitle(title);
    ViewSupport.showTimePicker(dialog);
  }

  private static long skew (StartState s) {
    return s.tdlib.currentTime(TimeUnit.SECONDS) - System.currentTimeMillis() / 1000L;
  }

  private static void onTimePicked (StartState s, SettingsAdapter adapter, int hourOfDay, int minute) {
    Calendar target = Calendar.getInstance();
    target.set(Calendar.HOUR_OF_DAY, hourOfDay);
    target.set(Calendar.MINUTE, minute);
    target.set(Calendar.SECOND, 0);
    target.set(Calendar.MILLISECOND, 0);
    if (target.getTimeInMillis() <= System.currentTimeMillis()) {
      target.add(Calendar.DAY_OF_YEAR, 1); // a time at or before now means tomorrow
    }
    long serverNow = s.tdlib.currentTime(TimeUnit.SECONDS);
    long eta = target.getTimeInMillis() / 1000L + skew(s);
    boolean raised = eta < serverNow + CheckInRules.MIN_ETA;
    eta = CheckInRules.clampEta(eta, serverNow, serverNow, false);
    s.customEta = eta;
    int index = adapter.indexOfViewById(R.id.btn_xgramCheckInCustom);
    if (index != -1) {
      ListItem item = adapter.getItems().get(index);
      item.setString(Lang.getString(R.string.XgramCheckInBackBy, Lang.time(eta, TimeUnit.SECONDS)));
      adapter.processToggle(null, item, true);
      adapter.updateItem(index);
    }
    updateInfo(s, adapter);
    if (raised) {
      XgramCheckIn.feedback(Lang.getString(R.string.XgramCheckInMin15));
    }
  }

  // After Start: location, notifications, the connection, then the engine

  private static void afterStart (StartState s) {
    MessagesController c = s.c;
    if (c.isDestroyed()) {
      return;
    }
    if ((s.loc || s.arrive) && !XgramCheckIn.hasLocationPermission(c.context())) {
      askLocation(s, () -> askNotifications(s));
    } else {
      askNotifications(s);
    }
  }

  /**
   * Foreground location only. Telegram X's rationale drops its cancel for foreground requests, so the rationale is
   * shown here (same text and look) and "Not now" goes on without location; the result is always checked again.
   */
  private static void askLocation (StartState s, Runnable next) {
    MessagesController c = s.c;
    Runnable after = () -> {
      if (!XgramCheckIn.hasLocationPermission(c.context())) {
        s.locationDenied = s.loc;
        if (s.arrive) {
          s.arrive = false;
          s.arrivalDropped = true;
        }
      }
      next.run();
    };
    Runnable request = () -> {
      boolean asked = c.context().permissions().requestPermissions((RunnableBool) granted -> after.run(), Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
      if (!asked) {
        after.run();
      }
    };
    if (!c.context().permissions().shouldShowAccessLocationRationale()) {
      request.run();
      return;
    }
    // ModernOptions.showLocationAlert's text: the request, then the disclaimer in italics.
    Lang.SpanCreator italicDisclaimer = (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 1 ? Lang.newItalicSpan(needFakeBold) : null;
    CharSequence text = Lang.getString(R.string.format_doubleLines, italicDisclaimer, Lang.getString(R.string.LocationAlertLocation), Lang.getString(R.string.LocationAlertLocationDisclaimer));
    // The start sheet is gone already: closing the rationale with back goes on without location, as Not now.
    if (XgramExpressive.isEnabled()) {
      PopupLayout hero = new ModernOptions.XgramHero(R.drawable.baseline_location_on_48, Lang.getString(R.string.XgramR5SheetsLocationTitle), text)
        .cancel(Lang.getString(R.string.XgramCheckInNotNow), after)
        .confirm(Lang.getString(R.string.Continue), request)
        .onDismiss(after)
        .show(c);
      if (hero == null) {
        after.run();
      }
    } else {
      // Telegram X's location alert: the big colored header, Continue, and a cancel that counts as not granted. As there,
      // a tap outside doesn't close it.
      final boolean[] handled = {false};
      PopupLayout popup = ModernOptions.xgramAddBigColoredHeader(c.showOptions(text, new int[] {R.id.btn_done, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.Continue), Lang.getString(R.string.XgramCheckInNotNow)},
        new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
        new int[] {R.drawable.baseline_check_circle_24, R.drawable.baseline_cancel_24}, new OptionDelegate() {
          @Override
          public boolean disableCancelOnTouchdown () {
            return true;
          }

          @Override
          public boolean onOptionItemPressed (View optionItemView, int id) {
            if (id == R.id.btn_done) {
              handled[0] = true;
              request.run();
            } else if (id == R.id.btn_cancel) {
              handled[0] = true;
              after.run();
            }
            return true;
          }
        }), R.drawable.baseline_location_on_48);
      if (popup == null) {
        after.run();
      } else {
        popup.setDismissListener(p -> {
          if (!handled[0]) {
            handled[0] = true;
            after.run();
          }
        });
      }
    }
  }

  private static void askNotifications (StartState s) {
    MessagesController c = s.c;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationManagerCompat.from(c.context()).areNotificationsEnabled()) {
      boolean asked = c.context().permissions().requestPostNotifications(granted -> afterPermissions(s));
      if (asked) {
        return;
      }
    }
    afterPermissions(s);
  }

  private static void afterPermissions (StartState s) {
    if (s.arrive) {
      pickDestination(s);
    } else {
      connectAndStart(s);
    }
  }

  /** "End when I arrive at a place…": the location picker in the chat pane; picking sends nothing. */
  private static void pickDestination (StartState s) {
    MessagesController c = s.c;
    if (c.isDestroyed()) {
      return;
    }
    org.thunderdog.challegram.component.attach.MediaLayout layout = new org.thunderdog.challegram.component.attach.MediaLayout(c);
    layout.xgramPickListener = new PickListener() {
      @Override
      public void onPick (TdApi.Location location, @Nullable String title) {
        s.hasDest = true;
        s.destLat = location.latitude;
        s.destLng = location.longitude;
        s.destLabel = title != null ? title.trim() : "";
        UI.post(() -> connectAndStart(s));
      }

      @Override
      public void onCancel () {
        UI.post(() -> askWithoutDestination(s));
      }
    };
    // Set before init, so the location tab's header reads "Use this location" from the start.
    layout.init(org.thunderdog.challegram.component.attach.MediaLayout.MODE_LOCATION, c);
    layout.show();
  }

  private static void askWithoutDestination (StartState s) {
    MessagesController c = s.c;
    if (c.isDestroyed()) {
      return;
    }
    c.showOptions(Lang.getString(R.string.XgramCheckInNoDestination), new int[] {R.id.btn_done, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramCheckInStart), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_check_circle_24, R.drawable.baseline_cancel_24}, (v, id) -> {
        if (id == R.id.btn_done) {
          s.arrive = false;
          s.hasDest = false;
          connectAndStart(s);
        }
        return true;
      });
  }

  @Nullable
  private static Location lastKnownLocation (Context context) {
    if (!XgramCheckIn.hasLocationPermission(context)) {
      return null;
    }
    try {
      LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
      if (lm == null) {
        return null;
      }
      Location best = null;
      for (String provider : lm.getProviders(true)) {
        try {
          Location l = lm.getLastKnownLocation(provider);
          if (l != null && (best == null || l.getTime() > best.getTime())) {
            best = l;
          }
        } catch (SecurityException ignored) { }
      }
      return best;
    } catch (Throwable t) {
      return null;
    }
  }

  private static void connectAndStart (StartState s) {
    MessagesController c = s.c;
    Tdlib tdlib = s.tdlib;
    if (!tdlib.isConnected()) {
      XgramCheckIn.feedback(Lang.getString(R.string.XgramCheckInNeedsConnection));
      return;
    }
    long now = tdlib.currentTime(TimeUnit.SECONDS);
    XgramCheckIn.Request req = new XgramCheckIn.Request();
    req.tdlib = tdlib;
    req.chatId = s.chatId;
    req.topic = s.topic;
    req.title = s.title;
    req.debug = s.debug;
    req.eta = s.debug ? now + CheckInRules.MIN_ETA_DEBUG : s.minutes > 0 ? now + s.minutes * 60L : s.customEta;
    req.grace = s.graceSeconds();
    req.loc = s.loc;
    req.bat = s.bat;
    req.follow = s.loc && XgramSettings.instance().checkInFollowUp();
    if (s.hasDest) {
      req.hasDest = true;
      req.destLat = s.destLat;
      req.destLng = s.destLng;
      req.destRadius = XgramSettings.instance().checkInRadius();
      req.destLabel = s.destLabel;
    }
    Context context = c.context();
    if (s.loc) {
      req.lastKnown = lastKnownLocation(context);
      req.coarse = !XgramCheckIn.hasFineLocation(context);
    }
    if (s.bat) {
      int[] battery = XgramCheckInService.readBattery(context);
      if (battery != null) {
        req.battery = battery[0];
        req.charging = battery[1] != 0;
      }
    }
    StringBuilder text = new StringBuilder(Lang.getString(R.string.XgramCheckInSettingUp));
    if (s.locationDenied) {
      text.append('\n').append(Lang.getString(R.string.XgramCheckInLocationOff));
    }
    if (s.arrivalDropped) {
      text.append('\n').append(Lang.getString(R.string.XgramCheckInArrivalNeedsLocation));
    }
    XgramCheckIn.feedback(text);
    XgramCheckIn.start(req, new XgramCheckIn.StartCallback() {
      @Override
      public void onStarted (long eta) {
        XgramCheckIn.feedback(Lang.getString(R.string.XgramCheckInStarted, Lang.time(eta, TimeUnit.SECONDS)));
      }

      @Override
      public void onFailed (String reason) {
        XgramCheckIn.feedback(Lang.getString(R.string.XgramCheckInDidntStart, TextUtils.isEmpty(reason) ? Lang.getString(R.string.XgramCheckInDidntStartText) : reason));
      }
    });
  }

  // Manage

  private static void showManage (MessagesController c, CheckInRecord r) {
    String alertAt = Lang.time(r.sendAt, TimeUnit.SECONDS);
    StringBuilder info = new StringBuilder();
    int[] ids;
    String[] titles;
    int[] colors, icons;
    switch (r.state) {
      case CheckInRecord.STARTING:
        info.append(Lang.getString(R.string.XgramCheckInManageStarting));
        ids = new int[] {R.id.btn_xgramCheckInCancel, R.id.btn_xgramCheckInClose};
        titles = new String[] {Lang.getString(R.string.XgramCheckInCancel), Lang.getString(R.string.XgramCheckInClose)};
        colors = new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL};
        icons = new int[] {R.drawable.baseline_cancel_24, R.drawable.baseline_close_24};
        break;
      case CheckInRecord.FIRED:
        info.append(Lang.getString(R.string.XgramCheckInFiredInfo, alertAt));
        ids = new int[] {R.id.btn_xgramCheckInSafe, R.id.btn_xgramCheckInClose};
        titles = new String[] {Lang.getString(R.string.XgramCheckInImSafe), Lang.getString(R.string.XgramCheckInClose)};
        colors = new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL};
        icons = new int[] {R.drawable.baseline_check_circle_24, R.drawable.baseline_close_24};
        break;
      case CheckInRecord.CLOSING:
        info.append(Lang.getString(R.string.XgramCheckInClosingInfo, alertAt, r.title));
        ids = new int[] {R.id.btn_xgramCheckInClose};
        titles = new String[] {Lang.getString(R.string.XgramCheckInClose)};
        colors = new int[] {ViewController.OptionColor.NORMAL};
        icons = new int[] {R.drawable.baseline_close_24};
        break;
      case CheckInRecord.ARMED:
      default: {
        info.append(Lang.getString(R.string.XgramCheckInManageArmed, Lang.time(r.eta, TimeUnit.SECONDS), r.title, alertAt, CheckInText.contents(r.loc, r.bat)));
        if (r.hasDest) {
          float d = XgramCheckIn.distanceToDest;
          info.append('\n').append(d >= 0 ?
            Lang.getString(R.string.XgramCheckInManageDest, CheckInText.placeLabel(r), Lang.shortDistance(d)) :
            Lang.getString(R.string.XgramCheckInManageDestNoDistance, CheckInText.placeLabel(r)));
        }
        if ((r.loc || r.hasDest) && XgramCheckIn.locationPaused) {
          info.append('\n').append(Lang.getString(R.string.XgramCheckInManagePaused));
        }
        ids = new int[] {R.id.btn_xgramCheckInSafe, R.id.btn_xgramCheckInAdd15, R.id.btn_xgramCheckInAdd60, R.id.btn_xgramCheckInCancel, R.id.btn_xgramCheckInClose};
        titles = new String[] {
          Lang.getString(R.string.XgramCheckInImSafe), Lang.getString(R.string.XgramCheckInAdd15), Lang.getString(R.string.XgramCheckInAdd60),
          Lang.getString(R.string.XgramCheckInCancel), Lang.getString(R.string.XgramCheckInClose)
        };
        colors = new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL};
        icons = new int[] {R.drawable.baseline_check_circle_24, R.drawable.baseline_schedule_24, R.drawable.baseline_access_time_24, R.drawable.baseline_cancel_24, R.drawable.baseline_close_24};
        break;
      }
    }
    final int acc = r.acc;
    final String ref = r.ref;
    final String title = r.title;
    c.showOptions(info.toString(), ids, titles, colors, icons, (v, id) -> {
      if (id == R.id.btn_xgramCheckInSafe) {
        XgramCheckIn.imSafe(acc, ref, CheckInRecord.KIND_SAFE);
      } else if (id == R.id.btn_xgramCheckInAdd15 || id == R.id.btn_xgramCheckInAdd60) {
        XgramCheckIn.addTime(acc, ref, id == R.id.btn_xgramCheckInAdd15 ? 15 * 60 : 60 * 60, (ok, message) -> {
          if (!TextUtils.isEmpty(message)) {
            XgramCheckIn.feedback(message);
          }
        });
      } else if (id == R.id.btn_xgramCheckInCancel) {
        UI.post(() -> confirmCancel(c, acc, ref, title));
      }
      return true;
    });
  }

  private static void confirmCancel (MessagesController c, int acc, String ref, String title) {
    if (c.isDestroyed()) {
      return;
    }
    String headline = Lang.getString(R.string.XgramCheckInCancelTitle);
    String supporting = Lang.getString(R.string.XgramCheckInCancelText, title);
    c.xgramShowConfirm(headline + " " + supporting, headline, supporting,
      new int[] {R.id.btn_xgramCheckInCancel, R.id.btn_xgramCheckInKeep},
      new String[] {Lang.getString(R.string.XgramCheckInCancel), Lang.getString(R.string.XgramCheckInKeep)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_cancel_24, R.drawable.baseline_close_24}, (v, id) -> {
        if (id == R.id.btn_xgramCheckInCancel) {
          XgramCheckIn.imSafe(acc, ref, CheckInRecord.KIND_CANCELLED);
        }
        return true;
      });
  }
}
