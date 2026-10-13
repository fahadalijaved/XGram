package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

public class WaitForPremiumController extends RecyclerViewController<TdApi.AuthorizationStateWaitPremiumPurchase> implements View.OnClickListener {
  public WaitForPremiumController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_waitForPremium;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.login_PremiumRequiredTitle);
  }

  // XGram: this is a login screen, so MainActivity must follow its state changes (e.g. to the QR code screen)
  @Override
  public boolean isUnauthorized () {
    return true;
  }

  private boolean oneShot;
  private boolean qrLoginInProgress; // XGram

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      oneShot = true;
      destroyStackItemById(R.id.controller_code);
      destroyStackItemById(R.id.controller_name);
      destroyStackItemById(R.id.controller_password);
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this);
    // XGram: XGram wording and the server's day count, plus the ways out TDLib allows from this state, see com.fahadali.xgram.login.PremiumRequired
    TdApi.AuthorizationStateWaitPremiumPurchase state = com.fahadali.xgram.login.PremiumRequired.currentState(tdlib, getArguments());
    java.util.List<ListItem> items = new java.util.ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_premium_star_96, com.fahadali.xgram.login.PremiumRequired.message(this, state)));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_buyPremium, 0, R.string.login_PremiumRequiredBtn));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    // XGram: another way in, approved from a device that is already logged in (TDLib allows it from this state)
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramQrLogin, R.drawable.xt3000_baseline_qrcode_scan_24, R.string.xgram_login_QrButton));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramPremiumOtherNumber, R.drawable.baseline_phone_24, R.string.xgram_login_UseDifferentNumber));
    if (com.fahadali.xgram.login.PremiumRequired.hasSupportEmail(state)) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_xgramPremiumSupport, R.drawable.baseline_help_24, R.string.xgram_login_PremiumSupport));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_buyPremium) {
      UI.openUrl("https://telegram.org/");
    } else if (v.getId() == R.id.btn_xgramQrLogin && !qrLoginInProgress) { // XGram
      qrLoginInProgress = com.fahadali.xgram.login.QrLogin.request(this, tdlib, error -> {
        qrLoginInProgress = false;
        if (error != null) {
          UI.showError(error);
        }
      });
    } else if (v.getId() == R.id.btn_xgramPremiumOtherNumber && !qrLoginInProgress) { // XGram
      com.fahadali.xgram.login.PremiumRequired.useDifferentNumber(this, tdlib);
    } else if (v.getId() == R.id.btn_xgramPremiumSupport) { // XGram
      com.fahadali.xgram.login.PremiumRequired.sendSupportEmail(tdlib, com.fahadali.xgram.login.PremiumRequired.currentState(tdlib, getArguments()));
    }
  }
}
