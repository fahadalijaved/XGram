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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/** ID sheet of a profile (NagramX, exteraGram): copyable IDs, estimated registration date, joined / created date. */
public final class XgramIdInfo {
  private XgramIdInfo () { }

  // Known (user id, unix time) points; dates of other ids are interpolated between them.
  private static final long[][] ANCHORS = {
    {2768409L, 1383264000L}, {7679610L, 1388448000L}, {11538514L, 1391212000L}, {15835244L, 1392940000L},
    {23646077L, 1393459000L}, {38015510L, 1393632000L}, {44634663L, 1399334000L}, {46145305L, 1400198000L},
    {54845238L, 1411768000L}, {63263518L, 1414808000L}, {101260938L, 1425855000L}, {111220210L, 1429906000L},
    {122600695L, 1437876000L}, {124872445L, 1439559000L}, {133909606L, 1444296000L}, {157242073L, 1446369000L},
    {171295414L, 1457201000L}, {181783990L, 1460147000L}, {222021233L, 1465733000L}, {225034354L, 1466996000L},
    {278941742L, 1473282000L}, {285253072L, 1476952000L}, {294851037L, 1479716000L}, {297621225L, 1480604000L},
    {328594461L, 1487687000L}, {337808429L, 1490203000L}, {341546272L, 1491856000L}, {352940995L, 1493547000L},
    {369669043L, 1499450000L}, {400169472L, 1505305000L}, {616816630L, 1529520000L}, {681896077L, 1533038000L},
    {727572658L, 1537710000L}, {796147074L, 1542055000L}, {925078064L, 1548098000L}, {1054883348L, 1553038000L},
    {1145856008L, 1563020000L}, {1227964864L, 1570450000L}, {1382531194L, 1585780000L}, {1658586909L, 1611000000L},
    {1692464211L, 1612923000L}, {1772991138L, 1617875000L}, {1807942741L, 1620224000L}, {1893390460L, 1626076000L},
    {1955701455L, 1630006000L}, {2032474488L, 1635800000L}, {2108519051L, 1641600000L}, {5000000000L, 1643700000L},
    {5500000000L, 1661000000L}, {6000000000L, 1680000000L}, {6500000000L, 1698000000L}, {7000000000L, 1711000000L},
    {7500000000L, 1723000000L}, {8000000000L, 1737000000L}
  };

  /** Rough registration time of a user, in unix seconds. */
  public static long estimateRegistration (long userId) {
    long now = System.currentTimeMillis() / 1000L;
    if (userId <= ANCHORS[0][0]) {
      return ANCHORS[0][1];
    }
    for (int i = 1; i < ANCHORS.length; i++) {
      if (userId <= ANCHORS[i][0]) {
        long[] a = ANCHORS[i - 1], b = ANCHORS[i];
        return a[1] + (long) ((double) (userId - a[0]) / (b[0] - a[0]) * (b[1] - a[1]));
      }
    }
    long[] a = ANCHORS[ANCHORS.length - 2], b = ANCHORS[ANCHORS.length - 1];
    long extrapolated = b[1] + (long) ((double) (userId - b[0]) / (b[0] - a[0]) * (b[1] - a[1]));
    return Math.min(extrapolated, now);
  }

  public static String monthYear (long unixSeconds) {
    return new SimpleDateFormat("MMM yyyy", Lang.locale()).format(new Date(unixSeconds * 1000L));
  }

  /** "~ Mar 2016", shown next to a user's ID. */
  public static String registrationLabel (long userId) {
    return "~" + monthYear(estimateRegistration(userId));
  }

  /** Opens the sheet for the ID row of a profile. */
  public static void show (ViewController<?> context, long peerId, @Nullable TdApi.User user, @Nullable TdApi.Chat chat, @Nullable TdApi.Supergroup supergroup) {
    StringBuilder info = new StringBuilder(Long.toString(peerId));
    String chatIdText = chat != null && chat.id != peerId ? Long.toString(chat.id) : null;
    String link = null;
    if (user != null) {
      info.append('\n').append(Lang.getString(R.string.XgramRegisteredAbout, monthYear(estimateRegistration(user.id))));
      link = "tg://user?id=" + user.id;
    } else if (supergroup != null && supergroup.date != 0) {
      boolean isMember = supergroup.status != null && supergroup.status.getConstructor() != TdApi.ChatMemberStatusLeft.CONSTRUCTOR && supergroup.status.getConstructor() != TdApi.ChatMemberStatusBanned.CONSTRUCTOR;
      info.append('\n').append(Lang.getString(isMember ? R.string.XgramJoinedOn : R.string.XgramCreatedOn, Lang.getDate(supergroup.date, TimeUnit.SECONDS)));
    }
    int count = 1 + (chatIdText != null ? 1 : 0) + (link != null ? 1 : 0);
    int[] ids = new int[count];
    String[] titles = new String[count];
    int[] icons = new int[count];
    int i = 0;
    ids[i] = R.id.btn_peer_id_copy;
    titles[i] = Lang.getString(R.string.Copy);
    icons[i++] = R.drawable.baseline_content_copy_24;
    if (chatIdText != null) {
      ids[i] = R.id.btn_xgramCopyChatId;
      titles[i] = Lang.getString(R.string.XgramCopyChatId, chatIdText);
      icons[i++] = R.drawable.baseline_content_copy_24;
    }
    if (link != null) {
      ids[i] = R.id.btn_copyLink;
      titles[i] = Lang.getString(R.string.CopyLink);
      icons[i] = R.drawable.baseline_link_24;
    }
    final String finalLink = link;
    context.showOptions(info.toString(), ids, titles, null, icons, (itemView, id) -> {
      if (id == R.id.btn_peer_id_copy) {
        UI.copyText(Long.toString(peerId), R.string.CopiedPeerId);
      } else if (id == R.id.btn_xgramCopyChatId) {
        UI.copyText(chatIdText, R.string.CopiedPeerId);
      } else if (id == R.id.btn_copyLink && finalLink != null) {
        UI.copyText(finalLink, R.string.CopiedLink);
      }
      return true;
    });
  }
}
