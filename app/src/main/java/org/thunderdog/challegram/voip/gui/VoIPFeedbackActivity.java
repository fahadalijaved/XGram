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
 * File created on 10/06/2017
 */
package org.thunderdog.challegram.voip.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;

public class VoIPFeedbackActivity extends Activity {
  @Override
  protected void onCreate (Bundle savedInstanceState) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    super.onCreate(savedInstanceState);

    overridePendingTransition(0, 0);

    View bgView = new View(this);
    setContentView(bgView);

    // XGram M3E: an M3 dialog: "Rate this call" (headlineSmall, styled by XgramM3Controls), the prompt in bodyMedium
    // onSurfaceVariant, 48dp star toggles and the filled comment field, 24dp from the dialog's sides
    final boolean xgram = com.fahadali.xgram.appearance.XgramExpressive.isEnabled();

    LinearLayout ll = new LinearLayout(this);
    ll.setOrientation(LinearLayout.VERTICAL);
    ll.setGravity(Gravity.CENTER_HORIZONTAL);
    int pad = Screen.dp(16f);
    if (xgram) {
      ll.setPadding(Screen.dp(24f), Screen.dp(4f), Screen.dp(24f), 0);
    } else {
      ll.setPadding(pad, pad, pad, pad);
    }

    final int textColor = Theme.textAccentColor();

    TextView text = new NoScrollTextView(this);
    if (xgram) {
      com.fahadali.xgram.appearance.XgramExpressive.applyText(text, com.fahadali.xgram.appearance.XgramExpressive.TS_BODY_M, false);
      text.setTextColor(com.fahadali.xgram.appearance.XgramExpressive.color(com.fahadali.xgram.appearance.XgramExpressive.ON_SURFACE_VARIANT));
      text.setGravity(Gravity.START);
      text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
    } else {
      text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
      text.setTextColor(textColor);
      text.setGravity(Gravity.CENTER);
    }
    text.setText(Lang.getString(R.string.VoipRateCallAlert));
    if (xgram) {
      ll.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    } else {
      ll.addView(text);
    }

    final BetterRatingView bar = new BetterRatingView(this);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
    params.topMargin = Screen.dp(xgram ? 12f : 16f);
    ll.addView(bar, params);

    int accountId = getIntent().getIntExtra("account_id", TdlibAccount.NO_ID);
    int callId = getIntent().getIntExtra("call_id", 0);
    Tdlib tdlib = TdlibManager.getTdlib(accountId); // FIXME: acquire reference?

    final MaterialEditTextGroup commentBox = new MaterialEditTextGroup(this, tdlib);
    commentBox.setHint(R.string.VoipFeedbackCommentHint);
    commentBox.setVisibility(View.GONE);
    commentBox.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
    params.topMargin = Screen.dp(16f);
    ll.addView(commentBox, params);

    AlertDialog.Builder builder = new AlertDialog.Builder(this, Theme.dialogTheme())
      .setTitle(Lang.getString(xgram ? R.string.XgramR5CallsRateTitle : R.string.AppName))
      .setView(ll)
      .setPositiveButton(Lang.getOK(), (dialog, which) -> {
        int rating = bar.getRating();
        String comment = rating < 5 ? commentBox.getText().toString() : "";
        Log.i(Log.TAG_VOIP, "Submitting call feedback, call_id: %d, rating: %d, comment: %s", callId, rating, comment);
        tdlib.send(new TdApi.SendCallRating(new TdApi.InputCallDiscarded(callId), rating, comment, null), tdlib.typedOkHandler());
        finishDelayed();
      })
      .setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> {
        Log.i(Log.TAG_VOIP, "User denied to give feedback");
        finishDelayed();
      });
    final AlertDialog alert;
    if (xgram) {
      // XGram M3E: as BaseActivity.showAlert, the M3 window motion is set before the dialog shows (this is no BaseActivity)
      alert = builder.create();
      com.fahadali.xgram.appearance.XgramAppMotion.dialogWindow(alert);
      alert.show();
    } else {
      alert = builder.show();
    }
    BaseActivity.modifyAlert(this, alert, null);
    alert.setCanceledOnTouchOutside(true);
    alert.setOnCancelListener(dialog -> finish());
    final View btn = alert.getButton(DialogInterface.BUTTON_POSITIVE);
    btn.setEnabled(false);
    bar.setOnRatingChangeListener(rating -> {
      btn.setEnabled(rating > 0);
      commentBox.setVisibility(rating < 5 && rating > 0 ? View.VISIBLE : View.GONE);
      if (commentBox.getVisibility() == View.GONE) {
        InputMethodManager manager = ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE));
        if (manager != null) {
          manager.hideSoftInputFromWindow(commentBox.getWindowToken(), 0);
        }
      }
    });
  }

  private void finishDelayed () {
    UI.post(() -> finish(), 500);
  }

  @Override
  public void finish() {
    super.finish();
    overridePendingTransition(0, 0);
  }
}
