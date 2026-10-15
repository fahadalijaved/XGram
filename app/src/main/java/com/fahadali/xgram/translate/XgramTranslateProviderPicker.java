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
package com.fahadali.xgram.translate;

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;

import me.vkryl.core.lambda.RunnableInt;

/**
 * The translation service picker, shared by XGram's Translation settings and Telegram X's Translate messages page.
 */
public final class XgramTranslateProviderPicker {
  private XgramTranslateProviderPicker () { }

  /**
   * Telegram, Google Translate or Yandex Translate; a third-party service asks once, saying the text goes to it.
   * {@code onChosen} gets the new service (never the current one) and saves it.
   */
  public static void show (ViewController<?> c, RunnableInt onChosen) {
    int[] providers = XgramTranslator.PROVIDERS;
    String[] titles = new String[providers.length];
    int[] ids = new int[providers.length];
    for (int i = 0; i < providers.length; i++) {
      titles[i] = XgramTranslator.name(providers[i]);
      ids[i] = i + 1;
    }
    c.showOptions(Lang.getString(R.string.XgramTrProvider), ids, titles, null, null, (itemView, optionId) -> {
      int provider = providers[optionId - 1];
      XgramSettings settings = XgramSettings.instance();
      if (provider == settings.translateProvider()) {
        return true;
      }
      if (XgramTranslator.isThirdParty(provider) && !settings.translateNoticeShown(provider)) {
        String name = XgramTranslator.name(provider);
        c.showConfirm(XgramTranslator.info(provider), Lang.getString(R.string.XgramTrUse, name), () -> {
          settings.setTranslateNoticeShown(provider);
          onChosen.runWithInt(provider);
        });
      } else {
        onChosen.runWithInt(provider);
      }
      return true;
    });
  }
}
