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
package com.fahadali.xgram.chats;

import android.content.Context;
import android.view.View;

import com.fahadali.xgram.XgramSettings;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;

/** Settings → XGram → Chats → Translation → Translate to: the app's language, or one of Telegram's languages. */
public final class XgramTranslateTargetController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public XgramTranslateTargetController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_xgramTrTarget;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.XgramTrTarget);
  }

  /** The value shown under "Translate to". */
  public static String currentName () {
    String target = XgramSettings.instance().translateTarget();
    return StringUtils.isEmpty(target) ?
      Lang.getString(R.string.XgramTrTargetApp, XgramAutoTranslate.languageName(XgramAutoTranslate.appLanguage())) :
      XgramAutoTranslate.languageName(target);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        Object data = item.getData();
        String code = data instanceof String ? (String) data : "";
        if (code.isEmpty()) {
          view.setData(XgramAutoTranslate.languageName(XgramAutoTranslate.appLanguage()));
        } else {
          TdApi.LanguagePackInfo info = new TdApi.LanguagePackInfo();
          view.setData(Lang.fixLanguageCode(code, info) && !StringUtils.isEmpty(info.nativeName) ? info.nativeName : code);
        }
        view.findRadioView().setChecked(code.equals(XgramSettings.instance().translateTarget()), isUpdate);
      }
    };
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.XgramTrTargetInfo));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2, R.id.btn_xgramTrTargetLanguage, 0, R.string.XgramTrTargetAppTitle).setData(""));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (String code : Lang.getSupportedLanguagesForTranslate()) {
      if (!first) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      first = false;
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2, R.id.btn_xgramTrTargetLanguage, 0, XgramAutoTranslate.languageName(code)).setData(code));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (v.getId() == R.id.btn_xgramTrTargetLanguage && tag instanceof ListItem && ((ListItem) tag).getData() instanceof String) {
      String code = (String) ((ListItem) tag).getData();
      if (!code.equals(XgramSettings.instance().translateTarget())) {
        XgramSettings.instance().setTranslateTarget(code);
        adapter.updateAllValuedSettingsById(R.id.btn_xgramTrTargetLanguage);
      }
    }
  }
}
