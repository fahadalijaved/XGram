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
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.telegram;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.voip.VoIP;
import org.thunderdog.challegram.voip.VoIPServerConfig;
import org.thunderdog.challegram.voip.gui.CallSettings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.Queue;

import me.vkryl.android.SdkVersion;
import me.vkryl.core.reference.ReferenceList;

public class CallManager implements GlobalCallListener {
  public interface CurrentCallListener {
    void onCurrentCallChanged (Tdlib tdlib, @Nullable TdApi.Call call);
  }

  // Impl

  private final ReferenceList<CurrentCallListener> listeners = new ReferenceList<>();

  CallManager (TdlibManager context) {
    context.global().addCallListener(this);
    try {
      com.fahadali.xgram.call.XgramCallVideo.installLifecycle(); // XGram: the camera runs only while an activity is started
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "XGram video: lifecycle", t);
    }
  }

  private Tdlib currentCallTdlib;
  private @Nullable TdApi.Call currentCall;
  private boolean currentCallAcknowledged;
  // XGram: the video of the current call (null on devices without video, see XgramCallVideo.isAvailable)
  private @Nullable com.fahadali.xgram.call.XgramCallVideo currentVideo;

  /** XGram: the video of the current call, or null. */
  public @Nullable com.fahadali.xgram.call.XgramCallVideo video () {
    return currentVideo != null && !currentVideo.isReleased() ? currentVideo : null;
  }

  /** XGram: the video of the call {@code callId} while it is the current one, or null. */
  public @Nullable com.fahadali.xgram.call.XgramCallVideo video (int callId) {
    com.fahadali.xgram.call.XgramCallVideo video = video();
    return video != null && video.callId() == callId ? video : null;
  }

  private void xgramStartVideo (Tdlib tdlib, TdApi.Call call) {
    xgramStopVideo();
    if (!com.fahadali.xgram.call.XgramCallVideo.isAvailable()) {
      return;
    }
    com.fahadali.xgram.call.XgramVideoBackend backend;
    try {
      backend = tgx.flavor.Flavor.createVideoBackend();
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "XGram video: no backend", t);
      backend = null;
    }
    if (backend != null) {
      currentVideo = new com.fahadali.xgram.call.XgramCallVideo(tdlib, call, backend);
      if (call.isOutgoing && call.isVideo) {
        currentVideo.setWantsVideo(true);
      }
    }
  }

  private void xgramStopVideo () {
    if (currentVideo != null) {
      currentVideo.release();
      currentVideo = null;
    }
  }

  public void addCurrentCallListener (CurrentCallListener listener) {
    listeners.add(listener);
  }

  public void removeCurrentCallListener (CurrentCallListener listener) {
    listeners.remove(listener);
  }

  private CancellationSignal serviceCancellationSignal;

  private void setCurrentCall (final Tdlib tdlib, @Nullable final TdApi.Call call) {
    if (currentCall == null && call == null) {
      return;
    }
    if (!UI.inUiThread()) { // XGram: the video lives on the main thread
      UI.post(() -> setCurrentCall(tdlib, call));
      return;
    }
    if (currentCall == null || call == null) {
      // XGram: the call's video comes before its screen (and the camera goes the moment it ends)
      if (call != null) {
        xgramStartVideo(tdlib, call);
      } else {
        xgramStopVideo();
      }
      this.currentCallTdlib = tdlib;
      this.currentCall = call;
      this.currentCallAcknowledged = call == null || UI.getUiState() != UI.State.RESUMED || UI.isNavigationBusyWithSomething();
      if (currentCallAcknowledged) {
        notifyCallListeners();
      }
      if (serviceCancellationSignal != null) {
        serviceCancellationSignal.cancel();
        serviceCancellationSignal = null;
      }
      if (call != null) {
        Intent intent = new Intent(AppContext.get(), TGCallService.class);
        intent.putExtra("account_id", tdlib.id());
        intent.putExtra("call_id", call.id);
        serviceCancellationSignal = new CancellationSignal();
        UI.startService(intent, UI.getUiState() != UI.State.RESUMED, true, serviceCancellationSignal);

        navigateToCallController(currentCallTdlib, currentCall);
      }
    } else if (currentCallTdlib.id() != tdlib.id() || currentCall.id != call.id) {
      discardCall(tdlib, call.id, call.isVideo);
    } else {
      currentCallTdlib = tdlib;
      currentCall = call;
      if (currentVideo != null) { // XGram
        currentVideo.onCallUpdated(call);
      }
    }
  }

  private long getCallDurationImpl (Tdlib tdlib, int callId) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null && service.compareCall(tdlib, callId)) {
      return service.getCallDuration();
    }
    return -1;
  }

  public int getCallBarsCount (Tdlib tdlib, int callId) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null && service.compareCall(tdlib, callId)) {
      return service.getCallBarsCount();
    }
    return -1;
  }

  public int getCallDuration (Tdlib tdlib, int callId) {
    long duration = getCallDurationImpl(tdlib, callId);
    if (duration != -1) {
      return (int) (duration / 1000l);
    }
    return -1;
  }

  public long getTimeTillNextCallDurationUpdate (Tdlib tdlib, int callId) {
    long duration = getCallDurationImpl(tdlib, callId);
    if (duration != -1) {
      return 1000l - duration % 1000l;
    }
    return 1000l;
  }

  private void notifyCallListeners () {
    for (CurrentCallListener listener : listeners) {
      listener.onCurrentCallChanged(currentCallTdlib, currentCall);
    }
  }

  /** XGram: the current call changed in a way its chip shows (video was turned on). */
  public void notifyCurrentCallChanged () {
    if (currentCall != null && currentCallAcknowledged) {
      notifyCallListeners();
    }
  }

  public void openCurrentCall () {
    TdApi.Call call = getCurrentCall();
    if (call != null) {
      navigateToCallController(currentCallTdlib, call);
    }
  }

  public int getCurrentCallId () {
    return currentCall != null && !TD.isFinished(currentCall) ? currentCall.id : 0;
  }

  public Tdlib getCurrentCallTdlib () {
    return currentCall != null && !TD.isFinished(currentCall) ? currentCallTdlib : null;
  }

  public int getCurrentCallAccountId () {
    return currentCall != null && !TD.isFinished(currentCall) ? currentCallTdlib.id() : TdlibAccount.NO_ID;
  }

  public TdApi.Call getCurrentCall () {
    return currentCallAcknowledged && !TD.isFinished(currentCall) ? currentCall : null;
  }

  public void acknowledgeCurrentCall (int callId) {
    if (!currentCallAcknowledged && currentCall != null && currentCall.id == callId) {
      currentCallAcknowledged = true;
      notifyCallListeners();
    }
  }

  private final Queue<Runnable> awaitingCurrentCallCompletion = new ArrayDeque<>();

  @Override
  public void onCallUpdated (final Tdlib tdlib, final TdApi.Call call) {
    if (currentCall != null && TD.isActive(currentCall)) {
      if (currentCallTdlib.id() != tdlib.id()) {
        return;
      }
      if (currentCall.id != call.id) {
        if (!call.isOutgoing) {
          if (call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
            tdlib.send(new TdApi.DiscardCall(call.id, false, null, 0, call.isVideo, 0), tdlib.typedOkHandler());
          }
        }
        return;
      }
    }
    if (Log.isEnabled(Log.TAG_VOIP)) {
      Log.v(Log.TAG_VOIP, "#%d: updateCall, userId:%s isOutgoing:%b state:%s", call.id, call.userId, call.isOutgoing, call.state);
    }
    if (call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR) {
      VoIPServerConfig.setConfig(((TdApi.CallStateReady) call.state).config);
    }
    if (TD.isFinished(call)) {
      if (currentVideo != null && currentVideo.callId() == call.id) { // XGram: the camera closes the moment the call ends
        currentVideo.onCallUpdated(call);
      }
      setCurrentCall(null, null);

      Runnable act;
      while ((act = awaitingCurrentCallCompletion.poll()) != null) {
        act.run();
      }
    } else {
      setCurrentCall(tdlib, call);
    }
  }

  @Override
  public void onCallSettingsChanged (Tdlib tdlib, int callId, CallSettings settings) { }

  private boolean navigateToCallController (Tdlib tdlib, TdApi.Call call) {
    BaseActivity activity = UI.getUiContext();
    if (activity != null && activity.getActivityState() == UI.State.RESUMED) {
      NavigationController navigation = UI.getNavigation();
      if (navigation != null) {
        ViewController<?> c = !navigation.isAnimating() ? navigation.getCurrentStackItem() : null;
        if (c instanceof CallController && c.tdlib() == tdlib && ((CallController) c).compareUserId(call.userId)) {
          ((CallController) c).replaceCall(call);
          return true;
        }

        if (activity.isNavigationBusy()) {
          return true;
        }

        CallController controller = new CallController(activity, tdlib);
        controller.setArguments(new CallController.Arguments(call));
        navigation.navigateTo(controller);
        return true;
      }
    }
    // TODO start activity
    return false;
  }

  private static void discardCall (Tdlib tdlib, final int callId, final boolean isVideo) {
    Log.v(Log.TAG_VOIP, "#%d: DiscardCall requested, isVideo:%b", callId, isVideo);
    tdlib.client().send(new TdApi.DiscardCall(callId, false, null, 0, isVideo, 0), object -> Log.v(Log.TAG_VOIP, "#%d: DiscardCall completed: %s", callId, object));
  }

  /*
createCall user_id:int protocol:callProtocol = CallId;
acceptCall id:long protocol:callProtocol = Ok;
discardCall id:long reason:CallDiscardReason duration:int connection_id:long = Ok;
rateCall id:long rating:int comment:string = Ok;
debugCall id:long debug:string = Ok;
*/

  private static final boolean CHECK_CONNECTION = true;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    AlertType.MICROPHONE_HARDWARE_MISSING,
    AlertType.MICROPHONE_PERMISSION_MISSING,
    AlertType.ANDROID_VERSION_UNSUPPORTED,
    AlertType.CAMERA_PERMISSION_MISSING
  })
  private @interface AlertType {
    int MICROPHONE_HARDWARE_MISSING = 1,
        MICROPHONE_PERMISSION_MISSING = 2,
        ANDROID_VERSION_UNSUPPORTED = 3,
        CAMERA_PERMISSION_MISSING = 4; // XGram: video calls
  }

  private void showAlert (@AlertType int alertType) {
    final Context context = UI.getContext();
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() && xgramShowAlert(context, alertType)) {
      return; // XGram M3E r6: hero sheets ("Allow microphone access" with [Not now] [Open settings])
    }
    AlertDialog.Builder b;
    b = new AlertDialog.Builder(context, Theme.dialogTheme());
    switch (alertType) {
      case AlertType.ANDROID_VERSION_UNSUPPORTED -> {
        b.setTitle(Lang.getString(R.string.AndroidVersionWarningTitle));
        b.setMessage(Lang.getString(R.string.AndroidVersionWarning, SdkVersion.getPrettyName(Build.VERSION_CODES.LOLLIPOP), SdkVersion.getPrettyVersionCode(Build.VERSION_CODES.LOLLIPOP)));
      }
      case AlertType.MICROPHONE_HARDWARE_MISSING -> {
        b.setTitle(Lang.getString(R.string.MicrophonePermission));
        b.setMessage(Lang.getString(R.string.MicrophoneMissing));
      }
      case AlertType.MICROPHONE_PERMISSION_MISSING -> {
        b.setTitle(Lang.getString(R.string.MicrophonePermission));
        b.setMessage(Lang.getString(R.string.MicrophonePermissionDesc));
      }
      case AlertType.CAMERA_PERMISSION_MISSING -> {
        b.setTitle(Lang.getString(R.string.XgramVideoAllowCamera));
        b.setMessage(Lang.getString(R.string.XgramVideoAllowCameraText));
      }
      default ->
        throw new UnsupportedOperationException(Integer.toString(alertType));
    }
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    b.setNeutralButton(Lang.getString(R.string.Settings), (dialog, which) -> {
      Intent intent = new Intent();
      intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      Uri uri = Uri.fromParts("package", context.getPackageName(), null);
      intent.setData(uri);
      context.startActivity(intent);
    });
    com.fahadali.xgram.appearance.XgramR5Calls.showAlert(context, b); // XGram: BaseActivity.showAlert with Expressive design
  }

  /** XGram M3E r6: the microphone alerts as hero sheets from the page on screen; false for the others (or no page). */
  private static boolean xgramShowAlert (Context context, @AlertType int alertType) {
    final ViewController<?> host = com.fahadali.xgram.appearance.XgramR6B1Pickers.host(context);
    switch (alertType) {
      case AlertType.MICROPHONE_PERMISSION_MISSING:
        return com.fahadali.xgram.appearance.XgramR6B1Prompts.showCallMicrophone(host);
      case AlertType.MICROPHONE_HARDWARE_MISSING:
        return com.fahadali.xgram.appearance.XgramR6B1Prompts.showNoMicrophone(host);
      case AlertType.CAMERA_PERMISSION_MISSING:
        // XGram: "Allow camera access" with [Not now] [Open settings]
        return com.fahadali.xgram.appearance.XgramR6B1Prompts.showSettingsPrompt(host, R.drawable.baseline_videocam_24,
          Lang.getString(R.string.XgramVideoAllowCamera), Lang.getString(R.string.XgramVideoAllowCameraText), Intents::openPermissionSettings);
      default:
        return false;
    }
  }

  public void acceptIncomingCall (Tdlib tdlib, int callId) {
    acceptCall(AppContext.get(), tdlib, callId);
  }

  public void declineIncomingCall (Tdlib tdlib, int callId, boolean isVideo) {
    discardCall(tdlib, callId, isVideo);
  }

  // XGram: the permissions of a call (the microphone, and the camera for video), asked for together

  public interface CallPermissionsResult {
    void onResult (boolean mic, boolean camera, boolean cameraPermanentlyDenied);
  }

  private static boolean isGranted (String permission) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AppContext.get().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  /** The microphone, plus the camera when {@code wantCamera}; {@code after} runs at once when nothing is missing. */
  public void checkCallPermissions (@Nullable BaseActivity activity, boolean wantCamera, CallPermissionsResult after) {
    final boolean mic = isGranted(Manifest.permission.RECORD_AUDIO);
    final boolean camera = isGranted(Manifest.permission.CAMERA);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (mic && (!wantCamera || camera)) || activity == null) {
      after.onResult(mic, camera, false);
      return;
    }
    final String[] missing;
    if (!mic && wantCamera && !camera) {
      missing = new String[] {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
    } else if (!mic) {
      missing = new String[] {Manifest.permission.RECORD_AUDIO};
    } else {
      missing = new String[] {Manifest.permission.CAMERA};
    }
    activity.requestCustomPermissions(missing, (code, permissions, grantResults, grantCount) -> {
      final boolean micNow = isGranted(Manifest.permission.RECORD_AUDIO);
      final boolean cameraNow = isGranted(Manifest.permission.CAMERA);
      final boolean permanent = wantCamera && !cameraNow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
      after.onResult(micNow, cameraNow, permanent);
    });
  }

  /** XGram: the camera alert (a hero sheet with Expressive design, Telegram X's dialog otherwise). */
  public void showCameraPermissionAlert () {
    showAlert(AlertType.CAMERA_PERMISSION_MISSING);
  }

  /** XGram: the microphone alert of a call. */
  public void showMicrophonePermissionAlert () {
    showAlert(AlertType.MICROPHONE_PERMISSION_MISSING);
  }

  /** XGram: a short notice: a snackbar with Expressive design (with an optional action), a toast otherwise. */
  public static void xgramNotice (CharSequence text, @Nullable CharSequence actionText, @Nullable Runnable action) {
    if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() &&
      com.fahadali.xgram.appearance.XgramSnackbar.show(text, Toast.LENGTH_LONG, actionText, action)) {
      return;
    }
    UI.showToast(text, Toast.LENGTH_LONG);
  }

  /** XGram: a notice about the camera, with "Settings" when it was denied for good. */
  public static void xgramCameraNotice (CharSequence text, boolean permanentlyDenied) {
    if (permanentlyDenied) {
      xgramNotice(text, Lang.getString(R.string.Settings), Intents::openPermissionSettings);
    } else {
      xgramNotice(text, null, null);
    }
  }

  public boolean checkRecordPermissions (final Context context, final Tdlib tdlib, final @Nullable TdApi.Call call, final long userId, final @Nullable ViewController<?> makeCallContext) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (AppContext.get().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        BaseActivity activity = UI.getUiContext();
        if (activity != null) {
          activity.requestMicPermissionForCall((code, permissions, grantResults, grantCount) -> {
            if (grantCount == permissions.length) {
              if (makeCallContext != null) {
                makeCall(makeCallContext, userId, null, false);
              } else {
                if (call != null) {
                  TdApi.Call updatedCall = tdlib.cache().getCall(call.id);
                  if (updatedCall != null && updatedCall.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
                    acceptCall(context, tdlib, call.id);
                  }
                }
              }
            } else {
              showAlert(AlertType.MICROPHONE_PERMISSION_MISSING);
            }
          });
        } else {
          // context.startActivity(new Intent(context, VoIPPermissionActivity.class));
        }
        return false;
      }
    }
    return true;
  }

  public void makeCallDelayed (final ViewController<?> context, final long userId, @Nullable final TdApi.UserFullInfo userFull, final boolean needPrompt) {
    makeCallDelayed(context, userId, userFull, needPrompt, false);
  }

  public void makeCallDelayed (final ViewController<?> context, final long userId, @Nullable final TdApi.UserFullInfo userFull, final boolean needPrompt, final boolean isVideo) {
    UI.post(() -> makeCall(context, userId, userFull, needPrompt, isVideo), 180l);
  }

  public boolean hasActiveCall () {
    return getCurrentCall() != null;
  }

  public boolean promptActiveCall () {
    if (hasActiveCall()) {
      Context context = UI.getUiContext();
      if (context != null) {
        AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
        b.setMessage(Lang.getString(R.string.SomeCallInProgressDesc));
        b.setNeutralButton(Lang.getString(R.string.HangUp), (dialog, which) -> hangUpCurrentCall());
        b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
        com.fahadali.xgram.appearance.XgramR5Calls.showAlert(context, b); // XGram: BaseActivity.showAlert with Expressive design
      }
      return true;
    }
    return false;
  }

  public void makeCall (final ViewController<?> context,final long userId, @Nullable TdApi.UserFullInfo userFull) {
    makeCall(context, userId, userFull, Settings.instance().needOutboundCallsPrompt());
  }

  /** XGram: a video call when {@code isVideo} (a voice call when the other side cannot take video). */
  public void makeCall (final ViewController<?> context, final long userId, @Nullable TdApi.UserFullInfo userFull, final boolean needPrompt, final boolean isVideo) {
    xgramMakeCall(context, userId, userFull, needPrompt, isVideo);
  }

  /**
   * XGram: a video call when {@code isVideo} (a voice call otherwise), with the outbound calls prompt as set. Not an
   * overload of makeCall: makeCall (context, userId, userFull, needPrompt) has the same signature.
   */
  public void makeVideoCall (final ViewController<?> context, final long userId, @Nullable TdApi.UserFullInfo userFull, final boolean isVideo) {
    xgramMakeCall(context, userId, userFull, Settings.instance().needOutboundCallsPrompt(), isVideo);
  }

  public void makeCall (final ViewController<?> context, final long userId, @Nullable TdApi.UserFullInfo userFull, final boolean needPrompt) {
    xgramMakeCall(context, userId, userFull, needPrompt, false);
  }

  private void xgramMakeCall (final ViewController<?> context, final long userId, @Nullable TdApi.UserFullInfo userFull, final boolean needPrompt, final boolean isVideoRequested) {
    if (userId == 0) {
      return;
    }
    if (Looper.myLooper() != Looper.getMainLooper()) {
      final TdApi.UserFullInfo userFullFinal = userFull;
      UI.post(() -> xgramMakeCall(context, userId, userFullFinal, needPrompt, isVideoRequested));
      return;
    }
    boolean isVideo = isVideoRequested;
    if (userFull == null) {
      userFull = context.tdlib().cache().userFull(userId);
    }
    if (!BuildConfig.CALLS_AVAILABLE) {
      showAlert(AlertType.ANDROID_VERSION_UNSUPPORTED);
      return;
    }
    if (!U.deviceHasMicrophone(AppContext.get())) {
      showAlert(AlertType.MICROPHONE_HARDWARE_MISSING);
      return;
    }
    /*final TdApi.Call pendingCall = context.tdlib().cache().getPendingCall();
    if (pendingCall != null && pendingCall.userId == userId) {
      navigateToCallController(context.tdlib().cache().getPendingCallTdlib(), pendingCall);
      return;
    }*/
    final TdApi.Call pendingCall = getCurrentCall();
    final Tdlib pendingCallTdlib = pendingCall != null ? currentCallTdlib : null;
    if (pendingCall != null || (userFull != null && !userFull.canBeCalled)|| (CHECK_CONNECTION && !context.tdlib().isConnected())) {
      if (pendingCall == null && !(userFull != null && !userFull.canBeCalled) && com.fahadali.xgram.appearance.XgramExpressive.isEnabled() &&
        com.fahadali.xgram.appearance.XgramR6B1Prompts.showCallOffline(context, U.isAirplaneModeOn())) {
        return; // XGram M3E r6: offline / airplane mode as a hero sheet
      }
      AlertDialog.Builder b = new AlertDialog.Builder(context.context(), Theme.dialogTheme());
      b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
      if (pendingCall != null) {
        if (pendingCall.userId == userId) {
          openCurrentCall();
          return;
        }
        b.setTitle(Lang.getString(R.string.VoipOngoingAlertTitle));
        TdApi.User pendingUser = pendingCallTdlib.cache().user(pendingCall.userId);
        TdApi.User currentUser = context.tdlib().cache().user(userId);
        final TdApi.UserFullInfo userFullFinal = userFull;
        b.setPositiveButton(Lang.getString(R.string.HangUp), (dialog, which) -> {
          final boolean[] signal = new boolean[1];
          hangUp(pendingCallTdlib, pendingCall.id, () -> {
            if (!signal[0]) {
              signal[0] = true;
              makeCall(context, userId, userFullFinal, false, isVideoRequested);
            }
          });
          UI.post(() -> {
            if (!signal[0]) {
              signal[0] = true;
              // XGram M3E r6: "Couldn't start the call" snackbar with "Try again" instead of the toast
              if (!com.fahadali.xgram.appearance.XgramR6B1Prompts.showCallFailed(() -> makeCall(context, userId, userFullFinal, false, isVideoRequested))) {
                UI.showToast(R.string.VoipFailed, Toast.LENGTH_SHORT);
              }
            }
          }, 1500);
        });
        b.setMessage(Lang.getStringBold(R.string.CallInProgressDesc, TD.getUserName(pendingUser), TD.getFirstName(currentUser)));
        b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
        b.setNeutralButton(Lang.getString(R.string.ShowCall), (dialog, which) -> {
          TdApi.Call newCurrentCall = getCurrentCall();
          if (newCurrentCall != null && (newCurrentCall == pendingCall || (newCurrentCall.id == pendingCall.id && !TD.isFinished(newCurrentCall)))) {
            navigateToCallController(pendingCallTdlib, pendingCall);
          }
        });
      } else if (userFull != null && !userFull.canBeCalled) {
        b.setTitle(Lang.getString(R.string.AppName));
        b.setMessage(Lang.getStringBold(R.string.NoRightToCall, context.tdlib().cache().userName(userId)));
        b.setNeutralButton(Lang.getString(R.string.OpenChat), (dialog, which) -> context.tdlib().ui().openPrivateChat(context, userId, null));
      } else if (U.isAirplaneModeOn()) {
        b.setTitle(Lang.getString(R.string.VoipOfflineAirplaneTitle));
        b.setMessage(Lang.getString(R.string.VoipOfflineAirplane));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          b.setNeutralButton(Lang.getString(R.string.Settings), (dialog, which) -> {
            Intents.openAirplaneSettings();
          });
        } else {
          b.setNeutralButton(Lang.getString(R.string.AirplaneModeDisable), (dialog, which) -> android.provider.Settings.System.putInt(context.context().getContentResolver(), android.provider.Settings.System.AIRPLANE_MODE_ON, 0));
        }
      } else {
        b.setTitle(Lang.getString(R.string.VoipOfflineTitle));
        b.setMessage(Lang.getString(R.string.VoipOffline));
      }
      com.fahadali.xgram.appearance.XgramR5Calls.showAlert(context.context(), b); // XGram: BaseActivity.showAlert with Expressive design
      return;
    }
    if (userFull == null) {
      context.tdlib().send(new TdApi.GetUserFullInfo(userId), (remoteUserFull, error) -> {
        if (error != null) {
          UI.showError(error);
        } else {
          xgramMakeCall(context, userId, remoteUserFull, needPrompt, isVideoRequested);
        }
      });
      return;
    }
    final boolean canVideo = com.fahadali.xgram.call.XgramCallVideo.isAvailable() && com.fahadali.xgram.call.XgramCallVideo.canVideoCall(userFull);
    if (needPrompt) {
      final TdApi.UserFullInfo userFullFinal = userFull;
      final int[] ids, icons;
      final String[] titles;
      if (canVideo) {
        // XGram: both kinds, the requested one first
        final int[] callItem = {R.id.btn_phone_call, R.string.Call, R.drawable.baseline_call_24};
        final int[] videoItem = {R.id.btn_videoCall, R.string.XgramVideoCall, R.drawable.baseline_videocam_24};
        final int[] first = isVideo ? videoItem : callItem, second = isVideo ? callItem : videoItem;
        ids = new int[] {first[0], second[0], R.id.btn_cancel};
        titles = new String[] {Lang.getString(first[1]), Lang.getString(second[1]), Lang.getString(R.string.Cancel)};
        icons = new int[] {first[2], second[2], R.drawable.baseline_cancel_24};
      } else {
        ids = new int[] {R.id.btn_phone_call, R.id.btn_cancel};
        titles = new String[] {Lang.getString(R.string.Call), Lang.getString(R.string.Cancel)};
        icons = new int[] {R.drawable.baseline_call_24, R.drawable.baseline_cancel_24};
      }
      context.showOptions(Lang.getStringBold(R.string.CallX, context.tdlib().cache().userName(userId)), ids, titles, null, icons, (itemView, id) -> {
        if (id == R.id.btn_phone_call) {
          makeCallDelayed(context, userId, userFullFinal, false, false);
        } else if (id == R.id.btn_videoCall) {
          makeCallDelayed(context, userId, userFullFinal, false, true);
        }
        return true;
      });
      // UI.getCurrentStackItem(context);
      return;
    }
    if (isVideo && !canVideo) {
      // XGram: the other side cannot take video: a voice call, and a word about it
      isVideo = false;
      if (com.fahadali.xgram.call.XgramCallVideo.isAvailable()) {
        xgramNotice(Lang.getStringBold(R.string.XgramVideoUnsupportedCalling, context.tdlib().cache().userName(userId)), null, null);
      }
    }
    // XGram: the microphone (and the camera for video) together; the call goes on once they are granted (before, a
    // grant for an outgoing call did nothing)
    final boolean wantVideo = isVideo;
    checkCallPermissions(context.context(), wantVideo, (mic, camera, cameraPermanentlyDenied) -> {
      if (!mic) {
        showAlert(AlertType.MICROPHONE_PERMISSION_MISSING);
        return;
      }
      final boolean video = wantVideo && camera;
      if (wantVideo && !camera) {
        xgramCameraNotice(Lang.getString(R.string.XgramVideoCameraDenied), cameraPermanentlyDenied);
      }
      context.context().closeAllMedia(false);
      context.tdlib().send(new TdApi.CreateCall(userId, VoIP.getProtocol(), video), (callId, error) -> {
        if (error != null) {
          Log.e(Log.TAG_VOIP, "Failed to create call: %s", TD.toErrorString(error));
          UI.showError(error);
        } else {
          Log.v(Log.TAG_VOIP, "#%d: call created, user_id:%d, video:%b", callId.id, userId, video);
        }
      });
    });
  }

  private boolean checkConnection (final Context context, final Tdlib tdlib) {
    AlertDialog.Builder b = null;
    if (CHECK_CONNECTION && !tdlib.isConnected()) {
      if (com.fahadali.xgram.appearance.XgramExpressive.isEnabled() &&
        com.fahadali.xgram.appearance.XgramR6B1Prompts.showCallOffline(com.fahadali.xgram.appearance.XgramR6B1Pickers.host(context), U.isAirplaneModeOn())) {
        return false; // XGram M3E r6: offline / airplane mode as a hero sheet
      }
      if (U.isAirplaneModeOn()) {
        b = new AlertDialog.Builder(context, Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.VoipOfflineAirplaneTitle));
        b.setMessage(Lang.getString(R.string.VoipOfflineAirplane));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          b.setNeutralButton(Lang.getString(R.string.Settings), (dialog, which) -> {
            Intent i = new Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
          });
        } else {
          b.setNeutralButton(Lang.getString(R.string.AirplaneModeDisable), (dialog, which) -> android.provider.Settings.System.putInt(context.getContentResolver(), android.provider.Settings.System.AIRPLANE_MODE_ON, 0));
        }
      } else {
        b = new AlertDialog.Builder(context, Theme.dialogTheme());
        b.setTitle(Lang.getString(R.string.VoipOfflineTitle));
        b.setMessage(Lang.getString(R.string.VoipOffline));
      }
    }
    if (b != null) {
      try { com.fahadali.xgram.appearance.XgramR5Calls.showAlert(context, b); } catch (Throwable ignored) { } // XGram: BaseActivity.showAlert with Expressive design
      return false;
    }
    return true;
  }

  public void acceptCall (Context context, Tdlib tdlib, final int callId) {
    if (checkConnection(context, tdlib)) {
      if (!checkRecordPermissions(context, tdlib, tdlib.cache().getCall(callId), 0, null)) {
        return;
      }
      Log.v(Log.TAG_VOIP, "#%d: AcceptCall requested", callId);
      tdlib.client().send(new TdApi.AcceptCall(callId, VoIP.getProtocol()), object -> Log.v(Log.TAG_VOIP, "#%d: AcceptCall completed: %s", callId, object));
    }
  }

  public void hangUpCurrentCall () {
    int currentCall = getCurrentCallId();
    if (currentCall != 0) {
      hangUp(currentCallTdlib, currentCall);
    }
  }

  public void hangUp (final Tdlib tdlib, final int callId) {
    hangUp(tdlib, callId, null);
  }

  public void hangUp (final Tdlib tdlib, final int callId, final @Nullable Runnable after) {
    TGCallService service = TGCallService.currentInstance();
    hangUp(tdlib, callId, false, service != null ? service.getConnectionId() : 0, after);
  }

  public void hangUp (final Tdlib tdlib, final int callId, final boolean isDisconnect, final long connectionId) {
    hangUp(tdlib, callId, isDisconnect, connectionId, null);
  }

  public void hangUp (final Tdlib tdlib, final int callId, final boolean isDisconnect, final long connectionId, final @Nullable Runnable after) {
    TdApi.Call call = tdlib.cache().getCall(callId);
    if (call == null) {
      return;
    }
    if (after != null) {
      if (TD.isFinished(call)) {
        after.run();
      } else {
        awaitingCurrentCallCompletion.offer(after);
      }
    }
    int duration = getCallDuration(tdlib, callId);
    // XGram: a video call (or one that used video) is discarded as one
    final com.fahadali.xgram.call.XgramCallVideo video = video(callId);
    final boolean isVideo = call.isVideo || (video != null && video.wasVideoUsed());
    Log.v(Log.TAG_VOIP, "#%d: DiscardCall, isDisconnect: %b, connectionId: %d, duration: %d, video: %b", callId, isDisconnect, connectionId, duration, isVideo);
    tdlib.client().send(new TdApi.DiscardCall(callId, isDisconnect, null, Math.max(0, duration), isVideo, connectionId), object -> {
      Log.v(Log.TAG_VOIP, "#%d: DiscardCall completed: %s", callId, object);
    });
  }
}
