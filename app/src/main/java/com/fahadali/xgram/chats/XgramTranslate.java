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

import com.fahadali.xgram.XgramSettings;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.StringUtils;

/**
 * When Translate is offered for a message and how it starts, shared by the XGram swipe, the message menu and
 * Telegram X's quick translation. Telegram X hides Translate for every message in a "Do not translate" language
 * (by default the app's own language); XGram offers it on every message unless "Show Translate on every message" is off.
 */
public final class XgramTranslate {
  private XgramTranslate () { }

  /** The message has text that can be translated. */
  public static boolean hasText (TGMessage m) {
    return m.xgramHasTextToTranslate();
  }

  /**
   * Whether to offer Translate. {@code explicitChoice} is the swipe the user set to Translate: it only needs text.
   * Never in secret chats: the text would leave the end-to-end encrypted chat.
   */
  public static boolean canOffer (TGMessage m, boolean explicitChoice) {
    if (!hasText(m) || m.isSecretChat()) {
      return false;
    }
    if (explicitChoice) {
      return true;
    }
    return m.translationStyleMode() != Settings.TRANSLATE_MODE_NONE && (XgramSettings.instance().translateAlways() || m.isTranslatable());
  }

  /**
   * Starts translating. The popup opens instead of Inline when Inline would do nothing: style None, a language
   * on the "Do not translate" list, or no target language other than the message's own (the popup lets the user pick one).
   */
  public static void start (MessagesController c, TGMessage m) {
    if (c == null) {
      return;
    }
    String from = m.getOriginalMessageLanguage();
    String to = Lang.getDefaultLanguageToTranslateV2(from);
    boolean forcePopup = m.translationStyleMode() == Settings.TRANSLATE_MODE_NONE || !m.isTranslatable() || StringUtils.isEmpty(to) || StringUtils.equalsOrBothEmpty(from, to);
    c.startTranslateMessages(m, forcePopup);
  }
}
