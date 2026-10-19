package com.fahadali.xgram.appearance;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

/**
 * Telegram X theme files are made for the classic design. With Expressive design on, most of XGram takes the M3E
 * scheme's colours, so a custom theme only shows here and there. Before such a theme is applied, XGram offers to turn
 * Expressive design off (the app restarts), to apply it anyway, or to cancel. XGram's own pack themes skip this.
 */
public final class XgramThemeGate {
  private XgramThemeGate () { }

  /** Runs {@code apply} now, or after asking when Expressive design is on. */
  public static void confirm (ViewController<?> c, Runnable apply) {
    if (!XgramExpressive.isEnabled()) {
      apply.run();
      return;
    }
    c.xgramShowConfirm(null,
      Lang.getString(R.string.XgramThemeGateTitle),
      Lang.getString(R.string.XgramThemeGateText),
      new int[] {R.id.btn_xgramThemeGateTurnOff, R.id.btn_xgramThemeGateApply, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.XgramThemeGateTurnOff), Lang.getString(R.string.XgramThemeGateApply), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_palette_24, R.drawable.baseline_check_circle_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_xgramThemeGateTurnOff) {
          apply.run();
          // After the theme switch is saved: Expressive design off restarts the app into the classic design
          UI.post(() -> {
            if (!c.isDestroyed()) {
              XgramExpressive.setEnabled(c, false);
            }
          }, 400);
        } else if (id == R.id.btn_xgramThemeGateApply) {
          apply.run();
        }
        return true;
      });
  }
}
